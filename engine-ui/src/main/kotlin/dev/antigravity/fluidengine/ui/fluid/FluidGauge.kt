/*
 * L'indicatore a quadrante (4.0, F8.6): un valore dentro un intervallo, da leggere a colpo d'occhio. Codice del Fluid
 * Engine.
 *
 * Il riferimento e' `Gauge` di SwiftUI (iOS 16): l'arco aperto in basso col numero al centro (`accessoryCircular`),
 * l'anello pieno (`accessoryCircularCapacity`, gli anelli di Attivita') e la barra a capacita' (`linearCapacity`, il
 * widget Batterie). E' della famiglia dei grafici: stessi colori portati a 3:1, stesso testo secondario a 4,5:1, stesso
 * «mai un grafico bugiardo» (un valore fuori dall'intervallo si ferma all'estremo e lo dice).
 *
 * Il costo e' quello di un arco: il valore si legge nel disegno (anche da una lambda, per chi lo cambia otto volte al
 * secondo, come i decibel di Pampa Sounds), l'arco si muove con una molla senza rimbalzo, e il numero si ricompone solo
 * quando cambia il testo. Fermo, non ridisegna.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Le tre forme del quadrante. */
enum class FluidGaugeStyle {
  /** L'arco grande aperto in basso, col numero al centro e il minimo e il massimo alle estremita' (`accessoryCircular`). */
  Arc,

  /** L'anello piccolo da riga, chiuso, col numero dentro se ci sta (`accessoryCircularCapacity`, gli anelli di Attivita'). */
  Ring,

  /** La barra a capacita', con l'etichetta e il valore sopra e il minimo e il massimo sotto (`linearCapacity`, Batterie). */
  Capacity,
}

/**
 * Una soglia del quadrante: da [value] in su l'arco prende [color] e sulla pista c'e' un segno. [label] la nomina
 * («Limite»): compare sotto il quadrante col suo valore e TalkBack la legge.
 *
 * @param color di serie (`Unspecified`) l'avviso per le soglie intermedie e l'errore del tema per l'ultima: il caso
 *   comune e' «piu' alto e' peggio» (assenze, spazio occupato, decibel). Chi ha una soglia buona (la media sopra il 6)
 *   passa il suo colore. Ogni colore e' portato a 3:1 sulle superfici, come i grafici.
 */
@Immutable
data class FluidGaugeThreshold(
  val value: Float,
  val label: String? = null,
  val color: Color = Color.Unspecified,
)

/**
 * La lettura di un quadrante, pura: dove sta il valore da 0 a 1 ([fraction]), quale soglia ha superato ([active],
 * l'indice in quelle date, -1 nessuna), dove stanno le soglie sulla pista ([thresholdFractions]) e se il valore era fuori
 * dall'intervallo e si e' fermato all'estremo ([clamped]). La usano [FluidGauge] e il quadrante del widget
 * (`EngineWidgetGauge`), cosi' i due dicono la stessa cosa.
 */
@Immutable
class FluidGaugeReading(
  val fraction: Float,
  val active: Int,
  val thresholdFractions: List<Float>,
  val clamped: Boolean,
)

/**
 * Legge [value] nell'intervallo [range] con le sue [thresholds]. Un intervallo vuoto o al contrario vale un punto solo (il
 * quadrante e' vuoto, non divide per zero); un valore non finito vale il minimo e conta come fuori dall'intervallo.
 */
fun fluidGaugeReading(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  thresholds: List<FluidGaugeThreshold> = emptyList(),
): FluidGaugeReading {
  val start = range.start
  val span = range.endInclusive - start
  fun fractionOf(v: Float): Float = if (span <= 0f || !v.isFinite()) 0f else ((v - start) / span).coerceIn(0f, 1f)
  val clamped = !value.isFinite() || value < start || value > range.endInclusive
  var active = -1
  var best = Float.NEGATIVE_INFINITY
  thresholds.forEachIndexed { i, t ->
    if (value.isFinite() && value >= t.value && t.value >= best) {
      best = t.value
      active = i
    }
  }
  return FluidGaugeReading(fractionOf(value), active, thresholds.map { fractionOf(it.value) }, clamped)
}

