/*
 * engine-scan (4.0, F8.17): lo stato di una scansione. Le pagine stanno sul disco (la foto e la pagina pulita, in JPEG)
 * e in memoria solo le miniature: dieci pagine non pesano 170 MB. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/**
 * Una pagina della fila: la foto scattata ([photo], sul disco), il foglio dentro la foto ([quad]), quanti quarti di giro
 * ([quarterTurns]) e il filtro. La pagina pulita si ricalcola quando cambia uno dei tre.
 */
@Immutable
data class FluidScanPage(
  val id: String,
  val quad: FluidScanQuad,
  val quarterTurns: Int = 0,
  val filter: FluidScanFilter = FluidScanFilter.Document,
  /** La foto intera, come è arrivata dalla fotocamera. */
  val photo: File,
  /** Larghezza e altezza della foto, in pixel. */
  val photoWidth: Int,
  val photoHeight: Int,
)

/** Le scelte dello scanner. */
@Immutable
data class FluidScanOptions(
  /** Cosa consegna «Salva». */
  val output: FluidScanOutput = FluidScanOutput.Pdf,
  /** Il filtro delle pagine nuove. */
  val filter: FluidScanFilter = FluidScanFilter.Document,
  /** Lo scatto da solo quando il foglio è fermo (la modalità «Auto» di VisionKit). */
  val autoCapture: Boolean = true,
  /** Il lato lungo di una pagina pulita, in pixel. */
  val maxLongSide: Int = FluidScanImages.DefaultMaxLongSide,
  /** La qualità JPEG delle pagine (e del PDF). 85: a occhio, il testo resta netto e una pagina A4 pesa 300–600 KB. */
  val jpegQuality: Int = 85,
  /** Il tetto delle pagine di una scansione; VisionKit non ne ha, ma la cache del telefono sì. */
  val maxPages: Int = 100,
  /** Il nome del documento consegnato; di serie «Scansione» con la data e l'ora, come Note. */
  val title: String? = null,
)

/**
 * Lo stato di una scansione: la fila delle pagine, le loro miniature e la consegna. Si crea con
 * [rememberFluidScanState]; le pagine sopravvivono alla rotazione e alla ricreazione dell'attività (sono sul disco).
 * Tutto ciò che legge Compose è stato di snapshot, scritto sul filo principale.
 */
