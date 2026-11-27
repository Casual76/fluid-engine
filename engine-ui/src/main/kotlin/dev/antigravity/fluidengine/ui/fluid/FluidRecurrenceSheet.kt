/*
 * Il pannello «Ripeti» (4.0, F8.8, `scelta-ricorrenza`): la schermata Ripetizione di Calendario e Promemoria, gia' pronta,
 * in un foglio di vetro dell'engine. La regola e' un dato puro di `engine-foundation` (`FluidRecurrenceRule`), che sa
 * espandersi in date e dirsi in una frase (`FluidRecurrenceText`). Codice del Fluid Engine.
 */
@file:OptIn(ExperimentalLayoutApi::class)

package dev.antigravity.fluidengine.ui.fluid

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidDates
import dev.antigravity.fluidengine.foundation.FluidRecurrenceDay
import dev.antigravity.fluidengine.foundation.FluidRecurrenceEnd
import dev.antigravity.fluidengine.foundation.FluidRecurrenceFrequency
import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.foundation.FluidRecurrenceText
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Le parole del pannello «Ripeti», di serie in italiano; [english] per l'inglese. Le frasi della regola non stanno qui:
 * le scrive [FluidRecurrenceText] nella lingua del pannello.
 */
@Immutable
data class FluidRecurrenceLabels(
  val title: String = "Ripeti",
  val never: String = "Mai",
  val daily: String = "Ogni giorno",
  val weekly: String = "Ogni settimana",
  val biweekly: String = "Ogni 2 settimane",
  val monthly: String = "Ogni mese",
  val yearly: String = "Ogni anno",
  val custom: String = "Personalizza",
  val every: String = "Ogni",
  val unitsOne: List<String> = listOf("giorno", "settimana", "mese", "anno"),
  val unitsMany: List<String> = listOf("giorni", "settimane", "mesi", "anni"),
  val unitName: String = "Frequenza",
  val days: String = "Giorni",
  val repeatOn: String = "Il giorno",
  val end: String = "Fine ripetizione",
  val endNever: String = "Mai",
  val endOnDate: String = "In una data",
  val endAfter: String = "Dopo",
  val oneTime: String = "1 volta",
  val manyTimes: String = "%d volte",
  val next: String = "Prossime date",
  val noNext: String = "Nessuna data in arrivo",
  val confirm: String = "Fine",
  val cancel: String = "Annulla",
) {
  companion object {
    /** Le stesse in inglese, come la schermata Repeat di Calendario. */
    fun english(): FluidRecurrenceLabels = FluidRecurrenceLabels(
      title = "Repeat",
      never = "Never",
      daily = "Every Day",
      weekly = "Every Week",
      biweekly = "Every 2 Weeks",
      monthly = "Every Month",
      yearly = "Every Year",
      custom = "Custom",
      every = "Every",
      unitsOne = listOf("day", "week", "month", "year"),
      unitsMany = listOf("days", "weeks", "months", "years"),
      unitName = "Frequency",
      days = "Days",
      repeatOn = "On",
      end = "End Repeat",
      endNever = "Never",
      endOnDate = "On Date",
      endAfter = "After",
      oneTime = "1 time",
      manyTimes = "%d times",
      next = "Next dates",
      noNext = "No upcoming dates",
      confirm = "Done",
      cancel = "Cancel",
    )
  }
}

