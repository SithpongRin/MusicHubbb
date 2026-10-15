package com.musichub.app.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dynamic artwork color palette extracted offline from the song's album art.
 * Formulated to guarantee high contrast and legibility for text and playback controls.
 */
@Stable
data class DynamicArtworkColors(
    val primaryColor: Color,
    val topGradientColor: Color,
    val midGradientColor: Color,
    val bottomGradientColor: Color,
    val ambientGlowColor: Color,
    val accentColor: Color
) {
    companion object {
        val DefaultDark = DynamicArtworkColors(
            primaryColor = Color(0xFF1E3A8A),
            topGradientColor = Color(0xFF17232C),
            midGradientColor = Color(0xFF10141A),
            bottomGradientColor = Color(0xFF0C0E12),
            ambientGlowColor = Color(0xFF2563EB).copy(alpha = 0.30f),
            accentColor = Color(0xFF38BDF8)
        )

        val DefaultLight = DynamicArtworkColors(
            primaryColor = Color(0xFF3B82F6),
            topGradientColor = Color(0xFF1B222E),
            midGradientColor = Color(0xFF11151C),
            bottomGradientColor = Color(0xFF0C0E12),
            ambientGlowColor = Color(0xFF3B82F6).copy(alpha = 0.28f),
            accentColor = Color(0xFF60A5FA)
        )
    }
}

/**
 * In-memory LRU cache to store extracted color palettes by artwork URL/URI.
 * Avoids redundant network/disk image decoding and Palette calculations.
 */
object DynamicColorCache {
    private const val MAX_ENTRIES = 64
    private val memoryCache = object : LinkedHashMap<String, DynamicArtworkColors>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DynamicArtworkColors>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun get(key: String): DynamicArtworkColors? = memoryCache[key]

    @Synchronized
    fun put(key: String, colors: DynamicArtworkColors) {
        memoryCache[key] = colors
    }

    @Synchronized
    fun clear() {
        memoryCache.clear()
    }
}

/**
 * Offline artwork color extractor using Coil and AndroidX Palette.
 */
object DynamicColorExtractor {

    suspend fun extractColors(
        context: Context,
        artworkUrl: String?,
        isDark: Boolean = true
    ): DynamicArtworkColors = withContext(Dispatchers.IO) {
        val defaultColors = if (isDark) DynamicArtworkColors.DefaultDark else DynamicArtworkColors.DefaultLight
        if (artworkUrl.isNullOrBlank()) {
            return@withContext defaultColors
        }

        // Return cached colors if available
        DynamicColorCache.get(artworkUrl)?.let { return@withContext it }

        val bitmap = loadThumbnailBitmap(context, artworkUrl) ?: return@withContext defaultColors

        val colors = generateHarmoniousColors(bitmap, defaultColors)
        DynamicColorCache.put(artworkUrl, colors)
        colors
    }

