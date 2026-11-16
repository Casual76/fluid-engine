package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Il carattere del viaggio del piano flottante (F3.9): le due curve sulla fase, la ricetta che porta il vetro verso la
 * luce, l'alone e la smerigliatura, il rigonfiamento, il margine della cattura, le manopole. Le regole che contano:
 * a livello zero (alla partenza e da [GlassJourneyLightSettle] in poi) **niente cambia di un bit**, così il passaggio
 * palco → riposo resta invisibile; e nessuna curva salta. I punti delle curve sono gli stessi che controlla
 * `tools/glassbench/carattere.py selftest`, la copia portata nel banco.
 */
class GlassJourneyTest {

  private val apple = GlassLiquidJourneys.Apple

  /** Un colore di Compose tiene i canali in 8 bit: mezzo livello di scarto è il suo arrotondamento. */
  private val colourStep = 0.5f / 255f + 1e-4f

  private fun button(dark: Boolean = false) = glassRecipe(
    role = GlassRole.Interactive, widthDp = 44f, heightDp = 44f, darkSide = dark,
    optics = GlassDefaults.buttonOptics().sanitized(), profile = GlassLiquidProfile.Shipped,
  )

  /** Ogni campo della ricetta, per confrontarne due al bit (come `GlassRecipeTest`). */
  private fun fields(recipe: GlassRecipe): Map<String, Any?> =
    GlassRecipe::class.java.declaredFields
      .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
      .onEach { it.isAccessible = true }
      .associate { it.name to it.get(recipe) }

  // ---- Le curve ------------------------------------------------------------------------------------------------

  @Test
  fun theLightRisesHoldsAndSettlesAtThePinnedPhases() {
    assertEquals(0f, apple.light(0f), 0f)
    assertEquals(0.5f, apple.light(GlassJourneyLightRise / 2f), 1e-5f)
    assertEquals(1f, apple.light(0.3f), 0f)
    assertEquals(1f, apple.light(GlassJourneyLightHold), 0f)
    // La discesa ha la coda lunga del render: a metà della rampa 0,3125, non 0,5.
    assertEquals(0.3125f, apple.light((GlassJourneyLightHold + GlassJourneyLightSettle) / 2f), 1e-5f)
    assertEquals(0f, apple.light(GlassJourneyLightSettle), 0f)
    assertEquals(0f, apple.light(1f), 0f)
    // Gli stessi punti di `carattere.py selftest`.
    assertEquals(0.5f, apple.light(0.055f), 1e-5f)
    assertEquals(0.3125f, apple.light(0.7f), 1e-5f)
    assertEquals(0f, apple.light(0.9f), 0f)
    // La forma del render (misure.md, quota della strada fra riposo e colmo): 0,81 al 58%, 0,38 al 67%, 0,23 al 72%.
    assertEquals(0.81f, apple.light(0.58f), 0.03f)
    assertEquals(0.38f, apple.light(0.67f), 0.06f)
    assertEquals(0.23f, apple.light(0.72f), 0.03f)
    assertEquals(1f, journeyLongTail(0f), 0f)
    assertEquals(0f, journeyLongTail(1f), 0f)
  }

  @Test
  fun aShortJourneyRisesInNoLessThanItsMinimum() {
    // 300 ms (un cambio di contesto di serie): la salita dura 70 ms, metà a 35 ms; resta piena fino a metà viaggio.
    assertEquals(0.5f, apple.light(35f / 300f, settleMs = 300f), 1e-4f)
    assertEquals(1f, apple.light(GlassJourneyLightRiseMinMs / 300f, settleMs = 300f), 0f)
    assertEquals(1f, apple.light(GlassJourneyLightHold, settleMs = 300f), 0f)
    // Un viaggio lungo prende la fase di Apple: da 640 ms in su la salita è lo 0,11.
    assertEquals(0.5f, apple.light(0.055f, settleMs = 1000f), 1e-5f)
    assertEquals(apple.light(0.03f), apple.light(0.03f, settleMs = 700f), 0f)
    // Mai oltre la tenuta, anche per un viaggio brevissimo; senza riposo è la sola fase.
    assertEquals(1f, apple.light(GlassJourneyLightHold, settleMs = 50f), 0f)
    assertEquals(apple.light(0.05f), apple.light(0.05f, settleMs = Float.NaN), 0f)
    // Sul banco: il primo fotogramma a 60 Hz di un viaggio da 300 ms sale di meno di un quinto.
    assertTrue(apple.light(16.7f / 300f, settleMs = 300f) < 0.2f)
  }

