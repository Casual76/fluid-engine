package dev.antigravity.fluidengine.wear.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La coda del polso: gli eventi salvati sull'orologio, ciascuno «In coda» finché il telefono non rimanda la ricevuta.
 *
 * - [enqueue] salva l'evento **su disco prima di tornare**: se l'orologio si spegne un attimo dopo, al riavvio l'evento
 *   c'è ancora ed è ancora in coda.
 * - [due] dice cosa consegnare al trasporto adesso: gli eventi mai consegnati, e quelli consegnati più di
 *   [resendAfterMillis] fa senza ricevuta (il telefono può averli ricevuti e non salvati, per esempio se la sua app è
 *   stata chiusa a metà). Il telefono riconosce le ripetizioni ([FluidWearInbox]) e rimanda la stessa ricevuta.
 * - [onReceipt] chiude un evento con l'esito del telefono. Una ricevuta ripetuta o di un evento che non c'è si ignora.
 * - Un evento oltre la sua scadenza si chiude «Scaduto» e non si consegna più. Se il telefono l'aveva salvato in tempo e
 *   la sua ricevuta arriva dopo, vince il telefono: «Registrato».
 * - Gli eventi chiusi restano [keepSettledMillis] (per mostrare «Registrato» al polso), poi si tolgono.
 *
 * Tutti i metodi si possono chiamare da qualunque filo: lavorano sotto un lucchetto e pubblicano in [deliveries] una
 * lista immutabile nuova, intera (la regola degli stati scritti in sottofondo, `regole.md`).
 */
