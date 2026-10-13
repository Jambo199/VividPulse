package com.jambo199.vividpulse.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

data class SensorEntry(
    val type: Int,
    val name: String,
    val kind: String,
    val vendor: String,
    val unit: String,
    val maxRange: Float,
    val powerMa: Float,
    val wakeUp: Boolean,
    /** How many of the reading's numbers mean something (x/y/z, or one value). */
    val axes: Int,
)

object SensorCatalog {
    fun list(context: Context): List<SensorEntry> {
        val sm = context.getSystemService(SensorManager::class.java) ?: return emptyList()
        return sm.getSensorList(Sensor.TYPE_ALL)
            .map { s ->
                SensorEntry(
                    type = s.type,
                    name = s.name,
                    kind = kind(s.type, s.stringType),
                    vendor = s.vendor,
                    unit = unit(s.type),
                    maxRange = s.maximumRange,
                    powerMa = s.power,
                    wakeUp = s.isWakeUpSensor,
                    axes = axes(s.type),
                )
            }
            .sortedWith(compareBy({ order(it.type) }, { it.kind }, { it.name }))
    }

    fun kind(type: Int, stringType: String? = null): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD -> "Compass (magnetometer)"
        Sensor.TYPE_LIGHT -> "Light"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_PRESSURE -> "Barometer"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game rotation"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic rotation"
        Sensor.TYPE_STEP_COUNTER -> "Step counter"
        Sensor.TYPE_STEP_DETECTOR -> "Step detector"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant motion"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperature"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
        Sensor.TYPE_HEART_RATE -> "Heart rate"
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (raw)"
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Compass (raw)"
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Accelerometer (raw)"
        Sensor.TYPE_HINGE_ANGLE -> "Hinge angle"
        else -> stringType?.substringAfterLast('.')?.replace('_', ' ')?.replaceFirstChar { it.titlecase() } ?: "Sensor $type"
    }

    fun unit(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "m/s²"
        Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
        Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
        Sensor.TYPE_LIGHT -> "lux"
        Sensor.TYPE_PROXIMITY -> "cm"
        Sensor.TYPE_PRESSURE -> "hPa"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
        Sensor.TYPE_STEP_COUNTER -> "steps"
        Sensor.TYPE_HEART_RATE -> "bpm"
        Sensor.TYPE_HINGE_ANGLE -> "°"
        else -> ""
    }

    fun axes(type: Int): Int = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
        -> 3
        else -> 1
    }

    /** Everyday sensors first. */
    private fun order(type: Int): Int = when (type) {
        Sensor.TYPE_ACCELEROMETER -> 0
        Sensor.TYPE_GYROSCOPE -> 1
        Sensor.TYPE_MAGNETIC_FIELD -> 2
        Sensor.TYPE_LIGHT -> 3
        Sensor.TYPE_PROXIMITY -> 4
        Sensor.TYPE_PRESSURE -> 5
        Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR -> 6
        Sensor.TYPE_STEP_COUNTER -> 7
        else -> if (type < Sensor.TYPE_DEVICE_PRIVATE_BASE) 8 else 9
    }
}
