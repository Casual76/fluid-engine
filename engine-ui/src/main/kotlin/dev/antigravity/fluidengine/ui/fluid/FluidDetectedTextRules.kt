package dev.antigravity.fluidengine.ui.fluid

import java.net.URI
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.TreeMap

/** Un tipo di dato che [FluidDetectedText] può rendere azionabile. */
enum class FluidDetectedTextKind { Date, Time, Address, Phone, Email, Link }

/**
 * Un dato che l'app può usare nelle sue azioni. [start] e [end] sono gli indici UTF-16
 * (inizio incluso, fine esclusa) nel testo mostrato. [text] conserva la grafia originale,
 * [value] è la destinazione normalizzata per telefoni e link.
 *
 * Un giorno senza ora ha [time] nullo. Un orario da solo usa il giorno del riconoscimento
 * in [date]. Una data seguita da un orario riconosciuto li porta entrambi.
 */
data class FluidDetectedValue internal constructor(
  val kind: FluidDetectedTextKind,
  val start: Int,
  val end: Int,
  val text: String,
  val value: String = text,
  val date: LocalDate? = null,
  val time: LocalTime? = null,
)

private const val WordStart = """(?<![\p{L}\p{N}_])"""
private const val WordEnd = """(?![\p{L}\p{N}_])"""
private const val Space = """[ \t\u00a0]"""
private const val Clock = """(\d{1,2})(?:[:.](\d{2}))?(?:[ \t]*([ap]\.?m)\.?)?(?![:.,]\d)"""
private const val DigitsClock = """(\d{1,2}):(\d{2})(?![:.,]\d)"""
private val urlPattern = Regex("""$WordStart(?:https?://|www\.)[^\s<>"“”]+""", RegexOption.IGNORE_CASE)
private val emailPattern = Regex("""(?<![\w.@+-])[\w.%+-]+@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,63}(?![\w@-])""", RegexOption.IGNORE_CASE)
private val phonePattern = Regex("""(?<![\p{L}\p{N}_.,/+\-])\+?\(?\d(?:[(). \u00a0-]{0,3}\d){6,14}(?![\p{L}\p{N}_/]|[.,-]\d)""")
private val phoneCue = Regex("""\b(?:tel(?:efono)?|cell(?:ulare)?|chiama|phone|call|mobile|telephone)\.?$Space*[:.]?$Space*$""", RegexOption.IGNORE_CASE)
private val groupedNumber = Regex("""\d{1,3}(?:[. \u00a0]\d{3}){2,4}""")
private val dottedQuad = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
private const val StreetWord = """[\p{L}][\p{L}'’.-]*"""
private val italianStreet = Regex(
  """$WordStart(via|viale|vicolo|piazza|piazzale|corso|largo)$Space+((?:$StreetWord$Space+){0,4}$StreetWord)((?:,$Space*|$Space+)(n\.$Space*)?\d{1,5}([/-][\p{L}\d]{1,3})?)$WordEnd""",
  RegexOption.IGNORE_CASE,
)

/**
 * «via», «corso» e «largo» sono anche parole di tutti i giorni («via posta il 12», «corso di inglese 10»,
 * «largo di 3 metri»): con loro serve un nome con la maiuscola («via Roma 12») o un segno di civico (la
 * virgola, «n.», «12/A»). Le altre (viale, piazza…) bastano da sole (R2).
 */
private fun MatchResult.isPlausibleItalianStreet(): Boolean {
  if (groupValues[1].lowercase(Locale.ROOT) !in ambiguousStreetWords) return true
  val names = groupValues[2].split(' ', '\t', '\u00a0')
  if (names.any { it.firstOrNull()?.isUpperCase() == true }) return true
  // «n.» puo' finire fra le parole del nome (e' una parola anche lei): conta come segno di civico.
  val civic = groupValues[3]
  return civic.startsWith(",") || groups[4] != null || groups[5] != null || names.last().equals("n.", ignoreCase = true)
}

