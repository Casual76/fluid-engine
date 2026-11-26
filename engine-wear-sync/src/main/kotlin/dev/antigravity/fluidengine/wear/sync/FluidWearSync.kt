package dev.antigravity.fluidengine.wear.sync

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Il ponte fra il telefono e l'orologio, un lato alla volta: la coda del polso ([outbox]), il registro del telefono
 * ([inbox]) e gli stati tenuti dall'orologio ([statuses]), più lo smistamento dei messaggi che arrivano ([route]).
 *
 * Non sa niente del trasporto: riceve un indirizzo e dei byte e dice cosa è successo ([FluidWearRouted]). Col Data Layer
 * di Play services c'è l'adattatore pronto, [FluidWearDataLayer], e il servizio [FluidWearListenerService]; un'app che
 * porta i messaggi in un altro modo (un suo canale, la prova della galleria) chiama [route] coi suoi byte.
 *
 * Il [role] decide quali messaggi spettano a questo lato: il telefono tratta solo gli eventi, l'orologio solo gli stati
 * e le ricevute. Così un elemento che il Data Layer rimanda anche al dispositivo che l'ha scritto non si tratta due
 * volte.
 */
class FluidWearSync(
  val role: FluidWearRole,
  private val store: FluidWearStore,
  private val clock: () -> Long = System::currentTimeMillis,
  private val newId: () -> String = { UUID.randomUUID().toString() },
) {
  /** La coda del polso (lato orologio). */
  val outbox: FluidWearOutbox by lazy { FluidWearOutbox(store, clock, newId) }

  /** Il registro degli eventi trattati (lato telefono). */
  val inbox: FluidWearInbox by lazy { FluidWearInbox(store, clock) }

  /** Gli stati tenuti (lato orologio). */
  val statuses: FluidWearStatusStore by lazy { FluidWearStatusStore(store) }

  /** L'ora di questo lato, quella con cui il ponte decide le scadenze. */
  fun now(): Long = clock()

  /** Smista un elemento cambiato all'indirizzo [path] coi suoi [bytes]. Non lancia. */
  fun route(path: String, bytes: ByteArray): FluidWearRouted {
    val kind = kindOf(path) ?: return FluidWearRouted.Ignored("fuori da ${FluidWearPaths.Root}: $path")
    if (!kind.belongsTo(role)) return FluidWearRouted.Ignored("non spetta a $role: $path")
    val tail = path.substringAfterLast('/')
    return when (val decoded = FluidWearFormat.decode(bytes)) {
      is FluidWearDecoded.Status -> {
        val status = decoded.status
        when {
          kind != Kind.Status || status.key != tail -> FluidWearRouted.Ignored("stato all'indirizzo sbagliato: $path")
          statuses.put(status) -> FluidWearRouted.StatusChanged(status)
          else -> FluidWearRouted.Ignored("stato non più nuovo di quello tenuto: ${status.key}")
        }
      }
      is FluidWearDecoded.Event -> {
        val event = decoded.event
        if (kind != Kind.Event || event.id != tail) return FluidWearRouted.Ignored("evento all'indirizzo sbagliato: $path")
        when (val result = inbox.receive(event)) {
          is FluidWearInboxResult.New -> FluidWearRouted.EventArrived(result.event)
          is FluidWearInboxResult.Repeated -> FluidWearRouted.ReceiptToSend(result.receipt, repeated = true)
          is FluidWearInboxResult.Expired -> FluidWearRouted.ReceiptToSend(result.receipt, repeated = false)
        }
      }
      is FluidWearDecoded.Receipt -> {
        val receipt = decoded.receipt
        if (kind != Kind.Receipt || receipt.eventId != tail) {
          return FluidWearRouted.Ignored("ricevuta all'indirizzo sbagliato: $path")
        }
        outbox.onReceipt(receipt)?.let { FluidWearRouted.DeliverySettled(it) }
          ?: FluidWearRouted.Ignored("ricevuta già vista o di un evento che non c'è: ${receipt.eventId}")
      }
      is FluidWearDecoded.Unsupported -> {
        val id = decoded.eventId
        if (kind == Kind.Event && id != null && id == tail) {
          FluidWearRouted.ReceiptToSend(inbox.refuseUnsupported(id), repeated = false)
        } else {
          FluidWearRouted.Ignored("versione ${decoded.version} del formato, questa legge la ${FluidWearFormat.Version}")
        }
      }
      is FluidWearDecoded.Unreadable -> FluidWearRouted.Ignored("illeggibile: ${decoded.reason}")
    }
  }

  /** Smista un elemento tolto all'indirizzo [path]: per l'orologio, il telefono ha tolto uno stato. */
  fun routeDeleted(path: String): FluidWearRouted {
    if (role != FluidWearRole.Watch || !path.startsWith(FluidWearPaths.StatusPrefix)) {
      return FluidWearRouted.Ignored("niente da togliere: $path")
    }
    val key = path.removePrefix(FluidWearPaths.StatusPrefix)
    return if (statuses.remove(key)) FluidWearRouted.StatusRemoved(key) else FluidWearRouted.Ignored("stato già assente: $key")
  }

  private enum class Kind {
    Status, Event, Receipt;

    fun belongsTo(role: FluidWearRole): Boolean = when (this) {
      Status, Receipt -> role == FluidWearRole.Watch
      Event -> role == FluidWearRole.Phone
    }
  }

  private fun kindOf(path: String): Kind? = when {
    path.startsWith(FluidWearPaths.StatusPrefix) -> Kind.Status
    path.startsWith(FluidWearPaths.EventPrefix) -> Kind.Event
    path.startsWith(FluidWearPaths.ReceiptPrefix) -> Kind.Receipt
    else -> null
  }

  companion object {
    @Volatile private var instance: FluidWearSync? = null

    /**
     * Il ponte del processo, con l'archivio su disco in `noBackupFilesDir/fluid-wear-sync` (fuori dal backup). Lo stesso
     * oggetto per il servizio del Data Layer, la Tile, la complicazione e l'app: una coda sola, un registro solo.
     */
    fun get(context: Context, role: FluidWearRole): FluidWearSync {
      instance?.let {
        check(it.role == role) { "il ponte di questo processo è già ${it.role}, non $role" }
        return it
      }
      return synchronized(this) {
        instance ?: FluidWearSync(
          role = role,
          store = FluidWearFileStore(File(context.applicationContext.noBackupFilesDir, DirectoryName)),
        ).also { instance = it }
      }.also { check(it.role == role) { "il ponte di questo processo è già ${it.role}, non $role" } }
    }

    /** La cartella dell'archivio, dentro `noBackupFilesDir`. */
    const val DirectoryName: String = "fluid-wear-sync"
  }
}

/** Che cosa ha fatto [FluidWearSync.route] con un messaggio. */
sealed interface FluidWearRouted {
  /** Orologio: uno stato nuovo, già tenuto. Si chiede alle Tile e alle complicazioni di ridisegnarsi. */
  data class StatusChanged(val status: FluidWearStatus) : FluidWearRouted

  /** Orologio: il telefono ha tolto lo stato [key]. */
  data class StatusRemoved(val key: String) : FluidWearRouted

  /** Telefono: un evento nuovo. L'app lo salva, poi `inbox.confirm(event.id)` e rimanda la ricevuta. */
  data class EventArrived(val event: FluidWearEvent) : FluidWearRouted

  /** Telefono: una ricevuta da rimandare subito (consegna ripetuta, evento scaduto, formato più nuovo). */
  data class ReceiptToSend(val receipt: FluidWearReceipt, val repeated: Boolean) : FluidWearRouted

  /** Orologio: un evento chiuso dalla ricevuta del telefono. */
  data class DeliverySettled(val delivery: FluidWearDelivery) : FluidWearRouted

  /** Niente da fare, con il perché (per il log). */
  data class Ignored(val reason: String) : FluidWearRouted
}
