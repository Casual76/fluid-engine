/*
 * Chi muove le pagine: il dito dentro l'app, uno scorrimento che passa la mano, il back di sistema.
 *
 * Il trascinamento "ovunque" e la sua regola di rilascio vengono da LibrePods, **GPL-3.0**:
 *
 *   me.kavishdevar.librepods.presentation.navigation.SwipeBack.kt
 *     righe 152-182 (il back predittivo: segue, poi conferma o annulla)
 *     righe 184-196 e 226-257 (`draggable` orizzontale sulla pagina, i figli che consumano vincono)
 *   https://github.com/librepods-org/librepods, branch `android/rewrite`, commit 3d4d270.
 *
 * Licenza: GPL-3.0, https://www.gnu.org/licenses/gpl-3.0.html; vedi LICENSES/LibrePods.md.
 *
 * **Cosa abbiamo cambiato.**
 *   - il riconoscitore sta sull'host, non sulla pagina che si muove: le posizioni del dito dentro un
 *     layer traslato sono relative al layer, e una pagina che insegue il dito vedrebbe il dito fermo;
 *   - la corsa e' `dito − contatto` dal fotogramma che supera la soglia (LibrePods accumula i delta di
 *     `draggable` dopo la soglia: la pagina restava indietro di una soglia rispetto al dito);
 *   - i primi 20 dp del bordo reclamano il dito *prima* dei figli; un primo movimento contro
 *     l'uscita abbandona; uno scorrimento orizzontale al suo inizio passa la mano;
 *   - il back di sistema arriva dal dispatcher di navigationevent con `touchX` (non
 *     `PredictiveBackHandler` col `progress`), cosi' i gestori interni dell'app restano prima di noi;
 *   - la velocita' e' misurata e passa alla molla; nessuna vibrazione durante il gesto.
 */
package dev.antigravity.fluidengine.ui.page

import android.os.Build
import android.os.SystemClock
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import kotlin.math.abs

/**
 * Il riconoscitore del trascinamento indietro, sull'host (mai sulla pagina che si muove).
 *
 * - **Bordo** (i primi [FluidPageMotion.EdgeZoneDp] dp del lato d'ingresso): decide nel passaggio
 *   `Initial`, prima dei figli. Con la navigazione a gesti quei tocchi li prende il sistema; conta
 *   coi 3 tasti.
 * - **Ovunque**: decide nel passaggio `Main`, dopo i figli. Se un figlio ha consumato (cursore,
 *   interruttore, una lista che scorre, un pager) il dito e' suo; il riconoscitore resta a guardare,
 *   e se uno scorrimento orizzontale arriva al suo inizio e offre il resto ([FluidPageNestedScroll])
 *   prende la pagina da li'.
 * - Il primo superamento della soglia contro l'uscita, o in verticale, abbandona.
 * - Preso il dito, consuma tutto nel passaggio `Initial`: i figli vedono il gesto consumato e lasciano.
 */
