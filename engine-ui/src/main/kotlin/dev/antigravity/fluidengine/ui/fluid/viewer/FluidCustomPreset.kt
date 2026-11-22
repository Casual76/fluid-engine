package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsuleDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionBackEffect
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.page.LocalFluidPagePreparing
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront

// F6C.8 · Il preset minimo della forma `Custom`: la pagina la disegna il modulo, il telaio le dà la barra, «Trova» e
// il posto fra le barre. Il preset vero di ogni famiglia è la pagina stessa; la grammatica comune la rifà F6C.9.

private object CustomPresetButton {
  const val Find = "fluid.viewer.custom.find"
}

@Immutable
private data class CustomScope(
  override val chromeVisible: Boolean,
  override val finding: Boolean,
  override val active: Boolean,
  override val contentPadding: PaddingValues,
) : FluidViewerCustomScope

/**
 * A page drawn by a module ([FluidViewerContent.Custom]) inside the [FluidFileViewer] renderer slot. The module's
 * [FluidViewerContent.Custom.actions] go to the viewer's bar for this page; with [FluidViewerContent.Custom.find] the
 * bar gets «Trova», which opens the find field above the keyboard (F6B.6) and closes when the page is left or on
 * back. The page receives [FluidViewerCustomScope.contentPadding] to keep its content between the bars.
 */
@Composable
fun FluidCustomPreset(
  content: FluidViewerContent.Custom,
  chromeVisible: Boolean,
  modifier: Modifier = Modifier,
) {
  val find = content.find
  var finding by rememberSaveable { mutableStateOf(false) }
  val active = currentFluidViewerPageActive()
  LaunchedEffect(active, find) { if (!active || find == null) finding = false }

  ViewerPresetToolbar {
    if (find != null) {
      if (finding) search(FluidSearchSpec.findInPage(find.query, find::onQueryChange, find.navigation, active = true,
        onActiveChange = { if (!it) finding = false }))
      button(CustomPresetButton.Find, FluidIcons.Search, find.label, { finding = true },
        placement = FluidToolbarPlacement.BottomBar)
    }
    content.actions(this)
  }
  val front by LocalFluidRouteFront.current
  if (finding && active && front && !LocalFluidPagePreparing.current) FluidSelectionBackEffect(enabled = true) { finding = false }

  val gap: Dp = FluidScreenDefaults.HorizontalPadding / 2
  val top = FluidScreenDefaults.topBarHeight() + gap
  val bottom = if (finding) {
    maxOf(FluidScreenDefaults.bottomContentPadding(FluidFloatingCapsuleDefaults.ActionSize),
      WindowInsets.ime.asPaddingValues().calculateBottomPadding() + FluidScreenDefaults.ControlRowHeight + gap)
  } else {
    FluidScreenDefaults.bottomContentPadding(FluidFloatingCapsuleDefaults.ActionSize)
  }
  val scope = CustomScope(chromeVisible, finding, active, PaddingValues(top = top, bottom = bottom))
  Box(modifier.fillMaxSize()) { content.draw(scope) }
}
