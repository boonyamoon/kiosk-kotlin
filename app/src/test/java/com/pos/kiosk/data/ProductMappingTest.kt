package com.pos.kiosk.data

import com.pos.kiosk.data.remote.dto.ProductDto
import com.pos.kiosk.data.remote.toDetail
import com.pos.kiosk.data.remote.toProduct
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMappingTest {
    // Same settings as NetworkModule's Json.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun product(serviceCharge: String?) = json.decodeFromString<ProductDto>(
        """{"id":"p","product_name":"Latte"${serviceCharge?.let { ""","service_charge":$it""" } ?: ""}}""",
    )

    @Test
    fun `reads the menu's per-product service charge flag`() {
        assertTrue(product("true").toProduct().serviceCharge)
        assertTrue(product("true").toDetail().serviceCharge)
    }

    @Test
    fun `a missing or null flag means no service charge`() {
        assertFalse(product(null).toProduct().serviceCharge)
        assertFalse(product("null").toProduct().serviceCharge)
        assertFalse(product("false").toDetail().serviceCharge)
    }
}
