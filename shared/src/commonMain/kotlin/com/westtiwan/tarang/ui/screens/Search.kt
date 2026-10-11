package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.data.SearchResult3
import com.westtiwan.tarang.ui.AlbumCard
import com.westtiwan.tarang.ui.ArtistCircle
import com.westtiwan.tarang.ui.CenteredMessage
import com.westtiwan.tarang.ui.Loading
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.OfflineLibrary
import com.westtiwan.tarang.ui.SectionHeader
import com.westtiwan.tarang.ui.SongRow
import kotlinx.coroutines.delay

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<SearchResult3?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        if (query.isBlank()) { result = null; return@LaunchedEffect }
        delay(300)
        busy = true
        error = null
        result = if (app.offline) SearchResult3(song = OfflineLibrary.search(app, query))
        else runCatching { app.api?.search(query, 20, 30, 100) }.onFailure { error = it.message }.getOrNull()
            ?: SearchResult3(song = OfflineLibrary.search(app, query))
        busy = false
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Songs, albums, artists") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        val r = result
        when {
            busy && r == null -> Loading()
            error != null && r == null -> CenteredMessage(error!!)
            r == null -> CenteredMessage("Search your whole library")
            r.artist.isEmpty() && r.album.isEmpty() && r.song.isEmpty() -> CenteredMessage("No results for \"$query\"")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (r.artist.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    item { LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) { items(r.artist, key = { it.id }) { ArtistCircle(it) } } }
                }
                if (r.album.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(r.album, key = { it.id }) { AlbumCard(it) }
                        }
                    }
                }
                if (r.song.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    items(r.song.size, key = { r.song[it].id }) { i -> SongRow(r.song[i], onClick = { app.player.play(r.song, i) }) }
                }
            }
        }
    }
}
