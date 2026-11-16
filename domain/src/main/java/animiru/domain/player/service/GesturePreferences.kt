package animiru.domain.player.service

import animiru.domain.player.model.SingleActionGesture
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

@Inject
@SingleIn(AppScope::class)
class GesturePreferences(
    preferenceStore: PreferenceStore,
) {
    // Sliders
    val gestureVolumeBrightness: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_gesture_volume_brightness",
        true,
    )
    val swapVolumeBrightness: Preference<Boolean> = preferenceStore.getBoolean("pref_swap_volume_and_brightness", false)

    // Gestures
    val gestureZoom: Preference<Boolean> = preferenceStore.getBoolean("pref_gesture_zoom", true)

    val gesturePan: Preference<Boolean> = preferenceStore.getBoolean("pref_gesture_pan", true)

    val gestureLongPress: Preference<Boolean> = preferenceStore.getBoolean("pref_gesture_long_press", true)

    // Seeking

    val gestureHorizontalSeek: Preference<Boolean> = preferenceStore.getBoolean("pref_gesture_horizontal_seek", true)
    val showSeekBar: Preference<Boolean> = preferenceStore.getBoolean("pref_show_seekbar", false)
    val defaultIntroLength: Preference<Int> = preferenceStore.getInt("pref_default_intro_length", 85)
    val skipLengthPreference: Preference<Int> = preferenceStore.getInt("pref_skip_length_preference", 10)
    val playerSmoothSeek: Preference<Boolean> = preferenceStore.getBoolean("pref_player_smooth_seek", false)

    // Double tap

    val leftDoubleTapGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_left_double_tap",
        SingleActionGesture.Seek,
    )
    val centerDoubleTapGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_center_double_tap",
        SingleActionGesture.PlayPause,
    )
    val rightDoubleTapGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_right_double_tap",
        SingleActionGesture.Seek,
    )

    // Media controls

    val mediaPreviousGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_media_previous",
        SingleActionGesture.Switch,
    )
    val mediaPlayPauseGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_media_playpause",
        SingleActionGesture.PlayPause,
    )
    val mediaNextGesture: Preference<SingleActionGesture> = preferenceStore.getEnum(
        "pref_media_next",
        SingleActionGesture.Switch,
    )
}
