package com.pos.kiosk.core.printer

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.printer.builtin.BuiltInPrinter
import com.pos.kiosk.core.printer.builtin.NoBuiltInPrinter
import com.pos.kiosk.core.printer.builtin.PAPER_58MM_DOTS
import com.pos.kiosk.core.printer.builtin.PAPER_80MM_DOTS
import com.pos.kiosk.core.printer.builtin.PrinterStatus
import com.pos.kiosk.core.printer.usb.AttachedUsbPrinter
import com.pos.kiosk.core.printer.usb.FakeUsbPaperSetting
import com.pos.kiosk.core.printer.usb.FakeUsbPrinter
import com.pos.kiosk.core.printer.usb.NoUsbPrinter
import com.pos.kiosk.core.printer.usb.UsbPrinter
import com.pos.kiosk.domain.model.ReceiptPrinterConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch

private class FakeBuiltInPrinter(
    var connects: Boolean = true,
    var statuses: MutableList<PrinterStatus> = mutableListOf(PrinterStatus.Ready),
    private val failPrinting: Boolean = false,
) : BuiltInPrinter {
    override val brand = "Sunmi"
    var connectCalls = 0
    val printed = mutableListOf<MonochromeImage>()

    override suspend fun connect(): Boolean {
        connectCalls++
        return connects
    }

    override fun paperWidthDots() = PAPER_58MM_DOTS

    /** Each read takes the next status; the last one repeats. */
    override fun status(): PrinterStatus = if (statuses.size > 1) statuses.removeAt(0) else statuses.first()

    override fun print(image: MonochromeImage) {
        if (failPrinting) throw IllegalStateException("print service refused the job")
        printed += image
    }
}

class PrinterRouterTest {
    private val image = MonochromeImage.fromPixels(8, 1, BooleanArray(8))
    private val lan = mockk<ReceiptPrinter>()
    private val assigned = ReceiptPrinterConfig(name = "Front", host = "192.168.1.50", port = 9100, paperSize = "80mm", copyCount = 2)
    private val paper = FakeUsbPaperSetting()
    private val usbInfo = PrinterInfo.Usb("XP-80C", PAPER_80MM_DOTS, PrinterStatus.Ready, permitted = true)

    private fun router(builtIn: BuiltInPrinter, usb: UsbPrinter = NoUsbPrinter) = PrinterRouter(lan, builtIn, usb, paper)

    @Test
    fun `a LAN printer assigned to the kiosk wins over the device's own`() = runTest {
        val builtIn = FakeBuiltInPrinter()

        assertEquals(PrinterInfo.Lan(assigned), router(builtIn).current(assigned))
        assertEquals(0, builtIn.connectCalls)
    }

    @Test
    fun `with nothing assigned the device's own printer is used, without any setup`() = runTest {
        val printer = router(FakeBuiltInPrinter()).current(assignedLanPrinter = null)

        assertEquals(PrinterInfo.BuiltIn("Sunmi", PAPER_58MM_DOTS, PrinterStatus.Ready), printer)
        assertEquals(58, (printer as PrinterInfo.BuiltIn).paperMm)
    }

    @Test
    fun `no printer anywhere is a normal state`() = runTest {
        assertNull(router(NoBuiltInPrinter).current(assignedLanPrinter = null))
        assertNull(router(FakeBuiltInPrinter(connects = false)).current(assignedLanPrinter = null))
    }

    @Test
    fun `a print service that died is bound again before giving up`() = runTest {
        val builtIn = FakeBuiltInPrinter(statuses = mutableListOf(PrinterStatus.Offline, PrinterStatus.Ready))

        val printer = router(builtIn).current(assignedLanPrinter = null) as PrinterInfo.BuiltIn

        assertEquals(PrinterStatus.Ready, printer.status)
        assertEquals(2, builtIn.connectCalls)
    }

