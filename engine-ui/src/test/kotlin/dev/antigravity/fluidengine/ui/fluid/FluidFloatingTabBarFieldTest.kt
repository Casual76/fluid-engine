package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.SpringSpec
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassPuckOptics
import dev.antigravity.fluidengine.ui.glass.field.glassPuckProfile
import dev.antigravity.fluidengine.ui.glass.field.glassPuckLensDepth
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceFrame
import dev.antigravity.fluidengine.ui.glass.field.trace.liquidElement
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneJourney
import androidx.compose.animation.core.TweenSpec
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FluidFloatingTabBarFieldTest {
  @Test fun prominentWinsRegardlessOfDeclarationOrderAndSearchIsStillReachable() {
    for (order in listOf(listOf(FluidTabRole.Prominent, FluidTabRole.Search), listOf(FluidTabRole.Search, FluidTabRole.Prominent))) {
      val scope = FluidFloatingTabBarScopeImpl()
      scope.tab("home", {}, {}, {})
      order.forEach { scope.standaloneTab(it, {}, it, {}) }
      assertEquals(FluidTabRole.Prominent, scope.standaloneTab?.key)
      assertEquals(listOf("home", FluidTabRole.Search), scope.tabs.map { it.key })
      assertEquals("home", scope.getInlineTab(FluidTabRole.Prominent)?.key)
      scope.previousKey = "home"
      assertEquals("home", scope.getInlineTab(FluidTabRole.Prominent)?.key)
    }
  }

  @Test fun searchAloneIsDetachedAndHistoricalActionStillUsesLastDeclaration() {
    val scope = FluidFloatingTabBarScopeImpl()
    scope.standaloneTab("one", {}, {})
    scope.standaloneTab("two", {}, {})
    assertEquals("two", scope.standaloneTab?.key)
    val navigation = FluidFloatingTabBarScopeImpl()
    navigation.standaloneTab("cerca", {}, FluidTabRole.Search, {})
    assertEquals("cerca", navigation.standaloneTab?.key)
    assertTrue(navigation.tabs.isEmpty())
  }

  @Test(expected = IllegalArgumentException::class) fun twoProminentDestinationsAreAmbiguous() {
    val scope = FluidFloatingTabBarScopeImpl()
    repeat(2) { scope.standaloneTab(it, {}, FluidTabRole.Prominent, {}) }
  }

  @Test fun mirroredDragUsesTheActualSlotAndClampsAtBothEnds() {
    for (index in 0..2) {
      val center = 4f + (index + 0.5f) * 80f
      assertEquals(index.toFloat(), tabBarSlot(center, 248f, 4f, 80f, 3, true), 0f)
      assertEquals(index.toFloat(), tabBarSlot(248f - center, 248f, 4f, 80f, 3, false), 0f)
    }
    assertEquals(0f, tabBarSlot(-20f, 248f, 4f, 80f, 3, true), 0f)
    assertEquals(2f, tabBarSlot(300f, 248f, 4f, 80f, 3, true), 0f)
  }

  private fun paneJourney(name: String, a: Rect, b: Rect, spec: TweenSpec<Float>): GlassPlaneJourney {
    fun pose(r: Rect) = GlassPlaneState(listOf(GlassPlanePiece(name, r, FluidCornerRadii.all(r.height / 2), id = name)),
      0f, Rect(0f, 0f, 432f, 164f))
    return GlassPlaneJourney(pose(a), pose(b), GlassPlaneMotion(spec = spec))
  }

  @Test fun appleFoldKeepsTheGlyphWhileOtherInkHasLeft() {
    val journey = paneJourney("gruppo", Rect(19f, 84f, 343f, 146f), Rect(19f, 94f, 71f, 146f), FluidMotion.FloatingTabFold)
    val widths = listOf(0.0, 33.37, 66.73, 100.10, 166.83, 233.57).map { journey.frame(it).pieces.single().bounds.width }
    listOf(324f, 310f, 183f, 134f, 68f, 52f).zip(widths).forEach { (expected, actual) -> assertEquals(expected, actual, 3f) }
    assertEquals(0f, tabBarLeavingAlpha(tabBarInkExtent(widths[2], 324f, 52f)), 0.001f)
    assertEquals(1f, tabBarLeavingAlpha(1f), 0f)
    assertEquals(1.4f, FluidMotion.tabLensScale(.96f, 1.4f), 0f)
  }

  @Test fun appleFoldKeepsTheSelectedTitleForOneMoreBeat() {
    val journey = paneJourney("gruppo", Rect(19f, 84f, 343f, 146f), Rect(19f, 94f, 71f, 146f), FluidMotion.FloatingTabFold)
    fun extent(ms: Double) = tabBarInkExtent(journey.frame(ms).pieces.single().bounds.width, 324f, 52f)
    assertEquals(0f, tabBarLeavingAlpha(extent(66.73)), .001f)
    assertEquals(1f, tabBarKeptTitleAlpha(extent(67.3)), .001f)
    val fading = tabBarKeptTitleAlpha(extent(83.9))
    assertTrue(fading in .01f.. .99f)
    val scale = maxOf(extent(83.9), fading)
    val glyphY = -7f * scale
    assertEquals(21f, tabBarKeptLabelOffset(14f, -7f, scale) - glyphY, .001f)
    assertEquals(0f, tabBarKeptTitleAlpha(extent(100.1)), .001f)
    assertEquals(1f, tabBarKeptTitleAlpha(1f), 0f)
    assertEquals(0f, tabBarKeptTitleAlpha(0f), 0f)
  }

  private fun motionPane(element: Map<String, Any?>, layers: List<Map<String, Any?>>): Map<String, Any?> {
    // Il banco del movimento richiede anche gli uniform spenti che il banco fermo lascia impliciti.
    @Suppress("UNCHECKED_CAST")
    val body = (element.getValue("body") as Map<String, Any?>).getValue("uniforms") as MutableMap<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val light = (element.getValue("light") as List<Map<String, Any?>>).first().getValue("uniforms") as Map<String, Any?>
    for ((key, value) in light) if (key != "lightPart") body.putIfAbsent(key, value)
    body["nodeClip"] = listOf(0, "0dp")
    body["shadowOffset"] = listOf("0dp", "0dp")
    body["shadowRadii"] = listOf("0dp", "0dp")
    body["shadowAlphas"] = listOf(0, 0)
    body["shadowColor"] = "#00000000"
    return (element - "items") + ("content" to layers)
  }

  @Test fun exportTabBarJourneys() {
    val directory = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4B3_DIR")) ?: return
    val ink = "#262529"
    val accent = "#811b88"
    fun glyph(name: String, x: Float, y: Float, color: String = ink) =
      mapOf("kind" to "icon", "icon" to "fluid:$name", "sizeDp" to 28, "x" to x, "y" to y, "color" to color)
    fun text(label: String, x: Float, y: Float, color: String = ink) = mapOf("kind" to "text", "text" to label,
      "sizeDp" to 11, "align" to "center", "x" to x, "y" to y, "color" to color)
    fun layer(name: String, items: List<Map<String, Any>>, alpha: Float = 1f, blur: Float = 6f * (1f - alpha)) =
      mapOf("name" to name, "items" to items, "alpha" to alpha, "blurDp" to blur)
    fun slab(name: String, r: Rect, layers: List<Map<String, Any?>>) = motionPane(liquidElement(
      mapOf("name" to name, "x" to r.left, "y" to r.top, "w" to r.width, "h" to r.height, "radius" to "capsule"),
      glassRecipe(GlassRole.Floating, r.width, r.height, false), null, "Campo F3 indipendente"),
      layers)
    for (search in listOf(false, true)) {
      val name = if (search) "tabbar-ricerca" else "tabbar-piega"
      val spec = if (search) FluidMotion.TabBarSearch else FluidMotion.FloatingTabFold
      val width = if (search) 214f else 324f
      val compact = if (search) 48f else 52f
      val group = paneJourney("gruppo", Rect(19f, 84f, 19 + width, 146f), Rect(19f, 146 - compact, 19 + compact, 146f), spec)
      val button = paneJourney("tondo", Rect(350f, 84f, 412f, 146f),
        if (search) Rect(74f, 98f, 412f, 146f) else Rect(360f, 94f, 412f, 146f), spec)
      val player = paneJourney("lettore", Rect(19f, 30f, 412f, 76f), Rect(78f, 94f, 353f, 146f), FluidMotion.TabBarPlayer)
      val tabs = if (search) listOf("photo_library" to "Raccolta", "grid_view" to "Album") else
        listOf("home" to "Home", "grid_view" to "Nuovi", "music_note" to "Musica", "photo_library" to "Raccolta")
      val chosen = if (search) 0 else tabs.lastIndex
      val frames = (-3..48).map { index ->
        val time = index * 1000.0 / 60
        val r = group.frame(time).pieces.single().bounds
        val extent = tabBarInkExtent(r.width, width, compact)
        val leaving = tabBarLeavingAlpha(extent)
        val groupInk = tabs.flatMapIndexed { i, item ->
          val x = r.width / 2 + tabBarInkOffset(((i + 0.5f) / tabs.size - 0.5f) * (width - 9.4f), extent)
          val color = if (i == chosen) accent else ink
          val keptTitle = i == chosen && !search
          val title = if (keptTitle) tabBarKeptTitleAlpha(extent) else leaving
          val yScale = if (keptTitle) maxOf(extent, title) else extent
          val labelY = if (keptTitle) tabBarKeptLabelOffset(14f, -7f, yScale) else 14 * extent
          listOf(layer("icona-$i", listOf(glyph(item.first, x, r.height / 2 - 7 * yScale, color)), if (i == chosen) 1f else leaving),
            layer("titolo-$i", listOf(text(item.second, x, r.height / 2 + labelY, color)), title,
              if (keptTitle) 0f else 6f * (1f - title)))
        }
        val elements = mutableListOf<Map<String, Any?>>()
        if (search) {
          val page = FluidMotion.FloatingTabSearchPage
          val wash = page.easing.transform((time.toFloat() / page.durationMillis).coerceIn(0f, 1f))
          val channel = (170 + 80 * wash).toInt()
          elements += mapOf("kind" to "shape", "name" to "pagina", "x" to 0, "y" to 0, "w" to 432, "h" to 164,
            "radius" to 0, "color" to "#%02x%02x%02x".format(channel, channel, channel))
        }
        elements += slab("gruppo", r, if (search) groupInk else emptyList())
        val c = button.frame(time).pieces.single().bounds
        elements += slab("tondo", c, listOf(layer("ricerca", listOf(glyph("search", if (search) 25f + 6 * extent else c.width / 2, c.height / 2)))))
        if (!search) {
          val a = player.frame(time).pieces.single().bounds
          elements += slab("lettore", a, listOf(layer("titolo", listOf(text("In ascolto", a.width / 2, a.height / 2)))))
          elements += mapOf("kind" to "shape", "name" to "inchiostro-in-volo", "x" to r.left, "y" to r.top,
            "w" to r.width, "h" to r.height, "color" to "#00000000", "content" to groupInk)
        }
        GlassTraceFrame(time, elements)
      }
      GlassTrace(name, "f4b-3", 2f, "light", 432f, 164f, frames, head = mapOf(
        "background" to mapOf("kind" to "color", "color" to "#eeeeee"), "palette" to "engine",
        "probes" to listOf(mapOf("name" to "larghezza", "kind" to "value", "path" to "gruppo.w")),
      )).write(File(directory, "$name/trace.json"))
    }
  }

  @Test fun exportSelectionAndPressure() {
    val directory = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4B3_DIR")) ?: return
    val height = 62f
    val inset = height * 0.076f
    val group = height * 5.23f
    val cell = (group - inset * 2) / 3
    val optics = GlassDefaults.optics(GlassRole.Floating).sanitized()
    val darkInk = "#1e1e20"
    val accent = "#811B88"
    fun tabs(left: Float, top: Float, color: String) =
      listOf("calendar_month" to "Oggi", "favorite" to "Preferiti", "search" to "Cerca").flatMapIndexed { i, item ->
        val x = left + cell * (i + 0.5f)
        listOf(mapOf("kind" to "icon", "icon" to "fluid:${item.first}", "x" to x, "y" to top + 19,
          "sizeDp" to 28, "color" to color),
          mapOf("kind" to "text", "text" to item.second, "x" to x, "y" to top + 44,
            "sizeDp" to 11, "align" to "center", "color" to color))
      }
    fun slab(name: String, x: Float, y: Float, w: Float, h: Float, items: List<Map<String, Any>>) =
      mapOf("name" to name, "x" to x, "y" to y, "w" to w, "h" to h, "radius" to "capsule", "items" to items)
    fun pane(shape: Map<String, Any>, width: Float) = liquidElement(shape,
      glassRecipe(GlassRole.Floating, width, height, false, optics = optics), null, "F4B.3, pannello Floating")
    val bar = pane(slab("gruppo", 19f, 20f, group, height, tabs(inset, inset, darkInk)), group)
    val circle = pane(slab("prominente", 19f + group + height * 0.115f, 20f, height, height, listOf(
      mapOf("kind" to "icon", "icon" to "fluid:star", "x" to 31, "y" to 31, "sizeDp" to 28, "color" to darkInk)
    )), height)
    fun SpringSpec<Float>.sample(t: Double, start: Float, end: Float): Float =
      FloatSpringSpec(dampingRatio, stiffness, visibilityThreshold ?: 0.001f)
        .getValueFromNanos((t.coerceAtLeast(0.0) * 1_000_000).toLong(), start, end, 0f)
    for (move in listOf(false, true)) {
      val name = if (move) "bolla-cambio" else "bolla-pressione"
      val frames = (-3..45).map { index ->
        val time = index * 1000.0 / 60
        val press = if (time <= 300) FluidMotion.DropPress.sample(time, 0f, 1f)
          else FluidMotion.DropPress.sample(time - 300, FluidMotion.DropPress.sample(300.0, 0f, 1f), 0f)
        val pos = if (move) FluidMotion.dropValue(0.001f).sample(time, 0f, 1f) else 0f
        val scale = FluidMotion.tabLensScale(press, 78f / 56f)
        val w = cell * scale
        val h = (height - 2 * inset) * scale
        val x = 19 + inset + cell * (pos + 0.5f) - w / 2
        val y = 20 + (height - h) / 2
        val puck = liquidElement(slab("bolla", x, y, w, h, tabs(19 + inset - x, 20 + inset - y, accent)),
          glassRecipe(GlassRole.Floating, w, h, false, optics = glassPuckOptics(optics), pressed = press,
            opticalDepth = glassPuckLensDepth(press), profile = glassPuckProfile(height)), null,
          "Profilo del componente; pressione tenuta 300 ms. Il banco approssima la lettura dei glifi senza dispersione.")
        val layers = listOf(bar, circle, puck).map { element ->
          motionPane(element, listOf(mapOf("name" to "inchiostro", "items" to element.getValue("items"))))
        }
        GlassTraceFrame(time, layers)
      }
      GlassTrace(name, "f4b-3", 2f, "light", 432f, 104f, frames, head = mapOf(
        "background" to mapOf("kind" to "color", "color" to "#eeeeee"), "palette" to "engine",
        "probes" to listOf(mapOf("name" to "larghezza", "kind" to "value", "path" to "bolla.w"))
      )).write(File(directory, "$name/trace.json"))
    }
  }
}
