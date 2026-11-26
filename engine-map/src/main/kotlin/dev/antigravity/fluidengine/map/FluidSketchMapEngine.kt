/*
 * engine-map (4.0, F8.26): il disegno della mappa, per le prove sul computer e per la galleria senza rete, coi gesti
 * del dito (trascinare, lanciare, pizzicare, ruotare, doppio tocco). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * I colori del disegno, chiaro e scuro: quelli di Liberty e Dark di OpenFreeMap, campionati dallo stile il 26/11/2026,
 * perche' passando dal disegno alla mappa vera il colore non salti.
 */
@Immutable
data class FluidSketchPalette(
  val land: Color,
  val water: Color,
  val park: Color,
  val road: Color,
  val minorRoad: Color,
) {
  companion object {
    val Light = FluidSketchPalette(Color(0xFFF8F4F0), Color(0xFFA0C8F0), Color(0xFFD8E8C8), Color(0xFFFFFFFF), Color(0xFFEDE7E0))
    val Dark = FluidSketchPalette(Color(0xFF1F2430), Color(0xFF16263A), Color(0xFF223326), Color(0xFF3A4252), Color(0xFF2B313D))
  }
}

/**
 * La mappa disegnata dall'engine: terra, acqua, parchi e strade **inventati** (una griglia con le celle scelte da un
 * numero fisso), ma fermi sul luogo, cosi' che trascinare, pizzicare e ruotare si vedano e si misurino. Niente rete e
 * niente librerie native: e' il motore delle prove sul computer (MapLibre non si disegna in Robolectric), della galleria
 * senza rete e dell'anteprima di una mappa.
 *
 * I gesti sono quelli di Mappe e di MapLibre: un dito trascina (e lancia, con l'attrito di [FlingFriction]), due dita
 * pizzicano e ruotano attorno al punto fra le dita, il doppio tocco avvicina di un passo attorno al tocco. Niente blur
 * su niente di cio' che muove il dito.
 */
class FluidSketchMapEngine(
  private val palette: (dark: Boolean) -> FluidSketchPalette = { if (it) FluidSketchPalette.Dark else FluidSketchPalette.Light },
) : FluidMapEngine {
  override val glassSamplesMap: Boolean get() = true

  @Composable
  override fun Surface(state: FluidMapState, style: FluidMapStyle, dark: Boolean, modifier: Modifier) {
    val colors = palette(dark)
    val scope = rememberCoroutineScope()
    val doubleTapMillis = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val slop = LocalViewConfiguration.current.touchSlop
    // Il volo e' quello dello stato (questo motore non vola da se'): nessun conducente da collegare.
    Canvas(
      modifier
        .fillMaxSize()
        .pointerInput(state) {
          var lastTapAt = 0L
          var lastTapPosition = Offset.Zero
          var fling: Job? = null
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fling?.cancel()
            scope.launch { state.stopFlight() }
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var moved = false
            var multi = false
            var travelled = 0f
            var pointers = 1
            // Il tratto prima della soglia non si perde: superata la soglia la mappa raggiunge il dito, e il punto toccato
            // resta sotto il dito (come MapLibre).
            var pending = Offset.Zero
            do {
              val event = awaitPointerEvent()
              val pressed = event.changes.count { it.pressed }
              if (pressed == 0) break
              pointers = max(pointers, pressed)
              if (pressed > 1) multi = true
              var pan = event.calculatePan()
              val zoom = event.calculateZoom()
              val rotation = event.calculateRotation()
              travelled += pan.getDistance()
              if (!moved) pending += pan
              if (!moved && (travelled > slop || abs(zoom - 1f) > 0.02f || abs(rotation) > 2f)) {
                moved = true
                pan = pending
                state.reportGesture(true)
              }
              if (moved) {
                val projector = state.projector()
                if (projector != null) {
                  val centroid = event.calculateCentroid(useCurrent = true)
                  var next = projector.camera
                  if (pressed > 1 && (zoom != 1f || rotation != 0f)) {
                    next = projector.transformed(centroid, ln(zoom.toDouble()) / ln(2.0), rotation.toDouble(), state.minZoom, state.maxZoom)
                  }
                  next = FluidMapProjector(next, projector.widthPx, projector.heightPx, projector.density).panned(pan)
                  state.report(next, byGesture = true)
                }
                event.changes.forEach { if (it.positionChanged()) it.consume() }
              }
              event.changes.firstOrNull { it.id == down.id && it.pressed }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
            } while (true)
            if (!moved) {
              // Un tocco: il secondo entro il doppio tocco avvicina attorno al dito, il primo dice il punto alla mappa.
              val now = down.uptimeMillis
              if (now - lastTapAt < doubleTapMillis && (down.position - lastTapPosition).getDistance() < slop * 4) {
                lastTapAt = 0L
                val projector = state.projector()
                if (projector != null) {
                  val target = projector.transformed(down.position, 1.0, 0.0, state.minZoom, state.maxZoom)
                  scope.launch { state.animateTo(target, FluidMapMotion.DoubleTapMillis) }
                }
              } else {
                lastTapAt = now
                lastTapPosition = down.position
                state.projector()?.let { state.reportTap(it.toGeo(down.position)) }
              }
              return@awaitEachGesture
            }
            val velocity = if (multi) Velocity.Zero else tracker.calculateVelocity()
            if (hypot(velocity.x, velocity.y) < MinFlingVelocity) {
              state.reportGesture(false)
              return@awaitEachGesture
            }
            fling = scope.launch {
              try {
                var last = Offset.Zero
                AnimationState(Offset.VectorConverter, Offset.Zero, Offset(velocity.x, velocity.y)).animateDecay(exponentialDecay(FlingFriction)) {
                  val step = value - last
                  last = value
                  val projector = state.projector() ?: return@animateDecay
                  state.report(projector.panned(step), byGesture = true)
                }
              } finally {
                state.reportGesture(false)
              }
            }
          }
        },
    ) {
      val projector = FluidMapProjector(state.camera, size.width, size.height, density)
      drawSketch(projector, colors)
    }
  }

  override suspend fun snapshot(request: FluidMapSnapshotRequest): ImageBitmap {
    val image = ImageBitmap(request.widthPx.coerceAtLeast(1), request.heightPx.coerceAtLeast(1))
    val canvas = ComposeCanvas(image)
    val size = Size(image.width.toFloat(), image.height.toFloat())
    CanvasDrawScope().draw(Density(request.density), LayoutDirection.Ltr, canvas, size) {
      drawSketch(FluidMapProjector(request.camera, size.width, size.height, request.density), palette(request.dark))
    }
    return image
  }

  companion object {
    /** L'attrito del lancio: quello di `exponentialDecay` a 1,5, che si ferma in circa mezzo secondo come Mappe. A occhio, 26/11/2026. */
    const val FlingFriction: Float = 1.5f

    /** Sotto questa velocita' (px/s) il dito si ferma senza lancio. */
    const val MinFlingVelocity: Float = 400f
  }
}

