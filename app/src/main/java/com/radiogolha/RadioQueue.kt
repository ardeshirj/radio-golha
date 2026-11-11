package com.radiogolha

import kotlin.random.Random

/**
 * The radio's running order, as song ids. Plain Kotlin with no Android parts, so it can be tested on the Mac.
 *
 * Songs are dealt in rounds: every song once, in random order. A new round never starts with the song that
 * just played, so the same song can't play twice in a row at a round boundary.
 */
class RadioQueue(private val random: Random = Random.Default) {

    /** A fresh round: every song in [library] once, shuffled, not starting with [lastPlayed]. */
    fun newRound(library: List<String>, lastPlayed: String?): List<String> {
        val round = library.distinct().shuffled(random).toMutableList()
        if (round.size > 1 && round.first() == lastPlayed) {
            val other = 1 + random.nextInt(round.size - 1)
            round[0] = round[other].also { round[other] = round[0] }
        }
        return round
    }

    /**
     * Picks up a saved queue: the songs still to play, minus any that are no longer on the phone.
     * If nothing usable is left, starts a new round.
     */
    fun restore(saved: List<String>, library: List<String>): List<String> {
        val onPhone = library.toSet()
        return saved.filter { it in onPhone }.ifEmpty { newRound(library, lastPlayed = null) }
    }
}
