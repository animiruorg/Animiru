package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import android.util.Log
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.download.DownloadCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.createFileInCacheDir
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.repository.RestoreRepository
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import java.io.File
import java.text.SimpleDateFormat
import java.util.BitSet
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
@AssistedInject
class BackupRestorer(
    @Assisted private val notifier: BackupNotifier,
    @Assisted private val isSync: Boolean,
    private val context: Context,

    private val downloadCache: DownloadCache,
    private val categoriesRestorer: CategoriesRestorer,
    private val preferenceRestorer: PreferenceRestorer,
    private val extensionStoreRestorer: ExtensionStoreRestorer,
    private val animeRestorer: AnimeRestorer,
    private val backupDecoder: BackupDecoder,
    private val sourceManager: SourceManager,
    private val stubSourceRepository: StubSourceRepository,
    // AY -->
    private val customButtonRestorer: CustomButtonRestorer,
    private val extensionsRestorer: ExtensionsRestorer,
    private val restoreRepository: RestoreRepository,
    // <-- AY
) {

    @AssistedFactory
    fun interface Factory {
        fun create(notifier: BackupNotifier, isSync: Boolean): BackupRestorer
    }

    private var restoreAmount = 0
    private val restoreProgress = AtomicInt(0)
    private val errors = CopyOnWriteArrayList<Pair<Date, String>>()

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        // Invalidate download cache to ensure UI reflects any restored downloads
        if (options.libraryEntries) {
            try {
                downloadCache.invalidateCache()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to invalidate download cache after restore" }
            }
        }

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val (entryCount, backup, allIds) = backupDecoder.decodeMetadata(uri)

        // Store source mapping for error messages
        val backupMaps = backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreSourceNames(backupMaps)
            restoreAmount += entryCount
        }
        if (options.categories) {
            restoreAmount += 1
        }
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionStores) {
            restoreAmount += backup.backupExtensionStores.size
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }
        // AY -->
        if (options.customButtons) {
            restoreAmount += 1
        }
        if (options.extensions) {
            restoreAmount += 1
        }
        // <-- AY

        coroutineScope {
            val restoreCategoriesJob = if (options.categories) {
                restoreCategories(backup.backupCategories)
            } else {
                null
            }
            if (options.appSettings) {
                restoreAppPreferences(
                    backup.backupPreferences,
                    backup.backupCategories.takeIf { options.categories },
                    restoreCategoriesJob,
                )
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                restoreAnime(
                    uri,
                    if (options.categories) backup.backupCategories else emptyList(),
                    // AM -->
                    allIds,
                    // <-- AM
                    restoreCategoriesJob,
                )
            }
            if (options.extensionStores) {
                restoreExtensionStores(backup.backupExtensionStores)
            }
            // AY -->
            if (options.customButtons) {
                restoreCustomButtons(backup.backupCustomButton)
            }
            if (options.extensions) {
                restoreExtensions(backup.backupExtensions)
            }
            // <-- AY

            // TODO: optionally trigger online library + tracker update
        }
    }

    // Without a stub, a source that isn't installed has no name for the next backup to write
    private suspend fun restoreSourceNames(backupSources: List<BackupSource>) {
        backupSources
            .filter { it.name.isNotBlank() }
            .filter {
                sourceManager.get(it.sourceId) == null && stubSourceRepository.getStubSource(it.sourceId) == null
            }
            .forEach { stubSourceRepository.upsertStubSource(it.sourceId, lang = "", name = it.name) }
    }

    private fun CoroutineScope.restoreCategories(backupCategories: List<BackupCategory>) = launch {
        ensureActive()
        categoriesRestorer(backupCategories)

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.categories),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreAnime(
        uri: Uri,
        backupCategories: List<BackupCategory>,
        // AM -->
        ids: HashMap<Long, Pair<Long, String>>,
        // <-- AM
        categoriesRestoreJob: Job?,
    ) = launch {
        categoriesRestoreJob?.join()
        // AM -->
        val urlsBySource = restoreRepository.getAnimeUrlsBySourceId()
        val isExisting: (BackupAnime) -> Boolean = { it.url in urlsBySource[it.source].orEmpty() }

        val backupAnimes = flow {
            val emittedIndices = BitSet()
            var currentParentIds = mutableSetOf<Long>()

            // Seasons are unlikely to run very deep, so checking 2 levels should be enough for most entries which means
            // it's probably fine to collect the rest in memory.
            val maxDepth = 2

            for (depth in 0..2) {
                val nextParentIds = mutableSetOf<Long>()
                val levelExistingIndices = BitSet()

                // New anime
                backupDecoder.decodeEntries(uri)
                    .withIndex()
                    .collect { (index, anime) ->
                        if (emittedIndices.get(index)) return@collect

                        val isMatch = if (depth == 0) anime.parentId == null else anime.parentId in currentParentIds
                        if (isMatch) {
                            if (isExisting(anime)) {
                                levelExistingIndices.set(index)
                            } else {
                                emit(anime)
                                emittedIndices.set(index)
                                anime.id?.let {
                                    nextParentIds.add(it)
                                }
                            }
                        }
                    }

                // Existing
                if (!levelExistingIndices.isEmpty) {
                    backupDecoder.decodeEntries(uri, levelExistingIndices::get)
                        .collect { anime ->
                            emit(anime)
                            anime.id?.let {
                                nextParentIds.add(it)
                            }
                        }
                }

                if (nextParentIds.isEmpty()) break // No seasons exists

                currentParentIds = nextParentIds
            }

            val remainingExistingIndices = BitSet()
            backupDecoder.decodeEntries(uri)
                .withIndex()
                .collect { (index, anime) ->
                    if (emittedIndices.get(index)) return@collect
                    if (isExisting(anime)) {
                        remainingExistingIndices.set(index)
                    } else {
                        emit(anime)
                        emittedIndices.set(index)
                    }
                }

            if (!remainingExistingIndices.isEmpty) {
                backupDecoder.decodeEntries(uri, remainingExistingIndices::get)
                    .collect { emit(it) }
            }
        }
        // <-- AM

        backupAnimes
            .onEach {
                Log.i("SOMETHING-restore", "${it.title} - ${it.episodes.joinToString(", ") { it.name }}")
            }
            .chunked(100)
            .collect { chunk ->
                val restoredAsBatch = try {
                    ensureActive()
                    animeRestorer.restore(
                        chunk,
                        backupCategories,
                        // AM -->
                        ids,
                        // <-- AM
                    )
                    true
                } catch (e: Exception) {
                    ensureActive()
                    logcat(LogPriority.WARN, e) { "Batch restore failed, retrying entry by entry" }
                    false
                }

                if (restoredAsBatch) {
                    restoreProgress.addAndFetch(chunk.size)
                } else {
                    chunk.forEach {
                        ensureActive()
                        try {
                            animeRestorer.restore(listOf(it), backupCategories, ids)
                        } catch (e: Exception) {
                            ensureActive()
                            val sourceName = sourceMapping[it.source] ?: it.source.toString()
                            errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                        }

                        restoreProgress.incrementAndFetch()
                    }
                }

                notifier.showRestoreProgress(chunk.last().title, restoreProgress.load(), restoreAmount, isSync)
            }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
        categoriesRestoreJob: Job?,
    ) = launch {
        categoriesRestoreJob?.join()
        ensureActive()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.app_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensionStores(
        backupExtensionStores: List<BackupExtensionStore>,
    ) = launch {
        backupExtensionStores
            .chunked(100)
            .forEach { chunk ->
                chunk.forEach {
                    ensureActive()

                    try {
                        extensionStoreRestorer(it)
                    } catch (e: Exception) {
                        errors.add(Date() to "Error Adding Repo: ${it.name} : ${e.message}")
                    }

                    restoreProgress.incrementAndFetch()
                }

                notifier.showRestoreProgress(
                    context.stringResource(MR.strings.extensionStores),
                    restoreProgress.load(),
                    restoreAmount,
                    isSync,
                )
            }
    }

    // AY -->
    private fun CoroutineScope.restoreCustomButtons(customButtons: List<BackupCustomButtons>) = launch {
        ensureActive()
        customButtonRestorer(customButtons)

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(AYMR.strings.custom_button_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }

    private fun CoroutineScope.restoreExtensions(extensions: List<BackupExtension>) = launch {
        ensureActive()
        extensionsRestorer.restoreExtensions(extensions)

        val progress = restoreProgress.incrementAndFetch()
        notifier.showRestoreProgress(
            context.stringResource(MR.strings.source_settings),
            progress,
            restoreAmount,
            isSync,
        )
    }
    // <-- AY

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("animiru_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) {
            // Empty
        }
        return File("")
    }
}
