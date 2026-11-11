package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassBlurFloor
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassSizeGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ricetta del materiale `Liquid` (F1A.5): è pura, quindi si prova tutta sul JVM. Ogni prova dice una regola
 * del piano (3.3, tabella dei bersagli di F1A) o una scelta scritta nel KDoc di [glassRecipe]: se una di queste
 * cambia, cambia l'aspetto di ogni vetro, e deve cambiare di proposito.
 */
class GlassRecipeTest {

  private val default = GlassLiquidProfile.Default

  /** Un colore di Compose tiene l'alfa in 8 bit: mezzo livello di scarto e' il suo arrotondamento. */
  private val ColourStep = 0.5f / 255f + 1e-4f

  private fun button(dark: Boolean = false, profile: GlassLiquidProfile = default) = glassRecipe(
    role = GlassRole.Interactive, widthDp = 44f, heightDp = 44f, darkSide = dark,
    optics = GlassDefaults.buttonOptics(), profile = profile,
  )

  private fun capsule(dark: Boolean = false, profile: GlassLiquidProfile = default) =
    glassRecipe(role = GlassRole.Floating, widthDp = 156f, heightDp = 56f, darkSide = dark, profile = profile)

  private fun menu(dark: Boolean = false, profile: GlassLiquidProfile = default) = glassRecipe(
    role = GlassRole.Modal, widthDp = 170f, heightDp = 192f, darkSide = dark,
    optics = GlassDefaults.panelOptics(compact = true), profile = profile,
  )

  private fun panel(dark: Boolean = false, profile: GlassLiquidProfile = default) = glassRecipe(
    role = GlassRole.Modal, widthDp = 330f, heightDp = 380f, darkSide = dark,
    optics = GlassDefaults.panelOptics(), profile = profile,
  )

  // ---- La taglia ---------------------------------------------------------------------------------------------

  @Test
  fun theRimDoesNotGrowWithTheSlab() {
    val recipes = listOf(button(), capsule(), menu(), panel())
    for (recipe in recipes) {
      assertEquals(0.6f, recipe.rimWidthDp, 0f)
      assertEquals(7f, recipe.reflexDepthDp, 0f)
      assertEquals(0.32f, recipe.rimDark.alpha, ColourStep)
      assertEquals(0.12f, recipe.reflexStrength, 1e-6f)
    }
    // Lo stesso vale sul lato scuro, e con ogni variante dei grandi.
    for (growth in listOf(
      GlassLiquidGrowths.None, GlassLiquidGrowths.Step, GlassLiquidGrowths.StepForWideShadow, GlassLiquidGrowths.TwoSteps,
    )) {
      val grown = default.copy(growth = growth)
      assertEquals(button(profile = grown).rimWidthDp, panel(profile = grown).rimWidthDp, 0f)
      assertEquals(button(dark = true, profile = grown).reflexDepthDp, panel(dark = true, profile = grown).reflexDepthDp, 0f)
    }
  }

  @Test
  fun frostAndShadowGrowWithTheSizeRuleOfTheEngine() {
    // Sotto 64 dp di lato corto il guadagno è zero: tasto e capsula hanno la stessa smerigliatura (la D, 4,5
    // dp) e, da 44 dp in su, la stessa ombra (la B: 34 dp, una componente).
    assertEquals(4.5f, button().blurDp, 1e-4f)
    assertEquals(4.5f, capsule().blurDp, 1e-4f)
    assertEquals(34f, capsule().shadowAmbientRadiusDp, 1e-4f)
    assertEquals(34f, button().shadowAmbientRadiusDp, 1e-4f)
    assertEquals(0f, capsule().shadowContactAlpha, 0f)

    // Un pannello col lato corto oltre 320 dp ha il guadagno pieno: un passo in più è una volta e mezza la
    // sfocatura; l'ombra larga cresce appena (raggi e scostamento per 1,3, alfa per 1,2).
    val big = panel()
    assertEquals(1f, glassSizeGain(330f), 0f)
    assertEquals(6.75f, big.blurDp, 1e-4f)
    assertEquals(34f * 1.3f, big.shadowAmbientRadiusDp, 1e-4f)
    assertEquals(0f, big.shadowContactRadiusDp, 0f)
    assertEquals(6.7f * 1.3f, big.shadowOffsetYDp, 1e-4f)
    assertEquals(0.09f * 1.2f, big.shadowAmbientAlpha, 1e-6f)

    // In mezzo, il guadagno è quello della regola di oggi, non uno scalino.
    val gain = glassSizeGain(170f)
    assertTrue(gain > 0.3f && gain < 0.45f)
    assertEquals(4.5f * (1f + 0.5f * gain), menu().blurDp, 1e-4f)
    assertEquals(34f * (1f + 0.3f * gain), menu().shadowAmbientRadiusDp, 1e-4f)

    // «Come una barra», il passo tarato sull'ombra corta e «due passi in più» sono lo stesso conto con altri numeri.
    assertEquals(4.5f, panel(profile = default.copy(growth = GlassLiquidGrowths.None)).blurDp, 1e-4f)
    assertEquals(34f, panel(profile = default.copy(growth = GlassLiquidGrowths.None)).shadowAmbientRadiusDp, 1e-4f)
    assertEquals(68f, panel(profile = default.copy(growth = GlassLiquidGrowths.Step)).shadowAmbientRadiusDp, 1e-4f)
    assertEquals(9f, panel(profile = default.copy(growth = GlassLiquidGrowths.TwoSteps)).blurDp, 1e-4f)
    assertEquals(102f, panel(profile = default.copy(growth = GlassLiquidGrowths.TwoSteps)).shadowAmbientRadiusDp, 1e-4f)

    // Un'ottica che spegne la regola (`sizeResponse` zero: lo scrim di un modale) resta quella di una barra.
    val scrim = glassRecipe(
      GlassRole.Modal, 400f, 800f, darkSide = false, optics = GlassDefaults.optics(GlassRole.Modal).copy(sizeResponse = 0f),
    )
    assertEquals(4.5f, scrim.blurDp, 1e-4f)
    assertEquals(34f, scrim.shadowAmbientRadiusDp, 1e-4f)
  }