/**
 * Il pannello «Ripeti», come la schermata Ripetizione di Calendario e Promemoria: in un foglio di vetro dell'engine
 * ([FluidGlassSheet], a meta' e in cima, che nasce dalla [source] se c'e' e ci torna).
 *
 * - **In cima le scelte pronte** di Calendario: mai, ogni giorno, ogni settimana, ogni due settimane, ogni mese, ogni
 *   anno, e «Personalizza».
 * - **Personalizza** apre la ruota «Ogni [N] [giorni | settimane | mesi | anni]» ([FluidWheelPicker] di F8.2) e, secondo
 *   la frequenza, i giorni della settimana in capsule (dal primo giorno della lingua; l'ultimo acceso non si spegne), o il
 *   giorno del mese («il 23», «il quarto lunedi'», «l'ultimo lunedi'» dall'inizio [start]).
 * - **La fine**: mai, in una data (la capsula col calendario, [FluidDateCapsule]) o dopo N volte.
 * - **La frase** della regola («Ogni lun e gio fino al 20 dicembre») e **le prossime date esatte** stanno sopra il tasto
 *   di conferma: le cambia la regola, non il dito, e cambiano sfocate ([FluidGlassContentTransition]).
 *
 * [onConfirm] riceve la regola, o nulla per «Mai»; l'annulla e il back non confermano.
 *
 * @param start il giorno da cui la regola parte: da' i giorni ai campi vuoti e la prima data della lista.
 * @param previewCount quante prossime date mostrare (Calendario non le mostra: e' la richiesta della scheda).
 */
@Composable
fun FluidRecurrenceSheet(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  onConfirm: (FluidRecurrenceRule?) -> Unit,
  start: LocalDate,
  initialRule: FluidRecurrenceRule? = null,
  locale: Locale = LocalConfiguration.current.locales[0],
  labels: FluidRecurrenceLabels = FluidRecurrenceLabels(),
  source: FluidTransitionSource? = null,
  previewCount: Int = 5,
) {
  // La bozza resiste alla rotazione e al processo ucciso, come il calendario accanto (R2, difetto 39).
  val draft = rememberSaveable(visible, initialRule, start, saver = fluidRecurrenceDraftSaver(initialRule, start, locale)) {
    FluidRecurrenceDraft(initialRule, start, locale)
  }
  FluidGlassSheet(
    visible = visible,
    onDismissRequest = onDismissRequest,
    detents = remember { setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large) },
    paneTitle = labels.title,
    source = source,
  ) {
    FluidSheetScaffold(
      title = labels.title,
      actions = {
        FluidButton(labels.confirm, { onConfirm(draft.rule()) }, fillWidth = true, size = FluidButtonSize.Large)
        FluidButton(labels.cancel, onDismissRequest, style = FluidButtonStyle.Plain, fillWidth = true, size = FluidButtonSize.Large)
      },
    ) {
      RecurrenceBody(draft, start, locale, labels, previewCount)
    }
  }
}

/** Le scelte pronte di Calendario, nell'ordine della sua lista. */
internal enum class FluidRecurrencePreset { Never, Daily, Weekly, Biweekly, Monthly, Yearly, Custom }

/** Come una regola mensile prende il suo giorno dall'inizio. */
internal enum class FluidMonthlyMode { DayOfMonth, OrdinalWeekday, LastWeekday }

/** Come finisce. */
internal enum class FluidEndMode { Never, OnDate, AfterCount }

/**
 * La regola mentre si sceglie: semplici stati, e la regola vera solo quando serve ([rule]). Se la persona non cambia
 * niente, [rule] restituisce la regola di partenza com'era (R2, difetto 39): la bozza sa dire solo una parte di una
 * regola ricca (giorni del mese, mesi, posizioni), e riscriverla l'avrebbe cambiata.
 */
@Stable
internal class FluidRecurrenceDraft(private val initial: FluidRecurrenceRule?, val start: LocalDate, locale: Locale) {
  var preset by mutableStateOf(fluidRecurrencePresetOf(initial))
  var frequency by mutableStateOf(initial?.frequency ?: FluidRecurrenceFrequency.Weekly)
  var interval by mutableIntStateOf(initial?.interval ?: 1)
  var weekdays by mutableStateOf(
    initial?.daysOfWeek?.map { it.dayOfWeek }?.toSet()?.takeIf { it.isNotEmpty() } ?: setOf(start.dayOfWeek),
  )
  var monthly by mutableStateOf(
    when {
      initial == null -> FluidMonthlyMode.DayOfMonth
      initial.daysOfWeek.any { it.ordinal < 0 } -> FluidMonthlyMode.LastWeekday
      initial.daysOfWeek.any { it.ordinal > 0 } -> FluidMonthlyMode.OrdinalWeekday
      else -> FluidMonthlyMode.DayOfMonth
    },
  )
  var endMode by mutableStateOf(
    when (initial?.end) {
      is FluidRecurrenceEnd.OnDate -> FluidEndMode.OnDate
      is FluidRecurrenceEnd.AfterCount -> FluidEndMode.AfterCount
      else -> FluidEndMode.Never
    },
  )
  var endDate by mutableStateOf((initial?.end as? FluidRecurrenceEnd.OnDate)?.date ?: start.plusMonths(1))
  var count by mutableIntStateOf((initial?.end as? FluidRecurrenceEnd.AfterCount)?.count ?: 10)
  val weekStart: DayOfWeek = FluidDates.firstDayOfWeek(locale)

