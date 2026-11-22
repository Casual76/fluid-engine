package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalGraphicsContext
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.glassSurface

/** Fa viaggiare il vetro della pillola fino alla posa. Usare la stessa tinta e le stesse ottiche
 * della pillola. La destinazione può avere un fondo opaco proprio. */
fun FluidTransitionSource.growingGlass(
  tint: GlassTint? = null,
  optics: GlassOptics = GlassDefaults.buttonOptics(),
): FluidTransitionSource = withGrowingGlass(GrowingPageGlass(tint, optics))

/** La pillola d'origine di «Cresce». La fotografia presta soltanto la faccia: il vetro, già al
 * primo fotogramma, viene disegnato alla misura del viaggio. Passare la stessa [tint] e le stesse
 * [optics] a [growingGlass] nella dichiarazione della destinazione. */
@Composable
fun FluidGrowingPagePill(
  id: Any,
  ns: FluidGlassNamespace,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  shape: Shape = FluidCapsuleShape,
  tint: GlassTint = GlassDefaults.controlTint(),
  optics: GlassOptics = GlassDefaults.buttonOptics(),
  content: @Composable BoxScope.() -> Unit,
) {
  val source = remember(id, ns) { FluidTransitionSource(id, ns) }
  Box(modifier.fluidTransitionSource(id, ns, shape)) {
    Box(Modifier.matchParentSize().drawWithContent { if (!source.isHidden) drawContent() }
      .glassSurface(backdrop, tint, shape, role = GlassRole.Interactive, optics = optics))
    content()
  }
}

internal data class GrowingPageGlass(val tint: GlassTint?, val optics: GlassOptics)

/** La stessa immagine in due sedi. Mettere il modifier prima del disegno in entrambe le pagine.
 * La prima immagine nella sorgente presta la propria identità a un solo viaggio. */
fun Modifier.fluidSharedElement(key: Any, ns: FluidGlassNamespace): Modifier =
  pageInk(SharedKey(ns, key))

/** I comandi in primo piano di una pagina «Cresce». Posarli insieme su un contenitore trasparente:
 * arrivano dopo il 70% mentre il fondo, anche quando la pagina ne dipinge uno suo, resta vetro. */
fun Modifier.fluidGrowingPageChrome(): Modifier = pageInk(null)

internal data class SharedKey(val ns: FluidGlassNamespace, val key: Any)
internal class SharedPageSpace(val host: FluidPageHostState, val page: Any) {
  var coordinates: LayoutCoordinates? = null
}

internal val LocalSharedPage = staticCompositionLocalOf<SharedPageSpace?> { null }

internal class PageInk(val key: SharedKey?, val space: SharedPageSpace?, val graphics: GraphicsContext) {
  var bounds by mutableStateOf<Rect?>(null)
  var root: LayoutCoordinates? = null
  var layer by mutableStateOf<GraphicsLayer?>(null)
  fun release() {
    layer?.let(graphics::releaseGraphicsLayer)
    layer = null
  }
}

internal class SharedOrigin(val identity: SharedKey, val bounds: Rect, val ink: PageInk)
private val placedInk = mutableStateListOf<PageInk>()

private fun Modifier.pageInk(identity: SharedKey?): Modifier = composed {
  val space = LocalSharedPage.current
  val graphics = LocalGraphicsContext.current
  val ink = remember(identity, space, graphics) { PageInk(identity, space, graphics) }
  DisposableEffect(ink) {
    placedInk.add(ink)
    onDispose {
      placedInk.remove(ink)
      ink.release()
      ink.root = null
    }
  }
  this.onGloballyPositioned { coords ->
    ink.root = coords.findRootCoordinates()
    val parent = space?.coordinates ?: ink.root
    if (parent != null && parent.isAttached) ink.bounds = parent.localBoundingBoxOf(coords, clipBounds = false)
  }.drawWithContent {
    val host = space?.host
    // La sorgente può vivere in una shell esterna all'host. Quando è consumata, la fotografia del
    // genitore deve escludere l'immagine che il palco già disegna.
    val owned = if (identity == null) {
      host != null && host.growing != null && host.upper == space.page && host.zoom != null
    } else {
      (host?.sharedPair?.let { it.first.ink === ink || it.second === ink } == true) ||
        (space == null && placedInk.any { it.space?.host?.sharedPair?.first?.ink === ink })
    }
    if (owned) {
      val layer = ink.layer ?: graphics.createGraphicsLayer()
      layer.record { this@drawWithContent.drawContent() }
      ink.layer = layer
    } else {
      ink.release()
      drawContent()
    }
  }
}

