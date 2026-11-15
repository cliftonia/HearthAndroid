package app.hearth.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.hearth.R
import app.hearth.domain.Conversation
import app.hearth.domain.DayPartKind
import app.hearth.domain.Greeting
import app.hearth.domain.GreetingKind
import app.hearth.domain.GreetingPlanner
import app.hearth.domain.Memory
import app.hearth.domain.ServerSettings
import app.hearth.domain.UserNameFinder
import app.hearth.domain.WeekdayKind
import java.util.Calendar
import kotlinx.coroutines.flow.StateFlow

// The line an empty chat opens with.

/** What is remembered about the user, among which the greeting may find their name. */
val ChatPresenter.memories: StateFlow<List<Memory>>
    get() = dependencies.memoryStore.memories

/**
 * A short greeting, by name when the name is known. It is steady for one chat and varies between chats.
 *
 * The name is the nickname in the user's profile, failing that the first word of their full name, and failing
 * that a name found in what is remembered about them. A private chat uses no memories, so there it comes from
 * the profile alone.
 */
internal fun chatGreeting(conversation: Conversation, settings: ServerSettings, memories: List<Memory>, now: Calendar = Calendar.getInstance()): Greeting {
    val remembered = if (settings.allowsMemory(conversation)) UserNameFinder().name(memories) else null
    return GreetingPlanner().greeting(
        name = settings.profile.preferredName ?: remembered,
        hour = now.get(Calendar.HOUR_OF_DAY),
        weekday = WeekdayKind.fromCalendarWeekday(now.get(Calendar.DAY_OF_WEEK)),
        seed = conversation.id.raw.fold(0) { seed, character -> (seed * 31 + character.code) and 0xFFFF },
    )
}

/** The greeting in the user's language. */
@Composable
internal fun Greeting.words(): String {
    val name = name
    if (name == null) {
        return stringResource(
            when (val kind = kind) {
                GreetingKind.LetsTalk, GreetingKind.GoodToSeeYou -> R.string.greet_lets_talk
                GreetingKind.OnYourMind -> R.string.greet_on_your_mind
                is GreetingKind.DayPart -> when (kind.part) {
                    DayPartKind.MORNING -> R.string.greet_morning
                    DayPartKind.AFTERNOON -> R.string.greet_afternoon
                    DayPartKind.EVENING -> R.string.greet_evening
                    DayPartKind.NIGHT -> R.string.greet_night
                }
                is GreetingKind.Weekday -> when (kind.day) {
                    WeekdayKind.SUNDAY -> R.string.greet_sunday
                    WeekdayKind.MONDAY -> R.string.greet_monday
                    WeekdayKind.TUESDAY -> R.string.greet_tuesday
                    WeekdayKind.WEDNESDAY -> R.string.greet_wednesday
                    WeekdayKind.THURSDAY -> R.string.greet_thursday
                    WeekdayKind.FRIDAY -> R.string.greet_friday
                    WeekdayKind.SATURDAY -> R.string.greet_saturday
                }
            },
        )
    }
    return stringResource(
        when (val kind = kind) {
            GreetingKind.LetsTalk -> R.string.greet_lets_talk_named
            GreetingKind.GoodToSeeYou -> R.string.greet_good_to_see_you_named
            GreetingKind.OnYourMind -> R.string.greet_on_your_mind_named
            is GreetingKind.DayPart -> when (kind.part) {
                DayPartKind.MORNING -> R.string.greet_morning_named
                DayPartKind.AFTERNOON -> R.string.greet_afternoon_named
                DayPartKind.EVENING -> R.string.greet_evening_named
                DayPartKind.NIGHT -> R.string.greet_night_named
            }
            is GreetingKind.Weekday -> when (kind.day) {
                WeekdayKind.SUNDAY -> R.string.greet_sunday_named
                WeekdayKind.MONDAY -> R.string.greet_monday_named
                WeekdayKind.TUESDAY -> R.string.greet_tuesday_named
                WeekdayKind.WEDNESDAY -> R.string.greet_wednesday_named
                WeekdayKind.THURSDAY -> R.string.greet_thursday_named
                WeekdayKind.FRIDAY -> R.string.greet_friday_named
                WeekdayKind.SATURDAY -> R.string.greet_saturday_named
            }
        },
        name,
    )
}
