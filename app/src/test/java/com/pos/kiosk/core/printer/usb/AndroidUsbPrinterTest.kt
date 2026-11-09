package com.pos.kiosk.core.printer.usb

import android.app.PendingIntent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.pos.kiosk.core.printer.builtin.PrinterStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUsbPrinterTest {
    private val output = endpoint(UsbConstants.USB_DIR_OUT)
    private val input = endpoint(UsbConstants.USB_DIR_IN)
    private val printerInterface = usbInterface(input, output)
    private val printerDevice = device(printerInterface)
    private val connection = mockk<UsbDeviceConnection>(relaxed = true) {
        every { claimInterface(any(), any()) } returns true
    }
    private val intent = mockk<PendingIntent>()

    private fun endpoint(direction: Int, type: Int = UsbConstants.USB_ENDPOINT_XFER_BULK) = mockk<UsbEndpoint> {
        every { this@mockk.direction } returns direction
        every { this@mockk.type } returns type
        every { maxPacketSize } returns 64
    }

    private fun usbInterface(vararg endpoints: UsbEndpoint, usbClass: Int = UsbConstants.USB_CLASS_PRINTER, subclass: Int = 1) = mockk<UsbInterface> {
        every { interfaceClass } returns usbClass
        every { interfaceSubclass } returns subclass
        every { endpointCount } returns endpoints.size
        endpoints.forEachIndexed { index, endpoint -> every { getEndpoint(index) } returns endpoint }
    }

    private fun device(vararg interfaces: UsbInterface, name: String? = "XP-80C") = mockk<UsbDevice> {
        every { productName } returns name
        every { interfaceCount } returns interfaces.size
        interfaces.forEachIndexed { index, usbInterface -> every { getInterface(index) } returns usbInterface }
    }

    private fun manager(vararg devices: UsbDevice, permitted: Boolean = true) = mockk<UsbManager>(relaxed = true) {
        every { deviceList } returns HashMap(devices.withIndex().associate { (index, device) -> "/dev/bus/usb/001/00$index" to device })
        every { hasPermission(any<UsbDevice>()) } returns permitted
        every { openDevice(any()) } returns connection
    }

    private fun printer(manager: UsbManager = manager(printerDevice)) = AndroidUsbPrinter(manager) { intent }

    /** Accepts every chunk in full, and records the order they came in. */
    private fun acceptEverything(): MutableList<Pair<Int, Int>> {
        val chunks = mutableListOf<Pair<Int, Int>>()
        every { connection.bulkTransfer(output, any(), any(), any(), any()) } answers {
            chunks += thirdArg<Int>() to arg<Int>(3)
            arg(3)
        }
        return chunks
    }

    private fun printerAnswers(status: Int) {
        every { connection.bulkTransfer(output, any(), any(), any()) } answers { thirdArg() }
        every { connection.bulkTransfer(input, any(), any(), any()) } answers {
            secondArg<ByteArray>()[0] = status.toByte()
            1
        }
    }

    // Finding it

    @Test
    fun `a printer-class device is found by its product name`() {
        assertEquals(AttachedUsbPrinter("XP-80C", permitted = true), printer().attached())
    }

    @Test
    fun `nothing plugged in, or no USB service at all, reads as no printer`() {
        assertNull(printer(manager()).attached())
        assertNull(AndroidUsbPrinter(null) { intent }.attached())
    }

    @Test
    fun `a keyboard or a card reader is not a printer`() {
        val keyboard = device(usbInterface(output, usbClass = UsbConstants.USB_CLASS_HID))

        assertNull(printer(manager(keyboard)).attached())
    }

    @Test
    fun `an interface of the printer class but another subclass is left alone`() {
        assertNull(printer(manager(device(usbInterface(output, subclass = 2)))).attached())
    }

    @Test
    fun `a printer with no bulk way in can't be used`() {
        val onlyInput = device(usbInterface(input))
        val interruptOutput = device(usbInterface(endpoint(UsbConstants.USB_DIR_OUT, UsbConstants.USB_ENDPOINT_XFER_INT)))

        assertNull(printer(manager(onlyInput)).attached())
        assertNull(printer(manager(interruptOutput)).attached())
    }

    @Test
    fun `a printer behind another interface of the same device is found`() {
        val composite = device(usbInterface(input, usbClass = UsbConstants.USB_CLASS_MASS_STORAGE), usbInterface(output), name = "Combo")

        assertEquals("Combo", printer(manager(composite)).attached()?.name)
    }

    @Test
    fun `a printer that gives no name still shows as one`() {
        assertEquals("Printer", printer(manager(device(printerInterface, name = null))).attached()?.name)
        assertEquals("Printer", printer(manager(device(printerInterface, name = "  "))).attached()?.name)
        assertEquals("POS58", printer(manager(device(printerInterface, name = " POS58 "))).attached()?.name)
    }

    @Test
    fun `it says whether Android has allowed the printer`() {
        val printer = printer(manager(printerDevice, permitted = false))

        assertFalse(printer.attached()!!.permitted)
    }

    // Sending

    @Test
    fun `a short job goes out in one transfer on the bulk OUT endpoint`() {
        val payload = ByteArray(1_000)
        val chunks = acceptEverything()

        assertTrue(printer().send(payload))

        assertEquals(listOf(0 to 1_000), chunks)
        verify { connection.claimInterface(printerInterface, true) }
    }

    @Test
    fun `a long job is split so old Android doesn't cut it short`() {
        val chunks = acceptEverything()

        assertTrue(printer().send(ByteArray(USB_CHUNK_BYTES * 2 + 100)))

        assertEquals(listOf(0 to USB_CHUNK_BYTES, USB_CHUNK_BYTES to USB_CHUNK_BYTES, USB_CHUNK_BYTES * 2 to 100), chunks)
    }

    @Test
    fun `a job of exactly one chunk needs no second transfer`() {
        val chunks = acceptEverything()

        assertTrue(printer().send(ByteArray(USB_CHUNK_BYTES)))

        assertEquals(listOf(0 to USB_CHUNK_BYTES), chunks)
    }

    @Test
    fun `a partial transfer carries on from where it stopped`() {
        val offsets = mutableListOf<Int>()
        every { connection.bulkTransfer(output, any(), any(), any(), any()) } answers {
            offsets += thirdArg<Int>()
            minOf(600, arg<Int>(3))
        }

        assertTrue(printer().send(ByteArray(1_000)))

        assertEquals(listOf(0, 600), offsets)
    }

    @Test
    fun `no transfer may wait forever, or a stuck printer would hang every later receipt`() {
        val timeout = slot<Int>()
        every { connection.bulkTransfer(output, any(), any(), any(), capture(timeout)) } answers { arg(3) }

        printer().send(ByteArray(10))

        assertTrue(timeout.captured > 0)
    }

    @Test(timeout = 5_000)
    fun `a printer that refuses the data fails the job at once`() {
        every { connection.bulkTransfer(output, any(), any(), any(), any()) } returns -1

        assertFalse(printer().send(ByteArray(USB_CHUNK_BYTES * 3)))

        verify(exactly = 1) { connection.bulkTransfer(output, any(), any(), any(), any()) }
    }

    @Test(timeout = 5_000)
    fun `a printer that takes nothing fails the job instead of spinning`() {
        every { connection.bulkTransfer(output, any(), any(), any(), any()) } returns 0

        assertFalse(printer().send(ByteArray(10)))
    }

    @Test
    fun `the port is given back after a job, whichever way it went`() {
        every { connection.bulkTransfer(output, any(), any(), any(), any()) } returns -1

        printer().send(ByteArray(10))

        verify { connection.releaseInterface(printerInterface) }
        verify { connection.close() }
    }

    @Test
    fun `a port something else holds fails the job and is closed`() {
        every { connection.claimInterface(any(), any()) } returns false

        assertFalse(printer().send(ByteArray(10)))

        verify(exactly = 0) { connection.bulkTransfer(any(), any(), any(), any(), any()) }
        verify { connection.close() }
    }

    @Test
    fun `without permission the port is never opened, which Android would answer with an exception`() {
        val manager = manager(printerDevice, permitted = false)

        assertFalse(printer(manager).send(ByteArray(10)))
        assertEquals(PrinterStatus.Offline, printer(manager).status())

        verify(exactly = 0) { manager.openDevice(any()) }
    }

    @Test
    fun `a printer unplugged at the wrong moment fails the job, not the app`() {
        val returnsNothing = manager(printerDevice).apply { every { openDevice(any()) } returns null }
        val throws = manager(printerDevice).apply { every { openDevice(any()) } throws IllegalArgumentException("device not found") }

        assertFalse(printer(returnsNothing).send(ByteArray(10)))
        assertFalse(printer(throws).send(ByteArray(10)))
    }

    @Test
    fun `a printer unplugged in the middle of a job closes the port too`() {
        every { connection.bulkTransfer(output, any(), any(), any(), any()) } throws IllegalStateException("gone")

        assertFalse(printer().send(ByteArray(10)))

        verify { connection.close() }
    }

    // Status

    @Test
    fun `the status question is ESC POS DLE EOT 2`() {
        val request = slot<ByteArray>()
        every { connection.bulkTransfer(output, capture(request), any(), any()) } answers { thirdArg() }

        printer().status()

        assertArrayEquals(byteArrayOf(0x10, 0x04, 0x02), request.captured)
    }

    @Test
    fun `the printer's answer is read`() {
        printerAnswers(0x32)

        assertEquals(PrinterStatus.OutOfPaper, printer().status())
        verify { connection.close() }
    }

    @Test
    fun `reading the status gives the port back as well`() {
        printerAnswers(0x12)

        printer().status()

        verify { connection.releaseInterface(printerInterface) }
    }

    @Test
    fun `a printer that doesn't answer is left to try the job`() {
        every { connection.bulkTransfer(output, any(), any(), any()) } answers { thirdArg() }
        every { connection.bulkTransfer(input, any(), any(), any()) } returns -1

        assertEquals(PrinterStatus.Ready, printer().status())
    }

    @Test
    fun `a printer busy with the question is left to try the job`() {
        every { connection.bulkTransfer(output, any(), any(), any()) } returns -1

        assertEquals(PrinterStatus.Ready, printer().status())
        verify(exactly = 0) { connection.bulkTransfer(input, any(), any(), any()) }
    }

    @Test
    fun `a printer with no way to answer is left to try the job`() {
        assertEquals(PrinterStatus.Ready, printer(manager(device(usbInterface(output)))).status())
    }

    @Test
    fun `a printer that can't be reached is offline`() {
        assertEquals(PrinterStatus.Offline, printer(manager()).status())
        assertEquals(PrinterStatus.Offline, printer(manager(printerDevice).apply { every { openDevice(any()) } returns null }).status())
    }

    @Test
    fun `the status byte follows ESC POS`() {
        assertEquals(PrinterStatus.Ready, usbPrinterStatus(0x12))
        assertEquals(PrinterStatus.Ready, usbPrinterStatus(0x1A)) // feed button held
        assertEquals(PrinterStatus.OutOfPaper, usbPrinterStatus(0x32))
        assertEquals(PrinterStatus.CoverOpen, usbPrinterStatus(0x16))
        assertEquals(PrinterStatus.Offline, usbPrinterStatus(0x52))
    }

    @Test
    fun `an open cover outranks the paper sensor, which can't see through it`() {
        assertEquals(PrinterStatus.CoverOpen, usbPrinterStatus(0x36))
    }

    @Test
    fun `a byte that isn't a status reply never stops a receipt`() {
        assertEquals(PrinterStatus.Ready, usbPrinterStatus(null))
        for (junk in listOf(0x00, 0xFF, 0x20, 0x33, 0x92, 0x10, 0x02)) {
            assertEquals("0x${junk.toString(16)}", PrinterStatus.Ready, usbPrinterStatus(junk))
        }
    }

    // Permission

    @Test
    fun `permission is asked for the printer that was found`() {
        val manager = manager(printerDevice, permitted = false)

        printer(manager).requestPermission()

        verify { manager.requestPermission(printerDevice, intent) }
    }

    @Test
    fun `with no printer there is nothing to ask`() {
        val manager = manager()

        printer(manager).requestPermission()

        verify(exactly = 0) { manager.requestPermission(any<UsbDevice>(), any()) }
    }

    @Test
    fun `a printer unplugged just before the prompt doesn't take the app down`() {
        val manager = manager(printerDevice).apply {
            every { requestPermission(any<UsbDevice>(), any()) } throws IllegalArgumentException("device not found")
        }

        printer(manager).requestPermission()
    }
}
