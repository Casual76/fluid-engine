package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.DefaultGlassBlurRadius
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.clampGlassUnit
import dev.antigravity.fluidengine.ui.fluid.glassSizedOptics
import dev.antigravity.fluidengine.ui.fluid.sanitized
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "A riposo e' una lastra" (3.0): la superficie fisica calcola sfocatura, lente, cupola, frangia e
 * pressione con la stessa funzione per tutto il viaggio, e quella funzione da' i numeri di
 * `glassSurface`. E il materiale che viaggia fra due ottiche non sporca mai la cattura.
 */
class FluidPhysicsMaterialTest {

  private val stock: List<Pair<String, GlassOptics>> =
    GlassRole.entries.map { it.name to GlassDefaults.optics(it).sanitized() } +
      listOf(
        "Button" to GlassDefaults.buttonOptics().sanitized(),
        "StackedContent" to GlassDefaults.stackedContentOptics().sanitized(),
        "Panel" to GlassDefaults.panelOptics().sanitized(),
        "PanelCompact" to GlassDefaults.panelOptics(compact = true).sanitized(),
        "PanelSharpPage" to GlassDefaults.panelOptics(overSharpPage = true).sanitized(),
        "PanelCompactSharpPage" to GlassDefaults.panelOptics(compact = true, overSharpPage = true).sanitized(),
      )

  private val sizesDp = listOf(24f, 44f, 64f, 96f, 160f, 250f, 320f, 600f)
  private val densities = listOf(1f, 2.625f, 3.5f)

  /**
   * I numeri della lente come li calcola `glassSurface` (`GlassMaterial.kt`, il blocco `effects`:
   * `depth = opticalDepth * amount * q`, `glassSizedOptics` sul lato corto, lo spostamento per
   * `1 + press * pressedDepthBoost`, la frangia solo con `dispersion && q > 0.75`). Scritti qui per
   * esteso apposta: se uno dei due lati cambia, questo test e' il posto dove si vede.
   */
  private data class GlassSurfaceLens(
    val blur: Float,
    val height: Float,
    val amount: Float,
    val depthEffect: Boolean,
    val chromaticAberration: Boolean,
  )

  private fun glassSurfaceLens(
    optics: GlassOptics,
    minDimPx: Float,
    density: Float,
    amount: Float,
    quality: Float,
    press: Float,
  ): GlassSurfaceLens {
    val depth = clampGlassUnit(1f) * amount * quality
    val sized = glassSizedOptics(
      optics = optics,
      baseBlurPx = DefaultGlassBlurRadius.value * density,
      amount = amount,
      quality = quality,
      minDimPx = minDimPx,
      density = density,
      lensAvailable = true,
    )
    return GlassSurfaceLens(
      blur = sized.blurPx,
      height = sized.refractionHeightPx * depth,
      amount = sized.refractionAmountPx * depth * (1f + press * optics.pressedDepthBoost),
      depthEffect = optics.depthEffect,
      chromaticAberration = optics.dispersion && quality > 0.75f,
    )
  }

  @Test
  fun atRestTheLensIsExactlyTheOneGlassSurfaceComputes() {
    for ((name, optics) in stock) {
      for (sizeDp in sizesDp) for (density in densities) {
        for (amount in listOf(1f, 0.5f)) for (quality in listOf(1f, 0.8f, 0.5f)) for (press in listOf(0f, 0.4f, 1f)) {
          val minDimPx = sizeDp * density
          val expected = glassSurfaceLens(optics, minDimPx, density, amount, quality, press)
          val actual = physicsLensUniforms(
            material = optics.asPhysicsMaterial(),
            baseBlurPx = DefaultGlassBlurRadius.value * density,
            amount = amount,
            quality = quality,
            minDimPx = minDimPx,
            density = density,
            lensAvailable = true,
            press = press,
          )
          val where = "$name a $sizeDp dp, densita' $density, intensita' $amount, qualita' $quality, pressione $press"
          assertEquals("sfocatura, $where", expected.blur, actual.blurPx, 0f)
          assertEquals("altezza, $where", expected.height, actual.refractionHeightPx, 0f)
          assertEquals("spostamento, $where", expected.amount, actual.refractionAmountPx, 0f)
          assertEquals("cupola, $where", if (expected.depthEffect) 1f else 0f, actual.depthEffect, 0f)
          assertEquals("frangia, $where", if (expected.chromaticAberration) 1f else 0f, actual.dispersion, 0f)
        }
      }
    }
  }

