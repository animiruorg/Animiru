package eu.kanade.tachiyomi.source

import android.content.Context
import eu.kanade.tachiyomi.animesource.AnimeSource
import mihon.app.di.appGraph
import mihon.domain.extension.model.ContentWarning
import tachiyomi.domain.source.model.StubSource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun AnimeSource.getNameForAnimeInfo(): String {
    val preferences = Injekt.get<Context>().appGraph.sourcePreferences
    val enabledLanguages = preferences.enabledLanguages.get()
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        // For edge cases where user disables a source they got anime of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages -> toString()
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name
        else -> toString()
    }
}

fun AnimeSource.isLocalOrStub(): Boolean = isLocal() || this is StubSource

// AM (DISCORD_RPC) -->
suspend fun AnimeSource?.isNsfw(): Boolean {
    if (this == null || this.isLocalOrStub()) return false
    val sourceUsed = Injekt.get<Context>().appGraph.extensionManager.getLoadedExtensions()
        .find { ext -> ext.sources.any { it.id == this.id } }!!
    return sourceUsed.contentWarning != ContentWarning.SAFE
}
// <-- AM (DISCORD_RPC)

// AY -->
suspend fun AnimeSource?.isSourceForTorrents(): Boolean {
    if (this == null || this.isLocalOrStub()) return false
    val sourceUsed = Injekt.get<Context>().appGraph.extensionManager.getLoadedExtensions()
        .find { ext -> ext.sources.any { it.id == this.id } }!!
    return sourceUsed.isTorrent
}
// <-- AY
