package dev.personal.ledger.ui.money

import dev.personal.ledger.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SearchOff
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.TxType
import dev.personal.ledger.data.Txn
import dev.personal.ledger.domain.Cards
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Ledger
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.Search
import dev.personal.ledger.domain.endMillis
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.EntryRequest
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.Route
import dev.personal.ledger.ui.SheetRequest
import dev.personal.ledger.ui.charts.LineChart
import dev.personal.ledger.ui.common.DayHeader
import dev.personal.ledger.ui.common.IconAction
import dev.personal.ledger.ui.common.Screen
import dev.personal.ledger.ui.common.SwipeRow
import dev.personal.ledger.ui.common.TxRow
import dev.personal.ledger.ui.common.groupByDay
import dev.personal.ledger.ui.components.Amount
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.AmountInput
import dev.personal.ledger.ui.components.Chip
import dev.personal.ledger.ui.components.ChoiceRow
import dev.personal.ledger.ui.components.EmptyState
import dev.personal.ledger.ui.components.FieldLabel
import dev.personal.ledger.ui.components.FormFooter
import dev.personal.ledger.ui.components.HueChoice
import dev.personal.ledger.ui.components.LedgerCard
import dev.personal.ledger.ui.components.TextInput
import dev.personal.ledger.ui.components.ToggleRow
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import java.time.LocalDate

@Composable
fun AccountDetailScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav, id: Long) {
    val c = LedgerTheme.colors
    val a = d.data.accountById[id] ?: run { nav.pop(); return }
    val balance = d.balances[id] ?: 0
    val txs = remember(d.data, id) { d.data.transactions.filter { it.accountId == id || it.toAccountId == id } }
    val history = remember(d.data, id) {
        // Daily end-of-day balance over the last 90 days, computed backwards from today's balance.
        val days = 90
        val out = LongArray(days)
        var b = balance
        val byDay = txs.groupBy { dev.personal.ledger.domain.daysBetween(it.date.let { m -> java.time.Instant.ofEpochMilli(m).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }, d.today).toInt() }
        for (i in 0 until days) {
            out[days - 1 - i] = b
            byDay[i]?.forEach { t -> b -= Ledger.delta(t, id) }
        }
        out.toList()
    }
    var sel by remember { mutableStateOf<Int?>(null) }
    Screen(a.name, onBack = { nav.pop() }, actions = { IconAction(Icons.Rounded.Edit, tr("Edit account")) { nav.push(Route.EditAccount(id)) } }) {
        if (a.type == AccountType.CREDIT_CARD) {
            item { CreditCard(Cards.status(d.data, a, d.today, d.balances), d, onPay = {
                val s = Cards.status(d.data, a, d.today, d.balances)
                nav.entry(EntryRequest(type = TxType.TRANSFER, toAccountId = a.id, amount = if (s.statementBalance > 0) s.statementBalance else s.used, cardId = a.id))
            }) {} }
        } else item {
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                val shown = sel?.let { history[it] } ?: balance
                Text(sel?.let { Fmt.full(d.today.minusDays((history.size - 1 - it).toLong())) } ?: tr("Balance"), style = LedgerTheme.type.label, color = c.textMuted)
                Amount(shown, style = LedgerTheme.type.hero, format = AmountFormat.FULL, color = if (shown < 0) c.negative else c.text)
            }
        }
        if (a.type != AccountType.CREDIT_CARD && txs.isNotEmpty()) item {
            Box(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                LineChart(history, (0 until history.size).map { Fmt.dayMonth(d.today.minusDays((history.size - 1 - it).toLong())) }, sel, { sel = it }, height = 130.dp)
            }
        }
        if (txs.isEmpty()) item { EmptyState(Icons.Rounded.SearchOff, tr("No transactions"), tr("Everything paid from or into %s will appear here.", a.name)) }
        groupByDay(txs.take(300)).forEach { (day, list) ->
            item(key = "d$day") { DayHeader(day, d.today, list.sumOf { -Ledger.delta(it, id) }) }
            items(list, key = { it.id }) { t -> TxRow(t, d.data) { nav.open(SheetRequest.TxDetail(t.id)) } }
        }
    }
}

