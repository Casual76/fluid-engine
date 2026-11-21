package dev.antigravity.fluidengine.ui.glass.field.trace

import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionGlyphProfile
import dev.antigravity.fluidengine.ui.fluid.action.FluidCheckMask
import dev.antigravity.fluidengine.ui.fluid.action.fluidActionShake
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionHold
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.fluidActionFace
import dev.antigravity.fluidengine.ui.fluid.action.fluidActionHoldMs
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Curve dei canali di disegno, allineate alle sei riprese Compose di F6b3GlyphFramesTest.
 * Origine t=0: cambio di stato. Le ampiezze di sfocatura sono sigma/altezza della sagoma.
 * Le immagini della finestra e i contatori del vetro sono prove distinte, nel modulo sample.
 */
class F6b3GlyphTraces {
  @Test fun `esporta le curve dei sei viaggi`() {
    val dir = File("build/reports/f6b-3b/curve").apply { mkdirs() }
    fun trace(name: String, columns: String, frames: Int = 32, values: (Float) -> List<Float>) {
      val samples = (0..frames).map { it * 16f }
      File(dir, "$name.csv").bufferedWriter().use { out ->
        out.appendLine("ms,$columns")
        samples.forEach { t ->
          val frame = values(t)
          assertTrue("valori finiti in $name a $t", frame.all(Float::isFinite))
          out.appendLine("${t.toInt()},${frame.joinToString(",")}")
        }
      }
    }
    val profile = FluidActionGlyphProfile
    for (name in listOf("tasto-invia-riuscito", "tasto-invia-fallito")) {
      trace(name, "sigma_uscita_su_H,sigma_entrata_su_H,alpha_uscita,alpha_entrata") { ms ->
        val u = ms / GlassContentDefaults.DurationMs
        listOf(GlassContentCurves.blurRise(u) * GlassContentDefaults.PeakBlur,
          GlassContentCurves.softBlur(u) * GlassContentDefaults.PeakBlur,
          GlassContentCurves.exitAlpha.at(u), GlassContentCurves.enterAlpha.at(u))
      }
    }
    trace("tasto-salva", "chiusura_anello,alpha_anello,maschera,punta_x,punta_y") { ms ->
      val p = profile.saveReveal(ms)
      val tip = FluidCheckMask.tip(p)
      listOf(profile.saveClosure(ms), profile.saveRingAlpha(ms), p, tip.x, tip.y)
    }
    trace("tasto-scarica-percentuale", "disco,maschera,punta_x,punta_y") { ms ->
      val p = profile.reveal(ms, true)
      val tip = FluidCheckMask.tip(p)
      listOf(profile.fill(ms, true), p, tip.x, tip.y)
    }
    trace("tasto-preferito-rifiutato", "scala_al_tocco,scossa_dopo_il_ritardo") { ms ->
      listOf(profile.heartScale(ms / profile.HeartMs),
        fluidActionShake(ms / dev.antigravity.fluidengine.ui.fluid.action.FluidActionProfile.ShakeMs))
    }
    val copy = FluidActionPreset.Copy
    val hold = fluidActionHoldMs(copy, FluidActionHold.Preset, FluidActionState.Succeeded)
    trace("tasto-copia", "spunta_durante_la_tenuta", frames = 70) { ms ->
      val state = if (ms < hold) FluidActionState.Succeeded else FluidActionState.Idle
      listOf(if (fluidActionFace(copy, state).icon == copy.success) 1f else 0f)
    }
  }
}
