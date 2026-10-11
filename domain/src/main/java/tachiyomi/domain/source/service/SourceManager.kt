package tachiyomi.domain.source.service

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.StubSource

interface SourceManager {

    val sources: Flow<List<AnimeSource>>

    suspend fun get(sourceKey: Long): AnimeSource?

    suspend fun getOrStub(sourceKey: Long): AnimeSource

    suspend fun getAll(): List<AnimeSource>

    suspend fun getOnlineSources(): List<AnimeHttpSource>

    suspend fun getStubSources(): List<StubSource>
}
