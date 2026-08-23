package dev.antigravity.fluidengine.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * How much of a list fits in the cell the launcher handed over.
 *
 * Glance has no measurement pass to fall back on: the content becomes RemoteViews and whatever does
 * not fit is cut off by the host, mid-row, with no callback to react to. The size is therefore
 * turned into an explicit budget — header, rows, a footer — and the content asks for exactly what
 * the budget allows.
 */
data class EngineWidgetLayout(
  val padding: Dp,
  val compact: Boolean,
  val rowLimit: Int,
  val showSubtitle: Boolean,
  val showFooter: Boolean,
)

object EngineWidgetMetrics {
  /** A two-line row: a 15sp title over a 12sp subtitle, plus the padding around them. */
  val RowHeight: Dp = 48.dp

  /** One line and a trailing value, for a cell too short to give a row two of them. */
  val CompactRowHeight: Dp = 34.dp

  val HeaderHeight: Dp = 48.dp
  val CompactHeaderHeight: Dp = 32.dp
  val FooterHeight: Dp = 38.dp
  val Gap: Dp = 8.dp
  val Padding: Dp = 14.dp
  val CompactPadding: Dp = 12.dp

  /** Beyond four rows a home-screen widget stops being glanceable and becomes a bad list. */
  const val MaxRows: Int = 4
}

/**
 * [hasFooter] is asked for rather than assumed: a footer that has nothing to say should not be
 * drawn, and the row budget should get its space back instead of leaving a gap.
 */
fun resolveEngineWidgetLayout(
  size: DpSize,
  hasFooter: Boolean = false,
  maxRows: Int = EngineWidgetMetrics.MaxRows,
): EngineWidgetLayout {
  val compact = size.width < 240.dp || size.height < 150.dp
  val padding = if (compact) EngineWidgetMetrics.CompactPadding else EngineWidgetMetrics.Padding
  val header = if (compact) EngineWidgetMetrics.CompactHeaderHeight else EngineWidgetMetrics.HeaderHeight
  val rowHeight = if (compact) EngineWidgetMetrics.CompactRowHeight else EngineWidgetMetrics.RowHeight

  var available = size.height - padding * 2 - header - EngineWidgetMetrics.Gap
  val showFooter = hasFooter &&
    available >= rowHeight + EngineWidgetMetrics.Gap + EngineWidgetMetrics.FooterHeight
  if (showFooter) {
    available -= EngineWidgetMetrics.FooterHeight + EngineWidgetMetrics.Gap
  }

  val fitting = if (available <= 0.dp) 0 else (available / rowHeight).toInt()
  // A widget showing only its header has nothing to say. When not even one row fits, the first is
  // drawn anyway and the host trims it — still more use than an empty card.
  val rowLimit = fitting.coerceIn(if (showFooter) 0 else 1, maxRows)

  return EngineWidgetLayout(
    padding = padding,
    compact = compact,
    rowLimit = rowLimit,
    showSubtitle = !compact,
    showFooter = showFooter,
  )
}
