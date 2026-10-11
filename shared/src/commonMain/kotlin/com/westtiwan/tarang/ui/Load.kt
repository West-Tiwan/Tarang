package com.westtiwan.tarang.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val message: String) : Load<Nothing>
}

/** In memory cache so navigating back does not refetch. */
object MemCache {
    private val map = HashMap<String, Any?>()
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = map[key] as T?
    fun put(key: String, v: Any?) { map[key] = v }
    fun invalidate(prefix: String) { map.keys.filter { it.startsWith(prefix) }.forEach { map.remove(it) } }
    fun clear() = map.clear()
}

class Loader<T>(initial: Load<T>, private val onReload: () -> Unit) {
    var state by mutableStateOf(initial)
        internal set
    fun reload() = onReload()
    val value: T? get() = (state as? Load.Ok<T>)?.value
}

@Composable
fun <T> rememberLoad(key: String, cache: Boolean = true, block: suspend () -> T): Loader<T> {
    var nonce by remember(key) { mutableIntStateOf(0) }
    val loader = remember(key) {
        val cached = if (cache) MemCache.get<T>(key) else null
        Loader<T>(if (cached != null) Load.Ok(cached) else Load.Loading) { nonce++ }
    }
    LaunchedEffect(key, nonce) {
        val hadValue = loader.state is Load.Ok
        if (nonce == 0 && hadValue) return@LaunchedEffect
        if (!hadValue) loader.state = Load.Loading
        loader.state = try {
            val v = block()
            if (cache) MemCache.put(key, v)
            Load.Ok(v)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (hadValue) loader.state else Load.Err(e.message ?: "Something went wrong")
        }
    }
    return loader
}
