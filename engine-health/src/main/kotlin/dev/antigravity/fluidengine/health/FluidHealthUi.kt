/*
 * engine-health (4.0, F8.26): gli anelli del giorno, le card con la fonte, la settimana e le parole. Codice del Fluid
 * Engine.
 */
package dev.antigravity.fluidengine.health

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.health.icons.DirectionsRun
import dev.antigravity.fluidengine.health.icons.Footprint
import dev.antigravity.fluidengine.health.icons.MonitorWeight
import dev.antigravity.fluidengine.health.icons.WaterDrop
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidIdentityColors
import dev.antigravity.fluidengine.ui.fluid.FluidNumericText
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.icons.Bedtime
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.theme.FluidCard
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** I tag delle prove. */
object FluidHealthTags {
  const val Rings: String = "fluid-health:anelli"
  const val Week: String = "fluid-health:settimana"
  fun card(metric: FluidHealthMetric): String = "fluid-health:card:${metric.id}"
  fun permission(metric: FluidHealthMetric): String = "fluid-health:permesso:${metric.id}"
}

/**
 * Le parole della salute, per le card, TalkBack e le righe dei permessi. Italiano di serie; [English] per le app in
 * inglese. Le frasi dicono **fatti**, mai cause: «Nessun dato dalle 14:10», non «orologio scarico».
 */
