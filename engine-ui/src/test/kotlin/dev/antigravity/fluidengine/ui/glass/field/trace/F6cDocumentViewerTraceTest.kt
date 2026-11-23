package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.viewer.ViewerEntranceMotion
import dev.antigravity.fluidengine.ui.fluid.viewer.viewerDocumentDismissRelease
import dev.antigravity.fluidengine.ui.fluid.viewer.viewerUnanchoredOpacity
import dev.antigravity.fluidengine.ui.page.*
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Test

/** The host's real clock and figures with drawn sample plates. Compose input/pixels are tested separately. */
@OptIn(ExperimentalCoroutinesApi::class)
class F6cDocumentViewerTraceTest {
  private val width = 360f
  private val height = 760f
  private val full = Rect(0f, 0f, width, height)
  private val thumbnail = Rect(22f, 220f, 146f, 314f)
  private val fps = 120

  @Test fun documentJourneysAt120Hz() {
    val out = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6C10_DIR"))
    for (kind in listOf("pdf", "docx", "zip", "json", "drag-docx")) for (dark in listOf(false, true)) {
      val scene = record(kind, dark)
      repeat(2) { assertEquals(scene.frames.map { it.elements }, record(kind, dark).frames.map { it.elements }) }
      if (out != null) scene.write(File(out, "${scene.name}/trace.json"))
    }
  }

  @Test fun thumbnailOpenAndReturnAt120Hz() {
    val out = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6C21_DIR"))
    for (file in listOf("pdf", "docx", "zip", "json", "audio", "dng")) for (dark in listOf(false, true)) {
      val scene = record(file, dark, includeReturn = true)
      repeat(2) { assertEquals(scene.frames.map { it.elements }, record(file, dark, includeReturn = true).frames.map { it.elements }) }
      if (out != null) scene.write(File(out, "${scene.name}/trace.json"))
    }
  }

  @Test fun releaseKeepsTheHeldRectangleAndAReversedGestureReturnsToFull() {
    for (fraction in listOf(.25f, .50f, .75f)) for (cancel in listOf(false, true)) {
      journey { state, clock, _ ->
        state.onStack(listOf("files", "viewer"))
        state.pagePlaced("viewer")
        for (i in 1..100) clock.sendFrame(glassTraceTimeNanos(i, fps))
        val token = requireNotNull(state.beginVertical(null))
        state.moveVertical(token, fraction * height, 0f)
        val held = state.zoomShape()?.bounds
        assertNotNull(held)
        assertEquals((1f - fraction) * .18f, state.dimOn("files"), .05f)
        state.releaseVertical(token, 0f, cancelled = cancel)
        assertEquals(held, state.zoomShape()?.bounds)
        for (i in 101..220) clock.sendFrame(glassTraceTimeNanos(i, fps))
        assertFalse(state.isMoving)
        assertEquals(if (cancel) "viewer" else "files", state.upper)
      }
    }
  }

  @Test fun missingSourceAndZeroMotionHaveNoThumbnailGeometry() {
    for (reduced in listOf(false, true)) journey { state, clock, source ->
      state.reducedMotion = reduced
      if (!reduced) state.resolveSource = { null }
      state.onStack(listOf("files", "viewer"))
      assertNull(state.zoomShape())
      assertFalse(source.isHidden)
      state.pagePlaced("viewer")
      for (i in 1..100) clock.sendFrame(glassTraceTimeNanos(i, fps))
      state.onStack(listOf("files"))
      assertNull(state.zoomShape())
      for (i in 101..200) clock.sendFrame(glassTraceTimeNanos(i, fps))
      assertEquals("files", state.upper)
    }
    assertEquals(1f, viewerUnanchoredOpacity(0f), 0f)
    assertEquals(.5f, viewerUnanchoredOpacity(.5f), 0f)
    assertEquals(0f, viewerUnanchoredOpacity(1f), 0f)
  }

