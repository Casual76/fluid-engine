package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidModalSourceMotion
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidTitleSelectorDefaults
import dev.antigravity.fluidengine.ui.fluid.toolbar.fluidTitleSelectorAnchor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6B.13, la traccia `titolo-pannello` del banco del movimento (piano F6B, «diviso, titolo»): il titolo «Firenze ▾» che
 * apre il pannello delle località come un **popover dal titolo** (F6B.11 sul tablet, F5B.1 per il viaggio), e il congedo
 * che rientra nel disco. È il costruttore del popover di [F5b1ModalTraceTest] con l'ancora del titolo selettore
 * (`fluidTitleSelectorAnchor`: il quadrato di 44 dp attorno al disco, come in `rememberFluidTitleSelector`), sulla tela di
 * F5B.1 (360 × 520 dp, densità 1). Il costruttore controlla in ogni fotogramma che la faccia della sorgente e il contenuto
 * del pannello non siano mai leggibili insieme.
 *
 * Il ramo del telefono (sotto i 600 dp il pannello è un foglio che nasce dal titolo, lo zoom di F5B.3) non è qui.
 *
 * Si scrive sempre in `build/reports/f6b-13/tracce/<nome>/trace.json` e, con `GLASS_F6B13_DIR`, in
 * `<dir>/f6b-13/<nome>/trace.json`.
 */
class F6b13TitlePanelTraceTest {
  private val out = File("build/reports/f6b-13/tracce")
  private val phaseDir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F6B13_DIR"))

  /** «Firenze» agganciato al centro della barra (21 dp di carattere, la misura di `titolo-selettore`) col suo disco. */
  private fun anchor(): Rect {
    val text = 7 * 21f * 0.52f
    val reserve = 21f * FluidTitleSelectorDefaults.Reserve
    val left = (360f - text - reserve) / 2f
    val disc = Offset(left + text + 21f * FluidTitleSelectorDefaults.Gap + 21f * FluidTitleSelectorDefaults.Disc / 2f, 32f + 23f)
    return fluidTitleSelectorAnchor(disc, 21f * FluidTitleSelectorDefaults.Disc, 44f)
  }

  private fun write(trace: GlassTrace) {
    trace.write(File(File(out, trace.name), "trace.json"))
    phaseDir?.let { trace.write(GlassTraceOutput.fileFor(it, trace)) }
  }

  /** Il simbolo della sorgente nei fotogrammi: dai tre puntini al disco con la freccia del titolo selettore. */
  private fun disc(value: Any?): Any? = when (value) {
    "fluid:more_horiz|dots" -> "fluid:expand_more|circle"
    is Map<*, *> -> value.entries.associate { (k, v) -> k to disc(v) }
    is List<*> -> value.map { disc(it) }
    else -> value
  }

  @Suppress("UNCHECKED_CAST")
  @Test fun `titolo-pannello, le localita nascono dal titolo e ci rientrano`() {
    val builder = F5b1ModalTraceTest()
    val anchor = anchor()
    // Il pannello delle località sotto la barra, centrato sul titolo: la lista della scena `titolo-pannello`.
    val panel = Rect(40f, 92f, 320f, 360f)
    val scene = F5b1ModalTraceTest.Scene("titolo-pannello", FluidModalSourceMotion.Popover, anchor, panel,
      listOf("Le tue località", "Firenze", "Milano", "Roma", "Bologna"))
    val note = "F6B.13: il titolo «Firenze ▾» apre il pannello delle località come popover dal titolo (F6B.11, tablet). " +
      "L'ancora è il quadrato di 44 dp attorno al disco (fluidTitleSelectorAnchor); il viaggio è il popover dalla sorgente " +
      "di F5B.1. Nel banco la sorgente è il disco con la freccia in un tondo di vetro; sul dispositivo il disco non ha vetro."
    for (closing in listOf(false, true)) {
      val built = builder.trace(scene, closing)
      val name = if (closing) "titolo-pannello-congedo" else "titolo-pannello"
      // La faccia della sorgente: il disco del titolo con la freccia, non i tre puntini del popover di F5B.1.
      val frames = built.frames.map { frame -> GlassTraceFrame(frame.tMs, frame.elements.map { disc(it) as Map<String, Any?> }) }
      val trace = GlassTrace(name, "f6b-13", built.density, built.theme, built.widthDp, built.heightDp, frames,
        built.fps, built.head + ("_" to note))
      assertTrue(trace.encode().isNotEmpty())
      assertEquals(37, trace.frames.size)
      write(trace)
    }
    assertTrue("l'ancora sta sul disco del titolo", anchor.width >= 44f - 1e-3f && anchor.center.y in 40f..70f)
    assertTrue("il disco al posto dei tre puntini", disc(mapOf("icon" to "fluid:more_horiz|dots")) == mapOf("icon" to "fluid:expand_more|circle"))
  }
}
