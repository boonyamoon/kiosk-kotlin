package com.pos.kiosk.core.printer

/**
 * 1-bit image already packed the way ESC/POS raster data is sent: [bytesPerRow] bytes per row,
 * most significant bit = leftmost dot, 1 = black. Packing up front keeps a long receipt at 1 bit per
 * dot (72 bytes per 80 mm row) instead of a byte or an int per dot. Plain Kotlin so it is unit-testable.
 */
class MonochromeImage(val width: Int, val height: Int, val rows: ByteArray) {
    val bytesPerRow: Int = (width + 7) / 8

    init {
        require(rows.size == bytesPerRow * height) { "rows must be bytesPerRow*height" }
    }

    operator fun get(x: Int, y: Int): Boolean = rows[y * bytesPerRow + x / 8].toInt() and (0x80 ushr (x % 8)) != 0

    companion object {
        /** Packs a row-major pixel array (`true` = black dot); convenient for small images and tests. */
        fun fromPixels(width: Int, height: Int, pixels: BooleanArray): MonochromeImage {
            require(pixels.size == width * height) { "pixels must be width*height" }
            val bytesPerRow = (width + 7) / 8
            val rows = ByteArray(bytesPerRow * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (pixels[y * width + x]) {
                        val i = y * bytesPerRow + x / 8
                        rows[i] = (rows[i].toInt() or (0x80 ushr (x % 8))).toByte()
                    }
                }
            }
            return MonochromeImage(width, height, rows)
        }
    }
}

/**
 * Encodes an image as ESC/POS raster graphics (GS v 0). Receipts are printed as images rather
 * than text because thermal printers' Thai code pages mangle stacked vowels/tone marks.
 */
object EscPosRaster {
    /** Many printers cap the height of one GS v 0 block; 255 rows is safe everywhere. */
    private const val BAND_HEIGHT = 255
    private const val BAND_HEADER_BYTES = 8

    fun encode(image: MonochromeImage, feedLines: Int = 4, cut: Boolean = true): ByteArray {
        val bytesPerRow = image.bytesPerRow
        val bands = (image.height + BAND_HEIGHT - 1) / BAND_HEIGHT
        // Exact size up front: one allocation, bulk copies, no per-byte stream writes.
        val out = ByteArray(2 + bands * BAND_HEADER_BYTES + image.rows.size + 3 + if (cut) 4 else 0)
        var pos = 0
        fun put(vararg bytes: Int) {
            for (b in bytes) out[pos++] = b.toByte()
        }

        put(0x1B, 0x40) // ESC @ — initialize
        var top = 0
        while (top < image.height) {
            val bandHeight = minOf(BAND_HEIGHT, image.height - top)
            put(0x1D, 0x76, 0x30, 0x00) // GS v 0, normal density
            put(bytesPerRow and 0xFF, (bytesPerRow shr 8) and 0xFF, bandHeight and 0xFF, (bandHeight shr 8) and 0xFF)
            val length = bandHeight * bytesPerRow
            image.rows.copyInto(out, destinationOffset = pos, startIndex = top * bytesPerRow, endIndex = top * bytesPerRow + length)
            pos += length
            top += bandHeight
        }

        put(0x1B, 0x64, feedLines.coerceIn(0, 255)) // ESC d n — feed
        if (cut) put(0x1D, 0x56, 0x42, 0x00) // GS V B 0 — partial cut
        check(pos == out.size)
        return out
    }
}
