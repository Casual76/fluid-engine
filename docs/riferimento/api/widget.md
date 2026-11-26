<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/widget.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-widget

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-widget/widget/EngineWidgetComponents.kt`

- `object EngineWidgetShape` — The radii the design system uses, in the sizes a home screen needs.
  - `val Container`
  - `val Group`
  - `val Tile`
  - `val Pill`
- `@Composable fun EngineWidgetSurface(palette: EngineWidgetPalette, layout: EngineWidgetLayout, onClick: Action? = null, content: @Composable ColumnScope.() -> Unit)` — The widget's outer surface: the app's background, rounded, with one tap target behind everything.
- `@Composable fun EngineWidgetHeader(title: String, palette: EngineWidgetPalette, layout: EngineWidgetLayout, subtitle: String? = null, subtitleIsWarning: Boolean = false, trailing: (@Composable () -> Unit)? = null)` — Title, an optional status line, and one trailing action.
- `@Composable fun EngineWidgetActionButton(@DrawableRes icon: Int, contentDescription: String, action: Action, palette: EngineWidgetPalette, layout: EngineWidgetLayout)` — A round, tinted glyph button — the widget's equivalent of a bar button item.
- `@Composable fun EngineWidgetGroup(palette: EngineWidgetPalette, content: @Composable ColumnScope.() -> Unit)` — Rows sharing one rounded card, the way an inset grouped list does.
- `@Composable fun EngineWidgetRow(title: String, palette: EngineWidgetPalette, layout: EngineWidgetLayout, @DrawableRes icon: Int? = null, tone: EngineWidgetTone = palette.neutralTone, subtitle: String? = null, trailing: String? = null, onClick: Action? = null)` — One row of a group: a tinted icon tile, one or two lines of text, and a trailing value.
- `@Composable fun EngineWidgetHairline(palette: EngineWidgetPalette, layout: EngineWidgetLayout)` — The separator between two rows, inset to where the row's text starts.
- `@Composable fun EngineWidgetIconTile(@DrawableRes icon: Int, tone: EngineWidgetTone, compact: Boolean = false)`
- `@Composable fun EngineWidgetPill(value: String, label: String, tone: EngineWidgetTone, modifier: GlanceModifier = GlanceModifier, compact: Boolean = false)` — A count and what it counts.
- `@Composable fun EngineWidgetMessage(message: String, palette: EngineWidgetPalette, layout: EngineWidgetLayout, @DrawableRes icon: Int? = null, tone: EngineWidgetTone = palette.neutralTone, detail: String? = null)` — The card a widget shows when it has nothing to list.
- `fun engineWidgetTextStyle(color: ColorProvider, size: TextUnit, weight: FontWeight = FontWeight.Normal): TextStyle` — Glance text has no typography scale to inherit, so the sizes are stated at each call site.

## `engine-widget/widget/EngineWidgetGauge.kt`

- `@Composable fun EngineWidgetGauge(value: Float, range: ClosedFloatingPointRange<Float>, palette: EngineWidgetPalette, layout: EngineWidgetLayout, valueText: String, modifier: GlanceModifier = GlanceModifier, style: FluidGaugeStyle = FluidGaugeStyle.Ring, label: String? = null, minText: String = "", maxText: String = "", thresholds: List<FluidGaugeThreshold> = emptyList(), thresholdTexts: List<String> = thresholds.map { engineWidgetPlainNumber(it.val…, tone: ColorProvider = palette.accent, size: Dp = if (style == FluidGaugeStyle.Ring) EngineWidget…, barWidth: Dp = EngineWidgetGaugeMetrics.BarWidth, labels: FluidDataLabels = FluidDataLabels())` — The gauge on the home screen (4.0, F8.6): `FluidGauge` for Glance, with the same reading, the same shapes and the same sentence for TalkBack — the arc open at the bottom with the number in it, the small ring, the capacity bar.
- `object EngineWidgetGaugeMetrics` — The sizes of the widget gauge.
  - `val RingSize: Dp` — A ring that sits in a widget row (34–48 dp) with its margin.
  - `val ArcSize: Dp` — The large arc, for a 2×2 cell.
  - `val BarHeight: Dp` — The capacity bar's height: thicker than in the app (6 dp), as the Batteries widget's bar is at arm's length.
  - `val BarWidth: Dp` — The default bar width: a 2×1 cell minus its padding.
  - `const val MaxBitmapScale: Float` — The bitmap scale cap. Masks are drawn at the screen density up to this, then scaled by the host: a 120 dp arc at 3× is 360 px a side (half a megabyte a mask), and a widget's bitmaps share one budget with the whole RemoteViews.

## `engine-widget/widget/EngineWidgetLayout.kt`

- `data class EngineWidgetLayout(val padding: Dp, val compact: Boolean, val rowLimit: Int, val showSubtitle: Boolean, val showFooter: Boolean)` — How much of a list fits in the cell the launcher handed over.
- `object EngineWidgetMetrics`
  - `val RowHeight: Dp` — A two-line row: a 15sp title over a 12sp subtitle, plus the padding around them.
  - `val CompactRowHeight: Dp` — One line and a trailing value, for a cell too short to give a row two of them.
  - `val HeaderHeight: Dp`
  - `val CompactHeaderHeight: Dp`
  - `val FooterHeight: Dp`
  - `val Gap: Dp`
  - `val Padding: Dp`
  - `val CompactPadding: Dp`
  - `const val MaxRows: Int` — Beyond four rows a home-screen widget stops being glanceable and becomes a bad list.
- `fun resolveEngineWidgetLayout(size: DpSize, hasFooter: Boolean = false, maxRows: Int = EngineWidgetMetrics.MaxRows): EngineWidgetLayout` — [hasFooter] is asked for rather than assumed: a footer that has nothing to say should not be drawn, and the row budget should get its space back instead of leaving a gap.

## `engine-widget/widget/EngineWidgetPalette.kt`

- `data class EngineWidgetTone(val container: ColorProvider, val content: ColorProvider)` — A background paired with the content colour that stays readable on it.
- `data class EngineWidgetPalette(val background: ColorProvider, val card: ColorProvider, val hairline: ColorProvider, val onSurface: ColorProvider, val onSurfaceVariant: ColorProvider, val accent: ColorProvider, val accentContainer: ColorProvider, val onAccentContainer: ColorProvider, val attention: ColorProvider, val primaryTone: EngineWidgetTone, val secondaryTone: EngineWidgetTone, val warningTone: EngineWidgetTone, val dangerTone: EngineWidgetTone, val successTone: EngineWidgetTone, val neutralTone: EngineWidgetTone)` — Every colour a widget draws, resolved for both launcher themes in advance.
- `fun engineWidgetPalette(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand): EngineWidgetPalette` — Builds a widget palette from the app's own appearance settings.
- `fun engineWidgetIsDark(settings: EngineSettings, launcherIsDark: Boolean): Boolean` — Whether a widget is dark when the launcher is [launcherIsDark]: **the rule**, public since 3.0.1.
- `fun engineWidgetColorScheme(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand, launcherIsDark: Boolean): ColorScheme` — The full Material scheme a widget uses for one launcher theme (public since 3.0.1, it was the private `widgetScheme`): the app's own palette — accent, dynamic colour, AMOLED — resolved with [engineWidgetIsDark]. For a widget drawn with something other than Glance that wants a role the palette does not carry; most want [engineWidgetArgb].
- `data class EngineWidgetArgb(val background: Int, val card: Int, val hairline: Int, val onSurface: Int, val onSurfaceVariant: Int, val accent: Int, val accentContainer: Int, val onAccentContainer: Int, val attention: Int, val warning: Int, val success: Int, val isDark: Boolean)` — The palette of a widget as plain ARGB ints, for one launcher theme (3.0.1): what a `RemoteViews` widget passes to `setTextColor`, `setInt(id, "setColorFilter", …)` or, from Android 12, to `setColorInt(id, method, day, night)` with the two sets ([launcherIsDark] false and true).
- `fun engineWidgetArgb(context: Context, settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand, launcherIsDark: Boolean): EngineWidgetArgb` — [EngineWidgetArgb] for the launcher theme [launcherIsDark]: see there.

## `engine-widget/widget/FluidQuickTile.kt`

- `data class FluidQuickTileOffer(val service: ComponentName, val label: String, val description: String, @DrawableRes val icon: Int)` — Un riquadro delle Impostazioni rapide dell'app, da proporre **dalla pagina delle impostazioni dell'app** (`StatusBarManager.requestAddTileService`, da Android 13), mai dalla vetrina dei widget (scelta di Alessio del 26/11/2026: la vetrina mostra solo i widget). [service] è il `TileService` dell'app (quello di Android, non le Tile dell'orologio).
- `sealed interface FluidQuickTileState` — L'esito della proposta del riquadro rapido.
  - `data object Idle : FluidQuickTileState`
  - `data object Asking : FluidQuickTileState`
  - `data object Added : FluidQuickTileState`
  - `data object AlreadyAdded : FluidQuickTileState`
  - `data object NotAdded : FluidQuickTileState`
  - `data object Unsupported : FluidQuickTileState` — Android prima del 13, o il sistema ha rifiutato la richiesta.
- `data class FluidQuickTileStrings(val add: String = "Aggiungi alle Impostazioni rapide", val added: String = "Nelle Impostazioni rapide.", val alreadyAdded: String = "È già nelle Impostazioni rapide.", val notAdded: String = "Non aggiunto.", val unsupported: String = "Si può proporre da Android 13: aprilo dalle Im…, tocca la matita e trascinalo su.")` — Le parole del riquadro rapido, in italiano semplice. Un'app in un'altra lingua passa le sue.
- `fun FluidQuickTileStrings.messageFor(state: FluidQuickTileState): String?` — La frase per l'esito del riquadro rapido, o `null` se non c'è niente da dire.
- `object FluidQuickTileTags` — I tag delle prove del riquadro rapido.
  - `const val Card: String`
  - `const val Add: String`
  - `const val Message: String`
- `class FluidQuickTileRequest internal constructor(private val context: Context, val offer: FluidQuickTileOffer)` — La richiesta del riquadro rapido, senza interfaccia. Qui, a differenza del widget, la risposta torna sempre: il sistema la manda sul filo principale.
  - `var state: FluidQuickTileState by mutableStateOf(FluidQuickTileState.Idle)`
  - `val isSupported: Boolean get()` — Vero da Android 13.
  - `fun request()`
  - `companion object`
    - `fun quickTileStateOf(result: Int): FluidQuickTileState` — Il codice di `StatusBarManager` come esito: 2 aggiunto, 1 già c'era, 0 non aggiunto, negativo un errore.
- `@Composable fun FluidQuickTileCard(offer: FluidQuickTileOffer, modifier: Modifier = Modifier, strings: FluidQuickTileStrings = FluidQuickTileStrings())` — La scheda della proposta del riquadro rapido, per la pagina delle impostazioni dell'app: l'icona del riquadro, il nome, una frase e il tasto «Aggiungi alle Impostazioni rapide»; sotto, l'esito (che qui torna sempre) o, prima di Android 13, la strada a mano. La frase arriva sfocata come ogni testo che cambia l'app, ferma con «Riduci movimento».

## `engine-widget/widget/FluidWidgetPin.kt`

- `sealed interface FluidWidgetPinState` — L'esito di «Aggiungi alla Home», trattato con prudenza: non tutti i launcher accettano la richiesta, e la risposta non sempre torna (scheda `vetrina-dei-widget`, «rischi»). Per questo l'engine non si fida della sola risposta del launcher: guarda anche se il widget c'è davvero (`AppWidgetManager.getAppWidgetIds`), quando l'app torna in primo piano.
  - `data object Idle : FluidWidgetPinState` — Niente in corso.
  - `data object Asking : FluidWidgetPinState` — La richiesta è sul launcher: l'utente sceglie lì.
  - `data class Added(val appWidgetId: Int?) : FluidWidgetPinState` — Il widget è sulla Home. [appWidgetId] è il suo id, se il launcher l'ha detto o si è visto.
  - `data object NotAdded : FluidWidgetPinState` — L'utente è tornato e il widget non c'è: ha chiuso la richiesta. Si può riprovare.
  - `data object NoAnswer : FluidWidgetPinState` — Il launcher ha detto di sì, ma non ha mostrato niente: si spiega la strada a mano.
  - `data object Unsupported : FluidWidgetPinState` — Il launcher non permette di aggiungere widget da un'app (`isRequestPinAppWidgetSupported` falso, o la richiesta rifiutata).
- `data class FluidWidgetShowcaseStrings(val add: String = "Aggiungi alla Home", val asking: String = "Conferma sulla Home.", val added: String = "Aggiunto alla Home.", val notAdded: String = "Non aggiunto. Puoi riprovare quando vuoi.", val manualSteps: (appName: String) -> String = { app -> "Tieni premuto su uno spazio vuoto del…, val noAnswer: String = "Il launcher non ha risposto.", val unsupported: String = "Questo launcher non permette di aggiungere wid…, val preview: (title: String, size: String) -> String = { title, size -> "Anteprima del widget $title,…, val sizeOf: (label: String, columns: Int, rows: Int) -> String = { label, c, r -> "$label · $c×$r" }, val resizeHint: String = "Arriva nella taglia di serie: sulla Home tieni…, val nextSize: String = "Taglia dopo", val previousSize: String = "Taglia prima")` — Le parole della vetrina, in italiano semplice. Un'app in un'altra lingua passa le sue; [appName] entra nella strada a mano («cerca «Pampa Widgets»»).
- `fun FluidWidgetShowcaseStrings.messageFor(state: FluidWidgetPinState, appName: String): String?` — La frase da mostrare sotto il tasto per [state], o `null` se non c'è niente da dire.
- `class FluidWidgetPin internal constructor(private val context: Context, val provider: ComponentName, private val launcher: FluidWidgetLauncher = SystemWidgetLauncher(AppWidgetManager.getInstan…)` — Il giro della richiesta al launcher, senza interfaccia: la vetrina lo usa con [rememberFluidWidgetPin], un'app che disegna il suo tasto lo può usare da sola.
  - `var state: FluidWidgetPinState by mutableStateOf(FluidWidgetPinState.Idle)` — L'esito, per la vetrina.
  - `val isSupported: Boolean get()` — Vero se il launcher sa ricevere la richiesta (Android 8 e un launcher che la accetta).
  - `fun request(nowMillis: Long = System.currentTimeMillis())` — Chiede al launcher di mettere il widget sulla Home.
  - `fun onCallback(appWidgetId: Int?)` — Il launcher ha risposto: il widget [appWidgetId] è sulla Home.
  - `fun onPaused()` — L'app è andata in pausa (il dialogo del launcher è sopra).
  - `fun onResumed(final: Boolean = false): Boolean` — L'app è tornata in primo piano. [final] vero: è il secondo sguardo, se il widget non c'è si dice «Non aggiunto». Torna vero se serve un secondo sguardo.
  - `fun onTimeout()` — È passato [NoAnswerMillis] dalla richiesta: se l'app non è mai andata in pausa, il launcher non ha mostrato niente.
  - `fun reset()` — Torna a [FluidWidgetPinState.Idle] (per esempio cambiando taglia).
  - `fun installedIds(): Set<Int>` — I widget di [provider] sulla Home adesso.
  - `companion object`
    - `const val RecheckMillis: Long` — Il secondo sguardo dopo il ritorno: 1,5 s. A occhio, 26/11/2026 (il launcher Samsung mette il widget dopo l'animazione).
    - `const val NoAnswerMillis: Long` — Dopo quanto un launcher che non ha mostrato niente si considera muto: 4 s. A occhio, 26/11/2026.
- `@Composable fun rememberFluidWidgetPin(provider: ComponentName): FluidWidgetPin` — Il giro di [FluidWidgetPin] legato alla schermata: il ricevitore del ritorno del launcher finché la schermata c'è, la pausa e il ritorno in primo piano, il secondo sguardo e il tempo del launcher muto.

## `engine-widget/widget/FluidWidgetPreview.kt`

- `data class FluidWidgetSize(val label: String, val width: Dp, val height: Dp, val columns: Int, val rows: Int, val provider: ComponentName? = null)` — Una taglia di widget, come nella galleria dei widget di iPhone: piccola, media, grande.
  - `val dpSize: DpSize get()` — La misura come [DpSize].
  - `companion object`
    - `val Small: FluidWidgetSize`
    - `val Medium: FluidWidgetSize`
    - `val Large: FluidWidgetSize`
    - `val All: List<FluidWidgetSize>` — Le tre taglie di iPhone, nell'ordine della galleria.
- `sealed interface FluidWidgetPreview` — Come si mostra l'anteprima di un widget.
  - `class Glance(val content: @Composable () -> Unit) : FluidWidgetPreview` — Il contenuto Glance del widget, lo stesso che il widget passa a `provideContent`: Glance lo compone alla taglia scelta (`LocalSize` è quella) e la vetrina mostra le `RemoteViews` vere, come le mostrerebbe il launcher.
  - `class Remote(val build: (context: Context, size: DpSize) -> RemoteViews) : FluidWidgetPreview` — Le `RemoteViews` di un widget non Glance, costruite dall'app per la taglia.
  - `class Image(@DrawableRes val drawable: Int) : FluidWidgetPreview` — Un'immagine preparata (l'anteprima del provider, `previewImage`), adattata alla taglia.
- `data class FluidWidgetEntry(val provider: ComponentName, val title: String, val description: String, val preview: FluidWidgetPreview, val sizes: List<FluidWidgetSize> = FluidWidgetSize.All)` — Un widget dell'app nella vetrina.
- `@Composable fun FluidWidgetPreviewBox(entry: FluidWidgetEntry, size: FluidWidgetSize, contentDescription: String, modifier: Modifier = Modifier)` — L'anteprima vera di [entry] alla taglia [size]: disposta alla sua misura in dp e rimpicciolita solo se lo spazio è più stretto (mai ingrandita). Non si tocca (è un'anteprima: i tocchi non arrivano al widget) e per TalkBack è un'immagine sola con [contentDescription].
- `fun fluidWidgetPreviewScale(widget: DpSize, available: Dp): Float` — Quanto rimpicciolire un widget largo [widget] per stare in [available]: 1 se ci sta, mai più di 1. Pura, per le prove.

## `engine-widget/widget/FluidWidgetShowcase.kt`

- `object FluidWidgetShowcaseTags` — I tag delle prove della vetrina.
  - `const val Add: String`
  - `const val Message: String`
  - `const val Pager: String`
  - `fun card(title: String): String`
  - `fun preview(size: String): String`
- `object FluidWidgetShowcaseDimens` — Le misure della vetrina.
  - `val Padding: Dp` — Il margine dentro la scheda e attorno al widget sulla «Home»: 16, il margine dei widget di Apple (HIG «Widgets»: «16 points for most widgets»).
  - `val Gap: Dp` — Lo spazio fra le schede.
- `@Composable fun FluidWidgetShowcase(entries: List<FluidWidgetEntry>, appName: String, modifier: Modifier = Modifier, strings: FluidWidgetShowcaseStrings = FluidWidgetShowcaseStrings(), home: (@Composable () -> Unit)? = null)` — La vetrina dei widget dell'app: una scheda per widget, con **l'anteprima vera** a ogni taglia, le taglie da sfogliare col dito e il tasto «Aggiungi alla Home» che apre la richiesta del launcher. Solo i widget: il riquadro delle Impostazioni rapide non sta mai qui (scelta di Alessio, 26/11/2026), si propone dalle impostazioni dell'app con [FluidQuickTileCard].
- `@Composable fun FluidWidgetShowcaseCard(entry: FluidWidgetEntry, appName: String, modifier: Modifier = Modifier, strings: FluidWidgetShowcaseStrings = FluidWidgetShowcaseStrings(), home: (@Composable () -> Unit)? = null)` — La scheda di un widget: nome e frase, l'anteprima per taglia sulla «Home», i punti delle taglie, il tasto e l'esito.
