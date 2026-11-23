package dev.antigravity.fluidengine.sample.dynamic

import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
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
 * F6B.15 · le barre e il campo dopo le riprese del 23 novembre 2026, nelle scene vere della galleria:
 * - **i gettoni sul tablet**: col campo in alto la fila delle pillole non copre l'intestazione della lista (a 1205 dp, la
 *   larghezza del Tab S9 in orizzontale, e a 360 e 412 dp);
 * - **la finestra che non si sposta**: con un campo nella barra la finestra passa a `adjustResize` (l'assistente con la
 *   tastiera su ha la barra in alto in vista) e torna com'era quando il campo se ne va; un'app con `adjustResize` non cambia;
 * - **i gettoni negli altri campi**: il campo sotto il titolo e `FluidSearchField` nella pagina, col viaggio dalla pillola;
 * - **la barra in basso a 28 dp dal bordo dello schermo**, come iOS 26.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class F6b15FieldsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var root: View

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  // --- Aiuti (quelli di FluidDynamicFieldsUiTest) ---------------------------------------------------------------------

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  private fun open(scene: DynamicScene?) = show { DynamicElementsPage(onBack = {}, initial = scene) }

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        root = LocalView.current
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(modals, backdrop = null)
          }
        }
      }
    }
    advance(900)
  }

  private fun keyboard(heightDp: Int) {
    compose.runOnUiThread {
      val px = (heightDp * root.resources.displayMetrics.density).toInt()
      ViewCompat.dispatchApplyWindowInsets(root, WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
        .setVisible(WindowInsetsCompat.Type.ime(), px > 0)
        .build())
    }
    Snapshot.sendApplyNotifications()
    advance(600)
  }

  private fun targets(name: String): List<SemanticsNode> = compose.onAllNodes(
    hasText(name) or hasContentDescription(name),
  ).fetchSemanticsNodes().filter { SemanticsActions.OnClick in it.config && SemanticsActions.SetText !in it.config }

  private fun SemanticsNode.dp(): Rect {
    val id = this.id
    val box = compose.onNode(SemanticsMatcher("nodo $id") { it.id == id }, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return Rect(box.left.value, box.top.value, box.right.value, box.bottom.value)
  }

  private fun tap(node: SemanticsNode) {
    val action = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
  }

  private fun pills(name: String): List<SemanticsNode> = compose.onAllNodesWithText(name).fetchSemanticsNodes()
    .filter { SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Selected) == false &&
      SemanticsProperties.ContentDescription !in it.config }

  private fun tokenNodes(name: String): List<SemanticsNode> = compose.onAllNodesWithContentDescription(name).fetchSemanticsNodes()
    .filter { SemanticsActions.OnClick in it.config && it.config.getOrNull(SemanticsProperties.Selected) != null }

  private fun shows(text: String): Boolean = compose.onAllNodes(
    hasText(text, substring = true) or hasContentDescription(text, substring = true), useUnmergedTree = true,
  ).fetchSemanticsNodes().isNotEmpty()

  private fun screenDp(): Rect = compose.onRoot().fetchSemanticsNode().dp()

  private fun header(text: String): Rect = compose.onAllNodes(hasText(text, ignoreCase = true), useUnmergedTree = true)
    .fetchSemanticsNodes().single().dp()

  private fun Rect.apart(other: Rect): Boolean =
    right <= other.left + 0.5f || left >= other.right - 0.5f || bottom <= other.top + 0.5f || top >= other.bottom - 0.5f

  // --- I gettoni sul tablet: la fila e l'intestazione --------------------------------------------------------------------

  private fun checkPillsApartFromHeader(width: String) {
    open(DynamicScene.Tokens)
    tap(targets("Cerca nella posta").first())
    advance(600)
    val head = header("Tutta la posta")
    val screen = screenDp()
    for (name in listOf("Anna", "Luca", "Marta", "Giorgio")) {
      val pill = pills(name).singleOrNull()?.dp() ?: continue
      assertTrue("$width: la pillola «$name» $pill e l'intestazione $head disgiunte", pill.apart(head))
      assertTrue("$width: la pillola «$name» nello schermo", pill.top >= screen.top - 0.5f && pill.bottom <= screen.bottom + 0.5f)
    }
    assertTrue("$width: la prima pillola c'è", pills("Anna").size == 1)
  }

  @Test @Config(qualifiers = "w1205dp-h753dp-xhdpi")
  fun `sul tablet la fila delle pillole sotto il campo in alto non copre l'intestazione`() = checkPillsApartFromHeader("tablet")

  @Test @Config(qualifiers = "w360dp-h780dp-xhdpi")
  fun `a 360 dp la fila delle pillole e l'intestazione sono disgiunte`() = checkPillsApartFromHeader("360")

  @Test fun `a 412 dp la fila delle pillole e l'intestazione sono disgiunte`() = checkPillsApartFromHeader("412")

  /**
   * «Anna», la prima pillola, col dito vero (non l'azione semantica): sul Tab S9 in verticale il tocco arrivava alla fila
   * (`tokens.touch … flights=0`) ma la pillola non diventava gettone, mentre «Luca» sì.
   */
  private fun checkFirstPillTakesTheFinger(width: String) {
    open(DynamicScene.Tokens)
    // Anche il campo si apre col dito: la fila nasce mentre il dito che l'ha aperto è ancora in giro.
    val search = targets("Cerca nella posta").first().id
    compose.onNode(SemanticsMatcher("il campo") { it.id == search }).performTouchInput { click() }
    Snapshot.sendApplyNotifications()
    advance(600)
    for (name in listOf("Anna", "Luca")) {
      val id = pills(name).single().id
      compose.onNode(SemanticsMatcher("pillola $name") { it.id == id }).performTouchInput { click() }
      Snapshot.sendApplyNotifications()
      advance(900)
      assertTrue("$width: «$name» toccata col dito diventa gettone", tokenNodes(name).size == 1 && pills(name).isEmpty())
    }
  }

  @Test @Config(qualifiers = "w753dp-h1205dp-xhdpi")
  fun `sul tablet in verticale la prima pillola prende il dito`() = checkFirstPillTakesTheFinger("tablet")

  @Test fun `a 412 dp la prima pillola prende il dito`() = checkFirstPillTakesTheFinger("412")

  // --- La finestra che non si sposta -------------------------------------------------------------------------------------

  private fun adjust(): Int = compose.activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST

  /**
   * Il modo che il `ViewRootImpl` della finestra manda al window manager (la sua copia `mWindowAttributes`, quella di
   * `relayoutWindow`): sul Tab S9 il modo nuovo restava nella `Window` e il window manager teneva quello dell'avvio
   * (`dumpsys window`: `sim={adjust=pan forwardNavigation}`).
   */
  private fun sentAdjust(): Int {
    var mode = 0
    compose.runOnUiThread {
      val viewRoot = compose.activity.window.decorView.parent
      val field = viewRoot.javaClass.getDeclaredField("mWindowAttributes").apply { isAccessible = true }
      mode = (field.get(viewRoot) as WindowManager.LayoutParams).softInputMode
    }
    return mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
  }

  @Test fun `con un campo nella barra la finestra non si sposta, e torna com'era`() {
    compose.runOnUiThread { compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN) }
    val scene = mutableStateOf(true)
    show { if (scene.value) DynamicElementsPage(onBack = {}, initial = DynamicScene.AssistantComposer) }
    advance(100)
    assertEquals("l'assistente ha il campo: la finestra non si sposta più", WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, adjust())
    assertEquals("e il modo arriva al window manager, non resta nella Window", WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, sentAdjust())
    // La tastiera su: la barra in alto resta in vista (in Robolectric la finestra non si sposta mai; sul tablet sì, prima).
    fun backs() = compose.onAllNodesWithContentDescription("Indietro").fetchSemanticsNodes()
      .filter { SemanticsActions.OnClick in it.config }.map { it.dp() }
    tap(targets("Scrivi un messaggio…").first())
    val before = backs()
    keyboard(320)
    advance(300)
    val after = backs()
    val screen = screenDp()
    // «Indietro» sta a 1 dp sopra il bordo anche senza tastiera (il vetro della lente): conta che non salga con la tastiera.
    assertTrue("la barra in alto in vista: gli indietro $after (prima $before), lo schermo $screen",
      after.isNotEmpty() && after.size == before.size &&
        after.zip(before).all { (now, then) -> kotlin.math.abs(now.top - then.top) <= 0.5f && now.bottom > screen.top + 24f })
    keyboard(0)
    // Il campo se ne va con la sua schermata: il modo dell'app torna com'era.
    compose.runOnUiThread { scene.value = false }
    Snapshot.sendApplyNotifications()
    advance(300)
    assertEquals("uscito il campo, il modo dell'app torna", WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN, adjust())
    assertEquals("anche al window manager", WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN, sentAdjust())
  }

  @Test fun `un'app che dichiara adjustResize non cambia`() {
    compose.runOnUiThread { compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
    open(DynamicScene.AssistantComposer)
    assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, adjust())
  }

  @Test fun `un'app che non vuole niente non cambia`() {
    compose.runOnUiThread { compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) }
    open(DynamicScene.AssistantComposer)
    assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING, adjust())
  }

  // --- I gettoni negli altri campi -----------------------------------------------------------------------------------------

  /** Il viaggio dalla pillola al campo e ritorno, nel campo [field]: la stessa prova del campo della barra. */
  private fun checkTokenTrip(name: String, other: String, keeps: String, drops: String) {
    val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().dp()
    val pill = pills(name).single()
    assertTrue("la fila sta sotto il campo", pill.dp().top >= field.bottom - 0.5f)
    val before = pills(other).single().dp()
    val action = pill.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(100)
    assertTrue("a metà volo «$name» non si legge, né pillola né gettone", pills(name).isEmpty() && tokenNodes(name).isEmpty())
    advance(600)
    val token = tokenNodes(name).single()
    val editor = compose.onNode(hasSetTextAction()).fetchSemanticsNode().dp()
    assertEquals("il gettone sta nella riga del campo", editor.center.y, token.dp().center.y, 6f)
    assertTrue("prima del testo", token.dp().right <= editor.left + 1f)
    assertTrue("la pagina si restringe", shows(keeps) && !shows(drops))
    assertTrue("la pillola non c'è più", pills(name).isEmpty())
    assertEquals("la prima pillola resta al suo posto", before.left, pills(other).single().dp().left, 0.5f)
    // Il gettone toccato torna pillola, al suo posto.
    tap(tokenNodes(name).single())
    advance(300)
    assertTrue("toccato, torna pillola", pills(name).size == 1 && tokenNodes(name).isEmpty())
    assertTrue("di nuovo tutto", shows(drops))
  }

  @Test fun `il campo sotto il titolo ha i gettoni e il viaggio dalla pillola`() {
    open(DynamicScene.TokensUnderTitle)
    assertTrue("a campo chiuso niente fila", pills("Luca").isEmpty())
    val editor = compose.onNode(hasSetTextAction()).fetchSemanticsNode()
    compose.runOnUiThread { editor.config[SemanticsActions.RequestFocus].action?.invoke() }
    Snapshot.sendApplyNotifications()
    advance(900)
    checkTokenTrip("Luca", "Anna", keeps = "Partita di giovedì", drops = "Fattura di novembre")
  }

  @Test fun `FluidSearchField nella pagina ha i gettoni e il viaggio dalla pillola`() {
    open(DynamicScene.TokensInPage)
    checkTokenTrip("Idee", "Lavoro", keeps = "Un'app per le piante", drops = "Lista della spesa")
  }

  // --- La barra in basso ---------------------------------------------------------------------------------------------------

  @Test fun `la barra in basso della pagina ha il fondo dei tasti a 28 dp dal bordo dello schermo`() {
    open(DynamicScene.SelectionFiles)
    val screen = screenDp()
    val bottomInset = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    val navigation = bottomInset / root.resources.displayMetrics.density
    val expected = maxOf(28f, navigation + 4f)
    for (name in listOf("Filtra", "Nuova cartella")) {
      val button = targets(name).single().dp()
      assertEquals("«$name»: il fondo del vetro dal bordo dello schermo", expected, screen.bottom - button.bottom, 1f)
    }
  }
}