  @Test
  fun theLensIsAFractionOfTheShortSideAndTheDomeLeavesTheLargeOnes() {
    // La media: banda 0,24 del lato corto, spostamento 0,9 della banda, negato.
    assertEquals(0.24f * 44f, button().thicknessDp, 1e-4f)
    assertEquals(-0.24f * 44f * 0.9f, button().refractionDp, 1e-4f)
    assertEquals(0.5f, button().dome, 1e-6f)
    // Il tetto della banda: su un pannello la lente è un bordo.
    assertEquals(GlassLiquidLensBandMaxDp, panel().thicknessDp, 0f)
    assertEquals(0f, panel().dome, 0f)
    assertEquals(0f, menu().dome, 0f)
    assertTrue(capsule().dome > 0f && capsule().dome < button().dome)
    // Mai oltre mezzo lato corto, qualunque profilo arrivi: lì le bande di due lati opposti si incontrano.
    val absurd = default.copy(lens = GlassLiquidLens(band = 0.9f, bend = 1f, dome = 0f))
    assertEquals(22f, button(profile = absurd).thicknessDp, 0f)
    // L'ombreggiatura del corpo ha lo stesso limite.
    assertEquals(22f, button().bodyShadeDepthDp, 0f)
    assertEquals(22f, panel().bodyShadeDepthDp, 0f)
    val thin = glassRecipe(GlassRole.Floating, 120f, 30f, darkSide = false)
    assertEquals(15f, thin.bodyShadeDepthDp, 0f)
    // Sotto 44 dp il riflesso rientra al più per 0,16 del lato corto (vedi i piccolissimi).
    assertEquals(0.16f * 30f, thin.reflexDepthDp, 1e-5f)
  }

  @Test
  fun thePaddingCoversTheBlurTheLensDoesNot() {
    // Di serie (4,5 dp) la banda della lente copre la sfocatura ovunque: nessun margine. Con 12 dp il tasto
    // (banda 10,56) ne aveva 1,44.
    assertEquals(0f, button().paddingDp, 0f)
    assertEquals(0f, capsule().paddingDp, 0f)
    assertEquals(0f, panel().paddingDp, 0f)
    assertEquals(12f - 10.56f, button(profile = default.copy(frost = GlassLiquidFrosts.Radius12)).paddingDp, 1e-4f)
    // Con la smerigliatura larga il margine cresce, e resta quello a qualità piena mentre la qualità scende.
    val wide = default.copy(frost = GlassLiquidFrosts.Radius24)
    val full = button(profile = wide)
    val moving = glassRecipe(
      GlassRole.Interactive, 44f, 44f, darkSide = false, optics = GlassDefaults.buttonOptics(), quality = 0.25f,
      profile = wide,
    )
    assertEquals(24f - 10.56f, full.paddingDp, 1e-4f)
    assertEquals(full.paddingDp, moving.paddingDp, 0f)
    assertTrue(moving.blurDp < full.blurDp)
  }

  // ---- Il lato -----------------------------------------------------------------------------------------------

  @Test
  fun theSideChoosesEdgeVeilAndShadow() {
    val light = button(dark = false)
    val dark = button(dark = true)
    // Sul chiaro un filo scuro e il riflesso; sullo scuro il riflesso e un filo chiaro che copre (la B di serie).
    assertTrue(light.drawsRim)
    assertTrue(light.drawsReflex)
    assertTrue(dark.drawsRim)
    assertTrue(dark.drawsReflex)
    assertEquals(0f, light.rimDark.red, 0f)
    assertEquals(1f, dark.rimDark.red, 0f)
    assertEquals(0.34f, dark.rimDark.alpha, ColourStep)
    assertEquals(-0.25f, dark.rimLift, 0f)
    // Sul corpo scuro la luce che somma è più forte che sul chiaro.
    assertEquals(0.12f, light.reflexStrength, 1e-6f)
    assertEquals(0.20f, dark.reflexStrength, 1e-6f)
    assertEquals(GlassLiquidVeils.Ios27.color, light.veilColor)
    assertEquals(GlassLiquidVeils.DarkLighter.color, dark.veilColor)
    assertEquals(GlassLiquidVeils.DarkLighter.shade, dark.bodyShade)
    assertTrue(dark.shadowAmbientAlpha > light.shadowAmbientAlpha)
    // La smerigliatura e la lente non dipendono dal lato.
    assertEquals(light.blurDp, dark.blurDp, 0f)
    assertEquals(light.thicknessDp, dark.thicknessDp, 0f)
    assertEquals(light.refractionDp, dark.refractionDp, 0f)
  }

