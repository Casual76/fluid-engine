package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Le manopole del profilo (F1A.9): sono ciò con cui la sessione sul dispositivo fotografa le varianti delle
 * tavole di gusto, quindi devono dare **esattamente** i pezzi dei cataloghi, e senza manopole il profilo deve
 * restare quello di serie (le immagini di riferimento del banco sono quello).
 */
class GlassLiquidTuningTest {

  @Before
  @After
  fun noOverride() {
    installGlassLiquidProfileOverride(null)
  }

  private val shipped = GlassLiquidProfile.Shipped

  @Test
  fun withoutKnobsTheProfileIsTheShippedOne() {
    assertSame(shipped, GlassLiquidProfile.Default)
    val tuned = GlassLiquidTuning.apply(shipped, emptyMap())
    assertSame(shipped, tuned.profile)
    assertTrue(tuned.accepted.isEmpty())
    assertTrue(tuned.rejected.isEmpty())
  }

  @Test
  fun aVariantIsTheCataloguePieceAndNothingElse() {
    val tuned = GlassLiquidTuning.apply(shipped, mapOf("edge" to "ios26"))
    assertSame(GlassLiquidEdges.Ios26, tuned.profile.lightEdge)
    assertEquals(shipped.copy(lightEdge = GlassLiquidEdges.Ios26), tuned.profile)
    assertEquals(mapOf("edge" to "ios26"), tuned.accepted)

    val all = GlassLiquidTuning.apply(
      shipped,
      mapOf(
        "veil" to "ios26", "shadow" to "ios26", "round" to "apple", "frost" to "wide", "lens" to "strong",
        "large" to "two", "darkedge" to "wire", "darkveil" to "deeper",
      ),
    ).profile
    assertSame(GlassLiquidVeils.Ios26, all.lightVeil)
    assertSame(GlassLiquidShadows.Ios26, all.lightShadow)
    assertEquals(GlassLiquidProfile.SmallShadowLikeApple, all.smallShadow, 0f)
    assertSame(GlassLiquidFrosts.WideMixed, all.frost)
    assertSame(GlassLiquidLenses.Strong, all.lens)
    assertSame(GlassLiquidGrowths.TwoSteps, all.growth)
    assertSame(GlassLiquidEdges.DarkReflexAndWire, all.darkEdge)
    assertSame(GlassLiquidVeils.DarkDeeper, all.darkVeil)
  }

  @Test
  fun numbersApplyAfterVariantsWhateverTheOrder() {
    // Il numero arriva prima della variante, ma cambia il pezzo che la variante ha scelto.
    val tuned = GlassLiquidTuning.apply(shipped, linkedMapOf("rimAlpha" to "0,5", "edge" to "today"))
    assertEquals(GlassLiquidEdges.Today.copy(rim = GlassLiquidEdges.Today.rim.copy(alpha = 0.5f)), tuned.profile.lightEdge)
    assertEquals(mapOf("edge" to "today", "rimalpha" to "0.5"), tuned.accepted)
  }

  @Test
  fun eachNumberChangesItsField() {
    fun tune(key: String, value: String) = GlassLiquidTuning.apply(shipped, mapOf(key to value)).profile
    assertEquals(1.2.dp, tune("rimwidth", "1.2").lightEdge.rimWidth)
    assertEquals(1.2.dp, tune("rimwidth", "1.2").darkEdge.rimWidth)
    assertEquals(0.2f, tune("reflex", "0.2").lightEdge.reflexStrength, 0f)
    assertEquals(0.3f, tune("halo", "0.3").lightEdge.reflexHalo, 0f)
    assertEquals(0.6f, tune("veilalpha", "0.6").lightVeil.color.alpha, 0.5f / 255f)
    assertEquals(0f, tune("adapt", "0").lightVeil.adapt, 0f)
    assertEquals(24.dp, tune("blur", "24").frost.blur)
    assertEquals(0.12f, tune("mix", "0.12").frost.mix, 0f)
    assertEquals(1.25f, tune("bend", "1.25").lens.bend, 0f)
    val doubled = tune("shadowscale", "2")
    assertEquals(shipped.lightShadow.contactAlpha * 2f, doubled.lightShadow.contactAlpha, 1e-6f)
    assertEquals(shipped.lightShadow.ambientAlpha * 2f, doubled.lightShadow.ambientAlpha, 1e-6f)
    assertEquals(shipped.darkShadow.ambientAlpha * 2f, doubled.darkShadow.ambientAlpha, 1e-6f)
    assertEquals(shipped.lightShadow.ambientRadius, doubled.lightShadow.ambientRadius)
    assertEquals(Color.White.copy(alpha = 0.1f), tune("darkwire", "0.1").darkEdge.rim)
    assertEquals(0.2f, tune("darkreflex", "0.2").darkEdge.reflexStrength, 0f)
    assertEquals(0.4f, tune("darkveilalpha", "0.4").darkVeil.color.alpha, 0.5f / 255f)
  }

  @Test
  fun whatIsNotRecognisedChangesNothingAndIsReported() {
    val tuned = GlassLiquidTuning.apply(
      shipped,
      mapOf("edge" to "ios25", "blur" to "200", "mix" to "molto", "colore" to "rosso", " Lens " to " LIGHT "),
    )
    assertEquals(shipped.copy(lens = GlassLiquidLenses.Light), tuned.profile)
    assertEquals(mapOf("lens" to "light"), tuned.accepted)
    assertEquals(setOf("edge", "blur", "mix", "colore"), tuned.rejected.keys)
  }

  @Test
  fun everyKnobIsListedWithItsValues() {
    val knobs = GlassLiquidTuning.knobs
    assertEquals(listOf("ios27", "ios26", "today"), knobs["edge"])
    assertEquals(listOf("r12", "r24", "wide", "ios26"), knobs["frost"])
    assertEquals(listOf("0..64"), knobs["blur"])
    // Ogni valore elencato si accetta: l'elenco è quello che la galleria mostra.
    for ((name, values) in knobs) {
      val value = values.first().substringBefore("..")
      assertEquals(name, mapOf(name to value), GlassLiquidTuning.apply(shipped, mapOf(name to value)).accepted)
    }
  }

  @Test
  fun theRecipeReadsTheInstalledProfile() {
    val tuned = GlassLiquidTuning.apply(shipped, mapOf("frost" to "r24", "edge" to "ios26")).profile
    installGlassLiquidProfileOverride(tuned)
    assertSame(tuned, GlassLiquidProfile.Default)
    // La ricetta che non nomina un profilo prende quello installato: è così che lo vede una superficie che nasce.
    val implicit = glassRecipe(role = GlassRole.Floating, widthDp = 156f, heightDp = 56f, darkSide = false)
    val explicit = glassRecipe(role = GlassRole.Floating, widthDp = 156f, heightDp = 56f, darkSide = false, profile = tuned)
    assertEquals(explicit.blurDp, implicit.blurDp, 0f)
    assertEquals(explicit.reflexStrength, implicit.reflexStrength, 0f)
    assertEquals(explicit.rimDark, implicit.rimDark)

    installGlassLiquidProfileOverride(null)
    assertSame(shipped, GlassLiquidProfile.Default)
  }
}
