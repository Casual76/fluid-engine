package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDimDark
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDimLight
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * F5B, giro di correzione del foglio: le prove del velo della pagina.
 *
 *  - `foglio-soglia`: la salita di `foglio-a-tutta-altezza` (il dito di Apple, WWDC25 323) con `undimmedUpTo = Medium`.
 *    Il velo ora cresce con la strada fra il detent medio e la cima (`fluidSheetDim`); in F5B.4 (`f5b-4/foglio-soglia`)
 *    si accendeva intero nel primo fotogramma sopra la soglia.
 *  - `velo-<chiaro|scuro>-<meta|cima>-<oggi|nuovo>`: il foglio fermo a meta' altezza e in cima, col velo di prima (12% sul
 *    chiaro, 20% sullo scuro, il livello dei modali) e con quello nuovo dei fogli (23% e 31%, `FluidSheetDimLight`). Sono
 *    le scene della tavola per Alessio, accanto ad Apple (323 f12203 a meta', 284 f25440 in cima; Apple non ha un foglio
 *    sullo scuro nel materiale).
 *
 * Gli esportatori di F5B.2 e F5B.3 disegnano da qui lo scrim dei fogli di oggi ([todaySheetVeil]); quello di F5B.4 resta
 * col suo velo al 12% (`F5b4PresentationTraces.todaySheetScrim`), cioe' com'era in F5B.4.
 *
 * Si scrivono con `GLASS_F5BCORR_DIR` (di solito `tools/glassbench/traces/f5b-corr`), fase `f5b-corr`.
 */

/**
 * Lo scrim di un foglio di oggi (`FluidPresentationScrim`): un nero senza sfocatura su tutta la tela, al livello [level]
 * del velo intero per [presence] (la presenza dell'host per la frazione della soglia di `undimmedUpTo`). La presenza resta
 * scritta in `_presenza`, un commento per il banco, cosi' chi rifa' lo scrim a un altro livello (F5B.4) la ritrova.
 */
internal fun todaySheetVeil(widthDp: Float, heightDp: Float, level: Float, presence: Float): Map<String, Any?> {
  val p = presence.coerceIn(0f, 1f)
  return linkedMapOf(
    "kind" to "shape", "name" to "scrim", "_presenza" to round3(p),
    "x" to 0, "y" to 0, "w" to widthDp, "h" to heightDp, "radius" to 0,
    "color" to glassColor(Color.Black.copy(alpha = level * p)),
  )
}

internal object F5bCorrSheetTraces {
  const val Phase = "f5b-corr"

  private fun rephased(built: GlassTrace, name: String, note: String, extra: Map<String, Any?> = emptyMap()): GlassTrace {
    val head = LinkedHashMap(built.head)
    head["_"] = note
    head.remove("apple")
    head.putAll(extra)
    return GlassTrace(name, Phase, built.density, built.theme, built.widthDp, built.heightDp, built.frames, built.fps, head)
  }

  /** Le tracce del giro di correzione: la soglia, poi le otto scene ferme della tavola. */
  fun build(): List<GlassTrace> {
    val source = "F5B, giro di correzione del foglio, dal codice di oggi: il costruttore di F5B.2 (F5b2SheetTraceTest.kt)"
    val rise = F5b2SheetTraceTest().aTuttaAltezza(undimmedUpTo = FluidSheetDetent.Medium)
    @Suppress("UNCHECKED_CAST")
    val riseEvents = rise.head["events"] as List<Map<String, Any?>>
    val firstVeiled = rise.frames.first { f -> f.elements.any { it["name"] == "scrim" } }.tMs
    val threshold = rephased(
      rise, "foglio-soglia",
      "$source con undimmedUpTo = Medium: a meta' altezza la pagina resta attiva e chiara; sopra il velo dei fogli (23%) " +
        "cresce con la strada fino alla cima (fluidSheetDim), il tocco passa di colpo alla soglia. Apple non ha la scena: " +
        "UIKit (WWDC21 10063, 7:44) fa crescere il velo col dito.",
      linkedMapOf(
        "probes" to listOf(
          linkedMapOf<String, Any?>(
            "name" to "bordo-alto", "kind" to "value", "path" to "foglio.y", "unit" to "dp",
            "label" to "il bordo alto del foglio (dp dalla cima): il dito di Apple, poi la molla dei detent",
          ),
          linkedMapOf<String, Any?>(
            "name" to "luce-pagina", "kind" to "luma", "rect" to listOf(20, 8, 362, 40),
            "label" to "un tratto di pagina che il foglio non copre mai: scende del 23% quando il foglio arriva in cima",
          ),
        ),
        "events" to (riseEvents + linkedMapOf<String, Any?>("t" to firstVeiled, "kind" to "mark", "label" to "il velo comincia"))
          .sortedBy { (it["t"] as Number).toDouble() },
      ),
    )

    val stills = ArrayList<GlassTrace>()
    for (dark in listOf(false, true)) {
      val theme = if (dark) "scuro" else "chiaro"
      for ((where, detent) in listOf("meta" to FluidSheetDetent.Medium, "cima" to FluidSheetDetent.Large)) {
        for ((when_, level) in listOf("oggi" to GlassPaintedScrim.alpha(dark), "nuovo" to null)) {
          val name = "velo-$theme-$where-$when_"
          val sheets = F5b2SheetTraceTest().also {
            it.dark = dark
            it.dimLevel = level
          }
          val percent = Math.round((level ?: if (dark) FluidSheetDimDark else FluidSheetDimLight) * 100f)
          val what = "il foglio fermo ${if (where == "meta") "a meta' altezza" else "in cima"} sul $theme, col velo " +
            "${if (when_ == "oggi") "di prima" else "nuovo dei fogli"} al $percent%"
          stills += rephased(sheets.fermo(name, detent, what), name, "$source: $what. Una scena ferma, per la tavola.")
        }
      }
    }
    return listOf(threshold) + stills
  }
}

