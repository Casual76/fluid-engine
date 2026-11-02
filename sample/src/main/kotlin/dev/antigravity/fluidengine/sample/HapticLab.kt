package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainConfig
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHapticPatterns
import dev.antigravity.fluidengine.ui.haptics.FluidHapticTrace
import dev.antigravity.fluidengine.ui.haptics.FluidHapticTraceOutcome
import dev.antigravity.fluidengine.ui.haptics.FluidHapticTraceRow
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHapticGrainConfig
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.haptics.ProvideFluidHapticTrace
import dev.antigravity.fluidengine.ui.haptics.TracingFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import kotlin.math.roundToInt

/**
 * Gli strumenti per giudicare l'aptica in mano (3.0): il registro di cio' che vibra e la taratura della
 * grana. Vivono alla radice della galleria ([LocalGalleryTools]), cosi' il registro sente anche i menù e
 * le pagine e non solo la scheda Controlli, e lo stile scelto vale per ogni tasto dell'app.
 *
 * @property trace il registro: [ProvideFluidHapticTrace][dev.antigravity.fluidengine.ui.haptics.ProvideFluidHapticTrace]
 *   lo mette attorno all'aptica del tema.
 * @property overlayVisible il registro in piccolo sopra a tutto, che non prende il tocco: per guardare
 *   un menù che si apre senza dover stare nella scheda del registro.
 * @property grain la grana in uso, letta da `LocalFluidHapticGrainConfig` alla radice.
 */
@Stable
internal class GalleryTools(
  val trace: FluidHapticTrace = FluidHapticTrace(),
) {
  var overlayVisible by mutableStateOf(false)
  var grain by mutableStateOf(FluidHapticGrainConfig.Default)
}

/** Nulla fuori dalla galleria (anteprime, test di un pezzo): le sezioni che li usano non compaiono. */
internal val LocalGalleryTools = staticCompositionLocalOf<GalleryTools?> { null }

/**
 * Gli strumenti di tutto il processo: la galleria e la schermata a parte delle pagine
 * ([PagesDemoActivity]) li condividono, cosi' il registro acceso da Controlli sente anche un indietro
 * partito da un tasto, e la grana scelta vale in entrambe. I test passano i propri.
 */
internal val SharedGalleryTools: GalleryTools by lazy { GalleryTools() }

/**
 * Installa gli strumenti attorno a [content]: la grana scelta (`LocalFluidHapticGrainConfig`), il
 * registro attorno all'aptica del tema ([ProvideFluidHapticTrace]) e [LocalGalleryTools] per le
 * sezioni che li mostrano. **Va dentro il `FluidTheme`**: e' lui a installare l'aptica.
 */
@Composable
internal fun ProvideGalleryTools(tools: GalleryTools, content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalGalleryTools provides tools,
    // Letta qui, in composizione: cambiarla ricompone tutto cio' che sta sotto (e' una variabile
    // statica), ed e' per questo che i cursori applicano solo al rilascio.
    LocalFluidHapticGrainConfig provides tools.grain,
  ) {
    ProvideFluidHapticTrace(tools.trace, content)
  }
}

/** Il registro in piccolo, in alto a destra sotto la barra di stato, se e' acceso. Non prende il tocco. */
@Composable
internal fun BoxScope.GalleryToolsOverlay(tools: GalleryTools) {
  if (!tools.overlayVisible) return
  HapticTraceOverlay(
    trace = tools.trace,
    // Sotto la barra del titolo (56 dp), cosi' non copre i tasti di vetro in alto.
    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 8.dp),
  )
}

/**
 * I numeri della grana che i cursori possono muovere, e come si tengono coerenti. Pura: la prova sta
 * sul computer.
 *
 * La regola che conta e' una: **lo spegnimento sta sempre sotto la partenza**, di almeno
 * [Hysteresis]. Una soglia sola farebbe accendere e spegnere la grana a ogni evento di un dito che
 * oscilla attorno a essa (vedi [FluidHapticGrainConfig.releaseDpPerSec]).
 */
internal object GrainTuning {
  val OnsetRange = 150f..1200f
  val SpacingRange = 3f..24f
  val IntensityRange = 0.15f..1f
  const val ReleaseMin = 80f
  const val Hysteresis = 40f

  fun withStyle(config: FluidHapticGrainConfig, style: FluidHapticGrainStyle): FluidHapticGrainConfig =
    config.copy(style = style)

  /** Alzare o abbassare la partenza trascina con se' lo spegnimento, se no sarebbe sopra. */
  fun withOnset(config: FluidHapticGrainConfig, value: Float): FluidHapticGrainConfig {
    val onset = value.coerceIn(OnsetRange)
    return config.copy(
      onsetDpPerSec = onset,
      releaseDpPerSec = minOf(config.releaseDpPerSec, onset - Hysteresis).coerceAtLeast(ReleaseMin),
    )
  }

