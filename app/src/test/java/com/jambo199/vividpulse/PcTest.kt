package com.jambo199.vividpulse

import com.jambo199.vividpulse.alerts.PcAlertMath
import com.jambo199.vividpulse.pc.LibraryEntry
import com.jambo199.vividpulse.pc.LibraryJson
import com.jambo199.vividpulse.pc.PcAddress
import com.jambo199.vividpulse.pc.PcFinished
import com.jambo199.vividpulse.pc.PcLibrary
import com.jambo199.vividpulse.pc.PcLibraryMath
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
        // ARC Raiders is on its first download: not installed yet.
        assertEquals(6, s.installedGames.size)
        assertEquals(listOf(-1234567890 to "My Emulator"), s.shortcuts.map { it.appId to it.name })
        assertEquals("5f1c2a7b9e3d4c60", s.stamp)
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
            installed = listOf(LibraryEntry(-1234567890, "My Emulator", "", true, 0, 0, shortcut = true)),
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

    @Test
    fun readsDownloads() {
        val d = PcParse.downloads(sample("downloads.json"))
        assertEquals(listOf(1808500, 730), d.downloads.map { it.appId })
        val arc = d.downloads[0]
        assertEquals("downloading", arc.state)
        assertEquals(0.25f, arc.fraction!!, 0.001f)
        // 27 GB to go at 31.5 MB/s: about 14 minutes.
        assertEquals(857L, arc.secondsLeft)
        assertEquals("queued", d.downloads[1].state)
        assertNull(d.downloads[1].secondsLeft)
        assertTrue(d.finished.isEmpty())
        assertTrue(d.active)
        assertEquals("5f1c2a7b9e3d4c60", d.libraryStamp)
        // From the first PC program: no stamp, so the library isn't fetched again over it.
        assertNull(PcParse.downloads("""{"at": 1, "downloads": [], "finished": []}""").libraryStamp)
    }

    @Test
    fun installedListsWhatSteamDoes() {
        // Games, demos, mods, software (Wallpaper Engine and the like) and playtests count.
        listOf(0, 1, 2, 6, 12, null).forEach { assertTrue("kind $it", PcLibraryMath.countsAsInstalled(it)) }
        // DLC, soundtracks, videos and Steam's tools don't.
        listOf(3, 4, 7, 11, 13).forEach { assertTrue("kind $it", !PcLibraryMath.countsAsInstalled(it)) }
    }

    @Test
    fun alertsOnceAndOnlyWhenRecent() {
        val now = 1_760_000_000_000L
        val fresh = PcFinished(1808500, "ARC Raiders", now - 60_000)
        val old = PcFinished(730, "Counter-Strike 2", now - 5 * 3600_000L)
        val d = PcParse.downloads("""{"at": $now, "downloads": [], "finished": [{"appId": 1808500, "name": "ARC Raiders", "at": ${now - 60_000}}, {"appId": 730, "at": ${now - 5 * 3600_000L}}]}""")
        assertEquals(listOf(fresh.appId), PcAlertMath.newlyFinished(d, emptySet()).map { it.appId })
        assertTrue(PcAlertMath.newlyFinished(d, setOf(PcAlertMath.key(fresh))).isEmpty())
        assertEquals(old.appId, d.finished[1].appId)
        // Hot: at or over the limit, at most once per half hour.
        assertTrue(PcAlertMath.heatDue(86f, 85, 0, now))
        assertTrue(!PcAlertMath.heatDue(84f, 85, 0, now))
        assertTrue(!PcAlertMath.heatDue(90f, 85, now - 10 * 60_000, now))
        assertTrue(PcAlertMath.heatDue(90f, 85, now - 31 * 60_000, now))
        assertTrue(!PcAlertMath.heatDue(null, 85, 0, now))
    }
}
