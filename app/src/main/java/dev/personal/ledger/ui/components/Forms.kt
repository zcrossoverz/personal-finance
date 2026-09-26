package dev.personal.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.personal.ledger.data.Account
import dev.personal.ledger.data.Category
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Money
import dev.personal.ledger.i18n.tr
import androidx.compose.foundation.border
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LedgerTheme.type.label, color = LedgerTheme.colors.textMuted, modifier = modifier.padding(start = Space.gutter, end = Space.gutter, top = Space.l, bottom = 6.dp))
}

@Composable
fun TextInput(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text, helper: String? = null, error: String? = null) {
    val c = LedgerTheme.colors
    Column(modifier.padding(horizontal = Space.l)) {
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Shapes.chip).background(c.surfaceAlt).padding(horizontal = 16.dp, vertical = 15.dp),
            textStyle = LedgerTheme.type.body.copy(color = c.text), singleLine = true, cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, style = LedgerTheme.type.body, color = c.textFaint); inner() } },
        )
        if (error != null) Text(error, style = LedgerTheme.type.caption, color = c.negative, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        else if (helper != null) Text(helper, style = LedgerTheme.type.caption, color = c.textMuted, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
    }
}

/** Amount field with live grouping preview ("5000000" → "= 5,000,000 ₫"). Accepts shorthand like 5m, 260k. */
@Composable
fun AmountInput(value: String, onChange: (String) -> Unit, placeholder: String = "0", helper: String? = null) {
    val parsed = Money.parse(value)
    TextInput(value, onChange, placeholder, helper = when {
        value.isBlank() -> helper ?: tr("Type 260k, 4.5tr or a full number")
        parsed == null -> null
        else -> "= " + Money.full(parsed)
    }, error = if (value.isNotBlank() && parsed == null) tr("Not a valid amount") else null)
}

@Composable
fun ToggleRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val c = LedgerTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.heightIn(min = 60.dp).padding(horizontal = Space.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LedgerTheme.type.bodyStrong, color = c.text)
            if (subtitle != null) Text(subtitle, style = LedgerTheme.type.caption, color = c.textMuted)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent, uncheckedTrackColor = c.surfaceAlt, uncheckedBorderColor = c.hairline))
    }
}

@Composable
fun <T> ChoiceRow(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(state = selectedInView(options.indexOf(selected)), contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(options.size) { i -> val o = options[i]; Chip(label(o), o == selected) { onSelect(o) } }
    }
}

@Composable
fun AccountChoice(accounts: List<Account>, selected: Long?, onSelect: (Long) -> Unit) {
    LazyRow(state = selectedInView(accounts.indexOfFirst { it.id == selected }), contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(accounts, key = { it.id }) { a -> Chip(a.name, a.id == selected, icon = LedgerIcons.forAccount(a.type)) { onSelect(a.id) } }
    }
}

@Composable
fun CategoryChoice(categories: List<Category>, selected: Long?, onSelect: (Long) -> Unit) {
    LazyRow(state = selectedInView(categories.indexOfFirst { it.id == selected }), contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(categories, key = { it.id }) { cat -> Chip(cat.name, cat.id == selected, icon = LedgerIcons.of(cat.icon)) { onSelect(cat.id) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.padding(horizontal = Space.l)) { Chip(Fmt.full(date), icon = Icons.Rounded.CalendarToday) { open = true } }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton({ state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; open = false }) { Text(tr("OK")) } },
            dismissButton = { TextButton({ open = false }) { Text(tr("Cancel")) } },
        ) { DatePicker(state) }
    }
}

@Composable
fun IconChoice(selected: String, tint: androidx.compose.ui.graphics.Color, onSelect: (String) -> Unit) {
    val c = LedgerTheme.colors
    LazyRow(contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items(LedgerIcons.pickable) { key ->
            Box(
                Modifier.clip(Shapes.well).background(if (key == selected) c.accentSoft else c.surfaceAlt)
                    .border(1.5.dp, if (key == selected) c.accent else androidx.compose.ui.graphics.Color.Transparent, Shapes.well).clickable { onSelect(key) }.padding(10.dp),
            ) { androidx.compose.material3.Icon(LedgerIcons.of(key), key, tint = if (key == selected) tint else c.textMuted) }
        }
    }
}

@Composable
fun HueChoice(selected: Int, onSelect: (Int) -> Unit) {
    val c = LedgerTheme.colors
    Row(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        c.hues.forEachIndexed { i, h ->
            Box(Modifier.clip(Shapes.pill).background(h.copy(alpha = if (i == selected) 1f else 0.35f)).clickable { onSelect(i) }.padding(if (i == selected) 16.dp else 14.dp))
        }
    }
}

@Composable
fun FormFooter(saveLabel: String, enabled: Boolean, onSave: () -> Unit, destructive: String? = null, onDestructive: (() -> Unit)? = null) {
    val c = LedgerTheme.colors
    Spacer(Modifier.height(Space.xxl))
    PrimaryButton(saveLabel, Modifier.fillMaxWidth().padding(horizontal = Space.l), enabled = enabled, onClick = onSave)
    if (destructive != null && onDestructive != null) {
        Spacer(Modifier.height(Space.m))
        Text(destructive, style = LedgerTheme.type.bodyStrong, color = c.negative,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDestructive).padding(vertical = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Horizontal pickers open scrolled so the current choice is visible (one chip of context before it). */
@Composable
private fun selectedInView(index: Int) = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (index - 1).coerceAtLeast(0))
