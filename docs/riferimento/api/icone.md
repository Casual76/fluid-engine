<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/icone.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Icone (FluidIcons)

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidIcon.kt`

- `val LocalFluidIconSize: ProvidableCompositionLocal<Dp>` — La taglia che una [FluidIcon] senza `size` prende dal posto in cui sta.
- `@Composable fun FluidIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = LocalFluidIconSize.current)` — Un'icona che prende **la taglia dal posto in cui sta** ([LocalFluidIconSize]) e **l'inchiostro dal contenuto** (`LocalContentColor`, come `Icon` di Material).

## `engine-ui/ui/fluid/FluidSymbolEffect.kt`

- `class FluidSymbolEffect private constructor(internal val kind: Int, internal val variant: Int, val speed: Float, val repeatCount: Int, internal val pivotX: Float, internal val pivotY: Float)` — Un effetto dei simboli, dal vocabolario di SF Symbols: si applica a un'icona con [fluidSymbolEffect] (una volta, a ogni cambio di un valore), con [fluidSymbolEffectWhile] (finché è attivo), o dentro [FluidSymbol], che sa anche il colore variabile per strati, la sostituzione e Draw On / Draw Off.
  - `fun speed(factor: Float): FluidSymbolEffect` — Lo stesso effetto alla velocità [factor] (`.speed` di Apple).
  - `fun repeat(count: Int): FluidSymbolEffect` — Lo stesso effetto ripetuto [count] volte a ogni colpo (`.repeat` di Apple).
  - `companion object`
    - `val Bounce: FluidSymbolEffect` — Il rimbalzo verso l'alto (`.bounce`): il simbolo cresce del 25 %, scende sotto la taglia e si posa, 0,6 s.
    - `val BounceDown: FluidSymbolEffect` — Il rimbalzo verso il basso (`.bounce.down`): si stringe e torna.
    - `val Pulse: FluidSymbolEffect` — La pulsazione (`.pulse`): l'opacità scende al 48 % e torna, 2 s.
    - `val Wiggle: FluidSymbolEffect` — Il dondolio in avanti, nel verso della lettura (`.wiggle.forward`): 0,9 s, picco al 7 % della taglia.
    - `val WiggleUp: FluidSymbolEffect` — Il dondolio in alto e in basso (`.wiggle.up`).
    - `val WiggleClockwise: FluidSymbolEffect` — Il dondolio che ruota attorno al centro in senso orario (`.wiggle.clockwise`): ±10°.
    - `fun wiggleRotation(clockwise: Boolean = true, pivotX: Float = 0.5f, pivotY: Float = 0.5f): FluidSymbolEffect` — Il dondolio che ruota attorno a un perno ([pivotX], [pivotY], in frazione del riquadro): la campanella si appende in alto (`pivotY = 0,12`). [clockwise] falso parte in senso antiorario.
    - `val Rotate: FluidSymbolEffect` — Un giro in senso orario (`.rotate`), 1,9 s; attivo, gira finché lo è.
    - `val RotateCounterClockwise: FluidSymbolEffect` — Un giro in senso antiorario (`.rotate.counterClockwise`).
    - `val Breathe: FluidSymbolEffect` — Il respiro (`.breathe`): +23 % e schiarito al 65 %, un ciclo di 3 s.
    - `val VariableColor: FluidSymbolEffect` — Il colore variabile «a uno a uno» (`.variableColor.iterative`): una tacca accesa per volta.
    - `val VariableColorCumulative: FluidSymbolEffect` — Il colore variabile «a riempire» (`.variableColor.cumulative`): le tacche restano accese.
- `fun Modifier.fluidSymbolEffect(effect: FluidSymbolEffect, trigger: Any?): Modifier` — Suona [effect] sul contenuto (un'icona, un'immagine) ogni volta che [trigger] cambia, come `symbolEffect(_:value:)` di SwiftUI: la prima composizione non suona. Lavora sul simbolo intero (scala, spostamento, rotazione, opacità nel livello del contenuto): il contenuto non si ridisegna e non si ricompone. Il colore variabile per strati, la sostituzione e Draw On / Off sono in [FluidSymbol];
- `fun Modifier.fluidSymbolEffectWhile(effect: FluidSymbolEffect, active: Boolean): Modifier` — Tiene [effect] in corso finché [active] è vero, come `symbolEffect(_:isActive:)`: la sincronizzazione che gira, il microfono che respira. Spento, finisce il ciclo in corso e si ferma a riposo (la rotazione finisce il giro frenando). Fuori dallo schermo il giro dei fotogrammi si ferma col nodo;
- `@Composable fun FluidSymbol(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = LocalFluidIconSize.current, effect: FluidSymbolEffect? = null, trigger: Any? = null, active: Boolean = false, drawn: Boolean = true)` — Un'icona che sa i suoi strati: il simbolo di SF Symbols per le icone di [dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons]. Oltre a ciò che fa [fluidSymbolEffect] sa:

## `engine-ui/ui/fluid/icons/FluidIcons.kt`

- `object FluidIcons` — Le icone dell'engine: Material Symbols arrotondate a peso 500, al tratto.
  - `object Filled` — Le stesse sagome a riempimento pieno, solo per le icone che ne hanno bisogno: la scheda della tab bar (in Apple la tab bar e' piena e le barre al tratto, R6), la forma «accesa» di uno stato (preferito, scelto) e i comandi di riproduzione. Nelle barre si usa la forma al tratto.

## Il set: 175 icone al tratto, 31 anche piene

Dai file generati da `tools/icons/gen_fluid_icons.py` (`FluidIconsFilled.kt`: 31; `FluidIconsOutline.kt`: 175), una riga per icona invece che una per dichiarazione. Ogni riga vale la proprietà al tratto `val FluidIcons.<icona>: ImageVector` e, quando la terza colonna non è vuota, anche quella piena `val FluidIcons.Filled.<icona>: ImageVector`, con l'uso per cui la piena esiste. Si importano per nome (`import dev.antigravity.fluidengine.ui.fluid.icons.<icona>`): lo stesso import porta tutte e due. La seconda colonna è il nome in Material Symbols, quello da cercare partendo da un'icona Material; la corrispondenza con le icone che le app usano oggi è in `tools/icons/corrispondenza.md`.

| icona | Material Symbols | piena |
|---|---|---|
| `AccountBalance` | `account_balance` |  |
| `Add` | `add` |  |
| `Android` | `android` |  |
| `Animation` | `animation` | `Filled.Animation` · tab bar |
| `Apps` | `apps` | `Filled.Apps` · tab bar |
| `Archive` | `archive` |  |
| `ArrowBack` | `arrow_back` |  |
| `ArrowBackIos` | `arrow_back_ios` |  |
| `ArrowBackIosNew` | `arrow_back_ios_new` |  |
| `ArrowDownward` | `arrow_downward` |  |
| `ArrowForward` | `arrow_forward` |  |
| `ArrowUpward` | `arrow_upward` |  |
| `AttachFile` | `attach_file` |  |
| `AudioFile` | `audio_file` |  |
| `AutoAwesome` | `auto_awesome` | `Filled.AutoAwesome` · tab bar |
| `Backpack` | `backpack` | `Filled.Backpack` · tab bar |
| `Bedtime` | `bedtime` |  |
| `Bolt` | `bolt` |  |
| `Calculate` | `calculate` |  |
| `CalendarMonth` | `calendar_month` | `Filled.CalendarMonth` · tab bar |
| `Campaign` | `campaign` | `Filled.Campaign` · tab bar |
| `Check` | `check` |  |
| `CheckBox` | `check_box` |  |
| `CheckBoxOutlineBlank` | `check_box_outline_blank` |  |
| `CheckCircle` | `check_circle` | `Filled.CheckCircle` · scelta |
| `Checklist` | `checklist` |  |
| `ChevronLeft` | `chevron_left` |  |
| `ChevronRight` | `chevron_right` |  |
| `CleaningServices` | `cleaning_services` |  |
| `Close` | `close` |  |
| `Cloud` | `cloud` |  |
| `CloudDownload` | `cloud_download` |  |
| `CloudOff` | `cloud_off` |  |
| `CloudUpload` | `cloud_upload` |  |
| `Code` | `code` |  |
| `Computer` | `computer` |  |
| `ContentCopy` | `content_copy` |  |
| `Crop` | `crop` |  |
| `Delete` | `delete` |  |
| `DeleteForever` | `delete_forever` |  |
| `DeleteSweep` | `delete_sweep` |  |
| `Description` | `description` |  |
| `Dns` | `dns` |  |
| `DocumentScanner` | `document_scanner` |  |
| `Download` | `download` | `Filled.Download` · tab bar |
| `Draft` | `draft` |  |
| `Drafts` | `drafts` |  |
| `DragHandle` | `drag_handle` |  |
| `DriveFileMove` | `drive_file_move` |  |
| `Edit` | `edit` |  |
| `EditCalendar` | `edit_calendar` |  |
| `Error` | `error` |  |
| `Event` | `event` |  |
| `EventBusy` | `event_busy` |  |
| `ExpandLess` | `expand_less` |  |
| `ExpandMore` | `expand_more` |  |
| `Face` | `face` |  |
| `Favorite` | `favorite` | `Filled.Favorite` · accesa |
| `FilterList` | `filter_list` |  |
| `Fingerprint` | `fingerprint` |  |
| `FlashlightOff` | `flashlight_off` |  |
| `FlashlightOn` | `flashlight_on` |  |
| `Folder` | `folder` |  |
| `FolderZip` | `folder_zip` |  |
| `FormatBold` | `format_bold` |  |
| `FormatIndentDecrease` | `format_indent_decrease` |  |
| `FormatIndentIncrease` | `format_indent_increase` |  |
| `FormatItalic` | `format_italic` |  |
| `FormatListBulleted` | `format_list_bulleted` |  |
| `FormatListNumbered` | `format_list_numbered` |  |
| `FormatQuote` | `format_quote` |  |
| `FormatUnderlined` | `format_underlined` |  |
| `Forum` | `forum` | `Filled.Forum` · tab bar |
| `Gavel` | `gavel` |  |
| `GraphicEq` | `graphic_eq` | `Filled.GraphicEq` · tab bar |
| `GridView` | `grid_view` | `Filled.GridView` · tab bar |
| `Group` | `group` |  |
| `Groups` | `groups` |  |
| `History` | `history` | `Filled.History` · tab bar |
| `Home` | `home` | `Filled.Home` · tab bar |
| `Image` | `image` |  |
| `ImageSearch` | `image_search` |  |
| `Inbox` | `inbox` | `Filled.Inbox` · tab bar |
| `Info` | `info` |  |
| `Insights` | `insights` |  |
| `IosShare` | `ios_share` |  |
| `Key` | `key` |  |
| `KeyboardArrowDown` | `keyboard_arrow_down` |  |
| `KeyboardArrowUp` | `keyboard_arrow_up` |  |
| `Label` | `label` |  |
| `Language` | `language` |  |
| `Layers` | `layers` |  |
| `Leaderboard` | `leaderboard` | `Filled.Leaderboard` · tab bar |
| `Link` | `link` |  |
| `LocationOn` | `location_on` |  |
| `LocationSearching` | `location_searching` |  |
| `Lock` | `lock` |  |
| `LockOpen` | `lock_open` |  |
| `Map` | `map` | `Filled.Map` · tab bar |
| `MarkEmailRead` | `mark_email_read` |  |
| `MarkEmailUnread` | `mark_email_unread` |  |
| `Memory` | `memory` |  |
| `Menu` | `menu` |  |
| `MenuBook` | `menu_book` |  |
| `Mic` | `mic` |  |
| `MicOff` | `mic_off` |  |
| `MoreHoriz` | `more_horiz` | `Filled.MoreHoriz` · tab bar |
| `MoreVert` | `more_vert` |  |
| `Movie` | `movie` |  |
| `MusicNote` | `music_note` |  |
| `MyLocation` | `my_location` |  |
| `Nightlight` | `nightlight` |  |
| `Notifications` | `notifications` |  |
| `NotificationsActive` | `notifications_active` |  |
| `NotificationsOff` | `notifications_off` |  |
| `NotificationsUnread` | `notifications_unread` |  |
| `OpenInNew` | `open_in_new` |  |
| `Palette` | `palette` |  |
| `Pause` | `pause` | `Filled.Pause` · riproduzione |
| `Person` | `person` | `Filled.Person` · tab bar |
| `PersonAdd` | `person_add` |  |
| `PhoneAndroid` | `phone_android` |  |
| `PhotoAlbum` | `photo_album` |  |
| `PhotoCamera` | `photo_camera` |  |
| `PhotoLibrary` | `photo_library` | `Filled.PhotoLibrary` · tab bar |
| `PictureAsPdf` | `picture_as_pdf` |  |
| `PlayArrow` | `play_arrow` | `Filled.PlayArrow` · riproduzione |
| `Print` | `print` |  |
| `PriorityHigh` | `priority_high` |  |
| `Psychology` | `psychology` |  |
| `Public` | `public` |  |
| `QrCode` | `qr_code` |  |
| `QrCodeScanner` | `qr_code_scanner` |  |
| `Redo` | `redo` |  |
| `Refresh` | `refresh` |  |
| `Replay` | `replay` |  |
| `RotateRight` | `rotate_right` |  |
| `Save` | `save` |  |
| `Schedule` | `schedule` |  |
| `School` | `school` |  |
| `Science` | `science` |  |
| `Screenshot` | `screenshot` |  |
| `Search` | `search` | `Filled.Search` · tab bar |
| `SearchOff` | `search_off` |  |
| `Security` | `security` |  |
| `Send` | `send` |  |
| `Settings` | `settings` | `Filled.Settings` · tab bar |
| `Share` | `share` |  |
| `Shield` | `shield` |  |
| `Shuffle` | `shuffle` |  |
| `SkipNext` | `skip_next` | `Filled.SkipNext` · riproduzione |
| `Slideshow` | `slideshow` |  |
| `Speed` | `speed` | `Filled.Speed` · tab bar |
| `Star` | `star` | `Filled.Star` · tab bar, accesa |
| `Stop` | `stop` | `Filled.Stop` · riproduzione |
| `Storage` | `storage` |  |
| `SwapHoriz` | `swap_horiz` |  |
| `SwapVert` | `swap_vert` |  |
| `Sync` | `sync` |  |
| `SystemUpdateAlt` | `system_update_alt` |  |
| `TableChart` | `table_chart` |  |
| `TextFormat` | `text_format` |  |
| `Today` | `today` |  |
| `Tune` | `tune` | `Filled.Tune` · tab bar |
| `Undo` | `undo` |  |
| `UnfoldMore` | `unfold_more` |  |
| `VideoFile` | `video_file` |  |
| `Visibility` | `visibility` |  |
| `VisibilityOff` | `visibility_off` |  |
| `VolumeOff` | `volume_off` |  |
| `VolumeUp` | `volume_up` |  |
| `Warning` | `warning` |  |
| `WbSunny` | `wb_sunny` | `Filled.WbSunny` · tab bar |
| `Widgets` | `widgets` | `Filled.Widgets` · tab bar |
| `Wifi` | `wifi` |  |
