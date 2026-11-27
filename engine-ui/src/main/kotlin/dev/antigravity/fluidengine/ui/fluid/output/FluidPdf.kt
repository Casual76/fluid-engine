package dev.antigravity.fluidengine.ui.fluid.output

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.platform.LocalView
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Quante pagine sono uscite e quanto pesa il file. */
class FluidPdfResult(val pages: Int, val bytes: Long)

/**
 * Il PDF impaginato dell'engine (4.0, F8.10): il «Salva PDF» e la «Stampa» di una pagina Compose qualunque, non solo del
 * contenuto web. Il testo e' testo vero (il `Canvas` di `PdfDocument` lo scrive come testo, coi caratteri dentro il
 * file), le pagine hanno l'intestazione e il numero, e nessuna riga e' tagliata a meta'.
 *
 * Per un documento senza viste Compose basta [FluidPdf.write], anche lontano da una pagina; con le viste Compose serve
 * il [FluidPdfWriter] della pagina ([rememberFluidPdfWriter]).
 */
object FluidPdf {
  /**
   * Scrive [document] su [out] e ritorna quante pagine sono uscite. Lavora su `Dispatchers.Default` (impaginare) e
   * `Dispatchers.IO` (scrivere): mai sul thread principale. Un documento con viste Compose lancia
   * `IllegalArgumentException`: per quelle c'e' [FluidPdfWriter].
   *
   * La memoria: `PdfDocument` tiene ogni pagina finche' il file non e' scritto. Ogni pagina si prenota nel registro
   * dell'engine (`FluidMemoryBudget`, categoria `VIEWER`) prima di disegnarla; se non c'e' posto il lavoro si ferma con
   * [FluidOutputTooLargeException] invece di finire la memoria. L'impaginazione va a pezzi, un paragrafo alla volta.
   */
  suspend fun write(context: Context, document: FluidPdfDocument, out: OutputStream, style: FluidPdfStyle = FluidPdfStyle.Default): FluidPdfResult {
    require(!document.needsCompose) { "Un documento con viste Compose si scrive con FluidPdfWriter" }
    val budget = FluidGlassResources.get(context).budget
    val pdf = withContext(Dispatchers.Default) { fluidBuildPdf(document, style, null, budget) }
    return withContext(Dispatchers.IO) { pdf.writeAndClose(out) }
  }

  /**
   * L'impaginazione di [document] senza scrivere il file: quante pagine, e ogni pagina disegnata su una tela qualunque
   * (l'anteprima delle pagine prima di salvare, una miniatura). Senza viste Compose; per quelle [FluidPdfWriter.layout].
   *
   * **Impagina subito, sul thread di chi chiama** (R2): per un documento lungo chiamarla fuori dal thread principale
   * (`withContext(Dispatchers.Default) { FluidPdf.layout(documento) }`), e disegnare le pagine dove serve.
   */
  fun layout(document: FluidPdfDocument, style: FluidPdfStyle = FluidPdfStyle.Default): FluidPdfLayout {
    require(!document.needsCompose) { "Un documento con viste Compose si impagina con FluidPdfWriter" }
    return FluidPdfLayout(document, style, null)
  }

  /** [write] in un file nuovo della cartella delle uscite ([FluidShareFiles]). */
  suspend fun writeToFile(context: Context, document: FluidPdfDocument, fileName: String, style: FluidPdfStyle = FluidPdfStyle.Default): File {
    val file = withContext(Dispatchers.IO) { FluidShareFiles.newFile(context, fluidPdfName(fileName)) }
    withContext(Dispatchers.IO) { file.outputStream().buffered() }.use { write(context, document, it, style) }
    return file
  }
}

/**
 * Lo scrittore di PDF di una pagina Compose: conosce il tema (carattere e accento, [rememberFluidPdfStyle]) e sa
 * disegnare i pezzi [FluidPdfBlock.Compose]. Si ottiene con [rememberFluidPdfWriter].
 */
