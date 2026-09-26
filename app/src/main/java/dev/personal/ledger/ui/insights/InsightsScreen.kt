package dev.personal.ledger.ui.insights

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import dev.personal.ledger.data.Settings
import dev.personal.ledger.data.TxType
import dev.personal.ledger.domain.Analytics
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.InsightsCalc
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.toLocalDate
import dev.personal.ledger.domain.startMillis
import dev.personal.ledger.domain.endMillis
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.Route
import dev.personal.ledger.ui.SheetRequest
import dev.personal.ledger.ui.charts.CalendarHeatmap
import dev.personal.ledger.ui.charts.CumulativeChart
import dev.personal.ledger.ui.charts.FlowNode
import dev.personal.ledger.ui.charts.MoneyFlowChart
import dev.personal.ledger.ui.charts.MonthBarUi
import dev.personal.ledger.ui.charts.MonthBarsChart
import dev.personal.ledger.ui.charts.StackedBar
import dev.personal.ledger.ui.common.TxRow
import dev.personal.ledger.ui.components.Amount
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.Bar
import dev.personal.ledger.ui.components.EmptyState
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerCard
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.Metric
import dev.personal.ledger.ui.components.SectionHeader
import dev.personal.ledger.ui.components.Tag
import dev.personal.ledger.ui.components.Tone
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

private data class InsightsModel(
    val month: Analytics.Month,
    val previous: Analytics.Month,
    val insights: List<InsightsCalc.Insight>,
    val bars: List<Analytics.MonthBar>,
)

@Composable
fun InsightsScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav) {
    val c = LedgerTheme.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    var monthStr by rememberSaveable { mutableStateOf(YearMonth.from(d.today).toString()) }
    val month = YearMonth.parse(monthStr)
    val current = YearMonth.from(d.today)
    val model by produceState<InsightsModel?>(null, d.data, month, settings) {
        value = withContext(Dispatchers.Default) { compute(d, settings, month) }
    }

    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "title") {
            Row(Modifier.statusBarsPadding().padding(start = Space.gutter, end = 6.dp, top = Space.m, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Insights", style = LedgerTheme.type.title, color = c.text)
                    Text(Fmt.monthYear(month) + if (month == current) " · day ${d.today.dayOfMonth}/${month.lengthOfMonth()}" else "", style = LedgerTheme.type.label, color = c.textMuted)
                }
                MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month") { monthStr = month.minusMonths(1).toString() }
                MonthArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", enabled = month < current) { monthStr = month.plusMonths(1).toString() }
            }
        }
        val m = model
        if (m == null) { item { Box(Modifier.fillMaxWidth().height(400.dp)) }; return@LazyColumn }
        if (m.month.spending == 0L && m.month.income == 0L) {
            item { EmptyState(Icons.Rounded.Insights, "No activity in ${Fmt.month(month)}", "Insights appear once there are transactions in this month.") }
            if (m.bars.any { it.spending > 0 || it.income > 0 }) item { SixMonths(m, month) { monthStr = it.toString() } }
            return@LazyColumn
        }
        item(key = "flow") { CashFlowCard(m) }
        item(key = "pace") { PaceCard(m, month == current) }
        if (m.insights.isNotEmpty()) {
            item(key = "ins-h") { SectionHeader("What stands out", Modifier.padding(top = Space.m)) }
            item(key = "ins") { InsightList(m.insights) { id -> nav.push(Route.CategoryDetail(id, month)) } }
        }
        item(key = "cat-h") { SectionHeader("Where it went", Modifier.padding(top = Space.m)) }
        item(key = "cats") { CategoryBreakdown(m.month) { id -> nav.push(Route.CategoryDetail(id, month)) } }
        item(key = "fixed") { FixedFlexible(m.month) }
        item(key = "heat-h") { SectionHeader("Daily spending", Modifier.padding(top = Space.m)) }
        item(key = "heat") { Heatmap(m.month, d.today, nav) }
        if (m.month.income > 0) {
            item(key = "sankey-h") { SectionHeader("Money flow", Modifier.padding(top = Space.m)) }
            item(key = "sankey") { FlowCard(m.month) }
        }
        item(key = "six-h") { SectionHeader("Last 6 months", Modifier.padding(top = Space.m)) }
        item(key = "six") { SixMonths(m, month) { monthStr = it.toString() } }
    }
}

