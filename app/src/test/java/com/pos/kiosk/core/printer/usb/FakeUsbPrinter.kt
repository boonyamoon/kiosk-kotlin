package com.pos.kiosk.core.printer.usb

import com.pos.kiosk.core.printer.builtin.PAPER_80MM_DOTS
import com.pos.kiosk.core.printer.builtin.PrinterStatus

internal class FakeUsbPrinter(
    var printer: AttachedUsbPrinter? = AttachedUsbPrinter("XP-80C", permitted = true),
    var status: PrinterStatus = PrinterStatus.Ready,
    /** Jobs it takes before it stops accepting data. */
    private val takes: Int = Int.MAX_VALUE,
    private val beforeSend: () -> Unit = {},
) : UsbPrinter {
    val sent = mutableListOf<ByteArray>()
    var attachedReads = 0
    var statusReads = 0
    var sendCalls = 0
    var permissionRequests = 0

    override fun attached(): AttachedUsbPrinter? {
        attachedReads++
        return printer
    }

    override fun status(): PrinterStatus {
        statusReads++
        return status
    }

    override fun send(payload: ByteArray): Boolean {
        sendCalls++
        beforeSend()
        if (sent.size >= takes) return false
        sent += payload
        return true
    }

    override fun requestPermission() {
        permissionRequests++
    }
}

internal class FakeUsbPaperSetting(var dots: Int = PAPER_80MM_DOTS) : UsbPaperSetting {
    override suspend fun widthDots(): Int = dots

    override suspend fun setWidthDots(dots: Int) {
        this.dots = dots
    }
}
