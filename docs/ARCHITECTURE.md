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
├── chat/                    The conversation: presenter, view model, screen, composer, hold to talk, greeting, messages, pictures, research, the canvas's cards
├── canvas/                  The canvas: presenter, view model, screen, the code view, the web view and its guard, the wide layout
├── drawer/                  The chat list: presenter, projects, sections, rows, floating buttons
├── project/                 The sheet that makes, edits and deletes a project: presenter, view model, sheet
├── projectpicker/           The sheet that files a chat in a project: presenter, view model, sheet
├── settings/                Settings: presenter, screen, one file per card, the profile, notifications and sync pages
├── sync/                    When to sync (`SyncScheduler`), and the hooks sync is given into the chat
├── voice/                   The voice conversation: presenter (and its camera), view model, screen, controls, the camera's card and its layout
├── lock/                    The lock: presenter, screen
├── designsystem/            Colours, type, spacing, shapes, sizes, shared components and haptics
├── shared/                  The little that more than one feature needs and is not styling
├── widget/                  The home screen widgets
└── integrations/            Wrappers round the platform: speech recogniser, media player, camera, files, saved pictures, profile photo, notifications, the hub token's keystore seal

domain/src/main/kotlin/app/hearth/domain/
├── Conversation.kt, Message.kt, Project.kt, UserProfile.kt, ReplyVersion.kt, ImageModel.kt, VoiceCamera.kt, …   Models, one concept a file
├── ChatInteractor.kt, ResearchInteractor.kt, SpeechInteractor.kt, ImageInteractor.kt, ProjectInteractor.kt, EraseInteractor.kt, SyncInteractor.kt   Business logic
├── ConversationStore.kt, ProjectStore.kt, MemoryStore.kt, ProfileStore.kt, ModelStore.kt, SettingsStore.kt, SyncStore.kt   Shared state
├── SavedDataLoader.kt                                                             Reads the chats, projects and memories at launch
├── Sync.kt, SyncJournal.kt, SyncMapping.kt, SyncPlanner.kt, SyncDataProvider.kt    Sync: its models, the journal, the wire mapping, the decisions
├── ChatDataProvider.kt, ProjectDataProvider.kt, ProfilePhotoProvider.kt, NotificationProvider.kt, CameraProvider.kt, …   The seams to the outside
├── GreetingPlanner.kt, ReplyNotificationPlanner.kt                                 Small decisions, kept pure
├── Research.kt, ResearchQueryParser.kt, ResearchSourceSelector.kt, ResearchPromptBuilder.kt, ResearchCitationTransformer.kt   Research: its models and pure steps
├── Canvas.kt, CanvasExtractor.kt, CanvasVersionMatcher.kt, CanvasWebPolicy.kt      The canvas: its models and pure decisions
├── ConversationTitle.kt, TextLimit.kt                                              Titles (first message, renamed, from the model), and cutting text to a limit
└── PromptBuilder.kt, PromptBuilderProject.kt, MarkdownTransformer.kt, …            Pure text work

data/src/main/kotlin/app/hearth/data/
├── OllamaService.kt, SearchService.kt, SpeechService.kt, ImageService.kt, …       One service per server
├── OllamaDtos.kt, OllamaTools.kt, ComfyDtos.kt, ImageGraph.kt, SyncDtos.kt         What goes over the wire
├── SyncService.kt, SyncWire.kt, WireTime.kt                                        The hub: its requests, raw JSON and unknown fields, its times
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

**Pages inside a screen.** The settings have three pages of their own, the profile, the notifications and sync. Which
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
extension functions in the files beside it (`ChatPresenterReplies.kt`, `ChatPresenterPictures.kt`, `ChatPresenterResearch.kt`,
`ChatPresenterAttachments.kt`, `ChatPresenterEditing.kt`, `ChatPresenterDictation.kt`, `ChatPresenterNarration.kt`,
`ChatPresenterNotifications.kt`, `ChatPresenterCanvas.kt`, `ChatPresenterMenu.kt`), and `SettingsPresenter` does the same for its two pages
(`SettingsPresenterProfile.kt`, `SettingsPresenterNotifications.kt`, `SettingsPresenterSync.kt`), and `VoicePresenter` for its camera
(`VoicePresenterCamera.kt`). The state they share is
`internal` for that reason.

