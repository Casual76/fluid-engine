package dev.antigravity.fluidengine.sample.bilanci

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.ai.memory.AiMemoryBook
import dev.antigravity.fluidengine.ai.memory.AiMemoryConfig
import dev.antigravity.fluidengine.ai.memory.AiMemoryStore
import dev.antigravity.fluidengine.sample.GlassBenchDevice
import dev.antigravity.fluidengine.sample.GlassBenchHeap
import dev.antigravity.fluidengine.sample.novita.NewElementSections
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F7.28 · I bilanci degli elementi nuovi (F8): ogni scena della pagina «Elementi nuovi», montata com'è nella galleria
 * nel formato telefono (411 × 891 dp a densità 1), misurata col copione comune ([BalanceRuns.measureNewElement]) e
 * confrontata col frammento del suo pacchetto (`f8p<n>.bilancio`, del pacchetto).
 *
 * Una scena che al PC non si può montare (una libreria nativa che manca, un servizio di sistema) ha nel frammento le
 * sue righe con `NV` in ogni colonna e il motivo: qui non si monta, e la prova vera è quella scritta nel motivo.
 * Un pacchetto nuovo, che qui non ha ancora il suo metodo, lo misura [pacchettiNuovi].
 */
@RunWith(RobolectricTestRunner::class)
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
class F8BalanceTest {

  @get:Rule
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

  /**
   * F7.29 · Le scene di F9.3 aprono la memoria di PampAI su un filo in sottofondo e intanto mostrano «apro la memoria…».
   * La prima apertura del processo costa sui 80 ms veri (le classi e l'indice; le altre 2–3 ms): arrivava dopo
   * l'assestamento, e la chat vera compariva a caso nel moto o nell'uscita di `ricordi-chat` (2 corse su 4, 27/11).
   * Si apre una memoria vuota una volta prima della misura, fuori dai contatori (nessun layer, nessun filtro): la scena
   * arriva nell'entrata, come sul telefono dopo la prima apertura. È lo stesso caso dei programmi condivisi del vetro
   * ([BalanceHarness]): un costo che il processo paga una volta sola non va in conto alla scena che capita prima.
   */
  private fun openMemoryOnce() {
    runBlocking { AiMemoryBook.open(AiMemoryStore.inMemory(), AiMemoryConfig()) }
  }

  private fun pack(name: String) {
    val section = NewElementSections.single { it.pack == name }
    val file = GlassBalanceRegistry.load(GlassBalanceRegistry.fragmentName(name))
    val problems = ArrayList<String>()
    if (file == null) problems += "$name: manca il frammento ${GlassBalanceRegistry.fragmentName(name)}"
    if (name == "F9.3") openMemoryOnce()
    for (scene in section.scenes) {
      val rows = file?.rowsOf(scene.id).orEmpty()
      if (rows.isNotEmpty() && rows.all { row -> row.values.values.all { it == BalanceValue.NotVerifiable } }) {
        println("Bilancio ${scene.id}: non misurabile al PC (${rows.first().reason})")
        continue
      }
      harness.clearLog()
      val meter = try {
        BalanceRuns.measureNewElement(harness, scene, file?.margins?.get(scene.id) ?: 0)
      } catch (error: Throwable) {
        problems += "$name/${scene.id}: la scena non si misura al PC (${error.javaClass.simpleName}: ${error.message?.take(300)}). " +
          "Se è così per natura, righe NV col motivo nel frammento"
        error.printStackTrace()
        runCatching {
          harness.clear()
          harness.idle(500, drawEvery = 8)
        }
        continue
      }
      val registry = file ?: BalanceFile(GlassBalanceRegistry.fragmentName(name), emptyList(), emptyList(), emptyMap())
      problems += meter.compare(registry, scene.id, version = "F7.28", proof = "F8BalanceTest")
    }
    assertTrue("$name:\n  ${problems.joinToString("\n  ")}", problems.isEmpty())
  }

  @Test fun f8p1() = pack("F8.1")
  @Test fun f8p2() = pack("F8.2")
  @Test fun f8p3() = pack("F8.3")
  @Test fun f8p4() = pack("F8.4")
  @Test fun f8p5() = pack("F8.5")
  @Test fun f8p6() = pack("F8.6")
  @Test fun f8p7() = pack("F8.7")
  @Test fun f8p8() = pack("F8.8")
  @Test fun f8p9() = pack("F8.9")
  @Test fun f8p10() = pack("F8.10")
  @Test fun f8p11() = pack("F8.11")
  @Test fun f8p12() = pack("F8.12")
  @Test fun f8p13() = pack("F8.13")
  @Test fun f8p14() = pack("F8.14")
  @Test fun f8p15() = pack("F8.15")
  @Test fun f8p16() = pack("F8.16")
  @Test fun f8p17() = pack("F8.17")
  @Test fun f8p18() = pack("F8.18")
  @Test fun f8p19() = pack("F8.19")
  @Test fun f8p20() = pack("F8.20")
  @Test fun f8p21() = pack("F8.21")
  @Test fun f8p22() = pack("F8.22")
  @Test fun f8p23() = pack("F8.23")
  @Test fun f8p24() = pack("F8.24")
  @Test fun f8p25() = pack("F8.25")
  @Test fun f8p26() = pack("F8.26")
  @Test fun f9p1() = pack("F9.1")
  @Test fun f9p2() = pack("F9.2")
  @Test fun f9p3() = pack("F9.3")

  /** I pacchetti arrivati dopo F7.28, che non hanno ancora il loro metodo: si misurano qui, tutti insieme. */
  @Test
  fun pacchettiNuovi() {
    val fresh = NewElementSections.map { it.pack }.filter { it !in Known }
    for (name in fresh) pack(name)
  }

  companion object {
    /** I pacchetti col loro metodo (F7.29: F8.24, F8.25, F8.26, F9.1, F9.2 e F9.3, arrivati dopo F7.28). */
    val Known = (1..26).map { "F8.$it" }.toSet() + setOf("F9.1", "F9.2", "F9.3")
  }
}
