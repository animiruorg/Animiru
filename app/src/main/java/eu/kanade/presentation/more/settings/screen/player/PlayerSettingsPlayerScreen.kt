package eu.kanade.presentation.more.settings.screen.player

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import animiru.domain.player.model.PlayerOrientation
import animiru.domain.player.service.PlayerPreferences
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.JUST_PLAYER
import eu.kanade.tachiyomi.ui.player.MPV_KT
import eu.kanade.tachiyomi.ui.player.MPV_KT_PREVIEW
import eu.kanade.tachiyomi.ui.player.MPV_PLAYER
import eu.kanade.tachiyomi.ui.player.MPV_REMOTE
import eu.kanade.tachiyomi.ui.player.MX_PLAYER
import eu.kanade.tachiyomi.ui.player.MX_PLAYER_FREE
import eu.kanade.tachiyomi.ui.player.MX_PLAYER_PRO
import eu.kanade.tachiyomi.ui.player.NEXT_PLAYER
import eu.kanade.tachiyomi.ui.player.VLC_PLAYER
import eu.kanade.tachiyomi.ui.player.WEB_VIDEO_CASTER
import eu.kanade.tachiyomi.ui.player.X_PLAYER
import eu.kanade.tachiyomi.util.system.castIncluded
import eu.kanade.tachiyomi.util.system.toast
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

object PlayerSettingsPlayerScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_internal

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val playerPreferences = remember { context.appGraph.playerPreferences }
        val basePreferences = remember { context.appGraph.basePreferences }
        val deviceSupportsPip = basePreferences.deviceHasPip()

        return listOfNotNull(
            Preference.PreferenceItem.ListPreference(
                preference = playerPreferences.progressPreference,
                entries = mapOf(
                    1.00F to stringResource(AYMR.strings.pref_progress_100),
                    0.95F to stringResource(AYMR.strings.pref_progress_95),
                    0.90F to stringResource(AYMR.strings.pref_progress_90),
                    0.85F to stringResource(AYMR.strings.pref_progress_85),
                    0.80F to stringResource(AYMR.strings.pref_progress_80),
                    0.75F to stringResource(AYMR.strings.pref_progress_75),
                    0.70F to stringResource(AYMR.strings.pref_progress_70),
                ),
                title = stringResource(AYMR.strings.pref_progress_mark_as_seen),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = playerPreferences.preserveWatchingPosition,
                title = stringResource(AYMR.strings.pref_preserve_watching_position),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = playerPreferences.switchOnFailure,
                title = stringResource(AMMR.strings.player_pref_switch_on_failure),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = playerPreferences.defaultPlayerOrientationType,
                entries = PlayerOrientation.entries.associateWith {
                    stringResource(it.titleRes)
                },
                title = stringResource(AYMR.strings.pref_category_player_orientation),
            ),
            getControlsGroup(playerPreferences = playerPreferences),
            getHosterGroup(playerPreferences = playerPreferences),
            getDisplayGroup(playerPreferences = playerPreferences),
            getIntroSkipGroup(playerPreferences = playerPreferences),
            if (deviceSupportsPip) getPipGroup(playerPreferences = playerPreferences) else null,
            // AM (CAST) -->
            if (castIncluded) getCastGroup(playerPreferences = playerPreferences) else null,
            // <-- AM (CAST)
            getExternalPlayerGroup(
                playerPreferences = playerPreferences,
                basePreferences = basePreferences,
            ),
        )
    }

    @Composable
    private fun getControlsGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val allowGestures = playerPreferences.allowGestures
        val showLoading = playerPreferences.showLoadingCircle
        val showChapter = playerPreferences.showCurrentChapter
        val rememberPlayerBrightness = playerPreferences.rememberPlayerBrightness
        val rememberPlayerVolume = playerPreferences.rememberPlayerVolume

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_controls),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = allowGestures,
                    title = stringResource(AYMR.strings.pref_controls_allow_gestures_in_panels),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showLoading,
                    title = stringResource(AYMR.strings.pref_controls_show_loading),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showChapter,
                    title = stringResource(AYMR.strings.pref_controls_show_chapter_indicator),
                    subtitle = stringResource(AYMR.strings.pref_controls_show_chapter_indicator_info),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rememberPlayerBrightness,
                    title = stringResource(AYMR.strings.pref_remember_brightness),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rememberPlayerVolume,
                    title = stringResource(AYMR.strings.pref_remember_volume),
                ),
            ),
        )
    }

    @Composable
    private fun getHosterGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val showFailure = playerPreferences.showFailedHosters
        val showEmpty = playerPreferences.showEmptyHosters

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_hosters),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = showFailure,
                    title = stringResource(AYMR.strings.pref_hosters_show_failure),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showEmpty,
                    title = stringResource(AYMR.strings.pref_hosters_show_empty),
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val fullScreen = playerPreferences.playerFullscreen
        val hideControls = playerPreferences.hideControls
        val displayVol = playerPreferences.displayVolPer
        val showSystemBar = playerPreferences.showSystemStatusBar
        val reduceMotion = playerPreferences.reduceMotion
        val hideTime = playerPreferences.playerTimeToDisappear

        val numberFormat = remember { NumberFormat.getPercentInstance() }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = fullScreen,
                    title = stringResource(AYMR.strings.pref_player_fullscreen),
                    visible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = hideControls,
                    title = stringResource(AYMR.strings.pref_player_hide_controls),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = displayVol,
                    title = stringResource(AYMR.strings.pref_controls_display_volume_percentage),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = showSystemBar,
                    title = stringResource(AYMR.strings.pref_show_system_bar),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = reduceMotion,
                    title = stringResource(AYMR.strings.pref_reduce_motion),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = hideTime,
                    entries = listOf(500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000).associateWith {
                        stringResource(AYMR.strings.pref_player_time_to_disappear_summary, it)
                    },
                    title = stringResource(AYMR.strings.pref_player_time_to_disappear),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = playerPreferences.panelOpacity,
                    valueRange = 0..100,
                    title = stringResource(AYMR.strings.pref_panel_opacity),
                    valueText = { numberFormat.format(it / 100f) },
                ),
            ),
        )
    }

    @Composable
    private fun getIntroSkipGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val enableSkipIntro = playerPreferences.enableSkipIntro
        val isIntroSkipEnabled by enableSkipIntro.collectAsState()

        val enableAutoAniSkip = playerPreferences.autoSkipIntro
        val enableNetflixAniSkip = playerPreferences.enableNetflixStyleIntroSkip
        val waitingTimeAniSkip = playerPreferences.waitingTimeIntroSkip

        // AniSkip
        val enableAniSkip = playerPreferences.aniSkipEnabled
        val disableAniSkipChapters = playerPreferences.disableAniSkipOnChapters
        val isAniSkipEnabled by enableAniSkip.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_intro_skip),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = enableSkipIntro,
                    title = stringResource(AYMR.strings.pref_enable_intro_skip),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = enableAutoAniSkip,
                    title = stringResource(AYMR.strings.pref_enable_auto_skip_ani_skip),
                    visible = isIntroSkipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = enableNetflixAniSkip,
                    title = stringResource(AYMR.strings.pref_enable_netflix_style_aniskip),
                    visible = isIntroSkipEnabled,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = waitingTimeAniSkip,
                    entries = mapOf(
                        5 to stringResource(AYMR.strings.pref_waiting_time_aniskip_5),
                        6 to stringResource(AYMR.strings.pref_waiting_time_aniskip_6),
                        7 to stringResource(AYMR.strings.pref_waiting_time_aniskip_7),
                        8 to stringResource(AYMR.strings.pref_waiting_time_aniskip_8),
                        9 to stringResource(AYMR.strings.pref_waiting_time_aniskip_9),
                        10 to stringResource(AYMR.strings.pref_waiting_time_aniskip_10),
                    ),
                    title = stringResource(AYMR.strings.pref_waiting_time_aniskip),
                    visible = isIntroSkipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = enableAniSkip,
                    title = stringResource(AYMR.strings.pref_enable_aniskip),
                    visible = isIntroSkipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = disableAniSkipChapters,
                    title = stringResource(AYMR.strings.pref_disable_aniskip_chapter),
                    visible = isIntroSkipEnabled && isAniSkipEnabled,
                ),
                Preference.PreferenceItem.InfoPreference(
                    title = stringResource(AYMR.strings.pref_category_player_aniskip_info),
                    visible = isIntroSkipEnabled,
                ),
            ),
        )
    }

    @Composable
    private fun getPipGroup(playerPreferences: PlayerPreferences): Preference.PreferenceGroup {
        val enablePip = playerPreferences.enablePip
        val pipEpisodeToasts = playerPreferences.pipEpisodeToasts
        val pipOnExit = playerPreferences.pipOnExit
        val pipReplaceWithPrevious = playerPreferences.pipReplaceWithPrevious

        val isPipEnabled by enablePip.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_pip),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = enablePip,
                    title = stringResource(AYMR.strings.pref_enable_pip),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = pipEpisodeToasts,
                    title = stringResource(AYMR.strings.pref_pip_episode_toasts),
                    visible = isPipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = pipOnExit,
                    title = stringResource(AYMR.strings.pref_pip_on_exit),
                    visible = isPipEnabled,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = pipReplaceWithPrevious,
                    title = stringResource(AYMR.strings.pref_pip_replace_with_previous),
                    visible = isPipEnabled,
                ),
            ),
        )
    }

    // AM (CAST) -->
    @Composable
    private fun getCastGroup(
        playerPreferences: PlayerPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val enableCast = playerPreferences.enableCast
        val castProxy = playerPreferences.castProxy
        val castProxyPort = playerPreferences.castProxyPort

        val enableCastValue by enableCast.collectAsState()
        val enableCastProxyValue by castProxy.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(AMMR.strings.pref_cast),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = enableCast,
                    title = stringResource(AMMR.strings.pref_cast_enable),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = castProxy,
                    title = stringResource(AMMR.strings.pref_cast_proxy),
                    visible = enableCastValue,
                ),
                Preference.PreferenceItem.EditTextInfoPreference(
                    preference = castProxyPort,
                    dialogSubtitle = stringResource(AMMR.strings.pref_cast_port_subtitle),
                    title = stringResource(AMMR.strings.pref_cast_port),
                    visible = enableCastValue && enableCastProxyValue,
                    validate = { pref ->
                        val port = pref.toIntOrNull()
                            ?: return@EditTextInfoPreference false

                        if (port !in 1025..65535) {
                            return@EditTextInfoPreference false
                        }

                        true
                    },
                    errorMessage = { _ ->
                        stringResource(AMMR.strings.pref_cast_port_error)
                    },
                ),
            ),
        )
    }
    // <-- AM (CAST)

    @Composable
    private fun getExternalPlayerGroup(
        playerPreferences: PlayerPreferences,
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val alwaysUseExternalPlayer = playerPreferences.alwaysUseExternalPlayer
        val externalPlayerPreference = playerPreferences.externalPlayerPreference

        val pm = basePreferences.context.packageManager
        val installedPackages = pm.getInstalledPackages(0)
        val supportedPlayers = installedPackages.filter { it.packageName in externalPlayers }

        val packageNames = supportedPlayers.map { it.packageName }
        val packageNamesReadable = supportedPlayers
            .map { pm.getApplicationLabel(it.applicationInfo!!).toString() }

        val packageNamesMap: Map<String, String> =
            packageNames.zip(packageNamesReadable)
                .toMap()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_external_player),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = alwaysUseExternalPlayer,
                    title = stringResource(AYMR.strings.pref_always_use_external_player),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = externalPlayerPreference,
                    entries = (mapOf("" to "None") + packageNamesMap),
                    title = stringResource(AYMR.strings.pref_external_player_preference),
                ),
            ),
        )
    }
}

val externalPlayers = listOf(
    MPV_PLAYER,
    MX_PLAYER,
    MX_PLAYER_FREE,
    MX_PLAYER_PRO,
    VLC_PLAYER,
    MPV_KT,
    MPV_KT_PREVIEW,
    MPV_REMOTE,
    JUST_PLAYER,
    NEXT_PLAYER,
    X_PLAYER,
    WEB_VIDEO_CASTER,
)
