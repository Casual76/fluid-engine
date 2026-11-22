package dev.antigravity.fluidengine.viewer.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Una proprietà di iCalendar o vCard: `DTSTART;TZID=Europe/Rome:20261124T100000`. */
internal class IcsProperty(val name: String, val params: Map<String, String>, val value: String) {
  fun param(key: String): String? = params[key.uppercase(Locale.ROOT)]
}

/** Un componente (`VEVENT`, `VTODO`, `VTIMEZONE`, `VALARM`…) con le sue proprietà e i componenti dentro. */
internal class IcsComponent(val name: String) {
  val properties = ArrayList<IcsProperty>()
  val children = ArrayList<IcsComponent>()
  fun first(name: String): IcsProperty? = properties.firstOrNull { it.name == name }
  fun all(name: String): List<IcsProperty> = properties.filter { it.name == name }
}

/** Un istante di un evento: la data, l'ora (`null` per un giorno intero) e il fuso come dichiarato. */
internal class IcsTime(
  val date: LocalDate,
  val time: LocalTime?,
  val zone: ZoneId?,
  /** Il nome del fuso come nel file («Europe/Rome», «W. Europe Standard Time»), `null` per UTC o un'ora fluttuante. */
  val zoneName: String?,
) {
  val allDay: Boolean get() = time == null
  /** Il momento vero, nel fuso del file (o in quello del dispositivo per un'ora fluttuante). */
  fun zoned(device: ZoneId): ZonedDateTime = ZonedDateTime.of(date, time ?: LocalTime.MIDNIGHT, zone ?: device)
}

/** Un evento o un'attività, pronto per la scheda. */
internal class DataCalendarEvent(
  val todo: Boolean,
  val summary: String?,
  val description: String?,
  val location: String?,
  val start: IcsTime?,
  val end: IcsTime?,
  val repeat: String?,
  val exceptions: Int,
  val organizer: String?,
  val attendees: List<String>,
  val status: String?,
  val url: String?,
  val alarms: List<String>,
  val categories: List<String>,
  val percent: Int?,
  val color: String?,
)

/** Un calendario letto. */
internal class DataCalendar(
  val name: String?,
  val product: String?,
  val version: String?,
  val method: String?,
  val zones: List<String>,
  val events: List<DataCalendarEvent>,
  val journals: Int,
  val unknownZones: Set<String>,
)

/**
 * F6C.13 · Il lettore iCalendar (RFC 5545), in Kotlin puro (§1.3): le righe piegate, i parametri fra virgolette, i
 * testi con gli escape, VEVENT e VTODO, i fusi con `java.time` (gli ID IANA, i nomi di Windows più comuni, e se no lo
 * scostamento del VTIMEZONE del file), le giornate intere (`VALUE=DATE`, la fine esclusa), e la regola di ripetizione
 * detta in italiano per i casi comuni. La stessa piegatura serve a vCard ([DataVcard]).
 */
internal object DataIcs {

  /** Le righe logiche: una riga che comincia con spazio o tabulazione continua la precedente. */
  fun unfold(text: String): List<String> {
    val out = ArrayList<String>()
    for (raw in text.split('\n')) {
      val line = raw.removeSuffix("\r")
      if (line.isEmpty()) continue
      if ((line[0] == ' ' || line[0] == '\t') && out.isNotEmpty()) out[out.lastIndex] = out.last() + line.substring(1)
      else out += line
    }
    return out
  }

