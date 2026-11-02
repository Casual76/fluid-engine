package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextStyle
import dev.antigravity.fluidengine.ui.fluid.FluidTitleMorphState
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassTint

/**
 * Quello che una pagina dice della sua barra (3.0, fase 8): titolo, tasto indietro, azioni e i
 * numeri del titolo che si aggancia. Dentro un [FluidPageHost] la barra non si disegna nella pagina:
 * la disegna l'host, **una per host**, ancorata sopra le pagine, e la fa passare da una pagina
 * all'altra mentre le pagine si muovono. Fuori da un host `FluidScreen` disegna la sua come sempre.
 *
 * [context] e' il `CompositionLocalContext` della pagina: le azioni si compongono nella barra
 * dell'host ma vedono quello che vedevano nella pagina (tema, ViewModel della voce, host dei modali).
 */
internal data class FluidPageBarSpec(
  val title: String,
  val activeFacet: State<String?>,
  val onBack: (() -> Unit)?,
  val actions: @Composable RowScope.() -> Unit,
  val context: CompositionLocalContext,
  val collapseProgress: State<Float>,
  val glassIntensity: State<Float>,
  /** Null per una pagina senza titolo grande ([FluidPageChrome]): il titolo e' solo quello agganciato. */
  val morphState: FluidTitleMorphState?,
  val compactStyle: TextStyle,
  val contentTranslation: () -> Float,
  val fontScale: Float,
  val reducedMotion: Boolean,
  val onTapTitle: (() -> Unit)?,
  val barTint: GlassTint?,
  /** La registrazione della pagina che il vetro della barra rifrange; null: niente vetro. */
  val backdrop: GlassBackdropState?,
)

/**
 * Il posto della barra di una pagina, dato dall'host a ogni pagina. Piu' registrazioni nella stessa
 * pagina (una `FluidScreen` dentro un'altra cosa che ne ha gia' una): vince l'ultima.
 */
internal class FluidPageBarSlot(
  val page: Any,
  private val registry: SnapshotStateMap<Any, FluidPageBarSlot>,
) : RememberObserver {

  private val registrations = mutableStateListOf<FluidPageBarRegistration>()

  /** La barra che la pagina chiede adesso, o null se la pagina non ne ha una. */
  val spec: FluidPageBarSpec? get() = registrations.lastOrNull()?.spec

  internal fun add(registration: FluidPageBarRegistration) {
    registrations += registration
  }

  internal fun remove(registration: FluidPageBarRegistration) {
    registrations.remove(registration)
  }

  init {
    // In composizione: la barra dell'host si compone dopo le pagine e trova il posto subito.
    registry[page] = this
  }

  override fun onRemembered() = Unit

  override fun onForgotten() {
    if (registry[page] === this) registry.remove(page)
  }

  override fun onAbandoned() = onForgotten()
}

/** Una registrazione: entra nel posto mentre si ricorda, ne esce quando la composizione la dimentica. */
internal class FluidPageBarRegistration(private val slot: FluidPageBarSlot) : RememberObserver {
  var spec: FluidPageBarSpec? by mutableStateOf(null)

  init {
    // Nella composizione, non in un effetto: la barra dell'host si compone dopo le pagine nello
    // stesso passaggio, e cosi' la vede gia' al primo fotogramma della pagina.
    slot.add(this)
  }

  override fun onRemembered() = Unit

  override fun onForgotten() {
    slot.remove(this)
  }

  override fun onAbandoned() {
    slot.remove(this)
  }
}

/** Il posto della barra della pagina che contiene questo punto dell'albero, o null fuori da un host. */
internal val LocalFluidPageBar = staticCompositionLocalOf<FluidPageBarSlot?> { null }

/** Registra [spec] nel posto: la barra la disegna l'host. */
@Composable
internal fun RegisterFluidPageBar(slot: FluidPageBarSlot, spec: FluidPageBarSpec) {
  val registration = remember(slot) { FluidPageBarRegistration(slot) }
  // Scritta in composizione per la stessa ragione della registrazione; uguale a quella di prima
  // (data class) non invalida niente.
  registration.spec = spec
}

/**
 * Dove i tasti di vetro della barra dicono dove stanno, e chiedono se il loro materiale va spento.
 *
 * Mentre le pagine cambiano i tasti non disegnano il loro vetro: lo disegna **una** superficie di
 * Fluid-physics fra il vetro della barra e le icone, che porta i pezzi della pagina che esce sui
 * pezzi di quella che arriva. Ogni `glassControlSurface` dentro la barra si iscrive qui (posizione
 * della lente, forma) e legge [suppressed] in disegno.
 */
internal class FluidBarPieceSink(
  /** Se il vetro dei tasti va spento adesso: letto in disegno. */
  val suppressed: () -> Boolean,
  /** Chiamato quando un pezzo si iscrive, si sposta o se ne va. */
  val onChanged: () -> Unit,
) {
  internal val pieces = ArrayList<FluidBarPiece>()

  internal fun add(piece: FluidBarPiece) {
    pieces += piece
    onChanged()
  }

  internal fun remove(piece: FluidBarPiece) {
    pieces.remove(piece)
    onChanged()
  }
}

/** Un tasto di vetro nella barra: dove sta la lente, e se e' una capsula (il campo fuso parla solo quelle). */
internal class FluidBarPiece(val capsule: Boolean) {
  var coordinates: LayoutCoordinates? = null
}

/** Il collettore dei pezzi della barra in cui ci si trova, o null fuori dalla barra ancorata. */
internal val LocalFluidBarPieceSink = staticCompositionLocalOf<FluidBarPieceSink?> { null }

/** Il registro dei posti di un host, dalla chiave della pagina. */
internal typealias FluidPageBarRegistry = SnapshotStateMap<Any, FluidPageBarSlot>
