package dev.antigravity.fluidengine.editor

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Il salvataggio: la foto modificata come **file nuovo**, a piena risoluzione, fuori dal thread principale. Il file di
 * partenza non si tocca; dove salvare (accanto all'originale, una copia nella galleria, la cache per un allegato) lo
 * decide l'app.
 *
 * **Come**, per non tenere mai la foto intera due volte in memoria (la scheda dell'idea: «foto da 50 megapixel non stanno
 * in memoria»):
 * 1. si legge solo la parte del file che il ritaglio usa (`BitmapRegionDecoder`), **a strisce**, e ogni striscia si
 *    disegna girata e ritagliata nel file che esce;
 * 2. le regolazioni si calcolano **a fasce** di righe, divise fra i processori, con lo stesso codice dell'anteprima;
 * 3. se il ritaglio supera [defaultMaxPixels] (secondo la memoria del telefono) esce ridotto, e [Result.reduced] lo dice.
 *
 * **Cosa resta del file di partenza**: il profilo colore (una foto Display P3 esce P3), i metadati di scatto (data, ora,
 * fotocamera, obiettivo, esposizione) e la posizione (di serie si', come Foto di iOS; `keepLocation = false` la toglie,
 * R3). Il verso dell'EXIF si scrive «normale», perche' la rotazione e' gia' nei pixel. **Non resta** l'HDR delle foto
 * Ultra HDR (la mappa di guadagno): il file esce SDR
 * (domanda per Alessio nel rapporto di F8.15).
 */
object FluidImageExport {

  /** Il formato del file che esce. */
  class Format private constructor(internal val compress: Bitmap.CompressFormat, val quality: Int, val mimeType: String) {
    companion object {
      /** JPEG, qualita' 95 di serie (quella di Foto, a occhio). */
      fun jpeg(quality: Int = 95) = Format(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), "image/jpeg")
      /** PNG, senza perdite (le schermate, i disegni). */
      val Png = Format(Bitmap.CompressFormat.PNG, 100, "image/png")
      /** WebP senza perdite, da Android 11; prima, WebP alla qualita' 100. */
      val WebpLossless = Format(
        if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP,
        100, "image/webp",
      )
    }
  }

  /**
   * Com'e' uscito il file: le misure, se e' stato ridotto per la memoria ([reduced]), il peso e il tipo.
   */
  data class Result(val width: Int, val height: Int, val reduced: Boolean, val bytes: Long, val mimeType: String)

  /** Il tetto di pixel del file che esce, secondo la memoria del telefono: 12, 24 o 50 megapixel. */
  fun defaultMaxPixels(context: Context): Long {
    val am = context.getSystemService(ActivityManager::class.java)
    val info = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
    val gb = info.totalMem / (1024.0 * 1024 * 1024)
    return when {
      am?.isLowRamDevice == true || gb in 0.1..3.5 -> 12_000_000L
      gb < 6.5 -> 24_000_000L
      else -> 50_000_000L
    }
  }

  /**
   * R3 · Se la copia tiene la posizione GPS dello scatto quando chi chiama non lo dice: **di serie sì**, come Foto di iOS
   * (scelta di Alessio, R3-M2b). Vale per tutti gli `export`, anche quelli dello stato dell'editor. La persona la toglie
   * dal foglio di condivisione (l'opzione «Posizione», verificata sul file); chi manda la copia senza il foglio passa
   * `keepLocation = false`.
   */
  internal const val KeepLocationByDefault: Boolean = true

  /**
   * Salva la foto [source] modificata da [edit] nel file [target] (sovrascritto solo a salvataggio riuscito).
   *
   * Il file si scrive accanto a [target] e prende il suo posto solo se il codificatore l'ha scritto per intero: con il
   * disco pieno (`Bitmap.compress` falso) lancia `IOException` e [target] resta com'era (R3).
   *
   * @param keepLocation se la posizione GPS dello scatto resta nella copia: di serie **sì**, come Foto di iOS (R3);
   *   `false` la toglie, campo GPS per campo GPS (un allegato che parte senza il foglio di condivisione).
   */
  suspend fun export(
    context: Context,
    source: FluidEditorSource,
    edit: FluidImageEdit,
    target: File,
    format: Format = Format.jpeg(),
    maxPixels: Long = defaultMaxPixels(context),
    keepLocation: Boolean = KeepLocationByDefault,
  ): Result = withContext(Dispatchers.Default) {
    val rendered = renderFull(context, source, edit, maxPixels)
    val temp = File(target.absoluteFile.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
    try {
      val written = try {
        FileOutputStream(temp).use { rendered.bitmap.compress(format.compress, format.quality, it) }
      } finally {
        rendered.bitmap.recycle()
      }
      // R3: `compress` dice falso (non lancia) quando lo scrittore fallisce a meta': prima usciva un successo col file tronco.
      if (!written) throw IOException("La foto non si e' scritta per intero (il disco e' pieno?): ${target.name}")
      source.uri?.let { copyMetadata(context, it, temp, keepLocation, rendered.width, rendered.height) }
      replace(temp, target)
    } finally {
      temp.delete()
    }
    Result(rendered.width, rendered.height, rendered.reduced, target.length(), format.mimeType)
  }

  /**
   * Salva in un indirizzo dell'app ([target], aperto in scrittura: un file del MediaStore appena creato, un documento
   * scelto con «Salva come»). Passa da un file nella cache, per poter scrivere i metadati.
   */
  suspend fun export(
    context: Context,
    source: FluidEditorSource,
    edit: FluidImageEdit,
    target: Uri,
    format: Format = Format.jpeg(),
    maxPixels: Long = defaultMaxPixels(context),
    keepLocation: Boolean = KeepLocationByDefault,
  ): Result = withContext(Dispatchers.IO) {
    val temp = File.createTempFile("fluid-edit", ".tmp", context.cacheDir)
    try {
      val result = export(context, source, edit, temp, format, maxPixels, keepLocation)
      val out = context.contentResolver.openOutputStream(target, "wt") ?: error("Impossibile scrivere in $target")
      out.use { o -> temp.inputStream().use { it.copyTo(o) } }
      result
    } finally {
      temp.delete()
    }
  }

  /**
   * La foto modificata in memoria, col lato lungo al piu' [maxSide]: per passarla a un'altra fase senza un file (il
   * disegno di `engine-ink`, un'anteprima di condivisione). La bitmap e' di chi chiama.
   */
  suspend fun render(context: Context, source: FluidEditorSource, edit: FluidImageEdit, maxSide: Int): Bitmap =
    withContext(Dispatchers.Default) {
      renderFull(context, source, edit, maxPixels = maxSide.toLong() * maxSide, maxSide = maxSide).bitmap
    }

  internal class Rendered(val bitmap: Bitmap, val width: Int, val height: Int, val reduced: Boolean)

  internal suspend fun renderFull(
    context: Context, source: FluidEditorSource, edit: FluidImageEdit, maxPixels: Long, maxSide: Int = Int.MAX_VALUE,
  ): Rendered {
    val g = edit.geometry
    val info: SourceInfo
    val inMemory = source.bitmap
    info = if (inMemory != null) SourceInfo(inMemory.width, inMemory.height, ExifInterface.ORIENTATION_NORMAL, null)
      else EditorDecode.info(context, source.uri!!)
    val sw = info.width
    val sh = info.height
    val (outW, outH) = g.outputSize(sw, sh)
    val k = min(1.0, min(sqrt(maxPixels.toDouble() / (outW.toDouble() * outH)), maxSide.toDouble() / max(outW, outH)))
    val finalW = max(1, (outW * k).roundToInt())
    val finalH = max(1, (outH * k).roundToInt())
    val scale = FluidAffine(finalW.toFloat() / outW, 0f, 0f, finalH.toFloat() / outH, 0f, 0f)
    // Dai pixel del file ai pixel del risultato.
    val rawToFinal = scale.then(g.sourceToOutput(sw, sh)).then(info.exif(info.rawWidth.toFloat(), info.rawHeight.toFloat()))
    val out = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888, true, outputColorSpace(context, source))
    val canvas = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    if (inMemory != null) {
      canvas.drawBitmap(inMemory, Matrix().apply { setValues(rawToFinal.toMatrixValues()) }, paint)
    } else {
      drawStrips(context, source.uri!!, info, rawToFinal, finalW, finalH, k, canvas, paint)
    }
    adjust(context, source, info, edit, out, scale.then(g.sourceToOutput(sw, sh)))
    return Rendered(out, finalW, finalH, k < 1.0)
  }

  /** Legge dal file solo la parte che serve, a strisce, e la disegna girata nel risultato. */
  private fun drawStrips(
    context: Context, uri: Uri, info: SourceInfo, rawToFinal: FluidAffine, finalW: Int, finalH: Int, k: Double,
    canvas: Canvas, paint: Paint,
  ) {
    val finalToRaw = rawToFinal.inverted()
    val xs = floatArrayOf(0f, finalW.toFloat(), 0f, finalW.toFloat())
    val ys = floatArrayOf(0f, 0f, finalH.toFloat(), finalH.toFloat())
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (i in 0 until 4) {
      val x = finalToRaw.mapX(xs[i], ys[i])
      val y = finalToRaw.mapY(xs[i], ys[i])
      left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
    }
    val region = Rect(
      max(0, floor(left).toInt() - 2), max(0, floor(top).toInt() - 2),
      min(info.rawWidth, ceil(right).toInt() + 2), min(info.rawHeight, ceil(bottom).toInt() + 2),
    )
    if (region.isEmpty) return
    var sample = 1
    while (sample * 2 <= 1.0 / k) sample *= 2
    val decoder = runCatching {
      EditorDecode.open(context, uri).use { stream ->
        if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(stream)
        else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(stream, false)
      }
    }.getOrNull()
    if (decoder == null) {
      // Un formato che non si legge a pezzi (HEIF prima di Android 9, GIF): tutto insieme, ridotto quanto basta.
      val decoded = EditorDecode.decode(context, uri, sample) ?: error("Impossibile leggere la foto: $uri")
      val m = if (decoded.oriented) {
        // ImageDecoder l'ha gia' girata: dai suoi pixel a quelli dritti del file, poi come sopra.
        rawToFinal.then(info.exif(info.rawWidth.toFloat(), info.rawHeight.toFloat()).inverted())
          .then(FluidAffine(info.width.toFloat() / decoded.bitmap.width, 0f, 0f, info.height.toFloat() / decoded.bitmap.height, 0f, 0f))
      } else {
        rawToFinal.then(FluidAffine(info.rawWidth.toFloat() / decoded.bitmap.width, 0f, 0f,
          info.rawHeight.toFloat() / decoded.bitmap.height, 0f, 0f))
      }
      canvas.drawBitmap(decoded.bitmap, Matrix().apply { setValues(m.toMatrixValues()) }, paint)
      decoded.bitmap.recycle()
      return
    }
    try {
      val decodedWidth = ceil(region.width().toDouble() / sample).toInt()
      val stripRows = max(16, stripPixels / max(1, decodedWidth)) * sample
      val overlap = 2 * sample
      var y0 = region.top
      val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
      lastStrips = 0
      while (y0 < region.bottom) {
        lastStrips++
        val y1 = min(region.bottom, y0 + stripRows)
        val rect = Rect(region.left, max(region.top, y0 - overlap), region.right, min(region.bottom, y1 + overlap))
        val strip = decoder.decodeRegion(rect, opts) ?: error("Striscia illeggibile: $rect")
        val sx = rect.width().toFloat() / strip.width
        val sy = rect.height().toFloat() / strip.height
        val m = rawToFinal.then(FluidAffine(sx, 0f, 0f, sy, rect.left.toFloat(), rect.top.toFloat()))
        canvas.save()
        canvas.concat(Matrix().apply { setValues(m.toMatrixValues()) })
        // Solo le righe di questa striscia: le sovrapposte servono al filtro, non si disegnano due volte.
        canvas.clipRect(0f, (y0 - rect.top) / sy, strip.width.toFloat(), (y1 - rect.top) / sy)
        canvas.drawBitmap(strip, 0f, 0f, paint)
        canvas.restore()
        strip.recycle()
        y0 = y1
      }
    } finally {
      decoder.recycle()
    }
  }

  /** Le regolazioni sul risultato, a fasce di righe. [sourceToFinal] va dai pixel della foto dritta al risultato. */
  private suspend fun adjust(
    context: Context, source: FluidEditorSource, info: SourceInfo, edit: FluidImageEdit, out: Bitmap,
    sourceToFinal: FluidAffine,
  ) {
    if (edit.adjustments.isNeutral) return
    val plan = AdjustPlan(edit.adjustments)
    val w = out.width
    val h = out.height
    val map = if (plan.definition != 0f) definitionMap(context, source, info) else null
    val toMap = map?.let {
      FluidAffine(it.width.toFloat() / info.width, 0f, 0f, it.height.toFloat() / info.height, 0f, 0f).then(sourceToFinal.inverted())
    }
    val radius = AdjustCurves.sharpRadius(min(w, h))
    val margin = if (plan.needsNeighbours) radius else 0
    var y0 = 0
    while (y0 < h) {
      val y1 = min(h, y0 + BandRows)
      val top = max(0, y0 - margin)
      val bottom = min(h, y1 + margin)
      val rows = bottom - top
      val src = IntArray(w * rows)
      out.getPixels(src, 0, w, 0, top, w, rows)
      val dst = src.copyOf()
      val ctx = AdjustContext(
        toOutput = FluidAffine(1f / w, 0f, 0f, 1f / h, 0f, 0f),
        toMap = toMap,
        map = map,
        sharpRadius = radius,
        rowOffset = top,
      )
      EditorCompute.process(src, w, rows, plan, ctx, dst, (y0 - top) until (y1 - top))
      out.setPixels(dst, (y0 - top) * w, w, 0, y0, w, y1 - y0)
      y0 = y1
    }
  }

  /** La mappa della definizione da una lettura ridotta del file intero (≈ 512 pixel sul lato lungo). */
  private fun definitionMap(context: Context, source: FluidEditorSource, info: SourceInfo): DefinitionMap {
    val image = EditorImage.load(context, source, maxSide = 512)
    return image.definition.also { if (image.preview !== source.bitmap) image.preview.recycle() }
  }

  private fun outputColorSpace(context: Context, source: FluidEditorSource): ColorSpace {
    source.bitmap?.colorSpace?.let { return it }
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { EditorDecode.open(context, source.uri!!).use { BitmapFactory.decodeStream(it, null, opts) } }
    return opts.outColorSpace?.takeIf { it.model == ColorSpace.Model.RGB } ?: ColorSpace.get(ColorSpace.Named.SRGB)
  }

  /** I metadati di scatto che restano (mai il verso, mai le misure vecchie, la posizione solo se [keepLocation]). */
  private fun copyMetadata(context: Context, uri: Uri, target: File, keepLocation: Boolean, w: Int, h: Int) {
    runCatching {
      val from = EditorDecode.open(context, uri).use { ExifInterface(it) }
      val to = ExifInterface(target.absolutePath)
      val tags = KeptTags + if (keepLocation) LocationTags else emptyList()
      for (tag in tags) from.getAttribute(tag)?.let { to.setAttribute(tag, it) }
      to.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
      to.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, w.toString())
      to.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, h.toString())
      to.saveAttributes()
    }
  }

  /**
   * Mette [from] al posto di [to] in un colpo (sul telefono un `rename`). Sulla JVM di Windows delle prove un file appena
   * scritto puo' restare aperto un attimo da altri (l'antivirus): si riprova per mezzo secondo, come lo scanner.
   */
  private fun replace(from: File, to: File) {
    var tries = 0
    while (true) {
      try {
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return
      } catch (missing: java.nio.file.NoSuchFileException) {
        throw missing
      } catch (busy: java.nio.file.FileSystemException) {
        if (++tries >= 20) throw busy
        Thread.sleep(25)
      }
    }
  }

  /** Quanti pixel decodificati per striscia (4 megapixel; le prove lo abbassano per avere tante strisce). */
  @Volatile internal var stripPixels = 4_000_000
  private const val BandRows = 256

  /** Quante strisce ha letto l'ultimo salvataggio (per le prove). */
  @Volatile internal var lastStrips = 0

  private val KeptTags = listOf(
    ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, "LensMake", "LensModel",
    ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, "PhotographicSensitivity", "ISOSpeedRatings",
    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_FLASH,
    ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
  )

  private val LocationTags = listOf(
    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
  )
}
