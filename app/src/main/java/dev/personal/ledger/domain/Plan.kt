package dev.personal.ledger.domain

import dev.personal.ledger.i18n.tr
import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.Cadence
import dev.personal.ledger.data.InstallmentStatus
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Recurring
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.Settings
import java.time.LocalDate
import java.time.YearMonth

object Recurrence {
    fun next(d: LocalDate, cadence: Cadence, anchorDay: Int): LocalDate = when (cadence) {
        Cadence.WEEKLY -> d.plusWeeks(1)
        else -> YearMonth.from(d).plusMonths(cadence.months.toLong()).dayClamped(anchorDay)
    }

    fun previous(d: LocalDate, cadence: Cadence, anchorDay: Int): LocalDate = when (cadence) {
        Cadence.WEEKLY -> d.minusWeeks(1)
        else -> YearMonth.from(d).minusMonths(cadence.months.toLong()).dayClamped(anchorDay)
    }

    /** Unpaid occurrences from nextDue up to [until] inclusive (overdue ones included). */
    fun occurrences(r: Recurring, until: LocalDate, max: Int = 120): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = r.nextDue.epochDayToDate()
        while (!d.isAfter(until) && out.size < max) {
            out += d
            d = next(d, r.cadence, r.anchorDay)
        }
        return out
    }
}

enum class ObKind { BILL, SUBSCRIPTION, INSTALLMENT, CARD, INCOME, TRANSFER }

/** How sure we are about an upcoming item — drives the visual grammar (solid / light / dashed). */
enum class Certainty { CONFIRMED, EXPECTED, ESTIMATED }

data class Obligation(
    val key: String,
    val kind: ObKind,
    val title: String,
    val date: LocalDate,
    val amount: Long,
    val certainty: Certainty,
    val overdue: Boolean,
    val icon: String,
    val colorIndex: Int,
    val accountId: Long,
    /** Effect on spendable cash on [date]: negative outflow, positive inflow, 0 when it lands elsewhere (card, savings). */
    val cashDelta: Long,
    val variable: Boolean = false,
    /** True for the next unpaid occurrence, which can be acted on (pay / enter amount). */
    val actionable: Boolean = false,
    val recurringId: Long? = null,
    val installmentId: Long? = null,
    val cardId: Long? = null,
    val subtitle: String = "",
) {
    val isInflow get() = kind == ObKind.INCOME
}

object Obligations {

    private fun spendable(a: Account?) = a != null && a.spendable && Ledger.isEveryday(a)

