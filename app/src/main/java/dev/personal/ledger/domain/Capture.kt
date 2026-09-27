package dev.personal.ledger.domain

import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Preset
import dev.personal.ledger.data.TxType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Preset ordering and learned amounts. Deterministic and explainable:
 * pinned presets keep the user's order (muscle memory); unpinned ones are ranked by recency-weighted frequency,
 * time-of-day affinity and weekday/weekend affinity.
 */
object PresetEngine {

    fun ordered(data: LedgerData, now: LocalDateTime): List<Preset> {
        val visible = data.presets.filter { !it.hidden }
        val pinned = visible.filter { it.pinned }.sortedBy { it.sortOrder }
        val rest = visible.filter { !it.pinned }
        if (rest.size <= 1) return pinned + rest
        val scores = score(data, rest, now)
        return pinned + rest.sortedWith(compareByDescending<Preset> { scores[it.id] ?: 0.0 }.thenBy { it.sortOrder })
    }

    fun score(data: LedgerData, presets: List<Preset>, now: LocalDateTime): Map<Long, Double> {
        val ids = presets.map { it.id }.toSet()
        val horizon = now.toLocalDate().minusDays(90).startMillis()
        val uses = data.transactions.filter { it.presetId in ids && it.date >= horizon }.groupBy { it.presetId!! }
        val hourNow = now.hour + now.minute / 60.0
        val weekendNow = now.toLocalDate().isWeekend
        val freq = presets.associate { p ->
            p.id to uses[p.id].orEmpty().sumOf { t ->
                val age = daysBetween(t.date.toLocalDate(), now.toLocalDate()).toDouble()
                exp(-age / 30.0)
            }
        }
        val maxFreq = freq.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        return presets.associate { p ->
            val u = uses[p.id].orEmpty()
            val hourAff = if (u.isEmpty()) 0.0 else u.count { t ->
                val dt = t.date.toLocalDateTime()
                val h = dt.hour + dt.minute / 60.0
                val diff = abs(h - hourNow).let { min(it, 24 - it) }
                diff <= 1.5
            }.toDouble() / u.size
            val dayAff = if (u.isEmpty()) 0.0 else u.count { it.date.toLocalDate().isWeekend == weekendNow }.toDouble() / u.size
            p.id to (0.5 * (freq[p.id]!! / maxFreq) + 0.35 * hourAff + 0.15 * dayAff)
        }
    }

    /**
     * Contextual presets resolve to a time-appropriate subcategory: a child tagged "lunch" between 10:30–14:00,
     * "dinner" between 17:00–21:30. Position and icon never change, only the label specialises.
     */
    fun resolveCategory(data: LedgerData, preset: Preset, now: LocalDateTime): Long? {
        val base = preset.categoryId ?: return null
        if (!preset.contextual) return base
        val minutes = now.hour * 60 + now.minute
        val tag = when (minutes) {
            in 630..839 -> "lunch"
            in 1020..1289 -> "dinner"
            else -> return base
        }
        return data.categories.firstOrNull { it.parentId == base && it.aliases.split(',').any { a -> a.trim() == tag } }?.id ?: base
    }

    fun labelFor(data: LedgerData, preset: Preset, now: LocalDateTime): String {
        val resolved = resolveCategory(data, preset, now)
        return if (preset.contextual && resolved != preset.categoryId) data.categoryById[resolved]?.name ?: preset.label else preset.label
    }

    /** Up to [n] amounts this preset is usually entered with, ascending. Falls back to the preset's seed amounts. */
    fun quickAmounts(data: LedgerData, preset: Preset, today: LocalDate, n: Int = 4): List<Long> {
        val horizon = today.minusDays(120).startMillis()
        val history = data.transactions.filter { it.presetId == preset.id && it.date >= horizon }
        val ranked = history.groupBy { it.amount }
            .map { (amt, ts) -> amt to (ts.size * 10 + ts.maxOf { it.date } / 86_400_000L % 10) }
            .sortedByDescending { it.second }
            .map { it.first }
        val seeds = preset.seedAmounts.split(',').mapNotNull { it.trim().toLongOrNull() }
        return (ranked + seeds).distinct().take(n).sorted()
    }

    /** Account for a preset: explicit, else last used with this preset, else global default, else first everyday account. */
    fun accountFor(data: LedgerData, preset: Preset?, defaultAccountId: Long?): Account? {
        preset?.accountId?.let { id -> data.accountById[id]?.takeIf { !it.archived }?.let { return it } }
        if (preset != null) {
            data.transactions.firstOrNull { it.presetId == preset.id }?.let { t ->
                data.accountById[t.accountId]?.takeIf { !it.archived }?.let { return it }
            }
        }
        return defaultAccountId?.let { data.accountById[it] }?.takeIf { !it.archived }
            ?: data.accounts.firstOrNull { !it.archived && it.type == AccountType.BANK }
            ?: data.accounts.firstOrNull { !it.archived }
    }
}

/**
 * Command-style entry for experienced users:
 *   "85k ăn" · "350k xăng mb" · "250k shopee vp" · "chuyển 5m mb vcb" · "+15m lương" · "hôm qua 45k cafe"
 */
