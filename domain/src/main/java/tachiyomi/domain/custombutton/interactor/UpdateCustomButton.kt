// AY -->
package tachiyomi.domain.custombutton.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.custombutton.model.CustomButtonUpdate
import tachiyomi.domain.custombutton.repository.CustomButtonRepository

@Inject
class UpdateCustomButton(
    private val customButtonRepository: CustomButtonRepository,
) {
    suspend fun await(update: CustomButtonUpdate): Result {
        return try {
            customButtonRepository.updatePartialCustomButton(update)
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    suspend fun await(updates: List<CustomButtonUpdate>): Result {
        return try {
            customButtonRepository.updatePartialCustomButtons(updates)
            Result.Success
        } catch (e: Exception) {
            Result.InternalError(e)
        }
    }

    sealed interface Result {
        data object Success : Result
        data class InternalError(val error: Throwable) : Result
    }
}
// <-- AY
