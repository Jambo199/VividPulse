package com.jambo199.vividpulse.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import com.jambo199.vividpulse.util.Format
import java.net.Inet4Address

/** Data speed right now, in bytes per second, plus totals since the phone started. */
data class NetSample(
    val rxPerSec: Long,
    val txPerSec: Long,
    val totalRx: Long,
    val totalTx: Long,
    val available: Boolean,
) {
    companion object {
        val EMPTY = NetSample(0, 0, 0, 0, false)
    }
}

/** What the phone is connected through. */
data class Connection(
    val kind: String,
    val name: String?,
    val details: List<Pair<String, String>>,
    /** 0..4 bars, when known. */
    val bars: Int?,
)

class NetworkReader(private val context: Context) {
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L

    fun sample(): NetSample {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return NetSample.EMPTY
        val now = SystemClock.elapsedRealtime()
        val sample = if (lastRx >= 0 && now > lastAt) {
            val secs = (now - lastAt) / 1000f
            NetSample(
                rxPerSec = ((rx - lastRx).coerceAtLeast(0) / secs).toLong(),
                txPerSec = ((tx - lastTx).coerceAtLeast(0) / secs).toLong(),
                totalRx = rx,
                totalTx = tx,
                available = true,
            )
        } else {
            NetSample(0, 0, rx, tx, true)
        }
        lastRx = rx
        lastTx = tx
        lastAt = now
        return sample
    }

    @Suppress("DEPRECATION")
    fun connection(): Connection {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
            ?: return Connection("Offline", null, emptyList(), null)
        val details = mutableListOf<Pair<String, String>>()
        val link = cm.getLinkProperties(network)
        link?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress?.let {
            details += "IP address" to it
        }
        val down = caps.linkDownstreamBandwidthKbps
        val up = caps.linkUpstreamBandwidthKbps
        if (down > 0) details += "Estimated link" to "↓ ${Format.kbps(down)}  ↑ ${Format.kbps(up)}"
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) details += "Metered" to "No" else details += "Metered" to "Yes"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val wm = context.applicationContext.getSystemService(WifiManager::class.java)
                val info = wm?.connectionInfo
                var bars: Int? = null
                if (info != null) {
                    val mhz = info.frequency
                    if (mhz > 0) details.add(0, "Band" to "${bandOf(mhz)} · channel ${channelOf(mhz)}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        wifiStandard(info.wifiStandard)?.let { details.add(0, "Standard" to it) }
                    }
                    if (info.linkSpeed > 0) details += "Link speed" to "${info.linkSpeed} Mbps"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.rxLinkSpeedMbps > 0) {
                        details += "Down / up link" to "${info.rxLinkSpeedMbps} / ${info.txLinkSpeedMbps} Mbps"
                    }
                    val rssi = info.rssi
                    if (rssi in -127..-1) {
                        details += "Signal" to "$rssi dBm"
                        bars = when {
                            rssi >= -55 -> 4
                            rssi >= -67 -> 3
                            rssi >= -75 -> 2
                            rssi >= -85 -> 1
                            else -> 0
                        }
                    }
                }
                val ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                Connection("Wi-Fi", ssid, details, bars)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm = context.getSystemService(TelephonyManager::class.java)
                val operator = tm?.networkOperatorName?.ifBlank { null }
                val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tm?.signalStrength?.level else null
                Connection("Mobile data", operator, details, bars)
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Connection("Ethernet", null, details, null)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Connection("VPN", null, details, null)
            else -> Connection("Connected", null, details, null)
        }
    }

    companion object {
        fun bandOf(mhz: Int): String = when {
            mhz in 2400..2500 -> "2.4 GHz"
            mhz in 4900..5900 -> "5 GHz"
            mhz in 5925..7125 -> "6 GHz"
            mhz >= 57_000 -> "60 GHz"
            else -> "$mhz MHz"
        }

        fun channelOf(mhz: Int): Int = when {
            mhz == 2484 -> 14
            mhz in 2412..2472 -> (mhz - 2407) / 5
            mhz in 5925..7125 -> (mhz - 5950) / 5
            mhz in 4900..5900 -> (mhz - 5000) / 5
            else -> 0
        }

        fun wifiStandard(code: Int): String? = when (code) {
            1 -> "Wi-Fi 1–3 (legacy)"
            4 -> "Wi-Fi 4 (802.11n)"
            5 -> "Wi-Fi 5 (802.11ac)"
            6 -> "Wi-Fi 6 (802.11ax)"
            7 -> "802.11ad (WiGig)"
            8 -> "Wi-Fi 7 (802.11be)"
            else -> null
        }
    }
}
