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
import dev.antigravity.fluidengine.ui.glass.backdrop.captureRegion
import dev.antigravity.fluidengine.ui.glass.backdrop.windowedEffect
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
  private fun travellingBody(): RenderEffect {
    val relative = "tools/glassbench/traces/tasto/apre-gruppo/trace.json"
    val file = generateSequence(File(".").canonicalFile) { it.parentFile }.map { it.resolve(relative) }.first { it.isFile }
    val trace = JSONObject(file.readText())
    val density = trace.getDouble("density").toFloat()
    val material = trace.getJSONArray("frames").getJSONObject(10).getJSONArray("elements").getJSONObject(0)
    val uniforms = material.getJSONObject("body").getJSONObject("uniforms")
    val body = RuntimeShader(GlassShaderSources.BodyRing.text)
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