@Stable
class FluidPdfWriter internal constructor(
  private val view: View,
  private val parent: CompositionContext,
  /** Lo stile della carta preso dal tema della pagina. */
  val style: FluidPdfStyle,
) {
  internal val composer: FluidPdfComposer get() = FluidPdfComposer(view, parent)

  /** Scrive [document] su [out]. Le viste Compose si disegnano sul thread principale, il resto fuori. */
  suspend fun write(document: FluidPdfDocument, out: OutputStream): FluidPdfResult {
    val budget = FluidGlassResources.get(view.context).budget
    val pdf = if (document.needsCompose) {
      withContext(Dispatchers.Main.immediate) { fluidBuildPdf(document, style, composer, budget) }
    } else {
      withContext(Dispatchers.Default) { fluidBuildPdf(document, style, null, budget) }
    }
    return withContext(Dispatchers.IO) { pdf.writeAndClose(out) }
  }

  /** [write] in un file nuovo della cartella delle uscite, pronto per il foglio di condivisione. */
  suspend fun writeToFile(document: FluidPdfDocument, fileName: String): File {
    val file = withContext(Dispatchers.IO) { FluidShareFiles.newFile(view.context, fluidPdfName(fileName)) }
    withContext(Dispatchers.IO) { file.outputStream().buffered() }.use { write(document, it) }
    return file
  }

  /** L'impaginazione di [document], anche con le viste Compose (vedi [FluidPdf.layout]). Dal thread principale. */
  fun layout(document: FluidPdfDocument): FluidPdfLayout = FluidPdfLayout(document, style, composer)

  /** Apre l'anteprima di stampa di Android per [document] ([FluidPrint.print]). */
  fun print(document: FluidPdfDocument, jobName: String = document.title): android.print.PrintJob? =
    FluidPrint.printWith(view.context, document, jobName, style, composer)
}

/** Lo scrittore di PDF della pagina, con lo stile del suo tema. */
@Composable
fun rememberFluidPdfWriter(): FluidPdfWriter {
  val view = LocalView.current
  val parent = rememberCompositionContext()
  val style = rememberFluidPdfStyle()
  return remember(view, parent, style) { FluidPdfWriter(view, parent, style) }
}

internal fun fluidPdfName(name: String): String = if (name.lowercase().endsWith(".pdf")) name else "$name.pdf"

/**
 * Un documento impaginato (4.0, F8.10): le pagine sono decise, e ognuna si disegna su una tela qualunque in punti
 * (1/72 di pollice; per una bitmap si scala prima). E' la stessa impaginazione del PDF e della stampa: stesse righe,
 * stessi tagli fra le pagine, stessa intestazione e stesso numero.
 */
class FluidPdfLayout internal constructor(
  val document: FluidPdfDocument,
  private val style: FluidPdfStyle,
  private val composer: FluidPdfComposer?,
) {
  private val pages: List<FluidPdfPage> = fluidLayoutPdf(document, style, composer)

  /** Quante pagine. */
  val pageCount: Int get() = pages.size

  /** Disegna la pagina [number] (da 1) su [canvas], con l'origine in alto a sinistra della pagina e i punti come unita'. */
  fun draw(number: Int, canvas: Canvas) {
    require(number in 1..pages.size) { "Pagina $number di ${pages.size}" }
    fluidDrawPages(document, style, listOf(pages[number - 1]), object : FluidPdfSink {
      override fun begin(number: Int, widthPt: Int, heightPt: Int): Canvas = canvas
      override fun end() = Unit
    }, total = pages.size)
  }
}

// --- Le viste Compose dentro un PDF ------------------------------------------------------------------------------

/**
 * Compone, misura e disegna i pezzi Compose di un documento. Un dp vale [PtPerDp] punti; la tela lavora a [Oversample]
 * pixel per punto, cosi' i bordi da mezzo dp non si arrotondano a zero.
 */
internal class FluidPdfComposer(private val view: View, private val parent: CompositionContext?) {
  fun measure(block: FluidPdfBlock.Compose, widthPt: Float): Float {
    val widthPx = ceil(widthPt * Oversample).toInt()
    return fluidComposeOffscreen(view, parent, widthPx, null, PtPerDp * Oversample, block.content) { _, h -> h / Oversample }
  }

