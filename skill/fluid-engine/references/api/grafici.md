# API · Grafici: ciambella, barre, linea, legenda

_Generato sull'engine 3.1.1 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

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
- `@Composable fun FluidBarChart(groups: List<FluidBarGroup>, series: List<FluidChartSeries>, modifier: Modifier = Modifier, stacked: Boolean = false, horizontal: Boolean = false, valueText: ((Float) -> String)? = null, maxValue: Float? = null, scaleLabel: String? = null, showLegend: Boolean = series.size > 1, height: Dp = FluidChartDefaults.PlotHeight, selectedIndex: Int? = null, onSelectedChange: ((Int?) -> Unit)? = null, contentDescription: String? = null, emptyText: String? = null)` — Barre, verticali o orizzontali, anche impilate: il confronto fra voci.
- `@Composable fun FluidLineChart(series: List<FluidChartSeries>, points: List<List<Float?>>, xLabels: List<String>, modifier: Modifier = Modifier, fill: Boolean = false, startAtZero: Boolean = true, valueText: ((Float) -> String)? = null, scaleLabel: String? = null, showLegend: Boolean = series.size > 1, height: Dp = FluidChartDefaults.PlotHeight, selectedIndex: Int? = null, onSelectedChange: ((Int?) -> Unit)? = null, contentDescription: String? = null, emptyText: String? = null)` — Un andamento nel tempo: una o piu' linee (o aree) su un asse comune.
