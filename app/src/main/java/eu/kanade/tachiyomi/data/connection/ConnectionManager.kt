// AM (CONNECTION) -->
package eu.kanade.tachiyomi.data.connection

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.data.connection.discord.Discord

@Inject
@SingleIn(AppScope::class)
class ConnectionManager {

    companion object {
        // AM (DISCORD_RPC) -->
        const val DISCORD = 201L
        // <-- AM (DISCORD_RPC)
    }

    // AM (DISCORD_RPC) -->
    val discord = Discord(DISCORD)
    // <-- AM (DISCORD_RPC)

    val services: List<BaseConnection> = listOf(discord)
}
// <-- AM (CONNECTION)
