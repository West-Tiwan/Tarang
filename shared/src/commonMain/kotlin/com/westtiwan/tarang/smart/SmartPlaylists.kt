package com.westtiwan.tarang.smart

import com.westtiwan.tarang.data.Song
import kotlinx.serialization.Serializable

@Serializable
enum class SmartField(val label: String, val kind: Kind) {
    TITLE("Title", Kind.TEXT), ARTIST("Artist", Kind.TEXT), ALBUM("Album", Kind.TEXT), ALBUM_ARTIST("Album artist", Kind.TEXT),
    GENRE("Genre", Kind.TEXT), PATH("File path", Kind.TEXT), FORMAT("Format", Kind.TEXT), COMMENT("Comment", Kind.TEXT),
    YEAR("Year", Kind.NUMBER), RATING("Rating", Kind.NUMBER), PLAY_COUNT("Play count", Kind.NUMBER),
    DURATION("Duration (sec)", Kind.NUMBER), BITRATE("Bitrate (kbps)", Kind.NUMBER), SAMPLE_RATE("Sample rate (Hz)", Kind.NUMBER),
    BIT_DEPTH("Bit depth", Kind.NUMBER), BPM("BPM", Kind.NUMBER), TRACK("Track number", Kind.NUMBER), DISC("Disc number", Kind.NUMBER),
    DATE_ADDED("Date added", Kind.DATE), LAST_PLAYED("Last played", Kind.DATE), STARRED_AT("Date favourited", Kind.DATE),
    STARRED("Favourite", Kind.BOOL), LOSSLESS("Lossless", Kind.BOOL), HI_RES("Hi-res", Kind.BOOL);

    enum class Kind { TEXT, NUMBER, DATE, BOOL }
}

@Serializable
enum class SmartOp(val label: String) {
    CONTAINS("contains"), NOT_CONTAINS("does not contain"), IS("is"), IS_NOT("is not"), STARTS_WITH("starts with"), ENDS_WITH("ends with"),
    GT("greater than"), LT("less than"), BETWEEN("between"),
    IN_LAST("in the last (days)"), NOT_IN_LAST("not in the last (days)"), NEVER("never"),
    TRUE("is true"), FALSE("is false");

    companion object {
        fun forKind(kind: SmartField.Kind): List<SmartOp> = when (kind) {
            SmartField.Kind.TEXT -> listOf(CONTAINS, NOT_CONTAINS, IS, IS_NOT, STARTS_WITH, ENDS_WITH)
            SmartField.Kind.NUMBER -> listOf(IS, IS_NOT, GT, LT, BETWEEN)
            SmartField.Kind.DATE -> listOf(IN_LAST, NOT_IN_LAST, NEVER)
            SmartField.Kind.BOOL -> listOf(TRUE, FALSE)
        }
    }
}

@Serializable
data class SmartRule(val field: SmartField, val op: SmartOp, val value: String = "", val value2: String = "")

@Serializable
enum class SmartSort(val label: String) {
    RANDOM("Random"), TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), YEAR("Year"), PLAY_COUNT("Play count"),
    RATING("Rating"), DATE_ADDED("Date added"), LAST_PLAYED("Last played"), DURATION("Duration"), BITRATE("Bitrate"),
}

@Serializable
data class SmartPlaylist(
    val id: String,
    val name: String,
    val matchAll: Boolean = true,
    val rules: List<SmartRule> = emptyList(),
    val sort: SmartSort = SmartSort.RANDOM,
    val descending: Boolean = false,
    val limit: Int? = 100,
    /** When set, the result is written to a regular server playlist so every client sees it. */
    val syncToServer: Boolean = false,
    val serverPlaylistId: String? = null,
)

@Serializable
data class SmartState(val playlists: List<SmartPlaylist> = SmartEngine.defaults, val updatedAt: Long = 0)