/** Le misure e i tempi del quadrante. */
object FluidGaugeDefaults {
  /**
   * L'apertura dell'arco in basso: **118°**, cioe' un arco di 242°. Misurata fra i centri delle estremita' tonde nelle
   * immagini ufficiali: l'apertura delle HIG «Gauges» ~118°, la complicazione delle HIG ~116°, `Gauge` `.circular` di
   * watchOS ~106° (fiducia media: misura su immagine @2x, 24 novembre 2026; `sessioni/f8-6/riferimenti/fonti.md`).
   */
  const val ArcGapDegrees: Float = 118f

  /** Lo spessore dell'arco sul diametro esterno: **0,1**, uguale nelle tre immagini (fiducia media, stessa misura). */
  const val ArcThickness: Float = 0.1f

  /** Il diametro di serie dell'arco grande. */
  val ArcSize: Dp = 160.dp

  /** Il diametro di serie dell'anello da riga: sta in una riga da 44 dp con il suo margine. */
  val RingSize: Dp = 36.dp

  /**
   * Lo spessore dell'anello sul diametro: **0,14**. Gli anelli di Attivita' delle HIG sono 0,106 del diametro esterno e
   * l'anello del widget Batterie 0,097 (misure su immagine, 24 novembre 2026), ma sono grandi: a 36 dp quello spessore
   * fa 3,5 dp e l'anello sparisce in una riga. A occhio, 24 novembre 2026: piu' spesso per la taglia piccola.
   */
  const val RingThickness: Float = 0.14f

  /**
   * L'altezza della barra a capacita': **6 dp**. Nelle HIG la barra e' 0,055–0,065 della sua lunghezza, ma e' disegnata
   * corta (un orologio); su una riga di telefono quel rapporto darebbe 20 dp. A occhio, 24 novembre 2026, la barra
   * sottile di `linearCapacity` su iOS.
   */
  val CapacityHeight: Dp = 6.dp

  /** Lo spessore del segno di una soglia sulla pista. */
  val ThresholdMarkWidth: Dp = 2.dp

  /** Quanto il segno di una soglia sporge dalla pista, per parte. */
  val ThresholdMarkReach: Dp = 3.dp

  /** L'inchiostro della pista, sul colore del valore: **0,2**, come la pista spenta di un anello di Attivita'. */
  const val TrackAlpha: Float = 0.2f

  /**
   * La molla del valore che cambia: risposta **0,55 s**, smorzamento **1**, la `.spring()` di serie di SwiftUI (fiducia
   * alta: e' il valore documentato). Senza rimbalzo: un arco che supera il valore e torna direbbe per un attimo un numero
   * falso (la stessa regola dell'ingresso dei grafici).
   */
  fun <T> valueSpring(): SpringSpec<T> = spring(FluidMotion.DampingChrome, FluidMotion.stiffnessForResponse(0.55))
}

/** Gli angoli dell'arco del quadrante, in gradi alla maniera di `drawArc` (da ore 3, in senso orario): inizio e ampiezza. */
fun fluidGaugeArcAngles(style: FluidGaugeStyle, gapDegrees: Float = FluidGaugeDefaults.ArcGapDegrees): Pair<Float, Float> =
  if (style == FluidGaugeStyle.Arc) (90f + gapDegrees / 2f) to (360f - gapDegrees) else -90f to 360f

/** Il colore di una soglia senza colore: l'avviso per le intermedie, l'errore per l'ultima. Pura. */
internal fun fluidGaugeThresholdColor(index: Int, thresholds: List<FluidGaugeThreshold>, scheme: ColorScheme, dark: Boolean): Color {
  val given = thresholds[index].color
  if (given != Color.Unspecified) return given
  val highest = thresholds.indices.maxByOrNull { thresholds[it].value }
  return if (index == highest) scheme.error else if (dark) GaugeWarningDark else GaugeWarningLight
}

private val GaugeWarningLight = Color(0xFFE65100)
private val GaugeWarningDark = Color(0xFFFFB74D)

/**
 * La frase di un quadrante per TalkBack: il valore nel suo intervallo, le soglie (col nome, o «soglia») e, se superata,
 * quale. «18 %, da 0 a 100 %. Limite 25 %.» La usano [FluidGauge] e il quadrante del widget. Pura.
 *
 * @param thresholdTexts i valori scritti delle soglie, nell'ordine di [thresholds].
 */
