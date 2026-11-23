package dev.antigravity.fluidengine.ui.fluid.viewer

import dev.antigravity.fluidengine.ui.page.LocalSharedPage

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.LocalFluidThemeSides
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Optional player capability. Publish the result through durationMillis, without starting playback. */
interface FluidMediaDurationLoader {
  fun requestDuration()
}

data class FluidViewerInfoRow(val label: String, val value: String,
  /** Original value copied on long press when the displayed value is localized. */
  val copyValue: String = value)
data class FluidViewerInfoSection(val title: String, val rows: List<FluidViewerInfoRow>,
  val content: (@Composable () -> Unit)? = null)
data class FluidAudioChapter(val title: String, val startMillis: Long)
data class FluidViewerInfoAction(val label: String, val onClick: () -> Unit)

/** Coordinates stay copyable when the app supplies a map. Navigation belongs to the app. */
data class FluidViewerLocation(val coordinates: String, val map: (@Composable () -> Unit)? = null,
  val openMap: (() -> Unit)? = null)

/** Metadata from the owner. A location section can contain coordinates and, optionally, the app's map.
 * Supply a small [thumbnail] of the file, its cover or its application icon. */
data class FluidViewerInfo(
  val file: FluidFileInfo,
  val title: String = file.name,
  val subtitle: String? = null,
  val thumbnail: ImageBitmap? = null,
  val facts: List<String> = emptyList(),
  val sections: List<FluidViewerInfoSection> = emptyList(),
  val actions: List<FluidViewerInfoAction> = emptyList(),
  val audioWaveform: (@Composable () -> Unit)? = null,
  val location: FluidViewerLocation? = null,
  val reading: FluidViewerInfoSection? = null,
  val searchPages: (suspend (String) -> List<FluidViewerPageMatch>)? = null,
  val chapters: List<FluidAudioChapter> = emptyList(),
  val audioFormat: String? = null,
  val audioOutput: (() -> Unit)? = null,
)

/** Each rectangle is in page-relative coordinates (0..1), with the origin at the top left. */
data class FluidViewerPageMatch(val page: Int, val bounds: List<androidx.compose.ui.geometry.Rect>)

/** Associates rich metadata with pager indices, including when adjacent pages are composed together. */
@Composable
fun FluidViewerInfoProvider(info: @Composable (Int) -> FluidViewerInfo?, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalViewerInfoSource provides info, content = content)
}

internal val LocalViewerInfoSource = staticCompositionLocalOf<@Composable (Int) -> FluidViewerInfo?> { { null } }
internal val LocalViewerInfo = compositionLocalOf<FluidViewerInfo?> { null }

@Composable
internal fun defaultViewerInfo(file: FluidViewerContent, strings: FluidViewerStrings): FluidViewerInfo {
  val labels = LocalFluidLabels.current
  val kind = when (file) {
    is FluidViewerContent.Image -> FluidFileKind.Image
    is FluidViewerContent.Media -> if (file.videoSurface == null) FluidFileKind.Audio else FluidFileKind.Video
    is FluidViewerContent.Pages -> FluidFileKind.Document
    is FluidViewerContent.Text -> FluidFileKind.Text
    is FluidViewerContent.Unknown -> file.info.kind
    else -> FluidFileKind.Other
  }
  val info = (file as? FluidViewerContent.Unknown)?.info ?: FluidFileInfo(labels.fileKind(kind), kind = kind)
  val actions = buildList {
    if (file is FluidViewerContent.Unknown) {
      file.openWith?.let { add(FluidViewerInfoAction(strings.openWith, it)) }
      file.share?.let { add(FluidViewerInfoAction(strings.share, it)) }
    }
  }
  val facts = buildList {
    when (file) {
      is FluidViewerContent.Pages -> add(strings.pages(file.source.count))
      is FluidViewerContent.Text -> {
        file.source.language?.let(::add)
        file.source.lineCount?.let { add(strings.lines(it)) }
      }
      is FluidViewerContent.Media -> file.session.durationMillis?.let { add(FluidScrubbing.clock(it)) }
      else -> info.name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
    }
  }
  return FluidViewerInfo(info, facts = facts, actions = actions)
}

/**
 * Tavola D · Mix (F6C.7, tavole_base.py e tavole_d.py, 22 novembre 2026).
 * Le misure CSS dei controlli e gli sp sono fissi. La colonna cresce fino a 552 dp.
 * Le tinte e l'alone sono scelti a occhio, 22 novembre.
 */
