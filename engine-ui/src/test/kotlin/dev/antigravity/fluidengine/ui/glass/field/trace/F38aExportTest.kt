package dev.antigravity.fluidengine.ui.glass.field.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Le tracce di F3.8a ([F38aTraces]): il piano flottante col contenuto. Senza la variabile d'ambiente `GLASS_F38A_DIR` si
 * costruiscono e si controllano, senza scrivere niente; con la variabile si scrivono sotto quella cartella, una per
 * traccia (`<nome>/trace.json`).
 *
 * I controlli sono quelli che S3 ha fatto a occhio sul tablet, letti dalla traccia: nessun contenuto scritto fuori dal
 * suo vetro, il contenuto di chi nasce che arriva dopo metà viaggio, due tasti fermi che non passano dal palco,
 * «Unisci» e «Separa» che durano un viaggio.
 */
class F38aExportTest {

  private val traces by lazy { F38aTraces.scenes().associate { it.name to F38aTraces.build(it) } }

  @Suppress("UNCHECKED_CAST")
  private fun layers(element: Map<String, Any?>): List<Map<String, Any?>> =
    (element["content"] as? List<Map<String, Any?>>).orEmpty()

  /**
   * Lo strato [name] nel fotogramma, riportato alle coordinate della **tela**: nella traccia il contenuto (e il suo
   * ritaglio) sta rispetto all'angolo del suo elemento, e un pezzo cambia elemento quando passa dal riposo al palco.
   */
  @Suppress("UNCHECKED_CAST")
  private fun layer(frame: GlassTraceFrame, name: String): Map<String, Any?>? {
    for (element in frame.elements) {
      val found = layers(element).firstOrNull { it["name"] == name } ?: continue
      val dx = number(element, "x", 0.0)
      val dy = number(element, "y", 0.0)
      val out = LinkedHashMap(found)
      out["x"] = number(found, "x", 0.0) + dx
      out["y"] = number(found, "y", 0.0) + dy
      (found["clip"] as? Map<String, Any?>)?.let { clip ->
        out["clip"] = LinkedHashMap(clip).also {
          it["x"] = number(clip, "x", 0.0) + dx
          it["y"] = number(clip, "y", 0.0) + dy
        }
      }
      return out
    }
    return null
  }

  private fun number(map: Map<String, Any?>, key: String, default: Double): Double = (map[key] as? Number)?.toDouble() ?: default

  @Test
  fun theTracesBuildAndExportOnlyWhenAsked() {
    val dir = GlassTraceOutput.requestedDir(System.getenv(Variable))
    println("tracce di F3.8a: $Variable = ${System.getenv(Variable)} -> ${dir ?: "niente da scrivere"}")
    assertEquals("nomi unici", traces.size, traces.values.map { it.name }.distinct().size)
    for (trace in traces.values) {
      assertTrue(trace.name, trace.encode().isNotEmpty())
      assertTrue(trace.name, trace.frames.all { it.elements.isNotEmpty() })
      dir?.let { trace.write(File(File(it, trace.name), "trace.json")) }
    }
  }

  @Test
  fun theTextOfABornGlassIsNeverWrittenOutsideItsGlass() {
    // S3: da 8% «Fatto» era scritto sulla foto senza vetro. Ora non c'è finché il vetro non l'ha coperto, e quando c'è
    // è ritagliato sulla lastra in volo, che lo contiene tutto.
    for (name in listOf("uno-due-chiaro", "uno-due-scuro")) {
      val trace = traces.getValue(name)
      var firstSeen = Double.NaN
      for (frame in trace.frames) {
        val text = layer(frame, "c-fatto") ?: continue
        if (firstSeen.isNaN()) firstSeen = frame.tMs
        val clip = text["clip"]
        if (clip != null) {
          @Suppress("UNCHECKED_CAST")
          val box = clip as Map<String, Any?>
          // Il testo sta nel mezzo del suo posto: se la lastra copre il centro del posto per un terzo della larghezza
          // da ogni parte, la scritta è dentro.
          val x = number(text, "x", 0.0)
          val w = number(text, "w", 0.0)
          val left = number(box, "x", 0.0)
          val right = left + number(box, "w", 0.0)
          assertTrue("$name t=${frame.tMs}: la lastra ($left..$right) copre la scritta", left <= x + w / 3 && right >= x + 2 * w / 3)
        }
      }
      // Riposo a vista a 300 ms: il testo compare dopo metà viaggio, sfocato, ed è nitido prima della fine.
      assertTrue("$name: «Fatto» compare a $firstSeen ms", firstSeen in 150.0..185.0)
      val early = layer(trace.frames.first { it.tMs >= 180.0 }, "c-fatto")!!
      assertTrue("a 180 ms è sfocato", number(early, "blurDp", 0.0) > 1.0)
      assertTrue("e traslucido", number(early, "alpha", 1.0) < 0.7)
      val late = layer(trace.frames.first { it.tMs >= 300.0 }, "c-fatto")!!
      assertEquals("a 300 ms è nitido", 0.0, number(late, "blurDp", 0.0), 0.0)
      assertEquals(1.0, number(late, "alpha", 1.0), 0.0)
      // «Modifica» resta: mai sfocata, mai traslucida.
      for (frame in trace.frames) {
        val icon = layer(frame, "c-modifica")
        assertNotNull("$name t=${frame.tMs}: «Modifica» c'è sempre", icon)
        assertNull(icon!!["blurDp"])
        assertEquals(1.0, number(icon, "alpha", 1.0), 0.0)
      }
    }
  }

