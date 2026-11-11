package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassLiquidMinSdk
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.field.GlassNodeClipFullSmoothing
import dev.antigravity.fluidengine.ui.glass.field.glassNodeClipCorner
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyKernel
import dev.antigravity.fluidengine.ui.glass.field.GlassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldGeometry
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidTravels
import dev.antigravity.fluidengine.ui.glass.field.GlassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassSlabGeometry
import dev.antigravity.fluidengine.ui.glass.field.glassSlabIsSmall
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import dev.antigravity.fluidengine.ui.glass.field.glassTravelRecipe
import dev.antigravity.fluidengine.ui.glass.field.lerpGlassRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

/**
 * Fluid-physics disegnato dal materiale nuovo (4.0, F1B.1): tutto ciò che è puro.
 *
 * Cinque promesse, ognuna con la sua prova: il livello e il materiale sono una tabella di verità; il piano porta
 * otto pezzi; a riposo i pezzi sono separati e il collo vive solo in viaggio; la ricetta di un viaggio coincide ai
 * due capi con quella della lastra ferma, e a viaggio fermo è quella; il margine della cattura non si muove.
 */
class PhysicsLiquidTest {

  private val density = 3f
  private fun px(dp: Float) = dp * density

  private fun circle(leftDp: Float, topDp: Float, sizeDp: Float = 44f): FluidForm.Slab =
    FluidFormPresets.capsule(Rect(px(leftDp), px(topDp), px(leftDp + sizeDp), px(topDp + sizeDp)))

  private val neutral =
    GlassTint(overlay = Color.Transparent, fallback = Color.Transparent, hairline = Color.Transparent, mode = GlassTintMode.Neutral)

  /** Ogni numero di una ricetta, nell'ordine dei campi: due ricette uguali danno due liste uguali. */
  private fun numbers(r: GlassRecipe): List<Any> = listOf(
    r.saturation, r.blurDp, r.mix, r.paddingDp, r.captureScale, r.thicknessDp, r.refractionDp, r.dome, r.dispersion,
    r.magnify, r.materialize, r.veilColor, r.veilAdapt, r.tintColor, r.tintMode, r.tintFollow, r.bodyShade,
    r.bodyShadeDepthDp, r.bodyShadeLeanX, r.bodyShadeLeanY, r.dim, r.grain, r.aa, r.rimWidthDp, r.rimDark, r.rimFeather,
    r.rimLift, r.reflexDirX, r.reflexDirY, r.reflexHalfWidth, r.reflexOppositeHalfWidth, r.reflexStrength,
    r.reflexDepthDp, r.reflexFloor, r.reflexHalo, r.lightColor, r.glowPeak, r.glowFill, r.shadowContactRadiusDp,
    r.shadowAmbientRadiusDp, r.shadowOffsetXDp, r.shadowOffsetYDp, r.shadowContactAlpha, r.shadowAmbientAlpha,
    r.shadowColor,
  )

  // ---- La scelta del livello e del materiale ---------------------------------------------------------------

  private fun glass(
    sdk: Int = FluidGlassLiquidMinSdk,
    renderEffect: Boolean = true,
    agsl: Boolean = true,
    classic: Boolean = true,
    liquid: Boolean = true,
    tier: FluidPhysicsTier? = null,
    model: FluidGlassModel = FluidGlassModel.Liquid,
  ): PhysicsGlass = resolvePhysicsGlass(
    sdk = sdk,
    renderEffectSupported = renderEffect,
    runtimeShaderSupported = agsl,
    classicHealthy = { classic },
    liquidHealthy = { liquid },
    requestedTier = tier,
    requestedModel = model,
  )

