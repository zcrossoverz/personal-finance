package dev.personal.ledger.domain

import dev.personal.ledger.i18n.tr
import dev.personal.ledger.data.Category
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Settings
import dev.personal.ledger.data.TxType
import java.time.LocalDate
import java.time.YearMonth

object Analytics {

    data class CategoryTotal(
        val category: Category?,
        val amount: Long,
        /** Average of the 3 previous months over the same day window (so mid-month comparisons are fair). */
        val baseline: Long,
        val count: Int,
    ) {
        val deltaPct: Float? get() = if (baseline > 0) (amount - baseline).toFloat() / baseline else null
    }

    data class Month(
        val month: YearMonth,
        /** Last day with data: today for the current month, month end otherwise. */
        val throughDay: Int,
        val income: Long,
        val spending: Long,
        val fixed: Long,
        val flexible: Long,
        val daily: LongArray,
        val cumulative: LongArray,
        val prevCumulative: LongArray,
        val avgCumulative: LongArray,
        val categories: List<CategoryTotal>,
        val fixedCategories: List<CategoryTotal>,
        val flexibleCategories: List<CategoryTotal>,
        val incomeSources: List<Pair<String, Long>>,
        val weekendPerDay: Long,
        val weekdayPerDay: Long,
        val txCount: Int,
    ) {
        val net get() = income - spending
        val daysInMonth get() = month.lengthOfMonth()
        val isPartial get() = throughDay < daysInMonth
    }

    private fun cumulativeOf(data: LedgerData, m: YearMonth, flexibleOnly: Boolean = false): LongArray {
        val arr = LongArray(m.lengthOfMonth())
        Ledger.expenseLines(data, m.startMillis(), m.endMillis()).forEach {
            if (flexibleOnly && Ledger.isFixed(data, it.categoryId)) return@forEach
            arr[it.tx.date.toLocalDate().dayOfMonth - 1] += it.amount
        }
        for (i in 1 until arr.size) arr[i] += arr[i - 1]
        return arr
    }

    fun month(data: LedgerData, m: YearMonth, today: LocalDate): Month {
        val through = when {
            YearMonth.from(today) == m -> today.dayOfMonth
            m.isAfter(YearMonth.from(today)) -> 0
            else -> m.lengthOfMonth()
        }
        val lines = Ledger.expenseLines(data, m.startMillis(), m.endMillis())
        val daily = LongArray(m.lengthOfMonth())
        lines.forEach { daily[it.tx.date.toLocalDate().dayOfMonth - 1] += it.amount }
        val cumulative = daily.copyOf().also { for (i in 1 until it.size) it[i] += it[i - 1] }

        val prev = cumulativeOf(data, m.minusMonths(1))
        val avg = LongArray(m.lengthOfMonth())
        val history = (1..3).map { cumulativeOf(data, m.minusMonths(it.toLong())) }
        val monthsWithData = history.count { it.isNotEmpty() && it.last() > 0 }.coerceAtLeast(1)
        for (i in avg.indices) {
            avg[i] = history.sumOf { h -> if (h.isEmpty()) 0L else h[minOf(i, h.size - 1)] } / monthsWithData
        }

        // Baseline window: day 1..through of each of the previous 3 months.
        val window = if (through == 0) m.lengthOfMonth() else through
        val baselineByRoot = HashMap<Long?, Long>()
        (1..3).forEach { k ->
            val pm = m.minusMonths(k.toLong())
            val end = pm.dayClamped(window).endMillis()
            Ledger.expenseLines(data, pm.startMillis(), end).forEach {
                val root = data.rootOf(it.categoryId)?.id
                baselineByRoot[root] = (baselineByRoot[root] ?: 0) + it.amount
            }
        }

        val grouped = lines.groupBy { data.rootOf(it.categoryId)?.id }
        val cats = grouped.map { (rootId, ls) ->
            CategoryTotal(rootId?.let { data.categoryById[it] }, ls.sumOf { it.amount }, (baselineByRoot[rootId] ?: 0) / monthsWithData, ls.map { it.tx.id }.distinct().size)
        }.filter { it.amount != 0L }.sortedByDescending { it.amount }

        val fixedCats = cats.filter { Ledger.isFixed(data, it.category?.id) }
        val flexCats = cats.filterNot { Ledger.isFixed(data, it.category?.id) }

        val income = data.transactions.filter { it.type == TxType.INCOME && it.date in m.startMillis()..m.endMillis() }
        val sources = income.groupBy { data.categoryById[it.categoryId]?.name ?: tr("Other income") }
            .map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

        var we = 0L; var wd = 0L; var weDays = 0; var wdDays = 0
        for (day in 1..(if (through == 0) 0 else through)) {
            val date = m.atDay(day)
            if (date.isWeekend) { we += daily[day - 1]; weDays++ } else { wd += daily[day - 1]; wdDays++ }
        }

        return Month(
            month = m,
            throughDay = through,
            income = income.sumOf { it.amount },
            spending = lines.sumOf { it.amount },
            fixed = fixedCats.sumOf { it.amount },
            flexible = flexCats.sumOf { it.amount },
            daily = daily,
            cumulative = cumulative,
            prevCumulative = prev,
            avgCumulative = avg,
            categories = cats,
            fixedCategories = fixedCats,
            flexibleCategories = flexCats,
            incomeSources = sources,
            weekendPerDay = if (weDays > 0) we / weDays else 0,
            weekdayPerDay = if (wdDays > 0) wd / wdDays else 0,
            txCount = lines.map { it.tx.id }.distinct().size,
        )
    }