private val ambiguousStreetWords = setOf("via", "corso", "largo")
private val englishStreet = Regex(
  """$WordStart\d{1,5}[a-z]?$Space+(?:[\p{L}][\p{L}'’-]*$Space+){1,5}(?:Street|Road|Avenue|Lane|Drive|Boulevard|Court|Way|St|Rd|Ave|Ln|Dr|Blvd|Ct)$WordEnd""",
  RegexOption.IGNORE_CASE,
)

internal fun detectTextRules(
  text: String,
  locale: Locale,
  today: LocalDate,
  kinds: Set<FluidDetectedTextKind>,
): List<FluidDetectedValue> {
  val values = DetectedRanges()
  // Gli indirizzi web e mail possiedono l'intero intervallo, anche se l'app ha spento quei tipi.
  // Una stringa come https://example.org/2026-11-28 non contiene un appuntamento autonomo.
  for (match in urlPattern.findAll(text)) {
    val raw = trimWebLink(match.value)
    normalizedWebLink(raw)?.let { values.add(FluidDetectedValue(FluidDetectedTextKind.Link, match.range.first, match.range.first + raw.length, raw, it)) }
  }
  for (match in emailPattern.findAll(text)) {
    values.add(match.detected(FluidDetectedTextKind.Email))
  }
  val grammar = when (locale.language) {
    "it" -> italianDates
    "en" -> if (locale.country in monthFirstCountries) americanDates else englishDates
    else -> null
  }
  if (FluidDetectedTextKind.Date in kinds || FluidDetectedTextKind.Time in kinds) {
    grammar?.addTo(text, today, kinds, values)
  }
  if (FluidDetectedTextKind.Address in kinds) {
    val street = when (locale.language) {
      "it" -> italianStreet
      "en" -> englishStreet
      else -> null
    }
    street?.findAll(text)?.forEach {
      if (street !== italianStreet || it.isPlausibleItalianStreet()) values.add(it.detected(FluidDetectedTextKind.Address))
    }
  }
  if (FluidDetectedTextKind.Phone in kinds) {
    for (match in phonePattern.findAll(text)) {
      val start = match.range.first
      val end = match.range.last + 1
      normalizedPhone(text, start, end)?.let { values.add(match.detected(FluidDetectedTextKind.Phone, it)) }
    }
  }
  return values.toList().filter { it.kind in kinds }
}

private val monthFirstCountries = setOf("", "US", "PH")

private fun MatchResult.detected(kind: FluidDetectedTextKind, value: String = this.value) =
  FluidDetectedValue(kind, range.first, range.last + 1, this.value, value)

/** Risolve le sovrapposizioni senza scansionare tutti i dati già riconosciuti a ogni nuovo dato. */
internal class DetectedRanges {
  private val byStart = TreeMap<Int, FluidDetectedValue>()

  fun add(value: FluidDetectedValue) {
    if (byStart.floorEntry(value.start)?.value?.end?.let { it > value.start } == true) return
    if (byStart.ceilingKey(value.start)?.let { it < value.end } == true) return
    byStart[value.start] = value
  }

  fun toList(): List<FluidDetectedValue> = byStart.values.toList()
}

private fun trimWebLink(raw: String): String {
  val trimmed = raw.trimEnd('.', ',', ';', ':', '!', '?', '’', '\'', ']', '}')
  val excess = (trimmed.count { it == ')' } - trimmed.count { it == '(' }).coerceAtLeast(0)
  return trimmed.dropLast(minOf(excess, trimmed.length - trimmed.trimEnd(')').length))
}

/**
 * Il link pronto da aprire, o null: solo `http` e `https`, mai con nome e password, sempre con un host
 * che ha un punto. `java.net.URI` rifiuta i caratteri che non conosce (`|`, `^`…): si riprova con quelli
 * codificati. Un host con lettere accentate (`città.it`) o col trattino basso non e' un nome che `URI`
 * sappia leggere (l'host resta nullo): lo si legge dall'autorita' e lo si controlla qui (R2). Il valore
 * resta quello scritto: e' cio' che si apre.
 */
