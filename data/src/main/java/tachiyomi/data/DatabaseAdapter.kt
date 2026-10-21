package tachiyomi.data

import app.cash.sqldelight.ColumnAdapter
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import kotlinx.serialization.json.JsonObject
import mihon.core.common.extensions.toByteArray
import mihon.core.common.extensions.toJsonObject
import kotlin.time.Instant

object InstantColumnAdapter : ColumnAdapter<Instant, Long> {
    override fun decode(databaseValue: Long): Instant = Instant.fromEpochMilliseconds(databaseValue)
    override fun encode(value: Instant): Long = value.toEpochMilliseconds()
}

private const val LIST_OF_STRINGS_SEPARATOR = ", "
object StringListColumnAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) = if (databaseValue.isEmpty()) {
        emptyList()
    } else {
        databaseValue.split(LIST_OF_STRINGS_SEPARATOR)
    }
    override fun encode(value: List<String>) = value.joinToString(
        separator = LIST_OF_STRINGS_SEPARATOR,
    )
}

object UpdateStrategyColumnAdapter : ColumnAdapter<AnimeUpdateStrategy, Long> {
    override fun decode(databaseValue: Long): AnimeUpdateStrategy =
        AnimeUpdateStrategy.entries.getOrElse(databaseValue.toInt()) { AnimeUpdateStrategy.ALWAYS_UPDATE }

    override fun encode(value: AnimeUpdateStrategy): Long = value.ordinal.toLong()
}

// AY -->
object FetchTypeColumnAdapter : ColumnAdapter<FetchType, Long> {
    override fun decode(databaseValue: Long): FetchType =
        FetchType.entries.getOrElse(databaseValue.toInt()) { FetchType.Episodes }

    override fun encode(value: FetchType): Long = value.ordinal.toLong()
}
// <-- AY

object MemoColumnAdapter : ColumnAdapter<JsonObject, ByteArray> {
    override fun decode(databaseValue: ByteArray): JsonObject = databaseValue.toJsonObject()
    override fun encode(value: JsonObject): ByteArray = value.toByteArray()
}

// SQLite has one integer type, so a column holding a value that cannot exceed Int range narrows
// here rather than at every call site that reads or writes it.
object IntColumnAdapter : ColumnAdapter<Int, Long> {
    override fun decode(databaseValue: Long): Int = databaseValue.toInt()
    override fun encode(value: Int): Long = value.toLong()
}
