/*
 * Il calendario in pagina (4.0, F8.8, `calendario-in-pagina`): la griglia del mese che prima viveva solo dentro
 * `FluidDateTimeSheet`, ora un componente da mettere nella pagina, e il foglio la usa. Ha i segni sotto i giorni, la
 * scelta di un giorno, di giorni sparsi o di un intervallo, il mese che cambia col dito e la striscia della settimana in
 * cui si stringe quando la lista sotto scorre. I dati sono in `FluidCalendarModel.kt`. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.ui.fluid

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.FluidDates
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronLeft
import dev.antigravity.fluidengine.ui.fluid.icons.ChevronRight
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Lo stato di un [FluidCalendar]: il mese che mostra, la striscia della settimana e il collegamento con la lista sotto.
 *
 * **La striscia della settimana** ([collapseProgress]) e' quella dell'app Calendario: quando la lista sotto il calendario
 * scorre in su, la griglia del mese si stringe fino alla sola settimana del giorno di riferimento, e torna intera quando
 * la lista, arrivata in cima, si tira giu'. Per averla basta dare alla lista [nestedScrollConnection]
 * (`Modifier.nestedScroll(state.nestedScrollConnection)`). Nella striscia lo scorrimento di lato cambia settimana.
 *
 * Il moto (la pagina che segue il dito e poi si posa, la striscia che si apre o si chiude) e' calcolato da
 * [FluidDetentInertia], senza allocazioni per fotogramma, e scritto in stati letti solo nel disegno e nella misura.
 */
@Stable
class FluidCalendarState(initialMonth: YearMonth, collapsed: Boolean = false) {
  /** Il mese mostrato; nella striscia, il mese del giorno di riferimento. */
  var month: YearMonth by mutableStateOf(initialMonth)
    private set

  /** Il giorno di riferimento: la riga della striscia e la settimana mostrata. */
  internal var focus: LocalDate by mutableStateOf(initialMonth.atDay(1))

  /** La riga del giorno di riferimento nel mese mostrato: la legge il disegno (la striscia), mai la composizione. */
  internal val focusRow: Int
    get() {
      val f = focus
      return if (YearMonth.from(f) != month) 0 else fluidCalendarRowOf(f, weekStart)
    }

  private val collapse = mutableFloatStateOf(if (collapsed) 1f else 0f)

  /** Quanto la griglia e' stretta: 0 il mese intero, 1 la striscia della settimana. */
  val collapseProgress: Float get() = collapse.floatValue

  private val strip = derivedStateOf { collapse.floatValue >= 1f }

  /** Vero quando si vede solo la striscia della settimana (derivato: cambia una volta, non a ogni fotogramma). */
  val isWeekStrip: Boolean get() = strip.value

  /**
   * Vero mentre al posto della griglia ci sono le ruote del mese e dell'anno: il titolo toccato, come in
   * `DatePicker(.graphical)`. Le ruote cambiano [month] mentre girano; il titolo toccato di nuovo torna alla griglia.
   */
  var isMonthYearPickerShown: Boolean by mutableStateOf(false)
    private set

  /** Apre ([show] vero) o chiude le ruote del mese e dell'anno. Si aprono solo dalla griglia intera, non dalla striscia. */
  fun showMonthYearPicker(show: Boolean) {
    if (show && collapse.floatValue > 0f) return
    if (show) {
      stopPage()
      page.floatValue = 0f
    }
    isMonthYearPickerShown = show
  }

  /** Vero quando il mese si puo' cambiare col dito: griglia intera o striscia chiusa, mai a meta'. */
  internal val canDrag = derivedStateOf { collapse.floatValue <= 0f || collapse.floatValue >= 1f }

  /** Dove sta la pagina: 0 a riposo, ±1 il mese (o la settimana) dopo e prima del tutto mostrati. */
  internal val page = mutableFloatStateOf(0f)

  /** Vero mentre la pagina si posa da sola: un dito che arriva la prende al volo. */
  internal var pageMoving by mutableStateOf(false)
    private set

  internal var scope: CoroutineScope? = null
  internal var widthPx = 1f
  internal var rowPx = 1f
  internal var reduced = false

  /** Falso col testo grande: c'e' solo il mese mostrato, e le frecce lo cambiano senza scorrere. */
  internal var pagingEnabled = true
  internal var rtl = false
  internal var minDate: LocalDate? = null
  internal var maxDate: LocalDate? = null
  internal var weekStart: DayOfWeek = DayOfWeek.MONDAY
  internal var focusFor: (YearMonth) -> LocalDate = { it.atDay(1) }

  internal val pageMotion = FluidDetentInertia()
  internal val collapseMotion = FluidDetentInertia()
  private var pageJob: Job? = null
  private var dragRaw = 0f
  private var pageLast = -1L
  private var collapseLast = -1L
  // Il passo di un fotogramma, senza allocazioni. `withFrameNanos` lo chiama attraverso una lambda: il `Long` del tempo
  // lo impacchetta Compose (lo stesso per ogni animazione dell'orologio dei fotogrammi), non questo codice.
  internal fun stepPage(nanos: Long) {
    val dt = if (pageLast < 0L) 0f else (nanos - pageLast) / 1_000_000_000f
    pageLast = nanos
    pageMotion.step(dt)
    page.floatValue = pageMotion.position
  }

  internal fun stepCollapse(nanos: Long) {
    val dt = if (collapseLast < 0L) 0f else (nanos - collapseLast) / 1_000_000_000f
    collapseLast = nanos
    collapseMotion.step(dt)
    collapse.floatValue = collapseMotion.position.coerceIn(0f, 1f)
  }

  private val pageFrame: (Long) -> Unit = { stepPage(it) }
  private val collapseFrame: (Long) -> Unit = { stepCollapse(it) }

  /** Mostra [month] subito, senza moto. */
  fun showMonth(month: YearMonth) {
    stopPage()
    page.floatValue = 0f
    this.month = month
    focus = focusFor(month)
  }

  /** Porta il calendario a [month]: scorrendo se e' quello dopo o quello prima, se no subito. */
  suspend fun animateToMonth(month: YearMonth) {
    val delta = (month.year - this.month.year) * 12 + (month.monthValue - this.month.monthValue)
    if (abs(delta) == 1 && !isWeekStrip && !reduced) animatePage(delta) else showMonth(month)
  }

