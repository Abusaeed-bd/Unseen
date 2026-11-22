package io.github.abusaeedbd.unseen

import android.app.Application
import io.github.abusaeedbd.unseen.data.CrashLog
import io.github.abusaeedbd.unseen.data.EventLog
import io.github.abusaeedbd.unseen.data.ListUpdater
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.data.WeeklyReport
import io.github.abusaeedbd.unseen.dns.Blocklist

class UnseenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Stats.init(this)
        EventLog.load(this)
        ListUpdater.init(this)
        Blocklist.applySettings(this)
        // The ad list has ~80,000 entries: read it off the main thread so the app opens instantly.
        Thread({
            Blocklist.load(this)
            ListUpdater.schedule(this)
            WeeklyReport.schedule(this)
        }, "unseen-load").start()
    }
}
