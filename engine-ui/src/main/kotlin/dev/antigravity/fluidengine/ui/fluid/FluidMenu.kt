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
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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

  /**
   * Dove il dito cade nella geometria a riposo ([geometry]), dato dove sta sullo schermo: mentre il menu nasce (o torna) le voci
   * si disegnano spostate e scalate col corpo (la mappa del contenuto: il distacco e la crescita della nascita), e il dito mira
   * a quelle che vede. La scrive l'host ([fluidMenuPointerAtRest]); null, il dito e' gia' nella geometria. Non tiene conto
   * della piega della lente, che e' una distorsione e non una mappa. Lo slop si misura sullo schermo.
   */
  var pointerMap: ((Offset) -> Offset)? = null

  private fun atRest(root: Offset): Offset = pointerMap?.invoke(root) ?: root

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

  /**
   * Dove stava il dito quando ha acceso la voce evidenziata ([hovered]), sullo schermo. Mentre il menu si muove (la nascita:
   * le voci stanno col corpo che cresce) la voce accesa resta quella finche' il dito non si sposta oltre lo slop da qui, e al
   * rilascio si sceglie lei (S4f, NO 4).
   */
  private var hoverAt: Offset? = null

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
    val atRest = atRest(root)
    // S4f sul Tab S9 (NO 4): il dito arriva su «Condividi» mentre il menu nasce e resta fermo; la voce, col corpo che cresce,
    // gli scivola via da sotto. La voce accesa resta quella che il dito ha scelto finche' non si sposta lui: mentre il menu si
    // muove contano i movimenti del dito, non quelli delle voci. A menu fermo (la mappa e' l'identita') come sempre.
    if (!onDown && fluidMenuHoverHolds(hovered, hoverAt, root, touchSlop, moving = atRest != root)) return
    val cell = fluidMenuCellAt(g, atRest)
    val enabled = g.isEnabled(cell)
    val tick = fluidMenuHoverTicks(previous = hovered, next = cell, onDown = onDown, enabled = enabled)
    hovered = if (enabled) cell else -1
    hoverAt = root
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
      val cell = fluidMenuReleaseCell(g, hovered, hoverAt, root, atRest(root), touchSlop)
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
    hoverAt = null
  }
}

/**
 * La voce accesa [hovered] resta accesa? Si', se il menu si muove sotto il dito ([moving]: la mappa delle voci non e'
 * l'identita') e il dito e' ancora entro lo slop dal punto [hoverAt] in cui l'ha accesa. Il tremolio di un dito fermo, o un
 * iniettore che ripete lo stesso punto, non la spostano su un'altra voce che il corpo che cresce gli porta sotto.
 */
internal fun fluidMenuHoverHolds(hovered: Int, hoverAt: Offset?, root: Offset, touchSlop: Float, moving: Boolean): Boolean =
  moving && hovered >= 0 && hoverAt != null && (root - hoverAt).getDistance() <= touchSlop

/**
 * La cella del rilascio (S4f sul Tab S9, NO 4: la voce evidenziata durante la nascita, il dito fermo, il menu che restava
 * aperto). In ordine:
 * 1. la voce evidenziata [hovered], se il dito e' ancora entro lo slop da dove l'ha accesa ([hoverAt]): si sceglie quella che
 *    si vede accesa, anche se nel frattempo il corpo che cresce l'ha portata un poco piu' in la';
 * 2. la voce che si vede sotto il dito ([atRest], il punto riportato alla posa dalla mappa delle voci);
 * 3. se li' non si vede niente (il menu e' ancora piccolo), la voce della posa sotto il dito ([root]): quella che sta
 *    arrivando li'. Il dito che ha mirato al posto della voce non resta senza scelta perche' il menu nasce ancora.
 */