  @Test fun documentThresholdUsesDpEvenOnAFastRelease() {
    assertFalse(viewerDocumentDismissRelease(220f, 4800f, 2f))
    assertTrue(viewerDocumentDismissRelease(221f, 0f, 2f))
    assertFalse(viewerDocumentDismissRelease(250f, -4800f, 2f))
  }

  private fun <T> journey(block: (FluidPageHostState, BroadcastFrameClock, FluidTransitionSource) -> T): T {
    val clock = BroadcastFrameClock()
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    val source = FluidTransitionSource("file", FluidGlassNamespace())
    val state = FluidPageHostState().apply {
      this.scope = scope
      width = this@F6cDocumentViewerTraceTest.width
      height = this@F6cDocumentViewerTraceTest.height
      transitionOf = { FluidPageTransition.Expand }
      dismissOf = { FluidPageDismiss.Down }
      sourceOf = { if (it == "viewer") source else null }
      resolveSource = { FluidTransitionSourceGeometry(thumbnail, RectangleShape) }
      verticalDismissRelease["viewer"] = { travel, speed -> viewerDocumentDismissRelease(travel, speed, 1f) }
      onBack = { onStack(listOf("files")) }
      onStack(listOf("files"))
    }
    return try {
      block(state, clock, source)
    } finally {
      state.releaseSources()
      scope.cancel()
    }
  }

