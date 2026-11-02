package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import kotlin.math.floor

/*
 * Il modello puro dei menu liquidi (3.0): cosa sta sotto il dito, chi tiene il dito, cosa succede al
 * rilascio. Niente composizione qui dentro, apposta: il gesto di un menu si sbaglia nella mano e mai
 * in una revisione, quindi ogni regola e' una funzione che un test JVM puo' chiamare.
 *
 * La regola che questo file esiste per far rispettare (e' una richiesta esplicita di Alessio): **il
 * menu si sceglie col dito che l'ha aperto, e un rilascio altrove lo lascia aperto.** Tieni premuto,
 * il menu si apre, trascina su una voce e rilascia: scelta. Tieni premuto, il menu si apre, e
 * rilascia senza esserti mosso (o rilascia fuori da una voce, o su una voce disabilitata): il menu
 * RESTA aperto e si sceglie con un tocco. Mai una scelta per sbaglio, mai un menu che si chiude
 * da solo sotto un dito che voleva solo guardare.
 */

// --- Geometria -------------------------------------------------------------------

/**
 * Dove stanno le celle di un menu, in pixel della radice, alla posa FINALE del pannello.
 *
 * La posa finale e non quella del momento, perche' la goccia e il hit-test parlano della stessa cosa
 * che il dito vede quando il pannello e' fermo: durante l'apertura il pannello cresce, ma le righe
 * sono gia' dove stanno per essere e il dito che arriva dall'ancora le trova li'. Le celle sono solo
 * le voci: il padding del pannello e i separatori non sono celle e quindi non sono mai sotto il dito.
 *
 * [enabled] ha un elemento per cella; [panel] e' il rettangolo del pannello intero (serve a chi deve
 * sapere dov'e' il vetro, non al hit-test delle voci).
 */
@Immutable
internal data class FluidMenuGeometry(
  val cells: List<Rect>,
  val enabled: List<Boolean>,
  val panel: Rect,
) {
  init {
    require(cells.size == enabled.size) {
      "Una cella, un flag: ${cells.size} celle e ${enabled.size} flag di abilitazione."
    }
  }

  /** Vero per una voce che esiste ed e' abilitata; falso per qualunque altro indice, compreso −1. */
  fun isEnabled(index: Int): Boolean = index in enabled.indices && enabled[index]
}

/**
 * L'indice della cella sotto [root] (pixel della radice), o −1 se il punto sta fuori, nel padding
 * del pannello o su un separatore.
 *
 * Le celle sono semiaperte (sinistra e alto dentro, destra e basso fuori): due celle adiacenti non
 * si contendono mai il confine, e su un separatore disegnato proprio li' vince la riga sotto. Una
 * cella disabilitata e' comunque una cella: dirlo e' compito di [FluidMenuGeometry.isEnabled].
 */
internal fun fluidMenuCellAt(g: FluidMenuGeometry, root: Offset): Int {
  for (i in g.cells.indices) {
    if (g.cells[i].contains(root)) return i
  }
  return -1
}

/**
 * Il centro della goccia a [value], un indice FRAZIONARIO di cella: fra la cella `i` e la `i + 1`
 * e' l'interpolazione dei due centri. Lo scrive la molla della goccia (`0..(n − 1)`) e lo legge il
 * layer: cosi' la goccia scivola fra le righe invece di saltare.
 *
 * Vale per qualunque disposizione: una pila verticale di righe, due pulsanti affiancati di un avviso
 * (la goccia si muove in orizzontale), una griglia. [value] fuori da `0..(n − 1)` si ferma alla prima
 * o all'ultima cella: la molla della goccia e' criticamente smorzata e non oltrepassa, e un centro
 * fuori dal pannello e' comunque peggio di uno che si ferma sul bordo.
 */
internal fun fluidMenuDropCenter(g: FluidMenuGeometry, value: Float): Offset {
  val n = g.cells.size
  if (n == 0) return g.panel.center
  if (n == 1) return g.cells[0].center
  val v = if (value.isNaN()) 0f else value.coerceIn(0f, (n - 1).toFloat())
  val i = floor(v).toInt().coerceAtMost(n - 2)
  return lerp(g.cells[i].center, g.cells[i + 1].center, v - i)
}

// --- Chi tiene il dito ----------------------------------------------------------

