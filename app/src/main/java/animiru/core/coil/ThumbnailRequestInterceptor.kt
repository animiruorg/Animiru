package animiru.core.coil

import animiru.domain.episode.model.EpisodeThumbnail
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import eu.kanade.tachiyomi.data.coil.DeDupeConcurrentRequests

class ThumbnailRequestInterceptor : Interceptor {

    private val concurrentRequests = DeDupeConcurrentRequests<ImageResult> { it is SuccessResult }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        if (data !is EpisodeThumbnail) return chain.proceed()

        return concurrentRequests.apply(data to chain.size) { chain.proceed() }
    }
}
