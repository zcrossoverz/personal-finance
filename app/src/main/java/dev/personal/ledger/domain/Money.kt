package dev.personal.ledger.domain

import dev.personal.ledger.i18n.I18n
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Currency-aware formatting. VND has no minor unit, so one unit = one đồng.
 * Vietnamese: 65.000 ₫ · 65k · 8,4tr · 1,2tỷ. English: 65,000 ₫ · 65k · 8.4m · 1.2b.
 */
object Money {
    var symbol: String = "₫"

    private val group get() = if (I18n.vi) '.' else ','
    private val decimal get() = if (I18n.vi) ',' else '.'

    fun full(v: Long, sign: Boolean = false): String = prefix(v, sign) + digits(abs(v)) + " " + symbol

    /** Grouped number without symbol: 65000 → "65.000" (vi) / "65,000" (en). */
    fun group(v: Long): String = (if (v < 0) "−" else "") + digits(abs(v))

    private fun digits(a: Long): String {
        val s = a.toString()
        val sb = StringBuilder()
        s.forEachIndexed { i, c ->
            if (i > 0 && (s.length - i) % 3 == 0) sb.append(group)
            sb.append(c)
        }
        return sb.toString()
    }

    private fun prefix(v: Long, sign: Boolean) = when {
        v < 0 -> "−"
        sign && v > 0 -> "+"
        else -> ""
    }

    /** Compact, ≤ 3 significant digits: 850 → "850", 65000 → "65k", 1420000 → "1,42tr" / "1.42m". */
    fun compact(v: Long, sign: Boolean = false): String {
        val a = abs(v)
        val (m, b) = if (I18n.vi) "tr" to "tỷ" else "m" to "b"
        val body = when {
            a < 1_000 -> a.toString()
            a < 1_000_000 -> scaled(a, 1_000.0) + "k"
            a < 1_000_000_000 -> scaled(a, 1_000_000.0) + m
            else -> scaled(a, 1_000_000_000.0) + b
        }
        return prefix(v, sign) + body
    }

    private fun scaled(a: Long, unit: Double): String {
        val x = a / unit
        val decimals = when {
            x >= 100 -> 0
            x >= 10 -> 1
            else -> 2
        }
        val factor = Math.pow(10.0, decimals.toDouble())
        val r = (x * factor).roundToLong() / factor
        var s = if (decimals == 0) r.roundToLong().toString() else "%.${decimals}f".format(java.util.Locale.US, r)
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        return s.replace('.', decimal)
    }

    /**
     * Parses user amounts in either convention: "65000", "65.000", "65,000", "65k", "1.5m", "1,5tr", "5tr",
     * "1tr5", "2 tỷ". Returns null when unparseable.
     */
    fun parse(input: String): Long? {
        val s = input.trim().lowercase().replace(" ", "").replace("đ", "").replace("₫", "").replace("vnd", "")
        if (s.isEmpty()) return null
        Regex("^(\\d+)tr(\\d)?$").matchEntire(s)?.let { m ->
            val whole = m.groupValues[1].toLong()
            val frac = m.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: 0
            return whole * 1_000_000 + frac * 100_000
        }
        val m = Regex("^(\\d+(?:[.,]\\d+)?)(k|m|tr|trieu|b|ty|tỷ)?$").matchEntire(s) ?: run {
            return s.replace(",", "").replace(".", "").toLongOrNull()
        }
        val numStr = m.groupValues[1]
        val unit = when (m.groupValues[2]) {
            "k" -> 1_000L
            "m", "tr", "trieu" -> 1_000_000L
            "b", "ty", "tỷ" -> 1_000_000_000L
            else -> 1L
        }
        if (unit == 1L) return numStr.replace(",", "").replace(".", "").toLongOrNull()
        val d = numStr.replace(',', '.').toDoubleOrNull() ?: return null
        return (d * unit).roundToLong()
    }
}

/** Lowercase, strip Vietnamese diacritics, đ → d. Used for matching and search. */
fun String.fold(): String {
    val n = Normalizer.normalize(this.lowercase(), Normalizer.Form.NFD)
    return n.replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')
}
