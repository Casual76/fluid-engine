package dev.antigravity.fluidengine.foundation

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/*
 * Le unità e le conversioni del campo con unità (F8.3, idea `campo-con-unita`): una tabella dichiarata e conversioni
 * pure, senza Android. Il campo vive in `engine-ui` (`FluidUnitField`); qui c'è solo l'aritmetica, che è la parte che non
 * deve sbagliare.
 *
 * **Il riferimento Apple** è `Measurement<UnitType>` di Foundation: una unità è un simbolo più un convertitore lineare
 * verso l'unità base della sua dimensione (`UnitConverterLinear(coefficient:constant:)`, con
 * `base = valore × coefficiente + costante`). Qui la stessa idea, con due differenze volute:
 *  - **niente Double.** Apple converte in `Double`; qui ogni fattore è una frazione esatta di [BigInteger] e la sola
 *    divisione è l'ultima, alla scala chiesta (scheda dell'idea, «rischi»: «mai Double per gli importi»). Così 1 mi è
 *    esattamente 1609,344 m e 98,6 °F sono esattamente 37 °C;
 *  - **lo scostamento viene prima del fattore** (`base = (valore + scostamento) × fattore`): per la temperatura
 *    l'unità base è il grado Celsius e °F diventa `(F − 32) × 5/9`, due numeri esatti invece di `F × 5/9 + 255,3722…`.
 */

/**
 * Una grandezza fisica o di conto: lunghezza, massa, temperatura, valuta… Due unità si convertono solo se hanno la stessa
 * dimensione. È un valore (uguale per [id]): un'app può dichiararne una sua, per esempio `FluidUnitDimension("punti")`.
 */
class FluidUnitDimension(val id: String) {
  init {
    require(id.isNotBlank()) { "dimensione senza nome" }
  }

  override fun equals(other: Any?): Boolean = other is FluidUnitDimension && other.id == id
  override fun hashCode(): Int = id.hashCode()
  override fun toString(): String = "FluidUnitDimension($id)"

  companion object {
    val Length: FluidUnitDimension = FluidUnitDimension("length")
    val Mass: FluidUnitDimension = FluidUnitDimension("mass")
    val Temperature: FluidUnitDimension = FluidUnitDimension("temperature")
    val Volume: FluidUnitDimension = FluidUnitDimension("volume")
    val Area: FluidUnitDimension = FluidUnitDimension("area")
    val Speed: FluidUnitDimension = FluidUnitDimension("speed")
    val Duration: FluidUnitDimension = FluidUnitDimension("duration")
    val Data: FluidUnitDimension = FluidUnitDimension("data")
    val Energy: FluidUnitDimension = FluidUnitDimension("energy")
    val Percent: FluidUnitDimension = FluidUnitDimension("percent")
    val Currency: FluidUnitDimension = FluidUnitDimension("currency")
  }
}

/**
 * Una unità: il simbolo che si legge accanto al numero e come si arriva all'unità base della sua [dimension].
 *
 * `base = (valore + shift) × numerator / denominator`, tutto esatto. Si crea con [linear], [affine] o [currency]; le
 * unità di serie stanno in [FluidUnits].
 *
 * @property id il nome stabile, da salvare nelle impostazioni ("km", "celsius", "EUR").
 * @property symbol il simbolo breve ("km", "°C", "€"). Per una valuta di serie è quello della lingua al momento del
 *   formato ([FluidUnitFormat]): qui c'è il codice.
 * @property fractionDigits quante cifre dopo la virgola si scrivono e si accettano di serie (2 per i chilometri, 0 per i
 *   byte, quelle della valuta per una valuta).
 * @property minFractionDigits quante cifre dopo la virgola si scrivono sempre (2 per l'euro: «12,50 €»; 0 di serie).
 * @property currencyCode il codice ISO 4217 di una valuta, o null.
 */
