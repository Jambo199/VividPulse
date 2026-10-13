package com.jambo199.vividpulse.tools

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** A Private DNS server (DNS over TLS). [host] null means Android's automatic setting. */
data class DnsProvider(val name: String, val host: String?, val about: String, val blocksAds: Boolean = false)

/** What the phone's Private DNS is set to. */
data class DnsState(val mode: String, val host: String?) {
    val label: String
        get() = when (mode) {
            MODE_HOST -> host ?: "Custom"
            MODE_OFF -> "Off"
            else -> "Automatic"
        }

    companion object {
        const val MODE_OFF = "off"
        const val MODE_AUTO = "opportunistic"
        const val MODE_HOST = "hostname"
    }
}

/**
 * Android's Private DNS (Android 9+): encrypted DNS for the whole phone, on Wi-Fi and mobile data.
 * Changing it from an app needs a permission only a computer can grant (adb), once. Without it,
 * the app copies the server's name and opens the setting.
 */
object PrivateDns {
    val PROVIDERS = listOf(
        DnsProvider("Automatic", null, "Your network's own DNS, encrypted when it can be (Android's default)"),
        DnsProvider("Cloudflare", "one.one.one.one", "Usually the fastest. Doesn't keep logs of what you look up"),
        DnsProvider("Cloudflare + malware blocking", "security.cloudflare-dns.com", "Cloudflare, also blocking known malware sites"),
        DnsProvider("Google", "dns.google", "Fast and reliable everywhere"),
        DnsProvider("Quad9", "dns.quad9.net", "Blocks known malicious and phishing sites"),
        DnsProvider("AdGuard", "dns.adguard-dns.com", "Blocks most ads and trackers in apps and games", blocksAds = true),
        DnsProvider("Mullvad ad blocking", "adblock.dns.mullvad.net", "Blocks ads and trackers, no logs", blocksAds = true),
        DnsProvider("AdGuard Family", "family.adguard-dns.com", "Blocks ads, adult sites and turns on safe search", blocksAds = true),
    )

    /** The command that lets the app switch DNS itself, run once from a computer with USB debugging. */
    fun grantCommand(context: Context) =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    fun canApply(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** The setting as it is now (null when Android won't say). */
    fun current(context: Context): DnsState? {
        val mode = runCatching { Settings.Global.getString(context.contentResolver, "private_dns_mode") }.getOrNull()
        val host = runCatching { Settings.Global.getString(context.contentResolver, "private_dns_specifier") }.getOrNull()
        if (mode != null) return DnsState(mode, host?.ifBlank { null })
        // Not readable: what the connection in use reports instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val server = lp.privateDnsServerName
        return when {
            server != null -> DnsState(DnsState.MODE_HOST, server)
            lp.isPrivateDnsActive -> DnsState(DnsState.MODE_AUTO, null)
            else -> null
        }
    }

    /** Switches to [provider]; false when the app isn't allowed to. */
    fun apply(context: Context, provider: DnsProvider): Boolean {
        if (!canApply(context)) return false
        return runCatching {
            val cr = context.contentResolver
            if (provider.host == null) {
                Settings.Global.putString(cr, "private_dns_mode", DnsState.MODE_AUTO)
            } else {
                Settings.Global.putString(cr, "private_dns_specifier", provider.host)
                Settings.Global.putString(cr, "private_dns_mode", DnsState.MODE_HOST)
            }
        }.getOrDefault(false)
    }

    /** Copies the server name, ready to paste into Android's Private DNS setting. */
    fun copy(context: Context, host: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Private DNS", host))
    }

    /** Opens Android's Private DNS setting, or the nearest page that has it. */
    fun openSettings(context: Context) {
        val tries = listOf("android.settings.PRIVATE_DNS_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)
        for (action in tries) {
            try {
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (ignored: ActivityNotFoundException) {
            } catch (ignored: SecurityException) {
            }
        }
    }

    /**
     * How long it takes to reach the server and set up the encrypted connection, in ms: the best
     * of three tries. Null when it can't be reached.
     */
    suspend fun responseMs(host: String): Long? = withContext(Dispatchers.IO) {
        (1..3).mapNotNull { once(host) }.minOrNull()
    }

    private fun once(host: String): Long? = runCatching {
        val start = SystemClock.elapsedRealtime()
        (SSLSocketFactory.getDefault().createSocket() as SSLSocket).use { s ->
            s.connect(InetSocketAddress(host, 853), 3_000)
            s.soTimeout = 3_000
            s.startHandshake()
        }
        SystemClock.elapsedRealtime() - start
    }.getOrNull()
}
