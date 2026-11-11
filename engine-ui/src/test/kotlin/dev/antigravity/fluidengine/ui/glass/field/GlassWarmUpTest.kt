package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassFalloff
import dev.antigravity.fluidengine.ui.fluid.resolveFluidGlassModel
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassLayerKind
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassModelReason
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassNodeKind
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassWarmUpState
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassNodeCensus
import dev.antigravity.fluidengine.ui.glass.backdrop.glassApiLevel
import dev.antigravity.fluidengine.ui.glass.backdrop.glassApiSimulationAllowed
import dev.antigravity.fluidengine.ui.glass.backdrop.installSimulatedGlassApiLevel
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.simulatedGlassApiLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il riscaldamento dei programmi del materiale `Liquid` (F1A.7) e ciò che la diagnostica conta, nelle parti pure.
 *
 * Il **quando** (dopo il primo fotogramma, a finestra ferma) e il **come** (un `HardwareRenderer` fuori schermo)
 * chiedono la piattaforma: li prova il gemello strumentato (`GlassShaderCompileInstrumentedTest`). Qui le regole:
 * che cosa si scalda e in che ordine, che non riparte, che una sorgente rifiutata o un disegno fallito fermano solo
 * la loro famiglia, che una finestra che se ne va non butta ciò che è già caldo; poi il motivo di ogni modello, i
 * contatori che salgono e scendono a coppie, e il livello di API simulato che può solo scendere.
 */
class GlassWarmUpTest {

  @After
  fun forget() {
    GlassFieldHealth.resetForTesting()
    installSimulatedGlassApiLevel(null)
  }

  /** Un riscaldamento senza piattaforma: AGSL e verdetti si decidono qui, i disegni si annotano. */
  private class Bench {
    var agsl = true
    val refused = mutableSetOf<GlassShaderSource>()
    val asked = mutableListOf<String>()
    val drawn = mutableListOf<GlassWarmStep>()
    var failOn: GlassWarmStep? = null
    var clock = 0L
    val core = GlassWarmUpCore(
      agsl = { agsl },
      usable = { source ->
        asked += source.name
        source !in refused
      },
      // Ogni lettura dell'orologio avanza di un millisecondo: un passo ne legge due, quindi costa 1 ms.
      nanoTime = { clock.also { clock += 1_000_000L } },
    )

    fun draw(step: GlassWarmStep) {
      if (step == failOn) throw IllegalStateException("rifiutato apposta dal test")
      drawn += step
    }

    fun runAll(): List<GlassWarmStep> {
      while (true) {
        val next = core.next() ?: break
        core.run(next, ::draw)
      }
      return drawn
    }

    fun status(family: GlassWarmFamily) = core.status().first { it.family == family.name }
  }

  // ---- Che cosa si scalda, e quando smette ---------------------------------------------------------------------

  @Test
  fun aRequestWaitsThenWarmsTheBodyBeforeTheLightOneProgramPerStep() {
    val bench = Bench()
    assertTrue(bench.core.request(GlassWarmFamily.Slab, gpuWindow = true))
    // Chiedere non disegna: si aspetta il primo fotogramma e la finestra ferma.
    assertEquals(FluidGlassWarmUpState.Waiting, bench.status(GlassWarmFamily.Slab).state)
    assertTrue(bench.drawn.isEmpty())
    assertTrue(bench.core.pending)

    val first = bench.core.next()
    assertEquals(GlassWarmStep.SlabBody, first)
    bench.core.run(first!!, bench::draw)
    assertEquals(FluidGlassWarmUpState.Running, bench.status(GlassWarmFamily.Slab).state)
    assertEquals(listOf(GlassWarmStep.SlabBody), bench.drawn)

    val second = bench.core.next()
    assertEquals(GlassWarmStep.SlabLight, second)
    bench.core.run(second!!, bench::draw)
    val done = bench.status(GlassWarmFamily.Slab)
    assertEquals(FluidGlassWarmUpState.Done, done.state)
    assertEquals(listOf("GlassBody1ShaderString", "GlassLight1ShaderString"), done.warmed)
    assertEquals(done.programs, done.warmed)
    assertEquals(2f, done.mainThreadMillis, 0f)
    assertNull(bench.core.next())
    assertFalse(bench.core.pending)
    // La famiglia dei gruppi non l'ha chiesta nessuno: resta ferma.
    assertEquals(FluidGlassWarmUpState.Idle, bench.status(GlassWarmFamily.Group).state)
  }