class FluidUnit private constructor(
  val id: String,
  val dimension: FluidUnitDimension,
  val symbol: String,
  internal val numerator: BigInteger?,
  internal val denominator: BigInteger,
  internal val shift: Ratio,
  val fractionDigits: Int,
  val minFractionDigits: Int,
  val currencyCode: String?,
) {
  /** Vero se si converte: una valuta senza cambio dichiarato non si converte in nessun'altra. */
  val isConvertible: Boolean get() = numerator != null

  /** Vero se si converte in [other]: stessa dimensione ed entrambe con il loro fattore. */
  fun canConvertTo(other: FluidUnit): Boolean =
    this == other || (dimension == other.dimension && isConvertible && other.isConvertible)

  override fun equals(other: Any?): Boolean = other is FluidUnit && other.id == id && other.dimension == dimension
  override fun hashCode(): Int = 31 * id.hashCode() + dimension.hashCode()
  override fun toString(): String = "FluidUnit($id)"

  companion object {
    /**
     * Una unità lineare: un'unità vale `factor` unità base. [factor] è una stringa decimale esatta ("1609.344") o una
     * frazione ("5/9"), così non passa mai da un Double.
     */
    fun linear(
      id: String,
      dimension: FluidUnitDimension,
      symbol: String,
      factor: String,
      fractionDigits: Int = 2,
      minFractionDigits: Int = 0,
    ): FluidUnit = affine(id, dimension, symbol, factor, shift = "0", fractionDigits = fractionDigits,
      minFractionDigits = minFractionDigits)

    /** Una unità con uno scostamento ([shift], sommato prima del fattore): i gradi Fahrenheit e i kelvin. */
    fun affine(
      id: String,
      dimension: FluidUnitDimension,
      symbol: String,
      factor: String,
      shift: String,
      fractionDigits: Int = 2,
      minFractionDigits: Int = 0,
    ): FluidUnit {
      require(id.isNotBlank()) { "unità senza id" }
      require(fractionDigits in 0..12 && minFractionDigits in 0..fractionDigits) { "cifre decimali non valide: $id" }
      val f = Ratio.parse(factor)
      require(f.num.signum() > 0) { "fattore non positivo: $id" }
      return FluidUnit(id, dimension, symbol, f.num, f.den, Ratio.parse(shift), fractionDigits, minFractionDigits, null)
    }

    /**
     * Una valuta (ISO 4217, «EUR»). Le cifre decimali sono quelle della valuta (2 per l'euro, 0 per lo yen) se [digits] è
     * null. Il cambio è dell'app ([rate]: quante unità della valuta base vale una di questa; null: non si converte),
     * perché l'engine non sa quanto vale un dollaro oggi. Mai Double: [rate] è una stringa decimale.
     */
    fun currency(code: String, rate: String? = null, digits: Int? = null): FluidUnit {
      val upper = code.uppercase()
      val fraction = digits ?: runCatching { java.util.Currency.getInstance(upper).defaultFractionDigits }
        .getOrNull()?.takeIf { it >= 0 } ?: 2
      val r = rate?.let { Ratio.parse(it) }
      require(r == null || r.num.signum() > 0) { "cambio non positivo: $code" }
      return FluidUnit(upper, FluidUnitDimension.Currency, upper, r?.num, r?.den ?: BigInteger.ONE, Ratio.Zero, fraction,
        fraction, upper)
    }
  }
}

/**
 * Un numero con la sua unità: quello che il campo con unità restituisce, mai la stringa (scheda dell'idea). È un valore:
 * `FluidQuantity(BigDecimal("12.5"), FluidUnits.Kilometer)`.
 */
data class FluidQuantity(val value: BigDecimal, val unit: FluidUnit) {
  /** Lo stesso valore in [target], arrotondato a [scale] cifre (di serie quelle di [target]); null se non si converte. */
  fun to(target: FluidUnit, scale: Int = target.fractionDigits): FluidQuantity? =
    FluidUnits.convert(value, unit, target, scale)?.let { FluidQuantity(it, target) }

