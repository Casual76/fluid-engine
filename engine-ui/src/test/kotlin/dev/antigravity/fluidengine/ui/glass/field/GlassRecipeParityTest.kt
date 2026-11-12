package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import dev.antigravity.fluidengine.ui.fluid.isFlatVeil
import dev.antigravity.fluidengine.ui.fluid.resolveFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidFormPresets
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLiquidSurface
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.resolvePhysicsGlass
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScopeImpl
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **Un materiale solo** (F1B.6, piano 4.4): le strade che oggi chiamano il materiale `Liquid` gli passano la stessa
 * cosa. Non prova che la ricetta sia uguale a se stessa: passa dagli **adattatori** di ogni strada, cioè da ciò che il
 * nodo riceve davvero.
 *
 * Le strade sono cinque: `glassSurface` (la lastra ferma, [GlassLiquidSurface]); Fluid-physics, ferma e in viaggio
 * ([PhysicsLiquidSurface]); la pillola della tab bar ([glassPuckProfile]); la goccia dei controlli e dei menù
 * ([glassDropProfile]); il vetro dipinto di fogli e avvisi in un'altra finestra ([glassPaintedRecipe]).
 *
 * ### A · `glassSurface` e Fluid-physics ferma, e i capi di un viaggio
 * Stesso ruolo, stessa ottica, stessa taglia, stesso tema, stesso stato (intensità, qualità, pellicola): i numeri del
 * corpo, della luce e dell'ombra coincidono. Si confrontano **in dp** (o adimensionali), perché i due nodi catturano a
 * risoluzioni diverse (vedi sotto). La tolleranza è [Tolerance]: i due lati fanno gli stessi conti in float32, ma uno
 * porta le lunghezze nello spazio della cattura a frazione di risoluzione e le riporta indietro (due prodotti e una
 * divisione), l'altro no. Un millesimo di dp su un filo è un trecentesimo di pixel: sotto ogni cosa che si vede.
 * L'ombra ha [ShadowTolerancePx], perché il corpo riceve i raggi passati per il sigma della piattaforma e ritorno
 * (`GlassShadowProfile.scaledRadius`).
 *
 * Ciò che **non** si confronta, per scelta scritta nel codice (non per comodità del test):
 *  - il **margine della cattura** e lo scostamento del corpo: Fluid-physics ha un margine fisso per tutto il viaggio
 *    (`glassTravelPaddingDp`), una lastra quello della sua taglia; cambia quanti pixel attorno si catturano, non il vetro;
 *  - la **risoluzione della cattura**: Fluid-physics cattura a risoluzione piena (`PhysicsLiquidCaptureScale`, perché
 *    filo e riflesso li compone il corpo), una lastra alla frazione della ricetta. Per questo si confronta in dp;
 *  - il **programma**: una lastra ferma usa `GlassBody1` e la luce sul nodo, Fluid-physics `GlassBody8` con la luce
 *    nel corpo e il ritaglio del nodo emulato (`nodeClip`); che i due programmi siano la stessa formula lo dicono il banco
 *    (`riposo-contro-viaggio`, entro 2 livelli su 255) e `PhysicsLiquidTest`;
 *  - `aa`, che è un numero di pixel dello spazio in cui gira e resta lo stesso numero in entrambi (si confronta così);
 *  - la goccia viva (`liquidAllowed`): il primo fotogramma di un viaggio con la goccia è l'anello, un altro nucleo, e
 *    il suo raccordo con la lastra lo misurano le scene `f1b/riposo-contro-viaggio-*` e `DropPlanTest`.
 *
 * ### B · I profili derivati seguono quello di serie
 * Pillola, goccia e dipinto non hanno numeri del materiale loro, salvo quelli che dichiarano. Si prova in due modi:
 * cambiando velo, filo, riflesso, ombra, smerigliatura e lente di [GlassLiquidProfile.Shipped] in una copia, ciò che
 * cambia nella ricetta di serie cambia anche nella ricetta del derivato (salvo i campi che il derivato dichiara suoi);
 * e leggendo il sorgente dei tre profili: i numeri scritti lì sono un **elenco chiuso** ([OwnNumbers]). Un numero nuovo
 * non dichiarato fa fallire il test, e uno dichiarato che sparisce anche.
 *
 * ### C · Sotto Android 13 e con `Classic` nessuna strada prende il materiale nuovo
 * La tabella di verità intera: modello chiesto × livello di API × livello di Fluid-physics chiesto (`Full`, `Balanced`,
 * `Lite`, o nessuno), per le cinque strade. Il livello di API è un parametro delle funzioni di scelta, ed è lo stesso
 * numero che il livello simulato delle build di debug dà loro (`glassApiLevel`): 32 qui è 32 simulato sul tablet.
 *
 * ### Disparità note
 * Si scrivono come prove che falliscono, saltate con la ragione (`NOTO F1B.6 …` in uscita), come quelle di F1B.8. Se
 * il codice cambia e la prova passa, il test fallisce per chiedere di togliere la riga: una nota non resta indietro.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GlassRecipeParityTest {

  private companion object {
    /** Assoluta più relativa: vedi in testa. */
    const val Tolerance: Float = 1e-4f
    const val RelativeTolerance: Float = 1e-5f

    /** Raggi e scostamenti dell'ombra, in pixel: passano dal sigma della piattaforma e ritorno. */
    const val ShadowTolerancePx: Float = 1e-3f
  }

  // ------------------------------------------------------------------------------------------------------------
  // Le forme e gli stati
  // ------------------------------------------------------------------------------------------------------------

  private val neutral =
    GlassTint(overlay = Color.Transparent, fallback = Color.Transparent, hairline = Color.Transparent, mode = GlassTintMode.Neutral)

  /** Una pellicola dipinta com'è (`Fixed`): prende il posto del velo sulle due strade. */
  private val film = GlassTint(overlay = Color(0x5C2A7FFF), fallback = Color(0xFF2A7FFF), hairline = Color.Transparent)

  /** Una taglia della prova: la stessa forma come `Shape` (per `glassSurface`) e come `FluidForm` (per Fluid-physics). */
  private class Piece(
    val name: String,
    val role: GlassRole,
    val optics: GlassOptics,
    val widthDp: Float,
    val heightDp: Float,
    val radiusDp: Float?,
  ) {
    val shape: Shape = if (radiusDp == null) FluidCapsuleShape else ContinuousCornerShape(radiusDp.dp)

    fun form(density: Float): FluidForm.Slab {
      val frame = Rect(0f, 0f, widthDp * density, heightDp * density)
      return if (radiusDp == null) {
        FluidFormPresets.capsule(frame)
      } else {
        FluidForm.Slab(frame, FluidCornerRadii.all(radiusDp * density), ContinuousCornerShape.IosSmoothing)
      }
    }
  }

  /** Le tre taglie della pagina «Parità»: tasto, capsula, pannello. */
  private val pieces = listOf(
    Piece("tasto", GlassRole.Interactive, GlassDefaults.buttonOptics().sanitized(), 44f, 44f, null),
    Piece("capsula", GlassRole.Floating, GlassDefaults.optics(GlassRole.Floating).sanitized(), 200f, 52f, null),
    Piece("pannello", GlassRole.Modal, GlassDefaults.panelOptics().sanitized(), 320f, 240f, 28f),
  )

  private class State(val name: String, val intensity: Float, val quality: Float, val tint: (GlassTint, GlassTint) -> GlassTint)

  private val states = listOf(
    State("riposo", 1f, 1f) { n, _ -> n },
    State("mezza intensità", 0.5f, 1f) { n, _ -> n },
    State("qualità di scorrimento", 1f, 0.6f) { n, _ -> n },
    State("pellicola", 1f, 1f) { _, f -> f },
  )

  private val densities = listOf(2f, 3f)

  // ------------------------------------------------------------------------------------------------------------
  // Gli adattatori: ciò che il nodo riceve
  // ------------------------------------------------------------------------------------------------------------

  private fun stillSurface(piece: Piece, dark: Boolean, density: Float, state: State, tint: GlassTint): GlassLiquidSurface {
    val mode = dev.antigravity.fluidengine.ui.fluid.glassTintLiquidMode(tint)
    return GlassLiquidSurface(
      role = piece.role,
      optics = piece.optics,
      shape = piece.shape,
      darkSide = dark,
      screenDensity = density,
      intensity = { state.intensity },
      opticalDepth = { 1f },
      pressed = { 0f },
      quality = { state.quality },
      film = { if (mode == GlassTintMode.Fixed) tint.overlay else Color.Unspecified },
      tint = { if (mode == GlassTintMode.Colored) tint.overlay else Color.Unspecified },
      touch = { null },
    )
  }

  private fun physicsSurface(
    physics: FluidPhysicsState,
    piece: Piece,
    dark: Boolean,
    density: Float,
    state: State,
    tint: GlassTint,
    widthPx: Float,
  ): PhysicsLiquidSurface = PhysicsLiquidSurface(
    state = physics,
    role = piece.role,
    material = PhysicsMaterialSource(null, piece.optics),
    darkSide = dark,
    screenDensity = density,
    tint = tint,
    tintFrom = null,
    pressState = null,
    glowWeight = 1f,
    profile = GlassLiquidProfile.Default,
    intensity = { state.intensity },
    tintBlend = { 1f },
    opticsBlend = { 1f },
    quality = { state.quality },
    layoutWidth = { widthPx },
    onPanel = { false },
  )

  /**
   * Gli ingressi che la superficie fisica ha appena dato al corpo. `PhysicsLiquidSurface` non ha (ancora) un
   * adattatore pubblico come `GlassLiquidSurface.drawInputs`: si chiamano i suoi `effects` con lo scope che il nodo
   * darebbe (a risoluzione piena, la sua) e si legge il contenitore che ha riempito. Sul JVM `glassBody` esce prima di
   * toccare uno shader (`isRuntimeShaderSupported()` è falso), dopo che gli ingressi sono scritti.
   */
  private fun physicsInputs(surface: PhysicsLiquidSurface, widthPx: Float, heightPx: Float, density: Float): GlassDrawInputs {
    // La forma del nodo di una superficie fisica è il rettangolo del palco: il corpo non la legge.
    val scope = object : BackdropEffectScopeImpl() {
      override val shape: Shape get() = androidx.compose.ui.graphics.RectangleShape
    }
    scope.density = density
    scope.size = Size(widthPx, heightPx)
    surface.effects(scope)
    val field = PhysicsLiquidSurface::class.java.getDeclaredField("inputs").apply { isAccessible = true }
    return field.get(surface) as GlassDrawInputs
  }

  private fun shadowOf(surface: GlassLiquidSurface, size: Size, density: Float): GlassShadowSpec? {
    var spec: GlassShadowSpec? = null
    CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(android.graphics.Canvas()), size) {
      with(surface.shadowOutline) { resolve(GlassShadowPieces()) }
      spec = surface.shadow()
    }
    return spec
  }

  // ------------------------------------------------------------------------------------------------------------
  // I numeri, in dp o adimensionali
  // ------------------------------------------------------------------------------------------------------------

  private fun MutableMap<String, Float>.color(name: String, c: Color) {
    put("$name.r", c.red)
    put("$name.g", c.green)
    put("$name.b", c.blue)
    put("$name.a", c.alpha)
  }

  /**
   * Corpo e luce di un contenitore di ingressi, in dp. [captureDensity] porta di là le lunghezze del corpo; la luce sta
   * nel corpo per Fluid-physics (nello spazio della cattura) e sul nodo per una lastra (pixel dello schermo).
   */
  private fun material(inputs: GlassDrawInputs, captureDensity: Float, screenDensity: Float): Map<String, Float> {
    val m = LinkedHashMap<String, Float>()
    m["saturation"] = inputs.saturation
    m["blurDp"] = inputs.blurPx / captureDensity
    m["mix"] = inputs.mix
    m["thicknessDp"] = inputs.thickness / captureDensity
    m["refractionDp"] = inputs.refraction / captureDensity
    m["dome"] = inputs.dome
    m["dispersion"] = inputs.dispersion
    m["magnify"] = inputs.magnify
    m["materialize"] = inputs.materialize
    m.color("veilColor", inputs.veilColor)
    m["veilAdapt"] = inputs.veilAdapt
    m.color("tintColor", inputs.tintColor)
    m["tintMode"] = inputs.tintMode
    m["tintFollow"] = inputs.tintFollow
    m.color("bodyShade", inputs.bodyShade)
    m["bodyShadeDepthDp"] = inputs.bodyShadeDepth / captureDensity
    m["bodyShadeLeanX"] = inputs.bodyShadeLeanX
    m["bodyShadeLeanY"] = inputs.bodyShadeLeanY
    m["dim"] = inputs.dim
    m["grain"] = inputs.grain
    m["aa"] = inputs.aa
    // La luce.
    m["rimWidthDp"] = if (inputs.lightInBody) inputs.bodyRimWidth / captureDensity else inputs.rimWidth / screenDensity
    m.color("rimDark", inputs.rimDark)
    m["rimFeather"] = inputs.rimFeather
    m["rimLift"] = inputs.rimLift
    m["reflexDirX"] = inputs.reflexDirX
    m["reflexDirY"] = inputs.reflexDirY
    m["reflexHalfWidth"] = inputs.reflexHalfWidth
    m["reflexOppositeHalfWidth"] = inputs.reflexOppositeHalfWidth
    m["reflexStrength"] = inputs.reflexStrength
    m["reflexDepthDp"] = if (inputs.lightInBody) inputs.bodyReflexDepth / captureDensity else inputs.reflexDepth / screenDensity
    m["reflexFloor"] = inputs.reflexFloor
    m["reflexHalo"] = inputs.reflexHalo
    m.color("lightColor", inputs.lightColor)
    m["lightMaterialize"] = inputs.lightMaterialize
    m["glow"] = inputs.glow
    m["glowFill"] = inputs.glowFill
    m["glowRadiusDp"] = if (inputs.lightInBody) inputs.bodyGlowRadius / captureDensity else inputs.glowRadius / screenDensity
    m["drawsRim"] = if (inputs.drawsRim) 1f else 0f
    m["drawsGlowing"] = if (inputs.drawsGlowing) 1f else 0f
    return m
  }

  /** La sagoma del corpo, in pixel dello schermo relativi all'angolo del nodo: mezze misure, raggi, raccordo, centro. */
  private fun outline(plan: GlassFieldPlan, toScreen: Float): Map<String, Float> {
    val m = LinkedHashMap<String, Float>()
    m["count"] = plan.count.toFloat()
    for (i in 0 until plan.count) {
      val at = i * GlassFieldGeometry.FloatsPerPiece
      m["$i.centerX"] = plan.rects[at] / toScreen
      m["$i.centerY"] = plan.rects[at + 1] / toScreen
      m["$i.halfWidth"] = plan.rects[at + 2] / toScreen
      m["$i.halfHeight"] = plan.rects[at + 3] / toScreen
      for (k in 0 until 4) m["$i.radius$k"] = plan.radii[at + k] / toScreen
      m["$i.smooth"] = plan.smooth[i]
      m["$i.blend"] = plan.blend[i] / toScreen
    }
    return m
  }

  /** L'ombra di una lastra (le maschere a riposo), in dp e alfa con l'intensità. */
  private fun shadow(spec: GlassShadowSpec?): Map<String, Float> {
    val m = LinkedHashMap<String, Float>()
    if (spec == null) return m
    m["contactRadiusDp"] = spec.contactRadius.value
    m["ambientRadiusDp"] = spec.ambientRadius.value
    m["offsetXDp"] = spec.offset.x.value
    m["offsetYDp"] = spec.offset.y.value
    m["contactAlpha"] = spec.contactAlpha
    m["ambientAlpha"] = spec.ambientAlpha
    m.color("color", spec.color)
    return m
  }

  /** L'ombra nel corpo di Fluid-physics, negli stessi termini: le alfa tornano moltiplicate per `materialize`. */
  private fun shadow(inputs: GlassDrawInputs, screenDensity: Float, scale: Float): Map<String, Float> {
    val m = LinkedHashMap<String, Float>()
    if (inputs.shadowContactAlpha + inputs.shadowAmbientAlpha <= 0f) return m
    // Il corpo riceve `scaledRadius(raggio, scala)`: a scala uno è il raggio, a meno dell'andata e ritorno dal sigma.
    m["contactRadiusDp"] = GlassShadowProfile.scaledRadius(inputs.shadowContactRadius, 1f / scale) / screenDensity
    m["ambientRadiusDp"] = GlassShadowProfile.scaledRadius(inputs.shadowAmbientRadius, 1f / scale) / screenDensity
    m["offsetXDp"] = inputs.shadowOffsetX / scale / screenDensity
    m["offsetYDp"] = inputs.shadowOffsetY / scale / screenDensity
    m["contactAlpha"] = inputs.shadowContactAlpha * inputs.materialize
    m["ambientAlpha"] = inputs.shadowAmbientAlpha * inputs.materialize
    m.color("color", inputs.shadowColor)
    return m
  }

  /** Le differenze fra due elenchi di numeri, oltre [tol] (più il relativo): vuoto se coincidono. */
  private fun differences(expected: Map<String, Float>, actual: Map<String, Float>, tol: Float = Tolerance): List<String> {
    val out = ArrayList<String>()
    if (expected.keys != actual.keys) out += "campi diversi: ${expected.keys} contro ${actual.keys}"
    for ((key, e) in expected) {
      val a = actual[key] ?: continue
      if (abs(e - a) > tol + abs(e) * RelativeTolerance) out += "$key: $e contro $a"
    }
    return out
  }

  private fun assertSameNumbers(where: String, expected: Map<String, Float>, actual: Map<String, Float>, tol: Float = Tolerance) {
    val diff = differences(expected, actual, tol)
    assertTrue("$where:\n  " + diff.joinToString("\n  "), diff.isEmpty())
  }

  // ------------------------------------------------------------------------------------------------------------
  // A · glassSurface e Fluid-physics ferma
  // ------------------------------------------------------------------------------------------------------------

  @Test
  fun aStillPhysicsSurfaceIsTheGlassSurfaceOfTheSameShape() {
    var compared = 0
    for (piece in pieces) for (dark in listOf(false, true)) for (density in densities) for (state in states) {
      val where = "${piece.name}, ${if (dark) "scuro" else "chiaro"}, densità $density, ${state.name}"
      val tint = state.tint(neutral, film)
      val w = piece.widthDp * density
      val h = piece.heightDp * density
      val size = Size(w, h)

      // glassSurface: l'adattatore del nodo, alla risoluzione a cui cattura.
      val still = stillSurface(piece, dark, density, state, tint)
      val scale = still.effectiveCaptureScale(size)
      val g = still.drawInputs(size)
      val gMaterial = material(g, density * scale, density)
      val gOutline = outline(g.body, scale)
      val gLightOutline = outline(g.light, 1f)
      val gShadow = shadow(shadowOf(still, size, density))

      // Fluid-physics ferma: la stessa forma, nodo grande quanto lei, a risoluzione piena.
      val physics = FluidPhysicsState(piece.form(density)).apply {
        this.density = density
        maskInShader = true
      }
      val p = physicsInputs(physicsSurface(physics, piece, dark, density, state, tint, w), w, h, density)

      assertTrue("$where: la luce di Fluid-physics è nel corpo", p.lightInBody)
      assertEquals("$where: un pezzo", 1, p.body.count)
      assertSameNumbers("$where, corpo e luce", gMaterial, material(p, density, density))
      assertSameNumbers("$where, sagoma del corpo", gOutline, outline(p.body, 1f))
      assertSameNumbers("$where, sagoma della luce sul nodo contro quella nel corpo", gLightOutline, outline(p.body, 1f))
      assertSameNumbers("$where, ombra", gShadow, shadow(p, density, 1f), ShadowTolerancePx / density)
      compared++
    }
    assertEquals(pieces.size * 2 * densities.size * states.size, compared)
  }

  // ------------------------------------------------------------------------------------------------------------
  // A · i capi di un viaggio
  // ------------------------------------------------------------------------------------------------------------

  private class FrameClock : MonotonicFrameClock {
    val frames = Channel<Long>(Channel.UNLIMITED)
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
  }

  private class Journey(val name: String, val from: Piece, val to: Piece)

  /** Due viaggi con lo stesso ruolo e la stessa ottica: fra due capsule, e da un tasto a un pannello (la fascia dei veli). */
  private val journeys: List<Journey> = run {
    val floating = GlassDefaults.optics(GlassRole.Floating).sanitized()
    val button = Piece("tasto", GlassRole.Floating, floating, 44f, 44f, null)
    val wide = Piece("capsula", GlassRole.Floating, floating, 200f, 52f, null)
    val panel = Piece("pannello", GlassRole.Floating, floating, 320f, 240f, 28f)
    listOf(
      Journey("tasto → capsula", button, wide),
      Journey("tasto → pannello", button, panel),
      Journey("pannello → tasto", panel, button),
    )
  }

  /**
   * **Nota F1B.6** (`fluidphysics/FluidPhysicsEffects.kt`, `PhysicsLargeVeil.next` e `PhysicsLargeVeilMaxStepSeconds`):
   * sul tema scuro, fra un piccolo e un grande, il velo dei grandi parte al primo disegno del viaggio con un passo di un
   * fotogramma a 30 Hz, perché il disegno prima (la superficie ferma) è vecchio quanto la pausa, e la pausa conta
   * 1/30 s. A 120 Hz è il passo di quattro fotogrammi in uno: l'11% della strada fra i due veli, che sul tema scuro
   * distano 66 livelli nel colore (`DarkLighter` e `DarkLarge`), cioè ~3–4 livelli a schermo, nel fotogramma in cui la
   * sagoma è ancora quella ferma. Sul tema chiaro i due veli sono lo stesso e non succede.
   */
  // Chiusa dal giro di correzione di F1B.2: il tempo del velo riparte col viaggio, e il primo fotogramma è identico alla
  // superficie ferma anche sul tema scuro. La nota qui sopra resta come storia; l'elenco dei noti è vuoto.
  private val knownFirstFrame = emptyMap<String, String>()

  @Test
  fun theFirstAndTheLastFrameOfAJourneyAreTheSurfaceAtRest() = runTest {
    val density = 3f
    val stage = Rect(0f, 0f, 360f * density, 280f * density)
    val known = ArrayList<String>()
    for (journey in journeys) for (dark in listOf(false, true)) {
      val where = "${journey.name}, ${if (dark) "scuro" else "chiaro"}"
      val state = State("riposo", 1f, 1f) { n, _ -> n }
      val physics = FluidPhysicsState(journey.from.form(density)).apply {
        this.density = density
        maskInShader = true
        liquidAllowed = false
      }
      val surface = physicsSurface(physics, journey.to, dark, density, state, neutral, stage.width)

      // Ferma, prima del viaggio. Il nodo è il palco: la sagoma sta nel suo angolo.
      val rest = physicsInputs(surface, stage.width, stage.height, density)
      val restMaterial = material(rest, density, density)
      val restOutline = outline(rest.body, 1f)
      val restShadow = shadow(rest, density, 1f)

      // Una superficie ferma non si ridisegna: il primo disegno del viaggio arriva dopo una pausa vera.
      Thread.sleep(50)
      val clock = FrameClock()
      // Il transito si installa a progresso zero nello stesso snapshot: il disegno che segue è il primo del viaggio.
      val job = launch(clock) { physics.morphTo(journey.to.form(density)) }
      runCurrent()
      var time = 0L
      assertTrue("$where: il viaggio è partito", physics.journeyTargetNow() != null)
      assertEquals("$where: il primo fotogramma è a progresso zero", 0f, physics.progress, 0f)
      val first = physicsInputs(surface, stage.width, stage.height, density)
      val firstDiff = differences(restMaterial, material(first, density, density)) +
        differences(restOutline, outline(first.body, 1f)) +
        differences(restShadow, shadow(first, density, 1f), ShadowTolerancePx / density)
      val note = knownFirstFrame[where]
      if (note == null) {
        assertTrue("$where, primo fotogramma contro la superficie ferma:\n  " + firstDiff.joinToString("\n  "), firstDiff.isEmpty())
      } else if (firstDiff.isEmpty()) {
        fail("$where: la disparità nota «$note» non c'è più: togli la riga da knownFirstFrame")
      } else {
        known += "$where: $note (${firstDiff.size} numeri, per esempio ${firstDiff.first()})"
      }

      // Il resto del viaggio a 60 Hz, col tempo vero che passa (il velo dei grandi va col tempo del disegno).
      var frames = 0
      while (!job.isCompleted && frames < 240) {
        Thread.sleep(16)
        time += 16_666_667L
        clock.frames.send(time)
        runCurrent()
        physicsInputs(surface, stage.width, stage.height, density)
        frames++
      }
      assertTrue("$where: il viaggio finisce", job.isCompleted)

      // L'arrivo: la superficie fisica ferma sulla forma d'arrivo è la glassSurface di quella forma.
      val last = physicsInputs(surface, stage.width, stage.height, density)
      val target = journey.to
      val size = Size(target.widthDp * density, target.heightDp * density)
      val still = stillSurface(target, dark, density, state, neutral)
      val scale = still.effectiveCaptureScale(size)
      val g = still.drawInputs(size)
      assertSameNumbers("$where, arrivo: corpo e luce", material(g, density * scale, density), material(last, density, density))
      assertSameNumbers("$where, arrivo: sagoma", outline(g.body, scale), outline(last.body, 1f))
      assertSameNumbers(
        "$where, arrivo: ombra",
        shadow(shadowOf(still, size, density)),
        shadow(last, density, 1f),
        ShadowTolerancePx / density,
      )
    }
    if (known.isNotEmpty()) {
      known.forEach { println("NOTO F1B.6 $it") }
      assumeTrue(known.joinToString("; "), false)
    }
  }

  // ------------------------------------------------------------------------------------------------------------
  // B · i profili derivati seguono quello di serie
  // ------------------------------------------------------------------------------------------------------------

  private val base = GlassLiquidProfile.Shipped

  /** Le copie di [base] con un pezzo cambiato: ciò che un giorno potrebbe cambiare nel profilo di serie. */
  private val mutations: List<Pair<String, GlassLiquidProfile>> = listOf(
    "velo" to base.copy(
      lightVeil = base.lightVeil.copy(color = Color(0x70E8F0F8), adapt = base.lightVeil.adapt * 0.5f + 0.1f),
      darkVeil = base.darkVeil.copy(color = Color(0x70505868)),
      darkLargeVeil = base.darkLargeVeil.copy(color = Color(0x70303440)),
    ),
    "filo" to base.copy(
      lightEdge = base.lightEdge.copy(rimWidth = base.lightEdge.rimWidth + 0.5.dp, rim = Color(0x55102030)),
      darkEdge = base.darkEdge.copy(rimWidth = base.darkEdge.rimWidth + 0.5.dp, rim = Color(0x40FFFFFF)),
    ),
    "riflesso" to base.copy(
      lightEdge = base.lightEdge.copy(reflexStrength = base.lightEdge.reflexStrength * 1.5f, reflexDepth = base.lightEdge.reflexDepth + 2.dp),
      darkEdge = base.darkEdge.copy(reflexStrength = base.darkEdge.reflexStrength * 1.5f, reflexDepth = base.darkEdge.reflexDepth + 2.dp),
    ),
    "ombra" to base.copy(
      lightShadow = base.lightShadow.copy(
        contactAlpha = base.lightShadow.contactAlpha * 1.7f,
        ambientAlpha = base.lightShadow.ambientAlpha * 1.4f,
        ambientRadius = base.lightShadow.ambientRadius * 1.5f,
      ),
      darkShadow = base.darkShadow.copy(
        contactAlpha = base.darkShadow.contactAlpha * 1.7f,
        ambientAlpha = base.darkShadow.ambientAlpha * 1.4f,
        ambientRadius = base.darkShadow.ambientRadius * 1.5f,
      ),
    ),
    "smerigliatura" to base.copy(frost = base.frost.copy(blur = 8.dp, saturation = 1.8f)),
    "lente" to base.copy(lens = GlassLiquidLenses.Strong),
  )

  /** Ogni campo di una ricetta col suo nome. */
  private fun fields(r: GlassRecipe): Map<String, Any> = linkedMapOf(
    "saturation" to r.saturation, "blurDp" to r.blurDp, "mix" to r.mix, "paddingDp" to r.paddingDp,
    "captureScale" to r.captureScale, "thicknessDp" to r.thicknessDp, "refractionDp" to r.refractionDp, "dome" to r.dome,
    "dispersion" to r.dispersion, "magnify" to r.magnify, "materialize" to r.materialize, "veilColor" to r.veilColor,
    "veilAdapt" to r.veilAdapt, "tintColor" to r.tintColor, "tintMode" to r.tintMode, "tintFollow" to r.tintFollow,
    "bodyShade" to r.bodyShade, "bodyShadeDepthDp" to r.bodyShadeDepthDp, "bodyShadeLeanX" to r.bodyShadeLeanX,
    "bodyShadeLeanY" to r.bodyShadeLeanY, "dim" to r.dim, "grain" to r.grain, "aa" to r.aa, "rimWidthDp" to r.rimWidthDp,
    "rimDark" to r.rimDark, "rimFeather" to r.rimFeather, "rimLift" to r.rimLift, "reflexDirX" to r.reflexDirX,
    "reflexDirY" to r.reflexDirY, "reflexHalfWidth" to r.reflexHalfWidth, "reflexOppositeHalfWidth" to r.reflexOppositeHalfWidth,
    "reflexStrength" to r.reflexStrength, "reflexDepthDp" to r.reflexDepthDp, "reflexFloor" to r.reflexFloor,
    "reflexHalo" to r.reflexHalo, "lightColor" to r.lightColor, "glowPeak" to r.glowPeak, "glowFill" to r.glowFill,
    "shadowContactRadiusDp" to r.shadowContactRadiusDp, "shadowAmbientRadiusDp" to r.shadowAmbientRadiusDp,
    "shadowOffsetXDp" to r.shadowOffsetXDp, "shadowOffsetYDp" to r.shadowOffsetYDp,
    "shadowContactAlpha" to r.shadowContactAlpha, "shadowAmbientAlpha" to r.shadowAmbientAlpha, "shadowColor" to r.shadowColor,
  )

  private fun changed(a: GlassRecipe, b: GlassRecipe): Set<String> {
    val x = fields(a)
    val y = fields(b)
    return x.keys.filter { x[it] != y[it] }.toSet()
  }

  /**
   * Un derivato: la sua ricetta da un profilo di base, la ricetta di serie con cui si confronta (stesso ruolo, stessa
   * ottica, stessa taglia, stesso tema, stesso stato), e i campi che **dichiara suoi**, cioè che non seguono la base.
   */
  private class Derived(
    val name: String,
    val recipe: (GlassLiquidProfile, Boolean) -> GlassRecipe,
    val stock: (GlassLiquidProfile, Boolean) -> GlassRecipe,
    val own: Set<String>,
  )

  private val barHeightDp = 62f
  private val puckOptics = GlassDefaults.optics(GlassRole.Floating).sanitized()

  private val derived: List<Derived> = listOf(
    // La pillola a riposo: velo della barra più la selezione, metà filo sul chiaro, ombra dell'annidato. Tutto viene
    // dalla base; niente è suo, salvo i numeri della selezione (che modulano, non sostituiscono).
    Derived(
      name = "pillola a riposo",
      recipe = { p, dark -> glassRecipe(GlassRole.Floating, 120f, 52f, dark, optics = puckOptics, profile = glassPuckProfile(barHeightDp, p)) },
      stock = { p, dark -> glassRecipe(GlassRole.Floating, 120f, 52f, dark, optics = puckOptics, profile = p) },
      // Il velo con la selezione sopra non si ispessisce (`glassPuckSelectedVeil`): il colore segue, l'ispessimento no.
      own = setOf("veilAdapt"),
    ),
    // La riga nascosta: il materiale della barra.
    Derived(
      name = "riga nascosta della tab bar",
      recipe = { p, dark -> glassRecipe(GlassRole.Floating, 300f, 52f, dark, optics = puckOptics, profile = glassPuckRowProfile(barHeightDp, p)) },
      stock = { p, dark -> glassRecipe(GlassRole.Floating, 300f, 52f, dark, optics = puckOptics, profile = p) },
      own = emptySet(),
    ),
    // La pillola premuta sopra la riga: non sfoca e non satura di nuovo, e del velo posa la sola selezione.
    Derived(
      name = "pillola premuta",
      recipe = { p, dark -> glassRecipe(GlassRole.Floating, 120f, 52f, dark, optics = puckOptics, profile = glassPuckOverRowProfile(barHeightDp, p)) },
      stock = { p, dark -> glassRecipe(GlassRole.Floating, 120f, 52f, dark, optics = puckOptics, profile = p) },
      own = setOf("mix", "saturation", "veilColor", "veilAdapt"),
    ),
    // La goccia: limpida (tono al posto del velo, niente smerigliatura né saturazione), lente piatta, viaggio spento.
    Derived(
      name = "goccia",
      recipe = { p, dark -> glassDropRecipe(24f, 24f, dark, pressure = 1f, profile = glassDropProfile(p)) },
      stock = { p, dark -> glassRecipe(GlassDropRole, 24f, 24f, dark, optics = GlassDropOptics, opticalDepth = 1f, profile = p) },
      own = setOf("mix", "saturation", "veilColor", "veilAdapt", "dome"),
    ),
    // Il dipinto: la ricetta di un pannello modale, col profilo com'è.
    Derived(
      name = "vetro dipinto",
      recipe = { p, dark -> glassPaintedRecipe(360f, 300f, dark, largeVeil = 1f, profile = p) },
      stock = { p, dark -> glassRecipe(GlassRole.Modal, 360f, 300f, dark, optics = GlassDefaults.panelOptics(), profile = p, largeVeil = 1f) },
      own = emptySet(),
    ),
  )

  @Test
  fun theDerivedProfilesFollowTheStockOne() {
    for (d in derived) for ((what, mutated) in mutations) for (dark in listOf(false, true)) {
      val where = "${d.name}, $what, ${if (dark) "scuro" else "chiaro"}"
      val stockChange = changed(d.stock(base, dark), d.stock(mutated, dark))
      val derivedChange = changed(d.recipe(base, dark), d.recipe(mutated, dark))
      val mustFollow = stockChange - d.own
      val missing = mustFollow - derivedChange
      assertTrue("$where: la serie cambia $mustFollow, il derivato non segue $missing", missing.isEmpty())
      // Al contrario: il derivato non cambia ciò che la serie non cambia (salvo dove scrive un suo numero sopra quello
      // che cambia, come la selezione della pillola sul velo).
      val extra = derivedChange - stockChange
      assertTrue("$where: il derivato cambia anche $extra, che la serie non cambia", extra.isEmpty())
    }
    // Il corpo del pannello dipinto (il colore del foglio di piattaforma) segue il velo.
    for (dark in listOf(false, true)) {
      val page = if (dark) Color(0xFF0D0D0F) else Color(0xFFF2F2F7)
      val before = glassPaintedPanelBody(page, dark, base)
      val after = glassPaintedPanelBody(page, dark, mutations.first { it.first == "velo" }.second)
      assertTrue("il corpo del foglio dipinto segue il velo (${if (dark) "scuro" else "chiaro"})", before != after)
    }
  }

  /**
   * I numeri **propri** dei tre profili derivati: tutto ciò che è scritto come numero nel loro sorgente, oltre a zero e
   * uno. È un elenco chiuso: un numero nuovo va qui con la ragione (o, se è del materiale, nel profilo di serie), uno
   * che sparisce si toglie. Le chiavi sono il letterale come sta nel sorgente.
   */
  private val OwnNumbers: Map<String, Map<String, String>> = mapOf(
    "GlassPuckProfile.kt" to mapOf(
      "0.09f" to "selezione sul chiaro: quanto la pillola è più scura della barra (GlassPuckSelections.Ios27)",
      "0.24f" to "selezione sullo scuro (GlassPuckSelections.Ios27)",
      "0.5f" to "il filo della pillola sul chiaro, frazione di quello di serie (GlassPuckSelections.Ios27.lightRim)",
    ),
    "GlassDropProfile.kt" to mapOf(
      "0.05f" to "il tono della goccia al posto del velo (GlassDropTone)",
      "1.5f" to "lo spostamento della lente in multipli del moto pieno (GlassDropBend)",
    ),
    "GlassPaintedProfile.kt" to mapOf(
      "0.12f" to "scrim sul chiaro (GlassPaintedScrim.LightAlpha), lo stesso dei modali di vetro",
      "0.20f" to "scrim sullo scuro (GlassPaintedScrim.DarkAlpha)",
      "0xFFF2F2F7" to "la pagina di riferimento chiara (GlassPaintedPage)",
      "0xFF0D0D0F" to "la pagina di riferimento scura (GlassPaintedPage)",
      "0.36f" to "la pellicola d'accento della selezione (GlassPaintedSelectionAlpha), quella di glassControlSurface",
      "320f" to "la taglia con cui si chiede la ricetta di un pannello prima della misura (PanelReferenceDp)",
      "0.5f" to "il passo dell'alone in dp (GlassPaintedHaloStepDp); e i mezzi di un tratto centrato",
      "24" to "il tetto dei tratti dell'alone (GlassPaintedHaloMaxSteps)",
      "0.2126f" to "luma Rec. 709, come nello shader del corpo",
      "0.7152f" to "luma Rec. 709",
      "0.0722f" to "luma Rec. 709",
      "255f" to "un livello su 255: sotto, un tratto dell'alone non si disegna",
      "0.25f" to "un quarto di livello: la soglia sotto cui un tratto dell'alone non si disegna",
      "0.0001f" to "un epsilon perché i limiti di un lobo restino in ordine",
      "0.001f" to "un epsilon perché le posizioni di un gradiente crescano",
      "16" to "i campioni di un gradiente",
      "2" to "aritmetica (metà, doppio)",
      "2f" to "aritmetica (metà, doppio)",
      "2.0" to "PI / 2",
      "3" to "aritmetica (smoothstep) e indici",
      "3f" to "aritmetica (smoothstep)",
      "4" to "i quattro angoli",
      "6" to "i passi del quarto di giro",
      "6f" to "i passi del quarto di giro",
      "8f" to "i passi di un lobo",
    ),
  )

  /** Chi deriva non sceglie una variante fissa del profilo: le prende da quello che riceve. */
  private val forbiddenInDerived = listOf(
    "GlassLiquidProfile.Shipped", "GlassLiquidEdges.", "GlassLiquidVeils.", "GlassLiquidShadows.",
    "GlassLiquidFrosts.", "GlassLiquidLenses.", "GlassLiquidGrowths.",
  )

  private fun sourceDir(): File = listOf(File("src/main/kotlin"), File("engine-ui/src/main/kotlin")).first { it.isDirectory }

  /** Il codice senza commenti e senza stringhe: solo ciò che si esegue. */
  private fun code(file: File): String = file.readText()
    .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
    .replace(Regex("//[^\\n]*"), "")
    // Forma «srotolata»: quella con l'alternativa dentro la stella ricorre una volta per carattere, e su una stringa
    // lunga migliaia di caratteri (ce n'è una in `FluidMarkdownParser.kt`) finisce in StackOverflowError.
    .replace(Regex("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\""), "\"\"")

  @Test
  fun whatEachDerivedProfileCallsItsOwnIsAClosedList() {
    val dir = File(sourceDir(), "dev/antigravity/fluidengine/ui/glass/field")
    val literal = Regex("(?<![\\w.])(0x[0-9A-Fa-f]+L?|\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?[fFL]?)(?![\\w.])")
    val structural = setOf("0", "1", "0f", "1f")
    for ((name, declared) in OwnNumbers) {
      val text = code(File(dir, name))
      val found = literal.findAll(text).map { it.value }.toSet() - structural
      val undeclared = found - declared.keys
      val gone = declared.keys - found
      assertTrue(
        "$name: numeri propri non dichiarati $undeclared. Se è un numero del materiale va nel profilo di serie " +
          "(GlassLiquidProfile), altrimenti dichiaralo in OwnNumbers con la ragione.",
        undeclared.isEmpty(),
      )
      assertTrue("$name: dichiarati ma spariti $gone: togli la riga da OwnNumbers", gone.isEmpty())
      for (token in forbiddenInDerived) {
        assertTrue("$name sceglie una variante fissa ($token) invece di derivare da quella che riceve", token !in text)
      }
    }
  }

  // ------------------------------------------------------------------------------------------------------------
  // C · la tabella di verità
  // ------------------------------------------------------------------------------------------------------------

  private val apiLevels = listOf(26, 30, 31, 32, 33, 34, 35)
  private val tiers: List<FluidPhysicsTier?> = listOf(null, FluidPhysicsTier.Full, FluidPhysicsTier.Balanced, FluidPhysicsTier.Lite)

  /** Le funzioni di scelta di ogni strada, con gli ingressi che la strada passa davvero (sorgenti sane). */
  private fun pathTakesLiquid(path: String, model: FluidGlassModel, sdk: Int, tier: FluidPhysicsTier?): Boolean = when (path) {
    "glassSurface" -> resolveFluidGlassModel(
      sdk, { true }, model, GlassFalloff.Uniform, genericShape = !glassFieldRenders(FluidCapsuleShape),
      flat = GlassDefaults.buttonOptics().sanitized().isFlatVeil(),
    ) == FluidGlassModel.Liquid
    "fluid-physics" -> resolvePhysicsGlass(
      sdk = sdk,
      renderEffectSupported = sdk >= 31,
      runtimeShaderSupported = sdk >= FluidGlassLiquidMinSdk,
      classicHealthy = { true },
      liquidHealthy = { true },
      requestedTier = tier,
      requestedModel = model,
    ).liquid
    // `FluidFloatingTabBar`: la scelta della lastra sulla forma della scheda, e il campo deve rendere la barra.
    "pillola" -> resolveFluidGlassModel(
      sdk, { true }, model, GlassFalloff.Uniform, genericShape = !glassFieldRenders(FluidCapsuleShape),
      flat = puckOptics.isFlatVeil(),
    ) == FluidGlassModel.Liquid && glassFieldRenders(FluidCapsuleShape)
    // `glassDrop`: la scelta della lastra con l'ottica della goccia.
    "goccia" -> resolveFluidGlassModel(
      sdk, { true }, model, GlassFalloff.Uniform, genericShape = !glassFieldRenders(FluidCapsuleShape),
      flat = GlassDropOptics.isFlatVeil(),
    ) == FluidGlassModel.Liquid
    // Il dipinto: se chi lo chiama passa da una scelta del modello, quella; altrimenti la ricetta sempre.
    "dipinto" -> if (paintedIsGated()) {
      resolveFluidGlassModel(sdk, { true }, model, GlassFalloff.Uniform, genericShape = false) == FluidGlassModel.Liquid
    } else {
      true
    }
    else -> error(path)
  }

  /**
   * Vero se ogni funzione che chiama `glassPainted` (fuori dal suo file) passa da una scelta del modello. Il dipinto non
   * ha una funzione di scelta da chiamare: si guarda il codice dei chiamanti.
   */
  private fun paintedIsGated(): Boolean {
    val dir = File(sourceDir(), "dev/antigravity/fluidengine/ui/fluid")
    val gates = listOf("rememberFluidGlassModel", "LocalFluidGlassModel", "resolveFluidGlassModel", "glassApiLevel")
    var callers = 0
    for (file in dir.listFiles().orEmpty().filter { it.name.endsWith(".kt") }) {
      val text = code(file)
      if (!text.contains(".glassPainted(")) continue
      // Le funzioni di primo livello: dal loro `fun` alla graffa chiusa a colonna zero.
      val functions = Regex("(?m)^(?:@\\w+(?:\\([^)]*\\))?\\s*\\n)*(?:internal |private |public )?fun [\\s\\S]*?\\n}\\n").findAll(text)
      for (f in functions) {
        if (!f.value.contains(".glassPainted(")) continue
        callers++
        if (gates.none { it in f.value }) return false
      }
    }
    assertTrue("nessun chiamante di glassPainted trovato: la lettura del sorgente è da rifare", callers > 0)
    return true
  }

  private val paths = listOf("glassSurface", "fluid-physics", "pillola", "goccia", "dipinto")

  /**
   * **Nota F1B.6**: il vetro dipinto (`fluidStaticGlassSurface` in `fluid/FluidGlassControls.kt`, `FluidSheet` e
   * l'avviso nella `Dialog` in `fluid/FluidSheet.kt`) non passa da nessuna scelta del modello: corpo, filo e riflesso
   * vengono dalla ricetta nuova anche con `FluidGlassModel.Classic` e sotto Android 13, mentre la sua ombra
   * (`glassShadow`) la scelta la fa. Con `Classic` il foglio di piattaforma è un ibrido: ombra di prima, vetro dipinto
   * nuovo, scrim nuovo.
   */
  // Chiusa dal giro di correzione di F1B.5: il dipinto sceglie il modello con `paintedGlassTakesLiquid`, e con `Classic`
  // o sotto Android 13 torna intero quello della 3.x. La nota qui sopra resta come storia; l'elenco dei noti è vuoto.
  private val knownPaths = emptyMap<String, String>()

  @Test
  fun belowAndroid13AndWithClassicNoPathTakesTheNewMaterial() {
    val known = ArrayList<String>()
    var rows = 0
    for (path in paths) {
      val wrong = ArrayList<String>()
      for (model in FluidGlassModel.entries) for (sdk in apiLevels) for (tier in tiers) {
        // Ciò che si vuole: il materiale nuovo solo se chiesto e da Android 13; per Fluid-physics anche al livello
        // `Full` (o senza richiesta). Le altre strade non hanno livelli: il livello chiesto non le tocca.
        val tierAllows = path != "fluid-physics" || tier == null || tier == FluidPhysicsTier.Full
        val expected = model == FluidGlassModel.Liquid && sdk >= FluidGlassLiquidMinSdk && tierAllows
        val actual = pathTakesLiquid(path, model, sdk, tier)
        if (actual != expected) wrong += "$model, API $sdk, livello ${tier ?: "del dispositivo"}: atteso $expected, è $actual"
        rows++
      }
      val note = knownPaths[path]
      when {
        note == null -> assertTrue("$path:\n  " + wrong.joinToString("\n  "), wrong.isEmpty())
        wrong.isEmpty() -> fail("$path: la disparità nota «$note» non c'è più: togli la riga da knownPaths")
        else -> known += "$path: $note (${wrong.size} righe su ${FluidGlassModel.entries.size * apiLevels.size * tiers.size}, per esempio ${wrong.first()})"
      }
    }
    assertEquals(paths.size * FluidGlassModel.entries.size * apiLevels.size * tiers.size, rows)
    if (known.isNotEmpty()) {
      known.forEach { println("NOTO F1B.6 $it") }
      assumeTrue(known.joinToString("; "), false)
    }
  }
}
