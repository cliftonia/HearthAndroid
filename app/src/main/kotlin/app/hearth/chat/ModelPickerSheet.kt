package app.hearth.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.designsystem.HearthRoundButton
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ChatModel
import app.hearth.domain.ImageModelKind
import app.hearth.domain.ServerSettings
import app.hearth.domain.ThinkingLevel
import app.hearth.shared.blurb
import app.hearth.shared.shortName
import kotlinx.coroutines.launch

/**
 * The sheet opened from the composer's model chip: the server's models to choose from, and how hard the chosen
 * one should think. While pictures are being made it lists the picture models instead.
 */
@Composable
internal fun ModelPickerSheet(viewModel: ChatViewModel, models: List<ChatModel>, settings: ServerSettings, presenter: ChatPresenter, onDismiss: () -> Unit) {
    val spacing = HearthTheme.spacing
    val scope = rememberCoroutineScope()
    // Opened to its full height at once: half open, the effort below the list would be off the screen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isPictureMode = viewModel.isPictureMode
    // The sheet slides away before it is taken off the screen.
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HearthTheme.colors.paper,
        // No tint from elevation: the sheet is the same paper as the screen it rises over.
        tonalElevation = 0.dp,
    ) {
        SheetHeader(stringResource(if (isPictureMode) R.string.model_picture_sheet_title else R.string.model_sheet_title), close)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = spacing.xxxl, end = spacing.xxxl, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.xxxl),
        ) {
            if (isPictureMode) {
                PictureModels(viewModel.pictureModels, viewModel.selectedPictureModel(settings)) { model ->
                    presenter.selectPictureModelAction(model)
                    close()
                }
            } else {
                ChatModels(models, viewModel.selectedModel) { model ->
                    presenter.selectModelAction(model)
                    close()
                }
            }
        }
        // The effort stays in view below the list, however many models the server has.
        if (!isPictureMode && viewModel.selectedModel?.canThink == true) EffortRow(settings.thinkingLevel, presenter::thinkingLevelAction)
    }
}

/** The sheet's title, with the button that closes it at the leading edge. */
@Composable
private fun SheetHeader(title: String, onClose: () -> Unit) {
    val colors = HearthTheme.colors
    Box(Modifier.fillMaxWidth().padding(start = HearthTheme.spacing.xxxl, end = HearthTheme.spacing.xxxl, bottom = HearthTheme.spacing.xxxl)) {
        HearthRoundButton(
            icon = Icons.Outlined.Close,
            label = stringResource(R.string.common_close),
            containerColor = colors.card,
            contentColor = colors.ink,
            size = HearthTheme.sizes.touchTarget,
            iconSize = HearthTheme.sizes.iconS,
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onClose,
        )
        Text(
            title,
            color = colors.ink,
            style = HearthTheme.type.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
    }
}

/** The models on the server, then those in the cloud. */
@Composable
private fun ChatModels(models: List<ChatModel>, selected: ChatModel?, onSelect: (ChatModel) -> Unit) {
    if (models.isEmpty()) EmptyNote(stringResource(R.string.chat_no_model))
    listOf(R.string.chat_local_models to models.filterNot { it.isCloud }, R.string.chat_cloud_models to models.filter { it.isCloud })
        .filter { it.second.isNotEmpty() }
        .forEach { (title, group) ->
            Column(verticalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s)) {
                Text(
                    stringResource(title),
                    color = HearthTheme.colors.mute,
                    style = HearthTheme.type.caption,
                    modifier = Modifier.padding(horizontal = HearthTheme.spacing.xs).semantics { heading() },
                )
                ModelRows(group) { model, isLast -> ModelRow(model.displayName, model.detail(), model.id == selected?.id, isLast) { onSelect(model) } }
            }
        }
}

/** The picture models the server offers, or a word that they are still being asked for. */
@Composable
private fun PictureModels(models: List<ImageModelKind>, selected: ImageModelKind?, onSelect: (ImageModelKind) -> Unit) {
    if (models.isEmpty()) {
        EmptyNote(stringResource(R.string.picture_no_models_yet))
    } else {
        ModelRows(models) { model, isLast -> ModelRow(model.shortName(), model.blurb(), model == selected, isLast) { onSelect(model) } }
    }
}

/** What a model can do beyond answering in words, as a line under its name. Empty for a model that only answers. */
@Composable
private fun ChatModel.detail(): String = listOfNotNull(
    stringResource(R.string.model_thinks).takeIf { canThink },
    stringResource(R.string.model_sees).takeIf { canSee },
    stringResource(R.string.model_searches).takeIf { canUseTools },
).joinToString(" · ")

/** One card holding a row for each model. Each row is told whether it is the last, which has no line under it. */
@Composable
private fun <Model> ModelRows(models: List<Model>, row: @Composable (Model, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(HearthTheme.shapes.card).background(HearthTheme.colors.card)) {
        models.forEachIndexed { index, model -> row(model, index == models.lastIndex) }
    }
}

/** One model: its name, what it is good for, and a tick if it is the one chosen. */
@Composable
private fun ModelRow(title: String, detail: String, isSelected: Boolean, isLast: Boolean, onSelect: () -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = spacing.xxxl, vertical = spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, style = HearthTheme.type.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) Text(detail, color = colors.mute, style = HearthTheme.type.footnoteSnug, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The tick keeps its place when it is not shown, so every name has the same room.
        Icon(Icons.Outlined.Check, null, tint = if (isSelected) colors.pine else Color.Transparent, modifier = Modifier.size(HearthTheme.sizes.iconM))
    }
    if (!isLast) HorizontalDivider(Modifier.padding(start = spacing.xxxl), thickness = HearthTheme.strokes.hairline, color = colors.line)
}

/** What stands in for the list while there is nothing to put in it. */
@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        color = HearthTheme.colors.mute,
        style = HearthTheme.type.subhead,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = HearthTheme.spacing.screen),
    )
}

/** How hard the chosen model thinks before it replies. A menu, since there are only four levels. */
@Composable
private fun EffortRow(level: ThinkingLevel, onSelect: (ThinkingLevel) -> Unit) {
    val colors = HearthTheme.colors
    val spacing = HearthTheme.spacing
    var isOpen by remember { mutableStateOf(false) }
    val levelTitle = level.title()
    Box(Modifier.fillMaxWidth().padding(start = spacing.xxxl, end = spacing.xxxl, top = spacing.m, bottom = spacing.xxxl)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(HearthTheme.shapes.card)
                .background(colors.card)
                .clickable(role = Role.DropdownList) { isOpen = true }
                .semantics(mergeDescendants = true) { stateDescription = levelTitle }
                .padding(horizontal = spacing.xxxl, vertical = spacing.xxl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Text(stringResource(R.string.model_effort), color = colors.ink, style = HearthTheme.type.subhead, modifier = Modifier.weight(1f))
            Text(levelTitle, color = colors.mute, style = HearthTheme.type.subhead)
            Icon(Icons.Outlined.UnfoldMore, null, tint = colors.mute, modifier = Modifier.size(HearthTheme.sizes.iconS))
        }
        // The menu opens under the level it changes, at the trailing edge.
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
                ThinkingLevel.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.title()) },
                        trailingIcon = { if (option == level) Icon(Icons.Outlined.Check, null, tint = colors.pine, modifier = Modifier.size(HearthTheme.sizes.iconS)) },
                        onClick = {
                            isOpen = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}
