package io.github.abusaeedbd.unseen

import android.app.Application
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.dns.Blocklist

class UnseenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Stats.init(this)
        Blocklist.load(this)
    }
}
