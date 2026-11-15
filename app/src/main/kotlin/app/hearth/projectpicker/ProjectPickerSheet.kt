package app.hearth.projectpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthSheetHeader
import app.hearth.designsystem.HearthTheme
import app.hearth.shared.ErrorDialog
import kotlinx.coroutines.launch

/**
 * The sheet that files a chat: the projects to choose from, then a new project and, for a chat already in one,
 * the way out of it.
 */
@Composable
fun ProjectPickerSheet(presenter: ProjectPickerPresenter) {
    val viewModel by presenter.viewModel.collectAsStateWithLifecycle()
    val projects by presenter.projects.collectAsStateWithLifecycle()
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    // The sheet slides away before it is taken off the screen.
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { presenter.closeAction() } }
    ModalBottomSheet(
        onDismissRequest = presenter::closeAction,
        sheetState = sheetState,
        containerColor = colors.paper,
        // No tint from elevation: the sheet is the same paper as the screen it rises over.
        tonalElevation = 0.dp,
    ) {
        HearthSheetHeader(stringResource(if (viewModel.isMoving) R.string.project_move else R.string.project_add), close)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = spacing.xxxl, end = spacing.xxxl, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.xxxl),
        ) {
            if (projects.isEmpty()) {
                Text(
                    stringResource(R.string.project_none),
                    color = colors.mute,
                    style = HearthTheme.type.subhead,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.m),
                )
            } else {
                // The saved projects in one card, the chat's present one ticked.
                PickerCard {
                    projects.forEachIndexed { index, project ->
                        val isSelected = project.id == viewModel.currentProjectId
                        PickerRow(project.name, Icons.Outlined.Folder, colors.ink, isSelected, isLast = index == projects.lastIndex) {
                            presenter.chooseAction(project.id)
                        }
                    }
                }
            }
            // A new project for the chat to go in and, when the chat is in one already, the way out of it.
            PickerCard {
                val newProject = stringResource(R.string.project_new_ellipsis)
                PickerRow(newProject, Icons.Outlined.CreateNewFolder, colors.pine, isSelected = null, isLast = !viewModel.isMoving, presenter::newProjectAction)
                if (viewModel.isMoving) {
                    PickerRow(stringResource(R.string.project_remove), Icons.Outlined.FolderOff, colors.danger, isSelected = null, isLast = true) {
                        presenter.chooseAction(null)
                    }
                }
            }
        }
    }
    viewModel.error?.let { ErrorDialog(it, presenter::dismissErrorAction) }
}

/** One card holding a group of choices. */
@Composable
private fun PickerCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(HearthTheme.shapes.card).background(HearthTheme.colors.card)) { content() }
}

/**
 * One choice: a symbol, its words, and a tick if it is how things stand now. [isSelected] is `null` for a row
 * that does something and is not one of the projects to choose between. The last row of a card has no line under it.
 */
@Composable
private fun PickerRow(title: String, icon: ImageVector, tint: Color, isSelected: Boolean?, isLast: Boolean, onClick: () -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    val action = if (isSelected == null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
    }
    Row(
        Modifier.fillMaxWidth().then(action).padding(horizontal = spacing.xxxl, vertical = spacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(HearthTheme.sizes.iconM))
        Text(title, color = tint, style = HearthTheme.type.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        // The tick keeps its place when it is not shown, so every name has the same room.
        Icon(Icons.Outlined.Check, null, tint = if (isSelected == true) colors.pine else Color.Transparent, modifier = Modifier.size(HearthTheme.sizes.iconM))
    }
    if (!isLast) HorizontalDivider(Modifier.padding(start = spacing.xxxl), thickness = HearthTheme.strokes.hairline, color = colors.line)
}