  @Test
  fun withoutAnOriginTheMaterialIsTheOpticsAsDeclared() {
    val panel = GlassDefaults.panelOptics()
    val material = physicsMaterialAt(null, panel, 0.3f)
    assertSame(panel, material.optics)
    assertEquals(0f, material.depthEffect, 0f)
    assertEquals(0f, material.dispersion, 0f)
  }

  @Test
  fun theBlendIsClampedAndLandsExactlyOnItsEnds() {
    val button = GlassDefaults.buttonOptics()
    val panel = GlassDefaults.panelOptics(compact = true)
    assertSame(button, lerpGlassOptics(button, panel, 0f))
    assertSame(panel, lerpGlassOptics(button, panel, 1f))
    // Una molla che oltrepassa non spinge il materiale oltre i suoi capi.
    assertSame(panel, lerpGlassOptics(button, panel, 1.3f))
    assertSame(button, lerpGlassOptics(button, panel, -0.2f))
    assertSame(button, lerpGlassOptics(button, panel, Float.NaN))
  }

  @Test
  fun halfwayEveryNumberIsHalfway() {
    val button = GlassDefaults.buttonOptics()
    val panel = GlassDefaults.panelOptics(compact = true)
    val mid = lerpGlassOptics(button, panel, 0.5f)
    assertEquals((button.blurScale + panel.blurScale) / 2f, mid.blurScale, 1e-5f)
    assertEquals((button.refractionHeight.value + panel.refractionHeight.value) / 2f, mid.refractionHeight.value, 1e-4f)
    assertEquals((button.refractionAmount.value + panel.refractionAmount.value) / 2f, mid.refractionAmount.value, 1e-4f)
    assertEquals((button.highlightAlpha + panel.highlightAlpha) / 2f, mid.highlightAlpha, 1e-5f)
    assertEquals((button.shadowRadius.value + panel.shadowRadius.value) / 2f, mid.shadowRadius.value, 1e-4f)
    assertEquals((button.shadowAlpha + panel.shadowAlpha) / 2f, mid.shadowAlpha, 1e-5f)
    assertEquals((button.pressedDepthBoost + panel.pressedDepthBoost) / 2f, mid.pressedDepthBoost, 1e-5f)
  }

  @Test
  fun theDomeAndTheFringeFadeInsteadOfSwitching() {
    // Il tasto ha cupola e frangia, il pannello no: lungo il viaggio i due pesi scendono con
    // continuita', non a meta' strada.
    val button = GlassDefaults.buttonOptics()
    val panel = GlassDefaults.panelOptics(compact = true)
    var previousDepth = 2f
    var previousFringe = 2f
    for (i in 0..20) {
      val t = i / 20f
      val m = physicsMaterialAt(button, panel, t)
      assertEquals(1f - t, m.depthEffect, 1e-5f)
      assertEquals(1f - t, m.dispersion, 1e-5f)
      assertTrue(m.depthEffect <= previousDepth && m.dispersion <= previousFringe)
      previousDepth = m.depthEffect
      previousFringe = m.dispersion
    }
  }

  @Test
  fun theFringeLeavesBelowTheQualityGlassSurfaceDropsItAt() {
    val button = GlassDefaults.buttonOptics().asPhysicsMaterial()
    fun fringe(quality: Float) = physicsLensUniforms(
      material = button,
      baseBlurPx = 2.5f,
      amount = 1f,
      quality = quality,
      minDimPx = 44f,
      density = 1f,
      lensAvailable = true,
    ).dispersion
    assertEquals(1f, fringe(1f), 0f)
    assertEquals(1f, fringe(0.76f), 0f)
    assertEquals(0f, fringe(0.75f), 0f)
  }

  @Test
  fun pressingDeepensOnlyTheDisplacement() {
    // La pressione approfondisce lo spostamento e mai l'altezza: l'altezza tiene il padding della
    // cattura, e un dito non deve sporcarla.
    val button = GlassDefaults.buttonOptics().asPhysicsMaterial()
    fun lens(press: Float) = physicsLensUniforms(button, 2.5f * 2.625f, 1f, 1f, 44f * 2.625f, 2.625f, true, press)
    val rest = lens(0f)
    val held = lens(1f)
    assertEquals(rest.refractionHeightPx, held.refractionHeightPx, 0f)
    assertEquals(rest.blurPx, held.blurPx, 0f)
    // Il tasto di vetro: 14 dp a riposo, 24 dp sotto il dito (la lente di LibrePods).
    assertEquals(14f * 2.625f, rest.refractionAmountPx, 1e-3f)
    assertEquals(24f * 2.625f, held.refractionAmountPx, 1e-3f)
  }

