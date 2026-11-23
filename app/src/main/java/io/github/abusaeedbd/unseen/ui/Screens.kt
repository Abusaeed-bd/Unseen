package io.github.abusaeedbd.unseen.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abusaeedbd.unseen.R
import io.github.abusaeedbd.unseen.data.AppNames
import io.github.abusaeedbd.unseen.data.CrashLog
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ================================================================ shell

private enum class Tab(val title: String, val icon: Int) {
    Overview("Overview", R.drawable.ic_tab_overview),
    Apps("Apps", R.drawable.ic_tab_apps),
    Settings("Settings", R.drawable.ic_tab_settings),
}

@Composable
fun UnseenScreen(actions: ScreenActions) {
    val context = LocalContext.current
    var onboarded by remember { mutableStateOf(Prefs.onboarded(context)) }
    var tab by rememberSaveable { mutableStateOf(Tab.Overview) }
    var detailFor by rememberSaveable { mutableStateOf<String?>(null) }
    var showShare by remember { mutableStateOf(false) }

    var hud by remember { mutableStateOf<String?>(null) }
    var hudId by remember { mutableLongStateOf(0L) }
    LaunchedEffect(hudId) {
        if (hud != null) {
            delay(2200)
            hud = null
        }
    }
    val showHud: (String) -> Unit = { message ->
        hud = message
        hudId += 1
    }

    BackHandler(enabled = detailFor != null || tab != Tab.Overview) {
        if (detailFor != null) detailFor = null else tab = Tab.Overview
    }

    CompositionLocalProvider(LocalHud provides showHud) {
        Box(
            Modifier
                .fillMaxSize()
                .background(IosBlack)
                .safeDrawingPadding()
        ) {
            if (!onboarded) {
                Onboarding {
                    Prefs.setOnboarded(context)
                    onboarded = true
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        val pkg = detailFor
                        when {
                            pkg != null -> AppDetailScreen(pkg, onBack = { detailFor = null }, actions = actions)
                            tab == Tab.Overview -> HomeScreen(
                                actions = actions,
                                onOpenApp = { detailFor = it },
                                onOpenSettings = { tab = Tab.Settings },
                                onShare = { showShare = true },
                            )
                            tab == Tab.Apps -> AppsScreen(onChanged = actions.appSettingsChanged, onOpenApp = { detailFor = it })
                            else -> SettingsScreen(actions)
                        }
                    }
                    if (detailFor == null) TabBar(tab) { tab = it }
                }
            }
            if (actions.consentExplainer.value) {
                ConsentExplainer(onContinue = actions.continueToConsent, onDismiss = actions.dismissExplainer)
            }
            HudPill(hud, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            if (showShare) {
                ShareSheet(onDismiss = { showShare = false }, onShare = {
                    showShare = false
                    actions.share()
                })
            }
        }
    }
}