  /** I campi della bozza, nell'ordine del suo salvataggio ([fluidRecurrenceDraftSaver]). */
  internal fun fields(): List<Any> = listOf(
    preset.ordinal, frequency.ordinal, interval, weekdays.map { it.value }.sorted().toIntArray(), monthly.ordinal,
    endMode.ordinal, endDate.toEpochDay(), count,
  )

  /** I campi com'erano alla nascita: se tornano uguali, la persona non ha cambiato niente. */
  private val pristine: List<Any> = fields()

  /** Rimette i campi salvati da [fields]. */
  internal fun restore(saved: List<Any>) {
    preset = FluidRecurrencePreset.entries[saved[0] as Int]
    frequency = FluidRecurrenceFrequency.entries[saved[1] as Int]
    interval = saved[2] as Int
    weekdays = (saved[3] as IntArray).map { DayOfWeek.of(it) }.toSet()
    monthly = FluidMonthlyMode.entries[saved[4] as Int]
    endMode = FluidEndMode.entries[saved[5] as Int]
    endDate = LocalDate.ofEpochDay(saved[6] as Long)
    count = saved[7] as Int
  }

  private fun untouched(): Boolean {
    val now = fields()
    return now.indices.all { i ->
      val a = now[i]
      val b = pristine[i]
      if (a is IntArray && b is IntArray) a.contentEquals(b) else a == b
    }
  }

  /** La regola scelta, o nulla per «Mai». */
  fun rule(): FluidRecurrenceRule? {
    if (initial != null && untouched()) return initial
    val end = when (endMode) {
      FluidEndMode.Never -> FluidRecurrenceEnd.Never
      FluidEndMode.OnDate -> FluidRecurrenceEnd.OnDate(maxOf(endDate, start))
      FluidEndMode.AfterCount -> FluidRecurrenceEnd.AfterCount(count.coerceAtLeast(1))
    }
    val base = when (preset) {
      FluidRecurrencePreset.Never -> return null
      FluidRecurrencePreset.Daily -> FluidRecurrenceRule(FluidRecurrenceFrequency.Daily)
      FluidRecurrencePreset.Weekly -> FluidRecurrenceRule(FluidRecurrenceFrequency.Weekly)
      FluidRecurrencePreset.Biweekly -> FluidRecurrenceRule(FluidRecurrenceFrequency.Weekly, interval = 2)
      FluidRecurrencePreset.Monthly -> FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly)
      FluidRecurrencePreset.Yearly -> FluidRecurrenceRule(FluidRecurrenceFrequency.Yearly)
      FluidRecurrencePreset.Custom -> customRule()
    }
    return base.copy(end = end, weekStart = weekStart)
  }

  private fun customRule(): FluidRecurrenceRule = when (frequency) {
    FluidRecurrenceFrequency.Daily -> FluidRecurrenceRule(FluidRecurrenceFrequency.Daily, interval)
    FluidRecurrenceFrequency.Weekly -> FluidRecurrenceRule(
      FluidRecurrenceFrequency.Weekly, interval,
      daysOfWeek = weekdays.sortedBy { Math.floorMod(it.value - weekStart.value, 7) }.map { FluidRecurrenceDay(it) },
    )
    FluidRecurrenceFrequency.Monthly -> FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, interval, daysOfWeek = monthlyDays())
    FluidRecurrenceFrequency.Yearly -> FluidRecurrenceRule(
      FluidRecurrenceFrequency.Yearly, interval,
      daysOfWeek = monthlyDays(),
      monthsOfYear = if (monthly == FluidMonthlyMode.DayOfMonth) emptyList() else listOf(start.month),
    )
  }

  private fun monthlyDays(): List<FluidRecurrenceDay> = when (monthly) {
    FluidMonthlyMode.DayOfMonth -> emptyList()
    FluidMonthlyMode.OrdinalWeekday -> listOf(FluidRecurrenceDay(start.dayOfWeek, fluidWeekdayOrdinal(start)))
    FluidMonthlyMode.LastWeekday -> listOf(FluidRecurrenceDay.last(start.dayOfWeek))
  }
}