    @Test
    fun `LAN jobs still go to the assigned address with its copies`() = runTest {
        coEvery { lan.print(any(), any(), any()) } returns AppResult.Success(Unit)

        router(FakeBuiltInPrinter()).print(PrinterInfo.Lan(assigned), image, copies = 2)

        coVerify { lan.print(PrinterTarget("192.168.1.50", 9100), image, 2) }
    }

    @Test
    fun `built-in jobs print each copy, up to the same limit as LAN`() = runTest {
        val builtIn = FakeBuiltInPrinter()
        val router = router(builtIn)
        val printer = PrinterInfo.BuiltIn("Sunmi", PAPER_58MM_DOTS, PrinterStatus.Ready)

        assertEquals(AppResult.Success(Unit), router.print(printer, image, copies = 2))
        assertEquals(2, builtIn.printed.size)

        router.print(printer, image, copies = 9)
        assertEquals(5, builtIn.printed.size)
    }

    @Test
    fun `no paper is reported as such and nothing is sent`() = runTest {
        val builtIn = FakeBuiltInPrinter(statuses = mutableListOf(PrinterStatus.OutOfPaper))

        val result = router(builtIn).print(PrinterInfo.BuiltIn("Sunmi", PAPER_58MM_DOTS, PrinterStatus.Ready), image)

        assertEquals(AppResult.Failure(AppError.PrinterOutOfPaper), result)
        assertEquals(0, builtIn.printed.size)
    }

    @Test
    fun `an open cover, a hot head or a dead service stop the job`() = runTest {
        for (status in listOf(PrinterStatus.CoverOpen, PrinterStatus.Overheated, PrinterStatus.Offline)) {
            val builtIn = FakeBuiltInPrinter(statuses = mutableListOf(status))

            val result = router(builtIn).print(PrinterInfo.BuiltIn("Sunmi", PAPER_58MM_DOTS, status), image)

            assertEquals(AppResult.Failure(AppError.PrinterUnavailable), result)
            assertEquals(0, builtIn.printed.size)
        }
    }

    @Test
    fun `an SDK that refuses the job fails the print instead of the app`() = runTest {
        val builtIn = FakeBuiltInPrinter(failPrinting = true)

        val result = router(builtIn).print(PrinterInfo.BuiltIn("Sunmi", PAPER_58MM_DOTS, PrinterStatus.Ready), image)

        assertEquals(AppResult.Failure(AppError.PrinterUnavailable), result)
    }

    @Test
    fun `a USB printer is used when the kiosk has no other, without any setup on the iPad`() = runTest {
        val usb = FakeUsbPrinter(status = PrinterStatus.OutOfPaper)

        assertEquals(usbInfo.copy(status = PrinterStatus.OutOfPaper), router(NoBuiltInPrinter, usb).current(assignedLanPrinter = null))
    }

    @Test
    fun `a device whose printer can't be reached still falls through to USB`() = runTest {
        assertEquals(usbInfo, router(FakeBuiltInPrinter(connects = false), FakeUsbPrinter()).current(assignedLanPrinter = null))
    }

    @Test
    fun `an assigned LAN printer and the device's own both come before USB`() = runTest {
        val usb = FakeUsbPrinter()

        assertEquals(PrinterInfo.Lan(assigned), router(NoBuiltInPrinter, usb).current(assigned))
        assertTrue(router(FakeBuiltInPrinter(), usb).current(assignedLanPrinter = null) is PrinterInfo.BuiltIn)
        assertEquals(0, usb.attachedReads)
    }

    @Test
    fun `nothing on the USB port is no printer`() = runTest {
        assertNull(router(NoBuiltInPrinter, FakeUsbPrinter(printer = null)).current(assignedLanPrinter = null))
    }

    @Test
    fun `a printer staff haven't allowed shows up as such, and is not opened`() = runTest {
        val usb = FakeUsbPrinter(printer = AttachedUsbPrinter("XP-80C", permitted = false))

        val printer = router(NoBuiltInPrinter, usb).current(assignedLanPrinter = null)

        assertEquals(usbInfo.copy(status = PrinterStatus.Offline, permitted = false), printer)
        assertEquals(0, usb.statusReads)
    }

