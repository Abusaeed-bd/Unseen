package io.github.abusaeedbd.unseen.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Today's counts. The total survives restarts; the per-app counts live in memory
 * for now (v1) and start again when the app process restarts.
 */
object Stats {
    private const val FILE = "unseen_stats"
    private const val KEY_DAY = "day"
    private const val KEY_COUNT = "count"

    private lateinit var prefs: SharedPreferences

    private val _blockedToday = MutableStateFlow(0)
    val blockedToday: StateFlow<Int> = _blockedToday.asStateFlow()

    /** package name -> trackers blocked today */
    private val _perApp = MutableStateFlow<Map<String, Int>>(emptyMap())
    val perApp: StateFlow<Map<String, Int>> = _perApp.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        _blockedToday.value = if (prefs.getString(KEY_DAY, null) == today()) prefs.getInt(KEY_COUNT, 0) else 0
    }

    @Synchronized
    fun recordBlocked(appKey: String) {
        val day = today()
        if (prefs.getString(KEY_DAY, null) != day) {
            _blockedToday.value = 0
            _perApp.value = emptyMap()
        }
        val total = _blockedToday.value + 1
        _blockedToday.value = total
        val apps = _perApp.value
        _perApp.value = apps + (appKey to (apps[appKey] ?: 0) + 1)
        prefs.edit().putString(KEY_DAY, day).putInt(KEY_COUNT, total).apply()
    }

    private fun today(): String = LocalDate.now().toString()
}
