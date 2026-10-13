package com.jambo199.vividpulse

import com.jambo199.vividpulse.games.Steam
import com.jambo199.vividpulse.system.PowerSession
import com.jambo199.vividpulse.tools.SpeedServer
import com.jambo199.vividpulse.update.UpdateNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExtrasTest {
    @Test
    fun updateNotesBecomeAList() {
        val body = """

            - Update banner on every page
            - Search any Steam game and
              see its live players
            - Charging stats
        """.trimIndent()
        assertEquals(
            listOf("Update banner on every page", "Search any Steam game and see its live players", "Charging stats"),
            UpdateNotes.changes(body),
        )
        // Plain paragraphs work too.
        assertEquals(listOf("One thing changed. It was small."), UpdateNotes.changes("One thing changed.\nIt was small."))
        assertEquals(emptyList<String>(), UpdateNotes.changes("\n\n"))
    }

    @Test
    fun steamSearch() {
        Locale.setDefault(Locale.UK)
        val json = """{"total":3,"items":[
            {"type":"app","name":"ELDEN RING","id":1245620,"tiny_image":"https://x/capsule_231x87.jpg","price":{"currency":"GBP","initial":4999,"final":2999}},
            {"type":"sub","name":"A package","id":5},
            {"type":"app","name":"Free Thing","id":42,"price":{"currency":"GBP","initial":0,"final":0}},
            {"type":"app","name":"No Price Yet","id":43}]}"""
        val games = Steam.parseSearch(json)
        assertEquals(listOf("ELDEN RING", "Free Thing", "No Price Yet"), games.map { it.name })
        val elden = games[0]
        assertEquals("£29.99", elden.price)
        assertEquals("£49.99", elden.originalPrice)
        assertEquals(40, elden.discountPercent)
        assertEquals("https://x/capsule_231x87.jpg", elden.imageUrl)
        assertEquals("Free", games[1].price)
        assertNull(games[2].price)
        assertTrue(games[2].imageUrl.endsWith("/steam/apps/43/header.jpg"))
    }

    @Test
    fun speedServer() {
        val meta = SpeedServer.parseMeta("""{"hostname":"speed.cloudflare.com","colo":"LHR","asOrganization":"British Telecommunications PLC","country":"GB"}""")!!
        assertEquals("London (LHR)", meta.place)
        assertEquals("British Telecommunications PLC", meta.provider)
        val trace = SpeedServer.parseTrace("fl=123\nh=speed.cloudflare.com\nip=1.2.3.4\ncolo=XYZ\nloc=GB\n")!!
        assertEquals("XYZ", trace.place)
        assertNull(trace.provider)
        assertNull(SpeedServer.parseTrace("nothing useful"))
    }

    @Test
    fun chargingSession() {
        val start = PowerSession.next(null, plugged = true, level = 40f, watts = 50f, nowElapsed = 0, sinceLastMs = 0, firstReading = false)
        assertTrue(start.charging)
        assertTrue(start.seenStart)
        // An hour at 60 W, ending at 90%.
        val later = PowerSession.next(start, plugged = true, level = 90f, watts = 60f, nowElapsed = 3_600_000, sinceLastMs = 3_600_000, firstReading = false)
        assertEquals(60f, later.peakWatts, 0.01f)
        assertEquals(60f, later.energyWh, 0.01f)
        assertEquals(50f, later.levelChange, 0.01f)
        assertEquals(50f, later.percentPerHour(3_600_000)!!, 0.01f)
        // Unplugging starts a new session.
        val unplugged = PowerSession.next(later, plugged = false, level = 90f, watts = -3f, nowElapsed = 3_700_000, sinceLastMs = 1000, firstReading = false)
        assertFalse(unplugged.charging)
        assertEquals(90f, unplugged.startLevel, 0.01f)
        // Already charging when the app opened.
        assertFalse(PowerSession.next(null, true, 50f, 10f, 0, 0, firstReading = true).seenStart)
        assertNull(start.percentPerHour(60_000))
    }
}
