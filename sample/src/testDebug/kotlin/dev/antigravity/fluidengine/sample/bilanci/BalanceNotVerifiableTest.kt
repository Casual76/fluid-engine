package dev.antigravity.fluidengine.sample.bilanci

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.GlassBenchDevice
import dev.antigravity.fluidengine.sample.GlassBenchHeap
import dev.antigravity.fluidengine.sample.novita.NewElementSections
import java.util.Collections
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F7.32d (D5 della review R3) · Le scene che al PC non si misurano hanno nel loro frammento righe `NV` in ogni colonna, e
 * `F8BalanceTest` le salta. Fino a F7.32c le saltava **senza provare a montarle**: se un giorno Robolectric le avesse
 * sapute montare, sarebbero rimaste NV per sempre. Qui ognuna prova il copione intero, nella sua attività (una scena che
 * ferma la composizione, come la mappa con MapLibre, non tocca le altre: in `F8BalanceTest` fermava anche le cartoline
 * che venivano dopo, F7.29). Se il copione riesce senza nessun errore, e la composizione dopo è ancora viva, la prova
 * cade, «la scena ora si misura: togliere NV», e stampa le righe misurate da copiare nel frammento.
 *
 * Un errore della scena può arrivare in tre modi: dal copione (una composizione che lancia), da un altro filo (il PDF che
 * si prepara su IO), o dagli effetti della composizione, che la regola di Compose raccoglie e rilancia a prova finita
 * (PdfRenderer o MapLibre in un `LaunchedEffect`, che fermano anche il Recomposer). La regola [notMounting], fuori da
 * quella di Compose, li vede tutti: ognuno conferma la NV.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = "w411dp-h891dp-mdpi",
  application = GlassBenchDevice::class,
  shadows = [
    GlassBenchHeap::class, CountingRenderNode::class, CountingBitmap::class, CountingRenderEffect::class,
    CountingRuntimeShader::class,
  ],
)
class BalanceNotVerifiableTest(private val pack: String, private val sceneId: String) {

  /** La scena si è misurata davvero: l'unico esito che fa cadere la prova. */
  private class NowMeasurable(message: String) : AssertionError(message)

  /** Fuori dalla regola di Compose: un errore della scena, anche quello rilanciato a prova finita, conferma la NV. */
  @get:Rule(order = 0)
  val notMounting = TestRule { base, _ ->
    object : Statement() {
      override fun evaluate() {
        try {
          base.evaluate()
        } catch (error: Throwable) {
          val all = flatten(error)
          val scene = all.firstOrNull { it !is NowMeasurable } ?: throw error
          confirmed(scene)
        }
      }
    }
  }

  @get:Rule(order = 1)
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val harness = BalanceHarness(compose)
  private lateinit var restore: AutoCloseable

  @Before
  fun start() {
    compose.mainClock.autoAdvance = false
    restore = harness.install()
  }

  @After
  fun stop() = restore.close()

  @Test
  fun laScenaNonSiMontaAlPc() {
    val file = GlassBalanceRegistry.require(GlassBalanceRegistry.fragmentName(pack))
    val scene = NewElementSections.single { it.pack == pack }.scenes.single { it.id == sceneId }
    val reason = file.rowsOf(sceneId).first().reason
    // Anche un errore su un altro filo (il PDF che si prepara in sottofondo) vuol dire che la scena al PC non si misura.
    val elsewhere = Collections.synchronizedList(ArrayList<Throwable>())
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, error -> elsewhere += error }
    val attempt = try {
      harness.awaitsBackground = sceneId in file.background
      runCatching { BalanceRuns.measureNewElement(harness, scene, file.margins[sceneId] ?: 0) }
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
    val error = attempt.exceptionOrNull() ?: elsewhere.firstOrNull()
    if (error != null) return confirmed(error)
    // La composizione è ancora viva? Un effetto caduto ferma il Recomposer: la scena non se ne va, e niente si compone più.
    harness.show { Box(Modifier.size(1.dp).testTag(Probe)) }
    repeat(3) { harness.frame() }
    if (harness.semantics().none { it.config.getOrNull(SemanticsProperties.TestTag) == Probe }) {
      println("Bilancio $sceneId: NV confermata, al PC la composizione si ferma (il copione finisce, ma niente si compone più)")
      return
    }
    attempt.getOrThrow().compare(file, sceneId, version = "F7.32d", proof = "F8BalanceTest")
    throw NowMeasurable(
      "$pack/$sceneId: la scena ora si misura al PC: togliere NV e scrivere le righe misurate qui sopra (motivo di oggi: $reason)",
    )
  }

  private fun confirmed(error: Throwable) {
    println("Bilancio $sceneId: NV confermata, al PC non si monta (${error.javaClass.simpleName}: ${error.message?.take(300)})")
  }

  companion object {
    private const val Probe = "bilancio-sonda-viva"

    /** L'errore e quelli che porta con sé (soppressi, o raccolti da JUnit). */
    private fun flatten(error: Throwable): List<Throwable> {
      val parts = if (error is MultipleFailureException) error.failures else listOf(error)
      return parts.flatMap { listOf(it) + it.suppressed }
    }

    /** Le scene con tutte le righe NV, per pacchetto. */
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(name = "{1}")
    fun scenes(): List<Array<Any>> = NewElementSections.flatMap { section ->
      val file = GlassBalanceRegistry.load(GlassBalanceRegistry.fragmentName(section.pack)) ?: return@flatMap emptyList()
      section.scenes
        .filter { scene ->
          val rows = file.rowsOf(scene.id)
          rows.isNotEmpty() && rows.all { row -> row.values.values.all { it == BalanceValue.NotVerifiable } }
        }
        .map { arrayOf<Any>(section.pack, it.id) }
    }
  }
}
