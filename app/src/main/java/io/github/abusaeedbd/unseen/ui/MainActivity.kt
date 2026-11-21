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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.abusaeedbd.unseen.vpn.UnseenVpnService

class MainActivity : ComponentActivity() {

    private val askVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) UnseenVpnService.start(this)
        }

    private val askNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The "Unseen is protecting you" notification needs this on Android 13+.
        if (savedInstanceState == null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            UnseenTheme {
                UnseenScreen(
                    onToggle = ::toggleProtection,
                    onAppSettingsChanged = ::reapplyIfRunning,
                    isPrivateDnsStrict = { isPrivateDnsStrict(this) },
                    openNetworkSettings = {
                        startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    },
                )
            }
        }
    }

    private fun toggleProtection() {
        if (UnseenVpnService.running.value) {
            UnseenVpnService.stop(this)
            return
        }
        // Android shows its own "Connection request" dialog the first time.
        val consent = VpnService.prepare(this)
        if (consent != null) askVpnPermission.launch(consent) else UnseenVpnService.start(this)
    }

    private fun reapplyIfRunning() {
        if (UnseenVpnService.running.value) UnseenVpnService.start(this)
    }
}

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
