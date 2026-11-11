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
    send(MotionEvent.ACTION_CANCEL, at)
    isDown = false
  }

  private fun send(action: Int, at: Offset) {
    val properties = arrayOf(
      MotionEvent.PointerProperties().apply {
        id = 0
        toolType = MotionEvent.TOOL_TYPE_FINGER
      },
    )
    val coords = arrayOf(
      MotionEvent.PointerCoords().apply {
        x = at.x
        y = at.y
        pressure = 1f
        size = 1f
      },
    )
    val event = MotionEvent.obtain(
      downTime, SystemClock.uptimeMillis(), action, 1, properties, coords,
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
