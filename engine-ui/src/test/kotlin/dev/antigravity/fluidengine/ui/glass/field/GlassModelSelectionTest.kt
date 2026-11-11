package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.foundation.shape.AbsoluteCutCornerShape
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassLiquidMinSdk
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassFalloff
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.glassLiquidSlabShadersHealthy
import dev.antigravity.fluidengine.ui.fluid.glassResolutionScale
import dev.antigravity.fluidengine.ui.fluid.isFlatVeil
import dev.antigravity.fluidengine.ui.fluid.resolveFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import java.lang.reflect.Proxy
import kotlin.math.max
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'innesto del materiale `Liquid` (F1A.6), nelle sue parti pure: **quale** materiale disegna una superficie
 * ([resolveFluidGlassModel]), **quale sagoma** il campo riceve da una forma ([glassSlabGeometry]), e **in
 * quale spazio** arrivano i numeri a corpo e luce ([GlassDrawInputs]).
 *
 * Il primo gruppo è la tabella di verità del piano (4.4): è ciò che tiene il vetro di prima dov'era sotto
 * Android 13, su un driver che rifiuta, sulla fascia in alto e dove un antenato lo chiede.
 */
class GlassModelSelectionTest {

  @After
  fun forget() {
    GlassFieldHealth.resetForTesting()
  }

  private val healthy: () -> Boolean = { true }

  private fun resolve(
    sdk: Int = 35,
    shadersHealthy: () -> Boolean = healthy,
    requested: FluidGlassModel = FluidGlassModel.Liquid,
    falloff: GlassFalloff = GlassFalloff.Uniform,
    genericShape: Boolean = false,
    flat: Boolean = false,
  ) = resolveFluidGlassModel(sdk, shadersHealthy, requested, falloff, genericShape, flat)

  // ---- Quale materiale ---------------------------------------------------------------------------------------

  @Test
  fun liquidNeedsAndroid13() {
    for (sdk in listOf(26, 30, 31, 32)) {
      assertEquals("API $sdk", FluidGlassModel.Classic, resolve(sdk = sdk))
    }
    for (sdk in listOf(33, 34, 35, 36)) {
      assertEquals("API $sdk", FluidGlassModel.Liquid, resolve(sdk = sdk))
    }
    assertEquals(33, FluidGlassLiquidMinSdk)
  }

  @Test
  fun anAncestorAskingForClassicGetsItEverywhere() {
    for (sdk in listOf(26, 31, 32, 33, 35)) {
      assertEquals("API $sdk", FluidGlassModel.Classic, resolve(sdk = sdk, requested = FluidGlassModel.Classic))
    }
  }

  @Test
  fun aRefusingCompilerKeepsTheGlassOfBefore() {
    assertEquals(FluidGlassModel.Classic, resolve(shadersHealthy = { false }))
  }

  @Test
  fun theFadingBarStaysClassicUntilTheScrollEdgeReplacesIt() {
    assertEquals(FluidGlassModel.Classic, resolve(falloff = GlassFalloff.FadeDown))
    assertEquals(FluidGlassModel.Liquid, resolve(falloff = GlassFalloff.Uniform))
  }

  @Test
  fun aShapeTheFieldCannotRenderStaysClassic() {
    assertEquals(FluidGlassModel.Classic, resolve(genericShape = true))
  }

  @Test
  fun aVeilWithoutThicknessStaysClassic() {
    assertEquals(FluidGlassModel.Classic, resolve(flat = true))
  }

  /** La prima domanda alla sonda compila: chi resta `Classic` per un'altra ragione non deve farla. */
  @Test
  fun theCompilerIsAskedLastAndOnlyWhenItDecides() {
    val neverAsked: () -> Boolean = { throw AssertionError("la sonda non andava consultata") }
    resolve(shadersHealthy = neverAsked, requested = FluidGlassModel.Classic)
    resolve(shadersHealthy = neverAsked, sdk = 32)
    resolve(shadersHealthy = neverAsked, falloff = GlassFalloff.FadeDown)
    resolve(shadersHealthy = neverAsked, genericShape = true)
    resolve(shadersHealthy = neverAsked, flat = true)
    var asked = 0
    resolve(shadersHealthy = { asked++; true })
    assertEquals(1, asked)
  }

