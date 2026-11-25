/*
 * La data in una riga (4.0, F8.8, `intervalli-e-giorni-sparsi`): la capsula «23 nov 2026» di `DatePicker(.compact)` che
 * apre il calendario in un popover nato da lei, e la stessa capsula per una durata («45 min») che apre la ruota del
 * timer di F8.2. Il popover e' quello dalla sorgente dell'engine (F5B.1): la capsula diventa il pannello e ci torna.
 * Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Le misure della capsula, da `DatePicker(.compact)` (HIG «Pickers» di iOS 26 e la scheda dell'evento di Calendario in
 * iOS 26 e 27, misurate il 24 novembre 2026: `artifacts/liquid-glass/sessioni/f8-8/riferimenti/misure.md`, § 2).
 */
object FluidDateCapsuleDefaults {
  /** L'altezza: **34 dp** (HIG 34 pt, Calendario 34,5 in iOS 27 e 36 in iOS 26). Fiducia media. */
  val Height: Dp = 34.dp

  /** Il bersaglio intorno: **44 dp**, quanto chiedono le HIG; la capsula sta al centro. */
  val TouchHeight: Dp = 44.dp

  /** Il margine del testo: **13 dp** per lato (13–14 pt misurati). Il raggio e' meta' dell'altezza: una capsula piena. */
  val HorizontalPadding: Dp = 13.dp

  /** Il testo: **17 sp** nel colore dell'etichetta (`label`), come in Calendario. */
  const val FontSp: Float = 17f

  /**
   * Il fondo: il grigio `tertiarySystemFill`, **(118, 118, 128) al 12%**, in chiaro e in scuro (HIG di iOS 26; iOS 27 lo
   * porta al 16%). Fiducia alta sul colore.
   */
  val Fill: Color = Color(118, 118, 128).copy(alpha = 0.12f)
}

/**
 * La capsula di una data in una riga, come `DatePicker` con lo stile `.compact`: «23 nov 2026» (la data media della
 * lingua) in un grigio leggero. Toccata, **diventa il popover del calendario**; la capsula scrive subito il giorno scelto,
 * e il popover ci rientra quando si tocca fuori: il popover dalla sorgente dell'engine (F5B.1), anche sul telefono
 * (`FluidCompactAdaptation.Popover`).
 *
 * La capsula sa le tre scelte del calendario:
 * - un giorno ([FluidDateSelection.Single]): la capsula scrive «23 nov 2026»;
 * - un intervallo ([FluidDateSelection.Range]): la capsula scrive «23–30 nov 2026»;
 * - giorni sparsi ([FluidDateSelection.Multiple]): la capsula scrive «3 giorni».
 *
 * @param label cosa legge TalkBack prima del valore («Inizio», «Scadenza»).
 * @param closeOnSelection falso (di serie): il popover resta aperto finche' si tocca fuori, come `DatePicker(.compact)` di
 *   iOS (scelta di Alessio del 24 novembre 2026, F8.8b). Vero: si chiude appena la scelta e' fatta (un giorno, o la fine
 *   dell'intervallo; coi giorni sparsi resta aperto comunque).
 * @param text di serie la data media della lingua; un'app puo' scrivere la sua.
 */
@Composable
fun FluidDateCapsule(
  selection: FluidDateSelection,
  onSelectionChange: (FluidDateSelection) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  minDate: LocalDate? = null,
  maxDate: LocalDate? = null,
  isDateEnabled: ((LocalDate) -> Boolean)? = null,
  marks: FluidCalendarMarks? = null,
  today: LocalDate = remember { LocalDate.now() },
  locale: Locale = LocalConfiguration.current.locales[0],
  labels: FluidCalendarLabels = FluidCalendarLabels(),
  text: (FluidDateSelection) -> String = { fluidDateCapsuleText(it, locale, labels) },
  closeOnSelection: Boolean = false,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  val calendar = rememberFluidCalendarState(java.time.YearMonth.from(selection.anchorDate() ?: LocalDate.now()))
  FluidPickerCapsule(
    text = text(selection),
    label = label,
    open = open,
    onOpenChange = { opening ->
      if (opening) calendar.showMonth(java.time.YearMonth.from(selection.anchorDate() ?: LocalDate.now()))
      open = opening
    },
    modifier = modifier,
    paneTitle = label,
  ) {
    FluidCalendar(
      selection = selection,
      onSelectionChange = { picked ->
        onSelectionChange(picked)
        val done = closeOnSelection && when (picked) {
          is FluidDateSelection.Single -> true
          is FluidDateSelection.Range -> picked.end != null
          is FluidDateSelection.Multiple -> false
        }
        if (done) open = false
      },
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      state = calendar,
      marks = marks,
      minDate = minDate,
      maxDate = maxDate,
      isDateEnabled = isDateEnabled,
      today = today,
      locale = locale,
      labels = labels,
    )
  }
}

/**
 * La capsula di una durata («45 min», «1 h 30 min»): toccata diventa il popover con la ruota del timer di Orologio
 * ([FluidDurationWheel], F8.2), la `UIDatePicker` in modo `countDownTimer` con l'aptica a scatti. La ruota non e' rifatta:
 * e' quella, con le sue colonne collegate e il suo minimo. Il popover si chiude toccando fuori.
 */
