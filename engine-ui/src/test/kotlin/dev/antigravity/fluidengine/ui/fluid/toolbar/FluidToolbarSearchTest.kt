package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.glass.field.*
import dev.antigravity.fluidengine.ui.glass.field.trace.*
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import java.io.File
import org.junit.Assert.*
import org.junit.Test

internal class FluidToolbarSearchTest {
  private val spec = FluidSearchSpec("", {})
  private val metrics = fluidBottomBarMetrics()
  private val tools = FluidToolbarModel(List(3) {
    FluidToolbarItem(it, placement = FluidToolbarPlacement.BottomBar)
  })
  private fun pose(active: Boolean, model: FluidToolbarModel = tools, width: Float = 400f) =
    ToolbarSearch(spec.copy(active = active), FluidToolbarBar.Bottom, true).layout(model, width, metrics)

  @Test fun placementFollowsTheWindowAndExplicitPlacementWins() {
    assertEquals(FluidToolbarBar.Bottom, searchBar(FluidSearchPlacement.Automatic, 599.dp))
    assertEquals(FluidToolbarBar.Top, searchBar(FluidSearchPlacement.Automatic, 600.dp))
    assertEquals(FluidToolbarBar.Top, searchBar(FluidSearchPlacement.TopBar, 320.dp))
    assertEquals(FluidToolbarBar.Bottom, searchBar(FluidSearchPlacement.BottomBar, 960.dp))
    assertNull(searchBar(FluidSearchPlacement.Principal, 320.dp))
  }

  @Test fun automaticCountsNeighboursInTheSameBarAndKeepsTheirCommands() {
    assertTrue(pose(false).compact)
    assertEquals(46f, pose(false).width, 0f)
    assertFalse(pose(false, FluidToolbarModel(tools.items.take(2))).compact)
    val open = pose(true)
    assertFalse(open.compact)
    assertTrue(open.width > pose(false).width)
    assertEquals(tools.items.map { it.id }, open.neighbours.overflow.map { it.id })
    assertTrue(open.neighbours.groups.all { it.leftDp + it.widthDp < open.left })
  }

  @Test fun pinnedStaysOutsideUntilActivationAndPlacementOverridesTravel() {
    val top = ToolbarSearch(spec.copy(behavior = FluidSearchBehavior.CollapseToButton), FluidToolbarBar.Top, true)
    val model = FluidToolbarModel(listOf(FluidToolbarItem("pin", placement = FluidToolbarPlacement.PinnedTrailing)))
    val rest = top.layout(model, 380f, fluidToolbarMetrics())
    assertTrue(rest.left + rest.width < rest.neighbours.element("pin")!!.leftDp)
    val open = top.copy(spec = top.spec.copy(active = true))
    assertFalse(open.occupiesBottom)
    assertEquals("pin", open.layout(model, 380f, fluidToolbarMetrics()).neighbours.overflow.single().id)
    assertTrue(open.copy(spec = open.spec.copy(expansion = FluidSearchExpansion.AboveKeyboard)).occupiesBottom)
    for (width in listOf(80f, 200f, 320f, 960f)) {
      val pose = open.layout(model, width, fluidToolbarMetrics())
      assertTrue(pose.left >= 0 && pose.left + pose.width <= width)
    }
  }

  @Test fun lateKeyboardAndRotationChangeTheRemainingTravel() {
    // Prima dell'IME il campo arriva in fondo. Con gli inset successivi segue esattamente il bordo.
    val row = 48
    for (available in listOf(800, 760, 620, 500, 620, 800, 380)) {
      assertEquals(available, toolbarSearchOffset(available, row, 1f) + row)
      assertEquals(0, toolbarSearchOffset(available, row, 0f))
      assertEquals((available - row) / 2, toolbarSearchOffset(available, row, .5f))
    }
    assertEquals(0, toolbarSearchOffset(20, row, .75f))
    // Stesso bordo fisico a y=864 anche se Android ha già traslato la radice o ridimensionato il pannello.
    assertEquals(808, searchImeHeight(1500, 48, 864, 8))
    assertEquals(1280, searchImeHeight(1500, -424, 864, 8))
    assertEquals(600, searchImeHeight(600, 48, 864, 8))
    assertEquals(0, searchImeHeight(600, 940, 864, 8))
  }

