package dev.antigravity.fluidengine.viewer.office

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridCell
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidGridSource
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.roundToInt
import kotlinx.coroutines.*

// F6C.15b · La miniatura di un documento Office non è mai bianca. Quella salvata nel file si usa solo se dice qualcosa;
// se manca, è rotta, minuscola o quasi tutta di un colore (Word e PowerPoint ne salvano spesso una bianca), la prima
// pagina, la prima slide o l'inizio del primo foglio si disegnano con il motore del visore, come fa Quick Look.

/**
 * How long drawing the first page may take before the app keeps its icon: a occhio, 23 novembre 2026. The readers are
 * the viewer's, so a document that opens in time also gives its thumbnail in time; on the JVM (Robolectric) the corpus
 * files take 20–70 ms. The check is cooperative: between reading, laying out and drawing.
 */
internal const val OfficeThumbnailMillis = 2_000L

/**
 * A saved thumbnail «says something» when at least this share of its samples differ from its main colour by more than
 * [OfficeThumbnailInkStep] in some channel. A blank Word or PowerPoint thumbnail has none; the thinnest real one of the
 * corpus (the sheet of `Bilancio.ods`) has about 5%, a page with only a title about 0,6%. A occhio, 23 novembre 2026.
 */
internal const val OfficeThumbnailInkShare = 0.0025f
internal const val OfficeThumbnailInkStep = 24

/** Up to this many samples per side when looking for ink: enough for text strokes, cheap on a 2048 px image. */
private const val InkSamples = 160

/** The thumbnail of [request] at most [sizePx] on its longer side; `null` keeps the app's icon. */
internal suspend fun officeThumbnail(
  request: FluidViewerRequest,
  sizePx: Int,
  budgetMillis: Long = OfficeThumbnailMillis,
): ImageBitmap? =
  withContext(Dispatchers.IO) {
    if (sizePx <= 0) return@withContext null
    val target = sizePx.coerceAtMost(1024)
    val ext = request.type.id
    val file = File.createTempFile("fluid-office-preview-", ".tmp", request.context.cacheDir)
    try {
      copyOfficeInput(request, file)
      if (ext == "rtf") {
        if (file.length() > 8_000_000) return@withContext null
        return@withContext withTimeoutOrNull(budgetMillis) {
          val fonts = OfficeFonts(request.context)
          drawnPage(firstPage(readRtf(file.readBytes(), OfficeInfo()), fonts), target)
        }
      }
      ZipFile(file).use { zip ->
        val pkg = OfficePackage(zip)
        if (ext.startsWith("od") && pkg.xml("META-INF/manifest.xml")?.first("encryption-data") != null)
          return@use null
        savedThumbnail(zip, pkg, target)?.let { return@use it.asImageBitmap() }
        withTimeoutOrNull(budgetMillis) {
          val fonts = OfficeFonts(request.context)
          val info = OfficeInfo()
          when (ext) {
            "docx" -> drawnPage(firstPage(WordReader(pkg, info).read(), fonts), target)
            "odt" -> drawnPage(firstPage(OdfReader(pkg, info).document(), fonts), target)
            "pptx" -> drawnPage(PowerPointReader(pkg, info, fonts).read().firstOrNull(), target)
            "odp" -> drawnPage(OdfReader(pkg, info).slides(fonts).firstOrNull(), target)
            "xlsx" ->
              drawnSheet(
                ExcelReader(pkg, info, request.context.resources.configuration.locales[0], fonts).read(),
                target,
              )
            "ods" -> drawnSheet(OdfReader(pkg, info).grid(), target)
            else -> null
          }
        }
      }
    } finally {
      file.delete()
    }
  }

/**
 * The thumbnail saved in the package (`Thumbnails/thumbnail.png` of ODF, `docProps/thumbnail.*` of OOXML), at most
 * [target] on its longer side, or `null` when it is missing, broken, tiny or nearly one colour.
 */