/** All transactions with natural-language search ("food this week", "> 1m", "mb september"). */
@Composable
fun ActivityScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav, initial: String) {
    val c = LedgerTheme.colors
    var query by rememberSaveable { mutableStateOf(initial) }
    val parsed = remember(query, d.data) { Search.parse(query, d.data, d.today) }
    val results = remember(parsed, d.data) { Search.run(parsed, d.data) }
    val spent = remember(results) { results.filter { it.type == TxType.EXPENSE }.sumOf { it.amount } - results.filter { it.type.offsetsExpense }.sumOf { it.amount } }
    val income = remember(results) { results.filter { it.type == TxType.INCOME }.sumOf { it.amount } }
    val fr = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { if (initial.isEmpty() && nav.top is Route.Activity) fr.requestFocus() }
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) focus.clearFocus() }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = Space.l, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Icons.AutoMirrored.Rounded.ArrowBack, tr("Back")) { nav.pop() }
            BasicTextField(
                query, { query = it },
                Modifier.weight(1f).heightIn(min = 48.dp).clip(Shapes.pill).background(c.surfaceAlt).padding(horizontal = 18.dp, vertical = 14.dp).focusRequester(fr),
                textStyle = LedgerTheme.type.body.copy(color = c.text), singleLine = true, cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { if (query.isEmpty()) Text(tr("Search: shopee, > 1m, food this week"), style = LedgerTheme.type.body, color = c.textFaint); inner() }
                        if (query.isNotEmpty()) Icon(Icons.Rounded.Close, tr("Clear"), tint = c.textMuted, modifier = Modifier.size(20.dp).clickable { query = "" })
                    }
                },
            )
        }
        if (query.isEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = Space.l, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                items(if (dev.personal.ledger.i18n.I18n.vi) listOf("tháng này", "ăn uống tuần này", "shopee", "> 1tr", "đăng ký", "chuyển", "hoàn tiền", "tháng trước") else listOf("this month", "food this week", "shopee", "> 1m", "subscriptions", "transfer", "refund", "last month")) { s -> Chip(s) { query = s } }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(parsed.chips) { ch -> dev.personal.ledger.ui.components.Tag(ch, dev.personal.ledger.ui.components.Tone.ACCENT) }
                    if (parsed.text.isNotEmpty()) item { dev.personal.ledger.ui.components.Tag("“${parsed.text.joinToString(" ")}”") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter).padding(bottom = Space.s)) {
                Text(tr("%d transactions", results.size), style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.weight(1f))
                if (spent != 0L) { Text(tr("Spent") + " ", style = LedgerTheme.type.label, color = c.textMuted); Amount(spent, style = LedgerTheme.type.label) }
                if (income > 0) { Text("  " + tr("In") + " ", style = LedgerTheme.type.label, color = c.textMuted); Amount(income, style = LedgerTheme.type.label, color = c.positive) }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 140.dp)) {
            if (results.isEmpty()) item { EmptyState(Icons.Rounded.SearchOff, tr("No matches"), tr("Try a merchant, an amount like 500k, a range like > 1m, or a period like “last month”.")) }
            if (query.isBlank()) groupByDay(results.take(600)).forEach { (day, txs) ->
                item(key = "d$day") { DayHeader(day, d.today, txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }) }
                items(txs, key = { it.id }) { t ->
                    SwipeRow(onDelete = { vm.deleteTransaction(t) }, onDuplicate = { vm.duplicate(t) }) {
                        TxRow(t, d.data) { nav.open(SheetRequest.TxDetail(t.id)) }
                    }
                }
            } else items(results.take(600), key = { it.id }) { t ->
                // Search results are a compact flat list; the date moves into the row.
                SwipeRow(onDelete = { vm.deleteTransaction(t) }, onDuplicate = { vm.duplicate(t) }) {
                    TxRow(t, d.data, showDate = true) { nav.open(SheetRequest.TxDetail(t.id)) }
                }
            }
            if (results.size > 600) item { Text(tr("Showing the latest 600 — refine the search to see older ones."), style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(Space.gutter)) }
        }
    }
}

