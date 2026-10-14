package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.track.TrackStatus
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.nullIfBlank
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import mihon.domain.episode.interactor.FilterEpisodesForDownload
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.createFileInCacheDir
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.FetchInterval
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.model.NoEpisodesException
import tachiyomi.domain.library.model.GroupLibraryMode
import tachiyomi.domain.library.model.LibraryAnime
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_HAS_UNSEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_NON_SEEN
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ANIME_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock

@OptIn(ExperimentalAtomicApi::class)
class LibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var sourceManager: SourceManager

    @Inject private lateinit var libraryPreferences: LibraryPreferences

    @Inject private lateinit var downloadManager: DownloadManager

    @Inject private lateinit var getLibraryAnime: GetLibraryAnime

    @Inject private lateinit var getAnime: GetAnime

    @Inject private lateinit var fetchInterval: FetchInterval

    @Inject private lateinit var filterEpisodesForDownload: FilterEpisodesForDownload

    @Inject private lateinit var updateAnimeFromRemote: UpdateAnimeFromRemote

    @Inject private lateinit var notifier: LibraryUpdateNotifier

    // AY -->
    @Inject private lateinit var getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId
    // <-- AY

    // AM (GROUPING) -->
    @Inject private lateinit var getTracks: GetTracks

    @Inject private lateinit var trackerManager: TrackerManager
    // <-- AM (GROUPING)

    private var animeToUpdate: List<LibraryAnime> = mutableListOf()

    override suspend fun doWork(): Result {
        graph.inject(this)

        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val restrictions = libraryPreferences.autoUpdateDeviceRestrictions.get()
                if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
            }

            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        setForegroundSafely()

        libraryPreferences.lastUpdatedTimestamp.set(Clock.System.now().toEpochMilliseconds())

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        // AM (GROUPING) -->
        val group = inputData.getInt(KEY_GROUP, LibraryGroup.BY_DEFAULT)
        val groupExtra = inputData.getString(KEY_GROUP_EXTRA)
        addAnimeToQueue(categoryId, group, groupExtra)
        // <-- AM (GROUPING)

        return withIOContext {
            try {
                updateEpisodeList()
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
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private suspend fun addAnimeToQueue(
        categoryId: Long,
        // AM (GROUPING) -->
        group: Int,
        groupExtra: String?,
        // <-- AM (GROUPING)
    ) {
        val libraryAnime = getLibraryAnime.await()

        // AM (GROUPING) -->
        val groupLibraryUpdateType = libraryPreferences.groupLibraryUpdateType.get()

        val listToUpdate = if (categoryId != -1L) {
            libraryAnime.filter { categoryId in it.categories }
        } else if (
            group == LibraryGroup.BY_DEFAULT ||
            groupLibraryUpdateType == GroupLibraryMode.GLOBAL ||
            (groupLibraryUpdateType == GroupLibraryMode.ALL_BUT_UNGROUPED && group == LibraryGroup.UNGROUPED)
        ) {
            val includedCategories = libraryPreferences.updateCategories.get().map { it.toLong() }
            val excludedCategories = libraryPreferences.updateCategoriesExclude.get().map { it.toLong() }

            libraryAnime.filter {
                val included = includedCategories.isEmpty() || it.categories.intersect(includedCategories).isNotEmpty()
                val excluded = it.categories.intersect(excludedCategories).isNotEmpty()
                included && !excluded
            }
        } else {
            when (group) {
                LibraryGroup.BY_TRACK_STATUS -> {
                    val trackingExtra = groupExtra?.toIntOrNull() ?: -1
                    val tracks = runBlocking { getTracks.await() }.groupBy { it.animeId }

                    libraryAnime.filter { (anime) ->
                        val status = tracks[anime.id]?.firstNotNullOfOrNull { track ->
                            TrackStatus.parseTrackerStatus(trackerManager, track.trackerId, track.status)
                        } ?: TrackStatus.OTHER
                        status.int == trackingExtra
                    }
                }
                LibraryGroup.BY_SOURCE -> {
                    val sourceExtra = groupExtra?.nullIfBlank()?.toIntOrNull()
                    val source = libraryAnime.map { it.anime.source }
                        .distinct()
                        .sorted()
                        .getOrNull(sourceExtra ?: -1)

                    if (source != null) libraryAnime.filter { it.anime.source == source } else emptyList()
                }
                LibraryGroup.BY_STATUS -> {
                    val statusExtra = groupExtra?.toLongOrNull() ?: -1
                    libraryAnime.filter {
                        it.anime.status == statusExtra
                    }
                }
                LibraryGroup.UNGROUPED -> libraryAnime
                else -> libraryAnime
            }
        }
        // <-- AM (GROUPING)

        // AY -->
        val includeSeasons = libraryPreferences.updateSeasonOnLibraryUpdate.get()
        val lastToUpdateWithSeasons = listToUpdate.flatMap { libAnime ->
            when (libAnime.anime.fetchType) {
                FetchType.Seasons -> {
                    if (includeSeasons) {
                        val seasons = getAnimeSeasonsByParentId.await(libAnime.anime.id)
                        seasons
                            .filter { s ->
                                s.anime.fetchType == FetchType.Episodes && !s.anime.favorite
                            }
                            .map { it.toLibraryAnime() }
                    } else {
                        emptyList()
                    }
                }
                FetchType.Episodes -> listOf(libAnime)
            }
        }
        // <-- AY

        val restrictions = libraryPreferences.autoUpdateAnimeRestrictions.get()
        val skippedUpdates = mutableListOf<Pair<Anime, String?>>()
        val timeZone = TimeZone.currentSystemDefault()
        val (_, fetchWindowUpperBound) = fetchInterval.getWindow(
            Clock.System.now().toLocalDateTime(timeZone).date,
            timeZone,
        )

        animeToUpdate = lastToUpdateWithSeasons
            .filter {
                when {
                    it.anime.updateStrategy == AnimeUpdateStrategy.ONLY_FETCH_ONCE && it.totalCount > 0L -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ANIME_NON_COMPLETED in restrictions && it.anime.status.toInt() == SAnime.COMPLETED -> {
                        skippedUpdates.add(it.anime to context.stringResource(MR.strings.skipped_reason_completed))
                        false
                    }

                    ANIME_HAS_UNSEEN in restrictions && it.unseenCount != 0L -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(AMMR.strings.skipped_reason_not_caught_up_anime),
                        )
                        false
                    }

                    ANIME_NON_SEEN in restrictions && it.totalCount > 0L && !it.hasStarted -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(AMMR.strings.skipped_reason_not_started_anime),
                        )
                        false
                    }

                    ANIME_OUTSIDE_RELEASE_PERIOD in restrictions && it.anime.nextUpdate > fetchWindowUpperBound -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_in_release_period),
                        )
                        false
                    }

                    else -> true
                }
            }
            .sortedBy { it.anime.title }

        notifier.showQueueSizeWarningNotificationIfNeeded(animeToUpdate)

        if (skippedUpdates.isNotEmpty()) {
            // TODO: surface skipped reasons to user?
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    /**
     * Method that updates anime in [animeToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each anime it calls [updateAnime] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun updateEpisodeList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInt(0)
        val currentlyUpdatingAnime = CopyOnWriteArrayList<Anime>()
        val newUpdates = CopyOnWriteArrayList<Pair<Anime, Array<Episode>>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Anime, String?>>()
        val hasDownloads = AtomicBoolean(false)
        val timeZone = TimeZone.currentSystemDefault()
        val fetchWindow = fetchInterval.getWindow(Clock.System.now().toLocalDateTime(timeZone).date, timeZone)

        coroutineScope {
            animeToUpdate.groupBy { it.anime.source }.values
                .map { animeInSource ->
                    async {
                        semaphore.withPermit {
                            animeInSource.forEach { libraryAnime ->
                                val anime = libraryAnime.anime
                                ensureActive()

                                // Don't continue to update if anime is not in library
                                // AY -->
                                if (anime.parentId == null && getAnime.await(anime.id)?.favorite != true) {
                                    // <-- AY
                                    return@forEach
                                }

                                withUpdateNotification(
                                    currentlyUpdatingAnime,
                                    progressCount,
                                    anime,
                                ) {
                                    try {
                                        val newEpisodes = updateAnime(anime, fetchWindow)
                                            .sortedByDescending { it.sourceOrder }

                                        if (newEpisodes.isNotEmpty()) {
                                            val episodesToDownload = filterEpisodesForDownload.await(anime, newEpisodes)

                                            if (episodesToDownload.isNotEmpty()) {
                                                downloadEpisodes(anime, episodesToDownload)
                                                hasDownloads.store(true)
                                            }

                                            libraryPreferences.newUpdatesCount.getAndSet { it + newEpisodes.size }

                                            // Convert to the anime that contains new episodes
                                            newUpdates.add(anime to newEpisodes.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        val errorMessage = when (e) {
                                            is NoEpisodesException -> context.stringResource(
                                                AYMR.strings.no_episodes_error,
                                            )
                                            // failedUpdates will already have the source, don't need to copy it into the message
                                            is SourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )
                                            else -> e.message
                                        }
                                        failedUpdates.add(anime to errorMessage)
                                    }
                                }
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.load()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates.size,
                errorFile.getUriCompat(context),
            )
        }
    }

    private suspend fun downloadEpisodes(anime: Anime, episodes: List<Episode>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadEpisodes(anime, episodes, false)
    }

    /**
     * Updates the episodes for the given anime and adds them to the database.
     *
     * @param anime the anime to update.
     * @return a pair of the inserted and removed episodes.
     */
    private suspend fun updateAnime(anime: Anime, fetchWindow: Pair<Long, Long>): List<Episode> {
        val source = sourceManager.getOrStub(anime.source)

        val update = updateAnimeFromRemote.awaitEpisodesUpdate(
            source = source,
            anime = anime,
            fetchDetails = libraryPreferences.autoUpdateMetadata.get(),
            fetchEpisodes = true,
            fetchWindow = fetchWindow,
        )
            .getOrThrow()

        return if (update.anime.favorite) update.newEpisodes else emptyList()
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
        completed.incrementAndFetch()
        notifier.showProgressNotification(
            updatingAnime,
            completed.load(),
            animeToUpdate.size,
        )
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private suspend fun writeErrorFile(errors: List<Pair<Anime, String?>>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("animiru_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n")
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Anime
                    errors.groupBy({ it.second }, { it.first }).forEach { (error, animes) ->
                        out.write("\n! ${error}\n")
                        animes.groupBy { it.source }.forEach { (srcId, animes) ->
                            val source = sourceManager.getOrStub(srcId)
                            out.write("  # $source\n")
                            animes.forEach { anime ->
                                out.write("    - ${anime.title}\n")
                            }
                        }
                    }
                }
                return file
            }
        } catch (_: Exception) {}
        return File("")
    }

    companion object {
        private const val TAG = "LibraryUpdate"
        private const val WORK_NAME_AUTO = "LibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "LibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://aniyomi.org/docs/guides/troubleshooting/"

        private const val ANIME_PER_SOURCE_QUEUE_WARNING_THRESHOLD = 60

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "category"

        // AM (GROUPING) -->

        /**
         * Key for group to update.
         */
        const val KEY_GROUP = "group"
        const val KEY_GROUP_EXTRA = "group_extra"
        // <-- AM (GROUPING)

        // AM -->
        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }
        // <-- AM

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = context.appGraph.libraryPreferences
            val interval = prefInterval ?: preferences.autoUpdateInterval.get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions.get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequest = NetworkRequest.Builder().apply {
                    removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    if (DEVICE_ONLY_ON_WIFI in restrictions) {
                        addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    }
                    if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                        addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                    }
                }
                    .build()
                val constraints = Constraints.Builder()
                    // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                    .setRequiredNetworkRequest(networkRequest, networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<LibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
        }

        fun startNow(
            context: Context,
            category: Category? = null,
            // AM (GROUPING) -->
            group: Int = LibraryGroup.BY_DEFAULT,
            groupExtra: String? = null,
            // <-- AM (GROUPING)
        ): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val inputData = workDataOf(
                KEY_CATEGORY to category?.id,
                // AM (GROUPING) -->
                KEY_GROUP to group,
                KEY_GROUP_EXTRA to groupExtra,
                // <-- AM (GROUPING)
            )

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            wm.getWorkInfos(workQuery).get()
                // Should only return one work but just in case
                .forEach {
                    wm.cancelWorkById(it.id)

                    // Re-enqueue cancelled scheduled work
                    if (it.tags.contains(WORK_NAME_AUTO)) {
                        setupTask(context)
                    }
                }
        }
    }
}
