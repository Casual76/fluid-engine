package dev.antigravity.fluidengine.ui.fluid.toolbar

import dev.antigravity.fluidengine.ui.fluid.FluidSearchFieldDefaults
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTrace
import dev.antigravity.fluidengine.ui.glass.field.trace.GlassTraceOutput
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** F6B.7: le tracce del composer per il banco ([ComposerTraces]) dicono ciò che dicono i test del campo. */
internal class ComposerTracesTest {
  private fun trace(name: String) = ComposerTraces.build(ComposerTraces.scenes().first { it.name == name })

  private fun GlassTrace.measure(ms: Double, key: String): Float =
    (frames.minBy { abs(it.tMs - ms) }.elements.first { it["name"] == "misure" }[key] as Number).toFloat()

  @Suppress("UNCHECKED_CAST")
  private fun GlassTrace.layers(ms: Double, element: String = "contenuto"): List<Map<String, Any?>> =
    frames.minBy { abs(it.tMs - ms) }.elements.first { it["name"] == element }["content"] as List<Map<String, Any?>>

  // F6B.8b: il campo aperto è alto 48 dp (scelta di Alessio del 23 novembre 2026, «tasti più grandi»); prima 40.
  private val row = FluidSearchFieldDefaults.BarFieldHeight.value

  @Test fun theLinesTraceGrowsUpwardWithTheBottomStillAndScrollsPastTheLimit() {
    val trace = trace("composer-righe")
    val bottoms = trace.frames.map { frame -> (frame.elements.first { it["name"] == "misure" }["campoBasso"] as Number).toFloat() }
    // La tastiera è su: il bordo basso del vetro non si muove mai.
    assertTrue(bottoms.all { abs(it - bottoms.first()) < 0.01f })
    // Una riga: 48 dp; con più righe i due margini da 8 e 23 dp per riga (bodyLarge), con la molla, fino a cinque.
    fun lines(n: Int) = 2 * 8f + 23f * n
    assertEquals(row, trace.measure(300.0, "campoAltezza"), 0.05f)
    assertEquals(lines(2), trace.measure(780.0, "campoAltezza"), 0.3f)
    assertEquals(lines(3), trace.measure(1180.0, "campoAltezza"), 0.3f)
    assertEquals(lines(5), trace.measure(1980.0, "campoAltezza"), 0.3f)
    // La sesta riga scorre dentro: il campo resta alto cinque righe.
    assertEquals(trace.measure(1980.0, "campoAltezza"), trace.measure(2380.0, "campoAltezza"), 0.01f)
    // La crescita è continua: nessun salto di una riga fra due fotogrammi a 120 Hz, nemmeno quando cala di quattro.
    val heights = trace.frames.map { frame -> (frame.elements.first { it["name"] == "misure" }["campoAltezza"] as Number).toFloat() }
    assertTrue(heights.zipWithNext().all { (a, b) -> abs(b - a) < 10f })
    // Inviato: torna a una riga.
    assertEquals(row, trace.measure(2990.0, "campoAltezza"), 0.05f)
    // Mentre cresce il piano segue senza viaggio.
    assertEquals(1f, trace.measure(500.0, "segue"), 0f)
  }

  @Test fun theAttachmentsTraceOpensTheTrayUpAndClosesItAfterItsContentLeft() {
    val trace = trace("composer-allegati")
    val bottom = trace.measure(0.0, "campoBasso")
    for (ms in listOf(200.0, 600.0, 1200.0, 1700.0, 2200.0)) assertEquals(bottom, trace.measure(ms, "campoBasso"), 0.01f)
    // Il vassoio: 6 dp di margine, la pillola da 40 e 4 dp prima della riga (F6B.8b; prima la pillola era da 30, senza spazio).
    assertEquals(row + 50f, trace.measure(450.0, "campoAltezza"), 0.3f)
    assertEquals(row + 50f, trace.measure(1450.0, "campoAltezza"), 0.3f)
    // Tolto l'ultimo, il contenuto esce sfocato e poi il vetro si richiude: a 100 ms è ancora aperto, alla fine chiuso.
    assertEquals(row + 50f, trace.measure(1600.0, "campoAltezza"), 0.3f)
    assertTrue(trace.layers(1600.0).any { (it["name"] as String).startsWith("vassoio") })
    assertEquals(row, trace.measure(2250.0, "campoAltezza"), 0.05f)
    assertTrue(trace.layers(2250.0).none { (it["name"] as String).startsWith("vassoio") })
  }

