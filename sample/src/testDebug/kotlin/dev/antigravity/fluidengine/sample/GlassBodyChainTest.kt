@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.RenderNode
import android.graphics.Shader
import android.widget.FrameLayout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScopeImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.field.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassBodyChainTest {
  private class Pane(pieces: Int = 1) : BackdropEffectScopeImpl() {
    override val shape = RectangleShape
    val chain = GlassBodyChain()
    val inputs = GlassDrawInputs(pieces).apply {
      body.setSlab(64f, 64f, GlassSlabGeometry())
      materialize = 1f
      saturation = 0.9f
      blurPx = 2f
      mix = 0.25f
    }
    init { size = Size(64f, 64f) }

    // Come nel censimento F7.3, il backend Windows può rifiutare lo srotolamento a otto pezzi.
    // Questa prova delle pellicole disegna un solo pezzo e mantiene gli array originali.
    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader = try {
      super.obtainRuntimeShader(key, string)
    } catch (error: IllegalArgumentException) {
      if (key !in listOf("GlassBodyFilms8", "GlassBody8") || error.message?.contains("program is too large") != true) throw error
      super.obtainRuntimeShader(key, string.replace("i < 8; ++i", "i < 1; ++i"))
    }

    fun update(): RenderEffect? {
      apply { glassBody(inputs, chain) }
      return renderEffect
    }

    fun counts(gaussian: Int, blend: Int, body: Int, final: Int) {
      assertEquals("Gaussian", gaussian, chain.gaussianBuilds)
      assertEquals("mescola", blend, chain.mixBuilds)
      assertEquals("corpo", body, chain.bodyBuilds)
      assertEquals("concatenazione finale", final, chain.chainBuilds)
    }
  }

  @Before fun acceptShaders() = GlassFieldHealth.installVerdictsForTesting(true)
  @After fun resetShaders() = GlassFieldHealth.resetForTesting()

  @Test fun tintMixRadiusAndSaturationInvalidateOnlyTheirDependents() {
    val pane = Pane()
    val first = pane.update()
    assertSame(first, pane.update())
    pane.counts(1, 1, 1, 1)
    pane.inputs.tintColor = Color.Red
    assertNotSame(first, pane.update())
    pane.counts(1, 1, 2, 2)
    pane.inputs.mix = 0.625f
    pane.update()
    pane.counts(1, 2, 2, 3)
    pane.inputs.blurPx = Math.nextUp(2f)
    pane.update()
    pane.counts(2, 3, 2, 4)
    pane.inputs.saturation = 0.8f
    val last = pane.update()
    pane.counts(2, 4, 2, 5)
    assertSame(last, pane.update())
    val mirror = pane.chain.upstream(0.8f, pane.inputs.blurPx, 0.625f, Shader.TileMode.MIRROR)
    assertSame(mirror, pane.chain.upstream(0.8f, pane.inputs.blurPx, 0.625f, Shader.TileMode.MIRROR))
    pane.counts(3, 5, 2, 5)
  }

  @Test fun identicalBuffersHitAndAnInPlaceEditOrScaleChangeMisses() {
    val pane = Pane()
    val rest = pane.update()
    pane.inputs.glow = 1f // la luce a riposo è in un disegno separato
    assertSame(rest, pane.update())
    pane.inputs.body.radii[0] = 11f
    assertNotSame(rest, pane.update())
    pane.inputs.lightInBody = true
    pane.update()
    pane.inputs.shadowAmbientAlpha = 0.3125f
    pane.update()
    pane.counts(1, 1, 4, 4)
    pane.size = Size(32f, 32f)
    pane.inputs.body.setSlab(32f, 32f, GlassSlabGeometry())
    pane.inputs.blurPx = 1f
    pane.update()
    pane.counts(2, 2, 5, 5)
    pane.size = Size(64f, 64f)
    pane.inputs.body.setSlab(64f, 64f, GlassSlabGeometry())
    pane.inputs.blurPx = 2f
    pane.inputs.lightInBody = false
    val settled = pane.update()
    repeat(8) { assertSame(settled, pane.update()) }
    pane.counts(3, 3, 6, 6)
  }

  @Test fun fallbackKeepsUpstreamAndSharpBypassBuildsNoGaussian() {
    val pane = Pane()
    val complete = pane.update()
    GlassFieldHealth.installVerdictsForTesting(false)
    val fallback = pane.update()
    assertNotNull(fallback)
    assertNotSame(complete, fallback)
    assertSame(fallback, pane.update())
    assertSame(pane.chain.upstream(0.9f, 2f, 0.25f), fallback!!.asAndroidRenderEffect())
    pane.inputs.saturation = 1f
    pane.inputs.mix = 1f
    assertNull(pane.update())
    pane.inputs.mix = 0.25f
    pane.inputs.blurPx = 0f
    assertNull(pane.update())
    pane.counts(1, 1, 1, 1)
    GlassFieldHealth.installVerdictsForTesting(true)
    assertNotNull(pane.update())
    pane.counts(1, 1, 1, 1)
  }

  @Test fun travelKeepsTheSampleFilterThenRestoresTheOrdinaryChain() {
    val pane = Pane()
    val graphics = GraphicsContext(FrameLayout(RuntimeEnvironment.getApplication()))
    val sample = graphics.createGraphicsLayer()
    try {
      pane.update()
      pane.inputs.lightInBody = true
      val travel = GlassTravelCapture(sample, 1f) { glassBody(pane.inputs, pane.chain) }
      pane.apply(travel.effects)
      val filter = sample.renderEffect
      val finish = pane.renderEffect
      assertNotNull(filter)
      assertNotNull(finish)
      pane.inputs.tintColor = Color.Green
      pane.apply(travel.effects)
      assertSame(filter, sample.renderEffect)
      assertNotSame(finish, pane.renderEffect)
      pane.counts(2, 2, 3, 1)
      pane.inputs.lightInBody = false
      pane.inputs.tintColor = Color.Transparent
      val rest = pane.update()
      assertSame(rest, pane.update())
      pane.counts(3, 3, 4, 2)
    } finally { graphics.releaseGraphicsLayer(sample) }
  }

  @Test fun ringVertexAndPaddingAreComparedByValue() {
    val pane = Pane()
    pane.inputs.kernel = GlassBodyKernel.Ring
    val ring = pane.inputs.ring
    ring.count = 4
    floatArrayOf(0f, 0f, 64f, 0f, 64f, 64f, 0f, 64f).copyInto(ring.verts)
    val initial = pane.update()
    ring.verts[4] = Math.nextDown(64f)
    val moved = pane.update()
    assertNotSame(initial, moved)
    assertSame(moved, pane.update())
    pane.inputs.paddingPx = 2f
    pane.inputs.bodyOffsetX = -2f
    pane.inputs.bodyOffsetY = -2f
    assertNotSame(moved, pane.update())
    assertEquals(2f, pane.padding, 0f)
    pane.counts(1, 1, 3, 3)
  }

  @Test fun activeFilmValuesAndTheActualProgramSelectTheSnapshot() {
    val pane = Pane(GlassFieldMaxPieces)
    val red = GlassTint(Color.Red, Color.Black, Color.Black, GlassTintMode.Fixed)
    val films = mutableListOf(red)
    pane.inputs.slabFilms = films
    val first = pane.update()
    pane.inputs.slabFilms = mutableListOf(red.copy())
    assertSame(first, pane.update())
    pane.inputs.slabFilms = films
    films[0] = red.copy(overlay = Color.Blue)
    assertNotSame(first, pane.update())
    films[0] = films[0].copy(mode = GlassTintMode.Colored)
    pane.update()
    pane.counts(1, 1, 3, 3)
    GlassFieldHealth.installForTesting(true, { check(it !== GlassShaderSources.BodyFilms8) }, { _, _ -> })
    val plain = pane.update()
    films[0] = red
    assertSame(plain, pane.update())
    pane.counts(1, 1, 4, 4)
    GlassFieldHealth.installVerdictsForTesting(true)
    assertNotSame(plain, pane.update())
    pane.counts(1, 1, 5, 5)
  }

  @Test fun alternatingPanesKeepIndependentPixelsAndTheEdgeFollowsTheRadius() {
    val a = Pane()
    val b = Pane()
    a.inputs.tintColor = Color.Red
    b.inputs.tintColor = Color.Blue
    val red = a.update()!!
    val blue = b.update()!!
    assertSame(red, a.update())
    assertEquals(Color.Red.toArgb(), render(red).getPixel(32, 32))
    assertEquals(Color.Blue.toArgb(), render(blue).getPixel(32, 32))
    a.inputs.tintColor = Color.Green
    assertEquals(Color.Green.toArgb(), render(a.update()!!).getPixel(32, 32))
    assertSame(blue, b.update())
    assertEquals(Color.Blue.toArgb(), render(blue).getPixel(32, 32))
    a.counts(1, 1, 2, 2)
    b.counts(1, 1, 1, 1)

    a.inputs.tintColor = Color.Transparent
    a.inputs.mix = 0f
    val narrow = render(a.update()!!)
    a.inputs.blurPx = 12f
    val wide = render(a.update()!!)
    val near = android.graphics.Color.red(narrow.getPixel(27, 32))
    val far = android.graphics.Color.red(wide.getPixel(27, 32))
    println("F7.2b bordo nero/bianco a x=27: r2=$near r12=$far (su 255)")
    assertTrue("bordo nero/bianco a x=27, r2=$near r12=$far", far > near + 10)
    a.counts(2, 1, 3, 4)
  }

  @Test fun gaussianComposedWithSaturationMatchesTheOriginalExplicitInput() {
    val chain = GlassBodyChain()
    val sharp = chain.upstream(0.6f, 0f, 0f)!!
    for (radius in listOf(1.75f, 9.125f)) {
      val embedded = android.graphics.RenderEffect.createBlurEffect(radius, radius, sharp, Shader.TileMode.CLAMP)
      val composed = chain.upstream(0.6f, radius, 0f)!!
      val before = render(embedded.asComposeRenderEffect(), Color.Blue, Color.Yellow)
      val after = render(composed.asComposeRenderEffect(), Color.Blue, Color.Yellow)
      assertNotEquals(before.getPixel(4, 32), before.getPixel(60, 32))
      assertTrue("saturazione seguita dal Gaussian a r=$radius", before.sameAs(after))
    }
  }

  private fun render(effect: RenderEffect, left: Color = Color.Black, right: Color = Color.White): Bitmap {
    val node = RenderNode("F7.2b").apply { setPosition(0, 0, 64, 64) }
    val canvas = node.beginRecording()
    canvas.drawColor(left.toArgb())
    canvas.drawRect(32f, 0f, 64f, 64f, Paint().apply { color = right.toArgb() })
    node.endRecording()
    node.setRenderEffect(effect.asAndroidRenderEffect())
    val root = RenderNode("F7.2b uscita").apply { setPosition(0, 0, 64, 64) }
    root.beginRecording().drawRenderNode(node)
    root.endRecording()
    // Lo stesso ingresso nascosto usato da Bitmap.createBitmap(Picture) per leggere una display list.
    val render = HardwareRenderer::class.java.getDeclaredMethod(
      "createHardwareBitmap", RenderNode::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
    )
    return try {
      val bitmap = render.invoke(null, root, 64, 64) as Bitmap
      checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
    } finally {
      root.discardDisplayList()
      node.discardDisplayList()
    }
  }
}
