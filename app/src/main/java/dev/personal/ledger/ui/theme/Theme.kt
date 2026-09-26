package dev.personal.ledger.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Three-layer tokens (docs/PRODUCT.md §7):
 *   primitives (Palette) → semantic roles (LedgerColors) → component usage (in components).
 * Components never use raw hex; they read LedgerTheme.colors.
 */

private object Palette {
    val ink950 = Color(0xFF0A0C0F); val ink900 = Color(0xFF13161B); val ink850 = Color(0xFF1A1E24); val ink800 = Color(0xFF23282F)
    val ink700 = Color(0xFF30363F); val ink500 = Color(0xFF6B7380); val ink300 = Color(0xFF9BA3AF); val ink100 = Color(0xFFF1F3F6)
    val paper = Color(0xFFF5F6F8); val white = Color(0xFFFFFFFF); val mist = Color(0xFFEEF0F3); val line = Color(0xFFE3E6EA)
    val slate = Color(0xFF5A6371); val slateLight = Color(0xFF8B94A1); val night = Color(0xFF0D1015)

    val iris600 = Color(0xFF4263EB); val iris300 = Color(0xFF8097FF)
    val jade600 = Color(0xFF0F8F63); val jade300 = Color(0xFF3DD68C)
    val coral600 = Color(0xFFD23C41); val coral300 = Color(0xFFFF7477)
    val amber600 = Color(0xFFA86A12); val amber300 = Color(0xFFF2B55A)
}

@Immutable
data class LedgerColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceHigh: Color,
    val hairline: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val positive: Color,
    val negative: Color,
    val caution: Color,
    val positiveSoft: Color,
    val negativeSoft: Color,
    val cautionSoft: Color,
    val scrim: Color,
    /** Muted category hues — identity only, shown as tinted icon wells. */
    val hues: List<Color>,
    val chartGrid: Color,
    val chartMuted: Color,
)

private val hueLight = listOf(0xFFE8590C, 0xFF9C6B3C, 0xFF1C7ED6, 0xFFC2255C, 0xFF7048E8, 0xFF0C8599, 0xFF2F9E44, 0xFF5C677D).map { Color(it) }
private val hueDark = listOf(0xFFFF922B, 0xFFD4A373, 0xFF4DABF7, 0xFFF06595, 0xFF9775FA, 0xFF3BC9DB, 0xFF69DB7C, 0xFFADB5C7).map { Color(it) }

val LightColors = LedgerColors(
    isDark = false,
    bg = Palette.paper, surface = Palette.white, surfaceAlt = Palette.mist, surfaceHigh = Palette.white, hairline = Palette.line,
    text = Palette.night, textMuted = Palette.slate, textFaint = Palette.slateLight,
    accent = Palette.iris600, onAccent = Palette.white, accentSoft = Palette.iris600.copy(alpha = 0.10f),
    positive = Palette.jade600, negative = Palette.coral600, caution = Palette.amber600,
    positiveSoft = Palette.jade600.copy(alpha = 0.10f), negativeSoft = Palette.coral600.copy(alpha = 0.09f), cautionSoft = Palette.amber600.copy(alpha = 0.11f),
    scrim = Color(0x66000000), hues = hueLight, chartGrid = Color(0xFFE9EBEF), chartMuted = Color(0xFFC9CED6),
)

val DarkColors = LedgerColors(
    isDark = true,
    bg = Palette.ink950, surface = Palette.ink900, surfaceAlt = Palette.ink850, surfaceHigh = Palette.ink800, hairline = Palette.ink800,
    text = Palette.ink100, textMuted = Palette.ink300, textFaint = Palette.ink500,
    accent = Palette.iris300, onAccent = Palette.ink950, accentSoft = Palette.iris300.copy(alpha = 0.14f),
    positive = Palette.jade300, negative = Palette.coral300, caution = Palette.amber300,
    positiveSoft = Palette.jade300.copy(alpha = 0.12f), negativeSoft = Palette.coral300.copy(alpha = 0.12f), cautionSoft = Palette.amber300.copy(alpha = 0.13f),
    scrim = Color(0x99000000), hues = hueDark, chartGrid = Color(0xFF1E232A), chartMuted = Palette.ink700,
)

private val Sans = FontFamily.SansSerif
private const val TNUM = "tnum, lnum"

@Immutable
data class LedgerType(
    val hero: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.03).em, fontFeatureSettings = TNUM),
    val display: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.025).em, fontFeatureSettings = TNUM),
    val title: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.015).em),
    val headline: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TNUM),
    val body: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, fontFeatureSettings = TNUM),
    val label: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM),
    val caption: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em, fontFeatureSettings = TNUM),
    val overline: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.06.em),
    val amount: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = TNUM),
    val numpad: TextStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = TNUM),
)

object Space { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp; val gutter = 20.dp }

object Shapes {
    val card = RoundedCornerShape(22.dp)
    val tile = RoundedCornerShape(18.dp)
    val well = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(12.dp)
    val key = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(50)
}

object Motion {
    const val FAST = 120
    const val MICRO = 160
    const val STANDARD = 240
    const val EMPHASIZED = 320
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val exit = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    fun <T> gentle() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
}

val LocalLedgerColors = staticCompositionLocalOf { LightColors }
val LocalLedgerType = staticCompositionLocalOf { LedgerType() }
/** When true, amounts render as "•••" (privacy mode). */
val LocalHideAmounts = staticCompositionLocalOf { false }

object LedgerTheme {
    val colors: LedgerColors @Composable get() = LocalLedgerColors.current
    val type: LedgerType @Composable get() = LocalLedgerType.current
}

@Composable
fun LedgerAppTheme(theme: String = "system", hideAmounts: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textMuted, outline = c.hairline, outlineVariant = c.hairline, error = c.negative,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surfaceAlt, surfaceContainerHighest = c.surfaceHigh,
        inverseSurface = c.text, inverseOnSurface = c.bg, primaryContainer = c.accentSoft, onPrimaryContainer = c.accent, secondaryContainer = c.accentSoft,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textMuted, outline = c.hairline, outlineVariant = c.hairline, error = c.negative,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surfaceAlt, surfaceContainerHighest = c.surfaceHigh,
        inverseSurface = Color(0xFF1A1E24), inverseOnSurface = Color(0xFFF1F3F6), primaryContainer = c.accentSoft, onPrimaryContainer = c.accent, secondaryContainer = c.accentSoft,
    )
    CompositionLocalProvider(LocalLedgerColors provides c, LocalHideAmounts provides hideAmounts) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
