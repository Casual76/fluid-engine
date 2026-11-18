package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import dev.antigravity.fluidengine.ui.glass.field.trace.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

internal class FluidAdaptiveNavigationTest {
  @Test fun widthUsesTheExistingPaneBreakpoints() {
    for (sidebar in listOf(false, true)) {
      assertEquals(AdaptiveForm.Bottom, adaptiveForm(599.dp, sidebar))
      assertEquals(AdaptiveForm.Rail, adaptiveForm(600.dp, sidebar))
      assertEquals(AdaptiveForm.Rail, adaptiveForm(999.dp, sidebar))
      assertEquals(if (sidebar) AdaptiveForm.Sidebar else AdaptiveForm.Top, adaptiveForm(1000.dp, sidebar))
    }
  }

  @Test fun rowAndColumnShareTheirEndPointsEvenWhenTheRowOverflows() {
    for (count in listOf(1, 3, 20)) {
      val p = AdaptivePoses(1024f, 600f, count)
      assertEquals(512f, p.top.center.x, .001f)
      assertEquals(0f, p.inset(p.top), .001f)
      assertEquals(p.side.right + p.side.left, p.inset(p.side), .001f)
      assertEquals(p.inset(p.side), p.inset(p.bridge), .001f)
      assertEquals(0f, p.ink(p.bridge), .001f)
      assertEquals(1f, p.ink(p.side), .001f)
      val top = AdaptiveCells(p.top, p.unfold(p.top))
      val side = AdaptiveCells(p.side, p.unfold(p.side))
      assertEquals(0f, top.stepY, .001f)
      assertEquals(0f, side.stepX, .001f)
      assertEquals(48f, side.stepY, .001f)
    }
  }

  private val window = Rect(0f, 0f, 1024f, 600f)
  private val poses = AdaptivePoses(window.width, window.height, 3)

  private fun leg(a: Rect, b: Rect, spec: TweenSpec<Float>): GlassPlaneJourney {
    fun state(r: Rect): GlassPlaneState {
      val radius = if (r == poses.side) FluidRadius.Group.value else r.height / 2
      return GlassPlaneState(listOf(GlassPlanePiece("navigation", r, FluidCornerRadii.all(radius), id = "navigation")), 0f, window)
    }
    return GlassPlaneJourney(state(a), state(b), GlassPlaneMotion(spec = spec))
  }

  @Test fun thePageReachesTheEdgeBeforeTheRowsOpenAndLeavesAfterTheyFold() {
    for (open in listOf(true, false)) {
      val a = leg(poses.end(!open), poses.bridge, if (open) AdaptiveSlide else AdaptiveFold)
      val b = leg(poses.bridge, poses.end(open), if (open) AdaptiveUnfold else AdaptiveSlide)
      val midA = a.frame(160.0).pieces.single().bounds
      val midB = b.frame(160.0).pieces.single().bounds
      assertTrue(poses.unfold(if (open) midB else midA) in .01f.. .99f)
      assertTrue(poses.slide(if (open) midA else midB) in .01f.. .99f)
      assertEquals(1f, poses.slide(if (open) midB else midA), .001f)
      assertEquals(0f, poses.unfold(if (open) midA else midB), .001f)
      assertEquals(a.frame(1000.0).pieces.single().bounds, b.frame(0.0).pieces.single().bounds)
    }
  }

