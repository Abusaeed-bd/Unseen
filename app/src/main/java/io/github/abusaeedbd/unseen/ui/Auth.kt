package io.github.abusaeedbd.unseen.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.abusaeedbd.unseen.data.Prefs

/**
 * Asks for the phone's own fingerprint / face / PIN / pattern, using Android's system
 * dialog. Unseen never sees or stores any of it.
 */
object Auth {

    /** True when the phone has a screen lock, which the app lock needs. */
    fun deviceIsSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Runs [onSuccess] at once if the app lock is off; otherwise only after the user confirms. */
    fun guard(activity: Activity, reason: String, onSuccess: () -> Unit) {
        if (!Prefs.lockEnabled(activity) || !deviceIsSecure(activity)) {
            onSuccess()
            return
        }
        confirm(activity, reason, onSuccess)
    }

    /** Always asks (used to switch the lock itself on or off). */
    fun confirm(activity: Activity, reason: String, onSuccess: () -> Unit) {
        if (!deviceIsSecure(activity)) {
            onSuccess()
            return
        }
        val builder = BiometricPrompt.Builder(activity)
            .setTitle("Confirm it's you")
            .setSubtitle(reason)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            },
        )
    }
}

/** Lets any screen ask for confirmation: LocalAuth.current("Pause bKash") { ... } */
val LocalAuth = staticCompositionLocalOf<(String, () -> Unit) -> Unit> { { _, action -> action() } }
