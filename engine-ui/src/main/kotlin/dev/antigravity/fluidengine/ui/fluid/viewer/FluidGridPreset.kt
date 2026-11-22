package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

// F6C.8 · Il preset minimo della forma `Grid`: fogli, celle a blocchi, righe e colonne bloccate, unioni. Non decodifica
// niente (le celle arrivano già formattate dal modulo di famiglia). Il disegno è quello essenziale di un foglio di
// calcolo; l'aspetto vero lo scelgono F6C.9 e le famiglie.

/** Labels of the grid preset. Numbers passed to labels are one based. */
data class FluidGridPresetStrings(
  val empty: String = "Foglio vuoto",
  val noSheets: String = "Nessun foglio",
  val error: String = "Impossibile leggere le celle",
  val retry: String = "Riprova",
  val sheets: String = "Fogli",
  val sheet: (name: String, current: Int, count: Int) -> String = { name, current, count -> "$name, foglio $current di $count" },
  val row: (row: Int) -> String = { "Riga $it" },
  val scrollEnd: String = "Colonne successive",
  val scrollStart: String = "Colonne precedenti",
  val zoomIn: String = "Ingrandisci",
  val zoomOut: String = "Riduci",
  val zoom: (percent: Int) -> String = { "Zoom $it%" },
)

/**
 * A spreadsheet or a table ([FluidViewerContent.Grid]) inside the [FluidFileViewer] renderer slot: rows scroll
 * vertically, columns sideways (the pager takes over at the edges), a pinch changes the zoom from half to four times.
 * The frozen rows and columns of the sheet stay in place; merged cells draw once across their block. With more than
 * one sheet a row of tabs sits above the bottom capsule while the chrome is visible. Cells are read in blocks around
 * what is visible and forgotten when far away. The source is borrowed while this preset is composed.
 */
@Composable
fun FluidGridPreset(
  content: FluidViewerContent.Grid,
  chromeVisible: Boolean,
  modifier: Modifier = Modifier,
  strings: FluidGridPresetStrings = FluidGridPresetStrings(),
) {
  val source = content.source
  val sheets = source.sheets
  if (sheets.isEmpty()) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.noSheets) }
    return
  }
  var selected by rememberSaveable(source) { mutableIntStateOf(0) }
  val index = selected.coerceIn(0, sheets.lastIndex)
  val sheet = sheets[index]
  key(source, index) {
    GridSheet(source, index, sheet, sheets, chromeVisible, strings, modifier) { selected = it }
  }
}

