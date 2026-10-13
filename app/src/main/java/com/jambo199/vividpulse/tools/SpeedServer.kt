package com.jambo199.vividpulse.tools

import com.jambo199.vividpulse.games.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Which Cloudflare data centre the speed test reaches, and through which internet provider. */
data class ServerInfo(val colo: String?, val provider: String?, val country: String?) {
    /** "London (LHR)". */
    val place: String?
        get() = colo?.let { code -> SpeedServer.CITIES[code]?.let { "$it ($code)" } ?: code }
}

object SpeedServer {
    suspend fun lookup(): ServerInfo? = withContext(Dispatchers.IO) {
        runCatching { parseMeta(Http.get("https://speed.cloudflare.com/meta")) }.getOrNull()
            ?: runCatching { parseTrace(Http.get("https://speed.cloudflare.com/cdn-cgi/trace", accept = "text/plain")) }.getOrNull()
    }

    fun parseMeta(json: String): ServerInfo? {
        val o = JSONObject(json)
        val colo = o.optString("colo").ifBlank { null } ?: return null
        return ServerInfo(colo, o.optString("asOrganization").ifBlank { null }, o.optString("country").ifBlank { null })
    }

    fun parseTrace(text: String): ServerInfo? {
        val fields = text.lines().mapNotNull { line ->
            val k = line.substringBefore('=', "")
            if (k.isEmpty()) null else k to line.substringAfter('=')
        }.toMap()
        val colo = fields["colo"]?.ifBlank { null } ?: return null
        return ServerInfo(colo, null, fields["loc"])
    }

    /** Cloudflare names its data centres after the nearest airport. */
    val CITIES = mapOf(
        "LHR" to "London", "MAN" to "Manchester", "EDI" to "Edinburgh", "DUB" to "Dublin", "AMS" to "Amsterdam",
        "FRA" to "Frankfurt", "CDG" to "Paris", "MAD" to "Madrid", "MXP" to "Milan", "ARN" to "Stockholm",
        "WAW" to "Warsaw", "VIE" to "Vienna", "ZRH" to "Zurich", "HKG" to "Hong Kong", "SIN" to "Singapore",
        "NRT" to "Tokyo", "KIX" to "Osaka", "ICN" to "Seoul", "TPE" to "Taipei", "SYD" to "Sydney",
        "MEL" to "Melbourne", "AKL" to "Auckland", "LAX" to "Los Angeles", "SJC" to "San Jose", "SEA" to "Seattle",
        "ORD" to "Chicago", "DFW" to "Dallas", "IAD" to "Ashburn", "EWR" to "Newark", "ATL" to "Atlanta",
        "MIA" to "Miami", "YYZ" to "Toronto", "YVR" to "Vancouver", "BOM" to "Mumbai", "DEL" to "Delhi",
        "MAA" to "Chennai", "BLR" to "Bangalore", "KUL" to "Kuala Lumpur", "BKK" to "Bangkok", "MNL" to "Manila",
        "CGK" to "Jakarta", "DXB" to "Dubai", "JNB" to "Johannesburg", "GRU" to "São Paulo", "PVG" to "Shanghai",
        "CAN" to "Guangzhou", "SZX" to "Shenzhen", "PEK" to "Beijing",
    )
}