@Composable
fun FluidDurationCapsule(
  seconds: Long,
  onSecondsChange: (Long) -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  showSeconds: Boolean = false,
  maxHours: Int = 23,
  minuteInterval: Int = 1,
  minimumSeconds: Long = if (showSeconds) 1L else 60L,
  units: FluidDurationUnits = FluidDurationUnits(),
  text: (Long) -> String = { fluidDurationText(it, showSeconds, units) },
) {
  var open by rememberSaveable { mutableStateOf(false) }
  FluidPickerCapsule(
    text = text(seconds),
    label = label,
    open = open,
    onOpenChange = { open = it },
    modifier = modifier,
    paneTitle = label,
  ) {
    FluidDurationWheel(
      seconds = seconds,
      onSecondsChange = onSecondsChange,
      modifier = Modifier.width(300.dp).padding(vertical = 8.dp),
      showSeconds = showSeconds,
      maxHours = maxHours,
      minuteInterval = minuteInterval,
      minimumSeconds = minimumSeconds,
      units = units,
    )
  }
}

/**
 * La capsula e il suo popover: la capsula e' la sorgente (`fluidTransitionSource`), il popover la prende in prestito per
 * tutta la corsa e la restituisce al ritorno, nello stesso fotogramma in cui la sagoma ci rientra.
 */
@Composable
internal fun FluidPickerCapsule(
  text: String,
  label: String?,
  open: Boolean,
  onOpenChange: (Boolean) -> Unit,
  modifier: Modifier,
  paneTitle: String?,
  content: @Composable ColumnScope.() -> Unit,
) {
  val namespace = rememberFluidGlassNamespace()
  val source = remember(namespace) { FluidTransitionSource(FluidPickerCapsuleId, namespace) }
  val scheme = MaterialTheme.colorScheme
  Box(
    modifier
      .heightIn(min = FluidDateCapsuleDefaults.TouchHeight)
      .fluidPressable(onClick = { onOpenChange(true) }, role = Role.Button, haptic = FluidHapticEvent.Tap)
      .semantics(mergeDescendants = true) {
        // Con un'etichetta TalkBack legge «Inizio, 23 nov 2026»; senza, il testo della capsula basta.
        if (label != null) {
          contentDescription = label
          stateDescription = text
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .fluidTransitionSource(FluidPickerCapsuleId, namespace, FluidCapsuleShape)
        .height(FluidDateCapsuleDefaults.Height)
        .background(FluidDateCapsuleDefaults.Fill, FluidCapsuleShape)
        .padding(horizontal = FluidDateCapsuleDefaults.HorizontalPadding),
      contentAlignment = Alignment.Center,
    ) {
      BasicText(
        text,
        style = TextStyle(fontSize = FluidDateCapsuleDefaults.FontSp.sp, color = if (open) scheme.primary else scheme.onSurface),
        maxLines = 1,
      )
    }
  }
  FluidGlassModalPortal(
    visible = open,
    onDismissRequest = { onOpenChange(false) },
    paneTitle = paneTitle,
    source = source,
    compactAdaptation = FluidCompactAdaptation.Popover,
    content = content,
  )
}

private const val FluidPickerCapsuleId = "fluid-picker-capsule"

/**
 * Il testo di serie della capsula: la data media della lingua («23 nov 2026», «Nov 23, 2026»); un intervallo nel modo
 * corto («23–30 nov 2026», «23 nov – 5 dic 2026», «Nov 23 – 30, 2026»); l'inizio solo («Dal 23 nov 2026»); i giorni
 * sparsi contati («3 giorni»), o la data se e' uno.
 */
internal fun fluidDateCapsuleText(selection: FluidDateSelection, locale: Locale, labels: FluidCalendarLabels): String {
  val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
  return when (selection) {
    is FluidDateSelection.Single -> selection.date?.format(medium) ?: labels.noDate
    is FluidDateSelection.Multiple -> when (selection.dates.size) {
      0 -> labels.noDate
      1 -> selection.dates.first().format(medium)
      else -> labels.days.replace("%d", selection.dates.size.toString())
    }
    is FluidDateSelection.Range -> {
      val start = selection.start
      val end = selection.end
      when {
        start == null -> labels.noDate
        end == null -> labels.fromDate.replace("%s", start.format(medium))
        else -> fluidDateRangeText(start, end, locale)
      }
    }
  }
}

/** «23–30 nov 2026», «23 nov – 5 dic 2026», «28 dic 2026 – 3 gen 2027»; in inglese «Nov 23 – 30, 2026». */
internal fun fluidDateRangeText(start: LocalDate, end: LocalDate, locale: Locale): String {
  val medium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
  if (start == end) return start.format(medium)
  fun f(pattern: String, d: LocalDate) = d.format(DateTimeFormatter.ofPattern(pattern, locale))
  return when (locale.language) {
    "it" -> when {
      start.year != end.year -> "${f("d MMM yyyy", start)} – ${f("d MMM yyyy", end)}"
      start.month != end.month -> "${f("d MMM", start)} – ${f("d MMM yyyy", end)}"
      else -> "${start.dayOfMonth}–${f("d MMM yyyy", end)}"
    }
    "en" -> when {
      start.year != end.year -> "${f("MMM d, yyyy", start)} – ${f("MMM d, yyyy", end)}"
      start.month != end.month -> "${f("MMM d", start)} – ${f("MMM d, yyyy", end)}"
      else -> "${f("MMM d", start)} – ${f("d, yyyy", end)}"
    }
    else -> "${start.format(medium)} – ${end.format(medium)}"
  }
}

/** «45 min», «1 h 30 min», «2 h», e con i secondi «1 min 5 s». */
internal fun fluidDurationText(seconds: Long, showSeconds: Boolean, units: FluidDurationUnits): String {
  val h = seconds / 3600
  val m = seconds % 3600 / 60
  val s = seconds % 60
  val parts = buildList {
    if (h > 0) add("$h h")
    if (m > 0 || (h == 0L && (!showSeconds || s == 0L))) add("$m ${units.minutes}")
    if (showSeconds && s > 0) add("$s ${units.seconds}")
  }
  return parts.joinToString(" ")
}
