package dev.antigravity.fluidengine.wear.sync

import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Il ponte sopra il Data Layer di Wear OS (`DataClient` di Play services), con il client dell'app.
 *
 * Perché gli elementi di dati e non i messaggi: un `putDataItem` riesce anche col telefono lontano, Play services lo tiene
 * su disco e lo consegna appena i due dispositivi si ritrovano, anche dopo un riavvio. È la stessa promessa di
 * `transferUserInfo` di Watch Connectivity (fiducia alta: documentazione Android «Sync data items», letta il 26/11/2026).
 * Ogni messaggio sta in un elemento suo ([FluidWearPaths]), nel campo [BytesKey], ed è urgente (`setUrgent`): uno stato o
 * una ricevuta che arrivano mezz'ora dopo non servono.
 *
 * **Play services non è nel modulo** (`compileOnly`): l'app che usa questa classe aggiunge
 * `com.google.android.gms:play-services-wearable` alla sua build, con la sua versione, e passa il suo client. La libreria
 * è gratuita ma non open source (Android Software Development Kit License): l'engine la permette col permesso della
 * GPL-3.0 § 7 (`LICENSES/Permesso-librerie-Google.md`).
 *
 * Sul telefono: [publish] e [withdraw] per gli stati, e il servizio [FluidWearListenerService] per gli eventi. Sul polso:
 * `sync.outbox.enqueue(...)` e poi [flush]; [pullStatuses] all'avvio dell'app dell'orologio.
 */
class FluidWearDataLayer(
  private val dataClient: DataClient,
  /** Il ponte di questo lato. */
  val sync: FluidWearSync,
) {

  /** Telefono: pubblica [status]. L'orologio lo tiene e lo mostra anche ad app chiusa. */
  suspend fun publish(status: FluidWearStatus) {
    check(sync.role == FluidWearRole.Phone) { "gli stati li pubblica il telefono" }
    dataClient.putDataItem(requestFor(FluidWearPaths.status(status.key), FluidWearFormat.encode(status))).await()
  }

  /** Telefono: toglie lo stato [key] (non è più vero e non c'è niente da mostrare). */
  suspend fun withdraw(key: String) {
    dataClient.deleteDataItems(wildcard(FluidWearPaths.status(key))).await()
  }

  /**
   * Polso: consegna al Data Layer gli eventi in coda che lo aspettano ([FluidWearOutbox.due]). Torna quanti. Un
   * `putDataItem` che non riesce lascia l'evento in coda: si riprova al prossimo [flush].
   */
  suspend fun flush(resendAll: Boolean = false): Int {
    check(sync.role == FluidWearRole.Watch) { "gli eventi li salva il polso" }
    var sent = 0
    for (outgoing in sync.outbox.due(resendAll)) {
      dataClient.putDataItem(requestFor(outgoing.path, outgoing.bytes())).await()
      sync.outbox.markSent(outgoing.event.id)
      sent++
    }
    return sent
  }

  /** Telefono: rimanda la ricevuta [receipt]. */
  suspend fun sendReceipt(receipt: FluidWearReceipt) {
    dataClient.putDataItem(requestFor(FluidWearPaths.receipt(receipt.eventId), FluidWearFormat.encode(receipt))).await()
  }

  /**
   * Polso: legge gli stati già sul Data Layer (quelli arrivati prima che l'app fosse installata o mentre il servizio non
   * girava) e li tiene. Torna quanti sono cambiati.
   */
  suspend fun pullStatuses(): Int {
    val buffer = dataClient.getDataItems(wildcard(FluidWearPaths.StatusPrefix), DataClient.FILTER_PREFIX).await()
    var changed = 0
    try {
      for (item in buffer) {
        val path = item.uri.path ?: continue
        val bytes = bytesOf(item) ?: continue
        if (sync.route(path, bytes) is FluidWearRouted.StatusChanged) changed++
      }
    } finally {
      buffer.release()
    }
    return changed
  }

  /**
   * Toglie dal Data Layer l'evento [eventId] e la sua ricevuta, quando l'evento è chiuso: gli elementi non scadono da
   * soli, e senza pulizia resterebbero per sempre su tutti e due i dispositivi.
   */
  suspend fun forget(eventId: String) {
    dataClient.deleteDataItems(wildcard(FluidWearPaths.event(eventId))).await()
    dataClient.deleteDataItems(wildcard(FluidWearPaths.receipt(eventId))).await()
  }

  /** Smista gli eventi di `onDataChanged`. Va chiamato dentro il callback: il buffer vale solo lì. */
  fun handle(events: Iterable<DataEvent>): List<FluidWearRouted> = route(sync, events)

  companion object {
    /** [handle] senza il client: smista [events] nel ponte [sync]. */
    fun route(sync: FluidWearSync, events: Iterable<DataEvent>): List<FluidWearRouted> = events.mapNotNull { event ->
      val path = event.dataItem.uri.path ?: return@mapNotNull null
      if (!path.startsWith(FluidWearPaths.Root + "/")) return@mapNotNull null
      when (event.type) {
        DataEvent.TYPE_CHANGED -> bytesOf(event.dataItem)?.let { sync.route(path, it) }
        DataEvent.TYPE_DELETED -> sync.routeDeleted(path)
        else -> null
      }
    }

    /** Il campo della `DataMap` coi byte del messaggio. */
    const val BytesKey: String = "fws"

    /** La richiesta urgente per [path] coi [bytes]. */
    fun requestFor(path: String, bytes: ByteArray): PutDataRequest {
      val request = PutDataMapRequest.create(path)
      request.dataMap.putByteArray(BytesKey, bytes)
      return request.asPutDataRequest().setUrgent()
    }

    /** I byte del messaggio dentro [item], o `null` se non è un elemento del ponte. */
    fun bytesOf(item: DataItem): ByteArray? = try {
      DataMapItem.fromDataItem(item).dataMap.getByteArray(BytesKey)
    } catch (e: RuntimeException) {
      null
    }

    /** L'indirizzo [path] su tutti i dispositivi (nessun nodo nell'indirizzo). */
    fun wildcard(path: String): Uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(path).build()
  }
}

