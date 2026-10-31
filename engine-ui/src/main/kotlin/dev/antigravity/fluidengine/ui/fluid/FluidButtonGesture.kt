/*
 * Il gesto dei pulsanti (3.0) e' quello di LibrePods.
 *
 * Il ciclo del puntatore — la pressione che parte al contatto, la somma degli spostamenti che segue
 * il dito mentre la pagina fa quel che vuole, la fine che e' sempre la stessa per un rilascio e per
 * un annullo — e' portato dal codice di LibrePods, **GPL-3.0**:
 *
 *   me.kavishdevar.librepods.presentation.components.primitives
 *     StyledButton.kt      (righe 326-389: il `pointerInput` e `inspectDragGestures`)
 *     StyledIconButton.kt  (righe 194-244: lo stesso, per il tasto tondo)
 *   me.kavishdevar.librepods.utils.DragUtils.kt   (`inspectDragGestures`, identico a quello di Kyant,
 *     Apache-2.0, `DragGestureInspector.kt`: da noi in `GlassInteraction.kt`)
 *   https://github.com/librepods-org/librepods, branch `android/rewrite`, commit 3d4d270.
 *
 * Licenza: GPL-3.0, https://www.gnu.org/licenses/gpl-3.0.html; il testo e la lista di quello che
 * l'engine ha preso sono in LICENSES/LibrePods.md. Chi distribuisce un'app con questo file la
 * distribuisce sotto GPL-3.0.
 *
 * **Cosa e' uguale.** Il gesto parte al *down*, senza soglia e senza ritardo, e non consuma mai
 * niente: se il dito scorre, la pagina scorre e il pulsante risponde insieme — un `verticalScroll` o
 * una `LazyColumn` attorno non lo staccano dal dito, che resta premuto e lo segue fino al
 * sollevamento. Il click non e' nostro: e' `clickable`, come in LibrePods, che scatta se il dito si
 * solleva sul pulsante e nessuno ha consumato — quindi non scatta se la pagina ha scorso. La fine di
 * un gesto, rilascio o annullo, e' una sola.
 *
 * **Cosa abbiamo cambiato.**
 *   - le vibrazioni sono il vocabolario dell'engine (`PressDown`, `PressRelease`, `PressClick` e la
 *     grana), con un interruttore per il controllo che vibra gia' per conto suo;
 *   - il click lungo, che LibrePods non ha e i nostri menu si'. Scatta se il dito e' rimasto entro lo
 *     slop per il tempo del click lungo; lo slop e' misurato sulla strada percorsa dal dito sullo
 *     schermo, non sulla sua posizione sul pulsante, cosi' una pagina che scorre non lo inganna.
 *     Dopo un click lungo il rilascio e' consumato: `clickable` non clicca;
 *   - il mouse col tasto destro non preme il pulsante, e la rotella non e' un movimento del dito;
 *   - un down gia' consumato da un discendente non e' del pulsante;
 *   - il rilascio, se il gesto finisce perche' il pulsante esce dalla composizione (un menu che lo
 *     sostituisce), si scioglie senza vibrare;
 *   - il gesto non segue un secondo dito: finisce quando il primo si solleva.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import kotlin.math.max

/**
 * Il click lungo di un pulsante, deciso solo da tempo e movimento.
 *
 * Pura e senza Compose attorno: sbagliata, si vede solo nella mano e mai in una revisione, quindi
 * ogni regola qui sotto e' provata in `FluidPressTest`. Chi la guida ([fluidButtonGesture]) le dice
 * cosa succede — l'ora di un evento, quanta strada ha fatto il dito — e lei dice se e' scattato.
 *
 * Scatta a [longPressMillis] dal contatto, **una volta sola**, se il dito non ha superato lo slop
 * dal punto in cui e' sceso (un dito che trascina non sta tenendo premuto). Senza click lungo
 * ([NoLongPress]) non scatta mai e non ha scadenze.
 */
