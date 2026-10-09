package tachiyomi.domain.download.service

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Inject
@SingleIn(AppScope::class)
class DownloadPreferences(
    preferenceStore: PreferenceStore,
) {

    val downloadOnlyOverWifi: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_download_only_over_wifi_key",
        true,
    )

    // AM -->
    val ignoreBrokenTracks: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_download_ignore_broken_tracks",
        false,
    )

    val useInternalDownloader: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_download_use_internal",
        true,
    )

    val downloadThreads: Preference<Int> = preferenceStore.getInt(
        "pref_download_threads",
        4,
    )

    val downloadSpeedLimit: Preference<Int> = preferenceStore.getInt(
        "pref_download_speed",
        0,
    )
    // <-- AM

    // AY -->
    val useExternalDownloader: Preference<Boolean> = preferenceStore.getBoolean("use_external_downloader", false)

    val externalDownloaderSelection: Preference<String> = preferenceStore.getString(
        "external_downloader_selection",
        "",
    )
    // <-- AY

    val autoDownloadWhileWatching: Preference<Int> = preferenceStore.getInt("auto_download_while_watching", 0)

    val removeAfterSeenSlots: Preference<Int> = preferenceStore.getInt("remove_after_seen_slots", -1)

    val removeAfterMarkedAsSeen: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_remove_after_marked_as_seen_key",
        false,
    )

    val removeBookmarkedEpisodes: Preference<Boolean> = preferenceStore.getBoolean("pref_remove_bookmarked", false)

    // AY -->
    val downloadFillermarkedEpisodes: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_no_download_fillermarked",
        false,
    )
    // <-- AY

    val removeExcludeCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val downloadNewEpisodes: Preference<Boolean> = preferenceStore.getBoolean("download_new_episode", false)

    val downloadNewEpisodeCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val downloadNewEpisodeCategoriesExclude: Preference<Set<String>> = preferenceStore.getStringSet(
        DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        emptySet(),
    )

    val downloadNewUnseenEpisodesOnly: Preference<Boolean> = preferenceStore.getBoolean(
        "download_new_unseen_episodes_only",
        false,
    )

    val parallelSourceLimit: Preference<Int> = preferenceStore.getInt("download_parallel_source_limit", 5)

    companion object {
        private const val REMOVE_EXCLUDE_CATEGORIES_PREF_KEY = "remove_exclude_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_PREF_KEY = "download_new_categories"
        private const val DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY = "download_new_categories_exclude"
        val categoryPreferenceKeys = setOf(
            REMOVE_EXCLUDE_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_PREF_KEY,
            DOWNLOAD_NEW_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