  @Test fun exportBothTabletTrips() {
    val dir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4B4_DIR")) ?: return
    for (open in listOf(true, false)) {
      val first = if (open) AdaptiveSlide else AdaptiveFold
      val a = leg(poses.end(!open), poses.bridge, first)
      val b = leg(poses.bridge, poses.end(open), if (open) AdaptiveUnfold else AdaptiveSlide)
      val samples = (-3..57).map { i ->
        val ms = i * 1000.0 / 60
        val flight = if (ms <= first.durationMillis) a.frame(ms) else b.frame(ms - first.durationMillis)
        val pane = flight.pieces.single()
        val r = pane.bounds
        val u = poses.unfold(r)
        val cells = AdaptiveCells(r, u)
        val clarity = poses.ink(r)
        fun shape(name: String, x: Float, y: Float, width: Float, height: Float, color: String, radius: Int = 0) =
          mapOf("name" to name, "kind" to "shape", "x" to x, "y" to y, "w" to width, "h" to height, "color" to color, "radius" to radius)
        val inset = poses.inset(r)
        val page = mutableListOf<Map<String, Any?>>()
        val elements = mutableListOf<Map<String, Any?>>()
        val column = (window.width - inset - 48f) / 3
        listOf("#37b4aa", "#ac79be", "#ed9257").forEachIndexed { index, color ->
          // Copia specchiata delle due file: il banco approssima la sfocatura dell'estensione
          // con la cattura del materiale. Sul dispositivo il layer riflesso ha anche la sua sfocatura.
          val mirrorX = inset - 12 - index * (column + 12) - column
          page += shape("riflesso-alto-$index", mirrorX, 108f, column, 236f, color, 14)
          page += shape("riflesso-basso-$index", mirrorX, 366f, column, 158f, color, 14)
          page += shape("copertina-$index", inset + 12 + index * (column + 12), 108f, column, 236f, color, 14)
          page += shape("album-$index", inset + 12 + index * (column + 12), 366f, column, 158f, color, 14)
        }
        val glass = liquidElement(mapOf("name" to "vetro", "x" to r.left, "y" to r.top, "w" to r.width,
          "h" to r.height, "radius" to pane.radii.topLeft), glassRecipe(GlassRole.Floating, r.width, r.height, false),
          null, "Sagoma F3, pagina a copertine schematiche")
        @Suppress("UNCHECKED_CAST")
        val body = (glass["body"] as Map<String, Any?>)["uniforms"] as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val light = ((glass["light"] as List<Map<String, Any?>>).first()["uniforms"] as Map<String, Any?>)
        light.filterKeys { it != "lightPart" }.forEach { (k, v) -> body.putIfAbsent(k, v) }
        body.putAll(mapOf("nodeClip" to listOf(0, "0dp"), "shadowOffset" to listOf("0dp", "0dp"),
          "shadowRadii" to listOf("0dp", "0dp"), "shadowAlphas" to listOf(0, 0), "shadowColor" to "#00000000"))
        fun icon(name: String, x: Float, y: Float, size: Float) = mapOf("kind" to "icon", "icon" to "fluid:$name",
          "sizeDp" to size, "x" to x, "y" to y, "color" to "#202023")
        val ink = mutableListOf<Map<String, Any?>>()
        listOf("Raccolta", "Serie", "Preferiti").forEachIndexed { index, title ->
          val x = cells.x + cells.stepX * index
          val y = cells.y + cells.stepY * index + 24f
          if (u > .01f) ink += icon(listOf("photo_library","grid_view","star")[index], x + 23, y, 26f * u)
          ink += mapOf("kind" to "text", "text" to title, "x" to (x + cells.width / 2 * (1-u) + 70 * u),
            "y" to y + 5, "align" to "center", "sizeDp" to 14, "color" to "#202023")
        }
        glass["content"] = listOf(mapOf("name" to "voci", "items" to ink, "alpha" to clarity, "blurDp" to 5 * (1-clarity)),
          mapOf("name" to "tasto", "alpha" to clarity, "blurDp" to 5 * (1-clarity), "items" to listOf(icon(if (u > 0f) "grid_view" else "menu",
            cells.toggleX + 24f, 28f, 26f))))
        glass["contentClip"] = "alpha"
        elements += glass
        // La selezione resta sulla seconda voce nelle due direzioni.
        elements += shape("selezione", r.left + cells.x + cells.stepX, r.top + cells.y + cells.stepY,
          cells.width, 48f, "#222222%02x".format((22*clarity).toInt()), 24)
        GlassTraceFrame(ms, elements) to page
      }
      val name = if (open) "tabbar-in-barra-laterale" else "barra-laterale-in-tabbar"
      val trace = GlassTrace(name, "f4b-4", 1f, "light", window.width, window.height, samples.map { it.first }, head = mapOf(
        "background" to mapOf("kind" to "color", "color" to "#f4f2ed"), "palette" to "engine",
        "probes" to listOf(mapOf("name" to "altezza", "kind" to "value", "path" to "vetro.h"),
          mapOf("name" to "posizione", "kind" to "value", "path" to "vetro.x"))
      ))
      // Il materiale campiona la pagina di questo fotogramma, compresa la sua estensione specchiata.
      val json = trace.toJson() + ("frames" to samples.map { (frame, page) ->
        mapOf("t" to frame.tMs, "page" to page, "elements" to frame.elements)
      })
      val file = File(dir, "$name/trace.json")
      file.parentFile.mkdirs()
      file.writeText(GlassTraceJson.encode(json))
    }
  }
}
