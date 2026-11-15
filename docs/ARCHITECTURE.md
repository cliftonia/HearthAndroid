# Architecture

Hearth for Android is three Gradle modules. Each sees only the one below it, and the app has no injection
framework, no navigation library and no `ViewModel` classes from the platform: a coordinator, presenters and
plain Compose.

## Modules

```
app  ──▶  domain  ──▶  data
```

| Module | Kind | Holds | May import |
|---|---|---|---|
| `:app` | Android application | The coordinator, presenters, Compose screens, the design system, the phone's own services | `domain` |
| `:domain` | Plain Kotlin | Models, interactors, stores, data providers, transformers | `data` |
| `:data` | Plain Kotlin | HTTP services, DTOs, the file store | nothing of ours |

`domain` depends on `data` with `implementation`, not `api`, so the app cannot name a service or a DTO even
by accident. What the app needs from the outside world it gets as a **data provider**: a small class whose
properties are functions.

`domain` and `data` have no Android in them, which is why their tests run on the JVM alone.

## Where things live

```
app/src/main/kotlin/app/hearth/
├── MainActivity.kt          The one window: lock or screens, fingerprint prompt, shortcut, what other apps share
├── HearthApplication.kt     Holds the coordinator for the life of the process
├── AppCoordinator.kt        Owns the presenters; decides which screen shows
├── AppDependencies.kt       Builds the stores, interactors and providers, once
├── RootView.kt              Drawer or two columns, back button, which screen is drawn
├── chat/                    The conversation: presenter, view model, screen, composer, hold to talk, greeting, messages, pictures
├── drawer/                  The chat list: presenter, projects, sections, rows, floating buttons
├── project/                 The sheet that makes, edits and deletes a project: presenter, view model, sheet
├── projectpicker/           The sheet that files a chat in a project: presenter, view model, sheet
├── settings/                Settings: presenter, screen, one file per card, the profile and notifications pages
├── voice/                   The voice conversation: presenter, view model, screen
├── lock/                    The lock: presenter, screen
├── designsystem/            Colours, type, spacing, shapes, sizes, shared components and haptics
├── shared/                  The little that more than one feature needs and is not styling
├── widget/                  The home screen widgets
└── integrations/            Wrappers round the platform: speech recogniser, media player, files, saved pictures, profile photo, notifications

domain/src/main/kotlin/app/hearth/domain/
├── Conversation.kt, Message.kt, Project.kt, UserProfile.kt, ReplyVersion.kt, ImageModel.kt, …   Models, one concept a file
├── ChatInteractor.kt, SpeechInteractor.kt, ImageInteractor.kt, ProjectInteractor.kt, EraseInteractor.kt   Business logic
├── ConversationStore.kt, ProjectStore.kt, MemoryStore.kt, ProfileStore.kt, ModelStore.kt, SettingsStore.kt   Shared state
├── ChatDataProvider.kt, ProjectDataProvider.kt, ProfilePhotoProvider.kt, NotificationProvider.kt, …   The seams to the outside
├── GreetingPlanner.kt, ReplyNotificationPlanner.kt                                 Small decisions, kept pure
└── PromptBuilder.kt, PromptBuilderProject.kt, MarkdownTransformer.kt, …            Pure text work

data/src/main/kotlin/app/hearth/data/
├── OllamaService.kt, SearchService.kt, SpeechService.kt, ImageService.kt, …       One service per server
├── OllamaDtos.kt, OllamaTools.kt, ComfyDtos.kt, ImageGraph.kt                      What goes over the wire
└── FileStoreService.kt, HearthHttp.kt, HearthJson.kt, PageAddressPolicy.kt         Plumbing
```

A directory is always its package. A feature package never imports another feature package; what two
features share goes in `shared` (if it is behaviour) or `designsystem` (if it is how something looks).
The top-level package is the only place that knows every feature.

## The pattern: screen, presenter, coordinator

This mirrors the iPhone app, so that a change in one can be read across to the other.

