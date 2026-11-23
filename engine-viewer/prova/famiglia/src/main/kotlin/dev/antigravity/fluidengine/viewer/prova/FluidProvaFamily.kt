package dev.antigravity.fluidengine.viewer.prova

import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidTextSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F6C.8 · La famiglia di prova dell'APK: il tipo «.prova» (un testo che comincia con `FLUIDPROVA`), aperto come testo
 * semplice. Si registra con `META-INF/services/dev.antigravity.fluidengine.viewer.FluidViewerFamily`, senza toccare il
 * risolutore di engine-viewer.
 */
class FluidProvaFamily : FluidViewerFamily {
  override val id: String = "prova"

  override val types: List<FluidViewerFamilyType> = listOf(
    FluidViewerFamilyType(
      id = "prova",
      label = "File di prova",
      extensions = setOf("prova"),
      mimeTypes = setOf("application/x-fluid-prova"),
      signature = FluidViewerSignature.ascii(Magic),
      kind = FluidFileKind.Text,
      preset = FluidViewerPreset.Text,
    ),
  )

  override suspend fun open(request: FluidViewerRequest): FluidViewerOpened? {
    val text = withContext(Dispatchers.IO) { request.source.open().use { it.readBytes().decodeToString() } }
    if (!text.startsWith(Magic)) return null
    val lines = text.removePrefix(Magic).trimStart('\r', '\n').lines()
    val source = object : FluidTextSource {
      override val lineCount: Int = lines.size
      override suspend fun readLines(start: Int, count: Int): List<String> = lines.drop(start).take(count)
    }
    return FluidViewerOpened(FluidViewerContent.Text(source), listOf(FluidViewerDetail("Righe", lines.size.toString())))
  }

  companion object {
    const val Magic: String = "FLUIDPROVA"
  }
}
