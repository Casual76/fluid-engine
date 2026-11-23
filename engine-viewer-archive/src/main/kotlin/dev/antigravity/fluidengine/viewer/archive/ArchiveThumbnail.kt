package dev.antigravity.fluidengine.viewer.archive

import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FolderZip
import dev.antigravity.fluidengine.viewer.FluidThumbnailLook
import dev.antigravity.fluidengine.viewer.FluidThumbnailPainter
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import java.util.Locale

/**
 * F6C.17 · La miniatura di un archivio: l'icona d'archivio nello stile della pagina «D · Mix», cioè la tessera con la
 * tinta leggera della pagina (l'accento in alto, il terziario in basso), la cartella con la cerniera nell'accento e,
 * sotto, l'estensione in una capsula. Mai una tessera grigia. Non legge l'archivio (libarchive resta per la pagina):
 * costa quanto un disegno, anche su un dispositivo dove la libreria nativa non si carica.
 */
internal suspend fun archiveThumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap {
  val leaf = request.source.name.substringAfterLast('/')
  val lower = leaf.lowercase(Locale.ROOT)
  val label = DoubleExtensions.firstOrNull { lower.endsWith(".$it") } ?: leaf.substringAfterLast('.', request.type.id)
  return FluidThumbnailPainter.kindSymbol(FluidIcons.FolderZip, label, sizePx, FluidThumbnailLook.current(), placeholder = true)
}

/** Le estensioni doppie che si dicono intere: «TAR.GZ» dice più di «GZ». */
private val DoubleExtensions = listOf("tar.gz", "tar.bz2", "tar.xz", "tar.zst")