## Below the presenters

**Interactors** (`ChatInteractor`, `ResearchInteractor`, `SpeechInteractor`, `ImageInteractor`, `ProjectInteractor`, `EraseInteractor`, `SyncInteractor`) are concrete classes that carry out a piece of business
logic from the providers, or the stores, they are given. They hold no screen state.

**Stores** (`ConversationStore`, `ProjectStore`, `MemoryStore`, `ProfileStore`, `ModelStore`, `SettingsStore`, `SyncStore`) hold what more than one screen
needs, as `StateFlow`s with methods to change them. They are changed from the main thread only, and never read or
write the disk or the keystore on it: their providers do that work on `Dispatchers.IO`, as `FileStoreService` does.

**Loading at launch.** Building `AppDependencies` reads nothing. `AppCoordinator.start()` launches the reading:
first `SettingsStore.load()`, `SyncStore.load()` (its settings, journal and whether a token is kept) and where the
chat list was left, after which the lock is set from the settings and `isReady` turns true; `MainActivity` draws
nothing, holding the launch screen, until then, and intents from shortcuts, widgets and notifications wait for it
too, so the first frame is never drawn from the defaults. Then `SavedDataLoader` reads the chats, projects and
memories, each store on its own so that one unreadable store leaves the others full. Sync is started only if all
three were read, so that it never weighs the hub against a list that only looks empty; then the models are asked for.

**Planners** (`GreetingPlanner`, `ReplyNotificationPlanner`, `SyncPlanner`) each make decisions from plain values and
touch nothing: which line an empty chat opens with, whether a finished reply is announced and in what words, and
which copy of a synced item wins.

**Data providers** are the seams. Each is a class of function-typed properties with a `live()` factory in its
companion:

```kotlin
class SpeechDataProvider(
    val synthesize: suspend (String, String, String) -> ByteArray,
    val fetchVoices: suspend (String) -> List<String>,
)
```

A test builds one from lambdas; nothing is mocked. Four providers, `AudioPlaybackProvider`,
`DictationProvider`, `NotificationProvider` and `CameraProvider`, are declared in `domain` but have their `live()` in
`app/integrations`, because playing sound, listening, notifying and the camera are the phone's business.

**Errors.** Services throw what the transport throws. The domain remaps those to `ChatError`, `SpeechError`,
`ImageError` or `ResearchError` where the meaning matters. A presenter catches at the action, logs the kind of failure (never its content),
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

## Deep research

Research answers one question with a report written from pages the app searched for and read. It is a turn like
any other: it runs with the chat's selected model, streams into a reply, and is saved in the conversation. It
mirrors the iPhone app step for step, with the same bounds, prompts and wording.

- **Chosen as an effort.** The model sheet's Effort menu lists the thinking levels and then Deep research.
  `EffortChoiceKind` is that choice: `Thinking(ThinkingLevel)` or `Research`. Research is never a thinking level
  and is never sent to the model as one; while it is chosen the model thinks at the level saved in the settings.
  A thinking level is saved for every chat (`ServerSettings.thinkingLevel`); research is only
  `ChatViewModel.isResearchMode`, which belongs to the open chat, is not saved, and is cleared by a new chat, by
  opening another chat and by picture mode. Whether it is in force is `isResearchChosen(settings)`, which also
  wants a search address, so taking the address away takes research with it. A model that cannot think is offered
  one ordinary choice, "Standard", and research. Without a search address research is listed, disabled, with a line
  saying why. It is not in the + menu.
- **The run** is `ResearchInteractor.run`, a `Flow` of events, built on the same `ChatDataProvider` and
  `SearchDataProvider` as a reply, so requests go to the same model through the same service and pages are read
  through `PageService` and its `PageAddressPolicy`. In order: plan (one request), search each query, choose the
  pages, read them a few at a time, take notes on each (one request a page), ask once whether anything important is
  missing (one request) and, if so, search, read and note once more, then write (one streamed request).
- **What is pure** sits in transformers: `ResearchQueryParser` reads queries out of JSON, fenced JSON, objects or
  plain lines, and falls back to the question; `ResearchSourceSelector` drops repeats by normalised address, lets
  the searches take turns and caps the pages from one site; `ResearchPromptBuilder` words each request and fences
  page text and notes between markers made up for the run; `ResearchCitationTransformer` turns every citation into
  a plain `[n]`, removes any number with no source behind it, and cuts the sources to those cited, renumbered in
  order. Citations inside code and the text of links are left alone.