  @Test
  fun theLevelAndTheMaterialAreATruthTable() {
    val full = FluidPhysicsTier.Full
    val balanced = FluidPhysicsTier.Balanced
    val lite = FluidPhysicsTier.Lite
    // Il caso di serie: Android 13, tutto sano, nessuno chiede altro.
    assertEquals(PhysicsGlass(full, FluidGlassModel.Liquid), glass())
    assertTrue(glass().liquid)
    // Un antenato chiede il vetro di prima: Full, com'era.
    assertEquals(PhysicsGlass(full, FluidGlassModel.Classic), glass(model = FluidGlassModel.Classic))
    // Il compilatore rifiuta una sorgente nuova: si scende di un gradino, al vetro di prima a Full.
    assertEquals(PhysicsGlass(full, FluidGlassModel.Classic), glass(liquid = false))
    // Rifiuta le vecchie ma non le nuove: il materiale nuovo non perde la lente per questo.
    assertEquals(PhysicsGlass(full, FluidGlassModel.Liquid), glass(classic = false))
    // Rifiuta tutte e due: Balanced, come sempre.
    assertEquals(PhysicsGlass(balanced, FluidGlassModel.Classic), glass(classic = false, liquid = false))
    // Sotto Android 13 (o simulato): niente AGSL, niente materiale nuovo.
    assertEquals(PhysicsGlass(balanced, FluidGlassModel.Classic), glass(sdk = 32, agsl = false))
    assertEquals(PhysicsGlass(lite, FluidGlassModel.Classic), glass(sdk = 30, renderEffect = false, agsl = false))
    // Un livello chiesto può solo scendere, e sotto Full il materiale è quello di prima.
    assertEquals(PhysicsGlass(balanced, FluidGlassModel.Classic), glass(tier = balanced))
    assertEquals(PhysicsGlass(lite, FluidGlassModel.Classic), glass(tier = lite))
    assertEquals(PhysicsGlass(full, FluidGlassModel.Liquid), glass(tier = full))
    assertFalse(glass(tier = balanced).liquid)
  }

  @Test
  fun aSurfaceThatStaysClassicNeverCompilesTheNewSources() {
    var asked = 0
    resolvePhysicsGlass(
      sdk = FluidGlassLiquidMinSdk, renderEffectSupported = true, runtimeShaderSupported = true,
      classicHealthy = { true }, liquidHealthy = { asked++; true },
      requestedTier = FluidPhysicsTier.Balanced, requestedModel = FluidGlassModel.Liquid,
    )
    resolvePhysicsGlass(
      sdk = FluidGlassLiquidMinSdk, renderEffectSupported = true, runtimeShaderSupported = true,
      classicHealthy = { true }, liquidHealthy = { asked++; true },
      requestedTier = null, requestedModel = FluidGlassModel.Classic,
    )
    assertEquals(0, asked)
    // E chi prende il materiale nuovo non compila le vecchie.
    var old = 0
    resolvePhysicsGlass(
      sdk = FluidGlassLiquidMinSdk, renderEffectSupported = true, runtimeShaderSupported = true,
      classicHealthy = { old++; true }, liquidHealthy = { true },
      requestedTier = null, requestedModel = FluidGlassModel.Liquid,
    )
    assertEquals(0, old)
  }

  // ---- Otto pezzi --------------------------------------------------------------------------------------------

  @Test
  fun thePlanCarriesEightPiecesAndTheFieldReceivesThemAll() {
    assertEquals(8, FluidForm.Group.MaxPieces)
    assertEquals(GlassFieldMaxPieces, PhysicsMaxPieces)
    val slabs = List(8) { i -> circle(10f + i * 52f, 20f) }
    val plan = PhysicsRenderPlan()
    buildRestPlan(plan, FluidForm.Group(slabs, blendRadius = px(16f)))
    assertEquals(8, plan.pieceCount)
    val field = physicsFieldPlan(plan, 1f, GlassFieldPlan(GlassFieldMaxPieces))
    assertEquals(8, field.count)
    for (i in 0 until 8) {
      assertEquals(slabs[i].frame.center.x, field.rects[i * 4], 1e-3f)
      assertEquals(px(22f), field.rects[i * 4 + 2], 1e-3f)
      assertEquals(px(22f), field.radii[i * 4], 1e-3f)
    }
    // A risoluzione ridotta ogni lunghezza passa per la scala; lo smoothing no.
    val half = physicsFieldPlan(plan, 0.5f, GlassFieldPlan(GlassFieldMaxPieces))
    assertEquals(field.rects[4] * 0.5f, half.rects[4], 1e-3f)
    assertEquals(field.radii[4] * 0.5f, half.radii[4], 1e-3f)
    // Un transito a otto coppie regge anche lui.
    buildSlabPlan(plan, slabs.map { it to it }, px(16f), 0.5f)
    assertEquals(8, plan.pieceCount)
  }

