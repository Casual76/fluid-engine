package dev.antigravity.fluidengine.wear.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Il registro del telefono: ricorda quali eventi del polso ha già trattato e con quale esito, così una consegna ripetuta
 * riceve **la stessa ricevuta** e non si applica due volte.
 *
 * Il giro giusto, sul telefono:
 * 1. [receive] dice se l'evento è nuovo ([FluidWearInboxResult.New]), ripetuto ([FluidWearInboxResult.Repeated]) o
 *    scaduto ([FluidWearInboxResult.Expired]);
 * 2. per un evento nuovo l'app **lo salva** (col suo [FluidWearEvent.id], così un salvataggio ripetuto non raddoppia);
 * 3. solo dopo chiama [confirm], che registra l'esito su disco e dà la ricevuta da rimandare.
 *
 * Se il telefono si spegne fra il punto 2 e il 3, l'evento non è registrato qui: il polso lo riconsegna, torna nuovo, e
 * l'app lo trova già salvato col suo id. È l'unico buco possibile, ed è per questo che l'id va salvato con il dato.
 * [handle] fa i tre passi in fila.
 *
 * Il registro tiene gli esiti per [retainMillis] (una settimana: più a lungo di qualunque coda del polso) e al massimo
 * [maxRemembered] voci.
 */
class FluidWearInbox(
  private val store: FluidWearStore,
  private val clock: () -> Long = System::currentTimeMillis,
  val retainMillis: Long = DefaultRetainMillis,
  val maxRemembered: Int = DefaultMaxRemembered,
) {
  private val lock = Any()
  private var seen: LinkedHashMap<String, FluidWearReceipt> = load()

  /** Il primo passo: che cosa fare di [event]. */
  fun receive(event: FluidWearEvent): FluidWearInboxResult = synchronized(lock) {
    seen[event.id]?.let { return@synchronized FluidWearInboxResult.Repeated(it) }
    if (event.isExpired(clock())) {
      return@synchronized FluidWearInboxResult.Expired(recordLocked(event.id, FluidWearReceiptOutcome.Expired))
    }
    FluidWearInboxResult.New(event)
  }

  /**
   * Il terzo passo: l'app ha trattato [eventId] con l'esito [outcome] (di serie «Registrato»: salvato). Registra su disco
   * e torna la ricevuta da rimandare. Chiamato due volte, torna la ricevuta della prima.
   */
  fun confirm(eventId: String, outcome: FluidWearReceiptOutcome = FluidWearReceiptOutcome.Registered): FluidWearReceipt =
    synchronized(lock) { seen[eventId] ?: recordLocked(eventId, outcome) }

  /** La ricevuta già data per [eventId], se c'è. */
  fun receiptFor(eventId: String): FluidWearReceipt? = synchronized(lock) { seen[eventId] }

  /**
   * I tre passi in fila. [save] salva l'evento e dice se l'ha accettato (`false`: un tipo che l'app non conosce, e la
   * ricevuta dice «Non registrato»). Se [save] lancia, non c'è ricevuta: il polso riconsegnerà.
   */
  suspend fun handle(event: FluidWearEvent, save: suspend (FluidWearEvent) -> Boolean): FluidWearReceipt =
    when (val result = receive(event)) {
      is FluidWearInboxResult.Repeated -> result.receipt
      is FluidWearInboxResult.Expired -> result.receipt
      is FluidWearInboxResult.New -> {
        val accepted = save(result.event)
        confirm(event.id, if (accepted) FluidWearReceiptOutcome.Registered else FluidWearReceiptOutcome.Refused)
      }
    }

  /**
   * Un evento di una versione del formato che questo telefono non sa leggere ([FluidWearDecoded.Unsupported]): si
   * risponde «Non registrato», così il polso smette di aspettare e l'app può dire di aggiornare l'app del telefono.
   */
  fun refuseUnsupported(eventId: String): FluidWearReceipt = confirm(eventId, FluidWearReceiptOutcome.Refused)

  private fun recordLocked(eventId: String, outcome: FluidWearReceiptOutcome): FluidWearReceipt {
    val now = clock()
    val receipt = FluidWearReceipt(eventId, outcome, now)
    val next = LinkedHashMap<String, FluidWearReceipt>(seen)
    next[eventId] = receipt
    // Il registro dimentica prima i piu' vecchi: per eta', poi per tetto.
    val iterator = next.entries.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      if (now - entry.value.atMillis >= retainMillis) iterator.remove()
    }
    while (next.size > maxRemembered) {
      next.remove(next.keys.first())
    }
    store.write(FileName, encode(next.values))
    seen = next
    return receipt
  }

  private fun load(): LinkedHashMap<String, FluidWearReceipt> {
    val bytes = store.read(FileName) ?: return LinkedHashMap()
    return try {
      decode(bytes)
    } catch (e: IOException) {
      // Un registro rotto si ricomincia: il peggio e' un evento ripetuto che torna «nuovo», e l'app lo riconosce dal suo id.
      LinkedHashMap()
    }
  }

  companion object {
    /** Una settimana. A occhio, 26/11/2026: più a lungo di qualunque coda del polso ([FluidWearOutbox.DefaultKeepSettledMillis]). */
    const val DefaultRetainMillis: Long = 7 * 24 * 60 * 60 * 1000L

    /** 2000 eventi: qualche decina di byte l'uno. */
    const val DefaultMaxRemembered: Int = 2000

    private const val FileName = "registro.fws"
    private const val FileVersion = 1

    private fun encode(receipts: Collection<FluidWearReceipt>): ByteArray {
      val buffer = ByteArrayOutputStream()
      DataOutputStream(buffer).use { out ->
        out.writeInt(FileVersion)
        out.writeInt(receipts.size)
        for (r in receipts) {
          val bytes = FluidWearFormat.encode(r)
          out.writeInt(bytes.size)
          out.write(bytes)
        }
      }
      return buffer.toByteArray()
    }

    private fun decode(bytes: ByteArray): LinkedHashMap<String, FluidWearReceipt> {
      val input = DataInputStream(ByteArrayInputStream(bytes))
      if (input.readInt() != FileVersion) throw IOException("versione del registro sconosciuta")
      val count = input.readInt()
      if (count < 0 || count > 1_000_000) throw IOException("conto del registro fuori misura: $count")
      val map = LinkedHashMap<String, FluidWearReceipt>(count)
      repeat(count) {
        val size = input.readInt()
        if (size < 0 || size > input.available()) throw IOException("ricevuta fuori misura")
        val raw = ByteArray(size).also { input.readFully(it) }
        val receipt = (FluidWearFormat.decode(raw) as? FluidWearDecoded.Receipt)?.receipt
          ?: throw IOException("ricevuta illeggibile nel registro")
        map[receipt.eventId] = receipt
      }
      return map
    }
  }
}

/** Il primo passo del registro ([FluidWearInbox.receive]). */
sealed interface FluidWearInboxResult {
  /** Un evento mai visto: l'app lo salva e poi chiama [FluidWearInbox.confirm]. */
  data class New(val event: FluidWearEvent) : FluidWearInboxResult

  /** Un evento già trattato: si rimanda [receipt], senza applicarlo di nuovo. */
  data class Repeated(val receipt: FluidWearReceipt) : FluidWearInboxResult

  /** Arrivato dopo la scadenza: non si applica, si rimanda [receipt] («Scaduto»). Già registrato. */
  data class Expired(val receipt: FluidWearReceipt) : FluidWearInboxResult
}