  fun draw(block: FluidPdfBlock.Compose, canvas: Canvas, widthPt: Float, heightPt: Float) {
    val widthPx = ceil(widthPt * Oversample).toInt()
    val heightPx = ceil(heightPt * Oversample).toInt().coerceAtLeast(1)
    fluidComposeOffscreen(view, parent, widthPx, heightPx, PtPerDp * Oversample, block.content) { composed, _ ->
      canvas.save()
      canvas.scale(1f / Oversample, 1f / Oversample)
      fluidDrawView(composed, canvas)
      canvas.restore()
    }
  }

  companion object {
    /** Un dp in punti: 0,75, cioe' un corpo da 16 sp a 12 pt (a occhio, 24 novembre 2026: la misura di un libro). */
    const val PtPerDp: Float = 0.75f

    /** Pixel per punto della tela delle viste Compose. */
    const val Oversample: Float = 4f
  }
}

// --- L'impaginazione ------------------------------------------------------------------------------------------------

/** Un pezzo pronto da mettere in pagina: alto [height] punti, spezzabile solo nei punti [breaks] (crescenti, l'ultimo e' [height]). */
internal abstract class FluidPdfPiece(val spaceBefore: Float) {
  abstract val height: Float
  abstract val breaks: FloatArray
  open val keepWithNext: Boolean = false

  /** Disegna la fetta [from]–[to] del pezzo con la cima della fetta all'origine della tela, larga [width]. */
  abstract fun draw(canvas: Canvas, from: Float, to: Float, width: Float)

  /** Il testo del pezzo, per le prove e l'accessibilita' del PDF. */
  open val text: String = ""
}

internal class FluidPdfTextPiece(
  private val layout: StaticLayout,
  spaceBefore: Float,
  override val keepWithNext: Boolean = false,
) : FluidPdfPiece(spaceBefore) {
  override val height: Float = layout.height.toFloat()
  override val breaks: FloatArray = FloatArray(layout.lineCount) { layout.getLineBottom(it).toFloat() }
  override val text: String = layout.text.toString()
  val lineCount: Int get() = layout.lineCount

  override fun draw(canvas: Canvas, from: Float, to: Float, width: Float) {
    canvas.save()
    // Il taglio cade sul fondo di una riga, che e' anche la cima della riga dopo. StaticLayout sceglie le righe da
    // disegnare dal ritaglio letto in pixel interi (arrotondato verso fuori): senza un margine la riga dopo, o quella
    // prima in cima alla pagina nuova, finirebbe nel PDF ritagliata via, invisibile ma presente nel testo che si
    // seleziona. Il margine sta dentro l'interlinea (un quarto del corpo, sopra i 2,5 pt a 11 pt), mai sui segni.
    // La prima e l'ultima fetta tengono aria fuori per gli accenti e le discendenti.
    val top = if (from > 0.01f) 1f else -4f
    val bottom = if (to >= height - 0.01f) to - from + 4f else to - from - 1.5f
    canvas.clipRect(-4f, top, width + 4f, bottom)
    canvas.translate(0f, -from)
    layout.draw(canvas)
    canvas.restore()
  }
}

