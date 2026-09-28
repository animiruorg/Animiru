package animiru.core.coil

import animiru.domain.episode.model.EpisodeThumbnail
import coil3.key.Keyer
import coil3.request.Options

class EpisodeThumbnailKeyer : Keyer<EpisodeThumbnail> {
    override fun key(data: EpisodeThumbnail, options: Options): String? {
        return "episode;${data.url};${data.lastModified}"
    }
}
