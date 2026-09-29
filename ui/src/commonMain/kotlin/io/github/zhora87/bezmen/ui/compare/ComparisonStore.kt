package io.github.zhora87.bezmen.ui.compare

import io.github.zhora87.bezmen.domain.comparison.ComparisonItem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Where the comparison list lives between launches. */
interface ComparisonStore {
    suspend fun load(): List<ComparisonItem>

    suspend fun save(items: List<ComparisonItem>)
}

/**
 * The list as one JSON document; [read] and [write] are the platform's file access. A missing or
 * unreadable document is an empty list: the data is a shopping aid, not a ledger.
 */
class JsonComparisonStore(
    private val read: suspend () -> String?,
    private val write: suspend (String) -> Unit,
) : ComparisonStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ComparisonItem.serializer())

    override suspend fun load(): List<ComparisonItem> {
        val text = read() ?: return emptyList()
        return try {
            json.decodeFromString(serializer, text)
        } catch (ignored: SerializationException) {
            emptyList()
        } catch (ignored: IllegalArgumentException) {
            emptyList()
        }
    }

    override suspend fun save(items: List<ComparisonItem>) = write(json.encodeToString(serializer, items))
}