  @Test
  fun itIsIdempotentAndNeverStartsAgain() {
    val bench = Bench()
    assertTrue(bench.core.request(GlassWarmFamily.Slab, gpuWindow = true))
    // Cento superfici che entrano in composizione sono una richiesta sola.
    repeat(100) { assertFalse(bench.core.request(GlassWarmFamily.Slab, gpuWindow = true)) }
    bench.runAll()
    assertEquals(2, bench.drawn.size)
    // Fatta, non riparte: né a una ricomposizione, né a una finestra nuova.
    assertFalse(bench.core.request(GlassWarmFamily.Slab, gpuWindow = true))
    bench.runAll()
    assertEquals(2, bench.drawn.size)
    // I verdetti si chiedono una volta, prima del primo passo.
    assertEquals(listOf("GlassBody1ShaderString", "GlassLight1ShaderString"), bench.asked)
  }

  @Test
  fun belowAndroid13NothingIsAskedNorDrawn() {
    val bench = Bench().apply { agsl = false }
    assertFalse(bench.core.request(GlassWarmFamily.Slab, gpuWindow = true))
    val status = bench.status(GlassWarmFamily.Slab)
    assertEquals(FluidGlassWarmUpState.Skipped, status.state)
    assertEquals(GlassWarmUpCore.NoAgsl, status.detail)
    assertNull(bench.core.next())
    assertTrue(bench.asked.isEmpty())
    assertTrue(bench.drawn.isEmpty())
  }

  @Test
  fun aWindowWithoutAGpuIsSkippedAndCanAskAgain() {
    val bench = Bench()
    assertFalse(bench.core.request(GlassWarmFamily.Slab, gpuWindow = false))
    assertEquals(FluidGlassWarmUpState.Skipped, bench.status(GlassWarmFamily.Slab).state)
    assertEquals(GlassWarmUpCore.NoGpuWindow, bench.status(GlassWarmFamily.Slab).detail)
    // Un salto non è definitivo: la prossima finestra disegnata dalla GPU la fa partire.
    assertTrue(bench.core.request(GlassWarmFamily.Slab, gpuWindow = true))
    assertEquals(listOf(GlassWarmStep.SlabBody, GlassWarmStep.SlabLight), bench.runAll())
  }

  @Test
  fun aRefusedProgramSkipsItsWholeFamilyAndOnlyIt() {
    val bench = Bench()
    bench.refused += GlassShaderSources.Light1
    bench.core.request(GlassWarmFamily.Slab, gpuWindow = true)
    bench.core.request(GlassWarmFamily.Group, gpuWindow = true)
    // La lastra resta `Classic` (la sua luce è rifiutata): scaldarne il corpo non serve a nessuno.
    assertEquals(listOf(GlassWarmStep.GroupBody, GlassWarmStep.GroupLight), bench.runAll())
    val slab = bench.status(GlassWarmFamily.Slab)
    assertEquals(FluidGlassWarmUpState.Skipped, slab.state)
    assertTrue(slab.detail!!, "GlassLight1ShaderString" in slab.detail!!)
    assertTrue(slab.warmed.isEmpty())
    assertEquals(FluidGlassWarmUpState.Done, bench.status(GlassWarmFamily.Group).state)
  }

