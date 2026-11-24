/*
 * Le ruote pronte (4.0, F8.2): la durata del timer di Orologio (`UIDatePicker.Mode.countDownTimer`) e l'ora di
 * `UIDatePicker` a ruota. Sono [FluidWheelPicker] con le colonne **collegate**: il valore e' uno (una durata, un'ora),
 * le colonne ne sono le parti, e una parte che cambia puo' muoverne un'altra (AM e PM che girano quando l'ora passa da
 * 11 a 12; una durata che non scende sotto il minimo). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.ui.fluid

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import java.text.DateFormatSymbols
import java.time.LocalTime
import java.util.Locale

/**
 * Le parole di una durata a ruota: le unita' ferme nella fascia (il timer di Orologio scrive «ore», «min», «s») e i
 * nomi delle colonne per TalkBack.
 */
@Immutable
data class FluidDurationUnits(
  val hours: String = "ore",
  val minutes: String = "min",
  val seconds: String = "s",
  val hoursName: String = "Ore",
  val minutesName: String = "Minuti",
  val secondsName: String = "Secondi",
) {
  companion object {
    /** Le stesse in inglese, come il timer di Orologio in inglese: «hours», «min», «sec». */
    fun english(): FluidDurationUnits = FluidDurationUnits(
      hours = "hours",
      minutes = "min",
      seconds = "sec",
      hoursName = "Hours",
      minutesName = "Minutes",
      secondsName = "Seconds",
    )
  }
}

/**
 * La durata a ruota del timer di Orologio: ore, minuti e (di serie) secondi, ognuno con la sua unita' ferma nella
 * fascia. Il valore e' uno, in secondi ([seconds]); le colonne lo scompongono.
 *
 * **Le colonne sono collegate.** Una durata sotto [minimumSeconds] non si sceglie: se la ruota si ferma su `0 ore 0 min
 * 0 s`, la colonna piu' piccola va al minimo da sola, come `UIDatePicker.Mode.countDownTimer` che non lascia zero
 * minuti.
 *
 * **Di serie nessuna colonna gira all'infinito**, come il timer di Orologio di iOS 26 e 27: sopra «0 ore» e sopra «0 s»
 * non c'e' niente (F8.2c, dalle schermate di `riferimenti/`). Con [cyclic] minuti e secondi girano (dopo 59 torna 0),
 * come la ruota di `countDownTimer` fino a iOS 13; le ore mai (0…[maxHours]).
 *
 * @param seconds la durata scelta, in secondi.
 * @param onSecondsChange la ruota si e' fermata su un'altra durata (gia' portata dentro i limiti).
 * @param showSeconds la colonna dei secondi, come il timer di Orologio; senza, ore e minuti come `countDownTimer`.
 * @param maxHours l'ultima ora (23 in Orologio).
 * @param minuteInterval il passo dei minuti (`minuteInterval` di `UIDatePicker`: 1, 5, 15…), un divisore di 60.
 * @param minimumSeconds la durata piu' corta: di serie un secondo, o un minuto senza secondi.
 * @param units le parole: di serie in italiano, [FluidDurationUnits.english] per l'inglese.
 * @param cyclic minuti e secondi girano all'infinito; di serie no, come il timer di Orologio di iOS 27.
 */
