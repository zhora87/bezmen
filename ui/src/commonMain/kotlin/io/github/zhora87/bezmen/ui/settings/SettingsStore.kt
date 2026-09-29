package io.github.zhora87.bezmen.ui.settings

import io.github.zhora87.bezmen.domain.Settings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Where settings live between launches; null means nothing saved yet. */
interface SettingsStore {
    suspend fun load(): Settings?

    suspend fun save(settings: Settings)
}

/** Settings as one JSON document; [read] and [write] are the platform's file access. */
class JsonSettingsStore(
    private val read: suspend () -> String?,
    private val write: suspend (String) -> Unit,
) : SettingsStore {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun load(): Settings? {
        val text = read() ?: return null
        return try {
            json.decodeFromString(Settings.serializer(), text)
        } catch (ignored: SerializationException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }

    override suspend fun save(settings: Settings) = write(json.encodeToString(Settings.serializer(), settings))
}
