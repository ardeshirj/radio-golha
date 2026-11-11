package com.radiogolha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class RadioQueueTest {
    private val songs = listOf("a", "b", "c", "d", "e")

    @Test
    fun `a round has every song exactly once`() {
        val queue = RadioQueue(Random(1))
        repeat(200) {
            assertEquals(songs.sorted(), queue.newRound(songs, lastPlayed = null).sorted())
        }
    }

    @Test
    fun `a new round never starts with the song that just played`() {
        val queue = RadioQueue(Random(2))
        repeat(1000) {
            val last = songs.random()
            assertNotEquals(last, queue.newRound(songs, lastPlayed = last).first())
        }
    }

    @Test
    fun `playing many rounds back to back never repeats a song too soon`() {
        val queue = RadioQueue(Random(3))
        val played = mutableListOf<String>()
        repeat(100) { played += queue.newRound(songs, lastPlayed = played.lastOrNull()) }
        // Each round has all 5 songs, and no song plays twice in a row across round boundaries.
        played.chunked(5).forEach { assertEquals(songs.sorted(), it.sorted()) }
        played.zipWithNext().forEach { (x, y) -> assertNotEquals(x, y) }
    }

    @Test
    fun `restoring keeps the saved order and drops songs that are gone`() {
        val queue = RadioQueue(Random(4))
        val restored = queue.restore(saved = listOf("c", "x", "a", "e"), library = songs)
        assertEquals(listOf("c", "a", "e"), restored)
    }

    @Test
    fun `restoring with nothing usable starts a fresh round`() {
        val queue = RadioQueue(Random(5))
        assertEquals(songs.sorted(), queue.restore(saved = emptyList(), library = songs).sorted())
        assertEquals(songs.sorted(), queue.restore(saved = listOf("gone"), library = songs).sorted())
    }

    @Test
    fun `a single song still works`() {
        val queue = RadioQueue(Random(6))
        assertEquals(listOf("a"), queue.newRound(listOf("a"), lastPlayed = "a"))
    }
}
