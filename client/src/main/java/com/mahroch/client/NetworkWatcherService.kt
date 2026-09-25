package com.mahroch.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Keeps a live, dynamically-registered NetworkCallback for as long as
 * protection is enabled. This replaces relying on the CONNECTIVITY_ACTION /
 * WIFI_STATE_CHANGE broadcasts declared in the manifest: since Android 7.0
 * those implicit broadcasts are NOT delivered to statically-declared
 * receivers, so NetworkChangeReceiver alone cannot reliably catch "the rival
 * VPN just disconnected, go re-enable the filter" on modern devices.
 *
 * A NetworkCallback registered at runtime (this service) does not have that
 * restriction and fires immediately on every relevant network transition.
 */
class NetworkWatcherService : Service() {
    private val channel = "mahroch_watcher"
    private lateinit var cm: ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        cm = getSystemService(ConnectivityManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "Mahroch Network Watcher", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(9002, notification())
        registerCallback()
        return START_STICKY
    }

    private fun registerCallback() {
        if (callback != null) {
            evaluate()
            return
        }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = evaluate()
            override fun onLost(network: Network) = evaluate()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = evaluate()
        }
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder().build(), cb)
            callback = cb
        } catch (e: Throwable) {
            Log.e("MahrochWatcher", "registerNetworkCallback failed", e)
        }
        evaluate()
    }

    /** Re-checks current network state and starts/stops the firewall accordingly. */
    private fun evaluate() {
        if (!Policy.enabled(this)) return

        val n = cm.activeNetwork
        val caps = n?.let { cm.getNetworkCapabilities(it) }
        val foreignVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true && !FirewallVpnService.isActive
        if (foreignVpn) {
            DeviceGuard.reactToForeignVpn(this)
            return
        }

        val target = NetworkMonitor.isTarget(this)
        val firewall = Intent(this, FirewallVpnService::class.java)
        if (target && !FirewallVpnService.isActive) {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(firewall) else startService(firewall)
        } else if (!target && FirewallVpnService.isActive) {
            stopService(firewall)
        }
    }

    override fun onDestroy() {
        callback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Throwable) {} }
        callback = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val n = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channel) else Notification.Builder(this)
        return n.setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Mahroch Client")
            .setContentText("پایش شبکه فعال است")
            .setOngoing(true).build()
    }
}
