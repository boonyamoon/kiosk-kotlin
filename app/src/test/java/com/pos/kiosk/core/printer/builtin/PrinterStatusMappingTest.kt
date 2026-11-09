package com.pos.kiosk.core.printer.builtin

import com.sunmi.printerx.enums.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class PrinterStatusMappingTest {
    @Test
    fun `Sunmi statuses`() {
        assertEquals(PrinterStatus.Ready, sunmiStatus(Status.READY))
        assertEquals(PrinterStatus.OutOfPaper, sunmiStatus(Status.ERR_PAPER_OUT))
        assertEquals(PrinterStatus.CoverOpen, sunmiStatus(Status.ERR_COVER))
        assertEquals(PrinterStatus.Overheated, sunmiStatus(Status.ERR_PRINTER_HOT))
        assertEquals(PrinterStatus.Offline, sunmiStatus(Status.OFFLINE))
        assertEquals(PrinterStatus.Offline, sunmiStatus(Status.UNKNOWN))
    }

    @Test
    fun `Sunmi paper width, narrow when the service can't tell`() {
        assertEquals(PAPER_80MM_DOTS, sunmiPaperWidthDots("576"))
        assertEquals(PAPER_58MM_DOTS, sunmiPaperWidthDots("384"))
        assertEquals(PAPER_80MM_DOTS, sunmiPaperWidthDots("80mm"))
        assertEquals(PAPER_58MM_DOTS, sunmiPaperWidthDots(""))
        assertEquals(PAPER_58MM_DOTS, sunmiPaperWidthDots(null))
    }

    @Test
    fun `iMin SDK 2 statuses`() {
        assertEquals(PrinterStatus.Ready, iminStatus(0))
        assertEquals(PrinterStatus.CoverOpen, iminStatus(3))
        assertEquals(PrinterStatus.Overheated, iminStatus(4))
        assertEquals(PrinterStatus.OutOfPaper, iminStatus(7))
        assertEquals(PrinterStatus.Offline, iminStatus(-1))
    }

    @Test
    fun `iMin SDK 2 paper width`() {
        assertEquals(PAPER_80MM_DOTS, iminPaperWidthDots(80))
        assertEquals(PAPER_58MM_DOTS, iminPaperWidthDots(58))
        assertEquals(PAPER_58MM_DOTS, iminPaperWidthDots(-1)) // no service: the width that fits either head
    }

    @Test
    fun `iMin SDK 1 statuses`() {
        assertEquals(PrinterStatus.Ready, legacyIminStatus(0))
        assertEquals(PrinterStatus.Ready, legacyIminStatus(8)) // S1: paper running low, still printing
        assertEquals(PrinterStatus.CoverOpen, legacyIminStatus(3))
        assertEquals(PrinterStatus.Overheated, legacyIminStatus(5))
        assertEquals(PrinterStatus.OutOfPaper, legacyIminStatus(7))
        assertEquals(PrinterStatus.Offline, legacyIminStatus(1))
        assertEquals(PrinterStatus.Offline, legacyIminStatus(-1))
    }

    @Test
    fun `iMin SDK 1 paper width comes from the model name`() {
        listOf("D1-501", "D1p-603", "D1 Pro", "M2-203", "M2-Max", "Swift 1").forEach {
            assertEquals(it, PAPER_58MM_DOTS, legacyIminPaperWidthDots(it))
        }
        listOf("D4-504", "D1w-702", "Falcon 1", "Swan 1", "K1-101", "S1-701").forEach {
            assertEquals(it, PAPER_80MM_DOTS, legacyIminPaperWidthDots(it))
        }
    }
}
