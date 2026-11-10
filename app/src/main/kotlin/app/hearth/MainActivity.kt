package app.hearth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.ui.ChatScreen
import app.hearth.ui.DrawerContent
import app.hearth.ui.HearthTheme
import app.hearth.ui.LockScreen
import app.hearth.ui.SettingsScreen
import app.hearth.ui.VoiceScreen
import app.hearth.ui.isDark

class MainActivity : FragmentActivity() {
    private val coordinator: AppCoordinator get() = (application as HearthApplication).coordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        publishVoiceShortcut()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by coordinator.settingsStore.settings.collectAsStateWithLifecycle()
            val isLocked by coordinator.lock.isLocked.collectAsStateWithLifecycle()
            val isDark = settings.appearance.isDark()
            // The clock and battery icons must stay readable over whichever theme the user chose.
            DisposableEffect(isDark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            // With the lock on, the conversations are also kept out of the recent apps list and out of screenshots.
            LaunchedEffect(settings.isLockEnabled) {
                if (settings.isLockEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            HearthTheme(isDark) {
                // While locked nothing else is drawn at all, so no menu or dialog can sit above the lock.
                if (isLocked) {
                    LockScreen(onUnlock = { authenticate(R.string.lock_reason) { isConfirmed -> if (isConfirmed) coordinator.lock.unlockedAction() } })
                } else {
                    RootView(coordinator) { onResult -> authenticate(R.string.lock_enable_reason, onResult) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        // Turning the phone round is not leaving the app.
        if (!isChangingConfigurations) coordinator.lock.backgroundedAction()
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_VOICE) coordinator.startVoice()
    }

    /** Offers "Start voice conversation" when the app's icon is held down. */
    private fun publishVoiceShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, "voice")
            .setShortLabel(getString(R.string.composer_voice))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_voice))
            .setIntent(Intent(this, MainActivity::class.java).setAction(ACTION_VOICE))
            .build()
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, listOf(shortcut)) }
    }

    /** Asks for a fingerprint, a face or the phone's own PIN. A phone with none of these set up cannot confirm anyone. */
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

    private companion object {
        const val ACTION_VOICE = "app.hearth.action.VOICE"
    }
}

@Composable
private fun RootView(coordinator: AppCoordinator, authenticate: (onResult: (Boolean) -> Unit) -> Unit) {
    val screen by coordinator.screen.collectAsStateWithLifecycle()
    val isDrawerOpen by coordinator.isDrawerOpen.collectAsStateWithLifecycle()
    val chat by coordinator.chat.viewModel.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val focusManager = LocalFocusManager.current

    // The coordinator says when the drawer should be open; a swipe by hand is reported back to it.
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) {
            focusManager.clearFocus()
            drawerState.open()
        } else {
            drawerState.close()
        }
    }
    LaunchedEffect(drawerState.currentValue) { coordinator.drawerChanged(drawerState.currentValue == DrawerValue.Open) }
    BackHandler(enabled = isDrawerOpen || screen != Screen.CHAT) {
        when {
            isDrawerOpen -> coordinator.drawerChanged(false)
            screen == Screen.VOICE -> coordinator.voice.endAction()
            else -> coordinator.settings.doneAction()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(HearthTheme.colors.paper)) {
        // On a tablet or an unfolded phone the chat list stays beside the conversation.
        val isWide = maxWidth >= 840.dp
        val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        val content: @Composable (Modifier) -> Unit = { modifier ->
            when (screen) {
                Screen.CHAT -> ChatScreen(coordinator.chat, coordinator.settingsStore, coordinator.modelStore, coordinator.attachments, modifier, showsMenu = !isWide)
                Screen.SETTINGS -> SettingsScreen(coordinator.settings, authenticate, modifier)
                Screen.VOICE -> VoiceScreen(coordinator.voice, modifier)
            }
        }
        if (isWide) {
            Row(insets) {
                DrawerContent(coordinator.drawer, chat.conversation.id, coordinator.settingsStore, coordinator.modelStore, Modifier.width(320.dp))
                Box(Modifier.width(1.dp).fillMaxHeight().background(HearthTheme.colors.line))
                content(Modifier.weight(1f))
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = screen == Screen.CHAT,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = HearthTheme.colors.paper, modifier = Modifier.widthIn(max = 320.dp)) {
                        DrawerContent(coordinator.drawer, chat.conversation.id, coordinator.settingsStore, coordinator.modelStore)
                    }
                },
            ) { content(insets) }
        }
    }
}
