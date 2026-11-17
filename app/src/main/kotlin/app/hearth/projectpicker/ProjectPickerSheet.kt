package app.hearth.projectpicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthChoiceCard
import app.hearth.designsystem.HearthChoiceRow
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
                HearthChoiceCard {
                    projects.forEachIndexed { index, project ->
                        val isSelected = project.id == viewModel.currentProjectId
                        HearthChoiceRow(
                            project.name,
                            isSelected,
                            isLast = index == projects.lastIndex,
                            icon = Icons.Outlined.Folder,
                            verticalPadding = spacing.xxl,
                        ) { presenter.chooseAction(project.id) }
                    }
                }
            }
            // A new project for the chat to go in and, when the chat is in one already, the way out of it.
            HearthChoiceCard {
                val newProject = stringResource(R.string.project_new_ellipsis)
                HearthChoiceRow(
                    newProject,
                    isSelected = null,
                    isLast = !viewModel.isMoving,
                    icon = Icons.Outlined.CreateNewFolder,
                    tint = colors.pine,
                    verticalPadding = spacing.xxl,
                    onClick = presenter::newProjectAction,
                )
                if (viewModel.isMoving) {
                    HearthChoiceRow(
                        stringResource(R.string.project_remove),
                        isSelected = null,
                        isLast = true,
                        icon = Icons.Outlined.FolderOff,
                        tint = colors.danger,
                        verticalPadding = spacing.xxl,
                    ) { presenter.chooseAction(null) }
                }
            }
        }
    }
    viewModel.error?.let { ErrorDialog(it, presenter::dismissErrorAction) }
}
