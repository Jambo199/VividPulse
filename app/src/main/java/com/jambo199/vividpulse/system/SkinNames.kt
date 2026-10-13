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

    /**
     * Every other maker's version of Android, from the properties each one sets ([prop] reads one,
     * "" when unset). Null for phones that run Android as it comes (Pixel, Motorola, Sony…).
     */
    fun other(maker: String, brand: String, prop: (String) -> String): String? {
        val who = "$maker $brand".lowercase()
        val oneUi = prop("ro.build.version.oneui").toIntOrNull()
        if (oneUi != null && oneUi > 0) return "One UI " + tidy("${oneUi / 10000}.${oneUi / 100 % 100}")
        prop("ro.mi.os.version.name").ifBlank { null }?.let { return "HyperOS " + tidy(it.removePrefix("OS")) }
        prop("ro.miui.ui.version.name").ifBlank { null }?.let { return "MIUI " + tidy(it.removePrefix("V")) }
        prop("ro.build.version.realmeui").ifBlank { null }?.let { return "realme UI " + version(it) }
        prop("ro.build.version.oplusrom").ifBlank { null }?.let {
            return (if ("oneplus" in who) "OxygenOS " else "ColorOS ") + version(it).substringBefore('.')
        }
        prop("hw_sc.build.platform.version").ifBlank { null }?.let { return "HarmonyOS " + version(it) }
        prop("ro.build.version.magic").ifBlank { null }?.let { return "MagicOS " + version(it) }
        prop("ro.build.version.emui").ifBlank { null }?.let { return "EMUI " + version(it) }
        return null
    }

    /** "V15.0.0", "MagicOS_8.0.0", "EmotionUI_14.2.0" → "15", "8", "14.2". */
    private fun version(raw: String): String {
        val v = Regex("\\d+(?:\\.\\d+)?").find(raw)?.value ?: return raw.trim()
        return tidy(v)
    }

    /** "16.0" → "16"; anything else stays as it is. */
    private fun tidy(v: String): String = v.trim().removeSuffix(".0")
}
