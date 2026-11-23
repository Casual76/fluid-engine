package dev.antigravity.fluidengine.viewer.prova

import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSignature
import java.util.concurrent.atomic.AtomicInteger

/**
 * F6C.8 · Il modulo di prova dei test JVM: dichiara il tipo «.prova» con
 * `src/test/resources/META-INF/services/dev.antigravity.fluidengine.viewer.FluidViewerFamily`, come farà un modulo di
 * famiglia vero, e il risolutore lo trova senza che engine-viewer ne sappia niente. Un file `.prova` è un testo che
 * comincia con `FLUIDPROVA`; si apre in una pagina `Custom` con il numero di righe nel pannello info. Un `.prova` che
 * non comincia così è «non questo» (`null`).
 */
class FluidProvaTestFamily : FluidViewerFamily {
  override val id: String = "prova"

  override val types: List<FluidViewerFamilyType> = listOf(
    FluidViewerFamilyType(
      id = "prova",
      label = "File di prova",
      extensions = setOf("prova"),
      mimeTypes = setOf("application/x-fluid-prova"),
      signature = FluidViewerSignature.ascii(Magic),
      kind = FluidFileKind.Text,
      preset = FluidViewerPreset.Custom,
    ),
  )

  override suspend fun open(request: FluidViewerRequest): FluidViewerOpened? {
    val text = request.source.open().use { it.readBytes().decodeToString() }
    if (!text.startsWith(Magic)) return null
    val lines = text.removePrefix(Magic).trim().lines()
    opened.incrementAndGet()
    return FluidViewerOpened(
      content = FluidViewerContent.Custom {},
      details = listOf(FluidViewerDetail("Righe", lines.size.toString())),
      onClose = { closed.incrementAndGet() },
    )
  }

  companion object {
    const val Magic: String = "FLUIDPROVA"
    val opened = AtomicInteger()
    val closed = AtomicInteger()
  }
}