  @Test
  fun theSwellPeaksWhereAppleSwellsAndIsGoneBeforeRest() {
    assertEquals(0f, apple.swell(0f), 0f)
    assertEquals(0.5f, apple.swell(0.225f), 1e-5f)
    assertEquals(1f, apple.swell(GlassJourneySwellPeak), 0f)
    assertEquals(0f, apple.swell(GlassJourneySwellSettle), 0f)
    assertEquals(0f, apple.swell(1f), 0f)
    // La barra a 16:02 (classe B, misure.md): metà del colmo al 20% e al 70%, un quarto all'80%.
    assertEquals(0.41f, apple.swell(0.2f), 0.02f)
    assertEquals(0.5f, apple.swell(0.7f), 0.02f)
    assertEquals(0.22f, apple.swell(0.8f), 0.03f)
  }

  @Test
  fun noCurveJumpsAndBothStartFromRestWithZeroSlope() {
    val steps = 2000
    var prevLight = apple.light(0f)
    var prevSwell = apple.swell(0f)
    for (i in 1..steps) {
      val p = i / steps.toFloat()
      val light = apple.light(p)
      val swell = apple.swell(p)
      // Il passo più ripido della luce è la salita in 0,11: 1,5 / 0,11 per unità di fase.
      assertTrue("luce a $p", abs(light - prevLight) <= 1.5f / GlassJourneyLightRise / steps + 1e-5f)
      assertTrue("gonfio a $p", abs(swell - prevSwell) <= 1.5f / GlassJourneySwellPeak / steps + 1e-5f)
      prevLight = light
      prevSwell = swell
    }
    // Derivata zero alla partenza: il primo fotogramma a 120 Hz di un viaggio da 300 ms (fase 0,028) è appena acceso.
    assertTrue(apple.light(1f / 36f) < 0.17f)
    assertTrue(apple.swell(1f / 36f) < 0.02f)
  }

  @Test
  fun anInterruptedJourneyRestartsFromItsLevel() {
    for (entry in listOf(0f, 0.3f, 0.7f, 1f)) {
      assertEquals(entry, apple.light(0f, entry), 0f)
      assertEquals(entry, apple.swell(0f, entry), 0f)
      // Non scende mai sotto l'entrata nella salita, e arriva comunque a zero.
      assertTrue(apple.light(0.05f, entry) >= entry - 1e-6f)
      assertEquals(0f, apple.light(1f, entry), 0f)
      assertEquals(0f, apple.swell(1f, entry), 0f)
    }
    assertEquals(0f, apple.light(Float.NaN), 0f)
    assertEquals(0.4f, apple.light(0f, 0.4f), 0f)
  }

  @Test
  fun thePhaseIsElapsedOverTheVisibleRest() {
    assertEquals(0f, glassJourneyPhase(0.0, 300.0), 0f)
    assertEquals(0.5f, glassJourneyPhase(150.0, 300.0), 0f)
    assertEquals(1f, glassJourneyPhase(900.0, 300.0), 0f)
    assertEquals(0f, glassJourneyPhase(-10.0, 300.0), 0f)
    // Nessun riposo (sole comparse) o un tempo non finito: il viaggio è già arrivato.
    assertEquals(1f, glassJourneyPhase(10.0, 0.0), 0f)
    assertEquals(1f, glassJourneyPhase(Double.NaN, 300.0), 0f)
  }

  // ---- La ricetta ----------------------------------------------------------------------------------------------

