package dev.antigravity.fluidengine.ui.glass.field.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La traccia del tocco col materiale nuovo ([LiquidTasto], giro di correzione di F1A): stessa corsa di `oggi-tasto`,
 * vestita dalla ricetta. Si controllano i fatti che la rendono credibile, poi, **solo se** `GLASS_TRACE_DIR` c'e', si
 * scrive (vedi [GlassTraceOutput]).
 */
class GlassLiquidTraceExportTest {

  @Test
  fun aPressedLiquidButtonGrowsGlowsAndComesBack() {
    val built = LiquidTasto.build()
    val trace = built.trace
    assertEquals("f1a", trace.phase)
    // A riposo prima e dopo: 44 dp, bagliore spento.
    assertEquals(44.0, built.widthsDp.first().toDouble(), 0.01)
    assertEquals(44.0, built.widthsDp.last().toDouble(), 0.05)
    // Premuto, il tasto cresce e il bagliore si accende.
    assertTrue(built.widthsDp.max() > 46f)
    val glows = trace.frames.map { frame ->
      @Suppress("UNCHECKED_CAST")
      val light = frame.elements.single()["light"] as List<Map<String, Any?>>
      @Suppress("UNCHECKED_CAST")
      ((light.first()["uniforms"] as Map<String, Any?>)["glow"] as Number).toDouble()
    }
    assertEquals(0.0, glows.first(), 0.0)
    assertTrue(glows.max() > 0.0)
    assertTrue(trace.encode().isNotEmpty())
    GlassTraceOutput.writeIfRequested(trace)?.let { println("traccia scritta: $it (${trace.frames.size} fotogrammi)") }
  }
}
