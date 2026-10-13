package com.jambo199.vividpulse

import com.jambo199.vividpulse.system.GsmArena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Reading phones' specs from GSMArena's search results and phone pages. */
class GsmArenaTest {
    private val search = """
        <div class="makers">
        <ul>
        <li><a href="vivo_x200_ultra-13762.php"><img src="https://fdn2.gsmarena.com/vv/bigpic/vivo-x200-ultra.jpg" title="vivo X200 Ultra Android smartphone. Announced Apr 2025. Features 6.82″ display"><strong><span>vivo<br>X200 Ultra</span></strong></a></li>
        <li><a href="vivo_x200-13417.php"><img src="x.jpg" title="vivo X200"><strong><span>vivo<br>X200</span></strong></a></li>
        <li><a href="vivo_x200_pro-13418.php"><img src="y.jpg" title="vivo X200 Pro"><strong><span>vivo<br>X200 Pro</span></strong></a></li>
        </ul>
        </div>
    """

    private val page = """
        <h1 class="specs-phone-name-title" data-spec="modelname">vivo X200 Ultra</h1>
        <tr><td class="ttl">Type</td><td class="nfo" data-spec="displaytype">LTPO AMOLED, 1B colors, 120Hz, Dolby Vision, HDR Vivid, 4500 nits (peak)</td></tr>
        <tr><td class="nfo" data-spec="cam1modules">50 MP, f/1.7, 35mm (wide), 1/1.28&quot;, 1.22µm, dual pixel PDAF, gimbal OIS<br />
        200 MP, f/2.3, 85mm (periscope telephoto), 1/1.4", 0.56µm, multi-directional PDAF, OIS, 3.7x optical zoom<br />
        50 MP, f/2.0, 14mm, 116˚ (ultrawide), 1/1.28", 1.22µm, dual pixel PDAF, OIS</td></tr>
        <tr><td class="nfo" data-spec="cam1features">Zeiss optics, Laser AF, color spectrum sensor, LED flash, panorama, HDR</td></tr>
        <tr><td class="nfo" data-spec="cam1video">8K@30fps, 4K@24/30/60/120fps, 1080p@30/60/120/240fps, gyro-EIS, Dolby Vision HDR, 10-bit Log</td></tr>
        <tr><td class="nfo" data-spec="cam2modules">50 MP, f/2.5, 24mm (wide), 1/2.76", 0.64µm, AF</td></tr>
        <tr><td class="nfo" data-spec="cam2features">Panorama</td></tr>
        <tr><td class="nfo" data-spec="cam2video">4K@30/60fps, 1080p@30/60fps</td></tr>
    """

    @Test
    fun findsThePhoneItself() {
        val results = GsmArena.parseResults(search)
        assertEquals(listOf("vivo X200 Ultra", "vivo X200", "vivo X200 Pro"), results.map { it.first })
        assertEquals("https://www.gsmarena.com/vivo_x200_ultra-13762.php", GsmArena.bestMatch(results, "vivo X200 Ultra"))
        // "X200" is the X200, not the first phone whose name holds it.
        assertEquals("https://www.gsmarena.com/vivo_x200-13417.php", GsmArena.bestMatch(results, "vivo X200"))
        assertNull(GsmArena.bestMatch(results, "vivo X300 Pro"))
        assertNull(GsmArena.bestMatch(emptyList(), "vivo X200"))
        val samsung = listOf(
            "Samsung Galaxy S24 Ultra" to "u", "Samsung Galaxy S24+" to "p", "Samsung Galaxy S24" to "s", "Samsung Galaxy S24 FE" to "fe",
        )
        assertEquals("s", GsmArena.bestMatch(samsung, "Samsung Galaxy S24"))
        assertEquals("p", GsmArena.bestMatch(samsung, "Samsung Galaxy S24 Plus"))
        assertEquals("u", GsmArena.bestMatch(samsung, "Samsung Galaxy S24 Ultra 5G"))
    }

    @Test
    fun readsCamerasAndScreen() {
        val sheet = GsmArena.parsePhone(page, "https://www.gsmarena.com/vivo_x200_ultra-13762.php")
        assertNotNull(sheet)
        val back = sheet!!.back!!
        assertEquals(listOf("Main", "Periscope telephoto", "Ultra-wide"), back.lenses.map { it.kind })
        assertEquals(listOf(50f, 200f, 50f), back.lenses.map { it.megapixels })
        assertEquals(listOf(35, 85, 14), back.lenses.map { it.mm })
        assertEquals(listOf("1/1.28\"", "1/1.4\"", "1/1.28\""), back.lenses.map { it.sensor })
        assertEquals(listOf("f/1.7", "f/2.3", "f/2.0"), back.lenses.map { it.aperture })
        assertTrue(back.lenses.all { it.ois })
        assertEquals("3.7× optical", back.lenses[1].zoom)
        assertEquals("8K 30 fps · 4K 120 fps · 1080p 240 fps", back.video)
        assertEquals(listOf("Dolby Vision"), back.hdrVideo)
        assertEquals(true, back.hdrPhotos)
        val front = sheet.front!!
        assertEquals("Selfie", front.lenses.single().kind)
        assertFalse(front.lenses.single().ois)
        assertEquals("4K 60 fps · 1080p 60 fps", front.video)
        assertTrue(front.hdrVideo.isEmpty())
        assertEquals(false, front.hdrPhotos)
        assertEquals(listOf("Dolby Vision", "HDR Vivid"), sheet.displayHdr)
    }

    @Test
    fun hdrNamesAndOddLenses() {
        assertEquals(listOf("HDR10+", "HDR10"), GsmArena.hdrFormats("Dynamic AMOLED 2X, 120Hz, HDR10+, HDR10"))
        assertEquals(listOf("HDR"), GsmArena.hdrFormats("4K@30fps, HDR, gyro-EIS"))
        assertTrue(GsmArena.hdrFormats("IPS LCD, 90Hz").isEmpty())
        assertEquals("Macro", GsmArena.lens("2 MP, f/2.4, (macro)", front = false)?.kind)
        assertEquals("Depth", GsmArena.lens("TOF 3D, (depth)", front = false)?.kind)
        assertEquals("1\"", GsmArena.lens("50 MP, f/1.6, 23mm (wide), 1.0\", 1.6µm, OIS", front = false)?.sensor?.replace("1.0", "1"))
        assertNull(GsmArena.lens("", front = false))
    }

    /** GSMArena's real pages, when the build fetched them (ci-gsm/), so a change in their layout shows up. */
    @Test
    fun readsTheRealPages() {
        val dir = File("../ci-gsm")
        val searchFile = File(dir, "search.html")
        val pageFile = File(dir, "phone.html")
        assumeTrue("GSMArena pages weren't fetched", pageFile.exists() && pageFile.readText().contains("data-spec"))
        if (searchFile.exists()) {
            val results = GsmArena.parseResults(searchFile.readText())
            // Shown in the build log; GSMArena's search page changes more often than its phone pages.
            System.err.println("GSMArena search found ${results.size}: ${results.take(12)}")
            System.err.println("GSMArena best match: ${GsmArena.bestMatch(results, "vivo X200 Ultra")}")
        }
        val sheet = GsmArena.parsePhone(pageFile.readText(), "https://www.gsmarena.com/vivo_x200_ultra-13762.php")
        println("page: $sheet")
        assertNotNull(sheet)
        assertEquals(3, sheet!!.back!!.lenses.size)
        assertTrue(sheet.back!!.video!!.startsWith("8K"))
        assertEquals(1, sheet.front!!.lenses.size)
    }
}
