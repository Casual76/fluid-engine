package dev.antigravity.fluidengine.ui.fluidphysics

import dev.antigravity.fluidengine.ui.glass.field.GlassShaderProbe
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSource
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidPhysicsTierTest {

  @Test
  fun theMenuPanelPrefersAboveAndStaysInsideTheHost() {
    val host = androidx.compose.ui.geometry.Rect(0f, 0f, 1080f, 2200f)
    // Un tasto in basso: il menù cresce verso l'alto, dentro i margini.
    val anchorLow = androidx.compose.ui.geometry.Rect(700f, 2000f, 940f, 2110f)
    val above = solveMorphMenuPanel(anchorLow, host, 3, 130f, 700f, 22f, 32f, 44f)
    assertEquals(2000f - 32f - (3 * 130f + 44f), above.top, 0.001f)
    org.junit.Assert.assertTrue(above.left >= 44f && above.right <= 1080f - 44f)
    // Il centro del pannello insegue quello del tasto finché i margini lo lasciano fare.
    assertEquals((1080f - 44f - 700f), above.left, 0.001f)

    // Un tasto in alto senza spazio sopra: il menù scende sotto.
    val anchorHigh = androidx.compose.ui.geometry.Rect(100f, 60f, 340f, 170f)
    val below = solveMorphMenuPanel(anchorHigh, host, 3, 130f, 700f, 22f, 32f, 44f)
    assertEquals(170f + 32f, below.top, 0.001f)
  }

  @Test
  fun theDeviceCeilingIsATruthTable() {
    assertEquals(
      FluidPhysicsTier.Full,
      resolvePhysicsTier(renderEffectSupported = true, runtimeShaderSupported = true, shadersHealthy = true, requested = null),
    )
    assertEquals(
      FluidPhysicsTier.Balanced,
      resolvePhysicsTier(renderEffectSupported = true, runtimeShaderSupported = false, shadersHealthy = false, requested = null),
    )
    // Uno shader rifiutato dal driver vale come non averlo: Balanced, non un crash.
    assertEquals(
      FluidPhysicsTier.Balanced,
      resolvePhysicsTier(renderEffectSupported = true, runtimeShaderSupported = true, shadersHealthy = false, requested = null),
    )
    assertEquals(
      FluidPhysicsTier.Lite,
      resolvePhysicsTier(renderEffectSupported = false, runtimeShaderSupported = false, shadersHealthy = false, requested = null),
    )
  }

  @Test
  fun aRequestCanOnlyStepDownNeverUp() {
    // Scendere è legittimo: il Playground lo usa per far vedere i livelli.
    assertEquals(
      FluidPhysicsTier.Balanced,
      resolvePhysicsTier(true, true, true, requested = FluidPhysicsTier.Balanced),
    )
    assertEquals(
      FluidPhysicsTier.Lite,
      resolvePhysicsTier(true, true, true, requested = FluidPhysicsTier.Lite),
    )
    // Salire no: Full su un dispositivo senza RuntimeShader è una richiesta di crash.
    assertEquals(
      FluidPhysicsTier.Balanced,
      resolvePhysicsTier(true, false, false, requested = FluidPhysicsTier.Full),
    )
    assertEquals(
      FluidPhysicsTier.Lite,
      resolvePhysicsTier(false, false, false, requested = FluidPhysicsTier.Full),
    )
  }

  // I verdetti per sorgente (F0.1). Il compilatore è finto: rifiuta per nome, con il messaggio che il Tab S9 ha
  // dato davvero alla 3.1.2, e conta quante volte compila ogni sorgente.

  private class FakeCompiler(private val refused: Set<GlassShaderSource>) {
    val compiled = mutableListOf<String>()
    val logged = mutableListOf<String>()

    fun compile(source: GlassShaderSource) {
      compiled += source.name
      if (source in refused) throw IllegalArgumentException("error: 70: expected ';', but found 'flat'")
    }

    fun probe() = GlassShaderProbe(GlassShaderSources.all, ::compile) { source, _ -> logged += source.name }
  }

  private fun tier(probe: GlassShaderProbe) = resolvePhysicsTier(
    renderEffectSupported = true,
    runtimeShaderSupported = true,
    shadersHealthy = physicsShadersHealthy(probe),
    requested = null,
  )

  @Test
  fun everySourceAcceptedMeansFullWithTheFringe() {
    val compiler = FakeCompiler(refused = emptySet())
    val probe = compiler.probe()
    assertEquals(FluidPhysicsTier.Full, tier(probe))
    assertTrue(physicsDispersionHealthy(probe))
    assertEquals((PhysicsBaseShaders + PhysicsDispersionShaders).map { it.name }, probe.verdicts().keys.toList())
    assertTrue(probe.verdicts().values.all { it == null })
    assertTrue(compiler.logged.isEmpty())
  }

  @Test
  fun aRefusedBaseSourceHoldsTheEngineAtBalancedAndSaysWhich() {
    // È la 3.1.2: `flat` nello shader poligonale.
    val compiler = FakeCompiler(refused = setOf(GlassShaderSources.PolyMorphRefraction))
    val probe = compiler.probe()
    assertEquals(FluidPhysicsTier.Balanced, tier(probe))
    assertFalse(physicsDispersionHealthy(probe))
    val verdicts = probe.verdicts()
    assertNull(verdicts["SlabGroupRefractionShaderString"])
    assertEquals("error: 70: expected ';', but found 'flat'", verdicts["PolyMorphRefractionShaderString"]?.message)
    // La frangia non si prova nemmeno: senza base non serve, come prima.
    assertEquals(listOf("SlabGroupRefractionShaderString", "PolyMorphRefractionShaderString"), compiler.compiled)
    assertEquals(listOf("PolyMorphRefractionShaderString"), compiler.logged)
  }

  @Test
  fun aRefusedFringeOnlyDropsTheFringe() {
    val compiler = FakeCompiler(refused = setOf(GlassShaderSources.SlabGroupDispersion))
    val probe = compiler.probe()
    assertEquals(FluidPhysicsTier.Full, tier(probe))
    assertFalse(physicsDispersionHealthy(probe))
    assertEquals(listOf("SlabGroupDispersionShaderString"), compiler.logged)
    // La seconda variante con la frangia non serve più: la prima ha già deciso.
    assertFalse("PolyMorphDispersionShaderString" in compiler.compiled)
  }

  @Test
  fun aVerdictIsGivenOnceAndLoggedOnce() {
    val compiler = FakeCompiler(refused = setOf(GlassShaderSources.SlabGroupRefraction))
    val probe = compiler.probe()
    repeat(3) { tier(probe) }
    repeat(2) { physicsDispersionHealthy(probe) }
    probe.probeAll()
    // Ognuna una volta sola, in qualunque ordine l'abbiano chiesta.
    assertEquals(GlassShaderSources.all.map { it.name }.sorted(), compiler.compiled.sorted())
    assertEquals(listOf("SlabGroupRefractionShaderString"), compiler.logged)
  }

  @Test
  fun compilingEverythingGivesAVerdictForEverySourceEvenAfterARefusal() {
    val compiler = FakeCompiler(
      refused = setOf(GlassShaderSources.RoundedRectRefraction, GlassShaderSources.PolyMorphDispersion),
    )
    val probe = compiler.probe()
    val verdicts = probe.probeAll()
    assertEquals(GlassShaderSources.all.map { it.name }, verdicts.keys.toList())
    assertEquals(
      setOf("RoundedRectRefractionShaderString", "PolyMorphDispersionShaderString"),
      verdicts.filterValues { it != null }.keys,
    )
    assertEquals(compiler.logged.toSet(), verdicts.filterValues { it != null }.keys)
    // Dopo, chi chiede legge i verdetti senza ricompilare.
    val compiledBefore = compiler.compiled.size
    assertEquals(FluidPhysicsTier.Full, tier(probe))
    assertFalse(physicsDispersionHealthy(probe))
    assertEquals(compiledBefore, compiler.compiled.size)
  }

  @Test
  fun theFamiliesAreSourcesTheProbeKnows() {
    val names = GlassShaderSources.all.map { it.name }
    assertEquals("Un nome ripetuto confonderebbe i verdetti.", names.size, names.toSet().size)
    for (source in PhysicsBaseShaders + PhysicsDispersionShaders) assertTrue(source.name, source in GlassShaderSources.all)
  }
}