internal fun Modifier.fluidPageBackSwipe(state: FluidPageHostState): Modifier =
  pointerInput(state) {
    val slop = viewConfiguration.touchSlop
    val edgePx = FluidPageMotion.EdgeZoneDp.dp.toPx()
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      state.recordDown(down.position, size)
      val dir = state.layoutDirectionSign

      // Una pagina che sta tornando a posto si riprende al volo, dal punto in cui e'.
      if (state.canGrab()) {
        val startTravel = state.travelPx
        val token = state.beginDrag(state.direction)
        if (token != null) {
          down.consume()
          trackDrag(state, token, down.id, down.position.x, startTravel, state.direction, null)
          return@awaitEachGesture
        }
      }

      val mode = state.swipeMode() ?: return@awaitEachGesture
      if (mode == FluidBackSwipe.Off) return@awaitEachGesture
      val fromEdge = if (dir > 0) down.position.x else size.width - down.position.x
      val inEdge = fromEdge <= edgePx
      if (!inEdge && mode == FluidBackSwipe.EdgeOnly) return@awaitEachGesture
      val pass = if (inEdge) PointerEventPass.Initial else PointerEventPass.Main

      var watching = false
      var lastX = down.position.x
      try {
        while (true) {
          val event = awaitPointerEvent(if (watching) PointerEventPass.Main else pass)
          val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
          if (!change.pressed) return@awaitEachGesture

          if (watching) {
            val handed = state.takeHandoff()
            if (handed > 0f) {
              // Lo scorrimento e' arrivato al suo inizio: il resto del dito e' della pagina, che
              // parte gia' spostata di quel resto e da qui segue il dito pixel per pixel.
              val token = state.beginDrag(dir) ?: return@awaitEachGesture
              change.consume()
              trackDrag(state, token, down.id, lastX - handed * dir, 0f, dir, change)
              return@awaitEachGesture
            }
            lastX = change.position.x
            continue
          }

          if (!inEdge && change.isConsumed) {
            // Un figlio si e' preso il dito: si guarda, nel caso passi la mano.
            watching = true
            state.handoffWatcher = mode == FluidBackSwipe.Anywhere
            lastX = change.position.x
            continue
          }

          val dx = change.position.x - down.position.x
          val dy = change.position.y - down.position.y
          val abandoned = when {
            abs(dx) > slop && abs(dx) > abs(dy) -> {
              if (dx * dir > 0f) {
                val token = state.beginDrag(dir) ?: return@awaitEachGesture
                change.consume()
                trackDrag(state, token, down.id, down.position.x, 0f, dir, change)
                return@awaitEachGesture
              }
              true
            }
            abs(dy) > slop -> true
            else -> false
          }
          if (abandoned) {
            if (inEdge || mode != FluidBackSwipe.Anywhere) return@awaitEachGesture
            // Contro l'uscita o in verticale: non e' un back. Ma un pager o una riga che scorre
            // potrebbero tornare al loro inizio e passare la mano, quindi si resta a guardare.
            watching = true
            state.handoffWatcher = true
            lastX = change.position.x
          }
        }
      } finally {
        state.handoffWatcher = false
        state.takeHandoff()
      }
    }
  }

/**
 * Il dito tiene la pagina: corsa = dito − ancora, snap sincrono a ogni evento, velocita' misurata
 * e passata alla molla al rilascio. Tutto consumato in `Initial`.
 *
 * La velocita' si misura come la misura `draggable` (`addPointerInputChange`: anche i campioni
 * storici di un evento che ne raccoglie piu' d'uno), nelle coordinate dell'host, che non si muove.
 */
private suspend fun AwaitPointerEventScope.trackDrag(
  state: FluidPageHostState,
  token: Int,
  pointer: PointerId,
  anchorX: Float,
  startTravel: Float,
  dir: Int,
  first: PointerInputChange?,
) {
  val tracker = VelocityTracker()
  if (first != null) {
    tracker.addPointerInputChange(first)
    state.dragTo(token, FluidPageMotion.inAppTravel(first.position.x, anchorX, dir, startTravel))
  }
  var released = false
  try {
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val change = event.changes.firstOrNull { it.id == pointer } ?: break
      change.consume()
      if (!state.isDragging(token)) {
        // La pila e' cambiata sotto il dito: la pagina e' andata, il resto del gesto si beve.
        if (!change.pressed) {
          released = true
          return
        }
        continue
      }
      tracker.addPointerInputChange(change)
      if (!change.pressed) {
        released = true
        state.release(token, tracker.calculateVelocity().x * dir)
        return
      }
      state.dragTo(token, FluidPageMotion.inAppTravel(change.position.x, anchorX, dir, startTravel))
    }
  } finally {
    // Gesto interrotto (l'host esce dalla composizione, il puntatore sparisce): decide la distanza.
    if (!released) state.release(token, 0f)
  }
}

/**
 * Il passaggio di mano da uno scorrimento orizzontale: quando una riga che scorre (o un pager) e'
 * al suo inizio e il dito continua verso l'uscita, il resto che nessuno ha consumato va alla pagina.
 * Mentre la pagina e' del dito, il resto orizzontale si beve: niente stiramento di overscroll sotto
 * una pagina che si sposta, niente lancio della riga quando la pagina la lascia.
 */
internal class FluidPageNestedScroll(private val state: FluidPageHostState) : NestedScrollConnection {

  override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
    if (source != NestedScrollSource.UserInput) return Offset.Zero
    if (state.phase == FluidPageHostState.Phase.Dragging) return Offset(available.x, 0f)
    val towardExit = available.x * state.layoutDirectionSign
    if (towardExit > 0f && abs(available.x) > abs(available.y) && state.offerHandoff(towardExit)) {
      return Offset(available.x, 0f)
    }
    return Offset.Zero
  }

  override suspend fun onPreFling(available: Velocity): Velocity =
    if (state.phase == FluidPageHostState.Phase.Dragging) Velocity(available.x, 0f) else Velocity.Zero
}

