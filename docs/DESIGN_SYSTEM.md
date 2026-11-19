# Design system

Everything about how Hearth looks is in `app/src/main/kotlin/app/hearth/designsystem/`. Screens read it
through one object, `HearthTheme`.

## The rule

**No raw colours, text sizes or radii in screens.** Nor spacing, nor the size of a symbol or a control. A screen
says `HearthTheme.spacing.m`, never `8.dp`; `HearthTheme.type.footnote`, never `fontSize = 13.sp`;
`HearthTheme.shapes.card`, never `RoundedCornerShape(16.dp)`.

If the value you need is not there, add a token and say what it is for. Do not reach for the nearest one:
several tokens differ by a point or two on purpose, and each was kept as it was drawn.

The exceptions are few and named:

- A measure that belongs to one drawing and nothing else may be a private constant beside it, with a comment.
  The ember on the voice screen and the dots in the held composer are the examples.
- Proportions (`weight(1f)`, a scale of `0.86f`, the stops of a gradient) are not tokens.
- Material's own components (buttons, menus, fields, switches, dialogs, snackbars) are used as they come. They take their
  colours from `HearthTheme`, which fills in the Material colour scheme.

## Tokens

### Colours: `HearthTheme.colors`

Pine green on stone paper, in a light and a dark form. This is the only part of the theme that changes with
the appearance, so it is the only part read from the composition.

| Token | Used for |
|---|---|
| `paper` | The background of every screen |
| `card` | Surfaces raised off the paper: settings cards, the composer, banners |
| `ink` | Text and symbols meant to be read |
| `mute` | Secondary text, and symbols at rest. Light `#5F6A65`, dark enough for small text on `paper` and `card` |
| `line` | Borders and dividers |
| `pine` | The accent: links, chosen options, the main button |
| `pineSoft` | A quiet wash of the accent: chosen rows, notices, suggestions |
| `bubble` | The fill behind the user's messages, code and file chips |
| `onPine` | Text and symbols on `pine` |
| `danger` | Errors, and actions that cannot be undone, in text and symbols. Light `#B0472F`, dark enough for small text on `paper` and `card` |
| `dangerFill` | The fill of a button that cannot be undone, such as ending a voice conversation. Light `#B0472F`, dark `#B54E3A`: the dark `danger` is a text colour, too light for white on it |
| `onDangerFill` | Text and symbols on `dangerFill`: white in both appearances |
| `dangerSoft` | A quiet wash of `danger`, with `ink` on it: Material's error container. Light `#F6E1DB`, dark `#3D2420` |
| `online` | The server can be reached: the status dot only. It is too light for text, so a check that succeeded is worded in `pine` |
| `codeKeyword` | The words a language reserves, in a block of code. Light `#2C6B5A`, dark `#6FC2A8` |
| `codeString` | Text in quotes, in a block of code. Light `#8A4B12`, dark `#E0B15A` |
| `codeNumber` | Numbers, in a block of code. Light `#3B5BA5`, dark `#8FB0F0` |

The three code colours are the iPhone app's, and each is dark or light enough for small text on `bubble` and on
`card` in both appearances. A comment in code is `mute`, and everything else `ink`.

### Type: `HearthTheme.type`

Each token is a complete `TextStyle`. Pass it as `style` and add only a colour (and alignment or a line limit).
Serif is for the brand, titles and headings; the rest is the phone's sans-serif.