  /** Il valore nell'unità base della dimensione, a [scale] cifre; null se l'unità non si converte. */
  fun inBase(scale: Int = 12): BigDecimal? = FluidUnits.toBase(value, unit, scale)
}

/**
 * Le unità di serie e le conversioni. I fattori sono le definizioni esatte del SI e degli accordi internazionali (1 in =
 * 2,54 cm dal 1959, 1 lb = 0,45359237 kg, 1 gal US = 231 in³ = 3,785411784 l, 1 kcal = 4184 J, 1 KiB = 1024 B): fiducia
 * alta, sono definizioni, non misure. L'elenco segue le unità più usate da `UnitLength`, `UnitMass`, `UnitTemperature`,
 * `UnitVolume`, `UnitArea`, `UnitSpeed`, `UnitDuration`, `UnitInformationStorage` e `UnitEnergy` di Foundation.
 */
object FluidUnits {
  // --- Lunghezza (base: metro) ---
  val Millimeter: FluidUnit = FluidUnit.linear("mm", FluidUnitDimension.Length, "mm", "0.001", 1)
  val Centimeter: FluidUnit = FluidUnit.linear("cm", FluidUnitDimension.Length, "cm", "0.01", 1)
  val Meter: FluidUnit = FluidUnit.linear("m", FluidUnitDimension.Length, "m", "1")
  val Kilometer: FluidUnit = FluidUnit.linear("km", FluidUnitDimension.Length, "km", "1000")
  val Inch: FluidUnit = FluidUnit.linear("in", FluidUnitDimension.Length, "in", "0.0254")
  val Foot: FluidUnit = FluidUnit.linear("ft", FluidUnitDimension.Length, "ft", "0.3048")
  val Yard: FluidUnit = FluidUnit.linear("yd", FluidUnitDimension.Length, "yd", "0.9144")
  val Mile: FluidUnit = FluidUnit.linear("mi", FluidUnitDimension.Length, "mi", "1609.344")

  // --- Massa (base: chilogrammo) ---
  val Gram: FluidUnit = FluidUnit.linear("g", FluidUnitDimension.Mass, "g", "0.001", 1)
  val Kilogram: FluidUnit = FluidUnit.linear("kg", FluidUnitDimension.Mass, "kg", "1")
  val Ounce: FluidUnit = FluidUnit.linear("oz", FluidUnitDimension.Mass, "oz", "0.028349523125")
  val Pound: FluidUnit = FluidUnit.linear("lb", FluidUnitDimension.Mass, "lb", "0.45359237")

  // --- Temperatura (base: grado Celsius) ---
  val Celsius: FluidUnit = FluidUnit.linear("celsius", FluidUnitDimension.Temperature, "°C", "1", 1)
  val Fahrenheit: FluidUnit = FluidUnit.affine("fahrenheit", FluidUnitDimension.Temperature, "°F", "5/9", "-32", 1)
  val Kelvin: FluidUnit = FluidUnit.affine("kelvin", FluidUnitDimension.Temperature, "K", "1", "-273.15", 2)

  // --- Volume (base: litro) ---
  val Milliliter: FluidUnit = FluidUnit.linear("ml", FluidUnitDimension.Volume, "ml", "0.001", 0)
  val Liter: FluidUnit = FluidUnit.linear("l", FluidUnitDimension.Volume, "l", "1")
  val CubicMeter: FluidUnit = FluidUnit.linear("m3", FluidUnitDimension.Volume, "m³", "1000", 3)
  val GallonUS: FluidUnit = FluidUnit.linear("gal", FluidUnitDimension.Volume, "gal", "3.785411784")

  // --- Superficie (base: metro quadrato) ---
  val SquareMeter: FluidUnit = FluidUnit.linear("m2", FluidUnitDimension.Area, "m²", "1")
  val Hectare: FluidUnit = FluidUnit.linear("ha", FluidUnitDimension.Area, "ha", "10000", 3)
  val SquareKilometer: FluidUnit = FluidUnit.linear("km2", FluidUnitDimension.Area, "km²", "1000000", 3)

