@file:OptIn(ExperimentalLayoutApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// I grafici dell'engine (Fase 1 di Pampa Mail, F1.5b): ciambella, barre (anche impilate), andamento, legenda.
//
// **Una famiglia, un linguaggio.** Stesse forme continue e token di tema delle altre famiglie; i colori di serie
// vengono dalla palette d'identita' (`FluidIdentityColors`, contrasti provati) e, se li da l'app, si portano
// **del minimo che serve** a staccarsi a 3:1 dallo sfondo. Il grafico non e' vetro: sta dentro una card o un
// pannello di vetro dell'app (`FluidCard(glass = true)`) e non dipinge un fondo suo.
//
// **Mai un grafico bugiardo.** Dati che non si possono disegnare senza mentire (negativi in una ripartizione, valori
// che superano la scala dichiarata, una ripartizione che non somma al totale) non diventano un disegno storto: il
// grafico dice «dati non validi» (o «nessun dato nel periodo», per il vuoto). Le percentuali scritte sommano
// sempre 100; un buco nei dati spezza la linea.
//
// **Il colore non e' l'unica informazione.** Le fette e le barre sono separate da spazio; ogni voce ha il suo testo
// (legenda con valore e percentuale, valori sulle barre quando c'e' posto); TalkBack legge una descrizione
// generata dai dati e permette di esplorare fetta per fetta, barra per barra, punto per punto, senza trascinare.
//
// **Il lavoro e' fuori dal disegno.** I conti (`FluidChartMath.kt`), le misure del testo e i percorsi si fanno in
// `remember` con chiavi sui dati e sulla misura; il disegno legge solo la frazione d'ingresso e la selezione.

/** Le misure e le soglie dei grafici. */
object FluidChartDefaults {
  /** Sotto questa quota (0..1) le fette di una ciambella confluiscono in «Altro» (se sono almeno due). */
  const val GroupBelow: Float = 0.03f

  /** Il massimo di fette disegnate: oltre, le piu' piccole confluiscono in «Altro». Piu' di otto tinte non si distinguono. */
  const val MaxSlices: Int = 8

  /** Il lato massimo della ciambella: oltre non cresce, resta al centro. */
  val DonutMaxSize: Dp = 240.dp

  /** L'altezza dell'area di disegno di barre e andamento (le etichette dell'asse stanno fuori, sotto). */
  val PlotHeight: Dp = 180.dp

  /** Lo spessore massimo di una barra verticale: una barra non diventa una lastra con poche voci. */
  val MaxBarThickness: Dp = 44.dp

  /** Lo spazio fra due fette, fra due barre affiancate e fra due segmenti di una pila: la separazione e' di spazio. */
  val SegmentGap: Dp = 3.dp

  /** Quanto cresce una fetta scelta, per parte. */
  val SelectGrow: Dp = 4.dp

  /** Lo spessore di una barra orizzontale (per serie). */
  val HorizontalBarThickness: Dp = 20.dp

  /** L'altezza minima di un riquadro di stato (vuoto, errore, caricamento). */
  val MessageMinHeight: Dp = 120.dp
}

/** Una serie di un grafico: il nome (nella legenda e nelle descrizioni) e il colore (`Unspecified`: dalla palette). */
@Immutable
data class FluidChartSeries(
  val label: String,
  val color: Color = Color.Unspecified,
)

/** Una casella di un grafico a barre: l'etichetta e un valore per serie, **nell'ordine delle serie**. */
@Immutable
data class FluidBarGroup(
  val label: String,
  val values: List<Float>,
)

/** Una riga della legenda: nome, colore e (opzionale) un testo di dettaglio, di solito il valore e la percentuale. */
@Immutable
data class FluidChartLegendItem(
  val label: String,
  val color: Color = Color.Unspecified,
  val detail: String? = null,
)

/**
 * La lingua con cui i grafici scrivono numeri e percentuali. `null` (di serie): quella della configurazione del
 * telefono. Un'app che ha una sua lingua (diversa da quella del sistema) la mette qui, insieme a
 * `LocalFluidLabels`, cosi' le frasi e le cifre sono della stessa lingua.
 */
val LocalFluidChartLocale = staticCompositionLocalOf<Locale?> { null }

@Composable
internal fun fluidChartLocale(): Locale {
  LocalFluidChartLocale.current?.let { return it }
  // La lingua del telefono si legge dalla configurazione, che la composizione osserva: cambiarla a runtime
  // ricompone i grafici. `Locale.getDefault()` resta solo per una lista vuota (mai su un telefono vero) e sta
  // fuori dalla funzione componibile perche' li' non sarebbe osservabile.
  val locales = LocalConfiguration.current.locales
  return if (locales.isEmpty) defaultChartLocale() else locales.get(0)
}

private fun defaultChartLocale(): Locale = Locale.getDefault()

// --- Colori, selezione, ingresso -----------------------------------------------------------------------------

/** Le superfici su cui puo' stare un grafico: pagina, card (due livelli) e pellicola del vetro. */
internal fun fluidChartSurfaces(scheme: ColorScheme, glassFilm: Color): List<Color> =
  listOf(scheme.surface, scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHigh, glassFilm)

/**
 * I colori di una serie come si vedono: quelli dati dall'app (o, dove mancano, dalla palette d'identita' per
 * posizione) **portati del minimo che serve** a staccarsi a 3:1 da tutte le superfici ([fluidChartSurfaces]).
 *
 * Per i colori della palette non cambia niente (e' provato da un test su tutta la palette nei due temi): il
 * ritocco serve ai colori che porta l'app. Pura: aritmetica di colori.
 */
internal fun fluidChartColors(
  requested: List<Color>,
  scheme: ColorScheme,
  glassFilm: Color,
  darkTheme: Boolean,
): List<Color> {
  val surfaces = fluidChartSurfaces(scheme, glassFilm)
  return requested.mapIndexed { index, color ->
    val base = if (color == Color.Unspecified) {
      FluidIdentityColors.colorAt(fluidSeriesPaletteIndex(index, requested.size)).forTheme(darkTheme)
    } else {
      color
    }
    legibleFill(base.copy(alpha = 1f), surfaces)
  }
}

@Composable
internal fun rememberFluidChartColors(requested: List<Color>): List<Color> {
  val scheme = MaterialTheme.colorScheme
  val film = GlassDefaults.glassFilm()
  val dark = GlassDefaults.isDarkSurface()
  return remember(requested, scheme, film, dark) { fluidChartColors(requested, scheme, film, dark) }
}

/**
 * Il colore del testo secondario dei grafici (assi, didascalie, dettagli): `onSurfaceVariant` del tema, **portato
 * verso `onSurface` del minimo che serve** perche' si legga a 4,5:1 su tutte le superfici ([fluidChartSurfaces]).
 * Sulla pellicola del vetro scuro (un grigio medio) il grigio chiaro del tema fa meno di 4:1.
 */
internal fun fluidChartMutedText(scheme: ColorScheme, glassFilm: Color): Color {
  val surfaces = fluidChartSurfaces(scheme, glassFilm)
  fun worst(c: Color) = surfaces.minOf { fluidContrastRatio(c, it) }
  val start = scheme.onSurfaceVariant
  if (worst(start) >= 4.5f) return start
  var low = 0f
  var high = 1f
  repeat(12) {
    val mid = (low + high) / 2f
    if (worst(androidx.compose.ui.graphics.lerp(start, scheme.onSurface, mid)) >= 4.5f) high = mid else low = mid
  }
  return androidx.compose.ui.graphics.lerp(start, scheme.onSurface, high)
}

@Composable
internal fun rememberFluidChartMuted(): Color {
  val scheme = MaterialTheme.colorScheme
  val film = GlassDefaults.glassFilm()
  return remember(scheme, film) { fluidChartMutedText(scheme, film) }
}

/** Il colore dell'avanzo («Altro»): un neutro, portato a 3:1 come tutti gli altri. */
@Composable
internal fun rememberFluidChartNeutral(): Color {
  val scheme = MaterialTheme.colorScheme
  val film = GlassDefaults.glassFilm()
  return remember(scheme, film) { legibleFill(scheme.outline.copy(alpha = 1f), fluidChartSurfaces(scheme, film)) }
}

/**
 * La selezione di un grafico: l'indice scelto (o null) e come si cambia. Un tocco sulla voce scelta la toglie, un
 * tocco fuori da ogni voce pure; ogni scelta suona un colpo aptico (`Tap`), mai due.
 */
internal class FluidChartSelection(
  val index: Int?,
  private val commit: (Int?) -> Unit,
  private val onTap: () -> Unit,
) {
  fun tap(target: Int?) {
    val next = if (target == null || target == index) null else target
    if (next == index) return
    if (next != null) onTap()
    commit(next)
  }
}

/**
 * Lo stato di selezione: **controllato** se c'e' [onSelectedChange] (la sorgente e' [selectedIndex]), altrimenti
 * gestito dal grafico e salvato con lo stato della pagina. Un indice fuori dai dati (la lista e' diminuita) vale
 * come nessuna selezione.
 */
@Composable
internal fun rememberFluidChartSelection(
  count: Int,
  selectedIndex: Int?,
  onSelectedChange: ((Int?) -> Unit)?,
): FluidChartSelection {
  var own by rememberSaveable { mutableStateOf<Int?>(null) }
  val haptics = LocalFluidHaptics.current
  val callback by rememberUpdatedState(onSelectedChange)
  val raw = if (onSelectedChange != null) selectedIndex else own
  val current = raw?.takeIf { it in 0 until count }
  return FluidChartSelection(
    index = current,
    commit = { next ->
      val controlled = callback
      if (controlled != null) {
        controlled(next)
      } else {
        own = next
      }
    },
    onTap = { haptics.play(FluidHapticEvent.Tap) },
  )
}

/** Da dove parte l'ingresso: gia' intero (1) con il movimento ridotto o se e' gia' avvenuto, altrimenti da zero. */
internal fun fluidChartEnterStart(reducedMotion: Boolean, alreadyPlayed: Boolean): Float =
  if (reducedMotion || alreadyPlayed) 1f else 0f

/**
 * L'ingresso: una frazione da 0 a 1. **Una volta sola** per grafico (salvata: un grafico in una lista che esce e
 * rientra non ricresce), e con il movimento ridotto il grafico nasce gia' intero, senza un solo fotogramma di
 * crescita.
 */
@Composable
internal fun rememberFluidChartEnter(): State<Float> {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  var played by rememberSaveable { mutableStateOf(false) }
  val progress = remember { Animatable(fluidChartEnterStart(reduced, played)) }
  LaunchedEffect(reduced) {
    if (reduced) {
      progress.snapTo(1f)
      played = true
    } else if (progress.value < 1f) {
      progress.animateTo(1f, FluidMotion.chartEnter())
      played = true
    }
  }
  return progress.asState()
}

/** L'evidenza di una voce scelta: cresce e torna con [FluidMotion.chartSelect], di colpo con il movimento ridotto. */
@Composable
internal fun rememberFluidChartEmphasis(selected: Int?): Pair<State<Float>, State<Int>> {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val emphasis = remember { Animatable(0f) }
  val shown = remember { mutableIntStateOf(-1) }
  LaunchedEffect(selected, reduced) {
    if (selected != null) {
      if (shown.intValue != selected) {
        shown.intValue = selected
        emphasis.snapTo(0f)
      }
      if (reduced) emphasis.snapTo(1f) else emphasis.animateTo(1f, FluidMotion.chartSelect())
    } else if (reduced) {
      emphasis.snapTo(0f)
    } else {
      emphasis.animateTo(0f, FluidMotion.chartSelect())
    }
  }
  return emphasis.asState() to shown
}

// --- Stati: vuoto, errore, caricamento ----------------------------------------------------------------------

/**
 * Il riquadro di un grafico che non c'e': «nessun dato nel periodo», «dati non validi», un errore di caricamento
 * con il suo «Riprova». Sta al posto del grafico, con la sua altezza minima, cosi' la pagina non salta quando i
 * dati arrivano.
 *
 * I grafici lo usano da soli per il vuoto e i dati non validi; lo usa l'app per lo stato che sa solo lei (il
 * calcolo e' fallito).
 *
 * @param detail una seconda riga piu' piccola (cosa fare, su quali dati si basa).
 * @param actionLabel il tasto («Riprova»); compare solo con [onAction].
 */
@Composable
fun FluidChartMessage(
  text: String,
  modifier: Modifier = Modifier,
  detail: String? = null,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = FluidChartDefaults.MessageMinHeight)
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )
    if (detail != null) {
      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = rememberFluidChartMuted(),
        textAlign = TextAlign.Center,
      )
    }
    if (actionLabel != null && onAction != null) {
      FluidButton(text = actionLabel, onClick = onAction, style = FluidButtonStyle.Tinted)
    }
  }
}