| Token | Size / leading | Used for |
|---|---|---|
| `brand` | 22 serif bold | The app's name; a screen's title |
| `display` | 26 / 32 serif | The one large line on an empty screen |
| `heading(level)` | 22, 19, 17 serif | Headings in a reply |
| `reply` | 16 / 26 | Paragraphs of a reply, and the cells of a table in one, whose header row is semibold |
| `code` | 13 / 19 mono | Code blocks |
| `math` | 19 / 26 serif | Display maths |
| `input`, `inputHint` | 16 / 22, 16 / 24 | The composer's text and its prompt |
| `message` | 16 / 23 | A message the user sent |
| `lead` | 17 / 25 | The caption of a voice conversation |
| `title` | 17 | The title of a sheet |
| `listRow` | 16 | The name of a chat or a project in the chat list, of a source in the sources sheet, and of a row in the sheet the composer's plus button opens |
| `subhead`, `subheadSnug`, `subheadStrong` | 15 | Row labels; multi-line text; a prominent button |
| `callout`, `calloutSnug`, `calloutTight`, `calloutStrong` | 14 | Status lines; thinking; error text; error title |
| `footnote`, `footnoteStrong`, `footnoteSnug` | 13 | Group titles and chips; the name on a chip that is a button; multi-line notices |
| `caption`, `captionSnug` | 12 | Headings in lists and menus; the note under a card |
| `avatar`, `avatarLarge` | 17, 34 / 40 | The initials on the user's avatar: in a row; at the head of the profile page |
| `control` | 14 | Text inside Material's buttons and menu items |

Where no leading is given, the style keeps Material's body leading of 24. `Snug` and `Tight` set the lines
closer for text that wraps. `control` starts from Material's label style, so use it only inside a button or a
menu item.

### Spacing: `HearthTheme.spacing`

| Token | dp | |
|---|---|---|
| `xxs` `xs` `s` `m` `l` `xl` `xxl` `xxxl` | 2 4 6 8 10 12 14 16 | The everyday scale: padding inside a component, gaps between its parts |
| `gapS` `gapM` `gapL` `gapXl` `gapXxl` | 18 20 22 28 36 | Between whole blocks on a screen |
| `screen` | 24 | The margin round a screen whose content is centred |
| `chipInset`, `chipInsetTall` | 7, 9 | Above and below the text of a picture's buttons and a file chip |
| `badgeInset` | 9 | A status dot, in from the corner of its button |
| `messageIndent` | 48 | How far the user's messages stop short of the left edge |
| `floatingClearance` | 96 | Under a list, so the last row clears floating buttons |

### Shapes: `HearthTheme.shapes`

| Token | Radius | Rounds |
|---|---|---|
| `bar` | 2 | A thin accent bar |
| `control` | 8 | A tappable line of text |
| `inset` | 10 | Notices and tables inside a reply |
| `tile` | 12 | Thumbnails, file chips, list rows, code, thinking |
| `field` | 14 | Search fields, banners, suggested questions |
| `card` | 16 | A group of settings |
| `bubble` | 20 | A message the user sent |
| `preview` | 20 | The live picture of the camera in a voice conversation |
| `composer` | 24 | The composer, and the cards of the sheet its plus button opens |
| `pill` | half the height | Chips and buttons with fully rounded ends |
| `circle` | | Round buttons, dots, halos |

### Sizes: `HearthTheme.sizes`

| Token | dp | |
|---|---|---|
| `iconXs` `iconS` `iconM` `iconL` `iconXl` | 14 18 20 22 28 | Symbols, from one tucked into text to one on a voice button |
| `controlXs` `controlS` `controlM` `controlL` `controlXl` | 24 32 36 40 64 | Round controls, from the remove-photo button to the voice buttons |
| `touchTarget` | 48 | The least a finger needs; the height of the floating buttons |
| `markS` `markM` `markL` | 28 40 64 | The app's mark |
| `sourceMark`, `sourceMarkOverlap` | 20, 6 | The circle that stands for a source's site, and how far each lies over the one before |
| `avatarS` `avatarL` | 44 96 | The user's avatar: in a row of the settings; at the head of the profile page |
| `thumbnailS` `thumbnailL` | 64 96 | A photo in the composer; in a sent message |
| `pictureMaxSide` | 420 | The most room, each way, a picture the app made takes in a reply |
| `statusDot`, `progress` | 8, 14 | The connection dot; the small spinner |
| `fieldMinHeight`, `fieldMaxHeight` | 24, 140 | The composer's field |
| `chipLabelMaxWidth`, `tableColumn`, `sourceNumberWidth` | 180, 150, 22 | Set widths |
| `drawerWidth`, `wideLayoutMinWidth` | 320, 840 | The chat list; where it starts to sit beside the chat |
| `fullScreenDialogMaxWidth`, `dialogMaxWidth` | 600, 560 | Below the first an editor is a full-screen dialog; above it, a dialog in the middle no wider than the second |
| `readableMaxWidth` | 840 | The widest the messages, the composer and the settings are drawn, centred, on a wide window |
| `canvasCardMaxWidth`, `canvasSwitchWidth`, `canvasColumnMinWidth` | 420, 200, 360 | The card of a piece of work in a reply; the canvas's Preview and Code switch; the least the chat and the canvas each need for the chat list to stay beside them |

