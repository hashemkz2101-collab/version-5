package com.mahroch.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Policy.enabled(c)) return
        if (VpnService.prepare(c) != null) return

        // Start the watcher (not the firewall directly): it does the
        // immediate isTarget() check itself AND keeps a live NetworkCallback
        // registered afterwards, so future network changes (Wi-Fi switch,
        // rival VPN connect/disconnect) are caught reliably too — a single
        // one-shot check at boot is not enough on its own.
        val x = Intent(c, NetworkWatcherService::class.java)
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(x)
        else c.startService(x)
    }
}
