package app.hearth

import android.app.Application
import android.content.Intent
import android.util.Log
import app.hearth.domain.ChatModel
import app.hearth.domain.Conversation
import app.hearth.domain.MemoryRequest
import app.hearth.domain.Message
import app.hearth.domain.MessageRole
import app.hearth.domain.ModelId
import app.hearth.domain.Project
import app.hearth.domain.ScheduledTask
import app.hearth.domain.SearchSource
import app.hearth.domain.SyncSettings
import app.hearth.domain.TaskModels
import app.hearth.domain.TaskSchedule
import app.hearth.domain.TaskTime
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// Sample data for the README's pictures. This is the debug build's version, and is in no build that ships.
//
//     adb shell am start -n <application id>/app.hearth.MainActivity --ez hearth_screenshots true
//
// fills the app with a few tidy example chats, a project, memories, scheduled tasks, a canvas piece and a research
// report, a stand-in list of models, placeholder server addresses and a person called Sam. From then on, until the
// app's storage is cleared (`adb shell pm clear`) or it is uninstalled (the in-app Erase leaves it), the model
// list is the stand-in one, a sync touches no hub, and the
// window may be captured while the lock is on. Nothing reaches a real server on the sample data's behalf.

/** The intent extra that asks for the sample data. */
private const val SHOWCASE_EXTRA = "hearth_screenshots"

/** The file, among the app's own, whose presence keeps the app on its sample data. */
private const val SHOWCASE_FILE = "showcase"

private const val TAG = "Showcase"
private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

private fun isOn(application: Application): Boolean = File(application.filesDir, SHOWCASE_FILE).isFile

/** The models of the sample data: one on the server and two passed on to the cloud. */
private val standInModels = listOf(
    ChatModel(ModelId("gemma4"), parameterSize = "12B", canThink = true, canSee = true, canUseTools = true),
    ChatModel(ModelId("deepseek-v4.1-flash:cloud"), isCloud = true, canThink = true, canUseTools = true),
    ChatModel(ModelId("kimi-k3.1-pro:cloud"), isCloud = true, canThink = true, canSee = true, canUseTools = true),
)

internal fun showcaseModels(application: Application, real: suspend (String) -> List<ChatModel>): suspend (String) -> List<ChatModel> =
    { url -> if (isOn(application)) standInModels else real(url) }

/** With the sample data a sync succeeds at once and sends nothing: its hub is a placeholder. */
internal fun showcaseSync(application: Application, real: suspend () -> Int): suspend () -> Int =
    { if (isOn(application)) 0 else real() }

internal fun showcaseAllowsCapture(application: Application): Boolean = isOn(application)

internal fun showcaseRequested(intent: Intent?): Boolean = intent?.getBooleanExtra(SHOWCASE_EXTRA, false) == true

/** Writes the sample data into the stores, as if it had been made in the app, and opens the sample chat. */
internal fun startShowcase(application: HearthApplication) {
    File(application.filesDir, SHOWCASE_FILE).writeText("on")
    Log.i(TAG, "Sample data in place of the user's servers")
    val dependencies = application.dependencies
    MainScope().launch {
        val now = System.currentTimeMillis()
        val settings = dependencies.settingsStore.settings.value.copy(
            ollamaAddress = "https://my-server:11434",
            searchAddress = "https://my-server:8888",
            speechAddress = "https://my-server:8880",
            imageAddress = "https://my-server:8188",
            defaultModelId = ModelId("gemma4"),
            fullName = "Sam Rivers",
            nickname = "Sam",
            customInstructions = "Answer briefly unless I ask for detail. Use metric units.",
            voice = "af_heart",
        )
        dependencies.settingsStore.update(settings)
        dependencies.modelStore.refresh(settings)
        val garden = Project(
            name = "Garden",
            instructions = "Help me plan a small vegetable garden in a cool climate. Keep plans practical.",
            createdAt = now - 20 * DAY,
        )
        dependencies.projectStore.save(garden)
        listOf("Prefers metric units", "Grows tomatoes and basil on a sunny balcony", "Is learning Kotlin", "Has a dog called Pepper")
            .forEach { dependencies.memoryStore.apply(MemoryRequest.Save(it), isFromUser = true) }
        val tasks = sampleTasks(now)
        tasks.forEach { dependencies.taskStore.save(it) }
        sampleChats(now, garden, tasks.first()).forEach { dependencies.conversationStore.save(it) }
        dependencies.syncStore.update(SyncSettings(hubAddress = "https://my-server:8765", isEnabled = true))
        application.coordinator.startNewChat()
    }
}

