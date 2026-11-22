package dev.antigravity.fluidengine.aiui

import androidx.compose.runtime.saveable.SaverScope
import dev.antigravity.fluidengine.aiui.chat.FluidAssistantFieldHaloProfile
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.FluidComposerState
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldAmplitude
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldMood
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldCometWeight
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldCometHead
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldBreath
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldMoves
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldPresence
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldRingAlpha
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldTempo
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantFieldTrailing
import dev.antigravity.fluidengine.aiui.chat.fluidComposerAction
import dev.antigravity.fluidengine.aiui.halo.FluidHaloMood
import dev.antigravity.fluidengine.aiui.strings.FluidChatStrings
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTrailing
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.VolumeOff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6B.7: il composer nuovo dell'assistente dentro il campo (`fluidAssistantField`). Il tasto a destra a stati dal tasto
 * del composer di prima, l'umore e i numeri dell'alone, lo stato che sopravvive alla rotazione. Il campo stesso (righe,
 * allegati, invio) è provato in engine-ui, `FluidComposerInFieldTest`.
 */
class FluidComposerInFieldTest {
  private val strings = FluidChatStrings()
  private val log = mutableListOf<String>()
  private val voice = { log += "voce"; Unit }
  private val stop = { log += "ferma"; Unit }
  private val stopListening = { log += "smetti"; Unit }
  private val mute = { log += "zitta"; Unit }

  private fun trailing(action: FluidComposerAction, hasInput: Boolean, sending: FluidActionState = FluidActionState.Idle) =
    fluidAssistantFieldTrailing(action, hasInput, strings, sending, voice, stop, stopListening, mute)

  @Test fun theRightKeyComesFromTheComposerActionAsOneKey() {
    // Il microfono a campo vuoto: il tasto del composer, che a testo scritto diventa «Invia» sulla stessa sagoma.
    val mic = trailing(FluidComposerAction.Voice, hasInput = false)
    assertSame(voice, mic.onVoice)
    assertEquals(mic, trailing(FluidComposerAction.Send, hasInput = true))
    // Senza microfono e a campo vuoto: «Invia» (che a campo vuoto è la X).
    assertEquals(FluidSearchTrailing.Send, trailing(FluidComposerAction.Send, hasInput = false))
    // Al lavoro, con o senza la prossima domanda: lo stop (la tastiera la manda in coda).
    for (action in listOf(FluidComposerAction.Stop, FluidComposerAction.StopAndQueue)) {
      val key = trailing(action, hasInput = action == FluidComposerAction.StopAndQueue)
      assertSame(stop, key.onStop)
      assertEquals(strings.stop, key.stopLabel)
      assertNull(key.stopIcon)
    }
    // In ascolto: lo stop dell'ascolto; mentre legge: «Zitta» con l'altoparlante barrato.
    val listening = trailing(FluidComposerAction.StopListening, hasInput = false)
    assertSame(stopListening, listening.onStop)
    assertEquals(strings.voiceStop, listening.stopLabel)
    val speaking = trailing(FluidComposerAction.Mute, hasInput = false)
    assertSame(mute, speaking.onStop)
    assertEquals(FluidIcons.VolumeOff, speaking.stopIcon)
    // Lo stato dell'invio (F6B.3a) arriva al tasto: mentre lavora è spento.
    val working = FluidActionState.Working()
    assertSame(working, trailing(FluidComposerAction.Send, hasInput = true, sending = working).sending)
    // Lo stesso albero di casi del composer di prima: ascoltare vince su tutto.
    for (busy in listOf(false, true)) for (input in listOf(false, true)) for (speaking2 in listOf(false, true)) {
      val action = fluidComposerAction(input, busy, listening = true, speaking = speaking2)
      assertEquals(FluidComposerAction.StopListening, action)
      assertSame(stopListening, trailing(action, input).onStop)
    }
  }

  @Test fun theHaloMoodFollowsTheAssistant() {
    assertEquals(FluidHaloMood.LISTENING, fluidAssistantFieldMood(FluidComposerAction.StopListening))
    assertEquals(FluidHaloMood.WORKING, fluidAssistantFieldMood(FluidComposerAction.Stop))
    assertEquals(FluidHaloMood.WORKING, fluidAssistantFieldMood(FluidComposerAction.StopAndQueue))
    assertEquals(FluidHaloMood.WRITING, fluidAssistantFieldMood(FluidComposerAction.Mute))
    assertEquals(FluidHaloMood.IDLE, fluidAssistantFieldMood(FluidComposerAction.Voice))
    assertEquals(FluidHaloMood.IDLE, fluidAssistantFieldMood(FluidComposerAction.Send))
    assertEquals(FluidHaloMood.ERROR, fluidAssistantFieldMood(FluidComposerAction.Voice, failed = true))
    // Mentre lavora l'errore di prima non conta: lavora.
    assertEquals(FluidHaloMood.WORKING, fluidAssistantFieldMood(FluidComposerAction.Stop, failed = true))
  }

