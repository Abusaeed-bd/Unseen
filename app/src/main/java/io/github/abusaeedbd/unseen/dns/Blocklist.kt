package io.github.abusaeedbd.unseen.dns

import android.content.Context
import android.util.Log
import io.github.abusaeedbd.unseen.data.Prefs
import java.io.File

/** Why a domain was blocked. */
enum class Category { SCAM, TRACKER, AD, GAMBLING }

/** Matches a domain and all its subdomains: "ads.example.com" blocks "x.ads.example.com". */
class DomainMatcher(domains: Collection<String>) {
    private val set: Set<String> = domains.mapTo(HashSet(domains.size * 2)) { it.trim().lowercase().trimEnd('.') }

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
        private val NOT_DOMAINS = setOf("localhost", "localhost.localdomain", "broadcasthost", "local", "0.0.0.0")

        /**
         * Accepts plain domain lines and hosts-file lines ("0.0.0.0 domain"); # starts a comment.
         * Entries that are IP addresses or local names are skipped.
         */
        fun parse(lines: Sequence<String>): DomainMatcher = DomainMatcher(
            lines.map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .map { it.split(' ', '\t').last { part -> part.isNotEmpty() } }
                .filter { it.contains('.') && it !in NOT_DOMAINS && !it.all { c -> c.isDigit() || c == '.' } }
                .toList()
        )
    }
}

/**
 * Spots fake bKash / Nagad pages, a common scam in Bangladesh, even before they reach any
 * public list: "bkash-bonus-offer.xyz", "nagad-gift.top" and the like.
 */
object Lookalike {
    private val OFFICIAL = listOf("bkash.com", "nagad.com.bd")
    private val BAIT = listOf("bonus", "offer", "gift", "reward", "cashback", "prize", "lottery", "free", "verify", "login")

    fun isScam(name: String): Boolean {
        val n = name.lowercase().trimEnd('.')
        if (OFFICIAL.any { n == it || n.endsWith(".$it") }) return false
        if ("bkash" in n) return true
        if ("nagad" in n && BAIT.any { it in n }) return true
        return false
    }
}

/**
 * All the lists in use. Trackers ship inside the app; ads, scams and gambling lists are
 * downloaded and refreshed daily by [io.github.abusaeedbd.unseen.data.ListUpdater].
 */
object Blocklist {
    private val empty = DomainMatcher(emptyList())

    @Volatile private var trackers = empty
    @Volatile private var ads = empty
    @Volatile private var scams = empty
    @Volatile private var gambling = empty
    @Volatile private var allowed: Set<String> = emptySet()

    @Volatile private var adsOn = true
    @Volatile private var scamsOn = true
    @Volatile private var gamblingOn = false

    fun sizeOf(category: Category): Int = when (category) {
        Category.TRACKER -> trackers.size
        Category.AD -> ads.size
        Category.SCAM -> scams.size
        Category.GAMBLING -> gambling.size
    }

    fun listFile(context: Context, category: Category): File =
        File(File(context.filesDir, "lists"), "${category.name.lowercase()}.txt")

    /** Loads everything. Can take a second with the big lists: call it off the main thread. */
    fun load(context: Context) {
        trackers = try {
            context.assets.open("blocklist.txt").bufferedReader().useLines { DomainMatcher.parse(it) }
        } catch (e: java.io.IOException) {
            Log.e("Unseen", "blocklist.txt missing from assets", e)
            empty
        }
        for (category in listOf(Category.AD, Category.SCAM, Category.GAMBLING)) reload(context, category)
        applySettings(context)
    }

    /** Reads one downloaded list again after an update. */
    fun reload(context: Context, category: Category) {
        val file = listFile(context, category)
        val matcher = if (file.exists()) {
            try {
                file.bufferedReader().useLines { DomainMatcher.parse(it) }
            } catch (e: Exception) {
                Log.e("Unseen", "Could not read ${file.name}", e)
                empty
            }
        } else empty
        when (category) {
            Category.AD -> ads = matcher
            Category.SCAM -> scams = matcher
            Category.GAMBLING -> gambling = matcher
            Category.TRACKER -> trackers = matcher
        }
    }

    /** Picks up switches from Settings (block ads, scam protection, gambling, allowed sites). */
    fun applySettings(context: Context) {
        adsOn = Prefs.blockAds(context)
        scamsOn = Prefs.blockScams(context)
        gamblingOn = Prefs.blockGambling(context)
        allowed = Prefs.allowedDomains(context)
    }

    /** Returns why [name] should be blocked, or null to let it through. */
    fun classify(name: String): Category? {
        if (allowed.isNotEmpty() && isAllowed(name)) return null
        if (scamsOn && (scams.matches(name) || Lookalike.isScam(name))) return Category.SCAM
        if (trackers.matches(name)) return Category.TRACKER
        if (adsOn && ads.matches(name)) return Category.AD
        if (gamblingOn && gambling.matches(name)) return Category.GAMBLING
        return null
    }

    /** The category a domain belongs to, ignoring switches and allowed sites (for showing details). */
    fun categoryOf(name: String): Category? = when {
        scams.matches(name) || Lookalike.isScam(name) -> Category.SCAM
        trackers.matches(name) -> Category.TRACKER
        ads.matches(name) -> Category.AD
        gambling.matches(name) -> Category.GAMBLING
        else -> null
    }

    fun isAllowed(name: String): Boolean {
        var n = name.lowercase().trimEnd('.')
        while (n.isNotEmpty()) {
            if (n in allowed) return true
            val dot = n.indexOf('.')
            if (dot < 0) return false
            n = n.substring(dot + 1)
        }
        return false
    }
}
