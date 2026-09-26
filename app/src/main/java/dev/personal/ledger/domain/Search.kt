package dev.personal.ledger.domain

import dev.personal.ledger.i18n.tr
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.TxType
import dev.personal.ledger.data.Txn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

/**
 * Natural search over transactions. Understands:
 *   "shopee" · "500k" · "food this week" · "mb september" · "subscriptions" · "> 1m" · "<200k" · "refund" · "tháng 9"
 * Everything that isn't a recognised token is matched as text against note, category, account and preset.
 */
object Search {
    data class Query(
        val text: List<String> = emptyList(),
        val exact: Long? = null,
        val min: Long? = null,
        val max: Long? = null,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
        val accountIds: Set<Long> = emptySet(),
        val categoryIds: Set<Long> = emptySet(),
        /** Words that resolved to categories; a transaction also matches if its text contains them. */
        val categoryWords: List<String> = emptyList(),
        val types: Set<TxType> = emptySet(),
        val recurringOnly: Boolean = false,
        val chips: List<String> = emptyList(),
    ) {
        val isEmpty get() = text.isEmpty() && exact == null && min == null && max == null && from == null &&
            accountIds.isEmpty() && categoryIds.isEmpty() && types.isEmpty() && !recurringOnly
    }

    private val months = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3, "apr" to 4, "april" to 4,
        "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7, "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9,
        "september" to 9, "oct" to 10, "october" to 10, "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )

    fun parse(input: String, data: LedgerData, today: LocalDate): Query {
        var q = Query()
        val chips = ArrayList<String>()
        val raw = input.fold()
            .replace("this week", "thisweek").replace("tuan nay", "thisweek")
            .replace("last week", "lastweek").replace("tuan truoc", "lastweek")
            .replace("this month", "thismonth").replace("thang nay", "thismonth")
            .replace("last month", "lastmonth").replace("thang truoc", "lastmonth")
            .replace("hom nay", "today").replace("hom qua", "yesterday")
            .replace("dang ky", "dangky").replace("hoan tien", "hoantien").replace("chuyen khoan", "chuyen").replace("thu nhap", "thunhap")
            .replace(Regex("thang\\s+(\\d{1,2})"), "thang$1")
            .replace(Regex("([<>])\\s+"), "$1")
        val text = ArrayList<String>()
        // Multi-word category names first ("ăn uống", "đi chợ", "tiền trọ"), longest first.
        var rest = " $raw "
        data.categories.filter { !it.archived && it.name.contains(' ') }.sortedByDescending { it.name.length }.forEach { c ->
            val key = " " + c.name.fold() + " "
            if (rest.contains(key)) {
                rest = rest.replace(key, " ")
                val ids = listOf(c.id) + data.categories.filter { it.parentId == c.id }.map { it.id }
                q = q.copy(categoryIds = q.categoryIds + ids, categoryWords = q.categoryWords + c.name.fold()); chips += c.name
            }
        }
        for (tok in rest.split(Regex("\\s+")).filter { it.isNotBlank() }) {
            when {
                tok.startsWith(">") || tok.startsWith("<") -> {
                    val v = Money.parse(tok.drop(1).removePrefix("=")) ?: continue
                    q = if (tok[0] == '>') q.copy(min = v) else q.copy(max = v)
                    chips += "${tok[0]} ${Money.compact(v)}"
                }
                tok.first().isDigit() && Money.parse(tok) != null && tok.any { it.isLetter() || it == ',' || it == '.' } || (tok.all { it.isDigit() } && tok.length >= 4) -> {
                    val v = Money.parse(tok)!!
                    q = q.copy(exact = v); chips += Money.compact(v)
                }
                tok == "today" -> { q = q.copy(from = today, to = today); chips += tr("Today") }
                tok == "yesterday" -> { q = q.copy(from = today.minusDays(1), to = today.minusDays(1)); chips += tr("Yesterday") }
                tok == "thisweek" -> {
                    val start = today.with(DayOfWeek.MONDAY); q = q.copy(from = start, to = today); chips += tr("This week")
                }
                tok == "lastweek" -> {
                    val start = today.with(DayOfWeek.MONDAY).minusWeeks(1); q = q.copy(from = start, to = start.plusDays(6)); chips += tr("Last week")
                }
                tok == "thismonth" -> { q = q.copy(from = today.withDayOfMonth(1), to = today); chips += tr("This month") }
                tok == "lastmonth" -> {
                    val m = YearMonth.from(today).minusMonths(1); q = q.copy(from = m.atDay(1), to = m.atEndOfMonth()); chips += Fmt.month(m)
                }
                tok in months || Regex("^thang(\\d{1,2})$").matches(tok) -> {
                    val n = months[tok] ?: tok.removePrefix("thang").toInt()
                    if (n in 1..12) {
                        var m = YearMonth.of(today.year, Month.of(n))
                        if (m.isAfter(YearMonth.from(today))) m = m.minusYears(1)
                        q = q.copy(from = m.atDay(1), to = m.atEndOfMonth()); chips += Fmt.monthYear(m)
                    }
                }
                tok in setOf("subscription", "subscriptions", "sub", "subs", "dangky") -> { q = q.copy(recurringOnly = true); chips += tr("Subscriptions") }
                tok in setOf("transfer", "transfers", "chuyen") -> { q = q.copy(types = q.types + TxType.TRANSFER); chips += tr("Transfers") }
                tok in setOf("income", "thu", "thunhap") -> { q = q.copy(types = q.types + TxType.INCOME); chips += tr("Income") }
                tok in setOf("refund", "refunds", "hoan", "hoantien") -> { q = q.copy(types = q.types + TxType.REFUND + TxType.REIMBURSEMENT); chips += tr("Refunds") }
                else -> {
                    val acc = data.accounts.firstOrNull { a -> a.aliases.split(',').any { it.trim().fold() == tok } || a.name.fold().split(' ').first() == tok }
                    val cats = data.categories.filter { c -> c.name.fold().split(' ', '/').any { it.startsWith(tok) && tok.length >= 3 } || c.aliases.split(',').any { it.trim().fold() == tok } }
                    when {
                        acc != null -> { q = q.copy(accountIds = q.accountIds + acc.id); chips += acc.name }
                        cats.isNotEmpty() && tok.length >= 3 -> {
                            val ids = cats.flatMap { c -> listOf(c.id) + data.categories.filter { it.parentId == c.id }.map { it.id } }
                            q = q.copy(categoryIds = q.categoryIds + ids, categoryWords = q.categoryWords + tok); chips += cats.first().name
                        }
                        else -> text += tok
                    }
                }
            }
        }
        return q.copy(text = text, chips = chips)
    }

