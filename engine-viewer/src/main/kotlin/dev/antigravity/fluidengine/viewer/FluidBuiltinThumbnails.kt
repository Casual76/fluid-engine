package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.icons.AudioFile
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// F6C.17 · Le miniature di engine-viewer di serie e il giro sui candidati del registro. Il registro le usa per
// `FluidViewerRegistry.thumbnail` (testo, codice, APK e le famiglie) e `FluidFileThumbnails` per tutto il resto
// (immagini, PDF, video, audio e l'icona del documento).

/** Quanti byte di un testo legge la miniatura: le prime righe stanno nei primi 16 KB. */
private const val ThumbnailTextBytes = 16 * 1024

/** Le righe più lunghe si tagliano: la miniatura ne mostra al più qualche decina di caratteri. */
private const val ThumbnailLineChars = 240

/**
 * La miniatura di [source] come la dà il registro: la famiglia che lo apre (nell'ordine dei candidati di F6C.8), poi il
 * preset di serie. [everything] falso tiene solo testo, codice e APK (le foto e i PDF sono dell'app, come in
 * `FluidViewerRegistry.thumbnail`); vero aggiunge immagini, PDF, video e audio. `null` se niente dà un'immagine. Non
 * chiude [source] e non lancia, se non per l'annullamento.
 */
internal suspend fun fluidThumbnailOf(
  context: Context,
  source: FluidViewerSource,
  sizePx: Int,
  options: FluidViewerOptions,
  everything: Boolean,
): FluidFileThumbnail? {
  val app = context.applicationContext ?: context
  val size = max(1, sizePx)
  val start = FluidViewerType.builtin(source.mimeType, source.name, null).type
  // Un eseguibile non arriva mai a una famiglia e non si apre: di un APK si legge solo l'icona, come fa il preset
  // sconosciuto per il suo avviso.
  if (start.executable) return if (fluidIsApk(source)) thumbnailSafely { apkThumbnail(app, source, size) } else null
  val families = FluidViewerRegistry.families
  val needsHeader = start.preset == FluidViewerPreset.Unknown && start.mimeType == null
  val probe = FluidViewerProbe.read(source, families, always = needsHeader)
  val candidates = FluidViewerResolver.candidates(families, source.mimeType, source.name, probe.header, probe.entries)
  for (candidate in candidates) {
    val made = thumbnailSafely {
      val family = candidate.family
      if (family != null) {
        val request = FluidViewerRequest(app, source, requireNotNull(candidate.familyType), options, null, null, null)
        family.thumbnail(request, size)?.let { FluidFileThumbnail.of(it, fillsByKind = candidate.type.fillsFrame) }
      } else {
        builtinThumbnail(app, source, candidate.type, size, everything)
      }
    }
    if (made != null) return made
  }
  // Un file senza nome né MIME che si legge come testo: come il preset di serie, che in fondo prova il testo.
  if (needsHeader && candidates.isEmpty()) {
    return thumbnailSafely { textThumbnail(source, FluidViewerType(FluidViewerPreset.Text, FluidFileKind.Text, null, null), size) }
  }
  return null
}

/** L'icona del documento con l'estensione di [name], come File: il ripiego di ogni miniatura. */
internal fun fluidPlaceholderThumbnail(name: String, sizePx: Int): FluidFileThumbnail {
  val leaf = name.substringAfterLast('/').substringAfterLast('\\')
  val extension = if (leaf.lastIndexOf('.') > 0) leaf.substringAfterLast('.') else ""
  return FluidFileThumbnail.of(FluidThumbnailPainter.documentIcon(extension, sizePx), fillsByKind = false)
}

internal fun fluidIsApk(source: FluidViewerSource): Boolean =
  source.name.lowercase(Locale.ROOT).endsWith(".apk") ||
    FluidViewerType.normalizeMime(source.mimeType) == "application/vnd.android.package-archive"

private val FluidViewerType.fillsFrame: Boolean
  get() = preset == FluidViewerPreset.Image || preset == FluidViewerPreset.Video

private suspend fun thumbnailSafely(block: suspend () -> FluidFileThumbnail?): FluidFileThumbnail? = try {
  block()
} catch (e: CancellationException) {
  throw e
} catch (_: Exception) {
  null
} catch (_: LinkageError) {
  // La libreria nativa di una famiglia non si carica su questo dispositivo: niente miniatura da lei.
  null
} catch (_: OutOfMemoryError) {
  null
} catch (_: StackOverflowError) {
  null
}

