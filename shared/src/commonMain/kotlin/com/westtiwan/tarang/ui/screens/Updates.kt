package com.westtiwan.tarang.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.update.UpdateState
import com.westtiwan.tarang.ui.LocalApp
import kotlin.math.PI
import kotlin.math.sin

/** Animated waves, the Tarang mark. */
@Composable
fun WaveMark(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    val c1 = MaterialTheme.colorScheme.primary
    val c2 = MaterialTheme.colorScheme.tertiary
    val c3 = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        listOf(Triple(c1, 0f, 1f), Triple(c2, 1.2f, 0.7f), Triple(c3, 2.4f, 0.45f)).forEachIndexed { i, (color, offset, amp) ->
            val path = Path()
            val midY = size.height * (0.35f + i * 0.15f)
            val steps = 60
            for (s in 0..steps) {
                val x = size.width * s / steps
                val y = midY + sin(s / steps.toFloat() * 4 * PI.toFloat() + t + offset) * size.height * 0.12f * amp
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = size.height * (0.07f - i * 0.015f), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Notes(body: String?) {
    val lines = body.orEmpty().lines().map { it.trim() }.filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lines.isEmpty()) Text("Bug fixes and improvements.", style = MaterialTheme.typography.bodyLarge)
        lines.forEach { line ->
            val bullet = line.startsWith("- ") || line.startsWith("* ")
            Row {
                if (bullet) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(10.dp))
                }
                Text(line.removePrefix("- ").removePrefix("* ").replace("`", "").trimStart('#', ' '), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Shown once after the app updates. */
@Composable
fun WhatsNewSplash(version: String, onDone: () -> Unit) {
    val app = LocalApp.current
    var notes by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(version) { notes = app.updater.notesFor(version)?.body }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)),
        ).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WaveMark(Modifier.width(180.dp).height(110.dp))
            Spacer(Modifier.height(20.dp))
            Text("Tarang $version", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text("What's new", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(20.dp)) { Notes(notes) }
            }
            Spacer(Modifier.height(24.dp))
            Button(onDone, Modifier.fillMaxWidth().height(56.dp)) { Text("Let's go") }
        }
    }
}

/** Floating card when an update is available, downloading, or ready to install. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val state by app.updater.state.collectAsState()
    var notesOpen by remember { mutableStateOf(false) }
    val visible = state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready
    if (!visible) return
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 6.dp, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    when (val s = state) {
                        is UpdateState.Available -> Text("Tarang ${s.release.version} is out", style = MaterialTheme.typography.titleMedium)
                        is UpdateState.Downloading -> Text("Downloading ${s.release.version}", style = MaterialTheme.typography.titleMedium)
                        is UpdateState.Ready -> Text("${s.release.version} is ready to install", style = MaterialTheme.typography.titleMedium)
                        else -> {}
                    }
                }
                if (state is UpdateState.Available) IconButton({ app.updater.dismiss() }) { Icon(Icons.Rounded.Close, "Later") }
            }
            when (val s = state) {
                is UpdateState.Available -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ notesOpen = !notesOpen }) { Text(if (notesOpen) "Hide notes" else "What's new") }
                    Button({ app.launch { app.updater.download(s.release, s.asset) } }) { Text("Update") }
                }
                is UpdateState.Downloading -> LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                is UpdateState.Ready -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button({ app.updater.install(s.path) }) { Text(if (app.updater.restartsApp) "Restart and update" else "Install") }
                }
                else -> {}
            }
            if (notesOpen) (state as? UpdateState.Available)?.let { Box(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()).padding(top = 8.dp)) { Notes(it.release.body) } }
        }
    }
}
