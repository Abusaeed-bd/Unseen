package io.github.abusaeedbd.unseen.data

import android.content.Context
import android.content.SharedPreferences
import io.github.abusaeedbd.unseen.dns.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Counts for one day, by reason. */
data class DayCounts(val trackers: Int = 0, val ads: Int = 0, val scams: Int = 0, val gambling: Int = 0) {
    val total: Int get() = trackers + ads + scams + gambling

    fun plus(category: Category) = when (category) {
        Category.TRACKER -> copy(trackers = trackers + 1)
        Category.AD -> copy(ads = ads + 1)
        Category.SCAM -> copy(scams = scams + 1)
        Category.GAMBLING -> copy(gambling = gambling + 1)
    }

    operator fun plus(other: DayCounts) = DayCounts(
        trackers + other.trackers, ads + other.ads, scams + other.scams, gambling + other.gambling
    )

    /**
     * A rough estimate of mobile data not spent: blocked tracker calls are small,
     * blocked ads (images, videos, scripts) are big. Shown to users as an estimate.
     */
    val dataSavedBytes: Long get() = trackers * TRACKER_BYTES + ads * AD_BYTES

    companion object {
        const val TRACKER_BYTES = 3_000L
        const val AD_BYTES = 30_000L
    }
}

/** A summary of the last 7 days, for the weekly report and the share card. */
data class WeekSummary(
    val counts: DayCounts,
    val topApps: List<Pair<String, Int>>,
    val companies: Int,
)

/**
 * Today's and recent counts. Everything is stored on the phone only, and saved
 * a few seconds after changes so blocking never waits on disk.
 */
object Stats {
    private const val FILE = "unseen_stats"
    private const val KEY_DAYS = "days"               // date \t trackers \t ads \t scams \t gambling
    private const val KEY_APPS = "apps"               // date \t package \t count
    private const val KEY_DOMAINS = "domains"         // date \t package \t domain \t count
    private const val KEEP_DAYS = 60L
    private const val KEEP_APP_DAYS = 7L

    private lateinit var prefs: SharedPreferences
    private val saver = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var saveScheduled = false

    private val days = HashMap<String, DayCounts>()
    private val apps = HashMap<String, HashMap<String, Int>>()                       // date -> pkg -> n
    private val domains = HashMap<String, HashMap<String, HashMap<String, Int>>>()  // date -> pkg -> domain -> n

    private val _today = MutableStateFlow(DayCounts())
    val today: StateFlow<DayCounts> = _today.asStateFlow()

    /** package -> blocked today */
    private val _perApp = MutableStateFlow<Map<String, Int>>(emptyMap())
    val perApp: StateFlow<Map<String, Int>> = _perApp.asStateFlow()

    /** package -> (domain -> blocked today) */
    private val _perAppDomains = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    val perAppDomains: StateFlow<Map<String, Map<String, Int>>> = _perAppDomains.asStateFlow()

    /** Changes every time anything is recorded, so screens can refresh week figures. */
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs.getString(KEY_DAYS, "")!!.lines().forEach { line ->
            val p = line.split('\t')
            if (p.size == 5) days[p[0]] = DayCounts(n(p[1]), n(p[2]), n(p[3]), n(p[4]))
        }
        prefs.getString(KEY_APPS, "")!!.lines().forEach { line ->
            val p = line.split('\t')
            if (p.size == 3) apps.getOrPut(p[0]) { HashMap() }[p[1]] = n(p[2])
        }
        prefs.getString(KEY_DOMAINS, "")!!.lines().forEach { line ->
            val p = line.split('\t')
            if (p.size == 4) domains.getOrPut(p[0]) { HashMap() }.getOrPut(p[1]) { HashMap() }[p[2]] = n(p[3])
        }
        publish()
    }

    @Synchronized
    fun recordBlocked(appKey: String, domain: String, category: Category) {
        val day = todayKey()
        days[day] = (days[day] ?: DayCounts()).plus(category)
        val appsToday = apps.getOrPut(day) { HashMap() }
        appsToday[appKey] = (appsToday[appKey] ?: 0) + 1
        val forApp = domains.getOrPut(day) { HashMap() }.getOrPut(appKey) { HashMap() }
        forApp[domain] = (forApp[domain] ?: 0) + 1
        publish()
        scheduleSave()
    }

    /** Totals for the last [n] days including today. */
    @Synchronized
    fun lastDays(n: Int): DayCounts {
        val today = LocalDate.now()
        return (0 until n).fold(DayCounts()) { acc, i -> acc + (days[today.minusDays(i.toLong()).toString()] ?: DayCounts()) }
    }

    @Synchronized
    fun week(): WeekSummary {
        val keys = lastDayKeys(7)
        val appTotals = HashMap<String, Int>()
        val companyNames = HashSet<String>()
        for (k in keys) {
            apps[k]?.forEach { (pkg, n) -> appTotals[pkg] = (appTotals[pkg] ?: 0) + n }
            domains[k]?.values?.forEach { m ->
                m.keys.forEach { d -> companyNames.add(if (TrackerInfo.isKnown(d)) TrackerInfo.lookup(d).company else d) }
            }
        }
        return WeekSummary(
            counts = lastDays(7),
            topApps = appTotals.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
            companies = companyNames.size,
        )
    }

    /** Every tracker domain [packageName] tried to reach in the last 7 days. */
    @Synchronized
    fun weekDomains(packageName: String): Set<String> =
        lastDayKeys(7).flatMap { domains[it]?.get(packageName)?.keys ?: emptySet() }.toSet()

    private fun lastDayKeys(n: Int): List<String> {
        val today = LocalDate.now()
        return (0 until n).map { today.minusDays(it.toLong()).toString() }
    }

    private fun publish() {
        val day = todayKey()
        _today.value = days[day] ?: DayCounts()
        _perApp.value = HashMap(apps[day] ?: emptyMap<String, Int>())
        _perAppDomains.value = (domains[day] ?: emptyMap<String, Map<String, Int>>())
            .mapValues { HashMap(it.value) }
        _version.value = _version.value + 1
    }

    private fun scheduleSave() {
        if (saveScheduled) return
        saveScheduled = true
        saver.schedule({ save() }, 5, TimeUnit.SECONDS)
    }

    @Synchronized
    private fun save() {
        saveScheduled = false
        val today = LocalDate.now()
        val oldest = today.minusDays(KEEP_DAYS).toString()
        val oldestApp = today.minusDays(KEEP_APP_DAYS).toString()
        days.keys.removeAll { it < oldest }
        apps.keys.removeAll { it < oldestApp }
        domains.keys.removeAll { it < oldestApp }
        prefs.edit()
            .putString(KEY_DAYS, days.entries.joinToString("\n") { (d, c) ->
                "$d\t${c.trackers}\t${c.ads}\t${c.scams}\t${c.gambling}"
            })
            .putString(KEY_APPS, apps.entries.joinToString("\n") { (d, m) ->
                m.entries.joinToString("\n") { (pkg, n) -> "$d\t$pkg\t$n" }
            })
            .putString(KEY_DOMAINS, domains.entries.joinToString("\n") { (d, byApp) ->
                byApp.entries.joinToString("\n") { (pkg, m) ->
                    m.entries.joinToString("\n") { (dom, n) -> "$d\t$pkg\t$dom\t$n" }
                }
            })
            .apply()
    }

    /** Re-reads "today" (after midnight the screens should start from zero). */
    @Synchronized
    fun refresh() = publish()

    private fun n(text: String): Int = text.toIntOrNull() ?: 0

    private fun todayKey(): String = LocalDate.now().toString()
}