private fun compute(d: Dashboard, settings: Settings, month: YearMonth): InsightsModel {
    val m = Analytics.month(d.data, month, d.today)
    val prev = Analytics.month(d.data, month.minusMonths(1), d.today)
    val endMonth = if (month < YearMonth.from(d.today).minusMonths(2)) month.plusMonths(2) else YearMonth.from(d.today)
    return InsightsModel(m, prev, InsightsCalc.compute(d.data, settings, d.today, m), Analytics.months(d.data, endMonth, 6))
}

@Composable
private fun MonthArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = if (enabled) c.text else c.textFaint.copy(alpha = 0.4f))
    }
}

/** Month cash flow: income, spending, kept. Net worth lives on the Money tab — never mixed in here. */
@Composable
private fun CashFlowCard(m: InsightsModel) {
    val c = LedgerTheme.colors
    val mo = m.month
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        Text("Cash flow", style = LedgerTheme.type.label, color = c.textMuted)
        Spacer(Modifier.height(Space.s))
        Row {
            FlowMetric("Income", mo.income, m.previous.income, c.positive, Modifier.weight(1f), higherIsGood = true)
            FlowMetric("Spending", mo.spending, if (mo.isPartial) m.previous.cumulative.getOrElse(mo.throughDay - 1) { m.previous.spending } else m.previous.spending, c.text, Modifier.weight(1f), higherIsGood = false)
            FlowMetric("Kept", mo.net, null, if (mo.net < 0) c.negative else c.text, Modifier.weight(1f), higherIsGood = true)
        }
        if (mo.income > 0) {
            Spacer(Modifier.height(Space.m))
            StackedBar(listOf(mo.fixed to c.accent, mo.flexible to c.accent.copy(alpha = 0.45f), (mo.income - mo.spending).coerceAtLeast(0) to c.positive.copy(alpha = 0.6f)))
            Spacer(Modifier.height(6.dp))
            Row {
                LegendDot(c.accent, "Fixed ${Money.compact(mo.fixed)}"); Spacer(Modifier.width(Space.m))
                LegendDot(c.accent.copy(alpha = 0.45f), "Flexible ${Money.compact(mo.flexible)}"); Spacer(Modifier.width(Space.m))
                if (mo.net > 0) LegendDot(c.positive.copy(alpha = 0.6f), "Kept ${(mo.net * 100 / mo.income)}%")
            }
        }
        if (mo.isPartial) {
            Spacer(Modifier.height(6.dp))
            Text("Spending compared with the same ${mo.throughDay} days of last month.", style = LedgerTheme.type.caption, color = c.textFaint)
        }
    }
}

@Composable
private fun FlowMetric(label: String, value: Long, previous: Long?, color: Color, modifier: Modifier, higherIsGood: Boolean) {
    val c = LedgerTheme.colors
    Column(modifier) {
        Text(label, style = LedgerTheme.type.caption, color = c.textMuted)
        Amount(value, style = LedgerTheme.type.headline, color = color)
        if (previous != null && previous > 0) {
            val pct = ((value - previous) * 100.0 / previous).roundToInt()
            val good = if (higherIsGood) pct >= 0 else pct <= 0
            Text("${if (pct >= 0) "▲" else "▼"} ${abs(pct)}%", style = LedgerTheme.type.caption, color = if (abs(pct) < 3) c.textMuted else if (good) c.positive else c.caution)
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = LedgerTheme.type.caption, color = LedgerTheme.colors.textMuted)
    }
}

