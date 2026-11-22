package dev.antigravity.fluidengine.ui.fluid.viewer

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
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

/** D · Mix. Intensità e posizione degli aloni a occhio, 22 novembre 2026. */
internal object ViewerMixProfile {
  const val FileTint = .20f
  const val HaloTint = .36f
  val CoverSize = 280.dp
  val IconSize = 136.dp
  val PanelWidth = 444.dp
  val Amber = Color(0xFFFF9500)
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
    val group = bins.values.maxByOrNull { it.size } ?: return null
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
internal fun Modifier.viewerWash(accent: Color, halo: Boolean = false): Modifier {
  if (viewerSolidMaterial()) return this
  return drawWithCache {
    val center = Offset(size.width / 2, size.height * (if (halo) .5f else .34f))
    val radius = if (halo) size.minDimension / 2 else size.maxDimension * .84f
    val brush = Brush.radialGradient(listOf(accent.copy(alpha = if (halo) ViewerMixProfile.HaloTint
      else ViewerMixProfile.FileTint), Color.Transparent), center, radius.coerceAtLeast(1f))
    onDrawBehind { drawRect(brush) }
  }
}

@Composable
internal fun ViewerFacts(facts: List<String>, backdrop: GlassBackdropState, modifier: Modifier = Modifier) {
  if (facts.isEmpty()) return
  Row(modifier.horizontalScroll(rememberScrollState()).semantics { isTraversalGroup = true },
    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    facts.forEach { fact ->
      Text(fact, Modifier.viewerGlass(backdrop).padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ViewerInfoBody(info: FluidViewerInfo, backdrop: GlassBackdropState, strings: FluidViewerStrings,
  close: () -> Unit, extra: @Composable () -> Unit) {
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
  Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).semantics { isTraversalGroup = true }
    .padding(FluidScreenDefaults.HorizontalPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      val thumb = info.thumbnail
      if (thumb == null) FileIcon(info.file.kind, 44)
      else Image(thumb, null, Modifier.size(48.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
      Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
        Text(info.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        if (summary != info.title) Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
      }
      FluidGlassIconButton(close, backdrop = backdrop, size = FluidGlassSize.Compact) {
        FluidIcon(FluidIcons.Close, strings.close)
      }
    }
    ViewerFacts(info.facts, backdrop)
    extra()
    sections.forEach { section ->
      if (section.rows.isNotEmpty() || section.content != null) {
        FluidSectionHeader(section.title)
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
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
          FluidButton(action.label, {
            actionFailed = false
            fluidRunFileAction(action.onClick) { actionFailed = true }
          }, style = FluidButtonStyle.Tinted)
        }
      }
      if (actionFailed) Text(strings.actionFailed, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.error)
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
    .padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(row.label, Modifier.weight(.42f), style = MaterialTheme.typography.bodyMedium)
    Text(row.value, Modifier.weight(.58f), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
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