  @Test fun theHaloIsAliveButQuietAndStillAtRest() {
    val rest = FluidHaloMood.IDLE
    // A riposo: presente ma basso, spento d'ampiezza, e **fermo**: nessun fotogramma.
    assertEquals(FluidAssistantFieldHaloProfile.RestPresence, fluidAssistantFieldPresence(rest, focused = false), 0f)
    assertEquals(0f, fluidAssistantFieldAmplitude(rest, focused = false, level = 1f), 0f)
    assertFalse(fluidAssistantFieldMoves(rest, focused = false, reducedMotion = false))
    assertFalse(fluidAssistantFieldMoves(FluidHaloMood.DONE, focused = false, reducedMotion = false))
    // Mentre si scrive: pieno, basso, e si muove (il giro di luce fa il bordo in 5 s).
    assertEquals(1f, fluidAssistantFieldPresence(rest, focused = true), 0f)
    assertEquals(FluidAssistantFieldHaloProfile.FocusAmplitude, fluidAssistantFieldAmplitude(rest, true, 0f), 0f)
    assertTrue(fluidAssistantFieldMoves(rest, focused = true, reducedMotion = false))
    assertEquals(FluidAssistantFieldHaloProfile.FocusTempo, fluidAssistantFieldTempo(rest), 0f)
    assertEquals(5f, 1f / (FluidAssistantFieldHaloProfile.CometLapsPerSec * fluidAssistantFieldTempo(rest)), 1e-4f)
    assertTrue(fluidAssistantFieldTempo(rest) < fluidAssistantFieldTempo(FluidHaloMood.LISTENING))
    // In ascolto segue la voce senza mai spegnersi fra due sillabe; al lavoro e nell'errore resta acceso e scorre.
    assertEquals(0.30f, fluidAssistantFieldAmplitude(FluidHaloMood.LISTENING, false, 0f), 1e-6f)
    assertEquals(1f, fluidAssistantFieldAmplitude(FluidHaloMood.LISTENING, false, 1f), 1e-6f)
    assertEquals(1f, fluidAssistantFieldAmplitude(FluidHaloMood.LISTENING, false, 7f), 1e-6f)
    for (mood in listOf(FluidHaloMood.WORKING, FluidHaloMood.WRITING, FluidHaloMood.ERROR)) {
      assertEquals(FluidAssistantFieldHaloProfile.BusyAmplitude, fluidAssistantFieldAmplitude(mood, false, 0f), 0f)
      assertTrue(fluidAssistantFieldMoves(mood, focused = false, reducedMotion = false))
    }
    // Al lavoro e mentre legge scorre più svelto che a campo aperto; nell'errore più piano.
    assertTrue(fluidAssistantFieldTempo(FluidHaloMood.WORKING) > fluidAssistantFieldTempo(rest))
    assertTrue(fluidAssistantFieldTempo(FluidHaloMood.WRITING) > fluidAssistantFieldTempo(rest))
    assertTrue(fluidAssistantFieldTempo(FluidHaloMood.ERROR) < fluidAssistantFieldTempo(rest))
    // Il giro più svelto resta sopra i 3 s: vivo, non una giostra.
    for (mood in FluidHaloMood.entries) {
      assertTrue("$mood", 1f / (FluidAssistantFieldHaloProfile.CometLapsPerSec * fluidAssistantFieldTempo(mood)) >= 3f)
    }
    // Nascosto: niente.
    assertEquals(0f, fluidAssistantFieldPresence(FluidHaloMood.HIDDEN, true), 0f)
    assertFalse(fluidAssistantFieldMoves(FluidHaloMood.HIDDEN, true, false))
  }

  @Test fun atRestAndWithReducedMotionTheHaloDoesNotAnimate() {
    // A riposo (campo chiuso, assistente fermo, o fatto) il moto è spento: niente orologio, niente scia, niente respiro.
    for (mood in listOf(FluidHaloMood.IDLE, FluidHaloMood.DONE)) {
      assertFalse("$mood", fluidAssistantFieldMoves(mood, focused = false, reducedMotion = false))
    }
    // Fermo, il respiro vale 1 a ogni istante: l'alfa non cambia.
    for (t in listOf(0f, 0.7f, 1.125f, 3.3f, 1000f)) assertEquals(1f, fluidAssistantFieldBreath(t, motion = 0f), 0f)
    // Col movimento ridotto niente si muove, anche col campo aperto, in ascolto o al lavoro.
    for (mood in FluidHaloMood.entries) assertFalse("$mood", fluidAssistantFieldMoves(mood, focused = true, reducedMotion = true))
    // Attivo, il respiro sta in ±12% e passa per 1 senza salti (mai un lampeggio: al più 0,3% di alfa per fotogramma, anche al lavoro).
    var previous = fluidAssistantFieldBreath(0f, 1f)
    var t = 0f
    while (t < 2 * FluidAssistantFieldHaloProfile.BreathPeriod) {
      t += 1f / 120f * FluidAssistantFieldHaloProfile.WorkingTempo
      val b = fluidAssistantFieldBreath(t, 1f)
      assertTrue(b in 0.88f..1.12f)
      assertTrue(kotlin.math.abs(b - previous) < 0.003f)
      previous = b
    }
  }

