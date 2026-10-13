package com.westtiwan.tarang.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.data.RepeatMode
import com.westtiwan.tarang.data.formatKhz
import com.westtiwan.tarang.player.AudioInfo
import com.westtiwan.tarang.player.PlayerState
import com.westtiwan.tarang.ui.CoverArt
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.LocalNav
import com.westtiwan.tarang.ui.NowPlayingPage
import com.westtiwan.tarang.ui.Screen
import com.westtiwan.tarang.ui.SongMenu
import com.westtiwan.tarang.ui.formatDuration

@Composable
fun MiniPlayer() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val st by app.player.state.collectAsState()
    val song = st.current ?: return
    val progress = if (st.durationMs > 0) (st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Column(
            Modifier.clickable { nav.nowPlayingPage = NowPlayingPage.ART; nav.nowPlayingOpen = true }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(onDragEnd = {
                        if (total < -120) app.player.next() else if (total > 120) app.player.previous()
                        total = 0f
                    }) { _, d -> total += d }
                },
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverArt(song.coverArt, Modifier.size(48.dp), size = 120, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(song.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ app.toggleStar(song) }) {
                    Icon(if (song.isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (song.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PlayPauseButton(st.playing, small = true) { app.player.toggle() }
                IconButton({ app.player.next() }, enabled = st.hasNext) { Icon(Icons.Rounded.SkipNext, null) }
            }
            WavyProgress(progress, st.playing, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
        }
    }
}

/** Squiggly while playing, flat while paused, like Android's own media controls. */
@Composable
fun WavyProgress(progress: Float, playing: Boolean, modifier: Modifier = Modifier) {
    if (playing) LinearWavyProgressIndicator(progress = { progress }, modifier = modifier)
    else LinearProgressIndicator(progress = { progress }, modifier = modifier)
}

/** Play button that morphs from a circle to a rounded square while playing. */
@Composable
fun PlayPauseButton(playing: Boolean, small: Boolean = false, onClick: () -> Unit) {
    val corner by animateIntAsState(if (playing) 30 else 50, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
    val width by animateDpAsState(if (small) (if (playing) 52.dp else 44.dp) else (if (playing) 104.dp else 84.dp), MaterialTheme.motionScheme.fastSpatialSpec(), label = "width")
    FilledIconButton(
        onClick,
        Modifier.size(width = width, height = if (small) 44.dp else 84.dp),
        shape = RoundedCornerShape(percent = corner),
    ) {
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play", Modifier.size(if (small) 26.dp else 44.dp))
    }
}

@Composable
fun NowPlaying() {
    val app = LocalApp.current
    val settings by app.settings.collectAsState()
    val st by app.player.state.collectAsState()
    val seed = if (settings.albumArtColors) com.westtiwan.tarang.ui.rememberSeedColor(st.current?.coverArt) else null
    com.westtiwan.tarang.ui.TarangTheme(settings.theme, settings.dynamicColor, settings.pureBlack, seed) {
        NowPlayingContent()
    }
}

@Composable
private fun NowPlayingContent() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val st by app.player.state.collectAsState()
    val song = st.current
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(onDragEnd = { if (total > 250) nav.nowPlayingOpen = false; total = 0f }) { _, d -> total += d }
        },
    ) {
        if (song == null) {
            nav.nowPlayingOpen = false
            return@Box
        }
        // Blurred artwork backdrop.
        CoverArt(song.coverArt, Modifier.fillMaxSize().blur(80.dp), size = 300, shape = RoundedCornerShape(0.dp))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))),
            ),
        )
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth > 800.dp && maxWidth > maxHeight
            if (wide) {
                Row(Modifier.fillMaxSize().padding(24.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        TopRow(st)
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CoverArt(song.coverArt, Modifier.fillMaxHeight(0.9f).aspectRatio(1f), size = 1000, shape = RoundedCornerShape(32.dp))
                        }
                        Controls(st)
                    }
                    Spacer(Modifier.width(24.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        PageTabs()
                        Box(Modifier.weight(1f)) {
                            if (nav.nowPlayingPage == NowPlayingPage.QUEUE) QueuePanel(compact = false) else LyricsPanel(song)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    TopRow(st)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        AnimatedContent(nav.nowPlayingPage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "np") { page ->
                            when (page) {
                                NowPlayingPage.ART -> Box(
                                    Modifier.fillMaxSize().pointerInput(Unit) {
                                        var total = 0f
                                        detectHorizontalDragGestures(onDragEnd = {
                                            if (total < -150) app.player.next() else if (total > 150) app.player.previous()
                                            total = 0f
                                        }) { _, d -> total += d }
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val scale by animateFloatAsState(if (st.playing) 1f else 0.86f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "cover")
                                    CoverArt(
                                        song.coverArt,
                                        Modifier.widthIn(max = 520.dp).fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = scale; scaleY = scale },
                                        size = 1000, shape = RoundedCornerShape(32.dp),
                                    )
                                }
                                NowPlayingPage.LYRICS -> LyricsPanel(song)
                                NowPlayingPage.QUEUE -> QueuePanel(compact = false)
                            }
                        }
                    }
                    Controls(st)
                    BottomActions()
                }
            }
        }
    }
}

