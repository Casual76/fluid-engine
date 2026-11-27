package dev.antigravity.fluidengine.widget

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
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
  /**
   * La taglia piccola, come i widget piccoli di iOS (F8.25c): la cella e' piu' stretta di
   * [EngineWidgetMetrics.StackedWidth], quindi in una riga il valore a destra scende **sotto** il titolo, il titolo ha
   * tutta la larghezza (niente «Mat…» accanto a un orario) e le righe sono meno ([EngineWidgetMetrics.StackedRowHeight]).
   * Niente tessera dell'icona: in una cella cosi' ruberebbe il posto al nome.
   */
  val stacked: Boolean = false,
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

  /**
   * Sotto questa larghezza le righe si impilano ([EngineWidgetLayout.stacked]): 170 dp. Un 2×2 su un telefono va da
   * 130 dp (la griglia a 5 colonne: (73·2 − 16) dp, documentazione Android «App widget layouts») a circa 170 dp (One UI a
   * 4 colonne su un telefono da 411 dp); il piccolo di iPhone e' 158 pt (HIG «Widgets»). Sopra, nome e valore stanno
   * sulla stessa riga anche coi nomi lunghi. A occhio fra queste misure, 26/11/2026.
   */
  val StackedWidth: Dp = 170.dp

  /** Una riga impilata: il titolo a 13 sp sopra il valore a 11 sp, col padding verticale della riga compatta. */
  val StackedRowHeight: Dp = 44.dp
}

/**
 * [hasFooter] is asked for rather than assumed: a footer that has nothing to say should not be
 * drawn, and the row budget should get its space back instead of leaving a gap.
 *
 * [fontScale] e' la grandezza del carattere di sistema (Impostazioni › Display › Dimensioni carattere), quella con cui il
 * launcher disegna i testi del widget: di serie quella del sistema. I testi sono in `sp` e i margini in `dp`, quindi col
 * carattere grande una riga e' piu' alta di [EngineWidgetMetrics.RowHeight] e il budget cresce con lei (R3: a 1,3× le
 * righe concesse dal budget le tagliava il launcher, a meta'). Fino a 1× il budget e' quello delle misure. Se a questa
 * grandezza non ci sta nemmeno una riga, il widget prende la forma compatta (una riga per voce, l'intestazione senza
 * sottotitolo), come in una cella piccola.
 *
 * @throws IllegalArgumentException se [maxRows] e' meno di 1.
 */
fun resolveEngineWidgetLayout(
  size: DpSize,
  hasFooter: Boolean = false,
  maxRows: Int = EngineWidgetMetrics.MaxRows,
  fontScale: Float = engineWidgetSystemFontScale(),
): EngineWidgetLayout {
  require(maxRows >= 1) { "maxRows dev'essere almeno 1, non $maxRows" }
  val scale = if (fontScale.isFinite() && fontScale > 1f) fontScale else 1f
  val small = size.width < 240.dp || size.height < 150.dp
  val stacked = size.width < EngineWidgetMetrics.StackedWidth
  val (layout, fitting) = budget(size, hasFooter, maxRows, compact = small, stacked = stacked, scale = scale)
  if (!small && fitting == 0 && scale > 1f) return budget(size, hasFooter, maxRows, compact = true, stacked = stacked, scale = scale).first
  return layout
}