/**
 * Il servizio del Data Layer, pronto: smista gli elementi del ponte anche ad app chiusa (Wear OS e Android avviano il
 * servizio quando arriva qualcosa). L'app lo estende, dice il suo [role] e lo dichiara nel manifest:
 *
 * ```xml
 * <service android:name=".PonteOrologio" android:exported="true">
 *   <intent-filter>
 *     <action android:name="com.google.android.gms.wearable.DATA_CHANGED" />
 *     <data android:scheme="wear" android:host="*" android:pathPrefix="/fluid-wear" />
 *   </intent-filter>
 * </service>
 * ```
 *
 * Sul telefono si scrive [onEvent] (salva l'evento, col suo id, e dice se l'ha accettato): la ricevuta parte da sola,
 * **dopo** il salvataggio. Sull'orologio [onStatusChanged] chiede di ridisegnare le Tile e le complicazioni
 * (`FluidWearSurfaces.refresh` di `engine-wear`), [onDeliveryChanged] aggiorna ciò che mostra il polso.
 */
abstract class FluidWearListenerService : WearableListenerService() {

  /** Chi è questo lato. */
  protected abstract val role: FluidWearRole

  /** Il ponte del processo. */
  protected open fun sync(): FluidWearSync = FluidWearSync.get(this, role)

  /** L'adattatore col client del Data Layer di questo servizio. */
  protected open fun dataLayer(): FluidWearDataLayer = FluidWearDataLayer(Wearable.getDataClient(this), sync())

  override fun onDataChanged(dataEvents: DataEventBuffer) {
    val layer = dataLayer()
    val routed = layer.handle(dataEvents)
    if (routed.isEmpty()) return
    // Il callback gira su un filo del servizio, non sul principale: aspettare qui tiene vivo il servizio finche' la
    // ricevuta non e' partita.
    runBlocking {
      for (r in routed) dispatch(layer, r)
    }
  }

  private suspend fun dispatch(layer: FluidWearDataLayer, routed: FluidWearRouted) {
    when (routed) {
      is FluidWearRouted.StatusChanged -> onStatusChanged(routed.status)
      is FluidWearRouted.StatusRemoved -> onStatusRemoved(routed.key)
      is FluidWearRouted.EventArrived -> {
        // Prima il salvataggio dell'app, poi il registro, poi la ricevuta: mai una ricevuta per un evento non salvato.
        val accepted = onEvent(routed.event)
        val receipt = layer.sync.inbox.confirm(
          routed.event.id,
          if (accepted) FluidWearReceiptOutcome.Registered else FluidWearReceiptOutcome.Refused,
        )
        layer.sendReceipt(receipt)
      }
      is FluidWearRouted.ReceiptToSend -> layer.sendReceipt(routed.receipt)
      is FluidWearRouted.DeliverySettled -> {
        onDeliveryChanged(routed.delivery)
        layer.forget(routed.delivery.event.id)
      }
      is FluidWearRouted.Ignored -> Unit
    }
  }

  /** Orologio: uno stato nuovo è già tenuto; qui si chiede alle Tile e alle complicazioni di ridisegnarsi. */
  protected open suspend fun onStatusChanged(status: FluidWearStatus) = Unit

  /** Orologio: il telefono ha tolto lo stato [key]. */
  protected open suspend fun onStatusRemoved(key: String) = Unit

  /**
   * Telefono: salva [event] (col suo id, così una consegna ripetuta dopo un guasto non lo raddoppia) e torna vero se
   * l'ha accettato. Di serie `false`: un'app che non lo scrive risponde «Non registrato», che è la verità.
   */
  protected open suspend fun onEvent(event: FluidWearEvent): Boolean = false

  /** Orologio: un evento della coda è chiuso. */
  protected open suspend fun onDeliveryChanged(delivery: FluidWearDelivery) = Unit
}

/** Un `Task` di Play services come sospensione, senza la libreria `kotlinx-coroutines-play-services`. */
internal suspend fun <T> Task<T>.await(): T {
  if (isComplete) {
    exception?.let { throw it }
    if (isCanceled) throw kotlinx.coroutines.CancellationException("Task annullato")
    @Suppress("UNCHECKED_CAST")
    return result as T
  }
  return suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
      val error = task.exception
      when {
        error != null -> continuation.resumeWithException(error)
        task.isCanceled -> continuation.cancel()
        else -> {
          @Suppress("UNCHECKED_CAST")
          continuation.resume(task.result as T)
        }
      }
    }
  }
}
