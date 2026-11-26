package dev.antigravity.fluidengine.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Da dove viene la foto da modificare: un indirizzo (`content://` del selettore di `engine-media`, di MyCloud Gallery, del
 * Photo Picker di Android, o un `file://`) oppure una bitmap che l'app ha gia' in memoria (gia' girata per il verso giusto).
 *
 * Un indirizzo dato da un'altra app si legge **finche' il permesso vale** (F8.14): l'editor apre l'anteprima subito, e il
 * salvataggio rilegge il file; un'app che tiene la modifica aperta a lungo ne fa prima una copia.
 */
class FluidEditorSource private constructor(internal val uri: Uri?, internal val bitmap: Bitmap?) {
  override fun equals(other: Any?) = other is FluidEditorSource && other.uri == uri && other.bitmap === bitmap
  override fun hashCode() = uri?.hashCode() ?: System.identityHashCode(bitmap)
  override fun toString() = "FluidEditorSource(${uri ?: "bitmap ${bitmap?.width}x${bitmap?.height}"})"

  companion object {
    fun uri(uri: Uri): FluidEditorSource = FluidEditorSource(uri, null)
    fun bitmap(bitmap: Bitmap): FluidEditorSource = FluidEditorSource(null, bitmap)
  }
}

/** Le misure e il verso di un file: [rawWidth] × [rawHeight] come sono scritti, [orientation] dall'EXIF. */
internal class SourceInfo(val rawWidth: Int, val rawHeight: Int, val orientation: Int, val mime: String?) {
  /** Le rotazioni e gli specchi dell'EXIF che si scambiano i lati. */
  private val transposed = orientation in setOf(
    ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270,
  )
  val width = if (transposed) rawHeight else rawWidth
  val height = if (transposed) rawWidth else rawHeight

  /** Dai pixel del file (largo [w], alto [h]: anche una versione ridotta) a quelli della foto dritta. */
  fun exif(w: Float, h: Float): FluidAffine = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> FluidAffine(-1f, 0f, 0f, 1f, w, 0f)
    ExifInterface.ORIENTATION_ROTATE_180 -> FluidAffine(-1f, 0f, 0f, -1f, w, h)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> FluidAffine(1f, 0f, 0f, -1f, 0f, h)
    ExifInterface.ORIENTATION_TRANSPOSE -> FluidAffine(0f, 1f, 1f, 0f, 0f, 0f)
    ExifInterface.ORIENTATION_ROTATE_90 -> FluidAffine(0f, 1f, -1f, 0f, h, 0f)
    ExifInterface.ORIENTATION_TRANSVERSE -> FluidAffine(0f, -1f, -1f, 0f, h, w)
    ExifInterface.ORIENTATION_ROTATE_270 -> FluidAffine(0f, -1f, 1f, 0f, 0f, w)
    else -> FluidAffine.Identity
  }
}

/**
 * La foto aperta nell'editor: la sorgente ridotta ([preview], gia' dritta) e le misure vere ([sourceWidth] ×
 * [sourceHeight]), piu' cio' che serve al calcolo, preparato una volta sola.
 */
internal class EditorImage(val preview: Bitmap, val sourceWidth: Int, val sourceHeight: Int) {
  val width get() = preview.width
  val height get() = preview.height

  /** I pixel dell'anteprima (per la CPU e per «Auto»). */
  val pixels: IntArray by lazy { IntArray(width * height).also { preview.getPixels(it, 0, width, 0, 0, width, height) } }

  val definition: DefinitionMap by lazy { DefinitionMap.of(pixels, width, height) }

