package eu.kanade.tachiyomi.data.download

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.size
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.isLocal
import java.io.IOException

/**
 * This class is used to provide the directories where the downloads should be saved.
 * It uses the following path scheme: /<root downloads dir>/<source name>/<anime>/<episode>
 *
 * @param context the application context.
 */
@Inject
@SingleIn(AppScope::class)
class DownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager,
    private val libraryPreferences: LibraryPreferences,
    // AM (FILE_SIZE) -->
    private val localFileSystem: LocalSourceFileSystem,
    // <-- AM (FILE_SIZE)
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Returns the download directory for an anime. For internal use only.
     *
     * @param animeTitle the title of the anime to query.
     * @param source the source of the anime.
     */
    internal fun getAnimeDir(animeTitle: String, source: AnimeSource): Result<UniFile> {
        val downloadsDir = downloadsDir
        if (downloadsDir == null) {
            logcat(LogPriority.ERROR) { "Failed to create download directory" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_download_directory)),
            )
        }

        val sourceDirName = getSourceDirName(source)
        val sourceDir = downloadsDir.createDirectory(sourceDirName)
        if (sourceDir == null) {
            val displayablePath = downloadsDir.displayablePath + "/$sourceDirName"
            logcat(LogPriority.ERROR) { "Failed to create source download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        val animeDirName = getAnimeDirName(animeTitle)
        val animeDir = sourceDir.createDirectory(animeDirName)
        if (animeDir == null) {
            val displayablePath = sourceDir.displayablePath + "/$animeDirName"
            logcat(LogPriority.ERROR) { "Failed to create anime download directory: $displayablePath" }
            return Result.failure(
                IOException(context.stringResource(MR.strings.storage_failed_to_create_directory, displayablePath)),
            )
        }

        return Result.success(animeDir)
    }

    /**
     * Returns the download directory for a source if it exists.
     *
     * @param source the source to query.
     */
    fun findSourceDir(source: AnimeSource): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    /**
     * Returns the download directory for an anime if it exists.
     *
     * @param animeTitle the title of the anime to query.
     * @param source the source of the anime.
     */
    fun findAnimeDir(animeTitle: String, source: AnimeSource): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getAnimeDirName(animeTitle))
    }

    /**
     * Returns the download directory for an episode if it exists.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     * @param episodeUrl url of the episode to query.
     * @param animeTitle the title of the anime to query.
     * @param source the source of the episode.
     */
    fun findEpisodeDir(
        episodeName: String,
        episodeScanlator: String?,
        episodeUrl: String,
        animeTitle: String,
        source: AnimeSource,
    ): UniFile? {
        val animeDir = findAnimeDir(animeTitle, source)
        return getValidEpisodeDirNames(episodeName, episodeScanlator, episodeUrl).asSequence()
            .mapNotNull { animeDir?.findFile(it) }
            .firstOrNull()
    }

    /**
     * Returns a list of downloaded directories for the episodes that exist.
     *
     * @param episodes the episodes to query.
     * @param anime the anime of the episode.
     * @param source the source of the episode.
     */
    fun findEpisodeDirs(episodes: List<Episode>, anime: Anime, source: AnimeSource): Pair<UniFile?, List<UniFile>> {
        // AM (CUSTOM_INFORMATION) -->
        val animeDir = findAnimeDir(anime.ogTitle, source) ?: return null to emptyList()
        // <-- AM (CUSTOM_INFORMATION)
        return animeDir to episodes.mapNotNull { episode ->
            getValidEpisodeDirNames(episode.name, episode.scanlator, episode.url).asSequence()
                .mapNotNull { animeDir.findFile(it) }
                .firstOrNull()
        }
    }

    /**
     * Returns the download directory name for a source.
     *
     * @param source the source to query.
     */
    fun getSourceDirName(source: AnimeSource): String {
        return DiskUtil.buildValidFilename(
            source.toString(),
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
        )
    }

    /**
     * Returns the download directory name for an anime.
     *
     * @param animeTitle the title of the anime to query.
     */
    fun getAnimeDirName(animeTitle: String): String {
        return DiskUtil.buildValidFilename(
            animeTitle,
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
        )
    }

    /**
     * Returns the episode directory name for an episode.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query.
     * @param episodeUrl url of the episode to query.
     */
    fun getEpisodeDirName(
        episodeName: String,
        episodeScanlator: String?,
        episodeUrl: String,
        disallowNonAsciiFilenames: Boolean = libraryPreferences.disallowNonAsciiFilenames.get(),
        enableEpisodeNameHash: Boolean = libraryPreferences.enableEpisodeNameHash.get(),
    ): String {
        return EpisodeDirNameParts(episodeName, episodeScanlator, episodeUrl)
            .dirName(disallowNonAsciiFilenames, enableEpisodeNameHash, sanitizeScanlator = true)
    }

    /**
     * Returns list of names that might have been previously used as
     * the directory name for an episode.
     * Add to this list if naming pattern ever changes.
     */
    private fun getLegacyEpisodeDirNames(parts: EpisodeDirNameParts): Set<String> {
        val episodeNameV1 = DiskUtil.buildValidFilename(
            when {
                !parts.episodeScanlator.isNullOrBlank() -> "${parts.episodeScanlator}_${parts.episodeName}"
                else -> parts.episodeName
            },
        )

        // Generate all possible legacy directory name variations by combining
        // different states of non-ASCII filenames and episode name hash settings.
        // This ensures that episodes downloaded under any past configuration
        // combination can still be successfully found.
        val booleanPairPermutation = listOf(false to false, false to true, true to false, true to true)
        val othersEpisodeDirNames = booleanPairPermutation
            .map { (disallowNonAsciiFilenames, enableEpisodeNameHash) ->
                parts.dirName(disallowNonAsciiFilenames, enableEpisodeNameHash, sanitizeScanlator = true)
            }

        // Scanlators weren't sanitized for a while. One with a "/" nested its folder, so it was never found anyway.
        val scanlator = parts.episodeScanlator
        val unsanitizedScanlatorEpisodeDirNames = if (scanlator != null && '/' !in scanlator) {
            booleanPairPermutation.map { (disallowNonAsciiFilenames, enableChapterNameHash) ->
                parts.dirName(disallowNonAsciiFilenames, enableChapterNameHash, sanitizeScanlator = false)
            }
        } else {
            emptyList()
        }

        return buildSet {
            // Episode name without hash (unable to handle duplicate
            // episode names)
            add(episodeNameV1)
            addAll(othersEpisodeDirNames)
            addAll(unsanitizedScanlatorEpisodeDirNames)
        }
    }

    fun isEpisodeDirNameChanged(oldEpisode: Episode, newEpisode: Episode): Boolean {
        return getEpisodeDirName(oldEpisode.name, oldEpisode.scanlator, oldEpisode.url) !=
            getEpisodeDirName(newEpisode.name, newEpisode.scanlator, newEpisode.url)
    }

    /**
     * Returns valid downloaded episode directory names.
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query.
     * @param episodeUrl url of the episode to query.
     */
    fun getValidEpisodeDirNames(episodeName: String, episodeScanlator: String?, episodeUrl: String): List<String> {
        val parts = EpisodeDirNameParts(episodeName, episodeScanlator, episodeUrl)
        val episodeDirName = parts.dirName(
            disallowNonAscii = libraryPreferences.disallowNonAsciiFilenames.get(),
            enableHash = libraryPreferences.enableEpisodeNameHash.get(),
            sanitizeScanlator = true,
        )
        val legacyEpisodeDirNames = getLegacyEpisodeDirNames(parts)

        return buildList {
            add(episodeDirName)
            addAll(legacyEpisodeDirNames)
        }
    }

    // AM (FILE_SIZE) -->

    /**
     * Returns an episode file size in bytes.
     * Returns null if the episode is not found in expected location
     *
     * @param episodeName the name of the episode to query.
     * @param episodeScanlator scanlator of the episode to query
     * @param animeTitle the title of the anime
     * @param animeSource the source of the anime
     */
    fun getEpisodeFileSize(
        episodeName: String,
        episodeUrl: String?,
        episodeScanlator: String?,
        animeTitle: String,
        animeSource: AnimeSource?,
    ): Long? {
        if (animeSource == null) return null
        return if (animeSource.isLocal()) {
            val (animeDirName, episodeDirName) = episodeUrl?.split('/', limit = 2) ?: return null
            localFileSystem.getBaseDirectory()?.findFile(animeDirName)?.findFile(episodeDirName)?.size()
        } else {
            val episodeUrl = episodeUrl ?: return null
            findEpisodeDir(episodeName, episodeScanlator, episodeUrl, animeTitle, animeSource)?.size()
        }
    }
    // <-- AM (FILE_SIZE)
}