/**
 * Lo scheletro di un grafico che sta arrivando: un riquadro che respira, alto [height]. TalkBack dice «caricamento»
 * una volta sola ([label], di serie `FluidLabels.chartLoading`).
 */
@Composable
fun FluidChartSkeleton(
  modifier: Modifier = Modifier,
  height: Dp = FluidChartDefaults.PlotHeight,
  label: String? = null,
) {
  val description = label ?: LocalFluidLabels.current.chartLoading
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clearAndSetSemantics { contentDescription = description },
  ) {
    FluidSkeleton(
      modifier = Modifier.fillMaxWidth(),
      height = height,
      shape = ContinuousCornerShape(FluidRadius.Card),
    )
  }
}

// --- Legenda -----------------------------------------------------------------------------------------------

/**
 * La legenda di un grafico: un campione di colore, il nome e (se c'e') il dettaglio, per voce.
 *
 * **Perche' ha anche il testo.** Il colore da solo non basta a chi non distingue le tinte: la legenda e' la
 * seconda via per capire quale voce e' quale, e con [onItemClick] e' anche un bersaglio di tocco comodo (48 dp)
 * per le fette sottili che il dito non prenderebbe.
 *
 * - [vertical]: una voce per riga, il dettaglio a destra (le ripartizioni); altrimenti le voci si dispongono in
 *   fila e vanno a capo (le serie di un andamento).
 * - [accessible] falso la nasconde a TalkBack: lo si usa quando il grafico espone gia' le stesse voci una per una
 *   (altrimenti ogni voce si ascolterebbe due volte). Resta toccabile.
 * - I colori `Unspecified` si risolvono come nel grafico (palette per posizione); quelli dati si portano a 3:1.
 */
@Composable
fun FluidChartLegend(
  items: List<FluidChartLegendItem>,
  modifier: Modifier = Modifier,
  vertical: Boolean = false,
  selectedIndex: Int? = null,
  onItemClick: ((Int) -> Unit)? = null,
  accessible: Boolean = true,
) {
  val colors = rememberFluidChartColors(items.map { it.color })
  val scheme = MaterialTheme.colorScheme
  val muted = rememberFluidChartMuted()
  val plate = scheme.onSurface.copy(alpha = 0.08f)
  val shape = ContinuousCornerShape(FluidRadius.Small)
  val semanticsModifier = if (accessible) Modifier else Modifier.semantics { hideFromAccessibility() }

  @Composable
  fun Entry(index: Int, item: FluidChartLegendItem, rowModifier: Modifier) {
    val selected = selectedIndex == index
    var entry = rowModifier
      .clip(shape)
      .then(if (selected) Modifier.drawBehind { drawRect(plate) } else Modifier)
    if (onItemClick != null) {
      entry = entry
        .heightIn(min = 48.dp)
        .fluidPressable(
          onClick = { onItemClick(index) },
          role = Role.Button,
          haptic = null,
        )
    }
    Row(
      modifier = entry.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier = Modifier
          .size(12.dp)
          .clip(FluidCapsuleShape)
          .drawBehind { drawRect(colors[index]) },
      )
      Text(
        text = item.label,
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurface,
        modifier = if (vertical) Modifier.weight(1f) else Modifier,
      )
      if (item.detail != null) {
        Text(
          text = item.detail,
          style = MaterialTheme.typography.bodyMedium,
          color = muted,
          textAlign = TextAlign.End,
        )
      }
    }
  }

  if (vertical) {
    Column(modifier = modifier.fillMaxWidth().then(semanticsModifier)) {
      items.forEachIndexed { index, item -> Entry(index, item, Modifier.fillMaxWidth()) }
    }
  } else {
    FlowRow(
      modifier = modifier.fillMaxWidth().then(semanticsModifier),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      items.forEachIndexed { index, item -> Entry(index, item, Modifier) }
    }
  }
}

/** La legenda delle [series] di un grafico (nome e colore), in fila. */
@Composable
fun FluidChartLegend(
  series: List<FluidChartSeries>,
  modifier: Modifier = Modifier,
  selectedIndex: Int? = null,
  onItemClick: ((Int) -> Unit)? = null,
  accessible: Boolean = true,
) {
  val items = remember(series) { series.map { FluidChartLegendItem(it.label, it.color) } }
  FluidChartLegend(items, modifier, vertical = false, selectedIndex = selectedIndex, onItemClick = onItemClick, accessible = accessible)
}

// --- Descrizioni generate dai dati --------------------------------------------------------------------------

/** La sintesi che TalkBack legge per prima e le frasi, una per voce, che si esplorano una a una. */
internal class FluidChartText(val summary: String, val items: List<String>)

private fun joinSentences(vararg parts: String?): String =
  parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" ") { part ->
    // Ogni parte e' una frase: TalkBack fa una pausa solo dove c'e' il punto.
    if (part.trimEnd().last() in ".!?") part else "$part."
  }

/**
 * Il testo accessibile di una ciambella, generato dalle fette: «Arrivi per categoria. Ripartizione in 5 parti. La
 * piu' grande: Principale, 62%.» e per ogni fetta «Principale: 620 (62%)». Valori e percentuali sono quelli scritti
 * a schermo, nella lingua di [locale].
 */
