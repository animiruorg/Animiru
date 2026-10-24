// AY -->
package tachiyomi.domain.custombutton.interactor

import dev.zacsweers.metro.Inject
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.custombutton.model.CustomButtonUpdate
import tachiyomi.domain.custombutton.repository.CustomButtonRepository

@Inject
class DeleteCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend fun await(customButtonId: Long): Result {
        try {
            customButtonRepository.deleteCustomButton(customButtonId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return Result.InternalError(e)
        }

        val customButtons = customButtonRepository.getAll()
        val updates = customButtons.mapIndexed { index, customButton ->
            CustomButtonUpdate(
                id = customButton.id,
                sortIndex = index.toLong(),
            )
        }

        return try {
            customButtonRepository.updatePartialCustomButtons(updates)
            Result.Success
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
// <-- AY
