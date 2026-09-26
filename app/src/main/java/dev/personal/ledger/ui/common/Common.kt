package dev.personal.ledger.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.TxType
import dev.personal.ledger.data.Txn
import dev.personal.ledger.domain.Certainty
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Obligation
import dev.personal.ledger.domain.toLocalDate
import dev.personal.ledger.ui.components.Amount
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.Haptics
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.Tag
import dev.personal.ledger.ui.components.Tone
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Space
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** Standard pushed screen: back arrow, title, optional trailing actions, lazy content. */
@Composable
fun Screen(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val c = LedgerTheme.colors
    Column(modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = c.text)
                }
            } else Spacer(Modifier.width(Space.l))
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = LedgerTheme.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 1)
            }
            actions()
        }
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 120.dp), content = content)
    }
}

@Composable
fun IconAction(icon: ImageVector, description: String, tint: Color = LedgerTheme.colors.text, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Presentation of a transaction: icon, colour, title and subtitle derived consistently everywhere. */
data class TxVisual(val icon: ImageVector, val colorIndex: Int?, val title: String, val subtitle: String, val tag: String?)

fun txVisual(t: Txn, data: LedgerData): TxVisual {
    val cat = data.categoryById[t.categoryId]
    val acc = data.accountById[t.accountId]?.name ?: "?"
    val time = Fmt.time(t.date)
    val splits = data.splitsByTx[t.id]
    return when (t.type) {
        TxType.TRANSFER -> {
            val to = data.accountById[t.toAccountId]
            val isCardPayment = to?.type == dev.personal.ledger.data.AccountType.CREDIT_CARD
            TxVisual(LedgerIcons.of(if (isCardPayment) "credit_card" else "transfer"), null,
                t.note.ifBlank { if (isCardPayment) "Card payment" else "Transfer" }, "$acc → ${to?.name ?: "?"} · $time", if (isCardPayment) "Card payment" else "Transfer")
        }
        TxType.REFUND, TxType.REIMBURSEMENT -> TxVisual(LedgerIcons.of("refund"), cat?.colorIndex,
            t.note.ifBlank { cat?.name ?: "Refund" }, "${cat?.name ?: ""} · $acc · $time".trimStart(' ', '·'),
            if (t.type == TxType.REFUND) "Refund" else "Paid back")
        TxType.ADJUSTMENT -> TxVisual(LedgerIcons.of("more"), null, "Balance adjustment", "$acc · $time", null)
        else -> {
            val preset = data.presetById[t.presetId]
            val title = when {
                splits != null -> t.note.ifBlank { "Split · ${splits.size} categories" }
                t.note.isNotBlank() -> t.note
                else -> cat?.name ?: preset?.label ?: "Expense"
            }
            val catLabel = if (splits != null) splits.joinToString(" + ") { data.categoryById[it.categoryId]?.name ?: "?" } else cat?.name ?: ""
            val sub = listOf(if (t.note.isNotBlank() || splits != null) catLabel else "", acc, time).filter { it.isNotBlank() }.joinToString(" · ")
            TxVisual(LedgerIcons.of(cat?.icon ?: preset?.icon), cat?.colorIndex ?: preset?.colorIndex, title, sub,
                if (t.recurringId != null) "Scheduled" else if (t.installmentId != null) "Installment" else null)
        }
    }
}

/** Signed amount shown for a transaction from the user's cash-flow point of view. */
fun txSigned(t: Txn): Long = when (t.type) {
    TxType.EXPENSE -> -t.amount
    TxType.INCOME, TxType.REFUND, TxType.REIMBURSEMENT -> t.amount
    TxType.TRANSFER -> t.amount
    TxType.ADJUSTMENT -> t.amount
}

@Composable
fun TxRow(t: Txn, data: LedgerData, modifier: Modifier = Modifier, showDate: Boolean = false, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val v = txVisual(t, data)
    val tint = v.colorIndex?.let { hue(it) } ?: c.textMuted
    Row(
        modifier.fillMaxWidth().background(c.bg).clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = Space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconWell(v.icon, tint)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = LedgerTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (showDate) "${Fmt.dayMonth(t.date.toLocalDate())} · ${v.subtitle}" else v.subtitle
            Text(sub, style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.s))
        Column(horizontalAlignment = Alignment.End) {
            val signed = txSigned(t)
            val color = when (t.type) {
                TxType.INCOME, TxType.REFUND, TxType.REIMBURSEMENT -> c.positive
                TxType.TRANSFER -> c.textMuted
                else -> c.text
            }
            Amount(if (t.type == TxType.TRANSFER) t.amount else signed, color = color, format = AmountFormat.NUMBER, sign = t.type != TxType.TRANSFER && t.type != TxType.EXPENSE)
            if (v.tag != null) Text(v.tag, style = LedgerTheme.type.caption, color = c.textFaint)
        }
    }
}

/**
 * Swipe ← to delete, → to duplicate. The action colour and icon reveal under the row as it moves,
 * with a haptic tick when the threshold is crossed. Row snaps back after duplicate.
 */
