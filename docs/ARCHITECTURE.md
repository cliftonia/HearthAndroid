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
├── MainActivity.kt          The one window: lock or screens, fingerprint prompt, shortcut
├── HearthApplication.kt     Holds the coordinator for the life of the process
├── AppCoordinator.kt        Owns the presenters; decides which screen shows
├── AppDependencies.kt       Builds the stores, interactors and providers, once
├── RootView.kt              Drawer or two columns, back button, which screen is drawn
├── chat/                    The conversation: presenter, view model, screen, composer, messages
├── drawer/                  The chat list: presenter, sections, rows, floating buttons
├── settings/                Settings: presenter, screen, one file per card
├── voice/                   The voice conversation: presenter, view model, screen
├── lock/                    The lock: presenter, screen
├── designsystem/            Colours, type, spacing, shapes, sizes and shared components
├── shared/                  The little that more than one feature needs and is not styling
└── integrations/            Wrappers round the platform: speech recogniser, media player, files

domain/src/main/kotlin/app/hearth/domain/
├── Conversation.kt, Message.kt, Memory.kt, ChatModel.kt, ServerSettings.kt, …     Models, one concept a file
├── ChatInteractor.kt, SpeechInteractor.kt                                          Business logic
├── ConversationStore.kt, MemoryStore.kt, ModelStore.kt, SettingsStore.kt          Shared state
├── ChatDataProvider.kt, SearchDataProvider.kt, DictationProvider.kt, …            The seams to the outside
└── PromptBuilder.kt, MarkdownTransformer.kt, SentenceChunker.kt, …                 Pure text work

data/src/main/kotlin/app/hearth/data/
├── OllamaService.kt, SearchService.kt, SpeechService.kt, PageService.kt           One service per server
├── OllamaDtos.kt, OllamaTools.kt                                                    What goes over the wire
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
    fun drawerPresenterDidRequestNewChat()
    …
}
```

Not every presenter needs a view model of its own. `SettingsPresenter` passes the stores' flows straight
through beside a few small ones, and `LockPresenter` is a single `Boolean`.

**Screen.** A composable that takes the presenter, collects its state with `collectAsStateWithLifecycle()` and
calls its actions. Screens hold only view-local state: whether a menu is open, what is typed in a dialog.
A screen-level composable ends in `Screen`; a part of one ends in `Section`, `Card`, `Row`, `Menu` and so on.

**Coordinator.** `AppCoordinator` creates every presenter, implements every delegate, and holds the two pieces
of navigation state: which `Screen` is showing and whether the drawer is open. A delegate call usually becomes
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
extension functions in the files beside it (`ChatPresenterReplies.kt`, `ChatPresenterAttachments.kt`,
`ChatPresenterEditing.kt`, `ChatPresenterDictation.kt`, `ChatPresenterNarration.kt`). The state they share is
`internal` for that reason.

## Below the presenters

**Interactors** (`ChatInteractor`, `SpeechInteractor`) are concrete classes that carry out a piece of business
logic from the providers they are given. They hold no screen state.

**Stores** (`ConversationStore`, `MemoryStore`, `ModelStore`, `SettingsStore`) hold what more than one screen
needs, as `StateFlow`s with methods to change them. They are changed from the main thread only.

**Data providers** are the seams. Each is a class of function-typed properties with a `live()` factory in its
companion:

```kotlin
class SpeechDataProvider(
    val synthesize: suspend (String, String, String) -> ByteArray,
    val fetchVoices: suspend (String) -> List<String>,
)
```

A test builds one from lambdas; nothing is mocked. Two providers, `AudioPlaybackProvider` and
`DictationProvider`, are declared in `domain` but have their `live()` in `app/integrations`, because playing
sound and listening are the phone's business.

**Errors.** Services throw what the transport throws. The domain remaps those to `ChatError` or `SpeechError`
where the meaning matters. A presenter catches at the action, logs the kind of failure (never its content),
and puts an `ErrorViewConfiguration` in its state for the screen to word.

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

The app is launched through `app.hearth.MainActivity`. That name, the intent action
`app.hearth.action.VOICE` and the shortcut id `voice` are known to launchers outside the app, so they stay
put even when code around them moves.
