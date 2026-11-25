package dev.antigravity.fluidengine.sync

/**
 * Un istante dell'orologio logico ibrido (HLC: S. Kulkarni e altri, «Logical Physical Clocks and Consistent Snapshots in
 * Globally Distributed Databases», 2014): i millisecondi dell'orologio corretto, un contatore per gli eventi nello stesso
 * millisecondo e il nodo (il dispositivo) che l'ha preso.
 *
 * L'ordine e' quello dei tre campi, in quest'ordine, ed e' **lo stesso su ogni dispositivo**: per un campo vince lo
 * stamp piu' grande, e due dispositivi che vedono gli stessi stamp scelgono la stessa cosa. Non e' l'ora del telefono:
 * una modifica fatta **dopo averne vista un'altra** ha sempre uno stamp piu' grande, anche se l'orologio del telefono
 * e' indietro di ore ([SyncClock.observe]). Lo stamp di una scrittura e' anche l'identificativo della sua revisione
 * ([encode]).
 */
class SyncHlc(val millis: Long, val counter: Int, val node: String) : Comparable<SyncHlc> {
  init {
    require(millis >= 0) { "millisecondi negativi: $millis" }
    require(counter >= 0) { "contatore negativo: $counter" }
  }

  override fun compareTo(other: SyncHlc): Int {
    if (millis != other.millis) return millis.compareTo(other.millis)
    if (counter != other.counter) return counter.compareTo(other.counter)
    return node.compareTo(other.node)
  }

  /**
   * La forma testuale, ordinabile come testo nello stesso ordine degli stamp: 12 cifre esadecimali di millisecondi
   * (fino all'anno 10 889), 8 di contatore, poi il nodo. E' l'identificativo di una revisione nel foglio della scelta.
   */
  fun encode(): String = buildString(21 + node.length) {
    append(millis.toString(16).padStart(12, '0'))
    append('-')
    append(counter.toString(16).padStart(8, '0'))
    append('-')
    append(node)
  }

  override fun equals(other: Any?): Boolean =
    other is SyncHlc && other.millis == millis && other.counter == counter && other.node == node

  override fun hashCode(): Int = (millis.hashCode() * 31 + counter) * 31 + node.hashCode()

  override fun toString(): String = encode()

  companion object {
    /** Lo stamp piu' piccolo: per le pietre tombali che il relay ha gia' cancellato e di cui non resta la data. */
    val Zero: SyncHlc = SyncHlc(0, 0, "")

    /** Il contrario di [encode]; `null` se il testo non e' uno stamp. */
    fun parse(text: String): SyncHlc? {
      if (text.length < 22 || text[12] != '-' || text[21] != '-') return null
      val millis = text.substring(0, 12).toLongOrNull(16) ?: return null
      val counter = text.substring(13, 21).toLongOrNull(16)?.takeIf { it <= Int.MAX_VALUE } ?: return null
      return SyncHlc(millis, counter.toInt(), text.substring(22))
    }

    /** Un nodo valido: da 1 a 32 lettere, cifre o trattini bassi. */
    fun isValidNode(node: String): Boolean =
      node.length in 1..32 && node.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
  }
}

/**
 * L'orologio logico ibrido di un dispositivo.
 *
 * **Monotono.** [now] da' sempre uno stamp piu' grande di tutti quelli che ha dato e di tutti quelli che ha visto
 * ([observe]), anche se l'orologio del telefono torna indietro (un cambio d'ora manuale, la batteria scarica). L'ultimo
 * stamp si salva nel registro, cosi' vale anche dopo un riavvio.
 *
 * **Corretto dal relay.** Il relay dice la sua ora a ogni risposta ([SyncRelayTime]); [offsetMillis] e' la differenza
 * fra quella e l'orologio del telefono. Un telefono avanti di tre ore, dopo il primo contatto, smette di dare stamp del
 * futuro: due modifiche fatte senza vedersi le ordina l'ora vera, non quella del telefono sbagliato. Prima del primo
 * contatto l'ordine fra modifiche indipendenti segue l'ora del telefono (il limite e' scritto nella scheda del modulo).
 */
class SyncClock(
  val node: String,
  private val wall: () -> Long = System::currentTimeMillis,
  last: SyncHlc? = null,
) {
  init {
    require(SyncHlc.isValidNode(node)) { "nodo non valido: «$node»" }
  }

  @Volatile
  private var latest: SyncHlc = last ?: SyncHlc(0, 0, node)

  /** La correzione dell'orologio del telefono letta dal relay, in millisecondi (positiva se il telefono e' indietro). */
  @Volatile
  var offsetMillis: Long = 0

  /** L'ultimo stamp dato o visto. */
  val current: SyncHlc get() = latest

  /** L'ora corretta, senza avanzare l'orologio logico. */
  fun wallMillis(): Long = (wall() + offsetMillis).coerceAtLeast(0)

  /** Uno stamp nuovo per una scrittura: piu' grande di tutto quello che questo orologio ha dato o visto. */
  @Synchronized
  fun now(): SyncHlc {
    val pt = wallMillis()
    val last = latest
    val next = if (pt > last.millis) SyncHlc(pt, 0, node) else SyncHlc(last.millis, last.counter + 1, node)
    latest = next
    return next
  }

  /** Registra uno stamp arrivato da un altro dispositivo: il prossimo [now] sara' piu' grande. */
  @Synchronized
  fun observe(remote: SyncHlc) {
    if (remote > latest) latest = SyncHlc(remote.millis, remote.counter, node)
  }
}
