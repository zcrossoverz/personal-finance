package dev.personal.ledger.i18n

import java.util.Locale

enum class Lang(val code: String, val label: String) {
    VI("vi", "Tiếng Việt"), EN("en", "English");

    companion object { fun of(code: String?) = entries.firstOrNull { it.code == code } ?: VI }
}

/** The active language. Vietnamese is the default; set from Settings before any UI is composed. */
object I18n {
    @Volatile var lang: Lang = Lang.VI
    val vi: Boolean get() = lang == Lang.VI
}

/**
 * Translates an English source string. Arguments use String.format (%s, %d, %%).
 * English strings are the keys, so code stays readable and a missing translation degrades to English instead
 * of crashing; I18nTest fails the build if any key used in the sources has no Vietnamese entry.
 */
fun tr(en: String, vararg args: Any?): String {
    val pattern = if (I18n.vi) Vi.table[en] ?: en else en
    return if (args.isEmpty()) pattern else String.format(Locale.ROOT, pattern, *args)
}
