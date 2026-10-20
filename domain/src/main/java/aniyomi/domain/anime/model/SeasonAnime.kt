package aniyomi.domain.anime.model

import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.model.LibraryAnime
import kotlin.time.Instant

data class SeasonAnime(
    val anime: Anime,
    val totalCount: Long,
    val seenCount: Long,
    val bookmarkCount: Long,
    val fillermarkCount: Long,
    val latestUpload: Instant?,
    val fetchedAt: Instant?,
    val lastSeen: Instant?,
) {
    val id: Long = anime.id

    val seen
        get() = totalCount == seenCount

    val unseenCount
        get() = totalCount - seenCount

    val hasStarted = seenCount > 0

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasFillermarks
        get() = fillermarkCount > 0

    fun toLibraryAnime(): LibraryAnime {
        return LibraryAnime(
            anime = anime,
            categories = emptyList(),
            totalCount = totalCount,
            seenCount = seenCount,
            bookmarkCount = bookmarkCount,
            fillermarkCount = fillermarkCount,
            latestUpload = latestUpload,
            episodeFetchedAt = fetchedAt,
            lastSeen = lastSeen,
        )
    }
}
