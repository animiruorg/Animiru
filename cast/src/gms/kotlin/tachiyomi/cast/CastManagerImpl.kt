package tachiyomi.cast

import android.content.Context
import androidx.core.net.toUri
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import animiru.domain.player.service.GesturePreferences
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import tachiyomi.cast.domain.CodecInformation
import tachiyomi.cast.domain.TrackInformation
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

// Some code taken from https://github.com/MakD/AFinity/blob/master/app/src/main/java/com/makd/afinity/cast/CastManager.kt
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CastManagerImpl(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val gesturePreferences: GesturePreferences,
) : CastManager {

    lateinit var castContext: CastContext
    private var castSession: CastSession? = null
    private var remoteMediaClient: RemoteMediaClient? = null
    private var startJob: Job? = null
    private var volumeDebounceJob: Job? = null

    @Volatile
    private var initialized = false

    private val _castState = MutableStateFlow(CastState())
    override val castState = _castState.asStateFlow()

    private val _castEvent = MutableSharedFlow<CastEvent>()
    override val castEvent = _castEvent.asSharedFlow()

    override fun initialize(context: Context) {
        if (initialized) return

        try {
            castContext = CastContext.getSharedInstance(context)
            castContext
                .sessionManager
                .addSessionManagerListener(castSessionManagerListener, CastSession::class.java)

            MediaRouter.getInstance(context).routerParams = MediaRouterParams.Builder()
                .setDialogType(MediaRouterParams.DIALOG_TYPE_DYNAMIC_GROUP)
                .setOutputSwitcherEnabled(true)
                .build()

            initialized = true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize cast" }
            initialized = false
        }
    }

    override fun disconnect() {
        scope.launch {
            try {
                castContext.sessionManager.endCurrentSession(true)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to disconnect cast" }
            }
        }
    }

    override fun startCasting(
        video: Video,
        videoInformation: CodecInformation,
        subtitleTracks: List<TrackInformation>,
        audioTracks: List<TrackInformation>,
        subtitleId: Long?,
        audioId: Long?,
        anime: Anime,
        episodeTitle: String,
        startPosition: Long,
        playbackRate: Double,
    ) {
        startJob?.cancel()
        startJob = scope.launch {
            val client = remoteMediaClient
            if (client == null) {
                _castEvent.emit(CastEvent.PlaybackError(CastNotConnectedException()))
                return@launch
            }

            _castState.update { it.copy(isLoading = true) }

            val subtitleMediaTracks = subtitleTracks.map {
                MediaTrack.Builder(it.index, MediaTrack.TYPE_TEXT).apply {
                    setName(it.title)
                    setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                    setContentType(it.contentType)
                    setLanguage(it.language)
                    it.contentId?.let { id ->
                        setContentId(id)
                    }
                }.build()
            }

            val audioMediaTracks = audioTracks.map {
                MediaTrack.Builder(it.index, MediaTrack.TYPE_AUDIO).apply {
                    setName(it.title)
                    setSubtype(MediaTrack.SUBTYPE_NONE)
                    setContentType(it.contentType)
                    setLanguage(it.language)
                    it.contentId?.let { id ->
                        setContentId(id)
                    }
                }.build()
            }

            val preferred = listOfNotNull(subtitleId, audioId).toLongArray()

            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, anime.title)
                putString(MediaMetadata.KEY_SUBTITLE, episodeTitle)
                anime.thumbnailUrl?.let {
                    addImage(WebImage(it.toUri()))
                }
            }

            val mediaInfo = MediaInfo.Builder(video.videoUrl).apply {
                setContentType(videoInformation.contentType)
                setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                setMediaTracks(subtitleMediaTracks + audioMediaTracks)
                videoInformation.duration?.let {
                    setStreamDuration(it.times(1000).toLong())
                }
                setMetadata(metadata)
            }.build()

            val loadRequest = MediaLoadRequestData.Builder().apply {
                setMediaInfo(mediaInfo)
                setAutoplay(true)
                setCurrentTime(startPosition * 1000L)
                setActiveTrackIds(preferred)
                setPlaybackRate(
                    playbackRate.coerceIn(MediaLoadOptions.PLAYBACK_RATE_MIN, MediaLoadOptions.PLAYBACK_RATE_MAX),
                )
            }.build()

            val loadTask = client.load(loadRequest)
            val result = suspendCancellableCoroutine { continuation ->
                loadTask.setResultCallback { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                continuation.invokeOnCancellation {
                    loadTask.cancel()
                }
            }

            if (result.status.isSuccess) {
                _castState.update { it.copy(isLoading = false) }
                if (startPosition > 0L) {
                    client.seek(
                        MediaSeekOptions.Builder()
                            .setPosition(startPosition * 1000L)
                            .build(),
                    )
                    scope.launch {
                        _castEvent.emit(CastEvent.OnSecondReached(startPosition.toInt()))
                    }
                }
            } else {
                _castEvent.emit(CastEvent.LoadingFailed)
            }

            _castState.update {
                it.copy(
                    hasLoadedVideo = true,
                )
            }

            _castEvent.emit(CastEvent.Ready)
        }
    }

    override fun seekTo(position: Long) {
        scope.launch {
            val client = remoteMediaClient
            if (client == null) {
                _castEvent.emit(CastEvent.PlaybackError(CastNotConnectedException()))
                return@launch
            }

            val durationMs = castState.value.durationMs
            client.seek(
                MediaSeekOptions.Builder()
                    .setPosition(position.times(1000L).coerceIn(0, durationMs))
                    .build(),
            )
        }
    }

    override fun seekBy(delta: Long) {
        scope.launch {
            val client = remoteMediaClient
            if (client == null) {
                _castEvent.emit(CastEvent.PlaybackError(CastNotConnectedException()))
                return@launch
            }

            val position = castState.value.position
            val durationMs = castState.value.durationMs
            client.seek(
                MediaSeekOptions.Builder()
                    .setPosition((position + delta).times(1000L).coerceIn(0, durationMs))
                    .build(),
            )
        }
    }

    override fun setSpeed(speed: Double) {
        scope.launch {
            val client = remoteMediaClient
            if (client == null) {
                _castEvent.emit(CastEvent.PlaybackError(CastNotConnectedException()))
                return@launch
            }

            client.setPlaybackRate(speed)
        }
    }

    override fun loadTrack(trackId: Long, isAudio: Boolean) {
        scope.launch {
            val client = remoteMediaClient
            if (client == null) {
                _castEvent.emit(CastEvent.PlaybackError(CastNotConnectedException()))
                return@launch
            }

            val loadTask = client.setActiveMediaTracks(longArrayOf(trackId))
            val result = suspendCancellableCoroutine { continuation ->
                loadTask.setResultCallback { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                continuation.invokeOnCancellation {
                    loadTask.cancel()
                }
            }

            if (result.status.isSuccess) {
                if (isAudio) {
                    _castState.update { it.copy(lastLoadedAudioId = trackId) }
                } else {
                    _castState.update { it.copy(lastLoadedSubId = trackId) }
                }
            }

            _castEvent.emit(
                CastEvent.TrackLoadResult(trackId, result.status.isSuccess, isAudio),
            )
        }
    }

    override fun stopRemoteMediaClient() {
        remoteMediaClient?.stop()
    }

    override fun handleCastManagerEvent(event: CastManagerEvent) {
        when (event) {
            is CastManagerEvent.DoubleTapSeek -> {
                val seekLength = gesturePreferences.skipLengthPreference.get().toLong()
                if (seekLength > 0L) {
                    seekBy(if (event.forwards) seekLength else -seekLength)
                }
            }
            is CastManagerEvent.Next -> {
                scope.launch {
                    _castEvent.emit(CastEvent.NextEpisode(event.next))
                }
            }
            CastManagerEvent.PlayPause -> {
                if (castState.value.playing) {
                    remoteMediaClient?.pause()
                } else {
                    remoteMediaClient?.play()
                }
            }
            is CastManagerEvent.VolumeChange -> {
                setVolume(event.volume.toDouble())
            }
        }
    }

    private fun setVolume(volume: Double) {
        volumeDebounceJob?.cancel()
        volumeDebounceJob = scope.launch {
            delay(300.milliseconds)
            try {
                castSession?.volume = volume.coerceIn(0.0, 1.0)
                _castState.update { it.copy(volume = volume.coerceIn(0.0, 1.0)) }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to set volume on cast receiver" }
            }
        }
    }

    private fun updateStatusFromRemote() {
        try {
            val client = remoteMediaClient ?: return
            val mediaStatus = client.mediaStatus
            if (mediaStatus != null) {
                _castState.update {
                    it.copy(
                        playing = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING,
                        buffering =
                        mediaStatus.playerState == MediaStatus.PLAYER_STATE_BUFFERING ||
                            mediaStatus.playerState == MediaStatus.PLAYER_STATE_LOADING,
                        speed = mediaStatus.playbackRate,
                    )
                }
            }

            castSession?.let { cSession ->
                _castState.update {
                    it.copy(
                        volume = cSession.volume,
                        muted = cSession.isMute,
                    )
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to update cast from remote" }
        }
    }

    private fun addListeners(session: CastSession) {
        castSession = session
        castSession?.addCastListener(castListener)
        remoteMediaClient = session.remoteMediaClient
        remoteMediaClient?.registerCallback(remoteMediaClientCallback)
        remoteMediaClient?.addProgressListener(remoteMediaClientProgressListener, 100)
    }

    private fun removeListeners() {
        castSession?.removeCastListener(castListener)
        castSession = null
        remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        remoteMediaClient?.removeProgressListener(remoteMediaClientProgressListener)
        remoteMediaClient = null
        startJob?.cancel()
        volumeDebounceJob?.cancel()
    }

    private val castSessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _castState.update { _ -> CastState() }
            scope.launch {
                _castEvent.emit(CastEvent.ConnectionStart)
            }
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            val deviceName = session.castDevice?.friendlyName
            addListeners(session)

            _castState.update {
                it.copy(
                    isConnected = true,
                    deviceName = deviceName,
                    volume = session.volume,
                    muted = session.isMute,
                )
            }

            scope.launch {
                _castEvent.emit(CastEvent.Connected)
            }
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            logcat(LogPriority.ERROR) { "Cast session failed: $error" }
            resetCastState()
            scope.launch {
                _castEvent.emit(CastEvent.ConnectionError)
            }
        }

        override fun onSessionEnding(session: CastSession) {
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            val state = castState.value
            resetCastState()
            removeListeners()

            scope.launch {
                _castEvent.emit(
                    CastEvent.Disconnected(state.position),
                )
            }
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            addListeners(session)
            _castState.update {
                it.copy(
                    isConnected = true,
                    deviceName = session.castDevice?.friendlyName,
                )
            }
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            removeListeners()
        }
    }

    private val remoteMediaClientProgressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        if (castState.value.position != progressMs / 1000L) {
            scope.launch {
                _castEvent.emit(CastEvent.OnSecondReached(position = progressMs.div(1000).toInt()))
            }
        }
        _castState.update {
            it.copy(
                position = progressMs / 1000L,
                durationMs = durationMs,
            )
        }
    }

    private val castListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            castSession?.volume?.let { v ->
                _castState.update { it.copy(volume = v) }
            }
        }
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            updateStatusFromRemote()
        }
    }

    private fun resetCastState() {
        _castState.update { _ -> CastState() }
    }
}
