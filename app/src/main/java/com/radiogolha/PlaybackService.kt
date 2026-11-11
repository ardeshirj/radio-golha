package com.radiogolha

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Owns the player and the radio's running order. Running as a media service is what keeps music going with the
 * screen off, and gives the notification, lock-screen controls and Bluetooth/headphone buttons.
 *
 * The player only ever holds the current song and the rest of its round; when the last one starts, the next
 * round is added. That list, the position in the current song and the station are saved, so a restart carries on.
 * Rounds are dealt only from the songs that match the station.
 */
class PlaybackService : MediaSessionService() {
    companion object {
        /** Sent by the screen: load the radio if needed, then play. [ARG_STATION] switches station first. */
        const val START_RADIO = "com.radiogolha.START_RADIO"
        const val ARG_STATION = "station"
        private const val PREFS = "radio"
        private const val KEY_QUEUE = "queue"
        private const val KEY_POSITION = "position"
        private const val KEY_STATION = "station"

        fun savedStation(context: Context): Station =
            Station.fromKey(context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_STATION, null))
    }

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val queue = RadioQueue()
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        val music = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // Streams come from Wikimedia, whose rules ask apps to name themselves.
        val web = DefaultHttpDataSource.Factory().setUserAgent("RadioGolha/0.1 (personal Android app)")
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, web)))
            .setAudioAttributes(music, /* handleAudioFocus = */ true) // pause for calls and other apps
            .setHandleAudioBecomingNoisy(true)                        // pause when headphones come out
            .setWakeMode(C.WAKE_MODE_NETWORK)                         // keep CPU and Wi-Fi awake with the screen off
            .build()
        player.addListener(radioListener)
        session = MediaSession.Builder(this, player).setCallback(callback).build()
    }

    private val station: Station get() = savedStation(this)

    /** The songs on the current station. If the station has none left (tags changed), go back to all songs. */
    private fun stationSongs(): List<Song> {
        val library = SongLibrary.load(this)
        val songs = library.filter { station.matches(it.tags) }
        if (songs.isEmpty() && station != Station.ALL) {
            prefs.edit().putString(KEY_STATION, Station.ALL.key).apply()
            return library
        }
        return songs
    }

    /** Switching station starts a fresh round from that station's songs, instead of finishing the old one. */
    private fun switchTo(newStation: Station) {
        if (newStation == station) return
        prefs.edit().putString(KEY_STATION, newStation.key).remove(KEY_QUEUE).remove(KEY_POSITION).commit()
        player.clearMediaItems()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /** Swiping the app away stops the service, unless music is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        saveState()
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveState()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    /** Loads the saved queue (or a fresh round) if the player is empty, then plays. False if there are no songs. */
    private fun startRadio(): Boolean {
        if (player.mediaItemCount == 0) {
            val library = stationSongs()
            if (library.isEmpty()) return false
            val byId = library.associateBy { it.id }
            val saved = prefs.getString(KEY_QUEUE, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
            val ids = queue.restore(saved, library.map { it.id })
            // Resume mid-song only if the song we stopped in is still first in line.
            val position = if (ids.first() == saved.firstOrNull()) prefs.getLong(KEY_POSITION, 0) else 0
            player.setMediaItems(ids.map { byId.getValue(it).toMediaItem() }, 0, position)
            player.prepare()
            addRoundIfOnLastSong()
        }
        if (player.playerError != null) player.prepare() // try again after a failure
        player.play()
        return true
    }

    /** When the last song of a round starts, deal the next round behind it, so "next" always works. */
    private fun addRoundIfOnLastSong() {
        if (player.mediaItemCount == 0 || player.currentMediaItemIndex < player.mediaItemCount - 1) return
        val library = stationSongs()
        if (library.isEmpty()) return
        val byId = library.associateBy { it.id }
        val current = player.currentMediaItem?.mediaId
        player.addMediaItems(queue.newRound(library.map { it.id }, lastPlayed = current).map { byId.getValue(it).toMediaItem() })
    }

    /** Saves the songs still to play (current first) and the position in the current one. */
    private fun saveState() {
        if (player.mediaItemCount == 0) return
        val ids = (player.currentMediaItemIndex until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        prefs.edit()
            .putString(KEY_QUEUE, ids.joinToString("\n"))
            .putLong(KEY_POSITION, player.currentPosition)
            .apply()
    }

    /** Songs that failed in a row (e.g. a stream with no internet); reset once something plays. */
    private var failuresInARow = 0

    private val radioListener = object : Player.Listener {
        /** A song that can't play is skipped; after a whole round of failures, stop instead of spinning. */
        override fun onPlayerError(error: PlaybackException) {
            failuresInARow++
            if (failuresInARow >= stationSongs().size.coerceAtLeast(1) || !player.hasNextMediaItem()) return
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Forget songs already played; the queue is always "current song, then what's next".
            val index = player.currentMediaItemIndex
            if (index > 0) player.removeMediaItems(0, index)
            addRoundIfOnLastSong()
            saveState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) failuresInARow = 0
            if (!isPlaying) saveState()
        }
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(START_RADIO, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != START_RADIO) return super.onCustomCommand(session, controller, customCommand, args)
            args.getString(ARG_STATION)?.let { switchTo(Station.fromKey(it)) }
            val result = if (startRadio()) SessionResult.RESULT_SUCCESS else SessionResult.RESULT_ERROR_BAD_VALUE
            return Futures.immediateFuture(SessionResult(result))
        }
    }
}