  // ---- Pezzi separati a riposo, collo solo in viaggio -------------------------------------------------------

  @Test
  fun piecesAreApartAtRestAndTheNeckLivesOnlyInTravel() {
    val a = circle(20f, 20f)
    val b = circle(68f, 20f)
    val group = FluidForm.Group(listOf(a, b), blendRadius = px(16f))
    val plan = PhysicsRenderPlan()
    val field = GlassFieldPlan(GlassFieldMaxPieces)

    // A riposo: nessun raggio di fusione nel campo, quindi fra i due pezzi (a 4 dp) il fondale è nudo.
    buildRestPlan(plan, group)
    assertEquals(0f, plan.fusion, 0f)
    physicsFieldPlan(plan, 1f, field)
    assertEquals(0f, field.blend[1], 0f)
    val midX = (a.frame.right + b.frame.left) / 2f
    val midY = a.frame.center.y
    val restDistance = GlassFieldGeometry.fieldDistance(field.count, field.rects, field.radii, field.blend, field.smooth, midX, midY)
    assertEquals("a metà del varco di 4 dp la distanza è mezzo varco", px(2f), restDistance, 0.01f)
    // Il vetro di prima tiene il suo raggio: il suo gruppo fermo resta com'era.
    assertEquals(px(16f), plan.pieceBlend[1], 1e-3f)

    // In viaggio, a metà: il collo c'è (la distanza fusa a metà del varco è dentro la sagoma).
    buildSlabPlan(plan, listOf(a to a, b to b), px(16f), 0.5f)
    assertEquals(1f, plan.fusion, 1e-6f)
    physicsFieldPlan(plan, 1f, field)
    assertEquals(px(16f), field.blend[1], 1e-3f)
    val bridged = GlassFieldGeometry.fieldDistance(field.count, field.rects, field.radii, field.blend, field.smooth, midX, midY)
    assertTrue("a metà viaggio il ponte copre il varco: $bridged", bridged < 0f)

    // Ai due capi del viaggio il collo è morto: il primo e l'ultimo fotogramma sono pezzi separati, come a riposo.
    for (t in listOf(0f, 1f)) {
      buildSlabPlan(plan, listOf(a to a, b to b), px(16f), t)
      assertEquals("t=$t", 0f, plan.fusion, 0f)
    }
    // Una lastra sola non ha un collo, qualunque cosa le si chieda.
    buildSlabPlan(plan, listOf(a to b), px(16f), 0.5f)
    assertEquals(0f, plan.fusion, 0f)
  }

