package com.pos.kiosk.core.printer.builtin

import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.imin.printerlib.IminPrintUtils
import com.pos.kiosk.core.printer.EscPosRaster
import com.pos.kiosk.core.printer.MonochromeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * iMin SDK 1.0: Android 11 and below, where the app drives the USB printer itself (D4, D1, D1 Pro, D1w, Falcon 1,
 * M2 Max, Swift 1). Android asks once for the USB permission; the handheld models that print over SPI aren't kiosks
 * and aren't supported, since that path needs the SDK's native library.
 */
internal class IminLegacyBuiltInPrinter(context: Context) : BuiltInPrinter {
    override val brand: String = "iMin"

    private val appContext = context.applicationContext
    private val utils: IminPrintUtils by lazy { IminPrintUtils.getInstance(appContext) }
    private val connectLock = Mutex()

    @Volatile
    private var connected = false

    private var lastAttemptAt = 0L

    override suspend fun connect(): Boolean = connectLock.withLock {
        if (connected) return true
        // A failed attempt may mean a USB permission prompt: don't put it back on screen on every call.
        val now = SystemClock.elapsedRealtime()
        if (lastAttemptAt != 0L && now - lastAttemptAt < RETRY_AFTER_MS) return false
        lastAttemptAt = now
        // LinkageError too: a model the SDK can't drive fails with those, and must not take the kiosk down.
        connected = try {
            withContext(Dispatchers.IO) { utils.initPrinter(CONNECTION) }
            delay(SDK_INIT_WAIT_MS)
            status() != PrinterStatus.Offline
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
        connected
    }

    override fun paperWidthDots(): Int = legacyIminPaperWidthDots(Build.MODEL)

    override fun status(): PrinterStatus {
        val status = try {
            legacyIminStatus(utils.getPrinterStatus(CONNECTION))
        } catch (_: RuntimeException) {
            PrinterStatus.Offline
        }
        if (status == PrinterStatus.Offline) connected = false
        return status
    }

    override fun print(image: MonochromeImage) {
        // Recommended over partialCut(): it waits for the receipt to finish and feeds before cutting.
        utils.sendRAWData(EscPosRaster.encode(image, feedLines = 0, cut = false))
        utils.partialCutPaper()
    }

    private companion object {
        val CONNECTION = IminPrintUtils.PrintConnectType.USB
        const val SDK_INIT_WAIT_MS = 2_000L // from the SDK's documentation
        const val RETRY_AFTER_MS = 60_000L
    }
}

/**
 * Codes as documented for the D4 series; on D1, D1 Pro and M2 Max 7 also covers an open cover, and on S1 8 means paper
 * running out, which can still print. -1 and 1 are "not initialised" and "disconnected or off".
 */
internal fun legacyIminStatus(code: Int): PrinterStatus = when (code) {
    0, 8 -> PrinterStatus.Ready
    3 -> PrinterStatus.CoverOpen
    5 -> PrinterStatus.Overheated
    7 -> PrinterStatus.OutOfPaper
    else -> PrinterStatus.Offline
}

/** SDK 1.0 can't report the paper size. The 58 mm models per iMin's documentation; the rest print on 80 mm. */
internal fun legacyIminPaperWidthDots(model: String): Int =
    if (LEGACY_58MM_MODELS.containsMatchIn(model)) PAPER_58MM_DOTS else PAPER_80MM_DOTS

private val LEGACY_58MM_MODELS = Regex("""^(D1[- p]|M2|Swift)""", RegexOption.IGNORE_CASE)
