package app.hearth.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Folder as FolderFilled
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthIcons
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.Project

// What stands above the dated groups: the projects, or the heading of the one whose chats are being looked at.

/**
 * The projects under their own heading, and the row that makes a new one. While searching, only the projects
 * whose names match are listed, and the heading and the new project row give way to the results.
 */
internal fun LazyListScope.projectListItems(projects: List<Project>, isSearching: Boolean, presenter: DrawerPresenter) {
    if (!isSearching || projects.isNotEmpty()) {
        item(key = "projects") { DrawerHeading(stringResource(R.string.project_title)) }
    }
    items(projects, key = { it.id.raw }) { project ->
        ProjectRow(project.name, Icons.Outlined.Folder, HearthTheme.colors.ink, HearthTheme.colors.mute) { presenter.selectProjectAction(project.id) }
    }
    if (!isSearching) {
        item(key = "new project") {
            ProjectRow(stringResource(R.string.project_new), Icons.Outlined.Add, HearthTheme.colors.mute, HearthTheme.colors.mute, presenter::newProjectAction)
        }
    }
}

/** The way back to every chat, then the project's name beside the button for its settings, then a new chat in it. */
internal fun LazyListScope.projectHeaderItems(project: Project, presenter: DrawerPresenter) {
    item(key = "all chats") {
        val mute = HearthTheme.colors.mute
        ProjectRow(stringResource(R.string.project_all_chats), Icons.AutoMirrored.Outlined.KeyboardArrowLeft, mute, mute, presenter::allChatsAction)
    }
    item(key = "project name") {
        val colors = HearthTheme.colors
        val spacing = HearthTheme.spacing
        Row(
            Modifier.fillMaxWidth().padding(start = spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Icon(Icons.Filled.FolderFilled, null, tint = colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconS))
            Text(
                project.name,
                color = colors.ink,
                style = HearthTheme.type.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            HearthIconButton(Icons.Outlined.Tune, stringResource(R.string.project_settings), onClick = presenter::projectSettingsAction)
        }
    }
    item(key = "new chat in project") {
        val pine = HearthTheme.colors.pine
        ProjectRow(stringResource(R.string.project_new_chat), HearthIcons.NewChat, pine, pine, presenter::newChatAction)
    }
}

/** A row the height of a chat's, with a symbol before its words, each in its own colour. */
@Composable
private fun ProjectRow(title: String, icon: ImageVector, tint: Color, symbolTint: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(HearthTheme.shapes.tile)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = HearthTheme.sizes.touchTarget)
            .padding(horizontal = HearthTheme.spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m),
    ) {
        Icon(icon, null, tint = symbolTint, modifier = Modifier.size(HearthTheme.sizes.iconS))
        Text(title, color = tint, style = HearthTheme.type.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
