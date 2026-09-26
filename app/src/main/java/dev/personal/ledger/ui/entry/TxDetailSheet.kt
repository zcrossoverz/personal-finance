package dev.personal.ledger.ui.entry

import dev.personal.ledger.i18n.tr
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.personal.ledger.data.Split
import dev.personal.ledger.data.TxType
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.toLocalDate
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.EntryRequest
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.SheetRequest
import dev.personal.ledger.ui.common.txSigned
import dev.personal.ledger.ui.common.txVisual
import dev.personal.ledger.ui.components.Amount
import dev.personal.ledger.ui.components.AmountFormat
import dev.personal.ledger.ui.components.Chip
import dev.personal.ledger.ui.components.Divider
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.PrimaryButton
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space

/** Everything about one transaction, with every action one tap away (no deep screen stack). */
@Composable
fun ColumnScope.TxDetailSheet(d: Dashboard, vm: LedgerViewModel, nav: Nav, id: Long) {
    val c = LedgerTheme.colors
    val t = d.data.txById[id] ?: run { Text(tr("This transaction no longer exists."), Modifier.padding(Space.xxl), color = c.textMuted); return }
    val v = txVisual(t, d.data)
    val splits = d.data.splitsByTx[t.id]
    val linked = t.linkedTxId?.let { d.data.txById[it] }
    val offsets = d.data.transactions.filter { it.linkedTxId == t.id }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Space.l)) {
        Row(Modifier.padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
            IconWell(v.icon, v.colorIndex?.let { hue(it) } ?: c.textMuted, size = 44.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = LedgerTheme.type.headline, color = c.text)
                Text("${Fmt.full(t.date.toLocalDate())} · ${Fmt.time(t.date)}", style = LedgerTheme.type.caption, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(Space.m))
        Amount(txSigned(t).let { if (t.type == TxType.TRANSFER) t.amount else it }, Modifier.padding(horizontal = Space.gutter), style = LedgerTheme.type.display,
            format = AmountFormat.FULL, sign = t.type != TxType.EXPENSE && t.type != TxType.TRANSFER,
            color = when (t.type) { TxType.INCOME, TxType.REFUND, TxType.REIMBURSEMENT -> c.positive; else -> c.text })
        Spacer(Modifier.height(Space.m))

        val rows = buildList {
            add(tr("Type") to when (t.type) {
                TxType.EXPENSE -> tr("Expense"); TxType.INCOME -> tr("Income"); TxType.TRANSFER -> tr("Transfer · not income or expense")
                TxType.REFUND -> tr("Refund · offsets spending"); TxType.REIMBURSEMENT -> tr("Paid back · offsets spending"); TxType.ADJUSTMENT -> tr("Adjustment")
            })
            if (t.type == TxType.TRANSFER) add(tr("From → To") to "${d.data.accountById[t.accountId]?.name} → ${d.data.accountById[t.toAccountId]?.name}")
            else add(tr("Account") to (d.data.accountById[t.accountId]?.name ?: "—"))
            if (splits == null && t.type != TxType.TRANSFER) d.data.categoryById[t.categoryId]?.let { cat ->
                add(tr("Category") to (cat.parentId?.let { p -> "${d.data.categoryById[p]?.name} › " } ?: "") + cat.name)
            }
            if (t.note.isNotBlank()) add(tr("Note") to t.note)
            t.recurringId?.let { rid -> d.data.recurring.firstOrNull { it.id == rid }?.let { add(tr("Schedule") to it.name) } }
            t.installmentId?.let { iid -> d.data.installments.firstOrNull { it.id == iid }?.let { add(tr("Installment") to it.name) } }
            linked?.let { add(tr("Offsets") to "${txVisual(it, d.data).title} · ${Money.compact(it.amount)}") }
            if (offsets.isNotEmpty()) add(tr("Offset by") to offsets.joinToString { if (it.type == TxType.REFUND) tr("%s refunded", Money.compact(it.amount)) else tr("%s paid back", Money.compact(it.amount)) })
        }
        Column(Modifier.padding(horizontal = Space.l).clip(Shapes.card).background(c.surfaceAlt)) {
            rows.forEachIndexed { i, (k, value) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 12.dp)) {
                    Text(k, style = LedgerTheme.type.label, color = c.textMuted, modifier = Modifier.width(96.dp))
                    Text(value, style = LedgerTheme.type.label, color = c.text, modifier = Modifier.weight(1f))
                }
                if (i < rows.lastIndex) Divider(inset = Space.l)
            }
        }
        if (splits != null) {
            Spacer(Modifier.height(Space.m))
            Text(tr("Split lines"), style = LedgerTheme.type.section, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.gutter, vertical = 6.dp))
            splits.forEach { s ->
                val cat = d.data.categoryById[s.categoryId]
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconWell(LedgerIcons.of(cat?.icon), hue(cat?.colorIndex ?: 0), size = 32.dp, iconSize = 18.dp)
                    Spacer(Modifier.width(Space.m))
                    Text(s.note.ifBlank { cat?.name ?: "?" }, style = LedgerTheme.type.body, color = c.text, modifier = Modifier.weight(1f))
                    Amount(s.amount, format = AmountFormat.NUMBER)
                }
            }
        }
        Spacer(Modifier.height(Space.l))

        val isExpense = t.type == TxType.EXPENSE
        val deleteLabel = tr("Delete")
        val actions = buildList {
            add(Triple(Icons.Rounded.Edit, tr("Edit")) { nav.entry(EntryRequest(editTxId = t.id)) })
            add(Triple(Icons.Rounded.ContentCopy, tr("Duplicate")) { vm.duplicate(t); nav.closeSheet() })
            if (isExpense) {
                add(Triple(Icons.AutoMirrored.Rounded.CallSplit, tr("Split")) { nav.open(SheetRequest.Split(t.id)) })
                add(Triple(Icons.AutoMirrored.Rounded.Undo, tr("Refund")) {
                    nav.entry(EntryRequest(type = TxType.REFUND, categoryId = t.categoryId, accountId = t.accountId, amount = t.amount - offsets.sumOf { it.amount }, linkedTxId = t.id, note = tr("Refund · %s", v.title)))
                })
                add(Triple(Icons.Rounded.Groups, tr("Paid back")) {
                    nav.entry(EntryRequest(type = TxType.REIMBURSEMENT, categoryId = t.categoryId, accountId = d.data.accounts.firstOrNull { it.type == dev.personal.ledger.data.AccountType.BANK }?.id ?: t.accountId,
                        amount = 0, linkedTxId = t.id, note = tr("Paid back · %s", v.title)))
                })
            }
            add(Triple(Icons.Rounded.DeleteOutline, deleteLabel) { vm.deleteTransaction(t); nav.closeSheet() })
        }
        actions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { (icon, label, action) -> ActionTile(icon, label, if (label == deleteLabel) c.negative else c.text, Modifier.weight(1f), action) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(Space.s))
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Column(
        modifier.heightIn(min = 72.dp).clip(Shapes.tile).background(c.surfaceAlt).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = LedgerTheme.type.label, color = tint)
    }
}

