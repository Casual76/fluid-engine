package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import dev.antigravity.fluidengine.ui.fluid.toolbar.*
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidElement
import java.io.File
import org.junit.Assert.*
import org.junit.Test

internal class FluidFloatingTabBarSelectionTest {
  private val gallery = FluidSelectionActions.gallery({}, {}, {}, {}, {}, {})
  private val chosen = FluidSelectionState<Int>().apply { select(3) }
  private fun declaration(actions: FluidToolbarScope.() -> Unit) = buildFluidToolbar(null) {
    selection(chosen, 1, null, {}, actions = actions)
  }
  private fun seats(actions: FluidToolbarScope.() -> Unit, two: Boolean, width: Float = 384f): TabSelectionSlots {
    val resolved = resolveFluidToolbar(fluidToolbarModel(declaration(actions), { 0f }), width,
      metrics = fluidBottomBarMetrics(), bar = FluidToolbarBar.Bottom)
    return TabSelectionSlots(resolved, two)
  }
  private val original = Rect(16f, 18f, 322f, 80f)
  private val circle = Rect(330f, 18f, 392f, 80f)
  private val phase = mutableStateOf(0f)
  private fun stage(slots: TabSelectionSlots) = TabSelectionStage(phase).apply {
    sources[TabBarPiece.Group] = original
    sources[TabBarPiece.Standalone] = circle
    host = Rect(0f, 0f, 408f, 80f)
    mainWidth = slots.mainWidth
    moreWidth = slots.more?.widthDp ?: 0f
    height = fluidBottomBarMetrics().buttonWidthDp
    gap = FluidFloatingTabBarDefaults.Spacing.value
  }

  @Test fun entranceAndReturnShareEveryPoseAndNeverShowBothFaces() {
    val layout = stage(seats(gallery, true))
    val outward = (0..120).map {
      phase.value = it / 120f
      assertTrue(tabSelectionLook(phase.value, false).alpha * tabSelectionLook(phase.value, true).alpha == 0f)
      layout.rect(TabBarPiece.Group)!!
    }
    assertEquals(original, outward.first())
    assertEquals(layout.mainWidth, outward.last().width, .01f)
    assertTrue(outward.zipWithNext().all { (a, b) -> a.width >= b.width && a.width - b.width < 4f })
    assertTrue(tabSelectionLook(.55f, true).blur > 0f)
    assertTrue(tabSelectionLook(.3f, false).blur > 0f)
    outward.indices.reversed().forEach { phase.value = it / 120f; assertEquals(outward[it], layout.rect(TabBarPiece.Group)) }
    assertEquals(circle, layout.rect(TabBarPiece.Standalone))
  }

  @Test fun prominentAndSearchBecomeMoreOrMergeWithTheCapsule() {
    val withMore = seats(gallery, true)
    assertEquals(FluidToolbarOverflowId, withMore.more!!.item.id)
    assertEquals(listOf("share", "favorite", "album", "delete"), withMore.main.map { it.item.id })
    assertEquals(FluidToolbarOverflowId, seats(gallery, false).main.last().item.id)
    phase.value = 1f
    val layout = stage(withMore)
    val r = layout.rect(TabBarPiece.Standalone)!!
    assertEquals(r.height, r.width, 0f)
    assertTrue(r.left >= layout.rect(TabBarPiece.Group)!!.right)
    layout.rtl = true
    assertTrue(layout.rect(TabBarPiece.Standalone)!!.right <= layout.rect(TabBarPiece.Group)!!.left)
    val merge = stage(seats(FluidSelectionActions.deleteOnly({}), true))
    val end = merge.rect(TabBarPiece.Standalone)!!
    assertTrue(end.isEmpty && merge.rect(TabBarPiece.Group)!!.contains(end.center))
    assertTrue(seats(gallery, true, 180f).mainWidth < withMore.mainWidth)
  }

