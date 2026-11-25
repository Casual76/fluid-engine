package dev.antigravity.fluidengine.ui.fluid.output

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Il formato di un'immagine da condividere: la tela in pixel e la larghezza in dp a cui si compone il contenuto.
 * Le tre di serie sono quelle delle chat e delle storie: [Square] (1080 × 1080), [Portrait] (4:5, 1080 × 1350, la piu'
 * alta che un post mostra intera) e [Story] (9:16, 1080 × 1920). A 360 dp di larghezza il contenuto ha la misura di un
 * telefono: un dp e' 3 pixel, e il testo si legge come sullo schermo.
 */
@Immutable
class FluidShareImageFormat(val name: String, val widthPx: Int, val heightPx: Int, val widthDp: Float = 360f) {
  init {
    require(widthPx > 0 && heightPx > 0 && widthDp > 0f)
  }

  /** I pixel per dp della tela. */
  val density: Float get() = widthPx / widthDp

  /** I byte della bitmap ARGB del formato. */
  val bytes: Long get() = widthPx.toLong() * heightPx * 4

  override fun toString(): String = "FluidShareImageFormat($name, ${widthPx}x$heightPx)"

  companion object {
    val Square: FluidShareImageFormat = FluidShareImageFormat("1:1", 1080, 1080)
    val Portrait: FluidShareImageFormat = FluidShareImageFormat("4:5", 1080, 1350)
    val Story: FluidShareImageFormat = FluidShareImageFormat("9:16", 1080, 1920)

    /** I tre di serie, nell'ordine dei selettori. */
    val all: List<FluidShareImageFormat> = listOf(Square, Portrait, Story)
  }
}

/** La tela non entra nella memoria che l'engine lascia alle uscite (`FluidMemoryBudget`): meglio un errore che un OOM. */
class FluidOutputTooLargeException(message: String) : IllegalStateException(message)

/**
 * Fa un'immagine da una vista Compose (4.0, F8.10), come `ImageRenderer` di SwiftUI: il voto o l'impegno «come
 * immagine, senza fare lo screenshot» della nota di Alessio. Si ottiene con [rememberFluidShareImageRenderer] nella
 * pagina che condivide, e la vista nuova eredita il tema, la lingua e le parole della pagina.
 *
 * ```kotlin
 * val renderer = rememberFluidShareImageRenderer()
 * scope.launch {
 *   val file = renderer.renderToFile(FluidShareImageFormat.Story, "Voto di matematica.png") {
 *     FluidShareImageFrame(signature = "ClasseViva") { GradeCard(grade) }
 *   }
 *   share = FluidShareItem.file(context, file, "image/png")
 * }
 * ```
 *
 * - **Il disegno** e' software, sul thread principale, una volta sola per immagine: il testo e le forme sono veri; la
 *   sfocatura no. Per questo nelle immagini il vetro e' il materiale piatto ([FluidPrintPanel], [LocalFluidPrinting]).
 * - **La compressione** del PNG e la scrittura del file vanno su `Dispatchers.IO`.
 * - **La memoria**: la tela si prenota nel registro dell'engine (categoria `VIEWER`, la stessa dei visori) prima di
 *   esistere; se non c'e' posto [render] lancia [FluidOutputTooLargeException], e la bitmap si libera appena scritta.
 */
@Stable
class FluidShareImageRenderer internal constructor(
  private val view: View,
  private val parent: CompositionContext,
) {
  /**
   * La bitmap di [content] nel [format]. Chi la riceve la ricicla quando ha finito. Dal thread principale.
   *
   * Se il registro della memoria non ha posto per la tela intera, l'immagine esce **a meta' misura** (540 px di
   * larghezza, lo stesso disegno): meglio un'immagine piu' piccola che nessuna. Se non c'e' posto nemmeno per quella,
   * [FluidOutputTooLargeException].
   */
  fun render(format: FluidShareImageFormat, content: @Composable () -> Unit): Bitmap {
    val budget = FluidGlassResources.get(view.context).budget
    var scale = 1f
    val reservation = budget.tryReserve(FluidMemoryCategory.VIEWER, format.bytes)
      ?: budget.tryReserve(FluidMemoryCategory.VIEWER, format.bytes / 4)?.also { scale = 0.5f }
      ?: throw FluidOutputTooLargeException("Nessuno spazio per una tela ${format.widthPx}×${format.heightPx}")
    val width = (format.widthPx * scale).toInt()
    val height = (format.heightPx * scale).toInt()
    try {
      return fluidComposeOffscreen(view, parent, width, height, format.density * scale, {
        Box(Modifier.fillMaxSize()) { content() }
      }) { composed, _ ->
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        fluidDrawView(composed, Canvas(bitmap))
        bitmap
      }
    } finally {
      // La bitmap e' del chiamante: il registro conta le tele in costruzione, non quelle consegnate.
      reservation.close()
    }
  }

  /**
   * [render] e poi il PNG in un file nuovo della cartella delle uscite dell'engine ([FluidShareFiles]), pronto per
   * [dev.antigravity.fluidengine.ui.fluid.share.FluidShareItem.file]. La bitmap si ricicla appena scritta.
   */
  suspend fun renderToFile(
    format: FluidShareImageFormat,
    fileName: String,
    content: @Composable () -> Unit,
  ): File {
    val bitmap = withContext(Dispatchers.Main.immediate) { render(format, content) }
    return withContext(Dispatchers.IO) {
      val name = if (fileName.lowercase().endsWith(".png")) fileName else "$fileName.png"
      val file = FluidShareFiles.newFile(view.context, name)
      try {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      } finally {
        bitmap.recycle()
      }
      file
    }
  }

  /** Il contesto della pagina, per chi costruisce l'oggetto da condividere. */
  val context: Context get() = view.context
}

/** Il renderer delle immagini da condividere per questa pagina (vedi [FluidShareImageRenderer]). */
@Composable
fun rememberFluidShareImageRenderer(): FluidShareImageRenderer {
  val view = LocalView.current
  val parent = rememberCompositionContext()
  return remember(view, parent) { FluidShareImageRenderer(view, parent) }
}
