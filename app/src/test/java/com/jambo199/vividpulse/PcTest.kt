package com.jambo199.vividpulse

import com.jambo199.vividpulse.pc.LibraryEntry
import com.jambo199.vividpulse.pc.LibraryJson
import com.jambo199.vividpulse.pc.PcAddress
import com.jambo199.vividpulse.pc.PcLibrary
import com.jambo199.vividpulse.pc.PcParse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What Vivid Pulse PC sends, as the app reads it. The samples are the ones the emulator test serves. */
class PcTest {
    private fun sample(name: String) = File("../ci/pc-sample/$name").readText()

    @Test
    fun readsSpecs() {
        val s = PcParse.specs(sample("specs.json"))
        assertEquals("JAMBO-PC", s.name)
        assertEquals("AMD Ryzen 7 7800X3D 8-Core Processor", s.cpu.name)
        assertEquals(8, s.cpu.cores)
        assertEquals(16, s.cpu.threads)
        assertEquals(2, s.gpus.size)
        assertEquals(12884901888L, s.gpus[0].vramBytes)
        assertEquals(165, s.gpus[0].refreshHz)
        assertNull(s.gpus[1].vramBytes)
        assertEquals(2, s.sticks.size)
        assertEquals("DDR5", s.sticks[0].type)
        assertEquals(listOf("C:", "D:"), s.volumes.map { it.letter })
        assertEquals("SSD", s.drives[0].kind)
        // One monitor arrives as an object, not a list (Windows PowerShell does that).
        assertEquals(listOf("LG ULTRAGEAR"), s.monitors.map { it.name })
        assertEquals("24H2", s.os.version)
    }

    @Test
    fun readsLiveReadings() {
        val l = PcParse.live(sample("live.json"))
        assertEquals(23f, l.cpuPercent)
        assertEquals(16, l.cpuCores.size)
        assertEquals(61f, l.gpu?.tempC)
        assertEquals(12282f, l.gpu?.vramTotalMb)
        assertEquals(1808500, l.playingAppId)
        assertEquals(15230L, l.uptimeSec)
    }

    @Test
    fun readsSteamLibraryAndCollections() {
        val s = PcParse.steam(sample("steam.json"))
        assertTrue(s.installed)
        assertEquals("Jambo", s.userName)
        assertEquals(7, s.installedGames.size)
        assertEquals(6120L, s.games.first { it.appId == 1091500 }.minutes)
        assertEquals(3, s.collections.size)
        // The hand-made "PURCHASED" collection, not the dynamic "Installed" one.
        assertEquals("PURCHASED", s.purchasedCollection?.name)
        assertEquals(13, s.purchasedCollection?.appIds?.size)
        assertTrue(s.libraryAppIds.containsAll(listOf(570, 730, 1091500)))
    }

    @Test
    fun copesWithWindowsPowerShellLists() {
        val json = """
            {"installed": true, "user": {"name": "A"},
             "games": {"value": [{"appId": 10, "name": "X", "installed": true, "sizeBytes": 5.0E9}], "Count": 1},
             "collections": {"id": "uc-1", "name": "My purchases", "appIds": 10, "dynamic": false},
             "libraryAppIds": {"value": [10, 20], "Count": 2}}
        """
        val s = PcParse.steam(json)
        assertEquals(listOf(10), s.games.map { it.appId })
        assertEquals(5_000_000_000L, s.games[0].sizeBytes)
        assertEquals(listOf(10), s.purchasedCollection?.appIds)
        assertEquals(listOf(10, 20), s.libraryAppIds)
    }

    @Test
    fun purchasedNeedsAHandMadeCollection() {
        val dynamic = PcParse.steam("""{"installed": true, "collections": [{"name": "Purchased", "appIds": [], "dynamic": true}]}""")
        assertNull(dynamic.purchasedCollection)
        val none = PcParse.steam("""{"installed": false}""")
        assertNull(none.purchasedCollection)
        assertTrue(none.games.isEmpty())
    }

    @Test
    fun knowsVividPulsePcFromOtherAnswers() {
        assertEquals("JAMBO-PC", PcParse.found(sample("hello.json"), "10.0.0.5")?.name)
        assertEquals(47800, PcParse.found(sample("hello.json"), "10.0.0.5")?.port)
        assertNull(PcParse.found("""{"app": "Something"}""", "10.0.0.5"))
        assertNull(PcParse.found("not json", "10.0.0.5"))
    }

    @Test
    fun keepsTheLibraryForWhenThePcIsOff() {
        val l = PcLibrary(
            purchased = listOf(LibraryEntry(1091500, "Cyberpunk 2077", "https://x/c.jpg", true, 74_000_000_000, 6120)),
            installed = emptyList(),
            fromCollection = null,
        )
        assertEquals(l, LibraryJson.read(LibraryJson.write(l)))
        val named = l.copy(fromCollection = "Purchased")
        assertEquals(named, LibraryJson.read(LibraryJson.write(named)))
    }

    @Test
    fun readsTypedAddresses() {
        assertEquals("192.168.1.20" to 47800, PcAddress.parse(" 192.168.1.20 "))
        assertEquals("192.168.1.20" to 5000, PcAddress.parse("192.168.1.20:5000"))
        assertEquals("192.168.1.20" to 47800, PcAddress.parse("http://192.168.1.20:47800/"))
        assertEquals("gaming-pc.local" to 47800, PcAddress.parse("gaming-pc.local"))
        assertNull(PcAddress.parse(""))
        assertNull(PcAddress.parse("192.168.1.20:port"))
    }
}
