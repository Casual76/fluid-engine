package dev.antigravity.fluidengine.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The budget is the one part of a Glance widget a test can hold on to: everything below it is
 * RemoteViews, and the host truncates silently rather than failing.
 */
class EngineWidgetLayoutTest {

  @Test
  fun `the smallest cell keeps one row and drops everything optional`() {
    val layout = resolveEngineWidgetLayout(DpSize(180.dp, 110.dp), hasFooter = true)

    assertTrue(layout.compact)
    assertEquals(1, layout.rowLimit)
    assertFalse("a footer does not fit next to a row here", layout.showFooter)
    assertFalse(layout.showSubtitle)
  }

  @Test
  fun `height decides compactness as much as width does`() {
    assertTrue(resolveEngineWidgetLayout(DpSize(320.dp, 110.dp)).compact)
    assertTrue(resolveEngineWidgetLayout(DpSize(180.dp, 320.dp)).compact)
    assertFalse(resolveEngineWidgetLayout(DpSize(300.dp, 240.dp)).compact)
  }

  @Test
  fun `a footer takes its space from the rows`() {
    val withFooter = resolveEngineWidgetLayout(DpSize(180.dp, 180.dp), hasFooter = true)
    val withoutFooter = resolveEngineWidgetLayout(DpSize(180.dp, 180.dp), hasFooter = false)

    assertTrue(withFooter.showFooter)
    assertFalse(withoutFooter.showFooter)
    assertTrue(
      "nothing to report in the footer means the rows get the space back",
      withoutFooter.rowLimit > withFooter.rowLimit,
    )
  }

  @Test
  fun `a taller cell buys more rows`() {
    val tall = resolveEngineWidgetLayout(DpSize(300.dp, 300.dp), hasFooter = true)
    val standard = resolveEngineWidgetLayout(DpSize(300.dp, 240.dp), hasFooter = true)

    assertTrue(tall.rowLimit > standard.rowLimit)
  }

  @Test
  fun `a list stops being glanceable past the row cap`() {
    assertEquals(
      EngineWidgetMetrics.MaxRows,
      resolveEngineWidgetLayout(DpSize(300.dp, 900.dp), hasFooter = true).rowLimit,
    )
    assertEquals(2, resolveEngineWidgetLayout(DpSize(300.dp, 900.dp), maxRows = 2).rowLimit)
  }
}
