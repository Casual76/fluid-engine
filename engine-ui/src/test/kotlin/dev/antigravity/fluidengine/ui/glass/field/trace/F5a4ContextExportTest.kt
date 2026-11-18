package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidContextScrimLevel
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchorGap
import dev.antigravity.fluidengine.ui.fluid.fluidLiftedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le tracce del menu contestuale di F5A.4 ([F5a4ContextTraces]), scritte dal codice dell'engine.
 *
 * Prima i fatti che le rendono credibili: al click lungo la card e' la riga come il dito l'ha gonfiata (nessun gradino),
 * alla fine del ritorno e' la riga a riposo (che ricompare nella pagina nello stesso fotogramma), il pannello sta sotto la
 * card alla distanza di serie, lo scrim arriva e se ne va senza salti. Poi, **solo se** `GLASS_TRACE_DIR` c'e', si
 * scrivono (`tools/glassbench/traces`, fase `f5a-4`): l'apertura per i tre livelli dello sfondo, chiaro e scuro, e il
 * ritorno.
 */
class F5a4ContextExportTest {

  private fun assertRect(where: String, expected: Rect, actual: Rect, tolerance: Float) {
    assertEquals("$where: left", expected.left, actual.left, tolerance)
    assertEquals("$where: top", expected.top, actual.top, tolerance)
    assertEquals("$where: right", expected.right, actual.right, tolerance)
    assertEquals("$where: bottom", expected.bottom, actual.bottom, tolerance)
  }

  @Suppress("UNCHECKED_CAST")
  private fun scrimAlpha(frame: GlassTraceFrame): Float =
    (frame.elements.firstOrNull { it["name"] == "scrim" }?.get("alpha") as Number?)?.toFloat() ?: 0f

  @Test
  fun contextTracesAreContinuousAndWriteWhenAsked() {
    for (level in FluidContextScrimLevel.entries) {
      for (dark in listOf(false, true)) {
        val built = F5a4ContextTraces.build(level, dark)
        val where = "${level.name}${if (dark) " scuro" else ""}"

        // Il dito ha tenuto premuto 400 ms: la riga e' gonfiata (quasi) del tutto.
        assertTrue("$where: scala al click lungo ${built.anchorScaleAtLongPress}", built.anchorScaleAtLongPress > 1.015f)
        // Al click lungo la card e' la riga gonfiata, attorno al centro: nessun gradino (mezzo pixel, la goccia ferma).
        assertRect("$where: card al click lungo", fluidLiftedFrame(built.row, built.anchorScaleAtLongPress), built.cardAtLongPress, 0.5f)
        // Alla fine del ritorno la card e' la riga a riposo.
        assertRect("$where: card alla fine", built.row, built.cardAtEnd, 1f)
        // Il pannello sotto la card, alla distanza di serie.
        assertEquals("$where: distanza del pannello", FluidMenuAnchorGap.value * 3f, built.panel.top - built.lifted.bottom, 1f)

        // Lo scrim sale senza mai tornare indietro fino al riposo, e al ritorno scende senza mai risalire.
        val open = built.opens.frames.filter { it.tMs >= 0.0 }.map(::scrimAlpha)
        for (i in 1 until open.size) assertTrue("$where: scrim in apertura al fotogramma $i", open[i] >= open[i - 1] - 1e-4f)
        assertEquals("$where: scrim pieno a riposo", 1f, open.last(), 1e-3f)
        val close = built.closes.frames.filter { it.tMs >= 0.0 }.map(::scrimAlpha)
        for (i in 1 until close.size) assertTrue("$where: scrim al ritorno al fotogramma $i", close[i] <= close[i - 1] + 1e-4f)
        assertEquals("$where: scrim spento alla fine", 0f, close.last(), 1e-3f)

        // Le voci stanno dentro il vetro in ogni fotogramma, anche mentre la goccia si posa dopo l'arrivo (S9, f218:
        // «Elimina», il cestino e i capi dei separatori fuori dalla sagoma). Mezzo pixel e' l'antialias della maschera.
        println(
          "F5A.4 $where: inchiostro fuori dal vetro, apertura ${built.inkOutsideOpen} px (a ${built.inkWorstOpenMs} ms), " +
            "ritorno ${built.inkOutsideClose} px; con il ritaglio di prima ${built.inkOutsideBefore} px",
        )
        assertTrue("$where: le voci escono dal vetro in apertura di ${built.inkOutsideOpen} px a ${built.inkWorstOpenMs} ms", built.inkOutsideOpen <= 0.6f)
        assertTrue("$where: le voci escono dal vetro al ritorno di ${built.inkOutsideClose} px", built.inkOutsideClose <= 0.6f)

        GlassTraceOutput.writeIfRequested(built.opens)
        GlassTraceOutput.writeIfRequested(built.closes)
      }
    }
  }
}