  @Test
  fun theNeckIsBornAndDiesWithoutAStepAndIsInheritedMidFlight() {
    val fade = GlassLiquidProfile.Shipped.travel.neck
    assertEquals(0f, travelFusion(0f, fade), 0f)
    assertEquals(0f, travelFusion(1f, fade), 0f)
    assertEquals(1f, travelFusion(0.5f, fade), 1e-6f)
    // Oltre i capi (una molla che oltrepassa, una partenza all'indietro) resta zero.
    assertEquals(0f, travelFusion(1.2f, fade), 0f)
    assertEquals(0f, travelFusion(-0.3f, fade), 0f)
    assertEquals(0f, travelFusion(Float.NaN, fade), 0f)
    // Continua: fra due progressi vicini non salta.
    var previous = travelFusion(0f, fade)
    var t = 0f
    while (t <= 1f) {
      val now = travelFusion(t, fade)
      assertTrue("t=$t: $previous -> $now", abs(now - previous) < 0.05f)
      assertTrue(now in 0f..1f)
      previous = now
      t += 0.01f
    }
    // Un viaggio che ne interrompe un altro parte col collo com'era, non da zero.
    assertEquals(0.7f, travelFusion(0f, fade, start = 0.7f), 1e-6f)
    assertEquals(0f, travelFusion(1f, fade, start = 0.7f), 0f)
    assertTrue(travelFusion(0.25f, fade, start = 0.7f) >= 0.7f)

    // Lo porta il transito: un viaggio nuovo eredita dal vecchio.
    val a = circle(20f, 20f)
    val b = circle(68f, 20f)
    val transit = SlabTransit(listOf(a to a, b to b), px(16f))
    assertEquals(0f, transit.fusionAt(0f), 0f)
    transit.fusionStart = 0.6f
    assertEquals(0.6f, transit.fusionAt(0f), 1e-6f)
    val plan = PhysicsRenderPlan()
    buildTransitPlan(plan, transit, 0f)
    assertEquals(0.6f, plan.fusion, 1e-6f)
    // All'oltrepasso i pezzi sono il traguardo gonfiato: nessun collo.
    buildTransitPlan(plan, transit, 1.05f)
    assertEquals(0f, plan.fusion, 0f)
  }

  // ---- La sagoma a riposo è quella di glassSurface ------------------------------------------------------------

  @Test
  fun aRestingSlabHasTheOutlineGlassSurfaceGivesIt() {
    val plan = PhysicsRenderPlan()
    // Una capsula: un semicerchio esatto, nessun raccordo.
    buildRestPlan(plan, circle(0f, 0f))
    assertEquals(px(22f), plan.fieldRadii[0], 1e-3f)
    assertEquals(0f, plan.fieldSmooth[0], 0f)

    // Un grande (un menu): i raggi nominali e il raccordo della forma.
    val panel = FluidForm.Slab(Rect(0f, 0f, px(170f), px(192f)), FluidCornerRadii.all(px(28f)), ContinuousCornerShape.IosSmoothing)
    buildRestPlan(plan, panel)
    val rule = glassSlabGeometry(
      px(170f), px(192f), px(28f), px(28f), px(28f), px(28f), ContinuousCornerShape.IosSmoothing,
      continuous = true, small = glassSlabIsSmall(px(192f)),
    )
    assertEquals(rule.topLeft, plan.fieldRadii[0], 0f)
    assertEquals(rule.smoothing, plan.fieldSmooth[0], 0f)
    assertEquals(ContinuousCornerShape.IosSmoothing, plan.fieldSmooth[0], 0f)

    // Un piccolo non capsula: il raggio equivalente che l'engine già disegna.
    val small = FluidForm.Slab(Rect(0f, 0f, px(60f), px(44f)), FluidCornerRadii.all(px(12f)), ContinuousCornerShape.IosSmoothing)
    buildRestPlan(plan, small)
    assertEquals(px(12f) * ContinuousCornerShape.RoundedEquivalentFactor, plan.fieldRadii[0], 1e-3f)
    assertEquals(0f, plan.fieldSmooth[0], 0f)

    // In viaggio fra il tasto e il menu: ai capi la sagoma dei capi, in mezzo niente scatta.
    val button = circle(60f, 150f)
    buildSlabPlan(plan, listOf(button to panel), 0f, 0f)
    assertEquals(px(22f), plan.fieldRadii[0], 1e-3f)
    assertEquals(0f, plan.fieldSmooth[0], 0f)
    buildSlabPlan(plan, listOf(button to panel), 0f, 1f)
    assertEquals(px(28f), plan.fieldRadii[0], 1e-3f)
    assertEquals(ContinuousCornerShape.IosSmoothing, plan.fieldSmooth[0], 0f)
    var last = 0f
    var t = 0f
    while (t <= 1f) {
      buildSlabPlan(plan, listOf(button to panel), 0f, t)
      assertTrue("lo smoothing sale senza scatti: $last -> ${plan.fieldSmooth[0]} a t=$t", abs(plan.fieldSmooth[0] - last) < 0.02f)
      last = plan.fieldSmooth[0]
      t += 0.01f
    }
  }

