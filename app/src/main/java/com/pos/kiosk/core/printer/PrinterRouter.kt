package com.pos.kiosk.core.printer

import com.pos.kiosk.core.common.AppError
import com.pos.kiosk.core.common.AppResult
import com.pos.kiosk.core.printer.builtin.BuiltInPrinter
import com.pos.kiosk.core.printer.builtin.PAPER_58MM_DOTS
import com.pos.kiosk.core.printer.builtin.PrinterStatus
import com.pos.kiosk.core.printer.usb.UsbPaperSetting
import com.pos.kiosk.core.printer.usb.UsbPrinter
import com.pos.kiosk.domain.model.ReceiptPrinterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The printer this kiosk uses right now. */
sealed interface PrinterInfo {
    val paperWidthDots: Int
    val paperMm: Int get() = if (paperWidthDots == PAPER_58MM_DOTS) 58 else 80

    /** Assigned to this kiosk in the back office. */
    data class Lan(val config: ReceiptPrinterConfig) : PrinterInfo {
        override val paperWidthDots: Int get() = config.paperWidthDots
    }

    /** Inside the device, found on its own: an all-in-one POS needs no printer setup. */
    data class BuiltIn(val brand: String, override val paperWidthDots: Int, val status: PrinterStatus) : PrinterInfo

    /** Plugged into the device, found on its own. Until [permitted] it can't print: staff allow it in the admin menu. */
    data class Usb(val name: String, override val paperWidthDots: Int, val status: PrinterStatus, val permitted: Boolean) : PrinterInfo
}

/**
 * Picks the printer for a job. A LAN printer assigned to this kiosk wins, since someone chose it; otherwise the
 * device's own printer, then one plugged in over USB. No printer at all is a normal state: the customer writes down
 * the queue number.
 */
@Singleton
class PrinterRouter @Inject constructor(
    private val lan: ReceiptPrinter,
    private val builtIn: BuiltInPrinter,
    private val usb: UsbPrinter,
    private val usbPaper: UsbPaperSetting,
) {
    /** One job at a time, as on the LAN printer: a test page and a receipt must not interleave. */
    private val builtInLock = Mutex()

    /** Reading the status opens the port too, so it waits for a job in progress. */
    private val usbLock = Mutex()

    suspend fun current(assignedLanPrinter: ReceiptPrinterConfig?): PrinterInfo? {
        if (assignedLanPrinter != null) return PrinterInfo.Lan(assignedLanPrinter)
        return withContext(Dispatchers.IO) {
            if (!builtIn.connect()) return@withContext currentUsb()
            var status = builtIn.status()
            // A print service that died is only noticed here; the second connect binds it again.
            if (status == PrinterStatus.Offline && builtIn.connect()) status = builtIn.status()
            PrinterInfo.BuiltIn(builtIn.brand, builtIn.paperWidthDots(), status)
        }
    }

    suspend fun print(printer: PrinterInfo, image: MonochromeImage, copies: Int = 1): AppResult<Unit> = when (printer) {
        is PrinterInfo.Lan -> lan.print(PrinterTarget(printer.config.host, printer.config.port), image, copies)
        is PrinterInfo.BuiltIn -> printOnBuiltIn(image, copies)
        is PrinterInfo.Usb -> printOnUsb(image, copies)
    }

    fun requestUsbPermission() = usb.requestPermission()

    suspend fun setUsbPaperWidth(widthDots: Int) = usbPaper.setWidthDots(widthDots)

    private suspend fun currentUsb(): PrinterInfo.Usb? = usbLock.withLock {
        val attached = usb.attached() ?: return null
        PrinterInfo.Usb(
            name = attached.name,
            paperWidthDots = usbPaper.widthDots(),
            status = if (attached.permitted) usb.status() else PrinterStatus.Offline,
            permitted = attached.permitted,
        )
    }

    private suspend fun printOnBuiltIn(image: MonochromeImage, copies: Int): AppResult<Unit> = builtInLock.withLock {
        withContext(Dispatchers.IO) {
            when (val status = builtIn.status()) {
                PrinterStatus.Ready -> try {
                    repeat(copies.coerceIn(1, 3)) { builtIn.print(image) }
                    AppResult.Success(Unit)
                } catch (_: Exception) {
                    AppResult.Failure(AppError.PrinterUnavailable)
                }
                else -> notReady(status)
            }
        }
    }

    private suspend fun printOnUsb(image: MonochromeImage, copies: Int): AppResult<Unit> {
        val payload = withContext(Dispatchers.Default) { EscPosRaster.encode(image) }
        return usbLock.withLock {
            withContext(Dispatchers.IO) {
                when (val status = usb.status()) {
                    PrinterStatus.Ready -> {
                        val sent = (1..copies.coerceIn(1, 3)).all { usb.send(payload) }
                        if (sent) AppResult.Success(Unit) else AppResult.Failure(AppError.PrinterUnavailable)
                    }
                    else -> notReady(status)
                }
            }
        }
    }

    private fun notReady(status: PrinterStatus): AppResult<Unit> = AppResult.Failure(
        if (status == PrinterStatus.OutOfPaper) AppError.PrinterOutOfPaper else AppError.PrinterUnavailable,
    )
}
