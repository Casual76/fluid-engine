package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope

/** Metadata supplied by the owner of the file. A null size is unknown, zero means empty. */
data class FluidFileInfo(
  val name: String,
  val mimeType: String? = null,
  val sizeBytes: Long? = null,
  val kind: FluidFileKind = FluidFileKind.from(mimeType, name),
)

/** A player owned by the app. Publish changing properties as Compose state. Times are milliseconds. */
interface FluidMediaSession {
  val playing: Boolean
  val positionMillis: Long
  val durationMillis: Long?
  val canSeek: Boolean
  val error: String? get() = null
  fun play()
  fun pause()
  fun seekTo(positionMillis: Long)
}

/** Optional dimensions for fitting a video surface and bounding its zoom and pan. */
interface FluidVideoGeometry {
  val videoAspectRatio: Float?
}

/**
 * Zero-based pages. Both suspending operations must be safe to call on the main dispatcher.
 *
 * F6C.8: one contract for a PDF and for an Office document laid out by a family module (DOCX, ODT and RTF as pages,
 * PPTX and ODP as slides). [size] is the page in points (1/72 inch) for both; the preset reads only its aspect ratio
 * and picks the pixels to ask [render] for. A document still being laid out publishes a growing [count] as Compose
 * state and keeps [countFinal] false until the last page is known, so the first page shows before the whole layout.
 */
interface FluidPageSource {
  val count: Int
  suspend fun size(index: Int): IntSize
  suspend fun render(index: Int, target: IntSize): ImageBitmap

  /** False while [count] can still grow (a document being laid out). Publish it as Compose state. */
  val countFinal: Boolean get() = true

  /**
   * The sheet of page [index]: its exact measure, its margins and the paper it is printed on. Every section of a
   * document may change sheet and orientation. The default is [size] on white paper, without margins (a PDF).
   */
  suspend fun sheet(index: Int): FluidPageSheet = size(index).let { FluidPageSheet(it.width.toFloat(), it.height.toFloat()) }
}

/**
 * The sheet of a page, as the document declares it: [width] and [height] in points (1/72 inch, 72 dpi), the
 * [margins] in points and the [paper] colour behind the ink (also the colour to show while the page renders).
 */
@Immutable
data class FluidPageSheet(
  val width: Float,
  val height: Float,
  val margins: FluidPageMargins = FluidPageMargins.None,
  val paper: Color = Color.White,
) {
  /** Width over height, the only number the page preset needs. */
  val aspectRatio: Float get() = if (height > 0f) width / height else 0f

  companion object {
    /** ISO A4, 210 × 297 mm. */
    val A4: FluidPageSheet = FluidPageSheet(595.28f, 841.89f)
    /** US Letter, 8.5 × 11 in. */
    val Letter: FluidPageSheet = FluidPageSheet(612f, 792f)
  }
}

/** The margins of a [FluidPageSheet] in points, on the physical sides of the paper. */
@Immutable
data class FluidPageMargins(
  val left: Float = 0f,
  val top: Float = 0f,
  val right: Float = 0f,
  val bottom: Float = 0f,
) {
  companion object {
    val None: FluidPageMargins = FluidPageMargins()
  }
}

/** Read at most [count] lines from a zero-based offset. A short chunk marks the end of the file. */
interface FluidTextSource {
  val language: String? get() = null
  val lineCount: Int? get() = null
  suspend fun readLines(start: Int, count: Int): List<String>
}

/**
 * F6C.8 · A table of cells in one or more sheets: a spreadsheet (XLSX, ODS) from the Office family, a CSV or TSV from
 * the data family. Rows and columns are zero based. [sheets] may grow while the file is read: publish it as Compose
 * state. [cells] is safe to call on the main dispatcher; it reads a block and returns only the cells that have
 * something to show (text, fill or borders), in any order. Cells outside the block are ignored.
 */
interface FluidGridSource {
  val sheets: List<FluidGridSheet>
  suspend fun cells(sheet: Int, rows: IntRange, columns: IntRange): List<FluidGridCell>
}

