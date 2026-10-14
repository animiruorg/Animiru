package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import animiru.domain.player.interactor.TrackSelect
import animiru.domain.player.model.ArtType
import animiru.domain.player.model.CustomKeyCodes
import animiru.domain.player.model.PlayerOrientation
import animiru.domain.player.model.SetAsArt
import animiru.domain.player.model.SingleActionGesture
import animiru.domain.player.model.VideoAspect
import animiru.domain.player.service.AudioPreferences
import animiru.domain.player.service.DecoderPreferences
import animiru.domain.player.service.GesturePreferences
import animiru.domain.player.service.PlayerPreferences
import animiru.domain.player.service.SubtitlePreferences
import animiru.feature.cast.CastProxyServerService
import aniyomi.core.common.torrent.TorrentPreferences
import aniyomi.core.common.torrent.TorrentServerApi
import aniyomi.core.common.torrent.TorrentServerUtils
import com.yubyf.truetypeparser.TTFFile
import dev.icerock.moko.resources.StringResource
import dev.vivvvek.seeker.Segment
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import eu.kanade.domain.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.anime.interactor.UpdateAnime
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.connection.SyncPreferences
import eu.kanade.domain.episode.model.toDbEpisode
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.track.interactor.TrackEpisode
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.HttpServer
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.toHosterList
import eu.kanade.tachiyomi.animesource.model.ThumbnailInfo
import eu.kanade.tachiyomi.animesource.model.TileInfo
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.cache.BackgroundCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.connection.syncmiru.SyncDataJob
import eu.kanade.tachiyomi.data.database.models.Episode
import eu.kanade.tachiyomi.data.database.models.isRecognizedNumber
import eu.kanade.tachiyomi.data.database.models.toDomainEpisode
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.player.service.HttpServerService
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.data.torrent.service.TorrentServerService
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.cast.CastDialog
import eu.kanade.tachiyomi.ui.player.cast.CastSheet
import eu.kanade.tachiyomi.ui.player.cast.CastUiData
import eu.kanade.tachiyomi.ui.player.components.HosterState
import eu.kanade.tachiyomi.ui.player.components.MAX_BRIGHTNESS
import eu.kanade.tachiyomi.ui.player.components.MIN_BRIGHTNESS
import eu.kanade.tachiyomi.ui.player.components.getChangedAt
import eu.kanade.tachiyomi.ui.player.controls.components.IndexedSegment
import eu.kanade.tachiyomi.ui.player.domain.AudioManager
import eu.kanade.tachiyomi.ui.player.domain.BrightnessManager
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.ui.player.mpv.ChapterNode
import eu.kanade.tachiyomi.ui.player.mpv.MPVPlayer
import eu.kanade.tachiyomi.ui.player.mpv.MpvVideoTrack
import eu.kanade.tachiyomi.ui.player.mpv.TrackNode
import eu.kanade.tachiyomi.ui.player.mpv.TrackState
import eu.kanade.tachiyomi.ui.player.utils.AniSkipApi
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils
import eu.kanade.tachiyomi.ui.player.utils.ChapterUtils.Companion.getStringRes
import eu.kanade.tachiyomi.util.editBackground
import eu.kanade.tachiyomi.util.editCover
import eu.kanade.tachiyomi.util.editThumbnail
import eu.kanade.tachiyomi.util.episode.filterDownloaded
import eu.kanade.tachiyomi.util.lang.byteSize
import eu.kanade.tachiyomi.util.lang.takeBytes
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.storage.cacheImageDir
import eu.kanade.tachiyomi.util.system.getWanIp
import eu.kanade.tachiyomi.util.system.workManager
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.cast.CastEvent
import tachiyomi.cast.CastManager
import tachiyomi.cast.domain.TrackInformation
import tachiyomi.cast.domain.VideoInformation
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.custombutton.interactor.GetCustomButtons
import tachiyomi.domain.custombutton.model.CustomButton
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.episode.interactor.UpdateEpisode
import tachiyomi.domain.episode.model.EpisodeUpdate
import tachiyomi.domain.episode.service.getEpisodeSort
import tachiyomi.domain.history.interactor.GetNextEpisodes
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.image.LocalBackgroundManager
import tachiyomi.source.local.image.LocalCoverManager
import tachiyomi.source.local.image.LocalEpisodeThumbnailManager
import tachiyomi.source.local.isLocal
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration.Companion.seconds

