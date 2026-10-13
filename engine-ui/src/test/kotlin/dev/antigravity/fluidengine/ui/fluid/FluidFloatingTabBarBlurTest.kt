package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Di quanto sfocano la riga nascosta e il puck a riposo della barra flottante, che e' una decisione
 * sola e dipende da chi dipinge il pannello: se e' la barra, `FluidFloatingTabBarGlass.blurRadius`
 * e' il raggio di base e si rifa' il conto del pannello; se e' l'app, e' gia' il raggio finale.
 *
 * Il caso dell'app e' quello che la 2.12.0 aveva rotto: la riga e il puck moltiplicavano per 1,8 un
 * numero che l'app aveva gia' dato al suo pannello, e il puck risultava piu' sfocato della barra
 * intorno. Aritmetica pura, quindi si prova qui.
 */
class FluidFloatingTabBarBlurTest {

  private val floating = GlassDefaults.optics(GlassRole.Floating).sanitized()

  private val radiiDp = listOf(0f, 1f, 2f, DefaultGlassBlurRadius.value, 4f, 8f, 16f)
  private val densities = listOf(1f, 2f, 2.625f, 3.5f)
  private val heightsDp = listOf(48f, 56f, 64f, 72f, 96f, 200f, 320f, 600f)
  private val qualities = listOf(1f, 0.75f, 0.5f, 0.1f, 0f)

  private fun blur(
    appSuppliesPane: Boolean,
    radiusDp: Float = DefaultGlassBlurRadius.value,
    heightDp: Float = 64f,
    density: Float = 1f,
    quality: Float = 1f,
    lensAvailable: Boolean = true,
  ) = floatingBarBlurPx(
    appSuppliesPane = appSuppliesPane,
    optics = floating,
    blurRadiusPx = radiusDp * density,
    paneHeightPx = heightDp * density,
    quality = quality,
    density = density,
    lensAvailable = lensAvailable,
  )

  // ----- il pannello lo passa l'app: il numero e' gia' quello finale --------------------------

  @Test
  fun anAppPane_isBlurredByExactlyTheRadiusTheAppGave_whateverTheBarIs() {
    for (radius in radiiDp) for (density in densities) for (height in heightsDp) {
      for (quality in qualities) for (lens in listOf(true, false)) {
        assertEquals(
          "raggio $radius dp, x$density, barra $height dp, q $quality, lente $lens",
          radius * density,
          blur(true, radius, height, density, quality, lens),
          0f,
        )
      }
    }
  }

  @Test
  fun anAppPane_getsNoRoleMultiplier_theRegressionOfTwoPointTwelve() {
    // L'app di cui si parla: pannello a 4 dp, `FluidFloatingTabBarGlass(blurRadius = 4.dp)`.
    // Nella 2.12.0 la riga nascosta e il puck sfocavano 7,2 dp sopra un pannello da 4.
    val app = blur(appSuppliesPane = true, radiusDp = 4f)
    val engine = blur(appSuppliesPane = false, radiusDp = 4f)
    assertEquals(4f, app, 0f)
    assertEquals(4f * floating.blurScale, engine, 0.0001f)
    assertTrue("il puck sfocherebbe piu' del pannello", engine > app)
  }

  @Test
  fun anAppPane_doesNotChangeWithTheScrollQuality() {
    // La riga nascosta non l'ha mai applicata, e il numero dell'app e' quello del suo pannello.
    val full = blur(true, quality = 1f)
    qualities.forEach { assertEquals("q $it", full, blur(true, quality = it), 0f) }
  }

  // ----- il pannello lo fa la barra: il conto del pannello ------------------------------------

  @Test
  fun theBarsOwnPane_isBlurredLikeThePaneItself_basePlusRoleFromTheDefault() {
    // 2,5 dp di base per il x1,8 del ruolo `Floating`: i 4,5 dp che il CHANGELOG della 2.12.0 dice.
    assertEquals(4.5f, blur(appSuppliesPane = false), 0.0001f)
  }

  @Test
  fun theBarsOwnPane_isExactlyWhatGlassSurfaceComputes_atEverySizeAndQuality() {
    for (radius in radiiDp) for (density in densities) for (height in heightsDp) {
      for (quality in qualities) for (lens in listOf(true, false)) {
        val pane = glassSizedOptics(
          optics = floating,
          baseBlurPx = radius * density,
          amount = 1f,
          quality = quality,
          minDimPx = height * density,
          density = density,
          lensAvailable = lens,
        ).blurPx
        assertEquals(
          "raggio $radius dp, x$density, barra $height dp, q $quality, lente $lens",
          pane,
          blur(false, radius, height, density, quality, lens),
          0f,
        )
      }
    }
  }

  @Test
  fun theBarsOwnPane_followsTheQuality_andTheAppPaneDoesNot() {
    assertTrue(blur(false, quality = 0f) < blur(false, quality = 1f))
    assertEquals(
      4.5f * FluidGlassBlurFloor,
      blur(false, quality = 0f),
      0.0001f,
    )
  }

  @Test
  fun aBarOfOrdinaryHeight_hasNoSizeRule_soTheTwoCasesDifferOnlyByTheRole() {
    // Sotto i 64 dp la regola della taglia vale zero: la barra e l'app differiscono del solo ruolo.
    for (radius in radiiDp.filter { it > 0f }) for (density in densities) {
      val ratio = blur(false, radius, 64f, density) / blur(true, radius, 64f, density)
      assertEquals("raggio $radius dp, x$density", floating.blurScale, ratio, 0.0001f)
    }
  }

  // ----- la risoluzione a cui si registra la riga nascosta ------------------------------------

  @Test
  fun theHiddenRowResolution_followsTheRadiusThatIsReallyUsed() {
    for (radius in radiiDp) {
      assertEquals(
        radius,
        floatingBarBlurDp(appSuppliesPane = true, blurRadiusDp = radius, blurScale = floating.blurScale),
        0f,
      )
      assertEquals(
        radius * floating.blurScale,
        floatingBarBlurDp(appSuppliesPane = false, blurRadiusDp = radius, blurScale = floating.blurScale),
        0f,
      )
    }
    // Un pannello dell'app a 4 dp si registra alla frazione che il suo `liquidGlass` sceglie per 4 dp
    // (`glassResolutionScale(blurRadiusDp)`), non a quella di 7,2.
    val app = glassResolutionScale(
      floatingBarBlurDp(appSuppliesPane = true, blurRadiusDp = 4f, blurScale = floating.blurScale)
    )
    assertEquals(glassResolutionScale(4f), app, 0f)
    assertTrue(app > glassResolutionScale(4f * floating.blurScale))
  }
}