internal class FluidPressGesture(
  downUptime: Long,
  private val touchSlop: Float,
  longPressMillis: Long = NoLongPress,
) {
  /** La pressione lunga e' gia' scattata: al rilascio non c'e' click. */
  var longPressed: Boolean = false
    private set

  private val longPressAt: Long =
    if (longPressMillis == NoLongPress) Long.MAX_VALUE else downUptime + longPressMillis
  private var armed: Boolean = longPressMillis != NoLongPress

  /**
   * Il prossimo istante in cui il tempo, da solo, cambia qualcosa — [Long.MAX_VALUE] se nessuno.
   * E' quello che serve a un dito fermo: non arriva nessun evento, e il click lungo deve scattare
   * lo stesso.
   */
  val nextDeadline: Long
    get() = if (armed && !longPressed) longPressAt else Long.MAX_VALUE

  /** Il tempo e' arrivato a [uptime]: vero se il click lungo scatta adesso. */
  fun advance(uptime: Long): Boolean {
    if (!armed || longPressed || uptime < longPressAt) return false
    longPressed = true
    return true
  }

  /**
   * Il dito ha percorso [travel] dal contatto (somma degli spostamenti sullo schermo). Oltre lo slop
   * non e' piu' una pressione lunga, e tornare al punto di partenza non la riarma.
   */
  fun moved(travel: Offset) {
    if (armed && travel.getDistance() > touchSlop) armed = false
  }

  companion object {
    /** Nessuna pressione lunga: il tasto ha solo il click. */
    const val NoLongPress: Long = Long.MAX_VALUE
  }
}

/**
 * Il gesto di un pulsante, dal dito che scende al rilascio: la pressione, il seguire il dito, le
 * vibrazioni, il click lungo. Il click vero lo fa `clickable`, che sta *fuori* da questo modificatore
 * (`fluidPressable` li mette in quest'ordine): in ogni passaggio Main il puntatore arriva prima al
 * nodo piu' interno, quindi il rilascio passa di qui — `PressRelease` — e poi dal click —
 * `PressClick` —, ed e' l'ordine di LibrePods.
 *
 *  * **Al contatto**: la pressione parte, il bagliore si accende dov'e' sceso il dito e suona
 *    [FluidHapticEvent.PressDown]. Nessun ritardo, nessuna soglia, in una lista o fuori.
 *  * **Non consuma mai niente**, salvo il rilascio dopo un click lungo (vedi sotto). La pagina
 *    scorre se vuole, e il pulsante continua a seguire il dito e a vibrare mentre scorre.
 *  * **Seguire il dito**: ogni evento con uno spostamento somma la strada percorsa, vedi
 *    [FluidPressState.touchMove]. E' la somma degli spostamenti sullo schermo, non la posizione sul
 *    pulsante.
 *  * **Al rilascio o all'annullo** — sempre, anche per un tap che poi clicca, come `Reject` di
 *    LibrePods — la pressione si scioglie e suona [FluidHapticEvent.PressRelease]. Non suona dopo un
 *    click lungo: quel momento ha gia' il suo colpo.
 *  * **Click lungo**: `FluidPressGesture`. Dopo, il rilascio e' consumato, cosi' che `clickable`
 *    veda un cambio consumato e non clicchi.
 *  * Un cambio gia' consumato da un discendente finisce il gesto, come in LibrePods.
 *
 * Il tempo viene dall'ora degli eventi ([androidx.compose.ui.input.pointer.PointerInputChange.uptimeMillis])
 * e, per il dito fermo che non ne produce, da un timer sul prossimo scadere
 * ([FluidPressGesture.nextDeadline]).
 *
 * @param playsHaptics falso per un controllo che ha detto di vibrare gia' per conto suo
 *   (`fluidPressable(haptic = null)`): niente contatto, niente rilascio, niente grana.
 * @param onLongClick null se il pulsante non ha un click lungo.
 */
