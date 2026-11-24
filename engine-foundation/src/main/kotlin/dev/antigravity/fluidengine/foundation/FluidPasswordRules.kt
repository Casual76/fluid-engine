package dev.antigravity.fluidengine.foundation

import java.security.SecureRandom
import java.util.Random
import kotlin.math.ln

/*
 * Le regole della password, la stima della forza e la password forte suggerita (F8.3, `campo-password`), pure e senza
 * Android. Il campo vive in `engine-ui` (`FluidPasswordField`) e qui prende le spunte, la forza e la proposta.
 *
 * Il riferimento Apple sono le **Password Rules** di iCloud Keychain (`UITextInputPasswordRules`, l'attributo HTML
 * `passwordrules`): un servizio dichiara in una riga cosa accetta, e il portachiavi genera solo password che lo
 * rispettano. [FluidPasswordRules.parse] legge la stessa sintassi.
 */

/**
 * Un insieme di caratteri di una regola: le classi di Apple (`upper`, `lower`, `digit`, `special`, `ascii-printable`,
 * `unicode`) o un insieme su misura (`[-().&@?'#,/"+]`).
 *
 * @property id il nome della classe ("upper"), o i caratteri fra parentesi quadre per uno su misura.
 */
class FluidPasswordCharset private constructor(
  val id: String,
  internal val test: (Char) -> Boolean,
  /** I caratteri da cui si genera: per `unicode` quelli stampabili ASCII, che ogni servizio accetta. */
  internal val pool: String,
) {
  /** Vero se [c] è di questo insieme. */
  operator fun contains(c: Char): Boolean = test(c)

  override fun equals(other: Any?): Boolean = other is FluidPasswordCharset && other.id == id
  override fun hashCode(): Int = id.hashCode()
  override fun toString(): String = "FluidPasswordCharset($id)"

  companion object {
    private const val UpperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LowerChars = "abcdefghijklmnopqrstuvwxyz"
    private const val DigitChars = "0123456789"

    /** I simboli di Apple (`special`): `-~!@#$%^&*_+=`|(){}[:;"'<>,.?]/` e lo spazio, che però non si genera mai. */
    private const val SpecialChars = "-~!@#\$%^&*_+=`|(){}[:;\"'<>,.?]/"
    private val PrintableChars = (0x21..0x7E).map { it.toChar() }.joinToString("")

    val Upper: FluidPasswordCharset = FluidPasswordCharset("upper", { it in 'A'..'Z' }, UpperChars)
    val Lower: FluidPasswordCharset = FluidPasswordCharset("lower", { it in 'a'..'z' }, LowerChars)
    val Digit: FluidPasswordCharset = FluidPasswordCharset("digit", { it in '0'..'9' }, DigitChars)
    val Special: FluidPasswordCharset = FluidPasswordCharset("special", { it == ' ' || it in SpecialChars }, SpecialChars)
    val AsciiPrintable: FluidPasswordCharset = FluidPasswordCharset("ascii-printable", { it in ' '..'~' }, PrintableChars)
    val Unicode: FluidPasswordCharset = FluidPasswordCharset("unicode", { !it.isISOControl() }, PrintableChars)

    /** Un insieme su misura, i caratteri come li scrive la regola fra parentesi quadre. */
    fun of(chars: String): FluidPasswordCharset {
      val set = chars.toSet()
      require(set.isNotEmpty()) { "insieme di caratteri vuoto" }
      val pool = set.filter { it != ' ' }.sorted().joinToString("")
      return FluidPasswordCharset("[${set.sorted().joinToString("")}]", { it in set }, pool.ifEmpty { " " })
    }

    internal fun named(name: String): FluidPasswordCharset? = when (name.trim().lowercase()) {
      "upper" -> Upper
      "lower" -> Lower
      "digit" -> Digit
      "special" -> Special
      "ascii-printable" -> AsciiPrintable
      "unicode" -> Unicode
      else -> null
    }
  }
}

/** Una regola sola, quella che il campo spunta mentre si scrive. */
sealed class FluidPasswordRequirement {
  /** Almeno [count] caratteri (`minlength`). */
  data class MinLength(val count: Int) : FluidPasswordRequirement()

  /** Al più [count] caratteri (`maxlength`). */
  data class MaxLength(val count: Int) : FluidPasswordRequirement()

  /** Almeno un carattere di uno degli [any] insiemi (un `required`). */
  data class Contains(val any: List<FluidPasswordCharset>) : FluidPasswordRequirement()

  /** Mai più di [count] caratteri uguali di fila (`max-consecutive`). */
  data class MaxConsecutive(val count: Int) : FluidPasswordRequirement()

  /** Solo caratteri degli [allowed] insiemi (gli `allowed` e i `required` insieme). */
  data class OnlyAllowed(val allowed: List<FluidPasswordCharset>) : FluidPasswordRequirement()
}

