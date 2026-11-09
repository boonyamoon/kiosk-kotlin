package com.pos.kiosk.core.printer.usb

import android.app.PendingIntent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.pos.kiosk.core.printer.builtin.PrinterStatus

private const val PRINTER_SUBCLASS = 1
private const val DEFAULT_NAME = "Printer"

/** Android 8 cuts a longer transfer to this anyway, and each chunk gets the timeout to itself. */
internal const val USB_CHUNK_BYTES = 16_384
private const val WRITE_TIMEOUT_MS = 10_000
private const val STATUS_TIMEOUT_MS = 500
private const val STATUS_REPLY_MIN_BYTES = 64

/** DLE EOT 2: ESC/POS real-time status, answered even while the printer has stopped. */
private val STATUS_REQUEST = byteArrayOf(0x10, 0x04, 0x02)
private const val STATUS_FIXED_MASK = 0x93
private const val STATUS_FIXED_BITS = 0x12
private const val STATUS_COVER_OPEN = 0x04
private const val STATUS_PAPER_OUT = 0x20
private const val STATUS_ERROR = 0x40

/**
 * Reads the reply to DLE EOT 2. Bits 0, 1, 4 and 7 are fixed, so a byte without them isn't that reply. A printer that
 * can't be understood is left to try: a wrong "out of paper" would cost a receipt that would have printed.
 * An open cover comes first, since the paper sensor can't see the paper then.
 */
internal fun usbPrinterStatus(reply: Int?): PrinterStatus = when {
    reply == null || reply and STATUS_FIXED_MASK != STATUS_FIXED_BITS -> PrinterStatus.Ready
    reply and STATUS_COVER_OPEN != 0 -> PrinterStatus.CoverOpen
    reply and STATUS_PAPER_OUT != 0 -> PrinterStatus.OutOfPaper
    reply and STATUS_ERROR != 0 -> PrinterStatus.Offline
    else -> PrinterStatus.Ready
}

/**
 * Sends [size] bytes through [transfer], which returns how many it took. Zero counts as a failure as well, or a printer
 * that takes nothing would keep this loop going forever.
 */
internal inline fun writeInChunks(size: Int, transfer: (offset: Int, length: Int) -> Int): Boolean {
    var offset = 0
    while (offset < size) {
        val written = transfer(offset, minOf(USB_CHUNK_BYTES, size - offset))
        if (written <= 0) return false
        offset += written
    }
    return true
}

/**
 * The USB printer class on Android's USB host API. Each call opens the port and closes it again, so a printer that was
 * unplugged or swapped between receipts needs nothing special. Every transfer has a timeout: a stalled printer fails
 * the receipt, and the kiosk moves on to the next customer.
 */
internal class AndroidUsbPrinter(
    private val manager: UsbManager?,
    private val permissionIntent: () -> PendingIntent,
) : UsbPrinter {

    private class Port(val device: UsbDevice, val printerInterface: UsbInterface, val output: UsbEndpoint, val input: UsbEndpoint?)

    override fun attached(): AttachedUsbPrinter? {
        val port = findPort() ?: return null
        val name = port.device.productName?.trim().orEmpty().ifEmpty { DEFAULT_NAME }
        return AttachedUsbPrinter(name, manager?.hasPermission(port.device) == true)
    }

    override fun status(): PrinterStatus = withConnection { connection, port ->
        val input = port.input ?: return@withConnection PrinterStatus.Ready
        val asked = connection.bulkTransfer(port.output, STATUS_REQUEST, STATUS_REQUEST.size, STATUS_TIMEOUT_MS)
        if (asked != STATUS_REQUEST.size) return@withConnection PrinterStatus.Ready
        val reply = ByteArray(maxOf(input.maxPacketSize, STATUS_REPLY_MIN_BYTES))
        val read = connection.bulkTransfer(input, reply, reply.size, STATUS_TIMEOUT_MS)
        usbPrinterStatus(if (read > 0) reply[0].toInt() and 0xFF else null)
    } ?: PrinterStatus.Offline

    override fun send(payload: ByteArray): Boolean = withConnection { connection, port ->
        writeInChunks(payload.size) { offset, length ->
            connection.bulkTransfer(port.output, payload, offset, length, WRITE_TIMEOUT_MS)
        }
    } ?: false

    override fun requestPermission() {
        val port = findPort() ?: return
        try {
            manager?.requestPermission(port.device, permissionIntent())
        } catch (_: RuntimeException) {
            // Unplugged between the two calls: there is nothing left to ask about.
        }
    }

    private fun findPort(): Port? = manager?.deviceList?.values?.firstNotNullOfOrNull(::portOf)

    private fun portOf(device: UsbDevice): Port? {
        for (index in 0 until device.interfaceCount) {
            val candidate = device.getInterface(index)
            if (candidate.interfaceClass != UsbConstants.USB_CLASS_PRINTER || candidate.interfaceSubclass != PRINTER_SUBCLASS) continue
            val endpoints = (0 until candidate.endpointCount).map(candidate::getEndpoint).filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
            val output = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT } ?: continue
            return Port(device, candidate, output, endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN })
        }
        return null
    }

    /** Null when the port can't be used: no permission, claimed by something else, or unplugged along the way. */
    private inline fun <T : Any> withConnection(use: (UsbDeviceConnection, Port) -> T): T? {
        val manager = manager ?: return null
        val port = findPort() ?: return null
        return try {
            if (!manager.hasPermission(port.device)) return null
            val connection = manager.openDevice(port.device) ?: return null
            try {
                if (!connection.claimInterface(port.printerInterface, true)) return null
                try {
                    use(connection, port)
                } finally {
                    connection.releaseInterface(port.printerInterface)
                }
            } finally {
                connection.close()
            }
        } catch (_: RuntimeException) {
            null // Android throws when the device has left since it was found.
        }
    }
}
