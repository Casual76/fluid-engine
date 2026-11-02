/*
 * Il gesto dei pulsanti (3.0) e' quello di LibrePods, con una differenza: il pulsante tiene il dito.
 *
 * Il ciclo del puntatore — la pressione che parte al contatto, la somma degli spostamenti che segue
 * il dito, la fine che e' sempre la stessa per un rilascio e per un annullo — e' portato dal codice
 * di LibrePods, **GPL-3.0**:
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
 * **Cosa e' uguale.** Il gesto parte al *down*, senza soglia e senza ritardo; il pulsante cresce e
 * segue il dito con la somma dei suoi spostamenti, e vibra, finche' il dito e' giu'. La fine di un
 * gesto, rilascio o annullo, e' una sola. Il click, per un tocco che non si muove, e' `clickable`,
 * come in LibrePods.
 *
 * **Cosa abbiamo cambiato.**
 *   - **il pulsante tiene il dito** (3.0, dopo la prova in mano). In LibrePods il tasto non consuma
 *     niente e la pagina scorre sotto un dito che e' partito da lui; qui il dito che scende su un
 *     pulsante e' del pulsante fino al sollevamento — come nella 2.12, ma senza l'attesa — e la
 *     pagina (`verticalScroll`, `LazyColumn`, un pager, il back a scorrimento) non parte. Per
 *     scorrere si parte fuori dai pulsanti. Il consumo e' descritto sotto, in [fluidButtonGesture];
 *   - **il click dopo un trascinamento e' del gesto**: `clickable` annulla alla vista di un cambio
 *     consumato, e il pulsante che tiene il dito ne consuma; quindi se il dito ha trascinato, e si
 *     solleva sul pulsante (piu' un margine), clicca il gesto stesso, con lo stesso `PressClick` di
 *     un tocco. Un tocco che non si muove resta di `clickable`;
 *   - [FluidPressGrip.YieldsToScroll], per i pulsanti che stanno in una fila che scorre (i chip):
 *     appena un antenato prende il gesto il pulsante si scioglie, senza click, come in iOS;
 *   - le vibrazioni sono il vocabolario dell'engine (`PressDown`, `PressRelease`, `PressClick` e la
 *     grana), con un interruttore per il controllo che vibra gia' per conto suo;
 *   - il click lungo, che LibrePods non ha e i nostri menu si'. Scatta se il dito e' rimasto entro lo
 *     slop per il tempo del click lungo; lo slop e' misurato sulla strada percorsa dal dito sullo
 *     schermo, non sulla sua posizione sul pulsante. Dopo un click lungo il rilascio e' consumato:
 *     `clickable` non clicca;
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import kotlin.math.max

/**
 * Di chi e' il dito che e' sceso su un pulsante, quando una pagina che lo contiene vuole scorrere.
 *
 * E' una scelta di chi costruisce il pulsante, non un'impostazione: dipende da dove il pulsante sta.
 * Un pulsante da solo, in una pagina che scorre, **tiene il dito** — per scorrere si parte fuori da
 * lui, e c'e' sempre un fuori. Un pulsante in una fila che scorre a sua volta (i chip di un filtro,
 * i pallini di un selettore) non ha un fuori da cui partire, e il dito che parte su un chip deve poter
 * far scorrere la fila: li' il pulsante **cede**.
 */
enum class FluidPressGrip {
  /**
   * Il dito che scende sul pulsante e' suo fino al sollevamento (di serie, per ogni pulsante).
   *
   * Il pulsante cresce, segue il dito e vibra come sempre, e **consuma** ogni spostamento del dito
   * appena questo e' piu' di un tremito, prima che qualunque antenato lo legga: la pagina sotto — un
   * `verticalScroll`, una `LazyColumn`, un pager, il back a scorrimento — non parte. Al sollevamento
   * il click scatta se il dito e' sul pulsante piu' un margine di 16 dp, anche dopo un trascinamento;
   * oltre il margine il pulsante si scioglie e basta. E' la regola della 2.12, senza i 100 ms di
   * attesa: premuto al contatto, come in LibrePods.
   */
  HoldsFinger,

  /**
   * Il pulsante cede il dito alla pagina: non consuma niente, e appena un antenato prende il gesto
   * — la fila comincia a scorrere — il pulsante si scioglie (cala, smette di seguire il dito, suona
   * `PressRelease`) e **non clicca**. E' il comportamento dei pulsanti in iOS dentro uno scorrevole.
   * Finche' nessuno prende il gesto, il pulsante risponde come sempre; il click e' di `clickable`.
   */
  YieldsToScroll,
}