  /** La mappa della definizione come immagine grigia, per lo shader. */
  val definitionBitmap: Bitmap by lazy {
    val m = definition
    val argb = IntArray(m.width * m.height) {
      val v = m.values[it].toInt() and 0xFF
      (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    Bitmap.createBitmap(argb, m.width, m.height, Bitmap.Config.ARGB_8888)
  }

  /** La proposta di «Auto» per questa foto. */
  val auto: FluidAdjustments by lazy { FluidAutoAdjust.suggest(pixels) }

  /** Il raggio della nitidezza in pixel dell'anteprima, per un ritaglio di [geometry]. */
  fun sharpRadius(geometry: FluidCropGeometry): Float {
    val (w, h) = geometry.outputSize(sourceWidth, sourceHeight)
    val full = AdjustCurves.sharpRadius(min(w, h))
    return max(1f, full * width.toFloat() / sourceWidth)
  }

  /** Dai pixel dell'anteprima alle frazioni del ritaglio (la vignettatura). */
  fun toOutputFraction(geometry: FluidCropGeometry): FluidAffine {
    val m = geometry.sourceToOutput(width, height)
    val (ow, oh) = geometry.outputSize(width, height)
    return FluidAffine(1f / ow, 0f, 0f, 1f / oh, 0f, 0f).then(m)
  }

  companion object {
    /** L'anteprima con il lato lungo al piu' [maxSide]. Fuori dal thread principale. */
    fun load(context: Context, source: FluidEditorSource, maxSide: Int): EditorImage {
      source.bitmap?.let { b ->
        val k = min(1f, maxSide.toFloat() / max(b.width, b.height))
        val preview = scaled(b, FluidAffine(k, 0f, 0f, k, 0f, 0f), (b.width * k).roundToInt(), (b.height * k).roundToInt())
        return EditorImage(preview, b.width, b.height)
      }
      val uri = source.uri!!
      val info = EditorDecode.info(context, uri)
      val sample = EditorDecode.sampleFor(max(info.rawWidth, info.rawHeight), maxSide)
      val decoded = EditorDecode.decode(context, uri, sample)
        ?: error("Impossibile leggere la foto: $uri")
      return fromDecoded(decoded.bitmap, decoded.oriented, info, maxSide)
    }

    internal fun fromDecoded(bitmap: Bitmap, oriented: Boolean, info: SourceInfo, maxSide: Int): EditorImage {
      val exif = if (oriented) FluidAffine.Identity else info.exif(bitmap.width.toFloat(), bitmap.height.toFloat())
      val dw = if (oriented) bitmap.width else if (info.width == info.rawWidth) bitmap.width else bitmap.height
      val dh = if (oriented) bitmap.height else if (info.width == info.rawWidth) bitmap.height else bitmap.width
      val k = min(1f, maxSide.toFloat() / max(dw, dh))
      val w = max(1, (dw * k).roundToInt())
      val h = max(1, (dh * k).roundToInt())
      val m = FluidAffine(w.toFloat() / dw, 0f, 0f, h.toFloat() / dh, 0f, 0f).then(exif)
      val preview = scaled(bitmap, m, w, h)
      if (preview !== bitmap) bitmap.recycle()
      return EditorImage(preview, info.width, info.height)
    }

    private fun scaled(src: Bitmap, m: FluidAffine, w: Int, h: Int): Bitmap {
      if (m == FluidAffine.Identity && src.config == Bitmap.Config.ARGB_8888 && w == src.width && h == src.height) return src
      val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      Canvas(out).drawBitmap(src, Matrix().apply { setValues(m.toMatrixValues()) }, Paint(Paint.FILTER_BITMAP_FLAG))
      return out
    }
  }
}

/** Le letture dal disco: le misure, il verso, la decodifica ridotta. */
internal object EditorDecode {
  class Decoded(val bitmap: Bitmap, val oriented: Boolean)

  fun open(context: Context, uri: Uri): InputStream =
    context.contentResolver.openInputStream(uri) ?: error("Impossibile aprire $uri")

  fun info(context: Context, uri: Uri): SourceInfo {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open(context, uri).use { BitmapFactory.decodeStream(it, null, opts) }
    val orientation = runCatching {
      open(context, uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    if (opts.outWidth <= 0 && Build.VERSION.SDK_INT >= 28) {
      // HEIF o AVIF che BitmapFactory non sa leggere: ImageDecoder da' le misure gia' dritte.
      var w = 0
      var h = 0
      runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, i, _ ->
          w = i.size.width
          h = i.size.height
          d.setTargetSize(1, 1)
        }
      }
      return SourceInfo(w, h, ExifInterface.ORIENTATION_NORMAL, null)
    }
    return SourceInfo(opts.outWidth, opts.outHeight, orientation, opts.outMimeType)
  }

  /** La potenza di due piu' grande che lascia il lato lungo almeno a [target] pixel. */
  fun sampleFor(longSide: Int, target: Int): Int {
    var s = 1
    while (longSide / (s * 2) >= target) s *= 2
    return s
  }

  fun decode(context: Context, uri: Uri, sample: Int): Decoded? {
    val opts = BitmapFactory.Options().apply {
      inSampleSize = sample
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    open(context, uri).use { stream -> BitmapFactory.decodeStream(stream, null, opts) }?.let { return Decoded(it, false) }
    if (Build.VERSION.SDK_INT >= 28) {
      return runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, i, _ ->
          d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          d.setTargetSize(max(1, i.size.width / sample), max(1, i.size.height / sample))
        }
        Decoded(bitmap, true)
      }.getOrNull()
    }
    return null
  }
}

/**
 * Il calcolo delle regolazioni sulla CPU, diviso fra i fili ([Dispatchers.Default]). Lo usano il salvataggio, l'anteprima
 * finita e, sotto Android 13, l'anteprima mentre il dito muove.
 */
internal object EditorCompute {

