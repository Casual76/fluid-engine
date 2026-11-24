package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronRight
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Lo stato di una [FluidDisclosureGroup]: aperta o chiusa (`DisclosureGroup(isExpanded:)` di SwiftUI).
 *
 * L'app lo tiene dove vuole: con [rememberFluidDisclosureState] sopravvive alla rotazione; per ricordare quali righe
 * erano aperte fra una visita e l'altra (le note di versione di Pampa Store) lo crea lei da ciò che ha salvato, con
 * `FluidDisclosureState(initiallyExpanded = …)`, e ne legge [expanded]. Si cambia dal thread principale.
 */
@Stable
class FluidDisclosureState(initiallyExpanded: Boolean = false) {
  /** Vero se il contenuto e' aperto sotto il titolo. */
  var expanded: Boolean by mutableStateOf(initiallyExpanded)

  fun expand() {
    expanded = true
  }

  fun collapse() {
    expanded = false
  }

  fun toggle() {
    expanded = !expanded
  }

  companion object {
    /** Salva solo «aperta o chiusa». */
    val Saver: Saver<FluidDisclosureState, Boolean> = Saver(save = { it.expanded }, restore = { FluidDisclosureState(it) })
  }
}

/** Uno stato di [FluidDisclosureGroup] che sopravvive alla rotazione e alla morte del processo. */
@Composable
fun rememberFluidDisclosureState(initiallyExpanded: Boolean = false): FluidDisclosureState =
  rememberSaveable(saver = FluidDisclosureState.Saver) { FluidDisclosureState(initiallyExpanded) }

/**
 * I numeri della sezione richiudibile (F8.4). Il vetro che si separa e torna lo fa il piano ([FluidGlassContainer]) con
 * i suoi tempi; qui ci sono solo lo spazio che la sezione chiede alla lista e la freccia.
 */
internal object FluidDisclosureMotion {
  /**
   * Il varco fra il titolo e il contenuto aperto: 8 dp, `FluidMetrics.GlassSpacing`, i due vetri di un gruppo. Sta
   * dentro lo `spacing` del piano (16 dp), quindi il contenuto **nasce dal vetro del titolo** invece di comparire per
   * lente, e a riposo i due vetri restano distinti (il collo esiste solo in viaggio, scelta di Alessio del 2026-11-11).
   */
  val Gap: Dp = 8.dp

  /**
   * L'attesa prima che la sezione allarghi il suo posto nella lista: 124 ms, cioe' l'attesa della nascita del piano
   * (0,23 di [FluidMotion.PlaneReleaseMillis], misurata su Apple in F3.11). Durante l'attesa il vetro nuovo e' ancora
   * dentro il titolo: se le righe sotto scendessero subito si aprirebbe un buco vuoto.
   */
  const val OpenWaitMillis: Long = 124L

  /**
   * Come la sezione allarga il suo posto: smorzamento 0,6 e rigidezza 122. A occhio, 2026-11-24, tarata sulla nascita
   * del piano perche' le righe sotto siano spinte dal vetro che esce: il primo passaggio per l'arrivo a ~250 ms dopo
   * l'attesa (il vetro nato arriva a ~296 ms), il colmo a ~355 ms con il 9,5% di superamento (quello del rilascio,
   * `FluidMotion.PlaneReleaseMillis`: colmo a ~367 ms, superamento 9,5%).
   */
  val Open: SpringSpec<Float> = spring(dampingRatio = 0.6f, stiffness = 122f, visibilityThreshold = 0.001f)

  /**
   * Come la sezione restringe il suo posto quando si chiude: smorzamento 0,85, rigidezza 200, senza attesa. A occhio,
   * 2026-11-24: la confluenza del piano entra nel vicino subito e ha il colmo a 335 ms (0,62 del riposo).
   */
  val Close: SpringSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 200f, visibilityThreshold = 0.001f)

  /**
   * La freccia che gira di 90°: smorzamento 0,8, rigidezza 400 (a occhio, 2026-11-24). In SwiftUI la freccia di
   * `DisclosureGroup` gira con l'animazione di serie, che non e' documentata.
   */
  val Chevron: SpringSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 400f, visibilityThreshold = 0.1f)

  /** La riga del titolo: 44 dp, il minimo di una riga di tabella di iOS e il bersaglio delle HIG. */
  val RowMinHeight: Dp = 44.dp
}

/** Le parole di [FluidDisclosureGroup] per TalkBack: lo stato della riga e le due azioni. */
@Immutable
data class FluidDisclosureLabels(
  val expanded: String = "Espanso",
  val collapsed: String = "Compresso",
  val expand: String = "Espandi",
  val collapse: String = "Comprimi",
) {
  companion object {
    fun english() = FluidDisclosureLabels(expanded = "Expanded", collapsed = "Collapsed", expand = "Expand", collapse = "Collapse")
  }
}

/** Il contenuto aperto che sta in un vetro suo sotto il titolo: gli id dei due pezzi del piano. */
private object DisclosurePiece {
  const val Head = "disclosure-head"
  const val Body = "disclosure-body"
}

