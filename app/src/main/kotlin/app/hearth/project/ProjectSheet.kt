package app.hearth.project

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.AttachedDocument
import app.hearth.integrations.AttachmentLoader
import app.hearth.shared.DiscardChangesDialog
import app.hearth.shared.ErrorDialog
import app.hearth.shared.rememberDocumentPicker

/**
 * The project editor: its name, its instructions, its reference files and, for one that exists, a way to delete it.
 * On a phone it is Android's full-screen dialog: a top app bar with the close button at the start and Save at the end.
 * On a wider window the same is a dialog in the middle of it, over the dimmed app. Closing it with changes asks first.
 */
@Composable
fun ProjectSheet(presenter: ProjectPresenter, attachments: AttachmentLoader) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    var isConfirmingDelete by remember { mutableStateOf(false) }
    val pickFiles = rememberDocumentPicker(
        attachments,
        onRead = { presenter.addDocumentsAction(it.documents, it.unreadableCount) },
        onUnavailable = presenter::filesUnavailableAction,
    )
    // The close button and the back button would lose any changes, so the presenter is asked first.
    val close: () -> Unit = { if (presenter.dismissRequestAction()) presenter.cancelAction() }
    BackHandler(onBack = close)
    // Drawn in the app's own window, over everything else, so that it is laid out edge to edge as the app is and keeps
    // clear of the bars and the keyboard by their insets.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isFullScreen = maxWidth < HearthTheme.sizes.fullScreenDialogMaxWidth
        val appears = remember { MutableTransitionState(false).apply { targetState = true } }
        // In the middle of a wide window the app is dimmed behind it, and cannot be reached through the dimming.
        if (!isFullScreen) Box(Modifier.fillMaxSize().background(BottomSheetDefaults.ScrimColor).pointerInput(Unit) { })
        val frame = if (isFullScreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = HearthTheme.sizes.dialogMaxWidth)
                .fillMaxHeight(WIDE_DIALOG_HEIGHT)
        }
        AnimatedVisibility(appears, modifier = frame, enter = fadeIn() + slideInVertically { it / ENTER_RISE }) {
            Surface(shape = if (isFullScreen) RectangleShape else AlertDialogDefaults.shape, color = HearthTheme.colors.paper) {
                ProjectEditor(viewModel, presenter, isFullScreen, close, onAddFiles = pickFiles) { isConfirmingDelete = true }
            }
        }
    }
    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text(stringResource(R.string.project_delete_question)) },
            text = { Text(stringResource(R.string.project_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingDelete = false
                    Haptics.play(HapticKind.WARNING)
                    presenter.deleteAction()
                }) { Text(stringResource(R.string.common_delete), color = HearthTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    if (viewModel.isAskingToDiscard) {
        DiscardChangesDialog(
            onKeepEditing = presenter::keepEditingAction,
            onDiscard = {
                presenter.discardAction()
                presenter.cancelAction()
            },
        )
    }
    viewModel.error?.let { ErrorDialog(it, presenter::dismissErrorAction) }
}

/** How much of a wide window's height, inside its bars, the editor takes as a dialog in the middle of it. */
private const val WIDE_DIALOG_HEIGHT = 0.9f

/** How far up the editor rises as it appears: this part of its height. */
private const val ENTER_RISE = 8

/** The editor itself: the bar with its ways out, and the fields under it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectEditor(
    viewModel: ProjectViewModel,
    presenter: ProjectPresenter,
    isFullScreen: Boolean,
    onClose: () -> Unit,
    onAddFiles: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = HearthTheme.spacing
    val noInsets = WindowInsets(0, 0, 0, 0)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isExisting) R.string.project_edit else R.string.project_new), style = HearthTheme.type.title) },
                navigationIcon = { HearthIconButton(Icons.Outlined.Close, stringResource(R.string.common_close), onClick = onClose) },
                actions = {
                    TextButton(onClick = presenter::saveAction, enabled = viewModel.canSave) { Text(stringResource(R.string.common_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HearthTheme.colors.paper, titleContentColor = HearthTheme.colors.ink),
                // Full screen, the bar keeps clear of the status bar; in the middle of a wide window, there is none above it.
                windowInsets = if (isFullScreen) TopAppBarDefaults.windowInsets else noInsets,
            )
        },
        containerColor = HearthTheme.colors.paper,
        contentWindowInsets = if (isFullScreen) WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom) else noInsets,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.xxxl, end = spacing.xxxl, top = spacing.m, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.gapL),
        ) {
            HearthCard {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = presenter::nameAction,
                    label = { Text(stringResource(R.string.project_name)) },
                    placeholder = { Text(stringResource(R.string.project_name_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = viewModel.instructions,
                    onValueChange = presenter::instructionsAction,
                    label = { Text(stringResource(R.string.project_instructions)) },
                    placeholder = { Text(stringResource(R.string.project_instructions_placeholder)) },
                    supportingText = { Text(stringResource(R.string.project_instructions_footer)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 3,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FilesCard(viewModel.documents, onAdd = onAddFiles, onRemove = presenter::removeDocumentAction)
            // Only a project that exists can be deleted, so a new one has no such row at all.
            if (viewModel.isExisting) DeleteRow(onDelete)
        }
    }
}

/** The files, each with a button that takes it off, and the row that adds more. */
@Composable
private fun FilesCard(documents: List<AttachedDocument>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    HearthCard(stringResource(R.string.project_files), stringResource(R.string.project_files_footer)) {
        if (documents.isEmpty()) Text(stringResource(R.string.project_no_files), color = colors.mute, style = HearthTheme.type.callout)
        documents.forEach { document ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                Icon(Icons.Outlined.Description, null, tint = colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconS))
                Text(document.name, color = colors.ink, style = HearthTheme.type.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                HearthIconButton(Icons.Outlined.Close, stringResource(R.string.composer_remove_file, document.name), tint = colors.mute) {
                    onRemove(document.id)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(HearthTheme.shapes.control)
                .clickable(role = Role.Button, onClick = onAdd)
                .heightIn(min = HearthTheme.sizes.touchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.l),
        ) {
            Icon(Icons.Outlined.Add, null, tint = colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconS))
            Text(stringResource(R.string.project_add_files), color = colors.pine, style = HearthTheme.type.subhead)
        }
    }
}

/** Asks to delete the project. It stands apart from the cards, as the one thing here that cannot be undone. */
@Composable
private fun DeleteRow(onDelete: () -> Unit) {
    val spacing = HearthTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HearthTheme.shapes.card)
            .background(HearthTheme.colors.card)
            .clickable(role = Role.Button, onClick = onDelete)
            .heightIn(min = HearthTheme.sizes.touchTarget)
            .padding(horizontal = spacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.project_delete), color = HearthTheme.colors.danger, style = HearthTheme.type.subhead)
    }
}
