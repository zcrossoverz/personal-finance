package dev.personal.ledger.ui.entry

import dev.personal.ledger.i18n.tr
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.Category
import dev.personal.ledger.data.TxType
import dev.personal.ledger.data.Txn
import dev.personal.ledger.domain.CommandParser
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.PresetEngine
import dev.personal.ledger.domain.atMillis
import dev.personal.ledger.domain.toLocalDate
import dev.personal.ledger.domain.toLocalDateTime
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.EntryRequest
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.Chip
import dev.personal.ledger.ui.components.Haptics
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.Numpad
import dev.personal.ledger.ui.components.PrimaryButton
import dev.personal.ledger.ui.components.Tag
import dev.personal.ledger.ui.components.Tone
import dev.personal.ledger.ui.components.formatAmount
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.LocalHideAmounts
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The single capture surface. Opened from a preset it is: amount → Save (everything else is preselected).
 * From the capture button it becomes a composer (type, category, command line). From a schedule it is
 * prefilled so a fixed amount is one tap to confirm.
 */
@Composable
fun ColumnScope.EntrySheet(d: Dashboard, vm: LedgerViewModel, nav: Nav, req: EntryRequest) {
    val c = LedgerTheme.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val data = d.data
    val now = remember { LocalDateTime.now() }
    val editing = req.editTxId?.let { data.txById[it] }
    val preset = req.presetId?.let { data.presetById[it] }
    val recurring = req.recurringId?.let { id -> data.recurring.firstOrNull { it.id == id } }
    val plan = req.installmentId?.let { id -> data.installments.firstOrNull { it.id == id } }
    val card = req.cardId?.let { data.accountById[it] }
    val accounts = remember(data.accounts) { data.accounts.filter { !it.archived } }

    var type by remember { mutableStateOf(editing?.type ?: preset?.type ?: req.type) }
    var digits by remember { mutableStateOf((editing?.amount ?: req.amount).takeIf { it > 0 }?.toString() ?: "") }
    var categoryId by remember { mutableStateOf(editing?.categoryId ?: preset?.let { PresetEngine.resolveCategory(data, it, now) } ?: req.categoryId) }
    var accountId by remember {
        mutableStateOf(editing?.accountId ?: req.accountId ?: PresetEngine.accountFor(data, preset, settings.defaultAccountId)?.id)
    }
    var toAccountId by remember { mutableStateOf(editing?.toAccountId ?: req.toAccountId ?: preset?.toAccountId) }
    var date by remember { mutableStateOf(editing?.date?.toLocalDate() ?: req.date ?: LocalDate.now()) }
    var note by remember { mutableStateOf(editing?.note ?: req.note) }
    var noteOpen by remember { mutableStateOf(false) }
    var commandMode by remember { mutableStateOf(req.command) }
    val hasSplits = editing != null && data.splitsByTx[editing.id] != null

    val amount = digits.toLongOrNull() ?: 0L
    val category = categoryId?.let { data.categoryById[it] }
    val needsCategory = type == TxType.EXPENSE || type == TxType.INCOME
    val canSave = amount > 0 && accountId != null &&
        (type != TxType.TRANSFER || (toAccountId != null && toAccountId != accountId)) &&
        (!needsCategory || categoryId != null || hasSplits)

    fun save() {
        if (!canSave) return
        val time = if (date == LocalDate.now()) LocalTime.now() else editing?.date?.toLocalDateTime()?.toLocalTime() ?: LocalTime.NOON
        val millis = date.atMillis(time)
        val label = when {
            recurring != null -> recurring.name
            plan != null -> plan.name
            type == TxType.TRANSFER -> tr("Transfer")
            else -> category?.name ?: preset?.label ?: tr("Transaction")
        }
        when {
            recurring != null && editing == null -> vm.payRecurring(recurring, amount, accountId!!, date)
            editing != null -> vm.updateTransaction(
                editing.copy(type = type, amount = amount, accountId = accountId!!, toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
                    categoryId = if (type == TxType.TRANSFER) null else categoryId, date = millis, note = note.trim()),
                message = tr("Updated · %s", Money.compact(amount)),
            )
            else -> vm.addTransaction(
                Txn(type = type, amount = amount, accountId = accountId!!, toAccountId = if (type == TxType.TRANSFER) toAccountId else null,
                    categoryId = if (type == TxType.TRANSFER) null else categoryId, date = millis, note = note.trim(),
                    presetId = preset?.id, installmentId = plan?.id, linkedTxId = req.linkedTxId),
                message = tr("%s · %s saved", label, Money.compact(amount)),
            )
        }
        nav.closeSheet()
    }

    // ---------- header ----------
    val title = when {
        recurring != null -> recurring.name
        plan != null -> plan.name
        card != null -> tr("Pay %s", card.name)
        req.linkedTxId != null -> if (type == TxType.REFUND) tr("Refund") else tr("Paid back")
        editing != null -> tr("Edit transaction")
        type == TxType.TRANSFER -> tr("Transfer")
        preset != null -> d.presetLabels[preset.id] ?: preset.label
        else -> category?.name ?: tr("Choose a category")
    }
    val subtitle = when {
        card != null -> tr("Settles card debt · not an expense")
        type == TxType.TRANSFER -> tr("Not income or expense")
        recurring != null && recurring.variable -> tr("Variable bill · usually ≈ %s", Money.compact(recurring.amount))
        plan != null -> tr("Installment payment")
        type == TxType.REFUND || type == TxType.REIMBURSEMENT -> tr("Reduces %s · not income", category?.name ?: tr("spending"))
        category != null && preset != null && category.name != title -> category.name
        else -> null
    }
    val headerIcon = when {
        type == TxType.TRANSFER -> LedgerIcons.of(if (card != null) "credit_card" else "transfer")
        else -> LedgerIcons.of(category?.icon ?: preset?.icon ?: recurring?.icon)
    }
    val headerTint = when {
        type == TxType.TRANSFER -> c.accent
        else -> hue(category?.colorIndex ?: preset?.colorIndex ?: 0)
    }

    if (req.composer && editing == null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(Shapes.chip).background(c.surfaceAlt).padding(3.dp)) {
                listOf(TxType.EXPENSE to tr("Expense"), TxType.INCOME to tr("Income"), TxType.TRANSFER to tr("Transfer")).forEach { (t, l) ->
                    val sel = type == t && !commandMode
                    Box(
                        Modifier.weight(1f).heightIn(min = 38.dp).clip(Shapes.chip).background(if (sel) c.accent else c.surfaceAlt)
                            .clickable { commandMode = false; if (type != t) { type = t; categoryId = null; if (t == TxType.TRANSFER && toAccountId == null) toAccountId = accounts.firstOrNull { it.id != accountId && it.type != AccountType.CREDIT_CARD }?.id } },
                        contentAlignment = Alignment.Center,
                    ) { Text(l, style = LedgerTheme.type.label, color = if (sel) c.onAccent else c.textMuted) }
                }
            }
            Spacer(Modifier.width(Space.s))
            Box(
                Modifier.size(44.dp).clip(Shapes.chip).background(if (commandMode) c.accent else c.surfaceAlt).clickable { commandMode = !commandMode },
                contentAlignment = Alignment.Center,
            ) { Icon(if (commandMode) Icons.Rounded.Keyboard else Icons.Rounded.Terminal, tr("Command entry"), tint = if (commandMode) c.onAccent else c.text, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.height(Space.m))
    }

    if (commandMode) {
        CommandEntry(d, vm, nav, settings.defaultAccountId)
        return
    }

    Row(Modifier.fillMaxWidth().padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
        IconWell(headerIcon, headerTint, size = 44.dp)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = LedgerTheme.type.headline, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DateChip(date) { date = it }
    }

    // ---------- amount ----------
    val hidden = LocalHideAmounts.current
    val shown = if (amount == 0L) "0" else formatAmount(amount, AmountFormat.NUMBER, hidden = hidden)
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), verticalAlignment = Alignment.Bottom) {
        val style = if (shown.length > 11) LedgerTheme.type.display else LedgerTheme.type.hero.copy(fontSize = 44.sp, lineHeight = 50.sp)
        AnimatedContent(shown, transitionSpec = { fadeIn(tween(Motion.FAST)) togetherWith fadeOut(tween(60)) }, label = "amt") { s ->
            Text(s, style = style, color = if (amount == 0L) c.textFaint else c.text, maxLines = 1)
        }
        Text(" ${Money.symbol}", style = LedgerTheme.type.title, color = c.textMuted, modifier = Modifier.padding(bottom = 6.dp))
        Spacer(Modifier.weight(1f))
        if (recurring != null && editing == null) {
            Chip(tr("Skip this time")) { vm.skipRecurring(recurring); nav.closeSheet() }
        }
    }

    // ---------- quick amounts ----------
    val quick = remember(preset?.id, categoryId, recurring?.id, type, accountId, toAccountId) {
        when {
            preset != null -> PresetEngine.quickAmounts(data, preset, LocalDate.now())
            recurring != null && recurring.variable -> listOf(recurring.amount)
            recurring != null || plan != null || card != null || req.linkedTxId != null -> emptyList()
            else -> learnedAmounts(data, type, categoryId, accountId, toAccountId)
        }
    }
    if (quick.isNotEmpty() && editing == null) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            quick.forEach { a ->
                val sel = amount == a
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(Shapes.chip).background(if (sel) c.accent else c.surfaceAlt)
                        .clickable { digits = a.toString() },
                    contentAlignment = Alignment.Center,
                ) { Text(formatAmount(a, AmountFormat.COMPACT, hidden = hidden), style = LedgerTheme.type.bodyStrong, color = if (sel) c.onAccent else c.text) }
            }
        }
        Spacer(Modifier.height(Space.m))
    }

    // ---------- category (composer / edit / anything without a preset) ----------
    val showCategories = needsCategory && preset == null && recurring == null && plan == null && !hasSplits
    if (showCategories) {
        CategoryRow(d, type == TxType.INCOME, categoryId) { categoryId = it }
        Spacer(Modifier.height(Space.s))
    }
    if (hasSplits) {
        Text(tr("Split across %d categories — edit the lines from the transaction.", data.splitsByTx[editing.id]!!.size), style = LedgerTheme.type.caption, color = c.textMuted,
            modifier = Modifier.padding(horizontal = Space.gutter, vertical = 4.dp))
    }

    // ---------- accounts ----------
    if (type == TxType.TRANSFER) {
        AccountRow(tr("From"), accounts.filter { it.type != AccountType.CREDIT_CARD }, accountId, d.balances) { accountId = it }
        Spacer(Modifier.height(Space.s))
        if (card == null) AccountRow(tr("To"), accounts.filter { it.id != accountId }, toAccountId, d.balances) { toAccountId = it }
    } else {
        // Savings rarely pay for things directly; they stay reachable through transfers.
        AccountRow(null, accounts.filter { it.type != AccountType.SAVINGS || it.id == accountId }, accountId, d.balances) { accountId = it }
    }

    // ---------- note ----------
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
        if (noteOpen) {
            val fr = remember { FocusRequester() }
            LaunchedEffect(Unit) { fr.requestFocus() }
            BasicTextField(
                note, { note = it.take(120) },
                Modifier.weight(1f).heightIn(min = 44.dp).clip(Shapes.chip).background(c.surfaceAlt).padding(horizontal = 14.dp, vertical = 12.dp).focusRequester(fr),
                textStyle = LedgerTheme.type.body.copy(color = c.text), singleLine = true, cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { noteOpen = false }),
                decorationBox = { inner -> Box { if (note.isEmpty()) Text(tr("Note (optional)"), style = LedgerTheme.type.body, color = c.textFaint); inner() } },
            )
            Spacer(Modifier.width(Space.s))
            Chip(tr("Done"), selected = true) { noteOpen = false }
        } else {
            Chip(if (note.isBlank()) tr("Add note") else note, icon = Icons.Rounded.EditNote, modifier = Modifier.weight(1f, fill = false)) { noteOpen = true }
        }
    }

    // ---------- keypad ----------
    AnimatedVisibility(!noteOpen, enter = slideInVertically { it / 3 } + fadeIn(), exit = fadeOut(tween(60))) {
        Numpad(
            onDigit = { k -> if (digits.length < 12 && !(digits.isEmpty() && k.startsWith("0"))) digits = (digits + k).take(12) },
            onBackspace = { digits = digits.dropLast(1) },
            onClear = { digits = "" },
            saveEnabled = canSave,
            saveLabel = when { recurring != null && editing == null -> tr("Pay"); card != null -> tr("Pay"); editing != null -> tr("Update"); else -> tr("Save") },
            onSave = ::save,
            modifier = Modifier.padding(top = 4.dp, bottom = Space.l),
        )
    }
}