  @Test
  fun theSlabNeedsBothItsBodyAndItsLight() {
    fun install(vararg refused: GlassShaderSource) =
      GlassFieldHealth.installForTesting(agsl = true, compile = { source ->
        if (source in refused) throw IllegalArgumentException("rifiutata apposta dal test")
      }) { _, _ -> }

    install()
    assertTrue(glassLiquidSlabShadersHealthy())
    install(GlassShaderSources.Body1)
    assertFalse(glassLiquidSlabShadersHealthy())
    install(GlassShaderSources.Light1)
    assertFalse(glassLiquidSlabShadersHealthy())
    // Una sorgente che la lastra ferma non usa non la riguarda.
    install(GlassShaderSources.Body8, GlassShaderSources.Light8, GlassShaderSources.RoundedRectRefraction)
    assertTrue(glassLiquidSlabShadersHealthy())
    // Senza AGSL non c'è niente da chiedere al compilatore.
    GlassFieldHealth.installForTesting(agsl = false, compile = { throw AssertionError("non si compila") }) { _, _ -> }
    assertFalse(glassLiquidSlabShadersHealthy())
  }

  @Test
  fun everyStockOpticsIsASlabAndTheModalScrimIsNot() {
    val stock = GlassRole.entries.map { GlassDefaults.optics(it) } + listOf(
      GlassDefaults.buttonOptics(),
      GlassDefaults.stackedContentOptics(),
      GlassDefaults.panelOptics(),
      GlassDefaults.panelOptics(compact = true),
      GlassDefaults.panelOptics(overSharpPage = true),
      GlassDefaults.panelOptics(compact = true, overSharpPage = true),
    )
    for (optics in stock) assertFalse(optics.toString(), optics.sanitized().isFlatVeil())

    // Lo scrim di un modale (`FluidGlassModalScrim`): sfoca appena, senza lente, bordo né ombra.
    val scrim: GlassOptics = GlassDefaults.optics(GlassRole.Modal).copy(
      blurScale = 1.5f, sizeResponse = 0f, refractionHeight = 0.dp, refractionAmount = 0.dp, vibrancy = 1f,
      highlightAlpha = 0f, innerShadowAlpha = 0f, shadowAlpha = 0f,
    )
    assertTrue(scrim.sanitized().isFlatVeil())
    // Basta una delle tre perché sia una lastra.
    assertFalse(scrim.copy(highlightAlpha = 0.2f).isFlatVeil())
    assertFalse(scrim.copy(shadowAlpha = 0.2f).isFlatVeil())
    assertFalse(scrim.copy(refractionHeight = 4.dp, refractionAmount = 4.dp).isFlatVeil())
    // Una lente senza spostamento non piega niente.
    assertTrue(scrim.copy(refractionHeight = 4.dp).isFlatVeil())
  }

  @Test
  fun theFieldRendersRectanglesAndCornerShapesOnly() {
    val renders = listOf(
      RectangleShape, RoundedCornerShape(12.dp), CircleShape, AbsoluteRoundedCornerShape(8.dp),
      ContinuousCornerShape(28.dp), FluidCapsuleShape,
    )
    for (shape in renders) assertTrue(shape.toString(), glassFieldRenders(shape))
    val custom = object : Shape {
      override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        throw AssertionError("la forma non va disegnata per sapere se il campo la rende")
    }
    assertFalse(glassFieldRenders(custom))
    assertFalse(glassFieldRenders(GenericShape { _, _ -> }))
  }