  /** Chiude la griglia nella striscia della settimana. */
  suspend fun collapse() = animateCollapse(1f, 0f)

  /** Riapre la griglia del mese. */
  suspend fun expand() = animateCollapse(0f, 0f)

  /**
   * Da dare alla lista sotto il calendario: quando la lista scorre in su la griglia si stringe prima che la lista si
   * muova; quando la lista e' in cima e si tira giu', la griglia si riapre. A dito lasciato la striscia si posa aperta o
   * chiusa, mai a meta'.
   */
  val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      // Con le ruote aperte la griglia non c'e': non si stringe.
      if (isMonthYearPickerShown) return Offset.Zero
      if (available.y >= 0f || collapse.floatValue >= 1f || page.floatValue != 0f) return Offset.Zero
      return Offset(0f, collapseBy(available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      if (isMonthYearPickerShown) return Offset.Zero
      if (available.y <= 0f || collapse.floatValue <= 0f || page.floatValue != 0f) return Offset.Zero
      return Offset(0f, collapseBy(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
      val p = collapse.floatValue
      if (p <= 0f || p >= 1f) return Velocity.Zero
      settleCollapse(available.y)
      return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      val p = collapse.floatValue
      if (p > 0f && p < 1f) settleCollapse(available.y)
      return Velocity.Zero
    }
  }

  // --- La pagina ---------------------------------------------------------------------------------------------------

  /** Vero se c'e' un mese (o una settimana) nella direzione [dir] dentro i limiti. */
  internal fun canPage(dir: Int): Boolean {
    if (dir == 0) return true
    return if (isWeekStrip) {
      val start = weekStartOf(focus).plusWeeks(dir.toLong())
      val end = start.plusDays(6)
      (minDate == null || !end.isBefore(minDate)) && (maxDate == null || !start.isAfter(maxDate))
    } else {
      val target = month.plusMonths(dir.toLong())
      (minDate == null || !target.isBefore(YearMonth.from(minDate))) &&
        (maxDate == null || !target.isAfter(YearMonth.from(maxDate)))
    }
  }

  /** Quante pagine dista la pagina [anchor] da quella mostrata (−1, 0, 1): la legge solo il disegno. */
  internal fun pageOffsetOf(anchor: Long, strip: Boolean): Int = if (strip) {
    ((anchor - weekStartOf(focus).toEpochDay()) / 7L).toInt()
  } else {
    val m = month
    (anchor - (m.year * 12L + m.monthValue - 1)).toInt()
  }

  internal fun weekStartOf(date: LocalDate): LocalDate =
    date.minusDays(Math.floorMod(date.dayOfWeek.value - weekStart.value, 7).toLong())

  /** Un giorno toccato diventa quello di riferimento; nella striscia la testata dice il suo mese. */
  internal fun focusOn(date: LocalDate) {
    focus = date
    if (isWeekStrip) month = YearMonth.from(date)
  }

  /** Il dito ha preso la pagina: si ferma dov'e'. */
  internal fun catchPage() {
    stopPage()
    dragRaw = page.floatValue
  }

  /** Il dito si e' mosso di [deltaPx] (a destra positivo). */
  internal fun dragBy(deltaPx: Float) {
    val dir = if (rtl) -1f else 1f
    dragRaw -= deltaPx / widthPx * dir
    // Oltre una pagina intera la pagina dopo diventa quella mostrata: si puo' trascinare per piu' mesi di fila.
    while (dragRaw >= 1f && canPage(1)) { commit(1); dragRaw -= 1f }
    while (dragRaw <= -1f && canPage(-1)) { commit(-1); dragRaw += 1f }
    page.floatValue = when {
      dragRaw > 0f && !canPage(1) -> fluidCalendarRubber(dragRaw)
      dragRaw < 0f && !canPage(-1) -> -fluidCalendarRubber(-dragRaw)
      else -> dragRaw.coerceIn(-1f, 1f)
    }
  }

  /** Il dito ha lasciato la pagina con [velocityPx] px/s: si posa sul mese giusto, al piu' uno piu' in la'. */
  internal fun releasePage(velocityPx: Float) {
    val dir = if (rtl) -1f else 1f
    val p = page.floatValue
    val speed = -velocityPx / widthPx * dir
    val lower = if (canPage(-1)) -1f else 0f
    val upper = if (canPage(1)) 1f else 0f
    val landing = fluidDetentLanding(p, speed, reduced, lower, upper).coerceIn(-1f, 1f)
    if (reduced) {
      // «Riduci movimento»: la pagina va subito al suo posto, ferma e leggibile.
      val target = landing.roundToInt()
      if (target != 0) commit(target)
      page.floatValue = 0f
      return
    }
    // Una pagina non scivola come una lista: si posa con la molla corta, partendo con la velocita' del dito, senza
    // passare oltre il mese d'arrivo (dove non c'e' niente da vedere).
    pageMotion.springTo(p, fluidCalendarSettleSpeed(p, speed, landing, FluidCalendarDefaults.PageResponseSeconds), landing,
      widthPx, FluidCalendarDefaults.PageResponseSeconds)
    launchPage(landing.roundToInt())
  }

  /** Le frecce: un mese (o una settimana) avanti o indietro. */
  internal fun pageBy(dir: Int) {
    if (!canPage(dir)) return
    if (reduced || !pagingEnabled) {
      stopPage()
      commit(dir)
      page.floatValue = 0f
      return
    }
    stopPage()
    pageMotion.springTo(page.floatValue, 0f, dir.toFloat(), widthPx, FluidCalendarDefaults.PageResponseSeconds)
    launchPage(dir)
  }

  private suspend fun animatePage(dir: Int) {
    stopPage()
    pageMotion.springTo(page.floatValue, 0f, dir.toFloat(), widthPx, FluidCalendarDefaults.PageResponseSeconds)
    runPage(dir)
  }

  private fun launchPage(target: Int) {
    val s = scope
    if (s == null) {
      if (target != 0) commit(target)
      page.floatValue = 0f
      return
    }
    pageJob = s.launch { runPage(target) }
  }

  private suspend fun runPage(target: Int) {
    pageMoving = true
    pageLast = -1L
    try {
      while (pageMotion.moving) withFrameNanos(pageFrame)
      // Lo stesso fotogramma: la pagina arrivata diventa quella mostrata e torna a zero, senza un salto.
      if (target != 0) commit(target)
      page.floatValue = 0f
    } finally {
      pageMoving = false
    }
  }

  private fun stopPage() {
    pageJob?.cancel()
    pageJob = null
    pageMotion.stop()
    pageMoving = false
  }

  private fun commit(dir: Int) {
    if (isWeekStrip) {
      focus = focus.plusWeeks(dir.toLong())
      month = YearMonth.from(focus)
    } else {
      month = month.plusMonths(dir.toLong())
      focus = focusFor(month)
    }
    page.floatValue -= dir
  }

  // --- La striscia -------------------------------------------------------------------------------------------------

  private fun collapseDistance(): Float = rowPx * (FluidCalendarDefaults.Rows - 1)

  /** Stringe (dy negativo) o apre la griglia di [dyPx]; restituisce quanto ha preso. */
  private fun collapseBy(dyPx: Float): Float {
    val distance = collapseDistance()
    if (distance <= 0f) return 0f
    val before = collapse.floatValue
    val after = (before - dyPx / distance).coerceIn(0f, 1f)
    if (after == before) return 0f
    collapseMotion.stop()
    if (before >= 1f && after < 1f) month = YearMonth.from(focus)
    collapse.floatValue = after
    return -(after - before) * distance
  }

  private suspend fun settleCollapse(velocityY: Float) {
    val distance = collapseDistance()
    val p = collapse.floatValue
    val speed = if (distance > 0f) -velocityY / distance else 0f
    val target = fluidDetentLanding(p, speed, reduced, 0f, 1f)
    animateCollapse(target, speed)
  }

  private suspend fun animateCollapse(target: Float, speed: Float) {
    if (reduced || collapseDistance() <= 0f) {
      collapse.floatValue = target
      return
    }
    val from = collapse.floatValue
    collapseMotion.springTo(from, fluidCalendarSettleSpeed(from, speed, target, FluidCalendarDefaults.CollapseResponseSeconds),
      target, collapseDistance(), FluidCalendarDefaults.CollapseResponseSeconds)
    collapseLast = -1L
    while (collapseMotion.moving) withFrameNanos(collapseFrame)
    collapse.floatValue = target
  }

  companion object {
    /** Conserva mese, giorno di riferimento, striscia e ruote aperte fra una ricreazione e l'altra. */
    val Saver = listSaver<FluidCalendarState, String>(
      save = { listOf(it.month.toString(), it.focus.toString(), it.isWeekStrip.toString(), it.isMonthYearPickerShown.toString()) },
      restore = { saved ->
        FluidCalendarState(YearMonth.parse(saved[0]), saved[2].toBoolean()).apply {
          focus = LocalDate.parse(saved[1])
          isMonthYearPickerShown = saved.getOrNull(3)?.toBoolean() == true
        }
      },
    )
  }
}

/**
 * La velocita' con cui parte la molla critica da [from] verso [to]: quella del dito, ma non tanta da passare oltre
 * l'arrivo. Una molla critica `e(t) = (e0 + (v0 + w·e0)·t)·exp(−w·t)` non attraversa lo zero finche' `v0 + w·e0` ha il
 * segno di `e0`; la velocita' del dito che si allontana dall'arrivo si tiene com'e'.
 */
internal fun fluidCalendarSettleSpeed(from: Float, speed: Float, to: Float, responseSeconds: Double): Float {
  val e0 = from - to
  if (e0 == 0f) return 0f
  val w = (2.0 * Math.PI / responseSeconds).toFloat()
  val limit = w * abs(e0)
  // Verso l'arrivo la velocita' ha il segno opposto allo scarto.
  return if (speed * e0 < 0f) speed.coerceIn(-limit, limit) else speed
}

/** La resistenza oltre l'ultimo mese, quella di `UIScrollView`: `(1 − 1 / (x · 0,55 + 1))`, in pagine. */
internal fun fluidCalendarRubber(pages: Float): Float = 1f - 1f / (pages * 0.55f + 1f)

/** Uno stato del calendario che sopravvive alla ricreazione, aperto su [initialMonth]. */
@Composable
fun rememberFluidCalendarState(
  initialMonth: YearMonth = YearMonth.now(),
  collapsed: Boolean = false,
): FluidCalendarState = rememberSaveable(saver = FluidCalendarState.Saver) { FluidCalendarState(initialMonth, collapsed) }

/**
 * Il calendario del mese, da mettere nella pagina: `DatePicker` con lo stile `.graphical` di SwiftUI, `MultiDatePicker`
 * e le decorazioni di `UICalendarView`, con la striscia della settimana dell'app Calendario.
 *
 * - **La scelta** ([selection]): un giorno ([FluidDateSelection.Single]), giorni sparsi anche in mesi diversi
 *   ([FluidDateSelection.Multiple]) o un intervallo ([FluidDateSelection.Range], la fascia fra i due giorni). Il calendario
 *   non tiene la scelta: chiama [onSelectionChange] e mostra quella che riceve.
 * - **Il mese cambia col dito**, scorrendo di lato: la pagina segue il dito, nitida, e si posa sul mese giusto (al piu'
 *   uno piu' in la', come una pagina di `UIScrollView`), o con le frecce della testata. Fra i limiti [minDate] e
 *   [maxDate] non si esce: oltre, la pagina resiste.
 * - **I segni** ([marks]): pallini o un numero sotto i giorni, dentro l'altezza della riga. Un mese che arriva dalla rete
 *   non fa saltare la griglia e ricompone solo i giorni toccati; il segno che arriva entra sfocato (lo muove l'app).
 * - **Il primo giorno della settimana** viene da [locale] ([FluidDates.firstDayOfWeek]); i mesi hanno sempre sei righe.
 * - **Testo grande**: se sette celle da 48 dp per la scala del carattere non ci stanno, la griglia scorre di lato (la
 *   regola delle sette colonne di `FluidDateTimeSheet`) e il mese cambia con le frecce.
 * - **TalkBack** legge «25 novembre, 3 impegni», con «oggi» e la parte dell'intervallo; le frecce cambiano mese.
 * - **Il titolo** apre le ruote del mese e dell'anno al posto della griglia (la ruota di F8.2), come
 *   `DatePicker(.graphical)`: il titolo passa all'accento, la freccia gira verso il basso, e un altro tocco torna alla
 *   griglia sul mese scelto. Lo scambio entra sfocato (lo fa l'app); le ruote, che muove il dito, restano nitide.
 * - **Riduci movimento**: la pagina e la striscia vanno al loro posto senza moto; le ruote compaiono di colpo.
 * - **Fermo non ridisegna**: niente gira a riposo.
 *
 * @param today il giorno che si scrive nell'accento; di serie quello di oggi alla prima composizione.
 * @param isDateEnabled per spegnere altri giorni oltre ai limiti (i festivi, i giorni pieni).
 */
@Composable
fun FluidCalendar(
  selection: FluidDateSelection,
  onSelectionChange: (FluidDateSelection) -> Unit,
  modifier: Modifier = Modifier,
  state: FluidCalendarState = rememberFluidCalendarState(
    YearMonth.from(selection.anchorDate() ?: LocalDate.now()),
  ),
  marks: FluidCalendarMarks? = null,
  minDate: LocalDate? = null,
  maxDate: LocalDate? = null,
  isDateEnabled: ((LocalDate) -> Boolean)? = null,
  today: LocalDate = remember { LocalDate.now() },
  locale: Locale = LocalConfiguration.current.locales[0],
  labels: FluidCalendarLabels = FluidCalendarLabels(),
) = FluidCalendarContent(
  selection, onSelectionChange, modifier, state, marks, minDate, maxDate, isDateEnabled, today, locale, labels,
  fullDayDescriptions = false,
)

/** Tutto quello che le celle leggono: un oggetto solo, sempre lo stesso, cosi' una cella salta la ricomposizione. */
@Stable
internal class FluidCalendarCells {
  var selection by mutableStateOf<FluidDateSelection>(FluidDateSelection.Single(null))
  var enabled by mutableStateOf<(LocalDate) -> Boolean>({ true })
  var today by mutableLongStateOf(0L)
  var marks by mutableStateOf<FluidCalendarMarks?>(null)
  var labels by mutableStateOf(FluidCalendarLabels())
  var describe by mutableStateOf<(LocalDate) -> String>({ it.toString() })
  var numbers by mutableStateOf<Array<String>>(Array(31) { (it + 1).toString() })
  var onTap: (LocalDate) -> Unit = {}

  /** I segni che stanno entrando (sfocati), animati da un solo orologio per tutto il calendario. */
  val reveals = ArrayList<FluidCalendarMarkReveal>()
  var scope: CoroutineScope? = null
  private var revealJob: Job? = null

  /** Un segno comincia a entrare: l'orologio parte se non gira gia', e si ferma quando l'ultimo e' entrato. */
  fun reveal(r: FluidCalendarMarkReveal) {
    if (r !in reveals) reveals.add(r)
    if (revealJob?.isActive == true) return
    val s = scope ?: run { r.progress.floatValue = 1f; r.revealing = false; reveals.remove(r); return }
    revealJob = s.launch {
      while (reveals.isNotEmpty()) withFrameNanos(revealFrame)
    }
  }

  private val revealFrame: (Long) -> Unit = { nanos ->
    var i = 0
    while (i < reveals.size) {
      val r = reveals[i]
      if (r.startNanos < 0L) r.startNanos = nanos
      val t = (nanos - r.startNanos) / (FluidTextMotion.WordArrivalMillis * 1_000_000f)
      if (t >= 1f) {
        r.progress.floatValue = 1f
        r.revealing = false
        reveals.removeAt(i)
      } else {
        r.progress.floatValue = t
        i++
      }
    }
  }
}

/** Un segno che entra: da 0 a 1 in [FluidTextMotion.WordArrivalMillis]. */
@Stable
internal class FluidCalendarMarkReveal(var shown: FluidCalendarMark?) {
  val progress = mutableFloatStateOf(1f)
  var startNanos = -1L
  var pending = false

  /**
   * Vero mentre il segno entra (F7.29): solo allora il segno ha il suo layer, quello della sfocatura. Un segno fermo si
   * disegna nella cella; prima ogni segno teneva il layer per sempre, e una pagina o la striscia della settimana che
   * entrava ne creava uno per giorno segnato. Cambia due volte per arrivo: la ricomposizione è del solo segno.
   */
  var revealing by mutableStateOf(false)
}

/** Il calendario con le descrizioni scelte: brevi in pagina, intere (con l'anno e il giorno) nel foglio della data. */
@Composable
internal fun FluidCalendarContent(
  selection: FluidDateSelection,
  onSelectionChange: (FluidDateSelection) -> Unit,
  modifier: Modifier,
  state: FluidCalendarState,
  marks: FluidCalendarMarks?,
  minDate: LocalDate?,
  maxDate: LocalDate?,
  isDateEnabled: ((LocalDate) -> Boolean)?,
  today: LocalDate,
  locale: Locale,
  labels: FluidCalendarLabels,
  fullDayDescriptions: Boolean,
) {
  val scope = rememberCoroutineScope()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val weekStart = remember(locale) { FluidDates.firstDayOfWeek(locale) }
  val haptics = LocalFluidHaptics.current
  val cells = remember { FluidCalendarCells() }

  // Le celle leggono queste scritture nella stessa composizione: e' il disegno di `rememberUpdatedState`.
  cells.selection = selection
  cells.today = today.toEpochDay()
  cells.marks = marks
  cells.labels = labels
  cells.enabled = remember(minDate, maxDate, isDateEnabled) {
    { d: LocalDate -> (minDate == null || !d.isBefore(minDate)) && (maxDate == null || !d.isAfter(maxDate)) && isDateEnabled?.invoke(d) != false }
  }
  cells.describe = remember(locale, fullDayDescriptions, today.year) {
    val full = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val short = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale)
    val withYear = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMMy"), locale)
    val description: (LocalDate) -> String = { d ->
      when {
        fullDayDescriptions -> d.format(full)
        d.year != today.year -> d.format(withYear)
        else -> d.format(short)
      }
    }
    description
  }
  cells.numbers = remember(locale) { Array(31) { String.format(locale, "%d", it + 1) } }
  cells.onTap = { date ->
    haptics.play(FluidHapticEvent.Tick)
    state.focusOn(date)
    onSelectionChange(fluidCalendarTap(selection, date))
  }

  state.scope = scope
  state.reduced = reduced
  state.rtl = rtl
  state.minDate = minDate
  state.maxDate = maxDate
  state.weekStart = weekStart
  state.focusFor = { m ->
    val chosen = when (selection) {
      is FluidDateSelection.Single -> selection.date?.takeIf { YearMonth.from(it) == m }
      is FluidDateSelection.Multiple -> selection.dates.filter { YearMonth.from(it) == m }.minOrNull()
      is FluidDateSelection.Range -> listOfNotNull(selection.start, selection.end).firstOrNull { YearMonth.from(it) == m }
    }
    chosen ?: today.takeIf { YearMonth.from(it) == m } ?: m.atDay(1)
  }
  // Il giorno di riferimento iniziale: la scelta o oggi, se stanno nel mese mostrato.
  remember(state) {
    // Senza osservare: la composizione del calendario non dipende dal mese, che leggono solo la testata e le pagine.
    Snapshot.withoutReadObservation {
      if (YearMonth.from(state.focus) != state.month || state.focus == state.month.atDay(1)) state.focus = state.focusFor(state.month)
    }
    0
  }

  cells.scope = scope

  val density = LocalDensity.current
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val fit = minOf(maxWidth / 7, FluidCalendarDefaults.MaxColumnWidth)
    val minCell = FluidCalendarDefaults.MinCellAtFontScale * density.fontScale
    val overflow = minCell > maxWidth / 7
    val column = if (overflow) minCell else fit
    val row = maxOf(column * FluidCalendarDefaults.CircleFraction, FluidCalendarDefaults.MinRowHeight * min(density.fontScale, 1.6f))
      .coerceAtMost(column)
      .coerceAtLeast(if (overflow) minCell else 0.dp)
    val circle = minOf(column * FluidCalendarDefaults.CircleFraction, row)
    with(density) {
      state.widthPx = (column * 7).toPx()
      state.rowPx = row.toPx()
    }
    val grid = column * 7
    val available = maxWidth
    state.pagingEnabled = !overflow
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      CalendarHeader(state, locale, labels, Modifier.width(if (overflow) available else grid))
      val gridHeight = FluidCalendarDefaults.WeekdayRowHeight + row * FluidCalendarDefaults.Rows
      val body: @Composable () -> Unit = {
        // La griglia o le ruote del mese e dell'anno: le scambia il tocco sul titolo, cioe' l'app, e il contenuto che
        // cambia identita' entra sfocato (R7). Le ruote stanno nell'altezza della griglia: la pagina sotto non si muove.
        FluidGlassContentTransition(
          targetState = state.isMonthYearPickerShown,
          blurReference = row,
          contentAlignment = Alignment.TopCenter,
        ) { wheels ->
          if (wheels) {
            MonthYearWheels(state, locale, labels, minDate, maxDate, today, Modifier.width(grid).height(gridHeight))
          } else {
            Column {
              WeekdayRow(weekStart, locale, column)
              CalendarPager(state, cells, column, row, circle, pagingEnabled = !overflow)
            }
          }
        }
      }
      if (overflow) {
        // Il testo grande: sette colonne vere, la griglia scorre di lato e il mese cambia con le frecce.
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { body() }
      } else {
        body()
      }
    }
  }
}

