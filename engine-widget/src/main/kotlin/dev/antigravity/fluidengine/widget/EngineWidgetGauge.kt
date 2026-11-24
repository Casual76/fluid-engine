package dev.antigravity.fluidengine.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import dev.antigravity.fluidengine.ui.fluid.FluidDataLabels
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeReading
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeThreshold
import dev.antigravity.fluidengine.ui.fluid.fluidGaugeArcAngles
import dev.antigravity.fluidengine.ui.fluid.fluidGaugeDescription
import dev.antigravity.fluidengine.ui.fluid.fluidGaugeReading
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The gauge on the home screen (4.0, F8.6): `FluidGauge` for Glance, with the same reading, the same shapes and the same
 * sentence for TalkBack — the arc open at the bottom with the number in it, the small ring, the capacity bar.
 *
 * Glance has no canvas. The shapes are therefore drawn once into **white masks** (one bitmap for the track, one for the
 * value, one per threshold mark) and tinted by the host with the palette's colour providers, so the widget follows the
 * launcher between light and dark without being redrawn. The number and the labels stay real Glance text: they scale
 * with the system font and the host renders them crisply.
 *
 * Widgets do not animate: the gauge shows the value it was composed with. The app updates the widget when the value
 * changes, as with every other widget.
 *
 * @param value the value, in the unit of [range]. Outside the range it stops at the end, and the number stays the true one.
 * @param valueText the number as written («18 %»): the app formats it, as in its own screens.
 * @param minText, maxText the ends as written; empty hides them.
 * @param thresholds from each one up the value takes its colour, and the track has a mark. A colour left `Unspecified`
 *   takes the palette's warning tone, the highest one the danger tone; a given colour is used as is, in both themes.
 * @param thresholdTexts the thresholds' values as written, in the same order (for the legend and TalkBack).
 * @param tone the colour of the value below every threshold; the accent by default.
 * @param size the diameter of arc and ring; the bar takes [barWidth].
 * @param barWidth the width of the capacity bar: Glance cannot measure, so the cell width minus its padding.
 */
@Composable
fun EngineWidgetGauge(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  palette: EngineWidgetPalette,
  layout: EngineWidgetLayout,
  valueText: String,
  modifier: GlanceModifier = GlanceModifier,
  style: FluidGaugeStyle = FluidGaugeStyle.Ring,
  label: String? = null,
  minText: String = "",
  maxText: String = "",
  thresholds: List<FluidGaugeThreshold> = emptyList(),
  thresholdTexts: List<String> = thresholds.map { engineWidgetPlainNumber(it.value) },
  tone: ColorProvider = palette.accent,
  size: Dp = if (style == FluidGaugeStyle.Ring) EngineWidgetGaugeMetrics.RingSize else EngineWidgetGaugeMetrics.ArcSize,
  barWidth: Dp = EngineWidgetGaugeMetrics.BarWidth,
  labels: FluidDataLabels = FluidDataLabels(),
) {
  val reading = fluidGaugeReading(value, range, thresholds)
  val active = if (reading.active >= 0) engineWidgetThresholdColor(reading.active, thresholds, palette) else tone
  val spoken = fluidGaugeDescription(valueText, minText, maxText, thresholds, thresholdTexts, reading, labels)
  val description = if (label.isNullOrBlank()) spoken else "$label. $spoken"
  val scale = LocalContext.current.resources.displayMetrics.density.coerceIn(1f, EngineWidgetGaugeMetrics.MaxBitmapScale)

  when (style) {
    FluidGaugeStyle.Capacity -> Column(modifier = modifier.semantics { contentDescription = description }) {
      Row(modifier = GlanceModifier.width(barWidth), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = label.orEmpty(),
          style = engineWidgetTextStyle(palette.onSurface, if (layout.compact) 12.sp else 13.sp, FontWeight.Medium),
          maxLines = 1,
          modifier = GlanceModifier.defaultWeight(),
        )
        Text(text = valueText, style = engineWidgetTextStyle(palette.onSurface, if (layout.compact) 13.sp else 15.sp, FontWeight.Bold), maxLines = 1)
      }
      Spacer(GlanceModifier.height(4.dp))
      val masks = engineWidgetGaugeMasks(FluidGaugeStyle.Capacity, reading, barWidth.value, EngineWidgetGaugeMetrics.BarHeight.value, scale)
      GaugeLayers(masks, thresholds, palette, active, GlanceModifier.width(barWidth).height(EngineWidgetGaugeMetrics.BarHeight))
      if (minText.isNotEmpty() || maxText.isNotEmpty()) {
        Row(modifier = GlanceModifier.width(barWidth)) {
          Text(minText, style = engineWidgetTextStyle(palette.onSurfaceVariant, 11.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
          Text(maxText, style = engineWidgetTextStyle(palette.onSurfaceVariant, 11.sp), maxLines = 1)
        }
      }
    }
    else -> Box(
      modifier = modifier.size(size).semantics { contentDescription = description },
      contentAlignment = Alignment.Center,
    ) {
      val masks = engineWidgetGaugeMasks(style, reading, size.value, size.value, scale)
      GaugeLayers(masks, thresholds, palette, active, GlanceModifier.size(size))
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val numberSize = if (style == FluidGaugeStyle.Ring) (size.value * 0.28f).coerceIn(10f, 14f) else (size.value * 0.2f).coerceIn(14f, 30f)
        Text(text = valueText, style = engineWidgetTextStyle(palette.onSurface, numberSize.sp, FontWeight.Bold), maxLines = 1)
        if (style == FluidGaugeStyle.Arc && !label.isNullOrBlank()) {
          Text(text = label, style = engineWidgetTextStyle(palette.onSurfaceVariant, 11.sp), maxLines = 1)
        }
      }
    }
  }
}

