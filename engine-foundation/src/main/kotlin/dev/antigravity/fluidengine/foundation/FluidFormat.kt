package dev.antigravity.fluidengine.foundation

import android.icu.text.CompactDecimalFormat
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.text.ListFormatter
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/*
 * F8.1 · `formattatori-e-tempi-vivi`. Un posto solo per scrivere i valori come li legge una persona: «12 min fa», «ieri,
 * 09:04», «1 h e 25 min», «2,4 MB», «Anna, Luca e altri 3», «1,2 Mln», con i plurali giusti e nella lingua che l'app
 * chiede (di serie quella del sistema).
 *
 * Il modello sono le `FormatStyle` di Foundation (iOS 15): `Date.FormatStyle`, `Date.RelativeFormatStyle`,
 * `ByteCountFormatStyle`, `ListFormatStyle`, `Duration.UnitsFormatStyle` e `Duration.TimeFormatStyle`,
 * `.number.notation(.compactName)`. Come Apple, le parole e i plurali vengono dai dati CLDR: qui dalle ICU di Android
 * (`android.icu.*`, API 24–26, tutte sotto il pavimento dell'engine), che sono le stesse tabelle. Niente Compose, niente
 * risorse: funzioni pure, con l'istante «adesso» e il fuso passati da fuori, così si provano con un orologio finto.
 *
 * Le ICU cambiano un poco fra versioni di Android («1 min fa» o «1 minuto fa»): le prove fissano la lingua e l'SDK.
 */

/** La larghezza delle parole, come `unitsStyle` di Apple: `.wide`, `.abbreviated`, `.narrow`. */
enum class FluidFormatWidth {
  /** «12 minuti fa», «1 ora e 25 minuti»: la forma intera, anche quella che legge TalkBack. */
  Wide,

  /** «12 min fa», «1 h e 25 min»: la forma di serie nelle righe. */
  Short,

  /** «12 min fa», «1h 25min»: la più stretta, per badge e capsule. */
  Narrow,
}

/** Come si contano i byte, come `ByteCountFormatStyle.Style` di Apple. */
enum class FluidByteStyle {
  /** I file: 1 KB = 1000 byte (`.file`, che Apple usa nel Finder e in File). */
  File,

  /** La memoria: 1 KB = 1024 byte (`.memory`), con le stesse sigle. */
  Memory,
}

/**
 * Le funzioni di formattazione dell'engine. Ognuna prende la lingua (di serie quella del sistema) e, dove c'entra il
 * tempo, l'istante «adesso» e il fuso: nessuna legge l'orologio di nascosto.
 */
object FluidFormat {

  // ------------------------------------------------------------------------------------------------------------------
  // Date e ore
  // ------------------------------------------------------------------------------------------------------------------

  /** La data nello stile della lingua: «23 nov 2026» (`MEDIUM`), «23/11/26» (`SHORT`), come `Date.FormatStyle(date:)`. */
  fun date(
    instant: Instant,
    style: FormatStyle = FormatStyle.MEDIUM,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
  ): String = DateTimeFormatter.ofLocalizedDate(style).withLocale(locale).format(instant.atZone(zone))

  /** L'ora nello stile della lingua: «09:04» in italiano, «9:04 AM» in inglese, come `Date.FormatStyle(time: .shortened)`. */
  fun time(
    instant: Instant,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
  ): String = icuDateFormat(DateFormat.NONE, DateFormat.SHORT, locale, zone).format(Date.from(instant))