  // ---- La ricetta: riposo e viaggio sono lo stesso vetro ------------------------------------------------------

  private val button = GlassDefaults.buttonOptics().sanitized()
  private val panelOptics = GlassDefaults.panelOptics(compact = true).sanitized()

  private fun travelRecipe(shortDp: Float, blend: Float, motion: Float, dark: Boolean = false): GlassRecipe = physicsLiquidRecipe(
    role = GlassRole.Modal,
    material = PhysicsMaterialSource(button, panelOptics).at(blend),
    shortDp = shortDp,
    darkSide = dark,
    amount = 1f,
    quality = 1f,
    press = 0f,
    tint = neutral,
    tintFrom = neutral,
    tintBlend = blend,
    nested = false,
    motion = motion,
  )

  @Test
  fun aJourneyStartsAndEndsOnTheRecipeOfTheSlabAtRest() {
    for (dark in listOf(false, true)) {
      // Il primo fotogramma del menu che nasce dal tasto: la ricetta del tasto di vetro (ruolo `Interactive`).
      val start = travelRecipe(44f, blend = 0f, motion = 0f, dark = dark)
      val restButton = glassRecipe(GlassRole.Interactive, 44f, 44f, dark, optics = button)
      assertEquals(numbers(restButton), numbers(start))
      // L'ultimo: la ricetta del pannello fermo.
      val end = travelRecipe(170f, blend = 1f, motion = 0f, dark = dark)
      val restPanel = glassRecipe(GlassRole.Modal, 170f, 192f, dark, optics = panelOptics)
      assertEquals(numbers(restPanel), numbers(end))
    }
    // Una superficie senza partenza è la ricetta di glassSurface e basta, a ogni taglia.
    for (short in listOf(26f, 44f, 56f, 120f, 330f)) {
      val fixed = physicsLiquidRecipe(
        GlassRole.Floating, PhysicsMaterialSource(null, GlassDefaults.optics(GlassRole.Floating).sanitized()).at(1f), short,
        darkSide = false, amount = 1f, quality = 1f, press = 0f, tint = neutral, tintFrom = null, tintBlend = 1f,
        nested = false, motion = 0f,
      )
      assertEquals("$short", numbers(glassRecipe(GlassRole.Floating, short, short, false)), numbers(fixed))
    }
  }

  @Test
  fun stillnessIsRestAndMotionOnlyMakesTheGlassClearer() {
    val rest = travelRecipe(100f, blend = 0.5f, motion = 0f)
    val moving = travelRecipe(100f, blend = 0.5f, motion = 1f)
    val travel = GlassLiquidProfile.Shipped.travel
    // Fermo: nemmeno un bit di differenza dalla ricetta senza viaggio.
    val plain = travelRecipe(100f, blend = 0.5f, motion = 0f)
    assertEquals(numbers(plain), numbers(rest))
    // In moto: più nitido, più lente, un filo d'ingrandimento. Il raggio di sfocatura e la banda no (tengono il
    // margine), e velo, bordo e ombra no: è lo stesso vetro.
    assertEquals(rest.mix + (1f - rest.mix) * travel.clarity, moving.mix, 1e-6f)
    assertEquals(rest.refractionDp * (1f + travel.bend), moving.refractionDp, 1e-5f)
    assertEquals(travel.magnify, moving.magnify, 1e-6f)
    assertTrue(moving.dome >= rest.dome)
    assertEquals(rest.blurDp, moving.blurDp, 0f)
    assertEquals(rest.thicknessDp, moving.thicknessDp, 0f)
    assertEquals(rest.veilColor, moving.veilColor)
    assertEquals(rest.rimDark, moving.rimDark)
    assertEquals(rest.reflexStrength, moving.reflexStrength, 0f)
    assertEquals(rest.shadowAmbientAlpha, moving.shadowAmbientAlpha, 0f)
    // A metà moto, a metà strada; e col viaggio spento dal profilo niente cambia mai.
    val halfway = travelRecipe(100f, blend = 0.5f, motion = 0.5f)
    assertEquals((rest.mix + moving.mix) / 2f, halfway.mix, 1e-6f)
    val off = glassTravelRecipe(travelRecipe(100f, 0.5f, 0f), GlassLiquidTravels.Off, motion = 1f)
    assertEquals(rest.mix, off.mix, 0f)
    assertEquals(rest.refractionDp, off.refractionDp, 0f)
    assertEquals(0f, off.magnify, 0f)
  }

