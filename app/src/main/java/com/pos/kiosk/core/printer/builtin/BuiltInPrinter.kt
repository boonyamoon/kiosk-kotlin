package com.pos.kiosk.core.printer.builtin

import com.pos.kiosk.core.printer.MonochromeImage

/** Printable width of the two thermal heads, in dots (8 dots per mm). */
const val PAPER_58MM_DOTS = 384
const val PAPER_80MM_DOTS = 576

/** What a printer reports, in the kiosk's terms: enough to tell staff what to fix. */
enum class PrinterStatus { Ready, OutOfPaper, CoverOpen, Overheated, Offline }

/**
 * The printer inside an all-in-one POS (Sunmi, iMin). It has no network address: the app reaches it through the
 * vendor's SDK, so each SDK gets one implementation in this package and no vendor type leaves it.
 *
 * [paperWidthDots], [status] and [print] block on the SDK: call them off the main thread, after [connect].
 */
interface BuiltInPrinter {
    /** Shown to staff, e.g. "Sunmi". */
    val brand: String

    /** Reaches the printer, binding the vendor's service when needed. False when this device can't print. */
    suspend fun connect(): Boolean

    fun paperWidthDots(): Int

    /** Never throws: anything unexpected reads as [PrinterStatus.Offline]. */
    fun status(): PrinterStatus

    /** Hands one receipt to the printer and cuts it. Throws when the SDK refuses the job. */
    fun print(image: MonochromeImage)
}

/** Every other device (tablets, phones): receipts go to a LAN printer, or aren't printed at all. */
object NoBuiltInPrinter : BuiltInPrinter {
    override val brand: String = ""

    override suspend fun connect(): Boolean = false

    override fun paperWidthDots(): Int = PAPER_80MM_DOTS

    override fun status(): PrinterStatus = PrinterStatus.Offline

    override fun print(image: MonochromeImage): Unit = throw UnsupportedOperationException("This device has no printer")
}
