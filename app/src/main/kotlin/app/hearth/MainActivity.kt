package app.hearth

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.designsystem.HapticsHost
import app.hearth.designsystem.HearthTheme
import app.hearth.designsystem.isDark
import app.hearth.domain.ConversationId
import app.hearth.integrations.rememberUnlockMethod
import app.hearth.lock.LockScreen

/**
 * The app's one window. It draws the lock or the screens, and does what only an activity can: asks for a
 * fingerprint, publishes the home screen shortcut, takes the intents of shortcuts, widgets and other apps' share
 * sheets, and keeps the window private.
 *
 * It holds no state of its own. Everything lives in the [AppCoordinator], which outlives it.
 */
class MainActivity : FragmentActivity() {
    private val coordinator: AppCoordinator get() = (application as HearthApplication).coordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publishVoiceShortcut()
        holdFirstFrameUntilReady()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val isReady by coordinator.isReady.collectAsStateWithLifecycle()
            // Nothing is composed from the defaults: the lock and the theme wait for the saved settings.
            if (isReady) Content()
        }
    }

    /** The lock or the screens, in the theme the user chose, with the window kept private while the lock is on. */
    @Composable
    private fun Content() {
        val settings by coordinator.settingsStore.settings.collectAsStateWithLifecycle()
        val isLocked by coordinator.lock.isLocked.collectAsStateWithLifecycle()
        val isDark = settings.appearance.isDark()
        val unlockMethod = rememberUnlockMethod()
        // Every haptic in the app asks this one switch, and is played through this window.
        HapticsHost(settings.isHapticsEnabled)
        // The clock and battery icons must stay readable over whichever theme the user chose.
        DisposableEffect(isDark) {
            val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            onDispose { }
        }
        // With the lock on, the conversations are also kept out of the recent apps list and out of screenshots.
        LaunchedEffect(settings.isLockEnabled) {
            val secure = WindowManager.LayoutParams.FLAG_SECURE
            if (settings.isLockEnabled && !showcaseAllowsCapture(application)) window.addFlags(secure) else window.clearFlags(secure)
        }
        HearthTheme(isDark) {
            // While locked nothing else is drawn at all, so no menu or dialog can sit above the lock.
            if (isLocked) {
                LockScreen(
                    unlockMethod,
                    onUnlock = { authenticate(R.string.lock_reason) { isConfirmed -> if (isConfirmed) coordinator.lock.unlockedAction() } },
                )
            } else {
                RootView(coordinator, unlockMethod) { onResult -> authenticate(R.string.lock_enable_reason, onResult) }
            }
        }
    }

    /**
     * Keeps the launch screen up until the saved settings have been read off the main thread, which takes a moment
     * at most, so that the first frame drawn is already the right one.
     */
    private fun holdFirstFrameUntilReady() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!coordinator.isReady.value) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        coordinator.foregroundChanged(isOnScreen = true)
    }

    override fun onStop() {
        super.onStop()
        // Turning the phone round is not leaving the app.
        if (!isChangingConfigurations) {
            coordinator.foregroundChanged(isOnScreen = false)
            coordinator.lock.backgroundedAction()
        }
    }

    /**
     * Does what the app was opened for: a voice conversation or a fresh chat from a shortcut or a widget, or
     * taking in what another app shared. It waits for the saved settings, which say whether voice has a server.
     */
    private fun handle(intent: Intent?) = coordinator.whenReady {
        if (showcaseRequested(intent)) {
            startShowcase(application as HearthApplication)
            return@whenReady
        }
        when (intent?.action) {
            ACTION_VOICE -> coordinator.startVoice(isFromOutside = true)
            ACTION_NEW_CHAT -> coordinator.startNewChat()
            ACTION_OPEN_CHAT -> openNotifiedChat(intent)
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> receiveShare(intent)
        }
    }

    /**
     * Opens the chat a tapped notification was about. An intent brought back from the recent apps list is one
     * already followed, so it is left alone.
     */
    private fun openNotifiedChat(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val id = intent.getStringExtra(EXTRA_CONVERSATION_ID)?.takeIf { it.isNotEmpty() } ?: return
        coordinator.openConversation(ConversationId(id))
    }

    /**
     * Hands what was shared to the chat. An intent brought back from the recent apps list is one already taken
     * in, so it is left alone.
     */
    private fun receiveShare(intent: Intent) {
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        coordinator.receiveShared(text, streams.map(Uri::toString))
    }

    /** Offers "Start voice conversation" when the app's icon is held down. */
    private fun publishVoiceShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, VOICE_SHORTCUT_ID)
            .setShortLabel(getString(R.string.composer_voice))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_voice))
            .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_VOICE))
            .build()
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut)) }
    }

    /**
     * Asks for a fingerprint, a face or the phone's own screen lock. A phone with none of these set up cannot confirm
     * anyone. Only unlocking and turning the lock on ask; turning it off does not.
     */
    private fun authenticate(reason: Int, onResult: (Boolean) -> Unit) {
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onResult(false)
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
        }
        val prompt = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.lock_title))
            .setSubtitle(getString(reason))
            .setAllowedAuthenticators(authenticators)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(prompt)
    }

    companion object {
        /** The action of the intent the voice shortcut and the voice widget send. */
        const val ACTION_VOICE = "app.hearth.action.VOICE"

        /** The action of the intent that opens the app on a fresh chat, as the New Chat widget does. */
        const val ACTION_NEW_CHAT = "app.hearth.action.NEW_CHAT"

        /** The action of the intent a reply notification sends when it is tapped, with [EXTRA_CONVERSATION_ID]. */
        const val ACTION_OPEN_CHAT = "app.hearth.action.OPEN_CHAT"

        /** Carries the identifier of the chat a notification was about. */
        const val EXTRA_CONVERSATION_ID = "app.hearth.extra.CONVERSATION_ID"

        /** The name the launcher knows the shortcut by; changing it would orphan one already pinned. */
        private const val VOICE_SHORTCUT_ID = "voice"
    }
}