internal class FluidPdfRowPiece(
  private val label: StaticLayout,
  private val value: StaticLayout,
  private val detail: StaticLayout?,
  private val gap: Float,
  spaceBefore: Float,
  /** L'altezza di una pagina: una riga piu' alta si spezza fra le righe, come un paragrafo (R2). */
  maxHeight: Float = Float.MAX_VALUE,
) : FluidPdfPiece(spaceBefore) {
  override val height: Float = max(label.height.toFloat() + (detail?.let { gap / 2 + it.height } ?: 0f), value.height.toFloat())

  /**
   * Una riga che sta in una pagina non si spezza mai. Una piu' alta (un'etichetta lunghissima) si spezza solo dove
   * nessuno dei tre testi ha una riga a meta': i fondi delle righe dell'etichetta e del dettaglio, sotto il valore.
   */
  override val breaks: FloatArray = if (height <= maxHeight) floatArrayOf(height) else fluidRowBreaks()

  override val text: String = listOfNotNull(label.text, value.text, detail?.text).joinToString(" ")

  private fun fluidRowBreaks(): FloatArray {
    val detailTop = label.height + gap / 2
    val candidates = sortedSetOf(height)
    for (i in 0 until label.lineCount) candidates += label.getLineBottom(i).toFloat()
    detail?.let { d -> for (i in 0 until d.lineCount) candidates += detailTop + d.getLineBottom(i) }
    fun cutsALine(layout: StaticLayout, top: Float, y: Float): Boolean {
      if (y <= top + 0.01f || y >= top + layout.height - 0.01f) return false
      return (0 until layout.lineCount).none { abs(top + layout.getLineBottom(it) - y) < 0.01f }
    }
    return candidates.filter { y ->
      !cutsALine(value, 0f, y) && !cutsALine(label, 0f, y) && (detail == null || !cutsALine(detail, detailTop, y))
    }.toFloatArray()
  }

  override fun draw(canvas: Canvas, from: Float, to: Float, width: Float) {
    canvas.save()
    // R2 · Come il pezzo di testo: solo la fetta [from, to]. Una riga piu' alta di una pagina non sborda oltre il fondo
    // e non ripete in cima alla pagina dopo le righe gia' scritte (il margine dentro l'interlinea, mai sui segni).
    if (from > 0.01f || to < height - 0.01f) {
      val top = if (from > 0.01f) 1f else -4f
      val bottom = if (to >= height - 0.01f) to - from + 4f else to - from - 1.5f
      canvas.clipRect(-4f, top, width + 4f, bottom)
    }
    canvas.translate(0f, -from)
    label.draw(canvas)
    detail?.let {
      canvas.save()
      canvas.translate(0f, label.height + gap / 2)
      it.draw(canvas)
      canvas.restore()
    }
    canvas.save()
    canvas.translate(width - value.width, 0f)
    value.draw(canvas)
    canvas.restore()
    canvas.restore()
  }
}

internal class FluidPdfRulePiece(private val color: Int, spaceBefore: Float, private val after: Float) : FluidPdfPiece(spaceBefore) {
  override val height: Float = 0.5f + after
  override val breaks: FloatArray = floatArrayOf(height)
  override fun draw(canvas: Canvas, from: Float, to: Float, width: Float) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@FluidPdfRulePiece.color; strokeWidth = 0.5f }
    canvas.drawLine(0f, 0.25f - from, width, 0.25f - from, paint)
  }
}

internal class FluidPdfSpacePiece(override val height: Float) : FluidPdfPiece(0f) {
  override val breaks: FloatArray = floatArrayOf(height)
  override fun draw(canvas: Canvas, from: Float, to: Float, width: Float) = Unit
}

internal class FluidPdfComposePiece(
  private val block: FluidPdfBlock.Compose,
  private val composer: FluidPdfComposer,
  private val measured: Float,
  private val maxHeight: Float,
  spaceBefore: Float,
) : FluidPdfPiece(spaceBefore) {
  /** Una vista piu' alta di una pagina si riduce fino a starci. */
  val scale: Float = if (measured > maxHeight) maxHeight / measured else 1f
  override val height: Float = measured * scale
  override val breaks: FloatArray = floatArrayOf(height)

  override fun draw(canvas: Canvas, from: Float, to: Float, width: Float) {
    canvas.save()
    canvas.translate((width - width * scale) / 2f, -from)
    canvas.scale(scale, scale)
    composer.draw(block, canvas, width, measured)
    canvas.restore()
  }
}

/** Un pezzo (o una sua fetta) in una pagina: da [from] a [to] del pezzo, con la cima a [y] punti dal margine. */
internal class FluidPdfPlacement(val piece: FluidPdfPiece, val from: Float, val to: Float, val y: Float)

/** Una pagina impaginata. */
internal class FluidPdfPage(val number: Int, val placements: List<FluidPdfPlacement>)

/** Le misure del testo della carta, in punti. A occhio (24 novembre 2026), sulle misure di una lettera stampata. */
internal object FluidPdfType {
  const val Title = 22f
  const val Subtitle = 11f
  const val Heading = 14f
  const val Body = 11f
  const val Detail = 9f
  const val Running = 8.5f
  const val LineSpacing = 1.25f
  const val HeaderGap = 18f
}

