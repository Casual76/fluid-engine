@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLayerProbe
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import dev.antigravity.fluidengine.ui.fluid.FluidMenuProbeFrame
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreVert
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Quello che S4c ha visto sul Tab S9 (1600 × 2560 a 340 dpi, cioe' ~753 × 1205 dp), riprodotto sui tre menu di `MenusDemo` montati
 * come nella galleria ([MenusScene]): la chrome, la schermata «Menù liquidi» e l'host dei modali che rifrange il fondale eletto dalla chrome.
 *
 * - **RTL (ripresa 24)**: nel «Altro», nel gruppo e nel contestuale le voci erano fuori dal vetro o assenti. Il vetro si posa
 *   in coordinate della finestra, e le voci con `absoluteOffset` dentro un contenitore allineato all'inizio, che in RTL e'
 *   a destra: la x delle voci era larghezza dell'host − la loro + la loro sinistra. Qui le voci devono stare nella sagoma che
 *   il menu disegna ([FluidMenuLayerProbe]) e sullo schermo, e il dito le deve scegliere.
 * - **Il ritorno dopo «Condividi» (ripresa 25)**: la scelta, il congedo, l'azione che cambia la pagina e la ricomposizione,
 *   nell'ordine dell'app, a 120 Hz. La sagoma del ritorno non deve mai ricrescere e il fondale del menu non deve cambiare.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w753dp-h1205dp-xhdpi")
class FluidContextMenuS4cTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var diagnosticsWere = false
  private val frames = ArrayList<FluidMenuProbeFrame>()
  private val hostBackdrops = ArrayList<GlassBackdropState>()

  @Before fun start() {
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidMenuLayerProbe.onFrame = { frames += it }
  }

  @After fun stop() {
    FluidMenuLayerProbe.onFrame = null
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun step(ms: Int) {
    compose.mainClock.advanceTimeBy(ms.toLong())
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms.toLong()))
    compose.waitForIdle()
  }

  private fun advance(ms: Int) = repeat(ms / 16) { step(16) }

  /** La pagina «Menù liquidi» della galleria, con la sua chrome e l'host dei modali alla radice. */
  private fun menusPage(direction: LayoutDirection) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      val chrome = rememberFluidChromeController()
      val scrollToTop = remember { FluidScrollToTopBus() }
      val host = rememberFluidGlassModalHostState()
      val fallback = rememberGlassBackdrop()
      val backdrop = chrome.activeBackdrop.value ?: fallback
      SideEffect { if (hostBackdrops.lastOrNull() !== backdrop) hostBackdrops += backdrop }
      CompositionLocalProvider(LocalLayoutDirection provides direction) {
        FluidTheme(settings = remember { EngineSettings() }) {
          CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
            Box(Modifier.fillMaxSize()) {
              ProvideFluidChrome(controller = chrome, bottomInset = 0.dp, scrollToTop = scrollToTop) {
                Box(Modifier.fillMaxSize().fluidChromePages().fluidGlassModalObscured()) {
                  MenusScene()
                }
              }
              // Sonda del gesto, come in FluidMenuBirthPickTest: il backend Skia di Robolectric non disegna il vetro liquido.
              FluidGlassModalHost(state = host, backdrop = backdrop, modifier = Modifier.drawWithContent { })
            }
          }
        }
      }
    }
    advance(256)
  }

  /** Il riquadro del nodo nella radice, non ritagliato dai genitori (in RTL le voci finivano fuori dallo schermo). */
  private fun SemanticsNodeInteraction.rect(): Rect = fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

  private fun screen(): Rect = compose.onRoot().rect()

  private fun waitForMenu(label: String) {
    var waited = 0
    while (compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()) {
      advance(16)
      waited += 16
      assertTrue("il menu non si apre", waited < 2_000)
    }
  }

  /** La sagoma che il menu disegna adesso, a menu aperto. */
  private fun glassAtRest(): Rect {
    val open = frames.lastOrNull { it.visible && !it.closing }
    return requireNotNull(open) { "nessun fotogramma del menu aperto" }.drawn
  }

  private fun assertVoicesInGlass(menu: String, labels: List<String>) {
    val glass = glassAtRest()
    val screen = screen()
    for (label in labels) {
      val voice = compose.onNodeWithText(label).rect()
      val tol = 1f
      assertTrue("$menu: «$label» sullo schermo: $voice in $screen", voice.left >= screen.left - tol && voice.right <= screen.right + tol)
      assertTrue(
        "$menu: «$label» dentro il vetro: voce $voice, vetro $glass",
        voice.left >= glass.left - tol && voice.right <= glass.right + tol && voice.top >= glass.top - tol && voice.bottom <= glass.bottom + tol,
      )
    }
  }

  /** Il dito che ha aperto il menu scorre sulla voce e si alza: la scelta dell'app, come nelle riprese. */
  private fun slideAndRelease(from: Offset, label: String, delayMillis: Long = 20) {
    val target = compose.onNodeWithText(label).rect().center
    compose.onRoot().performTouchInput {
      repeat(24) { i -> moveTo(from + (target - from) * ((i + 1) / 24f), delayMillis = delayMillis) }
      up()
    }
  }

  private val voices = listOf("Condividi", "Aggiungi ai preferiti", "Elimina")

  /** «Altro», il gruppo della foto o la riga: dove tenere il dito per aprirne il menu, e se si apre al tocco. */
  private fun anchorOf(menu: String): Pair<Offset, Boolean> = when (menu) {
    "altro" -> compose.onNodeWithTag("menus-bar-tap").rect().center to false
    "gruppo" -> compose.onNodeWithContentDescription("Menu del gruppo").rect().center to true
    else -> compose.onNodeWithTag("menus-row").rect().center to true
  }

  private fun anchorRect(menu: String): Rect = when (menu) {
    "altro" -> compose.onNodeWithTag("menus-bar-tap").rect()
    "gruppo" -> compose.onNodeWithContentDescription("Menu del gruppo").rect()
    else -> compose.onNodeWithTag("menus-row").rect()
  }

  private fun Rect.box(): String = "%.1f,%.1f,%.1f,%.1f".format(left, top, right, bottom)

  private fun scrollTo(menu: String) {
    // La barra in basso e' ferma; «Altro» e la riga stanno in cima alla pagina.
    if (menu != "gruppo") compose.onNodeWithTag(if (menu == "altro") "menus-bar-tap" else "menus-row").performScrollTo()
    advance(160)
  }

  /** Un menu in RTL: le voci nel vetro, sullo schermo, e il dito che sceglie [pick]. Una scena per menu, aperta una volta. */
  private fun rtlMenu(menu: String, pick: String) {
    menusPage(LayoutDirection.Rtl)
    scrollTo(menu)
    val (at, hold) = anchorOf(menu)
    if (hold) compose.onRoot().performTouchInput { down(at) } else compose.onRoot().performTouchInput { click(at) }
    waitForMenu("Condividi")
    advance(1_400)
    // Per la tavola (strumenti/tavola_rtl.py): lo schermo, l'ancora, il vetro disegnato e le voci, in pixel della radice.
    println("TAVOLA $menu schermo ${screen().box()} ancora ${runCatching { anchorRect(menu).box() }.getOrDefault("-")} vetro ${glassAtRest().box()}")
    for (label in voices) println("TAVOLA $menu voce $label ${runCatching { compose.onNodeWithText(label).rect().box() }.getOrDefault("-")}")
    assertVoicesInGlass(menu, voices)
    if (hold) {
      slideAndRelease(at, pick)
    } else {
      val voice = compose.onNodeWithText(pick).rect().center
      compose.onRoot().performTouchInput { click(voice) }
    }
    advance(900)
    compose.onNodeWithText("Scelta: $pick").assertExists()
  }

  @Test fun `in RTL le voci di Altro stanno nel vetro e il dito le sceglie`() = rtlMenu("altro", "Elimina")

  @Test fun `in RTL le voci del gruppo stanno nel vetro e il dito le sceglie`() = rtlMenu("gruppo", "Aggiungi ai preferiti")

  @Test fun `in RTL le voci del contestuale stanno nel vetro e il dito le sceglie`() = rtlMenu("contestuale", "Condividi")

  /**
   * Una presa della ripresa 25: il dito tiene premuta «Nota della riunione», aspetta [holdMs] dal primo fotogramma col menu,
   * scorre su «Condividi» e si alza. Scrive i fotogrammi del ritorno in [report] e li verifica: dal primo
   * fotogramma la sagoma parte da quella che si vedeva e non ricresce mai; il fondale del menu non cambia.
   */
  private fun condividi(take: String, rowAt: Offset, holdMs: Int, report: StringBuilder, problems: MutableList<String>) {
    frames.clear()
    compose.onRoot().performTouchInput { down(rowAt) }
    waitForMenu("Condividi")
    advance(holdMs)
    val shown = requireNotNull(frames.lastOrNull { it.visible && !it.closing }) { "$take: nessun fotogramma aperto" }.drawn
    // Il dito scorre senza far correre l'orologio: con 20 ms per evento i 24 eventi portavano la scelta quasi mezzo secondo
    // piu' in la', a nascita finita (il primo giro di misura).
    slideAndRelease(rowAt, "Condividi", delayMillis = 0)
    val pickedAt = frames.size
    repeat(60) { step(8) }
    val after = frames.drop(pickedAt)
    report.appendLine("$take: tenuto ${holdMs} ms, sagoma alla scelta $shown")
    val t0 = after.firstOrNull()?.nanos ?: 0L
    for (f in after) {
      report.appendLine(
        "  %6.1f ms  strato %08x  %s %s %s  p %.3f  nascita %s  ferma %s  disegnata %6.1f x %6.1f @ (%6.1f, %6.1f)  quieta %s  vetro %.2f  scala %.3f  inchiostro %.2f  fondale %08x".format(
          (f.nanos - t0) / 1e6, f.layer, if (f.visible) "aperto" else "chiuso", if (f.closing) "ritorno" else "andata ",
          if (f.controlledReturn) "contorno" else "liquido ", f.progress, f.birthMillis?.let { "%.0f".format(it) } ?: "-",
          f.frozen?.let { "%.0fx%.0f".format(it.width, it.height) } ?: "-",
          f.drawn.width, f.drawn.height, f.drawn.center.x, f.drawn.center.y, f.liquidQuiet, f.window, f.contentScale, f.inkAlpha,
          System.identityHashCode(f.backdrop),
        ),
      )
    }
    println(report)
    report.clear()
    compose.onNodeWithText("Scelta: Condividi").assertExists()
    val closing = after.filter { it.closing && it.window > 0.004f }
    if (closing.isEmpty()) problems += "$take: il ritorno non si vede"
    if (closing.isNotEmpty() && closing.first().drawn.width < shown.width * 0.9f) {
      problems += "$take: il primo fotogramma del ritorno non parte dalla sagoma che si vedeva: ${closing.first().drawn} contro $shown"
    }
    for (i in 1 until closing.size) {
      val a = closing[i - 1].drawn
      val b = closing[i].drawn
      if (b.width > a.width + shown.width * 0.01f || b.height > a.height + shown.height * 0.01f) {
        problems += "$take: la sagoma ricresce al fotogramma $i del ritorno: $a -> $b"
      }
    }
    if (after.map { System.identityHashCode(it.backdrop) }.distinct().size != 1) problems += "$take: il fondale del menu cambia nel ritorno"
    advance(1_200)
  }

  /**
   * S4c, ripresa 25: «Condividi» scelto col dito che ha tenuto premuta «Nota della riunione». Sul tablet, al primo fotogramma
   * dopo la scelta, la goccia del seme col menu intero rimpicciolito dentro (f0516); poi un pannello all'80% con le voci
   * sfocate (f0517); poi niente (f0518), col velo appena all'11, 16 e 30% della sua dissolvenza. Qui la stessa sequenza
   * nell'ordine dell'app: a menu fermo (tre prese, come la ripresa) e durante la nascita, fino alla sua coda (le voci sono
   * gia' nitide da ~334 ms, la nascita si ferma a ~770). Un fotogramma lungo subito dopo la scelta (sul tablet f0516–f0518
   * arrivano a 58, 33 e 26 ms) qui non si fa: l'orologio dei test avanza a fotogrammi da 16 ms.
   */
  @Test fun `dopo Condividi il ritorno non ricresce e il fondale non cambia`() {
    menusPage(LayoutDirection.Ltr)
    compose.onNodeWithTag("menus-row").performScrollTo()
    advance(160)
    val rowAt = compose.onNodeWithTag("menus-row").rect().center
    val report = StringBuilder()
    val problems = ArrayList<String>()
    for (take in 1..3) condividi("a menu fermo, presa $take", rowAt, holdMs = 1_400 + 16 * (take - 1), report = report, problems = problems)
    for (hold in listOf(304, 448, 560, 656, 752, 864)) condividi("nella nascita", rowAt, holdMs = hold, report = report, problems = problems)
    assertTrue(problems.joinToString("\n"), problems.isEmpty())
  }
}