object SmartEngine {
    val defaults = listOf(
        SmartPlaylist("recent-added", "Recently added", rules = listOf(SmartRule(SmartField.DATE_ADDED, SmartOp.IN_LAST, "30")), sort = SmartSort.DATE_ADDED, descending = true, limit = 200),
        SmartPlaylist("most-played", "Most played", rules = listOf(SmartRule(SmartField.PLAY_COUNT, SmartOp.GT, "0")), sort = SmartSort.PLAY_COUNT, descending = true, limit = 100),
        SmartPlaylist("never-played", "Never played", rules = listOf(SmartRule(SmartField.PLAY_COUNT, SmartOp.IS, "0")), limit = 200),
        SmartPlaylist("top-rated", "Top rated", rules = listOf(SmartRule(SmartField.RATING, SmartOp.GT, "3")), sort = SmartSort.RATING, descending = true, limit = 200),
        SmartPlaylist("hi-res", "Hi-res lossless", rules = listOf(SmartRule(SmartField.HI_RES, SmartOp.TRUE), SmartRule(SmartField.LOSSLESS, SmartOp.TRUE)), limit = 300),
        SmartPlaylist(
            "forgotten", "Forgotten favourites",
            rules = listOf(SmartRule(SmartField.STARRED, SmartOp.TRUE), SmartRule(SmartField.LAST_PLAYED, SmartOp.NOT_IN_LAST, "90")), limit = 100,
        ),
    )

    fun evaluate(pl: SmartPlaylist, songs: List<Song>, nowMs: Long, seed: Long = nowMs / 3_600_000): List<Song> {
        val matching = songs.filter { s ->
            if (pl.rules.isEmpty()) true
            else if (pl.matchAll) pl.rules.all { matches(it, s, nowMs) }
            else pl.rules.any { matches(it, s, nowMs) }
        }
        val sorted = when (pl.sort) {
            SmartSort.RANDOM -> matching.shuffled(kotlin.random.Random(seed))
            SmartSort.TITLE -> matching.sortedBy { it.title.lowercase() }
            SmartSort.ARTIST -> matching.sortedWith(compareBy({ it.artist?.lowercase() }, { it.album?.lowercase() }, { it.discNumber }, { it.track }))
            SmartSort.ALBUM -> matching.sortedWith(compareBy({ it.album?.lowercase() }, { it.discNumber }, { it.track }))
            SmartSort.YEAR -> matching.sortedBy { it.year ?: 0 }
            SmartSort.PLAY_COUNT -> matching.sortedBy { it.playCount ?: 0 }
            SmartSort.RATING -> matching.sortedBy { it.userRating ?: 0 }
            SmartSort.DATE_ADDED -> matching.sortedBy { it.created ?: "" }
            SmartSort.LAST_PLAYED -> matching.sortedBy { it.played ?: "" }
            SmartSort.DURATION -> matching.sortedBy { it.duration ?: 0 }
            SmartSort.BITRATE -> matching.sortedBy { it.bitRate ?: 0 }
        }
        val ordered = if (pl.descending && pl.sort != SmartSort.RANDOM) sorted.reversed() else sorted
        return pl.limit?.let { ordered.take(it) } ?: ordered
    }

    private fun text(f: SmartField, s: Song): String = when (f) {
        SmartField.TITLE -> s.title
        SmartField.ARTIST -> (listOfNotNull(s.artist) + s.artists.map { it.name }).joinToString(" / ")
        SmartField.ALBUM -> s.album ?: ""
        SmartField.ALBUM_ARTIST -> s.displayAlbumArtist ?: s.albumArtists.joinToString(" / ") { it.name }
        SmartField.GENRE -> (listOfNotNull(s.genre) + s.genres.map { it.name }).joinToString(" / ")
        SmartField.PATH -> s.path ?: ""
        SmartField.FORMAT -> s.suffix ?: ""
        SmartField.COMMENT -> s.comment ?: ""
        else -> ""
    }

