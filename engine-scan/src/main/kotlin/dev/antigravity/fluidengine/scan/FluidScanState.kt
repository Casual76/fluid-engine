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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
 * Le pagine che non si leggono (la foto rotta sul disco, la pagina pulita che non si scrive): [pages] sono i numeri
 * nella fila, da 1. La lancia [FluidScanState.export], e lo scanner la dice.
 */
internal class ScanUnreadablePages(val pages: List<Int>) : IOException("Pagine che non si leggono: ${pages.joinToString(", ")}")

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
  /** R1: le pagine la cui pulizia è fallita (il disco pieno, una foto che non si legge). */
  private val failures = mutableStateMapOf<String, Boolean>()
  private val versions = HashMap<String, Int>()
  private val jobs = HashMap<String, Job>()
  private val disk = Mutex()
  /** R1: cresce a ogni [clear]: uno scatto che finisce dopo non entra nella fila buttata. */
  private var generation = 0

  /** Le pagine, nell'ordine della fila. */
  val pages: List<FluidScanPage> get() = list

  /** Vero mentre una pagina si sta pulendo o la consegna si sta scrivendo. */
  var working by mutableStateOf(false)
    private set

  /** La miniatura pulita della pagina [id] (lato lungo 480 px), `null` finché non è pronta. */
  fun thumbnail(id: String): ImageBitmap? = thumbs[id]

  /** Vero se la pagina [id] non si è potuta pulire: «Salva» ci riprova, e se non va lo dice. */
  internal fun failed(id: String): Boolean = failures[id] == true

  /** La cartella di questa scansione nella cache dell'app (la crea: per chi scrive). */
  internal val folder: File get() = File(ScanFiles.root(context), session).apply { mkdirs() }

  /** La cartella senza crearla: per chi legge o cancella. */
  private val place: File get() = File(ScanFiles.root(context), session)

  /**
   * Aggiunge in coda la foto [photo] col foglio [quad] (quello trovato, o tutta la foto). La foto va sul disco e la
   * pagina pulita si calcola fuori dal filo principale; la pagina è nella fila subito, con la miniatura che arriva dopo.
   * Restituisce la pagina, o `null` se la fila è piena (o se la scansione è stata buttata nel frattempo). Lancia
   * `IOException` se la foto non si scrive per intero (il disco pieno, R3): la fila resta com'era.
   */
  suspend fun add(photo: Bitmap, quad: FluidScanQuad? = null): FluidScanPage? {
    if (list.size >= options.maxPages) return null
    val round = generation
    val id = UUID.randomUUID().toString().take(8)
    val file = File(folder, "foto-$id.jpg")
    val width = photo.width
    val height = photo.height
    // R3: uno scatto che il disco non prende per intero non entra nella fila (lo scanner dice «scatto non riuscito»).
    withContext(Dispatchers.IO) {
      disk.withLock { writeJpeg(file, photo, PhotoQuality) }
    }
    val found = quad ?: withContext(Dispatchers.Default) { FluidScanEdges.detect(photo)?.quad } ?: FluidScanQuad.Full
    if (round != generation) {
      // «Butta» mentre lo scatto si scriveva: la foto non resta nella cache.
      ScanFiles.cleanup.launch { disk.withLock { file.delete() } }
      return null
    }
    val page = FluidScanPage(id, found, 0, options.filter, file, width, height)
    list.add(page)
    process(page, photo)
    return page
  }

  /** Toglie la pagina [id]. */
  fun remove(id: String) {
    val index = list.indexOfFirst { it.id == id }
    if (index < 0) return
    val job = jobs.remove(id)?.also { it.cancel() }
    val page = list.removeAt(index)
    thumbs.remove(id)
    failures.remove(id)
    working = jobs.isNotEmpty()
    // R1: i file si tolgono anche se la schermata se ne va subito dopo, e dopo il lavoro che li stava scrivendo.
    ScanFiles.cleanup.launch {
      job?.join()
      disk.withLock { page.photo.delete(); cleanFile(id).delete() }
    }
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

  /**
   * Rifà la pagina [id] con una foto nuova (il «Riprendi» di VisionKit): stessa posizione nella fila. Lo scatto nuovo
   * prende il posto della foto solo quando la pagina nuova è pronta: annullato a metà (Indietro mentre si scrive), lascia
   * la foto e la pagina di prima. Se lo scatto nuovo non si scrive per intero (il disco pieno) lancia `IOException`, e
   * anche allora resta tutto com'era (R3).
   */
  suspend fun retake(id: String, photo: Bitmap, quad: FluidScanQuad? = null) {
    if (list.none { it.id == id }) return
    // R1: lo scatto nuovo in un file suo. Prima si scriveva sopra la foto della pagina: un annullamento a metà lasciava
    // la pagina di prima (fila e pagina pulita) con i byte della foto nuova, e il ritaglio dopo usava quelli.
    val fresh = File(folder, "foto-$id.nuova-${UUID.randomUUID().toString().take(6)}.jpg")
    try {
      withContext(Dispatchers.IO) { writeJpeg(fresh, photo, PhotoQuality) }
      val found = quad ?: withContext(Dispatchers.Default) { FluidScanEdges.detect(photo)?.quad } ?: FluidScanQuad.Full
      // Da qui la foto e la pagina cambiano insieme, senza annullamenti in mezzo.
      withContext(NonCancellable) {
        val target = list.firstOrNull { it.id == id } ?: return@withContext
        disk.withLock { withContext(Dispatchers.IO) { replace(fresh, target.photo) } }
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) {
          // Tolta mentre la foto prendeva il suo posto: niente file rimasti.
          ScanFiles.cleanup.launch { disk.withLock { target.photo.delete() } }
          return@withContext
        }
        val page = list[index].copy(quad = found, quarterTurns = 0, photoWidth = photo.width, photoHeight = photo.height)
        list[index] = page
        process(page, photo)
      }
    } finally {
      // Lo scatto annullato non resta nella cache (dopo il cambio di posto non c'è più, e non succede niente).
      withContext(NonCancellable + Dispatchers.IO) { fresh.delete() }
    }
  }

  private fun update(id: String, change: (FluidScanPage) -> FluidScanPage) {
    val index = list.indexOfFirst { it.id == id }
    if (index < 0) return
    val page = change(list[index])
    if (page == list[index]) return
    list[index] = page
    process(page, null)
  }

  /**
   * Ricalcola la pagina pulita di [page] in sottofondo; l'ultima versione chiesta vince.
   *
   * R3: al piu' [CleaningParallelism] pagine si puliscono insieme (in tutto il processo). Ognuna tiene la foto, la
   * pagina raddrizzata e le mappe del filtro: il filtro cambiato a cento pagine, o cento pagine ritrovate, partivano
   * tutte insieme. Con [reuse] (il ripristino, per una pagina pulita gia' sul disco e uguale alla pagina salvata) la
   * miniatura si legge dal disco invece di rifare la pagina; se non si legge, la pagina si rifa'.
   */
  private fun process(page: FluidScanPage, photo: Bitmap?, reuse: Boolean = false) {
    val version = (versions[page.id] ?: 0) + 1
    versions[page.id] = version
    jobs.remove(page.id)?.cancel()
    failures.remove(page.id)
    working = true
    jobs[page.id] = scope.launch {
      try {
        val thumb = Cleaning.withPermit {
          withContext(Dispatchers.Default) {
            val kept = if (!reuse) null else disk.withLock {
              withContext(Dispatchers.IO) { cleanFile(page.id).takeIf { it.exists() }?.let { decodeOrNull(it, ThumbnailLongSide) } }
            }
            val small = kept ?: run {
              val source = photo ?: decode(page.photo)
              val clean = render(page, source)
              if (photo == null) source.recycle()
              try {
                // R3: una pagina pulita che il disco non prende per intero non resta come buona.
                disk.withLock { withContext(Dispatchers.IO) { writeJpeg(File(folder, "pagina-${page.id}.jpg"), clean, options.jpegQuality) } }
              } catch (e: Throwable) {
                clean.recycle()
                throw e
              }
              scaleTo(clean, ThumbnailLongSide).also { if (it !== clean) clean.recycle() }
            }
            small.asImageBitmap()
          }
        }
        if (versions[page.id] == version && list.any { it.id == page.id }) thumbs[page.id] = thumb
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        // R1: il disco pieno, una foto che non si legge. Prima l'eccezione usciva dal lavoro (e chiudeva l'app) e
        // working restava vero; ora la pagina resta nella fila senza la pagina pulita, e «Salva» ci riprova e lo dice.
        if (versions[page.id] == version && list.any { it.id == page.id }) failures[page.id] = true
      } finally {
        // Anche annullato (la pagina tolta, una versione più nuova): solo se questo lavoro è ancora l'ultimo chiesto.
        if (jobs[page.id] === coroutineContext[Job]) jobs.remove(page.id)
        working = jobs.isNotEmpty()
      }
    }
  }

  /** Aspetta che tutte le pagine pulite siano sul disco (o fallite). */
  suspend fun awaitProcessed() {
    while (true) {
      // Un lavoro che non è mai partito (lo scope già annullato) è finito anche lui.
      val pending = jobs.values.filter { !it.isCompleted }
      if (pending.isEmpty()) return
      pending.forEach { it.join() }
    }
  }

  /**
   * Scrive la consegna (il PDF, le immagini o tutte e due, come dice [output]) e la restituisce. Le pagine pulite che
   * mancano si finiscono prima (e quelle fallite si rifanno una volta). Gira fuori dal filo principale.
   *
   * Lancia `IOException` se una pagina non si legge (il messaggio dice quali, per numero) o se il documento non si
   * scrive (il disco pieno): la scansione resta com'è, e si può togliere o rifare la pagina e riprovare.
   */
  suspend fun export(output: FluidScanOutput = options.output, title: String = options.title ?: defaultTitle()): FluidScanResult {
    // R1: una pagina senza pagina pulita (la pulizia fallita, la cache svuotata) si rifà prima di scrivere.
    val queued = list.toList()
    val missing = withContext(Dispatchers.IO) { queued.filter { !cleanFile(it.id).exists() }.map { it.id }.toSet() }
    for (page in list.toList()) if (jobs[page.id]?.isCompleted != false && (page.id in missing || failed(page.id))) process(page, null)
    awaitProcessed()
    val unreadable = list.mapIndexedNotNull { i, page -> if (failed(page.id)) i + 1 else null }
    if (unreadable.isNotEmpty()) throw ScanUnreadablePages(unreadable)
    working = true
    // R3: le pagine pulite rotte trovate sul disco, da segnare da rifare qui sul filo principale.
    var broken: List<String> = emptyList()
    try {
      val pages = list.toList()
      return withContext(Dispatchers.IO) {
        disk.withLock {
          val gone = pages.mapIndexedNotNull { i, page -> if (cleanFile(page.id).exists()) null else i + 1 }
          if (gone.isNotEmpty()) throw ScanUnreadablePages(gone)
          // R3: una pagina pulita che non ha nemmeno le misure (tronca, rotta da fuori) non va nel documento: e' da rifare
          // (come una pulizia fallita), e il prossimo «Salva» la rifa' dalla foto.
          val unreadableClean = pages.filter { page ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(cleanFile(page.id).path, bounds)
            bounds.outWidth <= 0 || bounds.outHeight <= 0
          }
          if (unreadableClean.isNotEmpty()) {
            broken = unreadableClean.map { it.id }
            unreadableClean.forEach { cleanFile(it.id).delete() }
            throw ScanUnreadablePages(unreadableClean.map { pages.indexOf(it) + 1 })
          }
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
      // Sul filo principale, come le altre scritture di stato: la revisione dice «Pagina non leggibile», e la cancellazione
      // del file (che sulla JVM di Windows delle prove puo' non riuscire subito) non decide da sola.
      for (id in broken) if (list.any { it.id == id }) failures[id] = true
      working = jobs.isNotEmpty()
    }
  }

  /** La pagina pulita [id] intera, dal disco (per la revisione a tutto schermo); `null` se non c'è o non si legge. */
  suspend fun cleanPage(id: String, maxLongSide: Int = Int.MAX_VALUE): Bitmap? = withContext(Dispatchers.IO) {
    val file = cleanFile(id)
    if (!file.exists()) return@withContext null
    disk.withLock { decodeOrNull(file, maxLongSide) }
  }

  /** La foto della pagina [id], ridotta a [maxLongSide] (per il ritaglio); `null` se non c'è o non si legge. */
  suspend fun photo(id: String, maxLongSide: Int): Bitmap? {
    val page = list.firstOrNull { it.id == id } ?: return null
    return withContext(Dispatchers.IO) { disk.withLock { decodeOrNull(page.photo, maxLongSide) } }
  }

  /**
   * Toglie tutte le pagine e i file della scansione. I file si cancellano in sottofondo anche dopo che la schermata si
   * è chiusa, dopo i lavori che li stavano scrivendo.
   */
  fun clear() {
    generation++
    val pending = jobs.values.toList()
    pending.forEach { it.cancel() }
    jobs.clear()
    list.clear()
    thumbs.clear()
    failures.clear()
    working = false
    val dir = place
    // R1: prima la pulizia partiva dallo scope della composizione, che «Butta» chiude subito dopo (o già chiuso), e non
    // aspettava la pagina pulita che si stava scrivendo: restavano foto di documenti che la persona aveva buttato.
    ScanFiles.cleanup.launch {
      pending.joinAll()
      disk.withLock {
        // Un file tenuto aperto un attimo da altri (l'antivirus di Windows nelle prove) non lascia la cartella.
        var tries = 0
        while (!dir.deleteRecursively() && ++tries < DeleteTries) delay(DeleteRetryMillis)
      }
    }
  }

  /**
   * Le pagine ritrovate dopo la ricreazione. R3: quelle in [ready] avevano la pagina pulita sul disco quando l'attivita'
   * si e' salvata (nessuna pulizia in corso, nessuna fallita): se c'e' ancora, se ne legge solo la miniatura.
   */
  internal fun restore(saved: List<FluidScanPage>, ready: Set<String> = emptySet()) {
    if (list.isNotEmpty()) return
    for (page in saved) if (page.photo.exists()) { list.add(page); process(page, null, reuse = page.id in ready) }
  }

  /** R3: la pagina pulita di [id] sul disco e' quella della pagina com'e' adesso (per il salvataggio dello stato). */
  internal fun cleanOnDisk(id: String): Boolean = jobs[id] == null && failures[id] != true && thumbs[id] != null

  private fun cleanFile(id: String) = File(place, "pagina-$id.jpg")

  /**
   * Mette [from] al posto di [to] (sul telefono un `rename`, che sostituisce in un colpo). Sulla JVM di Windows delle
   * prove un file appena scritto può restare aperto un attimo da altri (l'antivirus): si riprova per mezzo secondo.
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
        if (++tries >= DeleteTries) throw busy
        Thread.sleep(DeleteRetryMillis)
      }
    }
  }

  private fun render(page: FluidScanPage, photo: Bitmap): Bitmap {
    val straight = FluidScanImages.straighten(photo, page.quad, page.quarterTurns, options.maxLongSide)
    val clean = FluidScanImages.filter(straight, page.filter)
    if (clean !== straight) straight.recycle()
    return clean
  }

  /**
   * «Scansione 25 nov 2026, 14.32»: la data nella lingua del telefono, la parola in italiano come le altre parole di serie
   * ([FluidScanStrings]). Un'app in un'altra lingua passa il suo nome con [FluidScanOptions.title] (R3).
   */
  fun defaultTitle(): String =
    "Scansione " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date()).replace(':', '.')

  internal companion object {
    const val ThumbnailLongSide = 480
    /** La qualita' JPEG della foto scattata sul disco (a occhio, F8.17). */
    const val PhotoQuality = 92

    /**
     * R3 · Quante pagine si puliscono insieme, in tutto il processo: 2. Una pagina in pulizia tiene la foto (fino a 8 MP,
     * 32 MB), la pagina raddrizzata (2400 px, ~17 MB) e le mappe del filtro; due tengono i processori occupati senza che
     * cento pagine diventino centinaia di MB (a occhio, 27/11/2026).
     */
    const val CleaningParallelism = 2
    private val Cleaning = Semaphore(CleaningParallelism)

    /**
     * R3 · Scrive [bitmap] in JPEG in [file]. `Bitmap.compress` non lancia quando lo scrittore fallisce (il disco pieno):
     * torna falso, con una parte dei byte gia' scritta. Allora il file si toglie e si lancia `IOException`.
     */
    fun writeJpeg(file: File, bitmap: Bitmap, quality: Int) {
      val written = try {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
      } catch (e: IOException) {
        file.delete()
        throw e
      }
      if (!written) {
        file.delete()
        throw IOException("JPEG non scritto per intero (il disco e' pieno?): ${file.name}")
      }
    }
    /** I tentativi di cancellare o spostare un file tenuto aperto da altri, a 25 ms l'uno dall'altro. */
    const val DeleteTries = 20
    const val DeleteRetryMillis = 25L

    fun decode(file: File, maxLongSide: Int = Int.MAX_VALUE): Bitmap {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.path, bounds)
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxLongSide) sample *= 2
      val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("Pagina illeggibile: ${file.name}")
      return if (maxOf(bitmap.width, bitmap.height) > maxLongSide) scaleTo(bitmap, maxLongSide).also { if (it !== bitmap) bitmap.recycle() } else bitmap
    }

    /** Come [decode], ma `null` invece dell'errore (la revisione e il ritaglio mostrano il vuoto, non cadono). */
    fun decodeOrNull(file: File, maxLongSide: Int): Bitmap? = try {
      decode(file, maxLongSide)
    } catch (_: IllegalStateException) {
      null
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

/**
 * La sessione e le pagine come righe di testo nel Bundle: id, angoli, quarti, filtro, misure e (R3) se la pagina pulita
 * sul disco era pronta per la pagina com'era. Le foto restano sul disco.
 */
internal fun scanStateSaver(context: Context, scope: CoroutineScope, options: FluidScanOptions) = Saver<FluidScanState, ArrayList<String>>(
  save = { state ->
    ArrayList(listOf(state.session) + state.pages.map { p ->
      (listOf(p.id, p.quarterTurns.toString(), p.filter.id, p.photoWidth.toString(), p.photoHeight.toString()) +
        p.quad.points.flatMap { listOf(it.x.toString(), it.y.toString()) } +
        (if (state.cleanOnDisk(p.id)) "1" else "0")).joinToString(";")
    })
  },
  restore = { rows ->
    val session = rows.firstOrNull() ?: return@Saver null
    val folder = File(ScanFiles.root(context), session)
    val ready = HashSet<String>()
    val pages = rows.drop(1).mapNotNull { row ->
      val f = row.split(';')
      if (f.size != 13 && f.size != 14) return@mapNotNull null
      if (f.size == 14 && f[13] == "1") ready += f[0]
      val pts = (0 until 4).map { FluidScanPoint(f[5 + 2 * it].toFloat(), f[6 + 2 * it].toFloat()) }
      FluidScanPage(f[0], FluidScanQuad(pts[0], pts[1], pts[2], pts[3]), f[1].toInt(), FluidScanFilter.of(f[2]),
        File(folder, "foto-${f[0]}.jpg"), f[3].toInt(), f[4].toInt())
    }
    FluidScanState(context, scope, options, session).also { it.restore(pages, ready) }
  },
)
