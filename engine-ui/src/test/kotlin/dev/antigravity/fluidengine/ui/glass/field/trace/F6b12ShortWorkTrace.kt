package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionRing
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionRunner
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.fluidActionFace
import dev.antigravity.fluidengine.ui.fluid.action.label
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La traccia `tasto-lavoro-breve` del banco del movimento (piano «iOS in Android», F6B, «Il banco del movimento»; F6B.12):
 * un «Salva» il cui lavoro dura 180 ms, sotto la soglia di 300 ms. È l'unica delle sette tracce dei tasti con esito che
 * i pacchetti F6B.3a e F6B.3b non avevano scritto come curva (`F6b3GlyphTraces` ne scrive sei).
 *
 * Gli stati vengono dall'**esecutore vero** ([FluidActionRunner]) sull'orologio virtuale delle coroutine. Il disegno,
 * dalla correzione dopo le riprese (scelta di Alessio del 22 novembre 2026): senza anello da chiudere la spunta entra con
 * lo scambio sfocato di R7 mentre l'icona di Salva esce, sovrapposte (`SaveGlyph`); prima l'icona usciva in 67 ms e la
 * spunta cominciava a disegnarsi solo dopo, con ≈32 ms di vetro vuoto. Origine t = 0: il tocco. Un campione ogni 16 ms,
 * come le curve di F6B.3b.
 *
 * Che cosa prova, oltre a scrivere la curva: in nessun campione c'è l'anello del lavoro (il lavoro breve mostra solo
 * l'esito, R8); l'icona di riposo resta finché il lavoro non finisce; **in ogni campione dello scambio c'è una faccia**
 * (la somma delle opacità di chi esce e di chi entra non scende sotto 0,5: R7 la porta a ≈0,7 all'incrocio); il tasto
 * torna a riposo alla fine della tenuta del preset. Le immagini della finestra sono di `FluidSaveShortWorkFramesTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class F6b12ShortWorkTrace {
  @Test fun `esporta il lavoro breve con l'esecutore vero`() = runTest {
    val preset = FluidActionPreset.Save
    val runner = FluidActionRunner(this, preset, clock = { testScheduler.currentTime })
    val workMs = 180L
    val endMs = 2_000L
    val stepMs = 16L
    val t = GlassContentDefaults.DurationMs
    val rows = ArrayList<String>()
    var doneAt = -1L
    var ringSeen = false
    var weakest = Float.MAX_VALUE
    val labels = LinkedHashSet<String>()

    assertTrue(runner.run { delay(workMs) })
    runCurrent()
    var now = 0L
    while (now <= endMs) {
      val state = runner.state
      val face = fluidActionFace(preset, state)
      if (face.ring != FluidActionRing.None) ringSeen = true
      if (state == FluidActionState.Succeeded && doneAt < 0L) doneAt = now
      labels += state.label
      val checked = state == FluidActionState.Succeeded
      // Lo scambio sfocato di R7 dall'icona di Salva alla spunta, dal campione dell'esito.
      val u = if (doneAt < 0L || !checked) -1f else (now - doneAt) / t
      val exitAlpha = if (u < 0f) 1f else GlassContentCurves.exitAlpha.at(u)
      val exitSigma = if (u < 0f) 0f else GlassContentDefaults.PeakBlur * GlassContentCurves.blurRise(u)
      val enterAlpha = if (u < 0f) 0f else GlassContentCurves.enterAlpha.at(u)
      val enterSigma = if (u < 0f) 0f else GlassContentDefaults.PeakBlur * GlassContentCurves.softBlur(u)
      if (checked) weakest = minOf(weakest, exitAlpha + enterAlpha)
      val values = listOf(if (face.ring == FluidActionRing.None) 0f else 1f, exitAlpha, exitSigma, enterAlpha, enterSigma)
      assertTrue("valori finiti a $now ms", values.all(Float::isFinite))
      rows += "$now,${state.label},${values.joinToString(",")}"
      advanceTimeBy(stepMs)
      runCurrent()
      now += stepMs
    }

    assertTrue("l'anello non si vede mai nel lavoro breve", !ringSeen)
    // L'esito arriva al primo campione dopo la fine del lavoro, e prima della soglia del lavoro.
    assertTrue("esito a $doneAt ms", doneAt in workMs..workMs + stepMs)
    assertEquals(listOf("lavoro sotto soglia", "riuscito", "riposo"), labels.toList())
    assertTrue("mai un campione senza una faccia: la somma più bassa è $weakest", weakest >= 0.5f)
    assertEquals("a riposo dopo la tenuta del preset", FluidActionState.Idle, runner.state)

    val dir = File("build/reports/f6b-12/curve").apply { mkdirs() }
    File(dir, "tasto-lavoro-breve.csv").bufferedWriter().use { out ->
      out.appendLine("ms,stato,anello,alpha_icona_esce,sigma_icona_esce_su_H,alpha_spunta_entra,sigma_spunta_entra_su_H")
      rows.forEach(out::appendLine)
    }
  }
}
