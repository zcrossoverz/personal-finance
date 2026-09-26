package dev.personal.ledger.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personal.ledger.data.Preset
import dev.personal.ledger.data.TxType
import dev.personal.ledger.data.Txn
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.PresetEngine
import dev.personal.ledger.domain.SafeToSpendCalc
import dev.personal.ledger.i18n.I18n
import dev.personal.ledger.i18n.tr
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.EntryRequest
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.Route
import dev.personal.ledger.ui.SheetRequest
import dev.personal.ledger.ui.Tab
import dev.personal.ledger.ui.common.DayHeader
import dev.personal.ledger.ui.common.IconAction
import dev.personal.ledger.ui.common.ObligationRow
import dev.personal.ledger.ui.common.SwipeRow
import dev.personal.ledger.ui.common.TxRow
import dev.personal.ledger.ui.common.groupByDay
import dev.personal.ledger.ui.common.obligationEntry
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.AnimatedAmount
import dev.personal.ledger.ui.components.Bar
import dev.personal.ledger.ui.components.EmptyState
import dev.personal.ledger.ui.components.Haptics
import dev.personal.ledger.ui.components.LedgerCard
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.SectionHeader
import dev.personal.ledger.ui.components.Tag
import dev.personal.ledger.ui.components.Tone
import dev.personal.ledger.ui.components.formatAmount
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.LocalHideAmounts
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Home is operational: state → capture → what's due → what just happened. No decorative charts.
 * Hierarchy (docs/PRODUCT.md §6): cash on hand, safe to spend, presets, alerts, next 7 days, recent.
 */
