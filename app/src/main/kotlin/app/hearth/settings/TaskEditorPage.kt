package app.hearth.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthSwitchRow
import app.hearth.designsystem.HearthTheme
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
 * searched first. Saved in the device's time zone; deleted from every device.
 */
@Composable
internal fun TaskEditorPage(presenter: SettingsPresenter) {
    val draft by presenter.taskDraft.collectAsStateWithLifecycle()
    val models by presenter.chatModels.collectAsStateWithLifecycle()
    val current = draft ?: return
    val colors = HearthTheme.colors
    var isConfirmingDelete by remember { mutableStateOf(false) }
    HearthCard(stringResource(R.string.task_title)) {
        OutlinedTextField(
            value = current.title,
            onValueChange = { text -> presenter.taskDraftAction { it.copy(title = text) } },
            placeholder = { Text(stringResource(R.string.task_title_placeholder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    HearthCard(stringResource(R.string.task_prompt)) {
        OutlinedTextField(
            value = current.prompt,
            onValueChange = { text -> presenter.taskDraftAction { it.copy(prompt = text) } },
            placeholder = { Text(stringResource(R.string.task_prompt_placeholder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            maxLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ModelCard(current.model, TaskModels.choices(models)) { model -> presenter.taskDraftAction { it.copy(model = model) } }
    ScheduleCard(current, presenter)
    HearthCard {
        HearthSwitchRow(
            stringResource(R.string.task_web_search),
            current.usesWebSearch,
            detail = stringResource(R.string.task_web_search_detail),
        ) { isOn -> presenter.taskDraftAction { it.copy(usesWebSearch = isOn) } }
    }
    HearthCard {
        Button(
            onClick = {
                Haptics.play(HapticKind.SUCCESS)
                presenter.saveTaskAction()
            },
            enabled = presenter.canSaveTask(current),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.task_save)) }
        if (current.hasFailed) {
            Text(
                stringResource(R.string.task_save_failed),
                color = colors.danger,
                style = HearthTheme.type.footnoteSnug,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (current.isExisting) {
            TextButton(onClick = { isConfirmingDelete = true }) { Text(stringResource(R.string.task_delete), color = colors.danger) }
        }
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
                }) { Text(stringResource(R.string.task_delete), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/**
 * The model: Automatic, which leaves the choice to the hub, or one of the chat server's cloud models. The one already
 * chosen stays offered if the server no longer lists it.
 */
@Composable
private fun ModelCard(chosen: String, choices: List<String>, onChoose: (String) -> Unit) {
    val colors = HearthTheme.colors
    val offered = if (chosen in choices) choices else choices + chosen
    var isMenuOpen by remember { mutableStateOf(false) }
    HearthCard(stringResource(R.string.task_model), stringResource(R.string.task_model_footer)) {
        val label = taskModelWords(chosen)
        val description = stringResource(R.string.task_model_chosen, label)
        Box {
            OutlinedButton(onClick = { isMenuOpen = true }, modifier = Modifier.semantics { contentDescription = description }) { Text(label) }
            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                offered.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(taskModelWords(model), color = colors.ink) },
                        onClick = {
                            isMenuOpen = false
                            onChoose(model)
                        },
                    )
                }
            }
        }
        if (chosen == TaskModels.AUTOMATIC) {
            Text(stringResource(R.string.task_model_automatic_detail), color = colors.mute, style = HearthTheme.type.footnoteSnug)
        }
        // Only Automatic is offered when the chat server has no cloud model.
        if (offered.size == 1) Text(stringResource(R.string.task_model_none), color = colors.mute, style = HearthTheme.type.footnoteSnug)
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
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { index, (kind, label) ->
                SegmentedButton(
                    selected = draft.repeat == kind,
                    onClick = { presenter.taskDraftAction { it.copy(repeat = kind) } },
                    shape = SegmentedButtonDefaults.itemShape(index, kinds.size),
                ) { Text(stringResource(label)) }
            }
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
            val time = stringResource(R.string.task_time, draft.time.text)
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

/** The phone's clock face for a time of day, on a 24-hour clock. */
@Composable
private fun TimeDialog(time: TaskTime, onDismiss: () -> Unit, onChoose: (TaskTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onChoose(TaskTime(state.hour, state.minute)) }) { Text(stringResource(R.string.task_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
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
            }) { Text(stringResource(R.string.task_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    ) { DatePicker(state) }
}