  @Test
  fun atLevelZeroTheRecipeIsTheRestingOneToTheBit() {
    for (dark in listOf(false, true)) {
      for (journey in listOf(GlassLiquidJourneys.Off, GlassLiquidJourneys.Gentle, GlassLiquidJourneys.Half, apple)) {
        val rest = fields(button(dark))
        for (level in listOf(0f, -1f, Float.NaN)) {
          val got = fields(glassJourneyRecipe(button(dark), journey, level, dark))
          for ((name, value) in rest) assertEquals("$journey $level $name", value, got[name])
        }
      }
      // E il carattere spento non cambia niente nemmeno al colmo.
      val off = fields(glassJourneyRecipe(button(dark), GlassLiquidJourneys.Off, 1f, dark))
      for ((name, value) in fields(button(dark))) assertEquals("off $name", value, off[name])
    }
  }

  @Test
  fun atThePeakLikeAppleTheGlassIsAWhiteFrostedBlockWithLightUnderIt() {
    val rest = button()
    val peak = glassJourneyRecipe(button(), apple, 1f, darkSide = false)
    // Il velo è il colore della luce, pieno; il filo scuro e l'adattività del velo non ci sono più.
    assertEquals(1f, peak.veilColor.alpha, colourStep)
    assertEquals(0f, peak.veilAdapt, 0f)
    assertEquals(0f, peak.bodyShade.alpha, colourStep)
    assertEquals(rest.lightColor.red, peak.veilColor.red, colourStep)
    assertEquals(rest.lightColor.green, peak.veilColor.green, colourStep)
    assertEquals(0f, peak.rimDark.alpha, colourStep)
    // La smerigliatura: raggio per 1 + frost, mescola tutta smerigliata.
    assertEquals(rest.blurDp * (1f + apple.frost), peak.blurDp, 1e-4f)
    assertEquals(0f, peak.mix, 0f)
    // L'alone: il colore della luce all'alfa di Apple, il contatto spento.
    assertEquals(rest.lightColor.red, peak.shadowColor.red, colourStep)
    assertEquals(apple.glow, peak.shadowAmbientAlpha, 1e-6f)
    assertEquals(0f, peak.shadowContactAlpha, 0f)
    // Cio' che non è del carattere non si muove: lente, riflesso, raggi e scostamento dell'ombra.
    assertEquals(rest.refractionDp, peak.refractionDp, 0f)
    assertEquals(rest.reflexStrength, peak.reflexStrength, 0f)
    assertEquals(rest.shadowAmbientRadiusDp, peak.shadowAmbientRadiusDp, 0f)
    assertEquals(rest.shadowOffsetYDp, peak.shadowOffsetYDp, 0f)
  }

  @Test
  fun theDarkSideTakesItsShareOfTheLight() {
    val rest = button(dark = true)
    val peak = glassJourneyRecipe(button(dark = true), apple, 1f, darkSide = true)
    val share = apple.light * apple.darkLight
    assertEquals(rest.veilColor.alpha + (1f - rest.veilColor.alpha) * share, peak.veilColor.alpha, colourStep)
    assertEquals(rest.veilColor.red + (rest.lightColor.red - rest.veilColor.red) * share, peak.veilColor.red, colourStep)
    assertTrue(peak.veilColor.red < 0.9f)
  }

  @Test
  fun halfwayTheGlowNeverDarkensMoreThanTheShadow() {
    val rest = button()
    val shadow = rest.shadowAmbientAlpha * rest.shadowColor.alpha
    for (i in 1..9) {
      val e = i / 10f
      val r = glassJourneyRecipe(button(), apple, e, darkSide = false)
      // La parte scura premoltiplicata dell'alone: (1 − bianco) · alfa. Mai più dell'ombra di riposo, e cala con e.
      val darkPart = (1f - r.shadowColor.red) * r.shadowAmbientAlpha
      assertEquals("e=$e", (1f - e) * shadow, darkPart, 0.004f)
      assertEquals("e=$e", e * apple.glow, r.shadowColor.red * r.shadowAmbientAlpha, 0.004f)
    }
  }

