package dev.personal.ledger.ui.plan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.Cadence
import dev.personal.ledger.data.Installment
import dev.personal.ledger.data.InstallmentStatus
import dev.personal.ledger.data.Recurring
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.Seeds
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Installments
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.epochDayToDate
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.Route
import dev.personal.ledger.ui.common.IconAction
import dev.personal.ledger.ui.common.Screen
import dev.personal.ledger.ui.components.AccountChoice
import dev.personal.ledger.ui.components.Amount
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.AmountInput
import dev.personal.ledger.ui.components.Bar
import dev.personal.ledger.ui.components.CategoryChoice
import dev.personal.ledger.ui.components.ChoiceRow
import dev.personal.ledger.ui.components.DateField
import dev.personal.ledger.ui.components.Divider
import dev.personal.ledger.ui.components.EmptyState
import dev.personal.ledger.ui.components.FieldLabel
import dev.personal.ledger.ui.components.FormFooter
import dev.personal.ledger.ui.components.HueChoice
import dev.personal.ledger.ui.components.IconChoice
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerCard
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.ListRow
import dev.personal.ledger.ui.components.Metric
import dev.personal.ledger.ui.components.PrimaryButton
import dev.personal.ledger.ui.components.SectionHeader
import dev.personal.ledger.ui.components.Tag
import dev.personal.ledger.ui.components.TextInput
import dev.personal.ledger.ui.components.ToggleRow
import dev.personal.ledger.ui.components.Tone
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Space
import java.time.LocalDate

// ---------------- subscriptions & bills ----------------

@Composable
fun RecurringListScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav, kind: RecurringKind?) {
    val c = LedgerTheme.colors
    val all = d.data.recurring.filter { kind == null || it.kind == kind }
    val active = all.filter { it.active }.sortedBy { it.nextDue }
    val inactive = all.filter { !it.active }
    val monthly = active.sumOf { it.cadence.monthly(it.amount) }
    val title = when (kind) { RecurringKind.SUBSCRIPTION -> "Subscriptions"; RecurringKind.BILL -> "Bills"; RecurringKind.INCOME -> "Income"; else -> "Recurring" }
    Screen(title, onBack = { nav.pop() }, actions = { IconAction(Icons.Rounded.Add, "Add") { nav.push(Route.EditRecurring(null, kind ?: RecurringKind.BILL)) } }) {
        item {
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                Text("${active.size} active", style = LedgerTheme.type.label, color = c.textMuted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Amount(monthly, style = LedgerTheme.type.hero, format = AmountFormat.COMPACT)
                    Text(" / month", style = LedgerTheme.type.bodyStrong, color = c.textMuted, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("${Money.compact(monthly * 12)} / year", style = LedgerTheme.type.bodyStrong, color = c.textMuted)
            }
        }
        if (active.isEmpty()) item {
            EmptyState(Icons.Rounded.Subscriptions, "Nothing here yet", "Add $title so they show up in your timeline, forecast and safe-to-spend.", action = "Add") {
                nav.push(Route.EditRecurring(null, kind ?: RecurringKind.BILL))
            }
        }
        if (active.isNotEmpty()) {
            item { SectionHeader("Upcoming charges", Modifier.padding(top = Space.m)) }
            items(active.take(5), key = { "u${it.id}" }) { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.monthDay(r.nextDue.epochDayToDate()), style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.width(64.dp))
                    Text(r.name, style = LedgerTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                    Amount(r.amount, color = if (r.variable) c.textMuted else c.text)
                }
            }
            item { SectionHeader("All", Modifier.padding(top = Space.m)) }
            items(active, key = { it.id }) { r -> RecurringRow(d, r) { nav.push(Route.EditRecurring(r.id, r.kind)) } }
        }
        if (inactive.isNotEmpty()) {
            item { SectionHeader("Paused", Modifier.padding(top = Space.m)) }
            items(inactive, key = { it.id }) { r -> Column(Modifier.graphicsLayer { alpha = 0.55f }) { RecurringRow(d, r) { nav.push(Route.EditRecurring(r.id, r.kind)) } } }
        }
    }
}

