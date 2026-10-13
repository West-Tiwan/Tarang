package com.westtiwan.tarang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.Image
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.themeColor
import com.materialkolor.rememberDynamicColorScheme
import com.westtiwan.tarang.data.ThemeMode

/** Android 12+ wallpaper colours. Returns null where unsupported. */
@Composable
expect fun platformDynamicScheme(dark: Boolean): ColorScheme?

/** Asks Coil for bitmaps whose pixels can be read (Android hardware bitmaps cannot). */
expect fun ImageRequest.Builder.softwareBitmaps(): ImageRequest.Builder

/** Converts a decoded Coil image into a Compose bitmap for colour extraction. */
expect fun Image.toComposeBitmap(): ImageBitmap?

val BrandSeed = Color(0xFFFF8F3F)

private val AppTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** The scalloped "cookie" shape Material 3 Expressive uses for avatars. */
val ArtistShape: Shape
    @Composable get() = MaterialShapes.Cookie9Sided.toShape()

val CoverShape: Shape get() = RoundedCornerShape(20.dp)

@Composable
fun isDarkTheme(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

/**
 * Material 3 Expressive theme. Colours come from (in order) the given seed such as album art, the
 * Android wallpaper, or the brand colour, always run through the Material tonal palette.
 */
@Composable
fun TarangTheme(mode: ThemeMode, dynamic: Boolean, pureBlack: Boolean, seed: Color? = null, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    val wallpaper = if (dynamic && seed == null) platformDynamicScheme(dark) else null
    val generated = rememberDynamicColorScheme(
        seedColor = seed ?: BrandSeed,
        isDark = dark,
        isAmoled = dark && pureBlack,
        style = if (seed != null) PaletteStyle.Fidelity else PaletteStyle.TonalSpot,
    )
    var scheme = wallpaper ?: generated
    if (dark && pureBlack && wallpaper != null) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

private val seedCache = HashMap<String, Color>()

/** Dominant, theme-friendly colour of a cover, or null until it has loaded. */
@Composable
fun rememberSeedColor(coverArt: String?): Color? {
    val app = LocalApp.current
    val context = LocalPlatformContext.current
    var color by remember(coverArt) { mutableStateOf(coverArt?.let { seedCache[it] }) }
    LaunchedEffect(coverArt) {
        if (coverArt == null || color != null) return@LaunchedEffect
        val url = app.coverUrl(coverArt, 128) ?: return@LaunchedEffect
        val req = ImageRequest.Builder(context).data(url).size(128).softwareBitmaps().build()
        val bmp = runCatching { SingletonImageLoader.get(context).execute(req).image?.toComposeBitmap() }.getOrNull() ?: return@LaunchedEffect
        val c = runCatching { bmp.themeColor(fallback = BrandSeed) }.getOrNull() ?: return@LaunchedEffect
        seedCache[coverArt] = c
        color = c
    }
    return color
}