private suspend fun builtinThumbnail(
  context: Context,
  source: FluidViewerSource,
  type: FluidViewerType,
  size: Int,
  everything: Boolean,
): FluidFileThumbnail? = when (type.preset) {
  FluidViewerPreset.Text -> textThumbnail(source, type, size)
  FluidViewerPreset.Image -> if (everything) imageThumbnail(source, size) else null
  FluidViewerPreset.Pages -> if (everything) pdfThumbnail(source, size) else null
  FluidViewerPreset.Video -> if (everything) videoThumbnail(source, size) else null
  FluidViewerPreset.Audio -> if (everything) audioThumbnail(source, size) else null
  else -> null
}

/**
 * Le prime righe di un testo su un foglio: il codice (con una lingua riconosciuta) a spaziatura fissa coi colori di
 * Xcode, senza a capo; il testo semplice come prosa che va a capo. Un file che non è testo non ha miniatura.
 */
private suspend fun textThumbnail(source: FluidViewerSource, type: FluidViewerType, size: Int): FluidFileThumbnail? {
  val document = withContext(Dispatchers.IO) {
    source.open().use { input ->
      FluidTextDecoder.read(input, source.name, source.mimeType, source.sizeBytes, ThumbnailTextBytes, ThumbnailLineChars,
        language = type.language ?: FluidSyntax.languageFor(source.name, source.mimeType))
    }
  } ?: return null
  val language = document.language
  val image = if (language == null) {
    FluidThumbnailPainter.prosePage(document.lines.take(48).joinToString("\n"), size)
  } else {
    FluidThumbnailPainter.codePage(document.lines.take(FluidThumbnailPainter.CodeLinesPerPage + 1),
      FluidSyntax.highlighter(language), size)
  }
  return FluidFileThumbnail.of(image, fillsByKind = false)
}

/** Un'immagine ridotta al lato [size], col suo orientamento EXIF. */
private suspend fun imageThumbnail(source: FluidViewerSource, size: Int): FluidFileThumbnail? = withContext(Dispatchers.IO) {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  source.open().use { BitmapFactory.decodeStream(it, null, bounds) }
  if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
  var sample = 1
  while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
  val decoded = source.open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    ?: return@withContext null
  val degrees = try {
    when (source.open().use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }) {
      android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
      android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
      android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
      else -> 0
    }
  } catch (_: Exception) {
    0
  }
  FluidFileThumbnail.of(FluidThumbnailPainter.picture(fit(decoded, size, degrees)), fillsByKind = true)
}

/** La prima pagina di un PDF, su carta bianca. */
private suspend fun pdfThumbnail(source: FluidViewerSource, size: Int): FluidFileThumbnail? {
  val pages = if (source is FluidViewerFileSource) FluidPdfPageSource.open(source.file) else FluidPdfPageSource.open(source.seekable())
  return pages.use {
    if (it.count <= 0) return@use null
    val page = it.size(0)
    if (page.width <= 0 || page.height <= 0) return@use null
    val scale = size.toFloat() / max(page.width, page.height)
    val target = IntSize(max(1, (page.width * scale).roundToInt()), max(1, (page.height * scale).roundToInt()))
    val rendered = it.render(0, target).asAndroidBitmap()
    FluidFileThumbnail.of(FluidThumbnailPainter.tag(rendered, fills = false, placeholder = false), fillsByKind = false)
  }
}

/** Un fotogramma del video a un secondo (o al primo), ridotto. */
private suspend fun videoThumbnail(source: FluidViewerSource, size: Int): FluidFileThumbnail? = withContext(Dispatchers.IO) {
  source.seekable().use { descriptor ->
    val retriever = MediaMetadataRetriever()
    try {
      retriever.setDataSource(descriptor.fileDescriptor)
      val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        ?: retriever.frameAtTime ?: return@withContext null
      FluidFileThumbnail.of(FluidThumbnailPainter.picture(fit(frame, size, 0)), fillsByKind = true)
    } finally {
      retriever.release()
    }
  }
}

