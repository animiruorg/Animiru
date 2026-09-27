package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.JsonObjectEmptyBytes
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.domain.episode.model.Episode

@Serializable
class BackupEpisode(
    // in 1.x some of these values have different names
    // url is called key in 1.x
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var seen: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    // lastSecondSeen is called progress in 1.x
    @ProtoNumber(6) var lastSecondSeen: Long = 0,
    // AY -->
    @ProtoNumber(16) var totalSeconds: Long = 0,
    // <-- AY
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    // episodeNumber is called number is 1.x
    @ProtoNumber(9) var episodeNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Long = 0,
    @ProtoNumber(11) var lastModifiedAt: Long = 0,
    @ProtoNumber(12) var version: Long = 0,
    @ProtoNumber(13) var memo: ByteArray = JsonObjectEmptyBytes,

    // AY -->
    // Aniyomi specific values
    @ProtoNumber(501) var fillermark: Boolean = false,
    @ProtoNumber(502) var summary: String? = null,
    @ProtoNumber(503) var previewUrl: String? = null,
    // <-- AY
) {
    fun toEpisodeImpl(): Episode {
        return Episode.create().copy(
            url = this@BackupEpisode.url,
            name = this@BackupEpisode.name,
            episodeNumber = this@BackupEpisode.episodeNumber.toDouble(),
            scanlator = this@BackupEpisode.scanlator,
            // AY -->
            summary = this@BackupEpisode.summary,
            previewUrl = this@BackupEpisode.previewUrl,
            // <-- AY
            seen = this@BackupEpisode.seen,
            bookmark = this@BackupEpisode.bookmark,
            // AY -->
            fillermark = this@BackupEpisode.fillermark,
            // <-- AY
            lastSecondSeen = this@BackupEpisode.lastSecondSeen,
            // AY -->
            totalSeconds = this@BackupEpisode.totalSeconds,
            // <-- AY
            dateFetch = this@BackupEpisode.dateFetch,
            dateUpload = this@BackupEpisode.dateUpload,
            sourceOrder = this@BackupEpisode.sourceOrder,
            lastModifiedAt = this@BackupEpisode.lastModifiedAt,
            version = this@BackupEpisode.version,
            memo = MemoColumnAdapter.decode(this@BackupEpisode.memo),
        )
    }
}

val backupEpisodeMapper = {
        _: Long,
        _: Long,
        url: String,
        name: String,
        scanlator: String?,
        seen: Boolean,
        bookmark: Boolean,
        // AY -->
        fillermark: Boolean,
        // <-- AY
        lastSecondSeen: Long,
        // AY -->
        totalSeconds: Long,
        // <-- AY
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        _: Long,
        // AY -->
        summary: String?,
        previewUrl: String?,
        // <-- AY
        memo: JsonObject,
        // AM -->
        _: Long,
        // <-- AM
    ->
    BackupEpisode(
        url = url,
        name = name,
        episodeNumber = episodeNumber.toFloat(),
        scanlator = scanlator,
        // AY -->
        summary = summary,
        previewUrl = previewUrl,
        // <-- AY
        seen = seen,
        bookmark = bookmark,
        // AY -->
        fillermark = fillermark,
        // <-- AY
        lastSecondSeen = lastSecondSeen,
        // AY -->
        totalSeconds = totalSeconds,
        // <-- AY
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = sourceOrder,
        lastModifiedAt = lastModifiedAt,
        version = version,
        memo = MemoColumnAdapter.encode(memo),
    )
}