internal fun fluidDonutText(
  slices: List<FluidDonutSlice>,
  values: List<String>,
  percents: List<String>,
  labels: FluidLabels,
  locale: Locale,
  title: String?,
  centerValue: String?,
  centerLabel: String?,
): FluidChartText {
  val largest = slices.indices.maxByOrNull { slices[it].fraction } ?: 0
  val center = when {
    centerValue.isNullOrBlank() -> null
    centerLabel.isNullOrBlank() -> centerValue
    else -> fluidChartFormat(locale, labels.chartGroupItem, centerLabel, centerValue)
  }
  val summary = joinSentences(
    title,
    center,
    fluidChartFormat(locale, labels.chartDonutSummary, slices.size),
    fluidChartFormat(locale, labels.chartLargest, slices[largest].label, percents[largest]),
  )
  val items = slices.mapIndexed { i, slice ->
    if (values[i].isBlank()) {
      fluidChartFormat(locale, labels.chartGroupItem, slice.label, percents[i])
    } else {
      fluidChartFormat(locale, labels.chartSliceItem, slice.label, values[i], percents[i])
    }
  }
  return FluidChartText(summary, items)
}

/**
 * Il testo accessibile di un grafico a barre: la sintesi (tipo, voci, serie, valore piu' alto, scala dichiarata) e
 * una frase per casella con i valori di tutte le serie.
 *
 * @param texts i valori scritti, `texts[gruppo][serie]`.
 * @param highest la casella col valore piu' alto e il suo valore scritto, o null.
 */
internal fun fluidBarsText(
  groups: List<FluidBarGroup>,
  series: List<FluidChartSeries>,
  texts: List<List<String>>,
  stacked: Boolean,
  highest: Pair<Int, String>?,
  scaleLabel: String?,
  labels: FluidLabels,
  locale: Locale,
  title: String?,
): FluidChartText {
  val summary = joinSentences(
    title,
    fluidChartFormat(locale, if (stacked) labels.chartStackedSummary else labels.chartBarsSummary, groups.size),
    if (series.size > 1) fluidChartFormat(locale, labels.chartSeriesCount, series.size) else null,
    highest?.let { fluidChartFormat(locale, labels.chartHighest, groups[it.first].label, it.second) },
    scaleLabel?.let { fluidChartFormat(locale, labels.chartScale, it) },
  )
  val items = groups.mapIndexed { g, group ->
    val values = if (series.size <= 1) {
      texts[g].firstOrNull().orEmpty()
    } else {
      series.indices.joinToString(", ") { s -> "${series[s].label} ${texts[g][s]}" }
    }
    fluidChartFormat(locale, labels.chartGroupItem, group.label, values)
  }
  return FluidChartText(summary, items)
}

/**
 * Il testo accessibile di un andamento: punti, prima e ultima etichetta, serie, minimo e massimo; e una frase per
 * punto con i valori di tutte le serie (un buco dice «nessun dato»).
 *
 * @param texts i valori scritti, `texts[serie][punto]`, null per un buco.
 */
internal fun fluidLineText(
  xLabels: List<String>,
  series: List<FluidChartSeries>,
  texts: List<List<String?>>,
  range: Pair<String, String>?,
  scaleLabel: String?,
  labels: FluidLabels,
  locale: Locale,
  title: String?,
): FluidChartText {
  val noValue = labels.chartNoValue
  val summary = joinSentences(
    title,
    fluidChartFormat(locale, labels.chartLineSummary, xLabels.size, xLabels.first(), xLabels.last()),
    if (series.size > 1) fluidChartFormat(locale, labels.chartSeriesCount, series.size) else null,
    range?.let { fluidChartFormat(locale, labels.chartRange, it.first, it.second) },
    scaleLabel?.let { fluidChartFormat(locale, labels.chartScale, it) },
  )
  val items = xLabels.indices.map { x ->
    val values = if (series.size <= 1) {
      texts[0][x] ?: noValue
    } else {
      series.indices.joinToString(", ") { s -> "${series[s].label} ${texts[s][x] ?: noValue}" }
    }
    fluidChartFormat(locale, labels.chartGroupItem, xLabels[x], values)
  }
  return FluidChartText(summary, items)
}

// --- Ciambella ----------------------------------------------------------------------------------------------

/**
 * Una ripartizione a ciambella: il tutto diviso in parti che sommano il 100%.
 *
 * **Perche' esiste.** «Com'e' fatto questo totale?» e' la domanda piu' comune di una pagina di statistiche
 * (categorie di arrivo, spesa per fornitore, tempo per materia), e quasi sempre la risposta e' un cerchio
 * disegnato a mano con un colore scritto nel composable e senza una parola per TalkBack. Qui i colori sono
 * quelli della palette d'identita', gli spazi separano le fette (il colore non e' l'unica informazione), la legenda
 * scrive valore e percentuale di ogni voce, e TalkBack esplora fetta per fetta.
 *
 * **La ciambella e' una promessa: le parti fanno il tutto.** Usala solo per voci **disgiunte** (ogni elemento in
 * una sola voce). Etichette che si sovrappongono (un messaggio puo' averne due) non sommano al 100% e vanno in
 * barre separate col denominatore dichiarato ([FluidBarChart] con `maxValue` e `scaleLabel`). Ne' [entries]
 * negative ne' non finite si disegnano: il grafico dice «dati non validi». Tutto a zero o nessuna voce:
 * «nessun dato nel periodo».
 *
 * **Se i dati dichiarano un totale.** Con [whole] (per esempio 100 se i valori sono percentuali, o 1240 mail) la
 * somma deve tornare entro lo 0,5%: altrimenti, di serie ([FluidChartSumPolicy.Strict]), il grafico non si
 * disegna e scrive somma e totale. [FluidChartSumPolicy.Normalize] la sceglie chi sa che c'e' un resto: le quote
 * si calcolano allora sulla somma e il disegno e' comunque pieno al 100%.
 *
 * **Fette minuscole.** Sotto [groupBelow] (3%) le fette confluiscono in «Altro» se sono almeno due, e oltre
 * [maxSlices] restano le maggiori: otto tinte sono il massimo che si distingue. Con [onSelectedChange] l'indice e'
 * quello delle fette **disegnate**; [fluidDonutLayout] da' le stesse fette (e `sourceIndices`, per risalire alle
 * voci di partenza).
 *
 * **Selezione.** Un tocco su una fetta (o sulla sua riga di legenda) la evidenzia e scrive nel centro la sua
 * percentuale; un tocco sulla stessa, o fuori, la toglie. Gestita dal grafico e salvata, oppure controllata da
 * [selectedIndex] + [onSelectedChange] (in tal caso la sorgente e' [selectedIndex]).
 *
 * **Testo grande.** Al 200% l'anello si assottiglia e il foro cresce; il valore del centro si riduce per entrare;
 * la legenda va a capo. Tutti i numeri esistono anche come testo, nella legenda.
 *
 * @param centerValue il valore nel foro (il totale, «1.240»), con [centerLabel] sotto; senza scelta.
 * @param valueText il testo del valore di una fetta (legenda e descrizione); di serie il numero nella lingua. Una
 *   stringa vuota nasconde il valore e lascia la sola percentuale (quando il valore **e'** gia' la percentuale).
 * @param contentDescription di cosa parla il grafico («Arrivi per categoria»): TalkBack lo legge **prima**
 *   della sintesi generata dai dati.
 * @param emptyText il testo del vuoto, di serie `FluidLabels.chartNoData`.
 */
@Composable
fun FluidDonutChart(
  entries: List<FluidChartEntry>,
  modifier: Modifier = Modifier,
  centerValue: String? = null,
  centerLabel: String? = null,
  whole: Float? = null,
  sumPolicy: FluidChartSumPolicy = FluidChartSumPolicy.Strict,
  groupBelow: Float = FluidChartDefaults.GroupBelow,
  maxSlices: Int = FluidChartDefaults.MaxSlices,
  valueText: ((FluidDonutSlice) -> String)? = null,
  showLegend: Boolean = true,
  selectedIndex: Int? = null,
  onSelectedChange: ((Int?) -> Unit)? = null,
  contentDescription: String? = null,
  emptyText: String? = null,
) {
  val labels = LocalFluidLabels.current
  val locale = fluidChartLocale()
  val layout = remember(entries, whole, sumPolicy, groupBelow, maxSlices, labels.chartOther) {
    fluidDonutLayout(entries, whole, sumPolicy, groupBelow, maxSlices, labels.chartOther)
  }
  when (layout) {
    FluidDonutLayout.Empty -> FluidChartMessage(text = emptyText ?: labels.chartNoData, modifier = modifier)
    is FluidDonutLayout.Invalid -> FluidChartMessage(
      text = when (layout.problem) {
        FluidDonutProblem.SumMismatch -> fluidChartFormat(
          locale,
          labels.chartInvalidSum,
          fluidFormatNumber(layout.actual.toDouble(), locale),
          fluidFormatNumber(layout.declared.toDouble(), locale),
        )
        else -> labels.chartInvalidData
      },
      modifier = modifier,
    )
    is FluidDonutLayout.Ready -> DonutContent(
      layout = layout,
      modifier = modifier,
      centerValue = centerValue,
      centerLabel = centerLabel,
      valueText = valueText,
      showLegend = showLegend,
      selectedIndex = selectedIndex,
      onSelectedChange = onSelectedChange,
      contentDescription = contentDescription,
      labels = labels,
      locale = locale,
    )
  }
}

