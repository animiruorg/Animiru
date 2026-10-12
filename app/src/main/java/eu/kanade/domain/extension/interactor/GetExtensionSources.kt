package eu.kanade.domain.extension.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Inject
class GetExtensionSources(
    private val preferences: SourcePreferences,
) {

    fun subscribe(extension: Extension.Loaded): Flow<List<ExtensionSourceItem>> {
        val isMultiSource = extension.sources.size > 1
        val isMultiLangSingleSource =
            isMultiSource && extension.sources.map { it.name }.distinct().size == 1

        return preferences.disabledSources.changes().map { disabledSources ->
            fun AnimeSource.isEnabled() = id.toString() !in disabledSources

            extension.sources
                .map { source ->
                    ExtensionSourceItem(
                        source = source,
                        enabled = source.isEnabled(),
                        labelAsName = isMultiSource && !isMultiLangSingleSource,
                    )
                }
        }
    }
}

data class ExtensionSourceItem(
    val source: AnimeSource,
    val enabled: Boolean,
    val labelAsName: Boolean,
)
