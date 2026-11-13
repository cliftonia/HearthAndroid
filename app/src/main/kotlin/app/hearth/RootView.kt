package app.hearth

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.chat.ChatScreen
import app.hearth.designsystem.HearthTheme
import app.hearth.drawer.DrawerContent
import app.hearth.settings.SettingsScreen
import app.hearth.voice.VoiceScreen

/**
 * Lays out whichever screen the coordinator says is showing, with the chat list as a drawer on a phone and
 * as a column of its own on anything wide, where the chat's menu button puts it away and brings it back.
 * Also decides what the back button does.
 *
 * [authenticate] asks the phone to confirm who is holding it, and reports whether it did.
 */
@Composable
internal fun RootView(coordinator: AppCoordinator, authenticate: (onResult: (Boolean) -> Unit) -> Unit) {
    val screen by coordinator.screen.collectAsStateWithLifecycle()
    val isDrawerOpen by coordinator.isDrawerOpen.collectAsStateWithLifecycle()
    val isSideListShown by coordinator.isSideListShown.collectAsStateWithLifecycle()
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
        // On a tablet or an unfolded phone the chat list sits beside the conversation until it is put away.
        val isWide = maxWidth >= HearthTheme.sizes.wideLayoutMinWidth
        SideEffect { coordinator.widthChanged(isWide) }
        val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        val content: @Composable (Modifier) -> Unit = { modifier ->
            when (screen) {
                Screen.CHAT -> ChatScreen(
                    coordinator.chat,
                    coordinator.settingsStore,
                    coordinator.modelStore,
                    coordinator.attachments,
                    coordinator.pictures,
                    modifier,
                )
                Screen.SETTINGS -> SettingsScreen(coordinator.settings, authenticate, modifier)
                Screen.VOICE -> VoiceScreen(coordinator.voice, modifier)
            }
        }
        if (isWide) {
            Row(insets) {
                if (isSideListShown) {
                    val drawerWidth = Modifier.width(HearthTheme.sizes.drawerWidth)
                    DrawerContent(coordinator.drawer, chat.conversation.id, coordinator.settingsStore, coordinator.modelStore, drawerWidth)
                    Box(Modifier.width(HearthTheme.strokes.hairline).fillMaxHeight().background(HearthTheme.colors.line))
                }
                content(Modifier.weight(1f))
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = screen == Screen.CHAT,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = HearthTheme.colors.paper,
                        // No tint from elevation: the drawer is the same paper as the fade under its floating buttons.
                        drawerTonalElevation = 0.dp,
                        modifier = Modifier.widthIn(max = HearthTheme.sizes.drawerWidth),
                    ) {
                        DrawerContent(coordinator.drawer, chat.conversation.id, coordinator.settingsStore, coordinator.modelStore)
                    }
                },
            ) { content(insets) }
        }
    }
}