/** Answers "am I spending faster than usual?" with three lines and one sentence. */
@Composable
private fun PaceCard(m: InsightsModel, isCurrent: Boolean) {
    val c = LedgerTheme.colors
    val mo = m.month
    var sel by remember(mo.month) { mutableStateOf<Int?>(null) }
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        val day = sel ?: (mo.throughDay - 1).coerceAtLeast(0)
        val cur = mo.cumulative.getOrElse(day) { 0 }
        val avg = mo.avgCumulative.getOrElse(day) { 0 }
        val prev = mo.prevCumulative.getOrElse(minOf(day, mo.prevCumulative.size - 1)) { 0 }
        AnimatedContent(sel, transitionSpec = { fadeIn(tween(Motion.FAST)) togetherWith fadeOut(tween(Motion.FAST)) }, label = "pace") { s ->
            Column {
                Text(if (s == null) "Spending pace" else "${Fmt.monthDay(mo.month.atDay(s + 1))}", style = LedgerTheme.type.label, color = c.textMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Amount(if (s != null && s >= mo.throughDay) 0 else cur, style = LedgerTheme.type.display)
                    if (avg > 0 && (s == null || s < mo.throughDay)) {
                        val pct = ((cur - avg) * 100.0 / avg).roundToInt()
                        Spacer(Modifier.width(Space.s))
                        Tag(if (pct >= 0) "$pct% above usual" else "${-pct}% below usual", if (pct > 10) Tone.CAUTION else if (pct < -5) Tone.POSITIVE else Tone.NEUTRAL, Modifier.padding(bottom = 6.dp))
                    }
                }
                Text("Last month ${Money.compact(prev)} · 3-month avg ${Money.compact(avg)} by day ${day + 1}", style = LedgerTheme.type.caption, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(Space.m))
        CumulativeChart(mo.cumulative, mo.throughDay, mo.prevCumulative, mo.avgCumulative, sel, { sel = it }, revealKey = mo.month)
        Spacer(Modifier.height(Space.s))
        Row {
            LegendDot(c.accent, if (isCurrent) "This month" else Fmt.monthShort(mo.month)); Spacer(Modifier.width(Space.m))
            LegendDot(c.chartMuted, "Last month"); Spacer(Modifier.width(Space.m))
            LegendDot(c.chartMuted.copy(alpha = 0.6f), "3-mo avg (dashed)")
        }
    }
}

@Composable
private fun InsightList(list: List<InsightsCalc.Insight>, onCategory: (Long) -> Unit) {
    val c = LedgerTheme.colors
    Column(Modifier.padding(horizontal = Space.l)) {
        list.take(6).forEach { ins ->
            val (tone, dashed) = when (ins.kind) {
                InsightsCalc.Kind.FACT -> Tone.NEUTRAL to false
                InsightsCalc.Kind.ESTIMATE -> Tone.CAUTION to true
                InsightsCalc.Kind.FORECAST -> Tone.ACCENT to true
            }
            val accentBar = when (ins.tone) { InsightsCalc.Tone.WARN -> c.caution; InsightsCalc.Tone.GOOD -> c.positive; else -> c.hairline }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(Shapes.chip).background(c.surface)
                    .then(if (ins.categoryId != null) Modifier.clickable { onCategory(ins.categoryId) } else Modifier),
            ) {
                Box(Modifier.width(3.dp).heightIn(min = 72.dp).background(accentBar))
                Column(Modifier.padding(horizontal = Space.m, vertical = Space.m)) {
                    Tag(ins.kind.label, tone, dashed = dashed)
                    Spacer(Modifier.height(6.dp))
                    Text(ins.text, style = LedgerTheme.type.bodyStrong, color = c.text)
                    Text(ins.detail, style = LedgerTheme.type.caption, color = c.textMuted)
                }
            }
        }
    }
}

