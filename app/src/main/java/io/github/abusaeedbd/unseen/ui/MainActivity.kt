package io.github.abusaeedbd.unseen.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import io.github.abusaeedbd.unseen.data.EventLog
import io.github.abusaeedbd.unseen.data.Prefs
import io.github.abusaeedbd.unseen.data.WeeklyReport
import io.github.abusaeedbd.unseen.dns.Blocklist
import io.github.abusaeedbd.unseen.vpn.Protection
import io.github.abusaeedbd.unseen.vpn.UnseenVpnService

class MainActivity : ComponentActivity() {

    companion object {
        /** Sent by the Quick Settings tile: turn protection off after confirming. */
        const val ACTION_REQUEST_OFF = "io.github.abusaeedbd.unseen.REQUEST_OFF"
    }

    private val askVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) UnseenVpnService.start(this)
        }

    private val askNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Alerts ("protection was turned off", "dangerous site blocked") need this on Android 13+.
        if (savedInstanceState == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            UnseenTheme {
                CompositionLocalProvider(LocalAuth provides { reason, action -> Auth.guard(this, reason, action) }) {
                    UnseenScreen(
                        actions = ScreenActions(
                            turnOn = ::turnOn,
                            turnOff = { Auth.guard(this, "Turn off protection") { UnseenVpnService.stop(this) } },
                            pause = { minutes ->
                                Auth.guard(this, "Pause protection for $minutes minutes") {
                                    UnseenVpnService.pause(this, minutes)
                                }
                            },
                            resume = { UnseenVpnService.resume(this) },
                            appSettingsChanged = ::reapplyIfRunning,
                            filtersChanged = { Blocklist.applySettings(this) },
                            setAppLock = ::setAppLock,
                            share = { ShareCard.share(this) },
                            isPrivateDnsStrict = { isPrivateDnsStrict(this) },
                            openNetworkSettings = { open(Settings.ACTION_WIRELESS_SETTINGS) },
                            openVpnSettings = { open(Settings.ACTION_VPN_SETTINGS) },
                        )
                    )
                }
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_REQUEST_OFF -> if (UnseenVpnService.state.value != Protection.OFF) {
                Auth.guard(this, "Turn off protection") { UnseenVpnService.stop(this) }
            }
            WeeklyReport.ACTION_SHARE -> ShareCard.share(this)
        }
    }

    private fun turnOn() {
        // Android shows its own "Connection request" dialog the first time.
        val consent = VpnService.prepare(this)
        if (consent != null) askVpnPermission.launch(consent) else UnseenVpnService.start(this)
    }

    private fun reapplyIfRunning() {
        if (UnseenVpnService.state.value == Protection.ON) UnseenVpnService.start(this)
    }

    /** Switching the lock on or off always asks, so nobody can quietly remove it. */
    private fun setAppLock(on: Boolean, done: (Boolean) -> Unit) {
        if (on && !Auth.deviceIsSecure(this)) {
            Toast.makeText(this, "Set a screen lock (PIN, pattern or fingerprint) on your phone first.", Toast.LENGTH_LONG).show()
            done(false)
            return
        }
        Auth.confirm(this, if (on) "Turn on app lock" else "Turn off app lock") {
            Prefs.setLockEnabled(this, on)
            EventLog.add(this, if (on) "App lock turned on" else "App lock turned off", warning = !on)
            done(on)
        }
    }

    private fun open(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

/** Everything the screens can ask the app to do. */
class ScreenActions(
    val turnOn: () -> Unit,
    val turnOff: () -> Unit,
    val pause: (minutes: Int) -> Unit,
    val resume: () -> Unit,
    val appSettingsChanged: () -> Unit,
    val filtersChanged: () -> Unit,
    val setAppLock: (on: Boolean, done: (Boolean) -> Unit) -> Unit,
    val share: () -> Unit,
    val isPrivateDnsStrict: () -> Boolean,
    val openNetworkSettings: () -> Unit,
    val openVpnSettings: () -> Unit,
)

/**
 * True when the phone's Private DNS is set to a specific provider. Android then sends
 * DNS straight to that provider, encrypted, and Unseen cannot see or block it.
 */
@Suppress("DEPRECATION") // allNetworks: still the simplest way to see the non-VPN networks
fun isPrivateDnsStrict(context: Context): Boolean = try {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    cm != null && cm.allNetworks.any { network ->
        val caps = cm.getNetworkCapabilities(network)
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        !isVpn && cm.getLinkProperties(network)?.privateDnsServerName != null
    }
} catch (e: Exception) {
    false
}