@Immutable
data class FluidHealthLabels(
  val metricName: (FluidHealthMetric) -> String = { m ->
    when (m) {
      FluidHealthMetric.Steps -> "Passi"
      FluidHealthMetric.Sleep -> "Sonno"
      FluidHealthMetric.HeartRate -> "Battito"
      FluidHealthMetric.Exercise -> "Allenamenti"
      FluidHealthMetric.Weight -> "Peso"
      FluidHealthMetric.Hydration -> "Acqua"
      else -> m.id
    }
  },
  val noData: String = "Nessun dato",
  val noDataToday: String = "Nessun dato oggi",
  val from: (sources: String) -> String = { "Da $it" },
  val fromMany: (count: Int) -> String = { "Da $it fonti" },
  val gap: (from: String, to: String) -> String = { a, b -> "Niente dalle $a alle $b" },
  val gapUntilNow: (from: String) -> String = { "Niente dalle $it" },
  val ofGoal: (value: String, goal: String) -> String = { v, g -> "$v su $g" },
  val read: String = "Lettura",
  val write: String = "Scrittura",
  val allowed: String = "Consentito",
  val notAllowed: String = "Non consentito",
  val readOnly: String = "Solo lettura",
  val writeOnly: String = "Solo scrittura",
  val unavailable: String = "Health Connect non c'e' su questo telefono",
  val needsUpdate: String = "Health Connect va aggiornato",
  val openHealthConnect: String = "Apri Health Connect",
  val askAgain: String = "Consenti in Health Connect",
  val watch: String = "orologio",
  val phone: String = "telefono",
  val ring: String = "anello",
  val scale: String = "bilancia",
  val beatsPerMinute: String = "bpm",
  val kilograms: String = "kg",
  val liters: String = "l",
  val steps: String = "passi",
  val minutes: String = "min",
) {
  companion object {
    val English: FluidHealthLabels = FluidHealthLabels(
      metricName = { m ->
        when (m) {
          FluidHealthMetric.Steps -> "Steps"; FluidHealthMetric.Sleep -> "Sleep"; FluidHealthMetric.HeartRate -> "Heart rate"
          FluidHealthMetric.Exercise -> "Workouts"; FluidHealthMetric.Weight -> "Weight"; FluidHealthMetric.Hydration -> "Water"
          else -> m.id
        }
      },
      noData = "No data", noDataToday = "No data today", from = { "From $it" }, fromMany = { "From $it sources" },
      gap = { a, b -> "Nothing from $a to $b" }, gapUntilNow = { "Nothing since $it" }, ofGoal = { v, g -> "$v of $g" },
      read = "Read", write = "Write", allowed = "Allowed", notAllowed = "Not allowed", readOnly = "Read only",
      writeOnly = "Write only", unavailable = "Health Connect isn't available on this phone",
      needsUpdate = "Health Connect needs an update", openHealthConnect = "Open Health Connect",
      askAgain = "Allow in Health Connect", watch = "watch", phone = "phone", ring = "ring", scale = "scale",
      steps = "steps",
    )
  }

  /** «Da Sounds», «Da Fitbit · orologio», «Da Sounds e Fitbit», «Da 3 fonti». */
  fun sourceLine(sources: List<FluidHealthSource>, locale: Locale = Locale.getDefault()): String? {
    val distinct = sources.distinctBy { it.packageName }
    return when {
      distinct.isEmpty() -> null
      distinct.size == 1 -> {
        val s = distinct[0]
        val device = deviceWord(s.device)
        from(if (device != null) "${s.label} · $device" else s.label)
      }
      distinct.size == 2 -> from(FluidFormat.list(distinct.map { it.label }, locale))
      else -> fromMany(distinct.size)
    }
  }

  /** La parola dell'apparecchio, o null se non si sa. */
  fun deviceWord(kind: FluidHealthDeviceKind): String? = when (kind) {
    FluidHealthDeviceKind.Watch -> watch
    FluidHealthDeviceKind.Phone -> phone
    FluidHealthDeviceKind.Ring -> ring
    FluidHealthDeviceKind.Scale -> scale
    else -> null
  }

  /** Il buco piu' lungo, a parole: «Niente dalle 14:10 alle 18:30» (o «dalle 14:10» se arriva fino ad adesso). */
  fun gapLine(gaps: List<FluidHealthGap>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String? {
    val longest = gaps.maxByOrNull { it.duration } ?: return null
    val a = FluidFormat.time(longest.start, locale, zone)
    return if (Duration.between(longest.end, now).abs() < Duration.ofMinutes(1)) gapUntilNow(a)
    else gap(a, FluidFormat.time(longest.end, locale, zone))
  }
}

/**
 * I colori delle misure: dalla palette d'identita' dell'engine (contrasto gia' provato), uno per misura, chiaro e scuro.
 * Non quelli di Salute: i passi arancio, il sonno indaco, il battito rosa, gli allenamenti verdi, il peso viola, l'acqua
 * blu.
 */
object FluidHealthColors {
  fun of(metric: FluidHealthMetric, dark: Boolean): Color = when (metric) {
    FluidHealthMetric.Steps -> FluidIdentityColors.colorAt(1)
    FluidHealthMetric.Sleep -> FluidIdentityColors.colorAt(8)
    FluidHealthMetric.HeartRate -> FluidIdentityColors.colorAt(11)
    FluidHealthMetric.Exercise -> FluidIdentityColors.colorAt(4)
    FluidHealthMetric.Weight -> FluidIdentityColors.colorAt(9)
    FluidHealthMetric.Hydration -> FluidIdentityColors.colorAt(7)
    else -> FluidIdentityColors.colorFor(metric.id)
  }.forTheme(dark)

  /** Il simbolo pieno di una misura (quello delle card). */
  fun icon(metric: FluidHealthMetric): ImageVector = when (metric) {
    FluidHealthMetric.Steps -> FluidIcons.Filled.Footprint
    FluidHealthMetric.Sleep -> FluidIcons.Bedtime
    FluidHealthMetric.HeartRate -> FluidIcons.Filled.Favorite
    FluidHealthMetric.Exercise -> FluidIcons.Filled.DirectionsRun
    FluidHealthMetric.Weight -> FluidIcons.Filled.MonitorWeight
    FluidHealthMetric.Hydration -> FluidIcons.Filled.WaterDrop
    else -> FluidIcons.Filled.Favorite
  }
}

/**
 * Un anello del giorno: quanto si e' fatto ([value]) rispetto all'obiettivo ([goal]), col nome e il testo del valore.
 * [color] `Unspecified` prende i colori di serie degli anelli (l'accento e due tinte dell'engine).
 */
@Immutable
data class FluidHealthRing(
  val label: String,
  val value: Double,
  val goal: Double,
  val valueText: String,
  val goalText: String,
  val color: Color = Color.Unspecified,
) {
  init {
    require(goal > 0) { "Un obiettivo e' piu' di zero" }
  }

  /** Quanto dell'obiettivo, da 0 (anche oltre 1). */
  val fraction: Double get() = (value / goal).coerceAtLeast(0.0)

  override fun toString(): String = "FluidHealthRing($label, ***)"
}

/**
 * Le misure degli anelli. Il disegno e' dell'engine e **non** quello di Fitness: archi **aperti** in basso (300°, come il
 * quadrante di `FluidGauge`), a tinta piena senza sfumature e senza frecce, con la traccia chiara sotto; oltre
 * l'obiettivo l'arco resta pieno e la percentuale lo dice (niente secondo giro sovrapposto). A occhio, 26/11/2026.
 */
object FluidHealthRingDefaults {
  val Diameter: Dp = 132.dp
  val Stroke: Dp = 13.dp
  val Gap: Dp = 3.dp
  const val SweepDegrees: Float = 300f
  const val TrackAlpha: Float = 0.18f
}

/**
 * Gli anelli del giorno: uno, due o tre archi concentrici aperti in basso. Quando i valori arrivano (o cambiano) gli
 * archi crescono con una molla dall'ultimo valore; col movimento ridotto sono gia' li'. Un anello fermo non ridisegna.
 * TalkBack legge ogni anello: «Passi, 7 412 su 10 000, 74%».
 */
@Composable
fun FluidHealthRings(
  rings: List<FluidHealthRing>,
  modifier: Modifier = Modifier,
  diameter: Dp = FluidHealthRingDefaults.Diameter,
  stroke: Dp = FluidHealthRingDefaults.Stroke,
  labels: FluidHealthLabels = FluidHealthLabels(),
  center: (@Composable () -> Unit)? = null,
) {
  require(rings.size in 1..3) { "Da uno a tre anelli" }
  val colors = ringColors(rings)
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val progress = remember(rings.size) { List(rings.size) { Animatable(0f) } }
  rings.forEachIndexed { i, ring ->
    val target = min(ring.fraction, 1.0).toFloat()
    LaunchedEffect(target, reduced) {
      if (reduced) progress[i].snapTo(target) else progress[i].animateTo(target, spring(dampingRatio = 0.9f, stiffness = 120f))
    }
  }
  val probe = LocalFluidHealthRingsProbe.current
  val description = rings.joinToString(". ") { r ->
    "${r.label}, ${labels.ofGoal(r.valueText, r.goalText)}, ${FluidFormat.percent(r.fraction)}"
  }
  Box(
    modifier
      .size(diameter)
      .testTag(FluidHealthTags.Rings)
      .clearAndSetSemantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(diameter)) {
      val w = stroke.toPx()
      val gap = FluidHealthRingDefaults.Gap.toPx()
      val start = 90f + (360f - FluidHealthRingDefaults.SweepDegrees) / 2f
      rings.forEachIndexed { i, _ ->
        val inset = w / 2f + i * (w + gap)
        val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        if (arcSize.width <= 0f) return@forEachIndexed
        val topLeft = Offset(inset, inset)
        val color = colors[i]
        drawArc(color.copy(alpha = FluidHealthRingDefaults.TrackAlpha), start, FluidHealthRingDefaults.SweepDegrees, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        val p = progress[i].value
        if (i == 0) probe?.invoke(progress.map { it.value })
        if (p > 0f) drawArc(color, start, FluidHealthRingDefaults.SweepDegrees * p, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
      }
    }
    center?.invoke()
  }
}

/** Per le prove: riceve le frazioni degli archi a ogni disegno (nell'app non c'e', e non costa niente). */
internal val LocalFluidHealthRingsProbe = staticCompositionLocalOf<((List<Float>) -> Unit)?> { null }

@Composable
private fun ringColors(rings: List<FluidHealthRing>): List<Color> {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val defaults = listOf(MaterialTheme.colorScheme.primary, FluidIdentityColors.colorAt(5).forTheme(dark), FluidIdentityColors.colorAt(1).forTheme(dark))
  return rings.mapIndexed { i, r -> if (r.color == Color.Unspecified) defaults[i] else r.color }
}

/**
 * La card del giorno: gli anelli a sinistra e, a destra, una riga per anello col pallino del colore, il nome e
 * «7 412 su 10 000» (il numero scorre, sfocato, quando arriva: e' l'app che lo cambia). Sotto, la fonte.
 */
@Composable
fun FluidHealthDayCard(
  rings: List<FluidHealthRing>,
  modifier: Modifier = Modifier,
  title: String? = null,
  sources: List<FluidHealthSource> = emptyList(),
  labels: FluidHealthLabels = FluidHealthLabels(),
  glass: Boolean = false,
) {
  val colors = ringColors(rings)
  FluidCard(modifier = modifier, glass = glass) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
      title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 12.dp)) }
      Row(verticalAlignment = Alignment.CenterVertically) {
        FluidHealthRings(rings, labels = labels)
        Spacer(Modifier.width(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.clearAndSetSemantics { }) {
          rings.forEachIndexed { i, r ->
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(9.dp)) { drawCircle(colors[i]) }
                Spacer(Modifier.width(6.dp))
                Text(r.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Row(verticalAlignment = Alignment.Bottom) {
                FluidNumericText(text = r.valueText, value = r.value, style = FluidTextStyles.numeric, color = colors[i])
                Text(" / ${r.goalText}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
      labels.sourceLine(sources)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
      }
    }
  }
}

/**
 * La card di una misura, come le card di Salute: il simbolo e il nome nel colore della misura, l'ora a destra, il valore
 * grande con l'unita', e sotto **la fonte** («Da Sounds · telefono») e, se ci sono, i buchi («Niente dalle 14:10 alle
 * 18:30»). Senza valore dice «Nessun dato», mai «0». Il valore che arriva scorre sfocato ([FluidNumericText]).
 *
 * @param value il testo del valore («7 412», «7 h 12 min»); null: nessun dato.
 * @param numeric il valore come numero, per lo scorrimento delle cifre.
 */
@Composable
fun FluidHealthCard(
  metric: FluidHealthMetric,
  value: String?,
  modifier: Modifier = Modifier,
  numeric: Double = 0.0,
  unit: String? = null,
  time: String? = null,
  sources: List<FluidHealthSource> = emptyList(),
  note: String? = null,
  labels: FluidHealthLabels = FluidHealthLabels(),
  glass: Boolean = false,
  onClick: (() -> Unit)? = null,
  chart: (@Composable () -> Unit)? = null,
) {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val tint = FluidHealthColors.of(metric, dark)
  val name = labels.metricName(metric)
  val sourceLine = labels.sourceLine(sources)
  val description = listOfNotNull(name, value?.let { listOfNotNull(it, unit).joinToString(" ") } ?: labels.noData, time, sourceLine, note).joinToString(", ")
  FluidCard(modifier = modifier, glass = glass, onClick = onClick) {
    Column(
      Modifier
        .fillMaxWidth()
        .testTag(FluidHealthTags.card(metric))
        .clearAndSetSemantics { contentDescription = description }
        .padding(16.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        FluidIcon(FluidHealthColors.icon(metric), contentDescription = null, tint = tint, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = tint, modifier = Modifier.weight(1f))
        time?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
      }
      Spacer(Modifier.height(10.dp))
      if (value == null) {
        Text(labels.noData, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        Row(verticalAlignment = Alignment.Bottom) {
          FluidNumericText(text = value, value = numeric, style = FluidTextStyles.largeNumeric, color = MaterialTheme.colorScheme.onSurface)
          unit?.let {
            Text(" $it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
          }
        }
      }
      chart?.let {
        Spacer(Modifier.height(12.dp))
        it()
      }
      if (sourceLine != null || note != null) Spacer(Modifier.height(8.dp))
      note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
      sourceLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
  }
}

/** Un giorno della settimana: l'etichetta («L») e il valore, o null se quel giorno non ha dati (niente barra, un trattino). */
@Immutable
data class FluidHealthWeekDay(val label: String, val value: Double?, val valueText: String? = null) {
  override fun toString(): String = "FluidHealthWeekDay($label, ***)"
}

/**
 * La settimana di una misura: sette barre dal basso, piene fino all'obiettivo ([goal], la linea tratteggiata), e per un
 * giorno **senza dati** nessuna barra ma un trattino: un giorno vuoto non e' un giorno a zero. TalkBack legge giorno per
 * giorno.
 */
@Composable
fun FluidHealthWeek(
  days: List<FluidHealthWeekDay>,
  metric: FluidHealthMetric,
  modifier: Modifier = Modifier,
  goal: Double? = null,
  height: Dp = 72.dp,
  labels: FluidHealthLabels = FluidHealthLabels(),
) {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val tint = FluidHealthColors.of(metric, dark)
  val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  val ink = MaterialTheme.colorScheme.onSurfaceVariant
  val max = listOfNotNull(goal, days.mapNotNull { it.value }.maxOrNull()).maxOrNull()?.takeIf { it > 0 } ?: 1.0
  val description = days.joinToString(", ") { d -> "${d.label} ${d.valueText ?: d.value?.let { FluidFormat.number(it) } ?: labels.noData}" }
  Column(modifier.testTag(FluidHealthTags.Week).clearAndSetSemantics { contentDescription = "${labels.metricName(metric)}: $description" }) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
      val n = days.size.coerceAtLeast(1)
      val slot = size.width / n
      val barW = min(slot * 0.46f, 14.dp.toPx())
      days.forEachIndexed { i, d ->
        val cx = slot * (i + 0.5f)
        val left = cx - barW / 2f
        drawRoundRect(track, Offset(left, 0f), Size(barW, size.height), androidx.compose.ui.geometry.CornerRadius(barW / 2f))
        val v = d.value
        if (v == null) {
          // Il trattino del giorno senza dati, a meta' altezza.
          drawLine(ink.copy(alpha = 0.6f), Offset(cx - barW / 2f, size.height / 2f), Offset(cx + barW / 2f, size.height / 2f), 2.dp.toPx(), StrokeCap.Round)
        } else if (v > 0) {
          val h = (v / max).toFloat().coerceIn(0f, 1f) * size.height
          drawRoundRect(tint, Offset(left, size.height - h), Size(barW, h.coerceAtLeast(barW)), androidx.compose.ui.geometry.CornerRadius(barW / 2f))
        }
      }
      goal?.let { g ->
        val y = size.height - (g / max).toFloat().coerceIn(0f, 1f) * size.height
        drawLine(ink.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
      }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
      days.forEach { d ->
        Text(d.label, style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
      }
    }
  }
}

/**
 * Le parole dei valori: i passi interi («7 412»), il sonno in ore e minuti («7 h 12 min»), il battito («58»), il peso con
 * un decimale («72,4»), l'acqua in litri con un decimale («1,2»). Nella lingua del telefono.
 */
object FluidHealthFormat {
  fun steps(value: Double, locale: Locale = Locale.getDefault()): String = FluidFormat.number(value.roundToInt().toDouble(), locale, 0)
  fun sleep(duration: Duration, locale: Locale = Locale.getDefault()): String = FluidFormat.duration(duration, locale, maxUnits = 2)
  fun heart(value: Double, locale: Locale = Locale.getDefault()): String = FluidFormat.number(value.roundToInt().toDouble(), locale, 0)
  fun weight(kilograms: Double, locale: Locale = Locale.getDefault()): String = FluidFormat.number(kilograms, locale, 1)
  fun water(milliliters: Double, locale: Locale = Locale.getDefault()): String = FluidFormat.number(milliliters / 1000.0, locale, 1)

  /** Le letture di una settimana per [FluidHealthWeek], con le iniziali dei giorni («L», «M», …) dalla lingua. */
  fun week(readings: List<FluidHealthReading>, first: LocalDate, locale: Locale = Locale.getDefault(), text: (Double) -> String = { steps(it, locale) }): List<FluidHealthWeekDay> =
    readings.mapIndexed { i, r ->
      val day = first.plusDays(i.toLong())
      val label = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale).uppercase(locale)
      when (r) {
        FluidHealthReading.NoData -> FluidHealthWeekDay(label, null)
        is FluidHealthReading.Value -> FluidHealthWeekDay(label, r.value, text(r.value))
      }
    }
}