  /**
   * Il giorno e l'ora come li scrive una lista di messaggi: ieri, oggi e domani col nome del giorno («ieri, 09:04»),
   * altrimenti la data media e l'ora. È il `Date.FormatStyle` con la data relativa di Apple (`doesRelativeDateFormatting` di
   * `DateFormatter`), dalle stesse tabelle CLDR: il nome del giorno da `RelativeDateTimeFormatter`, l'unione di giorno e
   * ora dal modello della lingua (`DateTimePatternGenerator.dateTimeFormat`). Un giorno è un giorno del calendario nel
   * fuso [zone], non 24 ore, e «oggi» è quello di [now], non quello dell'orologio del telefono.
   *
   * @param includeTime falso: solo il giorno («ieri», «23 nov 2026»).
   */
  fun dayAndTime(
    instant: Instant,
    now: Instant,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
    includeTime: Boolean = true,
  ): String {
    val day = instant.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val direction = when (day) {
      today -> RelativeDateTimeFormatter.Direction.THIS
      today.minusDays(1) -> RelativeDateTimeFormatter.Direction.LAST
      today.plusDays(1) -> RelativeDateTimeFormatter.Direction.NEXT
      else -> null
    }
    // Il nome del giorno («ieri») va minuscolo in mezzo a una riga; a inizio frase lo rialza chi scrive la frase.
    val dayText = if (direction != null) {
      RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale),
        null,
        RelativeDateTimeFormatter.Style.LONG,
        DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE,
      ).format(direction, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
    } else {
      date(instant, FormatStyle.MEDIUM, locale, zone)
    }
    if (!includeTime) return dayText
    // Il modello unisce giorno ({1}) e ora ({0}): «{1}, {0}» in italiano. Le parole fra apici sono letterali.
    val pattern = android.icu.text.DateTimePatternGenerator.getInstance(locale).dateTimeFormat.replace("'", "")
    return pattern.replace("{1}", dayText).replace("{0}", time(instant, locale, zone))
  }

  /**
   * La data corta di una riga di lista, come la colonna dell'ora di Mail e Messaggi: oggi l'ora («09:04»), ieri «ieri»,
   * nella settimana il giorno («lunedì»), nell'anno giorno e mese («12 mar»), prima la data corta («12/03/24»).
   * Soglie di Mail su iOS 17, a occhio il 23 novembre 2026.
   */
  fun listDate(
    instant: Instant,
    now: Instant,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
  ): String {
    val time = instant.atZone(zone)
    val day = time.toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when {
      day == today -> time(instant, locale, zone)
      day == today.minusDays(1) -> dayAndTime(instant, now, locale, zone, includeTime = false)
      day.isAfter(today.minusDays(7)) && !day.isAfter(today) ->
        DateTimeFormatter.ofPattern("EEEE", locale).format(time)
      day.year == today.year -> DateTimeFormatter.ofPattern(skeletonPattern("dMMM", locale), locale).format(time)
      else -> date(instant, FormatStyle.SHORT, locale, zone)
    }
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Tempo relativo
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * Quanto manca o quanto è passato: «ora», «12 min fa», «tra 2 giorni», come `Date.RelativeFormatStyle` di Apple
   * (`presentation: .numeric`, con «ora» sotto il minuto come `.named`).
   *
   * Le soglie (a occhio il 23 novembre 2026, sul modello di Apple che mostra l'unità più grande non nulla e tronca):
   * sotto 1 minuto «ora»; poi i minuti fino a 59; le ore fino a 23; i giorni fino a 6; le settimane fino a 4; i mesi
   * (30 giorni) fino a 11; poi gli anni (365 giorni). Si tronca sempre: 1 h 59 min è «1 h fa».
   *
   * @param width [FluidFormatWidth.Wide] per TalkBack («12 minuti fa»).
   */
  fun relative(
    instant: Instant,
    now: Instant,
    locale: Locale = Locale.getDefault(),
    width: FluidFormatWidth = FluidFormatWidth.Short,
  ): String {
    val delta = instant.toEpochMilli() - now.toEpochMilli()
    val formatter = RelativeDateTimeFormatter.getInstance(
      ULocale.forLocale(locale),
      null,
      when (width) {
        FluidFormatWidth.Wide -> RelativeDateTimeFormatter.Style.LONG
        FluidFormatWidth.Short -> RelativeDateTimeFormatter.Style.SHORT
        FluidFormatWidth.Narrow -> RelativeDateTimeFormatter.Style.NARROW
      },
      DisplayContext.CAPITALIZATION_FOR_MIDDLE_OF_SENTENCE,
    )
    val step = relativeStep(abs(delta))
      ?: return formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
    val count = (abs(delta) / step.millis).toDouble()
    val direction = if (delta < 0) RelativeDateTimeFormatter.Direction.LAST else RelativeDateTimeFormatter.Direction.NEXT
    return formatter.format(count, direction, step.unit)
  }

  /**
   * Fra quanti millisecondi, da [now], il testo di [relative] cambia: il passo giusto per un testo vivo, che così batte una
   * volta al minuto a «12 min fa», una volta all'ora a «3 h fa» e non a ogni fotogramma. Sempre almeno 1 ms.
   */
  fun relativeNextChangeMillis(instant: Instant, now: Instant): Long {
    val delta = instant.toEpochMilli() - now.toEpochMilli()
    val age = abs(delta)
    val unit = relativeStep(age)?.millis ?: Minute
    return if (delta <= 0L) {
      // Passato: il numero cresce al prossimo multiplo dell'unità, o l'unità cambia alla soglia dopo (28 giorni sono
      // «4 settimane», 30 sono già «1 mese»): vince la più vicina.
      var next = unit - age % unit
      for (threshold in Thresholds) if (threshold > age) {
        next = minOf(next, threshold - age)
        break
      }
      next.coerceAtLeast(1L)
    } else {
      // Futuro: il numero scende quando quel che manca scende sotto il multiplo che si vede.
      (age % unit + 1L).coerceAtLeast(1L)
    }
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Byte
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * Un peso: «532 B», «2,4 MB», «1,1 GB», come `ByteCountFormatStyle` di Apple: una cifra decimale al massimo, niente
   * gruppi, e mai «1000 KB» quando l'arrotondamento tocca l'unità dopo. È la stessa regola di `fluidFormatFileSize`
   * dell'interfaccia, che ora la chiama.
   */
  fun bytes(count: Long, style: FluidByteStyle = FluidByteStyle.File, locale: Locale = Locale.getDefault()): String {
    require(count >= 0) { "Un peso non può essere negativo: $count" }
    val base = if (style == FluidByteStyle.File) 1000.0 else 1024.0
    var unit = if (count < base) 0 else (ln(count.toDouble()) / ln(base)).toInt().coerceAtMost(ByteUnits.lastIndex)
    if (unit < ByteUnits.lastIndex && count / base.pow(unit) >= base - 0.05) unit++
    val number = java.text.NumberFormat.getNumberInstance(locale).apply {
      maximumFractionDigits = if (unit == 0) 0 else 1
      isGroupingUsed = false
    }
    return "${number.format(count / base.pow(unit))} ${ByteUnits[unit]}"
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Liste
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * Un elenco di nomi: «Anna, Luca e Marco», «Anna, Luca, and Marco», come `ListFormatStyle(.and)` di Apple
   * (`ListFormatter` delle ICU, API 26). Con [limit] i nomi oltre il limite diventano «altri 3» («3 others»), ed è
   * l'ultima voce della stessa lista: «Anna, Luca e altri 3».
   *
   * @param others la voce del resto: di serie le parole per italiano, inglese, francese, spagnolo e tedesco, «+3»
   *   nelle altre lingue. Un'app tradotta in un'altra lingua passa la sua.
   */
  fun list(
    items: List<String>,
    locale: Locale = Locale.getDefault(),
    limit: Int = Int.MAX_VALUE,
    others: (Int) -> String = { n -> defaultOthers(n, locale) },
  ): String {
    require(limit >= 1) { "Il limite è almeno 1: $limit" }
    if (items.isEmpty()) return ""
    val shown = if (items.size > limit) items.take(limit) + others(items.size - limit) else items
    return ListFormatter.getInstance(locale).format(shown)
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Durate
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * Una durata in parole: «1 h e 25 min», «1 ora e 25 minuti», «45 s», come `Duration.UnitsFormatStyle` di Apple
   * (`MeasureFormat` delle ICU). Mostra al più [maxUnits] unità, dalla più grande non nulla, e tronca le altre:
   * 1 h 25 min 40 s con due unità è «1 h e 25 min». Zero è «0 s».
   */
  fun duration(
    duration: Duration,
    locale: Locale = Locale.getDefault(),
    width: FluidFormatWidth = FluidFormatWidth.Short,
    maxUnits: Int = 2,
  ): String {
    require(maxUnits >= 1) { "Almeno un'unità: $maxUnits" }
    val total = abs(duration.seconds)
    val parts = listOf(
      total / 86_400 to MeasureUnit.DAY,
      total % 86_400 / 3_600 to MeasureUnit.HOUR,
      total % 3_600 / 60 to MeasureUnit.MINUTE,
      total % 60 to MeasureUnit.SECOND,
    )
    val first = parts.indexOfFirst { it.first > 0 }
    val measures = if (first < 0) {
      listOf(Measure(0, MeasureUnit.SECOND))
    } else {
      parts.drop(first).take(maxUnits).filter { it.first > 0 }.map { Measure(it.first, it.second) }
    }
    val format = MeasureFormat.getInstance(
      ULocale.forLocale(locale),
      when (width) {
        FluidFormatWidth.Wide -> MeasureFormat.FormatWidth.WIDE
        FluidFormatWidth.Short -> MeasureFormat.FormatWidth.SHORT
        FluidFormatWidth.Narrow -> MeasureFormat.FormatWidth.NARROW
      },
    )
    val text = format.formatMeasures(*measures.toTypedArray())
    return if (duration.isNegative) "-$text" else text
  }

  /**
   * Una durata da cronometro: «0:07», «25:07», «1:25:07», come `Duration.TimeFormatStyle` (`.hourMinuteSecond`) e
   * `Text(timerInterval:)` di Apple: i minuti senza zero davanti sotto l'ora, le ore senza zero davanti. Si arrotonda
   * per eccesso al secondo, come un conto alla rovescia che dice «0:01» fino all'ultimo istante. Le cifre sono quelle
   * della lingua.
   */
  fun timer(duration: Duration, locale: Locale = Locale.getDefault()): String {
    val millis = abs(duration.toMillis())
    val total = (millis + 999) / 1000
    val hours = total / 3_600
    val minutes = total % 3_600 / 60
    val seconds = total % 60
    val digits = java.text.NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    val two = java.text.NumberFormat.getIntegerInstance(locale).apply {
      isGroupingUsed = false
      minimumIntegerDigits = 2
    }
    val body = if (hours > 0) {
      "${digits.format(hours)}:${two.format(minutes)}:${two.format(seconds)}"
    } else {
      "${digits.format(minutes)}:${two.format(seconds)}"
    }
    return if (duration.isNegative && total > 0) "-$body" else body
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Numeri
  // ------------------------------------------------------------------------------------------------------------------

  /** Un numero con i separatori della lingua: «1.234,5». [fractionDigits] fissa le cifre decimali (di serie 0–2). */
  fun number(value: Double, locale: Locale = Locale.getDefault(), fractionDigits: Int? = null): String =
    NumberFormat.getNumberInstance(locale).apply {
      if (fractionDigits != null) {
        minimumFractionDigits = fractionDigits
        maximumFractionDigits = fractionDigits
      } else {
        maximumFractionDigits = 2
      }
    }.format(value)

  /** Una frazione come percentuale: 0,425 è «43%» (o «42,5%» con una cifra), con la forma della lingua. */
  fun percent(fraction: Double, locale: Locale = Locale.getDefault(), fractionDigits: Int = 0): String =
    NumberFormat.getPercentInstance(locale).apply {
      minimumFractionDigits = fractionDigits
      maximumFractionDigits = fractionDigits
    }.format(fraction)

  /**
   * Un numero grande in breve: «1,2 Mln», «3,4 Mrd» in italiano, «1.2K» in inglese, come `.notation(.compactName)` di
   * Apple (`CompactDecimalFormat` delle ICU, stesse tabelle CLDR). In italiano le migliaia non si abbreviano: CLDR scrive
   * «1200», come iOS.
   */
  fun compact(value: Double, locale: Locale = Locale.getDefault(), width: FluidFormatWidth = FluidFormatWidth.Short): String =
    CompactDecimalFormat.getInstance(
      locale,
      if (width == FluidFormatWidth.Wide) CompactDecimalFormat.CompactStyle.LONG else CompactDecimalFormat.CompactStyle.SHORT,
    ).format(value)

  // ------------------------------------------------------------------------------------------------------------------

  private const val Second = 1_000L
  private const val Minute = 60 * Second
  private const val Hour = 60 * Minute
  private const val Day = 24 * Hour
  private const val Week = 7 * Day
  private const val Month = 30 * Day
  private const val Year = 365 * Day

  private val ByteUnits = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")

  private class RelativeStep(val millis: Long, val unit: RelativeDateTimeFormatter.RelativeUnit)

  private val Steps = listOf(
    RelativeStep(Year, RelativeDateTimeFormatter.RelativeUnit.YEARS),
    RelativeStep(Month, RelativeDateTimeFormatter.RelativeUnit.MONTHS),
    RelativeStep(Week, RelativeDateTimeFormatter.RelativeUnit.WEEKS),
    RelativeStep(Day, RelativeDateTimeFormatter.RelativeUnit.DAYS),
    RelativeStep(Hour, RelativeDateTimeFormatter.RelativeUnit.HOURS),
    RelativeStep(Minute, RelativeDateTimeFormatter.RelativeUnit.MINUTES),
  )

  /** Le soglie dove l'unità cambia, in salita. */
  private val Thresholds = longArrayOf(Minute, Hour, Day, Week, Month, Year)

  /** L'unità che si mostra per uno scarto di [age] ms, o null sotto il minuto («ora»). Le settimane vanno fino a 4. */
  private fun relativeStep(age: Long): RelativeStep? = if (age < Minute) null else Steps.first { age >= it.millis }

  private fun icuDateFormat(dateStyle: Int, timeStyle: Int, locale: Locale, zone: ZoneId): DateFormat {
    val format = if (dateStyle == DateFormat.NONE) {
      DateFormat.getTimeInstance(timeStyle, locale)
    } else {
      DateFormat.getDateTimeInstance(dateStyle, timeStyle, locale)
    }
    format.timeZone = android.icu.util.TimeZone.getTimeZone(TimeZone.getTimeZone(zone).id)
    return format
  }

  private fun skeletonPattern(skeleton: String, locale: Locale): String =
    android.icu.text.DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton)

  /** «altri 3», «3 others», «3 autres», «otros 3», «3 weitere», o «+3». */
  internal fun defaultOthers(count: Int, locale: Locale): String = when (locale.language) {
    "it" -> if (count == 1) "un altro" else "altri $count"
    "en" -> if (count == 1) "1 other" else "$count others"
    "fr" -> if (count == 1) "1 autre" else "$count autres"
    "es" -> if (count == 1) "otro" else "otros $count"
    "de" -> if (count == 1) "1 weitere Person" else "$count weitere"
    else -> "+$count"
  }
}