  private fun record(kind: String, dark: Boolean, includeReturn: Boolean = false): GlassTrace = journey { state, clock, source ->
    val drag = kind == "drag-docx"
    val file = kind.removePrefix("drag-")
    val frames = mutableListOf<GlassTraceFrame>()
    val readyAt = when (file) {
      "pdf" -> 600f
      "docx" -> 715f
      "zip" -> 250f
      else -> 160f
    }
    val start = 6
    val closeAt = 132
    val specialImage = file == "dng"
    var token: Int? = null
    if (drag) {
      state.onStack(listOf("files", "viewer"))
      state.pagePlaced("viewer")
      for (i in 1..100) clock.sendFrame(glassTraceTimeNanos(i, fps))
    }
    var lastEdge: Float? = null
    val last = if (includeReturn) closeAt + 78 else if (drag) 104 else 122
    for (i in 0..last) {
      if (!drag && i == start) {
        if (specialImage) state.ownPageBackground["viewer"] = Color.Black
        state.onStack(listOf("files", "viewer"))
        state.pagePlaced("viewer")
      }
      if (drag && i == 6) token = requireNotNull(state.beginVertical(null))
      if (drag && i in 7..30) state.moveVertical(requireNotNull(token), 7f, 0f)
      if (drag && i == 31) state.releaseVertical(requireNotNull(token), 0f)
      if (includeReturn && i == closeAt) {
        assertFalse(state.isMoving)
        assertEquals("viewer", state.upper)
        state.onStack(listOf("files"))
      }
      clock.sendFrame(glassTraceTimeNanos(i + 101, fps))
      val elements = mutableListOf<Map<String, Any?>>()
      val showing = state.upper == "viewer"
      if (showing && state.isMoving || !showing) {
        elements += mapOf("name" to "files")
        if (!source.isHidden) elements += mapOf("name" to "tile")
        val dim = state.dimOn("files")
        if (dim > 0f) elements += mapOf("name" to "veil", "color" to glassColor(Color.Black.copy(alpha = dim)), "_alpha" to dim)
      }
      if (showing) {
        val shape = state.zoomShape()
        val bounds = shape?.bounds ?: full
        val reveal = if (drag) 1f else (((i - start) * 1000f / fps - readyAt) /
          ViewerEntranceMotion.RevealMillis).coerceIn(0f, 1f)
        val faces = ViewerEntranceMotion.faces(reveal, 1f - state.upperExitFraction())
        val pageAlpha = faces.pageAlpha
        val tileAlpha = faces.sourceAlpha
        val backdrop = if (state.hasClosingBackdrop("viewer")) 0f else state.backdropArrival("viewer")
        assertEquals("Neither face covers the card in frame $i", 1f, maxOf(pageAlpha, tileAlpha), 0f)
        if (reveal == 0f) {
          assertEquals("The unprepared page must stay hidden in frame $i", 0f, pageAlpha, 0f)
          assertEquals("The thumbnail must stay in frame $i", 1f, tileAlpha, 0f)
          assertEquals("The waiting thumbnail must stay sharp in frame $i", 0f, faces.sourceBlur, 0f)
        }
        val sharp = GlassContentDefaults.PeakBlur * .7f
        assertFalse("Two readable faces in frame $i",
          pageAlpha > .25f && tileAlpha > .25f && faces.pageBlur < sharp && faces.sourceBlur < sharp)
        if (!drag && i < closeAt) {
          lastEdge?.let { assertTrue("Opening retreats in frame $i", bounds.right >= it - .05f) }
          lastEdge = bounds.right
        }
        val chrome = if (drag) state.backdropArrival("viewer") else
          minOf(backdrop, ((i - 6) * 1000f / fps / 350f).coerceIn(0f, 1f))
        val close = state.hasClosingBackdrop("viewer")
        if (close) assertEquals(0f, backdrop, 0f)
        val tileScale = maxOf(bounds.width / thumbnail.width, bounds.height / thumbnail.height)
        val contents = if (specialImage) {
          // The same image is on the shared layer for the whole trip. This is the illustrative plate,
          // with timing from the host and a fixed fitted image instead of a device layout.
          val fitted = Rect(0f, 279f, width, 481f)
          val image = if (shape == null) fitted else
            state.sharedImageBounds(thumbnail, fitted, state.fractionToward { it == state.upper }, shape)
          listOf(
            mapOf("name" to "page-face", "x" to (image.left - bounds.left), "y" to (image.top - bounds.top),
              "w" to image.width, "h" to image.height),
            mapOf("name" to "tile-face", "alpha" to 0f),
            mapOf("name" to "chrome", "alpha" to chrome),
          )
        } else listOf(
          mapOf("name" to "page-face", "alpha" to pageAlpha,
            "blurSigmaDp" to glassContentSigmaPx(faces.pageBlur, GlassContentDefaults.BlurReference.value)),
          mapOf("name" to "tile-face", "alpha" to tileAlpha,
            "blurSigmaDp" to glassContentSigmaPx(faces.sourceBlur, GlassContentDefaults.BlurReference.value),
            "x" to ((bounds.width - thumbnail.width) / 2f), "y" to ((bounds.height - thumbnail.height) / 2f),
            "w" to thumbnail.width, "h" to thumbnail.height, "scale" to tileScale),
          mapOf("name" to "chrome", "alpha" to chrome, "blurSigmaDp" to (1f - chrome) * 7f),
        )
        elements += rect("card", bounds) + mapOf(
          "radius" to (shape?.corners?.topLeft ?: 0f), "_backdrop" to backdrop, "_exit" to state.upperExitFraction(), "_ready" to (drag || reveal > 0f),
          "_moving" to state.isMoving, "content" to contents,
        )
      }
      frames += GlassTraceFrame(glassTraceTimeMs(i, fps), elements)
    }
    assertFalse(state.isMoving)
    assertEquals(if (drag || includeReturn) "files" else "viewer", state.upper)
    if (includeReturn) assertFalse(source.isHidden)
    val name = if (includeReturn) "visore-apre-e-torna-$file" else if (drag) "visore-chiude-dito-docx" else "visore-apre-$file"
    val phase = if (includeReturn) "f6c-21" else "f6c"
    val reference = if (includeReturn) mapOf("_openMs" to glassTraceTimeMs(start, fps),
      "_returnMs" to glassTraceTimeMs(closeAt, fps)) else emptyMap()
    GlassTrace("$name-${if (dark) "scuro" else "chiaro"}", phase, 1f, if (dark) "dark" else "light",
      width, height, frames, fps, head(file, dark, includeReturn) + reference)
  }

  private fun rect(name: String, rect: Rect) =
    mapOf("name" to name, "x" to rect.left, "y" to rect.top, "w" to rect.width, "h" to rect.height)

