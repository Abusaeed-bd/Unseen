package io.github.abusaeedbd.unseen.data

import android.content.Context
import android.content.SharedPreferences

/** Small settings store: whether protection should be on, and which apps are paused. */
object Prefs {
    private const val FILE = "unseen"
    private const val KEY_WANT_ON = "want_on"
    private const val KEY_PAUSED = "paused_apps"
    private const val KEY_LOCK = "app_lock"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_ADS = "block_ads"
    private const val KEY_SCAMS = "block_scams"
    private const val KEY_GAMBLING = "block_gambling"
    private const val KEY_ALLOWED = "allowed_domains"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** True when the user last turned protection on; used to restart it after a reboot. */
    fun wantOn(context: Context): Boolean = prefs(context).getBoolean(KEY_WANT_ON, false)

    fun setWantOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_WANT_ON, on).apply()
    }

    /**
     * Payment apps that refuse to open while any VPN is on. They start paused so they
     * work out of the box; the user can turn protection back on for them.
     */
    private val PAUSED_BY_DEFAULT = setOf(
        "com.bKash.customerapp", // bKash
        "com.konasl.nagad",      // Nagad
        "com.dbbl.mbs.apps.main", // Rocket (DBBL)
    )

    /** Apps whose traffic skips Unseen entirely ("Pause protection for this app"). */
    fun pausedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PAUSED, null)?.toSet() ?: PAUSED_BY_DEFAULT

    fun setPaused(context: Context, packageName: String, paused: Boolean) {
        val now = pausedApps(context).toMutableSet()
        if (paused) now.add(packageName) else now.remove(packageName)
        prefs(context).edit().putStringSet(KEY_PAUSED, now).apply()
    }

    /** Ask for fingerprint / screen lock before protection can be turned off or apps paused. */
    fun lockEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_LOCK, false)

    fun setLockEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK, on).apply()
    }

    /** True once the welcome screen has been seen. */
    fun onboarded(context: Context): Boolean = prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun blockAds(context: Context): Boolean = prefs(context).getBoolean(KEY_ADS, true)
    fun blockScams(context: Context): Boolean = prefs(context).getBoolean(KEY_SCAMS, true)
    fun blockGambling(context: Context): Boolean = prefs(context).getBoolean(KEY_GAMBLING, false)

    fun setBlockAds(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_ADS, on).apply()
    fun setBlockScams(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_SCAMS, on).apply()
    fun setBlockGambling(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_GAMBLING, on).apply()

    /** Domains the user chose to let through, e.g. when blocking one broke an app. */
    fun allowedDomains(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ALLOWED, emptySet())?.toSet() ?: emptySet()

    fun setAllowed(context: Context, domain: String, allowed: Boolean) {
        val now = allowedDomains(context).toMutableSet()
        if (allowed) now.add(domain.lowercase()) else now.remove(domain.lowercase())
        prefs(context).edit().putStringSet(KEY_ALLOWED, now).apply()
    }
}
