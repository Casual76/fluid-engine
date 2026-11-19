package dev.antigravity.fluidengine.sample.measure

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.Gallery
import dev.antigravity.fluidengine.sample.GalleryEntry
import dev.antigravity.fluidengine.sample.GalleryNav
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.RouteChrome
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog
import java.time.Duration

/**
 * Ogni scenario fisso (F7.1) si avvia, finisce e dà i suoi segnali, nella galleria vera e lanciato come lo lancia lo
 * script sul tablet: la pagina «Misure» aperta sopra Chrome, gli extra `measure.*` nell'intento dell'attività, le righe
 * `SCENARIO` in logcat. Un ciclo, attese corte: la prova guarda la sequenza, non i tempi (sul computer non valgono).
 *
 * Il flag `debuggable` si accende a mano come in `GalleryExtrasTest`: senza, la pagina ignora gli extra (è la regola
 * della galleria pubblicata).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class MeasureScenarioUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var flagsWere = 0
  private var diagnosticsWere = false

  @Before fun start() {
    val info = compose.activity.applicationInfo
    flagsWere = info.flags
    info.flags = info.flags or ApplicationInfo.FLAG_DEBUGGABLE
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    ShadowLog.reset()
  }

  @After fun stop() {
    compose.activity.applicationInfo.flags = flagsWere
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun lines(): List<String> =
    ShadowLog.getLogsForTag(MeasureLogTag).map { it.msg }.filter { it.startsWith("SCENARIO ") }

  private fun shown(text: String): Boolean =
    compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun described(text: String): Boolean =
    compose.onAllNodesWithContentDescription(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  /** Il bordo alto del primo nodo con [text], in pixel della radice (null se non c'è). */
  private fun top(text: String): Float? =
    compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top

  /**
   * Lancia [scenario] con un ciclo e attese corte, fa correre l'orologio fino a `CHIUSO` e controlla la sequenza. A ogni
   * `FINE` chiama il controllo del tratto in [effects], se c'è: lo scenario deve aver fatto davvero quello che dice
   * (un menu aperto che poi non c'è più, una lista che ha cambiato riga), non solo aver scritto le sue righe.
   */
  private fun runs(scenario: MeasureScenario, effects: Map<String, () -> Unit> = emptyMap()) {
    val extras = mapOf(
      "gallery.page" to "measure",
      "measure.scenario" to scenario.key,
      "measure.cycles" to "1",
      "measure.warmup" to "0.4",
      "measure.lead" to "0.3",
    )
    compose.activity.intent = Intent(compose.activity.intent).apply { extras.forEach { (k, v) -> putExtra(k, v) } }
    val config = requireNotNull(ScenarioConfig.parse { extras[it] })
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Gallery(
          settings = EngineSettings(),
          onSettingsChange = {},
          nav = remember { GalleryNav(listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(GalleryPage.Measure))) },
        )
      }
    }
    val limit = config.warmupMillis + config.durationMillis() + 8_000L
    var elapsed = 0L
    var ends = 0
    val checked = ArrayList<String>()
    fun verify(key: String) {
      val check = effects[key] ?: return
      try {
        check()
      } catch (error: AssertionError) {
        throw AssertionError("${scenario.key}/$key: ${error.message}\n${lines().joinToString("\n")}", error)
      }
      checked += key
    }
    while (elapsed < limit && lines().none { it.startsWith("SCENARIO CHIUSO") || it.startsWith("SCENARIO ERRORE") }) {
      // L'orologio della composizione e quello del filo principale al passo (come in AssistantPageTest): il dito finto
      // prende i tempi degli eventi da SystemClock, e con quello fermo il secondo gesto avrebbe i tempi del primo.
      compose.mainClock.advanceTimeBy(StepMillis)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StepMillis))
      elapsed += StepMillis
      // `pronto`: la scena composta e ferma, prima del primo viaggio (per prendere le posizioni di partenza).
      if (Ready !in checked && lines().any { it.startsWith("SCENARIO PRONTO") }) {
        if (Ready in effects) verify(Ready) else checked += Ready
      }
      val finished = lines().filter { it.startsWith("SCENARIO FINE") }
      while (ends < finished.size) {
        Regex("""fase=(\S+)""").find(finished[ends])?.groupValues?.get(1)?.let(::verify)
        ends++
      }
    }
    if (Ready !in effects) checked.remove(Ready)
    assertEquals("${scenario.key}: controlli d'effetto", effects.keys.toSet(), checked.toSet())
    val seen = lines()
    seen.firstOrNull { it.startsWith("SCENARIO ERRORE") }?.let { fail("${scenario.key}: $it\n${seen.joinToString("\n")}") }
    assertTrue("${scenario.key}: nessun CHIUSO in ${limit} ms\n${seen.joinToString("\n")}", seen.any { it.startsWith("SCENARIO CHIUSO") })

    // La sequenza: PRONTO, poi per ogni tratto INIZIO, la SONDA se c'è, FINE; poi CHIUSO. Tutte con l'id dello scenario.
    val expected = ArrayList<String>()
    expected += "PRONTO"
    config.timeline().forEach { step ->
      expected += "INIZIO ${step.cycleLabel} ${step.phase.name}"
      if (scenario.probes && step.phase.probeMillis != null) expected += "SONDA ${step.cycleLabel} ${step.phase.name}"
      expected += "FINE ${step.cycleLabel} ${step.phase.name}"
    }
    expected += "CHIUSO"
    val actual = seen.map { line ->
      assertTrue(line, line.contains(" id=${scenario.key} "))
      val kind = line.removePrefix("SCENARIO ").substringBefore(' ')
      val cycle = Regex("""ciclo=(\S+)""").find(line)?.groupValues?.get(1)
      val phase = Regex("""fase=(\S+)""").find(line)?.groupValues?.get(1)
      listOfNotNull(kind, cycle, phase).joinToString(" ")
    }
    assertEquals(scenario.key, expected, actual)
  }

  /**
   * Il dito ha trascinato la lista: la prima riga è salita (o è uscita) e al ritorno è scesa di nuovo. Le soglie sono
   * larghe di proposito: il lancio dopo il rilascio dipende dalla velocità degli ultimi eventi, e qui conta che la
   * lista si muova col dito in tutte e due le direzioni, non di quanto.
   */
  private fun scrolled(first: String): Map<String, () -> Unit> {
    var start = 0f
    var lifted: Float? = null
    val shift = 120f  // pixel: il trascinamento ne fa 0,38 × 900 dp × 2 ≈ 680, meno la soglia del tocco
    return mapOf(
      Ready to { start = requireNotNull(top(first)) { "$first non c'è prima di cominciare" } },
      "su" to {
        lifted = top(first)
        assertTrue("$first è salita da $start a $lifted", lifted == null || lifted!! < start - shift)
      },
      "giu" to {
        val back = top(first)
        assertTrue("$first non è tornata giù (da $lifted a $back)", back != null && (lifted == null || back > lifted!! + shift))
      },
    )
  }

  /** Il menu si è aperto (c'è una sua voce) e poi chiuso. */
  private val menuOpensAndCloses = mapOf(
    "apre" to { assertTrue("il menu non si è aperto", shown("Aggiungi ai preferiti")) },
    "chiude" to { assertTrue("il menu non si è chiuso", !shown("Aggiungi ai preferiti")) },
  )

  @Test fun scorrimentoFoto() = runs(MeasureScenario.ScrollPhotos, scrolled("Foto 1"))
  @Test fun scorrimentoTesto() = runs(MeasureScenario.ScrollText, scrolled("Paragrafo 1"))
  @Test fun menuTasto() = runs(MeasureScenario.MenuButton, menuOpensAndCloses)
  @Test fun menuGruppo() = runs(MeasureScenario.MenuGroup, menuOpensAndCloses)
  @Test fun contestuale() = runs(MeasureScenario.ContextMenu, menuOpensAndCloses)

  @Test fun barraContesto() = runs(
    MeasureScenario.BarContext,
    mapOf(
      "cambia" to { assertTrue("la barra non è passata a seleziona", described("Elimina") && !shown("Seleziona")) },
      "torna" to { assertTrue("la barra non è tornata a sfoglia", shown("Seleziona")) },
    ),
  )

  @Test fun capsulaPremuta() = runs(MeasureScenario.CapsulePress)

  @Test fun foglioSale() = runs(
    MeasureScenario.SheetRise,
    mapOf(
      "sale" to { assertTrue("il foglio non è salito", shown("Luoghi vicini")) },
      "scende" to { assertTrue("il foglio non è sceso", !shown("Luoghi vicini")) },
    ),
  )

  @Test fun foglioTrascinato() {
    var atRest: Float? = null
    var lifted: Float? = null
    runs(
      MeasureScenario.SheetDrag,
      mapOf(
        "su" to {
          lifted = top("Luoghi vicini")
          assertTrue("il foglio non c'è dopo il trascinamento in su", lifted != null)
        },
        "giu" to {
          atRest = top("Luoghi vicini")
          assertTrue("il foglio si è chiuso trascinandolo in giù", atRest != null)
          assertTrue("il foglio non è salito ($lifted) sopra la media ($atRest)", lifted!! < atRest!! - 100f)
        },
      ),
    )
    assertTrue("il foglio è stato riaperto", lines().none { it.contains("nota=riaperto") })
  }

  @Test fun ottoLastre() = runs(MeasureScenario.EightSlabs, scrolled("Foto 1"))

  @Test fun pushPop() = runs(
    MeasureScenario.PushPop,
    mapOf(
      "push" to { assertTrue("la pagina non è entrata", shown("Note")) },
      "pop" to { assertTrue("la pagina non è uscita", !shown("Note")) },
    ),
  )

  @Test fun zoomCard() = runs(
    MeasureScenario.ZoomCard,
    mapOf(
      "apre" to { assertTrue("la card non si è aperta", shown("Raccolta 3")) },
      "chiude" to { assertTrue("la card non si è chiusa", !shown("Raccolta 3")) },
    ),
  )

  @Test fun f1bC() = runs(MeasureScenario.F1bGroupMenu)
  @Test fun f1bF() = runs(MeasureScenario.F1bContext, menuOpensAndCloses)

  @Test fun f1bH() = runs(
    MeasureScenario.F1bCard,
    mapOf(
      "apre" to { assertTrue("il pop-up non si è aperto", shown("Un pop-up in vetro")) },
      "chiude" to { assertTrue("il pop-up non si è chiuso", !shown("Un pop-up in vetro")) },
    ),
  )

  @Test
  fun `ogni scenario ha la sua prova qui`() {
    val tested = MeasureScenarioUiTest::class.java.declaredMethods
      .filter { method -> method.getAnnotation(Test::class.java) != null }
      .size - 1
    assertEquals(MeasureScenario.entries.size, tested)
  }

  private companion object {
    const val StepMillis = 16L

    /** La chiave del controllo fatto al segnale `PRONTO`. */
    const val Ready = "pronto"
  }
}