@Composable
private fun CalendarHeader(state: FluidCalendarState, locale: Locale, labels: FluidCalendarLabels, modifier: Modifier) {
  val scheme = MaterialTheme.colorScheme
  val haptics = LocalFluidHaptics.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val strip = state.isWeekStrip
  val open = state.isMonthYearPickerShown
  // Le ruote si aprono dalla griglia intera: nella striscia il titolo e' solo un titolo.
  val canOpen = !strip && state.canDrag.value && state.collapseProgress <= 0f
  val title = remember(state.month, locale) {
    // Il mese da solo («novembre 2026», «November 2026»), come la testata di `DatePicker`.
    state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
  }
  // La freccia dopo il titolo gira verso il basso quando le ruote sono aperte, come in `DatePicker(.graphical)`.
  val turn = animateFloatAsState(
    targetValue = if (open) 90f else 0f,
    animationSpec = if (reduced) snap() else FluidMotion.snappy(),
    label = "fluid calendar title chevron",
  )
  Row(modifier.height(FluidCalendarDefaults.HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
    Row(
      Modifier
        .weight(1f)
        .height(FluidCalendarDefaults.HeaderHeight)
        .then(
          if (canOpen || open) {
            Modifier
              .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = if (open) labels.hideMonthYear else labels.showMonthYear,
              ) {
                haptics.play(FluidHapticEvent.Tap)
                state.showMonthYearPicker(!open)
              }
              .semantics {
                heading()
                // TalkBack dice «espanso» o «compresso», e il doppio tocco apre o chiude le ruote.
                if (open) collapse { state.showMonthYearPicker(false); true } else expand { state.showMonthYearPicker(true); true }
              }
          } else {
            Modifier.semantics { heading() }
          },
        )
        .padding(start = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicText(
        title,
        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (open) scheme.primary else scheme.onSurface),
        maxLines = 1,
      )
      if (canOpen || open) {
        // La freccia nell'accento, a 7,5 pt dal titolo e alta 10,5 (HIG «Pickers», misure.md § 1b).
        FluidIcon(
          FluidIcons.ChevronRight,
          contentDescription = null,
          tint = scheme.primary,
          size = 18.dp,
          modifier = Modifier.padding(start = 4.dp).graphicsLayer { rotationZ = if (rtl) -turn.value else turn.value },
        )
      }
    }
    // Con le ruote aperte le frecce del mese non ci sono (come in iOS), ma il loro posto resta: niente si sposta.
    CalendarArrow(FluidIcons.ChevronLeft, if (strip) labels.previousWeek else labels.previousMonth, state.canPage(-1), visible = !open) {
      state.pageBy(-1)
    }
    CalendarArrow(FluidIcons.ChevronRight, if (strip) labels.nextWeek else labels.nextMonth, state.canPage(1), visible = !open) {
      state.pageBy(1)
    }
  }
}

