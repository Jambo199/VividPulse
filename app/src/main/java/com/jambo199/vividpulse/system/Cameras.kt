package com.jambo199.vividpulse.system

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaRecorder
import android.os.Build
import android.util.Size
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One physical camera: a sensor behind a lens. */
data class LensSpec(
    val id: String,
    val facing: String,
    val megapixels: Float?,
    /** As a 35 mm camera would describe it: 14, 35, 85 mm. */
    val equivalentMm: Int?,
    val aperture: Float?,
    /** Sensor size in the usual "1/1.28-inch" style. */
    val sensorType: String?,
    val ois: Boolean,
) {
    val kind: String get() = equivalentMm?.let { CameraMath.lensKind(it, facing == "Front") } ?: facing
}

/** A camera as apps open it: often several lenses joined into one ("logical") camera. */
data class CameraSpec(
    val id: String,
    val facing: String,
    /** The physical lenses behind it (just one for a simple camera). */
    val lenses: List<LensSpec>,
    val zoom: ClosedFloatingPointRange<Float>?,
    val megapixels: Float?,
    val video: String?,
    val slowMotionFps: Int?,
    val hdrVideo: List<String>,
    val raw: Boolean,
    val modes: List<String>,
    val flash: Boolean,
    val level: String,
    /** Left out of the camera list Android gives apps, but there when asked for by number. */
    val hidden: Boolean,
)

data class CameraReport(
    val cameras: List<CameraSpec>,
    /** Every distinct lens found, back ones first, widest first. */
    val lenses: List<LensSpec>,
)

object CameraReader {
    fun read(context: Context): CameraReport {
        val cm = context.getSystemService(CameraManager::class.java) ?: return CameraReport(emptyList(), emptyList())
        val listed = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        // Phones often keep extra lenses out of the list apps get; asking for them by number can
        // still work, so try the usual numbers too.
        val shown = listed.mapNotNull { id -> runCatching { camera(cm, id, cm.getCameraCharacteristics(id), hidden = false) }.getOrNull() }
        // Lenses already shown as part of a listed camera aren't counted twice.
        val known = listed.toSet() + shown.flatMap { c -> c.lenses.map { it.id } }
        val probed = (0..15).map { it.toString() }.filter { it !in known }.mapNotNull { id ->
            runCatching { camera(cm, id, cm.getCameraCharacteristics(id), hidden = true) }.getOrNull()
        }
        val cameras = shown + probed
        val lenses = cameras.flatMap { it.lenses }
            .distinctBy { Triple(it.facing, it.equivalentMm, it.megapixels?.roundToInt()) }
            .sortedWith(compareBy({ it.facing != "Back" }, { it.equivalentMm ?: 999 }))
        return CameraReport(cameras, lenses)
    }

    private fun camera(cm: CameraManager, id: String, c: CameraCharacteristics, hidden: Boolean): CameraSpec {
        val facing = facingOf(c)
        val physical = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) c.physicalCameraIds.toList() else emptyList()
        val lenses = physical.mapNotNull { pid -> runCatching { lens(pid, cm.getCameraCharacteristics(pid)) }.getOrNull() }
            .ifEmpty { listOf(lens(id, c)) }
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val streams = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val video = streams?.getOutputSizes(MediaRecorder::class.java)?.maxByOrNull { it.width.toLong() * it.height }
        val videoFps = video?.let { size ->
            runCatching { streams?.getOutputMinFrameDuration(MediaRecorder::class.java, size) }.getOrNull()
                ?.takeIf { it > 0 }?.let { (1e9 / it).roundToInt() }
        }
        val slowMo = if (caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO)) {
            runCatching { streams?.highSpeedVideoFpsRanges?.maxOfOrNull { it.upper } }.getOrNull()
        } else {
            null
        }
        val zoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { it.lower..it.upper }?.takeIf { it.endInclusive > 1.01f }
        } else {
            null
        }
        return CameraSpec(
            id = id,
            facing = facing,
            lenses = lenses,
            zoom = zoom ?: c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.takeIf { it > 1f }?.let { 1f..it },
            megapixels = megapixels(c),
            video = video?.let { v -> CameraMath.videoName(v.width, v.height) + (videoFps?.takeIf { it > 0 }?.let { " $it fps" } ?: "") },
            slowMotionFps = slowMo,
            hdrVideo = hdrVideo(c),
            raw = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW),
            modes = modes(cm, id),
            flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3 (full manual and RAW)"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
                else -> "Legacy"
            },
            hidden = hidden,
        )
    }

    private fun lens(id: String, c: CameraCharacteristics): LensSpec {
        val physical = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val diagonal = physical?.let { sqrt(it.width * it.width + it.height * it.height) }?.takeIf { it > 0 }
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        return LensSpec(
            id = id,
            facing = facingOf(c),
            megapixels = megapixels(c),
            equivalentMm = if (focal != null && diagonal != null) CameraMath.equivalentMm(focal, diagonal) else null,
            aperture = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull(),
            sensorType = diagonal?.let { CameraMath.opticalFormat(it) },
            ois = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.any { it == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON } == true,
        )
    }

    /** The biggest picture the sensor can give: many phones report a binned size unless asked for full resolution. */
    private fun megapixels(c: CameraCharacteristics): Float? {
        val sizes = mutableListOf<Size>()
        c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let { sizes += it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION)?.let { sizes += it }
            c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                ?.getOutputSizes(ImageFormat.JPEG)?.let { sizes += it }
        }
        c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG)?.let { sizes += it }
        return sizes.maxOfOrNull { it.width.toLong() * it.height }?.let { it / 1_000_000f }
    }

    private fun hdrVideo(c: CameraCharacteristics): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return emptyList()
        val profiles = runCatching { c.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)?.supportedProfiles }.getOrNull() ?: return emptyList()
        return CameraMath.hdrNames(profiles)
    }

    /** Night, portrait and other modes the phone offers to apps (Android 12+ camera extensions). */
    private fun modes(cm: CameraManager, id: String): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return emptyList()
        val supported = runCatching { cm.getCameraExtensionCharacteristics(id).supportedExtensions }.getOrNull() ?: return emptyList()
        return supported.mapNotNull {
            when (it) {
                4 -> "Night"
                1 -> "Portrait"
                3 -> "HDR"
                2 -> "Beauty"
                0 -> "Auto"
                else -> null
            }
        }
    }

    private fun facingOf(c: CameraCharacteristics) = when (c.get(CameraCharacteristics.LENS_FACING)) {
        CameraMetadata.LENS_FACING_FRONT -> "Front"
        CameraMetadata.LENS_FACING_BACK -> "Back"
        else -> "External"
    }
}

