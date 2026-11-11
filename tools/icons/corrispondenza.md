# FluidIcons: corrispondenza con le icone di oggi

Generato da `tools/icons/gen_fluid_icons.py`: non modificare a mano. Stesse righe, in forma da script, in
`corrispondenza.csv` (`origine;nome;symbols;fluid;nota`).

Il set ha **162 icone** al tratto e **31** varianti piene. Delle icone Material del
censimento (`censimento-app.txt`) **180 su 361** hanno un equivalente nel set (1684 usi su 2117); **181** restano fuori (433 usi). Per ognuna
c'e' il nome di Material Symbols: per portarla nel set basta la riga nell'elenco.

**Come si migra.** `Icons.Rounded.X` (o `Icons.AutoMirrored.Rounded.X`, `Icons.Outlined.X`) diventa
la colonna FluidIcons, con l'import `dev.antigravity.fluidengine.ui.fluid.icons.<Nome>`. Le icone del
set sono al tratto: dove Material era piena l'aspetto cambia, ed e' voluto (le barre di Apple sono al
tratto). Le eccezioni sono scritte nella nota: le forme «accese» di una coppia (preferito, stella,
spunta nel cerchio) e i comandi di riproduzione vanno sulla variante piena, e nella tab bar si usa la
piena.

## Nel set

