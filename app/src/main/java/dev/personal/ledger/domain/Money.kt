package dev.personal.ledger.domain

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToLong

/** Currency-aware formatting. VND has no minor unit, so one unit = one đồng. */
object Money {
    var symbol: String = "₫"

    /** 65000 → "65,000 ₫". */
    fun full(v: Long, sign: Boolean = false): String {
        val s = group(abs(v))
        val prefix = when {
            v < 0 -> "−"
            sign && v > 0 -> "+"
            else -> ""
        }
        return "$prefix$s $symbol"
    }

    /** Plain grouped number without symbol: 65000 → "65,000". */
    fun group(v: Long): String {
        val digits = abs(v).toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return (if (v < 0) "−" else "") + sb.toString()
    }

    /** Compact, ≤ 3 significant digits: 850 → "850", 65000 → "65k", 1420000 → "1.42m", 8400000 → "8.4m". */
    fun compact(v: Long, sign: Boolean = false): String {
        val a = abs(v)
        val body = when {
            a < 1_000 -> a.toString()
            a < 1_000_000 -> scaled(a, 1_000.0) + "k"
            a < 1_000_000_000 -> scaled(a, 1_000_000.0) + "m"
            else -> scaled(a, 1_000_000_000.0) + "b"
        }
        val prefix = when {
            v < 0 -> "−"
            sign && v > 0 -> "+"
            else -> ""
        }
        return prefix + body
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
        return s
    }

    /**
     * Parses user amounts: "65000", "65k", "1.5m", "5tr", "1tr5", "2,5m", "100". Returns null when unparseable.
     * Bare numbers below 1000 are read literally (the numpad never produces them for VND in practice, but a
     * command like "85 ăn" is ambiguous, so the command parser applies its own thousand heuristic).
     */
    fun parse(input: String): Long? {
        val s = input.trim().lowercase().replace(" ", "").replace("đ", "").replace("₫", "").replace("vnd", "")
        if (s.isEmpty()) return null
        Regex("^(\\d+)tr(\\d)?$").matchEntire(s)?.let { m ->
            val whole = m.groupValues[1].toLong()
            val frac = m.groupValues[2].takeIf { it.isNotEmpty() }?.toLong() ?: 0
            return whole * 1_000_000 + frac * 100_000
        }
        val m = Regex("^(\\d+(?:[.,]\\d+)?)(k|m|tr|b|ty|tỷ)?$").matchEntire(s) ?: run {
            val digitsOnly = s.replace(",", "").replace(".", "")
            return digitsOnly.toLongOrNull()
        }
        val numStr = m.groupValues[1]
        val unit = when (m.groupValues[2]) {
            "k" -> 1_000L
            "m", "tr" -> 1_000_000L
            "b", "ty", "tỷ" -> 1_000_000_000L
            else -> 1L
        }
        if (unit == 1L) {
            // "65,000" / "65.000" grouping
            return numStr.replace(",", "").replace(".", "").toLongOrNull()
        }
        val d = numStr.replace(',', '.').toDoubleOrNull() ?: return null
        return (d * unit).roundToLong()
    }
}

/** Lowercase, strip Vietnamese diacritics, đ → d. Used for matching and search. */
fun String.fold(): String {
    val n = Normalizer.normalize(this.lowercase(), Normalizer.Form.NFD)
    return n.replace(Regex("\\p{Mn}+"), "").replace('đ', 'd')
}
