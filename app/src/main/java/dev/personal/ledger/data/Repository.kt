package dev.personal.ledger.data

import dev.personal.ledger.domain.Installments
import dev.personal.ledger.domain.Recurrence
import dev.personal.ledger.domain.atMillis
import dev.personal.ledger.domain.epochDayToDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime

/** Reversal of a write, returned by operations that offer Undo. */
typealias Undo = suspend () -> Unit

class Repository(private val dao: LedgerDao, val prefs: Prefs, scope: CoroutineScope) {

    /** The whole ledger, kept in memory and updated whenever any table changes. */
    val data: StateFlow<LedgerData?> = combine(
        combine(dao.accounts(), dao.categories(), dao.transactions(), dao.splits()) { a, c, t, s -> arrayOf<Any>(a, c, t, s) },
        combine(dao.presets(), dao.recurring(), dao.priceChanges(), dao.installments()) { p, r, pc, i -> arrayOf<Any>(p, r, pc, i) },
    ) { x, y ->
        @Suppress("UNCHECKED_CAST")
        LedgerData(
            accounts = x[0] as List<Account>, categories = x[1] as List<Category>,
            transactions = x[2] as List<Txn>, splits = x[3] as List<Split>,
            presets = y[0] as List<Preset>, recurring = y[1] as List<Recurring>,
            priceChanges = y[2] as List<PriceChange>, installments = y[3] as List<Installment>,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    // ---------- transactions ----------

    suspend fun save(t: Txn, splits: List<Split> = emptyList()): Long {
        val id = dao.upsert(t)
        val txId = if (t.id == 0L) id else t.id
        dao.replaceSplits(txId, splits)
        return txId
    }

    /** Insert a new transaction and return an Undo that removes it. */
    suspend fun add(t: Txn, splits: List<Split> = emptyList()): Pair<Long, Undo> {
        val id = save(t.copy(id = 0), splits)
        return id to { dao.deleteSplits(id); dao.delete(t.copy(id = id)) }
    }

    suspend fun update(t: Txn, splits: List<Split>? = null): Undo {
        val before = currentTx(t.id)
        val beforeSplits = dao.splitsOf(t.id)
        dao.upsert(t)
        if (splits != null) dao.replaceSplits(t.id, splits)
        return { if (before != null) { dao.upsert(before); dao.replaceSplits(before.id, beforeSplits) } }
    }

    suspend fun delete(t: Txn): Undo {
        val splits = dao.splitsOf(t.id)
        dao.deleteSplits(t.id)
        dao.delete(t)
        // Deleting the latest payment of a schedule re-opens that occurrence.
        val recurringBefore = t.recurringId?.let { id -> dao.recurringNow().firstOrNull { it.id == id } }
        val isLatest = recurringBefore != null &&
            data.value?.transactions?.filter { it.recurringId == t.recurringId }?.maxByOrNull { it.date }?.id == t.id
        if (recurringBefore != null && isLatest) {
            dao.upsert(recurringBefore.copy(nextDue = Recurrence.previous(recurringBefore.nextDue.epochDayToDate(), recurringBefore.cadence, recurringBefore.anchorDay).toEpochDay()))
        }
        return {
            dao.upsert(t)
            dao.replaceSplits(t.id, splits)
            recurringBefore?.let { dao.upsert(it) }
        }
    }

    private fun currentTx(id: Long) = data.value?.txById?.get(id)

    // ---------- schedules ----------

    /** Record one occurrence of [r] and advance its schedule. */
    suspend fun payRecurring(r: Recurring, amount: Long, accountId: Long = r.accountId, date: LocalDate = LocalDate.now(), time: LocalTime = LocalTime.now()): Undo {
        val type = when (r.kind) {
            RecurringKind.INCOME -> TxType.INCOME
            RecurringKind.TRANSFER -> TxType.TRANSFER
            else -> TxType.EXPENSE
        }
        val (id, undoTx) = add(Txn(type = type, amount = amount, accountId = accountId, toAccountId = r.toAccountId,
            categoryId = r.categoryId, date = date.atMillis(time), note = r.name, recurringId = r.id))
        val next = Recurrence.next(r.nextDue.epochDayToDate(), r.cadence, r.anchorDay)
        // Variable bills learn their latest amount so the forecast estimate stays realistic.
        dao.upsert(r.copy(nextDue = next.toEpochDay(), amount = if (r.variable) amount else r.amount))
        return { undoTx(); dao.upsert(r) }
    }

    suspend fun skipRecurring(r: Recurring): Undo {
        dao.upsert(r.copy(nextDue = Recurrence.next(r.nextDue.epochDayToDate(), r.cadence, r.anchorDay).toEpochDay()))
        return { dao.upsert(r) }
    }

    suspend fun saveRecurring(r: Recurring): Long {
        val before = dao.recurringNow().firstOrNull { it.id == r.id }
        val id = dao.upsert(r).let { if (r.id == 0L) it else r.id }
        if (before == null || before.amount != r.amount) {
            dao.upsert(PriceChange(recurringId = id, amount = r.amount, effectiveDay = LocalDate.now().toEpochDay()))
        }
        return id
    }

    suspend fun deleteRecurring(r: Recurring) = dao.delete(r)

    suspend fun payInstallment(plan: Installment, date: LocalDate = LocalDate.now()): Undo {
        val d = data.value ?: return {}
        val p = Installments.progress(d, plan)
        val idx = p.nextIndex ?: return {}
        val (_, undo) = add(Txn(type = TxType.EXPENSE, amount = plan.periodAmount(idx), accountId = plan.accountId,
            categoryId = plan.categoryId, date = date.atMillis(LocalTime.now()), note = "${plan.name} · ${idx + 1}/${plan.periods}", installmentId = plan.id))
        if (idx + 1 >= plan.periods) dao.upsert(plan.copy(status = InstallmentStatus.COMPLETED))
        return { undo(); dao.upsert(plan) }
    }

    suspend fun saveInstallment(i: Installment): Long = dao.upsert(i)
    suspend fun deleteInstallment(i: Installment) = dao.delete(i)

    /**
     * Posts every due occurrence of auto-pay schedules up to [today]. Fixed-amount auto-pay only: variable bills
     * always wait for the user to enter the amount. Idempotent — each post advances the schedule.
     */
    suspend fun autoPost(today: LocalDate = LocalDate.now()): Int {
        var posted = 0
        for (r in dao.recurringNow()) {
            if (!r.active || !r.autoPay || r.variable) continue
            var cur = r
            var guard = 0
            while (cur.nextDue <= today.toEpochDay() && guard++ < 36) {
                val due = cur.nextDue.epochDayToDate()
                val type = when (cur.kind) {
                    RecurringKind.INCOME -> TxType.INCOME
                    RecurringKind.TRANSFER -> TxType.TRANSFER
                    else -> TxType.EXPENSE
                }
                dao.upsert(Txn(type = type, amount = cur.amount, accountId = cur.accountId, toAccountId = cur.toAccountId,
                    categoryId = cur.categoryId, date = due.atMillis(LocalTime.of(8, 0)), note = cur.name, recurringId = cur.id))
                cur = cur.copy(nextDue = Recurrence.next(due, cur.cadence, cur.anchorDay).toEpochDay())
                posted++
            }
            if (cur != r) dao.upsert(cur)
        }
        for (plan in dao.installmentsNow()) {
            if (plan.status != InstallmentStatus.ACTIVE || !plan.autoPay) continue
            var paid = plan.prepaidPeriods + dao.installmentPayments(plan.id)
            val first = plan.firstDue.epochDayToDate()
            while (paid < plan.periods && !Installments.dueOf(first, paid).isAfter(today)) {
                dao.upsert(Txn(type = TxType.EXPENSE, amount = plan.periodAmount(paid), accountId = plan.accountId,
                    categoryId = plan.categoryId, date = Installments.dueOf(first, paid).atMillis(LocalTime.of(8, 0)),
                    note = "${plan.name} · ${paid + 1}/${plan.periods}", installmentId = plan.id))
                paid++; posted++
            }
            if (paid >= plan.periods) dao.upsert(plan.copy(status = InstallmentStatus.COMPLETED))
        }
        return posted
    }

    // ---------- setup objects ----------

    suspend fun saveAccount(a: Account): Long = dao.upsert(a).let { if (a.id == 0L) it else a.id }
    suspend fun saveCategory(c: Category): Long = dao.upsert(c).let { if (c.id == 0L) it else c.id }
    suspend fun savePreset(p: Preset): Long = dao.upsert(p).let { if (p.id == 0L) it else p.id }
    suspend fun savePresets(p: List<Preset>) = dao.upsertPresets(p)
    suspend fun deletePreset(p: Preset) = dao.delete(p)
    suspend fun hasAccounts() = dao.accountCount() > 0

    suspend fun replaceAll(d: LedgerData) = dao.replaceAll(d)
}