internal fun fluidMenuReleaseCell(
  g: FluidMenuGeometry,
  hovered: Int,
  hoverAt: Offset?,
  root: Offset,
  atRest: Offset,
  touchSlop: Float,
): Int {
  if (hovered >= 0 && hoverAt != null && (root - hoverAt).getDistance() <= touchSlop && g.isEnabled(hovered)) return hovered
  val seen = fluidMenuCellAt(g, atRest)
  if (seen >= 0 || atRest == root) return seen
  return fluidMenuCellAt(g, root)
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
 *   3.0 tiene il dito, ma legge nel Main, dopo l'Initial dei genitori) trova il cambio consumato e
 *   finisce: senza, il tasto nascosto continuava a seguire il dito sul menu e a suonare la sua grana
 *   sotto i tick delle voci.
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

// --- La struttura (4.0, F5A.3) ---------------------------------------------------------------

/**
 * In che ordine stanno le voci quando il menu si apre verso l'alto (`menuOrder` di SwiftUI, 4.0).
 *
 * Il problema che risolve: un menu che nasce da un tasto in basso cresce verso l'alto, e la prima voce dichiarata
 * finisce in cima, il piu' lontano possibile dal dito che l'ha aperto. Con [Priority] il menu si capovolge e le
 * prime voci restano vicine al dito; con [Fixed] l'ordine e' sempre quello scritto, che e' cio' che serve a una
 * lista che si legge (un elenco ordinato, un selettore).
 */
enum class FluidMenuOrder {
  /**
   * Di serie: [Priority] per un menu dal tasto, [Fixed] per un menu contestuale. Per conservare l'ordine
   * scritto in un menu dal tasto, per esempio in un contenuto scorrevole, si può chiedere [Fixed].
   */
  Automatic,

  /** Le prime voci piu' vicine al punto da cui il menu si apre: capovolto quando si apre verso l'alto. */
  Priority,

  /** Sempre dall'alto in basso, come sono scritte. */
  Fixed,
}

/**
 * Quanto spazio prende una voce (`preferredElementSize` di UIKit, 4.0).
 *
 * [Small] e [Medium] mettono la **prima sezione** del menu su una fila sola in cima, una tessera per voce, come la
 * fila «Taglia, Copia, Incolla» di iOS: [Small] fino a quattro simboli senza etichetta, [Medium] fino a tre con
 * l'etichetta corta sotto. Le voci della prima sezione che non ci stanno continuano nella lista. [Large] e' la
 * lista di sempre.
 */
enum class FluidMenuElementSize {
  /** Fino a quattro simboli in fila, senza etichetta (che resta per TalkBack). */
  Small,

  /** Fino a tre simboli in fila, con l'etichetta corta sotto. */
  Medium,

  /** Una riga per voce: la lista di sempre. */
  Large,
}

/**
 * Un menu come valore: le voci piu' come si dispongono ([order], [elementSize]). E' una `List<FluidContextAction>`,
 * quindi va dove va una lista di voci — `contextActions = { … }`, `FluidBarAction(actions = { … })`,
 * `action(menu = { … })`, `Modifier.fluidContextMenu(actions = { … })` — e chi non lo conosce lo legge come una lista.
 *
 * **Il contenuto si legge dal vivo.** Il costruttore con la lambda la rivaluta ogni volta che il menu si ridisegna:
 * se legge uno stato di Compose, la voce che lo mostra si aggiorna mentre il menu e' aperto. E' cio' che serve a una
 * voce con [FluidContextAction.keepsMenuOpen] (un interruttore che resta sotto il dito e deve mostrare la spunta
 * nuova). Una lista passata al costruttore secondario, invece, e' fotografata li', come una lista qualunque.
 *
 * ```kotlin
 * contextActions = {
 *   FluidMenuActions(order = FluidMenuOrder.Priority, elementSize = FluidMenuElementSize.Small) {
 *     listOf(
 *       FluidContextAction("Condividi", FluidIcons.Share) { … },
 *       FluidContextAction("Preferiti", FluidIcons.Filled.Star) { … },
 *       FluidContextAction("Griglia", selected = grid, keepsMenuOpen = true, startsSection = true) { grid = !grid },
 *     )
 *   }
 * }
 * ```
 *
 * L'uguaglianza e' quella di una lista (le voci), come chiede il contratto di `List`: due menu con le stesse voci e
 * un ordine diverso sono uguali come liste. Fila, ordine e sottomenu valgono nell'host dei modali e in
 * [dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost].
 */
@Stable
class FluidMenuActions(
  val order: FluidMenuOrder = FluidMenuOrder.Automatic,
  val elementSize: FluidMenuElementSize = FluidMenuElementSize.Large,
  private val content: () -> List<FluidContextAction>,
) : AbstractList<FluidContextAction>() {

  /** Una lista fissa, fotografata qui: per un menu che non cambia mentre e' aperto. */
  constructor(
    actions: List<FluidContextAction>,
    order: FluidMenuOrder = FluidMenuOrder.Automatic,
    elementSize: FluidMenuElementSize = FluidMenuElementSize.Large,
  ) : this(order, elementSize, fixedFluidMenuContent(actions))

  /** Le voci di adesso: una lettura sola, da usare quando le si scorre tutte. */
  fun current(): List<FluidContextAction> = content()

  override val size: Int get() = content().size

  override fun get(index: Int): FluidContextAction = content()[index]

  override fun iterator(): Iterator<FluidContextAction> = content().iterator()

  override fun toString(): String = "FluidMenuActions(order=$order, elementSize=$elementSize, ${content()})"
}

/** Il contenuto di una lista fissa: una copia, cosi' una lista mutabile cambiata dopo non cambia il menu. */
private fun fixedFluidMenuContent(actions: List<FluidContextAction>): () -> List<FluidContextAction> {
  val fixed = actions.toList()
  return { fixed }
}

/** L'ordine dichiarato da una lista di voci: quello di [FluidMenuActions], o [FluidMenuOrder.Automatic]. */
internal fun fluidMenuOrderOf(actions: List<FluidContextAction>): FluidMenuOrder =
  (actions as? FluidMenuActions)?.order ?: FluidMenuOrder.Automatic

/** La taglia dichiarata da una lista di voci: quella di [FluidMenuActions], o [FluidMenuElementSize.Large]. */
internal fun fluidMenuElementSizeOf(actions: List<FluidContextAction>): FluidMenuElementSize =
  (actions as? FluidMenuActions)?.elementSize ?: FluidMenuElementSize.Large

/** Le voci di adesso, lette una volta: il contenuto vivo di [FluidMenuActions], o la lista com'e'. */
internal fun fluidMenuSnapshot(actions: List<FluidContextAction>): List<FluidContextAction> =
  (actions as? FluidMenuActions)?.current() ?: actions

/**
 * Il menu si apre verso l'alto? Il pannello sta piu' in alto dell'ancora: cresce dal suo bordo basso (un tasto in
 * basso, una riga vicina al fondo dello schermo).
 */
internal fun fluidMenuOpensUpward(anchor: Rect, panel: Rect): Boolean = panel.center.y < anchor.center.y

/** Il capovolgimento serve solo verso l'alto, quando la priorità segue il punto d'interazione. */
internal fun fluidMenuReverses(order: FluidMenuOrder, opensUpward: Boolean, fromButton: Boolean): Boolean =
  opensUpward && when (order) {
    FluidMenuOrder.Automatic -> fromButton
    FluidMenuOrder.Priority -> true
    FluidMenuOrder.Fixed -> false
  }

/** Quante voci stanno nella fila in cima per [size]: 4, 3, nessuna. */
internal fun fluidMenuRowCapacity(size: FluidMenuElementSize): Int = when (size) {
  FluidMenuElementSize.Small -> 4
  FluidMenuElementSize.Medium -> 3
  FluidMenuElementSize.Large -> 0
}

/** Come si disegna una cella del pannello. */
internal enum class FluidMenuCellKind {
  /** Una riga della lista: etichetta, sottotitolo, icona, spunta o gallone. */
  Row,

  /** Una tessera della fila in cima ([FluidMenuElementSize.Small] o [FluidMenuElementSize.Medium]). */
  Tile,

  /** In cima a un sottomenu, la voce madre: sceglierla torna al menu di prima. */
  Header,
}

/**
 * Una cella del pannello nell'ordine in cui si vede: la voce, come si disegna, se apre una sezione, e da quale
 * indice della lista dichiarata viene ([source]; −1 per l'intestazione di un sottomenu). La scelta col dito parla
 * di celle; l'host le traduce in voci.
 */
@Immutable
internal class FluidMenuEntry(
  val action: FluidContextAction,
  val kind: FluidMenuCellKind,
  val startsSection: Boolean,
  val source: Int,
) {
  /** La cella si sceglie: una voce spenta no; l'intestazione si', e torna indietro. */
  val enabled: Boolean get() = kind == FluidMenuCellKind.Header || action.enabled

  /** La cella apre un sottomenu: ha figli, e non e' gia' l'intestazione di quel sottomenu. */
  val opensSubmenu: Boolean get() = kind != FluidMenuCellKind.Header && action.children.isNotEmpty()
}

/**
 * Le celle del menu di primo livello, nell'ordine in cui si vedono.
 *
 * - La fila: con [elementSize] [FluidMenuElementSize.Small] o [FluidMenuElementSize.Medium] le prime voci della prima
 *   sezione (fino a [fluidMenuRowCapacity]) diventano tessere; il resto della sezione continua nella lista, separato
 *   dalla fila da una banda.
 * - Il capovolgimento ([reversed], vedi [fluidMenuReverses]): le sezioni della lista in ordine inverso e le voci di
 *   ogni sezione pure, cosi' la prima voce dichiarata sta in fondo, accanto al dito; la fila va in fondo anch'essa,
 *   con le tessere nell'ordine scritto (una fila si legge da sinistra). Le bande seguono le sezioni: apre una
 *   sezione la prima voce di ogni sezione capovolta.
 *
 * Senza fila e senza capovolgimento e' la lista dichiarata, voce per voce, con i suoi `startsSection`.
 */
internal fun fluidMenuEntries(
  actions: List<FluidContextAction>,
  elementSize: FluidMenuElementSize,
  reversed: Boolean,
): List<FluidMenuEntry> {
  val firstSectionEnd = (1 until actions.size).firstOrNull { actions[it].startsSection } ?: actions.size
  val rowCount = minOf(fluidMenuRowCapacity(elementSize), firstSectionEnd)
  val tiles = (0 until rowCount).map { i -> FluidMenuEntry(actions[i], FluidMenuCellKind.Tile, false, i) }
  val list = (rowCount until actions.size).map { i ->
    // La prima voce della lista dopo la fila apre una sezione: la banda separa la fila dalla lista.
    val starts = if (i == rowCount) rowCount > 0 else actions[i].startsSection
    FluidMenuEntry(actions[i], FluidMenuCellKind.Row, starts, i)
  }
  if (!reversed) return tiles + list
  val flipped = fluidMenuReverseSections(list)
  if (tiles.isEmpty()) return flipped
  val row = tiles.mapIndexed { k, e -> FluidMenuEntry(e.action, e.kind, k == 0 && flipped.isNotEmpty(), e.source) }
  return flipped + row
}

/**
 * Le celle di un sottomenu: in cima la voce madre ([parent], l'intestazione che torna indietro) e sotto i suoi
 * figli, con le loro sezioni; capovolto ([reversed]) l'intestazione sta in fondo, sul bordo da cui il menu e'
 * cresciuto, e i figli salgono da li' nello stesso ordine di priorita' del menu madre. Un livello solo: i figli dei
 * figli sono righe e basta.
 */
internal fun fluidMenuSubmenuEntries(parent: FluidContextAction, reversed: Boolean): List<FluidMenuEntry> {
  val header = FluidMenuEntry(parent, FluidMenuCellKind.Header, false, -1)
  val children = parent.children.mapIndexed { i, child ->
    val leaf = if (child.children.isEmpty()) child else child.copy(children = emptyList())
    FluidMenuEntry(leaf, FluidMenuCellKind.Row, i > 0 && child.startsSection, i)
  }
  if (!reversed) return listOf(header) + children
  return fluidMenuReverseSections(children) + header
}

/** Le sezioni in ordine inverso, e le voci di ogni sezione pure; apre una sezione la prima di ogni sezione. */
private fun fluidMenuReverseSections(list: List<FluidMenuEntry>): List<FluidMenuEntry> {
  if (list.isEmpty()) return list
  val sections = ArrayList<List<FluidMenuEntry>>()
  var current = ArrayList<FluidMenuEntry>()
  list.forEachIndexed { i, e ->
    if (i > 0 && e.startsSection) {
      sections += current
      current = ArrayList()
    }
    current += e
  }
  sections += current
  val out = ArrayList<FluidMenuEntry>(list.size)
  for (section in sections.asReversed()) {
    section.asReversed().forEachIndexed { k, e ->
      out += FluidMenuEntry(e.action, e.kind, k == 0 && out.isNotEmpty(), e.source)
    }
  }
  return out
}

/**
 * Le misure verticali delle celle, in pixel: la riga di sempre, quella col sottotitolo, la tessera della fila, il
 * padding del pannello e la banda di sezione.
 */
@Immutable
internal data class FluidMenuStructureMetrics(
  val rowHeightPx: Float,
  val subtitleRowHeightPx: Float,
  val tileHeightPx: Float,
  val paddingPx: Float,
  val sectionGapPx: Float,
)

/**
 * Dove stanno le celle dentro il pannello (coordinate del pannello): per ogni cella il bordo alto, l'altezza, la
 * colonna nella sua fila e quante colonne ha la fila (1 per una riga); l'altezza totale; il bordo alto di ogni
 * banda di sezione.
 *
 * Una riga occupa tutta la larghezza, come le celle di sempre; le tessere di una fila se la dividono in parti
 * uguali. L'aritmetica verticale e' quella di `fluidMenuPanelLayout`, operazione per operazione: un menu senza fila
 * e senza sottotitoli ha le stesse celle al bit.
 */
internal class FluidMenuStructure(
  val entries: List<FluidMenuEntry>,
  val tops: FloatArray,
  val heights: FloatArray,
  val columns: IntArray,
  val columnCounts: IntArray,
  val height: Float,
  val sectionBands: FloatArray,
  val sectionGapPx: Float,
) {
  val size: Int get() = entries.size

  /** La cella [i] in coordinate del pannello largo [width]. */
  fun cell(i: Int, width: Float): Rect {
    val n = columnCounts[i]
    if (n <= 1) return Rect(0f, tops[i], width, tops[i] + heights[i])
    val left = width * columns[i] / n
    val right = width * (columns[i] + 1) / n
    return Rect(left, tops[i], right, tops[i] + heights[i])
  }

  /** Le celle [a] e [b] sono tessere affiancate sulla stessa fila. */
  fun sameLine(a: Int, b: Int): Boolean = columnCounts[a] > 1 && columnCounts[b] > 1 && tops[a] == tops[b]

  /** La geometria del hit-test per un pannello che sta a [panel] (pixel della radice). */
  fun geometry(panel: Rect): FluidMenuGeometry = FluidMenuGeometry(
    cells = List(size) { i ->
      // Una riga con la stessa aritmetica di `fluidMenuGeometryOf`, perche' una lista di sempre dia le stesse celle.
      if (columnCounts[i] <= 1) {
        Rect(panel.left, panel.top + tops[i], panel.right, panel.top + tops[i] + heights[i])
      } else {
        val c = cell(i, panel.width)
        Rect(panel.left + c.left, panel.top + c.top, panel.left + c.right, panel.top + c.bottom)
      }
    },
    enabled = entries.map { it.enabled },
    panel = panel,
  )
}

/** [FluidMenuStructure] per [entries] con le misure di [m]. */
internal fun fluidMenuStructure(entries: List<FluidMenuEntry>, m: FluidMenuStructureMetrics): FluidMenuStructure {
  val n = entries.size
  val tops = FloatArray(n)
  val heights = FloatArray(n)
  val columns = IntArray(n)
  val counts = IntArray(n)
  val bands = ArrayList<Float>()
  var y = m.paddingPx
  var i = 0
  while (i < n) {
    val e = entries[i]
    if (i > 0 && e.startsSection) {
      bands += y
      y += m.sectionGapPx
    }
    if (e.kind == FluidMenuCellKind.Tile) {
      var j = i + 1
      while (j < n && entries[j].kind == FluidMenuCellKind.Tile && !entries[j].startsSection) j++
      for (k in i until j) {
        tops[k] = y
        heights[k] = m.tileHeightPx
        columns[k] = k - i
        counts[k] = j - i
      }
      y += m.tileHeightPx
      i = j
    } else {
      val h = if (e.kind == FluidMenuCellKind.Row && e.action.subtitle != null) m.subtitleRowHeightPx else m.rowHeightPx
      tops[i] = y
      heights[i] = h
      counts[i] = 1
      y += h
      i++
    }
  }
  return FluidMenuStructure(entries, tops, heights, columns, counts, y + m.paddingPx, bands.toFloatArray(), m.sectionGapPx)
}

/**
 * La taglia della goccia a [value] (indice frazionario di cella, come [fluidMenuDropCenter]): fra una tessera e una
 * riga cambia misura scivolando, non a scatti. In una lista di righe uguali e' la taglia di una riga.
 */
internal fun fluidMenuDropCellSize(structure: FluidMenuStructure, width: Float, value: Float): Size {
  val n = structure.size
  if (n == 0) return Size(width, 0f)
  if (n == 1) return structure.cell(0, width).size
  val v = if (value.isNaN()) 0f else value.coerceIn(0f, (n - 1).toFloat())
  val i = floor(v).toInt().coerceAtMost(n - 2)
  val a = structure.cell(i, width).size
  val b = structure.cell(i + 1, width).size
  val t = v - i
  return Size(a.width + (b.width - a.width) * t, a.height + (b.height - a.height) * t)
}

/**
 * Il pannello di un sottomenu, che cresce DENTRO quello del menu madre ([parent]): stesso centro orizzontale, lo
 * stesso bordo da cui il menu era cresciuto (l'alto, o il basso se si apriva verso l'alto: [upward]) e la taglia
 * nuova, sempre dentro i margini dell'host. Il vetro cambia forma restando dov'era: e' lo stesso menu che diventa
 * un altro, non un secondo menu accanto.
 */
internal fun fluidMenuSubmenuPanel(
  parent: Rect,
  width: Float,
  height: Float,
  host: Size,
  marginPx: Float,
  upward: Boolean,
): Rect {
  val w = width.coerceAtMost((host.width - marginPx * 2f).coerceAtLeast(1f))
  val left = (parent.center.x - w / 2f).coerceIn(marginPx, (host.width - marginPx - w).coerceAtLeast(marginPx))
  val maxTop = (host.height - marginPx - height).coerceAtLeast(marginPx)
  val top = if (upward) (parent.bottom - height).coerceIn(marginPx, maxTop) else parent.top.coerceIn(marginPx, maxTop)
  return Rect(left, top, left + w, top + height)
}

/*
 * Il cambio di livello (aprire un sottomenu, tornare indietro) a `u`, il suo tempo in frazioni di T (300 ms, il cambio di
 * contenuto di F3.4), da 0 a [FluidMenuLevelEnd]: le voci di prima se ne vanno sfocandosi e quelle nuove arrivano
 * sfocate e si mettono a fuoco (R7: cio' che cambia identita' passa dalla sfocatura). Due strati, con le curve di F3.4,
 * che si incrociano a ≈0,47 T con la somma delle opacita' ≈0,7: il vetro non resta mai vuoto. Senza sfocatura (API
 * sotto 31) le curve del ripiego, che si passano il posto senza sovrapporsi: due testi nitidi uno sull'altro sono
 * peggio di un buco breve.
 */

/** L'opacita' del livello che arriva a [u]. */
internal fun fluidMenuLevelEnterAlpha(u: Float, blur: Boolean): Float = GlassContentCurves.alpha(GlassContentRole.Enter, u, blur)

/** L'opacita' del livello che se ne va a [u]: zero da [GlassContentDefaults.ExitEnd] in poi. */
internal fun fluidMenuLevelExitAlpha(u: Float, blur: Boolean): Float = GlassContentCurves.alpha(GlassContentRole.Exit, u, blur)

/** La sfocatura del livello che arriva, in frazione del massimo: sale fino a meta' e torna a zero entro T. */
internal fun fluidMenuLevelEnterBlur(u: Float): Float = GlassContentCurves.blur(GlassContentRole.Enter, u)

/** La sfocatura del livello che se ne va: sale fino a meta' e resta, finche' non e' sparito. */
internal fun fluidMenuLevelExitBlur(u: Float): Float = GlassContentCurves.blur(GlassContentRole.Exit, u)

/** La scala del livello che arriva: 93% fino a 0,62 T, poi 1. */
internal fun fluidMenuLevelEnterScale(u: Float): Float = GlassContentCurves.scale(GlassContentRole.Enter, u)

/** La scala del livello che se ne va: ferma, poi 90%. */
internal fun fluidMenuLevelExitScale(u: Float): Float = GlassContentCurves.scale(GlassContentRole.Exit, u)

/** Fin dove arriva `u`: l'opacita' di chi entra tocca 1 a 1,15 T (R7). */
internal const val FluidMenuLevelEnd: Float = GlassContentDefaults.EnterEnd

/**
 * L'altezza di una riga col sottotitolo: le due righe di testo e [FluidMenuSubtitleRowPadding], mai meno di una riga
 * di sempre. Segue il font come quella ([fluidMenuRowHeight]): al testo grande cresce invece di tagliare.
 */
internal fun fluidMenuSubtitleRowHeight(lineHeight: Dp, subtitleLineHeight: Dp): Dp =
  maxOf(FluidMenuMinRowHeight.value, (lineHeight + subtitleLineHeight + FluidMenuSubtitleRowPadding).value).dp

/** L'altezza di una tessera [FluidMenuElementSize.Medium]: simbolo, etichetta e il loro respiro, almeno 64 dp. */
internal fun fluidMenuMediumTileHeight(labelLineHeight: Dp): Dp =
  maxOf(
    FluidMenuMediumTileMinHeight.value,
    (FluidMenuRowGlyph + FluidMenuTileGap + labelLineHeight + FluidMenuMediumTilePadding).value,
  ).dp

/** Il respiro sopra e sotto le due righe di una voce col sottotitolo (8 + 8 dp: una riga sola ne ha 12 + 12). */
internal val FluidMenuSubtitleRowPadding = 16.dp

/** Il minimo di una tessera con l'etichetta. */
internal val FluidMenuMediumTileMinHeight = 64.dp

/** Il respiro sopra e sotto simbolo ed etichetta di una tessera (12 + 12 dp, come una riga). */
internal val FluidMenuMediumTilePadding = 24.dp

/** La sfocatura massima del cambio di livello: quella di un glifo da 26 dp in un cambio di contenuto (F3.4). */
internal val FluidMenuLevelPeakBlurDp: Float =
  GlassContentDefaults.PeakBlur * GlassContentDefaults.GlyphFraction * GlassContentDefaults.BlurReference.value
