package com.mahroch.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

object NetworkMonitor {
    fun isTarget(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false
        val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ssid = wm.connectionInfo?.ssid?.trim('"') ?: ""
        return ssid == Policy.ssid(c)
    }
}
