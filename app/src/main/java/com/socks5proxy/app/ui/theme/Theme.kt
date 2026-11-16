package com.socks5proxy.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class NeumorphicColors(
    val bgOuter: Color,
    val bgBase: Color,
    val bgSurface: Color,
    val gradStart: Color,
    val gradEnd: Color,
    val insetBg: Color,
    val insetDeepBg: Color,
    val shadowLight: Color,
    val shadowDark: Color,
    val insetShadowDark: Color,
    val insetShadowLight: Color,
    val rimLight: Color,
    val borderSubtle: Color,
    val dividerColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentBright: Color,
    val accentGlow: Color,
    val danger: Color,
    val isDark: Boolean
)

val DarkNeumorphicColors = NeumorphicColors(
    bgOuter = DarkBgOuter,
    bgBase = DarkBgBase,
    bgSurface = DarkBgSurface,
    gradStart = DarkGradStart,
    gradEnd = DarkGradEnd,
    insetBg = DarkInsetBg,
    insetDeepBg = DarkInsetDeepBg,
    shadowLight = DarkShadowLight,
    shadowDark = DarkShadowDark,
    insetShadowDark = DarkInsetShadowDark,
    insetShadowLight = DarkInsetShadowLight,
    rimLight = DarkRimLight,
    borderSubtle = DarkBorderSubtle,
    dividerColor = DarkDivider,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    accent = DarkAccent,
    accentBright = DarkAccentBright,
    accentGlow = DarkAccentGlow,
    danger = DarkDanger,
    isDark = true
)

val LightNeumorphicColors = NeumorphicColors(
    bgOuter = LightBgOuter,
    bgBase = LightBgBase,
    bgSurface = LightBgSurface,
    gradStart = LightGradStart,
    gradEnd = LightGradEnd,
    insetBg = LightInsetBg,
    insetDeepBg = LightInsetDeepBg,
    shadowLight = LightShadowLight,
    shadowDark = LightShadowDark,
    insetShadowDark = LightInsetShadowDark,
    insetShadowLight = LightInsetShadowLight,
    rimLight = LightRimLight,
    borderSubtle = LightBorderSubtle,
    dividerColor = LightDivider,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    accent = LightAccent,
    accentBright = LightAccentBright,
    accentGlow = LightAccentGlow,
    danger = LightDanger,
    isDark = false
)

val LocalNeumorphicColors = staticCompositionLocalOf { DarkNeumorphicColors }

object NeumorphismTheme {
    val colors: NeumorphicColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNeumorphicColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBgOuter,
    primaryContainer = DarkInsetBg,
    onPrimaryContainer = DarkAccentBright,
    secondary = DarkAccentBright,
    onSecondary = Color.White,
    background = DarkBgOuter,
    onBackground = DarkTextPrimary,
    surface = DarkBgBase,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkBgSurface,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkShadowDark,
    outlineVariant = DarkBorderSubtle,
    error = DarkDanger,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = Color.White,
    primaryContainer = LightInsetBg,
    onPrimaryContainer = LightAccent,
    secondary = LightAccentBright,
    onSecondary = Color.White,
    background = LightBgOuter,
    onBackground = LightTextPrimary,
    surface = LightBgBase,
    onSurface = LightTextPrimary,
    surfaceVariant = LightBgSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightShadowDark,
    outlineVariant = LightBorderSubtle,
    error = LightDanger,
    onError = Color.White
)

@Composable
fun Socks5ProxyTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val neumorphicColors = if (darkTheme) DarkNeumorphicColors else LightNeumorphicColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = neumorphicColors.bgOuter.toArgb()
            window.navigationBarColor = neumorphicColors.bgBase.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalNeumorphicColors provides neumorphicColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Socks5Typography,
            content = content
        )
    }
}