/**
 * A sheet of a [FluidGridSource]. Lengths are dp at 100% zoom (a spreadsheet's pixels at 100%): [columnWidth] and
 * [rowHeight] by default, [columnWidths] and [rowHeights] where a column or a row differs; zero hides it. The first
 * [frozenRows] and [frozenColumns] stay in place while the rest scrolls. [headers] shows the column letters and the
 * row numbers of a spreadsheet; a CSV usually turns them off and freezes its heading row instead. [countFinal] is false
 * while [rowCount] can still grow (a file read as a stream).
 */
@Immutable
data class FluidGridSheet(
  val name: String,
  val rowCount: Int,
  val columnCount: Int,
  val frozenRows: Int = 0,
  val frozenColumns: Int = 0,
  val columnWidth: Float = DefaultColumnWidth,
  val rowHeight: Float = DefaultRowHeight,
  val columnWidths: Map<Int, Float> = emptyMap(),
  val rowHeights: Map<Int, Float> = emptyMap(),
  val merges: List<FluidGridRange> = emptyList(),
  val headers: Boolean = true,
  val countFinal: Boolean = true,
  /** Tab colour saved by the workbook, shown alongside the sheet name when present. */
  val color: Color? = null,
) {
  companion object {
    /** 80 × 24 dp: a spreadsheet's default column and row at 100% with room for a finger. A occhio, 22 novembre 2026. */
    const val DefaultColumnWidth: Float = 80f
    const val DefaultRowHeight: Float = 24f
  }
}

/** A block of cells, both ends included: a merged cell, or a selection. */
@Immutable
data class FluidGridRange(
  val firstRow: Int,
  val firstColumn: Int,
  val lastRow: Int,
  val lastColumn: Int,
) {
  operator fun contains(cell: Pair<Int, Int>): Boolean =
    cell.first in firstRow..lastRow && cell.second in firstColumn..lastColumn
}

/**
 * One cell as it shows: [text] is the value already formatted by the family (the saved result of a formula, a date in
 * its format). [numeric] aligns it to the end when [FluidGridStyle.horizontal] is null, as spreadsheets do.
 */
@Immutable
data class FluidGridCell(
  val row: Int,
  val column: Int,
  val text: String,
  val style: FluidGridStyle = FluidGridStyle.Default,
  val numeric: Boolean = false,
)

/**
 * How a cell looks. Unspecified colours take the theme's (ink, no fill, no border). [horizontal] null is automatic:
 * numbers at the end, text at the start. [fontScale] multiplies the grid's text size.
 */
@Immutable
data class FluidGridStyle(
  val bold: Boolean = false,
  val italic: Boolean = false,
  val underline: Boolean = false,
  val strikethrough: Boolean = false,
  val color: Color = Color.Unspecified,
  val fill: Color = Color.Unspecified,
  val horizontal: Alignment.Horizontal? = null,
  val vertical: Alignment.Vertical = Alignment.Bottom,
  val wrap: Boolean = false,
  val fontScale: Float = 1f,
  val borders: FluidGridBorders = FluidGridBorders.None,
) {
  companion object {
    val Default: FluidGridStyle = FluidGridStyle()
  }
}

/** The lines around a cell, one colour per side; unspecified draws only the grid's own hairline. */
@Immutable
data class FluidGridBorders(
  val top: Color = Color.Unspecified,
  val bottom: Color = Color.Unspecified,
  val left: Color = Color.Unspecified,
  val right: Color = Color.Unspecified,
) {
  companion object {
    val None: FluidGridBorders = FluidGridBorders()
  }
}

/**
 * The page colour of a [FluidViewerContent.Custom]: the theme's surface for documents and data, or black, as photos
 * and videos (scelta di Alessio del 21 novembre). Not an `enum` on purpose (rule 3.11): another value is a constant.
 */
@Immutable
@JvmInline
value class FluidViewerBackground private constructor(private val id: Int) {
  override fun toString(): String = if (id == BlackId) "Black" else "Theme"

  companion object {
    private const val ThemeId = 0
    private const val BlackId = 1

    val Theme: FluidViewerBackground = FluidViewerBackground(ThemeId)
    val Black: FluidViewerBackground = FluidViewerBackground(BlackId)
  }
}

