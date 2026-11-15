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
import app.hearth.domain.NotificationProvider
import app.hearth.domain.ReplyNotification

/** The channel the phone files reply notifications under; the user can silence it in the phone's own settings. */
private const val REPLIES_CHANNEL = "replies"

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
    val channel = NotificationChannelCompat.Builder(REPLIES_CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        .setName(context.getString(R.string.notifications_replies))
        .setDescription(context.getString(R.string.notifications_replies_detail))
        .build()
    manager.createNotificationChannel(channel)

    fun post(notification: ReplyNotification) {
        // Before Android 13 there is no leave to ask for; from it on, leave may never have been given or may have been taken back.
        val hasLeave = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!hasLeave || !manager.areNotificationsEnabled()) {
            Log.i(TAG, "A reply notification was not posted: the phone does not allow them")
            return
        }
        val raw = notification.conversationId.raw
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_CHAT)
            .putExtra(MainActivity.EXTRA_CONVERSATION_ID, raw)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Each chat has a request of its own, so that one notification's tap cannot open another's chat.
        val tap = PendingIntent.getActivity(context, raw.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val built = NotificationCompat.Builder(context, REPLIES_CHANNEL)
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
        post = { post(it) },
        removeAll = { manager.cancelAll() },
    )
}

/** Opens the phone's own page of notification settings for the app, where leave is given once it has been refused. */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { Log.e(TAG, "The notification settings could not be opened: ${it.javaClass.simpleName}") }
}
