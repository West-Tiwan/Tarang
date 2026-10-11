package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.download.DownloadStatus
import com.westtiwan.tarang.ui.CenteredMessage
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.SectionHeader
import com.westtiwan.tarang.ui.SongRow
import com.westtiwan.tarang.ui.formatBytes

@Composable
fun DownloadsScreen() {
    val app = LocalApp.current
    val state by app.downloads.state.collectAsState()
    val items = state.items.values.sortedByDescending { it.addedAt }
    val done = items.filter { it.status == DownloadStatus.DONE }
    val active = items.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
    val failed = items.filter { it.status == DownloadStatus.FAILED }
    Column(Modifier.fillMaxSize()) {
        BackBar("Downloads") {
            IconButton({ app.player.play(done.map { it.song }.shuffled()) }, enabled = done.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, "Shuffle downloads") }
            if (failed.isNotEmpty()) IconButton({ app.downloads.retryFailed() }) { Icon(Icons.Rounded.Refresh, "Retry failed") }
            IconButton({ app.downloads.remove(failed.map { it.song.id } + active.map { it.song.id }) }, enabled = failed.isNotEmpty() || active.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteSweep, "Cancel pending")
            }
        }
        Text(
            "${done.size} songs • ${formatBytes(done.sumOf { it.bytes })}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (items.isEmpty()) CenteredMessage("Download albums, playlists or songs to play them without a connection.")
        LazyColumn(Modifier.weight(1f)) {
            if (active.isNotEmpty()) {
                item { SectionHeader("In progress (${active.size})") }
                items(active, key = { "a" + it.song.id }) { d ->
                    Column {
                        SongRow(d.song, onClick = {})
                        LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                }
            }
            if (failed.isNotEmpty()) {
                item { SectionHeader("Failed (${failed.size})") }
                items(failed, key = { "f" + it.song.id }) { d ->
                    Column {
                        SongRow(d.song, onClick = {})
                        Text(d.error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            if (done.isNotEmpty()) {
                item { SectionHeader("Downloaded") }
                val songs = done.map { it.song }
                items(songs.size, key = { "d" + songs[it].id }) { i -> SongRow(songs[i], onClick = { app.player.play(songs, i) }) }
            }
        }
    }
}
