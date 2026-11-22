package io.github.abusaeedbd.unseen.data

/** Who runs a tracker domain and what it collects, in plain words for the user. */
object TrackerInfo {

    data class Tracker(val company: String, val purpose: String)

    private const val ADS = "Shows you targeted ads and tracks which ads you see"
    private const val ANALYTICS = "Records how you use the app: taps, screens, time spent"
    private const val ATTRIBUTION = "Tracks which ad made you install or open the app"
    private const val SESSION = "Records your screen and taps inside the app"

    private val bySuffix: Map<String, Tracker> = mapOf(
        "google-analytics.com" to Tracker("Google Analytics", ANALYTICS),
        "app-measurement.com" to Tracker("Google Firebase Analytics", ANALYTICS),
        "firebaselogging-pa.googleapis.com" to Tracker("Google Firebase", ANALYTICS),
        "googleadservices.com" to Tracker("Google Ads", ADS),
        "googlesyndication.com" to Tracker("Google AdSense", ADS),
        "doubleclick.net" to Tracker("Google DoubleClick", ADS),
        "adservice.google.com" to Tracker("Google Ads", ADS),
        "admob.com" to Tracker("Google AdMob", ADS),
        "an.facebook.com" to Tracker("Meta Audience Network", ADS),
        "connect.facebook.net" to Tracker("Meta Pixel", ANALYTICS),
        "pixel.facebook.com" to Tracker("Meta Pixel", ANALYTICS),
        "analytics.tiktok.com" to Tracker("TikTok Analytics", ANALYTICS),
        "analytics-sg.tiktok.com" to Tracker("TikTok Analytics", ANALYTICS),
        "ads.tiktok.com" to Tracker("TikTok Ads", ADS),
        "pangleglobal.com" to Tracker("Pangle (ByteDance)", ADS),
        "pangle.io" to Tracker("Pangle (ByteDance)", ADS),
        "appsflyer.com" to Tracker("AppsFlyer", ATTRIBUTION),
        "appsflyersdk.com" to Tracker("AppsFlyer", ATTRIBUTION),
        "adjust.com" to Tracker("Adjust", ATTRIBUTION),
        "adjust.net.in" to Tracker("Adjust", ATTRIBUTION),
        "kochava.com" to Tracker("Kochava", ATTRIBUTION),
        "singular.net" to Tracker("Singular", ATTRIBUTION),
        "branch.io" to Tracker("Branch", ATTRIBUTION),
        "mixpanel.com" to Tracker("Mixpanel", ANALYTICS),
        "amplitude.com" to Tracker("Amplitude", ANALYTICS),
        "segment.io" to Tracker("Segment", ANALYTICS),
        "mparticle.com" to Tracker("mParticle", ANALYTICS),
        "flurry.com" to Tracker("Flurry (Yahoo)", ANALYTICS),
        "appmetrica.yandex.net" to Tracker("Yandex AppMetrica", ANALYTICS),
        "mobile.yandex.net" to Tracker("Yandex", ADS),
        "clevertap-prod.com" to Tracker("CleverTap", ANALYTICS),
        "wzrkt.com" to Tracker("CleverTap", ANALYTICS),
        "moengage.com" to Tracker("MoEngage", ANALYTICS),
        "webengage.com" to Tracker("WebEngage", ANALYTICS),
        "hotjar.com" to Tracker("Hotjar", SESSION),
        "hotjar.io" to Tracker("Hotjar", SESSION),
        "smartlook.com" to Tracker("Smartlook", SESSION),
        "uxcam.com" to Tracker("UXCam", SESSION),
        "appcenter.ms" to Tracker("Microsoft App Center", ANALYTICS),
        "aria.microsoft.com" to Tracker("Microsoft telemetry", ANALYTICS),
        "events.data.microsoft.com" to Tracker("Microsoft telemetry", ANALYTICS),
        "vortex.data.microsoft.com" to Tracker("Microsoft telemetry", ANALYTICS),
        "telemetry.microsoft.com" to Tracker("Microsoft telemetry", ANALYTICS),
        "ads-twitter.com" to Tracker("X (Twitter) Ads", ADS),
        "ads-api.twitter.com" to Tracker("X (Twitter) Ads", ADS),
        "analytics.twitter.com" to Tracker("X (Twitter) Analytics", ANALYTICS),
        "crashlytics.com" to Tracker("Google Crashlytics", "Sends crash and usage reports"),
        "onesignal.com" to Tracker("OneSignal", ANALYTICS),
        "bugsnag.com" to Tracker("Bugsnag", "Sends crash and usage reports"),
        "sentry.io" to Tracker("Sentry", "Sends crash and usage reports"),
        "newrelic.com" to Tracker("New Relic", ANALYTICS),
        "nr-data.net" to Tracker("New Relic", ANALYTICS),
        "applovin.com" to Tracker("AppLovin", ADS),
        "applvn.com" to Tracker("AppLovin", ADS),
        "unity3d.com" to Tracker("Unity Ads", ADS),
        "ironsrc.com" to Tracker("ironSource", ADS),
        "ironsrc.mobi" to Tracker("ironSource", ADS),
        "supersonicads.com" to Tracker("ironSource", ADS),
        "vungle.com" to Tracker("Vungle (Liftoff)", ADS),
        "liftoff.io" to Tracker("Liftoff", ADS),
        "chartboost.com" to Tracker("Chartboost", ADS),
        "adcolony.com" to Tracker("AdColony", ADS),
        "inmobi.com" to Tracker("InMobi", ADS),
        "startapp.com" to Tracker("Start.io", ADS),
        "start.io" to Tracker("Start.io", ADS),
        "mintegral.com" to Tracker("Mintegral", ADS),
        "mintegral.net" to Tracker("Mintegral", ADS),
        "rayjump.com" to Tracker("Mintegral", ADS),
        "smaato.net" to Tracker("Smaato", ADS),
        "fyber.com" to Tracker("Fyber (DT Exchange)", ADS),
        "inner-active.mobi" to Tracker("Fyber (DT Exchange)", ADS),
        "tapjoy.com" to Tracker("Tapjoy", ADS),
        "tapjoyads.com" to Tracker("Tapjoy", ADS),
        "mopub.com" to Tracker("MoPub", ADS),
        "bidmachine.io" to Tracker("BidMachine", ADS),
        "yandexadexchange.net" to Tracker("Yandex Ads", ADS),
        "adnxs.com" to Tracker("Xandr (Microsoft)", ADS),
        "amazon-adsystem.com" to Tracker("Amazon Ads", ADS),
        "criteo.com" to Tracker("Criteo", ADS),
        "criteo.net" to Tracker("Criteo", ADS),
        "pubmatic.com" to Tracker("PubMatic", ADS),
        "rubiconproject.com" to Tracker("Magnite", ADS),
        "openx.net" to Tracker("OpenX", ADS),
        "taboola.com" to Tracker("Taboola", ADS),
        "outbrain.com" to Tracker("Outbrain", ADS),
        "moatads.com" to Tracker("Moat (Oracle)", ANALYTICS),
        "scorecardresearch.com" to Tracker("Comscore", ANALYTICS),
        "quantserve.com" to Tracker("Quantcast", ADS),
        "adsrvr.org" to Tracker("The Trade Desk", ADS),
        "bidswitch.net" to Tracker("BidSwitch", ADS),
        "casalemedia.com" to Tracker("Index Exchange", ADS),
        "media.net" to Tracker("Media.net", ADS),
        "yieldmo.com" to Tracker("Yieldmo", ADS),
        "3lift.com" to Tracker("TripleLift", ADS),
        "sharethrough.com" to Tracker("Sharethrough", ADS),
        "teads.tv" to Tracker("Teads", ADS),
    )

