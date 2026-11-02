package com.shiyin.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Violet = Color(0xFFB49BFF)
val Mint = Color(0xFF6FE7DD)
val Ink = Color(0xFF0A0A0F)
val CardDark = Color(0xFF14141B)
val CardDarkHigh = Color(0xFF1E1E29)
val DimText = Color(0xFF9E9BB0)

val HeroBrush: Brush = Brush.linearGradient(listOf(Violet, Mint))

private val DarkScheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF180F33),
    primaryContainer = Color(0xFF32245A),
    onPrimaryContainer = Color(0xFFE9E2FF),
    secondary = Mint,
    onSecondary = Color(0xFF04201D),
    secondaryContainer = Color(0xFF14332F),
    onSecondaryContainer = Color(0xFFB8FFF5),
    background = Ink,
    onBackground = Color(0xFFF0EEF7),
    surface = CardDark,
    onSurface = Color(0xFFF0EEF7),
    surfaceVariant = CardDarkHigh,
    onSurfaceVariant = DimText,
    outline = Color(0xFF3A3946),
    error = Color(0xFFFF8A8A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6244D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7DEFF),
    onPrimaryContainer = Color(0xFF1E0F4A),
    secondary = Color(0xFF0E8C80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F5EF),
    onSecondaryContainer = Color(0xFF00312C),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF14131A),
    surface = Color.White,
    onSurface = Color(0xFF14131A),
    surfaceVariant = Color(0xFFEFEDF6),
    onSurfaceVariant = Color(0xFF5B586B),
    outline = Color(0xFFD7D4E3),
    error = Color(0xFFC0392B),
)

private val ShiYinTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun ShiYinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = ShiYinTypography, content = content)
}
