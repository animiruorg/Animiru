// AY -->
package tachiyomi.data.custombutton

import android.database.sqlite.SQLiteException
import app.cash.sqldelight.async.coroutines.awaitAsList
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.custombutton.exception.SaveCustomButtonException
import tachiyomi.domain.custombutton.model.CustomButton
import tachiyomi.domain.custombutton.model.CustomButtonUpdate
import tachiyomi.domain.custombutton.repository.CustomButtonRepository

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class CustomButtonRepositoryImpl(
    private val database: Database,
) : CustomButtonRepository {
    override fun subscribeAll(): Flow<List<CustomButton>> {
        return database.custom_buttonsQueries.findAll(::mapCustomButton).subscribeToList()
    }

    override suspend fun getAll(): List<CustomButton> {
        return database.custom_buttonsQueries.findAll(::mapCustomButton).awaitAsList()
    }

    override suspend fun insertCustomButton(
        name: String,
        favorite: Boolean,
        sortIndex: Long,
        content: String,
        longPressContent: String,
        onStartup: String,
    ) {
        try {
            database.custom_buttonsQueries.insert(
                name,
                favorite,
                sortIndex,
                content,
                longPressContent,
                onStartup,
            )
        } catch (ex: SQLiteException) {
            throw SaveCustomButtonException(ex)
        }
    }

    override suspend fun updatePartialCustomButton(update: CustomButtonUpdate) {
        database.custom_buttonsQueries.update(
            name = update.name,
            isFavorite = update.isFavorite,
            sortIndex = update.sortIndex,
            content = update.content,
            longPressContent = update.longPressContent,
            customButtonId = update.id,
            onStartup = update.onStartup,
        )
    }

    override suspend fun updatePartialCustomButtons(updates: List<CustomButtonUpdate>) {
        database.transaction {
            updates.forEach { updatePartialCustomButton(it) }
        }
    }

    override suspend fun deleteCustomButton(customButtonId: Long) {
        database.custom_buttonsQueries.delete(customButtonId)
    }

    private fun mapCustomButton(
        id: Long,
        name: String,
        isFavorite: Boolean,
        sortIndex: Long,
        content: String,
        longPressContent: String,
        onStartup: String,
    ): CustomButton = CustomButton(
        id = id,
        name = name,
        isFavorite = isFavorite,
        sortIndex = sortIndex,
        content = content,
        longPressContent = longPressContent,
        onStartup = onStartup,
    )
}
// <-- AY