    /** Everything scheduled between today (plus anything overdue) and [until]. Sorted by date. */
    fun upcoming(data: LedgerData, today: LocalDate, until: LocalDate, balances: Map<Long, Long> = Ledger.balances(data)): List<Obligation> {
        val out = ArrayList<Obligation>()
        // Items charged to a card, keyed by card, so they roll into that card's future statements instead of cash.
        val cardCharges = HashMap<Long, MutableList<Pair<LocalDate, Long>>>()

        for (r in data.recurring) {
            if (!r.active) continue
            val acc = data.accountById[r.accountId]
            val occ = Recurrence.occurrences(r, until)
            occ.forEachIndexed { i, d ->
                val kind = when (r.kind) {
                    RecurringKind.SUBSCRIPTION -> ObKind.SUBSCRIPTION
                    RecurringKind.BILL -> ObKind.BILL
                    RecurringKind.INCOME -> ObKind.INCOME
                    RecurringKind.TRANSFER -> ObKind.TRANSFER
                }
                val cash = when (r.kind) {
                    RecurringKind.INCOME -> if (spendable(acc)) r.amount else 0
                    RecurringKind.TRANSFER -> {
                        val to = r.toAccountId?.let { data.accountById[it] }
                        (if (spendable(acc)) -r.amount else 0) + (if (spendable(to)) r.amount else 0)
                    }
                    else -> if (spendable(acc)) -r.amount else 0
                }
                if (acc?.type == AccountType.CREDIT_CARD && r.kind != RecurringKind.INCOME) {
                    cardCharges.getOrPut(acc.id) { mutableListOf() } += d to r.amount
                }
                out += Obligation(
                    key = "r${r.id}-${d.toEpochDay()}",
                    kind = kind,
                    title = r.name,
                    date = d,
                    amount = r.amount,
                    certainty = if (r.variable) Certainty.ESTIMATED else Certainty.EXPECTED,
                    overdue = d.isBefore(today),
                    icon = r.icon,
                    colorIndex = r.colorIndex,
                    accountId = r.accountId,
                    cashDelta = cash,
                    variable = r.variable,
                    actionable = i == 0,
                    recurringId = r.id,
                    subtitle = acc?.name ?: "",
                )
            }
        }

        for (plan in data.installments) {
            if (plan.status != InstallmentStatus.ACTIVE) continue
            val p = Installments.progress(data, plan)
            val acc = data.accountById[plan.accountId]
            var idx = p.nextIndex ?: continue
            val first = plan.firstDue.epochDayToDate()
            var isFirst = true
            while (idx < plan.periods) {
                val d = Installments.dueOf(first, idx)
                if (d.isAfter(until)) break
                val amt = plan.periodAmount(idx)
                if (acc?.type == AccountType.CREDIT_CARD) cardCharges.getOrPut(acc.id) { mutableListOf() } += d to amt
                out += Obligation(
                    key = "i${plan.id}-$idx",
                    kind = ObKind.INSTALLMENT,
                    title = plan.name,
                    date = d,
                    amount = amt,
                    certainty = Certainty.CONFIRMED,
                    overdue = d.isBefore(today),
                    icon = plan.icon,
                    colorIndex = plan.colorIndex,
                    accountId = plan.accountId,
                    cashDelta = if (spendable(acc)) -amt else 0,
                    actionable = isFirst,
                    installmentId = plan.id,
                    subtitle = tr("%d of %d · %s", idx + 1, plan.periods, acc?.name ?: ""),
                )
                isFirst = false
                idx++
            }
        }

        for (card in data.accounts) {
            if (card.type != AccountType.CREDIT_CARD || card.archived) continue
            val s = Cards.status(data, card, today, balances)
            val due = s.dueDate ?: continue
            if (s.statementBalance > 0 && !due.isAfter(until)) {
                out += Obligation(
                    key = "c${card.id}-${due.toEpochDay()}",
                    kind = ObKind.CARD,
                    title = card.name,
                    date = due,
                    amount = s.statementBalance,
                    certainty = Certainty.CONFIRMED,
                    overdue = s.overdue,
                    icon = "credit_card",
                    colorIndex = card.colorIndex,
                    accountId = card.id,
                    cashDelta = -s.statementBalance,
                    actionable = true,
                    cardId = card.id,
                    subtitle = tr("Statement balance"),
                )
            }
            // Future statements: unbilled charges so far + scheduled charges before each close.
            var close = s.nextClose ?: continue
            var prevClose = s.statementClose ?: continue
            var carry = s.unbilled
            val charges = cardCharges[card.id].orEmpty()
            while (true) {
                val cycleDue = Cards.dueFor(close, card.statementDay!!, card.dueDay!!)
                if (cycleDue.isAfter(until)) break
                val scheduled = charges.filter { it.first.isAfter(prevClose) && !it.first.isAfter(close) }.sumOf { it.second }
                val amt = carry + scheduled
                if (amt > 0) out += Obligation(
                    key = "c${card.id}-${cycleDue.toEpochDay()}",
                    kind = ObKind.CARD,
                    title = card.name,
                    date = cycleDue,
                    amount = amt,
                    certainty = Certainty.ESTIMATED,
                    overdue = false,
                    icon = "credit_card",
                    colorIndex = card.colorIndex,
                    accountId = card.id,
                    cashDelta = -amt,
                    cardId = card.id,
                    subtitle = tr("Next statement · estimate"),
                )
                carry = 0
                prevClose = close
                close = YearMonth.from(close).plusMonths(1).dayClamped(card.statementDay)
            }
        }
        return out.sortedWith(compareBy<Obligation> { it.date }.thenBy { it.isInflow }.thenByDescending { it.amount })
    }

    fun nextSalary(data: LedgerData, today: LocalDate): Pair<LocalDate, Recurring>? =
        data.recurring.filter { it.active && it.isSalary }
            .map { r ->
                // An overdue salary that was not recorded still means the next pay day is the following one.
                var d = r.nextDue.epochDayToDate()
                while (d.isBefore(today)) d = Recurrence.next(d, r.cadence, r.anchorDay)
                d to r
            }
            .minByOrNull { it.first }
}

object SafeToSpendCalc {
    enum class Confidence { HIGH, MEDIUM, LOW }

    data class Result(
        val spendable: Long,
        val cardDues: Long,
        val obligations: Long,
        val protected: Long,
        val safe: Long,
        val perDay: Long,
        val daysLeft: Int,
        val horizon: LocalDate,
        val salary: Recurring?,
        val items: List<Obligation>,
        val notes: List<String>,
        val confidence: Confidence,
    )