internal fun savedThumbnail(zip: ZipFile, pkg: OfficePackage, target: Int): Bitmap? {
  val entry =
    listOf("Thumbnails/thumbnail.png", "docProps/thumbnail.jpeg", "docProps/thumbnail.jpg", "docProps/thumbnail.png")
      .firstNotNullOfOrNull(zip::getEntry) ?: return null
  if (entry.size !in 1..4_000_000L) return null
  val bytes = try { pkg.bytes(entry.name) } catch (_: Exception) { null } ?: return null
  val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
  if (options.outWidth <= 0 || options.outHeight <= 0) return null
  // Minuscola: ingrandita più del doppio sarebbe sfocata, e la pagina disegnata è nitida a ogni misura.
  if (maxOf(options.outWidth, options.outHeight) * 2 < target || minOf(options.outWidth, options.outHeight) < 16)
    return null
  options.inJustDecodeBounds = false
  options.inSampleSize = 1
  while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2 * target) options.inSampleSize *= 2
  val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
  if (!saysSomething(decoded)) {
    decoded.recycle()
    return null
  }
  val scale = minOf(1f, target.toFloat() / maxOf(decoded.width, decoded.height))
  if (scale == 1f) return decoded
  return try {
    Bitmap.createScaledBitmap(
      decoded,
      maxOf(1, (decoded.width * scale).toInt()),
      maxOf(1, (decoded.height * scale).toInt()),
      true,
    )
  } finally {
    decoded.recycle()
  }
}

/**
 * Whether [bitmap] has something on it: enough samples away from its main colour ([OfficeThumbnailInkShare]). Pixels
 * are composed over white, so a transparent thumbnail counts as blank paper.
 */
internal fun saysSomething(bitmap: Bitmap): Boolean {
  val w = bitmap.width
  val h = bitmap.height
  if (w <= 0 || h <= 0) return false
  val columns = minOf(w, InkSamples)
  val rows = minOf(h, InkSamples)
  val samples = IntArray(columns * rows)
  val line = IntArray(w)
  for (r in 0 until rows) {
    bitmap.getPixels(line, 0, w, 0, (r * h) / rows, w, 1)
    for (c in 0 until columns) samples[r * columns + c] = overWhite(line[(c * w) / columns])
  }
  // The main colour: the fullest of 4096 boxes (four bits per channel), as the average of what fell in it.
  val counts = IntArray(4096)
  for (color in samples) counts[colorBox(color)]++
  val main = counts.indices.maxBy { counts[it] }
  var red = 0L
  var green = 0L
  var blue = 0L
  for (color in samples) if (colorBox(color) == main) {
    red += color shr 16 and 0xFF
    green += color shr 8 and 0xFF
    blue += color and 0xFF
  }
  val n = counts[main].coerceAtLeast(1)
  val r0 = (red / n).toInt()
  val g0 = (green / n).toInt()
  val b0 = (blue / n).toInt()
  var ink = 0
  for (color in samples) {
    val distance = maxOf(
      kotlin.math.abs((color shr 16 and 0xFF) - r0),
      kotlin.math.abs((color shr 8 and 0xFF) - g0),
      kotlin.math.abs((color and 0xFF) - b0),
    )
    if (distance > OfficeThumbnailInkStep) ink++
  }
  return ink >= 4 && ink >= samples.size * OfficeThumbnailInkShare
}

private fun colorBox(color: Int) = (color shr 20 and 0xF shl 8) or (color shr 12 and 0xF shl 4) or (color shr 4 and 0xF)

private fun overWhite(color: Int): Int {
  val alpha = color ushr 24
  if (alpha == 0xFF) return color and 0xFFFFFF
  fun mix(channel: Int) = (channel * alpha + 255 * (255 - alpha)) / 255
  return (mix(color shr 16 and 0xFF) shl 16) or (mix(color shr 8 and 0xFF) shl 8) or mix(color and 0xFF)
}

