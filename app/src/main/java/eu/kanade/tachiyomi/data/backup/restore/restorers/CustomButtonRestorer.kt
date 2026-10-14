// AY -->
package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupCustomButtons
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tachiyomi.data.Database
import tachiyomi.domain.custombutton.interactor.GetCustomButtons
import tachiyomi.domain.custombutton.repository.CustomButtonRepository

@Inject
class CustomButtonRestorer(
    private val database: Database,
    private val getCustomButtons: GetCustomButtons,
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend operator fun invoke(
        backupCustomButtons: List<BackupCustomButtons>,
    ) {
        if (backupCustomButtons.isNotEmpty()) {
            val dbCustomButtons = getCustomButtons.getAll()
            val dbCustomButtonsByName = dbCustomButtons.associateBy { it.name }
            var nextSortIndex = dbCustomButtons.maxOfOrNull { it.sortIndex }?.plus(1) ?: 0
            val dbHasFavorite = dbCustomButtons.firstOrNull { it.isFavorite } != null

            backupCustomButtons
                .sortedBy { it.sortIndex }
                .forEach {
                    currentCoroutineContext().ensureActive()
                    val dbCustomButton = dbCustomButtonsByName[it.name]
                    if (dbCustomButton != null) return@forEach
                    val sortIndex = nextSortIndex++
                    val isFavorite = it.isFavorite && !dbHasFavorite
                    customButtonRepository.insertCustomButton(
                        name = it.name,
                        favorite = isFavorite,
                        sortIndex = sortIndex,
                        content = it.content,
                        longPressContent = it.longPressContent,
                        onStartup = it.onStartup,
                    )
                }
        }
    }
}
// <-- AY