  // --- Velocità (base: metro al secondo) ---
  val MetersPerSecond: FluidUnit = FluidUnit.linear("m/s", FluidUnitDimension.Speed, "m/s", "1", 1)
  val KilometersPerHour: FluidUnit = FluidUnit.linear("km/h", FluidUnitDimension.Speed, "km/h", "5/18", 1)
  val MilesPerHour: FluidUnit = FluidUnit.linear("mph", FluidUnitDimension.Speed, "mph", "0.44704", 1)
  val Knot: FluidUnit = FluidUnit.linear("kn", FluidUnitDimension.Speed, "kn", "463/900", 1)

  // --- Durata (base: secondo) ---
  val Second: FluidUnit = FluidUnit.linear("s", FluidUnitDimension.Duration, "s", "1", 0)
  val Minute: FluidUnit = FluidUnit.linear("min", FluidUnitDimension.Duration, "min", "60", 1)
  val Hour: FluidUnit = FluidUnit.linear("h", FluidUnitDimension.Duration, "h", "3600", 1)

  // --- Dati (base: byte) ---
  val Byte: FluidUnit = FluidUnit.linear("B", FluidUnitDimension.Data, "B", "1", 0)
  val Kilobyte: FluidUnit = FluidUnit.linear("kB", FluidUnitDimension.Data, "kB", "1000", 1)
  val Megabyte: FluidUnit = FluidUnit.linear("MB", FluidUnitDimension.Data, "MB", "1000000", 1)
  val Gigabyte: FluidUnit = FluidUnit.linear("GB", FluidUnitDimension.Data, "GB", "1000000000", 2)
  val Kibibyte: FluidUnit = FluidUnit.linear("KiB", FluidUnitDimension.Data, "KiB", "1024", 1)
  val Mebibyte: FluidUnit = FluidUnit.linear("MiB", FluidUnitDimension.Data, "MiB", "1048576", 1)

  // --- Energia (base: joule) ---
  val Joule: FluidUnit = FluidUnit.linear("J", FluidUnitDimension.Energy, "J", "1", 0)
  val Kilojoule: FluidUnit = FluidUnit.linear("kJ", FluidUnitDimension.Energy, "kJ", "1000", 1)
  val Kilocalorie: FluidUnit = FluidUnit.linear("kcal", FluidUnitDimension.Energy, "kcal", "4184", 0)
  val KilowattHour: FluidUnit = FluidUnit.linear("kWh", FluidUnitDimension.Energy, "kWh", "3600000", 2)

  // --- Percentuale (base: frazione, 1 = 100%) ---
  val Percent: FluidUnit = FluidUnit.linear("percent", FluidUnitDimension.Percent, "%", "1/100", 1)

  // --- Valute di serie, senza cambio: l'app dà il cambio con FluidUnit.currency(codice, cambio) ---
  val Euro: FluidUnit = FluidUnit.currency("EUR")
  val Dollar: FluidUnit = FluidUnit.currency("USD")
  val PoundSterling: FluidUnit = FluidUnit.currency("GBP")

  /** Tutte le unità di serie, nell'ordine delle dimensioni. */
  val all: List<FluidUnit> = listOf(
    Millimeter, Centimeter, Meter, Kilometer, Inch, Foot, Yard, Mile,
    Gram, Kilogram, Ounce, Pound,
    Celsius, Fahrenheit, Kelvin,
    Milliliter, Liter, CubicMeter, GallonUS,
    SquareMeter, Hectare, SquareKilometer,
    MetersPerSecond, KilometersPerHour, MilesPerHour, Knot,
    Second, Minute, Hour,
    Byte, Kilobyte, Megabyte, Gigabyte, Kibibyte, Mebibyte,
    Joule, Kilojoule, Kilocalorie, KilowattHour,
    Percent,
    Euro, Dollar, PoundSterling,
  )

