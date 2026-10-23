package mihon.domain.database.repository

import kotlinx.coroutines.flow.StateFlow

interface DatabaseRepository {

    /**
     * True while the database is being migrated from an older schema, which happens on its first use after an
     * app update.
     */
    val isMigrating: StateFlow<Boolean>

    // AM -->
    fun updateIsMigrating(value: Boolean)
    // <-- AM

    /**
     * Opens the database, migrating it if needed, and throws what made the migration fail.
     */
    suspend fun awaitOpen()
}
