package dev.personal.ledger.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.Cadence
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Recurring
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.Seeds
import dev.personal.ledger.data.Settings
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.accountAliases
import dev.personal.ledger.domain.dayClamped
import dev.personal.ledger.domain.fold
import dev.personal.ledger.i18n.I18n
import dev.personal.ledger.i18n.Lang
import dev.personal.ledger.i18n.tr
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.components.AmountInput
import dev.personal.ledger.ui.components.Chip
import dev.personal.ledger.ui.components.ChoiceRow
import dev.personal.ledger.ui.components.FieldLabel
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.PrimaryButton
import dev.personal.ledger.ui.components.SecondaryButton
import dev.personal.ledger.ui.components.TextInput
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth

/** An account the user may create during setup: one of the suggestions, or one they typed themselves. */
private data class AccountDraft(val key: Int, val name: String, val type: AccountType, val aliases: String, val color: Int, val custom: Boolean = false)

/** Typed details for a draft; everything optional. */
private data class DraftDetails(val balance: String = "", val limit: String = "", val statementDay: String = "", val dueDay: String = "")

/** Popular banks and wallets offered as one-tap additions; anything else can be typed. */
private val quickNames = listOf(
    "HDBank" to AccountType.BANK, "BIDV" to AccountType.BANK, "VietinBank" to AccountType.BANK, "ACB" to AccountType.BANK,
    "VPBank" to AccountType.BANK, "TPBank" to AccountType.BANK, "Sacombank" to AccountType.BANK, "VIB" to AccountType.BANK,
    "Agribank" to AccountType.BANK, "SHB" to AccountType.BANK, "ShopeePay" to AccountType.EWALLET, "Viettel Money" to AccountType.EWALLET,
)

