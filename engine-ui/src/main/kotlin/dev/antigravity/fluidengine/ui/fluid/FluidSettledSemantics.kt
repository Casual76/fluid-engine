package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * F7.24 · La semantica di qualcosa che il dito cambia (il valore di un cursore, di un righello, di una ruota, il numero
 * scritto accanto): **ferma finché un dito muove un valore**, aggiornata quando il gesto finisce (il dito alzato e
 * l'inerzia ferma) o, in un gesto lungo, al più una volta ogni [FluidSettledSemanticsLook.SlowPaceMillis].
 *
 * Perché: con un servizio di accessibilità acceso (TalkBack, ma anche «Collegamento a Windows» o un'app che legge lo
 * schermo) ogni semantica che cambia fa ricontrollare a Compose tutta la pagina (`checkForSemanticsChanges`, 1,3–4,4 ms
 * sul thread dell'interfaccia del Tab S9): a ogni tacca di un righello, fuori dal fotogramma. TalkBack legge comunque il
 * valore quando il gesto finisce, e un valore cambiato da TalkBack («aumenta», «imposta») o dall'app arriva subito: lì
 * nessun dito muove niente.
 *
 * Si usa come `semantics { }`: [properties] può leggere stati (non ricompone niente); le letture si osservano qui. Il
 * gesto lo dicono i controlli dell'engine (`FluidFingerDrive`): un `Modifier` di un'app che muove un valore col suo
 * `pointerInput` non lo dice, e la sua semantica resta immediata.
 */
fun Modifier.fluidSettledSemantics(
  mergeDescendants: Boolean = false,
  properties: SemanticsPropertyReceiver.() -> Unit,
): Modifier = this then FluidSettledSemanticsElement(mergeDescendants, properties)

/** Le misure di [fluidSettledSemantics]. */
object FluidSettledSemanticsLook {
  /**
   * In un gesto lungo, la semantica si aggiorna al più ogni secondo (a occhio, F7.24): un controllo della pagina al
   * secondo non si vede, e chi esplora col dito giù sente che il valore cambia.
   */
  const val SlowPaceMillis: Long = 1_000L
}

/**
 * I contatori delle prove di [fluidSettledSemantics] (F7.24): **non sono API per le app**, come
 * `FluidRulerDialDiagnostics`. [publishes] conta le semantiche date al sistema, [deferred] quelle tenute ferme perché
 * un dito muoveva un valore.
 */
object FluidSettledSemanticsDiagnostics {
  var publishes: Int = 0
    internal set
  var deferred: Int = 0
    internal set

  fun resetForTesting() {
    publishes = 0
    deferred = 0
  }
}

private data class FluidSettledSemanticsElement(
  val mergeDescendants: Boolean,
  val properties: SemanticsPropertyReceiver.() -> Unit,
) : ModifierNodeElement<FluidSettledSemanticsNode>() {
  override fun create() = FluidSettledSemanticsNode(mergeDescendants, properties)

  override fun update(node: FluidSettledSemanticsNode) {
    node.mergeDescendants = mergeDescendants
    node.properties = properties
    // Un chiamante che si ricompone a ogni valore passa una lambda nuova: conta come un cambio letto.
    node.refresh(force = false)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "fluidSettledSemantics"
    properties["mergeDescendants"] = mergeDescendants
  }
}

internal class FluidSettledSemanticsNode(
  var mergeDescendants: Boolean,
  var properties: SemanticsPropertyReceiver.() -> Unit,
) : Modifier.Node(), SemanticsModifierNode, ObserverModifierNode {
  /** Quello che TalkBack vede: una copia, non le letture (così Compose non la ricalcola a ogni cambio). */
  private var shown = SemanticsConfiguration()
  private var shownPrint: List<Pair<String, Any?>> = emptyList()
  private var latest: SemanticsConfiguration? = null
  private var pending = false
  private var pace: Job? = null

  override val shouldMergeDescendantSemantics: Boolean get() = mergeDescendants

  override fun onAttach() {
    refresh(force = true)
  }

  override fun onDetach() {
    pace = null
    pending = false
    latest = null
  }

  override fun onObservedReadsChanged() = refresh(force = false)

  /** Rilegge [properties] (osservando le letture) e decide se dare subito il risultato o aspettare la fine del gesto. */
  fun refresh(force: Boolean) {
    if (!isAttached) return
    val next = SemanticsConfiguration()
    observeReads {
      next.properties()
      // La fine del gesto risveglia il nodo: è uno stato, letto qui.
      FluidFingerDrive.idleGeneration
    }
    val print = next.fingerprint()
    when {
      force -> publish(next, print)
      !pending && print == shownPrint -> shown = next // Solo le azioni nuove (lambde): niente da dire al sistema.
      FluidFingerDrive.holding -> {
        latest = next
        if (!pending) FluidSettledSemanticsDiagnostics.deferred++
        pending = true
        schedulePace()
      }
      else -> publish(next, print)
    }
  }

  private fun publish(config: SemanticsConfiguration, print: List<Pair<String, Any?>>) {
    shown = config
    shownPrint = print
    pending = false
    latest = null
    FluidSettledSemanticsDiagnostics.publishes++
    invalidateSemantics()
  }

  private fun schedulePace() {
    if (pace?.isActive == true) return
    pace = coroutineScope.launch {
      delay(FluidSettledSemanticsLook.SlowPaceMillis)
      val l = latest
      if (pending && l != null && isAttached) publish(l, l.fingerprint())
    }
  }

  override fun SemanticsPropertyReceiver.applySemantics() {
    for ((key, value) in shown) {
      @Suppress("UNCHECKED_CAST")
      this[key as SemanticsPropertyKey<Any?>] = value
    }
  }
}

/**
 * Cosa conta per TalkBack in una semantica: i valori, non le azioni (lambde nuove a ogni lettura). Le azioni su misura
 * contano per l'etichetta.
 */
private fun SemanticsConfiguration.fingerprint(): List<Pair<String, Any?>> = mapNotNull { (key, value) ->
  when (value) {
    is AccessibilityAction<*> -> key.name to value.label
    is Function<*> -> null
    is List<*> -> key.name to value.map { (it as? CustomAccessibilityAction)?.label ?: it }
    else -> key.name to value
  }
}

/**
 * F7.24 · Un testo su una riga che cambia col dito (un valore scritto accanto a un righello, «Rotazione: 12°», il numero
 * nel tasto di una regolazione): **si disegna senza ricomporre e senza rimisurare** ([text] si legge nel disegno), e
 * TalkBack lo legge con [fluidSettledSemantics], quando il gesto finisce. Un `Text` che cambia a ogni valore si
 * ricomporrebbe, si rimisurerebbe e, con l'accessibilità accesa, farebbe ricontrollare la pagina a ogni tacca.
 *
 * Occupa la larghezza che riceve (come `Modifier.fillMaxWidth`) e l'altezza di una riga di [style] (misurata su «0»: con
 * un'interlinea data, come negli stili di Fluid, è l'altezza di ogni riga). Il testo non va a capo: se non ci sta, si
 * taglia ([TextOverflow.Clip]). [color] non specificato prende quello di [style], poi `LocalContentColor`.
 */
@Composable
fun FluidValueText(
  text: () -> String,
  modifier: Modifier = Modifier,
  style: TextStyle = LocalTextStyle.current,
  color: Color = Color.Unspecified,
  textAlign: TextAlign = TextAlign.Start,
) {
  val measurer = rememberTextMeasurer(cacheSize = 8)
  val ink = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
  val resolved = remember(style, ink) { style.copy(color = ink) }
  val density = LocalDensity.current
  val lineHeight = remember(resolved, density, measurer) { measurer.measure("0", resolved, maxLines = 1).size.height }
  val current: State<() -> String> = rememberUpdatedState(text)
  FluidValueTextLayout(current, measurer, resolved, textAlign, lineHeight, modifier)
}

@Composable
private fun FluidValueTextLayout(
  text: State<() -> String>,
  measurer: androidx.compose.ui.text.TextMeasurer,
  style: TextStyle,
  textAlign: TextAlign,
  lineHeightPx: Int,
  modifier: Modifier,
) {
  val heightDp = with(LocalDensity.current) { lineHeightPx.toDp() }
  androidx.compose.foundation.layout.Spacer(
    modifier
      .fillMaxWidth()
      .height(heightDp)
      .fluidSettledSemantics { this.text = AnnotatedString(text.value()) }
      // Uno strato suo: il ridisegno a ogni valore resta qui, e non fa riregistrare la pagina (né la sorgente di un
      // vetro che sta nello stesso strato: vedi `LayerBackdropNode`).
      .graphicsLayer()
      .drawBehind {
        val layout = measurer.measure(
          text.value(), style, overflow = TextOverflow.Clip, softWrap = false, maxLines = 1,
          constraints = Constraints(maxWidth = size.width.toInt().coerceAtLeast(0)), layoutDirection = layoutDirection,
          density = this,
        )
        val free = size.width - layout.size.width
        val ltr = layoutDirection == LayoutDirection.Ltr
        // Come Alignment di Compose: le posizioni arrotondate al pixel, così il testo è quello di un `Text` al bit.
        val x = when (textAlign) {
          TextAlign.Center -> (free / 2f).roundToInt().toFloat()
          TextAlign.Right -> free
          TextAlign.Left -> 0f
          TextAlign.End -> if (ltr) free else 0f
          else -> if (ltr) 0f else free
        }
        val y = ((size.height - layout.size.height) / 2f).roundToInt().toFloat()
        drawText(layout, topLeft = Offset(x, y))
      },
  )
}
