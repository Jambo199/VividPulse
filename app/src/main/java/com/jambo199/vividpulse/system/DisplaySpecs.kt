package com.jambo199.vividpulse.system

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DisplaySpec(
    /** The panel's full resolution (portrait). */
    val maxWidth: Int,
    val maxHeight: Int,
    /** The resolution it's running at now; vivo phones often run below full to save power. */
    val width: Int,
    val height: Int,
    val refreshRates: List<Float>,
    val currentHz: Float,
    val hdr: List<String>,
    val maxNits: Float?,
    val densityDpi: Int,
    val ppi: Int?,
    val inches: Float?,
    val wideColor: Boolean,
    val colorSpace: String?,
) {
    fun rows(): List<SpecRow> = listOfNotNull(
        SpecRow("Resolution", "$maxWidth × $maxHeight"),
        if (width != maxWidth || height != maxHeight) SpecRow("Running at", "$width × $height") else null,
        inches?.let { SpecRow("Size", String.format(Locale.US, "%.2f\"", it)) },
        ppi?.let { SpecRow("Sharpness", "$it ppi") },
        SpecRow("Refresh rates", refreshRates.joinToString(" / ") { "${it.roundToInt()}" } + " Hz"),
        SpecRow("HDR", hdr.ifEmpty { listOf("No") }.joinToString(", ")),
        maxNits?.takeIf { hdr.isNotEmpty() }?.let { SpecRow("HDR brightness", "${it.roundToInt()} nits") },
        SpecRow("Wide colour", if (wideColor) colorSpace ?: "Yes" else "No"),
        SpecRow("Density", "$densityDpi dpi"),
    )
}

object DisplayReader {
    @Suppress("DEPRECATION")
    fun read(context: Context): DisplaySpec? {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val modes = display.supportedModes.toList()
        val biggest = modes.maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight } ?: display.mode
        val now = display.mode
        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        val xInches = metrics.widthPixels / metrics.xdpi
        val yInches = metrics.heightPixels / metrics.ydpi
        val inches = sqrt(xInches * xInches + yInches * yInches).takeIf { it in 3f..20f }
        val fullShort = min(biggest.physicalWidth, biggest.physicalHeight)
        val ppi = if (xInches > 0 && inches != null) (fullShort / min(xInches, yInches)).roundToInt() else null
        val hdr = display.hdrCapabilities
        val hdrNames = hdr?.supportedHdrTypes?.toList().orEmpty().distinct().mapNotNull {
            when (it) {
                1 -> "Dolby Vision"
                2 -> "HDR10"
                3 -> "HLG"
                4 -> "HDR10+"
                else -> null
            }
        }
        val space = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) display.preferredWideGamutColorSpace?.name else null
        return DisplaySpec(
            maxWidth = fullShort,
            maxHeight = max(biggest.physicalWidth, biggest.physicalHeight),
            width = min(now.physicalWidth, now.physicalHeight),
            height = max(now.physicalWidth, now.physicalHeight),
            refreshRates = modes.map { it.refreshRate }.map { (it * 10).roundToInt() / 10f }.distinct().sorted(),
            currentHz = display.refreshRate,
            hdr = hdrNames,
            maxNits = hdr?.desiredMaxLuminance?.takeIf { it > 100f && it < 20_000f },
            densityDpi = metrics.densityDpi,
            ppi = ppi?.takeIf { it in 100..1000 },
            inches = inches,
            wideColor = display.isWideColorGamut,
            colorSpace = space,
        )
    }

    /** The refresh rate the screen is running at right now. */
    fun currentHz(context: Context): Float =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 60f
}