  /** L'unità di serie con questo [id] ("km", "celsius", "EUR"), o null. */
  fun byId(id: String): FluidUnit? = all.firstOrNull { it.id == id }

  /** Le unità di serie di una [dimension], nell'ordine della tabella: quelle che il campo offre di serie. */
  fun of(dimension: FluidUnitDimension): List<FluidUnit> = all.filter { it.dimension == dimension }

  /**
   * [value] da [from] a [to], arrotondato a [scale] cifre dopo la virgola con l'arrotondamento al pari (quello di serie
   * delle `FormatStyle` di Apple, `.toNearestOrEven`). Null se le due unità non si convertono.
   */
  fun convert(value: BigDecimal, from: FluidUnit, to: FluidUnit, scale: Int = to.fractionDigits): BigDecimal? {
    if (from == to) return value.setScale(scale, RoundingMode.HALF_EVEN)
    if (!from.canConvertTo(to)) return null
    return exact(value, from, to).toBigDecimal(scale)
  }

  /** [value] di [unit] nell'unità base della sua dimensione, a [scale] cifre. Null se [unit] non si converte. */
  fun toBase(value: BigDecimal, unit: FluidUnit, scale: Int = 12): BigDecimal? {
    val num = unit.numerator ?: return null
    return Ratio.of(value).plus(unit.shift).times(Ratio(num, unit.denominator)).toBigDecimal(scale)
  }

  /** La conversione esatta, come frazione: una sola divisione, alla fine. */
  internal fun exact(value: BigDecimal, from: FluidUnit, to: FluidUnit): Ratio {
    val base = Ratio.of(value).plus(from.shift).times(Ratio(from.numerator!!, from.denominator))
    return base.times(Ratio(to.denominator, to.numerator!!)).minus(to.shift)
  }
}

/** Una frazione esatta, ridotta, col denominatore positivo: l'aritmetica di [FluidUnits]. */
internal class Ratio(num: BigInteger, den: BigInteger) {
  val num: BigInteger
  val den: BigInteger

  init {
    require(den.signum() != 0) { "denominatore zero" }
    val g = num.gcd(den).let { if (it.signum() == 0) BigInteger.ONE else it }
    val sign = if (den.signum() < 0) -1 else 1
    this.num = num.divide(g).multiply(BigInteger.valueOf(sign.toLong()))
    this.den = den.divide(g).abs()
  }

  fun plus(other: Ratio): Ratio = Ratio(num * other.den + other.num * den, den * other.den)
  fun minus(other: Ratio): Ratio = Ratio(num * other.den - other.num * den, den * other.den)
  fun times(other: Ratio): Ratio = Ratio(num * other.num, den * other.den)

  fun toBigDecimal(scale: Int): BigDecimal =
    BigDecimal(num).divide(BigDecimal(den), scale.coerceAtLeast(0), RoundingMode.HALF_EVEN)

  override fun equals(other: Any?): Boolean = other is Ratio && other.num == num && other.den == den
  override fun hashCode(): Int = 31 * num.hashCode() + den.hashCode()
  override fun toString(): String = "$num/$den"

  companion object {
    val Zero: Ratio = Ratio(BigInteger.ZERO, BigInteger.ONE)

    fun of(value: BigDecimal): Ratio = if (value.scale() >= 0) {
      Ratio(value.unscaledValue(), BigInteger.TEN.pow(value.scale()))
    } else {
      Ratio(value.unscaledValue() * BigInteger.TEN.pow(-value.scale()), BigInteger.ONE)
    }

    /** "1609.344", "-32", "5/9": un decimale o una frazione di due decimali. */
    fun parse(text: String): Ratio {
      val parts = text.trim().split('/')
      require(parts.size in 1..2) { "fattore non valido: $text" }
      val top = of(BigDecimal(parts[0].trim(), MathContext.UNLIMITED))
      if (parts.size == 1) return top
      val bottom = of(BigDecimal(parts[1].trim(), MathContext.UNLIMITED))
      return top.times(Ratio(bottom.den, bottom.num))
    }
  }
}
