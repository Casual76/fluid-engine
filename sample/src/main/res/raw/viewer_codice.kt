package dev.antigravity.fluidengine.sample.viewer

import androidx.compose.runtime.Composable
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles

/**
 * Un visore che apre una lista di file con engine-viewer.
 * Il telaio mostra, il modulo apre, l'app possiede.
 */
@Composable
fun Visore(files: List<FluidViewerFile>, onBack: () -> Unit) {
  val opened = rememberFluidViewerFiles(files, openWith = { apri(it) })
  FluidFileViewer(
    count = opened.count,
    content = opened::content,
    onBack = onBack,
  )
}

private fun apri(file: FluidViewerFile) {
  // "Apri con…" passa il file al sistema: qui non succede niente di pericoloso.
  val nome = file.name.substringBeforeLast('.')
  val pezzi = listOf(1, 2, 3).map { it * 2 }
  println("Apro $nome: ${pezzi.sum()} pezzi")
}