**View model.** An immutable `data class` holding everything a screen shows (`ChatViewModel`,
`VoiceViewModel`). It has no behaviour beyond the odd derived property.

**Presenter.** A plain class, one per screen. It exposes its view model as a `StateFlow`, and its methods are
the things a user can do, each named for the intent and ending in `Action` (`sendAction`, `renameAction`).
A presenter never navigates and never talks to another presenter. When the user asks for something that is
not its own to do, it tells its **delegate**:

```kotlin
interface DrawerPresenterDelegate {
    fun drawerPresenterDidSelect(id: ConversationId)
    fun drawerPresenterDidRequestNewChat(projectId: ProjectId?)
    …
}
```

Not every presenter needs a view model of its own. `SettingsPresenter` passes the stores' flows straight
through beside a few small ones, and `LockPresenter` is a single `Boolean`.

**Screen.** A composable that takes the presenter, collects its state with `collectAsStateWithLifecycle()` and
calls its actions. Screens hold only view-local state: whether a menu is open, what is typed in a dialog.
A screen-level composable ends in `Screen`; a part of one ends in `Section`, `Card`, `Row`, `Menu` and so on.

**Pages inside a screen.** The settings have two pages of their own, the profile and the notifications. Which
is showing is the settings presenter's own state (`page`), not the coordinator's: nothing outside the settings
cares, and the back button asks the presenter, which steps back a page before it leaves.

**Coordinator.** `AppCoordinator` creates every presenter, implements every delegate, and holds the navigation
state: which `Screen` is showing, whether the drawer is open, and which project sheet is up, if either. It is also
told by the window whether the app is in front of the user, which the chat asks before it announces a reply. A delegate call usually becomes
one line there: change the screen, or call the presenter that must act.

```kotlin
override fun drawerPresenterDidSelect(id: ConversationId) {
    chat.selectConversationAction(id)
    drawerState.value = false
}
```

The coordinator lives in `HearthApplication`, not the activity, and runs on its own coroutine scope. That is
what lets a reply keep streaming while the phone is rotated or the app is in the background.

**Large presenters are split by concern.** `ChatPresenter` keeps its core in one file and its other actions as
extension functions in the files beside it (`ChatPresenterReplies.kt`, `ChatPresenterPictures.kt`,
`ChatPresenterAttachments.kt`, `ChatPresenterEditing.kt`, `ChatPresenterDictation.kt`, `ChatPresenterNarration.kt`,
`ChatPresenterNotifications.kt`, `ChatPresenterMenu.kt`), and `SettingsPresenter` does the same for its two pages
(`SettingsPresenterProfile.kt`, `SettingsPresenterNotifications.kt`). The state they share is
`internal` for that reason.

## Below the presenters

**Interactors** (`ChatInteractor`, `SpeechInteractor`, `ImageInteractor`, `ProjectInteractor`, `EraseInteractor`) are concrete classes that carry out a piece of business
logic from the providers, or the stores, they are given. They hold no screen state.

**Stores** (`ConversationStore`, `ProjectStore`, `MemoryStore`, `ProfileStore`, `ModelStore`, `SettingsStore`) hold what more than one screen
needs, as `StateFlow`s with methods to change them. They are changed from the main thread only.

**Planners** (`GreetingPlanner`, `ReplyNotificationPlanner`) each make one small decision from plain values and
touch nothing: which line an empty chat opens with, and whether a finished reply is announced and in what words.

**Data providers** are the seams. Each is a class of function-typed properties with a `live()` factory in its
companion:

```kotlin
class SpeechDataProvider(
    val synthesize: suspend (String, String, String) -> ByteArray,
    val fetchVoices: suspend (String) -> List<String>,
)
```

A test builds one from lambdas; nothing is mocked. Three providers, `AudioPlaybackProvider`,
`DictationProvider` and `NotificationProvider`, are declared in `domain` but have their `live()` in
`app/integrations`, because playing sound, listening and notifying are the phone's business.