object CameraMath {
    /** 35 mm film's diagonal. */
    private const val FULL_FRAME_DIAGONAL = 43.27f

    fun equivalentMm(focalMm: Float, sensorDiagonalMm: Float): Int = (focalMm * FULL_FRAME_DIAGONAL / sensorDiagonalMm).roundToInt()

    /** Sensor size the way phone makers quote it: a "1-inch type" sensor is about 16 mm across. */
    fun opticalFormat(diagonalMm: Float): String {
        val inches = diagonalMm / 16f
        return if (inches >= 0.95f) {
            String.format(Locale.US, "%.1f\"", inches).replace(".0\"", "\"")
        } else {
            String.format(Locale.US, "1/%.2f\"", 1f / inches).replace(Regex("0+\""), "\"").replace(".\"", "\"")
        }
    }

    fun videoName(w: Int, h: Int): String {
        val long = maxOf(w, h)
        return when {
            long >= 7680 -> "8K"
            long >= 3840 -> "4K"
            long >= 2560 -> "1440p"
            long >= 1920 -> "1080p"
            long >= 1280 -> "720p"
            else -> "${w}×$h"
        }
    }

    /** "Ultra-wide", "Main", "Telephoto" from the 35 mm equivalent. */
    fun lensKind(equivalentMm: Int, front: Boolean): String = when {
        front -> "Selfie"
        equivalentMm <= 18 -> "Ultra-wide"
        equivalentMm <= 40 -> "Main"
        equivalentMm <= 60 -> "Portrait"
        else -> "Telephoto"
    }

    /** Camera2 dynamic range profile flags to the HDR formats they mean. */
    fun hdrNames(profiles: Set<Long>): List<String> = buildList {
        if (profiles.contains(2L)) add("HLG") // HLG10
        if (profiles.contains(4L)) add("HDR10")
        if (profiles.contains(8L)) add("HDR10+")
        if (profiles.any { it in 16L..2048L }) add("Dolby Vision")
    }
}

/** A camera lens as the maker lists it. */
data class KnownLens(val kind: String, val megapixels: Int, val mm: Int?, val sensor: String?, val aperture: String?, val notes: String?)

data class KnownCameras(val source: String, val lenses: List<KnownLens>, val video: String)

/**
 * What some phones' cameras are as the maker sells them. Apps only see what Android shares with
 * them, which on many phones is fewer lenses (and binned resolutions) than the camera app uses.
 */
object KnownPhones {
    fun cameras(phoneName: String): KnownCameras? {
        val n = phoneName.lowercase(Locale.US)
        return when {
            "x200 ultra" in n -> KnownCameras(
                source = "vivo's published specs",
                lenses = listOf(
                    KnownLens("Main", 50, 35, "Sony LYT-818 · 1/1.28\"", "f/1.69", "Stabilised"),
                    KnownLens("Ultra-wide", 50, 14, "Sony LYT-818 · 1/1.28\"", "f/2.0", null),
                    KnownLens("Telephoto (periscope)", 200, 85, "Samsung ISOCELL HP9 · 1/1.4\"", "f/2.27", "3.7× optical · macro from 15 cm"),
                    KnownLens("Selfie", 50, null, null, "f/2.0", null),
                ),
                video = "4K up to 120 fps on all three back cameras · 4K 60 fps Log · Dolby Vision",
            )
            else -> null
        }
    }
}
