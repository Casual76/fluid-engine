<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/controlli.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Controlli

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidAudioWaveform.kt`

- `object FluidAudioWaveformDefaults` — Le misure, i colori di serie e i testi di [FluidAudioWaveform] e [FluidAudioTimeline].
  - `val BarPitch: Dp` — Il passo delle barre: 4 dp. Misurato sulla guida dell'iPhone (iOS 26, «Send and receive audio messages» e «Make a recording» di Memo Vocali): 4,5 px a 1,13 px per punto, nella bolla di Messaggi e nell'onda di Memo Vocali. Fiducia alta sul passo, le immagini sono ufficiali.
  - `val BarWidth: Dp` — La barra della riga: 2 dp, tonda. Nella bolla di Messaggi le barre sono 1–2 px nella guida (~1,5 pt); su Android 2 dp hanno lo stesso peso. A occhio, 25 novembre 2026.
  - `val Height: Dp` — L'altezza di serie della riga: 44 dp, il bersaglio del dito. Le barre ne usano il 70% al massimo.
  - `const val BarHeightFraction: Float` — Quanto dell'altezza usa la barra piu' alta della riga.
  - `const val KeyStepMillis: Long` — Il tratto di un salto con la tastiera e con le azioni di TalkBack: 15 s, come i tasti di Memo Vocali.
  - `const val ContentDescription: String` — Il nome per TalkBack.
  - `const val StepBackLabel: String`
  - `const val StepForwardLabel: String`
  - `val Spoken: (positionMillis: Long, durationMillis: Long) -> String` — Il valore a voce: «1:05 di 3:20».
- `@Composable fun FluidAudioWaveform(envelope: FluidAudioEnvelope, position: () -> Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, durationMillis: Long = envelope.durationMillis, enabled: Boolean = true, onScrubStart: (() -> Unit)? = null, onScrub: ((Long) -> Unit)? = null, onScrubEnd: (() -> Unit)? = null, playedColor: Color = MaterialTheme.colorScheme.primary, pendingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy…, barWidth: Dp = FluidAudioWaveformDefaults.BarWidth, barPitch: Dp = FluidAudioWaveformDefaults.BarPitch, contentDescription: String = FluidAudioWaveformDefaults.ContentDescription, valueDescription: (positionMillis: Long, durationMillis: Long) -> String = FluidAudioWaveformDefaults.Spoken, keyStepMillis: Long = FluidAudioWaveformDefaults.KeyStepMillis, stepBackLabel: String = FluidAudioWaveformDefaults.StepBackLabel, stepForwardLabel: String = FluidAudioWaveformDefaults.StepForwardLabel)` — L'**onda audio da scorrere**, tutta nella riga (F8.13, idea `onda-audio`): il messaggio audio di Messaggi di iOS 26. Barre tonde ogni [FluidAudioWaveformDefaults.BarPitch], alte quanto la media del loro tratto di [envelope]; quelle gia' ascoltate in [playedColor], le altre in [pendingColor]; un silenzio è un puntino (la barra piu' bassa e' larga quanto e' alta), come i tratti muti di Memo Vocali.
- `object FluidAudioTimelineDefaults` — Le misure di [FluidAudioTimeline], da Memo Vocali di iOS 26 (guida dell'iPhone, «Make a recording» e «Edit»).
  - `const val DefaultDpPerSecond: Float` — Lo zoom di serie: 100 dp al secondo. Misurato sul righello della registrazione e del taglio di Memo Vocali (113 px fra due secondi a 1,13 px per punto): fiducia alta. A questo zoom una barra ogni 4 dp e' una barra ogni 40 ms.
  - `const val MaxDpPerSecond: Float` — Lo zoom massimo col pizzico: 400 dp al secondo, una barra ogni 10 ms. A occhio, 25 novembre 2026.
  - `const val MinDpPerSecond: Float` — Lo zoom minimo: l'audio intero nella larghezza, ma mai sotto 0,5 dp al secondo.
  - `val BarWidth: Dp` — Le barre dell'onda che scorre: 1 dp ogni 4 dp. Nella guida una barra e' 1 px a 1,13 px per punto: fiducia media.
  - `val PlayheadWidth: Dp` — La testina: 1 dp, con un pallino da 7 dp sopra e sotto la fascia (8 px nella guida).
  - `val PlayheadDot: Dp`
  - `val RulerHeight: Dp` — L'altezza del righello sotto la fascia, con le scritte dei secondi.
  - `val Height: Dp` — L'altezza di serie: 240 dp. In Memo Vocali la fascia e' alta ~305 punti a tutto schermo; qui e' un componente.
  - `const val BarHeightFraction: Float` — Quanto della fascia usa la barra piu' alta.
  - `const val ContentDescription: String` — Il nome per TalkBack.
  - `const val ZoomInLabel: String`
  - `const val ZoomOutLabel: String`
  - `val RecordingRedLight: Color` — Il rosso della testina mentre si registra: `systemRed` delle HIG «Color» (#FF3B30 in chiaro, #FF453A in scuro).
  - `val RecordingRedDark: Color`
  - `fun labelEverySeconds(dpPerSecond: Float, minLabelDp: Float = 64f): Long` — Ogni quanti secondi una scritta del righello, perche' due scritte stiano ad almeno [minLabelDp]: 1, 2, 5, 10, 15, 30 s, poi 1, 2, 5, 10, 15, 30 minuti, poi le ore.
- `class FluidAudioTimelineState(initialDpPerSecond: Float = FluidAudioTimelineDefaults.DefaultDpPerSecond)` — Lo zoom di un'[FluidAudioTimeline], in dp al secondo: il pizzico lo cambia, l'app lo legge o lo fissa. Di serie [FluidAudioTimelineDefaults.DefaultDpPerSecond].
  - `var dpPerSecond by mutableFloatStateOf(initialDpPerSecond)` — Lo zoom di adesso, in dp al secondo.
  - `fun zoomTo(dpPerSecond: Float)` — Fissa lo zoom, dentro i limiti di [FluidAudioTimelineDefaults].
- `@Composable fun rememberFluidAudioTimelineState(initialDpPerSecond: Float = FluidAudioTimelineDefaults.DefaultDpPerSecond): FluidAudioTimelineState` — Lo stato dello zoom per questa composizione.
- `@Composable fun FluidAudioTimeline(envelope: FluidAudioEnvelope, position: () -> Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, durationMillis: () -> Long = { envelope.durationMillis }, state: FluidAudioTimelineState = rememberFluidAudioTimelineState(), recording: Boolean = false, advancing: Boolean = false, enabled: Boolean = !recording, onScrubStart: (() -> Unit)? = null, onScrub: ((Long) -> Unit)? = null, onScrubEnd: (() -> Unit)? = null, barColor: Color = if (recording) recordingRed() else MaterialThem…, playheadColor: Color = if (recording) recordingRed() else MaterialThem…, bandColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh, rulerColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, contentDescription: String = FluidAudioTimelineDefaults.ContentDescription, valueDescription: (positionMillis: Long, durationMillis: Long) -> String = FluidAudioWaveformDefaults.Spoken, keyStepMillis: Long = FluidAudioWaveformDefaults.KeyStepMillis, stepBackLabel: String = FluidAudioWaveformDefaults.StepBackLabel, stepForwardLabel: String = FluidAudioWaveformDefaults.StepForwardLabel, zoomInLabel: String = FluidAudioTimelineDefaults.ZoomInLabel, zoomOutLabel: String = FluidAudioTimelineDefaults.ZoomOutLabel)` — L'**onda che scorre sotto la testina** (F8.13): la registrazione e il taglio di Memo Vocali di iOS 26. La testina sta ferma al centro, con un pallino sopra e uno sotto la fascia; l'onda scorre sotto di lei, a [FluidAudioTimelineState.dpPerSecond] (di serie 100 dp al secondo, come Memo Vocali); sotto, il righello dei secondi.

## `engine-ui/ui/fluid/FluidCalendar.kt`

- `class FluidCalendarState(initialMonth: YearMonth, collapsed: Boolean = false)` — Lo stato di un [FluidCalendar]: il mese che mostra, la striscia della settimana e il collegamento con la lista sotto.
  - `var month: YearMonth by mutableStateOf(initialMonth)` — Il mese mostrato; nella striscia, il mese del giorno di riferimento.
  - `val collapseProgress: Float get()` — Quanto la griglia e' stretta: 0 il mese intero, 1 la striscia della settimana.
  - `val isWeekStrip: Boolean get()` — Vero quando si vede solo la striscia della settimana (derivato: cambia una volta, non a ogni fotogramma).
  - `var isMonthYearPickerShown: Boolean by mutableStateOf(false)` — Vero mentre al posto della griglia ci sono le ruote del mese e dell'anno: il titolo toccato, come in `DatePicker(.graphical)`. Le ruote cambiano [month] mentre girano; il titolo toccato di nuovo torna alla griglia.
  - `fun showMonthYearPicker(show: Boolean)` — Apre ([show] vero) o chiude le ruote del mese e dell'anno. Si aprono solo dalla griglia intera, non dalla striscia.
  - `fun showMonth(month: YearMonth)` — Mostra [month] subito, senza moto.
  - `suspend fun animateToMonth(month: YearMonth)` — Porta il calendario a [month]: scorrendo se e' quello dopo o quello prima, se no subito.
  - `suspend fun collapse()` — Chiude la griglia nella striscia della settimana.
  - `suspend fun expand()` — Riapre la griglia del mese.
  - `val nestedScrollConnection: NestedScrollConnection` — Da dare alla lista sotto il calendario: quando la lista scorre in su la griglia si stringe prima che la lista si muova; quando la lista e' in cima e si tira giu', la griglia si riapre. A dito lasciato la striscia si posa aperta o chiusa, mai a meta'.
  - `companion object`
    - `val Saver` — Conserva mese, giorno di riferimento, striscia e ruote aperte fra una ricreazione e l'altra.
- `@Composable fun rememberFluidCalendarState(initialMonth: YearMonth = YearMonth.now(), collapsed: Boolean = false): FluidCalendarState` — Uno stato del calendario che sopravvive alla ricreazione, aperto su [initialMonth].
- `@Composable fun FluidCalendar(selection: FluidDateSelection, onSelectionChange: (FluidDateSelection) -> Unit, modifier: Modifier = Modifier, state: FluidCalendarState = rememberFluidCalendarState(YearMonth.from(selec…, marks: FluidCalendarMarks? = null, minDate: LocalDate? = null, maxDate: LocalDate? = null, isDateEnabled: ((LocalDate) -> Boolean)? = null, today: LocalDate = remember { LocalDate.now() }, locale: Locale = LocalConfiguration.current.locales[0], labels: FluidCalendarLabels = FluidCalendarLabels())` — Il calendario del mese, da mettere nella pagina: `DatePicker` con lo stile `.graphical` di SwiftUI, `MultiDatePicker` e le decorazioni di `UICalendarView`, con la striscia della settimana dell'app Calendario.

## `engine-ui/ui/fluid/FluidCalendarModel.kt`

- `sealed interface FluidDateSelection` — Cosa e' scelto nel calendario, e quindi cosa fa un tocco su un giorno. Le tre forme di Apple: `DatePicker` (`.graphical`), `MultiDatePicker` e l'intervallo «dal… al…».
  - `data class Single(val date: LocalDate?) : FluidDateSelection` — Un giorno solo ([date] nullo: niente ancora). Il tocco sceglie il giorno toccato.
  - `data class Multiple(val dates: Set<LocalDate> = emptySet()) : FluidDateSelection` — Giorni sparsi, anche in mesi diversi (`MultiDatePicker`). Il tocco accende o spegne il giorno.
  - `data class Range(val start: LocalDate? = null, val end: LocalDate? = null) : FluidDateSelection` — Un intervallo dal [start] all'[end], compresi. Il primo tocco fissa l'inizio; il secondo la fine, se viene dopo (o e' lo stesso giorno: un intervallo di un giorno); un secondo tocco **prima** dell'inizio ricomincia da li', e un tocco a intervallo completo ricomincia da capo. I giorni in mezzo si disegnano come una fascia.
    - `val range: FluidDateRange? get()` — L'intervallo completo, o nulla mentre manca la fine.
- `enum class FluidCalendarMarkStyle` — Come si mostra un segno sotto un giorno.
- `data class FluidCalendarMark(val count: Int = 1, val style: FluidCalendarMarkStyle = FluidCalendarMarkStyle.Dots, val color: Color = Color.Unspecified, val description: String? = null)` — Il segno sotto un giorno: quanti impegni, verifiche o note ha. L'app dice quali giorni hanno un segno e cosa vuol dire; l'engine lo disegna dentro la cella, **senza cambiare l'altezza della riga**: un segno che arriva non sposta niente.
- `class FluidCalendarMarks` — I segni del calendario, un giorno alla volta. Ogni giorno ha il suo stato: un segno che arriva o cambia **ricompone solo il suo giorno**, e un mese che arriva dalla rete ([setMonth]) tocca solo i giorni che cambiano davvero. Si scrive dal thread principale o da qualunque altro (gli stati sono di Compose, la tabella e' protetta).
  - `operator fun get(date: LocalDate): FluidCalendarMark?` — Il segno di [date], o nulla.
  - `operator fun set(date: LocalDate, mark: FluidCalendarMark?)` — Mette o toglie (nulla) il segno di [date].
  - `fun setMonth(month: YearMonth, marks: Map<LocalDate, FluidCalendarMark>)` — I segni di [month], arrivati tutti insieme: i giorni di [month] che non sono in [marks] perdono il loro. Scrive solo i giorni che cambiano.
  - `fun clear()` — Toglie tutti i segni.
- `data class FluidCalendarLabels(val previousMonth: String = "Mese precedente", val nextMonth: String = "Mese successivo", val previousWeek: String = "Settimana precedente", val nextWeek: String = "Settimana successiva", val today: String = "oggi", val rangeStart: String = "inizio dell'intervallo", val rangeEnd: String = "fine dell'intervallo", val inRange: String = "nell'intervallo", val oneMark: String = "1 impegno", val manyMarks: String = "%d impegni", val noDate: String = "Nessuna data", val fromDate: String = "Dal %s", val days: String = "%d giorni", val showMonthYear: String = "Scegli mese e anno", val hideMonthYear: String = "Torna ai giorni", val monthWheel: String = "Mese", val yearWheel: String = "Anno")` — Le parole del calendario: i tasti del mese, e cosa legge TalkBack su un giorno («25 novembre, 3 impegni»). Di serie in italiano; [english] per l'inglese.
  - `companion object`
    - `fun english(): FluidCalendarLabels` — Le stesse in inglese.
- `object FluidCalendarDefaults` — Le misure del calendario, da `DatePicker(.graphical)` di SwiftUI e dalle HIG «Pickers» (iOS 26), misurate il 24 novembre 2026 sulle immagini ufficiali (`artifacts/liquid-glass/sessioni/f8-8/riferimenti/misure.md`).
  - `val MaxColumnWidth: Dp` — La colonna piu' larga: **64 dp**. Il calendario divide la sua larghezza per sette (come `DatePicker` e la vista mese di Calendario, 55,9 pt su un iPhone da 393), ma su un tablet sette colonne da 220 dp non sono un calendario: oltre questa misura la griglia resta al centro. A occhio, 24 novembre 2026.
  - `const val CircleFraction: Float` — Il cerchio del giorno scelto e il passo delle righe, in frazione della colonna: **0,82**. Il render di `graphicalStyle` da' cerchio/colonna 0,815 e riga/colonna 0,82 (i cerchi di due righe si toccano); le HIG 0,86. Fiducia media.
  - `val MinRowHeight: Dp` — La riga non scende mai sotto i 44 dp di un bersaglio (HIG), anche su una finestra stretta.
  - `val MinCellAtFontScale: Dp` — Il lato minimo di una cella col testo grande: **48 dp per la scala del carattere**, la regola delle sette colonne di `FluidDateTimeSheet` (3.1.0). Quando sette celle cosi' non ci stanno, la griglia scorre di lato invece di stringersi, e il mese cambia con le frecce.
  - `val HeaderHeight: Dp` — La testata col mese: **44 dp**, la fila di un bersaglio; il titolo a 17 sp semibold (`DatePicker`, fiducia media).
  - `val WeekdayRowHeight: Dp` — La fila delle lettere dei giorni: **20 dp**, lettere a 13 sp semibold maiuscole nel grigio terziario. Dal centro delle lettere al centro della prima riga ci sono cosi' 10 + 22 = 32 dp: 31 nel render di SwiftUI, 33 nelle HIG.
  - `const val DayFontSp: Float` — Il numero del giorno: **20 sp** regular; scelto e oggi semibold, e il scelto 7% piu' grande (misurato: +7%).
  - `const val SelectedDayScale: Float`
  - `const val SelectedTintAlpha: Float` — L'accento sotto il giorno scelto: **12%** sul fondo, in chiaro e in scuro (render di SwiftUI e HIG, fiducia alta).
  - `const val RangeTintAlpha: Float` — La fascia di un intervallo: l'accento al **12%**, alta quanto il cerchio, con le estremita' piene nell'accento e il numero bianco. Apple non ha un intervallo nel calendario: e' la forma della fascia di Voli e di Calendario quando si trascina un evento su piu' giorni, a occhio il 24 novembre 2026.
  - `val MarkDotSize: Dp` — I pallini dei segni: **5 dp**, **3 dp** fra l'uno e l'altro, al piu' tre (il pallino di Calendario, a occhio).
  - `val MarkDotGap: Dp`
  - `const val MaxMarkDots: Int`
  - `const val MarkCountFontSp: Float` — Il numero di un segno: 11 sp, a metà fra il numero del giorno e il fondo del cerchio.
  - `const val Rows: Int` — Le righe di un mese: sempre **sei**, come `DatePicker`: cambiare mese non cambia l'altezza e la lista sotto non salta.
  - `const val PageResponseSeconds: Double` — La molla di una pagina lasciata a meta' o di un tocco sulle frecce: critica, **0,38 s** ([FluidMotion.ResponseStandard]).
  - `const val CollapseResponseSeconds: Double` — La molla della striscia che si chiude o si apre dopo un lancio della lista: critica, **0,30 s**.

## `engine-ui/ui/fluid/FluidCodeField.kt`

- `class FluidCodeFieldState(val length: Int)` — Lo stato di un [FluidCodeField]: il codice scritto, e l'esito che l'app gli dà. È dell'app (ricordato con [rememberFluidCodeFieldState]): l'app legge [code], e a codice sbagliato chiama [fail].
  - `var code: String by mutableStateOf("")` — Il codice scritto finora, mai più lungo di [length].
  - `val isComplete: Boolean get()` — Vero quando tutte le caselle sono piene.
  - `var isError: Boolean by mutableStateOf(false)` — Vero dal [fail] finché non si riscrive: le caselle sono nella tinta d'errore.
  - `fun set(value: String)` — Scrive [value] (tagliato a [length]): lo usano l'incolla e l'autocompilazione. Toglie l'errore.
  - `fun clear()` — Svuota le caselle.
  - `fun fail()` — Il codice non è giusto: le caselle tremano con l'aptica d'errore, prendono la tinta d'errore e si svuotano (come il codice di sblocco di iOS e i campi del codice dell'Account Apple).
- `@Composable fun rememberFluidCodeFieldState(length: Int = 6): FluidCodeFieldState` — Uno stato del campo a codice di [length] caselle, ricordato nella composizione.
- `object FluidCodeFieldDefaults` — Le misure del campo a codice. **A occhio** (23 novembre 2026), dai campi del codice dell'Account Apple e di Impostazioni su iOS 26 (fiducia media: nessuna misura al pixel), nella famiglia dei campi dell'engine.
  - `val CellHeight: Dp` — L'altezza di una casella: 52 dp, quella del campo di vetro dell'engine ([FluidSearchFieldDefaults.Height], F6B.15).
  - `val CellMinWidth: Dp` — La larghezza minima di una casella: 48 dp, il bersaglio minimo; col testo grande la casella cresce con la cifra e le caselle vanno a capo a gruppi, mai sotto i 48 dp (scheda dell'idea, «rischi»).
  - `val CellSpacing: Dp` — Lo spazio fra due caselle: 8 dp.
  - `val GroupSpacing: Dp` — Lo spazio fra due gruppi (3 + 3 per sei cifre, 4 + 4 per otto): 20 dp, come lo stacco dei codici di Apple.
  - `val CellRadius: Dp` — Il raggio di una casella: quello dei controlli ([FluidRadius.Control], 12 dp), con l'angolo continuo.
  - `val FocusBorder: Dp` — Il bordo della casella attiva e di quella in errore: 2 dp, nell'accento o nella tinta d'errore.
- `@Composable fun FluidCodeField(state: FluidCodeFieldState, onComplete: (String) -> Unit, modifier: Modifier = Modifier, digitsOnly: Boolean = true, secret: Boolean = false, verifying: Boolean = false, enabled: Boolean = true, autoFocus: Boolean = false, pasteButton: Boolean = true, label: String? = null, labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current, singleRow: Boolean = false)` — Il campo a codice (F8.3, idea `campo-codice`): le caselle per un codice di 4–8 cifre (verifica in due passaggi, PIN, codice di abbinamento). È **un campo solo** per la tastiera, con le caselle disegnate sopra: il fuoco avanza e torna indietro da solo, «cancella» toglie l'ultima cifra, e a codice completo chiama [onComplete].

## `engine-ui/ui/fluid/FluidColorPicker.kt`

- `enum class FluidColorPickerMode` — I tre modi del foglio, come i segmenti di `UIColorPickerViewController`: Griglia, Spettro, Cursori.
- `class FluidRecentColors(initial: List<Color> = emptyList(), val capacity: Int = FluidColorPickerDefaults.RecentCapacity, onChange: (List<Color>) -> Unit = {})` — I colori recenti del selettore: l'ultimo scelto davanti, senza doppioni, al massimo [capacity]. Il foglio ci aggiunge il colore scelto quando si chiude (se e' cambiato) e quello che il «+» salva, come i colori salvati di `UIColorPickerViewController`.
  - `var colors: List<Color> by mutableStateOf(normalize(initial))` — I colori, il piu' recente per primo.
  - `fun add(color: Color)` — Mette [color] davanti (togliendolo da dov'era) e lascia cadere il piu' vecchio oltre [capacity].
  - `fun clear()` — Dimentica tutto.
- `@Composable fun rememberFluidRecentColors(initial: List<Color> = emptyList(), capacity: Int = FluidColorPickerDefaults.RecentCapacity, onChange: (List<Color>) -> Unit = {}): FluidRecentColors` — I colori recenti, conservati attraverso i cambi di configurazione. [initial] e' quello che l'app aveva salvato, [onChange] dove salvarli di nuovo.
- `data class FluidColorPickerStrings(val title: String = "Colori", val grid: String = "Griglia", val spectrum: String = "Spettro", val sliders: String = "Cursori", val red: String = "Rosso", val green: String = "Verde", val blue: String = "Blu", val hex: String = "Codice esadecimale", val opacity: String = "Opacità", val recents: String = "Recenti", val addToRecents: String = "Salva il colore", val eyedropper: String = "Contagocce", val eyedropperHint: String = "Trascina la lente sul colore e solleva il dito", val close: String = "Chiudi", val current: String = "Colore scelto", val custom: String = "Altro colore", val colorName: (Color) -> String = ::fluidColorName)` — Le parole del selettore, in italiano di serie. [colorName] dice un colore a parole a chi usa TalkBack: un colore da solo non e' un'etichetta.
  - `fun modeLabel(mode: FluidColorPickerMode): String` — Il nome di un modo, per il segmento.
- `object FluidColorPickerDefaults` — Le misure del selettore. Dove non c'e' una misura di Apple a cui appoggiarsi il KDoc dice «a occhio» con la data: le schermate di riferimento sono quelle di `UIColorPickerViewController` su iPhone (iOS 17–26), fiducia media sulla struttura e bassa sui punti.
  - `val WellSize: Dp` — Il diametro del pozzetto: **28 dp**, quanto `UIColorWell` in una riga di Impostazioni. A occhio, 24 novembre 2026.
  - `val WellRing: Dp` — Lo spessore dell'anello dell'arcobaleno del pozzetto: **3 dp** (a occhio, 24 novembre 2026, su `UIColorWell`).
  - `val WellGap: Dp` — Il vuoto fra l'anello e il colore: **2 dp** (a occhio, 24 novembre 2026).
  - `val WellTarget: Dp` — Il bersaglio del pozzetto: **44 dp**, il minimo di Apple per un tocco.
  - `const val GridColumns: Int` — La griglia: **12 colonne per 10 righe**, la prima di grigi dal bianco al nero e nove di tinte dallo scuro al chiaro, come la griglia di `UIColorPickerViewController` (fiducia media: stessa struttura; i valori sono quelli della griglia di iOS ripresi dalle schermate, fiducia bassa sul singolo colore).
  - `const val GridRows: Int` — Le righe della griglia: vedi [GridColumns].
  - `val PanelCorner: Dp` — Il raggio degli angoli della griglia e dello spettro: **16 dp**. Misurato sulla schermata di iOS 26 a @3x (la griglia del selettore su iPhone, `riferimentipple-colorpicker-griglia-iphone-ios26.webp` di F8.7): ~15 pt, con le caselle da 29,7 pt. Fiducia media (una schermata), 24 novembre 2026.
  - `val SliderHeight: Dp` — L'altezza della pista dei cursori: **30 dp**, una capsula col colore dentro (a occhio, 24 novembre 2026, su iOS).
  - `val ThumbRing: Dp` — L'anello bianco del pomello dei cursori e del segno dello spettro: **3 dp** (a occhio, 24 novembre 2026).
  - `val SpectrumMarker: Dp` — Il diametro del segno sullo spettro: **28 dp** (a occhio, 24 novembre 2026, sul segno di iOS).
  - `val CurrentSwatch: Dp` — Il quadrato del colore scelto in fondo al foglio: **74 dp**, angoli `FluidRadius.Card` (18). Misurato sulla stessa schermata di iOS 26: 74 × 75 pt, angoli ~17 pt (fiducia media, 24 novembre 2026).
  - `val RecentDot: Dp` — Il diametro dei colori recenti: **28 dp**, i pallini di [FluidColorDot] (bersaglio 48 dp). Misurato su iOS 26: 28 pt, a 46 pt di passo, in righe che vanno a capo col «+» in fondo (fiducia media, 24 novembre 2026).
  - `val ValueBoxWidth: Dp` — La casella del valore accanto ai cursori («255», «100 %»): **64 × 34 dp**, a capsula, chiara sul foglio. iOS 26 mette il valore in una capsula bianca accanto alla pista (fiducia media sulla forma, a occhio sulla misura).
  - `val ValueBoxHeight: Dp` — L'altezza della casella del valore: vedi [ValueBoxWidth].
  - `const val RecentCapacity: Int` — Quanti colori recenti tiene la lista di serie: **10** (a occhio, 24 novembre 2026: una fila che scorre poco).
  - `val CheckerCell: Dp` — Il lato dei quadretti della scacchiera dietro i colori trasparenti: **6 dp** (a occhio, 24 novembre 2026).
  - `val SheetHeight: Dp` — L'altezza del foglio a riposo: **640 dp**, quanto serve ai tre modi con l'opacita' e i recenti; sotto quella altezza il foglio e' vetro staccato dai bordi, sopra (il secondo detent, `Large`) diventa pagina come ogni foglio di vetro. A occhio, 24 novembre 2026: il selettore di iOS sta quasi a tutta altezza su iPhone.
  - `val MaxContentWidth: Dp` — La larghezza massima del contenuto del foglio, su un tablet: **420 dp** (a occhio, 24 novembre 2026).
  - `val LoupeSize: Dp` — Il diametro della lente del contagocce: **120 dp**. Sulla lente di iPadOS delle HIG («Color wells», l'immagine della lente su un iPad da 12,9") ~122 pt (fiducia bassa: la scala dell'immagine e' stimata), 24 novembre 2026.
  - `const val LoupePixels: Int` — Quanti pixel per lato mostra la lente: **11**, dispari perche' quello scelto stia nel mezzo.
  - `val LoupeRing: Dp` — Lo spessore dell'anello del colore attorno alla lente: **12 dp** (sulla stessa immagine ~14 pt; fiducia bassa).
  - `const val LoupeVeilAlpha: Float` — Il velo sull'app mentre si sceglie: nero al **28 %**, con una finestra chiara attorno alla lente larga **3,4** raggi e alta tre quarti, come la lente di iPadOS (264 × 200 px attorno a una lente di 156: a occhio sulla stessa immagine, 24 novembre 2026).
  - `const val LoupeWindow: Float` — Vedi [LoupeVeilAlpha]: la finestra chiara, in raggi della lente.
  - `const val EyedropperCaptureDelayMillis: Long` — Quanto aspetta il contagocce prima di fotografare la finestra: **420 ms**, il tempo in cui il foglio esce del tutto (la molla d'uscita dell'host e' ferma verso 400 ms). Col movimento ridotto il foglio sparisce subito e bastano 60 ms.
  - `const val EyedropperCaptureDelayReducedMillis: Long` — Vedi [EyedropperCaptureDelayMillis]: col movimento ridotto.
- `object FluidColorPickerTags` — I tag dei test e delle riprese.
  - `const val Well`
  - `const val Picker`
  - `const val Modes`
  - `const val Grid`
  - `const val Spectrum`
  - `const val Hex`
  - `const val Opacity`
  - `const val Current`
  - `const val Recents`
  - `const val AddRecent`
  - `const val Eyedropper`
  - `const val Loupe`
  - `const val Close`
  - `fun slider(channel: String): String`
- `fun fluidColorHex(color: Color, withAlpha: Boolean = false): String` — Il codice esadecimale di [color] in sRGB, `#RRGGBB` (o `#RRGGBBAA` con [withAlpha]), in maiuscolo.
- `fun fluidParseHexColor(text: String): Color?` — Il colore di un codice esadecimale: `RGB`, `RRGGBB` o `RRGGBBAA`, col `#` o senza, maiuscole o minuscole, spazi ai lati. Null se il testo non e' un colore.
- `fun fluidColorName(color: Color): String` — Un colore detto a parole, in italiano: «rosso», «blu scuro», «verde chiaro», «grigio», «bianco», con l'opacita' se non e' piena. Per TalkBack: e' il nome che il selettore da' a ogni casella, al pozzetto e ai recenti.
- `val FluidColorGrid: List<Color> by lazy` — I 120 colori della griglia, riga per riga: la prima di grigi dal bianco al nero, poi nove righe dallo scuro al chiaro, come la griglia di `UIColorPickerViewController`. I valori sono quelli del selettore di sistema, campionati al centro di ogni casella della schermata di iOS 26 su iPhone (F8.7, `riferimenti/apple-colorpicker-griglia-iphone-ios26.webp`;
- `@Composable fun FluidColorWell(color: Color, onColorChange: (Color) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, supportsOpacity: Boolean = true, recents: FluidRecentColors = rememberFluidRecentColors(), strings: FluidColorPickerStrings = FluidColorPickerStrings())` — Il pozzetto: un cerchio col colore scelto dentro l'anello dell'arcobaleno, come `ColorPicker` di SwiftUI e `UIColorWell`. Toccato apre [FluidColorPickerSheet], che **nasce dal pozzetto** e ci rientra (il viaggio del foglio di vetro dell'engine). Bersaglio di 44 dp; per TalkBack dice [label] e il nome del colore.
- `@Composable fun FluidColorRow(title: String, color: Color, onColorChange: (Color) -> Unit, modifier: Modifier = Modifier, subtitle: String = "", enabled: Boolean = true, supportsOpacity: Boolean = true, recents: FluidRecentColors = rememberFluidRecentColors(), strings: FluidColorPickerStrings = FluidColorPickerStrings())` — Una riga delle impostazioni col pozzetto a destra, come `ColorPicker("Sfondo", selection: …)` dentro un `Form` di SwiftUI: tutta la riga si tocca e apre il foglio, che nasce dal pozzetto. Per TalkBack e' un tasto che dice il titolo e il nome del colore.
- `@Composable fun FluidColorSwatches(colors: List<Color>, selected: Color?, onSelect: (Color) -> Unit, modifier: Modifier = Modifier, strings: FluidColorPickerStrings = FluidColorPickerStrings(), customLabel: String? = strings.custom, supportsOpacity: Boolean = false, recents: FluidRecentColors = rememberFluidRecentColors())` — La versione corta, per una riga delle impostazioni: una fila di campioni ([FluidColorDot], gli stessi pallini di `FluidAccentPicker`, bersaglio 48 dp, ruolo `RadioButton` e nome a parole) e, se [customLabel] non e' null, un pozzetto in fondo per un colore su misura. La fila scorre di lato se non ci sta.
- `@Composable fun FluidColorPickerSheet(visible: Boolean, color: Color, onColorChange: (Color) -> Unit, onDismissRequest: () -> Unit, supportsOpacity: Boolean = true, recents: FluidRecentColors = rememberFluidRecentColors(), source: FluidTransitionSource? = null, strings: FluidColorPickerStrings = FluidColorPickerStrings())` — Il foglio del selettore: il foglio di vetro dell'engine (`FluidGlassSheet`) con dentro [FluidColorPicker], a [FluidColorPickerDefaults.SheetHeight] (vetro) e a tutta altezza (pagina). Con [source] nasce da quella vista e ci rientra (il pozzetto lo passa da se'). Il colore scelto entra nei [recents] quando il foglio si chiude, se e' cambiato.
- `@Composable fun FluidColorPicker(color: Color, onColorChange: (Color) -> Unit, modifier: Modifier = Modifier, supportsOpacity: Boolean = true, recents: FluidRecentColors? = null, initialMode: FluidColorPickerMode = FluidColorPickerMode.Grid, onEyedropper: (() -> Unit)? = null, onClose: (() -> Unit)? = null, strings: FluidColorPickerStrings = FluidColorPickerStrings(), mode: FluidColorPickerMode? = null, onModeChange: (FluidColorPickerMode) -> Unit = {})` — Il contenuto del selettore, anche fuori da un foglio (in un popover, in una pagina su un tablet): la testata col contagocce, il titolo e Chiudi; i tre modi; l'opacita'; il colore scelto e i recenti.
- `@Composable fun FluidColorEyedropper(active: Boolean, onPick: (Color?) -> Unit, strings: FluidColorPickerStrings = FluidColorPickerStrings())` — Il contagocce: quando [active], fotografa la finestra dell'app (`PixelCopy`, dopo che il foglio e' uscito) e mostra la lente al centro; il dito la porta, la lente ingrandisce gli [FluidColorPickerDefaults.LoupePixels] pixel attorno al suo centro, e al rilascio [onPick] riceve il colore del pixel nel mezzo. Indietro, o una finestra che non si lascia fotografare (`FLAG_SECURE`), danno null.

## `engine-ui/ui/fluid/FluidControls.kt`

- `enum class FluidButtonStyle` — How much a control's colour says about it.
- `enum class FluidButtonSize`
- `object FluidButtonDefaults`
  - `fun height(size: FluidButtonSize): Dp`
  - `fun horizontalPadding(size: FluidButtonSize): Dp`
  - `fun shape(size: FluidButtonSize): Shape`
- `@Composable fun FluidButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: FluidButtonStyle = FluidButtonStyle.Filled, size: FluidButtonSize = FluidButtonSize.Medium, enabled: Boolean = true, loading: Boolean = false, fillWidth: Boolean = false, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, leading: (@Composable () -> Unit)? = null)` — A button.
- `@Composable fun <T> FluidSegmentedControl(options: List<T>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: (@Composable (option: T, selected: Boolean) -> Unit)? = null, label: (T) -> String)` — A segmented control.
- `@Composable fun FluidChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leading: (@Composable () -> Unit)? = null)` — A filter pill.
- `@Composable fun FluidRowValue(text: String, modifier: Modifier = Modifier)` — A stepper-free numeric or textual value shown at the trailing edge of a settings row.
- `@Composable fun FluidHairline(modifier: Modifier = Modifier, startInset: Dp = 0.dp, color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha…)` — A hairline. 0.5dp is a real width on every screen the app runs on, and it is what iOS draws.
- `@Composable fun FluidColorDot(color: Color, selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier, size: Dp = 30.dp)` — A circular dot, used to preview an accent colour in the picker.

## `engine-ui/ui/fluid/FluidDateCapsule.kt`

- `object FluidDateCapsuleDefaults` — Le misure della capsula, da `DatePicker(.compact)` (HIG «Pickers» di iOS 26 e la scheda dell'evento di Calendario in iOS 26 e 27, misurate il 24 novembre 2026: `artifacts/liquid-glass/sessioni/f8-8/riferimenti/misure.md`, § 2).
  - `val Height: Dp` — L'altezza: **34 dp** (HIG 34 pt, Calendario 34,5 in iOS 27 e 36 in iOS 26). Fiducia media.
  - `val TouchHeight: Dp` — Il bersaglio intorno: **44 dp**, quanto chiedono le HIG; la capsula sta al centro.
  - `val HorizontalPadding: Dp` — Il margine del testo: **13 dp** per lato (13–14 pt misurati). Il raggio e' meta' dell'altezza: una capsula piena.
  - `const val FontSp: Float` — Il testo: **17 sp** nel colore dell'etichetta (`label`), come in Calendario.
  - `val Fill: Color` — Il fondo: il grigio `tertiarySystemFill`, **(118, 118, 128) al 12%**, in chiaro e in scuro (HIG di iOS 26; iOS 27 lo porta al 16%). Fiducia alta sul colore.
- `@Composable fun FluidDateCapsule(selection: FluidDateSelection, onSelectionChange: (FluidDateSelection) -> Unit, modifier: Modifier = Modifier, label: String? = null, minDate: LocalDate? = null, maxDate: LocalDate? = null, isDateEnabled: ((LocalDate) -> Boolean)? = null, marks: FluidCalendarMarks? = null, today: LocalDate = remember { LocalDate.now() }, locale: Locale = LocalConfiguration.current.locales[0], labels: FluidCalendarLabels = FluidCalendarLabels(), text: (FluidDateSelection) -> String = { fluidDateCapsuleText(it, locale, labels) }, closeOnSelection: Boolean = false)` — La capsula di una data in una riga, come `DatePicker` con lo stile `.compact`: «23 nov 2026» (la data media della lingua) in un grigio leggero. Toccata, **diventa il popover del calendario**; la capsula scrive subito il giorno scelto, e il popover ci rientra quando si tocca fuori: il popover dalla sorgente dell'engine (F5B.1), anche sul telefono (`FluidCompactAdaptation.Popover`).
- `@Composable fun FluidDurationCapsule(seconds: Long, onSecondsChange: (Long) -> Unit, modifier: Modifier = Modifier, label: String? = null, showSeconds: Boolean = false, maxHours: Int = 23, minuteInterval: Int = 1, minimumSeconds: Long = if (showSeconds) 1L else 60L, units: FluidDurationUnits = FluidDurationUnits(), text: (Long) -> String = { fluidDurationText(it, showSeconds, units) })` — La capsula di una durata («45 min», «1 h 30 min»): toccata diventa il popover con la ruota del timer di Orologio ([FluidDurationWheel], F8.2), la `UIDatePicker` in modo `countDownTimer` con l'aptica a scatti. La ruota non e' rifatta: e' quella, con le sue colonne collegate e il suo minimo. Il popover si chiude toccando fuori.

## `engine-ui/ui/fluid/FluidFieldClipboard.kt`

- `class FluidPasteKind private constructor(internal val id: String, val codeLengths: IntRange, val digitsOnly: Boolean)` — Il tipo di dato che un campo aspetta: il **dato concordato** della nota di Alessio («se è richiesto un OTP, o un link, o uno screen, allora si appaiono in automatico; appunti normali no»). Il tasto compare **solo** se negli appunti c'è questo tipo di dato.
  - `companion object`
    - `fun code(lengths: IntRange = 4..8, digitsOnly: Boolean = true): FluidPasteKind` — Un codice di [lengths] cifre (di serie 4–8: verifica in due passaggi, PIN, abbinamento).
    - `val Link: FluidPasteKind` — Un link web (`http`, `https`).
    - `val Image: FluidPasteKind` — Un'immagine, per esempio uno screenshot appena fatto.
- `sealed class FluidPasted` — Quello che il tasto incolla consegna, già del tipo concordato.
  - `data class Code(val code: String) : FluidPasted()` — Il codice, solo le sue cifre («123-456» diventa «123456»).
  - `data class Link(val url: String) : FluidPasted()` — Il link, intero.
  - `data class Image(val uri: Uri, val mimeType: String) : FluidPasted()` — L'immagine: l'indirizzo e il tipo. Leggerla (con `contentResolver`) è dell'app.
- `class FluidPasteOffer internal constructor(val kind: FluidPasteKind, private val context: Context, private val manager: ClipboardManager?)` — L'offerta del tasto incolla: se negli appunti c'è il dato di [kind] ([available]) e, al tocco, il dato ([take]). Si aggiorna quando gli appunti cambiano, quando la finestra torna in primo piano (la persona ha copiato in un'altra app) e quando la pagina riprende. Non legge mai il contenuto prima di [take] (da Android 12).
  - `var available: Boolean by mutableStateOf(false)` — Vero se il tasto deve comparire.
  - `fun take(): FluidPasted?` — Il dato, letto ora (è il tocco della persona: qui Android può mostrare il suo avviso). Null se nel frattempo gli appunti sono cambiati e il dato non c'è più.
- `@Composable fun rememberFluidPasteOffer(kind: FluidPasteKind): FluidPasteOffer` — L'offerta del tasto incolla per [kind], che si aggiorna da sola. Per un tasto suo; quello pronto è [FluidPasteButton].
- `@Composable fun FluidPasteButton(kind: FluidPasteKind, onPaste: (FluidPasted) -> Unit, modifier: Modifier = Modifier, label: String? = null, labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — Il tasto incolla per i dati concordati (F8.3, idea `tasto-incolla` rivista da Alessio): un tasto di vetro dell'engine che **compare da solo solo se negli appunti c'è il dato che il campo aspetta** — un codice per il campo del codice, un link per un campo link, un'immagine per un campo che accetta uno screenshot — e altrimenti non c'è. Gli appunti normali non lo fanno comparire.
- `class FluidSensitiveCopy internal constructor(internal val token: String, val expiresAtElapsedRealtime: Long, val duration: Duration)` — Una copia delicata in corso (F8.3, `copia-delicata`): il suo tempo e la sua fine. [isPending] è vero finché gli appunti non sono stati cancellati o sostituiti.
  - `var isPending: Boolean by mutableStateOf(true)` — Vero finché la copia aspetta la sua cancellazione.
  - `var wasCleared: Boolean by mutableStateOf(false)` — Vero se alla fine gli appunti sono stati cancellati dall'engine (falso: sostituiti da altro, o lasciati nel dubbio).
  - `fun remainingMillis(now: Long = SystemClock.elapsedRealtime()): Long` — I millisecondi che restano, mai sotto zero.
- `object FluidSensitiveClipboard` — La copia delicata: un dato che si copia **segnato come riservato** e che **si cancella dopo un tempo** (di serie 60 s). È `UIPasteboard.setItems(_:options:)` con `.expirationDate` di iOS, come copiano le app di password.
  - `val DefaultExpiry: Duration` — Il tempo di serie: 60 s, quello della scheda dell'idea (e delle app di password su iOS, che di solito danno 60–90 s).
  - `fun copy(context: Context, text: CharSequence, label: CharSequence = "", expireAfter: Duration = DefaultExpiry): FluidSensitiveCopy` — Copia [text] come dato riservato, che si cancella dopo [expireAfter] se è ancora negli appunti. [label] è l'etichetta del clip (non si vede da nessuna parte, ma i gestori degli appunti la leggono): di serie vuota, così non racconta cosa è stato copiato.
  - `fun clearNow(copy: FluidSensitiveCopy)` — Cancella subito gli appunti di [copy], se sono ancora i suoi.
- `fun Context.fluidCopySensitive(text: CharSequence, label: CharSequence = "", expireAfter: Duration = FluidSensitiveClipboard.DefaultExpiry): FluidSensitiveCopy` — Copia [text] come dato riservato che si cancella dopo [expireAfter] (di serie 60 s): [FluidSensitiveClipboard.copy].
- `@Composable fun FluidCopyButton(text: () -> String, modifier: Modifier = Modifier, label: String? = null, expireAfter: Duration? = FluidSensitiveClipboard.DefaultExpiry, labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current, backdrop: GlassBackdropState = currentGlassBackdrop())` — Il tasto della copia delicata (F8.3): il tasto con esito di Copia ([FluidActionPreset.Copy]) che copia [text] come dato riservato, poi **dice «Copiato» e fa vedere il tempo che resta** nello stesso vetro, col pieno che avanza del contatore «Annulla» ([FluidUndoCountdown]); TalkBack legge «Copiato, si cancella fra 58 s». Alla fine gli appunti si cancellano (se sono ancora quelli) e il tasto torna Copia.

## `engine-ui/ui/fluid/FluidFilterChips.kt`

- `enum class FluidFilterMode` — Come si sceglie nella fila: una sola pillola alla volta (gli ambiti, esclusivi) o quante se ne vuole.
- `data class FluidFilterValue(val id: String, val label: String)` — Un valore di una pillola a piu' valori (il periodo: «7 giorni», «30 giorni», «Altro»).
- `data class FluidFilter(val id: String, val label: String, val icon: ImageVector? = null, val priority: FluidToolbarPriority = FluidToolbarPriority.Automatic, val values: List<FluidFilterValue> = emptyList(), val enabled: Boolean = true)` — Una pillola della fila.
  - `val hasValues: Boolean get()` — Vero se la pillola si apre in un menu di valori.
- `data class FluidFilterSelection(val selected: Set<String> = emptySet(), val values: Map<String, String> = emptyMap())` — La selezione di una fila, posseduta dall'app: quali pillole sono accese e, per quelle a piu' valori, quale valore. E' **la stessa** per la fila e per il menu: una sola funzione ([toggle], [choose]) la cambia da tutti e due i posti.
  - `fun isSelected(id: String): Boolean`
  - `fun valueOf(id: String): String?` — Il valore scelto della pillola [id], o null se la pillola e' spenta o non ha valori.
  - `val count: Int get()` — Quante pillole sono accese.
  - `fun toggle(id: String, mode: FluidFilterMode): FluidFilterSelection` — Il tocco su una pillola semplice. [FluidFilterMode.Multiple]: accende o spegne. [FluidFilterMode.Single]: la sceglie al posto delle altre; toccata quando e' gia' la scelta resta com'e' (gli ambiti, esclusivi, ne hanno sempre una).
  - `fun choose(id: String, value: String?, mode: FluidFilterMode): FluidFilterSelection` — La scelta di un valore della pillola [id] ([value] null: «Qualsiasi», la pillola si spegne).
  - `companion object`
    - `val None: FluidFilterSelection`
    - `fun of(vararg ids: String): FluidFilterSelection` — Una selezione con le pillole [ids] accese.
    - `val Saver: Saver<FluidFilterSelection, Any>` — Per `rememberSaveable(stateSaver = FluidFilterSelection.Saver)`: sopravvive alla morte del processo.
- `data class FluidFilterSet(val filters: List<FluidFilter>, val mode: FluidFilterMode = FluidFilterMode.Multiple, val label: String? = null)` — Una fila dichiarata: le pillole, il modo e il nome del tasto in cui si raccoglie. E' cio' che restituiscono i preset ([FluidFilterPresets]) e cio' che `FluidSearchSpec(filters = …)` porta come gettoni suggeriti (F6B.8b).
- `data class FluidFilterLabels(val filters: String = "Filtri", val more: String = "Altro", val activeCount: String = "%1\$s · %2\$d", val activeSpoken: String = "attivi: %1\$d", val anyValue: String = "Qualsiasi", val removeRecent: String = "Rimuovi dai recenti", val noResults: String = "Nessun risultato")` — Le parole che la fila e i suggerimenti dicono da se'. **In italiano di serie**, come [FluidLabels]: un'app tradotta le passa una volta alla radice con [LocalFluidFilterLabels].
- `val LocalFluidFilterLabels: ProvidableCompositionLocal<FluidFilterLabels>` — Le parole della fila e dei suggerimenti per tutto l'albero.
- `object FluidFilterChipsDefaults` — Le misure della fila.
  - `val Spacing: Dp` — Lo spazio fra due pillole: come fra due tasti di una fila di controlli.
- `@Composable fun FluidFilterChips(filters: List<FluidFilter>, selection: FluidFilterSelection, onChange: (FluidFilterSelection) -> Unit, mode: FluidFilterMode = FluidFilterMode.Multiple, modifier: Modifier = Modifier, label: String? = null, labels: FluidFilterLabels = LocalFluidFilterLabels.current)` — Una fila di filtri a pillole. Si usa sotto il campo di ricerca (lo slot `suggestions` di `FluidSearchSpec`, sopra [FluidSearchSuggestions]), nel pannello dei suggerimenti, e anche fuori dalla ricerca.
- `@Composable fun FluidFilterChips(set: FluidFilterSet, selection: FluidFilterSelection, onChange: (FluidFilterSelection) -> Unit, modifier: Modifier = Modifier, labels: FluidFilterLabels = LocalFluidFilterLabels.current)` — Una fila dichiarata ([FluidFilterSet], per esempio da [FluidFilterPresets]).
- `enum class FluidSuggestionKind` — Che cosa e' un suggerimento: decide il simbolo di serie e, per i recenti, la ✕.
- `data class FluidSearchSuggestion(val id: String, val title: String, val subtitle: String? = null, val kind: FluidSuggestionKind = FluidSuggestionKind.Result, val relevance: Float = 0f, val icon: ImageVector? = null)` — Un suggerimento della lista sotto il campo.
- `@Composable fun FluidSearchSuggestions(query: String, suggestions: List<FluidSearchSuggestion>, onSuggestionClick: (FluidSearchSuggestion) -> Unit, modifier: Modifier = Modifier, onRemoveRecent: ((FluidSearchSuggestion) -> Unit)? = null, loading: Boolean = false, labels: FluidFilterLabels = LocalFluidFilterLabels.current)` — La lista dei suggerimenti sotto il campo (lo slot `suggestions` di `FluidSearchSpec`, o dove serve).
- `object FluidFilterPresets` — I preset della fila e dei suggerimenti (F6B.8): **Ambiti** (esclusivi, in linea), **Filtri a scelta multipla**, **Intervalli** (una pillola a piu' valori), **Gettoni** («da: persona», F6B.8b), **Recenti**, **Vicini e popolari**.
  - `fun scopes(options: List<FluidFilterValue>, label: String? = null): FluidFilterSet` — **Ambiti**: scelte esclusive in linea sotto il campo, come gli ambiti di iOS 27 («Tutte le caselle», «Casella attuale», WWDC26 292). Una sola alla volta: l'app parte con `FluidFilterSelection.of(<il primo id>)`.
  - `fun choices(filters: List<FluidFilter>, label: String? = null): FluidFilterSet` — **Filtri a scelta multipla**: interruttori, accesi insieme; raccolti, il menu resta aperto a ogni scelta.
  - `fun range(id: String, label: String, values: List<FluidFilterValue>, icon: ImageVector? = FluidIcons.Schedule, priority: FluidToolbarPriority = FluidToolbarPriority.Automatic): FluidFilter` — **Intervalli**: una pillola a piu' valori, che si apre in menu dal suo vetro.
  - `fun lastDays(id: String = "periodo", label: String = "Periodo", days: List<Int> = listOf(7, 30), dayLabel: (Int) -> String = { "$it giorni" }, other: String? = "Altro"): FluidFilter` — L'intervallo piu' comune, il periodo: «7 giorni», «30 giorni» e «Altro» (che l'app tratta come vuole, per esempio con la scelta delle date). Gli id dei valori sono `"7"`, `"30"` e `"altro"`.
  - `fun tokens(values: List<FluidFilterValue>, label: String? = null, icon: ImageVector? = null, mode: FluidFilterMode = FluidFilterMode.Multiple): FluidFilterSet` — **Gettoni** («da: persona», F6B.8b): i gettoni suggeriti di una ricerca, per `FluidSearchSpec(filters = …)`. Ogni valore e' una pillola che, scelta, diventa gettone nel campo (SwiftUI `suggestedTokens`), con l'id e l'etichetta del valore; [icon] sta in testa alla pillola e al gettone (di serie nessuno: un nome corto e niente simbolo fanno stare tre gettoni e il testo nel campo di un telefono da 360 dp;
  - `fun recent(id: String, title: String, subtitle: String? = null, relevance: Float = 0f): FluidSearchSuggestion` — **Recenti**: una ricerca fatta prima, con la ✕ nella lista.
  - `fun nearby(id: String, title: String, subtitle: String? = null, relevance: Float = 0f): FluidSearchSuggestion` — Un luogo vicino.
  - `fun popular(id: String, title: String, subtitle: String? = null, relevance: Float = 0f): FluidSearchSuggestion` — Una ricerca popolare.
  - `fun nearbyAndPopular(nearby: List<FluidSearchSuggestion>, popular: List<FluidSearchSuggestion>): List<FluidSearchSuggestion>` — **Vicini e popolari**: le due liste in una sola, per pertinenza (quella dell'app); a parita' prima i vicini. E' la lista prima del primo carattere, al posto di uno stato vuoto.

## `engine-ui/ui/fluid/FluidGlassButtonStyle.kt`

- `class FluidGlassButtonStyle private constructor(val glass: FluidGlass, val isProminent: Boolean, val tint: Color, val fill: Color = Color.Unspecified, val fillInk: Color = Color.Unspecified)` — Lo stile di un tasto di vetro ([FluidGlassButton], [FluidGlassIconButton]): `buttonStyle(.glass)`, `.glassProminent` e `.glass(_:)` di SwiftUI.
  - `companion object`
    - `val Glass: FluidGlassButtonStyle` — Il vetro di serie dei tasti (`.glass`).
    - `val Clear: FluidGlassButtonStyle` — Il vetro limpido (`.glass(.clear)`): sopra foto e video, con contenuto chiaro e marcato.
    - `val Prominent: FluidGlassButtonStyle` — L'azione principale (`.glassProminent`), tinta dell'accento del tema.
    - `fun Glass(glass: FluidGlass): FluidGlassButtonStyle` — Un vetro qualunque (`.glass(_:)`): `FluidGlass.Regular.tint(c)` per una tinta di Apple, [FluidGlass.Identity] per un tasto senza vetro. L'interattivita' del vetro non conta: un tasto reagisce sempre al dito.
    - `fun Prominent(tint: Color): FluidGlassButtonStyle` — L'azione principale con una tinta sua (`.glassProminent` con `.tint(_:)`). [Color.Unspecified] vale l'accento. Conta il colore, non la sua alfa: quanto copre lo decide l'etichetta ([fluidProminentCoverage]).
    - `fun Filled(fill: Color = Color.White, ink: Color = Color.Unspecified): FluidGlassButtonStyle` — Il tasto pieno (4.0, F8.7b): una capsula di [fill] pieno, senza vetro, come il «Play» bianco della pagina dell'album di Apple Music (iOS 26.4) fra i due tasti tondi di vetro. L'etichetta e l'icona prendono [ink] se ci fa almeno [FluidProminentLabelContrast] (4,5:1), altrimenti l'inchiostro che sul pieno si legge meglio (il quasi nero o il quasi bianco, come [Prominent]): il colore della pagina va bene come [ink], e s…
- `enum class FluidButtonSizing` — Quanto un tasto di vetro e' largo (`buttonSizing` di SwiftUI).
- `val LocalFluidGlassSize: ProvidableCompositionLocal<FluidGlassSize>` — La taglia dei tasti di vetro di questo punto dell'albero (`controlSize` di SwiftUI): un [FluidGlassIconButton] o un [FluidGlassButton] senza `size` prende questa. Di serie [FluidGlassSize.Regular].
- `val LocalFluidGlassButtonStyle: ProvidableCompositionLocal<FluidGlassButtonStyle>` — Lo stile dei tasti di vetro di questo punto dell'albero: un tasto senza `style` prende questo. Di serie [FluidGlassButtonStyle.Glass]. Fogli e popover dell'host lo riportano al valore di serie (vedi [LocalFluidGlassSize]).
- `val LocalFluidButtonSizing: ProvidableCompositionLocal<FluidButtonSizing>` — La larghezza dei tasti con etichetta di questo punto dell'albero ([FluidGlassButton] senza `sizing`). Di serie [FluidButtonSizing.Fitted]. Fogli e popover dell'host la riportano al valore di serie (vedi [LocalFluidGlassSize]).
- `const val FluidProminentLabelContrast: Float` — Il contrasto che l'etichetta di un tasto [FluidGlassButtonStyle.Prominent] tiene sul fondo peggiore: 4,5:1, la soglia WCAG 1.4.3 per il testo normale. Il tasto tinto della 4.0 scritto a mano stava a 3,90:1 su una foto scura in tema chiaro e a 3,20:1 su una chiara in tema scuro (chiusura di F2, 2026-11-16).

## `engine-ui/ui/fluid/FluidGlassControls.kt`

- `enum class FluidGlassSize(val lens: Dp, val buttonHeight: Dp)` — La taglia di un tasto di vetro ([FluidGlassIconButton], [FluidGlassButton]).
  - `val touchTarget: Dp get()` — Il riquadro che il tasto tondo occupa nel layout e che il dito colpisce: mai meno di 48 dp.
  - `val iconSize: Dp get()` — Quanto sta bene un'icona dentro il vetro: 0,6 del lato (24, 28, 32 dp), la taglia che il tasto fornisce a una [FluidIcon] senza `size` ([LocalFluidIconSize]). Fino alla 3.x era la meta' (20, 22, 27): R6 ha misurato che cosi' l'icona riempiva 0,37 del vetro contro 0,44-0,48 di Apple, e sembrava sottile ([FluidMetrics.IconLensRatio]).
- `@Composable fun Modifier.glassControlSurface(backdrop: GlassBackdropState, shape: Shape = FluidCapsuleShape, tint: GlassTint = GlassDefaults.controlTint(), optics: GlassOptics = GlassDefaults.buttonOptics(), selected: Boolean = false, interactive: Boolean = true, depth: () -> Float = { 1f }, frozen: () -> Boolean = { false }, heldWhileMoving: () -> Boolean = { false }, pressState: FluidPressState? = null): Modifier` — A pane of glass that behaves like a control.
- `@Composable fun Modifier.fluidStaticGlassSurface(shape: Shape = FluidCapsuleShape, selected: Boolean = false, pressure: () -> Float = { 0f }): Modifier` — Glass without a backdrop to refract.
- `@Composable fun FluidGlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentGlassBackdrop(), enabled: Boolean = true, selected: Boolean = false, shape: Shape = FluidCapsuleShape, onLongClick: (() -> Unit)? = null, size: FluidGlassSize = LocalFluidGlassSize.current, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current, content: @Composable BoxScope.() -> Unit)` — A glass lens for icon actions that live in chrome or another overlay.
- `@Composable fun FluidGlassIconButton(onClick: () -> Unit, size: Dp, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentGlassBackdrop(), enabled: Boolean = true, selected: Boolean = false, shape: Shape = FluidCapsuleShape, onLongClick: (() -> Unit)? = null, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current, content: @Composable BoxScope.() -> Unit)` — Un tasto tondo di vetro di una misura qualunque (3.0): i 62 e i 76 dp dei dischi del trasporto di un player, i 52 di un hero, i 54 di un pulsante di menu, che le tre taglie di [FluidGlassSize] non dicono.
- `@Composable fun FluidGlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…, enabled: Boolean = true, selected: Boolean = false, shape: Shape = FluidCapsuleShape, size: FluidGlassSize = LocalFluidGlassSize.current, haptic: FluidHapticEvent? = FluidHapticEvent.Tap, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, leading: (@Composable () -> Unit)? = null, maxLines: Int = Int.MAX_VALUE, style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current, sizing: FluidButtonSizing = LocalFluidButtonSizing.current)` — A glass action button.

## `engine-ui/ui/fluid/FluidPasswordField.kt`

- `object FluidPasswordFieldDefaults` — Le misure del campo password. A occhio, 23 novembre 2026, nella famiglia del campo di testo.
  - `val StrengthHeight: Dp` — I segmenti della forza: quattro capsule alte 4 dp, a 4 dp l'una dall'altra.
  - `val StrengthGap: Dp`
  - `val RuleMark: Dp` — Il segno di una regola: 16 dp, il cerchio vuoto o la spunta.
- `@Composable fun FluidPasswordField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, rules: FluidPasswordRules? = FluidPasswordRules.Default, newPassword: Boolean = rules != null, showStrength: Boolean = newPassword, suggestStrong: Boolean = newPassword && rules != null, label: String? = null, placeholder: String? = null, enabled: Boolean = true, isError: Boolean = false, supportingText: String? = null, imeAction: ImeAction = if (newPassword) ImeAction.Next else ImeAction.…, keyboardActions: KeyboardActions = KeyboardActions.Default, labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current)` — Il campo password guidato (F8.3, idea `campo-password`): [FluidTextField] segreto, con l'occhio che mostra e nasconde, e sotto **le regole che si spuntano mentre si scrive**, **la forza stimata** e, a campo vuoto, **la password forte proposta**, come le schermate di registrazione di iOS con le Password Rules di iCloud Keychain.

## `engine-ui/ui/fluid/FluidPrivateValue.kt`

- `val LocalFluidDiscreetMode: ProvidableCompositionLocal<Boolean>` — La **modalità discreta** (F8.3, `valore-riservato`): vera, vela insieme tutti i valori riservati segnati `discreetOnly` (un voto in classe, un saldo sui mezzi). L'interruttore è dell'app; l'engine la legge da qui.
- `fun interface FluidPrivateGate` — Chi decide se un valore si può scoprire: di serie nessuno (basta il tocco). L'app, o il blocco dell'engine quando ci sarà (`blocco-biometrico`), ne dà uno che chiede l'impronta o il PIN e risponde vero o falso.
  - `suspend fun authorize(): Boolean` — Vero se il valore si può mostrare. Si chiama dopo il tocco, sul thread dell'interfaccia.
- `val LocalFluidPrivateGate: ProvidableCompositionLocal<FluidPrivateGate?>` — Il cancello in uso per i valori riservati di questo albero; null: il tocco basta.
- `class FluidPrivateState` — Lo stato di un valore riservato: coperto o scoperto. Non si salva: dopo una rotazione o il ritorno nell'app è coperto.
  - `var isRevealed: Boolean by mutableStateOf(false)` — Vero se il valore si vede.
  - `fun reveal()` — Mostra il valore, senza passare dal cancello (per un tasto «Mostra» dell'app che ha già chiesto l'impronta).
  - `fun cover()` — Copre il valore.
- `@Composable fun rememberFluidPrivateState(): FluidPrivateState` — Uno stato coperto, ricordato nella composizione.
- `class FluidPrivateVeil private constructor(internal val blur: Boolean)` — Come si vela un valore riservato: un segnaposto al posto del testo, o la sfocatura per un'immagine.
  - `companion object`
    - `val Placeholder: FluidPrivateVeil` — Il segnaposto di `redacted(reason: .privacy)` di SwiftUI: barre arrotondate al posto delle righe, col segno dell'occhio barrato. Il valore **non si disegna** (scheda dell'idea: un testo corto sfocato si indovina).
    - `val Blur: FluidPrivateVeil` — Il velo dei contenuti sensibili di Messaggi: l'immagine molto sfocata con l'occhio e «Tocca per vedere». Per le immagini: una foto sfocata non si legge, e la sagoma aiuta a riconoscerla. Sotto Android 12 (niente sfocatura) diventa il segnaposto.
- `object FluidPrivateDefaults` — I numeri del valore riservato. **A occhio**, 23 novembre 2026, salvo dove detto.
  - `val RecoverAfter: Duration` — Dopo quanto un valore scoperto si ricopre da solo: 30 s. Apple non ricopre a tempo un `privacySensitive` (lo fa il sistema col blocco dello schermo); 30 s è il tempo dei gestori di password per un campo mostrato. A occhio.
- `@Composable fun Modifier.fluidPrivate(state: FluidPrivateState, discreetOnly: Boolean = false, veil: FluidPrivateVeil = FluidPrivateVeil.Placeholder, recoverAfter: Duration? = FluidPrivateDefaults.RecoverAfter, protectWindow: Boolean = true, blockScreenshots: Boolean = false, gate: FluidPrivateGate? = LocalFluidPrivateGate.current, labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current): Modifier` — Un valore riservato (F8.3, idea `valore-riservato`): il dato — una chiave, un voto, un saldo, un'anteprima — coperto da un velo finché la persona non lo tocca, come `redacted(reason: .privacy)` e `privacySensitive()` di SwiftUI e il velo dei contenuti sensibili di Messaggi.
- `@Composable fun FluidSecureWindowHold(active: Boolean, blockScreenshots: Boolean)` — Tiene la finestra fuori dalle recenti finché [active]: l'anteprima nelle app recenti è vuota (`setRecentsScreenshotEnabled(false)` da Android 13, `FLAG_SECURE` prima, che toglie anche gli screenshot), e con [blockScreenshots] anche gli screenshot sono neri. Le richieste si contano: la finestra torna com'era quando l'ultima se ne va, e un'app che aveva già `FLAG_SECURE` lo tiene.

## `engine-ui/ui/fluid/FluidRangeSlider.kt`

- `@Composable fun FluidRangeSlider(value: ClosedFloatingPointRange<Float>, onValueChange: (ClosedFloatingPointRange<Float>) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null, minDistance: Float = 0f, snapValues: List<Float> = emptyList(), snapThreshold: Float = 0.03f, startContentDescription: String? = null, endContentDescription: String? = null, startStateDescription: String? = null, endStateDescription: String? = null, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — Un cursore con due maniglie: il minimo e il massimo di un intervallo (la durata dei brani, il peso delle foto, la dimensione di un allegato). E' [FluidSlider] con una maniglia in piu': la pista, le maniglie di vetro che salgono sotto il dito, la presa, le tacche e l'aptica sono le sue.
- `object FluidRangeSliderDefaults` — I nomi di serie delle due maniglie per TalkBack. Un'app in un'altra lingua li passa sempre.
  - `const val StartDescription: String`
  - `const val EndDescription: String`

## `engine-ui/ui/fluid/FluidRecurrenceSheet.kt`

- `data class FluidRecurrenceLabels(val title: String = "Ripeti", val never: String = "Mai", val daily: String = "Ogni giorno", val weekly: String = "Ogni settimana", val biweekly: String = "Ogni 2 settimane", val monthly: String = "Ogni mese", val yearly: String = "Ogni anno", val custom: String = "Personalizza", val every: String = "Ogni", val unitsOne: List<String> = listOf("giorno", "settimana", "mese", "anno"), val unitsMany: List<String> = listOf("giorni", "settimane", "mesi", "anni"), val unitName: String = "Frequenza", val days: String = "Giorni", val repeatOn: String = "Il giorno", val end: String = "Fine ripetizione", val endNever: String = "Mai", val endOnDate: String = "In una data", val endAfter: String = "Dopo", val oneTime: String = "1 volta", val manyTimes: String = "%d volte", val next: String = "Prossime date", val noNext: String = "Nessuna data in arrivo", val confirm: String = "Fine", val cancel: String = "Annulla")` — Le parole del pannello «Ripeti», di serie in italiano; [english] per l'inglese. Le frasi della regola non stanno qui: le scrive [FluidRecurrenceText] nella lingua del pannello.
  - `companion object`
    - `fun english(): FluidRecurrenceLabels` — Le stesse in inglese, come la schermata Repeat di Calendario.
- `@Composable fun FluidRecurrenceSheet(visible: Boolean, onDismissRequest: () -> Unit, onConfirm: (FluidRecurrenceRule?) -> Unit, start: LocalDate, initialRule: FluidRecurrenceRule? = null, locale: Locale = LocalConfiguration.current.locales[0], labels: FluidRecurrenceLabels = FluidRecurrenceLabels(), source: FluidTransitionSource? = null, previewCount: Int = 5)` — Il pannello «Ripeti», come la schermata Ripetizione di Calendario e Promemoria: in un foglio di vetro dell'engine ([FluidGlassSheet], a meta' e in cima, che nasce dalla [source] se c'e' e ci torna).

## `engine-ui/ui/fluid/FluidRulerDial.kt`

- `@Composable fun FluidRulerDial(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = -45f..45f, step: Float = 1f, majorEvery: Int = 10, neutralValue: Float? = 0f, enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null, valueText: ((Float) -> String)? = { fluidRulerDefaultText(it) }, contentDescription: String? = null)` — La ghiera a righello: un righello di tacche fitte che scorre di lato sotto un indice fermo, per regolare un valore fine (i gradi per raddrizzare una foto, lo zoom, la velocita', una calibrazione). A differenza del cursore non ha un inizio e una fine in vista, e il dito non copre il valore: il valore e' scritto sopra l'indice.
- `@Composable fun FluidRulerDial(value: () -> Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = -45f..45f, step: Float = 1f, majorEvery: Int = 10, neutralValue: Float? = 0f, enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null, valueText: ((Float) -> String)? = { fluidRulerDefaultText(it) }, contentDescription: String? = null)` — La ghiera col valore letto da [value] solo dove serve (F8.15c): chi la usa non legge il valore in composizione, e mentre il dito la muove **non si ricompone niente**, ne' il chiamante ne' la ghiera. Cambiano il disegno delle tacche, la scritta sopra l'indice (che si ricompone da sola, quando cambia la scritta) e la semantica (quando cambia la tacca).
- `object FluidRulerDialDiagnostics` — I contatori delle prove della ghiera (F8.15c): **non sono API per le app**, come `FluidChartDiagnostics`. Contano sempre (un intero in piu' per composizione o disegno); le prove li azzerano con [resetForTesting] e guardano quanto crescono mentre il dito muove il righello.
  - `var compositions: Int` — Le composizioni del corpo della ghiera: non devono crescere a ogni valore.
  - `var labelCompositions: Int` — Le composizioni della scritta sopra l'indice: una per scritta nuova, non per fotogramma.
  - `var tickDraws: Int` — I disegni delle tacche: uno per fotogramma in cui il righello si muove.
  - `fun resetForTesting()`

## `engine-ui/ui/fluid/FluidScrubber.kt`

- `typealias FluidScrubberFormat` — Come un [FluidScrubber] racconta una posizione a parole: la [position] e l'[range] in cui sta, nella stessa unita' del valore. Quella dell'app: millisecondi, secondi, l'indice di un fotogramma.
- `@Composable fun FluidScrubber(position: () -> Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, buffered: (() -> Float)? = null, step: Float = 0f, marks: List<Float> = emptyList(), onScrubStart: (() -> Unit)? = null, onScrub: ((Float) -> Unit)? = null, onScrubEnd: (() -> Unit)? = null, startLabel: FluidScrubberFormat? = null, endLabel: FluidScrubberFormat? = null, contentDescription: String = FluidScrubberDefaults.ContentDescription, valueDescription: FluidScrubberFormat = FluidScrubbing.Spoken, keyStep: Float = 0f, stepBackLabel: String = FluidScrubberDefaults.StepBackLabel, stepForwardLabel: String = FluidScrubberDefaults.StepForwardLabel, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…, onStartLabelClick: (() -> Unit)? = null, onEndLabelClick: (() -> Unit)? = null)` — Lo **scrubber**: la barra con cui si sceglie dove andare in qualcosa che dura — un brano, una registrazione, una cronologia di fotogrammi.
- `@Composable fun FluidScrubber(position: Float, onSeek: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, buffered: (() -> Float)? = null, step: Float = 0f, marks: List<Float> = emptyList(), onScrubStart: (() -> Unit)? = null, onScrub: ((Float) -> Unit)? = null, onScrubEnd: (() -> Unit)? = null, startLabel: FluidScrubberFormat? = null, endLabel: FluidScrubberFormat? = null, contentDescription: String = FluidScrubberDefaults.ContentDescription, valueDescription: FluidScrubberFormat = FluidScrubbing.Spoken, keyStep: Float = 0f, stepBackLabel: String = FluidScrubberDefaults.StepBackLabel, stepForwardLabel: String = FluidScrubberDefaults.StepForwardLabel, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…, onStartLabelClick: (() -> Unit)? = null, onEndLabelClick: (() -> Unit)? = null)` — [FluidScrubber] con la posizione data come valore: per chi la posizione la cambia di rado (una cronologia a passi).
- `object FluidScrubberDefaults` — Le misure, i testi e le soglie dello scrubber che un'app puo' voler conoscere.
  - `const val ContentDescription: String` — Il nome per TalkBack, quando l'app non ne da' uno piu' preciso.
  - `const val StepBackLabel: String` — Le due azioni di TalkBack per muoversi del `keyStep` di [FluidScrubber].
  - `const val StepForwardLabel: String`
  - `const val MaxStepDots: Int` — Oltre questo numero di passi i puntini non si disegnano: sarebbero un tratteggio, e sotto un polpastrello non si contano. Il passo c'e' lo stesso, e la maniglia ci si posa.
- `object FluidScrubbing` — I conti e i testi di [FluidScrubber]. Pubblico per i formati: un'app che mostra il tempo da se' accanto alla barra usa gli stessi.
  - `fun clock(millis: Long): String` — Un tempo come lo scrive un orologio: «3:20», «59:59», «1:02:03». Da [millis] in millisecondi, per difetto al secondo (un lettore a 3:19,9 e' ancora a 3:19) e mai sotto zero.
  - `val Elapsed: FluidScrubberFormat` — Il tempo trascorso, «1:05». Per i millisecondi: per un'altra unita' si passa un formato proprio.
  - `val Remaining: FluidScrubberFormat` — Quanto manca, «−2:15», con il meno tipografico.
  - `val Total: FluidScrubberFormat` — La durata intera, «3:20».
  - `val Spoken: FluidScrubberFormat` — Il valore a voce, «1:05 di 3:20»: il default di [FluidScrubber].

## `engine-ui/ui/fluid/FluidSlider.kt`

- `@Composable fun FluidSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null, snapValues: List<Float> = emptyList(), snapThreshold: Float = 0.03f, contentDescription: String? = null, stateDescription: String? = null, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — Uno slider di vetro: una pista sottile riempita con l'accento, e una pillola che a riposo e' bianca e opaca e si fa lente mentre il dito la tiene.

## `engine-ui/ui/fluid/FluidSpecialFieldLabels.kt`

- `data class FluidSpecialFieldLabels(val unitPicker: String = "Cambia unità", val converted: (String) -> String = { "≈ $it" }, val code: String = "Codice", val codePosition: (position: Int, total: Int) -> String = { position, total -> "cifra $position di $total…, val codeComplete: String = "codice completo", val codeVerifying: String = "verifica in corso", val codeWrong: String = "Codice non corretto", val strengthLevels: List<String> = listOf("Debole", "Discreta", "Buona", "Forte"), val strengthSpoken: (String) -> String = { "Forza stimata: ${it.lowercase()}" }, val strengthNote: String = "Stima indicativa", val ruleMinLength: (Int) -> String = { "Almeno $it caratteri" }, val ruleMaxLength: (Int) -> String = { "Al massimo $it caratteri" }, val ruleLower: String = "Una lettera minuscola", val ruleUpper: String = "Una lettera maiuscola", val ruleDigit: String = "Un numero", val ruleSpecial: String = "Un simbolo", val ruleLetter: String = "Una lettera", val ruleCustom: (String) -> String = { "Uno di questi: $it" }, val ruleOr: String = " o ", val ruleMaxConsecutive: (Int) -> String = { "Non più di $it caratteri uguali di fila" }, val ruleOnlyAllowed: String = "Solo caratteri ammessi", val ruleMet: String = "fatto", val ruleMissing: String = "da fare", val suggestedTitle: String = "Password forte", val useSuggested: String = "Usa password forte", val suggestedNote: String = "Generata su questo dispositivo. Salvala nel tu…, val ownPassword: String = "Scegli la mia password", val tapToReveal: String = "Tocca per vedere", val hidden: String = "Valore nascosto", val reveal: String = "Mostra", val hide: String = "Nascondi", val copy: String = "Copia", val copied: String = "Copiato", val clearsIn: (seconds: Int) -> String = { "Si cancella fra $it s" }, val cleared: String = "Appunti cancellati", val pasteCode: String = "Incolla il codice", val pasteLink: String = "Incolla il link", val pasteImage: String = "Incolla l'immagine")` — Le parole dei campi speciali (F8.3): il campo con unità, il campo a codice, il campo password, il valore riservato, la copia delicata e il tasto incolla. Di serie in italiano; [English] è la versione inglese completa. Un'app in un'altra lingua ne dà una sua con [LocalFluidSpecialFieldLabels].
  - `companion object`
    - `val English: FluidSpecialFieldLabels` — La versione inglese completa.
- `val LocalFluidSpecialFieldLabels: ProvidableCompositionLocal<FluidSpecialFieldLabels>` — Le parole dei campi speciali in uso.

## `engine-ui/ui/fluid/FluidStepper.kt`

- `@Composable fun FluidStepper(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE, step: Int = 1, enabled: Boolean = true, label: String? = null, valueText: (Int) -> String = { it.toString() }, repeatOnHold: Boolean = true)` — Uno stepper: meno e piu' in una capsula, per un numero piccolo che si aggiusta a passi (copie, persone, minuti). Dalla 4.0 (piano «iOS in Android», F6.6): prima l'engine non l'aveva, e le app ne scrivevano uno con due `FluidButton`.

## `engine-ui/ui/fluid/FluidSwitch.kt`

- `@Composable fun FluidSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — The switch.
- `@Composable fun FluidGlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — **DEPRECATO: FluidSwitch is this control now.** — The name this control had for one release, while it was a second control beside the old switch.
- `val FluidSwitchWidth: Dp` — Width helper so a row of controls can reserve the switch's footprint without instantiating one.

## `engine-ui/ui/fluid/FluidTextEdit.kt`

- `object FluidTextEdit` — Le operazioni di un editor di testo semplice, sul valore e non sul componente.
  - `fun insert(value: TextFieldValue, snippet: String): TextFieldValue` — Mette [snippet] al posto della selezione, o al cursore, e lascia il cursore dopo di esso.
  - `fun wrap(value: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue` — Avvolge la selezione fra [prefix] e [suffix] e la tiene selezionata, cosi' un secondo tocco puo' avvolgerla ancora. Senza selezione mette la coppia e lascia il cursore in mezzo, pronto a scrivere dentro.
  - `fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue` — Mette [prefix] all'inizio della riga del cursore (`# `, `- `, `> `), o lo toglie se c'e' gia'.

## `engine-ui/ui/fluid/FluidTextField.kt`

- `@Composable fun FluidTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null, label: String? = null, enabled: Boolean = true, readOnly: Boolean = false, singleLine: Boolean = true, minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, isError: Boolean = false, supportingText: String? = null, minHeight: androidx.compose.ui.unit.Dp = FluidTextFieldDefaults.MinHeight, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default, visualTransformation: VisualTransformation = VisualTransformation.None, showClearButton: Boolean = singleLine, secret: Boolean = false, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)` — A text field.
- `@Composable fun FluidTextField(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null, label: String? = null, enabled: Boolean = true, readOnly: Boolean = false, singleLine: Boolean = true, minLines: Int = 1, maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE, isError: Boolean = false, supportingText: String? = null, minHeight: androidx.compose.ui.unit.Dp = FluidTextFieldDefaults.MinHeight, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default, visualTransformation: VisualTransformation = VisualTransformation.None, showClearButton: Boolean = singleLine, secret: Boolean = false, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)` — The text field, with the caret.
- `object FluidTextFieldDefaults` — I valori di serie di [FluidTextField].
  - `val MinHeight: Dp` — L'altezza minima del campo: 40 dp, i 40,0 pt del campo pieno di iOS 27 (147 px a 3,67 px/pt, sessione 278 a 11:15; fiducia alta). Era 44. Col contenuto di 24 dp e 8 dp sopra e sotto il campo non cresce per l'icona a destra.
  - `val MultilineRadius: Dp` — Il raggio di un campo su piu' righe: 20 dp, quello della capsula a una riga.

## `engine-ui/ui/fluid/FluidTokenField.kt`

- `data class FluidToken(val id: String, val label: String, val isError: Boolean = false)` — Gettone di dominio neutro. L'app assegna ID univoci e decide validità e normalizzazione.
- `data class FluidTokenFieldValue(val tokens: List<FluidToken> = emptyList(), val draft: TextFieldValue = TextFieldValue(), val selectedTokenId: String? = null)` — Bozza completa e selezione a due tempi; composizione IME transiente, mai ripristinata dopo morte processo.
  - `companion object`
    - `val Saver` — Per rememberSaveable(stateSaver = Saver): salva anche cursore, errori e selezione del gettone.
- `object FluidTokenFieldDefaults` — Operazioni pure: nessuna regex di dominio. Un risultato null del creatore conserva il testo rifiutato.
  - `val Separators: Set<Char>` — Separazione predefinita; aggiungere lo spazio soltanto per domini che non hanno nomi composti.
  - `fun update(value: FluidTokenFieldValue, draft: TextFieldValue, separators: Set<Char> = Separators, commitRemainder: Boolean = false, createToken: (String, List<FluidToken>) -> FluidToken?): FluidTokenFieldValue` — Conferma soltanto porzioni terminate da separatore, mai durante composizione. commitRemainder serve per Invio, perdita focus o incolla esplicito di una lista completa. createToken vede anche i gettoni appena aggiunti: la deduplica è interamente dell'app. null conserva la porzione originale (separatore incluso), senza perdere dati non riconosciuti.
  - `fun backspace(value: FluidTokenFieldValue): FluidTokenFieldValue` — Prima pressione seleziona, seconda rimuove. Testo o composizione presenti lasciano agire l'editor.
- `data class FluidTokenSuggestion(val id: String, val title: String, val subtitle: String? = null, val leading: (@Composable () -> Unit)? = null)` — Suggerimento controllato dall'app: ID stabile per evitare che un aggiornamento scelga un'altra riga.
- `@Composable fun FluidTokenField(value: FluidTokenFieldValue, onValueChange: (FluidTokenFieldValue) -> Unit, createToken: (String, List<FluidToken>) -> FluidToken?, modifier: Modifier = Modifier, label: String? = null, placeholder: String? = null, separators: Set<Char> = FluidTokenFieldDefaults.Separators, enabled: Boolean = true, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, onEditToken: ((FluidToken) -> Unit)? = null, suggestions: List<FluidTokenSuggestion> = emptyList(), onSuggestionClick: (FluidTokenSuggestion) -> Unit = {})` — Campo a gettoni che cresce nella pagina: inserirlo nello scroll del chiamante, senza limite verticale interno. createToken valida, normalizza e deduplica (null conserva il testo); un gettone non valido usa isError. onEditToken è opzionale: l'app decide se rimuovere il gettone e mettere la sua etichetta nella bozza.
- `@Composable fun FluidSuggestionRow(title: String, subtitle: String? = null, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false)` — Riga in pagina, un nodo accessibile; non prende focus alla tastiera mentre si sceglie un suggerimento.

## `engine-ui/ui/fluid/FluidUnitField.kt`

- `object FluidUnitFieldDefaults` — Le misure della capsula dell'unità. A occhio, 23 novembre 2026: alta quanto il contenuto del campo da 40 dp.
  - `val CapsuleHeight: Dp` — L'altezza della capsula dell'unità: 28 dp, nel campo da 40 con 8 dp sopra e sotto meno 2.
  - `val CapsulePadding: Dp` — Il margine ai lati del simbolo nella capsula.
- `@Composable fun FluidUnitField(value: BigDecimal?, onValueChange: (BigDecimal?) -> Unit, unit: FluidUnit, modifier: Modifier = Modifier, units: List<FluidUnit> = emptyList(), onUnitChange: ((FluidUnit) -> Unit)? = null, convertOnUnitChange: Boolean = true, convertTo: FluidUnit? = null, label: String? = null, placeholder: String? = null, supportingText: String? = null, isError: Boolean = false, enabled: Boolean = true, allowNegative: Boolean = unit.dimension == FluidUnitDimension.Temperature, locale: Locale = fluidCurrentLocale(), imeAction: ImeAction = ImeAction.Done, keyboardActions: KeyboardActions = KeyboardActions.Default, labels: FluidSpecialFieldLabels = LocalFluidSpecialFieldLabels.current)` — Il campo con unità (F8.3, idea `campo-con-unita`): un numero con significato — un importo, una misura, una percentuale — con la sua unità accanto, che si legge, si scrive e si converte. **Restituisce sempre il numero** ([onValueChange], un [BigDecimal] nell'unità [unit], o null a campo vuoto), mai la stringa; il valore nell'unità base è `FluidQuantity(valore, unit).inBase()`.

## `engine-ui/ui/fluid/FluidVoiceRecorder.kt`

- `enum class FluidRecorderPhase` — In che punto e' un registratore, per l'interfaccia. Nuovo nella 4.0 (F8.13).
- `data class FluidRecorderStrings(val record: String = "Registra", val pause: String = "Pausa", val resume: String = "Riprendi", val done: String = "Fine", val newRecording: String = "Nuova registrazione", val duration: String = "Durata", val play: String = "Riproduci", val pausePlayback: String = "Metti in pausa l'ascolto", val skipBack: String = "Indietro di 15 secondi", val skipForward: String = "Avanti di 15 secondi", val clipping: String = "Il suono è troppo forte", val interrupted: String = "In pausa: il microfono serve a un'altra app")` — I testi del registratore, in italiano di serie. Un'app tradotta li passa tutti.
- `object FluidRecorderDefaults` — Le misure del registratore, da Memo Vocali di iOS 26 (guida dell'iPhone, «Make a recording», 1,13 px per punto).
  - `val RecordButtonSize: Dp` — Il tasto rosso dell'elenco: 52 dp (58 px nella guida). Fiducia alta.
  - `val PauseCapsuleWidth: Dp` — La capsula della pausa: 147 × 58 dp (166 × 66 px nella guida). Fiducia alta.
  - `val PauseCapsuleHeight: Dp`
  - `val PauseBarWidth: Dp` — Le due barre rosse della pausa: 9 × 24 dp con 5 dp fra loro (10 × 27 px e 6 px nella guida).
  - `val PauseBarHeight: Dp`
  - `val PauseBarGap: Dp`
  - `val TimelineHeight: Dp` — L'onda del pannello: la fascia della registrazione, piu' bassa che a tutto schermo.
  - `const val SkipMillis: Long` — Il salto dei tasti di ascolto: 15 s, come Memo Vocali.
  - `val PanelRadius: Dp` — Il raggio del pannello: quello dei fogli.
- `@Composable fun FluidRecorderClock(elapsedMillis: () -> Long, advancing: Boolean, modifier: Modifier = Modifier, style: TextStyle = FluidTextStyles.largeNumeric, color: Color = MaterialTheme.colorScheme.onSurface, label: String = FluidRecorderStrings().duration)` — Il **tempo della registrazione**, «00:07.00», come Memo Vocali: minuti, secondi e centesimi, 34 sp in grassetto a cifre tabulari ([FluidTextStyles.largeNumeric]); oltre l'ora «1:02:03.45».
- `@Composable fun FluidRecordButton(onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentGlassBackdrop(), enabled: Boolean = true, label: String = FluidRecorderStrings().record)` — Il **tasto rosso** di Memo Vocali: un disco di vetro tinto di rosso (`systemRed`) da 52 dp, che galleggia sopra l'elenco. Il vetro tinto e' quello di serie dell'engine (la dose C di F6B.16). Il tocco suona `Tap`; TalkBack dice «Registra».
- `@Composable fun FluidRecorderPanel(phase: FluidRecorderPhase, envelope: FluidAudioEnvelope, elapsedMillis: () -> Long, onPause: () -> Unit, onResume: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier, title: String = FluidRecorderStrings().newRecording, subtitle: String? = null, playback: FluidMediaSession? = null, backdrop: GlassBackdropState = currentGlassBackdrop(), clipping: Boolean = false, interrupted: Boolean = false, leading: (@Composable () -> Unit)? = null, bottomStart: (@Composable () -> Unit)? = null, bottomEnd: (@Composable () -> Unit)? = null, strings: FluidRecorderStrings = FluidRecorderStrings())` — Il **pannello della registrazione**, il contenuto del foglio di Memo Vocali: il titolo con l'ora e la durata, l'onda che scorre sotto la testina ([FluidAudioTimeline]: rossa mentre registra, dell'accento in pausa), il tempo grande ([FluidRecorderClock]), i tasti dell'ascolto (meno e piu' 15 secondi e Play, spenti mentre registra, come in Memo Vocali) e in fondo la **capsula della pausa**: due barre rosse mentre regis…
- `@Composable fun FluidVoiceRecorder(phase: FluidRecorderPhase, onRecord: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onDone: () -> Unit, envelope: FluidAudioEnvelope, elapsedMillis: () -> Long, modifier: Modifier = Modifier, title: String = FluidRecorderStrings().newRecording, subtitle: String? = null, playback: FluidMediaSession? = null, backdrop: GlassBackdropState = currentGlassBackdrop(), clipping: Boolean = false, interrupted: Boolean = false, leading: (@Composable () -> Unit)? = null, bottomStart: (@Composable () -> Unit)? = null, bottomEnd: (@Composable () -> Unit)? = null, strings: FluidRecorderStrings = FluidRecorderStrings())` — Il **registratore** di Memo Vocali in un pezzo solo: a riposo il tasto rosso ([FluidRecordButton]); registrando, il suo vetro diventa il pannello ([FluidRecorderPanel]) e a registrazione finita ci torna. Il cambio lo anima il piano flottante (`FluidGlassContainer`, Fluid-physics): un vetro solo con la stessa identita', che cambia forma;
- `data class FluidRecordingQualityStrings(val title: String = "Qualità di registrazione", val voice: String = "Voce per l'IA", val standard: String = "Standard", val high: String = "Alta", val lossless: String = "Senza perdita", val detail: (quality: FluidRecordingQuality, perHour: String) -> String = { quality, perHour -> val format = if (quality.…)` — I testi della riga della qualità, in italiano di serie: il titolo, il nome di ogni preimpostazione e la riga sotto («AAC 96 kbit/s · circa 44 MB all'ora»). Un'app tradotta li passa tutti.
  - `fun label(quality: FluidRecordingQuality): String` — Il nome di [quality]; una preimpostazione che questi testi non conoscono si chiama col suo id.
- `@Composable fun FluidRecordingQualityRow(selected: FluidRecordingQuality, onSelected: (FluidRecordingQuality) -> Unit, modifier: Modifier = Modifier, qualities: List<FluidRecordingQuality> = FluidRecordingQuality.All, enabled: Boolean = true, strings: FluidRecordingQualityStrings = FluidRecordingQualityStrings())` — La **riga della qualità di registrazione** (F8.13b), pronta per la pagina delle impostazioni di un'app: il titolo, la qualità di adesso a destra, e al tocco la riga diventa il pop-up di vetro con le preimpostazioni ([FluidPickerRow]), ognuna col suo formato e con **lo spazio che occupa un'ora** («AAC 96 kbit/s · circa 43,6 MB all'ora»). Sotto il titolo, la stessa riga per la qualità scelta.

## `engine-ui/ui/fluid/FluidWheelPicker.kt`

- `class FluidWheelColumn(val count: Int, val selected: Int, val onSelectedChange: (Int) -> Unit, val label: (Int) -> String, val cyclic: Boolean = false, val unit: String? = null, val contentDescription: String? = null, val stateDescription: ((Int) -> String)? = null, val weight: Float = 1f, val textAlign: TextAlign = if (unit != null) TextAlign.End else TextAlign.…, val isEnabled: ((Int) -> Boolean)? = null)` — Una colonna di un [FluidWheelPicker]: [count] voci, quella scelta, e come si chiama ognuna.
- `object FluidWheelDefaults` — Le misure di serie della ruota, per chi le deve riservare.
  - `val Height: Dp` — 216 dp: l'altezza di `UIPickerView` e di `UIDatePicker` a ruota (Apple, fiducia alta).
  - `val RowHeight: Dp` — L'altezza di una voce: 32 dp, quella di `UIPickerView` (fiducia media: non documentata da Apple, misurata dalle schermate; con il testo grande cresce con la scala del carattere, fino a 1,6).
- `@Composable fun FluidWheelPicker(count: Int, selected: Int, onSelectedChange: (Int) -> Unit, label: (Int) -> String, modifier: Modifier = Modifier, cyclic: Boolean = false, unit: String? = null, contentDescription: String? = null, stateDescription: ((Int) -> String)? = null, enabled: Boolean = true)` — Un selettore a ruota di una sola colonna. Vedi l'altra forma di [FluidWheelPicker] per il comportamento.
- `@Composable fun FluidWheelPicker(columns: List<FluidWheelColumn>, modifier: Modifier = Modifier, enabled: Boolean = true)` — Il selettore a ruota di iOS: una o piu' colonne di voci che girano col dito su un cilindro e si fermano a scatti sotto una fascia grigia, che ingrandisce appena il valore scelto. Piu' colonne affiancate fanno una durata, un'ora, un valore con la sua unita' (le forme pronte: [FluidDurationWheel], [FluidTimeWheel]).

## `engine-ui/ui/fluid/FluidWheelPresets.kt`

- `data class FluidDurationUnits(val hours: String = "ore", val minutes: String = "min", val seconds: String = "s", val hoursName: String = "Ore", val minutesName: String = "Minuti", val secondsName: String = "Secondi")` — Le parole di una durata a ruota: le unita' ferme nella fascia (il timer di Orologio scrive «ore», «min», «s») e i nomi delle colonne per TalkBack.
  - `companion object`
    - `fun english(): FluidDurationUnits` — Le stesse in inglese, come il timer di Orologio in inglese: «hours», «min», «sec».
- `@Composable fun FluidDurationWheel(seconds: Long, onSecondsChange: (Long) -> Unit, modifier: Modifier = Modifier, showSeconds: Boolean = true, maxHours: Int = 23, minuteInterval: Int = 1, minimumSeconds: Long = if (showSeconds) 1L else 60L, units: FluidDurationUnits = FluidDurationUnits(), enabled: Boolean = true, cyclic: Boolean = false)` — La durata a ruota del timer di Orologio: ore, minuti e (di serie) secondi, ognuno con la sua unita' ferma nella fascia. Il valore e' uno, in secondi ([seconds]); le colonne lo scompongono.
- `@Composable fun FluidTimeWheel(time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier = Modifier, use24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current), locale: Locale = LocalConfiguration.current.locales[0], minuteInterval: Int = 1, hourDescription: String = LocalFluidLabels.current.dateTime.hour, minuteDescription: String = LocalFluidLabels.current.dateTime.minute, enabled: Boolean = true)` — L'ora a ruota di `UIDatePicker` (`.time`, stile `.wheels`): ore e minuti, e in 12 ore anche AM e PM, nell'ordine della lingua (in coreano e in giapponese AM e PM vengono prima). Fatta per stare in un foglio o in una riga che si apre, e per prendere il posto dei tasti «−» e «+» di `FluidDateTimeSheet` (F8.8).
