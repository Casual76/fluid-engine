package dev.antigravity.fluidengine.ai.orchestrator

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

enum class ConfirmationOutcome { CONFIRMED, REJECTED, TIMEOUT }

/**
 * Il cancello delle conferme: un tool che vuole scrivere chiama [ask] e resta sospeso finche' la
 * UI, che osserva [current], non chiama [resolve] — o finche' non scade il tempo. Una sola
 * conferma per volta: una seconda richiesta mentre la prima aspetta la fa cadere come rifiutata,
 * perche' due tasti Conferma nella stessa card sarebbero una domanda a cui nessuno sa rispondere.
 *
 * L'oggetto dell'azione non passa di qui: l'app lo tiene per se' e qui arrivano solo le parole con
 * cui mostrarla ([PendingConfirmation]).
 */
class AiConfirmationGate(private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {

  private class Waiting(val request: PendingConfirmation, val answer: CompletableDeferred<Boolean>)

  private val ids = AtomicLong(1)
  private val state = MutableStateFlow<PendingConfirmation?>(null)
  private var waiting: Waiting? = null

  val current: StateFlow<PendingConfirmation?> = state

  suspend fun ask(title: String, detail: String?): ConfirmationOutcome {
    waiting?.answer?.complete(false)
    val request = Waiting(PendingConfirmation(ids.getAndIncrement(), title, detail), CompletableDeferred())
    waiting = request
    state.value = request.request
    return try {
      when (withTimeoutOrNull(timeoutMillis) { request.answer.await() }) {
        true -> ConfirmationOutcome.CONFIRMED
        false -> ConfirmationOutcome.REJECTED
        null -> ConfirmationOutcome.TIMEOUT
      }
    } finally {
      if (waiting === request) {
        waiting = null
        state.value = null
      }
    }
  }

  fun resolve(id: Long, confirmed: Boolean) {
    val current = waiting ?: return
    if (current.request.id != id) return
    current.answer.complete(confirmed)
  }

  /** La domanda e' stata fermata: chi aspettava riceve un no. */
  fun cancel() {
    waiting?.answer?.complete(false)
  }

  companion object {
    const val DEFAULT_TIMEOUT_MILLIS = 60_000L
  }
}