  @Test fun backCancellationAndConfirmationUseTheElectedPage() {
    val chrome = FluidChromeController(0f, 1f)
    val pageBar = declaration(gallery)
    val state = FluidBottomReplacement().apply { declaration = pageBar; replacesTabBar = true }
    chrome.registerBackdrop("album", GlassBackdropState(emptyBackdrop(), null, 2.dp))
    chrome.setBottomReplacement("album", state)
    chrome.setBackdropFront("album", true)
    chrome.claimBottomReplacement("tabs", true)
    val dispatcher = NavigationEventDispatcher()
    val input = DirectNavigationEventInput()
    dispatcher.addInput(input)
    dispatcher.addHandler(FluidSelectionBackGesture { state.declaration!!.selection!!.exit(); state.replacesTabBar = false })
    for (confirm in listOf(false, true)) {
      chrome.holdBottomReplacement("host", true)
      val start = NavigationEvent(touchX = 1f, touchY = 320f, progress = 0f,
        swipeEdge = NavigationEvent.EDGE_LEFT, frameTimeMillis = 0L)
      input.backStarted(start)
      repeat(30) {
        input.backProgressed(NavigationEvent(touchX = it * 10f, touchY = 320f, progress = it / 30f,
          swipeEdge = NavigationEvent.EDGE_LEFT, frameTimeMillis = it * 8L))
        assertTrue(chrome.bottomReplacement.value!!.replacesTabBar)
      }
      if (confirm) input.backCompleted() else input.backCancelled()
      chrome.holdBottomReplacement("host", false)
      assertEquals(!confirm, chosen.isActive)
      assertEquals(!confirm, chrome.bottomReplacement.value!!.replacesTabBar)
    }
    chrome.claimBottomReplacement("tabs", false)
    assertFalse(chrome.bottomReplacementClaimed)
  }

  @Test fun railAndSidebarKeepTheListPaneAsOwner() {
    assertEquals(AdaptiveForm.Bottom, adaptiveForm(410.dp, false))
    assertEquals(AdaptiveForm.Rail, adaptiveForm(800.dp, true))
    assertEquals(AdaptiveForm.Top, adaptiveForm(1100.dp, false))
    assertEquals(AdaptiveForm.Sidebar, adaptiveForm(1100.dp, true))
  }

