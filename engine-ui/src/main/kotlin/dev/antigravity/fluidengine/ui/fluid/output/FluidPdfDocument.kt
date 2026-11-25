package dev.antigravity.fluidengine.ui.fluid.output

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import java.util.Locale

/** Il formato di una pagina, in punti (1/72 di pollice). [A4] di serie; [Letter] per chi stampa negli Stati Uniti. */
@Immutable
class FluidPdfPageSize(val name: String, val widthPt: Int, val heightPt: Int) {
  init {
    require(widthPt in 72..14400 && heightPt in 72..14400)
  }

  override fun toString(): String = "FluidPdfPageSize($name, ${widthPt}x$heightPt pt)"

  companion object {
    val A4: FluidPdfPageSize = FluidPdfPageSize("A4", 595, 842)
    val Letter: FluidPdfPageSize = FluidPdfPageSize("Letter", 612, 792)
  }
}

/**
 * Un pezzo di un documento da impaginare. Il testo ([Paragraph], i titoli, le righe) si scrive come testo vero, che nel
 * PDF si seleziona e si cerca; un [Compose] e' una vista dell'app disegnata come vettori e testo (un grafico, una card
 * col suo stile). Le pagine si spezzano solo **fra le righe** di un paragrafo o fra un pezzo e l'altro: mai una riga
 * tagliata a meta'.
 */
sealed class FluidPdfBlock {
  /** Il titolo del documento, nell'accento, con una riga sotto facoltativa. */
  class Title(val text: String, val subtitle: String? = null) : FluidPdfBlock()

  /** Il titolo di una sezione: non resta mai da solo in fondo a una pagina. */
  class Heading(val text: String) : FluidPdfBlock()

  /** Un paragrafo: va a capo e si spezza fra le righe, con almeno due righe per parte quando si puo'. */
  class Paragraph(val text: String) : FluidPdfBlock()

  /** Una riga «etichetta — valore» (una materia e il suo voto), con una nota sotto facoltativa. Non si spezza. */
  class Row(val label: String, val value: String, val detail: String? = null) : FluidPdfBlock()

  /** Un filo sottile fra due parti. */
  object Divider : FluidPdfBlock()

  /** Uno spazio vuoto di [heightPt] punti. */
  class Space(val heightPt: Float) : FluidPdfBlock()

  /**
   * Una vista Compose dell'app, larga quanto la colonna del testo e alta quanto serve: un dp vale 0,75 punti, cosi' un
   * corpo da 16 sp esce a 12 pt. Non si spezza: se e' piu' alta di una pagina si riduce fino a starci. Serve un
   * [FluidPdfWriter] (una pagina Compose che lo fornisce), e si disegna sul thread principale.
   */
  class Compose(val content: @Composable () -> Unit) : FluidPdfBlock()
}

/**
 * Un documento da impaginare in PDF o da stampare (4.0, F8.10): un titolo, un'intestazione ripetuta in cima a ogni
 * pagina, il numero di pagina in fondo, e i pezzi in fila. Si scrive con [fluidPdfDocument].
 *
 * @param header la riga in cima a ogni pagina (di serie il titolo); null per nessuna.
 * @param footer il numero di pagina in fondo («Pagina 2 di 5»): la frase la da' [pageLabel].
 * @param marginPt il margine, uguale sui quattro lati (di serie 48 pt, due terzi di pollice).
 * @param locale la lingua della sillabazione e dei numeri.
 */
@Immutable
class FluidPdfDocument(
  val title: String,
  val blocks: List<FluidPdfBlock>,
  val header: String? = title,
  val footer: Boolean = true,
  val pageSize: FluidPdfPageSize = FluidPdfPageSize.A4,
  val marginPt: Float = 48f,
  val locale: Locale = Locale.getDefault(),
  val pageLabel: (page: Int, pages: Int) -> String = { page, pages -> "Pagina $page di $pages" },
) {
  init {
    require(marginPt >= 0f && marginPt * 2 < minOf(pageSize.widthPt, pageSize.heightPt) - 72) { "Margini troppo larghi per la pagina" }
  }

  /** Vero se qualche pezzo e' una vista Compose (che va disegnata sul thread principale). */
  val needsCompose: Boolean get() = blocks.any { it is FluidPdfBlock.Compose }

  /** Lo stesso documento su un'altra pagina (la stampa sceglie il foglio). */
  fun withPageSize(size: FluidPdfPageSize): FluidPdfDocument =
    FluidPdfDocument(title, blocks, header, footer, size, marginPt, locale, pageLabel)
}

