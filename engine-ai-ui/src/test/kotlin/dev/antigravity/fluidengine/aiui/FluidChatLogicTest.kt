package dev.antigravity.fluidengine.aiui

import dev.antigravity.fluidengine.aiui.chat.FluidBubbleOutcome
import dev.antigravity.fluidengine.aiui.chat.FluidChatScrollKeys
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.FluidComposerState
import dev.antigravity.fluidengine.aiui.chat.FluidFollowAction
import dev.antigravity.fluidengine.aiui.chat.FluidFollowRule
import dev.antigravity.fluidengine.aiui.chat.FluidOutcomeLine
import dev.antigravity.fluidengine.aiui.chat.fluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.fluidOutcomeLines
import dev.antigravity.fluidengine.aiui.strings.FluidChatStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il kit della chat: il fondo della lista (le tre regole), il tasto del composer, gli esiti di una
 * risposta senza testo. Tutto cio' che in PampAI stava dentro un `when` di un composable.
 */
class FluidChatLogicTest {

  private val slack = 100

  private fun decide(
    follow: Boolean = true,
    scrolling: Boolean = false,
    distance: Int? = 0,
    newItems: Boolean = false,
    grew: Boolean = false,
    revealed: Boolean = false,
  ) = FluidFollowRule.decide(follow, scrolling, distance, newItems, grew, revealed, slack)

  // ---- il fondo della lista ----------------------------------------------------------------------

  @Test
  fun `una domanda appena mandata porta in fondo anche da risaliti, e riaggancia il seguito`() {
    val decision = decide(follow = false, distance = 5_000, revealed = true)
    assertTrue(decision.follow)
    assertEquals(FluidFollowAction.ScrollToEnd, decision.action)
  }

  @Test
  fun `mentre il dito scorre il seguito lo decide il dito, e non si muove niente`() {
    val up = decide(follow = true, scrolling = true, distance = 400)
    assertFalse("risalito oltre lo scarto: non si segue piu'", up.follow)
    assertEquals(FluidFollowAction.None, up.action)
    val down = decide(follow = false, scrolling = true, distance = 40)
    assertTrue("tornato in fondo: si riaggancia", down.follow)
    assertEquals(FluidFollowAction.None, down.action)
    // Un ultimo item che non e' nemmeno in vista e' lontano, non "in fondo".
    assertFalse(decide(follow = true, scrolling = true, distance = null).follow)
  }

  @Test
  fun `chi e' risalito a rileggere non viene riportato giu' dalla risposta che cresce`() {
    val decision = decide(follow = false, distance = 800, grew = true, newItems = true)
    assertFalse(decision.follow)
    assertEquals(FluidFollowAction.None, decision.action)
  }

  @Test
  fun `chi e' risalito si riaggancia tornando in fondo, senza scrollare`() {
    val decision = decide(follow = false, distance = 30)
    assertTrue(decision.follow)
    assertEquals(FluidFollowAction.None, decision.action)
  }

  @Test
  fun `seguendo, un item nuovo lontano si raggiunge con la molla`() {
    val decision = decide(follow = true, distance = 900, newItems = true)
    assertEquals(FluidFollowAction.ScrollToEnd, decision.action)
    assertTrue(decision.follow)
  }

  @Test
  fun `seguendo, se l'ultimo item non e' in vista si va in fondo`() {
    assertEquals(FluidFollowAction.ScrollToEnd, decide(follow = true, distance = null).action)
  }

  @Test
  fun `seguendo, una crescita di pochi pixel si insegue senza animazione`() {
    val decision = decide(follow = true, distance = 24, grew = true)
    assertEquals(FluidFollowAction.ChaseGrowth, decision.action)
    assertTrue(decision.follow)
  }

  @Test
  fun `seguendo e fermi non si fa niente, e un dito lasciato vicino al fondo resta dov'e'`() {
    val decision = decide(follow = true, distance = 20, grew = false)
    assertEquals(FluidFollowAction.None, decision.action)
    assertTrue(decision.follow)
  }

  @Test
  fun `un item nuovo vicino al fondo non fa saltare, lo insegue la crescita`() {
    // Distanza dentro lo scarto: non e' un salto da far vedere, e' la lista che cresce.
    assertEquals(FluidFollowAction.ChaseGrowth, decide(follow = true, distance = 60, newItems = true, grew = true).action)
  }