  @Test
  fun aCutCornerShapeStaysClassic() {
    // È una `CornerBasedShape`, ma i suoi angoli sono tagliati: il campo, che conosce solo angoli tondi o
    // raccordati, la disegnava arrotondata (divergenza di F1A.10). Resta al vetro di prima, che la ritaglia col
    // suo path, come ogni forma che il campo non sa rendere.
    for (shape in listOf(CutCornerShape(8.dp), AbsoluteCutCornerShape(8.dp), CutCornerShape(0.dp))) {
      assertFalse(shape.toString(), glassFieldRenders(shape))
      val model = resolveFluidGlassModel(
        sdk = FluidGlassLiquidMinSdk,
        shadersHealthy = { error("una forma che resta Classic non deve far compilare niente") },
        requested = FluidGlassModel.Liquid,
        falloff = GlassFalloff.Uniform,
        genericShape = !glassFieldRenders(shape),
      )
      assertEquals(FluidGlassModel.Classic, model)
    }
    // Una sottoclasse di `CornerBasedShape` scritta da un'app: il campo non sa che curva disegna.
    val custom = object : CornerBasedShape(CornerSize(8.dp), CornerSize(8.dp), CornerSize(8.dp), CornerSize(8.dp)) {
      override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) = this
      override fun createOutline(
        size: Size, topStart: Float, topEnd: Float, bottomEnd: Float, bottomStart: Float, layoutDirection: LayoutDirection,
      ): Outline = throw AssertionError("la forma non va disegnata per sapere se il campo la rende")
    }
    assertFalse(glassFieldRenders(custom))
  }

  // ---- La tinta ----------------------------------------------------------------------------------------------

  @Test
  fun aHandWrittenTintIsStillThePaintedFilm() {
    val tint = GlassTint(Color.Red, Color.Blue, Color.Transparent)
    assertEquals(GlassTintMode.Fixed, tint.mode)
    assertEquals(tint, tint.copy())
    assertEquals(GlassTintMode.Neutral, tint.copy(mode = GlassTintMode.Neutral).copy(overlay = Color.Green).mode)
  }

  // ---- Forma -> piano ----------------------------------------------------------------------------------------

  private val density3 = Density(3f)

  private fun geometry(shape: Shape, widthDp: Float, heightDp: Float, rtl: Boolean = false): GlassSlabGeometry {
    val size = Size(widthDp * 3f, heightDp * 3f)
    return glassSlabGeometry(
      shape, size, density3, if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, size.maxDimension,
    )
  }

  private fun GlassSlabGeometry.radii() = listOf(topLeft, topRight, bottomRight, bottomLeft)

  @Test
  fun aCapsuleIsAnExactSemicircle() {
    for ((w, h) in listOf(44f to 44f, 156f to 56f, 56f to 220f)) {
      val g = geometry(FluidCapsuleShape, w, h)
      assertEquals(List(4) { minOf(w, h) * 3f / 2f }, g.radii())
      assertEquals(0f, g.smoothing, 0f)
      assertEquals(1f, g.clipScale, 0f)
      assertFalse(g.smoothed)
    }
    // Un raggio da mezzo lato corto in su è una capsula, comunque sia scritto.
    assertEquals(List(4) { 66f }, geometry(ContinuousCornerShape(1000.dp), 44f, 44f).radii())
  }

  @Test
  fun aLargeEngineShapeKeepsItsRadiiItsSmoothingAndIsClippedAt98Percent() {
    val g = geometry(ContinuousCornerShape(28.dp), 170f, 192f)
    assertEquals(List(4) { 84f }, g.radii())
    assertEquals(ContinuousCornerShape.IosSmoothing, g.smoothing, 0f)
    assertEquals(0.98f, g.clipScale, 0f)
    assertTrue(g.smoothed && g.nominal)
    // Lo `smoothing` è quello della forma, non uno supposto.
    assertEquals(0.3f, geometry(ContinuousCornerShape(28.dp, smoothing = 0.3f), 170f, 192f).smoothing, 0f)
    // Chiesto a zero è un angolo circolare, e il ritaglio torna pieno.
    val circular = geometry(ContinuousCornerShape(28.dp, smoothing = 0f), 170f, 192f)
    assertEquals(0f, circular.smoothing, 0f)
    assertEquals(1f, circular.clipScale, 0f)
  }

  @Test
  fun aSmallEngineShapeUsesTheEquivalentRadiusTheEngineAlreadyDraws() {
    // 72 x 36 dp a densità 3: 216 px di lato lungo, sotto i 256.
    val g = geometry(ContinuousCornerShape(10.dp), 72f, 36f)
    val equivalent = 30f * ContinuousCornerShape.RoundedEquivalentFactor
    assertEquals(List(4) { equivalent }, g.radii())
    assertEquals(0f, g.smoothing, 0f)
    assertEquals(1f, g.clipScale, 0f)
    assertFalse(g.nominal)
    // È il raggio di `ContinuousCornerShape.roundedEquivalent`, angolo per angolo: quello già a mezzo lato resta.
    val mixed = geometry(ContinuousCornerShape(topStart = 18.dp, topEnd = 6.dp), 72f, 36f)
    assertEquals(listOf(54f, 18f * ContinuousCornerShape.RoundedEquivalentFactor, 0f, 0f), mixed.radii())
  }

  @Test
  fun theSmallThresholdIsInScreenPixelsWhateverTheCaptureResolution() {
    // 170 x 192 dp a densità 3 è un grande (576 px). Il corpo lo riceve a 0,4: 230 px, che sarebbe un piccolo.
    val scale = 0.4f
    val size = Size(170f * 3f * scale, 192f * 3f * scale)
    val body = glassSlabGeometry(ContinuousCornerShape(28.dp), size, Density(3f * scale), LayoutDirection.Ltr, 576f)
    assertEquals(84f * scale, body.topLeft, 1e-4f)
    assertEquals(ContinuousCornerShape.IosSmoothing, body.smoothing, 0f)
    assertTrue(body.smoothed)
    assertTrue(glassSlabIsSmall(255.9f))
    assertFalse(glassSlabIsSmall(256f))
  }

  @Test
  fun plainCornerShapesAreCircularAndARectangleHasNoCorners() {
    val rounded = geometry(RoundedCornerShape(12.dp), 300f, 200f)
    assertEquals(List(4) { 36f }, rounded.radii())
    assertEquals(0f, rounded.smoothing, 0f)
    assertEquals(1f, rounded.clipScale, 0f)
    // Nemmeno da piccola una forma circolare si stringe: il raggio equivalente è delle forme continue.
    assertEquals(List(4) { 30f }, geometry(RoundedCornerShape(10.dp), 72f, 36f).radii())
    val rect = geometry(RectangleShape, 360f, 64f)
    assertEquals(List(4) { 0f }, rect.radii())
    assertEquals(1f, rect.clipScale, 0f)
    // Un rettangolo scritto come forma continua non ha un angolo da raccordare.
    assertFalse(geometry(ContinuousCornerShape(0.dp), 360f, 64f).smoothed)
    // I raggi non superano mai mezzo lato corto.
    assertEquals(List(4) { 96f }, geometry(RoundedCornerShape(500.dp), 300f, 64f).radii())
  }

  @Test
  fun cornersMirrorWithTheReadingDirectionUnlessTheShapeIsAbsolute() {
    val sheet = ContinuousCornerShape(topStart = 28.dp, topEnd = 10.dp, bottomEnd = 4.dp, bottomStart = 0.dp)
    assertEquals(listOf(84f, 30f, 12f, 0f), geometry(sheet, 300f, 400f).radii())
    assertEquals(listOf(30f, 84f, 0f, 12f), geometry(sheet, 300f, 400f, rtl = true).radii())
    val absolute = AbsoluteRoundedCornerShape(topLeft = 28.dp, topRight = 10.dp, bottomRight = 4.dp, bottomLeft = 0.dp)
    assertEquals(listOf(84f, 30f, 12f, 0f), geometry(absolute, 300f, 400f, rtl = true).radii())
  }

  // ---- In quale spazio arrivano i numeri ---------------------------------------------------------------------

  private fun surface(
    role: GlassRole,
    shape: Shape,
    optics: GlassOptics = GlassDefaults.optics(role),
    dark: Boolean = false,
    density: Float = 3f,
    intensity: () -> Float = { 1f },
    pressed: () -> Float = { 0f },
    touch: () -> GlassTouchLight? = { null },
    profile: GlassLiquidProfile = GlassLiquidProfile.Default,
  ) = GlassLiquidSurface(
    role = role, optics = optics.sanitized(), shape = shape, darkSide = dark, screenDensity = density,
    intensity = intensity, opticalDepth = { 1f }, pressed = pressed, quality = { 1f },
    film = { Color.Unspecified }, tint = { Color.Unspecified }, touch = touch, profile = profile,
  )

  @Test
  fun theBodyLivesInTheCaptureAndTheLightOnTheScreen() {
    val capsule = surface(GlassRole.Floating, FluidCapsuleShape)
    val size = Size(156f * 3f, 56f * 3f)
    val full = capsule.drawInputs(size, scale = 1f)
    val thickness = full.thickness
    val rimWidth = full.rimWidth
    val lightRadius = full.light.radii[0]
    assertEquals(84f, full.body.radii[0], 1e-3f)
    assertEquals(84f, lightRadius, 1e-3f)
    assertEquals(0.6f * 3f, rimWidth, 1e-5f)

    val reduced = capsule.drawInputs(size, scale = 0.4f)
    // Il corpo: taglia, raggi e lunghezze nel mondo rimpicciolito della cattura.
    assertEquals(thickness * 0.4f, reduced.thickness, 1e-3f)
    assertEquals(84f * 0.4f, reduced.body.radii[0], 1e-3f)
    assertEquals(size.width * 0.4f / 2f, reduced.body.rects[0], 1e-3f)
    assertEquals(size.height * 0.4f / 2f, reduced.body.rects[3], 1e-3f)
    // La luce: sempre in pixel dello schermo. Il filo non si assottiglia con la risoluzione della cattura.
    assertEquals(rimWidth, reduced.rimWidth, 0f)
    assertEquals(lightRadius, reduced.light.radii[0], 0f)
    assertEquals(size.width / 2f, reduced.light.rects[0], 0f)
    // L'antialias è un pixel e un quarto dello spazio in cui ognuno gira.
    assertEquals(GlassLiquidAa, reduced.aa, 0f)
    assertEquals(GlassLiquidAa, reduced.lightAa, 0f)
  }

  @Test
  fun theCaptureMarginIsWholePixelsAndDoesNotMoveWithIntensityQualityOrPressure() {
    var intensity = 1f
    var pressed = 0f
    // Con la smerigliatura di serie (4,5 dp) la lente copre tutto e il margine è zero; con 12 dp il tasto ne ha
    // 1,44 dp, ed è lì che si vede come si arrotonda.
    assertEquals(
      0f,
      surface(GlassRole.Interactive, FluidCapsuleShape, GlassDefaults.buttonOptics()).drawInputs(Size(132f, 132f), scale = 1f).paddingPx,
      0f,
    )
    val button = surface(
      GlassRole.Interactive, FluidCapsuleShape, optics = GlassDefaults.buttonOptics(),
      intensity = { intensity }, pressed = { pressed },
      profile = GlassLiquidProfile.Default.copy(frost = GlassLiquidFrosts.Radius12),
    )
    val size = Size(132f, 132f)
    val rest = button.drawInputs(size, scale = 1f)
    val padding = rest.paddingPx
    val refraction = rest.refraction
    // 1,44 dp a densità 3 sono 4,32 px: troncati, come nel banco.
    assertEquals(4f, padding, 0f)
    assertEquals(-padding, rest.bodyOffsetX, 0f)
    assertEquals(-padding, rest.bodyOffsetY, 0f)

    pressed = 1f
    val down = button.drawInputs(size, scale = 1f)
    assertEquals(padding, down.paddingPx, 0f)
    assertTrue("la pressione approfondisce la lente", down.refraction < refraction)
    intensity = 0.3f
    val fading = button.drawInputs(size, scale = 1f)
    assertEquals(padding, fading.paddingPx, 0f)
    assertEquals(0.3f, fading.materialize, 0f)
    assertEquals(0.3f, fading.lightMaterialize, 0f)
  }

  @Test
  fun theGlowComesFromTheFingerAndRelightsTheFirstDraw() {
    var amount = 0f
    var finger = Offset(30f, 200f)
    val touch = GlassTouchLight(amount = { amount }, position = { finger })
    val size = Size(132f, 132f)
    // Sul lato scuro col solo riflesso (la variante A del bordo scuro) il filo non c'è: a riposo un disegno solo,
    // quello che somma.
    val dark = surface(
      GlassRole.Interactive, FluidCapsuleShape, GlassDefaults.buttonOptics(), dark = true, touch = { touch },
      profile = GlassLiquidProfile.Default.copy(darkEdge = GlassLiquidEdges.DarkReflex),
    )
    val rest = dark.drawInputs(size, scale = 1f)
    assertTrue(rest.drawsGlowing)
    assertFalse(rest.drawsRim)
    assertEquals(0f, rest.glow, 0f)

    amount = 1f
    val down = dark.drawInputs(size, scale = 1f)
    assertEquals(GlassLiquidGlowPeak, down.glow, 0f)
    assertEquals(GlassLiquidGlowFill, down.glowFill, 0f)
    // Il raggio è il lato lungo; il dito resta dentro la lastra.
    assertEquals(132f, down.glowRadius, 0f)
    assertEquals(30f, down.glowCenterX, 0f)
    assertEquals(132f, down.glowCenterY, 0f)

    // Un'ottica senza bordo non ha un riflesso da sommare: il primo disegno esiste solo sotto il dito.
    val bare = GlassDefaults.buttonOptics().copy(highlightAlpha = 0f)
    val quiet = surface(GlassRole.Interactive, FluidCapsuleShape, bare, touch = { touch })
    amount = 0f
    val idle = quiet.drawInputs(size, scale = 1f)
    assertFalse(idle.drawsGlowing)
    assertFalse(idle.drawsRim)
    amount = 0.5f
    val touched = quiet.drawInputs(size, scale = 1f)
    assertTrue(touched.drawsGlowing)
    assertFalse(touched.drawsRim)
    assertEquals(GlassLiquidGlowPeak * 0.5f, touched.glow, 1e-6f)

    // Senza un dito il centro è quello della lastra.
    finger = Offset.Unspecified
    val centred = quiet.drawInputs(size, scale = 1f)
    assertEquals(66f, centred.glowCenterX, 0f)
    assertEquals(66f, centred.glowCenterY, 0f)
  }

  @Test
  fun atZeroIntensityNothingIsLit() {
    val gone = surface(GlassRole.Floating, FluidCapsuleShape, intensity = { 0f })
    val inputs = gone.drawInputs(Size(468f, 168f), scale = 1f)
    assertFalse(inputs.drawsGlowing)
    assertFalse(inputs.drawsRim)
    assertEquals(0f, inputs.materialize, 0f)
    // Tutto nitido, e il margine è quello di sempre: uscire da zero non rifà la cattura.
    assertEquals(1f, inputs.mix, 0f)
    assertEquals(
      surface(GlassRole.Floating, FluidCapsuleShape).drawInputs(Size(468f, 168f), scale = 1f).paddingPx,
      inputs.paddingPx,
      0f,
    )
  }

  // ---- I due disegni della luce ------------------------------------------------------------------------------

  /** Uno shader senza piattaforma: annota gli uniform, come il doppio di `GlassShaderFallbackTest` annota le richieste. */
  private class RecordingShader : RuntimeShader {
    val floats = HashMap<String, FloatArray>()
    val colors = HashMap<String, Color>()
    override fun setFloatUniform(name: String, value: Float) { floats[name] = floatArrayOf(value) }
    override fun setFloatUniform(name: String, value1: Float, value2: Float) { floats[name] = floatArrayOf(value1, value2) }
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {
      floats[name] = floatArrayOf(value1, value2, value3)
    }
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {
      floats[name] = floatArrayOf(value1, value2, value3, value4)
    }
    override fun setFloatUniform(name: String, values: FloatArray) { floats[name] = values.copyOf() }
    override fun setIntUniform(name: String, value: Int) = error("la luce non ha uniform interi")
    override fun setIntUniform(name: String, value1: Int, value2: Int) = error("la luce non ha uniform interi")
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) = error("la luce non ha uniform interi")
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) =
      error("la luce non ha uniform interi")
    override fun setIntUniform(name: String, values: IntArray) = error("la luce non ha uniform interi")
    override fun setColorUniform(name: String, color: Color) { colors[name] = color }
  }

  /** Un disegno della luce come lo vede il canvas: il modo di comporre, e `lightPart` in quel momento. */
  private data class LightDraw(val blend: BlendMode, val lightPart: Float)

  /** Disegna la luce di [inputs] su un canvas che annota i rettangoli; gli uniform restano in [shader]. */
  private fun lightDraws(inputs: GlassDrawInputs, shader: RecordingShader, size: Size): List<LightDraw> {
    val draws = ArrayList<LightDraw>()
    val canvas = Proxy.newProxyInstance(Canvas::class.java.classLoader, arrayOf(Canvas::class.java)) { _, method, args ->
      if (method.name == "drawRect") {
        val paint = args!!.last() as Paint
        draws += LightDraw(paint.blendMode, shader.floats.getValue("lightPart").single())
      }
      null
    } as Canvas
    CanvasDrawScope().draw(density3, LayoutDirection.Ltr, canvas, size) {
      // Un pennello qualsiasi: ciò che si guarda è quante volte, in che ordine e con che modo di comporre.
      drawGlassLight(inputs, shader, SolidColor(Color.White))
    }
    return draws
  }

  @Test
  fun theLightIsTwoDrawsFirstTheOneThatAddsThenTheWire() {
    val size = Size(468f, 168f)
    val light = surface(GlassRole.Floating, FluidCapsuleShape).drawInputs(size, scale = 0.4f)
    val shader = RecordingShader()
    assertEquals(
      listOf(LightDraw(BlendMode.Plus, 0f), LightDraw(BlendMode.SrcOver, 1f)),
      lightDraws(light, shader, size),
    )
    // Gli uniform della luce sono in pixel dello schermo, e la flessione è l'identità.
    assertEquals(listOf(0f, 0f), shader.floats.getValue("offset").toList())
    assertEquals(listOf(1f, 0f, 0f, 1f), shader.floats.getValue("flex").toList())
    assertEquals(listOf(234f, 84f, 234f, 84f), shader.floats.getValue("pieceRect").toList())
    assertEquals(List(4) { 84f }, shader.floats.getValue("pieceRadii").toList())
    assertEquals(listOf(1f), shader.floats.getValue("pieceCount").toList())
    assertEquals(1.8f, shader.floats.getValue("rimWidth").single(), 1e-5f)
    assertEquals(0f, shader.floats.getValue("glow").single(), 0f)
    // Ogni uniform che il programma dichiara è impostato: l'istanza è una per processo, e ciò che una
    // superficie non imposta resta quello dell'ultima che ha disegnato.
    val declared = Regex("""uniform\s+(?!shader\b)\w+\s+(\w+)\s*(?:\[\d+])?\s*;""")
      .findAll(GlassLight1ShaderString).map { it.groupValues[1] }.toSet()
    assertEquals(declared, shader.floats.keys + shader.colors.keys)
  }

  @Test
  fun theDarkSideDrawsTheLightOnceAndNothingAtAllWhenAbsent() {
    val size = Size(468f, 168f)
    // Di serie (la B: riflesso e filo chiaro) due disegni come sul chiaro; col solo riflesso uno.
    val wire = surface(GlassRole.Floating, FluidCapsuleShape, dark = true).drawInputs(size, scale = 1f)
    assertEquals(
      listOf(LightDraw(BlendMode.Plus, 0f), LightDraw(BlendMode.SrcOver, 1f)),
      lightDraws(wire, RecordingShader(), size),
    )
    val reflexOnly = GlassLiquidProfile.Default.copy(darkEdge = GlassLiquidEdges.DarkReflex)
    val dark = surface(GlassRole.Floating, FluidCapsuleShape, dark = true, profile = reflexOnly).drawInputs(size, scale = 1f)
    assertEquals(listOf(LightDraw(BlendMode.Plus, 0f)), lightDraws(dark, RecordingShader(), size))

    val gone = surface(GlassRole.Floating, FluidCapsuleShape, intensity = { 0f }).drawInputs(size, scale = 1f)
    val untouched = RecordingShader()
    assertEquals(emptyList<LightDraw>(), lightDraws(gone, untouched, size))
    // Niente da disegnare: lo shader condiviso non si tocca nemmeno.
    assertTrue(untouched.floats.isEmpty() && untouched.colors.isEmpty())
  }

  // ---- Il ritaglio hardware ----------------------------------------------------------------------------------

  private fun clip(surface: GlassLiquidSurface, size: Size): Outline =
    surface.clipShapeBlock().createOutline(size, LayoutDirection.Ltr, density3)

  @Test
  fun theHardwareClipIsARoundedRectAt98PercentOfASmoothedCorner() {
    val menu = surface(GlassRole.Modal, ContinuousCornerShape(28.dp), GlassDefaults.panelOptics(compact = true))
    val outline = clip(menu, Size(510f, 576f))
    // Un contorno arrotondato, mai un path: il ritaglio resta quello hardware.
    assertTrue(outline is Outline.Rounded)
    val rect = (outline as Outline.Rounded).roundRect
    assertEquals(84f * 0.98f, rect.topLeftCornerRadius.x, 1e-4f)
    assertEquals(84f * 0.98f, rect.bottomRightCornerRadius.y, 1e-4f)
    assertEquals(510f, rect.width, 0f)
    // È la stessa frazione che il corpo riceve.
    assertEquals(GlassSmoothedClipScale, menu.drawInputs(Size(510f, 576f)).clipScale, 0f)

    // Capsule e piccoli: il ritaglio sta sugli stessi raggi del campo.
    val capsule = clip(surface(GlassRole.Floating, FluidCapsuleShape), Size(468f, 168f)) as Outline.Rounded
    assertEquals(84f, capsule.roundRect.topLeftCornerRadius.x, 0f)
    val chip = clip(surface(GlassRole.Floating, ContinuousCornerShape(10.dp)), Size(216f, 108f)) as Outline.Rounded
    assertEquals(30f * ContinuousCornerShape.RoundedEquivalentFactor, chip.roundRect.topLeftCornerRadius.x, 1e-4f)
    // Un rettangolo si ritaglia da rettangolo.
    assertTrue(clip(surface(GlassRole.Bar, RectangleShape), Size(1080f, 192f)) is Outline.Rectangle)
    // La lambda è una sola per superficie: l'elemento del nodo resta uguale a se stesso.
    assertSame(menu.clipShapeBlock, menu.clipShapeBlock)
    assertSame(menu.clipShapeBlock(), menu.clipShapeBlock())
  }

  // ---- L'ombra -----------------------------------------------------------------------------------------------

  /** Risolve la sagoma dell'ombra come fa il nodo (prima la sagoma, poi i numeri) e restituisce i numeri. */
  private fun shadowOf(surface: GlassLiquidSurface, size: Size, pieces: GlassShadowPieces = GlassShadowPieces()): GlassShadowSpec? {
    var spec: GlassShadowSpec? = null
    CanvasDrawScope().draw(density3, LayoutDirection.Ltr, Canvas(android.graphics.Canvas()), size) {
      with(surface.shadowOutline) { resolve(pieces) }
      spec = surface.shadow()
    }
    return spec
  }

  @Test
  fun theShadowHasTheFieldSilhouetteAndGrowsWithTheSlab() {
    val pieces = GlassShadowPieces()
    val panel = surface(GlassRole.Modal, ContinuousCornerShape(36.dp), GlassDefaults.panelOptics())
    val big = shadowOf(panel, Size(990f, 1140f), pieces)
    assertNotNull(big)
    assertEquals(1, pieces.count)
    assertEquals(listOf(495f, 570f, 495f, 570f), pieces.rects.take(4))
    assertEquals(List(4) { 108f }, pieces.radii.take(4))
    assertEquals(ContinuousCornerShape.IosSmoothing, pieces.smooth[0], 0f)

    val capsule = surface(GlassRole.Floating, FluidCapsuleShape)
    val small = shadowOf(capsule, Size(468f, 168f))!!
    // La regola della taglia: il pannello ha il guadagno pieno, la capsula nessuno.
    assertTrue(big!!.ambientRadius > small.ambientRadius)
    assertTrue(big.ambientAlpha > small.ambientAlpha)
    assertTrue(capsule.castsShadow)
  }

  @Test
  fun theShadowNumbersAreKeptUntilTheyChange() {
    var intensity = 1f
    val capsule = surface(GlassRole.Floating, FluidCapsuleShape, intensity = { intensity })
    val size = Size(468f, 168f)
    val first = shadowOf(capsule, size)
    // A riposo i numeri non cambiano: lo stesso oggetto, nessuna allocazione per fotogramma.
    assertSame(first, shadowOf(capsule, size))
    intensity = 0.5f
    val half = shadowOf(capsule, size)!!
    assertEquals(first!!.ambientAlpha * 0.5f, half.ambientAlpha, 1e-6f)
    // I raggi non dipendono dall'intensità: le maschere restano buone.
    assertEquals(first.ambientRadius, half.ambientRadius)
    intensity = 0f
    assertNull(shadowOf(capsule, size))
  }

  @Test
  fun aRoleWithoutAShadowDoesNotMountTheNode() {
    assertFalse(surface(GlassRole.Bar, RectangleShape).castsShadow)
    assertFalse(surface(GlassRole.Content, ContinuousCornerShape(20.dp)).castsShadow)
    for (role in listOf(GlassRole.Floating, GlassRole.Interactive, GlassRole.Modal)) {
      assertTrue(role.name, surface(role, FluidCapsuleShape).castsShadow)
    }
  }

  // ---- La frazione di risoluzione ----------------------------------------------------------------------------

  @Test
  fun everyStockRoleCapturesAtTheSameScaleWhateverItsSize() {
    val sizes = listOf(44f to 44f, 156f to 56f, 170f to 192f, 330f to 380f)
    val frost = GlassLiquidProfile.Default.frost.blur.value
    for (role in GlassRole.entries) {
      val optics = GlassDefaults.optics(role)
      val atComposition = glassLiquidCaptureScale(role, optics)
      // La smerigliatura del ruolo (4,5 dp di serie: il 73% della risoluzione), per la `backdropResolution`
      // dell'ottica, mai sotto il pavimento.
      val expected = max(glassResolutionScale(frost) * optics.backdropResolution, GlassLiquidMinCaptureScale).coerceAtMost(1f)
      assertEquals(role.name, expected, atComposition, 0f)
      // Decisa in composizione, senza la taglia: è quella che la ricetta dà a ogni taglia, anche ai grandi che
      // sfocano un poco di più.
      for ((w, h) in sizes) {
        assertEquals("$role $w x $h", glassRecipe(role, w, h, darkSide = false, optics = optics).captureScale, atComposition, 0f)
      }
      // Con una smerigliatura oltre i 10 dp ogni ruolo scende al pavimento, come con quella di F1A.5.
      val frosted = GlassLiquidProfile.Default.copy(frost = GlassLiquidFrosts.Radius12)
      assertEquals(role.name, GlassLiquidMinCaptureScale, glassLiquidCaptureScale(role, optics, frosted), 0f)
    }
    // Un'ottica che spegne la sfocatura cattura a risoluzione piena: una lente limpida è tutta bordo.
    val clear = GlassDefaults.optics(GlassRole.Interactive).copy(blurScale = 0f)
    assertEquals(1f, glassLiquidCaptureScale(GlassRole.Interactive, clear), 0f)
  }
}
