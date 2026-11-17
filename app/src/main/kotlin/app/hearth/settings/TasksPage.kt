package app.hearth.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hearth.R
import app.hearth.designsystem.HapticKind
import app.hearth.designsystem.Haptics
import app.hearth.designsystem.HearthCard
import app.hearth.designsystem.HearthTheme
import app.hearth.domain.ScheduledTask
import app.hearth.domain.TaskModels
import app.hearth.domain.TaskRunKind
import app.hearth.domain.TaskSchedule
import app.hearth.shared.ErrorDialog
import java.time.ZoneId

/**
 * The scheduled tasks: each with its schedule, how its last run went and a switch, and a way to add one. Tasks are
 * run by the hub, so without sync the page says so, leads to the sync page, and adds nothing.
 */
@Composable
internal fun TasksPage(presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val tasks by presenter.tasks.collectAsStateWithLifecycle()
    val syncSettings by presenter.syncSettings.collectAsStateWithLifecycle()
    val error by presenter.taskError.collectAsStateWithLifecycle()
    val canSync = syncSettings.canSync
    error?.let { ErrorDialog(it, presenter::dismissTaskErrorAction) }
    if (!canSync) {
        HearthCard {
            Text(stringResource(R.string.tasks_needs_sync), color = colors.ink, style = HearthTheme.type.footnoteSnug)
            OutlinedButton(onClick = presenter::syncAction) { Text(stringResource(R.string.tasks_open_sync)) }
        }
    }
    HearthCard(footer = stringResource(R.string.tasks_footer)) {
        if (tasks.isEmpty()) Text(stringResource(R.string.tasks_empty), color = colors.mute, style = HearthTheme.type.subhead)
        tasks.forEachIndexed { index, task ->
            if (index > 0) HorizontalDivider(color = colors.line)
            TaskRow(task, presenter)
        }
        OutlinedButton(onClick = presenter::addTaskAction, enabled = canSync) { Text(stringResource(R.string.tasks_add)) }
    }
}

/** One task: tap it to change it; its switch turns it on or off. */
@Composable
private fun TaskRow(task: ScheduledTask, presenter: SettingsPresenter) {
    val colors = HearthTheme.colors
    val title = task.title.ifBlank { stringResource(R.string.tasks_untitled) }
    val schedule = taskScheduleWords(task.schedule)
    val lastRun = taskRunWords(task.lastRun)
    val isFailed = task.lastRun is TaskRunKind.Failed
    val edit = stringResource(R.string.tasks_edit, title)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HearthTheme.spacing.xl)) {
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = edit) { presenter.editTaskAction(task.id) },
        ) {
            Text(title, color = if (task.isEnabled) colors.ink else colors.mute, style = HearthTheme.type.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(schedule, color = colors.mute, style = HearthTheme.type.footnoteSnug)
            Text(taskModelWords(task.model), color = colors.mute, style = HearthTheme.type.captionSnug, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lastRun, color = if (isFailed) colors.danger else colors.mute, style = HearthTheme.type.captionSnug)
        }
        val switchLabel = stringResource(R.string.tasks_switch, title)
        Switch(
            checked = task.isEnabled,
            onCheckedChange = { isOn ->
                Haptics.play(HapticKind.LIGHT)
                presenter.taskEnabledAction(task.id, isOn)
            },
            modifier = Modifier.semantics { contentDescription = switchLabel },
        )
    }
}

/** A schedule in words: "Every day at 7:30", "Every Friday at 18:00", "Once, 20 Nov at 9:00". */
@Composable
internal fun taskScheduleWords(schedule: TaskSchedule, zone: ZoneId = ZoneId.systemDefault()): String {
    val locale = LocalConfiguration.current.locales[0]
    return schedule.summary(zone, locale).words(
        stringResource(R.string.task_schedule_daily),
        stringResource(R.string.task_schedule_weekly),
        stringResource(R.string.task_schedule_once),
    )
}

/** How the last run went, in words. */
@Composable
private fun taskRunWords(run: TaskRunKind): String {
    val context = LocalContext.current
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
    return when (run) {
        TaskRunKind.NotYet -> stringResource(R.string.tasks_not_run)
        is TaskRunKind.Succeeded -> stringResource(R.string.tasks_last_run, DateUtils.formatDateTime(context, run.atMillis, flags))
        is TaskRunKind.Failed -> stringResource(R.string.tasks_last_run_failed, DateUtils.formatDateTime(context, run.atMillis, flags))
    }
}


/** A task's model as the user reads it: "Automatic" for the hub's own choice, otherwise the model's name. */
@Composable
internal fun taskModelWords(model: String): String =
    if (model == TaskModels.AUTOMATIC) stringResource(R.string.task_model_automatic) else model
