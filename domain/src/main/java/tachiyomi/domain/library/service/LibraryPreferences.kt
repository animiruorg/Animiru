package tachiyomi.domain.library.service

import aniyomi.domain.anime.model.SeasonDisplayMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort

@Inject
@SingleIn(AppScope::class)
class LibraryPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val displayMode: Preference<LibraryDisplayMode> = preferenceStore.getObjectFromString(
        "pref_display_mode_library",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    val sortingMode: Preference<LibrarySort> = preferenceStore.getObjectFromString(
        "library_sorting_mode",
        LibrarySort.default,
        LibrarySort.Serializer::serialize,
        LibrarySort.Serializer::deserialize,
    )

    val randomSortSeed: Preference<Int> = preferenceStore.getInt("library_random_sort_seed", 0)

    val portraitColumns: Preference<Int> = preferenceStore.getInt("pref_library_columns_portrait_key", 0)

    val landscapeColumns: Preference<Int> = preferenceStore.getInt("pref_library_columns_landscape_key", 0)

    val lastUpdatedTimestamp: Preference<Long> = preferenceStore.getLong(
        Preference.appStateKey("library_update_last_timestamp"),
        0L,
    )
    val autoUpdateInterval: Preference<Int> = preferenceStore.getInt("pref_library_update_interval_key", 0)

    val autoUpdateDeviceRestrictions: Preference<Set<String>> = preferenceStore.getStringSet(
        "library_update_restriction",
        setOf(
            DEVICE_ONLY_ON_WIFI,
        ),
    )
    val autoUpdateAnimeRestrictions: Preference<Set<String>> = preferenceStore.getStringSet(
        "library_update_anime_restriction",
        setOf(
            ANIME_HAS_UNSEEN,
            ANIME_NON_COMPLETED,
            ANIME_NON_SEEN,
            ANIME_OUTSIDE_RELEASE_PERIOD,
        ),
    )

    val autoUpdateMetadata: Preference<Boolean> = preferenceStore.getBoolean("auto_update_metadata", false)

    val showContinueWatchingButton: Preference<Boolean> = preferenceStore.getBoolean(
        "display_continue_watching_button",
        false,
    )

    val markDuplicateSeenEpisodeAsSeen: Preference<Set<String>> = preferenceStore.getStringSet(
        "mark_duplicate_seen_episode_seen",
        emptySet(),
    )

    // region Filter

    val filterDownloaded: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_downloaded_v2",
        TriState.DISABLED,
    )

    val filterUnseen: Preference<TriState> = preferenceStore.getEnum("pref_filter_library_unseen_v2", TriState.DISABLED)

    val filterStarted: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_started_v2",
        TriState.DISABLED,
    )

    val filterBookmarked: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_bookmarked_v2",
        TriState.DISABLED,
    )

    // AY -->
    val filterFillermarked: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_fillermarked_v2",
        TriState.DISABLED,
    )
    // <-- AY

    val filterCompleted: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_completed_v2",
        TriState.DISABLED,
    )

    val filterIntervalCustom: Preference<TriState> = preferenceStore.getEnum(
        "pref_filter_library_interval_custom",
        TriState.DISABLED,
    )

    fun filterTracking(id: Int) = preferenceStore.getEnum(
        "pref_filter_library_tracked_${id}_v2",
        TriState.DISABLED,
    )

    // endregion

    // region Badges

    val downloadBadge: Preference<Boolean> = preferenceStore.getBoolean("display_download_badge", false)

    val unseenBadge: Preference<Boolean> = preferenceStore.getBoolean("display_unseen_badge", true)

    val localBadge: Preference<Boolean> = preferenceStore.getBoolean("display_local_badge", true)

    val languageBadge: Preference<Boolean> = preferenceStore.getBoolean("display_language_badge", false)

    val newShowUpdatesCount: Preference<Boolean> = preferenceStore.getBoolean("library_show_updates_count", true)
    val newUpdatesCount: Preference<Int> = preferenceStore.getInt(
        Preference.appStateKey("library_unseen_updates_count"),
        0,
    )

    // endregion

    // region Category

    val defaultCategory: Preference<Int> = preferenceStore.getInt(DEFAULT_CATEGORY_PREF_KEY, -1)

    val lastUsedCategory: Preference<Int> = preferenceStore.getInt(Preference.appStateKey("last_used_category"), 0)

    val categoryTabs: Preference<Boolean> = preferenceStore.getBoolean("display_category_tabs", true)

    val categoryNumberOfItems: Preference<Boolean> = preferenceStore.getBoolean("display_number_of_items", false)

    val categorizedDisplaySettings: Preference<Boolean> = preferenceStore.getBoolean("categorized_display", false)

    // AY -->
    val hideHiddenCategoriesSettings: Preference<Boolean> = preferenceStore.getBoolean("hidden_categories", false)
    // <-- AY

    val updateCategories: Preference<Set<String>> = preferenceStore.getStringSet(
        LIBRARY_UPDATE_CATEGORIES_PREF_KEY,
        emptySet(),
    )

    val updateCategoriesExclude: Preference<Set<String>> = preferenceStore.getStringSet(
        LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
        emptySet(),
    )

    // endregion

    // region Episode

    val filterEpisodeBySeen: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_seen",
        Anime.SHOW_ALL,
    )

    val filterEpisodeByDownloaded: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_downloaded",
        Anime.SHOW_ALL,
    )

    val filterEpisodeByBookmarked: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_bookmarked",
        Anime.SHOW_ALL,
    )

    // AY-->
    val filterEpisodeByFillermarked: Preference<Long> = preferenceStore.getLong(
        "default_episode_filter_by_fillermarked",
        Anime.SHOW_ALL,
    )
    // <-- AY

    // and upload date
    val sortEpisodeBySourceOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_episode_sort_by_source_or_number",
        Anime.EPISODE_SORTING_SOURCE,
    )

    val displayEpisodeByNameOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_episode_display_by_name_or_number",
        Anime.EPISODE_DISPLAY_NAME,
    )

    val sortEpisodeByAscendingOrDescending: Preference<Long> = preferenceStore.getLong(
        "default_episode_sort_by_ascending_or_descending",
        Anime.EPISODE_SORT_DESC,
    )

    // AY -->
    val showEpisodeThumbnailPreviews: Preference<Long> = preferenceStore.getLong(
        "default_episode_show_thumbnail_previews",
        Anime.EPISODE_SHOW_PREVIEWS,
    )

    val showEpisodeSummaries: Preference<Long> = preferenceStore.getLong(
        "default_episode_show_summaries",
        Anime.EPISODE_SHOW_SUMMARIES,
    )
    // <-- AY

    // AM -->
    // 0-3 -> 0.2x - 0.8x
    // 4 -> 1x
    // 5-9 -> 1.2x-2x
    val episodeThumbnailSize: Preference<Int> = preferenceStore.getInt(
        "default_episode_thumbnail_size",
        4,
    )
    // <-- AM

    fun setEpisodeSettingsDefault(anime: Anime) {
        filterEpisodeBySeen.set(anime.unseenFilterRaw)
        filterEpisodeByDownloaded.set(anime.downloadedFilterRaw)
        filterEpisodeByBookmarked.set(anime.bookmarkedFilterRaw)
        // AY -->
        filterEpisodeByFillermarked.set(anime.fillermarkedFilterRaw)
        // <-- AY
        sortEpisodeBySourceOrNumber.set(anime.sorting)
        displayEpisodeByNameOrNumber.set(anime.displayMode)
        sortEpisodeByAscendingOrDescending.set(
            if (anime.sortDescending()) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC,
        )
        // AY -->
        showEpisodeThumbnailPreviews.set(anime.showPreviewsRaw)
        showEpisodeSummaries.set(anime.showSummariesRaw)
        // <-- AY
        // AM -->
        episodeThumbnailSize.set(anime.episodeThumbnailSize)
        // <-- AM
    }

    val hideMissingEpisodes: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_hide_missing_episode_indicators",
        false,
    )
    // endregion

    // AY -->
    // Seasons

    val filterSeasonByDownload: Preference<Long> = preferenceStore.getLong(
        "default_season_filter_by_downloaded",
        Anime.SHOW_ALL,
    )

    val filterSeasonByUnseen: Preference<Long> = preferenceStore.getLong(
        "default_season_filter_by_unseen",
        Anime.SHOW_ALL,
    )

    val filterSeasonByStarted: Preference<Long> = preferenceStore.getLong(
        "default_season_filter_by_started",
        Anime.SHOW_ALL,
    )

    val filterSeasonByCompleted: Preference<Long> = preferenceStore.getLong(
        "default_season_filter_by_completed",
        Anime.SHOW_ALL,
    )

    val filterSeasonByBookmarked: Preference<Long> = preferenceStore.getLong(
        "default_season_filter_by_bookmarked",
        Anime.SHOW_ALL,
    )

    val filterSeasonByFillermarked: Preference<Long> = preferenceStore.getLong(
        "default_season_filter_by_fillermarked",
        Anime.SHOW_ALL,
    )

    val sortSeasonBySourceOrNumber: Preference<Long> = preferenceStore.getLong(
        "default_season_sort_by_source_or_number",
        Anime.SEASON_SORT_SOURCE,
    )

    val sortSeasonByAscendingOrDescending: Preference<Long> = preferenceStore.getLong(
        "default_season_sort_by_ascending_or_descending",
        Anime.SEASON_SORT_DESC,
    )

    val seasonDisplayGridMode: Preference<Long> = preferenceStore.getLong(
        "default_season_grid_display_mode",
        SeasonDisplayMode.toLong(SeasonDisplayMode.CompactGrid),
    )

    val seasonDisplayGridSize: Preference<Int> = preferenceStore.getInt(
        "default_season_grid_display_size",
        0,
    )

    val seasonDownloadOverlay: Preference<Boolean> = preferenceStore.getBoolean(
        "default_season_download_overlay",
        false,
    )

    val seasonUnseenOverlay: Preference<Boolean> = preferenceStore.getBoolean(
        "default_season_unseen_overlay",
        true,
    )

    val seasonLocalOverlay: Preference<Boolean> = preferenceStore.getBoolean(
        "default_season_local_overlay",
        true,
    )

    val seasonLangOverlay: Preference<Boolean> = preferenceStore.getBoolean(
        "default_season_lang_overlay",
        false,
    )

    val seasonContinueOverlay: Preference<Boolean> = preferenceStore.getBoolean(
        "default_season_continue_overlay",
        true,
    )

    val seasonDisplayMode: Preference<Long> = preferenceStore.getLong(
        "default_season_display_mode",
        Anime.SEASON_DISPLAY_MODE_SOURCE,
    )

    fun setSeasonSettingsDefault(anime: Anime) {
        filterSeasonByDownload.set(anime.seasonUnseenFilterRaw)
        filterSeasonByUnseen.set(anime.seasonUnseenFilterRaw)
        filterSeasonByStarted.set(anime.seasonStartedFilterRaw)
        filterSeasonByCompleted.set(anime.seasonCompletedFilterRaw)
        filterSeasonByBookmarked.set(anime.seasonBookmarkedFilterRaw)
        filterSeasonByFillermarked.set(anime.seasonFillermarkedFilterRaw)
        sortSeasonBySourceOrNumber.set(anime.seasonSorting)
        sortSeasonByAscendingOrDescending.set(
            if (anime.seasonSortDescending()) Anime.SEASON_SORT_DESC else Anime.SEASON_SORT_ASC,
        )
        seasonDisplayGridMode.set(SeasonDisplayMode.toLong(anime.seasonDisplayGridMode))
        seasonDisplayGridSize.set(anime.seasonDisplayGridSize)
        seasonDownloadOverlay.set(anime.seasonDownloadedOverlay)
        seasonUnseenOverlay.set(anime.seasonUnseenOverlay)
        seasonLocalOverlay.set(anime.seasonLocalOverlay)
        seasonLangOverlay.set(anime.seasonLangOverlay)
        seasonContinueOverlay.set(anime.seasonContinueOverlay)
        seasonDisplayMode.set(anime.seasonDisplayMode)
    }

    // Season behavior

    val updateSeasonOnRefresh: Preference<Boolean> = preferenceStore.getBoolean("pref_update_season_on_refresh", false)

    val updateSeasonOnLibraryUpdate: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_update_season_on_library_update",
        false,
    )
    // <-- AY

    // region Swipe Actions

    val swipeToStartAction: Preference<EpisodeSwipeAction> = preferenceStore.getEnum(
        "pref_episode_swipe_end_action",
        EpisodeSwipeAction.ToggleBookmark,
    )

    val swipeToEndAction: Preference<EpisodeSwipeAction> = preferenceStore.getEnum(
        "pref_episode_swipe_start_action",
        EpisodeSwipeAction.ToggleSeen,
    )

    val updateAnimeTitles: Preference<Boolean> = preferenceStore.getBoolean("pref_update_library_anime_titles", false)

    val disallowNonAsciiFilenames: Preference<Boolean> = preferenceStore.getBoolean(
        "disallow_non_ascii_filenames",
        false,
    )

    // endregion

    enum class EpisodeSwipeAction {
        ToggleSeen,
        ToggleBookmark,

        // AY -->
        ToggleFillermark,

        // <-- AY
        Download,
        Disabled,
    }

    // AM (GROUPING) -->
    val groupLibraryUpdateType: Preference<GroupLibraryMode> = preferenceStore.getEnum(
        "group_library_update_type",
        GroupLibraryMode.GLOBAL,
    )

    val groupLibraryBy: Preference<Int> = preferenceStore.getInt("group_library_by", LibraryGroup.BY_DEFAULT)
    // <-- AM (GROUPING)

    companion object {
        const val DEVICE_ONLY_ON_WIFI = "wifi"
        const val DEVICE_NETWORK_NOT_METERED = "network_not_metered"
        const val DEVICE_CHARGING = "ac"

        const val ANIME_NON_COMPLETED = "anime_ongoing"
        const val ANIME_HAS_UNSEEN = "anime_fully_seen"
        const val ANIME_NON_SEEN = "anime_started"
        const val ANIME_OUTSIDE_RELEASE_PERIOD = "anime_outside_release_period"

        const val MARK_DUPLICATE_EPISODE_SEEN_NEW = "new"
        const val MARK_DUPLICATE_EPISODE_SEEN_EXISTING = "existing"

        const val DEFAULT_CATEGORY_PREF_KEY = "default_category"
        private const val LIBRARY_UPDATE_CATEGORIES_PREF_KEY = "library_update_categories"
        private const val LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY = "library_update_categories_exclude"
        val categoryPreferenceKeys = setOf(
            DEFAULT_CATEGORY_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_PREF_KEY,
            LIBRARY_UPDATE_CATEGORIES_EXCLUDE_PREF_KEY,
        )
    }
}
