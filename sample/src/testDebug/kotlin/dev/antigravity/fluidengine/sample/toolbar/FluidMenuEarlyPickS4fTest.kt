@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenu
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidMenuOpensOn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMenuLayerProbe
import dev.antigravity.fluidengine.ui.fluid.FluidMenuProbeFrame
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
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
 * S4f sul Tab S9 (NO 4): sulla riga in scuro il dito sceglie «Condividi» durante la crescita, la voce resta evidenziata
 * (f0214–f0217), e dopo il rilascio il menu resta aperto senza l'azione (f0224); il gruppo fa lo stesso. Al PC il test del
 * contestuale ([FluidMenuBirthPickTest]) era verde: il dito arrivava sulla voce e si alzava nello stesso istante.
 *
 * Qui il gesto del tablet, nell'ordine dei suoi eventi: il dito tiene premuta la sorgente, il menu nasce, il dito arriva
 * sulla prima voce, **resta fermo** mentre il menu finisce di crescere, e si alza. Tre modi di arrivare: sulla voce come si
 * vede in quell'istante, sul suo riquadro a riposo (le coordinate lette a menu fermo), e con eventi ripetuti nello stesso
 * punto. Per ogni presa: la scelta che si vede dopo il rilascio e l'istante della nascita.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w753dp-h1205dp-xhdpi")
