package dev.antigravity.fluidengine.viewer.graphics

import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.caverock.androidsvg.SVG
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidDecodedImage
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.math.ceil

/** Licenze da aggiungere alla lista di [dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection]. */
val FluidGraphicsViewerLicenses: List<FluidLicense> = listOf(
  FluidLicense("AndroidSVG", "Paul LeBeau", "Apache-2.0", "https://github.com/BigBadaboom/androidsvg",
    "Anteprima vettoriale SVG e SVGZ"),
  FluidLicense("AndroidX ExifInterface", "Android Open Source Project", "Apache-2.0",
    "https://developer.android.com/jetpack/androidx/releases/exifinterface", "Dati della fotocamera nelle foto RAW"),
)

/** Famiglia opt-in delle immagini speciali e dei caratteri, scoperta dal registro dei visori. */
class FluidGraphicsFamily : FluidViewerFamily {
  override val id = "graphics"
  override val types: List<FluidViewerFamilyType>
    get() = listOf(
      imageType("heif", "HEIC / HEIF", setOf("heic", "heif", "heics", "heifs"),
        setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence"),
        bmff("heic", "heix", "hevc", "hevx", "heim", "heis"),
        platformPriority(28)),
      imageType("avif", "AVIF", setOf("avif", "avifs"), setOf("image/avif", "image/avif-sequence"),
        bmff("avif", "avis"), platformPriority(31)),
      imageType("dng", "DNG", setOf("dng"), setOf("image/x-adobe-dng", "image/dng")),
      imageType("cr2", "Canon CR2", setOf("cr2"), setOf("image/x-canon-cr2"),
        FluidViewerSignature.bytes(67, 82, 2, 0, offset = 8)),
      imageType("cr3", "Canon CR3", setOf("cr3"), setOf("image/x-canon-cr3"), bmff("crx ")),
      imageType("nef", "Nikon NEF", setOf("nef"), setOf("image/x-nikon-nef")),
      imageType("arw", "Sony ARW", setOf("arw"), setOf("image/x-sony-arw")),
      imageType("tiff", "TIFF", setOf("tif", "tiff"), setOf("image/tiff", "image/x-tiff"),
        preset = FluidViewerPreset.Pages),
      imageType("psd", "Photoshop", setOf("psd", "psb"), setOf("image/vnd.adobe.photoshop", "image/x-photoshop", "image/psd"),
        FluidViewerSignature.ascii("8BPS")),
      imageType("svg", "SVG", setOf("svg", "svgz"), setOf("image/svg+xml", "image/svg+xml-compressed")),
      fontType("ttf", "TrueType", setOf("font/ttf", "application/x-font-ttf")),
      fontType("otf", "OpenType", setOf("font/otf", "application/x-font-opentype"), "OTTO"),
      fontType("ttc", "Collezione di caratteri", setOf("font/collection"), "ttcf", setOf("ttc", "otc")),
      fontType("woff", "WOFF", setOf("font/woff", "application/font-woff"), "wOFF"),
      fontType("woff2", "WOFF2", setOf("font/woff2", "application/font-woff2"), "wOF2"),
    )

