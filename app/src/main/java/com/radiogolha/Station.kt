package com.radiogolha

/**
 * What a song's tags say about it; empty or null when a tag is missing. Read from: singer = artist, dastgah = genre,
 * program = album. A Golha programme often has several singers (and now and then two dastgahs), so those are lists.
 */
data class Tags(val singers: List<String> = emptyList(), val dastgahs: List<String> = emptyList(), val program: String? = null) {
    constructor(singer: String?, dastgah: String? = null, program: String? = null) : this(listOfNotNull(singer), listOfNotNull(dastgah), program)
}

/** A station is a filter on the tags: it plays only the songs that match, as its own shuffled radio. */
data class Station(val kind: Kind, val value: String = "") {
    /** How each kind is named on screen. */
    enum class Kind(val label: String) { ALL("همه‌ی برنامه‌ها"), SINGER("خواننده"), DASTGAH("دستگاه"), PROGRAM("برنامه") }

    val name: String get() = if (kind == Kind.ALL) Kind.ALL.label else value

    fun matches(tags: Tags): Boolean = when (kind) {
        Kind.ALL -> true
        Kind.SINGER -> value in tags.singers
        Kind.DASTGAH -> value in tags.dastgahs
        Kind.PROGRAM -> tags.program == value
    }

    /** How a station is saved, e.g. "SINGER:بنان". */
    val key: String get() = "${kind.name}:$value"

    companion object {
        val ALL = Station(Kind.ALL)

        fun fromKey(key: String?): Station {
            val kind = Kind.entries.find { it.name == key?.substringBefore(':') } ?: return ALL
            return if (kind == Kind.ALL) ALL else Station(kind, key!!.substringAfter(':'))
        }

        /** Shuffle skips stations smaller than this, so next still has something to choose from. */
        const val SHUFFLE_MIN_SONGS = 5

        /**
         * A random singer or dastgah station to tune to, other than [current], with at least [SHUFFLE_MIN_SONGS]
         * songs; null if there's none.
         */
        fun shuffle(songsPerStation: Map<Station, Int>, current: Station, random: kotlin.random.Random = kotlin.random.Random): Station? =
            songsPerStation
                .filter { (s, n) -> s.kind in setOf(Kind.SINGER, Kind.DASTGAH) && s != current && n >= SHUFFLE_MIN_SONGS }
                .keys.toList().randomOrNull(random)

        /** "All", then a station for every singer, dastgah and program in the tags, each group in alphabetical order. */
        fun listFor(songs: List<Tags>): List<Station> {
            val groups = listOf<Pair<Kind, (Tags) -> List<String>>>(
                Kind.SINGER to Tags::singers, Kind.DASTGAH to Tags::dastgahs, Kind.PROGRAM to { listOfNotNull(it.program) },
            )
            return listOf(ALL) + groups.flatMap { (kind, tag) ->
                songs.flatMap(tag).distinct().sortedWith(PersianOrder).map { Station(kind, it) }
            }
        }
    }
}

/**
 * Persian alphabetical order. Sorting by character code puts the four Persian letters (پ چ ژ گ) after ی, since
 * they were added to Unicode after the Arabic ones; here each letter gets its place in the alphabet. The
 * zero-width non-joiner (as in گل‌ها) is ignored; anything else (Latin names, digits) comes after Persian, by code.
 */
object PersianOrder : Comparator<String> {
    private const val ALPHABET = "آاأإبپتثجچحخدذرزژسشصضطظعغفقکگلمنوهیئ"
    private const val ZWNJ = '‌'

    private fun rank(c: Char): Int {
        val letter = when (c) { 'ي' -> 'ی'; 'ك' -> 'ک'; 'ة' -> 'ه'; 'ؤ' -> 'و'; else -> c }
        val i = ALPHABET.indexOf(letter)
        return when {
            c == ' ' -> -1
            i >= 0 -> i
            else -> ALPHABET.length + c.code
        }
    }

    override fun compare(a: String, b: String): Int {
        val x = a.filter { it != ZWNJ }
        val y = b.filter { it != ZWNJ }
        for (i in 0 until minOf(x.length, y.length)) {
            val d = rank(x[i]).compareTo(rank(y[i]))
            if (d != 0) return d
        }
        return x.length.compareTo(y.length)
    }
}

/** Numbers in Persian digits: 346 → ۳۴۶. */
fun Int.persianDigits(): String = toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