class F5bCorrSheetTraceTest {

  /** L'alfa del colore `#RRGGBBAA` di un elemento. */
  private fun alphaOf(element: Map<String, Any?>): Float = (element["color"] as String).takeLast(2).toInt(16) / 255f

  @Test
  fun `il velo della soglia cresce senza scatti e ha la misura di Apple`() {
    val traces = F5bCorrSheetTraces.build()
    val byName = traces.associateBy { it.name }
    assertEquals(9, traces.size)
    for (trace in traces) {
      assertEquals("${trace.name}: fase", F5bCorrSheetTraces.Phase, trace.phase)
      for (frame in trace.frames) for (e in frame.elements.filter { it["name"] == "scrim" }) {
        assertEquals("${trace.name} a ${frame.tMs}: il velo dei fogli non sfoca", "shape", e["kind"])
      }
      assertTrue("${trace.name}: serializzabile", trace.encode().isNotEmpty())
    }

    // La soglia: a meta' altezza nessun velo; in cima il velo intero dei fogli; nel mezzo, a ogni fotogramma, la pagina
    // chiara (242 livelli) scende di pochi livelli, non dei 30 di F5B.4. Il dito di Apple e' svelto (55 px del video in un
    // suo fotogramma): il passo piu' grande e' a meta' strada, dove la S e' piu' ripida.
    val threshold = requireNotNull(byName["foglio-soglia"])
    fun veil(frame: GlassTraceFrame) = frame.elements.firstOrNull { it["name"] == "scrim" }?.let(::alphaOf) ?: 0f
    assertTrue("a meta' altezza nessun velo", threshold.frames.filter { it.tMs <= 0.0 }.all { veil(it) == 0f })
    assertEquals("in cima il velo intero", FluidSheetDimLight, veil(threshold.frames.last()), 1f / 255f)
    val steps = threshold.frames.zipWithNext { a, b -> (veil(b) - veil(a)) * 242f }
    assertTrue("il velo non cala salendo", steps.all { it >= -0.5f })
    val firstStep = steps.first { it > 0f }
    assertTrue("il primo fotogramma sopra la soglia: $firstStep livelli", firstStep < 1f)
    assertTrue("il passo piu' grande: ${steps.max()} livelli", steps.max() < 8f)

    // Le scene ferme: i livelli di prima (quelli dei modali) e quelli nuovi dei fogli, sul chiaro e sullo scuro.
    for ((name, level) in mapOf(
      "velo-chiaro-meta-oggi" to 0.12f, "velo-chiaro-meta-nuovo" to FluidSheetDimLight,
      "velo-scuro-cima-oggi" to 0.20f, "velo-scuro-cima-nuovo" to FluidSheetDimDark,
    )) {
      val still = requireNotNull(byName[name])
      assertEquals("$name: una scena ferma", 1, still.frames.size)
      assertEquals("$name: velo", level, veil(still.frames.single()), 1f / 255f)
      assertEquals("$name: tema", if ("scuro" in name) "dark" else "light", still.theme)
    }

    GlassTraceOutput.requestedDir(System.getenv("GLASS_F5BCORR_DIR"))?.let { dir ->
      for (t in traces) t.write(File(dir, "${t.name}/trace.json"))
    }
  }
}