  override suspend fun open(request: FluidViewerRequest): FluidViewerOpened = withContext(Dispatchers.IO) {
    val format = request.type.id
    platformLimitation(format)?.let { reason ->
      val source = request.source
      val info = FluidFileInfo(source.name, source.mimeType, source.sizeBytes, request.type.kind)
      return@withContext FluidViewerOpened(
        FluidViewerContent.Unknown(info, request.openWith, request.share),
        listOf(FluidViewerDetail("Anteprima", reason)),
      )
    }
    when (format) {
      "ttf", "otf", "ttc", "woff" -> openFont(request)
      "svg" -> {
        val painter = readSvg(request)
        FluidViewerOpened(FluidViewerContent.Image(painter, request.source.name), listOf(
          FluidViewerDetail("Formato", if (painter.compressed) "SVGZ" else "SVG"),
          FluidViewerDetail("Dimensioni", "${painter.width} × ${painter.height}"),
          FluidViewerDetail("Resa", "Disegno vettoriale", "Lettura"),
        ))
      }
      "tiff" -> {
        val pages = TiffPages(request.source.open().use { it.readGraphic() }, pixelLimit(request))
        val first = pages.size(0)
        FluidViewerOpened(FluidViewerContent.Pages(pages), listOf(
          FluidViewerDetail("Formato", "TIFF"), FluidViewerDetail("Pagine", pages.count.toString()),
          FluidViewerDetail("Prima pagina", "${first.width} × ${first.height} px", "Immagine"),
        ))
      }
      "psd" -> {
        val image = Photoshop(request.source.open().use { it.readGraphic() })
        val (bitmap, preview) = image.image(pixelLimit(request))
        bitmapViewer(bitmap, request.source.name, image.details(preview))
      }
      "heif", "avif" -> {
        val image = decodePlatform(request, pixelLimit(request))
        FluidViewerOpened(image.content(request.source.name), listOf(FluidViewerDetail("Formato", request.type.label)))
      }
      else -> {
        val raw = CameraRaw(request.source.open().use { it.readGraphic() }, format)
        val platform = decodeDng(request)
        if (platform != null) FluidViewerOpened(platform.content(request.source.name), raw.details(platform = true))
        else {
          val bitmap = raw.preview(pixelLimit(request), null)
            ?: unsupported("Il file RAW non contiene un'anteprima JPEG leggibile")
          bitmapViewer(bitmap, request.source.name, raw.details(platform = false))
        }
      }
    }
  }

  override suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int) = withContext(Dispatchers.IO) {
    if (sizePx <= 0) return@withContext null
    val format = request.type.id
    if (platformLimitation(format) != null) return@withContext null
    val edge = sizePx.coerceAtMost(1024)
    val limit = pixelLimit(request)
    when (format) {
      "tiff" -> {
        val pages = TiffPages(request.source.open().use { it.readGraphic() }, limit)
        val size = pages.size(0)
        val (w, h) = fitRaster(size.width, size.height, edge)
        pages.render(0, androidx.compose.ui.unit.IntSize(w, h))
      }
      "psd" -> Photoshop(request.source.open().use { it.readGraphic() }).image(limit, edge).first.asImageBitmap()
      "svg" -> readSvg(request).thumbnail(edge).asImageBitmap()
      "heif", "avif" -> decodePlatform(request, minOf(limit, edge.toLong() * edge)).still?.let {
        val (w, h) = fitRaster(it.width, it.height, edge)
        Bitmap.createScaledBitmap(it.asAndroidBitmap(), w, h, true).asImageBitmap()
      }
      "dng" -> dngThumbnail(request, edge, limit)
      "cr2", "cr3", "nef", "arw" -> CameraRaw(request.source.open().use { it.readGraphic() }, format)
        .preview(limit, edge)?.asImageBitmap()
      // F6C.17: un carattere mostra «Aa» nel suo disegno, come in File.
      "ttf", "otf", "ttc", "woff" -> fontThumbnail(request, edge)
      else -> null
    }
  }

  private suspend fun openFont(request: FluidViewerRequest): FluidViewerOpened {
    val font = FontFile(request.source.open().use { it.readGraphic(MaxFontBytes) })
    val faces = font.faces
    val format = request.type.id.uppercase(Locale.ROOT)
    val file = File.createTempFile("viewer-font-", ".sfnt", request.context.cacheDir)
    try {
      file.writeBytes(font.bytes)
      val initial = faces.mapIndexed { index, _ ->
        Typeface.Builder(file).setTtcIndex(index).build() ?: corrupt("Il carattere non è leggibile da Android")
      }
      val details = buildList {
        addAll(faces.first().facts(format))
        addAll(faces.first().details())
        if (faces.size > 1) faces.forEachIndexed { index, face ->
          add(FluidViewerDetail("Carattere ${index + 1}", "${face.family} · ${face.style}", "Collezione"))
        }
      }
      return FluidViewerOpened(FluidViewerContent.Custom {
        FontSpecimen(file, faces, initial, format, contentPadding)
      }, details, onClose = { file.delete() })
    } catch (t: Throwable) {
      file.delete()
      throw t
    }
  }

  private fun pixelLimit(request: FluidViewerRequest) = request.options.imageMaxPixels.coerceIn(1L, MaxRasterPixels)

  private suspend fun decodeDng(request: FluidViewerRequest): FluidDecodedImage? {
    if (request.type.id != "dng" || Build.VERSION.SDK_INT < 28) return null
    return try {
      decodePlatform(request, pixelLimit(request))
    } catch (_: FluidViewerException) {
      null
    }
  }

  /** Il descrittore resta aperto durante la decodifica Android, anche per provider e flussi del registro. */
  private suspend fun decodePlatform(request: FluidViewerRequest, max: Long): FluidDecodedImage =
    request.source.seekable().use {
      FluidDecodedImage.decode(File("/proc/self/fd/${it.fd}"), max, animate = false)
    }

  private suspend fun readSvg(request: FluidViewerRequest): SvgPainter {
    val input = request.source.open().use { it.readGraphic(MaxSvgBytes) }
    val compressed = input.size >= 2 && input[0] == 31.toByte() && input[1] == 139.toByte()
    val xml = if (compressed) GZIPInputStream(input.inputStream()).use { it.readGraphic(MaxSvgBytes) } else input
    // Il visore disegna solo il documento. Non passa a parser o resolver dichiarazioni DTD o entità.
    val header = String(xml, Charsets.ISO_8859_1).replace("\u0000", "")
    if (header.contains("<!DOCTYPE", ignoreCase = true) || header.contains("<!ENTITY", ignoreCase = true))
      unsupported("Il documento SVG contiene dichiarazioni XML non supportate")
    val svg = SVG.getFromInputStream(xml.inputStream())
    val viewBox = svg.documentViewBox
    val width = svg.documentWidth.takeIf { it.isFinite() && it > 0f }
    val height = svg.documentHeight.takeIf { it.isFinite() && it > 0f }
    val ratio = svg.documentAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val w = width ?: height?.times(ratio) ?: viewBox?.width()?.takeIf { it.isFinite() && it > 0f } ?: 300f
    val h = height ?: (w / ratio)
    return SvgPainter(svg, w.coerceIn(1f, 100_000f), h.coerceIn(1f, 100_000f), compressed)
  }

  private companion object {
    const val MaxSvgBytes = 8 * 1024 * 1024
    fun platformPriority(min: Int) = if (Build.VERSION.SDK_INT >= min) -10 else 20

    fun bmff(vararg brands: String) = FluidViewerSignature.anyOf(*brands.map {
      FluidViewerSignature.ascii("ftyp$it", offset = 4)
    }.toTypedArray())

    fun fontType(id: String, label: String, mimes: Set<String>, magic: String? = null, extensions: Set<String> = setOf(id)) =
      FluidViewerFamilyType(id, label, extensions, mimes, magic?.let { FluidViewerSignature.ascii(it) }, priority = 10)

    fun imageType(id: String, label: String, extensions: Set<String>, mimes: Set<String>, magic: FluidViewerSignature? = null,
      priority: Int = 10, preset: FluidViewerPreset = FluidViewerPreset.Image) =
      FluidViewerFamilyType(id, label, extensions, mimes, magic, FluidFileKind.Image, priority, preset)
  }
}