  @Test
  fun theDarkVeilDeepensWithTheSizeOnlyOnTheDarkSide() {
    // S1-ter, difetto 2: i grandi scuri portano testo, e con il velo dei piccoli il testo secondario non si
    // legge. Il tasto ha il velo dei piccoli, menu e pannello quello dei grandi.
    assertEquals(GlassLiquidVeils.DarkLighter.color, button(dark = true).veilColor)
    assertEquals(GlassLiquidVeils.DarkLarge.color, menu(dark = true).veilColor)
    assertEquals(GlassLiquidVeils.DarkLarge.color, panel(dark = true).veilColor)
    // Il passaggio è continuo e monotono col lato corto: nessuno scatto fra un tasto largo e una card piccola.
    var previous = Float.POSITIVE_INFINITY
    var side = 40f
    while (side <= 110f) {
      val grey = glassRecipe(GlassRole.Modal, 300f, side, darkSide = true, optics = GlassDefaults.panelOptics()).veilColor.red
      assertTrue("il velo a $side dp", grey <= previous)
      if (previous.isFinite()) assertTrue("uno scatto a $side dp", previous - grey <= 3.5f / 255f)
      previous = grey
      side += 1f
    }
    assertEquals(0f, glassLiquidLargeVeilGain(GlassLiquidDarkLargeStartDp), 0f)
    assertEquals(0.5f, glassLiquidLargeVeilGain((GlassLiquidDarkLargeStartDp + GlassLiquidDarkLargeEndDp) / 2f), 1e-6f)
    assertEquals(1f, glassLiquidLargeVeilGain(GlassLiquidDarkLargeEndDp), 0f)
    assertEquals(0f, glassLiquidLargeVeilGain(Float.NaN), 0f)
    // Alla stessa alfa: la pagina passa quanto sotto un tasto.
    assertEquals(GlassLiquidVeils.DarkLighter.color.alpha, GlassLiquidVeils.DarkLarge.color.alpha, 0f)
    // Sul chiaro il velo non cambia con la taglia: il testo vi si legge come oggi.
    assertEquals(button().veilColor, panel().veilColor)
    assertEquals(button().veilAdapt, panel().veilAdapt, 0f)
    // Con il velo dei grandi uguale a quello dei piccoli, niente cambia con la taglia neanche sullo scuro.
    val flat = default.copy(darkLargeVeil = default.darkVeil)
    assertEquals(button(dark = true).veilColor, panel(dark = true, profile = flat).veilColor)
    // Una pellicola fissa prende il posto del velo a ogni taglia.
    val film = glassRecipe(
      GlassRole.Modal, 330f, 380f, darkSide = true, optics = GlassDefaults.panelOptics(), film = Color(0x9E202024),
    )
    assertEquals(0f, film.veilColor.alpha, 0f)
  }

  @Test
  fun theTinyOnesKeepTheWireAndSoftenReflexAndShadow() {
    fun dot(side: Float, dark: Boolean = false) = glassRecipe(
      GlassRole.Interactive, side, side, darkSide = dark, optics = GlassDefaults.buttonOptics(),
    )
    // Da 44 dp in su niente cambia.
    assertEquals(0.12f, dot(44f).reflexStrength, 1e-6f)
    assertEquals(7f, dot(44f).reflexDepthDp, 0f)
    assertEquals(34f, dot(44f).shadowAmbientRadiusDp, 1e-4f)
    // A 26 dp (l'indicatore di sincronizzazione): il filo è lo stesso, il riflesso più tenue e meno profondo,
    // l'ombra in scala col lato.
    val small = dot(26f)
    assertEquals(0.6f, small.rimWidthDp, 0f)
    assertEquals(dot(44f).rimDark, small.rimDark)
    val floor = GlassLiquidTinyReflexFloor
    val t = (26f - GlassLiquidTinyReflexFromDp) / (GlassLiquidTinyBelowDp - GlassLiquidTinyReflexFromDp)
    assertEquals(0.12f * (floor + (1f - floor) * t), small.reflexStrength, 1e-6f)
    assertEquals(26f * GlassLiquidTinyReflexDepthFraction, small.reflexDepthDp, 1e-5f)
    assertEquals(34f * 26f / 44f, small.shadowAmbientRadiusDp, 1e-4f)
    assertEquals(6.7f * 26f / 44f, small.shadowOffsetYDp, 1e-4f)
    // L'alfa dell'ombra no: è più piccola, non più chiara.
    assertEquals(dot(44f).shadowAmbientAlpha, small.shadowAmbientAlpha, 0f)
    // Sotto 20 dp il riflesso resta al suo minimo; e vale anche sul lato scuro.
    assertEquals(0.12f * floor, dot(16f).reflexStrength, 1e-6f)
    assertEquals(0.20f * floor, dot(16f, dark = true).reflexStrength, 1e-6f)
    // Continuo: crescendo il lato riflesso, rientro e ombra non calano mai.
    var last = dot(10f)
    var side = 11f
    while (side <= 60f) {
      val next = dot(side)
      assertTrue(next.reflexStrength >= last.reflexStrength)
      assertTrue(next.reflexDepthDp >= last.reflexDepthDp)
      assertTrue(next.shadowAmbientRadiusDp >= last.shadowAmbientRadiusDp)
      last = next
      side += 1f
    }
  }

  // ---- intensity, opticalDepth, qualità, pressione (piano 3.3) -----------------------------------------------

  @Test
  fun zeroIntensityIsTheSharpBackdrop() {
    val gone = glassRecipe(GlassRole.Floating, 156f, 56f, darkSide = false, intensity = 0f)
    assertEquals(0f, gone.materialize, 0f)
    assertEquals(1f, gone.mix, 0f)
    assertEquals(1f, gone.saturation, 0f)
    assertFalse(gone.drawsShadow)
    assertNull(gone.shadowSpec())
    assertFalse(gone.drawsRim)
    assertFalse(gone.drawsReflex)
    // Il raggio no: resta quello pieno, e con lui il margine della cattura.
    assertEquals(capsule().blurDp, gone.blurDp, 0f)
    assertEquals(capsule().paddingDp, gone.paddingDp, 0f)
    assertEquals(capsule().thicknessDp, gone.thicknessDp, 0f)
  }

