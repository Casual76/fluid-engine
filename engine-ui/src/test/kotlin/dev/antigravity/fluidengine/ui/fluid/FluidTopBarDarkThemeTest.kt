package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.theme.FluidStatusBarSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G1, rilievi A-R2, B-R3 e D-R9: sul tema scuro la barra in alto ospita nello specchio di F2.3c. Il lato di riposo è lo
 * scuro; sopra un contenuto chiaro gira verso il chiaro, con una decisione sola per striscia, titolo e tasti, e le icone
 * di sistema la seguono nei due versi.
 */
class FluidTopBarDarkThemeTest {

  private val dark = Color(0xFF000000)
  private val light = Color(0xFFF2F2F7)

  @Test
  fun theVeilGoesFromTheDarkBackgroundToTheLightOneWithTheSide() {
    assertEquals("a riposo, il fondo scuro", dark, fluidScrollEdgeDarkThemeVeil(light, dark, 1f))
    assertEquals("girata, il fondo chiaro", light, fluidScrollEdgeDarkThemeVeil(light, dark, 0f))
    assertEquals("senza misura, il fondo scuro", dark, fluidScrollEdgeDarkThemeVeil(light, dark, Float.NaN))
    val half = fluidScrollEdgeDarkThemeVeil(light, dark, 0.5f)
    assertTrue("a metà, in mezzo: $half", half.red > dark.red && half.red < light.red)
  }

  @Test
  fun theLightThemeBarIsUnchanged() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = true, inking = true)
    val shade = FluidScrollEdgeShade(Modifier, { 0f }, host = state)
    // Il lato chiaro: l'inchiostro del tema. Nessun velo da cambiare.
    assertFalse(shade.darkTheme)
    assertEquals(null, shade.veil)
  }

  @Test
  fun theStatusBarIconsFollowTheBarBothWays() {
    val side = FluidStatusBarSide()
    val owner = Any()
    // Nessuno lo dice: quelle del tema.
    assertTrue(side.lightStatusBars(themeLightBars = true))
    assertFalse(side.lightStatusBars(themeLightBars = false))
    // Tema chiaro, la barra scurisce: icone chiare (come da F2.3b).
    side.claim(owner, darkUnder = true)
    assertFalse(side.lightStatusBars(themeLightBars = true))
    // Tema scuro, la barra a riposo: icone chiare.
    assertFalse(side.lightStatusBars(themeLightBars = false))
    // Tema scuro, la barra gira verso il chiaro: icone scure (prima restavano chiare sul chiaro).
    side.claim(owner, darkUnder = false)
    assertTrue(side.lightStatusBars(themeLightBars = false))
    // La schermata se ne va: tornano quelle del tema.
    side.release(owner)
    assertFalse(side.lightStatusBars(themeLightBars = false))
    assertTrue(side.lightStatusBars(themeLightBars = true))
  }
}
