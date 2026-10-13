package com.jambo199.vividpulse.system

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import com.jambo199.vividpulse.util.Format
import java.util.Locale

data class SpecRow(val label: String, val value: String)

data class SpecSection(val title: String, val rows: List<SpecRow>)

/** What the GPU calls itself through OpenGL. */
data class GpuInfo(val renderer: String, val vendor: String, val glVersion: String)

/** The phone's fixed specs. Build it off the main thread: it reads files and starts OpenGL. */
class DeviceSpecs(private val context: Context, private val cpu: CpuReader) {
    val brand: String = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.US) }

    val name: String = run {
        val market = listOf("ro.vivo.market.name", "ro.product.marketname", "ro.product.vendor.marketname", "ro.config.marketing_name")
            .map { SysFs.prop(it) }.firstOrNull { it.isNotBlank() }
            ?: runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
                ?.takeIf { it.isNotBlank() && !it.equals(Build.MODEL, ignoreCase = true) }
            ?: Build.MODEL
        if (market.lowercase(Locale.US).startsWith(Build.MANUFACTURER.lowercase(Locale.US))) market else "$brand $market"
    }

    /** "OriginOS 5" and the like, when the maker's own Android skin says what it is. */
    val skin: String? = run {
        val vivoName = SysFs.prop("ro.vivo.os.name")
        val vivoVersion = SysFs.prop("ro.vivo.os.version")
        val oneUi = SysFs.prop("ro.build.version.oneui").toIntOrNull()
        val hyperOs = SysFs.prop("ro.mi.os.version.name")
        val miui = SysFs.prop("ro.miui.ui.version.name")
        val coloros = SysFs.prop("ro.build.version.oplusrom")
        when {
            vivoVersion.isNotBlank() -> "${vivoName.ifBlank { "OriginOS" }.replace("_", " ")} $vivoVersion".trim()
            oneUi != null -> "One UI ${oneUi / 10000}.${oneUi / 100 % 100}"
            hyperOs.isNotBlank() -> "HyperOS ${hyperOs.removePrefix("OS")}"
            miui.isNotBlank() -> "MIUI ${miui.removePrefix("V")}"
            coloros.isNotBlank() -> "ColorOS ${coloros.removePrefix("V")}"
            else -> null
        }
    }

    val androidVersion: String = "Android ${Build.VERSION.RELEASE}"

    val socModel: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null

    val socName: String? = socModel?.let { SocNames.friendly(it) }
        ?: cpu.hardware?.let { SocNames.friendly(it) ?: it }
        ?: SysFs.prop("ro.soc.model").ifBlank { null }?.let { SocNames.friendly(it) ?: it }

    val gpu: GpuInfo? = readGpu()

    val ramBytes: Long = context.getSystemService(ActivityManager::class.java)?.let {
        val info = ActivityManager.MemoryInfo()
        it.getMemoryInfo(info)
        info.totalMem
    } ?: 0L

    val vulkan: String? = run {
        val pm = context.packageManager
        listOf(0x404000 to "1.4", 0x403000 to "1.3", 0x402000 to "1.2", 0x401000 to "1.1", 0x400000 to "1.0")
            .firstOrNull { pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, it.first) }?.second
    }

    /** One line for the top of the app: "Snapdragon 8 Elite · Android 15 · OriginOS 5". */
    val summary: String get() = listOfNotNull(socName, androidVersion, skin).joinToString(" · ")

    fun sections(display: List<SpecRow>, storageTotal: Long): List<SpecSection> {
        val pm = context.packageManager
        val am = context.getSystemService(ActivityManager::class.java)
        val elapsed = SystemClock.elapsedRealtime()
        val awake = SystemClock.uptimeMillis()
        return listOf(
            SpecSection(
                "Phone",
                listOfNotNull(
                    SpecRow("Name", name),
                    SpecRow("Model", Build.MODEL),
                    SpecRow("Maker", brand),
                    SpecRow("Codename", Build.DEVICE),
                    SpecRow("RAM", "${MemInfo.marketedRamGb(ramBytes)} GB (${Format.bytes(ramBytes)} usable)"),
                    if (storageTotal > 0) SpecRow("Storage", "${MemInfo.marketedStorageGb(storageTotal)} GB") else null,
                ),
            ),
            SpecSection(
                "Software",
                listOfNotNull(
                    SpecRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
                    skin?.let { SpecRow("System", it) },
                    SpecRow("Security update", Build.VERSION.SECURITY_PATCH.ifBlank { "Unknown" }),
                    SpecRow("Build", Build.DISPLAY),
                    SpecRow("Kernel", (System.getProperty("os.version") ?: "Unknown").substringBefore('-').ifBlank { "Unknown" }),
                    Build.getRadioVersion()?.takeIf { it.isNotBlank() }?.let { SpecRow("Baseband", it) },
                    SpecRow("Bootloader", Build.BOOTLOADER),
                    SpecRow("Seamless updates", if (SysFs.prop("ro.build.ab_update") == "true") "Yes (A/B)" else "No"),
                    SpecRow("Up for", Format.duration(elapsed)),
                    SpecRow("Deep sleep", "${((elapsed - awake) * 100 / elapsed.coerceAtLeast(1))}% of that time"),
                ),
            ),
            SpecSection(
                "Processor",
                listOfNotNull(
                    socName?.let { SpecRow("Chip", it) },
                    socModel?.takeIf { it != socName }?.let { SpecRow("Chip model", it) },
                    SpecRow("CPU", "${cpu.cores.size} cores"),
                ) + cpu.clusters.map { c ->
                    SpecRow(c.name, "${c.cores.size} × ${c.part ?: "core"}" + (c.maxKHz.takeIf { it > 0 }?.let { " @ ${Format.mhz(it / 1000)}" } ?: ""))
                } + listOfNotNull(
                    SpecRow("Instruction sets", Build.SUPPORTED_ABIS.joinToString()),
                    cpu.clusters.firstOrNull()?.governor?.let { SpecRow("Governor", it) },
                    gpu?.let { SpecRow("GPU", it.renderer) },
                    am?.deviceConfigurationInfo?.glEsVersion?.let { SpecRow("OpenGL ES", it) },
                    vulkan?.let { SpecRow("Vulkan", it) },
                ),
            ),
            SpecSection("Display", display),
            SpecSection("Connectivity & features", features(pm)),
        )
    }

    private fun features(pm: PackageManager): List<SpecRow> {
        fun has(f: String) = pm.hasSystemFeature(f)
        fun yesNo(b: Boolean) = if (b) "Yes" else "No"
        val rows = mutableListOf<SpecRow>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            val best = listOf(8 to "Wi-Fi 7", 6 to "Wi-Fi 6", 5 to "Wi-Fi 5", 4 to "Wi-Fi 4")
                .firstOrNull { runCatching { wm?.isWifiStandardSupported(it.first) == true }.getOrDefault(false) }
            best?.let { rows += SpecRow("Wi-Fi", it.second + if (runCatching { wm?.is6GHzBandSupported() == true }.getOrDefault(false)) " · 6 GHz" else "") }
        }
        val bluetooth = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
        if (bluetooth != null) {
            val le = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { bluetooth.isLeAudioSupported() == 10 }.getOrDefault(false) // BluetoothStatusCodes.FEATURE_SUPPORTED
            } else {
                false
            }
            rows += SpecRow("Bluetooth", if (le) "Yes · LE Audio" else "Yes")
        }
        rows += SpecRow("NFC", yesNo(has(PackageManager.FEATURE_NFC)))
        rows += SpecRow("Infrared remote", yesNo(has(PackageManager.FEATURE_CONSUMER_IR)))
        rows += SpecRow("Ultra-wideband", yesNo(has("android.hardware.uwb")))
        rows += SpecRow("eSIM", yesNo(has("android.hardware.telephony.euicc")))
        rows += SpecRow("Fingerprint", yesNo(has(PackageManager.FEATURE_FINGERPRINT)))
        rows += SpecRow("USB host (OTG)", yesNo(has(PackageManager.FEATURE_USB_HOST)))
        rows += SpecRow("Barometer", yesNo(has(PackageManager.FEATURE_SENSOR_BAROMETER)))
        return rows
    }

    private fun readGpu(): GpuInfo? = try {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(display, version, 0, version, 1)
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        val attribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0)
        val config = configs[0]
        if (count[0] == 0 || config == null) {
            EGL14.eglTerminate(display)
            null
        } else {
            val ctx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(display, surface, surface, ctx)
            val info = GpuInfo(
                renderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty().replace("(TM)", "").replace("  ", " ").trim(),
                vendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty(),
                glVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty(),
            )
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, ctx)
            EGL14.eglTerminate(display)
            info.takeIf { it.renderer.isNotBlank() }
        }
    } catch (ignored: Throwable) {
        null
    }
}