class FluidWearOutbox(
  private val store: FluidWearStore,
  private val clock: () -> Long = System::currentTimeMillis,
  private val newId: () -> String = { UUID.randomUUID().toString() },
  /** Dopo quanto un evento consegnato e senza ricevuta si consegna di nuovo. */
  val resendAfterMillis: Long = DefaultResendAfterMillis,
  /** Quanto resta in elenco un evento chiuso. */
  val keepSettledMillis: Long = DefaultKeepSettledMillis,
) {
  private val lock = Any()
  private val state = MutableStateFlow(load())

  /** Gli eventi in coda e quelli chiusi da poco, nell'ordine in cui sono stati salvati. */
  val deliveries: StateFlow<List<FluidWearDelivery>> = state.asStateFlow()

  /**
   * Salva un evento al polso e lo mette in coda. [ttlMillis] è la sua vita: oltre, l'evento scade e non si applica più
   * (un comando con un effetto subito vuole una vita corta, per esempio 30 s; un preferito nessuna).
   */
  fun enqueue(type: String, payload: String = "", ttlMillis: Long? = null): FluidWearEvent {
    require(ttlMillis == null || ttlMillis > 0) { "ttlMillis deve essere positivo" }
    synchronized(lock) {
      val now = clock()
      val event = FluidWearEvent(
        id = newId(),
        type = type,
        payload = payload,
        createdAtMillis = now,
        expiresAtMillis = ttlMillis?.let { now + it },
      )
      commit(state.value + FluidWearDelivery(event, FluidWearDeliveryState.Queued))
      return event
    }
  }

  /**
   * Gli eventi da consegnare al trasporto adesso. Prima chiude «Scaduto» quelli oltre la scadenza. Con [resendAll] vero
   * (il telefono è appena tornato vicino) riconsegna tutti quelli in coda, senza aspettare [resendAfterMillis].
   */
  fun due(resendAll: Boolean = false): List<FluidWearOutgoing> = synchronized(lock) {
    expireLocked()
    val now = clock()
    state.value
      .filter { it.state == FluidWearDeliveryState.Queued }
      .filter { d -> resendAll || d.lastSentAtMillis == null || now - d.lastSentAtMillis >= resendAfterMillis }
      .map { FluidWearOutgoing(it.event, it.attempts) }
  }

  /** Il trasporto ha preso in carico l'evento [eventId] (per il Data Layer: `putDataItem` riuscito). */
  fun markSent(eventId: String) {
    synchronized(lock) {
      val now = clock()
      commit(
        state.value.map { d ->
          if (d.event.id == eventId && d.state == FluidWearDeliveryState.Queued) {
            d.copy(attempts = d.attempts + 1, lastSentAtMillis = now)
          } else {
            d
          }
        },
      )
    }
  }

  /**
   * La ricevuta del telefono. Torna l'evento chiuso, o `null` se la ricevuta non cambia niente (un evento che non c'è,
   * o già chiuso da un'altra ricevuta).
   */
  fun onReceipt(receipt: FluidWearReceipt): FluidWearDelivery? = synchronized(lock) {
    val current = state.value.firstOrNull { it.event.id == receipt.eventId } ?: return@synchronized null
    if (current.confirmedByPhone) return@synchronized null
    val settled = current.copy(
      state = when (receipt.outcome) {
        FluidWearReceiptOutcome.Registered -> FluidWearDeliveryState.Registered
        FluidWearReceiptOutcome.Expired -> FluidWearDeliveryState.Expired
        FluidWearReceiptOutcome.Refused -> FluidWearDeliveryState.Refused
      },
      settledAtMillis = clock(),
      confirmedByPhone = true,
    )
    commit(state.value.map { if (it.event.id == receipt.eventId) settled else it })
    settled
  }

  /** Chiude «Scaduto» gli eventi in coda oltre la scadenza e torna quelli appena chiusi. */
  fun expire(): List<FluidWearDelivery> = synchronized(lock) { expireLocked() }

  /** L'evento [eventId], se è ancora in elenco. */
  fun get(eventId: String): FluidWearDelivery? = state.value.firstOrNull { it.event.id == eventId }

  /** Toglie gli eventi chiusi più vecchi di [keepSettledMillis]. */
  fun prune() {
    synchronized(lock) { commit(state.value) }
  }

  private fun expireLocked(): List<FluidWearDelivery> {
    val now = clock()
    val expired = ArrayList<FluidWearDelivery>()
    val next = state.value.map { d ->
      if (d.state == FluidWearDeliveryState.Queued && d.event.isExpired(now)) {
        d.copy(state = FluidWearDeliveryState.Expired, settledAtMillis = now).also { expired += it }
      } else {
        d
      }
    }
    if (expired.isNotEmpty()) commit(next)
    return expired
  }

  /** Scrive su disco, poi pubblica: chi legge [deliveries] non vede mai uno stato che il disco non ha. */
  private fun commit(next: List<FluidWearDelivery>) {
    val now = clock()
    var kept = next.filter { d -> d.settledAtMillis == null || now - d.settledAtMillis < keepSettledMillis }
    if (kept.size > MaxDeliveries) {
      // Oltre il tetto si tolgono prima i chiusi piu' vecchi; quelli in coda restano tutti.
      val overflow = kept.size - MaxDeliveries
      val dropped = kept.filter { it.settledAtMillis != null }.sortedBy { it.settledAtMillis }.take(overflow)
        .map { it.event.id }.toSet()
      kept = kept.filter { it.event.id !in dropped }
    }
    val frozen = java.util.Collections.unmodifiableList(ArrayList(kept))
    store.write(FileName, encode(frozen))
    state.value = frozen
  }

  private fun load(): List<FluidWearDelivery> {
    val bytes = store.read(FileName) ?: return emptyList()
    return try {
      decode(bytes)
    } catch (e: IOException) {
      // Un file rotto non deve fermare l'orologio: la coda riparte vuota (gli elementi gia' affidati al Data Layer
      // arrivano lo stesso al telefono, e le loro ricevute si ignorano).
      emptyList()
    }
  }

  companion object {
    /** 15 minuti: abbastanza per non insistere a ogni risveglio, abbastanza poco per un telefono che ha perso l'evento. A occhio, 26/11/2026. */
    const val DefaultResendAfterMillis: Long = 15 * 60 * 1000L

    /** Un giorno: l'evento chiuso resta visibile al polso fino a domani. A occhio, 26/11/2026. */
    const val DefaultKeepSettledMillis: Long = 24 * 60 * 60 * 1000L

    /** Il tetto dell'elenco. */
    const val MaxDeliveries: Int = 200

    private const val FileName = "coda.fws"
    private const val FileVersion = 1

    private fun stateCode(state: FluidWearDeliveryState): Int = when (state) {
      FluidWearDeliveryState.Queued -> 0
      FluidWearDeliveryState.Registered -> 1
      FluidWearDeliveryState.Expired -> 2
      FluidWearDeliveryState.Refused -> 3
    }

    private fun stateOf(code: Int): FluidWearDeliveryState = when (code) {
      0 -> FluidWearDeliveryState.Queued
      1 -> FluidWearDeliveryState.Registered
      2 -> FluidWearDeliveryState.Expired
      3 -> FluidWearDeliveryState.Refused
      else -> throw IOException("stato sconosciuto: $code")
    }

    private fun encode(list: List<FluidWearDelivery>): ByteArray {
      val buffer = ByteArrayOutputStream()
      DataOutputStream(buffer).use { out ->
        out.writeInt(FileVersion)
        out.writeInt(list.size)
        for (d in list) {
          val event = FluidWearFormat.encode(d.event)
          out.writeInt(event.size)
          out.write(event)
          out.writeByte(stateCode(d.state))
          out.writeInt(d.attempts)
          out.writeLong(d.lastSentAtMillis ?: -1L)
          out.writeLong(d.settledAtMillis ?: -1L)
          out.writeBoolean(d.confirmedByPhone)
        }
      }
      return buffer.toByteArray()
    }

    private fun decode(bytes: ByteArray): List<FluidWearDelivery> {
      val input = DataInputStream(ByteArrayInputStream(bytes))
      if (input.readInt() != FileVersion) throw IOException("versione della coda sconosciuta")
      val count = input.readInt()
      if (count < 0 || count > MaxDeliveries * 4) throw IOException("conto della coda fuori misura: $count")
      val list = ArrayList<FluidWearDelivery>(count)
      repeat(count) {
        val size = input.readInt()
        if (size < 0 || size > input.available()) throw IOException("evento fuori misura")
        val raw = ByteArray(size).also { input.readFully(it) }
        val event = (FluidWearFormat.decode(raw) as? FluidWearDecoded.Event)?.event
          ?: throw IOException("evento illeggibile nella coda")
        val state = stateOf(input.readByte().toInt())
        val attempts = input.readInt()
        val lastSent = input.readLong().takeIf { it >= 0 }
        val settled = input.readLong().takeIf { it >= 0 }
        val confirmed = input.readBoolean()
        list += FluidWearDelivery(event, state, attempts, lastSent, settled, confirmed)
      }
      return java.util.Collections.unmodifiableList(list)
    }
  }
}

/** Un evento da consegnare al trasporto, col numero della consegna ([FluidWearFormat.encode]). */
class FluidWearOutgoing(val event: FluidWearEvent, val attempt: Int) {
  /** I byte da mettere sul Data Layer. */
  fun bytes(): ByteArray = FluidWearFormat.encode(event, attempt)

  /** L'indirizzo sul Data Layer. */
  val path: String get() = FluidWearPaths.event(event.id)
}
