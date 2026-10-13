package com.jambo199.vividpulse.games

import com.jambo199.vividpulse.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Small blocking HTTP helper. Call it off the main thread. */
object Http {
    private val USER_AGENT = "VividPulse/${BuildConfig.VERSION_NAME} (Android; https://github.com/Jambo199/VividPulse)"

    fun get(url: String, accept: String = "application/json", timeoutMs: Int = 15_000): String {
        var target = url
        repeat(6) {
            val conn = URL(target).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 10_000
                conn.readTimeout = timeoutMs
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", accept)
                when (val code = conn.responseCode) {
                    in 200..299 -> return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    301, 302, 303, 307, 308 -> {
                        val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without a location")
                        target = URL(URL(target), location).toString()
                    }
                    else -> throw IOException("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    fun encode(text: String): String = URLEncoder.encode(text, "UTF-8")
}

fun Throwable.friendlyMessage(): String = when (this) {
    is java.net.UnknownHostException -> "No internet connection"
    is java.net.SocketTimeoutException -> "The server took too long to answer"
    is IOException -> "Couldn't connect (${message ?: "network error"})"
    is org.json.JSONException -> "The store sent something unexpected"
    else -> "Something went wrong (${javaClass.simpleName})"
}
