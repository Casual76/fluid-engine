package dev.antigravity.fluidengine.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Un suggerimento al primo uso (1.20.0): due parole di titolo, **una frase**, e il gesto disegnato
 * se il gesto e' la cosa da spiegare. Non e' un tour: e' quello che una persona accanto direbbe
 * indicando l'elemento, la prima volta che lo si incontra.
 *
 * [priority] decide chi parla per primo quando piu' suggerimenti della stessa schermata sono
 * pronti; [anchorId] e' l'elemento a cui il callout si aggancia (di solito ha il nome del
 * suggerimento). Le parole dei tasti stanno in [FluidTutorialLabels]: sono le stesse per tutti.
 */
data class FluidTutorial(
  val id: String,
  val priority: Int,
  val title: String,
  val text: String,
  val hint: FluidGestureHint? = null,
  val anchorId: String = id,
)

/** Il gesto che il callout mima: un dito disegnato vale piu' di una frase che lo descrive. */
enum class FluidGestureHint { Tap, LongPress, SwipeHorizontal, DragReorder, Scrub, LongPressAndTap }

/** Le parole dei tasti del callout, in lingua dell'app. */
data class FluidTutorialLabels(
  val dismiss: String,
  val next: String,
  val optOut: String,
)

/**
 * Quando un suggerimento puo' comparire. Pura di proposito: le regole sono tutte di tempo e di
 * stato, e un tempo si prova con un orologio finto meglio che con un dito.
 *
 * Le condizioni, tutte insieme: l'ancora e' sullo schermo; sono passati [quietMillis] dall'ultima
 * interazione (nessun dito, nessuno scorrimento) e dall'ultimo caricamento; non c'e' un pannello
 * dell'engine in scena; dalla chiusura del precedente c'e' stata almeno un'interazione. Fra piu'
 * candidati vince la priorita' piu' alta, a parita' l'ordine in cui sono stati offerti.
 */
class FluidTutorialPolicy(val quietMillis: Long = DEFAULT_QUIET_MILLIS) {

  fun choose(
    candidates: List<FluidTutorial>,
    anchors: Map<String, Rect>,
    nowMillis: Long,
    lastInteractionMillis: Long,
    loading: Boolean,
    modalPresenting: Boolean,
    awaitingInteraction: Boolean,
  ): FluidTutorial? {
    if (candidates.isEmpty()) return null
    if (loading || modalPresenting || awaitingInteraction) return null
    if (nowMillis - lastInteractionMillis < quietMillis) return null
    return candidates
      .filter { tutorial ->
        val bounds = anchors[tutorial.anchorId]
        bounds != null && bounds.width > 0f && bounds.height > 0f
      }
      .maxByOrNull { it.priority }
  }

  companion object {
    /** Sei decimi di secondo: il tempo in cui una mano che stava facendo qualcosa si ferma. */
    const val DEFAULT_QUIET_MILLIS = 600L

    /** Ogni quanto la politica si richiede se e' il momento, mentre qualcosa e' in attesa. */
    const val TICK_MILLIS = 120L

    /** Lo stesso, quando non c'e' niente da mostrare: il ciclo resta, ma quasi fermo. */
    const val IDLE_TICK_MILLIS = 400L
  }
}

/**
 * Lo stato del padrone di casa dei suggerimenti: chi e' in coda, dove sono gli elementi, quando
 * l'utente ha toccato per l'ultima volta, e chi e' in scena adesso (uno solo per volta).
 *
 * Le schermate offrono i loro candidati e marcano gli elementi con [Modifier.fluidTutorialAnchor];
 * l'app decide quali offrire (i non ancora visti) e cosa fare quando uno si chiude. Qui dentro non
 * c'e' nessuna memoria: un suggerimento visto lo ricorda l'app, non il componente.
 */
