package com.mahroch.client

import android.content.Context

object Policy {
    private const val P = "mahroch_policy"
    fun domains(c: Context): Set<String> =
        c.getSharedPreferences(P, 0)
            .getStringSet("domains", setOf("mahroch.com","mahroch-ir.ir","script.google.com"))
            ?.map { it.trim().lowercase().trimEnd('.') }
            ?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun saveDomains(c: Context, value: String) {
        val set = value.lines().map { it.trim().lowercase().trimEnd('.') }
            .filter { it.isNotBlank() && !it.contains(" ") }.toSet()
        c.getSharedPreferences(P, 0).edit().putStringSet("domains", set).apply()
    }

    fun ssid(c: Context) = c.getSharedPreferences(P,0)
        .getString("ssid","HSC_mahroch-2101") ?: "HSC_mahroch-2101"
    fun gateway(c: Context) = c.getSharedPreferences(P,0)
        .getString("gateway","192.168.33.1") ?: "192.168.33.1"
    fun enabled(c: Context) = c.getSharedPreferences(P,0).getBoolean("enabled",false)
    fun setEnabled(c: Context, v:Boolean) = c.getSharedPreferences(P,0).edit().putBoolean("enabled",v).apply()
    fun token(c: Context) = c.getSharedPreferences("device",0)
        .getString("token","mahroch-2101") ?: "mahroch-2101"
}
