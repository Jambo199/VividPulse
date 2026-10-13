package com.jambo199.vividpulse

import com.jambo199.vividpulse.system.BatteryMath
import com.jambo199.vividpulse.system.CameraMath
import com.jambo199.vividpulse.system.CpuParsing
import com.jambo199.vividpulse.system.MemInfo
import com.jambo199.vividpulse.system.NetworkReader
import com.jambo199.vividpulse.system.SocNames
import com.jambo199.vividpulse.system.SysFs
import com.jambo199.vividpulse.system.ThermalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemParsingTest {
    private val snapdragon8Elite = """
        processor	: 0
        BogoMIPS	: 38.40
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics
        CPU implementer	: 0x51
        CPU architecture: 8
        CPU variant	: 0x3
        CPU part	: 0x001
        CPU revision	: 2

        processor	: 6
        CPU implementer	: 0x51
        CPU part	: 0x001

        processor	: 7
        CPU implementer	: 0x41
        CPU part	: 0xd85
    """.trimIndent()

    @Test
    fun cpuPartsAreNamedPerCore() {
        val parts = CpuParsing.partsByCore(snapdragon8Elite)
        assertEquals("Oryon", parts[0])
        assertEquals("Oryon", parts[6])
        assertEquals("Cortex-X925", parts[7])
    }

    @Test
    fun clustersGroupByTopSpeedFastestFirst() {
        // Snapdragon 8 Elite: 6 performance cores at 3.53 GHz, 2 prime cores at 4.32 GHz.
        val max = listOf(3532800L, 3532800, 3532800, 3532800, 3532800, 3532800, 4320000, 4320000)
        val groups = CpuParsing.clusterOf(max)
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 0, 0), groups)
        assertEquals(listOf("Prime", "Performance"), CpuParsing.clusterNames(listOf(2, 6)))
        assertEquals(listOf("Prime", "Performance", "Efficiency"), CpuParsing.clusterNames(listOf(1, 3, 4)))
        assertEquals(listOf("Performance", "Efficiency"), CpuParsing.clusterNames(listOf(4, 4)))
    }

    @Test
    fun loadFollowsTheClockWithinItsRange() {
        assertEquals(0f, CpuParsing.loadOf(384_000, 384_000, 4_320_000), 0.001f)
        assertEquals(1f, CpuParsing.loadOf(4_320_000, 384_000, 4_320_000), 0.001f)
        assertEquals(0.5f, CpuParsing.loadOf(2_352_000, 384_000, 4_320_000), 0.001f)
        // Faster cores weigh more; switched-off cores count as idle.
        val weighted = CpuParsing.weightedLoad(listOf(4000L, 2000L), listOf(1f, null))!!
        assertEquals(4000f / 6000f, weighted, 0.001f)
        assertNull(CpuParsing.weightedLoad(listOf(1L), listOf(null)))
    }

    @Test
    fun procStatUsage() {
        val before = CpuParsing.parseStat("cpu  100 0 100 800 0 0 0 0\ncpu0 50 0 50 400 0 0 0 0")
        val after = CpuParsing.parseStat("cpu  200 0 200 1000 0 0 0 0\ncpu0 100 0 100 450 0 0 0 0")
        assertEquals(0.5f, CpuParsing.usageBetween(before.getValue("cpu"), after.getValue("cpu")), 0.001f)
        assertEquals(100f / 150f, CpuParsing.usageBetween(before.getValue("cpu0"), after.getValue("cpu0")), 0.001f)
    }

    @Test
    fun memInfoAndMarketedSizes() {
        val m = MemInfo.parse("MemTotal:       15523412 kB\nMemAvailable:    6123456 kB\nSwapTotal:       8388604 kB\n")
        assertEquals(15523412L, m["MemTotal"])
        assertEquals(8388604L, m["SwapTotal"])
        assertEquals(16, MemInfo.marketedRamGb(15_523_412L * 1024))
        assertEquals(12, MemInfo.marketedRamGb(11_400_000_000L))
        assertEquals(512, MemInfo.marketedStorageGb(500_000_000_000L))
        assertEquals(1024, MemInfo.marketedStorageGb(1_000_000_000_000L))
        assertEquals(256, MemInfo.marketedStorageGb(256_000_000_000L))
    }

    @Test
    fun batteryCurrentUnitsAndSign() {
        // Standard: µA, positive while charging.
        assertEquals(2500f, BatteryMath.currentMa(2_500_000, charging = true, discharging = false), 0.01f)
        // Some phones report mA, and some flip the sign.
        assertEquals(2500f, BatteryMath.currentMa(-2500, charging = true, discharging = false), 0.01f)
        assertEquals(-450f, BatteryMath.currentMa(450_000, charging = false, discharging = true), 0.01f)
    }

    @Test
    fun socNames() {
        assertEquals("Snapdragon 8 Elite", SocNames.friendly("SM8750"))
        assertEquals("Dimensity 9400", SocNames.friendly("mt6991"))
        assertEquals("Snapdragon 8 Elite", SocNames.friendly("Qualcomm Technologies, Inc SM8750"))
        assertNull(SocNames.friendly("XYZ123"))
    }

    @Test
    fun cpuRanges() {
        assertEquals((0..7).toList(), SysFs.parseRange("0-7"))
        assertEquals(listOf(0, 1, 2, 3, 6, 7), SysFs.parseRange("0-3,6-7"))
        assertEquals(listOf(0), SysFs.parseRange("0"))
    }

    @Test
    fun thermalZones() {
        assertEquals(42.5f, ThermalMath.celsius(42_500), 0.01f)
        assertEquals(38f, ThermalMath.celsius(38), 0.01f)
        assertEquals("Battery", ThermalMath.friendlyZone("battery"))
        assertEquals("CPU 3", ThermalMath.friendlyZone("cpu-1-3"))
        assertNull(ThermalMath.friendlyZone("pm8550_tz"))
    }

    @Test
    fun wifiBandsAndChannels() {
        assertEquals("5 GHz", NetworkReader.bandOf(5180))
        assertEquals(36, NetworkReader.channelOf(5180))
        assertEquals("2.4 GHz", NetworkReader.bandOf(2437))
        assertEquals(6, NetworkReader.channelOf(2437))
        assertEquals("6 GHz", NetworkReader.bandOf(5975))
        assertEquals("Wi-Fi 7 (802.11be)", NetworkReader.wifiStandard(8))
    }

    @Test
    fun cameraLenses() {
        // A 1/1.28" main sensor (about 12.3 mm across) with a 6.9 mm lens is a 24 mm-equivalent.
        assertEquals(24, CameraMath.equivalentMm(6.9f, 12.4f))
        assertEquals("Ultra-wide", CameraMath.lensKind(14, front = false))
        assertEquals("Main", CameraMath.lensKind(35, front = false))
        assertEquals("Telephoto", CameraMath.lensKind(85, front = false))
        assertEquals("8K", CameraMath.videoName(7680, 4320))
        assertTrue(CameraMath.videoName(3840, 2160) == "4K")
    }
}
