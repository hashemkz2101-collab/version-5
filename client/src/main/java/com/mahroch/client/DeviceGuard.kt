package com.mahroch.client

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Lightweight "detect and react" guard for devices that are NOT Device Owner
 * (no factory reset / no account removal required). This cannot block a
 * competing VPN app the way Device Owner + lockdown can — Android does not
 * expose that power to a plain Device Admin — but it can detect the moment
 * a foreign VPN takes over and react immediately by locking the screen.
 */
object DeviceGuard {
    private const val TAG = "MahrochGuard"

    private fun adminComponent(context: Context) =
        ComponentName(context.applicationContext, MahrochDeviceAdminReceiver::class.java)

    private fun dpm(context: Context) =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isAdminActive(context: Context): Boolean =
        dpm(context).isAdminActive(adminComponent(context))

    /** User-initiated removal of Device Admin — part of the real "off switch". */
    fun removeAdmin(context: Context) {
        try {
            dpm(context).removeActiveAdmin(adminComponent(context))
        } catch (e: Throwable) {
            Log.e(TAG, "removeActiveAdmin() failed", e)
        }
    }

    /** Launches the system screen where the user approves Device Admin. */
    fun requestAdmin(activity: Activity, requestCode: Int) {
        val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent(activity))
            putExtra(
                android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "برای قفل‌کردن فوری گوشی هنگام تشخیص فیلترشکن، Mahroch Client به دسترسی مدیریت دستگاه نیاز دارد."
            )
        }
        activity.startActivityForResult(intent, requestCode)
    }

    /**
     * Called the moment a foreign VPN is detected (either via onRevoke() in
     * FirewallVpnService, or via NetworkChangeReceiver). Locks the screen
     * immediately if we have Device Admin permission; otherwise just logs it
     * since we have no other enforcement power without Device Owner.
     */
    fun reactToForeignVpn(context: Context) {
        Log.w(TAG, "Foreign VPN detected on this device")
        if (!isAdminActive(context)) {
            Log.w(TAG, "Device Admin not active — cannot lock, only logging")
            return
        }
        try {
            dpm(context).lockNow()
        } catch (e: Throwable) {
            Log.e(TAG, "lockNow() failed", e)
        }
    }
}
