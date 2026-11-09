package com.pos.kiosk.core.printer.builtin

import android.content.Context
import com.pos.kiosk.core.printer.EscPosRaster
import com.pos.kiosk.core.printer.MonochromeImage
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.SdkException
import com.sunmi.printerx.enums.PrinterInfo
import com.sunmi.printerx.enums.Status
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sunmi's PrinterX SDK: one API over the print services of all its devices, including the K2 kiosk's.
 * Receipts go out as the same ESC/POS raster the LAN printers get; the SDK only replaces the socket.
 */
internal class SunmiBuiltInPrinter(private val context: Context) : BuiltInPrinter {
    override val brand: String = "Sunmi"

    private val connectLock = Mutex()

    /** Cleared when a call finds the service gone, so the next [connect] binds again. */
    @Volatile
    private var printer: PrinterSdk.Printer? = null

    @Volatile
    private var paperWidth: Int? = null

    override suspend fun connect(): Boolean = connectLock.withLock {
        val current = printer ?: requestDefaultPrinter()?.also { printer = it } ?: return false
        val status = current.readStatus() ?: return false
        // UNKNOWN is how the SDK reports "no printer detected": the variants of a model sold without one.
        status != Status.UNKNOWN
    }

    private suspend fun requestDefaultPrinter(): PrinterSdk.Printer? {
        val result = CompletableDeferred<PrinterSdk.Printer?>()
        val listener = object : PrinterSdk.PrinterListen {
            override fun onDefPrinter(printer: PrinterSdk.Printer?) {
                result.complete(printer)
            }

            override fun onPrinters(printers: MutableList<PrinterSdk.Printer>?) = Unit
        }
        try {
            PrinterSdk.getInstance().getPrinter(context, listener)
        } catch (_: SdkException) {
            return null // "Not found print service"
        }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) { result.await() }
    }

    private fun PrinterSdk.Printer.readStatus(): Status? = try {
        queryApi().status
    } catch (_: SdkException) {
        printer = null
        null
    }

    override fun paperWidthDots(): Int {
        paperWidth?.let { return it }
        val info = try {
            printer?.queryApi()?.getInfo(PrinterInfo.PAPER)
        } catch (_: SdkException) {
            null
        }
        return sunmiPaperWidthDots(info).also { if (!info.isNullOrBlank()) paperWidth = it }
    }

    override fun status(): PrinterStatus = printer?.readStatus()?.let(::sunmiStatus) ?: PrinterStatus.Offline

    override fun print(image: MonochromeImage) {
        val printer = checkNotNull(printer) { "Not connected" }
        // autoOut() feeds the paper to the cutter or tear bar and cuts where the device has a cutter.
        printer.commandApi().sendEscCommand(EscPosRaster.encode(image, feedLines = 0, cut = false))
        printer.lineApi().autoOut()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}

internal fun sunmiStatus(status: Status): PrinterStatus = when (status) {
    Status.READY, Status.WARN_THERMAL_PAPER -> PrinterStatus.Ready
    Status.ERR_PAPER_OUT, Status.ERR_PAPER_JAM -> PrinterStatus.OutOfPaper
    Status.ERR_COVER, Status.ERR_COVER_INCOMPLETE -> PrinterStatus.CoverOpen
    Status.ERR_PRINTER_HOT, Status.ERR_MOTOR_HOT -> PrinterStatus.Overheated
    else -> PrinterStatus.Offline
}

/**
 * The SDK answers in dots ("384", "576") and leaves it empty when the service can't tell; the newest service may answer
 * in millimetres. Unknown prints at the narrow width, which fits either head; the wide one would cut a 58 mm receipt off.
 */
internal fun sunmiPaperWidthDots(info: String?): Int = when (info?.filter(Char::isDigit)?.toIntOrNull()) {
    PAPER_80MM_DOTS, 80 -> PAPER_80MM_DOTS
    else -> PAPER_58MM_DOTS
}
