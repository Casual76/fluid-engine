package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMaterialStateTest {

  @Test
  fun glassOptics_presetsRunFromQuietChromeToAThickControl() {
    val bar = GlassDefaults.optics(GlassRole.Bar)
    val floating = GlassDefaults.optics(GlassRole.Floating)
    val interactive = GlassDefaults.optics(GlassRole.Interactive)
    val modal = GlassDefaults.optics(GlassRole.Modal)
    val content = GlassDefaults.optics(GlassRole.Content)

    // Content glass is the one role with nothing outside its own perimeter. A card is at the same
    // depth as the text beside it, and the moment two cards in a scrolling list both cast a shadow
    // the page reads as a stack of loose slabs rather than as a page.
    assertEquals(0f, content.shadowAlpha, 0f)
    assertEquals(0f, content.shadowRadius.value, 0f)
    assertFalse(content.depthEffect)

    // And the top bar is the one role that keeps a heavy kernel: it is the only pane with sharp text
    // scrolling underneath it, arriving from a page it does not control. Everything else here stands
    // over something soft and can afford to be nearly clear.
    assertTrue(bar.blurScale > content.blurScale)
    assertTrue(bar.blurScale > floating.blurScale)

    // A control hides the least of anything on screen: it is too small to frost its background and
    // still read as glass, so the bend at its edge has to do the identifying instead.
    assertTrue(interactive.blurScale < bar.blurScale)
    assertTrue(interactive.blurScale < modal.blurScale)
    assertTrue(interactive.blurScale < floating.blurScale)
    assertTrue(interactive.depthEffect)

    // Displacement is bounded by the size of the thing displacing, not by how important it is. The
    // control is the smallest surface here — 44 dp — so its absolute reach is the smallest too, and
    // asserting the opposite is how it grew a coloured halo the first time round.
    assertTrue(interactive.refractionAmount < floating.refractionAmount)

    // A bar is a wide shallow sheet: it reaches far in and displaces gently, or the whole top of
    // the screen swims while a list moves under it.
    assertTrue(bar.refractionAmount < bar.refractionHeight)
    assertFalse(bar.depthEffect)
    assertFalse(bar.dispersion)

    // Seven samples per pixel are only ever worth it on something small.
    assertTrue(interactive.dispersion)
    assertFalse(modal.dispersion)

    // The two heavy roles, and they are heavy for the same reason: both stand over a page that is
    // still fully sharp and both are the only thing separating themselves from it. Everything else
    // in this file stands over something already soft — a wash, a bar's own fill, a control's
    // track — and gets to be nearly clear.
    assertTrue(modal.blurScale > interactive.blurScale)
    assertTrue(bar.blurScale > interactive.blurScale)

    // Only the surfaces that float carry a shadow; a bar is flush with the screen.
    assertEquals(0f, bar.shadowAlpha, 0f)
    assertTrue(floating.shadowAlpha > 0f)

    // Every preset transmits more colour than it receives, or glass reads as grey plastic.
    listOf(bar, floating, interactive, modal).forEach { assertTrue(it.vibrancy > 1f) }

    // Content is the one role allowed to trade backdrop resolution for cost: what it refracts is
    // the ambient wash, and a gradient downsampled to half comes back up identical. Everything that
    // stands over *content* — sharp text scrolling under a bar — keeps full resolution, because
    // half resolution there reads as smear.
    assertTrue(content.backdropResolution < 1f)
    listOf(bar, floating, interactive, modal).forEach {
      assertEquals(1f, it.backdropResolution, 0f)
    }
  }

  @Test
  fun glassOptics_pressOnlyDeepensTheSurfacesAFingerCanReach() {
    assertEquals(0f, GlassDefaults.optics(GlassRole.Bar).pressedDepthBoost, 0f)
    assertEquals(0f, GlassDefaults.optics(GlassRole.Modal).pressedDepthBoost, 0f)
    assertTrue(GlassDefaults.optics(GlassRole.Interactive).pressedDepthBoost > 0f)
    assertTrue(GlassDefaults.optics(GlassRole.Floating).pressedDepthBoost > 0f)
  }

  @Test
  fun glassOptics_sanitizedCorrectsMalformedCustomValues() {
    val sanitized = GlassOptics(
      blurScale = Float.NaN,
      refractionHeight = (-4).dp,
      refractionAmount = Float.NaN.dp,
      depthEffect = true,
      dispersion = true,
      vibrancy = Float.NaN,
      highlightWidth = (-1).dp,
      highlightAlpha = 4f,
      highlightAngle = Float.NaN,
      innerShadowRadius = (-2).dp,
      innerShadowAlpha = -1f,
      shadowRadius = Float.NEGATIVE_INFINITY.dp,
      shadowAlpha = Float.NaN,
      pressedDepthBoost = 9f,
      backdropResolution = Float.NaN,
    ).sanitized()

    assertEquals(0f, sanitized.blurScale, 0f)
    assertEquals(0.dp, sanitized.refractionHeight)
    assertEquals(0.dp, sanitized.refractionAmount)
    assertEquals(1f, sanitized.vibrancy, 0f)
    assertEquals(0.dp, sanitized.highlightWidth)
    assertEquals(1f, sanitized.highlightAlpha, 0f)
    assertEquals(45f, sanitized.highlightAngle, 0f)
    assertEquals(0.dp, sanitized.innerShadowRadius)
    assertEquals(0f, sanitized.innerShadowAlpha, 0f)
    assertEquals(0.dp, sanitized.shadowRadius)
    assertEquals(0f, sanitized.shadowAlpha, 0f)
    assertEquals(2f, sanitized.pressedDepthBoost, 0f)
    assertEquals(1f, sanitized.backdropResolution, 0f)
  }

  @Test
  fun glassOptics_sanitizedIsIdentityOnEveryPreset() {
    GlassRole.entries.forEach { role ->
      val optics = GlassDefaults.optics(role)
      assertEquals("$role was written outside the range the renderer honours", optics, optics.sanitized())
    }
  }

  @Test
  fun clampGlassUnit_treatsAMalformedIntensityAsAbsentRatherThanFull() {
    assertEquals(0f, clampGlassUnit(Float.NaN), 0f)
    assertEquals(0f, clampGlassUnit(-3f), 0f)
    assertEquals(1f, clampGlassUnit(3f), 0f)
    assertEquals(0.5f, clampGlassUnit(0.5f), 0f)
  }

  @Test
  fun surfaceSide_isGuessedFromLuminance_untilTheAppSaysOtherwise() {
    // The ordinary case, and the one that must not change: an app with an opaque page gets the
    // answer it always got, without declaring anything.
    assertTrue(GlassDefaults.darkSurface(null, Color(0xFF101114)))
    assertFalse(GlassDefaults.darkSurface(null, Color(0xFFF4F4F6)))

    // The case this exists for. A design whose surfaces are translucent films files a film in
    // `surface`; luminance does not look at alpha, so a white at ten percent reads as 1.0 and a
    // black app is called a light one. Every pane of glass takes the wrong branch at once.
    val film = Color.White.copy(alpha = 0.10f)
    assertFalse(
      "guessed light from a film that is painted over black: that is the whole reason for this",
      GlassDefaults.darkSurface(null, film),
    )

    // Declared, it is believed — both ways round, so this is a hand-over and not a dark switch.
    assertTrue(GlassDefaults.darkSurface(true, film))
    assertFalse(GlassDefaults.darkSurface(false, film))
    assertFalse(GlassDefaults.darkSurface(false, Color(0xFF101114)))
  }

  @Test
  fun anIndicatorKeepsItsEdgeWhenNobodyIsTouchingIt() {
    // No floor is the behaviour every surface had before this existed: the rim and the shadow go
    // exactly where the bend goes, which for a press-driven surface means nowhere at rest.
    assertEquals(0f, glassEdgeDepth(0f, 0f), 0f)
    assertEquals(1f, glassEdgeDepth(0f, 1f), 0f)

    // With one, the edge starts there and the press takes it the rest of the way. The point is the
    // first of these: a tab indicator with no rim over a bar standing on a bright cover has no
    // shape of its own at all.
    assertEquals(0.5f, glassEdgeDepth(0.5f, 0f), 0.0001f)
    assertEquals(0.75f, glassEdgeDepth(0.5f, 0.5f), 0.0001f)
    assertEquals(1f, glassEdgeDepth(0.5f, 1f), 0.0001f)

    // A floor is never a ceiling, and nonsense from a hand-written optics is corrected rather than
    // propagated into a RenderEffect chain.
    assertEquals(1f, glassEdgeDepth(2f, 0f), 0f)
    assertEquals(0.5f, glassEdgeDepth(0.5f, Float.NaN), 0.0001f)
  }
}