/** Il disegno della mappa per [projector]: senza allocazioni, solo le righe e le celle che si vedono. */
internal fun DrawScope.drawSketch(projector: FluidMapProjector, colors: FluidSketchPalette) {
  drawRect(colors.land)
  val world = projector.worldPx
  // Le strade: una griglia del mondo il cui passo sta fra 72 e 144 px sullo schermo, e le minori a meta'.
  var cells = 1.0
  while (world / cells > 144.0 && cells < (1 shl 26)) cells *= 2.0
  val step = 1.0 / cells
  val radius = projector.screenRadiusNormalized()
  val cx = projector.centerX
  val cy = projector.centerY
  val x0 = floor((cx - radius) / step).toLong()
  val x1 = floor((cx + radius) / step).toLong()
  val y0 = max(0L, floor((cy - radius) / step).toLong())
  val y1 = min((cells - 1).toLong(), floor((cy + radius) / step).toLong())
  val bearing = projector.camera.bearing.toFloat()
  val c = Offset(size.width / 2f, size.height / 2f)
  // Le celle e le strade si disegnano nel riferimento del nord, e la tela ruota attorno al centro.
  val north = FluidMapProjector(projector.camera.copy(bearing = 0.0), size.width, size.height, projector.density)
  rotate(-bearing, c) {
    // L'acqua e i parchi: celle scelte da un numero fisso della cella (sempre le stesse a quello zoom).
    for (gx in x0..x1) for (gy in y0..y1) {
      val h = cellHash(gx, gy, cells)
      val kind = h % 23
      if (kind != 0L && kind != 7L && kind != 11L) continue
      val tl = north.toScreenNormalized(gx * step, gy * step)
      val br = north.toScreenNormalized((gx + 1) * step, (gy + 1) * step)
      val color = if (kind == 0L) colors.water else colors.park
      drawRect(color, topLeft = Offset(min(tl.x, br.x), min(tl.y, br.y)), size = Size(abs(br.x - tl.x), abs(br.y - tl.y)))
    }
    val half = step / 2
    val minorWidth = 1.2f * projector.density
    val roadWidth = 3.2f * projector.density
    for (gx in x0..x1 + 1) {
      val a = north.toScreenNormalized(gx * step, (cy - radius).coerceAtLeast(0.0))
      val b = north.toScreenNormalized(gx * step, (cy + radius).coerceAtMost(1.0))
      drawLine(colors.road, a, b, roadWidth, StrokeCap.Butt)
      val m1 = north.toScreenNormalized(gx * step + half, (cy - radius).coerceAtLeast(0.0))
      val m2 = north.toScreenNormalized(gx * step + half, (cy + radius).coerceAtMost(1.0))
      drawLine(colors.minorRoad, m1, m2, minorWidth, StrokeCap.Butt)
    }
    for (gy in y0..y1 + 1) {
      val a = north.toScreenNormalized(cx - radius, gy * step)
      val b = north.toScreenNormalized(cx + radius, gy * step)
      drawLine(colors.road, a, b, roadWidth, StrokeCap.Butt)
      val m1 = north.toScreenNormalized(cx - radius, gy * step + half)
      val m2 = north.toScreenNormalized(cx + radius, gy * step + half)
      drawLine(colors.minorRoad, m1, m2, minorWidth, StrokeCap.Butt)
    }
  }
}

/** Un numero fisso per la cella ([x], [y]) di una griglia di [cells] per lato (il giro del mondo riportato). */
private fun cellHash(x: Long, y: Long, cells: Double): Long {
  val n = cells.toLong().coerceAtLeast(1)
  val wx = ((x % n) + n) % n
  var h = wx * 73856093L xor (y * 19349663L) xor (n * 83492791L)
  h = h xor (h ushr 13)
  h *= 0x5bd1e995L
  h = h xor (h ushr 15)
  return abs(h)
}
