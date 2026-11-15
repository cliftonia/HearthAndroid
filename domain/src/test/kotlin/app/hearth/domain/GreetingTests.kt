package app.hearth.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/** How the line on an empty chat is chosen, and how the user's name is found among the memories. */
class GreetingTests {
    private val planner = GreetingPlanner()

    private fun memory(text: String) = Memory(MemoryId("m1"), text, isFromUser = false, createdAt = 0, updatedAt = 0)

    @Test
    fun `one chat always gets the same greeting, and different chats get different ones`() {
        assertEquals(planner.greeting("Sam", 9, WeekdayKind.SUNDAY, seed = 7), planner.greeting("Sam", 9, WeekdayKind.SUNDAY, seed = 7))
        val kinds = (0 until 10).map { planner.greeting("Sam", 9, WeekdayKind.SUNDAY, seed = it).kind }.toSet()
        val expected = setOf(
            GreetingKind.LetsTalk,
            GreetingKind.DayPart(DayPartKind.MORNING),
            GreetingKind.OnYourMind,
            GreetingKind.Weekday(WeekdayKind.SUNDAY),
            GreetingKind.GoodToSeeYou,
        )
        assertEquals(expected, kinds)
    }

    @Test
    fun `without a name nobody is told it is good to see them`() {
        val greetings = (0 until 12).map { planner.greeting("  ", 20, WeekdayKind.FRIDAY, seed = it) }
        assertTrue(greetings.all { it.name == null })
        val expected = setOf(
            GreetingKind.LetsTalk,
            GreetingKind.DayPart(DayPartKind.EVENING),
            GreetingKind.OnYourMind,
            GreetingKind.Weekday(WeekdayKind.FRIDAY),
        )
        assertEquals(expected, greetings.map { it.kind }.toSet())
    }

    @Test
    fun `the hour decides the part of the day`() {
        val cases = listOf(
            5 to DayPartKind.MORNING,
            6 to DayPartKind.MORNING,
            11 to DayPartKind.MORNING,
            12 to DayPartKind.AFTERNOON,
            13 to DayPartKind.AFTERNOON,
            17 to DayPartKind.AFTERNOON,
            18 to DayPartKind.EVENING,
            19 to DayPartKind.EVENING,
            22 to DayPartKind.EVENING,
            23 to DayPartKind.NIGHT,
            2 to DayPartKind.NIGHT,
            4 to DayPartKind.NIGHT,
        )
        cases.forEach { (hour, part) ->
            assertEquals(GreetingKind.DayPart(part), planner.greeting(null, hour, WeekdayKind.MONDAY, seed = 1).kind, "at $hour")
        }
    }

    @Test
    fun `the day of the week is named, with and without the user's name`() {
        WeekdayKind.entries.forEach { day ->
            // The day's greeting is the fourth in the rotation.
            assertEquals(Greeting(GreetingKind.Weekday(day), "Sam"), planner.greeting("Sam", 9, day, seed = 3))
            assertEquals(Greeting(GreetingKind.Weekday(day), null), planner.greeting(null, 9, day, seed = 3))
        }
    }

    @Test
    fun `a calendar's weekday number is read with Sunday first`() {
        val cases = listOf(1 to WeekdayKind.SUNDAY, 2 to WeekdayKind.MONDAY, 7 to WeekdayKind.SATURDAY, 8 to WeekdayKind.SUNDAY, 0 to WeekdayKind.SATURDAY)
        cases.forEach { (number, day) -> assertEquals(day, WeekdayKind.fromCalendarWeekday(number), "for $number") }
    }

    @Test
    fun `a seed below zero still chooses a greeting`() {
        assertEquals(planner.greeting("Sam", 9, WeekdayKind.SUNDAY, seed = 3), planner.greeting("Sam", 9, WeekdayKind.SUNDAY, seed = -3))
        assertEquals(GreetingKind.LetsTalk, planner.greeting(null, 9, WeekdayKind.SUNDAY, seed = Int.MIN_VALUE).kind)
    }

    @Test
    fun `a name is trimmed before it is used`() {
        assertEquals("Sam", planner.greeting("  Sam \n", 9, WeekdayKind.SUNDAY, seed = 0).name)
    }

    @Test
    fun `a name said in a memory is found, first name only`() {
        val cases = listOf(
            "The user's name is Clifton." to "Clifton",
            "My name is Mary Jane Watson" to "Mary",
            "User is called Sam and lives in Brisbane" to "Sam",
            "Name: Priya" to "Priya",
            "Likes to go by Alex at work" to "Alex",
        )
        cases.forEach { (text, name) ->
            assertEquals(name, UserNameFinder().name(listOf(memory("Prefers tea"), memory(text))), "in \"$text\"")
        }
    }

    @Test
    fun `memories that say no name give none`() {
        val finder = UserNameFinder()
        assertNull(finder.name(emptyList()))
        assertNull(finder.name(listOf(memory("Lives in Brisbane"), memory("The dog's name is rex"))))
    }
}
