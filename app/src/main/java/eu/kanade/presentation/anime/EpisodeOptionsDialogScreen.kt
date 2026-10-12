// AY -->
package eu.kanade.presentation.anime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.core.screen.Screen
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.components.HosterState
import eu.kanade.tachiyomi.ui.player.components.QualitySheetHosterContent
import eu.kanade.tachiyomi.ui.player.components.QualitySheetVideoContent
import eu.kanade.tachiyomi.ui.player.components.getChangedAt
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.ui.player.loader.HosterLoader
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.OpenInNew
import mihon.icons.materialsymbols.rounded.ChevronRight
import mihon.icons.materialsymbols.rounded.ContentCopy
import mihon.icons.materialsymbols.rounded.Download
import mihon.icons.materialsymbols.rounded.Input
import mihon.icons.materialsymbols.rounded.SystemUpdateAlt
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.episode.interactor.GetEpisode
import tachiyomi.domain.episode.model.Episode
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

class EpisodeOptionsDialogScreen(
    private val useExternalDownloader: Boolean,
    private val episodeTitle: String,
    private val episodeId: Long,
    private val animeId: Long,
    private val sourceId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val vm = assistedMetroViewModel<EpisodeOptionsDialogViewModel, EpisodeOptionsDialogViewModel.Factory> {
            create(episodeId = episodeId, animeId = animeId, sourceId = sourceId)
        }

        val episode by vm.episode.collectAsState()
        val anime by vm.anime.collectAsState()
        val hosterState by vm.hosterState.collectAsState()
        val hosterExpandedList by vm.hosterExpandedList.collectAsState()
        val selectedHosterVideoIndex by vm.selectedHosterVideoIndex.collectAsState()
        val currentVideo by vm.currentVideo.collectAsState()
        val showAllQualities by vm.showAllQualities.collectAsState()

        EpisodeOptionsDialog(
            useExternalDownloader = useExternalDownloader,
            episodeTitle = episodeTitle,
            episode = episode,
            anime = anime,
            showAllQualities = showAllQualities,
            resultList = hosterState,
            expandedList = hosterExpandedList,
            currentVideo = currentVideo,
            selectedHosterVideoIndex = selectedHosterVideoIndex,
            onShowAllQualities = vm::onShowAllQualities,
            onClickHoster = vm::onClickHoster,
            onClickVideo = vm::onClickVideo,
            getHosterList = vm::getHosterList,
            downloadEpisode = vm::downloadEpisode,
        )
    }

    companion object {
        var onDismissDialog: () -> Unit = {}
    }
}

