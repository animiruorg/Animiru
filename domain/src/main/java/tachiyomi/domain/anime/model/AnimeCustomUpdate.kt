// AM -->
package tachiyomi.domain.anime.model

data class AnimeCustomUpdate(
    val id: Long,
    val customArtist: String?,
    val customAuthor: String?,
    val customDescription: String?,
    val customGenre: List<String>?,
    val customTitle: String?,
    val customStatus: Long?,
)
// <-- AM
