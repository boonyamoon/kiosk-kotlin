package com.pos.kiosk.data

import com.pos.kiosk.data.remote.dto.KioskConfigDto
import com.pos.kiosk.data.remote.toDomain
import com.pos.kiosk.domain.model.DisplayImage
import com.pos.kiosk.domain.model.KioskConfig
import com.pos.kiosk.domain.model.KioskDisplay
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KioskDisplayMappingTest {
    // Same settings as NetworkModule's Json.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun config(display: String?): KioskConfig {
        val displayField = display?.let { ""","display":$it""" } ?: ""
        val body = """{"terminal":{"terminal_id":"t","terminal_code":"K1","terminal_name":"Kiosk"},
            "branch":{"business_id":"b","branch_id":"br"},"ordering":{},"tax":{},"payment_methods":{}$displayField}"""
        return json.decodeFromString<KioskConfigDto>(body).toDomain()
    }

    @Test
    fun `a backend without the display block gives the defaults`() {
        val display = config(display = null).display
        assertEquals(KioskDisplay(), display)
        assertFalse(display.screensaver.active)
        assertTrue(display.allImageUrls.isEmpty())
    }

    @Test
    fun `maps background, screensaver and banners in order`() {
        val display = config(
            """{"background_image_url":"https://cdn/bg.webp",
                "screensaver":{"enabled":true,"idle_seconds":45,"slide_seconds":6,"images":[{"id":"s1","url":"https://cdn/1.webp"},{"id":"s2","url":"https://cdn/2.webp"}]},
                "banners":{"slide_seconds":4,"images":[{"id":"b1","url":"https://cdn/b1.webp"}]}}""",
        ).display

        assertEquals("https://cdn/bg.webp", display.backgroundImageUrl)
        assertTrue(display.screensaver.active)
        assertEquals(45, display.screensaver.idleSeconds)
        assertEquals(6, display.screensaver.slideSeconds)
        assertEquals(listOf(DisplayImage("s1", "https://cdn/1.webp"), DisplayImage("s2", "https://cdn/2.webp")), display.screensaver.images)
        assertEquals(4, display.banners.slideSeconds)
        assertEquals(
            listOf("https://cdn/bg.webp", "https://cdn/1.webp", "https://cdn/2.webp", "https://cdn/b1.webp"),
            display.allImageUrls,
        )
    }

    @Test
    fun `kiosk defaults offer both order types, matching pos-services`() {
        val config = config(display = null)
        assertTrue(config.allowDineIn)
        assertTrue(config.allowTakeaway)
    }

    @Test
    fun `out-of-range seconds are clamped so the kiosk never spins or stalls`() {
        val display = config(
            """{"screensaver":{"enabled":true,"idle_seconds":0,"slide_seconds":9999,"images":[]},"banners":{"slide_seconds":1,"images":[]}}""",
        ).display
        assertEquals(10, display.screensaver.idleSeconds)
        assertEquals(60, display.screensaver.slideSeconds)
        assertEquals(3, display.banners.slideSeconds)
    }

    @Test
    fun `an enabled screensaver without images stays off, blank and duplicate images are dropped`() {
        val display = config(
            """{"background_image_url":"","screensaver":{"enabled":true,"images":[{"id":"a","url":""},{"id":"b","url":"https://cdn/b.webp"},{"id":"b","url":"https://cdn/b2.webp"}]}}""",
        ).display
        assertEquals(null, display.backgroundImageUrl)
        assertEquals(listOf(DisplayImage("b", "https://cdn/b.webp")), display.screensaver.images)

        assertFalse(config("""{"screensaver":{"enabled":true,"images":[]}}""").display.screensaver.active)
    }
}
