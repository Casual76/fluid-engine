# API · Grafici: ciambella, barre, linea, legenda, scrubber e quadrante

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidChartMath.kt`

- `data class FluidChartEntry(val label: String, val value: Float, val color: Color = Color.Unspecified)` — Una voce di una ripartizione: un'etichetta, un valore e (se serve) il colore.
- `data class FluidDonutSlice(val label: String, val value: Float, val fraction: Float, val color: Color, val sourceIndices: List<Int>, val isOther: Boolean)` — Una fetta cosi' come la disegna la ciambella, dopo il raggruppamento in «Altro» ([fluidDonutLayout]).
- `enum class FluidDonutProblem` — Perche' una ripartizione non si disegna.
- `enum class FluidChartSumPolicy` — Cosa fare quando i valori di una ripartizione non sommano al totale che il chiamante ha dichiarato.
- `sealed interface FluidDonutLayout` — Il risultato di [fluidDonutLayout].
  - `data class Ready(val slices: List<FluidDonutSlice>, val total: Float, val normalized: Boolean) : FluidDonutLayout` — Fette da disegnare, con le quote che sommano 1.
  - `data object Empty : FluidDonutLayout` — Nessuna voce, o tutte a zero: «nessun dato nel periodo». Non e' un errore.
  - `data class Invalid(val problem: FluidDonutProblem, val index: Int = -1, val declared: Float = Float.NaN, val actual: Float = Float.NaN) : FluidDonutLayout` — Dati che non si possono disegnare senza mentire.
- `fun fluidDonutLayout(entries: List<FluidChartEntry>, whole: Float? = null, sumPolicy: FluidChartSumPolicy = FluidChartSumPolicy.Strict, groupBelow: Float = FluidChartDefaults.GroupBelow, maxSlices: Int = FluidChartDefaults.MaxSlices, otherLabel: String = "Altro"): FluidDonutLayout` — Ripartisce [entries] in fette: valida i dati, calcola le quote e raggruppa le fette minuscole in «Altro».

## `engine-ui/ui/fluid/FluidChartScrub.kt`

- `object FluidChartTouchDefaults` — Le misure e i tempi dello scrubber dei grafici (F8.6).
  - `const val HoldMillis: Long` — Quanto il dito deve restare fermo sul grafico perche' lo scrubber parta senza che il dito si muova: **300 ms**. Borsa dice «tocca e tieni premuto il grafico»; il trascinamento di lato lo fa partire subito, come `chartXSelection`. A occhio (24 novembre 2026): sotto i 400–500 ms del tocco lungo di Android, sopra un tocco normale (~100 ms).
  - `val RuleWidth: Dp` — Lo spessore della linea verticale: **1 dp**. E' il `lineWidth` di serie di `RuleMark` in Swift Charts (1 pt; fiducia alta: il valore di serie di `StrokeStyle`).
  - `const val RuleAlpha: Float` — L'inchiostro della linea verticale con piu' serie e sulle barre, sul testo del tema: **0,45** (a occhio, 24 novembre 2026). Con una serie sola la linea e' del colore della serie, come in Borsa (immagine della guida di Borsa per Mac).
  - `val DotRadius: Dp` — Il raggio del punto scelto: **5 dp**, con un alone di **1,5 dp** del colore della superficie: 13 dp in tutto, il punto di Borsa (~12,5 pt col suo bordo chiaro, misura sull'immagine @2x della guida di Borsa per Mac, fiducia media, 24 novembre 2026; `sessioni/f8-6/riferimenti/fonti.md`).
  - `val DotHalo: Dp` — L'alone del punto scelto, del colore della superficie: lo stacca dalla linea che gli passa sotto.
  - `const val BandAlpha: Float` — La fascia fra due punti scelti con due dita: l'accento a **0,14** (a occhio, 24 novembre 2026, come Borsa).
  - `val CapsuleGap: Dp` — Lo spazio fra la capsula e il punto, quando la capsula deve scendere sotto il punto per non coprirlo.
  - `val CapsulePaddingHorizontal: Dp` — L'imbottitura della capsula, di lato e in alto e in basso: una pastiglia come la bolla dello scorrimento rapido.
  - `val CapsulePaddingVertical: Dp`
  - `const val CapsuleBirthScale: Float` — Da quanto e' piccola la capsula quando nasce dal punto (e quando ci torna): **0,6**, con la molla `snappy` dei controlli. A occhio (24 novembre 2026): il viaggio d'oro di F1B, che nasce dal punto e non dal nulla.
- `object FluidChartDiagnostics` — Gli interruttori e i contatori delle prove dei grafici (la galleria, le sessioni sul tablet): **non sono API per le app**, come `FluidPageBarDiagnostics`. I contatori contano sempre (un intero in piu' per disegno): le prove li azzerano con [resetForTesting] e guardano quanto crescono.
  - `const val CapsuleTag: String` — Il tag di prova della capsula dello scrubber (c'e' solo mentre la capsula c'e').
  - `var plotCompositions: Int` — Le composizioni del piano di un grafico cartesiano (assi, tracciati, etichette): non devono crescere sotto il dito.
  - `var plotDraws: Int` — I disegni del piano di un grafico cartesiano (guide, barre, tracciati).
  - `var scrubDraws: Int` — I disegni del livello dello scrubber (linea, fascia, punto): uno per punto toccato, non per pixel.
  - `var capsuleCompositions: Int` — Le composizioni della capsula: una per punto toccato, non per pixel.
  - `fun resetForTesting()`

## `engine-ui/ui/fluid/FluidCharts.kt`

- `object FluidChartDefaults` — Le misure e le soglie dei grafici.
  - `const val GroupBelow: Float` — Sotto questa quota (0..1) le fette di una ciambella confluiscono in «Altro» (se sono almeno due).
  - `const val MaxSlices: Int` — Il massimo di fette disegnate: oltre, le piu' piccole confluiscono in «Altro». Piu' di otto tinte non si distinguono.
  - `val DonutMaxSize: Dp` — Il lato massimo della ciambella: oltre non cresce, resta al centro.
  - `val PlotHeight: Dp` — L'altezza dell'area di disegno di barre e andamento (le etichette dell'asse stanno fuori, sotto).
  - `val MaxBarThickness: Dp` — Lo spessore massimo di una barra verticale: una barra non diventa una lastra con poche voci.
  - `val SegmentGap: Dp` — Lo spazio fra due fette, fra due barre affiancate e fra due segmenti di una pila: la separazione e' di spazio.
  - `val SelectGrow: Dp` — Quanto cresce una fetta scelta, per parte.
  - `val HorizontalBarThickness: Dp` — Lo spessore di una barra orizzontale (per serie).
  - `val MessageMinHeight: Dp` — L'altezza minima di un riquadro di stato (vuoto, errore, caricamento).
- `data class FluidChartSeries(val label: String, val color: Color = Color.Unspecified)` — Una serie di un grafico: il nome (nella legenda e nelle descrizioni) e il colore (`Unspecified`: dalla palette).
- `data class FluidBarGroup(val label: String, val values: List<Float>)` — Una casella di un grafico a barre: l'etichetta e un valore per serie, **nell'ordine delle serie**.
- `data class FluidChartLegendItem(val label: String, val color: Color = Color.Unspecified, val detail: String? = null)` — Una riga della legenda: nome, colore e (opzionale) un testo di dettaglio, di solito il valore e la percentuale.
- `val LocalFluidChartLocale` — La lingua con cui i grafici scrivono numeri e percentuali. `null` (di serie): quella della configurazione del telefono. Un'app che ha una sua lingua (diversa da quella del sistema) la mette qui, insieme a `LocalFluidLabels`, cosi' le frasi e le cifre sono della stessa lingua.
- `@Composable fun FluidChartMessage(text: String, modifier: Modifier = Modifier, detail: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null)` — Il riquadro di un grafico che non c'e': «nessun dato nel periodo», «dati non validi», un errore di caricamento con il suo «Riprova». Sta al posto del grafico, con la sua altezza minima, cosi' la pagina non salta quando i dati arrivano.
- `@Composable fun FluidChartSkeleton(modifier: Modifier = Modifier, height: Dp = FluidChartDefaults.PlotHeight, label: String? = null)` — Lo scheletro di un grafico che sta arrivando: un riquadro che respira, alto [height]. TalkBack dice «caricamento» una volta sola ([label], di serie `FluidLabels.chartLoading`).
- `@Composable fun FluidChartLegend(items: List<FluidChartLegendItem>, modifier: Modifier = Modifier, vertical: Boolean = false, selectedIndex: Int? = null, onItemClick: ((Int) -> Unit)? = null, accessible: Boolean = true)` — La legenda di un grafico: un campione di colore, il nome e (se c'e') il dettaglio, per voce.
- `@Composable fun FluidChartLegend(series: List<FluidChartSeries>, modifier: Modifier = Modifier, selectedIndex: Int? = null, onItemClick: ((Int) -> Unit)? = null, accessible: Boolean = true)` — La legenda delle [series] di un grafico (nome e colore), in fila.
- `@Composable fun FluidDonutChart(entries: List<FluidChartEntry>, modifier: Modifier = Modifier, centerValue: String? = null, centerLabel: String? = null, whole: Float? = null, sumPolicy: FluidChartSumPolicy = FluidChartSumPolicy.Strict, groupBelow: Float = FluidChartDefaults.GroupBelow, maxSlices: Int = FluidChartDefaults.MaxSlices, valueText: ((FluidDonutSlice) -> String)? = null, showLegend: Boolean = true, selectedIndex: Int? = null, onSelectedChange: ((Int?) -> Unit)? = null, contentDescription: String? = null, emptyText: String? = null)` — Una ripartizione a ciambella: il tutto diviso in parti che sommano il 100%.
- `@Composable fun FluidBarChart(groups: List<FluidBarGroup>, series: List<FluidChartSeries>, modifier: Modifier = Modifier, stacked: Boolean = false, horizontal: Boolean = false, valueText: ((Float) -> String)? = null, maxValue: Float? = null, scaleLabel: String? = null, showLegend: Boolean = series.size > 1, height: Dp = FluidChartDefaults.PlotHeight, selectedIndex: Int? = null, onSelectedChange: ((Int?) -> Unit)? = null, contentDescription: String? = null, emptyText: String? = null, scrubbing: Boolean = true, onScrub: ((IntRange?) -> Unit)? = null, deltaText: ((from: Float, to: Float) -> String)? = null)` — Barre, verticali o orizzontali, anche impilate: il confronto fra voci.
- `@Composable fun FluidLineChart(series: List<FluidChartSeries>, points: List<List<Float?>>, xLabels: List<String>, modifier: Modifier = Modifier, fill: Boolean = false, startAtZero: Boolean = true, valueText: ((Float) -> String)? = null, scaleLabel: String? = null, showLegend: Boolean = series.size > 1, height: Dp = FluidChartDefaults.PlotHeight, selectedIndex: Int? = null, onSelectedChange: ((Int?) -> Unit)? = null, contentDescription: String? = null, emptyText: String? = null, scrubbing: Boolean = true, onScrub: ((IntRange?) -> Unit)? = null, deltaText: ((from: Float, to: Float) -> String)? = null)` — Un andamento nel tempo: una o piu' linee (o aree) su un asse comune.

## `engine-ui/ui/fluid/FluidDataLabels.kt`

- `data class FluidDataLabels(val chartSpan: (from: String, to: String) -> String = { from, to -> "$from – $to" }, val chartDelta: (difference: String, percent: String) -> String = { difference, percent -> "$difference ($percent…, val gaugeValueOf: (value: String, min: String, max: String) -> String = { value, min, max -> "$value, da $min a $max" }, val gaugeThresholdAt: (label: String, value: String) -> String = { label, value -> "$label $value" }, val gaugeThreshold: String = "soglia", val gaugeBeyond: (label: String) -> String = { "oltre: $it" })` — Le parole dei grafici da toccare e del quadrante (F8.6): la capsula dello scrubber dei grafici, la differenza fra due punti e le frasi di [FluidGauge] per TalkBack. Di serie in italiano; [English] è la versione inglese completa. Un'app in un'altra lingua ne dà una sua con [LocalFluidDataLabels].
  - `companion object`
    - `val English: FluidDataLabels` — Le stesse parole in inglese.
- `val LocalFluidDataLabels: ProvidableCompositionLocal<FluidDataLabels>` — Le parole di grafici da toccare e quadrante in scope: di serie quelle italiane. Vedi [FluidDataLabels].

## `engine-ui/ui/fluid/FluidGauge.kt`

- `enum class FluidGaugeStyle` — Le tre forme del quadrante.
- `data class FluidGaugeThreshold(val value: Float, val label: String? = null, val color: Color = Color.Unspecified)` — Una soglia del quadrante: da [value] in su l'arco prende [color] e sulla pista c'e' un segno. [label] la nomina («Limite»): compare sotto il quadrante col suo valore e TalkBack la legge.
- `class FluidGaugeReading(val fraction: Float, val active: Int, val thresholdFractions: List<Float>, val clamped: Boolean)` — La lettura di un quadrante, pura: dove sta il valore da 0 a 1 ([fraction]), quale soglia ha superato ([active], l'indice in quelle date, -1 nessuna), dove stanno le soglie sulla pista ([thresholdFractions]) e se il valore era fuori dall'intervallo e si e' fermato all'estremo ([clamped]). La usano [FluidGauge] e il quadrante del widget (`EngineWidgetGauge`), cosi' i due dicono la stessa cosa.
- `fun fluidGaugeReading(value: Float, range: ClosedFloatingPointRange<Float>, thresholds: List<FluidGaugeThreshold> = emptyList()): FluidGaugeReading` — Legge [value] nell'intervallo [range] con le sue [thresholds]. Un intervallo vuoto o al contrario vale un punto solo (il quadrante e' vuoto, non divide per zero); un valore non finito vale il minimo e conta come fuori dall'intervallo.
- `object FluidGaugeDefaults` — Le misure e i tempi del quadrante.
  - `const val ArcGapDegrees: Float` — L'apertura dell'arco in basso: **118°**, cioe' un arco di 242°. Misurata fra i centri delle estremita' tonde nelle immagini ufficiali: l'apertura delle HIG «Gauges» ~118°, la complicazione delle HIG ~116°, `Gauge` `.circular` di watchOS ~106° (fiducia media: misura su immagine @2x, 24 novembre 2026; `sessioni/f8-6/riferimenti/fonti.md`).
  - `const val ArcThickness: Float` — Lo spessore dell'arco sul diametro esterno: **0,1**, uguale nelle tre immagini (fiducia media, stessa misura).
  - `val ArcSize: Dp` — Il diametro di serie dell'arco grande.
  - `val RingSize: Dp` — Il diametro di serie dell'anello da riga: sta in una riga da 44 dp con il suo margine.
  - `const val RingThickness: Float` — Lo spessore dell'anello sul diametro: **0,14**. Gli anelli di Attivita' delle HIG sono 0,106 del diametro esterno e l'anello del widget Batterie 0,097 (misure su immagine, 24 novembre 2026), ma sono grandi: a 36 dp quello spessore fa 3,5 dp e l'anello sparisce in una riga. A occhio, 24 novembre 2026: piu' spesso per la taglia piccola.
  - `val CapacityHeight: Dp` — L'altezza della barra a capacita': **6 dp**. Nelle HIG la barra e' 0,055–0,065 della sua lunghezza, ma e' disegnata corta (un orologio); su una riga di telefono quel rapporto darebbe 20 dp. A occhio, 24 novembre 2026, la barra sottile di `linearCapacity` su iOS.
  - `val ThresholdMarkWidth: Dp` — Lo spessore del segno di una soglia sulla pista.
  - `val ThresholdMarkReach: Dp` — Quanto il segno di una soglia sporge dalla pista, per parte.
  - `const val TrackAlpha: Float` — L'inchiostro della pista, sul colore del valore: **0,2**, come la pista spenta di un anello di Attivita'.
  - `fun <T> valueSpring(): SpringSpec<T>` — La molla del valore che cambia: risposta **0,55 s**, smorzamento **1**, la `.spring()` di serie di SwiftUI (fiducia alta: e' il valore documentato). Senza rimbalzo: un arco che supera il valore e torna direbbe per un attimo un numero falso (la stessa regola dell'ingresso dei grafici).
- `fun fluidGaugeArcAngles(style: FluidGaugeStyle, gapDegrees: Float = FluidGaugeDefaults.ArcGapDegrees): Pair<Float, Float>` — Gli angoli dell'arco del quadrante, in gradi alla maniera di `drawArc` (da ore 3, in senso orario): inizio e ampiezza.
- `fun fluidGaugeDescription(valueText: String, minText: String, maxText: String, thresholds: List<FluidGaugeThreshold>, thresholdTexts: List<String>, reading: FluidGaugeReading, labels: FluidDataLabels = FluidDataLabels()): String` — La frase di un quadrante per TalkBack: il valore nel suo intervallo, le soglie (col nome, o «soglia») e, se superata, quale. «18 %, da 0 a 100 %. Limite 25 %.» La usano [FluidGauge] e il quadrante del widget. Pura.
- `@Composable fun FluidGauge(value: Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, style: FluidGaugeStyle = FluidGaugeStyle.Arc, label: String? = null, valueText: ((Float) -> String)? = null, minLabel: String? = null, maxLabel: String? = null, thresholds: List<FluidGaugeThreshold> = emptyList(), color: Color = Color.Unspecified, size: Dp = if (style == FluidGaugeStyle.Ring) FluidGaugeDe…)` — Un valore dentro un intervallo, da leggere a colpo d'occhio: `FluidGauge(valore, intervallo)`.
- `@Composable fun FluidGauge(value: () -> Float, range: ClosedFloatingPointRange<Float>, modifier: Modifier = Modifier, style: FluidGaugeStyle = FluidGaugeStyle.Arc, label: String? = null, valueText: ((Float) -> String)? = null, minLabel: String? = null, maxLabel: String? = null, thresholds: List<FluidGaugeThreshold> = emptyList(), color: Color = Color.Unspecified, size: Dp = if (style == FluidGaugeStyle.Ring) FluidGaugeDe…)` — Come l'altro [FluidGauge], col valore letto da una lambda: per chi lo cambia spesso (otto volte al secondo, i decibel). La lambda si legge nel disegno e nel numero, mai nella composizione di chi chiama: l'arco si ridisegna, la pagina no.