@Stable
class FluidScanState internal constructor(
  private val context: Context,
  private val scope: CoroutineScope,
  val options: FluidScanOptions,
  internal val session: String,
) {
  private val list = mutableStateListOf<FluidScanPage>()
  private val thumbs = mutableStateMapOf<String, ImageBitmap>()
  private val versions = HashMap<String, Int>()
  private val jobs = HashMap<String, Job>()
  private val disk = Mutex()

  /** Le pagine, nell'ordine della fila. */
  val pages: List<FluidScanPage> get() = list

  /** Vero mentre una pagina si sta pulendo o la consegna si sta scrivendo. */
  var working by mutableStateOf(false)
    private set

  /** La miniatura pulita della pagina [id] (lato lungo 480 px), `null` finché non è pronta. */
  fun thumbnail(id: String): ImageBitmap? = thumbs[id]

  /** La cartella di questa scansione nella cache dell'app. */
  internal val folder: File get() = File(ScanFiles.root(context), session).apply { mkdirs() }

  /**
   * Aggiunge in coda la foto [photo] col foglio [quad] (quello trovato, o tutta la foto). La foto va sul disco e la
   * pagina pulita si calcola fuori dal filo principale; la pagina è nella fila subito, con la miniatura che arriva dopo.
   * Restituisce la pagina, o `null` se la fila è piena.
   */
  suspend fun add(photo: Bitmap, quad: FluidScanQuad? = null): FluidScanPage? {
    if (list.size >= options.maxPages) return null
    val id = UUID.randomUUID().toString().take(8)
    val file = File(folder, "foto-$id.jpg")
    val width = photo.width
    val height = photo.height
    withContext(Dispatchers.IO) {
      disk.withLock { FileOutputStream(file).use { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) } }
    }
    val found = quad ?: withContext(Dispatchers.Default) { FluidScanEdges.detect(photo)?.quad } ?: FluidScanQuad.Full
    val page = FluidScanPage(id, found, 0, options.filter, file, width, height)
    list.add(page)
    process(page, photo)
    return page
  }

  /** Toglie la pagina [id]. */
  fun remove(id: String) {
    val index = list.indexOfFirst { it.id == id }
    if (index < 0) return
    jobs.remove(id)?.cancel()
    val page = list.removeAt(index)
    thumbs.remove(id)
    scope.launch(Dispatchers.IO) { page.photo.delete(); cleanFile(id).delete() }
  }

  /** Sposta la pagina da [from] a [to] (il riordino col dito o con TalkBack). */
  fun move(from: Int, to: Int) {
    if (from !in list.indices || to !in list.indices || from == to) return
    val page = list.removeAt(from)
    list.add(to, page)
  }

  /** Cambia il foglio della pagina [id] (il ritaglio a mano). */
  fun setQuad(id: String, quad: FluidScanQuad) = update(id) { it.copy(quad = quad) }

  /** Gira la pagina [id] di un quarto in senso orario. */
  fun rotate(id: String) = update(id) { it.copy(quarterTurns = (it.quarterTurns + 1).mod(4)) }

  /** Il filtro della pagina [id]. */
  fun setFilter(id: String, filter: FluidScanFilter) = update(id) { it.copy(filter = filter) }

  /** Il filtro di tutte le pagine (il menu dei filtri sulla fotocamera vale per tutte, come VisionKit). */
  fun setFilterForAll(filter: FluidScanFilter) {
    for (page in list.toList()) setFilter(page.id, filter)
  }

  /** Rifà la pagina [id] con una foto nuova (il «Riprendi» di VisionKit): stessa posizione nella fila. */
  suspend fun retake(id: String, photo: Bitmap, quad: FluidScanQuad? = null) {
    val index = list.indexOfFirst { it.id == id }
    if (index < 0) return
    val old = list[index]
    withContext(Dispatchers.IO) { disk.withLock { FileOutputStream(old.photo).use { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) } } }
    val found = quad ?: withContext(Dispatchers.Default) { FluidScanEdges.detect(photo)?.quad } ?: FluidScanQuad.Full
    val page = old.copy(quad = found, quarterTurns = 0, photoWidth = photo.width, photoHeight = photo.height)
    list[index] = page
    process(page, photo)
  }

  private fun update(id: String, change: (FluidScanPage) -> FluidScanPage) {
    val index = list.indexOfFirst { it.id == id }
    if (index < 0) return
    val page = change(list[index])
    if (page == list[index]) return
    list[index] = page
    process(page, null)
  }

  /** Ricalcola la pagina pulita di [page] in sottofondo; l'ultima versione chiesta vince. */
  private fun process(page: FluidScanPage, photo: Bitmap?) {
    val version = (versions[page.id] ?: 0) + 1
    versions[page.id] = version
    jobs.remove(page.id)?.cancel()
    working = true
    jobs[page.id] = scope.launch {
      val thumb = withContext(Dispatchers.Default) {
        val source = photo ?: decode(page.photo)
        val clean = render(page, source)
        if (photo == null) source.recycle()
        disk.withLock { withContext(Dispatchers.IO) { FileOutputStream(cleanFile(page.id)).use { clean.compress(Bitmap.CompressFormat.JPEG, options.jpegQuality, it) } } }
        val small = scaleTo(clean, ThumbnailLongSide)
        if (small !== clean) clean.recycle()
        small.asImageBitmap()
      }
      if (versions[page.id] == version && list.any { it.id == page.id }) thumbs[page.id] = thumb
      // Solo se questo lavoro è ancora l'ultimo chiesto: uno più nuovo resta in elenco.
      if (jobs[page.id] === coroutineContext[Job]) jobs.remove(page.id)
      working = jobs.isNotEmpty()
    }
  }

  /** Aspetta che tutte le pagine pulite siano sul disco. */
  suspend fun awaitProcessed() {
    while (true) {
      val pending = jobs.values.toList()
      if (pending.isEmpty()) return
      pending.forEach { it.join() }
    }
  }

  /**
   * Scrive la consegna (il PDF, le immagini o tutte e due, come dice [output]) e la restituisce. Le pagine pulite che
   * mancano si finiscono prima. Gira fuori dal filo principale.
   */
  suspend fun export(output: FluidScanOutput = options.output, title: String = options.title ?: defaultTitle()): FluidScanResult {
    awaitProcessed()
    working = true
    try {
      val pages = list.toList()
      return withContext(Dispatchers.IO) {
        disk.withLock {
          val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim().ifEmpty { "Scansione" }
          val images = if (output.images) pages.mapIndexed { i, page ->
            File(folder, if (pages.size == 1) "$safe.jpg" else "$safe - ${i + 1}.jpg").also { cleanFile(page.id).copyTo(it, overwrite = true) }
          } else emptyList()
          val pdf = if (output.pdf) {
            val file = File(folder, "$safe.pdf")
            val content = pages.map { page ->
              val bytes = cleanFile(page.id).readBytes()
              val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
              BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
              ScanPdfWriter.Page(bytes, bounds.outWidth, bounds.outHeight)
            }
            FileOutputStream(file).use { ScanPdfWriter.write(content, title, it) }
            file
          } else null
          FluidScanResult(
            pdf = pdf?.let { ScanFiles.uri(context, it) },
            images = images.map { ScanFiles.uri(context, it) },
            pageCount = pages.size,
            title = title,
            pdfFile = pdf,
            imageFiles = images,
          )
        }
      }
    } finally {
      working = jobs.isNotEmpty()
    }
  }

  /** La pagina pulita [id] intera, dal disco (per la revisione a tutto schermo). */
  suspend fun cleanPage(id: String, maxLongSide: Int = Int.MAX_VALUE): Bitmap? = withContext(Dispatchers.IO) {
    val file = cleanFile(id)
    if (!file.exists()) return@withContext null
    disk.withLock { decode(file, maxLongSide) }
  }

  /** La foto della pagina [id], ridotta a [maxLongSide] (per il ritaglio). */
  suspend fun photo(id: String, maxLongSide: Int): Bitmap? {
    val page = list.firstOrNull { it.id == id } ?: return null
    return withContext(Dispatchers.IO) { disk.withLock { decode(page.photo, maxLongSide) } }
  }

  /** Toglie tutte le pagine e i file della scansione. */
  fun clear() {
    jobs.values.forEach { it.cancel() }
    jobs.clear()
    list.clear()
    thumbs.clear()
    val dir = File(ScanFiles.root(context), session)
    scope.launch(Dispatchers.IO) { dir.deleteRecursively() }
  }

  internal fun restore(saved: List<FluidScanPage>) {
    if (list.isNotEmpty()) return
    for (page in saved) if (page.photo.exists()) { list.add(page); process(page, null) }
  }

  private fun cleanFile(id: String) = File(folder, "pagina-$id.jpg")

  private fun render(page: FluidScanPage, photo: Bitmap): Bitmap {
    val straight = FluidScanImages.straighten(photo, page.quad, page.quarterTurns, options.maxLongSide)
    val clean = FluidScanImages.filter(straight, page.filter)
    if (clean !== straight) straight.recycle()
    return clean
  }

  /** «Scansione 25 nov 2026, 14:32», nella lingua del telefono. */
  fun defaultTitle(): String =
    "Scansione " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date()).replace(':', '.')

  internal companion object {
    const val ThumbnailLongSide = 480

    fun decode(file: File, maxLongSide: Int = Int.MAX_VALUE): Bitmap {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.path, bounds)
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongSide) sample *= 2
      val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("Pagina illeggibile: ${file.name}")
      return if (maxOf(bitmap.width, bitmap.height) > maxLongSide) scaleTo(bitmap, maxLongSide).also { if (it !== bitmap) bitmap.recycle() } else bitmap
    }

    fun scaleTo(bitmap: Bitmap, longSide: Int): Bitmap {
      val long = maxOf(bitmap.width, bitmap.height)
      if (long <= longSide) return bitmap
      val f = longSide.toFloat() / long
      return Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * f).toInt()), maxOf(1, (bitmap.height * f).toInt()), true)
    }
  }
}

