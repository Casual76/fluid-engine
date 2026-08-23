package dev.antigravity.fluidengine.widget

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * The radii the design system uses, in the sizes a home screen needs.
 *
 * Glance can only ask the host for a rounded outline, and only from Android 12 on. Below that the
 * corners stay square and the palette carries the identity on its own — which is why none of these
 * components rely on shape alone to say anything.
 */
object EngineWidgetShape {
  val Container = 24.dp
  val Group = 20.dp
  val Tile = 9.dp
  val Pill = 18.dp
}

/**
 * The widget's outer surface: the app's background, rounded, with one tap target behind everything.
 *
 * [onClick] should be the app's front door. A widget with no whole-surface action makes people hunt
 * for the one pixel that opens the app.
 */
@Composable
fun EngineWidgetSurface(
  palette: EngineWidgetPalette,
  layout: EngineWidgetLayout,
  onClick: Action? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box(
    modifier = GlanceModifier
      .fillMaxSize()
      .background(palette.background)
      .cornerRadius(EngineWidgetShape.Container)
      .let { if (onClick != null) it.clickable(onClick) else it }
      .padding(layout.padding),
  ) {
    Column(
      modifier = GlanceModifier.fillMaxSize(),
      verticalAlignment = Alignment.Top,
      content = content,
    )
  }
}

/** Title, an optional status line, and one trailing action. */
@Composable
fun EngineWidgetHeader(
  title: String,
  palette: EngineWidgetPalette,
  layout: EngineWidgetLayout,
  subtitle: String? = null,
  subtitleIsWarning: Boolean = false,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = GlanceModifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = title,
        style = engineWidgetTextStyle(
          color = palette.onSurface,
          size = if (layout.compact) 15.sp else 17.sp,
          weight = FontWeight.Bold,
        ),
        maxLines = 1,
      )
      if (subtitle != null && !layout.compact) {
        Text(
          text = subtitle,
          style = engineWidgetTextStyle(
            color = if (subtitleIsWarning) palette.attention else palette.onSurfaceVariant,
            size = 12.sp,
          ),
          maxLines = 1,
        )
      }
    }
    if (trailing != null) {
      Spacer(GlanceModifier.width(10.dp))
      trailing()
    }
  }
}

/**
 * A round, tinted glyph button — the widget's equivalent of a bar button item.
 *
 * Tinted rather than labelled: a labelled chip competes with the surface's own tap target for both
 * space and attention, and a widget has room for exactly one primary action.
 */
@Composable
fun EngineWidgetActionButton(
  @DrawableRes icon: Int,
  contentDescription: String,
  action: Action,
  palette: EngineWidgetPalette,
  layout: EngineWidgetLayout,
) {
  val diameter = if (layout.compact) 30.dp else 36.dp
  Box(
    modifier = GlanceModifier
      .size(diameter)
      .background(palette.accentContainer)
      // Half the diameter, not the diameter: a radius larger than the shorter side is undefined
      // territory for the host's outline provider rather than "more round".
      .cornerRadius(diameter / 2)
      .semantics { this.contentDescription = contentDescription }
      .clickable(action),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      provider = ImageProvider(icon),
      contentDescription = null,
      modifier = GlanceModifier.size(if (layout.compact) 15.dp else 18.dp),
      colorFilter = ColorFilter.tint(palette.onAccentContainer),
    )
  }
}

/** Rows sharing one rounded card, the way an inset grouped list does. */
@Composable
fun EngineWidgetGroup(
  palette: EngineWidgetPalette,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier = GlanceModifier
      .fillMaxWidth()
      .background(palette.card)
      .cornerRadius(EngineWidgetShape.Group),
    content = content,
  )
}

/**
 * One row of a group: a tinted icon tile, one or two lines of text, and a trailing value.
 *
 * The row's category is carried on the tile rather than on its background — tinting whole rows
 * turns a tidy group into a patchwork of coloured blocks, and at widget sizes there is no room to
 * recover from that.
 */
@Composable
fun EngineWidgetRow(
  title: String,
  palette: EngineWidgetPalette,
  layout: EngineWidgetLayout,
  @DrawableRes icon: Int? = null,
  tone: EngineWidgetTone = palette.neutralTone,
  subtitle: String? = null,
  trailing: String? = null,
  onClick: Action? = null,
) {
  Row(
    modifier = GlanceModifier
      .fillMaxWidth()
      .let { if (onClick != null) it.clickable(onClick) else it }
      .padding(
        horizontal = if (layout.compact) 10.dp else 12.dp,
        vertical = if (layout.compact) 7.dp else 9.dp,
      ),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      EngineWidgetIconTile(icon = icon, tone = tone, compact = layout.compact)
      Spacer(GlanceModifier.width(if (layout.compact) 8.dp else 10.dp))
    }
    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = title,
        style = engineWidgetTextStyle(
          color = palette.onSurface,
          size = if (layout.compact) 13.sp else 15.sp,
          weight = FontWeight.Medium,
        ),
        maxLines = 1,
      )
      if (layout.showSubtitle && !subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = engineWidgetTextStyle(color = palette.onSurfaceVariant, size = 12.sp),
          maxLines = 1,
        )
      }
    }
    if (!trailing.isNullOrBlank()) {
      Spacer(GlanceModifier.width(8.dp))
      Text(
        text = trailing,
        style = engineWidgetTextStyle(
          color = tone.content,
          size = if (layout.compact) 11.sp else 12.sp,
          weight = FontWeight.Bold,
        ),
        maxLines = 1,
      )
    }
  }
}

