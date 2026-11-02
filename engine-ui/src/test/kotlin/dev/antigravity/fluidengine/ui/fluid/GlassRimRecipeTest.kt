package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Il bordo speculare e' uno solo: `glassSurface` e `fluidPhysicsSurface` lo chiedono a
 * [glassRimRecipe], e il tratto fisico ne disegna il profilo con un gradiente.
 *
 * Il difetto che questo inchioda era in un colore e in un modo di comporre, e non si vede leggendo
 * il codice: il bordo fisico era un gradiente bianco in entrambi i temi, quindi la stessa lastra ferma
 * (`glassSurface`) aveva un bordo scuro sul lato chiaro e quella in viaggio un bordo bianco su una
 * faccia chiara, cioe' nessuno.
 */
class GlassRimRecipeTest {

  // ----- il colore, per lato -------------------------------------------------------------------

  @Test
  fun darkSide_isLightAddedOnTop() {
    val rim = glassRimRecipe(darkSide = true)
    assertEquals(Color.White.copy(alpha = 0.5f), rim.color)
    assertEquals(BlendMode.Plus, rim.blendMode)
  }

  @Test
  fun lightSide_isADarkLinePutDownOrdinarily() {
    val rim = glassRimRecipe(darkSide = false)
    assertEquals(Color.Black.copy(alpha = 0.28f), rim.color)
    assertEquals(BlendMode.SrcOver, rim.blendMode)
  }

  @Test
  fun theTurnOverIsBothHalves_aDarkColourAndANonAdditiveBlend() {
    val dark = glassRimRecipe(darkSide = true)
    val light = glassRimRecipe(darkSide = false)
    // Girare il solo colore compila, gira e non disegna niente: bianco in Plus su una faccia chiara
    // somma luce dove non ne resta, e nero in Plus somma zero. Le due meta' vanno insieme.
    assertNotEquals(dark.blendMode, light.blendMode)
    assertTrue("sul lato chiaro il bordo e' scuro", light.color.luminance() < 0.5f)
    assertTrue("sul lato scuro il bordo e' chiaro", dark.color.luminance() > 0.5f)
    // E quello scuro e' piu' quieto del gemello bianco: una riga scura su carta si legge a un
    // contrasto piu' basso di una chiara al buio.
    assertTrue(light.color.alpha < dark.color.alpha)
  }

  @Test
  fun theShadowColour_isTheSameCountForEverySurfaceThatDrawsOne() {
    assertEquals(Color.Black.copy(alpha = 0.16f), glassShadowColor(darkSide = false))
    assertEquals(Color.Black.copy(alpha = 0.10f), glassShadowColor(darkSide = true))
    // Sul chiaro pesa di piu': la pagina e' quasi bianca e l'ombra e' tutto cio' che alza il pannello.
    assertTrue(glassShadowColor(false).alpha > glassShadowColor(true).alpha)
  }

  // ----- il profilo ----------------------------------------------------------------------------

  @Test
  fun aCapsuleRamp_isHalfTheHeight_soTheRimIsZeroExactlyAtMidHeight() {
    // Raggio = mezza altezza: l'arco allargato dello shader e' mezza altezza a sua volta.
    assertEquals(0.5f, glassRimRamp(cornerRadiusPx = 28f, widthPx = 200f, heightPx = 56f), 0f)
    // Un raggio piu' grande del possibile non allarga oltre la capsula.
    assertEquals(0.5f, glassRimRamp(cornerRadiusPx = 500f, widthPx = 200f, heightPx = 56f), 0f)
  }

  @Test
  fun aCardRamp_isOneAndAHalfCornersLong() {
    // 24 px di raggio, 36 di arco allargato, su una card alta 200.
    assertEquals(36f / 200f, glassRimRamp(24f, 320f, 200f), 1e-6f)
    // Una sagoma stretta: il tetto e' mezzo lato corto, e la rampa e' la frazione dell'altezza.
    assertEquals(20f / 200f, glassRimRamp(100f, 40f, 200f), 1e-6f)
  }

  @Test
  fun anUnusableSize_hasNoRamp_andASquareCornerHasNone() {
    assertEquals(0f, glassRimRamp(24f, 0f, 200f), 0f)
    assertEquals(0f, glassRimRamp(24f, 200f, 0f), 0f)
    assertEquals(0f, glassRimRamp(24f, Float.NaN, 200f), 0f)
    assertEquals(0f, glassRimRamp(0f, 320f, 200f), 0f)
    assertEquals(0f, glassRimRamp(-5f, 320f, 200f), 0f)
  }

