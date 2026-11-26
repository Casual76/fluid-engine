/*
 * engine-viewer-ocr (4.0, F8.17): l'estensione dei visori che aggiunge «Leggi testo» a foto, schermate e PDF. Si scopre
 * da sola (META-INF/services): basta la dipendenza. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.viewer.ocr

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageNotes
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageOverlays
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSheet
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageTextSearch
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPageAccessory
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPageMatch
import dev.antigravity.fluidengine.ui.fluid.viewer.currentFluidViewerPageActive
import dev.antigravity.fluidengine.viewer.FluidViewerExtension
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * «Leggi testo» per i visori: le foto e le schermate (`FluidViewerContent.Image`) e i documenti a pagine
 * (`FluidViewerContent.Pages`, i PDF scansiti) ricevono il disegno e il tasto di Testo attivo. La lettura parte quando la
 * pagina è ferma da [FluidLiveText.SettleMillis] e si ricorda (un file riaperto è già letto).
 */
class FluidLiveTextExtension : FluidViewerExtension {
  override val id: String = "ocr"

  override fun decorate(context: Context, file: FluidViewerFile, content: FluidViewerContent): FluidViewerContent {
    if (!FluidLiveText.enabled) return content
    val key = "${file.uri}|${file.sizeBytes}|${file.modifiedAtMillis}"
    return when (content) {
      is FluidViewerContent.Image -> FluidViewerContent.Image(content.aspectRatio) {
        content.draw()
        LiveTextImage(key, content.aspectRatio ?: 1f) { ctx -> decodeForReading(ctx, file.uri, ReadMaxPixels) }
      }
      // Solo i PDF (i documenti scansiti): un documento Office ha già il suo testo, e la sua sorgente ha capacità sue
      // (le note del relatore, FluidPageNotes) che un involucro nasconderebbe.
      is FluidViewerContent.Pages -> if (content.source is LiveTextPages || content.source is FluidPageNotes || !file.isPdf) content
        else FluidViewerContent.Pages(LiveTextPages(content.source, key))
      else -> content
    }
  }

  private val FluidViewerFile.isPdf: Boolean
    get() = mimeType?.lowercase()?.startsWith("application/pdf") == true || name.lowercase().endsWith(".pdf")

  internal companion object {
    /** La foto da leggere: al più 8 MP. Di più non serve al riconoscimento e costa memoria. */
    const val ReadMaxPixels: Long = 8_000_000L
  }
}

/**
 * La foto da leggere, in memoria normale (Tesseract legge i pixel: una bitmap «hardware» del visore non si legge) e al più
 * di [maxPixels]. Con `ImageDecoder` (Android 9 e oltre) il verso EXIF è lo stesso del visore, che decodifica allo stesso
 * modo; sotto, `BitmapFactory`.
 */
internal suspend fun decodeForReading(context: Context, uri: android.net.Uri, maxPixels: Long): Bitmap? =
  kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val local: File? = uri.takeIf { it.scheme == "file" }?.path?.let { File(it) }
    val decoded = if (android.os.Build.VERSION.SDK_INT >= 28) runCatching {
      // Un file dell'app si apre direttamente; il resto dal ContentResolver.
      val source = if (local != null) android.graphics.ImageDecoder.createSource(local)
        else android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
      android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        val pixels = info.size.width.toLong() * info.size.height
        if (pixels > maxPixels) {
          val f = kotlin.math.sqrt(maxPixels.toDouble() / pixels)
          decoder.setTargetSize(max(1, (info.size.width * f).toInt()), max(1, (info.size.height * f).toInt()))
        }
      }
    }.getOrNull() else null
    // Sotto Android 9, o se ImageDecoder non ce la fa (un formato che solo BitmapFactory legge): BitmapFactory.
    decoded ?: run {
      val open = { local?.inputStream() ?: context.contentResolver.openInputStream(uri) }
      val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
      open()?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
      var sample = 1
      while (bounds.outWidth.toLong() * bounds.outHeight / (sample * sample) > maxPixels) sample *= 2
      open()?.use {
        android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
      }
    }
  }

