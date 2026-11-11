package com.radiogolha

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.media3.session.R as R3
import com.google.common.util.concurrent.ListenableFuture

/** What the screen shows about the song on air. */
data class NowPlaying(val title: String, val singers: String?, val dastgah: String?, val program: String?, val page: String?)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // What the screen shows.
    private var canReadFiles by mutableStateOf(false)
    private var songCount by mutableStateOf<Int?>(null)
    private var playing by mutableStateOf(false)
    private var nowPlaying by mutableStateOf<NowPlaying?>(null)
    private var stations by mutableStateOf(listOf(Station.ALL))
    private var songsPerStation by mutableStateOf(emptyMap<Station, Int>())
    private var station by mutableStateOf(Station.ALL)

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canReadFiles = granted
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
                    canReadFiles = canReadFiles,
                    songCount = songCount,
                    playing = playing,
                    nowPlaying = nowPlaying,
                    stations = stations,
                    songsPerStation = songsPerStation,
                    station = station,
                    onAllowFiles = { askPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                    onPlayStop = { if (playing) controller?.pause() else playRadio() },
                    onNext = { controller?.seekToNext() },
                    onShuffle = { Station.shuffle(songsPerStation, station)?.let { playRadio(it) } },
                    onStation = { playRadio(it) },
                    onOpenPage = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
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
        canReadFiles = SongLibrary.canReadFiles(this)
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
        val songs = SongLibrary.load(this)
        songCount = songs.size
        stations = Station.listFor(songs.map { it.tags })
        songsPerStation = stations.associateWith { s -> songs.count { s.matches(it.tags) } }
        // A saved station that no longer exists (e.g. a name from an older version) shows as "all".
        station = PlaybackService.savedStation(this).takeIf { it in stations } ?: Station.ALL
    }

    private fun showState(player: Player) {
        playing = player.playWhenReady
        val meta = player.mediaMetadata
        nowPlaying = meta.title?.let {
            NowPlaying(
                title = it.toString(),
                singers = meta.artist?.toString(),
                dastgah = meta.genre?.toString(),
                program = meta.albumTitle?.toString(),
                page = meta.extras?.getString(EXTRA_PAGE),
            )
        }
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
    MaterialTheme(colorScheme = colors) {
        // The app is in Persian, so it reads right to left whatever the phone's language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

@Composable
private fun RadioScreen(
    canReadFiles: Boolean,
    songCount: Int?,
    playing: Boolean,
    nowPlaying: NowPlaying?,
    stations: List<Station>,
    songsPerStation: Map<Station, Int>,
    station: Station,
    onAllowFiles: () -> Unit,
    onPlayStop: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onStation: (Station) -> Unit,
    onOpenPage: (String) -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Text("رادیو گلها", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("برنامه‌های گلها، یکی پس از دیگری", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(28.dp))

            OnAirCard(playing, nowPlaying, station, songCount, onOpenPage)
            Spacer(Modifier.height(28.dp))

            Controls(playing, canPlay = songCount != 0, canSkip = nowPlaying != null, onPlayStop, onNext, onShuffle)
            Spacer(Modifier.height(32.dp))

            StationPicker(stations, songsPerStation, station, onStation)
            Spacer(Modifier.height(24.dp))

            if (!canReadFiles) {
                TextButton(onClick = onAllowFiles) { Text("آهنگ‌های ذخیره‌شده در گوشی را هم پخش کن") }
            } else {
                Text(
                    "آهنگ‌های پوشه‌ی \u2066${SongLibrary.FOLDER}\u2069 هم پخش می‌شوند", // keeps the path left to right
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "برنامه‌ها از golhaprogram.com پخش می‌شوند",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The song on air: an "On air" light, the title, singers, dastgah and program, and a link to its page. */
@Composable
private fun OnAirCard(playing: Boolean, now: NowPlaying?, station: Station, songCount: Int?, onOpenPage: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OnAirLight(playing)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (playing) "روی آنتن" else "خاموش",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    station.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            when {
                now != null -> {
                    Text(now.title, style = MaterialTheme.typography.headlineSmall)
                    now.singers?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    val details = listOfNotNull(now.dastgah, now.program.takeIf { it != null && !now.title.startsWith(it) })
                    if (details.isNotEmpty()) {
                        Text(
                            details.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    now.page?.let { page ->
                        TextButton(
                            onClick = { onOpenPage(page) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("شعرها و نوازندگان در golhaprogram.com")
                        }
                    }
                }
                songCount == 0 -> Text("برنامه‌ای برای پخش نیست.", style = MaterialTheme.typography.titleMedium)
                else -> Text(
                    "${songCount?.persianDigits() ?: "…"} برنامه آماده‌ی پخش است.\nبرای شنیدن، دکمه‌ی پخش را بزنید.",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** A small red light that glows while on air, like a studio's sign. */
@Composable
private fun OnAirLight(on: Boolean) {
    val glow = if (on) {
        rememberInfiniteTransition(label = "on air").animateFloat(
            initialValue = 1f, targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glow",
        ).value
    } else 1f
    val color = if (on) Color(0xFFE53935) else MaterialTheme.colorScheme.outline
    Box(Modifier.size(10.dp).alpha(glow).background(color, CircleShape))
}

/**
 * Shuffle (tune to a random station), play/stop, next (a random programme of this station). The screen reads right
 * to left, so shuffle is on the right and next on the left, both pointing left.
 */
@Composable
private fun Controls(
    playing: Boolean,
    canPlay: Boolean,
    canSkip: Boolean,
    onPlayStop: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        FilledTonalIconButton(
            onClick = onShuffle,
            enabled = canPlay,
            modifier = Modifier.size(56.dp).semantics { contentDescription = "یک ایستگاه تصادفی" },
        ) {
            MediaIcon(R3.drawable.media3_icon_shuffle_on, 26.dp, mirrored = true)
        }
        FilledIconButton(
            onClick = onPlayStop,
            enabled = canPlay,
            modifier = Modifier.size(88.dp).semantics { contentDescription = if (playing) "توقف" else "پخش" },
        ) {
            MediaIcon(if (playing) R3.drawable.media3_icon_stop else R3.drawable.media3_icon_play, 44.dp)
        }
        FilledTonalIconButton(
            onClick = onNext,
            enabled = canSkip,
            modifier = Modifier.size(56.dp).semantics { contentDescription = "بعدی" },
        ) {
            MediaIcon(R3.drawable.media3_icon_next, 28.dp, mirrored = true)
        }
    }
}

/**
 * Media3's player icons (Google's Material Symbols, the same ones on the lock screen and in the notification),
 * in the button's colour. [mirrored] flips them to point left, the way the screen reads.
 */
@Composable
private fun MediaIcon(icon: Int, size: Dp, mirrored: Boolean = false) {
    Icon(
        painterResource(icon),
        contentDescription = null, // the button describes itself
        modifier = Modifier.size(size).graphicsLayer(scaleX = if (mirrored) -1f else 1f),
    )
}

/**
 * Two dropdowns: what to listen by (all songs, a singer, a dastgah or a program), then which one. Picking one
 * starts that station; each choice shows how many songs it has.
 */
@Composable
private fun StationPicker(
    stations: List<Station>,
    songsPerStation: Map<Station, Int>,
    selected: Station,
    onStation: (Station) -> Unit,
) {
    var kind by remember(selected) { mutableStateOf(selected.kind) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Dropdown(
            label = "پخش بر اساس",
            value = kind.label,
            options = stations.map { it.kind }.distinct(),
            optionText = { it.label },
            optionCount = { k -> if (k == Station.Kind.ALL) songsPerStation[Station.ALL] else stations.count { it.kind == k } },
            onPick = { k ->
                kind = k
                if (k == Station.Kind.ALL) onStation(Station.ALL)
            },
        )
        if (kind != Station.Kind.ALL) {
            Dropdown(
                label = kind.label,
                value = selected.takeIf { it.kind == kind }?.name ?: "یک ${kind.label} انتخاب کنید",
                options = stations.filter { it.kind == kind },
                optionText = { it.name },
                optionCount = { songsPerStation[it] },
                onPick = onStation,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    label: String,
    value: String,
    options: List<T>,
    optionText: (T) -> String,
    optionCount: (T) -> Int?,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    trailingIcon = {
                        optionCount(option)?.let {
                            Text(it.persianDigits(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
                        }
                    },
                    onClick = {
                        open = false
                        onPick(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