/**
 * Il click lungo di un pulsante e il possesso del dito, decisi solo da tempo e movimento.
 *
 * Pura e senza Compose attorno: sbagliata, si vede solo nella mano e mai in una revisione, quindi
 * ogni regola qui sotto e' provata in `FluidPressTest`. Chi la guida ([fluidButtonGesture]) le dice
 * cosa succede — l'ora di un evento, quanta strada ha fatto il dito, dove si e' sollevato — e lei
 * dice se e' scattato il click lungo, se il dito va consumato, se il rilascio e' un click.
 *
 * **Click lungo.** Scatta a `longPressMillis` dal contatto, **una volta sola**, se il dito non ha
 * superato lo slop dal punto in cui e' sceso (un dito che trascina non sta tenendo premuto). Senza
 * click lungo ([NoLongPress]) non scatta mai e non ha scadenze.
 *
 * **Possesso** (solo [FluidPressGrip.HoldsFinger]). Il gesto *prende* il dito, cioe' comincia a
 * consumare, quando la strada percorsa supera `claimDistance` — meta' dello slop, di serie — o
 * quando il dito esce dall'area in cui `clickable` conta ancora un click. Da quel momento `clickable`
 * ha annullato (vede un cambio consumato) e il click, se c'e', e' del gesto: si decide al rilascio,
 * in [clicks]. Il tremito di un tocco vero sta sotto la soglia, e li' il consumo non parte: il click
 * resta di `clickable` e un tocco e' esattamente quello di LibrePods.
 *
 * Perche' meta' dello slop e non lo slop: le pagine che scorrono usano lo slop del sistema, e
 * decidono se il dito e' di un trascinamento dopo averlo letto; il pulsante, che sta sotto di loro
 * nel passaggio Main, consuma prima, quindi non puo' arrivare dopo. Per un mouse la soglia e' zero: non
 * ha un tremito da lasciar passare, e il suo slop nelle pagine che scorrono puo' essere piu' piccolo di
 * quello del dito.
 */
internal class FluidPressGesture(
  downUptime: Long,
  private val touchSlop: Float,
  longPressMillis: Long = NoLongPress,
  private val grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  private val claimDistance: Float = touchSlop * ClaimFraction,
) {
  /** La pressione lunga e' gia' scattata: al rilascio non c'e' click. */
  var longPressed: Boolean = false
    private set

  /**
   * Il gesto ha preso il dito: da qui ogni cambio del puntatore si consuma, `clickable` e' fuori e
   * il click e' del gesto. Mai vero per [FluidPressGrip.YieldsToScroll].
   */
  var claimed: Boolean = false
    private set

  private var path: Float = 0f

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

  /**
   * Il dito si e' spostato di [step]: il gesto lo prende? Vero da quando la strada percorsa (la somma
   * delle lunghezze, non lo spostamento: un dito che va e torna conta due volte) supera
   * `claimDistance`, o il dito e' fuori da [insideClickable] — e in quel caso `clickable` annulla da
   * solo, e il click deve passare al gesto. Una volta vero resta vero fino alla fine del gesto.
   *
   * Sempre falso per [FluidPressGrip.YieldsToScroll].
   */
  fun claim(step: Offset, insideClickable: Boolean): Boolean {
    if (grip != FluidPressGrip.HoldsFinger) return false
    if (!claimed) {
      path += step.getDistance()
      if (path > claimDistance || !insideClickable) claimed = true
    }
    return claimed
  }

  /**
   * Il dito si e' sollevato in [position]: e' un click del gesto? Si', se il pulsante tiene il dito,
   * la pressione lunga non e' gia' scattata, **`clickable` non cliccherebbe** (il gesto ha preso il
   * dito, o il rilascio cade fuori dall'area di `clickable`) e il dito e' dentro il pulsante piu'
   * [slack]. Anche un dito che ha viaggiato lontano e torna: conta solo dove si solleva.
   *
   * Il caso opposto — un tocco che non si e' mai mosso oltre il tremito — non e' un click del gesto
   * perche' e' di `clickable`: due sorgenti per lo stesso tocco sono due click.
   *
   * @param size il pulsante.
   * @param clickableSlack quanto `clickable` allarga la sua area (il tocco minimo di Compose).
   * @param slack il margine del click del gesto, vedi [fluidPressSlack].
   */
  fun clicks(position: Offset, size: Size, clickableSlack: Size, slack: Size): Boolean =
    grip == FluidPressGrip.HoldsFinger && !longPressed &&
      (claimed || !fluidPressInside(position, size, clickableSlack)) &&
      fluidPressInside(position, size, slack)

  companion object {
    /** Nessuna pressione lunga: il tasto ha solo il click. */
    const val NoLongPress: Long = Long.MAX_VALUE

    /** La frazione dello slop del sistema oltre la quale il gesto prende il dito: vedi la classe. */
    const val ClaimFraction: Float = 0.5f
  }
}

