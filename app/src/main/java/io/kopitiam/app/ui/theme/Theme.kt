package io.kopitiam.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// MARK: - Warm Kopi palette (matches iOS Theme.swift + web exactly)

/** Design tokens resolved for the active color scheme. */
data class KopiColors(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val textSoft: Color,
    val line: Color,
    val accent: Color,
    val brand: Color,
    val onBrand: Color,
    val dark: Boolean,
)

val KopiLight = KopiColors(
    bg = Color(0xFFF7F1E8),
    surface = Color(0xFFFFFDF9),
    text = Color(0xFF1A1310),
    textSoft = Color(0xFF6B5D52),
    line = Color(0xFFE7DCCB),
    accent = Color(0xFFC05621),
    brand = Color(0xFF6F4E37),
    onBrand = Color(0xFFF7F1E8),
    dark = false,
)

val KopiDark = KopiColors(
    bg = Color(0xFF2A1A12),
    surface = Color(0xFF33251C),
    text = Color(0xFFF7F1E8),
    textSoft = Color(0xFFC9A876),
    line = Color(0xFF4A3628),
    accent = Color(0xFFE9843F),
    brand = Color(0xFFC9A876),
    onBrand = Color(0xFF2A1A12),
    dark = true,
)

/** Radius scale — mirrors iOS Radius (8/14/22/pill). */
object Radius {
    val sm = 8.dp
    val md = 14.dp
    val lg = 22.dp
    val pill = 999.dp
}

/** Spacing scale — mirrors iOS Space (6/10/16/24/36). */
object Space {
    val xs = 6.dp
    val sm = 10.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 36.dp
}

/** Access the resolved warm-kopi tokens from anywhere in the tree. */
val LocalKopi = staticCompositionLocalOf { KopiLight }

/**
 * True when the OS "Remove animations" accessibility setting is on. Used to
 * freeze the house-ad and press-scale motion, matching iOS reduceMotion.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(
        resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
    )
    return scale == 0f
}

@Composable
fun KopitiamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val kopi = if (darkTheme) KopiDark else KopiLight

    // Map the warm-kopi tokens onto a Material3 ColorScheme so stock components
    // (buttons, sliders, pickers) inherit the palette.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = kopi.brand,
            onPrimary = kopi.onBrand,
            secondary = kopi.accent,
            onSecondary = kopi.onBrand,
            background = kopi.bg,
            onBackground = kopi.text,
            surface = kopi.surface,
            onSurface = kopi.text,
            surfaceVariant = kopi.surface,
            onSurfaceVariant = kopi.textSoft,
            outline = kopi.line,
            error = Color(0xFFE5484D),
        )
    } else {
        lightColorScheme(
            primary = kopi.brand,
            onPrimary = kopi.onBrand,
            secondary = kopi.accent,
            onSecondary = kopi.onBrand,
            background = kopi.bg,
            onBackground = kopi.text,
            surface = kopi.surface,
            onSurface = kopi.text,
            surfaceVariant = kopi.surface,
            onSurfaceVariant = kopi.textSoft,
            outline = kopi.line,
            error = Color(0xFFD1332B),
        )
    }

    CompositionLocalProvider(LocalKopi provides kopi) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content,
        )
    }
}
