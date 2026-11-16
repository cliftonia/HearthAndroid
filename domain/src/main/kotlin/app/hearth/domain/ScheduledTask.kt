package app.hearth.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A question the user's Hearth Hub asks a cloud model on a schedule, filing each answer as a new chat in the user's
 * account, which reaches every device through sync. The app only makes, changes and deletes tasks, and shows how the
 * last run went; it never runs one itself.
 */
@Serializable
data class ScheduledTask(
    val id: TaskId = TaskId.new(),
    val title: String = "",
    /** What the model is asked each time. */
    val prompt: String = "",
    /** The cloud model the hub asks, by the chat server's name for it. */
    val model: String = "",
    val schedule: TaskSchedule = TaskSchedule.Daily(TaskTime(7, 30), "UTC"),
    val isEnabled: Boolean = true,
    /** Whether the hub searches the web before it asks the model. */
    val usesWebSearch: Boolean = false,
    /** When the hub last ran it, in seconds since 1970, as the hub wrote it; `null` before the first run. */
    val lastRunAt: Double? = null,
    /** Whether that run failed. Written by the hub. */
    val lastRunFailed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** When anything about it last changed, here or on the hub; what sync compares. */
    val updatedAt: Long = createdAt,
) {
    /** How the last run went, for the list. */
    val lastRun: TaskRunKind
        get() = when {
            lastRunAt == null -> TaskRunKind.NotYet
            lastRunFailed -> TaskRunKind.Failed((lastRunAt * 1000).toLong())
            else -> TaskRunKind.Succeeded((lastRunAt * 1000).toLong())
        }
}

/** How a task's last run went. */
sealed interface TaskRunKind {
    data object NotYet : TaskRunKind

    data class Succeeded(val atMillis: Long) : TaskRunKind

    data class Failed(val atMillis: Long) : TaskRunKind
}

/** A time of day on a 24-hour clock. */
@Serializable
data class TaskTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59) { "Not a time of day" }
    }

    /** As the wire writes it: `07:30`. */
    val wire: String get() = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    /** As the user reads it: `7:30`, `18:00`. */
    val text: String get() = String.format(Locale.ROOT, "%d:%02d", hour, minute)

    companion object {
        /** A time read from `HH:MM` (one or two digits for the hour); `null` if it is not one. */
        fun parse(text: String): TaskTime? {
            val parts = text.trim().split(':')
            if (parts.size != 2 || parts.any { part -> part.isEmpty() || part.length > 2 || part.any { it !in '0'..'9' } }) return null
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()
            return if (hour in 0..23 && minute in 0..59) TaskTime(hour, minute) else null
        }
    }
}

/** When a task runs. Daily and weekly times are in the time zone the task was saved in, by the device's own zone. */
@Serializable
sealed interface TaskSchedule {
    /** Every day at [time]. */
    @Serializable
    @SerialName("daily")
    data class Daily(val time: TaskTime, val timeZone: String) : TaskSchedule

    /** Every week on [weekday] (1 for Monday to 7 for Sunday) at [time]. */
    @Serializable
    @SerialName("weekly")
    data class Weekly(val time: TaskTime, val weekday: Int, val timeZone: String) : TaskSchedule {
        init {
            require(weekday in 1..7) { "Not a weekday" }
        }
    }

    /** Once, at [atSeconds] since 1970. */
    @Serializable
    @SerialName("once")
    data class Once(val atSeconds: Long) : TaskSchedule

    /**
     * What the schedule says, in parts for the app to put in a sentence: the time as `7:30`, the weekday's name and
     * a one-off date as `20 Nov`, named in [locale]. A one-off time is shown in [zone], the device's own.
     */
    fun summary(zone: ZoneId, locale: Locale): TaskScheduleSummary = when (this) {
        is Daily -> TaskScheduleSummary.Daily(time.text)
        is Weekly -> TaskScheduleSummary.Weekly(DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, locale), time.text)
        is Once -> {
            val moment = Instant.ofEpochSecond(atSeconds).atZone(zone)
            TaskScheduleSummary.Once(
                moment.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                TaskTime(moment.hour, moment.minute).text,
            )
        }
    }
}

/** A schedule's parts, ready to be put in words: "Every day at 7:30", "Every Friday at 18:00", "Once, 20 Nov at 9:00". */
sealed interface TaskScheduleSummary {
    data class Daily(val time: String) : TaskScheduleSummary

    data class Weekly(val weekday: String, val time: String) : TaskScheduleSummary

    data class Once(val date: String, val time: String) : TaskScheduleSummary

    /** The sentence, from the three templates the app holds in its own language. */
    fun words(daily: String, weekly: String, once: String): String = when (this) {
        is Daily -> daily.format(time)
        is Weekly -> weekly.format(weekday, time)
        is Once -> once.format(date, time)
    }
}

/** Which models a task may use: the hub's own choice, or a cloud model named outright. */
object TaskModels {
    /** The model a task names to leave the choice to the hub, which tries its cloud models and then the user's own. */
    const val AUTOMATIC = "auto"

    /** Whether a task may name this model: [AUTOMATIC], or a cloud model. */
    fun isAllowed(name: String): Boolean = name == AUTOMATIC || isCloud(name)

    /** What the editor offers, in order: [AUTOMATIC] first, then the cloud models among those offered. */
    fun choices(models: List<ChatModel>): List<String> = listOf(AUTOMATIC) + cloud(models).map { it.id.raw }

    /** Whether a model's name marks it as a cloud model: it ends in `:cloud` or `-cloud`, in any case. */
    fun isCloud(name: String): Boolean = name.lowercase(Locale.ROOT).let { it.endsWith(":cloud") || it.endsWith("-cloud") }

    /** The cloud models among those offered, in their order. */
    fun cloud(models: List<ChatModel>): List<ChatModel> = models.filter { isCloud(it.id.raw) }
}
