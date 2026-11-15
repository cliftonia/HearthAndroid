package app.hearth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.canvas.CanvasScreen
import app.hearth.chat.ChatScreen
import app.hearth.chat.MarkdownSection
import app.hearth.designsystem.HearthTheme
import app.hearth.drawer.DrawerContent
import app.hearth.project.ProjectSheet
import app.hearth.projectpicker.ProjectPickerSheet
import app.hearth.settings.SettingsScreen
import app.hearth.voice.VoiceScreen

/**
 * Lays out whichever screen the coordinator says is showing, with the chat list as a drawer on a phone and
 * as a column of its own on anything wide, where the chat's menu button puts it away and brings it back.
 * The canvas, when it is open, is a column beside the chat on anything wide and a page over it on a phone.
 * Also decides what the back button does, and raises the project sheets over it all.
 *
 * [authenticate] asks the phone to confirm who is holding it, and reports whether it did.
 */
@Composable
internal fun RootView(coordinator: AppCoordinator, authenticate: (onResult: (Boolean) -> Unit) -> Unit) {
    val screen by coordinator.screen.collectAsStateWithLifecycle()
    val isDrawerOpen by coordinator.isDrawerOpen.collectAsStateWithLifecycle()
    val isSideListShown by coordinator.isSideListShown.collectAsStateWithLifecycle()
    val chat by coordinator.chat.viewModel.collectAsStateWithLifecycle()
    val projectSheet by coordinator.projectSheet.collectAsStateWithLifecycle()
    val isCanvasOpen by coordinator.isCanvasOpen.collectAsStateWithLifecycle()
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
    // The canvas takes the whole of a narrow window, so the keyboard under it is put away as it opens.
    LaunchedEffect(isCanvasOpen) { if (isCanvasOpen) focusManager.clearFocus() }
    BackHandler(enabled = isDrawerOpen || isCanvasOpen || screen != Screen.CHAT) {
        when {
            isDrawerOpen -> coordinator.drawerChanged(false)
            isCanvasOpen -> coordinator.canvas.closeAction()
            screen == Screen.VOICE -> coordinator.voice.endAction()
            else -> coordinator.settings.backAction()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(HearthTheme.colors.paper)) {
        // On a tablet or an unfolded phone the chat list sits beside the conversation until it is put away.
        val isWide = maxWidth >= HearthTheme.sizes.wideLayoutMinWidth
        val width = maxWidth.value
        SideEffect { coordinator.widthChanged(isWide, width) }
        // A document in the canvas is laid out by the chat's own renderer, which the canvas itself knows nothing of.
        val canvas: @Composable (Modifier) -> Unit = { modifier -> CanvasScreen(coordinator.canvas, { MarkdownSection(it) }, modifier) }
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
                Screen.SETTINGS -> SettingsScreen(coordinator.settings, coordinator.profilePhotos, authenticate, modifier)
                Screen.VOICE -> VoiceScreen(coordinator.voice, coordinator.deviceCamera, coordinator.cameraLeave, modifier)
            }
        }
        if (isWide) {
            // The canvas has half of the room beside the chat list, and the list gives way while there is not room for all three.
            val columns = coordinator.columns(width, isSideListShown, isCanvasOpen)
            val divider: @Composable () -> Unit = { Box(Modifier.width(HearthTheme.strokes.hairline).fillMaxHeight().background(HearthTheme.colors.line)) }
            Row(insets) {
                if (columns.sideListWidth > 0f) {
                    DrawerContent(coordinator.drawer, chat.conversation.id, coordinator.settingsStore, coordinator.modelStore, Modifier.width(columns.sideListWidth.dp))
                    divider()
                }
                content(Modifier.weight(1f))
                if (columns.canvasWidth > 0f) {
                    divider()
                    canvas(Modifier.width(columns.canvasWidth.dp).fillMaxHeight())
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = screen == Screen.CHAT && !isCanvasOpen,
                // Under the canvas the chat is still there, as it was left, but a screen reader is not led into it.
                modifier = if (isCanvasOpen) Modifier.clearAndSetSemantics { } else Modifier,
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
            // On a narrow window the canvas is a page of the full height, which rises over the chat.
            AnimatedVisibility(isCanvasOpen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                // Touches stop here, so that nothing under the page is reached through it.
                canvas(Modifier.fillMaxSize().background(HearthTheme.colors.paper).pointerInput(Unit) { }.then(insets))
            }
        }
    }
    // The project sheets rise over whatever is showing, the chat list included.
    when (projectSheet) {
        ProjectSheetKind.EDITOR -> ProjectSheet(coordinator.project, coordinator.attachments)
        ProjectSheetKind.PICKER -> ProjectPickerSheet(coordinator.projectPicker)
        null -> Unit
    }
}
