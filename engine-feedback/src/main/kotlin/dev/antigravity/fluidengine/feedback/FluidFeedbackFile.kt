package dev.antigravity.fluidengine.feedback

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.antigravity.fluidengine.diagnostics.FluidPrivacyScrubber
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Il file di una segnalazione (F8.24b, scelta di Alessio): **uno ZIP** da mandare su WhatsApp come documento, col foglio
 * Condividi dell'engine. Dentro, nell'ordine:
 * - `LEGGIMI.txt`, il primo, da leggere senza programmi: la frase, l'app e il telefono, le funzioni in prova, gli ultimi
 *   arresti, i ringraziamenti, e cosa c'è negli altri file;
 * - `segnalazione.json`, gli stessi dati per i programmi (lo schema [JsonSchema]);
 * - `screenshot.png`, lo screenshot segnato, se c'è e se è acceso;
 * - `registro.txt`, gli ultimi eventi dell'app, se acceso.
 *
 * I dati personali sono tolti **prima di entrare nello ZIP**: il registro e gli arresti escono già puliti da
 * [FluidFeedbackInfo.collect], e la frase e il nome passano qui da [FluidPrivacyScrubber]. Le parti spente non ci sono, in
 * nessun file.
 */
object FluidFeedbackFile {
  /** Il tipo dello ZIP: WhatsApp lo manda come documento. */
  const val MimeType: String = "application/zip"

  /** La versione di `segnalazione.json`. */
  const val JsonSchema: Int = 1

  /** I nomi dei file dentro lo ZIP. */
  object Names {
    const val Readme: String = "LEGGIMI.txt"
    const val Json: String = "segnalazione.json"
    const val Screenshot: String = "screenshot.png"
    const val Log: String = "registro.txt"
  }

  /**
   * I file dello ZIP, nell'ordine in cui entrano (`LEGGIMI.txt` per primo), ognuno coi suoi byte. Puro: lo provano i test
   * senza scrivere niente. [screenshot] sono i byte del PNG segnato (nulla per nessuno).
   */
  fun entries(
    draft: FluidFeedbackDraft,
    info: FluidFeedbackInfo,
    screenshot: ByteArray?,
    labels: FluidFeedbackLabels = FluidFeedbackLabels(),
    locale: Locale = Locale.getDefault(),
  ): List<Pair<String, ByteArray>> {
    val withShot = screenshot != null && draft.includeScreenshot
    val withLog = draft.includeLog && info.log.isNotEmpty()
    val out = ArrayList<Pair<String, ByteArray>>()
    out += Names.Readme to readme(draft, info, labels, locale, withShot, withLog).toByteArray(Charsets.UTF_8)
    out += Names.Json to json(draft, info, withShot).toByteArray(Charsets.UTF_8)
    if (withShot) out += Names.Screenshot to screenshot!!
    if (withLog) out += Names.Log to (info.log.joinToString("\r\n") + "\r\n").toByteArray(Charsets.UTF_8)
    return out
  }

