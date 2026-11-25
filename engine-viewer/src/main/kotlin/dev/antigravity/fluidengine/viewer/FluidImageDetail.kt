package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect as AndroidRect
import android.media.ExifInterface
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.FluidZoomState
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageRead
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// F8.18 · Il bilancio delle immagini enormi (`visore-con-bilancio`, «pagine o tasselli»). Una foto da 50 o 200 MP si
// decodifica ridotta a 12 MP (FluidDecodedImage); ingrandendola oltre quella risoluzione, **da fermi** si decodifica
// solo la parte in vista, alla risoluzione dello schermo (`BitmapRegionDecoder`), e la si disegna sopra la foto ridotta,
// nello stesso posto: durante il pizzico resta il tassello di prima, che si muove con la foto. Come Foto di iOS, che
// porta i dettagli quando ci si ferma. Niente da chiedere all'utente; la memoria passa dal registro del visore (F7.13).

/** Il tassello da decodificare: la parte dell'originale ([source]), il campionamento e il posto nella foto mostrata. */
internal data class ImageDetailPlan(val source: IntRect, val sample: Int, val local: Rect) {
  /** La misura della bitmap che ne esce. */
  val output: IntSize get() = IntSize(
    ((source.width + sample - 1) / sample).coerceAtLeast(1),
    ((source.height + sample - 1) / sample).coerceAtLeast(1),
  )
}

/**
 * Il tassello per una foto [source] (pixel dell'originale), decodificata a [decodedWidth] pixel di larghezza, mostrata
 * in un riquadro di [box] pixel che lo zoom del telaio ([scale] attorno al centro, poi [panX] e [panY]) porta in una
 * vista di [viewport] pixel. Null se la foto ridotta basta (non si è ingranditi oltre la sua risoluzione, con un
 * margine di [Slack]) o se non si vede niente. Pura, per le prove.
 */
internal fun imageDetailPlan(
  source: IntSize, decodedWidth: Int, box: IntSize, viewport: IntSize,
  scale: Float, panX: Float, panY: Float,
): ImageDetailPlan? {
  if (source.width <= 0 || source.height <= 0 || box.width <= 0 || box.height <= 0 || scale <= 0f) return null
  // Quanti pixel dello schermo prende la larghezza della foto, contro quanti ne ha la foto ridotta.
  if (box.width * scale <= decodedWidth * Slack) return null
  if (decodedWidth >= source.width) return null
  val cx = box.width / 2f
  val cy = box.height / 2f
  val left = (cx + (-viewport.width / 2f - panX) / scale).coerceIn(0f, box.width.toFloat())
  val right = (cx + (viewport.width / 2f - panX) / scale).coerceIn(0f, box.width.toFloat())
  val top = (cy + (-viewport.height / 2f - panY) / scale).coerceIn(0f, box.height.toFloat())
  val bottom = (cy + (viewport.height / 2f - panY) / scale).coerceIn(0f, box.height.toFloat())
  if (right - left < 1f || bottom - top < 1f) return null
  val kx = source.width / box.width.toFloat()
  val ky = source.height / box.height.toFloat()
  val region = IntRect(
    IntOffset(floor(left * kx).toInt().coerceIn(0, source.width - 1), floor(top * ky).toInt().coerceIn(0, source.height - 1)),
    IntOffset(ceil(right * kx).toInt().coerceIn(1, source.width), ceil(bottom * ky).toInt().coerceIn(1, source.height)),
  )
  // Il campionamento più grosso che dà ancora almeno un pixel dell'originale per pixel dello schermo.
  val screen = (right - left) * scale
  var sample = 1
  while (region.width / (sample * 2f) >= screen && sample < MaxSample) sample *= 2
  // Il posto nel riquadro, dai pixel interi dell'originale.
  val local = Rect(region.left / kx, region.top / ky, region.right / kx, region.bottom / ky)
  return ImageDetailPlan(region, sample, local)
}

