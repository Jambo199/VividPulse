package com.jambo199.vividpulse.system

/** Names for phone makers' own versions of Android, worked out from the phone's properties. */
object SkinNames {
    /**
     * vivo's Android is OriginOS: in China since 2020, and worldwide from OriginOS 6 (Android 16),
     * which replaced Funtouch OS. Phones often still carry the old Funtouch property names, with the
     * Android version as the "OS version", so taking those at face value names the wrong system.
     * In order: an OriginOS name in any property wins; Android 16 and later is OriginOS; a
     * China build (no "_EX_" export tag, or not marked overseas) is OriginOS; otherwise Funtouch OS.
     *
     * [displayId] is ro.vivo.os.build.display.id, [osName] ro.vivo.os.name, [osVersion]
     * ro.vivo.os.version, [build] the vivo build number (ro.vivo.product.version or
     * ro.build.version.bbk, e.g. "PD2453F_EX_A_15.2.12.4.W30"), [overseas] ro.vivo.product.overseas.
     */
    fun vivo(displayId: String, osName: String, osVersion: String, build: String, overseas: String, androidMajor: Int): String {
        val named = Regex("(?i)origin\\s*_?os[ _]*(ocean|\\d+(?:\\.\\d+)?)?")
            .find(listOf(displayId, osName, build).joinToString(" "))
        if (named != null) {
            val version = named.groups[1]?.value?.let(::tidy)?.replaceFirstChar { it.uppercase() }
            return "OriginOS " + (version ?: originFor(androidMajor))
        }
        val export = build.contains("_EX_", ignoreCase = true) || overseas.equals("yes", ignoreCase = true)
        val china = !export && (overseas.equals("no", ignoreCase = true) || build.contains("_A_", ignoreCase = true))
        if (androidMajor >= 16 || china) return "OriginOS ${originFor(androidMajor)}"
        val version = Regex("\\d+(?:\\.\\d+)?").find(osVersion)?.value?.let(::tidy) ?: androidMajor.toString()
        return "Funtouch OS $version"
    }

    /** OriginOS releases follow Android: OriginOS 3 came with Android 13, OriginOS 6 with Android 16. */
    fun originFor(androidMajor: Int): String = when {
        androidMajor >= 13 -> (androidMajor - 10).toString()
        androidMajor == 12 -> "Ocean"
        else -> "1"
    }

    /** "16.0" → "16"; anything else stays as it is. */
    private fun tidy(v: String): String = v.trim().removeSuffix(".0")
}