private fun platformLimitation(format: String): String? = when {
  format == "heif" && Build.VERSION.SDK_INT < 28 -> "Per HEIC e HEIF serve Android 9 o successivo"
  format == "avif" && Build.VERSION.SDK_INT < 31 -> "Per AVIF serve Android 12 o successivo"
  format == "woff2" -> "La compressione dei caratteri WOFF2 non è supportata"
  else -> null
}

private fun bitmapViewer(bitmap: Bitmap, name: String, details: List<FluidViewerDetail>) =
  FluidViewerOpened(FluidViewerContent.Image(BitmapPainter(bitmap.asImageBitmap()), name),
    details + FluidViewerDetail("Anteprima in pixel", "${bitmap.width} × ${bitmap.height}", "Immagine"))

private class SvgPainter(svg: SVG, val width: Float, val height: Float, val compressed: Boolean) : Painter() {
  override val intrinsicSize = Size(width, height)
  private val picture = svg.renderToPicture(ceil(width).toInt(), ceil(height).toInt())

  override fun DrawScope.onDraw() = drawIntoCanvas {
    it.nativeCanvas.drawPicture(picture, android.graphics.RectF(0f, 0f, size.width, size.height))
  }

  fun thumbnail(edge: Int): Bitmap {
    val (w, h) = fitRaster(width.toInt(), height.toInt(), edge)
    return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
      android.graphics.Canvas(it).drawPicture(picture, android.graphics.Rect(0, 0, w, h))
    }
  }
}
