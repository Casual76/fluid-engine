package dev.antigravity.fluidengine.diagnostics

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// F8.24 · Il registro circolare (`segnala-problema`): gli ultimi eventi dell'app, in memoria e mai sul disco, che una
// segnalazione allega solo se l'utente lo lascia, dopo averli visti. I dati personali si tolgono **prima di uscire**
// ([FluidPrivacyScrubber]), e la regola per chi scrive è semplice: si scrive cosa è successo, non i dati («sincronizzate
// 3 note», non il titolo delle note). I rapporti automatici della diagnostica non lo portano mai.

/** Quanto è grave un evento del registro. */
enum class FluidLogLevel(val mark: Char) {
  Info('I'),
  Warning('W'),
  Error('E'),
}

/** Un evento del registro: l'ora, il livello, il nome del pezzo dell'app ([tag]) e la frase. */
class FluidLogEvent(val timeMillis: Long, val level: FluidLogLevel, val tag: String, val message: String)

/**
 * Il registro circolare dell'app: gli ultimi [Capacity] eventi in memoria. Si scrive da qualunque filo, costa una riga
 * in un array (nessun disco, nessuna rete), e si legge solo per una segnalazione.
 *
 * ```kotlin
 * FluidLog.event("sync", "Sincronizzate 3 note")
 * FluidLog.warn("rete", "Timeout dopo 15 s")
 * ```
 */
object FluidLog {
  /** Quanti eventi si tengono (a occhio, 26/11/2026: qualche minuto di uso normale). */
  const val Capacity: Int = 200

  /** La lunghezza massima di una frase: il resto si taglia. */
  const val MaxMessageLength: Int = 300

  private val lock = Any()
  private val events = arrayOfNulls<FluidLogEvent>(Capacity)
  private var next = 0
  private var size = 0

  @Volatile
  internal var clock: () -> Long = System::currentTimeMillis

  /** Un evento normale. */
  fun event(tag: String, message: String) = add(FluidLogLevel.Info, tag, message)

  /** Qualcosa che non va, ma l'app continua. */
  fun warn(tag: String, message: String) = add(FluidLogLevel.Warning, tag, message)

  /** Un errore. Solo la frase: mai il messaggio di un'eccezione, che può contenere i dati. */
  fun error(tag: String, message: String) = add(FluidLogLevel.Error, tag, message)

  /** Aggiunge un evento di livello [level]. */
  fun add(level: FluidLogLevel, tag: String, message: String) {
    val event = FluidLogEvent(clock(), level, tag.take(32), message.take(MaxMessageLength))
    synchronized(lock) {
      events[next] = event
      next = (next + 1) % Capacity
      if (size < Capacity) size++
    }
  }

  /** Gli eventi, dal più vecchio, **così come sono stati scritti**: solo per l'app, mai per uscire dal telefono. */
  fun snapshot(): List<FluidLogEvent> = synchronized(lock) {
    List(size) { i -> events[(next - size + i + Capacity) % Capacity]!! }
  }

  /**
   * Le ultime [limit] righe pronte per uscire: l'ora, il livello, il pezzo e la frase **coi dati personali tolti**
   * ([FluidPrivacyScrubber.scrub]). È quello che una segnalazione mostra e allega.
   */
  fun export(limit: Int = Capacity, zone: ZoneId = ZoneId.systemDefault()): List<String> {
    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
    return snapshot().takeLast(limit.coerceAtLeast(0)).map { e ->
      "${formatter.format(Instant.ofEpochMilli(e.timeMillis))} ${e.level.mark} ${FluidPrivacyScrubber.scrub(e.tag)}: ${FluidPrivacyScrubber.scrub(e.message)}"
    }
  }

  /** Svuota il registro. */
  fun clear() {
    synchronized(lock) {
      events.fill(null)
      next = 0
      size = 0
    }
  }
}

/**
 * Il filtro dei dati personali (F8.24): toglie da un testo quello che non deve uscire dal telefono, e lascia il resto
 * leggibile. Mette al posto di ogni dato un segno fra «»: «[email]», «[telefono]», «[chiave]»…
 *
 * Cosa trova: indirizzi mail, numeri di telefono, IBAN, numeri di carta (col controllo di Luhn), codici fiscali,
 * indirizzi IP, chiavi e token (JWT, `Bearer …`, `password=…`, lunghe stringhe casuali), identificativi (UUID), il
 * percorso e i parametri di un indirizzo web (resta solo il dominio), i file e gli Uri `content://`.
 *
 * Cosa non può trovare: un voto, un nome, il testo di una nota. Quelli non si scrivono nel registro (la regola per chi
 * scrive, in [FluidLog]); e la segnalazione fa vedere tutto prima di mandarlo.
 */
