package com.pos.kiosk.core.printer.usb

import com.pos.kiosk.core.printer.builtin.PrinterStatus

/** What can be told about a USB printer without opening it. */
data class AttachedUsbPrinter(val name: String, val permitted: Boolean)

/**
 * A receipt printer plugged into the device. It has no address to set on the iPad, so the kiosk finds it by itself;
 * any ESC/POS printer that is a USB printer-class device works, with no vendor SDK in between.
 *
 * [status] and [send] block on the USB port: call them off the main thread, one at a time.
 */
interface UsbPrinter {
    /** The printer on the USB port right now, if any. Asks the user nothing. */
    fun attached(): AttachedUsbPrinter?

    /** Never throws. [PrinterStatus.Offline] when the printer can't be opened; one that won't say reads as ready. */
    fun status(): PrinterStatus

    /** Hands one receipt's bytes to the printer. Never throws: false when it stopped taking them. */
    fun send(payload: ByteArray): Boolean

    /**
     * Shows Android's prompt. For staff only: customers must never see it, and it can't open over a pinned or locked
     * app. No answer comes back here; the admin menu reads [attached] again when the kiosk resumes.
     */
    fun requestPermission()
}

/** A device with a printer of its own: that one is used, and the one inside an old iMin is itself a USB device. */
object NoUsbPrinter : UsbPrinter {
    override fun attached(): AttachedUsbPrinter? = null

    override fun status(): PrinterStatus = PrinterStatus.Offline

    override fun send(payload: ByteArray): Boolean = false

    override fun requestPermission() = Unit
}
