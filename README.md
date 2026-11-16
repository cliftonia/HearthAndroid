# Hearth for Android

A native Android chat app for your own [Ollama](https://ollama.com) server. Pick any model the server
offers, chat with streamed replies, search the web through your own SearXNG, and talk hands-free with
replies spoken by your own Kokoro voice, and have pictures made by your own ComfyUI. Nothing goes through anyone else's cloud, and your
conversations stay on the device.

<p align="center">
  <img src="docs/chat.png" alt="A reply about Kotlin coroutines with suggested follow-up questions" width="24%">
  <img src="docs/picture.png" alt="A picture of a lighthouse at sunset, made from a description, with Save and Share" width="24%">
  <img src="docs/picture-change.png" alt="The same lighthouse changed to a snowy winter night by a follow-up message" width="24%">
  <img src="docs/drawer.png" alt="The drawer of conversations, with search, Settings and New chat" width="24%">
</p>

This is the Android sibling of [Hearth for iPhone and iPad](https://github.com/cliftonia/Hearth). The
two share a design and a feature list, not code.

- **Platform**: Android 9+, Kotlin, Jetpack Compose
- **Talks to**: Ollama for chat, [SearXNG](https://github.com/searxng/searxng) for web search and
  [Kokoro-FastAPI](https://github.com/remsky/Kokoro-FastAPI) for speech and
  [ComfyUI](https://github.com/comfyanonymous/ComfyUI) for pictures; all but the first are optional

## What it does

- **Chat** with any model on the server: streamed Markdown, tables, code blocks, maths shown as readable
  text, folded thinking with a level of Off, Light, Medium or High
- **The canvas**: a web page, a drawing, a document or a long block of code in a reply is folded into a card, and the
  card opens it on its own: a page or a drawing shown working, a document laid out, the code with its lines numbered,
  with Copy, Share and a way to step back through earlier versions when you ask for changes
- **Web search when it is needed**: a model that can call tools searches on its own, reads the top pages
  it finds and cites its sources; for other models a toggle searches before the reply
- **Deep research**: choose it as the effort in the model sheet, after Light, Medium and High, and ask a question.
  Hearth has the chat's own model plan a handful of searches, runs them on your SearXNG, reads a dozen of the pages
  found, takes notes on each, looks once more for anything important that is missing, and writes a report that cites
  its sources as `[1]`, `[2]`, with the numbered sources listed beneath it. The steps show while it works, Stop ends
  it at once, and Try again researches again. The choice stays with its chat and is never saved, so a new chat is
  never researched by accident
- **Memory**: lasting facts you tell a model are kept on the device and carried into every conversation;
  a **private chat** uses none and adds none
- **Voice**: dictation by the microphone button or by holding the composer down, replies read aloud as they are
  written, and a hands-free voice conversation
- **Voice with the camera**: turn the camera on in a voice conversation and one still picture goes with each thing
  you say, so that you can ask about what is in front of you. Nothing is recorded
- **Attachments**: photos for models that can see, PDFs and text files for any model
- **Pictures**: make one from a description, change or enlarge a photo, and carry on from the last picture
  with words such as "make it a snowy night"; save or share what is made
- **Share to Hearth** from other apps: words, links, pictures, PDFs and text files land in the composer
- **Edit and resend**, **Try again** with the earlier replies kept to step back through, suggested follow-up
  questions
- **Profile**: your name and what you like to be called, a photo, and standing instructions for every conversation.
  A new chat greets you by name, a little differently each time, and the model is told who it is talking to
- **Notifications**: told on the phone itself when a reply or a picture finishes while Hearth is not on screen,
  or when a scheduled task's answer arrives; a private chat's notification says only that a reply is ready
- **Scheduled tasks**: a question and a time ("Every day at 7:30", "Every Friday at 18:00", "Once, 20 Nov at
  9:00"); your Hearth Hub asks a cloud model at that time, searching the web first if you like, and files the answer
  as a new chat marked "Scheduled task", which reaches every device through sync. They need sync
- **Vibration**: a light tap when a message is sent, a reply finishes or something is switched, with one switch
  that silences them all (not offered on a device with nothing to tap with)
- **Projects**: gather chats round a subject, with instructions and reference files (PDFs and text files)
  that every chat in the project is given
- **History**: pin, rename, share as Markdown, delete, search
- **Lock** with fingerprint, face or screen lock, named for what the phone has set up, and asked for when the lock is
  turned on, not off; light and dark themes; a side-by-side layout on wide screens
- **Sync** through a Hearth Hub of your own (a small server, kept in a separate repository): ordinary chats, projects
  memories and scheduled tasks kept in step with your other phones, tablets and Macs running Hearth, on Android or Apple's systems.
  Off until you turn it on; private chats, settings, addresses and your profile never leave the phone
- **Erase all data on this device**: there are no accounts, so one button removes everything the app holds. With
  sync on, erasing removes nothing anywhere else: it turns sync off first
- **Home screen widgets** for a new chat and for a voice conversation

## Finding your way round

- **The composer** is a single row at rest. Tap it and it opens out, showing the options for the reply you are
  about to ask for: the model, web search, and whether replies are read aloud. Which of these appear depends on
  the model and on the servers you have set. Tap the model's name to choose another, and the effort that goes into
  a reply: how hard the model should think, or **Deep research**. While research is chosen the model's name is
  followed by "Deep research" in green and the field reads "Ask a question to research". Without a search address
  it is listed but cannot be chosen.
- **Hold the composer** while it is at rest and empty to talk to it: it listens for as long as you hold, shows how
  loud it hears you, and when you let go your words are in the message field for you to read, change and send.
  Nothing is sent by itself. Sliding your finger off, or leaving the app, stops it too.
- **The top of a chat** holds the private chat button until the first message is sent. From then on it holds a
  new chat button and a menu to pin, share, rename or delete the chat, or to add it to a project. A chat in a
  project shows the project's name beneath.
- **The chat list** slides in from the left. Its **Settings** and **New chat** buttons float at the bottom; the
  dot on Settings is green while your server can be reached. Hold a chat, or tap its dots, to pin, rename,
  share or delete it, or to add it to a project.
- **Projects** are listed at the top of the chat list, above **New project**. Tap one to see only its chats;
  **New chat** then starts a chat in that project, the button beside its name opens its settings, and **All chats**
  leads back. Deleting a project keeps its chats.
- **A voice conversation** has three buttons: the camera, mute and end. The camera button shows what the camera
  sees on a card above the ember, with a button on the card to change between the cameras where the phone has one
  on each side. While it is on, each thing you say is sent with one picture taken as you finish speaking, and that
  picture is kept on your message in the chat like any attached photo. The phone asks for leave to use the camera
  the first time the button is turned on; if it is refused, a notice leads to the phone's Settings and the
  conversation carries on without it. The camera goes off by its button, when the conversation ends and when you
  leave the app, and is off each time a conversation opens. For a model that cannot see pictures the button is
  shown faint with a line saying why; the model is never changed for you.
- **The canvas** opens from the card a reply shows in place of a page, a drawing, a document or a block of code of
  fifteen lines or more; shorter code stays in the reply as before. While the reply is still being written the card
  says so and counts the lines. In the canvas, **Preview** and **Code** switch between the thing itself and its
  text (code has only its text), the arrows step between versions ("2 of 3") when a later reply wrote it again, and
  the canvas follows the newest version as it is written unless you have stepped back. On a wide screen it sits
  beside the chat, and the chat list gives way while there is not room for all three; on a phone it is a page over
  the chat. Its close button, the back button, a new chat and another chat all put it away. Nothing about it is
  saved: it is read out of the reply each time.
- **Private chat** is the padlock at the top right of a chat that has not yet begun. Once the chat has begun,
  the padlock only shows that it is private.
- **Settings** begin with your **Profile**, a page of its own: your photo, names and instructions. Below it are
  groups of rows, each saying how things stand on the page it opens: **Chats** (**Memory**, the follow-up questions
  switch, **Web search**, **Scheduled tasks**), **Voice & pictures** (**Voice**, shown but unavailable until there is a
  speech server, and **Pictures**), **Notifications**, **Appearance & privacy** (the theme, vibration and the lock) and
  **Servers & sync** (**Servers**, with the four addresses and **Test connection** beneath them, and **Sync**). Last,
  in red, is **Erase all data on this device**, which also turns sync off; the hub and your other devices keep their
  copies. Changes are kept as you make them; a changed chat server address is tested once you leave the Servers page.
- **Schedule a task** in the composer's **+** menu opens a new scheduled task asking what you have written, leaving
  the message where it was; without sync it explains that tasks need it and leads to **Set up sync**.
- **Sync** has the hub's address, an optional access key, the **Sync this device** switch (which needs an address
  first), **Sync now**, **Test connection** and a status line saying when it last synced or what went wrong. Once on,
  it syncs when Hearth opens, when it comes back to the front, every minute while it stays there, a few seconds
  after a change, at once when a key is saved or the hub changes, when asked, and about every fifteen minutes in
  the background through the phone's job scheduler, one sync at a time. A chat open on screen takes in a change made elsewhere, or gives way to a new chat if it was deleted
  elsewhere; a reply still being written is never replaced, and is sent once it is finished.
- **Scheduled tasks** lists each task with its schedule, how its last run went and a switch, and leads to an editor:
  title, what to ask, the model (Automatic, the default, leaves it to the hub; otherwise only models whose names end in `:cloud` or `-cloud` are offered), every day,
  every week or once, the time, the day or the date, **Search the web first**, and **Delete task**. Times are kept
  in the phone's time zone. Without sync the page says why and leads to the Sync page, and nothing can be added.
- **Notifications** ask the phone for leave only when you turn a switch on. If the phone refuses,
  the switch stays on and the page shows where leave is given. Tapping a notification opens its chat.

## How it is built

Three Gradle modules, each seeing only the one below it:

| Module | Holds | Depends on |
|---|---|---|
| `app` | Coordinator, presenters, Compose screens, the design system, the phone's own services (speech, the camera, files, biometrics, notifications) | `domain` |
| `domain` | Models, the chat, research, speech, picture, project, sync and erase interactors, stores, the prompt, research and text transformers, the greeting, notification and sync planners | `data` |
| `data` | HTTP services and DTOs for Ollama, SearXNG, Kokoro, ComfyUI, Hearth Hub and web pages | nothing |

Presenters own their state as a `StateFlow` and tell the coordinator what the user asked for through a
delegate. Dependencies are passed as closures and small provider classes; there is no injection framework.
Inside `app` each screen has a package of its own, holding its presenter beside its composables.

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): the modules, the presenter and coordinator pattern, where
  things live and how to add a screen
- [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md): the colour, type, spacing and shape tokens, the shared
  components, and the rule that screens use nothing else

### What is guarded

- **Web pages are untrusted.** Only public HTTPS pages are fetched; names that resolve to private,
  local or tailnet addresses are refused, and the connection is made to the addresses that were checked.
- **Search results cannot give orders.** They reach the model inside a fence whose marker is made up
  afresh for every search, marked as material to weigh, not instructions.
- **A web page cannot plant a memory.** Memory closes for the whole conversation once it holds a web
  source, a file or a photo, and a model cannot change memories the user wrote.
- **Nor can a project's file.** Reference files reach the model inside a fence of the same kind, marked as
  material and not instructions, and a project that has any closes memory for its chats.
- **Research is under the same rules, and bounded.** The pages a report is written from are fetched through the
  same guarded path as a search's pages, and their text and the notes taken from it reach the model inside a fence
  made up for the run. At most 5 planned searches and 3 more to fill a gap, 12 pages and then 4 more, no more than
  2 pages from one site, 4 pages fetched at once, 12 seconds for a page, 6,000 characters read from each and 24,000
  characters of notes handed to the writer. That is about 15 to 20 requests to the model, all to the one the chat
  already uses. Pages are read by the phone, so those sites see the phone's address. If no page can be read, or
  none says anything about the question, it says so and writes no report. The log holds counts only.
- **A page a model wrote is shut in.** The canvas shows it in a web view that is given the page as text, with no
  address of its own, so it has no files, cookies or storage. Its scripts run, since a page is made of them, but
  every load from the network is refused (requests, pictures, scripts, styles, frames, `fetch`, sockets, beacons),
  every navigation is cancelled, so a link in it opens nothing, here or in the browser, and it is given no window,
  camera, microphone, place, file or download. Peer connections (WebRTC) are taken away from the page and from
  every frame it makes, the moment the frame is made, so it cannot send anything that way either; a packet capture
  on the emulator shows none leaving. Where that cannot be done it fails closed: no frame is given a `sandbox`,
  which would put it out of reach, and a frame that still cannot be reached is taken out of the page. A sandboxed
  frame in a closed shadow root written in the page's own markup cannot be found; the web view seals it where it
  can run the script in every frame, as it measured. **Two things are not yet shut off**, because the phone's web view offers no
  way to do it: a page can still have a name looked up (`dns-prefetch`) and can open a bare connection that
  carries no request (`preconnect`). Neither lets it load anything, but each can carry a little out. See "The
  canvas" in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- **Only web links open.** Links from a model or a search result with any other scheme lead nowhere.
- **A name stays a name.** The name in your profile reaches the model on one line, in quotation marks it cannot
  close, so it cannot pass itself off as instructions.
- **The camera takes one picture at a time.** Nothing but the live picture on screen is fed while it is on; a
  still is taken only as a spoken turn ends, with no sound, and no video is ever recorded or sent. Turning it off
  closes the camera itself. A picture closes memory for the conversation, as any photo does.
- **Sync sends only what it should.** Private chats never reach the hub, not even as a deletion; nor do settings,
  addresses, the profile or the token. The token is sealed with a key the phone's keystore holds and goes only in
  the request's `Authorization` header. A source from another device is kept only if it is an `http` or `https`
  link. Erasing the phone sends no deletion. The log holds counts and kinds of failure only.
- **A private chat stays private on the lock screen.** Its notification carries neither its title nor a word of
  the reply.

## Building

```sh
cp local.example.properties local.properties   # then fill in your SDK path and server addresses
./gradlew test lintDebug assembleDebug
```

`local.properties` is ignored by git; your server addresses never enter the repository. `hearth.hubAddress` there
fills in the Sync page's address on first launch; sync stays off until it is turned on. The app also
runs with no addresses built in and asks for them in Settings.

## Status

The domain and data modules are covered by unit tests, and so are most presenters' rules; the screens are not.
`docs/ARCHITECTURE.md` says where each part lives, and the tests beside it say what is covered. Every push is
built, tested and linted by `.github/workflows/android.yml`, which needs no `local.properties`.

Test vectors shared with the iPhone app and Hearth Hub live in the iPhone repository's `Spec/` folder. Run
`tools/sync-spec.sh` after it changes: it copies the folder into the test resources here, and `SpecCopyTests` fails
while the copy differs (it is skipped where `../Hearth/Spec` is not beside this repository).

Differences from the iPhone app: the background sync runs when Android's job scheduler lets it, about every
fifteen minutes at best and only with a network. Dictation stops when you pause (Android's recogniser takes one utterance at a
time), so a held composer stops listening at the first pause as well; and there is no background keep-alive, so a
reply continues, and its notification is posted, only while Android leaves the app running; that holds for
research too, which takes longer than a reply. A photo sent to a model, from the camera of a voice conversation as
from anywhere else, is at most 1568 pixels on its longest side, where the iPhone app sends 1280. The canvas's page
is shut in by the means Android has, which differ from the iPhone's and leave the two gaps listed under "What is
guarded"; and turning the phone round starts a page in the canvas afresh.
