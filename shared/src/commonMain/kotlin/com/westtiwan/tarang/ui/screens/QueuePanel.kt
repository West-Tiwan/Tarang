package com.westtiwan.tarang.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westtiwan.tarang.ui.AddToPlaylistDialog
import com.westtiwan.tarang.ui.CoverArt
import com.westtiwan.tarang.ui.LocalApp
import com.westtiwan.tarang.ui.formatDuration
import kotlinx.coroutines.launch

@Composable
fun QueuePanel(compact: Boolean) {
    val app = LocalApp.current
    val st by app.player.state.collectAsState()
    val listState = rememberLazyListState()
    var save by remember { mutableStateOf(false) }
    val rowHeight = 64.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) { if (st.index > 0) listState.scrollToItem(maxOf(0, st.index - 1)) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.titleLarge)
                val remaining = st.queue.drop(st.index + 1).sumOf { it.durationMs }
                Text("${st.queue.size} songs • ${formatDuration(remaining)} left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton({
                app.launch {
                    val ok = app.coordinator?.restoreServerQueue(false) ?: false
                    app.toast(if (ok) "Restored queue from server" else "No saved queue on server")
                }
            }) { Icon(Icons.Rounded.CloudSync, "Restore server queue") }
            IconButton({ save = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Save as playlist") }
            IconButton({ app.player.clearQueue() }) { Icon(Icons.Rounded.ClearAll, "Clear") }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(st.queue, key = { i, s -> "$i-${s.id}" }) { i, song ->
                val isCurrent = i == st.index
                val dragging = i == dragIndex
                val target = if (dragIndex >= 0) (dragIndex + kotlin.math.round(dragOffset / rowPx).toInt()).coerceIn(0, st.queue.lastIndex) else -1
                val shift = when {
                    dragIndex < 0 || dragging -> 0f
                    i in (dragIndex + 1)..target -> -rowPx
                    i in target until dragIndex -> rowPx
                    else -> 0f
                }
                Row(
                    Modifier.fillMaxWidth().height(rowHeight)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else shift; shadowElevation = if (dragging) 16f else 0f }
                        .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { app.player.skipTo(i) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp)) {
                        CoverArt(song.coverArt, Modifier.size(44.dp), size = 120, shape = RoundedCornerShape(6.dp))
                        if (isCurrent) Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else if (i < st.index) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        Text(song.artist ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!compact || !isCurrent) IconButton({ app.player.remove(i) }) { Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp)) }
                    Icon(
                        Icons.Rounded.DragHandle, "Reorder",
                        modifier = Modifier.padding(8.dp).pointerInput(i, st.queue.size) {
                            detectDragGestures(
                                onDragStart = { dragIndex = i; dragOffset = 0f },
                                onDragEnd = {
                                    val target = (dragIndex + (dragOffset / rowPx).let { kotlin.math.round(it).toInt() }).coerceIn(0, st.queue.lastIndex)
                                    if (dragIndex >= 0 && target != dragIndex) app.player.move(dragIndex, target)
                                    dragIndex = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragIndex = -1; dragOffset = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                            }
                        },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (save) AddToPlaylistDialog(st.queue.filter { it.streamUrlOverride == null }) { save = false }
}
