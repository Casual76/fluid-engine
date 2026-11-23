package dev.antigravity.fluidengine.viewer.office

import android.content.Context
import android.graphics.Typeface
import android.text.Layout
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.viewer.FluidViewerDetail

/**
 * Pass FluidEngineLicenses + FluidOfficeLicenses to fluidLicensesSection when distributing Office.
 */
val FluidOfficeLicenses: List<FluidLicense> =
  listOf(
    FluidLicense(
      "Carlito",
      "Łukasz Dziedzic et al.",
      "OFL-1.1",
      "https://github.com/googlefonts/carlito",
      "Misure di Calibri nel documento.",
    ),
    FluidLicense(
      "Caladea",
      "Huerta Tipográfica",
      "OFL-1.1",
      "https://github.com/huertatipografica/Caladea",
      "Misure di Cambria nel documento.",
    ),
    FluidLicense(
      "Liberation Sans, Serif e Mono",
      "Google e Red Hat",
      "OFL-1.1",
      "https://github.com/liberationfonts/liberation-fonts",
      "Misure di Arial, Times New Roman e Courier New.",
    ),
  )

internal val metricFamilies =
  listOf("Carlito", "Caladea", "Liberation Sans", "Liberation Serif", "Liberation Mono")

internal fun replacement(font: String): String =
  when (font.lowercase(java.util.Locale.ROOT)) {
    "calibri",
    "carlito" -> metricFamilies[0]
    "cambria",
    "caladea" -> metricFamilies[1]
    "arial",
    "helvetica",
    "liberation sans" -> metricFamilies[2]
    "times new roman",
    "liberation serif" -> metricFamilies[3]
    "courier new",
    "liberation mono" -> metricFamilies[4]
    "georgia",
    "garamond" -> "serif"
    else -> "sans-serif"
  }

internal class OfficeFonts(private val context: Context) {
  private val loaded = mutableMapOf<Triple<String, Boolean, Boolean>, Typeface>()

  @Synchronized
  fun face(style: Ink): Typeface {
    val family = replacement(style.font)
    return loaded.getOrPut(Triple(family, style.bold, style.italic)) {
      val index = metricFamilies.indexOf(family)
      val variant =
        when {
          style.bold && style.italic -> "BoldItalic"
          style.bold -> "Bold"
          style.italic -> "Italic"
          else -> "Regular"
        }
      if (index >= 0)
        Typeface.createFromAsset(context.assets, "office/latin${index + 1}-$variant.ttf")
      else
        Typeface.create(
          family,
          (if (style.bold) Typeface.BOLD else 0) or (if (style.italic) Typeface.ITALIC else 0),
        )
    }
  }
}

internal data class Ink(
  val font: String = "Calibri",
  val pt: Float = 11f,
  val bold: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val strike: Boolean = false,
  val color: Int = android.graphics.Color.BLACK,
  val highlight: Int = 0,
  val baseline: Int = 0,
)

internal data class Run(val text: String, val ink: Ink = Ink())

internal sealed interface Block

internal data class Paragraph(
  val runs: List<Run>,
  val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
  val before: Float = 0f,
  val after: Float = 0f,
  val left: Float = 0f,
  val right: Float = 0f,
  val indent: Float = 0f,
  val leading: Float = 1f,
  val justify: Boolean = false,
  val notes: List<String> = emptyList(),
  val keep: Boolean = false,
) : Block

internal data class Picture(val bytes: ByteArray, val w: Float, val h: Float) : Block

internal data class Cell(
  val paragraphs: List<Paragraph>,
  val span: Int = 1,
  val fill: Int? = null,
  val continued: Boolean = false,
  val continues: Boolean = false,
)

internal data class TableRow(val cells: List<Cell>, val header: Boolean = false)

internal data class Table(val rows: List<TableRow>, val widths: List<Float> = emptyList()) : Block

internal object PageBreak : Block

internal data class Paper(
  val width: Float = 612f,
  val height: Float = 792f,
  val left: Float = 72f,
  val top: Float = 72f,
  val right: Float = 72f,
  val bottom: Float = 72f,
  val headers: Map<String, String> = emptyMap(),
  val footers: Map<String, String> = emptyMap(),
  val firstDifferent: Boolean = false,
  val headerDistance: Float = 32f,
  val footerDistance: Float = 32f,
) {
  fun checked(): Paper {
    if (
      width !in 36f..4000f ||
        height !in 36f..4000f ||
        left < 0 ||
        right < 0 ||
        top < 0 ||
        bottom < 0 ||
        width - left - right < 12 ||
        height - top - bottom < 14
    )
      damaged("Dimensioni o margini del foglio non validi")
    return this
  }

  fun sheet() =
    FluidPageSheet(width, height, FluidPageMargins(left, top, right, bottom), Color.White)
}

internal data class Section(val paper: Paper, val blocks: List<Block>)

/** La sezione del pannello info con le note del relatore di una presentazione («Slide 3»: il testo). */
internal const val SpeakerNotesSection = "Note del relatore"

internal class OfficeInfo {
  val details = mutableListOf<FluidViewerDetail>()
  val fonts = linkedSetOf<String>()
  val omitted = linkedSetOf<String>()
  var nonLatin = false
  var words = 0

  fun observe(run: Run) {
    if (run.text.isBlank()) return
    fonts += run.ink.font
    var inWord = false
    for (c in run.text) {
      if (!c.isWhitespace() && !inWord) words++
      inWord = !c.isWhitespace()
      if (c.code in 0x370..0x8ff || c.code in 0x3000..0x9fff || c.code in 0xac00..0xd7af)
        nonLatin = true
    }
  }

  fun add(label: String, value: Any?, section: String) {
    if (value != null && value.toString().isNotBlank())
      details += FluidViewerDetail(label, value.toString(), section)
  }

  fun finish(): List<FluidViewerDetail> = buildList {
    addAll(details)
    if (words > 0 && details.none { it.label == "Parole" })
      add(FluidViewerDetail("Parole", words.toString(), "Contenuto"))
    for (font in fonts) {
      val target = replacement(font)
      add(
        FluidViewerDetail(
          font,
          if (target in metricFamilies) "$target · stesse misure"
          else "$target · misure approssimate",
          "Caratteri",
        )
      )
    }
    if (nonLatin)
      add(FluidViewerDetail("A capo", "Approssimati per i caratteri non latini", "Impaginazione"))
    for (part in omitted) add(FluidViewerDetail("Resa parziale", part, "Lettura"))
  }
}

internal fun alignment(value: String?) =
  when (value) {
    "center" -> Layout.Alignment.ALIGN_CENTER
    "right",
    "end" -> Layout.Alignment.ALIGN_OPPOSITE
    else -> Layout.Alignment.ALIGN_NORMAL
  }

internal fun simpleParagraph(text: String, ink: Ink = Ink()) = Paragraph(listOf(Run(text, ink)))
