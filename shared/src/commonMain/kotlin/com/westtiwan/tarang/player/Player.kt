package com.westtiwan.tarang.player

import com.westtiwan.tarang.data.RepeatMode
import com.westtiwan.tarang.data.Song
import com.westtiwan.tarang.eq.OutputDevice
import kotlinx.coroutines.flow.StateFlow

data class AudioInfo(
    val codec: String? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val bitrate: Int? = null,
    /** True when samples reach the DAC untouched (no resampling, mixing, EQ or volume scaling). */
    val bitPerfect: Boolean = false,
    val output: String? = null,
    val outputRate: Int? = null,
    val pipeline: List<String> = emptyList(),
    val source: String? = null,
)

data class PlayerState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val audio: AudioInfo = AudioInfo(),
    val error: String? = null,
) {
    val current: Song? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index < queue.lastIndex || repeat != RepeatMode.OFF
}

/** Implemented with Media3 on Android and libVLC on desktop. */
interface PlayerController {
    val state: StateFlow<PlayerState>
    val outputs: StateFlow<List<OutputDevice>>
    val currentOutput: StateFlow<OutputDevice?>

    fun play(songs: List<Song>, startIndex: Int = 0, startPositionMs: Long = 0, autoPlay: Boolean = true)
    fun playNext(songs: List<Song>)
    fun enqueue(songs: List<Song>)
    fun toggle()
    fun pause()
    fun resume()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun skipTo(index: Int)
    fun remove(index: Int)
    fun move(from: Int, to: Int)
    fun clearQueue()
    fun setShuffle(on: Boolean)
    fun setRepeat(mode: RepeatMode)
    fun setSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun selectOutput(key: String?)
    /** Re-applies EQ, replay gain and bit perfect settings. */
    fun refreshAudio()
    /** Replaces queue metadata after a star or rating change without interrupting playback. */
    fun updateSong(song: Song)
}
