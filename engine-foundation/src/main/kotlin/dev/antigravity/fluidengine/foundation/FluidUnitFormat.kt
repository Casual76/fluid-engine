package dev.antigravity.fluidengine.foundation

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Scrivere e leggere un numero con la sua unità nella lingua di chi legge (F8.3, `campo-con-unita`), pure e senza
 * Android. È la parte delle `FormatStyle` di Apple che serve a un campo: `Measurement.FormatStyle(width: .abbreviated,
 * usage: .asProvided)` e `Decimal.FormatStyle.Currency` col loro `parse`.
 *
 * - **I separatori** sono quelli della lingua ([separators]): «12,5 km» in italiano, «12.5 km» in inglese; le migliaia
 *   dalle quattro cifre («1.234,5»).
 * - **L'arrotondamento** è al pari (`.toNearestOrEven`, quello di serie di Apple), alle cifre dell'unità.
 * - **Lo spazio fra numero e simbolo** è uno spazio che non va a capo (U+00A0), come nelle `FormatStyle`: il simbolo non
 *   resta mai da solo all'inizio della riga dopo. Le eccezioni sono quelle del CLDR per le lingue che le app usano:
 *   in inglese la valuta sta davanti e attaccata («€12.50») e i gradi sono attaccati («21°C»); in italiano la valuta
 *   sta dopo («12,50 €») e i gradi staccati («21 °C»); la percentuale è attaccata in italiano e in inglese («12,5%») e
 *   staccata in tedesco, francese e spagnolo. Fiducia: alta per italiano e inglese (CLDR 44, verificato sulle
 *   `FormatStyle` di iOS 18), media per le altre.
 * - **La lettura è larga** ([parse]): accetta il separatore della lingua e quello dell'altra convenzione, gli spazi e gli
 *   apostrofi delle migliaia, il segno meno tipografico e il simbolo dell'unità attaccato. Un testo incollato da un'altra
 *   app («1,234.56» in un campo italiano) si legge giusto (scheda dell'idea, «rischi»).
 */
object FluidUnitFormat {
  /** Lo spazio che non va a capo fra il numero e il simbolo. */
  const val NoBreakSpace: Char = ' '

  /** Il separatore decimale e quello delle migliaia di una lingua. */
  data class Separators(val decimal: Char, val grouping: Char)

  /**
   * I separatori di [locale], dalle `DecimalFormatSymbols` della piattaforma. Lo spazio stretto delle migliaia (francese,
   * U+202F) e quello largo restano quelli della lingua: in lettura valgono tutti.
   *
   * **Le cifre sono sempre latine** (0–9), e i gruppi sempre da tre: in una lingua con cifre sue (l'arabo d'Egitto) i
   * separatori sono allora quelli latini, «1,234.5», e non quelli arabi accanto a cifre latine («1٬234٫5», R2). In
   * lettura le cifre e i separatori arabi valgono lo stesso. I gruppi dell'India (3 + 2) si leggono, ma si scrive a tre.
   */
  fun separators(locale: Locale): Separators {
    val symbols = DecimalFormatSymbols.getInstance(locale)
    if (symbols.zeroDigit != '0') return Separators('.', ',')
    return Separators(symbols.decimalSeparator, symbols.groupingSeparator)
  }

  /**
   * [value] scritto nella lingua di [locale], con al più [maxFractionDigits] cifre dopo la virgola (arrotondate al pari) e
   * almeno [minFractionDigits]; le migliaia raggruppate se [grouping]. «1234,5» → «1.234,5» in italiano.
   */
  fun number(
    value: BigDecimal,
    locale: Locale,
    maxFractionDigits: Int,
    minFractionDigits: Int = 0,
    grouping: Boolean = true,
  ): String {
    val sep = separators(locale)
    val max = maxFractionDigits.coerceAtLeast(0)
    val min = minFractionDigits.coerceIn(0, max)
    var rounded = value.setScale(max, RoundingMode.HALF_EVEN)
    // Le cifre finali a zero vanno via fino al minimo: «12,50» resta per l'euro, «12,5» per i chilometri.
    while (rounded.scale() > min && rounded.unscaledValue().mod(java.math.BigInteger.TEN).signum() == 0) {
      rounded = rounded.setScale(rounded.scale() - 1, RoundingMode.UNNECESSARY)
    }
    val negative = rounded.signum() < 0
    val plain = rounded.abs().toPlainString()
    val dot = plain.indexOf('.')
    val integer = if (dot < 0) plain else plain.substring(0, dot)
    val fraction = if (dot < 0) "" else plain.substring(dot + 1)
    val builder = StringBuilder()
    if (negative) builder.append('-')
    builder.append(if (grouping) group(integer, sep.grouping) else integer)
    if (fraction.isNotEmpty()) builder.append(sep.decimal).append(fraction)
    return builder.toString()
  }

  /** Le cifre intere [digits] raggruppate a tre con [separator], dalle quattro cifre in su. */
  fun group(digits: String, separator: Char): String {
    if (digits.length < 4) return digits
    val builder = StringBuilder(digits.length + digits.length / 3)
    val head = digits.length % 3
    digits.forEachIndexed { index, c ->
      if (index > 0 && (index - head) % 3 == 0) builder.append(separator)
      builder.append(c)
    }
    return builder.toString()
  }

  /** Il simbolo di [unit] in [locale]: per una valuta quello della lingua («€», «$», «USD» in italiano), se no il suo. */
  fun symbol(unit: FluidUnit, locale: Locale): String {
    val code = unit.currencyCode ?: return unit.symbol
    return runCatching { java.util.Currency.getInstance(code).getSymbol(locale) }.getOrNull() ?: code
  }

  /**
   * [quantity] scritta come la scriverebbe Apple con le `FormatStyle` abbreviate: «12,5 km», «21 °C», «12,50 €» in
   * italiano; «12.5 km», «21°C», «€12.50» in inglese. Le cifre sono quelle dell'unità, se non le passi.
   */
  fun format(
    quantity: FluidQuantity,
    locale: Locale,
    maxFractionDigits: Int = quantity.unit.fractionDigits,
    minFractionDigits: Int = quantity.unit.minFractionDigits,
  ): String {
    val number = number(quantity.value, locale, maxFractionDigits, minFractionDigits)
    return attach(number, quantity.unit, locale)
  }

  /** Il numero già scritto [number] col simbolo di [unit] al suo posto, secondo le regole della lingua. */
  fun attach(number: String, unit: FluidUnit, locale: Locale): String {
    val symbol = symbol(unit, locale)
    return when (placement(unit, locale, symbol)) {
      Placement.Before -> {
        // «-€12.50» in inglese: il segno resta davanti a tutto.
        if (number.startsWith('-')) "-$symbol${number.substring(1)}" else "$symbol$number"
      }
      Placement.BeforeSpaced -> "$symbol$NoBreakSpace$number"
      Placement.After -> "$number$symbol"
      Placement.AfterSpaced -> "$number$NoBreakSpace$symbol"
    }
  }

  /**
   * Dove sta il simbolo di [unit] in [locale], e se staccato: l'unica parte delle regole CLDR che serve qui. Pubblica
   * per il campo, che mette la capsula dell'unità dallo stesso lato.
   */
  fun symbolBefore(unit: FluidUnit, locale: Locale): Boolean =
    placement(unit, locale, symbol(unit, locale)).let { it == Placement.Before || it == Placement.BeforeSpaced }

  private enum class Placement { Before, BeforeSpaced, After, AfterSpaced }

  private val SpacedPercentLanguages = setOf("de", "fr", "es", "sv", "nb", "no", "da", "fi", "cs", "sk", "pl", "ru", "uk")

  private fun placement(unit: FluidUnit, locale: Locale, symbol: String): Placement {
    val language = locale.language
    return when {
      unit.currencyCode != null -> when {
        language == "en" && symbol.all { it.isLetter() } -> Placement.BeforeSpaced
        language == "en" -> Placement.Before
        else -> Placement.AfterSpaced
      }
      unit.dimension == FluidUnitDimension.Percent ->
        if (language in SpacedPercentLanguages) Placement.AfterSpaced else Placement.After
      unit.symbol.startsWith("°") -> if (language == "en") Placement.After else Placement.AfterSpaced
      else -> Placement.AfterSpaced
    }
  }

  /**
   * Il nome di [unit] in [locale], al singolare o al [plural]: «chilometro», «gradi Celsius». Null per le lingue senza le
   * parole (ci sono italiano e inglese) e per le unità che non sono di serie.
   */
  fun name(unit: FluidUnit, locale: Locale, plural: Boolean = false): String? =
    FluidUnitNames.of(unit, locale)?.let { if (plural) it.second else it.first }

  /**
   * Come la legge TalkBack: «12,5 chilometri», «21 gradi Celsius», «1 metro». Le parole ci sono in italiano e in inglese
   * per le unità di serie; per le altre (e per le valute, che TalkBack legge bene da sé) resta [format].
   */
  fun spoken(quantity: FluidQuantity, locale: Locale): String {
    val names = FluidUnitNames.of(quantity.unit, locale) ?: return format(quantity, locale)
    val number = number(quantity.value, locale, quantity.unit.fractionDigits, quantity.unit.minFractionDigits,
      grouping = false)
    val one = quantity.value.compareTo(BigDecimal.ONE) == 0 || quantity.value.compareTo(BigDecimal.ONE.negate()) == 0
    return "$number ${if (one) names.first else names.second}"
  }

  /**
   * Un numero scritto da una persona o incollato da un'altra app, nella lingua di [locale] e con tolleranza: «1.234,5»,
   * «1 234,5», «1'234.5», «1,234.5», «−3», «12,5 km». Null se non è un numero.
   *
   * La regola dei separatori, da sinistra a destra:
   *  - con **entrambi** `.` e `,` il decimale è l'ultimo dei due («1.234,5» e «1,234.5» sono lo stesso numero);
   *  - con **uno solo**, ripetuto, sono migliaia («1.234.567»);
   *  - con **uno solo**, una volta: se è il decimale della lingua è decimale; se è quello delle migliaia ed è seguito da
   *    esattamente tre cifre sono migliaia («1.500» in italiano è millecinquecento), altrimenti è decimale («1.5» in
   *    italiano è uno virgola cinque, incollato da un'app inglese). Nel dubbio vince la lingua.
   */
  fun parse(text: String, locale: Locale): BigDecimal? {
    val sep = separators(locale)
    val cleaned = stripSymbols(normalize(text)) ?: return null
    return parseNormalized(cleaned, sep)
  }

  /**
   * Come [parse], e in più riconosce il simbolo di una delle [units] attaccato al numero («12,5 km», «€ 12,50»): la
   * quantità ha quell'unità, o [fallback] se il testo non ne porta una. Null se non è un numero o se il simbolo è di
   * un'unità che non è fra le [units].
   */
  fun parseQuantity(text: String, locale: Locale, units: List<FluidUnit>, fallback: FluidUnit): FluidQuantity? {
    val normalized = normalize(text)
    val match = units.asSequence().flatMap { unit -> symbolsOf(unit, locale).map { it to unit } }
      .sortedByDescending { it.first.length }
      .firstOrNull { (symbol, _) -> normalized.startsWith(symbol) || normalized.endsWith(symbol) }
    val rest = if (match == null) normalized else normalized.removePrefix(match.first).removeSuffix(match.first)
    val cleaned = stripSymbols(rest) ?: return null
    // Un simbolo che resta e non è di nessuna delle unità date: non è un numero di questo campo.
    if (match == null && cleaned.length != rest.length && rest.any { it.isLetter() }) return null
    val value = parseNormalized(cleaned, separators(locale)) ?: return null
    return FluidQuantity(value, match?.second ?: fallback)
  }

  private fun symbolsOf(unit: FluidUnit, locale: Locale): List<String> = buildList {
    add(unit.symbol)
    add(symbol(unit, locale))
    unit.currencyCode?.let { add(it) }
    if (unit.symbol.startsWith("°")) add(unit.symbol.removePrefix("°"))
  }.filter { it.isNotEmpty() && !it[0].isDigit() }.distinct()

  /**
   * Gli spazi (anche quelli che non vanno a capo), gli apostrofi delle migliaia e il meno tipografico; le cifre di altre
   * scritture (arabe, persiane, devanagari…) diventano latine, e i separatori arabi (U+066B decimale, U+066C migliaia)
   * punto e virgola latini (R2).
   */
  private fun normalize(text: String): String = buildString {
    for (c in text.trim()) {
      if (c !in '0'..'9' && Character.isDigit(c)) {
        append('0' + Character.digit(c, 10))
        continue
      }
      when (c) {
        '٫' -> append('.')
        '٬' -> append(',')
        ' ', ' ', ' ', ' ', '\'', '’', '_' -> Unit
        '−', '–' -> append('-')
        else -> append(c)
      }
    }
  }

  /** Toglie il simbolo attaccato a una delle due estremità; null se in mezzo c'è altro che cifre e separatori. */
  private fun stripSymbols(text: String): String? {
    if (text.isEmpty()) return null
    val first = text.indexOfFirst { it.isDigit() || it == '.' || it == ',' }
    if (first < 0) return null
    val last = text.indexOfLast { it.isDigit() }
    if (last < first) return null
    val prefix = text.substring(0, first)
    val sign = when {
      prefix.endsWith("-") || prefix.startsWith("-") -> "-"
      else -> ""
    }
    val body = text.substring(first, last + 1)
    if (body.any { !it.isDigit() && it != '.' && it != ',' }) return null
    return sign + body
  }

  private fun parseNormalized(text: String, sep: Separators): BigDecimal? {
    val negative = text.startsWith('-')
    val body = text.removePrefix("-")
    if (body.isEmpty() || body.none { it.isDigit() }) return null
    val dots = body.count { it == '.' }
    val commas = body.count { it == ',' }
    val decimal: Char? = when {
      dots > 0 && commas > 0 -> if (body.lastIndexOf('.') > body.lastIndexOf(',')) '.' else ','
      dots + commas == 0 -> null
      dots > 1 || commas > 1 -> null
      else -> {
        val c = if (dots == 1) '.' else ','
        val at = body.indexOf(c)
        val after = body.length - at - 1
        when {
          c == sep.decimal -> c
          after == 3 && at in 1..3 -> null
          else -> c
        }
      }
    }
    val grouping = if (decimal == null) body.firstOrNull { it == '.' || it == ',' } else if (decimal == '.') ',' else '.'
    if (decimal != null && body.lastIndexOf(decimal) != body.indexOf(decimal)) return null
    if (decimal != null && grouping != null && body.indexOf(grouping).let { it >= 0 && it > body.indexOf(decimal) }) return null
    // Le migliaia sono gruppi veri (R2): il primo da 1 a 3 cifre, l'ultimo da 3, quelli in mezzo da 3 (o da 2, i gruppi
    // dell'India). «1.2.3» non e' 123: non e' un numero.
    if (grouping != null) {
      val integer = if (decimal != null) body.substringBefore(decimal) else body
      if (grouping in integer) {
        val groups = integer.split(grouping)
        val middleOk = groups.subList(1, groups.size - 1).all { it.length == 3 || it.length == 2 }
        if (groups.first().length !in 1..3 || groups.last().length != 3 || !middleOk) return null
      }
    }
    val plain = buildString {
      if (negative) append('-')
      for (c in body) {
        when (c) {
          decimal -> append('.')
          grouping -> Unit
          else -> append(c)
        }
      }
    }
    if (plain.endsWith('.')) return plain.dropLast(1).takeIf { it.isNotEmpty() && it != "-" }?.toBigDecimalOrNull()
    return plain.toBigDecimalOrNull()
  }
}

/**
 * I nomi delle unità di serie per TalkBack, al singolare e al plurale, in italiano e in inglese. Le altre lingue usano
 * il simbolo (che TalkBack sa leggere): una lingua in più è una riga per unità.
 */
internal object FluidUnitNames {
  private val italian = mapOf(
    "mm" to ("millimetro" to "millimetri"), "cm" to ("centimetro" to "centimetri"), "m" to ("metro" to "metri"),
    "km" to ("chilometro" to "chilometri"), "in" to ("pollice" to "pollici"), "ft" to ("piede" to "piedi"),
    "yd" to ("iarda" to "iarde"), "mi" to ("miglio" to "miglia"),
    "g" to ("grammo" to "grammi"), "kg" to ("chilogrammo" to "chilogrammi"), "oz" to ("oncia" to "once"),
    "lb" to ("libbra" to "libbre"),
    "celsius" to ("grado Celsius" to "gradi Celsius"), "fahrenheit" to ("grado Fahrenheit" to "gradi Fahrenheit"),
    "kelvin" to ("kelvin" to "kelvin"),
    "ml" to ("millilitro" to "millilitri"), "l" to ("litro" to "litri"), "m3" to ("metro cubo" to "metri cubi"),
    "gal" to ("gallone" to "galloni"),
    "m2" to ("metro quadrato" to "metri quadrati"), "ha" to ("ettaro" to "ettari"),
    "km2" to ("chilometro quadrato" to "chilometri quadrati"),
    "m/s" to ("metro al secondo" to "metri al secondo"), "km/h" to ("chilometro orario" to "chilometri orari"),
    "mph" to ("miglio orario" to "miglia orarie"), "kn" to ("nodo" to "nodi"),
    "s" to ("secondo" to "secondi"), "min" to ("minuto" to "minuti"), "h" to ("ora" to "ore"),
    "B" to ("byte" to "byte"), "kB" to ("kilobyte" to "kilobyte"), "MB" to ("megabyte" to "megabyte"),
    "GB" to ("gigabyte" to "gigabyte"), "KiB" to ("kibibyte" to "kibibyte"), "MiB" to ("mebibyte" to "mebibyte"),
    "J" to ("joule" to "joule"), "kJ" to ("kilojoule" to "kilojoule"), "kcal" to ("chilocaloria" to "chilocalorie"),
    "kWh" to ("chilowattora" to "chilowattora"),
    "percent" to ("per cento" to "per cento"),
  )

  private val english = mapOf(
    "mm" to ("millimeter" to "millimeters"), "cm" to ("centimeter" to "centimeters"), "m" to ("meter" to "meters"),
    "km" to ("kilometer" to "kilometers"), "in" to ("inch" to "inches"), "ft" to ("foot" to "feet"),
    "yd" to ("yard" to "yards"), "mi" to ("mile" to "miles"),
    "g" to ("gram" to "grams"), "kg" to ("kilogram" to "kilograms"), "oz" to ("ounce" to "ounces"),
    "lb" to ("pound" to "pounds"),
    "celsius" to ("degree Celsius" to "degrees Celsius"), "fahrenheit" to ("degree Fahrenheit" to "degrees Fahrenheit"),
    "kelvin" to ("kelvin" to "kelvins"),
    "ml" to ("milliliter" to "milliliters"), "l" to ("liter" to "liters"), "m3" to ("cubic meter" to "cubic meters"),
    "gal" to ("gallon" to "gallons"),
    "m2" to ("square meter" to "square meters"), "ha" to ("hectare" to "hectares"),
    "km2" to ("square kilometer" to "square kilometers"),
    "m/s" to ("meter per second" to "meters per second"), "km/h" to ("kilometer per hour" to "kilometers per hour"),
    "mph" to ("mile per hour" to "miles per hour"), "kn" to ("knot" to "knots"),
    "s" to ("second" to "seconds"), "min" to ("minute" to "minutes"), "h" to ("hour" to "hours"),
    "B" to ("byte" to "bytes"), "kB" to ("kilobyte" to "kilobytes"), "MB" to ("megabyte" to "megabytes"),
    "GB" to ("gigabyte" to "gigabytes"), "KiB" to ("kibibyte" to "kibibytes"), "MiB" to ("mebibyte" to "mebibytes"),
    "J" to ("joule" to "joules"), "kJ" to ("kilojoule" to "kilojoules"), "kcal" to ("kilocalorie" to "kilocalories"),
    "kWh" to ("kilowatt-hour" to "kilowatt-hours"),
    "percent" to ("percent" to "percent"),
  )

  fun of(unit: FluidUnit, locale: Locale): Pair<String, String>? = when (locale.language) {
    "it" -> italian[unit.id]
    "en" -> english[unit.id]
    else -> null
  }?.takeIf { FluidUnits.byId(unit.id) == unit }
}