    private fun number(f: SmartField, s: Song): Double? = when (f) {
        SmartField.YEAR -> s.year?.toDouble()
        SmartField.RATING -> (s.userRating ?: 0).toDouble()
        SmartField.PLAY_COUNT -> (s.playCount ?: 0).toDouble()
        SmartField.DURATION -> s.duration?.toDouble()
        SmartField.BITRATE -> s.bitRate?.toDouble()
        SmartField.SAMPLE_RATE -> s.samplingRate?.toDouble()
        SmartField.BIT_DEPTH -> s.bitDepth?.toDouble()
        SmartField.BPM -> s.bpm?.toDouble()
        SmartField.TRACK -> s.track?.toDouble()
        SmartField.DISC -> s.discNumber?.toDouble()
        else -> null
    }

    private fun date(f: SmartField, s: Song): Long? = when (f) {
        SmartField.DATE_ADDED -> s.created?.let(::parseIsoMillis)
        SmartField.LAST_PLAYED -> s.played?.let(::parseIsoMillis)
        SmartField.STARRED_AT -> s.starred?.let(::parseIsoMillis)
        else -> null
    }

    fun matches(r: SmartRule, s: Song, nowMs: Long): Boolean = when (r.field.kind) {
        SmartField.Kind.TEXT -> {
            val t = text(r.field, s).lowercase()
            val v = r.value.lowercase().trim()
            when (r.op) {
                SmartOp.CONTAINS -> t.contains(v)
                SmartOp.NOT_CONTAINS -> !t.contains(v)
                SmartOp.IS -> t.split(" / ").any { it == v } || t == v
                SmartOp.IS_NOT -> t.split(" / ").none { it == v } && t != v
                SmartOp.STARTS_WITH -> t.startsWith(v)
                SmartOp.ENDS_WITH -> t.endsWith(v)
                else -> false
            }
        }
        SmartField.Kind.NUMBER -> {
            val n = number(r.field, s)
            val v = r.value.toDoubleOrNull()
            val v2 = r.value2.toDoubleOrNull()
            if (n == null || v == null) r.op == SmartOp.IS_NOT
            else when (r.op) {
                SmartOp.IS -> n == v
                SmartOp.IS_NOT -> n != v
                SmartOp.GT -> n > v
                SmartOp.LT -> n < v
                SmartOp.BETWEEN -> v2 != null && n >= minOf(v, v2) && n <= maxOf(v, v2)
                else -> false
            }
        }
        SmartField.Kind.DATE -> {
            val d = date(r.field, s)
            val days = r.value.toDoubleOrNull() ?: 0.0
            val cutoff = nowMs - (days * 86_400_000).toLong()
            when (r.op) {
                SmartOp.IN_LAST -> d != null && d >= cutoff
                SmartOp.NOT_IN_LAST -> d == null || d < cutoff
                SmartOp.NEVER -> d == null
                else -> false
            }
        }
        SmartField.Kind.BOOL -> {
            val b = when (r.field) {
                SmartField.STARRED -> s.isStarred
                SmartField.LOSSLESS -> s.isLossless
                SmartField.HI_RES -> s.isHiRes
                else -> false
            }
            if (r.op == SmartOp.TRUE) b else !b
        }
    }
}

/** Parses the ISO 8601 timestamps Subsonic returns, for example 2024-03-01T10:22:33.123Z. */
fun parseIsoMillis(s: String): Long? {
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2}))?(?:\.(\d+))?)?(Z|[+-]\d{2}:?\d{2})?""").find(s) ?: return null
    val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    val h = m.groupValues[4].toIntOrNull() ?: 0
    val mi = m.groupValues[5].toIntOrNull() ?: 0
    val se = m.groupValues[6].toIntOrNull() ?: 0
    val ms = m.groupValues[7].take(3).padEnd(3, '0').toIntOrNull() ?: 0
    val days = daysFromCivil(y, mo, d)
    var millis = ((days * 24 + h) * 60 + mi) * 60_000L + se * 1000L + ms
    val tz = m.groupValues[8]
    if (tz.isNotEmpty() && tz != "Z") {
        val sign = if (tz[0] == '-') -1 else 1
        val digits = tz.drop(1).replace(":", "")
        val off = (digits.take(2).toInt() * 60 + digits.drop(2).toInt()) * 60_000L
        millis -= sign * off
    }
    return millis
}

private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097L + doe - 719468
}