@Composable
fun FluidDurationWheel(
  seconds: Long,
  onSecondsChange: (Long) -> Unit,
  modifier: Modifier = Modifier,
  showSeconds: Boolean = true,
  maxHours: Int = 23,
  minuteInterval: Int = 1,
  minimumSeconds: Long = if (showSeconds) 1L else 60L,
  units: FluidDurationUnits = FluidDurationUnits(),
  enabled: Boolean = true,
  cyclic: Boolean = false,
) {
  val interval = fluidWheelMinuteInterval(minuteInterval)
  val clamped = fluidDurationClamp(seconds, maxHours, showSeconds, interval, minimumSeconds)
  val hours = (clamped / 3600L).toInt()
  val minutes = ((clamped % 3600L) / 60L).toInt()
  val secs = (clamped % 60L).toInt()
  val current by rememberUpdatedState(clamped)
  val change by rememberUpdatedState(onSecondsChange)
  fun propose(h: Int, m: Int, s: Int) {
    val next = fluidDurationClamp(h * 3600L + m * 60L + s, maxHours, showSeconds, interval, minimumSeconds)
    if (next != current) change(next)
  }
  val minuteCount = 60 / interval
  val columns = buildList {
    add(
      FluidWheelColumn(
        count = maxHours.coerceAtLeast(0) + 1,
        selected = hours,
        onSelectedChange = { propose(it, (current % 3600L / 60L).toInt(), (current % 60L).toInt()) },
        label = remember { { i: Int -> i.toString() } },
        unit = units.hours,
        contentDescription = units.hoursName,
      ),
    )
    add(
      FluidWheelColumn(
        count = minuteCount,
        selected = minutes / interval,
        onSelectedChange = { propose((current / 3600L).toInt(), it * interval, (current % 60L).toInt()) },
        label = remember(interval) { { i: Int -> (i * interval).toString() } },
        cyclic = cyclic,
        unit = units.minutes,
        contentDescription = units.minutesName,
      ),
    )
    if (showSeconds) {
      add(
        FluidWheelColumn(
          count = 60,
          selected = secs,
          onSelectedChange = { propose((current / 3600L).toInt(), (current % 3600L / 60L).toInt(), it) },
          label = remember { { i: Int -> i.toString() } },
          cyclic = cyclic,
          unit = units.seconds,
          contentDescription = units.secondsName,
        ),
      )
    }
  }
  FluidWheelPicker(columns = columns, modifier = modifier, enabled = enabled)
}

/**
 * L'ora a ruota di `UIDatePicker` (`.time`, stile `.wheels`): ore e minuti, e in 12 ore anche AM e PM, nell'ordine della
 * lingua (in coreano e in giapponese AM e PM vengono prima). Fatta per stare in un foglio o in una riga che si apre, e
 * per prendere il posto dei tasti «−» e «+» di `FluidDateTimeSheet` (F8.8).
 *
 * **Le colonne sono collegate**, come in iOS: in 12 ore la ruota delle ore che passa da 11 a 12 (o torna da 12 a 11)
 * gira anche AM e PM. Le ore e i minuti girano all'infinito; i minuti non portano le ore (59 → 00 resta nella stessa
 * ora, come in iOS).
 *
 * @param time l'ora scelta; i secondi si ignorano.
 * @param onTimeChange la ruota si e' fermata su un'altra ora.
 * @param use24Hour di serie quello del sistema.
 * @param locale le parole di AM e PM e l'ordine delle colonne.
 * @param minuteInterval il passo dei minuti (1, 5, 15…), un divisore di 60: l'ora data si arrotonda per difetto.
 * @param hourDescription il nome della colonna delle ore per TalkBack; di serie quello di `FluidLabels`.
 * @param minuteDescription quello dei minuti.
 */
@Composable
fun FluidTimeWheel(
  time: LocalTime,
  onTimeChange: (LocalTime) -> Unit,
  modifier: Modifier = Modifier,
  use24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
  locale: Locale = LocalConfiguration.current.locales[0],
  minuteInterval: Int = 1,
  hourDescription: String = LocalFluidLabels.current.dateTime.hour,
  minuteDescription: String = LocalFluidLabels.current.dateTime.minute,
  enabled: Boolean = true,
) {
  val interval = fluidWheelMinuteInterval(minuteInterval)
  val shown = time.withSecond(0).withNano(0).withMinute(time.minute / interval * interval)
  val current by rememberUpdatedState(shown)
  val change by rememberUpdatedState(onTimeChange)
  fun propose(next: LocalTime) {
    if (next != current) change(next)
  }
  val minuteColumn = FluidWheelColumn(
    count = 60 / interval,
    selected = shown.minute / interval,
    onSelectedChange = { propose(current.withMinute(it * interval)) },
    label = remember(interval) { { i: Int -> (i * interval).toString().padStart(2, '0') } },
    cyclic = true,
    contentDescription = minuteDescription,
    textAlign = TextAlign.Start,
    stateDescription = remember(interval) { { i: Int -> (i * interval).toString().padStart(2, '0') } },
  )
  val columns = if (use24Hour) {
    listOf(
      FluidWheelColumn(
        count = 24,
        selected = shown.hour,
        onSelectedChange = { propose(current.withHour(it)) },
        label = remember { { i: Int -> i.toString().padStart(2, '0') } },
        cyclic = true,
        contentDescription = hourDescription,
        textAlign = TextAlign.End,
      ),
      minuteColumn,
    )
  } else {
    val symbols = remember(locale) { DateFormatSymbols.getInstance(locale).amPmStrings }
    val periodFirst = remember(locale) { fluidTimeWheelPeriodFirst(locale) }
    val hourColumn = FluidWheelColumn(
      count = 12,
      // 12, 1, 2, … 11: la voce 0 e' «12», come in iOS.
      selected = shown.hour % 12,
      onSelectedChange = { index -> propose(fluidTimeWheelHour12(current, index)) },
      label = remember { { i: Int -> if (i == 0) "12" else i.toString() } },
      cyclic = true,
      contentDescription = hourDescription,
      textAlign = TextAlign.End,
    )
    val periodColumn = FluidWheelColumn(
      count = 2,
      selected = if (shown.hour < 12) 0 else 1,
      onSelectedChange = { p -> propose(current.withHour(current.hour % 12 + if (p == 0) 0 else 12)) },
      label = remember(symbols) { { i: Int -> symbols[i] } },
      textAlign = TextAlign.Start,
    )
    if (periodFirst) listOf(periodColumn, hourColumn, minuteColumn) else listOf(hourColumn, minuteColumn, periodColumn)
  }
  FluidWheelPicker(columns = columns, modifier = modifier, enabled = enabled)
}

