package mihon.feature.common.utils

import dev.icerock.moko.resources.StringResource
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.i18n.MR
import tachiyomi.i18n.animiru.AMMR
import tachiyomi.i18n.aniyomi.AYMR

fun MigrationFlag.getLabel(): StringResource {
    return when (this) {
        MigrationFlag.EPISODE -> AYMR.strings.episodes
        MigrationFlag.SEASON -> AMMR.strings.seasons
        MigrationFlag.CATEGORY -> MR.strings.categories
        MigrationFlag.CUSTOM_COVER -> MR.strings.custom_cover
        // AY -->
        MigrationFlag.CUSTOM_BACKGROUND -> AYMR.strings.custom_background
        // <-- AY
        MigrationFlag.NOTES -> MR.strings.action_notes
        MigrationFlag.REMOVE_DOWNLOAD -> MR.strings.delete_downloaded
    }
}