@AssistedInject
class PlayerViewModel(
    @Assisted private val savedState: SavedStateHandle,
    private val context: Context,
    private val json: Json,
    private val mpvFactory: MPVPlayer.Factory,

    private val getAnime: GetAnime,
    private val getNextEpisodes: GetNextEpisodes,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val getCategories: GetCategories,
    private val getTracks: GetTracks,
    private val getIncognitoState: GetIncognitoState,

    private val updateAnime: UpdateAnime,
    private val upsertHistory: UpsertHistory,
    private val updateEpisode: UpdateEpisode,
    private val trackEpisode: TrackEpisode,
    private val setAnimeViewerFlags: SetAnimeViewerFlags,
    private val downloadCache: DownloadCache,
    private val coverCache: CoverCache,
    private val backgroundCache: BackgroundCache,

    private val imageSaver: ImageSaver,
    private val downloadManager: DownloadManager,
    private val sourceManager: SourceManager,
    private val storageManager: StorageManager,
    private val trackerManager: TrackerManager,
    private val episodeLoader: EpisodeLoader,
    private val hosterLoader: HosterLoader,
    private val aniSkipApi: AniSkipApi,

    private val torrentServerApi: TorrentServerApi,
    private val torrentServerUtils: TorrentServerUtils,
    private val torrentPreferences: TorrentPreferences,

    private val basePreferences: BasePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val trackPreferences: TrackPreferences,
    private val playerPreferences: PlayerPreferences,
    decoderPreferences: DecoderPreferences,
    private val gesturePreferences: GesturePreferences,
    private val audioPreferences: AudioPreferences,
    private val subtitlePreferences: SubtitlePreferences,
    private val getCustomButtons: GetCustomButtons,
    private val trackSelect: TrackSelect,
    private val audioManager: AudioManager,
    brightnessManager: BrightnessManager,
    private val coverManager: LocalCoverManager,
    private val backgroundManager: LocalBackgroundManager,
    private val thumbnailManager: LocalEpisodeThumbnailManager,
    // AM (SYNC) -->
    private val syncPreferences: SyncPreferences,
    // <-- AM (SYNC)
    // AM --> (CAST)
    internal val castManager: CastManager,
    private val videoInformation: VideoInformation,
    // <-- AM (CAST)
) : ViewModel() {

    @AssistedFactory
    @ViewModelAssistedFactoryKey(PlayerViewModel::class)
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ViewModelAssistedFactory {
        override fun create(extras: CreationExtras): PlayerViewModel {
            return create(extras.createSavedStateHandle())
        }

        fun create(@Assisted savedState: SavedStateHandle): PlayerViewModel
    }

    val videoOutput = if (decoderPreferences.gpuNext.get()) "gpu-next" else "gpu"

    val player = mpvFactory.create(videoOutput)
    val mpv = player.mpv

    // Prefs
    private val reduceMotion = playerPreferences.reduceMotion.get()
    private val playerTimeToDisappearMs = playerPreferences.playerTimeToDisappear.get()
    private val swapVolumeAndBrightness = gesturePreferences.swapVolumeBrightness.get()
    private val boostCap = audioPreferences.volumeBoostCap.get()
    private val displayVolumeAsPercentage = playerPreferences.displayVolPer.get()
    private val showLoadingCircle = playerPreferences.showLoadingCircle.get()
    private val invertDuration = playerPreferences.invertDuration.get()
    private val smoothSeeking = gesturePreferences.playerSmoothSeek.get()
    private val showChapterIndicator = playerPreferences.showCurrentChapter.get()
    private val enableCast = playerPreferences.enableCast.get()

    private val aniSkipEnabled = playerPreferences.aniSkipEnabled.get()
    private val disableAniSkipOnChapters = playerPreferences.disableAniSkipOnChapters.get()
    private val introSkipEnabled = playerPreferences.enableSkipIntro.get()
    private val autoSkip = playerPreferences.autoSkipIntro.get()
    private val netflixStyle = playerPreferences.enableNetflixStyleIntroSkip.get()
    private val defaultWaitingTime = playerPreferences.waitingTimeIntroSkip.get()
    private val leftDoubleTapGesture = gesturePreferences.leftDoubleTapGesture.get()
    private val centerDoubleTapGesture = gesturePreferences.centerDoubleTapGesture.get()
    private val rightDoubleTapGesture = gesturePreferences.rightDoubleTapGesture.get()
    private val doubleTapToSeekDuration = gesturePreferences.skipLengthPreference.get()
    private val showSeekBar = gesturePreferences.showSeekBar.get()
    private val pipEpisodeToasts = playerPreferences.pipEpisodeToasts.get()
    private val showStatusBar = playerPreferences.showSystemStatusBar.get()
    private val downloadAheadAmount = downloadPreferences.autoDownloadWhileWatching.get()
    private val progress = playerPreferences.progressPreference.get()
    private val castProxy = playerPreferences.castProxy.get()
    private val castProxyPort = playerPreferences.castProxyPort.get().toInt()

    private val fontExtensionRegex = Regex($$""".*\.[ot]tf$""")
    private val maxVolume = audioManager.getMaxVolume()
    private val screenAspectRatio: Double by lazy {
        val metrics = context.resources.displayMetrics
        metrics.widthPixels.toDouble() / metrics.heightPixels.toDouble()
    }

    private val _stateData = MutableStateFlow(
        PlayerStateData(
            maxVolume = maxVolume,
        ),
    )
    val stateData = _stateData.asStateFlow()
    private val _uiData = MutableStateFlow(
        PlayerUiData(
            reduceMotion = reduceMotion,
            playerTimeToDisappearMs = playerTimeToDisappearMs,
            swapVolumeAndBrightness = swapVolumeAndBrightness,
            boostCap = boostCap,
            displayVolumeAsPercentage = displayVolumeAsPercentage,
            showLoadingCircle = showLoadingCircle,
            invertDuration = invertDuration,
            smoothSeeking = smoothSeeking,
            showChapterIndicator = showChapterIndicator,
            enableCast = enableCast,
        ),
    )
    val uiData = _uiData.asStateFlow()
    private val _playbackData = MutableStateFlow(
        PlayerPlaybackData(
            currentVolume = if (playerPreferences.rememberPlayerVolume.get()) {
                playerPreferences.playerVolumeValue.get().takeUnless { it == -1 }
                    ?: audioManager.getVolume()
            } else {
                audioManager.getVolume()
            },
            currentBrightness = if (playerPreferences.rememberPlayerBrightness.get()) {
                playerPreferences.playerBrightnessValue.get()
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            },
        ),
    )
    val playbackData = _playbackData.asStateFlow()
    private val _castUiData = MutableStateFlow(
        CastUiData(
            invertDurationTimer = invertDuration,
            showChapterIndicator = showChapterIndicator,
        ),
    )
    val castUiData = _castUiData.asStateFlow()

    private val _aspectRatio = MutableStateFlow<Double?>(null)
    val aspectRatio = _aspectRatio.asStateFlow()

    private val _eventFlow = MutableSharedFlow<Event>()
    val eventFlow = _eventFlow.asSharedFlow()

    private var httpServer: HttpServer? = null
    private var timerJob: Job? = null
    private var getHosterVideoLinksJob: Job? = null
    private var episodeToDownload: Download? = null
    private var currentHosterList: List<Hoster>? = null
    private var thumbnailFetchJob: Job? = null
    private val thumbnailTileCache =
        object : LinkedHashMap<Int, Bitmap>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?) = size > 3
        }

    init {
        viewModelScope.launchIO {
            getCustomButtons.subscribeAll().collectLatest { buttons ->
                setupCustomButtons(buttons)
            }
        }

        viewModelScope.launchIO {
            subtitlePreferences.subtitleSystemFonts.changes().collectLatest { fonts ->
                updateUiData { it.copy(fontList = fetchFonts(fonts)) }
            }
        }

        viewModelScope.launch {
            player.eventFlow
                .onEach { handlePlayerFlow(it) }
                .launchIn(viewModelScope)

            castManager.castEvent
                .onEach { handleCastFlow(it) }
                .launchIn(viewModelScope)

            playerPreferences.autoplayEnabled.changes()
                .onEach { v -> updateUiData { it.copy(autoPlayEnabled = v) } }
                .launchIn(viewModelScope)

            playerPreferences.playerSpeed.changes()
                .onEach { v -> updateUiData { it.copy(playerSpeedPref = v) } }
                .launchIn(viewModelScope)

            combine(
                propFlow<Double>("video-params/aspect"),
                propFlow<Int>("video-params/rotate"),
            ) { aspect, rotation -> aspect to rotation }
                .onEach { (aspect, rotation) ->
                    _aspectRatio.update { _ ->
                        aspect?.let {
                            if (it < 0.001) return@update 0.0
                            if ((rotation ?: 0) % 180 == 90) 1.0 / it else it
                        }
                    }
                }
                .launchIn(viewModelScope)

            propFlow<Int>("video-params/w")
                .filterNotNull()
                .onEach { v -> updateStateData { it.copy(videoWidth = v) } }
                .launchIn(viewModelScope)

            propFlow<Int>("video-params/h")
                .filterNotNull()
                .onEach { v -> updateStateData { it.copy(videoHeight = v) } }
                .launchIn(viewModelScope)

            propFlow<MPVNode>("track-list")
                .filterNotNull()
                .onEach { onTrackListChanged(it) }
                .launchIn(viewModelScope)

            propFlow<MPVNode>("chapter-list")
                .filterNotNull()
                .onEach { onChapterListChanged(it) }
                .launchIn(viewModelScope)

            propFlow<Int>("chapter")
                .onEach { onChapterChanged(it) }
                .launchIn(viewModelScope)

            propFlow<Int>("duration")
                .filterNotNull()
                .onEach { v ->
                    updatePlaybackData { it.copy(duration = v) }
                }
                .launchIn(viewModelScope)

            propFlow<Int>("time-pos")
                .filterNotNull()
                .onEach { onSecondReached(it) }
                .launchIn(viewModelScope)

            propFlow<Boolean>("pause")
                .filterNotNull()
                .onEach { v ->
                    updatePlaybackData { it.copy(paused = v) }
                    _eventFlow.emit(Event.UpdateDiscordRPC(v))
                }
                .launchIn(viewModelScope)

            propFlow<Int>("volume-max")
                .filterNotNull()
                .onEach { v ->
                    updateStateData { it.copy(volumeBoostCap = v) }
                }
                .launchIn(viewModelScope)

            propFlow<MPVNode>("sid")
                .onEach { onSubtitleTrackSelectChange() }
                .launchIn(viewModelScope)

            propFlow<MPVNode>("secondary-sid")
                .onEach { onSubtitleTrackSelectChange() }
                .launchIn(viewModelScope)

            propFlow<MPVNode>("aid")
                .onEach { onAudioTrackSelectChange() }
                .launchIn(viewModelScope)

            propFlow<Long>("user-data/current-anime/intro-length")
                .filterNotNull()
                .onEach { setAnimeSkipIntroLength(it) }
                .launchIn(viewModelScope)
        }
    }

    fun isPlayerExiting(): Boolean {
        return player.isExiting
    }

    fun setPlayerExiting(value: Boolean) {
        player.isExiting = value
    }

    private fun updateStateData(update: (PlayerStateData) -> PlayerStateData) {
        _stateData.update { update(it) }
    }

    private fun updateUiData(update: (PlayerUiData) -> PlayerUiData) {
        _uiData.update { update(it) }
    }

    private fun updatePlaybackData(update: (PlayerPlaybackData) -> PlayerPlaybackData) {
        _playbackData.update { update(it) }
    }

    private fun updateCastUiData(update: (CastUiData) -> CastUiData) {
        _castUiData.update { update(it) }
    }

    inline fun <reified T> propFlow(name: String): StateFlow<T?> {
        return mpv.propFlow<T>(name)
    }

    fun setPropertyBoolean(property: String, value: Boolean) {
        mpv.setPropertyBoolean(property, value)
    }

    fun setPropertyInt(property: String, value: Int) {
        mpv.setPropertyInt(property, value)
    }

    fun setPropertyFloat(property: String, value: Float) {
        mpv.setPropertyFloat(property, value)
    }

    fun setPropertyDouble(property: String, value: Double) {
        mpv.setPropertyDouble(property, value)
    }

    fun setPropertyString(property: String, value: String) {
        mpv.setPropertyString(property, value)
    }

    fun setPropertyNode(property: String, value: MPVNode) {
        mpv.setPropertyNode(property, value)
    }

    fun mpvCommand(vararg command: String) {
        mpv.command(*command)
    }

    fun handlePlayerEvent(event: PlayerEvent) {
        when (event) {
            PlayerEvent.ChangeAspect -> {
                cycleAspectRatio()
            }
            is PlayerEvent.ChangeSpeed -> {
                setSpeed(event.value)
            }
            PlayerEvent.CycleRotation -> {
                cycleRotations()
            }
            PlayerEvent.EnterPip -> {
                viewModelScope.launch {
                    _eventFlow.emit(Event.EnterPip)
                }
            }
            is PlayerEvent.ExecuteCustomButton -> {
                uiData.value.primaryButton?.let {
                    if (event.long) {
                        executeLongPressButton(it)
                    } else {
                        executeButton(it)
                    }
                }
            }
            is PlayerEvent.LockControls -> {
                updateUiData { it.copy(isControlsLocked = event.lock) }
            }
            is PlayerEvent.NextEpisode -> {
                nextEpisode(event.next)
            }
            PlayerEvent.PlayPause -> {
                pauseUnpause()
            }
            is PlayerEvent.Seek -> {
                updateSeekPos(event.position.toFloat())
            }
            is PlayerEvent.SeekFinished -> {
                updatePlaybackData { it.copy(isSeeking = false) }
                seekTo(event.position)
            }
            is PlayerEvent.SetAutoPlay -> {
                setAutoPlay(event.value)
            }
            is PlayerEvent.SetPanel -> {
                setPanel(event.panel)
            }
            is PlayerEvent.SetSheet -> {
                setSheet(event.sheet)
            }
            is PlayerEvent.ShowBrightnessSlider -> {
                displayBrightnessSlider(event.show)
            }
            PlayerEvent.ShowEpisodeDialog -> {
                updateUiData { it.copy(dialogShown = Dialogs.EpisodeList) }
            }
            is PlayerEvent.ShowPlayerUpdate -> {
                updateUiData { it.copy(playerUpdate = event.update) }
            }
            is PlayerEvent.ShowVolumeSlider -> {
                displayVolumeSlider(event.show)
            }
            PlayerEvent.SkipIntro -> {
                onSkipIntro()
            }
            PlayerEvent.ToggleDurationTimer -> {
                toggleDurationTimer()
            }
        }
    }

    fun handlePlayerFlow(event: MPVPlayer.Event) {
        when (event) {
            is MPVPlayer.Event.EOF -> eofReached(event.value)
            is MPVPlayer.Event.EndFile -> endFile(event.node)
            MPVPlayer.Event.FileLoaded -> fileLoaded()
            is MPVPlayer.Event.LuaEvent -> handleLuaInvocation(event.property, event.value)
            is MPVPlayer.Event.TrackLoadFailure -> onTrackLoadedFailure(event.url)
        }
    }

    fun handleCastFlow(event: CastEvent) {
        val castState = castManager.castState.value
        when (event) {
            CastEvent.ConnectionError -> {
                updateStateData {
                    it.copy(
                        isCasting = false,
                        isLoadingCasting = false,
                        isErrorCasting = true,
                    )
                }
                stopHttpServerService()
            }
            CastEvent.ConnectionStart -> {
                updateStateData {
                    it.copy(
                        isCasting = false,
                        isLoadingCasting = true,
                        isErrorCasting = false,
                    )
                }
            }
            CastEvent.Connected -> {
                updateStateData {
                    it.copy(
                        isCasting = castState.isConnected,
                        isLoadingCasting = false,
                        isErrorCasting = false,
                    )
                }

                if (castState.isConnected && !castState.hasLoadedVideo) {
                    val position = playbackData.value.position.toLong()
                    startCasting(startPosition = position)
                }
            }
            is CastEvent.Disconnected -> {
                updateStateData {
                    it.copy(
                        isCasting = false,
                        isLoadingCasting = false,
                        isErrorCasting = false,
                    )
                }

                context.stopService(Intent(context, CastProxyServerService::class.java))
                stopHttpServerService()
            }
            is CastEvent.NextEpisode -> {
                nextEpisode(next = event.next)
            }
            is CastEvent.OnSecondReached -> {
                onSecondReached(position = event.position, isCasting = true)
            }
            is CastEvent.PlaybackError -> {
            }
            is CastEvent.TrackLoadResult -> {
                trackLoaded(event.trackId, event.success, event.isAudio)
            }
            CastEvent.Ready -> {
                updateCastUiData { it.copy(isLoadingEpisode = false) }
            }
            CastEvent.LoadingFailed -> {
                viewModelScope.launch {
                    _eventFlow.emit(Event.ToastResource(AMMR.strings.cast_server_load_failed))
                }
            }
        }
    }

    // === Setup ===

    /**
     * The position in the current video. Used to restore from process kill.
     */
    private var episodePosition = savedState.get<Long>("episode_position")
        set(value) {
            savedState["episode_position"] = value
            field = value
        }

    /**
     * The current video's quality index. Used to restore from process kill.
     */
    private var qualityIndex = savedState.get<Pair<Int, Int>>("quality_index") ?: Pair(-1, -1)
        set(value) {
            savedState["quality_index"] = value
            field = value
        }

    /**
     * The episode id of the currently loaded episode. Used to restore from process kill.
     */
    private var episodeId = savedState.get<Long>("episode_id") ?: -1L
        set(value) {
            savedState["episode_id"] = value
            field = value
        }

    fun fetchFonts(includeSystemFonts: Boolean): List<String> {
        val fontFiles = mutableListOf<String>()

        storageManager.getFontsDirectory()?.listFiles()?.filter { file ->
            file.name?.lowercase()?.matches(fontExtensionRegex) == true
        }?.mapNotNull {
            try {
                TTFFile.open(it.openInputStream()).families.values.first()
            } catch (_: Exception) {
                null
            }
        }?.let {
            fontFiles.addAll(it)
        }

        if (!includeSystemFonts) {
            return fontFiles.distinct()
        }

        val fontDirectories = listOf(
            "/system/fonts/",
            "/product/fonts/",
        )

        for (directory in fontDirectories) {
            val dir = File(directory)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles()
                files?.filter { file ->
                    file.isFile && file.name.lowercase().matches(fontExtensionRegex)
                }?.forEach { file ->
                    try {
                        fontFiles.add(
                            TTFFile.open(file.inputStream()).families.values.first(),
                        )
                    } catch (_: Exception) { }
                }
            }
        }

        return fontFiles.distinct()
    }

    // === Initialize ===

    fun updateIsLoadingHosters(value: Boolean) {
        updateUiData { it.copy(isLoadingHosters = value) }
    }

    fun updateIsLoadingEpisode(value: Boolean) {
        updateUiData { it.copy(isLoadingEpisode = value) }
    }

    /**
     * Whether this viewModel is initialized with the correct episode.
     */
    private fun needsInit(animeId: Long, episodeId: Long): Boolean {
        return stateData.value.let {
            it.currentAnime?.id != animeId || it.currentEpisode?.id != episodeId
        }
    }

    data class InitResult(
        val hosterList: List<Hoster>?,
        val videoIndex: Pair<Int, Int>,
        val position: Long?,
    )

    class ExceptionWithStringResource(
        message: String,
        val stringResource: StringResource,
    ) : Exception(message)

    suspend fun init(
        animeId: Long,
        initialEpisodeId: Long,
        hostList: String,
        hostIndex: Int,
        vidIndex: Int,
    ): Pair<InitResult, Result<Boolean>> {
        val defaultResult = InitResult(currentHosterList, qualityIndex, null)
        if (!needsInit(animeId, initialEpisodeId)) return Pair(defaultResult, Result.success(true))

        return try {
            getAnime.await(animeId)?.let { anime ->
                val source = sourceManager.getOrStub(anime.source)
                val incognito = getIncognitoState.await(anime.source)

                updateStateData { it.copy(currentAnime = anime, currentSource = source, incognitoMode = incognito) }
                updateUiData { it.copy(animeTitle = anime.title) }
                episodeId = initialEpisodeId

                setupTrackers(anime.id)
                setupEpisodeList(anime)

                val episode = stateData.value.currentPlaylist.firstOrNull { it.id == episodeId }
                    ?: throw ExceptionWithStringResource("No episode loaded", AYMR.strings.no_episode_loaded)
                setupEpisode(episode)

                val skipIntroLength = getAnimeSkipIntroLength()
                updateCastUiData { it.copy(skipIntroLength = skipIntroLength.toLong()) }

                // Write to mpv table
                val parentTitle = anime.parentId?.let { getAnime.await(it)?.title } ?: ""
                setPropertyString("user-data/current-anime/anime-title", anime.title)
                setPropertyString("user-data/current-anime/parent-title", parentTitle)
                setPropertyInt("user-data/current-anime/intro-length", skipIntroLength)
                setPropertyString(
                    "user-data/current-anime/category",
                    getCategories.await(anime.id).joinToString {
                        it.name
                    },
                )

                // Load hosters
                if (hostList.isNotBlank()) {
                    currentHosterList = hostList.toHosterList().ifEmpty {
                        currentHosterList = null
                        throw ExceptionWithStringResource(
                            "Hoster selected from empty list",
                            AYMR.strings.select_hoster_from_empty_list,
                        )
                    }
                    qualityIndex = Pair(hostIndex, vidIndex)
                } else {
                    episodeLoader.getHosters(episode.toDomainEpisode()!!, anime, source)
                        .takeIf { it.isNotEmpty() }
                        ?.also { currentHosterList = it }
                        ?: run {
                            currentHosterList = null
                            throw ExceptionWithStringResource("Hoster list is empty", AYMR.strings.no_hosters)
                        }
                }

                val result = InitResult(
                    hosterList = currentHosterList,
                    videoIndex = qualityIndex,
                    position = episodePosition,
                )

                Pair(result, Result.success(true))
            } ?: Pair(defaultResult, Result.success(false)) // Unlikely but okay
        } catch (e: Throwable) {
            Pair(defaultResult, Result.failure(e))
        }
    }

    private fun setupCustomButtons(buttons: List<CustomButton>) {
        val primaryButton = buttons.firstOrNull { it.isFavorite }

        updateUiData {
            it.copy(
                customButtons = buttons,
                primaryButton = primaryButton ?: it.primaryButton,
                primaryButtonTitle = if (it.primaryButtonTitle.isEmpty() && primaryButton != null) {
                    primaryButton.name
                } else {
                    it.primaryButtonTitle
                },
            )
        }
    }

    private suspend fun setupTrackers(animeId: Long) {
        val tracks = getTracks.await(animeId)
        updateStateData { it.copy(hasTrackers = tracks.isNotEmpty()) }
    }

    private suspend fun setupEpisodeList(anime: Anime) {
        val episodes = getEpisodesByAnimeId.await(anime.id)
            .sortedWith(getEpisodeSort(anime, sortDescending = false))
            .run {
                if (basePreferences.downloadedOnly.get()) {
                    filterDownloaded(anime, downloadCache)
                } else {
                    this
                }
            }
            .map { it.toDbEpisode() }

        val selectedEpisode = episodes.find { it.id == episodeId }
            ?: error("Requested episode of id $episodeId not found in episode list")

        val source = stateData.value.currentSource ?: return

        val filtered = episodes.filterNot {
            (anime.unseenFilterRaw == Anime.EPISODE_SHOW_SEEN && !it.seen) ||
                (anime.unseenFilterRaw == Anime.EPISODE_SHOW_UNSEEN && it.seen) ||
                (
                    anime.downloadedFilterRaw == Anime.EPISODE_SHOW_DOWNLOADED &&
                        !downloadManager.isEpisodeDownloadedOnDisk(
                            it.name,
                            it.scanlator,
                            it.url,
                            // AM (CUSTOM_INFORMATION) -->
                            anime.ogTitle,
                            // <-- AM (CUSTOM_INFORMATION)
                            source,
                        )
                    ) ||
                (
                    anime.downloadedFilterRaw == Anime.EPISODE_SHOW_NOT_DOWNLOADED &&
                        downloadManager.isEpisodeDownloadedOnDisk(
                            it.name,
                            it.scanlator,
                            it.url,
                            // AM (CUSTOM_INFORMATION) -->
                            anime.ogTitle,
                            // <-- AM (CUSTOM_INFORMATION)
                            source,
                        )
                    ) ||
                (
                    anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_BOOKMARKED &&
                        !it.bookmark
                    ) ||
                (
                    anime.bookmarkedFilterRaw == Anime.EPISODE_SHOW_NOT_BOOKMARKED &&
                        it.bookmark
                    ) ||
                (
                    anime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_FILLERMARKED &&
                        !it.fillermark
                    ) ||
                (
                    anime.fillermarkedFilterRaw == Anime.EPISODE_SHOW_NOT_FILLERMARKED &&
                        it.fillermark
                    )
        }.toMutableList()

        if (filtered.all { it.id != episodeId }) {
            filtered += listOf(selectedEpisode)
        }

        updateStateData { it.copy(currentPlaylist = filtered.toList()) }
    }

    private fun isEpisodeOnline(episode: Episode): Boolean? {
        val currentState = stateData.value

        val anime = currentState.currentAnime ?: return null
        val source = currentState.currentSource ?: return null
        return source is AnimeHttpSource &&
            !episodeLoader.isDownload(
                episode.toDomainEpisode()!!,
                anime,
                source,
            )
    }

    private fun setupEpisode(episode: Episode) {
        val currentState = stateData.value

        val currentEpisodeIndex = currentState.currentPlaylist.indexOfFirst {
            episode.id == it.id
        }

        updateStateData {
            it.copy(
                currentEpisode = episode,
                currentPlaylistIndex = currentEpisodeIndex,
                isEpisodeOnline = isEpisodeOnline(episode) == true,
                hasPreviousEpisode = currentEpisodeIndex != 0,
                hasNextEpisode = currentEpisodeIndex != currentState.currentPlaylist.size - 1,
            )
        }

        updateUiData {
            it.copy(mediaTitle = episode.name)
        }

        setPropertyDouble("user-data/current-anime/episode-number", episode.episode_number.toDouble())
    }

    fun setupPlayerOrientation() {
        if (player.isExiting) return
        val orientation = when (playerPreferences.defaultPlayerOrientationType.get()) {
            PlayerOrientation.Free -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            PlayerOrientation.Video -> if ((aspectRatio.value ?: 0.0) > 1.0) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            PlayerOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlayerOrientation.ReversePortrait -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorPortrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            PlayerOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            PlayerOrientation.ReverseLandscape -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
            PlayerOrientation.SensorLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        updatePlaybackData { it.copy(currentOrientation = orientation) }
    }

    private fun cycleRotations() {
        val orientation = when (playbackData.value.currentOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            -> {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            else -> {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        updatePlaybackData { it.copy(currentOrientation = orientation) }
    }

    // === Casting ===

    private fun stopHttpServerService() {
        context.stopService(Intent(context, HttpServerService::class.java))
    }

    private fun getProxyUrl(address: String, url: String, headers: Headers, isTorrent: Boolean): String {
        if (isTorrent) return url
        if (url.toHttpUrlOrNull()?.host?.startsWith(address) == true) return url

        return "http://$address:$castProxyPort".toHttpUrl().newBuilder().apply {
            addPathSegment("proxy")
            addQueryParameter("url", url)
            addQueryParameter("header", json.encodeToString(headers.toMap()))
        }.build().toString()
    }

    private fun getLocalUrl(address: String, url: String): String {
        return "http://$address:$castProxyPort".toHttpUrl().newBuilder().apply {
            addPathSegment("local")
            addQueryParameter("url", url)
        }.build().toString()
    }

    private fun String.setToLocal(): String {
        return toHttpUrl().newBuilder().apply {
            host("localhost")
            port(1)
        }.build().toString()
    }

    private fun startCasting(startPosition: Long = 0) {
        var video = stateData.value.currentVideo ?: return
        val source = stateData.value.currentSource ?: return
        val anime = stateData.value.currentAnime ?: return
        val episode = stateData.value.currentEpisode ?: return

        if (!player.isExiting) {
            mpvCommand("stop")
        }

        pause()
        updatePlaybackData {
            it.copy(currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        }
        updateStateData {
            it.copy(
                hasLoadedTracks = false,
                hasLoadedSubs = false,
                hasLoadedAudio = false,
            )
        }

        viewModelScope.launch {
            val address = context.getWanIp() ?: "127.0.0.1"
            val isTorrent = torrentPreferences.torrServerEnable.get() && isTorrent(video)

            // If the video already requires a http server, we need to relaunch it with the service.
            if (httpServer != null) {
                stopHttpServer()
                val (success, port) = MainActivity.startHttpServerService(context, source.id)
                if (!success) {
                    _eventFlow.emit(Event.ToastResource(AYMR.strings.http_server_start_failure))
                    return@launch
                }

                video = video
                    .copy(
                        videoUrl = video.videoUrl.setToLocal(),
                        subtitleTracks = video.subtitleTracks.map {
                            it.copy(url = it.url.setToLocal())
                        },
                        audioTracks = video.audioTracks.map {
                            it.copy(url = it.url.setToLocal())
                        },
                    )
                    .copyHttpServer(port)
            }

            if (isTorrent) {
                video = withIOContext {
                    TorrentServerService.start(context)
                    val videoUrl = getTorrentUrl(video.videoUrl, video.videoTitle).toHttpUrl().newBuilder()
                        .host(address)
                        .build()
                        .toString()
                    video.copy(
                        videoUrl = videoUrl,
                    )
                }
            } else if (stateData.value.isEpisodeOnline && castProxy) {
                context.startService(
                    Intent(context, CastProxyServerService::class.java)
                        .putExtra(CastProxyServerService.EXTRA_ADDRESS, address),
                )

                val isReady = withTimeoutOrNull(5.seconds) {
                    CastProxyServerService.isRunning.first { it }
                }

                if (isReady != true) {
                    _eventFlow.emit(Event.ToastResource(AMMR.strings.cast_server_start_failed))
                    stopCasting()
                    return@launch
                }
            }

            val headers = video.headers
                ?: (source as? AnimeHttpSource)?.headers
                ?: Headers.EMPTY

            val codecInformation = withContext(Dispatchers.IO) {
                videoInformation.getVideoInformation(
                    videoUrl = video.videoUrl,
                    headers = headers,
                )
            }

            val videoHeaders = video.headers ?: Headers.EMPTY

            val video = if (!stateData.value.isEpisodeOnline) {
                video.copy(
                    videoUrl = getLocalUrl(address, video.videoUrl),
                )
            } else if (castProxy) {
                video.copy(
                    videoUrl = getProxyUrl(address, video.videoUrl, videoHeaders, isTorrent),
                    subtitleTracks = video.subtitleTracks.map {
                        it.copy(url = getProxyUrl(address, it.url, videoHeaders, isTorrent))
                    },
                    audioTracks = video.audioTracks.map {
                        it.copy(url = getProxyUrl(address, it.url, videoHeaders, isTorrent))
                    },
                )
            } else {
                video
            }

            val maxIndex = codecInformation.tracks.maxByOrNull { it.index }?.index?.plus(1) ?: 1
            val externalSubtitleTracks = video.subtitleTracks.mapIndexed { index, track ->
                TrackInformation(
                    index = maxIndex + index,
                    type = "subtitle",
                    contentType = videoInformation.getSubtitleContentType(track.url),
                    title = track.lang,
                    language = "und",
                    contentId = track.url,
                )
            }
            val externalAudioTracks = video.audioTracks.mapIndexed { index, track ->
                TrackInformation(
                    index = maxIndex + externalSubtitleTracks.size + index,
                    type = "audio",
                    contentType = videoInformation.getAudioContentType(track.url),
                    title = track.lang,
                    language = "und",
                    contentId = track.url,
                )
            }
            val subtitleTracks = codecInformation.tracks.filter { it.type == "subtitle" } + externalSubtitleTracks
            val audioTracks = codecInformation.tracks.filter { it.type == "audio" } + externalAudioTracks
            val preferredSubtitle = trackSelect.getPreferredTrackIndex(subtitleTracks, subtitle = true)
            val preferredAudio = trackSelect.getPreferredTrackIndex(audioTracks, subtitle = false)

            val chapters = ChapterUtils.mergeChapters(
                currentChapters = codecInformation.chapters.sortedBy { it.startTime }.map {
                    IndexedSegment(
                        name = it.name,
                        start = it.startTime.toFloat(),
                    )
                },
                stamps = video.timestamps + stateData.value.aniskipChapters,
                codecInformation.duration?.toInt(),
            ).map { it.toSegment() }

            updateCastUiData {
                it.copy(
                    duration = codecInformation.duration?.toLong() ?: 0L,
                    subTracks = subtitleTracks,
                    audioTracks = audioTracks,
                    currentSubId = preferredSubtitle?.index ?: -1L,
                    currentAudioId = preferredAudio?.index ?: -1L,
                    chapters = chapters,
                )
            }

            loadAniSkip(codecInformation.chapters.size, codecInformation.duration?.toInt())

            castManager.stopRemoteMediaClient()
            castManager.startCasting(
                video = video,
                videoInformation = codecInformation,
                subtitleTracks = subtitleTracks,
                audioTracks = audioTracks,
                subtitleId = preferredSubtitle?.index,
                audioId = preferredAudio?.index,
                anime = anime,
                episodeTitle = episode.name,
                startPosition = startPosition,
                playbackRate = mpv.getPropertyDouble("speed") ?: 1.0,
            )

            _eventFlow.emit(Event.UpdateDiscordRPC(false))
        }
    }

    fun stopCasting() {
        castManager.disconnect()
        updateStateData { it.copy(isCasting = false) }
        updateUiData { it.copy(isLoadingEpisode = true) }
        val video = stateData.value.currentVideo
        setVideo(video)
        unpause()
    }

    fun setCastSheet(sheet: CastSheet) {
        updateCastUiData { it.copy(sheetShown = sheet) }
        if (sheet == CastSheet.None) {
            resetDismissSheet()
        }
    }

    fun setCastDialog(dialog: CastDialog) {
        updateCastUiData { it.copy(dialogShown = dialog) }
    }

    fun selectTrack(track: TrackInformation, isAudio: Boolean) {
        if (isAudio) {
            updateCastUiData {
                val index = it.audioTracks.indexOfFirst { t -> track.index == t.index }
                it.copy(
                    audioTracks = it.audioTracks.toMutableList().apply {
                        this[index] = this[index].copy(loading = true, error = false)
                    }.toList(),
                )
            }
        } else {
            updateCastUiData {
                val index = it.subTracks.indexOfFirst { t -> track.index == t.index }
                it.copy(
                    subTracks = it.subTracks.toMutableList().apply {
                        this[index] = this[index].copy(loading = true, error = false)
                    }.toList(),
                )
            }
        }

        castManager.loadTrack(track.index, isAudio)
    }

    private fun trackLoaded(id: Long, success: Boolean, isAudio: Boolean) {
        if (isAudio) {
            updateCastUiData {
                val index = it.audioTracks.indexOfFirst { t -> id == t.index }
                it.copy(
                    audioTracks = it.audioTracks.toMutableList().apply {
                        this[index] = this[index].copy(loading = false, error = !success)
                    }.toList(),
                    currentAudioId = if (success) id else castManager.castState.value.lastLoadedAudioId,
                )
            }
        } else {
            updateCastUiData {
                val index = it.subTracks.indexOfFirst { t -> id == t.index }
                it.copy(
                    subTracks = it.subTracks.toMutableList().apply {
                        this[index] = this[index].copy(loading = false, error = !success)
                    }.toList(),
                    currentSubId = if (success) id else castManager.castState.value.lastLoadedSubId,
                )
            }
        }
    }

    fun castSetSkipIntroLength(value: Long) {
        updateCastUiData { it.copy(skipIntroLength = value) }
        setAnimeSkipIntroLength(value)
    }

    fun castOnSeekIntro() {
        castManager.seekBy(castUiData.value.skipIntroLength)
    }

    fun castSeekTo(value: Long) {
        castManager.seekTo(value)
    }

    fun castStartSeek(position: Float) {
        updateCastUiData {
            it.copy(
                seekPosition = position,
                isSeeking = true,
            )
        }
    }

    fun castEndSeek() {
        castManager.seekTo(castUiData.value.seekPosition.toLong())
        updateCastUiData {
            it.copy(isSeeking = false)
        }
    }

    private fun castOnChapterChanged(chapter: Segment?) {
        updateCastUiData { it.copy(currentChapter = chapter) }
        if (chapter == null) {
            updateCastUiData {
                it.copy(
                    skipIntroText = null,
                    netflixTimeout = null,
                )
            }
            return
        }

        val chapterType = chapter.getChapterType()
        if (chapterType == ChapterType.Other) {
            updateCastUiData {
                it.copy(
                    skipIntroText = null,
                    netflixTimeout = null,
                )
            }
        } else {
            if (netflixStyle) {
                // show a toast with the seconds before the skip
                viewModelScope.launch {
                    _eventFlow.emit(
                        Event.ToastString(
                            "Skip Intro: ${context.stringResource(
                                AYMR.strings.player_aniskip_dontskip_toast,
                                chapter.name.substringBeforeLast(ChapterUtils.ANIYOMI_CHAPTER_IDENTIFIER),
                                defaultWaitingTime,
                            )}",
                        ),
                    )
                }
                updateCastUiData {
                    it.copy(
                        skipIntroText = context.stringResource(AYMR.strings.player_aniskip_dontskip),
                        netflixTimeout = defaultWaitingTime,
                    )
                }
            } else if (autoSkip) {
                castSkipIntro(chapter)
            } else {
                updateSkipIntroButton(chapterType)
            }
        }
    }

    private fun Segment.getChapterType(): ChapterType {
        return name.substringAfterLast(
            delimiter = ChapterUtils.ANIYOMI_CHAPTER_IDENTIFIER,
            missingDelimiterValue = ChapterType.Other.ordinal.toString(),
        ).toInt().let { ChapterType.entries[it] }
    }

    fun castOnSkipIntro() {
        val chapter = castUiData.value.currentChapter ?: return
        if ((castUiData.value.netflixTimeout ?: 0) > 0 && netflixStyle) {
            updateCastUiData { it.copy(netflixTimeout = null) }
            updateSkipIntroButton(chapter.getChapterType())
            return
        }

        updateCastUiData { it.copy(netflixTimeout = null) }
        castSkipIntro(chapter)
    }

    private fun castSkipIntro(chapter: Segment) {
        val nextChapterStart = castUiData.value.chapters.filter { it.start > chapter.start }
            .minByOrNull { it.start }?.start?.toLong()
            ?: castUiData.value.duration
        castManager.seekTo(nextChapterStart)
    }

    // === Load ===

    fun stopHttpServer() {
        httpServer?.stop()
        httpServer = null
    }

    fun cancelHosterVideoLinksJob() {
        getHosterVideoLinksJob?.cancel()
    }

    fun loadHosters(hosterList: List<Hoster>, hosterIndex: Int, videoIndex: Int) {
        val hasFoundPreferredVideo = AtomicBoolean(false)

        updateStateData { it.copy(hosterList = hosterList) }
        updateUiData { it.copy(hosterExpandedList = List(hosterList.size) { true }) }

        val source = stateData.value.currentSource
            ?: throw Exception("No source available")

        getHosterVideoLinksJob?.cancel()
        getHosterVideoLinksJob = viewModelScope.launchIO {
            updateStateData {
                it.copy(
                    hosterState = hosterList.map { hoster ->
                        if (hoster.lazy) {
                            HosterState.Idle(hoster.hosterName)
                        } else if (hoster.videoList == null) {
                            HosterState.Loading(hoster.hosterName)
                        } else {
                            val videoList = hoster.videoList!!
                            HosterState.Ready(
                                hoster.hosterName,
                                videoList,
                                List(videoList.size) { Video.State.QUEUE },
                            )
                        }
                    },
                )
            }

            try {
                coroutineScope {
                    hosterList.mapIndexed { hosterIdx, hoster ->
                        async {
                            val hosterState = episodeLoader.loadHosterVideos(source, hoster)

                            updateHosterStateAt(hosterIdx, hosterState)

                            if (hosterState is HosterState.Ready) {
                                if (hosterIdx == hosterIndex) {
                                    hosterState.videoList.getOrNull(videoIndex)?.let {
                                        hasFoundPreferredVideo.set(true)
                                        val success = loadVideo(it, hosterIndex, videoIndex)
                                        if (!success) {
                                            hasFoundPreferredVideo.set(false)
                                        }
                                    }
                                }

                                val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                                if (prefIndex != -1 && hosterIndex == -1) {
                                    if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                        if (uiData.value.selectedHosterVideoIndex == Pair(-1, -1)) {
                                            val success =
                                                loadVideo(
                                                    hosterState.videoList[prefIndex],
                                                    hosterIdx,
                                                    prefIndex,
                                                )
                                            if (!success) {
                                                hasFoundPreferredVideo.set(false)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }.awaitAll()

                    if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                        if (uiData.value.selectedHosterVideoIndex == Pair(-1, -1)) {
                            val (hosterIdx, videoIdx) = hosterLoader.selectBestVideo(stateData.value.hosterState)
                            if (hosterIdx == -1) {
                                throw ExceptionWithStringResource(
                                    "No available videos",
                                    AYMR.strings.no_available_videos,
                                )
                            }

                            val video = (stateData.value.hosterState[hosterIdx] as HosterState.Ready)
                                .videoList[videoIdx]
                            loadVideo(video, hosterIdx, videoIdx)
                        }
                    }
                }
            } catch (e: CancellationException) {
                updateStateData {
                    it.copy(
                        hosterState = it.hosterList.map { h ->
                            HosterState.Idle(h.hosterName)
                        },
                    )
                }

                throw e
            }
        }
    }

    fun loadBestVideo(): Boolean {
        val (hosterIdx, videoIdx) = hosterLoader.selectBestVideo(stateData.value.hosterState)
        if (hosterIdx == -1) return false
        val newVideo = (stateData.value.hosterState[hosterIdx] as HosterState.Ready).videoList[videoIdx]
        viewModelScope.launchIO {
            loadVideo(newVideo, hosterIdx, videoIdx)
        }
        return true
    }

    /**
     * Try and load a video
     *
     * returns true if successful, false if not
     */
    private suspend fun loadVideo(video: Video, hosterIndex: Int, videoIndex: Int): Boolean {
        val source = stateData.value.currentSource
            ?: throw Exception("No source loaded")
        val currentUi = uiData.value
        val selectedHosterState = (stateData.value.hosterState[hosterIndex] as? HosterState.Ready)
            ?: return false

        val oldSelectedIndex = currentUi.selectedHosterVideoIndex
        updateUiData {
            it.copy(
                isLoadingEpisode = true,
                selectedHosterVideoIndex = Pair(hosterIndex, videoIndex),
                previousPauseState = it.previousPauseState ?: playbackData.value.paused,
            )
        }
        updateStateData {
            it.copy(
                hosterState = getHosterStateAt(
                    hosters = it.hosterState,
                    index = hosterIndex,
                    state = selectedHosterState.getChangedAt(videoIndex, video, Video.State.LOAD_VIDEO),
                ),
            )
        }

        // Pause until everything has loaded
        pause()

        val resolvedVideo = if (selectedHosterState.videoState[videoIndex] != Video.State.READY) {
            hosterLoader.getResolvedVideo(source, video)
        } else {
            video
        }

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            if (stateData.value.currentVideo == null) {
                updateHosterStateAt(hosterIndex, selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR))

                val (newHosterIdx, newVideoIdx) = hosterLoader.selectBestVideo(stateData.value.hosterState)
                if (newHosterIdx == -1) {
                    if (stateData.value.hosterState.any { it is HosterState.Loading }) {
                        updateUiData { it.copy(selectedHosterVideoIndex = Pair(-1, -1)) }
                        return false
                    } else {
                        throw ExceptionWithStringResource("No available videos", AYMR.strings.no_available_videos)
                    }
                }

                val newVideo = (stateData.value.hosterState[newHosterIdx] as HosterState.Ready).videoList[newVideoIdx]
                return loadVideo(newVideo, newHosterIdx, newVideoIdx)
            } else {
                updateStateData {
                    it.copy(
                        hosterState = getHosterStateAt(
                            hosters = it.hosterState,
                            index = hosterIndex,
                            state = selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                        ),
                    )
                }
                updateUiData {
                    it.copy(
                        selectedHosterVideoIndex = oldSelectedIndex,
                    )
                }
                return false
            }
        }

        updateHosterStateAt(
            index = hosterIndex,
            state = selectedHosterState.getChangedAt(videoIndex, resolvedVideo, Video.State.READY),
        )
        updateStateData { it.copy(currentVideo = resolvedVideo) }

        if (stateData.value.hasLoadedTracks) {
            clearTracks()
        }

        viewModelScope.launchIO {
            loadThumbnails(resolvedVideo, source)
        }

        qualityIndex = Pair(hosterIndex, videoIndex)
        setVideo(resolvedVideo)
        return true
    }

    private fun setVideo(video: Video?) {
        if (player.isExiting) return
        if (video == null) return
        stopHttpServer()

        val castState = castManager.castState.value
        val isLoadingEpisode = if (castState.hasLoadedVideo) {
            castUiData.value.isLoadingEpisode
        } else {
            uiData.value.isLoadingEpisode
        }
        val resumePosition = if (isLoadingEpisode) {
            stateData.value.currentEpisode?.let { episode ->
                val preservePos = playerPreferences.preserveWatchingPosition.get()
                if (episode.seen && !preservePos) {
                    0L
                } else {
                    episode.last_second_seen / 1000L
                }
            }
        } else if (castState.hasLoadedVideo) {
            castState.position
        } else {
            playbackData.value.position.toLong()
        }

        if (stateData.value.isCasting) {
            startCasting(startPosition = resumePosition ?: 0L)
            return
        }

        updateStateData { it.copy(isStopped = false) }
        setHttpOptions(video)
        resumePosition?.let {
            mpvCommand("set", "start", it.toString())
        }

        // We handle selecting these in the viewmodel
        val mpvOpts = listOf(
            Pair("sid", "no"),
            Pair("aid", "no"),
            Pair("vid", "auto"),
        )
        val videoOptions = (video.mpvArgs + mpvOpts).joinToString(",") { (option, value) ->
            "$option=\"$value\""
        }

        if (torrentPreferences.torrServerEnable.get() && isTorrent(video)) {
            launchIO {
                TorrentServerService.start(context)
                torrentLinkHandler(video.videoUrl, video.videoTitle, videoOptions)
            }
        } else {
            launchIO {
                val httpSource = stateData.value.currentSource as? AnimeHttpSource
                var videoUrl: String = video.videoUrl
                if (video.usesHttpServer() && httpSource != null) {
                    val port = try {
                        httpServer = httpSource.createHttpServer()
                        httpServer?.start()
                        httpServer?.listeningPort ?: 0
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Failed to start http server" }
                        _eventFlow.emit(Event.ToastResource(AYMR.strings.http_server_start_failure))
                        return@launchIO
                    }

                    val newVideo = video.copyHttpServer(port)
                    videoUrl = newVideo.videoUrl
                    updateStateData { it.copy(currentVideo = newVideo) }
                }

                mpvCommand(
                    "loadfile",
                    parseVideoUrl(videoUrl)!!,
                    "replace",
                    "0",
                    videoOptions,
                )
            }
        }

        viewModelScope.launch {
            _eventFlow.emit(Event.UpdateDiscordRPC(false))
        }
    }

    private suspend fun torrentLinkHandler(videoUrl: String, title: String, videoOptions: String) {
        val videoTorrentUrl = getTorrentUrl(videoUrl, title)
        mpvCommand(
            "loadfile",
            videoTorrentUrl,
            "replace",
            "0",
            videoOptions,
        )
    }

    private suspend fun getTorrentUrl(videoUrl: String, title: String): String {
        var index = 0

        // check if link is from localSource
        if (videoUrl.startsWith("content://")) {
            val videoInputStream = context.contentResolver.openInputStream(videoUrl.toUri())
            val torrent = torrentServerApi.uploadTorrent(videoInputStream!!, title, false)
            return torrentServerUtils.getTorrentPlayLink(torrent, 0)
        }

        // check if link is from magnet, in that check if index is present
        if (videoUrl.startsWith("magnet")) {
            if (videoUrl.contains("index=")) {
                index = try {
                    videoUrl.substringAfter("index=").substringBefore("&").toInt()
                } catch (_: NumberFormatException) {
                    0
                }
            }
        }

        val currentTorrent = torrentServerApi.addTorrent(videoUrl, title, "", "", false)
        return torrentServerUtils.getTorrentPlayLink(currentTorrent, index)
    }

    private fun isTorrent(video: Video): Boolean {
        if (video.videoUrl.startsWith(torrentServerApi.hostUrl)) {
            return true
        }

        if (video.videoUrl.startsWith("magnet")) {
            return true
        }

        return video.videoUrl.endsWith("torrent")
    }

    private suspend fun loadThumbnails(video: Video, source: AnimeSource?) {
        if (source is AnimeHttpSource) {
            try {
                val thumbInfo = source.getVideoThumbnails(video)
                if (thumbInfo != null) {
                    updatePlaybackData {
                        it.copy(
                            thumbnailInfo = ThumbnailInfo(
                                tileInfo = thumbInfo.tileInfo.sortedBy { it.timeMs },
                                imageTileUrls = thumbInfo.imageTileUrls,
                            ),
                        )
                    }

                    // Preload first 2 tilemaps
                    thumbInfo.imageTileUrls.take(2).forEachIndexed { index, tileUrl ->
                        val bitmap = source.getImageTile(tileUrl)
                        if (bitmap != null) {
                            thumbnailTileCache[index] = bitmap
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { "Failed to fetch thumbnails" }
            }
        }
    }

    private fun parseVideoUrl(videoUrl: String?): String? {
        return videoUrl?.toUri()?.resolveUri(context)
            ?: videoUrl
    }

    private fun setHttpOptions(video: Video) {
        if (!stateData.value.isEpisodeOnline) return
        val source = stateData.value.currentSource as? AnimeHttpSource
            ?: return

        val headers = (video.headers ?: source.headers)
            .toMultimap()
            .mapValues { it.value.firstOrNull() ?: "" }

        val httpHeaderString = headers.map {
            it.key + ": " + it.value.replace(",", "\\,")
        }.joinToString(",")

        mpv.setOptionString("http-header-fields", httpHeaderString)
    }

    private fun eofReached(eofReached: Boolean) {
        if (eofReached && uiData.value.autoPlayEnabled) {
            nextEpisode(next = true, autoplay = true)
        }
    }

    private fun endFile(node: MPVNode) {
        val errorNode = node.asMap()?.get("file_error") ?: return
        var errorMessage = errorNode.asString() ?: "Error: File ended"

        val httpError = player.getHttpError()
        if (!httpError.isNullOrEmpty()) {
            errorMessage += ": $httpError"
            player.resetHttpError()
        }

        logcat(LogPriority.ERROR) { errorMessage }
        viewModelScope.launch {
            _eventFlow.emit(Event.ToastString(errorMessage))
        }

        setCurrentVideoError()

        if (playerPreferences.switchOnFailure.get()) {
            if (!loadBestVideo()) {
                viewModelScope.launch { _eventFlow.emit(Event.Finish) }
            }
        } else {
            updateStateData { it.copy(isStopped = true) }
        }
    }

    fun setCurrentVideoError() {
        val (hosterIdx, videoIdx) = uiData.value.selectedHosterVideoIndex
        val currentHosterState = (stateData.value.hosterState[hosterIdx] as? HosterState.Ready) ?: return
        val currentVideo = currentHosterState.videoList[videoIdx]

        updateStateData {
            it.copy(
                currentVideo = null,
                hosterState = getHosterStateAt(
                    hosters = it.hosterState,
                    index = hosterIdx,
                    state = currentHosterState.getChangedAt(videoIdx, currentVideo, Video.State.ERROR),
                ),
            )
        }
    }

    fun onVideoClicked(hosterIndex: Int, videoIndex: Int) {
        val hosterState = stateData.value.hosterState[hosterIndex] as? HosterState.Ready
        val video = hosterState?.videoList
            ?.getOrNull(videoIndex)
            ?: return // How did we get here?

        val videoState = hosterState.videoState
            .getOrNull(videoIndex)
            ?: return

        if (videoState == Video.State.ERROR) {
            return
        }

        viewModelScope.launchIO {
            val success = loadVideo(video, hosterIndex, videoIndex)
            if (success) {
                if (uiData.value.sheetShown == Sheets.QualityTracks ||
                    castUiData.value.sheetShown == CastSheet.Quality
                ) {
                    dismissSheet()
                }
            }
        }
    }

    fun onHosterClicked(index: Int) {
        when (stateData.value.hosterState[index]) {
            is HosterState.Ready -> {
                updateUiData {
                    it.copy(
                        hosterExpandedList = it.hosterExpandedList.toMutableList().apply {
                            this[index] = !it.hosterExpandedList[index]
                        }.toList(),
                    )
                }
            }
            is HosterState.Idle -> {
                val source = stateData.value.currentSource
                    ?: throw Exception("Source not loaded")

                val hosterName = stateData.value.hosterList[index].hosterName
                updateHosterStateAt(index, HosterState.Loading(hosterName))

                viewModelScope.launchIO {
                    val hosterState = episodeLoader.loadHosterVideos(
                        source = source,
                        hoster = stateData.value.hosterList[index],
                        force = true,
                    )
                    updateHosterStateAt(index, hosterState)
                }
            }
            is HosterState.Error, is HosterState.Loading -> { }
        }
    }

    private fun getHosterStateAt(hosters: List<HosterState>, index: Int, state: HosterState): List<HosterState> {
        return hosters.toMutableList().apply {
            this[index] = state
        }.toList()
    }

    private fun updateHosterStateAt(index: Int, state: HosterState) {
        updateStateData {
            it.copy(
                hosterState = getHosterStateAt(it.hosterState, index, state),
            )
        }
    }

    private fun fileLoaded() {
        if (player.isExiting) return

        setMpvOptions()
        setMpvMediaTitle()
        setupChapters()
        setupPlayerOrientation()
        checkFileLoaded()

        // AniSkip stuff
        val chapterCount = mpv.getPropertyInt("chapter-list/count") ?: 0
        val duration = playbackData.value.duration
        loadAniSkip(chapterCount, duration)
    }

    private fun loadAniSkip(chapterCount: Int, duration: Int?) {
        viewModelScope.launchIO {
            if (introSkipEnabled && aniSkipEnabled && !(disableAniSkipOnChapters && chapterCount > 0)) {
                aniSkipResponse(duration)?.let { stamps ->
                    updateStateData { it.copy(aniskipChapters = stamps) }
                    if (!stateData.value.isCasting) {
                        addTimeStamps(stamps)
                    }
                } ?: run {
                    updateStateData { it.copy(aniskipChapters = emptyList()) }
                }
            } else {
                updateStateData { it.copy(aniskipChapters = emptyList()) }
            }
        }
    }

    private fun setMpvOptions() {
        val video = stateData.value.currentVideo ?: return

        // Only check for `MPV_ARGS_TAG` on downloaded videos
        if (listOf("file", "content", "data").none { video.videoUrl.startsWith(it) }) {
            return
        }

        try {
            val metadata = mpv.getPropertyNode("metadata")?.asMap()
                ?: return

            val opts = metadata[Video.MPV_ARGS_TAG]
                ?.asString()
                ?.split(";")
                ?.map { it.split("=", limit = 2) }
                ?: return

            opts.forEach { (option, value) ->
                setPropertyString(option, value)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to read video metadata" }
        }
    }

    private fun setMpvMediaTitle() {
        val anime = stateData.value.currentAnime ?: return
        val episode = stateData.value.currentEpisode ?: return

        // Write to mpv table
        setPropertyString("user-data/current-anime/episode-title", episode.name)

        val epNumber = episode.episode_number.let { number ->
            if (ceil(number) == floor(number)) number.toInt() else number
        }.toString().padStart(2, '0')

        val title = context.stringResource(
            AYMR.strings.mpv_media_title,
            anime.title,
            epNumber,
            episode.name,
        )

        setPropertyString("force-media-title", title)
    }

    private fun setupChapters() {
        val timeStamps = stateData.value.currentVideo?.timestamps?.takeIf { it.isNotEmpty() }
            ?.map { timeStamp ->
                if (timeStamp.name.isEmpty() && timeStamp.type != ChapterType.Other) {
                    timeStamp.copy(
                        name = timeStamp.type.getStringRes()?.let { context.stringResource(it) } ?: "",
                    )
                } else {
                    timeStamp
                }
            }
            ?: return

        addTimeStamps(timeStamps)
    }

    private fun addTimeStamps(timeStamps: List<TimeStamp>) {
        if (timeStamps.isEmpty()) return

        val current = (
            mpv.getPropertyNode("chapter-list")
                ?.toObject<List<ChapterNode>>(json) ?: emptyList()
            )
            .map { IndexedSegment(name = it.chapterTitle, start = it.time, index = 0) }
        val merged = ChapterUtils.mergeChapters(current, timeStamps, playbackData.value.duration)
        val node = MPVNode.ArrayNode(
            merged.map { c ->
                MPVNode.MapNode(
                    value = mapOf(
                        "time" to MPVNode.DoubleNode(c.start.toDouble()),
                        "title" to MPVNode.StringNode(c.name),
                    ),
                )
            }.toTypedArray(),
        )
        setPropertyNode("chapter-list", node)
    }

    /**
     * Check when file has loaded and see if the player can be (un)paused.
     *
     * If external subs/audio tracks was selected, wait until mpv has fetched them.
     */
    private fun checkFileLoaded() {
        if (uiData.value.isLoadingEpisode && stateData.value.hasLoadedSubs && stateData.value.hasLoadedAudio) {
            uiData.value.previousPauseState?.let { shouldPause ->
                if (shouldPause) pause() else unpause()
            }

            updateUiData {
                it.copy(
                    isLoadingEpisode = false,
                    previousPauseState = null,
                )
            }
        }
    }

    fun clearTracks() {
        updateStateData {
            it.copy(
                externalSubtitleTracks = emptyList(),
                externalAudioTracks = emptyList(),
                hasLoadedTracks = false,
                hasLoadedSubs = false,
                hasLoadedAudio = false,
            )
        }
    }

    /**
     * When all subtitle/audio tracks are loaded, select the preferred one based on preferences,
     * or select the first one in the list if trackSelect fails.
     */
    fun onTrackListChanged(tracks: MPVNode) {
        val tracks = tracks.toObject<List<TrackNode>>(json).ifEmpty { return }
        updateStateData {
            it.copy(
                subtitleTracks = tracks.filter { it.isSubtitle }
                    .filterNot { it.title?.startsWith(MpvVideoTrack.TRACK_TITLE_TAG) == true },
                audioTracks = tracks.filter { it.isAudio }
                    .filterNot { it.title?.startsWith(MpvVideoTrack.TRACK_TITLE_TAG) == true },
            )
        }

        if (stateData.value.hasLoadedTracks) {
            onTrackAdded(tracks)
        } else {
            updateStateData { it.copy(hasLoadedTracks = true) }
            onTracksLoaded(tracks)
        }
    }

    /**
     * Called when a new track has been added to mpv
     *
     * Every new external tracks needs to be tracked internally
     */
    private fun onTrackAdded(tracks: List<TrackNode>) {
        val externalSubtitle = tracks.filter {
            it.isSubtitle && it.title?.startsWith(MpvVideoTrack.TRACK_TITLE_TAG) == true
        }
        val externalAudio = tracks.filter {
            it.isAudio && it.title?.startsWith(MpvVideoTrack.TRACK_TITLE_TAG) == true
        }

        externalSubtitle.forEach { track ->
            val idx = track.title!!.split("=")[1].toInt()
            val external = stateData.value.externalSubtitleTracks[idx]

            if (external.id != null) {
                // External subtitle has already been added
                return@forEach
            }

            updateSubtitleTrackAt(idx) {
                it.copy(id = track.id, state = TrackState.Loaded, lang = track.getLanguage())
            }
            updateStateData { it.copy(hasLoadedSubs = true) }
            checkFileLoaded()
            selectSubById(track.id)
        }

        externalAudio.forEach { track ->
            val idx = track.title!!.split("=")[1].toInt()
            val external = stateData.value.externalAudioTracks[idx]

            if (external.id != null) {
                // External audio has already been added
                return@forEach
            }

            updateAudioTrackAt(idx) {
                it.copy(id = track.id, state = TrackState.Loaded, lang = track.getLanguage())
            }
            updateStateData { it.copy(hasLoadedAudio = true) }
            checkFileLoaded()
            selectAudioById(track.id, false)
        }
    }

    /**
     * Called when embedded tracks are first loaded
     */
    private fun onTracksLoaded(tracks: List<TrackNode>) {
        val embeddedSubs = tracks.filter { it.isSubtitle }
        val embeddedAudio = tracks.filter { it.isAudio }
        val currentVideo = stateData.value.currentVideo
        val externalSubs = currentVideo?.subtitleTracks.orEmpty().distinctBy { it.url }
            .mapIndexed { idx, track -> MpvVideoTrack.External(track, idx) }
        val externalAudio = currentVideo?.audioTracks.orEmpty().distinctBy { it.url }
            .mapIndexed { idx, track -> MpvVideoTrack.External(track, idx) }

        updateStateData {
            it.copy(
                externalSubtitleTracks = externalSubs,
                externalAudioTracks = externalAudio,
            )
        }

        val preferredSubtitle = trackSelect.getPreferredTrackIndex(
            tracks = embeddedSubs.map { MpvVideoTrack.Internal(it) } + externalSubs,
            subtitle = true,
        )
        if (preferredSubtitle == null) {
            updateStateData { it.copy(hasLoadedSubs = true) }
        } else {
            selectSub(preferredSubtitle)
        }

        val preferredAudio = trackSelect.getPreferredTrackIndex(
            tracks = embeddedAudio.map { MpvVideoTrack.Internal(it) } + externalAudio,
            subtitle = false,
        )
        if (preferredAudio == null) {
            updateStateData { it.copy(hasLoadedAudio = true) }
        } else {
            selectAudio(preferredAudio, true)
        }
    }

    private fun updateSubtitleTrackAt(index: Int, transform: (MpvVideoTrack.External) -> MpvVideoTrack.External) {
        updateStateData {
            it.copy(
                externalSubtitleTracks = it.externalSubtitleTracks.toMutableList().apply {
                    this[index] = transform(this[index])
                }.toList(),
            )
        }
    }

    private fun updateAudioTrackAt(index: Int, transform: (MpvVideoTrack.External) -> MpvVideoTrack.External) {
        updateStateData {
            it.copy(
                externalAudioTracks = it.externalAudioTracks.toMutableList().apply {
                    this[index] = transform(this[index])
                }.toList(),
            )
        }
    }

    fun addSubtitle(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(context) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(context) else null
        if (name == null) {
            mpvCommand("sub-add", path, "cached")
        } else {
            mpvCommand("sub-add", path, "cached", name)
        }
    }

    fun selectSub(track: MpvVideoTrack) {
        when (track) {
            is MpvVideoTrack.External -> {
                if (track.id == null) {
                    updateSubtitleTrackAt(track.index) {
                        it.copy(state = TrackState.Loading)
                    }
                    viewModelScope.launchIO {
                        mpvCommand(
                            "sub-add",
                            track.data.url,
                            "auto",
                            "${MpvVideoTrack.TRACK_TITLE_TAG}=${track.index}",
                        )
                    }
                } else {
                    updateStateData { it.copy(hasLoadedSubs = true) }
                    checkFileLoaded()
                    selectSubById(track.id)
                }
            }
            is MpvVideoTrack.Internal -> {
                updateStateData { it.copy(hasLoadedSubs = true) }
                checkFileLoaded()
                selectSubById(track.data.id)
            }
        }
    }

    private fun selectSubById(id: Int) {
        val selectedSubs = Pair(mpv.getPropertyInt("sid"), mpv.getPropertyInt("secondary-sid"))
        when (id) {
            selectedSubs.first -> Pair(selectedSubs.second, null)
            selectedSubs.second -> Pair(selectedSubs.first, null)
            else -> if (selectedSubs.first != null) Pair(selectedSubs.first, id) else Pair(id, null)
        }.let {
            it.second?.let { setPropertyInt("secondary-sid", it) }
                ?: setPropertyBoolean("secondary-sid", false)
            it.first?.let { setPropertyInt("sid", it) } ?: setPropertyBoolean("sid", false)
        }
    }

    private fun onSubtitleTrackSelectChange() {
        val id = mpv.getPropertyInt("sid")
        val sid = mpv.getPropertyInt("secondary-sid")

        updateStateData {
            it.copy(
                externalSubtitleTracks = it.externalSubtitleTracks.map { tracks ->
                    tracks.copy(
                        mainSelection = when (tracks.id) {
                            null -> -1
                            id -> 0
                            sid -> 1
                            else -> -1
                        },
                    )
                },
            )
        }
    }

    fun addAudio(uri: Uri) {
        val url = uri.toString()
        val isContentUri = url.startsWith("content://")
        val path = (if (isContentUri) uri.openContentFd(context) else url)
            ?: return
        val name = if (isContentUri) uri.getFileName(context) else null
        if (name == null) {
            mpvCommand("audio-add", path, "cached")
        } else {
            mpvCommand("audio-add", path, "cached", name)
        }
    }

    fun selectAudio(track: MpvVideoTrack, force: Boolean = false) {
        when (track) {
            is MpvVideoTrack.External -> {
                if (track.id == null) {
                    updateAudioTrackAt(track.index) {
                        it.copy(state = TrackState.Loading)
                    }
                    viewModelScope.launchIO {
                        mpvCommand(
                            "audio-add",
                            track.data.url,
                            "auto",
                            "${MpvVideoTrack.TRACK_TITLE_TAG}=${track.index}",
                        )
                    }
                } else {
                    updateStateData { it.copy(hasLoadedAudio = true) }
                    checkFileLoaded()
                    selectAudioById(track.id, force)
                }
            }
            is MpvVideoTrack.Internal -> {
                updateStateData { it.copy(hasLoadedAudio = true) }
                checkFileLoaded()
                selectAudioById(track.data.id, force)
            }
        }
    }

    private fun selectAudioById(id: Int, force: Boolean) {
        if (!force && id == mpv.getPropertyInt("aid")) {
            setPropertyBoolean("aid", false)
        } else {
            setPropertyInt("aid", id)
        }
    }

    private fun onAudioTrackSelectChange() {
        val id = mpv.getPropertyInt("aid")

        updateStateData {
            it.copy(
                externalAudioTracks = it.externalAudioTracks.map { tracks ->
                    tracks.copy(
                        mainSelection = when (tracks.id) {
                            null -> -1
                            id -> 0
                            else -> -1
                        },
                    )
                },
            )
        }
    }

    fun onTrackLoadedFailure(url: String) {
        val subtitleIdx = stateData.value.externalSubtitleTracks.indexOfFirst {
            it.data.url == url
        }
        if (subtitleIdx != -1) {
            updateSubtitleTrackAt(subtitleIdx) {
                it.copy(state = TrackState.Error)
            }
            updateStateData { it.copy(hasLoadedSubs = true) }
            checkFileLoaded()
        }
        val audioIdx = stateData.value.externalAudioTracks.indexOfFirst {
            it.data.url == url
        }
        if (audioIdx != -1) {
            updateAudioTrackAt(audioIdx) {
                it.copy(state = TrackState.Error)
            }
            updateStateData { it.copy(hasLoadedAudio = true) }
            checkFileLoaded()
        }
    }

    fun onChapterListChanged(node: MPVNode) {
        val chapters = node.toObject<List<ChapterNode>>(json).map {
            it.toSegment()
        }
        updateStateData { it.copy(chapters = chapters) }
    }

    private data class EpisodeLoadResult(
        val hosterList: List<Hoster>?,
        val episodeTitle: String,
    )

    /**
     * Load an episode, returning the hosterlist, episode title, and source
     * associated with the episode.
     */
    private suspend fun loadEpisode(episodeId: Long?): EpisodeLoadResult? {
        val anime = stateData.value.currentAnime ?: return null
        val source = sourceManager.getOrStub(anime.source)

        val chosenEpisode = stateData.value.currentPlaylist.firstOrNull { ep ->
            ep.id == episodeId
        } ?: return null

        setupEpisode(chosenEpisode)

        return withIOContext {
            try {
                currentHosterList = episodeLoader.getHosters(
                    episode = chosenEpisode.toDomainEpisode()!!,
                    anime,
                    source,
                )
                this@PlayerViewModel.episodeId = chosenEpisode.id!!
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                logcat(LogPriority.ERROR, e) { e.message ?: "Error getting links" }
            }

            EpisodeLoadResult(
                hosterList = currentHosterList,
                episodeTitle = "${anime.title} - ${chosenEpisode.name}",
            )
        }
    }

    /**
     * Load next or previous episode
     */
    fun nextEpisode(next: Boolean, autoplay: Boolean = false) {
        val currentIndex = stateData.value.currentPlaylistIndex
        val newIndex = if (next) currentIndex + 1 else currentIndex - 1

        if (newIndex !in stateData.value.currentPlaylist.indices) return
        val episodeId = stateData.value.currentPlaylist.getOrNull(newIndex)?.id ?: return

        changeEpisode(episodeId, autoplay)
    }

    /**
     * Switches to the episode based on [episodeId],
     *
     * @param episodeId id of the episode to switch the player to
     * @param autoPlay whether the episode is switching due to auto play
     */
    fun changeEpisode(episodeId: Long?, autoPlay: Boolean = false) {
        if (stateData.value.isCasting) {
            castManager.stopRemoteMediaClient()
            updateCastUiData { it.copy(isLoadingEpisode = true) }
        } else {
            pause()
            clearTracks()
        }

        updateStateData { it.copy(hosterList = emptyList()) }
        updateUiData {
            it.copy(
                sheetShown = Sheets.None,
                panelShown = Panels.None,
                isLoadingEpisode = true,
                isLoadingHosters = true,
                previousPauseState = false,
                hosterExpandedList = emptyList(),
                selectedHosterVideoIndex = Pair(-1, -1),
            )
        }
        cancelHosterVideoLinksJob()
        thumbnailTileCache.clear()
        thumbnailFetchJob?.cancel()
        lastThumbnailFetch = 0L

        viewModelScope.launch {
            val switchMethod = loadEpisode(episodeId)
            updateUiData { it.copy(isLoadingHosters = false) }

            if (switchMethod == null) {
                if (stateData.value.currentAnime != null && !autoPlay) {
                    _eventFlow.emit(Event.ToastResource(AYMR.strings.no_next_episode))
                }
                return@launch
            }

            if (switchMethod.hosterList != null) {
                when {
                    switchMethod.hosterList.isEmpty() -> _eventFlow.emit(
                        Event.InitialEpisodeError(
                            ExceptionWithStringResource(
                                "Hoster list is empty",
                                AYMR.strings.no_hosters,
                            ),
                        ),
                    )
                    else -> {
                        loadHosters(
                            hosterList = switchMethod.hosterList,
                            hosterIndex = -1,
                            videoIndex = -1,
                        )
                    }
                }
            } else {
                logcat(LogPriority.ERROR) { "Error getting links" }
            }

            if (pipEpisodeToasts) {
                _eventFlow.emit(Event.EpisodeTitle(switchMethod.episodeTitle))
            }
        }
    }

    // === Controls ===

    fun onKey(keyEvent: KeyEvent): Boolean {
        return player.onKey(keyEvent)
    }

    fun updateHasPip(value: Boolean) {
        updateStateData { it.copy(isPipAvailable = value) }
    }

    fun pauseUnpause() = mpvCommand("cycle", "pause")
    fun pause() {
        setPropertyBoolean("pause", true)

        // PiP needs to know the state immediately, so we update it here
        updatePlaybackData { it.copy(paused = true) }
    }
    fun unpause() {
        setPropertyBoolean("pause", false)
        updatePlaybackData { it.copy(paused = false) }
    }

    fun showControls() {
        val currentUi = uiData.value
        if (currentUi.sheetShown != Sheets.None ||
            currentUi.panelShown != Panels.None ||
            currentUi.dialogShown != Dialogs.None
        ) {
            return
        }
        updateUiData {
            it.copy(
                controlsShown = true,
                statusBarShown = showStatusBar,
            )
        }
    }

    fun hideControls() {
        updateUiData {
            it.copy(
                controlsShown = false,
                statusBarShown = false,
            )
        }
    }

    fun hideSeekBar() {
        updateUiData { it.copy(seekBarShown = false) }
    }

    fun showSeekBar() {
        if (uiData.value.sheetShown != Sheets.None) return
        updateUiData { it.copy(seekBarShown = true) }
    }

    fun toggleDurationTimer() {
        val newValue = !uiData.value.invertDuration
        playerPreferences.invertDuration.set(newValue)
        updateUiData { it.copy(invertDuration = newValue) }
        updateCastUiData { it.copy(invertDurationTimer = newValue) }
    }

    fun dismissSheet() {
        updateUiData { it.copy(dismissSheet = true) }
    }

    private fun resetDismissSheet() {
        updateUiData { it.copy(dismissSheet = false) }
    }

    fun setSheet(sheet: Sheets) {
        updateUiData { it.copy(sheetShown = sheet) }
        if (sheet == Sheets.None) {
            resetDismissSheet()
            showControls()
        } else {
            hideControls()
            updateUiData {
                it.copy(
                    panelShown = Panels.None,
                    dialogShown = Dialogs.None,
                )
            }
        }
    }

    fun setPanel(panel: Panels) {
        updateUiData { it.copy(panelShown = panel) }
        if (panel == Panels.None) {
            showControls()
        } else {
            hideControls()
            updateUiData {
                it.copy(
                    sheetShown = Sheets.None,
                    dialogShown = Dialogs.None,
                )
            }
        }
    }

    fun setDialog(dialog: Dialogs) {
        updateUiData { it.copy(dialogShown = dialog) }
        if (dialog == Dialogs.None) {
            showControls()
        } else {
            hideControls()
            updateUiData {
                it.copy(
                    sheetShown = Sheets.None,
                    panelShown = Panels.None,
                )
            }
        }
    }

    fun changeBrightnessTo(brightness: Float) {
        updatePlaybackData { it.copy(currentBrightness = brightness.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)) }
    }

    fun displayBrightnessSlider(show: Boolean) {
        updateUiData { it.copy(isBrightnessSliderShown = show) }
    }

    fun changeVolumeBy(change: Int) {
        val mpvVolume = mpv.getPropertyInt("volume")
        if ((stateData.value.volumeBoostCap ?: audioPreferences.volumeBoostCap.get()) > 0 &&
            playbackData.value.currentVolume == maxVolume
        ) {
            if (mpvVolume == 100 && change < 0) changeVolumeTo(playbackData.value.currentVolume + change)

            val finalMPVVolume = (mpvVolume?.plus(change))?.coerceAtLeast(100) ?: 100
            if (finalMPVVolume in
                100..(stateData.value.volumeBoostCap ?: audioPreferences.volumeBoostCap.get()) + 100
            ) {
                changeMPVVolumeTo(finalMPVVolume)
                return
            }
        }
        changeVolumeTo(playbackData.value.currentVolume + change)
    }

    fun setVolumeTo(volume: Int) {
        updatePlaybackData { it.copy(currentVolume = volume) }
    }

    fun changeVolumeTo(volume: Int) {
        val newVolume = volume.coerceIn(0..maxVolume)
        audioManager.setVolume(newVolume)
        playerPreferences.playerVolumeValue.set(newVolume)
        updatePlaybackData { it.copy(currentVolume = newVolume) }
    }

    fun changeMPVVolumeTo(volume: Int) {
        setPropertyInt("volume", volume)
    }

    fun displayVolumeSlider(show: Boolean) {
        updateUiData { it.copy(isVolumeSliderShown = show) }
    }

    private fun cycleAspectRatio() {
        val newAspectRatio = when (playerPreferences.aspectState.get()) {
            VideoAspect.Fit -> VideoAspect.Stretch
            VideoAspect.Stretch -> VideoAspect.Crop
            VideoAspect.Crop -> VideoAspect.Fit
        }

        setAspectRatio(newAspectRatio)
    }

    fun setAspectRatio(aspect: VideoAspect) {
        val (pan, ratio) = when (aspect) {
            VideoAspect.Crop -> {
                1.0 to -1.0
            }
            VideoAspect.Fit -> {
                0.0 to -1.0
            }
            VideoAspect.Stretch -> {
                0.0 to screenAspectRatio
            }
        }

        setPropertyDouble("panscan", pan)
        setPropertyDouble("video-aspect-override", ratio)
        playerPreferences.aspectState.set(aspect)
        updateUiData { it.copy(playerUpdate = PlayerUpdates.AspectRatio(aspect)) }
    }

    private fun setSpeed(value: Float) {
        setPropertyFloat("speed", value)
        playerPreferences.playerSpeed.set(value)
    }

    private fun setAutoPlay(value: Boolean) {
        val textRes = if (value) {
            AYMR.strings.enable_auto_play
        } else {
            AYMR.strings.disable_auto_play
        }
        updateUiData { it.copy(playerUpdate = PlayerUpdates.ShowTextResource(textRes)) }
        playerPreferences.autoplayEnabled.set(value)
    }

    // === Custom buttons ===

    fun executeButton(button: CustomButton) {
        mpvCommand("script-message", "call_button_${button.id}")
    }

    fun executeLongPressButton(button: CustomButton) {
        mpvCommand("script-message", "call_button_${button.id}_long")
    }

    fun setPrimaryCustomButtonTitle(button: CustomButton) {
        updateUiData { it.copy(primaryButtonTitle = button.name) }
    }

    fun handleLuaInvocation(property: String, value: String) {
        val data = value
            .removePrefix("\"")
            .removeSuffix("\"")
            .ifEmpty { return }

        when (property.substringAfterLast("/")) {
            "show_text" -> updateUiData { it.copy(playerUpdate = PlayerUpdates.ShowText(data)) }
            "toggle_ui" -> {
                when (data) {
                    "show" -> showControls()
                    "toggle" -> if (uiData.value.controlsShown) hideControls() else showControls()
                    "hide" -> {
                        updateUiData {
                            it.copy(
                                sheetShown = Sheets.None,
                                panelShown = Panels.None,
                                dialogShown = Dialogs.None,
                            )
                        }
                        hideControls()
                    }
                }
            }
            "show_panel" -> {
                when (data) {
                    "subtitle_settings" -> setPanel(Panels.SubtitleSettings)
                    "subtitle_delay" -> setPanel(Panels.SubtitleDelay)
                    "audio_delay" -> setPanel(Panels.AudioDelay)
                    "video_filters" -> setPanel(Panels.VideoFilters)
                }
            }
            "set_button_title" -> {
                updateUiData { it.copy(primaryButtonTitle = data) }
            }
            "reset_button_title" -> {
                uiData.value.customButtons.firstOrNull { it.isFavorite }?.let {
                    setPrimaryCustomButtonTitle(it)
                }
            }
            "switch_episode" -> {
                when (data) {
                    "n" -> nextEpisode(next = true)
                    "p" -> nextEpisode(next = false)
                }
            }
            "launch_int_picker" -> {
                val (title, nameFormat, start, stop, step, pickerProperty) = data.split("|")
                val defaultValue = mpv.getPropertyInt(pickerProperty)!!
                setDialog(
                    Dialogs.IntegerPicker(
                        defaultValue = defaultValue,
                        minValue = start.toInt(),
                        maxValue = stop.toInt(),
                        step = step.toInt(),
                        nameFormat = nameFormat,
                        title = title,
                        onChange = { setPropertyInt(pickerProperty, it) },
                        onDismissRequest = { setDialog(Dialogs.None) },
                    ),
                )
            }
            "show_seek_text" -> {
                val (forward, text) = data.split("|", limit = 2)
                showSeekText(forward == "true", text)
            }
            "pause" -> {
                when (data) {
                    "pause" -> pause()
                    "unpause" -> unpause()
                    "pauseunpause" -> pauseUnpause()
                }
            }
            "seek_to_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekToWithText(seekValue.toInt(), text)
            }
            "seek_by_with_text" -> {
                val (seekValue, text) = data.split("|", limit = 2)
                seekByWithText(seekValue.toInt(), text)
            }
            "seek_by" -> seekByWithText(data.toInt(), null)
            "seek_to" -> seekToWithText(data.toInt(), null)
            "toggle_button" -> {
                fun showButton() {
                    if (uiData.value.primaryButton == null) {
                        updateUiData {
                            it.copy(
                                primaryButton = it.customButtons.firstOrNull { it.isFavorite },
                            )
                        }
                    }
                }

                when (data) {
                    "show" -> showButton()
                    "hide" -> updateUiData { it.copy(primaryButton = null) }
                    "toggle" -> if (uiData.value.primaryButton == null) {
                        showButton()
                    } else {
                        updateUiData { it.copy(primaryButton = null) }
                    }
                }
            }
            "software_keyboard" -> {
                viewModelScope.launch {
                    when (data) {
                        "show" -> _eventFlow.emit(Event.SetKeyboard(true))
                        "hide" -> _eventFlow.emit(Event.SetKeyboard(false))
                        "toggle" -> _eventFlow.emit(Event.ToggleKeyboard)
                    }
                }
            }
        }

        setPropertyString(property, "")
    }

    private operator fun <T> List<T>.component6(): T = get(5)

    // === Seeking ===

    fun updateGestureSeekAmount(value: Pair<Int, Int>?) {
        updatePlaybackData { it.copy(gestureSeekAmount = value, isGestureSeeking = value != null) }
    }

    fun updateIsSeeking(value: Boolean) {
        updatePlaybackData { it.copy(isSeeking = value) }
        if (!value) {
            updatePlaybackData { it.copy(thumbnailImage = null) }
        }
    }

    fun updateSeekAmount(amount: Int) {
        updatePlaybackData { it.copy(doubleTapSeekAmount = amount) }
    }

    fun updateSeekText(value: String?) {
        updatePlaybackData { it.copy(seekText = value) }
    }

    fun handleLeftDoubleTap() {
        when (leftDoubleTapGesture) {
            SingleActionGesture.None -> { }
            SingleActionGesture.Seek -> {
                leftSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Switch -> {
                nextEpisode(next = false)
            }
            SingleActionGesture.Custom -> {
                mpvCommand("keypress", CustomKeyCodes.DoubleTapLeft.keyCode)
            }
        }
    }

    fun handleCenterDoubleTap() {
        when (centerDoubleTapGesture) {
            SingleActionGesture.None -> { }
            SingleActionGesture.Seek -> { }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Switch -> { }
            SingleActionGesture.Custom -> {
                mpvCommand("keypress", CustomKeyCodes.DoubleTapCenter.keyCode)
            }
        }
    }

    fun handleRightDoubleTap() {
        when (rightDoubleTapGesture) {
            SingleActionGesture.None -> { }
            SingleActionGesture.Seek -> {
                rightSeek()
            }
            SingleActionGesture.PlayPause -> {
                pauseUnpause()
            }
            SingleActionGesture.Switch -> {
                nextEpisode(next = true)
            }
            SingleActionGesture.Custom -> {
                mpvCommand("keypress", CustomKeyCodes.DoubleTapRight.keyCode)
            }
        }
    }

    fun leftSeek() {
        if (playbackData.value.position > 0) {
            updatePlaybackData { it.copy(doubleTapSeekAmount = it.doubleTapSeekAmount - doubleTapToSeekDuration) }
        }
        updatePlaybackData { it.copy(isSeekingForwards = false) }
        seekBy(-doubleTapToSeekDuration)
        if (showSeekBar) showSeekBar()
    }

    fun rightSeek() {
        if (playbackData.value.position < playbackData.value.duration) {
            updatePlaybackData { it.copy(doubleTapSeekAmount = it.doubleTapSeekAmount + doubleTapToSeekDuration) }
        }
        updatePlaybackData { it.copy(isSeekingForwards = true) }
        seekBy(doubleTapToSeekDuration)
        if (showSeekBar) showSeekBar()
    }

    private fun showSeekText(isForward: Boolean, text: String) {
        updatePlaybackData {
            it.copy(
                seekText = text,
                isSeekingForwards = isForward,
                doubleTapSeekAmount = if (isForward) 1 else -1,
            )
        }
        if (showSeekBar) showSeekBar()
    }

    private fun seekToWithText(seekValue: Int, text: String?) {
        updatePlaybackData {
            it.copy(
                seekText = text,
                isSeekingForwards = seekValue > 0,
                doubleTapSeekAmount = seekValue - it.position,
            )
        }
        seekTo(seekValue)
        if (showSeekBar) showSeekBar()
    }

    private fun seekByWithText(value: Int, text: String?) {
        updatePlaybackData {
            it.copy(
                seekText = text,
                isSeekingForwards = value > 0,
                doubleTapSeekAmount = if ((value < 0 && it.doubleTapSeekAmount < 0) ||
                    it.position + value > it.duration
                ) {
                    0
                } else {
                    it.doubleTapSeekAmount + value
                },
            )
        }
        seekBy(value)
        if (showSeekBar) showSeekBar()
    }

    fun seekBy(offset: Int) {
        mpvCommand("seek", offset.toString(), if (smoothSeeking) "relative+exact" else "relative")
    }

    fun seekTo(position: Int) {
        if (position !in 0..playbackData.value.duration) return
        mpvCommand("seek", position.toString(), if (smoothSeeking) "absolute" else "absolute+keyframes")

        viewModelScope.launch {
            if (!playbackData.value.paused) {
                _eventFlow.emit(Event.UpdateDiscordRPC(false, position))
            }
        }
    }

    fun selectChapter(index: Int) {
        setPropertyInt("chapter", index)
        dismissSheet()
        unpause()
    }

    private var lastThumbnailFetch = 0L

    fun updateSeekPos(pos: Float) {
        updatePlaybackData { it.copy(seekPosition = pos, isSeeking = true) }

        val thumbInfo = playbackData.value.thumbnailInfo ?: return
        val info = thumbInfo.tileInfo.lastOrNull { it.timeMs <= pos * 1000L }
        if (info != null) {
            val tileBitmap = thumbnailTileCache[info.imageIndex]
            if (tileBitmap != null) {
                createThumbnail(tileBitmap, info)
            } else {
                val now = System.currentTimeMillis()
                if (now - lastThumbnailFetch < 2.seconds.inWholeMilliseconds) return
                lastThumbnailFetch = now

                thumbnailFetchJob?.cancel()
                thumbnailFetchJob = viewModelScope.launchIO {
                    val source = stateData.value.currentSource as? AnimeHttpSource ?: return@launchIO

                    try {
                        val tileUrl = thumbInfo.imageTileUrls[info.imageIndex]
                        val bitmap = source.getImageTile(tileUrl)
                        if (bitmap != null) {
                            withUIContext {
                                thumbnailTileCache[info.imageIndex] = bitmap
                                createThumbnail(bitmap, info)
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        logcat(LogPriority.ERROR, e) { "Failed to fetch thumbnails tiles" }
                    }
                }
            }
        }
    }

    private fun createThumbnail(tileBitmap: Bitmap, tileInfo: TileInfo) {
        val thumbnail = Bitmap.createBitmap(tileBitmap, tileInfo.x, tileInfo.y, tileInfo.width, tileInfo.height)
        updatePlaybackData {
            it.copy(thumbnailImage = thumbnail.asImageBitmap())
        }
    }

    // === Aniyomi ===

    /**
     * Called when the activity is saved and not changing configurations. It updates the database
     * to persist the current progress of the active episode.
     */
    fun onSaveInstanceStateNonConfigurationChange() {
        val currentEpisode = stateData.value.currentEpisode ?: return
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(currentEpisode)
        }
    }

    override fun onCleared() {
        stateData.value.currentEpisode?.let {
            saveWatchingProgress(it)
            episodeToDownload?.let { toDownload ->
                downloadManager.addDownloadsToStartOfQueue(listOf(toDownload))
            }
        }

        super.onCleared()
    }

    /**
     * Called every time a second is reached in the player. Used to mark the flag of episode being
     * seen, update tracking services, enqueue downloaded episode deletion and download next episode.
     */
    fun onSecondReached(position: Int, isCasting: Boolean = false) {
        updatePlaybackData { it.copy(position = position) }
        if (uiData.value.isLoadingEpisode) return
        val currentEpisode = stateData.value.currentEpisode ?: return
        if (episodeId == -1L) return
        val duration = if (isCasting) castUiData.value.duration.toInt() else playbackData.value.duration
        if (duration == 0) return

        if (isCasting) {
            val chapter = castUiData.value.chapters.filter { it.start <= position }.maxByOrNull { it.start }
            if (chapter != castUiData.value.currentChapter) {
                castOnChapterChanged(chapter)
            }
        }

        // Set netflix-style timeout
        if (isCasting) {
            castUiData.value.netflixTimeout?.let { timeout ->
                if (timeout > 0) {
                    updateCastUiData { it.copy(netflixTimeout = timeout - 1) }
                } else {
                    castOnSkipIntro()
                }
            }
        } else {
            playbackData.value.netflixTimeout?.let { timeout ->
                if (timeout > 0) {
                    updatePlaybackData { it.copy(netflixTimeout = timeout - 1) }
                } else {
                    onSkipIntro()
                }
            }
        }

        // It's called seconds, but it's supposed to be in milliseconds. WTF?
        currentEpisode.last_second_seen = position.toLong() * 1000L
        currentEpisode.total_seconds = duration.toLong() * 1000L

        episodePosition = position.toLong()
        val shouldTrack = !stateData.value.incognitoMode || stateData.value.hasTrackers
        if (position >= duration * progress && shouldTrack) {
            viewModelScope.launchNonCancellable {
                updateEpisodeProgressOnComplete(currentEpisode)
            }
        }

        saveWatchingProgress(currentEpisode)

        val inDownloadRange = position.toDouble() / duration > 0.35
        if (inDownloadRange) {
            downloadNextEpisodes()
        }
    }

    private suspend fun updateEpisodeProgressOnComplete(currentEp: Episode) {
        currentEp.seen = true
        updateTrackEpisodeSeen(currentEp)
        deleteEpisodeIfNeeded(currentEp)

        val markDuplicateAsSeen = libraryPreferences.markDuplicateSeenEpisodeAsSeen.get()
            .contains(LibraryPreferences.MARK_DUPLICATE_EPISODE_SEEN_EXISTING)
        if (!markDuplicateAsSeen) return

        val duplicateUnseenEpisodes = stateData.value.currentPlaylist
            .mapNotNull { episode ->
                if (
                    !episode.seen &&
                    episode.isRecognizedNumber &&
                    episode.episode_number == currentEp.episode_number
                ) {
                    EpisodeUpdate(episode.id!!) { seen = true }
                } else {
                    null
                }
            }
        updateEpisode.awaitAll(duplicateUnseenEpisodes)

        // AM (SYNC) -->
        val isSyncEnabled = syncPreferences.isSyncEnabled()
        val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
        if (isSyncEnabled && syncTriggerOpt.syncOnEpisodeSeen) {
            SyncDataJob.startNow(context.workManager)
        }
        // <-- AM (SYNC)
    }

    private fun updateTrackEpisodeSeen(episode: Episode) {
        if (basePreferences.incognitoMode.get() || !stateData.value.hasTrackers) return
        if (!trackPreferences.autoUpdateTrack.get()) return

        val anime = stateData.value.currentAnime ?: return

        viewModelScope.launchNonCancellable {
            trackEpisode.await(context, anime.id, episode.episode_number.toDouble())
        }
    }

    fun saveCurrentEpisodeWatchingProgress() {
        stateData.value.currentEpisode?.let {
            saveWatchingProgress(it)
        }
    }

    /**
     * Called when episode is changed in player or when activity is paused.
     */
    private fun saveWatchingProgress(episode: Episode) {
        viewModelScope.launchNonCancellable {
            saveEpisodeProgress(episode)
            saveEpisodeHistory(episode)
        }
    }

    /**
     * Saves this [episode] progress (last second seen and whether it's seen).
     * If incognito mode isn't on or has at least 1 tracker
     */
    private suspend fun saveEpisodeProgress(episode: Episode) {
        val stateData = stateData.value
        if (!stateData.incognitoMode || stateData.hasTrackers) {
            updateEpisode.await(
                EpisodeUpdate(episode.id!!) {
                    seen = episode.seen
                    bookmark = episode.bookmark
                    fillermark = episode.fillermark
                    lastSecondSeen = episode.last_second_seen
                    totalSeconds = episode.total_seconds
                },
            )
            // AM (SYNC) -->
            val isSyncEnabled = syncPreferences.isSyncEnabled()
            val syncTriggerOpt = syncPreferences.getSyncTriggerOptions()
            if (isSyncEnabled && syncTriggerOpt.syncOnEpisodeOpen && episode.last_second_seen >= 1L) {
                SyncDataJob.startNow(context.workManager)
            }
            // <-- AM (SYNC)
        }
    }

    /**
     * Saves this [episode] last seen history if incognito mode isn't on.
     */
    private suspend fun saveEpisodeHistory(episode: Episode) {
        if (!stateData.value.incognitoMode) {
            val episodeId = episode.id!!
            val seenAt = Date()
            upsertHistory.await(
                HistoryUpdate(episodeId, seenAt),
            )
        }
    }

    /**
     * Bookmarks the currently active episode.
     */
    fun bookmarkEpisode(episodeId: Long?, bookmarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(episodeId!!) {
                    bookmark = bookmarked
                },
            )
        }
    }

    /**
     * Fillermarks the currently active episode.
     */
    fun fillermarkEpisode(episodeId: Long?, fillermarked: Boolean) {
        viewModelScope.launchNonCancellable {
            updateEpisode.await(
                EpisodeUpdate(episodeId!!) {
                    fillermark = fillermarked
                },
            )
        }
    }

    private fun downloadNextEpisodes() {
        if (downloadAheadAmount == 0) return
        val anime = stateData.value.currentAnime ?: return
        val source = stateData.value.currentSource ?: return

        val currentPlaylist = stateData.value.currentPlaylist
        val currentPlaylistIndex = stateData.value.currentPlaylistIndex

        // Only download ahead if current + next episode is already downloaded too to avoid jank
        if (currentPlaylistIndex == currentPlaylist.lastIndex) return
        val currentEpisode = stateData.value.currentEpisode ?: return

        val nextEpisode = currentPlaylist[currentPlaylistIndex + 1]
        val episodesAreDownloaded =
            episodeLoader.isDownload(currentEpisode.toDomainEpisode()!!, anime, source) &&
                episodeLoader.isDownload(nextEpisode.toDomainEpisode()!!, anime, source)

        viewModelScope.launchIO {
            if (!episodesAreDownloaded) {
                return@launchIO
            }
            val episodesToDownload = getNextEpisodes.await(anime.id, nextEpisode.id!!)
                .take(downloadAheadAmount)
            downloadManager.downloadEpisodes(anime, episodesToDownload)
        }
    }

    /**
     * Determines if deleting option is enabled and nth to last episode actually exists.
     * If both conditions are satisfied enqueues episode for delete
     * @param chosenEpisode current episode, which is going to be marked as seen.
     */
    private fun deleteEpisodeIfNeeded(chosenEpisode: Episode) {
        // Determine which episode should be deleted and enqueue
        val currentEpisodePosition = stateData.value.currentPlaylist.indexOf(chosenEpisode)
        val removeAfterSeenSlots = downloadPreferences.removeAfterSeenSlots.get()
        val episodeToDelete = stateData.value.currentPlaylist.getOrNull(
            currentEpisodePosition - removeAfterSeenSlots,
        )
        // If episode is completely seen no need to download it
        episodeToDownload = null

        // Check if deleting option is enabled and episode exists
        if (removeAfterSeenSlots != -1 && episodeToDelete != null) {
            enqueueDeleteSeenEpisodes(episodeToDelete)
        }
    }

    /**
     * Enqueues this [episode] to be deleted when [deletePendingEpisodes] is called. The download
     * manager handles persisting it across process deaths.
     */
    private fun enqueueDeleteSeenEpisodes(episode: Episode) {
        if (!episode.seen) return
        val anime = stateData.value.currentAnime ?: return
        viewModelScope.launchNonCancellable {
            downloadManager.enqueueEpisodesToDelete(listOf(episode.toDomainEpisode()!!), anime)
        }
    }

    /**
     * Deletes all the pending episodes. This operation will run in a background thread and errors
     * are ignored.
     */
    fun deletePendingEpisodes() {
        viewModelScope.launchNonCancellable {
            downloadManager.deletePendingEpisodes()
        }
    }

    /**
     * Results of the save image feature.
     */
    sealed class SaveImageResult {
        class Success(val uri: Uri) : SaveImageResult()
        class Error(val error: Throwable) : SaveImageResult()
    }

    /**
     * Sets the screenshot as art and notifies the UI of the result.
     */
    fun setAsArt(artType: ArtType, imageStream: () -> InputStream) {
        val anime = stateData.value.currentAnime ?: return
        val episode = stateData.value.currentEpisode ?: return

        viewModelScope.launchNonCancellable {
            val result = try {
                when (artType) {
                    ArtType.Cover -> anime.editCover(coverManager, imageStream(), updateAnime, coverCache)
                    ArtType.Background -> anime.editBackground(
                        backgroundManager,
                        imageStream(),
                        updateAnime,
                        backgroundCache,
                    )
                    ArtType.Thumbnail -> episode.editThumbnail(anime, thumbnailManager, imageStream())
                }

                if (anime.isLocal() || anime.favorite) {
                    SetAsArt.Success
                } else {
                    SetAsArt.AddToLibraryFirst
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to set art" }
                SetAsArt.Error
            }
            _eventFlow.emit(Event.SetArtResult(result, artType))
        }
    }

    /**
     * Shares the screenshot and notifies the UI with the path of the file to share.
     * The image must be first copied to the internal partition because there are many possible
     * formats it can come from, like a zipped chapter, in which case it's not possible to directly
     * get a path to the file, and it has to be decompressed somewhere first. Only the last shared
     * image will be kept so it won't be taking lots of internal disk space.
     */
    fun shareImage(imageStream: () -> InputStream) {
        val anime = stateData.value.currentAnime ?: return
        val pos = playbackData.value.position

        val destDir = context.cacheImageDir

        val seconds = Utils.prettyTime(pos)
        val filename = generateFilename(anime, seconds) ?: return

        try {
            viewModelScope.launchIO {
                destDir.deleteRecursively()
                val uri = imageSaver.save(
                    image = Image.Screenshot(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Cache,
                    ),
                )
                _eventFlow.emit(Event.ShareImage(uri, seconds))
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e)
        }
    }

    /**
     * Saves the screenshot on the pictures directory and notifies the UI of the result.
     * There's also a notification to allow sharing the image somewhere else or deleting it.
     */
    fun saveImage(imageStream: () -> InputStream) {
        val anime = stateData.value.currentAnime ?: return
        val pos = playbackData.value.position

        val notifier = SaveImageNotifier(context)
        notifier.onClear()

        val seconds = Utils.prettyTime(pos)
        val filename = generateFilename(anime, seconds) ?: return

        // Pictures directory.
        val relativePath = DiskUtil.buildValidFilename(anime.title)

        // Copy file in background.
        viewModelScope.launchNonCancellable {
            try {
                val uri = imageSaver.save(
                    image = Image.Screenshot(
                        inputStream = imageStream,
                        name = filename,
                        location = Location.Pictures(relativePath),
                    ),
                )
                notifier.onComplete(uri)
                _eventFlow.emit(Event.SavedImage(SaveImageResult.Success(uri)))
            } catch (e: Throwable) {
                notifier.onError(e.message)
                _eventFlow.emit(Event.SavedImage(SaveImageResult.Error(e)))
            }
        }
    }

    // TODO: Make use of nodes instead of saving to cache
    fun takeScreenshot(showSubtitles: Boolean): InputStream? {
        val filename = context.cacheDir.path + "/${System.currentTimeMillis()}_mpv_screenshot_tmp.png"
        val subtitleFlag = if (showSubtitles) "subtitles" else "video"

        mpvCommand("screenshot-to-file", filename, subtitleFlag)
        val tempFile = File(filename).takeIf { it.exists() } ?: return null
        val newFile = File("${context.cacheDir.path}/mpv_screenshot.png")

        newFile.delete()
        tempFile.renameTo(newFile)
        return newFile.takeIf { it.exists() }?.inputStream()
    }

    /**
     * Generate a filename for the given [anime] and [timePos]
     */
    private fun generateFilename(
        anime: Anime,
        timePos: String,
    ): String? {
        val episode = stateData.value.currentEpisode ?: return null
        val filenameSuffix = " - $timePos"
        return DiskUtil.buildValidFilename(
            "${anime.title} - ${episode.name}".takeBytes(
                DiskUtil.MAX_FILE_NAME_BYTES - filenameSuffix.byteSize(),
            ),
        ) + filenameSuffix
    }

    // === Skip intro ===

    fun onChapterChanged(chapterIndex: Int?) {
        if (chapterIndex == null) {
            updateStateData { it.copy(currentChapter = null) }
            return
        }

        val chapterList = mpv.getPropertyNode("chapter-list")?.toObject<List<ChapterNode>>(json)
            ?: emptyList()
        val chapter = if (chapterIndex == -1) {
            ChapterNode(
                time = 0.0f,
                "",
            )
        } else {
            chapterList.getOrNull(chapterIndex) ?: return
        }
        updateStateData { it.copy(currentChapter = chapter.toSegment()) }

        if (!introSkipEnabled) return
        val chapterType = chapter.chapterType

        if (chapterType == ChapterType.Other) {
            updateUiData { it.copy(skipIntroText = null) }
            updatePlaybackData { it.copy(netflixTimeout = null) }
        } else {
            if (netflixStyle) {
                // show a toast with the seconds before the skip
                viewModelScope.launch {
                    _eventFlow.emit(
                        Event.ToastString(
                            "Skip Intro: ${context.stringResource(
                                AYMR.strings.player_aniskip_dontskip_toast,
                                chapter.chapterTitle,
                                defaultWaitingTime,
                            )}",
                        ),
                    )
                }
                updateUiData { it.copy(skipIntroText = context.stringResource(AYMR.strings.player_aniskip_dontskip)) }
                updatePlaybackData { it.copy(netflixTimeout = defaultWaitingTime) }
            } else if (autoSkip) {
                skipIntro(chapter.chapterTitle)
            } else {
                updateSkipIntroButton(chapterType)
            }
        }
    }

    private fun skipIntro(chapterName: String) {
        mpvCommand("add", "chapter", "1")
        showSeekText(true, context.stringResource(AYMR.strings.player_intro_skipped, chapterName))
    }

    private fun updateSkipIntroButton(chapterType: ChapterType) {
        val skipButtonString = chapterType.getStringRes()
        val skipIntroText = skipButtonString?.let { s ->
            context.stringResource(
                AYMR.strings.player_skip_action,
                context.stringResource(s),
            )
        }

        updateUiData { it.copy(skipIntroText = skipIntroText) }
        updateCastUiData { it.copy(skipIntroText = skipIntroText) }
    }

    fun onSkipIntro() {
        val chapterIndex = mpv.getPropertyInt("chapter") ?: return
        val chapterList = mpv.getPropertyNode("chapter-list")?.toObject<List<ChapterNode>>(json)
            ?: emptyList()
        val chapter = chapterList.getOrNull(chapterIndex) ?: return

        if ((playbackData.value.netflixTimeout ?: 0) > 0 && netflixStyle) {
            updatePlaybackData { it.copy(netflixTimeout = null) }
            updateSkipIntroButton(chapter.chapterType)
            return
        }

        updatePlaybackData { it.copy(netflixTimeout = null) }
        skipIntro(chapter.chapterTitle)
    }

    /**
     * Returns the skipIntroLength used by this anime or the default one.
     */
    fun getAnimeSkipIntroLength(): Int {
        val default = gesturePreferences.defaultIntroLength.get()
        val anime = stateData.value.currentAnime ?: return default
        val skipIntroLength = anime.skipIntroLength
        val skipIntroDisable = anime.skipIntroDisable
        return when {
            skipIntroDisable -> 0
            skipIntroLength <= 0 -> default
            else -> anime.skipIntroLength
        }
    }

    /**
     * Updates the skipIntroLength for the open anime.
     */
    fun setAnimeSkipIntroLength(skipIntroLength: Long) {
        val anime = stateData.value.currentAnime ?: return
        if (!anime.favorite) return
        // Skip unnecessary database operation
        if (skipIntroLength == getAnimeSkipIntroLength().toLong()) return
        viewModelScope.launchIO {
            setAnimeViewerFlags.awaitSetSkipIntroLength(anime.id, skipIntroLength)
            val newAnime = getAnime.await(anime.id)
            updateStateData { it.copy(currentAnime = newAnime) }
        }
    }

    /**
     * Returns the response of the AniSkipApi for this episode.
     *
     * Only works if tracking is enabled.
     */
    suspend fun aniSkipResponse(playerDuration: Int?): List<TimeStamp>? {
        val animeId = stateData.value.currentAnime?.id ?: return null
        var malId: Long?
        val episodeNumber = stateData.value.currentEpisode?.episode_number?.toInt() ?: return null
        if (getTracks.await(animeId).isEmpty()) {
            logcat(LogPriority.DEBUG) { "AniSkip: No tracks found for anime $animeId" }
            return null
        }

        getTracks.await(animeId).forEach { track ->
            val tracker = trackerManager.get(track.trackerId)
            malId = when (tracker) {
                is MyAnimeList -> track.remoteId
                is Anilist -> aniSkipApi.getMalIdFromAL(track.remoteId)
                else -> null
            }
            val duration = playerDuration ?: return null
            return malId?.let {
                aniSkipApi.getResult(it.toInt(), episodeNumber, duration.toLong())
            }
        }
        return null
    }

    // === Misc ===

    /**
     * Starts a sleep timer/cancels the current timer if [seconds] is less than 1.
     */
    fun startTimer(seconds: Int) {
        timerJob?.cancel()
        updatePlaybackData { it.copy(remainingTime = seconds) }
        if (seconds < 1) return
        timerJob = viewModelScope.launch {
            for (time in seconds downTo 0) {
                updatePlaybackData { it.copy(remainingTime = time) }
                delay(1.seconds)
            }
            setPropertyBoolean("pause", true)
            _eventFlow.emit(Event.ToastResource(AYMR.strings.toast_sleep_timer_ended))
        }
    }

    // === Data ===
    @Stable
    data class PlayerStateData(
        val isCasting: Boolean = false,
        val isLoadingCasting: Boolean = false,
        val isErrorCasting: Boolean = false,
        val isStopped: Boolean = false,
        val hasTrackers: Boolean = false,
        val incognitoMode: Boolean = false,
        val currentPlaylist: List<Episode> = emptyList(),
        val currentPlaylistIndex: Int = -1,
        val hasPreviousEpisode: Boolean = false,
        val hasNextEpisode: Boolean = false,
        val isEpisodeOnline: Boolean = false,
        val currentEpisode: Episode? = null,
        val currentAnime: Anime? = null,
        val currentSource: AnimeSource? = null,
        val currentVideo: Video? = null,
        val videoHeight: Int = 0,
        val videoWidth: Int = 0,
        val maxVolume: Int,
        val volumeBoostCap: Int? = null,
        val hasLoadedTracks: Boolean = false,
        val hasLoadedSubs: Boolean = false,
        val hasLoadedAudio: Boolean = false,
        val chapters: List<Segment> = emptyList(),
        val currentChapter: Segment? = null,
        val aniskipChapters: List<TimeStamp> = emptyList(),
        val subtitleTracks: List<TrackNode> = emptyList(),
        val audioTracks: List<TrackNode> = emptyList(),
        val externalSubtitleTracks: List<MpvVideoTrack.External> = emptyList(),
        val externalAudioTracks: List<MpvVideoTrack.External> = emptyList(),
        val hosterList: List<Hoster> = emptyList(),
        val hosterState: List<HosterState> = emptyList(),
        val isPipAvailable: Boolean = false,
    )

    @Stable
    data class PlayerUiData(
        val isLoadingHosters: Boolean = false,
        val isLoadingEpisode: Boolean = false,
        val previousPauseState: Boolean? = false,
        val hosterExpandedList: List<Boolean> = emptyList(),
        val selectedHosterVideoIndex: Pair<Int, Int> = Pair(-1, -1),
        val mediaTitle: String = "",
        val animeTitle: String = "",
        val controlsShown: Boolean = true,
        val statusBarShown: Boolean = false,
        val seekBarShown: Boolean = true,
        val isControlsLocked: Boolean = false,
        val playerUpdate: PlayerUpdates = PlayerUpdates.None,
        val isBrightnessSliderShown: Boolean = false,
        val isVolumeSliderShown: Boolean = false,
        val sheetShown: Sheets = Sheets.None,
        val panelShown: Panels = Panels.None,
        val dialogShown: Dialogs = Dialogs.None,
        val dismissSheet: Boolean = false,
        val fontList: List<String> = emptyList(),
        val customButtons: List<CustomButton> = emptyList(),
        val primaryButtonTitle: String = "",
        val primaryButton: CustomButton? = null,
        val skipIntroText: String? = null,

        // Prefs
        val reduceMotion: Boolean = false,
        val playerTimeToDisappearMs: Int = 4000,
        val swapVolumeAndBrightness: Boolean = false,
        val boostCap: Int = 30,
        val displayVolumeAsPercentage: Boolean = true,
        val showLoadingCircle: Boolean = true,
        val invertDuration: Boolean = false,
        val smoothSeeking: Boolean = false,
        val autoPlayEnabled: Boolean = false,
        val showChapterIndicator: Boolean = true,
        val playerSpeedPref: Float = 1f,
        val enableCast: Boolean = false,
    )

    @Stable
    data class PlayerPlaybackData(
        val paused: Boolean = false,
        val position: Int = 0,
        val duration: Int = 0,
        val currentVolume: Int,
        val currentBrightness: Float,
        val currentOrientation: Int? = null,
        val isSeeking: Boolean = false,
        val isGestureSeeking: Boolean = false,
        val seekPosition: Float = 0f,
        val thumbnailImage: ImageBitmap? = null,
        val thumbnailInfo: ThumbnailInfo? = null,
        val seekText: String? = null,
        val doubleTapSeekAmount: Int = 0,
        val isSeekingForwards: Boolean = false,
        val gestureSeekAmount: Pair<Int, Int>? = null,
        val remainingTime: Int = 0,
        val netflixTimeout: Int? = null,
    )

    sealed interface PlayerEvent {
        data object ChangeAspect : PlayerEvent
        data class ChangeSpeed(val value: Float) : PlayerEvent
        data object CycleRotation : PlayerEvent
        data object EnterPip : PlayerEvent
        data class ExecuteCustomButton(val long: Boolean) : PlayerEvent
        data class LockControls(val lock: Boolean) : PlayerEvent
        data class NextEpisode(val next: Boolean) : PlayerEvent
        data object PlayPause : PlayerEvent
        data class Seek(val position: Int) : PlayerEvent
        data class SeekFinished(val position: Int) : PlayerEvent
        data class SetAutoPlay(val value: Boolean) : PlayerEvent
        data class SetPanel(val panel: Panels) : PlayerEvent
        data class SetSheet(val sheet: Sheets) : PlayerEvent
        data class ShowBrightnessSlider(val show: Boolean) : PlayerEvent
        data object ShowEpisodeDialog : PlayerEvent
        data class ShowPlayerUpdate(val update: PlayerUpdates) : PlayerEvent
        data class ShowVolumeSlider(val show: Boolean) : PlayerEvent
        data object SkipIntro : PlayerEvent
        data object ToggleDurationTimer : PlayerEvent
    }

    sealed interface Event {
        data object EnterPip : Event
        data class EpisodeTitle(val name: String) : Event
        data object Finish : Event
        data class InitialEpisodeError(val error: Throwable) : Event
        data class SavedImage(val result: SaveImageResult) : Event
        data class SetArtResult(val result: SetAsArt, val artType: ArtType) : Event
        data class SetKeyboard(val show: Boolean) : Event
        data class ShareImage(val uri: Uri, val seconds: String) : Event
        data class ToastResource(val stringRes: StringResource) : Event
        data class ToastString(val string: String) : Event
        data object ToggleKeyboard : Event
        data class UpdateDiscordRPC(val paused: Boolean, val position: Int? = null) : Event
    }
}