/** Split one payment across categories. Lines must add up to the total before Save is enabled. */
@Composable
fun ColumnScope.SplitSheet(d: Dashboard, vm: LedgerViewModel, nav: Nav, txId: Long) {
    val c = LedgerTheme.colors
    val t = d.data.txById[txId] ?: return
    data class Line(val categoryId: Long?, val amount: String, val note: String)
    val lines = remember {
        mutableStateListOf<Line>().apply {
            val existing = d.data.splitsByTx[txId]
            if (existing != null) existing.forEach { add(Line(it.categoryId, it.amount.toString(), it.note)) }
            else { add(Line(t.categoryId, t.amount.toString(), "")); add(Line(null, "", "")) }
        }
    }
    var picking by remember { mutableStateOf<Int?>(null) }
    val assigned = lines.sumOf { it.amount.toLongOrNull() ?: 0 }
    val remaining = t.amount - assigned
    val valid = remaining == 0L && lines.size >= 2 && lines.all { it.categoryId != null && (it.amount.toLongOrNull() ?: 0) > 0 }
    val cats = remember { d.data.categories.filter { !it.archived && !it.isIncome } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Space.l)) {
        Row(Modifier.padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Split transaction"), style = LedgerTheme.type.headline, color = c.text)
                Text(tr("%s · analytics use these lines", Money.full(t.amount)), style = LedgerTheme.type.caption, color = c.textMuted)
            }
        }
        Spacer(Modifier.height(Space.m))
        lines.forEachIndexed { i, line ->
            val cat = line.categoryId?.let { d.data.categoryById[it] }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(cat?.name ?: tr("Category"), selected = picking == i, icon = LedgerIcons.of(cat?.icon), modifier = Modifier.weight(1f)) { picking = if (picking == i) null else i }
                Spacer(Modifier.width(Space.s))
                BasicTextField(
                    line.amount, { v -> lines[i] = line.copy(amount = v.filter { it.isDigit() }.take(12)) },
                    Modifier.width(130.dp).heightIn(min = 44.dp).clip(Shapes.chip).background(c.surfaceAlt).padding(horizontal = 12.dp, vertical = 12.dp),
                    textStyle = LedgerTheme.type.bodyStrong.copy(color = c.text, textAlign = TextAlign.End), singleLine = true, cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (lines.size > 2) Box(Modifier.size(40.dp).clip(Shapes.chip).clickable { lines.removeAt(i); picking = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, tr("Remove line"), tint = c.textMuted, modifier = Modifier.size(18.dp))
                }
            }
            if (picking == i) {
                LazyRow(contentPadding = PaddingValues(horizontal = Space.l, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    items(cats, key = { it.id }) { cc -> Chip(cc.name, cc.id == line.categoryId, icon = LedgerIcons.of(cc.icon)) { lines[i] = line.copy(categoryId = cc.id); picking = null } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Chip(tr("Add line"), icon = Icons.Rounded.Add) { lines.add(Line(null, remaining.coerceAtLeast(0).takeIf { it > 0 }?.toString() ?: "", "")) }
            Spacer(Modifier.weight(1f))
            Text(
                if (remaining == 0L) tr("Fully assigned") else if (remaining > 0) tr("%s unassigned", Money.compact(remaining)) else tr("%s over", Money.compact(-remaining)),
                style = LedgerTheme.type.label, color = if (remaining == 0L) c.positive else c.caution,
            )
        }
        Spacer(Modifier.height(Space.m))
        Row(Modifier.padding(horizontal = Space.l)) {
            if (d.data.splitsByTx[txId] != null) {
                Chip(tr("Remove split")) {
                    // The largest line's category becomes the transaction's category again.
                    val main = d.data.splitsByTx[txId]?.maxByOrNull { it.amount }?.categoryId
                    vm.updateTransaction(t.copy(categoryId = main), emptyList(), tr("Split removed")); nav.closeSheet()
                }
                Spacer(Modifier.width(Space.s))
            }
            PrimaryButton(tr("Save split"), Modifier.weight(1f), enabled = valid) {
                vm.updateTransaction(t.copy(categoryId = null), lines.map { Split(txId = txId, categoryId = it.categoryId!!, amount = it.amount.toLong(), note = it.note) }, tr("Split saved"))
                nav.closeSheet()
            }
        }
    }
}