internal fun Modifier.fluidButtonGesture(
  state: FluidPressState,
  playsHaptics: Boolean,
  onLongClick: (() -> Unit)?,
): Modifier = this.pointerInput(state, playsHaptics, onLongClick != null) {
  val longPressMillis =
    if (onLongClick != null) viewConfiguration.longPressTimeoutMillis else FluidPressGesture.NoLongPress

  awaitEachGesture {
    // Un down gia' consumato da un discendente (un interruttore, un altro pulsante dentro questo) e'
    // suo: LibrePods non ha pulsanti dentro pulsanti e legge anche quello, ma qui non e' del tasto.
    val down = awaitFirstDown(requireUnconsumed = true)
    // Il tasto destro di un mouse non preme il pulsante: resta com'era, a `clickable`.
    if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
    val id = down.id
    val gesture = FluidPressGesture(
      downUptime = down.uptimeMillis,
      touchSlop = viewConfiguration.touchSlop,
      longPressMillis = longPressMillis,
    )

    state.touchDown(down.position)
    state.press()
    if (playsHaptics) state.haptics.play(FluidHapticEvent.PressDown)

    var finished = false
    fun finish(haptic: Boolean) {
      if (finished) return
      finished = true
      state.touchEnd()
      state.release()
      if (haptic && playsHaptics && !gesture.longPressed) state.haptics.play(FluidHapticEvent.PressRelease)
    }

    var lastUptime = down.uptimeMillis
    var travel = Offset.Zero
    try {
      while (true) {
        val deadline = gesture.nextDeadline
        val event = if (deadline == Long.MAX_VALUE) {
          awaitPointerEvent()
        } else {
          withTimeoutOrNull(deadline - lastUptime) { awaitPointerEvent() }
        }
        if (event == null) {
          // Nessun evento per tutto il tempo che serviva: e' il dito fermo, ed e' proprio il
          // caso in cui il tempo decide da solo.
          lastUptime = deadline
          if (gesture.advance(deadline)) onLongClick?.invoke()
          continue
        }

        // Il puntatore e' sparito senza un rilascio: e' un annullo, e finisce uguale.
        val change = event.changes.fastFirstOrNull { it.id == id } ?: break
        lastUptime = max(lastUptime, change.uptimeMillis)
        if (gesture.advance(lastUptime)) onLongClick?.invoke()

        if (!change.pressed) {
          // `clickable` clicca solo su un rilascio non consumato: dopo un click lungo questo non
          // deve essere un click, e consumarlo e' il modo che ha `clickable` di saperlo.
          if (gesture.longPressed) change.consume()
          break
        }
        // Un discendente se l'e' preso: il gesto e' suo, e finisce qui, come `drag` in LibrePods.
        if (change.isConsumed) break
        // La rotella di un mouse non e' un movimento del dito.
        if (event.type == PointerEventType.Scroll) continue

        if (change.position != change.previousPosition) {
          val moved = change.positionChange()
          travel += moved
          gesture.moved(travel)
          state.touchMove(moved, change.uptimeMillis, density, grainEnabled = playsHaptics)
        }
      }
      finish(haptic = true)
    } finally {
      // Uscita per cancellazione della coroutine (il pulsante lascia la composizione): la pressione
      // si scioglie, ma niente colpo — nessuno ha sollevato il dito.
      finish(haptic = false)
    }
  }
}

/**
 * La tastiera, per un pulsante che cresce: Invio, Spazio e il centro del D-pad lo premono finche'
 * restano giu'. Il click lo fa `clickable`; qui c'e' solo la pressione, che sul puntatore decide
 * [fluidButtonGesture].
 *
 * Messo *fuori* da `clickable`, e in anteprima (genitori prima dei figli): il fuoco sta sul nodo di
 * `clickable`, e un tasto che lui gestisce arriverebbe qui gia' consumato. Non consuma niente.
 */
internal fun Modifier.fluidButtonKeys(state: FluidPressState): Modifier = this.onPreviewKeyEvent { event ->
  if (event.key.isFluidPressKey()) {
    when (event.type) {
      KeyEventType.KeyDown -> state.press()
      KeyEventType.KeyUp -> state.release()
      else -> Unit
    }
  }
  false
}

private fun Key.isFluidPressKey(): Boolean =
  this == Key.Enter || this == Key.NumPadEnter || this == Key.DirectionCenter || this == Key.Spacebar
