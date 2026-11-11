package dev.antigravity.fluidengine.ui.glass.backdrop

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.HighlightStyle
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSource
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Il vetro di oggi ripiega davvero quando il dispositivo rifiuta uno shader (F0.11).
 *
 * Fino alla 3.1.2 lente, riflessi e bagliore guardavano solo il livello di API: su un dispositivo il cui
 * compilatore rifiuta una loro sorgente, il costruttore di `RuntimeShader` lanciava dentro il disegno, a ogni
 * schermata di vetro. Ora chiedono il verdetto della propria sorgente e, su un rifiuto, prendono il ramo che
 * hanno sotto Android 13. Qui si prova sul JVM con il compilatore finto della sonda: AGSL si dichiara presente
 * (sul JVM `SDK_INT` vale zero) e il compilatore rifiuta per nome.
 *
 * Perché questi test sono rossi sul codice di prima è ragionato, non provato: nessuno chiedeva il verdetto (quindi nessuna
 * riga d'errore nei casi «rifiutata»), e sul JVM la sola guardia di API non lasciava passare niente (i casi «accettata»).
 * Non si sono mai eseguiti sul codice di prima, e non potrebbero compilare: usano `GlassFieldHealth.installForTesting`,
 * che prima non esisteva.
 */
class GlassShaderFallbackTest {

  private class FakeCompiler(private val refused: Set<GlassShaderSource>) {
    val compiled = mutableListOf<String>()
    val logged = mutableListOf<String>()

    fun compile(source: GlassShaderSource) {
      compiled += source.name
      if (source in refused) throw IllegalArgumentException("error: 1: rifiutata apposta dal test")
    }
  }

  private lateinit var compiler: FakeCompiler

  private fun install(vararg refused: GlassShaderSource) {
    compiler = FakeCompiler(refused.toSet())
    GlassFieldHealth.installForTesting(agsl = true, compile = compiler::compile) { source, _ ->
      compiler.logged += source.name
    }
  }

  @After
  fun forget() {
    GlassFieldHealth.resetForTesting()
  }

  /** Arrivati allo shader: sul JVM non c'è una piattaforma che lo trasformi in un effetto, ci si ferma qui. */
  private class ShaderReached : RuntimeException()

  /** Uno scope di vetro senza piattaforma: annota le richieste di shader e si ferma alla prima. */
  private class Scope : BackdropEffectScopeImpl() {
    override val shape: Shape = RoundedCornerShape(12f)
    val requested = mutableListOf<Pair<String, String>>()

    init {
      size = Size(200f, 100f)
      padding = 12f
    }

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
      requested += key to string
      throw ShaderReached()
    }
  }

  private val lenses = listOf(
    Triple(GlassShaderSources.RoundedRectRefraction, false, "Refraction"),
    Triple(GlassShaderSources.RoundedRectRefractionWithDispersion, true, "RefractionWithDispersion"),
  )

  @Test
  fun aRefusedLensTakesTheBranchBelowAndroid13() {
    for ((source, dispersion, _) in lenses) {
      install(source)
      val scope = Scope()
      scope.lens(refractionHeight = 8f, refractionAmount = 16f, chromaticAberration = dispersion)
      // Il ramo sotto API 33: nessun effetto, margine intatto, nessuno shader chiesto.
      assertNull(source.name, scope.renderEffect)
      assertEquals(source.name, 12f, scope.padding, 0f)
      assertTrue(source.name, scope.requested.isEmpty())
      assertEquals(listOf(source.name), compiler.logged)
    }
  }

  @Test
  fun anAcceptedLensDrawsAsBefore() {
    for ((source, dispersion, key) in lenses) {
      install()
      val scope = Scope()
      assertThrows(ShaderReached::class.java) {
        scope.lens(refractionHeight = 8f, refractionAmount = 16f, chromaticAberration = dispersion)
      }
      // Come prima: la stessa chiave, la stessa sorgente, il margine scontato dell'altezza della lente.
      assertEquals(listOf(key to source.text), scope.requested)
      assertEquals(source.name, 4f, scope.padding, 0f)
      assertTrue(compiler.logged.isEmpty())
    }
  }

  @Test
  fun aSurfaceWithoutRefractionNeverCompilesALens() {
    install()
    val scope = Scope()
    scope.lens(refractionHeight = 0f, refractionAmount = 16f)
    scope.lens(refractionHeight = 8f, refractionAmount = 0f)
    assertTrue(compiler.compiled.isEmpty())
    assertEquals(0, GlassFieldHealth.consultationsForTesting)
  }

  private fun highlightShader(style: HighlightStyle): RuntimeShader? = with(style) {
    CanvasDrawScope().createShader(RoundedCornerShape(12f), Size(200f, 100f), Density(1f), RuntimeShaderCacheImpl())
  }

  private val highlights = listOf(
    HighlightStyle.Default() to GlassShaderSources.DefaultHighlight,
    HighlightStyle.Ambient() to GlassShaderSources.AmbientHighlight,
  )

  @Test
  fun aRefusedHighlightIsTheFlatOne() {
    for ((style, source) in highlights) {
      install(source)
      // `null` è il riflesso a tinta piatta, quello di Android 12: il modificatore disegna senza shader.
      assertNull(source.name, highlightShader(style))
      assertEquals(listOf(source.name), compiler.logged)
    }
  }

  @Test
  fun anAcceptedHighlightKeepsItsShader() {
    for ((style, source) in highlights) {
      install()
      assertNotNull(source.name, highlightShader(style))
      assertEquals(listOf(source.name), compiler.compiled)
      assertTrue(compiler.logged.isEmpty())
    }
  }

  private fun glow() = GlassTouchHighlight(animationScope = CoroutineScope(Dispatchers.Unconfined))

  @Test
  fun aRefusedGlowIsTheFlatLift() {
    install(GlassShaderSources.Glow)
    assertFalse(glow().glowUsesShader)
    assertEquals(listOf(GlassShaderSources.Glow.name), compiler.logged)
  }

  @Test
  fun anAcceptedGlowKeepsItsShader() {
    install()
    assertTrue(glow().glowUsesShader)
    assertEquals(listOf(GlassShaderSources.Glow.name), compiler.compiled)
    assertTrue(compiler.logged.isEmpty())
  }

  /** Il verdetto si chiede una volta per processo: a regime il disegno legge una risposta, non la sonda. */
  @Test
  fun theVerdictIsNotAskedAtEveryDraw() {
    install(GlassShaderSources.RoundedRectRefraction)
    repeat(100) { Scope().lens(refractionHeight = 8f, refractionAmount = 16f) }
    repeat(100) { highlightShader(HighlightStyle.Default()) }
    repeat(20) { glow() }
    assertEquals(3, GlassFieldHealth.consultationsForTesting)
    assertEquals(
      listOf("RoundedRectRefractionShaderString", "DefaultHighlightShaderString", "GlassGlowShaderString"),
      compiler.compiled,
    )
    assertEquals(listOf("RoundedRectRefractionShaderString"), compiler.logged)
  }

  /**
   * Nessun percorso arriva al compilatore senza un verdetto. Ogni file dei sorgenti di `engine-ui` che chiama
   * `obtainRuntimeShader`, `runtimeShaderEffect`, il costruttore `RuntimeShader(…)` o la cache condivisa deve
   * stare qui sotto, con il motivo per cui non può lanciare su una sorgente dell'engine rifiutata. Chi
   * disegna uno shader dell'engine passa da `obtainEngineShader` e non compare.
   */
  @Test
  fun noDrawPathReachesTheCompilerWithoutAVerdict() {
    val allowed = mapOf(
      "glass/backdrop/RuntimeShaderCache.kt" to "la cache e la sua porta con il verdetto, `obtainEngineShader`",
      "glass/backdrop/BackdropEffectScope.kt" to "delega alla cache, senza sorgenti sue",
      "glass/backdrop/RuntimeShader.kt" to "il costruttore",
      "glass/backdrop/effects/RenderEffect.kt" to "`runtimeShaderEffect`, API pubblica per le sorgenti delle app",
      "glass/backdrop/effects/Lens.kt" to "chiede `GlassFieldHealth.usable` prima di toccare il margine",
      "glass/field/GlassFieldHealth.kt" to "la sonda, che compila dopo il controllo di API",
      "fluidphysics/FluidPhysicsEffects.kt" to "solo a `Full`, livello deciso dai verdetti delle sue famiglie",
    )
    val call = Regex("""\b(obtainRuntimeShader|runtimeShaderEffect|RuntimeShader|SharedRuntimeShaders\.obtain)\s*\(""")
    val declaration = Regex("""\bfun\s+(?:[\w.]+\.)?(?:obtainRuntimeShader|runtimeShaderEffect|RuntimeShader|obtain)\s*\(""")
    val root = listOf(File("src/main/kotlin"), File("engine-ui/src/main/kotlin"))
      .map { File(it, "dev/antigravity/fluidengine/ui") }
      .firstOrNull { it.isDirectory }
      ?: throw AssertionError("Non trovo i sorgenti di engine-ui da ${File(".").absolutePath}.")
    val offenders = mutableListOf<String>()
    root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
      val path = file.relativeTo(root).invariantSeparatorsPath
      val code = file.readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")
      code.lines().forEachIndexed { index, line ->
        if (call.containsMatchIn(line) && !declaration.containsMatchIn(line) && path !in allowed) {
          offenders += "$path:${index + 1}: ${line.trim()}"
        }
      }
    }
    if (offenders.isNotEmpty()) {
      fail(
        "Arrivano al compilatore AGSL senza passare da un verdetto (obtainEngineShader): un rifiuto del " +
          "dispositivo lancerebbe dentro il disegno.\n" + offenders.joinToString("\n"),
      )
    }
  }
}