  @Test
  fun `la card che segue la risposta, risalito si ferma e in fondo riparte`() {
    // Il dito ha portato il corpo su di piu' dello scarto: il seguito si ferma.
    assertFalse(FluidFollowRule.followColumn(follow = true, value = 100, last = 400, max = 1_000, slackPx = 30))
    // Un tremito dentro lo scarto non lo ferma.
    assertTrue(FluidFollowRule.followColumn(follow = true, value = 380, last = 400, max = 1_000, slackPx = 30))
    // Tornato in fondo (a meno dello scarto) riparte.
    assertTrue(FluidFollowRule.followColumn(follow = false, value = 980, last = 500, max = 1_000, slackPx = 30))
    // Lontano dal fondo e fermo: resta com'e'.
    assertFalse(FluidFollowRule.followColumn(follow = false, value = 500, last = 500, max = 1_000, slackPx = 30))
  }

  // ---- la chiave della lista ---------------------------------------------------------------------

  @Test
  fun `una lista per conversazione, e una nuova a ogni chat nuova`() {
    val keys = FluidChatScrollKeys()
    val first = keys.keyFor(conversationId = null, liveShown = false)
    assertEquals("nuova-0", first)
    assertEquals("c-7", keys.keyFor(7, false))
    assertEquals("c-8", keys.keyFor(8, false))
    // Tornando a una chat nuova la lista riparte: epoca nuova.
    assertEquals("nuova-1", keys.keyFor(null, false))
    // Un'altra volta senza una conversazione in mezzo: la stessa chat nuova.
    assertEquals("nuova-1", keys.keyFor(null, false))
  }

  @Test
  fun `la chat che nasce su disco mentre risponde eredita la lista, non la rifa'`() {
    val keys = FluidChatScrollKeys()
    // Una domanda in corso sulla chat nuova...
    assertEquals("nuova-0", keys.keyFor(null, liveShown = true))
    // ...e quando nasce su disco e' ancora la stessa pagina: stessa chiave.
    assertEquals("nuova-0", keys.keyFor(41, liveShown = false))
    assertEquals("nuova-0", keys.keyFor(41, liveShown = false))
    // Un'altra conversazione e' un'altra lista.
    assertEquals("c-42", keys.keyFor(42, false))
    // E 41 ritrovata dopo non e' piu' "nuova": e' una conversazione come le altre.
    assertEquals("c-41", keys.keyFor(41, false))
  }

  @Test
  fun `una chat nuova senza domanda in corso non ruba la chiave a chi nasce dopo`() {
    val keys = FluidChatScrollKeys()
    assertEquals("nuova-0", keys.keyFor(null, liveShown = false))
    // La conversazione compare senza che nessuno stesse rispondendo (arriva da fuori): e' sua.
    assertEquals("c-5", keys.keyFor(5, liveShown = false))
  }

  // ---- il composer -------------------------------------------------------------------------------

  @Test
  fun `il tasto del composer, per ogni combinazione`() {
    fun action(input: Boolean, busy: Boolean, listening: Boolean, speaking: Boolean, mic: Boolean = true) =
      fluidComposerAction(input, busy, listening, speaking, mic)
    assertEquals(FluidComposerAction.Voice, action(input = false, busy = false, listening = false, speaking = false))
    assertEquals(FluidComposerAction.Send, action(input = true, busy = false, listening = false, speaking = false))
    assertEquals(FluidComposerAction.Stop, action(input = false, busy = true, listening = false, speaking = false))
    // Lavora e c'e' gia' la domanda dopo: lo stop non toglie l'invio che la mette in coda.
    assertEquals(FluidComposerAction.StopAndQueue, action(input = true, busy = true, listening = false, speaking = false))
    assertEquals(FluidComposerAction.StopListening, action(input = false, busy = false, listening = true, speaking = false))
    assertEquals(FluidComposerAction.Mute, action(input = false, busy = false, listening = false, speaking = true))
    // Se c'e' del testo, "zitta" non occupa il posto dell'invio.
    assertEquals(FluidComposerAction.Send, action(input = true, busy = false, listening = false, speaking = true))
  }

