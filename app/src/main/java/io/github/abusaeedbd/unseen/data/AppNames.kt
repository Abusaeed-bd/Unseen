package io.github.abusaeedbd.unseen.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/** Turns the Linux user id that owns a connection into a package name and a readable label. */
object AppNames {
    private val packageByUid = ConcurrentHashMap<Int, String>()
    private val labelByPackage = ConcurrentHashMap<String, String>()

    fun packageFor(context: Context, uid: Int): String = packageByUid.getOrPut(uid) {
        when {
            uid == Process.SYSTEM_UID -> "android"
            uid < 0 -> "unknown"
            else -> context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "uid:$uid"
        }
    }

    fun label(context: Context, packageName: String): String = labelByPackage.getOrPut(packageName) {
        when {
            packageName == "android" -> "Android system"
            packageName == "unknown" || packageName.startsWith("uid:") -> "Unknown app"
            else -> try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }
    }
}
