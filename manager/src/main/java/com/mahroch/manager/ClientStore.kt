package com.mahroch.manager

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Simple local persistence for the saved client list (name + ip + token). */
object ClientStore {
    private const val PREF = "mahroch_manager_clients"
    private const val KEY = "clients_json"

    fun load(context: Context): MutableList<ManagedClient> {
        val json = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return mutableListOf()
        val list = mutableListOf<ManagedClient>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ManagedClient(
                        name = o.optString("name", ""),
                        ip = o.optString("ip", ""),
                        token = o.optString("token", "")
                    )
                )
            }
        } catch (_: Exception) {
            // Corrupt/old data: start fresh rather than crash.
        }
        return list
    }

    fun save(context: Context, clients: List<ManagedClient>) {
        val arr = JSONArray()
        for (client in clients) {
            val o = JSONObject()
            o.put("name", client.name)
            o.put("ip", client.ip)
            o.put("token", client.token)
            arr.put(o)
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString())
            .apply()
    }
}
