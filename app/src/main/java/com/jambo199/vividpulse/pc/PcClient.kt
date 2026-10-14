package com.jambo199.vividpulse.pc

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

/** An address typed in: "192.168.1.20", "192.168.1.20:47800" or "http://192.168.1.20:47800/". */
object PcAddress {
    fun parse(text: String): Pair<String, Int>? {
        val t = text.trim().removePrefix("http://").removePrefix("https://").substringBefore('/').trim()
        if (t.isEmpty()) return null
        val colon = t.lastIndexOf(':')
        if (colon > 0 && t.count { it == ':' } == 1) {
            val port = t.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return t.substring(0, colon) to port
        }
        return t to PcClient.PORT
    }
}

/** Vivid Pulse PC turned down the code. */
class WrongCodeException : IOException("Wrong code")

/**
 * Talks to Vivid Pulse PC over the home network: plain HTTP on the local network, with the
 * pairing code in a header. Written on a socket, as Android only allows plain HTTP that way.
 */
object PcClient {
    const val PORT = 47800
    const val DISCOVERY_PORT = 47801
    private const val DISCOVER = "VIVIDPULSE_DISCOVER"

    /** GET [path] (or POST when [post]); the body when the PC answers 200. */
    fun request(host: String, port: Int, path: String, code: String?, post: Boolean = false, timeoutMs: Int = 5_000): String =
        String(requestBytes(host, port, path, code, post, timeoutMs), Charsets.UTF_8)

    /** Like [request], for a picture. */
    fun requestBytes(host: String, port: Int, path: String, code: String?, post: Boolean = false, timeoutMs: Int = 5_000): ByteArray {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val head = buildString {
                append(if (post) "POST " else "GET ").append(path).append(" HTTP/1.1\r\n")
                append("Host: ").append(host).append("\r\n")
                if (code != null) append("X-Pulse-Code: ").append(code).append("\r\n")
                if (post) append("Content-Length: 0\r\n")
                append("Connection: close\r\n\r\n")
            }
            socket.getOutputStream().apply {
                write(head.toByteArray(Charsets.US_ASCII))
                flush()
            }
            val all = ByteArrayOutputStream()
            socket.getInputStream().copyTo(all)
            val bytes = all.toByteArray()
            val split = indexOf(bytes, "\r\n\r\n".toByteArray())
            if (split < 0) throw IOException("The PC sent something unexpected")
            val status = String(bytes, 0, split, Charsets.US_ASCII).lineSequence().first().split(" ").getOrNull(1)?.toIntOrNull()
            val body = bytes.copyOfRange(split + 4, bytes.size)
            return when (status) {
                200 -> body
                401 -> throw WrongCodeException()
                else -> throw IOException("The PC answered ${status ?: "oddly"}")
            }
        }
    }

    /**
     * Asks the local network who's running Vivid Pulse PC: a broadcast on every Wi-Fi/Ethernet
     * network, collecting answers for [waitMs].
     */
    fun discover(waitMs: Int = 2_500): List<PcFound> {
        val found = linkedMapOf<String, PcFound>()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 300
            val message = DISCOVER.toByteArray(Charsets.US_ASCII)
            val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")
            repeat(2) {
                targets.distinct().forEach { address ->
                    runCatching { socket.send(DatagramPacket(message, message.size, address, DISCOVERY_PORT)) }
                }
            }
            val until = System.currentTimeMillis() + waitMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < until) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (e: SocketTimeoutException) {
                    continue
                }
                val host = packet.address.hostAddress ?: continue
                PcParse.found(String(packet.data, 0, packet.length, Charsets.UTF_8), host)?.let { found[host] = it }
            }
        }
        return found.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .filter { it.address is Inet4Address }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())

    private fun indexOf(data: ByteArray, target: ByteArray): Int {
        outer@ for (i in 0..data.size - target.size) {
            for (j in target.indices) if (data[i + j] != target[j]) continue@outer
            return i
        }
        return -1
    }
}