internal fun normalizedWebLink(raw: String): String? {
  val target = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
  val uri = parseLenientUri(target) ?: return null
  if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https") || uri.userInfo != null) return null
  val host = uri.host ?: registryHost(uri.rawAuthority) ?: return null
  if (!host.contains('.')) return null
  return target
}

private fun parseLenientUri(target: String): URI? = try {
  URI(target)
} catch (_: java.net.URISyntaxException) {
  try {
    URI(encodeUriIllegal(target))
  } catch (_: java.net.URISyntaxException) {
    null
  }
}

/** I caratteri ASCII che `URI` non accetta, in `%XX`; il resto (anche le lettere accentate) com'e'. */
private fun encodeUriIllegal(text: String): String = buildString(text.length) {
  for (char in text) {
    if (char.code in 0x21..0x7e && char !in UriIllegalAscii) append(char)
    else if (char.code < 0x80) append('%').append("%02X".format(char.code))
    else append(char)
  }
}

private const val UriIllegalAscii = "\"<>\\^`{|}"

/**
 * L'host di un'autorita' che `URI` non sa leggere come nome di server, se e' un nome credibile: niente
 * nome e password, lettere (anche accentate), cifre, `-`, `_` e punti, senza punti in testa, in coda o
 * doppi. La porta, se c'e', si toglie.
 */
private fun registryHost(authority: String?): String? {
  if (authority.isNullOrEmpty() || '@' in authority) return null
  val host = authority.substringBeforeLast(':').takeIf { authority.substringAfterLast(':', "").all(Char::isDigit) } ?: authority
  if (host.isEmpty() || host.startsWith('.') || host.endsWith('.') || ".." in host) return null
  if (!host.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) return null
  return host
}

internal fun isDetectedEmail(raw: String): Boolean = emailPattern.matches(raw)

internal fun normalizedPhone(text: String, start: Int, end: Int): String? {
  val raw = text.substring(start, end)
  if (!phonePattern.matches(raw) || dottedQuad.matches(raw)) return null
  if (text.getOrNull(start - 1)?.isLetterOrDigit() == true || text.getOrNull(end)?.isLetterOrDigit() == true) return null
  val digits = raw.filter(Char::isDigit)
  val international = raw.startsWith('+')
  val prefix = text.substring(maxOf(0, start - 24), start)
  val hasCue = phoneCue.containsMatchIn(prefix)
  if (prefix.trimEnd().lastOrNull() in listOf('€', '$', '£', '¥')) return null
  if (text.getOrNull(end) in listOf('%', '€', '$', '£', '¥')) return null
  // Un blocco di cifre senza contesto può essere un codice, un importo o un numero di pratica.
  // Un punto solo è tipico di un decimale o di un raggruppamento delle migliaia.
  if (!international && !hasCue && (digits.length !in 9..11 || raw.all(Char::isDigit) || groupedNumber.matches(raw))) return null
  if (raw.count { it == '.' } == 1 && raw.all { it == '.' || it.isDigit() }) return null
  if (international && digits.length < 8) return null
  return if (international) "+$digits" else digits
}

private class DateRule(
  val pattern: Regex,
  val resolve: (MatchResult, LocalDate) -> LocalDate?,
)