/**
 * The separator between two rows, inset to where the row's text starts.
 *
 * Glance modifiers are a set of properties rather than an ordered chain, so a padding and a
 * background declared on the same element would still paint edge to edge: the inset has to come
 * from a wrapper.
 */
@Composable
fun EngineWidgetHairline(
  palette: EngineWidgetPalette,
  layout: EngineWidgetLayout,
) {
  val inset = if (layout.compact) 42.dp else 52.dp
  Box(modifier = GlanceModifier.fillMaxWidth().padding(start = inset)) {
    Box(
      modifier = GlanceModifier
        .fillMaxWidth()
        .height(1.dp)
        .background(palette.hairline),
    ) {}
  }
}

@Composable
fun EngineWidgetIconTile(
  @DrawableRes icon: Int,
  tone: EngineWidgetTone,
  compact: Boolean = false,
) {
  val tile = if (compact) 24.dp else 30.dp
  Box(
    modifier = GlanceModifier
      .size(tile)
      .background(tone.container)
      .cornerRadius(EngineWidgetShape.Tile),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      provider = ImageProvider(icon),
      contentDescription = null,
      modifier = GlanceModifier.size(if (compact) 14.dp else 17.dp),
      colorFilter = ColorFilter.tint(tone.content),
    )
  }
}

/**
 * A count and what it counts.
 *
 * [modifier] is a parameter because the pill is nearly always laid out by its parent — given a
 * weight in a row, and a tap action of its own.
 */
@Composable
fun EngineWidgetPill(
  value: String,
  label: String,
  tone: EngineWidgetTone,
  modifier: GlanceModifier = GlanceModifier,
  compact: Boolean = false,
) {
  Row(
    modifier = modifier
      .background(tone.container)
      .cornerRadius(EngineWidgetShape.Pill)
      .padding(horizontal = 12.dp, vertical = if (compact) 7.dp else 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = value,
      style = engineWidgetTextStyle(
        color = tone.content,
        size = if (compact) 15.sp else 17.sp,
        weight = FontWeight.Bold,
      ),
      maxLines = 1,
    )
    Spacer(GlanceModifier.width(6.dp))
    Text(
      text = label,
      style = engineWidgetTextStyle(color = tone.content, size = 12.sp),
      maxLines = 1,
    )
  }
}

/**
 * The card a widget shows when it has nothing to list.
 *
 * Shaped like a row rather than like an error, because most of the time it is not one: nothing due,
 * nothing unread, or not signed in yet.
 */
@Composable
fun EngineWidgetMessage(
  message: String,
  palette: EngineWidgetPalette,
  layout: EngineWidgetLayout,
  @DrawableRes icon: Int? = null,
  tone: EngineWidgetTone = palette.neutralTone,
  detail: String? = null,
) {
  Row(
    modifier = GlanceModifier
      .fillMaxWidth()
      .background(palette.card)
      .cornerRadius(EngineWidgetShape.Group)
      .padding(horizontal = 12.dp, vertical = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      EngineWidgetIconTile(icon = icon, tone = tone, compact = layout.compact)
      Spacer(GlanceModifier.width(10.dp))
    }
    Column(modifier = GlanceModifier.defaultWeight()) {
      Text(
        text = message,
        style = engineWidgetTextStyle(
          color = palette.onSurface,
          size = 13.sp,
          weight = FontWeight.Medium,
        ),
        maxLines = 2,
      )
      if (!detail.isNullOrBlank()) {
        Text(
          text = detail,
          style = engineWidgetTextStyle(color = palette.attention, size = 11.sp),
          maxLines = 1,
        )
      }
    }
  }
}

/**
 * Glance text has no typography scale to inherit, so the sizes are stated at each call site.
 *
 * They are the design system's ramp read at widget distance: 17 for a title, 15 for a row, 13 for a
 * dense row, 12 for a subtitle, 11 for a trailing value.
 */
fun engineWidgetTextStyle(
  color: ColorProvider,
  size: TextUnit,
  weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(color = color, fontSize = size, fontWeight = weight)