/** Oltre questo rapporto fra lo schermo e la foto ridotta serve il tassello: 1,25, a occhio (25 novembre 2026). */
private const val Slack = 1.25f
private const val MaxSample = 64

/** Quanto la foto deve restare ferma prima di chiedere il tassello: 150 ms, a occhio (25 novembre 2026). */
internal const val ImageDetailSettleMillis = 150L

private class DetailTile(val plan: ImageDetailPlan, val bitmap: ImageBitmap, val reader: FluidPageRead)

/**
 * Il tassello sopra la foto ridotta: riempie il riquadro della foto (dentro lo zoom del telaio) e disegna, quando c'è,
 * la parte in vista alla risoluzione dello schermo. [open] apre l'originale (un descrittore su cui ci si sposta).
 */
@Composable
internal fun ImageDetailLayer(zoom: FluidZoomState, source: IntSize, decodedWidth: Int, open: () -> ParcelFileDescriptor?) {
  val context = LocalContext.current.applicationContext
  val window = LocalWindowInfo.current.containerSize
  val currentWindow by rememberUpdatedState(window)
  var box by remember { mutableStateOf(IntSize.Zero) }
  var tile by remember { mutableStateOf<DetailTile?>(null) }
  DisposableEffect(Unit) { onDispose { tile?.reader?.close() } }
  LaunchedEffect(open, source, decodedWidth) {
    snapshotFlow { Triple(zoom.scale, zoom.panX, zoom.panY) to box }.collectLatest { (transform, size) ->
      val plan = imageDetailPlan(source, decodedWidth, size, currentWindow, transform.first, transform.second, transform.third)
      if (plan == null) {
        tile?.reader?.close()
        tile = null
        return@collectLatest
      }
      if (tile?.plan == plan) return@collectLatest
      // Solo da fermi: il pizzico e il lancio tengono il tassello di prima, che si muove con la foto.
      delay(ImageDetailSettleMillis)
      val reader = FluidPageRead()
      val bitmap = try {
        withContext(Dispatchers.IO + reader) { decodeImageDetail(context, open, plan) }
      } catch (cancelled: CancellationException) {
        reader.close()
        throw cancelled
      } catch (_: Exception) {
        null
      }
      if (bitmap == null) {
        reader.close()
        return@collectLatest
      }
      tile?.reader?.close()
      tile = DetailTile(plan, bitmap, reader)
    }
  }
  Spacer(Modifier.fillMaxSize().onSizeChanged { box = it }.drawBehind {
    val shown = tile ?: return@drawBehind
    val local = shown.plan.local
    drawImage(shown.bitmap, dstOffset = IntOffset(local.left.roundToInt(), local.top.roundToInt()),
      dstSize = IntSize(local.width.roundToInt().coerceAtLeast(1), local.height.roundToInt().coerceAtLeast(1)))
  })
}

/** Decodifica il tassello col registro della memoria del visore; null se il formato non lo permette. */
private suspend fun decodeImageDetail(context: Context, open: () -> ParcelFileDescriptor?, plan: ImageDetailPlan): ImageBitmap? =
  FluidViewerRaster.render(context, plan.output) { admitted ->
    // Con poca memoria il registro ammette una misura più piccola: si campiona di più.
    var sample = plan.sample
    while ((plan.source.width + sample - 1) / sample > admitted.width && sample < 1024) sample *= 2
    // Una foto girata dall'EXIF si mostra girata; il decodificatore a regioni non la gira: niente tassello.
    val orientation = try {
      open()?.use { exif ->
        ExifInterface(exif.fileDescriptor).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
      } ?: return@render null
    } catch (_: Exception) {
      ExifInterface.ORIENTATION_NORMAL
    }
    if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) return@render null
    val descriptor = open() ?: return@render null
    descriptor.use { fd ->
      val decoder = if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(fd)
        else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(fd.fileDescriptor, false)
      try {
        val rect = AndroidRect(plan.source.left, plan.source.top, plan.source.right, plan.source.bottom)
        decoder?.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
      } finally {
        decoder?.recycle()
      }
    }
  }
