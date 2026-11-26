/*
 * engine-viewer-ocr (4.0, F8.17): il riconoscimento del testo, sul telefono e mai in rete. Il contratto e Tesseract 5
 * (Apache-2.0) attraverso i collegamenti di bytedeco (JavaCPP, Apache-2.0), col modello «fast» dell'italiano.
 * Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.viewer.ocr

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.tesseract.TessBaseAPI
import org.bytedeco.tesseract.global.tesseract.OEM_LSTM_ONLY
import org.bytedeco.tesseract.global.tesseract.PSM_AUTO
import org.bytedeco.tesseract.global.tesseract.RIL_BLOCK
import org.bytedeco.tesseract.global.tesseract.RIL_TEXTLINE
import org.bytedeco.tesseract.global.tesseract.RIL_WORD
import org.bytedeco.tesseract.global.tesseract.TessDeleteText
import org.bytedeco.tesseract.global.tesseract.TessResultIteratorDelete
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Chi legge il testo di un'immagine. L'engine dà [FluidTesseractRecognizer]; un'app può darne un altro (per esempio uno
 * che usa ML Kit, se lo sceglie) con [FluidLiveText.recognizer]. Il riconoscimento avviene **sul telefono**: nessuna
 * immagine va in rete.
 */
interface FluidTextRecognizer {
  /** Falso se sul telefono non si può (manca la libreria per l'architettura, il modello non si apre). */
  val available: Boolean

  /** Il testo di [image]. Gira fuori dal filo principale; non ricicla [image]. */
  suspend fun recognize(image: Bitmap): FluidRecognizedText
}

/**
 * Tesseract 5 sul telefono, col modello LSTM «fast» dell'italiano (`ita.traineddata`, Apache-2.0, 2,7 MB, negli asset
 * del modulo): riconosce anche l'inglese e le altre lingue con l'alfabeto latino, meno bene le parole straniere. Per
 * un'altra lingua l'app mette il suo `<lingua>.traineddata` in `assets/fluid-ocr/tessdata/` e la nomina in [languages]
 * (per esempio `"ita+eng"`).
 *
 * Prima della lettura l'immagine passa in toni di grigio con **la luce della carta pareggiata** (la stessa idea del
 * filtro «Documento» dello scanner): Tesseract binarizza con una soglia sola, e una foto con l'ombra o il gradiente
 * perderebbe le righe al buio. Il testo piccolo si ingrandisce fino a [targetLongSide] pixel sul lato lungo.
 *
 * Un solo Tesseract per processo, su un filo suo (non è rientrante); il modello si carica alla prima lettura (circa
 * 100 ms) e resta.
 */
class FluidTesseractRecognizer(
  context: Context,
  private val languages: String = "ita",
  private val targetLongSide: Int = DefaultTargetLongSide,
) : FluidTextRecognizer {
  private val app = context.applicationContext ?: context

  override val available: Boolean
    get() = TesseractHost.available(app, languages)

  override suspend fun recognize(image: Bitmap): FluidRecognizedText = withContext(TesseractHost.dispatcher) {
    // R1: anche la copia dei pixel sul filo di Tesseract (a 12 MP sono 48 MB): il contratto dice «fuori dal filo
    // principale», e chi chiama da una coroutine dell'interfaccia non deve pagarla lì.
    val w = image.width
    val h = image.height
    val pixels = IntArray(w * h)
    image.getPixels(pixels, 0, w, 0, 0, w, h)
    val gray = OcrImage.prepare(pixels, w, h, targetLongSide)
    TesseractHost.read(app, languages, gray)
  }

  companion object {
    /** Il lato lungo a cui si porta l'immagine: 2400 px. A occhio (25/11/2026): le lettere di una foto a 12 MP restano alte almeno 20 px, quanto chiede Tesseract. */
    const val DefaultTargetLongSide: Int = 2400
  }
}

/** Un'immagine in toni di grigio pronta per la lettura, e il fattore rispetto all'originale. */
internal class OcrGray(val bytes: ByteArray, val width: Int, val height: Int, val scale: Float, val sourceWidth: Int, val sourceHeight: Int)

