package dev.personal.ledger.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
private fun styled(text: String, format: AmountFormat, style: TextStyle): AnnotatedString {
    val muted = LedgerTheme.colors.textFaint
    if (format != AmountFormat.FULL || !text.endsWith(Money.symbol)) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.removeSuffix(Money.symbol).trimEnd())
        pushStyle(SpanStyle(fontSize = style.fontSize * 0.5f, color = muted, fontWeight = FontWeight.Medium))
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

/**
 * A changing total slides in the direction of the change (up when it grows, down when it shrinks).
 * Cheaper and calmer than a rolling counter, and it never makes proportional digits jitter.
 */
@Composable
fun AnimatedAmount(
    value: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LedgerTheme.type.hero,
    color: Color = LedgerTheme.colors.text,
    format: AmountFormat = AmountFormat.FULL,
    sign: Boolean = false,
) {
    val hidden = LocalHideAmounts.current
    AnimatedContent(
        value, modifier.semantics { contentDescription = Money.full(value) },
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(Motion.STANDARD, easing = Motion.emphasized)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(Motion.MICRO))) togetherWith
                (slideOutVertically(tween(Motion.MICRO)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(Motion.FAST))) using SizeTransform(clip = false)
        },
        label = "amount",
    ) { v ->
        Text(styled(formatAmount(v, format, sign, hidden), format, style), color = color, style = style, maxLines = 1)
    }
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
    val scale by animateFloatAsState(if (pressed && onClick != null) 0.99f else 1f, Motion.snappy(), label = "card")
    Column(
        modifier
            .scale(scale)
            .clip(Shapes.card)
            .background(color)
            .border(BorderStroke(1.dp, c.hairline), Shapes.card)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = ripple(), onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Neutral well with a coloured glyph: category identity without pastel tiles everywhere. */
@Composable
fun IconWell(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, iconSize: Dp = 20.dp, shape: Shape = Shapes.well, background: Color = LedgerTheme.colors.surfaceAlt) {
    Box(modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
fun hue(index: Int): Color = LedgerTheme.colors.hues[index.mod(LedgerTheme.colors.hues.size)]

/** Sentence-case section title with an optional quiet action on the right. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    val c = LedgerTheme.colors
    Row(modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.m).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = LedgerTheme.type.section, color = c.text, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Row(
                Modifier.clip(Shapes.chip).then(if (onTrailing != null) Modifier.clickable(onClick = onTrailing) else Modifier).padding(start = 8.dp, end = 2.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(trailing, style = LedgerTheme.type.label, color = c.textMuted)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textFaint, modifier = Modifier.size(18.dp))
            }
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
    val bg by animateColorAsState(if (selected) c.accent else c.surface, tween(Motion.MICRO), label = "chipbg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.text, tween(Motion.MICRO), label = "chipfg")
    Row(
        modifier.heightIn(min = 40.dp).clip(Shapes.chip).background(bg)
            .border(1.dp, if (selected) c.accent else c.hairline, Shapes.chip)
            .combinedClickable(onLongClick = onLongClick, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(17.dp), tint = if (selected) fg else c.textMuted); Spacer(Modifier.width(7.dp)) }
        Text(text, style = LedgerTheme.type.label, color = fg, maxLines = 1)
    }
}

enum class Tone { NEUTRAL, ACCENT, POSITIVE, NEGATIVE, CAUTION }

@Composable
fun toneColor(tone: Tone): Color = when (tone) {
    Tone.NEUTRAL -> LedgerTheme.colors.textMuted
    Tone.ACCENT -> LedgerTheme.colors.text
    Tone.POSITIVE -> LedgerTheme.colors.positive
    Tone.NEGATIVE -> LedgerTheme.colors.negative
    Tone.CAUTION -> LedgerTheme.colors.caution
}

/**
 * A quiet status marker: a small dot and a caption, no pill. Estimates use a hollow dot — the same grammar as the
 * dashed forecast line.
 */
@Composable
fun Tag(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier, dashed: Boolean = false) {
    val col = toneColor(tone)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(6.dp).clip(CircleShape)
                .then(if (dashed) Modifier.border(1.2.dp, col, CircleShape) else Modifier.background(col)),
        )
        Spacer(Modifier.width(5.dp))
        Text(text, style = LedgerTheme.type.caption, color = if (tone == Tone.NEUTRAL) LedgerTheme.colors.textMuted else col, maxLines = 1)
    }
}

/** Progress bar with an optional reference marker (expected pace, 3-month average). */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = LedgerTheme.colors.accent, track: Color = LedgerTheme.colors.surfaceAlt, height: Dp = 6.dp, marker: Float? = null) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(Motion.EMPHASIZED, easing = Motion.emphasized), label = "bar")
    val markerColor = LedgerTheme.colors.text
    val surface = LedgerTheme.colors.surface
    Box(modifier.fillMaxWidth().height(height + if (marker != null) 6.dp else 0.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
            Box(Modifier.fillMaxWidth(f).height(height).clip(CircleShape).background(color))
        }
        if (marker != null) {
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val x = size.width * marker.coerceIn(0f, 1f)
                drawLine(surface, Offset(x, 0f), Offset(x, size.height), strokeWidth = 4.dp.toPx())
                drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
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
        Icon(icon, null, Modifier.size(28.dp), tint = c.textFaint)
        Spacer(Modifier.height(Space.m))
        Text(title, style = LedgerTheme.type.headline, color = c.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = LedgerTheme.type.body, color = c.textMuted, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(Space.l))
            SecondaryButton(action, onClick = onAction)
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, Motion.snappy(), label = "btn")
    Row(
        modifier.scale(scale).heightIn(min = 52.dp).clip(Shapes.button)
            .background(if (enabled) c.accent else c.surfaceAlt)
            .clickable(interaction, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
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
        modifier.heightIn(min = 48.dp).clip(Shapes.button).border(1.dp, c.hairline, Shapes.button).background(c.surface)
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
