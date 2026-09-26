package dev.personal.ledger.ui.settings

import dev.personal.ledger.i18n.tr
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.personal.ledger.data.Preset
import dev.personal.ledger.data.TxType
import dev.personal.ledger.domain.Money
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.common.IconAction
import dev.personal.ledger.ui.common.Screen
import dev.personal.ledger.ui.components.AccountChoice
import dev.personal.ledger.ui.components.CategoryChoice
import dev.personal.ledger.ui.components.Chip
import dev.personal.ledger.ui.components.ChoiceRow
import dev.personal.ledger.ui.components.FieldLabel
import dev.personal.ledger.ui.components.HueChoice
import dev.personal.ledger.ui.components.IconChoice
import dev.personal.ledger.ui.components.IconWell
import dev.personal.ledger.ui.components.LedgerIcons
import dev.personal.ledger.ui.components.PrimaryButton
import dev.personal.ledger.ui.components.SectionHeader
import dev.personal.ledger.ui.components.TextInput
import dev.personal.ledger.ui.components.ToggleRow
import dev.personal.ledger.ui.components.hue
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space

/**
 * Pinned presets keep this order on Home; the rest are ranked automatically by time of day and habit.
 * Editing happens inline — no extra screens.
 */
@Composable
fun PresetsScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav) {
    val c = LedgerTheme.colors
    var editing by remember { mutableStateOf<Long?>(null) }
    val pinned = d.data.presets.filter { it.pinned && !it.hidden }.sortedBy { it.sortOrder }
    val auto = d.data.presets.filter { !it.pinned && !it.hidden }.sortedBy { it.sortOrder }
    val hidden = d.data.presets.filter { it.hidden }

    fun move(p: Preset, dir: Int) {
        val list = pinned.toMutableList()
        val i = list.indexOf(p); val j = i + dir
        if (j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        vm.savePresets(list.mapIndexed { k, x -> x.copy(sortOrder = k) })
    }

    Screen(tr("Quick presets"), onBack = { nav.pop() }, actions = {
        IconAction(Icons.Rounded.Add, tr("New preset")) {
            vm.run {
                val id = vm.repo.savePreset(Preset(label = tr("New preset"), icon = "more", colorIndex = 1, categoryId = d.data.categories.firstOrNull { !it.isIncome }?.id, sortOrder = d.data.presets.size))
                editing = id
            }
        }
    }) {
        item {
            Text(tr("Pinned presets stay in place so your thumb learns them. Others adapt to the time of day and what you use most."),
                style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.s))
        }
        item { SectionHeader(tr("Pinned · fixed order")) }
        itemsIndexed(pinned, key = { _, p -> p.id }) { i, p ->
            PresetRow(d, vm, p, editing == p.id, { editing = if (editing == p.id) null else p.id }, onUp = if (i > 0) ({ move(p, -1) }) else null, onDown = if (i < pinned.lastIndex) ({ move(p, 1) }) else null)
        }
        item { SectionHeader(tr("Adaptive"), Modifier.padding(top = Space.m)) }
        itemsIndexed(auto, key = { _, p -> p.id }) { _, p -> PresetRow(d, vm, p, editing == p.id, { editing = if (editing == p.id) null else p.id }) }
        if (hidden.isNotEmpty()) {
            item { SectionHeader(tr("Hidden"), Modifier.padding(top = Space.m)) }
            itemsIndexed(hidden, key = { _, p -> p.id }) { _, p -> Column(Modifier.graphicsLayer { alpha = 0.6f }) { PresetRow(d, vm, p, editing == p.id, { editing = if (editing == p.id) null else p.id }) } }
        }
    }
}

@Composable
private fun PresetRow(d: Dashboard, vm: LedgerViewModel, p: Preset, expanded: Boolean, onToggle: () -> Unit, onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null) {
    val c = LedgerTheme.colors
    val cat = d.data.categoryById[p.categoryId]
    val acc = d.data.accountById[p.accountId]
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 60.dp).padding(start = Space.gutter, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconWell(LedgerIcons.of(p.icon), hue(p.colorIndex))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(p.label, style = LedgerTheme.type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(cat?.name, acc?.name ?: tr("last used account")).joinToString(" · "), style = LedgerTheme.type.caption, color = c.textMuted, maxLines = 1)
            }
            if (onUp != null) IconAction(Icons.Rounded.KeyboardArrowUp, tr("Move up"), c.textMuted, onUp)
            if (onDown != null) IconAction(Icons.Rounded.KeyboardArrowDown, tr("Move down"), c.textMuted, onDown)
            IconAction(if (p.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (p.pinned) tr("Unpin") else tr("Pin"), if (p.pinned) c.accent else c.textMuted) {
                vm.savePreset(p.copy(pinned = !p.pinned, sortOrder = if (!p.pinned) d.data.presets.count { it.pinned } else p.sortOrder))
            }
            IconAction(if (p.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (p.hidden) tr("Show") else tr("Hide"), c.textMuted) { vm.savePreset(p.copy(hidden = !p.hidden, pinned = false)) }
        }
        if (expanded) PresetEditor(d, vm, p, onDone = onToggle)
    }
}

