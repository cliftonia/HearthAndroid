package app.hearth

import android.app.Application

/**
 * Holds the coordinator for as long as the process lives, so that a reply keeps streaming and a conversation
 * keeps its place while the activity is rebuilt.
 */
class HearthApplication : Application() {
    lateinit var coordinator: AppCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        coordinator = AppCoordinator(AppDependencies.live(this)).also { it.start() }
    }
}