/** La copertina di un audio; senza, il simbolo dell'audio nella tinta dell'app (dice il tipo, non il contenuto). */
private suspend fun audioThumbnail(source: FluidViewerSource, size: Int): FluidFileThumbnail? {
  val cover = withContext(Dispatchers.IO) {
    source.seekable().use { descriptor ->
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(descriptor.fileDescriptor)
        retriever.embeddedPicture
      } catch (_: Exception) {
        null
      } finally {
        retriever.release()
      }
    }
  }
  val picture = cover?.let { bytes ->
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
  }
  if (picture != null) return FluidFileThumbnail.of(FluidThumbnailPainter.picture(fit(picture, size, 0)), fillsByKind = true)
  val extension = source.name.substringAfterLast('.', "")
  return FluidFileThumbnail.of(FluidThumbnailPainter.kindSymbol(FluidIcons.AudioFile, extension, size,
    FluidThumbnailLook.current(), placeholder = true), fillsByKind = false)
}

/**
 * L'icona di un APK, letta dal pacchetto come fa il preset sconosciuto per il suo avviso, con un alone del suo colore
 * (§5.1, «l'eseguibile con l'icona e l'alone»). Il pacchetto non si installa e non si esegue.
 */
private suspend fun apkThumbnail(context: Context, source: FluidViewerSource, size: Int): FluidFileThumbnail? {
  val glyph = fluidApkIcon(context, source, (size * .56f).roundToInt()) ?: return null
  val halo = averageColor(glyph)
  val image = FluidThumbnailPainter.symbol(size) { canvas, side ->
    val edge = glyph.width.toFloat()
    val left = (side - edge) / 2f
    val top = (side - edge) / 2f
    canvas.drawCircle(side / 2f, side / 2f, side * 0.48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = RadialGradient(side / 2f, side / 2f, side * 0.48f,
        intArrayOf(Color.argb(110, Color.red(halo), Color.green(halo), Color.blue(halo)), Color.TRANSPARENT),
        floatArrayOf(0.35f, 1f), Shader.TileMode.CLAMP)
    })
    canvas.drawBitmap(glyph, null, RectF(left, top, left + edge, top + edge), Paint(Paint.FILTER_BITMAP_FLAG))
  }
  return FluidFileThumbnail.of(image, fillsByKind = false)
}

/** Read the actual declared application icon. The page and the thumbnail supply their own shape and halo. */
internal suspend fun fluidApkIcon(context: Context, source: FluidViewerSource, size: Int): Bitmap? =
  withContext(Dispatchers.IO) {
    val direct = when (source) {
      is FluidViewerFileSource -> source.file.takeIf { it.isFile }?.path
      is FluidViewerUriSource -> source.uri.takeIf { it.scheme == "file" }?.path?.takeIf { File(it).isFile }
      else -> null
    }
    val descriptor: ParcelFileDescriptor? = if (direct == null) source.seekable() else null
    try {
      val path = direct ?: "/proc/self/fd/${requireNotNull(descriptor).fd}"
      val pm = context.packageManager
      val info = pm.getPackageArchiveInfo(path, PackageManager.GET_META_DATA) ?: return@withContext null
      val application = info.applicationInfo ?: return@withContext null
      application.sourceDir = path
      application.publicSourceDir = path
      if (application.icon == 0) return@withContext null
      val icon = pm.getDrawable(application.packageName, application.icon, application) ?: return@withContext null
      val edge = size.coerceIn(1, FluidThumbnailPainter.MaxSizePx)
      Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888).also { bitmap ->
        icon.setBounds(0, 0, edge, edge)
        icon.draw(Canvas(bitmap))
      }
    } finally {
      descriptor?.close()
    }
  }

/** Il colore medio dei pixel visibili di [bitmap], su una griglia rada. */
private fun averageColor(bitmap: Bitmap): Int {
  var r = 0L
  var g = 0L
  var b = 0L
  var n = 0L
  val step = max(1, bitmap.width / 24)
  for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) {
    val c = bitmap.getPixel(x, y)
    if (Color.alpha(c) < 128) continue
    r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
  }
  if (n == 0L) return Color.GRAY
  return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}

/** [bitmap] girato di [degrees] e ridotto perché il lato lungo stia in [size]. */
private fun fit(bitmap: Bitmap, size: Int, degrees: Int): Bitmap {
  val scale = minOf(1f, size.toFloat() / max(bitmap.width, bitmap.height))
  if (scale >= 1f && degrees == 0) return bitmap
  val matrix = Matrix().apply {
    postScale(scale, scale)
    if (degrees != 0) postRotate(degrees.toFloat())
  }
  return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