@Composable
fun SwipeRow(onDelete: () -> Unit, onDuplicate: () -> Unit, content: @Composable () -> Unit) {
    val c = LedgerTheme.colors
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    val crossed = remember { booleanArrayOf(false) }
    Box(Modifier.fillMaxWidth()) {
        val x = offset.value
        Row(
            Modifier.matchParentSize().background(if (x < 0) c.negativeSoft else if (x > 0) c.accentSoft else Color.Transparent).padding(horizontal = Space.xxl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (x < 0) Arrangement.End else Arrangement.Start,
        ) {
            val active = abs(x) > threshold
            if (x > 0) {
                Icon(Icons.Rounded.ContentCopy, null, tint = c.accent, modifier = Modifier.graphicsLayer { scaleX = if (active) 1.15f else 0.9f; scaleY = scaleX })
                Spacer(Modifier.width(8.dp)); Text("Duplicate", style = LedgerTheme.type.label, color = c.accent)
            } else if (x < 0) {
                Text("Delete", style = LedgerTheme.type.label, color = c.negative); Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.DeleteOutline, null, tint = c.negative, modifier = Modifier.graphicsLayer { scaleX = if (active) 1.15f else 0.9f; scaleY = scaleX })
            }
        }
        Box(
            Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }.draggable(
                rememberDraggableState { d ->
                    scope.launch {
                        offset.snapTo(offset.value + d)
                        val over = abs(offset.value) > threshold
                        if (over != crossed[0]) { crossed[0] = over; if (over) Haptics.tick(view) }
                    }
                },
                Orientation.Horizontal,
                onDragStopped = {
                    val v = offset.value
                    when {
                        v < -threshold -> { offset.animateTo(-2000f, androidx.compose.animation.core.tween(Motion.MICRO)); onDelete(); offset.snapTo(0f) }
                        v > threshold -> { onDuplicate(); offset.animateTo(0f, Motion.snappy()) }
                        else -> offset.animateTo(0f, Motion.snappy())
                    }
                    crossed[0] = false
                },
            ),
        ) { content() }
    }
}

/** Day badge used in timelines: big day number with weekday underneath. */
@Composable
fun DateBadge(date: LocalDate, today: LocalDate, overdue: Boolean = false) {
    val c = LedgerTheme.colors
    val isToday = date == today
    Column(
        Modifier.width(44.dp).clip(dev.personal.ledger.ui.theme.Shapes.well)
            .background(when { overdue -> c.negativeSoft; isToday -> c.accentSoft; else -> c.surfaceAlt }).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(date.dayOfMonth.toString(), style = LedgerTheme.type.headline, color = if (overdue) c.negative else if (isToday) c.accent else c.text)
        Text(Fmt.weekday(date).uppercase(), style = LedgerTheme.type.caption, color = if (overdue) c.negative else c.textMuted)
    }
}

/** One upcoming obligation. Estimates are labelled as such; variable bills invite amount entry. */
@Composable
fun ObligationRow(o: Obligation, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = Space.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateBadge(o.date, today, o.overdue)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(LedgerIcons.of(o.icon), null, tint = hue(o.colorIndex), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(o.title, style = LedgerTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val due = if (o.overdue) "Overdue · ${Fmt.dueIn(o.date, today)}" else o.subtitle.ifBlank { Fmt.dueIn(o.date, today) }
            Text(due, style = LedgerTheme.type.caption, color = if (o.overdue) c.negative else c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.s))
        Column(horizontalAlignment = Alignment.End) {
            if (o.variable && o.actionable) {
                Text("≈ " + dev.personal.ledger.domain.Money.compact(o.amount), style = LedgerTheme.type.amount, color = c.textMuted)
                Tag("Enter amount", Tone.ACCENT)
            } else {
                Amount(if (o.isInflow) o.amount else -o.amount, color = if (o.isInflow) c.positive else c.text, sign = o.isInflow,
                    modifier = Modifier.graphicsLayer { alpha = if (o.certainty == Certainty.ESTIMATED) 0.75f else 1f })
                when (o.certainty) {
                    Certainty.ESTIMATED -> Tag("Estimate", Tone.CAUTION, dashed = true)
                    Certainty.CONFIRMED -> if (o.cashDelta == 0L && !o.isInflow) Text("on card", style = LedgerTheme.type.caption, color = c.textFaint)
                    Certainty.EXPECTED -> if (o.cashDelta == 0L && !o.isInflow) Text("on card", style = LedgerTheme.type.caption, color = c.textFaint)
                }
            }
        }
    }
}

/** Group transactions by local date, newest first. */
fun groupByDay(txs: List<Txn>): List<Pair<LocalDate, List<Txn>>> =
    txs.groupBy { it.date.toLocalDate() }.toList().sortedByDescending { it.first }

@Composable
fun DayHeader(date: LocalDate, today: LocalDate, total: Long, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    Row(modifier.fillMaxWidth().background(c.bg).padding(start = Space.gutter, end = Space.gutter, top = Space.l, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.relativeDay(date, today), style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.weight(1f))
        if (total != 0L) Amount(-total, style = LedgerTheme.type.label, color = c.textMuted, format = AmountFormat.COMPACT)
    }
}