/**
 * Chi ha il dito, in questo momento. Uno solo alla volta, sempre: due proprietari sullo stesso dito
 * sono due scelte per un tocco.
 *
 * - [None]: nessun dito sul menu.
 * - [HandOff]: il dito che ha aperto il menu (l'ancora glielo ha passato: il pannello e' comparso a
 *   dito giu' e non ha mai visto quel down).
 * - [Panel]: un dito sceso sul pannello (un tocco su una voce, o un trascinamento che parte li').
 */
internal enum class FluidMenuOwner { None, HandOff, Panel }

/** Cosa fa un rilascio: sceglie una voce, o lascia il menu com'e'. */
internal sealed interface FluidMenuRelease {
  /** Il rilascio e' avvenuto su una voce abilitata: e' la scelta. */
  data class Pick(val index: Int) : FluidMenuRelease

  /** Il menu resta aperto: nessuna scelta, nessuna chiusura. */
  data object StayOpen : FluidMenuRelease
}

/**
 * L'esito di un rilascio. [index] e' la cella sotto il dito (−1 fuori), [enabled] dice se quella
 * voce e' abilitata.
 *
 * - un dito sceso nel pannello ([FluidMenuOwner.Panel]) sceglie se si solleva su una voce
 *   abilitata: ha toccato li', e' una scelta;
 * - il dito del click lungo ([FluidMenuOwner.HandOff]) sceglie solo se e' [armed], cioe' se si e'
 *   MOSSO: senza, "tieni e rilascia" lascia il menu aperto anche quando il pannello e' comparso
 *   proprio sotto il dito;
 * - senza proprietario non c'e' un dito che rilascia: il menu resta com'e'.
 *
 * Tutto il resto — fuori dalle voci, su un separatore, su una voce disabilitata — e' [StayOpen].
 */
internal fun fluidMenuRelease(
  owner: FluidMenuOwner,
  armed: Boolean,
  index: Int,
  enabled: Boolean,
): FluidMenuRelease {
  if (index < 0 || !enabled) return FluidMenuRelease.StayOpen
  return when (owner) {
    FluidMenuOwner.None -> FluidMenuRelease.StayOpen
    FluidMenuOwner.HandOff -> if (armed) FluidMenuRelease.Pick(index) else FluidMenuRelease.StayOpen
    FluidMenuOwner.Panel -> FluidMenuRelease.Pick(index)
  }
}

/**
 * Il dito del click lungo si e' mosso abbastanza da poter scegliere? Dopo il click lungo il dito e'
 * fermo per definizione (e' per questo che il click lungo e' scattato): finche' non esce dallo slop
 * da dove e' partito, e' ancora "il dito che tiene premuto", e un rilascio e' un "ho aperto e ora
 * guardo", non una scelta.
 *
 * La distanza e' dal punto in cui il menu ha preso il dito ([origin]), in linea retta. Chi la usa la
 * tiene accesa una volta scattata: un dito che si e' mosso e poi torna dov'era e' un dito che ha
 * deciso di scegliere.
 */
internal fun fluidMenuHandOffArmed(origin: Offset, now: Offset, touchSlop: Float): Boolean =
  (now - origin).getDistance() > touchSlop

/**
 * Il tick della goccia, sì o no. Solo quando la goccia passa a un'ALTRA voce abilitata; mai sul down
 * di un tocco nel pannello (la prima presa non e' un cambio, e il tocco ha gia' la sua risposta),
 * mai su una voce disabilitata, mai uscendo da tutte, mai restando sulla stessa.
 *
 * [previous] e [next] sono celle (−1 se nessuna); [enabled] dice se [next] e' abilitata.
 */
internal fun fluidMenuHoverTicks(previous: Int, next: Int, onDown: Boolean, enabled: Boolean): Boolean =
  !onDown && enabled && next >= 0 && next != previous

// --- La selezione ----------------------------------------------------------------

/**
 * Chi riceve il dito passato dall'ancora. L'ancora (un tasto, una barra, una riga) sa dove sta il
 * dito ma non cosa sia un menu; la selezione sa cosa sia un menu ma non vede mai il dito che ha
 * aperto: questa interfaccia e' il filo fra i due, in pixel della radice.
 */
internal interface FluidMenuPointerSink {
  /** Il menu ha preso il dito, che in questo momento sta a [root]. */
  fun handOffBegan(root: Offset)

  /** Il dito passato si e' spostato a [root]. */
  fun hover(root: Offset)

  /** Il dito passato si e' sollevato a [root]. */
  fun release(root: Offset)

