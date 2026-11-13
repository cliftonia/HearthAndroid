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
  The ember on the voice screen is the example.
- Proportions (`weight(1f)`, a scale of `0.86f`, the stops of a gradient) are not tokens.
- Material's own components (buttons, menus, fields, switches, dialogs) are used as they come. They take their
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
| `mute` | Secondary text, and symbols at rest |
| `line` | Borders and dividers |
| `pine` | The accent: links, chosen options, the main button |
| `pineSoft` | A quiet wash of the accent: chosen rows, notices, suggestions |
| `bubble` | The fill behind the user's messages, code and file chips |
| `onPine` | Text and symbols on `pine` or `danger` |
| `danger` | Errors, and actions that cannot be undone |
| `online` | The server can be reached |

### Type: `HearthTheme.type`

Each token is a complete `TextStyle`. Pass it as `style` and add only a colour (and alignment or a line limit).
Serif is for the brand, titles and headings; the rest is the phone's sans-serif.

| Token | Size / leading | Used for |
|---|---|---|
| `brand` | 22 serif bold | The app's name; a screen's title |
| `display` | 26 / 32 serif | The one large line on an empty screen |
| `heading(level)` | 22, 19, 17 serif | Headings in a reply |
| `reply` | 16 / 24 | Paragraphs of a reply |
| `replyTable` | 14 / 20 | Table cells in a reply |
| `code` | 13 / 19 mono | Code blocks |
| `math` | 19 / 26 serif | Display maths |
| `input`, `inputHint` | 16 / 22, 16 / 24 | The composer's text and its prompt |
| `message` | 16 / 23 | A message the user sent |
| `lead` | 17 / 25 | The caption of a voice conversation |
| `subhead`, `subheadSnug`, `subheadStrong` | 15 | Row labels; multi-line text; a prominent button |
| `callout`, `calloutSnug`, `calloutTight`, `calloutStrong` | 14 | Status lines; thinking; error text; error title |
| `footnote`, `footnoteSnug` | 13 | Group titles and chips; multi-line notices |
| `caption`, `captionSnug` | 12 | Headings in lists and menus; the note under a card |
| `control`, `controlTitle`, `controlDetail` | 14, 16, 12 | Text inside Material's buttons and menu items |

Where no leading is given, the style keeps Material's body leading of 24. `Snug` and `Tight` set the lines
closer for text that wraps. The `control` styles start from Material's label style, so use them only inside a
button or a menu item.

### Spacing: `HearthTheme.spacing`

| Token | dp | |
|---|---|---|
| `xxs` `xs` `s` `m` `l` `xl` `xxl` `xxxl` | 2 4 6 8 10 12 14 16 | The everyday scale: padding inside a component, gaps between its parts |
| `gapS` `gapM` `gapL` `gapXl` `gapXxl` | 18 20 22 28 36 | Between whole blocks on a screen |
| `screen` | 24 | The margin round a screen whose content is centred |
| `chipInset`, `chipInsetTall` | 7, 9 | Above and below the text of a link chip and a file chip |
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
| `composer` | 24 | The composer |
| `pill` | half the height | Chips and buttons with fully rounded ends |
| `circle` | | Round buttons, dots, halos |

### Sizes: `HearthTheme.sizes`

| Token | dp | |
|---|---|---|
| `iconXs` `iconS` `iconM` `iconL` `iconXl` | 14 18 20 22 28 | Symbols, from one tucked into text to one on a voice button |
| `controlXs` `controlS` `controlM` `controlL` `controlXl` | 24 32 36 40 64 | Round controls, from the remove-photo button to the voice buttons |
| `touchTarget` | 48 | The least a finger needs; the height of the floating buttons |
| `markS` `markM` `markL` | 28 56 64 | The app's mark |
| `thumbnailS` `thumbnailL` | 64 96 | A photo in the composer; in a sent message |
| `pictureMaxSide` | 420 | The most room, each way, a picture the app made takes in a reply |
| `statusDot`, `progress` | 8, 14 | The connection dot; the small spinner |
| `fieldMinHeight`, `fieldMaxHeight` | 24, 140 | The composer's field |
| `modelNameMaxWidth`, `chipLabelMaxWidth`, `tableColumn` | 220, 180, 150 | Set widths |
| `drawerWidth`, `wideLayoutMinWidth` | 320, 840 | The chat list; where it starts to sit beside the chat |

### Strokes and elevation: `HearthTheme.strokes`

| Token | dp | |
|---|---|---|
| `hairline` | 1 | Borders and dividers |
| `progress` | 2 | The ring of the small spinner |
| `accent` | 3 | The bar beside a quotation |

There are no elevation tokens because there is no elevation. Nothing in the app casts a shadow: one surface is
told from another by colour (`card` on `paper`) and, where that is not enough, by a `hairline` border in `line`.

### Widgets

A home screen widget is drawn by the launcher from XML, which cannot read `HearthTheme`. Its colours and
measures are repeated in `res/values/widgets.xml` (and `values-night`), and must be kept in step with the tokens.

## Components

Shared components are named `Hearth…` and live beside the tokens. A composable belongs here once it is used
by two features, or in two files of one, and is about looks and not about what a feature does.

| Component | What it is |
|---|---|
| `HearthTheme(isDark) { … }` | Provides the colours, and hands them to Material's components |
| `HearthMark(sideLength)` | The app's mark, drawn in proportion to its side |
| `HearthIconButton(icon, label, tint) { … }` | A borderless button holding one symbol. The label is for screen readers and is required |
| `HearthRoundButton(icon, label, containerColor, contentColor, size, iconSize) { … }` | A filled circle for a screen's main action: send, mute, end |
| `HearthCard(title, footer) { … }` | A titled group of controls on a card, with a note beneath |
| `HearthSwitchRow(label, isOn) { … }` | A label and a switch that act as one control |

Things that look like components but are used by one feature stay with it: the composer's toggles, the
drawer's New chat and Settings buttons, the chips for sources and files.

## Accessibility

- Every button has a label. A symbol that only decorates has `null` for its description.
- Group titles are marked as headings; a row that is a switch is one switch to a screen reader.
- Text sizes are in `sp` and follow the phone's font size.
