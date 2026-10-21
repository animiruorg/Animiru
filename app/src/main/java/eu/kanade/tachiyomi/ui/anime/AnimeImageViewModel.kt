package eu.kanade.tachiyomi.ui.anime

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.util.editBackground
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.image.LocalBackgroundManager
import tachiyomi.source.local.image.LocalCoverManager
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class AnimeImageViewModel(
    @Assisted private val animeId: Long,
    private val getAnime: GetAnime,
    private val imageSaver: ImageSaver,
    private val coverCache: CoverCache,
    // AY -->
    private val backgroundCache: BackgroundCache,
    private val backgroundManager: LocalBackgroundManager,
    // <-- AY
    private val updateAnime: UpdateAnime,
    private val coverManager: LocalCoverManager,

    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    // AY -->
    val pagerState: PagerState = PagerState(pageCount = { 2 }),
    // <-- AY
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(animeId: Long): AnimeImageViewModel
    }

    // AY -->
    private val isCover: Boolean
        get() = pagerState.currentPage != 1
    // <-- AY

    val state: StateFlow<Anime?> = getAnime.subscribe(animeId)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), null)

    fun saveImage(context: Context) {
        // AY -->
        val savedStringResource = if (isCover) {
            MR.strings.cover_saved
        } else {
            AYMR.strings.background_saved
        }
        val errorSavingStringResource = if (isCover) {
            MR.strings.error_saving_cover
        } else {
            AYMR.strings.error_saving_background
        }
        // <-- AY
        viewModelScope.launch {
            try {
                saveImageInternal(context, temp = false)
                snackbarHostState.showSnackbar(
                    context.stringResource(savedStringResource),
                    withDismissAction = true,
                )
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(errorSavingStringResource),
                    withDismissAction = true,
                )
            }
        }
    }

    fun shareImage(context: Context) {
        // AY -->
        val errorSharingStringResource = if (isCover) {
            MR.strings.error_sharing_cover
        } else {
            AYMR.strings.error_sharing_background
        }
        // <-- AY
        viewModelScope.launch {
            try {
                val uri = saveImageInternal(context, temp = true) ?: return@launch
                withContext(Dispatchers.Main) {
                    context.startActivity(uri.toShareIntent(context))
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
                snackbarHostState.showSnackbar(
                    context.stringResource(errorSharingStringResource),
                    withDismissAction = true,
                )
            }
        }
    }

    /**
     * Save anime image Bitmap to picture or temporary share directory.
     *
     * @param context The context for building and executing the ImageRequest
     * @return the uri to saved file
     */
    private suspend fun saveImageInternal(context: Context, temp: Boolean): Uri? {
        val anime = state.value ?: return null
        val req = ImageRequest.Builder(context)
            .data(anime)
            .size(Size.ORIGINAL)
            .build()

        return withContext(Dispatchers.IO) {
            val result = context.imageLoader.execute(req).image?.asDrawable(context.resources)

            // TODO: Handle animated image
            val bitmap = result?.getBitmapOrNull() ?: return@withContext null
            imageSaver.save(
                Image.Cover(
                    bitmap = bitmap,
                    // AY -->
                    name = if (isCover) "${anime.title}-cover" else "${anime.title}-background",
                    // <-- AY
                    location = if (temp) Location.Cache else Location.Pictures.create(),
                ),
            )
        }
    }

    /**
     * Update image with local file.
     *
     * @param context Context.
     * @param data uri of the image resource.
     */
    fun editImage(context: Context, data: Uri) {
        val anime = state.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            context.contentResolver.openInputStream(data)?.use {
                try {
                    if (isCover) {
                        anime.editCover(coverManager, it, updateAnime, coverCache)
                    } else {
                        // AY -->
                        anime.editBackground(backgroundManager, it, updateAnime, backgroundCache)
                        // <-- AY
                    }
                    notifyImageUpdated(context)
                } catch (e: Exception) {
                    notifyFailedImageUpdate(context, e)
                }
            }
        }
    }

    fun deleteCustomImage(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isCover) {
                    coverCache.deleteCustomCover(animeId)
                    updateAnime.awaitUpdateCoverLastModified(animeId)
                } else {
                    backgroundCache.deleteCustomBackground(animeId)
                    updateAnime.awaitUpdateBackgroundLastModified(animeId)
                }
                notifyImageUpdated(context)
            } catch (e: Exception) {
                notifyFailedImageUpdate(context, e)
            }
        }
    }

    fun addToLibraryWarning(context: Context) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.notification_first_add_to_library),
                withDismissAction = true,
            )
        }
    }

    private fun notifyImageUpdated(context: Context) {
        // AY -->
        val updatedStringResource = if (isCover) {
            MR.strings.cover_updated
        } else {
            AYMR.strings.background_updated
        }
        // <-- AY
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(updatedStringResource),
                withDismissAction = true,
            )
        }
    }

    private fun notifyFailedImageUpdate(context: Context, e: Throwable) {
        // AY -->
        val updateFailedStringResource = if (isCover) {
            MR.strings.notification_cover_update_failed
        } else {
            AYMR.strings.notification_background_update_failed
        }
        // <-- AY
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                context.stringResource(updateFailedStringResource),
                withDismissAction = true,
            )
            logcat(LogPriority.ERROR, e)
        }
    }
}