  @Test
  fun aFailedDrawStopsItsFamilyForGoodAndSaysWhy() {
    val bench = Bench()
    bench.failOn = GlassWarmStep.SlabBody
    bench.core.request(GlassWarmFamily.Slab, gpuWindow = true)
    bench.core.request(GlassWarmFamily.Group, gpuWindow = true)
    assertEquals(listOf(GlassWarmStep.GroupBody, GlassWarmStep.GroupLight), bench.runAll())
    val slab = bench.status(GlassWarmFamily.Slab)
    assertEquals(FluidGlassWarmUpState.Failed, slab.state)
    assertTrue(slab.detail!!, "GlassBody1ShaderString" in slab.detail!! && "rifiutato apposta" in slab.detail!!)
    // Il primo vetro della famiglia pagherà la compilazione: riprovare a ogni superficie no.
    assertFalse(bench.core.request(GlassWarmFamily.Slab, gpuWindow = true))
    assertNull(bench.core.next())
  }

  @Test
  fun aWindowThatLeavesKeepsWhatIsAlreadyWarm() {
    val bench = Bench()
    bench.core.request(GlassWarmFamily.Slab, gpuWindow = true)
    bench.core.run(bench.core.next()!!, bench::draw)
    bench.core.cancel()
    assertEquals(FluidGlassWarmUpState.Idle, bench.status(GlassWarmFamily.Slab).state)
    assertEquals(listOf("GlassBody1ShaderString"), bench.status(GlassWarmFamily.Slab).warmed)
    assertFalse(bench.core.pending)
    // La finestra dopo riparte dal passo che mancava, non dal primo.
    assertTrue(bench.core.request(GlassWarmFamily.Slab, gpuWindow = true))
    assertEquals(listOf(GlassWarmStep.SlabBody, GlassWarmStep.SlabLight), bench.runAll())
    assertEquals(FluidGlassWarmUpState.Done, bench.status(GlassWarmFamily.Slab).state)
  }

  @Test
  fun aSimulationArrivingBetweenTwoStepsStopsTheRest() {
    val bench = Bench()
    bench.core.request(GlassWarmFamily.Slab, gpuWindow = true)
    bench.core.run(bench.core.next()!!, bench::draw)
    bench.agsl = false
    assertNull(bench.core.next())
    assertEquals(FluidGlassWarmUpState.Skipped, bench.status(GlassWarmFamily.Slab).state)
    assertEquals(listOf(GlassWarmStep.SlabBody), bench.drawn)
  }

  @Test
  fun everyFamilyStepCompilesADifferentProgramOfItsOwnFamily() {
    for (family in GlassWarmFamily.entries) {
      assertTrue(family.name, family.steps.isNotEmpty())
      for (step in family.steps) assertEquals(step.name, family, step.family)
      assertEquals(family.name, family.sources.size, family.sources.toSet().size)
    }
    assertEquals(GlassWarmStep.entries.size, GlassWarmFamily.entries.sumOf { it.steps.size })
  }

  /**
   * La lastra che si scalda passa per i due disegni della luce (`Plus` e `SrcOver`) e per una catena a monte vera:
   * se uno dei due mancasse, quel programma resterebbe freddo e lo pagherebbe il primo tasto.
   */
  @Test
  fun theWarmSlabDrawsBothLightPartsOverARealChain() {
    val density = 3f
    val side = GlassWarmSlabSideDp * density
    val surface = glassWarmSlabSurface(density)
    val inputs = surface.drawInputs(Size(side, side))
    assertTrue("la parte che somma", inputs.drawsGlowing)
    assertTrue("il filo", inputs.drawsRim)
    assertTrue("la sfocatura della catena", inputs.blurPx > 0f)
    // La catena di un tasto vero: rimpicciolita come quella del tasto (la smerigliatura di serie, 4,5 dp: il 73%).
    assertEquals(surface.captureScale, surface.effectiveCaptureScale(Size(side, side)), 0f)
    assertTrue(surface.captureScale < 1f)
    assertTrue(surface.castsShadow)
  }