  @Test
  fun intensityRampsTheMaterialWithoutTouchingTheRadius() {
    val half = glassRecipe(GlassRole.Floating, 156f, 56f, darkSide = false, intensity = 0.5f)
    val full = capsule()
    assertEquals(0.5f, half.materialize, 0f)
    assertEquals(0.5f, half.mix, 1e-6f)
    assertEquals(1.25f, half.saturation, 1e-6f)
    assertEquals(full.blurDp, half.blurDp, 0f)
    assertEquals(full.shadowAmbientAlpha * 0.5f, half.shadowAmbientAlpha, 1e-6f)
    assertEquals(full.shadowAmbientRadiusDp, half.shadowAmbientRadiusDp, 0f)
    // Velo, lente e luce li scala `materialize` nello shader: qui restano pieni.
    assertEquals(full.veilColor, half.veilColor)
    assertEquals(full.refractionDp, half.refractionDp, 0f)
    assertEquals(full.rimDark, half.rimDark)
    // Con la mescola a riposo sopra zero la rampa arriva lì, non a zero.
    val mixed = default.copy(frost = GlassLiquidFrosts.WideMixed)
    assertEquals(0.12f, capsule(profile = mixed).mix, 1e-6f)
    assertEquals(0.56f, glassRecipe(GlassRole.Floating, 156f, 56f, false, intensity = 0.5f, profile = mixed).mix, 1e-6f)
    // Fuori dall'intervallo e non finita: come oggi, si ferma agli estremi.
    assertEquals(1f, glassRecipe(GlassRole.Floating, 156f, 56f, false, intensity = 7f).materialize, 0f)
    assertEquals(0f, glassRecipe(GlassRole.Floating, 156f, 56f, false, intensity = Float.NaN).materialize, 0f)
  }

  @Test
  fun opticalDepthMovesLensEdgeAndShadowButNotVeilNorFrost() {
    val flat = glassRecipe(GlassRole.Floating, 156f, 56f, darkSide = false, opticalDepth = 0f)
    val full = capsule()
    assertEquals(0f, flat.refractionDp, 0f)
    assertEquals(full.thicknessDp, flat.thicknessDp, 0f)
    assertEquals(full.paddingDp, flat.paddingDp, 0f)
    assertEquals(0f, flat.rimDark.alpha, 0f)
    assertEquals(0f, flat.reflexStrength, 0f)
    assertFalse(flat.drawsShadow)
    assertEquals(full.veilColor, flat.veilColor)
    assertEquals(full.blurDp, flat.blurDp, 0f)
    assertEquals(full.mix, flat.mix, 0f)
    assertEquals(1f, flat.materialize, 0f)
    // `edgeFloor`: un indicatore tiene bordo e ombra anche a profondità zero, come oggi.
    val floored = glassRecipe(
      GlassRole.Floating, 156f, 56f, darkSide = false, opticalDepth = 0f,
      optics = GlassDefaults.optics(GlassRole.Floating).copy(edgeFloor = 0.5f),
    )
    assertEquals(0f, floored.refractionDp, 0f)
    assertEquals(full.rimDark.alpha * 0.5f, floored.rimDark.alpha, ColourStep)
    assertEquals(full.shadowAmbientAlpha * 0.5f, floored.shadowAmbientAlpha, 1e-6f)
  }

  @Test
  fun qualityScalesShapeAndBlurButNeverTheVeil() {
    val full = button()
    val low = glassRecipe(
      GlassRole.Interactive, 44f, 44f, darkSide = false, optics = GlassDefaults.buttonOptics(), quality = 0f,
    )
    assertEquals(full.blurDp * FluidGlassBlurFloor, low.blurDp, 1e-5f)
    assertEquals(0f, low.refractionDp, 0f)
    assertEquals(0f, low.rimDark.alpha, 0f)
    assertFalse(low.drawsShadow)
    assertEquals(full.veilColor, low.veilColor)
    assertEquals(full.bodyShade, low.bodyShade)
    assertEquals(full.mix, low.mix, 0f)
    // La frangia è dei soli tasti, e se ne va per prima.
    assertEquals(GlassLiquidDispersion, full.dispersion, 0f)
    assertEquals(0f, capsule().dispersion, 0f)
    val moving = glassRecipe(
      GlassRole.Interactive, 44f, 44f, darkSide = false, optics = GlassDefaults.buttonOptics(), quality = 0.75f,
    )
    assertEquals(0f, moving.dispersion, 0f)
  }

  @Test
  fun pressureDeepensTheLensAsToday() {
    val optics = GlassDefaults.buttonOptics()
    val rest = button()
    val down = glassRecipe(GlassRole.Interactive, 44f, 44f, darkSide = false, optics = optics, pressed = 1f)
    assertEquals(rest.refractionDp * (1f + optics.pressedDepthBoost), down.refractionDp, 1e-4f)
    assertEquals(rest.thicknessDp, down.thicknessDp, 0f)
    assertEquals(GlassLiquidGlowPeak, down.glowPeak, 0f)
    assertEquals(GlassLiquidGlowFill, down.glowFill, 0f)
  }

  // ---- L'ottica di oggi come attenuatore ---------------------------------------------------------------------