@Composable
private fun RecurringRow(d: Dashboard, r: Recurring, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val history = d.data.priceChanges.filter { it.recurringId == r.id }.sortedBy { it.effectiveDay }
    val prev = history.dropLast(1).lastOrNull()?.amount
    val acc = d.data.accountById[r.accountId]?.name ?: ""
    ListRow(
        r.name,
        subtitle = listOfNotNull(r.cadence.label, acc, if (r.autoPay) "auto-pay" else null, if (r.variable) "variable" else null).joinToString(" · "),
        leading = { IconWell(LedgerIcons.of(r.icon), hue(r.colorIndex)) },
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.End) {
            Amount(r.amount, color = if (r.variable) c.textMuted else c.text)
            when {
                prev != null && prev != r.amount -> Tag("${if (r.amount > prev) "↑" else "↓"} from ${Money.compact(prev)}", if (r.amount > prev) Tone.CAUTION else Tone.POSITIVE)
                else -> Text("next ${Fmt.monthDay(r.nextDue.epochDayToDate())}", style = LedgerTheme.type.caption, color = c.textMuted)
            }
        }
    }
}

@Composable
fun EditRecurringScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav, id: Long?, defaultKind: RecurringKind) {
    val existing = id?.let { rid -> d.data.recurring.firstOrNull { it.id == rid } }
    val accounts = d.data.accounts.filter { !it.archived }
    var kind by remember { mutableStateOf(existing?.kind ?: defaultKind) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var variable by remember { mutableStateOf(existing?.variable ?: false) }
    var cadence by remember { mutableStateOf(existing?.cadence ?: Cadence.MONTHLY) }
    var next by remember { mutableStateOf(existing?.nextDue?.epochDayToDate() ?: LocalDate.now().plusDays(7)) }
    var account by remember { mutableStateOf(existing?.accountId ?: accounts.firstOrNull { it.type == AccountType.BANK }?.id) }
    var toAccount by remember { mutableStateOf(existing?.toAccountId) }
    var category by remember { mutableStateOf(existing?.categoryId ?: if (defaultKind == RecurringKind.SUBSCRIPTION) Seeds.SUBS else null) }
    var autoPay by remember { mutableStateOf(existing?.autoPay ?: (defaultKind == RecurringKind.SUBSCRIPTION)) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var salary by remember { mutableStateOf(existing?.isSalary ?: false) }
    var icon by remember { mutableStateOf(existing?.icon ?: if (defaultKind == RecurringKind.SUBSCRIPTION) "subscriptions" else "receipt") }
    var color by remember { mutableStateOf(existing?.colorIndex ?: 7) }
    val parsed = Money.parse(amount)
    val valid = name.isNotBlank() && parsed != null && parsed > 0 && account != null && (kind != RecurringKind.TRANSFER || (toAccount != null && toAccount != account))

    Screen(if (existing == null) "New ${kind.name.lowercase()}" else existing.name, onBack = { nav.pop() }) {
        item {
            FieldLabel("Type")
            ChoiceRow(RecurringKind.entries, kind, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { kind = it }
            FieldLabel("Name"); TextInput(name, { name = it }, "Netflix, Tiền trọ, Điện…")
            FieldLabel(if (variable) "Typical amount (used as estimate)" else "Amount"); AmountInput(amount, { amount = it })
            if (kind == RecurringKind.BILL || kind == RecurringKind.SUBSCRIPTION)
                ToggleRow("Variable amount", variable, "You'll be reminded to enter the actual amount (e.g. electricity)") { variable = it; if (it) autoPay = false }
            FieldLabel("Repeats"); ChoiceRow(Cadence.entries, cadence, { it.label }) { cadence = it }
            FieldLabel("Next due"); DateField(next) { next = it }
            FieldLabel(if (kind == RecurringKind.INCOME) "Paid into" else if (kind == RecurringKind.TRANSFER) "From" else "Paid from")
            AccountChoice(accounts, account) { account = it }
            if (kind == RecurringKind.TRANSFER) { FieldLabel("To"); AccountChoice(accounts.filter { it.id != account }, toAccount) { toAccount = it } }
            if (kind != RecurringKind.TRANSFER) {
                FieldLabel("Category")
                CategoryChoice(d.data.categories.filter { it.isIncome == (kind == RecurringKind.INCOME) && !it.archived }, category) { category = it }
            }
            FieldLabel("Icon"); IconChoice(icon, hue(color)) { icon = it }
            Spacer(Modifier.height(Space.s)); HueChoice(color) { color = it }
            Spacer(Modifier.height(Space.m))
            if (!variable) ToggleRow("Auto-pay", autoPay, "Recorded automatically on the due date") { autoPay = it }
            if (kind == RecurringKind.INCOME) ToggleRow("This is my salary", salary, "Safe to spend counts down to this date") { salary = it }
            ToggleRow("Active", active) { active = it }
            if (existing != null) {
                val history = d.data.priceChanges.filter { it.recurringId == existing.id }.sortedByDescending { it.effectiveDay }
                if (history.size > 1) {
                    SectionHeader("Price history", Modifier.padding(top = Space.m))
                    history.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 6.dp)) {
                            Text("since ${Fmt.dayMonth(p.effectiveDay.epochDayToDate())} ${p.effectiveDay.epochDayToDate().year}", style = LedgerTheme.type.label, color = LedgerTheme.colors.textMuted, modifier = Modifier.weight(1f))
                            Amount(p.amount, format = AmountFormat.NUMBER)
                        }
                    }
                }
            }
            FormFooter("Save", valid, onSave = {
                vm.saveRecurring(Recurring(
                    id = existing?.id ?: 0, kind = kind, name = name.trim(), amount = parsed!!, variable = variable, cadence = cadence,
                    nextDue = next.toEpochDay(), anchorDay = next.dayOfMonth, accountId = account!!, toAccountId = if (kind == RecurringKind.TRANSFER) toAccount else null,
                    categoryId = if (kind == RecurringKind.TRANSFER) null else category, autoPay = autoPay && !variable, active = active,
                    isSalary = kind == RecurringKind.INCOME && salary, icon = icon, colorIndex = color, note = existing?.note ?: "",
                ))
                nav.pop()
            }, destructive = if (existing != null) "Delete" else null, onDestructive = existing?.let { e -> { vm.deleteRecurring(e); nav.pop() } })
        }
    }
}

