package app.hearth.domain

import kotlin.math.abs

/** The part of the day, for a greeting that suits the hour. */
enum class DayPartKind {
    MORNING,
    AFTERNOON,
    EVENING,

    /** The small hours, when "good evening" and "good morning" both ring false. */
    NIGHT,
}

/** The day of the week, for a greeting that names it. */
enum class WeekdayKind {
    SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY;

    companion object {
        /** The day for a calendar's weekday number, where 1 is Sunday. A number outside 1 to 7 wraps round. */
        fun fromCalendarWeekday(number: Int): WeekdayKind = entries[Math.floorMod(number - 1, entries.size)]
    }
}

/** What an empty chat says to invite a message. */
sealed interface GreetingKind {
    data object LetsTalk : GreetingKind

    data class DayPart(val part: DayPartKind) : GreetingKind

    data object OnYourMind : GreetingKind

    /** Wishes a happy day, by the day's name. */
    data class Weekday(val day: WeekdayKind) : GreetingKind

    /** Only said to someone whose name is known. */
    data object GoodToSeeYou : GreetingKind
}

/** A greeting and, when it is known, the name of the person greeted. */
data class Greeting(val kind: GreetingKind, val name: String?)

/**
 * Chooses the short line an empty chat opens with, so that it is not the same every time.
 *
 * The choice is steady for one chat, so the line does not change while it is being looked at, and varies
 * from one new chat to the next.
 */
class GreetingPlanner {
    /**
     * @param name The user's name, if the app knows it.
     * @param hour The hour of the day, from 0 to 23.
     * @param weekday The day of the week.
     * @param seed Any number that stays the same for one chat and differs between chats.
     */
    fun greeting(name: String?, hour: Int, weekday: WeekdayKind, seed: Int): Greeting {
        val known = name?.trim()?.ifEmpty { null }
        val kinds = buildList {
            add(GreetingKind.LetsTalk)
            add(GreetingKind.DayPart(dayPart(hour)))
            add(GreetingKind.OnYourMind)
            add(GreetingKind.Weekday(weekday))
            if (known != null) add(GreetingKind.GoodToSeeYou)
        }
        return Greeting(kinds[(abs(seed.toLong()) % kinds.size).toInt()], known)
    }

    private fun dayPart(hour: Int): DayPartKind = when (hour) {
        in 5..11 -> DayPartKind.MORNING
        in 12..17 -> DayPartKind.AFTERNOON
        in 18..22 -> DayPartKind.EVENING
        else -> DayPartKind.NIGHT
    }
}

/** Looks for the user's name among what is remembered about them, for when they have not given it in their profile. */
class UserNameFinder {
    /** The first name found, from the memories in the order given; `null` when none says one. */
    fun name(memories: List<Memory>): String? {
        for (memory in memories) {
            for (pattern in PATTERNS) {
                val found = pattern.find(memory.text)?.groupValues?.get(1) ?: continue
                // Only the first name is used: a greeting by full name reads like a form letter.
                return found.split(" ").firstOrNull { it.isNotEmpty() }
            }
        }
        return null
    }

    private companion object {
        /** How a memory is likely to say the name. The name itself is one or two capitalised words. */
        val PATTERNS = listOf(
            Regex(
                "(?i:\\bmy name is|\\bname is|\\bis called|\\bis named|\\bgo(?:es)? by|\\bcall (?:me|them|him|her))" +
                    "\\s+([A-Z][\\p{L}'-]+(?:\\s[A-Z][\\p{L}'-]+)?)",
            ),
            Regex("(?i:\\bname:)\\s*([A-Z][\\p{L}'-]+)"),
        )
    }
}
