package com.westtiwan.tarang.sync

import com.westtiwan.tarang.data.SubsonicApi
import com.westtiwan.tarang.data.json
import com.westtiwan.tarang.eq.EqPreset
import com.westtiwan.tarang.eq.EqState
import com.westtiwan.tarang.smart.SmartPlaylist
import com.westtiwan.tarang.smart.SmartState
import kotlinx.serialization.Serializable

@Serializable
data class SyncedSettings(
    val version: Int = 1,
    val eqUpdatedAt: Long = 0,
    val eqPresets: List<EqPreset> = emptyList(),
    val deviceProfiles: Map<String, String> = emptyMap(),
    val smartUpdatedAt: Long = 0,
    val smart: List<SmartPlaylist> = emptyList(),
)

/**
 * Navidrome has no API for client settings, so EQ presets, per device EQ assignments and smart
 * playlist definitions are stored as JSON in the comment of a private playlist owned by the user.
 * That makes them follow the account to every device running Tarang.
 */
object SettingsSync {
    const val PLAYLIST_NAME = "Tarang settings"

    fun isSettingsPlaylist(name: String) = name == PLAYLIST_NAME

    suspend fun pull(api: SubsonicApi): SyncedSettings? {
        val pl = api.getPlaylists().firstOrNull { isSettingsPlaylist(it.name) && it.owner == api.server.username } ?: return null
        val comment = pl.comment ?: return null
        return runCatching { json.decodeFromString(SyncedSettings.serializer(), comment) }.getOrNull()
    }

    suspend fun push(api: SubsonicApi, eq: EqState, smart: SmartState) {
        val payload = SyncedSettings(
            eqUpdatedAt = eq.updatedAt,
            eqPresets = eq.presets,
            deviceProfiles = eq.deviceProfiles,
            smartUpdatedAt = smart.updatedAt,
            smart = smart.playlists,
        )
        val text = json.encodeToString(SyncedSettings.serializer(), payload)
        val existing = api.getPlaylists().firstOrNull { isSettingsPlaylist(it.name) && it.owner == api.server.username }
        val id = existing?.id ?: api.createPlaylist(PLAYLIST_NAME, emptyList())?.id
            ?: api.getPlaylists().firstOrNull { isSettingsPlaylist(it.name) }?.id
            ?: return
        api.updatePlaylist(id, comment = text, public = false)
    }
}
