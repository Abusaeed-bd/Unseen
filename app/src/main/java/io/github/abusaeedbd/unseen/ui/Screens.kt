package io.github.abusaeedbd.unseen.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abusaeedbd.unseen.R
import io.github.abusaeedbd.unseen.data.AppNames
import io.github.abusaeedbd.unseen.data.Prefs
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.vpn.UnseenVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UnseenScreen(
    onToggle: () -> Unit,
    onAppSettingsChanged: () -> Unit,
    isPrivateDnsStrict: () -> Boolean,
    openNetworkSettings: () -> Unit,
) {
    var showApps by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showApps) { showApps = false }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            if (showApps) {
                AppsScreen(onBack = { showApps = false }, onChanged = onAppSettingsChanged)
            } else {
                HomeScreen(
                    onToggle = onToggle,
                    onOpenApps = { showApps = true },
                    isPrivateDnsStrict = isPrivateDnsStrict,
                    openNetworkSettings = openNetworkSettings,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onToggle: () -> Unit,
    onOpenApps: () -> Unit,
    isPrivateDnsStrict: () -> Boolean,
    openNetworkSettings: () -> Unit,
) {
    val context = LocalContext.current
    val running by UnseenVpnService.running.collectAsStateWithLifecycle()
    val blocked by Stats.blockedToday.collectAsStateWithLifecycle()
    val perApp by Stats.perApp.collectAsStateWithLifecycle()

    var strictDns by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        strictDns = isPrivateDnsStrict()
        onPauseOrDispose { }
    }
    LaunchedEffect(running) { strictDns = isPrivateDnsStrict() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Unseen", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Apps can't watch what they can't see.", color = Muted, fontSize = 14.sp)

        Spacer(Modifier.height(40.dp))
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(if (running) Accent else CardBg)
                .clickable(role = Role.Button, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_stat_unseen),
                contentDescription = if (running) "Turn protection off" else "Turn protection on",
                tint = Color.White,
                modifier = Modifier.size(96.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (running) "Protection is ON" else "Protection is OFF",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(if (running) "Tap to turn off" else "Tap to turn on", color = Muted)

        if (strictDns) {
            Spacer(Modifier.height(24.dp))
            PrivateDnsWarning(openNetworkSettings)
        }

        Spacer(Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("$blocked", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(if (blocked == 1) "tracker blocked today" else "trackers blocked today", color = Muted)
            }
        }

        if (perApp.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Most tracker requests today", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    perApp.entries.sortedByDescending { it.value }.take(5).forEach { (pkg, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
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
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Manage apps")
        }
    }
}

@Composable
private fun PrivateDnsWarning(openNetworkSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBg),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Private DNS is on", color = Warning, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your phone sends DNS straight to another provider, so Unseen can't block trackers. " +
                    "Set Private DNS to Automatic or Off in Network settings.",
                color = Muted,
                fontSize = 14.sp,
            )
            TextButton(onClick = openNetworkSettings) { Text("Open network settings") }
        }
    }
}

private data class AppRow(val packageName: String, val label: String)

@Composable
private fun AppsScreen(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppRow>?>(null) }
    var paused by remember { mutableStateOf(Prefs.pausedApps(context)) }
    val perApp by Stats.perApp.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
            Text("‹ Back")
        }
        Text(
            "Apps",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            "Every app is protected. If an app stops working, turn protection off for that app only.",
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
                    val isProtected = app.packageName !in paused
                    AppRowItem(
                        app = app,
                        isProtected = isProtected,
                        blockedToday = perApp[app.packageName] ?: 0,
                        onToggle = { protect ->
                            Prefs.setPaused(context, app.packageName, paused = !protect)
                            paused = Prefs.pausedApps(context)
                            onChanged()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRowItem(app: AppRow, isProtected: Boolean, blockedToday: Int, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = isProtected, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(app.packageName, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    !isProtected -> "Paused"
                    blockedToday > 0 -> "$blockedToday trackers blocked today"
                    else -> "Protected"
                },
                color = if (isProtected) Muted else Warning,
                fontSize = 13.sp,
            )
        }
        Switch(checked = isProtected, onCheckedChange = null)
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

