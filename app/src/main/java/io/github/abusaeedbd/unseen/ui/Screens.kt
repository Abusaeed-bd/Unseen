package io.github.abusaeedbd.unseen.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abusaeedbd.unseen.R
import io.github.abusaeedbd.unseen.data.AppNames
import io.github.abusaeedbd.unseen.data.CrashLog
import io.github.abusaeedbd.unseen.data.DayCounts
import io.github.abusaeedbd.unseen.data.EventLog
import io.github.abusaeedbd.unseen.data.ListUpdater
import io.github.abusaeedbd.unseen.data.Prefs
import io.github.abusaeedbd.unseen.data.PrivacyGrade
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.data.TrackerInfo
import io.github.abusaeedbd.unseen.data.formatBytes
import io.github.abusaeedbd.unseen.dns.Blocklist
import io.github.abusaeedbd.unseen.dns.Category
import io.github.abusaeedbd.unseen.vpn.Protection
import io.github.abusaeedbd.unseen.vpn.UnseenVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------- navigation

@Composable
fun UnseenScreen(actions: ScreenActions) {
    val context = LocalContext.current
    var onboarded by remember { mutableStateOf(Prefs.onboarded(context)) }
    // "home", "apps", "settings" or "app:<package>"
    var screen by rememberSaveable { mutableStateOf("home") }
    BackHandler(enabled = screen != "home") { screen = "home" }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            when {
                !onboarded -> Onboarding {
                    Prefs.setOnboarded(context)
                    onboarded = true
                }
                screen == "apps" -> AppsScreen(
                    onBack = { screen = "home" },
                    onChanged = actions.appSettingsChanged,
                    onOpenApp = { screen = "app:$it" },
                )
                screen == "settings" -> SettingsScreen(actions, onBack = { screen = "home" })
                screen.startsWith("app:") -> AppDetailScreen(
                    packageName = screen.removePrefix("app:"),
                    onBack = { screen = "home" },
                    actions = actions,
                )
                else -> HomeScreen(
                    actions = actions,
                    onOpenApps = { screen = "apps" },
                    onOpenApp = { screen = "app:$it" },
                    onOpenSettings = { screen = "settings" },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- welcome

@Composable
private fun Onboarding(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        GhostCircle(color = Accent, size = 120.dp)
        Spacer(Modifier.height(28.dp))
        Text("Welcome to Unseen", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Apps can't watch what they can't see.", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(32.dp))
        WelcomePoint("Blocks trackers, ads and scam sites", "Inside all your apps, without turning off any permission. Your apps keep working.")
        WelcomePoint("Nothing leaves your phone", "Unseen works on your phone only. No account, no servers, no ads.")
        WelcomePoint("You'll see a key icon at the top", "Android shows it whenever a VPN is on. Here, it means Unseen is protecting you.")
        WelcomePoint("Banking apps keep working", "bKash, Nagad and Rocket are left out from the start, because they refuse to open with a VPN on.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Get started", fontSize = 17.sp)
        }
    }
}

@Composable
private fun WelcomePoint(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(Accent)
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(body, color = Muted, fontSize = 14.sp)
        }
    }
}

// ---------------------------------------------------------------- home

