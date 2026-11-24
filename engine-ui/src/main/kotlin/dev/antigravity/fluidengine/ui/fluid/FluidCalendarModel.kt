/*
 * Il calendario in pagina (4.0, F8.8): i dati. La scelta (un giorno, piu' giorni sparsi, un intervallo), i segni sotto i
 * giorni con il loro contenitore che arriva dalla rete un mese alla volta, le parole e le misure di Apple. Il disegno e il
 * gesto stanno in `FluidCalendar.kt`. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidDateRange
import java.time.LocalDate
import java.time.YearMonth

/**
 * Cosa e' scelto nel calendario, e quindi cosa fa un tocco su un giorno. Le tre forme di Apple: `DatePicker`
 * (`.graphical`), `MultiDatePicker` e l'intervallo «dal… al…».
 */
@Immutable
sealed interface FluidDateSelection {
  /** Un giorno solo ([date] nullo: niente ancora). Il tocco sceglie il giorno toccato. */
  @Immutable
  data class Single(val date: LocalDate?) : FluidDateSelection

  /** Giorni sparsi, anche in mesi diversi (`MultiDatePicker`). Il tocco accende o spegne il giorno. */
  @Immutable
  data class Multiple(val dates: Set<LocalDate> = emptySet()) : FluidDateSelection

  /**
   * Un intervallo dal [start] all'[end], compresi. Il primo tocco fissa l'inizio; il secondo la fine, se viene dopo
   * (o e' lo stesso giorno: un intervallo di un giorno); un secondo tocco **prima** dell'inizio ricomincia da li', e un
   * tocco a intervallo completo ricomincia da capo. I giorni in mezzo si disegnano come una fascia.
   */
  @Immutable
  data class Range(val start: LocalDate? = null, val end: LocalDate? = null) : FluidDateSelection {
    init {
      require(end == null || (start != null && !end.isBefore(start))) { "la fine di un intervallo viene dopo l'inizio" }
    }

    /** L'intervallo completo, o nulla mentre manca la fine. */
    val range: FluidDateRange? get() = if (start != null && end != null) FluidDateRange(start, end) else null
  }
}

/** Il mese da mostrare per primo per una scelta: quello del giorno scelto, dell'inizio, o del primo giorno sparso. */
internal fun FluidDateSelection.anchorDate(): LocalDate? = when (this) {
  is FluidDateSelection.Single -> date
  is FluidDateSelection.Multiple -> dates.minOrNull()
  is FluidDateSelection.Range -> start
}

/** La scelta dopo un tocco su [date] (pura: provata in `F8p8CalendarTest`). */
internal fun fluidCalendarTap(selection: FluidDateSelection, date: LocalDate): FluidDateSelection = when (selection) {
  is FluidDateSelection.Single -> FluidDateSelection.Single(date)
  is FluidDateSelection.Multiple ->
    FluidDateSelection.Multiple(if (date in selection.dates) selection.dates - date else selection.dates + date)
  is FluidDateSelection.Range -> {
    val start = selection.start
    when {
      start == null || selection.end != null -> FluidDateSelection.Range(date, null)
      date.isBefore(start) -> FluidDateSelection.Range(date, null)
      else -> FluidDateSelection.Range(start, date)
    }
  }
}

/** Come si disegna un giorno nella scelta: bit di [FluidCalendarStyle]. */
internal object FluidCalendarStyle {
  /** Il giorno e' scelto (un giorno solo, o uno dei giorni sparsi). */
  const val Selected = 1

  /** Primo giorno di un intervallo (anche a fine ancora da scegliere). */
  const val RangeStart = 2

  /** Ultimo giorno di un intervallo completo. */
  const val RangeEnd = 4

  /** Un giorno in mezzo a un intervallo completo: la fascia. */
  const val InRange = 8
}

/** Lo stile di [day] in [selection]: zero se non e' scelto. */
internal fun fluidCalendarStyleOf(selection: FluidDateSelection, day: LocalDate): Int = when (selection) {
  is FluidDateSelection.Single -> if (selection.date == day) FluidCalendarStyle.Selected else 0
  is FluidDateSelection.Multiple -> if (day in selection.dates) FluidCalendarStyle.Selected else 0
  is FluidDateSelection.Range -> {
    val start = selection.start
    val end = selection.end
    var style = 0
    if (start == day) style = style or FluidCalendarStyle.RangeStart
    if (end == day) style = style or FluidCalendarStyle.RangeEnd
    if (start != null && end != null && day.isAfter(start) && day.isBefore(end)) style = style or FluidCalendarStyle.InRange
    style
  }
}

/** Come si mostra un segno sotto un giorno. */
enum class FluidCalendarMarkStyle {
  /** Da uno a tre pallini (`UICalendarView.Decoration.default`, il pallino di Calendario). */
  Dots,