  private fun state(pose: ToolbarSearchPose): GlassPlaneState {
    fun piece(id: String, x: Float, w: Float, h: Float): GlassPlanePiece {
      val rect = Rect(x, 48f - h / 2, x + w, 48f + h / 2)
      return GlassPlanePiece(id, rect, FluidCornerRadii.all(h / 2), id = id)
    }
    val field = piece("campo", pose.left, pose.width,
      if (pose.compact) FluidMetrics.GlassButtonLens.value else FluidTextFieldDefaults.MinHeight.value)
    val group = pose.neighbours.groups.single()
    return GlassPlaneState(listOf(field, piece("vicini", group.leftDp, group.widthDp, metrics.buttonWidthDp)),
      FluidGlassContainerDefaults.Spacing.value, Rect(0f, 0f, 400f, 96f))
  }

  @Test fun interruptionRestartsAtTheVisibleBounds() {
    val a = state(pose(false))
    val trip = GlassPlaneJourney(a, state(pose(true)), GlassPlaneMotion(spec = ToolbarSearchTiming))
    for (at in listOf(65.0, 150.0)) {
      val before = trip.frame(at).pieces.first { it.piece.key == "campo" }.bounds
      assertTrue(before.width > pose(false).width && before.width < pose(true).width)
      val back = trip.retarget(at, a)
      assertEquals(before, back.frame(0.0).pieces.first { it.piece.key == "campo" }.bounds)
      assertEquals(pose(false).width, back.frame(1000.0).pieces.first { it.piece.key == "campo" }.bounds.width, .01f)
    }
  }

  @Test fun onlySearchActivationAndDeactivationGiveTheGlyphsACommonClock() {
    val idle = pose(false).neighbours
    val active = pose(true).neighbours
    val memory = FluidToolbarMemory()
    memory.update(idle, FluidToolbarMotion.Full, searchActive = false)
    memory.update(active, FluidToolbarMotion.Full, searchActive = true)
    assertEquals(setOf(FluidToolbarOverflowId), memory.searchArrivals)
    assertEquals(3, memory.ghosts.count { it.searchSwap })
    memory.update(idle, FluidToolbarMotion.Full, searchActive = false)
    assertEquals(setOf<Any>(0, 1, 2), memory.searchArrivals)
    assertEquals(FluidToolbarOverflowId, memory.ghosts.single { it.searchSwap }.element.item.id)

    val regular = FluidToolbarMemory()
    regular.update(idle, FluidToolbarMotion.Full)
    regular.update(active, FluidToolbarMotion.Full)
    assertTrue(regular.searchArrivals.isEmpty())
    assertTrue(regular.ghosts.none { it.searchSwap })
  }

  @Test fun commandInkBlursImmediatelyAndNeverOverlapsOverflowInkInEitherDirection() {
    assertEquals(1f, toolbarSearchInkAlpha(0f, false), 0f)
    assertEquals(0f, toolbarSearchInkBlur(0f, false), 0f)
    assertTrue(toolbarSearchInkBlur((1000f / 60) / ToolbarSearchInkMillis, false) > .4f)
    for (millis in 0..ToolbarSearchInkMillis) {
      val phase = millis.toFloat() / ToolbarSearchInkMillis
      assertEquals(0f, toolbarSearchInkAlpha(phase, false) * toolbarSearchInkAlpha(phase, true), 0f)
      if (millis <= 167) assertEquals(0f, toolbarSearchInkAlpha(phase, true), 0f)
    }
    assertEquals(1f, toolbarSearchInkAlpha(1f, true), 0f)
    assertEquals(0f, toolbarSearchInkBlur(1f, true), 0f)
  }

