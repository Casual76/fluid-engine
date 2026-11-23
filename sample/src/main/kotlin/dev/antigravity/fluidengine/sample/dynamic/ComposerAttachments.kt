package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAttachment
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAttachmentDefaults
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAttachmentStyle
import dev.antigravity.fluidengine.sample.viewer.VisoreFile
import dev.antigravity.fluidengine.sample.viewer.VisoriFiles
import dev.antigravity.fluidengine.viewer.FluidFileThumbnailImage
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.rememberFluidFileThumbnail

// F6B.16: gli allegati del composer con l'anteprima. Le scene «Messaggio» e «assistente» allegano file veri della galleria
// «Visori» (una foto e il PDF del quaderno), copiati nella cartella dell'app come fa un'app vera, e l'anteprima è la
// miniatura di `FluidFileThumbnails` (F6C.17): la foto ritagliata, la prima pagina del PDF.

/** I file che le scene del composer allegano: due foto e un PDF della galleria «Visori». */
internal object ComposerAttachmentFiles {
  val photos: List<VisoreFile> = VisoriFiles.gallery.filter { it.id == "lancio" || it.id == "collins" }
  val document: VisoreFile = VisoriFiles.gallery.first { it.id == "quaderno" }
  val all: List<VisoreFile> = photos + document

  /** La cartella in cui si copiano. */
  const val Scene: String = "composer"
}

/** I file allegabili, pronti nella cartella dell'app (null finché la copia non è finita). */
@Composable
internal fun rememberComposerAttachmentFiles(): State<Map<String, FluidViewerFile>?> {
  val context = LocalContext.current
  return produceState<Map<String, FluidViewerFile>?>(null, context) {
    val files = ComposerAttachmentFiles.all
    value = files.map { it.id }.zip(VisoriFiles.prepare(context, files, ComposerAttachmentFiles.Scene)).toMap()
  }
}

/**
 * Un allegato del vassoio con l'anteprima vera: [file] (null finché non è pronto) dà la miniatura di `FluidFileThumbnails`
 * alla misura del riquadro. Una foto riempie il riquadro, gli altri file stanno accanto al nome.
 */
@Composable
internal fun ComposerFileAttachment(
  file: FluidViewerFile?,
  label: String,
  photo: Boolean,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val px = with(LocalDensity.current) { FluidComposerAttachmentDefaults.Height.roundToPx() }
  FluidComposerAttachment(
    label = label,
    onRemove = onRemove,
    modifier = modifier,
    style = if (photo) FluidComposerAttachmentStyle.Photo else FluidComposerAttachmentStyle.File,
  ) {
    val thumbnail = rememberFluidFileThumbnail(file, px)
    if (thumbnail != null) FluidFileThumbnailImage(thumbnail, Modifier.fillMaxSize(), contentDescription = null)
  }
}
