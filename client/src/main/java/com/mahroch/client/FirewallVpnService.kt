package com.mahroch.client

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import hev.htproxy.TProxyService
import java.io.File

class FirewallVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var tunFd: Int = -1
    private var socks: DirectSocks5Server? = null
    private val channel = "mahroch_firewall"

    companion object {
        /** True only while OUR tunnel is actually established and filtering traffic. */
        @Volatile var isActive: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        ClientControlServer.start(this)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "Mahroch Firewall", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(9001, notification())
        startFirewall()
        return START_STICKY
    }

    private fun startFirewall() {
        if (tun != null) return

        // Route all IPv4/IPv6 traffic into TUN: fail-closed.
        tun = Builder()
            .setSession("Mahroch Client")
            .addAddress("198.18.0.1", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:mahroch::1", 128)
            .addRoute("::", 0)
            // DNS requests are intercepted by Hev's mapped-DNS feature.
            .addDnsServer("8.8.8.8")
            .setMtu(1500)
            .establish()

        if (tun == null) {
            Log.e("Mahroch", "VPN interface could not be established")
            stopSelf()
            return
        }

        socks = DirectSocks5Server({ Policy.domains(this) }, { s -> protect(s) })
        socks!!.start()

        val config = """
            tunnel:
              name: tun0
              mtu: 1500
              ipv4: 198.18.0.1
              ipv6: 'fd00:mahroch::1'
            socks5:
              address: 127.0.0.1
              port: 1080
              udp: 'udp'
            mapdns:
              address: 8.8.8.8
              port: 53
              network: 100.64.0.0
              netmask: 255.192.0.0
              cache-size: 10000
            misc:
              log-level: error
        """.trimIndent()

        val configFile = File(filesDir, "hev.yml")
        configFile.writeText(config)

        try {
            // TProxyService expects the raw Linux file-descriptor integer.
            // ParcelFileDescriptor.fileDescriptor is a java.io.FileDescriptor and
            // does not expose a public .fd property. detachFd() transfers ownership
            // of the descriptor to the native tunnel service.
            tunFd = tun!!.detachFd()
            tun = null

            val ok = TProxyService.TProxyStartService(configFile.absolutePath, tunFd)
            if (!ok) {
                closeDetachedTunFd()
                throw IllegalStateException("hev tunnel failed to start")
            }
            isActive = true
        } catch (t: Throwable) {
            Log.e("Mahroch", "Firewall engine failed", t)
            stopFirewall()
        }
    }

    private fun stopFirewall() {
        isActive = false
        try { TProxyService.TProxyStopService() } catch (_: Throwable) {}
        try { socks?.stop() } catch (_: Throwable) {}
        socks = null
        try { tun?.close() } catch (_: Throwable) {}
        tun = null
        // After a successful native start, TProxyStopService owns the detached fd.
        // If it was never started, closeDetachedTunFd() handles the failure path.
        tunFd = -1
    }

    private fun closeDetachedTunFd() {
        if (tunFd >= 0) {
            try { ParcelFileDescriptor.adoptFd(tunFd).close() } catch (_: Throwable) {}
            tunFd = -1
        }
    }

    override fun onDestroy() {
        stopFirewall()
        super.onDestroy()
    }

    override fun onRevoke() {
        // Android calls this specifically when another app just took over as
        // the active VPN, kicking ours out. This is the most reliable signal
        // we get that a filter-bypass VPN was just connected.
        if (Policy.enabled(this)) {
            DeviceGuard.reactToForeignVpn(this)
        }
        stopFirewall()
        super.onRevoke()
    }

    private fun notification(): Notification {
        val n = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channel) else Notification.Builder(this)
        return n.setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Mahroch Client")
            .setContentText("فیلتر اینترنت فعال است")
            .setOngoing(true).build()
    }
}