@Composable
private fun PresetEditor(d: Dashboard, vm: LedgerViewModel, p: Preset, onDone: () -> Unit) {
    val c = LedgerTheme.colors
    var label by remember(p.id) { mutableStateOf(p.label) }
    var icon by remember(p.id) { mutableStateOf(p.icon) }
    var color by remember(p.id) { mutableStateOf(p.colorIndex) }
    var type by remember(p.id) { mutableStateOf(p.type) }
    var category by remember(p.id) { mutableStateOf(p.categoryId) }
    var account by remember(p.id) { mutableStateOf(p.accountId) }
    var toAccount by remember(p.id) { mutableStateOf(p.toAccountId) }
    var amounts by remember(p.id) { mutableStateOf(p.seedAmounts.split(',').mapNotNull { it.trim().toLongOrNull() }.joinToString(", ") { Money.compact(it) }) }
    var contextual by remember(p.id) { mutableStateOf(p.contextual) }
    val accounts = d.data.accounts.filter { !it.archived }
    val hasMealChildren = d.data.categories.any { it.parentId == category && (it.aliases.contains("lunch") || it.aliases.contains("dinner")) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s).clip(Shapes.card).background(c.surface).padding(bottom = Space.l)) {
        FieldLabel(tr("Label")); TextInput(label, { label = it.take(24) }, "Ăn uống")
        FieldLabel(tr("Icon")); IconChoice(icon, hue(color)) { icon = it }
        Spacer(Modifier.height(Space.s)); HueChoice(color) { color = it }
        FieldLabel(tr("Type")); ChoiceRow(listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER), type, { when (it) { TxType.INCOME -> tr("Income"); TxType.TRANSFER -> tr("Transfer"); else -> tr("Expense") } }) { type = it }
        if (type != TxType.TRANSFER) {
            FieldLabel(tr("Category"))
            CategoryChoice(d.data.categories.filter { it.isIncome == (type == TxType.INCOME) && !it.archived }, category) { category = it }
        }
        FieldLabel(if (type == TxType.TRANSFER) tr("From") else tr("Account"))
        Row(Modifier.padding(horizontal = Space.l)) { Chip(tr("Last used"), account == null) { account = null } }
        Spacer(Modifier.height(6.dp))
        AccountChoice(accounts, account) { account = it }
        if (type == TxType.TRANSFER) { FieldLabel(tr("To")); AccountChoice(accounts.filter { it.id != account }, toAccount) { toAccount = it } }
        FieldLabel(tr("Starting quick amounts")); TextInput(amounts, { amounts = it }, "35k, 50k, 100k", helper = tr("Replaced by your real habits as you use the preset"))
        if (hasMealChildren) ToggleRow(tr("Adapt to meal time"), contextual, tr("Becomes “lunch” at noon and “dinner” in the evening")) { contextual = it }
        Spacer(Modifier.height(Space.m))
        Row(Modifier.padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Delete"), style = LedgerTheme.type.bodyStrong, color = c.negative, modifier = Modifier.clip(Shapes.chip).clickable { vm.deletePreset(p); onDone() }.padding(12.dp))
            Spacer(Modifier.weight(1f))
            PrimaryButton(tr("Save"), enabled = label.isNotBlank() && (type == TxType.TRANSFER && toAccount != null || type != TxType.TRANSFER && category != null)) {
                vm.savePreset(p.copy(label = label.trim(), icon = icon, colorIndex = color, type = type, categoryId = if (type == TxType.TRANSFER) null else category,
                    accountId = account, toAccountId = if (type == TxType.TRANSFER) toAccount else null, contextual = contextual && hasMealChildren,
                    seedAmounts = amounts.split(',').mapNotNull { Money.parse(it.trim()) }.joinToString(",")))
                onDone()
            }
        }
    }
}
