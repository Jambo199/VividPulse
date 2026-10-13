package com.jambo199.vividpulse

import com.jambo199.vividpulse.tools.SpeedMath
import com.jambo199.vividpulse.ui.components.SpeedScale
import com.jambo199.vividpulse.util.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatAndSpeedTest {
    @Test
    fun bytes() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1.5 KB", Format.bytes(1500))
        assertEquals("9.8 GB", Format.bytes(9_800_000_000))
        assertEquals("12 GB", Format.bytes(12_000_000_000))
    }

    @Test
    fun speeds() {
        assertEquals("8 Mbps", Format.bitsPerSec(1_000_000))
        assertEquals("12.4 Mbps", Format.bitsPerSec(1_550_000))
        assertEquals("830 Kbps", Format.bitsPerSec(103_750))
        assertEquals("1.2 Gbps", Format.bitsPerSec(150_000_000))
    }

    @Test
    fun compactAndDuration() {
        assertEquals("1.73M", Format.compact(1_726_304))
        assertEquals("38.1M", Format.compact(38_123_456))
        assertEquals("45.2K", Format.compact(45_210))
        assertEquals("999", Format.compact(999))
        assertEquals("1,726,304", Format.grouped(1_726_304))
        assertEquals("2d 3h", Format.duration((2 * 86_400 + 3 * 3600) * 1000L))
        assertEquals("1h 5m", Format.duration(3_900_000))
    }

    @Test
    fun temperature() {
        assertEquals("36.5°C", Format.temperature(36.5f, fahrenheit = false))
        assertEquals("97.7°F", Format.temperature(36.5f, fahrenheit = true))
    }

    @Test
    fun speedTestMaths() {
        // 12.5 MB in one second is 100 Mbps.
        assertEquals(100f, SpeedMath.mbps(12_500_000, 1_000_000_000), 0.01f)
        assertEquals(20f, SpeedMath.median(listOf(30f, 10f, 20f)), 0.01f)
        assertEquals(15f, SpeedMath.median(listOf(10f, 20f)), 0.01f)
        assertEquals(5f, SpeedMath.jitter(listOf(10f, 15f, 10f)), 0.01f)
        // The ramp-up at the start is left out, and one lucky burst doesn't count.
        val samples = listOf(5f, 10f, 50f, 95f, 100f, 98f, 101f, 99f, 100f, 97f, 102f, 250f)
        val result = SpeedMath.result(samples)
        assertTrue(result in 99f..103f)
    }

    @Test
    fun speedometerScale() {
        assertEquals(0f, SpeedScale.fraction(0f), 0.0001f)
        assertTrue(SpeedScale.fraction(100f) > SpeedScale.fraction(50f))
        assertEquals(1f, SpeedScale.fraction(10_000f), 0.0001f)
    }
}