internal object ViewerMixProfile {
  val ColumnWidth = 552.dp
  val Cover = 280.dp
  val CoverRadius = 22.dp
  val FileIcon = 136.dp
  val FileIconRadius = 32.dp
  val PlayerRadius = 30.dp
  val WarningRadius = 24.dp
  val Edge = 16.dp
  val FactsHeight = 28.dp
  val FileFactsHeight = 26.dp
  val FactPadding = 11.dp
  const val FileTint = .20f
  const val LowerTint = .16f
  const val WarningTint = .27f
  val Amber = Color(0xFFFF9500)
  val HaloDrop = 6.dp
  fun haloSpread(itemSize: Dp): Dp = (itemSize * .16f).coerceIn(20.dp, 46.dp)
}

/** The board uses the Fluid font families at these exact sizes. Font scaling still comes from the system. */
internal fun TextStyle.viewerSize(size: Float, weight: FontWeight = fontWeight ?: FontWeight.Normal,
  line: Float = size * 1.2f): TextStyle =
  copy(fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = 0.sp)

@Composable
internal fun viewerBoardTop(position: Dp): Dp = maxOf(position, FluidScreenDefaults.topBarHeight() + 8.dp)

@Composable
internal fun ViewerBoardButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
  style: FluidButtonStyle = FluidButtonStyle.Filled, height: Dp = 46.dp, textSize: Float = 15.5f) {
  val colors = MaterialTheme.colorScheme
  val fill = when (style) {
    FluidButtonStyle.Filled -> colors.primary
    FluidButtonStyle.Tinted -> colors.primary.copy(alpha = .12f)
    FluidButtonStyle.Destructive -> colors.errorContainer
    FluidButtonStyle.Plain -> Color.Transparent
  }
  val ink = when (style) {
    FluidButtonStyle.Filled -> colors.onPrimary
    FluidButtonStyle.Destructive -> colors.onErrorContainer
    else -> colors.primary
  }
  Box(modifier.heightIn(min = height).fluidPressable(onClick = onClick, role = Role.Button,
    feedback = FluidPressFeedback.Expand, grip = FluidPressGrip.YieldsToScroll)
    .background(fill, FluidCapsuleShape).padding(horizontal = 14.dp, vertical = 4.dp),
    contentAlignment = Alignment.Center) {
    Text(text, style = MaterialTheme.typography.bodyMedium.viewerSize(textSize, FontWeight.SemiBold),
      color = ink, textAlign = TextAlign.Center)
  }
}

/** Bound the reading column independently of Android's density, text settings and the window insets. */
@Composable
internal fun ViewerMixColumn(modifier: Modifier, content: @Composable (width: Dp) -> Unit) {
  BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
    val width = minOf(maxWidth, ViewerMixProfile.ColumnWidth)
    Box(Modifier.width(width), contentAlignment = Alignment.TopCenter) { content(width) }
  }
}

@Composable
internal fun viewerSolidMaterial(): Boolean {
  val access by rememberGlassMaterialAccessibility()
  return access.reduceTransparency || access.contrast >= FluidSystemContrast.High
}

@Composable
internal fun Modifier.viewerGlass(backdrop: GlassBackdropState, shape: Shape = FluidCapsuleShape,
  tint: Color = Color.Unspecified): Modifier {
  if (viewerSolidMaterial()) return background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
  val glass = if (tint == Color.Unspecified) FluidGlass.Regular else FluidGlass.Regular.tint(tint)
  return fluidGlass(glass, shape, backdrop)
}

/** Sample the most frequent colour bucket in a small image. Never read or recycle the caller's pixels in place. */
private fun thumbnailAccent(image: ImageBitmap): Color? {
  val original = image.asAndroidBitmap()
  val small = Bitmap.createScaledBitmap(original, 8, 8, true)
  val readable = if (Build.VERSION.SDK_INT >= 26 && small.config == Bitmap.Config.HARDWARE)
    small.copy(Bitmap.Config.ARGB_8888, false) else small
  try {
    val bins = mutableMapOf<Int, MutableList<Int>>()
    for (y in 0 until 8) for (x in 0 until 8) {
      val pixel = readable.getPixel(x, y)
      if (pixel ushr 24 < 128) continue
      val bucket = pixel and 0x00e0e0e0
      bins.getOrPut(bucket) { mutableListOf() }.add(pixel)
    }
    // Prefer chromatic pixels to the neutral field around an app icon or the rim of a cover.
    val group = bins.values.maxByOrNull { pixels ->
      val p = pixels.first()
      val r = (p ushr 16) and 255
      val g = (p ushr 8) and 255
      val b = p and 255
      pixels.size * (22 + maxOf(r, g, b) - minOf(r, g, b))
    } ?: return null
    fun mean(shift: Int) = group.sumOf { (it ushr shift) and 255 } / group.size
    return Color(mean(16), mean(8), mean(0))
  } finally {
    if (readable !== small) readable.recycle()
    if (small !== original) small.recycle()
  }
}

