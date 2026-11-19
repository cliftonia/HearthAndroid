package app.hearth.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthDropdownField
import app.hearth.designsystem.HearthSheetHeader
import app.hearth.designsystem.HearthTheme
import app.hearth.designsystem.isLargeFont
import app.hearth.domain.ChatModel
import app.hearth.domain.EffortChoiceKind
import app.hearth.domain.ImageModelKind
import app.hearth.domain.ServerSettings
import app.hearth.shared.blurb
import app.hearth.shared.shortName
import kotlinx.coroutines.launch

/**
 * The sheet opened from the composer's model chip: the server's models to choose from, and the effort that goes
 * into a reply: how hard the chosen model thinks and, where there is a search address, whether the next messages
 * are researched. While pictures are being made it lists the picture models instead, and nothing else.
 * A bottom sheet as Android draws one: a handle, the title at the leading edge, a plain list of radio buttons, and no
 * button to close it, which the handle, a tap beside it and the back button do.
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
        HearthSheetHeader(stringResource(if (isPictureMode) R.string.model_picture_sheet_title else R.string.model_sheet_title))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = spacing.m)) {
            if (isPictureMode) {
                PictureModels(viewModel.pictureModels, viewModel.selectedPictureModel(settings)) { model ->
                    Haptics.play(HapticKind.SELECTION)
                    presenter.selectPictureModelAction(model)
                    close()
                }
            } else {
                ChatModels(models, viewModel.selectedModel) { model ->
                    Haptics.play(HapticKind.SELECTION)
                    presenter.selectModelAction(model)
                    close()
                }
            }
        }
        // The effort stays in view below the list, however many models the server has. It is left out only where there
        // is nothing to choose: a model that does not think, with no search address to research through.
        val canResearch = settings.searchUrl != null
        if (!isPictureMode && (viewModel.selectedModel?.canThink == true || canResearch)) {
            EffortField(viewModel, settings, canResearch, presenter::selectEffortAction)
        }
    }
}

/** The models on the server, then those in the cloud, each group under its heading. */
@Composable
private fun ChatModels(models: List<ChatModel>, selected: ChatModel?, onSelect: (ChatModel) -> Unit) {
    if (models.isEmpty()) EmptyNote(stringResource(R.string.chat_no_model))
    listOf(R.string.chat_local_models to models.filterNot { it.isCloud }, R.string.chat_cloud_models to models.filter { it.isCloud })
        .filter { it.second.isNotEmpty() }
        .forEach { (title, group) ->
            Text(
                stringResource(title),
                color = HearthTheme.colors.pine,
                style = HearthTheme.type.footnoteStrong,
                modifier = Modifier
                    .padding(start = HearthTheme.spacing.screen, end = HearthTheme.spacing.screen, top = HearthTheme.spacing.xl, bottom = HearthTheme.spacing.xs)
                    .semantics { heading() },
            )
            ModelRows(group) { model -> ModelRow(model.displayName, model.detail(), model.id == selected?.id) { onSelect(model) } }
        }
}

/** The picture models the server offers, or a word that they are still being asked for. */
@Composable
private fun PictureModels(models: List<ImageModelKind>, selected: ImageModelKind?, onSelect: (ImageModelKind) -> Unit) {
    if (models.isEmpty()) {
        EmptyNote(stringResource(R.string.picture_no_models_yet))
    } else {
        ModelRows(models) { model -> ModelRow(model.shortName(), model.blurb(), model == selected) { onSelect(model) } }
    }
}

/** What a model can do beyond answering in words, as a line under its name. Empty for a model that only answers. */
@Composable
private fun ChatModel.detail(): String = listOfNotNull(
    stringResource(R.string.model_thinks).takeIf { canThink },
    stringResource(R.string.model_sees).takeIf { canSee },
    stringResource(R.string.model_searches).takeIf { canUseTools },
).joinToString(" · ")

/** A group of rows, heard by a screen reader as one group of choices. */
@Composable
private fun <Model> ModelRows(models: List<Model>, row: @Composable (Model) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) { models.forEach { row(it) } }
}

/** One model: its name, what it is good for, and a radio button showing whether it is the one chosen. */
@Composable
private fun ModelRow(title: String, detail: String, isSelected: Boolean, onSelect: () -> Unit) {
    // Large text is given a second line before anything is cut short.
    val lines = if (isLargeFont()) 2 else 1
    ListItem(
        headlineContent = { Text(title, maxLines = lines, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (detail.isEmpty()) null else { { Text(detail, maxLines = lines, overflow = TextOverflow.Ellipsis) } },
        // The row is the control; the button only shows how it stands.
        trailingContent = { RadioButton(selected = isSelected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = HearthTheme.colors.paper, supportingColor = HearthTheme.colors.mute),
        modifier = Modifier
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = HearthTheme.spacing.m),
    )
}

/** What stands in for the list while there is nothing to put in it. */
@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        color = HearthTheme.colors.mute,
        style = HearthTheme.type.subhead,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(HearthTheme.spacing.screen),
    )
}

/**
 * The effort that goes into a reply: how hard the chosen model thinks before it, and whether it is researched. An
 * exposed dropdown, since the choices are few. The thinking levels are one choice, and the level saved in the
 * settings is the one shown as chosen. Under them, past a line and only where research [canResearch], is deep
 * research, ticked while it is on: it is the plus button's Research switch again, so choosing it changes no level
 * and choosing a level leaves it as it was. While research is on the field says so before the level, in the brand
 * colour.
 */
@Composable
private fun EffortField(
    viewModel: ChatViewModel,
    settings: ServerSettings,
    canResearch: Boolean,
    onSelect: (EffortChoiceKind) -> Unit,
) {
    val spacing = HearthTheme.spacing
    val canThink = viewModel.selectedModel?.canThink == true
    // One choice at a time: with research on, no level is marked, and choosing a level switches research off.
    val chosen: EffortChoiceKind = if (viewModel.isResearchChosen(settings)) EffortChoiceKind.Research else EffortChoiceKind.Thinking(settings.thinkingLevel)
    val isResearching = chosen == EffortChoiceKind.Research
    HearthDropdownField(
        label = stringResource(R.string.model_effort),
        value = chosen.title(canThink),
        modifier = Modifier.fillMaxWidth().padding(start = spacing.screen, end = spacing.screen, top = spacing.m, bottom = spacing.xxxl),
        valueColor = if (isResearching) HearthTheme.colors.pine else Color.Unspecified,
    ) { close ->
        (viewModel.effortChoices(settings) + if (canResearch) listOf(EffortChoiceKind.Research) else emptyList()).forEach { option ->
            DropdownMenuItem(
                text = {
                    Column(Modifier.padding(vertical = spacing.xs)) {
                        Text(option.title(canThink))
                        if (option == EffortChoiceKind.Research) {
                            Text(stringResource(R.string.research_detail), color = HearthTheme.colors.mute, style = HearthTheme.type.footnoteSnug)
                        }
                    }
                },
                trailingIcon = { RadioButton(selected = option == chosen, onClick = null) },
                onClick = {
                    close()
                    Haptics.play(HapticKind.SELECTION)
                    onSelect(option)
                },
            )
        }
    }
}