/**
 * Il margine oltre il bordo in cui un dito sollevato conta ancora come un click del gesto.
 *
 * 16 dp, o il margine che Compose da' a un elemento piu' piccolo del suo tocco minimo se e' di piu'
 * (la sua area di tocco: un'icona di 24 dp ne ha 12 per lato). Piccolo di proposito: un pulsante che
 * segue il dito fuori dai suoi bordi e' un pulsante che si puo' annullare, e il gesto per farlo e'
 * andarsene. Ma non a filo: un pollice che ha trascinato un tasto di 48 dp non si solleva al pixel.
 * Era il margine della 2.12, e non e' cambiato.
 */
internal val FluidPressClickSlack: Dp = 16.dp

/** Il margine di [fluidPressSlack]: [extraPx] oltre l'area di tocco minima, ~16 dp, come si dice li'. */
internal fun fluidPressSlack(minTouchTargetPadding: Size, extraPx: Float): Size =
  Size(max(minTouchTargetPadding.width, extraPx), max(minTouchTargetPadding.height, extraPx))

/** Vero se [position] sta nel rettangolo [size] allargato di [slack] da ogni lato. */
internal fun fluidPressInside(position: Offset, size: Size, slack: Size): Boolean =
  position.x >= -slack.width && position.x <= size.width + slack.width &&
    position.y >= -slack.height && position.y <= size.height + slack.height

/**
 * Il gesto di un pulsante, dal dito che scende al rilascio: la pressione, il seguire il dito, le
 * vibrazioni, il click lungo, il possesso del dito. Il click di un tocco lo fa `clickable`, che sta
 * *fuori* da questo modificatore (`fluidPressable` li mette in quest'ordine): in ogni passaggio Main
 * il puntatore arriva prima al nodo piu' interno, quindi il rilascio passa di qui — `PressRelease` —
 * e poi dal click — `PressClick` —, ed e' l'ordine di LibrePods.
 *
 *  * **Al contatto**: la pressione parte, il bagliore si accende dov'e' sceso il dito e suona
 *    [FluidHapticEvent.PressDown]. Nessun ritardo, nessuna soglia, in una lista o fuori. Il down non
 *    si consuma: `clickable` lo vede, e un tocco e' suo.
 *  * **Il dito e' del pulsante** ([FluidPressGrip.HoldsFinger]). Quando il dito si e' mosso oltre un
 *    tremito (vedi [FluidPressGesture]) il gesto consuma ogni cambio, **nel passaggio Main**. Compose
 *    manda il Main dal nodo piu' profondo al piu' alto, quindi il pulsante lo legge prima di qualunque
 *    antenato: uno `scrollable`, un `draggable`, il riconoscitore del back guardano se il cambio e'
 *    consumato *prima* di decidere che il dito ha superato lo slop, e vedendolo consumato rinunciano.
 *    La pagina non parte, e non parte nemmeno se il pulsante sta dentro un'altra pagina che scorre.
 *  * **Il click dopo un trascinamento.** `clickable` cancella un click se vede un cambio consumato, e
 *    il gesto ne ha consumati. Al sollevamento, quindi, se il gesto ha preso il dito (o il dito e'
 *    uscito dall'area di `clickable`) e si solleva dentro il pulsante piu' [FluidPressClickSlack],
 *    clicca il gesto: `PressRelease`, poi il click del pulsante (`PressClick` e `onClick`), e il
 *    rilascio e' consumato come fa `clickable`. Fuori dal margine, `PressRelease` e basta. Un tocco
 *    che non si muove non passa di qui: e' di `clickable`, che e' piu' esterno e legge dopo.
 *  * **Seguire il dito**: ogni evento con uno spostamento somma la strada percorsa, vedi
 *    [FluidPressState.touchMove]. E' la somma degli spostamenti sullo schermo, non la posizione sul
 *    pulsante.
 *  * **Al rilascio o all'annullo** — sempre, anche per un tap che poi clicca, come `Reject` di
 *    LibrePods — la pressione si scioglie e suona [FluidHapticEvent.PressRelease]. Non suona dopo un
 *    click lungo: quel momento ha gia' il suo colpo.
 *  * **Click lungo**: [FluidPressGesture]. Dopo, il rilascio e' consumato, cosi' che `clickable`
 *    veda un cambio consumato e non clicchi.
 *  * Un cambio gia' consumato da un discendente, o da un antenato nell'Initial (il relè di un menu
 *    che ha preso il dito), finisce il gesto.
 *  * **[FluidPressGrip.YieldsToScroll]**: niente consumo. Dopo ogni spostamento si guarda anche il
 *    passaggio Final, che arriva per ultimo, cioe' dopo che gli antenati hanno deciso: un cambio
 *    consumato li' e' una pagina che e' partita, e il pulsante si scioglie con `PressRelease`, senza
 *    click (`clickable` ha annullato per lo stesso motivo).
 *
 * Il tempo viene dall'ora degli eventi ([androidx.compose.ui.input.pointer.PointerInputChange.uptimeMillis])
 * e, per il dito fermo che non ne produce, da un timer sul prossimo scadere
 * ([FluidPressGesture.nextDeadline]).
 *
 * @param playsHaptics falso per un controllo che ha detto di vibrare gia' per conto suo
 *   (`fluidPressable(haptic = null)`): niente contatto, niente rilascio, niente grana.
 * @param onLongClick null se il pulsante non ha un click lungo.
 * @param onClick il click del pulsante (colpo `PressClick` compreso), per il rilascio di un dito che
 *   ha trascinato: lo stesso che `clickable` chiama per un tocco.
 * @param grip di chi e' il dito quando la pagina sotto vuole scorrere.
 */