@Composable
private fun DonutContent(
  layout: FluidDonutLayout.Ready,
  modifier: Modifier,
  centerValue: String?,
  centerLabel: String?,
  valueText: ((FluidDonutSlice) -> String)?,
  showLegend: Boolean,
  selectedIndex: Int?,
  onSelectedChange: ((Int?) -> Unit)?,
  contentDescription: String?,
  labels: FluidLabels,
  locale: Locale,
) {
  val slices = layout.slices
  val regular = slices.count { !it.isOther }
  val resolved = rememberFluidChartColors(
    remember(slices) { slices.filter { !it.isOther }.map { it.color } },
  )
  val neutral = rememberFluidChartNeutral()
  val colors = remember(slices, resolved, neutral) {
    var next = 0
    slices.map { if (it.isOther) neutral else resolved[next++] }
  }
  val fractions = remember(slices) { slices.map { it.fraction } }
  val percents = remember(slices, locale) { fluidPercentTexts(fractions, locale) }
  val values = remember(slices, valueText, locale) {
    slices.map { valueText?.invoke(it) ?: fluidFormatNumber(it.value.toDouble(), locale) }
  }
  val text = remember(slices, values, percents, labels, locale, contentDescription, centerValue, centerLabel) {
    fluidDonutText(slices, values, percents, labels, locale, contentDescription, centerValue, centerLabel)
  }
  val selection = rememberFluidChartSelection(slices.size, selectedIndex, onSelectedChange)
  val legendItems = remember(slices, colors, values, percents) {
    slices.mapIndexed { i, slice ->
      FluidChartLegendItem(
        label = slice.label,
        color = colors[i],
        detail = if (values[i].isBlank()) percents[i] else "${values[i]} · ${percents[i]}",
      )
    }
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .semantics { this.contentDescription = text.summary },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      DonutPlot(
        side = min(maxWidth, FluidChartDefaults.DonutMaxSize),
        slices = slices,
        colors = colors,
        fractions = fractions,
        percents = percents,
        itemTexts = text.items,
        selection = selection,
        centerValue = centerValue,
        centerLabel = centerLabel,
        labels = labels,
        regularCount = regular,
      )
    }
    if (showLegend) {
      FluidChartLegend(
        items = legendItems,
        vertical = true,
        selectedIndex = selection.index,
        onItemClick = { selection.tap(it) },
        // Le stesse voci sono gia' i nodi della ciambella: leggerle due volte e' rumore.
        accessible = false,
      )
    }
  }
}

@Composable
private fun DonutPlot(
  side: Dp,
  slices: List<FluidDonutSlice>,
  colors: List<Color>,
  fractions: List<Float>,
  percents: List<String>,
  itemTexts: List<String>,
  selection: FluidChartSelection,
  centerValue: String?,
  centerLabel: String?,
  labels: FluidLabels,
  regularCount: Int,
) {
  val density = LocalDensity.current
  val enter by rememberFluidChartEnter()
  val (emphasisState, shownState) = rememberFluidChartEmphasis(selection.index)
  val scheme = MaterialTheme.colorScheme
  val selectedBy by rememberUpdatedState(selection)

  val geometry = remember(side, fractions, density.density, density.fontScale) {
    val sidePx = with(density) { side.toPx() }
    val grow = with(density) { FluidChartDefaults.SelectGrow.toPx() }
    val thickness = sidePx * fluidDonutRingFraction(density.fontScale)
    val outer = sidePx / 2f - grow
    val mid = outer - thickness / 2f
    val gapDegrees = Math.toDegrees(with(density) { FluidChartDefaults.SegmentGap.toPx() }.toDouble() / mid).toFloat()
    DonutGeometry(
      sidePx = sidePx,
      outer = outer,
      inner = outer - thickness,
      mid = mid,
      thickness = thickness,
      grow = grow,
      angles = fluidDonutAngles(fractions, gapDegrees),
    )
  }

  Box(
    modifier = Modifier
      .size(side)
      .semantics { collectionInfo = CollectionInfo(rowCount = slices.size, columnCount = 1) },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(side)
        .drawBehind {
          val g = geometry
          val limit = enter * 360f
          val emphasis = emphasisState.value
          val shown = shownState.value
          val center = Offset(size.width / 2f, size.height / 2f)
          g.angles.forEachIndexed { i, (start, sweep) ->
            if (sweep <= 0f) return@forEachIndexed
            val visible = (limit - start).coerceIn(0f, sweep)
            if (visible <= 0f) return@forEachIndexed
            val widen = if (i == shown) g.grow * 2f * emphasis else 0f
            drawArc(
              color = colors[i],
              startAngle = -90f + start,
              sweepAngle = visible,
              useCenter = false,
              topLeft = Offset(center.x - g.mid, center.y - g.mid),
              size = Size(g.mid * 2f, g.mid * 2f),
              style = Stroke(width = g.thickness + widen),
            )
          }
        }
        .pointerInput(geometry, fractions) {
          detectTapGestures { position ->
            val g = geometry
            val hit = fluidDonutHit(
              dx = position.x - size.width / 2f,
              dy = position.y - size.height / 2f,
              innerRadius = g.inner,
              outerRadius = g.outer,
              slop = g.grow * 2f,
              fractions = fractions,
            )
            selectedBy.tap(if (hit < 0) null else hit)
          }
        },
    )

    // Il foro: il valore dato dall'app, o la percentuale della fetta scelta.
    val selected = selection.index
    val holePx = geometry.inner * 2f
    val holeDp = with(density) { (holePx * 0.82f).toDp() }
    val main: String?
    val sub: String?
    if (selected != null) {
      main = percents[selected]
      sub = slices[selected].label
    } else {
      main = centerValue
      sub = centerLabel
    }
    if (main != null) {
      DonutCenter(main = main, sub = sub, width = holeDp, modifier = Modifier.semantics { hideFromAccessibility() })
    }

    // Un nodo per fetta, per TalkBack: il rettangolo dell'arco, l'azione di scelta, lo stato.
    Box(modifier = Modifier.size(side)) {
      slices.forEachIndexed { i, _ ->
        val (start, sweep) = geometry.angles[i]
        val bounds = fluidArcBounds(
          cx = geometry.sidePx / 2f,
          cy = geometry.sidePx / 2f,
          outer = geometry.outer,
          inner = geometry.inner,
          startDegrees = start,
          sweepDegrees = max(sweep, 0.1f),
        )
        val width = with(density) { max(bounds[2] - bounds[0], 1f).toDp() }
        val height = with(density) { max(bounds[3] - bounds[1], 1f).toDp() }
        val isSelected = selected == i
        Box(
          modifier = Modifier
            .offset { IntOffset(bounds[0].roundToInt(), bounds[1].roundToInt()) }
            .size(width, height)
            .semantics {
              this.contentDescription = itemTexts[i]
              role = Role.Button
              this.selected = isSelected
              if (isSelected) stateDescription = labels.selected
              collectionItemInfo = CollectionItemInfo(i, 1, 0, 1)
              onClick {
                selectedBy.tap(i)
                true
              }
            },
        )
      }
    }
  }
}

private class DonutGeometry(
  val sidePx: Float,
  val outer: Float,
  val inner: Float,
  val mid: Float,
  val thickness: Float,
  val grow: Float,
  val angles: List<Pair<Float, Float>>,
)

/**
 * Il valore e l'etichetta del foro. Il valore **si riduce** per entrare nella larghezza del foro (al 200% un
 * «1.240» in 34 sp non ci sta): mai a capo (una cifra spezzata e' un numero sbagliato) e mai tagliato; l'etichetta
 * puo' andare a capo su due righe.
 */
