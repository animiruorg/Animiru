package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import java.io.IOException

/**
 * Writes the same bytes as encoding a whole [Backup], without holding all of its entries at once.
 */
class BackupWriter(
    private val sink: BufferedSink,
    private val parser: ProtoBuf,
) {
    var isEmpty = true
        private set

    fun writeEntry(entry: BackupAnime) {
        val bytes = parser.encodeToByteArray(BackupAnime.serializer(), entry)
        sink.writeVarint(ENTRY_TAG)
        sink.writeVarint(bytes.size.toLong())
        sink.write(bytes)
        isEmpty = false
    }

    /**
     * Writes the other fields after the entries, as [metadata] without entries of its own.
     */
    fun writeMetadata(metadata: Backup) {
        val bytes = parser.encodeToByteArray(Backup.serializer(), metadata)
        sink.write(bytes)
        if (bytes.isNotEmpty()) isEmpty = false
    }
}

/**
 * Reads an encoded [Backup] one entry at a time. Every other field is set aside for [readMetadata], wherever it
 * appears.
 *
 * Malformed or truncated input throws an [IOException].
 */
class BackupReader(
    private val source: BufferedSource,
    private val parser: ProtoBuf,
) {
    private val metadata = Buffer()
    private var entryLength = NO_ENTRY

    /**
     * Moves to the next entry, which [readEntry] or [skipEntry] then consumes.
     *
     * @return false once the input is exhausted.
     */
    fun nextEntry(): Boolean {
        check(entryLength == NO_ENTRY)
        while (!source.exhausted()) {
            val tag = source.readVarint()
            val fieldNumber = tag ushr 3
            val wireType = (tag and 0x7).toInt()
            if (fieldNumber == 0L) throw IOException("Invalid field number 0")

            if (tag == ENTRY_TAG) {
                entryLength = source.readLength()
                return true
            }

            metadata.writeVarint(tag)
            when (wireType) {
                WIRE_TYPE_VARINT -> metadata.writeVarint(source.readVarint())
                WIRE_TYPE_FIXED64 -> metadata.write(source, 8)
                WIRE_TYPE_LENGTH_DELIMITED -> {
                    val length = source.readLength()
                    metadata.writeVarint(length)
                    metadata.write(source, length)
                }
                WIRE_TYPE_FIXED32 -> metadata.write(source, 4)
                else -> throw IOException("Unsupported wire type $wireType")
            }
        }
        return false
    }

    fun readEntry(): BackupAnime {
        val bytes = source.readByteArray(takeEntryLength())
        return decode(BackupAnime.serializer(), bytes)
    }

    fun skipEntry() {
        source.skip(takeEntryLength())
    }

    // AM -->
    // id, source, url
    fun readEntryId(): Triple<Long, Long, String>? {
        val bytes = source.readByteArray(takeEntryLength())
        val buffer = Buffer().write(bytes)

        var id: Long? = null
        var source: Long? = null
        var url: String? = null

        var foundId = false
        var foundSource = false
        var foundUrl = false

        while (!buffer.exhausted()) {
            val tag = buffer.readVarint()
            val fieldNumber = tag ushr 3
            val wireType = (tag and 0x7).toInt()
            if (fieldNumber == 0L) throw IOException("Invalid field number 0")

            when {
                tag == ID_TAG -> {
                    id = buffer.readVarint()
                    foundId = true
                }
                tag == SOURCE_TAG -> {
                    source = buffer.readVarint()
                    foundSource = true
                }
                tag == URL_TAG -> {
                    val l = buffer.readLength()
                    url = buffer.readUtf8(l)
                    foundUrl = true
                }
                else -> {
                    when (wireType) {
                        WIRE_TYPE_VARINT -> buffer.readVarint()
                        WIRE_TYPE_FIXED64 -> buffer.skip(8)
                        WIRE_TYPE_LENGTH_DELIMITED -> buffer.skip(buffer.readLength())
                        WIRE_TYPE_FIXED32 -> buffer.skip(4)
                        else -> throw IOException("Unsupported wire type $wireType")
                    }
                }
            }

            if (foundId && foundSource && foundUrl) break
        }

        return if (id != null && source != null && url != null) {
            Triple(id, source, url)
        } else {
            null
        }
    }
    // <-- AM

    /**
     * Skips the remaining entries and decodes everything else as a [Backup] without entries.
     */
    fun readMetadata(): Backup {
        if (entryLength != NO_ENTRY) skipEntry()
        while (nextEntry()) skipEntry()
        return decode(Backup.serializer(), metadata.readByteArray())
    }

    private fun takeEntryLength(): Long {
        check(entryLength != NO_ENTRY)
        return entryLength.also { entryLength = NO_ENTRY }
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        return try {
            parser.decodeFromByteArray(deserializer, bytes)
        } catch (e: SerializationException) {
            throw IOException(e)
        }
    }
}

private fun BufferedSource.readVarint(): Long {
    var result = 0L
    for (shift in 0 until Long.SIZE_BITS step 7) {
        val byte = readByte().toInt()
        result = result or ((byte and 0x7F).toLong() shl shift)
        if ((byte and 0x80) == 0) return result
    }
    throw IOException("Malformed varint")
}

private fun BufferedSource.readLength(): Long {
    val length = readVarint()
    if (length !in 0..Int.MAX_VALUE) throw IOException("Invalid length $length")
    return length
}

private fun BufferedSink.writeVarint(value: Long) {
    var remaining = value
    while ((remaining and 0x7FL.inv()) != 0L) {
        writeByte((remaining and 0x7F).toInt() or 0x80)
        remaining = remaining ushr 7
    }
    writeByte(remaining.toInt())
}

private const val WIRE_TYPE_VARINT = 0
private const val WIRE_TYPE_FIXED64 = 1
private const val WIRE_TYPE_LENGTH_DELIMITED = 2
private const val WIRE_TYPE_FIXED32 = 5

// Backup.backupAnime is field 1, and each entry is length-delimited
private const val ENTRY_TAG = 1L shl 3 or WIRE_TYPE_LENGTH_DELIMITED.toLong()

// AM -->
private const val SOURCE_TAG = 1L shl 3 or WIRE_TYPE_VARINT.toLong()
private const val URL_TAG = 2L shl 3 or WIRE_TYPE_LENGTH_DELIMITED.toLong()
private const val ID_TAG = 503L shl 3 or WIRE_TYPE_VARINT.toLong()
// <-- AM

private const val NO_ENTRY = -1L
