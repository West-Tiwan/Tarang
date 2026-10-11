package com.westtiwan.tarang.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.westtiwan.tarang.data.ThemeMode

/** Android 12+ wallpaper colours. Returns null where unsupported. */
@Composable
expect fun platformDynamicScheme(dark: Boolean): ColorScheme?

private val Saffron = Color(0xFFFF8F3F)
private val Teal = Color(0xFF3FD0C9)

private val DarkScheme = darkColorScheme(
    primary = Saffron,
    onPrimary = Color(0xFF2B1300),
    primaryContainer = Color(0xFF5A2C00),
    onPrimaryContainer = Color(0xFFFFDCC6),
    secondary = Teal,
    onSecondary = Color(0xFF003734),
    secondaryContainer = Color(0xFF00504C),
    onSecondaryContainer = Color(0xFF9EF2EB),
    tertiary = Color(0xFFE7B6FF),
    background = Color(0xFF121014),
    onBackground = Color(0xFFE8E1E6),
    surface = Color(0xFF121014),
    onSurface = Color(0xFFE8E1E6),
    surfaceVariant = Color(0xFF2B272E),
    onSurfaceVariant = Color(0xFFCBC4CC),
    surfaceContainerLowest = Color(0xFF0C0A0E),
    surfaceContainerLow = Color(0xFF19171B),
    surfaceContainer = Color(0xFF1E1B20),
    surfaceContainerHigh = Color(0xFF28252B),
    surfaceContainerHighest = Color(0xFF333036),
    outline = Color(0xFF948E96),
    outlineVariant = Color(0xFF49454C),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF9A4A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC6),
    onPrimaryContainer = Color(0xFF321200),
    secondary = Color(0xFF006A65),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9EF2EB),
    onSecondaryContainer = Color(0xFF00201E),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

@Composable
fun TarangTheme(mode: ThemeMode, dynamic: Boolean, pureBlack: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    var scheme = (if (dynamic) platformDynamicScheme(dark) else null) ?: if (dark) DarkScheme else LightScheme
    if (dark && pureBlack) {
        scheme = scheme.copy(
            background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A), surfaceContainer = Color(0xFF111111),
        )
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
