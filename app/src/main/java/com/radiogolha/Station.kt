package com.radiogolha

/** What a song's tags say about it; null when a tag is missing. Read from: singer = artist, dastgah = genre, program = album. */
data class Tags(val singer: String? = null, val dastgah: String? = null, val program: String? = null)

/** A station is a filter on the tags: it plays only the songs that match, as its own shuffled radio. */
data class Station(val kind: Kind, val value: String = "") {
    enum class Kind(val heading: String) { ALL(""), SINGER("Singers"), DASTGAH("Dastgah"), PROGRAM("Programs") }

    val name: String get() = if (kind == Kind.ALL) "All songs" else value

    fun matches(tags: Tags): Boolean = when (kind) {
        Kind.ALL -> true
        Kind.SINGER -> tags.singer == value
        Kind.DASTGAH -> tags.dastgah == value
        Kind.PROGRAM -> tags.program == value
    }

    /** How a station is saved, e.g. "SINGER:Banan". */
    val key: String get() = "${kind.name}:$value"

    companion object {
        val ALL = Station(Kind.ALL)

        fun fromKey(key: String?): Station {
            val kind = Kind.entries.find { it.name == key?.substringBefore(':') } ?: return ALL
            return if (kind == Kind.ALL) ALL else Station(kind, key!!.substringAfter(':'))
        }

        /** "All songs", then a station for every singer, dastgah and program in the tags, each group sorted by name. */
        fun listFor(songs: List<Tags>): List<Station> {
            val groups = listOf(Kind.SINGER to Tags::singer, Kind.DASTGAH to Tags::dastgah, Kind.PROGRAM to Tags::program)
            return listOf(ALL) + groups.flatMap { (kind, tag) ->
                songs.mapNotNull(tag).distinct().sorted().map { Station(kind, it) }
            }
        }
    }
}