  // ---- Il motivo di ogni modello --------------------------------------------------------------------------------

  @Test
  fun theReasonIsTheFirstRuleThatHoldsAndAgreesWithTheResolution() {
    val flags = listOf(false, true)
    var rows = 0
    for (sdk in listOf(26, 30, 31, 32, 33, 35, 36)) {
      for (requested in FluidGlassModel.entries) for (falloff in GlassFalloff.entries) {
        for (generic in flags) for (flat in flags) for (healthy in flags) {
          val model = resolveFluidGlassModel(sdk, { healthy }, requested, falloff, generic, flat)
          val reason = glassModelReason(model, sdk, requested, falloff, generic, flat)
          val expected = when {
            requested == FluidGlassModel.Classic -> FluidGlassModelReason.ClassicRequested
            sdk < 33 -> FluidGlassModelReason.ApiLevel
            falloff == GlassFalloff.FadeDown -> FluidGlassModelReason.FadeDown
            generic -> FluidGlassModelReason.GenericShape
            flat -> FluidGlassModelReason.FlatVeil
            !healthy -> FluidGlassModelReason.ShadersRefused
            else -> FluidGlassModelReason.Liquid
          }
          val row = "sdk=$sdk requested=$requested falloff=$falloff generic=$generic flat=$flat healthy=$healthy"
          assertEquals(row, expected, reason)
          assertEquals(row, model, reason.model)
          rows++
        }
      }
    }
    assertEquals(7 * 2 * 2 * 2 * 2 * 2, rows)
  }

  @Test
  fun onlyATreeThatAsksForLiquidWhereItCanExistStartsTheWarmUp() {
    fun warms(
      model: FluidGlassModel = FluidGlassModel.Liquid,
      sdk: Int = 35,
      requested: FluidGlassModel = FluidGlassModel.Liquid,
      falloff: GlassFalloff = GlassFalloff.Uniform,
      generic: Boolean = false,
      flat: Boolean = false,
    ) = glassModelChoice(model, sdk, requested, falloff, generic, flat, view = null).warms

    assertTrue("una lastra Liquid", warms())
    // La fascia in alto resta `Classic`, ma sta in un albero che chiede `Liquid`: è il vetro della prima schermata.
    assertTrue("la fascia", warms(model = FluidGlassModel.Classic, falloff = GlassFalloff.FadeDown))
    assertTrue("una forma generica", warms(model = FluidGlassModel.Classic, generic = true))
    assertTrue("lo scrim", warms(model = FluidGlassModel.Classic, flat = true))
    assertFalse("sotto Android 13", warms(model = FluidGlassModel.Classic, sdk = 32))
    assertFalse("l'orologio, o chi chiede Classic", warms(model = FluidGlassModel.Classic, requested = FluidGlassModel.Classic))
    assertFalse("un compilatore che rifiuta", warms(model = FluidGlassModel.Classic))
  }

  // ---- I contatori -----------------------------------------------------------------------------------------------

  private class Counts(
    val surfaces: Map<FluidGlassModel, Int> = FluidGlassDiagnostics.liveSurfaces,
    val choices: Map<FluidGlassModelReason, Int> = FluidGlassDiagnostics.liveModelChoices,
    val nodes: Map<FluidGlassNodeKind, Int> = FluidGlassDiagnostics.liveNodes,
    val layers: Map<FluidGlassLayerKind, Int> = FluidGlassDiagnostics.liveLayers,
  ) {
    override fun equals(other: Any?) = other is Counts && surfaces == other.surfaces && choices == other.choices &&
      nodes == other.nodes && layers == other.layers

    override fun hashCode() = surfaces.hashCode()

    override fun toString() = "superfici $surfaces, scelte $choices, nodi $nodes, layer $layers"

    fun minus(before: Counts): Map<String, Int> = buildMap {
      for ((k, v) in surfaces) if (v != before.surfaces[k]) put("superfici.$k", v - before.surfaces.getValue(k))
      for ((k, v) in choices) if (v != before.choices[k]) put("scelte.$k", v - before.choices.getValue(k))
      for ((k, v) in nodes) if (v != before.nodes[k]) put("nodi.$k", v - before.nodes.getValue(k))
      for ((k, v) in layers) if (v != before.layers[k]) put("layer.$k", v - before.layers.getValue(k))
    }
  }

