package dev.personal.ledger.ui

import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.TxType
import java.time.LocalDate
import java.time.YearMonth

enum class Tab(val label: String) { HOME("Home"), MONEY("Money"), PLAN("Plan"), INSIGHTS("Insights") }

/** Screens pushed on top of a tab. */
sealed interface Route {
    data class Activity(val query: String = "") : Route
    data class AccountDetail(val id: Long) : Route
    data class RecurringList(val kind: RecurringKind?) : Route
    data object InstallmentList : Route
    data class InstallmentDetail(val id: Long) : Route
    data class CategoryDetail(val categoryId: Long?, val month: YearMonth) : Route
    data object Settings : Route
    data object Presets : Route
    data class EditAccount(val id: Long?) : Route
    data class EditRecurring(val id: Long?, val kind: RecurringKind = RecurringKind.BILL) : Route
    data class EditInstallment(val id: Long?) : Route
}

/** What the entry sheet is being opened for. Every capture path goes through the same surface. */
data class EntryRequest(
    val type: TxType = TxType.EXPENSE,
    val presetId: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val amount: Long = 0,
    val date: LocalDate? = null,
    val note: String = "",
    val editTxId: Long? = null,
    val recurringId: Long? = null,
    val installmentId: Long? = null,
    val cardId: Long? = null,
    val linkedTxId: Long? = null,
    /** Full composer: type switcher, category picker, command line. */
    val composer: Boolean = false,
    val command: Boolean = false,
)

sealed interface SheetRequest {
    data class Entry(val req: EntryRequest) : SheetRequest
    data class TxDetail(val id: Long) : SheetRequest
    data class Day(val date: LocalDate) : SheetRequest
    data object SafeToSpend : SheetRequest
    data class Split(val txId: Long) : SheetRequest
}

/** Actions that can arrive from outside (notification, launcher shortcut). */
sealed interface LaunchAction {
    data class PayRecurring(val id: Long) : LaunchAction
    data class PayCard(val id: Long) : LaunchAction
    data class Preset(val id: Long) : LaunchAction
    data object Command : LaunchAction
    data object Plan : LaunchAction
}
