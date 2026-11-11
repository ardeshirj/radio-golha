package com.radiogolha

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray

/**
 * A song on the phone or a stream. For a file, [id] is its folder and file name (e.g. "Music/Golha/Test 01.m4a"):
 * it stays the same when Android re-indexes the files, unlike the media library's own number, so a saved queue
 * still matches. For a stream, [id] is its URL, and [page] is its page on the web, if it has one.
 */
data class Song(val id: String, val uri: Uri, val title: String, val tags: Tags, val page: String? = null)

/** Where the screen finds a song's web page, in its media item's extras. */
const val EXTRA_PAGE = "page"

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(tags.singers.joinToString(" و ").ifEmpty { null })
            .setAlbumTitle(tags.program)
            .setGenre(tags.dastgahs.joinToString("، ").ifEmpty { null })
            .setExtras(Bundle().apply { putString(EXTRA_PAGE, page) })
            .build()
    )
    .build()

/**
 * The radio's songs: files in Music/Golha/ (and its subfolders), found through Android's media library, plus the
 * the Golha programmes streamed from golhaprogram.com, listed in assets/golhaprogram.json (made by
 * scripts/make_golha_streams.py). Credits in CREDITS.md.
 */
object SongLibrary {
    const val FOLDER = "Music/Golha/"

    fun canReadFiles(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun load(context: Context): List<Song> = files(context) + streams(context, "golhaprogram.json")

    /**
     * Streams play only with an internet connection; they're never saved to the phone. In the list, "singer" and
     * "dastgah" are one name or a list of names.
     */
    private fun streams(context: Context, asset: String): List<Song> {
        val list = JSONArray(context.assets.open(asset).bufferedReader().use { it.readText() })
        return (0 until list.length()).map { i ->
            val s = list.getJSONObject(i)
            val url = s.getString("url")
            fun names(name: String): List<String> = when (val value = s.opt(name)) {
                is JSONArray -> (0 until value.length()).map { value.getString(it) }
                is String -> listOf(value)
                else -> emptyList()
            }.map { it.trim() }.filter { it.isNotEmpty() }
            fun text(name: String) = s.optString(name).takeIf { !s.isNull(name) && it.isNotBlank() }
            Song(url, Uri.parse(url), s.getString("title"), Tags(names("singer"), names("dastgah"), text("program")), text("page"))
        }
    }

    /** Songs on the phone; none until the app is allowed to read them (streams don't need that). */
    private fun files(context: Context): List<Song> {
        if (!canReadFiles(context)) return emptyList()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.GENRE,
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val songs = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, arrayOf("$FOLDER%"), null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val genreCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                val folder = cursor.getString(pathCol).orEmpty()
                val id = folder + cursor.getString(nameCol).orEmpty()
                // Android fills in "<unknown>" for a missing artist and the folder's name for a missing album.
                val folderName = folder.trimEnd('/').substringAfterLast('/')
                val tags = Tags(
                    singer = cursor.getString(artistCol).tag(),
                    dastgah = cursor.getString(genreCol).tag(),
                    program = cursor.getString(albumCol).tag()?.takeIf { it != folderName },
                )
                songs += Song(id, uri, cursor.getString(titleCol) ?: "Unknown", tags)
            }
        }
        return songs
    }
}

/** A tag's value, or null if it's empty or Android's placeholder for "missing". */
private fun String?.tag(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
