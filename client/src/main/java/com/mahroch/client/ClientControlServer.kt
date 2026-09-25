package com.mahroch.client

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors

/** LAN control endpoint used by Mahroch Manager. */
object ClientControlServer {
    private const val TAG = "MahrochControl"
    const val PORT = 8765
    private const val MAX_HEADER_BYTES = 16 * 1024
    private const val MAX_BODY_BYTES = 64 * 1024
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false
    @Volatile private var server: ServerSocket? = null

    fun start(context: Context) {
        if (running) return
        synchronized(this) {
            if (running) return
            val app = context.applicationContext
            try {
                server = ServerSocket()
                server!!.reuseAddress = true
                server!!.bind(InetSocketAddress("0.0.0.0", PORT), 32)
                running = true
                pool.execute {
                    while (running) {
                        try {
                            val socket = server?.accept() ?: break
                            pool.execute { handle(app, socket) }
                        } catch (e: Throwable) {
                            if (running) Log.e(TAG, "Accept failed", e)
                        }
                    }
                }
                Log.i(TAG, "Control server listening on 0.0.0.0:$PORT")
            } catch (e: Throwable) {
                running = false
                try { server?.close() } catch (_: Throwable) {}
                server = null
                Log.e(TAG, "Could not start control server", e)
            }
        }
    }

    fun isRunning(): Boolean = running

    private fun handle(context: Context, socket: java.net.Socket) {
        socket.use { s ->
            s.soTimeout = 5000
            try {
                val input = BufferedInputStream(s.getInputStream())
                val output = BufferedOutputStream(s.getOutputStream())
                val request = readRequest(input)
                when {
                    request.method == "GET" && request.path == "/health" ->
                        respond(output, 200, "application/json", "{\"ok\":true,\"service\":\"mahroch-client\",\"port\":$PORT}")
                    request.method == "POST" && request.path == "/policy" ->
                        handlePolicy(context, request, output)
                    else -> respond(output, 404, "text/plain; charset=utf-8", "Not found")
                }
            } catch (e: RequestException) {
                respond(BufferedOutputStream(s.getOutputStream()), e.status, "text/plain; charset=utf-8", e.message ?: "Bad request")
            } catch (e: Throwable) {
                Log.e(TAG, "Request failed", e)
                try { respond(BufferedOutputStream(s.getOutputStream()), 500, "text/plain; charset=utf-8", "Internal server error") } catch (_: Throwable) {}
            }
        }
    }

    private fun handlePolicy(context: Context, request: HttpRequest, output: BufferedOutputStream) {
        val token = request.headers["x-mahroch-token"] ?: request.form["token"] ?: ""
        if (!sameToken(token, Policy.token(context))) {
            respond(output, 401, "text/plain; charset=utf-8", "Invalid Client token")
            return
        }
        val domains = request.form["domains"] ?: ""
        val cleaned = domains.lines()
            .map { it.trim().lowercase().trimEnd('.') }
            .filter { it.isNotBlank() && !it.contains(" ") }
            .filter { isValidDomain(it) }
            .distinct()
        if (cleaned.isEmpty()) {
            respond(output, 400, "text/plain; charset=utf-8", "No valid domains supplied")
            return
        }
        Policy.saveDomains(context, cleaned.joinToString("\n"))
        Policy.setEnabled(context, true)
        respond(output, 200, "application/json", "{\"ok\":true,\"domains\":${cleaned.size}}")
    }

    private fun sameToken(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(StandardCharsets.UTF_8), b.toByteArray(StandardCharsets.UTF_8))

    private fun isValidDomain(value: String): Boolean {
        if (value.length > 253 || value.startsWith('.') || value.endsWith('.')) return false
        return value.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.first().isLetterOrDigit() && label.last().isLetterOrDigit() &&
                label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
    }

    private fun readRequest(input: BufferedInputStream): HttpRequest {
        val headerBytes = readUntilHeaderEnd(input)
        val headerText = String(headerBytes, StandardCharsets.ISO_8859_1)
        val lines = headerText.split("\r\n")
        val first = lines.firstOrNull()?.split(' ') ?: throw RequestException(400, "Malformed request")
        if (first.size < 2) throw RequestException(400, "Malformed request line")
        val headers = mutableMapOf<String, String>()
        for (line in lines.drop(1)) {
            val p = line.indexOf(':')
            if (p > 0) headers[line.substring(0, p).trim().lowercase()] = line.substring(p + 1).trim()
        }
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) throw RequestException(413, "Request body too large")
        val body = ByteArray(contentLength)
        readFully(input, body)
        return HttpRequest(first[0].uppercase(), first[1].substringBefore('?'), headers, parseForm(String(body, StandardCharsets.UTF_8)))
    }

    private fun readUntilHeaderEnd(input: BufferedInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        var b1 = -1
        var b2 = -1
        var b3 = -1
        while (out.size() < MAX_HEADER_BYTES) {
            val b4 = input.read()
            if (b4 < 0) throw RequestException(400, "Unexpected end of request")
            out.write(b4)
            if (b1 == '\r'.code && b2 == '\n'.code && b3 == '\r'.code && b4 == '\n'.code) return out.toByteArray()
            b1 = b2; b2 = b3; b3 = b4
        }
        throw RequestException(431, "Request headers too large")
    }

    private fun readFully(input: BufferedInputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) throw EOFException("Unexpected end of body")
            offset += n
        }
    }

    private fun parseForm(body: String): Map<String, String> {
        if (body.isBlank()) return emptyMap()
        return body.split('&').associate { part ->
            val p = part.indexOf('=')
            val k = if (p >= 0) part.substring(0, p) else part
            val v = if (p >= 0) part.substring(p + 1) else ""
            java.net.URLDecoder.decode(k, "UTF-8") to java.net.URLDecoder.decode(v, "UTF-8")
        }
    }

    private fun respond(output: BufferedOutputStream, status: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; 413 -> "Payload Too Large"; 431 -> "Request Header Fields Too Large"; else -> "Internal Server Error"
        }
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray(StandardCharsets.ISO_8859_1)); output.write(bytes); output.flush()
    }

    private data class HttpRequest(val method: String, val path: String, val headers: Map<String, String>, val form: Map<String, String>)
    private class RequestException(val status: Int, message: String) : Exception(message)
}

fun Context.localLanIpv4(): String? {
    try {
        val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (!ni.isUp || ni.isLoopback) continue
            val addresses = ni.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is java.net.Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    val host = address.hostAddress ?: continue
                    if (!host.startsWith("198.18.")) return host
                }
            }
        }
    } catch (_: Throwable) {}
    return null
}
