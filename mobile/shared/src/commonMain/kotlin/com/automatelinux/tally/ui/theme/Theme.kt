package com.automatelinux.tally.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tally looks like chalk marks on slate: quiet ink surfaces, a brass accent for anything
 * you can press, and exactly two loud colours reserved for the only thing that matters —
 * money coming in and money going out. Nothing else is allowed to be green or red.
 */
@Immutable
data class TallyColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val line: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val income: Color,
    val incomeSoft: Color,
    val expense: Color,
    val expenseSoft: Color,
    val isDark: Boolean,
)

private val DarkPalette = TallyColors(
    bg = Color(0xFF0D1116),
    surface = Color(0xFF161C24),
    surfaceAlt = Color(0xFF1E2630),
    line = Color(0xFF2A333F),
    text = Color(0xFFE9EDF3),
    textDim = Color(0xFF9AA7B8),
    textFaint = Color(0xFF64717F),
    brand = Color(0xFFF5B544),
    onBrand = Color(0xFF231703),
    brandSoft = Color(0xFF3A2C0E),
    income = Color(0xFF3DD68C),
    incomeSoft = Color(0xFF10321F),
    expense = Color(0xFFFF7A8A),
    expenseSoft = Color(0xFF3A1219),
    isDark = true,
)

private val LightPalette = TallyColors(
    bg = Color(0xFFF4F6F9),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEDF1F6),
    line = Color(0xFFDCE3EC),
    text = Color(0xFF0D1116),
    textDim = Color(0xFF5A6879),
    textFaint = Color(0xFF8C99A8),
    brand = Color(0xFFF0A81E),
    onBrand = Color(0xFF241700),
    brandSoft = Color(0xFFFDF0D6),
    income = Color(0xFF0E9463),
    incomeSoft = Color(0xFFDFF4EA),
    expense = Color(0xFFD62D50),
    expenseSoft = Color(0xFFFCE4E9),
    isDark = false,
)

val LocalTallyColors = staticCompositionLocalOf { DarkPalette }

/** Shorthand: `T.income` anywhere inside the theme. */
val T: TallyColors
    @Composable get() = LocalTallyColors.current

/** Numbers carry this screen, so they get their own scale — tight, heavy, unmissable. */
object Num {
    val hero = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.6).sp)
    val large = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
    val medium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val keypad = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium)
}

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp),
)

/** The six stripes a tally can wear, so a list of them is scannable at a glance. */
val AccentPalette = listOf(
    Color(0xFFF5B544),
    Color(0xFF2DD4BF),
    Color(0xFF8B9DFF),
    Color(0xFFFF8FA3),
    Color(0xFFA3E635),
    Color(0xFF56C2FF),
)

fun accentAt(index: Int): Color = AccentPalette[((index % AccentPalette.size) + AccentPalette.size) % AccentPalette.size]

@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkPalette else LightPalette
    // Stock Material components (dialogs, menus, snackbars, text selection) read the
    // colorScheme, so it is mapped onto the same palette instead of left at defaults.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.brand, onPrimary = c.onBrand,
            primaryContainer = c.brandSoft, onPrimaryContainer = c.brand,
            secondary = c.brand, onSecondary = c.onBrand,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textDim,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceAlt,
            outline = c.line, outlineVariant = c.line,
            error = c.expense, onError = Color(0xFF3A1219),
            inverseSurface = c.surfaceAlt, inverseOnSurface = c.text,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF8A5A00), onPrimary = Color.White,
            primaryContainer = c.brandSoft, onPrimaryContainer = Color(0xFF4A3000),
            secondary = Color(0xFF8A5A00), onSecondary = Color.White,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textDim,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceAlt,
            outline = c.line, outlineVariant = c.line,
            error = c.expense, onError = Color.White,
            inverseSurface = Color(0xFF20262E), inverseOnSurface = Color(0xFFF1F4F8),
        )
    }
    CompositionLocalProvider(LocalTallyColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
