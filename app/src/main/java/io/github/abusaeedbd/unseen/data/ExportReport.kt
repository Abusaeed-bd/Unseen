package io.github.abusaeedbd.unseen.data

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import io.github.abusaeedbd.unseen.dns.Blocklist
import io.github.abusaeedbd.unseen.dns.Category
import io.github.abusaeedbd.unseen.vpn.UnseenVpnService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds a JSON report of what Unseen saw (last 7 days in detail, 60 days of totals)
 * and opens the share sheet. Nothing is sent anywhere unless the user shares the file.
 */
object ExportReport {

    fun share(context: Context) {
        val file = write(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Unseen report ${LocalDate.now()}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun write(context: Context): File {
        val json = build(context)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "unseen-report-${LocalDate.now()}.json")
        file.writeText(json.toString(2))
        return file
    }

    private fun build(context: Context): JSONObject {
        val snap = Stats.snapshot()
        val zone = ZoneId.systemDefault()
        val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        val report = JSONObject()
        report.put("format", "unseen-report/1")
        report.put("generatedAt", iso.format(Instant.now().atZone(zone)))
        report.put("timeZone", zone.id)

        report.put("app", JSONObject().apply {
            put("version", appVersion(context))
            put("protection", UnseenVpnService.state.value.name)
            put("alwaysOn", UnseenVpnService.alwaysOn.value)
            put("blockWithoutVpn", UnseenVpnService.lockdown.value)
        })
        report.put("device", JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("android", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
        })
        report.put("settings", JSONObject().apply {
            put("blockAds", Prefs.blockAds(context))
            put("blockScams", Prefs.blockScams(context))
            put("blockGambling", Prefs.blockGambling(context))
            put("appLock", Prefs.lockEnabled(context))
            put("pausedApps", JSONArray(Prefs.pausedApps(context).sorted().map { appJson(context, it) }))
            put("allowedDomains", JSONArray(Prefs.allowedDomains(context).sorted()))
        })
        report.put("lists", JSONObject().apply {
            put("trackers", Blocklist.sizeOf(Category.TRACKER))
            put("ads", Blocklist.sizeOf(Category.AD))
            put("scams", Blocklist.sizeOf(Category.SCAM))
            put("gambling", Blocklist.sizeOf(Category.GAMBLING))
            val last = ListUpdater.lastUpdated.value
            put("lastUpdated", if (last == 0L) JSONObject.NULL else iso.format(Instant.ofEpochMilli(last).atZone(zone)))
        })

        // Totals per day (up to 60 days)
        report.put("days", JSONArray().apply {
            snap.days.forEach { (date, c) ->
                put(JSONObject().apply {
                    put("date", date)
                    put("total", c.total)
                    put("trackers", c.trackers)
                    put("ads", c.ads)
                    put("scams", c.scams)
                    put("gambling", c.gambling)
                    put("dataSavedBytesEstimate", c.dataSavedBytes)
                    snap.hours[date]?.let { put("perHour", JSONArray(it)) }
                })
            }
        })

        // Per app, per day, with every blocked domain (last 7 days)
        report.put("appsByDay", JSONArray().apply {
            snap.apps.forEach { (date, byApp) ->
                put(JSONObject().apply {
                    put("date", date)
                    put("apps", JSONArray().apply {
                        byApp.entries.sortedByDescending { it.value }.forEach { (pkg, count) ->
                            put(appJson(context, pkg).apply {
                                put("blocked", count)
                                put("domains", JSONArray().apply {
                                    snap.domains[date]?.get(pkg)?.entries?.sortedByDescending { it.value }?.forEach { (domain, n) ->
                                        val category = Blocklist.categoryOf(domain)
                                        val info = TrackerInfo.describe(domain, category)
                                        put(JSONObject().apply {
                                            put("domain", domain)
                                            put("count", n)
                                            put("category", category?.name ?: "UNKNOWN")
                                            put("company", info.company)
                                            put("knownCompany", TrackerInfo.isKnown(domain))
                                            put("allowedNow", Blocklist.isAllowed(domain))
                                        })
                                    }
                                })
                            })
                        }
                    })
                })
            }
        })

        // Activity history
        report.put("activity", JSONArray().apply {
            EventLog.events.value.forEach { e ->
                put(JSONObject().apply {
                    put("time", iso.format(Instant.ofEpochMilli(e.time).atZone(zone)))
                    put("text", e.text)
                    put("warning", e.warning)
                })
            }
        })
        return report
    }

    private fun appJson(context: Context, pkg: String) = JSONObject().apply {
        put("package", pkg)
        put("name", AppNames.label(context, pkg))
    }

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
