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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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

/** The on-air card's status line. */
enum class OnAir(val label: String) {
    OFF("خاموش"),
    ON("روی آنتن"),
    CONNECTING("در حال اتصال…"),
    NO_CONNECTION("بدون اتصال"),
    FAILED("پخش نشد"),
}

/** What the screen shows about the song on air. */
data class NowPlaying(val title: String, val singers: String?, val dastgah: String?, val program: String?, val page: String?)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    // What the screen shows.
    private var canReadFiles by mutableStateOf(false)
    private var songCount by mutableStateOf<Int?>(null)
    private var playing by mutableStateOf(false)
    private var onAir by mutableStateOf(OnAir.OFF)
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
                    onAir = onAir,
                    nowPlaying = nowPlaying,
                    stations = stations,
                    songsPerStation = songsPerStation,
                    station = station,
                    onAllowFiles = { askPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                    onPlayStop = { if (playing) controller?.pause() else playRadio() },
                    onRetry = { playRadio() },
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
        val error = player.playerError
        onAir = when {
            error != null -> if (PlaybackService.isConnectionError(error)) OnAir.NO_CONNECTION else OnAir.FAILED
            player.isPlaying -> OnAir.ON
            player.playWhenReady && player.playbackState == Player.STATE_BUFFERING -> OnAir.CONNECTING
            else -> OnAir.OFF
        }
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

/** Vazirmatn (SIL Open Font License, licenses/Vazirmatn-OFL.txt): a Persian typeface made for screens. */
private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/** Material's type scale, every style set in Vazirmatn. */
private val VazirmatnTypography = Typography().run {
    fun TextStyle.v() = copy(fontFamily = Vazirmatn)
    Typography(
        displayLarge = displayLarge.v(), displayMedium = displayMedium.v(), displaySmall = displaySmall.v(),
        headlineLarge = headlineLarge.v(), headlineMedium = headlineMedium.v(), headlineSmall = headlineSmall.v(),
        titleLarge = titleLarge.v(), titleMedium = titleMedium.v(), titleSmall = titleSmall.v(),
        bodyLarge = bodyLarge.v(), bodyMedium = bodyMedium.v(), bodySmall = bodySmall.v(),
        labelLarge = labelLarge.v(), labelMedium = labelMedium.v(), labelSmall = labelSmall.v(),
    )
}

// The icon's colours (scripts/make_icon.py): lapis, gold and cream. By day the app is cream paper with lapis ink, like
// the icon; at night it is deep lapis with gold, like the icon's other palette.
private val GolhaLight = lightColorScheme(
    primary = Color(0xFF1E2B5E), onPrimary = Color(0xFFF4ECE0),
    primaryContainer = Color(0xFFD9DEF2), onPrimaryContainer = Color(0xFF1E2B5E),
    secondary = Color(0xFF7A5A12), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBCB86), onSecondaryContainer = Color(0xFF1E2B5E),
    tertiary = Color(0xFF7A5A12), onTertiary = Color.White,
    background = Color(0xFFF4ECE0), onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFF4ECE0), onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFE6DCCB), onSurfaceVariant = Color(0xFF4E4639),
    surfaceContainerLowest = Color(0xFFFFFBF5), surfaceContainerLow = Color(0xFFF8F1E6),
    surfaceContainer = Color(0xFFF0E7D8), surfaceContainerHigh = Color(0xFFEAE0CF),
    surfaceContainerHighest = Color(0xFFE4D9C6),
    outline = Color(0xFF7F7667), outlineVariant = Color(0xFFD1C5B4),
)
private val GolhaDark = darkColorScheme(
    primary = Color(0xFFE9B949), onPrimary = Color(0xFF1E2B5E),
    primaryContainer = Color(0xFF2F4183), onPrimaryContainer = Color(0xFFF4ECE0),
    secondary = Color(0xFFEBCB86), onSecondary = Color(0xFF1E2B5E),
    secondaryContainer = Color(0xFF2F4183), onSecondaryContainer = Color(0xFFF4ECE0),
    tertiary = Color(0xFFEBCB86), onTertiary = Color(0xFF1E2B5E),
    background = Color(0xFF111733), onBackground = Color(0xFFECE6DA),
    surface = Color(0xFF111733), onSurface = Color(0xFFECE6DA),
    surfaceVariant = Color(0xFF2A3360), onSurfaceVariant = Color(0xFFC8C3D8),
    surfaceContainerLowest = Color(0xFF0B1028), surfaceContainerLow = Color(0xFF161D3D),
    surfaceContainer = Color(0xFF1A2247), surfaceContainerHigh = Color(0xFF1F2952),
    surfaceContainerHighest = Color(0xFF26305C),
    outline = Color(0xFF8E8AA6), outlineVariant = Color(0xFF3B4370),
)

@Composable
private fun RadioTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) GolhaDark else GolhaLight
    MaterialTheme(colorScheme = colors, typography = VazirmatnTypography) {
        // The app is in Persian, so it reads right to left whatever the phone's language is.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}

@Composable
private fun RadioScreen(
    canReadFiles: Boolean,
    songCount: Int?,
    playing: Boolean,
    onAir: OnAir,
    nowPlaying: NowPlaying?,
    stations: List<Station>,
    songsPerStation: Map<Station, Int>,
    station: Station,
    onAllowFiles: () -> Unit,
    onPlayStop: () -> Unit,
    onRetry: () -> Unit,
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

            ProblemBanner(onAir, onRetry)
            OnAirCard(onAir, nowPlaying, station, songCount, onOpenPage)
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

/** The song on air: an "On air" light and status, the title, singers, dastgah and program, and a link to its page. */
@Composable
private fun OnAirCard(onAir: OnAir, now: NowPlaying?, station: Station, songCount: Int?, onOpenPage: (String) -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OnAirLight(onAir == OnAir.ON)
                Spacer(Modifier.width(8.dp))
                Text(
                    onAir.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (onAir == OnAir.NO_CONNECTION || onAir == OnAir.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * A small banner above the card when the radio can't play: no connection (no internet, or the site can't be
 * reached), or programmes that failed. "Try again" plays the programme on air again, from where it stopped.
 */
@Composable
private fun ProblemBanner(onAir: OnAir, onRetry: () -> Unit) {
    val message = when (onAir) {
        OnAir.NO_CONNECTION -> "اتصال برقرار نشد."
        OnAir.FAILED -> "برنامه‌های این ایستگاه پخش نشدند."
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onRetry, modifier = Modifier.semantics { contentDescription = "دوباره" }) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
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