fun fluidGaugeDescription(
  valueText: String,
  minText: String,
  maxText: String,
  thresholds: List<FluidGaugeThreshold>,
  thresholdTexts: List<String>,
  reading: FluidGaugeReading,
  labels: FluidDataLabels = FluidDataLabels(),
): String {
  val parts = mutableListOf(labels.gaugeValueOf(valueText, minText, maxText))
  thresholds.forEachIndexed { i, t ->
    val named = labels.gaugeThresholdAt(t.label ?: labels.gaugeThreshold, thresholdTexts[i])
    parts += if (i == reading.active) labels.gaugeBeyond(named) else named
  }
  return parts.joinToString(". ") { it.trim().trimEnd('.') } + "."
}

/**
 * Un valore dentro un intervallo, da leggere a colpo d'occhio: `FluidGauge(valore, intervallo)`.
 *
 * **Perche' esiste.** «Quanto manca?» e «quanto ne ho usato?» sono le domande di una pagina di stato: le ore di assenza
 * sul massimo (ClasseViva), lo spazio occupato (Pampa Store, MyCloud), il livello in decibel (Pampa Sounds), l'avanzamento
 * di una coda (Universal Converter). La ciambella dice di cosa e' fatto un tutto, non quanto manca a un traguardo.
 *
 * **Tre forme** ([style]): l'arco grande col numero al centro e il minimo e il massimo alle estremita'; l'anello piccolo da
 * riga, col numero dentro se ci sta; la barra a capacita', con [label] e il valore sopra e gli estremi sotto.
 *
 * **Soglie.** Da ogni [thresholds] in su l'arco prende il colore della soglia (si scambia con una dissolvenza) e sulla
 * pista c'e' un segno; sotto l'arco e la barra le soglie con nome si leggono col loro valore («Limite 25 %»), e quella
 * superata e' scritta in grassetto, «oltre». Il colore non e' l'unica informazione: il numero c'e' sempre, e la soglia superata
 * e' anche scritta.
 *
 * **Mai bugiardo.** Un valore fuori dall'intervallo si ferma all'estremo (l'arco non gira oltre) e il numero resta quello
 * vero; un valore non finito vale il minimo.
 *
 * **Movimento.** Il valore che cambia muove l'arco con [FluidGaugeDefaults.valueSpring], senza rimbalzo; la prima volta
 * l'arco cresce da zero con l'ingresso dei grafici, una volta sola. Con «Riduci movimento» si posa.
 *
 * **Accessibilita'.** Un nodo solo, con [label] come nome, l'intervallo e una frase dai dati: «18 %, da 0 a 100 %.
 * Limite 25 %.»
 *
 * Il numero e' un testo che cambia di colpo: quando `FluidNumericText` (F8.1) sara' nell'engine, il punto da cambiare e'
 * uno solo (`GaugeNumber` qui sotto).
 *
 * @param value il valore, nell'unita' di [range].
 * @param range l'intervallo.
 * @param label cosa misura («Assenze»): il nome per TalkBack, sotto il numero nell'arco, sopra la barra.
 * @param valueText il valore scritto; di serie il numero nella lingua. Si usa anche per estremi e soglie.
 * @param minLabel, maxLabel gli estremi scritti; di serie [valueText] degli estremi. Una stringa vuota li nasconde.
 * @param color il colore del valore sotto ogni soglia; di serie l'accento del tema. Portato a 3:1.
 * @param size il diametro di arco e anello; la barra prende la larghezza che ha.
 */
@Composable
fun FluidGauge(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
  style: FluidGaugeStyle = FluidGaugeStyle.Arc,
  label: String? = null,
  valueText: ((Float) -> String)? = null,
  minLabel: String? = null,
  maxLabel: String? = null,
  thresholds: List<FluidGaugeThreshold> = emptyList(),
  color: Color = Color.Unspecified,
  size: Dp = if (style == FluidGaugeStyle.Ring) FluidGaugeDefaults.RingSize else FluidGaugeDefaults.ArcSize,
) {
  val current by rememberUpdatedState(value)
  FluidGauge(
    value = { current },
    range = range,
    modifier = modifier,
    style = style,
    label = label,
    valueText = valueText,
    minLabel = minLabel,
    maxLabel = maxLabel,
    thresholds = thresholds,
    color = color,
    size = size,
  )
}

/**
 * Come l'altro [FluidGauge], col valore letto da una lambda: per chi lo cambia spesso (otto volte al secondo, i decibel).
 * La lambda si legge nel disegno e nel numero, mai nella composizione di chi chiama: l'arco si ridisegna, la pagina no.
 */
