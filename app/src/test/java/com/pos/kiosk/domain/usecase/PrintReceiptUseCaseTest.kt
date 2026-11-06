package com.pos.kiosk.domain.usecase

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.printer.EscPosRaster
import com.pos.kiosk.core.printer.MonochromeImage
import com.pos.kiosk.core.printer.PrinterInfo
import com.pos.kiosk.core.printer.PrinterRouter
import com.pos.kiosk.core.printer.ReceiptPrinter
import com.pos.kiosk.core.printer.ReceiptRenderer
import com.pos.kiosk.core.printer.builtin.BuiltInPrinter
import com.pos.kiosk.core.printer.builtin.NoBuiltInPrinter
import com.pos.kiosk.core.printer.builtin.PAPER_58MM_DOTS
import com.pos.kiosk.core.printer.builtin.PrinterStatus
import com.pos.kiosk.core.printer.usb.AttachedUsbPrinter
import com.pos.kiosk.core.printer.usb.FakeUsbPaperSetting
import com.pos.kiosk.core.printer.usb.FakeUsbPrinter
import com.pos.kiosk.core.printer.usb.NoUsbPrinter
import com.pos.kiosk.core.printer.usb.UsbPrinter
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.Receipt
import com.pos.kiosk.domain.model.ReceiptPrinterConfig
import com.pos.kiosk.domain.repository.ConfigRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintReceiptUseCaseTest {
    private val image = MonochromeImage.fromPixels(8, 1, BooleanArray(8))
    private val receipt = mockk<Receipt>()
    private val renderer = mockk<ReceiptRenderer> {
        every { render(any(), any(), any()) } returns image
        every { renderTestPage(any(), any()) } returns image
    }
    private val lan = mockk<ReceiptPrinter> { coEvery { print(any(), any(), any()) } returns AppResult.Success(Unit) }
    private val builtIn = mockk<BuiltInPrinter>(relaxed = true) {
        coEvery { connect() } returns true
        every { brand } returns "iMin"
        every { paperWidthDots() } returns PAPER_58MM_DOTS
        every { status() } returns PrinterStatus.Ready
    }

    private val paper = FakeUsbPaperSetting()

    private fun useCase(lanPrinter: ReceiptPrinterConfig?, device: BuiltInPrinter = builtIn, usb: UsbPrinter = NoUsbPrinter): PrintReceiptUseCase {
        val config = mockk<KioskConfig> { every { receiptPrinter } returns lanPrinter }
        val repository = mockk<ConfigRepository> { every { this@mockk.config } returns MutableStateFlow(config) }
        return PrintReceiptUseCase(repository, renderer, PrinterRouter(lan, device, usb, paper))
    }

    @Test
    fun `an all-in-one with no printer assigned prints at its own paper width`() = runTest {
        val useCase = useCase(lanPrinter = null)

        assertEquals(AppResult.Success(Unit), useCase(receipt))

        verify { renderer.render(receipt, any(), PAPER_58MM_DOTS) }
        verify { builtIn.print(image) }
        coVerify(exactly = 0) { lan.print(any(), any(), any()) }
    }

    @Test
    fun `an assigned LAN printer keeps its width and copies`() = runTest {
        val assigned = ReceiptPrinterConfig("Front", "192.168.1.50", 9100, "80mm", copyCount = 2)

        useCase(assigned)(receipt)

        verify { renderer.render(receipt, any(), 576) }
        coVerify { lan.print(any(), image, 2) }
        verify(exactly = 0) { builtIn.print(any()) }
    }

    @Test
    fun `without any printer the print fails and hasPrinter says so`() = runTest {
        val useCase = useCase(lanPrinter = null, device = NoBuiltInPrinter)

        assertFalse(useCase.hasPrinter())
        assertEquals(AppResult.Failure(AppError.PrinterUnavailable), useCase(receipt))
    }

    @Test
    fun `a USB printer prints at the paper width staff set, one copy`() = runTest {
        val usb = FakeUsbPrinter()
        val useCase = useCase(lanPrinter = null, device = NoBuiltInPrinter, usb = usb)

        useCase.setUsbPaperWidth(PAPER_58MM_DOTS)
        assertEquals(AppResult.Success(Unit), useCase(receipt))

        verify { renderer.render(receipt, any(), PAPER_58MM_DOTS) }
        assertEquals(1, usb.sent.size)
    }

    @Test
    fun `a USB printer nobody has allowed counts as a printer, so the failure reaches staff`() = runTest {
        val usb = FakeUsbPrinter(printer = AttachedUsbPrinter("XP-80C", permitted = false), status = PrinterStatus.Offline)
        val useCase = useCase(lanPrinter = null, device = NoBuiltInPrinter, usb = usb)

        assertTrue(useCase.hasPrinter())
        assertEquals(AppResult.Failure(AppError.PrinterUnavailable), useCase(receipt))
    }

    @Test
    fun `the admin menu reaches the USB prompt through the use case`() {
        val usb = FakeUsbPrinter()

        useCase(lanPrinter = null, device = NoBuiltInPrinter, usb = usb).requestUsbPermission()

        assertEquals(1, usb.permissionRequests)
    }

    @Test
    fun `the test page goes through the same printer`() = runTest {
        val useCase = useCase(lanPrinter = null)

        assertTrue(useCase.hasPrinter())
        useCase.printTestPage()

        verify { renderer.renderTestPage(any(), match { it is PrinterInfo.BuiltIn && it.brand == "iMin" }) }
        verify { builtIn.print(image) }
    }

    @Test
    fun `built-in printers get the raster without a cut, which the vendor SDK does itself`() {
        val withCut = EscPosRaster.encode(image, feedLines = 0, cut = true)
        val withoutCut = EscPosRaster.encode(image, feedLines = 0, cut = false)

        assertArrayEquals(withoutCut, withCut.copyOf(withCut.size - 4))
    }
}