/** Chip model numbers to the names they're sold under. */
object SocNames {
    fun friendly(model: String): String? {
        val m = model.uppercase(Locale.US).trim()
        NAMES[m]?.let { return it }
        // "Qualcomm Technologies, Inc SM8750" and similar.
        return NAMES.entries.firstOrNull { m.contains(it.key) }?.value
    }

    private val NAMES = mapOf(
        "SM8850" to "Snapdragon 8 Elite Gen 5",
        "SM8750" to "Snapdragon 8 Elite",
        "SM8735" to "Snapdragon 8s Gen 4",
        "SM8650" to "Snapdragon 8 Gen 3",
        "SM8635" to "Snapdragon 8s Gen 3",
        "SM8550" to "Snapdragon 8 Gen 2",
        "SM8475" to "Snapdragon 8+ Gen 1",
        "SM8450" to "Snapdragon 8 Gen 1",
        "SM8350" to "Snapdragon 888",
        "SM8250" to "Snapdragon 865",
        "SM7750" to "Snapdragon 7+ Gen 4",
        "SM7675" to "Snapdragon 7+ Gen 3",
        "SM7550" to "Snapdragon 7 Gen 3",
        "SM7475" to "Snapdragon 7+ Gen 2",
        "SM6450" to "Snapdragon 6 Gen 1",
        "MT6993" to "Dimensity 9500",
        "MT6991" to "Dimensity 9400",
        "MT6989" to "Dimensity 9300",
        "MT6985" to "Dimensity 9200",
        "MT6983" to "Dimensity 9000",
        "MT6899" to "Dimensity 8400",
        "MT6897" to "Dimensity 8300",
        "MT6896" to "Dimensity 8200",
        "MT6878" to "Dimensity 7300",
        "MT6886" to "Dimensity 7200",
        "S5E9955" to "Exynos 2500",
        "S5E9945" to "Exynos 2400",
        "S5E9925" to "Exynos 2200",
        "TENSOR G5" to "Google Tensor G5",
        "TENSOR G4" to "Google Tensor G4",
        "TENSOR G3" to "Google Tensor G3",
        "ZUMAPRO" to "Google Tensor G4",
        "ZUMA" to "Google Tensor G3",
    )
}
