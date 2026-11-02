package dev.antigravity.fluidengine.aiui

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.orchestrator.ProviderSwitch
import dev.antigravity.fluidengine.ai.orchestrator.SwitchReason
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantHapticTracker
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantOverlayState
import dev.antigravity.fluidengine.aiui.overlay.FluidFailureReport
import dev.antigravity.fluidengine.aiui.overlay.FluidOverlayMode
import dev.antigravity.fluidengine.aiui.overlay.FluidSentences
import dev.antigravity.fluidengine.aiui.overlay.dockStatus
import dev.antigravity.fluidengine.aiui.overlay.micDotScale
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La logica dell'overlay: l'aptica di ogni stato, il gettone dell'ascolto, la voce, il resoconto di un errore. */
class FluidOverlayLogicTest {

  private fun done() = AssistantState.Done("q", "a", emptyList(), ProviderId.GROQ, AskMode.VOICE, null, emptyList(), 10)

  // ---- l'aptica ----------------------------------------------------------------------------------

  @Test
  fun `l'ascolto comincia una volta, finisce una volta`() {
    val tracker = FluidAssistantHapticTracker()
    assertEquals(listOf(FluidHapticEvent.ListenStart), tracker.onState(AssistantState.Listening(0)))
    // Lo stato di ascolto si riemette ogni secondo (il tempo che passa): non ricomincia.
    assertEquals(emptyList<FluidHapticEvent>(), tracker.onState(AssistantState.Listening(1_000)))
    assertEquals(listOf(FluidHapticEvent.ListenEnd), tracker.onState(AssistantState.Transcribing))
    assertEquals(emptyList<FluidHapticEvent>(), tracker.onState(AssistantState.Classifying("q", ProviderId.GROQ)))
  }

  @Test
  fun `il primo parlato si segnala una volta per ascolto, e solo ascoltando`() {
    val tracker = FluidAssistantHapticTracker()
    assertNull(tracker.onSpeaking(true))
    tracker.onState(AssistantState.Listening(0))
    assertEquals(FluidHapticEvent.SpeechDetected, tracker.onSpeaking(true))
    assertNull(tracker.onSpeaking(true))
    assertNull(tracker.onSpeaking(false))
    // Un secondo ascolto riparte da capo.
    tracker.onState(AssistantState.Transcribing)
    tracker.onState(AssistantState.Listening(0))
    assertEquals(FluidHapticEvent.SpeechDetected, tracker.onSpeaking(true))
  }

  @Test
  fun `la risposta pronta si sente una volta, anche se lo stato Done si riemette`() {
    val tracker = FluidAssistantHapticTracker()
    assertEquals(listOf(FluidHapticEvent.ReplyReady), tracker.onState(done()))
    assertEquals(emptyList<FluidHapticEvent>(), tracker.onState(done()))
    // Una domanda nuova riarma.
    tracker.onState(AssistantState.Classifying("q2", ProviderId.GROQ))
    assertEquals(listOf(FluidHapticEvent.ReplyReady), tracker.onState(done()))
  }

  @Test
  fun `errore, stop, cambio di servizio e attesa hanno il loro colpo, e l'attesa un tick per secondo`() {
    val tracker = FluidAssistantHapticTracker()
    assertEquals(listOf(FluidHapticEvent.Error), tracker.onState(AssistantState.Failed("q", FailureKind.NETWORK, null, null, null)))
    assertEquals(listOf(FluidHapticEvent.Stop), tracker.onState(AssistantState.Cancelled("q", null)))
    assertEquals(
      listOf(FluidHapticEvent.ProviderSwitched),
      tracker.onState(AssistantState.SwitchingProvider("q", ProviderId.GROQ, ProviderId.GEMINI)),
    )
    assertEquals(listOf(FluidHapticEvent.WaitTick), tracker.onState(AssistantState.WaitingRateLimit("q", ProviderId.GROQ, 5)))
    assertEquals(emptyList<FluidHapticEvent>(), tracker.onState(AssistantState.WaitingRateLimit("q", ProviderId.GROQ, 5)))
    assertEquals(listOf(FluidHapticEvent.WaitTick), tracker.onState(AssistantState.WaitingRateLimit("q", ProviderId.GROQ, 4)))
  }

  @Test
  fun `un ascolto che finisce in errore suona la fine e poi l'errore, in quest'ordine`() {
    val tracker = FluidAssistantHapticTracker()
    tracker.onState(AssistantState.Listening(0))
    assertEquals(
      listOf(FluidHapticEvent.ListenEnd, FluidHapticEvent.Error),
      tracker.onState(AssistantState.Failed(null, FailureKind.MICROPHONE, null, null, null)),
    )
  }

  // ---- lo stato dell'overlay ---------------------------------------------------------------------

  @Test
  fun `il gettone dell'ascolto si consuma una volta sola per tocco`() {
    val overlay = FluidAssistantOverlayState()
    assertFalse(overlay.consumeVoiceRequest())
    overlay.openVoice()
    assertEquals(FluidOverlayMode.VOICE, overlay.mode)
    assertTrue(overlay.consumeVoiceRequest())
    assertFalse(overlay.consumeVoiceRequest())
    overlay.openVoice()
    assertTrue(overlay.consumeVoiceRequest())
  }

  @Test
  fun `scrivere apre la barra con la tastiera, la risposta la riporta senza`() {
    val overlay = FluidAssistantOverlayState()
    overlay.openText()
    assertTrue(overlay.composing)
    assertTrue(overlay.autoFocus)
    overlay.sent()
    assertFalse(overlay.composing)
    overlay.resumeComposing()
    assertTrue(overlay.composing)
    assertFalse("riaprire con la tastiera coprirebbe la risposta appena arrivata", overlay.autoFocus)
  }

