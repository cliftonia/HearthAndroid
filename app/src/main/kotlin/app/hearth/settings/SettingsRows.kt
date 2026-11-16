package app.hearth.settings

import app.hearth.domain.ImageModelKind
import app.hearth.domain.ScheduledTask
import app.hearth.domain.ServerReachability
import app.hearth.domain.ServerSettings
import app.hearth.domain.SyncSettings

// How things stand behind each row of the settings list, for the list to put into words beside the row. Each is
// worked out here, from plain values, so that what a row says can be tested without drawing it.

/** Memory: off, or on with this many memories kept. */
sealed interface MemoryRowStatus {
    data object Off : MemoryRowStatus

    data class On(val count: Int) : MemoryRowStatus
}

/** Scheduled tasks: they need sync, none is on, or this many are. */
sealed interface TasksRowStatus {
    data object NeedsSync : TasksRowStatus

    data object None : TasksRowStatus

    data class Active(val count: Int) : TasksRowStatus
}

/** Voice: there is no speech server to speak with, or this voice, read aloud or not. */
sealed interface VoiceRowStatus {
    data object NeedsServer : VoiceRowStatus

    data class Chosen(val voice: String, val readsAloud: Boolean) : VoiceRowStatus
}

/** Which notifications are on. */
enum class NotificationsRowStatus { OFF, REPLIES, TASKS, REPLIES_AND_TASKS }

/** The chat server: being checked, answered with this many models, did not answer, or not yet asked. */
sealed interface ServersRowStatus {
    data object Checking : ServersRowStatus

    data class Connected(val modelCount: Int) : ServersRowStatus

    data object NotConnected : ServersRowStatus

    data object NotChecked : ServersRowStatus
}

/** Sync: off, or on, and when it last synced if it has. */
sealed interface SyncRowStatus {
    data object Off : SyncRowStatus

    data class On(val lastSyncedAt: Long?) : SyncRowStatus
}

fun memoryRowStatus(settings: ServerSettings, memoryCount: Int): MemoryRowStatus =
    if (settings.isMemoryEnabled) MemoryRowStatus.On(memoryCount) else MemoryRowStatus.Off

/** Tasks are run by the hub, so without sync none will run, whatever their switches say. */
fun tasksRowStatus(tasks: List<ScheduledTask>, canSync: Boolean): TasksRowStatus {
    val active = tasks.count { it.isEnabled }
    return when {
        !canSync -> TasksRowStatus.NeedsSync
        active == 0 -> TasksRowStatus.None
        else -> TasksRowStatus.Active(active)
    }
}

fun voiceRowStatus(settings: ServerSettings): VoiceRowStatus =
    if (settings.speechUrl == null) VoiceRowStatus.NeedsServer else VoiceRowStatus.Chosen(settings.voice, settings.isAutoReadEnabled)

/** The picture model chosen; `null` for Automatic. */
fun pictureRowStatus(settings: ServerSettings): ImageModelKind? = settings.imageModel

fun notificationsRowStatus(settings: ServerSettings): NotificationsRowStatus = when {
    settings.isReplyNotificationsEnabled && settings.isTaskNotificationsEnabled -> NotificationsRowStatus.REPLIES_AND_TASKS
    settings.isReplyNotificationsEnabled -> NotificationsRowStatus.REPLIES
    settings.isTaskNotificationsEnabled -> NotificationsRowStatus.TASKS
    else -> NotificationsRowStatus.OFF
}

/**
 * The last test of the connection, when there has been one since the address last changed; otherwise what the chat
 * found when it last asked the server for its models.
 */
fun serversRowStatus(test: ConnectionTest?, reachability: ServerReachability, modelCount: Int): ServersRowStatus = when (test) {
    ConnectionTest.Checking -> ServersRowStatus.Checking
    is ConnectionTest.Succeeded -> ServersRowStatus.Connected(test.modelCount)
    ConnectionTest.InvalidAddress, is ConnectionTest.Failed -> ServersRowStatus.NotConnected
    null -> when (reachability) {
        ServerReachability.REACHABLE -> ServersRowStatus.Connected(modelCount)
        ServerReachability.UNREACHABLE -> ServersRowStatus.NotConnected
        ServerReachability.UNKNOWN -> ServersRowStatus.NotChecked
    }
}

fun syncRowStatus(settings: SyncSettings, lastSyncedAt: Long?): SyncRowStatus =
    if (settings.isEnabled) SyncRowStatus.On(lastSyncedAt) else SyncRowStatus.Off