internal fun FluidPageHostState.findSharedOrigins(source: Rect): List<SharedOrigin> {
  val host = hostCoordinates ?: return emptyList()
  val origins = mutableListOf<SharedOrigin>()
  val root = host.findRootCoordinates()
  for (ink in placedInk) {
    val key = ink.key ?: continue
    if (ink.root !== root) continue
    val space = ink.space
    if (space != null && (space.host !== this || space.page != lower)) continue
    val local = ink.bounds ?: continue
    val box = if (space != null) local else local.translate(-hostOriginInRoot)
    if (box.isFinite && !box.isEmpty && source.contains(box.center)) origins.add(SharedOrigin(key, box, ink))
  }
  return origins
}

internal fun FluidPageHostState.findSharedPair(): Pair<SharedOrigin, PageInk>? {
  if (zoom == null) return null
  for (origin in sharedOrigins) {
    val target = placedInk.firstOrNull { ink ->
      ink.key == origin.identity && ink.space?.host === this && ink.space.page == upper && ink.bounds != null
    }
    if (target != null) return origin to target
  }
  return null
}

internal fun FluidPageHostState.zoomFaces(): ZoomFaces {
  val p = fractionToward { it == upper }
  val destination = upper
  if (destination != null && destination in ownPageBackground && sharedPair != null)
    return viewerZoomFaces(p, backdropArrival(destination))
  if (growing != null || sharedPair != null) return FluidZoom.faces(p)
  // Senza immagine condivisa il centro resta occupato dalla pagina in scala. Usiamo l'orologio
  // dello zoom F5B.3, con le due identità che si danno il cambio nel massimo della sfocatura.
  val u = zoomContentAlpha(FluidPageHostState.PageRole.Upper)
  val mapped = if (u < 0.5f) u * 2f * FluidPageMotion.SourceFaceEnd
    else FluidPageMotion.PageChromeStart + (u * 2f - 1f) * (1f - FluidPageMotion.PageChromeStart)
  return FluidZoom.faces(mapped)
}

/** The shared media travels on its own layer. Its controls stay with the arriving backdrop. */
internal fun viewerZoomFaces(progress: Float, backdrop: Float): ZoomFaces {
  val faces = FluidZoom.faces(progress)
  return faces.copy(pageAlpha = faces.pageAlpha * backdrop, sourceAlpha = 0f)
}

/** Continue the actual held image rectangle to the thumbnail or back to the fitted image. */
internal fun FluidPageHostState.sharedImageBounds(origin: Rect, target: Rect, progress: Float, shape: FluidZoomShape): Rect {
  val full = fullZoomShape()
  val journey = zoom ?: return androidx.compose.ui.geometry.lerp(origin, target, progress)
  val via = journey.viaExit
  if (!journey.pinched || via == null) return FluidZoom.sharedBounds(origin, target, progress, shape, journey.source, full)
  val held = FluidZoom.sharedBounds(target, target, 1f, journey.from, full, full)
  val exit = upperExitFraction()
  return if (exit <= via) androidx.compose.ui.geometry.lerp(target, held, exit / via)
    else androidx.compose.ui.geometry.lerp(held, origin, (exit - via) / (1f - via))
}

internal fun DrawScope.drawPageInk(layer: GraphicsLayer, rect: Rect) {
  if (layer.size.width <= 0 || layer.size.height <= 0 || rect.isEmpty) return
  translate(rect.left, rect.top) {
    scale(rect.width / layer.size.width, rect.height / layer.size.height, Offset.Zero) { drawLayer(layer) }
  }
}

@Composable
internal fun SharedPageOverlay(state: FluidPageHostState, modifier: Modifier) {
  Canvas(modifier) {
    val zoom = state.zoomShape() ?: return@Canvas
    val clip = Path().apply { addRoundRect(zoom.toRoundRect()) }
    clipPath(clip) {
      val p = state.fractionToward { it == state.upper }
      state.sharedPair?.let { (origin, target) ->
        val layer = target.layer ?: origin.ink.layer ?: return@let
        val box = state.sharedImageBounds(origin.bounds, target.bounds!!, p, zoom)
        drawPageInk(layer, box)
      }
      if (state.growing != null) {
        val frame = FluidZoom.faces(p)
        val full = state.fullZoomShape().bounds
        val content = FluidZoom.content(zoom, full)
        for (ink in placedInk) {
          if (ink.key != null || ink.space?.host !== state || ink.space.page != state.upper) continue
          val layer = ink.layer ?: continue
          val box = ink.bounds ?: continue
          val at = full.center + (box.topLeft - full.center) * content.scale + content.translation
          val rect = Rect(at + Offset(0f, frame.riseDp * density), box.size * content.scale)
          layer.alpha = frame.pageAlpha
          layer.renderEffect = frame.pageBlurEffect(density)
          drawPageInk(layer, rect)
        }
      }
    }
  }
}