object FluidPrivacyScrubber {
  private val rules: List<Pair<Regex, (MatchResult) -> String>> = listOf(
    Regex("""(?i)\b(?:content|file)://\S+""") to { _ -> "«[file]»" },
    // R3 · Anche con un IPv6 fra parentesi quadre al posto del dominio (`http://[2001:db8::1]:8080/…`): prima la
    // regola non lo riconosceva, e restavano l'indirizzo, il percorso e i parametri.
    Regex("""(?i)\b(https?)://(\[[0-9A-Za-z:.%_-]+\]|[A-Za-z0-9.-]+)(?::\d+)?([/?#]\S*)?""") to { m ->
      val tail = m.groupValues[3]
      val host = m.groupValues[2].let { if (it.startsWith("[")) "«[ip]»" else it }
      "${m.groupValues[1].lowercase()}://$host" + if (tail.length > 1) "/«…»" else ""
    },
    Regex("""(?i)(?:/storage/|/sdcard/|/data/user/|/data/data/)\S+""") to { _ -> "«[file]»" },
    Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""") to { _ -> "«[email]»" },
    Regex("""\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""") to { _ -> "«[chiave]»" },
    Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""") to { _ -> "Bearer «[chiave]»" },
    Regex("""(?i)\b(password|passwd|pwd|pass|token|secret|segreto|apikey|api_key|api-key|pin|otp|codice)(\s*[:=]\s*)\S+""") to { m ->
      "${m.groupValues[1]}${m.groupValues[2]}«[nascosto]»"
    },
    Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b""") to { _ -> "«[id]»" },
    Regex("""\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){2,7}(?:\s?[A-Z0-9]{1,4})?\b""") to { _ -> "«[iban]»" },
    Regex("""(?i)\b[A-Z]{6}\d{2}[ABCDEHLMPRST]\d{2}[A-Z]\d{3}[A-Z]\b""") to { _ -> "«[codice fiscale]»" },
    Regex("""(?<![\d.])(?:\d[ -]?){12,18}\d(?![\d.])""") to { m ->
      if (luhn(m.value.filter(Char::isDigit))) "«[carta]»" else m.value
    },
    // R3 · L'IPv6 in forma compressa (`2001:db8::1`, `::1`, `fe80::1%wlan0`, `::ffff:192.0.2.10`): un `::` con almeno un
    // gruppo, la zona e un IPv4 in coda facoltativi. Prima dell'IPv4, che altrimenti ne toglierebbe solo la coda. Mai
    // dentro una parola (`Note::class`) e mai un `::` da solo.
    Regex(
      """(?i)(?<![0-9a-z_:])(?:(?:[0-9a-f]{1,4}:){1,7}:(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,6})?|::[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,6})""" +
        """(?::(?:\d{1,3}\.){3}\d{1,3})?(?:%[0-9a-z._-]+)?(?![0-9a-z_]|\.\d)""",
    ) to { _ -> "«[ip]»" },
    Regex("""\b(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)\b""") to { _ -> "«[ip]»" },
    Regex("""(?i)\b(?:[0-9a-f]{1,4}:){4,7}[0-9a-f]{1,4}\b""") to { _ -> "«[ip]»" },
    Regex("""(?<![\w.])(?:\+|00)?\d(?:[\s./-]?\d){8,14}(?![\w.])""") to { _ -> "«[telefono]»" },
    Regex("""\b(?=[A-Za-z0-9_-]*\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{32,}\b""") to { _ -> "«[chiave]»" },
    Regex("""(?i)\b[0-9a-f]{24,}\b""") to { _ -> "«[chiave]»" },
  )

  /** [text] coi dati personali tolti. */
  fun scrub(text: String): String {
    var out = text
    for ((regex, replace) in rules) out = regex.replace(out, replace)
    return out
  }

  /** Vero se [digits] passa il controllo di Luhn (i numeri delle carte). */
  internal fun luhn(digits: String): Boolean {
    if (digits.length !in 13..19) return false
    var sum = 0
    var double = false
    for (i in digits.indices.reversed()) {
      var d = digits[i] - '0'
      if (double) {
        d *= 2
        if (d > 9) d -= 9
      }
      sum += d
      double = !double
    }
    return sum % 10 == 0
  }
}
