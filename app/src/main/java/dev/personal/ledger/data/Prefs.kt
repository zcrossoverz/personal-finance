package dev.personal.ledger.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** User settings. Small, synchronous, and part of the backup. */
@Serializable
data class Settings(
    val onboarded: Boolean = false,
    /** "vi" (default) or "en". */
    val language: String = "vi",
    val currency: String = "VND",
    val defaultAccountId: Long? = null,
    /** Money in spendable accounts that must never be counted as safe to spend. */
    val protectedSavings: Long = 0,
    val emergencyReserve: Long = 0,
    /** Optional monthly budget for flexible spending. Null = use the 3-month average as the pace reference. */
    val flexibleBudget: Long? = null,
    /** Include an estimate of normal day-to-day spending in the forecast. */
    val forecastIncludesEstimate: Boolean = true,
    val biometricLock: Boolean = false,
    val hideAmounts: Boolean = false,
    val secureScreen: Boolean = true,
    val remindersEnabled: Boolean = true,
    /** "system", "dark" or "light". */
    val theme: String = "system",
    val demo: Boolean = false,
)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("ledger", Context.MODE_PRIVATE)
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _settings = MutableStateFlow(load().also(::applyLocale))
    val settings: StateFlow<Settings> = _settings

    private fun load(): Settings = sp.getString("settings", null)
        ?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() }
        ?: Settings()

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        applyLocale(next)
        _settings.value = next
        sp.edit().putString("settings", json.encodeToString(Settings.serializer(), next)).apply()
    }

    fun replace(s: Settings) = update { s }

    /** Language is global state read by formatting code, so it is applied before observers see the new settings. */
    private fun applyLocale(s: Settings) {
        dev.personal.ledger.i18n.I18n.lang = dev.personal.ledger.i18n.Lang.of(s.language)
    }
}
