package app.hearth

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.canvas.CanvasScreen
import app.hearth.chat.ChatScreen
import app.hearth.chat.MarkdownSection
import app.hearth.designsystem.HearthTheme
import app.hearth.designsystem.fadeThrough
import app.hearth.drawer.DrawerContent
import app.hearth.project.ProjectSheet
import app.hearth.projectpicker.ProjectPickerSheet
import app.hearth.settings.SettingsScreen
import app.hearth.settings.backAction
import app.hearth.shared.UnlockMethodKind
import app.hearth.shared.UndoSnackbarHost
import app.hearth.voice.VoiceScreen
import kotlin.coroutines.cancellation.CancellationException

/**
 * Lays out whichever screen the coordinator says is showing, with the chat list as a drawer on a phone and
 * as a column of its own on anything wide, where the chat's menu button puts it away and brings it back.
 * The canvas, when it is open, is a column beside the chat on anything wide and a page over it on a phone.
 * Also decides what the back button does, and raises the project sheets over it all.
 *
 * [authenticate] asks the phone to confirm who is holding it, and reports whether it did; [unlockMethod] is what it
 * will ask for.
 */
@Composable
internal fun RootView(coordinator: AppCoordinator, unlockMethod: UnlockMethodKind, authenticate: (onResult: (Boolean) -> Unit) -> Unit) {
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
    // What the back button does, the innermost first. The drawer's own sheet follows a back gesture as it is made and
    // closes it; this is only in case it does not.
    BackHandler(enabled = isDrawerOpen) { coordinator.drawerChanged(false) }
    // The canvas and the settings follow a back gesture as it is made, so that the user sees what going back will do
    // before letting go; a voice conversation ends at once, since there is nothing behind it to show.
    var canvasBack by remember { mutableFloatStateOf(0f) }
    var settingsBack by remember { mutableStateOf(BackPreview()) }
    LaunchedEffect(isCanvasOpen) { if (isCanvasOpen) canvasBack = 0f }
    PredictiveBackHandler(enabled = !isDrawerOpen && isCanvasOpen) { progress ->
        try {
            progress.collect { canvasBack = it.progress }
            // Left where the gesture took it, so that the page slides away from there.
            coordinator.canvas.closeAction()
        } catch (cancelled: CancellationException) {
            canvasBack = 0f
            throw cancelled
        }
    }
    BackHandler(enabled = !isDrawerOpen && !isCanvasOpen && screen == Screen.VOICE) { coordinator.voice.endAction() }
    PredictiveBackHandler(enabled = !isDrawerOpen && !isCanvasOpen && screen == Screen.SETTINGS) { progress ->
        var hasLeft = false
        try {
            progress.collect { settingsBack = BackPreview(it.progress, it.swipeEdge == BackEventCompat.EDGE_LEFT) }
            coordinator.settings.backAction()
            hasLeft = coordinator.screen.value != Screen.SETTINGS
        } finally {
            // Leaving the settings, they fade from where the gesture left them; otherwise they are drawn whole again.
            if (!hasLeft) settingsBack = BackPreview()
        }
    }
    LaunchedEffect(screen) { if (screen == Screen.SETTINGS) settingsBack = BackPreview() }

    // Under the project editor everything is still there, but a screen reader is not led into it.
    val isEditingProject = projectSheet == ProjectSheetKind.EDITOR
    BoxWithConstraints(Modifier.fillMaxSize().background(HearthTheme.colors.paper).then(if (isEditingProject) Modifier.clearAndSetSemantics { } else Modifier)) {
        // On a tablet or an unfolded phone the chat list sits beside the conversation until it is put away.
        val isWide = maxWidth >= HearthTheme.sizes.wideLayoutMinWidth
        val width = maxWidth.value
        SideEffect { coordinator.widthChanged(isWide, width) }
        // A document in the canvas is laid out by the chat's own renderer, which the canvas itself knows nothing of.
        val canvas: @Composable (Modifier, Boolean, Boolean) -> Unit = { modifier, canMakePage, isFullScreen ->
            CanvasScreen(coordinator.canvas, { MarkdownSection(it) }, modifier, canMakePage, isFullScreen)
        }
        val insets = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        // One screen gives way to another by Material's fade through: they are not one inside the other.
        val content: @Composable (Modifier) -> Unit = { modifier ->
            AnimatedContent(targetState = screen, modifier = modifier, transitionSpec = { fadeThrough() }, label = "screen") { shown ->
                when (shown) {
                    Screen.CHAT -> ChatScreen(
                        coordinator.chat,
                        coordinator.settingsStore,
                        coordinator.modelStore,
                        coordinator.attachments,
                        coordinator.pictures,
                        Modifier.fillMaxSize(),
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        coordinator.settings,
                        coordinator.profilePhotos,
                        authenticate,
                        unlockMethod,
                        Modifier.fillMaxSize().backPreview(settingsBack),
                    )
                    Screen.VOICE -> VoiceScreen(coordinator.voice, coordinator.deviceCamera, coordinator.cameraLeave, Modifier.fillMaxSize())
                }
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
                    canvas(Modifier.width(columns.canvasWidth.dp).fillMaxHeight().graphicsLayer { alpha = 1f - canvasBack * BACK_FADE }, true, false)
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = screen == Screen.CHAT && !isCanvasOpen,
                // Under the canvas the chat is still there, as it was left, but a screen reader is not led into it.
                modifier = if (isCanvasOpen) Modifier.clearAndSetSemantics { } else Modifier,
                drawerContent = {
                    // Given the drawer's state, the sheet follows a back gesture as it is made and closes on it.
                    ModalDrawerSheet(
                        drawerState = drawerState,
                        drawerContainerColor = HearthTheme.colors.paper,
                        // No tint from elevation: the drawer is the same paper as the fade under its floating buttons.
                        drawerTonalElevation = 0.dp,
                        modifier = Modifier.widthIn(max = HearthTheme.sizes.drawerWidth),
                    ) {
                        // Inside a project's chats, back first goes up to every chat, and only then closes the drawer.
                        // Set here, after the sheet's own, so that it is asked first.
                        val drawerProjectId by coordinator.drawer.selectedProjectId.collectAsStateWithLifecycle()
                        val projects by coordinator.drawer.projects.collectAsStateWithLifecycle()
                        BackHandler(enabled = isDrawerOpen && projects.any { it.id == drawerProjectId }) { coordinator.drawer.allChatsAction() }
                        DrawerContent(coordinator.drawer, chat.conversation.id, coordinator.settingsStore, coordinator.modelStore)
                    }
                },
            ) { content(insets) }
            // On a narrow window the canvas is a page of the full height, which rises over the chat.
            AnimatedVisibility(isCanvasOpen, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                // A web view made while the page slides in can be drawn over the whole window, so the page's web view
                // waits until the page has risen into place.
                var hasRisen by remember { mutableStateOf(false) }
                LaunchedEffect(transition.currentState) { if (transition.currentState == EnterExitState.Visible) hasRisen = true }
                // Touches stop here, so that nothing under the page is reached through it. A back gesture draws it
                // down a little, as it will go.
                val page = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = canvasBack * size.height * BACK_DRAG
                        alpha = 1f - canvasBack * BACK_FADE
                    }
                    .clipToBounds()
                    .background(HearthTheme.colors.paper)
                    .pointerInput(Unit) { }
                    .then(insets)
                canvas(page, hasRisen, true)
            }
        }
        // Offers to undo the latest delete, over whatever is showing and clear of the keyboard and the system bars.
        UndoSnackbarHost(coordinator.undo, Modifier.align(Alignment.BottomCenter).then(insets))
    }
    // The project sheets rise over whatever is showing, the chat list included.
    when (projectSheet) {
        ProjectSheetKind.EDITOR -> ProjectSheet(coordinator.project, coordinator.attachments)
        ProjectSheetKind.PICKER -> ProjectPickerSheet(coordinator.projectPicker)
        null -> Unit
    }
}

/** How far a back gesture has gone, from 0 to 1, and whether it began at the left edge. */
private data class BackPreview(val progress: Float = 0f, val isFromLeft: Boolean = true)

/** How much of its height the canvas's page is drawn down by a back gesture made in full. */
private const val BACK_DRAG = 0.15f

/** How much a page fades under a back gesture made in full. */
private const val BACK_FADE = 0.3f

/** How much a page shrinks under a back gesture made in full, as Android's own pages do. */
private const val BACK_SCALE = 0.1f

/** How far, in dp, a page moves away from the edge a back gesture began at. */
private const val BACK_SHIFT_DP = 8f

/** Draws a page a little smaller and away from the edge, as a back gesture is made, to show that it will go back. */
private fun Modifier.backPreview(preview: BackPreview): Modifier = graphicsLayer {
    val scale = 1f - preview.progress * BACK_SCALE
    scaleX = scale
    scaleY = scale
    translationX = (if (preview.isFromLeft) 1f else -1f) * preview.progress * BACK_SHIFT_DP.dp.toPx()
}