  @Test
  fun everyDefaultOpticsGivesTheSameGlass() {
    // È la sottocategoria F1A-d: tasto, capsula, menu e pannello sono lo stesso vetro in quattro spessori. Alla
    // stessa taglia, ogni ottica di serie dà gli stessi numeri, salvo ciò che l'ottica dice di proposito: la
    // frangia, la pressione, l'ombra dove il ruolo non la vuole, la risoluzione della cattura.
    // Ognuna col ruolo con cui l'engine la usa.
    val all = stockOptics()
    val reference = glassRecipe(GlassRole.Floating, 200f, 100f, darkSide = false)
    for ((role, optics) in all) {
      val recipe = glassRecipe(role, 200f, 100f, darkSide = false, optics = optics)
      assertEquals(reference.blurDp, recipe.blurDp, 0f)
      assertEquals(reference.saturation, recipe.saturation, 1e-6f)
      assertEquals(reference.thicknessDp, recipe.thicknessDp, 0f)
      assertEquals(reference.refractionDp, recipe.refractionDp, 0f)
      assertEquals(reference.dome, recipe.dome, 0f)
      assertEquals(reference.veilColor, recipe.veilColor)
      assertEquals(reference.rimDark, recipe.rimDark)
      assertEquals(reference.reflexStrength, recipe.reflexStrength, 0f)
      if (optics.shadowAlpha > 0f) {
        assertEquals(reference.shadowAmbientAlpha, recipe.shadowAmbientAlpha, 0f)
      } else {
        // `Bar` e `Content` stanno nella pagina, non sopra: nessuna ombra, come oggi.
        assertFalse(recipe.drawsShadow)
      }
    }
  }

  /** Le ottiche di serie, ognuna col ruolo con cui l'engine la usa. */
  private fun stockOptics() = listOf(
    GlassRole.Bar to GlassDefaults.optics(GlassRole.Bar),
    GlassRole.Floating to GlassDefaults.optics(GlassRole.Floating),
    GlassRole.Interactive to GlassDefaults.optics(GlassRole.Interactive),
    GlassRole.Modal to GlassDefaults.optics(GlassRole.Modal),
    GlassRole.Content to GlassDefaults.optics(GlassRole.Content),
    GlassRole.Interactive to GlassDefaults.buttonOptics(),
    GlassRole.Content to GlassDefaults.stackedContentOptics(),
    GlassRole.Modal to GlassDefaults.panelOptics(),
    GlassRole.Modal to GlassDefaults.panelOptics(compact = true),
    GlassRole.Modal to GlassDefaults.panelOptics(overSharpPage = true),
    GlassRole.Modal to GlassDefaults.panelOptics(compact = true, overSharpPage = true),
  )

  @Test
  fun aHandWrittenOpticsThatAsksForMoreFrostGetsMore() {
    // Il pannello del lettore di Fluidify: `Modal` con `blurScale` 7, il doppio del ruolo. Prima della F1A.5c il
    // materiale nuovo lo ignorava (attenuatore a uno) e il pannello usciva meno smerigliato del vetro di ieri.
    val fluidify = GlassDefaults.panelOptics().copy(blurScale = 7f)
    val asked = glassRecipe(GlassRole.Modal, 360f, 420f, darkSide = false, optics = fluidify)
    assertEquals(3.5f, glassLiquidRoleBlurScale(GlassRole.Modal), 0f)
    assertEquals(panel().blurDp * 2f, asked.blurDp, 1e-4f)
    // La crescita è della smerigliatura e basta: lente, velo, bordo e ombra restano quelli del ruolo.
    assertEquals(panel().thicknessDp, asked.thicknessDp, 0f)
    assertEquals(panel().veilColor, asked.veilColor)
    assertEquals(panel().rimDark, asked.rimDark)
    assertEquals(panel().shadowAmbientAlpha, asked.shadowAmbientAlpha, 0f)
    // La risoluzione della cattura segue la sfocatura del ruolo, cresciuta: 9 dp.
    assertEquals(glassRecipe(GlassRole.Modal, 0f, 0f, false, optics = fluidify).captureScale, asked.captureScale, 0f)
    assertTrue(asked.captureScale < panel().captureScale)

    // Monotona in `blurScale`, dal vetro limpido al tetto; e mai oltre il tetto.
    var previous = -1f
    var scale = 0f
    while (scale <= 16f) {
      val blur = glassRecipe(
        GlassRole.Modal, 360f, 420f, darkSide = false, optics = GlassDefaults.panelOptics().copy(blurScale = scale),
      ).blurDp
      assertTrue("blurScale $scale", blur >= previous)
      previous = blur
      scale += 0.25f
    }
    assertEquals(panel().blurDp * GlassLiquidFrostRequestMax, previous, 1e-4f)
    assertEquals(GlassLiquidFrostRequestMax, glassOpticsFrost(100f, 1.8f), 0f)
    assertEquals(2f, glassOpticsFrost(7f, 3.5f), 0f)
    assertEquals(1f, glassOpticsFrost(3.5f, 3.5f), 0f)
    assertEquals(1f, glassOpticsFrost(1f, 3.5f), 0f)
    assertEquals(0.5f, glassOpticsFrost(GlassLiquidFullBlurScale / 2f, 3.5f), 1e-6f)
    assertEquals(0f, glassOpticsFrost(Float.NaN, 3.5f), 0f)
    assertEquals(0f, glassOpticsFrost(-2f, 3.5f), 0f)

    // Nessuna ottica di serie chiede più del suo ruolo: le soglie sono il massimo delle ottiche di ogni ruolo.
    for ((role, optics) in stockOptics()) {
      assertEquals("$role", 1f, glassOpticsFrost(optics.blurScale, glassLiquidRoleBlurScale(role)), 0f)
    }
    assertEquals(5f, glassLiquidRoleBlurScale(GlassRole.Bar), 0f)
    assertEquals(1.8f, glassLiquidRoleBlurScale(GlassRole.Content), 0f)
  }

