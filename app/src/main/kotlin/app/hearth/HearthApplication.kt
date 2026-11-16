package app.hearth

import android.app.Application
import app.hearth.sync.BackgroundSyncHost

/**
 * Holds the coordinator for as long as the process lives, so that a reply keeps streaming and a conversation
 * keeps its place while the activity is rebuilt. The phone's background sync job reaches the coordinator through it.
 */
class HearthApplication : Application(), BackgroundSyncHost {
    lateinit var coordinator: AppCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        coordinator = AppCoordinator(AppDependencies.live(this)).also { it.start() }
    }

    override suspend fun backgroundSync() = coordinator.backgroundSync()
}