/** Ranked bars (not a pie): exact values, share of total, and a tick at the 3-month average for the same days. */
@Composable
private fun CategoryBreakdown(m: Analytics.Month, onCategory: (Long?) -> Unit) {
    val c = LedgerTheme.colors
    val cats = m.categories.filter { it.amount > 0 }
    val max = (cats.maxOfOrNull { maxOf(it.amount, it.baseline) } ?: 1).coerceAtLeast(1)
    val total = m.spending.coerceAtLeast(1)
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), padding = PaddingValues(vertical = Space.s)) {
        cats.take(10).forEach { ct ->
            val cat = ct.category
            Row(
                Modifier.fillMaxWidth().clickable { onCategory(cat?.id) }.padding(horizontal = Space.l, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconWell(LedgerIcons.of(cat?.icon), hue(cat?.colorIndex ?: 0), size = 36.dp, iconSize = 20.dp)
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat?.name ?: "Uncategorised", style = LedgerTheme.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        ct.deltaPct?.let { dp ->
                            if (abs(dp) >= 0.1f && ct.baseline > 50_000) Text("${if (dp > 0) "▲" else "▼"}${(abs(dp) * 100).roundToInt()}%  ", style = LedgerTheme.type.caption,
                                color = if (dp > 0) c.caution else c.positive)
                        }
                        Amount(ct.amount, style = LedgerTheme.type.bodyStrong)
                    }
                    Spacer(Modifier.height(6.dp))
                    Bar(ct.amount.toFloat() / max, color = hue(cat?.colorIndex ?: 0), height = 6.dp, marker = if (ct.baseline > 0) ct.baseline.toFloat() / max else null)
                    Spacer(Modifier.height(3.dp))
                    Text("${ct.amount * 100 / total}% · ${ct.count} transactions${if (ct.baseline > 0) " · avg ${Money.compact(ct.baseline)}" else ""}", style = LedgerTheme.type.caption, color = c.textMuted)
                }
            }
        }
        if (cats.size > 10) Text("+ ${cats.size - 10} smaller categories", style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s))
    }
}