### Strokes and elevation: `HearthTheme.strokes`

| Token | dp | |
|---|---|---|
| `hairline` | 1 | Borders and dividers |
| `progress` | 2 | The ring of the small spinner |
| `accent` | 3 | The bar beside a quotation |
| `floatingShadow` | 3 | The lift of a control that floats over the conversation: the pill at the top of a chat |

`floatingShadow` is the only elevation. Everywhere else one surface is told from another by colour (`card` on
`paper`) and, where that is not enough, by a `hairline` border in `line`.

### Widgets

A home screen widget is drawn by the launcher from XML, which cannot read `HearthTheme`. Its colours and
measures are repeated in `res/values/widgets.xml` (and `values-night`), and must be kept in step with the tokens.

## Components

Shared components are named `Hearth…` and live beside the tokens. A composable belongs here once it is used
by two features, or in two files of one, and is about looks and not about what a feature does.

| Component | What it is |
|---|---|
| `HearthTheme(isDark) { … }` | Provides the colours, and hands them to Material's components, the inverse and error-container roles included, so a snackbar is ink on paper with the other appearance's pine for its action |
| `HearthMark(sideLength)` | The app's mark, drawn in proportion to its side |
| `HearthIcons` | The symbols the app draws itself, because Material's set has none like them: `NewChat` |
| `HearthIconButton(icon, label, tint) { … }` | A borderless button holding one symbol. The label is for screen readers and is required |
| `HearthRoundButton(icon, label, containerColor, contentColor, size, iconSize) { … }` | A filled circle for a screen's main action: send, mute, end |
| `HearthCircleButton(icon, label, tint, containerColor, isOn) { … }` | One symbol in a floating circle the size of a touch target: the frosted card colour, a hairline edge and a soft shadow, drawn like the composer. The chat list and private chat buttons at the top of a chat. `containerColor` fills it to show it is switched on; `isOn` makes it a switch to a screen reader |
| `HearthSmallIconButton(icon, label, sideLength) { … }` | A symbol drawn smaller than a finger (taking a photo or a file off the message) that still answers to, and is heard over, a whole touch target, without taking more room |
| `HearthVersionStepper(labels, hasPrevious, hasNext) { step -> … }` | The arrows either side of "2/3" that step through the versions of a reply or of a piece in the canvas, with a selection haptic each step |
| `HearthChoiceCard { … }`, `HearthChoiceRow(title, isSelected, isLast, detail, icon, tint) { … }` | A card of choices, heard as one group, and a row of it: a symbol, the words, a line under them and a tick for the one chosen. `isSelected = null` makes a row an action rather than a choice. The project picker |
| `HearthDropdownField(label, value, supportingText, valueColor) { close -> … }` | One choice from a list as Material's exposed dropdown: an outlined field with its label inside and the ▼ that opens the menu. `valueColor` draws the value in a colour of its own, as `pine` while research is on. The effort in the model sheet and a task's model |
| `HearthCard(title, footer) { … }` | A group of controls on a card, with a title above and a note beneath, either of which may be left out |
| `HearthSwitchRow(label, isOn, detail) { … }` | A label and a switch that act as one control, at least `touchTarget` high, with a line beneath the label on what it does, where given |
| `HearthPageRow(description, onClick, enabled) { … }` | A settings row that opens a page, at least `touchTarget` high, with no arrow, as Android lists settings; TalkBack hears it as one button, by its description. Unavailable, it is heard as such |
| `HearthPageRow(title, status, onClick, enabled)` | The common page row: a title and beneath it how things stand on the page or, unavailable, the reason. Heard as "Memory, On, 12 saved" |
| `HearthAvatar(photo, initials, diameter, isLarge)` | The user as a circle: their photo, else their initials, else a figure |
| `HearthSheetHeader(title)` | The title of a bottom sheet, at the leading edge under its handle. No close button: the handle, the scrim and back close it. The sheet of the composer's plus button has a heading of its own instead, with a close button: it mirrors the iPhone app's sheet and holds switches, so it is not put away by choosing |
| `HearthSegmentedChoice(options, selected) { value -> … }` | A few short choices: Material's segmented buttons without the tick, each label on one line; with large text, a list of radio buttons. The theme and how often a task repeats |
| `isLargeFont()` | Whether the phone's text is set large (1.3 and up). A line cut short at the usual size is given a second line: drawer titles, the model sheet's rows |
| `HearthPermissionNotice(icon, message, actionTitle) { … }` | A card saying that the phone is holding something back, with a button that leads to where that is changed; the button is left out where there is nowhere to send the user |

