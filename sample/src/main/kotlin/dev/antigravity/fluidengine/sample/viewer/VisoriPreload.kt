package dev.antigravity.fluidengine.sample.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageNotes
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidStyledTextSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidTextPresetDefaults
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.FluidViewerFiles
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal val VisoreTipo.preparesDocument: Boolean
  get() = when (this) {
    VisoreTipo.Foto, VisoreTipo.Animata, VisoreTipo.Video, VisoreTipo.Audio -> false
    else -> true
  }

internal const val VisoriPreparationMillis = 120L

@Composable
internal fun rememberVisoriDocuments(catalog: List<VisoreFile>, files: List<FluidViewerFile>?,
  scene: String = "galleria"): FluidViewerFiles {
  val context = LocalContext.current
  val list = files.orEmpty()
  fun sample(file: FluidViewerFile) = catalog[list.indexOf(file)]
  return rememberFluidViewerFiles(list,
    openWith = { VisoriFiles.openWith(context, sample(it), scene) },
    share = { VisoriFiles.share(context, sample(it), scene) })
}

internal class VisoriWarmPage(val source: FluidPageSource, size: IntSize, bitmap: ImageBitmap) {
  /**
   * La prima pagina già resa, poi le altre dalla sorgente. F6C.20: l'involucro porta anche le capacità facoltative della
   * sorgente che il preset riconosce (`FluidPageNotes`, le note del relatore). Prima delegava solo [FluidPageSource]: sul
   * dispositivo la galleria apre le slide con la pagina pronta, e «Note del relatore» spariva da «•••»; le prove non
   * passano di qui (aprono senza preparare), e le note c'erano.
   */
  val content = FluidViewerContent.Pages(
    (source as? FluidPageNotes)?.let { notes -> WarmNotedPages(source, notes, size, bitmap) }
      ?: WarmPages(source, size, bitmap),
  )
}

private open class WarmPages(private val source: FluidPageSource, private val size: IntSize,
  private val bitmap: ImageBitmap) : FluidPageSource by source {
  override suspend fun render(index: Int, target: IntSize): ImageBitmap =
    if (index == 0 && target.width <= size.width && target.height <= size.height) bitmap
    else source.render(index, target)
}

private class WarmNotedPages(source: FluidPageSource, notes: FluidPageNotes, size: IntSize, bitmap: ImageBitmap) :
  WarmPages(source, size, bitmap), FluidPageNotes by notes

/** Resolve through the same registry as the route. Raster and text work can outlive the click's short wait. */
internal suspend fun prepareVisoriFirstContent(files: FluidViewerFiles, index: Int, width: Int): VisoriWarmPage? {
  try {
    files.content(index)
    val content = snapshotFlow { files.document(index) }.filterNotNull().first().content
    return withContext(Dispatchers.Default) {
      when (content) {
        is FluidViewerContent.Pages -> {
          val source = content.source
          snapshotFlow { source.count > 0 || source.countFinal }.first { it }
          if (source.count == 0) return@withContext null
          val natural = source.size(0)
          val scale = minOf(width.toDouble() / natural.width, 2048.0 / maxOf(natural.width, natural.height))
          val target = IntSize((natural.width * scale).roundToInt().coerceAtLeast(1),
            (natural.height * scale).roundToInt().coerceAtLeast(1))
          VisoriWarmPage(source, target, source.render(0, target))
        }
        is FluidViewerContent.Text -> {
          val source = content.source
          if (source is FluidStyledTextSource) source.readStyledLines(0, FluidTextPresetDefaults.ChunkLines)
          else source.readLines(0, FluidTextPresetDefaults.ChunkLines)
          null
        }
        is FluidViewerContent.Grid -> {
          content.source.sheets.firstOrNull()?.let { content.source.cells(0, 0..63, 0..31) }
          null
        }
        else -> null
      }
    }
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: Exception) {
    // The normal reader owns the error and retry UI.
    return null
  }
}