@Composable
private fun FixedFlexible(m: Analytics.Month) {
    val c = LedgerTheme.colors
    if (m.spending <= 0) return
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        Text("Fixed vs flexible", style = LedgerTheme.type.label, color = c.textMuted)
        Spacer(Modifier.height(Space.s))
        Row {
            Metric("Fixed · ${m.fixed * 100 / m.spending}%", m.fixed, Modifier.weight(1f))
            Metric("Flexible · ${m.flexible * 100 / m.spending}%", m.flexible, Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.s))
        StackedBar(listOf(m.fixed to c.accent, m.flexible to c.accent.copy(alpha = 0.4f)), height = 10.dp)
        Spacer(Modifier.height(Space.s))
        Row {
            Text(m.fixedCategories.take(4).joinToString(" · ") { "${it.category?.name} ${Money.compact(it.amount)}" }, style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.m))
            Text(m.flexibleCategories.take(4).joinToString(" · ") { "${it.category?.name} ${Money.compact(it.amount)}" }, style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Heatmap(m: Analytics.Month, today: LocalDate, nav: Nav) {
    val c = LedgerTheme.colors
    var sel by remember(m.month) { mutableStateOf<Int?>(null) }
    val isCurrent = YearMonth.from(today) == m.month
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        val peak = m.daily.withIndex().maxByOrNull { it.value }
        Text(
            peak?.let { if (it.value > 0) "Heaviest day: ${Fmt.monthDay(m.month.atDay(it.index + 1))} · ${Money.compact(it.value)}" else null } ?: "No spending yet",
            style = LedgerTheme.type.label, color = c.textMuted,
        )
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, s ->
                Text(s, style = LedgerTheme.type.caption, color = if (i >= 5) c.text else c.textFaint, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        CalendarHeatmap(
            m.daily, m.month.atDay(1).dayOfWeek.value - 1, if (isCurrent) today.dayOfMonth - 1 else null, sel,
            onDay = { i -> sel = i; nav.open(SheetRequest.Day(m.month.atDay(i + 1))) },
        )
        Spacer(Modifier.height(Space.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weekend ${Money.compact(m.weekendPerDay)}/day · weekday ${Money.compact(m.weekdayPerDay)}/day", style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.weight(1f))
            Text("Less ", style = LedgerTheme.type.caption, color = c.textFaint)
            listOf(0.18f, 0.36f, 0.62f, 0.92f).forEach { a -> Box(Modifier.padding(horizontal = 1.dp).size(10.dp).clip(Shapes.pill).background(c.accent.copy(alpha = a))) }
            Text(" More", style = LedgerTheme.type.caption, color = c.textFaint)
        }
    }
}

@Composable
private fun FlowCard(m: Analytics.Month) {
    val c = LedgerTheme.colors
    // One income node keeps ribbons from crossing; the sources are listed underneath.
    val sources = mutableListOf(FlowNode("Income", m.income, c.positive))
    val top = m.categories.filter { it.amount > 0 }.take(5)
    val rest = m.spending - top.sumOf { it.amount }
    val targets = top.map { FlowNode(it.category?.name ?: "Other", it.amount, hue(it.category?.colorIndex ?: 0)) }.toMutableList()
    if (rest > 0) targets += FlowNode("Other", rest, c.chartMuted)
    val kept = m.income - m.spending
    if (kept > 0) targets += FlowNode("Kept", kept, c.positive) else if (kept < 0) sources += FlowNode("From balance", -kept, c.caution)
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        Text("Income ${Money.compact(m.income)} → where it went", style = LedgerTheme.type.label, color = c.textMuted)
        Spacer(Modifier.height(Space.m))
        MoneyFlowChart(sources, targets)
        Spacer(Modifier.height(Space.s))
        Text("Income: " + m.incomeSources.joinToString(" · ") { "${it.first} ${Money.compact(it.second)}" } + if (kept < 0) " · ${Money.compact(-kept)} from existing balance" else "", style = LedgerTheme.type.caption, color = c.textMuted)
    }
}

@Composable
private fun SixMonths(m: InsightsModel, selected: YearMonth, onSelect: (YearMonth) -> Unit) {
    val c = LedgerTheme.colors
    val bars = m.bars
    val idx = bars.indexOfFirst { it.month == selected }.coerceAtLeast(0)
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        val avg = bars.filter { it.month != YearMonth.now() && it.spending > 0 }.map { it.spending }.let { if (it.isEmpty()) 0 else it.sum() / it.size }
        Text("Spending by month · average ${Money.compact(avg)}", style = LedgerTheme.type.label, color = c.textMuted)
        Spacer(Modifier.height(Space.m))
        MonthBarsChart(bars.map { MonthBarUi(Fmt.monthShort(it.month), it.spending, it.fixed, it.income) }, idx, { onSelect(bars[it].month) })
        Spacer(Modifier.height(Space.s))
        Row {
            LegendDot(c.accent, "Fixed"); Spacer(Modifier.width(Space.m))
            LegendDot(c.accent.copy(alpha = 0.45f), "Flexible"); Spacer(Modifier.width(Space.m))
            LegendDot(c.positive, "Income")
        }
    }
}

// ---------- drill-down ----------

@Composable
fun CategoryDetailScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav, categoryId: Long?, month: YearMonth) {
    val c = LedgerTheme.colors
    val cat = categoryId?.let { d.data.categoryById[it] }
    val ids = remember(categoryId) { setOfNotNull(categoryId) + d.data.categories.filter { it.parentId == categoryId && categoryId != null }.map { it.id } }
    val lines = remember(d.data, month, categoryId) {
        dev.personal.ledger.domain.Ledger.expenseLines(d.data, month.atDay(1).startMillis(), month.atEndOfMonth().endMillis())
            .filter { if (categoryId == null) d.data.rootOf(it.categoryId) == null else it.categoryId in ids }
    }
    val total = lines.sumOf { it.amount }
    val bySub = lines.groupBy { it.categoryId }.map { (k, v) -> d.data.categoryById[k] to v.sumOf { it.amount } }.sortedByDescending { it.second }
    val trend = remember(d.data, categoryId) {
        (5 downTo 0).map { k ->
            val ym = YearMonth.from(d.today).minusMonths(k.toLong())
            val v = dev.personal.ledger.domain.Ledger.expenseLines(d.data, ym.atDay(1).startMillis(), ym.atEndOfMonth().endMillis())
                .filter { if (categoryId == null) d.data.rootOf(it.categoryId) == null else it.categoryId in ids }.sumOf { it.amount }
            MonthBarUi(Fmt.monthShort(ym), v, 0, 0)
        }
    }
    val txs = lines.map { it.tx }.distinct().sortedByDescending { it.date }
    dev.personal.ledger.ui.common.Screen(cat?.name ?: "Uncategorised", onBack = { nav.pop() }, subtitle = Fmt.monthYear(month)) {
        item {
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                Amount(total, style = LedgerTheme.type.hero, format = AmountFormat.FULL)
                Text("${txs.size} transactions · ${Money.compact(if (month == YearMonth.from(d.today)) total / d.today.dayOfMonth else total / month.lengthOfMonth())}/day",
                    style = LedgerTheme.type.label, color = c.textMuted)
            }
        }
        if (bySub.size > 1) item {
            LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
                Text("Breakdown", style = LedgerTheme.type.label, color = c.textMuted)
                val max = bySub.maxOf { it.second }.coerceAtLeast(1)
                bySub.forEach { (sc, v) ->
                    Spacer(Modifier.height(Space.s))
                    Row { Text(if (sc?.id == categoryId) "${sc?.name ?: "Other"} (general)" else sc?.name ?: "Other", style = LedgerTheme.type.label, color = c.text, modifier = Modifier.weight(1f)); Amount(v, style = LedgerTheme.type.label) }
                    Spacer(Modifier.height(4.dp))
                    Bar(v.toFloat() / max, color = hue(sc?.colorIndex ?: cat?.colorIndex ?: 0))
                }
            }
        }
        item {
            LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
                Text("6-month trend", style = LedgerTheme.type.label, color = c.textMuted)
                Spacer(Modifier.height(Space.m))
                MonthBarsChart(trend, trend.indexOfFirst { it.label == Fmt.monthShort(month) }.coerceAtLeast(5), {}, height = 130.dp)
            }
        }
        item { SectionHeader("Transactions", Modifier.padding(top = Space.m)) }
        txs.forEach { t -> item(key = t.id) { TxRow(t, d.data, showDate = true) { nav.open(SheetRequest.TxDetail(t.id)) } } }
    }
}

@Composable
fun ColumnScope.DaySheet(d: Dashboard, nav: Nav, date: LocalDate) {
    val c = LedgerTheme.colors
    val txs = d.data.transactions.filter { it.date.toLocalDate() == date }
    val spent = dev.personal.ledger.domain.Ledger.expenseLines(d.data, date.startMillis(), date.endMillis()).sumOf { it.amount }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Space.l)) {
        Column(Modifier.padding(horizontal = Space.gutter)) {
            Text(Fmt.full(date), style = LedgerTheme.type.headline, color = c.text)
            Row(verticalAlignment = Alignment.Bottom) {
                Amount(spent, style = LedgerTheme.type.display)
                Text("  spent · ${txs.size} transactions", style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.padding(bottom = 5.dp))
            }
        }
        Spacer(Modifier.height(Space.s))
        if (txs.isEmpty()) Text("Nothing recorded this day.", style = LedgerTheme.type.body, color = c.textMuted, modifier = Modifier.padding(Space.gutter))
        txs.forEach { t -> TxRow(t, d.data) { nav.open(SheetRequest.TxDetail(t.id)) } }
    }
}

