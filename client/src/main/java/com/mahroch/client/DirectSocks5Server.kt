package com.mahroch.client

import java.io.*
import java.net.*
import java.util.concurrent.Executors

class DirectSocks5Server(
    private val allowed: () -> Set<String>,
    private val protectSocket: (Socket) -> Boolean
) {
    private val pool = Executors.newCachedThreadPool()
    @Volatile private var running = false
    private var server: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        server = ServerSocket(1080, 32, InetAddress.getByName("127.0.0.1"))
        pool.execute {
            while (running) try {
                val s = server!!.accept()
                pool.execute { handle(s) }
            } catch (_: Throwable) {}
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Throwable) {}
    }

    private fun handle(s: Socket) {
        s.use { sock ->
            val input = sock.getInputStream()
            val output = sock.getOutputStream()
            if (input.read() != 5) return
            val n = input.read()
            if (n < 0) return
            repeat(n) { input.read() }
            output.write(byteArrayOf(5, 0)); output.flush()
            if (input.read() != 5) return
            val cmd = input.read()
            input.read()
            val atyp = input.read()
            if (cmd != 1) { reject(output); return }

            val host = when (atyp) {
                1 -> {
                    val b = ByteArray(4)
                    readFully(input, b)
                    InetAddress.getByAddress(b).hostAddress
                }
                3 -> {
                    val len = input.read(); if (len < 0) return
                    val b = ByteArray(len)
                    readFully(input, b)
                    b.toString(Charsets.UTF_8)
                }
                4 -> {
                    val b = ByteArray(16)
                    readFully(input, b)
                    InetAddress.getByAddress(b).hostAddress
                }
                else -> return
            }
            val portHigh = input.read()
            val portLow = input.read()
            if (portHigh < 0 || portLow < 0) return
            val port = (portHigh shl 8) or portLow

            // Only hostname-based destinations are allowed. Literal IP destinations
            // are rejected so an app cannot bypass the domain policy.
            val h = host.lowercase().trimEnd('.')
            val isIp = h.matches(Regex("\\d+(\\.\\d+){3}")) || h.contains(":")
            val ok = !isIp && allowed().any { h == it || h.endsWith(".$it") }
            if (!ok) { reject(output); return }

            val remote = Socket()
            if (!protectSocket(remote)) { remote.close(); reject(output); return }
            remote.connect(InetSocketAddress(host, port), 10000)
            output.write(byteArrayOf(5,0,0,1,0,0,0,0,0,0)); output.flush()

            val a = pool.submit { copy(sock.getInputStream(), remote.getOutputStream()) }
            val b = pool.submit { copy(remote.getInputStream(), sock.getOutputStream()) }
            try { a.get() } catch (_: Throwable) {}
            try { remote.close() } catch (_: Throwable) {}
            try { b.cancel(true) } catch (_: Throwable) {}
        }
    }

    /** Read exactly [buffer.size] bytes or fail if the stream ends early. */
    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) throw EOFException("Unexpected end of SOCKS5 request")
            if (n == 0) continue
            offset += n
        }
    }

    private fun copy(i: InputStream, o: OutputStream) {
        val buf = ByteArray(32768)
        while (running) {
            val n = i.read(buf)
            if (n < 0) break
            o.write(buf, 0, n); o.flush()
        }
    }

    private fun reject(o: OutputStream) {
        o.write(byteArrayOf(5,2,0,1,0,0,0,0,0,0)); o.flush()
    }
}