  /** Il gesto e' finito senza un rilascio (annullo, ancora uscita dalla composizione, congedo). */
  fun cancel()
}

/**
 * Lo stato di scelta di UN menu: quale voce sta sotto il dito, chi ha il dito, cosa succede al
 * rilascio. Lo condividono la goccia (che la legge in disegno), il pannello (che le scrive la
 * geometria e le passa i gesti del pannello) e l'ancora (che le passa il dito che ha aperto).
 *
 * **[hovered] si legge solo in disegno** (layer, `drawWithContent`, `snapshotFlow`): cambia a ogni
 * voce attraversata, e leggerlo in composizione ricomporrebbe il menu a ogni passaggio di un dito.
 * Vale una voce ABILITATA o −1: una voce disabilitata non sta mai sotto la goccia.
 *
 * **Un proprietario alla volta** ([owner]): durante un hand-off i gesti del pannello sono ignorati
 * (il dito che ha aperto e' quello che decide) e viceversa. Un secondo proprietario non scavalca
 * il primo, e non lo annulla.
 *
 * **La geometria puo' arrivare tardi.** Il pannello la scrive dopo il primo layout; un dito passato
 * che si muove prima di allora non si perde: l'ultimo [hover] resta in attesa ([pending]) e viene
 * rigiocato quando [geometry] arriva. Un rilascio senza geometria non sa dove cade: lascia aperto.
 */
@Stable
internal class FluidMenuSelection(
  /** Lo slop del tocco, in px: la `ViewConfiguration` del pannello lo scrive qui. */
  var touchSlop: Float = DefaultMenuTouchSlopPx,
  /** Un tick: la goccia e' passata a un'altra voce abilitata. */
  var onTick: () -> Unit = {},
  /** Una scelta: il rilascio e' avvenuto su questa voce abilitata. */
  var onPick: (index: Int) -> Unit = {},
) : FluidMenuPointerSink {

  /**
   * Le celle alla posa finale, scritte dal pannello a ogni layout. Una `var` semplice, non uno
   * stato: la scrive onGloballyPositioned e la leggono i gesti, nessun disegno la osserva.
   */
  var geometry: FluidMenuGeometry? = null
    set(value) {
      field = value
      if (value != null) replayPending(value)
    }

  /** La voce abilitata sotto il dito, o −1. SOLO in disegno: vedi la nota sulla classe. */
  var hovered: Int by mutableIntStateOf(-1)
    private set

  private var ownerState by mutableStateOf(FluidMenuOwner.None)

  /** Chi ha il dito adesso. */
  val owner: FluidMenuOwner get() = ownerState

  /** Un dito e' sul menu (di chiunque sia). */
  val held: Boolean get() = ownerState != FluidMenuOwner.None

  /** Il punto dove il menu ha preso il dito passato, per misurare lo slop. */
  private var origin = Offset.Zero

  /** Il dito passato ha gia' superato lo slop: da qui in poi un rilascio puo' scegliere. */
  private var armed = false

  /** Un movimento arrivato prima della geometria, da rigiocare quando c'e'. */
  private var pending: PendingMove? = null

  private class PendingMove(val root: Offset, val onDown: Boolean)

  // --- Il dito passato dall'ancora -------------------------------------------------

  override fun handOffBegan(root: Offset) {
    if (ownerState != FluidMenuOwner.None) return
    ownerState = FluidMenuOwner.HandOff
    origin = root
    armed = false
    pending = null
    hovered = -1
  }

  override fun hover(root: Offset) {
    if (ownerState != FluidMenuOwner.HandOff) return
    if (!armed && fluidMenuHandOffArmed(origin, root, touchSlop)) armed = true
    // Sotto lo slop il dito e' ancora quello che tiene premuto: niente goccia, niente tick.
    if (!armed) return
    move(root, onDown = false)
  }

  override fun release(root: Offset) {
    if (ownerState != FluidMenuOwner.HandOff) return
    // Il rilascio puo' arrivare lontano dall'ultimo movimento (un dito veloce, un evento saltato):
    // armare anche qui, con la posizione di sollevamento.
    if (!armed && fluidMenuHandOffArmed(origin, root, touchSlop)) armed = true
    finish(root)
  }

  override fun cancel() {
    // Annulla chiunque tenga il dito: e' il congedo (back, scrim) o un'ancora che sparisce. Mai una
    // scelta, mai un tick.
    clear()
  }

  // --- Il dito sceso sul pannello -----------------------------------------------------

  /** Un dito scende sul pannello a [root]. Ignorato se il menu ha gia' un altro proprietario. */
  fun panelDown(root: Offset) {
    if (ownerState == FluidMenuOwner.HandOff) return
    ownerState = FluidMenuOwner.Panel
    armed = true
    pending = null
    // Il down e' una presa, non un cambio di voce: la goccia arriva sotto il dito senza tick.
    move(root, onDown = true)
  }

  /** Il dito del pannello si sposta a [root]. */
  fun panelMove(root: Offset) {
    if (ownerState != FluidMenuOwner.Panel) return
    move(root, onDown = false)
  }

  /** Il dito del pannello si solleva a [root]: scelta su una voce abilitata, altrimenti resta aperto. */
  fun panelUp(root: Offset) {
    if (ownerState != FluidMenuOwner.Panel) return
    finish(root)
  }

  /** Il gesto del pannello e' stato annullato: niente scelta. */
  fun panelCancel() {
    if (ownerState != FluidMenuOwner.Panel) return
    clear()
  }

  // --- Interno -----------------------------------------------------------------------

  private fun move(root: Offset, onDown: Boolean) {
    val g = geometry
    if (g == null) {
      pending = PendingMove(root, onDown)
      return
    }
    applyHover(g, root, onDown)
  }

  private fun applyHover(g: FluidMenuGeometry, root: Offset, onDown: Boolean) {
    val cell = fluidMenuCellAt(g, root)
    val enabled = g.isEnabled(cell)
    val tick = fluidMenuHoverTicks(previous = hovered, next = cell, onDown = onDown, enabled = enabled)
    hovered = if (enabled) cell else -1
    if (tick) onTick()
  }

  private fun replayPending(g: FluidMenuGeometry) {
    val move = pending ?: return
    pending = null
    // Se nel frattempo il dito e' stato rilasciato o annullato `pending` e' gia' stato svuotato.
    if (ownerState != FluidMenuOwner.None) applyHover(g, move.root, move.onDown)
  }

  private fun finish(root: Offset) {
    val g = geometry
    val owner = ownerState
    val outcome = if (g == null) {
      // Senza geometria non si sa dove il dito sia caduto: nessuna scelta, il menu resta aperto.
      FluidMenuRelease.StayOpen
    } else {
      val cell = fluidMenuCellAt(g, root)
      fluidMenuRelease(owner, armed, cell, g.isEnabled(cell))
    }
    // Lo stato prima della callback: una scelta che chiude il menu non deve ritrovarsi un dito
    // ancora "tenuto" se ri-entra.
    clear()
    if (outcome is FluidMenuRelease.Pick) onPick(outcome.index)
  }

  private fun clear() {
    ownerState = FluidMenuOwner.None
    armed = false
    pending = null
    hovered = -1
  }
}