/** Lays out [sections] only up to their first page, then stops. */
private suspend fun firstPage(sections: List<Section>, fonts: OfficeFonts): OfficePage? {
  if (sections.isEmpty()) return null
  val pages = OfficePages(CoroutineScope(SupervisorJob() + Dispatchers.Default))
  try {
    pages.start(onPartial = {}) { paginate(sections, it, fonts) }
    return pages.at(0)
  } finally {
    // Il resto dell'impaginazione non serve: la chiusura la ferma.
    pages.close()
  }
}

private fun drawnPage(page: OfficePage?, target: Int): ImageBitmap? {
  page ?: return null
  val paper = page.paper
  val size =
    if (paper.width >= paper.height) IntSize(target, (target * paper.height / paper.width).roundToInt())
    else IntSize((target * paper.width / paper.height).roundToInt(), target)
  return drawOfficePage(page, size).asImageBitmap()
}

/**
 * The top left of the first sheet on white, as Quick Look shows a workbook: the used columns up to [SheetMaxWidth] and
 * the rows that fit a portrait page of that width, with the saved widths, fills, borders and text. No column letters:
 * the thumbnail shows the document, not the program.
 */
private suspend fun drawnSheet(grid: FluidGridSource, target: Int): ImageBitmap? {
  val sheet = grid.sheets.firstOrNull() ?: return null
  if (sheet.rowCount <= 0 || sheet.columnCount <= 0) return null
  fun width(column: Int) = (sheet.columnWidths[column] ?: sheet.columnWidth).coerceAtLeast(0f)
  fun height(row: Int) = (sheet.rowHeights[row] ?: sheet.rowHeight).coerceAtLeast(0f)
  var columns = 0
  var sheetWidth = 0f
  while (columns < sheet.columnCount && columns < 64 && sheetWidth < SheetMaxWidth) sheetWidth += width(columns++)
  sheetWidth = sheetWidth.coerceIn(SheetMinWidth, SheetMaxWidth)
  val pageWidth = sheetWidth + 2 * SheetMargin
  val pageHeight = pageWidth * SheetPageRatio
  var rows = 0
  var sheetHeight = 0f
  while (rows < 256 && sheetHeight < pageHeight - 2 * SheetMargin) sheetHeight += height(rows++)
  val cells = grid.cells(0, 0 until minOf(rows, sheet.rowCount), 0 until columns)
  currentCoroutineContext().ensureActive()
  val bitmap = Bitmap.createBitmap(
    maxOf(1, (target / SheetPageRatio).roundToInt()), target, Bitmap.Config.ARGB_8888,
  )
  val canvas = Canvas(bitmap)
  canvas.drawColor(android.graphics.Color.WHITE)
  canvas.scale(bitmap.width / pageWidth, bitmap.height / pageHeight)
  canvas.translate(SheetMargin, SheetMargin)
  canvas.clipRect(0f, 0f, sheetWidth, pageHeight - 2 * SheetMargin)
  val xs = FloatArray(columns + 1).also { for (c in 0 until columns) it[c + 1] = it[c] + width(c) }
  val ys = FloatArray(rows + 1).also { for (r in 0 until rows) it[r + 1] = it[r] + height(r) }
  val line = Paint().apply { color = SheetGridline; strokeWidth = 0.75f }
  for (x in xs) if (x <= sheetWidth) canvas.drawLine(x, 0f, x, ys[rows], line)
  for (y in ys) canvas.drawLine(0f, y, xs[columns], y, line)
  val merged = sheet.merges.filter { it.firstRow < rows && it.firstColumn < columns }
  val covered = HashSet<Long>()
  for (range in merged) for (r in range.firstRow..range.lastRow) for (c in range.firstColumn..range.lastColumn) {
    if (r != range.firstRow || c != range.firstColumn) covered += (r.toLong() shl 32) or c.toLong()
  }
  val fill = Paint()
  val text = TextPaint(Paint.ANTI_ALIAS_FLAG)
  for (cell in cells) {
    if ((cell.row.toLong() shl 32 or cell.column.toLong()) in covered) continue
    val range = merged.firstOrNull { it.firstRow == cell.row && it.firstColumn == cell.column }
    val right = range?.lastColumn?.plus(1)?.coerceAtMost(columns) ?: (cell.column + 1)
    val bottom = range?.lastRow?.plus(1)?.coerceAtMost(rows) ?: (cell.row + 1)
    val box = RectF(xs[cell.column], ys[cell.row], xs[right], ys[bottom])
    if (box.width() <= 0f || box.height() <= 0f) continue
    drawCell(canvas, cell, box, fill, text, merged = range != null)
  }
  return bitmap.asImageBitmap()
}

