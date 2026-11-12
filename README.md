# Hearth for Android

A native Android chat app for your own [Ollama](https://ollama.com) server. Pick any model the server
offers, chat with streamed replies, search the web through your own SearXNG, and talk hands-free with
replies spoken by your own Kokoro voice. Nothing goes through anyone else's cloud, and your
conversations stay on the device.

This is the Android sibling of [Hearth for iPhone and iPad](https://github.com/cliftonia/Hearth). The
two share a design and a feature list, not code.

- **Platform**: Android 9+, Kotlin, Jetpack Compose
- **Talks to**: Ollama for chat, [SearXNG](https://github.com/searxng/searxng) for web search and
  [Kokoro-FastAPI](https://github.com/remsky/Kokoro-FastAPI) for speech; the last two are optional

## What it does

- **Chat** with any model on the server: streamed Markdown, tables, code blocks, maths shown as readable
  text, folded thinking with a level of Off, Light, Medium or High
- **Web search when it is needed**: a model that can call tools searches on its own, reads the top pages
  it finds and cites its sources; for other models a toggle searches before the reply
- **Memory**: lasting facts you tell a model are kept on the device and carried into every conversation;
  a **private chat** uses none and adds none
- **Voice**: dictation, replies read aloud as they are written, and a hands-free voice conversation
- **Attachments**: photos for models that can see, PDFs and text files for any model
- **Edit and resend**, suggested follow-up questions, custom instructions
- **History**: pin, rename, share as Markdown, delete, search
- **Lock** with fingerprint, face or PIN; light and dark themes; a side-by-side layout on wide screens

## Finding your way round

- **The composer** is a single row at rest. Tap it and it opens out, showing the options for the reply you are
  about to ask for: web search, how hard the model should think, and whether replies are read aloud. Which of
  these appear depends on the model and on the servers you have set.
- **The chat list** slides in from the left. Its **Settings** and **New chat** buttons float at the bottom; the
  dot on Settings is green while your server can be reached.
- **Private chat** is the padlock at the top right of a chat that has not yet begun. Once the chat has begun,
  the padlock only shows that it is private.
- **Settings** hold the server addresses, the voice and its **Hear this voice** button, your memories, the lock
  and the theme. Changes are kept as you make them.

## How it is built

Three Gradle modules, each seeing only the one below it:

| Module | Holds | Depends on |
|---|---|---|
| `app` | Coordinator, presenters, Compose screens, the design system, the phone's own services (speech, files, biometrics) | `domain` |
| `domain` | Models, the chat and speech interactors, stores, the prompt and text transformers | `data` |
| `data` | HTTP services and DTOs for Ollama, SearXNG, Kokoro and web pages | nothing |

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
- **Only web links open.** Links from a model or a search result with any other scheme lead nowhere.

## Building

```sh
cp local.example.properties local.properties   # then fill in your SDK path and server addresses
./gradlew test assembleDebug
```

`local.properties` is ignored by git; your server addresses never enter the repository. The app also
runs with no addresses built in and asks for them in Settings.

## Status

The data and domain modules are covered by unit tests. The presenters and screens are not yet, and
there is no lint or CI configuration. Differences from the iPhone app: dictation stops when you pause
(Android's recogniser takes one utterance at a time), and there is no background keep-alive, so a
reply continues only while Android leaves the app running.
