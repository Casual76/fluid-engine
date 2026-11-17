package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.glass.field.trace.F38aTraces
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Prove del pacchetto lente: riusa l'esportatore del piano senza cambiare le sue tracce condivise. Le copie vanno
 * soltanto nella cartella esplicita della sessione; senza variabile d'ambiente il test resta una verifica pura.
 */
class FluidGlassLensProofTest {
  @Test fun exportAssignedContentAndHaloScenesOnlyWhenRequested() {
    val requested = System.getenv("GLASS_LENS_PROOF_DIR") ?: return
    val directory = File(requested)
    require(directory.isAbsolute && directory.path.replace('\\', '/').contains("/sessioni/f3-corr-lente/"))
    val selected = F38aTraces.scenes().filter { it.name in listOf("comparsa", "scomparsa", "uno-due-chiaro", "uno-due-scuro") } +
      F38aTraces.appleScenes().filter { it.name == "nascita-apple" } +
      F38aTraces.f310Scenes().filter { it.name in listOf("seleziona-chiaro-meta", "seleziona-chiaro-dito", "seleziona-scuro-meta") }
    assertEquals(8, selected.size)
    selected.forEach { scene ->
      F38aTraces.build(scene).write(File(directory, "${scene.name}/trace.json"))
      // Confronto isolato dell'alone: stessa geometria e stesse curve del contenuto, solo il valore prima/dopo.
      if (scene.name.startsWith("seleziona-")) {
        val before = F38aTraces.Scene(
          scene.name + "-alone-prima", scene.note, scene.widthDp, scene.heightDp, scene.dark,
          scene.initial, scene.steps, scene.labels, scene.lastFrame, scene.density, scene.firstFrame,
          scene.spacingDp, scene.background, scene.apple, scene.journey.copy(glow = 0.5f), scene.touch, scene.phase,
        )
        F38aTraces.build(before).write(File(directory, "${before.name}/trace.json"))
      }
      if (scene.name.startsWith("uno-due-")) {
        for (variant in listOf("alone-prima", "dito")) {
          val comparison = F38aTraces.Scene(
            scene.name + "-" + variant, scene.note, scene.widthDp, scene.heightDp, scene.dark,
            scene.initial, scene.steps, scene.labels, scene.lastFrame, scene.density, scene.firstFrame,
            scene.spacingDp, scene.background, scene.apple,
            if (variant == "alone-prima") scene.journey.copy(glow = 0.5f) else scene.journey,
            if (variant == "dito") F38aTraces.Touch("modifica", 1f) else null, scene.phase,
          )
          F38aTraces.build(comparison).write(File(directory, "${comparison.name}/trace.json"))
        }
      }
    }
  }
}
