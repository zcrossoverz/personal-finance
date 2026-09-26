package dev.personal.ledger.ui.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.Cadence
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Recurring
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.Seeds
import dev.personal.ledger.data.Settings
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.dayClamped
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.components.AmountInput
import dev.personal.ledger.ui.components.ChoiceRow
import dev.personal.ledger.ui.components.FieldLabel
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.PrimaryButton
import dev.personal.ledger.ui.components.TextInput
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import java.time.LocalDate
import java.time.YearMonth

private data class AccountDraft(val name: String, val type: AccountType, val aliases: String, val color: Int)

/**
 * Four short steps, everything optional except having one account: currency → accounts → presets → pay day.
 * "Explore with demo data" is on the first screen for instant review.
 */
@Composable
fun Onboarding(vm: LedgerViewModel) {
    val c = LedgerTheme.colors
    var step by remember { mutableStateOf(0) }
    var currency by remember { mutableStateOf("VND") }
    val suggestions = remember {
        listOf(
            AccountDraft("Tiền mặt", AccountType.CASH, "cash,tm,tienmat", 1),
            AccountDraft("MB Bank", AccountType.BANK, "mb,mbbank", 2),
            AccountDraft("Vietcombank", AccountType.BANK, "vcb", 0),
            AccountDraft("Techcombank", AccountType.BANK, "tcb", 3),
            AccountDraft("MoMo", AccountType.EWALLET, "momo", 4),
            AccountDraft("ZaloPay", AccountType.EWALLET, "zalo,zalopay", 2),
            AccountDraft("Credit card", AccountType.CREDIT_CARD, "card,the", 5),
            AccountDraft("Savings", AccountType.SAVINGS, "tk,saving", 6),
        )
    }
    val chosen = remember { mutableStateListOf(0, 1) }
    val balances = remember { mutableStateMapOf<Int, String>() }
    val presets = remember { mutableStateListOf<Long>().apply { addAll(Seeds.presets().filter { it.sortOrder < 8 }.map { it.id }) } }
    var salaryDay by remember { mutableStateOf("") }
    var salaryAmount by remember { mutableStateOf("") }

    // Asked once, at the end, in context: reminders are how variable bills get recorded in two taps.
    val notifications = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}

    fun finish() {
        if (android.os.Build.VERSION.SDK_INT >= 33) notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        val accounts = chosen.sorted().mapIndexed { i, idx ->
            val s = suggestions[idx]
            val bal = balances[idx]?.let { Money.parse(it) } ?: 0L
            Account(id = i + 1L, name = s.name, type = s.type, aliases = s.aliases, openingBalance = if (s.type == AccountType.CREDIT_CARD) -bal else bal,
                colorIndex = s.color, spendable = s.type != AccountType.SAVINGS && s.type != AccountType.CREDIT_CARD, sortOrder = i,
                creditLimit = if (s.type == AccountType.CREDIT_CARD) 30_000_000 else null,
                statementDay = if (s.type == AccountType.CREDIT_CARD) 20 else null, dueDay = if (s.type == AccountType.CREDIT_CARD) 5 else null)
        }
        val default = accounts.firstOrNull { it.type == AccountType.BANK } ?: accounts.firstOrNull()
        val today = LocalDate.now()
        val day = salaryDay.toIntOrNull()?.takeIf { it in 1..31 }
        val salary = if (day != null && default != null) {
            var next = YearMonth.from(today).dayClamped(day)
            if (!next.isAfter(today)) next = YearMonth.from(today).plusMonths(1).dayClamped(day)
            listOf(Recurring(1, RecurringKind.INCOME, "Lương", Money.parse(salaryAmount) ?: 0, salaryAmount.isBlank(), Cadence.MONTHLY, next.toEpochDay(), day,
                default.id, null, Seeds.SALARY, autoPay = salaryAmount.isNotBlank(), isSalary = true, icon = "salary"))
        } else emptyList()
        vm.replaceAll(
            LedgerData(accounts = accounts, categories = Seeds.categories, presets = Seeds.presets().map { it.copy(hidden = it.id !in presets) }, recurring = salary),
            Settings(onboarded = true, currency = currency, defaultAccountId = default?.id),
        )
    }

    BackHandler(step > 0) { step-- }
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (step > 0) Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i < step) c.accent else c.surfaceAlt)) }
        }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = { (slideInHorizontally(tween(Motion.STANDARD)) { it / 5 } + fadeIn()) togetherWith fadeOut(tween(Motion.FAST)) }, label = "onb") { s ->
            if (s == 0) Welcome(onStart = { step = 1 }, onDemo = { vm.loadDemo() })
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (s) {
                    1 -> {
                        Title("Currency", "Amounts are stored in whole units of this currency.")
                        ChoiceRow(listOf("VND", "USD", "EUR", "JPY", "KRW", "THB"), currency, { it }) { currency = it }
                    }
                    2 -> {
                        Title("Where is your money?", "Pick what you use. Balances are optional — you can adjust later.")
                        suggestions.forEachIndexed { i, a ->
                            val on = i in chosen
                            Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 4.dp).clip(Shapes.card)
                                .background(if (on) c.surface else c.bg).border(1.dp, if (on) c.accent.copy(alpha = 0.5f) else c.hairline, Shapes.card)) {
                                Row(Modifier.fillMaxWidth().clickable { if (on) chosen.remove(i) else chosen.add(i) }.padding(Space.m), verticalAlignment = Alignment.CenterVertically) {
                                    IconWell(LedgerIcons.forAccount(a.type), hue(a.color))
                                    Spacer(Modifier.width(Space.m))
                                    Text(a.name, style = LedgerTheme.type.bodyStrong, color = c.text, modifier = Modifier.weight(1f))
                                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) c.accent else c.surfaceAlt), contentAlignment = Alignment.Center) {
                                        if (on) Icon(Icons.Rounded.Check, null, tint = c.onAccent, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (on) Box(Modifier.padding(bottom = Space.m)) {
                                    AmountInput(balances[i] ?: "", { balances[i] = it }, placeholder = if (a.type == AccountType.CREDIT_CARD) "Current debt (optional)" else "Balance (optional)")
                                }
                            }
                        }
                    }
                    3 -> {
                        Title("Quick presets", "One tap from Home. You can pin, reorder and add more any time.")
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
                        Title("Pay day (optional)", "Lets “safe to spend” count down to your next salary.")
                        FieldLabel("Day of month")
                        TextInput(salaryDay, { salaryDay = it.filter(Char::isDigit).take(2) }, "10", keyboard = KeyboardType.Number)
                        FieldLabel("Usual amount (optional)")
                        AmountInput(salaryAmount, { salaryAmount = it })
                    }
                }
            }
        }
        if (step > 0) Row(Modifier.fillMaxWidth().padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
            if (step == 4) Text("Skip", style = LedgerTheme.type.bodyStrong, color = c.textMuted, modifier = Modifier.clip(Shapes.pill).clickable { salaryDay = ""; finish() }.padding(16.dp))
            Spacer(Modifier.weight(1f))
            PrimaryButton(if (step == 4) "Done" else "Continue", enabled = step != 2 || chosen.isNotEmpty()) { if (step == 4) finish() else step++ }
        }
    }
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
private fun Welcome(onStart: () -> Unit, onDemo: () -> Unit) {
    val c = LedgerTheme.colors
    Column(Modifier.fillMaxSize().padding(horizontal = Space.xxl).padding(top = 72.dp, bottom = Space.l)) {
        Box(Modifier.size(64.dp).clip(Shapes.tile).background(c.accent), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(22.dp, 32.dp, 14.dp).forEach { w -> Box(Modifier.width(w).height(5.dp).clip(CircleShape).background(c.onAccent)) }
            }
        }
        Spacer(Modifier.height(Space.xxl))
        Text("Know where your\nmoney is. Always.", style = LedgerTheme.type.display, color = c.text)
        Spacer(Modifier.height(Space.l))
        listOf(
            "Log an expense in three taps: preset, amount, save.",
            "See what's safe to spend before your next salary.",
            "Every bill, card due and installment on one timeline.",
            "Private: stored only on this phone, exportable anytime.",
        ).forEach { line ->
            Row(Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.accent))
                Spacer(Modifier.width(Space.m))
                Text(line, style = LedgerTheme.type.body, color = c.textMuted)
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Get started", Modifier.fillMaxWidth(), onClick = onStart)
        Spacer(Modifier.height(Space.m))
        Text("Explore with demo data", style = LedgerTheme.type.bodyStrong, color = c.accent,
            modifier = Modifier.fillMaxWidth().clip(Shapes.pill).clickable(onClick = onDemo).heightIn(min = 48.dp).padding(14.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