private fun sampleTasks(now: Long): List<ScheduledTask> {
    val zone = ZoneId.systemDefault().id
    val todayAtHalfSeven = ZonedDateTime.now().withHour(7).withMinute(30).withSecond(0).toEpochSecond()
    val nextMonth = ZonedDateTime.now().plusDays(24).withHour(9).withMinute(0).withSecond(0).toEpochSecond()
    return listOf(
        ScheduledTask(
            title = "Morning briefing",
            prompt = "What are the three most important news stories this morning? A line on each.",
            model = TaskModels.AUTOMATIC,
            schedule = TaskSchedule.Daily(TaskTime(7, 30), zone),
            usesWebSearch = true,
            lastRunAt = todayAtHalfSeven.toDouble(),
            createdAt = now - 30 * DAY,
        ),
        ScheduledTask(
            title = "Weekend reading",
            prompt = "Suggest two long reads about science or design published this week, with a sentence on each.",
            model = "kimi-k3.1-pro:cloud",
            schedule = TaskSchedule.Weekly(TaskTime(18, 0), weekday = 5, timeZone = zone),
            usesWebSearch = true,
            createdAt = now - 14 * DAY,
        ),
        ScheduledTask(
            title = "Passport renewal",
            prompt = "Remind me what I need to renew a passport, as a short checklist.",
            model = "deepseek-v4.1-flash:cloud",
            schedule = TaskSchedule.Once(nextMonth),
            createdAt = now - 2 * DAY,
        ),
    )
}

private fun user(text: String, at: Long) = Message(role = MessageRole.USER, text = text, createdAt = at)

private fun reply(text: String, at: Long, thinkingSeconds: Int? = null, sources: List<SearchSource> = emptyList(), isResearch: Boolean = false) =
    Message(role = MessageRole.ASSISTANT, text = text, thinkingSeconds = thinkingSeconds, sources = sources, isResearch = isResearch, createdAt = at)

private fun chat(title: String, at: Long, vararg messages: Message, isPinned: Boolean = false, project: Project? = null, taskId: String? = null) =
    Conversation(
        title = title,
        modelId = ModelId("gemma4"),
        messages = messages.toList(),
        isPinned = isPinned,
        projectId = project?.id,
        createdAt = at - 2 * MINUTE,
        updatedAt = at,
        taskId = taskId,
    )

private fun sampleChats(now: Long, garden: Project, briefing: ScheduledTask): List<Conversation> = listOf(
    chat(
        "Sourdough timetable",
        now - 12 * DAY,
        user("Give me a sourdough timetable for baking on Saturday morning.", now - 12 * DAY),
        reply(SOURDOUGH, now - 12 * DAY, thinkingSeconds = 3),
        isPinned = true,
    ),
    chat("TCP and UDP, simply", now - 9 * DAY, user("Explain TCP and UDP to me like I'm new to networking.", now - 9 * DAY), reply(TCP, now - 9 * DAY)),
    chat(
        "Planning the spring beds",
        now - 4 * DAY,
        user("What should go in two raised beds this spring?", now - 4 * DAY),
        reply(BEDS, now - 4 * DAY, thinkingSeconds = 5),
        project = garden,
    ),
    chat("Heat pumps in cold climates", now - DAY, user(RESEARCH_QUESTION, now - DAY), reply(RESEARCH, now - DAY, thinkingSeconds = 41, sources = RESEARCH_SOURCES, isResearch = true)),
    chat(
        "Morning briefing",
        now - 3 * HOUR,
        user(briefing.prompt, now - 3 * HOUR),
        reply(BRIEFING, now - 3 * HOUR, sources = BRIEFING_SOURCES),
        taskId = briefing.id.raw,
    ),
    chat("A Pomodoro timer page", now - 40 * MINUTE, user("Make me a small Pomodoro timer as a web page.", now - 40 * MINUTE), reply(POMODORO, now - 40 * MINUTE, thinkingSeconds = 6)),
    chat("Retrying with backoff in Kotlin", now - 5 * MINUTE, user(RETRY_QUESTION, now - 5 * MINUTE), reply(RETRY, now - 5 * MINUTE, thinkingSeconds = 4)),
)

private const val RETRY_QUESTION = "How do I retry a network call with exponential backoff in Kotlin?"

private const val RETRY = """Wrap the call in a small **suspend** function that waits a little longer after each failure.

```kotlin
suspend fun <T> retrying(times: Int = 4, block: suspend () -> T): T {
    var wait = 500L
    repeat(times - 1) {
        try {
            return block()
        } catch (e: IOException) {
            delay(wait)
            wait *= 2
        }
    }
    return block()
}
```

- **Only retry what can recover**: timeouts and dropped connections, not a 404.
- **Add jitter** (`wait + Random.nextLong(250)`) so that many clients don't retry in step.
- **Cap the wait** at a few seconds, and give up after the last attempt."""