  /** Un numero piccolo («3»). */
  Count,
}

/**
 * Il segno sotto un giorno: quanti impegni, verifiche o note ha. L'app dice quali giorni hanno un segno e cosa vuol dire;
 * l'engine lo disegna dentro la cella, **senza cambiare l'altezza della riga**: un segno che arriva non sposta niente.
 *
 * @property count quanti: i pallini sono al piu' tre, il numero e' scritto.
 * @property color il colore dei pallini o del numero; di serie il grigio delle etichette secondarie, come Calendario.
 * @property description cosa legge TalkBack dopo la data («3 verifiche»); di serie «3 impegni» da [FluidCalendarLabels].
 */
@Immutable
data class FluidCalendarMark(
  val count: Int = 1,
  val style: FluidCalendarMarkStyle = FluidCalendarMarkStyle.Dots,
  val color: Color = Color.Unspecified,
  val description: String? = null,
) {
  init {
    require(count >= 1) { "un segno conta almeno 1: era $count" }
  }
}

/**
 * I segni del calendario, un giorno alla volta. Ogni giorno ha il suo stato: un segno che arriva o cambia **ricompone solo
 * il suo giorno**, e un mese che arriva dalla rete ([setMonth]) tocca solo i giorni che cambiano davvero. Si scrive dal
 * thread principale o da qualunque altro (gli stati sono di Compose, la tabella e' protetta).
 */
@Stable
class FluidCalendarMarks {
  private val states = HashMap<Long, MutableState<FluidCalendarMark?>>()

  /** Il segno di [date], o nulla. */
  operator fun get(date: LocalDate): FluidCalendarMark? = stateOf(date.toEpochDay()).value

  /** Mette o toglie (nulla) il segno di [date]. */
  operator fun set(date: LocalDate, mark: FluidCalendarMark?) {
    val state = stateOf(date.toEpochDay())
    if (state.value != mark) state.value = mark
  }

  /**
   * I segni di [month], arrivati tutti insieme: i giorni di [month] che non sono in [marks] perdono il loro. Scrive solo i
   * giorni che cambiano.
   */
  fun setMonth(month: YearMonth, marks: Map<LocalDate, FluidCalendarMark>) {
    for (day in 1..month.lengthOfMonth()) {
      val date = month.atDay(day)
      set(date, marks[date])
    }
  }

  /** Toglie tutti i segni. */
  fun clear() {
    val all = synchronized(states) { states.values.toList() }
    all.forEach { if (it.value != null) it.value = null }
  }

  internal fun stateOf(epochDay: Long): MutableState<FluidCalendarMark?> = synchronized(states) {
    states.getOrPut(epochDay) { mutableStateOf(null) }
  }
}

/**
 * Le parole del calendario: i tasti del mese, e cosa legge TalkBack su un giorno («25 novembre, 3 impegni»). Di serie in
 * italiano; [english] per l'inglese.
 *
 * @property oneMark e [manyMarks] il segno di serie, quando l'app non da' una [FluidCalendarMark.description]:
 *   `manyMarks` ha `%d` al posto del numero.
 * @property noDate, [fromDate] e [days] le parole della capsula ([FluidDateCapsule]): niente scelto, un intervallo senza
 *   ancora la fine («Dal 23 nov 2026»), quanti giorni sparsi («3 giorni»).
 * @property showMonthYear e [hideMonthYear] cosa fa il doppio tocco di TalkBack sul titolo; [monthWheel] e [yearWheel] i
 *   nomi delle due ruote.
 */
@Immutable
data class FluidCalendarLabels(
  val previousMonth: String = "Mese precedente",
  val nextMonth: String = "Mese successivo",
  val previousWeek: String = "Settimana precedente",
  val nextWeek: String = "Settimana successiva",
  val today: String = "oggi",
  val rangeStart: String = "inizio dell'intervallo",
  val rangeEnd: String = "fine dell'intervallo",
  val inRange: String = "nell'intervallo",
  val oneMark: String = "1 impegno",
  val manyMarks: String = "%d impegni",
  val noDate: String = "Nessuna data",
  val fromDate: String = "Dal %s",
  val days: String = "%d giorni",
  val showMonthYear: String = "Scegli mese e anno",
  val hideMonthYear: String = "Torna ai giorni",
  val monthWheel: String = "Mese",
  val yearWheel: String = "Anno",
) {
  /** Il segno come lo legge TalkBack. */
  internal fun markText(mark: FluidCalendarMark): String =
    mark.description ?: if (mark.count == 1) oneMark else manyMarks.replace("%d", mark.count.toString())

  companion object {
    /** Le stesse in inglese. */
    fun english(): FluidCalendarLabels = FluidCalendarLabels(
      previousMonth = "Previous month",
      nextMonth = "Next month",
      previousWeek = "Previous week",
      nextWeek = "Next week",
      today = "today",
      rangeStart = "start of range",
      rangeEnd = "end of range",
      inRange = "in range",
      oneMark = "1 event",
      manyMarks = "%d events",
      noDate = "No date",
      fromDate = "From %s",
      days = "%d days",
      showMonthYear = "Choose month and year",
      hideMonthYear = "Back to days",
      monthWheel = "Month",
      yearWheel = "Year",
    )
  }
}