  @Test
  fun aLabelledButtonBecomesThreeLikeSelectBecomesDone() {
    // La sequenza della clip di Apple (R4 B2 (d)): il contenuto vecchio è già sfocato all'8% del viaggio e sparito nel
    // vetro entro un terzo; quello nuovo non c'è prima di metà viaggio, arriva sfocato dentro il vetro ed è nitido a
    // quattro quinti. Mai una scritta nitida fuori dal vetro o prima del vetro.
    for (name in listOf("seleziona-fatto-chiaro", "seleziona-fatto-scuro")) {
      val trace = traces.getValue(name)
      val old = layer(trace.frames.first { it.tMs >= 24.0 }, "c-seleziona")
      assertNotNull("$name: «Seleziona» c'è ancora all'8%", old)
      assertTrue("$name: ed è già sfocata", number(old!!, "blurDp", 0.0) > 0.5)
      val gone = trace.frames.first { it.tMs >= 0.0 && layer(it, "c-seleziona") == null }
      assertTrue("$name: «Seleziona» è sparita a ${gone.tMs} ms", gone.tMs in 55.0..100.0)
      for (frame in trace.frames.filter { it.tMs > 0.0 && it.tMs < gone.tMs }) {
        assertNotNull("$name t=${frame.tMs}: ritagliata sul vetro che se ne va", layer(frame, "c-seleziona")!!["clip"])
      }
      assertNull("$name: a riposo non resta niente del vecchio", layer(trace.frames.last(), "c-seleziona"))
      for (key in listOf("c-cerchio", "c-triangolo", "c-fatto")) {
        val first = trace.frames.first { it.tMs >= 0.0 && layer(it, key) != null }
        assertTrue("$name: $key compare a ${first.tMs} ms", first.tMs in 150.0..185.0)
        val born = layer(first, key)!!
        assertTrue("$name: $key arriva sfocato", number(born, "blurDp", 0.0) > 1.0)
        assertNotNull("$name: $key arriva dentro il vetro", born["clip"])
        val sharp = layer(trace.frames.first { it.tMs >= 240.0 }, key)!!
        assertEquals("$name: $key è nitido a 240 ms", 0.0, number(sharp, "blurDp", 0.0), 0.0)
        assertEquals("$name: $key a riposo", 1.0, number(layer(trace.frames.last(), key)!!, "alpha", 1.0), 0.0)
      }
    }
  }

  @Test
  fun theTextOfAGlassThatLeavesBlursAwayWhereItWas() {
    val trace = traces.getValue("due-uno-chiaro")
    val before = layer(trace.frames.first(), "c-fatto")!!
    val x0 = number(before, "x", 0.0)
    val gone = trace.frames.first { it.tMs >= 0.0 && layer(it, "c-fatto") == null }
    assertTrue("«Fatto» è sparito a ${gone.tMs} ms", gone.tMs in 55.0..100.0)
    for (frame in trace.frames.filter { it.tMs > 0.0 && it.tMs < gone.tMs }) {
      val text = layer(frame, "c-fatto")!!
      assertEquals("resta dov'era", x0, number(text, "x", 0.0), 0.01)
      assertNotNull("ritagliato dalla lastra che se ne va", text["clip"])
      if (frame.tMs >= 24.0) assertTrue("già sfocato a ${frame.tMs} ms", number(text, "blurDp", 0.0) > 0.5)
    }
    assertNull("a riposo non resta niente", layer(trace.frames.last(), "c-fatto"))
  }

  @Test
  fun anInterruptedBirthNeverMakesTheTextJump() {
    val trace = traces.getValue("uno-due-interrotto")
    var previous: Double? = null
    for (frame in trace.frames) {
      val alpha = layer(frame, "c-fatto")?.let { number(it, "alpha", 1.0) } ?: 0.0
      previous?.let { before ->
        assertTrue("t=${frame.tMs}: l'opacità di «Fatto» passa da $before a $alpha in un fotogramma", kotlin.math.abs(alpha - before) < 0.3)
      }
      previous = alpha
    }
    assertEquals("alla fine «Fatto» c'è, pieno", 1.0, previous!!, 0.0)
  }