  @Test
  fun aTintIsBlendedFieldByFieldAndIsExactAtBothEnds() {
    val film = GlassTint(Color(0x80FF0000), Color.Red, Color.Transparent)
    fun recipe(blend: Float) = physicsLiquidRecipe(
      GlassRole.Interactive, PhysicsMaterialSource(null, button).at(1f), 44f, darkSide = false, amount = 1f, quality = 1f,
      press = 0f, tint = film, tintFrom = neutral, tintBlend = blend, nested = false, motion = 0f,
    )
    val neutralOnly = glassRecipe(GlassRole.Interactive, 44f, 44f, false, optics = button)
    val filmOnly = glassRecipe(GlassRole.Interactive, 44f, 44f, false, optics = button, film = film.overlay)
    assertEquals(numbers(neutralOnly), numbers(recipe(0f)))
    assertEquals(numbers(filmOnly), numbers(recipe(1f)))
    val mid = recipe(0.5f)
    // Un colore di Compose tiene otto bit per canale: la media di due livelli può cadere a metà fra due, e
    // l'arrotondamento la sposta di mezzo livello.
    val halfLevel = 0.5f / 255f + 1e-4f
    assertEquals((neutralOnly.veilColor.alpha + filmOnly.veilColor.alpha) / 2f, mid.veilColor.alpha, halfLevel)
    assertEquals(filmOnly.tintColor.alpha / 2f, mid.tintColor.alpha, halfLevel)
    // `lerpGlassRecipe` può scrivere su uno dei due capi.
    val a = glassRecipe(GlassRole.Interactive, 44f, 44f, false, optics = button)
    val b = glassRecipe(GlassRole.Modal, 170f, 192f, true, optics = panelOptics)
    assertEquals(numbers(a), numbers(lerpGlassRecipe(a, b, 0f)))
    assertEquals(numbers(b), numbers(lerpGlassRecipe(a, b, 1f)))
    val expected = numbers(b)
    assertEquals(expected, numbers(lerpGlassRecipe(a, b, 1f, out = a)))
  }

  // ---- Il margine della cattura è fisso -----------------------------------------------------------------------

  @Test
  fun theCapturePaddingDoesNotMoveAlongAJourney() {
    val padding = glassTravelPaddingDp(GlassRole.Modal, panelOptics, button)
    val profile = GlassLiquidProfile.Shipped
    // È la sfocatura piena alla crescita massima: 4,5 dp per una volta e mezza.
    assertEquals(profile.frost.blur.value * (1f + profile.growth.frost), padding, 1e-4f)
    // Copre il margine di ogni lastra ferma che il viaggio attraversa, a ogni taglia e a ogni miscela.
    var short = 8f
    while (short <= 400f) {
      for (blend in listOf(0f, 0.3f, 0.7f, 1f)) {
        val r = travelRecipe(short, blend, motion = 0f)
        assertTrue("lato $short, miscela $blend: ${r.paddingDp} > $padding", r.paddingDp <= padding + 1e-4f)
        assertTrue("la sfocatura sta nel margine", r.blurDp <= padding + 1e-4f)
      }
      short += 7f
    }
    // E ciò che arriva al nodo è lo stesso numero di pixel a ogni fotogramma di un viaggio dal tasto al menu.
    val from = circle(60f, 150f)
    val to = FluidForm.Slab(Rect(px(20f), px(10f), px(190f), px(202f)), FluidCornerRadii.all(px(28f)))
    val plan = PhysicsRenderPlan()
    val seen = HashSet<Float>()
    var t = 0f
    while (t <= 1f) {
      buildSlabPlan(plan, listOf(from to to), 0f, t)
      val recipe = travelRecipe(physicsRecipeShortPx(plan) / density, t, motion = 0.4f)
      val inputs = physicsLiquidInputs(plan, recipe, 1f, density, floor(padding * density))
      seen += inputs.paddingPx
      assertEquals(-inputs.paddingPx, inputs.bodyOffsetX, 0f)
      t += 0.05f
    }
    assertEquals(setOf(floor(padding * density)), seen)
    // Con la smerigliatura larga delle manopole il margine cresce, ma resta uno.
    val wide = GlassLiquidProfile.Shipped.copy(frost = dev.antigravity.fluidengine.ui.glass.field.GlassLiquidFrosts.WideMixed)
    assertEquals(36f * 1.5f, glassTravelPaddingDp(GlassRole.Modal, panelOptics, button, wide), 1e-3f)
  }

