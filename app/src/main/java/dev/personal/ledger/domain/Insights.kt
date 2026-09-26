package dev.personal.ledger.domain

import dev.personal.ledger.data.InstallmentStatus
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.Settings
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/** Deterministic, explainable insights. Every sentence says whether it is a fact, an estimate or a forecast. */
object InsightsCalc {
    enum class Kind(val label: String) { FACT("Fact"), ESTIMATE("Estimate"), FORECAST("Forecast") }
    enum class Tone { NEUTRAL, GOOD, WARN }

    data class Insight(val kind: Kind, val tone: Tone, val text: String, val detail: String, val categoryId: Long? = null, val weight: Int)

    fun compute(data: LedgerData, settings: Settings, today: LocalDate, month: Analytics.Month): List<Insight> {
        val out = ArrayList<Insight>()
        val current = YearMonth.from(today) == month.month
        val window = if (month.isPartial) "by day ${month.throughDay}" else "for the month"

        // 1. Category vs its 3-month baseline over the same day window.
        // Only differences that matter in money, not just in percent: ≥ 15 % and ≥ 500k, ranked by the absolute gap.
        month.categories.filter { it.category != null && it.baseline >= 300_000 }
            .mapNotNull { c -> c.deltaPct?.let { c to it } }
            .filter { abs(it.second) >= 0.15f && abs(it.first.amount - it.first.baseline) >= 500_000 }
            .sortedByDescending { abs(it.first.amount - it.first.baseline) }
            .take(2)
            .forEach { (c, d) ->
                val pct = (abs(d) * 100).roundToInt()
                val dir = if (d > 0) "higher" else "lower"
                out += Insight(Kind.FACT, if (d > 0) Tone.WARN else Tone.GOOD,
                    "${c.category!!.name} spending is $pct% $dir than your 3-month average.",
                    "${Money.compact(c.amount)} vs ${Money.compact(c.baseline)} $window.", c.category.id, 90 + pct / 5)
            }

        // 2. Weekend vs weekday intensity.
        if (month.weekdayPerDay > 0 && month.weekendPerDay > 0 && month.throughDay >= 10) {
            val ratio = month.weekendPerDay.toDouble() / month.weekdayPerDay
            if (ratio >= 1.4 || ratio <= 0.6) {
                val txt = if (ratio >= 1) "You spend %.1fx more per day on weekends than weekdays.".format(java.util.Locale.US, ratio)
                else "Weekdays cost you %.1fx more per day than weekends.".format(java.util.Locale.US, 1 / ratio)
                out += Insight(Kind.FACT, Tone.NEUTRAL, txt,
                    "Weekend ${Money.compact(month.weekendPerDay)}/day · weekday ${Money.compact(month.weekdayPerDay)}/day.", weight = 60)
            }
        }

        // 3. Category growing 3 full months in a row.
        val full = (1..4).map { YearMonth.from(today).minusMonths(it.toLong()) }.reversed()
        val perMonth = full.map { m -> Ledger.expenseLines(data, m.startMillis(), m.endMillis()).groupBy { data.rootOf(it.categoryId)?.id }.mapValues { e -> e.value.sumOf { it.amount } } }
        data.categories.filter { it.parentId == null && !it.isIncome }.forEach { c ->
            val v = perMonth.map { it[c.id] ?: 0 }
            if (v.all { it > 100_000 } && v[1] > v[0] * 1.05 && v[2] > v[1] * 1.05 && v[3] > v[2] * 1.05) {
                out += Insight(Kind.FACT, Tone.WARN, "${c.name} has increased for 3 consecutive months.",
                    full.zip(v).joinToString(" → ") { "${Fmt.monthShort(it.first)} ${Money.compact(it.second)}" }, c.id, 80)
            }
        }

        // 4. Subscriptions.
        val subs = data.recurring.filter { it.active && it.kind == RecurringKind.SUBSCRIPTION }
        if (subs.isNotEmpty()) {
            val monthly = subs.sumOf { it.cadence.monthly(it.amount) }
            out += Insight(Kind.FACT, Tone.NEUTRAL, "Subscriptions cost ${Money.compact(monthly)}/month.",
                "${subs.size} active · ${Money.compact(monthly * 12)} a year.", weight = 40)
        }

        // 5. Installment burden vs expected income.
        val plans = data.installments.filter { it.status == InstallmentStatus.ACTIVE }
        val monthlyIncome = expectedMonthlyIncome(data, today)
        if (plans.isNotEmpty() && monthlyIncome > 0) {
            val burden = plans.sumOf { p -> Installments.progress(data, p).let { if (it.nextIndex != null) it.nextAmount else 0 } }
            val pct = (burden * 100.0 / monthlyIncome).roundToInt()
            out += Insight(Kind.ESTIMATE, if (pct >= 20) Tone.WARN else Tone.NEUTRAL,
                "Installments take $pct% of your expected monthly income.",
                "${Money.compact(burden)}/month across ${plans.size} plan${if (plans.size > 1) "s" else ""} · income ≈ ${Money.compact(monthlyIncome)}.", weight = 55)
        }

        // 6. Pace projection (current month only).
        if (current) Analytics.pace(data, settings, today)?.let { p ->
            if (p.day >= 5) {
                val tone = if (p.projected > p.reference * 1.05) Tone.WARN else Tone.GOOD
                out += Insight(Kind.FORECAST, tone,
                    "At the current pace, flexible spending may reach ${Money.compact(p.projected)} this month.",
                    "${Money.compact(p.spent)} in ${p.day} days · ${if (p.referenceIsBudget) "budget" else "usual"} ${Money.compact(p.reference)}.", weight = 85)
            }
        }

        // 7. Largest upcoming outflow.
        if (current) {
            val next = Obligations.upcoming(data, today, today.plusDays(30)).filter { !it.isInflow && !it.overdue }.maxByOrNull { it.amount }
            if (next != null) out += Insight(Kind.FORECAST, Tone.NEUTRAL,
                "Your largest upcoming outflow is ${Money.compact(next.amount)} on ${Fmt.monthDay(next.date)}.",
                "${next.title}${if (next.certainty == Certainty.ESTIMATED) " · estimated" else ""}.", weight = 50)
        }

        // 8. Month saved rate (closed months, or current with income).
        if (month.income > 0 && !month.isPartial) {
            val rate = (month.net * 100.0 / month.income).roundToInt()
            out += Insight(Kind.FACT, if (rate >= 20) Tone.GOOD else if (rate < 0) Tone.WARN else Tone.NEUTRAL,
                if (rate >= 0) "You kept $rate% of your income in ${Fmt.month(month.month)}." else "You spent ${-rate}% more than you earned in ${Fmt.month(month.month)}.",
                "Income ${Money.compact(month.income)} · spending ${Money.compact(month.spending)}.", weight = 70)
        }
        return out.sortedByDescending { it.weight }
    }

    /** Salary schedule if present, otherwise the 3-month average of recorded income. */
    fun expectedMonthlyIncome(data: LedgerData, today: LocalDate): Long {
        val salary = data.recurring.filter { it.active && it.kind == RecurringKind.INCOME }.sumOf { it.cadence.monthly(it.amount) }
        if (salary > 0) return salary
        val months = (1..3).map { YearMonth.from(today).minusMonths(it.toLong()) }
        return months.sumOf { Ledger.income(data, it.startMillis(), it.endMillis()) } / 3
    }
}
