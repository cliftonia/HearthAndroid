package app.hearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthDropdownField
import app.hearth.designsystem.HearthIconButton
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.designsystem.HearthSegmentedChoice
import app.hearth.designsystem.HearthTheme
import app.hearth.shared.DiscardChangesDialog
import app.hearth.domain.TaskModels
import app.hearth.domain.TaskTime
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/**
 * Makes or changes a scheduled task: its title, what it asks, the cloud model, when it runs and whether the web is
 * searched first. Saved in the device's time zone; deleted from every device. As Android's full-screen editors do, it
 * is kept with Save in the top app bar, drawn by the settings screen; the fields carry their names inside them.
 */
@Composable
internal fun TaskEditorPage(presenter: SettingsPresenter) {
    val draft by presenter.taskDraft.collectAsStateWithLifecycle()
    val models by presenter.chatModels.collectAsStateWithLifecycle()
    val isAskingToDiscard by presenter.isAskingToDiscardTask.collectAsStateWithLifecycle()
    // Once left, the draft is let go of while the page is still sliding away; it goes on showing what it showed.
    var lastDraft by remember { mutableStateOf(draft) }
    if (draft != null) lastDraft = draft
    val current = draft ?: lastDraft ?: return
    val colors = HearthTheme.colors
    var isConfirmingDelete by remember { mutableStateOf(false) }
    if (isAskingToDiscard) DiscardChangesDialog(onKeepEditing = presenter::keepEditingTaskAction, onDiscard = presenter::discardTaskAction)
    HearthCard {
        OutlinedTextField(
            value = current.title,
            onValueChange = { text -> presenter.taskDraftAction { it.copy(title = text) } },
            label = { Text(stringResource(R.string.task_title)) },
            placeholder = { Text(stringResource(R.string.task_title_placeholder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = current.prompt,
            onValueChange = { text -> presenter.taskDraftAction { it.copy(prompt = text) } },
            label = { Text(stringResource(R.string.task_prompt)) },
            placeholder = { Text(stringResource(R.string.task_prompt_placeholder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
        ModelField(current.model, TaskModels.choices(models)) { model -> presenter.taskDraftAction { it.copy(model = model) } }
    }
    ScheduleCard(current, presenter)
    HearthCard {
        HearthSwitchRow(
            stringResource(R.string.task_web_search),
            current.usesWebSearch,
            detail = stringResource(R.string.task_web_search_detail),
        ) { isOn -> presenter.taskDraftAction { it.copy(usesWebSearch = isOn) } }
    }
    if (current.hasFailed) {
        Text(
            stringResource(R.string.task_save_failed),
            color = colors.danger,
            style = HearthTheme.type.footnoteSnug,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    // Only a task that exists can be deleted, and it is done from here, apart from Save at the top.
    if (current.isExisting) {
        TextButton(onClick = { isConfirmingDelete = true }) { Text(stringResource(R.string.task_delete), color = colors.danger) }
    }
    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text(stringResource(R.string.task_delete_question)) },
            text = { Text(stringResource(R.string.task_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingDelete = false
                    Haptics.play(HapticKind.WARNING)
                    presenter.deleteTaskAction()
                }) { Text(stringResource(R.string.common_delete), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/**
 * The model, as an exposed dropdown: Automatic, which leaves the choice to the hub, or one of the chat server's cloud
 * models. The one already chosen stays offered if the server no longer lists it. Beneath, what the choice means.
 */
@Composable
private fun ModelField(chosen: String, choices: List<String>, onChoose: (String) -> Unit) {
    val offered = if (chosen in choices) choices else choices + chosen
    val note = when {
        // Only Automatic is offered when the chat server has no cloud model.
        offered.size == 1 -> R.string.task_model_none
        chosen == TaskModels.AUTOMATIC -> R.string.task_model_automatic_detail
        else -> R.string.task_model_footer
    }
    HearthDropdownField(
        label = stringResource(R.string.task_model),
        value = taskModelWords(chosen),
        supportingText = stringResource(note),
    ) { close ->
        offered.forEach { model ->
            DropdownMenuItem(
                text = { Text(taskModelWords(model), color = HearthTheme.colors.ink) },
                trailingIcon = { RadioButton(selected = model == chosen, onClick = null) },
                onClick = {
                    close()
                    onChoose(model)
                },
            )
        }
    }
}

/** How often, at what time, and on which day or date. */
@Composable
private fun ScheduleCard(draft: TaskDraft, presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    var isPickingTime by rememberSaveable { mutableStateOf(false) }
    var isPickingDate by rememberSaveable { mutableStateOf(false) }
    HearthCard(stringResource(R.string.task_repeat), stringResource(R.string.task_zone, presenter.taskZone().id)) {
        val kinds = listOf(
            TaskRepeatKind.DAILY to R.string.task_repeat_daily,
            TaskRepeatKind.WEEKLY to R.string.task_repeat_weekly,
            TaskRepeatKind.ONCE to R.string.task_repeat_once,
        )
        HearthSegmentedChoice(kinds.map { (kind, label) -> kind to stringResource(label) }, draft.repeat) { kind ->
            presenter.taskDraftAction { it.copy(repeat = kind) }
        }
        if (draft.repeat == TaskRepeatKind.WEEKLY) {
            Text(stringResource(R.string.task_weekday), color = colors.mute, style = HearthTheme.type.footnoteSnug)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.s)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = draft.weekday == day.value,
                        onClick = { presenter.taskDraftAction { it.copy(weekday = day.value) } },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                        modifier = Modifier.semantics { contentDescription = day.getDisplayName(TextStyle.FULL, locale) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.m)) {
            val time = stringResource(R.string.task_time, rememberTaskTimeFormat().time(draft.time))
            OutlinedButton(onClick = { isPickingTime = true }) { Text(time) }
            if (draft.repeat == TaskRepeatKind.ONCE) {
                val dateText = draft.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
                val date = stringResource(R.string.task_date, dateText)
                OutlinedButton(onClick = { isPickingDate = true }) { Text(date) }
            }
        }
        Text(taskScheduleWords(draft.schedule(presenter.taskZone())), color = colors.ink, style = HearthTheme.type.subhead)
        if (presenter.isTaskInPast(draft)) {
            Text(stringResource(R.string.task_in_past), color = colors.danger, style = HearthTheme.type.footnoteSnug)
        }
    }
    if (isPickingTime) {
        TimeDialog(draft.time, onDismiss = { isPickingTime = false }) { time ->
            isPickingTime = false
            presenter.taskDraftAction { it.copy(time = time) }
        }
    }
    if (isPickingDate) {
        DateDialog(draft.date, onDismiss = { isPickingDate = false }) { date ->
            isPickingDate = false
            presenter.taskDraftAction { it.copy(date = date) }
        }
    }
}

/**
 * Material's time picker: the clock face, or two fields to type the hour and minute into, the button at its foot
 * changing one for the other. Its clock is the phone's own, 12- or 24-hour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(time: TaskTime, onDismiss: () -> Unit, onChoose: (TaskTime) -> Unit) {
    val context = LocalContext.current
    val uses24Hour = remember(context) { usesTwentyFourHourClock(context) }
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = uses24Hour)
    var isTyping by rememberSaveable { mutableStateOf(false) }
    val spacing = HearthTheme.spacing
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = AlertDialogDefaults.shape, color = AlertDialogDefaults.containerColor, tonalElevation = AlertDialogDefaults.TonalElevation) {
            Column(Modifier.padding(spacing.screen), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (isTyping) R.string.task_time_enter else R.string.task_time_select),
                    color = HearthTheme.colors.mute,
                    style = HearthTheme.type.footnote,
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.gapM).semantics { heading() },
                )
                if (isTyping) TimeInput(state) else TimePicker(state)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val switchLabel = stringResource(if (isTyping) R.string.task_time_use_clock else R.string.task_time_use_keyboard)
                    HearthIconButton(if (isTyping) Icons.Outlined.Schedule else Icons.Outlined.Keyboard, switchLabel, tint = HearthTheme.colors.mute) {
                        isTyping = !isTyping
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
                    TextButton(onClick = { onChoose(TaskTime(state.hour, state.minute)) }) { Text(stringResource(R.string.common_ok)) }
                }
            }
        }
    }
}

/** A calendar for the day of a one-off run. The picker counts days in UTC, so the day is read back in UTC. */
@Composable
private fun DateDialog(date: LocalDate, onDismiss: () -> Unit, onChoose: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val chosen = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: date
                onChoose(chosen)
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    ) { DatePicker(state) }
}