/** Una regola e se la password di adesso la rispetta. */
data class FluidPasswordCheck(val requirement: FluidPasswordRequirement, val satisfied: Boolean)

/**
 * Le regole di un servizio: le dichiara l'app (scheda dell'idea: «le regole sono dati che l'app dichiara»), a mano o
 * con la riga di Apple ([parse]). Servono a tre cose: le spunte ([check]), la conferma ([isSatisfiedBy]) e la password
 * forte proposta ([generate]), che le rispetta sempre.
 *
 * @property allowed i caratteri accettati; null: quelli dei `required` più ogni stampabile ASCII, come Apple quando la
 *   regola non dice `allowed`.
 */
class FluidPasswordRules(
  val minLength: Int = 8,
  val maxLength: Int? = null,
  val required: List<List<FluidPasswordCharset>> = emptyList(),
  val allowed: List<FluidPasswordCharset>? = null,
  val maxConsecutive: Int? = null,
) {
  init {
    require(minLength >= 0) { "minLength negativa" }
    require(maxLength == null || maxLength >= minLength.coerceAtLeast(1)) { "maxLength sotto minLength" }
    require(maxConsecutive == null || maxConsecutive >= 1) { "maxConsecutive sotto 1" }
  }

  /** Le regole nell'ordine in cui il campo le mostra: lunghezza, classi, ripetizioni, caratteri ammessi. */
  val requirements: List<FluidPasswordRequirement> = buildList {
    if (minLength > 0) add(FluidPasswordRequirement.MinLength(minLength))
    maxLength?.let { add(FluidPasswordRequirement.MaxLength(it)) }
    required.forEach { add(FluidPasswordRequirement.Contains(it)) }
    maxConsecutive?.let { add(FluidPasswordRequirement.MaxConsecutive(it)) }
    allowed?.let { add(FluidPasswordRequirement.OnlyAllowed((it + required.flatten()).distinct())) }
  }

  /** Ogni regola con la sua spunta per [password]. */
  fun check(password: String): List<FluidPasswordCheck> = requirements.map { FluidPasswordCheck(it, satisfies(it, password)) }

  /** Vero se [password] rispetta tutte le regole. */
  fun isSatisfiedBy(password: String): Boolean = requirements.all { satisfies(it, password) }

  private fun satisfies(requirement: FluidPasswordRequirement, password: String): Boolean = when (requirement) {
    is FluidPasswordRequirement.MinLength -> password.length >= requirement.count
    is FluidPasswordRequirement.MaxLength -> password.length <= requirement.count
    is FluidPasswordRequirement.Contains -> password.any { c -> requirement.any.any { c in it } }
    is FluidPasswordRequirement.MaxConsecutive -> longestRun(password) <= requirement.count
    is FluidPasswordRequirement.OnlyAllowed -> password.all { c -> requirement.allowed.any { c in it } }
  }

  /**
   * Una password forte che rispetta queste regole, da [random] (di serie `SecureRandom`, mai un generatore prevedibile).
   *
   * **Come quella di Apple** quando le regole lo permettono: 20 caratteri in tre gruppi da sei, separati da due trattini,
   * con una maiuscola e una cifra, gli altri sedici minuscoli e pronunciabili (consonante, vocale, consonante,
   * consonante, vocale, consonante: «dixtuf-nezKun-zigvy6»). Fonte: *Apple Platform Security*, «Automatic strong
   * passwords» («20 characters long… one digit, one uppercase character, two hyphens, and 16 lowercase characters…
   * 71 bits of entropy»); fiducia alta sul formato, media sul disegno pronunciabile, letto dagli esempi.
   *
   * Se le regole non lo ammettono (niente trattini, più corta di 20, un simbolo diverso richiesto) la password è
   * casuale: lunga [minLength] o 20, un carattere per ogni `required` e il resto dagli ammessi, rimescolata.
   */
  fun generate(random: Random = SecureRandom()): String {
    val apple = appleStyle(random)
    if (isSatisfiedBy(apple)) return apple
    repeat(200) {
      val candidate = randomStyle(random)
      if (isSatisfiedBy(candidate)) return candidate
    }
    error("nessuna password soddisfa queste regole")
  }

  private fun appleStyle(random: Random): String {
    val consonants = "bcdfghjkmnpqrstvwxz"
    val vowels = "aeiouy"
    val pattern = "cvccvc"
    val chunks = Array(3) {
      CharArray(6) { i -> if (pattern[i] == 'c') consonants[random.nextInt(consonants.length)] else vowels[random.nextInt(vowels.length)] }
    }
    // La cifra sta all'inizio o alla fine di un gruppo («zigvy6», «6waqby»); la maiuscola su una lettera a caso.
    val digitChunk = random.nextInt(3)
    val digitAt = if (random.nextBoolean()) 0 else 5
    chunks[digitChunk][digitAt] = '0' + random.nextInt(10)
    while (true) {
      val chunk = random.nextInt(3)
      val at = random.nextInt(6)
      if (chunk == digitChunk && at == digitAt) continue
      chunks[chunk][at] = chunks[chunk][at].uppercaseChar()
      break
    }
    return chunks.joinToString("-") { String(it) }
  }

  private fun randomStyle(random: Random): String {
    val length = (maxLength?.let { minOf(it, 20) } ?: 20).coerceAtLeast(minLength).coerceAtLeast(required.size)
    val pool = generationPool()
    val chars = CharArray(length) { pool[random.nextInt(pool.length)] }
    // Un carattere per ogni regola «contiene», in posti diversi.
    val slots = (0 until length).shuffled(random).toMutableList()
    required.forEach { any ->
      val choices = any.joinToString("") { it.pool }.filter { allowedChar(it) }.ifEmpty { any.first().pool }
      chars[slots.removeAt(0)] = choices[random.nextInt(choices.length)]
    }
    return String(chars)
  }

  private fun generationPool(): String {
    val base = allowed ?: (required.flatten().ifEmpty {
      listOf(FluidPasswordCharset.Lower, FluidPasswordCharset.Upper, FluidPasswordCharset.Digit)
    })
    return (base + required.flatten()).distinct().joinToString("") { it.pool }.toSet().joinToString("")
  }

  private fun allowedChar(c: Char): Boolean = allowed?.let { list -> (list + required.flatten()).any { c in it } } ?: true

  companion object {
    /**
     * Le regole di serie: almeno 8 caratteri con una minuscola, una maiuscola e una cifra. È il minimo che quasi ogni
     * servizio accetta (le linee guida NIST SP 800-63B chiedono 8 caratteri; le classi sono la richiesta più comune
     * dei servizi delle app Pampa). A occhio, 23 novembre 2026.
     */
    val Default: FluidPasswordRules = FluidPasswordRules(
      minLength = 8,
      required = listOf(listOf(FluidPasswordCharset.Lower), listOf(FluidPasswordCharset.Upper),
        listOf(FluidPasswordCharset.Digit)),
    )

    /**
     * Le regole scritte come le Password Rules di Apple: `minlength: 8; maxlength: 64; required: lower; required: upper;
     * required: digit; required: [-().&@?'#,/"+]; max-consecutive: 2; allowed: ascii-printable`. Le proprietà
     * sconosciute e i valori non validi si ignorano, come fa Safari.
     */
    fun parse(rules: String): FluidPasswordRules {
      var min: Int? = null
      var max: Int? = null
      var consecutive: Int? = null
      val required = mutableListOf<List<FluidPasswordCharset>>()
      var allowed: MutableList<FluidPasswordCharset>? = null
      splitOutsideBrackets(rules, ';').forEach { property ->
        val colon = property.indexOf(':')
        if (colon < 0) return@forEach
        val name = property.substring(0, colon).trim().lowercase()
        val value = property.substring(colon + 1).trim()
        when (name) {
          "minlength" -> value.toIntOrNull()?.takeIf { it >= 0 }?.let { min = maxOf(min ?: 0, it) }
          "maxlength" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { max = minOf(max ?: it, it) }
          "max-consecutive" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { consecutive = minOf(consecutive ?: it, it) }
          "required" -> charsets(value).takeIf { it.isNotEmpty() }?.let { required += it }
          "allowed" -> charsets(value).takeIf { it.isNotEmpty() }?.let { (allowed ?: mutableListOf<FluidPasswordCharset>().also { list -> allowed = list }).addAll(it) }
        }
      }
      val minimum = min ?: 8
      return FluidPasswordRules(
        minLength = minimum,
        maxLength = max?.coerceAtLeast(minimum.coerceAtLeast(1)),
        required = required,
        allowed = allowed?.distinct(),
        maxConsecutive = consecutive,
      )
    }

    private fun charsets(value: String): List<FluidPasswordCharset> =
      splitOutsideBrackets(value, ',').mapNotNull { token ->
        val t = token.trim()
        if (t.startsWith("[") && t.endsWith("]") && t.length > 2) {
          FluidPasswordCharset.of(t.substring(1, t.length - 1).replace("&quot;", "\""))
        } else {
          FluidPasswordCharset.named(t)
        }
      }

    private fun splitOutsideBrackets(text: String, separator: Char): List<String> {
      val parts = mutableListOf<String>()
      val current = StringBuilder()
      var depth = 0
      for (c in text) {
        when {
          c == '[' && depth == 0 -> { depth = 1; current.append(c) }
          c == ']' && depth == 1 -> { depth = 0; current.append(c) }
          c == separator && depth == 0 -> { parts += current.toString(); current.clear() }
          else -> current.append(c)
        }
      }
      if (current.isNotBlank()) parts += current.toString()
      return parts.filter { it.isNotBlank() }
    }

    internal fun longestRun(text: String): Int {
      var best = 0
      var run = 0
      var previous: Char? = null
      for (c in text) {
        run = if (c == previous) run + 1 else 1
        previous = c
        if (run > best) best = run
      }
      return best
    }
  }
}