/** Il disegno e il tasto per un'immagine del visore, con la lettura quando la pagina è ferma. */
@Composable
internal fun LiveTextImage(key: String, aspect: Float, load: suspend (Context) -> Bitmap?) {
  val context = LocalContext.current
  val state = remember(key) { FluidLiveTextState(aspect).also { s -> FluidLiveText.cached(key)?.let(s::show) } }
  val active = currentFluidViewerPageActive()
  LaunchedEffect(key, active) {
    if (!active || state.status == FluidLiveTextStatus.Ready) return@LaunchedEffect
    delay(FluidLiveText.SettleMillis)
    // Una foto: se ci si vede un foglio storto, si legge raddrizzato (F8.17d, «scansiona, poi leggi»).
    state.read { FluidLiveText.read(context, key, findSheet = true) { load(context) } }
  }
  FluidLiveTextOverlay(state)
  FluidViewerPageAccessory { FluidLiveTextControls(state) }
}

/**
 * Le pagine di un documento con «Leggi testo»: la stessa sorgente (conteggio, misure, resa) più il disegno sopra ogni
 * pagina e la ricerca nelle parole trovate. Una pagina si legge quando è in vista e ferma; la ricerca legge anche le
 * pagine non ancora lette, fino a [SearchPages].
 */
internal class LiveTextPages(private val pages: FluidPageSource, private val key: String) : FluidPageSource,
  FluidPageOverlays, FluidPageTextSearch {
  // Una mappa semplice: ogni stato è già osservabile, e la si riempie durante la composizione (niente scritture di stato).
  private val states = HashMap<Int, FluidLiveTextState>()
  /** La pagina che porta il tasto: la prima pagina in vista. */
  private val composed = mutableStateMapOf<Int, Boolean>()
  @Volatile private var context: Context? = null
  /** La modalità attiva vale per tutto il documento. */
  private val document = FluidLiveTextState(1f)

  override val count: Int get() = pages.count
  override val countFinal: Boolean get() = pages.countFinal
  override suspend fun size(index: Int): IntSize = pages.size(index)
  override suspend fun render(index: Int, target: IntSize) = pages.render(index, target)
  override suspend fun sheet(index: Int): FluidPageSheet = pages.sheet(index)

  private fun state(index: Int): FluidLiveTextState =
    states.getOrPut(index) { FluidLiveTextState().also { s -> s.parent = document; FluidLiveText.cached(pageKey(index))?.let(s::show) } }

  private fun pageKey(index: Int) = "$key#$index"

  private suspend fun read(context: Context, index: Int): FluidRecognizedText? = FluidLiveText.read(context, pageKey(index)) {
    val natural = pages.size(index)
    // La pagina a 2000 px sul lato lungo: circa 240 dpi su un A4, quanto basta a Tesseract.
    val scale = ReadLongSide.toFloat() / max(natural.width, natural.height).coerceAtLeast(1)
    val target = IntSize(max(1, (natural.width * scale).roundToInt()), max(1, (natural.height * scale).roundToInt()))
    pages.render(index, target).asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
  }

  @Composable
  override fun PageOverlay(index: Int, active: Boolean) {
    val ctx = LocalContext.current
    context = ctx.applicationContext
    val state = state(index)
    androidx.compose.runtime.DisposableEffect(index) {
      composed[index] = true
      onDispose { composed.remove(index) }
    }
    LaunchedEffect(index, active) {
      if (!active || state.status == FluidLiveTextStatus.Ready) return@LaunchedEffect
      delay(FluidLiveText.SettleMillis)
      state.read { read(ctx, index) }
    }
    FluidLiveTextOverlay(state)
    // Il tasto vale per tutto il documento (la modalità e la ricerca sono sue); il testo dei comandi è quello della
    // prima pagina in vista, che porta il tasto.
    val anchor = composed.keys.minOrNull()
    if (anchor == index) {
      val pageText = state.text
      LaunchedEffect(pageText) { if (pageText != null && !pageText.isEmpty) document.show(pageText) }
      FluidViewerPageAccessory { FluidLiveTextControls(document) }
    }
  }

  override suspend fun searchPages(query: String): List<FluidViewerPageMatch> {
    val ctx = context ?: return emptyList()
    val out = ArrayList<FluidViewerPageMatch>()
    for (index in 0 until minOf(count, SearchPages)) {
      val text = FluidLiveText.cached(pageKey(index)) ?: read(ctx, index)?.also { t -> states[index]?.show(t) } ?: continue
      for (m in text.search(query)) {
        val rects = spanRects(text, m.first, m.last, androidx.compose.ui.geometry.Size(1f, 1f))
        out += FluidViewerPageMatch(index, rects)
      }
    }
    return out
  }

  companion object {
    const val ReadLongSide = 2000
    /** Quante pagine legge al più la ricerca: 40. Un PDF scansito di 300 pagine si cerca nelle prime 40 (limite scritto nella scheda). */
    const val SearchPages = 40
  }
}