/** L'informazione che l'host pubblica al dispatcher: nessuna, il back e' tutto quello che fa. */
internal object FluidPageBackInfo : NavigationEventInfo()

/**
 * Il back di sistema, dal dispatcher di navigationevent.
 *
 * Con la navigazione a gesti (Android 14+) arrivano inizio, avanzamenti e fine: la pagina segue
 * `touchX`, il dito vero, e il `progress` del sistema (smussato da una sua molla e scalato su una
 * sua distanza) serve solo a capire in anticipo un annullo. Coi 3 tasti, o fino ad Android 13, arriva
 * solo la fine: e' un tap-back, l'app toglie la pagina e la molla l'accompagna fuori.
 *
 * Registrato sullo stesso dispatcher dei `BackHandler` dell'app *prima* delle pagine: chi si
 * registra dopo (un modale, una selezione) passa davanti, come deve.
 */
internal class FluidPageBackHandler(private val state: FluidPageHostState) :
  NavigationEventHandler<FluidPageBackInfo>(FluidPageBackInfo, false) {

  private var token: Int? = null
  private var anchor = 0f
  private var direction = 1
  private var lastTouchX = 0f
  private var lastProgress = 0f
  private var cancelInferred = false
  private val tracker = VelocityTracker1D(isDataDifferential = false)

  override fun onBackStarted(event: NavigationEvent) {
    reset()
    val edge = when (event.swipeEdge) {
      NavigationEvent.EDGE_LEFT -> FluidSwipeEdge.Left
      NavigationEvent.EDGE_RIGHT -> FluidSwipeEdge.Right
      else -> FluidSwipeEdge.None
    }
    direction = FluidPageMotion.systemDirection(edge, state.layoutDirectionSign)
    val started = state.beginDrag(direction) ?: return
    token = started
    anchor = FluidPageMotion.systemAnchor(
      firstTouchX = event.touchX,
      edge = edge,
      hostLeft = state.hostLeftInWindow,
      hostRight = state.hostRightInWindow,
      windowWidth = state.windowWidth,
    )
    follow(started, event)
  }

  override fun onBackProgressed(event: NavigationEvent) {
    val current = token ?: return
    if (cancelInferred) {
      if (abs(event.touchX - lastTouchX) < FluidPageMotion.SystemStillPx) {
        lastProgress = event.progress
        return
      }
      // Il dito c'era ancora: si riprende la pagina da dove la molla l'aveva portata.
      val again = state.beginDrag(direction) ?: return
      token = again
      cancelInferred = false
      follow(again, event)
      return
    }
    if (FluidPageMotion.inferSystemCancel(lastTouchX, event.touchX, lastProgress, event.progress)) {
      cancelInferred = true
      lastProgress = event.progress
      state.release(current, 0f, decided = false)
      return
    }
    follow(current, event)
  }

  override fun onBackCompleted() {
    val current = token
    if (current == null) {
      state.requestBack()
    } else {
      val velocity = tracker.calculateVelocity() * direction
      if (cancelInferred) state.systemCommit(current, velocity) else state.release(current, velocity, decided = true)
    }
    reset()
  }

  override fun onBackCancelled() {
    val current = token
    if (current != null && !cancelInferred) {
      state.release(current, tracker.calculateVelocity() * direction, decided = false)
    }
    reset()
  }

  private fun follow(current: Int, event: NavigationEvent) {
    lastTouchX = event.touchX
    lastProgress = event.progress
    val time = if (event.frameTimeMillis > 0L) event.frameTimeMillis else SystemClock.uptimeMillis()
    tracker.addDataPoint(time, event.touchX)
    state.dragTo(
      current,
      FluidPageMotion.systemTravel(event.touchX, anchor, direction, state.exitTravel()),
    )
  }

  private fun reset() {
    token = null
    cancelInferred = false
    tracker.resetTracking()
  }
}

/** I raggi degli angoli del display (API 31+), 0 dove non ci sono o prima. */
internal fun displayCornerRadii(view: View): FluidCornerRadii =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) displayCornerRadii31(view) else FluidCornerRadii.Zero

@RequiresApi(Build.VERSION_CODES.S)
private fun displayCornerRadii31(view: View): FluidCornerRadii {
  val insets = view.rootWindowInsets ?: return FluidCornerRadii.Zero
  fun radius(position: Int): Float = insets.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
  return FluidCornerRadii(
    topLeft = radius(RoundedCorner.POSITION_TOP_LEFT),
    topRight = radius(RoundedCorner.POSITION_TOP_RIGHT),
    bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
    bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT),
  )
}
