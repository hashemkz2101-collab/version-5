package com.mahroch.client

import android.app.admin.DeviceAdminReceiver

/**
 * Receiver used when Mahroch Client is provisioned as Device Owner.
 * Device Owner status is what permits the app to configure Always-on VPN
 * and lockdown through DevicePolicyManager.
 */
class MahrochDeviceAdminReceiver : DeviceAdminReceiver()
