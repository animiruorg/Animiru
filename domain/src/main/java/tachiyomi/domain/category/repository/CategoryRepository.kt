package tachiyomi.domain.category.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.NewCategory

interface CategoryRepository {

    suspend fun get(id: Long): Category?

    suspend fun getAll(): List<Category>

    fun getAllAsFlow(): Flow<List<Category>>

    suspend fun getCategoriesByAnimeId(animeId: Long): List<Category>

    fun getCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<Category>>

    // AY -->
    suspend fun getAllVisible(): List<Category>

    fun getAllVisibleAsFlow(): Flow<List<Category>>

    suspend fun getVisibleCategoriesByAnimeId(animeId: Long): List<Category>

    fun getVisibleCategoriesByAnimeIdAsFlow(animeId: Long): Flow<List<Category>>
    // <-- AY

    suspend fun insert(category: NewCategory)

    suspend fun insertAll(categories: List<NewCategory>)

    suspend fun updateName(categoryId: Long, name: String)

    suspend fun updateFlags(categoryId: Long, flags: Long)

    // AY -->
    suspend fun updateHidden(categoryId: Long, hidden: Boolean)
    // <-- AY

    suspend fun updateAllFlags(flags: Long?)

    suspend fun updateAllOrders(orderedIds: List<Long>)

    suspend fun delete(categoryId: Long)
}
