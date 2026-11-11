package com.radiogolha

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // What the screen shows.
    private var hasPermission by mutableStateOf(false)
    private var songCount by mutableStateOf<Int?>(null)
    private var playing by mutableStateOf(false)
    private var nowPlaying by mutableStateOf<String?>(null)
    private var nowSinger by mutableStateOf<String?>(null)
    private var stations by mutableStateOf(listOf(Station.ALL))
    private var station by mutableStateOf(Station.ALL)

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        refreshLibrary()
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = showState(player)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RadioTheme {
                RadioScreen(
                    hasPermission = hasPermission,
                    songCount = songCount,
                    playing = playing,
                    nowPlaying = nowPlaying,
                    nowSinger = nowSinger,
                    stations = stations,
                    station = station,
                    onAllow = { askPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                    onPlayStop = { if (playing) controller?.pause() else playRadio() },
                    onNext = { controller?.seekToNext() },
                    onStation = { playRadio(it) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            controller = future.get().also {
                it.addListener(playerListener)
                showState(it)
            }
        }, mainExecutor)
    }

    override fun onResume() {
        super.onResume()
        hasPermission = checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        refreshLibrary()
    }

    override fun onStop() {
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun refreshLibrary() {
        val songs = if (hasPermission) SongLibrary.load(this) else null
        songCount = songs?.size
        stations = Station.listFor(songs.orEmpty().map { it.tags })
        station = PlaybackService.savedStation(this)
    }

    private fun showState(player: Player) {
        playing = player.playWhenReady
        nowPlaying = player.mediaMetadata.title?.toString()
        nowSinger = player.mediaMetadata.artist?.toString()
    }

    /**
     * The playback service owns the running order; ask it to start (or carry on from where it stopped).
     * With a different [newStation], it starts that station instead.
     */
    private fun playRadio(newStation: Station = station) {
        val args = Bundle().apply { putString(PlaybackService.ARG_STATION, newStation.key) }
        val sent = controller?.sendCustomCommand(SessionCommand(PlaybackService.START_RADIO, Bundle.EMPTY), args)
        sent?.addListener({ refreshLibrary() }, mainExecutor)
    }
}

@Composable
private fun RadioTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun RadioScreen(
    hasPermission: Boolean,
    songCount: Int?,
    playing: Boolean,
    nowPlaying: String?,
    nowSinger: String?,
    stations: List<Station>,
    station: Station,
    onAllow: () -> Unit,
    onPlayStop: () -> Unit,
    onNext: () -> Unit,
    onStation: (Station) -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Text("Radio Golha", style = MaterialTheme.typography.headlineLarge)
            Text(station.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))

            val status = when {
                !hasPermission -> "Radio Golha needs access to the music on this phone."
                songCount == 0 -> "No songs found. Put some in ${SongLibrary.FOLDER} on the phone."
                nowPlaying != null -> listOfNotNull(nowPlaying, nowSinger).joinToString("\n")
                songCount != null -> "$songCount songs ready"
                else -> ""
            }
            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))

            if (!hasPermission) {
                Button(onClick = onAllow) { Text("Allow access") }
            } else {
                Button(
                    onClick = onPlayStop,
                    enabled = songCount != 0,
                    shape = CircleShape,
                    modifier = Modifier.size(140.dp),
                ) {
                    Text(if (playing) "Stop" else "Play", fontSize = 26.sp)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onNext, enabled = nowPlaying != null) { Text("Next") }
                if (stations.size > 1) {
                    Spacer(Modifier.height(32.dp))
                    StationList(stations, station, onStation)
                }
            }
        }
    }
}

/** The stations, grouped under headings (singers, dastgah, programs). Tapping one plays it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationList(stations: List<Station>, selected: Station, onStation: (Station) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        stations.groupBy { it.kind }.forEach { (kind, group) ->
            if (kind.heading.isNotEmpty()) {
                Text(
                    kind.heading,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { s ->
                    FilterChip(selected = s == selected, onClick = { onStation(s) }, label = { Text(s.name) })
                }
            }
        }
    }
}