- **Bounds** are the constants of `ResearchLimits`: queries, sources, sources from one site, pages fetched at once,
  note requests at once, the wait for a page, characters read from a page, characters of one page's notes, and
  characters of notes handed to the writer.
- **What stops it.** A search or a page that fails is passed over. With nothing to write from it throws a
  `ResearchError` that says which step came to nothing (`SearchFailed`, `NothingFound`, `NothingReadable`,
  `NothingRelevant`) and no report is written. The model failing on one page loses that page; failing on all of
  them is the chat server's error. Stopping cancels the coroutine that collects the flow, and with it every fetch
  and request under way: the pages and the notes are children of that coroutine, and the services close their
  connections when their caller is cancelled (`Call.await` in `HearthHttp.kt`), so nothing is left running.
- **Kept** as an ordinary reply whose `Message.isResearch` is set, with the report as its text and the cited pages
  as its `sources`, so "Try again" researches again whatever effort is then chosen, keeping the earlier report as
  a version. A chat saved before there was research reads as having none. A report stopped part of the way is kept
  as far as it got, tidied in the same way; one stopped before it had a word is dropped, and the earlier report is
  shown again if it was another attempt. The sources close memory for the rest of the conversation, as any web
  search does, and research itself reads no memories. It is announced like any reply when it finishes.
- **On screen**, `ChatPresenterResearch.kt` keeps the step in `ChatViewModel.researchStep`, which `MessageSection`
  words as a line in place of the report until its first words arrive, and `ResearchSourcesSection` lists the
  sources under a finished report by number, title and site. The composer's chip names the effort in the brand
  colour and its field asks for a question to research. The logs hold counts only: never the question, a page's
  text or an address. Reducing a page to text is done off the main thread.

## The canvas

A reply may hold a piece of standalone work: a web page, a drawing, a document or a long block of code. The chat
shows a card in its place, and the card opens it in the canvas. Nothing about it is stored and nothing about it is
sent to the model; a piece is read out of the saved text of its reply each time it is wanted. It mirrors the iPhone
app rule for rule, with the same wording.

- **What a piece is** is decided by `CanvasExtractor`, a pure transformer over a reply's Markdown. It reads the
  text once, line by line, and cuts it into `CanvasSegment`s: stretches of ordinary Markdown and `CanvasPiece`s. A
  fenced block is a piece when it is HTML or SVG, by its language tag or by how it begins (`<!doctype html`,
  `<html`, `<svg`), or when it has at least `CanvasExtractor.MINIMUM_LINE_COUNT` lines, 15, as a `markdown`/`md`
  document or as code. Any other block stays in the Markdown around it, exactly as written, and is drawn by
  `MarkdownSection` as before. A fence still open when the text ends makes a piece that is not yet closed
  (`isClosed`), which is how a reply on its way is read. A block is closed by a bare fence at least as long as
  its own, and a Markdown document counts the fenced blocks inside it in and out.
- **Its kind** is a `CanvasKind`: `Html`, `Svg`, `Markdown` or `Code(language)`. **Its title** is the page's or the
  drawing's own `<title>`, a document's first heading, or else the nearest heading within twelve lines above it;
  with none, the screen names it for its kind ("Page", "Drawing", "Document", "Python code"; `shared/CanvasWording.kt`).
  **Its identity** is its place among the pieces of its reply, which cannot change while the reply streams, with
  the message's identifier: a `CanvasReference`.
- **Versions** are found by `CanvasVersionMatcher`, also pure: a piece anywhere in the chat is a version of the
  open one when it is the same kind (any two pieces of code are) and either has the same title or is the only
  piece in its reply. The canvas remembers the piece it was opened for and, at most, a version stepped back to;
  with none it shows the newest, so a version being written is followed as it arrives.
- **In the chat**, `ReplyBodySection` (`chat/CanvasCard.kt`) lays a reply out from its segments, with a card for
  each piece. A card whose piece is open in a reply still streaming shows that it is being written.
