package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.CustomButtonBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionsBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetFavorites
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.i18n.MR
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock

@AssistedInject
class BackupCreator(
    @Assisted private val isAutoBackup: Boolean,
    private val context: Context,
    private val parser: ProtoBuf,
    private val getFavorites: GetFavorites,
    private val backupPreferences: BackupPreferences,
    private val animeRepository: AnimeRepository,
    private val categoriesBackupCreator: CategoriesBackupCreator,
    private val animeBackupCreator: AnimeBackupCreator,
    private val preferenceBackupCreator: PreferenceBackupCreator,
    private val extensionStoresBackupCreator: ExtensionStoresBackupCreator,
    private val sourcesBackupCreator: SourcesBackupCreator,
    private val backupFileValidator: BackupFileValidator,
    // AY -->
    private val customButtonBackupCreator: CustomButtonBackupCreator,
    private val extensionsBackupCreator: ExtensionsBackupCreator,
    // <-- AY
) {
    @AssistedFactory
    fun interface Factory {
        fun create(isAutoBackup: Boolean): BackupCreator
    }

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = if (isAutoBackup) {
                // Get dir of file and create
                val dir = UniFile.fromUri(context, uri)

                // Delete older backups
                dir?.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS - 1)
                    .forEach { it.delete() }

                // Create new file to place backup
                dir?.createFile(getFilename())
            } else {
                UniFile.fromUri(context, uri)
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            val nonFavoriteAnime = if (options.seenEntries) animeRepository.getSeenAnimeNotInLibrary() else emptyList()
            val backupAnime = backupAnimes(getFavorites.await() + nonFavoriteAnime, options)

            val backup = Backup(
                backupPreferences = backupAppPreferences(options),
                backupSourcePreferences = backupSourcePreferences(options),

                // AY -->
                isLegacy = false,
                backupAnime = backupAnime,
                backupCategories = backupCategories(options),
                backupSources = backupSources(backupAnime),
                backupExtensions = backupExtensions(options),
                backupExtensionStores = backupExtensionStores(options),
                backupCustomButton = backupCustomButtons(options),
                // <-- AY
            )

            val byteArray = parser.encodeToByteArray(Backup.serializer(), backup)
            if (byteArray.isEmpty()) {
                throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
            }

            file.openOutputStream()
                .also {
                    // Force overwrite old file
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }
                .sink().gzip().buffer().use {
                    it.write(byteArray)
                }
            val fileUri = file.uri

            // Make sure it's a valid backup file
            backupFileValidator.validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp.set(Clock.System.now().toEpochMilliseconds())
            }

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    internal suspend fun backupCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        return categoriesBackupCreator()
    }

    internal suspend fun backupAnimes(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        if (!options.libraryEntries) return emptyList()

        return animeBackupCreator(animes, options)
    }

    internal suspend fun backupSources(animes: List<BackupAnime>): List<BackupSource> {
        return sourcesBackupCreator(animes)
    }

    internal fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    private suspend fun backupExtensionStores(options: BackupOptions): List<BackupExtensionStore> {
        if (!options.extensionStores) return emptyList()

        return extensionStoresBackupCreator()
    }

    internal suspend fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    // AY -->
    private suspend fun backupCustomButtons(options: BackupOptions): List<BackupCustomButtons> {
        if (!options.customButton) return emptyList()

        return customButtonBackupCreator()
    }

    private suspend fun backupExtensions(options: BackupOptions): List<BackupExtension> {
        if (!options.extensions) return emptyList()

        return extensionsBackupCreator()
    }
    // <-- AY

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4
        private val FILENAME_REGEX = """${BuildConfig.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