/**
 * Un titolo che apre e chiude cio' che ha sotto: i dettagli di una riga o un gruppo di righe (`DisclosureGroup` di
 * SwiftUI, la scheda `sezioni-richiudibili`).
 *
 * **Il vetro che si separa** (nota di Alessio: «con effetto di vetro che si separa dall'elemento originale
 * all'apertura»). Titolo e contenuto sono due pezzi di vetro di un piano ([FluidGlassContainer]) a 8 dp l'uno
 * dall'altro. Aprendo, il contenuto **nasce dal vetro del titolo**: ci sta dentro piccolo, ne viene rilasciato con la
 * molla che supera il suo posto e torna, e un collo lo tiene unito al titolo finche' non si stacca (il linguaggio dei
 * menu di F5 e del viaggio d'oro). Chiudendo **torna dentro** il titolo, rimpicciolendosi, e il titolo lo accoglie. Il
 * contenuto si vede solo dentro il suo vetro ed entra sfocato (R7). Intanto la sezione allarga o restringe il posto
 * che chiede alla lista con una molla tarata sulla nascita ([FluidDisclosureMotion]): le righe sotto sono spinte dal
 * vetro che esce, non saltano.
 *
 * **Stato, TalkBack e focus restano sulla riga del titolo.** Il titolo e' un pulsante con lo stato «Espanso» o
 * «Compresso» e le azioni Espandi e Comprimi: aprendo e chiudendo il nodo resta lo stesso, quindi il fuoco non si
 * sposta. Da chiusa la riga puo' mostrare un riepilogo ([summary], «3 allegati»), che sparisce aprendo, perche' il
 * contenuto dice la stessa cosa per intero.
 *
 * **Dove sta.** E' un gruppo di lista a se', non una riga dentro un [dev.antigravity.fluidengine.ui.theme.FluidListGroup]:
 * il vetro va sul gruppo (regola del design system), e una sezione richiudibile e' un gruppo che si divide. Il
 * contenuto e' una colonna: righe con i loro separatori, un testo, una tabella di dettagli.
 *
 * **Il vetro.** Di serie rifrange il fondale della pagina (`LocalFluidCanvasBackdrop`, come i gruppi di lista); sopra una
 * foto o in un pannello l'app passa il suo [backdrop]. Senza un fondale (una pagina piatta, sotto Android 12) i due
 * pezzi sono superfici piene e il contenuto entra in dissolvenza dal titolo.
 *
 * **In una `LazyColumn`.** La sezione chiede alla lista l'altezza che si allarga: la lista la rimisura a ogni
 * fotogramma della molla (solo il layout, nessuna ricomposizione). Le voci sotto seguono da sole.
 *
 * **A riposo** niente si muove e niente si ridisegna: le molle sono ferme e nessuno stato si scrive.
 * **Movimento ridotto**: il piano cambia senza viaggio, l'altezza e la freccia arrivano in un fotogramma.
 *
 * @param title il titolo della riga.
 * @param state aperta o chiusa ([rememberFluidDisclosureState]).
 * @param summary il riepilogo da chiusa («3 allegati»), a destra prima della freccia; null nessuno.
 * @param leading cio' che sta prima del titolo (una piastrella, un'icona), o null.
 * @param backdrop cio' che i due vetri rifrangono; null li fa superfici piene.
 * @param onExpandedChange chiamata dopo ogni cambio fatto dal dito o da TalkBack, col valore nuovo.
 * @param labels le parole per TalkBack ([FluidDisclosureLabels.english] in inglese).
 * @param content il contenuto aperto, in una colonna larga quanto la sezione.
 */
