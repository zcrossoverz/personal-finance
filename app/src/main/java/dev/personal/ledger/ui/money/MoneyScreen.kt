package dev.personal.ledger.ui.money

import androidx.compose.foundation.border
import dev.personal.ledger.ui.common.TabTitle
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.domain.Analytics
import dev.personal.ledger.domain.Cards
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Money
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.EntryRequest
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.Route
import dev.personal.ledger.data.TxType
import dev.personal.ledger.ui.charts.LineChart
import dev.personal.ledger.ui.components.Amount
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.AnimatedAmount
import dev.personal.ledger.ui.components.Bar
import dev.personal.ledger.ui.components.Chip
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerCard
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.ListRow
import dev.personal.ledger.ui.components.Metric
import dev.personal.ledger.ui.components.SectionHeader
import dev.personal.ledger.ui.components.Tag
import dev.personal.ledger.ui.components.Tone
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import java.time.temporal.ChronoUnit

/** "Where is my money?" — net worth (assets − liabilities), then every account as a real object. */
@Composable
fun MoneyScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav) {
    val c = LedgerTheme.colors
    val accounts = d.data.accounts.filter { !it.archived }
    val everyday = accounts.filter { it.type in setOf(AccountType.CASH, AccountType.BANK, AccountType.EWALLET, AccountType.OTHER) }
    val savings = accounts.filter { it.type == AccountType.SAVINGS }
    val history = remember(d.data) { Analytics.netWorthHistory(d.data, d.today, 12) }
    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = PaddingValues(bottom = 150.dp)) {
        item(key = "title") {
            TabTitle(tr("Money")) {
                Chip(tr("Activity"), icon = Icons.AutoMirrored.Rounded.ReceiptLong) { nav.push(Route.Activity()) }
                Spacer(Modifier.width(Space.m))
            }
        }
        item(key = "nw") { NetWorthCard(d, history) }
        item(key = "dist") { Distribution(d, accounts) }
        if (d.cards.isNotEmpty()) {
            item(key = "cards-h") { SectionHeader(tr("Credit cards"), Modifier.padding(top = Space.m)) }
            items(d.cards, key = { "card-${it.account.id}" }) { s -> CreditCard(s, d, onPay = {
                nav.entry(EntryRequest(type = TxType.TRANSFER, toAccountId = s.account.id, amount = if (s.statementBalance > 0) s.statementBalance else s.used, cardId = s.account.id))
            }) { nav.push(Route.AccountDetail(s.account.id)) } }
        }
        item(key = "every-h") { SectionHeader(tr("Everyday"), Modifier.padding(top = Space.m), trailing = tr("Add account")) { nav.push(Route.EditAccount(null)) } }
        items(everyday, key = { "a-${it.id}" }) { a -> AccountRow(a, d.balances[a.id] ?: 0) { nav.push(Route.AccountDetail(a.id)) } }
        if (savings.isNotEmpty()) {
            item(key = "sav-h") { SectionHeader(tr("Savings"), Modifier.padding(top = Space.s)) }
            items(savings, key = { "s-${it.id}" }) { a -> AccountRow(a, d.balances[a.id] ?: 0) { nav.push(Route.AccountDetail(a.id)) } }
        }
        if (d.position.installmentDebt > 0) {
            item(key = "inst") {
                SectionHeader(tr("Other liabilities"), Modifier.padding(top = Space.s))
                ListRow(tr("Installment plans"), subtitle = tr("Unpaid remainder · counted in net worth"), leading = { IconWell(Icons.Rounded.CreditScore, c.negative) }, onClick = { nav.push(Route.InstallmentList) }) {
                    Amount(-d.position.installmentDebt, color = c.negative)
                    Icon(Icons.Rounded.ChevronRight, null, tint = c.textFaint)
                }
            }
        }
    }
}

@Composable
private fun NetWorthCard(d: Dashboard, history: List<Analytics.NetWorthPoint>) {
    val c = LedgerTheme.colors
    var sel by remember { mutableStateOf<Int?>(null) }
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        val point = sel?.let { history.getOrNull(it) }
        Text(if (point != null) tr("Net worth · end of %s", Fmt.monthYear(point.month)) else tr("Net worth"), style = LedgerTheme.type.label, color = c.textMuted)
        AnimatedAmount(point?.net ?: d.position.netWorth, style = LedgerTheme.type.display, color = if ((point?.net ?: d.position.netWorth) < 0) c.negative else c.text)
        Spacer(Modifier.height(Space.s))
        Row {
            Metric(tr("Assets"), point?.assets ?: d.position.assets, Modifier.weight(1f), style = LedgerTheme.type.bodyStrong)
            Metric(tr("Liabilities"), -(point?.liabilities ?: d.position.liabilities), Modifier.weight(1f), color = c.negative, style = LedgerTheme.type.bodyStrong)
            if (history.size >= 2 && point == null) {
                val change = history.last().net - history[history.size - 2].net
                Metric(tr("This month"), change, Modifier.weight(1f), color = if (change >= 0) c.positive else c.negative, sign = true, style = LedgerTheme.type.bodyStrong)
            }
        }
        if (history.size >= 3) {
            Spacer(Modifier.height(Space.m))
            LineChart(history.map { it.net }, history.map { Fmt.monthShort(it.month) }, sel, { sel = it }, height = 120.dp)
        } else {
            Spacer(Modifier.height(Space.s))
            Text(tr("A net-worth timeline appears after a few months of history."), style = LedgerTheme.type.caption, color = c.textMuted)
        }
        Spacer(Modifier.height(4.dp))
        Text(tr("Net worth = assets − card debt − unpaid installments. Separate from monthly cash flow."), style = LedgerTheme.type.caption, color = c.textFaint)
    }
}

