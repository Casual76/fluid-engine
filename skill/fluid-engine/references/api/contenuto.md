# API · Contenuto: righe, card, liste, testo

_Generato sull'engine 3.0.5 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidContent.kt`

- `@Composable fun FluidSectionHeader(title: String, modifier: Modifier = Modifier, detail: String? = null)` — Header above a grouped list.
- `@Composable fun FluidLoadingBlock(modifier: Modifier = Modifier, minHeight: Dp = 180.dp)` — Placeholder while a screen's first load is in flight.
- `@Composable fun FluidSectionFootnote(text: String, modifier: Modifier = Modifier)` — Footnote under a grouped list, for the explanation a row's subtitle would make too long.

## `engine-ui/ui/fluid/FluidGlassGroups.kt`

- `const val FluidGlassGroupMaxRows` — Quante righe stanno in un pannello di vetro prima che ne cominci un altro. Vedi [fluidGlassGroups].
- `fun <T> LazyListScope.fluidGlassGroups(items: List<T>, key: String? = null, maxRows: Int = FluidGlassGroupMaxRows, row: @Composable (T) -> Unit)` — Una sezione di righe, dentro una lista pigra, come pannelli di vetro.
- `fun <T> LazyListScope.fluidGlassGroupsIndexed(items: List<T>, key: String? = null, maxRows: Int = FluidGlassGroupMaxRows, row: @Composable (index: Int, item: T) -> Unit)` — [fluidGlassGroups] con la posizione della riga nella lista data (3.0.1), come `itemsIndexed`: per il numero di una classifica, una riga che si comporta diversamente se e' la prima, un'animazione sfalsata. L'indice e' quello in [items], non quello nel pannello: il taglio in pannelli resta invisibile anche qui.
- `@Composable fun <T> FluidGlassGroup(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit)` — Le stesse righe in un pannello solo, fuori da una lista pigra: per una sezione breve che sta in una colonna ([fluidColumns]) accanto ad altre.

## `engine-ui/ui/fluid/FluidHero.kt`

- `enum class FluidHeroTone` — Dove una schermata si colloca nella famiglia cromatica dell'app.
- `enum class FluidHeroMotif` — Il motivo astratto disegnato dietro l'intestazione.
- `data class FluidHeroMetric(val label: String, val value: String, val onClick: (() -> Unit)? = null)`
- `@Composable fun FluidHero(tone: FluidHeroTone, motif: FluidHeroMotif, eyebrow: String, value: String, title: String, description: String, icon: ImageVector, modifier: Modifier = Modifier, metrics: List<FluidHeroMetric> = emptyList(), urgent: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null)` — L'apertura editoriale di una schermata piena di dati.
- `@Composable fun FluidHeroBand(tone: FluidHeroTone, motif: FluidHeroMotif, eyebrow: String, value: String, label: String, icon: ImageVector, modifier: Modifier = Modifier, urgent: Boolean = false, trailing: (@Composable RowScope.() -> Unit)? = null)` — L'apertura di una schermata quando lo spazio conta: una fascia, non un pannello.

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

## `engine-ui/ui/fluid/FluidLiveDot.kt`

- `object FluidLiveDotDefaults` — Le misure del [FluidLiveDot].
  - `val Size: Dp` — Il diametro: 7 dp, accanto a un testo da 13-17 sp il pallino si legge senza farsi guardare.
- `@Composable fun FluidLiveDot(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, size: Dp = FluidLiveDotDefaults.Size, pulsing: Boolean = true, contentDescription: String? = null)` — Il pallino che pulsa accanto a un dato vivo: i minuti di un bus seguito dal feed, «in diretta», un lettore che sta registrando.

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

## `engine-ui/ui/fluid/FluidTextReveal.kt`

- `const val FluidRevealMillis` — Quanto ci mette una parola nuova a sfumare dentro.
- `const val FluidRewriteMillis` — Quanto ci mette una parola riscritta: il riconoscitore che cambia idea su cio' che ha gia' detto, o il modello che chiude un `**`. Piu' corta, perche' al posto della parola vecchia ce n'e' gia' una nuova da leggere.
- `@Composable fun FluidRevealingText(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip, onTextLayout: ((TextLayoutResult) -> Unit)? = null, revealMillis: Int = FluidRevealMillis, rewriteMillis: Int = FluidRewriteMillis, softWrap: Boolean = true, animateFirst: Boolean = false, reveal: Boolean = true)` — Testo che *arriva*: le parole nuove sfumano dentro in [revealMillis], quelle gia' viste restano ferme. Un unico `Text`, un solo layout; la sfumatura e' una maschera `DstOut` sui glifi delle parole nuove, in un layer fuori schermo acceso solo mentre c'e' qualcosa da rivelare.

## `engine-ui/ui/fluid/FluidVividCard.kt`

- `data class FluidVividColors(val start: Color, val end: Color, val content: Color)` — A saturated gradient and the one content colour readable on it.
  - `companion object`
    - `fun from(start: Color, end: Color = start): FluidVividColors`
- `enum class FluidVividEffect`
- `@Composable fun FluidVividCard(colors: FluidVividColors, modifier: Modifier = Modifier, effect: FluidVividEffect = FluidVividEffect.None, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, contextActions: (() -> List<FluidContextAction>)? = null, contentPadding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit)` — A surface that is entirely its own colour: saturated gradient, on-colour content.
