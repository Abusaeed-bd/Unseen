package io.github.abusaeedbd.unseen

import android.app.Application
import io.github.abusaeedbd.unseen.data.CrashLog
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.dns.Blocklist

class UnseenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        Stats.init(this)
        Blocklist.load(this)
    }
}
