package com.jambo199.vividpulse

import com.jambo199.vividpulse.alerts.ChargeMath
import com.jambo199.vividpulse.alerts.ChargePoint
import com.jambo199.vividpulse.alerts.ChargeSession
import com.jambo199.vividpulse.alerts.LevelSample
import com.jambo199.vividpulse.alerts.Overnight
import com.jambo199.vividpulse.games.AlertRule
import com.jambo199.vividpulse.games.GgDeals
import com.jambo199.vividpulse.games.PriceAlert
import com.jambo199.vividpulse.games.SaleAlerts
import com.jambo199.vividpulse.games.SeenPrice
import com.jambo199.vividpulse.games.SteamSales
import com.jambo199.vividpulse.games.StoreLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertsAndPricesTest {
    private fun point(min: Int, level: Float) = ChargePoint(min * 60_000L, level, 40f, 30f)

    @Test
    fun chargeLimitAlertsOnceWhenClimbingPastIt() {
        val s = ChargeSession(0, listOf(point(0, 60f), point(10, 79f)))
        assertFalse(ChargeMath.shouldAlert(s, 80, 79f))
        assertTrue(ChargeMath.shouldAlert(s, 80, 80f))
        assertFalse(ChargeMath.shouldAlert(s.copy(limitAlerted = true), 80, 81f))
        // Plugged in above the limit already: no alert.
        assertFalse(ChargeMath.shouldAlert(ChargeSession(0, listOf(point(0, 85f))), 80, 86f))
        // "No alert" is 100.
        assertFalse(ChargeMath.shouldAlert(s, 100, 100f))
    }

    @Test
    fun chargeContinuesOnlyWithRecentReadings() {
        assertTrue(ChargeMath.continues(point(0, 50f), 2 * 60_000L, 51f))
        assertFalse(ChargeMath.continues(point(0, 50f), 10 * 60_000L, 51f))
        // Unplugged and used in between: a new charge.
        assertFalse(ChargeMath.continues(point(0, 100f), 60_000L, 72f))
        assertFalse(ChargeMath.continues(null, 0, 50f))
    }

    @Test
    fun minutesToTheLimitFromRecentSpeed() {
        // 2% a minute from 60%: 80% in 10 minutes.
        val points = (0..5).map { point(it, 50f + it * 2) }
        assertEquals(10, ChargeMath.minutesTo(80, points))
        assertEquals(0, ChargeMath.minutesTo(55, points))
        assertNull(ChargeMath.minutesTo(80, listOf(point(0, 50f))))
    }

    @Test
    fun longChargesAreThinnedButKeepBothEnds() {
        val points = (0 until 400).map { point(it, it / 4f) }
        val thin = ChargeMath.thin(points, 300)
        assertTrue(thin.size < 300)
        assertEquals(points.first(), thin.first())
        assertEquals(points.last(), thin.last())
    }

    @Test
    fun overnightReport() {
        val zone = ZoneId.of("Europe/London")
        fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
        val night = listOf(
            LevelSample(at(12, 22), 80f, false),
            LevelSample(at(12, 23, 40), 78f, false),
            LevelSample(at(13, 2), 77f, false),
            LevelSample(at(13, 7, 30), 74f, false),
        )
        val r = Overnight.report(night, at(13, 7, 45), zone)
        assertNotNull(r)
        assertEquals(78, r!!.fromLevel)
        assertEquals(74, r.toLevel)
        assertEquals(4, r.used)
        assertFalse(r.charged)
        assertTrue(r.title.startsWith("Overnight: used 4%"))
        val charged = Overnight.report(night + LevelSample(at(13, 3), 90f, true), at(13, 7, 45), zone)
        assertTrue(charged!!.charged)
        // Nothing near bedtime: no report.
        assertNull(Overnight.report(night.drop(3), at(13, 7, 45), zone))
        assertTrue(Overnight.due(at(13, 8), zone, "2026-10-12"))
        assertFalse(Overnight.due(at(13, 8), zone, "2026-10-13"))
        assertFalse(Overnight.due(at(13, 5), zone, null))
    }

    @Test
    fun saleAlerts() {
        val sale = PriceAlert(AlertRule.SALE)
        val full = SeenPrice(0, 3999, null)
        // First check only notes the price.
        assertNull(SaleAlerts.decide(sale, null, SeenPrice(50, 1999, null)))
        assertEquals(SaleAlerts.Why.SALE, SaleAlerts.decide(sale, full, SeenPrice(40, 2399, null)))
        assertNull(SaleAlerts.decide(sale, SeenPrice(40, 2399, null), SeenPrice(45, 2199, null)))
        assertEquals(SaleAlerts.Why.DEEPER_SALE, SaleAlerts.decide(sale, SeenPrice(40, 2399, null), SeenPrice(60, 1599, null)))
        val target = PriceAlert(AlertRule.PRICE, targetCents = 2000, anywhere = true)
        // A key seller under the target counts when "anywhere" is on…
        assertEquals(SaleAlerts.Why.TARGET, SaleAlerts.decide(target, full, SeenPrice(0, 3999, 1850)))
        // …but not on Steam only.
        assertNull(SaleAlerts.decide(target.copy(anywhere = false), full, SeenPrice(0, 3999, 1850)))
        // Already under the target last time: not news.
        assertNull(SaleAlerts.decide(target, SeenPrice(0, 3999, 1900), SeenPrice(0, 3999, 1850)))
        assertNull(SaleAlerts.decide(PriceAlert(AlertRule.OFF), full, SeenPrice(90, 399, 100)))
    }

    @Test
    fun steamSaleCalendar() {
        val before = Instant.parse("2026-10-13T12:00:00Z")
        assertNull(SteamSales.current(before))
        assertEquals("Steam Winter Sale", SteamSales.next(before)?.name)
        val during = Instant.parse("2026-12-25T12:00:00Z")
        assertEquals("Steam Winter Sale", SteamSales.current(during)?.name)
        assertEquals("Steam Spring Sale", SteamSales.next(during)?.name)
        // Ends at 10 am Pacific on 4 January.
        assertNull(SteamSales.current(Instant.parse("2027-01-04T18:00:00Z")))
    }

    @Test
    fun ggDealsPrices() {
        val json = """{"success":true,"data":{
            "1086940":{"title":"Baldur's Gate 3","url":"https://gg.deals/game/baldurs-gate-3/","prices":{
                "currentRetail":"39.99","currentKeyshops":"25.49","historicalRetail":"29.99","historicalKeyshops":"19.79","currency":"GBP"}},
            "730":{"title":"Counter-Strike 2","url":"https://gg.deals/game/counter-strike-2/","prices":{"currentRetail":null,"currentKeyshops":null,"currency":"GBP"}},
            "5":null}}"""
        val p = GgDeals.parse(json)
        val bg3 = p.getValue(1086940)
        assertEquals(3999L, bg3.retailCents)
        assertEquals(2549L, bg3.keyshopCents)
        assertEquals(2549L, bg3.bestCents)
        assertTrue(bg3.bestIsKeyshop)
        assertEquals(1979L, bg3.lowestCents)
        assertEquals("GBP", bg3.currency)
        assertNull(p.getValue(730).bestCents)
        assertFalse(p.containsKey(5))
        assertEquals(2499L, GgDeals.cents(24.99))
        assertEquals(1000L, GgDeals.cents("10"))
        assertNull(GgDeals.cents("free"))
        assertTrue(GgDeals.pricesUrl(listOf(1, 2), " abc ", "gb").endsWith("ids=1,2&key=abc&region=gb"))
    }

    @Test(expected = java.io.IOException::class)
    fun ggDealsRefusalIsAnError() {
        GgDeals.parse("""{"success":false,"data":{"name":"Unauthorized","message":"Invalid API key","code":0,"status":401}}""")
    }

    @Test
    fun storeSearches() {
        val links = StoreLinks.forGame("Elden Ring")
        assertTrue(links.first { it.name == "AllKeyShop" }.url.contains("search-Elden+Ring"))
        assertTrue(links.any { !it.keySeller })
    }
}
