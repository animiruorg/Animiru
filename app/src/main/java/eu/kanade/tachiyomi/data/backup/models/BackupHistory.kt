package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import mihon.core.common.extensions.toInstantOrNull
import tachiyomi.domain.history.model.History

@Serializable
data class BackupHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastSeen: Long,
) {
    fun getHistoryImpl(): History {
        return History.create().copy(
            seenAt = lastSeen.toInstantOrNull(),
        )
    }
}
