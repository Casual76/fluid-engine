package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHapticTrace
import dev.antigravity.fluidengine.ui.haptics.FluidHapticTraceOutcome
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.haptics.ProvideFluidHapticTrace
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Il motore sotto il registro: ricorda cosa gli e' arrivato, come farebbe il vibratore. */
private class BetaHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  var grains = 0
    private set

  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) {
    grains++
  }
}

/**
 * La beta della 3.0 nella galleria vera: la pagina "Da provare" elenca le voci e ogni collegamento
 * apre la sua demo, le demo si raggiungono con due tocchi dal lancio, e il registro aptico della
 * radice sente un tocco su un tasto e lo mostra nella scheda Controlli.
 *
 * Lo schermo e' piu' alto del solito (800 dp): la galleria ha la barra flottante in basso, e a 640 dp
 * le liste avrebbero poco da mostrare sopra di lei.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class FluidBetaGalleryTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val haptics = BetaHaptics()
  private lateinit var nav: GalleryNav
  private lateinit var tools: GalleryTools

  private fun show(initial: List<GalleryEntry> = GalleryNav.Launch) {
    nav = GalleryNav(initial)
    tools = GalleryTools()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        // Il motore sotto e' un finto: il registro che la galleria installa ci si mette sopra.
        CompositionLocalProvider(LocalFluidHaptics provides haptics) {
          Gallery(settings = EngineSettings(), onSettingsChange = {}, nav = nav, tools = tools)
        }
      }
    }
    compose.waitForIdle()
  }

  /** Scorre la lista della pagina finche' il nodo con questa etichetta non e' composto. */
  private fun scrollTo(tag: String): SemanticsNodeInteraction {
    compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasTestTag(tag))
    compose.waitForIdle()
    return compose.onNodeWithTag(tag)
  }

  private fun shown(text: String, substring: Boolean = false): Boolean =
    compose.onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()

  /** Il giro piu' nuovo, quello che la pagina mette in cima e che il sottotitolo nomina. */
  private val round get() = TryRounds.last()

  /** Le voci di tutti i giri: una beta nuova non toglie le prove di quelle di prima. */
  private val allItems get() = TryRounds.flatMap { it.items }

  // --- La pagina Da provare -------------------------------------------------------------------------

  @Test
  fun `all'avvio c'e' Da provare, con la versione della beta e la prima voce`() {
    show()

    assertEquals(GalleryPage.ToTry, nav.page)
    compose.onNodeWithTag("to-try-page").assertExists()
    assertTrue(shown("Da provare"))
    // Il sottotitolo dice quale beta e quale versione sta provando.
    assertTrue(shown("${round.title} · versione ${BuildConfig.VERSION_NAME}"))
    // La prima voce della prima area si vede senza scorrere.
    compose.onNodeWithTag("to-try-${round.items.first().id}").assertExists()
  }

  @Test
  fun `la pagina elenca ogni voce di ogni giro`() {
    show()

    // La lista e' pigra: ogni voce si compone solo se la si scorre. Raggiungerle tutte per tag e'
    // anche la prova che sono tutte li', con il loro titolo e la loro frase.
    allItems.forEach { item ->
      scrollTo("to-try-${item.id}").assertExists()
      assertTrue("manca il titolo di ${item.id}", shown(item.title))
      assertTrue("manca il dove di ${item.id}", shown("Apri: ${item.target.where}"))
    }
  }

  @Test
  fun `ogni voce apre la sua demo, e Indietro torna alla lista`() {
    show()

    allItems.forEach { item ->
      compose.runOnIdle { nav.reset(GalleryNav.Launch) }
      compose.waitForIdle()
      scrollTo("to-try-${item.id}").performClick()
      compose.waitForIdle()

      when (val target = item.target) {
        is TryTarget.Tab -> {
          assertEquals("${item.id}: la scheda", target.route, nav.route)
          assertEquals("${item.id}: nessuna pagina sopra la scheda", null, nav.page)
          assertTrue("${item.id}: la scheda non si vede", tabMarkerShown(target.route))
        }
        is TryTarget.Page -> {
          assertEquals("${item.id}: la pagina", target.page, nav.page)
          assertTrue("${item.id}: la demo non si vede", pageMarkerShown(target.page))
        }
        is TryTarget.Screen -> {
          val started = shadowOf(compose.activity).nextStartedActivity
          assertEquals(
            "${item.id}: l'attivita' delle pagine",
            PagesDemoActivity::class.java.name,
            started?.component?.className,
          )
          // Resta dov'era: la schermata delle pagine e' un'altra attivita'.
          assertEquals(GalleryPage.ToTry, nav.page)
          return@forEach
        }
      }

      // L'"Indietro" della galleria torna alla lista, senza chiudere l'app.
      compose.runOnIdle { assertTrue(nav.back()) }
      compose.waitForIdle()
      assertEquals("${item.id}: dopo Indietro", GalleryPage.ToTry, nav.page)
      compose.onNodeWithTag("to-try-page").assertExists()
    }
  }

  @Test
  fun `le voci coprono ogni demo della 3_0, e ogni demo ha almeno una voce`() {
    val targets = allItems.map { it.target }
    // «Misure», «Materiale» e «Icone» sono strumenti per le sessioni sul dispositivo: non hanno una voce in «Da provare».
    val tools = setOf(GalleryPage.ToTry, GalleryPage.Measure, GalleryPage.Material, GalleryPage.Icons)
    GalleryPage.entries.filter { it !in tools }.forEach { page ->
      assertTrue("nessuna voce porta a $page", targets.any { it is TryTarget.Page && it.page == page })
    }
    assertTrue("nessuna voce porta alle pagine", targets.any { it is TryTarget.Screen })
  }

  // --- Due tocchi dal lancio ------------------------------------------------------------------------

  /**
   * Primo tocco: la scheda Chrome, dalla barra (il tocco va sull'etichetta, nell'albero non unito: la
   * scheda non espone il testo del figlio, ma il dito cade li'). Secondo: la riga del catalogo.
   *
   * Una demo per prova, ognuna da una galleria appena aperta: scorrere una lista chiude la barra
   * flottante in una pillola sola, e da chiusa non mostra le etichette delle altre schede.
   */
  private fun opensFromChrome(tag: String, page: GalleryPage) {
    show(listOf(GalleryEntry.Tab(RouteMaterial)))

    compose.onAllNodes(hasText("Chrome"), useUnmergedTree = true).onFirst().performClick()
    compose.waitForIdle()
    assertEquals(RouteChrome, nav.route)

    scrollTo(tag).performClick()
    compose.waitForIdle()
    assertEquals(page, nav.page)
    assertTrue("$page non si vede", pageMarkerShown(page))
  }

  @Test
  fun `Righe e fogli si apre con due tocchi da Chrome`() = opensFromChrome("chrome-open-rows", GalleryPage.RowsAndSheets)

  @Test
  fun `Capsule e pezzi piccoli si apre con due tocchi da Chrome`() =
    opensFromChrome("chrome-open-capsules", GalleryPage.CapsulesAndParts)

  @Test
  fun `I buchi delle app si apre con due tocchi da Chrome`() = opensFromChrome("chrome-open-gaps", GalleryPage.Gaps)

  @Test
  fun `Menu liquidi si apre con due tocchi da Chrome`() = opensFromChrome("chrome-open-menus", GalleryPage.Menus)

  @Test
  fun `Tutto in vetro si apre con due tocchi da Chrome`() = opensFromChrome("chrome-open-all-glass", GalleryPage.AllGlass)

  @Test
  fun `Riordino, aggiornamento e visore si apre con due tocchi da Chrome`() =
    opensFromChrome("chrome-open-more", GalleryPage.MoreComponents)

  @Test
  fun `la riga in cima a Materiale apre Da provare con un tocco`() {
    show(listOf(GalleryEntry.Tab(RouteMaterial)))

    compose.onNodeWithTag("material-to-try").performClick()
    compose.waitForIdle()

    assertEquals(GalleryPage.ToTry, nav.page)
    compose.onNodeWithTag("to-try-page").assertExists()
  }

  @Test
  fun `l'Indietro di sistema chiude la pagina e poi lascia l'app al sistema`() {
    show()
    // Una voce che apre una scheda (la prima: le piu' nuove aprono pagine, e qui serve una scheda).
    val tabItem = allItems.first { it.target is TryTarget.Tab }
    scrollTo("to-try-${tabItem.id}")

    // Una scheda aperta da una voce, poi Indietro: torna alla lista.
    compose.onNodeWithTag("to-try-${tabItem.id}").performClick()
    compose.waitForIdle()
    assertEquals((tabItem.target as TryTarget.Tab).route, nav.route)
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertEquals(GalleryPage.ToTry, nav.page)

    // E ancora Indietro: la scheda in fondo, Materiale. Li' la galleria non ha piu' niente da chiudere.
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertEquals(null, nav.page)
    assertEquals(RouteMaterial, nav.route)
    assertFalse(nav.canGoBack)
  }

  // --- I menu liquidi nella galleria vera -----------------------------------------------------------

  /**
   * La demo dei menu ha bisogno dell'host dei modali della radice (`LocalFluidGlassModalHostState`):
   * le righe con il menu contestuale, le azioni della barra, la card e l'avviso vivono li'. Qui la
   * demo sta nella galleria vera, quindi e' la prova che il collegamento c'e'.
   */
  @Test
  fun `una riga della demo dei menu apre il suo menu nell'host della radice, e si sceglie con un tocco`() {
    show(listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(GalleryPage.Menus)))
    assertTrue("la demo dei menu non si vede", pageMarkerShown(GalleryPage.Menus))
    assertFalse("il menu e' aperto prima di aprirlo", shown("Condividi"))

    compose.onNodeWithTag("menus-row").performScrollTo()
    compose.waitForIdle()
    compose.onNodeWithTag("menus-row").performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    assertTrue("il menu della riga non si e' aperto nell'host della galleria", shown("Condividi"))

    // Un rilascio fermo lascia il menu aperto: si sceglie con un tocco, e la demo dice cosa e' stato scelto.
    compose.onNodeWithTag("menus-row").performTouchInput { up() }
    compose.waitForIdle()
    assertTrue("rilasciato fermo, il menu deve restare aperto", shown("Condividi"))
    compose.onNodeWithText("Condividi").performClick()
    compose.waitForIdle()
    assertTrue("la scelta non e' arrivata alla demo", shown("Scelta: Condividi"))
  }

  // --- Il registro aptico ---------------------------------------------------------------------------

  @Test
  fun `un tocco su un tasto della galleria finisce nel registro, e Controlli lo mostra`() {
    show(listOf(GalleryEntry.Tab(RouteControls)))

    val button = scrollTo("touch-primary")
    // Quello che e' suonato per arrivare fin qui non conta: si parte da un registro vuoto.
    compose.runOnIdle {
      tools.trace.clear()
      haptics.played.clear()
    }
    button.performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()

    // Il registro della radice ha sentito i tre colpi, in ordine; il motore sotto li ha ricevuti tali e
    // quali (il registro non cambia cosa suona).
    val expected = listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick)
    assertEquals(expected, tools.trace.entries.map { it.event })
    assertEquals(expected, haptics.played)

    // E la scheda li mostra: la riga piu' nuova in cima, tre voci.
    scrollTo("haptic-log-rows").assertExists()
    assertEquals(3, tools.trace.rows().size)
    assertTrue(shown("PressClick"))
    assertTrue(shown("PressDown"))
  }

  @Test
  fun `lo stile della grana e i cursori della taratura cambiano la grana della galleria`() {
    show(listOf(GalleryEntry.Tab(RouteControls)))
    assertEquals(FluidHapticGrainStyle.LibrePods, tools.grain.style)

    // Il controllo ha due segmenti: la meta' destra e' Fluid.
    scrollTo("grain-style").performTouchInput { click(Offset(width * 0.75f, height / 2f)) }
    compose.waitForIdle()
    assertEquals(FluidHapticGrainStyle.Fluid, tools.grain.style)

    // Con Fluid compaiono i cursori; la partenza trascinata e rilasciata cambia la taratura.
    val onsetBefore = tools.grain.onsetDpPerSec
    // La maniglia sta al 35% della pista ((520 - 150) / (1200 - 150)): ci si prende e si porta all'80%.
    scrollTo("grain-onset").performTouchInput {
      down(Offset(width * 0.352f, height / 2f))
      moveTo(Offset(width * 0.8f, height / 2f))
      up()
    }
    compose.waitForIdle()
    assertNotEquals(onsetBefore, tools.grain.onsetDpPerSec)
    assertTrue(
      "lo spegnimento non e' sotto la partenza",
      tools.grain.releaseDpPerSec <= tools.grain.onsetDpPerSec - GrainTuning.Hysteresis,
    )

    // Ripristina: i numeri di serie, e lo stile scelto resta.
    scrollTo("grain-reset").performClick()
    compose.waitForIdle()
    assertEquals(onsetBefore, tools.grain.onsetDpPerSec, 0f)
    assertEquals(FluidHapticGrainStyle.Fluid, tools.grain.style)
  }

  @Test
  fun `il registro in piccolo compare quando e' acceso e scrive le righe piu' nuove`() {
    show(listOf(GalleryEntry.Tab(RouteMaterial)))
    assertFalse(compose.onAllNodes(hasTestTag("haptic-overlay")).fetchSemanticsNodes().isNotEmpty())

    compose.runOnIdle {
      tools.overlayVisible = true
      tools.trace.recordEvent(FluidHapticEvent.Open)
    }
    compose.waitForIdle()

    compose.onNodeWithTag("haptic-overlay").assertExists()
    // La riga piccola e' "— Open": il trattino e' il ritardo della prima voce, che non ha un colpo prima.
    assertTrue(shown("Open", substring = true))
  }

  // --- Cosa e' visibile ----------------------------------------------------------------------------

  /** Un segno che la scheda di questa strada e' quella composta. */
  private fun tabMarkerShown(route: String): Boolean = when (route) {
    // Non il titolo: e' anche l'etichetta della barra, e ci sarebbe comunque. Una cosa che sta solo
    // in cima alla scheda.
    RouteControls -> shown("Tocchi registrati", substring = true)
    RouteChrome -> shown("Modale in-root")
    RouteSettings -> shown("Aspetto")
    RouteMaterial -> shown("Vetro su vetro")
    else -> true
  }

  /** Un segno che la pagina e' quella composta: qualcosa che sta solo nella sua demo. */
  private fun pageMarkerShown(page: GalleryPage): Boolean = when (page) {
    GalleryPage.ToTry -> compose.onAllNodes(hasTestTag("to-try-page")).fetchSemanticsNodes().isNotEmpty()
    GalleryPage.RowsAndSheets -> compose.onAllNodes(hasTestTag("demo-toggle-notifications")).fetchSemanticsNodes().isNotEmpty()
    GalleryPage.CapsulesAndParts -> compose.onAllNodes(hasTestTag("capsules-and-parts-demo")).fetchSemanticsNodes().isNotEmpty()
    GalleryPage.Gaps -> shown("Tasti di vetro: tre taglie")
    GalleryPage.Menus -> compose.onAllNodes(hasTestTag("menus-demo")).fetchSemanticsNodes().isNotEmpty()
    GalleryPage.AllGlass -> compose.onAllNodes(hasTestTag("all-glass-demo")).fetchSemanticsNodes().isNotEmpty()
    GalleryPage.MoreComponents -> compose.onAllNodes(hasTestTag("more-components-demo")).fetchSemanticsNodes().isNotEmpty()
    // I componenti nuovi della fase 1 (F1.6): una pagina per demo, tutte con la stessa radice. Un ramo solo, perche'
    // la prova di ognuna (che si apra, che regga la matrice) sta in FluidStateMatrixTest.
    GalleryPage.LongLists,
    GalleryPage.Identity,
    GalleryPage.DrawerAndTabs,
    GalleryPage.NotificationUndo,
    GalleryPage.TokenField,
    GalleryPage.DateTime,
    GalleryPage.RichText,
    GalleryPage.WebContent,
    GalleryPage.Charts,
    GalleryPage.FileTile,
    -> compose.onAllNodes(hasTestTag(NewComponentPageTag)).fetchSemanticsNodes().isNotEmpty()
    // «Misure» e' uno strumento per le sessioni sul dispositivo, non una demo di «Da provare»: nessuna prova di
    // questa classe la apre. Il ramo c'e' perche' il `when` resti esaustivo; il segno e' una frase che sta solo
    // nel suo sottotitolo.
    GalleryPage.Measure -> shown("adb logcat -s FluidMisure", substring = true)
    // «Materiale» (F1A.9), come «Misure»: uno strumento per le sessioni sul dispositivo. Senza `material.scene` mostra
    // il suo elenco, il cui sottotitolo dice il tag di logcat.
    GalleryPage.Material -> shown("adb logcat -s FluidMateriale", substring = true)
    // «Icone» (F1A.11), come «Materiale»: il segno e' la frase del sottotitolo che dice l'extra della sezione.
    GalleryPage.Icons -> shown("--es icons.section", substring = true)
  }
}

