package io.github.abusaeedbd.unseen.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A short history of protection changes, so the owner can see if someone turned it off. */
object EventLog {

    data class Event(val time: Long, val text: String, val warning: Boolean)

    private const val FILE = "unseen_events"
    private const val KEY = "events"
    private const val MAX = 50

    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val raw = prefs(context).getString(KEY, "") ?: ""
        _events.value = raw.lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) null
            else parts[0].toLongOrNull()?.let { Event(it, parts[2], parts[1] == "!") }
        }
        loaded = true
    }

    @Synchronized
    fun add(context: Context, text: String, warning: Boolean = false) {
        load(context)
        val clean = text.replace('\t', ' ').replace('\n', ' ')
        val list = (listOf(Event(System.currentTimeMillis(), clean, warning)) + _events.value).take(MAX)
        _events.value = list
        prefs(context).edit()
            .putString(KEY, list.joinToString("\n") { "${it.time}\t${if (it.warning) "!" else "-"}\t${it.text}" })
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