class FluidMenuEarlyPickS4fTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var diagnosticsWere = false
  private val frames = ArrayList<FluidMenuProbeFrame>()
  private val picks = ArrayList<String>()
  private var hostState: dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHostState? = null

  /** La selezione del menu aperto: la voce accesa e dove la mappa del dito porta [at]. Diagnosi del NO 4. */
  private fun selectionAt(at: Offset): String {
    val entry = hostState?.entries?.lastOrNull { it.visible } ?: return "nessun menu"
    val s = entry.selection
    val g = s.geometry
    val rest = s.pointerMap?.invoke(at)
    return "accesa=${s.hovered} proprietario=${s.owner} riposo=$rest celle=${g?.cells?.joinToString { "%.0f..%.0f".format(it.top, it.bottom) }} " +
      "x=${g?.cells?.firstOrNull()?.let { "%.0f..%.0f".format(it.left, it.right) }}"
  }

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

  private fun SemanticsNodeInteraction.rect(): Rect = fetchSemanticsNode().let { Rect(it.positionInRoot, it.size.toSize()) }

  private fun scene() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      val chrome = rememberFluidChromeController()
      val scrollToTop = remember { FluidScrollToTopBus() }
      val host = rememberFluidGlassModalHostState()
      hostState = host
      val fallback = rememberGlassBackdrop()
      val backdrop = chrome.activeBackdrop.value ?: fallback
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            ProvideFluidChrome(controller = chrome, bottomInset = 0.dp, scrollToTop = scrollToTop) {
              Box(Modifier.fillMaxSize().fluidChromePages().fluidGlassModalObscured()) {
                EarlyPickScene { picks += it }
              }
            }
            FluidGlassModalHost(state = host, backdrop = backdrop, modifier = Modifier.drawWithContent { })
          }
        }
      }
    }
    advance(256)
  }

  private fun waitForMenu(): Int {
    var waited = 0
    while (compose.onAllNodesWithText("Condividi").fetchSemanticsNodes().isEmpty()) {
      advance(16)
      waited += 16
      assertTrue("il menu non si apre", waited < 2_000)
    }
    return waited
  }

  private fun source(menu: String): Offset = when (menu) {
    "gruppo" -> compose.onNodeWithContentDescription("Menu del gruppo").rect().center
    "tondo" -> compose.onNodeWithTag("early-circle").rect().center
    "foto" -> compose.onNodeWithTag("early-photo").rect().center
    else -> compose.onNodeWithTag("early-row").rect().center
  }

  /** «Condividi» a menu fermo: il dito tiene premuto, aspetta la posa, si legge il riquadro e si sceglie. */
  private fun restTarget(menu: String): Offset {
    val at = source(menu)
    compose.onRoot().performTouchInput { down(at) }
    waitForMenu()
    advance(1_400)
    val target = compose.onNodeWithText("Condividi").rect().center
    compose.onRoot().performTouchInput {
      moveTo(target, delayMillis = 0)
      up()
    }
    advance(1_200)
    return target
  }

  /**
   * Una presa: il dito tiene premuta la sorgente, [arriveMs] dopo il primo fotogramma del menu arriva su «Condividi» ([how]),
   * resta fermo [stillMs] e si alza. Vero se «Condividi» e' scelta.
   */
  private fun take(menu: String, how: String, arriveMs: Int, stillMs: Int, rest: Offset, log: MutableList<String>): Boolean {
    val at = source(menu)
    val before = picks.size
    frames.clear()
    compose.onRoot().performTouchInput { down(at) }
    waitForMenu()
    advance(arriveMs)
    // Dove si vede la voce: i limiti nella radice con la scala dello strato delle voci (`boundsInRoot`, come
    // FluidMenuBirthPickTest). `rect()` (posizione + misura del layout) non scala: a 208 ms il dito finiva di lato alla voce.
    val seen = compose.onNodeWithText("Condividi").fetchSemanticsNode().boundsInRoot.center
    val target = if (how == "a riposo") rest else seen
    compose.onRoot().performTouchInput { moveTo(target, delayMillis = 0) }
    log += "TAVOLA $menu $how arrivo=$arriveMs dopo-lo-spostamento ${selectionAt(target)}"
    if (how == "ripetuto") {
      repeat(stillMs / 16) {
        compose.onRoot().performTouchInput { moveTo(target, delayMillis = 0) }
        step(16)
      }
    } else {
      advance(stillMs)
    }
    val atRelease = frames.lastOrNull()
    // La cella che avrebbe scelto la regola di prima: quella sotto il punto riportato alla posa dalla mappa di adesso.
    val ruleBefore = hostState?.entries?.lastOrNull { it.visible }?.selection?.let { s ->
      val p = s.pointerMap?.invoke(target) ?: target
      s.geometry?.cells?.indexOfFirst { it.contains(p) }
    }
    log += "TAVOLA $menu $how arrivo=$arriveMs prima-del-rilascio ${selectionAt(target)} regola-di-prima=$ruleBefore"
    compose.onRoot().performTouchInput { up() }
    advance(1_200)
    val picked = picks.drop(before)
    val ok = picked == listOf("Condividi")
    log += "TAVOLA $menu $how arrivo=$arriveMs fermo=$stillMs nascita-al-rilascio=${atRelease?.birthMillis ?: "-"} " +
      "p=${atRelease?.progress} visto=$seen bersaglio=$target riposo=$rest scelte=$picked ${if (ok) "SCEGLIE" else "RESTA-APERTO"}"
    // Un menu rimasto aperto si chiude toccando fuori, come sul tablet.
    if (compose.onAllNodesWithText("Condividi").fetchSemanticsNodes().isNotEmpty()) {
      compose.onRoot().performTouchInput { down(Offset(20f, 20f)); up() }
      advance(1_200)
    }
    return ok
  }

  private fun menu(menu: String) {
    scene()
    when (menu) {
      "gruppo" -> Unit
      "tondo" -> compose.onNodeWithTag("early-circle").performScrollTo()
      "foto" -> compose.onNodeWithTag("early-photo").performScrollTo()
      else -> compose.onNodeWithTag("early-row").performScrollTo()
    }
    advance(160)
    val rest = restTarget(menu)
    val log = ArrayList<String>()
    val failed = ArrayList<String>()
    for (how in listOf("come si vede", "a riposo", "ripetuto")) {
      for ((arrive, still) in listOf(304 to 0, 304 to 208, 208 to 304, 400 to 240)) {
        if (!take(menu, how, arrive, still, rest, log)) failed += "$how arrivo $arrive fermo $still"
      }
    }
    log.forEach(::println)
    assertTrue("$menu, restano aperti: $failed", failed.isEmpty())
  }

  /**
   * NO 1: l'ordine del velo e della nascita di «Altro» nel livello vero. Sul tablet il velo fa da orologio (parte nello stesso
   * effetto); qui si legge se la nascita parte nello stesso fotogramma del velo o uno dopo.
   */
  @Test fun `Altro, il velo e la nascita partono insieme`() {
    scene()
    val at = compose.onNodeWithTag("early-altro").rect().center
    frames.clear()
    compose.onRoot().performTouchInput { down(at); up() }
    repeat(14) { step(16) }
    val altro = frames.filter { it.visible }
    val t0 = altro.firstOrNull()?.nanos ?: 0L
    for (f in altro.take(14)) {
      println(
        "TAVOLA altro-ordine t=${(f.nanos - t0) / 1_000_000} velo=${"%.3f".format(f.fade)} nascita=${"%.1f".format(f.journeyBirth)} " +
          "p=${"%.3f".format(f.progress)} larghezza=${"%.1f".format(f.drawn.width)} nata=${f.born} " +
          "orologio-corre=${f.birthRunning} viaggio-corre=${f.progressRunning}",
      )
    }
    assertTrue("nessun fotogramma di «Altro»", altro.isNotEmpty())
    // Il velo e' una dissolvenza di 110 ms (EaseOut): il suo istante, letto dal valore, e quello della nascita stanno nello
    // stesso fotogramma (16 ms) per tutto il tratto in cui il velo cresce.
    for (f in altro) {
      if (f.fade <= 0.01f || f.fade >= 0.99f) continue
      var lo = 0f
      var hi = 110f
      repeat(30) {
        val mid = (lo + hi) / 2f
        if (androidx.compose.animation.core.EaseOut.transform(mid / 110f) < f.fade) lo = mid else hi = mid
      }
      assertTrue("il velo a ${"%.0f".format(lo)} ms e la nascita a ${f.journeyBirth} ms", kotlin.math.abs(lo - f.journeyBirth) <= 17f)
    }
  }

  /**
   * NO 5: in «Altro» scuro il gesto dal bordo chiudeva il menu, ma durante il trascinamento il vetro restava fermo (f0084–f0156)
   * e partiva al rilascio (f0162). Qui il gesto vero, dal dispatcher dell'attivita': il vetro segue il dito verso il tasto, il
   * congedo parte dalla sagoma tirata, e un gesto annullato torna al pannello col menu aperto.
   */
  @Test fun `Altro, l'Indietro predittivo tira il vetro col dito`() {
    scene()
    val at = compose.onNodeWithTag("early-altro").rect().center
    val dispatcher = compose.activity.onBackPressedDispatcher
    fun open(): Rect {
      compose.onRoot().performTouchInput { down(at); up() }
      advance(1_400)
      return requireNotNull(frames.lastOrNull { it.visible && !it.closing }).drawn
    }
    fun drag(progress: List<Float>): List<Rect> = progress.map { p ->
      compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(2f + 520f * p, 1160f, p, BackEventCompat.EDGE_LEFT)) }
      repeat(4) { step(16) }
      frames.last().drawn
    }
    // Il gesto completato.
    val rest = open()
    compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(2f, 1160f, 0f, BackEventCompat.EDGE_LEFT)) }
    step(16)
    val steps = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f)
    val pulled = drag(steps)
    steps.zip(pulled).forEach { (p, r) -> println("TAVOLA indietro gesto=$p vetro=${r.left.toInt()},${r.top.toInt()},${r.right.toInt()},${r.bottom.toInt()} riposo=${rest.height.toInt()}") }
    val before = frames.size
    compose.runOnUiThread { dispatcher.onBackPressed() }
    repeat(40) { step(8) }
    val back = frames.drop(before).filter { it.closing }
    back.take(12).forEach { println("TAVOLA indietro ritorno vetro=${it.drawn.left.toInt()},${it.drawn.top.toInt()},${it.drawn.right.toInt()},${it.drawn.bottom.toInt()}") }
    advance(1_200)
    assertTrue("il menu si chiude", compose.onAllNodesWithText("Condividi").fetchSemanticsNodes().isEmpty())
    for (i in 1 until pulled.size) {
      assertTrue("il vetro segue il dito: ${pulled[i - 1]} -> ${pulled[i]}", pulled[i].height <= pulled[i - 1].height + 1f)
    }
    assertTrue("a fine gesto il vetro e' tirato: ${pulled.last().height} su ${rest.height}", pulled.last().height < rest.height * 0.95f)
    val first = requireNotNull(back.firstOrNull()) { "nessun fotogramma del ritorno" }.drawn
    assertTrue("il ritorno parte dalla sagoma tirata: $first contro ${pulled.last()}", first.height <= pulled.last().height + rest.height * 0.02f)
    for (i in 1 until back.size) {
      assertTrue("il ritorno non ricresce: ${back[i - 1].drawn} -> ${back[i].drawn}", back[i].drawn.height <= back[i - 1].drawn.height + rest.height * 0.01f)
    }
    // Il gesto annullato: il vetro torna al pannello e il menu resta aperto.
    open()
    compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(2f, 1160f, 0f, BackEventCompat.EDGE_LEFT)) }
    step(16)
    val half = drag(listOf(0.3f, 0.6f)).last()
    compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
    advance(800)
    val again = requireNotNull(frames.lastOrNull { it.visible && !it.closing }).drawn
    println("TAVOLA indietro annullato tirato=${half.height.toInt()} poi=${again.height.toInt()} riposo=${rest.height.toInt()}")
    assertTrue("annullato, il menu resta aperto", compose.onAllNodesWithText("Condividi").fetchSemanticsNodes().isNotEmpty())
    assertTrue("annullato, torna al pannello: ${again.height} contro ${rest.height}", kotlin.math.abs(again.height - rest.height) < 2f)
  }

  @Test fun `nel contestuale il dito fermo sulla voce durante la nascita la sceglie al rilascio`() = menu("contestuale")

  /** S4g (NV): la scelta durante la crescita sul tondo, che si solleva tondo. */
  @Test fun `sul tondo il dito fermo sulla voce durante la nascita la sceglie al rilascio`() = menu("tondo")

  /** S4g (NV): la scelta durante la crescita sulla foto, che diventa la sua anteprima e apre il menu accanto. */
  @Test fun `sulla foto il dito fermo sulla voce durante la nascita la sceglie al rilascio`() = menu("foto")

  /**
   * S4g (NV): l'Indietro predittivo con l'inversione del dito. Il gesto parte dal bordo, tira il vetro, poi il dito torna verso
   * il bordo e si alza li': il sistema annulla il gesto. Il vetro torna indietro col dito, e il menu resta aperto al suo posto.
   */
  @Test fun `Altro, l'Indietro col dito che torna verso il bordo lascia il menu aperto`() {
    scene()
    val at = compose.onNodeWithTag("early-altro").rect().center
    val dispatcher = compose.activity.onBackPressedDispatcher
    compose.onRoot().performTouchInput { down(at); up() }
    advance(1_400)
    val rest = requireNotNull(frames.lastOrNull { it.visible && !it.closing }).drawn
    compose.runOnUiThread { dispatcher.dispatchOnBackStarted(BackEventCompat(2f, 1160f, 0f, BackEventCompat.EDGE_LEFT)) }
    step(16)
    val path = listOf(0.1f, 0.3f, 0.6f, 0.3f, 0.1f, 0f)
    val heights = path.map { p ->
      compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(BackEventCompat(2f + 520f * p, 1160f, p, BackEventCompat.EDGE_LEFT)) }
      repeat(4) { step(16) }
      frames.last().drawn.height
    }
    path.zip(heights).forEach { (p, h) -> println("TAVOLA indietro-inverso gesto=$p vetro=${h.toInt()} riposo=${rest.height.toInt()}") }
    compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
    advance(800)
    val again = requireNotNull(frames.lastOrNull { it.visible && !it.closing }).drawn
    println("TAVOLA indietro-inverso annullato poi=${again.height.toInt()} riposo=${rest.height.toInt()}")
    // Andando il vetro si accorcia, tornando ricresce: alla stessa distanza dal bordo la stessa sagoma.
    assertTrue("il vetro si accorcia: $heights", heights[2] < heights[0])
    assertTrue("il vetro ricresce col dito: $heights", heights[4] > heights[2] && heights[5] >= heights[4] - 1f)
    assertTrue("alla stessa distanza la stessa sagoma: $heights", kotlin.math.abs(heights[4] - heights[0]) < rest.height * 0.02f)
    assertTrue("il menu resta aperto", compose.onAllNodesWithText("Condividi").fetchSemanticsNodes().isNotEmpty())
    assertTrue("torna al suo posto: ${again.height} contro ${rest.height}", kotlin.math.abs(again.height - rest.height) < 2f)
    assertTrue("e nello stesso punto: $again contro $rest", kotlin.math.abs(again.top - rest.top) < 2f && kotlin.math.abs(again.left - rest.left) < 2f)
  }

  @Test fun `nel gruppo il dito fermo sulla voce durante la nascita la sceglie al rilascio`() = menu("gruppo")
}