@Composable
fun FluidGauge(
  value: () -> Float,
  range: ClosedFloatingPointRange<Float>,
  modifier: Modifier = Modifier,
  style: FluidGaugeStyle = FluidGaugeStyle.Arc,
  label: String? = null,
  valueText: ((Float) -> String)? = null,
  minLabel: String? = null,
  maxLabel: String? = null,
  thresholds: List<FluidGaugeThreshold> = emptyList(),
  color: Color = Color.Unspecified,
  size: Dp = if (style == FluidGaugeStyle.Ring) FluidGaugeDefaults.RingSize else FluidGaugeDefaults.ArcSize,
) {
  val locale = fluidChartLocale()
  val dataLabels = LocalFluidDataLabels.current
  val scheme = MaterialTheme.colorScheme
  val film = GlassDefaults.glassFilm()
  val dark = GlassDefaults.isDarkSurface()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val muted = rememberFluidChartMuted()
  val currentValue by rememberUpdatedState(value)
  val format by rememberUpdatedState(valueText ?: { v: Float -> fluidFormatNumber(v.toDouble(), locale) })

  // I colori: quello del valore e quelli delle soglie, portati a 3:1 sulle superfici dei grafici.
  val colors = remember(color, thresholds, scheme, film, dark) {
    val surfaces = fluidChartSurfaces(scheme, film)
    val base = legibleFill((if (color == Color.Unspecified) scheme.primary else color).copy(alpha = 1f), surfaces)
    base to thresholds.indices.map { i -> legibleFill(fluidGaugeThresholdColor(i, thresholds, scheme, dark).copy(alpha = 1f), surfaces) }
  }
  val reading by remember(range, thresholds) { derivedStateOf { fluidGaugeReading(currentValue(), range, thresholds) } }
  val number by remember { derivedStateOf { format(currentValue()) } }
  val minText = minLabel ?: format(range.start)
  val maxText = maxLabel ?: format(range.endInclusive)
  val thresholdTexts = remember(thresholds, valueText, locale) { thresholds.map { format(it.value) } }
  val spoken by remember(thresholds, thresholdTexts, minText, maxText, dataLabels) {
    derivedStateOf { fluidGaugeDescription(number, minText, maxText, thresholds, thresholdTexts, reading, dataLabels) }
  }

  // Il valore disegnato: insegue la frazione con la molla di SwiftUI, e cresce da zero la prima volta (ingresso dei grafici).
  val drawn = remember { Animatable(fluidGaugeReading(value(), range, thresholds).fraction) }
  LaunchedEffect(range, thresholds, reduced) {
    snapshotFlow { reading.fraction }.collectLatest { target ->
      if (reduced) drawn.snapTo(target) else drawn.animateTo(target, FluidGaugeDefaults.valueSpring())
    }
  }
  val enter by rememberFluidChartEnter()
  val activeColor = animateColorAsState(
    targetValue = reading.active.let { if (it >= 0) colors.second[it] else colors.first },
    animationSpec = FluidMotion.color(),
    label = "gauge",
  )
  val geometry = GaugeGeometry(style, colors.second, drawn, { enter }, activeColor)

  // La frase si rilegge quando cambia il numero scritto (non a ogni fotogramma della molla).
  val spokenNow = spoken
  val semanticsModifier = Modifier.clearAndSetSemantics {
    if (label != null) contentDescription = label
    stateDescription = spokenNow
    progressBarRangeInfo = ProgressBarRangeInfo(currentValue().coerceIn(range.start, maxOf(range.start, range.endInclusive)), range.start..maxOf(range.start, range.endInclusive))
  }

  when (style) {
    FluidGaugeStyle.Arc -> Column(
      modifier = modifier.then(semanticsModifier),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      ArcGauge(size, geometry, reading, thresholds, label, { number }, minText, maxText, muted)
      ThresholdLegend(thresholds, thresholdTexts, colors.second, { reading.active }, muted, dataLabels, centered = true)
    }
    FluidGaugeStyle.Ring -> Box(modifier = modifier.then(semanticsModifier)) {
      RingGauge(size, geometry, { number })
    }
    FluidGaugeStyle.Capacity -> Column(
      modifier = modifier.fillMaxWidth().then(semanticsModifier),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
          text = label.orEmpty(),
          style = MaterialTheme.typography.bodyMedium,
          color = scheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        GaugeNumber({ number }, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), scheme.onSurface)
      }
      Box(Modifier.fillMaxWidth().height(FluidGaugeDefaults.CapacityHeight + FluidGaugeDefaults.ThresholdMarkReach * 2).drawBehind { drawCapacity(geometry, reading) })
      if (minText.isNotEmpty() || maxText.isNotEmpty()) {
        Row(Modifier.fillMaxWidth()) {
          Text(minText, style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.weight(1f))
          Text(maxText, style = MaterialTheme.typography.labelSmall, color = muted, textAlign = TextAlign.End)
        }
      }
      ThresholdLegend(thresholds, thresholdTexts, colors.second, { reading.active }, muted, dataLabels, centered = false)
    }
  }
}