@Composable
private fun DonutCenter(main: String, sub: String?, width: Dp, modifier: Modifier = Modifier) {
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val base = FluidTextStyles.largeNumeric
  val widthPx = with(density) { width.toPx() }
  val style = remember(main, widthPx, density.fontScale, base) {
    val natural = measurer.measure(main, base, softWrap = false, maxLines = 1).size.width
    if (natural <= widthPx || natural == 0) {
      base
    } else {
      base.copy(
        fontSize = base.fontSize * (widthPx / natural).coerceAtLeast(0.5f),
        lineHeight = base.lineHeight * (widthPx / natural).coerceAtLeast(0.5f),
      )
    }
  }
  Column(
    modifier = modifier.width(width),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = main,
      style = style,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      softWrap = false,
      textAlign = TextAlign.Center,
    )
    if (sub != null) {
      Text(
        text = sub,
        style = MaterialTheme.typography.labelMedium,
        color = rememberFluidChartMuted(),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}

// --- L'asse cartesiano: barre verticali e andamento ---------------------------------------------------------

/** Un testo da posare sul grafico: il centro orizzontale, il bordo alto e la larghezza, in pixel. */
internal class FluidPlacedLabel(val text: String, val centerX: Float, val top: Float, val color: Color)

/** Cio' che un grafico cartesiano disegna: il disegno vero e proprio e i testi da posare. */
internal class FluidCartesianArt(
  val draw: DrawScope.(enter: Float, selected: Int?) -> Unit,
  val labels: List<FluidPlacedLabel> = emptyList(),
)

/** La misura di un grafico cartesiano: l'area di disegno, la fascia delle etichette e quali etichette si scrivono. */
internal class FluidCartesianPlan(
  val scale: FluidNiceScale,
  val count: Int,
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
  val totalHeight: Float,
  val gutter: Float,
  val labelHeight: Float,
  val xStride: Int,
  val xLabelWidth: Float,
  val gap: Float,
) {
  val width: Float get() = right - left
  val height: Float get() = bottom - top
  val slot: Float get() = if (count > 0) width / count else width

  fun yOf(value: Double): Float = bottom - scale.fraction(value) * height

  fun centerX(index: Int): Float = fluidSlotCenter(index, count, left, width)
}

/**
 * Misura il grafico: la larghezza dell'etichetta piu' larga dei valori decide la colonna degli assi; la fascia
 * sotto ospita fino a due righe di etichette; e le etichette delle caselle si **diradano** (una ogni [stride])
 * finche' tutte quelle scritte stanno nello spazio che hanno, a capo su due righe, senza spezzare una parola.
 */
private fun planCartesian(
  measurer: TextMeasurer,
  style: TextStyle,
  scale: FluidNiceScale,
  tickLabels: List<String>,
  xLabels: List<String>,
  widthPx: Float,
  plotHeightPx: Float,
  topInsetPx: Float,
  gapPx: Float,
): FluidCartesianPlan {
  val labelHeight = measurer.measure("0", style).size.height.toFloat()
  val gutter = (tickLabels.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0) + gapPx * 1.5f
  val left = gutter
  val right = max(widthPx - gapPx / 2f, left + 1f)
  val count = xLabels.size
  val slot = (right - left) / max(count, 1)
  val top = max(topInsetPx, labelHeight / 2f)
  val bottom = top + plotHeightPx
  val stride = fluidLabelStride(count) { s ->
    val limit = (s * slot - gapPx).coerceAtLeast(1f)
    var i = 0
    var fits = true
    while (i < count && fits) {
      val label = xLabels[i]
      val constraints = Constraints(maxWidth = limit.toInt().coerceAtLeast(1))
      val result = measurer.measure(label, style, TextOverflow.Ellipsis, true, 2, constraints = constraints)
      fits = !result.hasVisualOverflow && label.split(' ', '\n').all { word ->
        word.isEmpty() || measurer.measure(word, style, softWrap = false, maxLines = 1).size.width <= limit
      }
      i += s
    }
    fits
  }
  val xLabelWidth = (stride * slot - gapPx).coerceAtLeast(1f)
  val total = bottom + labelHeight * 2f + gapPx
  return FluidCartesianPlan(scale, count, left, top, right, bottom, total, gutter, labelHeight, stride, xLabelWidth, gapPx)
}

/**
 * Lo scheletro comune di barre verticali e andamento: la colonna dei valori, le linee guida, le etichette
 * delle caselle che si diradano, la selezione per casella (un tocco, senza trascinare) e un nodo per casella
 * per TalkBack. Il disegno specifico lo da' [art], costruito **una volta** per misura e dati.
 */
@Composable
private fun FluidCartesian(
  modifier: Modifier,
  plotHeight: Dp,
  scale: FluidNiceScale,
  tickLabels: List<String>,
  xLabels: List<String>,
  itemTexts: List<String>,
  selection: FluidChartSelection,
  labels: FluidLabels,
  topInset: (labelHeight: Float, gap: Float) -> Float,
  artKeys: List<Any?>,
  art: (FluidCartesianPlan) -> FluidCartesianArt,
) {
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val axisStyle = MaterialTheme.typography.labelSmall
  val scheme = MaterialTheme.colorScheme
  val muted = rememberFluidChartMuted()
  val enter by rememberFluidChartEnter()
  val selectedBy by rememberUpdatedState(selection)
  val artState by rememberUpdatedState(art)

  BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val widthPx = constraints.maxWidth.toFloat()
    val plan = remember(widthPx, plotHeight, scale, tickLabels, xLabels, axisStyle, density.density, density.fontScale) {
      val gap = with(density) { 6.dp.toPx() }
      val labelHeight = measurer.measure("0", axisStyle).size.height.toFloat()
      planCartesian(
        measurer = measurer,
        style = axisStyle,
        scale = scale,
        tickLabels = tickLabels,
        xLabels = xLabels,
        widthPx = widthPx,
        plotHeightPx = with(density) { plotHeight.toPx() },
        topInsetPx = topInset(labelHeight, gap),
        gapPx = gap,
      )
    }
    val drawing = remember(plan, artKeys) { artState(plan) }
    val plate = remember(plan, density.density) {
      ContinuousCornerShape(FluidRadius.Small).createOutline(Size(plan.slot, plan.height), LayoutDirection.Ltr, density)
    }
    val gridColor = scheme.onSurface.copy(alpha = 0.12f)
    val zeroColor = scheme.onSurfaceVariant.copy(alpha = 0.5f)
    val plateColor = scheme.onSurface.copy(alpha = 0.08f)
    val totalDp = with(density) { plan.totalHeight.toDp() }

    Box(modifier = Modifier.fillMaxWidth().height(totalDp)) {
      // Il piano: guide, casella scelta, disegno. Tutto legge solo l'ingresso e la selezione.
      Box(
        modifier = Modifier
          .matchParentSize()
          .drawBehind {
            plan.scale.ticks.forEach { tick ->
              val y = plan.yOf(tick)
              val isZero = tick == 0.0
              drawLine(
                color = if (isZero) zeroColor else gridColor,
                start = Offset(plan.left, y),
                end = Offset(plan.right, y),
                strokeWidth = (if (isZero) 1.5.dp else 1.dp).toPx(),
              )
            }
            selection.index?.let { index ->
              translate(plan.left + index * plan.slot, plan.top) { drawOutlinePlate(plate, plateColor) }
            }
            drawing.draw(this, enter, selection.index)
          }
          .pointerInput(plan) {
            detectTapGestures { position ->
              val inPlot = position.y >= plan.top && position.y <= plan.bottom
              val index = if (inPlot) fluidSlotAt(position.x, plan.count, plan.left, plan.width) else -1
              selectedBy.tap(if (index < 0) null else index)
            }
          },
      )

      // Le tacche dei valori, a destra nella colonna a sinistra.
      plan.scale.ticks.forEachIndexed { i, tick ->
        val y = plan.yOf(tick)
        Text(
          text = tickLabels[i],
          style = axisStyle,
          color = muted,
          maxLines = 1,
          softWrap = false,
          textAlign = TextAlign.End,
          modifier = Modifier
            .width(with(density) { (plan.gutter - plan.gap).toDp() })
            .offset { IntOffset(0, (y - plan.labelHeight / 2f).roundToInt()) }
            .semantics { hideFromAccessibility() },
        )
      }
      // Le etichette delle caselle: una ogni `xStride`, a capo su due righe, mai sovrapposte.
      var shown = 0
      while (shown < plan.count) {
        val index = shown
        val centered = plan.centerX(index) - plan.xLabelWidth / 2f
        val x = centered.coerceIn(0f, max(0f, widthPx - plan.xLabelWidth))
        Text(
          text = xLabels[index],
          style = axisStyle,
          color = muted,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .width(with(density) { plan.xLabelWidth.toDp() })
            .offset { IntOffset(x.roundToInt(), (plan.bottom + plan.gap / 2f).roundToInt()) }
            .semantics { hideFromAccessibility() },
        )
        shown += plan.xStride
      }
      // I valori sulle barre, dove c'e' posto.
      drawing.labels.forEach { label ->
        Text(
          text = label.text,
          style = axisStyle,
          color = label.color,
          maxLines = 1,
          softWrap = false,
          textAlign = TextAlign.Center,
          modifier = Modifier
            .offset {
              IntOffset(
                (label.centerX - measurer.measure(label.text, axisStyle, softWrap = false, maxLines = 1).size.width / 2f).roundToInt(),
                label.top.roundToInt(),
              )
            }
            .semantics { hideFromAccessibility() },
        )
      }
      // Un nodo per casella, per TalkBack: lo stesso rettangolo del tocco.
      Box(
        modifier = Modifier
          .matchParentSize()
          .semantics { collectionInfo = CollectionInfo(rowCount = plan.count, columnCount = 1) },
      ) {
        for (index in 0 until plan.count) {
          val isSelected = selection.index == index
          Box(
            modifier = Modifier
              .offset { IntOffset((plan.left + index * plan.slot).roundToInt(), plan.top.roundToInt()) }
              .size(
                with(density) { max(plan.slot, 1f).toDp() },
                with(density) { max(plan.height, 1f).toDp() },
              )
              .semantics {
                this.contentDescription = itemTexts[index]
                role = Role.Button
                this.selected = isSelected
                if (isSelected) stateDescription = labels.selected
                collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                onClick {
                  selectedBy.tap(index)
                  true
                }
              },
          )
        }
      }
    }
  }
}

private fun DrawScope.drawOutlinePlate(outline: Outline, color: Color) {
  drawOutline(outline, color)
}

/** Il numero di tacche che stanno sull'altezza di un'area di disegno col testo di sistema corrente. */
@Composable
private fun rememberTickBudget(plotHeight: Dp): Int {
  val density = LocalDensity.current
  val style = MaterialTheme.typography.labelSmall
  return remember(plotHeight, density.density, density.fontScale, style) {
    val line = with(density) {
      if (style.lineHeight.isSpecified) style.lineHeight.toPx() else (style.fontSize.takeIf { it.isSpecified } ?: 12.sp).toPx() * 1.3f
    }
    fluidTickBudget(with(density) { plotHeight.toPx() }, line)
  }
}

// --- Barre --------------------------------------------------------------------------------------------------

/**
 * Barre, verticali o orizzontali, anche impilate: il confronto fra voci.
 *
 * **Perche' esiste.** Le barre sono il grafico piu' onesto per confrontare quantita' (la lunghezza e' il
 * valore) e il piu' usato dove una ciambella mentirebbe: etichette che si sovrappongono (un elemento in piu' di
 * una), conteggi per periodo, spesa per fornitore. Un grafico fatto a mano scrive i numeri a occhio, sceglie una
 * scala a caso e al 200% del testo le etichette si sovrappongono.
 *
 * **Scala e denominatore.** La scala parte **sempre da zero** (e' una barra, non una linea) e finisce su un
 * numero tondo. Se le barre sono quote di un totale, dichiaralo: [maxValue] fissa la fine della scala (1.240 mail:
 * la scala finisce a 1.240, non a 1.500) e [scaleLabel] lo dice in parole sotto il grafico («% sulle 1.240 mail del
 * periodo»), nella sintesi per TalkBack. Un valore che **supera** [maxValue] e' un dato sbagliato: il grafico dice
 * «dati non validi» e non taglia una barra (una barra tagliata mente).
 *
 * **Pile.** [stacked] impila le serie in una barra sola: **solo per serie disgiunte e non negative** (la barra
 * intera e' la somma). Le pile non si usano per etichette che si sovrappongono. Valori negativi nelle barre
 * affiancate partono dallo zero verso il basso (o sinistra); in una pila sono dati non validi.
 *
 * **Etichette e testo grande.** Le etichette delle caselle vanno a capo su due righe e **si diradano** (una ogni
 * tanto) finche' non si sovrappongono; al 200% ne restano meno, mai sovrapposte, e il numero di ogni barra e' nella
 * descrizione. Il valore si scrive sulla barra quando c'e' posto (in cima, o dentro il segmento di una pila).
 * Le barre orizzontali hanno l'etichetta a sinistra (anche su tre righe) e il valore alla fine della barra.
 *
 * **Selezione.** Un tocco su una casella (o sulla sua riga, in orizzontale) la evidenzia e scrive sotto il suo
 * testo completo; il bersaglio e' tutta la casella, e il tocco fuori dall'area la toglie. Come per la ciambella:
 * [selectedIndex] + [onSelectedChange] la controllano.
 *
 * @param groups le caselle, con un valore per serie nell'ordine di [series].
 * @param series le serie (nome e colore); una per un grafico semplice. Con piu' di una compare la legenda.
 * @param valueText il testo di un valore (sulle barre, negli assi, nelle descrizioni); di serie il numero nella lingua.
 * @param scaleLabel la scala o il denominatore in parole; compare sotto il grafico finche' non si sceglie una casella.
 * @param height l'altezza dell'area di disegno (solo verticali).
 * @param contentDescription di cosa parla il grafico, letto prima della sintesi generata.
 */
@Composable
fun FluidBarChart(
  groups: List<FluidBarGroup>,
  series: List<FluidChartSeries>,
  modifier: Modifier = Modifier,
  stacked: Boolean = false,
  horizontal: Boolean = false,
  valueText: ((Float) -> String)? = null,
  maxValue: Float? = null,
  scaleLabel: String? = null,
  showLegend: Boolean = series.size > 1,
  height: Dp = FluidChartDefaults.PlotHeight,
  selectedIndex: Int? = null,
  onSelectedChange: ((Int?) -> Unit)? = null,
  contentDescription: String? = null,
  emptyText: String? = null,
) {
  val labels = LocalFluidLabels.current
  val locale = fluidChartLocale()
  val budget = rememberTickBudget(height)
  val rows = remember(groups) { groups.map { it.values } }
  val checked = remember(rows, series.size, stacked, maxValue, budget) {
    fluidBarScale(rows, series.size, stacked, maxValue, budget)
  }
  val allZero = remember(rows) { rows.all { row -> row.all { it == 0f } } }
  if (groups.isEmpty() || series.isEmpty() || (checked.second == null && allZero)) {
    FluidChartMessage(text = emptyText ?: labels.chartNoData, modifier = modifier)
    return
  }
  val scale = checked.first
  if (scale == null) {
    FluidChartMessage(text = labels.chartInvalidData, modifier = modifier)
    return
  }

  val colors = rememberFluidChartColors(remember(series) { series.map { it.color } })
  val format: (Float) -> String = valueText ?: { v -> fluidFormatNumber(v.toDouble(), locale) }
  val texts = remember(groups, valueText, locale) { groups.map { g -> g.values.map(format) } }
  val highest = remember(groups, stacked, valueText, locale) {
    val totals = groups.map { g -> if (stacked) g.values.sumOf { it.toDouble() } else (g.values.maxOrNull() ?: 0f).toDouble() }
    val top = totals.indices.maxByOrNull { totals[it] }
    top?.let { it to format(totals[it].toFloat()) }
  }
  val text = remember(groups, series, texts, stacked, highest, scaleLabel, labels, locale, contentDescription) {
    fluidBarsText(groups, series, texts, stacked, highest, scaleLabel, labels, locale, contentDescription)
  }
  val selection = rememberFluidChartSelection(groups.size, selectedIndex, onSelectedChange)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .semantics { this.contentDescription = text.summary },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (horizontal) {
      HorizontalBars(
        groups = groups,
        colors = colors,
        stacked = stacked,
        scale = scale,
        texts = texts,
        itemTexts = text.items,
        selection = selection,
        labels = labels,
      )
      HorizontalScale(scale = scale, locale = locale, valueText = valueText)
    } else {
      VerticalBars(
        groups = groups,
        colors = colors,
        stacked = stacked,
        scale = scale,
        texts = texts,
        itemTexts = text.items,
        selection = selection,
        labels = labels,
        locale = locale,
        valueText = valueText,
        height = height,
      )
    }
    ChartCaption(readout = selection.index?.let { text.items[it] }, scaleLabel = scaleLabel)
    if (showLegend) FluidChartLegend(series = series, accessible = false)
  }
}

