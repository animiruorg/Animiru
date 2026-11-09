// AM -->
package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.animiru.AMMR

enum class RelationDisplayMode(val titleRes: StringResource) {
    DESCRIPTION(AMMR.strings.appearance_relation_description),
    SEPARATE(AMMR.strings.appearance_relation_separate),
    MENU(AMMR.strings.appearance_relation_menu),
}
// <-- AM