@Composable
fun HomeScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav) {
    val c = LedgerTheme.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val recent = remember(d.data.transactions) { d.data.transactions.take(8) }
    val nextWeek = remember(d.upcoming, d.today) { d.upcoming.filter { !it.date.isAfter(d.today.plusDays(7)) } }
    val alerts = remember(d) { alertsFor(d) }

    LazyColumn(Modifier.fillMaxSize().background(c.bg), state = list, contentPadding = PaddingValues(bottom = 140.dp)) {
        item(key = "top") {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.gutter, end = 6.dp, top = 6.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.full(d.today), style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.weight(1f))
                IconAction(Icons.Rounded.Search, tr("Search"), c.textMuted) { nav.push(Route.Activity()) }
                IconAction(if (settings.hideAmounts) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (settings.hideAmounts) tr("Show amounts") else tr("Hide amounts"), c.textMuted) {
                    vm.updateSettings { it.copy(hideAmounts = !it.hideAmounts) }
                }
                IconAction(Icons.Rounded.Settings, tr("Settings"), c.textMuted) { nav.push(Route.Settings) }
            }
        }
        item(key = "hero") { Hero(d, nav) }
        item(key = "safe") { SafeToSpendCard(d.safe, d) { nav.open(SheetRequest.SafeToSpend) } }
        item(key = "presets") { PresetGrid(d, vm, nav) }
        if (alerts.isNotEmpty()) item(key = "alerts") {
            Column(Modifier.padding(horizontal = Space.l).padding(top = Space.s)) {
                alerts.forEach { a -> AlertRow(a) { a.entry?.let { nav.entry(it) } ?: nav.select(Tab.PLAN) }; Spacer(Modifier.height(Space.s)) }
            }
        }
        item(key = "upcoming-h") { SectionHeader(tr("Next 7 days"), Modifier.padding(top = Space.m), trailing = tr("Timeline")) { nav.select(Tab.PLAN) } }
        if (nextWeek.isEmpty()) item(key = "upcoming-empty") {
            Text(tr("Nothing scheduled this week."), style = LedgerTheme.type.body, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s))
        }
        items(nextWeek.take(4), key = { "o-" + it.key }) { o ->
            ObligationRow(o, d.today, onClick = { obligationEntry(o, d, settings.defaultAccountId)?.let(nav::entry) }, modifier = Modifier.animateItem())
        }
        if (nextWeek.size > 4) item(key = "upcoming-more") {
            Text(tr("+%d more · %s", nextWeek.size - 4, Money.compact(nextWeek.drop(4).filter { !it.isInflow }.sumOf { it.amount })), style = LedgerTheme.type.label, color = c.textMuted,
                modifier = Modifier.fillMaxWidth().clickable { nav.select(Tab.PLAN) }.padding(horizontal = Space.gutter, vertical = 12.dp))
        }
        item(key = "recent-h") { SectionHeader(tr("Recent"), Modifier.padding(top = Space.m), trailing = tr("All activity")) { nav.push(Route.Activity()) } }
        if (recent.isEmpty()) item(key = "recent-empty") {
            EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, tr("No transactions yet"), tr("Tap a preset above — it takes two taps and an amount."))
        }
        groupByDay(recent).forEach { (day, txs) ->
            item(key = "day-$day") {
                DayHeader(day, d.today, txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }, Modifier.animateItem())
            }
            items(txs, key = { "t-${it.id}" }) { t ->
                Box(Modifier.animateItem(fadeInSpec = tween(Motion.STANDARD), placementSpec = Motion.gentle())) {
                    SwipeRow(onDelete = { vm.deleteTransaction(t) }, onDuplicate = { vm.duplicate(t) }) {
                        TxRow(t, d.data) { nav.open(SheetRequest.TxDetail(t.id)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(d: Dashboard, nav: Nav) {
    val c = LedgerTheme.colors
    val month = Fmt.month(YearMonth.from(d.today)).let { if (I18n.vi) it.lowercase() else it }
    Column(Modifier.fillMaxWidth().clickable { nav.select(Tab.MONEY) }.padding(horizontal = Space.gutter, vertical = Space.s)) {
        Text(tr("Cash on hand"), style = LedgerTheme.type.label, color = c.textMuted)
        Spacer(Modifier.height(2.dp))
        AnimatedAmount(d.position.cashOnHand, style = LedgerTheme.type.hero)
        Spacer(Modifier.height(Space.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroStat(tr("Spent in %s", month), d.monthSpent, c.text)
            HeroDivider()
            HeroStat(tr("Today"), d.todaySpent, c.text)
            if (d.position.cardDebt > 0) { HeroDivider(); HeroStat(tr("Card debt"), -d.position.cardDebt, c.negative) }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: Long, color: Color) {
    Column {
        Text(label, style = LedgerTheme.type.caption, color = LedgerTheme.colors.textMuted, maxLines = 1)
        AnimatedAmount(value, style = LedgerTheme.type.bodyStrong, color = color, format = AmountFormat.COMPACT)
    }
}

@Composable
private fun HeroDivider() {
    Box(Modifier.padding(horizontal = Space.l).size(1.dp, 28.dp).background(LedgerTheme.colors.hairline))
}

@Composable
private fun SafeToSpendCard(s: SafeToSpendCalc.Result, d: Dashboard, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val negative = s.safe < 0
    LedgerCard(Modifier.padding(horizontal = Space.l, vertical = Space.s).fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Safe to spend"), style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.weight(1f))
            when (s.confidence) {
                SafeToSpendCalc.Confidence.HIGH -> {}
                SafeToSpendCalc.Confidence.MEDIUM -> Tag(tr("Includes estimates"), Tone.CAUTION, dashed = true)
                SafeToSpendCalc.Confidence.LOW -> Tag(tr("Incomplete data"), Tone.NEGATIVE)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textFaint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        AnimatedAmount(s.safe, style = LedgerTheme.type.display, color = if (negative) c.negative else c.text, format = AmountFormat.FULL)
        Spacer(Modifier.height(2.dp))
        val days = if (s.salary != null) tr("%d days to payday", s.daysLeft) else tr("%d days to month end", s.daysLeft)
        Text(
            if (negative) tr("Over by %s · %s", Money.compact(-s.safe), days) else tr("%s a day · %s", Money.compact(s.perDay), days),
            style = LedgerTheme.type.label, color = if (negative) c.negative else c.textMuted,
        )
        d.pace?.let { p ->
            Spacer(Modifier.height(Space.m))
            val ahead = p.aheadPct
            Bar(p.usedPct, color = if (ahead > 0.1f) c.caution else c.accent, marker = p.expectedPct, height = 4.dp)
            Spacer(Modifier.height(6.dp))
            val pct = (p.usedPct * 100).toInt()
            val pace = when {
                ahead > 0.05f -> tr("%d%% ahead of pace", (ahead * 100).toInt())
                ahead < -0.05f -> tr("%d%% under pace", (-ahead * 100).toInt())
                else -> tr("on pace")
            }
            Text(
                if (p.referenceIsBudget) tr("Flexible spending at %d%% of budget · %s", pct, pace)
                else tr("Flexible spending at %d%% of usual · %s", pct, pace),
                style = LedgerTheme.type.caption, color = if (ahead > 0.1f) c.caution else c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------- quick presets ----------

@Composable
private fun PresetGrid(d: Dashboard, vm: LedgerViewModel, nav: Nav) {
    val c = LedgerTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val presets = d.presets
    val visible = if (expanded) presets else presets.take(8)
    Column(Modifier.padding(top = Space.m)) {
        SectionHeader(tr("Quick add"), trailing = tr("Edit")) { nav.push(Route.Presets) }
        Column(Modifier.padding(horizontal = Space.m).animateContentSize(Motion.gentle())) {
            visible.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { p -> PresetTile(p, d, vm, nav, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (presets.size > 8) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) tr("Show less") else tr("%d more", presets.size - 8), style = LedgerTheme.type.label, color = c.textMuted)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PresetTile(p: Preset, d: Dashboard, vm: LedgerViewModel, nav: Nav, modifier: Modifier) {
    val c = LedgerTheme.colors
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, Motion.snappy(), label = "tile")
    var quick by remember { mutableStateOf(false) }
    val label = d.presetLabels[p.id] ?: p.label
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().scale(scale).clip(Shapes.tile)
                .combinedClickable(
                    interactionSource = interaction, indication = null, role = Role.Button,
                    onLongClick = { Haptics.long(view); quick = true },
                    onClick = { Haptics.tick(view); nav.entry(EntryRequest(presetId = p.id)) },
                )
                .semantics { contentDescription = tr("%s. Long press for quick amounts.", label) }
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(Shapes.tile).background(c.surface).border(1.dp, c.hairline, Shapes.tile),
                contentAlignment = Alignment.Center,
            ) { Icon(LedgerIcons.of(p.icon), null, Modifier.size(24.dp), tint = hue(p.colorIndex)) }
            Spacer(Modifier.height(7.dp))
            Text(label, style = LedgerTheme.type.caption, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 2.dp))
        }
        if (quick) QuickAmountsPopup(p, label, d, vm, onDismiss = { quick = false })
    }
}

/** Long-press on a preset: learned amounts. Tapping one saves immediately (Undo in the snackbar). */
@Composable
private fun QuickAmountsPopup(p: Preset, label: String, d: Dashboard, vm: LedgerViewModel, onDismiss: () -> Unit) {
    val c = LedgerTheme.colors
    val view = LocalView.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val amounts = remember(p.id, d.data) { PresetEngine.quickAmounts(d.data, p, d.today) }
    val account = remember(p.id, d.data) { PresetEngine.accountFor(d.data, p, settings.defaultAccountId) }
    val hidden = LocalHideAmounts.current
    Popup(popupPositionProvider = AboveAnchor, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        val appear = remember { Animatable(0.9f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, Motion.snappy()) }
        Box(Modifier.padding(16.dp).graphicsLayer { scaleX = appear.value; scaleY = appear.value; alpha = (appear.value - 0.9f) * 10f; transformOrigin = TransformOrigin(0.5f, 1f) }) {
            Column(
                Modifier.width(284.dp).shadow(8.dp, Shapes.card, ambientColor = Color.Black.copy(alpha = 0.2f)).clip(Shapes.card)
                    .background(if (c.isDark) c.surfaceHigh else c.surface).border(1.dp, c.hairline, Shapes.card).padding(Space.m),
            ) {
                Text(tr("%s · %s · tap to save", label, account?.name ?: ""), style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.s))
                if (amounts.isEmpty()) Text(tr("No history yet — tap the preset to enter an amount."), style = LedgerTheme.type.label, color = c.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    amounts.forEach { a ->
                        Box(
                            Modifier.weight(1f).heightIn(min = 48.dp).clip(Shapes.chip).background(c.surfaceAlt)
                                .clickable(role = Role.Button) {
                                    Haptics.confirm(view)
                                    if (account != null) vm.addTransaction(
                                        Txn(type = p.type, amount = a, accountId = account.id, toAccountId = p.toAccountId,
                                            categoryId = PresetEngine.resolveCategory(d.data, p, LocalDateTime.now()), date = System.currentTimeMillis(), presetId = p.id),
                                        message = tr("%s · %s saved", label, Money.compact(a)),
                                    )
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(formatAmount(a, AmountFormat.COMPACT, hidden = hidden), style = LedgerTheme.type.bodyStrong, color = c.text) }
                    }
                }
            }
        }
    }
}

/** Places a popup just above its anchor, centred and clamped to the window; flips below when there is no room. */
private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val margin = 24
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
        val above = anchorBounds.top - popupContentSize.height - 12
        return IntOffset(x, if (above >= margin) above else anchorBounds.bottom + 12)
    }
}

// ---------- alerts ----------

private data class Alert(val title: String, val detail: String, val entry: EntryRequest?)

private fun alertsFor(d: Dashboard): List<Alert> {
    val out = ArrayList<Alert>()
    // Overdue items already lead the "Next 7 days" list in red; alerts only surface what the list can't show.
    d.cards.filter { it.statementBalance > 0 && it.dueDate != null && !it.overdue && ChronoUnit.DAYS.between(d.today, it.dueDate) in 0..5 }.forEach { s ->
        out += Alert(tr("%s · payment %s", s.account.name, Fmt.dueIn(s.dueDate!!, d.today)), tr("Statement %s · tap to pay", Money.compact(s.statementBalance)),
            EntryRequest(type = TxType.TRANSFER, toAccountId = s.account.id, amount = s.statementBalance, cardId = s.account.id))
    }
    return out
}

@Composable
private fun AlertRow(a: Alert, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(Shapes.chip).background(c.surface).border(1.dp, c.hairline, Shapes.chip)
            .clickable(onClick = onClick).padding(horizontal = Space.m, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(Shapes.well).background(c.cautionSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CreditCard, null, tint = c.caution, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = LedgerTheme.type.bodyStrong, color = c.text)
            Text(a.detail, style = LedgerTheme.type.caption, color = c.textMuted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textFaint, modifier = Modifier.size(20.dp))
    }
}