  /** Una riga in nome, parametri (le chiavi in maiuscolo) e valore; il gruppo di vCard («item1.TEL») si toglie. */
  fun property(line: String): IcsProperty? {
    var quoted = false
    var colon = -1
    for (i in line.indices) {
      val c = line[i]
      if (c == '"') quoted = !quoted
      else if (c == ':' && !quoted) {
        colon = i
        break
      }
    }
    if (colon <= 0) return null
    val head = line.substring(0, colon)
    val value = line.substring(colon + 1)
    val parts = ArrayList<String>()
    val current = StringBuilder()
    quoted = false
    for (c in head) {
      if (c == '"') quoted = !quoted
      if (c == ';' && !quoted) {
        parts += current.toString()
        current.setLength(0)
      } else {
        current.append(c)
      }
    }
    parts += current.toString()
    val name = parts[0].substringAfterLast('.').trim().uppercase(Locale.ROOT)
    val params = LinkedHashMap<String, String>()
    for (p in parts.drop(1)) {
      val eq = p.indexOf('=')
      if (eq < 0) {
        // vCard 2.1: «TEL;CELL;VOICE:» sono tipi senza nome.
        val type = p.trim().uppercase(Locale.ROOT)
        when (type) {
          "QUOTED-PRINTABLE", "BASE64", "B" -> params["ENCODING"] = type
          else -> params["TYPE"] = listOfNotNull(params["TYPE"], type).joinToString(",")
        }
      } else {
        val key = p.substring(0, eq).trim().uppercase(Locale.ROOT)
        val v = p.substring(eq + 1).trim().removeSurrounding("\"")
        params[key] = if (key == "TYPE" && params["TYPE"] != null) params["TYPE"] + "," + v else v
      }
    }
    return IcsProperty(name, params, value)
  }