  /** La traccia prende pose, curva F3, inchiostro R7 e ricetta dal codice. I glifi sono esemplificativi. */
  @Test fun exportSearch() {
    val dir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4B2_DIR")) ?: return
    for (dark in listOf(false, true)) {
      val restPose = pose(false)
      val openPose = pose(true)
      val closed = state(restPose)
      val open = state(openPose)
      val motion = GlassPlaneMotion(spec = ToolbarSearchTiming)
      val forward = GlassPlaneJourney(closed, open, motion)
      val back = GlassPlaneJourney(open, closed, motion)
      val ink = if (dark) FluidGlassInkOnDark else FluidGlassInkOnLight
      val color = glassColor(ink).take(7)
      val frames = (-2..51).map { index ->
        val t = glassTraceTimeMs(index)
        val closing = t >= 450
        val time = if (closing) t - 450 else t
        val flight = (if (closing) back else forward).frame(time)
        val elements = flight.pieces.map { fly ->
          val r = fly.bounds
          val field = fly.piece.key == "campo"
          val shape = mapOf("name" to fly.piece.key, "x" to r.left, "y" to r.top, "w" to r.width,
            "h" to r.height, "radius" to "capsule")
          val element = liquidElement(shape, glassRecipe(GlassRole.Floating, r.width, r.height, dark), null, "Ricerca F4B.2")
          val role = if (closing) GlassContentRole.Exit else GlassContentRole.Born
          val u = (time.toFloat() / GlassContentDefaults.DurationMs).coerceAtLeast(0f)
          fun icon(name: String, x: Float) = mapOf("kind" to "icon", "icon" to "fluid:$name",
            "x" to x, "y" to r.height / 2, "sizeDp" to 22, "color" to color)
          val covered = ((r.width - restPose.width) / (openPose.width - restPose.width)).coerceIn(0f, 1f)
          val veil = fluidSearchReadingVeil(ink)
          val contents = if (field) listOf(
            mapOf("name" to "velo", "items" to listOf(mapOf("kind" to "rect", "x" to 0, "y" to 0, "w" to r.width,
              "h" to r.height, "radius" to "capsule", "color" to glassColor(veil.copy(alpha = veil.alpha * covered))))),
            mapOf("name" to "lente", "items" to listOf(icon("search", 22f))),
            mapOf("name" to "editor", "alpha" to GlassContentCurves.alpha(role, u, true),
              "scale" to GlassContentCurves.scale(role, u), "blurDp" to GlassContentCurves.blur(role, u) * GlassContentDefaults.PeakBlur *
                GlassContentDefaults.BlurReference.value * GlassContentDefaults.GlyphFraction,
              "items" to listOf(mapOf("kind" to "text", "text" to "Cerca nelle foto", "sizeDp" to 16,
                "x" to 48, "y" to r.height / 2 + 6, "color" to color), icon("close", r.width - 20))),
          ) else {
            val elapsed = (time.toFloat() / ToolbarSearchInkMillis).coerceIn(0f, 1f)
            val phase = if (closing) 1f - elapsed else elapsed
            fun layer(name: String, overflow: Boolean, icons: List<Map<String, Any>>) = mapOf(
              "name" to name, "alpha" to toolbarSearchInkAlpha(phase, overflow),
              "blurDp" to glassContentSigmaPx(toolbarSearchInkBlur(phase, overflow) * GlassContentDefaults.PeakBlur,
                fluidToolbarIconSize(FluidToolbarRole.Overflow).value),
              "items" to icons,
            )
            val x = restPose.neighbours.groups.single().leftDp - r.left
            listOf(layer("comandi", false, listOf(icon("favorite", x + 24), icon("ios_share", x + 72), icon("add", x + 120))),
              layer("altro", true, listOf(icon("more_horiz", r.width / 2))))
          }
          @Suppress("UNCHECKED_CAST")
          val body = (element.getValue("body") as Map<String, Any?>).getValue("uniforms") as MutableMap<String, Any?>
          @Suppress("UNCHECKED_CAST")
          val light = (element.getValue("light") as List<Map<String, Any?>>).first().getValue("uniforms") as Map<String, Any?>
          light.forEach { (key, value) -> if (key != "lightPart") body.putIfAbsent(key, value) }
          body["nodeClip"] = listOf(0, "0dp")
          body["shadowOffset"] = listOf("0dp", "0dp")
          body["shadowRadii"] = listOf("0dp", "0dp")
          body["shadowAlphas"] = listOf(0, 0)
          body["shadowColor"] = "#00000000"
          (element - "items") + ("contentClip" to "shape") + ("content" to contents)
        }
        GlassTraceFrame(t, elements)
      }
      val theme = if (dark) "dark" else "light"
      val name = if (dark) "ricerca-apre-scuro" else "ricerca-apre"
      GlassTrace(name, "f4b-2", 2f, theme, 400f, 96f, frames, head = mapOf(
        "palette" to "engine", "background" to mapOf("kind" to "synthetic", "scaleDp" to 64, "detail" to .45,
          "palette" to if (dark) listOf("#16283C", "#5A3145", "#1F423E") else listOf("#E6D0A0", "#AFC9D7", "#F7EEDD")),
        "events" to listOf(mapOf("t" to 0, "kind" to "mark", "label" to "apre"),
          mapOf("t" to 450, "kind" to "mark", "label" to "chiude")),
        "probes" to listOf("larghezza" to "campo.w", "altezza" to "campo.h", "sinistra" to "campo.x",
          "testo" to "campo.content[editor].alpha").map { (label, path) ->
          mapOf("name" to label, "kind" to "value", "path" to path)
        },
      )).write(File(dir, "$name/trace.json"))
    }
  }
}