- **The screen** is `CanvasPresenter`, `CanvasViewModel` and `CanvasScreen`. The presenter is given two closures,
  the open chat's messages and the reply on its way, and builds a `CanvasViewConfiguration` from them whenever the
  coordinator tells it the chat has changed, so it holds no copy of anything beyond what is on screen. The view
  model holds what was opened, the version looked at, the choice of Preview or Code, and that configuration. A
  piece still being written is shown as code. Copy and Share are the screen's, as they are for a reply. The canvas
  does not know the chat: the root view hands it `MarkdownSection` to lay a document out with.
- **Opening and closing** go through the coordinator: the card calls `ChatPresenter.canvasAction`, the coordinator
  opens the presenter and sets `isCanvasOpen`. A wide window shows the canvas as a trailing column of `RootView`;
  `CanvasLayout` gives it half of the space beside the chat list and puts the chat list away while there is not
  room for all three (each of the chat and the canvas wants `canvasColumnMinWidth`), which needs no state of its
  own, so closing brings the list back, and the chat list's button closes the canvas while the list is crowded
  out. A narrow window shows a page that rises over the chat; the chat stays as it was beneath it, out of reach of
  touch and of a screen reader. The canvas closes by its button, by the back button, by the lock, by the settings
  or a voice conversation taking the chat's place, and when its piece is no longer in the open chat, as when
  another chat or a new chat is opened.
- **A generated page is untrusted.** `CanvasWebPolicy` in the domain holds the decisions and `CanvasWebPreview`
  carries them out in an `android.webkit.WebView`, made only for a finished page or drawing and destroyed when the
  canvas goes:
  - the document is given with `loadDataWithBaseURL` at `about:blank`, so it has an opaque origin and no file or
    content address; file and content access are off; cookies, DOM storage, the database, form data and the cache
    are off or cleared;
  - scripts run, and nothing of the app is exposed to them (`addJavascriptInterface` is never called);
  - the network is refused three ways: `blockNetworkLoads` and `blockNetworkImage`; `shouldInterceptRequest`
    answers everything but `data:` and `about:` with an empty 403; and the policy puts a
    `Content-Security-Policy` first in the page's head (after its doctype, if it has one), which allows only what
    the page carries itself and no connection. The last matters: measured on the emulator, the first two alone
    let a WebSocket through, and the rules stop it;
  - `shouldOverrideUrlLoading` cancels every navigation but one to `about:`, the rules forbid sending a form, and
    `onCreateWindow` makes no window, so a link, `location.href`, a refresh and `window.open` all lead nowhere.
    The log notes that a navigation was cancelled and never its address;
  - permissions (camera, microphone), geolocation, file choosing and downloads are refused without asking;
  - a script put before the page's own (`CanvasWebPolicy.startScript`) takes peer connections out of the page's
    window and out of every frame the page makes, and has `sendBeacon` answer that nothing was sent. The engine
    does not know the `webrtc` rule, so this script is what stops WebRTC.
- **Peer connections in frames.** A frame starts with an empty document whose window has peer connections of its
  own, and a page can make one (an empty `<iframe>`, `srcdoc`, markup, `innerHTML`, a frame in a frame) and take
  `RTCPeerConnection` from it at once. `WebViewCompat.addDocumentStartJavaScript` (`androidx.webkit`), which the
  app now uses with every origin wherever the web view supports it, runs the script at the start of each
  document a frame loads, but measured on the emulator (WebView 113) it does not run in that first empty
  document, nor does a `frame-src 'none'` rule stop such a frame. So the script seals frames itself: every way a
  script makes a frame or reaches its window (inserting nodes or markup, `document.write` and `open`,
  `contentWindow` and `contentDocument`, shadow roots and the like) seals every frame in the page, walked by
  index, and installs the same script in each; a frame made by the parser is sealed by a `MutationObserver`
  before the next script runs, and one whose `load` handler runs at once by a listener ahead of the page's.
  Everything it relies on is taken before the page runs. Measured with a probe page that makes or reaches frames
  22 ways and reads the constructors from each, at once and three seconds later, and with scripts inside
  `srcdoc` frames that report on themselves: before, every frame gave a working constructor and a capture
  showed STUN packets leaving; with `addDocumentStartJavaScript` alone, all but the loaded `srcdoc` frames still
  did; after, none did and the capture showed none.