@Composable
fun FluidDisclosureGroup(
  title: String,
  modifier: Modifier = Modifier,
  state: FluidDisclosureState = rememberFluidDisclosureState(),
  summary: String? = null,
  leading: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  backdrop: GlassBackdropState? = LocalFluidCanvasBackdrop.current,
  onExpandedChange: ((Boolean) -> Unit)? = null,
  labels: FluidDisclosureLabels = FluidDisclosureLabels(),
  content: @Composable ColumnScope.() -> Unit,
) {
  val expanded = state.expanded
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val currentOnChange by rememberUpdatedState(onExpandedChange)
  // Quanto del posto del contenuto la sezione chiede alla lista (0 chiusa, 1 aperta) e la freccia (0 a destra, 1 in
  // giu'). Letti in layout e in disegno: una molla che corre rimisura e ridisegna, non ricompone.
  val opening = remember { Animatable(if (expanded) 1f else 0f) }
  val chevron = remember { Animatable(if (expanded) 1f else 0f) }
  LaunchedEffect(expanded, reducedMotion) {
    val target = if (expanded) 1f else 0f
    if (reducedMotion) {
      opening.snapTo(target)
      return@LaunchedEffect
    }
    if (expanded && opening.value == 0f) delay(FluidDisclosureMotion.OpenWaitMillis)
    opening.animateTo(target, if (expanded) FluidDisclosureMotion.Open else FluidDisclosureMotion.Close)
  }
  LaunchedEffect(expanded, reducedMotion) {
    val target = if (expanded) 1f else 0f
    if (reducedMotion) chevron.snapTo(target) else chevron.animateTo(target, FluidDisclosureMotion.Chevron)
  }

  val toggle: () -> Unit = {
    state.toggle()
    currentOnChange?.invoke(state.expanded)
  }
  val stateWord = if (expanded) labels.expanded else labels.collapsed
  val shape = ContinuousCornerShape(FluidRadius.Group)
  // L'altezza del titolo e quella del contenuto, lette nello stesso passo di misura in cui le misura il piano (campi
  // semplici, non stato: chi le legge e' il layout qui sotto, subito dopo).
  val extents = remember { DisclosureExtents() }

  val header: @Composable (Modifier) -> Unit = { glassModifier ->
    Row(
      modifier = glassModifier
        .layout { measurable, constraints ->
          val placeable = measurable.measure(constraints)
          extents.headPx = placeable.height
          layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        .fillMaxWidth()
        .heightIn(min = FluidDisclosureMotion.RowMinHeight)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          enabled = enabled,
          role = Role.Button,
          onClickLabel = if (expanded) labels.collapse else labels.expand,
          onClick = toggle,
        )
        .semantics {
          stateDescription = stateWord
          if (enabled) {
            if (expanded) {
              collapse(label = labels.collapse) { toggle(); true }
            } else {
              expand(label = labels.expand) { toggle(); true }
            }
          }
        }
        .padding(horizontal = 16.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leading != null) {
        Box(contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
      }
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (summary != null) {
        Text(
          text = summary,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          // Sparisce aprendo con la freccia: il contenuto aperto dice la stessa cosa per intero.
          modifier = Modifier.padding(start = 8.dp).graphicsLayer { alpha = 1f - chevron.value.coerceIn(0f, 1f) },
        )
      }
      Spacer(Modifier.width(8.dp))
      FluidIcon(
        imageVector = FluidIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        size = 16.dp,
        modifier = Modifier.graphicsLayer { rotationZ = 90f * chevron.value },
      )
    }
  }

  val body: @Composable (Modifier) -> Unit = { glassModifier ->
    Column(
      modifier = glassModifier
        .layout { measurable, constraints ->
          val placeable = measurable.measure(constraints)
          extents.bodyPx = placeable.height
          layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        .fillMaxWidth(),
      content = content,
    )
  }

  Layout(
    modifier = modifier.fillMaxWidth(),
    content = {
      if (backdrop != null) {
        val ns = rememberFluidGlassNamespace()
        val stacked = LocalFluidCanvasIsGlass.current
        val tint = if (stacked) GlassDefaults.stackedContentTint() else GlassDefaults.contentTint()
        val optics = if (stacked) GlassDefaults.stackedContentOptics() else GlassDefaults.optics(GlassRole.Content)
        FluidGlassContainer {
          Column(verticalArrangement = Arrangement.spacedBy(FluidDisclosureMotion.Gap)) {
            header(
              Modifier.fluidGlassId(DisclosurePiece.Head, ns)
                .fluidGlassPanel(backdrop, tint, shape, GlassRole.Content, optics),
            )
            if (expanded) {
              body(
                Modifier.fluidGlassId(DisclosurePiece.Body, ns)
                  .fluidGlassPanel(backdrop, tint, shape, GlassRole.Content, optics),
              )
            }
          }
        }
      } else {
        // Senza fondale: due superfici piene, e il contenuto che esce dal titolo in dissolvenza.
        val fill = MaterialTheme.colorScheme.surfaceContainerLow
        Column(verticalArrangement = Arrangement.spacedBy(FluidDisclosureMotion.Gap)) {
          Surface(shape = shape, color = fill, contentColor = MaterialTheme.colorScheme.onSurface) { header(Modifier) }
          if (expanded) {
            Surface(
              shape = shape,
              color = fill,
              contentColor = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.graphicsLayer {
                val p = opening.value.coerceIn(0f, 1f)
                alpha = p
                translationY = -(1f - p) * (extents.headPx * 0.5f)
              },
            ) { body(Modifier) }
          }
        }
      }
    },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val placeable = measurables.first().measure(
      Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity),
    )
    // Il posto del contenuto: misurato mentre c'e', ricordato mentre si chiude (il vetro e' gia' uscito dal layout,
    // ma la lista deve restringersi con la molla).
    val gapPx = FluidDisclosureMotion.Gap.roundToPx()
    if (expanded && placeable.height > extents.headPx) extents.openExtentPx = placeable.height - extents.headPx
    val extra = if (extents.openExtentPx > 0) extents.openExtentPx else gapPx + extents.bodyPx
    val height = (extents.headPx + extra * opening.value).roundToInt().coerceAtLeast(extents.headPx)
    layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) { placeable.place(0, 0) }
  }
}

/** Le misure dell'ultimo passo, fuori dallo stato (vedi [FluidDisclosureGroup]). */
private class DisclosureExtents {
  var headPx: Int = 0
  var bodyPx: Int = 0
  var openExtentPx: Int = 0
}