/** Il costruttore di [FluidPdfDocument]: un pezzo per chiamata, nell'ordine. */
class FluidPdfBuilder internal constructor() {
  internal val blocks = ArrayList<FluidPdfBlock>()

  fun title(text: String, subtitle: String? = null) { blocks += FluidPdfBlock.Title(text, subtitle) }
  fun heading(text: String) { blocks += FluidPdfBlock.Heading(text) }
  fun paragraph(text: String) { blocks += FluidPdfBlock.Paragraph(text) }
  fun row(label: String, value: String, detail: String? = null) { blocks += FluidPdfBlock.Row(label, value, detail) }
  fun divider() { blocks += FluidPdfBlock.Divider }
  fun space(heightPt: Float) { blocks += FluidPdfBlock.Space(heightPt) }
  fun compose(content: @Composable () -> Unit) { blocks += FluidPdfBlock.Compose(content) }
}

/** Un documento con i pezzi scritti in [build]: `fluidPdfDocument("Pagella") { title("Primo quadrimestre"); row("Matematica", "8") }`. */
fun fluidPdfDocument(
  title: String,
  header: String? = title,
  pageSize: FluidPdfPageSize = FluidPdfPageSize.A4,
  locale: Locale = Locale.getDefault(),
  build: FluidPdfBuilder.() -> Unit,
): FluidPdfDocument = FluidPdfDocument(title, FluidPdfBuilder().apply(build).blocks.toList(), header, pageSize = pageSize, locale = locale)

/**
 * Lo stile della carta: il carattere dell'app e il suo accento, e l'inchiostro scuro della stampa. La carta e' sempre
 * chiara, anche con il tema scuro: un PDF si legge e si stampa su bianco. L'accento si scurisce finche' sul bianco non
 * arriva al contrasto 4,5:1 delle regole dell'engine.
 */
@Immutable
class FluidPdfStyle(
  val typeface: Typeface = Typeface.DEFAULT,
  val boldTypeface: Typeface = Typeface.DEFAULT_BOLD,
  accent: Int = 0xFF0A63D1.toInt(),
  val ink: Int = 0xFF1C1C1E.toInt(),
  val secondaryInk: Int = 0xFF5E5E63.toInt(),
  val rule: Int = 0x33000000,
) {
  /** L'accento leggibile sul bianco. */
  val accent: Int = fluidReadableOnWhite(accent)

  companion object {
    val Default: FluidPdfStyle = FluidPdfStyle()
  }
}

/** Lo stile della carta preso dal tema: il carattere del corpo e dei titoli e l'accento. */
@Composable
fun rememberFluidPdfStyle(): FluidPdfStyle {
  val resolver = LocalFontFamilyResolver.current
  val typography = MaterialTheme.typography
  val regular by resolver.resolveAsTypeface(typography.bodyLarge.fontFamily, FontWeight.Normal)
  val bold by resolver.resolveAsTypeface(typography.titleLarge.fontFamily ?: typography.bodyLarge.fontFamily, FontWeight.SemiBold)
  return FluidPdfStyle(typeface = regular, boldTypeface = bold, accent = MaterialTheme.colorScheme.primary.toArgb())
}

/** Scurisce [argb] finche' sul bianco non arriva a 4,5:1 (WCAG); un colore gia' leggibile resta com'e'. */
internal fun fluidReadableOnWhite(argb: Int): Int {
  var color = Color(argb).copy(alpha = 1f)
  var steps = 0
  while (fluidContrastOnWhite(color) < 4.5f && steps < 20) {
    color = lerp(color, Color.Black, 0.1f)
    steps++
  }
  return color.toArgb()
}

internal fun fluidContrastOnWhite(color: Color): Float = (1.05f) / (color.luminance() + 0.05f)