  /** Il testo di un valore: `\n` a capo, `\,` `\;` `\\` i loro caratteri. */
  fun unescape(value: String): String {
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
      val c = value[i]
      if (c == '\\' && i + 1 < value.length) {
        when (val n = value[i + 1]) {
          'n', 'N' -> out.append('\n')
          ',', ';', '\\', ':' -> out.append(n)
          else -> out.append(c).append(n)
        }
        i += 2
      } else {
        out.append(c)
        i++
      }
    }
    return out.toString()
  }

  /** I componenti di un testo iCalendar o vCard. */
  fun components(text: String): List<IcsComponent> {
    val roots = ArrayList<IcsComponent>()
    val stack = ArrayList<IcsComponent>()
    for (line in unfold(text)) {
      val p = property(line) ?: continue
      when (p.name) {
        "BEGIN" -> {
          val c = IcsComponent(p.value.trim().uppercase(Locale.ROOT))
          stack.lastOrNull()?.children?.add(c) ?: roots.add(c)
          stack += c
        }
        "END" -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        else -> stack.lastOrNull()?.properties?.add(p)
      }
    }
    return roots
  }

  // I nomi di Windows più comuni (Outlook li scrive nei TZID): https://github.com/unicode-org/cldr, windowsZones.xml.
  private val WindowsZones = mapOf(
    "W. Europe Standard Time" to "Europe/Berlin", "Central Europe Standard Time" to "Europe/Budapest",
    "Romance Standard Time" to "Europe/Paris", "Central European Standard Time" to "Europe/Warsaw",
    "GMT Standard Time" to "Europe/London", "Greenwich Standard Time" to "Atlantic/Reykjavik",
    "E. Europe Standard Time" to "Europe/Chisinau", "FLE Standard Time" to "Europe/Kiev", "GTB Standard Time" to "Europe/Bucharest",
    "Russian Standard Time" to "Europe/Moscow", "Eastern Standard Time" to "America/New_York",
    "Central Standard Time" to "America/Chicago", "Mountain Standard Time" to "America/Denver",
    "Pacific Standard Time" to "America/Los_Angeles", "Tokyo Standard Time" to "Asia/Tokyo",
    "China Standard Time" to "Asia/Shanghai", "India Standard Time" to "Asia/Kolkata", "UTC" to "UTC",
    "AUS Eastern Standard Time" to "Australia/Sydney", "SA Pacific Standard Time" to "America/Bogota",
    "E. South America Standard Time" to "America/Sao_Paulo", "Arabian Standard Time" to "Asia/Dubai",
  )

  /** Il fuso di un TZID: IANA, poi Windows, poi lo scostamento del VTIMEZONE del file; `null` se nessuno. */
  fun zone(tzid: String, timezones: Map<String, ZoneOffset>): ZoneId? {
    val clean = tzid.trim().removePrefix("/").removeSurrounding("\"")
    try {
      return ZoneId.of(clean)
    } catch (_: Exception) {
    }
    WindowsZones[clean]?.let { return ZoneId.of(it) }
    // «(UTC+01:00) Amsterdam, Berlin…», «Europe/Rome» con prefissi di prodotti («/mozilla.org/20050126_1/Europe/Rome»).
    Regex("""[A-Z][a-z]+/[A-Za-z_]+(?:/[A-Za-z_]+)?""").find(clean)?.value?.let {
      try {
        return ZoneId.of(it)
      } catch (_: Exception) {
      }
    }
    return timezones[clean]
  }

  /** Un DTSTART, DTEND o DUE. `null` se il valore non è una data. */
  fun time(p: IcsProperty, timezones: Map<String, ZoneOffset>, unknown: MutableSet<String>): IcsTime? {
    val v = p.value.trim()
    val dateOnly = p.param("VALUE") == "DATE" || Regex("""^\d{8}$""").matches(v)
    return try {
      if (dateOnly) {
        IcsTime(LocalDate.parse(v.take(8), DateTimeFormatter.BASIC_ISO_DATE), null, null, null)
      } else {
        val local = LocalDateTime.parse(v.removeSuffix("Z").take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        val tzid = p.param("TZID")
        when {
          v.endsWith("Z") -> IcsTime(local.toLocalDate(), local.toLocalTime(), ZoneOffset.UTC, null)
          tzid != null -> {
            val zone = zone(tzid, timezones)
            if (zone == null) unknown += tzid
            IcsTime(local.toLocalDate(), local.toLocalTime(), zone, tzid)
          }
          else -> IcsTime(local.toLocalDate(), local.toLocalTime(), null, null)
        }
      }
    } catch (_: Exception) {
      null
    }
  }

  /** Una durata RFC 5545 (`PT1H30M`, `P1D`, `-PT15M`). */
  fun duration(value: String): Duration? {
    val v = value.trim()
    val negative = v.startsWith("-")
    val body = v.removePrefix("-").removePrefix("+")
    return try {
      val weeks = Regex("""^P(\d+)W$""").find(body)?.groupValues?.get(1)?.toLong()
      val d = if (weeks != null) Duration.ofDays(weeks * 7)
      else if ("T" in body) Duration.parse(body)
      else Duration.ofDays(Period.parse(body).days.toLong())
      if (negative) d.negated() else d
    } catch (_: Exception) {
      null
    }
  }

  fun parse(text: String): DataCalendar {
    val roots = components(text)
    val calendar = roots.firstOrNull { it.name == "VCALENDAR" } ?: IcsComponent("VCALENDAR").also { c -> roots.forEach { c.children += it } }
    // Il VTIMEZONE del file: lo scostamento di STANDARD, per i TZID che java.time non conosce.
    val offsets = HashMap<String, ZoneOffset>()
    val zoneNames = ArrayList<String>()
    for (tz in calendar.children.filter { it.name == "VTIMEZONE" }) {
      val id = tz.first("TZID")?.value?.trim() ?: continue
      zoneNames += id
      val standard = tz.children.firstOrNull { it.name == "STANDARD" } ?: tz.children.firstOrNull()
      standard?.first("TZOFFSETTO")?.value?.trim()?.let { raw ->
        try {
          offsets[id] = ZoneOffset.of(if (raw.length == 5) raw.substring(0, 3) + ":" + raw.substring(3) else raw)
        } catch (_: Exception) {
        }
      }
    }
    val unknown = LinkedHashSet<String>()
    val events = ArrayList<DataCalendarEvent>()
    for (c in calendar.children) {
      if (c.name != "VEVENT" && c.name != "VTODO") continue
      val start = c.first("DTSTART")?.let { time(it, offsets, unknown) }
      var end = (c.first("DTEND") ?: c.first("DUE"))?.let { time(it, offsets, unknown) }
      if (end == null && start != null) {
        c.first("DURATION")?.value?.let(::duration)?.let { d ->
          val z = start.zoned(ZoneId.systemDefault()).plus(d)
          end = if (start.allDay) IcsTime(z.toLocalDate(), null, null, null) else IcsTime(z.toLocalDate(), z.toLocalTime(), start.zone, start.zoneName)
        }
      }
      val alarms = c.children.filter { it.name == "VALARM" }.mapNotNull { alarm(it) }
      events += DataCalendarEvent(
        todo = c.name == "VTODO",
        summary = c.first("SUMMARY")?.value?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() },
        description = c.first("DESCRIPTION")?.value?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() },
        location = c.first("LOCATION")?.value?.let(::unescape)?.trim()?.takeIf { it.isNotEmpty() },
        start = start,
        end = end,
        repeat = c.first("RRULE")?.value?.let { describeRule(it, start) },
        exceptions = c.all("EXDATE").sumOf { it.value.split(',').size },
        organizer = c.first("ORGANIZER")?.let { person(it) },
        attendees = c.all("ATTENDEE").map { person(it) },
        status = c.first("STATUS")?.value?.trim()?.let(::status),
        url = c.first("URL")?.value?.trim(),
        alarms = alarms,
        categories = c.all("CATEGORIES").flatMap { unescape(it.value).split(',') }.map { it.trim() }.filter { it.isNotEmpty() },
        percent = c.first("PERCENT-COMPLETE")?.value?.trim()?.toIntOrNull(),
        color = c.first("COLOR")?.value?.trim(),
      )
    }
    val device = ZoneId.systemDefault()
    events.sortWith(compareBy<DataCalendarEvent, java.time.Instant?>(nullsLast()) { e -> e.start?.zoned(device)?.toInstant() })
    return DataCalendar(
      name = calendar.first("X-WR-CALNAME")?.value?.let(::unescape) ?: calendar.first("NAME")?.value?.let(::unescape),
      product = calendar.first("PRODID")?.value,
      version = calendar.first("VERSION")?.value,
      method = calendar.first("METHOD")?.value,
      zones = zoneNames,
      events = events,
      journals = calendar.children.count { it.name == "VJOURNAL" },
      unknownZones = unknown,
    )
  }

  private fun person(p: IcsProperty): String {
    val name = p.param("CN")?.trim('"')
    val address = p.value.trim().removePrefix("mailto:").removePrefix("MAILTO:")
    return if (name.isNullOrBlank()) address else "$name <$address>"
  }

  private fun status(value: String): String = when (value.uppercase(Locale.ROOT)) {
    "CONFIRMED" -> "Confermato"
    "TENTATIVE" -> "Provvisorio"
    "CANCELLED" -> "Annullato"
    "COMPLETED" -> "Completata"
    "NEEDS-ACTION" -> "Da fare"
    "IN-PROCESS" -> "In corso"
    else -> value
  }

  private fun alarm(c: IcsComponent): String? {
    val trigger = c.first("TRIGGER") ?: return null
    if (trigger.param("VALUE") == "DATE-TIME") return "Avviso il ${trigger.value}"
    val d = duration(trigger.value) ?: return null
    val minutes = -d.toMinutes()
    val after = minutes < 0
    val m = kotlin.math.abs(minutes)
    val text = when {
      m == 0L -> return "Avviso all'inizio"
      m % (60 * 24 * 7) == 0L -> plural(m / (60 * 24 * 7), "settimana", "settimane")
      m % (60 * 24) == 0L -> plural(m / (60 * 24), "giorno", "giorni")
      m % 60 == 0L -> plural(m / 60, "ora", "ore")
      else -> plural(m, "minuto", "minuti")
    }
    return "Avviso $text ${if (after) "dopo" else "prima"}"
  }

  private fun plural(n: Long, one: String, many: String) = if (n == 1L) "1 $one" else "$n $many"

  private val Days = mapOf(
    "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
    "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
  )
  private val Italian = Locale.ITALIAN

  private fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Italian)
  private fun monthName(m: Int) = java.time.Month.of(m).getDisplayName(TextStyle.FULL, Italian)
  private fun ordinal(n: Int): String = when (n) {
    1 -> "primo"; 2 -> "secondo"; 3 -> "terzo"; 4 -> "quarto"; 5 -> "quinto"; -1 -> "ultimo"; -2 -> "penultimo"
    else -> "$n°"
  }
  private fun joinItalian(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " e " + items.last()
  }

  /** Il giorno della settimana con l'articolo giusto: «il lunedì», «la domenica». */
  private fun withArticle(d: DayOfWeek) = (if (d == DayOfWeek.SUNDAY) "la " else "il ") + dayName(d)

  /**
   * La regola di ripetizione in italiano, per i casi comuni: «Ogni settimana il lunedì e il mercoledì, fino al 30 giugno
   * 2027», «Ogni mese l'ultimo venerdì, per 10 volte», «Ogni anno il 3 marzo». Una parte che non sa dire si aggiunge com'è.
   */
  fun describeRule(rule: String, start: IcsTime?): String {
    val parts = rule.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase(Locale.ROOT) to it[1] } }.toMap()
    val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val byDay = parts["BYDAY"]?.split(',')?.map { it.trim().uppercase(Locale.ROOT) }.orEmpty()
    val setPos = parts["BYSETPOS"]?.toIntOrNull()
    val byMonthDay = parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
    val byMonth = parts["BYMONTH"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
    fun every(one: String, many: String) = if (interval == 1) "Ogni $one" else "Ogni $interval $many"
    fun dayPhrase(code: String): String? {
      val m = Regex("""^([+-]?\d+)?([A-Z]{2})$""").find(code) ?: return null
      val day = Days[m.groupValues[2]] ?: return null
      val n = m.groupValues[1].toIntOrNull() ?: setPos ?: return withArticle(day)
      val feminine = day == DayOfWeek.SUNDAY
      val word = ordinal(n).let { if (feminine && it.endsWith("o")) it.dropLast(1) + "a" else it }
      val article = when {
        word.startsWith("ultim") -> "l'"
        feminine -> "la "
        else -> "il "
      }
      return "$article$word ${dayName(day)}"
    }
    val text = StringBuilder()
    when (parts["FREQ"]?.uppercase(Locale.ROOT)) {
      "DAILY" -> text.append(every("giorno", "giorni"))
      "WEEKLY" -> {
        val days = byDay.mapNotNull { Days[it.takeLast(2)] }.ifEmpty { listOfNotNull(start?.date?.dayOfWeek) }.distinct().sorted()
        val weekdays = days == listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        if (weekdays && interval == 1) text.append("Ogni giorno feriale")
        else {
          text.append(every("settimana", "settimane"))
          if (days.isNotEmpty()) text.append(" ").append(joinItalian(days.map(::withArticle)))
        }
      }
      "MONTHLY" -> {
        text.append(every("mese", "mesi"))
        when {
          byDay.isNotEmpty() -> joinItalian(byDay.mapNotNull(::dayPhrase)).takeIf { it.isNotEmpty() }?.let { text.append(" ").append(it) }
          byMonthDay.isNotEmpty() -> text.append(if (byMonthDay == listOf(-1)) " l'ultimo giorno" else " il giorno " + joinItalian(byMonthDay.map { it.toString() }))
          start != null -> text.append(" il giorno ").append(start.date.dayOfMonth)
        }
      }
      "YEARLY" -> {
        text.append(every("anno", "anni"))
        val month = byMonth.firstOrNull() ?: start?.date?.monthValue
        val day = byMonthDay.firstOrNull() ?: start?.date?.dayOfMonth
        if (byDay.isNotEmpty() && month != null) text.append(" ").append(joinItalian(byDay.mapNotNull(::dayPhrase))).append(" di ").append(monthName(month))
        else if (month != null && day != null) text.append(" il $day ${monthName(month)}")
      }
      "HOURLY" -> text.append(every("ora", "ore"))
      "MINUTELY" -> text.append(every("minuto", "minuti"))
      else -> return "Si ripete ($rule)"
    }
    parts["COUNT"]?.toIntOrNull()?.let { text.append(if (it == 1) ", una volta" else ", per $it volte") }
    parts["UNTIL"]?.let { until ->
      val date = try {
        LocalDate.parse(until.take(8), DateTimeFormatter.BASIC_ISO_DATE)
      } catch (_: Exception) {
        null
      }
      if (date != null) text.append(", fino al ").append(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Italian)))
    }
    return text.toString()
  }
}
