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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
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
    // Dove sta la fila: sul Tab S9 sotto il campo in alto (la riga della barra in alto), altrove sopra la tastiera.
    val anna = pills("Anna").single().dp()
    val field = compose.onNode(hasSetTextAction()).fetchSemanticsNode().dp()
    println("$width: la fila ${if (anna.top >= field.bottom - 0.5f) "sotto" else "sopra"} il campo $field, «Anna» $anna, " +
      "lo schermo ${screenDp()}")
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

  @Test @Config(qualifiers = "w1205dp-h753dp-xhdpi")
  fun `sul tablet in orizzontale la prima pillola prende il dito`() = checkFirstPillTakesTheFinger("1205")

  /** Lo stesso col vetro vero (verdetti degli shader accettati), se Robolectric lo regge: sul Tab S9 c'è lui. */
  @Test @Config(qualifiers = "w753dp-h1205dp-xhdpi")
  fun `sul tablet in verticale col vetro vero la prima pillola prende il dito`() {
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    try {
      checkFirstPillTakesTheFinger("tablet, vetro vero")
    } catch (e: AssertionError) {
      // Per capire chi prende il dito, se torna rossa: il percorso dell'hit test e gli strati del chip.
      runCatching { dumpTouchNodesAt("Anna") }.onFailure { println("TOCCO: diagnosi non riuscita, ${it::class.simpleName}") }
      throw e
    }
  }

  /** Per capire chi prende il dito: i nodi di layout sul centro di [name], con i loro modificatori, in ordine d'albero. */
  private fun dumpTouchNodesAt(name: String) {
    val target = (pills(name).singleOrNull() ?: compose.onAllNodesWithText(name).fetchSemanticsNodes().first()).boundsInRoot.center
    compose.runOnUiThread {
      val composeView = generateSequence(listOf<View>(compose.activity.window.decorView)) { level ->
        level.flatMap { v -> if (v is android.view.ViewGroup) (0 until v.childCount).map { v.getChildAt(it) } else emptyList() }
          .takeIf { it.isNotEmpty() }
      }.flatten().first { it.javaClass.simpleName == "AndroidComposeView" }
      val root = composeView.javaClass.getMethod("getRoot").invoke(composeView)!!
      fun children(node: Any): List<Any> {
        val m = node.javaClass.methods.firstOrNull { it.name == "getChildren\$ui_release" && it.parameterCount == 0 }
          ?: return emptyList()
        return (m.invoke(node) as? List<*>)?.filterNotNull().orEmpty()
      }
      fun walk(node: Any, depth: Int) {
        val info = node as androidx.compose.ui.layout.LayoutInfo
        val coords = info.coordinates
        val bounds = if (coords.isAttached) coords.boundsInRoot() else null
        val mods = info.getModifierInfo().map { it.modifier.toString().substringBefore('(').substringAfterLast('.').take(60) }
        if (bounds != null && bounds.contains(target) &&
          mods.any { "ointer" in it || "lick" in it || "ress" in it || "Gesture" in it || "Scroll" in it }) {
          println("TOCCO d=$depth $bounds ${mods.joinToString()}")
        }
        // Tutti i cliccabili vicini alla fila (anche quelli che non coprono il punto), con la semantica.
        if (bounds != null && mods.any { "Clickable" in it } && bounds.top < target.y + 200 && bounds.bottom > target.y - 200) {
          val label = (node as? androidx.compose.ui.layout.LayoutInfo)?.let { li ->
            runCatching { li.javaClass.getMethod("getCollapsedSemantics\$ui_release").invoke(li)?.toString()?.take(120) }.getOrNull()
          }
          println("CLIC d=$depth $bounds sem=$label")
        }
        for (child in children(node)) walk(child, depth + 1)
      }
      println("TOCCO: il centro di «$name» = $target")
      walk(root, 0)
      // Il percorso vero dell'hit test di Compose, sul centro di [name] e su quello di «Luca».
      for ((who, point) in listOf(name to target) +
        listOfNotNull(pills("Luca").singleOrNull()?.let { "Luca" to it.boundsInRoot.center })) {
        runCatching {
          val resultClass = Class.forName("androidx.compose.ui.node.HitTestResult")
          val result = resultClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
          val hit = root.javaClass.declaredMethods.first { it.name.startsWith("hitTest") && it.parameterTypes.size >= 3 &&
            it.parameterTypes[1] == resultClass }
          hit.isAccessible = true
          val packed = (point.x.toRawBits().toLong() shl 32) or (point.y.toRawBits().toLong() and 0xFFFFFFFFL)
          val args = arrayOfNulls<Any>(hit.parameterTypes.size)
          args[0] = packed; args[1] = result
          for (i in 2 until args.size) args[i] = when (hit.parameterTypes[i]) {
            Int::class.javaPrimitiveType -> 1; Boolean::class.javaPrimitiveType -> true; else -> null
          }
          hit.invoke(root, *args)
          val nodes = (result as List<*>).map { it?.javaClass?.name?.substringAfterLast('.') + "@" + System.identityHashCode(it) }
          println("PERCORSO $who $point: ${nodes.joinToString(" > ")}")
          // Tutti gli strati del percorso: misura, spostamento, ritaglio, e la misura del coordinatore.
          for (n in (result as List<*>).filterNotNull()) {
            val name = n.javaClass.simpleName
            if ("GraphicsLayer" !in name) continue
            runCatching {
              val co = n.javaClass.methods.first { it.name.startsWith("getCoordinator") && it.parameterCount == 0 }.invoke(n)!!
              val ly = co.javaClass.methods.firstOrNull { it.name.startsWith("getLayer") && it.parameterCount == 0 &&
                !it.name.contains("Block") && !it.name.contains("Positional") }?.invoke(co)
              val gf = ly?.javaClass?.declaredFields?.firstOrNull { it.type.name.endsWith("layer.GraphicsLayer") }?.apply { isAccessible = true }
              val g = gf?.get(ly) as? androidx.compose.ui.graphics.layer.GraphicsLayer
              val lc = co as androidx.compose.ui.layout.LayoutCoordinates
              println("STRATI $who $name@${System.identityHashCode(n)} coord=${lc.size} at=${if (lc.isAttached) lc.positionInRoot() else null} " +
                "layer=${g?.size} tx=${g?.translationX} ty=${g?.translationY} sx=${g?.scaleX} clip=${g?.clip} released=${g?.isReleased}")
            }.onFailure { println("STRATI $who $name: ${it::class.simpleName} ${it.message}") }
          }
          // La catena dei nodi dopo l'ultimo strato del percorso, fino al clic: coordinatore, strato, ritaglio, contorno.
          run {
            val pill = pills(who).singleOrNull()
            val chipLayout = pill?.layoutInfo
            val chain = chipLayout?.javaClass?.methods?.firstOrNull { it.name.startsWith("getNodes") && it.parameterCount == 0 }?.invoke(chipLayout)
            var n: Any? = chain?.javaClass?.methods?.firstOrNull { it.name.startsWith("getHead") && it.parameterCount == 0 }?.invoke(chain)
            var guard = 0
            while (n != null && guard++ < 25) {
              val co = runCatching { n.javaClass.methods.first { it.name.startsWith("getCoordinator") && it.parameterCount == 0 }.invoke(n) }.getOrNull()
              val ly = co?.let { c -> c.javaClass.methods.firstOrNull { it.name.startsWith("getLayer") && it.parameterCount == 0 &&
                !it.name.contains("Block") && !it.name.contains("Positional") }?.invoke(c) }
              val gf = ly?.javaClass?.declaredFields?.firstOrNull { it.type.name.endsWith("layer.GraphicsLayer") }?.apply { isAccessible = true }
              val g = gf?.get(ly) as? androidx.compose.ui.graphics.layer.GraphicsLayer
              val lc = co as? androidx.compose.ui.layout.LayoutCoordinates
              val local = runCatching { lc?.let { it.windowToLocal(point) } }.getOrNull()
              val outline = runCatching { g?.outline }.getOrNull()
              val ownSize = runCatching { ly!!.javaClass.getDeclaredField("size").apply { isAccessible = true }.getLong(ly).let { "${it shr 32}x${it and 0xFFFFFFFFL}" } }.getOrNull()
              val ownOutline = runCatching { ly!!.javaClass.getDeclaredField("outline").apply { isAccessible = true }.get(ly)?.let { o ->
                (o as? androidx.compose.ui.graphics.Outline)?.bounds } }.getOrNull()
              println("SEGUE $who ${n.javaClass.simpleName} strato=$ownSize contorno=$ownOutline gl=${runCatching { g?.outline?.bounds }.getOrNull()} " +
                "inLayer=${runCatching { ly?.javaClass?.methods?.first { it.name.startsWith("isInLayer") }?.invoke(ly, (local!!.x.toRawBits().toLong() shl 32) or (local.y.toRawBits().toLong() and 0xFFFFFFFFL)) }.getOrNull()}")
              n = n.javaClass.methods.firstOrNull { it.name.startsWith("getChild") && it.parameterCount == 0 }?.invoke(n)
            }
          }
          // Il livello dove il percorso si ferma: le sue proprietà (scala, spostamento, ritaglio, forma).
          val last = (result as List<*>).last()!!
          val coordinator = last.javaClass.methods.first { it.name.startsWith("getCoordinator") && it.parameterCount == 0 }
            .invoke(last)!!
          val props = coordinator.javaClass.methods.firstOrNull { it.name.startsWith("getLayerPositionalProperties") }
            ?.invoke(coordinator)
          val layer = coordinator.javaClass.methods.firstOrNull { it.name.startsWith("getLayer") && it.parameterCount == 0 &&
            !it.name.contains("Block") && !it.name.contains("Positional") }?.invoke(coordinator)
          val dump = props?.javaClass?.declaredFields?.joinToString { f -> f.isAccessible = true; "${f.name}=${f.get(props)}" }
          println("LIVELLO $who: coordinatore ${coordinator.javaClass.simpleName} ${coordinator.javaClass.methods.firstOrNull { it.name.startsWith("getSize") }?.let { runCatching { it.invoke(coordinator) }.getOrNull() }}, livello ${layer?.javaClass?.simpleName}, $dump")
          // Il livello vero (GraphicsLayerOwnerLayer → GraphicsLayer): spostamento, scala, ritaglio.
          runCatching {
            val f = layer!!.javaClass.declaredFields.first { it.type.name.endsWith("layer.GraphicsLayer") }
            f.isAccessible = true
            val g = f.get(layer) as androidx.compose.ui.graphics.layer.GraphicsLayer
            println("STRATO $who: tx=${g.translationX} ty=${g.translationY} sx=${g.scaleX} sy=${g.scaleY} clip=${g.clip} " +
              "size=${g.size} topLeft=${g.topLeft} alpha=${g.alpha} effect=${g.renderEffect}")
          }.onFailure { println("STRATO $who: ${it::class.simpleName} ${it.message} ${layer?.javaClass?.declaredFields?.map { f -> f.name + ":" + f.type.simpleName }}") }
          val layoutNode = coordinator.javaClass.methods.first { it.name.startsWith("getLayoutNode") && it.parameterCount == 0 }
            .invoke(coordinator) as androidx.compose.ui.layout.LayoutInfo
          for (info in layoutNode.getModifierInfo()) {
            val c = info.coordinates
            println("CATENA $who ${info.modifier.toString().substringBefore('(').substringAfterLast('.').take(40)} " +
              "size=${c.size} at=${if (c.isAttached) c.positionInRoot() else null}")
          }
          // Anche i modificatori del layout col blocco del livello: la scala e lo spostamento in uso.
          val block = last.javaClass.methods.firstOrNull { it.name.startsWith("getLayerBlock") }?.invoke(last)
          if (block != null) {
            val scope = Class.forName("androidx.compose.ui.graphics.ReusableGraphicsLayerScope").getDeclaredConstructor().newInstance()
            @Suppress("UNCHECKED_CAST")
            (block as (androidx.compose.ui.graphics.GraphicsLayerScope) -> Unit)(scope as androidx.compose.ui.graphics.GraphicsLayerScope)
            println("BLOCCO $who: scale=${scope.scaleX}x${scope.scaleY} tx=${scope.translationX} ty=${scope.translationY} " +
              "clip=${scope.clip} shape=${scope.shape} alpha=${scope.alpha} effect=${scope.renderEffect}")
          }
        }.onFailure { println("PERCORSO $who: ${it::class.simpleName} ${it.message} ${root.javaClass.declaredMethods.filter { m -> m.name.startsWith("hitTest") }.map { m -> m.name + m.parameterTypes.toList() }}") }
      }
    }
  }

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
