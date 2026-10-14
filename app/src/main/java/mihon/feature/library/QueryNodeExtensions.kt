package mihon.feature.library

import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import mihon.domain.library.model.search.AndNode
import mihon.domain.library.model.search.AnimeField
import mihon.domain.library.model.search.ComparisonField
import mihon.domain.library.model.search.ComparisonQueryNode
import mihon.domain.library.model.search.EmptyQueryNode
import mihon.domain.library.model.search.FieldQueryNode
import mihon.domain.library.model.search.GeneralQueryNode
import mihon.domain.library.model.search.NotNode
import mihon.domain.library.model.search.OrNode
import mihon.domain.library.model.search.QueryNode
import tachiyomi.source.local.LocalSource
import kotlin.math.abs
import kotlin.time.Instant

fun QueryNode.matches(item: LibraryItem): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item) }
        is OrNode -> children.any { it.matches(item) }
        is NotNode -> !child.matches(item)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
        is ComparisonQueryNode -> matches(item)
    }
}

private fun GeneralQueryNode.matches(item: LibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    // Use when so each added field has to be handled explicitly
    val match = AnimeField.entries.any { field ->
        if (field.fieldOnly) return@any false

        when (field) {
            AnimeField.TITLE -> anime.title.contains(value, ignoreCase = true)
            AnimeField.AUTHOR -> anime.author?.contains(value, ignoreCase = true) ?: false
            AnimeField.ARTIST -> anime.artist?.contains(value, ignoreCase = true) ?: false
            AnimeField.DESCRIPTION -> anime.description?.contains(value, ignoreCase = true) ?: false
            AnimeField.GENRE -> anime.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            AnimeField.SOURCE -> {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && anime.source == LocalSource.ID)
            }
            AnimeField.NOTES -> anime.notes.contains(value, ignoreCase = true)

            // field-only queries; unreachable; added here to make `when` exhaustive
            AnimeField.LANGUAGE, AnimeField.SOURCE_ID -> error("How did we get here?")
        }
    }
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: LibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    val match = when (field) {
        AnimeField.GENRE -> {
            if (value.isEmpty()) {
                anime.genre.isNullOrEmpty()
            } else {
                anime.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            }
        }

        AnimeField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && anime.source == LocalSource.ID)
            }
        }

        AnimeField.SOURCE_ID -> {
            value.toLongOrNull()?.let { it == anime.source } ?: false
        }

        else -> {
            val text = when (field) {
                AnimeField.TITLE -> anime.title
                AnimeField.AUTHOR -> anime.author
                AnimeField.ARTIST -> anime.artist
                AnimeField.DESCRIPTION -> anime.description
                AnimeField.NOTES -> anime.notes
                AnimeField.LANGUAGE -> item.sourceLanguage

                // unreachable; added here to make `when` exhaustive
                AnimeField.GENRE, AnimeField.SOURCE, AnimeField.SOURCE_ID -> error("How did we get here?")
            }

            if (value.isEmpty()) {
                text.isNullOrEmpty()
            } else {
                text?.contains(value, ignoreCase = true) ?: false
            }
        }
    }

    return if (negated) !match else match
}

private fun ComparisonQueryNode.matches(item: LibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    fun compareDates(timestamp: Long, value: String): Boolean? {
        val inputDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
        val animeDate = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(TimeZone.currentSystemDefault()).date
        return queryComparator.apply(animeDate, inputDate)
    }

    val match = when (field) {
        ComparisonField.ID -> value.toLongOrNull()?.let { queryComparator.apply(anime.id, it) }

        ComparisonField.DATE_ADDED -> anime.favoriteAt?.let { compareDates(it, value) }

        ComparisonField.FETCH_INTERVAL -> value.toIntOrNull()
            ?.let { queryComparator.apply(abs(anime.fetchInterval), it) }

        ComparisonField.NEXT_UPDATE -> compareDates(anime.nextUpdate, value)

        ComparisonField.UNSEEN -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.unseenCount, it)
            }
        }

        ComparisonField.SEEN -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryAnime.seenCount, it)
            }
        }

        ComparisonField.TOTAL -> {
            value.toLongOrNull()?.let {
                queryComparator.apply(item.libraryAnime.totalCount, it)
            }
        }
    } ?: false

    return if (negated) !match else match
}
