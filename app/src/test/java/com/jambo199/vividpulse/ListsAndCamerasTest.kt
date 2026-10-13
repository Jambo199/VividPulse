package com.jambo199.vividpulse

import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.games.Steam
import com.jambo199.vividpulse.games.StoreSearch
import com.jambo199.vividpulse.system.CameraMath
import com.jambo199.vividpulse.system.KnownPhones
import com.jambo199.vividpulse.ui.games.DealSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListsAndCamerasTest {
    @Test
    fun storeSearchPageKeepsOrderAndSkipsBundles() {
        val html = """
            <a href="https://store.steampowered.com/app/2622380/" data-ds-appid="2622380" class="search_result_row ds_collapse_flag"></a>
            <a href="https://store.steampowered.com/bundle/1/" data-ds-bundleid="1" data-ds-appid="10,20,30" class="search_result_row"></a>
            <a href="https://store.steampowered.com/app/1245620/" data-ds-appid="1245620" class="search_result_row"></a>
            <a href="https://store.steampowered.com/app/2622380/" data-ds-appid="2622380" class="search_result_row"></a>
        """.trimIndent()
        val json = org.json.JSONObject().put("success", 1).put("results_html", html).put("total_count", 3120).put("start", 0).toString()
        val page = Steam.parseSearchPage(json)
        assertEquals(listOf(2622380, 1245620), page.appIds)
        assertEquals(4, page.rows)
        assertEquals(3120, page.total)
    }

    @Test
    fun storeSearchAddresses() {
        val url = Steam.storeSearchUrl(StoreSearch.DEALS, 50, 50, "GB")
        assertTrue(url.startsWith("https://store.steampowered.com/search/results/?specials=1&filter=topsellers"))
        assertTrue(url.contains("&start=50&count=50&infinite=1&cc=GB"))
        assertTrue(Steam.storeSearchUrl(StoreSearch.UPCOMING, 0, 50, "GB").contains("filter=popularcomingsoon"))
        assertTrue(Steam.storeSearchUrl(StoreSearch.WISHLISTED, 0, 50, "GB").contains("filter=popularwishlist"))
    }

    @Test
    fun storeItemsCarryReviewsReleaseAndDealEnd() {
        val json = """{"response":{"store_items":[
            {"id":1086940,"appid":1086940,"name":"Baldur's Gate 3",
             "best_purchase_option":{"formatted_final_price":"£34.99","formatted_original_price":"£49.99","discount_pct":30,
                "final_price_in_cents":"3499","active_discounts":[{"discount_amount":"1500","discount_end_date":1760900000}]},
             "release":{"steam_release_date":1691020800},
             "reviews":{"summary_filtered":{"review_count":712000,"percent_positive":96,"review_score":9,"review_score_label":"Overwhelmingly Positive"}}},
            {"id":2,"appid":2,"name":"Not Out Yet",
             "release":{"steam_release_date":1798761600,"is_coming_soon":true,"coming_soon_display":"date_year"}},
            {"id":3,"appid":3,"name":"Someday",
             "release":{"is_coming_soon":true,"custom_release_date_message":"Coming 2027"}},
            {"id":4,"appid":4,"name":"Next Spring",
             "release":{"steam_release_date":1806537600,"is_coming_soon":true,"coming_soon_display":"date_quarter"}}]}}"""
        val items = Steam.parseItems(json)
        val bg3 = items.getValue(1086940)
        assertEquals(3499L, bg3.priceCents)
        assertEquals(1760900000L, bg3.dealEnds)
        assertEquals("Overwhelmingly Positive", bg3.reviewLabel)
        assertEquals(712000L, bg3.reviewCount)
        assertEquals(96, bg3.reviewPercent)
        assertNull(bg3.release)
        assertEquals("2027", items.getValue(2).release)
        assertEquals("Coming 2027", items.getValue(3).release)
        assertEquals("Q2 2027", items.getValue(4).release)
        val g = bg3.toGame(4)
        assertEquals(4, g.rank)
        assertEquals(30, g.discountPercent)
    }

    private fun game(id: Int, rank: Int, players: Long? = null, discount: Int = 0, percent: Int? = null, reviews: Long? = null) =
        Game(id, "Game $id", "", rank, playersNow = players, discountPercent = discount, reviewPercent = percent, reviewCount = reviews)

    @Test
    fun dealSorts() {
        val games = listOf(
            game(1, 1, players = 500, discount = 20, percent = 98, reviews = 40),
            game(2, 2, players = 90_000, discount = 75, percent = 95, reviews = 200_000),
            game(3, 3, players = null, discount = 90),
            game(4, 4, players = 3_000, discount = 75, percent = 80, reviews = 5_000),
        )
        assertEquals(listOf(1, 2, 3, 4), DealSort.POPULAR.sort(games.shuffled()).map { it.appId })
        assertEquals(listOf(2, 4, 1, 3), DealSort.PLAYERS.sort(games).map { it.appId })
        assertEquals(listOf(3, 2, 4, 1), DealSort.DISCOUNT.sort(games).map { it.appId })
        // 95% of 200,000 and 80% of 5,000 beat 98% of 40 (as Steam's own labels would); no reviews goes last.
        assertEquals(listOf(2, 4, 1, 3), DealSort.RATED.sort(games).map { it.appId })
    }

    @Test
    fun sensorSizesAsMakersQuoteThem() {
        assertEquals("1/1.28\"", CameraMath.opticalFormat(12.5f))
        assertEquals("1/1.4\"", CameraMath.opticalFormat(16f / 1.4f))
        assertEquals("1/2\"", CameraMath.opticalFormat(8f))
        assertEquals("1\"", CameraMath.opticalFormat(16f))
    }

    @Test
    fun videoModesBiggestFirstWithSlowMotion() {
        val normal = listOf(
            Triple(7680, 4320, 30), Triple(3840, 2160, 60), Triple(4096, 2160, 30),
            Triple(1920, 1080, 60), Triple(1280, 720, 60), Triple(640, 480, 30),
        )
        val slow = listOf(Triple(3840, 2160, 120), Triple(1920, 1080, 240), Triple(1280, 720, 60))
        val modes = CameraMath.videoModes(normal, slow)
        assertEquals(listOf("8K", "4K", "1080p", "720p"), modes.map { it.name })
        assertEquals("8K 30 fps", modes[0].toString())
        assertEquals("4K 60 fps · 120 fps slow-mo", modes[1].toString())
        assertEquals(240, modes[2].slowMotionFps)
        // Slow motion no faster than normal recording isn't worth showing.
        assertNull(modes[3].slowMotionFps)
        assertTrue(CameraMath.videoModes(emptyList(), slow).isEmpty())
    }

    @Test
    fun hdrFormats() {
        assertEquals(listOf("HLG", "HDR10", "HDR10+", "Dolby Vision"), CameraMath.hdrNames(setOf(1L, 2L, 4L, 8L, 64L)))
        assertTrue(CameraMath.hdrNames(setOf(1L)).isEmpty())
    }

    @Test
    fun x200UltraAsVivoSellsIt() {
        val known = KnownPhones.cameras("vivo X200 Ultra")
        assertNotNull(known)
        val back = known!!.lenses.filter { it.kind != "Selfie" }
        assertEquals(listOf(14, 35, 85), back.mapNotNull { it.mm }.sorted())
        assertEquals(200, back.maxOf { it.megapixels })
        assertEquals("8K 30 fps · 4K 120 fps · 240 fps slow motion", known.backVideo)
        assertEquals(listOf("Selfie"), known.lenses.filter { it.front }.map { it.kind })
        assertNull(KnownPhones.cameras("Pixel 9"))
    }

    @Test
    fun videoInOneShortLine() {
        val wide = CameraMath.videoModes(listOf(Triple(3840, 2160, 60), Triple(1920, 1080, 60)), listOf(Triple(1920, 1080, 240)))
        val tele = CameraMath.videoModes(listOf(Triple(7680, 4320, 24), Triple(3840, 2160, 30)), emptyList())
        assertEquals("8K 24 fps · 4K 60 fps · 240 fps slow motion", CameraMath.videoSummary(wide + tele))
        assertEquals("4K 60 fps · 240 fps slow motion", CameraMath.videoSummary(wide))
        assertEquals("1080p 30 fps", CameraMath.videoSummary(CameraMath.videoModes(listOf(Triple(1920, 1080, 30)), emptyList())))
        assertNull(CameraMath.videoSummary(emptyList()))
    }
}
