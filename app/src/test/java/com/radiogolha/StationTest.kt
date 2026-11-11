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
                Station.Kind.ALL to Station.Kind.ALL.label,
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
    fun `a programme with two singers plays on both singers' stations, each listed once`() {
        val duet = Tags(singers = listOf("Marzieh", "Banan"), dastgahs = listOf("Abu Atta", "Dashti"))
        assertTrue(Station(Station.Kind.SINGER, "Banan").matches(duet))
        assertTrue(Station(Station.Kind.SINGER, "Marzieh").matches(duet))
        assertTrue(Station(Station.Kind.DASTGAH, "Dashti").matches(duet))
        assertFalse(Station(Station.Kind.SINGER, "Elahe").matches(duet))
        val singers = Station.listFor(listOf(duet, banan, marzieh)).filter { it.kind == Station.Kind.SINGER }.map { it.name }
        assertEquals(listOf("Banan", "Marzieh"), singers)
    }

    @Test
    fun `stations are in Persian alphabetical order, not character-code order`() {
        val singers = listOf("گلپایگانی", "تاج", "پوران", "بنان", "چاووشی", "کسایی", "ژاله", "رضا").map { Tags(singer = it) }
        assertEquals(
            listOf("بنان", "پوران", "تاج", "چاووشی", "رضا", "ژاله", "کسایی", "گلپایگانی"),
            Station.listFor(singers).drop(1).map { it.name },
        )
        // The zero-width non-joiner in "سه‌گاه" doesn't change its place; the Arabic yeh sorts as the Persian one.
        assertTrue(PersianOrder.compare("سه\u200Cگاه", "سهگاه") == 0)
        assertTrue(PersianOrder.compare("شجريان", "شجریان") == 0)
    }

    @Test
    fun `shuffle tunes to another singer or dastgah with enough songs`() {
        val banan = Station(Station.Kind.SINGER, "بنان")
        val vaziri = Station(Station.Kind.SINGER, "عبدالعلی وزیری")
        val dashti = Station(Station.Kind.DASTGAH, "دشتی")
        val program = Station(Station.Kind.PROGRAM, "گل‌های رنگارنگ")
        val counts = mapOf(Station.ALL to 346, banan to 31, vaziri to 1, dashti to 55, program to 346)
        val random = Random(8)
        val picks = (1..200).mapNotNull { Station.shuffle(counts, current = banan, random = random) }.toSet()
        assertEquals(setOf(dashti), picks)
        assertEquals(setOf(banan, dashti), (1..200).mapNotNull { Station.shuffle(counts, Station.ALL, random) }.toSet())
        assertEquals(null, Station.shuffle(mapOf(Station.ALL to 3, vaziri to 1), Station.ALL, random))
    }

    @Test
    fun `numbers show in Persian digits`() {
        assertEquals("۳۴۶", 346.persianDigits())
        assertEquals("۰", 0.persianDigits())
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
