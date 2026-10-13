package com.jambo199.vividpulse.system

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.abs

data class BatterySample(
    val present: Boolean,
    /** 0..100 */
    val level: Float,
    val charging: Boolean,
    val full: Boolean,
    val statusLabel: String,
    /** "Charger", "USB", "Wireless", "Dock"; null on battery. */
    val plug: String?,
    val temperatureC: Float?,
    val voltageMv: Int?,
    /** Into the battery when positive (charging), out of it when negative. */
    val currentMa: Float?,
    val healthLabel: String,
    val healthGood: Boolean,
    val technology: String?,
    val cycles: Int?,
    val chargeCounterMah: Int?,
    val timeToFullMs: Long?,
) {
    /** Watts in (+) or out (−) of the battery. */
    val watts: Float?
        get() {
            val v = voltageMv ?: return null
            val i = currentMa ?: return null
            return v / 1000f * i / 1000f
        }

    /** What a full charge holds now, estimated from the charge counter and the level. */
    val estimatedFullMah: Int?
        get() {
            val counter = chargeCounterMah ?: return null
            if (level < 15f) return null
            return (counter / (level / 100f)).toInt()
        }

    companion object {
        val EMPTY = BatterySample(
            false, 0f, false, false, "", null, null, null, null, "", true, null, null, null, null,
        )
    }
}

class BatteryReader(private val context: Context) {
    private val bm = context.getSystemService(BatteryManager::class.java)

    /** What the battery was designed to hold, in mAh, when the phone says. */
    val designCapacityMah: Int? by lazy { readDesignCapacity() }

    fun sample(): BatterySample {
        val i: Intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        ) ?: return BatterySample.EMPTY
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val discharging = status == BatteryManager.BATTERY_STATUS_DISCHARGING ||
            status == BatteryManager.BATTERY_STATUS_NOT_CHARGING && plugged == 0
        val health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val voltage = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val cycles = i.getIntExtra(EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
            ?: SysFs.readLong("/sys/class/power_supply/battery/cycle_count")?.toInt()
        val current = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Long.MIN_VALUE && it != Int.MIN_VALUE.toLong() }
            ?.let { BatteryMath.currentMa(it, charging, discharging) }
        val counter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it > 0 && it != Int.MIN_VALUE }
            ?.let { if (it < 100_000) it else it / 1000 }
        val toFull = if (charging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bm?.computeChargeTimeRemaining()?.takeIf { it > 0 }
        } else {
            null
        }
        return BatterySample(
            present = i.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true),
            level = if (level >= 0) level * 100f / scale else 0f,
            charging = charging,
            full = status == BatteryManager.BATTERY_STATUS_FULL,
            statusLabel = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> if (plugged != 0) "Plugged in, not charging" else "On battery"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "On battery"
                else -> "Unknown"
            },
            plug = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "Charger"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                8 -> "Dock" // BATTERY_PLUGGED_DOCK, Android 13+
                else -> null
            },
            temperatureC = if (temp != Int.MIN_VALUE) temp / 10f else null,
            voltageMv = voltage.takeIf { it > 0 }?.let { if (it < 100) it * 1000 else it },
            currentMa = current,
            healthLabel = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Unknown"
            },
            healthGood = health == BatteryManager.BATTERY_HEALTH_GOOD || health == BatteryManager.BATTERY_HEALTH_UNKNOWN,
            technology = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { null },
            cycles = cycles,
            chargeCounterMah = counter,
            timeToFullMs = toFull,
        )
    }

    private fun readDesignCapacity(): Int? {
        SysFs.readLong("/sys/class/power_supply/battery/charge_full_design")?.let { uah ->
            if (uah > 0) return (if (uah > 100_000) uah / 1000 else uah).toInt()
        }
        return try {
            val c = Class.forName("com.android.internal.os.PowerProfile")
            val profile = c.getConstructor(Context::class.java).newInstance(context)
            val mah = (c.getMethod("getBatteryCapacity").invoke(profile) as Double).toInt()
            mah.takeIf { it > 1000 }
        } catch (ignored: Throwable) {
            null
        }
    }

    private companion object {
        /** BatteryManager.EXTRA_CYCLE_COUNT, Android 14+. */
        const val EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT"
    }
}

object BatteryMath {
    /**
     * The battery current in mA, positive while charging. Phones disagree on both the unit
     * (µA is the standard, some report mA) and the sign, so both are worked out from the reading.
     */
    fun currentMa(raw: Long, charging: Boolean, discharging: Boolean): Float {
        // A µA reading under 20 mA is implausible with the screen on; such phones report mA.
        var ma = if (abs(raw) < 20_000) raw.toFloat() else raw / 1000f
        if (charging && ma < 0 || discharging && ma > 0) ma = -ma
        return ma
    }
}