/** La preparazione dell'immagine, senza Android (si prova sul computer). */
internal object OcrImage {
  /**
   * L'immagine in grigio con la luce della carta pareggiata, ridotta o ingrandita verso [targetLongSide]. [pixels] (ARGB)
   * si riusa e alla fine non contiene più i colori: R2 (n. 11), prima c'erano altre due mappe intere (la luminanza e la
   * luce, 4 byte per pixel ognuna: a 8 MP 64 MB in più). I byte che escono sono gli stessi.
   */
  fun prepare(pixels: IntArray, w: Int, h: Int, targetLongSide: Int): OcrGray {
    val long = max(w, h)
    // Ingrandire fino al doppio il testo piccolo, ridurre oltre 1,3 volte il lato lungo voluto.
    val scale = when {
      long < targetLongSide -> min(2f, targetLongSide.toFloat() / long)
      long > targetLongSide * 1.3f -> targetLongSide * 1.3f / long
      else -> 1f
    }
    val ow = max(1, (w * scale).roundToInt())
    val oh = max(1, (h * scale).roundToInt())
    // La luminanza prende il posto del colore nello stesso array (i bit di un Float), poi la luminanza divisa per la
    // luce della carta: le stesse operazioni di prima, nello stesso ordine.
    for (i in 0 until w * h) {
      val c = pixels[i]
      pixels[i] = (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF)).toRawBits()
    }
    divideByPaperLight(pixels, w, h)
    val out = ByteArray(ow * oh)
    for (y in 0 until oh) {
      val sy = ((y + 0.5f) / scale - 0.5f).coerceIn(0f, (h - 1).toFloat())
      val y0 = sy.toInt(); val y1 = min(h - 1, y0 + 1); val ty = sy - y0
      for (x in 0 until ow) {
        val sx = ((x + 0.5f) / scale - 0.5f).coerceIn(0f, (w - 1).toFloat())
        val x0 = sx.toInt(); val x1 = min(w - 1, x0 + 1); val tx = sx - x0
        val i00 = y0 * w + x0; val i01 = y0 * w + x1; val i10 = y1 * w + x0; val i11 = y1 * w + x1
        val v = Float.fromBits(pixels[i00]) * (1 - tx) * (1 - ty) + Float.fromBits(pixels[i01]) * tx * (1 - ty) +
          Float.fromBits(pixels[i10]) * (1 - tx) * ty + Float.fromBits(pixels[i11]) * tx * ty
        out[y * ow + x] = (v * 255f).roundToInt().coerceIn(0, 255).toByte()
      }
    }
    return OcrGray(out, ow, oh, scale, w, h)
  }

  /**
   * La luce della carta: il 90° percentile su una griglia di 32 × 32 celle, allargato e sfumato (vedi lo scanner). In
   * [luma] (i bit dei Float della luminanza) ogni pixel diventa la sua luminanza divisa per la luce, calcolata lì dalla
   * griglia: niente mappa intera della luce.
   */
  fun divideByPaperLight(luma: IntArray, w: Int, h: Int) {
    val cell = max(8, max(w, h) / 32)
    val gw = (w + cell - 1) / cell
    val gh = (h + cell - 1) / cell
    val grid = FloatArray(gw * gh)
    val histogram = IntArray(64)
    for (gy in 0 until gh) for (gx in 0 until gw) {
      java.util.Arrays.fill(histogram, 0)
      var count = 0
      var y = gy * cell
      while (y < min(h, (gy + 1) * cell)) {
        var x = gx * cell
        while (x < min(w, (gx + 1) * cell)) { histogram[(Float.fromBits(luma[y * w + x]).toInt() shr 2).coerceIn(0, 63)]++; count++; x += 2 }
        y += 2
      }
      val target = (count * 0.9f).toInt()
      var sum = 0
      var value = 63
      for (i in 0 until 64) { sum += histogram[i]; if (sum > target) { value = i; break } }
      grid[gy * gw + gx] = (value * 4 + 2).toFloat()
    }
    var smooth = FloatArray(grid.size) { i ->
      val gx = i % gw; val gy = i / gw
      var m = 0f
      for (dy in -1..1) for (dx in -1..1) m = max(m, grid[(gy + dy).coerceIn(0, gh - 1) * gw + (gx + dx).coerceIn(0, gw - 1)])
      m
    }
    repeat(2) {
      val prev = smooth
      smooth = FloatArray(prev.size) { i ->
        val gx = i % gw; val gy = i / gw
        var s = 0f
        for (dy in -1..1) for (dx in -1..1) s += prev[(gy + dy).coerceIn(0, gh - 1) * gw + (gx + dx).coerceIn(0, gw - 1)]
        s / 9f
      }
    }
    for (y in 0 until h) {
      val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (gh - 1).toFloat())
      val y0 = fy.toInt(); val y1 = min(gh - 1, y0 + 1); val ty = fy - y0
      for (x in 0 until w) {
        val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (gw - 1).toFloat())
        val x0 = fx.toInt(); val x1 = min(gw - 1, x0 + 1); val tx = fx - x0
        val a = smooth[y0 * gw + x0] * (1 - tx) + smooth[y0 * gw + x1] * tx
        val b = smooth[y1 * gw + x0] * (1 - tx) + smooth[y1 * gw + x1] * tx
        val i = y * w + x
        luma[i] = (Float.fromBits(luma[i]) / max(32f, a * (1 - ty) + b * ty)).toRawBits()
      }
    }
  }
}

