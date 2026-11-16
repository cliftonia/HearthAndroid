package app.hearth.settings

import android.util.Log
import app.hearth.domain.ChatModel
import app.hearth.domain.ScheduledTask
import app.hearth.domain.TaskId
import app.hearth.domain.TaskModels
import app.hearth.domain.TaskSchedule
import app.hearth.domain.TaskStore
import app.hearth.domain.TaskTime
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// The scheduled tasks page and the editor it leads to. The hub runs the tasks; this only makes, changes and deletes
// them, and they reach the hub through sync, which they therefore need.

/** What the scheduled tasks pages are built from. */
class TaskControls(
    val store: TaskStore,
    /** The device's own time zone, which a task is saved in. */
    val zone: () -> ZoneId = { ZoneId.systemDefault() },
    val now: () -> Long = System::currentTimeMillis,
)

/** How often a task in the editor repeats. */
enum class TaskRepeatKind { DAILY, WEEKLY, ONCE }

/** A task as it is being made or changed in the editor. */
data class TaskDraft(
    /** The task being changed; `null` for a new one. */
    val id: TaskId? = null,
    val title: String = "",
    val prompt: String = "",
    /** The model chosen: [TaskModels.AUTOMATIC], or a cloud model's name. */
    val model: String = TaskModels.AUTOMATIC,
    val repeat: TaskRepeatKind = TaskRepeatKind.DAILY,
    val time: TaskTime = TaskTime(7, 30),
    /** 1 for Monday to 7 for Sunday. */
    val weekday: Int = 1,
    /** The day of a one-off run. */
    val date: LocalDate = LocalDate.now(),
    val usesWebSearch: Boolean = false,
    /** Whether the last save failed, until something is changed. */
    val hasFailed: Boolean = false,
) {
    val isExisting: Boolean get() = id != null

    /** The schedule as drafted, in [zone]: a daily or weekly time is kept in it, and a one-off time is read in it. */
    fun schedule(zone: ZoneId): TaskSchedule = when (repeat) {
        TaskRepeatKind.DAILY -> TaskSchedule.Daily(time, zone.id)
        TaskRepeatKind.WEEKLY -> TaskSchedule.Weekly(time, weekday, zone.id)
        TaskRepeatKind.ONCE -> TaskSchedule.Once(date.atTime(time.hour, time.minute).atZone(zone).toEpochSecond())
    }

    /** Whether a one-off run is drafted for a moment already gone. */
    fun isInPast(zone: ZoneId, nowMillis: Long): Boolean =
        repeat == TaskRepeatKind.ONCE && (schedule(zone) as TaskSchedule.Once).atSeconds * 1000 <= nowMillis

    /** Whether there is enough to save: a title, a question, a model a task may use, and a time still to come. */
    fun canSave(zone: ZoneId, nowMillis: Long): Boolean =
        title.isNotBlank() && prompt.isNotBlank() && TaskModels.isAllowed(model) && !isInPast(zone, nowMillis)

    companion object {
        /** A draft of a saved task. A one-off time is shown in [zone]; a repeating task offers [defaultDate] if made one-off. */
        fun of(task: ScheduledTask, zone: ZoneId, defaultDate: LocalDate): TaskDraft {
            val base = TaskDraft(task.id, task.title, task.prompt, task.model, date = defaultDate, usesWebSearch = task.usesWebSearch)
            return when (val schedule = task.schedule) {
                is TaskSchedule.Daily -> base.copy(repeat = TaskRepeatKind.DAILY, time = schedule.time)
                is TaskSchedule.Weekly -> base.copy(repeat = TaskRepeatKind.WEEKLY, time = schedule.time, weekday = schedule.weekday)
                is TaskSchedule.Once -> {
                    val moment = Instant.ofEpochSecond(schedule.atSeconds).atZone(zone)
                    base.copy(repeat = TaskRepeatKind.ONCE, time = TaskTime(moment.hour, moment.minute), date = moment.toLocalDate())
                }
            }
        }
    }
}

private val SettingsPresenter.taskControls: TaskControls get() = dependencies.tasks

/** Every scheduled task, oldest first. */
val SettingsPresenter.tasks: StateFlow<List<ScheduledTask>> get() = taskControls.store.tasks

/** The task in the editor; `null` while none is. */
val SettingsPresenter.taskDraft: StateFlow<TaskDraft?> get() = taskDraftState