  private inline fun withDiagnostics(on: Boolean, block: () -> Unit) {
    val was = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = on
    try {
      block()
    } finally {
      FluidGlassDiagnostics.enabled = was
    }
  }

  @Test
  fun aNodeCountsOnlyWhatWasBornWithDiagnosticsOnAndGivesAllOfItBack() {
    val before = Counts()
    withDiagnostics(on = false) {
      val quiet = GlassNodeCensus(FluidGlassNodeKind.Backdrop)
      quiet.attached()
      quiet.layer(FluidGlassLayerKind.Capture)
      quiet.surface(FluidGlassModel.Liquid)
      assertEquals(before, Counts())
      // Nato a diagnostica spenta: andandosene non porta niente sotto zero.
      quiet.detached()
      assertEquals(before, Counts())
    }
    withDiagnostics(on = true) {
      val node = GlassNodeCensus(FluidGlassNodeKind.Backdrop)
      node.attached()
      node.attached()
      node.layer(FluidGlassLayerKind.Capture)
      node.layer(FluidGlassLayerKind.Capture)
      node.layer(FluidGlassLayerKind.Placement)
      node.surface(FluidGlassModel.Classic)
      assertEquals(
        mapOf("superfici.Classic" to 1, "nodi.Backdrop" to 1, "layer.Capture" to 1, "layer.Placement" to 1),
        Counts().minus(before),
      )
      // Una superficie che cambia materiale passa da una colonna all'altra.
      node.surface(FluidGlassModel.Liquid)
      assertEquals(1, Counts().minus(before)["superfici.Liquid"])
      assertNull(Counts().minus(before)["superfici.Classic"])
      val shadow = GlassNodeCensus(FluidGlassNodeKind.Shadow)
      shadow.attached()
      shadow.layer(FluidGlassLayerKind.Shadow)
      // Spenta a metà: ciò che nasce adesso non si conta, ciò che era contato scende lo stesso.
      FluidGlassDiagnostics.enabled = false
      node.layer(FluidGlassLayerKind.Material)
      node.detached()
      shadow.detached()
      assertEquals(before, Counts())
      // Riattaccato (una lista che ricicla il nodo) si conta di nuovo, e di nuovo scende.
      FluidGlassDiagnostics.enabled = true
      node.attached()
      node.layer(FluidGlassLayerKind.Capture)
      node.surface(FluidGlassModel.Liquid)
      assertEquals(3, Counts().minus(before).values.sum())
      node.detached()
      assertEquals(before, Counts())
    }
  }

  @Test
  fun aChoiceIsCountedWhileItIsRememberedAndNeverWhenAbandoned() {
    val before = Counts()
    withDiagnostics(on = true) {
      val liquid = GlassModelChoice(FluidGlassModel.Liquid, FluidGlassModelReason.Liquid, warms = false, view = null)
      val scrim = GlassModelChoice(FluidGlassModel.Classic, FluidGlassModelReason.FlatVeil, warms = false, view = null)
      liquid.onRemembered()
      scrim.onRemembered()
      assertEquals(mapOf("scelte.Liquid" to 1, "scelte.FlatVeil" to 1), Counts().minus(before))
      liquid.onForgotten()
      scrim.onForgotten()
      assertEquals(before, Counts())
      val abandoned = GlassModelChoice(FluidGlassModel.Liquid, FluidGlassModelReason.Liquid, warms = false, view = null)
      abandoned.onAbandoned()
      assertEquals(before, Counts())
    }
    // Ricordata a diagnostica spenta e dimenticata a diagnostica accesa: niente sotto zero.
    val quiet = GlassModelChoice(FluidGlassModel.Liquid, FluidGlassModelReason.Liquid, warms = false, view = null)
    withDiagnostics(on = false) { quiet.onRemembered() }
    withDiagnostics(on = true) { quiet.onForgotten() }
    assertEquals(before, Counts())
  }

