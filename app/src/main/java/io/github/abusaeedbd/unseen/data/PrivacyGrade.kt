package io.github.abusaeedbd.unseen.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * A simple A–F privacy grade per app, from two things Unseen can see:
 * how many tracker companies the app contacted in the last 7 days, and how many
 * sensitive permissions it holds. It is a guide, not a verdict: a camera app needs the camera.
 */
object PrivacyGrade {

    data class Grade(
        val letter: Char,
        val score: Int,
        val companies: List<String>,
        val permissions: List<String>,
    )

    private val SENSITIVE: List<Pair<String, List<String>>> = listOf(
        "Location" to listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        "Contacts" to listOf(Manifest.permission.READ_CONTACTS),
        "Microphone" to listOf(Manifest.permission.RECORD_AUDIO),
        "Camera" to listOf(Manifest.permission.CAMERA),
        "SMS" to listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
        "Call logs" to listOf(Manifest.permission.READ_CALL_LOG),
        "Phone" to listOf(Manifest.permission.READ_PHONE_STATE),
    )

    const val EXPLANATION =
        "The grade starts at 100. Each tracker company the app contacted in the last 7 days " +
            "takes off 12 points (up to 5 companies), and each sensitive permission it holds " +
            "takes off 8 points (up to 5). A: 85+, B: 70+, C: 55+, D: 40+, F: below 40."

    fun of(context: Context, packageName: String): Grade {
        val companies = Stats.weekDomains(packageName)
            .map { TrackerInfo.lookup(it).company }
            .distinct()
            .sorted()
        val permissions = grantedSensitive(context, packageName)
        val score = (100 - 12 * minOf(companies.size, 5) - 8 * minOf(permissions.size, 5)).coerceIn(0, 100)
        val letter = when {
            score >= 85 -> 'A'
            score >= 70 -> 'B'
            score >= 55 -> 'C'
            score >= 40 -> 'D'
            else -> 'F'
        }
        return Grade(letter, score, companies, permissions)
    }

    @Suppress("DEPRECATION")
    private fun grantedSensitive(context: Context, packageName: String): List<String> {
        val info: PackageInfo = try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: Exception) {
            return emptyList()
        }
        val requested = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags ?: return emptyList()
        val granted = requested.indices
            .filter { flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
            .map { requested[it] }
            .toSet()
        return SENSITIVE.filter { (_, perms) -> perms.any { it in granted } }.map { it.first }
    }
}
