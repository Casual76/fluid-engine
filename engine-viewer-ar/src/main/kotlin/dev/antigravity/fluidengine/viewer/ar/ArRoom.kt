package dev.antigravity.fluidengine.viewer.ar

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.viewer.scene.FluidSceneModel
import java.text.NumberFormat

@Composable
internal fun ArRoom(model: FluidSceneModel, onClose: () -> Unit, onFailure: () -> Unit) {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
    LaunchedEffect(Unit) { onFailure() }
    return
  }
  var state by remember { mutableStateOf(ArRoomState()) }
  var texture by remember { mutableStateOf<ArTexture?>(null) }
  val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
  val backdrop = rememberGlassBackdrop()
  val spacing = FluidScreenDefaults.HorizontalPadding
  val locale = LocalConfiguration.current.locales[0]
  val scaleFormat = remember(locale) {
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2; minimumFractionDigits = 1 }
  }
  Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    AndroidView(
      modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop),
      factory = { context ->
        ArTexture(context, model, { state = it }, backdrop::notifyChanged, onFailure).also { texture = it }
      },
      onReset = null,
      onRelease = { it.dispose(); texture = null },
      update = { it.setActive(lifecycle.isAtLeast(Lifecycle.State.RESUMED)) },
    )
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(spacing)) {
      FluidGlassButton(stringResource(R.string.ar_model), onClose, Modifier.align(Alignment.TopStart), backdrop = backdrop)
      Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing / 2)) {
        val label = when (state.tracking) {
          ArTracking.Starting -> stringResource(R.string.ar_camera_starting)
          ArTracking.Scanning -> stringResource(R.string.ar_scan)
          ArTracking.SurfaceFound -> stringResource(R.string.ar_place)
          ArTracking.Placed -> stringResource(R.string.ar_scale, "${scaleFormat.format(state.scale)}×")
          ArTracking.Lost -> stringResource(R.string.ar_tracking_lost)
        }
        val announce = if (state.tracking == ArTracking.Placed) Modifier else Modifier.semantics {
          liveRegion = LiveRegionMode.Polite
        }
        Box(announce.fluidGlass(backdrop = backdrop).padding(horizontal = spacing, vertical = spacing / 2)) {
          Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.tracking == ArTracking.SurfaceFound) {
          FluidGlassButton(stringResource(R.string.ar_place_center), { texture?.placeAtCenter() }, backdrop = backdrop)
        }
      }
    }
  }
}