@Composable
fun EditAccountScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav, id: Long?) {
    val c = LedgerTheme.colors
    val existing = id?.let { d.data.accountById[it] }
    val settings = vm.settings.value
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.BANK) }
    var balance by remember { mutableStateOf(existing?.let { (d.balances[it.id] ?: 0).let { b -> if (it.type == AccountType.CREDIT_CARD) (-b).toString() else b.toString() } } ?: "") }
    var aliases by remember { mutableStateOf(existing?.aliases ?: "") }
    var color by remember { mutableStateOf(existing?.colorIndex ?: 2) }
    var limit by remember { mutableStateOf(existing?.creditLimit?.toString() ?: "") }
    var statementDay by remember { mutableStateOf(existing?.statementDay?.toString() ?: "") }
    var dueDay by remember { mutableStateOf(existing?.dueDay?.toString() ?: "") }
    var spendable by remember { mutableStateOf(existing?.spendable ?: true) }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    var isDefault by remember { mutableStateOf(settings.defaultAccountId == id && id != null) }
    val bal = if (balance.isBlank()) 0L else Money.parse(balance)
    val card = type == AccountType.CREDIT_CARD
    val sDay = statementDay.toIntOrNull(); val dDay = dueDay.toIntOrNull()
    val valid = name.isNotBlank() && bal != null && (!card || ((sDay == null || sDay in 1..31) && (dDay == null || dDay in 1..31)))
    val names = mapOf(AccountType.CASH to tr("Cash"), AccountType.BANK to tr("Bank"), AccountType.EWALLET to tr("E-wallet"), AccountType.SAVINGS to tr("Savings"), AccountType.CREDIT_CARD to tr("Credit card"), AccountType.OTHER to tr("Other"))

    Screen(if (existing == null) tr("New account") else tr("Edit %s", existing.name), onBack = { nav.pop() }) {
        item {
            FieldLabel(tr("Type")); ChoiceRow(AccountType.entries, type, { names[it]!! }) { type = it; spendable = it != AccountType.SAVINGS && it != AccountType.CREDIT_CARD }
            FieldLabel(tr("Name")); TextInput(name, { name = it }, "MB Bank, Tiền mặt, MoMo…")
            FieldLabel(if (card) tr("Current debt on the card") else tr("Current balance"))
            AmountInput(balance, { balance = it }, helper = if (existing != null) tr("Changing this records a balance adjustment") else null)
            FieldLabel(tr("Short names for quick entry")); TextInput(aliases, { aliases = it }, "mb, mbbank", helper = tr("Used in commands like “350k xăng mb” and in search"))
            if (card) {
                FieldLabel(tr("Credit limit")); AmountInput(limit, { limit = it })
                Row {
                    Column(Modifier.weight(1f)) { FieldLabel(tr("Statement day")); TextInput(statementDay, { statementDay = it.filter(Char::isDigit).take(2) }, "20", keyboard = KeyboardType.Number) }
                    Column(Modifier.weight(1f)) { FieldLabel(tr("Payment due day")); TextInput(dueDay, { dueDay = it.filter(Char::isDigit).take(2) }, "5", keyboard = KeyboardType.Number) }
                }
                Text(tr("Card purchases count as spending when made. Paying the card is a transfer, not another expense."), style = LedgerTheme.type.caption, color = c.textMuted,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s))
            }
            FieldLabel(tr("Colour")); HueChoice(color) { color = it }
            Spacer(Modifier.height(Space.s))
            if (!card && type != AccountType.SAVINGS) ToggleRow(tr("Counts toward safe to spend"), spendable) { spendable = it }
            if (!card) ToggleRow(tr("Default account"), isDefault, tr("Used when a preset has no account of its own")) { isDefault = it }
            if (existing != null) ToggleRow(tr("Archived"), archived, tr("Hidden from pickers; history is kept")) { archived = it }
            FormFooter(tr("Save"), valid, onSave = {
                val target = if (card) -(bal ?: 0) else (bal ?: 0)
                val base = Account(
                    id = existing?.id ?: 0, name = name.trim(), type = type, aliases = aliases.trim(),
                    openingBalance = existing?.openingBalance ?: target, colorIndex = color,
                    creditLimit = if (card) Money.parse(limit) else null, statementDay = if (card) sDay else null, dueDay = if (card) dDay else null,
                    spendable = spendable && !card && type != AccountType.SAVINGS, archived = archived, sortOrder = existing?.sortOrder ?: d.data.accounts.size,
                )
                vm.run {
                    val newId = vm.repo.saveAccount(base)
                    if (existing != null) {
                        val current = d.balances[existing.id] ?: 0
                        if (target != current) vm.repo.add(Txn(type = TxType.ADJUSTMENT, amount = target - current, accountId = existing.id, date = System.currentTimeMillis(), note = tr("Balance adjustment")))
                    }
                    if (isDefault) vm.updateSettings { it.copy(defaultAccountId = newId) }
                    else if (settings.defaultAccountId == newId) vm.updateSettings { it.copy(defaultAccountId = null) }
                }
                nav.pop()
            })
        }
    }
}
