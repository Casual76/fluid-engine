/*
 * engine-scan (4.0, F8.17): la revisione della scansione, come quella di VisionKit: le pagine una per volta, la fila
 * delle miniature da riordinare col dito (il riordino di engine-ui), e i comandi della pagina. Il dito resta nitido:
 * niente sfocature su pagine e fila (regola di F8). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidReorderStrings
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidMoved
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidReorderable
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowBackIosNew
import dev.antigravity.fluidengine.ui.fluid.icons.Crop
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoCamera
import dev.antigravity.fluidengine.ui.fluid.icons.RotateRight
import dev.antigravity.fluidengine.ui.fluid.icons.Tune
import dev.antigravity.fluidengine.ui.fluid.rememberFluidReorderState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ScanReviewScreen(
  state: FluidScanState,
  initial: Int,
  backdrop: GlassBackdropState,
  strings: FluidScanStrings,
  saving: Boolean,
  onBack: () -> Unit,
  onSave: () -> Unit,
  onCrop: (id: String, at: Int) -> Unit,
  onRetake: (id: String, at: Int) -> Unit,
  onEmpty: () -> Unit,
  onPageChange: (Int) -> Unit,
) {
  val pages = state.pages
  val count = pages.size
  LaunchedEffect(count) { if (count == 0) onEmpty() }
  if (count == 0) return
  val pager = rememberPagerState(initialPage = initial.coerceIn(0, count - 1)) { pages.size }
  val strip = rememberLazyListState(initialFirstVisibleItemIndex = (initial - 2).coerceAtLeast(0))
  val scope = rememberCoroutineScope()
  var filters by remember { mutableStateOf(false) }
  val current = pager.currentPage.coerceIn(0, count - 1)
  val page = pages[current]
  LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { onPageChange(it) } }
  // Il riordino della fila: la pagina che si guarda resta quella anche se cambia posto.
  val reorder = rememberFluidReorderState(strip, onMove = { from, to ->
    val watched = pages.getOrNull(pager.currentPage)?.id
    state.move(from, to)
    val at = state.pages.indexOfFirst { it.id == watched }
    if (at >= 0 && at != pager.currentPage) scope.launch { pager.scrollToPage(at) }
  })
  Box(Modifier.fillMaxSize().background(Color.Black).testTag(FluidScanTags.Review)) {
    HorizontalPager(pager, Modifier.fillMaxSize().glassBackdropSource(backdrop), key = { pages.getOrNull(it)?.id ?: it }) { index ->
      val item = pages.getOrNull(index) ?: return@HorizontalPager
      ScanReviewPage(state, item, strings.page(index + 1, count))
    }
    // In alto: indietro alla fotocamera, «2 di 5», Salva.
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically) {
      FluidGlassIconButton(onClick = onBack, backdrop = backdrop) { FluidIcon(FluidIcons.ArrowBackIosNew, strings.back) }
      Spacer(Modifier.weight(1f))
      Box(Modifier.fluidGlass(shape = FluidCapsuleShape, backdrop = backdrop).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(strings.pageOf(current + 1, count), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold,
          fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurface)
      }
      Spacer(Modifier.weight(1f))
      FluidGlassButton(strings.save, onSave, backdrop = backdrop, enabled = !saving, style = FluidGlassButtonStyle.Prominent,
        modifier = Modifier.testTag(FluidScanTags.Save))
    }
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (filters) ScanFilterStrip(state, page, strings, backdrop) { f -> state.setFilter(page.id, f) }
      // La fila delle pagine: tocca per andarci, tieni premuto e trascina per spostarla.
      LazyRow(state = strip, modifier = Modifier.fillMaxWidth().height(ReviewDefaults.StripHeight).testTag(FluidScanTags.Strip),
        contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(pages, key = { _, p -> p.id }) { index, p ->
          val thumb = state.thumbnail(p.id)
          val chosen = index == current
          Box(fluidReorderable(reorder, key = p.id, shape = ContinuousCornerShape(6.dp),
            strings = FluidReorderStrings())
            .height(ReviewDefaults.StripThumbHeight).aspectRatio(ReviewDefaults.ThumbAspect)
            .testTag(FluidScanTags.stripPage(index))
            .clip(ContinuousCornerShape(6.dp))
            .background(Color.White)
            .border(if (chosen) 2.5.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f), ContinuousCornerShape(6.dp))
            .fluidPressable(onClick = { scope.launch { pager.animateScrollToPage(index) } }, role = Role.Button)
            .semantics {
              contentDescription = strings.page(index + 1, count)
              selected = chosen
            }) {
            if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
          }
        }
      }
      // I comandi della pagina, in una capsula di vetro.
      Row(Modifier.fluidGlass(shape = FluidCapsuleShape, backdrop = backdrop).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        ReviewAction(FluidIcons.Crop, strings.crop, FluidScanTags.Crop) { onCrop(page.id, current) }
        ReviewAction(FluidIcons.RotateRight, strings.rotate, FluidScanTags.Rotate) { state.rotate(page.id) }
        ReviewAction(FluidIcons.Tune, strings.filter, FluidScanTags.Filter, selected = filters) { filters = !filters }
        ReviewAction(FluidIcons.PhotoCamera, strings.retake, FluidScanTags.Retake) { onRetake(page.id, current) }
        ReviewAction(FluidIcons.Delete, strings.delete, FluidScanTags.Delete, tint = MaterialTheme.colorScheme.error) {
          val at = current
          state.remove(page.id)
          if (state.pages.isNotEmpty()) scope.launch { pager.scrollToPage(at.coerceAtMost(state.pages.lastIndex)) }
        }
      }
    }
  }
}

@Composable
private fun ReviewAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String,
  selected: Boolean = false, tint: Color = Color.Unspecified, onClick: () -> Unit) {
  Box(Modifier.size(ReviewDefaults.ActionSize).clip(FluidCapsuleShape)
    .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
    .fluidPressable(onClick = onClick, role = Role.Button).testTag(tag)
    .semantics { contentDescription = label; if (selected) stateDescription = "attivo" },
    contentAlignment = Alignment.Center) {
    FluidIcon(icon, null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint)
  }
}

/** Una pagina pulita a tutto schermo: prima la miniatura (subito), poi la pagina intera dal disco. */
@Composable
private fun ScanReviewPage(state: FluidScanState, page: FluidScanPage, description: String) {
  val thumb = state.thumbnail(page.id)
  val screen = with(LocalDensity.current) {
    val c = LocalConfiguration.current
    maxOf(c.screenWidthDp, c.screenHeightDp).dp.roundToPx()
  }
  val full by produceState<ImageBitmap?>(null, page, thumb) {
    value = if (thumb == null) null else state.cleanPage(page.id, screen)?.asImageBitmap()
  }
  val shown = full ?: thumb
  Box(Modifier.fillMaxSize().padding(top = 72.dp, bottom = 190.dp, start = 20.dp, end = 20.dp), contentAlignment = Alignment.Center) {
    if (shown != null) {
      Image(shown, description, Modifier.aspectRatio(shown.width.toFloat() / shown.height).shadow(6.dp),
        contentScale = ContentScale.Fit)
    } else {
      Text("…", color = Color.White, textAlign = TextAlign.Center)
    }
  }
}