  @Test fun theKeyTraceNeverShowsMoreThanTwoFacesAndTheVoiceLineReplacesTheText() {
    val trace = trace("composer-tasto-stati")
    for (frame in trace.frames) {
      val faces = trace.layers(frame.tMs).count { (it["name"] as String).startsWith("destra") }
      assertTrue("${frame.tMs}: $faces facce", faces in 1..2)
    }
    assertTrue(trace.layers(2400.0).any { (it["name"] as String).startsWith("voce") })
    assertTrue(trace.layers(2400.0).none { (it["name"] as String).startsWith("testo") })
    assertTrue(trace.layers(3500.0).none { (it["name"] as String).startsWith("voce") })
  }

  @Test fun theHaloIsStillAtRestAndAnimatedWhileTheFieldIsActive() {
    val trace = trace("composer-alone")
    fun scia(ms: Double) = trace.layers(ms, "alone-sopra").any { (it["name"] as String).startsWith("scia") }
    // A riposo: presente al 70% e fermo (lo stesso disegno in due istanti), senza giro di luce.
    assertEquals(0.70f, trace.measure(100.0, "alonePresenza"), 1e-4f)
    assertEquals(0f, trace.measure(250.0, "aloneMoto"), 0f)
    assertEquals(trace.layers(0.0, "alone-dietro"), trace.layers(250.0, "alone-dietro"))
    assertEquals(trace.layers(0.0, "alone-sopra"), trace.layers(250.0, "alone-sopra"))
    assertTrue(!scia(250.0))
    // Aperto: pieno, il moto entra in 400 ms, la scia gira e il disegno cambia a ogni istante.
    assertEquals(1f, trace.measure(800.0, "alonePresenza"), 1e-4f)
    assertEquals(1f, trace.measure(800.0, "aloneMoto"), 1e-4f)
    assertTrue(scia(1000.0))
    assertTrue(trace.layers(1000.0, "alone-sopra") != trace.layers(1100.0, "alone-sopra"))
    // In un secondo aperto il giro di luce fa un quinto del bordo (5 s a giro), al lavoro di più.
    val open = trace.measure(1800.0, "aloneTempo") - trace.measure(800.0, "aloneTempo")
    assertEquals(1f, open, 0.02f)
    val working = trace.measure(4400.0, "aloneTempo") - trace.measure(3900.0, "aloneTempo")
    assertEquals(0.8f, working, 0.02f)
    // Il moto non salta mai: fra due fotogrammi a 120 Hz al più 1/48 in entrata (400 ms) e 1/72 in uscita (600 ms).
    val motion = trace.frames.map { frame -> (frame.elements.first { it["name"] == "misure" }["aloneMoto"] as Number).toFloat() }
    assertTrue(motion.zipWithNext().all { (a, b) -> abs(b - a) <= 1f / 48f + 1e-4f })
    // Chiuso: il moto esce in 600 ms, la presenza torna al 70%, e l'alone è di nuovo fermo.
    assertEquals(0.70f, trace.measure(5050.0, "alonePresenza"), 1e-4f)
    assertEquals(0f, trace.measure(5150.0, "aloneMoto"), 0f)
    assertTrue(!scia(5150.0))
    // Il tempo dell'alone è fermo (l'ampiezza finisce di posarsi e poi anche l'orologio vero smette).
    assertEquals(trace.measure(5250.0, "aloneTempo"), trace.measure(5450.0, "aloneTempo"), 0f)
  }

  /** Scrive le tracce del banco con `GLASS_F6B7_DIR` (piano 4.3): `<cartella>/f6b-7/<nome>/trace.json`. */
  @Test fun exportTheBenchTraces() {
    val dir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B7_DIR")) ?: return
    for (scene in ComposerTraces.scenes()) {
      val trace = ComposerTraces.build(scene)
      trace.write(GlassTraceOutput.fileFor(dir, trace))
    }
  }
}
