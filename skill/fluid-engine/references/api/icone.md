# API · Icone (FluidIcons)

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/icons/FluidIcons.kt`

- `object FluidIcons` — Le icone dell'engine: Material Symbols arrotondate a peso 500, al tratto.
  - `object Filled` — Le stesse sagome a riempimento pieno, solo per le icone che ne hanno bisogno: la scheda della tab bar (in Apple la tab bar e' piena e le barre al tratto, R6), la forma «accesa» di uno stato (preferito, scelto) e i comandi di riproduzione. Nelle barre si usa la forma al tratto.

## Il set: 162 icone al tratto, 31 anche piene

Dai file generati da `tools/icons/gen_fluid_icons.py` (`FluidIconsFilled.kt`: 31; `FluidIconsOutline.kt`: 162), una riga per icona invece che una per dichiarazione. Ogni riga vale la proprietà al tratto `val FluidIcons.<icona>: ImageVector` e, quando la terza colonna non è vuota, anche quella piena `val FluidIcons.Filled.<icona>: ImageVector`, con l'uso per cui la piena esiste. Si importano per nome (`import dev.antigravity.fluidengine.ui.fluid.icons.<icona>`): lo stesso import porta tutte e due. La seconda colonna è il nome in Material Symbols, quello da cercare partendo da un'icona Material; la corrispondenza con le icone che le app usano oggi è in `tools/icons/corrispondenza.md`.

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
| `Delete` | `delete` |  |
| `DeleteForever` | `delete_forever` |  |
| `DeleteSweep` | `delete_sweep` |  |
| `Description` | `description` |  |
| `Dns` | `dns` |  |
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
| `MoreHoriz` | `more_horiz` | `Filled.MoreHoriz` · tab bar |
| `MoreVert` | `more_vert` |  |
| `Movie` | `movie` |  |
| `MusicNote` | `music_note` |  |
| `MyLocation` | `my_location` |  |
| `Nightlight` | `nightlight` |  |
| `Notifications` | `notifications` |  |
| `NotificationsActive` | `notifications_active` |  |
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
| `PriorityHigh` | `priority_high` |  |
| `Psychology` | `psychology` |  |
| `Public` | `public` |  |
| `QrCodeScanner` | `qr_code_scanner` |  |
| `Redo` | `redo` |  |
| `Refresh` | `refresh` |  |
| `Replay` | `replay` |  |
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
| `Warning` | `warning` |  |
| `WbSunny` | `wb_sunny` | `Filled.WbSunny` · tab bar |
| `Widgets` | `widgets` | `Filled.Widgets` · tab bar |
