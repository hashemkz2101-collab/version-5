package com.mahroch.manager

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : Activity() {

    private lateinit var clients: MutableList<ManagedClient>
    private lateinit var listContainer: LinearLayout
    private lateinit var emptyState: TextView

    /** Parallel to `clients` — the row View currently shown for that client, if any. */
    private val rowViews = mutableListOf<View?>()

    private val defaultDomains = "mahroch.com\nmahroch-ir.ir\nscript.google.com"

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)

        clients = ClientStore.load(this)
        listContainer = findViewById(R.id.clientList)
        emptyState = findViewById(R.id.emptyState)

        findViewById<View>(R.id.addButton).setOnClickListener { showClientDialog(null) }
        findViewById<View>(R.id.refreshButton).setOnClickListener { refreshAll() }

        renderList()
    }

    // ---------- Rendering ----------

    private fun renderList() {
        listContainer.removeAllViews()
        rowViews.clear()

        emptyState.visibility = if (clients.isEmpty()) View.VISIBLE else View.GONE

        for (index in clients.indices) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_client, listContainer, false)
            bindRow(row, index)
            listContainer.addView(row)
            rowViews.add(row)
        }
    }

    private fun bindRow(row: View, index: Int) {
        val client = clients[index]
        row.findViewById<TextView>(R.id.name).text = client.name
        applyStatusToRow(row, client)

        row.findViewById<View>(R.id.menuButton).setOnClickListener { anchor ->
            showRowMenu(anchor, index)
        }
        row.setOnClickListener { showRowMenu(row.findViewById(R.id.menuButton), index) }
    }

    private fun applyStatusToRow(row: View, client: ManagedClient) {
        val dot = row.findViewById<View>(R.id.dot)
        val subtitle = row.findViewById<TextView>(R.id.subtitle)
        val statusText = when (client.status) {
            ClientStatus.ONLINE -> "آنلاین"
            ClientStatus.CHECKING -> "در حال بررسی..."
            ClientStatus.OFFLINE -> "آفلاین"
            ClientStatus.UNKNOWN -> "وضعیت نامشخص"
        }
        dot.setBackgroundResource(
            if (client.status == ClientStatus.ONLINE) R.drawable.dot_online else R.drawable.dot_offline
        )
        subtitle.text = "${client.ip} · $statusText"
    }

    private fun showRowMenu(anchor: View, index: Int) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.client_actions, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_test -> { testClient(index); true }
                R.id.action_push -> { showPolicyDialog(index); true }
                R.id.action_edit -> { showClientDialog(index); true }
                R.id.action_delete -> { confirmDelete(index); true }
                else -> false
            }
        }
        popup.show()
    }

    // ---------- Add / edit / delete ----------

    private fun showClientDialog(editIndex: Int?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_client, null)
        val nameInput = view.findViewById<EditText>(R.id.inputName)
        val ipInput = view.findViewById<EditText>(R.id.inputIp)
        val tokenInput = view.findViewById<EditText>(R.id.inputToken)

        if (editIndex != null) {
            val existing = clients[editIndex]
            nameInput.setText(existing.name)
            ipInput.setText(existing.ip)
            tokenInput.setText(existing.token)
        }

        AlertDialog.Builder(this)
            .setTitle(if (editIndex == null) "افزودن کلاینت" else "ویرایش کلاینت")
            .setView(view)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = nameInput.text.toString().trim()
                val ip = ipInput.text.toString().trim()
                val token = tokenInput.text.toString().trim()
                if (name.isBlank() || ip.isBlank()) {
                    toast("اسم و آی‌پی را وارد کنید.")
                    return@setPositiveButton
                }
                if (editIndex == null) {
                    clients.add(ManagedClient(name, ip, token))
                } else {
                    clients[editIndex].apply { this.name = name; this.ip = ip; this.token = token }
                }
                ClientStore.save(this, clients)
                renderList()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun confirmDelete(index: Int) {
        val client = clients[index]
        AlertDialog.Builder(this)
            .setTitle("حذف کلاینت")
            .setMessage("«${client.name}» حذف شود؟")
            .setPositiveButton("حذف") { _, _ ->
                clients.removeAt(index)
                ClientStore.save(this, clients)
                renderList()
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ---------- Health check ----------

    private fun refreshAll() {
        if (clients.isEmpty()) {
            toast("ابتدا یک کلاینت اضافه کنید.")
            return
        }
        for (index in clients.indices) {
            clients[index].status = ClientStatus.CHECKING
            rowViews.getOrNull(index)?.let { applyStatusToRow(it, clients[index]) }
        }
        // One thread per client so all /health requests go out in parallel.
        for (index in clients.indices) {
            Thread { checkHealth(index) }.start()
        }
    }

    private fun testClient(index: Int) {
        clients[index].status = ClientStatus.CHECKING
        rowViews.getOrNull(index)?.let { applyStatusToRow(it, clients[index]) }
        Thread { checkHealth(index, showToast = true) }.start()
    }

    private fun checkHealth(index: Int, showToast: Boolean = false) {
        val client = clients[index]
        val newStatus = try {
            val c = open("http://${client.ip}:8765/health")
            c.requestMethod = "GET"
            val code = c.responseCode
            c.disconnect()
            if (code == 200) ClientStatus.ONLINE else ClientStatus.OFFLINE
        } catch (e: Exception) {
            ClientStatus.OFFLINE
        }
        runOnUiThread {
            // The list may have changed (add/remove) while the request was in
            // flight — only apply the result if this row still refers to the
            // same client.
            if (index < clients.size && clients[index] === client) {
                client.status = newStatus
                rowViews.getOrNull(index)?.let { applyStatusToRow(it, client) }
            }
            if (showToast) {
                toast(
                    if (newStatus == ClientStatus.ONLINE) "${client.name}: آنلاین"
                    else "${client.name}: پاسخ نداد"
                )
            }
        }
    }

    // ---------- Push policy ----------

    private fun showPolicyDialog(index: Int) {
        val client = clients[index]
        if (client.token.isBlank()) {
            toast("برای این کلاینت توکنی ثبت نشده — ابتدا آن را ویرایش کنید.")
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_policy, null)
        val domainsInput = view.findViewById<EditText>(R.id.inputDomains)
        domainsInput.setText(defaultDomains)

        AlertDialog.Builder(this)
            .setTitle("ارسال سیاست به «${client.name}»")
            .setView(view)
            .setPositiveButton("ارسال") { _, _ ->
                pushPolicy(client, domainsInput.text.toString())
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun pushPolicy(client: ManagedClient, domainsText: String) {
        Thread {
            try {
                val c = open("http://${client.ip}:8765/policy")
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                c.setRequestProperty("X-Mahroch-Token", client.token)
                val body = "token=${URLEncoder.encode(client.token, "UTF-8")}&domains=${URLEncoder.encode(domainsText, "UTF-8")}"
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                c.disconnect()
                runOnUiThread {
                    toast(
                        when (code) {
                            200 -> "سیاست با موفقیت به «${client.name}» ارسال شد."
                            401 -> "کد اتصال «${client.name}» اشتباه است."
                            400 -> "دامنه‌های ارسالی معتبر نیستند."
                            else -> "پاسخ کلاینت: HTTP $code"
                        }
                    )
                }
            } catch (e: Exception) {
                runOnUiThread { toast("خطا در ارسال به «${client.name}»: ${e.message}") }
            }
        }.start()
    }

    // ---------- Helpers ----------

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 2000; readTimeout = 2000 }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