private class CalendarGrammar(
  private val dateRules: List<DateRule>,
  private val timeAfter: Regex,
  private val times: List<Regex>,
) {
  fun addTo(text: String, today: LocalDate, kinds: Set<FluidDetectedTextKind>, ranges: DetectedRanges) {
    // Compone prima data e orario, poi aggiunge solo gli orari rimasti da soli.
    for (rule in dateRules) {
      for (match in rule.pattern.findAll(text)) {
        val date = rule.resolve(match, today) ?: continue
        val dateEnd = match.range.last + 1
        val attached = timeAfter.matchAt(text, dateEnd)
        val time = attached?.let(::readTime).takeIf { FluidDetectedTextKind.Time in kinds }
        if (FluidDetectedTextKind.Date in kinds) {
          val end = if (time != null && attached != null) attached.range.last + 1 else dateEnd
          ranges.add(FluidDetectedValue(FluidDetectedTextKind.Date, match.range.first, end, text.substring(match.range.first, end), date = date, time = time))
        } else if (time != null && attached != null) {
          val textTime = attached.value.trimStart()
          val end = attached.range.last + 1
          ranges.add(FluidDetectedValue(FluidDetectedTextKind.Time, end - textTime.length, end, textTime, date = date, time = time))
        }
      }
    }
    if (FluidDetectedTextKind.Time in kinds) {
      for (pattern in times) {
        for (match in pattern.findAll(text)) {
          val time = readTime(match) ?: continue
          ranges.add(FluidDetectedValue(FluidDetectedTextKind.Time, match.range.first, match.range.last + 1, match.value, date = today, time = time))
        }
      }
    }
  }
}

private fun readTime(match: MatchResult): LocalTime? {
  val hour = match.groupValues[1].toInt()
  val minute = match.groupValues[2].toIntOrNull() ?: 0
  val marker = match.groupValues.getOrNull(3).orEmpty().lowercase(Locale.ROOT)
  val adjusted = when {
    marker.isEmpty() -> hour
    hour !in 1..12 -> return null
    marker.startsWith('p') -> hour % 12 + 12
    else -> hour % 12
  }
  return calendarValue { LocalTime.of(adjusted, minute) }
}

private fun dateRule(pattern: String, resolve: (MatchResult, LocalDate) -> LocalDate?): DateRule =
  DateRule(Regex("$WordStart$pattern$WordEnd", RegexOption.IGNORE_CASE), resolve)

private fun timeRule(pattern: String) = Regex("""(?<![\p{L}\p{N}_/:.,-])$pattern(?![\p{L}\p{N}_/])""", RegexOption.IGNORE_CASE)

private val isoDate = dateRule("""(\d{4})-(\d{2})-(\d{2})(?![-/]\d)""") { m, _ ->
  calendarValue { LocalDate.of(m.int(1), m.int(2), m.int(3)) }
}

private val italianDays = words("lunedì martedì mercoledì giovedì venerdì sabato domenica")
private val englishDays = words("monday tuesday wednesday thursday friday saturday sunday")
private val italianMonths = words("gennaio febbraio marzo aprile maggio giugno luglio agosto settembre ottobre novembre dicembre")
private val englishMonths = words("january february march april may june july august september october november december")
private val relativeItalian = mapOf("oggi" to 0L, "domani" to 1L, "dopodomani" to 2L)
private val relativeEnglish = mapOf("today" to 0L, "tomorrow" to 1L)

private val italianDates = CalendarGrammar(
  dateRules = listOf(
    isoDate,
    numericDate("il", monthFirst = false),
    dateRule("""(?:(${alternatives(italianDays)})$Space+)?(\d{1,2})$Space+(${alternatives(italianMonths)})(?:$Space+(\d{4}))?""") { m, today ->
      resolveMonth(today, m.int(2), italianMonths.getValue(m.word(3)), m.year(4), italianDays[m.word(1)])
    },
    dateRule("""(${alternatives(italianDays)})$Space+prossim[oa]""") { m, today ->
      today.with(TemporalAdjusters.next(DayOfWeek.of(italianDays.getValue(m.word(1)))))
    },
    relativeDate(relativeItalian),
    weekdayAndDay(italianDays),
  ),
  timeAfter = Regex("""$Space+(?:alle|all['’]|ore)$Space*$Clock$WordEnd""", RegexOption.IGNORE_CASE),
  times = listOf(timeRule("""(?:alle|all['’]|ore)$Space*$Clock"""), timeRule(DigitsClock)),
)