  /** Calcola [src] in [dst] dividendo le righe fra i processori. */
  suspend fun process(src: IntArray, w: Int, h: Int, plan: AdjustPlan, ctx: AdjustContext, dst: IntArray,
    rows: IntRange = 0 until h) {
    val total = rows.last - rows.first + 1
    if (total <= 0) return
    coroutineScope { work(src, w, h, plan, ctx, dst, rows, total) }
  }

  private suspend fun kotlinx.coroutines.CoroutineScope.work(src: IntArray, w: Int, h: Int, plan: AdjustPlan,
    ctx: AdjustContext, dst: IntArray, rows: IntRange, total: Int) {
    val parts = max(1, min(Runtime.getRuntime().availableProcessors(), total / 16))
    (0 until parts).map { i ->
      val from = rows.first + total * i / parts
      val to = rows.first + total * (i + 1) / parts
      async(Dispatchers.Default) { AdjustKernel.process(src, w, h, plan, ctx, dst, from until to) }
    }.awaitAll()
  }

  /**
   * Tutta l'anteprima con [adjustments], ridotta di [scale] (1 = piena): l'anteprima della CPU. [geometry] serve alla
   * vignettatura e al raggio della nitidezza.
   */
  suspend fun renderPreview(image: EditorImage, adjustments: FluidAdjustments, geometry: FluidCropGeometry, scale: Float): Bitmap {
    val w = max(1, (image.width * scale).roundToInt())
    val h = max(1, (image.height * scale).roundToInt())
    val src = if (w == image.width && h == image.height) image.pixels else {
      val small = Bitmap.createScaledBitmap(image.preview, w, h, true)
      IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h); small.recycle() }
    }
    val plan = AdjustPlan(adjustments)
    val toPreview = FluidAffine(image.width.toFloat() / w, 0f, 0f, image.height.toFloat() / h, 0f, 0f)
    val map = image.definition
    val ctx = AdjustContext(
      toOutput = image.toOutputFraction(geometry).then(toPreview),
      toMap = FluidAffine(map.width.toFloat() / w, 0f, 0f, map.height.toFloat() / h, 0f, 0f),
      map = map,
      sharpRadius = max(1, (image.sharpRadius(geometry) * w / image.width).roundToInt()),
    )
    val dst = IntArray(w * h)
    process(src, w, h, plan, ctx, dst)
    return Bitmap.createBitmap(dst, w, h, Bitmap.Config.ARGB_8888)
  }
}
