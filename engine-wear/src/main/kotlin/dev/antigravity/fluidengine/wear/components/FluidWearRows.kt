package dev.antigravity.fluidengine.wear.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.rememberEmptyGlassBackdrop
import dev.antigravity.fluidengine.wear.glass.FluidWearGlass
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens

/**
 * A row of a watch list: a cover or an icon, a title, maybe a line under it.
 *
 * No container, on purpose. A Wear list is a column of things on black, and boxing every row turns
 * a 200 dp circle into a stack of grey slabs. The row answers a press with the design system's
 * scale, and the leading tile carries the shape so the eye still finds an edge.
 *
 * @param leading the 40 dp tile at the start: a cover, an icon tile. Clipped to a continuous corner.
 * @param selected the row that is the current one — the song playing in a queue, the device the
 *   sound comes out of: its title takes the accent and, with no [trailing] of its own, a check.
 *   Since 2.11.0.
 */
@Composable
fun FluidWearListRow(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable RowScope.() -> Unit)? = null,
  selected: Boolean = false,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(if (onClick != null || onLongClick != null) Modifier.fluidPressable(onClick, onLongClick) else Modifier)
      .padding(vertical = RowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowGap),
  ) {
    if (leading != null) {
      Box(
        modifier = Modifier
          .size(FluidWearDimens.RowArtwork)
          .clip(ContinuousCornerShape(FluidRadius.Small)),
        contentAlignment = Alignment.Center,
      ) { leading() }
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrEmpty()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    when {
      trailing != null -> trailing.invoke(this)
      selected -> Icon(
        imageVector = SelectedCheck,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(FluidWearDimens.IconSmall),
      )
    }
  }
}

/** A plain check mark, drawn here so the engine needs no icon set. */
private val SelectedCheck: ImageVector by lazy {
  ImageVector.Builder(
    name = "FluidSelectedCheck",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    addPath(
      pathData = PathParser().parsePathString("M4.5,12.5 L9.5,17.5 L19.5,6.5").toNodes(),
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2.4f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    )
  }.build()
}

/**
 * A pill of glass with an icon and an optional label: the Search and Library buttons at the top of
 * a watch home.
 *
 * Control glass over whatever [backdrop] it is given — on a black page that is an empty backdrop,
 * and the pill shows its rim, its film and its touch light, which is the material without anything
 * to bend. Put it over a picture and the same pill refracts it. Since 2.11.0 it is the watch's one
 * glass ([FluidWearGlass.pageTint], no dispersion) rather than the phone's control film.
 */
@Composable
fun FluidWearPill(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = rememberEmptyGlassBackdrop(),
  label: String? = null,
  icon: @Composable () -> Unit,
) {
  Row(
    modifier = modifier
      .height(FluidWearDimens.PillHeight)
      .glassControlSurface(
        backdrop = backdrop,
        shape = FluidCapsuleShape,
        tint = FluidWearGlass.pageTint(),
        optics = FluidWearGlass.WideOptics,
      )
      .fluidPressable(onClick = onClick)
      .padding(horizontal = PillPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RowGap, Alignment.CenterHorizontally),
  ) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
      icon()
      if (label != null) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
      }
    }
  }
}

private val RowVerticalPadding = 6.dp
private val RowGap = 10.dp
private val PillPadding = 16.dp
