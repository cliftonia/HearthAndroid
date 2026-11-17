package app.hearth.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.hearth.MainActivity
import app.hearth.R

/**
 * A home screen widget that is one tile to tap: a symbol, a few words and the app's name, opening the app to do
 * one thing. It shows nothing that changes, so it is drawn once and left alone.
 */
abstract class ShortcutWidget(
    /** The words on the tile. */
    private val title: Int,
    /** The symbol on the tile. */
    private val icon: Int,
    /** The action of the intent the tile sends to the app's window. */
    private val action: String,
) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val open = Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val views = RemoteViews(context.packageName, R.layout.widget_shortcut).apply {
            setTextViewText(R.id.widget_title, context.getString(title))
            setImageViewResource(R.id.widget_icon, icon)
            setContentDescription(android.R.id.background, context.getString(title))
            setOnClickPendingIntent(android.R.id.background, tap)
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}

/** Opens Hearth on a fresh chat. */
class NewChatWidget : ShortcutWidget(R.string.widget_new_chat_title, R.drawable.ic_widget_new_chat, MainActivity.ACTION_NEW_CHAT)

/** Opens Hearth straight into a voice conversation. */
class VoiceWidget : ShortcutWidget(R.string.widget_voice_title, R.drawable.ic_widget_voice, MainActivity.ACTION_VOICE)
