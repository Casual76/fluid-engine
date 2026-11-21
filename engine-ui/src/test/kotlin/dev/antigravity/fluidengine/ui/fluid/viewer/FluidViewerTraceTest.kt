package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidToolbarMinimizeLook
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.trace.*
import dev.antigravity.fluidengine.ui.page.FluidPageDismiss
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.FluidZoom
import dev.antigravity.fluidengine.ui.page.FluidZoomShape
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Test

/** Synthetic geometry from the real page machine, minimize curve and sheet spring, sampled at 120 Hz.
 * GLASS_F6C1_DIR exports the traces for glassmotion. The photographic plate is supplied with the session. */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidViewerTraceTest {
  private val width = 320f
  private val height = 568f
  private val thumb = Rect(20f, 121f, 149f, 207f)
  private val destination = Rect(0f, 177.33f, width, 390.67f)
  private val full = FluidZoomShape(Rect(0f, 0f, width, height))
  private val fps = 120
  private val plate = "artifacts/liquid-glass/sessioni/f6c-1/tracce/dune.png"

  @Test fun exportFourViewerJourneysWithBothAppearances() {
    for (dark in listOf(false, true)) {
      for (scene in listOf("visore-apre", "visore-barre-tocco", "visore-info-su", "visore-chiude-giu")) {
        val trace = makeTrace(scene, dark)
        assertEquals(fps, trace.fps)
        assertTrue(trace.frames.all { frame -> frame.elements.count { it["name"] == "foto" } == 1 })
        GlassTraceOutput.requestedDir(System.getenv("GLASS_F6C1_DIR"))?.let { trace.write(File(it, "${trace.name}/trace.json")) }
      }
    }
  }

  @Test fun chromeUsesDirectionalDurationsAndZeroMotionHasNoTravel() {
    assertEquals(200, ViewerChromeMotion.duration(true, 1f, 1f))
    assertEquals(350, ViewerChromeMotion.duration(false, 1f, 1f))
    assertEquals(175, ViewerChromeMotion.duration(false, .5f, 1f))
    assertEquals(0, ViewerChromeMotion.duration(false, 1f, 0f))
    val reduced = fluidToolbarMinimizeLook(.6f, 48f, reducedMotion = true, blurAvailable = true)
    assertEquals(0f, reduced.offsetPx, 0f)
    assertEquals(0f, reduced.blur, 0f)
  }

  @Test fun verticalReleaseUsesImageDistanceAndHonoursAReversal() {
    assertFalse(viewerDismissRelease(220f, 0f, 2f))
    assertTrue(viewerDismissRelease(221f, 0f, 2f))
    assertFalse(viewerDismissRelease(240f, -4000f, 2f))
    assertTrue(viewerDismissRelease(2f, 4000f, 2f))
  }

  @Test fun returningAfterPagingReleasesTheFirstThumbnailAndBorrowsTheCurrentOne() {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    val ns = FluidGlassNamespace()
    val first = FluidTransitionSource("one", ns)
    val second = FluidTransitionSource("two", ns)
    var target = first
    val pages = FluidPageHostState().also {
      it.scope = scope
      it.width = width
      it.height = height
      it.density = 1f
      it.transitionOf = { FluidPageTransition.Expand }
      it.dismissOf = { FluidPageDismiss.Down }
      it.sourceOf = { target }
      it.resolveSource = { source -> FluidTransitionSourceGeometry(
        if (source.sameOrigin(first)) thumb else thumb.translate(140f, 0f), ContinuousCornerShape(FluidRadius.Card)) }
    }
    try {
      pages.onStack(listOf("grid"))
      pages.onStack(listOf("grid", "file"))
      pages.pagePlaced("file")
      repeat(160) { clock.sendFrame(glassTraceTimeNanos(it, fps)) }
      assertTrue(first.isHidden)
      target = second
      assertNotNull(pages.beginVertical(null))
      assertFalse(first.isHidden)
      assertTrue(second.isHidden)
    } finally {
      pages.releaseSources()
      scope.cancel()
    }
    assertFalse(second.isHidden)
  }

  @Test fun blackBelongsToImagesAndVideoAndFileKindReusesEngineRules() {
    val session = object : FluidMediaSession {
      override val playing = false
      override val positionMillis = 0L
      override val durationMillis: Long? = null
      override val canSeek = false
      override fun play() {}
      override fun pause() {}
      override fun seekTo(positionMillis: Long) {}
    }
    assertTrue(FluidViewerContent.Image {}.blackBackground)
    assertTrue(FluidViewerContent.Media(session, videoSurface = {}).blackBackground)
    assertFalse(FluidViewerContent.Media(session, artwork = {}).blackBackground)
    val file = FluidFileInfo("foto.png", "application/x-executable")
    assertEquals(FluidFileKind.Executable, file.kind)
    assertFalse(FluidViewerContent.Unknown(file).blackBackground)
  }

  private fun makeTrace(scene: String, dark: Boolean): GlassTrace {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    val source = FluidTransitionSource("dune", FluidGlassNamespace())
    val state = FluidPageHostState().also {
      it.scope = scope
      it.width = width
      it.height = height
      it.density = 1f
      it.transitionOf = { FluidPageTransition.Expand }
      it.dismissOf = { FluidPageDismiss.Down }
      it.verticalDismissRelease["file"] = { travel, speed -> viewerDismissRelease(travel, speed, 1f) }
      it.sourceOf = { source }
      it.resolveSource = { FluidTransitionSourceGeometry(thumb, ContinuousCornerShape(FluidRadius.Card)) }
      it.onBack = { it.onStack(listOf("album")) }
      it.onStack(listOf("album"))
    }
    var ticks = 0
    fun tick() = clock.sendFrame(glassTraceTimeNanos(ticks++, fps))
    fun open() { state.onStack(listOf("album", "file")); state.pagePlaced("file") }
    if (scene != "visore-apre") { open(); repeat(160) { tick() } }
    val medium = fluidSheetStops(listOf(FluidSheetDetent.Medium, FluidSheetDetent.Large), height, 24f, 1f).lowest
    val sheet = TargetBasedAnimation(FluidMotion.PopoverOpenY, Float.VectorConverter, 0f, 1f)
    val base = mutableMapOf<String, Any?>()
    val frames = mutableListOf<GlassTraceFrame>()
    var token: Int? = null
    try {
      val last = if (scene == "visore-barre-tocco") 150 else 120
      for (n in 0..last) {
        if (scene == "visore-apre" && n == 9) open()
        if (scene == "visore-chiude-giu") {
          if (n == 9) {
            state.recordDown(Offset(151f, 240f), IntSize(width.toInt(), height.toInt()))
            token = requireNotNull(state.beginVertical(null))
          }
          if (n in 10..39) state.moveVertical(token!!, 8f, 960f)
          if (n == 40) state.releaseVertical(token!!, 960f)
        }
        tick()
        val ms = glassTraceTimeMs(n, fps)
        val p = state.fractionToward { it == "file" }
        val shape = state.zoomShape() ?: if (p > .5f) full else FluidZoomShape(thumb)
        val photo = FluidZoom.sharedBounds(thumb, destination, p, shape, FluidZoomShape(thumb), full)
        val elements = mutableListOf<Map<String, Any?>>()
        val page = if (dark) "#161619" else "#F6F5F2"
        val ink = if (dark) "#FFFFFF" else "#242328"
        elements += rectangle("album", full.bounds, page)
        elements += label("titolo-album", "Foto", 20f, 84f, 32f, ink)
        elements += label("nome-file", "Dune", 20f, 230f, 16f, ink)
        if (p > 0f) elements += rectangle("visore", shape.bounds, "#000000", shape.corners.topLeft)
        elements += rectangle("foto", photo, "#00000000").plus("content" to listOf(mapOf(
          "name" to "foto", "w" to photo.width, "h" to photo.height, "items" to listOf(mapOf(
            "kind" to "background", "x" to 0, "y" to 0, "w" to photo.width, "h" to photo.height,
            "radius" to (1f - p) * FluidRadius.Card.value,
            "background" to mapOf("kind" to "image", "path" to plate),
          )),
        )))
        val hidden = if (scene != "visore-barre-tocco") 0f else when {
          ms < 125 -> 0f
          ms < 650 -> ((ms - 125) / ViewerChromeMotion.HideMillis).toFloat().coerceIn(0f, 1f)
          else -> (1f - ((ms - 650) / ViewerChromeMotion.ShowMillis).toFloat()).coerceIn(0f, 1f)
        }
        val look = fluidToolbarMinimizeLook(hidden, 48f, false, true)
        val faces = FluidZoom.faces(p)
        val alpha = look.alpha * faces.pageAlpha
        if (alpha > .001f) {
          val y = 22f + look.offsetPx + faces.riseDp
          val bottom = height - 66f - look.offsetPx
          elements += glass(base, "indietro", Rect(12f,y,60f,y+48f), true, listOf("arrow_back_ios_new"), alpha, look.blur)
          elements += label("conteggio", "1 di 3", 135f, y + 30f, 17f, "#FFFFFF", alpha, look.blur)
          elements += glass(base, "strumenti", Rect(112f,bottom,208f,bottom+48f), true, listOf("info","edit"), alpha, look.blur)
        }
        if (scene == "visore-info-su" && n >= 24) {
          val opened = sheet.getValueFromNanos(glassTraceTimeNanos(n-24, fps)).coerceIn(0f, 1f)
          val top = height - medium * opened
          elements += glass(base, "pannello", Rect(8f, top, width-8f, top+medium), dark, emptyList(), opened, 0f)
          elements += label("info", "Informazioni", 29f, top+52f, 22f, ink)
          elements += label("info-nome", "Dune.jpg", 29f, top+100f, 18f, ink)
          elements += label("info-tipo", "Immagine JPEG", 29f, top+130f, 15f, ink)
        }
        frames += GlassTraceFrame(ms, elements)
      }
      assertEquals(scene != "visore-chiude-giu", state.upper == "file")
      val name = scene + if (dark) "-scuro" else ""
      return GlassTrace(name, "f6c-1", 1f, if (dark) "dark" else "light", width, height, frames, fps,
        mapOf("background" to mapOf("kind" to "color", "color" to "#000000"), "base" to base,
          "_" to "Scena di banco. Zoom, dito, elemento condiviso, ritiro R5 V5, molla e detent dal codice Fluid."))
    } finally {
      scope.cancel()
      state.releaseSources()
    }
  }

  private fun rectangle(name: String, box: Rect, color: String, radius: Float = 0f) = mapOf<String, Any?>(
    "kind" to "shape", "name" to name, "x" to box.left, "y" to box.top, "w" to box.width, "h" to box.height,
    "radius" to radius, "color" to color,
  )

  private fun label(name: String, value: String, x: Float, baseline: Float, size: Float, color: String,
    alpha: Float = 1f, blur: Float = 0f): Map<String, Any?> {
    val box = Rect(x, baseline-size*1.5f, width, baseline+size)
    return rectangle(name, box, "#00000000").plus("content" to listOf(mapOf("name" to name, "w" to box.width, "h" to box.height,
      "alpha" to alpha, "blurSigmaDp" to glassContentSigmaPx(blur, FluidMetrics.BarIconSize.value),
      "items" to listOf(mapOf("kind" to "text", "text" to value, "x" to 0, "y" to size*1.5f,
        "sizeDp" to size, "color" to color, "weight" to 600)))))
  }

  private fun glass(base: MutableMap<String, Any?>, name: String, box: Rect, dark: Boolean, icons: List<String>,
    alpha: Float, blur: Float): Map<String, Any?> {
    base.getOrPut(name) {
      val panel = name == "pannello"
      val recipe = glassRecipe(if (panel) GlassRole.Modal else GlassRole.Interactive, box.width, box.height, dark,
        optics = if (panel) GlassDefaults.panelOptics() else GlassDefaults.buttonOptics())
      liquidElement(mapOf("name" to name, "x" to 0, "y" to 0, "w" to box.width, "h" to box.height,
        "radius" to if (panel) FluidRadius.Sheet.value else box.height / 2), recipe, null, "Ricetta Fluid")
        .also { completeRestBody(it) }
    }
    val content = if (icons.isEmpty()) emptyList() else listOf(mapOf(
      "name" to name, "w" to box.width, "h" to box.height, "alpha" to alpha,
      "blurSigmaDp" to glassContentSigmaPx(blur, FluidMetrics.BarIconSize.value),
      "icons" to icons.map { "fluid:$it" }, "iconSizeDp" to FluidMetrics.BarIconSize.value, "color" to "#FFFFFF"))
    return mapOf("name" to name, "x" to box.left, "y" to box.top, "content" to content)
  }
}