@Composable
internal fun viewerAccent(info: FluidViewerInfo?, fallback: Color): Color {
  val thumb = info?.thumbnail ?: return fallback
  if (viewerSolidMaterial()) return fallback
  return key(thumb, fallback) {
    val accent by produceState(fallback, thumb) {
      value = withContext(Dispatchers.Default) { thumbnailAccent(thumb) } ?: fallback
    }
    accent
  }
}

@Composable
internal fun Modifier.viewerWash(accent: Color, lower: Color, edge: Color): Modifier {
  if (viewerSolidMaterial()) return this
  val space = LocalSharedPage.current.takeIf { LocalViewerDocumentRoute.current }
  return drawWithCache {
    fun field(color: Color, alpha: Float, x: Float, y: Float, radius: Float) = Brush.radialGradient(
      0f to color.copy(alpha = alpha), .45f to color.copy(alpha = alpha * .78f),
      1f to color.copy(alpha = 0f), center = Offset(size.width * x, size.height * y),
      radius = (size.maxDimension * radius).coerceAtLeast(1f))
    val top = field(accent, ViewerMixProfile.FileTint, .45f, .27f, .78f)
    val foot = field(lower, ViewerMixProfile.LowerTint, .05f, .92f, .60f)
    val side = field(edge, ViewerMixProfile.LowerTint, .99f, .81f, .56f)
    onDrawBehind {
      val alpha = if (space == null || space.host.zoom == null) 1f
      else if (space.host.hasClosingBackdrop(space.page)) 0f else space.host.backdropArrival(space.page)
      if (alpha > 0f) {
        drawRect(top, alpha = alpha)
        drawRect(foot, alpha = alpha)
        drawRect(side, alpha = alpha)
      }
    }
  }
}

/** Soft, shape-following colour around the artwork. Draw outside its bounds, before its clip. */
@Composable
internal fun Modifier.viewerHalo(accent: Color, corner: Dp): Modifier {
  if (viewerSolidMaterial()) return this
  return drawWithCache {
    val spread = ViewerMixProfile.haloSpread(size.minDimension.toDp()).toPx()
    val radius = corner.toPx()
    val drop = ViewerMixProfile.HaloDrop.toPx()
    onDrawBehind {
      for (ring in 20 downTo 1) {
        val t = ring / 20f
        val extra = spread * t
        drawRoundRect(accent.copy(alpha = .09f * (1f - t) + .008f),
          Offset(-extra, drop - extra), Size(size.width + 2 * extra, size.height + 2 * extra),
          CornerRadius(radius + extra))
      }
    }
  }
}

