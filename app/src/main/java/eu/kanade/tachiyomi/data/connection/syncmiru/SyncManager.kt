// AM (SYNC) -->
package eu.kanade.tachiyomi.data.connection.syncmiru

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Inject
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.connection.SyncPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.connection.syncmiru.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.data.connection.syncmiru.service.SyncData
import eu.kanade.tachiyomi.data.connection.syncmiru.service.SyncYomiSyncService
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Episodes
import tachiyomi.data.anime.AnimeMapper.mapAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.episode.repository.EpisodeRepository
import java.io.File
import java.io.IOException
import java.util.Date
import kotlin.system.measureTimeMillis
import logcat.logcat as log

/**
 * A manager to handle synchronization tasks in the app, such as updating
 * sync preferences and performing synchronization with a remote server.
 *
 * @property context The application context.
 */
@Inject
class SyncManager(
    private val context: Context,
    private val updateAnime: UpdateAnime,
    private val animeRepository: AnimeRepository,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val syncPreferences: SyncPreferences,
    private val getCategories: GetCategories,
    private val backupCreatorFactory: BackupCreator.Factory,
    private val notifier: SyncNotifier,
    private val animeRestorer: AnimeRestorer,
) {
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Syncs data with a sync service.
     *
     * This function retrieves local data (favorites, anime, extensions, and categories)
     * from the database using the BackupManager, then synchronizes the data with a sync service.
     */
    suspend fun syncData() {
        val syncOptions = syncPreferences.getSyncSettings()
        val databaseAnime = getAllAnimeThatNeedsSync()

        val backupOptions = BackupOptions(
            libraryEntries = syncOptions.animelibEntries,
            categories = syncOptions.animeCategories,
            episodes = syncOptions.episodes,
            tracking = syncOptions.animeTracking,
            history = syncOptions.animeHistory,
            appSettings = syncOptions.appSettings,
            sourceSettings = syncOptions.sourceSettings,
            privateSettings = syncOptions.privateSettings,
            // AM (CUSTOM_INFORMATION) -->
            customInfo = syncOptions.customInfo,
            // <-- AM (CUSTOM_INFORMATION)
        )

        log(LogPriority.DEBUG) { "Begin create backup" }
        val backupCreator = backupCreatorFactory.create(isAutoBackup = false)
        val backupAnime = backupCreator.backupAnimes(databaseAnime, backupOptions)
        val backup = Backup(
            backupAnime = backupAnime,
            backupCategories = backupCreator.backupCategories(backupOptions),
            backupSources = backupCreator.backupSources(backupAnime),
            backupPreferences = backupCreator.backupAppPreferences(backupOptions),
            backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),
        )
        log(LogPriority.DEBUG) { "End create backup" }

        // Create the SyncData object
        val syncData = SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = backup,
        )

        // AM (SYNC_DRIVE) -->
        if (syncPreferences.googleDriveRefreshToken.get().isNotBlank()) {
            val syncService = GoogleDriveSyncService(context, json, syncPreferences)
            syncWithBackup(databaseAnime, backup, syncData, syncService.doSync(syncData))
        }
        // <-- AM (SYNC_DRIVE)

        // AM (SYNC_YOMI) -->
        if (syncPreferences.clientAPIKey.get().isNotBlank()) {
            val syncService = SyncYomiSyncService(context, json, syncPreferences, notifier)
            syncWithBackup(databaseAnime, backup, syncData, syncService.doSync(syncData))
        }
        // <-- AM (SYNC_YOMI)
    }

    private suspend fun syncWithBackup(
        databaseAnime: List<Anime>,
        backup: Backup,
        syncData: SyncData,
        remoteBackup: Backup?,
    ) {
        if (remoteBackup == null) {
            log(LogPriority.DEBUG) { "Skip restore due to network issues" }
            // should we call showSyncError?
            return
        }

        if (remoteBackup === syncData.backup) {
            // nothing changed
            syncPreferences.lastSyncTimestamp.set(Date().time)
            notifier.showSyncSuccess("Sync completed successfully")
            return
        }

        // Stop the sync early if the remote backup is null or empty
        if (remoteBackup.backupAnime.isEmpty()) {
            notifier.showSyncError("No data found on remote server.")
            return
        }

        // Check if it's first sync based on lastSyncTimestamp
        if (syncPreferences.lastSyncTimestamp.get() == 0L && databaseAnime.isNotEmpty()) {
            // It's first sync no need to restore data. (just update remote data)
            syncPreferences.lastSyncTimestamp.set(Date().time)
            notifier.showSyncSuccess("Updated remote data successfully")
            return
        }

        val (animeFilteredFavorites, animeNonFavorites) = animeFilterFavorites(remoteBackup)
        animeUpdateNonFavorites(animeNonFavorites)

        val newSyncData = backup.copy(
            backupAnime = animeFilteredFavorites,
            backupCategories = remoteBackup.backupCategories,
            backupSources = remoteBackup.backupSources,
            backupPreferences = remoteBackup.backupPreferences,
            backupSourcePreferences = remoteBackup.backupSourcePreferences,
        )

        // It's local sync no need to restore data. (just update remote data)
        if (animeFilteredFavorites.isEmpty()) {
            // update the sync timestamp
            syncPreferences.lastSyncTimestamp.set(Date().time)
            notifier.showSyncSuccess("Sync completed successfully")
            return
        }

        val backupUri = writeSyncDataToCache(context, newSyncData)
        logcat(LogPriority.DEBUG) { "Got Backup Uri: $backupUri" }
        if (backupUri != null) {
            BackupRestoreJob.start(
                context.workManager,
                backupUri,
                sync = true,
                options = RestoreOptions(
                    appSettings = true,
                    sourceSettings = true,
                    libraryEntries = true,
                    extensionStores = true,
                ),
            )

            // update the sync timestamp
            syncPreferences.lastSyncTimestamp.set(Date().time)
        } else {
            log(LogPriority.ERROR) { "Failed to write sync data to file" }
        }
    }

    private fun writeSyncDataToCache(context: Context, backup: Backup): Uri? {
        val cacheFile = File(context.cacheDir, "animiru_sync_data.proto.gz")
        return try {
            cacheFile.outputStream().use { output ->
                output.write(ProtoBuf.encodeToByteArray(Backup.serializer(), backup))
                Uri.fromFile(cacheFile)
            }
        } catch (e: IOException) {
            logcat(LogPriority.ERROR, throwable = e) { "Failed to write sync data to cache" }
            null
        }
    }

    /**
     * Retrieves all anime from the local database.
     *
     * @return a list of all anime stored in the database
     */
    private suspend fun getAllAnimeFromDB(): List<Anime> {
        return animeRepository.getAllAnime()
    }

    private suspend fun getAllAnimeThatNeedsSync(): List<Anime> {
        return animeRepository.getAllAnimeThatNeedsSync()
    }

    private suspend fun isAnimeDifferent(localAnime: Anime, remoteAnime: BackupAnime): Boolean {
        val localEpisodes = getEpisodesByAnimeId.await(localAnime.id, false)
        val localCategories = getCategories.await(localAnime.id).map { it.order }

        if (areEpisodesDifferent(localEpisodes, remoteAnime.episodes)) {
            return true
        }

        if (localCategories.toSet() != remoteAnime.categories.toSet()) {
            return true
        }

        return false
    }

    private fun areEpisodesDifferent(localEpisodes: List<Episode>, remoteEpisodes: List<BackupEpisode>): Boolean {
        val localEpisodeMap = localEpisodes.associateBy { it.url }
        val remoteEpisodeMap = remoteEpisodes.associateBy { it.url }

        if (localEpisodeMap.size != remoteEpisodeMap.size) {
            return true
        }

        for ((url, localEpisode) in localEpisodeMap) {
            val remoteEpisode = remoteEpisodeMap[url]

            // If a matching remote Episode doesn't exist, consider them different
            if (remoteEpisode == null) {
                return true
            }
        }

        return false
    }

    private suspend fun animeFilterFavorites(backup: Backup): Pair<List<BackupAnime>, List<BackupAnime>> {
        val favorites = mutableListOf<BackupAnime>()
        val nonFavorites = mutableListOf<BackupAnime>()
        val logTag = "filterFavoritesAndNonFavorites"

        val elapsedTimeMillis = measureTimeMillis {
            val databaseAnime = getAllAnimeFromDB()
            val localAnimeMap = databaseAnime.associateBy {
                Triple(it.source, it.url, it.title)
            }

            log(LogPriority.DEBUG, logTag) { "Starting to filter favorites and non-favorites from backup data." }

            backup.backupAnime.forEach { remoteAnime ->
                val compositeKey = Triple(remoteAnime.source, remoteAnime.url, remoteAnime.title)
                val localAnime = localAnimeMap[compositeKey]
                when {
                    // Checks if the anime is in favorites and needs updating or adding
                    remoteAnime.favorite -> {
                        if (localAnime == null || isAnimeDifferent(localAnime, remoteAnime)) {
                            log(LogPriority.DEBUG, logTag) { "Adding to favorites: ${remoteAnime.title}" }
                            favorites.add(remoteAnime)
                        } else {
                            log(LogPriority.DEBUG, logTag) { "Already up-to-date favorite: ${remoteAnime.title}" }
                        }
                    }
                    // Handle non-favorites
                    !remoteAnime.favorite -> {
                        log(LogPriority.DEBUG, logTag) { "Adding to non-favorites: ${remoteAnime.title}" }
                        nonFavorites.add(remoteAnime)
                    }
                }
            }
        }

        val minutes = elapsedTimeMillis / 60000
        val seconds = (elapsedTimeMillis % 60000) / 1000
        log(LogPriority.DEBUG, logTag) {
            "Filtering completed in ${minutes}m ${seconds}s. Favorites found: ${favorites.size}, " +
                "Non-favorites found: ${nonFavorites.size}"
        }

        return Pair(favorites, nonFavorites)
    }

    /**
     * Updates the non-favorite anime in the local database with their favorite status from the backup.
     * @param nonFavorites the list of non-favorite BackupAnime objects from the backup.
     */
    private suspend fun animeUpdateNonFavorites(nonFavorites: List<BackupAnime>) {
        val localAnimeList = getAllAnimeFromDB()

        val localAnimeMap = localAnimeList.associateBy { Triple(it.source, it.url, it.title) }

        nonFavorites.forEach { nonFavorite ->
            val key = Triple(nonFavorite.source, nonFavorite.url, nonFavorite.title)
            localAnimeMap[key]?.let { localAnime ->
                if (localAnime.favorite != nonFavorite.favorite) {
                    val favoriteAt = nonFavorite.dateAdded.takeIf { it != 0L }
                        ?: nonFavorite.favoriteModifiedAt?.times(1000L)
                    val updatedAnime = localAnime.copy(favoriteAt = favoriteAt)
                    animeRepository.updateAnime(updatedAnime)
                }
            }
        }
    }
}
// <-- AM (SYNC)
