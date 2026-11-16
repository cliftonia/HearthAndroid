package app.hearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ImageModelKind
import app.hearth.domain.ServerSettings
import app.hearth.shared.title

/**
 * Which model new pictures are made with, and what else the picture server can do. The server is asked as the page
 * opens, since the address can no longer be changing.
 */
@Composable
internal fun PicturesPage(settings: ServerSettings, presenter: SettingsPresenter) {
    val capabilities by presenter.pictureCapabilities.collectAsStateWithLifecycle()
    LaunchedEffect(settings.imageUrl) { presenter.loadPictureCapabilitiesAction() }
    HearthCard(footer = stringResource(R.string.picture_footer)) {
        if (capabilities.models.isEmpty()) {
            Text(stringResource(R.string.picture_no_models), color = HearthTheme.colors.mute, style = HearthTheme.type.subhead)
        } else {
            ModelRow(settings.imageModel, capabilities.models) { model -> presenter.updateAction { it.copy(imageModel = model) } }
            Ability(stringResource(R.string.picture_can_change), capabilities.canEdit)
            Ability(stringResource(R.string.picture_can_enlarge), capabilities.canEnlarge)
        }
    }
}

/**
 * The model new pictures are made with, opening a menu of those the server has. Automatic leaves the choice to the app.
 * TalkBack hears the button as the picture model's, with the one chosen.
 */
@Composable
private fun ModelRow(chosen: ImageModelKind?, models: List<ImageModelKind>, onSelect: (ImageModelKind?) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    val colors = HearthTheme.colors
    // A model chosen earlier that this server lacks is shown as it will be treated: the choice is the app's.
    val shown = chosen?.takeIf { it in models }
    val shownName = shown?.title() ?: stringResource(R.string.picture_model_automatic)
    val buttonDescription = stringResource(R.string.picture_model_button, shownName)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.picture_model), color = colors.ink, style = HearthTheme.type.subhead, modifier = Modifier.weight(1f))
        Box {
            TextButton(
                onClick = { isOpen = true },
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = buttonDescription
                    role = Role.Button
                    onClick {
                        isOpen = true
                        true
                    }
                },
            ) {
                Text(shownName, color = colors.pine, style = HearthTheme.type.control)
                Icon(Icons.Outlined.ExpandMore, null, tint = colors.mute, modifier = Modifier.padding(start = HearthTheme.spacing.xs).size(HearthTheme.sizes.iconS))
            }
            DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
                (listOf<ImageModelKind?>(null) + models).forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                model?.title() ?: stringResource(R.string.picture_model_automatic),
                                fontWeight = if (model == shown) FontWeight.SemiBold else null,
                            )
                        },
                        onClick = {
                            isOpen = false
                            onSelect(model)
                        },
                    )
                }
            }
        }
    }
}

/** One thing the server can or cannot do, with a tick or a dash. */
@Composable
private fun Ability(title: String, isThere: Boolean) {
    val colors = HearthTheme.colors
    val state = stringResource(if (isThere) R.string.picture_ability_there else R.string.picture_ability_missing)
    Row(
        Modifier.semantics(mergeDescendants = true) { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m),
    ) {
        Icon(
            if (isThere) Icons.Filled.CheckCircle else Icons.Outlined.RemoveCircleOutline,
            null,
            tint = if (isThere) colors.pine else colors.mute,
            modifier = Modifier.size(HearthTheme.sizes.iconS),
        )
        Text(title, color = if (isThere) colors.ink else colors.mute, style = HearthTheme.type.subhead)
    }
}