- **What fails closed.** The script can only seal a frame it can reach, and the one frame in a page it cannot reach
  is one whose `sandbox` puts it in an origin of its own. So no frame is given a sandbox: the attribute is taken off
  anything before it is put in the page, `setAttribute`, `setAttributeNS`, `toggleAttribute`, `setAttributeNode`,
  `setNamedItem` and the `sandbox` property ignore it, and the list of flags the page is handed changes nothing; a
  frame the sweep still cannot reach, such as one the parser made with a sandbox, is taken out of the page (so is a
  frame left on an error page by a blocked address, which shows nothing anyway). Markup that would make a shadow
  root from a script is read as plain markup, so a script cannot hide a frame in a closed one: `setHTMLUnsafe` and
  `Document.parseHTMLUnsafe` where the engine has them (WebView 113 has neither), a `DOMParser` asked to
  `includeShadowRoots`, which WebView 113 has, and `document.write` (below). Measured on the emulator (WebView
  113) with the probe above extended by 15 sandboxed frames that report on themselves (in the page's markup, in a declarative shadow root in it, by
  `setAttribute` with and without `allow-same-origin`, the property, an attribute node, its list of flags, after
  insertion, removed after insertion, by `innerHTML`, by `setHTMLUnsafe`, by `document.write` into a shadow root, by
  a `DOMParser` shadow root, and with a `javascript:` address from script and from markup). With the web view as
  shipped, `addDocumentStartJavaScript` already reached every sandboxed frame that loaded: `LEAKING: 0 of 61` before
  and `0 of 59` after, and a capture showed no STUN packet and no lookup of the STUN server. With the start script
  in the page alone, as on a web view without `DOCUMENT_START_SCRIPT`, the sandboxed frames were the gap: 12 of 61
  before, every sandboxed frame without `allow-same-origin` able to make a peer connection; 2 of 59 after.
- **Declarative shadow roots.** The parser makes a shadow root from `<template shadowrootmode>` without any script
  being asked, so the sweep neither sees it made nor looks inside it, and a frame there (in a closed one, a sandboxed
  frame above all) was out of its reach. Three things close that. `CanvasWebPolicy.document` puts the page, and a
  drawing, through `CanvasShadowRootTransformer`, which renames `shadowrootmode`, `shadowroot`,
  `shadowrootdelegatesfocus`, `shadowrootclonable` and `shadowrootserializable`, in any case, wherever an
  attribute's name can begin and end, to `data-hearth-…`: the template stays an inert template. It does not read
  the markup as the parser does, so it also renames the words where one stands alone in the page's text; one too
  many does no harm. The start script makes `document.write` and `writeln` do nothing, since a name split between two writes
  cannot be renamed reliably. And its
  sweep keeps every open shadow root it comes across, whoever made it, and watches it as it does one a script made;
  since a frame in a shadow root is not among the page's frames by index, a frame the sweep finds anywhere, or whose
  window is asked for, is sealed then and there. Which other ways make such a root was measured in Chromium (desktop
  157, with the network shut off) and in WebKit: `document.write` and `open` made one (one split between two writes
  too), as did `setHTMLUnsafe` and `Document.parseHTMLUnsafe`, which were already covered;
  `Range.createContextualFragment`, `DOMParser` (asked to include shadow roots or not), `innerHTML`,
  `insertAdjacentHTML` and `execCommand("insertHTML")` made none, and `setHTML` left no frame. With a probe page
  making frames in shadow roots those ways and the script in the page, the script as it was left every one of them
  working; after, every frame was sealed and no write made a root. Not yet measured on the emulator.
