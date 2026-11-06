package com.pos.kiosk.core.printer.builtin

import android.content.Context
import com.imin.printer.InitPrinterCallback
import com.imin.printer.PrinterHelper
import com.pos.kiosk.core.printer.EscPosRaster
import com.pos.kiosk.core.printer.MonochromeImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** iMin SDK 2.0: the print service of its Android 13 devices (D4 Pro, Swan 2, Falcon 2, Swift 2). */
internal class IminBuiltInPrinter(private val context: Context) : BuiltInPrinter {
    override val brand: String = "iMin"

    private val helper: PrinterHelper get() = PrinterHelper.getInstance()
    private val connectLock = Mutex()

    @Volatile
    private var connected = false

    override suspend fun connect(): Boolean = connectLock.withLock {
        if (connected) return true
        val bound = CompletableDeferred<Boolean>()
        val binding = helper.initPrinterService(
            context,
            object : InitPrinterCallback {
                override fun onConnected() {
                    bound.complete(true)
                }

                override fun onDisconnected() {
                    connected = false
                    bound.complete(false)
                }
            },
        )
        // onConnected() comes after PrinterHelper has registered this app with the service.
        connected = binding && withTimeoutOrNull(CONNECT_TIMEOUT_MS) { bound.await() } == true
        connected
    }

    override fun paperWidthDots(): Int = iminPaperWidthDots(helper.printerPaperType)

    override fun status(): PrinterStatus {
        val code = helper.printerStatus
        if (code == STATUS_NO_SERVICE) connected = false
        return iminStatus(code)
    }

    override fun print(image: MonochromeImage) {
        // Cut through the SDK: iMin's own ESC/POS firmware differs from the standard on cut commands. The feed clears
        // the end of the receipt past the cutter blade, which sits above the print head.
        helper.sendRAWData(EscPosRaster.encode(image, feedLines = FEED_LINES_BEFORE_CUT, cut = false), null)
        helper.partialCut()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val FEED_LINES_BEFORE_CUT = 4
    }
}

internal const val STATUS_NO_SERVICE = -1

/** Only the codes iMin documents as faults stop a print; anything else is worth trying. */
internal fun iminStatus(code: Int): PrinterStatus = when (code) {
    STATUS_NO_SERVICE -> PrinterStatus.Offline
    3 -> PrinterStatus.CoverOpen
    4 -> PrinterStatus.Overheated
    7 -> PrinterStatus.OutOfPaper
    else -> PrinterStatus.Ready
}

/** 80 or 58; anything else prints at the narrow width, which fits either head. */
internal fun iminPaperWidthDots(paperType: Int): Int = if (paperType == 80) PAPER_80MM_DOTS else PAPER_58MM_DOTS
