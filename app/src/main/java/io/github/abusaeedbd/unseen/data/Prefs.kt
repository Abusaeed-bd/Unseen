package io.github.abusaeedbd.unseen.data

import android.content.Context
import android.content.SharedPreferences

/** Small settings store: whether protection should be on, and which apps are paused. */
object Prefs {
    private const val FILE = "unseen"
    private const val KEY_WANT_ON = "want_on"
    private const val KEY_PAUSED = "paused_apps"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** True when the user last turned protection on; used to restart it after a reboot. */
    fun wantOn(context: Context): Boolean = prefs(context).getBoolean(KEY_WANT_ON, false)

    fun setWantOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_WANT_ON, on).apply()
    }

    /** Apps whose traffic skips Unseen entirely ("Pause protection for this app"). */
    fun pausedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PAUSED, emptySet())?.toSet() ?: emptySet()

    fun setPaused(context: Context, packageName: String, paused: Boolean) {
        val now = pausedApps(context).toMutableSet()
        if (paused) now.add(packageName) else now.remove(packageName)
        prefs(context).edit().putStringSet(KEY_PAUSED, now).apply()
    }
}
