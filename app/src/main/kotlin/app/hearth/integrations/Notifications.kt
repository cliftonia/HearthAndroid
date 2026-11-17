package app.hearth.integrations

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.hearth.MainActivity
import app.hearth.R
import app.hearth.domain.NotificationKind
import app.hearth.domain.NotificationProvider
import app.hearth.domain.ReplyNotification

/** The channel the phone files reply notifications under; the user can silence it in the phone's own settings. */
private const val REPLIES_CHANNEL = "replies"

/** The channel for scheduled tasks' answers, so that they can be silenced apart from replies. */
private const val TASKS_CHANNEL = "tasks"

/** The phone's channel for each kind of notification. */
private val NotificationKind.channel: String
    get() = when (this) {
        NotificationKind.REPLY -> REPLIES_CHANNEL
        NotificationKind.TASK -> TASKS_CHANNEL
    }

/** The number every reply notification is posted under. The chat's identifier, as the tag, tells one from another. */
private const val REPLY_NOTIFICATION_ID = 1

private const val TAG = "Notifications"

/**
 * Posts notifications on the phone itself. Nothing is sent to or from a server, and nothing runs in the
 * background for it: a notification is posted by the app while it is still running a reply.
 *
 * Tapping one opens the app's window with [MainActivity.ACTION_OPEN_CHAT] and the chat's identifier.
 */
fun NotificationProvider.Companion.live(context: Context): NotificationProvider {
    val manager = NotificationManagerCompat.from(context)
    val channels = listOf(
        Triple(REPLIES_CHANNEL, R.string.notifications_replies, R.string.notifications_replies_detail),
        Triple(TASKS_CHANNEL, R.string.notifications_tasks, R.string.notifications_tasks_detail),
    ).map { (id, name, detail) ->
        NotificationChannelCompat.Builder(id, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(name))
            .setDescription(context.getString(detail))
            .build()
    }
    // Making the channels is the phone's business and should never stop the app from starting.
    runCatching { manager.createNotificationChannelsCompat(channels) }
        .onFailure { Log.e(TAG, "The notification channels could not be made: ${it.javaClass.simpleName}") }

    fun post(notification: ReplyNotification) {
        // Before Android 13 there is no leave to ask for; from it on, leave may never have been given or may have been taken back.
        val hasLeave = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasLeave || !manager.areNotificationsEnabled() || !isChannelOn(manager, notification.kind)) {
            Log.i(TAG, "A notification was not posted: the phone does not allow them")
            return
        }
        val raw = notification.conversationId.raw
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CHAT)
            .putExtra(MainActivity.EXTRA_CONVERSATION_ID, raw)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Each chat has a request of its own, so that one notification's tap cannot open another's chat.
        val tap = PendingIntent.getActivity(context, raw.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val built = NotificationCompat.Builder(context, notification.kind.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.pine))
            .setContentTitle(notification.title)
            .setContentText(notification.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // A later reply in the same chat takes the place of an earlier one.
        manager.notify(raw, REPLY_NOTIFICATION_ID, built)
    }

    return NotificationProvider(
        isAllowed = { manager.areNotificationsEnabled() },
        isKindAllowed = { isChannelOn(manager, it) },
        post = { post(it) },
        removeAll = { manager.cancelAll() },
    )
}

/** Whether the user has left this kind's channel on in the phone's settings. A channel not yet made counts as on. */
private fun isChannelOn(manager: NotificationManagerCompat, kind: NotificationKind): Boolean =
    runCatching { manager.getNotificationChannelCompat(kind.channel)?.importance != NotificationManagerCompat.IMPORTANCE_NONE }.getOrDefault(true)

/**
 * Opens the phone's own notification settings where whatever holds notifications back is changed: the app's page
 * while the app may not notify at all, and otherwise the page of the one kind's channel, [blockedKind], if it was
 * turned off there. Should the phone have no such page, the app's own page is opened instead.
 */
fun openNotificationSettings(context: Context, blockedKind: NotificationKind? = null) {
    val appPage = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    val isAppAllowed = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(false)
    val page = if (isAppAllowed && blockedKind != null) {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, blockedKind.channel)
    } else {
        appPage
    }
    val opened = (if (page === appPage) listOf(page) else listOf(page, appPage)).any { intent ->
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
    if (!opened) Log.e(TAG, "The notification settings could not be opened")
}