### Motion

`sharedAxisX(forward)` and `fadeThrough()` in `HearthMotion.kt` are Material's transitions between screens: the shared
x axis between the settings' list and its pages, and the fade through between the chat, the settings and a voice
conversation. Compose plays them at the phone's animator duration scale, so they go when animations are turned off.

### Haptics

How the app feels under the finger is kept beside how it looks, and for the same reason: so that it is the same
everywhere.

| | What it is |
|---|---|
| `Haptics.play(kind)` | Plays one tap, unless haptics are switched off in Settings. Every haptic in the app goes through it |
| `HapticEffect(kind, trigger, condition)` | Plays a tap each time a value changes, when the condition holds for the change |
| `HapticsHost(isEnabled)` | Placed once by the window: gives `Haptics` a view to play through and keeps it in step with the switch |
| `HapticKind` | `LIGHT` sent, pinned or switched; `SOFT` something finished by itself; `SELECTION` a choice moved; `SUCCESS` done and now there; `WARNING` something is destroyed; `ERROR` something went wrong |

A haptic marks a moment, never a stretch of time: one as a reply finishes, none while it streams.

Things that look like components but are used by one feature stay with it: the composer's toggles and the button
beside the picture chip that adds a photo, the drawer's New chat and Settings buttons, the chips for files, the
list and sheet of a reply's sources and the line above a reply that searched the web, the card of a piece of
work in a reply, the heading, cards, rows (one ticked in `pine` while it is on, as Make a picture is) and switch
rows of the sheet the plus button opens, and a block of code with the sheet that shows it full size.
The canvas's switch is Material's segmented button as it comes, and its code is set in `type.code` on `card`,
with the line numbers in `mute`.

## Accessibility

- Every button has a label. A symbol that only decorates has `null` for its description. A button whose text says
  what it does needs no description of its own.
- Everything that can be tapped answers to at least `touchTarget` each way, however small it is drawn
  (`minimumInteractiveComponentSize()`, or `HearthSmallIconButton`).
- Each message is a group of its own, heard first as "You" or "Hearth". Headings in a reply are headings, and its
  links are link annotations, which TalkBack lists and opens. A sent message's menu is a custom action as well as a
  long press.
- Group titles are marked as headings; a row that is a switch is one switch to a screen reader, and a row that
  leads to a page is one button, heard with how things stand on that page.
- Results that arrive by themselves (a connection test, a voice sample that could not play, a problem on a page, the
  chat's error banner, the line saying what a reply is doing) are polite live regions, so a screen reader announces
  them.
- What only decorates is hidden from a screen reader: the avatar beside a name, the dots that swell with a voice,
  the line numbers beside code in the canvas. The card of a piece of work is one button that says what it is and
  where it leads.
- Text sizes are in `sp` and follow the phone's font size.
