package dev.antigravity.fluidengine.sample.novita

import android.content.ClipboardManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import dev.antigravity.fluidengine.health.day
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.health.FluidHealthMetric
import dev.antigravity.fluidengine.health.FluidHealthTags
import dev.antigravity.fluidengine.map.FluidMapPlaceTags
import dev.antigravity.fluidengine.map.FluidMapTags
import dev.antigravity.fluidengine.map.FluidSketchMapEngine
import dev.antigravity.fluidengine.map.LocalFluidMapEngine
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.26 · Le scene della galleria col dito vero, sul disegno della mappa (MapLibre non si disegna in Robolectric) e coi
 * dati di salute finti: il foglio sopra la mappa che non si chiude e torna giu', la scheda del luogo con «Copia
 * coordinate», la posizione simulata, la cartolina che apre la mappa, la salute che si aggiorna, le fonti unite e i
 * permessi rifiutati due volte.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class F8p26PlacesHealthUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun open(scene: String) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(EngineSettings()) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host, LocalFluidMapEngine provides FluidSketchMapEngine()) {
          Box(Modifier.fillMaxSize()) {
            NewElementsPageAt(onBack = {}, initial = scene)
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    settle(1_500)
  }

  private fun settle(ms: Long) {
    var t = 0L
    while (t < ms) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      t += 16
    }
    compose.waitForIdle()
  }

  /**
   * Fa girare i fotogrammi finche' [condition] e' vera, al piu' [timeoutMs] di tempo **vero**: le scene della salute fanno i
   * conti in sottofondo (Dispatchers.Default), e col PC carico il risultato arriva dopo un'attesa a tempo finto fissa.
   */
  private fun settleUntil(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      settle(48)
      if (condition()) return true
      Thread.sleep(20)
    }
    return condition()
  }

  private fun stepCards(): List<String> =
    compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, FluidHealthTags.card(FluidHealthMetric.Steps)))
      .fetchSemanticsNodes().map { it.config[SemanticsProperties.ContentDescription].first() }

  private fun top(text: String): Float = compose.onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot.top

  private fun description(tag: String): String =
    compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()

  @Test
  fun `il foglio della mappa sale a meta', e tirato oltre il fondo torna al suo posto invece di chiudersi`() {
    open("mappa")
    val low = top("Luoghi")
    compose.onAllNodesWithText("Luoghi").onFirst().performTouchInput { swipeUp(startY = centerY, endY = centerY - 900f, durationMillis = 300) }
    settle(900)
    val mid = top("Luoghi")
    assertTrue("a meta' sale: $low -> $mid", mid < low - 300f)
    compose.onAllNodesWithText("Luoghi").onFirst().performTouchInput { swipeDown(startY = centerY, endY = centerY + 2_000f, durationMillis = 250) }
    settle(1_200)
    val back = top("Luoghi")
    assertEquals("torna al detent piu' basso", low, back, 6f)
  }

  @Test
  fun `una riga del foglio sceglie il luogo, e la scheda copia le coordinate e lo dice`() {
    open("mappa")
    compose.onNodeWithText("Duomo").performClick()
    settle(1_200)
    compose.onNodeWithTag(FluidMapPlaceTags.Card).assertIsDisplayed()
    compose.onNodeWithText("Indicazioni").assertIsDisplayed()
    compose.onNodeWithTag(FluidMapPlaceTags.Copy).performClick()
    settle(100)
    val clip = compose.activity.getSystemService(ClipboardManager::class.java).primaryClip
    val copied = clip?.getItemAt(0)?.text?.toString().orEmpty()
    assertTrue(copied, Regex("""43\.77\d{3}, 11\.25\d{3}""").matches(copied))
    compose.onNodeWithText("Coordinate copiate").assertIsDisplayed()
    settle(2_000)
    assertEquals(0, compose.onAllNodesWithText("Coordinate copiate").fetchSemanticsNodes().size)
  }

  @Test
  fun `un segnaposto per luogo, ognuno col suo nome e le sue foto`() {
    // F8.26c: la galleria metteva un segnaposto per foto, tutti col nome del luogo, e il grappolo da 2 erano due «Duomo».
    val markers = F8p26Places.Markers
    assertEquals(markers.size, markers.map { it.title }.distinct().size)
    assertEquals(12, markers.size)
    assertEquals(46, F8p26Places.PhotoCount)
    open("mappa")
    compose.onNodeWithText("46 foto in 12 luoghi · Firenze").assertIsDisplayed()
  }

  @Test
  fun `il tasto della posizione mette la posizione simulata, mai quella vera`() {
    open("mappa")
    assertEquals(0, compose.onAllNodesWithTag(FluidMapTags.UserDot).fetchSemanticsNodes().size)
    compose.onNodeWithTag(FluidMapTags.Location).performClick()
    settle(200)
    compose.onNodeWithTag(FluidMapTags.UserDot).assertExists()
    compose.onNodeWithTag(FluidMapTags.Location).performClick()
    settle(900)
    assertEquals("Posizione, la mappa ti segue", description(FluidMapTags.Location))
  }

  @Test
  fun `la cartolina della nota apre la mappa sul luogo`() {
    open("cartolina")
    val postcards = compose.onAllNodesWithTag(FluidMapTags.Postcard).fetchSemanticsNodes()
    assertEquals(2, postcards.size)
    assertEquals("Mappa di Piazza della Signoria", postcards[0].config[SemanticsProperties.ContentDescription].first())
    compose.onAllNodesWithTag(FluidMapTags.Postcard).onFirst().performClick()
    settle(1_500)
    compose.onNodeWithTag(FluidMapPlaceTags.Card).assertIsDisplayed()
    compose.onAllNodesWithText("Piazza della Signoria").onFirst().assertIsDisplayed()
  }

  @Test
  fun `il giorno in salute si aggiorna quando arriva un dato e ogni card dice la fonte`() {
    open("salute")
    settleUntil { stepCards().firstOrNull()?.contains("Da ") == true }
    val before = description(FluidHealthTags.card(FluidHealthMetric.Steps))
    // Telefono e orologio contano le stesse ore: vince l'orologio, e la fonte detta e' quella che ha contato. Il nome e'
    // quello dell'app e l'apparecchio si dice una volta (F8.26c: prima «Da Orologio · orologio»).
    assertTrue(before, before.contains("Da Ritmo · orologio"))
    assertFalse(before, before.contains("orologio · orologio", ignoreCase = true))
    compose.onNodeWithTag(F8p26Tags.Arrive).performClick()
    settleUntil { description(FluidHealthTags.card(FluidHealthMetric.Steps)) != before }
    val after = description(FluidHealthTags.card(FluidHealthMetric.Steps))
    assertTrue("$before -> $after", before != after)
    val sleep = description(FluidHealthTags.card(FluidHealthMetric.Sleep))
    assertTrue(sleep, sleep.contains("Da Ritmo e Pampa Sounds"))
    assertTrue(compose.onNodeWithTag(FluidHealthTags.Rings).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first().startsWith("Passi, "))
  }

  @Test
  fun `buchi e doppioni dicono il totale del provider, il buco come fatto e il giorno vuoto senza zero`() {
    open("salute-fonti")
    settleUntil { stepCards().isNotEmpty() }
    // La lista compone solo cio' che si vede: si scorre e si raccolgono le card dei passi, nell'ordine.
    val texts = LinkedHashSet<String>()
    repeat(4) {
      texts.addAll(stepCards())
      compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.4f, durationMillis = 400) }
      settle(900)
    }
    val list = texts.toList()
    assertEquals(list.toString(), 4, list.size)
    // Il totale del provider con l'orologio primo nell'ordine dell'utente: 3 400 + 1 100, non 7 600; col telefono primo 4 200.
    assertTrue(list[0], list[0].startsWith("Passi, 4.500 passi"))
    assertTrue(list[1], list[1].startsWith("Passi, 4.200 passi"))
    assertTrue(list[2], list[2].contains("Niente dalle 14:15"))
    assertTrue(list[3], list[3].startsWith("Passi, Nessun dato"))
  }

  @Test
  fun `il permesso rifiutato due volte porta a Health Connect, quello consentito non chiede niente`() {
    open("salute-permessi")
    repeat(2) {
      compose.onNodeWithTag(FluidHealthTags.permission(FluidHealthMetric.HeartRate)).performClick()
      settle(700)
      compose.onNodeWithText("Non consentire").performClick()
      settle(700)
    }
    compose.onNodeWithText("Consenti in Health Connect").assertIsDisplayed()
    compose.onNodeWithTag(FluidHealthTags.permission(FluidHealthMetric.Weight)).performClick()
    settle(700)
    compose.onNodeWithText("Consenti").performClick()
    settle(700)
    assertTrue(compose.onAllNodesWithText("Consentito").fetchSemanticsNodes().size >= 2)
  }

  @Test
  fun `il conto della scena della salute cresce col dato nuovo, senza interfaccia`() = kotlinx.coroutines.runBlocking {
    val zone = java.time.ZoneId.systemDefault()
    val now = java.time.LocalDate.now(zone).atTime(18, 30).atZone(zone).toInstant()
    val today = java.time.LocalDate.ofInstant(now, zone)
    val g = dev.antigravity.fluidengine.health.FluidMemoryHealthGateway(sourcePriority = F8p26Health.SettingsOrder).apply {
      grantAll(); put(*F8p26Health.day(now, zone).toTypedArray())
    }
    val before = g.day(FluidHealthMetric.Steps, today, zone, now)
    val end = now.plus(java.time.Duration.ofMinutes(30))
    g.put(dev.antigravity.fluidengine.health.FluidHealthInterval("nuovo-p1", FluidHealthMetric.Steps, end.minus(java.time.Duration.ofMinutes(30)), end, 640.0, F8p26Health.Watch))
    val after = g.day(FluidHealthMetric.Steps, today, zone, end)
    assertEquals(
      ((before as dev.antigravity.fluidengine.health.FluidHealthReading.Value).value + 640.0),
      (after as dev.antigravity.fluidengine.health.FluidHealthReading.Value).value, 1e-6,
    )
  }
}