  // ---- Dove sta la luce ---------------------------------------------------------------------------------------

  @Test
  fun theLightIsAlwaysInTheBodySoRestIsTheFirstFrameOfTheJourney() {
    val plan = PhysicsRenderPlan()
    val recipe = glassRecipe(GlassRole.Interactive, 44f, 44f, false, optics = button)
    val slab = circle(10f, 10f)

    buildRestPlan(plan, slab)
    val rest = physicsLiquidInputs(plan, recipe, 1f, density, 0f, out = GlassDrawInputs(GlassFieldMaxPieces))
    assertEquals(GlassBodyKernel.Eight, rest.kernel)
    assertTrue(rest.lightInBody)
    assertTrue(rest.nodeClip)
    assertTrue(rest.drawsRim && rest.drawsGlowing)
    assertEquals(1, rest.body.count)
    // Nessun disegno di luce sul nodo: il piano della luce resta vuoto.
    assertEquals(0, rest.light.count)

    // Il primo fotogramma del viaggio dalla lastra a se stessa: gli stessi ingressi, numero per numero.
    buildSlabPlan(plan, listOf(slab to slab), 0f, 0f)
    val travel = physicsLiquidInputs(plan, recipe, 1f, density, 0f, out = GlassDrawInputs(GlassFieldMaxPieces))
    assertTrue(travel.lightInBody && travel.nodeClip)
    assertEquals(rest.bodyRimWidth, travel.bodyRimWidth, 0f)
    assertEquals(rest.bodyReflexDepth, travel.bodyReflexDepth, 0f)
    assertEquals(rest.thickness, travel.thickness, 0f)
    assertEquals(rest.nodeClipCorner, travel.nodeClipCorner, 0f)
    for (i in 0 until 4) {
      assertEquals(rest.body.rects[i], travel.body.rects[i], 0f)
      assertEquals(rest.body.radii[i], travel.body.radii[i], 0f)
    }
    // A risoluzione piena i numeri della luce in corpo sono quelli che una `glassSurface` dà alla luce sul nodo.
    assertEquals(rest.rimWidth, rest.bodyRimWidth, 0f)
    assertEquals(rest.reflexDepth, rest.bodyReflexDepth, 0f)
    // A mezza risoluzione il corpo, con la sua luce, sta nello spazio della cattura.
    val reduced = physicsLiquidInputs(plan, recipe, 0.5f, density, 0f, out = GlassDrawInputs(GlassFieldMaxPieces))
    assertEquals(travel.bodyRimWidth * 0.5f, reduced.bodyRimWidth, 1e-5f)
    assertEquals(travel.body.rects[2] * 0.5f, reduced.body.rects[2], 1e-4f)

    // Una sagoma libera non ha un programma di luce: anello, e luce in corpo anche da ferma.
    buildRestPlan(plan, FluidFormPresets.star(Rect(0f, 0f, 300f, 300f)))
    val ring = physicsLiquidInputs(plan, recipe, 1f, density, 0f, out = GlassDrawInputs(GlassFieldMaxPieces))
    assertEquals(GlassBodyKernel.Ring, ring.kernel)
    assertTrue(ring.lightInBody)
    assertEquals(plan.vertCount, ring.ring.count)
    assertEquals(-1f, ring.ring.dropReach, 0f)
    // La distanza è vera fin dove il corpo la legge.
    assertTrue(ring.ring.bandInner >= ring.thickness && ring.ring.bandInner >= ring.bodyShadeDepth)
    assertTrue(ring.ring.bandInner >= ring.bodyRimWidth + ring.bodyReflexDepth)
    assertTrue(ring.ring.bandOuter >= ring.aa)
    // Il riquadro contiene la sagoma allargata dell'erosione.
    assertTrue(ring.ring.bbox[0] <= plan.bounds.left - plan.soften)
    assertTrue(ring.ring.bbox[2] >= plan.bounds.right + plan.soften)
  }

