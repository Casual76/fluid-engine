package dev.antigravity.fluidengine.viewer.data

import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoAction
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoRow
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoSection
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerDocument

/**
 * F6C.13 · Il pannello info (D · Mix, `visori-2.md` §5.2) di un documento aperto dal registro: i [FluidViewerDetail]
 * della famiglia diventano un `FluidViewerInfo`. Le righe senza sezione sono i **fatti in capsule** (il loro valore),
 * le altre le **sezioni**, nell'ordine in cui arrivano; [actions] sono quelle dell'app («Apri con…», «Condividi»).
 *
 * Va chiamata in composizione: i dati di un CSV arrivano mentre il file si conta (la lista è uno stato), e il pannello
 * si aggiorna da solo.
 *
 * ```kotlin
 * FluidViewerInfoProvider({ i -> files.document(i)?.let { fluidDataViewerInfo(it, actions = azioni(i)) } }) {
 *   FluidFileViewer(count = files.count, content = files::content)
 * }
 * ```
 *
 * Non sa niente dei tipi della famiglia «data»: vale per i dettagli di ogni famiglia che segue la stessa regola.
 */
fun fluidDataViewerInfo(
  document: FluidViewerDocument,
  actions: List<FluidViewerInfoAction> = emptyList(),
  thumbnail: ImageBitmap? = null,
): FluidViewerInfo {
  val file = FluidFileInfo(document.file.name, document.file.mimeType, document.file.sizeBytes, document.type.kind)
  return fluidDataViewerInfo(file, document.details, document.type.label, actions, thumbnail)
}

/** Lo stesso, da un [FluidFileInfo] e da una lista di dettagli (per un'app che li tiene da sé). */
fun fluidDataViewerInfo(
  file: FluidFileInfo,
  details: List<FluidViewerDetail>,
  label: String? = null,
  actions: List<FluidViewerInfoAction> = emptyList(),
  thumbnail: ImageBitmap? = null,
): FluidViewerInfo {
  val facts = details.filter { it.section == null }.map { it.value }
  val sections = LinkedHashMap<String, MutableList<FluidViewerInfoRow>>()
  for (d in details) {
    val section = d.section ?: continue
    sections.getOrPut(section) { ArrayList() } += FluidViewerInfoRow(d.label, d.value)
  }
  val size = file.sizeBytes?.let { DataText.size(it) }
  return FluidViewerInfo(
    file = file,
    subtitle = listOfNotNull(label, size).joinToString(" · ").takeIf { it.isNotEmpty() },
    thumbnail = thumbnail,
    facts = facts,
    sections = sections.map { (title, rows) -> FluidViewerInfoSection(title, rows) },
    actions = actions,
  )
}