/** La riga sotto il grafico: il testo della casella scelta, o la scala dichiarata. Una riga sempre riservata. */
@Composable
private fun ChartCaption(readout: String?, scaleLabel: String?) {
  Text(
    text = readout ?: scaleLabel.orEmpty(),
    style = MaterialTheme.typography.labelMedium,
    color = rememberFluidChartMuted(),
    minLines = 1,
    maxLines = 4,
    modifier = Modifier.fillMaxWidth().semantics { hideFromAccessibility() },
  )
}

@Composable
private fun VerticalBars(
  groups: List<FluidBarGroup>,
  colors: List<Color>,
  stacked: Boolean,
  scale: FluidNiceScale,
  texts: List<List<String>>,
  itemTexts: List<String>,
  selection: FluidChartSelection,
  labels: FluidLabels,
  locale: Locale,
  valueText: ((Float) -> String)?,
  height: Dp,
) {
  val scheme = MaterialTheme.colorScheme
  val tickLabels = remember(scale, locale, valueText) {
    scale.ticks.map { tick ->
      if (valueText != null) valueText(tick.toFloat()) else fluidFormatNumber(tick, locale, compactFrom = 10_000.0)
    }
  }
  val xLabels = remember(groups) { groups.map { it.label } }
  val measurer = rememberTextMeasurer()
  val axisStyle = MaterialTheme.typography.labelSmall
  val density = LocalDensity.current
  val onSurface = scheme.onSurface
  val seriesCount = colors.size

  FluidCartesian(
    modifier = Modifier,
    plotHeight = height,
    scale = scale,
    tickLabels = tickLabels,
    xLabels = xLabels,
    itemTexts = itemTexts,
    selection = selection,
    labels = labels,
    topInset = { labelHeight, gap -> labelHeight + gap },
    artKeys = listOf(groups, colors, stacked, texts, onSurface),
  ) { plan ->
    val gap = plan.gap / 2f
    val spans = fluidBarSpans(
      slotExtent = plan.slot,
      seriesCount = seriesCount,
      stacked = stacked,
      maxThickness = with(density) { FluidChartDefaults.MaxBarThickness.toPx() },
      innerGap = gap,
    )
    val radius = FluidRadius.Small
    val bars = mutableListOf<PlacedBar>()
    val placed = mutableListOf<FluidPlacedLabel>()
    groups.forEachIndexed { g, group ->
      val slotLeft = plan.left + g * plan.slot
      if (stacked) {
        val edges = fluidStackEdges(group.values)
        val lastVisible = edges.indexOfLast { (from, to) -> to > from }
        edges.forEachIndexed { s, (from, to) ->
          if (to <= from) return@forEachIndexed
          bars.add(PlacedBar(slotLeft + spans[s].start, spans[s].size, from, to, colors[s], roundFree = s == lastVisible, positive = true, gapBelow = s > 0))
          val top = plan.yOf(to)
          val bottom = plan.yOf(from)
          val width = measurer.measure(texts[g][s], axisStyle, softWrap = false, maxLines = 1).size.width
          if (bottom - top >= plan.labelHeight + gap && spans[s].size >= width + gap) {
            placed.add(FluidPlacedLabel(texts[g][s], slotLeft + (spans[s].start + spans[s].end) / 2f, (top + bottom) / 2f - plan.labelHeight / 2f, fluidReadableOn(colors[s]).ink))
          }
        }
      } else {
        group.values.forEachIndexed { s, value ->
          if (value == 0f) return@forEachIndexed
          val positive = value > 0f
          bars.add(PlacedBar(slotLeft + spans[s].start, spans[s].size, 0.0, value.toDouble(), colors[s], roundFree = true, positive = positive, gapBelow = false))
          val width = measurer.measure(texts[g][s], axisStyle, softWrap = false, maxLines = 1).size.width
          if (spans[s].size + gap >= width) {
            val y = plan.yOf(value.toDouble())
            val labelTop = if (positive) y - plan.labelHeight - gap / 2f else y + gap / 2f
            placed.add(FluidPlacedLabel(texts[g][s], slotLeft + (spans[s].start + spans[s].end) / 2f, labelTop, onSurface))
          }
        }
      }
    }
    val shape = { positive: Boolean, round: Boolean ->
      if (!round) {
        ContinuousCornerShape(0.dp, 0.dp, 0.dp, 0.dp)
      } else if (positive) {
        ContinuousCornerShape(topStart = radius, topEnd = radius)
      } else {
        ContinuousCornerShape(bottomEnd = radius, bottomStart = radius)
      }
    }
    val segmentGap = plan.gap / 2f
    val cached = bars.map { bar ->
      val rect = bar.rect(plan, 1f, segmentGap)
      shape(bar.positive, bar.roundFree).createOutline(rect.second, LayoutDirection.Ltr, density)
    }
    FluidCartesianArt(
      labels = placed,
      draw = { enter, _ ->
        bars.forEachIndexed { i, bar ->
          val (origin, size) = bar.rect(plan, enter, segmentGap)
          if (size.height <= 0f) return@forEachIndexed
          val outline = if (enter >= 1f) cached[i] else shape(bar.positive, bar.roundFree).createOutline(size, LayoutDirection.Ltr, this)
          translate(origin.x, origin.y) { drawOutline(outline, bar.color) }
        }
      },
    )
  }
}