@Composable
private fun GridSheet(
  source: FluidGridSource,
  index: Int,
  sheet: FluidGridSheet,
  sheets: List<FluidGridSheet>,
  chromeVisible: Boolean,
  strings: FluidGridPresetStrings,
  modifier: Modifier,
  select: (Int) -> Unit,
) {
  val density = LocalDensity.current
  val theme = MaterialTheme.colorScheme
  val measurer = rememberTextMeasurer(cacheSize = GridTextCache)
  val typography = MaterialTheme.typography
  val baseStyle = if (sheet.headers) typography.bodyMedium else typography.bodySmall
  val headerStyle = typography.labelMedium
  // Coordinate sheets use the 15 sp reading body at 100%. Scale the saved dimensions by the
  // same ratio, including the device's text scale, independently of the user's pinch.
  val sheetDensity = if (sheet.headers) with(density) {
    baseStyle.fontSize.toPx() / typography.bodySmall.fontSize.value
  } else density.density
  val store = remember(source, index) { GridCellStore(source, index) }
  val fileColumns = remember(sheet) { GridAxis(sheet.columnCount, sheet.columnWidth, sheet.columnWidths) }
  val merges = remember(sheet) { GridMerges(sheet.merges) }
  var scale by rememberSaveable { mutableFloatStateOf(1f) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  var viewport by remember { mutableStateOf(IntSize.Zero) }
  val list = rememberLazyListState()
  val backdrop = rememberGlassBackdrop()

  val px = sheetDensity * scale
  val rowExtent = remember(sheet) {
    sheet.rowHeights.entries.fold(sheet.rowCount * sheet.rowHeight) { total, (row, height) ->
      if (row in 0 until sheet.rowCount) total + height.coerceAtLeast(0f) - sheet.rowHeight else total
    }
  }
  val visibleRows = if (sheet.headers) gridDisplayCount(sheet.rowCount, rowExtent, sheet.rowHeight, viewport.height / px) else sheet.rowCount
  val headerWidth = if (sheet.headers) gridRowHeaderWidth(visibleRows, measurer, headerStyle, sheetDensity) * scale else 0f
  val visibleColumns = if (sheet.headers) gridDisplayCount(sheet.columnCount, fileColumns.total, sheet.columnWidth,
    (viewport.width - headerWidth) / px) else sheet.columnCount
  val columns = remember(fileColumns, visibleColumns) {
    if (visibleColumns == sheet.columnCount) fileColumns else GridAxis(visibleColumns, sheet.columnWidth, sheet.columnWidths)
  }
  val headerHeight = if (sheet.headers) GridHeaderHeight * px else 0f
  val frozenColumns = sheet.frozenColumns.coerceIn(0, sheet.columnCount)
  val frozenRows = sheet.frozenRows.coerceIn(0, sheet.rowCount)
  val frozenWidth = columns.start(frozenColumns) * px
  val scrollArea = (viewport.width - headerWidth - frozenWidth).coerceAtLeast(0f)
  val maxOffset = ((fileColumns.total * px - frozenWidth) - scrollArea).coerceAtLeast(0f)
  LaunchedEffect(maxOffset) { if (offsetX > maxOffset) offsetX = maxOffset }
  val sideways = rememberScrollableState { delta ->
    val before = offsetX
    offsetX = (before - delta).coerceIn(0f, maxOffset)
    before - offsetX
  }
  val zoomTo: (Float) -> Unit = { requested ->
    val next = requested.coerceIn(GridMinScale, FluidZoom.DefaultMaxScale)
    if (next != scale) {
      offsetX *= next / scale
      scale = next
    }
  }
  val currentZoomTo by rememberUpdatedState(zoomTo)

  // Le celle attorno a ciò che si vede, a blocchi, più le celle che danno il contenuto alle unioni in vista.
  val currentHeaderWidth by rememberUpdatedState(headerWidth)
  LaunchedEffect(store, fileColumns, merges, sheetDensity) {
    snapshotFlow {
      val unit = sheetDensity * scale
      val visible = list.layoutInfo.visibleItemsInfo
      val firstRow = frozenRows + (visible.firstOrNull()?.index ?: 0)
      val lastRow = (frozenRows + (visible.lastOrNull()?.index ?: 0)).coerceAtMost(sheet.rowCount - 1)
      val origin = fileColumns.start(frozenColumns)
      val area = (viewport.width - currentHeaderWidth - origin * unit).coerceAtLeast(0f)
      val left = fileColumns.indexAt(origin + offsetX / unit).coerceAtLeast(frozenColumns)
      val right = fileColumns.indexAt(origin + (offsetX + area) / unit)
      GridWindow(firstRow, lastRow, left, right, frozenRows, frozenColumns) to store.attempt
    }.collectLatest { (window, _) -> store.ensure(window, merges) }
  }

  val gap = FluidScreenDefaults.HorizontalPadding / 2
  val top = FluidScreenDefaults.topBarHeight() + gap
  val tabs = sheets.size > 1
  val tabHeight = FluidGlassSize.Regular.touchTarget
  val bottomBar = FluidScreenDefaults.bottomContentPadding(FluidFloatingCapsuleDefaults.ActionSize)
  val bottom = bottomBar + if (tabs) tabHeight + gap else 0.dp
  val painter = GridPainter(
    measurer = measurer, sheet = sheet, columns = columns, merges = merges, store = store,
    style = baseStyle.copy(color = theme.onSurface), headerStyle = headerStyle.copy(color = theme.onSurfaceVariant),
    line = theme.outlineVariant, header = theme.surfaceContainer, density = sheetDensity, scale = scale,
    headerWidth = headerWidth, frozenColumns = frozenColumns, offsetX = { offsetX },
  )

  // The row list, horizontal scroll and outer pager all watch single-finger drags. Read changes
  // in the two-finger span before those handlers consume movement; leave single touches alone.
  Box(modifier.fillMaxSize().onSizeChanged { viewport = it }.clipToBounds().pointerInput(Unit) {
    awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.count { it.pressed && it.previousPressed } >= 2 && event.changes.none { it.isConsumed }) {
          val factor = event.calculateZoom()
          if (factor.isFinite() && factor > 0f && factor != 1f) {
            currentZoomTo(scale * factor)
            event.changes.forEach { it.consume() }
          }
        }
      } while (event.changes.any { it.pressed })
    }
  }) {
    Column(Modifier.fillMaxSize().padding(top = top)) {
      if (sheet.headers) {
        Box(Modifier.fillMaxWidth().height(with(density) { headerHeight.toDp() }).drawBehind { painter.drawColumnHeader(this) }
          .clearAndSetSemantics {})
      }
      for (row in 0 until frozenRows) {
        GridRow(painter, row, store, strings, Modifier)
      }
      if (sheet.rowCount == 0 || sheet.columnCount == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.empty) }
      } else {
        LazyColumn(
          state = list,
          modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop)
            .semantics {
              paneTitle = strings.sheet(sheet.name, index + 1, sheets.size)
              stateDescription = strings.zoom((scale * 100f).roundToInt())
              customActions = buildList {
                if (offsetX < maxOffset) add(CustomAccessibilityAction(strings.scrollEnd) {
                  offsetX = (offsetX + scrollArea * 0.8f).coerceAtMost(maxOffset); true
                })
                if (offsetX > 0f) add(CustomAccessibilityAction(strings.scrollStart) {
                  offsetX = (offsetX - scrollArea * 0.8f).coerceAtLeast(0f); true
                })
                if (scale < FluidZoom.DefaultMaxScale) add(CustomAccessibilityAction(strings.zoomIn) { zoomTo(scale * 1.5f); true })
                if (scale > GridMinScale) add(CustomAccessibilityAction(strings.zoomOut) { zoomTo(scale / 1.5f); true })
              }
            }
            .scrollable(sideways, Orientation.Horizontal, enabled = maxOffset > 0f),
          contentPadding = PaddingValues(bottom = bottom),
        ) {
          items(count = (visibleRows - frozenRows).coerceAtLeast(0)) { item ->
            GridRow(painter, frozenRows + item, store, strings, Modifier)
          }
        }
      }
    }
    val failure = store.failure
    if (failure != null) {
      Column(Modifier.align(Alignment.Center).fluidGlass(backdrop = backdrop, shape = FluidCapsuleShape)
        .padding(horizontal = gap * 2, vertical = gap), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(strings.error, style = MaterialTheme.typography.bodyMedium)
        Text(strings.retry, Modifier.heightIn(min = FluidGlassSize.Regular.touchTarget)
          .fluidPressable(onClick = { store.retry() }, role = Role.Button).wrapContentHeight(), color = theme.primary)
      }
    }
    if (tabs && chromeVisible) {
      Row(
        Modifier.align(Alignment.BottomCenter).padding(bottom = bottomBar - FluidScreenDefaults.ContentBottomSpacing + gap)
          .horizontalScroll(rememberScrollState()).padding(horizontal = FluidScreenDefaults.HorizontalPadding)
          .semantics { contentDescription = strings.sheets },
        horizontalArrangement = Arrangement.spacedBy(gap),
      ) {
        sheets.forEachIndexed { i, other ->
          val current = i == index
          Row(Modifier.heightIn(min = tabHeight).fluidGlass(backdrop = backdrop)
            .fluidPressable(onClick = { if (!current) select(i) }, role = Role.Tab)
            .semantics { selected = current; contentDescription = strings.sheet(other.name, i + 1, sheets.size) }
            .padding(horizontal = gap * 2), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap)) {
            other.color?.let { color -> Spacer(Modifier.size(7.dp).drawBehind { drawCircle(color) }) }
            Text(other.name,
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal),
              color = if (current) theme.primary else theme.onSurface, maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun GridRow(painter: GridPainter, row: Int, store: GridCellStore, strings: FluidGridPresetStrings, modifier: Modifier) {
  val density = LocalDensity.current
  val description = store.rowText(row)
  Box(modifier.fillMaxWidth().height(with(density) { painter.rowHeight(row).toDp() })
    .drawBehind { painter.drawRow(this, row) }
    .semantics { contentDescription = if (description.isEmpty()) strings.row(row + 1) else strings.row(row + 1) + ": " + description })
}

/** Empty trailing cells complete a spreadsheet viewport without changing the data source's extent. */
private fun gridDisplayCount(used: Int, extent: Float, step: Float, viewport: Float): Int {
  if (used == 0 || step <= 0f || extent >= viewport) return used
  return used + kotlin.math.ceil((viewport - extent) / step.coerceAtLeast(1f)).toInt()
}

/** The visible block: rows and columns, both ends included, plus the frozen ones that are always in view. */
@Immutable
internal data class GridWindow(
  val firstRow: Int, val lastRow: Int, val firstColumn: Int, val lastColumn: Int,
  val frozenRows: Int, val frozenColumns: Int,
)

/**
 * The positions along one axis, in dp at 100%: [start] of each index, the [total], and the index under a position. A
 * width of zero hides a column. Kept as a prefix array: a sheet has at most a few thousand columns.
 */
internal class GridAxis(val count: Int, default: Float, exceptions: Map<Int, Float>) {
  private val starts = FloatArray(count.coerceAtLeast(0) + 1).also { starts ->
    for (i in 0 until count.coerceAtLeast(0)) starts[i + 1] = starts[i] + (exceptions[i] ?: default).coerceAtLeast(0f)
  }

  val total: Float get() = starts[starts.lastIndex]

  fun start(index: Int): Float = starts[index.coerceIn(0, starts.lastIndex)]

  fun size(index: Int): Float = start(index + 1) - start(index)

  /** The index whose span contains [position]; clamped to the axis. A hidden index is never returned. */
  fun indexAt(position: Float): Int {
    if (count <= 0) return 0
    var low = 0
    var high = count - 1
    while (low < high) {
      val mid = (low + high + 1) ushr 1
      if (starts[mid] <= position) low = mid else high = mid - 1
    }
    return low
  }
}

/** The merged blocks of a sheet, looked up by row. Each cell belongs to at most one block. */
internal class GridMerges(ranges: List<FluidGridRange>) {
  val all: List<FluidGridRange> = ranges.filter { it.lastRow >= it.firstRow && it.lastColumn >= it.firstColumn &&
    (it.lastRow > it.firstRow || it.lastColumn > it.firstColumn) }
  private val byRow = HashMap<Int, MutableList<FluidGridRange>>().also { map ->
    for (range in all) for (row in range.firstRow..range.lastRow) map.getOrPut(row) { mutableListOf() } += range
  }

  fun inRow(row: Int): List<FluidGridRange> = byRow[row].orEmpty()

  fun at(row: Int, column: Int): FluidGridRange? = byRow[row]?.firstOrNull { column in it.firstColumn..it.lastColumn }
}

/** The automatic alignment of a cell: numbers to the end, text to the start, as spreadsheets do. */
internal fun gridHorizontal(cell: FluidGridCell): Alignment.Horizontal =
  cell.style.horizontal ?: if (cell.numeric) Alignment.End else Alignment.Start

/** A, B… Z, AA, AB…: the column letters of a spreadsheet. */
internal fun gridColumnName(column: Int): String {
  var n = column + 1
  val out = StringBuilder()
  while (n > 0) {
    val rest = (n - 1) % 26
    out.append('A' + rest)
    n = (n - 1) / 26
  }
  return out.reverse().toString()
}

private fun gridRowHeaderWidth(rowCount: Int, measurer: TextMeasurer, style: TextStyle, density: Float): Float {
  val digits = maxOf(2, rowCount.coerceAtLeast(1).toString().length)
  val sample = "0".repeat(digits)
  return measurer.measure(sample, style).size.width + 2 * GridCellPadding * density
}

/**
 * The cells read so far, by block. Reads one block at a time (the source is borrowed, maybe a parser on one thread),
 * keeps at most [MaxBlocks] and forgets the least recently used. A failed read stops the reading until [retry]. Lives
 * on the main thread: the reads come from composition and drawing, which follow [version].
 */
@Stable
internal class GridCellStore(private val source: FluidGridSource, private val sheet: Int) {
  private val cells = HashMap<Long, FluidGridCell>()
  private val byRow = HashMap<Int, java.util.TreeMap<Int, FluidGridCell>>()
  private val blocks = LinkedHashMap<Long, List<FluidGridCell>>(16, 0.75f, true)
  private var version by mutableIntStateOf(0)
  var attempt by mutableIntStateOf(0)
    private set
  var failure by mutableStateOf<Throwable?>(null)
    private set

  /** How many blocks are held: at most [MaxBlocks]. */
  val blockCount: Int get() = blocks.size

  operator fun get(row: Int, column: Int): FluidGridCell? {
    version
    return cells[key(row, column)]
  }

  /** The text of the loaded cells of [row], in column order, for TalkBack. */
  fun rowText(row: Int): String {
    version
    return byRow[row]?.values.orEmpty().filter { it.text.isNotEmpty() }
      .joinToString("; ") { "${gridColumnName(it.column)} ${it.text}" }
  }

  fun retry() {
    failure = null
    attempt++
  }

  suspend fun ensure(window: GridWindow, merges: GridMerges) {
    if (failure != null) return
    val wanted = LinkedHashSet<Long>()
    fun want(rows: IntRange, columns: IntRange) {
      if (rows.isEmpty() || columns.isEmpty() || rows.first < 0 || columns.first < 0) return
      for (blockRow in rows.first / BlockRows..rows.last / BlockRows) {
        for (blockColumn in columns.first / BlockColumns..columns.last / BlockColumns) wanted += key(blockRow, blockColumn)
      }
    }
    val rows = window.firstRow..window.lastRow
    val columns = window.firstColumn..window.lastColumn
    want(rows, columns)
    want(rows, 0 until window.frozenColumns)
    want(0 until window.frozenRows, columns)
    want(0 until window.frozenRows, 0 until window.frozenColumns)
    for (row in (0 until window.frozenRows) + rows) {
      for (range in merges.inRow(row)) if (range.lastColumn >= window.firstColumn || range.firstColumn < window.frozenColumns) {
        want(range.firstRow..range.firstRow, range.firstColumn..range.firstColumn)
      }
    }
    for (block in wanted) {
      if (blocks[block] != null) continue
      val blockRow = (block ushr 32).toInt()
      val blockColumn = (block and 0xFFFFFFFFL).toInt()
      val blockRows = blockRow * BlockRows until (blockRow + 1) * BlockRows
      val blockColumns = blockColumn * BlockColumns until (blockColumn + 1) * BlockColumns
      val read = try {
        source.cells(sheet, blockRows, blockColumns)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (e: Exception) {
        failure = e
        return
      }
      val kept = read.filter { it.row in blockRows && it.column in blockColumns }
      for (cell in kept) {
        cells[key(cell.row, cell.column)] = cell
        byRow.getOrPut(cell.row) { java.util.TreeMap() }[cell.column] = cell
      }
      blocks[block] = kept
      while (blocks.size > MaxBlocks) {
        val oldest = blocks.keys.first()
        blocks.remove(oldest)?.forEach { cell ->
          cells.remove(key(cell.row, cell.column))
          byRow[cell.row]?.let { row -> row.remove(cell.column); if (row.isEmpty()) byRow.remove(cell.row) }
        }
      }
      version++
    }
  }

  private fun key(row: Int, column: Int): Long = (row.toLong() shl 32) or (column.toLong() and 0xFFFFFFFFL)

  companion object {
    /** 64 × 32 cells per read, 48 blocks kept (about 100.000 cells): a occhio, 22 novembre 2026. */
    const val BlockRows = 64
    const val BlockColumns = 32
    const val MaxBlocks = 48
  }
}

/** Draws the rows of one sheet: the cells of the visible columns, the frozen columns over them, the row numbers. */
private class GridPainter(
  val measurer: TextMeasurer,
  val sheet: FluidGridSheet,
  val columns: GridAxis,
  val merges: GridMerges,
  val store: GridCellStore,
  val style: TextStyle,
  val headerStyle: TextStyle,
  val line: Color,
  val header: Color,
  val density: Float,
  val scale: Float,
  val headerWidth: Float,
  val frozenColumns: Int,
  val offsetX: () -> Float,
) {
  private val px = density * scale
  private val frozenRight = headerWidth + columns.start(frozenColumns) * px
  private val padding = GridCellPadding * px

  fun rowHeight(row: Int): Float = (sheet.rowHeights[row] ?: sheet.rowHeight).coerceAtLeast(0f) * px

  /** Where column [column] starts on screen. */
  private fun columnX(column: Int): Float =
    if (column < frozenColumns) headerWidth + columns.start(column) * px
    else frozenRight + (columns.start(column) - columns.start(frozenColumns)) * px - offsetX()

  fun drawColumnHeader(scope: DrawScope) = with(scope) {
    drawRect(header, size = size)
    val visible = visibleScrolling(size.width)
    clipRect(left = frozenRight) { for (c in visible) headerCell(c) }
    clipRect(left = headerWidth, right = frozenRight) { for (c in 0 until frozenColumns) headerCell(c) }
    drawLine(line, Offset(0f, size.height - Hairline), Offset(size.width, size.height - Hairline), Hairline)
  }

  private fun DrawScope.headerCell(column: Int) {
    val x = columnX(column)
    val w = columns.size(column) * px
    if (w <= 0f) return
    centered(gridColumnName(column), x, 0f, w, size.height, headerStyle)
    drawLine(line, Offset(x + w, 0f), Offset(x + w, size.height), Hairline)
  }

  fun drawRow(scope: DrawScope, row: Int) = with(scope) {
    val visible = visibleScrolling(size.width)
    clipRect(left = frozenRight) { for (c in visible) cell(row, c) }
    clipRect(left = headerWidth, right = frozenRight) { for (c in 0 until frozenColumns) cell(row, c) }
    for (range in merges.inRow(row)) {
      val anchor = store[range.firstRow, range.firstColumn]
      val scrolling = range.lastColumn >= frozenColumns
      val frozen = range.firstColumn < frozenColumns
      if (scrolling) clipRect(left = frozenRight) { merged(row, range, anchor) }
      if (frozen) clipRect(left = headerWidth, right = frozenRight) { merged(row, range, anchor) }
    }
    if (headerWidth > 0f) {
      drawRect(header, size = Size(headerWidth, size.height))
      centered("${row + 1}", 0f, 0f, headerWidth, size.height, headerStyle)
      drawLine(line, Offset(headerWidth - Hairline, 0f), Offset(headerWidth - Hairline, size.height), Hairline)
    }
  }

  private fun visibleScrolling(width: Float): IntRange {
    if (columns.count <= frozenColumns) return IntRange.EMPTY
    val origin = columns.start(frozenColumns)
    val left = columns.indexAt(origin + offsetX() / px).coerceAtLeast(frozenColumns)
    val right = columns.indexAt(origin + (offsetX() + (width - frozenRight).coerceAtLeast(0f)) / px)
    return left..right
  }

  private fun DrawScope.cell(row: Int, column: Int) {
    val x = columnX(column)
    val w = columns.size(column) * px
    if (w <= 0f || size.height <= 0f) return
    if (merges.at(row, column) != null) return
    val cell = store[row, column]
    box(cell, x, 0f, w, size.height)
    drawLine(line, Offset(x + w, 0f), Offset(x + w, size.height), Hairline)
    drawLine(line, Offset(x, size.height - Hairline), Offset(x + w, size.height - Hairline), Hairline)
  }

  /** The slice of a merged block that falls in [row]: the whole block drawn at its place, clipped to the row. */
  private fun DrawScope.merged(row: Int, range: FluidGridRange, anchor: FluidGridCell?) {
    val x = columnX(range.firstColumn)
    var w = 0f
    for (c in range.firstColumn..range.lastColumn) w += columns.size(c) * px
    var above = 0f
    for (r in range.firstRow until row) above += rowHeight(r)
    var h = 0f
    for (r in range.firstRow..range.lastRow) h += rowHeight(r)
    if (w <= 0f || h <= 0f) return
    clipRect(top = 0f, bottom = size.height) {
      box(anchor, x, -above, w, h)
      drawLine(line, Offset(x + w, 0f), Offset(x + w, size.height), Hairline)
      if (row == range.lastRow) drawLine(line, Offset(x, size.height - Hairline), Offset(x + w, size.height - Hairline), Hairline)
    }
  }

  /** Fill, borders and text of a cell (or of a merged block) at [x], [y] with size [w] × [h]. */
  private fun DrawScope.box(cell: FluidGridCell?, x: Float, y: Float, w: Float, h: Float) {
    val look = cell?.style ?: return
    if (look.fill.isSpecified) drawRect(look.fill, Offset(x, y), Size(w, h))
    val borders = look.borders
    val stroke = Hairline * 2
    if (borders.top.isSpecified) drawLine(borders.top, Offset(x, y), Offset(x + w, y), stroke)
    if (borders.bottom.isSpecified) drawLine(borders.bottom, Offset(x, y + h), Offset(x + w, y + h), stroke)
    if (borders.left.isSpecified) drawLine(borders.left, Offset(x, y), Offset(x, y + h), stroke)
    if (borders.right.isSpecified) drawLine(borders.right, Offset(x + w, y), Offset(x + w, y + h), stroke)
    if (cell.text.isEmpty()) return
    val factor = scale * look.fontScale
    val textStyle = style.copy(
      fontSize = style.fontSize.scaled(factor),
      lineHeight = style.lineHeight.scaled(factor),
      fontWeight = if (look.bold) FontWeight.Bold else style.fontWeight,
      fontStyle = if (look.italic) FontStyle.Italic else style.fontStyle,
      textDecoration = TextDecoration.combine(listOfNotNull(
        TextDecoration.Underline.takeIf { look.underline }, TextDecoration.LineThrough.takeIf { look.strikethrough })),
      color = if (look.color.isSpecified) look.color else style.color,
    )
    val room = (w - 2 * padding).roundToInt()
    if (room <= 0) return
    val layout = measurer.measure(cell.text, textStyle, overflow = TextOverflow.Clip, softWrap = look.wrap,
      maxLines = if (look.wrap) Int.MAX_VALUE else 1, constraints = Constraints(maxWidth = room))
    val left = x + padding + gridHorizontal(cell).align(layout.size.width, room, LayoutDirection.Ltr)
    val verticalPadding = minOf(padding, (h - layout.size.height).coerceAtLeast(0f) / 2)
    val top = y + look.vertical.align(layout.size.height, (h - 2 * verticalPadding).roundToInt().coerceAtLeast(0)) + verticalPadding
    clipRect(x, y, x + w, y + h) { drawText(layout, topLeft = Offset(left, top)) }
  }

  private fun DrawScope.centered(text: String, x: Float, y: Float, w: Float, h: Float, textStyle: TextStyle) {
    val sized = textStyle.copy(fontSize = textStyle.fontSize.scaled(scale), lineHeight = textStyle.lineHeight.scaled(scale))
    val layout = measurer.measure(text, sized, maxLines = 1, softWrap = false)
    drawText(layout, topLeft = Offset(x + (w - layout.size.width) / 2f, y + (h - layout.size.height) / 2f))
  }
}

private fun TextUnit.scaled(factor: Float): TextUnit = if (isSpecified) this * factor else this

/** The smallest zoom of the grid: half, to see more of a sheet on a phone. A occhio, 22 novembre 2026. */
private const val GridMinScale = 0.5f
/** The height of the column letters, and the padding inside a cell, in dp at 100%. */
private const val GridHeaderHeight = 24f
private const val GridCellPadding = 4f
private const val Hairline = 1f
private const val GridTextCache = 256
