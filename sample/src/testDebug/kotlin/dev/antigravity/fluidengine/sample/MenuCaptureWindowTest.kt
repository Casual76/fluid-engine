@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntRect
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader as BackdropShader
import dev.antigravity.fluidengine.ui.glass.backdrop.asAndroidRuntimeShader
import dev.antigravity.fluidengine.ui.fluidphysics.journeyCaptureRegion
import dev.antigravity.fluidengine.ui.glass.backdrop.captureRegion
import dev.antigravity.fluidengine.ui.glass.backdrop.windowedEffect
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurKernel
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurSource
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeTransparentGuard
import dev.antigravity.fluidengine.ui.glass.field.GlassRingTransparentGuard
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassScrollEdgeUniforms
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MenuCaptureWindowTest {
  private val window = IntRect(0, 0, 673, 553)
  private val crop = requireNotNull(captureRegion(Rect(97.3f, 73.5f, 548.25f, 468.8f),
    Size(window.width.toFloat(), window.height.toFloat()), 1f))

  @Test fun cardEPannelloUsanoLaPosaSoloQuandoMancaIlViaggio() {
    val live = Rect(84f, 130f, 144f, 177f)
    val first = Rect(0f, 0f, 24f, 24f)
    val last = Rect(290f, 450f, 320f, 480f)
    for (scale in listOf(1f, 0.5f)) {
      val extent = Size(320f * scale, 480f * scale)
      val expected = if (scale == 1f) IntRect(64, 112, 160, 192) else IntRect(32, 48, 80, 96)
      fun fallback(from: Rect?, to: Rect?, pose: Rect) =
        journeyCaptureRegion(from, to, null, 12f, extent, scale, useRoute = false, limitMissingJourney = true) { pose }
      for ((from, to) in listOf(null to null, first to null, null to last, Rect.Zero to last, first to Rect.Zero)) {
        assertEquals(expected, fallback(from, to, live))
      }
      assertEquals(IntRect.Zero, fallback(null, null, Rect.Zero))
      assertEquals(IntRect.Zero, fallback(null, last, Rect(400f, 600f, 415f, 630f)))
      assertEquals(IntRect.Zero, journeyCaptureRegion(null, last, null, 0f, Size(319f, 479f), 1f,
        true, true) { Rect(319.5f, 20f, 324f, 40f) })
      assertEquals(IntRect(0, 0, extent.width.toInt(), extent.height.toInt()),
        fallback(first, null, Rect(0f, 0f, 320f, 480f)))

      // Viaggio completo: stessa riserva di 5aefe1d, anche quando la proiezione copre l'host.
      assertNull(journeyCaptureRegion(first, last, null, 12f, extent, scale, true, true) { live })
      val old = captureRegion(live.inflate(live.maxDimension * 0.20f).inflate(12f), extent, scale)
      assertEquals(old, journeyCaptureRegion(live, live, null, 12f, extent, scale, true, true) { live })
      assertNull(journeyCaptureRegion(first, last, null, 12f, extent, scale, false, true) { error("posa inutile") })
      assertNull(journeyCaptureRegion(null, last, null, 12f, extent, scale, true, false) { error("posa inutile") })
    }
  }

  private fun node(name: String, rect: IntRect, record: Canvas.() -> Unit): RenderNode =
    RenderNode(name).apply {
      setPosition(rect.left, rect.top, rect.right, rect.bottom)
      val canvas = beginRecording()
      try { canvas.record() } finally { endRecording() }
    }

  private fun bitmap(root: RenderNode): Bitmap {
    val render = HardwareRenderer::class.java.getDeclaredMethod("createHardwareBitmap", RenderNode::class.java,
      Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    val pixels = render.invoke(null, root, root.width, root.height) as Bitmap
    return checkNotNull(pixels.copy(Bitmap.Config.ARGB_8888, false))
  }

  private fun Canvas.page() {
    drawColor(0xffe4dfd7.toInt())
    val paint = Paint()
    for (y in 0 until window.height step 24) for (x in 0 until window.width step 23) {
      paint.color = Color.rgb((x + y) % 220, 95 + y % 120, 60 + x % 125)
      drawRect(x.toFloat(), y.toFloat(), x + 19f, y + 10f, paint)
    }
  }

  /** La ricetta esportata dal clock del gruppo a 166,67 ms, inclusi i 96 vertici, la lente e l'ombra. */
  private fun travellingBody(outerBandPx: Float? = null, source: String = GlassShaderSources.BodyRing.text): RenderEffect {
    val relative = "tools/glassbench/traces/tasto/apre-gruppo/trace.json"
    val file = generateSequence(File(".").canonicalFile) { it.parentFile }.map { it.resolve(relative) }.first { it.isFile }
    val trace = JSONObject(file.readText())
    val density = trace.getDouble("density").toFloat()
    val material = trace.getJSONArray("frames").getJSONObject(10).getJSONArray("elements").getJSONObject(0)
    val uniforms = material.getJSONObject("body").getJSONObject("uniforms")
    val body = RuntimeShader(source)
    fun numbers(value: Any): List<Float> = when (value) {
      is JSONArray -> (0 until value.length()).flatMap { numbers(value.get(it)) }
      is Number -> listOf(value.toFloat())
      else -> {
        val text = value.toString()
        if (text.endsWith("dp^2")) listOf(text.removeSuffix("dp^2").toFloat() * density * density)
        else listOf(text.removeSuffix("dp").toFloat() * density)
      }
    }
    for (key in uniforms.keys()) {
      val value = uniforms.get(key)
      when {
        // Sposta la stessa posa in una finestra grande quanto un host, lontano dall'origine.
        key == "offset" -> body.setFloatUniform(key, -144f, -64f)
        value is String && value.startsWith("#") ->
          body.setColorUniform(key, Color.parseColor("#" + value.takeLast(2) + value.substring(1, 7)))
        else -> body.setFloatUniform(key, numbers(value).toFloatArray())
      }
    }
    if (outerBandPx != null) {
      // Stesso anello e stessa ombra. Si sposta soltanto la soglia di uscita dal ciclo geometrico.
      val band = numbers(uniforms.get("ringBand"))
      val box = numbers(uniforms.get("bbox"))
      val grow = outerBandPx - band[0]
      body.setFloatUniform("ringBand", outerBandPx, band[1])
      body.setFloatUniform("bbox", box[0] - grow, box[1] - grow, box[2] + grow, box[3] + grow)
    }
    return RenderEffect.createRuntimeShaderEffect(body, "content")
  }

  @Test fun integerWindowPreservesTheTravellingRingShadowAndSamplingPhase() {
    assertEquals(0, crop.left % 2)
    assertEquals(0, crop.top % 2)
    val effect = travellingBody()
    fun frame(bounds: IntRect): Bitmap {
      val reduced = IntRect(0, 0, (bounds.width + 1) / 2, (bounds.height + 1) / 2)
      val sample = node("campione", reduced) {
        scale(0.5f, 0.5f)
        translate(-bounds.left.toFloat(), -bounds.top.toFloat())
        page()
      }
      sample.setRenderEffect(RenderEffect.createBlurEffect(3f, 3f, Shader.TileMode.CLAMP))
      val finish = node("finitura", bounds) {
        scale(2f, 2f)
        drawRenderNode(sample)
      }
      finish.setRenderEffect(if (bounds == window) effect else
        windowedEffect(effect.asComposeRenderEffect(), bounds.topLeft).asAndroidRenderEffect())
      val output = node("fotogramma", window) { page(); drawRenderNode(finish) }
      return bitmap(output)
    }
    val before = frame(window)
    val after = frame(crop)
    val page = bitmap(node("pagina", window) { page() })
    assertFalse("il vetro deve essere visibile", before.sameAs(page))
    val dir = File("build/reports/f7-3b").apply { mkdirs() }
    for ((name, image) in listOf("prima" to before, "dopo" to after, "pagina" to page)) {
      dir.resolve("$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    assertTrue("anello e ombra, ogni pixel della finestra", before.sameAs(after))
  }

  @Test fun ringGuardPreservesTheWholeFrameIncludingTheShadow() {
    val shader = GlassShaderSources.BodyRing.text
    val reference = shader.replace(GlassRingTransparentGuard, "")
    assertNotEquals(shader, reference)
    fun image(source: String): Bitmap {
      val finish = node("anello", window) { page() }.apply { setRenderEffect(travellingBody(source = source)) }
      return bitmap(node("pagina e anello", window) { page(); drawRenderNode(finish) })
    }
    val before = image(reference)
    assertFalse(before.sameAs(bitmap(node("solo pagina", window) { page() })))
    assertTrue(before.sameAs(image(shader)))
  }

  @Test fun scrollEdgeGuardPreservesBothPassesAndTheEndOfTheFade() {
    val source = GlassEdgeBlurSource.text
    val reference = source.replace(GlassEdgeTransparentGuard, "")
    assertNotEquals(source, reference)
    for (profile in listOf(GlassScrollEdgeProfile.FadeOnly, GlassScrollEdgeProfile.Soft)) {
      for (pass in 0..1) {
        fun image(text: String): Bitmap {
          val shader = BackdropShader(text)
          val unit = 43.25f
          GlassScrollEdgeUniforms().apply {
            fill(profile, false, window.width.toFloat(), window.height.toFloat(), 56.5f, unit,
              androidx.compose.ui.graphics.Color.White, 0.4f, 1f,
              GlassEdgeBlurKernel.forSigma(GlassScrollEdgeUniforms.sigmaFor(profile, unit)))
            applyTo(shader, pass)
          }
          val edge = node("bordo della pagina", window) { page() }.apply {
            setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader.asAndroidRuntimeShader(), "content"))
          }
          return bitmap(node("striscia su blu", window) { drawColor(Color.BLUE); drawRenderNode(edge) })
        }
        val before = image(reference)
        assertNotEquals(Color.BLUE, before.getPixel(75, window.height - 12))
        if (pass == 1) assertEquals(Color.BLUE, before.getPixel(75, window.height / 2))
        assertTrue("blurShare=${profile.blurShare}, passata=$pass", before.sameAs(image(source)))
      }
    }
  }

  @Test fun ambientShadowDoesNotRevealTheRingsEarlyExitBounds() {
    fun frame(band: Float?): Bitmap {
      val finish = node("finitura", window) { drawColor(Color.WHITE) }
      finish.setRenderEffect(travellingBody(band))
      return bitmap(node("velo con goccia", window) {
        drawColor(0xffb0b0b0.toInt())
        drawRenderNode(finish)
      })
    }
    val narrow = frame(null)
    val wide = frame(160f)
    val rowA = IntArray(window.width)
    val rowB = IntArray(window.width)
    var maxDelta = 0
    for (y in 0 until window.height) {
      narrow.getPixels(rowA, 0, window.width, 0, y, window.width, 1)
      wide.getPixels(rowB, 0, window.width, 0, y, window.width, 1)
      for (x in rowA.indices) {
        maxDelta = maxOf(maxDelta, kotlin.math.abs(Color.red(rowA[x]) - Color.red(rowB[x])))
      }
    }
    // Sotto l'uovo deve esserci ancora l'ombra; cambiare la banda non può dipingere un rettangolo.
    assertTrue("ombra sotto la sagoma", Color.red(narrow.getPixel(360, 368)) < 176)
    assertTrue("gradino dovuto alla banda geometrica: $maxDelta/255", maxDelta <= 1)
  }

  @Test fun scrimFadesOneCachedMaterialUnderThePanel() {
    val bounds = IntRect(0, 0, 96, 80)
    for (ink in listOf(31, 51, 82, 122)) {
      val tint = Color.argb(ink, 0, 0, 0)
      val capture = node("cattura", bounds) { page() }.apply {
        setRenderEffect(RenderEffect.createBlurEffect(4f, 4f, Shader.TileMode.CLAMP))
      }
      fun Canvas.material() {
        drawRenderNode(capture)
        drawColor(tint)
      }
      val cached = node("materiale a piena opacità", bounds) { material() }.apply {
        setUseCompositingLayer(true, null)
      }
      for (opacity in listOf(1f, 0.53f, 0.25f, 0.004f, 0f)) {
        fun render(single: Boolean): Bitmap {
          val scrim = node("velo", bounds) {
            if (opacity > 0.004f) {
              if (single) drawRenderNode(cached) else material()
            }
          }
          scrim.setHasOverlappingRendering(!single)
          scrim.alpha = opacity
          if (single) assertFalse(scrim.hasOverlappingRendering())
          return bitmap(node("ritorno", bounds) {
            page()
            drawRenderNode(scrim)
            drawRect(30f, 15f, 82f, 52f, Paint().apply { color = 0x65457da8 })
          })
        }
        val before = render(false)
        val after = render(true)
        if (!before.sameAs(after)) {
          val output = File("build/reports/f7-3b").apply { mkdirs() }
          for ((label, image) in listOf("prima" to before, "dopo" to after)) {
            output.resolve("velo-$ink-$opacity-$label.png").outputStream().use {
              image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
          }
          fail("Velo con tinta=$ink alpha=$opacity, confronto in ${output.absolutePath}")
        }
      }
    }
  }
}
