package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionGlyphProfile
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionRing
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionRunner
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.fluidActionFace
import dev.antigravity.fluidengine.ui.fluid.action.label
import dev.antigravity.fluidengine.ui.fluid.glassContentSmoothstep
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
 * Gli stati vengono dall'**esecutore vero** ([FluidActionRunner]) sull'orologio virtuale delle coroutine; le curve del
 * disegno dalle funzioni del glifo di Salva ([FluidActionGlyphProfile]) e dalla sfocatura di R7 che fa uscire l'icona di
 * riposo, come le disegna `SaveSequence`. Origine t = 0: il tocco. Un campione ogni 16 ms, come le curve di F6B.3b.
 *
 * Che cosa prova, oltre a scrivere la curva: in nessun campione c'è l'anello del lavoro (il lavoro breve mostra solo
 * l'esito, R8), l'icona di riposo resta finché il lavoro non finisce, la spunta si disegna dopo, e il tasto torna a riposo
 * alla fine della tenuta del preset. Le immagini della finestra sono di `FluidActionFramesTest` (sample, `salva-breve`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class F6b12ShortWorkTrace {
  @Test fun `esporta il lavoro breve con l'esecutore vero`() = runTest {
    val preset = FluidActionPreset.Save
    val runner = FluidActionRunner(this, preset, clock = { testScheduler.currentTime })
    val workMs = 180L
    val endMs = 2_000L
    val stepMs = 16L
    val profile = FluidActionGlyphProfile
    val rows = ArrayList<String>()
    var doneAt = -1L
    var ringSeen = false
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
      // Il glifo di Salva: l'icona di riposo esce sfocandosi nei primi 67 ms dopo l'esito, poi la spunta si disegna.
      val since = if (doneAt < 0L) -1f else (now - doneAt).toFloat()
      val checked = state == FluidActionState.Succeeded
      val u = if (since < 0f || !checked) 0f else (since / profile.SaveDrawStartMs).coerceIn(0f, 1f)
      val baseAlpha = if (checked) 1f - glassContentSmoothstep(0f, 1f, u) else 1f
      val baseSigma = if (checked) GlassContentDefaults.PeakBlur * GlassContentCurves.blurRise(u) else 0f
      val mask = if (checked) profile.saveReveal(since) else 0f
      val values = listOf(if (face.ring == FluidActionRing.None) 0f else 1f, baseAlpha, baseSigma, mask)
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
    assertEquals("a riposo dopo la tenuta del preset", FluidActionState.Idle, runner.state)

    val dir = File("build/reports/f6b-12/curve").apply { mkdirs() }
    File(dir, "tasto-lavoro-breve.csv").bufferedWriter().use { out ->
      out.appendLine("ms,stato,anello,alpha_icona_riposo,sigma_icona_riposo_su_H,maschera_spunta")
      rows.forEach(out::appendLine)
    }
  }
}