/**
 * Le misure del calendario, da `DatePicker(.graphical)` di SwiftUI e dalle HIG «Pickers» (iOS 26), misurate il 24
 * novembre 2026 sulle immagini ufficiali (`artifacts/liquid-glass/sessioni/f8-8/riferimenti/misure.md`).
 */
object FluidCalendarDefaults {
  /**
   * La colonna piu' larga: **64 dp**. Il calendario divide la sua larghezza per sette (come `DatePicker` e la vista mese di
   * Calendario, 55,9 pt su un iPhone da 393), ma su un tablet sette colonne da 220 dp non sono un calendario: oltre questa
   * misura la griglia resta al centro. A occhio, 24 novembre 2026.
   */
  val MaxColumnWidth: Dp = 64.dp

  /**
   * Il cerchio del giorno scelto e il passo delle righe, in frazione della colonna: **0,82**. Il render di `graphicalStyle`
   * da' cerchio/colonna 0,815 e riga/colonna 0,82 (i cerchi di due righe si toccano); le HIG 0,86. Fiducia media.
   */
  const val CircleFraction: Float = 0.82f

  /** La riga non scende mai sotto i 44 dp di un bersaglio (HIG), anche su una finestra stretta. */
  val MinRowHeight: Dp = 44.dp

  /**
   * Il lato minimo di una cella col testo grande: **48 dp per la scala del carattere**, la regola delle sette colonne di
   * `FluidDateTimeSheet` (3.1.0). Quando sette celle cosi' non ci stanno, la griglia scorre di lato invece di stringersi, e
   * il mese cambia con le frecce.
   */
  val MinCellAtFontScale: Dp = 48.dp

  /** La testata col mese: **44 dp**, la fila di un bersaglio; il titolo a 17 sp semibold (`DatePicker`, fiducia media). */
  val HeaderHeight: Dp = 44.dp

  /**
   * La fila delle lettere dei giorni: **20 dp**, lettere a 13 sp semibold maiuscole nel grigio terziario. Dal centro delle
   * lettere al centro della prima riga ci sono cosi' 10 + 22 = 32 dp: 31 nel render di SwiftUI, 33 nelle HIG.
   */
  val WeekdayRowHeight: Dp = 20.dp

  /** Il numero del giorno: **20 sp** regular; scelto e oggi semibold, e il scelto 7% piu' grande (misurato: +7%). */
  const val DayFontSp: Float = 20f
  const val SelectedDayScale: Float = 1.07f

  /** L'accento sotto il giorno scelto: **12%** sul fondo, in chiaro e in scuro (render di SwiftUI e HIG, fiducia alta). */
  const val SelectedTintAlpha: Float = 0.12f

  /**
   * La fascia di un intervallo: l'accento al **12%**, alta quanto il cerchio, con le estremita' piene nell'accento e il
   * numero bianco. Apple non ha un intervallo nel calendario: e' la forma della fascia di Voli e di Calendario quando si
   * trascina un evento su piu' giorni, a occhio il 24 novembre 2026.
   */
  const val RangeTintAlpha: Float = 0.12f

  /** I pallini dei segni: **5 dp**, **3 dp** fra l'uno e l'altro, al piu' tre (il pallino di Calendario, a occhio). */
  val MarkDotSize: Dp = 5.dp
  val MarkDotGap: Dp = 3.dp
  const val MaxMarkDots: Int = 3

  /** Il numero di un segno: 11 sp, a metà fra il numero del giorno e il fondo del cerchio. */
  const val MarkCountFontSp: Float = 11f

  /** Le righe di un mese: sempre **sei**, come `DatePicker`: cambiare mese non cambia l'altezza e la lista sotto non salta. */
  const val Rows: Int = 6

  /** La molla di una pagina lasciata a meta' o di un tocco sulle frecce: critica, **0,38 s** ([FluidMotion.ResponseStandard]). */
  const val PageResponseSeconds: Double = 0.38

  /** La molla della striscia che si chiude o si apre dopo un lancio della lista: critica, **0,30 s**. */
  const val CollapseResponseSeconds: Double = 0.30
}