/** Prepara i pezzi di [document] per una colonna larga [width] punti. */
internal fun fluidPdfPieces(
  document: FluidPdfDocument,
  style: FluidPdfStyle,
  width: Float,
  contentHeight: Float,
  composer: FluidPdfComposer?,
): List<FluidPdfPiece> {
  val w = width.toInt().coerceAtLeast(1)
  fun paint(size: Float, bold: Boolean, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    typeface = if (bold) style.boldTypeface else style.typeface
    this.color = color
    textLocale = document.locale
  }
  fun layout(text: String, paint: TextPaint, width: Int = w, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
      .setAlignment(align)
      .setLineSpacing(0f, FluidPdfType.LineSpacing)
      .setIncludePad(false)
      .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
      .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
      .build()

  val pieces = ArrayList<FluidPdfPiece>()
  for ((index, block) in document.blocks.withIndex()) {
    val first = index == 0
    when (block) {
      is FluidPdfBlock.Title -> {
        pieces += FluidPdfTextPiece(layout(block.text, paint(FluidPdfType.Title, true, style.accent)), if (first) 0f else 18f, keepWithNext = true)
        block.subtitle?.let { pieces += FluidPdfTextPiece(layout(it, paint(FluidPdfType.Subtitle, false, style.secondaryInk)), 4f, keepWithNext = true) }
      }
      is FluidPdfBlock.Heading ->
        pieces += FluidPdfTextPiece(layout(block.text, paint(FluidPdfType.Heading, true, style.ink)), if (first) 0f else 16f, keepWithNext = true)
      is FluidPdfBlock.Paragraph ->
        pieces += FluidPdfTextPiece(layout(block.text, paint(FluidPdfType.Body, false, style.ink)), if (first) 0f else 8f)
      is FluidPdfBlock.Row -> {
        val valuePaint = paint(FluidPdfType.Body, true, style.ink)
        val valueWidth = min(ceil(StaticLayout.getDesiredWidth(block.value, valuePaint)).toInt() + 1, w / 2)
        val value = layout(block.value, valuePaint, valueWidth, Layout.Alignment.ALIGN_OPPOSITE)
        val labelWidth = (w - valueWidth - 12).coerceAtLeast(w / 3)
        val label = layout(block.label, paint(FluidPdfType.Body, false, style.ink), labelWidth)
        val detail = block.detail?.let { layout(it, paint(FluidPdfType.Detail, false, style.secondaryInk), labelWidth) }
        pieces += FluidPdfRowPiece(label, value, detail, gap = 3f, spaceBefore = if (first) 0f else 7f, maxHeight = contentHeight)
      }
      FluidPdfBlock.Divider -> pieces += FluidPdfRulePiece(style.rule, if (first) 0f else 8f, after = 0f)
      is FluidPdfBlock.Space -> pieces += FluidPdfSpacePiece(block.heightPt.coerceAtLeast(0f))
      is FluidPdfBlock.Compose -> {
        val c = requireNotNull(composer) { "Una vista Compose in un PDF chiede FluidPdfWriter" }
        pieces += FluidPdfComposePiece(block, c, c.measure(block, width), contentHeight, if (first) 0f else 10f)
      }
    }
  }
  return pieces
}

/**
 * L'impaginazione: i pezzi in fila nelle pagine, alte [contentHeight] punti. Un pezzo si spezza solo nei suoi punti
 * ([FluidPdfPiece.breaks]); un paragrafo lascia almeno due righe per parte quando ne ha quattro o piu' (niente righe
 * orfane o vedove); un titolo non resta solo in fondo: va alla pagina dopo col primo pezzo che lo segue.
 */
internal fun fluidPaginate(pieces: List<FluidPdfPiece>, contentHeight: Float): List<FluidPdfPage> {
  val pages = ArrayList<FluidPdfPage>()
  var current = ArrayList<FluidPdfPlacement>()
  var y = 0f
  fun newPage() {
    pages += FluidPdfPage(pages.size + 1, current)
    current = ArrayList()
    y = 0f
  }
  for ((index, piece) in pieces.withIndex()) {
    var from = 0f
    if (y > 0f) y += piece.spaceBefore
    // Un titolo e il primo pezzo (o la prima riga) che lo segue stanno insieme.
    if (piece.keepWithNext && y > 0f) {
      var need = piece.height
      var next = index + 1
      while (next < pieces.size) {
        val p = pieces[next]
        need += p.spaceBefore + (if (p.keepWithNext) p.height else p.breaks.first())
        if (!p.keepWithNext) break
        next++
      }
      if (y + need > contentHeight) newPage()
    }
    while (from < piece.height - 0.01f) {
      val avail = contentHeight - y
      val rest = piece.height - from
      if (rest <= avail + 0.01f) {
        current += FluidPdfPlacement(piece, from, piece.height, y)
        y += rest
        from = piece.height
        break
      }
      // In una pagina vuota le regole delle righe sole cedono: meglio una vedova che una riga tagliata.
      val cut = fluidBreakAt(piece.breaks, from, from + avail) ?: if (y == 0f) fluidBreakAt(piece.breaks, from, from + avail, strict = false) else null
      if (cut == null) {
        if (y == 0f) {
          // Neanche una riga entra in una pagina vuota (un pezzo piu' alto della pagina, gia' ridotto se Compose):
          // si taglia al bordo, l'unico caso in cui non c'e' scelta.
          current += FluidPdfPlacement(piece, from, from + avail, y)
          from += avail
        }
        newPage()
      } else {
        current += FluidPdfPlacement(piece, from, cut, y)
        from = cut
        newPage()
      }
    }
  }
  if (current.isNotEmpty() || pages.isEmpty()) pages += FluidPdfPage(pages.size + 1, current)
  return pages
}

/**
 * Il punto di taglio piu' basso fra [from] e [limit], con almeno due righe per parte quando il pezzo ne ha quattro o
 * piu'; null se non ce n'e'.
 */
internal fun fluidBreakAt(breaks: FloatArray, from: Float, limit: Float, strict: Boolean = true): Float? {
  val lines = breaks.size
  val start = breaks.indexOfFirst { it > from + 0.01f }
  if (start < 0) return null
  var best = -1
  for (i in start until lines) {
    if (breaks[i] <= limit + 0.01f) best = i else break
  }
  if (best < start) return null
  if (best == lines - 1) return breaks[best]
  if (strict && lines >= 4) {
    // Niente vedova: almeno due righe passano alla pagina dopo.
    if (lines - 1 - best < 2) best = lines - 3
    // Niente orfana: almeno due righe restano su questa pagina, o il pezzo va tutto alla pagina dopo.
    if (best - start + 1 < 2) return null
  }
  return if (best >= start) breaks[best] else null
}

// --- Il disegno delle pagine ----------------------------------------------------------------------------------------

/** Dove finiscono le pagine: il `PdfDocument` vero, o una tela di prova. */
internal interface FluidPdfSink {
  fun begin(number: Int, widthPt: Int, heightPt: Int): Canvas
  fun end()
}

/** Disegna [pages] in [sink]: intestazione, pezzi, numero di pagina. */
internal fun fluidDrawPages(
  document: FluidPdfDocument,
  style: FluidPdfStyle,
  pages: List<FluidPdfPage>,
  sink: FluidPdfSink,
  beforePage: (Int) -> Unit = {},
  total: Int = pages.size,
) {
  val size = document.pageSize
  val margin = document.marginPt
  val width = size.widthPt - margin * 2
  val headerHeight = fluidPdfHeaderHeight(document)
  val running = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = FluidPdfType.Running
    typeface = style.typeface
    color = style.secondaryInk
    textLocale = document.locale
  }
  val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.accent; strokeWidth = 0.75f }
  for (page in pages) {
    beforePage(page.number)
    val canvas = sink.begin(page.number, size.widthPt, size.heightPt)
    document.header?.let { header ->
      val line = TextUtils.ellipsize(header, running, width, TextUtils.TruncateAt.END).toString()
      canvas.drawText(line, margin, margin + FluidPdfType.Running, running)
      canvas.drawLine(margin, margin + FluidPdfType.Running + 5f, margin + width, margin + FluidPdfType.Running + 5f, rule)
    }
    val top = margin + headerHeight
    for (placement in page.placements) {
      canvas.save()
      canvas.translate(margin, top + placement.y)
      placement.piece.draw(canvas, placement.from, placement.to, width)
      canvas.restore()
    }
    if (document.footer) {
      val label = document.pageLabel(page.number, total)
      val x = (size.widthPt - running.measureText(label)) / 2f
      canvas.drawText(label, x, size.heightPt - margin + FluidPdfType.Running + 10f, running)
    }
    sink.end()
  }
}