  @Test
  fun aFieldHasTheRecipeOfItsTallestPiece() {
    val plan = PhysicsRenderPlan()
    // I tasti di una barra sono alti uguali: la ricetta di ognuno.
    buildRestPlan(plan, FluidForm.Group(listOf(circle(0f, 0f), circle(52f, 0f)), blendRadius = px(4f)))
    assertEquals(px(44f), physicsRecipeShortPx(plan), 1e-3f)
    // Un seme di un pixel accanto a un tasto non toglie la lente al tasto.
    val seed = FluidForm.Slab(Rect(px(100f), px(21f), px(100f) + 1f, px(21f) + 1f), FluidCornerRadii.all(0.5f))
    buildSlabPlan(plan, listOf(circle(0f, 0f) to circle(0f, 0f), seed to circle(100f, 0f)), px(4f), 0f)
    assertEquals(px(44f), physicsRecipeShortPx(plan), 1e-3f)
  }

  // ---- Il ritaglio del nodo, emulato --------------------------------------------------------------------------

  @Test
  fun theEmulatedNodeClipFollowsTheClipOfAGlassSurface() {
    // Angoli circolari (tasto, capsula): il ritaglio di una lastra coincide con la sagoma, nessun margine.
    assertEquals(0f, glassNodeClipCorner(66f, 0f), 0f)
    // Angoli raccordati: il ritaglio sta al 98% del raggio.
    assertEquals(0.02f * 84f, glassNodeClipCorner(84f, 0.6f), 1e-5f)
    // In mezzo sale senza scatti, ed è pieno presto.
    assertEquals(0.02f * 84f * 0.5f, glassNodeClipCorner(84f, GlassNodeClipFullSmoothing / 2f), 1e-5f)
    assertEquals(0f, glassNodeClipCorner(0f, 0.6f), 0f)
    assertEquals(0f, glassNodeClipCorner(Float.NaN, 0.6f), 0f)

    val plan = PhysicsRenderPlan()
    val recipe = glassRecipe(GlassRole.Modal, 170f, 192f, false, optics = panelOptics)
    // Un pannello grande, raccordato: il margine è quello del suo raggio nel piano del campo.
    val panel = FluidForm.Slab(Rect(px(20f), px(10f), px(190f), px(202f)), FluidCornerRadii.all(px(28f)), smoothing = 0.6f)
    buildRestPlan(plan, panel)
    val inputs = physicsLiquidInputs(plan, recipe, 1f, density, 0f, out = GlassDrawInputs(GlassFieldMaxPieces))
    assertTrue(inputs.nodeClip)
    assertEquals(glassNodeClipCorner(plan.fieldRadii[0], plan.fieldSmooth[0]), inputs.nodeClipCorner, 0f)
    assertTrue(plan.fieldSmooth[0] > 0f && inputs.nodeClipCorner > 0f)
    // Un tasto tondo: zero.
    buildRestPlan(plan, circle(10f, 10f))
    assertEquals(0f, physicsLiquidInputs(plan, recipe, 1f, density, 0f, out = GlassDrawInputs(GlassFieldMaxPieces)).nodeClipCorner, 0f)
  }
}
