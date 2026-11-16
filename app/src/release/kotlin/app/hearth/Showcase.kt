package app.hearth

import android.app.Application
import android.content.Intent
import app.hearth.domain.ChatModel

// A build that ships has no sample data: every hook below hands back exactly what it is given, and nothing can
// switch them. The debug build's version of this file fills the app with tidy examples for the README's pictures.

@Suppress("UNUSED_PARAMETER")
internal fun showcaseModels(application: Application, real: suspend (String) -> List<ChatModel>): suspend (String) -> List<ChatModel> = real

@Suppress("UNUSED_PARAMETER")
internal fun showcaseSync(application: Application, real: suspend () -> Int): suspend () -> Int = real

@Suppress("UNUSED_PARAMETER")
internal fun showcaseAllowsCapture(application: Application): Boolean = false

@Suppress("UNUSED_PARAMETER")
internal fun showcaseRequested(intent: Intent?): Boolean = false

@Suppress("UNUSED_PARAMETER")
internal fun startShowcase(application: HearthApplication) = Unit
