package com.mireli.driver.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleTest {
    @Test
    fun `dark theme swaps the published style, light keeps it`() {
        val light = mapStyleUrl(dark = false)
        val dark = mapStyleUrl(dark = true)
        assertEquals(com.mireli.driver.BuildConfig.MAP_STYLE_URL, light)
        assertTrue("dark style should differ from light: $dark", dark != light)
        assertTrue("dark style should be an openfreemap style URL: $dark", dark.startsWith("https://"))
    }
}
