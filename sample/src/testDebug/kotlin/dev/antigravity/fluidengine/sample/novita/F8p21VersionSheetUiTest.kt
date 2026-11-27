package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.revisions.FluidVersion
import dev.antigravity.fluidengine.revisions.FluidVersionChoice
import dev.antigravity.fluidengine.revisions.FluidVersionChoiceSheet
import dev.antigravity.fluidengine.revisions.FluidVersionChoiceTags
import dev.antigravity.fluidengine.revisions.FluidVersionDecision
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.21 · Il foglio della scelta tra versioni col dito vero (eventi del sistema con l'orologio che avanza, `MaterialFinger`)
 * e col vetro vero (verdetti degli shader accettati): due versioni, la scelta di una, di tutte e due, una revisione nuova a
 * foglio aperto, TalkBack, «Riduci movimento», e niente che si muove a riposo. Formato telefono (400 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p21VersionSheetUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private val decisions = ArrayList<FluidVersionDecision>()
  private var dismissed = 0
  private var choice by mutableStateOf<FluidVersionChoice?>(null)

  // Mezzogiorno di oggi: «Oggi, …» anche se la prova gira dopo mezzanotte.
  private val now = java.time.ZonedDateTime.now(ZoneId.of("Europe/Rome")).withHour(12).withMinute(30).toInstant().toEpochMilli()
  private val mine = FluidVersion("rev-a", "Questo telefono", now - 60_000, "Ritrovo alle 7:45 in stazione.\nPranzo al sacco.", current = true)
  private val theirs = FluidVersion("rev-b", "Tablet di Alessio", now - 120_000, "Ritrovo alle 8:30 al parcheggio.\nPranzo al sacco.")
  private val newer = FluidVersion("rev-c", "Computer", now - 5_000, "Ritrovo alle 8 in piazza San Marco.\nPranzo al sacco.", current = true)

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun show(reduced: Boolean = false) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
          LocalFluidGlassModalHostState provides host,
        ) {
          Box(Modifier.fillMaxSize()) {
            FluidVersionChoiceSheet(
              choice = choice,
              onDecision = { decisions += it },
              onDismissRequest = {
                dismissed++
                choice = null
              },
              zone = ZoneId.of("Europe/Rome"),
              locale = Locale.ITALY,
            )
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(300)
    finger = MaterialFinger(view)
    choice = FluidVersionChoice("Gita a Firenze", listOf(mine, theirs), key = "gita/testo")
    advance(900)
  }

  private fun node(tag: String): SemanticsNode = compose.onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode()

  private fun bounds(tag: String): Rect = node(tag).boundsInRoot

  private fun exists(tag: String): Boolean =
    compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

  private fun tap(tag: String) {
    val at = bounds(tag).center
    finger.down(at)
    advance(48)
    finger.up(at)
    advance(400)
  }

  private fun text(tag: String): String =
    node(tag).config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()

  private fun writesDuring(ms: Long): Int {
    var writes = 0
    val seen = LinkedHashSet<String>()
    val observer = Snapshot.registerGlobalWriteObserver {
      writes++
      if (seen.size < 12) seen += it.toString().take(140)
    }
    advance(ms)
    observer.dispose()
    if (writes > 0) println("F8p21: $writes scritture, per esempio $seen")
    return writes
  }

  /** Il testo unito di una card, compresi gli strati di un cambio in corso. */
  private fun cardText(id: String): String = text(FluidVersionChoiceTags.card(id))

  @Test fun `col dito si sceglie una versione e la scelta porta gli id delle revisioni`() {
    show()
    assertTrue(exists(FluidVersionChoiceTags.Pane))
    assertTrue("Scegli una versione" in text(FluidVersionChoiceTags.Keep))
    // Niente scelta, niente decisione: il tasto e' spento.
    tap(FluidVersionChoiceTags.Keep)
    assertTrue(decisions.isEmpty())
    tap(FluidVersionChoiceTags.card("rev-b"))
    assertTrue("Tieni questa versione" in text(FluidVersionChoiceTags.Keep))
    tap(FluidVersionChoiceTags.Keep)
    assertEquals(listOf(FluidVersionDecision(listOf("rev-b"), listOf("rev-a", "rev-b"))), decisions)
    assertEquals("il foglio non si chiude da solo: lo chiude l'app", 0, dismissed)
  }

  @Test fun `tutte e due - il tasto lo dice e la scelta le tiene tutte`() {
    show()
    tap(FluidVersionChoiceTags.card("rev-a"))
    tap(FluidVersionChoiceTags.card("rev-b"))
    assertTrue("Tieni tutte e due" in text(FluidVersionChoiceTags.Keep))
    tap(FluidVersionChoiceTags.Keep)
    assertTrue(decisions.single().keepsAll)
    // «Decidi dopo» chiude senza scegliere.
    tap(FluidVersionChoiceTags.Later)
    assertEquals(1, dismissed)
    advance(900)
    assertFalse(exists(FluidVersionChoiceTags.Pane))
  }

  @Test fun `una revisione nuova a foglio aperto azzera la scelta, lo dice e la card entra sfocata`() {
    show()
    tap(FluidVersionChoiceTags.card("rev-b"))
    assertFalse(exists(FluidVersionChoiceTags.Updated))
    choice = FluidVersionChoice("Gita a Firenze", listOf(newer, theirs), key = "gita/testo")
    advance(48)
    // A meta' del cambio la card del primo posto ha i due strati, chi esce e chi entra (sfocati): lo muove l'app.
    val during = cardText("rev-c")
    println("F8p21: card durante il cambio: " + during.replace('\n', ' '))
    assertTrue(during, "Computer" in during && "Questo telefono" in during)
    // Ogni strato porta il suo testo: chi esce resta «alle 7:45», chi entra e' «in piazza San Marco».
    assertTrue(during, "7:45" in during && "San Marco" in during)
    advance(900)
    assertFalse("finito il cambio resta solo la nuova", "Questo telefono" in cardText("rev-c"))
    assertTrue(exists(FluidVersionChoiceTags.Updated))
    val live = node(FluidVersionChoiceTags.Updated).config.getOrNull(SemanticsProperties.LiveRegion)
    assertEquals("TalkBack annuncia la versione nuova", androidx.compose.ui.semantics.LiveRegionMode.Polite, live)
    assertTrue("la scelta si e' azzerata", "Scegli una versione" in text(FluidVersionChoiceTags.Keep))
    tap(FluidVersionChoiceTags.card("rev-c"))
    tap(FluidVersionChoiceTags.Keep)
    assertEquals(FluidVersionDecision(listOf("rev-c"), listOf("rev-c", "rev-b")), decisions.single())
  }

  @Test fun `TalkBack - ogni versione e' una casella con lo stato, il dispositivo, l'ora e i passaggi`() {
    show()
    val card = node(FluidVersionChoiceTags.card("rev-b"))
    assertEquals(androidx.compose.ui.semantics.Role.Checkbox, card.config.getOrNull(SemanticsProperties.Role))
    assertEquals(ToggleableState.Off, card.config.getOrNull(SemanticsProperties.ToggleableState))
    val spoken = text(FluidVersionChoiceTags.card("rev-b"))
    assertTrue(spoken, "Tablet di Alessio" in spoken)
    assertTrue(spoken, "Oggi" in spoken)
    assertTrue(spoken, "8:30" in spoken)
    assertTrue("l'attuale e' segnata", "Attuale" in text(FluidVersionChoiceTags.card("rev-a")))
    compose.onNodeWithTag(FluidVersionChoiceTags.card("rev-b")).performSemanticsAction(SemanticsActions.OnClick)
    advance(300)
    assertEquals(ToggleableState.On, node(FluidVersionChoiceTags.card("rev-b")).config.getOrNull(SemanticsProperties.ToggleableState))
    assertEquals("Scelta", node(FluidVersionChoiceTags.card("rev-b")).config.getOrNull(SemanticsProperties.StateDescription))
    // I bersagli: almeno 44 dp (88 px a xhdpi).
    assertTrue(bounds(FluidVersionChoiceTags.card("rev-b")).height >= 88f)
    assertTrue(bounds(FluidVersionChoiceTags.Keep).height >= 88f)
  }

  @Test fun `a riposo niente si muove`() {
    show()
    assertEquals("il foglio aperto e fermo non scrive niente", 0, writesDuring(1000))
    tap(FluidVersionChoiceTags.card("rev-a"))
    // La spunta e la card che cede sotto il dito finiscono le loro molle, poi niente.
    advance(1_500)
    assertEquals("dopo la spunta, fermo", 0, writesDuring(1000))
  }

  @Test fun `con Riduci movimento la versione nuova arriva di colpo, senza strati sfocati`() {
    show(reduced = true)
    tap(FluidVersionChoiceTags.card("rev-a"))
    choice = FluidVersionChoice("Gita a Firenze", listOf(newer, theirs), key = "gita/testo")
    advance(48)
    val during = cardText("rev-c")
    assertTrue(during, "Computer" in during)
    assertFalse("nessuno strato in uscita: " + during, "Questo telefono" in during)
    assertTrue(exists(FluidVersionChoiceTags.Updated))
    advance(1_500)
    assertEquals("poi fermo", 0, writesDuring(1000))
    assertNull(decisions.firstOrNull())
  }

  /**
   * R2, il minore di F8.21: due tocchi rapidi su «Tieni…» mentre l'app salva (il foglio lo chiude lei, dopo). Una scelta
   * sola; se il salvataggio non va e l'app lascia il foglio aperto, toccare una card riaccende il tasto.
   */
  @Test fun `il doppio tocco su Tieni decide una volta sola`() {
    show()
    tap(FluidVersionChoiceTags.card("rev-a"))
    tap(FluidVersionChoiceTags.card("rev-b"))
    val at = bounds(FluidVersionChoiceTags.Keep).center
    repeat(2) {
      finger.down(at)
      advance(32)
      finger.up(at)
      advance(160)
    }
    advance(400)
    assertEquals(1, decisions.size)
    assertTrue(decisions.single().keepsAll)
    tap(FluidVersionChoiceTags.card("rev-a"))
    tap(FluidVersionChoiceTags.Keep)
    assertEquals(2, decisions.size)
    assertEquals(listOf("rev-b"), decisions.last().keep)
  }
}