// ---------------------------------------------------------------------------------------------
// I conti (puri, provati)
// ---------------------------------------------------------------------------------------------

/** Il passo dei minuti valido: un divisore di 60 fra 1 e 30 (come `minuteInterval` di `UIDatePicker`), se no 1. */
internal fun fluidWheelMinuteInterval(interval: Int): Int = if (interval in 1..30 && 60 % interval == 0) interval else 1

/**
 * Una durata portata dentro i suoi limiti: niente sotto [minimumSeconds] ne' oltre `maxHours:59:59`, i minuti al loro
 * passo, e senza secondi tolti i secondi. Sotto il minimo vale il minimo: la parte piu' piccola sale (il
 * `countDownTimer` di iOS che non lascia zero minuti).
 */
internal fun fluidDurationClamp(
  seconds: Long,
  maxHours: Int,
  showSeconds: Boolean,
  minuteInterval: Int,
  minimumSeconds: Long,
): Long {
  val max = maxHours.coerceAtLeast(0) * 3600L + 59L * 60L + if (showSeconds) 59L else 0L
  var value = seconds.coerceIn(0L, max)
  val h = value / 3600L
  val m = (value % 3600L) / 60L / minuteInterval * minuteInterval
  val s = if (showSeconds) value % 60L else 0L
  value = h * 3600L + m * 60L + s
  val floor = minimumSeconds.coerceIn(0L, max)
  if (value < floor) {
    // Il minimo, arrotondato per eccesso al passo che la ruota sa mostrare.
    val step = if (showSeconds) 1L else minuteInterval * 60L
    value = (floor + step - 1) / step * step
  }
  return value.coerceAtMost(max)
}

/**
 * L'ora di 24 che viene dalla ruota delle ore in 12 ore che si ferma su [index] (0 = «12»), partendo da [from]: la ruota
 * che passa da 11 a 12, o da 12 a 11, gira AM e PM, come in iOS. Il verso e' la strada corta fra le due voci.
 */
internal fun fluidTimeWheelHour12(from: LocalTime, index: Int): LocalTime {
  val oldIndex = from.hour % 12
  val pm = from.hour >= 12
  var delta = index - oldIndex
  if (delta > 6) delta -= 12
  if (delta < -6) delta += 12
  // Avanti oltre l'11 (verso il 12, cioe' la voce 0) o indietro oltre il 12 (verso l'11): si gira.
  val crossed = (delta > 0 && oldIndex + delta >= 12) || (delta < 0 && oldIndex + delta < 0)
  val nextPm = if (crossed) !pm else pm
  return from.withHour(index + if (nextPm) 12 else 0)
}

/** Vero se nella lingua [locale] AM e PM vengono prima dell'ora («오전 9:41»). */
internal fun fluidTimeWheelPeriodFirst(locale: Locale): Boolean {
  val pattern = DateFormat.getBestDateTimePattern(locale, "hm")
  val a = pattern.indexOf('a')
  val h = pattern.indexOfFirst { it == 'h' || it == 'K' }
  return a in 0 until h
}
