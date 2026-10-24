// AY -->
package tachiyomi.domain.custombutton.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombutton.model.CustomButton
import tachiyomi.domain.custombutton.model.CustomButtonUpdate
import tachiyomi.domain.custombutton.repository.CustomButtonRepository

@Inject
class ReorderCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    private val mutex = Mutex()

    suspend fun changeOrder(customButton: CustomButton, newIndex: Int): Result {
        mutex.withLock {
            val customButtons = customButtonRepository.getAll()
                .toMutableList()

            val currentIndex = customButtons.indexOfFirst { it.id == customButton.id }
            if (currentIndex == -1) {
                return Result.Unchanged
            }

            return try {
                customButtons.add(newIndex, customButtons.removeAt(currentIndex))

                val updates = customButtons.mapIndexed { index, customButton ->
                    CustomButtonUpdate(
                        id = customButton.id,
                        sortIndex = index.toLong(),
                    )
                }

                customButtonRepository.updatePartialCustomButtons(updates)
                Result.Success
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.InternalError(e)
            }
        }
    }

    sealed interface Result {
        data object Success : Result
        data object Unchanged : Result
        data class InternalError(val error: Throwable) : Result
    }
}
// <-- AY