/** Una parola come la dà Tesseract: pixel dell'immagine preparata, fiducia 0..100, inizio di riga o di blocco. */
internal class OcrWordBox(
  val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val confidence: Float,
  val newBlock: Boolean, val newLine: Boolean,
)

/** Le parole di Tesseract diventano blocchi e righe in coordinate 0..1 dell'immagine di partenza. */
internal object OcrAssembler {
  /** La fiducia minima di una parola, 0..100: sotto, è rumore (una macchia, un bordo). 35, a occhio sulle foto di prova. */
  const val MinConfidence = 35f

  fun assemble(words: List<OcrWordBox>, gray: OcrGray, language: String): FluidRecognizedText {
    val blocks = ArrayList<FluidRecognizedBlock>()
    var lines = ArrayList<FluidRecognizedLine>()
    var current = ArrayList<FluidRecognizedWord>()
    fun closeLine() {
      if (current.isNotEmpty()) {
        lines += FluidRecognizedLine(current, FluidTextQuad.span(current.first().quad, current.last().quad))
        current = ArrayList()
      }
    }
    fun closeBlock() {
      closeLine()
      if (lines.isNotEmpty()) {
        val all = lines.map { it.quad.bounds }
        val bounds = Rect(all.minOf { it.left }, all.minOf { it.top }, all.maxOf { it.right }, all.maxOf { it.bottom })
        blocks += FluidRecognizedBlock(lines, FluidTextQuad.of(bounds))
        lines = ArrayList()
      }
    }
    val sx = 1f / gray.width
    val sy = 1f / gray.height
    for (w in words) {
      if (w.newBlock) closeBlock() else if (w.newLine) closeLine()
      val text = w.text.trim()
      if (text.isEmpty() || w.confidence < MinConfidence) continue
      // Una «parola» di soli segni sottili (|, _, —) è quasi sempre una riga del modulo o un bordo.
      if (text.all { it in NoiseChars }) continue
      val rect = Rect(w.left * sx, w.top * sy, w.right * sx, w.bottom * sy)
      current += FluidRecognizedWord(text, FluidTextQuad.of(rect), (w.confidence / 100f).coerceIn(0f, 1f))
    }
    closeBlock()
    return FluidRecognizedText(blocks, gray.sourceWidth, gray.sourceHeight, language)
  }

  private const val NoiseChars = "|_-—–~.,:;'\"`^°*=+/\\()[]{}<>"
}