/** Le voci di `MenusDemo`: due sezioni, una voce spenta, una distruttiva. */
private fun sceneActions(pick: (String) -> Unit): List<FluidContextAction> = listOf(
  FluidContextAction(label = "Condividi", icon = FluidIcons.Share) { pick("Condividi") },
  FluidContextAction(label = "Aggiungi ai preferiti", icon = FluidIcons.Filled.Star) { pick("Aggiungi ai preferiti") },
  FluidContextAction(label = "Modifica (spenta)", icon = FluidIcons.Edit, enabled = false) { pick("Modifica") },
  FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) { pick("Elimina") },
)

/**
 * I tre menu di S4c come in `MenusDemo`, senza il resto della demo (montata intera, in Robolectric non risulta mai
 * inattiva: i giri `lg-corr-s4c-prima-r2`..`r4` si fermavano alla prima attesa): «Altro» che si apre al tocco in alto a
 * destra (in RTL a sinistra), il gruppo della barra in basso con il suo tasto del menu, la riga «Nota della riunione» col
 * contestuale. Come nella demo, la scelta riscrive il testo in cima e ricompone la pagina, e `pick` e' una lambda nuova a
 * ogni composizione.
 */
@Composable
private fun MenusScene() {
  var lastPick by remember { mutableStateOf<String?>(null) }
  val pick: (String) -> Unit = { lastPick = it }
  FluidScreen(
    title = "Menù liquidi",
    onBack = {},
    toolbar = {
      group("photo-tools", placement = FluidToolbarPlacement.BottomBar) {
        button("share-photo", FluidIcons.Share, "Condividi foto", onClick = { pick("Condividi foto") })
        button("star-photo", FluidIcons.Filled.Star, "Preferiti foto", onClick = { pick("Preferiti foto") })
        action("photo-menu", FluidIcons.MoreVert, "Menu del gruppo", menu = { sceneActions(pick) })
      }
    },
  ) {
    item {
      Text(text = lastPick?.let { "Scelta: $it" } ?: "Tieni premuto e trascina su una voce, oppure rilascia e tocca.")
    }
    item {
      Box(Modifier.fillMaxWidth().height(72.dp)) {
        FluidBarAction(
          icon = FluidIcons.MoreVert,
          contentDescription = "Altro",
          onClick = {},
          actions = { sceneActions(pick) },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp).testTag("menus-bar-tap"),
        )
      }
    }
    item {
      FluidListGroup {
        FluidListRow(
          title = "Nota della riunione",
          subtitle = "Tieni premuto, trascina, rilascia",
          onClick = { pick("Nota (tocco)") },
          contextActions = { sceneActions(pick) },
          modifier = Modifier.testTag("menus-row"),
        )
      }
    }
    items(12) { i -> Text("Riga di pagina ${i + 1}", modifier = Modifier.fillMaxWidth().height(48.dp)) }
  }
}