private fun englishGrammar(monthFirst: Boolean) = CalendarGrammar(
  dateRules = listOf(
    isoDate,
    numericDate("on", monthFirst),
    dateRule("""(?:(${alternatives(englishDays)}),?$Space+)?(${alternatives(englishMonths)})$Space+(\d{1,2})(?:st|nd|rd|th)?(?:,?$Space+(\d{4}))?""") { m, today ->
      resolveMonth(today, m.int(3), englishMonths.getValue(m.word(2)), m.year(4), englishDays[m.word(1)])
    },
    dateRule("""(?:(${alternatives(englishDays)}),?$Space+)?(\d{1,2})(?:st|nd|rd|th)?$Space+(?:of$Space+)?(${alternatives(englishMonths)})(?:$Space+(\d{4}))?""") { m, today ->
      resolveMonth(today, m.int(2), englishMonths.getValue(m.word(3)), m.year(4), englishDays[m.word(1)])
    },
    dateRule("""next$Space+(${alternatives(englishDays)})""") { m, today ->
      today.with(TemporalAdjusters.next(DayOfWeek.of(englishDays.getValue(m.word(1)))))
    },
    relativeDate(relativeEnglish),
    weekdayAndDay(englishDays),
  ),
  timeAfter = Regex("""$Space+at$Space+$Clock$WordEnd""", RegexOption.IGNORE_CASE),
  times = listOf(timeRule("""at$Space+$Clock"""), timeRule("""(\d{1,2})(?::(\d{2}))?$Space*([ap]\.?m)\.?$WordEnd"""), timeRule(DigitsClock)),
)

private val americanDates = englishGrammar(monthFirst = true)
private val englishDates = englishGrammar(monthFirst = false)

private fun words(text: String) = text.split(' ').withIndex().associate { (i, word) -> word to i + 1 }
private fun alternatives(words: Map<String, *>) = words.keys.joinToString("|")
private fun MatchResult.word(group: Int) = groupValues[group].lowercase(Locale.ROOT)
private fun MatchResult.int(group: Int) = groupValues[group].toInt()
private fun MatchResult.year(group: Int) = groupValues[group].toIntOrNull()

private fun relativeDate(offsets: Map<String, Long>) =
  dateRule("""(${alternatives(offsets)})""") { m, today -> today.plusDays(offsets.getValue(m.word(1))) }

private fun numericDate(cue: String, monthFirst: Boolean) =
  dateRule("""(?:($cue)$Space+)?(\d{1,2})/(\d{1,2})(?:/(\d{4}))?(?![/.-]\d)""") { m, today ->
    val year = m.year(4)
    if (year == null && m.word(1).isEmpty()) null
    else {
      val first = m.int(2)
      val second = m.int(3)
      resolveMonth(today, if (monthFirst) second else first, if (monthFirst) first else second, year)
    }
  }

private fun weekdayAndDay(weekdays: Map<String, Int>) =
  dateRule("""(${alternatives(weekdays)})$Space+(\d{1,2})(?:st|nd|rd|th)?(?![/.-]\d)""") { m, today ->
    val day = m.int(2)
    val month = YearMonth.from(today).plusMonths(if (day < today.dayOfMonth) 1 else 0)
    calendarValue { month.atDay(day) }?.takeIf { it.dayOfWeek.value == weekdays[m.word(1)] }
  }

private fun resolveMonth(today: LocalDate, day: Int, month: Int, year: Int?, weekday: Int? = null): LocalDate? {
  val current = calendarValue { LocalDate.of(year ?: today.year, month, day) }
  val date = if (year == null && (current == null || current < today)) {
    calendarValue { LocalDate.of(today.year + 1, month, day) }
  } else current
  return date?.takeIf { weekday == null || it.dayOfWeek.value == weekday }
}

private inline fun <T> calendarValue(block: () -> T): T? = try {
  block()
} catch (_: DateTimeException) {
  null
}