**Errors.** Services throw what the transport throws. The domain remaps those to `ChatError`, `SpeechError` or
`ImageError` where the meaning matters. A presenter catches at the action, logs the kind of failure (never its content),
and puts an `ErrorViewConfiguration` in its state for the screen to word.

## Projects

A project gathers chats round a subject. It has a name, instructions and reference files, and every chat in it
is given the instructions and can draw on the files.

**Where it is kept.** A `Project` is one JSON file in `Projects/`, as a `Conversation` is one in `Conversations/`.
A conversation carries the `projectId` of its project, or none. Both are read with defaults for anything absent,
so what was saved before projects existed still loads, in no project.

**Who decides where a chat belongs.** `ConversationStore` does, the way it decides whether a chat is pinned: once
a chat has been saved, saving a copy of it cannot move it, and only `setProject` can. A chat that has not been
saved yet carries its project in the copy the chat screen holds, and keeps it at its first save. Deleting a
project goes through `ProjectInteractor`, which first takes the project off every chat and then deletes it, so
the chats are kept and none is left pointing at a project that is gone.

**What the model is told.** `ChatInteractor.Turn` carries the project, and `PromptBuilder` puts it in the system
message after the user's own instructions (`PromptBuilderProject.kt`). Reference files are quoted between a
marker made up afresh for every turn and described as material, never instructions. They share 60,000
characters in order; the model is told which file was cut short and which were left out. A project that has
reference files closes the memory tools for its chats, exactly as an attached file does: what is in a file must
not be able to plant a memory. A project with instructions alone leaves memory open, since the user typed those.

**The screens.** The drawer lists the projects above the dated groups; choosing one narrows the list to its
chats, and `DrawerSections` does the narrowing and the grouping. Two sheets belong to projects, each a feature
of its own: `project` makes and edits one, `projectpicker` files a chat. The coordinator holds one value,
`projectSheet`, for both, so that the picker can give way to the editor when a new project is wanted for a chat;
`RootView` draws whichever it names over everything else.

## Profile, and erasing everything

**The names are settings.** `ServerSettings` keeps the full name and the nickname as they were typed, and its
`profile` gives a `UserProfile` with them tidied into names: trimmed, on one line. Everything that uses a name
goes through `UserProfile` (the initials on the avatar, the name in the greeting, the line in the prompt), so a
space typed at the end of a name is not taken away under the cursor. The instructions are the same
`customInstructions` setting they always were; only the field moved, from a card of its own to the profile page.

**The photo is a file.** `ProfileStore` holds it and `ProfilePhotoProvider` keeps it as `Profile/photo.jpg` in the
app's private files. `ProfilePhotoLoader` in `integrations` makes it from the picture the user chooses: the
middle square, 320 pixels each way, as a JPEG.

**What the model is told.** `PromptBuilder` adds one line saying who the user is, before their instructions, and
adds it in a private chat too: the name is a setting, not a memory. Quotation marks in a name are made into
apostrophes and the name is kept to one line, so that it cannot pass itself off as anything else.

**Erasing.** `EraseInteractor` erases the chats, the projects, the memories and the profile photo, each through
the store that keeps it, then what the app keeps outside the stores (a photo taken for a message, a picture lent
to another app, a memory file set aside), and the settings last. If anything fails, the failure is passed on
with the settings still in place, so the user can try again. The settings presenter stops whatever the chat is
doing first, through a closure the coordinator gives it, so that no reply is written back after it was erased.
The coordinator then puts the app back as it first opened.

## The greeting

An empty chat opens with a short line chosen by `GreetingPlanner`: one of a few kinds, by the time of day or the
day of the week, and by name when a name is known. The seed is made from the chat's identifier, so one chat
keeps its line and the next new chat is likely to get another. The name is the nickname, failing that the first
word of the full name, failing that one found among the memories by `UserNameFinder`; a private chat uses no
memories, so there it comes from the profile alone. `chat/ChatGreeting.kt` puts the planner's choice into words.

## Notifications