    data class Pace(
        val day: Int,
        val days: Int,
        val spent: Long,
        val reference: Long,
        val referenceIsBudget: Boolean,
        /** Share of the reference spent so far. */
        val usedPct: Float,
        /** Share of the month elapsed. */
        val expectedPct: Float,
        val projected: Long,
    ) {
        val aheadPct get() = usedPct - expectedPct
    }

    /** Flexible-spending pace for the current month vs a budget, or vs the 3-month average when no budget is set. */
    fun pace(data: LedgerData, settings: Settings, today: LocalDate): Pace? {
        val m = YearMonth.from(today)
        val flexible = cumulativeOf(data, m, flexibleOnly = true)
        val spent = flexible[today.dayOfMonth - 1]
        val history = (1..3).map { cumulativeOf(data, m.minusMonths(it.toLong()), flexibleOnly = true).lastOrNull() ?: 0 }.filter { it > 0 }
        val reference = settings.flexibleBudget ?: if (history.isEmpty()) return null else history.sum() / history.size
        if (reference <= 0) return null
        val days = m.lengthOfMonth()
        val day = today.dayOfMonth
        return Pace(day, days, spent, reference, settings.flexibleBudget != null,
            spent.toFloat() / reference, day.toFloat() / days, spent * days / day)
    }

    data class MonthBar(val month: YearMonth, val income: Long, val spending: Long, val fixed: Long)

    fun months(data: LedgerData, endMonth: YearMonth, count: Int): List<MonthBar> =
        (count - 1 downTo 0).map { k ->
            val m = endMonth.minusMonths(k.toLong())
            val lines = Ledger.expenseLines(data, m.startMillis(), m.endMillis())
            MonthBar(m, Ledger.income(data, m.startMillis(), m.endMillis()), lines.sumOf { it.amount },
                lines.filter { Ledger.isFixed(data, it.categoryId) }.sumOf { it.amount })
        }

    data class NetWorthPoint(val month: YearMonth, val assets: Long, val liabilities: Long) { val net get() = assets - liabilities }

    fun netWorthHistory(data: LedgerData, today: LocalDate, months: Int = 12): List<NetWorthPoint> {
        val first = data.transactions.minOfOrNull { it.date }?.toLocalDate() ?: return emptyList()
        val start = YearMonth.from(first)
        return (months - 1 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
            .filter { !it.isBefore(start) }
            .map { m ->
                val asOf = if (m == YearMonth.from(today)) System.currentTimeMillis() else m.endMillis()
                val p = Ledger.position(data, asOf)
                NetWorthPoint(m, p.assets, p.liabilities)
            }
    }
}
