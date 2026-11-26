/*
 * engine-scan (4.0, F8.17): il ritaglio a mano, come quello di VisionKit: la foto intera, il foglio con i quattro
 * angoli da trascinare e la lente che ingrandisce sotto il dito. Il dito resta nitido (F8). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
internal fun ScanCropScreen(
  state: FluidScanState,
  id: String,
  backdrop: GlassBackdropState,
  strings: FluidScanStrings,
  onDone: (FluidScanQuad?) -> Unit,
) {
  val page = state.pages.firstOrNull { it.id == id }
  if (page == null) {
    androidx.compose.runtime.LaunchedEffect(id) { onDone(null) }
    return
  }
  val density = LocalDensity.current
  val photo by produceState<ImageBitmap?>(null, page.id) {
    value = state.photo(page.id, CropDefaults.PhotoLongSide)?.asImageBitmap()
  }
  var quad by remember(page.id) { mutableStateOf(page.quad) }
  var dragging by remember { mutableIntStateOf(-1) }
  var finger by remember { mutableStateOf(Offset.Zero) }
  val scope = rememberCoroutineScope()
  val aspect = page.photoWidth.toFloat() / page.photoHeight
  Box(Modifier.fillMaxSize().background(Color.Black).testTag(FluidScanTags.CropEditor)) {
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
      .padding(top = 64.dp, bottom = 88.dp, start = 28.dp, end = 28.dp), contentAlignment = Alignment.Center) {
      Box(Modifier.aspectRatio(aspect).glassBackdropSource(backdrop)) {
        val image = photo
        if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        // Il foglio: fuori un velo scuro, il contorno e gli angoli.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
          val path = Path().apply {
            quad.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * size.width, p.y * size.height) else lineTo(p.x * size.width, p.y * size.height) }
            close()
          }
          drawRect(Color.Black.copy(alpha = 0.5f))
          drawPath(path, Color.Transparent, blendMode = BlendMode.Clear)
          drawPath(path, Color.White, style = Stroke(1.5.dp.toPx()))
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
          val w = constraints.maxWidth.toFloat()
          val h = constraints.maxHeight.toFloat()
          for (corner in 0 until 4) {
            val p = quad[corner]
            val handle = CropDefaults.HandleTouch
            val half = with(density) { handle.toPx() / 2f }
            Box(Modifier.offset { IntOffset((p.x * w - half).roundToInt(), (p.y * h - half).roundToInt()) }
              .size(handle).testTag(FluidScanTags.corner(corner))
              .pointerInput(corner, w, h) {
                detectDragGestures(
                  onDragStart = { dragging = corner; finger = Offset(quad[corner].x * w, quad[corner].y * h) },
                  onDragEnd = { dragging = -1 },
                  onDragCancel = { dragging = -1 },
                ) { change, amount ->
                  change.consume()
                  val current = quad[corner]
                  val moved = FluidScanPoint((current.x + amount.x / w).coerceIn(0f, 1f), (current.y + amount.y / h).coerceIn(0f, 1f))
                  val next = quad.with(corner, moved)
                  // Un foglio che si incrocia non si può raddrizzare: l'angolo si ferma sul bordo del convesso.
                  if (next.isConvex(aspect)) quad = next
                  finger = Offset(quad[corner].x * w, quad[corner].y * h)
                }
              }
              .semantics {
                contentDescription = strings.corners[corner]
                customActions = listOf(
                  CustomAccessibilityAction(strings.moveLeft) { nudge(quad, corner, -CropDefaults.Nudge, 0f, aspect)?.let { quad = it }; true },
                  CustomAccessibilityAction(strings.moveRight) { nudge(quad, corner, CropDefaults.Nudge, 0f, aspect)?.let { quad = it }; true },
                  CustomAccessibilityAction(strings.moveUp) { nudge(quad, corner, 0f, -CropDefaults.Nudge, aspect)?.let { quad = it }; true },
                  CustomAccessibilityAction(strings.moveDown) { nudge(quad, corner, 0f, CropDefaults.Nudge, aspect)?.let { quad = it }; true },
                )
              }, contentAlignment = Alignment.Center) {
              Box(Modifier.size(CropDefaults.HandleSize).background(Color.White, androidx.compose.foundation.shape.CircleShape)
                .padding(3.dp).background(Color.White.copy(alpha = 0f)))
            }
          }
          // La lente: il pezzo di foto sotto il dito, ingrandito due volte e mezza, sopra il dito.
          val image2 = photo
          if (dragging >= 0 && image2 != null) {
            val lens = with(density) { CropDefaults.LensSize.toPx() }
            val lift = with(density) { CropDefaults.LensLift.toPx() }
            Canvas(Modifier.offset { IntOffset((finger.x - lens / 2).roundToInt(), (finger.y - lift - lens).roundToInt()) }
              .size(CropDefaults.LensSize)) {
              val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
              clipPath(circle) {
                val zoom = CropDefaults.LensZoom
                val sx = image2.width / w
                val sy = image2.height / h
                val srcW = (size.width / zoom * sx).roundToInt().coerceAtLeast(1)
                val srcH = (size.height / zoom * sy).roundToInt().coerceAtLeast(1)
                val srcX = (finger.x * sx - srcW / 2f).roundToInt().coerceIn(0, (image2.width - srcW).coerceAtLeast(0))
                val srcY = (finger.y * sy - srcH / 2f).roundToInt().coerceIn(0, (image2.height - srcH).coerceAtLeast(0))
                drawRect(Color.Black)
                drawImage(image2, IntOffset(srcX, srcY), IntSize(srcW, srcH), dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                // La croce nel centro: dove va l'angolo.
                val c = Offset(size.width / 2, size.height / 2)
                drawLine(Color.White, c.copy(x = c.x - 10.dp.toPx()), c.copy(x = c.x + 10.dp.toPx()), 1.5.dp.toPx())
                drawLine(Color.White, c.copy(y = c.y - 10.dp.toPx()), c.copy(y = c.y + 10.dp.toPx()), 1.5.dp.toPx())
              }
              drawPath(circle, Color.White, style = Stroke(2.dp.toPx()))
            }
          }
        }
      }
    }
    // In basso: Annulla, rileva di nuovo o tutta la foto, Fine.
    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
      verticalAlignment = Alignment.CenterVertically) {
      FluidGlassButton(strings.cancel, { onDone(null) }, backdrop = backdrop)
      Spacer(Modifier.weight(1f))
      val whole = quad == FluidScanQuad.Full
      FluidGlassButton(if (whole) strings.reset else strings.wholePhoto, {
        if (!whole) quad = FluidScanQuad.Full
        else scope.launch {
          val photo = state.photo(page.id, 1024) ?: return@launch
          val found = withContext(Dispatchers.Default) { FluidScanEdges.detect(photo)?.quad }
          photo.recycle()
          if (found != null) quad = found
        }
      }, backdrop = backdrop)
      Spacer(Modifier.weight(1f))
      FluidGlassButton(strings.done, { onDone(quad) }, backdrop = backdrop, style = FluidGlassButtonStyle.Prominent,
        modifier = Modifier.testTag(FluidScanTags.CropDone))
    }
  }
}

private fun nudge(quad: FluidScanQuad, corner: Int, dx: Float, dy: Float, aspect: Float): FluidScanQuad? {
  val p = quad[corner]
  val next = quad.with(corner, FluidScanPoint((p.x + dx).coerceIn(0f, 1f), (p.y + dy).coerceIn(0f, 1f)))
  return next.takeIf { it.isConvex(aspect) }
}

internal object CropDefaults {
  /** La foto del ritaglio: lato lungo 1600 px, abbastanza per la lente a 2,5×. */
  const val PhotoLongSide = 1600
  /** L'angolo: un cerchio da 22 dp in un bersaglio da 48. A occhio, sulle maniglie del ritaglio di VisionKit. */
  val HandleSize = 22.dp
  val HandleTouch = 48.dp
  /** La lente: 110 dp, 2,5×, 36 dp sopra il dito. A occhio (VisionKit la mostra sopra il dito che trascina). */
  val LensSize = 110.dp
  const val LensZoom = 2.5f
  val LensLift = 36.dp
  /** Lo spostamento di un angolo con TalkBack: l'1% della foto. */
  const val Nudge = 0.01f
}