/**
 * Le ruote del mese e dell'anno, al posto della griglia (`DatePicker(.graphical)` col titolo toccato): la ruota di F8.2
 * con due colonne, nell'ordine della lingua (l'anno prima in giapponese, cinese, coreano e ungherese). Girando cambiano
 * il mese mostrato; i mesi fuori dai limiti sono spenti, gli anni vanno dal limite (o da cento anni prima di oggi) al
 * limite (o a cento anni dopo).
 */
@Composable
private fun MonthYearWheels(
  state: FluidCalendarState,
  locale: Locale,
  labels: FluidCalendarLabels,
  minDate: LocalDate?,
  maxDate: LocalDate?,
  today: LocalDate,
  modifier: Modifier,
) {
  val month = state.month
  val first = minOf(minDate?.year ?: (today.year - 100), month.year)
  val last = maxOf(maxDate?.year ?: (today.year + 100), month.year)
  val min = minDate?.let(YearMonth::from)
  val max = maxDate?.let(YearMonth::from)
  fun clamp(m: YearMonth): YearMonth = when {
    min != null && m.isBefore(min) -> min
    max != null && m.isAfter(max) -> max
    else -> m
  }
  val names = remember(locale) { Array(12) { java.time.Month.of(it + 1).getDisplayName(JavaTextStyle.FULL_STANDALONE, locale) } }
  val monthLabel = remember(names) { { i: Int -> names[i] } }
  val yearLabel = remember(locale, first) { { i: Int -> String.format(locale, "%d", first + i) } }
  val yearFirst = remember(locale) {
    val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMy") ?: ""
    pattern.indexOf('y') in 0 until pattern.indexOf('M').let { if (it < 0) Int.MAX_VALUE else it }
  }
  val monthColumn = FluidWheelColumn(
    count = 12,
    selected = month.monthValue - 1,
    onSelectedChange = { i -> state.showMonth(clamp(YearMonth.of(state.month.year, i + 1))) },
    label = monthLabel,
    cyclic = true,
    contentDescription = labels.monthWheel,
    weight = 1.4f,
    isEnabled = { i ->
      val m = YearMonth.of(month.year, i + 1)
      (min == null || !m.isBefore(min)) && (max == null || !m.isAfter(max))
    },
  )
  val yearColumn = FluidWheelColumn(
    count = last - first + 1,
    selected = month.year - first,
    onSelectedChange = { i -> state.showMonth(clamp(YearMonth.of(first + i, state.month.monthValue))) },
    label = yearLabel,
    contentDescription = labels.yearWheel,
  )
  Box(modifier, contentAlignment = Alignment.Center) {
    FluidWheelPicker(
      columns = if (yearFirst) listOf(yearColumn, monthColumn) else listOf(monthColumn, yearColumn),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun CalendarArrow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  description: String,
  enabled: Boolean,
  visible: Boolean = true,
  onClick: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  // Le frecce di `DatePicker`: nell'accento, a 42–45 pt l'una dall'altra (misure.md, § 1a); 44 dp di bersaglio.
  Box(
    Modifier.size(44.dp)
      .graphicsLayer { alpha = if (visible) 1f else 0f }
      .then(
        if (visible) {
          Modifier.fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button, haptic = FluidHapticEvent.Tick)
            .semantics { contentDescription = description }
        } else {
          Modifier.clearAndSetSemantics {}
        },
      ),
    contentAlignment = Alignment.Center,
  ) {
    FluidIcon(icon, contentDescription = null, tint = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = 0.3f), size = 22.dp)
  }
}

