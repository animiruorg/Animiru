package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.source.service.SourceManager
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

@OptIn(ExperimentalAtomicApi::class)
class MetadataUpdateWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var sourceManager: SourceManager

    @Inject private lateinit var getLibraryAnime: GetLibraryAnime

    @Inject private lateinit var updateAnimeFromRemote: UpdateAnimeFromRemote

    @Inject private lateinit var notifier: LibraryUpdateNotifier

    private var animeToUpdate: List<LibraryAnime> = mutableListOf()

    override suspend fun doWork(): Result {
        graph.inject(this)

        setForegroundSafely()

        addAnimeToQueue()

        return withContext(Dispatchers.IO) {
            try {
                updateMetadata()
                Result.success()
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Assume success although cancelled
                    Result.success()
                } else {
                    logcat(LogPriority.ERROR, e)
                    Result.failure()
                }
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    /**
     * Adds list of anime to be updated.
     */
    private suspend fun addAnimeToQueue() {
        animeToUpdate = getLibraryAnime.await()
        notifier.showQueueSizeWarningNotificationIfNeeded(animeToUpdate)
    }

    private suspend fun updateMetadata() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInt(0)
        val currentlyUpdatingAnime = CopyOnWriteArrayList<Anime>()

        coroutineScope {
            animeToUpdate.groupBy { it.anime.source }
                .values
                .map { animeInSource ->
                    async {
                        semaphore.withPermit {
                            animeInSource.forEach { libraryAnime ->
                                val anime = libraryAnime.anime
                                ensureActive()

                                withUpdateNotification(
                                    currentlyUpdatingAnime,
                                    progressCount,
                                    anime,
                                ) {
                                    val source = sourceManager.get(anime.source) ?: return@withUpdateNotification
                                    try {
                                        updateAnimeFromRemote.awaitEpisodesUpdate(
                                            source = source,
                                            anime = anime,
                                            fetchDetails = true,
                                        ).getOrThrow()
                                    } catch (e: Throwable) {
                                        // Ignore errors and continue
                                        logcat(LogPriority.ERROR, e)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()
    }

    private suspend fun withUpdateNotification(
        updatingAnime: CopyOnWriteArrayList<Anime>,
        completed: AtomicInt,
        anime: Anime,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingAnime.add(anime)
        notifier.showProgressNotification(
            updatingAnime,
            completed.load(),
            animeToUpdate.size,
        )

        block()

        ensureActive()

        updatingAnime.remove(anime)
        completed.fetchAndIncrement()
        notifier.showProgressNotification(
            updatingAnime,
            completed.load(),
            animeToUpdate.size,
        )
    }

    companion object {
        private const val TAG = "MetadataUpdate"
        private const val WORK_NAME_MANUAL = "MetadataUpdate"

        private const val ANIME_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        suspend fun startNow(workManager: WorkManager): Boolean {
            if (workManager.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }
            val request = OneTimeWorkRequestBuilder<MetadataUpdateWorker>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }
    }
}