private fun earlyActions(pick: (String) -> Unit): List<FluidContextAction> = listOf(
  FluidContextAction(label = "Condividi", icon = FluidIcons.Share) { pick("Condividi") },
  FluidContextAction(label = "Aggiungi ai preferiti", icon = FluidIcons.Filled.Star) { pick("Aggiungi ai preferiti") },
  FluidContextAction(label = "Modifica (spenta)", icon = FluidIcons.Edit, enabled = false) { pick("Modifica") },
  FluidContextAction(label = "Elimina", icon = FluidIcons.Delete, destructive = true, startsSection = true) { pick("Elimina") },
)

/** Il gruppo della barra in basso col suo tasto del menu e la riga col contestuale, come in `MenusDemo`. */
@Composable
private fun EarlyPickScene(pick: (String) -> Unit) {
  FluidScreen(
    title = "Menù liquidi",
    onBack = {},
    toolbar = {
      group("photo-tools", placement = FluidToolbarPlacement.BottomBar) {
        button("share-photo", FluidIcons.Share, "Condividi foto", onClick = {})
        button("star-photo", FluidIcons.Filled.Star, "Preferiti foto", onClick = {})
        action("photo-menu", FluidIcons.MoreVert, "Menu del gruppo", menu = { earlyActions(pick) })
      }
    },
  ) {
    item { Text("Tieni premuto e trascina su una voce.") }
    item {
      Box(Modifier.fillMaxWidth().height(72.dp)) {
        FluidBarAction(
          icon = FluidIcons.MoreVert,
          contentDescription = "Altro",
          onClick = {},
          actions = { earlyActions(pick) },
          opensOn = FluidMenuOpensOn.Tap,
          modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp).testTag("early-altro"),
        )
      }
    }
    item {
      FluidListGroup {
        FluidListRow(
          title = "Nota della riunione",
          subtitle = "Tieni premuto, trascina, rilascia",
          onClick = {},
          contextActions = { earlyActions(pick) },
          modifier = Modifier.testTag("early-row"),
        )
      }
    }
    // S4g (NV): il tondo e la foto, come nella galleria (MenusDemo, «Anteprima e forma»).
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
        Box(
          modifier = Modifier
            .size(96.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .drawBehind { drawRect(Color(0xFF8E6E53)) }
            .fluidContextMenu(
              actions = { earlyActions(pick) },
              previewShape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
              preview = { Box(Modifier.size(width = 280.dp, height = 180.dp).drawBehind { drawRect(Color(0xFF8E6E53)) }) },
            )
            .testTag("early-photo"),
        )
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .drawBehind { drawRect(Color(0xFF34C759)) }
            .fluidContextMenu(actions = { earlyActions(pick) }, previewShape = CircleShape)
            .testTag("early-circle"),
        )
      }
    }
    items(12) { i -> Text("Riga di pagina ${i + 1}", modifier = Modifier.fillMaxWidth().height(48.dp)) }
  }
}
