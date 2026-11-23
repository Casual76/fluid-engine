package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6C.20 · **Il menu del file in scuro sopra la carta bianca** (riprese del 23 novembre 2026: «Elimina» a 1,33:1 su un
 * corpo a 132). La finestra del menu porta nella pellicola la sua quota di scrim, e una pellicola cambiata si legge
 * `Fixed`: prende il posto del velo, e il soffitto del velo scuro dei grandi (F2.3) non la toccava. Ora la pellicola fissa
 * di un grande sul lato scuro prende il soffitto, e il menu (e l'avviso) chiede quello della voce distruttiva
 * ([GlassLiquidMenuDarkCeiling]).
 *
 * La pellicola qui è quella del menu del tema scuro di `FluidTheme`: il grigio del vetro (#4A4A4E) col 34% del contenitore
 * alto del tema (#2C2C2E), al 50%, composto sopra lo scrim nero al 20% (`FluidMenuLayer`, `GlassDefaults.modalTint`).
 * Il corpo piatto è `pellicola · a + sfondo · (1 − a)`, come lo misura il banco; il contrasto è quello del WCAG.
 */
class GlassFilmCeilingTest {

  private val menuFilm: Color = lerp(Color(0xFF4A4A4E), Color(0xFF2C2C2E), 0.34f).copy(alpha = 0.50f)
    .compositeOver(Color.Black.copy(alpha = 0.20f))
  private val destructive = Color(0xFFFF6961)
  private val lightInk = Color(0xFFF2F2F5)

  private fun luma(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

  private fun linear(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

  private fun luminance(c: Color) = 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)

  private fun contrast(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
  }

  /** Il corpo piatto della pellicola [film] della ricetta sopra uno sfondo grigio [background]. */
  private fun body(recipe: GlassRecipe, background: Float): Color {
    val film = recipe.tintColor
    val a = film.alpha
    fun mix(f: Float) = f * a + background * (1 - a)
    return Color(mix(film.red), mix(film.green), mix(film.blue))
  }

  private fun recipe(
    background: Float?,
    dark: Boolean = true,
    role: GlassRole = GlassRole.Modal,
    film: Color = menuFilm,
    filmCeiling: Float = GlassLiquidMenuDarkCeiling,
    profile: GlassLiquidProfile = GlassLiquidProfile.Shipped,
    width: Float = 250f,
    height: Float = 330f,
  ): GlassRecipe {
    val material = GlassAdaptiveMaterial()
    if (background == null) material.unmeasured()
    else material.measure(luminance = background, deviation = 0f, texture = 0f,
      color = Color(background, background, background), spread = profile.adaptive.darkLevelSpread)
    return glassRecipe(role = role, widthDp = width, heightDp = height, darkSide = dark,
      optics = GlassDefaults.panelOptics(compact = true), film = film, profile = profile, adaptive = material,
      filmCeiling = filmCeiling)
  }

  @Test
  fun prima_ilMenuScuroSopraIlBiancoStavaA132() {
    // Senza soffitto (il profilo spento) la pellicola resta com'è: il corpo che le riprese hanno misurato sul Tab S9.
    val off = GlassLiquidProfile.Shipped.copy(adaptive = GlassLiquidAdaptives.Off)
    val before = body(recipe(background = 1f, profile = off), 1f)
    assertEquals(132f, before.red * 255f, 3f)
    assertEquals(1.33, contrast(destructive, before), 0.08)
  }

  @Test
  fun dopo_laVoceDistruttivaSiLeggeComeSopraLaPaginaScura() {
    val after = body(recipe(background = 1f), 1f)
    val red = contrast(destructive, after)
    val ink = contrast(lightInk, after)
    // La pellicola del menu non arriva al soffitto (luma 0,21 contro 0,20) e si fa piena: il corpo è il suo grigio.
    assertEquals(1f, recipe(background = 1f).tintColor.alpha, 1e-4f)
    assertTrue("«Elimina» a $red:1", red >= 4.25)
    assertTrue("testo chiaro a $ink:1", ink >= 10.0)
    // Lo stesso menu sopra la pagina scura del tema (#1C1C1E), dove non cambia niente.
    val page = 0x1C / 255f
    val onDark = body(recipe(background = page), page)
    assertTrue("«Elimina» sulla pagina scura: ${contrast(destructive, onDark)}", contrast(destructive, onDark) >= 4.5)
    assertEquals(menuFilm.alpha, recipe(background = page).tintColor.alpha, 1e-4f)
  }

  @Test
  fun unaPellicolaFissaPrendeIlSoffittoDiSerieDeiGrandi() {
    // Senza il soffitto del menu, la pellicola fissa di un grande sale al soffitto di serie (luma 0,40, testo chiaro 4,5:1).
    for (background in listOf(0.6f, 0.8f, 1f)) {
      val r = recipe(background = background, filmCeiling = Float.NaN)
      assertTrue("sfondo $background", luma(body(r, background)) <= GlassLiquidDarkCeiling + 1e-3f)
      assertTrue(contrast(lightInk, body(r, background)) >= 4.5)
    }
    assertEquals(0.40f, GlassLiquidDarkCeiling, 0f)
  }

  @Test
  fun ilSoffittoNonToccaIlLatoChiaroIPiccoliNeLoSfondoScuro() {
    // Il lato chiaro, uno sfondo già scuro, nessuna misura: la pellicola com'è, al bit.
    assertEquals(menuFilm, recipe(background = 1f, dark = false).tintColor)
    assertEquals(menuFilm, recipe(background = 0.1f).tintColor)
    assertEquals(menuFilm, recipe(background = null).tintColor)
    // Un piccolo che gira (una capsula, `Floating`) non lo prende: gira invece.
    assertEquals(menuFilm, recipe(background = 1f, role = GlassRole.Floating, width = 156f, height = 56f).tintColor)
    // Il soffitto è un soffitto: una pellicola già più densa non si schiarisce.
    val dense = Color(0.05f, 0.05f, 0.06f, 0.98f)
    assertEquals(dense.alpha, recipe(background = 1f, film = dense, filmCeiling = Float.NaN).tintColor.alpha, 1e-4f)
  }

  @Test
  fun laPellicolaChePuoArrivarciSiFermaAlSoffitto() {
    val film = Color(0.08f, 0.08f, 0.09f, 0.5f)
    for (background in listOf(0.5f, 0.75f, 1f)) {
      val a = glassFilmCeilingAlpha(film, background, 0.2f)
      assertEquals("sfondo $background", 0.2f, luma(film) * a + background * (1 - a), 1e-5f)
    }
    // Sotto il soffitto non serve niente; senza misura, o senza soffitto, nemmeno.
    assertEquals(0f, glassFilmCeilingAlpha(film, 0.15f, 0.2f), 0f)
    assertEquals(0f, glassFilmCeilingAlpha(film, Float.NaN, 0.2f), 0f)
    assertEquals(0f, glassFilmCeilingAlpha(film, 1f, 1f), 0f)
    // Una pellicola che non ci scende si fa piena.
    assertEquals(1f, glassFilmCeilingAlpha(Color(0.3f, 0.3f, 0.3f, 0.5f), 1f, 0.2f), 0f)
  }
}
