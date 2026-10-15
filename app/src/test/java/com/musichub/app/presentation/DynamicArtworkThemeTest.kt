package com.musichub.app.presentation

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DynamicArtworkThemeTest {

    @Before
    fun setup() {
        DynamicColorCache.clear()
    }

    @Test
    fun testDefaultDarkFallbackHasHighContrastProperties() {
        val dark = DynamicArtworkColors.DefaultDark

        // Max RGB component should be <= 0.35f to guarantee deep darkness for readability
        val maxComponent = maxOf(dark.topGradientColor.red, dark.topGradientColor.green, dark.topGradientColor.blue)
        assertTrue("Top gradient components must be dark (<= 0.35f) for high text contrast", maxComponent <= 0.35f)
        assertTrue("Ambient glow alpha must be translucent", dark.ambientGlowColor.alpha in 0.1f..0.6f)
    }

    @Test
    fun testCachePutAndGet() {
        val testUrl = "content://media/external/audio/albumart/42"
        val customColors = DynamicArtworkColors(
            primaryColor = Color(0xFFFF4081),
            topGradientColor = Color(0xFF2B1218),
            midGradientColor = Color(0xFF1A0A0E),
            bottomGradientColor = Color(0xFF0C0507),
            ambientGlowColor = Color(0xFFFF4081).copy(alpha = 0.35f),
            accentColor = Color(0xFFFF5252)
        )

        assertNull(DynamicColorCache.get(testUrl))
        DynamicColorCache.put(testUrl, customColors)

        val cached = DynamicColorCache.get(testUrl)
        assertNotNull(cached)
        assertEquals(customColors.topGradientColor, cached?.topGradientColor)
        assertEquals(customColors.midGradientColor, cached?.midGradientColor)
        assertEquals(customColors.bottomGradientColor, cached?.bottomGradientColor)
        assertEquals(customColors.accentColor, cached?.accentColor)

        DynamicColorCache.clear()
        assertNull(DynamicColorCache.get(testUrl))
    }

    @Test
    fun testGradientProgressionDepth() {
        val colors = DynamicArtworkColors.DefaultDark
        // Verify top, mid, bottom have descending perceived brightness / luminance
        val topLum = (colors.topGradientColor.red * 0.299f) + (colors.topGradientColor.green * 0.587f) + (colors.topGradientColor.blue * 0.114f)
        val midLum = (colors.midGradientColor.red * 0.299f) + (colors.midGradientColor.green * 0.587f) + (colors.midGradientColor.blue * 0.114f)
        val bottomLum = (colors.bottomGradientColor.red * 0.299f) + (colors.bottomGradientColor.green * 0.587f) + (colors.bottomGradientColor.blue * 0.114f)

        assertTrue("Top should be brighter or equal to mid", topLum >= midLum)
        assertTrue("Mid should be brighter or equal to bottom", midLum >= bottomLum)
    }
}