| Material | usi | FluidIcons | nota |
|---|---:|---|---|
| `Warning` | 63 | `FluidIcons.Warning` |  |
| `Check` | 56 | `FluidIcons.Check` |  |
| `Delete` | 51 | `FluidIcons.Delete` |  |
| `Close` | 48 | `FluidIcons.Close` |  |
| `Folder` | 43 | `FluidIcons.Folder` |  |
| `ErrorOutline` | 39 | `FluidIcons.Error` |  |
| `Info` | 38 | `FluidIcons.Info` |  |
| `Schedule` | 36 | `FluidIcons.Schedule` |  |
| `VisibilityOff` | 35 | `FluidIcons.VisibilityOff` |  |
| `Lock` | 34 | `FluidIcons.Lock` |  |
| `CheckCircle` | 30 | `FluidIcons.Filled.CheckCircle` | la forma «scelta» della coppia CheckCircle / CheckCircleOutline |
| `Settings` | 30 | `FluidIcons.Settings` | piena (tab bar): FluidIcons.Filled.Settings |
| `Refresh` | 29 | `FluidIcons.Refresh` |  |
| `Group` | 28 | `FluidIcons.Group` |  |
| `PhotoLibrary` | 26 | `FluidIcons.PhotoLibrary` | piena (tab bar): FluidIcons.Filled.PhotoLibrary |
| `AutoAwesome` | 24 | `FluidIcons.AutoAwesome` | piena (tab bar): FluidIcons.Filled.AutoAwesome |
| `Image` | 24 | `FluidIcons.Image` |  |
| `CloudOff` | 23 | `FluidIcons.CloudOff` |  |
| `Download` | 23 | `FluidIcons.Download` | piena (tab bar): FluidIcons.Filled.Download |
| `Face` | 23 | `FluidIcons.Face` |  |
| `Search` | 23 | `FluidIcons.Search` | piena (tab bar): FluidIcons.Filled.Search |
| `Share` | 23 | `FluidIcons.Share` |  |
| `Edit` | 22 | `FluidIcons.Edit` |  |
| `Key` | 21 | `FluidIcons.Key` |  |
| `Favorite` | 20 | `FluidIcons.Filled.Favorite` | la forma «accesa» della coppia Favorite / FavoriteBorder |
| `Person` | 20 | `FluidIcons.Person` | piena (tab bar): FluidIcons.Filled.Person |
| `Star` | 20 | `FluidIcons.Filled.Star` | la forma «accesa» della coppia Star / StarBorder |
| `PhoneAndroid` | 19 | `FluidIcons.PhoneAndroid` |  |
| `DeleteForever` | 17 | `FluidIcons.DeleteForever` |  |
| `Tune` | 17 | `FluidIcons.Tune` | piena (tab bar): FluidIcons.Filled.Tune |
| `CalendarMonth` | 16 | `FluidIcons.CalendarMonth` | piena (tab bar): FluidIcons.Filled.CalendarMonth |
| `IosShare` | 16 | `FluidIcons.IosShare` |  |
| `Home` | 15 | `FluidIcons.Home` | piena (tab bar): FluidIcons.Filled.Home |
| `Mic` | 15 | `FluidIcons.Mic` |  |
| `PlayArrow` | 15 | `FluidIcons.Filled.PlayArrow` | riproduzione: pieni come in Apple |
| `Storage` | 15 | `FluidIcons.Dns` | al posto di `storage` (tre barre piene, pesa come piena): i server al tratto |
| `Add` | 14 | `FluidIcons.Add` |  |
| `Place` | 14 | `FluidIcons.LocationOn` |  |
| `ContentCopy` | 13 | `FluidIcons.ContentCopy` | si gira da sola da destra a sinistra |
| `Dns` | 13 | `FluidIcons.Dns` |  |
| `History` | 13 | `FluidIcons.History` | piena (tab bar): FluidIcons.Filled.History |
| `OpenInNew` | 13 | `FluidIcons.OpenInNew` | si gira da sola da destra a sinistra |
| `PhotoAlbum` | 12 | `FluidIcons.PhotoAlbum` |  |
| `QrCodeScanner` | 12 | `FluidIcons.QrCodeScanner` |  |
| `Shield` | 12 | `FluidIcons.Shield` |  |
| `Cloud` | 11 | `FluidIcons.Cloud` |  |
| `CloudDownload` | 11 | `FluidIcons.CloudDownload` |  |
| `Description` | 11 | `FluidIcons.Description` |  |
| `Groups` | 11 | `FluidIcons.Group` | al posto di `groups` (figurine piene): due figure al tratto |
| `Stop` | 11 | `FluidIcons.Filled.Stop` | riproduzione: pieni come in Apple |
| `CleaningServices` | 10 | `FluidIcons.CleaningServices` |  |
| `DriveFileMove` | 10 | `FluidIcons.DriveFileMove` | si gira da sola da destra a sinistra |
| `EditCalendar` | 10 | `FluidIcons.EditCalendar` |  |
| `GraphicEq` | 10 | `FluidIcons.GraphicEq` | piena (tab bar): FluidIcons.Filled.GraphicEq |
| `Visibility` | 10 | `FluidIcons.Visibility` |  |
| `Layers` | 9 | `FluidIcons.Layers` |  |
| `Pause` | 9 | `FluidIcons.Filled.Pause` | riproduzione: pieni come in Apple |
| `Public` | 9 | `FluidIcons.Language` | al posto di `public` (continenti pieni): il globo a reticolo, al tratto |
| `Sync` | 9 | `FluidIcons.Sync` |  |
| `ArrowUpward` | 8 | `FluidIcons.ArrowUpward` |  |
| `ChevronRight` | 8 | `FluidIcons.ChevronRight` | si gira da sola da destra a sinistra |
| `Code` | 8 | `FluidIcons.Code` |  |
| `Gavel` | 8 | `FluidIcons.Gavel` |  |
| `Inbox` | 8 | `FluidIcons.Inbox` | piena (tab bar): FluidIcons.Filled.Inbox |
| `Map` | 8 | `FluidIcons.Map` | piena (tab bar): FluidIcons.Filled.Map |
| `Checklist` | 7 | `FluidIcons.Checklist` |  |
| `CloudUpload` | 7 | `FluidIcons.CloudUpload` |  |
| `GridView` | 7 | `FluidIcons.GridView` | piena (tab bar): FluidIcons.Filled.GridView |
| `KeyboardArrowDown` | 7 | `FluidIcons.KeyboardArrowDown` |  |
| `MyLocation` | 7 | `FluidIcons.MyLocation` |  |
| `PersonAdd` | 7 | `FluidIcons.PersonAdd` |  |
| `ReportProblem` | 7 | `FluidIcons.Warning` |  |
| `ArrowBack` | 6 | `FluidIcons.ArrowBack` | si gira da sola da destra a sinistra |
| `Fingerprint` | 6 | `FluidIcons.Fingerprint` |  |
| `Forum` | 6 | `FluidIcons.Forum` | piena (tab bar): FluidIcons.Filled.Forum |
| `ImageSearch` | 6 | `FluidIcons.ImageSearch` |  |
| `InsertDriveFile` | 6 | `FluidIcons.Draft` | si gira da sola da destra a sinistra |
| `MoreHoriz` | 6 | `FluidIcons.MoreHoriz` | piena (tab bar): FluidIcons.Filled.MoreHoriz |
| `Movie` | 6 | `FluidIcons.Movie` |  |
| `Save` | 6 | `FluidIcons.Save` |  |
| `School` | 6 | `FluidIcons.School` |  |
| `WbSunny` | 6 | `FluidIcons.WbSunny` | piena (tab bar): FluidIcons.Filled.WbSunny |
| `Widgets` | 6 | `FluidIcons.Widgets` | piena (tab bar): FluidIcons.Filled.Widgets |
| `Apps` | 5 | `FluidIcons.Apps` | piena (tab bar): FluidIcons.Filled.Apps |
| `AttachFile` | 5 | `FluidIcons.AttachFile` |  |
| `CameraAlt` | 5 | `FluidIcons.PhotoCamera` |  |
| `Event` | 5 | `FluidIcons.Event` |  |
| `EventBusy` | 5 | `FluidIcons.EventBusy` |  |
| `Insights` | 5 | `FluidIcons.Insights` |  |
| `KeyboardArrowUp` | 5 | `FluidIcons.KeyboardArrowUp` |  |
| `Menu` | 5 | `FluidIcons.Menu` |  |
| `Palette` | 5 | `FluidIcons.Palette` |  |
| `Restore` | 5 | `FluidIcons.History` | piena (tab bar): FluidIcons.Filled.History |
| `Science` | 5 | `FluidIcons.Science` |  |
| `Screenshot` | 5 | `FluidIcons.Screenshot` |  |
| `Speed` | 5 | `FluidIcons.Speed` | piena (tab bar): FluidIcons.Filled.Speed |
| `WarningAmber` | 5 | `FluidIcons.Warning` |  |
| `ArrowBackIos` | 4 | `FluidIcons.ArrowBackIos` | si gira da sola da destra a sinistra |
| `ArrowForward` | 4 | `FluidIcons.ArrowForward` | si gira da sola da destra a sinistra |
| `Campaign` | 4 | `FluidIcons.Campaign` | piena (tab bar): FluidIcons.Filled.Campaign |
| `Computer` | 4 | `FluidIcons.Computer` |  |
| `FavoriteBorder` | 4 | `FluidIcons.Favorite` | piena (tab bar): FluidIcons.Filled.Favorite |
| `LocationSearching` | 4 | `FluidIcons.LocationSearching` |  |
| `MoreVert` | 4 | `FluidIcons.MoreVert` |  |
| `MusicNote` | 4 | `FluidIcons.MusicNote` |  |
| `Notifications` | 4 | `FluidIcons.Notifications` |  |
| `PriorityHigh` | 4 | `FluidIcons.PriorityHigh` |  |
| `Psychology` | 4 | `FluidIcons.Psychology` |  |
| `SearchOff` | 4 | `FluidIcons.SearchOff` |  |
| `Send` | 4 | `FluidIcons.Send` | si gira da sola da destra a sinistra |
| `SwapHoriz` | 4 | `FluidIcons.SwapHoriz` |  |
| `SwapVert` | 4 | `FluidIcons.SwapVert` |  |
| `Undo` | 4 | `FluidIcons.Undo` | si gira da sola da destra a sinistra |
| `Bedtime` | 3 | `FluidIcons.Bedtime` |  |
| `Bolt` | 3 | `FluidIcons.Bolt` |  |
| `CheckCircleOutline` | 3 | `FluidIcons.CheckCircle` | piena (tab bar): FluidIcons.Filled.CheckCircle |
| `ChevronLeft` | 3 | `FluidIcons.ChevronLeft` | si gira da sola da destra a sinistra |
| `Collections` | 3 | `FluidIcons.PhotoLibrary` | in Material Symbols «collections» e «filter» sono una sagoma, al tratto uguale a «photo_library»; nelle Material di oggi Collections e PhotoLibrary sono identiche; piena (tab bar): FluidIcons.Filled.PhotoLibrary |
| `DeleteSweep` | 3 | `FluidIcons.DeleteSweep` |  |
| `DragHandle` | 3 | `FluidIcons.DragHandle` |  |
| `ExpandLess` | 3 | `FluidIcons.ExpandLess` |  |
| `ExpandMore` | 3 | `FluidIcons.ExpandMore` |  |
| `Language` | 3 | `FluidIcons.Language` |  |
| `Link` | 3 | `FluidIcons.Link` |  |
| `MarkEmailRead` | 3 | `FluidIcons.MarkEmailRead` |  |
| `MarkEmailUnread` | 3 | `FluidIcons.MarkEmailUnread` |  |
| `Nightlight` | 3 | `FluidIcons.Nightlight` |  |
| `NotificationsActive` | 3 | `FluidIcons.NotificationsActive` |  |
| `PhotoCamera` | 3 | `FluidIcons.PhotoCamera` |  |
| `Replay` | 3 | `FluidIcons.Replay` |  |
| `Today` | 3 | `FluidIcons.Today` |  |
| `UnfoldMore` | 3 | `FluidIcons.UnfoldMore` |  |
| `VolumeOff` | 3 | `FluidIcons.VolumeOff` | si gira da sola da destra a sinistra |
| `AccountBalance` | 2 | `FluidIcons.AccountBalance` |  |
| `Android` | 2 | `FluidIcons.Android` |  |
| `Animation` | 2 | `FluidIcons.Animation` | piena (tab bar): FluidIcons.Filled.Animation |
| `Archive` | 2 | `FluidIcons.Archive` |  |
| `AudioFile` | 2 | `FluidIcons.AudioFile` |  |
| `Calculate` | 2 | `FluidIcons.Calculate` |  |
| `Create` | 2 | `FluidIcons.Edit` |  |
| `DeleteOutline` | 2 | `FluidIcons.Delete` |  |
| `FolderZip` | 2 | `FluidIcons.FolderZip` |  |
| `FormatBold` | 2 | `FluidIcons.FormatBold` |  |
| `FormatItalic` | 2 | `FluidIcons.FormatItalic` |  |
| `FormatUnderlined` | 2 | `FluidIcons.FormatUnderlined` |  |
| `Grade` | 2 | `FluidIcons.Star` | piena (tab bar): FluidIcons.Filled.Star |
| `LocationOn` | 2 | `FluidIcons.LocationOn` |  |
| `LockOpen` | 2 | `FluidIcons.LockOpen` |  |
| `Memory` | 2 | `FluidIcons.Memory` |  |
| `MenuBook` | 2 | `FluidIcons.MenuBook` | si gira da sola da destra a sinistra |
| `SaveAlt` | 2 | `FluidIcons.Download` | piena (tab bar): FluidIcons.Filled.Download |
| `Security` | 2 | `FluidIcons.Shield` | al posto di `security` (scudo campito a meta'): lo scudo al tratto |
| `Slideshow` | 2 | `FluidIcons.Slideshow` |  |
| `StarBorder` | 2 | `FluidIcons.Star` | piena (tab bar): FluidIcons.Filled.Star |
| `SystemUpdateAlt` | 2 | `FluidIcons.SystemUpdateAlt` |  |
| `TableChart` | 2 | `FluidIcons.TableChart` |  |
| `TextFormat` | 2 | `FluidIcons.TextFormat` |  |
| `VideoFile` | 2 | `FluidIcons.VideoFile` |  |
| `WbCloudy` | 2 | `FluidIcons.Cloud` |  |
| `ArrowDownward` | 1 | `FluidIcons.ArrowDownward` |  |
| `Backpack` | 1 | `FluidIcons.Backpack` | piena (tab bar): FluidIcons.Filled.Backpack |
| `CheckBox` | 1 | `FluidIcons.CheckBox` |  |
| `CheckBoxOutlineBlank` | 1 | `FluidIcons.CheckBoxOutlineBlank` |  |
| `CloudQueue` | 1 | `FluidIcons.Cloud` |  |
| `Drafts` | 1 | `FluidIcons.Drafts` |  |
| `Error` | 1 | `FluidIcons.Error` |  |
| `FileDownload` | 1 | `FluidIcons.Download` | piena (tab bar): FluidIcons.Filled.Download |
| `FilterList` | 1 | `FluidIcons.FilterList` |  |
| `FormatIndentDecrease` | 1 | `FluidIcons.FormatIndentDecrease` | si gira da sola da destra a sinistra |
| `FormatIndentIncrease` | 1 | `FluidIcons.FormatIndentIncrease` | si gira da sola da destra a sinistra |
| `FormatListBulleted` | 1 | `FluidIcons.FormatListBulleted` | si gira da sola da destra a sinistra |
| `FormatListNumbered` | 1 | `FluidIcons.FormatListNumbered` |  |
| `FormatQuote` | 1 | `FluidIcons.FormatQuote` |  |
| `Label` | 1 | `FluidIcons.Label` | si gira da sola da destra a sinistra |
| `Leaderboard` | 1 | `FluidIcons.Leaderboard` | piena (tab bar): FluidIcons.Filled.Leaderboard |
| `NavigateBefore` | 1 | `FluidIcons.ChevronLeft` | si gira da sola da destra a sinistra |
| `NavigateNext` | 1 | `FluidIcons.ChevronRight` | si gira da sola da destra a sinistra |
| `PictureAsPdf` | 1 | `FluidIcons.PictureAsPdf` |  |
| `Redo` | 1 | `FluidIcons.Redo` | si gira da sola da destra a sinistra |
| `SkipNext` | 1 | `FluidIcons.Filled.SkipNext` | riproduzione: pieni come in Apple |

## Fuori dal set

Criterio dell'elenco: tutte le icone dell'engine e della galleria; delle app, quelle usate da almeno
due app o almeno dieci volte, e quelle delle tab bar. Qui sotto il resto, con il nome da aggiungere.

| Material | usi | dove | Material Symbols |
|---|---:|---|---|
| `Block` | 9 | MyCloud-Gallery-2 | `block` |
| `WaterDrop` | 9 | FluidWeather | `water_drop` |
| `Wifi` | 9 | MyCloud-Gallery-2 | `wifi` |
| `AdminPanelSettings` | 8 | MyCloud-Gallery-2 | `admin_panel_settings` |
| `TextFields` | 8 | MyCloud-Gallery-2 | `text_fields` |
| `Backup` | 7 | MyCloud-Gallery-2 | `backup` |
| `FolderOff` | 7 | MyCloud-Gallery-2 | `folder_off` |
| `RestartAlt` | 7 | MyCloud-Gallery-2 | `restart_alt` |
| `Videocam` | 7 | MyCloud-Gallery-2 | `videocam` |
| `AddPhotoAlternate` | 6 | MyCloud-Gallery-2 | `add_photo_alternate` |
| `CallMerge` | 6 | MyCloud-Gallery-2 | `call_merge` |
| `Devices` | 6 | MyCloud-Gallery-2 | `devices` |
| `DriveFileRenameOutline` | 6 | MyCloud-Gallery-2 | `drive_file_rename_outline` |
| `FolderOpen` | 6 | MyCloud-Gallery-2 | `folder_open` |
| `PhonelinkErase` | 6 | MyCloud-Gallery-2 | `phonelink_erase` |
| `VpnKey` | 6 | MyCloud-Gallery-2 | `vpn_key` |
| `DirectionsWalk` | 5 | Fluid-transit | `directions_walk` |
| `GppBad` | 5 | MyCloud-Gallery-2 | `gpp_bad` |
| `GppGood` | 5 | MyCloud-Gallery-2 | `verified_user` |
| `HourglassTop` | 5 | MyCloud-Gallery-2 | `hourglass_top` |
| `LocationOff` | 5 | MyCloud-Gallery-2 | `location_off` |
| `QrCode2` | 5 | MyCloud-Gallery-2 | `qr_code_2` |
| `Verified` | 5 | MyCloud-Gallery-2 | `verified` |
| `WifiOff` | 5 | MyCloud-Gallery-2 | `wifi_off` |
| `Assignment` | 4 | classeviva-expressive | `assignment` |
| `CallSplit` | 4 | MyCloud-Gallery-2 | `call_split` |
| `DirectionsBus` | 4 | Fluid-transit | `directions_bus` |
| `Flight` | 4 | MyCloud-Gallery-2 | `flight` |
| `Lan` | 4 | MyCloud-Gallery-2 | `lan` |
| `LockClock` | 4 | MyCloud-Gallery-2 | `lock_clock` |
| `MoveToInbox` | 4 | MyCloud-Gallery-2 | `move_to_inbox` |
| `NetworkCell` | 4 | MyCloud-Gallery-2 | `network_cell` |
| `PauseCircle` | 4 | MyCloud-Gallery-2 | `pause_circle` |
| `PhonelinkOff` | 4 | MyCloud-Gallery-2 | `phonelink_off` |
| `Photo` | 4 | MyCloud-Gallery-2 | `photo` |
| `RotateRight` | 4 | MyCloud-Gallery-2 | `rotate_right` |
| `SelectAll` | 4 | MyCloud-Gallery-2 | `select_all` |
| `Timer` | 4 | MyCloud-Gallery-2 | `timer` |
| `Upload` | 4 | Pampa notes | `upload` |
| `VpnLock` | 4 | MyCloud-Gallery-2 | `vpn_lock` |
| `AcUnit` | 3 | FluidWeather | `ac_unit` |
| `Balance` | 3 | MyCloud-Gallery-2 | `balance` |
| `BatteryChargingFull` | 3 | MyCloud-Gallery-2 | `battery_charging_full` |
| `CloudDone` | 3 | MyCloud-Gallery-2 | `cloud_done` |
| `CloudSync` | 3 | MyCloud-Gallery-2 | `cloud_sync` |
| `CreateNewFolder` | 3 | MyCloud-Gallery-2 | `create_new_folder` |
| `Extension` | 3 | PampAI | `extension` |
| `Grain` | 3 | FluidWeather | `grain` |
| `InstallDesktop` | 3 | Pampa notes | `install_desktop` |
| `KeyOff` | 3 | MyCloud-Gallery-2 | `key_off` |
| `MotionPhotosOn` | 3 | MyCloud-Gallery-2 | `motion_photos_on` |
| `NearMe` | 3 | Fluid-transit | `near_me` |
| `NewReleases` | 3 | MyCloud-Gallery-2 | `new_releases` |
| `PersonOff` | 3 | MyCloud-Gallery-2 | `person_off` |
| `RestoreFromTrash` | 3 | MyCloud-Gallery-2 | `restore_from_trash` |
| `SwitchAccount` | 3 | MyCloud-Gallery-2 | `switch_account` |
| `TaskAlt` | 3 | MyCloud-Gallery-2 | `task_alt` |
| `Update` | 3 | MyCloud-Gallery-2 | `update` |
| `VerifiedUser` | 3 | MyCloud-Gallery-2 | `verified_user` |
| `Air` | 2 | FluidWeather | `air` |
| `AllInclusive` | 2 | MyCloud-Gallery-2 | `all_inclusive` |
| `AutoStories` | 2 | classeviva-expressive | `auto_stories` |
| `Chat` | 2 | MyCloud-Gallery-2 | `chat` |
| `CoPresent` | 2 | classeviva-expressive | `co_present` |
| `ContentCut` | 2 | MyCloud-Gallery-2 | `content_cut` |
| `DataUsage` | 2 | MyCloud-Gallery-2 | `data_usage` |
| `Directions` | 2 | Fluid-transit | `directions` |
| `Explore` | 2 | Fluid-transit | `explore` |
| `FastForward` | 2 | Pampa notes | `fast_forward` |
| `FolderCopy` | 2 | classeviva-expressive | `folder_copy` |
| `FolderShared` | 2 | MyCloud-Gallery-2 | `folder_shared` |
| `FolderSpecial` | 2 | MyCloud-Gallery-2 | `folder_special` |
| `GppMaybe` | 2 | MyCloud-Gallery-2 | `gpp_maybe` |
| `HistoryEdu` | 2 | classeviva-expressive | `history_edu` |
| `HourglassEmpty` | 2 | MyCloud-Gallery-2 | `hourglass_empty` |
| `Inventory2` | 2 | MyCloud-Gallery-2 | `inventory_2` |
| `KeyboardDoubleArrowDown` | 2 | MyCloud-Gallery-2 | `keyboard_double_arrow_down` |
| `LibraryBooks` | 2 | classeviva-expressive | `library_books` |
| `LinkOff` | 2 | MyCloud-Gallery-2 | `link_off` |
| `Luggage` | 2 | MyCloud-Gallery-2 | `luggage` |
| `Navigation` | 2 | FluidWeather | `navigation` |
| `Notes` | 2 | universal_converter | `notes` |
| `NotificationImportant` | 2 | MyCloud-Gallery-2 | `notification_important` |
| `Password` | 2 | MyCloud-Gallery-2 | `password` |
| `PushPin` | 2 | PampAI | `push_pin` |
| `RadioButtonChecked` | 2 | MyCloud-Gallery-2 | `radio_button_checked` |
| `RadioButtonUnchecked` | 2 | MyCloud-Gallery-2 | `radio_button_unchecked` |
| `Router` | 2 | MyCloud-Gallery-2 | `router` |
| `SignalWifiOff` | 2 | MyCloud-Gallery-2 | `signal_wifi_off` |
| `SystemUpdate` | 2 | MyCloud-Gallery-2 | `system_update` |
| `TabletAndroid` | 2 | MyCloud-Gallery-2 | `tablet_android` |
| `Thermostat` | 2 | FluidWeather | `thermostat` |
| `Thunderstorm` | 2 | FluidWeather | `thunderstorm` |
| `Toc` | 2 | Pampa notes | `toc` |
| `VideocamOff` | 2 | MyCloud-Gallery-2 | `videocam_off` |
| `VpnKeyOff` | 2 | MyCloud-Gallery-2 | `vpn_key_off` |
| `Album` | 1 | MyCloud-Gallery-2 | `album` |
| `Article` | 1 | universal_converter | `article` |
| `AspectRatio` | 1 | Pampa widgets | `aspect_ratio` |
| `AutoFixHigh` | 1 | classeviva-expressive | `auto_fix_high` |
| `Backspace` | 1 | Codex | `backspace` |
| `Badge` | 1 | MyCloud-Gallery-2 | `badge` |
| `Battery5Bar` | 1 | MyCloud-Gallery-2 | `battery_5_bar` |
| `Biotech` | 1 | Pampa notes | `biotech` |
| `BlurOn` | 1 | FluidWeather | `blur_on` |
| `BrokenImage` | 1 | MyCloud-Gallery-2 | `broken_image` |
| `Brush` | 1 | Pampa notes | `brush` |
| `BugReport` | 1 | classeviva-expressive | `bug_report` |
| `Build` | 1 | MyCloud-Gallery-2 | `build` |
| `CellTower` | 1 | MyCloud-Gallery-2 | `cell_tower` |
| `Church` | 1 | classeviva-expressive | `church` |
| `Compare` | 1 | MyCloud-Gallery-2 | `compare` |
| `DataObject` | 1 | universal_converter | `data_object` |
| `Dehaze` | 1 | FluidWeather | `dehaze` |
| `DirectionsRun` | 1 | classeviva-expressive | `directions_run` |
| `Dock` | 1 | MyCloud-Gallery-2 | `dock` |
| `DoneAll` | 1 | Codex | `done_all` |
| `Draw` | 1 | classeviva-expressive | `draw` |
| `FactCheck` | 1 | classeviva-expressive | `fact_check` |
| `FileUpload` | 1 | classeviva-expressive | `upload` |
| `FilterAlt` | 1 | MyCloud-Gallery-2 | `filter_alt` |
| `FilterAltOff` | 1 | MyCloud-Gallery-2 | `filter_alt_off` |
| `FilterDrama` | 1 | FluidWeather | `filter_drama` |
| `FindInPage` | 1 | MyCloud-Gallery-2 | `find_in_page` |
| `Flag` | 1 | Fluid-transit | `flag` |
| `FlashlightOff` | 1 | MyCloud-Gallery-2 | `flashlight_off` |
| `FlashlightOn` | 1 | MyCloud-Gallery-2 | `flashlight_on` |
| `FontDownload` | 1 | universal_converter | `font_download` |
| `Forward10` | 1 | Pampa notes | `forward_10` |
| `Fullscreen` | 1 | MyCloud-Gallery-2 | `fullscreen` |
| `Functions` | 1 | Pampa notes | `functions` |
| `Hail` | 1 | FluidWeather | `hail` |
| `Help` | 1 | MyCloud-Gallery-2 | `help` |
| `HelpOutline` | 1 | FluidWeather | `help` |
| `HideImage` | 1 | universal_converter | `hide_image` |
| `HighQuality` | 1 | MyCloud-Gallery-2 | `high_quality` |
| `HistoryToggleOff` | 1 | MyCloud-Gallery-2 | `history_toggle_off` |
| `Keyboard` | 1 | MyCloud-Gallery-2 | `keyboard` |
| `Landscape` | 1 | Fluid-transit | `landscape` |
| `ListAlt` | 1 | Pampa notes | `list_alt` |
| `LocationCity` | 1 | Fluid-transit | `location_city` |
| `Login` | 1 | classeviva-expressive | `login` |
| `Logout` | 1 | classeviva-expressive | `logout` |
| `MonitorHeart` | 1 | MyCloud-Gallery-2 | `monitor_heart` |
| `NetworkCheck` | 1 | MyCloud-Gallery-2 | `network_check` |
| `NightsStay` | 1 | FluidWeather | `nights_stay` |
| `NoEncryption` | 1 | MyCloud-Gallery-2 | `no_encryption` |
| `NoPhotography` | 1 | MyCloud-Gallery-2 | `no_photography` |
| `PersonRemove` | 1 | MyCloud-Gallery-2 | `person_remove` |
| `PhonelinkLock` | 1 | MyCloud-Gallery-2 | `phonelink_lock` |
| `PhotoSizeSelectSmall` | 1 | MyCloud-Gallery-2 | `photo_size_select_small` |
| `Pin` | 1 | MyCloud-Gallery-2 | `pin` |
| `PlayCircle` | 1 | MyCloud-Gallery-2 | `play_circle` |
| `Quiz` | 1 | classeviva-expressive | `quiz` |
| `Radar` | 1 | FluidWeather | `radar` |
| `RateReview` | 1 | FluidWeather | `rate_review` |
| `Remove` | 1 | MyCloud-Gallery-2 | `remove` |
| `RemoveCircleOutline` | 1 | MyCloud-Gallery-2 | `do_not_disturb_on` |
| `Replay10` | 1 | Pampa notes | `replay_10` |
| `Report` | 1 | classeviva-expressive | `report` |
| `RotateLeft` | 1 | MyCloud-Gallery-2 | `rotate_left` |
| `SdCardAlert` | 1 | MyCloud-Gallery-2 | `sd_card_alert` |
| `SettingsRemote` | 1 | MyCloud-Gallery-2 | `settings_remote` |
| `ShowChart` | 1 | classeviva-expressive | `show_chart` |
| `SignalCellularAlt` | 1 | MyCloud-Gallery-2 | `signal_cellular_alt` |
| `Smartphone` | 1 | MyCloud-Gallery-2 | `smartphone` |
| `SortByAlpha` | 1 | Pampa widgets | `sort_by_alpha` |
| `Speaker` | 1 | Pampa widgets | `speaker` |
| `SportsBasketball` | 1 | Pampa notes | `sports_basketball` |
| `Storefront` | 1 | Pampa-store-src | `storefront` |
| `TheaterComedy` | 1 | Pampa notes | `theater_comedy` |
| `Translate` | 1 | classeviva-expressive | `translate` |
| `Umbrella` | 1 | FluidWeather | `umbrella` |
| `UploadFile` | 1 | universal_converter | `upload_file` |
| `VerticalSplit` | 1 | MyCloud-Gallery-2 | `vertical_split` |
| `ViewInAr` | 1 | universal_converter | `view_in_ar` |
| `ViewList` | 1 | Pampa widgets | `view_list` |
| `VolumeUp` | 1 | MyCloud-Gallery-2 | `volume_up` |
| `WbTwilight` | 1 | FluidWeather | `wb_twilight` |
| `WifiFind` | 1 | MyCloud-Gallery-2 | `wifi_find` |
| `Work` | 1 | Pampa notes | `work` |

## Fluidify (Phosphor)

Fluidify usa Phosphor (`com.adamglin.phosphoricons`), non Material: le sue icone non entrano come tali.
Qui l'equivalente Material Symbols di ognuna, per nome e stile (98 righe, 84 nomi): 46 righe sono gia' nel set,
50 hanno un equivalente fuori dal set, 2 non ne hanno. «Vicina» vuol dire
stessa idea, sagoma diversa; lo stile `Fill` va sulla variante piena.

| Phosphor | Material Symbols | FluidIcons | nota |
|---|---|---|---|
| `Fill.ArrowCircleDown` | `arrow_circle_down` |  | fuori dal set: per averla, la riga `arrow_circle_down` in fluid-icons.txt |
| `Regular.ArrowCircleDown` | `arrow_circle_down` |  | fuori dal set: per averla, la riga `arrow_circle_down` in fluid-icons.txt |
| `Regular.ArrowCircleUp` | `arrow_circle_up` |  | fuori dal set: per averla, la riga `arrow_circle_up` in fluid-icons.txt |
| `Regular.ArrowClockwise` | `refresh` | `FluidIcons.Refresh` |  |
| `Regular.ArrowLeft` | `arrow_back` | `FluidIcons.ArrowBack` |  |
| `Regular.ArrowUpRight` | `arrow_outward` |  | fuori dal set: per averla, la riga `arrow_outward` in fluid-icons.txt |
| `Regular.ArrowsDownUp` | `swap_vert` | `FluidIcons.SwapVert` |  |
| `Regular.ArrowsInSimple` | `close_fullscreen` |  | fuori dal set: per averla, la riga `close_fullscreen` in fluid-icons.txt |
| `Regular.ArrowsOutSimple` | `open_in_full` |  | fuori dal set: per averla, la riga `open_in_full` in fluid-icons.txt |
| `Regular.Books` | `library_books` |  | vicina, non la stessa sagoma; fuori dal set: per averla, la riga `library_books` in fluid-icons.txt |
| `Regular.Broadcast` | `sensors` |  | fuori dal set: per averla, la riga `sensors` in fluid-icons.txt |
| `Regular.Car` | `directions_car` |  | fuori dal set: per averla, la riga `directions_car` in fluid-icons.txt |
| `Regular.CaretDown` | `keyboard_arrow_down` | `FluidIcons.KeyboardArrowDown` |  |
| `Regular.CaretLeft` | `chevron_left` | `FluidIcons.ChevronLeft` |  |
| `Regular.CaretRight` | `chevron_right` | `FluidIcons.ChevronRight` |  |
| `Regular.CaretUp` | `keyboard_arrow_up` | `FluidIcons.KeyboardArrowUp` |  |
| `Regular.CaretUpDown` | `unfold_more` | `FluidIcons.UnfoldMore` |  |
| `Fill.Check` | `check` | `FluidIcons.Check` | la piena non e' nel set (`check pieno` nell'elenco per averla) |
| `Regular.Check` | `check` | `FluidIcons.Check` |  |
| `Regular.ClockCounterClockwise` | `history` | `FluidIcons.History` |  |
| `Regular.CloudSlash` | `cloud_off` | `FluidIcons.CloudOff` |  |
| `Regular.Desktop` | `desktop_windows` |  | fuori dal set: per averla, la riga `desktop_windows` in fluid-icons.txt |
| `Regular.DeviceMobile` | `smartphone` |  | fuori dal set: per averla, la riga `smartphone` in fluid-icons.txt |
| `Regular.Devices` | `devices` |  | fuori dal set: per averla, la riga `devices` in fluid-icons.txt |
| `Regular.Disc` | `album` |  | fuori dal set: per averla, la riga `album` in fluid-icons.txt |
| `Regular.DotsSixVertical` | `drag_indicator` |  | fuori dal set: per averla, la riga `drag_indicator` in fluid-icons.txt |
| `Regular.DotsThree` | `more_horiz` | `FluidIcons.MoreHoriz` |  |
| `Regular.Download` | `download` | `FluidIcons.Download` |  |
| `Regular.DownloadSimple` | `download` | `FluidIcons.Download` |  |
| `Regular.Export` | `ios_share` | `FluidIcons.IosShare` |  |
| `Regular.FolderSimple` | `folder` | `FluidIcons.Folder` |  |
| `Regular.Gauge` | `speed` | `FluidIcons.Speed` |  |
| `Regular.GearSix` | `settings` | `FluidIcons.Settings` |  |
| `Regular.HardDrives` | `dns` | `FluidIcons.Dns` | vicina, non la stessa sagoma |
| `Regular.Headphones` | `headphones` |  | fuori dal set: per averla, la riga `headphones` in fluid-icons.txt |
| `Fill.Heart` | `favorite` | `FluidIcons.Filled.Favorite` |  |
| `Regular.Heart` | `favorite` | `FluidIcons.Favorite` |  |
| `Regular.HeartBreak` | `heart_broken` |  | fuori dal set: per averla, la riga `heart_broken` in fluid-icons.txt |
| `Fill.House` | `home` | `FluidIcons.Filled.Home` |  |
| `Regular.House` | `home` | `FluidIcons.Home` |  |
| `Fill.Info` | `info` | `FluidIcons.Info` | la piena non e' nel set (`info pieno` nell'elenco per averla) |
| `Regular.Info` | `info` | `FluidIcons.Info` |  |
| `Regular.Laptop` | `computer` | `FluidIcons.Computer` | in Material Symbols «laptop» e «computer» sono la stessa sagoma |
| `Fill.Lightning` | `bolt` | `FluidIcons.Bolt` | la piena non e' nel set (`bolt pieno` nell'elenco per averla) |
| `Regular.LinkSimple` | `link` | `FluidIcons.Link` |  |
| `Regular.ListBullets` | `format_list_bulleted` | `FluidIcons.FormatListBulleted` |  |
| `Regular.ListPlus` | `playlist_add` |  | fuori dal set: per averla, la riga `playlist_add` in fluid-icons.txt |
| `Fill.MagnifyingGlass` | `search` | `FluidIcons.Filled.Search` |  |
| `Regular.MagnifyingGlass` | `search` | `FluidIcons.Search` |  |
| `Fill.MicrophoneStage` | `mic_external_on` |  | vicina, non la stessa sagoma; fuori dal set: per averla, la riga `mic_external_on` in fluid-icons.txt |
| `Regular.MicrophoneStage` | `mic_external_on` |  | vicina, non la stessa sagoma; fuori dal set: per averla, la riga `mic_external_on` in fluid-icons.txt |
| `Regular.MonitorPlay` | `smart_display` |  | vicina, non la stessa sagoma; fuori dal set: per averla, la riga `smart_display` in fluid-icons.txt |
| `Regular.MusicNote` | `music_note` | `FluidIcons.MusicNote` |  |
| `Fill.MusicNotes` | `library_music` |  | vicina, non la stessa sagoma; fuori dal set: per averla, la riga `library_music` in fluid-icons.txt |
| `Regular.MusicNotes` | `library_music` |  | vicina, non la stessa sagoma; fuori dal set: per averla, la riga `library_music` in fluid-icons.txt |
| `Fill.Pause` | `pause` | `FluidIcons.Filled.Pause` |  |
| `Regular.PencilSimple` | `edit` | `FluidIcons.Edit` |  |
| `Fill.Play` | `play_arrow` | `FluidIcons.Filled.PlayArrow` |  |
| `Regular.Playlist` | `queue_music` |  | fuori dal set: per averla, la riga `queue_music` in fluid-icons.txt |
| `Regular.Plugs` | `power` |  | vicina, non la stessa sagoma; fuori dal set: per averla, la riga `power` in fluid-icons.txt |
| `Regular.Plus` | `add` | `FluidIcons.Add` |  |
| `Fill.PushPin` | `push_pin` |  | fuori dal set: per averla, la riga `push_pin` in fluid-icons.txt |
| `Regular.PushPin` | `push_pin` |  | fuori dal set: per averla, la riga `push_pin` in fluid-icons.txt |
| `Regular.Queue` | `playlist_play` |  | fuori dal set: per averla, la riga `playlist_play` in fluid-icons.txt |
| `Regular.Radio` | `radio` |  | fuori dal set: per averla, la riga `radio` in fluid-icons.txt |
| `Regular.Repeat` | `repeat` |  | fuori dal set: per averla, la riga `repeat` in fluid-icons.txt |
| `Regular.RepeatOnce` | `repeat_one` |  | fuori dal set: per averla, la riga `repeat_one` in fluid-icons.txt |
| `Regular.Shuffle` | `shuffle` |  | fuori dal set: per averla, la riga `shuffle` in fluid-icons.txt |
| `Fill.SkipBack` | `skip_previous` |  | fuori dal set: per averla, la riga `skip_previous` in fluid-icons.txt |
| `Fill.SkipForward` | `skip_next` | `FluidIcons.Filled.SkipNext` |  |
| `Regular.SpeakerHifi` | `speaker` |  | fuori dal set: per averla, la riga `speaker` in fluid-icons.txt |
| `Fill.SpeakerHigh` | `volume_up` |  | fuori dal set: per averla, la riga `volume_up` in fluid-icons.txt |
| `Regular.SpeakerHigh` | `volume_up` |  | fuori dal set: per averla, la riga `volume_up` in fluid-icons.txt |
| `Fill.SpeakerLow` | `volume_down` |  | fuori dal set: per averla, la riga `volume_down` in fluid-icons.txt |
| `Regular.SpeakerSimpleHigh` | `volume_up` |  | fuori dal set: per averla, la riga `volume_up` in fluid-icons.txt |
| `Regular.SpeakerSimpleLow` | `volume_down` |  | fuori dal set: per averla, la riga `volume_down` in fluid-icons.txt |
| `Fill.SpeakerX` | `volume_off` | `FluidIcons.VolumeOff` | la piena non e' nel set (`volume_off pieno` nell'elenco per averla) |
| `Regular.SpotifyLogo` | — |  | nessun equivalente; marchio di terzi |
| `Regular.SquaresFour` | `grid_view` | `FluidIcons.GridView` |  |
| `Fill.Stop` | `stop` | `FluidIcons.Filled.Stop` |  |
| `Regular.Television` | `tv` |  | fuori dal set: per averla, la riga `tv` in fluid-icons.txt |
| `Fill.TextAlignLeft` | `format_align_left` |  | fuori dal set: per averla, la riga `format_align_left` in fluid-icons.txt |
| `Regular.TextAlignLeft` | `format_align_left` |  | fuori dal set: per averla, la riga `format_align_left` in fluid-icons.txt |
| `Regular.Timer` | `timer` |  | fuori dal set: per averla, la riga `timer` in fluid-icons.txt |
| `Fill.Translate` | `translate` |  | fuori dal set: per averla, la riga `translate` in fluid-icons.txt |
| `Regular.Translate` | `translate` |  | fuori dal set: per averla, la riga `translate` in fluid-icons.txt |
| `Regular.Trash` | `delete` | `FluidIcons.Delete` |  |
| `Regular.User` | `person` | `FluidIcons.Person` |  |
| `Regular.UserCircle` | `account_circle` |  | fuori dal set: per averla, la riga `account_circle` in fluid-icons.txt |
| `Fill.VinylRecord` | `album` |  | fuori dal set: per averla, la riga `album` in fluid-icons.txt |
| `Regular.VinylRecord` | `album` |  | fuori dal set: per averla, la riga `album` in fluid-icons.txt |
| `Regular.Warning` | `warning` | `FluidIcons.Warning` |  |
| `Fill.Watch` | `watch` |  | fuori dal set: per averla, la riga `watch` in fluid-icons.txt |
| `Regular.Watch` | `watch` |  | fuori dal set: per averla, la riga `watch` in fluid-icons.txt |
| `Fill.Waveform` | `graphic_eq` | `FluidIcons.Filled.GraphicEq` | vicina, non la stessa sagoma |
| `Regular.X` | `close` | `FluidIcons.Close` |  |
| `Fill.XCircle` | `cancel` |  | fuori dal set: per averla, la riga `cancel` in fluid-icons.txt |
| `Regular.YoutubeLogo` | — |  | nessun equivalente; marchio di terzi |

Senza equivalente: `Regular.SpotifyLogo`, `Regular.YoutubeLogo`.
