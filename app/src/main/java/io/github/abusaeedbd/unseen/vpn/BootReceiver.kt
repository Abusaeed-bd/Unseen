package io.github.abusaeedbd.unseen.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import io.github.abusaeedbd.unseen.data.Prefs

/** Turns protection back on after a reboot or an app update, if it was on before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // prepare() returns null only when the user has already allowed Unseen's VPN.
        if (Prefs.wantOn(context) && VpnService.prepare(context) == null) {
            UnseenVpnService.start(context)
        }
    }
}
