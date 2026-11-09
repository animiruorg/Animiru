package aniyomi.domain.anime.model

import androidx.compose.runtime.Stable
import tachiyomi.domain.anime.model.Anime

@Stable
data class AnimeRelationGroup(
    val name: String,
    val anime: List<Anime>,
)