/**
 * I quattro filtri della pagina, come le anteprime di VisionKit: la pagina con ognuno, e il nome sotto. Le anteprime si
 * calcolano dalla foto ridotta, fuori dal filo principale.
 */
@Composable
private fun ScanFilterStrip(state: FluidScanState, page: FluidScanPage, strings: FluidScanStrings, backdrop: GlassBackdropState,
  onPick: (FluidScanFilter) -> Unit) {
  val previews by produceState<Map<FluidScanFilter, ImageBitmap>>(emptyMap(), page.id, page.quad, page.quarterTurns) {
    val photo = state.photo(page.id, ReviewDefaults.FilterPreviewSource) ?: return@produceState
    value = withContext(Dispatchers.Default) {
      val straight = FluidScanImages.straighten(photo, page.quad, page.quarterTurns, ReviewDefaults.FilterPreviewSide)
      photo.recycle()
      FluidScanFilter.All.associateWith { f ->
        val out: Bitmap = FluidScanImages.filter(straight, f)
        out.asImageBitmap()
      }
    }
  }
  Row(Modifier.fluidGlass(shape = ContinuousCornerShape(22.dp), backdrop = backdrop).padding(10.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    for (f in FluidScanFilter.All) {
      val chosen = f == page.filter
      Column(Modifier.width(ReviewDefaults.FilterWidth).fluidPressable(onClick = { onPick(f) }, role = Role.Button)
        .testTag(FluidScanTags.filter(f)).semantics(mergeDescendants = true) { selected = chosen },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(ReviewDefaults.ThumbAspect).clip(ContinuousCornerShape(6.dp)).background(Color.White)
          .border(if (chosen) 2.5.dp else 0.dp, if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent, ContinuousCornerShape(6.dp))) {
          previews[f]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(strings.filterNames(f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center, maxLines = 2)
      }
    }
  }
}

internal object ReviewDefaults {
  /** La fila: miniature alte 58 dp in una striscia di 66. A occhio, sulla revisione di VisionKit in iOS 26. */
  val StripHeight = 66.dp
  val StripThumbHeight = 58.dp
  /** Le proporzioni di serie di una miniatura: un A4 in verticale. */
  const val ThumbAspect = 0.7071f
  /** I tasti della capsula: 44 dp, il bersaglio minimo di Apple. */
  val ActionSize = 44.dp
  val FilterWidth = 64.dp
  const val FilterPreviewSource = 900
  const val FilterPreviewSide = 240
}
