package io.github.abusaeedbd.unseen.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.abusaeedbd.unseen.data.Prefs
import io.github.abusaeedbd.unseen.ui.MainActivity

/**
 * The Unseen button in the Quick Settings panel. Off → on works in one tap.
 * Turning protection off asks for the fingerprint/PIN first when the app lock is on.
 */
class ProtectionTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        // Never let someone toggle it from the lock screen without unlocking the phone.
        if (isLocked) unlockAndRun { handleClick() } else handleClick()
    }

    private fun handleClick() {
        when (UnseenVpnService.state.value) {
            Protection.OFF -> {
                if (VpnService.prepare(this) != null) {
                    openApp(null) // first time: Android must ask for the VPN permission
                } else {
                    tryOrOpenApp(null) { UnseenVpnService.start(this) }
                }
            }
            Protection.PAUSED -> tryOrOpenApp(null) { UnseenVpnService.resume(this) }
            Protection.ON -> {
                if (Prefs.lockEnabled(this)) {
                    openApp(MainActivity.ACTION_REQUEST_OFF)
                } else {
                    tryOrOpenApp(MainActivity.ACTION_REQUEST_OFF) { UnseenVpnService.stop(this) }
                }
            }
        }
        refresh()
    }

    /** Starting a service from the panel can be refused on some phones; then open the app instead. */
    private fun tryOrOpenApp(fallbackAction: String?, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            openApp(fallbackAction)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(action: String?) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (action != null) intent.action = action
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 30, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val state = UnseenVpnService.state.value
        tile.label = "Unseen"
        tile.state = if (state == Protection.ON) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when (state) {
            Protection.ON -> "On"
            Protection.PAUSED -> "Paused"
            Protection.OFF -> "Off"
        }
        tile.updateTile()
    }
}