@Composable
private fun WeekdayRow(weekStart: DayOfWeek, locale: Locale, column: Dp) {
  val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
  val letters = remember(weekStart, locale) {
    List(7) { weekStart.plus(it.toLong()).getDisplayName(JavaTextStyle.SHORT, locale).trimEnd('.').uppercase(locale) }
  }
  Row(Modifier.height(FluidCalendarDefaults.WeekdayRowHeight).clearAndSetSemantics {}) {
    letters.forEach { letter ->
      Box(Modifier.width(column), contentAlignment = Alignment.Center) {
        BasicText(letter, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color), maxLines = 1)
      }
    }
  }
}

/**
 * Le tre pagine (prima, questa, dopo), spostate solo nel disegno: il dito muove `translationX` dei loro livelli, la misura
 * e la composizione non cambiano finche' la pagina non arriva.
 */
@Composable
private fun CalendarPager(
  state: FluidCalendarState,
  cells: FluidCalendarCells,
  column: Dp,
  row: Dp,
  circle: Dp,
  pagingEnabled: Boolean,
) {
  val density = LocalDensity.current
  val rowPx = with(density) { row.toPx() }
  val fullPx = rowPx * FluidCalendarDefaults.Rows
  val strip = state.isWeekStrip
  val month = state.month
  // Il giorno di riferimento serve qui solo alla striscia (le settimane vicine); nel mese lo legge il disegno.
  val focus = if (strip) state.focus else null
  val drag = rememberDraggableState { delta -> state.dragBy(delta) }
  val canDrag = pagingEnabled && state.canDrag.value
  Box(
    Modifier
      .width(column * 7)
      .layout { measurable, constraints ->
        val p = state.collapseProgress
        val height = (fullPx - (fullPx - rowPx) * p).roundToInt()
        val placeable = measurable.measure(constraints.copy(minHeight = fullPx.roundToInt(), maxHeight = fullPx.roundToInt()))
        layout(placeable.width, height) { placeable.place(0, 0) }
      }
      .clipToBounds()
      .draggable(
        state = drag,
        orientation = Orientation.Horizontal,
        enabled = canDrag,
        startDragImmediately = state.pageMoving,
        onDragStarted = { state.catchPage() },
        onDragStopped = { velocity -> state.releasePage(velocity) },
      ),
  ) {
    // Le pagine con la loro chiave, sorelle nello stesso gruppo: quando il mese cambia, i due mesi che restano si spostano
    // con la loro composizione e se ne compone uno solo, quello nuovo. L'elenco si fa prima, fuori dal ciclo: un
    // `continue` o un `if` intorno a `key` chiudono ogni giro in un gruppo suo, e le chiavi non sarebbero piu' sorelle.
    val here = if (focus != null) state.weekStartOf(focus).toEpochDay() else (month.year * 12L + month.monthValue - 1)
    val step = if (focus != null) 7L else 1L
    val prev = pagingEnabled && state.canPage(-1)
    val next = pagingEnabled && state.canPage(1)
    val anchors = when {
      prev && next -> longArrayOf(here - step, here, here + step)
      prev -> longArrayOf(here - step, here)
      next -> longArrayOf(here, here + step)
      else -> longArrayOf(here)
    }
    for (anchor in anchors) {
      key(strip, anchor) {
        PagerSlot(state, anchor, strip, current = anchor == here, cells, column, row, circle)
      }
    }
  }
}

