package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import mihon.core.common.extensions.toJsonObject
import tachiyomi.domain.anime.model.Anime

@Suppress("DEPRECATION")
@Serializable
data class BackupAnime(
    // in 1.x some of these values have different names
    @ProtoNumber(1) var source: Long,
    // url is called key in 1.x
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    // thumbnailUrl is called cover in 1.x
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    // @ProtoNumber(10) val customCover: String = "", 1.x value, not used in 0.x
    // @ProtoNumber(11) val lastUpdate: Long = 0, 1.x value, not used in 0.x
    // @ProtoNumber(12) val lastInit: Long = 0, 1.x value, not used in 0.x
    @ProtoNumber(13) var dateAdded: Long = 0,
    // @ProtoNumber(15) val flags: Int = 0, 1.x value, not used in 0.x
    @ProtoNumber(16) var episodes: List<BackupEpisode> = emptyList(),
    @ProtoNumber(17) var categories: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupTracking> = emptyList(),
    // Bump by 100 for values that are not saved/implemented in 1.x but are used in 0.x
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var episodeFlags: Int = 0,
    // @ProtoNumber(102) var brokenHistory, legacy history model with non-compliant proto number
    @ProtoNumber(103) var viewer_flags: Int = 0,
    @ProtoNumber(104) var history: List<BackupHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
    // @ProtoNumber(106) var lastModifiedAt: Long, artifact of the abandoned sync attempt
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    // Mihon values start here
    // @ProtoNumber(109) var version: Long, artifact of the abandoned sync attempt
    @ProtoNumber(110) var notes: String = "",
    @ProtoNumber(111) var initialized: Boolean = false,
    @ProtoNumber(112) var memo: ByteArray = JsonObjectEmptyBytes,

    // AM (CUSTOM_INFORMATION) -->
    // Bump values by 200
    @ProtoNumber(200) var customStatus: Int = 0,
    @ProtoNumber(201) var customTitle: String? = null,
    @ProtoNumber(202) var customArtist: String? = null,
    @ProtoNumber(203) var customAuthor: String? = null,
    @ProtoNumber(204) var customDescription: String? = null,
    @ProtoNumber(205) var customGenre: List<String>? = null,
    // <-- AM (CUSTOM_INFORMATION)
    // Since ProtoNumber 108 was previously used in the past, we cannot reuse it. May cause issues with aniyomi
    // AM -->
    @ProtoNumber(206) var excludedScanlators: List<String> = emptyList(),
    // <-- AM

    // AY -->
    // Aniyomi specific values
    @ProtoNumber(500) var backgroundUrl: String? = null,
    // @ProtoNumber(501) Broken in aniyomi, do not use
    @ProtoNumber(502) var parentId: Long? = null,
    @ProtoNumber(503) var id: Long? = null, // Used to associate seasons with parents. Do not use for anything else.
    @ProtoNumber(504) var seasonFlags: Long = 0,
    @ProtoNumber(505) var seasonNumber: Double = -1.0,
    @ProtoNumber(506) var seasonSourceOrder: Long = 0,
    @ProtoNumber(507) var fetchType: FetchType = FetchType.Episodes,
    // <-- AY
) {
    fun getAnimeImpl(): Anime {
        return Anime.create().copy(
            url = this@BackupAnime.url,
            // AM (CUSTOM_INFORMATION) -->
            ogTitle = this@BackupAnime.title,
            ogArtist = this@BackupAnime.artist,
            ogAuthor = this@BackupAnime.author,
            ogDescription = this@BackupAnime.description,
            ogGenre = this@BackupAnime.genre,
            ogStatus = this@BackupAnime.status.toLong(),
            // <-- AM (CUSTOM_INFORMATION)
            thumbnailUrl = this@BackupAnime.thumbnailUrl,
            // AY -->
            backgroundUrl = this@BackupAnime.backgroundUrl,
            // <-- AY
            source = this@BackupAnime.source,
            // For libraries older than dateAdded, the favorite timestamp is the only record of when
            // an entry was added. It was written by a trigger in seconds, unlike dateAdded.
            favoriteAt = if (this@BackupAnime.favorite) {
                this@BackupAnime.dateAdded.takeIf { it != 0L }
                    ?: this@BackupAnime.favoriteModifiedAt?.times(1000)
                    ?: 0L
            } else {
                null
            },
            viewerFlags = this@BackupAnime.viewer_flags.toLong(),
            episodeFlags = this@BackupAnime.episodeFlags.toLong(),
            updateStrategy = this@BackupAnime.updateStrategy,
            notes = this@BackupAnime.notes,
            initialized = this@BackupAnime.initialized,
            // AY -->
            fetchType = this@BackupAnime.fetchType,
            parentId = this@BackupAnime.parentId,
            seasonFlags = this@BackupAnime.seasonFlags,
            seasonNumber = this@BackupAnime.seasonNumber,
            seasonSourceOrder = this@BackupAnime.seasonSourceOrder,
            // <-- AY
            memo = this@BackupAnime.memo.toJsonObject(),
        )
    }
}
