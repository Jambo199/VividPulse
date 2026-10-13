package com.jambo199.vividpulse

import com.jambo199.vividpulse.games.Html
import com.jambo199.vividpulse.games.Steam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

class GamesParsingTest {
    @Test
    fun mostPlayed() {
        val json = """{"response":{"rollup_date":1760227200,"ranks":[
            {"rank":1,"appid":730,"last_week_rank":1,"peak_in_game":1726304},
            {"rank":2,"appid":570,"last_week_rank":3,"peak_in_game":734117},
            {"rank":3,"appid":3240220,"peak_in_game":312000}]}}"""
        val games = Steam.parseMostPlayed(json)
        assertEquals(3, games.size)
        assertEquals(730, games[0].appId)
        assertEquals(1_726_304L, games[0].peakToday)
        assertEquals(0, games[0].rankChange)
        assertEquals(1, games[1].rankChange)
        assertNull(games[2].lastWeekRank)
        assertNull(games[2].rankChange)
    }

    @Test
    fun storeItemsGiveNamesPicturesAndPrices() {
        val json = """{"response":{"store_items":[
            {"item_type":0,"id":730,"success":1,"name":"Counter-Strike 2","appid":730,"is_free":true,
             "assets":{"asset_url_format":"steam/apps/730/${'$'}{FILENAME}?t=1729703045","header":"header.jpg"}},
            {"item_type":0,"id":1086940,"success":1,"name":"Baldur's Gate 3","appid":1086940,
             "best_purchase_option":{"formatted_final_price":"£34.99","formatted_original_price":"£49.99","discount_pct":30}}]}}"""
        val items = Steam.parseItems(json)
        val cs = items.getValue(730)
        assertEquals("Counter-Strike 2", cs.name)
        assertEquals("Free", cs.price)
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/730/header.jpg?t=1729703045", cs.imageUrl)
        val bg3 = items.getValue(1086940)
        assertEquals("£34.99", bg3.price)
        assertEquals("£49.99", bg3.originalPrice)
        assertEquals(30, bg3.discountPercent)
        assertTrue(bg3.imageUrl.endsWith("/steam/apps/1086940/header.jpg"))
    }

    @Test
    fun playersNow() {
        assertEquals(1_234_567L, Steam.parsePlayers("""{"response":{"player_count":1234567,"result":1}}"""))
        assertNull(Steam.parsePlayers("""{"response":{"result":42}}"""))
        // Steam says 0 for some games while people are playing: not known, not "0 playing".
        assertNull(Steam.parsePlayers("""{"response":{"player_count":0,"result":1}}"""))
        val now = Steam.parseConcurrent(
            """{"response":{"last_update":1760000000,"ranks":[
                {"rank":1,"appid":730,"concurrent_in_game":1312345,"peak_in_game":1600000},
                {"rank":2,"appid":570,"concurrent_in_game":612000,"peak_in_game":700000},
                {"rank":3,"appid":99,"concurrent_in_game":0}]}}""",
        )
        assertEquals(mapOf(730 to 1_312_345L, 570 to 612_000L), now)
        assertTrue(Steam.parseConcurrent("""{"response":{}}""").isEmpty())
    }

    @Test
    fun topSellersWithAndWithoutListings() {
        val json = """{"response":{"ranks":[
            {"rank":1,"appid":3240220,"last_week_rank":2,"consecutive_weeks":3,"item":{"appid":3240220,"name":"Some Game"}},
            {"rank":2,"appid":730}]}}"""
        val ranked = Steam.parseTopSellers(json)
        assertEquals("Some Game", ranked[0].first.name)
        assertEquals(1, ranked[0].first.rankChange)
        assertEquals(3, ranked[0].first.weeksOnChart)
        assertNull(ranked[1].second)
        assertEquals("", ranked[1].first.name)
    }

    @Test
    fun featuredSpecialsSkipBundlesAndDuplicates() {
        Locale.setDefault(Locale.UK)
        val json = """{"specials":{"id":"cat_specials","name":"Specials","items":[
            {"id":1086940,"type":0,"name":"Baldur's Gate 3","discounted":true,"discount_percent":30,"original_price":4999,"final_price":3499,"currency":"GBP","header_image":"https://x/h.jpg","discount_expiration":1760900000},
            {"id":1086940,"type":0,"name":"Baldur's Gate 3","discounted":true,"discount_percent":30,"original_price":4999,"final_price":3499,"currency":"GBP"},
            {"id":555,"type":1,"name":"A bundle","final_price":100,"currency":"GBP"}]}}"""
        val deals = Steam.parseFeatured(json, "specials")
        assertEquals(1, deals.size)
        val d = deals[0]
        assertEquals(1, d.rank)
        assertEquals("£34.99", d.price)
        assertEquals("£49.99", d.originalPrice)
        assertEquals(30, d.discountPercent)
        assertEquals(1760900000L, d.dealEnds)
    }

    @Test
    fun appDetails() {
        val json = """{"1086940":{"success":true,"data":{"name":"Baldur's Gate 3","short_description":"Gather your party &amp; return to the Forgotten Realms.",
            "header_image":"https://x/header.jpg","developers":["Larian Studios"],"publishers":["Larian Studios"],
            "release_date":{"coming_soon":false,"date":"3 Aug, 2023"},"genres":[{"id":"1","description":"Adventure"},{"id":"3","description":"RPG"}],
            "price_overview":{"currency":"GBP","initial":4999,"final":3499,"discount_percent":30,"initial_formatted":"£49.99","final_formatted":"£34.99"},
            "platforms":{"windows":true,"mac":true,"linux":false},"metacritic":{"score":96},"recommendations":{"total":712000},
            "screenshots":[{"id":0,"path_full":"https://x/1.jpg"},{"id":1,"path_full":"https://x/2.jpg"}],"controller_support":"full"}}}"""
        val d = Steam.parseDetails(json, 1086940)!!
        assertEquals("Gather your party & return to the Forgotten Realms.", d.description)
        assertEquals(listOf("Adventure", "RPG"), d.genres)
        assertEquals("£34.99", d.price)
        assertEquals(96, d.metacritic)
        assertTrue(d.mac)
        assertFalse(d.linux)
        assertEquals(2, d.screenshots.size)
        assertNull(Steam.parseDetails("""{"5":{"success":false}}""", 5))
    }

    @Test
    fun steamOnline() {
        assertEquals(38_123_456L to 12_345_678L, Steam.parseOnline("""{"users_online":"38,123,456","users_ingame":"12,345,678"}"""))
    }

    @Test
    fun htmlToText() {
        assertEquals("A & B\n\"C\"", Html.toText("<b>A &amp; B</b><br/>&quot;C&quot;"))
    }
}