/** Cio' che il disegno legge: tutto da stati, mai dalla composizione. */
private class GaugeGeometry(
  val style: FluidGaugeStyle,
  val thresholdColors: List<Color>,
  val drawn: Animatable<Float, *>,
  val enter: () -> Float,
  val activeColor: androidx.compose.runtime.State<Color>,
) {
  val fraction: Float get() = drawn.value * enter()
}

/**
 * Il numero del quadrante. **Il punto pronto per `FluidNumericText`** (F8.1, le cifre che scorrono col blur): quando sara'
 * nell'engine, si sostituisce qui il `Text`. Il testo si legge da una lambda: cambia il numero, si ricompone solo lui.
 */
@Composable
private fun GaugeNumber(text: () -> String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
  Text(text = text(), style = style, color = color, maxLines = 1, softWrap = false, modifier = modifier)
}

@Composable
private fun ArcGauge(
  size: Dp,
  geometry: GaugeGeometry,
  reading: FluidGaugeReading,
  thresholds: List<FluidGaugeThreshold>,
  label: String?,
  number: () -> String,
  minText: String,
  maxText: String,
  muted: Color,
) {
  val density = LocalDensity.current
  val scheme = MaterialTheme.colorScheme
  val base = FluidTextStyles.largeNumeric
  // Il numero si riduce con l'arco: 34 sp per 160 dp, mai sotto 16.
  val numberStyle = remember(size, base) {
    val scale = (size / FluidGaugeDefaults.ArcSize).coerceIn(0.45f, 1.4f)
    base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale)
  }
  Box(Modifier.size(size)) {
    Box(Modifier.size(size).drawBehind { drawRound(geometry, reading) })
    Column(
      Modifier.align(Alignment.Center).padding(bottom = size * 0.08f).width(size * 0.7f),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      GaugeNumber(number, numberStyle, scheme.onSurface)
      if (!label.isNullOrEmpty()) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = muted,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
      }
    }
    // Gli estremi, sotto le due estremita' dell'arco.
    val (start, sweep) = fluidGaugeArcAngles(FluidGaugeStyle.Arc)
    val sidePx = with(density) { size.toPx() }
    val radius = sidePx / 2f * (1f - FluidGaugeDefaults.ArcThickness)
    val endWidth = size * 0.34f
    listOf(start to minText, start + sweep to maxText).forEach { (angle, text) ->
      if (text.isEmpty()) return@forEach
      val rad = Math.toRadians(angle.toDouble())
      val x = sidePx / 2f + radius * cos(rad).toFloat()
      val y = sidePx / 2f + radius * sin(rad).toFloat() + sidePx * FluidGaugeDefaults.ArcThickness / 2f + with(density) { 2.dp.toPx() }
      val widthPx = with(density) { endWidth.toPx() }
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = muted,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .width(endWidth)
          .offset { IntOffset((x - widthPx / 2f).roundToInt(), y.roundToInt()) },
      )
    }
  }
}

