package dev.antigravity.fluidengine.sample.material

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier

/**
 * Un dito finto: eventi di tocco veri (`MotionEvent`, dito, schermo a sfioramento), consegnati alla vista della
 * composizione come li consegna il sistema.
 *
 * Esiste perché la pressione si fotografa sul componente vero: la pagina «Misure» guida `pressed` di una
 * `glassSurface` a mano, ma un tasto di vetro cresce, si illumina e porta il bagliore sotto il dito per i suoi
 * gesti, e solo un tocco li fa partire tutti. Il dito resta dentro l'app: niente permessi, niente `adb input`, e
 * la sessione lo lascia girare mentre registra.
 *
 * Le coordinate sono quelle della radice della composizione ([LayoutCoordinates.boundsInRoot]), che per la vista
 * della composizione sono le sue.
 */
@Stable
internal class MaterialFinger(private val view: View) {

  private var downTime = 0L

  /** Vero mentre il dito è giù. */
  var isDown: Boolean = false
    private set

  fun down(at: Offset) {
    if (isDown) up(at)
    downTime = SystemClock.uptimeMillis()
    isDown = true
    send(MotionEvent.ACTION_DOWN, at)
  }

  fun move(to: Offset) {
    if (isDown) send(MotionEvent.ACTION_MOVE, to)
  }

  fun up(at: Offset) {
    if (!isDown) return
    send(MotionEvent.ACTION_UP, at)
    isDown = false
  }

  /** Il dito se ne va senza un rilascio: il gesto si annulla (la scena che finisce a metà pressione). */
  fun cancel(at: Offset) {
    if (!isDown) return
    val points = if (fingers.size > 1) fingers else listOf(at)
    send(MotionEvent.ACTION_CANCEL, points)
    isDown = false
    fingers = emptyList()
  }

  /** Dove stanno le dita mentre sono giù in più d'una ([downAll]); vuota con un dito solo. */
  private var fingers: List<Offset> = emptyList()

  /**
   * Più dita che scendono **insieme**, una per punto di [points] (giro di correzione di F1A: due controlli premuti
   * nello stesso momento, ognuno col suo bagliore). Come le consegna il sistema: la prima con `ACTION_DOWN`, le altre
   * con `ACTION_POINTER_DOWN`, ognuna col suo `id`.
   */
  fun downAll(points: List<Offset>) {
    if (isDown) cancel(points.first())
    downTime = SystemClock.uptimeMillis()
    isDown = true
    send(MotionEvent.ACTION_DOWN, points.take(1))
    for (i in 1 until points.size) {
      send(MotionEvent.ACTION_POINTER_DOWN or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), points.take(i + 1))
    }
    fingers = points
  }

  /** Le dita di [downAll] si spostano tutte insieme. */
  fun moveAll(points: List<Offset>) {
    if (!isDown) return
    send(MotionEvent.ACTION_MOVE, points)
    fingers = points
  }

  /** Le dita di [downAll] si alzano, dall'ultima alla prima. */
  fun upAll(points: List<Offset>) {
    if (!isDown) return
    for (i in points.size - 1 downTo 1) {
      send(MotionEvent.ACTION_POINTER_UP or (i shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), points.take(i + 1))
    }
    send(MotionEvent.ACTION_UP, points.take(1))
    isDown = false
    fingers = emptyList()
  }

  private fun send(action: Int, at: Offset) = send(action, listOf(at))

  private fun send(action: Int, points: List<Offset>) {
    val properties = Array(points.size) { index ->
      MotionEvent.PointerProperties().apply {
        id = index
        toolType = MotionEvent.TOOL_TYPE_FINGER
      }
    }
    val coords = Array(points.size) { index ->
      MotionEvent.PointerCoords().apply {
        x = points[index].x
        y = points[index].y
        pressure = 1f
        size = 1f
      }
    }
    val event = MotionEvent.obtain(
      downTime, SystemClock.uptimeMillis(), action, points.size, properties, coords,
      0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
    )
    try {
      view.dispatchTouchEvent(event)
    } finally {
      event.recycle()
    }
  }
}

/** Dove sta un bersaglio del dito finto, in coordinate della radice: lo scrive [trackBounds]. */
@Stable
internal class MaterialTarget {
  var bounds: Rect? = null
    internal set
}

/** Tiene aggiornati i limiti del nodo in [target]. */
internal fun Modifier.trackBounds(target: MaterialTarget): Modifier =
  onGloballyPositioned { target.bounds = it.boundsInRoot() }