/** Every model the chat server offers; the editor offers only the cloud ones (`TaskModels.cloud`), which the hub runs tasks with. */
val SettingsPresenter.chatModels: StateFlow<List<ChatModel>> get() = dependencies.modelStore.models

/** The time zone a task is saved in: the device's. */
fun SettingsPresenter.taskZone(): ZoneId = taskControls.zone()

/** Whether the drafted task can be saved now. */
fun SettingsPresenter.canSaveTask(draft: TaskDraft): Boolean = draft.canSave(taskControls.zone(), taskControls.now())

/** Whether the drafted one-off time has passed. */
fun SettingsPresenter.isTaskInPast(draft: TaskDraft): Boolean = draft.isInPast(taskControls.zone(), taskControls.now())

/** Opens the scheduled tasks page. */
fun SettingsPresenter.tasksAction() {
    taskDraftState.value = null
    pageState.value = SettingsPageKind.TASKS
}

/** Opens the editor on a new task, leaving the model to the hub and with a one-off date of tomorrow. Needs sync. */
fun SettingsPresenter.addTaskAction() {
    if (!syncStore.settings.value.canSync) return
    taskDraftState.value = TaskDraft(date = tomorrow(), model = TaskModels.AUTOMATIC)
    pageState.value = SettingsPageKind.TASK
}

/** Opens the editor on a saved task. */
fun SettingsPresenter.editTaskAction(id: TaskId) {
    val task = taskControls.store.task(id) ?: return
    taskDraftState.value = TaskDraft.of(task, taskControls.zone(), tomorrow())
    pageState.value = SettingsPageKind.TASK
}

/** Changes the draft, as each field of the editor is changed. */
fun SettingsPresenter.taskDraftAction(change: (TaskDraft) -> TaskDraft) {
    val draft = taskDraftState.value ?: return
    taskDraftState.value = change(draft).copy(hasFailed = false)
}

/** Turns a task on or off from the list. */
fun SettingsPresenter.taskEnabledAction(id: TaskId, isEnabled: Boolean) {
    scope.launch {
        try {
            taskControls.store.setEnabled(id, isEnabled)
        } catch (failure: IOException) {
            Log.e(SettingsPresenter.TAG, "Switching a task failed: ${failure.javaClass.simpleName}")
        }
    }
}

/**
 * Keeps the drafted task, in the device's time zone, and goes back to the list. A task already saved keeps when it
 * was made, whether it is on, and what the hub said of its last run.
 */
fun SettingsPresenter.saveTaskAction() {
    val draft = taskDraftState.value ?: return
    if (!canSaveTask(draft)) return
    val zone = taskControls.zone()
    val existing = draft.id?.let(taskControls.store::task)
    val base = existing ?: ScheduledTask(createdAt = taskControls.now())
    val task = base.copy(
        title = draft.title.trim(),
        prompt = draft.prompt.trim(),
        model = draft.model,
        schedule = draft.schedule(zone),
        usesWebSearch = draft.usesWebSearch,
    )
    scope.launch {
        try {
            taskControls.store.save(task)
            taskDraftState.value = null
            pageState.value = SettingsPageKind.TASKS
        } catch (failure: IOException) {
            Log.e(SettingsPresenter.TAG, "Saving a task failed: ${failure.javaClass.simpleName}")
            taskDraftState.value = taskDraftState.value?.copy(hasFailed = true)
        }
    }
}

/** Deletes the task in the editor, on every device, and goes back to the list. Chats it filed are kept. */
fun SettingsPresenter.deleteTaskAction() {
    val id = taskDraftState.value?.id ?: return
    scope.launch {
        try {
            taskControls.store.delete(id)
            taskDraftState.value = null
            pageState.value = SettingsPageKind.TASKS
        } catch (failure: IOException) {
            Log.e(SettingsPresenter.TAG, "Deleting a task failed: ${failure.javaClass.simpleName}")
            taskDraftState.value = taskDraftState.value?.copy(hasFailed = true)
        }
    }
}

/** Tomorrow in the device's time zone, the day a one-off run starts on. */
private fun SettingsPresenter.tomorrow(): LocalDate = Instant.ofEpochMilli(taskControls.now()).atZone(taskControls.zone()).toLocalDate().plusDays(1)
