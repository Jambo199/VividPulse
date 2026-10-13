package com.jambo199.vividpulse.system

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaRecorder
import android.os.Build
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A camera as apps see it. Phones often hide some lenses behind one "logical" camera. */
data class CameraSpec(
    val id: String,
    val facing: String,
    val megapixels: Float?,
    /** The lens as a 35 mm camera would describe it, e.g. 14, 35, 85 mm. */
    val equivalentMm: List<Int>,
    val apertures: List<Float>,
    val ois: Boolean,
    val flash: Boolean,
    val maxZoom: Float?,
    val tenBit: Boolean,
    val raw: Boolean,
    val bestVideo: String?,
    val lenses: Int,
    val sensorMm: String?,
)

object CameraReader {
    fun read(context: Context): List<CameraSpec> {
        val cm = context.getSystemService(CameraManager::class.java) ?: return emptyList()
        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        return ids.mapNotNull { id -> runCatching { spec(id, cm.getCameraCharacteristics(id)) }.getOrNull() }
    }

    private fun spec(id: String, c: CameraCharacteristics): CameraSpec {
        val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
            CameraMetadata.LENS_FACING_FRONT -> "Front"
            CameraMetadata.LENS_FACING_BACK -> "Back"
            else -> "External"
        }
        val pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val physical = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val diagonal = physical?.let { sqrt(it.width * it.width + it.height * it.height) }
        val focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val streams = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val video = streams?.getOutputSizes(MediaRecorder::class.java)?.maxByOrNull { it.width.toLong() * it.height }
        val lenses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) c.physicalCameraIds.size.coerceAtLeast(1) else 1
        return CameraSpec(
            id = id,
            facing = facing,
            megapixels = pixels?.let { it.width.toLong() * it.height / 1_000_000f },
            equivalentMm = if (diagonal != null && diagonal > 0) focals.map { CameraMath.equivalentMm(it, diagonal) }.distinct() else emptyList(),
            apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty().distinct(),
            ois = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.any { it == CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON } == true,
            flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            maxZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)?.takeIf { it > 1f },
            tenBit = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && caps.contains(18), // DYNAMIC_RANGE_TEN_BIT
            raw = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) ||
                streams?.outputFormats?.contains(ImageFormat.RAW_SENSOR) == true,
            bestVideo = video?.let { CameraMath.videoName(it.width, it.height) },
            lenses = lenses,
            sensorMm = physical?.let { String.format(Locale.US, "%.1f × %.1f mm", it.width, it.height) },
        )
    }
}

object CameraMath {
    /** 35 mm film's diagonal. */
    private const val FULL_FRAME_DIAGONAL = 43.27f

    fun equivalentMm(focalMm: Float, sensorDiagonalMm: Float): Int = (focalMm * FULL_FRAME_DIAGONAL / sensorDiagonalMm).roundToInt()

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
}