/**
 * Una pagina: un mese ([anchor] = anno × 12 + mese − 1) o, nella striscia, una settimana ([anchor] = il giorno del suo
 * inizio). Dove sta lo decide il disegno, dal mese mostrato e dalla pagina che segue il dito: quando il mese cambia, le
 * pagine che restano non ricevono parametri nuovi a parte [current], e il mese dentro salta la ricomposizione.
 */
@Composable
private fun PagerSlot(
  state: FluidCalendarState,
  anchor: Long,
  strip: Boolean,
  current: Boolean,
  cells: FluidCalendarCells,
  column: Dp,
  row: Dp,
  circle: Dp,
) {
  Box(
    Modifier
      .graphicsLayer {
        val dir = if (state.rtl) -1f else 1f
        val offset = state.pageOffsetOf(anchor, strip)
        translationX = (offset - state.page.floatValue) * state.widthPx * dir
        translationY = if (offset == 0 && !strip) -state.collapseProgress * state.focusRow * state.rowPx else 0f
      }
      .then(if (!current) Modifier.clearAndSetSemantics {} else Modifier),
  ) {
    if (strip) WeekPanel(anchor, cells, column, row, circle)
    else MonthPanel(anchor.toInt(), state, cells, column, row, circle)
  }
}

@Composable
private fun MonthPanel(
  monthIndex: Int,
  state: FluidCalendarState,
  cells: FluidCalendarCells,
  column: Dp,
  row: Dp,
  circle: Dp,
) {
  val month = remember(monthIndex) { YearMonth.of(monthIndex / 12, monthIndex % 12 + 1) }
  val lead = Math.floorMod(month.atDay(1).dayOfWeek.value - state.weekStart.value, 7)
  val firstEpoch = month.atDay(1).toEpochDay() - lead
  val length = month.lengthOfMonth()
  val lastRow = (lead + length - 1) / 7
  Column {
    for (r in 0 until FluidCalendarDefaults.Rows) {
      Row {
        for (c in 0 until 7) {
          val i = r * 7 + c
          val day = i - lead + 1
          val epoch = firstEpoch + i
          when {
            day in 1..length -> {
              var edges = 0
              if (c == 0 || day == 1) edges = edges or EdgeLeft
              if (c == 6 || day == length) edges = edges or EdgeRight
              CalendarDay(epoch, edges, outside = false, state, cells, column, row, circle)
            }
            // I giorni degli altri mesi nella prima e nell'ultima riga, nascosti: entrano solo quelli della riga di
            // riferimento, mentre la griglia diventa la striscia (lo decide il disegno, non la composizione).
            r == 0 || r == lastRow -> {
              var edges = 0
              if (c == 0) edges = edges or EdgeLeft
              if (c == 6) edges = edges or EdgeRight
              CalendarDay(epoch, edges, outside = true, state, cells, column, row, circle, outsideRow = r, panelMonth = monthIndex)
            }
            else -> Spacer(Modifier.size(column, row))
          }
        }
      }
    }
  }
}