  /** Le sorgenti sono la posa della scena, le destinazioni e le facce vengono dal percorso Kotlin di produzione. */
  @Test fun exportEveryFrameAt120Hz() {
    val dir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B2_DIR")) ?: return
    val slots = seats(gallery, true)
    val layout = stage(slots)
    for (dark in listOf(false, true)) for (scene in listOf("entra", "esce", "back-confermato", "back-annullato")) {
      if (dark && scene.startsWith("back")) continue
      val name = "selezione-" + scene + if (dark) "-scuro" else ""
      val ink = if (dark) "#f1eef4" else "#29282c"
      val accent = if (dark) "#d8a7f5" else "#75388e"
      fun glyph(symbol: String, x: Float, y: Float, dp: Float, color: String = ink) = mapOf(
        "kind" to "icon", "icon" to "fluid:$symbol", "x" to x, "y" to y, "sizeDp" to dp, "color" to color)
      fun layer(name: String, actions: Boolean, items: List<Map<String, Any>>) : Map<String, Any> {
        val look = tabSelectionLook(phase.value, actions)
        return mapOf("name" to name, "alpha" to look.alpha, "scale" to look.scale,
          "blurSigmaDp" to glassContentSigmaPx(look.blur, FluidMetrics.BarIconSize.value) * look.scale, "items" to items)
      }
      fun pane(name: String, r: Rect, layers: List<Map<String, Any>>): Map<String, Any?> {
        val element = liquidElement(mapOf("name" to name, "x" to r.left, "y" to r.top, "w" to r.width,
          "h" to r.height, "radius" to "capsule"), glassRecipe(GlassRole.Floating, r.width, r.height, dark),
          null, "F6B.2, Floating")
        // Gli uniform spenti, impliciti nel banco fermo, sono obbligatori nel banco del movimento.
        @Suppress("UNCHECKED_CAST")
        val body = (element.getValue("body") as Map<String, Any?>).getValue("uniforms") as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val light = (element.getValue("light") as List<Map<String, Any?>>).first().getValue("uniforms") as Map<String, Any?>
        for ((k, v) in light) if (k != "lightPart") body.putIfAbsent(k, v)
        body["nodeClip"] = listOf(0, "0dp")
        body["shadowOffset"] = listOf("0dp", "0dp")
        body["shadowRadii"] = listOf("0dp", "0dp")
        body["shadowAlphas"] = listOf(0, 0)
        body["shadowColor"] = "#00000000"
        return element + mapOf("contentClip" to "shape", "content" to layers)
      }
      val back = scene.startsWith("back")
      val frames = (-3..if (back) 105 else 68).map { i ->
        val time = i * 1000.0 / 120
        val progress = ((time - if (back) 300 else 0) / FluidMotion.PlaneResizeMillis).toFloat().coerceIn(0f, 1f)
        phase.value = when (scene) { "entra" -> progress; "back-annullato" -> 1f; else -> 1f - progress }
        val pill = layout.rect(TabBarPiece.Group)!!
        val round = layout.rect(TabBarPiece.Standalone)!!
        val mx = pill.width / 2
        val my = pill.height / 2
        val nav = listOf("photo_library" to "Libreria", "grid_view" to "Album", "favorite" to "Preferiti")
        val cell = (original.width - 8) / nav.size
        val puck = mapOf("kind" to "rect", "x" to mx - cell / 2, "y" to my - 26, "w" to cell,
          "h" to 52, "radius" to "capsule", "color" to if (dark) "#0000003d" else "#00000017")
        val badge = listOf(mapOf("kind" to "circle", "x" to mx + 15, "y" to my - 15, "r" to 6, "color" to "#ba1a1a"),
          mapOf("kind" to "text", "text" to "3", "x" to mx + 15, "y" to my - 12, "sizeDp" to 8,
            "align" to "center", "color" to "#ffffff"))
        val tabs = listOf(puck) + nav.flatMapIndexed { j, (symbol, title) ->
          val x = mx + (j - 1) * (original.width - 8) / 3f
          val color = if (j == 1) accent else ink
          listOf(glyph(symbol, x, my - 7f, 28f, color),
            mapOf("kind" to "text", "text" to title, "x" to x, "y" to my + 14, "sizeDp" to 11,
              "align" to "center", "color" to color))
        } + badge
        var x = mx - slots.mainWidth / 2
        val actions = slots.main.map { el ->
          val symbol = mapOf("share" to "ios_share", "favorite" to "favorite", "album" to "photo_album",
              "delete" to "delete").getValue(el.item.id.toString())
          glyph(symbol, x + el.widthDp / 2, my, FluidMetrics.BarIconSize.value,
            if (el.item.id == "delete") (if (dark) "#ffb4ab" else "#ba1a1a") else ink).also { x += el.widthDp }
        }
        GlassTraceFrame(time, listOf(pane("pillola", pill, listOf(layer("schede", false, tabs), layer("azioni", true, actions))),
          pane("tondo", round, listOf(layer("cerca", false, listOf(glyph("search", round.width/2, round.height/2, 28f))),
            layer("altro", true, listOf(glyph("more_horiz", round.width/2, round.height/2, FluidMetrics.BarIconSize.value)))))))
      }
      val bg = if (dark) listOf("#252133", "#353043", "#272c38", "#201d2a")
        else listOf("#e8e1f1", "#f5ebde", "#d9e8e9", "#eee7f0")
      GlassTrace(name, "f6b-2", 2f, if (dark) "dark" else "light", 408f, 100f, frames, fps = 120,
        head = mapOf("background" to mapOf("kind" to "gradient4", "tl" to bg[0], "tr" to bg[1], "bl" to bg[2], "br" to bg[3]),
          "palette" to "engine")).write(File(dir, "$name/trace.json"))
    }
  }
}