  @Test
  fun aMaterialInTransitNeverMovesTheCapturePadding() {
    // Il patto di Fluid-physics: il margine della cattura vale max(0, sfocatura - altezza) e non
    // deve muoversi durante un viaggio. A materiale e qualita' pieni vale zero per ogni miscela fra
    // due ottiche di serie, a ogni taglia della sagoma: la sfocatura non supera mai l'altezza.
    val ts = (0..100).map { it / 100f }
    for ((fromName, from) in stock) for ((toName, to) in stock) {
      for (t in ts) {
        val material = physicsMaterialAt(from, to, t)
        for (sizeDp in sizesDp) for (density in listOf(1f, 2.625f)) {
          val lens = physicsLensUniforms(
            material = material,
            baseBlurPx = DefaultGlassBlurRadius.value * density,
            amount = 1f,
            quality = 1f,
            minDimPx = sizeDp * density,
            density = density,
            lensAvailable = true,
          )
          val where = "$fromName -> $toName a t=$t, $sizeDp dp, densita' $density"
          assertTrue("sfocatura oltre l'altezza: $where", lens.blurPx <= lens.refractionHeightPx + 1e-3f)
          assertEquals("padding: $where", 0f, physicsCapturePaddingPx(lens, material.optics.vibrancy != 1f), 0f)
        }
      }
    }
  }

  @Test
  fun theMenuJourneyOfTheDesignDocKeepsBlurUnderTheLens() {
    // Il caso del progetto dei menu (fase 7): dal tasto (Interactive, 3,5 dp di sfocatura su 10 di
    // lente) al pannello compatto (6,25 su 7). Le due rette non si incrociano mai.
    val interactive = GlassDefaults.optics(GlassRole.Interactive)
    val compact = GlassDefaults.panelOptics(compact = true)
    for (i in 0..1000) {
      val o = lerpGlassOptics(interactive, compact, i / 1000f)
      val blurDp = DefaultGlassBlurRadius.value * o.blurScale
      assertTrue("t=${i / 1000f}: $blurDp > ${o.refractionHeight}", blurDp <= o.refractionHeight.value)
    }
  }

  @Test
  fun aBlendBetweenEqualEndsIsExactlyThoseEnds() {
    // Il pannello compatto su una pagina nitida ha la sfocatura alta esattamente quanto la lente
    // (7 dp su 7): un lerp che sbaglia di un ulp fra due capi uguali aprirebbe un margine di cattura
    // di mezzo milionesimo di pixel, e un margine che cambia rifa' la cattura.
    val sharp = GlassDefaults.panelOptics(compact = true, overSharpPage = true)
    val copy = sharp.copy()
    for (i in 0..100) {
      val o = lerpGlassOptics(sharp, copy, i / 100f)
      assertEquals(sharp.refractionHeight, o.refractionHeight)
      assertEquals(sharp.blurScale, o.blurScale, 0f)
    }
  }

  @Test
  fun aSubQuarterPixelPaddingIsNoPadding() {
    // L'arrotondamento non deve poter muovere il margine della cattura.
    assertEquals(0f, physicsLensPadding(7f, 6.9999995f), 0f)
    assertEquals(0f, physicsLensPadding(7.2f, 7f), 0f)
    assertEquals(0f, physicsLensPadding(3f, 10f), 0f)
    assertEquals(6f, physicsLensPadding(10f, 4f), 0f)
  }

  @Test
  fun aHandWrittenEndWithBlurPastItsLensIsTheCallersPadding() {
    // Il contratto vale per costruzione solo se i capi lo rispettano: un'ottica scritta a mano con
    // la sfocatura oltre la lente ha un margine, e la funzione lo dice invece di nasconderlo.
    val foggy = GlassDefaults.panelOptics().copy(blurScale = 8f, refractionHeight = 4.dp)
    val lens = physicsLensUniforms(foggy.sanitized().asPhysicsMaterial(), 2.5f, 1f, 1f, 50f, 1f, true)
    assertTrue(physicsCapturePaddingPx(lens, vibrancyApplied = true) > 0f)
  }
}