  @Test
  fun `ascoltando il tasto e' sempre lo stop dell'ascolto, qualunque altra cosa succeda`() {
    for (input in listOf(true, false)) for (busy in listOf(true, false)) for (speaking in listOf(true, false)) {
      assertEquals(FluidComposerAction.StopListening, fluidComposerAction(input, busy, listening = true, speaking = speaking))
    }
  }

  @Test
  fun `senza microfono il campo vuoto non offre la voce`() {
    assertEquals(FluidComposerAction.Send, fluidComposerAction(hasInput = false, busy = false, listening = false, speaking = false, micAvailable = false))
    assertEquals(FluidComposerAction.Stop, fluidComposerAction(hasInput = false, busy = true, listening = false, speaking = false, micAvailable = false))
  }

  @Test
  fun `un testo che arriva da fuori si aggiunge a quello gia' scritto`() {
    val state = FluidComposerState()
    state.fill("Riassumi questo")
    assertEquals("Riassumi questo", state.text)
    state.fill("e traducilo")
    assertEquals("Riassumi questo\n\ne traducilo", state.text)
    state.sent()
    assertEquals("", state.text)
    // Uno spazio non e' testo: la prima riga non parte da due a capo.
    state.text = "  "
    state.fill("ciao")
    assertEquals("ciao", state.text)
  }

  // ---- l'esito di una risposta -------------------------------------------------------------------

  private val strings = FluidChatStrings()

  private fun lines(outcome: FluidBubbleOutcome, hasText: Boolean, busy: Boolean = false, settled: Boolean = true) =
    fluidOutcomeLines(outcome, hasText, busy, settled, strings)

  @Test
  fun `una risposta fallita senza testo dice l'errore al posto del testo, in rosso`() {
    val l = lines(FluidBubbleOutcome.Failed("Niente rete."), hasText = false)
    assertEquals(FluidOutcomeLine("Niente rete.", error = true), l.instead)
    assertNull(l.after)
  }

  @Test
  fun `una risposta fallita a meta' tiene il testo e dice l'errore sotto`() {
    val l = lines(FluidBubbleOutcome.Failed("Niente rete."), hasText = true)
    assertNull(l.instead)
    assertEquals(FluidOutcomeLine("Niente rete.", error = true), l.after)
  }

  @Test
  fun `una risposta fermata a meta' lo dice, perche' sembra tronca per un guasto`() {
    val l = lines(FluidBubbleOutcome.Cancelled, hasText = true)
    assertEquals(FluidOutcomeLine("Fermata qui.", error = false), l.after)
    assertNull(l.instead)
    // Mentre ancora si ferma non si dice: la riga arriva a lavoro finito.
    assertNull(lines(FluidBubbleOutcome.Cancelled, hasText = true, settled = false).after)
  }

  @Test
  fun `una risposta fermata prima di scrivere dice che e' stata fermata`() {
    val l = lines(FluidBubbleOutcome.Cancelled, hasText = false)
    assertEquals("Fermata prima della risposta.", l.instead?.text)
    assertFalse(l.instead!!.error)
  }

  @Test
  fun `una risposta vuota e una interrotta si dicono solo a lavoro fermo`() {
    assertEquals("Nessuna risposta: il servizio non ha scritto niente.", lines(FluidBubbleOutcome.Empty, hasText = false).instead?.text)
    assertEquals("Interrotta: l'app si è chiusa prima della risposta.", lines(FluidBubbleOutcome.Interrupted, hasText = false).instead?.text)
    // Mentre lavora lo dice la riga di stato: niente doppione.
    assertNull(lines(FluidBubbleOutcome.Empty, hasText = false, busy = true).instead)
    assertNull(lines(FluidBubbleOutcome.Interrupted, hasText = false, busy = true).instead)
  }

  @Test
  fun `una risposta che c'e' non ha niente da dire, ne' vuota ne' interrotta`() {
    for (outcome in listOf(FluidBubbleOutcome.None, FluidBubbleOutcome.Empty, FluidBubbleOutcome.Interrupted)) {
      val l = lines(outcome, hasText = true)
      assertNull("$outcome", l.instead)
      assertNull("$outcome", l.after)
    }
    assertNull(lines(FluidBubbleOutcome.None, hasText = false).instead)
  }
}
