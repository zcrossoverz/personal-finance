package dev.personal.ledger.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.personal.ledger.LedgerApp
import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.DemoData
import dev.personal.ledger.data.Installment
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Preset
import dev.personal.ledger.data.Recurring
import dev.personal.ledger.data.Settings
import dev.personal.ledger.data.Split
import dev.personal.ledger.data.TxType
import dev.personal.ledger.data.Txn
import dev.personal.ledger.data.Undo
import dev.personal.ledger.domain.Analytics
import dev.personal.ledger.domain.Cards
import dev.personal.ledger.domain.Ledger
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.Obligation
import dev.personal.ledger.domain.Obligations
import dev.personal.ledger.domain.PresetEngine
import dev.personal.ledger.domain.SafeToSpendCalc
import dev.personal.ledger.domain.endMillis
import dev.personal.ledger.domain.startMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Everything Home and the bottom-level screens need, computed off the main thread whenever data changes. */
data class Dashboard(
    val data: LedgerData,
    val today: LocalDate,
    val balances: Map<Long, Long>,
    val position: Ledger.Position,
    val safe: SafeToSpendCalc.Result,
    val upcoming: List<Obligation>,
    val cards: List<Cards.Status>,
    val monthSpent: Long,
    val monthIncome: Long,
    val todaySpent: Long,
    val pace: Analytics.Pace?,
    val presets: List<Preset>,
    val presetLabels: Map<Long, String>,
)

data class UiMessage(val text: String, val undo: Undo? = null, val id: Long = System.nanoTime())

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as LedgerApp).repo
    val data: StateFlow<LedgerData?> = repo.data
    val settings: StateFlow<Settings> = repo.prefs.settings

    /** Re-evaluates time-dependent state (presets by time of day, "today") every minute. */
    private val clock = flow { while (true) { emit(LocalDateTime.now().withSecond(0).withNano(0)); delay(60_000) } }

    val dashboard: StateFlow<Dashboard?> = combine(data.filterNotNull(), settings, clock) { d, s, now -> compute(d, s, now) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message = _message.asStateFlow()
    fun toast(text: String, undo: Undo? = null) { _message.value = UiMessage(text, undo) }
    fun dismissMessage(id: Long) { if (_message.value?.id == id) _message.value = null }
    fun run(block: suspend () -> Unit) = viewModelScope.launch { block() }

    private fun compute(d: LedgerData, s: Settings, now: LocalDateTime): Dashboard {
        val today = now.toLocalDate()
        val balances = Ledger.balances(d)
        val month = YearMonth.from(today)
        val ordered = PresetEngine.ordered(d, now)
        return Dashboard(
            data = d,
            today = today,
            balances = balances,
            position = Ledger.position(d, balances = balances),
            safe = SafeToSpendCalc.compute(d, s, today, balances),
            upcoming = Obligations.upcoming(d, today, today.plusDays(62), balances),
            cards = d.accounts.filter { it.type == AccountType.CREDIT_CARD && !it.archived }.map { Cards.status(d, it, today, balances) },
            monthSpent = Ledger.spending(d, month.startMillis(), month.endMillis()),
            monthIncome = Ledger.income(d, month.startMillis(), month.endMillis()),
            todaySpent = Ledger.spending(d, today.startMillis(), today.endMillis()),
            pace = Analytics.pace(d, s, today),
            presets = ordered,
            presetLabels = ordered.associate { it.id to PresetEngine.labelFor(d, it, now) },
        )
    }

    // ---------- writes (all offer Undo) ----------

    fun addTransaction(t: Txn, splits: List<Split> = emptyList(), message: String) = viewModelScope.launch {
        val (_, undo) = repo.add(t, splits)
        toast(message, undo)
    }

    fun updateTransaction(t: Txn, splits: List<Split>? = null, message: String = "Saved") = viewModelScope.launch {
        toast(message, repo.update(t, splits))
    }

    fun deleteTransaction(t: Txn) = viewModelScope.launch {
        toast("Deleted ${Money.compact(t.amount)}", repo.delete(t))
    }

    fun duplicate(t: Txn) = viewModelScope.launch {
        val splits = repo.data.value?.splitsByTx?.get(t.id).orEmpty()
        val copy = t.copy(id = 0, date = System.currentTimeMillis(), createdAt = System.currentTimeMillis(), recurringId = null, installmentId = null)
        val (_, undo) = repo.add(copy, splits)
        toast("Duplicated for today", undo)
    }

    fun payRecurring(r: Recurring, amount: Long, accountId: Long, date: LocalDate) = viewModelScope.launch {
        toast("${r.name} · ${Money.compact(amount)} recorded", repo.payRecurring(r, amount, accountId, date))
    }

    fun skipRecurring(r: Recurring) = viewModelScope.launch { toast("Skipped ${r.name} this time", repo.skipRecurring(r)) }
    fun saveRecurring(r: Recurring) = viewModelScope.launch { repo.saveRecurring(r) }
    fun deleteRecurring(r: Recurring) = viewModelScope.launch { repo.deleteRecurring(r); toast("Removed ${r.name}") }

    fun payInstallment(i: Installment) = viewModelScope.launch { toast("${i.name} payment recorded", repo.payInstallment(i)) }
    fun saveInstallment(i: Installment) = viewModelScope.launch { repo.saveInstallment(i) }
    fun deleteInstallment(i: Installment) = viewModelScope.launch { repo.deleteInstallment(i); toast("Removed ${i.name}") }

    fun saveAccount(a: Account) = viewModelScope.launch { repo.saveAccount(a) }
    fun savePreset(p: Preset) = viewModelScope.launch { repo.savePreset(p) }
    fun savePresets(p: List<Preset>) = viewModelScope.launch { repo.savePresets(p) }
    fun deletePreset(p: Preset) = viewModelScope.launch { repo.deletePreset(p) }

    fun updateSettings(transform: (Settings) -> Settings) = repo.prefs.update(transform)

    fun loadDemo() = viewModelScope.launch(Dispatchers.Default) {
        repo.replaceAll(DemoData.build())
        repo.prefs.replace(DemoData.settings().copy(biometricLock = settings.value.biometricLock, theme = settings.value.theme))
    }

    fun replaceAll(d: LedgerData, s: Settings) = viewModelScope.launch(Dispatchers.Default) {
        repo.replaceAll(d)
        repo.prefs.replace(s)
        repo.autoPost()
    }

    fun autoPost() = viewModelScope.launch { repo.autoPost() }

    /** Card payment = transfer into the card. Never an expense. */
    fun payCard(card: Account, from: Long, amount: Long) = addTransaction(
        Txn(type = TxType.TRANSFER, amount = amount, accountId = from, toAccountId = card.id, date = System.currentTimeMillis(), note = "Thanh toán ${card.name}"),
        message = "Paid ${Money.compact(amount)} to ${card.name}",
    )
}