/** Lo slop del tocco di Android a ~3x di densita', per chi non ha una `ViewConfiguration` in mano. */
internal const val DefaultMenuTouchSlopPx = 24f

/**
 * Quale gesto apre il menu di un tasto (3.0, fase 7).
 *
 * Di serie il tenuto premuto: il tocco resta l'azione del tasto, e chi non scopre il menu non perde
 * niente. Ma un tasto il cui UNICO mestiere e' il menu (i tre puntini, "Ordina", "Altro") lo apre al
 * tocco, ed e' quello che cinque app si riscrivevano a mano. Il tenuto premuto apre comunque, in tutti
 * e due i casi: e' lui che porta il dito sul menu e sceglie al rilascio.
 */
enum class FluidMenuOpensOn {
  /** Il tocco e' l'azione del tasto; il menu si apre tenendo premuto. */
  LongPress,

  /** Il tocco apre il menu (e l'azione del tasto non c'e'); anche tenendo premuto. */
  Tap,
}

// --- Il dito che passa dall'ancora al menu -----------------------------------------

/**
 * Il ponte fra l'ancora e il menu per UN dito. L'ancora lo tiene (`Modifier.fluidMenuPointerRelay`
 * ne aggiorna lo stato a ogni evento); quando il click lungo apre il menu, `attach` gli passa il
 * dito: da quel momento ogni movimento e il rilascio vanno alla selezione.
 *
 * Esiste perche' Compose sceglie i nodi di un puntatore al DOWN: moti e rilascio vanno al percorso
 * scelto allora, e un pannello (o uno scrim) che compare a dito giu' non vede mai quel dito. Il
 * percorso dell'ancora si'. [FluidMenuSelection] resta cosi' un modello puro, senza modificatori.
 */
