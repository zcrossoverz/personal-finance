package dev.personal.ledger.ui.settings

import dev.personal.ledger.i18n.Lang
import dev.personal.ledger.i18n.tr
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personal.ledger.backup.Backup
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.Settings
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.epochDayToDate
import dev.personal.ledger.reminders.Reminders
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.LedgerViewModel
import dev.personal.ledger.ui.Nav
import dev.personal.ledger.ui.Route
import dev.personal.ledger.ui.common.Screen
import dev.personal.ledger.ui.components.AccountChoice
import dev.personal.ledger.ui.components.AmountInput
import dev.personal.ledger.ui.components.ChoiceRow
import dev.personal.ledger.ui.components.FieldLabel
import dev.personal.ledger.ui.components.SectionHeader
import dev.personal.ledger.ui.components.ToggleRow
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(d: Dashboard, vm: LedgerViewModel, nav: Nav) {
    val c = LedgerTheme.colors
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var pendingRestore by remember { mutableStateOf<Pair<LedgerData, Settings>?>(null) }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.run {
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.encode(vm.settings.value, d.data).toByteArray()) } }
            vm.toast(tr("Backup saved"))
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.run {
            withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(Backup.csv(d.data).toByteArray()) } }
            vm.toast(tr("CSV exported"))
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.run {
            val result = withContext(Dispatchers.IO) { runCatching { Backup.decode(ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()) } }
            result.onSuccess { f -> pendingRestore = f.data to f.settings }
                .onFailure { vm.toast(tr("Couldn't read that file: %s", it.message ?: tr("invalid backup"))) }
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(remindersEnabled = granted) }
        if (!granted) vm.toast(tr("Reminders need notification permission"))
    }
    val biometricAvailable = remember {
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val salary = d.data.recurring.firstOrNull { it.isSalary && it.active }
    val accounts = d.data.accounts.filter { !it.archived }

    Screen(tr("Settings"), onBack = { nav.pop() }) {
        item {
            SectionHeader(tr("Safe to spend"))
            NavRow(tr("Salary"), salary?.let { tr("%s · next %s", Money.compact(it.amount), Fmt.dayMonth(it.nextDue.epochDayToDate())) } ?: tr("Not set — add it so safe-to-spend knows your pay day")) {
                nav.push(Route.EditRecurring(salary?.id, RecurringKind.INCOME))
            }
            FieldLabel(tr("Protected savings (kept in everyday accounts)"))
            MoneySetting(s.protectedSavings) { v -> vm.updateSettings { it.copy(protectedSavings = v) } }
            FieldLabel(tr("Emergency reserve"))
            MoneySetting(s.emergencyReserve) { v -> vm.updateSettings { it.copy(emergencyReserve = v) } }
            FieldLabel(tr("Monthly flexible budget (optional)"))
            MoneySetting(s.flexibleBudget ?: 0, helper = tr("Blank = compare pace with your 3-month average")) { v -> vm.updateSettings { it.copy(flexibleBudget = v.takeIf { x -> x > 0 }) } }
            ToggleRow(tr("Forecast includes everyday spending"), s.forecastIncludesEstimate, tr("Shown dashed and labelled as an estimate")) { v -> vm.updateSettings { it.copy(forecastIncludesEstimate = v) } }
            NavRow(tr("Bills & recurring"), tr("%d bills", d.data.recurring.count { it.active && it.kind == RecurringKind.BILL })) { nav.push(Route.RecurringList(RecurringKind.BILL)) }
        }
        item {
            SectionHeader(tr("Capture"), Modifier.padding(top = Space.m))
            NavRow(tr("Quick presets"), tr("%d visible · pin, reorder, hide, create", d.data.presets.count { !it.hidden })) { nav.push(Route.Presets) }
            FieldLabel(tr("Default account"))
            AccountChoice(accounts, s.defaultAccountId) { id -> vm.updateSettings { it.copy(defaultAccountId = id) } }
            Spacer(Modifier.height(Space.s))
            NavRow(tr("Add account"), tr("Bank, cash, e-wallet, savings or credit card")) { nav.push(Route.EditAccount(null)) }
        }
        item {
            SectionHeader(tr("Privacy & security"), Modifier.padding(top = Space.m))
            ToggleRow(tr("App lock"), s.biometricLock, if (biometricAvailable) tr("Fingerprint, face or screen lock after 1 minute away") else tr("Set up a screen lock on this device first")) { v ->
                if (biometricAvailable) vm.updateSettings { it.copy(biometricLock = v) }
            }
            ToggleRow(tr("Hide amounts"), s.hideAmounts, tr("Show ••• instead of numbers (also the eye on Home)")) { v -> vm.updateSettings { it.copy(hideAmounts = v) } }
            ToggleRow(tr("Protect screen"), s.secureScreen, tr("Block screenshots and blur the app in recent apps")) { v -> vm.updateSettings { it.copy(secureScreen = v) } }
            ToggleRow(tr("Due-date reminders"), s.remindersEnabled && Reminders.canNotify(ctx),
                if (s.remindersEnabled && !Reminders.canNotify(ctx)) tr("Notifications are blocked — tap to allow") else tr("Variable bills and card payments, once a day")) { v ->
                if (v && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(ctx)) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else vm.updateSettings { it.copy(remindersEnabled = v) }
            }
        }
        item {
            SectionHeader(tr("Language & appearance"), Modifier.padding(top = Space.m))
            ChoiceRow(Lang.entries, Lang.of(s.language), { it.label }) { l -> vm.updateSettings { it.copy(language = l.code) } }
            Spacer(Modifier.height(Space.s))
            ChoiceRow(listOf("system", "light", "dark"), s.theme, { when (it) { "light" -> tr("Light"); "dark" -> tr("Dark"); else -> tr("System") } }) { t -> vm.updateSettings { it.copy(theme = t) } }
            Spacer(Modifier.height(Space.s))
        }
        item {
            SectionHeader(tr("Your data"), Modifier.padding(top = Space.m))
            Text(tr("Everything is stored only on this phone. Nothing is sent anywhere unless you export it."), style = LedgerTheme.type.caption, color = c.textMuted,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = 4.dp))
            NavRow(tr("Export backup"), tr("Complete JSON file — restore it on any phone")) { exportJson.launch("ledger-backup-${LocalDate.now()}.json") }
            NavRow(tr("Export CSV"), tr("Transactions for spreadsheets")) { exportCsv.launch("ledger-transactions-${LocalDate.now()}.csv") }
            NavRow(tr("Restore from backup"), tr("Replaces everything on this phone")) { restore.launch(arrayOf("application/json", "*/*")) }
            NavRow(tr("Load demo data"), tr("Replace your data with a realistic sample for exploring")) {
                confirm = tr("Replace all data on this phone with demo data? Export a backup first if you want to keep it.") to { vm.loadDemo() }
            }
            NavRow(tr("Erase everything"), tr("Start over with onboarding"), danger = true) {
                confirm = tr("Erase all accounts, transactions and settings? This cannot be undone.") to {
                    vm.replaceAll(LedgerData(), Settings(theme = s.theme, language = s.language))
                }
            }
            Spacer(Modifier.height(Space.l))
            Text(tr("Ledger 1.0 · local-first · no accounts, no tracking"), style = LedgerTheme.type.caption, color = c.textFaint, modifier = Modifier.padding(horizontal = Space.gutter))
        }
    }

    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text, style = LedgerTheme.type.body) },
            confirmButton = { TextButton({ action(); confirm = null; nav.pop() }) { Text(tr("Continue"), color = c.negative) } },
            dismissButton = { TextButton({ confirm = null }) { Text(tr("Cancel")) } },
        )
    }
    pendingRestore?.let { (data, settings) ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            text = { Text(tr("Restore %d transactions and %d accounts? Everything currently on this phone will be replaced.", data.transactions.size, data.accounts.size), style = LedgerTheme.type.body) },
            confirmButton = { TextButton({ vm.replaceAll(data, settings.copy(onboarded = true)); pendingRestore = null; vm.toast(tr("Backup restored")) }) { Text(tr("Restore"), color = c.negative) } },
            dismissButton = { TextButton({ pendingRestore = null }) { Text(tr("Cancel")) } },
        )
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = LedgerTheme.colors
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = Space.gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = LedgerTheme.type.bodyStrong, color = if (danger) c.negative else c.text)
            Text(subtitle, style = LedgerTheme.type.caption, color = c.textMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = c.textFaint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MoneySetting(value: Long, helper: String? = null, onChange: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (value > 0) Money.group(value) else "") }
    AmountInput(text, { t -> text = t; val v = if (t.isBlank()) 0L else Money.parse(t); if (v != null) onChange(v) }, helper = helper)
}