  /** Il testo di `LEGGIMI.txt`, coi fine riga di Windows (si apre bene anche nel Blocco note). */
  fun readme(
    draft: FluidFeedbackDraft,
    info: FluidFeedbackInfo,
    labels: FluidFeedbackLabels = FluidFeedbackLabels(),
    locale: Locale = Locale.getDefault(),
    withScreenshot: Boolean = draft.attachedScreenshot != null,
    withLog: Boolean = draft.includeLog && info.log.isNotEmpty(),
  ): String {
    val created = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT, locale).format(Date(info.createdAtMillis))
    val lines = ArrayList<String>()
    // R3 · Con «L'app e il telefono» spento resta solo il nome dell'app (di che app è la segnalazione), senza versione.
    lines += "${labels.fileTitle} · ${info.appName}" + if (draft.includeDevice) " ${info.appVersion}" else ""
    lines += created
    lines += ""
    lines += labels.whatsWrong.uppercase(locale)
    lines += FluidPrivacyScrubber.scrub(draft.description.trim()).ifBlank { labels.noDescription }
    if (draft.includeDevice) {
      lines += ""
      lines += labels.where.uppercase(locale)
      info.deviceRows(labels).forEach { (label, value) -> lines += "$label: $value" }
    }
    if (draft.includeExperiments && info.experiments.isNotEmpty()) {
      lines += ""
      lines += labels.experiments.uppercase(locale)
      info.experiments.forEach { lines += "- $it" }
    }
    if (draft.includeCrashes && info.crashes.isNotEmpty()) {
      lines += ""
      lines += labels.crashes.uppercase(locale)
      info.crashes.forEach { lines += "- $it" }
    }
    val credit = FluidPrivacyScrubber.scrub(draft.credit.trim())
    if (credit.isNotBlank()) {
      lines += ""
      lines += labels.fileCredit(credit)
    }
    lines += ""
    lines += labels.readmeContents.uppercase(locale)
    lines += labels.readmeJson(Names.Json)
    if (withScreenshot) lines += labels.readmeScreenshot(Names.Screenshot)
    if (withLog) lines += labels.readmeLog(Names.Log, info.log.size)
    lines += ""
    lines += labels.readmePrivacy
    return lines.joinToString("\r\n") + "\r\n"
  }

  /**
   * Il testo di `segnalazione.json`: gli stessi dati di `LEGGIMI.txt`, per i programmi. Le parti spente non ci sono:
   * con «L'app e il telefono» spento `app` ha solo `name` (R3).
   */
  fun json(draft: FluidFeedbackDraft, info: FluidFeedbackInfo, withScreenshot: Boolean): String = buildJsonObject {
    put("schema", JsonSchema)
    put("createdAt", info.createdAtMillis)
    putJsonObject("app") {
      put("name", info.appName)
      if (draft.includeDevice) {
        put("id", info.appId)
        put("version", info.appVersion)
        put("code", info.versionCode)
        put("build", info.buildType)
        put("engine", info.engineVersion)
      }
    }
    put("description", FluidPrivacyScrubber.scrub(draft.description.trim()))
    if (draft.includeDevice) {
      putJsonObject("device") {
        put("phone", info.device)
        put("android", info.android)
        put("language", info.language)
        put("screen", info.screen)
        put("appearance", info.appearance)
      }
    }
    if (draft.includeExperiments) put("experiments", JsonArray(info.experiments.map { JsonPrimitive(it) }))
    if (draft.includeCrashes) put("crashes", JsonArray(info.crashes.map { JsonPrimitive(it) }))
    if (draft.includeLog) put("log", JsonArray(info.log.map { JsonPrimitive(it) }))
    put("screenshot", if (withScreenshot) JsonPrimitive(Names.Screenshot) else JsonNull)
    FluidPrivacyScrubber.scrub(draft.credit.trim()).takeIf { it.isNotBlank() }?.let { put("credit", it) }
  }.toString()

  /**
   * Scrive lo ZIP con [entries] in [out]. Le voci hanno l'ora della segnalazione ([timeMillis]): lo stesso ZIP due volte
   * ha gli stessi byte. Da un filo in sottofondo.
   */
  fun writeZip(out: OutputStream, entries: List<Pair<String, ByteArray>>, timeMillis: Long) {
    ZipOutputStream(out).use { zip ->
      for ((name, bytes) in entries) {
        val entry = ZipEntry(name)
        entry.time = timeMillis
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
      }
    }
  }

  /**
   * Lo ZIP della segnalazione in un file nuovo della cartella delle uscite di engine-ui (quella del foglio Condividi), col
   * nome chiaro `Segnalazione-<app>-<AAAAMMGG-hhmm>.zip`. Legge lo screenshot segnato dal suo file. Su `Dispatchers.IO`.
   */
  suspend fun writeToFile(
    context: Context,
    draft: FluidFeedbackDraft,
    info: FluidFeedbackInfo,
    labels: FluidFeedbackLabels = FluidFeedbackLabels(),
    locale: Locale = Locale.getDefault(),
  ): File = withContext(Dispatchers.IO) {
    val shot = draft.attachedScreenshot?.readBytes()
    val file = FluidShareFiles.newFile(context, FluidFeedbackText.fileName(info, "zip"))
    file.outputStream().buffered().use { writeZip(it, entries(draft, info, shot, labels, locale), info.createdAtMillis) }
    file
  }

  /** Lo screenshot di [file] per la miniatura del foglio, ridotto a [maxSide] pixel. Da un filo in sottofondo. */
  fun loadScreenshot(file: File, maxSide: Int = 1600): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return bitmap.asImageBitmap()
  }

  /**
   * Copia lo screenshot nella cartella Download del telefono col nome [name], per allegarlo alla issue di GitHub dal
   * browser. Android 10+, senza permessi (MediaStore). Torna l'Uri, o nulla sui telefoni più vecchi.
   */
  suspend fun saveToDownloads(context: Context, file: File, name: String): Uri? = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < 29) return@withContext null
    val resolver = context.contentResolver
    val values = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, name)
      put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
      put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
      put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
    try {
      resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IOException("Download non scrivibile")
      resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
      uri
    } catch (e: Exception) {
      resolver.delete(uri, null, null)
      null
    }
  }

  /** La cartella dei file di lavoro delle segnalazioni, nella cache dell'app (Android la svuota se serve spazio). */
  fun workDir(context: Context): File = File(context.cacheDir, WorkDirName).apply { mkdirs() }

  private const val WorkDirName = "fluid-feedback"

  /**
   * R3 · Quando questo processo ha toccato per la prima volta la cartella di lavoro: ogni file che ci scrive nasce dopo
   * ([workDir] passa da qui), quindi uno più vecchio è di un processo di prima.
   */
  private val processStartMillis = System.currentTimeMillis()

  /** R3 · Cancella [file] se è un file della cartella di lavoro (lo screenshot di una bozza dimenticata). */
  internal fun deleteWorkFile(file: File) {
    if (file.parentFile?.name == WorkDirName) runCatching { file.delete() }
  }

  /**
   * R3 · Cancella i file di lavoro di un processo di prima: uno screenshot scritto e poi rimasto lì perché il processo
   * è stato ucciso prima che la bozza partisse o si dimenticasse. Quelli di questo processo restano (una bozza aperta
   * li usa). Da un filo in sottofondo.
   */
  internal fun deleteOrphanWorkFiles(context: Context) {
    val files = File(context.cacheDir, WorkDirName).listFiles() ?: return
    for (file in files) {
      if (file.isFile && file.lastModified() < processStartMillis) runCatching { file.delete() }
    }
  }
}

/**
 * Lo screenshot di una finestra dell'app (F8.24): `PixelCopy` di ciò che la finestra mostra, con il vetro e tutto,
 * senza permessi e senza vedere le altre app. Android 8+.
 */
object FluidFeedbackCapture {
  /** Copia i pixel della finestra di [activity] in una bitmap. Dal thread principale; la copia la fa il sistema. */
  suspend fun window(activity: Activity): Bitmap {
    val window = activity.window ?: throw IOException("Nessuna finestra")
    val view = window.decorView
    if (view.width <= 0 || view.height <= 0) throw IOException("Finestra senza misura")
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    return suspendCancellableCoroutine { continuation ->
      PixelCopy.request(window, bitmap, { result ->
        if (result == PixelCopy.SUCCESS) continuation.resume(bitmap) else continuation.resumeWithException(IOException("PixelCopy: $result"))
      }, Handler(Looper.getMainLooper()))
    }
  }

  /** Scrive [bitmap] in PNG in [file]. Da un filo in sottofondo. */
  fun writePng(bitmap: Bitmap, file: File) {
    file.parentFile?.mkdirs()
    file.outputStream().buffered().use { out ->
      if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG non scritto")
    }
  }
}