- **What is not shut off.** The two left above, a sandboxed frame in a closed declarative shadow root in the
  page's markup and one written by `document.write`, are now kept from being made (not yet measured on the
  emulator). A closed declarative shadow root can still be made by a frame's own document, a `srcdoc` whose
  markup hides the attribute's name behind character references, say, and a sandboxed frame inside it cannot be
  found to be removed; where the web view has `DOCUMENT_START_SCRIPT` the script still runs in it. Checked with a
  packet capture on the emulator (WebView 113): a page can still cause a DNS lookup with `<link rel="dns-prefetch">`, and a lookup and a
  bare connection with `<link rel="preconnect">`. `android.webkit` and `androidx.webkit` have no setting for
  either, and nothing known closes them. No load succeeds through either, but each can carry data out of a page.
  The frame sealing is a script that stands between the page and the engine rather than a rule the engine
  enforces: it covers every way to make or reach a frame that the probe tried, but a way it does not intercept
  (firing a frame's handlers inside a shadow root, or a newer API) would reopen it. So a page should not be
  thought of as sealed.

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
the store that keeps it, after sync has been stopped and forgotten, then what the app keeps outside the stores (a photo taken for a message, a picture lent
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

## Voice with the camera

A voice conversation may have the camera on. While it is, each spoken turn is sent with one still picture taken as
the words end. No video is recorded or sent. It mirrors the iPhone app, account for account.

- **The provider.** `CameraProvider` in the domain is a class of closures: ask for leave, start facing one way,
  stop, flip, take one frame, and say what cameras there are. `CameraProvider.live` in `integrations/Camera.kt` is
  backed by `DeviceCamera`, which works the platform's own camera API (Camera2) with no library added. While the
  camera is on, the only thing it feeds is the live picture on screen; a still is one capture of its own, asked
  for when a turn ends, delivered as a JPEG, and no sound is made for it. Every step runs on the camera's own
  thread, one at a time. Turning it off closes the camera device, which is what puts the phone's indicator out.
- **The live picture** never passes through the domain. `VoiceCameraCard` holds a `TextureView` and tells
  `DeviceCamera` when it has somewhere to draw and when that has gone; the camera carries on without it, as it
  must while the phone is turned round and the screen is drawn afresh. The view lets go of its surface the moment
  it goes: a surface kept after its view has gone is drawn into with nobody taking the pictures away, and a
  camera held up like that takes many seconds to close. `CameraGeometry` holds the sums, apart from the camera so
  that they can be tested: which sizes to ask for, how far a still must be turned to be upright, and how the live
  picture is scaled and turned to fill its card unstretched whichever way the phone is held.
- **The account.** `VoiceCamera` is the pure state, kept on `VoiceViewModel`: off, asking, starting or on, which
  way it faces, and why it is off if the user would expect otherwise. Each step returns the account as it then
  stands, or `null` when the step does not apply. Every attempt to turn the camera on is numbered, and each step
  of it is checked against that number, so an attempt overtaken by the screen closing, the app leaving the front
  or another tap can never leave the camera running. `VoiceCamera.control` says whether the button is hidden (no
  camera), disabled (the chosen model cannot see; the screen says so, and no other model is chosen for the user)
  or enabled.
- **Leave.** Only a screen can put the phone's question to the user, and the presenter must not know the screen.
  `CameraLeave` stands between them: the voice screen lends it a way of asking for as long as it is showing, and
  the provider's `requestAuthorization` waits on it for the answer. Leave is asked for only when the button is
  first turned on. A refusal leaves the conversation as it was and shows a `HearthPermissionNotice` that leads to
  the phone's settings for the app.
- **When it stops.** By its button, by the conversation ending, by the screen leaving for any reason
  (`closedAction`, and the coordinator when it puts the conversation away), and by the app leaving the foreground:
  the screen passes on `ON_PAUSE` as covered and `ON_STOP` as off the screen. The phone's own question about the
  camera also covers the app, so an attempt that is only asking is left to finish unless the app goes off the
  screen altogether. Turning the phone round is none of these, and the camera stays as it was. It is off, facing
  away, each time a conversation opens, and stays off after the app returns.
- **The picture.** When the listener hears the end of a turn, the loop asks for one frame before anything is
  sent. The frame goes through `AttachmentLoader.frame`, the same decoding, shrinking and squeezing an attached
  photo is given, and is handed to the chat with the words (`ChatPresenter.submitSpoken`), where it is an ordinary
  entry in `Message.images`: shown on the user's message, saved with the chat, sent to the model, and closing
  memory, in a private chat as in any other. A frame that cannot be taken or read, or that is ready only after
  the camera was turned off, means the turn goes without one.
- **Take photo needs the same leave.** Once an app declares that it uses the camera, Android lets it borrow the
  phone's camera app only if it has leave to use the camera itself. The composer's Take photo therefore asks for
  that leave the first time, and says where it is given if it is refused.
- **Trying it on an emulator.** An emulator's recogniser hears nothing. A debug build looks for a file named
  `voice-script.txt` among the app's own files; if it is there, a scripted listener takes the recogniser's place
  and hears the words in the file once, twenty seconds after listening begins, and the voice says nothing and asks
  no speech server. With an empty file it hears nothing. The code is in `app/src/debug` with a twin in
  `app/src/release` that changes nothing, so it is in no build that ships.

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

## Kept in step with iOS

The iPhone app is a separate code base that must behave like this one. What the two have to agree on is written down
as data in the iPhone repository's `Spec/` folder (its `README.md` says how it is laid out and how to add to it).
`tools/sync-spec.sh` copies it into `domain/src/test/resources/spec` (vectors and wire examples) and
`data/src/test/resources/spec` (wire examples); `SpecPromptTests`, `SpecTextTests` and `SpecWireMappingTests` in the
domain and `SpecWireTests` in the data layer read the copy, and `SpecCopyTests` fails while it differs from
`../Hearth/Spec`, and is skipped where that folder is not there. Hearth Hub round-trips the wire examples. So far
the vectors cover pure functions only, and they settle these:

- **The date** in the system message and in every research prompt is `PromptBuilder.dateText`: English, weekday
  day month year with no commas (`Monday 16 November 2026`), whatever the phone's language, in its time zone. Both
  builders take a `timeZone`, the phone's by default, so the tests can fix it.
- **The tools** are explained in the system message memory first, then search.
- **The search query** (`PromptBuilder.searchQuery`) is the latest user message on one line, blank lines left out,
  joined by single spaces, cut to `MAXIMUM_SEARCH_QUERY_LENGTH` (200); white space alone is no query.
- **A memory** is refused as already saved when one is the same whatever its case or accents: both are taken apart
  (NFD), their combining marks dropped and the rest lowercased (`Locale.ROOT`) before they are compared.
- **Titles.** A provisional title is `provisionalTitle`, the first `PROVISIONAL_TITLE_LENGTH` (48) characters; a title
  typed by hand, in the drawer or the chat's menu, goes through `renamedTitle`, trimmed and cut to
  `MAXIMUM_RENAMED_TITLE_LENGTH` (80); a title a model wrote goes through `generatedTitle`, its first line that is not
  blank, unwrapped, cut to 60.
- **Limits count characters as each platform sees them.** Every limit on text (a title, a rename, a memory, a file,
  a page, a query, a name typed in Settings) is applied with `String.clipped` (`TextLimit.kt`), which counts UTF-16
  units, as `take` does, but never ends between the two halves of a surrogate pair, so an emoji is never cut in
  half. Swift counts whole characters, so an emoji with a skin tone or a letter with a combining accent is one
  there. The same long text may be cut at a different place on each; no attempt is made to make the counts agree.
- **The canvas start script** does three things the iOS one does not, each a Chromium matter: it has `sendBeacon`
  answer that nothing was sent (on iOS the content rules block a beacon like any other load, so nothing leaves
  either way); it replaces `Document.parseHTMLUnsafe` only where the engine has it (iOS puts it in place always,
  the stricter choice); and it keeps a `DOMParser` from being asked to `includeShadowRoots`, an
  option WebKit's parser does not have. The reasons are also beside the iOS script.

## Building and checking

```sh
./gradlew test lintDebug assembleDebug
```

`test` runs the domain and data modules' tests and the app's for both build types. Continuous integration
(`.github/workflows/android.yml`) runs the same checks for the debug build on every push and keeps the lint
report. It needs no `local.properties`: `app/build.gradle.kts` reads that file only if it exists, and each key
falls back to an empty address or the default application id. `app/lint.xml` silences one warning found in a
library, not in the app's code.

The app is launched through `app.hearth.MainActivity`. That name, the intent actions
`app.hearth.action.VOICE` and `app.hearth.action.NEW_CHAT`, the shortcut id `voice` and the widget classes
`app.hearth.widget.NewChatWidget` and `VoiceWidget` are known to launchers outside the app, so they stay
put even when code around them moves. So do `app.hearth.action.OPEN_CHAT`, its extra
`app.hearth.extra.CONVERSATION_ID` and the notification channel `replies`: a notification posted by one version
of the app may be tapped after the next is installed.

**The picture server shares one graphics card with the chat server.** `ImageInteractor` therefore asks the chat
server to unload its models before every picture and the picture server to free its memory after it, also when
the picture fails or is given up on. Keep both when changing it.