// ---------------- installments ----------------

@Composable
fun InstallmentListScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav) {
    val c = LedgerTheme.colors
    val plans = d.data.installments.sortedBy { it.status.ordinal }
    val progress = plans.associate { it.id to Installments.progress(d.data, it) }
    val active = plans.filter { it.status == InstallmentStatus.ACTIVE && progress[it.id]!!.nextIndex != null }
    Screen("Installments", onBack = { nav.pop() }, actions = { IconAction(Icons.Rounded.Add, "Add plan") { nav.push(Route.EditInstallment(null)) } }) {
        item {
            Row(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                Metric("Remaining debt", active.sumOf { progress[it.id]!!.remaining }, Modifier.weight(1f), style = LedgerTheme.type.display)
                Metric("Monthly burden", active.sumOf { progress[it.id]!!.nextAmount }, Modifier.weight(1f), style = LedgerTheme.type.display)
            }
        }
        if (plans.isEmpty()) item {
            EmptyState(Icons.Rounded.CreditScore, "No installment plans", "Track 0% card installments and pay-later plans as real debts with a schedule.", action = "Add plan") { nav.push(Route.EditInstallment(null)) }
        }
        items(plans, key = { it.id }) { p ->
            val pr = progress[p.id]!!
            LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 6.dp), onClick = { nav.push(Route.InstallmentDetail(p.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconWell(LedgerIcons.of(p.icon), hue(p.colorIndex))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = LedgerTheme.type.headline, color = c.text)
                        Text("Total ${Money.compact(p.totalPayable)} · ${d.data.accountById[p.accountId]?.name ?: ""}", style = LedgerTheme.type.caption, color = c.textMuted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Amount(pr.remaining, style = LedgerTheme.type.headline)
                        Text("remaining", style = LedgerTheme.type.caption, color = c.textMuted)
                    }
                }
                Spacer(Modifier.height(Space.m))
                PeriodTrack(p.periods, pr.paidPeriods)
                Spacer(Modifier.height(Space.s))
                Row {
                    Text("${pr.paidPeriods} / ${p.periods} paid", style = LedgerTheme.type.label, color = c.text, modifier = Modifier.weight(1f))
                    Text(pr.nextDue?.let { "Next ${Money.compact(pr.nextAmount)} on ${Fmt.monthDay(it)}" } ?: "Completed", style = LedgerTheme.type.label, color = if (pr.nextDue == null) c.positive else c.textMuted)
                }
            }
        }
    }
}

/** One segment per period: filled = paid. Reads faster than a percentage for "7 of 12". */
@Composable
fun PeriodTrack(periods: Int, paid: Int) {
    val c = LedgerTheme.colors
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val gap = if (periods > 24) 1.dp.toPx() else 3.dp.toPx()
        val w = (size.width - gap * (periods - 1)) / periods
        for (i in 0 until periods) {
            drawRoundRect(if (i < paid) c.accent else c.surfaceAlt, Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(size.height / 2))
        }
    }
}

