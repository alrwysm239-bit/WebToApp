package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ThemeMode

private val WebForgeDarkScheme = darkColorScheme(
    primary = WebForgePrimary,
    onPrimary = Color.White,
    primaryContainer = DarkSurfaceElevated,
    onPrimaryContainer = DarkTextPrimary,
    secondary = WebForgeAccent,
    onSecondary = DarkBackground,
    secondaryContainer = Color(0xFF0A324A),
    onSecondaryContainer = WebForgeAccent,
    tertiary = WebForgeSuccess,
    onTertiary = DarkBackground,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = WebForgeError,
    onError = Color.White,
)

private val WebForgeLightScheme = lightColorScheme(
    primary = WebForgePrimary,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = LightTextPrimary,
    secondary = Color(0xFF0099CC),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F4FF),
    onSecondaryContainer = Color(0xFF003B52),
    tertiary = Color(0xFF14B87A),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = WebForgeError,
    onError = Color.White,
)

@Immutable
data class WebForgeExtendedColors(
    val isDark: Boolean,
    val glowBorder: Color,
    val cardBorder: Color,
    val codeBackground: Color,
    val codeGutter: Color,
    val success: Color,
    val accent: Color,
    val softViolet: Color,
    val warning: Color,
    val syntaxTag: Color,
    val syntaxAttrName: Color,
    val syntaxAttrValue: Color,
    val syntaxComment: Color,
)

val LocalWebForgeColors = staticCompositionLocalOf {
    WebForgeExtendedColors(
        isDark = true,
        glowBorder = WebForgePrimary,
        cardBorder = DarkBorder,
        codeBackground = DarkSurface,
        codeGutter = DarkSurfaceVariant,
        success = WebForgeSuccess,
        accent = WebForgeAccent,
        softViolet = WebForgeSoftViolet,
        warning = WebForgeWarning,
        syntaxTag = SyntaxTag,
        syntaxAttrName = SyntaxAttrName,
        syntaxAttrValue = SyntaxAttrValue,
        syntaxComment = SyntaxComment,
    )
}

private val WebForgeShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun WebForgeTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val colorScheme = if (useDark) WebForgeDarkScheme else WebForgeLightScheme
    val extendedColors = if (useDark) {
        WebForgeExtendedColors(
            isDark = true,
            glowBorder = WebForgePrimary,
            cardBorder = DarkBorder,
            codeBackground = DarkSurface,
            codeGutter = DarkSurfaceVariant,
            success = WebForgeSuccess,
            accent = WebForgeAccent,
            softViolet = WebForgeSoftViolet,
            warning = WebForgeWarning,
            syntaxTag = SyntaxTag,
            syntaxAttrName = SyntaxAttrName,
            syntaxAttrValue = SyntaxAttrValue,
            syntaxComment = SyntaxComment,
        )
    } else {
        WebForgeExtendedColors(
            isDark = false,
            glowBorder = WebForgePrimary.copy(alpha = 0.7f),
            cardBorder = LightBorder,
            codeBackground = LightSurface,
            codeGutter = LightSurfaceVariant,
            success = Color(0xFF14B87A),
            accent = Color(0xFF0099CC),
            softViolet = WebForgePrimary,
            warning = Color(0xFFD97706),
            syntaxTag = SyntaxTagLight,
            syntaxAttrName = SyntaxAttrNameLight,
            syntaxAttrValue = SyntaxAttrValueLight,
            syntaxComment = SyntaxCommentLight,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalWebForgeColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WebForgeTypography,
            shapes = WebForgeShapes,
            content = content,
        )
    }
}