@Composable
private fun HomeScreen(
    actions: ScreenActions,
    onOpenApps: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val state by UnseenVpnService.state.collectAsStateWithLifecycle()
    val pausedUntil by UnseenVpnService.pausedUntil.collectAsStateWithLifecycle()
    val today by Stats.today.collectAsStateWithLifecycle()
    val perApp by Stats.perApp.collectAsStateWithLifecycle()
    val version by Stats.version.collectAsStateWithLifecycle()
    val week = remember(version) { Stats.week() }

    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    var strictDns by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        strictDns = actions.isPrivateDnsStrict()
        Stats.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(state) { strictDns = actions.isPrivateDnsStrict() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Unseen", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }
        Text(
            "Apps can't watch what they can't see.",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(),
        )

        crash?.let { report ->
            Spacer(Modifier.height(16.dp))
            CrashCard(report, onDismiss = {
                CrashLog.clear(context)
                crash = null
            })
        }

        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        Protection.ON -> Accent
                        Protection.PAUSED -> Warning.copy(alpha = 0.35f)
                        Protection.OFF -> CardBg
                    }
                )
                .clickable(role = Role.Button) {
                    when (state) {
                        Protection.OFF -> actions.turnOn()
                        Protection.ON -> actions.turnOff()
                        Protection.PAUSED -> actions.resume()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stat_unseen),
                contentDescription = when (state) {
                    Protection.ON -> "Turn protection off"
                    Protection.PAUSED -> "Resume protection"
                    Protection.OFF -> "Turn protection on"
                },
                tint = Color.White,
                modifier = Modifier.size(96.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            when (state) {
                Protection.ON -> "Protection is ON"
                Protection.PAUSED -> "Protection is PAUSED"
                Protection.OFF -> "Protection is OFF"
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when (state) {
                Protection.ON -> "Tap to turn off"
                Protection.PAUSED -> "Comes back at ${clock(pausedUntil)} · tap to resume now"
                Protection.OFF -> "Tap to turn on"
            },
            color = Muted,
            textAlign = TextAlign.Center,
        )
        if (state == Protection.ON) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { actions.pause(10) }) { Text("Pause for 10 minutes") }
            Text(
                "Handy for banking apps that refuse to open with a VPN on.",
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }

        if (strictDns) {
            Spacer(Modifier.height(20.dp))
            PrivateDnsWarning(actions.openNetworkSettings)
        }

        Spacer(Modifier.height(28.dp))
        TodayCard(today)

        Spacer(Modifier.height(16.dp))
        InfoCard {
            Text("Last 7 days", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("%,d blocked".format(week.counts.total), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "${week.companies} companies kept out · about ${formatBytes(week.counts.dataSavedBytes)} saved",
                color = Muted,
                fontSize = 14.sp,
            )
            if (week.counts.total > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = actions.share) { Text("Share my week") }
            }
        }

        if (perApp.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            InfoCard {
                Text("Most tracker requests today", fontWeight = FontWeight.SemiBold)
                Text("Tap an app to see who it tried to send data to.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                perApp.entries.sortedByDescending { it.value }.take(5).forEach { (pkg, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "Show trackers") { onOpenApp(pkg) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(pkg, 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            AppNames.label(context, pkg),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("$count", fontWeight = FontWeight.SemiBold)
                        Text("  ›", color = Muted)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Manage apps")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TodayCard(today: DayCounts) {
    InfoCard {
        Text("%,d".format(today.total), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("blocked today", color = Muted)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CountChip("Trackers", today.trackers)
            CountChip("Ads", today.ads)
            CountChip("Scams", today.scams, if (today.scams > 0) Warning else null)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "About ${formatBytes(today.dataSavedBytes)} of mobile data saved",
            fontSize = 14.sp,
        )
        Text(
            "Estimate: about 3 KB per blocked tracker and 30 KB per blocked ad.",
            color = Muted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun CountChip(label: String, count: Int, highlight: Color? = null) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Ink)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text("$label $count", fontSize = 13.sp, color = highlight ?: Color.White)
    }
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsScreen(actions: ScreenActions, onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = LocalAuth.current
    val scope = rememberCoroutineScope()
    val events by EventLog.events.collectAsStateWithLifecycle()
    val updating by ListUpdater.updating.collectAsStateWithLifecycle()
    val lastUpdated by ListUpdater.lastUpdated.collectAsStateWithLifecycle()
    val state by UnseenVpnService.state.collectAsStateWithLifecycle()
    val alwaysOn by UnseenVpnService.alwaysOn.collectAsStateWithLifecycle()
    val lockdown by UnseenVpnService.lockdown.collectAsStateWithLifecycle()

    var blockAds by remember { mutableStateOf(Prefs.blockAds(context)) }
    var blockScams by remember { mutableStateOf(Prefs.blockScams(context)) }
    var blockGambling by remember { mutableStateOf(Prefs.blockGambling(context)) }
    var lockOn by remember { mutableStateOf(Prefs.lockEnabled(context)) }
    var allowed by remember { mutableStateOf(Prefs.allowedDomains(context).sorted()) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    /** Turning a protection off asks for confirmation; turning it on never does. */
    fun toggle(on: Boolean, what: String, apply: (Boolean) -> Unit) {
        val run = {
            apply(on)
            actions.filtersChanged()
            EventLog.add(context, "$what turned ${if (on) "on" else "off"}", warning = !on)
        }
        if (on) run() else auth("Turn off $what", run)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackButton(onBack)
            ScreenTitle("Settings")

            SectionTitle("What to block")
            SettingSwitch(
                title = "Trackers",
                subtitle = "Always on. ${Blocklist.sizeOf(Category.TRACKER)} tracker services inside apps.",
                checked = true,
                enabled = false,
            ) { }
            SettingSwitch(
                title = "Ads",
                subtitle = listInfo(Blocklist.sizeOf(Category.AD), "ad and malware sites"),
                checked = blockAds,
            ) { on -> toggle(on, "Ad blocking") { Prefs.setBlockAds(context, it); blockAds = it } }
            SettingSwitch(
                title = "Scam & dangerous sites",
                subtitle = listInfo(Blocklist.sizeOf(Category.SCAM), "phishing and malware sites") +
                    " Also fake bKash and Nagad pages.",
                checked = blockScams,
            ) { on -> toggle(on, "Scam protection") { Prefs.setBlockScams(context, it); blockScams = it } }
            SettingSwitch(
                title = "Gambling & betting sites",
                subtitle = listInfo(Blocklist.sizeOf(Category.GAMBLING), "gambling sites"),
                checked = blockGambling,
            ) { on -> toggle(on, "Gambling blocking") { Prefs.setBlockGambling(context, it); blockGambling = it } }

            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    updateMessage ?: if (lastUpdated == 0L) "Lists not downloaded yet"
                    else "Lists updated ${dayAndClock(lastUpdated)} · they update daily",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (updating) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = {
                        scope.launch {
                            val ok = ListUpdater.updateAll(context)
                            updateMessage = if (ok) null else "Update failed. Check your internet and try again."
                            actions.filtersChanged()
                        }
                    }) { Text("Update now") }
                }
            }

            SectionTitle("Security")
            SettingSwitch(
                title = "App lock",
                subtitle = if (Auth.deviceIsSecure(context))
                    "Ask for your fingerprint or PIN before protection can be turned off or apps paused."
                else "Set a screen lock on your phone first.",
                checked = lockOn,
                enabled = Auth.deviceIsSecure(context),
            ) { on -> actions.setAppLock(on) { result -> lockOn = result } }

            InfoCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Keep Unseen always on", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        state != Protection.ON -> "Turn protection on to check this setting."
                        alwaysOn -> "Always-on VPN is ON. Unseen comes back by itself if it is closed."
                        else -> "Turn on Always-on VPN so Unseen restarts by itself if someone closes it."
                    },
                    color = if (state == Protection.ON && alwaysOn) Good else Muted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "In VPN settings: tap the gear next to Unseen → turn on \"Always-on VPN\". " +
                        "Leave \"Block connections without VPN\" OFF, or your internet will stop.",
                    color = Muted,
                    fontSize = 13.sp,
                )
                if (lockdown) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "\"Block connections without VPN\" is ON. Turn it off: Unseen only handles DNS.",
                        color = Warning,
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = actions.openVpnSettings) { Text("Open VPN settings") }
            }

            if (allowed.isNotEmpty()) {
                SectionTitle("Allowed sites")
                Text(
                    "These are never blocked. Remove one to block it again.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        items(allowed, key = { "allowed-$it" }) { domain ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(domain, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    Prefs.setAllowed(context, domain, false)
                    allowed = Prefs.allowedDomains(context).sorted()
                    actions.filtersChanged()
                }) { Text("Block again") }
            }
        }
        item {
            SectionTitle("Activity")
            if (events.isEmpty()) {
                Text("Nothing yet.", color = Muted, modifier = Modifier.padding(horizontal = 24.dp))
            }
        }
        items(events.take(30), key = { "event-${it.time}-${it.text.hashCode()}" }) { event ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
                Text(
                    dayAndClock(event.time),
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.width(112.dp),
                )
                Text(event.text, fontSize = 14.sp, color = if (event.warning) Warning else Color.White)
            }
        }
        item {
            SectionTitle("About")
            Text(
                "Unseen ${appVersion(context)}. Everything happens on your phone: no account, no servers, no analytics.\n\n" +
                    "Lists: ads and gambling from StevenBlack/hosts (MIT). Phishing and malware sites " +
                    "from malware-filter by curben (CC BY-SA 4.0), built from OpenPhish, PhishTank and URLhaus data.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun listInfo(size: Int, what: String): String =
    if (size == 0) "List downloads on first use. Needs internet once." else "%,d %s, updated daily.".format(size, what)

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

// ---------------------------------------------------------------- apps

private data class AppRow(val packageName: String, val label: String)

@Composable
private fun AppsScreen(onBack: () -> Unit, onChanged: () -> Unit, onOpenApp: (String) -> Unit) {
    val context = LocalContext.current
    val auth = LocalAuth.current
    var apps by remember { mutableStateOf<List<AppRow>?>(null) }
    var paused by remember { mutableStateOf(Prefs.pausedApps(context)) }
    val perApp by Stats.perApp.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    Column(Modifier.fillMaxSize()) {
        BackButton(onBack)
        ScreenTitle("Apps")
        Text(
            "Every app is protected. If an app stops working, turn protection off for that app only. " +
                "Tap an app to see its trackers and privacy grade.",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.packageName }) { app ->
                    AppRowItem(
                        app = app,
                        isProtected = app.packageName !in paused,
                        blockedToday = perApp[app.packageName] ?: 0,
                        onOpen = { onOpenApp(app.packageName) },
                        onToggle = { protect ->
                            val apply = {
                                Prefs.setPaused(context, app.packageName, paused = !protect)
                                paused = Prefs.pausedApps(context)
                                onChanged()
                            }
                            if (protect) apply() else auth("Pause protection for ${app.label}", apply)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRowItem(
    app: AppRow,
    isProtected: Boolean,
    blockedToday: Int,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val grade by produceState<PrivacyGrade.Grade?>(null, app.packageName, blockedToday) {
        value = withContext(Dispatchers.IO) { PrivacyGrade.of(context, app.packageName) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(app.packageName, 40.dp)
        Column(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = "Show trackers", onClick = onOpen)
        ) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    !isProtected -> "Paused"
                    blockedToday > 0 -> "$blockedToday blocked today"
                    else -> "Protected"
                },
                color = if (isProtected) Muted else Warning,
                fontSize = 13.sp,
            )
        }
        grade?.let { GradeBadge(it.letter, 32.dp) }
        Switch(checked = isProtected, onCheckedChange = onToggle)
    }
}

// ---------------------------------------------------------------- one app

@Composable
private fun AppDetailScreen(packageName: String, onBack: () -> Unit, actions: ScreenActions) {
    val context = LocalContext.current
    val auth = LocalAuth.current
    val perAppDomains by Stats.perAppDomains.collectAsStateWithLifecycle()
    val domains = (perAppDomains[packageName] ?: emptyMap()).entries.sortedByDescending { it.value }
    val total = domains.sumOf { it.value }
    var paused by remember { mutableStateOf(packageName in Prefs.pausedApps(context)) }
    var allowed by remember { mutableStateOf(Prefs.allowedDomains(context)) }
    var showHow by remember { mutableStateOf(false) }
    val isRealApp = !packageName.startsWith("uid:") && packageName != "unknown" && packageName != "android"
    val grade by produceState<PrivacyGrade.Grade?>(null, packageName, total) {
        value = withContext(Dispatchers.IO) { PrivacyGrade.of(context, packageName) }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackButton(onBack)
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(packageName, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        AppNames.label(context, packageName),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (total == 1) "1 request blocked today" else "$total requests blocked today",
                        color = Muted,
                        fontSize = 14.sp,
                    )
                }
            }

            grade?.let { g ->
                InfoCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GradeBadge(g.letter, 56.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Privacy grade ${g.letter}", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Text("Score ${g.score} / 100 · last 7 days", color = Muted, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (g.companies.isEmpty()) "No tracker companies contacted this week."
                        else "Tracker companies: " + g.companies.joinToString(", "),
                        fontSize = 14.sp,
                    )
                    Text(
                        if (g.permissions.isEmpty()) "No sensitive permissions."
                        else "Has access to: " + g.permissions.joinToString(", "),
                        fontSize = 14.sp,
                    )
                    TextButton(onClick = { showHow = !showHow }) {
                        Text(if (showHow) "Hide how it's calculated" else "How is this calculated?")
                    }
                    if (showHow) Text(PrivacyGrade.EXPLANATION, color = Muted, fontSize = 13.sp)
                }
            }

            if (isRealApp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = !paused, role = Role.Switch) { protect ->
                            val apply = {
                                Prefs.setPaused(context, packageName, paused = !protect)
                                paused = !protect
                                actions.appSettingsChanged()
                            }
                            if (protect) apply() else auth("Pause protection for this app", apply)
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Protect this app")
                        Text(
                            if (paused) "Paused: nothing is blocked for this app" else "Trackers, ads and scam sites are blocked",
                            color = if (paused) Warning else Muted,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(checked = !paused, onCheckedChange = null)
                }
            }
            Text(
                if (domains.isEmpty()) "Nothing blocked for this app today."
                else "Unseen stopped this app from contacting:",
                color = Muted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        items(domains, key = { it.key }) { (domain, count) ->
            val category = Blocklist.categoryOf(domain)
            val tracker = TrackerInfo.describe(domain, category)
            val isAllowed = Blocklist.isAllowed(domain)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = if (category == Category.SCAM) BorderStroke(1.dp, Warning) else null,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                tracker.company,
                                fontWeight = FontWeight.SemiBold,
                                color = if (category == Category.SCAM) Warning else Color.White,
                            )
                            Text(tracker.purpose, color = Muted, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(domain, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(if (count == 1) "1×" else "$count×", fontWeight = FontWeight.SemiBold, color = Accent)
                    }
                    if (category != Category.SCAM) {
                        TextButton(onClick = {
                            if (isAllowed) {
                                Prefs.setAllowed(context, domain, false)
                                allowed = Prefs.allowedDomains(context)
                                actions.filtersChanged()
                            } else {
                                auth("Allow $domain") {
                                    Prefs.setAllowed(context, domain, true)
                                    allowed = Prefs.allowedDomains(context)
                                    actions.filtersChanged()
                                }
                            }
                        }) {
                            Text(
                                if (domain.lowercase() in allowed || isAllowed) "Allowed · block again"
                                else "Allow this (if something broke)"
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun InfoCard(modifier: Modifier = Modifier.fillMaxWidth(), content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
        Text("‹ Back")
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = Accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun GradeBadge(letter: Char, size: Dp) {
    val color = when (letter) {
        'A' -> Good
        'B' -> Color(0xFF9BD86B)
        'C' -> Color(0xFFFFD25C)
        'D' -> Warning
        else -> Bad
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(color.copy(alpha = 0.18f))
            .border(1.5.dp, color, RoundedCornerShape(size / 4)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
private fun GhostCircle(color: Color, size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_stat_unseen),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size / 2),
        )
    }
}

@Composable
private fun CrashCard(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    InfoCard {
        Text("Unseen crashed last time", color = Warning, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Copy this report and send it to the developer.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(report.take(1500), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Muted)
        }
        Row {
            TextButton(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy report") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun PrivateDnsWarning(openNetworkSettings: () -> Unit) {
    InfoCard {
        Text("Private DNS is on", color = Warning, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Your phone sends DNS straight to another provider, so Unseen can't block anything. " +
                "Set Private DNS to Automatic or Off in Network settings.",
            color = Muted,
            fontSize = 14.sp,
        )
        TextButton(onClick = openNetworkSettings) { Text("Open network settings") }
    }
}

@Composable
private fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .background(CardBg)
        )
    }
}

@Suppress("DEPRECATION") // queryIntentActivities(Intent, Int) is fine on every supported version
private fun loadLaunchableApps(context: Context): List<AppRow> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .map { AppRow(it, AppNames.label(context, it)) }
        .sortedBy { it.label.lowercase() }
}

private fun clock(millis: Long): String =
    DateTimeFormatter.ofPattern("h:mm a").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun dayAndClock(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val day = when (time.toLocalDate()) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("d MMM").format(time)
    }
    return "$day ${DateTimeFormatter.ofPattern("h:mm a").format(time)}"
}
