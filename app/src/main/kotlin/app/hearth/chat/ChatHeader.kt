package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock as LockFilled
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.hearth.R
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ChatModel
import app.hearth.domain.ServerSettings

/** The top of the chat: the way to the chat list, the model in use, and the private chat button. */
@Composable
internal fun ChatHeader(viewModel: ChatViewModel, models: List<ChatModel>, settings: ServerSettings, presenter: ChatPresenter, showsMenu: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = HearthTheme.spacing.xs, vertical = HearthTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsMenu) {
            HearthIconButton(Icons.Outlined.Menu, stringResource(R.string.chat_menu), onClick = presenter::drawerAction)
        } else {
            // An empty place as wide as the button keeps the model's name in the middle.
            Spacer(Modifier.size(HearthTheme.sizes.touchTarget))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ModelPicker(viewModel.selectedModel, models, presenter::selectModelAction)
        }
        val canChange = viewModel.conversation.messages.isEmpty() && settings.isMemoryEnabled && settings.ollamaUrl != null
        PrivateToggle(viewModel.conversation.isPrivate, canChange, presenter::togglePrivateAction)
    }
}

/** Makes a chat that has not yet begun private. Once the chat has begun it only shows that the chat is private. */
@Composable
private fun PrivateToggle(isPrivate: Boolean, canChange: Boolean, onToggle: () -> Unit) {
    val colors = HearthTheme.colors
    if (!canChange && !isPrivate) {
        Spacer(Modifier.size(HearthTheme.sizes.touchTarget))
        return
    }
    IconButton(onClick = onToggle, enabled = canChange, modifier = Modifier.semantics { selected = isPrivate }) {
        Box(
            Modifier.size(HearthTheme.sizes.controlM).background(if (isPrivate) colors.pineSoft else Color.Transparent, HearthTheme.shapes.circle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPrivate) Icons.Filled.LockFilled else Icons.Outlined.Lock,
                stringResource(R.string.chat_private),
                tint = if (isPrivate) colors.pine else colors.ink,
                modifier = Modifier.size(HearthTheme.sizes.iconL),
            )
        }
    }
}

/** The name of the model in use, opening a menu of the models the server offers: its own first, then cloud ones. */
@Composable
private fun ModelPicker(selected: ChatModel?, models: List<ChatModel>, onSelect: (ChatModel) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    val colors = HearthTheme.colors
    Box {
        val openLabel = stringResource(R.string.chat_model_picker)
        TextButton(onClick = { isOpen = true }, modifier = Modifier.semantics { onClick(label = openLabel, action = null) }) {
            Text(
                selected?.displayName ?: stringResource(R.string.chat_no_model),
                color = colors.ink,
                style = HearthTheme.type.controlTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = HearthTheme.sizes.modelNameMaxWidth),
            )
            Icon(Icons.Outlined.ExpandMore, null, tint = colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconS))
        }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            listOf(R.string.chat_local_models to models.filterNot { it.isCloud }, R.string.chat_cloud_models to models.filter { it.isCloud })
                .filter { it.second.isNotEmpty() }
                .forEach { (title, group) ->
                    Text(
                        stringResource(title),
                        color = colors.mute,
                        style = HearthTheme.type.caption,
                        modifier = Modifier.padding(horizontal = HearthTheme.spacing.xl, vertical = HearthTheme.spacing.s).semantics { heading() },
                    )
                    group.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.displayName, fontWeight = if (model.id == selected?.id) FontWeight.SemiBold else null)
                                    model.parameterSize?.let { Text(it, color = colors.mute, style = HearthTheme.type.controlDetail) }
                                }
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