private const val POMODORO = """Here is a single page with a 25-minute timer, a start/pause button and a reset.

```html
<!doctype html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Pomodoro</title>
<style>
  body { font-family: system-ui, sans-serif; display: grid; place-items: center; height: 100vh; margin: 0; background: #f3f1ea; color: #1f2a26; }
  .time { font-size: 72px; font-weight: 600; letter-spacing: 2px; }
  button { font-size: 18px; padding: 12px 28px; margin: 8px; border: 0; border-radius: 24px; background: #2c6b5a; color: white; }
  button.quiet { background: #dfe6e1; color: #1f2a26; }
</style>
</head>
<body>
  <main style="text-align:center">
    <div class="time" id="time">25:00</div>
    <button id="go">Start</button><button class="quiet" id="reset">Reset</button>
  </main>
<script>
  let left = 25 * 60, timer = null;
  const show = () => time.textContent = String(Math.floor(left / 60)).padStart(2, "0") + ":" + String(left % 60).padStart(2, "0");
  go.onclick = () => {
    if (timer) { clearInterval(timer); timer = null; go.textContent = "Start"; return; }
    timer = setInterval(() => { left = Math.max(0, left - 1); show(); }, 1000);
    go.textContent = "Pause";
  };
  reset.onclick = () => { clearInterval(timer); timer = null; left = 25 * 60; go.textContent = "Start"; show(); };
</script>
</body>
</html>
```

Open it in the canvas to try it; ask if you would like a short break timer added."""

private const val RESEARCH_QUESTION = "Do heat pumps work well in really cold climates?"

private const val RESEARCH = """**Yes, modern cold-climate heat pumps keep heating well below freezing**, though their efficiency falls as it gets colder and very cold spells may call for backup heat [1][2].

## How well they work

- **Efficiency**: a cold-climate model typically delivers 2 to 3 units of heat per unit of electricity at −10 °C, and still more than 1.5 at −20 °C [1][3].
- **Capacity**: the best models hold most of their rated output down to about −15 °C before it drops off [2].
- **Field results**: monitored homes in cold regions cut heating energy use by roughly half compared with resistance heating [4].

| Outdoor temperature | Typical efficiency (COP) |
|---|---|
| 5 °C | 3.5–4.5 |
| −10 °C | 2.0–3.0 |
| −20 °C | 1.5–2.0 |

## What makes the difference

Sizing for the coldest days, good insulation and a defrost cycle that suits the climate matter more than the brand [3][5]. Backup heat is still common where nights fall below −25 °C [6]."""

private val RESEARCH_SOURCES = listOf(
    SearchSource("Cold-climate air source heat pumps: performance overview", "https://example.org/heat-pumps/cold-climate", ""),
    SearchSource("Heat pump capacity at low temperatures", "https://example.com/guides/heat-pump-capacity", ""),
    SearchSource("Choosing and sizing a heat pump", "https://example.net/home-energy/sizing", ""),
    SearchSource("Field study: heat pumps in northern homes", "https://research.example.edu/field-study", ""),
    SearchSource("Defrost cycles explained", "https://example.org/heat-pumps/defrost", ""),
    SearchSource("When do you need backup heat?", "https://example.com/guides/backup-heat", ""),
)

private const val BRIEFING = """1. **Climate talks**: negotiators agreed a draft text on adaptation funding after an overnight session [1].
2. **Markets**: shares rose in early trading after better-than-expected factory figures [2].
3. **Science**: a robotic arm assembled part of a test structure in orbit, a first for autonomous construction in space [3]."""

private val BRIEFING_SOURCES = listOf(
    SearchSource("Draft agreement reached on adaptation funding", "https://news.example.com/climate-talks", ""),
    SearchSource("Shares rise on factory data", "https://markets.example.org/today", ""),
    SearchSource("Robot builds structure in orbit", "https://science.example.net/space-robot", ""),
)

private const val SOURDOUGH = """For bread ready by **9 am Saturday**:

- **Friday 9 am**: feed the starter.
- **Friday 5 pm**: mix the dough; stretch and fold four times over two hours.
- **Friday 9 pm**: shape, then into the fridge overnight.
- **Saturday 7:30 am**: heat the oven to 250 °C with the pot inside.
- **Saturday 8 am**: bake 20 minutes lid on, 20 lid off."""

private const val TCP = """**TCP** is like a phone call: the two sides connect first, everything arrives in order, and anything lost is sent again. Web pages and email use it.

**UDP** is like posting postcards: each one goes on its own, quickly, with no promise it arrives or in what order. Video calls and games use it, because a late packet is worse than a lost one."""

private const val BEDS = """For two raised beds in a cool spring:

- **Bed one**: peas along the back on a trellis, then lettuce and spinach in front; all three like the cool.
- **Bed two**: potatoes now, followed by dwarf beans once the soil warms.

Sow a row of radishes between them; they are ready in four weeks."""
