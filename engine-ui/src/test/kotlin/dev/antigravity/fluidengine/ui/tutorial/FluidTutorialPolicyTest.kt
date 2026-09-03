package dev.antigravity.fluidengine.ui.tutorial

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun tutorial(id: String, priority: Int = 0, anchor: String = id) =
  FluidTutorial(id = id, priority = priority, title = id, text = "una frase", anchorId = anchor)

private val anchors = mapOf(
  "a" to Rect(0f, 0f, 100f, 40f),
  "b" to Rect(0f, 60f, 100f, 100f),
)

class FluidTutorialPolicyTest {

  private val policy = FluidTutorialPolicy()

  private fun choose(
    candidates: List<FluidTutorial>,
    anchorMap: Map<String, Rect> = anchors,
    now: Long = 10_000,
    lastInteraction: Long = 0,
    loading: Boolean = false,
    modal: Boolean = false,
    awaiting: Boolean = false,
  ) = policy.choose(candidates, anchorMap, now, lastInteraction, loading, modal, awaiting)

  @Test
  fun `senza candidati non succede niente`() {
    assertNull(choose(emptyList()))
  }

  @Test
  fun `prima della quiete nessuno parla`() {
    val candidates = listOf(tutorial("a"))
    assertNull("mezzo secondo non basta", choose(candidates, now = 1_500, lastInteraction = 1_000))
    assertEquals(tutorial("a"), choose(candidates, now = 1_600, lastInteraction = 1_000))
  }

  @Test
  fun `un caricamento, un pannello aperto o l'attesa di un tocco rimandano`() {
    val candidates = listOf(tutorial("a"))
    assertNull(choose(candidates, loading = true))
    assertNull(choose(candidates, modal = true))
    assertNull(choose(candidates, awaiting = true))
  }

  @Test
  fun `senza l'elemento sullo schermo il suggerimento aspetta`() {
    assertNull(choose(listOf(tutorial("z"))))
    assertNull(choose(listOf(tutorial("a")), anchorMap = mapOf("a" to Rect(0f, 0f, 0f, 0f))))
  }

  @Test
  fun `fra piu' candidati vince la priorita' piu' alta`() {
    val chosen = choose(listOf(tutorial("a", priority = 1), tutorial("b", priority = 5)))
    assertEquals("b", chosen?.id)
  }

  @Test
  fun `l'ancora puo' avere un nome diverso dal suggerimento`() {
    val chosen = choose(listOf(tutorial("terzo", anchor = "b")))
    assertEquals("terzo", chosen?.id)
  }
}

class FluidTutorialHostStateTest {

  private var now = 0L
  private val state = FluidTutorialHostState(FluidTutorialPolicy()) { now }

  private fun ready(id: String, priority: Int = 0) {
    state.offer(tutorial(id, priority), "schermata")
    state.anchorBounds(id, Rect(0f, 0f, 100f, 40f))
  }

  @Test
  fun `uno per volta, e il successivo aspetta un'interazione`() {
    val shown = mutableListOf<String>()
    val dismissed = mutableListOf<Pair<String, Boolean>>()
    state.onShown = { shown += it }
    state.onDismissed = { id, optOut -> dismissed += id to optOut }
    state.screenChanged("schermata")
    ready("a", priority = 1)
    ready("b", priority = 2)

    now = 1_000
    state.evaluate(modalPresenting = false)
    assertEquals("b", state.presenting?.id)
    // Il secondo non entra da solo: prima va chiuso il primo.
    state.evaluate(modalPresenting = false)
    assertEquals(listOf("b"), shown)

    state.dismiss()
    now = 5_000
    state.evaluate(modalPresenting = false)
    assertNull("dopo una chiusura serve un'interazione", state.presenting)

    state.interacted()
    now = 6_000
    state.evaluate(modalPresenting = false)
    assertEquals("a", state.presenting?.id)
    assertEquals(listOf("b", "a"), shown)
    assertEquals(listOf("b" to false), dismissed)
  }

  @Test
  fun `chi non e' stato offerto per questa schermata non entra`() {
    state.screenChanged("schermata")
    state.offer(tutorial("estraneo"), "un'altra")
    assertEquals(0, state.pendingCount)
  }

  @Test
  fun `cambiando schermata la coda si svuota`() {
    state.screenChanged("schermata")
    ready("a")
    assertEquals(1, state.pendingCount)
    state.screenChanged("radar")
    assertEquals(0, state.pendingCount)
    assertNull(state.presenting)
  }

  @Test
  fun `un caricamento in corso ferma tutto finche' non finisce`() {
    state.screenChanged("schermata")
    ready("a")
    state.loading(active = true)
    now = 2_000
    state.evaluate(modalPresenting = false)
    assertNull(state.presenting)
    state.loading(active = false)
    state.evaluate(modalPresenting = false)
    assertEquals("a", state.presenting?.id)
  }

  @Test
  fun `il link che li spegne tutti arriva all'app`() {
    var optOut = false
    state.onDismissed = { _, requested -> optOut = requested }
    state.screenChanged("schermata")
    ready("a")
    now = 2_000
    state.evaluate(modalPresenting = false)
    state.dismiss(optOut = true)
    assertEquals(true, optOut)
    assertNull(state.presenting)
  }

  @Test
  fun `un suggerimento ritirato non torna`() {
    state.screenChanged("schermata")
    ready("a")
    state.withdraw("a")
    now = 2_000
    state.evaluate(modalPresenting = false)
    assertNull(state.presenting)
  }
}