  fun withRelease(config: FluidHapticGrainConfig, value: Float): FluidHapticGrainConfig =
    config.copy(releaseDpPerSec = value.coerceIn(ReleaseMin, config.onsetDpPerSec - Hysteresis))

  fun withSpacing(config: FluidHapticGrainConfig, value: Float): FluidHapticGrainConfig =
    config.copy(spacingDp = value.coerceIn(SpacingRange))

  /** La forza e' il massimo; il minimo non lo supera mai (un granello non e' piu' forte del massimo). */
  fun withIntensity(config: FluidHapticGrainConfig, value: Float): FluidHapticGrainConfig {
    val max = value.coerceIn(IntensityRange)
    return config.copy(maxIntensity = max, minIntensity = minOf(config.minIntensity, max))
  }

  /** I numeri di serie, tenendo lo stile scelto: si ripristina la taratura, non il confronto. */
  fun reset(config: FluidHapticGrainConfig): FluidHapticGrainConfig =
    FluidHapticGrainConfig.Default.copy(style = config.style)
}

/**
 * Il registro aptico e la taratura della grana, nella pagina del tocco: due sezioni, sotto i tasti
 * che si provano. Il registro per primo, accanto ai tasti: dopo ogni gesto lo sguardo va li'.
 */
internal fun LazyListScope.hapticLabItems() {
  item(key = "haptic-log-header") {
    FluidSectionHeader(
      title = "Registro aptico",
      detail = "Cosa viene chiesto al vibratore, con i millisecondi dal colpo prima: sotto i " +
        "${FluidHapticPatterns.CoalesceWindowMillis} ms è lo stesso momento.",
    )
  }
  item(key = "haptic-log") { HapticLogSection() }

  item(key = "haptic-grain-header") {
    FluidSectionHeader(
      title = "Grana",
      detail = "Lo stile vale per ogni tasto dell'app, non solo per quelli di questa pagina.",
    )
  }
  item(key = "haptic-grain") { GrainTuningSection() }
}

/**
 * Per i controlli degli strumenti: l'aptica vera senza il registro, cosi' toccare "Svuota" non scrive
 * le proprie tre voci nel registro che sta svuotando. Quello che suona resta uguale.
 */
@Composable
private fun UntracedHaptics(content: @Composable () -> Unit) {
  val current = LocalFluidHaptics.current
  val plain = (current as? TracingFluidHaptics)?.delegate ?: current
  CompositionLocalProvider(LocalFluidHaptics provides plain, content = content)
}