private fun budget(
  size: DpSize,
  hasFooter: Boolean,
  maxRows: Int,
  compact: Boolean,
  stacked: Boolean,
  scale: Float,
): Pair<EngineWidgetLayout, Int> {
  val padding = if (compact) EngineWidgetMetrics.CompactPadding else EngineWidgetMetrics.Padding
  // Le altezze seguono EngineWidgetComponents: l'intestazione col tasto da 36 dp (30 compatta) accanto al titolo da 17 sp
  // sopra il sottotitolo da 12 (15 sp da solo, compatta); la riga con 9 dp sopra e sotto (7 compatta o impilata), la
  // tessera da 30 dp (24) e il separatore sotto; la pillola in fondo con 9 dp (7) attorno al valore da 17 sp (15).
  val header = if (compact) {
    grown(EngineWidgetMetrics.CompactHeaderHeight, scale, fixed = 0.dp, minContent = 30.dp, 15f)
  } else {
    grown(EngineWidgetMetrics.HeaderHeight, scale, fixed = 0.dp, minContent = 36.dp, 17f, 12f)
  }
  val rowHeight = when {
    stacked -> grown(EngineWidgetMetrics.StackedRowHeight, scale, fixed = 15.dp, minContent = 0.dp, 13f, 11f)
    compact -> grown(EngineWidgetMetrics.CompactRowHeight, scale, fixed = 15.dp, minContent = 24.dp, 13f)
    else -> grown(EngineWidgetMetrics.RowHeight, scale, fixed = 19.dp, minContent = 30.dp, 15f, 12f)
  }
  val footer = if (compact) {
    grown(EngineWidgetMetrics.FooterHeight, scale, fixed = 14.dp, minContent = 0.dp, 15f)
  } else {
    grown(EngineWidgetMetrics.FooterHeight, scale, fixed = 18.dp, minContent = 0.dp, 17f)
  }

  var available = size.height - padding * 2 - header - EngineWidgetMetrics.Gap
  val showFooter = hasFooter &&
    available >= rowHeight + EngineWidgetMetrics.Gap + footer
  if (showFooter) {
    available -= footer + EngineWidgetMetrics.Gap
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
    stacked = stacked,
  ) to fitting
}

/**
 * L'altezza di un blocco alla grandezza del carattere [scale]: [base] fino a 1×; sopra, [fixed] (i dp che non crescono:
 * i margini, il separatore) piu' il piu' alto fra [minContent] (una tessera, un tasto) e le righe di testo [linesSp]
 * (ognuna col suo dp in piu': la riga del `TextView` si arrotonda ai pixel interi in su).
 */
private fun grown(base: Dp, scale: Float, fixed: Dp, minContent: Dp, vararg linesSp: Float): Dp {
  if (scale <= 1f) return base
  val text = linesSp.fold(0f) { sum, sp -> sum + sp * engineWidgetLineHeightPerSp * scale + 1f }.dp
  return maxOf(base, fixed + maxOf(minContent, text))
}

/**
 * L'altezza di una riga di `TextView` (il launcher disegna con quella il testo di Glance) per sp di testo, misurata sul
 * carattere di sistema del telefono (quello del produttore, se l'utente l'ha scelto): dall'alto al basso del carattere
 * (`FontMetrics.top`/`bottom`, la riga col margine del carattere, `includeFontPadding`), il piu' alto fra il normale, il
 * medio (le righe) e il grassetto. Con Roboto e' 1,327 (misurato nella prova di taratura di R3: una riga da 17 sp a 1× e'
 * alta 22,7 dp, a 2× 38,7 dp su 29 dp di testo). E' un limite superiore: senza il margine la riga e' piu' bassa, e la
 * scala non lineare di Android 14 fa il testo grande piu' piccolo del lineare. Dove non si puo' misurare (le prove sulla
 * JVM) 1,33, quello di Roboto: (yMax − yMin) / unitsPerEm = (2163 + 555) / 2048.
 */
private val engineWidgetLineHeightPerSp: Float by lazy {
  runCatching {
    listOf(Typeface.DEFAULT, Typeface.create("sans-serif-medium", Typeface.NORMAL), Typeface.DEFAULT_BOLD).maxOf { face ->
      val metrics = Paint().apply { typeface = face; textSize = 100f }.fontMetrics
      (metrics.bottom - metrics.top) / 100f
    }
  }.getOrNull()?.takeIf { it.isFinite() && it in 0.5f..3f } ?: 1.33f
}

/** La grandezza del carattere di sistema, quella dei testi del launcher; 1 dove non c'e' (le prove sulla JVM). */
internal fun engineWidgetSystemFontScale(): Float =
  runCatching { Resources.getSystem().configuration.fontScale }.getOrNull()?.takeIf { it.isFinite() && it > 0f } ?: 1f
