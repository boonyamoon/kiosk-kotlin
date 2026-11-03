package com.pos.kiosk.feature.attract

import org.junit.Assert.assertEquals
import org.junit.Test

class FitModeTest {
    private val tablet = 16f / 10f       // 1920×1200 landscape
    private val kioskScreen = 16f / 9f   // 1920×1080 landscape
    private val portrait = 9f / 16f      // 1080×1920

    @Test
    fun `the recommended 1920x1200 design fills both common landscape screens`() {
        assertEquals(FitMode.Fill, fitMode(1920f / 1200f, tablet))
        assertEquals(FitMode.Fill, fitMode(1920f / 1200f, kioskScreen))
    }

    @Test
    fun `a 3-2 camera photo fills a 16-10 tablet (6 percent off) but not a 16-9 screen (16 percent off)`() {
        assertEquals(FitMode.Fill, fitMode(3f / 2f, tablet))
        assertEquals(FitMode.Letterbox, fitMode(3f / 2f, kioskScreen))
        // 4:3 is 17 % away from 16:10: shown whole rather than losing a sixth of the picture.
        assertEquals(FitMode.Letterbox, fitMode(4f / 3f, tablet))
    }

    @Test
    fun `a landscape image on a portrait kiosk is shown whole, and vice versa`() {
        assertEquals(FitMode.Letterbox, fitMode(1920f / 1200f, portrait))
        assertEquals(FitMode.Letterbox, fitMode(1200f / 1920f, tablet))
        assertEquals(FitMode.Fill, fitMode(1200f / 1920f, portrait))
    }

    @Test
    fun `a 3-1 banner never fills a full screen`() {
        assertEquals(FitMode.Letterbox, fitMode(3f, kioskScreen))
    }
}
