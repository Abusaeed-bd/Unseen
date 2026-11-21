package io.github.abusaeedbd.unseen.dns

import android.content.Context

/** Matches a domain and all its subdomains: "ads.example.com" blocks "x.ads.example.com". */
class DomainMatcher(domains: Collection<String>) {
    private val set: Set<String> = domains.mapTo(HashSet()) { it.trim().lowercase().trimEnd('.') }

    val size: Int get() = set.size

    fun matches(name: String): Boolean {
        var n = name.lowercase().trimEnd('.')
        while (n.isNotEmpty()) {
            if (n in set) return true
            val dot = n.indexOf('.')
            if (dot < 0) return false
            n = n.substring(dot + 1)
        }
        return false
    }

    companion object {
        /** Accepts plain domain lines and hosts-file lines ("0.0.0.0 domain"); # starts a comment. */
        fun parse(lines: Sequence<String>): DomainMatcher = DomainMatcher(
            lines.map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .map { it.split(Regex("\\s+")).last() }
                .filter { it.contains('.') }
                .toList()
        )
    }
}

/** The list in use, loaded once from assets/blocklist.txt. */
object Blocklist {
    @Volatile
    private var matcher = DomainMatcher(emptyList())

    val size: Int get() = matcher.size

    fun load(context: Context) {
        matcher = try {
            context.assets.open("blocklist.txt").bufferedReader().useLines { DomainMatcher.parse(it) }
        } catch (e: java.io.IOException) {
            android.util.Log.e("Unseen", "blocklist.txt missing from assets", e)
            DomainMatcher(emptyList())
        }
    }

    fun isBlocked(name: String): Boolean = matcher.matches(name)
}