    private suspend fun loadThumbnailBitmap(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(128, 128)
                .scale(Scale.FILL)
                .allowHardware(false) // Must be software bitmap for Palette CPU inspection
                .crossfade(false)
                .build()

            val result = loader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateHarmoniousColors(
        bitmap: Bitmap,
        fallback: DynamicArtworkColors
    ): DynamicArtworkColors {
        val palette = try {
            Palette.from(bitmap)
                .maximumColorCount(16)
                .resizeBitmapArea(128 * 128)
                .generate()
        } catch (e: Exception) {
            return fallback
        }

        // Swatch priority: Vibrant -> Dark Vibrant -> Dominant -> Light Vibrant -> Muted -> Dark Muted
        val primarySwatch = palette.vibrantSwatch
            ?: palette.darkVibrantSwatch
            ?: palette.dominantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.darkMutedSwatch

        val secondarySwatch = palette.mutedSwatch
            ?: palette.darkMutedSwatch
            ?: palette.dominantSwatch
            ?: primarySwatch

        if (primarySwatch == null) {
            return fallback
        }

        val primaryRgb = primarySwatch.rgb
        val secondaryRgb = secondarySwatch?.rgb ?: primaryRgb

        val primaryHsl = FloatArray(3)
        ColorUtils.colorToHSL(primaryRgb, primaryHsl)

        val secondaryHsl = FloatArray(3)
        ColorUtils.colorToHSL(secondaryRgb, secondaryHsl)

        val primaryHue = primaryHsl[0]
        val primarySat = primaryHsl[1].coerceIn(0.20f, 0.85f)

        val secondaryHue = secondaryHsl[0]
        val secondarySat = secondaryHsl[1].coerceIn(0.15f, 0.70f)

        // 1. Top Gradient: Rich, distinct tint matching the album hue, darkened for high text contrast
        val topHsl = floatArrayOf(
            primaryHue,
            (primarySat * 0.75f).coerceIn(0.25f, 0.65f),
            0.15f // Darkness ensured so white text and icons are 100% legible
        )
        val topColor = Color(ColorUtils.HSLToColor(topHsl))

        // 2. Mid Gradient: Transition tone blending primary/secondary hue into deeper dark
        val midHsl = floatArrayOf(
            if (Math.abs(primaryHue - secondaryHue) > 10f) secondaryHue else primaryHue,
            (secondarySat * 0.50f).coerceIn(0.15f, 0.45f),
            0.085f
        )
        val midColor = Color(ColorUtils.HSLToColor(midHsl))

        // 3. Bottom Gradient: Deep dark anchor (near black with a faint hint of artwork hue)
        val bottomHsl = floatArrayOf(
            primaryHue,
            (primarySat * 0.25f).coerceIn(0.05f, 0.25f),
            0.045f
        )
        val bottomColor = Color(ColorUtils.HSLToColor(bottomHsl))

        // 4. Ambient Glow: Radiant aura behind the album art or disc
        val ambientGlowHsl = floatArrayOf(
            primaryHue,
            primarySat.coerceAtLeast(0.50f),
            0.45f
        )
        val ambientGlowColor = Color(ColorUtils.HSLToColor(ambientGlowHsl)).copy(alpha = 0.35f)

        // 5. Accent: Harmonious highlight tint
        val accentHsl = floatArrayOf(
            primaryHue,
            primarySat.coerceAtLeast(0.60f),
            0.62f
        )
        val accentColor = Color(ColorUtils.HSLToColor(accentHsl))

        return DynamicArtworkColors(
            primaryColor = Color(primaryRgb),
            topGradientColor = topColor,
            midGradientColor = midColor,
            bottomGradientColor = bottomColor,
            ambientGlowColor = ambientGlowColor,
            accentColor = accentColor
        )
    }
}

/**
 * Animated dynamic artwork color state that smoothly transitions when the track changes.
 */
@Composable
fun rememberAnimatedDynamicColors(
    artworkUrl: String?,
    isDark: Boolean = true
): DynamicArtworkColors {
    val context = LocalContext.current
    val fallback = if (isDark) DynamicArtworkColors.DefaultDark else DynamicArtworkColors.DefaultLight
    var targetColors by remember(artworkUrl) {
        mutableStateOf(
            if (!artworkUrl.isNullOrBlank()) {
                DynamicColorCache.get(artworkUrl) ?: fallback
            } else {
                fallback
            }
        )
    }

    LaunchedEffect(artworkUrl, isDark) {
        val extracted = DynamicColorExtractor.extractColors(context, artworkUrl, isDark)
        targetColors = extracted
    }

    val animSpec = tween<Color>(durationMillis = 650, easing = FastOutSlowInEasing)

    val animatedTop by animateColorAsState(targetValue = targetColors.topGradientColor, animationSpec = animSpec, label = "topColor")
    val animatedMid by animateColorAsState(targetValue = targetColors.midGradientColor, animationSpec = animSpec, label = "midColor")
    val animatedBottom by animateColorAsState(targetValue = targetColors.bottomGradientColor, animationSpec = animSpec, label = "bottomColor")
    val animatedAmbient by animateColorAsState(targetValue = targetColors.ambientGlowColor, animationSpec = animSpec, label = "ambientGlow")
    val animatedAccent by animateColorAsState(targetValue = targetColors.accentColor, animationSpec = animSpec, label = "accentColor")
    val animatedPrimary by animateColorAsState(targetValue = targetColors.primaryColor, animationSpec = animSpec, label = "primaryColor")

    return DynamicArtworkColors(
        primaryColor = animatedPrimary,
        topGradientColor = animatedTop,
        midGradientColor = animatedMid,
        bottomGradientColor = animatedBottom,
        ambientGlowColor = animatedAmbient,
        accentColor = animatedAccent
    )
}
