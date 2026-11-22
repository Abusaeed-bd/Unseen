package io.github.abusaeedbd.unseen.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.abusaeedbd.unseen.dns.Blocklist
import io.github.abusaeedbd.unseen.dns.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Downloads the ad, scam and gambling lists and refreshes them once a day.
 * Each list has mirrors; the first one that answers wins.
 */
object ListUpdater {
    private const val TAG = "UnseenLists"
    private const val PERIODIC_WORK = "update-lists"
    private const val NOW_WORK = "update-lists-now"
    private const val PREFS = "unseen_lists"

    /** Sources. Ads + gambling: StevenBlack/hosts (MIT). Scams: malware-filter by curben (CC BY-SA 4.0). */
    val SOURCES: Map<Category, List<List<String>>> = mapOf(
        Category.AD to listOf(
            listOf(
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
            )
        ),
        Category.GAMBLING to listOf(
            listOf(
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts",
            )
        ),
        Category.SCAM to listOf(
            // Phishing sites
            listOf(
                "https://malware-filter.gitlab.io/malware-filter/phishing-filter-hosts.txt",
                "https://curbengh.github.io/malware-filter/phishing-filter-hosts.txt",
                "https://malware-filter.pages.dev/phishing-filter-hosts.txt",
            ),
            // Sites spreading malware (URLhaus, online only)
            listOf(
                "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-hosts-online.txt",
                "https://curbengh.github.io/malware-filter/urlhaus-filter-hosts-online.txt",
                "https://malware-filter.pages.dev/urlhaus-filter-hosts-online.txt",
            ),
        ),
    )

    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    private val _lastUpdated = MutableStateFlow(0L)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    fun init(context: Context) {
        _lastUpdated.value = prefs(context).getLong("last", 0L)
    }

    /** Daily refresh on any network, plus a first download right away if nothing is there yet. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS).setConstraints(online).build(),
        )
        if (!Blocklist.listFile(context, Category.AD).exists()) updateSoon(context)
    }

    fun updateSoon(context: Context) {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(online).build(),
        )
    }

    /** Downloads every list. Returns true if at least one list was updated. */
    suspend fun updateAll(context: Context): Boolean = withContext(Dispatchers.IO) {
        _updating.value = true
        var any = false
        try {
            for ((category, groups) in SOURCES) {
                val parts = groups.mapNotNull { mirrors -> downloadFirst(mirrors) }
                if (parts.isEmpty()) continue
                val file = Blocklist.listFile(context, category)
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(parts.joinToString("\n"))
                if (tmp.renameTo(file)) {
                    Blocklist.reload(context, category)
                    any = true
                }
            }
            if (any) {
                val now = System.currentTimeMillis()
                prefs(context).edit().putLong("last", now).apply()
                _lastUpdated.value = now
            }
        } finally {
            _updating.value = false
        }
        any
    }

    private fun downloadFirst(mirrors: List<String>): String? {
        for (url in mirrors) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "Unseen-Android")
                try {
                    if (conn.responseCode != 200) continue
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    // A real list has many lines; a short page is an error page, not a list.
                    if (text.count { it == '\n' } > 20) return text
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download failed: $url", e)
            }
        }
        return null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (ListUpdater.updateAll(applicationContext)) Result.success() else Result.retry()
}