@Composable
private fun RingGauge(size: Dp, geometry: GaugeGeometry, number: () -> String) {
  val scheme = MaterialTheme.colorScheme
  Box(Modifier.size(size), contentAlignment = Alignment.Center) {
    Box(Modifier.size(size).drawBehind { drawRound(geometry, null) })
    // Il numero dentro, se ci sta: da 32 dp in su, in 10–13 sp.
    if (size >= 32.dp) {
      val fontSize = (size.value * 0.3f).coerceIn(10f, 13f).sp
      GaugeNumber(
        number,
        MaterialTheme.typography.labelSmall.copy(fontSize = fontSize, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        scheme.onSurface,
      )
    }
  }
}

/** L'arco o l'anello: la pista, il valore, i segni delle soglie. */
private fun DrawScope.drawRound(geometry: GaugeGeometry, reading: FluidGaugeReading?) {
  val thickness = size.minDimension * if (geometry.style == FluidGaugeStyle.Ring) FluidGaugeDefaults.RingThickness else FluidGaugeDefaults.ArcThickness
  val (start, sweep) = fluidGaugeArcAngles(geometry.style)
  val diameter = size.minDimension - thickness
  val topLeft = Offset((size.width - diameter) / 2f, (size.minDimension - diameter) / 2f)
  val arcSize = Size(diameter, diameter)
  val color = geometry.activeColor.value
  drawArc(color.copy(alpha = FluidGaugeDefaults.TrackAlpha), start, sweep, false, topLeft, arcSize, style = Stroke(thickness, cap = StrokeCap.Round))
  val fraction = geometry.fraction
  if (fraction > 0f) {
    drawArc(color, start, sweep * fraction, false, topLeft, arcSize, style = Stroke(thickness, cap = StrokeCap.Round))
  }
  if (reading == null) return
  val centre = Offset(topLeft.x + diameter / 2f, topLeft.y + diameter / 2f)
  val reach = FluidGaugeDefaults.ThresholdMarkReach.toPx()
  reading.thresholdFractions.forEachIndexed { i, f ->
    val rad = Math.toRadians((start + sweep * f).toDouble())
    val dx = cos(rad).toFloat()
    val dy = sin(rad).toFloat()
    val inner = diameter / 2f - thickness / 2f - reach
    val outer = diameter / 2f + thickness / 2f + reach
    drawLine(
      geometry.thresholdColors.getOrElse(i) { color },
      Offset(centre.x + dx * inner, centre.y + dy * inner),
      Offset(centre.x + dx * outer, centre.y + dy * outer),
      strokeWidth = FluidGaugeDefaults.ThresholdMarkWidth.toPx(),
      cap = StrokeCap.Round,
    )
  }
}

/** La barra a capacita': la pista, il valore e i segni delle soglie. */
private fun DrawScope.drawCapacity(geometry: GaugeGeometry, reading: FluidGaugeReading) {
  val height = FluidGaugeDefaults.CapacityHeight.toPx()
  val reach = FluidGaugeDefaults.ThresholdMarkReach.toPx()
  val y = reach + height / 2f
  val color = geometry.activeColor.value
  val half = height / 2f
  // Le estremita' tonde stanno dentro la barra: la pista va da half a width - half.
  val left = half
  val right = size.width - half
  drawLine(color.copy(alpha = FluidGaugeDefaults.TrackAlpha), Offset(left, y), Offset(right, y), strokeWidth = height, cap = StrokeCap.Round)
  val fraction = geometry.fraction
  if (fraction > 0f) {
    drawLine(color, Offset(left, y), Offset(left + (right - left) * fraction, y), strokeWidth = height, cap = StrokeCap.Round)
  }
  reading.thresholdFractions.forEachIndexed { i, f ->
    val x = left + (right - left) * f
    drawLine(
      geometry.thresholdColors.getOrElse(i) { color },
      Offset(x, 0f),
      Offset(x, size.height),
      strokeWidth = FluidGaugeDefaults.ThresholdMarkWidth.toPx(),
      cap = StrokeCap.Round,
    )
  }
}

/** Le soglie con nome, col loro valore: quella superata nel suo colore e scritta «oltre». */
@Composable
private fun ThresholdLegend(
  thresholds: List<FluidGaugeThreshold>,
  texts: List<String>,
  colors: List<Color>,
  active: () -> Int,
  muted: Color,
  labels: FluidDataLabels,
  centered: Boolean,
) {
  val named = thresholds.indices.filter { thresholds[it].label != null }
  if (named.isEmpty()) return
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp, if (centered) Alignment.CenterHorizontally else Alignment.Start),
    modifier = if (centered) Modifier else Modifier.fillMaxWidth(),
  ) {
    named.forEach { i ->
      val beyond = active() == i
      val text = labels.gaugeThresholdAt(thresholds[i].label.orEmpty(), texts[i])
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(width = 3.dp, height = 10.dp).drawBehind { drawRect(colors[i]) })
        Text(
          text = if (beyond) labels.gaugeBeyond(text) else text,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (beyond) FontWeight.SemiBold else FontWeight.Normal),
          // Il colore della soglia sta nel segno (3:1); lo scritto resta a 4,5:1.
          color = if (beyond) MaterialTheme.colorScheme.onSurface else muted,
          maxLines = 1,
        )
      }
    }
  }
}
