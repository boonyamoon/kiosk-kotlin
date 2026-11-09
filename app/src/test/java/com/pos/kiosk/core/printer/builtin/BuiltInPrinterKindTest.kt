package com.pos.kiosk.core.printer.builtin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInPrinterKindTest {
    private fun device(manufacturer: String, brand: String = manufacturer, sdkInt: Int = 33, hasIminPrintService: Boolean = false) =
        DeviceProfile(manufacturer, brand, sdkInt, hasIminPrintService)

    @Test
    fun `Sunmi devices use PrinterX on every Android version`() {
        assertEquals(BuiltInPrinterKind.Sunmi, device("SUNMI", sdkInt = 28).builtInPrinterKind())
        assertEquals(BuiltInPrinterKind.Sunmi, device("SUNMI", sdkInt = 33).builtInPrinterKind())
    }

    @Test
    fun `iMin picks its SDK by Android version`() {
        assertEquals(BuiltInPrinterKind.Imin, device("iMin", hasIminPrintService = true).builtInPrinterKind())
        assertEquals(BuiltInPrinterKind.IminLegacy, device("iMin", sdkInt = 30).builtInPrinterKind())
        assertEquals(BuiltInPrinterKind.IminLegacy, device("iMin", sdkInt = 30, hasIminPrintService = true).builtInPrinterKind())
    }

    @Test
    fun `old iMin firmware may carry the company's former name`() {
        assertEquals(BuiltInPrinterKind.IminLegacy, device("NEOSTRA", sdkInt = 30).builtInPrinterKind())
    }

    @Test
    fun `an iMin on a newer Android without the print service has no printer to reach`() {
        assertEquals(BuiltInPrinterKind.None, device("iMin", sdkInt = 33).builtInPrinterKind())
    }

    @Test
    fun `every other device stays on LAN printers`() {
        assertEquals(BuiltInPrinterKind.None, device("TCL", sdkInt = 29).builtInPrinterKind())
        assertEquals(BuiltInPrinterKind.None, device("Google", "google").builtInPrinterKind())
    }

    @Test
    fun `USB printers are only looked for on devices with no printer inside`() {
        assertTrue(BuiltInPrinterKind.None.leavesRoomForUsbPrinter)
        for (kind in listOf(BuiltInPrinterKind.Sunmi, BuiltInPrinterKind.Imin, BuiltInPrinterKind.IminLegacy)) {
            assertFalse(kind.name, kind.leavesRoomForUsbPrinter)
        }
    }
}
