package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import dev.antigravity.fluidengine.ui.glass.field.trace.*
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FluidMediaPagesPresetTest {
  @Test fun seekCapabilityAndBoundsAreCheckedAtTheTimeOfAnAction() {
    var seek: Long? = null
    val session = object : FluidMediaSession {
      override val playing = false
      override var durationMillis: Long? = null
      override var positionMillis = Long.MAX_VALUE - 2
      override var canSeek = true
      override var error: String? = null
      override fun play() = Unit
      override fun pause() = Unit
      override fun seekTo(positionMillis: Long) { seek = positionMillis }
    }
    session.skipBy(MediaSkipMillis)
    assertNull(seek)
    session.durationMillis = Long.MAX_VALUE
    session.skipBy(MediaSkipMillis)
    assertEquals(Long.MAX_VALUE, seek)
    session.positionMillis = 4
    session.skipBy(-MediaSkipMillis)
    assertEquals(0L, seek)
    session.canSeek = false
    session.skipBy(MediaSkipMillis)
    assertEquals(0L, seek)
    session.canSeek = true
    session.error = "unavailable"
    session.skipBy(MediaSkipMillis)
    assertEquals(0L, seek)
  }

  @Test fun previewsPreserveAspectAndRequestFewerPixelsDuringScroll() {
    val portrait = IntSize(600, 800)
    assertEquals(IntSize(288, 384), viewerPageRenderSize(portrait, 700, preview = true, thumbnail = false))
    assertEquals(IntSize(700, 933), viewerPageRenderSize(portrait, 700, preview = false, thumbnail = false))
    assertEquals(IntSize(144, 192), viewerPageRenderSize(portrait, 700, preview = true, thumbnail = true))
    assertEquals(IntSize(2048, 1536), viewerPageRenderSize(IntSize(800, 600), 9999, false, false))
    assertEquals(IntSize(1, 384), viewerPageRenderSize(IntSize(1, Int.MAX_VALUE), 800, true, false))
  }

  @Test fun cacheReusesSharperPagesAndEvictsTheLeastRecentlyUsedPage() = runTest {
    val source = PageSource()
    val cache = ViewerPageRenderCache(source)
    val low = IntSize(200, 300)
    val high = IntSize(600, 900)
    val first = cache.render(0, high, false)
    assertSame(first, cache.render(0, low, false))
    assertNotSame(first, cache.render(0, low, true))
    assertEquals(2, source.renders)
    for (page in 1..5) cache.render(page, low, false)
    assertNotSame(first, cache.render(0, high, false))
    assertEquals(8, source.renders)
  }

  @Test fun cacheSerializesBothSourceMethodsAndCapsBitmapBytes() = runTest {
    val source = PageSource()
    val cache = ViewerPageRenderCache(source)
    val target = IntSize(2048, 2048)
    val first = cache.render(0, target, false)
    List(4) { page -> async { cache.measure(page); cache.render(page + 1, target, false) } }.awaitAll()
    assertEquals(1, source.peak)
    assertNotSame(first, cache.render(0, target, false))
    assertEquals(6, source.renders)
  }

  @Test fun reducedMotionAndLensPhasesMatchTheViewerBench() {
    assertTrue(FluidMotionPolicy(0f).reducedMotion)
    assertEquals(430, FluidMotion.PlaneAppearMillis)
    assertEquals(470, FluidMotion.PlaneDisappearMillis)
    val lens = GlassPieceContent(GlassPieceContentRole.Lens)
    assertEquals(0f, glassPieceContentLook(lens, .15f).alpha, 0f)
    val appearing = glassPieceContentLook(lens, .40f)
    assertTrue(appearing.scale > 1.8f && appearing.alpha in 0.1f..0.6f)
    assertEquals(GlassContentLook.Sharp, glassPieceContentLook(lens, 1f))
    assertEquals(0f, glassPieceContentLook(exitGlyph, .30f).alpha, 0f)
    assertEquals(0, ViewerChromeMotion.duration(false, 1f, 0f))
  }

  @Test fun exportVideoAndDocumentAt120HzInBothThemes() {
    val directory = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6C3_DIR"))
    for (dark in listOf(false, true)) {
      for (trace in listOf(videoTrace(dark), pagesTrace(dark))) {
        assertEquals(120, trace.fps)
        assertTrue(trace.frames.all { it.elements.isNotEmpty() })
        if (directory != null) writeViewerTrace(trace, File(directory, "${trace.name}/trace.json"))
      }
    }
  }

  private fun writeViewerTrace(trace: GlassTrace, file: File) {
    // The bank samples the page items and draws the glass afterwards. Keeping the video and
    // paper on the page layer also lets them move without clipping them to a glass capsule.
    val frames = trace.frames.map { frame ->
      val (page, elements) = frame.elements.partition { it["kind"] == "background" }
      mapOf("t" to frame.tMs, "page" to page, "elements" to elements)
    }
    file.parentFile?.mkdirs()
    file.writeText(GlassTraceJson.encode(trace.toJson() + ("frames" to frames)), Charsets.UTF_8)
  }

  private val exitGlyph = GlassPieceContent(GlassPieceContentRole.Exit, span = GlassPieceContentDefaults.LensExitSpan)
  private val lensGlyph = GlassPieceContent(GlassPieceContentRole.Lens)
  private val room = Rect(0f, 0f, 320f, 568f)

  private fun videoTrace(dark: Boolean): GlassTrace {
    val box = Rect(132f, 256f, 188f, 312f)
    val piece = GlassPlanePiece("play", box, FluidCornerRadii.all(28f), variant = GlassLiquidVariant.Clear,
      id = "play", transition = GlassPlaneTransition.Materialize)
    val shown = GlassPlaneState(listOf(piece), 0f, box, room = room)
    val hidden = GlassPlaneState(emptyList(), 0f, box, room = room)
    val hide = GlassPlaneJourney(shown, hidden)
    val show = GlassPlaneJourney(hidden, shown)
    val off = 150.0
    val on = 825.0
    val frames = (0..168).map { index ->
      val now = glassTraceTimeMs(index, 120)
      val video = Rect(-135f + index / 12f, 107f, 455f + index / 12f, 461f)
      val elements = mutableListOf(image("video", video, "dune.png"))
      val flight = when {
        now in off..(off + hide.durationMs) -> hide.frame(now - off).pieces.singleOrNull()
        now >= on -> show.frame(now - on).pieces.singleOrNull()
        else -> null
      }?.takeIf { it.materialize > 0f }
      val resting = now < off || now > on + show.durationMs
      if (flight != null || resting) {
        val bounds = flight?.bounds ?: box
        val look = when (flight?.change) {
          GlassPlaneChange.Materialize -> glassPieceContentLook(lensGlyph, flight.phase)
          GlassPlaneChange.Dematerialize -> glassPieceContentLook(exitGlyph, flight.phase)
          else -> GlassContentLook.Sharp
        }
        val content = listOf(mapOf(
          "name" to "glifo", "icon" to "fluid:play_arrow.fill", "iconSizeDp" to 29, "color" to "#FFFFFF",
          "x" to (bounds.width - box.width) / 2f, "y" to (bounds.height - box.height) / 2f,
          "w" to box.width, "h" to box.height, "alpha" to look.alpha, "scale" to look.scale,
          "blurSigmaDp" to glassContentSigmaPx(look.blur, box.height),
        ))
        elements += clearGlass("play", bounds, flight?.materialize ?: 1f, content)
      }
      val timelineAlpha = when {
        now < off -> 1f
        now < on -> (1f - ((now - off) / ViewerChromeMotion.HideMillis).toFloat()).coerceIn(0f, 1f)
        else -> ((now - on) / ViewerChromeMotion.ShowMillis).toFloat().coerceIn(0f, 1f)
      }
      if (timelineAlpha > 0f) {
        val footer = Rect(16f, 477f, 304f, 536f)
        elements += clearGlass("scrubber", footer, timelineAlpha, listOf(mapOf(
          "name" to "tempo", "w" to footer.width, "h" to footer.height, "alpha" to timelineAlpha,
          "items" to listOf(
            mapOf("kind" to "background", "x" to 14, "y" to 22, "w" to 260, "h" to 3,
              "background" to mapOf("kind" to "color", "color" to "#767676")),
            mapOf("kind" to "background", "x" to 14, "y" to 22, "w" to 82, "h" to 3,
              "background" to mapOf("kind" to "color", "color" to "#FFFFFF")),
            mapOf("kind" to "text", "x" to 14, "y" to 43, "text" to "0:18", "sizeDp" to 12, "color" to "#FFFFFF"),
            mapOf("kind" to "text", "x" to 242, "y" to 43, "text" to "0:56", "sizeDp" to 12, "color" to "#FFFFFF"),
          ),
        )))
      }
      GlassTraceFrame(now, elements)
    }
    return trace("video-comandi", dark, frames, "#000000",
      "Piano e curve di produzione R4 B3 a 120 Hz. Il fondale è il fermo delle dune spostato nel banco.",
      events = listOf(
        mapOf("t" to off, "kind" to "mark", "label" to "Tocco per nascondere"),
        mapOf("t" to on, "kind" to "mark", "label" to "Tocco per mostrare"),
      ),
    )
  }

  private fun pagesTrace(dark: Boolean): GlassTrace {
    val ink = if (dark) "#F3F0EF" else "#27272A"
    val frames = (0..144).map { index ->
      val progress = ((index - 15) / 112f).coerceIn(0f, 1f)
      val offset = 609f * progress * progress * (3f - 2f * progress)
      val top = listOf(72f, 440f, 666f)
      val heights = listOf(352f, 210f, 352f)
      val current = top.indices.last { top[it] - offset <= room.center.y }
      val elements = MutableList(top.size) { page ->
        image("pagina-${page + 1}", Rect(24f, top[page] - offset, 296f, top[page] - offset + heights[page]),
          "pagina-${page + 1}.png")
      }
      val controls = Rect(207f, 56f, 303f, 96f)
      val recipe = glassRecipe(GlassRole.Floating, controls.width, controls.height, dark)
      elements += liquidElement(shape("pagine", controls), recipe, null, "Contatore del visore").also(::completeRestBody) +
        ("content" to listOf(mapOf("name" to "contatore", "w" to 96, "h" to 40, "items" to listOf(
          mapOf("kind" to "text", "text" to FluidPagesPresetStrings().counter(current + 1, top.size),
            "x" to 16, "y" to 25, "sizeDp" to 16, "color" to ink, "weight" to 600),
          mapOf("kind" to "icon", "icon" to "fluid:grid_view", "x" to 59, "y" to 10, "sizeDp" to 20, "color" to ink),
        ))))
      GlassTraceFrame(glassTraceTimeMs(index, 120), elements)
    }
    return trace("pdf-pagine", dark, frames, if (dark) "#161619" else "#F6F5F2",
      "Scorrimento di banco con pagine miste e contatore del preset. Le immagini del documento sono di prova.")
  }

  private fun trace(
    name: String, dark: Boolean, frames: List<GlassTraceFrame>, background: String, note: String,
    events: List<Map<String, Any?>> = emptyList(),
  ) = GlassTrace(name + if (dark) "-scuro" else "", "f6c-3", 1f, if (dark) "dark" else "light",
    room.width, room.height, frames, 120,
    mapOf("background" to mapOf("kind" to "color", "color" to background), "events" to events, "_" to note))

  private fun shape(name: String, rect: Rect): Map<String, Any?> = mapOf(
    "name" to name, "x" to rect.left, "y" to rect.top, "w" to rect.width, "h" to rect.height,
  )

  private fun image(name: String, rect: Rect, path: String): Map<String, Any?> = shape(name, rect) + mapOf(
    "kind" to "background", "background" to mapOf("kind" to "image", "path" to "build/f6c-3-tracce-assets/$path"),
  )

  private fun clearGlass(name: String, rect: Rect, amount: Float, content: List<Map<String, Any?>>): Map<String, Any?> {
    val recipe = glassRecipe(GlassRole.Floating, rect.width, rect.height, true, GlassLiquidVariant.Clear)
    glassMaterializeRecipe(recipe, amount)
    return liquidElement(shape(name, rect), recipe, "dark", "Clear M2 / R4 B3").also(::completeRestBody) + ("content" to content)
  }

  private class PageSource : FluidPageSource {
    override val count = 10
    private var active = 0
    var peak = 0
    var renders = 0

    override suspend fun size(index: Int): IntSize = serialized { IntSize(600, 900) }
    override suspend fun render(index: Int, target: IntSize): ImageBitmap = serialized {
      renders++
      // The JVM runs without android.graphics. Only dimensions are part of the cache contract.
      Proxy.newProxyInstance(ImageBitmap::class.java.classLoader, arrayOf(ImageBitmap::class.java)) { _, method, _ ->
        when (method.name) {
          "getWidth" -> target.width
          "getHeight" -> target.height
          "toString" -> target.toString()
          else -> error("Unexpected bitmap method: ${method.name}")
        }
      } as ImageBitmap
    }

    private suspend fun <T> serialized(result: () -> T): T {
      active++
      peak = maxOf(peak, active)
      try {
        delay(1)
        return result()
      } finally {
        active--
      }
    }
  }
}