    @Test
    fun `the USB printer prints on the paper staff chose`() = runTest {
        val router = router(NoBuiltInPrinter, FakeUsbPrinter())

        router.setUsbPaperWidth(PAPER_58MM_DOTS)

        val printer = router.current(assignedLanPrinter = null)
        assertEquals(PAPER_58MM_DOTS, printer?.paperWidthDots)
        assertEquals(58, printer?.paperMm)
    }

    @Test
    fun `USB jobs carry the same raster and cut as LAN, per copy and up to the same limit`() = runTest {
        val usb = FakeUsbPrinter()
        val router = router(NoBuiltInPrinter, usb)

        assertEquals(AppResult.Success(Unit), router.print(usbInfo, image, copies = 2))
        assertEquals(2, usb.sent.size)
        assertArrayEquals(EscPosRaster.encode(image), usb.sent.first())

        router.print(usbInfo, image, copies = 9)
        assertEquals(5, usb.sent.size)
    }

    @Test
    fun `no paper on USB is reported as such and nothing is sent`() = runTest {
        val usb = FakeUsbPrinter(status = PrinterStatus.OutOfPaper)

        assertEquals(AppResult.Failure(AppError.PrinterOutOfPaper), router(NoBuiltInPrinter, usb).print(usbInfo, image))
        assertEquals(0, usb.sendCalls)
    }

    @Test
    fun `an open cover or a printer that can't be opened stops the USB job`() = runTest {
        // Offline is also what a printer without permission reads as.
        for (status in listOf(PrinterStatus.CoverOpen, PrinterStatus.Offline)) {
            val usb = FakeUsbPrinter(status = status)

            assertEquals(AppResult.Failure(AppError.PrinterUnavailable), router(NoBuiltInPrinter, usb).print(usbInfo, image))
            assertEquals(0, usb.sendCalls)
        }
    }

    @Test
    fun `the USB status is read at print time, not from when the printer was last looked at`() = runTest {
        val usb = FakeUsbPrinter(status = PrinterStatus.OutOfPaper)

        val result = router(NoBuiltInPrinter, usb).print(usbInfo.copy(status = PrinterStatus.Ready), image)

        assertEquals(AppResult.Failure(AppError.PrinterOutOfPaper), result)
    }

    @Test
    fun `a USB printer that stops taking data fails the job and drops the remaining copies`() = runTest {
        val usb = FakeUsbPrinter(takes = 1)

        val result = router(NoBuiltInPrinter, usb).print(usbInfo, image, copies = 3)

        assertEquals(AppResult.Failure(AppError.PrinterUnavailable), result)
        assertEquals(2, usb.sendCalls)
    }

    @Test(timeout = 10_000)
    fun `looking at the USB printer waits for a job in progress`() = runBlocking {
        val jobStarted = CountDownLatch(1)
        val finishJob = CountDownLatch(1)
        val usb = FakeUsbPrinter(
            beforeSend = {
                jobStarted.countDown()
                finishJob.await()
            },
        )
        val router = router(NoBuiltInPrinter, usb)

        val job = async(Dispatchers.Default) { router.print(usbInfo, image) }
        jobStarted.await()
        val look = async(Dispatchers.Default) { router.current(assignedLanPrinter = null) }
        delay(100)
        val answeredDuringTheJob = look.isCompleted
        finishJob.countDown() // before any assert: runBlocking would otherwise wait for the job forever
        job.await()

        assertFalse(answeredDuringTheJob)
        assertEquals(usbInfo, look.await())
    }

    @Test
    fun `the prompt is shown on request and only then`() = runTest {
        val usb = FakeUsbPrinter()
        val router = router(NoBuiltInPrinter, usb)
        router.current(assignedLanPrinter = null)
        router.print(usbInfo, image)
        assertEquals(0, usb.permissionRequests)

        router.requestUsbPermission()

        assertEquals(1, usb.permissionRequests)
    }
}