  private fun head(file: String, dark: Boolean, includeReturn: Boolean): Map<String, Any?> {
    val base = if (dark) "#242833" else "#EDEEF3"
    val card = if (file == "dng") "#000000" else if (dark) "#353A47" else "#FFFFFF"
    val ink = if (dark) "#E9EDF7" else "#1C2437"
    val chromeInk = if (file == "dng") "#E9EDF7" else ink
    fun text(label: String, x: Int, y: Int, size: Int, color: String = ink) =
      mapOf("kind" to "text", "text" to label, "x" to x, "y" to y, "sizeDp" to size, "color" to color)
    fun shape(name: String, bounds: Rect, color: String, content: List<Map<String, Any?>> = emptyList()) =
      rect(name, bounds) + mapOf("kind" to "shape", "radius" to 0, "color" to color, "content" to content)
    val rows = when (file) {
      "pdf" -> listOf("Quaderno di campo", "Prima pagina", "Strada verso il lago", "Note della mattina")
      "docx" -> listOf("Relazione", "Riepilogo della visita", "1. Osservazioni", "2. Misure")
      "zip" -> listOf("Allegati", "Appunti  /", "Dati  /", "Indice.txt")
      "audio" -> listOf("Campanelle", "Diario di bordo", "0:00                           0:28", "⏵")
      else -> listOf("Registro", "progetto: Taccuino di campo", "giornate: [ 2 ]", "rivisto: true")
    }
    fun image(path: String, x: Int, y: Int, w: Int, h: Int) = mapOf("kind" to "background",
      "background" to mapOf("kind" to "image", "path" to path), "x" to x, "y" to y, "w" to w, "h" to h)
    val plates = "tools/glassbench/traces/f6c-21/plates"
    val tile = when {
      file == "pdf" -> listOf(image("sample/src/main/res/drawable-nodpi/viewer_notes_cover.png", 29, 0, 66, 94))
      file == "audio" -> listOf(image("sample/src/main/res/drawable-nodpi/viewer_campanelle_cover.webp", 15, 0, 94, 94))
      file == "dng" -> listOf(image("$plates/dng.jpg", 0, 0, 124, 94))
      includeReturn -> listOf(image("$plates/$file-${if (dark) "scuro" else "chiaro"}.png", 20, 2, 84, 90))
      else -> listOf(text(file.uppercase(), 28, 35, 23))
    }
    val list = listOf(text("Visori", 22, 90, 34), text("File di esempio", 22, 154, 21),
      text("Documento a pagine", 164, 266, 17), text("Diario di bordo", 22, 386, 20),
      text("Archivio", 22, 470, 20), text("Altri file", 22, 554, 20))
    val page = if (file == "dng") listOf(image("$plates/dng.jpg", 0, 0, 360, 202))
    else rows.mapIndexed { i, label -> text(label, 32, 164 + i * 44, if (i == 0) 28 else 18) }
    return mapOf(
      "_confronto" to "F6D.0 §4 (profilo zoom), §5 (Quick Look JPEG). Tavole F6C.7 §3.",
      "_ripetizioni" to 3,
      "_sorgente" to "Sagoma e velo da FluidPageHostState, facce da ViewerEntranceMotion. Prontezza simulata, pagine illustrative.",
      "background" to mapOf("kind" to "color", "color" to base),
      "base" to mapOf(
        "files" to shape("files", full, base, listOf(mapOf("name" to "labels", "items" to list))),
        "tile" to shape("tile", thumbnail, card, listOf(mapOf("name" to "tile-label", "items" to tile))),
        "veil" to shape("veil", full, "#00000000"),
        "card" to (shape("card", full, card, listOf(
          mapOf("name" to "page-face", "items" to page),
          mapOf("name" to "tile-face", "items" to tile),
          mapOf("name" to "chrome", "items" to listOf(
            text("‹      $file", 24, 41, 22, chromeInk), text("Condividi", 232, 718, 19, chromeInk))),
        )) + ("contentClip" to "shape")),
      ),
    )
  }
}
