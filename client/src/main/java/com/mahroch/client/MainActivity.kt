package com.mahroch.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        ClientControlServer.start(this)

        findViewById<TextView>(R.id.status).text =
            if (Policy.enabled(this)) "وضعیت: فعال" else "وضعیت: خاموش"

        findViewById<TextView>(R.id.network).text =
            "Wi-Fi: ${Policy.ssid(this)}\nIP گوشی: ${localLanIpv4() ?: "در دسترس نیست"}\nControl Server: ${if (ClientControlServer.isRunning()) "فعال روی پورت ${ClientControlServer.PORT}" else "خاموش"}\nGateway: ${Policy.gateway(this)}\nDNS: 8.8.8.8"

        findViewById<TextView>(R.id.domains).text =
            "دامنه‌های مجاز:\n" + Policy.domains(this).joinToString("\n")

        findViewById<Button>(R.id.testServerButton).setOnClickListener {
            ClientControlServer.start(this)
            Thread {
                try {
                    val ip = localLanIpv4() ?: throw IllegalStateException("IP شبکه پیدا نشد")
                    val c = java.net.URL("http://$ip:${ClientControlServer.PORT}/health").openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 2000
                    c.readTimeout = 2000
                    val code = c.responseCode
                    runOnUiThread { toast(if (code == 200) "سرور Client فعال است؛ IP: $ip" else "پاسخ غیرمنتظره: HTTP $code") }
                    c.disconnect()
                } catch (e: Exception) {
                    runOnUiThread { toast("سرور کنترل آماده نیست: ${e.message}") }
                }
            }.start()
        }

        findViewById<Button>(R.id.vpnButton).setOnClickListener {
            enableVpn()
        }

        findViewById<Button>(R.id.manageButton).setOnClickListener {
            requestDeviceAdmin()
        }

        findViewById<Button>(R.id.disableButton).setOnClickListener {
            disableEverything()
        }
        refreshGuardStatus()
    }

    /**
     * Full, user-initiated opt-out: turns the policy flag off (so
     * BootReceiver / NetworkChangeReceiver stop re-enabling anything),
     * stops the running services right away, and removes Device Admin so
     * the foreign-VPN lock reaction can no longer fire either. This is the
     * real "off switch" the disclosure text on this screen promises.
     */
    private fun disableEverything() {
        Policy.setEnabled(this, false)
        stopService(Intent(this, FirewallVpnService::class.java))
        stopService(Intent(this, NetworkWatcherService::class.java))
        if (DeviceGuard.isAdminActive(this)) {
            DeviceGuard.removeAdmin(this)
        }
        findViewById<TextView>(R.id.status).text = "وضعیت: خاموش"
        refreshGuardStatus()
        toast("مدیریت این دستگاه به‌طور کامل غیرفعال شد.")
    }

    private fun refreshGuardStatus() {
        findViewById<Button>(R.id.manageButton).text =
            if (DeviceGuard.isAdminActive(this)) "محافظت فعال است ✓" else "فعال‌سازی محافظت در برابر فیلترشکن"
    }

    private fun requestDeviceAdmin() {
        if (DeviceGuard.isAdminActive(this)) {
            toast("محافظت از قبل فعال است.")
            return
        }
        // Just the standard system consent screen — no ADB, no factory
        // reset, no account removal needed.
        DeviceGuard.requestAdmin(this, ADMIN_REQUEST_CODE)
    }

    private fun enableVpn() {
        Policy.setEnabled(this, true)
        startWatcher()
        val p = VpnService.prepare(this)
        if (p != null) {
            startActivityForResult(p, 101)
        } else {
            startClient()
        }
    }

    private fun startWatcher() {
        val i = Intent(this, NetworkWatcherService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun startClient() {
        val i = Intent(this, FirewallVpnService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        super.onActivityResult(r, c, d)
        if (r == 101 && c == RESULT_OK) startClient()
        if (r == ADMIN_REQUEST_CODE) {
            refreshGuardStatus()
            toast(
                if (DeviceGuard.isAdminActive(this)) "محافظت فعال شد."
                else "محافظت فعال نشد — بدون تأیید Device Admin، فقط از فیلتر دامنه‌ها استفاده می‌شود."
            )
        }
    }

    companion object {
        private const val ADMIN_REQUEST_CODE = 202
    }
}
