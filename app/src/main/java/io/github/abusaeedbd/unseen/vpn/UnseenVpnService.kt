package io.github.abusaeedbd.unseen.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.abusaeedbd.unseen.R
import io.github.abusaeedbd.unseen.data.AppNames
import io.github.abusaeedbd.unseen.data.Prefs
import io.github.abusaeedbd.unseen.data.Stats
import io.github.abusaeedbd.unseen.dns.Blocklist
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * An on-device VPN that only sees DNS. Android is told the phone's DNS server is
 * 10.111.222.1, and only that address is routed into this VPN, so all other
 * traffic goes straight out as usual. Each DNS question is either answered
 * "blocked" (tracker domains) or passed on to a real DNS server.
 */
class UnseenVpnService : VpnService() {

    companion object {
        private const val TAG = "UnseenVpn"
        private const val ACTION_START = "io.github.abusaeedbd.unseen.START"
        private const val ACTION_STOP = "io.github.abusaeedbd.unseen.STOP"
        private const val CHANNEL_ID = "protection"
        private const val NOTIFICATION_ID = 1

        private const val TUN_ADDRESS = "10.111.222.2"
        private const val DNS_ADDRESS = "10.111.222.1"
        private val UPSTREAM_DNS = listOf("1.1.1.1", "8.8.8.8")
        private const val UPSTREAM_TIMEOUT_MS = 4000

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Starts protection, or re-applies settings (paused apps) if it is already on. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, UnseenVpnService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, UnseenVpnService::class.java).setAction(ACTION_STOP))
        }
    }

    @Volatile
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val workers: ExecutorService = Executors.newFixedThreadPool(8)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setWantOn(this, false)
            shutDown()
            return START_NOT_STICKY
        }
        showNotification()
        if (!establish()) {
            shutDown()
            return START_NOT_STICKY
        }
        Prefs.setWantOn(this, true)
        return START_STICKY
    }

    /** User switched the VPN off in system settings, or another VPN app took over. */
    override fun onRevoke() {
        Prefs.setWantOn(this, false)
        shutDown()
    }

    override fun onDestroy() {
        closeTun()
        workers.shutdownNow()
        _running.value = false
        super.onDestroy()
    }

    private fun shutDown() {
        closeTun()
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

        // Our own upstream DNS traffic must not loop back into the VPN.
        val skip = Prefs.pausedApps(this) + packageName
        for (pkg in skip) {
            try {
                builder.addDisallowedApplication(pkg)
            } catch (e: PackageManager.NameNotFoundException) {
                // App was uninstalled; nothing to skip.
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
        _running.value = true
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
            if (Blocklist.isBlocked(question.name)) {
                reply(packet, DnsMessage.blockedResponse(query, question), output)
                workers.execute { Stats.recordBlocked(appFor(packet)) }
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

    private fun showNotification() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Protection status", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_unseen)
            .setContentTitle("Unseen is protecting you")
            .setContentText("Trackers are being blocked")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