  @Test
  fun intensity_isFullAtTheTopAndBottomEdges_zeroInTheMiddle_andLinearBetween() {
    val ramp = 0.18f
    assertEquals(1f, glassRimIntensity(0f, ramp, ramp), 0f)
    assertEquals(1f, glassRimIntensity(1f, ramp, ramp), 0f)
    assertEquals(0f, glassRimIntensity(0.5f, ramp, ramp), 0f)
    assertEquals(0f, glassRimIntensity(ramp, ramp, ramp), 1e-6f)
    assertEquals(0.5f, glassRimIntensity(ramp / 2f, ramp, ramp), 1e-6f)
    assertEquals(0.5f, glassRimIntensity(1f - ramp / 2f, ramp, ramp), 1e-6f)
    // E le due rampe possono essere diverse: angoli diversi sopra e sotto.
    assertEquals(0f, glassRimIntensity(0.3f, 0.2f, 0.4f), 0f)
    assertEquals(0.5f, glassRimIntensity(0.1f, 0.2f, 0.4f), 1e-6f)
    assertEquals(0.5f, glassRimIntensity(0.8f, 0.2f, 0.4f), 1e-6f)
  }

  /**
   * Il profilo contro lo shader: `|normale . (0, 1)|` dove la normale e' quella di `gradSdRoundedRect`
   * con `gradRadius = min(raggio * 1,5, mezzo lato corto)` (`Shaders.kt`), letto lungo il bordo
   * vero della sagoma, sul fianco sinistro. Il gradiente fisico e' lineare per costruzione; qui si
   * misura quanto dista dallo shader, e il numero e' piccolo: un'approssimazione, non un'altra
   * ricetta.
   */
  private fun shaderIntensityOnTheLeftEdge(width: Float, height: Float, radius: Float, y: Float): Float {
    val halfW = width / 2f
    val halfH = height / 2f
    // Il punto sul bordo vero: l'arco di raggio `radius` in alto, poi il lato dritto.
    val x = if (y < radius) radius - sqrt(radius * radius - (radius - y) * (radius - y)) else 0f
    val cx = x - halfW
    val cy = y - halfH
    val gradRadius = min(radius * 1.5f, min(halfW, halfH))
    val cornerX = abs(cx) - (halfW - gradRadius)
    val cornerY = abs(cy) - (halfH - gradRadius)
    val gradY = if (cornerX >= 0f || cornerY >= 0f) {
      val mx = max(cornerX, 0f)
      val my = max(cornerY, 0f)
      val len = sqrt(mx * mx + my * my)
      if (len == 0f) 0f else my / len
    } else {
      if (cornerX >= cornerY) 0f else 1f
    }
    return abs(gradY)
  }

  @Test
  fun theLinearProfile_followsTheShaderAlongTheEdgeWithinAFewPercent() {
    for ((w, h, r) in listOf(
      Triple(320f, 200f, 24f),
      Triple(360f, 120f, 36f),
      Triple(210f, 96f, 18f),
      Triple(600f, 400f, 54f),
    )) {
      val ramp = glassRimRamp(r, w, h)
      var worst = 0f
      var y = 0f
      while (y <= h / 2f) {
        val shader = shaderIntensityOnTheLeftEdge(w, h, r, y)
        val profile = glassRimIntensity(y / h, ramp, ramp)
        worst = max(worst, abs(shader - profile))
        y += 0.5f
      }
      assertTrue("${w}x$h r$r: il profilo dista $worst dallo shader", worst < 0.12f)
    }
  }

  @Test
  fun aCapsule_matchesTheShaderExactly() {
    // Qui l'arco allargato coincide con quello vero: il gradiente e' esatto, non un'approssimazione.
    val w = 200f
    val h = 56f
    val ramp = glassRimRamp(h / 2f, w, h)
    var y = 0f
    while (y <= h / 2f) {
      assertEquals(
        "y $y",
        shaderIntensityOnTheLeftEdge(w, h, h / 2f, y),
        glassRimIntensity(y / h, ramp, ramp),
        0.03f,
      )
      y += 0.5f
    }
  }
}