@Composable
private fun WeekPanel(weekStart: Long, cells: FluidCalendarCells, column: Dp, row: Dp, circle: Dp) {
  Row {
    for (c in 0 until 7) {
      var edges = 0
      if (c == 0) edges = edges or EdgeLeft
      if (c == 6) edges = edges or EdgeRight
      CalendarDay(weekStart + c, edges, outside = false, state = null, cells, column, row, circle)
    }
  }
}

private const val EdgeLeft = 1
private const val EdgeRight = 2

/**
 * Un giorno. Legge la scelta, i limiti e il segno attraverso stati derivati suoi: quando la scelta passa dal 25 al 26 si
 * ricompongono quei due giorni, e quando arriva il segno del 25 solo il 25.
 */
@Composable
private fun CalendarDay(
  epochDay: Long,
  edges: Int,
  outside: Boolean,
  state: FluidCalendarState?,
  cells: FluidCalendarCells,
  column: Dp,
  row: Dp,
  circle: Dp,
  outsideRow: Int = -1,
  panelMonth: Int = 0,
) {
  val date = remember(epochDay) { LocalDate.ofEpochDay(epochDay) }
  val style by remember(epochDay, cells) { derivedStateOf { fluidCalendarStyleOf(cells.selection, date) } }
  val enabled by remember(epochDay, cells) { derivedStateOf { cells.enabled(date) } }
  val isToday by remember(epochDay, cells) { derivedStateOf { cells.today == epochDay } }
  val marks = cells.marks
  val mark = marks?.stateOf(epochDay)?.value
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val reveal = remember(epochDay) { FluidCalendarMarkReveal(mark) }
  if (mark != reveal.shown) {
    reveal.shown = mark
    // Il segno arriva dall'app, non dal dito: entra sfocato e si mette a fuoco (la regola «dito nitido, app sfocata»).
    if (mark != null && !reduced) {
      reveal.progress.floatValue = 0f
      reveal.startNanos = -1L
      reveal.pending = true
      reveal.revealing = true
    }
  }
  SideEffect {
    if (reveal.pending) {
      reveal.pending = false
      cells.reveal(reveal)
    }
  }

  val scheme = MaterialTheme.colorScheme
  val accent = scheme.primary
  val ends = style and (FluidCalendarStyle.RangeStart or FluidCalendarStyle.RangeEnd) != 0
  val selected = style and FluidCalendarStyle.Selected != 0
  val solid = ends || (selected && isToday)
  val inBand = style and FluidCalendarStyle.InRange != 0
  val bandLeft = inBand || (style and FluidCalendarStyle.RangeEnd != 0 && style and FluidCalendarStyle.RangeStart == 0)
  val bandRight = inBand || (style and FluidCalendarStyle.RangeStart != 0 && style and FluidCalendarStyle.RangeEnd == 0 &&
    (cells.selection as? FluidDateSelection.Range)?.end != null)
  val ink = when {
    solid -> scheme.onPrimary
    !enabled -> scheme.onSurface.copy(alpha = 0.3f)
    selected || isToday -> accent
    else -> scheme.onSurface
  }
  val bold = selected || isToday || ends
  val labels = cells.labels
  val description = if (outside) "" else buildString {
    append(cells.describe(date))
    if (isToday) append(", ").append(labels.today)
    if (mark != null) append(", ").append(labels.markText(mark))
  }
  val rangeState = when {
    outside -> null
    style and FluidCalendarStyle.RangeStart != 0 -> labels.rangeStart
    style and FluidCalendarStyle.RangeEnd != 0 -> labels.rangeEnd
    inBand -> labels.inRange
    else -> null
  }
  val tint = accent.copy(alpha = FluidCalendarDefaults.SelectedTintAlpha)
  val band = accent.copy(alpha = FluidCalendarDefaults.RangeTintAlpha)
  val withMarks = marks != null
  val base = Modifier
    .size(column, row)
    .drawBehind {
      val r = circle.toPx() / 2f
      val cx = size.width / 2f
      val cy = size.height / 2f
      if (bandLeft) {
        if (edges and EdgeLeft != 0) drawCircle(band, r, Offset(cx, cy))
        else drawRect(band, Offset(0f, cy - r), Size(cx, 2f * r))
      }
      if (bandRight) {
        if (edges and EdgeRight != 0) drawCircle(band, r, Offset(cx, cy))
        else drawRect(band, Offset(cx, cy - r), Size(size.width - cx, 2f * r))
      }
      when {
        solid -> drawCircle(accent, r, Offset(cx, cy))
        selected -> drawCircle(tint, r, Offset(cx, cy))
      }
    }
  val cell = if (outside) {
    base.graphicsLayer {
      val s = state
      val current = s != null && s.month.year * 12 + s.month.monthValue - 1 == panelMonth && s.focusRow == outsideRow
      alpha = if (current) s!!.collapseProgress else 0f
    }.clearAndSetSemantics {}
  } else {
    base
      .clickable(enabled = enabled, role = Role.Button, interactionSource = null, indication = null) { cells.onTap(date) }
      .semantics(mergeDescendants = true) {
        contentDescription = description
        this.selected = style != 0
        if (rangeState != null) stateDescription = rangeState
        if (!enabled) disabled()
      }
  }
  Box(cell, contentAlignment = Alignment.Center) {
    val fontSp = FluidCalendarDefaults.DayFontSp * if (selected || ends) FluidCalendarDefaults.SelectedDayScale else 1f
    BasicText(
      cells.numbers[date.dayOfMonth - 1],
      style = TextStyle(
        fontSize = fontSp.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = ink,
        textAlign = TextAlign.Center,
      ),
      maxLines = 1,
      modifier = if (withMarks) Modifier.offset(y = (-4).dp) else Modifier,
    )
    if (mark != null) {
      CalendarMark(
        mark,
        color = when {
          solid -> scheme.onPrimary
          mark.color.isSpecified -> mark.color
          else -> scheme.onSurfaceVariant
        },
        reveal = reveal,
        modifier = Modifier.offset(y = circle * 0.3f),
      )
    }
  }
}