/**
 * «Trova» on a [FluidViewerContent.Custom] page (F6B.6). The frame shows the button and the field above the keyboard;
 * the page searches its own content and highlights the [navigation]'s current match. Publish [query] and [navigation]
 * as Compose state.
 */
@Stable
interface FluidViewerFind {
  val query: String
  fun onQueryChange(query: String)
  val navigation: FluidSearchNavigation
  /** The button's label. */
  val label: String get() = "Trova"
}

/** What the frame tells a [FluidViewerContent.Custom] page while it draws. */
@Stable
interface FluidViewerCustomScope {
  /** False after a tap has hidden the bars. */
  val chromeVisible: Boolean
  /** True while «Trova» is open on this page. */
  val finding: Boolean
  /** True on the settled page; false while the pager or a finger carries it away, and on the pages next to it. */
  val active: Boolean
  /** Below the top bar and above the bottom capsule (and above the keyboard while finding): where content can rest. */
  val contentPadding: PaddingValues
}

/**
 * An open contract so applications and future presets may supply another representation.
 * The frame owns geometry and gestures. A renderer can read the sources without owning their lifecycle.
 */
interface FluidViewerContent {
  class Image(
    val aspectRatio: Float? = null,
    val draw: @Composable () -> Unit,
  ) : FluidViewerContent {
    constructor(painter: Painter, description: String? = null, aspectRatio: Float? = painter.intrinsicSize
      .takeIf { it.isSpecified && it.width > 0f && it.height > 0f }?.let { it.width / it.height }) :
      this(aspectRatio, { Image(painter, description, Modifier.fillMaxSize()) })
  }

  /** A non-null video surface identifies a video, even while it is buffering. */
  class Media(
    val session: FluidMediaSession,
    /** True for surfaces that the glass can sample, such as a TextureView or Compose content. */
    val sampleVideo: Boolean,
    val videoSurface: (@Composable () -> Unit)? = null,
    val artwork: (@Composable () -> Unit)? = null,
  ) : FluidViewerContent {
    constructor(session: FluidMediaSession, videoSurface: (@Composable () -> Unit)? = null,
      artwork: (@Composable () -> Unit)? = null) : this(session, false, videoSurface, artwork)
  }

  class Pages(val source: FluidPageSource) : FluidViewerContent
  class Text(val source: FluidTextSource) : FluidViewerContent
  class Unknown(
    val info: FluidFileInfo,
    val openWith: (() -> Unit)? = null,
    val share: (() -> Unit)? = null,
  ) : FluidViewerContent

  /** F6C.8 · A spreadsheet or a table: sheets of cells, frozen rows and columns, merges ([FluidGridSource]). */
  class Grid(val source: FluidGridSource) : FluidViewerContent

  /**
   * F6C.8 · A page that a module draws itself with the engine's public components (an archive's list, a JSON tree, a
   * book, a mail, a font, a 3D model). It declares its [background], whether it has «Trova» ([find]) and the
   * [actions] it adds to the viewer's bar («Estrai tutto», «Albero | Testo», «Presenta»). The frame keeps the chrome,
   * the pager, the info panel and dismissal; a black page is not zoomed by the frame, so its own gestures stay its own.
   */
  class Custom(
    val background: FluidViewerBackground = FluidViewerBackground.Theme,
    val find: FluidViewerFind? = null,
    val actions: FluidToolbarScope.() -> Unit = {},
    val draw: @Composable FluidViewerCustomScope.() -> Unit,
  ) : FluidViewerContent
}

internal val FluidViewerContent.blackBackground: Boolean
  get() = this is FluidViewerContent.Image || this is FluidViewerContent.Media && videoSurface != null ||
    this is FluidViewerContent.Custom && background == FluidViewerBackground.Black

/**
 * F6C.8: the pages the frame zooms and dismisses with a pinch, photos and videos. Before the Custom form this was the
 * same set as [blackBackground]; a black custom page (a 3D model) keeps its pinch and its drag.
 */
internal val FluidViewerContent.frameZoomed: Boolean
  get() = this is FluidViewerContent.Image || this is FluidViewerContent.Media && videoSurface != null
