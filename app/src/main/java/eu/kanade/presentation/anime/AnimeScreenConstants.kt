package eu.kanade.presentation.anime

enum class DownloadAction {
    NEXT_1_EPISODE,
    NEXT_5_EPISODES,
    NEXT_10_EPISODES,
    NEXT_25_EPISODES,
    UNSEEN_EPISODES,
    BOOKMARKED_EPISODES,
}

enum class EditCoverAction {
    EDIT,
    DELETE,
}

enum class AnimeScreenItem {
    INFO_BOX,
    ACTION_ROW,
    DESCRIPTION_WITH_TAG,
    EPISODE_HEADER,

    // AM -->
    EPISODE_MISSING_COUNT,
    SIMPLE_EPISODE,
    THUMBNAIL_EPISODE,
    SUMMARY_EPISODE,
    // <-- AM

    // AY -->
    AIRING_TIME,
    // <-- AY
}