  @Test
  fun theClearVariantStaysClearButTakesTheGlow() {
    val clear = glassRecipe(
      role = GlassRole.Interactive, widthDp = 44f, heightDp = 44f, darkSide = false,
      optics = GlassDefaults.buttonOptics().sanitized(), variant = GlassLiquidVariant.Clear,
      profile = GlassLiquidProfile.Shipped,
    )
    val veil = clear.veilColor
    val mix = clear.mix
    val blur = clear.blurDp
    val rim = clear.rimDark
    glassJourneyRecipe(clear, apple, 1f, darkSide = false, variant = GlassLiquidVariant.Clear)
    assertEquals(veil, clear.veilColor)
    assertEquals(mix, clear.mix, 0f)
    assertEquals(blur, clear.blurDp, 0f)
    assertEquals(rim, clear.rimDark)
    assertEquals(apple.glow, clear.shadowAmbientAlpha, 1e-6f)
  }

  // ---- Rigonfiamento e margini -----------------------------------------------------------------------------------

  @Test
  fun theSwellOutsetsEachSideByHalfTheFractionOfTheShortSide() {
    assertEquals(3.3f, glassJourneySwellDp(44f, 1f, apple), 1e-5f)
    assertEquals(1.65f, glassJourneySwellDp(44f, 0.5f, apple), 1e-5f)
    assertEquals(0f, glassJourneySwellDp(44f, 0f, apple), 0f)
    assertEquals(0f, glassJourneySwellDp(44f, 1f, GlassLiquidJourneys.Off), 0f)
    assertEquals(0f, glassJourneySwellDp(Float.NaN, 1f, apple), 0f)
    // Il tasto di Apple al colmo: +15% di altezza (misure.md, classe B).
    assertEquals(0.15f, 2f * glassJourneySwellDp(44f, 1f, apple) / 44f, 1e-6f)
  }

  @Test
  fun aSwollenSlabIsOffsetTheSameEverywhere() {
    val capsule = FluidForm.Slab(Rect(10f, 20f, 110f, 64f), FluidCornerRadii.all(22f), smoothing = 0.6f)
    val swollen = glassJourneySwollen(capsule, 3f)
    assertEquals(Rect(7f, 17f, 113f, 67f), swollen.frame)
    // Una capsula resta una capsula: raggio = metà del lato corto nuovo.
    assertEquals(FluidCornerRadii.all(25f), swollen.cornerRadii)
    assertEquals(0.6f, swollen.smoothing, 0f)
    // Un angolo stretto cresce della stessa distanza (concentrico), mai oltre il mezzo lato.
    val card = FluidForm.Slab(Rect(0f, 0f, 200f, 100f), FluidCornerRadii(8f, 8f, 0f, 60f))
    assertEquals(FluidCornerRadii(12f, 12f, 4f, 54f), glassJourneySwollen(card, 4f).cornerRadii)
    // Senza rigonfiamento è la stessa lastra, non una copia.
    assertSame(capsule, glassJourneySwollen(capsule, 0f))
    assertSame(capsule, glassJourneySwollen(capsule, Float.NaN))
  }

  @Test
  fun theCaptureMarginHoldsThePeakBlurOnlyForThePlane() {
    val optics = GlassDefaults.buttonOptics().sanitized()
    val profile = GlassLiquidProfile.Shipped
    val plain = glassTravelPaddingDp(GlassRole.Interactive, optics, profile = profile)
    // Senza carattere (menu, Fluid-physics fuori dal piano) il margine è quello di sempre.
    assertEquals(plain, glassTravelPaddingDp(GlassRole.Interactive, optics, profile = profile, journey = null), 0f)
    assertEquals(plain, glassTravelPaddingDp(GlassRole.Interactive, optics, profile = profile, journey = GlassLiquidJourneys.Off), 0f)
    assertEquals(plain * (1f + apple.frost), glassTravelPaddingDp(GlassRole.Interactive, optics, profile = profile, journey = apple), 1e-4f)
    // Il raggio al colmo ci sta: quello della ricetta in viaggio per 1 + frost, mai oltre il margine.
    val peak = glassJourneyRecipe(button(), apple, 1f, darkSide = false)
    assertTrue(peak.blurDp <= glassTravelPaddingDp(GlassRole.Interactive, optics, profile = profile, journey = apple) + 1e-4f)
    // La portata dell'ombra: invariata senza carattere, e l'alone non la accorcia.
    val reach = glassTravelShadowReachDp(optics, gain = 0f, profile = profile)
    assertEquals(reach, glassTravelShadowReachDp(optics, gain = 0f, profile = profile, journey = null), 0f)
    assertTrue(glassTravelShadowReachDp(optics, gain = 0f, profile = profile, journey = apple) >= reach)
  }