/**
 * La forza stimata di una password: **una stima semplice, dichiarata come tale** (scheda dell'idea: niente zxcvbn, «la
 * forza stimata è un aiuto visivo»). Non promette niente: dice solo se una password è corta, prevedibile o comune.
 *
 * @property level da 0 (vuota) a 4: 1 debole, 2 discreta, 3 buona, 4 forte. È quanti segmenti accende l'indicatore.
 * @property bits i bit stimati: lunghezza efficace × log₂ dell'alfabeto usato.
 */
data class FluidPasswordStrength(val level: Int, val bits: Double) {
  companion object {
    /**
     * Le soglie, in bit: sotto 36 debole, poi discreta fino a 60, buona fino a 80, forte da 80. A occhio, 23 novembre
     * 2026, sulle fasce dei gestori di password (KeePass chiama «buone» le password da 64 bit, «forti» da 80; la password
     * suggerita da Apple ne ha 71 per Apple e sta fra le forti con questa stima, che conta l'alfabeto e non il modello).
     */
    const val FairBits: Double = 36.0
    const val GoodBits: Double = 60.0
    const val StrongBits: Double = 80.0

    /**
     * La stima:
     *  - **l'alfabeto** è la somma delle classi usate (26 minuscole, 26 maiuscole, 10 cifre, 33 simboli, 100 per il resto);
     *  - **la lunghezza efficace** toglie tre quarti di carattere per ogni ripetizione («aaa») e per ogni passo di una
     *    sequenza («abc», «321», «qwe»);
     *  - **una password comune** (fra le più usate, anche con cifre o simboli in coda: «Password1!») resta debole.
     */
    fun estimate(password: String): FluidPasswordStrength {
      if (password.isEmpty()) return FluidPasswordStrength(0, 0.0)
      var alphabet = 0
      if (password.any { it in 'a'..'z' }) alphabet += 26
      if (password.any { it in 'A'..'Z' }) alphabet += 26
      if (password.any { it in '0'..'9' }) alphabet += 10
      if (password.any { it in ' '..'~' && !it.isLetterOrDigit() }) alphabet += 33
      if (password.any { it > '~' }) alphabet += 100
      var predictable = 0
      for (i in 1 until password.length) {
        val a = password[i - 1].lowercaseChar()
        val b = password[i].lowercaseChar()
        if (a == b || b - a == 1 || a - b == 1 || keyboardNeighbours(a, b)) predictable++
      }
      val effective = (password.length - predictable * 0.75).coerceAtLeast(1.0)
      val bits = effective * ln(alphabet.coerceAtLeast(2).toDouble()) / ln(2.0)
      val level = when {
        isCommon(password) -> 1
        bits < FairBits -> 1
        bits < GoodBits -> 2
        bits < StrongBits -> 3
        else -> 4
      }
      return FluidPasswordStrength(level, bits)
    }

    private const val KeyboardRows = "qwertyuiop asdfghjkl zxcvbnm 1234567890"

    private fun keyboardNeighbours(a: Char, b: Char): Boolean {
      val i = KeyboardRows.indexOf(a)
      val j = KeyboardRows.indexOf(b)
      return i >= 0 && j >= 0 && (j - i == 1 || i - j == 1) && KeyboardRows[minOf(i, j) + 1] != ' ' &&
        KeyboardRows[minOf(i, j)] != ' '
    }

    /** Le più usate (le classifiche pubbliche delle password trapelate), in italiano e in inglese. */
    private val Common = setOf(
      "password", "passw0rd", "qwerty", "qwertyuiop", "asdfgh", "letmein", "welcome", "admin", "iloveyou", "monkey",
      "dragon", "football", "baseball", "sunshine", "princess", "master", "shadow", "superman", "michael", "abc",
      "ciao", "amore", "juventus", "napoli", "inter", "milan", "roma", "francesca", "andrea", "alessandro", "ti amo",
      "tiamo", "forzanapoli", "forzajuve", "stellina", "cambiami", "pass", "test", "changeme", "secret",
    )

    private fun isCommon(password: String): Boolean {
      val core = password.lowercase().trimEnd { !it.isLetter() }.trimStart { !it.isLetter() }
      if (core in Common) return true
      // Solo cifre, o una cifra ripetuta: «123456», «000000».
      return password.all { it.isDigit() } && password.length <= 8
    }
  }
}