@Composable
private fun TabBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xF0161618))) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.14f)))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            Tab.entries.forEach { item ->
                val active = item == current
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab) { onSelect(item) }
                        .padding(horizontal = 18.dp, vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painterResource(item.icon),
                        contentDescription = null,
                        tint = if (active) Accent else Label2,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(item.title, color = if (active) Accent else Label2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ================================================================ welcome

@Composable
private fun Onboarding(onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        ProtectionOrb(state = Protection.ON, onClick = {})
        Spacer(Modifier.height(12.dp))
        LargeTitle("Welcome to Unseen")
        Text("Apps can't watch what they can't see.", color = Label2, fontSize = 15.sp)
        Spacer(Modifier.height(28.dp))
        IosGroup {
            WelcomePoint("Blocks trackers, ads and scam sites", "Inside all your apps, without turning off any permission. Your apps keep working.")
            IosHairline(36.dp)
            WelcomePoint("Nothing leaves your phone", "Unseen works on your phone only. No account, no servers, no ads.")
            IosHairline(36.dp)
            WelcomePoint("You'll see a key icon at the top", "Android shows it whenever an app filters your connections. Here, it simply means Unseen is protecting you.")
            IosHairline(36.dp)
            WelcomePoint("Banking apps keep working", "bKash, Nagad and Rocket are left out from the start, because they won't open while any filter like Unseen is on.")
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Get started", onDone)
    }
}

@Composable
private fun WelcomePoint(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(Accent))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Label2, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ================================================================ overview

@Composable
private fun HomeScreen(
    actions: ScreenActions,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val hud = LocalHud.current
    val state by UnseenVpnService.state.collectAsStateWithLifecycle()
    val pausedUntil by UnseenVpnService.pausedUntil.collectAsStateWithLifecycle()
    val today by Stats.today.collectAsStateWithLifecycle()
    val perApp by Stats.perApp.collectAsStateWithLifecycle()
    val perAppDomains by Stats.perAppDomains.collectAsStateWithLifecycle()
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

    // Count down while paused.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state == Protection.PAUSED) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosBlack)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // Large title header
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp)) {
            Column(Modifier.weight(1f)) {
                LargeTitle("Unseen")
                Text("Apps can't watch what they can't see.", color = Label2, fontSize = 13.sp)
            }
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Grouped)
                    .border(0.5.dp, CardBorder, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Settings", onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_tab_settings), contentDescription = "Settings", tint = AccentSoft, modifier = Modifier.size(19.dp))
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            crash?.let { report ->
                CrashCard(report) {
                    CrashLog.clear(context)
                    crash = null
                }
            }
            if (strictDns) PrivateDnsWarning(actions.openNetworkSettings)
        }

        // The orb
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ProtectionOrb(state) {
                when (state) {
                    Protection.OFF -> actions.turnOn()
                    Protection.ON -> actions.turnOff()
                    Protection.PAUSED -> {
                        actions.resume()
                        hud("Protection resumed")
                    }
                }
            }
            Text(
                when (state) {
                    Protection.ON -> "Protection is ON"
                    Protection.PAUSED -> "Paused"
                    Protection.OFF -> "Protection is OFF"
                },
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (state == Protection.PAUSED) {
                Text(
                    buildAnnotatedString {
                        append("Resumes in ")
                        withStyle(SpanStyle(color = IosOrange, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)) {
                            append(countdown(pausedUntil - now))
                        }
                    },
                    color = Label2,
                    fontSize = 13.sp,
                )
            } else {
                Text(if (state == Protection.ON) "Tap to turn off" else "Tap to turn on", color = Label2, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            when (state) {
                Protection.PAUSED -> PillButton("Resume now", onClick = {
                    actions.resume()
                    hud("Protection resumed")
                }, background = IosOrange, textColor = Color.Black, bordered = false)
                Protection.ON -> PillButton("Pause for 10 minutes", onClick = { actions.pause(10) })
                Protection.OFF -> {}
            }
            if (state != Protection.OFF) {
                Text(
                    "Handy for banking apps that won't open while Unseen is on.",
                    color = Label3,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).widthIn(max = 260.dp),
                )
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Card 1: blocked today
            IosGroup {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("%,d".format(today.total), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                            Text("blocked today", color = Label2, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IosGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("On-Device DNS", color = IosGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountPill("Trackers", today.trackers, Accent, AccentSoft)
                        CountPill("Ads", today.ads, IosBlue, IosCyan)
                        CountPill("Scams", today.scams, if (today.scams > 0) IosOrange else IosGreen, if (today.scams > 0) IosOrange else IosGreen)
                    }
                    Spacer(Modifier.height(14.dp))
                    IosHairline(0.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("About ${formatBytes(today.dataSavedBytes)} of mobile data saved", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Estimate: about 3 KB per blocked tracker and 30 KB per blocked ad.", color = Label2, fontSize = 11.sp)
                }
            }

            // Card 2: last 7 days
            IosGroup {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Last 7 days", color = Label2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("%,d blocked".format(week.counts.total), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${week.companies} companies kept out · about ${formatBytes(week.counts.dataSavedBytes)} saved", color = Label2, fontSize = 12.sp)
                    }
                    if (week.counts.total > 0) {
                        Spacer(Modifier.width(12.dp))
                        PillButton("Share my week", onShare, background = Elevated, bordered = false)
                    }
                }
            }

            // Card 3: most tracker requests today
            Column {
                IosSectionHeader("Most tracker requests today", trailing = if (perApp.isNotEmpty()) "Grade" else null)
                IosGroup {
                    val top = perApp.entries.sortedByDescending { it.value }.take(5)
                    if (top.isEmpty()) {
                        Text(
                            "Nothing blocked yet today. Use your apps as usual.",
                            color = Label2,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    top.forEachIndexed { index, (pkg, count) ->
                        val firstCompany = perAppDomains[pkg]?.entries?.maxByOrNull { it.value }?.key
                            ?.let { TrackerInfo.describe(it, Blocklist.categoryOf(it)).company }
                        TopAppRow(pkg, count, firstCompany, onClick = { onOpenApp(pkg) })
                        if (index < top.lastIndex) IosHairline(64.dp)
                    }
                }
                IosFooter("Tap an app to see who it tried to send data to.")
            }
        }
    }
}

@Composable
private fun TopAppRow(pkg: String, count: Int, subtitle: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val grade by produceState<PrivacyGrade.Grade?>(null, pkg, count) {
        value = withContext(Dispatchers.IO) { PrivacyGrade.of(context, pkg) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Show trackers", onClick = onClick)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(pkg, 36.dp, 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(AppNames.label(context, pkg), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Label2, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        grade?.let { GradeBadge(it.letter, 24.dp, 7.dp) }
        Text("$count", color = Label2, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 32.dp))
        Spacer(Modifier.width(6.dp))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Label3, modifier = Modifier.size(14.dp))
    }
}

/** The glowing button in the middle of Overview. */
@Composable
private fun ProtectionOrb(state: Protection, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        1f, 1.15f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    val pulseAlpha by pulse.animateFloat(
        0.42f, 0.12f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        if (state == Protection.ON) {
            Box(
                Modifier
                    .size(168.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.6f))
            )
        }
        val orb = when (state) {
            Protection.ON -> Modifier
                .shadow(36.dp, CircleShape, ambientColor = Accent, spotColor = Accent)
                .background(Brush.verticalGradient(listOf(Color(0xFF9D82FF), Color(0xFF7452F5))), CircleShape)
            Protection.PAUSED -> Modifier
                .shadow(24.dp, CircleShape, ambientColor = IosOrange, spotColor = IosOrange)
                .background(Color(0xFF2C2416), CircleShape)
                .border(1.dp, IosOrange.copy(alpha = 0.45f), CircleShape)
            Protection.OFF -> Modifier
                .background(Grouped, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
        }
        Box(
            Modifier
                .size(144.dp)
                .then(orb)
                .clip(CircleShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = when (state) {
                        Protection.ON -> "Turn protection off"
                        Protection.PAUSED -> "Resume protection"
                        Protection.OFF -> "Turn protection on"
                    },
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(if (state == Protection.ON) R.drawable.ic_brand_ghost_orb else R.drawable.ic_brand_ghost_plain),
                contentDescription = null,
                modifier = Modifier.size(56.dp).alpha(if (state == Protection.ON) 1f else 0.65f),
            )
        }
    }
}

// ================================================================ apps

private data class AppRow(val packageName: String, val label: String, val category: String)

@Composable
private fun AppsScreen(onChanged: () -> Unit, onOpenApp: (String) -> Unit) {
    val context = LocalContext.current
    val auth = LocalAuth.current
    val hud = LocalHud.current
    var apps by remember { mutableStateOf<List<AppRow>?>(null) }
    var paused by remember { mutableStateOf(Prefs.pausedApps(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var segment by rememberSaveable { mutableStateOf("All") }
    val perApp by Stats.perApp.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    val list = apps
    val filtered = list.orEmpty().filter { app ->
        app.label.contains(query, ignoreCase = true) && when (segment) {
            "Protected" -> app.packageName !in paused
            "Paused" -> app.packageName in paused
            else -> true
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(IosBlack)) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    LargeTitle("Apps", Modifier.weight(1f))
                    if (list != null) {
                        Text(
                            "${list.count { it.packageName !in paused }} of ${list.size} protected",
                            color = Label2,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                IosSearchField(query, "Search apps") { query = it }
                IosSegmented(listOf("All", "Protected", "Paused"), segment) { segment = it }
            }
        }
        if (list == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
        } else {
            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        IosGroup {
                            Text("No matching apps found.", color = Label2, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                        }
                    }
                }
            }
        }
        itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
            val isProtected = app.packageName !in paused
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppListRow(
                    app = app,
                    isProtected = isProtected,
                    blockedToday = perApp[app.packageName] ?: 0,
                    first = index == 0,
                    last = index == filtered.lastIndex,
                    onOpen = { onOpenApp(app.packageName) },
                    onToggle = { protect ->
                        val apply = {
                            Prefs.setPaused(context, app.packageName, paused = !protect)
                            paused = Prefs.pausedApps(context)
                            onChanged()
                            hud(if (protect) "${app.label} protected" else "${app.label} paused")
                        }
                        if (protect) apply() else auth("Pause protection for ${app.label}", apply)
                    },
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                IosFooter("Banking apps like bKash, Nagad and Rocket are paused by default so they keep working.")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** One app in the list; rows are drawn as one rounded group across LazyColumn items. */
@Composable
private fun AppListRow(
    app: AppRow,
    isProtected: Boolean,
    blockedToday: Int,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val grade by produceState<PrivacyGrade.Grade?>(null, app.packageName, blockedToday) {
        value = withContext(Dispatchers.IO) { PrivacyGrade.of(context, app.packageName) }
    }
    val shape = RoundedCornerShape(
        topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
        bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp,
    )
    Column(Modifier.fillMaxWidth().clip(shape).background(Grouped)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Show trackers", onClick = onOpen)
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName, 40.dp, 11.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    grade?.let {
                        Spacer(Modifier.width(8.dp))
                        GradeBadge(it.letter, 20.dp, 6.dp)
                    }
                }
                Text(
                    if (isProtected) "$blockedToday blocked today · ${app.category}" else "Paused · Not filtered",
                    color = if (isProtected) Label2 else IosOrange,
                    fontSize = 12.sp,
                    fontWeight = if (isProtected) FontWeight.Normal else FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            IosSwitch(checked = isProtected, onChange = onToggle)
        }
        if (!last) IosHairline(68.dp)
    }
}

// ================================================================ one app

@Composable
private fun AppDetailScreen(packageName: String, onBack: () -> Unit, actions: ScreenActions) {
    val context = LocalContext.current
    val auth = LocalAuth.current
    val hud = LocalHud.current
    val perAppDomains by Stats.perAppDomains.collectAsStateWithLifecycle()
    val domains = (perAppDomains[packageName] ?: emptyMap()).entries.sortedByDescending { it.value }
    val total = domains.sumOf { it.value }
    var paused by remember { mutableStateOf(packageName in Prefs.pausedApps(context)) }
    var allowed by remember { mutableStateOf(Prefs.allowedDomains(context)) }
    var showHow by remember { mutableStateOf(false) }
    val isRealApp = !packageName.startsWith("uid:") && packageName != "unknown" && packageName != "android"
    val label = AppNames.label(context, packageName)
    val grade by produceState<PrivacyGrade.Grade?>(null, packageName, total) {
        value = withContext(Dispatchers.IO) { PrivacyGrade.of(context, packageName) }
    }

    Column(Modifier.fillMaxSize().background(IosBlack)) {
        NavBar(label, onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName, 56.dp, 15.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (total == 1) "1 request blocked today" else "$total requests blocked today", color = Label2, fontSize = 13.sp)
                }
            }

            grade?.let { g ->
                IosGroup {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GradeBadge(g.letter, 56.dp, 14.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Privacy grade ${g.letter}", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text("Score ${g.score} / 100 · last 7 days", color = Label2, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        IosHairline(0.dp)
                        Spacer(Modifier.height(12.dp))
                        LabelledLine("Tracker companies: ", if (g.companies.isEmpty()) "None detected" else g.companies.joinToString(", "))
                        Spacer(Modifier.height(4.dp))
                        LabelledLine("Has access to: ", if (g.permissions.isEmpty()) "No sensitive permissions" else g.permissions.joinToString(", "))
                        Row(
                            Modifier
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button) { showHow = !showHow },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("How is this calculated?", color = AccentSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                painterResource(R.drawable.ic_chevron_down),
                                contentDescription = null,
                                tint = AccentSoft,
                                modifier = Modifier.size(14.dp).rotate(if (showHow) 180f else 0f),
                            )
                        }
                        if (showHow) {
                            Text(
                                PrivacyGrade.EXPLANATION,
                                color = Label2,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Elevated)
                                    .padding(12.dp),
                            )
                        }
                    }
                }
            }

            if (isRealApp) {
                IosGroup {
                    IosSwitchRow(
                        title = if (paused) "Protection paused for this app" else "Protect this app",
                        subtitle = if (paused) "Nothing is blocked for this app" else "Trackers, ads and scam sites are blocked",
                        checked = !paused,
                    ) { protect ->
                        val apply = {
                            Prefs.setPaused(context, packageName, paused = !protect)
                            paused = !protect
                            actions.appSettingsChanged()
                            hud(if (protect) "$label protected" else "$label paused")
                        }
                        if (protect) apply() else auth("Pause protection for this app", apply)
                    }
                }
            }

            Column {
                IosSectionHeader("Unseen stopped this app from contacting")
                IosGroup {
                    if (domains.isEmpty()) {
                        Text(
                            "No tracker requests recorded for this app today.",
                            color = Label2,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                        )
                    }
                    domains.forEachIndexed { index, (domain, count) ->
                        val category = Blocklist.categoryOf(domain)
                        val info = TrackerInfo.describe(domain, category)
                        val isAllowed = domain.lowercase() in allowed || Blocklist.isAllowed(domain)
                        DomainRow(
                            title = info.company,
                            description = info.purpose,
                            domain = domain,
                            count = count,
                            dangerous = category == Category.SCAM,
                            allowed = isAllowed,
                            onToggleAllow = {
                                if (isAllowed) {
                                    Prefs.setAllowed(context, domain, false)
                                    allowed = Prefs.allowedDomains(context)
                                    actions.filtersChanged()
                                    hud("Blocking $domain again")
                                } else {
                                    auth("Allow $domain") {
                                        Prefs.setAllowed(context, domain, true)
                                        allowed = Prefs.allowedDomains(context)
                                        actions.filtersChanged()
                                        hud("Allowed $domain")
                                    }
                                }
                            },
                        )
                        if (index < domains.lastIndex) IosHairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainRow(
    title: String,
    description: String,
    domain: String,
    count: Int,
    dangerous: Boolean,
    allowed: Boolean,
    onToggleAllow: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = if (dangerous) IosOrange else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (allowed) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(IosGreen.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Allowed", color = IosGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(description, color = Label2, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("$count×", color = AccentSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            domain,
            color = Color.White.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Elevated)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        if (!dangerous) {
            Text(
                if (allowed) "Allowed · Block again" else "Allow this (if something broke)",
                color = AccentSoft,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = onToggleAllow)
                    .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LabelledLine(label: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Label2)) { append(label) }
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append(value) }
        },
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

/** Frosted top bar with "‹ Back" and a centred title. */
@Composable
private fun NavBar(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xC7121214))) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(
                Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = null, tint = AccentSoft, modifier = Modifier.size(20.dp))
                Text("Back", color = AccentSoft, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 200.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.12f)))
    }
}