@Composable
private fun PageTabs() {
    val nav = LocalNav.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        androidx.compose.material3.FilterChip(nav.nowPlayingPage != NowPlayingPage.QUEUE, { nav.nowPlayingPage = NowPlayingPage.LYRICS }, label = { Text("Lyrics") })
        androidx.compose.material3.FilterChip(nav.nowPlayingPage == NowPlayingPage.QUEUE, { nav.nowPlayingPage = NowPlayingPage.QUEUE }, label = { Text("Queue") })
    }
}

fun describeAudio(a: AudioInfo): String = buildList {
    val fmt = listOfNotNull(
        a.codec?.uppercase(),
        if (a.bitDepth != null && a.sampleRate != null) "${a.bitDepth}-bit/${formatKhz(a.sampleRate)}kHz" else a.sampleRate?.let { "${formatKhz(it)}kHz" },
    ).joinToString(" ")
    if (fmt.isNotBlank()) add(fmt)
    a.output?.let { add(it) }
    if (a.bitPerfect) add("Bit-perfect")
}.joinToString(" • ")

@Composable
private fun TopRow(st: PlayerState) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ nav.nowPlayingOpen = false }) { Icon(Icons.Rounded.KeyboardArrowDown, "Close", Modifier.size(32.dp)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val info = describeAudio(st.audio)
            if (info.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (st.audio.bitPerfect) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { details = true },
                ) {
                    Text(info, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }
        Box {
            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
            st.current?.let { SongMenu(it, menu, { menu = false }) }
        }
    }
    if (details) AudioPipelineDialog(st.audio) { details = false }
}

@Composable
fun AudioPipelineDialog(a: AudioInfo, onDismiss: () -> Unit) {
    val desktop = com.westtiwan.tarang.ui.LocalPlatformUi.current.isDesktop
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Signal path") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How this song gets from the file to your ears.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PathStep("File", listOfNotNull(
                    a.codec?.uppercase(), a.bitDepth?.let { "$it-bit" }, a.sampleRate?.let { "${formatKhz(it)} kHz" },
                    a.channels?.let { if (it == 2) "stereo" else "$it channels" }, a.bitrate?.let { "${it / 1000} kbps" },
                ).joinToString(", ") + (a.source?.let { "\n$it" } ?: ""))
                a.pipeline.forEach { step ->
                    val (title, detail) = when {
                        step.startsWith("Decoder") -> "Decode" to step.substringAfter(": ").let { "$it turns the compressed file back into raw audio samples" }
                        step.startsWith("EQ:") -> "Equalizer" to step.substringAfter(": ")
                        step.startsWith("EQ bypassed") -> "Equalizer" to "Bypassed to keep the signal untouched"
                        step.startsWith("Replay gain") -> "Volume levelling" to step
                        step.startsWith("Java Sound") || step.startsWith("AudioTrack") -> "Hand off to the system" to step
                        step.startsWith("Bit-perfect") -> "Mixer" to "Dedicated bit-perfect mixer, nothing else mixed in"
                        step.startsWith("Android mixer") -> "Mixer" to "Android mixes this with other sounds and may resample it"
                        else -> "Step" to step
                    }
                    PathStep(title, detail)
                }
                if (desktop) PathStep("Mixer", "macOS Core Audio mixes this with other apps")
                a.output?.let { PathStep("Output", it + (a.outputRate?.let { r -> " at ${formatKhz(r)} kHz" } ?: "")) }
                val sameRate = a.outputRate != null && a.outputRate == a.sampleRate
                Text(
                    when {
                        a.bitPerfect -> "Bit-perfect: the DAC receives exactly the samples in the file."
                        sameRate && a.pipeline.none { it.startsWith("EQ:") || it.startsWith("Replay") } ->
                            "Not resampled or processed by Tarang, but the system mixer is still in the path, so it is not guaranteed bit-perfect."
                        else -> "Processed: the EQ, volume levelling or resampling changes the samples."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (a.bitPerfect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun PathStep(title: String, detail: String) {
    Row {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Controls(st: PlayerState) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val song = st.current ?: return
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = song.artistId != null) { song.artistId?.let { nav.push(Screen.ArtistDetail(it)) } },
                )
                if (song.album != null) Text(
                    song.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = song.albumId != null) { song.albumId?.let { nav.push(Screen.AlbumDetail(it)) } },
                )
            }
            IconButton({ app.toggleStar(song) }) {
                Icon(if (song.isStarred) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, Modifier.size(28.dp), tint = if (song.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(Modifier.padding(top = 2.dp)) {
            (1..5).forEach { i ->
                Icon(
                    if ((song.userRating ?: 0) >= i) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    "Rate $i",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = if ((song.userRating ?: 0) >= i) 1f else 0.5f),
                    modifier = Modifier.size(22.dp).clip(CircleShape).clickable { app.setRating(song, if (song.userRating == i) 0 else i) },
                )
            }
        }
        val duration = st.durationMs.takeIf { it > 0 } ?: song.durationMs
        val value = if (dragging) dragValue else if (duration > 0) (st.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            WavyProgress(value, st.playing && !dragging, Modifier.fillMaxWidth().padding(horizontal = 10.dp))
            Slider(
                value = value,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { app.player.seekTo((dragValue * duration).toLong()); dragging = false },
                colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(if (dragging) (dragValue * duration).toLong() else st.positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton({ app.player.setShuffle(!st.shuffle) }) {
                Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (st.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalIconButton({ app.player.previous() }, Modifier.size(64.dp), shape = RoundedCornerShape(22.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(32.dp)) }
            PlayPauseButton(st.playing) { app.player.toggle() }
            FilledTonalIconButton({ app.player.next() }, Modifier.size(64.dp), shape = RoundedCornerShape(22.dp), enabled = st.hasNext) { Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(32.dp)) }
            IconButton({
                app.player.setRepeat(when (st.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF })
            }) {
                Icon(if (st.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repeat", tint = if (st.repeat != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (st.error != null) Text(st.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BottomActions() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val st by app.player.state.collectAsState()
    var sleep by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) { HorizontalFloatingToolbar(expanded = true) {
        IconButton({ nav.nowPlayingPage = if (nav.nowPlayingPage == NowPlayingPage.LYRICS) NowPlayingPage.ART else NowPlayingPage.LYRICS }) {
            Icon(Icons.Rounded.Lyrics, "Lyrics", tint = if (nav.nowPlayingPage == NowPlayingPage.LYRICS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        IconButton({ nav.nowPlayingPage = if (nav.nowPlayingPage == NowPlayingPage.QUEUE) NowPlayingPage.ART else NowPlayingPage.QUEUE }) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", tint = if (nav.nowPlayingPage == NowPlayingPage.QUEUE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        IconButton({ nav.push(Screen.Equalizer) }) { Icon(Icons.Rounded.Equalizer, "Equalizer") }
        IconButton({ output = true }) { Icon(Icons.Rounded.Speaker, "Output") }
        IconButton({ speed = true }) { Icon(Icons.Rounded.Speed, "Speed", tint = if (st.speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
        IconButton({ sleep = true }) {
            Icon(Icons.Rounded.Bedtime, "Sleep timer", tint = if (app.coordinator?.sleepAtMs != null || app.coordinator?.sleepAfterCurrent == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    } }
    if (sleep) SleepDialog { sleep = false }
    if (speed) SpeedDialog { speed = false }
    if (output) OutputDialog { output = false }
}

@Composable
fun SleepDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val c = app.coordinator
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Sleep timer") },
        text = {
            Column {
                c?.sleepAtMs?.let { Text("Stops in ${formatDuration(it - com.westtiwan.tarang.platform.nowMillis())}", color = MaterialTheme.colorScheme.primary) }
                listOf(5, 15, 30, 45, 60, 90, 120).forEach { m ->
                    Text("$m minutes", Modifier.fillMaxWidth().clickable { c?.setSleepTimer(m); app.toast("Stopping in $m minutes"); onDismiss() }.padding(vertical = 10.dp))
                }
                Text("End of current track", Modifier.fillMaxWidth().clickable { c?.sleepAfterTrack(); app.toast("Stopping after this track"); onDismiss() }.padding(vertical = 10.dp))
                Text("Off", Modifier.fillMaxWidth().clickable { c?.setSleepTimer(null); onDismiss() }.padding(vertical = 10.dp))
            }
        },
    )
}

@Composable
fun SpeedDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val st by app.player.state.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
        dismissButton = { TextButton({ app.player.setSpeed(1f) }) { Text("Reset") } },
        title = { Text("Playback speed: ${(st.speed * 100).toInt() / 100.0}x") },
        text = {
            Slider(st.speed, { app.player.setSpeed((it * 20).toInt() / 20f) }, valueRange = 0.5f..2f, steps = 29)
        },
    )
}

@Composable
fun OutputDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val outputs by app.player.outputs.collectAsState()
    val current by app.player.currentOutput.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
        title = { Text("Audio output") },
        text = {
            Column {
                if (outputs.isEmpty()) Text("Using the system default output.")
                outputs.forEach { o ->
                    ListItem(
                        headlineContent = { Text(o.name) },
                        supportingContent = { Text(o.kind.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingContent = { RadioButton(current?.key == o.key, { app.player.selectOutput(o.key) }) },
                        modifier = Modifier.clickable { app.player.selectOutput(o.key) },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
    )
}
