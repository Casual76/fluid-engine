package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tracce di F5A.3 per il banco del movimento (`tools/glassbench/traces/f5a-3/`): il sottomenu che si apre dentro il
 * menu e torna indietro, e l'apertura verso l'alto con l'ordine `Priority` accanto a quello `Fixed`. Si scrivono con
 * `GLASS_TRACE_DIR`; qui si controlla cio' che deve valere in ogni caso.
 */
class F5a3MenuStructureExportTest {

  @Test
  fun theSubmenuGrowsInsideTheMenuFromTheSameEdge() {
    for ((name, opening) in listOf("sottomenu-apre" to true, "sottomenu-torna" to false)) {
      val built = F5a3MenuStructureTraces.build(name, opening)
      val trace = built.trace
      // Il vetro resta dov'era: stesso bordo alto (il menu si apriva verso il basso), stesso centro orizzontale.
      assertEquals("$name: bordo alto", built.from.top, built.to.top, 0.01f)
      assertEquals("$name: centro", built.from.center.x, built.to.center.x, 0.01f)
      assertTrue("$name: il sottomenu e' piu' alto del menu", if (opening) built.to.height > built.from.height else built.to.height < built.from.height)
      assertTrue("$name: la goccia ha integrato", built.integratedFrames > 0)
      // Primo e ultimo fotogramma: un livello solo, nitido, opaco, intero.
      for (frame in listOf(trace.frames.first(), trace.frames.last())) {
        val layers = layers(frame)
        assertEquals("$name a ${frame.tMs} ms: uno strato", listOf("voci"), layers.map { it["name"] })
        assertEquals(1f, layers[0]["alpha"] as Float, 0f)
        assertTrue("$name: niente sfocatura a riposo", layers[0]["blurDp"] == null)
      }
      // Durante il cambio: i due livelli si incrociano e il vetro non resta mai vuoto (R7: somma ≈0,7 all'incrocio).
      val change = trace.frames.filter { it.tMs >= 0.0 && it.tMs < GlassContentDefaults.DurationMs * GlassContentDefaults.EnterEnd }
      for (frame in change) {
        val sum = layers(frame).sumOf { (it["alpha"] as Float).toDouble() }
        assertTrue("$name a ${frame.tMs} ms: opacita' complessiva $sum", sum >= 0.5)
      }
      assertTrue("$name: sfocate in mezzo", change.any { f -> layers(f).any { (it["blurDp"] as? Double ?: 0.0) > 1.0 } })
      assertTrue("$name: i due livelli insieme", change.any { layers(it).size == 2 })
      // Chi se ne va e' sparito entro 0,66 T.
      val exitEnd = GlassContentDefaults.DurationMs * GlassContentDefaults.ExitEnd
      assertTrue(trace.frames.filter { it.tMs >= exitEnd }.all { f -> layers(f).none { it["name"] == "uscenti" } })
      assertTrue("$name: serializzabile", trace.encode().isNotEmpty())
      GlassTraceOutput.writeIfRequested(trace)
    }
  }

  @Test
  fun priorityPutsTheFirstActionNextToTheFingerWhenOpeningUpward() {
    for ((name, reversed) in listOf("priority-su" to true, "fixed-su" to false)) {
      val scene = F5a3MenuStructureTraces.orderedScene(name, reversed)
      val labels = scene.items.map { it.label }
      if (reversed) {
        assertEquals(listOf("Tracking", "Wishlist", "Orders", "Recents"), labels)
        assertEquals(listOf(false, true, false, false), scene.items.map { it.startsSection })
      } else {
        assertEquals(listOf("Recents", "Orders", "Wishlist", "Tracking"), labels)
      }
      val built = buildMenuTrace(
        scene, dress = TraceDress.Liquid, name = name, phase = F5a3MenuStructureTraces.Phase, unified = true,
        variant = MenuTraceVariant(pointOfLight = true),
      )
      // Il tasto sta in basso: il menu cresce verso l'alto, e il pannello finisce sopra l'ancora.
      assertTrue("$name: verso l'alto", built.panelPx.center.y < built.pressedPx.center.y)
      assertTrue("$name: serializzabile", built.trace.encode().isNotEmpty())
      GlassTraceOutput.writeIfRequested(built.trace)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun layers(frame: GlassTraceFrame): List<Map<String, Any?>> {
    val menu = frame.elements.first { it["name"] == "menu" }
    return (menu["content"] as List<*>).filterIsInstance<Map<String, Any?>>()
  }
}