There is one kind: a reply or a picture finished while the app was not on screen. Nothing runs in the background
for it and nothing comes from a server. The chat presenter, which is already running the reply, asks
`ReplyNotificationPlanner` when the turn ends and posts through `NotificationProvider`.

- The planner says nothing while the app is on screen, while the switch is off, and for a turn that failed or
  came to nothing. For a private chat it gives only `Discreet`: the app's name and "A reply is ready."
- When the app *is* on screen the same moment is felt as one haptic instead, and never while a reply streams.
- Leave to notify is asked for by the notifications page, and only when the switch is turned on. A refusal leaves
  the switch on; the page then says so and leads to the phone's own settings.
- A notification carries its chat's identifier. Tapping it starts `MainActivity` with
  `app.hearth.action.OPEN_CHAT`, and the coordinator opens that chat, reading the chats from disk first if the
  tap is what launched the app.

## Hold to talk

Holding the resting, empty composer dictates into it. The account of a press is `HoldToTalk` in `chat/`, a value
with no behaviour beyond its own steps (`pressed`, `begun`, `hearing`, `released`), tested by itself. The
presenter does the listening (`ChatPresenterDictation.kt`), and the touch itself is told apart from a tap in
`HoldToTalkRow.kt`.

The rule that matters: **the recogniser is never left listening with nobody holding on.** Three things keep it:

1. The press is noted before anything is waited for. A finger that lifts while leave to use the microphone is
   still being asked marks the press as no longer wanted, and the recogniser is then never started.
2. From the moment the presenter decides to listen, a release finds a recording to stop. The live
   `DictationProvider` remembers a stop that arrives before its recogniser exists, and does not set it listening.
3. A recogniser that has been asked to stop and has not let go within a few seconds is cut off.

The words wait in the press until it ends, then go into the draft; nothing is sent. The dots in the recording row
follow the level the recogniser reports (`VoiceLevelMeter`, `VoiceLevelDots`).

## Haptics

Every haptic goes through `Haptics` in `designsystem`, which the switch in Settings silences. A button calls
`Haptics.play(kind)`; a screen hangs `HapticEffect(kind, trigger)` on a value that changes; `HapticsHost`, placed
once by the window, gives `Haptics` a view to play through. Kinds are named for how they feel (`LIGHT`, `SOFT`,
`SELECTION`, `SUCCESS`, `WARNING`, `ERROR`), not for what caused them. The phone's own touch feedback setting is
respected, since the taps are played through the view.

## Adding a screen

1. **Domain first.** Add the models, and an interactor or store if there is logic or shared state. Reach the
   outside world through a data provider. Test it on the JVM.
2. **Make a package** under `app/hearth/` named for the feature.
3. **Presenter.** A class taking a `CoroutineScope` and its dependencies. Give it a `StateFlow` of an immutable
   view model, `…Action` methods, and a `…PresenterDelegate` interface for anything that leaves the screen.
4. **Screen.** A `@Composable fun …Screen(presenter, modifier)`. Style it only from `HearthTheme` and the
   components in `designsystem` (see [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)). Put every string in
   `res/values/strings.xml`.
5. **Coordinator.** Create the presenter in `AppCoordinator`, set its delegate in `start()`, implement the
   delegate, and add a case to `Screen`.
6. **Root.** Draw the screen for that case in `RootView`, and say what the back button does while it shows.

## Building and checking

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The app is launched through `app.hearth.MainActivity`. That name, the intent actions
`app.hearth.action.VOICE` and `app.hearth.action.NEW_CHAT`, the shortcut id `voice` and the widget classes
`app.hearth.widget.NewChatWidget` and `VoiceWidget` are known to launchers outside the app, so they stay
put even when code around them moves. So do `app.hearth.action.OPEN_CHAT`, its extra
`app.hearth.extra.CONVERSATION_ID` and the notification channel `replies`: a notification posted by one version
of the app may be tapped after the next is installed.

**The picture server shares one graphics card with the chat server.** `ImageInteractor` therefore asks the chat
server to unload its models before every picture and the picture server to free its memory after it, also when
the picture fails or is given up on. Keep both when changing it.
