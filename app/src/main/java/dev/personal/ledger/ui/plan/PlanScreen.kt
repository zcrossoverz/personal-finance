package dev.personal.ledger.ui.plan

import dev.personal.ledger.ui.common.TabTitle
import dev.personal.ledger.i18n.tr
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personal.ledger.data.InstallmentStatus
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.domain.Certainty
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.ForecastCalc
import dev.personal.ledger.domain.Installments
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.ObKind
import dev.personal.ledger.domain.Obligations
import dev.personal.ledger.domain.SafeToSpendCalc
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.Route
import dev.personal.ledger.ui.SheetRequest
import dev.personal.ledger.ui.charts.ForecastChart
import dev.personal.ledger.ui.charts.ForecastPointUi
import dev.personal.ledger.ui.common.ObligationRow
import dev.personal.ledger.ui.common.obligationEntry
import dev.personal.ledger.ui.components.Amount
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.Chip
import dev.personal.ledger.ui.components.Divider
import dev.personal.ledger.ui.components.EmptyState
import dev.personal.ledger.ui.components.LedgerCard
import dev.personal.ledger.ui.components.PrimaryButton
import dev.personal.ledger.ui.components.SectionHeader
import dev.personal.ledger.ui.components.Tag
import dev.personal.ledger.ui.components.Tone
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import java.time.YearMonth

private enum class PlanFilter(val kinds: Set<ObKind>) {
    ALL(ObKind.entries.toSet()),
    BILLS(setOf(ObKind.BILL)),
    SUBS(setOf(ObKind.SUBSCRIPTION)),
    INSTALLMENTS(setOf(ObKind.INSTALLMENT)),
    CARDS(setOf(ObKind.CARD)),
    INCOME(setOf(ObKind.INCOME));

    val label: String get() = when (this) {
        ALL -> tr("All"); BILLS -> tr("Bills"); SUBS -> tr("Subscriptions"); INSTALLMENTS -> tr("Installments"); CARDS -> tr("Cards"); INCOME -> tr("Income")
    }
}

