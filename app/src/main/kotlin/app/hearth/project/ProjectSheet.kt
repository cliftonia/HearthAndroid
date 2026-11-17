package app.hearth.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.AttachedDocument
import app.hearth.integrations.AttachmentLoader
import app.hearth.shared.ErrorDialog
import app.hearth.shared.rememberDocumentPicker
import kotlinx.coroutines.launch

/** The project sheet: its name, its instructions, its reference files and, for one that exists, a way to delete it. */
@Composable
fun ProjectSheet(presenter: ProjectPresenter, attachments: AttachmentLoader) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    val spacing = HearthTheme.spacing
    val scope = rememberCoroutineScope()
    // Opened to its full height at once: half open, the files and the way to delete would be off the screen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isConfirmingDelete by remember { mutableStateOf(false) }
    val pickFiles = rememberDocumentPicker(
        attachments,
        onRead = { presenter.addDocumentsAction(it.documents, it.unreadableCount) },
        onUnavailable = presenter::filesUnavailableAction,
    )
    // The sheet slides away before it is taken off the screen.
    val cancel: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { presenter.cancelAction() } }
    ModalBottomSheet(
        onDismissRequest = presenter::cancelAction,
        sheetState = sheetState,
        containerColor = HearthTheme.colors.paper,
        // No tint from elevation: the sheet is the same paper as the screen it rises over.
        tonalElevation = 0.dp,
    ) {
        ProjectSheetHeader(
            title = stringResource(if (viewModel.isExisting) R.string.project_edit else R.string.project_new),
            canSave = viewModel.canSave,
            onCancel = cancel,
            onDone = presenter::saveAction,
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.xxxl, end = spacing.xxxl, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.gapL),
        ) {
            HearthCard(stringResource(R.string.project_name)) {
                OutlinedTextField(
                    value = viewModel.name,
                    onValueChange = presenter::nameAction,
                    placeholder = { Text(stringResource(R.string.project_name_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HearthCard(stringResource(R.string.project_instructions), stringResource(R.string.project_instructions_footer)) {
                OutlinedTextField(
                    value = viewModel.instructions,
                    onValueChange = presenter::instructionsAction,
                    placeholder = { Text(stringResource(R.string.project_instructions_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    minLines = 3,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FilesCard(viewModel.documents, onAdd = pickFiles, onRemove = presenter::removeDocumentAction)
            // Only a project that exists can be deleted, so a new one has no such row at all.
            if (viewModel.isExisting) DeleteRow { isConfirmingDelete = true }
        }
    }
    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text(stringResource(R.string.project_delete_question)) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingDelete = false
                    Haptics.play(HapticKind.WARNING)
                    presenter.deleteAction()
                }) { Text(stringResource(R.string.project_delete), color = HearthTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
    viewModel.error?.let { ErrorDialog(it, presenter::dismissErrorAction) }
}


/** The sheet's title between the two ways out of it: giving up the draft, and keeping it. */
@Composable
private fun ProjectSheetHeader(title: String, canSave: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    val colors = HearthTheme.colors
    Box(Modifier.fillMaxWidth().padding(start = HearthTheme.spacing.m, end = HearthTheme.spacing.m, bottom = HearthTheme.spacing.m)) {
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(stringResource(R.string.settings_cancel), color = colors.mute)
        }
        Text(
            title,
            color = colors.ink,
            style = HearthTheme.type.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
        TextButton(onClick = onDone, enabled = canSave, modifier = Modifier.align(Alignment.CenterEnd)) {
            Text(stringResource(R.string.common_done), color = if (canSave) colors.pine else colors.mute)
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