@Composable
private fun CalendarMark(mark: FluidCalendarMark, color: Color, reveal: FluidCalendarMarkReveal, modifier: Modifier) {
  val density = LocalDensity.current
  // F7.29: il layer della sfocatura solo mentre il segno entra; fermo, il segno si disegna nella cella (stessi pixel:
  // a fine arrivo il layer aveva alfa piena e nessun filtro).
  val arriving = if (!reveal.revealing) Modifier else Modifier.graphicsLayer {
    val t = reveal.progress.floatValue
    if (t < 1f) {
      alpha = t
      // σ al massimo 0,20 dell'altezza del segno (FluidTextMotion.ArrivalPeakBlur), che scende col suo arrivo.
      val sigma = with(density) { 6.dp.toPx() } * FluidTextMotion.ArrivalPeakBlur * (1f - t) * 2f
      renderEffect = if (sigma > 0.3f) BlurEffect(sigma, sigma) else null
    } else {
      alpha = 1f
      renderEffect = null
    }
  }
  when (mark.style) {
    FluidCalendarMarkStyle.Dots -> {
      val n = min(mark.count, FluidCalendarDefaults.MaxMarkDots)
      val dot = FluidCalendarDefaults.MarkDotSize
      val gap = FluidCalendarDefaults.MarkDotGap
      Row(modifier.then(arriving), horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(n) { Box(Modifier.size(dot).drawBehind { drawCircle(color) }) }
      }
    }
    FluidCalendarMarkStyle.Count -> BasicText(
      mark.count.toString(),
      style = TextStyle(fontSize = FluidCalendarDefaults.MarkCountFontSp.sp, fontWeight = FontWeight.SemiBold, color = color),
      maxLines = 1,
      modifier = modifier.then(arriving),
    )
  }
}

/** Dove si posa una pagina lasciata a [page] con [speedPages] pagine al secondo (per i test). */
internal fun fluidCalendarPageLanding(page: Float, speedPages: Float, reduced: Boolean, canPrev: Boolean, canNext: Boolean): Int =
  fluidDetentLanding(page, speedPages, reduced, if (canPrev) -1f else 0f, if (canNext) 1f else 0f).coerceIn(-1f, 1f).roundToInt()

/** La riga di [date] nella griglia di sei righe del suo mese (per i test e per la striscia). */
internal fun fluidCalendarRowOf(date: LocalDate, weekStart: DayOfWeek): Int {
  val first = date.withDayOfMonth(1)
  val lead = Math.floorMod(first.dayOfWeek.value - weekStart.value, 7)
  return (lead + date.dayOfMonth - 1) / 7
}