    fun compute(data: LedgerData, settings: Settings, today: LocalDate, balances: Map<Long, Long> = Ledger.balances(data)): Result {
        val notes = ArrayList<String>()
        val salary = Obligations.nextSalary(data, today)
        val horizon = salary?.first ?: YearMonth.from(today).plusMonths(1).atDay(1).also {
            notes += tr("No pay day set — counting until the end of the month.")
        }
        salary?.second?.let { r ->
            if (r.nextDue.epochDayToDate().isBefore(today)) notes += tr("Last salary (%s) isn't recorded yet.", Fmt.dayMonth(r.nextDue.epochDayToDate()))
        }
        val position = Ledger.position(data, balances = balances)
        val items = Obligations.upcoming(data, today, horizon.minusDays(1), balances)
            .filter { it.cashDelta < 0 }
        val cardDues = items.filter { it.kind == ObKind.CARD }.sumOf { -it.cashDelta }
        val other = items.filter { it.kind != ObKind.CARD }.sumOf { -it.cashDelta }
        val protected = settings.protectedSavings + settings.emergencyReserve
        val safe = position.spendable - cardDues - other - protected
        val days = daysBetween(today, horizon).toInt().coerceAtLeast(1)

        val estimated = items.count { it.certainty == Certainty.ESTIMATED }
        if (estimated > 0) notes += tr("%d upcoming amounts are estimates (variable bills, next card statement).", estimated)
        val cardsWithoutCycle = data.accounts.count { it.type == AccountType.CREDIT_CARD && !it.archived && (it.statementDay == null || it.dueDay == null) }
        if (cardsWithoutCycle > 0) notes += tr("%d cards have no statement date, so card dues may be missing.", cardsWithoutCycle)
        val lastTx = data.transactions.maxOfOrNull { it.date }
        if (lastTx == null || daysBetween(lastTx.toLocalDate(), today) > 14) notes += tr("No transactions in the last 14 days — balances may be out of date.")

        val confidence = when {
            salary == null || lastTx == null -> Confidence.LOW
            notes.isEmpty() -> Confidence.HIGH
            else -> Confidence.MEDIUM
        }
        return Result(position.spendable, cardDues, other, protected, safe, if (safe > 0) safe / days else 0, days, horizon,
            salary?.second, items, notes, confidence)
    }
}

object ForecastCalc {
    data class Point(val date: LocalDate, val known: Long, val withEstimate: Long, val events: List<Obligation>)
    data class Result(
        val points: List<Point>,
        val dailyEstimate: Long,
        val estimateBasisDays: Int,
        val lowest: Point,
        val events: List<Obligation>,
    )

    /** Average daily flexible spending not tied to a schedule, over the last [window] days. */
    fun dailyFlexible(data: LedgerData, today: LocalDate, window: Int = 90): Pair<Long, Int> {
        val first = data.transactions.minOfOrNull { it.date }?.toLocalDate() ?: return 0L to 0
        val start = maxOf(first, today.minusDays(window.toLong()))
        val days = daysBetween(start, today).toInt()
        if (days < 21) return 0L to days
        val lines = Ledger.expenseLines(data, start.startMillis(), today.minusDays(1).endMillis())
            .filter { it.tx.recurringId == null && it.tx.installmentId == null && !Ledger.isFixed(data, it.categoryId) }
        return (lines.sumOf { it.amount }.coerceAtLeast(0) / days) to days
    }

    fun compute(data: LedgerData, settings: Settings, today: LocalDate, horizonDays: Int = 45, balances: Map<Long, Long> = Ledger.balances(data)): Result {
        val end = today.plusDays(horizonDays.toLong())
        val events = Obligations.upcoming(data, today, end, balances).filter { it.cashDelta != 0L }
        val byDay = events.groupBy { if (it.date.isBefore(today)) today else it.date }
        val (daily, basis) = if (settings.forecastIncludesEstimate) dailyFlexible(data, today) else 0L to 0
        var known = Ledger.position(data, balances = balances).spendable
        var est = known
        val points = ArrayList<Point>()
        var d = today
        while (!d.isAfter(end)) {
            val ev = byDay[d].orEmpty()
            val delta = ev.sumOf { it.cashDelta }
            known += delta
            est += delta
            if (d.isAfter(today)) est -= daily
            points += Point(d, known, est, ev)
            d = d.plusDays(1)
        }
        return Result(points, daily, basis, points.minBy { it.withEstimate }, events)
    }
}
