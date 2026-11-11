package com.radiogolha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StationTest {
    private val banan = Tags(singer = "Banan", dastgah = "Mahur", program = "Golha-ye Rangarang 1")
    private val marzieh = Tags(singer = "Marzieh", dastgah = "Shur", program = "Golha-ye Rangarang 1")
    private val untagged = Tags()

    @Test
    fun `stations come from the tags, all songs first, each group sorted`() {
        val names = Station.listFor(listOf(marzieh, banan, untagged, banan)).map { it.kind to it.name }
        assertEquals(
            listOf(
                Station.Kind.ALL to "All songs",
                Station.Kind.SINGER to "Banan", Station.Kind.SINGER to "Marzieh",
                Station.Kind.DASTGAH to "Mahur", Station.Kind.DASTGAH to "Shur",
                Station.Kind.PROGRAM to "Golha-ye Rangarang 1",
            ),
            names,
        )
    }

    @Test
    fun `a station matches only its own songs, untagged songs only play on all songs`() {
        val singer = Station(Station.Kind.SINGER, "Banan")
        assertTrue(singer.matches(banan))
        assertFalse(singer.matches(marzieh))
        assertFalse(singer.matches(untagged))
        assertTrue(Station(Station.Kind.DASTGAH, "Shur").matches(marzieh))
        assertTrue(Station.ALL.matches(untagged))
    }

    @Test
    fun `a station survives being saved and loaded, even with a colon in its name`() {
        val station = Station(Station.Kind.PROGRAM, "Golha: Barg-e Sabz 12")
        assertEquals(station, Station.fromKey(station.key))
        assertEquals(Station.ALL, Station.fromKey(Station.ALL.key))
        assertEquals(Station.ALL, Station.fromKey(null))
        assertEquals(Station.ALL, Station.fromKey("nonsense"))
    }

    @Test
    fun `a singer station deals only that singer, without repeats`() {
        val library = mapOf("1" to banan, "2" to marzieh, "3" to banan, "4" to untagged, "5" to banan)
        val station = Station(Station.Kind.SINGER, "Banan")
        val ids = library.filterValues(station::matches).keys.toList()
        val queue = RadioQueue(Random(7))
        val played = mutableListOf<String>()
        repeat(20) { played += queue.newRound(ids, lastPlayed = played.lastOrNull()) }
        assertEquals(setOf("1", "3", "5"), played.toSet())
        played.chunked(3).forEach { assertEquals(listOf("1", "3", "5"), it.sorted()) }
    }
}