object CommandParser {
    data class Parsed(
        val type: TxType,
        val amount: Long?,
        val preset: Preset?,
        val categoryId: Long?,
        val account: Account?,
        val toAccount: Account?,
        val daysAgo: Int,
        val note: String,
    ) {
        val complete get() = amount != null && amount > 0 && account != null &&
            (type != TxType.TRANSFER || (toAccount != null && toAccount.id != account.id)) &&
            (type == TxType.TRANSFER || categoryId != null || preset != null)
    }

    private val transferWords = setOf("chuyen", "ck", "transfer", "tf", "chuyenkhoan")
    private val incomeWords = setOf("thu", "income", "nhan", "luong", "salary")
    private val yesterdayWords = setOf("homqua", "hqua", "yesterday", "hq")

    fun parse(input: String, data: LedgerData, defaultAccountId: Long?): Parsed {
        var text = input.trim()
        var type = TxType.EXPENSE
        if (text.startsWith("+")) { type = TxType.INCOME; text = text.drop(1) }
        // Join "hôm qua" into one token before splitting.
        val folded = text.fold().replace("hom qua", "homqua")
        val tokens = folded.split(Regex("\\s+")).filter { it.isNotBlank() }
        var amount: Long? = null
        var daysAgo = 0
        val accounts = ArrayList<Account>()
        var preset: Preset? = null
        var categoryId: Long? = null
        val noteWords = ArrayList<String>()

        for (tok in tokens) {
            if (amount == null) {
                val a = Money.parse(tok)
                if (a != null && tok.first().isDigit()) {
                    // Bare small numbers in VND are thousands: "85 ăn" means 85k.
                    amount = if (a in 1..999 && tok.all { it.isDigit() }) a * 1000 else a
                    continue
                }
            }
            if (tok in transferWords) { type = TxType.TRANSFER; continue }
            if (tok in yesterdayWords) { daysAgo = 1; continue }
            val acc = matchAccount(tok, data)
            if (acc != null) { if (acc !in accounts) accounts += acc; continue }
            if (preset == null && categoryId == null) {
                val p = matchPreset(tok, data)
                if (p != null) { preset = p; categoryId = p.categoryId; if (p.type == TxType.INCOME) type = TxType.INCOME; continue }
                val c = matchCategory(tok, data)
                if (c != null) { categoryId = c; if (data.categoryById[c]?.isIncome == true) type = TxType.INCOME; continue }
                if (tok in incomeWords) {
                    type = TxType.INCOME
                    categoryId = data.categories.firstOrNull { it.isIncome && it.name.fold().contains("luong") }?.id
                        ?: data.categories.firstOrNull { it.isIncome }?.id
                    continue
                }
            }
            noteWords += tok
        }

        val (from, to) = if (type == TxType.TRANSFER) {
            (accounts.getOrNull(0) ?: PresetEngine.accountFor(data, null, defaultAccountId)) to accounts.getOrNull(1)
        } else {
            (accounts.firstOrNull() ?: PresetEngine.accountFor(data, preset, defaultAccountId)) to null
        }
        // Keep the user's original words (with diacritics) for the note.
        val originalWords = text.split(Regex("\\s+")).filter { w -> w.fold() in noteWords }
        return Parsed(type, amount, preset, categoryId, from, to, daysAgo, originalWords.joinToString(" "))
    }

    private fun accountMatches(a: Account, tok: String): Boolean {
        if (tok.length < 2) return false
        val keys = a.aliases.split(',').map { it.trim().fold() }.filter { it.isNotEmpty() } + a.name.fold().split(' ').first()
        return keys.any { it == tok || (tok.length >= 3 && it.startsWith(tok)) }
    }

    private fun matchAccount(tok: String, data: LedgerData): Account? =
        data.accounts.filter { !it.archived }.firstOrNull { accountMatches(it, tok) }

    private fun matchPreset(tok: String, data: LedgerData): Preset? {
        if (tok.length < 2) return null
        return data.presets.firstOrNull { p -> p.label.fold().split(' ', '/').any { it == tok } }
            ?: data.presets.firstOrNull { p -> p.label.fold().split(' ', '/').any { it.startsWith(tok) } }
    }

    private fun matchCategory(tok: String, data: LedgerData): Long? {
        if (tok.length < 2) return null
        val cats = data.categories.filter { !it.archived }
        return cats.firstOrNull { c -> c.aliases.split(',').any { it.trim().fold() == tok } || c.name.fold().split(' ').any { it == tok } }?.id
            ?: cats.firstOrNull { c -> c.name.fold().split(' ').any { it.startsWith(tok) } }?.id
    }
}

/**
 * Short keywords for command entry and search, derived from an account name the user typed:
 * "HDBank" → "hdbank,hd" · "Techcombank" → "techcombank,techcom" · "Ví Viettel Money" → "viviettelmoney,vvm,vi".
 */
fun accountAliases(name: String): String {
    val words = name.fold().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    val out = LinkedHashSet<String>()
    out += words.joinToString("")
    val joined = words.joinToString("")
    if (joined.endsWith("bank") && joined.length > 5) out += joined.removeSuffix("bank")
    if (words.size > 1) out += words.joinToString("") { it.take(1) }
    out += words.first()
    return out.filter { it.length >= 2 }.joinToString(",")
}