@AssistedInject
class EpisodeOptionsDialogViewModel(
    @Assisted episodeId: Long,
    @Assisted animeId: Long,
    @Assisted sourceId: Long,
    private val episodeLoader: EpisodeLoader,
    private val hosterLoader: HosterLoader,
    private val sourceManager: SourceManager,
    private val getEpisode: GetEpisode,
    private val getAnime: GetAnime,
    private val downloadManager: DownloadManager,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(episodeId: Long, animeId: Long, sourceId: Long): EpisodeOptionsDialogViewModel
    }

    private val _hosterState = MutableStateFlow<Result<List<HosterState>>?>(null)
    val hosterState = _hosterState.asStateFlow()
    private val _hosterExpandedList = MutableStateFlow<List<Boolean>>(emptyList())
    val hosterExpandedList = _hosterExpandedList.asStateFlow()
    private val _selectedHosterVideoIndex = MutableStateFlow(Pair(-1, -1))
    val selectedHosterVideoIndex = _selectedHosterVideoIndex.asStateFlow()
    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo = _currentVideo.asStateFlow()

    private val _episode = MutableStateFlow<Episode?>(null)
    val episode = _episode.asStateFlow()
    private val _anime = MutableStateFlow<Anime?>(null)
    val anime = _anime.asStateFlow()

    @Suppress("ktlint:standard:backing-property-naming")
    private val _hosterList = MutableStateFlow<List<Hoster>>(emptyList())

    @Suppress("ktlint:standard:backing-property-naming")
    private val _source = MutableStateFlow<AnimeSource?>(null)

    private val _showAllQualities = MutableStateFlow(false)
    val showAllQualities = _showAllQualities.asStateFlow()

    init {
        val hasFoundPreferredVideo = AtomicBoolean(false)

        viewModelScope.launchIO {
            val episode = getEpisode.await(episodeId)!!
            val anime = getAnime.await(animeId)!!
            val source = sourceManager.getOrStub(sourceId)

            _episode.update { _ -> episode }
            _anime.update { _ -> anime }
            _source.update { _ -> source }

            val hosterListResult = withIOContext {
                try {
                    Result.success(episodeLoader.getHosters(episode, anime, source))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            if (hosterListResult.isFailure) {
                _hosterState.update { _ -> Result.failure(hosterListResult.exceptionOrNull()!!) }
                return@launchIO
            }

            val hosterList = hosterListResult.getOrThrow()
            _hosterList.update { _ -> hosterList }
            _hosterExpandedList.update { _ ->
                List(hosterList.size) { true }
            }

            val initialHosterState = hosterList.map { hoster ->
                if (hoster.videoList == null) {
                    HosterState.Loading(hoster.hosterName)
                } else {
                    val videoList = hoster.videoList!!
                    HosterState.Ready(
                        hoster.hosterName,
                        videoList,
                        List(videoList.size) { Video.State.LOAD_VIDEO },
                    )
                }
            }

            _hosterState.update { _ -> Result.success(initialHosterState) }

            try {
                hosterList.mapIndexed { hosterIdx, hoster ->
                    async {
                        val hosterState = episodeLoader.loadHosterVideos(source, hoster)

                        _hosterState.updateAt(hosterIdx, hosterState)

                        if (hosterState is HosterState.Ready) {
                            val prefIndex = hosterState.videoList.indexOfFirst { it.preferred }
                            if (prefIndex != -1) {
                                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                                    val success =
                                        loadVideo(source, hosterState.videoList[prefIndex], hosterIdx, prefIndex)
                                    if (!success) {
                                        hasFoundPreferredVideo.set(false)
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()

                if (hasFoundPreferredVideo.compareAndSet(false, true)) {
                    if (selectedHosterVideoIndex.value == Pair(-1, -1)) {
                        val hosterStateList = hosterState.value!!.getOrThrow()
                        val (hosterIdx, videoIdx) = hosterLoader.selectBestVideo(hosterStateList)
                        if (hosterIdx == -1) {
                            _hosterState.update { _ ->
                                Result.failure(NoSuchElementException("No available videos"))
                            }
                            return@launchIO
                        }

                        val video = (hosterStateList[hosterIdx] as HosterState.Ready).videoList[videoIdx]

                        loadVideo(source, video, hosterIdx, videoIdx)
                    }
                }
            } catch (e: CancellationException) {
                _hosterState.update { _ ->
                    Result.success(hosterList.map { HosterState.Idle(it.hosterName) })
                }

                throw e
            }
        }
    }

    private suspend fun loadVideo(source: AnimeSource, video: Video, hosterIndex: Int, videoIndex: Int): Boolean {
        val selectedHosterState = (_hosterState.value!!.getOrThrow()[hosterIndex] as? HosterState.Ready) ?: return false

        val oldSelectedIndex = _selectedHosterVideoIndex.value
        _selectedHosterVideoIndex.update { _ -> Pair(hosterIndex, videoIndex) }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, video, Video.State.LOAD_VIDEO),
        )

        val resolvedVideo = if (selectedHosterState.videoState[videoIndex] != Video.State.READY) {
            hosterLoader.getResolvedVideo(source, video)
        } else {
            video
        }

        if (resolvedVideo == null || resolvedVideo.videoUrl.isEmpty()) {
            if (currentVideo.value == null) {
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                )

                val hosterStateList = hosterState.value?.getOrNull() ?: return false

                val (newHosterIdx, newVideoIdx) = hosterLoader.selectBestVideo(hosterStateList)
                if (newHosterIdx == -1) {
                    _hosterState.update { _ ->
                        Result.failure(NoSuchElementException("No available videos"))
                    }
                    return false
                }

                val newVideo = (hosterStateList[newHosterIdx] as HosterState.Ready).videoList[newVideoIdx]

                return loadVideo(source, newVideo, newHosterIdx, newVideoIdx)
            } else {
                _selectedHosterVideoIndex.update { _ -> oldSelectedIndex }
                _hosterState.updateAt(
                    hosterIndex,
                    selectedHosterState.getChangedAt(videoIndex, video, Video.State.ERROR),
                )
                return false
            }
        }

        _hosterState.updateAt(
            hosterIndex,
            selectedHosterState.getChangedAt(videoIndex, resolvedVideo, Video.State.READY),
        )
        _currentVideo.update { _ -> resolvedVideo }

        return true
    }

    private fun <T> MutableStateFlow<Result<List<T>>?>.updateAt(index: Int, newValue: T) {
        this.update { values ->
            values?.getOrNull()?.let {
                Result.success(
                    it.toMutableList().apply {
                        this[index] = newValue
                    },
                )
            } ?: values
        }
    }

    fun onShowAllQualities(value: Boolean) {
        _showAllQualities.update { _ -> value }
    }

    fun onClickHoster(hosterIndex: Int) {
        val hosterState = hosterState.value?.getOrNull()?.getOrNull(hosterIndex) ?: return

        when (hosterState) {
            is HosterState.Ready -> {
                _hosterExpandedList.update { values ->
                    values.toMutableList().apply {
                        this[hosterIndex] = !hosterExpandedList.value[hosterIndex]
                    }
                }
            }
            is HosterState.Error, is HosterState.Idle -> {
                val hosterName = hosterState.name
                _hosterState.updateAt(hosterIndex, HosterState.Loading(hosterName))

                viewModelScope.launchIO {
                    val newHosterState = episodeLoader.loadHosterVideos(
                        _source.value!!,
                        _hosterList.value[hosterIndex],
                    )
                    _hosterState.updateAt(hosterIndex, newHosterState)
                }
            }
            is HosterState.Loading -> {}
        }
    }

    fun onClickVideo(hosterIndex: Int, videoIndex: Int) {
        val video = (_hosterState.value?.getOrNull()?.getOrNull(hosterIndex) as? HosterState.Ready)
            ?.videoList
            ?.getOrNull(videoIndex)
            ?: return

        viewModelScope.launchIO {
            val success = loadVideo(_source.value!!, video, hosterIndex, videoIndex)
            if (success) {
                _showAllQualities.update { _ -> false }
            }
        }
    }

    fun getHosterList(): List<Hoster>? {
        val hosterStateList = hosterState.value?.getOrNull() ?: return null
        return _hosterList.value.mapIndexed { index, h ->
            if (hosterStateList[index] is HosterState.Ready) {
                Hoster(
                    hosterName = h.hosterName,
                    hosterUrl = h.hosterUrl,
                    videoList = (hosterStateList[index] as HosterState.Ready).videoList,
                    internalData = h.internalData,
                    memo = h.memo,
                )
            } else {
                h
            }
        }
    }

    fun downloadEpisode(useExternalDownloader: Boolean) {
        val anime = anime.value ?: return
        val episode = episode.value ?: return
        val currentVideo = currentVideo.value ?: return
        viewModelScope.launchIO {
            downloadManager.downloadEpisodes(
                anime,
                listOf(episode),
                true,
                useExternalDownloader,
                currentVideo,
            )
        }
    }
}

@Composable
fun EpisodeOptionsDialog(
    useExternalDownloader: Boolean,
    episodeTitle: String,
    episode: Episode?,
    anime: Anime?,
    showAllQualities: Boolean,
    resultList: Result<List<HosterState>>? = null,
    expandedList: List<Boolean>,
    currentVideo: Video?,
    selectedHosterVideoIndex: Pair<Int, Int>,
    onShowAllQualities: (Boolean) -> Unit,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    getHosterList: () -> List<Hoster>?,
    downloadEpisode: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = TabbedDialogPaddings.Vertical)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Text(
            text = episodeTitle,
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = MaterialTheme.typography.titleSmall,
        )

        Text(
            text = stringResource(AYMR.strings.choose_video_quality),
            modifier = Modifier.padding(horizontal = TabbedDialogPaddings.Horizontal),
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
        )

        val onError: () -> Unit = {
            logcat(LogPriority.ERROR) { "Error getting links" }
            scope.launchUI { context.toast("No available videos") }
            EpisodeOptionsDialogScreen.onDismissDialog()
        }
        if (resultList?.isFailure == true) {
            onError()
        }

        if (resultList == null || episode == null || anime == null || currentVideo == null) {
            LoadingScreen()
        } else {
            val hosterStateList = resultList.getOrNull()
            if (!hosterStateList.isNullOrEmpty()) {
                VideoList(
                    useExternalDownloader = useExternalDownloader,
                    episode = episode,
                    anime = anime,
                    showAllQualities = showAllQualities,
                    hosterStateList = hosterStateList,
                    expandedList = expandedList,
                    currentVideo = currentVideo,
                    selectedHosterVideoIndex = selectedHosterVideoIndex,
                    onShowAllQualities = onShowAllQualities,
                    onClickHoster = onClickHoster,
                    onClickVideo = onClickVideo,
                    getHosterList = getHosterList,
                    downloadEpisode = downloadEpisode,
                )
            } else {
                onError()
            }
        }
    }
}

@Composable
private fun VideoList(
    useExternalDownloader: Boolean,
    episode: Episode,
    anime: Anime,
    showAllQualities: Boolean,
    hosterStateList: List<HosterState>,
    expandedList: List<Boolean>,
    currentVideo: Video,
    selectedHosterVideoIndex: Pair<Int, Int>,
    onShowAllQualities: (Boolean) -> Unit,
    onClickHoster: (Int) -> Unit,
    onClickVideo: (Int, Int) -> Unit,
    getHosterList: () -> List<Hoster>?,
    downloadEpisode: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedString = stringResource(AYMR.strings.copied_video_link_to_clipboard)

    AnimatedVisibility(
        visible = !showAllQualities,
        enter = slideInHorizontally(),
        exit = slideOutHorizontally(),
    ) {
        Column {
            if (currentVideo.videoUrl.isNotEmpty() && !showAllQualities) {
                ClickableRow(
                    text = currentVideo.videoTitle,
                    icon = null,
                    onClick = { onShowAllQualities(true) },
                    showDropdownArrow = true,
                )

                QualityOptions(
                    onDownloadClicked = { downloadEpisode(useExternalDownloader) },
                    onExtDownloadClicked = { downloadEpisode(!useExternalDownloader) },
                    onCopyClicked = {
                        clipboardManager.setText(AnnotatedString(currentVideo.videoUrl))
                        scope.launch {
                            if (currentVideo.usesHttpServer()) {
                                MainActivity.startHttpServerService(context, anime.source)
                            }
                            context.toast(copiedString)
                        }
                    },
                    onExtPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                true,
                                anime.source,
                                currentVideo,
                            )
                        }
                    },
                    onIntPlayerClicked = {
                        scope.launch {
                            MainActivity.startPlayerActivity(
                                context,
                                anime.id,
                                episode.id,
                                false,
                                anime.source,
                                currentVideo,
                                selectedHosterVideoIndex.first,
                                selectedHosterVideoIndex.second,
                                getHosterList(),
                            )
                        }
                    },
                )
            }
        }
    }

    AnimatedVisibility(
        visible = showAllQualities,
        enter = slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = slideOutHorizontally(targetOffsetX = { it / 2 }),
    ) {
        if (showAllQualities) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TabbedDialogPaddings.Horizontal)
                    .heightIn(max = 600.dp),
            ) {
                if (
                    hosterStateList.size == 1 &&
                    hosterStateList.first().name == Hoster.NO_HOSTER_LIST &&
                    hosterStateList.first() is HosterState.Ready
                ) {
                    QualitySheetVideoContent(
                        videoList = (hosterStateList.first() as HosterState.Ready).videoList,
                        videoState = (hosterStateList.first() as HosterState.Ready).videoState,
                        selectedVideoIndex = selectedHosterVideoIndex.second,
                        onClickVideo = onClickVideo,
                    )
                } else {
                    QualitySheetHosterContent(
                        hosterState = hosterStateList,
                        expandedState = expandedList,
                        selectedVideoIndex = selectedHosterVideoIndex,
                        onClickHoster = onClickHoster,
                        onClickVideo = onClickVideo,
                        displayHosters = Pair(false, false),
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityOptions(
    onDownloadClicked: () -> Unit = {},
    onExtDownloadClicked: () -> Unit = {},
    onCopyClicked: () -> Unit = {},
    onExtPlayerClicked: () -> Unit = {},
    onIntPlayerClicked: () -> Unit = {},
) {
    val closeMenu = { EpisodeOptionsDialogScreen.onDismissDialog() }

    Column {
        ClickableRow(
            text = stringResource(MR.strings.copy),
            icon = MaterialSymbols.Rounded.ContentCopy,
            onClick = { onCopyClicked() },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_start_download_internally),
            icon = MaterialSymbols.Rounded.Download,
            onClick = {
                onDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_start_download_externally),
            icon = MaterialSymbols.Rounded.SystemUpdateAlt,
            onClick = {
                onExtDownloadClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_play_externally),
            icon = MaterialSymbols.AutoMirroredRounded.OpenInNew,
            onClick = {
                onExtPlayerClicked()
                closeMenu()
            },
        )

        ClickableRow(
            text = stringResource(AYMR.strings.action_play_internally),
            icon = MaterialSymbols.Rounded.Input,
            onClick = {
                onIntPlayerClicked()
                closeMenu()
            },
        )
    }
}

@Composable
private fun ClickableRow(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    showDropdownArrow: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = TabbedDialogPaddings.Horizontal)
            .clickable(role = Role.DropdownList, onClick = onClick)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var textPadding = MaterialTheme.padding.medium

        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.width(MaterialTheme.padding.small))

            textPadding = MaterialTheme.padding.small
        }
        Text(
            text = text,
            modifier = Modifier.padding(vertical = textPadding),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (showDropdownArrow) {
            Icon(
                imageVector = MaterialSymbols.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
// <-- AY
