package com.jambo199.vividpulse

import com.jambo199.vividpulse.update.Changelog
import com.jambo199.vividpulse.update.UpdateSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogTest {
    private val json = """{"entries":[
        {"version":"1.0.102","code":102,"date":"2026-10-13","title":"OriginOS named correctly","size":"major","changes":["One","Two"]},
        {"code":5,"date":"2026-10-12","title":"Little fixes","size":"small","changes":["Fix"]},
        {"code":106,"title":"Cameras","size":"MINOR","changes":[]},
        {"title":"no code"}]}"""

    @Test
    fun parsesNewestFirst() {
        val list = Changelog.parse(json)
        assertEquals(listOf(106, 102, 5), list.map { it.code })
        assertEquals("1.0.5", list[2].version)
        assertEquals(UpdateSize.MINOR, list[0].size)
        assertEquals(UpdateSize.MAJOR, list[1].size)
        assertEquals(listOf("One", "Two"), list[1].changes)
        assertEquals(UpdateSize.SMALL, UpdateSize.of("huge"))
    }

    @Test
    fun newerListWinsWhenMerging() {
        val bundled = Changelog.parse(json)
        val fetched = Changelog.parse("""{"entries":[{"code":107,"title":"New","size":"small","changes":["a"]},{"code":106,"title":"Cameras, fixed","size":"minor","changes":["b"]}]}""")
        val merged = Changelog.merge(bundled, fetched)
        assertEquals(listOf(107, 106, 102, 5), merged.map { it.code })
        assertEquals("Cameras, fixed", merged[1].title)
        assertEquals(mapOf(UpdateSize.MAJOR to 1, UpdateSize.MINOR to 1, UpdateSize.SMALL to 2), Changelog.counts(merged))
        assertTrue(Changelog.parse("{}").isEmpty())
    }
}