  @Test
  fun aHandWrittenOpticsThatAsksForLessGetsLess() {
    val base = GlassDefaults.optics(GlassRole.Floating)
    fun with(optics: dev.antigravity.fluidengine.ui.fluid.GlassOptics) =
      glassRecipe(GlassRole.Floating, 156f, 56f, darkSide = false, optics = optics)
    val full = capsule()

    // Zero sfocatura: nessun raggio (e nessun margine); metà della soglia: metà raggio.
    assertEquals(0f, with(base.copy(blurScale = 0f)).blurDp, 0f)
    assertEquals(full.blurDp / 2f, with(base.copy(blurScale = GlassLiquidFullBlurScale / 2f)).blurDp, 1e-5f)
    // Zero spostamento: lente piatta, la banda resta. Zero altezza: nessuna banda.
    assertEquals(0f, with(base.copy(refractionAmount = 0.dp)).refractionDp, 0f)
    assertEquals(full.thicknessDp, with(base.copy(refractionAmount = 0.dp)).thicknessDp, 0f)
    assertEquals(0f, with(base.copy(refractionHeight = 0.dp)).thicknessDp, 0f)
    // `vibrancy` 1 resta «nessun filtro».
    assertEquals(1f, with(base.copy(vibrancy = 1f)).saturation, 0f)
    assertEquals(1.5f, full.saturation, 1e-6f)
    // Nessun bordo, nessuna ombra.
    val bare = with(base.copy(highlightAlpha = 0f, shadowAlpha = 0f))
    assertFalse(bare.drawsRim)
    assertFalse(bare.drawsReflex)
    assertFalse(bare.drawsShadow)
    // Metà della soglia: metà forza.
    val faint = with(base.copy(shadowAlpha = GlassLiquidFullShadowAlpha / 2f, highlightAlpha = GlassLiquidFullHighlightAlpha / 2f))
    assertEquals(full.shadowAmbientAlpha / 2f, faint.shadowAmbientAlpha, 1e-6f)
    assertEquals(full.rimDark.alpha / 2f, faint.rimDark.alpha, ColourStep)
    assertEquals(full.reflexStrength / 2f, faint.reflexStrength, 1e-6f)

    assertEquals(1f, glassOpticsDimmer(5f, 0.8f), 0f)
    assertEquals(0.5f, glassOpticsDimmer(0.4f, 0.8f), 1e-6f)
    assertEquals(0f, glassOpticsDimmer(-1f, 0.8f), 0f)
    assertEquals(0f, glassOpticsDimmer(Float.NaN, 0.8f), 0f)
  }

  @Test
  fun theDefaultRoleOpticsAreUsedWhenNoneIsGiven() {
    val byRole = glassRecipe(GlassRole.Interactive, 44f, 44f, darkSide = false)
    val explicit = glassRecipe(
      GlassRole.Interactive, 44f, 44f, darkSide = false, optics = GlassDefaults.optics(GlassRole.Interactive),
    )
    assertEquals(explicit.dispersion, byRole.dispersion, 0f)
    assertEquals(explicit.shadowAmbientAlpha, byRole.shadowAmbientAlpha, 0f)
    // `Bar` di serie non ha ombra.
    assertFalse(glassRecipe(GlassRole.Bar, 400f, 56f, darkSide = false).drawsShadow)
  }

  // ---- Le varianti ---------------------------------------------------------------------------------------------

  @Test
  fun theEdgeVariantsGiveWhatTheySay() {
    // iOS 26: nessun filo, lobi in diagonale, più stretti e più forti.
    val ios26 = capsule(profile = default.copy(lightEdge = GlassLiquidEdges.Ios26))
    assertFalse(ios26.drawsRim)
    assertTrue(ios26.reflexDirX < 0f && ios26.reflexDirY < 0f)
    assertEquals(1f, ios26.reflexDirX * ios26.reflexDirX + ios26.reflexDirY * ios26.reflexDirY, 1e-4f)
    assertTrue(ios26.reflexHalfWidth < capsule().reflexHalfWidth)
    assertTrue(ios26.reflexStrength > capsule().reflexStrength)
    // Oggi: il filetto pieno sopra e sotto (`rimLift` −1), nessuna luce che somma.
    val today = capsule(profile = default.copy(lightEdge = GlassLiquidEdges.Today))
    assertEquals(-1f, today.rimLift, 0f)
    assertEquals(0.28f, today.rimDark.alpha, ColourStep)
    assertFalse(today.drawsReflex)
    assertTrue(today.drawsRim)
    // Tema scuro: il filo chiaro è un filo che copre, non una luce; la B della tavola al 10%, il solo riflesso
    // senza filo.
    val wire = capsule(dark = true, profile = default.copy(darkEdge = GlassLiquidEdges.DarkReflexAndWire))
    assertTrue(wire.drawsRim)
    assertEquals(1f, wire.rimDark.red, 0f)
    assertEquals(0.10f, wire.rimDark.alpha, ColourStep)
    val reflexOnly = capsule(dark = true, profile = default.copy(darkEdge = GlassLiquidEdges.DarkReflex))
    assertFalse(reflexOnly.drawsRim)
    assertTrue(reflexOnly.drawsReflex)
    val darkToday = capsule(dark = true, profile = default.copy(darkEdge = GlassLiquidEdges.DarkToday))
    assertFalse(darkToday.drawsRim)
    assertEquals(0.5f, darkToday.reflexStrength, 1e-6f)
    assertEquals(0f, darkToday.reflexHalo, 0f)
  }

