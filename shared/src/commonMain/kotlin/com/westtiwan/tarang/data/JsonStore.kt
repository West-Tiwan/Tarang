package com.westtiwan.tarang.data

import com.westtiwan.tarang.platform.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

/** A value persisted as JSON on disk, exposed as a StateFlow. Writes are debounced. */
class JsonStore<T>(
    private val path: String,
    private val serializer: KSerializer<T>,
    default: () -> T,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(load() ?: default())
    val state: StateFlow<T> = _state.asStateFlow()
    val value: T get() = _state.value
    private var pending: Job? = null

    private fun load(): T? = runCatching {
        Files.readText(path)?.let { json.decodeFromString(serializer, it) }
    }.getOrNull()

    fun update(transform: (T) -> T) {
        _state.value = transform(_state.value)
        pending?.cancel()
        pending = scope.launch(Dispatchers.Default) {
            delay(300)
            flush()
        }
    }

    fun set(value: T) = update { value }

    fun flush() {
        runCatching { Files.writeText(path, json.encodeToString(serializer, _state.value)) }
    }
}