internal fun Modifier.fluidButtonGesture(
  state: FluidPressState,
  playsHaptics: Boolean,
  onLongClick: (() -> Unit)?,
  onClick: () -> Unit,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
): Modifier = this.pointerInput(state, playsHaptics, onLongClick != null, grip) {
  val longPressMillis =
    if (onLongClick != null) viewConfiguration.longPressTimeoutMillis else FluidPressGesture.NoLongPress

  awaitEachGesture {
    // Un down gia' consumato da un discendente (un interruttore, un altro pulsante dentro questo) e'
    // suo: LibrePods non ha pulsanti dentro pulsanti e legge anche quello, ma qui non e' del tasto.
    val down = awaitFirstDown(requireUnconsumed = true)
    // Il tasto destro di un mouse non preme il pulsante: resta com'era, a `clickable`.
    if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
    val id = down.id
    val touchSlop = viewConfiguration.touchSlop
    val gesture = FluidPressGesture(
      downUptime = down.uptimeMillis,
      touchSlop = touchSlop,
      longPressMillis = longPressMillis,
      grip = grip,
      // Un mouse non trema, e il suo slop nelle pagine puo' essere piu' piccolo: prende il dito al primo passo.
      claimDistance = if (down.type == PointerType.Mouse) 0f else touchSlop * FluidPressGesture.ClaimFraction,
    )
    val slack = fluidPressSlack(extendedTouchPadding, FluidPressClickSlack.toPx())

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
    // Il click che il gesto fa per `clickable`, dopo il rilascio: vedi `FluidPressGesture.clicks`.
    var clicks = false
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
          if (gesture.longPressed) {
            // `clickable` clicca solo su un rilascio non consumato: dopo un click lungo questo non
            // deve essere un click, e consumarlo e' il modo che ha `clickable` di saperlo.
            change.consume()
          } else if (!change.isConsumed && gesture.clicks(change.position, size.toSize(), extendedTouchPadding, slack)) {
            // Il dito ha trascinato (e `clickable` ha annullato vedendo i nostri consumi) e si
            // solleva sul pulsante: il click e' nostro. Il rilascio si consuma come lo consumerebbe
            // `clickable`, cosi' un genitore cliccabile non lo legge come un suo tocco.
            change.consume()
            clicks = true
          }
          break
        }
        // Un discendente se l'e' preso, o un antenato nell'Initial: il gesto e' suo, e finisce qui.
        if (change.isConsumed) break
        // La rotella di un mouse non e' un movimento del dito.
        if (event.type == PointerEventType.Scroll) continue

        if (change.position != change.previousPosition) {
          val moved = change.positionChange()
          travel += moved
          gesture.moved(travel)
          state.touchMove(moved, change.uptimeMillis, density, grainEnabled = playsHaptics)
          gesture.claim(
            step = moved,
            insideClickable = fluidPressInside(change.position, size.toSize(), extendedTouchPadding),
          )
        }
        // Il dito e' del pulsante: la pagina, e `clickable`, vedono il cambio consumato. Nel Main,
        // prima di ogni antenato: per questo lo scorrimento non supera mai il suo slop.
        if (gesture.claimed) change.consume()

        if (grip == FluidPressGrip.YieldsToScroll) {
          // Il Final arriva per ultimo, dopo che la pagina ha deciso: un cambio consumato e' uno
          // scorrimento partito, e il pulsante lascia andare il dito.
          val after = awaitPointerEvent(PointerEventPass.Final)
          val seen = after.changes.fastFirstOrNull { it.id == id }
          if (seen == null || seen.isConsumed) break
        }
      }
      finish(haptic = true)
      if (clicks) onClick()
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
