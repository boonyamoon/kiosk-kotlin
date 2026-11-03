package com.pos.kiosk.core.printer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosRasterTest {

    @Test
    fun `encodes header, packed rows, feed and cut`() {
        // 10 x 2 image: row 0 = first and last pixel black, row 1 = all white
        val pixels = BooleanArray(20).apply {
            this[0] = true
            this[9] = true
        }
        val bytes = EscPosRaster.encode(MonochromeImage.fromPixels(10, 2, pixels), feedLines = 3, cut = true)

        val expected = byteArrayOf(
            0x1B, 0x40, // ESC @
            0x1D, 0x76, 0x30, 0x00, // GS v 0
            2, 0, // 2 bytes per row
            2, 0, // 2 rows
            0x80.toByte(), 0x40, // row 0: bit 0 and bit 9
            0x00, 0x00, // row 1
            0x1B, 0x64, 3, // ESC d 3
            0x1D, 0x56, 0x42, 0x00, // GS V B 0
        )
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun `tall images are split into bands of at most 255 rows`() {
        val image = MonochromeImage.fromPixels(8, 600, BooleanArray(8 * 600))
        val bytes = EscPosRaster.encode(image, cut = false)

        val headers = bytes.indices.count { i ->
            i + 3 < bytes.size && bytes[i] == 0x1D.toByte() && bytes[i + 1] == 0x76.toByte() && bytes[i + 2] == 0x30.toByte()
        }
        assertEquals(3, headers) // 255 + 255 + 90
        // init + 3 band headers + 600 one-byte rows + feed, nothing more or less
        assertEquals(2 + 3 * 8 + 600 + 3, bytes.size)
    }

    @Test
    fun `band data is copied in order across band boundaries`() {
        // 8 x 300: band 1 = rows 0..254, band 2 = rows 255..299; only row 255 (first row of band 2) is black
        val pixels = BooleanArray(8 * 300).apply { fill(true, 8 * 255, 8 * 256) }
        val bytes = EscPosRaster.encode(MonochromeImage.fromPixels(8, 300, pixels), cut = false)

        val secondBandData = 2 + 8 + 255 + 8 // init, band 1 header + rows, band 2 header
        assertEquals(0x00.toByte(), bytes[secondBandData - 9]) // row 254, last row of band 1
        assertEquals(0xFF.toByte(), bytes[secondBandData]) // row 255
        assertEquals(0x00.toByte(), bytes[secondBandData + 1]) // row 256
    }

    @Test
    fun `packed image reads back the same pixels`() {
        val pixels = BooleanArray(13 * 3) { it % 3 == 0 }
        val image = MonochromeImage.fromPixels(13, 3, pixels)

        assertEquals(2, image.bytesPerRow)
        for (y in 0 until 3) {
            for (x in 0 until 13) {
                if (pixels[y * 13 + x]) assertTrue("($x,$y)", image[x, y]) else assertFalse("($x,$y)", image[x, y])
            }
        }
    }
}
