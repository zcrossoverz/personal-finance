package dev.personal.ledger.domain

import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.TxType
import java.time.LocalDate
import java.time.YearMonth

/**
 * Credit-card statement maths. A purchase is an expense on the card; paying the bill is a transfer into the card.
 * Statement balance = debt when the last statement closed − credits since, floored at 0.
 */
object Cards {
    data class Status(
        val account: Account,
        val used: Long,
        val limit: Long?,
        val available: Long?,
        val utilization: Float?,
        /** Remaining amount of the last closed statement. */
        val statementBalance: Long,
        val statementClose: LocalDate?,
        val dueDate: LocalDate?,
        val overdue: Boolean,
        /** Charges since the last close — what the next statement will contain so far. */
        val unbilled: Long,
        val nextClose: LocalDate?,
        val nextDue: LocalDate?,
    ) {
        val paid get() = statementClose != null && statementBalance == 0L
    }

    fun lastClose(statementDay: Int, today: LocalDate): LocalDate {
        val thisMonth = YearMonth.from(today).dayClamped(statementDay)
        return if (!today.isBefore(thisMonth)) thisMonth else YearMonth.from(today).minusMonths(1).dayClamped(statementDay)
    }

    fun dueFor(close: LocalDate, statementDay: Int, dueDay: Int): LocalDate {
        val ym = YearMonth.from(close)
        return if (dueDay > statementDay) ym.dayClamped(dueDay) else ym.plusMonths(1).dayClamped(dueDay)
    }

    fun status(data: LedgerData, card: Account, today: LocalDate, balances: Map<Long, Long> = Ledger.balances(data)): Status {
        require(card.type == AccountType.CREDIT_CARD)
        val balance = balances[card.id] ?: card.openingBalance
        val used = (-balance).coerceAtLeast(0)
        val limit = card.creditLimit
        val sDay = card.statementDay
        val dDay = card.dueDay
        if (sDay == null || dDay == null) {
            return Status(card, used, limit, limit?.let { it - used }, limit?.let { if (it > 0) used.toFloat() / it else null },
                0, null, null, false, used, null, null)
        }
        val close = lastClose(sDay, today)
        val closeEnd = close.endMillis()
        val debtAtClose = (-(Ledger.balances(data, closeEnd)[card.id] ?: card.openingBalance)).coerceAtLeast(0)
        val credits = data.transactions.filter { it.date > closeEnd && isCredit(it, card.id) }.sumOf { it.amount }
        val statement = (debtAtClose - credits).coerceAtLeast(0)
        val due = dueFor(close, sDay, dDay)
        val nextClose = YearMonth.from(close).plusMonths(1).dayClamped(sDay)
        return Status(
            account = card,
            used = used,
            limit = limit,
            available = limit?.let { (it - used).coerceAtLeast(0) },
            utilization = limit?.let { if (it > 0) used.toFloat() / it else null },
            statementBalance = statement,
            statementClose = close,
            dueDate = due,
            overdue = statement > 0 && today.isAfter(due),
            unbilled = (used - statement).coerceAtLeast(0),
            nextClose = nextClose,
            nextDue = dueFor(nextClose, sDay, dDay),
        )
    }

    private fun isCredit(t: dev.personal.ledger.data.Txn, cardId: Long) = when (t.type) {
        TxType.TRANSFER -> t.toAccountId == cardId
        TxType.INCOME, TxType.REFUND, TxType.REIMBURSEMENT -> t.accountId == cardId
        TxType.ADJUSTMENT -> t.accountId == cardId && t.amount > 0
        TxType.EXPENSE -> false
    }
}
