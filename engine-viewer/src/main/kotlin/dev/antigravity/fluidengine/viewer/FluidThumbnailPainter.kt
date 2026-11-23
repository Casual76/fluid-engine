package dev.antigravity.fluidengine.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * F6C.17 · Gli attrezzi con cui engine-viewer e i moduli di famiglia disegnano le miniature, nella grammatica di File e
 * Quick Look di iOS 26 (`docs/liquid-glass/ricerca/visori-2.md` §2 e §3):
 * - **il foglio** ([page]): la miniatura di un documento è la sua prima pagina, su carta bianca in chiaro e in scuro,
 *   come Quick Look. Testo, codice, Markdown, tabelle, alberi e mail si disegnano qui sopra;
 * - **il simbolo** ([symbol], [kindSymbol]): un'icona su fondo trasparente, per i tipi che non hanno una pagina
 *   (l'archivio, la data di un evento, le iniziali di un contatto, l'icona di un'app);
 * - **l'icona del documento** ([documentIcon]): il foglio con l'angolo piegato e l'estensione, quella di File per un
 *   tipo che nessun modulo legge;
 * - **la figura** ([picture]): un'immagine che riempie la tessera (una foto, un modello 3D disegnato).
 *
 * Ogni immagine fatta qui porta con sé come sta nella tessera: [FluidFileThumbnails] la legge e la mette in
 * [FluidFileThumbnail.fillsFrame] e [FluidFileThumbnail.isPlaceholder]. Un'immagine fatta altrove vale come una pagina
 * (o una figura, per le immagini e i video).
 *
 * Tutto si disegna con `android.graphics` su una bitmap ARGB, fuori dal filo principale: niente Compose, niente
 * finestre, e lo stesso disegno sulla JVM (Robolectric) e sul dispositivo.
 */
object FluidThumbnailPainter {
  /**
   * Il foglio dei documenti: 3:4, verticale, come le miniature di File per testo e codice. A occhio, 23 novembre 2026
   * (fiducia bassa sulle proporzioni esatte di Apple, che dipendono dal formato della pagina).
   */
  const val PageAspect: Float = 0.75f

  /** Il foglio delle tabelle (CSV, TSV): 4:3, orizzontale, perché si legga «un pezzo di tabella». A occhio, 23 novembre 2026. */
  const val TableAspect: Float = 4f / 3f

  /** Il lato lungo più piccolo e più grande di una miniatura, in pixel: oltre, un'anteprima costa più di quanto mostra. */
  const val MinSizePx: Int = 32
  const val MaxSizePx: Int = 1024

  /** La carta: bianca anche nel tema scuro, come Quick Look (§2, «per i documenti è bianco»). */
  const val Paper: Int = 0xFFFFFFFF.toInt()

  /** L'inchiostro sulla carta, e quello attenuato (date, didascalie, intestazioni secondarie). Dai grigi di sistema di Apple. */
  const val Ink: Int = 0xFF1D1D1F.toInt()
  const val InkSecondary: Int = 0xFF6E6E73.toInt()

  /** Le righe sottili (la griglia di una tabella, il bordo dell'icona del documento) e il fondo delle intestazioni. */
  const val Rule: Int = 0xFFD1D1D6.toInt()
  const val Fill: Int = 0xFFF2F2F7.toInt()

  /** Il blu dei collegamenti sulla carta (systemBlue di Apple, chiaro). */
  const val Link: Int = 0xFF007AFF.toInt()

  /** Il margine del foglio, sul lato corto: 8%, a occhio, 23 novembre 2026. */
  const val PageMargin: Float = 0.08f

  /**
   * Quante righe di testo stanno nell'altezza di un foglio: 17 per il codice, 15 per la prosa. Abbastanza da
   * «leggere le prime righe» su un tablet e da vedere la forma del testo su un telefono. A occhio, 23 novembre 2026.
   */
  const val CodeLinesPerPage: Int = 17
  const val ProseLinesPerPage: Int = 15

  /** Il lato lungo di [sizePx] riportato fra [MinSizePx] e [MaxSizePx]. */
  fun clampSize(sizePx: Int): Int = sizePx.coerceIn(MinSizePx, MaxSizePx)

  /**
   * Un foglio bianco di lato lungo [sizePx] e proporzioni [aspect] (larghezza su altezza). [draw] riceve la tela, il
   * rettangolo dentro i margini e l'unità del testo (l'altezza di una riga di prosa). Con [clip] la tela è tagliata
   * ai margini (il testo lungo non esce); senza, si può disegnare a vivo (una copertina).
   */
  fun page(sizePx: Int, aspect: Float = PageAspect, clip: Boolean = true,
    draw: (canvas: Canvas, content: RectF, unit: Float) -> Unit): ImageBitmap {
    val size = clampSize(sizePx)
    val (w, h) = if (aspect >= 1f) size to max(1, (size / aspect).roundToInt()) else max(1, (size * aspect).roundToInt()) to size
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Paper)
    val margin = min(w, h) * PageMargin
    val content = RectF(margin, margin, w - margin, h - margin)
    canvas.save()
    if (clip) canvas.clipRect(content)
    draw(canvas, content, h / ProseLinesPerPage.toFloat())
    canvas.restore()
    return tag(bitmap, fills = false, placeholder = false)
  }

  /** Una tela quadrata trasparente di lato [sizePx] per un simbolo. [placeholder] vero: non mostra il contenuto. */
  fun symbol(sizePx: Int, placeholder: Boolean = false, draw: (canvas: Canvas, size: Float) -> Unit): ImageBitmap {
    val size = clampSize(sizePx)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    draw(Canvas(bitmap), size.toFloat())
    return tag(bitmap, fills = false, placeholder = placeholder)
  }

  /** [bitmap] come figura che riempie la tessera (una foto, un fotogramma, un modello disegnato). */
  fun picture(bitmap: Bitmap): ImageBitmap = tag(bitmap, fills = true, placeholder = false)

  /** Un pennello per il testo sulla carta. */
  fun paint(textSize: Float, color: Int = Ink, monospace: Boolean = false, bold: Boolean = false): TextPaint =
    TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
      this.textSize = textSize
      this.color = color
      linkColor = Link
      typeface = when {
        monospace && bold -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        monospace -> Typeface.MONOSPACE
        bold -> Typeface.DEFAULT_BOLD
        else -> Typeface.DEFAULT
      }
    }

  /**
   * Il testo [text], con i suoi span (colori, grassetto, dimensioni), largo [width]. Con [wrap] falso ogni riga resta
   * intera e il foglio la taglia, come il codice nel visore.
   */
  fun layout(text: CharSequence, paint: TextPaint, width: Int, wrap: Boolean = true, spacing: Float = 1.18f): StaticLayout {
    val layoutWidth = if (wrap) max(1, width) else max(1, width * 6)
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
      .setAlignment(Layout.Alignment.ALIGN_NORMAL)
      .setLineSpacing(0f, spacing)
      .setIncludePad(false)
      .setBreakStrategy(if (wrap) Layout.BREAK_STRATEGY_SIMPLE else Layout.BREAK_STRATEGY_SIMPLE)
      .build()
  }

  /** Disegna [layout] con l'angolo in alto a sinistra in ([x], [y]); dà la y sotto l'ultima riga. */
  fun draw(canvas: Canvas, layout: StaticLayout, x: Float, y: Float): Float {
    canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restore()
    return y + layout.height
  }

  /** [text] tagliato con «…» per stare in [width]. */
  fun ellipsize(text: CharSequence, paint: TextPaint, width: Float): CharSequence =
    TextUtils.ellipsize(text, paint, max(1f, width), TextUtils.TruncateAt.END)

  /**
   * Il colore di un ruolo di sintassi sulla carta: i colori di Xcode in chiaro, gli stessi della tavola D (parole chiave
   * viola, tipi blu, stringhe rosse, numeri blu scuro, commenti grigi). Fissi, perché la carta è bianca in tutti e due
   * i temi. A occhio, 23 novembre 2026.
   */
  fun syntaxColor(role: FluidSyntaxRole): Int = when (role) {
    FluidSyntaxRole.Keyword -> 0xFF9B2393.toInt()
    FluidSyntaxRole.Type -> 0xFF0B4F79.toInt()
    FluidSyntaxRole.String -> 0xFFC41A16.toInt()
    FluidSyntaxRole.Number -> 0xFF1C00CF.toInt()
    FluidSyntaxRole.Comment -> 0xFF5D6C79.toInt()
    FluidSyntaxRole.Tag -> 0xFF0F68A0.toInt()
    FluidSyntaxRole.Attribute -> 0xFF815F03.toInt()
    FluidSyntaxRole.Heading -> Ink
    FluidSyntaxRole.Link -> Link
    FluidSyntaxRole.Code -> 0xFF643820.toInt()
    else -> Ink
  }

  /** Le righe [lines] colorate con [highlighter] (o in un colore solo, senza), una per riga, pronte per [layout]. */
  fun syntaxText(lines: List<String>, highlighter: FluidSyntaxHighlighter?): CharSequence {
    val out = SpannableStringBuilder()
    var state = 0
    for ((index, raw) in lines.withIndex()) {
      if (index > 0) out.append('\n')
      val line = raw.replace("\t", "  ")
      val start = out.length
      out.append(line)
      if (highlighter != null) {
        val styled = highlighter.line(line, state)
        state = styled.endState
        for (span in styled.spans) {
          val from = start + span.start.coerceIn(0, line.length)
          val to = start + span.end.coerceIn(0, line.length)
          if (to <= from) continue
          out.setSpan(ForegroundColorSpan(syntaxColor(span.role)), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          if (span.role == FluidSyntaxRole.Keyword || span.role == FluidSyntaxRole.Heading) {
            out.setSpan(StyleSpan(Typeface.BOLD), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          }
          if (span.role == FluidSyntaxRole.Comment) {
            out.setSpan(StyleSpan(Typeface.ITALIC), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
          }
        }
      }
    }
    return out
  }

  /**
   * Un foglio con le prime righe di codice, coi colori: carattere a spaziatura fissa, senza a capo, [CodeLinesPerPage]
   * righe nell'altezza.
   */
  fun codePage(lines: List<String>, highlighter: FluidSyntaxHighlighter?, sizePx: Int): ImageBitmap =
    linesPage(syntaxText(lines.take(CodeLinesPerPage + 1), highlighter), sizePx)

  /**
   * Un foglio di righe che non vanno a capo ([text], una per `\n`, coi suoi span): [linesPerPage] righe nell'altezza,
   * a spaziatura fissa se [monospace]. Serve al codice, agli alberi di dati e ai testi strutturati.
   */
  fun linesPage(text: CharSequence, sizePx: Int, monospace: Boolean = true, linesPerPage: Int = CodeLinesPerPage,
    aspect: Float = PageAspect): ImageBitmap =
    page(sizePx, aspect) { canvas, content, _ ->
      val line = content.height() / linesPerPage
      val paint = paint(line / 1.3f, monospace = monospace)
      draw(canvas, layout(text, paint, content.width().toInt(), wrap = false, spacing = 1.3f / 1.17f), content.left, content.top)
    }

  /** Un foglio di prosa: [text] (anche con i suoi span) va a capo nella colonna, [ProseLinesPerPage] righe nell'altezza. */
  fun prosePage(text: CharSequence, sizePx: Int, aspect: Float = PageAspect): ImageBitmap =
    page(sizePx, aspect) { canvas, content, unit ->
      val paint = paint(unit / 1.32f)
      draw(canvas, layout(text, paint, content.width().toInt(), spacing = 1.12f), content.left, content.top)
    }

  /**
   * L'icona di File per un tipo che non mostra il contenuto: un foglio bianco con l'angolo piegato, il bordo grigio e
   * [extension] in maiuscolo sotto il centro. Uguale nei due temi, come in iOS. È un segnaposto: dice il tipo, non il
   * contenuto. Le misure (foglio alto il 62% del lato, piega del 26%) sono a occhio, 23 novembre 2026.
   */
  fun documentIcon(extension: String, sizePx: Int): ImageBitmap = symbol(sizePx, placeholder = true) { canvas, size ->
    val h = size * 0.62f
    val w = h * 0.78f
    val left = (size - w) / 2f
    val top = (size - h) / 2f
    val fold = w * 0.26f
    val radius = w * 0.07f
    val sheet = Path().apply {
      moveTo(left + radius, top)
      lineTo(left + w - fold, top)
      lineTo(left + w, top + fold)
      lineTo(left + w, top + h - radius)
      quadTo(left + w, top + h, left + w - radius, top + h)
      lineTo(left + radius, top + h)
      quadTo(left, top + h, left, top + h - radius)
      lineTo(left, top + radius)
      quadTo(left, top, left + radius, top)
      close()
    }
    val stroke = max(1f, size / 160f)
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1F000000 }
    canvas.save()
    canvas.translate(0f, stroke * 1.5f)
    canvas.drawPath(sheet, shadow)
    canvas.restore()
    canvas.drawPath(sheet, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Paper })
    canvas.drawPath(sheet, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Rule; style = Paint.Style.STROKE; strokeWidth = stroke })
    val corner = Path().apply {
      moveTo(left + w - fold, top)
      lineTo(left + w - fold, top + fold - radius)
      quadTo(left + w - fold, top + fold, left + w - fold + radius, top + fold)
      lineTo(left + w, top + fold)
      close()
    }
    canvas.drawPath(corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Fill })
    canvas.drawPath(corner, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Rule; style = Paint.Style.STROKE; strokeWidth = stroke })
    val label = extension.removePrefix(".").uppercase(Locale.ROOT).take(6)
    if (label.isNotEmpty()) {
      val paint = paint(w * 0.17f, InkSecondary, bold = true).apply { textAlign = Paint.Align.CENTER }
      while (paint.measureText(label) > w * 0.84f && paint.textSize > 4f) paint.textSize *= 0.9f
      canvas.drawText(label, left + w / 2f, top + h * 0.72f, paint)
    }
  }

  /**
   * Il simbolo di un tipo nello stile della pagina «D · Mix»: una tessera arrotondata con la tinta leggera del file (la
   * stessa sfumatura della pagina dell'archivio, più piena perché la tessera è piccola: [FluidThumbnailLook.accent] al
   * 30% in alto, al 10% al centro, [FluidThumbnailLook.accentSecondary] al 30% in basso, sulla superficie; a occhio,
   * 23 novembre 2026), il glifo nell'accento e, sotto, [label] in una capsula. Mai una tessera grigia. [placeholder]
   * vero se il simbolo dice solo il tipo.
   */
  fun kindSymbol(
    glyph: ImageVector,
    label: String?,
    sizePx: Int,
    look: FluidThumbnailLook = FluidThumbnailLook.Light,
    placeholder: Boolean = false,
  ): ImageBitmap = symbol(sizePx, placeholder) { canvas, size ->
    val tile = size * 0.56f
    val left = (size - tile) / 2f
    val top = size * (if (label.isNullOrEmpty()) 0.22f else 0.14f)
    val rect = RectF(left, top, left + tile, top + tile)
    val radius = tile * 0.2237f
    val accent = look.accent.toArgb()
    val second = look.accentSecondary.toArgb()
    val surface = look.surface.toArgb()
    val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
        intArrayOf(blend(surface, accent, 0.30f), blend(surface, accent, 0.10f), blend(surface, second, 0.30f)),
        floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (look.dark) 0x40000000 else 0x1A000000 }
    canvas.drawRoundRect(RectF(rect).apply { offset(0f, size / 120f) }, radius, radius, shadow)
    canvas.drawRoundRect(rect, radius, radius, wash)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = max(1f, size / 200f)
      color = blend(surface, accent, 0.28f)
    }
    canvas.drawRoundRect(rect, radius, radius, border)
    val inset = tile * 0.22f
    drawGlyph(canvas, glyph, RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), accent)
    if (!label.isNullOrEmpty()) {
      val text = label.uppercase(Locale.ROOT).take(8)
      val paint = paint(size * 0.075f, accent, bold = true).apply { textAlign = Paint.Align.CENTER }
      val width = paint.measureText(text)
      val capsuleH = paint.textSize * 1.75f
      val capsuleW = width + capsuleH
      val cy = rect.bottom + size * 0.1f + capsuleH / 2f
      val capsule = RectF(size / 2f - capsuleW / 2f, cy - capsuleH / 2f, size / 2f + capsuleW / 2f, cy + capsuleH / 2f)
      canvas.drawRoundRect(capsule, capsuleH / 2f, capsuleH / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = blend(surface, accent, 0.16f)
      })
      val metrics = paint.fontMetrics
      canvas.drawText(text, size / 2f, cy - (metrics.ascent + metrics.descent) / 2f, paint)
    }
  }

  /** Disegna l'icona [vector] (un `ImageVector` di `FluidIcons`) dentro [bounds], nel colore [color]. */
  fun drawGlyph(canvas: Canvas, vector: ImageVector, bounds: RectF, color: Int) {
    val scale = min(bounds.width() / vector.viewportWidth, bounds.height() / vector.viewportHeight)
    val matrix = Matrix().apply {
      setScale(scale, scale)
      postTranslate(bounds.centerX() - vector.viewportWidth * scale / 2f, bounds.centerY() - vector.viewportHeight * scale / 2f)
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    fun walk(group: VectorGroup) {
      for (node in group) {
        when (node) {
          is VectorGroup -> walk(node)
          is VectorPath -> {
            val path = node.pathData.toPath().asAndroidPath()
            path.fillType = if (node.pathFillType == PathFillType.EvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
            path.transform(matrix)
            canvas.drawPath(path, paint)
          }
        }
      }
    }
    walk(vector.root)
  }

  /** [a] verso [b] di [t] (0–1), alfa compresa. */
  fun blend(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    fun mix(x: Int, y: Int) = (x + (y - x) * u).roundToInt().coerceIn(0, 255)
    return Color.argb(mix(Color.alpha(a), Color.alpha(b)), mix(Color.red(a), Color.red(b)), mix(Color.green(a), Color.green(b)),
      mix(Color.blue(a), Color.blue(b)))
  }

  // --- Come sta nella tessera ------------------------------------------------------------------------------------

  /** Come un'immagine fatta qui sta nella tessera: la legge [FluidFileThumbnails]. */
  internal class Fit(val fills: Boolean, val placeholder: Boolean)

  private val fits: MutableMap<Bitmap, Fit> = Collections.synchronizedMap(WeakHashMap())

  internal fun tag(bitmap: Bitmap, fills: Boolean, placeholder: Boolean): ImageBitmap {
    fits[bitmap] = Fit(fills, placeholder)
    return bitmap.asImageBitmap()
  }

  internal fun fitOf(bitmap: Bitmap): Fit? = fits[bitmap]
}