    fun run(q: Query, data: LedgerData): List<Txn> {
        if (q.isEmpty) return data.transactions
        val subRecurring = data.recurring.filter { it.kind == RecurringKind.SUBSCRIPTION }.map { it.id }.toSet()
        return data.transactions.filter { t ->
            val d = t.date.toLocalDate()
            if (q.from != null && d.isBefore(q.from)) return@filter false
            if (q.to != null && d.isAfter(q.to)) return@filter false
            if (q.exact != null && t.amount != q.exact) return@filter false
            if (q.min != null && t.amount <= q.min) return@filter false
            if (q.max != null && t.amount >= q.max) return@filter false
            if (q.types.isNotEmpty() && t.type !in q.types) return@filter false
            if (q.accountIds.isNotEmpty() && t.accountId !in q.accountIds && t.toAccountId !in q.accountIds) return@filter false
            if (q.recurringOnly && t.recurringId !in subRecurring) return@filter false
            val hay by lazy {
                buildString {
                    append(t.note.fold()); append(' ')
                    append(data.categoryById[t.categoryId]?.name?.fold() ?: ""); append(' ')
                    append(data.accountById[t.accountId]?.name?.fold() ?: ""); append(' ')
                    append(data.presetById[t.presetId]?.label?.fold() ?: ""); append(' ')
                    data.splitsByTx[t.id]?.forEach { append(it.note.fold()); append(' '); append(data.categoryById[it.categoryId]?.name?.fold() ?: "") }
                    t.recurringId?.let { id -> append(data.recurring.firstOrNull { it.id == id }?.name?.fold() ?: "") }
                }
            }
            if (q.categoryIds.isNotEmpty()) {
                val cats = (data.splitsByTx[t.id]?.map { it.categoryId } ?: emptyList()) + listOfNotNull(t.categoryId)
                if (cats.none { it in q.categoryIds } && (q.categoryWords.isEmpty() || q.categoryWords.any { it !in hay })) return@filter false
            }
            if (q.text.isNotEmpty() && q.text.any { it !in hay }) return@filter false
            true
        }
    }
}
