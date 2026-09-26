package dev.personal.ledger.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.personal.ledger.domain.Money
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.LocalHideAmounts
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space

// ---------- haptics ----------

object Haptics {
    fun confirm(view: View) = view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
    fun tick(view: View) = view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.VIRTUAL_KEY)
    fun scrub(view: View) = view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun long(view: View) = view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

// ---------- amounts ----------

enum class AmountFormat { FULL, COMPACT, NUMBER }

fun formatAmount(v: Long, format: AmountFormat, sign: Boolean = false, hidden: Boolean = false): String = when {
    hidden -> "•••"
    format == AmountFormat.FULL -> Money.full(v, sign)
    format == AmountFormat.COMPACT -> Money.compact(v, sign)
    else -> Money.group(v).let { if (sign && v > 0) "+$it" else it }
}

/** Full amounts render the currency symbol smaller and muted so the digits stay the hero. */
@Composable
private fun styled(text: String, format: AmountFormat, style: TextStyle): androidx.compose.ui.text.AnnotatedString {
    val muted = LedgerTheme.colors.textMuted
    if (format != AmountFormat.FULL || !text.endsWith(Money.symbol)) return androidx.compose.ui.text.AnnotatedString(text)
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text.removeSuffix(Money.symbol).trimEnd())
        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = style.fontSize * 0.55f, color = muted, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
        append(" " + Money.symbol)
        pop()
    }
}

@Composable
fun Amount(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LedgerTheme.type.amount,
    color: Color = LedgerTheme.colors.text,
    format: AmountFormat = AmountFormat.COMPACT,
    sign: Boolean = false,
) {
    Text(styled(formatAmount(value, format, sign, LocalHideAmounts.current), format, style), modifier, color = color, style = style, maxLines = 1)
}

/** Rolls smoothly from the previous value to the new one (totals after a save). Never blocks input. */
@Composable
fun AnimatedAmount(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LedgerTheme.type.hero,
    color: Color = LedgerTheme.colors.text,
    format: AmountFormat = AmountFormat.FULL,
    sign: Boolean = false,
) {
    var from by remember { mutableLongStateOf(value) }
    var to by remember { mutableLongStateOf(value) }
    val t = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value != to) {
            from = (from + (to - from) * t.value).toLong()
            to = value
            t.snapTo(0f)
            t.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    val shown = from + ((to - from) * t.value).toLong()
    Text(styled(formatAmount(shown, format, sign, LocalHideAmounts.current), format, style), modifier.semantics { contentDescription = Money.full(value) },
        color = color, style = style, maxLines = 1)
}

// ---------- surfaces ----------