@Stable
internal class FluidMenuHandOff {

  /** Il dito dell'ancora e' giu'. */
  var pointerDown: Boolean = false
    private set

  /** L'ultimo punto noto del dito, in pixel della radice. */
  var lastRoot: Offset = Offset.Zero
    private set

  /** Chi ha ricevuto il dito, o null se il dito e' ancora solo dell'ancora. Impostarlo a null scollega. */
  var sink: FluidMenuPointerSink? = null

  /** Il dito e' giu' E il menu l'ha preso: il relè deve consumarne i movimenti. */
  val isActive: Boolean get() = sink != null && pointerDown

  /**
   * Passa il dito corrente a [sink]. Falso se il dito non e' piu' giu' (un click lungo da tastiera o
   * da TalkBack, un dito sollevato nel frattempo): il menu si apre lo stesso, ma a tocchi.
   */
  fun attach(sink: FluidMenuPointerSink): Boolean {
    if (!pointerDown) return false
    if (this.sink != null) return this.sink === sink
    this.sink = sink
    sink.handOffBegan(lastRoot)
    return true
  }

  /** Il relè ha visto un dito scendere a [root]: gesto nuovo, nessun menu lo ha ancora. */
  fun down(root: Offset) {
    // Un dito nuovo non eredita il menu del vecchio: se ne era rimasto uno agganciato lo si congeda.
    val stale = sink
    sink = null
    stale?.cancel()
    pointerDown = true
    lastRoot = root
  }

  fun moved(root: Offset) {
    lastRoot = root
    sink?.hover(root)
  }

  fun lifted(root: Offset) {
    lastRoot = root
    val s = sink
    sink = null
    pointerDown = false
    s?.release(root)
  }

  fun cancelled() {
    val s = sink
    sink = null
    pointerDown = false
    s?.cancel()
  }
}

/** Dal sistema di coordinate di un nodo alla radice: un'interfaccia perche' il relè non alloca una lambda per evento. */
internal fun interface FluidMenuRootMapper {
  fun toRoot(local: Offset): Offset
}

/**
 * La logica del relè, senza Compose attorno (il nodo la chiama; un test JVM la esercita con
 * `PointerInputChange` veri).
 *
 * - **Initial**: traccia l'id del PRIMO puntatore e inoltra down, movimenti e sollevamento a
 *   [FluidMenuHandOff]. Legge tutto "ignorando il consumato" (`changedToDownIgnoreConsumed`): un
 *   discendente che ha gia' preso il gesto — il pulsante stesso, `clickable` — non deve privare il
 *   menu del dito, e nel passaggio Initial i genitori arrivano comunque prima dei figli.
 * - **Consumo**: i cambiamenti di quel dito si consumano SOLO se il menu aveva preso il dito
 *   all'inizio dell'evento, e gia' nel passaggio **Initial** (3.0, fase 7). Cosi' la `LazyColumn`
 *   attorno alla riga non parte mentre il dito trascina sul menu, e il gesto del tasto sotto (che dal
 *   3.0 non prende mai il dito e legge nel Main) trova il cambio consumato e finisce: senza, il tasto
 *   nascosto continuava a seguire il dito sul menu e a suonare la sua grana sotto i tick delle voci.
 *   Tutto il resto del tempo il relè non tocca niente. Il sollevamento che CHIUDE un hand-off conta
 *   come hand-off: va consumato anch'esso, o `clickable` lo leggerebbe come un click sull'ancora.
 * - un puntatore che sparisce dall'evento senza un sollevamento e' un annullo, come
 *   `onCancelPointerInput`.
 */
internal class FluidMenuRelayTracker(var handOff: FluidMenuHandOff) {

  private var trackedId: PointerId? = null
  private var consumeId: PointerId? = null

  fun initial(changes: List<PointerInputChange>, mapper: FluidMenuRootMapper) {
    // Decidere ORA se questo evento e' di un hand-off attivo: dopo aver inoltrato un sollevamento
    // l'hand-off non e' piu' attivo, ma quel sollevamento va consumato lo stesso.
    consumeId = if (handOff.isActive) trackedId else null
    forward(changes, mapper)
    val id = consumeId ?: return
    changes.fastForEach { if (it.id == id) it.consume() }
  }