@Composable
fun InstallmentDetailScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav, id: Long) {
    val c = LedgerTheme.colors
    val p = d.data.installments.firstOrNull { it.id == id } ?: run { nav.pop(); return }
    val pr = Installments.progress(d.data, p)
    val first = p.firstDue.epochDayToDate()
    Screen(p.name, onBack = { nav.pop() }, subtitle = p.note.ifBlank { null }, actions = { IconAction(LedgerIcons.of("more"), "Edit") { nav.push(Route.EditInstallment(p.id)) } }) {
        item {
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                Text("Remaining", style = LedgerTheme.type.label, color = c.textMuted)
                Amount(pr.remaining, style = LedgerTheme.type.hero, format = AmountFormat.FULL)
                Spacer(Modifier.height(Space.m))
                PeriodTrack(p.periods, pr.paidPeriods)
                Spacer(Modifier.height(Space.s))
                Text("${pr.paidPeriods} of ${p.periods} paid · ${Money.compact(pr.paidAmount)} of ${Money.compact(p.totalPayable)}", style = LedgerTheme.type.label, color = c.textMuted)
            }
        }
        item { RemainingChart(p, pr.paidPeriods) }
        item {
            LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
                listOf(
                    "Original amount" to Money.full(p.principal),
                    "Interest" to Money.full(p.interest),
                    "Fees" to Money.full(p.fees),
                    "Total payable" to Money.full(p.totalPayable),
                    "Per period" to Money.full(p.periodAmount(0)),
                    "First payment" to Fmt.full(first),
                    "Last payment" to Fmt.full(Installments.dueOf(first, p.periods - 1)),
                    "Paid from" to (d.data.accountById[p.accountId]?.name ?: "—"),
                    "Status" to if (pr.nextIndex == null) "Completed" else if (p.autoPay) "Active · auto-recorded" else "Active",
                ).forEachIndexed { i, (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(k, style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.weight(1f))
                        Text(v, style = LedgerTheme.type.label, color = c.text)
                    }
                    if (i < 8) Divider()
                }
            }
        }
        if (pr.nextIndex != null) item {
            Spacer(Modifier.height(Space.m))
            // Auto-recorded plans never offer a manual button: recording it twice would double count.
            if (p.autoPay) Text("Payment ${pr.nextIndex + 1} of ${Money.compact(pr.nextAmount)} is recorded automatically on ${Fmt.dayMonth(pr.nextDue!!)}.",
                style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.gutter))
            else PrimaryButton("Record payment ${pr.nextIndex + 1} · ${Money.compact(pr.nextAmount)}", Modifier.fillMaxWidth().padding(horizontal = Space.l)) { vm.payInstallment(p) }
        }
        item { SectionHeader("Schedule", Modifier.padding(top = Space.l)) }
        items((0 until p.periods).toList()) { i ->
            val due = Installments.dueOf(first, i)
            val paid = i < pr.paidPeriods
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = LedgerTheme.type.label, color = c.textFaint, modifier = Modifier.width(28.dp))
                Text(Fmt.full(due), style = LedgerTheme.type.body, color = if (paid) c.textMuted else c.text, modifier = Modifier.weight(1f))
                Amount(p.periodAmount(i), color = if (paid) c.textMuted else c.text)
                Spacer(Modifier.width(Space.s))
                Tag(if (paid) "Paid" else if (i == pr.nextIndex) "Next" else "Due", if (paid) Tone.POSITIVE else if (i == pr.nextIndex) Tone.ACCENT else Tone.NEUTRAL)
            }
        }
    }
}