/**
 * Il registro aptico dentro un albero Compose vero: un tocco su un `FluidButton` suona tre colpi, e
 * il registro li scrive nell'ordine in cui il motore li riceve, senza cambiarli.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidHapticTraceComposeTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val real = BetaHaptics()

  @Composable
  private fun Themed(trace: FluidHapticTrace?, content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides real) {
        ProvideFluidHapticTrace(trace, content)
      }
    }
  }

  private fun tap() {
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()
  }

  @Test
  fun `un tap registra contatto, rilascio e click, in quest'ordine, e il motore li riceve uguali`() {
    val trace = FluidHapticTrace()
    compose.setContent {
      Themed(trace) {
        Box(Modifier.fillMaxSize()) { FluidButton(text = "Vai", onClick = {}, modifier = Modifier.testTag("button")) }
      }
    }
    compose.waitForIdle()

    tap()

    val expected = listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick)
    assertEquals(expected, trace.entries.map { it.event })
    assertEquals(expected, real.played)
    // Gli istanti non tornano mai indietro, e nessuna voce e' stata fusa: sono i tre colpi del tasto.
    assertEquals(trace.entries.map { it.atMillis }.sorted(), trace.entries.map { it.atMillis })
    assertTrue(trace.entries.all { it.outcome == FluidHapticTraceOutcome.Played })
  }

  @Test
  fun `un dito che corre scrive i granelli, con lo stile`() {
    val trace = FluidHapticTrace()
    compose.setContent {
      Themed(trace) {
        Box(Modifier.fillMaxSize()) {
          FluidButton(text = "Vai", onClick = {}, modifier = Modifier.testTag("button"), fillWidth = true)
        }
      }
    }
    compose.waitForIdle()

    // Venti px per evento: sopra i 18,7 della soglia di LibrePods, un granello a ogni passo.
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(150)
      repeat(10) { moveBy(Offset(20f, 0f), delayMillis = 8) }
      up()
    }
    compose.waitForIdle()

    val grains = trace.entries.filter { it.isGrain }
    assertEquals(10, grains.size)
    assertTrue(grains.all { it.grainStyle == FluidHapticGrainStyle.LibrePods })
    assertEquals(10, real.grains)
    // E la riga di registro e' una sola, con il conteggio.
    val row = trace.rows().first { it.label.startsWith("grana") }
    assertEquals(10, row.count)
    assertEquals("grana LibrePods ×10", row.text)
  }

  @Test
  fun `senza registro non cambia niente, il motore riceve gli stessi colpi`() {
    compose.setContent {
      Themed(null) {
        Box(Modifier.fillMaxSize()) { FluidButton(text = "Vai", onClick = {}, modifier = Modifier.testTag("button")) }
      }
    }
    compose.waitForIdle()

    tap()

    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick),
      real.played,
    )
  }

  @Test
  fun `un tema annidato toglie il registro, e ProvideFluidHapticTrace senza argomenti lo rimette`() {
    val trace = FluidHapticTrace()
    val nested = BetaHaptics()
    compose.setContent {
      Themed(trace) {
        // Un tema annidato (come quello della pagina Righe e fogli) installa la sua aptica.
        FluidTheme(settings = remember { EngineSettings() }, systemBars = false) {
          CompositionLocalProvider(LocalFluidHaptics provides nested) {
            Column(Modifier.fillMaxSize()) {
              FluidButton(text = "Senza", onClick = {}, modifier = Modifier.testTag("button"))
              ProvideFluidHapticTrace {
                FluidButton(text = "Con", onClick = {}, modifier = Modifier.testTag("traced"))
              }
            }
          }
        }
      }
    }
    compose.waitForIdle()

    // Senza: il registro non sente niente, il motore annidato si'.
    tap()
    assertTrue(trace.entries.isEmpty())
    assertEquals(3, nested.played.size)

    // Con: il registro ereditato sente, e il motore annidato riceve ancora tutto.
    nested.played.clear()
    compose.onNodeWithTag("traced").performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()
    assertEquals(3, trace.entries.size)
    assertEquals(3, nested.played.size)
  }
}
