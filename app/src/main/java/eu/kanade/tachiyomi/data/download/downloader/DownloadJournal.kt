package eu.kanade.tachiyomi.data.download.downloader

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.core.common.util.system.logcat
import java.io.FileOutputStream
import kotlin.time.Duration.Companion.seconds

@Serializable
data class Journal(
    val threadCount: Int,
    val videoTitle: String,
    val subtitleInputs: Map<Int, String> = emptyMap(),
    val audioInputs: Map<Int, String> = emptyMap(),
    val videoInput: String? = null,
)

@AssistedInject
class DownloadJournal(
    @Assisted private val destDir: UniFile,
    @Assisted private val initial: Journal,
    @AppCoroutineScope private val scope: CoroutineScope,
    private val json: Json,
) {
    @AssistedFactory
    fun interface Factory {
        fun create(destDir: UniFile, initial: Journal): DownloadJournal
    }

    private val journal = MutableStateFlow<Journal>(initial)

    private val writes = Channel<Unit>(Channel.CONFLATED)
    private val writeMutex = Mutex()

    init {
        scope.launch {
            writes.receiveAsFlow()
                .debounce(0.5.seconds)
                .collect {
                    writeJournal(journal.value)
                }
        }
    }

    private suspend fun writeJournal(journal: Journal) {
        withContext(NonCancellable + Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    val file = destDir.findFile(JOURNAL_NAME)
                        ?: destDir.createFile(JOURNAL_NAME)!!

                    file.openOutputStream()
                        .also { (it as? FileOutputStream)?.channel?.truncate(0) }
                        .use {
                            json.encodeToStream(journal, it)
                        }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to write journal" }
                }
            }
        }
    }

    suspend fun write() {
        writeJournal(journal.value)
    }

    fun getSubtitleInput(index: Int): String? {
        return journal.value.subtitleInputs.get(index)
    }

    fun getAudioInput(index: Int): String? {
        return journal.value.audioInputs.get(index)
    }

    fun getVideoInput(): String? {
        return journal.value.videoInput
    }

    suspend fun updateSubtitle(index: Int, input: String) {
        journal.update { it.copy(subtitleInputs = it.subtitleInputs + (index to input)) }
        writes.send(Unit)
    }

    suspend fun updateAudio(index: Int, input: String) {
        journal.update { it.copy(audioInputs = it.audioInputs + (index to input)) }
        writes.send(Unit)
    }

    suspend fun updateVideo(input: String) {
        journal.update { it.copy(videoInput = input) }
        writes.send(Unit)
    }

    companion object {
        private const val JOURNAL_NAME = "journal"

        fun read(destDir: UniFile, json: Json): Journal? {
            val file = destDir.findFile(JOURNAL_NAME) ?: return null
            return try {
                file.openInputStream().use {
                    json.decodeFromStream<Journal>(it)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
