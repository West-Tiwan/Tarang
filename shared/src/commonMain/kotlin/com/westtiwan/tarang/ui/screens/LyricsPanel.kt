package com.westtiwan.tarang.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.lyrics.LyricsDoc
import com.westtiwan.tarang.lyrics.Script
import com.westtiwan.tarang.lyrics.looksLikeHinglish
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.MemCache

@Composable
fun LyricsPanel(song: Song) {
    val app = LocalApp.current
    val settings by app.settings.collectAsState()
    val st by app.player.state.collectAsState()
    var doc by remember(song.id) { mutableStateOf<LyricsDoc?>(app.lyrics.cached(song)) }
    var loading by remember(song.id) { mutableStateOf(doc == null) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var translated by remember(song.id) { mutableStateOf<List<String>?>(MemCache.get("tr:${song.id}:${settings.translateTarget}")) }
    var translating by remember(song.id) { mutableStateOf(false) }
    var translateError by remember(song.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(song.id, reloadTick) {
        if (doc != null && reloadTick == 0) { loading = false; return@LaunchedEffect }
        loading = true
        doc = app.lyrics.load(song, if (app.offline) null else app.api, settings.lyricsSources, force = reloadTick > 0)
        loading = false
    }

    val current = doc
    if (loading && current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (current == null || current.lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No lyrics found", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton({ app.lyrics.clearCache(song); reloadTick++ }) { Icon(Icons.Rounded.Refresh, "Search again") }
            }
        }
        return
    }

    val (script, romanized) = remember(current) { app.lyricsProcessor.romanize(current) }
    val hinglish = remember(current) { script == Script.LATIN && looksLikeHinglish(current.plainText) }
    var showRoman by remember(song.id) { mutableStateOf(settings.showRomanized && romanized != null) }
    var showOriginal by remember(song.id) { mutableStateOf(true) }
    val foreign = remember(current, settings.translateTarget) { app.lyricsProcessor.isForeign(current, settings.translateTarget) }
    var showTranslation by remember(song.id, foreign) { mutableStateOf(settings.showTranslation || (settings.autoTranslateForeign && (foreign || hinglish))) }
    val fontScale = settings.lyricsFontScale

    LaunchedEffect(showTranslation, song.id, settings.translateTarget) {
        if (!showTranslation || translated != null || translating) return@LaunchedEffect
        val tr = app.translator()
        if (tr == null) {
            translateError = "Set a LibreTranslate server in Settings to translate on this device"
            return@LaunchedEffect
        }
        translating = true
        translateError = null
        runCatching { app.lyricsProcessor.translate(current, tr, settings.translateTarget) }
            .onSuccess { translated = it; MemCache.put("tr:${song.id}:${settings.translateTarget}", it) }
            .onFailure { translateError = it.message ?: "Translation failed" }
        translating = false
    }

    val active = current.activeIndex(st.positionMs + 250)
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(maxOf(0, active), scrollOffset = -listState.layoutInfo.viewportSize.height / 3)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (romanized != null) {
                FilterChip(showOriginal, { showOriginal = !showOriginal || !showRoman }, label = { Text("Original") })
                FilterChip(showRoman, { showRoman = !showRoman || !showOriginal }, label = { Text(if (script == Script.DEVANAGARI || script == Script.GURMUKHI) "Hinglish" else "Pronunciation") })
            }
            FilterChip(showTranslation, { showTranslation = !showTranslation }, label = { Text("Translation") })
            Box(Modifier.weight(1f))
            IconButton({ app.updateSettings { it.copy(lyricsFontScale = (fontScale - 0.1f).coerceAtLeast(0.7f)) } }) { Icon(Icons.Rounded.TextDecrease, "Smaller") }
            IconButton({ app.updateSettings { it.copy(lyricsFontScale = (fontScale + 0.1f).coerceAtMost(1.8f)) } }) { Icon(Icons.Rounded.TextIncrease, "Larger") }
        }
        if (translating) Text("Translating...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        translateError?.let { if (showTranslation) Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 120.dp)) {
            itemsIndexed(current.lines) { i, line ->
                val isActive = i == active
                val past = current.synced && active >= 0 && i < active
                val color by animateColorAsState(
                    when {
                        !current.synced -> MaterialTheme.colorScheme.onSurface
                        isActive -> MaterialTheme.colorScheme.onSurface
                        past -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    },
                    label = "lyric",
                )
                val primary = when {
                    showOriginal -> line.text
                    showRoman && romanized != null -> romanized[i]
                    else -> line.text
                }
                val secondary = if (showOriginal && showRoman && romanized != null) romanized[i] else null
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = current.synced && line.startMs != null) { line.startMs?.let { app.player.seekTo(it) } }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    if (line.text.isBlank()) {
                        Text("♪", color = color, fontSize = (20 * fontScale).sp)
                    } else {
                        Text(
                            primary,
                            color = color,
                            fontSize = ((if (isActive) 26 else 22) * fontScale).sp,
                            lineHeight = ((if (isActive) 34 else 30) * fontScale).sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        )
                        if (secondary != null && secondary != primary) {
                            Text(secondary, color = color.copy(alpha = color.alpha * 0.8f), fontSize = (17 * fontScale).sp, lineHeight = (23 * fontScale).sp)
                        }
                        val tr = translated?.getOrNull(i)
                        if (showTranslation && !tr.isNullOrBlank() && tr != line.text) {
                            Text(tr, color = MaterialTheme.colorScheme.secondary.copy(alpha = if (isActive || !current.synced) 1f else 0.7f), fontSize = (16 * fontScale).sp, fontStyle = FontStyle.Italic)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Source: " + when (current.source) { "server" -> "Navidrome"; "lrclib" -> "LRCLIB"; else -> current.source } + if (current.synced) " (synced)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton({ app.lyrics.clearCache(song); doc = null; reloadTick++ }) { Icon(Icons.Rounded.Refresh, "Reload lyrics") }
                }
            }
        }
    }
}