/** Il Tesseract del processo, su un filo suo. */
internal object TesseractHost {
  val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "fluid-ocr").apply { isDaemon = true } }.asCoroutineDispatcher()
  private var api: TessBaseAPI? = null
  private var loaded: String? = null
  @Volatile private var broken: Throwable? = null

  fun available(context: Context, languages: String): Boolean {
    if (broken != null) return false
    return try {
      // Solo che la classe ci sia, **senza inizializzarla** (F8.17d): l'inizializzazione di TessBaseAPI carica le
      // librerie native (14 MB sul telefono), e si fa nel filo di Tesseract alla prima lettura (engine). Dove le librerie
      // non ci sono (le prove della galleria sul PC) JavaCPP le cercava per un minuto, e qui era il filo principale.
      Class.forName("org.bytedeco.tesseract.TessBaseAPI", false, TesseractHost::class.java.classLoader)
      languages.split('+').all { lang -> hasModel(context, lang) }
    } catch (_: Throwable) {
      false
    }
  }

  private fun hasModel(context: Context, lang: String): Boolean = try {
    context.assets.open("$AssetDir/$lang.traineddata").close(); true
  } catch (_: Exception) {
    false
  }

  /** Legge [gray] (sul filo di [dispatcher]). */
  fun read(context: Context, languages: String, gray: OcrGray): FluidRecognizedText {
    val tess = engine(context, languages)
    tess.SetImage(gray.bytes, gray.width, gray.height, 1, gray.width)
    // La risoluzione dichiarata evita l'avviso «Invalid resolution» e regola le misure minime: 300 dpi equivalenti.
    tess.SetSourceResolution(300)
    if (tess.Recognize(null) != 0) return FluidRecognizedText(emptyList(), gray.sourceWidth, gray.sourceHeight, languages)
    val words = words(tess)
    tess.Clear()
    return OcrAssembler.assemble(words, gray, languages)
  }

  /** Le parole dell'ultima lettura di [tess] (sul filo di [dispatcher]), coi riquadri e la fiducia. */
  internal fun words(tess: TessBaseAPI): List<OcrWordBox> {
    val words = ArrayList<OcrWordBox>()
    val iterator = tess.GetIterator() ?: return words
    val left = IntPointer(1); val top = IntPointer(1); val right = IntPointer(1); val bottom = IntPointer(1)
    try {
      do {
        // R1: il testo della parola e l'iteratore li alloca Tesseract (new[] e new), e i loro oggetti JavaCPP non hanno
        // chi li libera: deallocate() e close() non facevano niente, e ogni lettura lasciava memoria nativa. Si liberano
        // con le funzioni C di Tesseract.
        val raw: BytePointer? = iterator.GetUTF8Text(RIL_WORD)
        val text = try {
          raw?.getString(Charsets.UTF_8) ?: ""
        } finally {
          if (raw != null && !raw.isNull) TessDeleteText(raw)
        }
        if (!iterator.BoundingBox(RIL_WORD, left, top, right, bottom)) continue
        words += OcrWordBox(text, left.get(), top.get(), right.get(), bottom.get(), iterator.Confidence(RIL_WORD),
          iterator.IsAtBeginningOf(RIL_BLOCK), iterator.IsAtBeginningOf(RIL_TEXTLINE))
      } while (iterator.Next(RIL_WORD))
    } finally {
      left.close(); top.close(); right.close(); bottom.close()
      TessResultIteratorDelete(iterator)
    }
    return words
  }

  /** Il Tesseract del processo per [languages], caricato alla prima lettura (sul filo di [dispatcher]). */
  internal fun engine(context: Context, languages: String): TessBaseAPI {
    broken?.let { throw IllegalStateException("Tesseract non disponibile", it) }
    api?.let { if (loaded == languages) return it }
    try {
      api?.let { it.End(); it.close() }
      api = null
      val dir = File(context.noBackupFilesDir, "fluid-ocr/tessdata").apply { mkdirs() }
      for (lang in languages.split('+')) {
        val target = File(dir, "$lang.traineddata")
        if (!target.exists() || target.length() == 0L) {
          val temp = File(dir, "$lang.traineddata.part")
          context.assets.open("$AssetDir/$lang.traineddata").use { input -> temp.outputStream().use { input.copyTo(it) } }
          temp.renameTo(target)
        }
      }
      val tess = TessBaseAPI()
      // Tesseract 5 vuole la cartella tessdata; alcune versioni la cartella che la contiene.
      if (tess.Init(dir.path, languages, OEM_LSTM_ONLY) != 0 && tess.Init(dir.parentFile!!.path, languages, OEM_LSTM_ONLY) != 0) {
        tess.close()
        error("Il modello $languages non si apre")
      }
      tess.SetPageSegMode(PSM_AUTO)
      // Niente dizionario degli utenti né file di parole: il modello LSTM da solo, come nelle misure del rapporto.
      tess.SetVariable("debug_file", "/dev/null")
      api = tess
      loaded = languages
      return tess
    } catch (e: Throwable) {
      if (e is UnsatisfiedLinkError || e is NoClassDefFoundError || e is ExceptionInInitializerError) broken = e
      throw e
    }
  }

  const val AssetDir = "fluid-ocr/tessdata"
}
