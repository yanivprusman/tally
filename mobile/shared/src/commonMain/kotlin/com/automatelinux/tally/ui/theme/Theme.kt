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
 * Warm paper and clay. Keeping a tally is a relaxed thing — you jot what you earned and
 * what you spent — so nothing here is allowed to look like an alert. In particular
 * **money out is terracotta, never red**: paying for dinner is not an error condition,
 * and a screen half-covered in warning colour makes an easy habit feel like a reckoning.
 * Money in is a sage green rather than a signal green, for the same reason.
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

/** Dark is a warm brown-charcoal — lamplight, not a terminal. */
private val DarkPalette = TallyColors(
    bg = Color(0xFF191512),
    surface = Color(0xFF231E19),
    surfaceAlt = Color(0xFF2E2721),
    line = Color(0xFF3B322A),
    text = Color(0xFFF0E8DE),
    textDim = Color(0xFFAC9F91),
    textFaint = Color(0xFF8A7F72),
    brand = Color(0xFFE8B571),
    onBrand = Color(0xFF2A1D0C),
    brandSoft = Color(0xFF3B2E1C),
    income = Color(0xFF8FC79A),
    incomeSoft = Color(0xFF25301F),
    expense = Color(0xFFE39B7C),
    expenseSoft = Color(0xFF36251C),
    isDark = true,
)

/** Light is warm paper, so white cards sit on it without glaring. */
private val LightPalette = TallyColors(
    bg = Color(0xFFFAF6F0),
    surface = Color(0xFFFFFCF8),
    surfaceAlt = Color(0xFFF1EADF),
    line = Color(0xFFE5DACB),
    text = Color(0xFF2E2A26),
    textDim = Color(0xFF6E655C),
    textFaint = Color(0xFF8C8073),
    brand = Color(0xFFE0A458),
    onBrand = Color(0xFF2E2216),
    brandSoft = Color(0xFFFAEEDB),
    income = Color(0xFF38724E),
    incomeSoft = Color(0xFFE6EFE3),
    expense = Color(0xFFB35C3E),
    expenseSoft = Color(0xFFF9E7DE),
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
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    // Section labels are sentence case, not shouted small-caps — same reason as the palette.
    labelSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
)

/** The six stripes a tally can wear — muted enough to sit quietly next to each other. */
val AccentPalette = listOf(
    Color(0xFFE8B571),
    Color(0xFF93B48B),
    Color(0xFF8FA8C4),
    Color(0xFFD89A8E),
    Color(0xFFB8B072),
    Color(0xFFA9A0C9),
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
            error = c.expense, onError = Color(0xFF36251C),
            inverseSurface = c.surfaceAlt, inverseOnSurface = c.text,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF8A5F2B), onPrimary = Color.White,
            primaryContainer = c.brandSoft, onPrimaryContainer = Color(0xFF4A3418),
            secondary = Color(0xFF8A5F2B), onSecondary = Color.White,
            background = c.bg, onBackground = c.text,
            surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textDim,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surfaceAlt,
            outline = c.line, outlineVariant = c.line,
            error = c.expense, onError = Color.White,
            inverseSurface = Color(0xFF2E2A26), inverseOnSurface = Color(0xFFFAF6F0),
        )
    }
    CompositionLocalProvider(LocalTallyColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
