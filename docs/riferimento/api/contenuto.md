<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/contenuto.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Contenuto: righe, card, liste, identità, testo, file

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidAsyncImage.kt`

- `sealed interface FluidImagePhase` — La fase di un'immagine che si carica, come `AsyncImagePhase` di SwiftUI: [Loading] (`.empty`), [Success], [Failure]. La dice l'app, che fa il caricamento vero (Coil, il suo client): l'engine disegna le fasi.
  - `data object Loading : FluidImagePhase` — Non c'è ancora niente: si vede l'impronta ([FluidAsyncImage] `thumbHash`) o un fondo quieto.
  - `data class Success(val painter: Painter) : FluidImagePhase` — L'immagine è arrivata.
  - `data class Failure(val error: Throwable? = null) : FluidImagePhase` — Non è arrivata: la tessera d'errore, con «Riprova» se l'app lo permette.
- `@Composable fun FluidAsyncImage(phase: FluidImagePhase, contentDescription: String?, modifier: Modifier = Modifier, thumbHash: ByteArray? = null, contentScale: ContentScale = ContentScale.Crop, shape: Shape = RectangleShape, onRetry: (() -> Unit)? = null)` — Il posto di un'immagine che arriva dalla rete o dal NAS (F8.1, `immagine-che-arriva`): **segnaposto, poi sfocata, poi nitida**, come le fasi di `AsyncImage` di SwiftUI.

## `engine-ui/ui/fluid/FluidAvatar.kt`

- `object FluidAvatarDefaults` — Le misure dell'[FluidAvatar] e del suo mosaico.
  - `val Size: Dp` — Il diametro di serie: 40 dp, quello di un avatar in una riga a due righe di testo.
  - `val TouchTarget: Dp` — Il bersaglio minimo di un avatar **toccabile**: 48 dp. Un avatar di 40 dp che si tocca occupa 48 dp nel layout (e li' si prende il tocco), centrato: l'alternativa, un'area invisibile piu' grande del disegno, sovrapporrebbe i bersagli della riga e dell'avatar.
  - `const val StackMax: Int` — Quante celle al massimo ha un mosaico ([FluidAvatarStack]): una griglia due per due.
  - `const val MonogramLetters: Int` — Quante lettere porta di serie un monogramma ([fluidMonogram]).
- `data class FluidAvatarItem(val name: String, val key: String = name, val color: Color = Color.Unspecified, val image: (@Composable () -> Unit)? = null)` — Una persona (o un gruppo, o un servizio) per il mosaico di [FluidAvatarStack].
- `@Composable fun FluidAvatar(name: String, modifier: Modifier = Modifier, key: String = name, size: Dp = FluidAvatarDefaults.Size, color: Color = Color.Unspecified, image: (@Composable () -> Unit)? = null, selected: Boolean? = null, corner: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null, contentDescription: String? = null)` — Il cerchio di una persona: un monogramma su un colore che non cambia mai, oppure un'immagine che porta l'app, e al posto del volto — in una lista che sta selezionando — il segno di selezione.
- `@Composable fun FluidAvatarStack(items: List<FluidAvatarItem>, modifier: Modifier = Modifier, size: Dp = FluidAvatarDefaults.Size, max: Int = FluidAvatarDefaults.StackMax, contentDescription: String? = null, onClick: (() -> Unit)? = null)` — Un mosaico di piu' persone in un cerchio solo: la conversazione con piu' interlocutori, il gruppo, le caselle di un'app con piu' account.
- `fun fluidAvatarStackDescription(names: List<String>, max: Int = FluidAvatarDefaults.StackMax, labels: FluidLabels = FluidLabels()): String` — Il nome per TalkBack di un mosaico: i nomi che si vedono, e il resto detto con le parole dell'app ([FluidLabels.moreAvatars]: «e altri 3»).
- `fun fluidMonogram(name: String, maxLetters: Int = FluidAvatarDefaults.MonogramLetters): String` — Le iniziali di un nome, per grafemi e non per caratteri: la lettera che si mette in un cerchio.

## `engine-ui/ui/fluid/FluidBadge.kt`

- `object FluidBadgeDefaults` — Le misure del [FluidBadge].
  - `val DotSize: Dp` — Il diametro del pallino: 8 dp. Non cresce con il testo di sistema: non contiene niente da leggere.
  - `val MinHeight: Dp` — L'altezza minima della capsula col numero: 16 dp. Col testo grande la capsula cresce con il numero.
  - `const val Max: Int` — Il numero oltre il quale la capsula dice «99+».
- `fun fluidBadgeText(count: Int, max: Int = FluidBadgeDefaults.Max): String` — Il testo di una capsula numerica: il numero, o «99+» se supera [max]; vuoto per zero e per i valori negativi (un badge a zero non si disegna).
- `@Composable fun FluidBadge(count: Int? = null, modifier: Modifier = Modifier, max: Int = FluidBadgeDefaults.Max, color: Color = Color.Unspecified, contentDescription: String? = null)` — Il segno piccolo di «c'e' qualcosa»: un pallino, o una capsula con un numero («3», «99+»).

## `engine-ui/ui/fluid/FluidContent.kt`

- `@Composable fun FluidSectionHeader(title: String, modifier: Modifier = Modifier, detail: String? = null)` — Header above a grouped list.
- `@Composable fun FluidLoadingBlock(modifier: Modifier = Modifier, minHeight: Dp = 180.dp)` — Placeholder while a screen's first load is in flight.
- `@Composable fun FluidSectionFootnote(text: String, modifier: Modifier = Modifier)` — Footnote under a grouped list, for the explanation a row's subtitle would make too long.

## `engine-ui/ui/fluid/FluidExpandableText.kt`

- `@Composable fun FluidExpandableText(text: AnnotatedString, modifier: Modifier = Modifier, collapsedMaxLines: Int = 3, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = MaterialTheme.colorScheme.onSurface, moreLabel: String = LocalFluidLabels.current.liveText.more, moreColor: Color = MaterialTheme.colorScheme.primary, collapsible: Boolean = false, arrival: FluidTextArrival = FluidTextArrival.Lines, onExpandedChange: ((Boolean) -> Unit)? = null)` — Un paragrafo lungo chiuso a [collapsedMaxLines] righe, con «altro» in fondo all'ultima riga: un tocco lo apre sul posto (F8.1, `testo-espandibile`), come le descrizioni e le novità dell'App Store. In SwiftUI non c'è un componente: l'App Store lo fa con `lineLimit` e un tasto in coda alla riga.
- `@Composable fun FluidExpandableText(text: String, modifier: Modifier = Modifier, collapsedMaxLines: Int = 3, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = MaterialTheme.colorScheme.onSurface, moreLabel: String = LocalFluidLabels.current.liveText.more, moreColor: Color = MaterialTheme.colorScheme.primary, collapsible: Boolean = false, arrival: FluidTextArrival = FluidTextArrival.Lines, onExpandedChange: ((Boolean) -> Unit)? = null)` — Lo stesso paragrafo da un testo semplice.

## `engine-ui/ui/fluid/FluidFileInfo.kt`

- `enum class FluidFileKind` — Categoria visiva generica; non certifica la sicurezza o il contenuto del file.
  - `companion object`
    - `fun from(mimeType: String?, fileName: String): FluidFileKind` — L'ultima estensione prevale; un MIME eseguibile non viene mai declassato da un nome innocuo.
- `fun fluidFormatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String` — Dimensione decimale (1000 byte = 1 KB), una cifra decimale al massimo; ignoto va rappresentato con null dal chiamante. Dalla 4.0 (F8.1) è [FluidFormat.bytes] con lo stile dei file, che ha preso la stessa regola: il testo non cambia.
- `fun fluidFormatFileSizeRange(before: Long?, after: Long?, locale: Locale = Locale.getDefault()): String?` — The original and expanded sizes share a unit when both values use the same one.
- `fun fluidFormatFileDate(instant: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone), includeTime: Boolean = false): String` — A file timestamp in the user's time zone. The current day and its predecessor use the locale's relative names.

## `engine-ui/ui/fluid/FluidFileTile.kt`

- `sealed interface FluidFileStatus` — Stato deciso dall'app: nessun download, accesso URI o caricamento immagini viene avviato dall'engine.
  - `data object Remote : FluidFileStatus`
  - `data object Ready : FluidFileStatus`
  - `class Transferring(val progress: () -> Float? = { null }) : FluidFileStatus` — Lettura differita: passare { state.value }, senza leggere il progresso nella riga chiamante. Null = indeterminato.
  - `data object Failed : FluidFileStatus`
- `@Composable fun FluidFileTile(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, mimeType: String? = null, sizeBytes: Long? = null, status: FluidFileStatus = FluidFileStatus.Ready, onCancel: (() -> Unit)? = null, onRetry: (() -> Unit)? = null, onRemove: (() -> Unit)? = null, thumbnail: (@Composable () -> Unit)? = null, trailingMenu: (@Composable () -> Unit)? = null, locale: Locale = LocalConfiguration.current.locales[0])` — File in un gruppo di contenuto. Nome fino a due righe, estensione finale conservata e nome intero per TalkBack. [thumbnail] e [trailingMenu] sono dell'app: il menu deve usare gli host Fluid, la miniatura non deve sostituire l'avviso sugli eseguibili. [onClick] scarica Remote o apre Ready; durante trasferimento/errore e disabilitato.
- `@Composable fun FluidFileChip(name: String, onClick: () -> Unit, modifier: Modifier = Modifier, mimeType: String? = null)` — File compatto per righe dense: superficie piu bassa del bersaglio tattile Compose da 48 dp, espanso fuori dal layout tramite ViewConfiguration. Lasciare spazio fra chip per evitare bersagli sovrapposti. Il gesto cede agli scroll degli antenati; l'estensione non viene sostituita da quella dichiarata dal MIME.

## `engine-ui/ui/fluid/FluidGlassGroups.kt`

- `const val FluidGlassGroupMaxRows` — Quante righe stanno in un pannello di vetro prima che ne cominci un altro. Vedi [fluidGlassGroups].
- `fun <T> LazyListScope.fluidGlassGroups(items: List<T>, key: String? = null, maxRows: Int = FluidGlassGroupMaxRows, row: @Composable (T) -> Unit)` — Una sezione di righe, dentro una lista pigra, come pannelli di vetro.
- `fun <T> LazyListScope.fluidGlassGroupsIndexed(items: List<T>, key: String? = null, maxRows: Int = FluidGlassGroupMaxRows, row: @Composable (index: Int, item: T) -> Unit)` — [fluidGlassGroups] con la posizione della riga nella lista data (3.0.1), come `itemsIndexed`: per il numero di una classifica, una riga che si comporta diversamente se e' la prima, un'animazione sfalsata. L'indice e' quello in [items], non quello nel pannello: il taglio in pannelli resta invisibile anche qui.
- `@Composable fun <T> FluidGlassGroup(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit)` — Le stesse righe in un pannello solo, fuori da una lista pigra: per una sezione breve che sta in una colonna ([fluidColumns]) accanto ad altre.

## `engine-ui/ui/fluid/FluidGroupedItems.kt`

- `annotation class FluidExperimentalRowGlass` — Il vetro **riga per riga** di [fluidGroupedItems] ([FluidGroupedMaterial.GlassSegments]).
- `value class FluidGroupedMaterial private constructor(private val id: Int)` — Il fondo delle righe di [fluidGroupedItems].
  - `companion object`
    - `val Opaque: FluidGroupedMaterial` — Il fondo opaco del gruppo. Di serie.
    - `val Tinted: FluidGroupedMaterial` — La pellicola del vetro, dipinta senza cattura.
    - `val GlassSegments: FluidGroupedMaterial` — Una lastra di vetro per riga: solo per il banco di misura.
- `object FluidGroupedItemsDefaults` — I valori di serie di [fluidGroupedItems].
  - `const val ContentType: String` — Il tipo di contenuto di serie di una riga: tutte le righe di tutte le sezioni si riciclano fra loro. Un'app con righe di forma diversa (una riga di posta e un'intestazione di giorno) passa i suoi tipi.
- `interface FluidGroupedItemScope : LazyItemScope` — Lo scope di una riga di [fluidGroupedItems]: quello di un item della lista pigra (per `animateItem`, `fillParentMaxWidth`...) piu' il pezzo che la riga occupa nella sezione.
  - `val segment: FluidGroupSegment` — Il pezzo della sezione: `Whole` se e' l'unica riga, `First`/`Last` agli estremi, `Middle` in mezzo.
  - `val segmentShape: Shape` — La sagoma del pezzo: angoli tondi solo dove la sezione comincia o finisce. Un'istanza stabile per pezzo.
  - `val rowFill: Color` — Un fondo **opaco** che somiglia a quello della riga: quello da dipingere sotto la riga quando si sposta (lo scorrimento di lato), perche' dietro c'e' il colore dell'azione e un fondo trasparente lo lascerebbe vedere attraverso il testo.
- `fun LazyListScope.fluidGroupedItems(count: Int, key: ((index: Int) -> Any)? = null, contentType: (index: Int) -> Any? = { FluidGroupedItemsDefaults.ContentType }, material: FluidGroupedMaterial = FluidGroupedMaterial.Opaque, dividers: Boolean = true, animateItems: Boolean = true, itemContent: @Composable FluidGroupedItemScope.(index: Int) -> Unit)` — Le righe di una sezione come **un item della lista pigra per riga**, raggruppate: angoli tondi solo sulla prima e sull'ultima, separatori fra l'una e l'altra, nessun vuoto in mezzo.
- `fun <T> LazyListScope.fluidGroupedItems(items: List<T>, key: ((item: T) -> Any)? = null, contentType: (item: T) -> Any? = { FluidGroupedItemsDefaults.ContentType }, material: FluidGroupedMaterial = FluidGroupedMaterial.Opaque, dividers: Boolean = true, animateItems: Boolean = true, itemContent: @Composable FluidGroupedItemScope.(item: T) -> Unit)` — [fluidGroupedItems] su una lista gia' in memoria, come `items(list)`: la chiave e il tipo si chiedono all'elemento. Per un elenco paginato resta la forma per indice.

## `engine-ui/ui/fluid/FluidHero.kt`

- `enum class FluidHeroTone` — Dove una schermata si colloca nella famiglia cromatica dell'app.
- `enum class FluidHeroMotif` — Il motivo astratto disegnato dietro l'intestazione.
- `data class FluidHeroMetric(val label: String, val value: String, val onClick: (() -> Unit)? = null)`
- `@Composable fun FluidHero(tone: FluidHeroTone, motif: FluidHeroMotif, eyebrow: String, value: String, title: String, description: String, icon: ImageVector, modifier: Modifier = Modifier, metrics: List<FluidHeroMetric> = emptyList(), urgent: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null)` — L'apertura editoriale di una schermata piena di dati.
- `@Composable fun FluidHeroBand(tone: FluidHeroTone, motif: FluidHeroMotif, eyebrow: String, value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier, urgent: Boolean = false, trailing: (@Composable RowScope.() -> Unit)? = null)` — L'apertura di una schermata quando lo spazio conta: una fascia, non un pannello.

## `engine-ui/ui/fluid/FluidIdentityColors.kt`

- `data class FluidIdentityColor(val light: Color, val dark: Color)` — Un colore d'identita' in due versioni: quella da usare su un tema chiaro e quella su un tema scuro.
  - `fun forTheme(darkTheme: Boolean): Color` — Il lato che serve al tema: [dark] se [darkTheme], altrimenti [light].
- `object FluidIdentityColors` — La palette categoriale dell'engine: dodici tinte distinguibili fra loro, per tutto cio' che deve **riconoscersi** senza dire un numero — il fondo di un monogramma, il colore di un account o di una materia, un'etichetta, la serie di un grafico.
  - `val palette: List<FluidIdentityColor>` — Le tinte, in ordine di cerchio cromatico (rosso → rosa): tre sono volutamente non sature (oro, lime) perche' sono quelle che un colore «vivo» renderebbe illeggibili sotto una sigla.
  - `fun indexFor(key: String): Int` — L'indice in [palette] di una chiave: **FNV-1a a 32 bit** sui byte UTF-8 del testo, preso senza segno modulo la dimensione della palette. Definito qui, nel dettaglio, perche' e' il contratto di stabilita': l'hash di `String.hashCode()` e' specificato dalla JVM ma non e' una promessa dell'engine, e un hash «tuo» scelto domani sposterebbe i colori di tutti.
  - `fun colorFor(key: String): FluidIdentityColor` — La coppia scelta da una chiave: `palette[indexFor(key)]`.
  - `fun colorAt(index: Int): FluidIdentityColor` — La coppia all'[index] dato, **con giro**: l'indice scelto da una persona (il colore di un account che ha toccato nelle impostazioni) resta valido anche se un domani la palette ha meno tinte, e un indice negativo non esplode.
- `@Composable fun fluidIdentityColor(key: String): Color` — Il colore d'identita' di una [key] nel tema in scope: il lato chiaro o scuro, secondo la luminosita' della superficie (non secondo `isSystemInDarkTheme`, che non sa se l'app ha il suo tema).
- `@Composable fun fluidIdentityColor(index: Int): Color` — Come [fluidIdentityColor] ma per un indice scelto dalla persona; vedi [FluidIdentityColors.colorAt].

## `engine-ui/ui/fluid/FluidImageViewer.kt`

- `object FluidZoom` — I conti dello zoom, puri e provati (`FluidZoomTest`): quanto si puo' ingrandire, dove l'immagine si puo' spingere, dove si deve spostare per tenere fermo il punto che si pizzica.
  - `const val MinScale: Float` — Piu' piccola di cosi' l'immagine non va: a scala 1 sta tutta nel contenitore.
  - `const val DefaultMaxScale: Float` — Quanto si puo' ingrandire, di serie: quattro volte l'immagine adattata al contenitore.
  - `const val DoubleTapScale: Float` — Dove porta un doppio tocco, partendo da scala 1.
  - `fun isZoomed(scale: Float): Boolean`
  - `fun clampScale(scale: Float, maxScale: Float = DefaultMaxScale): Float` — La scala tenuta fra [MinScale] e [maxScale] (mai sotto 1, anche se qualcuno passa un massimo minore).
  - `fun fitSize(container: Size, aspectRatio: Float?): Size` — La taglia a cui l'immagine sta, a scala 1, dentro [container] **adattata** (tutta visibile, senza tagliare): da qui si vede quanto e' grande davvero, e quindi fin dove si puo' spingere.
  - `fun maxPan(container: Size, content: Size, scale: Float): Offset` — Di quanto si puo' spingere l'immagine, per asse: quanto la sua taglia in scala eccede il contenitore, diviso due (la meta' per lato). **Zero** se in scala sta ancora dentro: un'immagine piu' piccola del contenitore resta al centro, non si porta in un angolo.
  - `fun clampPan(pan: Offset, container: Size, content: Size, scale: Float): Offset` — [pan] tenuto dentro [maxPan]: l'immagine non lascia mai uno spazio vuoto fra il suo bordo e quello dello schermo.
  - `fun zoomAbout(scale: Float, pan: Offset, newScale: Float, focal: Offset): Offset` — La traslazione nuova quando la scala passa da [scale] a [newScale] **tenendo fermo il punto [focal]** (rispetto al centro): il punto dell'immagine sotto le dita resta sotto le dita. E' la ragione per cui un pizzico ingrandisce *li'* e non verso il centro. Il punto fermo `q = (focal - pan) / scale` va a `focal = newScale * q + newPan`, quindi `newPan = focal - (newScale / scale) * (focal - pan)`.
  - `fun doubleTapTarget(scale: Float, maxScale: Float = DefaultMaxScale, tapScale: Float = DoubleTapScale): Float` — Dove porta un doppio tocco: da ingrandita torna a 1, da 1 va a [tapScale] (mai oltre [maxScale]).
  - `fun step(scale: Float, zoomIn: Boolean, maxScale: Float = DefaultMaxScale): Float` — Il livello dopo [scale] per i comandi a gradini (le azioni per TalkBack, la tastiera): 1, [DoubleTapScale], [maxScale]. Ingrandire da un livello va al successivo, ridurre al precedente; dagli estremi non si muove.
- `class FluidZoomState internal constructor(val maxScale: Float, private val scope: CoroutineScope, private val reducedMotion: () -> Boolean)` — Lo stato dello zoom di un'immagine: la scala e lo spostamento, e come cambiano.
  - `var scale: Float by mutableFloatStateOf(FluidZoom.MinScale)`
  - `var panX: Float by mutableFloatStateOf(0f)`
  - `var panY: Float by mutableFloatStateOf(0f)`
  - `val isZoomed: Boolean get()`
  - `fun reset()` — Torna a scala 1, senza animazione: per un'immagine che esce di scena.
  - `fun zoomTo(target: Float, focal: Offset = Offset.Zero)` — Porta la scala a [target] tenendo fermo il punto [focal]: un doppio tocco, un gradino. Con le animazioni ridotte arriva subito; altrimenti una molla critica (un'immagine che ingrandisce non rimbalza).
- `@Composable fun rememberFluidZoomState(maxScale: Float = FluidZoom.DefaultMaxScale): FluidZoomState` — Lo stato dello zoom di un'immagine.
- `@Composable fun Modifier.fluidZoomable(state: FluidZoomState, contentAspectRatio: Float? = null, enabled: Boolean = true, onTap: (() -> Unit)? = null, strings: FluidImageViewerStrings = FluidImageViewerStrings()): Modifier` — Rende ingrandibile quello che il modificatore avvolge: pizzico, doppio tocco, spostamento limitato al contenuto.
- `data class FluidImageViewerStrings(val zoomIn: String = "Ingrandisci", val zoomOut: String = "Riduci", val zoomState: (percent: Int) -> String = { "Zoom $it%" })` — Le parole del visualizzatore, in italiano di serie.
- `@Composable fun FluidImageViewer(visible: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, pageCount: Int = 1, initialPage: Int = 0, onPageChange: (page: Int) -> Unit = {}, title: (page: Int, pageCount: Int) -> String? = { page, count -> if (count > 1) "${page + 1} di…, maxScale: Float = FluidZoom.DefaultMaxScale, contentAspectRatio: (page: Int) -> Float? = { null }, strings: FluidImageViewerStrings = FluidImageViewerStrings(), background: Color? = null, page: @Composable (page: Int) -> Unit)` — Il visualizzatore di immagini a tutto schermo, **dentro l'host dei modali** dell'engine: una o piu' immagini, pizzico e doppio tocco per ingrandire, spostamento limitato all'immagine, congedo trascinando in giu'.

## `engine-ui/ui/fluid/FluidJumpPill.kt`

- `enum class FluidJumpDirection` — Verso dove porta una [FluidJumpPill]: in cima (le novita' sopra) o in fondo (le novita' sotto).
- `@Composable fun FluidJumpPill(visible: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, direction: FluidJumpDirection = FluidJumpDirection.Up, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…)` — La pillola di vetro «N nuove ↑»: arrivano elementi sopra (o sotto) il punto in cui l'utente sta leggendo, e invece di spostargli la lista sotto il dito glielo si dice, con un tocco per andarci.

## `engine-ui/ui/fluid/FluidLiveDot.kt`

- `object FluidLiveDotDefaults` — Le misure del [FluidLiveDot].
  - `val Size: Dp` — Il diametro: 7 dp, accanto a un testo da 13-17 sp il pallino si legge senza farsi guardare.
- `@Composable fun FluidLiveDot(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, size: Dp = FluidLiveDotDefaults.Size, pulsing: Boolean = true, contentDescription: String? = null)` — Il pallino che pulsa accanto a un dato vivo: i minuti di un bus seguito dal feed, «in diretta», un lettore che sta registrando.

## `engine-ui/ui/fluid/FluidLiveTime.kt`

- `fun interface FluidTimeSource` — L'ora dei testi vivi, in millisecondi dall'epoca. Di serie quella del sistema; le prove ne passano una finta.
  - `fun nowMillis(): Long`
  - `companion object`
    - `val System: FluidTimeSource` — L'orologio del sistema (`System.currentTimeMillis`).
- `val LocalFluidTimeSource` — L'ora dei testi vivi sotto questo punto dell'albero.
- `val LocalFluidLiveClock` — L'orologio della pagina, se c'è ([FluidLiveClockProvider]); senza, ogni testo vivo aspetta da solo.
- `class FluidLiveClock` — L'orologio unico di una pagina per i testi vivi: **un'attesa sola** per tutti i [FluidRelativeText] e i [FluidTimerText] sotto di lui, invece di un'attesa per riga. Si sveglia al primo cambio che serve, arrotondato al secondo pieno, e in quel giro aggiorna tutti i testi il cui cambio è arrivato: una lista di cento righe col «12 min fa» batte al più una volta al secondo, e solo quando un testo cambia davvero (la sched…
- `@Composable fun rememberFluidLiveClock(): FluidLiveClock` — Un orologio della pagina ricordato.
- `@Composable fun FluidLiveClockProvider(clock: FluidLiveClock = rememberFluidLiveClock(), content: @Composable () -> Unit)` — Mette l'orologio unico ([FluidLiveClock]) sotto [content]: una volta per pagina, attorno alla lista che ha i tempi vivi. Gira solo finché c'è un testo che aspetta.
- `@Composable fun FluidRelativeText(instant: Instant, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, width: FluidFormatWidth = FluidFormatWidth.Short, locale: Locale = currentLocale())` — Un tempo relativo che **si aggiorna da solo**, al passo giusto: «ora», «2 min fa», «tra 3 giorni» (F8.1, `formattatori-e-tempi-vivi`), come `Text(date, style: .relative)` di SwiftUI, con le parole e le soglie di [FluidFormat.relative]. Batte una volta al minuto a «12 min fa», una volta all'ora a «3 h fa», mai a ogni fotogramma; con [FluidLiveClockProvider] sulla pagina tutti i tempi della pagina battono insieme.
- `@Composable fun FluidTimerText(endsAt: Instant, modifier: Modifier = Modifier, style: TextStyle = FluidTextStyles.numeric, color: Color = Color.Unspecified, onExpire: (() -> Unit)? = null)` — Il tempo che manca a [endsAt], da cronometro: «4:59», «1:02:07» (F8.1, `conto-alla-rovescia`), come `Text(timerInterval:)` di SwiftUI. Le cifre **scorrono** come i numeri che scorrono ([FluidNumericText]), verso il basso perché il tempo cala, e sono tabulari: la riga non balla.
- `@Composable fun FluidCountdownBadge(endsAt: Instant, modifier: Modifier = Modifier, total: Duration? = null, tone: Color = MaterialTheme.colorScheme.primary, onExpire: (() -> Unit)? = null)` — Una capsula col tempo che scende (F8.1, `conto-alla-rovescia`): un anello che si svuota e il cronometro ([FluidTimerText]), come i timer delle Live Activity e di WidgetKit (`ProgressView(timerInterval:)` accanto a `Text(timerInterval:)`). Per il timer della notte di Sounds, la consegna di un compito, l'«Annulla invio» in linea.

## `engine-ui/ui/fluid/FluidMarkdown.kt`

- `data class FluidMarkdownStrings(val codeLabel: String = "codice", val copy: String = "copia", val copied: String = "copiato", val copyDescription: String = "Copia il codice", val codeBlockDescription: String = "Blocco di %1\$s", val taskDone: String = "Fatto", val taskOpen: String = "Da fare")` — Le parole del renderer: l'etichetta del blocco di codice e il suo tasto. Come le altre `Fluid*Strings`: un'app tradotta le passa tutte.
- `data class FluidMarkdownStyle(val body: TextStyle, val heading: TextStyle, val subheading: TextStyle, val code: TextStyle, val textColor: Color, val mutedColor: Color, val linkColor: Color, val markerColor: Color, val codeBackground: Color, val quoteBar: Color, val blockSpacing: Dp = 8.dp, val headings: List<TextStyle> = emptyList())` — Colori e misure del testo Markdown. Di serie vengono dal tema ([FluidMarkdownDefaults.style]): chi vuole il testo su un altro fondo (sopra il cielo di un onboarding, dentro una bolla colorata) cambia [textColor] e [mutedColor] e il resto segue.
  - `fun headingFor(level: Int): TextStyle` — La misura del titolo di [level] (da 1 a 6).
- `object FluidMarkdownDefaults`
  - `@Composable fun style(textColor: Color = MaterialTheme.colorScheme.onSurface, mutedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, blockSpacing: Dp = 8.dp, body: TextStyle = MaterialTheme.typography.bodyLarge, headings: List<TextStyle> = emptyList()): FluidMarkdownStyle` — Lo stile del tema corrente.
  - `@Composable fun documentHeadings(): List<TextStyle>` — I titoli di una pagina di testo, una nota o un changelog: quattro misure in semigrassetto, da `titleLarge` per `#` a `bodyLarge` dal `####`. Si passa a [style] come `headings`.
- `@Composable fun FluidMarkdown(markdown: String, modifier: Modifier = Modifier, streaming: Boolean = false, animateFirst: Boolean = streaming, style: FluidMarkdownStyle = FluidMarkdownDefaults.style(), strings: FluidMarkdownStrings = FluidMarkdownStrings(), options: FluidMarkdownOptions = FluidMarkdownOptions(), onLinkClick: ((String) -> Unit)? = null, selectable: Boolean = false)` — Il Markdown, disegnato: la risposta di un assistente come una nota, le novita' di una versione, il testo di una guida. Titoli, elenchi (anche annidati), corsivo, grassetto, barrato, codice in linea e a blocco (con la lingua in testa e il tasto "copia", il testo che scorre invece di essere tagliato), link, citazioni, tabelle che scorrono, righe.

## `engine-ui/ui/fluid/FluidMarkdownParser.kt`

- `sealed interface FluidMdInline` — Il Markdown ridotto a un albero: titoli, paragrafi, elenchi (anche annidati), citazioni, blocchi di codice, tabelle, righe, e in linea grassetto, corsivo, barrato, codice, link. **Nessuna riga di Compose qui dentro**: e' il pezzo che le app avevano scritto ognuna per conto suo (tre `MarkdownLite` da un centinaio di righe, una libreria esterna per le note, un secondo resa a blocchi per lo streaming di una chat) e che…
  - `data class Text(val text: String) : FluidMdInline`
  - `data class Bold(val children: List<FluidMdInline>) : FluidMdInline`
  - `data class Italic(val children: List<FluidMdInline>) : FluidMdInline`
  - `data class Strike(val children: List<FluidMdInline>) : FluidMdInline`
  - `data class Code(val text: String) : FluidMdInline`
  - `data class Link(val children: List<FluidMdInline>, val url: String) : FluidMdInline`
- `enum class FluidMdAlign` — Come si allinea una colonna di una tabella.
- `data class FluidMdListItem(val inlines: List<FluidMdInline>, val children: List<FluidMdBlock> = emptyList(), val checked: Boolean? = null)` — Una voce di elenco: il suo testo, e i blocchi che le stanno sotto (un sotto-elenco, un paragrafo rientrato).
- `sealed interface FluidMdBlock`
  - `data class Paragraph(val inlines: List<FluidMdInline>) : FluidMdBlock`
  - `data class Heading(val level: Int, val inlines: List<FluidMdInline>) : FluidMdBlock` — [level] da 1 a 6; di serie chi lo disegna ne fa due misure sole (vedi `FluidMarkdownStyle.headings`).
  - `data class ListBlock(val ordered: Boolean, val start: Int, val items: List<FluidMdListItem>) : FluidMdBlock` — [start] e' il numero della prima voce di un elenco numerato (un elenco che comincia da 3 resta da 3).
  - `data class CodeBlock(val language: String?, val code: String, val closed: Boolean) : FluidMdBlock` — [closed] falso: il testo e' ancora in arrivo e i tre apici di chiusura non ci sono.
  - `data class Quote(val blocks: List<FluidMdBlock>) : FluidMdBlock`
  - `data class Table(val header: List<List<FluidMdInline>>, val aligns: List<FluidMdAlign>, val rows: List<List<List<FluidMdInline>>>) : FluidMdBlock`
    - `val columns: Int get()`
  - `data object Rule : FluidMdBlock`
- `data class FluidMarkdownOptions(val hideChipMarkers: Boolean = false, val softBreak: String = " ")`
- `object FluidMarkdownParser`
  - `fun parse(markdown: String, options: FluidMarkdownOptions = FluidMarkdownOptions()): List<FluidMdBlock>`
  - `fun inlines(text: String, options: FluidMarkdownOptions = FluidMarkdownOptions()): List<FluidMdInline>` — Grassetto, corsivo, barrato, codice e link in una riga; un segno aperto e mai chiuso resta testo.
  - `fun plainText(markdown: String, includeCode: Boolean = false, bullets: Boolean = false, options: FluidMarkdownOptions = FluidMarkdownOptions()): String` — Lo stesso testo senza i segni: per la voce di sistema (che leggerebbe gli asterischi), per un'anteprima in cronologia, per la condivisione in chiaro. Il codice e' rumore detto ad alta voce e di default si salta; le voci di un elenco restano una per riga.
  - `fun plain(inlines: List<FluidMdInline>): String`

## `engine-ui/ui/fluid/FluidNumericText.kt`

- `@Composable fun FluidNumericText(text: String, value: Double, modifier: Modifier = Modifier, style: TextStyle = FluidTextStyles.numeric, color: Color = Color.Unspecified, contentDescription: String? = null)` — Un numero che cambia **facendo scorrere solo le cifre cambiate** (F8.1, `numeri-che-scorrono`): in su se il valore cresce, in giù se cala, sfocate mentre viaggiano (la nota di Alessio: «col blur durante il movimento»). È `Text(…).contentTransition(.numericText(value:))` di SwiftUI (iOS 17), quello dei timer di Orologio e dei saldi di Wallet.
- `@Composable fun FluidNumericText(value: Number, modifier: Modifier = Modifier, style: TextStyle = FluidTextStyles.numeric, color: Color = Color.Unspecified, format: (Number) -> String = { FluidFormat.number(it.toDouble()) })` — Un numero che scorre, scritto da [format]: di serie con i separatori della lingua ([FluidFormat.number]). Per un contatore («3 non lette») o una percentuale.

## `engine-ui/ui/fluid/FluidPagerDots.kt`

- `object FluidPagerDotsDefaults` — Le misure del [FluidPagerDots].
  - `val DotSize: Dp` — Un punto a riposo e quello della pagina corrente: 7 e 10 dp, come l'onboarding di Fluidweather.
  - `val ActiveSize: Dp`
  - `val Spacing: Dp` — Lo spazio fra due punti, bordo a bordo del punto grande.
  - `const val InactiveAlpha: Float` — L'opacita' di un punto a riposo e di quello corrente.
  - `const val ActiveAlpha: Float`
  - `const val MaxVisible: Int` — Quanti punti si vedono al massimo. Oltre, la fila scorre e quelli ai bordi si rimpiccioliscono, come i controlli di pagina di iOS: venti punti in fila non sono un indicatore, sono una riga.
- `@Composable fun FluidPagerDots(state: PagerState, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, activeColor: Color = color, maxVisible: Int = FluidPagerDotsDefaults.MaxVisible, hideForSinglePage: Boolean = true, pageLabel: (page: Int, count: Int) -> String = { page, count -> "Pagina $page di $count" })` — L'indicatore di pagina di un [PagerState]: un punto per pagina, quello corrente piu' grande e pieno.
- `@Composable fun FluidPagerDots(pageCount: Int, position: () -> Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, activeColor: Color = color, maxVisible: Int = FluidPagerDotsDefaults.MaxVisible, hideForSinglePage: Boolean = true, pageLabel: (page: Int, count: Int) -> String = { page, count -> "Pagina $page di $count" })` — L'indicatore di pagina: un punto per pagina, quello corrente piu' grande e pieno, che **segue il dito** e non scatta a pagina assestata.

## `engine-ui/ui/fluid/FluidProgress.kt`

- `@Composable fun FluidSpinner(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, progress: (() -> Float)? = null)` — The spinner.
- `@Composable fun FluidProgressBar(progress: () -> Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 4.dp)` — A determinate bar.
- `@Composable fun FluidIndeterminateBar(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, height: Dp = 3.dp)` — An indeterminate bar, for the rare case where something is loading but nothing knows how far.

## `engine-ui/ui/fluid/FluidReadyStrings.kt`

- `data class FluidLiveTextStrings(val more: String = "altro", val expand: String = "Espandi", val collapse: String = "Riduci", val collapsed: String = "Testo ridotto", val expanded: String = "Testo intero", val remaining: String = "Mancano %1\$s", val expired: String = "Tempo scaduto", val imageLoading: String = "Caricamento dell'immagine", val imageFailed: String = "Immagine non caricata", val retry: String = "Riprova")` — Le parole del testo che si muove (F8.1), **in italiano di serie** come il resto di [FluidLabels], che le porta in `FluidLabels.liveText`. Le frasi con un segnaposto (`%1$s`) restano stringhe, così un'app le traduce come le sue risorse.
  - `companion object`
    - `fun english(): FluidLiveTextStrings` — Le stesse parole in inglese.
- `data class FluidContentStateStrings(val noResultsFor: String = "Nessun risultato per «%1\$s»", val noResults: String = "Nessun risultato", val noResultsDetail: String = "Controlla come l'hai scritto o prova un'altra…, val offline: String = "Sei offline", val offlineDetail: String = "Quando torna la connessione la pagina si aggio…, val error: String = "Qualcosa non ha funzionato", val errorDetail: String = "Non è colpa tua. Puoi riprovare fra un momento…, val retry: String = "Riprova", val retrying: String = "Nuovo tentativo in corso…", val retried: String = "Fatto", val retryFailed: String = "Non è riuscito", val permission: String = "Serve un permesso", val permissionDetail: String = "Per mostrare questa pagina serve un permesso c…, val openSettings: String = "Apri le impostazioni", val empty: String = "Ancora niente qui", val emptyDetail: String = "Quando ci sarà qualcosa, lo troverai qui.", val loading: String = "Caricamento")` — Le parole degli stati pronti di una pagina (F8.1, `stati-pronti`), **in italiano di serie**: `FluidLabels.contentStates`. I titoli e le frasi sono quelli di `ContentUnavailableView` di Apple, detti come li direbbe una persona.
  - `companion object`
    - `fun english(): FluidContentStateStrings` — Le stesse parole in inglese.

## `engine-ui/ui/fluid/FluidReorderable.kt`

- `data class FluidReorderStrings(val moveBefore: String = "Sposta prima", val moveAfter: String = "Sposta dopo")` — Le parole dell'accessibilita' del riordino, in italiano di serie.
- `object FluidReorderDefaults` — Le misure del riordino.
  - `const val LiftScale: Float` — Di quanto cresce un elemento sollevato. Poco: una riga che cresce del 4% e' una cosa presa in mano, del 10% e' un pulsante, e una tessera di griglia piu' grande invaderebbe le vicine.
  - `val AutoScrollEdge: Dp` — Quanto e' larga la fascia, ai bordi della lista, in cui trascinare fa scorrere la lista.
  - `val AutoScrollMaxSpeed: Dp` — La velocita' massima dello scorrimento automatico, in dp al secondo: a un bordo sono piu' di quattro righe al secondo.
  - `val FallbackShadow: Dp` — L'ombra dell'elemento sollevato dove non c'e' vetro (sotto API 31, o senza un canvas): di piattaforma, perche' li' il vetro non c'e'.
- `object FluidReorder` — La matematica del riordino, pura e provata (`FluidReorderTest`): sopra chi si sta passando, dove sta l'elemento sotto il dito, quando la lista deve scorrere da sola.
  - `fun autoScrollDelta(visualStart: Float, visualEnd: Float, viewportStart: Float, viewportEnd: Float, zone: Float, maxSpeedPx: Float, seconds: Float): Float` — Di quanto far scorrere la lista in questo fotogramma, in pixel: negativo verso l'inizio, positivo verso la fine, zero se la sagoma e' lontana dai bordi.
- `fun <T> List<T>.fluidMoved(from: Int, to: Int): List<T>` — La lista con [from] spostato in [to]: l'ordine nuovo dopo un `onMove`. Gli altri scalano.
- `class FluidReorderState internal constructor(private val layout: FluidReorderLayout, private val scope: CoroutineScope, private val haptics: () -> FluidHaptics, private val reducedMotion: () -> Boolean, private val onMove: () -> (Int, Int) -> Unit, private val onDragEnd: () -> () -> Unit, private val canMoveTo: () -> (Int) -> Boolean, private val autoScrollEdgePx: () -> Float, private val autoScrollMaxSpeedPx: () -> Float)` — Lo stato di un riordino: chi e' sollevato, di quanto, e cosa succede quando si sposta o si posa.
  - `var draggingKey: Any? by mutableStateOf(null)` — La chiave dell'elemento sollevato, o che si sta posando; null se nessuno.
  - `val isDragging: Boolean get()` — Vero mentre un dito tiene un elemento.
  - `fun moveBy(key: Any, delta: Int): Boolean` — Sposta [key] di [delta] posti (-1 prima, +1 dopo) **senza trascinare**: la strada per TalkBack, per la tastiera e per chi ha un interruttore. Vero se lo spostamento e' stato chiesto.
- `@Composable fun rememberFluidReorderState(listState: LazyListState, onMove: (from: Int, to: Int) -> Unit, onDragEnd: () -> Unit = {}, canMoveTo: (index: Int) -> Boolean = { true }): FluidReorderState` — Lo stato del riordino di una `LazyColumn` o `LazyRow`. Vedi [fluidReorderable].
- `@Composable fun rememberFluidReorderState(gridState: LazyGridState, onMove: (from: Int, to: Int) -> Unit, onDragEnd: () -> Unit = {}, canMoveTo: (index: Int) -> Boolean = { true }): FluidReorderState` — Lo stato del riordino di una `LazyVerticalGrid`. Vedi [fluidReorderable].
- `@Composable fun LazyItemScope.fluidReorderable(state: FluidReorderState, key: Any, enabled: Boolean = true, shape: Shape = ContinuousCornerShape(FluidRadius.Card), strings: FluidReorderStrings = FluidReorderStrings()): Modifier` — Rende un elemento di una `LazyColumn` o `LazyRow` riordinabile: **tieni premuto, sollevi, trascini**.
- `@Composable fun LazyGridItemScope.fluidReorderable(state: FluidReorderState, key: Any, enabled: Boolean = true, shape: Shape = ContinuousCornerShape(FluidRadius.Card), strings: FluidReorderStrings = FluidReorderStrings()): Modifier` — [fluidReorderable] per un elemento di una `LazyVerticalGrid`: tutti e due gli assi, i vicini scalano.

## `engine-ui/ui/fluid/FluidSelectionMark.kt`

- `object FluidSelectionMarkDefaults` — Le misure e le parole del [FluidSelectionMark].
  - `val Size: Dp` — Il lato: 24 dp, quanto l'icona di una riga.
  - `val RingWidth: Dp` — Lo spessore dell'anello a [Size]: 1,75 dp, e scala con il lato.
  - `const val PhotoScrimAlpha: Float` — Quanto il velo scuro sotto l'anello di un segno [FluidSelectionMark] sopra una foto: abbastanza da leggere l'anello bianco su un cielo, poco da non far sembrare la foto sporca.
  - `fun description(selected: Boolean): String` — Cosa legge TalkBack, per stato. Un'app in un'altra lingua passa la sua.
- `@Composable fun FluidSelectionMark(selected: Boolean, modifier: Modifier = Modifier, size: Dp = FluidSelectionMarkDefaults.Size, onPhoto: Boolean = false, tint: Color = Color.Unspecified, contentDescription: String? = FluidSelectionMarkDefaults.description(selected))` — Il segno della selezione multipla: un cerchio vuoto quando la riga si puo' scegliere, un disco pieno con la spunta quando e' scelta. Nello spazio `leading` di una `FluidListRow`, o sull'angolo di una miniatura in una griglia.

## `engine-ui/ui/fluid/FluidSelectionState.kt`

- `sealed interface FluidSelectionMode<out K : Any>` — Cosa e' scelto, in una delle due forme che una selezione multipla puo' avere.
  - `data class Explicit<out K : Any>(val keys: Set<K>) : FluidSelectionMode<K>` — Sono scelte queste chiavi, nell'ordine in cui sono state scelte.
  - `data class AllExcept<out K : Any>(val excluded: Set<K>, val snapshot: String? = null) : FluidSelectionMode<K>` — E' scelto tutto tranne [excluded].
- `class FluidSelectionState<K : Any>(initial: FluidSelectionMode<K> = FluidSelectionMode.Explicit(emptySet()))` — Lo stato della selezione multipla di una lista: cosa e' scelto, e come si entra e si esce.
  - `val mode: FluidSelectionMode<K> get()` — Cosa e' scelto, come valore immutabile: salvabile, confrontabile, passabile a un'azione di massa.
  - `val isActive: Boolean` — C'e' una selezione in corso: almeno una chiave scelta, o «tutto tranne» qualcuno. E' cio' che fa comparire la capsula di azioni e il titolo «N selezionate», e che abilita il back.
  - `val isAllExcept: Boolean get()` — E' la forma «tutto tranne»: il numero scelto dipende dal totale ([countIn]).
  - `val snapshot: String?` — Il testimone dell'ultimo [selectAll]; null in forma esplicita o se l'app non ne ha passato uno.
  - `fun countIn(total: Int): Int` — Quante righe sono scelte, dato il [total] delle righe del risultato che l'app conosce.
  - `fun isSelected(key: K, belongsToSnapshot: Boolean = true): Boolean` — [key] e' scelta?
  - `fun toggle(key: K)` — Scelta se non lo e', non scelta se lo e'. Il tocco su un avatar o su una riga in selezione.
  - `fun select(key: K)` — Sceglie [key] se non lo era.
  - `fun deselect(key: K)` — Toglie [key] dalla selezione se c'era.
  - `fun setSelected(key: K, selected: Boolean)` — Porta [key] allo stato [selected]. In forma esplicita aggiunge o toglie la chiave; in forma «tutto tranne» toglie o aggiunge **l'esclusione**.
  - `fun selectAll(snapshot: String? = null)` — «Seleziona tutto»: passa alla forma «tutto tranne» senza esclusioni, **senza materializzare nessuna chiave**. Vale per l'intero risultato, anche le righe non caricate; vedi la documentazione della classe.
  - `fun clear()` — Esce dalla selezione: niente e' scelto.
  - `fun exitIfEmpty(total: Int)` — Esce se la selezione e' «tutto tranne» e il [total] non lascia niente di scelto (le esclusioni hanno raggiunto il totale: la persona ha tolto riga per riga tutto cio' che aveva scelto). Da chiamare quando cambiano il conteggio o le esclusioni, perche' l'engine non conosce il totale. In forma esplicita non fa niente: li' una selezione vuota e' gia' non attiva.
  - `fun retainOnly(valid: Set<K>)` — Toglie le chiavi **che non esistono piu'** nei dati, dopo un aggiornamento: l'azione e' stata fatta, la riga e' stata spostata, l'ha cancellata un'altra app.
  - `fun forget(keys: Collection<K>)` — Toglie le chiavi che l'app **sa** non esistere piu' (le righe che ha appena archiviato, eliminato, spostato), senza guardare le altre: e' la forma per le liste paginate, dove l'elenco di cio' che c'e' ancora non esiste. Vale per le due forme, per la stessa ragione di [retainOnly].
  - `fun clearIfSnapshotDiffers(currentSnapshot: String?)` — Esce dalla selezione «tutto tranne» se il testimone con cui fu fatta **non e' piu'** [currentSnapshot]: il risultato che la persona aveva scelto non e' piu' quello che vede (ha cambiato filtro, account, ricerca), e un'azione di massa lo applicherebbe a righe che non ha guardato. Non tocca la forma esplicita: le chiavi sono righe precise, valide in qualunque vista.
  - `companion object`
    - `fun <K : Any> saver(keySaver: Saver<K, out Any>? = null, maxSavedKeys: Int = DefaultMaxSavedKeys): Saver<FluidSelectionState<K>, Any>` — Il [Saver] di uno stato: la forma, il testimone e le chiavi, come una lista che il `Bundle` sa tenere. Sopravvive alla rotazione e alla morte del processo.
    - `const val DefaultMaxSavedKeys: Int` — Quante chiavi al massimo si salvano di serie: mille sono piu' di quante ne tocchi a mano una persona, e restano ben sotto il limite di un `Bundle`.
- `@Composable fun <K : Any> rememberFluidSelectionState(keySaver: Saver<K, out Any>? = null): FluidSelectionState<K>` — Lo stato di una selezione che resta com'e' alla rotazione e alla morte del processo ([FluidSelectionState.saver]).
- `@Composable fun FluidSelectionBackHandler(state: FluidSelectionState<*>, enabled: Boolean = true, onExit: (() -> Unit)? = null)` — Il back esce dalla selezione, e non da altro.

## `engine-ui/ui/fluid/FluidSettingRows.kt`

- `@Composable fun FluidToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String = "", enabled: Boolean = true, tone: FluidTone = FluidTone.Neutral, leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true)` — Una riga di impostazioni con un interruttore: **la riga e' l'interruttore**.
- `@Composable fun FluidChoiceRow(title: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, subtitle: String = "", enabled: Boolean = true, tone: FluidTone = FluidTone.Neutral, leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true)` — Una riga di scelta singola, con il segno di spunta a destra come nelle impostazioni di iOS: **non** un radio.
- `data class FluidPickerOption<K>(val key: K, val label: String, val subtitle: String = "")` — Un'alternativa di [FluidPickerRow]: la chiave che l'app conosce, e come si legge.
- `@Composable fun <K> FluidPickerRow(title: String, selectedKey: K, options: List<FluidPickerOption<K>>, onSelected: (K) -> Unit, modifier: Modifier = Modifier, subtitle: String = "", enabled: Boolean = true, tone: FluidTone = FluidTone.Neutral, leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true, paneTitle: String = title)` — Una riga che sceglie fra alternative in un pop-up (3.0.1): il titolo a sinistra, la scelta di adesso a destra, e al tocco **la riga diventa il pop-up** con le alternative — righe di scelta, la spunta su quella di adesso. Una scelta chiude il pop-up e chiama [onSelected] (solo se cambia); un tocco fuori lo chiude e basta.
- `@Composable fun FluidSliderRow(title: String, value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, valueLabel: String? = null, subtitle: String = "", valueRange: ClosedFloatingPointRange<Float> = 0f..1f, snapValues: List<Float> = emptyList(), enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null, tone: FluidTone = FluidTone.Neutral, leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true)` — Una riga con uno slider (3.0.1): il titolo e il valore in parole sopra, il [FluidSlider] sotto, nella misura di una riga di un gruppo — la luminosita', il volume, la soglia, il tempo di un timer.

## `engine-ui/ui/fluid/FluidSkeleton.kt`

- `object FluidSkeletonDefaults` — Le misure e i toni degli scheletri.
  - `const val LowAlpha: Float` — Il punto basso e quello alto del respiro: un velo del 6% che sale al 13%.
  - `const val HighAlpha: Float`
  - `const val RestAlpha: Float` — Fermo (animazioni ridotte): fra i due, perche' non e' ne' il punto basso ne' l'alto di niente.
  - `val Shape: Shape` — L'angolo dei blocchi: lo stesso di un'etichetta, `FluidRadius.Small`.
  - `val TileShape: Shape` — L'angolo di una tessera: quello di una card.
  - `val LineHeight: Dp` — Un'altezza di riga di testo corrente e quella di un titolo, per costruire gli scheletri a mano.
  - `val TitleHeight: Dp`
  - `val CaptionHeight: Dp`
- `enum class FluidSkeletonLeading` — Cosa c'e' davanti a una [FluidSkeletonRow]: niente, una piastrella come quella di `FluidListRow`, o un tondo.
- `@Composable fun FluidSkeleton(modifier: Modifier = Modifier, height: Dp = FluidSkeletonDefaults.LineHeight, shape: Shape = FluidSkeletonDefaults.Shape)` — Il segnaposto di un dato che sta arrivando: un blocco che respira.
- `@Composable fun FluidSkeletonText(modifier: Modifier = Modifier, lines: Int = 3, lineHeight: Dp = FluidSkeletonDefaults.LineHeight, spacing: Dp = 9.dp, lastLineFraction: Float = 0.64f)` — Un paragrafo che sta arrivando: [lines] righe, l'ultima corta ([lastLineFraction]).
- `@Composable fun FluidSkeletonRow(modifier: Modifier = Modifier, leading: FluidSkeletonLeading = FluidSkeletonLeading.Tile, lines: Int = 2, trailing: Boolean = false, variant: Int = 0)` — Una riga di elenco che sta arrivando, della misura di `FluidListRow`: piastrella, titolo, sotto titolo, e a destra un valore.
- `@Composable fun FluidSkeletonTile(modifier: Modifier = Modifier, aspectRatio: Float = 1f, caption: Boolean = true, shape: Shape = FluidSkeletonDefaults.TileShape)` — Una tessera di griglia che sta arrivando: un riquadro ([aspectRatio] come la miniatura vera) e, sotto, la didascalia.
- `@Composable fun FluidSkeletonList(modifier: Modifier = Modifier, rows: Int = 5, leading: FluidSkeletonLeading = FluidSkeletonLeading.Tile, lines: Int = 2, trailing: Boolean = false, label: String? = null)` — Un elenco che sta arrivando: [rows] righe che respirano insieme, con i titoli di larghezze diverse.

## `engine-ui/ui/fluid/FluidSpokenText.kt`

- `data class FluidSpokenWord(val start: Int, val end: Int, val startMs: Long, val endMs: Long)` — Una parola dentro un testo: dove sta, e quando viene detta.
- `@Composable fun FluidSpokenText(text: String, words: List<FluidSpokenWord>, positionMs: () -> Long, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, spokenColor: Color = MaterialTheme.colorScheme.onSurface, pendingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, onTextLayout: (TextLayoutResult) -> Unit = {})` — Il testo che si accende mentre l'audio va.
- `fun fluidSpokenChars(words: List<FluidSpokenWord>, positionMs: Long): Int` — Fin dove il testo e' stato detto, in caratteri, alla posizione data.

## `engine-ui/ui/fluid/FluidStepProgress.kt`

- `object FluidStepProgressDefaults` — Le misure e le parole di [FluidStepProgress].
  - `const val MaxSegments: Int` — Oltre questi passi la versione a segmenti diventerebbe una grana: si ripiega sulla barra continua.
  - `val SegmentHeight: Dp` — L'altezza dei segmenti: la barra continua dell'engine ne ha 4.
  - `val SegmentGap: Dp` — Lo spazio fra i segmenti.
  - `fun label(step: Int, steps: Int): String` — «Passo 2 di 5». Un'app in un'altra lingua passa la sua frase come `label`.
- `@Composable fun FluidStepProgress(step: Int, steps: Int, modifier: Modifier = Modifier, label: String = FluidStepProgressDefaults.label(step, steps), detail: String? = null, segmented: Boolean = false, pulseCurrent: Boolean = false, color: Color = MaterialTheme.colorScheme.primary, labels: List<String>? = null)` — L'intestazione di una procedura a passi: «Passo 2 di 5» e una barra che dice a che punto si e', senza rubare la scena al titolo.

## `engine-ui/ui/fluid/FluidSwipeActions.kt`

- `class FluidSwipeAction(val label: String, val icon: ImageVector, val color: Color, val removesItem: Boolean = false, val onAction: () -> Unit)` — Un'azione che una riga rivela scorrendo di lato: archivia, segna come letta, elimina, fissa.
- `fun FluidSwipeAction.asContextAction(destructive: Boolean = false): FluidContextAction` — La stessa azione come voce del menu contestuale della riga (4.0, F6.6).
- `value class FluidSwipeStyle private constructor(private val id: Int)` — L'aspetto delle azioni a scorrimento (4.0, F6.6).
  - `companion object`
    - `val Capsule: FluidSwipeStyle` — La capsula col glifo e l'etichetta sotto, la riga che si arrotonda: iOS 27. Di serie.
    - `val Fill: FluidSwipeStyle` — Il colore che riempie lo spazio rivelato: l'aspetto della 3.x.
- `val LocalFluidSwipeStyle: ProvidableCompositionLocal<FluidSwipeStyle>` — L'aspetto di serie delle azioni a scorrimento sotto questo punto: un'app (o la galleria, per mettere a confronto i due aspetti) lo cambia per tutte le righe senza toccarle una per una. Di serie [FluidSwipeStyle.Capsule].
- `@Composable fun FluidSwipeActionsContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit)` — Tiene una sola riga aperta fra le [fluidSwipeActions] discendenti, anche fra gruppi distinti. Avvolge la lista o l'area della schermata che deve richiudere la riga al tocco o allo scorrimento. I controlli esterni alla riga ricevono il loro tocco normalmente. Senza questo contenitore, un gruppo dell'engine coordina le proprie righe.
- `class FluidSwipeState internal constructor(private val scope: CoroutineScope)` — Lo stato di una riga a scorrimento: dove sta, se e' aperta, se la soglia e' superata, se e' uscita.
  - `var offset: Float by mutableFloatStateOf(0f)` — Lo spostamento della riga in px, **logico**: positivo verso la fine (rivela l'azione iniziale, a sinistra in LTR), negativo verso l'inizio. Letto nel disegno, mai nella composizione.
  - `var armed: Boolean by mutableStateOf(false)` — La soglia e' superata: rilasciando adesso l'azione parte.
  - `var isOpen: Boolean by mutableStateOf(false)` — La riga ha come destinazione la posa aperta, con l'azione esposta e toccabile.
  - `var isDismissed: Boolean by mutableStateOf(false)` — La riga e' uscita (un'azione con `removesItem` confermata) e aspetta che l'app la tolga. Non risponde al dito finche' non la si rimette con [reset].
  - `suspend fun reset()` — Rimette la riga a posto con la molla (istantanea col movimento ridotto): per un'azione che l'app non ha potuto fare, o per l'«Annulla» di una riga uscita che resta nei dati.
- `@Composable fun rememberFluidSwipeState(): FluidSwipeState` — Lo stato di una riga a scorrimento, ricordato nell'item.
- `@Composable fun Modifier.fluidSwipeActions(state: FluidSwipeState, startAction: FluidSwipeAction? = null, endAction: FluidSwipeAction? = null, enabled: Boolean = true, shape: Shape = RectangleShape, fill: Color = Color.Unspecified, accessibilityActions: Boolean = true, style: FluidSwipeStyle = LocalFluidSwipeStyle.current): Modifier` — Azioni a scorrimento orizzontale su una riga: un'azione per verso, soglia che si sente, annullabile tornando indietro col dito, uscita o ritorno al rilascio, e le stesse azioni per TalkBack.
- `fun Modifier.fluidAccessibilityActions(actions: List<CustomAccessibilityAction>): Modifier` — Le azioni personalizzate di TalkBack, **aggiunte** a quelle che il nodo ha gia'.

## `engine-ui/ui/fluid/FluidTextMotion.kt`

- `object FluidTextMotion` — I numeri di movimento del testo che si muove (F8.1): le parole e le righe che arrivano sfocate e si mettono a fuoco, le cifre che scorrono, il testo che si espande, l'immagine che arriva. Stanno qui, in un profilo loro, come chiedono le regole del piano («ogni numero di movimento sta nei profili o in `FluidMotion`»), e ognuno dice la fonte e la fiducia oppure «a occhio» con la data.
  - `const val WordArrivalMillis: Int` — Quanto ci mette una parola che arriva a mettersi a fuoco: **260 ms**. A occhio il 23 novembre 2026 (nota di Alessio «col blur»): i 180 ms della sola alfa (F3.2) bastavano a una dissolvenza, non lasciano vedere la messa a fuoco. È il tempo di [FluidRevealMillis].
  - `const val WordStaggerMillis: Int` — Il passo fra due parole dello stesso pezzo che arriva: **24 ms**, a occhio il 23 novembre 2026. Un modello manda le parole a pezzi di 3–10: senza passo un pezzo comparirebbe tutto insieme, ed è proprio quello che Alessio non vuole.
  - `const val WordSpreadMillis: Int` — Il ritardo massimo di una parola dietro al testo che arriva: **200 ms**, a occhio il 23 novembre 2026. Il passo non deve accumularsi: con un modello veloce le parole restano al più 200 ms indietro, e restano nell'ordine.
  - `const val LineArrivalMillis: Int` — Quanto ci mette una riga che arriva (il testo che si espande, una risposta che compare intera): **380 ms**, a occhio il 23 novembre 2026, vicino alla molla dell'altezza ([ExpandResponseSeconds]) perché l'ultima riga si mette a fuoco quando la cornice si ferma.
  - `const val LineStaggerMillis: Int` — Il passo fra due righe: **55 ms**, a occhio il 23 novembre 2026 (il «non tutto insieme ma gradualmente» di Alessio).
  - `const val LineSpreadMillis: Int` — Il ritardo massimo di una riga: **440 ms**, a occhio il 23 novembre 2026: un testo di 30 righe non arriva in due secondi.
  - `const val ArrivalPeakBlur: Float` — σ della sfocatura di ciò che arriva, al massimo, in frazione dell'altezza delle maiuscole: **0,15**. È la σ massima del contenuto che cambia identità di R7 A.2–A.3 (0,14–0,17 della sagoma, fiducia alta sulle icone; sul testo la stessa frazione è a occhio, 23 novembre 2026).
  - `const val CapHeight: Float` — L'altezza delle maiuscole di Inter in frazione del corpo: 0,727 (dai dati del carattere, `capHeight / unitsPerEm`).
  - `const val DigitResponseSeconds: Double` — La molla delle cifre che scorrono: risposta **0,5 s**, smorzamento 1. È `.smooth`, l'animazione di serie di `withAnimation` da iOS 17 (`Animation.default` è una molla senza rimbalzo di 0,5 s), con cui gira `contentTransition(.numericText(value:))` nei timer di Orologio e nei saldi di Wallet. Fiducia media: la durata è quella documentata, l'aspetto dei saldi è stato confrontato sulle tavole (F8.1).
  - `const val DigitTravel: Float` — Di quanto viaggia una cifra che esce o entra, in frazione dell'altezza della riga: **0,62**. A occhio il 23 novembre 2026 sui timer di Orologio: la cifra nuova non parte da una riga intera di distanza, sale da poco più di metà riga mentre la vecchia esce sfocandosi.
  - `const val DigitPeakBlur: Float` — σ massima di una cifra in viaggio, in frazione dell'altezza delle maiuscole: **0,15**, come [ArrivalPeakBlur] (R7).
  - `const val DigitReducedFadeMillis: Int` — La dissolvenza delle cifre col movimento ridotto: **200 ms**, quella di [FluidMotion.crossFade]. Apple col movimento ridotto sostituisce lo scorrimento con una dissolvenza (Riduci movimento, «Preferisci le dissolvenze»).
  - `const val ExpandResponseSeconds: Double` — La molla dell'altezza del testo che si espande: risposta **0,5 s**, smorzamento 1, quella delle superfici grandi ([FluidMotion.ResponseSmooth]) senza il rimbalzo: il testo che si apre nell'App Store non supera la sua altezza. A occhio il 23 novembre 2026.
  - `const val ImageArrivalMillis: Int` — Quanto ci mette un'immagine che arriva a passare dall'impronta alla foto nitida: **300 ms**, il T del contenuto che cambia identità di R7 ([GlassContentDefaults.DurationMs]). `AsyncImage` di SwiftUI non ha una transizione di serie: l'app la sceglie, e qui è quella del contenuto dell'engine.
  - `const val ImageArrivalBlurDp: Float` — σ massima dell'immagine che arriva: **6 dp**, a occhio il 23 novembre 2026 (leggera: la foto deve leggersi subito).

## `engine-ui/ui/fluid/FluidTextReveal.kt`

- `const val FluidRevealMillis` — Quanto ci mette una parola nuova a mettersi a fuoco. Dalla 4.0 (F8.1) è [FluidTextMotion.WordArrivalMillis], 260 ms: col blur chiesto da Alessio la sola alfa di 180 ms non lasciava vedere la messa a fuoco.
- `const val FluidRewriteMillis` — Quanto ci mette una parola riscritta: il riconoscitore che cambia idea su cio' che ha gia' detto, o il modello che chiude un `**`. Piu' corta, perche' al posto della parola vecchia ce n'e' gia' una nuova da leggere.
- `enum class FluidTextArrivalUnit` — In che pezzi arriva il testo.
- `data class FluidTextArrival(val unit: FluidTextArrivalUnit = FluidTextArrivalUnit.Word, val staggerMillis: Int = FluidTextMotion.WordStaggerMillis, val maxSpreadMillis: Int = FluidTextMotion.WordSpreadMillis, val blur: Boolean = true, val peakBlur: Float = FluidTextMotion.ArrivalPeakBlur)` — Come arriva il testo nuovo (F8.1, `testo-espandibile` e lo streaming di PampAI): **sfocato, che si mette a fuoco**, e non tutto insieme ma a pezzi, uno dopo l'altro. È la nota di Alessio: «il testo non appare tutto insieme ma gradualmente, e con il blur», da usare sia per il testo che si espande sia per il testo generato.
  - `companion object`
    - `val Streaming: FluidTextArrival` — Il testo generato o dettato: parola per parola, sfocato (di serie in [FluidRevealingText] e `FluidMarkdown`).
    - `val Lines: FluidTextArrival` — Il testo che compare intero o si apre: riga per riga, sfocato ([FluidExpandableText], [FluidArrivingText]).
    - `val Fade: FluidTextArrival` — La sola dissolvenza, senza sfocatura e senza passo: il testo che arrivava prima della 4.0.
- `@Composable fun FluidRevealingText(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, onTextLayout: ((TextLayoutResult) -> Unit)? = null, revealMillis: Int = FluidRevealMillis, rewriteMillis: Int = FluidRewriteMillis, softWrap: Boolean = true, animateFirst: Boolean = false, reveal: Boolean = true, arrival: FluidTextArrival = FluidTextArrival.Streaming)` — Testo che *arriva*: le parole nuove arrivano sfocate e si mettono a fuoco in [revealMillis], quelle gia' viste restano ferme. Un unico `Text`, un solo layout; la sfumatura e' una maschera `DstOut` sui glifi delle parole nuove, in un layer fuori schermo acceso solo mentre c'e' qualcosa da rivelare, e la sfocatura un fantasma del testo stesso in un livello sfocato ([FluidTextArrival]).
- `@Composable fun FluidArrivingText(text: String, modifier: Modifier = Modifier, style: TextStyle = androidx.compose.material3.MaterialTheme.typogr…, color: Color = Color.Unspecified, animateFirst: Boolean = true, arrival: FluidTextArrival = FluidTextArrival.Lines, maxLines: Int = Int.MAX_VALUE)` — Un testo che **arriva intero**, riga per riga, sfocato, e si mette a fuoco: una risposta che compare, una descrizione caricata, un testo che cambia identità. Quando [text] cambia, il nuovo arriva allo stesso modo dal primo carattere diverso in poi; quello che resta uguale non si muove.

## `engine-ui/ui/fluid/FluidThumbHash.kt`

- `class FluidThumbHashImage internal constructor(val width: Int, val height: Int, val rgba: ByteArray)` — Un'immagine piccola decodificata da un ThumbHash: [width] × [height] pixel (al più 32 per lato) in RGBA, un byte per canale, non premoltiplicati.
- `object FluidThumbHash` — ThumbHash (F8.1, `immagine-che-arriva`): l'impronta di un'immagine in **poche decine di byte**, che si decodifica in una miniatura sfocata coi colori veri, le proporzioni e la trasparenza. È il segnaposto di [FluidAsyncImage]: l'app (o il server) la calcola una volta al caricamento con [encode] e la salva accanto all'immagine.
  - `fun encode(width: Int, height: Int, rgba: ByteArray): ByteArray` — L'impronta di un'immagine di [width] × [height] pixel (al più 100 per lato: si rimpicciolisce prima, il dettaglio oltre non entra comunque nell'impronta), in RGBA non premoltiplicato, un byte per canale.
  - `fun decode(hash: ByteArray): FluidThumbHashImage` — La miniatura dell'impronta: al più 32 pixel sul lato lungo, con le proporzioni dell'immagine.
  - `fun averageColor(hash: ByteArray): Color` — Il colore medio dell'impronta: il fondo più semplice mentre l'immagine arriva.
  - `fun aspectRatio(hash: ByteArray): Float` — Le proporzioni (larghezza / altezza) dell'immagine, approssimate, lette dall'impronta.
  - `fun imageBitmap(hash: ByteArray): ImageBitmap` — L'impronta come immagine per Compose, **decodificata una volta e tenuta**: in una griglia lunga la stessa impronta torna a ogni scorrimento (la scheda dell'idea: «la decodifica delle impronte va messa in cache»). Al più 128 impronte, le meno usate escono.

## `engine-ui/ui/fluid/FluidVividCard.kt`

- `data class FluidVividColors(val start: Color, val end: Color, val content: Color)` — A saturated gradient and the one content colour readable on it.
  - `companion object`
    - `fun from(start: Color, end: Color = start): FluidVividColors`
- `enum class FluidVividEffect`
- `@Composable fun FluidVividCard(colors: FluidVividColors, modifier: Modifier = Modifier, effect: FluidVividEffect = FluidVividEffect.None, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, contextActions: (() -> List<FluidContextAction>)? = null, contentPadding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit)` — A surface that is entirely its own colour: saturated gradient, on-colour content.

## `engine-ui/ui/fluid/viewer/FluidCustomPreset.kt`

- `@Composable fun FluidCustomPreset(content: FluidViewerContent.Custom, chromeVisible: Boolean, modifier: Modifier = Modifier)` — A page drawn by a module ([FluidViewerContent.Custom]) inside the [FluidFileViewer] renderer slot. The module's [FluidViewerContent.Custom.actions] go to the viewer's bar for this page; with [FluidViewerContent.Custom.find] the bar gets «Trova», which opens the find field above the keyboard (F6B.6) and closes when the page is left or on back.

## `engine-ui/ui/fluid/viewer/FluidFileViewer.kt`

- `data class FluidViewerThumbnail(val id: Any, val namespace: FluidGlassNamespace)` — Put [fluidViewerThumbnail] before the thumbnail's drawing and clipping modifiers.
- `fun Modifier.fluidViewerThumbnail(thumbnail: FluidViewerThumbnail, shape: Shape = RectangleShape): Modifier`
- `fun NavGraphBuilder.fluidFileViewerRoute(route: String, arguments: List<NamedNavArgument> = emptyList(), deepLinks: List<NavDeepLink> = emptyList(), initial: (NavBackStackEntry) -> Int = { 0 }, thumbnail: (NavBackStackEntry, Int) -> FluidViewerThumbnail? = { _, _ -> null }, content: @Composable (NavBackStackEntry) -> Unit)` — Register the destination on a FluidNavHost. [initial] resolves the file requested by a deep link. Once a page settles, the entry remembers that page for both recreation and the return thumbnail. The source may disappear (for instance a filtered grid). Expand then uses its normal source-free exit.
- `@Composable fun FluidFileViewer(count: Int, initial: Int = LocalViewerRoute.current?.initial ?: 0, content: (index: Int) -> FluidViewerContent, toolbar: FluidToolbarScope.(index: Int) -> Unit = {}, tools: FluidViewerToolsScope.() -> Unit = {}, info: (@Composable (index: Int) -> Unit)? = null, onBack: () -> Unit = viewerBackAction(), modifier: Modifier = Modifier, onPageChange: (Int) -> Unit = {}, strings: FluidViewerStrings = FluidViewerStrings(), fileActions: @Composable (index: Int) -> FluidViewerFileActions? = { null }, renderContent: @Composable (FluidViewerContent, chromeVisible: Boolean) -> Unit = { file, visible -> ViewerBody(file, visible) })` — A full-screen file on a real route. Give the graph [fluidFileViewerRoute] for a zoom from the grid, downward dismissal and a restorable deep link. [onBack] defaults to the host's back dispatcher. [toolbar] uses the engine toolbar DSL. A tool owns the body and its own bottom actions until Done or Back. [renderContent] is the integration point for a preset or an app renderer (tiles, PDF, player, code).
- `@Composable fun FluidFileViewer(count: Int, initial: Int = LocalViewerRoute.current?.initial ?: 0, content: (index: Int) -> FluidViewerContent, toolbar: FluidToolbarScope.(index: Int) -> Unit = {}, tools: FluidViewerToolsScope.() -> Unit = {}, info: (@Composable (index: Int) -> Unit)? = null, onBack: () -> Unit = viewerBackAction(), modifier: Modifier = Modifier, onPageChange: (Int) -> Unit = {}, strings: FluidViewerStrings = FluidViewerStrings(), fileActions: @Composable (index: Int) -> FluidViewerFileActions? = { null }, renderContent: @Composable (FluidViewerContent, chromeVisible: Boolean) -> Unit = { file, visible -> ViewerBody(file, visible) }, onDismissRequest: (() -> Unit)?)` — Standalone variant. With [onDismissRequest], a downward drag or an inward pinch at 1× shrinks the media into the underlying app and calls the owner after the exit. Pass null, or use the overload without the callback, to leave standalone dismissal off. Inside a page host its route owns dismissal. [onBack] continues to handle the explicit back button. Video playback controls stay outside the zoom.

## `engine-ui/ui/fluid/viewer/FluidGridPreset.kt`

- `data class FluidGridPresetStrings(val empty: String = "Foglio vuoto", val noSheets: String = "Nessun foglio", val error: String = "Impossibile leggere le celle", val retry: String = "Riprova", val sheets: String = "Fogli", val sheet: (name: String, current: Int, count: Int) -> String = { name, current, count -> "$name, foglio $curre…, val row: (row: Int) -> String = { "Riga $it" }, val scrollEnd: String = "Colonne successive", val scrollStart: String = "Colonne precedenti", val zoomIn: String = "Ingrandisci", val zoomOut: String = "Riduci", val zoom: (percent: Int) -> String = { "Zoom $it%" }, val more: String = "Altro", val goToCell: String = "Vai alla cella…", val cell: String = "Cella", val cellHint: String = "Per esempio B12", val go: String = "Vai", val cancel: String = "Annulla", val actualSize: String = "Dimensioni reali")` — Labels of the grid preset. Numbers passed to labels are one based.
- `@Composable fun FluidGridPreset(content: FluidViewerContent.Grid, chromeVisible: Boolean, modifier: Modifier = Modifier, strings: FluidGridPresetStrings = FluidGridPresetStrings())` — A spreadsheet or a table ([FluidViewerContent.Grid]) inside the [FluidFileViewer] renderer slot: rows scroll vertically, columns sideways (the pager takes over at the edges), a pinch changes the zoom from half to four times. The frozen rows and columns of the sheet stay in place; merged cells draw once across their block.

## `engine-ui/ui/fluid/viewer/FluidImagePreset.kt`

- `@Composable fun currentFluidViewerPageActive(): Boolean` — Dentro una pagina di [FluidFileViewer] o di [dev.antigravity.fluidengine.ui.fluid.FluidImageViewer]: vero se è la pagina ferma, falso mentre il dito o il pager la portano via e per le pagine vicine. Fuori da un visore è sempre vero. Un contenuto che si muove da solo (un'animazione, un video) parte e si ferma con questo valore, come in Gallery-2 (F6C.0 §1).
- `@Composable fun currentFluidViewerZoom(): FluidZoomState?` — Dentro una pagina immagine o video del visore: lo zoom che il visore le applica, null altrove. Un visore a tasselli dell'app (Gallery-2, fino a 200 MP) passato come [FluidViewerContent.Image] legge qui [FluidZoomState.scale], [FluidZoomState.panX] e [FluidZoomState.panY] per scegliere la risoluzione e i tasselli da caricare; i gesti, i limiti dello spostamento e la chrome restano del visore (piano F6C, «Rischi»).
- `@Composable fun FluidImagePreset(file: FluidViewerContent.Image)` — Il preset immagine dentro il telaio: disegna il contenuto dell'app. Lo zoom, il pager, il congedo e il ritorno alla miniatura sono del telaio, che li applica attorno a questo corpo.
- `data class FluidImagePresetStrings(val play: String = "Riproduci animazione", val pause: String = "Metti in pausa l'animazione")` — Le parole del preset immagine.
- `fun fluidAnimatedImage(drawable: Drawable, description: String? = null, still: Painter? = null, aspectRatio: Float? = drawableAspectRatio(drawable) ?: still?.intrins…, strings: FluidImagePresetStrings = FluidImagePresetStrings()): FluidViewerContent.Image` — Un'immagine animata (GIF, WebP animata, l'animazione dell'app) come contenuto del visore. `engine-ui` non decodifica: [drawable] arriva già pronto, da `engine-viewer` (F6C.4, `ImageDecoder` e `AnimatedImageDrawable`) o dall'app (Coil e Glide danno drawable che implementano [Animatable]). Un drawable che non è [Animatable] resta un'immagine ferma.
- `@Composable fun FluidAnimatedImage(drawable: Drawable, modifier: Modifier = Modifier, description: String? = null, still: Painter? = null, strings: FluidImagePresetStrings = FluidImagePresetStrings())` — Il corpo di [fluidAnimatedImage], per chi disegna la pagina da sé: dentro una pagina di [dev.antigravity.fluidengine.ui.fluid.FluidImageViewer] o in un `renderContent` del telaio. Riempie il contenitore e adatta l'immagine.

## `engine-ui/ui/fluid/viewer/FluidMediaPreset.kt`

- `data class FluidMediaPresetStrings(val play: String = "Riproduci", val pause: String = "Pausa", val position: String = "Posizione di riproduzione", val skipBack: String = "Indietro di 15 secondi", val skipForward: String = "Avanti di 15 secondi", val unknownDuration: String = "Durata sconosciuta", val time: (elapsed: String, total: String) -> String = { elapsed, total -> "$elapsed di $total" }, val skipSeconds: Int = 15, val chapters: String = "Capitoli", val currentChapter: (Int, Int) -> String = { index, count -> "Capitolo $index di $count" }, val speed: String = "Velocità", val output: String = "Uscita audio", val more: String = "Altro", val sleepTimer: String = "Timer di spegnimento", val sleepOff: String = "Spento", val sleepMinutes: (minutes: Int) -> String = { if (it == 60) "1 ora" else "$it minuti" }, val sleepEndOfTrack: String = "Fine del brano")` — Labels for the viewer's single set of playback controls. The time arguments are already formatted.
- `@Composable fun FluidMediaPreset(content: FluidViewerContent.Media, chromeVisible: Boolean, modifier: Modifier = Modifier, sampleVideo: Boolean = content.sampleVideo, strings: FluidMediaPresetStrings = FluidMediaPresetStrings())` — Video or audio controls for the [FluidFileViewer] renderer slot. Pass its `chromeVisible` unchanged so a tap on the file remains the sole toggle. Play, pause and seek operate on the owner's session, which also controls autoplay, errors, and release. The supplied video surface should have its own controls disabled.

## `engine-ui/ui/fluid/viewer/FluidPagesPreset.kt`

- `data class FluidPagesPresetStrings(val counter: (current: Int, count: Int) -> String = { current, count -> "$current di $count" }, val page: (current: Int, count: Int) -> String = { current, count -> "Pagina $current di $count"…, val thumbnails: String = "Miniature delle pagine", val empty: String = "Nessuna pagina", val loading: String = "Caricamento pagina", val error: String = "Impossibile mostrare la pagina", val retry: String = "Riprova", val zoomIn: String = "Ingrandisci", val zoomOut: String = "Riduci", val zoom: (percent: Int) -> String = { "Zoom $it%" }, val find: String = "Trova", val searchError: String = "Impossibile cercare nel documento", val present: String = "Presenta", val endPresentation: String = "Chiudi presentazione", val more: String = "Altro", val goToPage: String = "Vai a pagina…", val goToSlide: String = "Vai alla slide…", val pageNumber: String = "Numero di pagina", val slideNumber: String = "Numero della slide", val range: (count: Int) -> String = { "Da 1 a $it" }, val go: String = "Vai", val cancel: String = "Annulla", val pageThumbnails: String = "Miniature", val speakerNotes: String = "Note del relatore", val slideNotes: (current: Int, count: Int) -> String = { current, count -> "Slide $current di $count" }, val noNotes: String = "Nessuna nota per questa slide", val fitWidth: String = "Adatta alla larghezza")` — Labels for a document's pages and the available reading actions. Numbers passed to labels are one based.
- `@Composable fun FluidPagesPreset(content: FluidViewerContent.Pages, chromeVisible: Boolean, modifier: Modifier = Modifier, strings: FluidPagesPresetStrings = FluidPagesPresetStrings())` — A continuous vertical reader for the [FluidFileViewer] renderer slot. A pinch changes the width of the whole column, preserving its place in the vertical list. Open the thumbnail sheet to jump directly to a page. The source is borrowed for as long as this preset is composed. Use a new source when the document changes.

## `engine-ui/ui/fluid/viewer/FluidTextPreset.kt`

- `interface FluidStyledTextSource : FluidTextSource` — Una sorgente di testo che dà anche i colori (la sintassi di poche lingue, in `engine-viewer` di F6C.4, o i colori dell'app). Il preset la preferisce a [readLines]; il contratto è lo stesso: righe da [start], al più [count], e un pezzo corto segna la fine del file.
  - `suspend fun readStyledLines(start: Int, count: Int): List<AnnotatedString>`
- `data class FluidTextPresetStrings(val find: String = "Trova", val wrap: String = "A capo", val loading: String = "Caricamento", val failed: String = "Impossibile leggere il file", val retry: String = "Riprova", val empty: String = "Il file è vuoto", val textSize: String = "Dimensione del testo", val more: String = "Altro", val goToLine: String = "Vai alla riga…", val lineNumber: String = "Numero di riga", val lineRange: (count: Int?) -> String = { count -> if (count == null) "Il numero della…, val go: String = "Vai", val cancel: String = "Annulla", val lineNumbers: String = "Numeri di riga", val copyText: String = "Copia il testo")` — Le parole del preset testo, in italiano di serie.
- `object FluidTextPresetDefaults` — I numeri del preset testo. Sono quantità di lettura, non di movimento.
  - `const val ChunkLines: Int` — Righe per lettura: circa 20–40 KB di codice, una lettura ogni qualche schermata di scorrimento. A occhio, 22/11/2026.
  - `const val CopyTextMaxChars: Int` — F6C.20 · Fin dove «Copia il testo» mette tutto il file negli appunti: gli appunti di Android passano dal binder, che tiene 1 MB per transazione (`TransactionTooLargeException`), e un carattere ne pesa due. Oltre, la voce non c'è. A occhio, 23 novembre 2026.
  - `const val PrefetchLines: Int` — La lettura successiva parte quando alla fine delle righe lette mancano queste righe. A occhio, 22/11/2026.
  - `const val SegmentChars: Int` — Una riga più lunga si spezza in pezzi di questa misura, uno per riga della lista: un JSON minimizzato di 5 MB su una riga sola non impagina 5 MB in un `Text`. Il numero di riga sta sul primo pezzo. A occhio, 22/11/2026.
  - `const val SyntaxAnnotation: String` — Il tag delle annotazioni di sintassi di [FluidStyledTextSource].
  - `val SyntaxRoles: List<String>` — I ruoli che il preset sa colorare; un altro valore resta senza colore.
  - `@Composable fun syntaxStyles(): Map<String, SpanStyle>` — Gli stili dei ruoli, dal tema: le parole chiave e i tag nell'accento, i tipi e i numeri nel terziario, le stringhe e gli attributi nel secondario, i commenti e la punteggiatura nell'inchiostro attenuato. A occhio, 22/11/2026.
- `@Composable fun FluidTextPreset(file: FluidViewerContent.Text, modifier: Modifier = Modifier, strings: FluidTextPresetStrings = FluidTextPresetStrings(), initialWrap: Boolean = file.source.language == null)` — Il preset testo e codice del visore: il file di [file] in monospazio, con i numeri di riga.

## `engine-ui/ui/fluid/viewer/FluidUnknownPreset.kt`

- `data class FluidUnknownPresetStrings(val openWith: String = "Apri con…", val share: String = "Condividi", val unknownSize: String = "Dimensione sconosciuta", val emptyFile: String = "File vuoto", val noPreview: String = "Nessuna anteprima per questo tipo di file", val executableWarning: String = "È un'app o un programma: aprilo solo se ti fid…, val openFailed: String = "Nessuna app può aprire questo file", val shareFailed: String = "Il file non si può condividere", val showBytes: String = "Mostra i primi byte", val hideBytes: String = "Nascondi i byte", val bytesFailed: String = "I byte non si possono leggere", val androidPackage: String = "Pacchetto Android", val executableType: String = "File eseguibile", val apkWarning: String = "È un'app da installare. Aprila solo se ti fidi…)` — Le parole del preset sconosciuto, in italiano di serie.
- `object FluidUnknownPresetDefaults` — I numeri del preset sconosciuto.
  - `const val HexDumpBytes: Int` — Quanti byte mostra il dump: il primo KB, come Universal Converter (F6C.0 §6).
  - `const val HexRowBytes: Int` — Byte per riga del dump: otto stanno in monospazio su un telefono da 360 dp. A occhio, 22/11/2026.
- `@Composable fun FluidUnknownPreset(file: FluidViewerContent.Unknown, modifier: Modifier = Modifier, strings: FluidUnknownPresetStrings = FluidUnknownPresetStrings(), firstBytes: (suspend () -> ByteArray)? = null)` — Il preset del file che il visore non sa mostrare: una scheda, mai un vicolo cieco.

## `engine-ui/ui/fluid/viewer/FluidViewerChrome.kt`

- `data class FluidViewerStrings(val counter: (current: Int, count: Int) -> String = { current, count -> "$current di $count" }, val info: String = "Informazioni", val done: String = "Fine", val empty: String = "Nessun file", val showControls: String = "Mostra controlli", val hideControls: String = "Nascondi controlli", val close: String = "Chiudi", val copy: String = "Copia valore", val file: String = "File", val name: String = "Nome", val type: String = "Tipo", val size: String = "Dimensione", val openWith: String = "Apri con…", val share: String = "Condividi", val actionFailed: String = "Impossibile completare l’azione", val pages: (Int) -> String = { "$it pagine" }, val lines: (Int) -> String = { "$it righe" }, val location: String = "Luogo", val coordinates: String = "Coordinate", val openMap: String = "Apri in Mappe", val chapters: String = "Capitoli", val more: String = "Altro", val sheets: (Int) -> String = { "$it fogli" }, val copyFile: String = "Copia", val print: String = "Stampa", val rename: String = "Rinomina", val move: String = "Sposta", val duplicate: String = "Duplica", val delete: String = "Elimina", val fileMenu: String = "Mostra le azioni del file")`
- `class FluidViewerToolsScope internal constructor()`
  - `fun tool(id: String, icon: ImageVector, label: String, actions: FluidToolbarScope.(index: Int) -> Unit = {}, content: @Composable (index: Int) -> Unit)` — Stable [id] restores the editor after recreation. Its actions share one capsule with Done.

## `engine-ui/ui/fluid/viewer/FluidViewerContent.kt`

- `data class FluidFileInfo(val name: String, val mimeType: String? = null, val sizeBytes: Long? = null, val kind: FluidFileKind = FluidFileKind.from(mimeType, name), val modifiedAtMillis: Long? = null)` — Metadata supplied by the owner of the file. A null size is unknown, zero means empty.
- `interface FluidMediaSession` — A player owned by the app. Publish changing properties as Compose state. Times are milliseconds.
  - `val playing: Boolean`
  - `val positionMillis: Long`
  - `val durationMillis: Long?`
  - `val canSeek: Boolean`
  - `val error: String? get()`
  - `fun play()`
  - `fun pause()`
  - `fun seekTo(positionMillis: Long)`
- `interface FluidAudioSpeed` — Optional audio transport capability. Publish changes as Compose state.
  - `val playbackSpeed: Float`
  - `val canChangePlaybackSpeed: Boolean`
  - `fun changePlaybackSpeed(speed: Float)`
- `interface FluidVideoGeometry` — Optional dimensions for fitting a video surface and bounding its zoom and pan.
  - `val videoAspectRatio: Float?`
- `interface FluidPageSource` — Zero-based pages. Both suspending operations must be safe to call on the main dispatcher.
  - `val count: Int`
  - `suspend fun size(index: Int): IntSize`
  - `suspend fun render(index: Int, target: IntSize): ImageBitmap`
  - `val countFinal: Boolean get()` — False while [count] can still grow (a document being laid out). Publish it as Compose state.
  - `suspend fun sheet(index: Int): FluidPageSheet` — The sheet of page [index]: its exact measure, its margins and the paper it is printed on. Every section of a document may change sheet and orientation. The default is [size] on white paper, without margins (a PDF).
- `data class FluidPageSheet(val width: Float, val height: Float, val margins: FluidPageMargins = FluidPageMargins.None, val paper: Color = Color.White)` — The sheet of a page, as the document declares it: [width] and [height] in points (1/72 inch, 72 dpi), the [margins] in points and the [paper] colour behind the ink (also the colour to show while the page renders).
  - `val aspectRatio: Float get()` — Width over height, the only number the page preset needs.
  - `companion object`
    - `val A4: FluidPageSheet` — ISO A4, 210 × 297 mm.
    - `val Letter: FluidPageSheet` — US Letter, 8.5 × 11 in.
- `data class FluidPageMargins(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f)` — The margins of a [FluidPageSheet] in points, on the physical sides of the paper.
  - `companion object`
    - `val None: FluidPageMargins`
- `interface FluidTextSource` — Read at most [count] lines from a zero-based offset. A short chunk marks the end of the file.
  - `val language: String? get()`
  - `val lineCount: Int? get()`
  - `suspend fun readLines(start: Int, count: Int): List<String>`
- `interface FluidGridSource` — F6C.8 · A table of cells in one or more sheets: a spreadsheet (XLSX, ODS) from the Office family, a CSV or TSV from the data family. Rows and columns are zero based. [sheets] may grow while the file is read: publish it as Compose state. [cells] is safe to call on the main dispatcher; it reads a block and returns only the cells that have something to show (text, fill or borders), in any order.
  - `val sheets: List<FluidGridSheet>`
  - `suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange): List<FluidGridCell>`
- `data class FluidGridSheet(val name: String, val rowCount: Int, val columnCount: Int, val frozenRows: Int = 0, val frozenColumns: Int = 0, val columnWidth: Float = DefaultColumnWidth, val rowHeight: Float = DefaultRowHeight, val columnWidths: Map<Int, Float> = emptyMap(), val rowHeights: Map<Int, Float> = emptyMap(), val merges: List<FluidGridRange> = emptyList(), val headers: Boolean = true, val countFinal: Boolean = true, val color: Color? = null)` — A sheet of a [FluidGridSource]. Lengths are dp at 100% zoom (a spreadsheet's pixels at 100%): [columnWidth] and [rowHeight] by default, [columnWidths] and [rowHeights] where a column or a row differs; zero hides it. The first [frozenRows] and [frozenColumns] stay in place while the rest scrolls. [headers] shows the column letters and the row numbers of a spreadsheet;
  - `companion object`
    - `const val DefaultColumnWidth: Float` — 80 × 24 dp: a spreadsheet's default column and row at 100% with room for a finger. A occhio, 22 novembre 2026.
    - `const val DefaultRowHeight: Float`
- `data class FluidGridRange(val firstRow: Int, val firstColumn: Int, val lastRow: Int, val lastColumn: Int)` — A block of cells, both ends included: a merged cell, or a selection.
  - `operator fun contains(cell: Pair<Int, Int>): Boolean`
- `data class FluidGridCell(val row: Int, val column: Int, val text: String, val style: FluidGridStyle = FluidGridStyle.Default, val numeric: Boolean = false)` — One cell as it shows: [text] is the value already formatted by the family (the saved result of a formula, a date in its format). [numeric] aligns it to the end when [FluidGridStyle.horizontal] is null, as spreadsheets do.
- `data class FluidGridStyle(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val strikethrough: Boolean = false, val color: Color = Color.Unspecified, val fill: Color = Color.Unspecified, val horizontal: Alignment.Horizontal? = null, val vertical: Alignment.Vertical = Alignment.Bottom, val wrap: Boolean = false, val fontScale: Float = 1f, val borders: FluidGridBorders = FluidGridBorders.None)` — How a cell looks. Unspecified colours take the theme's (ink, no fill, no border). [horizontal] null is automatic: numbers at the end, text at the start. [fontScale] multiplies the grid's text size.
  - `companion object`
    - `val Default: FluidGridStyle`
- `data class FluidGridBorders(val top: Color = Color.Unspecified, val bottom: Color = Color.Unspecified, val left: Color = Color.Unspecified, val right: Color = Color.Unspecified)` — The lines around a cell, one colour per side; unspecified draws only the grid's own hairline.
  - `companion object`
    - `val None: FluidGridBorders`
- `value class FluidViewerBackground private constructor(private val id: Int)` — The page colour of a [FluidViewerContent.Custom]: the theme's surface for documents and data, or black, as photos and videos (scelta di Alessio del 21 novembre). Not an `enum` on purpose (rule 3.11): another value is a constant.
  - `companion object`
    - `val Theme: FluidViewerBackground`
    - `val Black: FluidViewerBackground`
- `interface FluidViewerFind` — «Trova» on a [FluidViewerContent.Custom] page (F6B.6). The frame shows the button and the field above the keyboard; the page searches its own content and highlights the [navigation]'s current match. Publish [query] and [navigation] as Compose state.
  - `val query: String`
  - `fun onQueryChange(query: String)`
  - `val navigation: FluidSearchNavigation`
  - `val label: String get()` — The button's label.
- `interface FluidViewerCustomScope` — What the frame tells a [FluidViewerContent.Custom] page while it draws.
  - `val chromeVisible: Boolean` — False after a tap has hidden the bars.
  - `val finding: Boolean` — True while «Trova» is open on this page.
  - `val active: Boolean` — True on the settled page; false while the pager or a finger carries it away, and on the pages next to it.
  - `val contentPadding: PaddingValues` — Below the top bar and above the bottom capsule (and above the keyboard while finding): where content can rest.
- `interface FluidViewerContent` — An open contract so applications and future presets may supply another representation. The frame owns geometry and gestures. A renderer can read the sources without owning their lifecycle.
  - `class Image(val aspectRatio: Float? = null, val draw: @Composable () -> Unit) : FluidViewerContent`
  - `class Media(val session: FluidMediaSession, val sampleVideo: Boolean, val videoSurface: (@Composable () -> Unit)? = null, val artwork: (@Composable () -> Unit)? = null) : FluidViewerContent` — A non-null video surface identifies a video, even while it is buffering.
  - `class Pages(val source: FluidPageSource) : FluidViewerContent`
  - `class Text(val source: FluidTextSource) : FluidViewerContent`
  - `class Unknown(val info: FluidFileInfo, val openWith: (() -> Unit)? = null, val share: (() -> Unit)? = null, val icon: ImageBitmap? = null) : FluidViewerContent`
  - `class Grid(val source: FluidGridSource) : FluidViewerContent` — F6C.8 · A spreadsheet or a table: sheets of cells, frozen rows and columns, merges ([FluidGridSource]).
  - `class Custom(val background: FluidViewerBackground = FluidViewerBackground.Theme, val find: FluidViewerFind? = null, val actions: FluidToolbarScope.() -> Unit = {}, val draw: @Composable FluidViewerCustomScope.() -> Unit, internal val firstFrameReady: (() -> Boolean)? = null) : FluidViewerContent` — F6C.8 · A page that a module draws itself with the engine's public components (an archive's list, a JSON tree, a book, a mail, a font, a 3D model). It declares its [background], whether it has «Trova» ([find]) and the [actions] it adds to the viewer's bar («Estrai tutto», «Albero | Testo», «Presenta»). The frame keeps the chrome, the pager, the info panel and dismissal;

## `engine-ui/ui/fluid/viewer/FluidViewerFileMenu.kt`

- `data class FluidViewerFileActions(val share: (() -> Unit)? = null, val openWith: (() -> Unit)? = null, val copy: (() -> Unit)? = null, val print: (() -> Unit)? = null, val rename: (() -> Unit)? = null, val move: (() -> Unit)? = null, val duplicate: (() -> Unit)? = null, val delete: (() -> Unit)? = null, val more: List<FluidViewerInfoAction> = emptyList())` — F6C.19 · **Il menu del file.** In cima al visore il titolo è il nome del file senza estensione con «⌄» (il titolo selettore di F6B.11), e apre questo menu, come in File e in Quick Look di iOS 26 (il fermo `ql-pdf-menu.png`, la tavola «D · Mix»). Una voce c'è solo se la sua azione non è null.

## `engine-ui/ui/fluid/viewer/FluidViewerInfo.kt`

- `interface FluidMediaDurationLoader` — Optional player capability. Publish the result through durationMillis, without starting playback.
  - `fun requestDuration()`
- `data class FluidViewerInfoRow(val label: String, val value: String, val copyValue: String = value)`
- `data class FluidViewerInfoSection(val title: String, val rows: List<FluidViewerInfoRow>, val content: (@Composable () -> Unit)? = null)`
- `data class FluidAudioChapter(val title: String, val startMillis: Long)`
- `data class FluidViewerInfoAction(val label: String, val onClick: () -> Unit)`
- `data class FluidViewerLocation(val coordinates: String, val map: (@Composable () -> Unit)? = null, val openMap: (() -> Unit)? = null)` — Coordinates stay copyable when the app supplies a map. Navigation belongs to the app.
- `data class FluidViewerInfo(val file: FluidFileInfo, val title: String = file.name, val subtitle: String? = null, val thumbnail: ImageBitmap? = null, val facts: List<String> = emptyList(), val sections: List<FluidViewerInfoSection> = emptyList(), val actions: List<FluidViewerInfoAction> = emptyList(), val audioWaveform: (@Composable () -> Unit)? = null, val location: FluidViewerLocation? = null, val reading: FluidViewerInfoSection? = null, val searchPages: (suspend (String) -> List<FluidViewerPageMatch>)? = null, val chapters: List<FluidAudioChapter> = emptyList(), val audioFormat: String? = null, val audioOutput: (() -> Unit)? = null)` — Metadata from the owner. A location section can contain coordinates and, optionally, the app's map. Supply a small [thumbnail] of the file, its cover or its application icon.
- `data class FluidViewerPageMatch(val page: Int, val bounds: List<androidx.compose.ui.geometry.Rect>)` — Each rectangle is in page-relative coordinates (0..1), with the origin at the top left.
- `@Composable fun FluidViewerInfoProvider(info: @Composable (Int) -> FluidViewerInfo?, content: @Composable () -> Unit)` — Associates rich metadata with pager indices, including when adjacent pages are composed together.

## `engine-ui/ui/fluid/viewer/FluidViewerTypeActions.kt`

- `fun FluidToolbarScope.viewerTypeActions(label: String = "Altro", menu: () -> List<FluidContextAction>)` — F6C.19 · **«•••» della pagina: le azioni del tipo** (scelta di Alessio del 23 novembre: «•••» c'è sempre, come nella tavola «D · Mix», e tiene le azioni proprie del tipo, come Cerca e Annotazioni in Quick Look). Le azioni del file (Condividi, Apri con…, Informazioni…) stanno nel menu del nome in cima: il visore toglie da qui le voci che il nome ha già, così niente è doppio.
- `interface FluidPageNotes` — F6C.19 · Una sorgente di pagine che conosce le **note del relatore** delle sue slide (PPTX e ODP del modulo Office). Il preset delle pagine la riconosce e mette «Note del relatore» in «•••». È una capacità facoltativa, come [FluidMediaDurationLoader] per un player.
  - `fun notes(index: Int): String?` — Le note della pagina [index] (da zero), `null` se non ne ha.
  - `val hasNotes: Boolean` — Vero se almeno una pagina ha le note. Pubblicalo come stato di Compose se arriva dopo.
