package dev.personal.ledger.domain

import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.CategoryNature
import dev.personal.ledger.data.InstallmentStatus
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.TxType
import dev.personal.ledger.data.Txn
import java.time.LocalDate

/**
 * The single place where transaction semantics live (docs/PRODUCT.md §8). Every screen and insight goes
 * through these functions, so a rule can only be wrong in one place — and is covered by LedgerTest.
 */
object Ledger {

    /** Effect of [t] on the balance of [accountId]. */
    fun delta(t: Txn, accountId: Long): Long {
        var d = 0L
        if (t.accountId == accountId) d += when (t.type) {
            TxType.EXPENSE, TxType.TRANSFER -> -t.amount
            TxType.INCOME, TxType.REFUND, TxType.REIMBURSEMENT -> t.amount
            TxType.ADJUSTMENT -> t.amount
        }
        if (t.type == TxType.TRANSFER && t.toAccountId == accountId) d += t.amount
        return d
    }

    /** Balances as of [asOf] (inclusive, epoch millis). Credit cards are negative when in debt. */
    fun balances(data: LedgerData, asOf: Long = Long.MAX_VALUE): Map<Long, Long> {
        val out = HashMap<Long, Long>(data.accounts.size)
        data.accounts.forEach { out[it.id] = it.openingBalance }
        for (t in data.transactions) {
            if (t.date > asOf) continue
            val d1 = delta(t, t.accountId)
            out[t.accountId] = (out[t.accountId] ?: 0) + d1
            if (t.type == TxType.TRANSFER && t.toAccountId != null && t.toAccountId != t.accountId) {
                out[t.toAccountId] = (out[t.toAccountId] ?: 0) + t.amount
            }
        }
        return out
    }

    /** One categorised piece of spending. Positive = spent, negative = refunded/reimbursed. */
    data class Line(val tx: Txn, val categoryId: Long?, val amount: Long)

    /**
     * Spending lines in [from, to] (epoch millis, inclusive). Split transactions contribute their split lines;
     * refunds and reimbursements contribute negative lines in the category of what they offset. Transfers,
     * income and adjustments never appear here.
     */
    fun expenseLines(data: LedgerData, from: Long = Long.MIN_VALUE, to: Long = Long.MAX_VALUE): List<Line> {
        val out = ArrayList<Line>()
        for (t in data.transactions) {
            if (t.date < from || t.date > to) continue
            when {
                t.type == TxType.EXPENSE -> {
                    val splits = data.splitsByTx[t.id]
                    if (splits.isNullOrEmpty()) out += Line(t, t.categoryId, t.amount)
                    else splits.forEach { out += Line(t, it.categoryId, it.amount) }
                }
                t.type.offsetsExpense -> out += offsetLines(data, t)
            }
        }
        return out
    }

    private fun offsetLines(data: LedgerData, t: Txn): List<Line> {
        if (t.categoryId != null) return listOf(Line(t, t.categoryId, -t.amount))
        val original = t.linkedTxId?.let { data.txById[it] }
        val splits = original?.let { data.splitsByTx[it.id] }
        if (original != null && !splits.isNullOrEmpty()) {
            // Allocate proportionally to the original split; the last line absorbs rounding.
            var remaining = t.amount
            return splits.mapIndexed { i, s ->
                val part = if (i == splits.lastIndex) remaining else t.amount * s.amount / original.amount
                remaining -= part
                Line(t, s.categoryId, -part)
            }
        }
        return listOf(Line(t, original?.categoryId, -t.amount))
    }

    fun spending(data: LedgerData, from: Long, to: Long): Long = expenseLines(data, from, to).sumOf { it.amount }

    fun income(data: LedgerData, from: Long, to: Long): Long =
        data.transactions.filter { it.type == TxType.INCOME && it.date in from..to }.sumOf { it.amount }

    fun isFixed(data: LedgerData, categoryId: Long?): Boolean =
        data.rootOf(categoryId)?.nature == CategoryNature.FIXED ||
            data.categoryById[categoryId]?.nature == CategoryNature.FIXED

    /** Accounts that count as "cash on hand": everyday money the user can spend. */
    fun isEveryday(a: Account) = !a.archived && a.type in setOf(AccountType.CASH, AccountType.BANK, AccountType.EWALLET, AccountType.OTHER)

    data class Position(
        val cashOnHand: Long,
        val spendable: Long,
        val savings: Long,
        val cardDebt: Long,
        val installmentDebt: Long,
    ) {
        val assets get() = cashOnHand + savings
        val liabilities get() = cardDebt + installmentDebt
        val netWorth get() = assets - liabilities
    }

    fun position(data: LedgerData, asOf: Long = Long.MAX_VALUE, balances: Map<Long, Long> = balances(data, asOf)): Position {
        var cash = 0L; var spendable = 0L; var savings = 0L; var debt = 0L
        for (a in data.accounts) {
            if (a.archived) continue
            val b = balances[a.id] ?: 0
            when {
                a.type == AccountType.CREDIT_CARD -> if (b < 0) debt += -b else cash += b
                a.type == AccountType.SAVINGS -> savings += b
                else -> { cash += b; if (a.spendable) spendable += b }
            }
        }
        return Position(cash, spendable, savings, debt, installmentDebt(data, asOf))
    }

    /** Unpaid remainder of all active installment plans as of [asOf]. */
    fun installmentDebt(data: LedgerData, asOf: Long = Long.MAX_VALUE): Long =
        data.installments.filter { it.status == InstallmentStatus.ACTIVE }.sumOf { plan ->
            val existsFrom = plan.firstDue.epochDayToDate().minusMonths(1).startMillis()
            if (asOf < existsFrom) 0L else Installments.progress(data, plan, asOf).remaining
        }
}

/** Installment plan progress. Paid periods = prepaid + linked payments. */
object Installments {
    data class Progress(
        val paidPeriods: Int,
        val paidAmount: Long,
        val remaining: Long,
        val nextIndex: Int?,
        val nextDue: LocalDate?,
        val nextAmount: Long,
    )

    fun progress(data: LedgerData, plan: dev.personal.ledger.data.Installment, asOf: Long = Long.MAX_VALUE): Progress {
        val linked = data.transactions.count { it.installmentId == plan.id && it.date <= asOf }
        val paid = (plan.prepaidPeriods + linked).coerceAtMost(plan.periods)
        val paidAmount = (0 until paid).sumOf { plan.periodAmount(it) }
        val remaining = plan.totalPayable - paidAmount
        val next = if (paid < plan.periods && plan.status == InstallmentStatus.ACTIVE) paid else null
        val first = plan.firstDue.epochDayToDate()
        return Progress(
            paidPeriods = paid,
            paidAmount = paidAmount,
            remaining = remaining,
            nextIndex = next,
            nextDue = next?.let { dueOf(first, it) },
            nextAmount = next?.let { plan.periodAmount(it) } ?: 0,
        )
    }

    fun dueOf(first: LocalDate, index: Int): LocalDate =
        java.time.YearMonth.from(first).plusMonths(index.toLong()).dayClamped(first.dayOfMonth)
}