@Composable
private fun HapticLogSection() {
  val tools = LocalGalleryTools.current ?: return
  val trace = tools.trace
  val rows = trace.rows()

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    UntracedHaptics {
      FluidListGroup(glass = true) {
        SwitchRow("Registra", trace.recording) { trace.recording = it }
        FluidListDivider()
        SwitchRow("Mostra sopra a tutto", tools.overlayVisible) { tools.overlayVisible = it }
      }
    }
    FluidCard(glass = true) {
      if (rows.isEmpty()) {
        Text(
          text = "Niente ancora: tocca o trascina un tasto qui sopra.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("haptic-log-empty"),
        )
      } else {
        Column(
          modifier = Modifier.testTag("haptic-log-rows"),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          rows.forEach { HapticLogRow(it) }
        }
      }
      UntracedHaptics {
        FluidButton(
          text = "Svuota",
          onClick = trace::clear,
          style = FluidButtonStyle.Tinted,
          size = FluidButtonSize.Small,
        )
      }
      Text(
        text = "Una riga barrata non suona: la rete dei ${FluidHapticPatterns.CoalesceWindowMillis} ms l'ha " +
          "fusa con il colpo prima, o il motore l'ha scartata per essere troppo vicina. È una stima " +
          "col motore di serie, non una misura del vibratore.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun HapticLogRow(row: FluidHapticTraceRow) {
  val played = row.outcome == FluidHapticTraceOutcome.Played
  Row(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      text = hapticDeltaText(row),
      style = MaterialTheme.typography.labelMedium,
      fontFamily = FontFamily.Monospace,
      // Un momento nuovo (almeno la finestra dopo il colpo prima) si vede dal colore: e' il numero
      // che dice "questa e' un'altra vibrazione", e gli altri sono "la stessa, detta di nuovo".
      color = if (row.startsMoment) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.End,
      modifier = Modifier.width(72.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = row.text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (played) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (played) null else TextDecoration.LineThrough,
      )
      row.detail?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

/** "+412 ms", o un trattino per la prima riga, che non ha un colpo prima. */
internal fun hapticDeltaText(row: FluidHapticTraceRow): String = row.deltaMillis?.let { "+$it ms" } ?: "—"

/** Una riga in una riga sola, per il registro piccolo: `+3 Tap (fuso)`, `+210 grana Fluid ×14`. */
internal fun hapticOverlayLine(row: FluidHapticTraceRow): String {
  val suffix = when (row.outcome) {
    FluidHapticTraceOutcome.Played -> ""
    FluidHapticTraceOutcome.Merged -> " (fuso)"
    FluidHapticTraceOutcome.Dropped -> " (scartato)"
  }
  return "${row.deltaMillis?.let { "+$it" } ?: "—"} ${row.text}$suffix"
}

/**
 * Il registro in piccolo, sopra a tutto: sei righe su fondo scuro, che non prendono il tocco. Si
 * accende dalla scheda Controlli e resta mentre si va a provare un menù o una pagina.
 */
@Composable
internal fun HapticTraceOverlay(trace: FluidHapticTrace, modifier: Modifier = Modifier) {
  val rows = trace.rows(OverlayRows)
  Column(
    modifier = modifier
      .testTag("haptic-overlay")
      .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(12.dp))
      .widthIn(min = 150.dp, max = 250.dp)
      .padding(horizontal = 10.dp, vertical = 7.dp),
    verticalArrangement = Arrangement.spacedBy(1.dp),
  ) {
    Text(text = "registro aptico", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f))
    if (rows.isEmpty()) {
      Text(text = "—", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.55f))
    }
    rows.forEach { row ->
      Text(
        text = hapticOverlayLine(row),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = Color.White.copy(alpha = if (row.outcome == FluidHapticTraceOutcome.Played) 1f else 0.5f),
      )
    }
  }
}

private const val OverlayRows = 6

@Composable
private fun GrainTuningSection() {
  val tools = LocalGalleryTools.current ?: return
  val config = tools.grain

  FluidCard(glass = true) {
    UntracedHaptics {
      FluidSegmentedControl(
        options = FluidHapticGrainStyle.entries.toList(),
        selected = config.style,
        onSelect = { tools.grain = GrainTuning.withStyle(tools.grain, it) },
        label = { if (it == FluidHapticGrainStyle.LibrePods) "LibrePods" else "Fluid" },
        modifier = Modifier.testTag("grain-style"),
      )
    }
    if (config.style == FluidHapticGrainStyle.LibrePods) {
      Text(
        text = "Come LibrePods: un tick di sistema a ogni evento del dito che si sposta di più di " +
          "${kotlin.math.sqrt(config.librePodsSquaredPx).roundToInt()} px. Piano non suona niente, " +
          "veloce suona a ogni evento, senza tetto. Non c'è niente da tarare.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      Text(
        text = "Come l'engine: la velocità è misurata, la partenza ha un'isteresi e i granelli sono " +
          "irregolari. I cursori si applicano al rilascio, non mentre li muovi.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      UntracedHaptics {
        TuningSlider(
          label = "Parte sopra",
          value = config.onsetDpPerSec,
          range = GrainTuning.OnsetRange,
          format = { "${it.roundToInt()} dp/s" },
          tag = "grain-onset",
        ) { tools.grain = GrainTuning.withOnset(tools.grain, it) }
        TuningSlider(
          label = "Si spegne sotto",
          value = config.releaseDpPerSec,
          range = GrainTuning.ReleaseMin..(config.onsetDpPerSec - GrainTuning.Hysteresis),
          format = { "${it.roundToInt()} dp/s" },
          tag = "grain-release",
        ) { tools.grain = GrainTuning.withRelease(tools.grain, it) }
        TuningSlider(
          label = "Passo",
          value = config.spacingDp,
          range = GrainTuning.SpacingRange,
          format = { "${(it * 10).roundToInt() / 10f} dp" },
          tag = "grain-spacing",
        ) { tools.grain = GrainTuning.withSpacing(tools.grain, it) }
        TuningSlider(
          label = "Forza massima",
          value = config.maxIntensity,
          range = GrainTuning.IntensityRange,
          format = { "${(it * 100).roundToInt()}%" },
          tag = "grain-intensity",
        ) { tools.grain = GrainTuning.withIntensity(tools.grain, it) }
      }
    }
    UntracedHaptics {
      FluidButton(
        text = "Ripristina i numeri",
        onClick = { tools.grain = GrainTuning.reset(tools.grain) },
        style = FluidButtonStyle.Tinted,
        size = FluidButtonSize.Small,
        modifier = Modifier.testTag("grain-reset"),
      )
    }
  }
}

/**
 * Un cursore con la sua etichetta e il suo valore. Tiene una **bozza** mentre il dito lo muove e
 * applica solo al rilascio: la grana e' una variabile statica della composizione, e cambiarla a ogni
 * passo del cursore farebbe ricomporre l'intera galleria sotto il dito che la sta tarando.
 */
@Composable
private fun TuningSlider(
  label: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  format: (Float) -> String,
  tag: String,
  onCommit: (Float) -> Unit,
) {
  var draft by remember(value) { mutableFloatStateOf(value) }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyLarge)
    Text(
      text = format(draft),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
  FluidSlider(
    value = draft.coerceIn(range),
    onValueChange = { draft = it },
    modifier = Modifier.testTag(tag),
    valueRange = range,
    onValueChangeFinished = { onCommit(draft) },
    contentDescription = label,
  )
}