/**
 * The parts of a episode's directory name, each computed once however many name variations are built from them.
 */
private class EpisodeDirNameParts(episodeName: String, val episodeScanlator: String?, private val episodeUrl: String) {

    /** The episode name, or a placeholder when it's blank. */
    val episodeName = episodeName.ifBlank { "Episode" }

    private val hashSuffix by lazy(LazyThreadSafetyMode.NONE) { "_${md5(episodeUrl).take(6)}" }

    private val sanitizedScanlator by lazy(LazyThreadSafetyMode.NONE) {
        episodeScanlator?.let { DiskUtil.buildValidFilename(it) }
    }

    private val validEpisodeNames = arrayOfNulls<String>(2)

    fun dirName(disallowNonAscii: Boolean, enableHash: Boolean, sanitizeScanlator: Boolean): String {
        return buildString {
            if (!episodeScanlator.isNullOrBlank()) {
                // A "/" in the scanlator would otherwise nest the episode in a folder of its own
                append(if (sanitizeScanlator) sanitizedScanlator else episodeScanlator)
                append("_")
            }
            append(validEpisodeName(disallowNonAscii))
            if (enableHash) append(hashSuffix)
        }
    }

    private fun validEpisodeName(disallowNonAscii: Boolean): String {
        val index = if (disallowNonAscii) 1 else 0
        return validEpisodeNames[index]
            // Subtract 7 bytes for hash and underscore, 4 bytes for .mkv
            ?: DiskUtil.buildValidFilename(episodeName, DiskUtil.MAX_FILE_NAME_BYTES - 11, disallowNonAscii)
                .also { validEpisodeNames[index] = it }
    }
}