/** Most frequent recent amounts for this kind of entry (same category, or same transfer route). */
private fun learnedAmounts(data: dev.personal.ledger.data.LedgerData, type: TxType, categoryId: Long?, from: Long?, to: Long?): List<Long> {
    val horizon = System.currentTimeMillis() - 120L * 86_400_000
    val similar = data.transactions.filter { t ->
        t.date > horizon && t.type == type && when (type) {
            TxType.TRANSFER -> t.accountId == from && t.toAccountId == to
            else -> categoryId != null && t.categoryId == categoryId
        }
    }
    return similar.groupingBy { it.amount }.eachCount().entries.sortedByDescending { it.value }.take(4).map { it.key }.sorted()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChip(date: LocalDate, onChange: (LocalDate) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Box {
        Chip(Fmt.relativeDay(date, today).let { if (it.length > 12) Fmt.dayMonth(date) else it }, icon = Icons.Rounded.CalendarToday, selected = date != today) { menu = true }
        DropdownMenu(menu, { menu = false }) {
            listOf(0L, 1L, 2L).forEach { n ->
                val dd = today.minusDays(n)
                DropdownMenuItem({ Text(if (n == 0L) tr("Today") else if (n == 1L) tr("Yesterday") else Fmt.relativeDay(dd, today)) }, { onChange(dd); menu = false })
            }
            DropdownMenuItem({ Text(tr("Pick a date…")) }, { menu = false; picker = true })
        }
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picker = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picker = false
                }) { Text(tr("OK")) }
            },
            dismissButton = { TextButton({ picker = false }) { Text(tr("Cancel")) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun AccountRow(label: String?, accounts: List<Account>, selected: Long?, balances: Map<Long, Long>, onSelect: (Long) -> Unit) {
    val c = LedgerTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(start = Space.gutter).width(40.dp))
        LazyRow(state = androidx.compose.foundation.lazy.rememberLazyListState((accounts.indexOfFirst { it.id == selected } - 1).coerceAtLeast(0)),
            contentPadding = PaddingValues(horizontal = if (label != null) 4.dp else Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            items(accounts, key = { it.id }) { a ->
                Chip(a.name, selected = a.id == selected, icon = LedgerIcons.forAccount(a.type)) { onSelect(a.id) }
            }
        }
    }
}

@Composable
private fun CategoryRow(d: Dashboard, income: Boolean, selected: Long?, onSelect: (Long) -> Unit) {
    val data = d.data
    val ordered = remember(data.transactions.size, income) {
        val horizon = System.currentTimeMillis() - 90L * 86_400_000
        val usage = data.transactions.filter { it.date > horizon && it.categoryId != null }.groupingBy { it.categoryId!! }.eachCount()
        data.categories.filter { !it.archived && it.isIncome == income }
            .sortedWith(compareByDescending<Category> { usage[it.id] ?: 0 }.thenBy { it.sortOrder })
    }
    LazyRow(contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(ordered, key = { it.id }) { cat ->
            Chip(cat.name, selected = cat.id == selected, icon = LedgerIcons.of(cat.icon)) { onSelect(cat.id) }
        }
    }
}

/** "85k ăn", "chuyển 5m mb vcb" — parsed live, saved with Enter. Never required for normal use. */
@Composable
private fun CommandEntry(d: Dashboard, vm: LedgerViewModel, nav: Nav, defaultAccountId: Long?) {
    val c = LedgerTheme.colors
    val view = LocalView.current
    var text by remember { mutableStateOf("") }
    val parsed = remember(text, d.data) { if (text.isBlank()) null else CommandParser.parse(text, d.data, defaultAccountId) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    fun save() {
        val p = parsed ?: return
        if (!p.complete) return
        Haptics.confirm(view)
        val date = LocalDate.now().minusDays(p.daysAgo.toLong())
        vm.addTransaction(
            Txn(type = p.type, amount = p.amount!!, accountId = p.account!!.id, toAccountId = p.toAccount?.id,
                categoryId = if (p.type == TxType.TRANSFER) null else p.categoryId ?: p.preset?.categoryId,
                date = date.atMillis(LocalTime.now()), note = p.note, presetId = p.preset?.id),
            message = tr("%s saved", Money.compact(p.amount)),
        )
        nav.closeSheet()
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l)) {
        BasicTextField(
            text, { text = it },
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Shapes.chip).background(c.surfaceAlt).padding(horizontal = 16.dp, vertical = 16.dp).focusRequester(fr),
            textStyle = LedgerTheme.type.headline.copy(color = c.text), singleLine = true, cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
            decorationBox = { inner -> Box { if (text.isEmpty()) Text("85k ăn · 350k xăng mb · chuyển 5m mb vcb", style = LedgerTheme.type.body, color = c.textFaint); inner() } },
        )
        Spacer(Modifier.height(Space.m))
        if (parsed != null) {
            val cat = parsed.categoryId?.let { d.data.categoryById[it] }
            val parts = listOfNotNull(
                when (parsed.type) { TxType.TRANSFER -> tr("Transfer"); TxType.INCOME -> tr("Income"); else -> tr("Expense") },
                if (parsed.type != TxType.TRANSFER) cat?.name ?: tr("No category") else null,
                parsed.amount?.let { Money.full(it) } ?: tr("No amount"),
                if (parsed.type == TxType.TRANSFER) "${parsed.account?.name ?: "?"} → ${parsed.toAccount?.name ?: "?"}" else parsed.account?.name,
                if (parsed.daysAgo == 1) tr("Yesterday") else tr("Today"),
                parsed.note.takeIf { it.isNotBlank() }?.let { "“$it”" },
            )
            Text(parts.joinToString("  ·  "), style = LedgerTheme.type.label, color = if (parsed.complete) c.text else c.textMuted)
            Spacer(Modifier.height(Space.m))
        } else {
            Text(tr("Amount, then a preset or category word. Add an account word (mb, vcb, vp) to change the account; start with “chuyển” for transfers or “+” for income."),
                style = LedgerTheme.type.caption, color = c.textMuted)
            Spacer(Modifier.height(Space.m))
        }
        PrimaryButton(tr("Save"), Modifier.fillMaxWidth(), enabled = parsed?.complete == true, icon = Icons.AutoMirrored.Rounded.ArrowForward) { save() }
        Spacer(Modifier.height(Space.l))
    }
}