// ================================================================ settings

@Composable
private fun SettingsScreen(actions: ScreenActions) {
    val context = LocalContext.current
    val auth = LocalAuth.current
    val hud = LocalHud.current
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
            hud("$what ${if (on) "enabled" else "disabled"}")
        }
        if (on) run() else auth("Turn off $what", run)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IosBlack)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        LargeTitle("Settings", Modifier.padding(horizontal = 4.dp))

        // What to block
        Column {
            IosSectionHeader("What to block")
            IosGroup {
                IosSwitchRow("Trackers", "Always on. ${Blocklist.sizeOf(Category.TRACKER)} tracker services inside apps.", checked = true, enabled = false) { }
                IosHairline()
                IosSwitchRow("Ads", listInfo(Blocklist.sizeOf(Category.AD), "ad and malware sites"), blockAds) { on ->
                    toggle(on, "Ad blocking") { Prefs.setBlockAds(context, it); blockAds = it }
                }
                IosHairline()
                IosSwitchRow(
                    "Scam & dangerous sites",
                    listInfo(Blocklist.sizeOf(Category.SCAM), "phishing and malware sites") + " Also fake bKash and Nagad pages.",
                    blockScams,
                ) { on -> toggle(on, "Scam protection") { Prefs.setBlockScams(context, it); blockScams = it } }
                IosHairline()
                IosSwitchRow("Gambling & betting sites", listInfo(Blocklist.sizeOf(Category.GAMBLING), "gambling sites"), blockGambling) { on ->
                    toggle(on, "Gambling blocking") { Prefs.setBlockGambling(context, it); blockGambling = it }
                }
            }
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    updateMessage ?: if (lastUpdated == 0L) "Lists not downloaded yet" else "Lists updated ${dayAndClock(lastUpdated)} · they update daily",
                    color = Label2,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (updating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = AccentSoft, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Updating", color = AccentSoft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(
                        "Update now",
                        color = AccentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button) {
                                scope.launch {
                                    val ok = ListUpdater.updateAll(context)
                                    updateMessage = if (ok) null else "Update failed. Check your internet and try again."
                                    actions.filtersChanged()
                                    hud(if (ok) "Blocklists updated" else "Update failed")
                                }
                            }
                            .padding(4.dp),
                    )
                }
            }
        }

        // Security
        Column {
            IosSectionHeader("Security")
            IosGroup {
                IosSwitchRow(
                    "App lock",
                    if (Auth.deviceIsSecure(context))
                        "Ask for your fingerprint, face or PIN before protection can be turned off or apps paused."
                    else "Set a screen lock on your phone first.",
                    checked = lockOn,
                    enabled = Auth.deviceIsSecure(context),
                ) { on -> actions.setAppLock(on) { result -> lockOn = result } }
            }
            Spacer(Modifier.height(12.dp))
            IosGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Keep Unseen always on", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            state != Protection.ON -> "Turn protection on to check this setting."
                            alwaysOn -> "Always-on is ON. Unseen comes back by itself if it is closed."
                            else -> "Turn on Always-on so Unseen restarts by itself if someone closes it."
                        },
                        color = if (state == Protection.ON && alwaysOn) IosGreen else Label2,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        buildAnnotatedString {
                            append("Android keeps this under \"VPN\" in Settings, the name it uses for any app that filters connections. There: tap the gear next to ")
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append("Unseen") }
                            append(" → turn on ")
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append("\"Always-on VPN\"") }
                            append(". Leave ")
                            withStyle(SpanStyle(color = IosOrange, fontWeight = FontWeight.Medium)) { append("\"Block connections without VPN\" OFF") }
                            append(", or your internet will stop.")
                        },
                        color = Label2,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                    if (lockdown) {
                        Text(
                            "\"Block connections without VPN\" is ON. Turn it off: Unseen only filters on your phone, so this setting cuts your internet.",
                            color = IosOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button, onClick = actions.openVpnSettings)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Open Android settings", color = AccentSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = AccentSoft, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Allowed sites
        Column {
            IosSectionHeader("Allowed sites")
            IosGroup {
                if (allowed.isEmpty()) {
                    Text("No custom allowed sites.", color = Label2, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
                allowed.forEachIndexed { index, domain ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(domain, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            "Block again",
                            color = AccentSoft,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(role = Role.Button) {
                                    Prefs.setAllowed(context, domain, false)
                                    allowed = Prefs.allowedDomains(context).sorted()
                                    actions.filtersChanged()
                                    hud("Blocking $domain again")
                                }
                                .padding(4.dp),
                        )
                    }
                    if (index < allowed.lastIndex) IosHairline()
                }
            }
            IosFooter("These are never blocked. Remove one to block it again.")
        }

        // Activity
        Column {
            IosSectionHeader("Activity")
            IosGroup {
                val shown = events.take(10)
                if (shown.isEmpty()) {
                    Text("Nothing yet.", color = Label2, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                }
                shown.forEachIndexed { index, event ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (event.warning) IosOrange else Accent)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(event.text, color = if (event.warning) IosOrange else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp)
                            Text(dayAndClock(event.time), color = Label2, fontSize = 12.sp)
                        }
                    }
                    if (index < shown.lastIndex) IosHairline(36.dp)
                }
            }
        }

        // Your data
        Column {
            IosSectionHeader("Your data")
            IosGroup {
                IosRow(
                    onClick = {
                        actions.exportReport()
                        hud("Report ready to share")
                    },
                    trailing = {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = null, tint = AccentSoft, modifier = Modifier.size(18.dp))
                    },
                ) {
                    Text("Export report", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "A file with what Unseen blocked: per day, per hour, per app and per site, plus your settings.",
                        color = Label2,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            IosFooter("It lists the apps you use and the trackers they contacted, not the websites you visit. Nothing leaves your phone unless you share the file.")
        }

        // About
        Column {
            IosSectionHeader("About")
            IosGroup {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Unseen ${appVersion(context)}. Everything happens on your phone: no account, no servers, no analytics.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 17.sp,
                    )
                    Text(
                        "Lists: ads and gambling from StevenBlack/hosts (MIT). Phishing and malware sites from " +
                            "malware-filter by curben (CC BY-SA 4.0), built from OpenPhish, PhishTank and URLhaus data.",
                        color = Label2,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
}

// ================================================================ before Android's dialog

/**
 * Android's permission dialog says "VPN connection", which many people distrust.
 * This sheet explains, before that dialog, what Unseen actually does.
 */
@Composable
private fun ConsentExplainer(onContinue: () -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClickLabel = "Close", onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Grouped)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f)))
            Spacer(Modifier.height(16.dp))
            Text("One quick step", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Next, Android will ask to allow a \"VPN connection\". That is simply the name Android gives to any app " +
                    "that filters connections on your phone. Unseen is not a VPN service.",
                color = Label2,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(16.dp))
            IosGroup {
                TrustPoint("Stays on your phone", "Nothing is sent to any server. No account, no tracking.")
                IosHairline(36.dp)
                TrustPoint("Your location and IP don't change", "Websites and apps see you exactly as before.")
                IosHairline(36.dp)
                TrustPoint("Banking apps keep working", "bKash, Nagad and Rocket are left out from the start.")
                IosHairline(36.dp)
                TrustPoint("Open for anyone to check", "Unseen's code is public on GitHub.")
            }
            Text(
                "While Unseen is on, Android shows a small key icon at the top. That's normal.",
                color = Label2,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Continue", onContinue)
            Text(
                "Not now",
                color = AccentSoft,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun TrustPoint(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(IosGreen))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = Label2, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ================================================================ share sheet

@Composable
private fun ShareSheet(onDismiss: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    val week = remember { Stats.week() }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClickLabel = "Close", onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(Grouped)
                // Swallow taps so they don't reach the backdrop and close the sheet.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f)))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Weekly Privacy Card", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("1080 × 1350 image · ready for Stories", color = Label2, fontSize = 12.sp)
                }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Elevated)
                        .clickable(role = Role.Button, onClickLabel = "Close", onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = Label2, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            ShareCardPreview(
                total = week.counts.total,
                companies = week.companies,
                dataSaved = formatBytes(week.counts.dataSavedBytes),
                version = appVersion(context),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Share Image", onShare, icon = R.drawable.ic_share)
        }
    }
}

