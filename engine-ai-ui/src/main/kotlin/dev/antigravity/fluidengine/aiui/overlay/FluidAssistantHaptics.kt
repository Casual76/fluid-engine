package dev.antigravity.fluidengine.aiui.overlay

import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent

/**
 * Cosa si sente, e quando, mentre l'assistente lavora. L'assistente e' la parte dell'app che si
 * usa senza guardarla: l'ascolto che parte, il parlato riconosciuto, l'ascolto che finisce e la
 * risposta pronta si sentono sotto il dito.
 *
 * Il conto stava nell'overlay, in quattro variabili e due effetti; qui e' una macchina a stati
 * senza Compose, perche' le regole che contano sono tutte "una volta sola": un ascolto comincia una
 * volta, il primo parlato si segnala una volta, la risposta e' pronta una volta, un secondo di
 * attesa e' un tick e non due. Provata da `FluidAssistantHapticsTest`.
 *
 * Ogni evento passa poi da `FluidHaptics`, che fonde i colpi a meno di 70 ms: due momenti detti da
 * due componenti non fanno un toc-toc.
 */
class FluidAssistantHapticTracker {
  private var listening = false
  private var spoke = false
  private var answered = false
  private var waitSecond = -1

  /** Il livello del microfono ha detto che si sta parlando: il primo parlato dell'ascolto si segnala, i successivi no. */
  fun onSpeaking(speaking: Boolean): FluidHapticEvent? {
    if (!listening || !speaking || spoke) return null
    spoke = true
    return FluidHapticEvent.SpeechDetected
  }

  /** Gli eventi di uno stato nuovo, nell'ordine in cui si suonano. */
  fun onState(state: AssistantState): List<FluidHapticEvent> {
    val events = ArrayList<FluidHapticEvent>(2)
    if (state is AssistantState.Listening) {
      if (!listening) {
        listening = true
        spoke = false
        events += FluidHapticEvent.ListenStart
      }
    } else if (listening) {
      listening = false
      events += FluidHapticEvent.ListenEnd
    }
    when (state) {
      is AssistantState.Done -> if (!answered) {
        answered = true
        events += FluidHapticEvent.ReplyReady
      }
      is AssistantState.Failed -> events += FluidHapticEvent.Error
      is AssistantState.Cancelled -> events += FluidHapticEvent.Stop
      is AssistantState.SwitchingProvider -> events += FluidHapticEvent.ProviderSwitched
      is AssistantState.WaitingRateLimit -> if (state.secondsLeft != waitSecond) {
        waitSecond = state.secondsLeft
        events += FluidHapticEvent.WaitTick
      }
      else -> Unit
    }
    if (state !is AssistantState.Done) answered = false
    return events
  }
}