@Composable
fun LedgerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Space.l),
    color: Color = LedgerTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LedgerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.985f else 1f, Motion.snappy(), label = "card")
    Column(
        modifier
            .scale(scale)
            .clip(Shapes.card)
            .background(color)
            .then(if (!c.isDark) Modifier.border(BorderStroke(1.dp, c.hairline), Shapes.card) else Modifier)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = androidx.compose.material3.ripple(), onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun IconWell(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, iconSize: Dp = 22.dp, shape: androidx.compose.ui.graphics.Shape = Shapes.well, soft: Float = 0.14f) {
    Box(modifier.size(size).clip(shape).background(tint.copy(alpha = soft)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun hue(index: Int): Color = LedgerTheme.colors.hues[index.mod(LedgerTheme.colors.hues.size)]

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Space.gutter).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = LedgerTheme.type.overline, color = LedgerTheme.colors.textMuted, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing, style = LedgerTheme.type.label, color = LedgerTheme.colors.accent,
                modifier = Modifier.clip(Shapes.chip).then(if (onTrailing != null) Modifier.clickable(onClick = onTrailing) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LedgerTheme.colors
    val bg by animateColorAsState(if (selected) c.accent else c.surfaceAlt, tween(Motion.MICRO), label = "chipbg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.text, tween(Motion.MICRO), label = "chipfg")
    Row(
        modifier.heightIn(min = 40.dp).clip(Shapes.chip).background(bg)
            .combinedClickable(onLongClick = onLongClick, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = fg); Spacer(Modifier.width(6.dp)) }
        Text(text, style = LedgerTheme.type.label, color = fg, maxLines = 1)
    }
}

enum class Tone { NEUTRAL, ACCENT, POSITIVE, NEGATIVE, CAUTION }

@Composable
fun Tag(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, dashed: Boolean = false) {
    val c = LedgerTheme.colors
    val (bg, fg) = when (tone) {
        Tone.NEUTRAL -> c.surfaceAlt to c.textMuted
        Tone.ACCENT -> c.accentSoft to c.accent
        Tone.POSITIVE -> c.positiveSoft to c.positive
        Tone.NEGATIVE -> c.negativeSoft to c.negative
        Tone.CAUTION -> c.cautionSoft to c.caution
    }
    Text(
        text, style = LedgerTheme.type.caption, color = fg, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(bg)
            .then(if (dashed) Modifier.border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(8.dp)) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/** Progress bar with an optional reference marker (e.g. expected pace, 3-month average). */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = LedgerTheme.colors.accent, track: Color = LedgerTheme.colors.surfaceAlt, height: Dp = 6.dp, marker: Float? = null) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(Motion.EMPHASIZED, easing = Motion.emphasized), label = "bar")
    val markerColor = LedgerTheme.colors.text
    Box(modifier.fillMaxWidth().height(height + if (marker != null) 6.dp else 0.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
            Box(Modifier.fillMaxWidth(f).height(height).clip(CircleShape).background(color))
        }
        if (marker != null) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val x = size.width * marker.coerceIn(0f, 1f)
                drawLine(markerColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(LedgerTheme.colors.hairline))
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LedgerTheme.colors
    Column(modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = Space.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        IconWell(icon, c.textMuted, size = 52.dp, iconSize = 26.dp, shape = CircleShape, soft = 0.10f)
        Spacer(Modifier.height(Space.m))
        Text(title, style = LedgerTheme.type.headline, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(body, style = LedgerTheme.type.body, color = c.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(Space.l))
            PrimaryButton(action, onClick = onAction)
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Motion.snappy(), label = "btn")
    Row(
        modifier.scale(scale).heightIn(min = 52.dp).clip(Shapes.pill)
            .background(if (enabled) c.accent else c.surfaceAlt)
            .clickable(interaction, androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) c.onAccent else c.textFaint); Spacer(Modifier.width(8.dp)) }
        Text(text, style = LedgerTheme.type.bodyStrong, color = if (enabled) c.onAccent else c.textFaint)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = LedgerTheme.colors.text, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Row(
        modifier.heightIn(min = 48.dp).clip(Shapes.pill).background(c.surfaceAlt)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp), tint = color); Spacer(Modifier.width(8.dp)) }
        Text(text, style = LedgerTheme.type.label, color = color)
    }
}

/** A tappable list row with a leading well, title/subtitle and trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = LedgerTheme.colors
    Row(
        modifier.fillMaxWidth()
            .then(if (onClick != null || onLongClick != null) Modifier.combinedClickable(onLongClick = onLongClick, onClick = onClick ?: {}) else Modifier)
            .heightIn(min = 60.dp).padding(horizontal = Space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(Space.m)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = LedgerTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.s))
        trailing()
    }
}

/** Two-line metric: small label above, number below. */
@Composable
fun Metric(label: String, value: Long, modifier: Modifier = Modifier, color: Color = LedgerTheme.colors.text, sign: Boolean = false, style: TextStyle = LedgerTheme.type.headline) {
    Column(modifier) {
        Text(label, style = LedgerTheme.type.caption, color = LedgerTheme.colors.textMuted, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Amount(value, style = style, color = color, sign = sign)
    }
}