@Composable
private fun ShareCardPreview(total: Int, companies: Int, dataSaved: String, version: String) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF191924), Color(0xFF09090D))))
            .background(Brush.radialGradient(listOf(Accent.copy(alpha = 0.36f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(900f, 150f), radius = 700f))
            .background(Brush.radialGradient(listOf(IosGreen.copy(alpha = 0.14f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(100f, 1100f), radius = 700f))
            .border(0.5.dp, Color.White.copy(alpha = 0.15f), shape)
            .padding(22.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(BrandInk).border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_brand_ghost), contentDescription = null, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Unseen", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Weekly Report", color = AccentSoft, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("LAST 7 DAYS", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("%,d".format(total), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 58.sp)
            Text("Trackers & ads blocked", color = AccentSoft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Stopped silently on-device before leaving this phone.", color = Label2, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("$companies", "Companies kept out", Color.White, Modifier.weight(1f))
                StatTile("~$dataSaved", "Data saved", IosGreen, Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            IosHairline(0.dp)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("\"Apps can't watch what they can't see.\"", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, modifier = Modifier.weight(1f))
                Text("v$version", color = AccentSoft, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, color: Color, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Label2, fontSize = 11.sp)
    }
}

// ================================================================ small pieces

@Composable
private fun CrashCard(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    IosGroup {
        Column(Modifier.padding(16.dp)) {
            Text("Unseen crashed last time", color = IosOrange, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Copy this report and send it to the developer.", color = Label2, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(report.take(1500), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Label2)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Copy report", { clipboard.setText(AnnotatedString(report)) }, background = Elevated, bordered = false)
                PillButton("Dismiss", onDismiss, background = Elevated, textColor = Label2, bordered = false)
            }
        }
    }
}

@Composable
private fun PrivateDnsWarning(openNetworkSettings: () -> Unit) {
    IosGroup {
        Column(Modifier.padding(16.dp)) {
            Text("Private DNS is on", color = IosOrange, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Your phone sends DNS straight to another provider, so Unseen can't block anything. " +
                    "Set Private DNS to Automatic or Off in Network settings.",
                color = Label2,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = Role.Button, onClick = openNetworkSettings),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Open network settings", color = AccentSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = AccentSoft, modifier = Modifier.size(14.dp))
            }
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

@Suppress("DEPRECATION") // queryIntentActivities(Intent, Int) is fine on every supported version
private fun loadLaunchableApps(context: Context): List<AppRow> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { info ->
            val category = ApplicationInfo.getCategoryTitle(context, info.category)?.toString() ?: "App"
            AppRow(info.packageName, AppNames.label(context, info.packageName), category)
        }
        .sortedBy { it.label.lowercase() }
}

private fun countdown(millis: Long): String {
    val total = (millis.coerceAtLeast(0) + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

private fun dayAndClock(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val day = when (time.toLocalDate()) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("d MMM").format(time)
    }
    return "$day ${DateTimeFormatter.ofPattern("h:mm a").format(time)}"
}