/** The sizes of the widget gauge. */
object EngineWidgetGaugeMetrics {
  /** A ring that sits in a widget row (34–48 dp) with its margin. */
  val RingSize: Dp = 40.dp

  /** The large arc, for a 2×2 cell. */
  val ArcSize: Dp = 120.dp

  /** The capacity bar's height: thicker than in the app (6 dp), as the Batteries widget's bar is at arm's length. */
  val BarHeight: Dp = 8.dp

  /** The default bar width: a 2×1 cell minus its padding. */
  val BarWidth: Dp = 140.dp

  /**
   * The bitmap scale cap. Masks are drawn at the screen density up to this, then scaled by the host: a 120 dp arc at
   * 3× is 360 px a side (half a megabyte a mask), and a widget's bitmaps share one budget with the whole RemoteViews.
   */
  const val MaxBitmapScale: Float = 3f
}

/** The white masks of a gauge: the track (at the track's alpha), the value, one per threshold mark. */
internal class EngineWidgetGaugeMasks(val track: Bitmap, val value: Bitmap?, val marks: List<Bitmap>)

/**
 * Draws the masks of a gauge [widthDp] × [heightDp] at [scale] pixels per dp: white, so the host's tint gives them their
 * colour. The geometry is `FluidGauge`'s ([FluidGaugeDefaults], [fluidGaugeArcAngles]).
 */