/** Remaining balance after each period: bars shrink toward zero, paid ones solid, future ones outlined. */
@Composable
private fun RemainingChart(p: Installment, paid: Int) {
    val c = LedgerTheme.colors
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        Text("Balance after each payment", style = LedgerTheme.type.label, color = c.textMuted)
        Spacer(Modifier.height(Space.m))
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val n = p.periods
            val gap = 4.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            var remaining = p.totalPayable
            for (i in 0 until n) {
                remaining -= p.periodAmount(i)
                val h = size.height * remaining / p.totalPayable.toFloat()
                val tl = Offset(i * (w + gap), size.height - h.coerceAtLeast(2.dp.toPx()))
                val sz = Size(w, h.coerceAtLeast(2.dp.toPx()))
                if (i < paid) drawRoundRect(c.accent, tl, sz, CornerRadius(4.dp.toPx()))
                else drawRoundRect(c.accent.copy(alpha = 0.5f), tl, sz, CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

@Composable
fun EditInstallmentScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav, id: Long?) {
    val existing = id?.let { iid -> d.data.installments.firstOrNull { it.id == iid } }
    val accounts = d.data.accounts.filter { !it.archived }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var principal by remember { mutableStateOf(existing?.principal?.toString() ?: "") }
    var interest by remember { mutableStateOf(existing?.interest?.toString() ?: "0") }
    var fees by remember { mutableStateOf(existing?.fees?.toString() ?: "0") }
    var periods by remember { mutableStateOf(existing?.periods?.toString() ?: "12") }
    var prepaid by remember { mutableStateOf(existing?.prepaidPeriods?.toString() ?: "0") }
    var first by remember { mutableStateOf(existing?.firstDue?.epochDayToDate() ?: LocalDate.now().plusMonths(1)) }
    var account by remember { mutableStateOf(existing?.accountId ?: accounts.firstOrNull { it.type == AccountType.CREDIT_CARD }?.id ?: accounts.firstOrNull()?.id) }
    var autoPay by remember { mutableStateOf(existing?.autoPay ?: true) }
    var icon by remember { mutableStateOf(existing?.icon ?: "devices") }
    var color by remember { mutableStateOf(existing?.colorIndex ?: 5) }
    val p = Money.parse(principal); val i = Money.parse(interest.ifBlank { "0" }); val f = Money.parse(fees.ifBlank { "0" })
    val n = periods.toIntOrNull(); val pre = prepaid.toIntOrNull() ?: 0
    val valid = name.isNotBlank() && p != null && p > 0 && i != null && f != null && n != null && n in 1..120 && pre in 0..n && account != null
    Screen(if (existing == null) "New installment plan" else existing.name, onBack = { nav.pop() }) {
        item {
            FieldLabel("What did you buy?"); TextInput(name, { name = it }, "MacBook, iPhone, xe máy…")
            FieldLabel("Original amount"); AmountInput(principal, { principal = it })
            Row { Column(Modifier.weight(1f)) { FieldLabel("Interest"); AmountInput(interest, { interest = it }, helper = "Total, 0 if none") }
                Column(Modifier.weight(1f)) { FieldLabel("Fees"); AmountInput(fees, { fees = it }, helper = "Conversion fee etc.") } }
            Row { Column(Modifier.weight(1f)) { FieldLabel("Months"); TextInput(periods, { periods = it.filter(Char::isDigit) }, "12", keyboard = KeyboardType.Number) }
                Column(Modifier.weight(1f)) { FieldLabel("Already paid"); TextInput(prepaid, { prepaid = it.filter(Char::isDigit) }, "0", keyboard = KeyboardType.Number, helper = "Before using this app") } }
            if (valid) Text("= ${Money.full((p + i + f) / n)} per month", style = LedgerTheme.type.label, color = LedgerTheme.colors.accent, modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s))
            FieldLabel("First payment date"); DateField(first) { first = it }
            FieldLabel("Charged to"); AccountChoice(accounts, account) { account = it }
            FieldLabel("Icon"); IconChoice(icon, hue(color)) { icon = it }
            Spacer(Modifier.height(Space.s)); HueChoice(color) { color = it }
            ToggleRow("Record payments automatically", autoPay, "Each payment is posted on its due date as a fixed expense") { autoPay = it }
            Text("Each payment counts as spending when it happens; the unpaid rest is a liability in net worth. Don't also record the original purchase as an expense.",
                style = LedgerTheme.type.caption, color = LedgerTheme.colors.textMuted, modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s))
            FormFooter("Save", valid, onSave = {
                vm.saveInstallment(Installment(
                    id = existing?.id ?: 0, name = name.trim(), principal = p!!, interest = i!!, fees = f!!, periods = n!!, firstDue = first.toEpochDay(),
                    accountId = account!!, categoryId = existing?.categoryId ?: Seeds.INSTALLMENTS, prepaidPeriods = pre, autoPay = autoPay,
                    status = existing?.status ?: InstallmentStatus.ACTIVE, icon = icon, colorIndex = color, note = existing?.note ?: "",
                ))
                nav.pop()
            }, destructive = if (existing != null) "Delete plan" else null, onDestructive = existing?.let { e -> { vm.deleteInstallment(e); nav.pop(); nav.pop() } })
        }
    }
}