internal fun fluidPdfHeaderHeight(document: FluidPdfDocument): Float =
  if (document.header != null) FluidPdfType.Running + 5f + FluidPdfType.HeaderGap else 0f

/** L'altezza della colonna del testo di una pagina. */
internal fun fluidPdfContentHeight(document: FluidPdfDocument): Float =
  document.pageSize.heightPt - document.marginPt * 2 - fluidPdfHeaderHeight(document)

/** I pezzi e le pagine di [document]. */
internal fun fluidLayoutPdf(document: FluidPdfDocument, style: FluidPdfStyle, composer: FluidPdfComposer?): List<FluidPdfPage> {
  val width = document.pageSize.widthPt - document.marginPt * 2
  val height = fluidPdfContentHeight(document)
  return fluidPaginate(fluidPdfPieces(document, style, width, height, composer), height)
}

/** Quanto si prenota per pagina: il disegno registrato di una pagina di testo pesa pochi KB, una vista Compose di piu'. */
internal const val FluidPdfPageBytes: Long = 24L * 1024
internal const val FluidPdfComposeBytes: Long = 96L * 1024

/** Un PDF costruito in memoria, con le prenotazioni delle sue pagine, da scrivere una volta e chiudere. */
internal class FluidBuiltPdf(private val pdf: PdfDocument, private val residents: List<FluidMemoryBudget.Resident>, val pages: Int) {
  fun writeAndClose(out: OutputStream): FluidPdfResult {
    val counting = FluidCountingStream(out)
    try {
      pdf.writeTo(counting)
      counting.flush()
    } finally {
      pdf.close()
      residents.forEach { it.close() }
    }
    return FluidPdfResult(pages, counting.count)
  }
}