internal fun engineWidgetGaugeMasks(
  style: FluidGaugeStyle,
  reading: FluidGaugeReading,
  widthDp: Float,
  heightDp: Float,
  scale: Float,
): EngineWidgetGaugeMasks {
  val width = (widthDp * scale).roundToInt().coerceAtLeast(1)
  val height = (heightDp * scale).roundToInt().coerceAtLeast(1)
  val white = android.graphics.Color.WHITE
  fun paint(alpha: Float, stroke: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = white
    this.alpha = (alpha * 255).roundToInt()
    this.style = Paint.Style.STROKE
    strokeWidth = stroke
    strokeCap = Paint.Cap.ROUND
  }
  fun blank() = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val markWidth = FluidGaugeDefaults.ThresholdMarkWidth.value * scale
  val reach = FluidGaugeDefaults.ThresholdMarkReach.value * scale

  if (style == FluidGaugeStyle.Capacity) {
    val thickness = height.toFloat() * 0.6f
    val y = height / 2f
    val left = thickness / 2f
    val right = width - thickness / 2f
    val track = blank().also { Canvas(it).drawLine(left, y, right, y, paint(FluidGaugeDefaults.TrackAlpha, thickness)) }
    val value = if (reading.fraction > 0f) blank().also { Canvas(it).drawLine(left, y, left + (right - left) * reading.fraction, y, paint(1f, thickness)) } else null
    val marks = reading.thresholdFractions.map { f ->
      blank().also {
        val x = left + (right - left) * f
        Canvas(it).drawLine(x, 0f + markWidth / 2f, x, height - markWidth / 2f, paint(1f, markWidth))
      }
    }
    return EngineWidgetGaugeMasks(track, value, marks)
  }

  val side = minOf(width, height).toFloat()
  val thickness = side * if (style == FluidGaugeStyle.Ring) FluidGaugeDefaults.RingThickness else FluidGaugeDefaults.ArcThickness
  val diameter = side - thickness - reach * 2f
  val oval = RectF((width - diameter) / 2f, (height - diameter) / 2f, (width + diameter) / 2f, (height + diameter) / 2f)
  val (start, sweep) = fluidGaugeArcAngles(style)
  val track = blank().also { Canvas(it).drawArc(oval, start, sweep, false, paint(FluidGaugeDefaults.TrackAlpha, thickness)) }
  val value = if (reading.fraction > 0f) blank().also { Canvas(it).drawArc(oval, start, sweep * reading.fraction, false, paint(1f, thickness)) } else null
  val cx = oval.centerX()
  val cy = oval.centerY()
  val marks = reading.thresholdFractions.map { f ->
    blank().also {
      val rad = Math.toRadians((start + sweep * f).toDouble())
      val inner = diameter / 2f - thickness / 2f - reach
      val outer = diameter / 2f + thickness / 2f + reach
      val dx = cos(rad).toFloat()
      val dy = sin(rad).toFloat()
      Canvas(it).drawLine(cx + dx * inner, cy + dy * inner, cx + dx * outer, cy + dy * outer, paint(1f, markWidth))
    }
  }
  return EngineWidgetGaugeMasks(track, value, marks)
}

/** A number without a trailing «.0»: the default text of a threshold, for an app that did not write its own. */
internal fun engineWidgetPlainNumber(value: Float): String =
  if (value.isFinite() && value == value.roundToInt().toFloat()) value.roundToInt().toString() else value.toString()

/** The colour of a threshold on the home screen: the given one, or the palette's warning tone (danger for the highest). */
internal fun engineWidgetThresholdColor(index: Int, thresholds: List<FluidGaugeThreshold>, palette: EngineWidgetPalette): ColorProvider {
  val given = thresholds[index].color
  if (given != Color.Unspecified) return ColorProvider(given)
  val highest = thresholds.indices.maxByOrNull { thresholds[it].value }
  return if (index == highest) palette.dangerTone.content else palette.warningTone.content
}

@Composable
private fun GaugeLayers(
  masks: EngineWidgetGaugeMasks,
  thresholds: List<FluidGaugeThreshold>,
  palette: EngineWidgetPalette,
  active: ColorProvider,
  modifier: GlanceModifier,
) {
  Box(modifier = modifier) {
    Image(
      provider = ImageProvider(masks.track),
      contentDescription = null,
      contentScale = ContentScale.FillBounds,
      colorFilter = ColorFilter.tint(active),
      modifier = modifier,
    )
    masks.value?.let { bitmap ->
      Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(active),
        modifier = modifier,
      )
    }
    masks.marks.forEachIndexed { i, bitmap ->
      Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(engineWidgetThresholdColor(i, thresholds, palette)),
        modifier = modifier,
      )
    }
  }
}