/** Where money lives: one bar per account on a shared scale; debts drawn in the negative colour. */
@Composable
private fun Distribution(d: Dashboard, accounts: List<Account>) {
    val c = LedgerTheme.colors
    val rows = accounts.map { it to (d.balances[it.id] ?: 0L) }.filter { it.second != 0L }.sortedByDescending { it.second }
    val max = rows.maxOfOrNull { kotlin.math.abs(it.second) }?.coerceAtLeast(1) ?: 1
    if (rows.isEmpty()) return
    LedgerCard(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
        Text(tr("Where your money is"), style = LedgerTheme.type.label, color = c.textMuted)
        Spacer(Modifier.height(Space.s))
        rows.forEach { (a, b) ->
            val debt = b < 0
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, style = LedgerTheme.type.label, color = c.text, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).padding(horizontal = Space.s)) {
                    Bar(kotlin.math.abs(b).toFloat() / max, color = if (debt) c.negative else c.accent, track = androidx.compose.ui.graphics.Color.Transparent, height = 6.dp)
                }
                Amount(b, style = LedgerTheme.type.label, color = if (debt) c.negative else c.text, modifier = Modifier.width(64.dp))
            }
        }
    }
}

@Composable
private fun AccountRow(a: Account, balance: Long, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    ListRow(
        a.name,
        subtitle = when (a.type) { AccountType.CASH -> tr("Cash"); AccountType.BANK -> tr("Bank"); AccountType.EWALLET -> tr("E-wallet"); AccountType.SAVINGS -> tr("Savings · not in safe-to-spend"); else -> tr("Account") } +
            if (!a.spendable && a.type != AccountType.SAVINGS) " · " + tr("excluded") else "",
        leading = { IconWell(LedgerIcons.forAccount(a.type), hue(a.colorIndex)) },
        onClick = onClick,
    ) {
        Amount(balance, format = AmountFormat.NUMBER, color = if (balance < 0) c.negative else c.text)
    }
}

/**
 * A credit card rendered as the object it is: a dark card face with usage against the limit, then the
 * statement, the due date (the most important fact, promoted when close) and available credit.
 */
@Composable
fun CreditCard(s: Cards.Status, d: Dashboard, onPay: () -> Unit, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    val on = c.onCardFace
    val days = s.dueDate?.let { ChronoUnit.DAYS.between(d.today, it) }
    val dueSoon = s.statementBalance > 0 && days != null && days <= 5
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 6.dp).clip(Shapes.card)
            .background(c.cardFace)
            .then(if (c.isDark) Modifier.border(1.dp, c.hairline, Shapes.card) else Modifier)
            .clickable(onClick = onClick).padding(Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.account.name, style = LedgerTheme.type.bodyStrong, color = on, modifier = Modifier.weight(1f))
            Text(tr("Credit"), style = LedgerTheme.type.caption, color = on.copy(alpha = 0.55f))
        }
        Spacer(Modifier.height(Space.l))
        Text(tr("Used"), style = LedgerTheme.type.caption, color = on.copy(alpha = 0.6f))
        Row(verticalAlignment = Alignment.Bottom) {
            Amount(s.used, style = LedgerTheme.type.display, color = on, format = AmountFormat.FULL)
            if (s.limit != null) Text(" / " + Money.compact(s.limit), style = LedgerTheme.type.bodyStrong, color = on.copy(alpha = 0.55f), modifier = Modifier.padding(bottom = 4.dp))
        }
        if (s.utilization != null) {
            Spacer(Modifier.height(Space.s))
            Bar(s.utilization, color = if (s.utilization > 0.7f) c.negative else on, track = on.copy(alpha = 0.14f), height = 3.dp)
        }
        Spacer(Modifier.height(Space.l))
        Row(verticalAlignment = Alignment.Bottom) {
            CardFact(tr("Statement"), Money.compact(s.statementBalance), on, Modifier.weight(1f))
            CardFact(tr("Due"), s.dueDate?.let { Fmt.dayMonth(it) } ?: "—", if (s.overdue) c.negative else if (dueSoon) c.caution else on, Modifier.weight(1f))
            CardFact(tr("Available"), Money.compact(s.available ?: 0), on, Modifier.weight(1f))
            if (s.used > 0) Box(
                Modifier.clip(Shapes.button).background(on).clickable(onClick = onPay).padding(horizontal = 16.dp, vertical = 9.dp),
            ) { Text(tr("Pay"), style = LedgerTheme.type.label, color = c.cardFace) }
        }
        val status = when {
            s.overdue -> tr("Payment overdue")
            dueSoon -> tr("Payment %s", Fmt.dueIn(s.dueDate!!, d.today))
            s.paid -> tr("Statement paid")
            else -> null
        }
        val unbilled = if (s.unbilled > 0 && s.nextClose != null) tr("%s unbilled · next statement %s", Money.compact(s.unbilled), Fmt.dayMonth(s.nextClose)) else null
        listOfNotNull(status, unbilled).takeIf { it.isNotEmpty() }?.let {
            Spacer(Modifier.height(Space.m))
            Text(it.joinToString(" · "), style = LedgerTheme.type.caption, color = if (s.overdue) c.negative else if (dueSoon) c.caution else on.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun CardFact(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = LedgerTheme.type.caption, color = LedgerTheme.colors.onCardFace.copy(alpha = 0.6f))
        Text(value, style = LedgerTheme.type.bodyStrong, color = color)
    }
}