    /** What to tell the user about a blocked domain, using why it was blocked. */
    fun describe(domain: String, category: io.github.abusaeedbd.unseen.dns.Category?): Tracker {
        val known = lookup(domain)
        return when (category) {
            io.github.abusaeedbd.unseen.dns.Category.SCAM ->
                Tracker("Dangerous site", "Known scam, phishing or malware site")
            io.github.abusaeedbd.unseen.dns.Category.GAMBLING ->
                Tracker("Gambling site", "Betting or casino site")
            io.github.abusaeedbd.unseen.dns.Category.AD ->
                if (known !== UNKNOWN) known
                else Tracker("Ad or tracking server", "On public ad-blocking lists: shows ads or collects data about you")
            else -> known
        }
    }

    private val UNKNOWN = Tracker("Tracker", "Collects data about you for ads or analytics")

    /** True when we know which company runs this domain. */
    fun isKnown(domain: String): Boolean = lookup(domain) !== UNKNOWN

    fun lookup(domain: String): Tracker {
        var d = domain.lowercase().trimEnd('.')
        while (d.isNotEmpty()) {
            bySuffix[d]?.let { return it }
            val dot = d.indexOf('.')
            if (dot < 0) break
            d = d.substring(dot + 1)
        }
        return UNKNOWN
    }
}