  @Test fun theLightRunsAroundTheEdgeContinuously() {
    // La testa della scia fa il giro del bordo in 1 / CometLapsPerSec di tempo dell'orologio, senza salti a 120 Hz.
    var previous = fluidAssistantFieldCometHead(0f)
    var t = 0f
    repeat(1200) {
      t += 1f / 120f
      val head = fluidAssistantFieldCometHead(t)
      val step = (head - previous + 1f) % 1f
      assertTrue(step in 0f..0.003f)
      previous = head
    }
    assertEquals(0f, fluidAssistantFieldCometHead(1f / FluidAssistantFieldHaloProfile.CometLapsPerSec), 1e-4f)
    // La scia si accende dalla coda alla testa: le fette crescono, la testa è piena.
    val n = FluidAssistantFieldHaloProfile.CometSlices
    val weights = (0 until n).map { fluidAssistantFieldCometWeight(it, n) }
    assertTrue(weights.zipWithNext().all { (a, b) -> b > a })
    assertEquals(1f, weights.last(), 0f)
    assertTrue(weights.first() < 0.1f)
    // La scia è un tratto del bordo, non un anello: meno di un quinto.
    assertTrue(FluidAssistantFieldHaloProfile.CometLength < 0.2f)
  }

  @Test fun theHaloRespectsReducedMotion() {
    // Niente si muove, e la voce non fa pulsare: l'ascolto resta al suo minimo.
    for (mood in FluidHaloMood.entries) for (focused in listOf(false, true)) {
      assertFalse("$mood $focused", fluidAssistantFieldMoves(mood, focused, reducedMotion = true))
    }
    assertEquals(0.30f, fluidAssistantFieldAmplitude(FluidHaloMood.LISTENING, false, 1f, reducedMotion = true), 1e-6f)
    // La presenza (una dissolvenza, non un movimento) resta quella di sempre.
    assertEquals(FluidAssistantFieldHaloProfile.RestPresence, fluidAssistantFieldPresence(FluidHaloMood.IDLE, false), 0f)
  }

  @Test fun theRingIsAThreadThatNeverShouts() {
    // L'anello: dal 38% a riposo, mai oltre il 78% con la voce piena; per la presenza.
    assertEquals(FluidAssistantFieldHaloProfile.RingFloorAlpha, fluidAssistantFieldRingAlpha(0f, 1f), 1e-6f)
    assertEquals(0.78f, fluidAssistantFieldRingAlpha(1f, 1f), 1e-6f)
    assertEquals(0.78f, fluidAssistantFieldRingAlpha(3f, 1f), 1e-6f)
    assertEquals(FluidAssistantFieldHaloProfile.RingFloorAlpha * FluidAssistantFieldHaloProfile.RestPresence,
      fluidAssistantFieldRingAlpha(0f, FluidAssistantFieldHaloProfile.RestPresence), 1e-6f)
    assertEquals(0f, fluidAssistantFieldRingAlpha(1f, 0f), 0f)
    // Le macchie sono più piccole di quelle del composer di prima (28 dp, alto due righe); mai oltre la metà accese.
    val spec = FluidAssistantFieldHaloProfile.Spec
    assertEquals(6, spec.blobCount)
    assertTrue(spec.blobRadius.value < 28f)
    assertTrue(spec.peakAlpha <= 0.5f)
    // A riposo l'alone si vede (non è una cornice spenta) ma resta sotto il pieno.
    assertTrue(FluidAssistantFieldHaloProfile.RestPresence in 0.5f..0.9f)
    assertEquals(0f, spec.edgeStroke.value, 0f)
    // I colori girano sull'anello in 8 s, lenti abbastanza da non fare una giostra.
    assertEquals(8f, 1f / FluidAssistantFieldHaloProfile.RingCyclesPerSec, 1e-6f)
  }

  @Test fun theDraftSurvivesTheRotation() {
    // Il testo è dello stato del composer, che si salva con la pagina: la rotazione non lo perde.
    val state = FluidComposerState("Tre righe\ndi bozza\nda finire")
    val scope = SaverScope { true }
    val saved = with(FluidComposerState.Saver) { scope.save(state) }!!
    val restored = FluidComposerState.Saver.restore(saved)!!
    assertEquals(state.text, restored.text)
    // L'invio svuota solo se la domanda è partita: lo fa l'app con `sent()`.
    restored.sent()
    assertEquals("", restored.text)
  }
}
