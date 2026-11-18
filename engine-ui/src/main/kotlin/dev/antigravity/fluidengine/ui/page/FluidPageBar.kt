package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
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
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDeclaration
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMinimizeProgress
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.fluid.toolbar.buildFluidToolbar
import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole

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
  /**
   * La barra **dichiarata** della pagina (F4A.3), o null per una pagina con lo slot `actions` di sempre. Con il modello
   * l'host non compone più una riga di azioni per pagina: disegna **una barra sola** dal modello della pagina in cima
   * alla pila richiesta, e quando la cima cambia la differenza fra i due modelli diventa un cambio di stato del suo
   * contenitore (i tasti restano al loro posto e si trasformano). [actions] allora non conta.
   */
  val toolbar: FluidToolbarDeclaration? = null,
  /** La dichiarazione è già registrata anche da FluidScreen: il sottotitolo arriva senza una seconda registrazione. */
  val subtitle: String? = toolbar?.subtitle,
  /**
   * La barra che si ritira scorrendo (F4A.4), se la pagina la chiede (`barMinimize`): null per una pagina che non si
   * ritira. Il titolo e le azioni di sempre della pagina la leggono dal suo contesto; l'host la fonde con quella dell'altra
   * pagina per ciò che è suo ([fluidPageBarMinimizeProgressOf]: l'indietro, la barra del modello, l'area toccabile).
   */
  val minimize: FluidToolbarMinimizeProgress? = null,
)

/**
 * Quanto è ritirata la barra dell'host (giro di correzione di F4A, B1 e C8). L'indietro, la barra disegnata dal modello e
 * l'area che riporta in cima sono **uno per host**: a riposo seguono la pagina sopra, mentre due pagine si muovono la
 * corsa di ognuna pesata come lo sfondo della barra ([anchoredBarPageWeight]), così fra una pagina con la barra ritirata
 * e una con la barra aperta i tasti compaiono e se ne vanno con la pagina invece che in un fotogramma. [entries] sono le
 * pagine che la barra mostra, col loro ruolo e la loro corsa (0 per una pagina che non si ritira). Pura.
 *
 * Il titolo e le azioni di sempre restano della loro pagina: si ritirano con la corsa della pagina a cui appartengono.
 */
internal fun fluidPageBarMinimizeProgressOf(
  entries: List<Pair<PageRole, Float>>,
  moving: Boolean,
  frontFraction: Float,
): Float {
  val upper = entries.firstOrNull { it.first == PageRole.Upper }?.second ?: 0f
  if (!moving) return upper.coerceIn(0f, 1f)
  var weight = 0f
  var sum = 0f
  for ((role, progress) in entries) {
    val w = anchoredBarPageWeight(role, frontFraction)
    weight += w
    sum += w * progress.coerceIn(0f, 1f)
  }
  return if (weight > 0f) (sum / weight).coerceIn(0f, 1f) else upper.coerceIn(0f, 1f)
}

/** Il ritiro della barra dell'host: [fluidPageBarMinimizeProgressOf] sulle pagine che la barra mostra, letto in disegno. */
@Stable
internal class FluidPageBarMinimize(
  private val state: FluidPageHostState,
  private val shown: State<List<Pair<Any, FluidPageBarSpec>>>,
  override val reducedMotion: Boolean,
) : FluidToolbarMinimizeProgress {
  override val progress: Float
    get() = fluidPageBarMinimizeProgressOf(
      shown.value.map { (page, spec) -> state.roleOf(page) to (spec.minimize?.progress ?: 0f) },
      moving = state.isMoving,
      frontFraction = state.frontFraction(),
    )
}

// ================================================================================================================
// La barra dell'host disegnata dal modello (F4A.3)
// ================================================================================================================

/**
 * La pagina che dà i tasti alla barra dell'host: **la cima della pila richiesta** ([FluidPageHostState.interactionPage]),
 * non la pagina che il dito o la molla mostrano davanti. È la regola di R7 (M12) e la risposta D1 di Alessio in una riga:
 *
 *  - in un push la pila cambia al tocco: i tasti partono subito verso quelli della pagina nuova, col loro tempo;
 *  - in un pop col tasto, lo stesso verso la pagina sotto;
 *  - nel gesto indietro la pila **non** cambia finché il rilascio non conferma (`commit` chiama `onBack`, e l'app
 *    accorcia la pila): durante il trascinamento la barra resta quella della pagina in cima, al rilascio che conferma
 *    parte la trasformazione intera, all'annullo la pila non è mai cambiata e non succede niente;
 *  - un push interrotto da un gesto: la barra continua verso la pagina in cima e riparte da dove si trova alla conferma;
 *  - un cambio di scheda (D4) è un cambio di cima come un altro, senza verso.
 *
 * Prima della prima pila (stato non ancora inizializzato) vale la pagina che l'host disegna sopra. Pura.
 */
internal fun fluidPageBarPage(interactionPage: Any?, upper: Any?): Any? = interactionPage ?: upper

/**
 * Se la barra dell'host si disegna dal modello: basta che una delle pagine che mostra ([specs]) abbia dichiarato la sua
 * barra. Fra due pagine con lo slot `actions` di sempre la barra è quella di prima, identica (F4A-c). Pura.
 */