/** Una barra pronta da disegnare: dove sta, fra quali valori, di che colore e come si arrotonda. */
private class PlacedBar(
  val x: Float,
  val width: Float,
  val from: Double,
  val to: Double,
  val color: Color,
  val roundFree: Boolean,
  val positive: Boolean,
  val gapBelow: Boolean,
) {
  /** L'angolo in alto a sinistra e la misura della barra con l'ingresso [enter] (0 = a zero, 1 = intera). */
  fun rect(plan: FluidCartesianPlan, enter: Float, segmentGap: Float): Pair<Offset, Size> {
    val a = plan.yOf(from * enter)
    val b = plan.yOf(to * enter)
    var top = min(a, b)
    var bottom = max(a, b)
    if (gapBelow && bottom - top > segmentGap * 2f) bottom -= segmentGap
    return Offset(x, top) to Size(width, max(bottom - top, 0f))
  }
}

@Composable
private fun HorizontalScale(scale: FluidNiceScale, locale: Locale, valueText: ((Float) -> String)?) {
  val format: (Double) -> String = { v ->
    if (valueText != null) valueText(v.toFloat()) else fluidFormatNumber(v, locale, compactFrom = 10_000.0)
  }
  Row(modifier = Modifier.fillMaxWidth().semantics { hideFromAccessibility() }, horizontalArrangement = Arrangement.SpaceBetween) {
    Text(
      text = format(scale.min),
      style = MaterialTheme.typography.labelSmall,
      color = rememberFluidChartMuted(),
    )
    Text(
      text = format(scale.max),
      style = MaterialTheme.typography.labelSmall,
      color = rememberFluidChartMuted(),
    )
  }
}

@Composable
private fun HorizontalBars(
  groups: List<FluidBarGroup>,
  colors: List<Color>,
  stacked: Boolean,
  scale: FluidNiceScale,
  texts: List<List<String>>,
  itemTexts: List<String>,
  selection: FluidChartSelection,
  labels: FluidLabels,
) {
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val enter by rememberFluidChartEnter()
  val measurer = rememberTextMeasurer()
  val valueStyle = MaterialTheme.typography.labelSmall
  val plateShape = ContinuousCornerShape(FluidRadius.Small)
  val plate = scheme.onSurface.copy(alpha = 0.08f)
  val track = scheme.onSurface.copy(alpha = 0.06f)
  val selectedBy by rememberUpdatedState(selection)
  val maxLines = if (density.fontScale > 1.3f) 3 else 2
  val barHeight = FluidChartDefaults.HorizontalBarThickness
  val barRadius = FluidRadius.Small
  val zero = scale.fraction(0.0)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .semantics { collectionInfo = CollectionInfo(rowCount = groups.size, columnCount = 1) },
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    groups.forEachIndexed { g, group ->
      val isSelected = selection.index == g
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .clip(plateShape)
          .then(if (isSelected) Modifier.drawBehind { drawRect(plate) } else Modifier)
          .fluidPressable(onClick = { selectedBy.tap(g) }, role = Role.Button, haptic = null)
          .semantics(mergeDescendants = true) {
            this.contentDescription = itemTexts[g]
            this.selected = isSelected
            if (isSelected) stateDescription = labels.selected
            collectionItemInfo = CollectionItemInfo(g, 1, 0, 1)
          }
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = group.label,
          style = MaterialTheme.typography.bodyMedium,
          color = scheme.onSurface,
          maxLines = maxLines,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(0.36f).semantics { hideFromAccessibility() },
        )
        BoxWithConstraints(modifier = Modifier.weight(0.64f)) {
          val trackPx = constraints.maxWidth.toFloat()
          val bars = if (stacked) 1 else group.values.size
          val gap = 4.dp
          val blockHeight = barHeight * bars + gap * (bars - 1)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(blockHeight)
              .drawBehind {
                val barPx = barHeight.toPx()
                val gapPx = gap.toPx()
                val segGap = FluidChartDefaults.SegmentGap.toPx() / 2f
                val freeRound = ContinuousCornerShape(topEnd = barRadius, bottomEnd = barRadius)
                val freeRoundLeft = ContinuousCornerShape(topStart = barRadius, bottomStart = barRadius)
                val square = ContinuousCornerShape(0.dp, 0.dp, 0.dp, 0.dp)
                for (bar in 0 until bars) {
                  val y = bar * (barPx + gapPx)
                  drawRect(track, Offset(0f, y), Size(size.width, barPx))
                  if (stacked) {
                    val edges = fluidStackEdges(group.values)
                    val lastVisible = edges.indexOfLast { (from, to) -> to > from }
                    edges.forEachIndexed { s, (from, to) ->
                      if (to <= from) return@forEachIndexed
                      val x0 = scale.fraction(from * enter) * size.width + if (s > 0) segGap else 0f
                      val x1 = scale.fraction(to * enter) * size.width
                      if (x1 - x0 <= 0f) return@forEachIndexed
                      val outline = (if (s == lastVisible) freeRound else square).createOutline(Size(x1 - x0, barPx), layoutDirection, this)
                      translate(x0, y) { drawOutline(outline, colors[s]) }
                    }
                  } else {
                    val value = group.values[bar]
                    if (value != 0f) {
                      val end = scale.fraction(value * enter.toDouble()) * size.width
                      val base = zero * size.width
                      val x0 = min(base, end)
                      val x1 = max(base, end)
                      if (x1 - x0 > 0f) {
                        val outline = (if (value > 0f) freeRound else freeRoundLeft).createOutline(Size(x1 - x0, barPx), layoutDirection, this)
                        translate(x0, y) { drawOutline(outline, colors[bar]) }
                      }
                    }
                  }
                }
              },
          ) {
            // Il valore: dentro la barra se ci sta, altrimenti subito dopo la sua fine.
            for (bar in 0 until bars) {
              val total = if (stacked) group.values.sumOf { it.toDouble() } else group.values[bar].toDouble()
              if (total == 0.0) continue
              val label = if (stacked) null else texts[g][bar]
              val written = label ?: texts[g].joinToString(" · ")
              val textWidth = measurer.measure(written, valueStyle, softWrap = false, maxLines = 1).size.width
              val baseX = zero * trackPx
              val endX = scale.fraction(total) * trackPx
              val barLength = abs(endX - baseX)
              val pad = with(density) { 6.dp.toPx() }
              val inside = !stacked && barLength >= textWidth + pad * 2f
              val x = when {
                inside && total > 0.0 -> endX - textWidth - pad
                inside -> endX + pad
                total > 0.0 -> min(endX + pad, max(trackPx - textWidth, 0f))
                else -> max(endX - textWidth - pad, 0f)
              }
              val ink = if (inside) fluidReadableOn(colors[bar]).ink else scheme.onSurface
              if (stacked && barLength < textWidth) continue
              val y = bar * with(density) { (barHeight + gap).toPx() } + (with(density) { barHeight.toPx() } - measurer.measure("0", valueStyle).size.height) / 2f
              Text(
                text = written,
                style = valueStyle,
                color = ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.semantics { hideFromAccessibility() },
              )
            }
          }
        }
      }
    }
  }
}

