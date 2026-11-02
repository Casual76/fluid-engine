package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.provider.ProviderId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class ConfirmationOutcome { CONFIRMED, REJECTED, TIMEOUT }

/**
 * Il cancello delle conferme: un tool che vuole scrivere chiama [ask] e resta sospeso finche' la
 * UI, che osserva [current], non chiama [resolve] — o finche' non scade il tempo.
 *
 * **Le richieste fanno la fila** (3.0.0). Il prompt invita a chiamare piu' strumenti insieme, e
 * "togli la stella a X e alla linea 6" produce due azioni nello stesso giro: prima la seconda
 * richiesta faceva cadere la prima come rifiutata, e la prima azione restava li' senza che l'utente
 * avesse detto no. Ora si chiedono una per volta, nell'ordine in cui sono arrivate: la scheda a
 * schermo e' sempre una ([current], mai due tasti Conferma nella stessa card), le altre aspettano
 * il loro turno ([queued]) e ognuna ha la sua finestra di [timeoutMillis], che comincia quando
 * compare e non quando e' stata chiesta. Una richiesta in fila la cui coroutine viene cancellata
 * esce dalla fila senza disturbare le altre.
 *
 * Chi aspetta una conferma sta dentro il tempo del suo tool: l'orchestratore somma la finestra a
 * quel tempo ([AiOrchestrator.toolTimeoutMillis]), una per ogni conferma del giro.
 *
 * **Lo stato della card lo scrive il cancello**, se gli si dice dove ([bindState]): la richiesta a
 * schermo diventa [AssistantState.AwaitingConfirmation] con le sue parole, e quando ha una risposta
 * (o scade, o la domanda viene fermata) lo stato torna com'era. Ogni app se lo scriveva da se' con
 * un collettore su [current] e una copia delle stesse righe.
 *
 * L'oggetto dell'azione non passa di qui: l'app lo tiene per se' e qui arrivano solo le parole con
 * cui mostrarla ([PendingConfirmation]).
 */
class AiConfirmationGate(
  /** Quanto l'utente ha per rispondere a una conferma. L'orchestratore lo somma al tetto dei tool che la aspettano. */
  val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {

  /** Il cancello con la finestra della configurazione: e' il modo di tenere uguali i due numeri. */
  constructor(config: AiOrchestratorConfig) : this(config.confirmationTimeoutMillis)

  private class Waiting(val request: PendingConfirmation, val answer: CompletableDeferred<Boolean>)

  /** Dove scrivere [AssistantState.AwaitingConfirmation], e il provider da dire quando lo stato non ne ha uno. */
  private class Binding(val state: MutableStateFlow<AssistantState>, val fallbackProvider: ProviderId)

  private val ids = AtomicLong(1)

  /** La fila: il `Mutex` e' equo (chi arriva prima prende il turno prima) e un'attesa si puo' cancellare. */
  private val line = Mutex()

  /** Sale a ogni [cancel]: una richiesta chiesta prima, e che prende il turno dopo, e' stata fermata. */
  private val epoch = AtomicLong(0)
  private val state = MutableStateFlow<PendingConfirmation?>(null)
  private val behind = MutableStateFlow(0)

  @Volatile
  private var waiting: Waiting? = null

  @Volatile
  private var binding: Binding? = null

  /** La conferma a schermo, o null. E' sempre una: le altre aspettano in fila. */
  val current: StateFlow<PendingConfirmation?> = state

  /** Quante richieste aspettano il loro turno dietro a quella a schermo: "1 di 3" per una card che lo dice. */
  val queued: StateFlow<Int> = behind

  /**
   * Il cancello scrive su [target] lo stato [AssistantState.AwaitingConfirmation] mentre una conferma
   * e' a schermo: la domanda e il provider sono quelli dello stato di prima (o [fallbackProvider], se
   * quello non ne ha uno: ascolto, trascrizione), le parole sono quelle della richiesta. Alla
   * risposta, allo scadere o alla [cancel] lo stato torna com'era, **a meno che nel frattempo non
   * sia cambiato** (una cancellazione, un errore): uno stato vecchio non ne calpesta uno nuovo.
   * Si scrive solo su una domanda in corso ([AssistantState.isBusy]): su uno stato a riposo, o gia'
   * finito, non si fa ricomparire niente.
   */
  fun bindState(target: MutableStateFlow<AssistantState>, fallbackProvider: ProviderId = ProviderId.defaultOrder.first()) {
    binding = Binding(target, fallbackProvider)
  }

  /** Il cancello smette di scrivere lo stato. Le conferme restano su [current]. */
  fun unbindState() {
    binding = null
  }

  suspend fun ask(title: String, detail: String?): ConfirmationOutcome {
    val turn = epoch.get()
    behind.update { it + 1 }
    var counted = true
    try {
      return line.withLock {
        behind.update { it - 1 }
        counted = false
        // Fermata mentre aspettava in fila: non compare nemmeno.
        if (turn != epoch.get()) ConfirmationOutcome.REJECTED else present(title, detail, turn)
      }
    } finally {
      // Cancellata prima di prendere il turno: non e' piu' in fila.
      if (counted) behind.update { it - 1 }
    }
  }

  private suspend fun present(title: String, detail: String?, turn: Long): ConfirmationOutcome {
    val request = Waiting(PendingConfirmation(ids.getAndIncrement(), title, detail), CompletableDeferred())
    waiting = request
    // Una [cancel] che e' arrivata fra il controllo del turno e qui non ha trovato nessuno da
    // fermare: ma ha alzato il turno prima di guardare, e noi guardiamo dopo aver pubblicato. Una
    // delle due parti la vede sempre.
    if (turn != epoch.get()) {
      waiting = null
      return ConfirmationOutcome.REJECTED
    }
    val restore = announce(request.request)
    state.value = request.request
    return try {
      when (withTimeoutOrNull(timeoutMillis) { request.answer.await() }) {
        true -> ConfirmationOutcome.CONFIRMED
        false -> ConfirmationOutcome.REJECTED
        null -> ConfirmationOutcome.TIMEOUT
      }
    } finally {
      waiting = null
      state.value = null
      restore?.invoke()
    }
  }

  /** Scrive [AssistantState.AwaitingConfirmation] se il cancello e' legato a uno stato; torna come rimetterlo. */
  private fun announce(request: PendingConfirmation): (() -> Unit)? {
    val target = binding ?: return null
    val previous = target.state.value
    if (!previous.isBusy || previous is AssistantState.AwaitingConfirmation) return null
    val own = AssistantState.AwaitingConfirmation(
      question = previous.questionOrNull().orEmpty(),
      confirmation = request,
      provider = previous.providerOrNull() ?: target.fallbackProvider,
    )
    target.state.value = own
    // Solo se e' ancora il MIO: se nel frattempo e' arrivato altro non lo si calpesta con uno vecchio.
    return { target.state.compareAndSet(own, previous) }
  }

  /** La risposta alla conferma a schermo: un [id] che non e' il suo (la richiesta di prima, una in fila) non conta. */
  fun resolve(id: Long, confirmed: Boolean) {
    val head = waiting ?: return
    if (head.request.id != id) return
    head.answer.complete(confirmed)
  }

  /**
   * La domanda e' stata fermata: chi aspettava riceve un no — la conferma a schermo e tutte quelle
   * in fila. Una richiesta che arriva dopo comincia da capo.
   */
  fun cancel() {
    epoch.incrementAndGet()
    waiting?.answer?.complete(false)
  }

  companion object {
    const val DEFAULT_TIMEOUT_MILLIS = 60_000L
  }
}