internal fun fluidPageBarUsesModel(specs: Iterable<FluidPageBarSpec?>): Boolean = fluidPageBarUsesModelOf(specs.map { it?.toolbar })

/** La parte pura di [fluidPageBarUsesModel], sulle sole barre dichiarate (null: una pagina di sempre, o nessuna). */
internal fun fluidPageBarUsesModelOf(toolbars: Iterable<FluidToolbarDeclaration?>): Boolean = toolbars.any { it != null }

/**
 * Ciò che la barra disegnata dal modello mostra per la pagina [spec]: la sua barra dichiarata, oppure, per una pagina con
 * le `actions` di sempre accanto a una dichiarata, il solo tasto indietro (se ce l'ha). Così l'indietro è **uno**, con la
 * stessa identità ([dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarIds.Back]) nelle due pagine, e resta fermo
 * al pixel anche fra una pagina vecchia e una nuova; le azioni della pagina vecchia passano come prima, in dissolvenza.
 */
internal fun fluidPageBarDeclaration(spec: FluidPageBarSpec): FluidToolbarDeclaration =
  fluidPageBarDeclarationOf(spec.toolbar, spec.onBack)

/** La parte pura di [fluidPageBarDeclaration]: la barra dichiarata [toolbar], o il solo indietro di [onBack]. */
internal fun fluidPageBarDeclarationOf(toolbar: FluidToolbarDeclaration?, onBack: (() -> Unit)?): FluidToolbarDeclaration =
  toolbar ?: buildFluidToolbar(onBack) { }

/** Una barra senza tasti: una pagina che non ne ha. */
internal val FluidPageBarEmpty: FluidToolbarDeclaration =
  FluidToolbarDeclaration(emptyList(), FluidToolbarTitleMode.Large, null, null, null)

/**
 * La dichiarazione da disegnare per la pagina della barra, dato ciò che il registro dice di lei. Pura.
 *
 * - [registered] falso: la pagina non è ancora composta (la cima nuova arriva allo stato dopo la composizione, o una
 *   pagina di più indietro si compone al fotogramma dopo). Non dice ancora niente: resta l'ultima dichiarazione ([last]),
 *   così i tasti non muoiono per rinascere un fotogramma dopo.
 * - composta ma senza barra ([spec] null): durante un movimento resta l'ultima (una barra che si registra più tardi non
 *   fa morire e rinascere i tasti); a riposo, nessun tasto.
 * - altrimenti la sua ([fluidPageBarDeclaration]).
 */
internal fun fluidPageBarTarget(
  registered: Boolean,
  spec: FluidPageBarSpec?,
  moving: Boolean,
  last: FluidToolbarDeclaration?,
): FluidToolbarDeclaration = fluidPageBarTargetOf(registered, spec != null, spec?.toolbar, spec?.onBack, moving, last)

/** La parte pura di [fluidPageBarTarget]: [described] dice se la pagina ha registrato una barra. */
internal fun fluidPageBarTargetOf(
  registered: Boolean,
  described: Boolean,
  toolbar: FluidToolbarDeclaration?,
  onBack: (() -> Unit)?,
  moving: Boolean,
  last: FluidToolbarDeclaration?,
): FluidToolbarDeclaration = when {
  !registered -> last ?: FluidPageBarEmpty
  !described -> if (moving) last ?: FluidPageBarEmpty else FluidPageBarEmpty
  else -> fluidPageBarDeclarationOf(toolbar, onBack)
}

/**
 * La memoria della barra disegnata dal modello fra due composizioni: la pagina che mostra, la sua dichiarazione e il suo
 * contesto (ciò che vedeva la pagina: tema, ViewModel della voce, host dei modali). Si aggiorna in composizione.
 */
internal class FluidPageBarTargetMemory {
  var page: Any? = null
    private set
  var declaration: FluidToolbarDeclaration? = null
    private set
  var context: CompositionLocalContext? = null
    private set

  /**
   * Se l'host disegna dal modello: da quando una delle pagine che mostra ha dichiarato la barra, **e da lì in poi**. Così
   * la barra del modello non si monta e smonta a ogni passaggio fra una pagina di sempre e una dichiarata (ogni volta un
   * fotogramma senza indietro): succede una volta sola per host. Un host che non ha mai visto una barra dichiarata resta
   * quello di prima, identico; in uno che l'ha vista, l'indietro di ogni pagina è del modello e le azioni delle pagine di
   * sempre restano nella loro riga (fra due pagine di sempre col viaggio dei pezzi di prima).
   */
  var hostModel: Boolean = false
    private set

  /** [modelMode]: se le pagine che la barra mostra adesso ne hanno una dichiarata. Torna [hostModel]. */
  fun noteModel(modelMode: Boolean): Boolean {
    if (modelMode) hostModel = true
    return hostModel
  }

  fun update(page: Any?, registered: Boolean, spec: FluidPageBarSpec?, moving: Boolean): FluidToolbarDeclaration {
    val next = fluidPageBarTarget(registered, spec, moving, declaration)
    if (registered) this.page = page
    declaration = next
    if (spec != null) context = spec.context
    return next
  }
}

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
 * della lente, forma) e legge [suppressed] alla staffetta del materiale, lasciando disegnare le icone.
 */
internal class FluidBarPieceSink(
  /** Se il vetro dei tasti va spento adesso; cambia solo quando il morph prende o restituisce i pezzi. */
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