  @Test
  fun `a voce la barra di scrittura non torna`() {
    val overlay = FluidAssistantOverlayState()
    overlay.openVoice()
    overlay.resumeComposing()
    assertFalse(overlay.composing)
  }

  @Test
  fun `la pagina toccata fa da parte la card, solo se c'e'`() {
    val overlay = FluidAssistantOverlayState()
    overlay.onPageInteraction()
    assertFalse(overlay.collapsed)
    overlay.openText()
    overlay.onPageInteraction()
    assertTrue(overlay.collapsed)
    overlay.hide()
    assertFalse(overlay.collapsed)
    assertEquals(FluidOverlayMode.HIDDEN, overlay.mode)
  }

  @Test
  fun `aprire la voce o il testo riapre la card ridotta, e l'origine si tiene`() {
    val overlay = FluidAssistantOverlayState()
    overlay.originBounds = Rect(0f, 0f, 10f, 10f)
    overlay.openText()
    overlay.collapseForNavigation()
    assertTrue(overlay.collapsed)
    overlay.openVoice()
    assertFalse(overlay.collapsed)
    assertEquals(Rect(0f, 0f, 10f, 10f), overlay.originBounds)
  }

  // ---- la voce -----------------------------------------------------------------------------------

  @Test
  fun `la voce legge le frasi chiuse e tiene da parte l'ultima fino alla fine`() {
    val first = FluidSentences.next("Ciao a tutti. Come stai", 0, final = false)
    assertEquals(listOf("Ciao a tutti."), first.sentences)
    // Da dove ripartire: dopo la frase chiusa, non dopo l'ultimo pezzo.
    assertEquals("Ciao a tutti.".length, first.nextOffset)
    val second = FluidSentences.next("Ciao a tutti. Come stai?", first.nextOffset, final = false)
    assertEquals(listOf("Come stai?"), second.sentences)
    val last = FluidSentences.next("Ciao a tutti. Come stai? Bene", second.nextOffset, final = true)
    assertEquals(listOf("Bene"), last.sentences)
  }

  @Test
  fun `a risposta finita anche l'ultimo pezzo senza punto si legge`() {
    val result = FluidSentences.next("Una frase senza punto", 0, final = true)
    assertEquals(listOf("Una frase senza punto"), result.sentences)
    assertEquals("Una frase senza punto".length, result.nextOffset)
  }

  @Test
  fun `niente di nuovo, niente da leggere`() {
    assertEquals(emptyList<String>(), FluidSentences.next("Ciao.", 5, final = true).sentences)
    assertEquals(emptyList<String>(), FluidSentences.next("Ciao a", 0, final = false).sentences)
  }

  // ---- il resoconto di un errore -----------------------------------------------------------------

  @Test
  fun `il resoconto dallo stato Failed porta il codice e il messaggio del servizio`() {
    val error = AiError.Server(503, "overloaded")
    val failed = AssistantState.Failed("q", FailureKind.PROVIDER, error, null, null, provider = ProviderId.GEMINI, reason = SwitchReason.SERVER)
    val report = FluidFailureReport.of(failed)
    assertEquals(503, report.httpCode)
    assertEquals(ProviderId.GEMINI, report.provider)
    val strings = FluidAssistantStrings()
    val text = report.asText(strings)
    assertTrue(text, text.contains("Codice HTTP: 503"))
    assertTrue(text, text.contains("Servizio: Gemini"))
    assertTrue(text, text.contains("Gemini non era disponibile"))
  }

  @Test
  fun `la catena dei servizi provati e i loro perche'`() {
    val report = FluidFailureReport(
      kind = FailureKind.RATE_LIMITED,
      provider = ProviderId.OPENROUTER,
      switches = listOf(
        ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.RATE_LIMITED),
        ProviderSwitch(ProviderId.GEMINI, ProviderId.OPENROUTER, SwitchReason.TIMEOUT),
      ),
    )
    assertEquals("Groq → Gemini → OpenRouter", report.chain)
    val s = FluidAssistantStrings()
    assertEquals("Groq era al limite · Gemini non rispondeva", report.chainReasons(s))
    assertNull(FluidFailureReport(FailureKind.NETWORK, null).chain)
  }

  @Test
  fun `senza dettagli il resoconto dice solo la frase`() {
    val s = FluidAssistantStrings()
    val text = FluidFailureReport(kind = null, provider = null).asText(s)
    assertEquals("Dettagli dell'errore\nQualcosa è andato storto.", text)
  }

  // ---- le misure del pannello ancorato -------------------------------------------------------------

  @Test
  fun `il pallino del microfono segue la voce, al lavoro e' un po' piu' grande, a riposo uno`() {
    assertEquals(1f, micDotScale(0.9f, speaking = false, busy = false), 0f)
    assertEquals(1.15f, micDotScale(0.9f, speaking = false, busy = true), 0f)
    assertEquals(1.5f, micDotScale(1f, speaking = true, busy = false), 0f)
    // Un livello fuori scala non fa un pallino enorme.
    assertEquals(1.5f, micDotScale(7f, speaking = true, busy = false), 0f)
    assertEquals(1f, micDotScale(-3f, speaking = true, busy = false), 0f)
  }

  @Test
  fun `la testata del pannello`() {
    val s = FluidAssistantStrings()
    assertEquals("Chiedimi qualcosa", dockStatus(AssistantState.Idle, s))
    assertEquals("Fatto", dockStatus(done(), s))
    assertEquals("Ti ascolto…", dockStatus(AssistantState.Listening(0), s))
  }
}