/** "What's coming and can I afford it?" — forecast, then the obligation timeline grouped by month. */
@Composable
fun PlanScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav) {
    val c = LedgerTheme.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timeline = remember(d) { Obligations.upcoming(d.data, d.today, d.today.plusDays(92), d.balances) }
    val forecast = remember(d, settings.forecastIncludesEstimate) { ForecastCalc.compute(d.data, settings, d.today, 45, d.balances) }
    var filter by rememberSaveable { mutableStateOf(PlanFilter.ALL) }
    val shown = timeline.filter { it.kind in filter.kinds }
    val next30 = timeline.filter { !it.date.isAfter(d.today.plusDays(30)) }

    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "title") {
            TabTitle(tr("Plan"), tr("Next 30 days · %s out · %s in",
                Money.compact(next30.filter { !it.isInflow && it.cashDelta != 0L }.sumOf { it.amount }), Money.compact(next30.filter { it.isInflow }.sumOf { it.amount })))
        }
        item(key = "sts") { SafeStrip(d.safe) { nav.open(SheetRequest.SafeToSpend) } }
        // A forecast needs something to forecast: a balance or at least one scheduled item.
        if (forecast.events.isNotEmpty() || forecast.points.first().known != 0L) item(key = "forecast") { ForecastCard(forecast, settings.forecastIncludesEstimate) }
        item(key = "tiles") { SummaryTiles(d, nav) }
        item(key = "filters") {
            Column {
                SectionHeader(tr("Timeline"), Modifier.padding(top = Space.m))
                LazyRow(contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    items(PlanFilter.entries) { f -> Chip(f.label, f == filter) { filter = f } }
                }
                Spacer(Modifier.height(Space.s))
            }
        }
        if (shown.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Rounded.EventAvailable, tr("Nothing scheduled"), tr("Add bills, subscriptions or installments and they will appear here with their due dates."),
                action = tr("Add a bill")) { nav.push(Route.EditRecurring(null)) }
        }
        shown.groupBy { YearMonth.from(if (it.overdue) d.today else it.date) }.forEach { (m, list) ->
            item(key = "m-$m") { MonthHeader(m, list, m == YearMonth.from(d.today).plusMonths(1)) }
            items(list, key = { "p-" + it.key }) { o ->
                ObligationRow(o, d.today, onClick = { obligationEntry(o, d, settings.defaultAccountId)?.let(nav::entry) }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun MonthHeader(m: YearMonth, list: List<dev.personal.ledger.domain.Obligation>, isNext: Boolean) {
    val c = LedgerTheme.colors
    val out = list.filter { !it.isInflow && it.cashDelta != 0L }.sumOf { it.amount }
    val onCard = list.filter { !it.isInflow && it.cashDelta == 0L && it.kind != ObKind.CARD }.sumOf { it.amount }
    val income = list.filter { it.isInflow }.sumOf { it.amount }
    Row(Modifier.fillMaxWidth().background(c.bg).padding(start = Space.gutter, end = Space.gutter, top = Space.l, bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.month(m), style = LedgerTheme.type.headline, color = c.text)
                if (isNext) { Spacer(Modifier.width(6.dp)); Tag(tr("Next month"), Tone.ACCENT) }
            }
            if (onCard > 0) Text(tr("+ %s charged to cards (paid via card dues)", Money.compact(onCard)), style = LedgerTheme.type.caption, color = c.textFaint)
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(-out, style = LedgerTheme.type.bodyStrong)
            if (income > 0) Amount(income, style = LedgerTheme.type.caption, color = c.positive, sign = true)
        }
    }
}

@Composable
private fun SafeStrip(s: SafeToSpendCalc.Result, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    LedgerCard(Modifier.padding(horizontal = Space.l, vertical = Space.s).fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Safe to spend until %s", Fmt.dayMonth(s.horizon)), style = LedgerTheme.type.label, color = c.textMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Amount(s.safe, style = LedgerTheme.type.display, format = AmountFormat.COMPACT, color = if (s.safe < 0) c.negative else c.text)
                    Text("  " + tr("%s/day", Money.compact(s.perDay)), style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Text(tr("How?"), style = LedgerTheme.type.label, color = c.textMuted)
            Icon(Icons.Rounded.ChevronRight, null, tint = c.textFaint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ForecastCard(f: ForecastCalc.Result, withEstimate: Boolean) {
    val c = LedgerTheme.colors
    var sel by remember { mutableStateOf<Int?>(null) }
    val points = remember(f) {
        f.points.map { p -> ForecastPointUi(Fmt.dayMonth(p.date), p.known, p.withEstimate, p.events.filter { it.cashDelta > 0 }.sumOf { it.cashDelta }, p.events.filter { it.cashDelta < 0 }.sumOf { -it.cashDelta }) }
    }
    LedgerCard(Modifier.padding(horizontal = Space.l, vertical = Space.s).fillMaxWidth()) {
        val p = sel?.let { f.points[it] }
        val lowKnown = remember(f) { f.points.minBy { it.known } }
        val shown = p ?: lowKnown
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(if (p == null) tr("Lowest scheduled balance · %s", Fmt.dayMonth(lowKnown.date)) else Fmt.full(p.date), style = LedgerTheme.type.label, color = c.textMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Amount(shown.known, style = LedgerTheme.type.headline, format = AmountFormat.FULL, color = if (shown.known < 0) c.negative else c.text)
                    if (withEstimate && f.dailyEstimate > 0) {
                        Text("  " + tr("≈ %s with everyday spending", Money.compact(shown.withEstimate)), style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                val ev = p?.events.orEmpty()
                Text(
                    when {
                        p == null -> tr("Next 45 days of bills, cards, installments and salary · drag to inspect")
                        ev.isEmpty() -> tr("No scheduled items this day")
                        else -> ev.joinToString(" · ") { "${it.title} ${Money.compact(it.cashDelta, sign = true)}" }
                    },
                    style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        ForecastChart(points, sel, { sel = it }, showEstimate = withEstimate && f.dailyEstimate > 0)
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendLine(c.accent, dashed = false); Text(" " + tr("Scheduled"), style = LedgerTheme.type.caption, color = c.textMuted)
            if (withEstimate && f.dailyEstimate > 0) {
                Spacer(Modifier.width(Space.m))
                LegendLine(c.accent.copy(alpha = 0.55f), dashed = true); Text(" " + tr("+ everyday ≈ %s/day", Money.compact(f.dailyEstimate)), style = LedgerTheme.type.caption, color = c.textMuted)
            }
        }
        val low = f.lowest
        if (low.withEstimate < f.points.first().known) {
            Spacer(Modifier.height(6.dp))
            // Red only if scheduled items alone go below zero; an estimate dipping is a caution, not a fact.
            val tone = when { f.points.any { it.known < 0 } -> c.negative; low.withEstimate < 0 -> c.caution; else -> c.textMuted }
            Text(if (low.withEstimate < 0) tr("Lowest point ≈ %s on %s — below zero. Estimate, not a fact.", Money.compact(low.withEstimate), Fmt.dayMonth(low.date)) else tr("Lowest point ≈ %s on %s. Estimate, not a fact.", Money.compact(low.withEstimate), Fmt.dayMonth(low.date)),
                style = LedgerTheme.type.caption, color = tone)
        }
    }
}

@Composable
private fun LegendLine(color: Color, dashed: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp, 10.dp)) {
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, size.height / 2), androidx.compose.ui.geometry.Offset(size.width, size.height / 2), 2.dp.toPx(),
            pathEffect = if (dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null)
    }
}

@Composable
private fun SummaryTiles(d: Dashboard, nav: Nav) {
    val subs = d.data.recurring.filter { it.active && it.kind == RecurringKind.SUBSCRIPTION }
    val bills = d.data.recurring.filter { it.active && it.kind == RecurringKind.BILL }
    val plans = d.data.installments.filter { it.status == InstallmentStatus.ACTIVE }
    val planProgress = plans.map { Installments.progress(d.data, it) }
    Row(Modifier.padding(horizontal = Space.l, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        Tile(tr("Subscriptions"), tr("per month · %d active", subs.size), subs.sumOf { it.cadence.monthly(it.amount) }, Modifier.weight(1f)) { nav.push(Route.RecurringList(RecurringKind.SUBSCRIPTION)) }
        Tile(tr("Installments"), tr("per month · %s left", Money.compact(planProgress.sumOf { it.remaining })), planProgress.sumOf { if (it.nextIndex != null) it.nextAmount else 0 }, Modifier.weight(1f)) { nav.push(Route.InstallmentList) }
        Tile(tr("Bills"), tr("per month · %d recurring", bills.size), bills.sumOf { it.cadence.monthly(it.amount) }, Modifier.weight(1f)) { nav.push(Route.RecurringList(RecurringKind.BILL)) }
    }
}

@Composable
private fun Tile(title: String, sub: String, amount: Long, modifier: Modifier, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    LedgerCard(modifier, onClick = onClick, padding = PaddingValues(14.dp)) {
        Text(title, style = LedgerTheme.type.label, color = c.textMuted, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Amount(amount, style = LedgerTheme.type.headline)
        Text(sub, style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 2)
    }
}

/** The formula behind Safe to spend, every term visible, with the assumptions it rests on. */
@Composable
fun ColumnScope.SafeToSpendSheet(d: Dashboard, nav: Nav) {
    val c = LedgerTheme.colors
    val s = d.safe
    var showItems by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.gutter).padding(bottom = Space.l).animateContentSize(Motion.gentle())) {
        Text(tr("Safe to spend"), style = LedgerTheme.type.headline, color = c.text)
        Text(if (s.salary != null) tr("Until %s on %s", s.salary.name, Fmt.full(s.horizon)) else tr("Until %s", Fmt.full(s.horizon)), style = LedgerTheme.type.caption, color = c.textMuted)
        Spacer(Modifier.height(Space.l))
        val everyday = d.data.accounts.filter { it.spendable && dev.personal.ledger.domain.Ledger.isEveryday(it) }
        FormulaRow(tr("Spendable cash"), s.spendable, everyday.joinToString(" · ") { "${it.name} ${Money.compact(d.balances[it.id] ?: 0)}" }, null)
        FormulaRow(tr("Card dues"), -s.cardDues, s.items.filter { it.kind == ObKind.CARD }.joinToString(" · ") { "${it.title} ${Fmt.dayMonth(it.date)}" + if (it.certainty == Certainty.ESTIMATED) " " + tr("(est.)") else "" }.ifBlank { tr("None before pay day") }, "−")
        FormulaRow(tr("Bills, subscriptions, installments"), -s.obligations, tr("%d items before pay day", s.items.count { it.kind != ObKind.CARD }), "−") { showItems = !showItems }
        if (showItems) {
            s.items.filter { it.kind != ObKind.CARD }.forEach { o ->
                Row(Modifier.fillMaxWidth().padding(start = Space.l, top = 4.dp, bottom = 4.dp)) {
                    Text("${Fmt.dayMonth(o.date)}  ${o.title}" + if (o.certainty == Certainty.ESTIMATED) " " + tr("(est.)") else "", style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.weight(1f))
                    Amount(-o.amount, style = LedgerTheme.type.caption, color = c.textMuted)
                }
            }
        }
        FormulaRow(tr("Protected savings + reserve"), -s.protected, tr("Set in Settings"), "−")
        Spacer(Modifier.height(Space.s)); Divider(); Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(tr("= Safe to spend"), style = LedgerTheme.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
            Amount(s.safe, style = LedgerTheme.type.display, format = AmountFormat.COMPACT, color = if (s.safe < 0) c.negative else c.text)
        }
        Text(tr("÷ %d days = %s per day", s.daysLeft, Money.full(s.perDay)), style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.align(Alignment.End))
        Spacer(Modifier.height(Space.l))
        Text(tr("Assumptions"), style = LedgerTheme.type.section, color = c.text)
        Spacer(Modifier.height(6.dp))
        val base = listOf(
            tr("Income arriving before pay day is not counted."),
            tr("Money in savings accounts and on cards is excluded."),
            tr("Purchases on cards are counted when the statement is due."),
        )
        (s.notes.map { it to true } + base.map { it to false }).forEach { (n, warn) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(if (warn) c.caution else c.textFaint))
                Spacer(Modifier.width(Space.s))
                Text(n, style = LedgerTheme.type.label, color = if (warn) c.text else c.textMuted)
            }
        }
        Spacer(Modifier.height(Space.l))
        PrimaryButton(tr("Adjust assumptions"), Modifier.fillMaxWidth(), icon = Icons.Rounded.Tune) { nav.closeSheet(); nav.push(Route.Settings) }
    }
}

@Composable
private fun FormulaRow(label: String, value: Long, detail: String, op: String?, onClick: (() -> Unit)? = null) {
    val c = LedgerTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(Shapes.chip).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 52.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = LedgerTheme.type.bodyStrong, color = c.text)
            Text(detail, style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.s))
        Amount(value, style = LedgerTheme.type.bodyStrong, format = AmountFormat.NUMBER, color = if (op != null && value != 0L) c.textMuted else c.text)
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = c.textFaint, modifier = Modifier.size(18.dp))
    }
}