  private fun forward(changes: List<PointerInputChange>, mapper: FluidMenuRootMapper) {
    val id = trackedId
    if (id == null) {
      val down = changes.fastFirstOrNull { it.changedToDownIgnoreConsumed() } ?: return
      trackedId = down.id
      handOff.down(mapper.toRoot(down.position))
      return
    }

    val change = changes.fastFirstOrNull { it.id == id }
    if (change == null) {
      // Il nostro dito non e' piu' nell'evento e non si e' sollevato: annullo.
      cancel()
      return
    }
    when {
      change.changedToUpIgnoreConsumed() -> {
        trackedId = null
        handOff.lifted(mapper.toRoot(change.position))
      }

      change.pressed && change.position != change.previousPosition ->
        handOff.moved(mapper.toRoot(change.position))
    }
  }

  /**
   * Il passaggio Main: il consumo e' gia' avvenuto nell'Initial, qui si ripete (idempotente) e si
   * chiude l'evento. Con un'eccezione: il menu puo' aver preso il dito DURANTE questo evento, perche'
   * il click lungo scatta nel Main del tasto, un discendente che passa prima di qui. Quel cambio va
   * consumato adesso: un evento a scatti (un telefono che rallenta, un dito che parte proprio allo
   * scadere) porta il dito oltre lo slop in un colpo solo, e la lista attorno partirebbe facendo
   * scivolare la pagina sotto il menu.
   */
  fun main(changes: List<PointerInputChange>) {
    val id = consumeId ?: trackedId.takeIf { handOff.isActive } ?: return
    consumeId = null
    changes.fastForEach { if (it.id == id) it.consume() }
  }

  /** `onCancelPointerInput`, o un relè che cambia ancora a metà gesto. Idempotente. */
  fun cancel() {
    trackedId = null
    consumeId = null
    handOff.cancelled()
  }
}

/**
 * Il relè dell'hand-off: gira sul percorso dell'ancora, quello che Compose scelse al DOWN, e inoltra
 * il dito a [handOff]. Mettilo come PRIMO modificatore dell'ancora: nel passaggio Initial i genitori
 * arrivano prima dei figli, quindi il relè vede il dito prima di qualunque gesto dell'ancora e non
 * dipende da cosa quei gesti consumano.
 *
 * Non fa niente finche' nessuno chiama `attach`: un tasto con questo modificatore e senza menu e'
 * identico a uno senza.
 */
internal fun Modifier.fluidMenuPointerRelay(handOff: FluidMenuHandOff): Modifier =
  this then FluidMenuRelayElement(handOff)

private class FluidMenuRelayElement(
  val handOff: FluidMenuHandOff,
) : ModifierNodeElement<FluidMenuRelayNode>() {

  override fun create(): FluidMenuRelayNode = FluidMenuRelayNode(handOff)

  override fun update(node: FluidMenuRelayNode) {
    node.useHandOff(handOff)
  }

  // Identita' del FluidMenuHandOff: una selezione nuova e' un relè nuovo.
  override fun equals(other: Any?): Boolean = other is FluidMenuRelayElement && other.handOff === handOff

  override fun hashCode(): Int = System.identityHashCode(handOff)

  override fun InspectorInfo.inspectableProperties() {
    name = "fluidMenuPointerRelay"
  }
}

private class FluidMenuRelayNode(
  handOff: FluidMenuHandOff,
) : Modifier.Node(), PointerInputModifierNode, FluidMenuRootMapper {

  private val tracker = FluidMenuRelayTracker(handOff)

  fun useHandOff(handOff: FluidMenuHandOff) {
    if (tracker.handOff === handOff) return
    // Un dito in corso era di quella selezione: congedarlo, non portarselo dietro.
    tracker.cancel()
    tracker.handOff = handOff
  }

  override fun toRoot(local: Offset): Offset = requireLayoutCoordinates().localToRoot(local)

  override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
    when (pass) {
      PointerEventPass.Initial -> tracker.initial(pointerEvent.changes, this)
      PointerEventPass.Main -> tracker.main(pointerEvent.changes)
      else -> Unit
    }
  }

  override fun onCancelPointerInput() {
    tracker.cancel()
  }

  override fun onDetach() {
    // L'ancora esce dalla composizione con il dito ancora giu' (una riga di LazyColumn rimossa):
    // Compose di solito manda prima onCancelPointerInput, ma non e' un contratto su cui reggere un
    // menu. Idempotente.
    tracker.cancel()
  }
}
