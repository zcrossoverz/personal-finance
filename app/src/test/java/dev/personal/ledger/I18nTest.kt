package dev.personal.ledger

import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.Money
import dev.personal.ledger.i18n.I18n
import dev.personal.ledger.i18n.Lang
import dev.personal.ledger.i18n.Vi
import dev.personal.ledger.i18n.tr
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/** Vietnamese is the default language, so every user-facing string must have a Vietnamese entry. */
class I18nTest {
    @After fun reset() { I18n.lang = Lang.VI }

    private fun sourceKeys(): Set<String> {
        val root = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val pattern = Regex("""\btr\(\s*"((?:[^"\\]|\\.)*)"""")
        // Undo Kotlin source escapes so keys compare equal to the runtime strings in the table.
        return root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            pattern.findAll(f.readText()).map { m -> m.groupValues[1].replace("\\n", "\n").replace("\\\"", "\"").replace("\\$", "$") }
        }.toSet()
    }

    @Test fun everySourceStringHasAVietnameseTranslation() {
        val keys = sourceKeys()
        assertTrue("found only ${keys.size} keys — scan broken?", keys.size > 300)
        val missing = keys.filter { it !in Vi.table }
        assertTrue("Missing Vietnamese for:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test fun translationsKeepTheSameArguments() {
        val spec = Regex("""%(\d+\$)?[sd]""")
        Vi.table.forEach { (en, vi) ->
            assertEquals("argument count differs for \"$en\"", spec.findAll(en).count(), spec.findAll(vi).count())
            // Formatting must not throw.
            val args: Array<Any?> = spec.findAll(en).map<MatchResult, Any?> { if (it.value.endsWith("d")) 1 else "x" }.toList().toTypedArray()
            I18n.lang = Lang.VI; tr(en, *args)
        }
    }

    @Test fun vietnameseIsDefaultAndFormatsNumbersLocally() {
        I18n.lang = Lang.VI
        assertEquals("Có thể chi", tr("Safe to spend"))
        assertEquals("65.000 ₫", Money.full(65_000))
        assertEquals("8,4tr", Money.compact(8_400_000))
        assertEquals("1,42tr", Money.compact(1_420_000))
        assertEquals("65k", Money.compact(65_000))
        assertEquals(1_500_000L, Money.parse("1,5tr"))
        assertEquals(65_000L, Money.parse("65.000"))
        assertEquals("26/09", Fmt.dayMonth(LocalDate.of(2026, 9, 26)))
        assertEquals("T7", Fmt.weekday(LocalDate.of(2026, 9, 26)))
        assertEquals("Tháng 9", Fmt.month(YearMonth.of(2026, 9)))
        assertEquals("còn 3 ngày", Fmt.dueIn(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 26)))
        I18n.lang = Lang.EN
        assertEquals("Safe to spend", tr("Safe to spend"))
        assertEquals("8.4m", Money.compact(8_400_000))
    }
}