/**
 * Four short steps, everything optional except having one account: currency → accounts → presets → pay day.
 * "Explore with demo data" is on the first screen for instant review.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Onboarding(vm: LedgerViewModel) {
    val c = LedgerTheme.colors
    var step by remember { mutableStateOf(0) }
    var currency by remember { mutableStateOf("VND") }
    val drafts = remember(I18n.lang) {
        mutableStateListOf(
            AccountDraft(0, tr("Cash"), AccountType.CASH, "cash,tm,tienmat", 1),
            AccountDraft(1, "MB Bank", AccountType.BANK, "mb,mbbank", 2),
            AccountDraft(2, "Vietcombank", AccountType.BANK, "vcb,vietcombank", 0),
            AccountDraft(3, "Techcombank", AccountType.BANK, "tcb,techcombank", 3),
            AccountDraft(4, "MoMo", AccountType.EWALLET, "momo", 4),
            AccountDraft(5, "ZaloPay", AccountType.EWALLET, "zalo,zalopay", 2),
            AccountDraft(6, tr("Credit card"), AccountType.CREDIT_CARD, "card,the", 5),
            AccountDraft(7, tr("Savings"), AccountType.SAVINGS, "tk,saving", 6),
        )
    }
    val chosen = remember { mutableStateListOf(0, 1) }
    val details = remember { mutableStateMapOf<Int, DraftDetails>() }
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(AccountType.BANK) }
    val presets = remember { mutableStateListOf<Long>().apply { addAll(Seeds.presets().filter { it.sortOrder < 8 }.map { it.id }) } }
    var salaryDay by remember { mutableStateOf("") }
    var salaryAmount by remember { mutableStateOf("") }

    fun addCustom(name: String, type: AccountType) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        // Picking a name that is already listed just selects it.
        drafts.firstOrNull { it.name.fold() == clean.fold() }?.let { if (it.key !in chosen) chosen.add(it.key); return }
        val key = (drafts.maxOfOrNull { it.key } ?: 0) + 1
        drafts += AccountDraft(key, clean, type, accountAliases(clean), key % 8, custom = true)
        chosen.add(key)
    }

    // Asked once, at the end, in context: reminders are how variable bills get recorded in two taps.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun finish() {
        val accounts = drafts.filter { it.key in chosen }.mapIndexed { i, s ->
            val d = details[s.key] ?: DraftDetails()
            val bal = Money.parse(d.balance) ?: 0L
            val card = s.type == AccountType.CREDIT_CARD
            Account(
                id = i + 1L, name = s.name, type = s.type, aliases = s.aliases, openingBalance = if (card) -bal else bal,
                colorIndex = s.color, spendable = s.type != AccountType.SAVINGS && !card, sortOrder = i,
                // Card terms are only what the user entered; nothing is invented.
                creditLimit = if (card) Money.parse(d.limit)?.takeIf { it > 0 } else null,
                statementDay = if (card) d.statementDay.toIntOrNull()?.takeIf { it in 1..31 } else null,
                dueDay = if (card) d.dueDay.toIntOrNull()?.takeIf { it in 1..31 } else null,
            )
        }
        val default = accounts.firstOrNull { it.type == AccountType.BANK } ?: accounts.firstOrNull()
        val today = LocalDate.now()
        val day = salaryDay.toIntOrNull()?.takeIf { it in 1..31 }
        val salary = if (day != null && default != null) {
            var next = YearMonth.from(today).dayClamped(day)
            if (!next.isAfter(today)) next = YearMonth.from(today).plusMonths(1).dayClamped(day)
            listOf(Recurring(1, RecurringKind.INCOME, tr("Salary"), Money.parse(salaryAmount) ?: 0, salaryAmount.isBlank(), Cadence.MONTHLY, next.toEpochDay(), day,
                default.id, null, Seeds.SALARY, autoPay = salaryAmount.isNotBlank(), isSalary = true, icon = "salary"))
        } else emptyList()
        vm.replaceAll(
            LedgerData(accounts = accounts, categories = Seeds.categories, presets = Seeds.presets().map { it.copy(hidden = it.id !in presets) }, recurring = salary),
            Settings(onboarded = true, currency = currency, defaultAccountId = default?.id, language = I18n.lang.code),
        )
        if (android.os.Build.VERSION.SDK_INT >= 33) runCatching { notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }

    BackHandler(step > 0) { step-- }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (step > 0) Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < step) c.accent else c.surfaceAlt)) }
        }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { (slideInHorizontally(tween(Motion.STANDARD)) { it / 5 } + fadeIn()) togetherWith fadeOut(tween(Motion.FAST)) }, label = "onb") { s ->
            if (s == 0) Welcome(vm, onStart = { step = 1 }, onDemo = { vm.loadDemo() })
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (s) {
                    1 -> {
                        Title(tr("Currency"), tr("Amounts are stored in whole units of this currency."))
                        ChoiceRow(listOf("VND", "USD", "EUR", "JPY", "KRW", "THB"), currency, { it }) { currency = it }
                    }
                    2 -> {
                        Title(tr("Where is your money?"), tr("Pick what you use, or add your own bank or wallet. Balances are optional — you can adjust later."))
                        drafts.forEach { a ->
                            val on = a.key in chosen
                            val d = details[a.key] ?: DraftDetails()
                            Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 4.dp).clip(Shapes.card)
                                .background(if (on) c.surface else c.bg).border(1.dp, if (on) c.accent.copy(alpha = 0.5f) else c.hairline, Shapes.card)) {
                                Row(Modifier.fillMaxWidth().clickable { if (on) chosen.remove(a.key) else chosen.add(a.key) }.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                                    IconWell(LedgerIcons.forAccount(a.type), hue(a.color))
                                    Spacer(Modifier.width(Space.m))
                                    Column(Modifier.weight(1f)) {
                                        Text(a.name, style = LedgerTheme.type.bodyStrong, color = c.text)
                                        if (a.custom) Text(typeLabel(a.type), style = LedgerTheme.type.caption, color = c.textMuted)
                                    }
                                    if (a.custom) Box(
                                        Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) { drafts.remove(a); chosen.remove(a.key); details.remove(a.key) }
                                            .semantics { contentDescription = tr("Remove %s", a.name) },
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Rounded.Close, null, tint = c.textMuted, modifier = Modifier.size(18.dp)) }
                                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) c.accent else c.surfaceAlt), contentAlignment = Alignment.Center) {
                                        if (on) Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (on) Column(Modifier.padding(bottom = Space.m)) {
                                    val card = a.type == AccountType.CREDIT_CARD
                                    AmountInput(d.balance, { details[a.key] = (details[a.key] ?: DraftDetails()).copy(balance = it) },
                                        placeholder = if (card) tr("Current debt (optional)") else tr("Balance (optional)"), helper = "")
                                    if (card) {
                                        Spacer(Modifier.height(Space.s))
                                        AmountInput(d.limit, { details[a.key] = (details[a.key] ?: DraftDetails()).copy(limit = it) }, placeholder = tr("Credit limit (optional)"), helper = "")
                                        Spacer(Modifier.height(Space.s))
                                        Row {
                                            Box(Modifier.weight(1f)) {
                                                TextInput(d.statementDay, { details[a.key] = (details[a.key] ?: DraftDetails()).copy(statementDay = it.filter(Char::isDigit).take(2)) }, tr("Statement day"), keyboard = KeyboardType.Number)
                                            }
                                            Box(Modifier.weight(1f)) {
                                                TextInput(d.dueDay, { details[a.key] = (details[a.key] ?: DraftDetails()).copy(dueDay = it.filter(Char::isDigit).take(2)) }, tr("Payment due day"), keyboard = KeyboardType.Number)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Anything not in the list: tap a common name or type your own.
                        Text(tr("Other banks and wallets"), style = LedgerTheme.type.section, color = c.text,
                            modifier = Modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.xl, bottom = Space.s))
                        val taken = drafts.map { it.name.fold() }.toSet()
                        FlowRow(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            quickNames.filter { it.first.fold() !in taken }.forEach { (n, t) ->
                                Chip(n, icon = Icons.Rounded.Add) { addCustom(n, t) }
                            }
                        }
                        FieldLabel(tr("Or type a name"))
                        TextInput(newName, { newName = it.take(32) }, tr("e.g. HDBank, Viettel Money, Quỹ lớp"))
                        Spacer(Modifier.height(Space.s))
                        ChoiceRow(listOf(AccountType.BANK, AccountType.EWALLET, AccountType.CASH, AccountType.SAVINGS, AccountType.CREDIT_CARD), newType, { typeLabel(it) }) { newType = it }
                        Spacer(Modifier.height(Space.m))
                        Row(Modifier.padding(horizontal = Space.l)) {
                            SecondaryButton(tr("Add account"), icon = Icons.Rounded.Add, color = if (newName.isBlank()) c.textFaint else c.text) {
                                if (newName.isNotBlank()) { addCustom(newName, newType); newName = "" }
                            }
                        }
                        Spacer(Modifier.height(Space.xl))
                    }
                    3 -> {
                        Title(tr("Quick presets"), tr("One tap from Home. You can pin, reorder and add more any time."))
                        Seeds.presets().chunked(3).forEach { row ->
                            Row(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                row.forEach { p ->
                                    val on = p.id in presets
                                    Column(
                                        Modifier.weight(1f).padding(vertical = 4.dp).clip(Shapes.tile).background(if (on) c.accentSoft else c.surfaceAlt)
                                            .clickable { if (on) presets.remove(p.id) else presets.add(p.id) }.padding(vertical = Space.m),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Icon(LedgerIcons.of(p.icon), null, tint = if (on) hue(p.colorIndex) else c.textFaint)
                                        Spacer(Modifier.height(6.dp))
                                        Text(p.label, style = LedgerTheme.type.caption, color = if (on) c.text else c.textMuted)
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    4 -> {
                        Title(tr("Pay day (optional)"), tr("Lets “safe to spend” count down to your next salary."))
                        FieldLabel(tr("Day of month"))
                        TextInput(salaryDay, { salaryDay = it.filter(Char::isDigit).take(2) }, "10", keyboard = KeyboardType.Number)
                        FieldLabel(tr("Usual amount (optional)"))
                        AmountInput(salaryAmount, { salaryAmount = it })
                    }
                }
            }
        }
        if (step > 0) Row(Modifier.fillMaxWidth().padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
            if (step == 4) Text(tr("Skip"), style = LedgerTheme.type.bodyStrong, color = c.textMuted, modifier = Modifier.clip(Shapes.pill).clickable { salaryDay = ""; finish() }.padding(16.dp))
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (step == 4) tr("Done") else tr("Continue"), enabled = step != 2 || chosen.isNotEmpty()) { if (step == 4) finish() else step++ }
        }
    }
}

private fun typeLabel(t: AccountType): String = when (t) {
    AccountType.CASH -> tr("Cash"); AccountType.BANK -> tr("Bank"); AccountType.EWALLET -> tr("E-wallet")
    AccountType.SAVINGS -> tr("Savings"); AccountType.CREDIT_CARD -> tr("Credit card"); AccountType.OTHER -> tr("Other")
}

@Composable
private fun Title(title: String, sub: String) {
    val c = LedgerTheme.colors
    Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.l)) {
        Text(title, style = LedgerTheme.type.title, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(sub, style = LedgerTheme.type.body, color = c.textMuted)
    }
}

@Composable
private fun Welcome(vm: LedgerViewModel, onStart: () -> Unit, onDemo: () -> Unit) {
    val c = LedgerTheme.colors
    // Language switch is available before anything else; Vietnamese is the default.
    Box(Modifier.fillMaxWidth().padding(end = Space.l, top = Space.s), contentAlignment = Alignment.TopEnd) {
        val other = if (I18n.vi) Lang.EN else Lang.VI
        Text(other.label, style = LedgerTheme.type.label, color = c.textMuted,
            modifier = Modifier.clip(Shapes.pill).clickable { vm.updateSettings { it.copy(language = other.code) } }.padding(horizontal = 14.dp, vertical = 10.dp))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = Space.xxl).padding(top = 28.dp, bottom = Space.l)) {
        Box(Modifier.size(64.dp).clip(Shapes.tile).background(c.accent), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(22.dp, 32.dp, 14.dp).forEach { w -> Box(Modifier.width(w).height(5.dp).clip(CircleShape).background(c.onAccent)) }
            }
        }
        Spacer(Modifier.height(Space.xxl))
        Text(tr("Know where your\nmoney is. Always."), style = LedgerTheme.type.display.copy(fontSize = 32.sp, lineHeight = 40.sp), color = c.text)
        Spacer(Modifier.height(Space.l))
        listOf(
            tr("Log an expense in three taps: preset, amount, save."),
            tr("See what's safe to spend before your next salary."),
            tr("Every bill, card due and installment on one timeline."),
            tr("Private: stored only on this phone, exportable anytime."),
        ).forEach { line ->
            Row(Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(Space.m))
                Text(line, style = LedgerTheme.type.body, color = c.textMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton(tr("Get started"), Modifier.fillMaxWidth(), onClick = onStart)
        Spacer(Modifier.height(Space.m))
        Text(tr("Explore with demo data"), style = LedgerTheme.type.bodyStrong, color = c.textMuted,
            modifier = Modifier.fillMaxWidth().clip(Shapes.pill).clickable(onClick = onDemo).heightIn(min = 48.dp).padding(14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
