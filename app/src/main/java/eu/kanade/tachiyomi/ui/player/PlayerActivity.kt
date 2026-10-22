/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Code is a mix between PlayerActivity from mpvKt and the former
 * PlayerActivity from Aniyomi.
 */

package eu.kanade.tachiyomi.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import animiru.domain.player.model.ArtType
import animiru.domain.player.model.CustomKeyCodes
import animiru.domain.player.model.SetAsArt
import animiru.domain.player.model.SingleActionGesture
import animiru.domain.player.service.GesturePreferences
import animiru.domain.player.service.PlayerPreferences
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.Inject
import eu.kanade.domain.connection.service.ConnectionPreferences
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SerializableHoster.Companion.serialize
import eu.kanade.tachiyomi.data.connection.discord.DiscordRPCService
import eu.kanade.tachiyomi.data.connection.discord.PlayerData
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.isNsfw
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.player.components.MAX_BRIGHTNESS
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class PlayerActivity : BaseActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    private val viewModel by viewModels<PlayerViewModel> { graph.viewModelFactory }
    private val windowInsetsController by lazy { WindowCompat.getInsetsController(window, window.decorView) }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val inputMethodManager by lazy { getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager }

    private var mediaSession: MediaSession? = null

    @Inject private lateinit var gesturePreferences: GesturePreferences

    @Inject private lateinit var playerPreferences: PlayerPreferences

    // AM (DISCORD_RPC) -->
    @Inject private lateinit var connectionPreferences: ConnectionPreferences
    // <-- AM (DISCORD_RPC)

    private var pipRect: Rect? = null
    val isPipSupportedAndEnabled by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            playerPreferences.enablePip.get()
    }

    private var pipReceiver: BroadcastReceiver? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        var initialized = false
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                viewModel.pause()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    companion object {
        fun newIntent(
            context: Context,
            animeId: Long?,
            episodeId: Long?,
            hostList: List<Hoster>? = null,
            hostIndex: Int? = null,
            vidIndex: Int? = null,
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("animeId", animeId)
                putExtra("episodeId", episodeId)
                hostIndex?.let { putExtra("hostIndex", it) }
                vidIndex?.let { putExtra("vidIndex", it) }
                hostList?.let { putExtra("hostList", it.serialize()) }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val animeId = intent.extras?.getLong("animeId") ?: -1
        val episodeId = intent.extras?.getLong("episodeId") ?: -1
        val hostList = intent.extras?.getString("hostList") ?: ""
        val hostIndex = intent.extras?.getInt("hostIndex") ?: -1
        val vidIndex = intent.extras?.getInt("vidIndex") ?: -1
        if (animeId == -1L || episodeId == -1L) {
            finish()
            return
        }
        NotificationReceiver.dismissNotification(
            this,
            animeId.hashCode(),
            Notifications.ID_NEW_EPISODES,
        )

        lifecycleScope.launch {
            viewModel.updateIsLoadingEpisode(true)
            viewModel.updateIsLoadingHosters(true)
            viewModel.updateHasPip(
                packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
                    playerPreferences.enablePip.get(),
            )

            val initResult = viewModel.init(animeId, episodeId, hostList, hostIndex, vidIndex)
            if (!initResult.second.getOrDefault(false)) {
                val exception = initResult.second.exceptionOrNull() ?: IllegalStateException(
                    "Unknown error",
                )
                withContext(Dispatchers.Main) {
                    setInitialEpisodeError(exception)
                }
            }

            viewModel.updateIsLoadingHosters(false)

            lifecycleScope.launch {
                viewModel.loadHosters(
                    hosterList = initResult.first.hosterList ?: emptyList(),
                    hosterIndex = initResult.first.videoIndex.first,
                    videoIndex = initResult.first.videoIndex.second,
                )
            }
        }

        setIntent(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        enableEdgeToEdge()
        registerSecureActivity(this)
        super.onCreate(savedInstanceState)

        setupMediaSession()
        viewModel.setupPlayerOrientation()
        updateDiscordRPC(exitingPlayer = false)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runOnUiThread {
                toast(throwable.message)
            }
            logcat(LogPriority.ERROR, throwable)
            finish()
        }

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    PlayerViewModel.Event.EnterPip -> {
                        enterPictureInPictureMode(createPipParams())
                    }
                    is PlayerViewModel.Event.EpisodeTitle -> {
                        if (isInPictureInPictureMode) {
                            showToast(event.name)
                        }
                    }
                    PlayerViewModel.Event.Finish -> {
                        finish()
                    }
                    is PlayerViewModel.Event.InitialEpisodeError -> {
                        setInitialEpisodeError(event.error)
                    }
                    is PlayerViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is PlayerViewModel.Event.SetArtResult -> {
                        onSetAsArtResult(event.result, event.artType)
                    }
                    is PlayerViewModel.Event.SetKeyboard -> {
                        if (event.show) {
                            forceShowSoftwareKeyboard()
                        } else {
                            forceHideSoftwareKeyboard()
                        }
                    }
                    is PlayerViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.seconds)
                    }
                    is PlayerViewModel.Event.ToastResource -> {
                        showToast(this.stringResource(event.stringRes))
                    }
                    is PlayerViewModel.Event.ToastString -> {
                        showToast(event.string)
                    }
                    PlayerViewModel.Event.ToggleKeyboard -> {
                        toggleShowSoftwareKeyboard()
                    }
                    is PlayerViewModel.Event.UpdateDiscordRPC -> {
                        updateDiscordRPC(exitingPlayer = false, paused = event.paused, position = event.position)
                    }
                }
            }
            .launchIn(lifecycleScope)

        setContent {
            TachiyomiTheme {
                PlayerScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (isPipSupportedAndEnabled && !viewModel.playbackData.value.paused &&
                            playerPreferences.pipOnExit.get() && !viewModel.stateData.value.isCasting
                        ) {
                            enterPictureInPictureMode(createPipParams())
                        } else {
                            finish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned {
                            pipRect = run {
                                val boundsInWindow = it.boundsInWindow()
                                Rect(
                                    boundsInWindow.left.toInt(),
                                    boundsInWindow.top.toInt(),
                                    boundsInWindow.right.toInt(),
                                    boundsInWindow.bottom.toInt(),
                                )
                            }
                        },
                )
            }
        }

        onNewIntent(this.intent)
    }

    override fun onDestroy() {
        viewModel.player.release()
        viewModel.stopHttpServer()

        mediaSession?.let {
            it.isActive = false
            it.release()
        }

        if (noisyReceiver.initialized) {
            unregisterReceiver(noisyReceiver)
            noisyReceiver.initialized = false
        }

        super.onDestroy()
    }

    override fun onPause() {
        updateDiscordRPC(exitingPlayer = false)

        if (isInPictureInPictureMode) {
            super.onPause()
            return
        }

        viewModel.setPlayerExiting(true)
        if (isFinishing) {
            viewModel.deletePendingEpisodes()
            viewModel.mpvCommand("stop")
        } else {
            viewModel.pause()
        }

        super.onPause()
    }

    override fun onStop() {
        if (isInPictureInPictureMode && powerManager.isInteractive) {
            viewModel.deletePendingEpisodes()
        } else if (!isInPictureInPictureMode) {
            updateDiscordRPC(exitingPlayer = true)
        }

        super.onStop()
    }

    override fun onUserLeaveHint() {
        if (isPipSupportedAndEnabled && !viewModel.playbackData.value.paused && playerPreferences.pipOnExit.get()) {
            enterPictureInPictureMode()
        }
        super.onUserLeaveHint()
    }

    override fun onStart() {
        super.onStart()
        setPictureInPictureParams(createPipParams())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LOW_PROFILE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (playerPreferences.playerFullscreen.get()) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }
        }

        updateDiscordRPC(exitingPlayer = false)
    }

    override fun onResume() {
        updateDiscordRPC(exitingPlayer = false)

        if (!viewModel.isPlayerExiting()) {
            super.onResume()
            return
        }

        viewModel.setPlayerExiting(false)
        super.onResume()

        viewModel.setVolumeTo(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).also {
                if (it < viewModel.stateData.value.maxVolume) viewModel.changeMPVVolumeTo(100)
            },
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            viewModel.setAspectRatio(playerPreferences.aspectState.get())
        } else {
            viewModel.hideControls()
        }
        super.onConfigurationChanged(newConfig)
    }

    fun showToast(stringRes: StringResource) {
        runOnUiThread { toast(stringRes) }
    }

    fun showToast(message: String) {
        runOnUiThread { toast(message) }
    }

    fun createPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val anime = viewModel.stateData.value.currentAnime
            val episode = viewModel.stateData.value.currentEpisode

            if (anime != null && episode != null) {
                builder.setTitle(anime.title).setSubtitle(episode.name)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val autoEnter = playerPreferences.pipOnExit.get()
            builder.setAutoEnterEnabled(!viewModel.playbackData.value.paused && autoEnter)
            builder.setSeamlessResizeEnabled(!viewModel.playbackData.value.paused && autoEnter)
        }
        builder.setActions(
            createPipActions(
                context = this,
                isPaused = viewModel.playbackData.value.paused,
                replaceWithPrevious = playerPreferences.pipReplaceWithPrevious.get(),
                playlistCount = viewModel.stateData.value.currentPlaylist.size,
                playlistPosition = viewModel.stateData.value.currentPlaylistIndex,
            ),
        )
        builder.setSourceRectHint(pipRect)
        viewModel.stateData.value.let {
            val rational = if (it.videoWidth > 0 && it.videoHeight > 0) {
                Rational(it.videoWidth, it.videoHeight)
            } else {
                Rational(16, 9)
            }
            if (rational.toDouble() in 0.42..2.38) {
                builder.setAspectRatio(rational)
            }
        }
        return builder.build()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        if (!isInPictureInPictureMode) {
            pipReceiver?.let {
                unregisterReceiver(pipReceiver)
                pipReceiver = null
            }

            if (lifecycle.currentState == Lifecycle.State.CREATED) {
                window.decorView.postDelayed(
                    {
                        viewModel.player.release()
                        finish()
                    },
                    100,
                )
            } else {
                window.attributes = window.attributes.apply {
                    val brightness = viewModel.playbackData.value.currentBrightness
                    screenBrightness = if (brightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    } else {
                        brightness.coerceIn(0f, MAX_BRIGHTNESS)
                    }
                }
            }
        } else {
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            setPictureInPictureParams(createPipParams())
            viewModel.hideControls()
            viewModel.hideSeekBar()
            viewModel.displayBrightnessSlider(false)
            viewModel.displayVolumeSlider(false)
            viewModel.setSheet(Sheets.None)
            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null || intent.action != PIP_INTENTS_FILTER) return
                    when (intent.getIntExtra(PIP_INTENT_ACTION, 0)) {
                        PIP_PAUSE -> viewModel.pause()
                        PIP_PLAY -> viewModel.unpause()
                        PIP_NEXT -> viewModel.nextEpisode(next = true)
                        PIP_PREVIOUS -> viewModel.nextEpisode(next = false)
                        PIP_SKIP -> viewModel.seekBy(10)
                    }
                    setPictureInPictureParams(createPipParams())
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER), RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, IntentFilter(PIP_INTENTS_FILTER))
            }
        }

        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.changeVolumeBy(1)
                viewModel.displayVolumeSlider(true)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.changeVolumeBy(-1)
                viewModel.displayVolumeSlider(true)
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_DPAD_RIGHT -> viewModel.handleRightDoubleTap()
            KeyEvent.KEYCODE_SPACE -> viewModel.pauseUnpause()
            KeyEvent.KEYCODE_MEDIA_STOP -> finishAndRemoveTask()

            KeyEvent.KEYCODE_MEDIA_REWIND -> viewModel.handleLeftDoubleTap()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> viewModel.handleRightDoubleTap()

            // other keys should be bound by the user in input.conf ig
            else -> {
                event?.let { viewModel.onKey(it) }
                super.onKeyDown(keyCode, event)
            }
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.onKey(event!!)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun setupMediaSession() {
        val previousAction = gesturePreferences.mediaPreviousGesture.get()
        val playAction = gesturePreferences.mediaPlayPauseGesture.get()
        val nextAction = gesturePreferences.mediaNextGesture.get()

        mediaSession = MediaSession(this, "PlayerActivity").apply {
            setCallback(
                object : MediaSession.Callback() {
                    override fun onPlay() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPlay()
                                viewModel.unpause()
                            }
                            SingleActionGesture.Custom -> {
                                viewModel.mpvCommand("keypress", CustomKeyCodes.MediaPlay.keyCode)
                            }

                            SingleActionGesture.Switch -> {}
                        }
                    }

                    override fun onPause() {
                        when (playAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {}
                            SingleActionGesture.PlayPause -> {
                                super.onPause()
                                viewModel.pause()
                            }
                            SingleActionGesture.Custom -> {
                                viewModel.mpvCommand("keypress", CustomKeyCodes.MediaPlay.keyCode)
                            }

                            SingleActionGesture.Switch -> {}
                        }
                    }

                    override fun onSkipToPrevious() {
                        when (previousAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.leftSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                viewModel.mpvCommand("keypress", CustomKeyCodes.MediaPrevious.keyCode)
                            }

                            SingleActionGesture.Switch -> viewModel.nextEpisode(next = false)
                        }
                    }

                    override fun onSkipToNext() {
                        when (nextAction) {
                            SingleActionGesture.None -> {}
                            SingleActionGesture.Seek -> {
                                viewModel.rightSeek()
                            }
                            SingleActionGesture.PlayPause -> {
                                viewModel.pauseUnpause()
                            }
                            SingleActionGesture.Custom -> {
                                viewModel.mpvCommand("keypress", CustomKeyCodes.MediaNext.keyCode)
                            }

                            SingleActionGesture.Switch -> viewModel.nextEpisode(next = true)
                        }
                    }

                    override fun onStop() {
                        super.onStop()
                        isActive = false
                        this@PlayerActivity.onStop()
                    }
                },
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_STOP or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_SKIP_TO_NEXT,
                    )
                    .build(),
            )
            isActive = true
        }

        val filter = IntentFilter().apply { addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY) }
        registerReceiver(noisyReceiver, filter)
        noisyReceiver.initialized = true
    }

    // ==== END MPVKT ====

    /**
     * Called from the presenter if the initial load couldn't load the videos of the episode. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialEpisodeError(error: Throwable) {
        if (error is PlayerViewModel.ExceptionWithStringResource) {
            showToast(error.stringResource)
        } else {
            showToast(error.message ?: "")
        }
        logcat(LogPriority.ERROR, error)
        finish()
    }

    /**
     * Called from the presenter when a screenshot is ready to be shared. It shows Android's
     * default sharing tool.
     */
    private fun onShareImageResult(uri: Uri, seconds: String) {
        val anime = viewModel.stateData.value.currentAnime ?: return
        val episode = viewModel.stateData.value.currentEpisode ?: return

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = stringResource(AYMR.strings.share_screenshot_info, anime.title, episode.name, seconds),
        )
        startActivity(intent)
    }

    /**
     * Called from the presenter when a screenshot is saved or fails. It shows a message
     * or logs the event depending on the [result].
     */
    private fun onSaveImageResult(result: PlayerViewModel.SaveImageResult) {
        when (result) {
            is PlayerViewModel.SaveImageResult.Success -> {
                showToast(MR.strings.picture_saved)
            }
            is PlayerViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the presenter when a screenshot is set as art or fails.
     * It shows a different message depending on the [result].
     */
    private fun onSetAsArtResult(result: SetAsArt, artType: ArtType) {
        showToast(
            when (result) {
                SetAsArt.Success ->
                    when (artType) {
                        ArtType.Cover -> MR.strings.cover_updated
                        ArtType.Background -> AYMR.strings.background_updated
                        ArtType.Thumbnail -> AYMR.strings.thumbnail_updated
                    }
                SetAsArt.AddToLibraryFirst -> MR.strings.notification_first_add_to_library
                SetAsArt.Error -> MR.strings.notification_cover_update_failed
            },
        )
    }

    private fun toggleShowSoftwareKeyboard() {
        if (inputMethodManager.isActive) {
            forceHideSoftwareKeyboard()
        } else {
            forceShowSoftwareKeyboard()
        }
    }

    private fun forceShowSoftwareKeyboard() {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    private fun forceHideSoftwareKeyboard() {
        inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
    }

    // AM (DISCORD_RPC) -->
    private fun updateDiscordRPC(exitingPlayer: Boolean, paused: Boolean = false, position: Int? = null) {
        if (!connectionPreferences.enableDiscordRPC.get()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!exitingPlayer) {
                    val playbackData = viewModel.playbackData.value
                    val stateData = viewModel.stateData.value
                    if (playbackData.duration == 0) return@launch

                    val timePos = position?.seconds ?: playbackData.position.seconds
                    val duration = playbackData.duration.seconds

                    val startTimestamp = Clock.System.now() - timePos
                    val endTimestamp = startTimestamp + duration

                    val anime = stateData.currentAnime ?: return@launch
                    val episode = stateData.currentEpisode ?: return@launch

                    DiscordRPCService.setPlayerActivity(
                        context = this@PlayerActivity,
                        PlayerData(
                            incognitoMode = stateData.currentSource?.isNsfw() == true || stateData.incognitoMode,
                            paused = paused,
                            animeId = anime.id,
                            animeTitle = anime.ogTitle,
                            thumbnailUrl = anime.thumbnailUrl ?: "",
                            episodeNumber = if (connectionPreferences.discordShowEpisodeTitle.get()) {
                                episode.name
                            } else {
                                episode.episodeNumber.toString()
                            },
                            startTimestamp = startTimestamp.toEpochMilliseconds().takeUnless { paused },
                            endTimestamp = endTimestamp.toEpochMilliseconds().takeUnless { paused },
                        ),
                    )
                } else {
                    val lastUsedScreen = DiscordRPCService.lastUsedScreen
                    DiscordRPCService.setScreen(this@PlayerActivity, lastUsedScreen)
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Error updating Discord RPC: ${e.message}" }
            }
        }
    }
    // <-- AM (DISCORD_RPC)
}