  // ---- Profilo e manopole ----------------------------------------------------------------------------------------

  @Test
  fun theIntensitiesAreAppleHalfAndAQuarter() {
    assertTrue(GlassLiquidJourneys.Off.isOff)
    assertFalse(apple.isOff)
    assertEquals(GlassJourneySwellApple, apple.swell, 0f)
    assertEquals(apple.light / 2f, GlassLiquidJourneys.Half.light, 0f)
    assertEquals(apple.frost / 4f, GlassLiquidJourneys.Gentle.frost, 0f)
    assertEquals(apple.darkLight, GlassLiquidJourneys.Gentle.darkLight, 0f)
    assertSame(GlassLiquidJourneys.Shipped, GlassLiquidProfile.Shipped.journey)
    // La proposta: a metà, col rigonfiamento misurato di Apple.
    assertEquals(GlassLiquidJourneys.Half.copy(swell = GlassJourneySwellApple), GlassLiquidJourneys.Shipped)
    // Il viaggio dei menu (la scelta B) non c'entra.
    assertSame(GlassLiquidTravels.Soft, GlassLiquidProfile.Shipped.travel)
  }

  @Test
  fun theKnobsPickTheIntensityAndTheNumbers() {
    val shipped = GlassLiquidProfile.Shipped
    for ((name, journey) in listOf(
      "off" to GlassLiquidJourneys.Off, "gentle" to GlassLiquidJourneys.Gentle,
      "half" to GlassLiquidJourneys.Half, "apple" to apple,
    )) {
      val tuned = GlassLiquidTuning.apply(shipped, mapOf("journey" to name))
      assertSame(name, journey, tuned.profile.journey)
      assertEquals(shipped.copy(journey = journey), tuned.profile)
    }
    val numbers = GlassLiquidTuning.apply(
      shipped,
      mapOf("journey" to "apple", "travellight" to "0,4", "travelglow" to "0.2", "travelfrost" to "1", "travelswell" to "0.1"),
    ).profile.journey
    assertEquals(apple.copy(light = 0.4f, glow = 0.2f, frost = 1f, swell = 0.1f), numbers)
    val rejected = GlassLiquidTuning.apply(shipped, mapOf("travelswell" to "0.5", "journey" to "bianco"))
    assertEquals(setOf("travelswell", "journey"), rejected.rejected.keys)
    assertSame(shipped, rejected.profile)
    assertEquals(listOf("off", "gentle", "half", "apple"), GlassLiquidTuning.knobs["journey"])
    assertEquals(listOf("0..0.3"), GlassLiquidTuning.knobs["travelswell"])
  }

  @Test
  fun aWhiteLightOnAColouredVeilKeepsTheVeilHue() {
    // Un velo tinto (lato chiaro, adattivo) va verso la luce in linea retta: a metà luce è a metà strada.
    val recipe = button().apply { veilColor = Color(0.2f, 0.4f, 0.8f, 0.5f) }
    // Il colore d'ingresso è già in 8 bit (0,5 di alfa è 128/255): l'atteso parte da lì, e lo scarto è un arrotondamento.
    val before = recipe.veilColor
    glassJourneyRecipe(recipe, apple.copy(light = 0.5f), 1f, darkSide = false)
    assertEquals(before.red + (1f - before.red) * 0.5f, recipe.veilColor.red, colourStep)
    assertEquals(before.blue + (1f - before.blue) * 0.5f, recipe.veilColor.blue, colourStep)
    assertEquals(before.alpha + (1f - before.alpha) * 0.5f, recipe.veilColor.alpha, colourStep)
    assertEquals(0.6f, recipe.veilColor.red, 2f * colourStep)
    assertEquals(0.75f, recipe.veilColor.alpha, 2f * colourStep)
  }
}
