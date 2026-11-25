package dev.antigravity.fluidengine.transfer

import dev.antigravity.fluidengine.foundation.FluidConditionReason
import dev.antigravity.fluidengine.foundation.FluidWork
import dev.antigravity.fluidengine.foundation.FluidWorkNeeds
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferFailure
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import java.io.File
import java.util.UUID

/**
 * Un trasferimento da mettere in coda ([FluidTransfers.enqueue]). L'app dice cosa, dove e con quali vincoli; le
 * credenziali non si salvano: le da' [FluidTransferAuthorizer] a ogni tentativo.
 *
 * Si costruisce con [download] o [upload].
 */
data class FluidTransferRequest(
  val id: String,
  val direction: FluidTransferDirection,
  val url: String,
  /** Il file: dove scrivere (scaricamento) o cosa mandare (caricamento). Un percorso assoluto dell'app. */
  val file: File,
  val title: String,
  val subtitle: String? = null,
  /** Il peso atteso (dal manifest, dal server): uno scaricamento di un altro peso non e' riuscito. -1 se non si sa. */
  val expectedBytes: Long = -1L,
  /** L'impronta SHA-256 attesa (esadecimale), o null. */
  val sha256: String? = null,
  val wifiOnly: Boolean = false,
  val chargingOnly: Boolean = false,
  /** Il genere di lavoro per la regola delle condizioni ([FluidWork.LargeDownload] o [FluidWork.LargeUpload] di serie). */
  val work: FluidWork = if (direction == FluidTransferDirection.Upload) FluidWork.LargeUpload else FluidWork.LargeDownload,
  /** Un'etichetta dell'app, per ritrovare i suoi trasferimenti («apk», «foto»). */
  val tag: String? = null,
  /** Caricamento: il tipo del contenuto (`Content-Type`). */
  val mimeType: String? = null,
  /** Caricamento: il metodo HTTP (`PUT` o `POST`). */
  val method: String = if (direction == FluidTransferDirection.Upload) "PUT" else "GET",
  /**
   * Caricamento: il server riprende a pezzi con `Content-Range` (il protocollo delle sessioni riprendibili di Google
   * Cloud Storage e di Drive: ogni pezzo riceve `308` con `Range`). Falso: un invio solo, che dopo una pausa ricomincia.
   */
  val resumableUpload: Boolean = false,
) {
  companion object {
    /** Uno scaricamento da [url] in [target] (scritto prima in `<target>.part`, rinominato alla fine). */
    fun download(
      url: String,
      target: File,
      title: String,
      id: String = UUID.randomUUID().toString(),
      subtitle: String? = null,
      expectedBytes: Long = -1L,
      sha256: String? = null,
      wifiOnly: Boolean = false,
      chargingOnly: Boolean = false,
      work: FluidWork = FluidWork.LargeDownload,
      tag: String? = null,
    ): FluidTransferRequest = FluidTransferRequest(
      id = id, direction = FluidTransferDirection.Download, url = url, file = target, title = title, subtitle = subtitle,
      expectedBytes = expectedBytes, sha256 = sha256, wifiOnly = wifiOnly, chargingOnly = chargingOnly, work = work, tag = tag,
    )

    /** Un caricamento di [source] verso [url]. Con [resumable] il server riprende a pezzi (`Content-Range`). */
    fun upload(
      url: String,
      source: File,
      title: String,
      id: String = UUID.randomUUID().toString(),
      subtitle: String? = null,
      mimeType: String? = null,
      method: String = "PUT",
      resumable: Boolean = false,
      wifiOnly: Boolean = false,
      chargingOnly: Boolean = false,
      work: FluidWork = FluidWork.LargeUpload,
      tag: String? = null,
    ): FluidTransferRequest = FluidTransferRequest(
      id = id, direction = FluidTransferDirection.Upload, url = url, file = source, title = title, subtitle = subtitle,
      wifiOnly = wifiOnly, chargingOnly = chargingOnly, work = work, tag = tag, mimeType = mimeType, method = method,
      resumableUpload = resumable,
    )
  }
}

/**
 * Un trasferimento come sta nella coda salvata: la richiesta e dove e' arrivato. E' la forma su disco
 * ([FluidTransferStore]); l'interfaccia legge [toItem].
 *
 * @property validator l'`ETag` forte o il `Last-Modified` del primo scaricamento: si rimanda con `If-Range`, cosi' un
 *   file cambiato sul server non viene cucito a meta' con quello vecchio.
 * @property uploadAcked caricamento a pezzi: i byte che il server ha confermato.
 */
data class FluidTransferRecord(
  val request: FluidTransferRequest,
  val state: FluidTransferState = FluidTransferState.Queued,
  val bytesDone: Long = 0L,
  val bytesTotal: Long = request.expectedBytes,
  val validator: String? = null,
  val resumable: Boolean = true,
  val failure: FluidTransferFailure? = null,
  val waitingFor: List<FluidConditionReason> = emptyList(),
  val attempts: Int = 0,
  val forced: Boolean = false,
  val createdAtMillis: Long = 0L,
  val updatedAtMillis: Long = 0L,
  val finishedAtMillis: Long? = null,
  val uploadAcked: Long = 0L,
) {
  val id: String get() = request.id

  /** I vincoli del trasferimento per la regola delle condizioni. */
  val needs: FluidWorkNeeds get() = FluidWorkNeeds(wifiOnly = request.wifiOnly, chargingOnly = request.chargingOnly)

  /** Il trasferimento come si mostra. [bytesPerSecond] e [remainingMillis] li sa solo chi lo sta facendo. */
  fun toItem(bytesPerSecond: Long = 0L, remainingMillis: Long? = null): FluidTransferItem = FluidTransferItem(
    id = id,
    title = request.title,
    direction = request.direction,
    state = state,
    bytesDone = bytesDone,
    bytesTotal = bytesTotal,
    bytesPerSecond = if (state == FluidTransferState.Running) bytesPerSecond else 0L,
    remainingMillis = if (state == FluidTransferState.Running) remainingMillis else null,
    subtitle = request.subtitle,
    failure = failure,
    waitingFor = if (state == FluidTransferState.Waiting) waitingFor else emptyList(),
    canForce = state == FluidTransferState.Waiting && waitingFor.isNotEmpty() && waitingFor.all { it.forceable } && !forced,
    resumable = resumable,
    attempts = attempts,
    wifiOnly = request.wifiOnly,
    chargingOnly = request.chargingOnly,
    createdAtMillis = createdAtMillis,
    finishedAtMillis = finishedAtMillis,
  )
}