@Composable
internal fun ViewerFacts(facts: List<String>, backdrop: GlassBackdropState, modifier: Modifier = Modifier,
  contrast: ViewerInfoText? = null, height: Dp = ViewerMixProfile.FactsHeight) {
  if (facts.isEmpty()) return
  Row(modifier.horizontalScroll(rememberScrollState()).semantics { isTraversalGroup = true },
    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    facts.forEach { fact ->
      Box(Modifier.viewerGlass(backdrop).then(if (contrast == null) Modifier else
        Modifier.background(contrast.support, FluidCapsuleShape)).heightIn(min = height)
        .padding(horizontal = ViewerMixProfile.FactPadding), contentAlignment = Alignment.Center) {
        Text(fact, style = MaterialTheme.typography.labelLarge.viewerSize(12.5f, FontWeight.SemiBold),
          color = contrast?.ink ?: MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

internal val LocalViewerInfoPageColors = staticCompositionLocalOf<ColorScheme?> { null }

internal data class ViewerInfoText(val ink: Color, val support: Color)

/**
 * Keep the theme ink where it meets WCAG AA. At the light/dark crossover use black or white:
 * fading the two inks through grey would make the letters disappear. The tint behind a label
 * is just strong enough to keep every pixel legible even over a black or white detail in the glass.
 */
internal fun viewerInfoText(surface: Color, preferred: Color): ViewerInfoText {
  val darkInk = fluidContrastRatio(Color.Black, surface) >= fluidContrastRatio(Color.White, surface)
  val ink = if (preferred.alpha == 1f && (preferred.luminance() < surface.luminance()) == darkInk &&
    fluidContrastRatio(preferred, surface) >= 4.55f) preferred else if (darkInk) Color.Black else Color.White
  val worst = if (darkInk) Color.Black else Color.White
  var low = 0
  var high = 255
  while (low < high) {
    val mid = (low + high) / 2
    val background = surface.copy(alpha = mid / 255f).compositeOver(worst)
    val ordered = if (darkInk) background.luminance() >= ink.luminance() else background.luminance() <= ink.luminance()
    if (ordered && fluidContrastRatio(ink, background) >= 4.55f) high = mid else low = mid + 1
  }
  return ViewerInfoText(ink, surface.copy(alpha = low / 255f))
}

@Composable
internal fun ViewerInfoBody(info: FluidViewerInfo, backdrop: GlassBackdropState, strings: FluidViewerStrings,
  close: () -> Unit, extra: @Composable () -> Unit) {
  val page = LocalViewerInfoPageColors.current
  val overlay = LocalFluidSheetPageVeil.current
  if (page == null || overlay == null) {
    ViewerInfoContents(info, backdrop, strings, close, null, null, extra)
    return
  }
  val media = info.file.kind == FluidFileKind.Image || info.file.kind == FluidFileKind.Video
  val glass = if (media) LocalFluidThemeSides.current?.scheme(true) ?: androidx.compose.material3.darkColorScheme()
    else MaterialTheme.colorScheme
  val opacity = overlay.alpha().coerceIn(0f, 1f)
  fun layer(glassColor: Color, pageColor: Color) = pageColor.copy(alpha = opacity).compositeOver(glassColor)
  val surface = layer(glass.surface, overlay.color)
  val capsule = layer(glass.surfaceContainerHigh, page.surfaceContainerHigh)
  val row = layer(glass.surfaceContainerLow, page.surfaceContainerLow)
  val preferred = if (fluidContrastRatio(glass.onSurface, surface) > fluidContrastRatio(page.onSurface, surface))
    glass else page
  val title = remember(surface, preferred) { viewerInfoText(surface, preferred.onSurface) }
  val fact = remember(capsule, preferred) { viewerInfoText(capsule, preferred.onSurface) }
  val rowInk = remember(row, preferred) { viewerInfoText(row, preferred.onSurface).ink }
  val colors = preferred.copy(surface = surface, surfaceContainerLow = row, surfaceContainerHigh = capsule,
    onSurface = rowInk, onSurfaceVariant = viewerInfoText(row, preferred.onSurfaceVariant).ink)
  MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
    CompositionLocalProvider(LocalContentColor provides rowInk, LocalFluidGlassAppearance provides null,
      LocalFluidSurfaceSide provides (title.ink.luminance() > surface.luminance())) {
      ViewerInfoContents(info, currentCanvasBackdrop() ?: backdrop, strings, close, title, fact, extra)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewerInfoContents(info: FluidViewerInfo, backdrop: GlassBackdropState, strings: FluidViewerStrings,
  close: () -> Unit, title: ViewerInfoText?, fact: ViewerInfoText?, extra: @Composable () -> Unit) {
  val colors = MaterialTheme.colorScheme
  val kind = LocalFluidLabels.current.fileKind(info.file.kind)
  val summary = info.subtitle ?: fluidUnknownDetails(info.file, kind, FluidUnknownPresetStrings())
  val rows = buildList {
    add(FluidViewerInfoRow(strings.name, info.file.name))
    info.file.mimeType?.let { add(FluidViewerInfoRow(strings.type, it)) }
    info.file.sizeBytes?.takeIf { it >= 0 }?.let { add(FluidViewerInfoRow(strings.size, fluidFormatFileSize(it))) }
  }
  val location = info.location
  val sections = buildList {
    addAll(info.sections)
    if (info.chapters.isNotEmpty()) add(FluidViewerInfoSection(strings.chapters,
      info.chapters.map { FluidViewerInfoRow(it.title, FluidScrubbing.clock(it.startMillis)) }))
    if (location != null) add(FluidViewerInfoSection(strings.location,
      listOf(FluidViewerInfoRow(strings.coordinates, location.coordinates)), location.map))
    add(FluidViewerInfoSection(strings.file, rows))
    info.reading?.let(::add)
  }
  val actions = listOfNotNull(location?.openMap?.let { FluidViewerInfoAction(strings.openMap, it) }) + info.actions
  var actionFailed by remember(info.file) { mutableStateOf(false) }
  ViewerMixColumn(Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).semantics { isTraversalGroup = true }
      .padding(start = ViewerMixProfile.Edge, end = ViewerMixProfile.Edge, bottom = ViewerMixProfile.Edge)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val thumb = info.thumbnail
        if (thumb == null) FileIcon(info.file.kind, 44)
        else if (info.file.kind in setOf(FluidFileKind.Pdf, FluidFileKind.Document)) Image(thumb, null,
          Modifier.size(38.dp, 54.dp).clip(ContinuousCornerShape(2.dp)), contentScale = ContentScale.Fit)
        else Image(thumb, null, Modifier.size(48.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).wrapContentWidth(Alignment.Start)
          .then(if (title == null) Modifier else Modifier.background(title.support, MaterialTheme.shapes.small)
            .padding(horizontal = 6.dp, vertical = 3.dp))
          .semantics(mergeDescendants = true) { heading() }) {
          Text(info.title, style = MaterialTheme.typography.titleLarge.viewerSize(18f, FontWeight.Bold),
            color = title?.ink ?: colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
          if (summary != info.title) Text(summary, style = MaterialTheme.typography.bodySmall.viewerSize(13.5f),
            color = title?.ink ?: colors.onSurfaceVariant)
        }
        FluidGlassIconButton(close, backdrop = backdrop, size = 32.dp) {
          Box(Modifier.fillMaxSize().then(if (fact == null) Modifier else Modifier.background(fact.support, FluidCapsuleShape)),
            contentAlignment = Alignment.Center) {
            FluidIcon(FluidIcons.Close, strings.close, tint = fact?.ink ?: colors.onSurface, size = 16.dp)
          }
        }
      }
      ViewerFacts(info.facts, backdrop, Modifier.padding(top = 12.dp), contrast = fact)
      extra()
      sections.forEach { section ->
        if (section.rows.isNotEmpty() || section.content != null) {
          Text(section.title.uppercase(), Modifier.padding(start = 16.dp, top = 14.dp, bottom = 7.dp)
            .semantics { heading() }, color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium.viewerSize(12f, FontWeight.SemiBold).copy(letterSpacing = 0.6.sp))
          if (section.rows.isNotEmpty()) FluidListGroup {
            section.rows.forEachIndexed { index, row ->
              if (index > 0) FluidListDivider()
              ViewerInfoRow(row, strings.copy)
            }
          }
          section.content?.invoke()
        }
      }
      if (actions.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          actions.forEach { action ->
            ViewerBoardButton(action.label, {
              actionFailed = false
              fluidRunFileAction(action.onClick) { actionFailed = true }
            }, Modifier.weight(1f), style = FluidButtonStyle.Tinted, height = 40.dp, textSize = 13.5f)
          }
        }
        if (actionFailed) Text(strings.actionFailed, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          color = colors.error)
      }
    }
  }
}

@Suppress("DEPRECATION")
@Composable
private fun ViewerInfoRow(row: FluidViewerInfoRow, copyLabel: String) {
  val clipboard = LocalClipboardManager.current
  val copy = { clipboard.setText(AnnotatedString(row.copyValue)) }
  Row(Modifier.fillMaxWidth().pointerInput(row.copyValue, clipboard) { detectTapGestures(onLongPress = { copy() }) }
    .semantics(mergeDescendants = true) { onLongClick(copyLabel) { copy(); true } }
    .heightIn(min = 38.dp).padding(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(row.label, Modifier.weight(.38f), style = MaterialTheme.typography.bodyMedium.viewerSize(14f))
    Text(row.value, Modifier.weight(.62f), style = MaterialTheme.typography.bodyMedium.viewerSize(13.5f),
      textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Let a list read normally. At its bottom, a deliberate extra upward pull opens details. */
@Composable
internal fun Modifier.viewerPullForInfo(enabled: Boolean, open: () -> Unit): Modifier {
  if (!enabled) return this
  val current by rememberUpdatedState(open)
  val threshold = with(LocalDensity.current) { FluidSheetDismissTravel.toPx() }
  val connection = remember(threshold) {
    object : NestedScrollConnection {
      var pull = 0f
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y > 0) pull = 0f
        return Offset.Zero
      }
      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y < 0f) {
          pull -= available.y
          if (pull >= threshold) {
            pull = 0f
            current()
          }
        }
        return Offset.Zero
      }
      override suspend fun onPreFling(available: Velocity): Velocity {
        pull = 0f
        return Velocity.Zero
      }
    }
  }
  return nestedScroll(connection)
}
