package dev.antigravity.fluidengine.sample.robustness

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.playground.PlaygroundStage
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluidphysics.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart

/** La serie JVM sul vetro vero; il palco adatta le coordinate, mai il layer del fondale. */
@Composable
internal fun RobustnessPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val activity = remember(context) { context.activity() }
  val cases = MorphRobustnessCases.all
  var index by remember { mutableIntStateOf(if (context.isDebuggableApp())
    cases.indexOfFirst { it.name == activity?.intent?.getStringExtra("robustness.case") }.coerceAtLeast(0) else 0) }
  var auto by remember { mutableStateOf(context.isDebuggableApp() && activity?.intent?.getStringExtra("robustness.auto") == "true") }
  val variants = listOf("diretto", "inverti25", "inverti50", "inverti75", "terza25", "terza50", "terza75", "doppio")
  var mode by remember { mutableIntStateOf(0) }
  var width by remember { mutableIntStateOf(0) }
  var replay by remember { mutableIntStateOf(0) }
  val case = cases[index]
  val state = rememberFluidPhysicsState(case.from)
  LaunchedEffect(index, width, replay, mode) {
    if (width == 0) return@LaunchedEffect
    val a = case.from.frame; val b = case.to.frame
    val c = MorphRobustnessCases.third.frame
    val box = Rect(minOf(a.left,b.left,c.left), minOf(a.top,b.top,c.top), maxOf(a.right,b.right,c.right), maxOf(a.bottom,b.bottom,c.bottom))
    val scale = (width - 32f) / maxOf(box.width, box.height, 1f)
    state.snapTo(case.from.fit(box, scale))
    delay(150)
    coroutineScope {
      val journey = launch(start = CoroutineStart.UNDISPATCHED) { state.morphTo(case.to.fit(box, scale)) }
      val variant = variants[mode]
      if (variant != "diretto") {
        if (variant != "doppio") {
          val threshold = variant.takeLast(2).toFloat() / 100f
          while (journey.isActive && state.progress < threshold) withFrameNanos { }
        }
        state.morphTo((if (variant.startsWith("inverti")) case.from else MorphRobustnessCases.third).fit(box, scale))
      }
      journey.join()
    }
  }
  LaunchedEffect(auto, index, mode) {
    if (auto) {
      delay(1500)
      if (mode == variants.lastIndex) { mode = 0; index = (index + 1) % cases.size } else mode++
    }
  }
  FluidScreen(title = "Robustezza", subtitle = "F1B.8 · ${index+1}/${cases.size} · ${case.name}",
    onBack = onBack, extraBottomPadding = bottomInset) {
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidGlassButton(text = "Precedente", onClick = { index = (index + cases.size - 1) % cases.size })
        FluidGlassButton(text = "Successivo", onClick = { index = (index + 1) % cases.size })
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidGlassButton(text = if(auto) "Ferma" else "Automatico", onClick = { auto = !auto })
        FluidGlassButton(text = "Ripeti", onClick = { replay++ })
      }
    }
    item {
      FluidGlassButton(text = "Variante: ${variants[mode]}", onClick = { mode = (mode + 1) % variants.size })
      Text("${case.name} / ${variants[mode]}")
      PlaygroundStage(state, tier = null, drawing = false, onSized = { width = it.width })
      Text("Coordinate adattate al palco. I pezzi lontanissimi diventano piccoli: è lo stesso caso del banco.")
    }
  }
}

private tailrec fun Context.activity(): Activity? = when(this) {
  is Activity -> this
  is ContextWrapper -> baseContext.activity()
  else -> null
}

private fun FluidForm.fit(box: Rect, scale: Float): FluidForm {
  val f = frame
  val rect = Rect(16f+(f.left-box.left)*scale,16f+(f.top-box.top)*scale,
    16f+(f.right-box.left)*scale,16f+(f.bottom-box.top)*scale)
  return when(this) {
    is FluidForm.Slab -> copy(frame=rect, cornerRadii=FluidCornerRadii(cornerRadii.topLeft*scale,
      cornerRadii.topRight*scale,cornerRadii.bottomRight*scale,cornerRadii.bottomLeft*scale))
    is FluidForm.Poly -> copy(frame=rect, rounding=rounding*scale)
    is FluidForm.Group -> copy(pieces=pieces.map { it.fit(box,scale) }, blendRadius=blendRadius*scale)
  }
}
