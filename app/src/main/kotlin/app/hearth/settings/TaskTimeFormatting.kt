package app.hearth.settings

import android.content.Context
import android.icu.text.DateFormat
import android.icu.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.hearth.domain.TaskTimeFormat
import java.util.Date
import java.util.Locale

/** A day in milliseconds, to turn a date into the moment it starts. */
private const val DAY_MILLIS = 86_400_000L

/** A minute in milliseconds, to turn a time of day into a moment on the first day of 1970. */
private const val MINUTE_MILLIS = 60_000L

/**
 * Whether the phone shows the time on a 24-hour clock, as set in its settings (or, where it was never set, as its
 * language does). Should the phone fail to say, the 24-hour clock is taken, which no one misreads.
 */
fun usesTwentyFourHourClock(context: Context): Boolean =
    runCatching { android.text.format.DateFormat.is24HourFormat(context) }.getOrDefault(true)

/**
 * How the tasks' times and dates are written, following the phone: its 12- or 24-hour clock, read again each time
 * the app comes back, since it may have been changed meanwhile, and its language's own pattern for a time and for a
 * day and a month.
 */
@Composable
internal fun rememberTaskTimeFormat(): TaskTimeFormat {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var uses24Hour by remember(context) { mutableStateOf(usesTwentyFourHourClock(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { uses24Hour = usesTwentyFourHourClock(context) }
    return remember(locale, uses24Hour) { taskTimeFormat(locale, uses24Hour) }
}

/**
 * A format from the language's own patterns, by skeleton: the hour and minute on the clock asked for, and the day and
 * the short month. Should the phone fail to make one, the fixed patterns stand in, so writing a time never fails.
 */
private fun taskTimeFormat(locale: Locale, uses24Hour: Boolean): TaskTimeFormat {
    val fallback = TaskTimeFormat.fixed(uses24Hour, locale)
    // Each is written as a moment in UTC, so that no change of the clocks moves the time or the day shown.
    fun format(skeleton: String): DateFormat? = runCatching {
        DateFormat.getInstanceForSkeleton(skeleton, locale).apply { timeZone = TimeZone.GMT_ZONE }
    }.getOrNull()
    val clock = format(if (uses24Hour) "Hm" else "hm")
    val day = format("MMMd")
    return TaskTimeFormat(
        time = { time ->
            clock?.let { runCatching { it.format(Date((time.hour * 60L + time.minute) * MINUTE_MILLIS)) }.getOrNull() } ?: fallback.time(time)
        },
        date = { date -> day?.let { runCatching { it.format(Date(date.toEpochDay() * DAY_MILLIS)) }.getOrNull() } ?: fallback.date(date) },
    )
}