private fun drawCell(canvas: Canvas, cell: FluidGridCell, box: RectF, fill: Paint, text: TextPaint, merged: Boolean) {
  val look = cell.style
  if (look.fill.isSpecified) {
    fill.color = look.fill.toArgb()
    canvas.drawRect(box, fill)
  } else if (merged) {
    fill.color = android.graphics.Color.WHITE
    canvas.drawRect(box, fill)
  }
  val stroke = Paint().apply { strokeWidth = 1.5f }
  fun edge(color: androidx.compose.ui.graphics.Color, x0: Float, y0: Float, x1: Float, y1: Float) {
    if (!color.isSpecified) return
    stroke.color = color.toArgb()
    canvas.drawLine(x0, y0, x1, y1, stroke)
  }
  edge(look.borders.top, box.left, box.top, box.right, box.top)
  edge(look.borders.bottom, box.left, box.bottom, box.right, box.bottom)
  edge(look.borders.left, box.left, box.top, box.left, box.bottom)
  edge(look.borders.right, box.right, box.top, box.right, box.bottom)
  if (cell.text.isEmpty()) return
  text.typeface = Typeface.create(Typeface.SANS_SERIF,
    (if (look.bold) Typeface.BOLD else 0) or (if (look.italic) Typeface.ITALIC else 0))
  text.textSize = SheetTextSize * look.fontScale.coerceIn(0.25f, 8f)
  text.color = if (look.color.isSpecified) look.color.toArgb() else android.graphics.Color.BLACK
  text.isUnderlineText = look.underline
  text.isStrikeThruText = look.strikethrough
  val room = box.width() - 2 * SheetCellPadding
  if (room <= 0f) return
  val shown = TextUtils.ellipsize(cell.text.lineSequence().first(), text, room, TextUtils.TruncateAt.END).toString()
  val width = text.measureText(shown)
  val horizontal = look.horizontal ?: if (cell.numeric) Alignment.End else Alignment.Start
  val x = box.left + SheetCellPadding +
    horizontal.align(width.roundToInt(), room.roundToInt(), LayoutDirection.Ltr)
  val metrics = text.fontMetrics
  val lineHeight = metrics.descent - metrics.ascent
  val y = box.top + look.vertical.align(lineHeight.roundToInt(), box.height().roundToInt()) - metrics.ascent
  canvas.save()
  canvas.clipRect(box)
  canvas.drawText(shown, x, y.coerceAtMost(box.bottom - metrics.descent), text)
  canvas.restore()
}

/** The sheet thumbnail's page, in the grid's dp at 100%: a occhio, 23 novembre 2026. */
private const val SheetMaxWidth = 560f
private const val SheetMinWidth = 240f
private const val SheetMargin = 8f
private const val SheetPageRatio = 1.25f
private const val SheetCellPadding = 4f
/** Excel's 11 pt at 96 dpi, the size the saved column widths are measured with. */
private const val SheetTextSize = 14.67f
private const val SheetGridline = 0xFFD9D9D9.toInt()