  // ---- La sonda e il livello simulato ---------------------------------------------------------------------------

  /**
   * Il «no» dell'API non si ricorda: una risposta data sotto un livello simulato non deve restare quando la
   * simulazione si toglie. Si ricorda solo il verdetto del compilatore (F1A.7; prima, `usable` teneva il «no»).
   */
  @Test
  fun theProbeRemembersTheCompilerNotTheApiLevel() {
    var agsl = false
    var compiled = 0
    GlassFieldHealth.installForTesting(agsl = { agsl }, compile = { compiled++ }) { _, _ -> }
    assertFalse(GlassFieldHealth.usable(GlassShaderSources.Body1))
    assertEquals(0, compiled)
    assertEquals(0, GlassFieldHealth.consultationsForTesting)
    agsl = true
    assertTrue(GlassFieldHealth.usable(GlassShaderSources.Body1))
    assertEquals(1, compiled)
    agsl = false
    assertFalse(GlassFieldHealth.usable(GlassShaderSources.Body1))
    agsl = true
    assertTrue(GlassFieldHealth.usable(GlassShaderSources.Body1))
    // Una compilazione e una consultazione sola: il verdetto è rimasto.
    assertEquals(1, compiled)
    assertEquals(1, GlassFieldHealth.consultationsForTesting)
  }

  /** Il gancio dei test della galleria non accende niente sotto Android 13 (qui il JVM, livello zero). */
  @Test
  fun theGalleryHookLeavesTheApiLevelAlone() {
    GlassFieldHealth.installVerdictsForTesting(accept = true)
    assertFalse(GlassFieldHealth.usable(GlassShaderSources.Body1))
    assertTrue(GlassFieldHealth.verdicts().isEmpty())
  }

  @Test
  fun onlyADebuggableAppMaySimulateAndOnlyDownwards() {
    for (level in listOf(26, 30, 32, null)) {
      assertFalse("un'app di rilascio non simula $level", glassApiSimulationAllowed(level, realSdk = 35, debuggable = false))
    }
    assertTrue(glassApiSimulationAllowed(32, realSdk = 35, debuggable = true))
    assertTrue(glassApiSimulationAllowed(30, realSdk = 35, debuggable = true))
    assertTrue(glassApiSimulationAllowed(26, realSdk = 35, debuggable = true))
    assertTrue("togliere la simulazione", glassApiSimulationAllowed(null, realSdk = 35, debuggable = true))
    assertFalse("non sotto il minimo dell'engine", glassApiSimulationAllowed(25, realSdk = 35, debuggable = true))
    assertFalse("non il livello vero", glassApiSimulationAllowed(35, realSdk = 35, debuggable = true))
    assertFalse("mai sopra il vero", glassApiSimulationAllowed(36, realSdk = 35, debuggable = true))
    assertFalse("non sopra un Android 12 vero", glassApiSimulationAllowed(33, realSdk = 32, debuggable = true))
  }

  /** Sul JVM il livello vero è zero: un livello simulato più alto non lo alza, ed è la stessa regola del tablet. */
  @Test
  fun aSimulatedLevelNeverRaisesTheRealOne() {
    installSimulatedGlassApiLevel(32)
    assertEquals(32, simulatedGlassApiLevel())
    assertEquals(0, glassApiLevel())
    assertFalse(isRuntimeShaderSupported())
    assertFalse(isRenderEffectSupported())
    installSimulatedGlassApiLevel(null)
    assertNull(simulatedGlassApiLevel())
  }
}
