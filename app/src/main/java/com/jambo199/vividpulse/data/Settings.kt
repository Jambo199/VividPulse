package com.jambo199.vividpulse.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("Follow system") }

/** How often the live pages read the phone's vitals. */
enum class RefreshRate(val label: String, val ms: Long) {
    FAST("Fast · 0.5 s", 500),
    NORMAL("Normal · 1 s", 1000),
    SAVER("Battery saver · 2 s", 2000),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val refresh: RefreshRate = RefreshRate.NORMAL,
    val fahrenheit: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    /** Where Steam prices are shown for: a two-letter country code, or "" for this phone's region. */
    val steamCountry: String = "",
    /** Keep the screen awake while the app is open (watching a charge, temperatures…). */
    val keepScreenOn: Boolean = false,
    /** Watches every charge: live notification, the charge limit alert and the charge history. */
    val chargeCompanion: Boolean = false,
    /** Alert when the battery reaches this % while charging. */
    val chargeLimit: Int = 80,
    /** A note in the morning saying how much the battery used overnight. */
    val overnightReport: Boolean = false,
    /** Alerts when a starred game goes on sale or drops to the price you set. */
    val saleAlerts: Boolean = true,
    /** An alert when one of Steam's big seasonal sales starts. */
    val bigSaleAlerts: Boolean = true,
    /** Your GG.deals API key, for prices at other stores and key sellers. */
    val ggDealsKey: String = "",
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    fun update(change: (Settings) -> Settings) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit()
            .putString("theme", next.theme.name)
            .putString("refresh", next.refresh.name)
            .putBoolean("fahrenheit", next.fahrenheit)
            .putBoolean("autoCheckUpdates", next.autoCheckUpdates)
            .putString("steamCountry", next.steamCountry)
            .putBoolean("keepScreenOn", next.keepScreenOn)
            .putBoolean("chargeCompanion", next.chargeCompanion)
            .putInt("chargeLimit", next.chargeLimit)
            .putBoolean("overnightReport", next.overnightReport)
            .putBoolean("saleAlerts", next.saleAlerts)
            .putBoolean("bigSaleAlerts", next.bigSaleAlerts)
            .putString("ggDealsKey", next.ggDealsKey.trim())
            .apply()
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            theme = enumOr(prefs.getString("theme", null), d.theme),
            refresh = enumOr(prefs.getString("refresh", null), d.refresh),
            fahrenheit = prefs.getBoolean("fahrenheit", d.fahrenheit),
            autoCheckUpdates = prefs.getBoolean("autoCheckUpdates", d.autoCheckUpdates),
            steamCountry = prefs.getString("steamCountry", d.steamCountry).orEmpty(),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            chargeCompanion = prefs.getBoolean("chargeCompanion", d.chargeCompanion),
            chargeLimit = prefs.getInt("chargeLimit", d.chargeLimit),
            overnightReport = prefs.getBoolean("overnightReport", d.overnightReport),
            saleAlerts = prefs.getBoolean("saleAlerts", d.saleAlerts),
            bigSaleAlerts = prefs.getBoolean("bigSaleAlerts", d.bigSaleAlerts),
            ggDealsKey = prefs.getString("ggDealsKey", d.ggDealsKey).orEmpty(),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
