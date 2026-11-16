package com.closecircle.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colours from the approved mockups, light and dark. */
@Immutable
data class CcColors(
    val bg: Color, val surface: Color, val soft: Color, val soft2: Color, val off: Color,
    val text: Color, val muted: Color, val line: Color,
    val accent: Color, val onAccent: Color, val accentSoft: Color, val accentFg: Color,
    val green: Color, val greenFg: Color, val yellow: Color, val yellowFg: Color, val grey: Color, val greyFg: Color,
    val red: Color, val onRed: Color, val amberBg: Color, val amberFg: Color,
    val darkCard: Color, val onDark: Color, val onDarkMuted: Color, val onDarkAccent: Color,
    val mapBg: Color, val road: Color, val park: Color,
)

val LightCc = CcColors(
    bg = Color(0xFFF6F7F5), surface = Color(0xFFFFFFFF), soft = Color(0xFFEEF2F0), soft2 = Color(0xFFE3E9E6), off = Color(0xFFCBD5D1),
    text = Color(0xFF16211F), muted = Color(0xFF4B5755), line = Color(0xFFDDE4E0),
    accent = Color(0xFF0D8478), onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFE3F5F1), accentFg = Color(0xFF0A7A6E),
    green = Color(0xFF0D8478), greenFg = Color(0xFF0A7A6E), yellow = Color(0xFFE0A100), yellowFg = Color(0xFF7A5200),
    grey = Color(0xFF9AA5A1), greyFg = Color(0xFF4B5755),
    red = Color(0xFFB42318), onRed = Color(0xFFFFFFFF), amberBg = Color(0xFFFDEBDD), amberFg = Color(0xFF9A3412),
    darkCard = Color(0xFF16211F), onDark = Color(0xFFFFFFFF), onDarkMuted = Color(0xFFB9C6C2), onDarkAccent = Color(0xFF7DD3C8),
    mapBg = Color(0xFFE9EEEA), road = Color(0xFFFFFFFF), park = Color(0xFFD7E6D9),
)

val DarkCc = CcColors(
    bg = Color(0xFF0E1513), surface = Color(0xFF17211F), soft = Color(0xFF1F2B28), soft2 = Color(0xFF2A3835), off = Color(0xFF3E4F4B),
    text = Color(0xFFF2F6F4), muted = Color(0xFFA9B7B3), line = Color(0xFF2A3835),
    accent = Color(0xFF5CE5B4), onAccent = Color(0xFF052E22), accentSoft = Color(0xFF143A30), accentFg = Color(0xFF7CF0C8),
    green = Color(0xFF5CE5B4), greenFg = Color(0xFF7CF0C8), yellow = Color(0xFFFBBF24), yellowFg = Color(0xFFFCD34D),
    grey = Color(0xFF8B9A96), greyFg = Color(0xFFB8C4C0),
    red = Color(0xFFF87171), onRed = Color(0xFF2A0A07), amberBg = Color(0xFF3B1F10), amberFg = Color(0xFFFDBA74),
    darkCard = Color(0xFF0A100F), onDark = Color(0xFFF2F6F4), onDarkMuted = Color(0xFFA9B7B3), onDarkAccent = Color(0xFF5EEAD4),
    mapBg = Color(0xFF1A2421), road = Color(0xFF27332F), park = Color(0xFF1E2D24),
)

val LocalCc = staticCompositionLocalOf { LightCc }

object Cc {
    val colors: CcColors
        @Composable @ReadOnlyComposable get() = LocalCc.current
}

@Composable
fun CloseCircleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val cc = if (dark) DarkCc else LightCc
    val scheme = if (dark) {
        darkColorScheme(
            primary = cc.accent, onPrimary = cc.onAccent, background = cc.bg, onBackground = cc.text,
            surface = cc.surface, onSurface = cc.text, surfaceVariant = cc.soft, onSurfaceVariant = cc.muted,
            error = cc.red, onError = cc.onRed, outline = cc.line,
        )
    } else {
        lightColorScheme(
            primary = cc.accent, onPrimary = cc.onAccent, background = cc.bg, onBackground = cc.text,
            surface = cc.surface, onSurface = cc.text, surfaceVariant = cc.soft, onSurfaceVariant = cc.muted,
            error = cc.red, onError = cc.onRed, outline = cc.line,
        )
    }
    // shortcut: system font; load Plus Jakarta Sans (as in the mockups) when fonts are bundled
    CompositionLocalProvider(LocalCc provides cc) { MaterialTheme(colorScheme = scheme, content = content) }
}