// --- Andamento ----------------------------------------------------------------------------------------------

/**
 * Un andamento nel tempo: una o piu' linee (o aree) su un asse comune.
 *
 * **Perche' esiste.** «Com'e' andata nelle ultime settimane?» vuole una linea che si legge: assi con numeri
 * tondi, etichette che non si sovrappongono, un punto che si tocca per sapere quanto valeva. E vuole **non
 * mentire**: se un giorno non ha dati, la linea si interrompe, non si inventa un valore unendo i due vicini.
 *
 * **Dati.** `points[serie][posizione]`: una lista per serie, lunga come [xLabels]; `null` e' un **buco** (un
 * giorno senza dati). Un buco spezza la linea; un punto fra due buchi si disegna come un punto; la
 * descrizione dice «nessun dato». I valori devono essere finiti (negativi ammessi: la scala include lo zero e
 * lo mostra). Tutti buchi o nessun punto: «nessun dato nel periodo». Liste di lunghezze diverse: «dati non validi».
 * Il campionamento (50.000 record in 30 punti) lo fa chi chiama, **fuori** dal disegno.
 *
 * **Asse.** La scala include lo zero ([startAtZero], di serie; spegnerlo e' una scelta di chi sa che un asse
 * che parte da un valore diverso non inganna) e finisce su un numero tondo; con [fill] (area) lo zero e' sempre
 * incluso, perche' l'area e' la distanza dallo zero. Le etichette di [xLabels] si diradano e vanno a capo senza
 * sovrapporsi, anche al 200%.
 *
 * **La curva non inventa picchi.** Fra due punti la linea e' una curva monotona: non supera mai i due valori
 * misurati (una spline qualunque «rimbalza» e disegna un massimo che i dati non hanno).
 *
 * **Selezione.** Un tocco su un punto (la sua colonna) lo evidenzia e scrive sotto «etichetta – valori»; la
 * stessa cosa si esplora con TalkBack punto per punto.
 *
 * @param series le serie (nome, colore); piu' di una mostra la legenda.
 * @param valueText il testo di un valore (assi e descrizioni); di serie il numero nella lingua.
 * @param scaleLabel l'unita' o la scala in parole, sotto il grafico («mail al giorno»).
 * @param contentDescription di cosa parla il grafico, letto prima della sintesi generata.
 */
@Composable
fun FluidLineChart(
  series: List<FluidChartSeries>,
  points: List<List<Float?>>,
  xLabels: List<String>,
  modifier: Modifier = Modifier,
  fill: Boolean = false,
  startAtZero: Boolean = true,
  valueText: ((Float) -> String)? = null,
  scaleLabel: String? = null,
  showLegend: Boolean = series.size > 1,
  height: Dp = FluidChartDefaults.PlotHeight,
  selectedIndex: Int? = null,
  onSelectedChange: ((Int?) -> Unit)? = null,
  contentDescription: String? = null,
  emptyText: String? = null,
) {
  val labels = LocalFluidLabels.current
  val locale = fluidChartLocale()
  val budget = rememberTickBudget(height)
  val ragged = points.size != series.size || points.any { it.size != xLabels.size }
  val finiteValues = remember(points) { points.flatMap { row -> row.filterNotNull() } }
  if (series.isEmpty() || xLabels.isEmpty() || (!ragged && finiteValues.isEmpty())) {
    FluidChartMessage(text = emptyText ?: labels.chartNoData, modifier = modifier)
    return
  }
  if (ragged || finiteValues.any { !it.isFinite() }) {
    FluidChartMessage(text = labels.chartInvalidData, modifier = modifier)
    return
  }

  val scale = remember(finiteValues, fill, startAtZero, budget) {
    fluidNiceScale(
      dataMin = finiteValues.min().toDouble(),
      dataMax = finiteValues.max().toDouble(),
      targetTicks = budget,
      includeZero = fill || startAtZero,
    )
  }
  val colors = rememberFluidChartColors(remember(series) { series.map { it.color } })
  val format: (Float) -> String = valueText ?: { v -> fluidFormatNumber(v.toDouble(), locale) }
  val texts = remember(points, valueText, locale) { points.map { row -> row.map { v -> v?.let(format) } } }
  val tickLabels = remember(scale, locale, valueText) {
    scale.ticks.map { tick ->
      if (valueText != null) valueText(tick.toFloat()) else fluidFormatNumber(tick, locale, compactFrom = 10_000.0)
    }
  }
  val text = remember(xLabels, series, texts, finiteValues, scaleLabel, labels, locale, contentDescription) {
    fluidLineText(
      xLabels, series, texts,
      range = format(finiteValues.min()) to format(finiteValues.max()),
      scaleLabel = scaleLabel, labels = labels, locale = locale, title = contentDescription,
    )
  }
  val selection = rememberFluidChartSelection(xLabels.size, selectedIndex, onSelectedChange)
  val density = LocalDensity.current

  Column(
    modifier = modifier
      .fillMaxWidth()
      .semantics { this.contentDescription = text.summary },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FluidCartesian(
      modifier = Modifier,
      plotHeight = height,
      scale = scale,
      tickLabels = tickLabels,
      xLabels = xLabels,
      itemTexts = text.items,
      selection = selection,
      labels = labels,
      topInset = { labelHeight, gap -> labelHeight / 2f + gap },
      artKeys = listOf(points, colors, fill),
    ) { plan -> lineArt(plan, points, colors, fill, density) }
    ChartCaption(readout = selection.index?.let { text.items[it] }, scaleLabel = scaleLabel)
    if (showLegend) FluidChartLegend(series = series, accessible = false)
  }
}

/** I tracciati di un andamento, costruiti una volta per misura e dati. */
private fun lineArt(
  plan: FluidCartesianPlan,
  points: List<List<Float?>>,
  colors: List<Color>,
  fill: Boolean,
  density: Density,
): FluidCartesianArt {
  val baseline = plan.yOf(if (plan.scale.min <= 0.0 && plan.scale.max >= 0.0) 0.0 else plan.scale.min)
  class Track(val color: Color, val line: Path, val area: Path?, val isolated: List<Offset>, val dots: List<Pair<Int, Offset>>)
  val tracks = points.mapIndexed { s, row ->
    val line = Path()
    val area = if (fill) Path() else null
    val isolated = mutableListOf<Offset>()
    val dots = mutableListOf<Pair<Int, Offset>>()
    row.forEachIndexed { x, v -> if (v != null) dots.add(x to Offset(plan.centerX(x), plan.yOf(v.toDouble()))) }
    fluidLineRuns(row).forEach { run ->
      val xs = FloatArray(run.size) { plan.centerX(run[it]) }
      val ys = FloatArray(run.size) { plan.yOf(row[run[it]]!!.toDouble()) }
      if (run.size == 1) {
        isolated.add(Offset(xs[0], ys[0]))
        return@forEach
      }
      val tangents = fluidMonotoneTangents(xs, ys)
      line.moveTo(xs[0], ys[0])
      area?.moveTo(xs[0], baseline)
      area?.lineTo(xs[0], ys[0])
      for (i in 0 until run.size - 1) {
        val dx = xs[i + 1] - xs[i]
        line.cubicTo(xs[i] + dx / 3f, ys[i] + tangents[i] * dx / 3f, xs[i + 1] - dx / 3f, ys[i + 1] - tangents[i + 1] * dx / 3f, xs[i + 1], ys[i + 1])
        area?.cubicTo(xs[i] + dx / 3f, ys[i] + tangents[i] * dx / 3f, xs[i + 1] - dx / 3f, ys[i + 1] - tangents[i + 1] * dx / 3f, xs[i + 1], ys[i + 1])
      }
      area?.lineTo(xs.last(), baseline)
      area?.close()
    }
    Track(colors[s], line, area, isolated, dots)
  }
  val showDots = plan.count <= 24
  return FluidCartesianArt(
    draw = { enter, selected ->
      val strokeWidth = 2.5.dp.toPx()
      val dot = 3.dp.toPx()
      // L'ingresso scopre il tracciato da sinistra a destra: una linea che si disegna, mai un valore finto a meta'.
      clipRect(left = 0f, top = 0f, right = plan.left + (size.width - plan.left) * enter, bottom = size.height) {
        tracks.forEach { track ->
          track.area?.let { area ->
            drawPath(area, Brush.verticalGradient(listOf(track.color.copy(alpha = 0.28f), track.color.copy(alpha = 0.02f)), startY = plan.top, endY = baseline))
          }
          drawPath(track.line, track.color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
          track.isolated.forEach { drawCircle(track.color, dot * 1.2f, it) }
          if (showDots) track.dots.forEach { (_, p) -> drawCircle(track.color, dot, p) }
          if (selected != null) {
            track.dots.firstOrNull { it.first == selected }?.let { (_, p) -> drawCircle(track.color, dot * 1.8f, p) }
          }
        }
      }
    },
  )
}
