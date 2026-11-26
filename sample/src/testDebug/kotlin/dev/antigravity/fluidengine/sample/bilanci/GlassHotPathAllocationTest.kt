package dev.antigravity.fluidengine.sample.bilanci

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.GlassBenchDevice
import dev.antigravity.fluidengine.sample.GlassBenchHeap
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.lang.reflect.Method
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
 * F7.28 · Il banco ripetibile delle allocazioni sul percorso caldo del vetro: i byte che il filo principale alloca per
 * fotogramma di moto stabile, **meno** quelli della stessa scena senza vetro (lo stesso albero, lo stesso moto, lo stesso
 * filo, lo stesso numero di fotogrammi). I contatori delle fabbriche non vedono le allocazioni di Kotlin: questo sì.
 *
 * Come si misura. Il contatore di HotSpot del filo (`getCurrentThreadAllocatedBytes`, per riflessione: la classpath di
 * Android non ha `java.lang.management`). Prima 300 fotogrammi per scena, perché il JIT prenda il percorso del vetro;
 * poi due serie per scena, da 40 e da 80 fotogrammi: la differenza toglie il costo fisso; tre ripetizioni alternate
 * vetro/senza vetro. Il banco vede un incremento di qualche KB per fotogramma (la mutazione ne alloca 8), non un oggetto
 * da cento byte: il numero dipende ancora da quanto è caldo il JIT, e il tetto lascia quel margine. È rosso solo un incremento **ripetuto**: il più piccolo dei tre
 * incrementi sopra il tetto di `banco.bilancio`. Il log del vetro è spento (scrive stringhe); niente ombre che contano.
 *
 * Il moto: un intero letto nel posizionamento che sposta di 3 px a fotogramma la parte che si muove (la lastra, il
 * gruppo, o il contenuto della sorgente sotto una lastra ferma). Il numero sul JVM non è quello di ART: serve a vedere un
 * incremento, non a dire quanto alloca il telefono (per la pila delle allocazioni, una traccia ART sul tablet).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp-mdpi", application = GlassBenchDevice::class, shadows = [GlassBenchHeap::class])
class GlassHotPathAllocationTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val harness = BalanceHarness(compose)
  private lateinit var restore: AutoCloseable
  private val x = mutableIntStateOf(0)

  @Before
  fun start() {
    compose.mainClock.autoAdvance = false
    restore = harness.install(frameLog = false)
  }

  @After
  fun stop() = restore.close()

  @Composable
  private fun Stage(source: @Composable () -> Unit = {}, content: @Composable (GlassBackdropState) -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val backdrop = rememberGlassBackdrop()
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9))) { source() }
        content(backdrop)
      }
    }
  }

  @Composable
  private fun Modifier.slab(glass: Boolean, backdrop: GlassBackdropState): Modifier =
    if (glass) {
      glassSurface(
        state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape,
        role = GlassRole.Floating, optics = GlassDefaults.optics(GlassRole.Floating),
      )
    } else {
      background(Color.White.copy(alpha = 0.4f), FluidCapsuleShape)
    }

  @Composable
  private fun Scene(name: String, glass: Boolean, junk: Boolean) {
    val moving = Modifier.offset { IntOffset(x.intValue % 300, 0) }
    // La mutazione lavora a ogni fotogramma: legge il moto nel disegno (come un componente che si ridisegna mentre si
    // sposta) e alloca un array. Senza la lettura il disegno non si rifarebbe: si sposta solo il layer.
    val waste = if (junk) Modifier.drawBehind { x.intValue; sink = FloatArray(JunkFloats) } else Modifier
    when (name) {
      MovingSlab -> Stage { backdrop ->
        Box(moving.padding(start = 40.dp, top = 120.dp).size(156.dp, 56.dp).then(waste).slab(glass, backdrop))
      }
      MovingSource -> Stage(source = {
        Box(moving.padding(start = 20.dp, top = 100.dp).size(240.dp, 120.dp).background(Color.White))
      }) { backdrop ->
        Box(Modifier.padding(start = 60.dp, top = 130.dp).size(156.dp, 56.dp).then(waste).slab(glass, backdrop))
      }
      MovingGroup -> Stage { backdrop ->
        Box(moving.padding(start = 40.dp, top = 120.dp)) {
          if (glass) {
            FluidGlassContainer {
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { FluidGlassIconButton(onClick = {}, backdrop = backdrop) { Text("${it + 1}") } }
              }
            }
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              repeat(3) { Box(Modifier.size(48.dp).background(Color.White, FluidCapsuleShape)) { Text("${it + 1}") } }
            }
          }
        }
      }
      else -> error("scena del banco sconosciuta: $name")
    }
  }

  private val allocated: () -> Long = run {
    val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
    val method: Method = Class.forName("com.sun.management.ThreadMXBean").getMethod("getCurrentThreadAllocatedBytes")
    ({ method.invoke(bean) as Long })
  }

  private fun series(frames: Int): Long {
    val start = allocated()
    repeat(frames) {
      harness.runOnUi { x.intValue += 3 }
      harness.frame()
    }
    return allocated() - start
  }

  /** I byte per fotogramma della scena, tolto il costo fisso: la differenza fra una serie da 80 e una da 40. */
  private fun perFrame(name: String, glass: Boolean, junk: Boolean = false): Long {
    harness.show { Scene(name, glass, junk) }
    repeat(WarmFrames) {
      harness.runOnUi { x.intValue += 3 }
      harness.frame()
    }
    val short = series(40)
    val long = series(80)
    return (long - short) / 40
  }

  /**
   * Il JIT prima delle misure: il percorso del vetro è codice che solo la scena col vetro esegue, e finché il compilatore
   * non lo ha preso alloca di più (a JVM fredda la lastra mossa costava 3,8 KB per fotogramma, a JVM calda 1,1). Trecento
   * fotogrammi per scena, una volta.
   */
  private fun warmJit(name: String, junk: Boolean) {
    for (glass in listOf(true, false)) {
      harness.show { Scene(name, glass, junk = junk && glass) }
      repeat(JitFrames) {
        harness.runOnUi { x.intValue += 3 }
        harness.frame()
      }
    }
  }

  /** Gli incrementi del vetro sul banco vuoto, nelle tre ripetizioni alternate. */
  private fun increments(name: String, junk: Boolean = false): List<Long> = (1..3).also { warmJit(name, junk) }.map {
    val bare = perFrame(name, glass = false)
    val glass = perFrame(name, glass = true, junk = junk)
    println("Banco $name${if (junk) " (mutazione)" else ""}: senza vetro $bare B/f, col vetro $glass B/f, incremento ${glass - bare}")
    glass - bare
  }

  private fun ceiling(name: String): Long {
    val row = checkNotNull(GlassBalanceRegistry.require(BenchFile).row(name, "moto")) { "manca la riga $name moto in $BenchFile" }
    return (row.values.getValue(BalanceColumn.AllocPerFrame) as BalanceValue.Ceiling).value
  }

  private fun check(name: String) {
    val increments = increments(name)
    println("Bilancio misurato | ${name.padEnd(28)} moto  alloc/f ${increments.min()} (ripetizioni $increments)")
    val limit = ceiling(name)
    assertTrue("$name: il vetro alloca ${increments.min()} B per fotogramma in più del banco vuoto in tutte e tre le " +
      "ripetizioni $increments (tetto $limit, $BenchFile). Cercare la pila con una traccia ART sul tablet", increments.min() <= limit)
  }

  @Test fun lastraMossa() = check(MovingSlab)
  @Test fun lastraSuFondoMosso() = check(MovingSource)
  @Test fun gruppoMosso() = check(MovingGroup)

  /** Rosso → verde: la stessa lastra che alloca un array per fotogramma cade, col tetto di sempre. */
  @Test
  fun unArrayPerFotogrammaSulPercorsoCaldoCade() {
    val limit = ceiling(MovingSlab)
    val increments = increments(MovingSlab, junk = true)
    assertTrue("la mutazione deve cadere: $increments (tetto $limit)", increments.min() > limit)
  }

  /**
   * La pila delle allocazioni (facoltativa, per chi cerca da dove vengono i byte di una riga rossa): con la variabile
   * d'ambiente `BILANCI_PILA` il banco registra 200 fotogrammi di ogni scena col vetro e senza in una registrazione JFR
   * (`jdk.ObjectAllocationSample`, per riflessione) e stampa, per il primo chiamante dell'engine o di Compose, i byte per
   * fotogramma in più del banco vuoto. Di serie non gira.
   */
  @Test
  fun pilaDelleAllocazioni() {
    org.junit.Assume.assumeTrue("solo con BILANCI_PILA", System.getenv("BILANCI_PILA") != null)
    for (name in Scenes) {
      val bare = profile(name, glass = false)
      val glass = profile(name, glass = true)
      val sites = (glass.keys + bare.keys).associateWith { (glass[it] ?: 0L) - (bare[it] ?: 0L) }
        .entries.sortedByDescending { it.value }.take(14)
      println("Pila $name, byte per fotogramma in più del banco vuoto, per chiamante:")
      for ((site, bytes) in sites) println("Pila $name | ${bytes / ProfileFrames} B/f | $site")
    }
  }

  private fun profile(name: String, glass: Boolean): Map<String, Long> {
    harness.show { Scene(name, glass, junk = false) }
    repeat(WarmFrames) {
      harness.runOnUi { x.intValue += 3 }
      harness.frame()
    }
    val recordingClass = Class.forName("jdk.jfr.Recording")
    val settingsClass = Class.forName("jdk.jfr.EventSettings")
    val recording = recordingClass.getConstructor().newInstance()
    val settings = recordingClass.getMethod("enable", String::class.java).invoke(recording, "jdk.ObjectAllocationSample")
    settingsClass.getMethod("withStackTrace").invoke(settings)
    settingsClass.getMethod("with", String::class.java, String::class.java).invoke(settings, "throttle", "1000000/s")
    recordingClass.getMethod("start").invoke(recording)
    val thread = Thread.currentThread().name
    repeat(ProfileFrames) {
      harness.runOnUi { x.intValue += 3 }
      harness.frame()
    }
    recordingClass.getMethod("stop").invoke(recording)
    val path = java.nio.file.Files.createTempFile("bilanci", ".jfr")
    recordingClass.getMethod("dump", java.nio.file.Path::class.java).invoke(recording, path)
    recordingClass.getMethod("close").invoke(recording)
    val eventClass = Class.forName("jdk.jfr.consumer.RecordedEvent")
    val stackClass = Class.forName("jdk.jfr.consumer.RecordedStackTrace")
    val frameClass = Class.forName("jdk.jfr.consumer.RecordedFrame")
    val methodClass = Class.forName("jdk.jfr.consumer.RecordedMethod")
    val typeClass = Class.forName("jdk.jfr.consumer.RecordedClass")
    val threadClass = Class.forName("jdk.jfr.consumer.RecordedThread")
    val events = Class.forName("jdk.jfr.consumer.RecordingFile").getMethod("readAllEvents", java.nio.file.Path::class.java)
      .invoke(null, path) as List<*>
    java.nio.file.Files.deleteIfExists(path)
    val out = HashMap<String, Long>()
    for (event in events) {
      val owner = eventClass.getMethod("getThread", String::class.java).invoke(event, "eventThread")
      if (owner != null && threadClass.getMethod("getJavaName").invoke(owner) != thread) continue
      val stack = eventClass.getMethod("getStackTrace").invoke(event) ?: continue
      val frames = stackClass.getMethod("getFrames").invoke(stack) as List<*>
      val described = frames.map { frame ->
        val method = frameClass.getMethod("getMethod").invoke(frame)
        val type = typeClass.getMethod("getName").invoke(methodClass.getMethod("getType").invoke(method)) as String
        "${type.substringAfterLast('.')}.${methodClass.getMethod("getName").invoke(method)}:${frameClass.getMethod("getLineNumber").invoke(frame)}" to type
      }
      val first = described.indexOfFirst { (_, type) ->
        (type.startsWith("dev.antigravity.") && ".bilanci." !in type) || type.startsWith("androidx.compose.")
      }
      val site = if (first < 0) described.take(2).joinToString(" < ") { it.first }
      else described.drop(first).take(2).joinToString(" < ") { it.first }
      val weight = eventClass.getMethod("getLong", String::class.java).invoke(event, "weight") as Long
      out[site] = (out[site] ?: 0L) + weight
    }
    return out
  }

  companion object {
    const val ProfileFrames = 200
    const val BenchFile = "banco.bilancio"
    const val MovingSlab = "banco:lastra-mossa"
    const val MovingSource = "banco:lastra-su-fondo-mosso"
    const val MovingGroup = "banco:gruppo-mosso"
    val Scenes = listOf(MovingSlab, MovingSource, MovingGroup)
    const val WarmFrames = 60

    /** La mutazione: 2048 float (8 KiB e l'intestazione) per fotogramma. */
    const val JunkFloats = 2048

    /** I fotogrammi di riscaldamento del JIT, per scena e una volta sola. */
    const val JitFrames = 300

    @Volatile
    @JvmStatic
    var sink: FloatArray? = null
  }
}