/** Impagina e disegna [document] in un `PdfDocument`, prenotando ogni pagina nel registro. */
internal fun fluidBuildPdf(document: FluidPdfDocument, style: FluidPdfStyle, composer: FluidPdfComposer?, budget: FluidMemoryBudget): FluidBuiltPdf {
  val pages = fluidLayoutPdf(document, style, composer)
  val pdf = PdfDocument()
  val residents = ArrayList<FluidMemoryBudget.Resident>()
  var started: PdfDocument.Page? = null
  try {
    fluidDrawPages(document, style, pages, object : FluidPdfSink {
      override fun begin(number: Int, widthPt: Int, heightPt: Int): Canvas {
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(widthPt, heightPt, number).create())
        started = page
        return page.canvas
      }

      override fun end() {
        started?.let(pdf::finishPage)
        started = null
      }
    }, beforePage = { number ->
      val composeOnPage = pages[number - 1].placements.count { it.piece is FluidPdfComposePiece }
      val bytes = FluidPdfPageBytes + composeOnPage * FluidPdfComposeBytes
      val reservation = budget.tryReserve(FluidMemoryCategory.VIEWER, bytes)
        ?: throw FluidOutputTooLargeException("Il documento e' troppo lungo per la memoria di questo dispositivo (pagina $number)")
      residents += reservation.confirm() ?: run {
        reservation.close()
        throw FluidOutputTooLargeException("Il documento e' troppo lungo per la memoria di questo dispositivo (pagina $number)")
      }
    })
  } catch (t: Throwable) {
    started?.let { runCatching { pdf.finishPage(it) } }
    pdf.close()
    residents.forEach { it.close() }
    throw t
  }
  return FluidBuiltPdf(pdf, residents, pages.size)
}

internal class FluidCountingStream(private val out: OutputStream) : OutputStream() {
  var count: Long = 0
    private set

  override fun write(b: Int) { out.write(b); count++ }
  override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
  override fun flush() = out.flush()
}
