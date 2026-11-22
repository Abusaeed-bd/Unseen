package io.github.abusaeedbd.unseen.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.abusaeedbd.unseen.R
import io.github.abusaeedbd.unseen.data.AppNames
import io.github.abusaeedbd.unseen.data.EventLog
import io.github.abusaeedbd.unseen.data.Prefs
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.dns.Blocklist
import io.github.abusaeedbd.unseen.dns.Category
import io.github.abusaeedbd.unseen.dns.DnsMessage
import io.github.abusaeedbd.unseen.dns.IpUdpPacket
import io.github.abusaeedbd.unseen.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

enum class Protection { OFF, ON, PAUSED }

/**
 * An on-device VPN that only sees DNS. Android is told the phone's DNS server is
 * 10.111.222.1, and only that address is routed into this VPN, so all other
 * traffic goes straight out as usual. Each DNS question is either answered
 * "blocked" (trackers, ads, scam sites) or passed on to a real DNS server.
 */
class UnseenVpnService : VpnService() {

    companion object {
        private const val TAG = "UnseenVpn"
        private const val ACTION_START = "io.github.abusaeedbd.unseen.START"
        private const val ACTION_STOP = "io.github.abusaeedbd.unseen.STOP"
        private const val ACTION_PAUSE = "io.github.abusaeedbd.unseen.PAUSE"
        private const val ACTION_RESUME = "io.github.abusaeedbd.unseen.RESUME"
        private const val EXTRA_MINUTES = "minutes"

        private const val CHANNEL_STATUS = "protection"
        private const val CHANNEL_ALERTS = "alerts"
        private const val NOTIFICATION_STATUS = 1
        private const val NOTIFICATION_TURNED_OFF = 2
        private const val NOTIFICATION_SCAM = 3

        private const val TUN_ADDRESS = "10.111.222.2"
        private const val DNS_ADDRESS = "10.111.222.1"
        private val UPSTREAM_DNS = listOf("1.1.1.1", "8.8.8.8")
        private const val UPSTREAM_TIMEOUT_MS = 4000

        private val _state = MutableStateFlow(Protection.OFF)
        val state: StateFlow<Protection> = _state.asStateFlow()

        /** When a pause ends (epoch millis), or 0. */
        private val _pausedUntil = MutableStateFlow(0L)
        val pausedUntil: StateFlow<Long> = _pausedUntil.asStateFlow()

        /** Android's "Always-on VPN" for Unseen, and its "Block connections without VPN" switch. */
        private val _alwaysOn = MutableStateFlow(false)
        val alwaysOn: StateFlow<Boolean> = _alwaysOn.asStateFlow()
        private val _lockdown = MutableStateFlow(false)
        val lockdown: StateFlow<Boolean> = _lockdown.asStateFlow()

        /** Starts protection, or re-applies settings (paused apps) if it is already on. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(intent(context, ACTION_STOP))
        }

        fun pause(context: Context, minutes: Int) {
            context.startService(intent(context, ACTION_PAUSE).putExtra(EXTRA_MINUTES, minutes))
        }

        fun resume(context: Context) {
            ContextCompat.startForegroundService(context, intent(context, ACTION_RESUME))
        }

        private fun intent(context: Context, action: String) =
            Intent(context, UnseenVpnService::class.java).setAction(action)
    }

    @Volatile
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val workers: ExecutorService = Executors.newFixedThreadPool(8)
    private val handler = Handler(Looper.getMainLooper())
    private val autoResume = Runnable { resumeNow() }
    private val lastScamAlert = ConcurrentHashMap<String, Long>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (_state.value != Protection.OFF) EventLog.add(this, "Protection turned off")
                Prefs.setWantOn(this, false)
                shutDown()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                if (_state.value == Protection.OFF) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                pauseFor(intent?.getIntExtra(EXTRA_MINUTES, 10) ?: 10)
                return START_STICKY
            }
            ACTION_RESUME -> {
                showStatus()
                if (!resumeNow()) return START_NOT_STICKY
                return START_STICKY
            }
            else -> {
                // ACTION_START, or Android restarting the service (intent == null)
                val wasOff = _state.value == Protection.OFF
                showStatus()
                handler.removeCallbacks(autoResume)
                if (!establish()) {
                    shutDown()
                    return START_NOT_STICKY
                }
                if (wasOff) EventLog.add(this, if (intent == null) "Protection turned on (restarted by Android)" else "Protection turned on")
                Prefs.setWantOn(this, true)
                showStatus()
                return START_STICKY
            }
        }
    }

    /** User switched the VPN off in phone settings, or another VPN app took over. */
    override fun onRevoke() {
        EventLog.add(this, "Protection turned off from phone settings or by another VPN app", warning = true)
        Prefs.setWantOn(this, false)
        alertTurnedOff()
        shutDown()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        closeTun()
        workers.shutdownNow()
        setState(Protection.OFF)
        super.onDestroy()
    }

    private fun shutDown() {
        handler.removeCallbacks(autoResume)
        closeTun()
        _pausedUntil.value = 0L
        setState(Protection.OFF)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseFor(minutes: Int) {
        handler.removeCallbacks(autoResume)
        closeTun()
        _pausedUntil.value = System.currentTimeMillis() + minutes * 60_000L
        setState(Protection.PAUSED)
        showStatus()
        handler.postDelayed(autoResume, minutes * 60_000L)
        EventLog.add(this, "Protection paused for $minutes minutes")
    }

    private fun resumeNow(): Boolean {
        handler.removeCallbacks(autoResume)
        if (!establish()) {
            shutDown()
            return false
        }
        _pausedUntil.value = 0L
        showStatus()
        EventLog.add(this, "Protection resumed")
        return true
    }

    private fun setState(state: Protection) {
        _state.value = state
        try {
            TileService.requestListeningState(this, ComponentName(this, ProtectionTileService::class.java))
        } catch (e: Exception) {
            // Tile not added; nothing to refresh.
        }
    }

    @Synchronized
    private fun establish(): Boolean {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(TUN_ADDRESS, 32)
            .addRoute(DNS_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .setBlocking(false)
            .setMetered(false)
            .setMtu(1500)

        // Our own traffic (list downloads, upstream DNS) must not loop back into the VPN.
        val skip = Prefs.pausedApps(this) + packageName
        for (pkg in skip) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                // App is not installed; nothing to skip.
            }
        }

        val newTun = try {
            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start VPN", e)
            null
        } ?: return false

        // Swap in the new interface first, then close the old one, so there is no gap.
        val oldTun = tun
        tun = newTun
        reader = Thread({ readLoop(newTun) }, "unseen-tun").apply { start() }
        try {
            oldTun?.close()
        } catch (e: IOException) {
            // ignore
        }
        _alwaysOn.value = isAlwaysOn
        _lockdown.value = isLockdownEnabled
        setState(Protection.ON)
        return true
    }

    @Synchronized
    private fun closeTun() {
        try {
            tun?.close()
        } catch (e: IOException) {
            // ignore
        }
        tun = null
        reader?.interrupt()
        reader = null
    }

    private fun showStatus() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Protection status", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_unseen)
            .setContentIntent(open)
            .setOngoing(true)
        if (_state.value == Protection.PAUSED) {
            val until = DateTimeFormatter.ofPattern("h:mm a")
                .format(Instant.ofEpochMilli(_pausedUntil.value).atZone(ZoneId.systemDefault()))
            val resume = PendingIntent.getForegroundService(
                this, 1, intent(this, ACTION_RESUME), PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentTitle("Unseen is paused")
                .setContentText("Protection comes back at $until")
                .addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat_unseen), "Resume now", resume
                    ).build()
                )
        } else {
            builder.setContentTitle("Unseen is protecting you")
                .setContentText("Trackers, ads and scam sites are being blocked")
        }
        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_STATUS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_STATUS, notification)
        }
    }

    private fun alertChannel(nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun alertTurnedOff() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        alertChannel(nm)
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_unseen)
            .setContentTitle("Unseen protection was turned off")
            .setContentText("Trackers are no longer blocked. Tap to turn it back on.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_TURNED_OFF, n)
        } catch (e: SecurityException) {
            // Notifications not allowed.
        }
    }

    /** Tells the user why a page did not open: it was a scam or malware site. */
    private fun alertScam(domain: String, app: String) {
        val now = System.currentTimeMillis()
        val last = lastScamAlert[domain] ?: 0L
        if (now - last < 10 * 60_000L) return
        lastScamAlert[domain] = now
        val nm = getSystemService(NotificationManager::class.java) ?: return
        alertChannel(nm)
        val text = "Unseen blocked $domain in ${AppNames.label(this, app)}. " +
            "It is a known scam, phishing or malware site. Don't enter your PIN or password there."
        val n = Notification.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_unseen)
            .setContentTitle("Dangerous site blocked")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_SCAM, n)
        } catch (e: SecurityException) {
            // Notifications not allowed.
        }
        EventLog.add(this, "Blocked dangerous site $domain", warning = true)
    }

    private fun readLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        val poll = StructPollfd().apply {
            fd = pfd.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        }
        try {
            // Stop when this interface is no longer the current one (closed or replaced).
            while (!Thread.currentThread().isInterrupted && tun === pfd) {
                // Wait up to 1 s for packets, so a closed or restarted interface is noticed quickly.
                if (Os.poll(arrayOf(poll), 1000) <= 0) continue
                if (poll.revents.toInt() and OsConstants.POLLNVAL != 0) return
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val packet = IpUdpPacket.parse(buf, n) ?: continue
                    if (packet.dstPort != 53) continue
                    val query = buf.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength)
                    handleQuery(packet, query, output)
                }
            }
        } catch (e: Exception) {
            // The interface was closed: protection stopped or restarted.
        }
    }

    private fun handleQuery(packet: IpUdpPacket.Udp, query: ByteArray, output: FileOutputStream) {
        val question = DnsMessage.parseQuery(query) ?: return
        try {
            val category = Blocklist.classify(question.name)
            if (category != null) {
                // Find the app first: once it gets the answer it closes its socket and
                // Android can no longer say who it was.
                val app = appFor(packet)
                reply(packet, DnsMessage.blockedResponse(query, question), output)
                workers.execute {
                    Stats.recordBlocked(app, question.name, category)
                    if (category == Category.SCAM) alertScam(question.name, app)
                }
            } else {
                workers.execute { forward(packet, query, output) }
            }
        } catch (e: RejectedExecutionException) {
            // Service is shutting down.
        }
    }

    private fun forward(packet: IpUdpPacket.Udp, query: ByteArray, output: FileOutputStream) {
        for (server in UPSTREAM_DNS) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = UPSTREAM_TIMEOUT_MS
                    socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
                    val answer = ByteArray(4096)
                    val received = DatagramPacket(answer, answer.size)
                    socket.receive(received)
                    reply(packet, answer.copyOf(received.length), output)
                }
                return
            } catch (e: IOException) {
                // Try the next server.
            }
        }
    }

    private fun reply(packet: IpUdpPacket.Udp, payload: ByteArray, output: FileOutputStream) {
        val response = IpUdpPacket.build(
            srcIp = packet.dstIp, dstIp = packet.srcIp,
            srcPort = packet.dstPort, dstPort = packet.srcPort,
            payload = payload,
        )
        try {
            synchronized(output) { output.write(response) }
        } catch (e: IOException) {
            // Interface closed meanwhile.
        }
    }

    /** Which app asked: Android attributes DNS sockets to the app they were made for. */
    private fun appFor(packet: IpUdpPacket.Udp): String = try {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val uid = cm.getConnectionOwnerUid(
            OsConstants.IPPROTO_UDP,
            InetSocketAddress(InetAddress.getByAddress(packet.srcIp), packet.srcPort),
            InetSocketAddress(InetAddress.getByAddress(packet.dstIp), packet.dstPort),
        )
        AppNames.packageFor(this, uid)
    } catch (e: Exception) {
        "unknown"
    }
}