  @Test
  fun theFrostVariantsGiveWhatTheySay() {
    assertEquals(0f, capsule().mix, 0f)
    assertEquals(24f, capsule(profile = default.copy(frost = GlassLiquidFrosts.Radius24)).blurDp, 0f)
    val mixed = capsule(profile = default.copy(frost = GlassLiquidFrosts.WideMixed))
    assertEquals(36f, mixed.blurDp, 0f)
    assertEquals(0.12f, mixed.mix, 1e-6f)
    assertEquals(4.5f, capsule().blurDp, 0f)
    assertEquals(12f, capsule(profile = default.copy(frost = GlassLiquidFrosts.Radius12)).blurDp, 0f)
    assertEquals(8f, capsule(profile = default.copy(frost = GlassLiquidFrosts.Middle)).blurDp, 0f)
    // La cattura non scende sotto il pavimento, e una lastra limpida resta a risoluzione piena.
    assertEquals(GlassLiquidMinCaptureScale, mixed.captureScale, 0f)
    val clearOptics = GlassDefaults.optics(GlassRole.Floating).copy(blurScale = 0f)
    assertEquals(1f, glassRecipe(GlassRole.Floating, 156f, 56f, false, optics = clearOptics).captureScale, 0f)
    assertTrue(capsule().captureScale in GlassLiquidMinCaptureScale..1f)
  }

  @Test
  fun theShadowVariantsGiveWhatTheySay() {
    val standard = capsule()
    // Di serie l'ombra B, iOS 26: una componente sola, larga.
    assertEquals(GlassLiquidShadows.Ios26, standard.shadowSpec())
    val wide = standard.shadowSpec()
    assertNotNull(wide)
    assertEquals(0f, wide!!.contactAlpha, 0f)
    assertEquals(34.dp, wide.ambientRadius)
    // Sullo scuro la stessa geometria con l'alfa doppia.
    assertEquals(GlassLiquidShadows.Ios26Dark, capsule(dark = true).shadowSpec())
    // iOS 27: corta, due componenti.
    assertEquals(GlassLiquidShadows.Ios27, capsule(profile = default.copy(lightShadow = GlassLiquidShadows.Ios27)).shadowSpec())
    // Oggi: il colore del lato moltiplica l'alfa del ruolo.
    val optics = GlassDefaults.optics(GlassRole.Floating)
    val today = capsule(
      profile = default.copy(lightShadow = GlassLiquidShadows.today(optics.shadowRadius, optics.shadowAlpha, false)),
    )
    assertEquals(20f, today.shadowAmbientRadiusDp, 0f)
    assertEquals(0.7f, today.shadowAmbientAlpha, 1e-6f)
    assertEquals(0.16f, today.shadowColor.alpha, ColourStep)
    // Il tondo: di serie l'ombra della barra; «come Apple» un quarto sotto i 44 dp, piena da 56 dp.
    assertEquals(standard.shadowAmbientAlpha, button().shadowAmbientAlpha, 0f)
    val apple = default.copy(smallShadow = GlassLiquidProfile.SmallShadowLikeApple)
    assertEquals(standard.shadowAmbientAlpha * 0.25f, button(profile = apple).shadowAmbientAlpha, 1e-6f)
    assertEquals(standard.shadowAmbientAlpha, capsule(profile = apple).shadowAmbientAlpha, 1e-6f)
    val between = glassRecipe(GlassRole.Floating, 120f, 50f, darkSide = false, profile = apple)
    assertEquals(standard.shadowAmbientAlpha * (0.25f + 0.75f * 0.5f), between.shadowAmbientAlpha, 1e-6f)
    // I raggi restano quelli: sono le maschere registrate.
    assertEquals(standard.shadowAmbientRadiusDp, button(profile = apple).shadowAmbientRadiusDp, 0f)
  }

  @Test
  fun theLensAndVeilVariantsGiveWhatTheySay() {
    val light = button(profile = default.copy(lens = GlassLiquidLenses.Light))
    val strong = button(profile = default.copy(lens = GlassLiquidLenses.Strong))
    assertTrue(light.thicknessDp < button().thicknessDp && button().thicknessDp < strong.thicknessDp)
    assertTrue(-light.refractionDp < -button().refractionDp && -button().refractionDp < -strong.refractionDp)
    assertTrue(light.dome < button().dome && button().dome < strong.dome)
    assertEquals(GlassLiquidVeils.Ios26.color, capsule(profile = default.copy(lightVeil = GlassLiquidVeils.Ios26)).veilColor)
    assertEquals(
      GlassLiquidVeils.DarkDeeper.color,
      button(dark = true, profile = default.copy(darkVeil = GlassLiquidVeils.DarkDeeper)).veilColor,
    )
  }

  @Test
  fun aFixedFilmReplacesTheVeilAndATintSitsOverIt() {
    val film = Color(0xFFFBFBFB).copy(alpha = 0.52f)
    val fixed = glassRecipe(GlassRole.Floating, 156f, 56f, darkSide = false, film = film)
    assertEquals(0f, fixed.veilColor.alpha, 0f)
    assertEquals(0f, fixed.veilAdapt, 0f)
    assertEquals(film, fixed.tintColor)
    assertEquals(0f, fixed.tintMode, 0f)
    // L'ombreggiatura resta: è il volume, non il colore.
    assertEquals(capsule().bodyShade, fixed.bodyShade)

    val red = Color(0xF2DE000C)
    val tinted = glassRecipe(GlassRole.Floating, 156f, 56f, darkSide = false, tint = red)
    assertEquals(capsule().veilColor, tinted.veilColor)
    assertEquals(red, tinted.tintColor)
    assertEquals(1f, tinted.tintMode, 0f)
    assertEquals(GlassLiquidTintFollow, tinted.tintFollow, 0f)

    val plain = capsule()
    assertEquals(0f, plain.tintColor.alpha, 0f)
    assertEquals(0f, plain.tintMode, 0f)
  }