/**
 * Il salvataggio della bozza di «Ripeti»: i suoi campi semplici. La regola di partenza, l'inizio e la lingua non si
 * salvano: li ridà il chiamante, uguali, dopo la ricreazione (sono anche le chiavi della bozza).
 */
internal fun fluidRecurrenceDraftSaver(initial: FluidRecurrenceRule?, start: LocalDate, locale: Locale): Saver<FluidRecurrenceDraft, Any> =
  listSaver(
    save = { it.fields() },
    restore = { saved -> FluidRecurrenceDraft(initial, start, locale).apply { restore(saved) } },
  )

/** Il numero del giorno della settimana di [date] nel suo mese: il 24 novembre 2026, un martedi', e' il quarto (4). */
internal fun fluidWeekdayOrdinal(date: LocalDate): Int = (date.dayOfMonth - 1) / 7 + 1

/** Vero se [date] e' anche l'ultimo del suo giorno della settimana nel mese. */
internal fun fluidIsLastWeekday(date: LocalDate): Boolean = date.plusWeeks(1).month != date.month

/** La scelta pronta che corrisponde a [rule], o «Personalizza». */
internal fun fluidRecurrencePresetOf(rule: FluidRecurrenceRule?): FluidRecurrencePreset {
  if (rule == null) return FluidRecurrencePreset.Never
  val plain = rule.daysOfWeek.isEmpty() && rule.daysOfMonth.isEmpty() && rule.monthsOfYear.isEmpty() && rule.setPositions.isEmpty()
  if (!plain) return FluidRecurrencePreset.Custom
  return when {
    rule.frequency == FluidRecurrenceFrequency.Daily && rule.interval == 1 -> FluidRecurrencePreset.Daily
    rule.frequency == FluidRecurrenceFrequency.Weekly && rule.interval == 1 -> FluidRecurrencePreset.Weekly
    rule.frequency == FluidRecurrenceFrequency.Weekly && rule.interval == 2 -> FluidRecurrencePreset.Biweekly
    rule.frequency == FluidRecurrenceFrequency.Monthly && rule.interval == 1 -> FluidRecurrencePreset.Monthly
    rule.frequency == FluidRecurrenceFrequency.Yearly && rule.interval == 1 -> FluidRecurrencePreset.Yearly
    else -> FluidRecurrencePreset.Custom
  }
}

