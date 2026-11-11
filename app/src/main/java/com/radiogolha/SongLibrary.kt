package com.radiogolha

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray

/**
 * A song on the phone or a stream. For a file, [id] is its folder and file name (e.g. "Music/Golha/Test 01.m4a"):
 * it stays the same when Android re-indexes the files, unlike the media library's own number, so a saved queue
 * still matches. For a stream, [id] is its URL.
 */
data class Song(val id: String, val uri: Uri, val title: String, val tags: Tags)

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(tags.singer)
            .setAlbumTitle(tags.program)
            .setGenre(tags.dastgah)
            .build()
    )
    .build()

/**
 * The radio's songs: files in Music/Golha/ (and its subfolders), found through Android's media library, plus the
 * openly licensed streams listed in assets/streams.json (credits in CREDITS.md).
 */
object SongLibrary {
    const val FOLDER = "Music/Golha/"

    fun load(context: Context): List<Song> = files(context) + streams(context)

    /** Streams play only with an internet connection; they're never saved to the phone. */
    private fun streams(context: Context): List<Song> {
        val list = JSONArray(context.assets.open("streams.json").bufferedReader().use { it.readText() })
        return (0 until list.length()).map { i ->
            val s = list.getJSONObject(i)
            val url = s.getString("url")
            fun field(name: String) = s.optString(name).takeIf { !s.isNull(name) && it.isNotBlank() }
            Song(url, Uri.parse(url), s.getString("title"), Tags(field("singer"), field("dastgah"), field("program")))
        }
    }

    private fun files(context: Context): List<Song> {
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
