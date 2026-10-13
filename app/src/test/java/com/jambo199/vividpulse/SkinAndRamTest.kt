package com.jambo199.vividpulse

import com.jambo199.vividpulse.system.SkinNames
import com.jambo199.vividpulse.tools.BoostResult
import com.jambo199.vividpulse.tools.MemSnapshot
import com.jambo199.vividpulse.tools.RamBooster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinAndRamTest {
    @Test
    fun vivoOnAndroid16IsOriginOs6EvenWhenThePropertiesSayFuntouch() {
        // What an X200 Ultra on Android 16 reports: the old Funtouch property names, Android's version.
        assertEquals("OriginOS 6", SkinNames.vivo("", "Funtouch", "16.0", "PD2507F_EX_A_16.0.12.4.W30", "yes", 16))
        assertEquals("OriginOS 6", SkinNames.vivo("", "Funtouch_OS", "16", "", "", 16))
    }

    @Test
    fun anOriginOsNameInThePropertiesWins() {
        assertEquals("OriginOS 5", SkinNames.vivo("OriginOS 5", "Funtouch", "15.0", "", "yes", 15))
        assertEquals("OriginOS 5.1", SkinNames.vivo("", "", "", "OriginOS_5.1_PD2453", "", 15))
        assertEquals("OriginOS Ocean", SkinNames.vivo("originos ocean", "", "", "", "", 12))
    }

    @Test
    fun chinaBuildsAreOriginOsAndExportBuildsBeforeAndroid16AreFuntouch() {
        assertEquals("OriginOS 5", SkinNames.vivo("", "Funtouch", "15.0", "PD2453_A_15.0.20.1.W10", "", 15))
        assertEquals("OriginOS 4", SkinNames.vivo("", "Funtouch", "14.0", "", "no", 14))
        assertEquals("Funtouch OS 15", SkinNames.vivo("", "Funtouch", "15.0", "PD2453F_EX_A_15.0.20.1.W30", "yes", 15))
        assertEquals("Funtouch OS 14", SkinNames.vivo("", "", "", "", "yes", 14))
    }

    @Test
    fun ramSafetyFloor() {
        val gb = 1_000_000_000L
        // 16 GB phone: keep at least 2.4 GB free…
        assertEquals((16 * gb * 0.15).toLong(), RamBooster.floorFor(16 * gb, 300_000_000))
        // …and always well above Android's own low-memory line.
        assertEquals(1_500_000_000L, RamBooster.floorFor(4 * gb, 1_000_000_000))
    }

    @Test
    fun ramResultSummary() {
        val gb = 1_000_000_000L
        fun snap(avail: Long, cached: Long, swap: Long) = MemSnapshot(16 * gb, avail, cached, 8 * gb, swap, false, 300_000_000)
        val r = BoostResult(snap(5 * gb, 3 * gb, 1 * gb), snap(8 * gb, 2 * gb, 2 * gb), 200_000, 150_000, 4 * gb)
        assertEquals(3 * gb, r.gained)
        assertEquals(1 * gb, r.compressed)
        assertEquals(1 * gb, r.cacheCleared)
        assertTrue(RamBooster.describe(r).contains("+3 GB"))
        val none = BoostResult(snap(5 * gb, 3 * gb, 1 * gb), snap(5 * gb, 3 * gb, 1 * gb), 1, 1, 0)
        assertTrue(RamBooster.describe(none).contains("good shape"))
    }
}