@Composable
private fun RecurrenceBody(
  draft: FluidRecurrenceDraft,
  start: LocalDate,
  locale: Locale,
  labels: FluidRecurrenceLabels,
  previewCount: Int,
) {
  val rule = draft.rule()
  val summary = rule?.let { FluidRecurrenceText.describe(it, start, locale) } ?: labels.never
  val preview = remember(rule, start, previewCount) { rule?.occurrences(start, limit = previewCount).orEmpty() }
  val dayFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMMy"), locale) }

  // La frase: la cambia la regola, non il dito, e cambia sfocata (regola di Alessio del 24 novembre 2026).
  FluidGlassContentTransition(
    targetState = summary,
    modifier = Modifier.fillMaxWidth().semantics { heading() },
    blurReference = 22.dp,
    contentAlignment = Alignment.CenterStart,
  ) { text ->
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
  }

  FluidListGroup {
    val presets = FluidRecurrencePreset.entries
    presets.forEachIndexed { index, preset ->
      if (index > 0) FluidListDivider()
      FluidChoiceRow(
        title = when (preset) {
          FluidRecurrencePreset.Never -> labels.never
          FluidRecurrencePreset.Daily -> labels.daily
          FluidRecurrencePreset.Weekly -> labels.weekly
          FluidRecurrencePreset.Biweekly -> labels.biweekly
          FluidRecurrencePreset.Monthly -> labels.monthly
          FluidRecurrencePreset.Yearly -> labels.yearly
          FluidRecurrencePreset.Custom -> labels.custom
        },
        selected = draft.preset == preset,
        onSelect = {
          if (preset == FluidRecurrencePreset.Custom && draft.preset != FluidRecurrencePreset.Custom) {
            // «Personalizza» parte dalla scelta di prima, come Calendario.
            when (draft.preset) {
              FluidRecurrencePreset.Daily -> { draft.frequency = FluidRecurrenceFrequency.Daily; draft.interval = 1 }
              FluidRecurrencePreset.Biweekly -> { draft.frequency = FluidRecurrenceFrequency.Weekly; draft.interval = 2 }
              FluidRecurrencePreset.Monthly -> { draft.frequency = FluidRecurrenceFrequency.Monthly; draft.interval = 1 }
              FluidRecurrencePreset.Yearly -> { draft.frequency = FluidRecurrenceFrequency.Yearly; draft.interval = 1 }
              else -> { draft.frequency = FluidRecurrenceFrequency.Weekly; draft.interval = 1 }
            }
          }
          draft.preset = preset
        },
      )
    }
  }

  if (draft.preset == FluidRecurrencePreset.Custom) CustomEditor(draft, start, locale, labels)

  if (draft.preset != FluidRecurrencePreset.Never) {
    SectionTitle(labels.end)
    FluidSegmentedControl(
      options = FluidEndMode.entries,
      selected = draft.endMode,
      onSelect = { draft.endMode = it },
      modifier = Modifier.fillMaxWidth(),
      label = {
        when (it) {
          FluidEndMode.Never -> labels.endNever
          FluidEndMode.OnDate -> labels.endOnDate
          FluidEndMode.AfterCount -> labels.endAfter
        }
      },
    )
    when (draft.endMode) {
      FluidEndMode.Never -> Unit
      FluidEndMode.OnDate -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(labels.endOnDate, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FluidDateCapsule(
          selection = FluidDateSelection.Single(draft.endDate),
          onSelectionChange = { (it as? FluidDateSelection.Single)?.date?.let { d -> draft.endDate = d } },
          label = labels.endOnDate,
          minDate = start,
          locale = locale,
          labels = if (locale.language == "it") FluidCalendarLabels() else FluidCalendarLabels.english(),
        )
      }
      FluidEndMode.AfterCount -> FluidStepper(
        value = draft.count,
        onValueChange = { draft.count = it },
        modifier = Modifier.fillMaxWidth(),
        range = 1..999,
        label = labels.endAfter,
        valueText = { if (it == 1) labels.oneTime else labels.manyTimes.replace("%d", it.toString()) },
      )
    }

    SectionTitle(labels.next)
    // Le date esatte: anche loro le cambia la regola, e cambiano sfocate.
    FluidGlassContentTransition(
      targetState = preview,
      modifier = Modifier.fillMaxWidth(),
      blurReference = 22.dp,
      contentAlignment = Alignment.TopStart,
    ) { dates ->
      Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (dates.isEmpty()) {
          Text(labels.noNext, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        dates.forEach { d ->
          Text(d.format(dayFormat), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { heading() },
  )
}

@Composable
private fun CustomEditor(draft: FluidRecurrenceDraft, start: LocalDate, locale: Locale, labels: FluidRecurrenceLabels) {
  val frequencies = remember { listOf(FluidRecurrenceFrequency.Daily, FluidRecurrenceFrequency.Weekly, FluidRecurrenceFrequency.Monthly, FluidRecurrenceFrequency.Yearly) }
  val interval = draft.interval
  // «Ogni [N] [settimane]»: la ruota di F8.2, due colonne. Le unita' si accordano col numero.
  FluidWheelPicker(
    columns = listOf(
      FluidWheelColumn(
        count = 99,
        selected = interval - 1,
        onSelectedChange = { draft.interval = it + 1 },
        label = remember { { i: Int -> (i + 1).toString() } },
        contentDescription = labels.every,
      ),
      FluidWheelColumn(
        count = frequencies.size,
        selected = frequencies.indexOf(draft.frequency),
        onSelectedChange = { draft.frequency = frequencies[it] },
        label = remember(interval, labels) { { i: Int -> if (interval == 1) labels.unitsOne[i] else labels.unitsMany[i] } },
        contentDescription = labels.unitName,
      ),
    ),
    modifier = Modifier.fillMaxWidth(),
  )
  when (draft.frequency) {
    FluidRecurrenceFrequency.Daily -> Unit
    FluidRecurrenceFrequency.Weekly -> {
      SectionTitle(labels.days)
      WeekdayCapsules(draft, locale)
    }
    FluidRecurrenceFrequency.Monthly, FluidRecurrenceFrequency.Yearly -> {
      SectionTitle(labels.repeatOn)
      val modes = buildList {
        add(FluidMonthlyMode.DayOfMonth)
        if (fluidWeekdayOrdinal(start) <= 4) add(FluidMonthlyMode.OrdinalWeekday)
        if (fluidIsLastWeekday(start)) add(FluidMonthlyMode.LastWeekday)
      }
      FluidListGroup {
        modes.forEachIndexed { index, mode ->
          if (index > 0) FluidListDivider()
          val example = FluidRecurrenceRule(
            draft.frequency,
            interval,
            daysOfWeek = when (mode) {
              FluidMonthlyMode.DayOfMonth -> emptyList()
              FluidMonthlyMode.OrdinalWeekday -> listOf(FluidRecurrenceDay(start.dayOfWeek, fluidWeekdayOrdinal(start)))
              FluidMonthlyMode.LastWeekday -> listOf(FluidRecurrenceDay.last(start.dayOfWeek))
            },
            monthsOfYear = if (draft.frequency == FluidRecurrenceFrequency.Yearly && mode != FluidMonthlyMode.DayOfMonth) listOf(start.month) else emptyList(),
          )
          FluidChoiceRow(
            title = FluidRecurrenceText.describe(example, start, locale),
            selected = draft.monthly == mode,
            onSelect = { draft.monthly = mode },
          )
        }
      }
    }
  }
}

/** I giorni della settimana in capsule, dal primo della lingua: l'ultimo acceso non si spegne. */
@Composable
private fun WeekdayCapsules(draft: FluidRecurrenceDraft, locale: Locale) {
  val scheme = MaterialTheme.colorScheme
  val days = remember(draft.weekStart) { List(7) { draft.weekStart.plus(it.toLong()) } }
  FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    days.forEach { day ->
      val on = day in draft.weekdays
      val name = day.getDisplayName(JavaTextStyle.FULL, locale)
      Box(
        Modifier
          .heightIn(min = 44.dp)
          .widthIn(min = 40.dp)
          .background(if (on) scheme.primary else FluidDateCapsuleDefaults.Fill, FluidCapsuleShape)
          .fluidPressable(
            onClick = {
              draft.weekdays = if (on) (draft.weekdays - day).ifEmpty { draft.weekdays } else draft.weekdays + day
            },
            role = Role.Checkbox,
            haptic = FluidHapticEvent.Tick,
          )
          .semantics(mergeDescendants = true) {
            contentDescription = name
            selected = on
          }
          .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          day.getDisplayName(JavaTextStyle.SHORT, locale).trimEnd('.'),
          style = MaterialTheme.typography.labelLarge,
          color = if (on) scheme.onPrimary else scheme.onSurface,
        )
      }
    }
  }
}
