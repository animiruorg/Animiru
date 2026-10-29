// AY -->
package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.ui.home.HomeScreen
import tachiyomi.i18n.MR

enum class StartScreen(val titleRes: StringResource, val tab: HomeScreen.Tab) {
    ANIME(MR.strings.label_library, HomeScreen.Tab.Library()),
    UPDATES(MR.strings.label_recent_updates, HomeScreen.Tab.Updates),
    HISTORY(MR.strings.label_recent_manga, HomeScreen.Tab.History),

    // AM (BROWSE) -->
    BROWSE(MR.strings.browse, HomeScreen.Tab.Browse(toExtensions = false)),
    // <-- AM (BROWSE)
}
// <-- AY
