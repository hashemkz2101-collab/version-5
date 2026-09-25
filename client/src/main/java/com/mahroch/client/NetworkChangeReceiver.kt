package com.mahroch.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build

class NetworkChangeReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        // Backup detection: if a VPN is the active network right now but it
        // is NOT our own FirewallVpnService, a foreign VPN (e.g. a
        // circumvention app) just took over — react even if our service
        // was not running at the time (e.g. after a reboot, or if it had
        // been killed) so this is not only caught by onRevoke().
        // Only react while the user has left management enabled from the
        // Client screen — this is what makes the "غیرفعال‌سازی کامل" button
        // a real off switch instead of a partial one.
        if (Policy.enabled(c) && isForeignVpnActive(c)) {
            DeviceGuard.reactToForeignVpn(c)
        }

        val active = NetworkMonitor.isTarget(c)
        if (active && Policy.enabled(c) && VpnService.prepare(c) == null) {
            val x = Intent(c, FirewallVpnService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(x) else c.startService(x)
        } else if (!active) {
            c.stopService(Intent(c, FirewallVpnService::class.java))
        }
    }

    private fun isForeignVpnActive(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && !FirewallVpnService.isActive
    }
}