@Stable
class FluidTutorialHostState internal constructor(
  internal val policy: FluidTutorialPolicy,
  private val clock: () -> Long,
) {

  /**
   * I limiti degli elementi ancorati. Una mappa NORMALE, non uno stato: `onGloballyPositioned`
   * scrive qui a ogni passata di layout (a ogni fotogramma, mentre una lista scorre), e una mappa
   * di snapshot avrebbe invalidato chiunque l'avesse letta ogni volta — il padrone di casa, cioe'
   * la radice dell'app. La home ne usciva a scatti. Quello che serve alla composizione e' una cosa
   * sola, ed e' [presentingAnchor].
   */
  private val anchorBounds = HashMap<String, Rect>()
  private val candidates = mutableStateListOfCandidates()
  private var screenKey by mutableStateOf<String?>(null)
  private var loadingCount by mutableIntStateOf(0)
  private var lastInteractionAt by mutableStateOf(clock())
  private var awaitingInteraction by mutableStateOf(false)

  /** Il suggerimento in scena, se c'e'. Uno per volta, sempre. */
  var presenting: FluidTutorial? by mutableStateOf(null)
    private set

  /** Quanti ne restano in coda per questa schermata: serve al ciclo per andare in letargo. */
  val pendingCount: Int get() = candidates.size

  /** Chiamato quando un suggerimento entra in scena: l'app lo segna come visto. */
  var onShown: (String) -> Unit = {}

  /** Chiamato alla chiusura: `optOut` vero se l'utente ha chiesto di non vederne piu'. */
  var onDismissed: (id: String, optOut: Boolean) -> Unit = { _, _ -> }

  /** Offre un candidato per la schermata corrente. Ripetibile: il doppione non entra due volte. */
  fun offer(tutorial: FluidTutorial, screen: String) {
    if (screen != screenKey) return
    if (candidates.any { it.id == tutorial.id }) return
    if (presenting?.id == tutorial.id) return
    candidates += tutorial
  }

  /** Toglie un candidato dalla coda (l'app l'ha appena segnato visto, o non serve piu'). */
  fun withdraw(id: String) {
    candidates.removeAll { it.id == id }
    if (presenting?.id == id) {
      presenting = null
      presentingAnchor = null
    }
  }

  /**
   * Dove sta l'elemento del suggerimento in scena. Questo si' e' uno stato: il callout lo segue
   * se l'elemento si sposta, e assegnare un valore uguale non ricompone niente.
   */
  var presentingAnchor: Rect? by mutableStateOf(null)
    private set

  /** I limiti dell'elemento, in coordinate della radice; null quando esce di scena. */
  fun anchorBounds(id: String, bounds: Rect?) {
    if (bounds == null) anchorBounds.remove(id) else anchorBounds[id] = bounds
    if (id == presenting?.anchorId) presentingAnchor = bounds
  }

  /**
   * Dove sta il callout adesso, in coordinate della radice: serve a capire se un tocco e' sul
   * callout o sulla pagina. Lo scrive il padrone di casa quando lo posiziona.
   */
  internal var calloutBounds: Rect? = null

  /**
   * Un tocco sulla pagina, visto da chi ospita i suggerimenti. Vale come interazione (e quindi
   * sblocca il suggerimento successivo) e, se cade fuori dal callout, lo chiude.
   *
   * Sta qui e non dentro un modificatore perche' e' la regola, non il gesto: cosi' si prova senza
   * un dito e senza uno schermo.
   */
  fun touchAt(position: Offset) {
    interacted()
    if (presenting == null) return
    val bounds = calloutBounds
    if (bounds == null || !bounds.contains(position)) dismiss()
  }

  /** Un dito, uno scorrimento, un tasto: il conto della quiete riparte da qui. */
  fun interacted() {
    lastInteractionAt = clock()
    awaitingInteraction = false
  }

  /** Mentre una schermata carica non si parla: la pagina non e' ancora quella che si vedra'. */
  fun loading(active: Boolean) {
    loadingCount = (loadingCount + if (active) 1 else -1).coerceAtLeast(0)
  }

  /** Cambio di schermata: la coda della precedente non ha piu' senso, e la quiete riparte. */
  fun screenChanged(key: String?) {
    if (key == screenKey) return
    screenKey = key
    candidates.clear()
    presenting = null
    presentingAnchor = null
    awaitingInteraction = false
    lastInteractionAt = clock()
  }

  /**
   * Chiude quello in scena. Dopo, il successivo aspetta **un'interazione**: due callout di fila
   * senza che l'utente abbia fatto niente si leggono come un tutorial obbligatorio, che e'
   * esattamente quello che questa fase non vuole essere.
   */
  fun dismiss(optOut: Boolean = false) {
    val current = presenting ?: return
    presenting = null
    presentingAnchor = null
    awaitingInteraction = true
    lastInteractionAt = clock()
    onDismissed(current.id, optOut)
  }

  /** Un giro della politica. Lo chiama il padrone di casa, a intervalli. */
  internal fun evaluate(modalPresenting: Boolean) {
    if (presenting != null) return
    val chosen = policy.choose(
      candidates = candidates.toList(),
      anchors = anchorBounds,
      nowMillis = clock(),
      lastInteractionMillis = lastInteractionAt,
      loading = loadingCount > 0,
      modalPresenting = modalPresenting,
      awaitingInteraction = awaitingInteraction,
    ) ?: return
    candidates.removeAll { it.id == chosen.id }
    presenting = chosen
    presentingAnchor = anchorBounds[chosen.anchorId]
    onShown(chosen.id)
  }
}

private fun mutableStateListOfCandidates() = androidx.compose.runtime.mutableStateListOf<FluidTutorial>()

/** Il padrone di casa della schermata corrente; null dove i suggerimenti non sono montati. */
val LocalFluidTutorialHostState: ProvidableCompositionLocal<FluidTutorialHostState?> =
  staticCompositionLocalOf { null }

@Composable
fun rememberFluidTutorialHostState(
  policy: FluidTutorialPolicy = FluidTutorialPolicy(),
  clock: () -> Long = System::currentTimeMillis,
): FluidTutorialHostState = remember(policy) { FluidTutorialHostState(policy, clock) }