/**
 * Lo stato di una scansione, ricordato fra le ricreazioni dell'attività: le pagine sono sul disco, e si ritrovano.
 * Con [options] le scelte (cosa consegnare, il filtro, lo scatto automatico).
 */
@Composable
fun rememberFluidScanState(options: FluidScanOptions = FluidScanOptions()): FluidScanState {
  val context = LocalContext.current.applicationContext
  val scope = rememberCoroutineScope()
  return rememberSaveable(context, options, saver = scanStateSaver(context, scope, options)) {
    FluidScanState(context, scope, options, UUID.randomUUID().toString().take(12))
  }
}

/** La sessione e le pagine come righe di testo nel Bundle: id, angoli, quarti, filtro, misure. Le foto restano sul disco. */
private fun scanStateSaver(context: Context, scope: CoroutineScope, options: FluidScanOptions) = Saver<FluidScanState, ArrayList<String>>(
  save = { state ->
    ArrayList(listOf(state.session) + state.pages.map { p ->
      (listOf(p.id, p.quarterTurns.toString(), p.filter.id, p.photoWidth.toString(), p.photoHeight.toString()) +
        p.quad.points.flatMap { listOf(it.x.toString(), it.y.toString()) }).joinToString(";")
    })
  },
  restore = { rows ->
    val session = rows.firstOrNull() ?: return@Saver null
    val folder = File(ScanFiles.root(context), session)
    val pages = rows.drop(1).mapNotNull { row ->
      val f = row.split(';')
      if (f.size != 13) return@mapNotNull null
      val pts = (0 until 4).map { FluidScanPoint(f[5 + 2 * it].toFloat(), f[6 + 2 * it].toFloat()) }
      FluidScanPage(f[0], FluidScanQuad(pts[0], pts[1], pts[2], pts[3]), f[1].toInt(), FluidScanFilter.of(f[2]),
        File(folder, "foto-${f[0]}.jpg"), f[3].toInt(), f[4].toInt())
    }
    FluidScanState(context, scope, options, session).also { it.restore(pages) }
  },
)
