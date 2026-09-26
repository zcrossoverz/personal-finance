package dev.personal.ledger.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.personal.ledger.R

/*
 * Design system v2 — three layers (docs/PRODUCT.md §7):
 *   primitives (Palette) → semantic roles (LedgerColors) → component usage.
 *
 * Direction: an editorial, ink-on-paper finance tool. Warm neutrals, one "ink" colour for everything interactive,
 * and colour reserved for meaning (income, debt, caution) and for small category glyphs. No gradients, no glow,
 * no tinted cards. Numbers carry the hierarchy.
 */

private object Palette {
    // Warm paper / ink neutrals
    val paper = Color(0xFFF6F5F2); val white = Color(0xFFFFFFFF); val stone100 = Color(0xFFEFEEEA); val stone200 = Color(0xFFE7E5E0)
    val stone400 = Color(0xFF9C9A93); val stone600 = Color(0xFF69675F); val ink = Color(0xFF141412)
    val night = Color(0xFF0C0C0B); val night900 = Color(0xFF161615); val night850 = Color(0xFF1F1F1D); val night800 = Color(0xFF282826)
    val night700 = Color(0xFF3A3935); val night500 = Color(0xFF6F6D67); val night300 = Color(0xFFA3A19A); val bone = Color(0xFFF3F2EE)

    val green700 = Color(0xFF177245); val green300 = Color(0xFF55C48C)
    val red700 = Color(0xFFC4352B); val red300 = Color(0xFFF2695C)
    val amber700 = Color(0xFF9E5E06); val amber300 = Color(0xFFE9AE4E)
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
    /** The interactive colour: ink on paper in light mode, bone on night in dark mode. */
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
    /** Category glyph colours — muted, used only on small icons, never as fills. */
    val hues: List<Color>,
    val chartGrid: Color,
    val chartMuted: Color,
    /** The physical-card surface for credit cards. */
    val cardFace: Color,
    val onCardFace: Color,
)

private val hueLight = listOf(0xFFB9552A, 0xFF866446, 0xFF2D6AA8, 0xFFAA3A5F, 0xFF6552BD, 0xFF1D7C82, 0xFF3C8A4C, 0xFF5B6270).map { Color(it) }
private val hueDark = listOf(0xFFE08A60, 0xFFC7A07C, 0xFF78AEE3, 0xFFE27E9F, 0xFFA898F0, 0xFF62C4C9, 0xFF82CC90, 0xFFA7AEBA).map { Color(it) }

val LightColors = LedgerColors(
    isDark = false,
    bg = Palette.paper, surface = Palette.white, surfaceAlt = Palette.stone100, surfaceHigh = Palette.white, hairline = Palette.stone200,
    text = Palette.ink, textMuted = Palette.stone600, textFaint = Palette.stone400,
    accent = Palette.ink, onAccent = Palette.white, accentSoft = Palette.ink.copy(alpha = 0.06f),
    positive = Palette.green700, negative = Palette.red700, caution = Palette.amber700,
    positiveSoft = Palette.green700.copy(alpha = 0.09f), negativeSoft = Palette.red700.copy(alpha = 0.08f), cautionSoft = Palette.amber700.copy(alpha = 0.10f),
    scrim = Color(0x59141412), hues = hueLight, chartGrid = Color(0xFFECEAE5), chartMuted = Color(0xFFC4C1B9),
    cardFace = Color(0xFF1B1B19), onCardFace = Palette.bone,
)

val DarkColors = LedgerColors(
    isDark = true,
    bg = Palette.night, surface = Palette.night900, surfaceAlt = Palette.night850, surfaceHigh = Palette.night800, hairline = Color(0xFF2A2A27),
    text = Palette.bone, textMuted = Palette.night300, textFaint = Palette.night500,
    accent = Palette.bone, onAccent = Palette.night, accentSoft = Palette.bone.copy(alpha = 0.08f),
    positive = Palette.green300, negative = Palette.red300, caution = Palette.amber300,
    positiveSoft = Palette.green300.copy(alpha = 0.12f), negativeSoft = Palette.red300.copy(alpha = 0.12f), cautionSoft = Palette.amber300.copy(alpha = 0.12f),
    scrim = Color(0x99000000), hues = hueDark, chartGrid = Color(0xFF232321), chartMuted = Palette.night700,
    cardFace = Palette.night800, onCardFace = Palette.bone,
)

/** Be Vietnam Pro: designed for Vietnamese diacritics, clear numerals. Bundled (OFL), so the app stays offline. */
val BeVietnam = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = BeVietnam, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false), lineHeightStyle = trim,
)

/** Generous line heights: Vietnamese stacks diacritics above and below. */
@Immutable
data class LedgerType(
    val hero: TextStyle = style(38, 46, FontWeight.Medium, -0.03),
    val display: TextStyle = style(28, 36, FontWeight.Medium, -0.025),
    val title: TextStyle = style(26, 34, FontWeight.SemiBold, -0.02),
    val headline: TextStyle = style(17, 24, FontWeight.SemiBold, -0.01),
    val section: TextStyle = style(16, 22, FontWeight.SemiBold, -0.005),
    val body: TextStyle = style(15, 22, FontWeight.Normal),
    val bodyStrong: TextStyle = style(15, 22, FontWeight.Medium),
    val label: TextStyle = style(13, 18, FontWeight.Medium),
    val caption: TextStyle = style(12, 17, FontWeight.Normal),
    val overline: TextStyle = style(12, 16, FontWeight.Medium, 0.02),
    val amount: TextStyle = style(15, 22, FontWeight.Medium),
    val numpad: TextStyle = style(28, 34, FontWeight.Normal),
)

object Space { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp; val gutter = 20.dp }

object Shapes {
    val card = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(16.dp)
    val well = RoundedCornerShape(12.dp)
    val chip = RoundedCornerShape(12.dp)
    val key = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(14.dp)
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
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
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent, onPrimary = c.onAccent, background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textMuted, outline = c.hairline, outlineVariant = c.hairline, error = c.negative,
        surfaceContainer = c.surface, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surfaceAlt, surfaceContainerHighest = c.surfaceHigh,
        surfaceContainerLowest = c.surface, inverseSurface = c.text, inverseOnSurface = c.bg,
        primaryContainer = c.accentSoft, onPrimaryContainer = c.accent, secondaryContainer = c.accentSoft, onSecondaryContainer = c.text,
        secondary = c.accent, onSecondary = c.onAccent, tertiary = c.accent,
    )
    // Material components (dialogs, date picker, menus) inherit the same typeface.
    val t = LedgerType()
    val typography = Typography().let { base ->
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = BeVietnam), displayMedium = base.displayMedium.copy(fontFamily = BeVietnam),
            displaySmall = base.displaySmall.copy(fontFamily = BeVietnam), headlineLarge = base.headlineLarge.copy(fontFamily = BeVietnam),
            headlineMedium = base.headlineMedium.copy(fontFamily = BeVietnam), headlineSmall = base.headlineSmall.copy(fontFamily = BeVietnam),
            titleLarge = base.titleLarge.copy(fontFamily = BeVietnam), titleMedium = base.titleMedium.copy(fontFamily = BeVietnam),
            titleSmall = base.titleSmall.copy(fontFamily = BeVietnam), bodyLarge = t.body, bodyMedium = t.body.copy(fontSize = 14.sp),
            bodySmall = t.caption, labelLarge = t.label.copy(fontSize = 14.sp), labelMedium = t.label, labelSmall = t.caption,
        )
    }
    CompositionLocalProvider(LocalLedgerColors provides c, LocalHideAmounts provides hideAmounts, LocalLedgerType provides t) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
