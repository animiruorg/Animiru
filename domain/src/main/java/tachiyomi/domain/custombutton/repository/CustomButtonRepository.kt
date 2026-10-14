// AY -->
package tachiyomi.domain.custombutton.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.custombutton.model.CustomButton
import tachiyomi.domain.custombutton.model.CustomButtonUpdate

interface CustomButtonRepository {

    fun subscribeAll(): Flow<List<CustomButton>>

    suspend fun getAll(): List<CustomButton>

    suspend fun insertCustomButton(
        name: String,
        favorite: Boolean,
        sortIndex: Long,
        content: String,
        longPressContent: String,
        onStartup: String,
    )

    suspend fun updatePartialCustomButton(update: CustomButtonUpdate)

    suspend fun updatePartialCustomButtons(updates: List<CustomButtonUpdate>)

    suspend fun deleteCustomButton(customButtonId: Long)
}
// <-- AY