  @Test
  fun theClearVariantKeepsTheRadiusAndDropsFrostAndVeil() {
    val clear = glassRecipe(GlassRole.Floating, 156f, 56f, darkSide = false, variant = GlassLiquidVariant.Clear)
    val regular = capsule()
    assertEquals(1f, clear.mix, 0f)
    assertEquals(regular.blurDp, clear.blurDp, 0f)
    assertEquals(regular.paddingDp, clear.paddingDp, 0f)
    assertEquals(0f, clear.veilColor.alpha, 0f)
    assertEquals(GlassLiquidClearDim, clear.dim, 0f)
    assertEquals(0f, regular.dim, 0f)
    assertEquals(regular.refractionDp, clear.refractionDp, 0f)
    assertEquals(regular.rimDark, clear.rimDark)
    assertEquals(regular.shadowAmbientAlpha, clear.shadowAmbientAlpha, 0f)
  }

  // ---- La forma -------------------------------------------------------------------------------------------------

  @Test
  fun theRecipeRewritesTheHolderItIsGiven() {
    val holder = GlassRecipe()
    val first = glassRecipe(GlassRole.Floating, 156f, 56f, darkSide = false, tint = Color.Red, out = holder)
    assertSame(holder, first)
    assertEquals(1f, holder.tintMode, 0f)
    val second = glassRecipe(
      GlassRole.Modal, 330f, 380f, darkSide = true, optics = GlassDefaults.panelOptics(), intensity = 0.3f, out = holder,
    )
    assertSame(holder, second)
    // Niente resta della chiamata di prima: è lo stesso risultato di un contenitore nuovo.
    val fresh = glassRecipe(
      GlassRole.Modal, 330f, 380f, darkSide = true, optics = GlassDefaults.panelOptics(), intensity = 0.3f,
    )
    assertEquals(fresh.tintMode, holder.tintMode, 0f)
    assertEquals(fresh.tintColor, holder.tintColor)
    assertEquals(fresh.veilColor, holder.veilColor)
    assertEquals(fresh.rimDark, holder.rimDark)
    assertEquals(fresh.blurDp, holder.blurDp, 0f)
    assertEquals(fresh.mix, holder.mix, 0f)
    assertEquals(fresh.shadowSpec(), holder.shadowSpec())
  }

  @Test
  fun aSlabWithNoSizeDoesNotPoisonTheNumbers() {
    for (side in listOf(0f, -4f, Float.NaN, Float.POSITIVE_INFINITY)) {
      val recipe = glassRecipe(GlassRole.Floating, side, 56f, darkSide = false)
      for (value in listOf(
        recipe.blurDp, recipe.mix, recipe.saturation, recipe.paddingDp, recipe.captureScale, recipe.thicknessDp,
        recipe.refractionDp, recipe.dome, recipe.bodyShadeDepthDp, recipe.reflexDepthDp, recipe.shadowAmbientRadiusDp,
        recipe.shadowAmbientAlpha, recipe.shadowContactAlpha,
      )) {
        assertTrue("un numero non finito con il lato $side", value.isFinite())
      }
      assertTrue(recipe.thicknessDp >= 0f)
    }
  }

  @Test
  fun theShadowDefaultsOfTheShadowFileAreTheProfileOnes() {
    // `GlassShadowDefaults` è rimasto come rimando: gli stessi oggetti, non una seconda copia dei numeri.
    assertSame(GlassLiquidShadows.Ios27, GlassShadowDefaults.Ios27)
    assertSame(GlassLiquidShadows.Ios26, GlassShadowDefaults.Ios26)
    assertSame(GlassLiquidGrowths.Step.shadow, GlassShadowDefaults.GrowthStep)
  }

  @Test
  fun theShippedProfileIsAlessiosChoices() {
    // Le sei scelte dell'11 novembre 2026, lettera per lettera (`GlassLiquidProfile.Shipped`).
    val shipped = GlassLiquidProfile.Shipped
    assertSame(GlassLiquidEdges.Ios27, shipped.lightEdge) // 1 · bordo A
    assertSame(GlassLiquidVeils.Ios27, shipped.lightVeil) // 2 · velo A
    assertSame(GlassLiquidShadows.Ios26, shipped.lightShadow) // 3 · ombra B
    assertSame(GlassLiquidShadows.Ios26Dark, shipped.darkShadow)
    assertSame(GlassLiquidFrosts.Ios26, shipped.frost) // 4 · smerigliatura D
    assertSame(GlassLiquidEdges.DarkWire, shipped.darkEdge) // 5 · bordo scuro B, più deciso
    assertSame(GlassLiquidVeils.DarkLighter, shipped.darkVeil) // 6 · velo scuro A, sui piccoli
    assertSame(GlassLiquidVeils.DarkLarge, shipped.darkLargeVeil)
    assertSame(GlassLiquidLenses.Medium, shipped.lens)
    assertSame(GlassLiquidGrowths.StepForWideShadow, shipped.growth)
    assertEquals(1f, shipped.smallShadow, 0f)
    // Il «prima» delle tavole è quello di F1A.5, e da 44 dp in su la ricetta lo rende come allora.
    val before = GlassLiquidProfile.BeforeChoices
    assertSame(GlassLiquidShadows.Ios27, before.lightShadow)
    assertSame(GlassLiquidEdges.DarkReflex, before.darkEdge)
    assertSame(GlassLiquidFrosts.Radius12, before.frost)
    assertSame(GlassLiquidGrowths.Step, before.growth)
    assertEquals(before.darkVeil, before.darkLargeVeil)
    val oldButton = button(profile = before)
    assertEquals(12f, oldButton.blurDp, 0f)
    assertEquals(1.44f, oldButton.paddingDp, 1e-4f)
    assertEquals(GlassLiquidMinCaptureScale, oldButton.captureScale, 0f)
    assertEquals(GlassLiquidVeils.DarkLighter.color, panel(dark = true, profile = before).veilColor)
  }
}
