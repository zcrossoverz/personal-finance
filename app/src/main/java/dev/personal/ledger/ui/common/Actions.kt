package dev.personal.ledger.ui.common

import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.TxType
import dev.personal.ledger.domain.ObKind
import dev.personal.ledger.domain.Obligation
import dev.personal.ledger.ui.Dashboard
import dev.personal.ledger.ui.EntryRequest

/** Tapping any upcoming item opens the same entry surface, prefilled so a fixed amount is one tap to confirm. */
fun obligationEntry(o: Obligation, d: Dashboard, defaultAccountId: Long?): EntryRequest? = when (o.kind) {
    ObKind.CARD -> EntryRequest(type = TxType.TRANSFER, accountId = defaultAccountId, toAccountId = o.cardId, amount = o.amount, cardId = o.cardId)
    ObKind.INSTALLMENT -> d.data.installments.firstOrNull { it.id == o.installmentId }?.let { p ->
        EntryRequest(type = TxType.EXPENSE, categoryId = p.categoryId, accountId = p.accountId, amount = o.amount, installmentId = p.id)
    }
    else -> d.data.recurring.firstOrNull { it.id == o.recurringId }?.let { r ->
        EntryRequest(
            type = when (r.kind) { RecurringKind.INCOME -> TxType.INCOME; RecurringKind.TRANSFER -> TxType.TRANSFER; else -> TxType.EXPENSE },
            categoryId = r.categoryId, accountId = r.accountId, toAccountId = r.toAccountId,
            amount = if (r.variable) 0 else r.amount, recurringId = r.id,
        )
    }
}