  @Test
  fun aLabelThatStaysTravelsSharpInsideItsCapsule() {
    val trace = traces.getValue("capsule-fuse")
    val start = number(layer(trace.frames.first(), "c-video")!!, "x", 0.0)
    val end = number(layer(trace.frames.last(), "c-video")!!, "x", 0.0)
    assertTrue(start - end > 90.0)
    var previous = start
    for (frame in trace.frames) {
      val text = layer(frame, "c-video")!!
      assertNull("t=${frame.tMs}: nitido", text["blurDp"])
      assertEquals(1.0, number(text, "alpha", 1.0), 0.0)
      val x = number(text, "x", 0.0)
      // Si sposta in continuo (una molla a 120 Hz non fa passi più lunghi di così), non salta al posto d'arrivo.
      assertTrue("t=${frame.tMs}: da $previous a $x", kotlin.math.abs(x - previous) < 12.0)
      previous = x
    }
    assertEquals(end, previous, 0.01)
  }

  @Test
  fun stillNeighboursKeepTheirRestGlassWhileAThirdAppears() {
    // «cerca» e «preferiti» mentre «play» compare (S3): al primo fotogramma del palco si univano con un ponte pieno.
    val trace = traces.getValue("comparsa")
    for (frame in trace.frames) {
      val rest = frame.elements.filter { (it["name"] as String).startsWith("riposo-") }
      assertTrue("t=${frame.tMs}: i due fermi hanno il loro vetro di riposo", rest.any { (it["name"] as String).contains("cerca") })
      val stage = frame.elements.filter { (it["name"] as String).startsWith("palco-") }
      assertTrue("t=${frame.tMs}: sul palco c'è al più chi compare", stage.size <= 1)
      stage.forEach { element ->
        assertTrue("t=${frame.tMs}: i due fermi non stanno sul palco", layers(element).none { it["name"] == "c-cerca" || it["name"] == "c-preferiti" })
      }
      assertNotNull("t=${frame.tMs}: «cerca» è sempre al suo posto, nitida", layer(frame, "c-cerca"))
      assertNull(layer(frame, "c-cerca")!!["blurDp"])
    }
    // Il simbolo: non c'è prima di un quarto della comparsa, poi si condensa senza fermarsi, ed è pieno a 430 ms.
    assertNull(layer(trace.frames.first { it.tMs >= 100.0 }, "c-play"))
    var scale = Double.MAX_VALUE
    for (frame in trace.frames.filter { it.tMs in 116.0..300.0 }) {
      val symbol = layer(frame, "c-play")!!
      val now = number(symbol, "scale", 1.0)
      assertTrue("t=${frame.tMs}: la scala scende sempre ($scale -> $now)", now < scale)
      scale = now
    }
    val full = layer(trace.frames.first { it.tMs >= 430.0 }, "c-play")!!
    assertEquals(1.0, number(full, "scale", 1.0), 0.0)
    assertNull(full["blurDp"])
  }

  @Test
  fun joiningAndSeparatingAreJourneysOfTheNeck() {
    val join = traces.getValue("unisci")
    val necks = join.frames.filter { it.tMs >= 0.0 }.mapNotNull { frame ->
      frame.elements.firstOrNull { (it["name"] as String).startsWith("palco-") }?.let { frame.tMs to (it["_collo"] as Double) }
    }
    assertTrue("«Unisci» dura un viaggio: ${necks.size} fotogrammi sul palco", necks.size >= 30)
    assertEquals("parte dai pezzi separati", -1.0, necks.first().second, 0.0)
    assertEquals("finisce col collo pieno", 1.0, necks.last().second, 0.0)
    assertTrue("il collo cresce", necks.zipWithNext().all { (a, b) -> b.second >= a.second - 1e-6 })
    assertFalse("non è pieno al secondo fotogramma", necks[1].second > 0.0)
    val split = traces.getValue("separa")
    val falling = split.frames.filter { it.tMs >= 0.0 }.mapNotNull { frame ->
      frame.elements.firstOrNull { (it["name"] as String).startsWith("palco-") }?.let { it["_collo"] as Double }
    }
    assertTrue(falling.size >= 30)
    assertEquals("parte dal collo pieno", 1.0, falling.first(), 0.0)
    assertTrue("si rompe una volta", falling.zipWithNext().count { (a, b) -> a > 0.0 && b <= 0.0 } == 1)
    assertTrue(falling.last() < -0.9)
  }

  companion object {
    const val Variable = "GLASS_F38A_DIR"
  }
}
