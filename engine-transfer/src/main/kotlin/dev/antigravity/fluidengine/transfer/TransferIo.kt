package dev.antigravity.fluidengine.transfer

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.exp

/**
 * La velocita' e il tempo che manca, come li mostrano i download di Safari e del Finder: una media che dimentica piano
 * (esponenziale, costante di tempo [tauMillis]), cosi' il numero non salta a ogni pezzo; il tempo che manca compare
 * solo dopo [warmupMillis] di misure, prima e' «non so». Pura: il tempo lo passa chi la usa.
 *
 * I due tempi sono a occhio (25 novembre 2026): tre secondi bastano a togliere i salti di una rete mobile senza far
 * sembrare ferma una velocita' che cambia davvero.
 */
internal class TransferRate(
  private val tauMillis: Double = 3_000.0,
  private val warmupMillis: Long = 2_000L,
) {
  private var startTick = -1L
  private var lastTick = -1L
  private var lastBytes = 0L
  private var perMilli = 0.0

  /** Riparte da [bytes] all'istante [nowMillis] (un trasferimento che comincia o riprende). */
  fun reset(nowMillis: Long, bytes: Long) {
    startTick = nowMillis
    lastTick = nowMillis
    lastBytes = bytes
    perMilli = 0.0
  }

  /** Una misura: a [nowMillis] si e' a [bytes]. */
  fun sample(nowMillis: Long, bytes: Long) {
    if (lastTick < 0L) {
      reset(nowMillis, bytes)
      return
    }
    val dt = (nowMillis - lastTick).toDouble()
    if (dt <= 0.0) return
    val instant = (bytes - lastBytes).coerceAtLeast(0L) / dt
    perMilli = if (perMilli == 0.0) instant else perMilli + (instant - perMilli) * (1.0 - exp(-dt / tauMillis))
    lastTick = nowMillis
    lastBytes = bytes
  }

  /** Byte al secondo, o 0 se non si sa ancora. */
  val bytesPerSecond: Long get() = (perMilli * 1000.0).toLong()

  /** Il tempo per [left] byte, o null se le misure sono ancora poche o la velocita' e' zero. */
  fun remainingMillis(left: Long, nowMillis: Long): Long? {
    if (startTick < 0L || nowMillis - startTick < warmupMillis || perMilli <= 0.0 || left < 0L) return null
    return (left / perMilli).toLong()
  }
}

/** Una risposta HTTP gia' aperta: il codice e le intestazioni che contano. */
internal class TransferResponse(val connection: HttpURLConnection) {
  val code: Int = connection.responseCode
  fun header(name: String): String? = connection.getHeaderField(name)
  val contentLength: Long get() = connection.contentLengthLong
}

/** Le chiamate HTTP della coda, con la piattaforma (`HttpURLConnection`), come `EngineHttp`. */
internal class TransferHttp(private val config: FluidTransferConfig) {
  /**
   * Apre [method] su [url] seguendo i rinvii a mano (anche quelli che cambiano protocollo, come fa un CDN), con
   * [headers] a ogni salto. [prepare] puo' scrivere il corpo (caricamenti). Chiama [onConnection] appena c'e' una
   * connessione: la pausa la chiude per fermare una lettura bloccata.
   */
  fun open(
    method: String,
    url: String,
    headers: Map<String, String>,
    onConnection: (HttpURLConnection) -> Unit,
    prepare: ((HttpURLConnection) -> Unit)? = null,
  ): TransferResponse {
    var current = url
    var hops = 0
    while (true) {
      val connection = (URL(current).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = config.connectTimeoutMillis
        readTimeout = config.readTimeoutMillis
        instanceFollowRedirects = false
        useCaches = false
        setRequestProperty("User-Agent", config.userAgent)
        // Niente compressione: con gzip la lunghezza e i `Range` sarebbero quelli del contenuto compresso.
        setRequestProperty("Accept-Encoding", "identity")
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
      }
      onConnection(connection)
      prepare?.invoke(connection)
      val response = TransferResponse(connection)
      if (response.code in 300..399 && response.code != 304 && response.code != 308 && hops < MaxRedirects) {
        val location = response.header("Location")
        if (!location.isNullOrBlank()) {
          current = URL(URL(current), location).toString()
          hops++
          connection.disconnect()
          continue
        }
      }
      return response
    }
  }

  private companion object {
    const val MaxRedirects = 5
  }
}

/** `Content-Range: bytes 100-199/1000` → (100, 199, 1000); il totale e' -1 se e' «*». Null se non si legge. */
internal fun parseContentRange(value: String?): Triple<Long, Long, Long>? {
  val text = value?.trim() ?: return null
  val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""").find(text) ?: return null
  val (start, end, total) = match.destructured
  return Triple(start.toLong(), end.toLong(), if (total == "*") -1L else total.toLong())
}

/** `Range: bytes=0-1048575` (risposta 308 di un caricamento a pezzi) → l'ultimo byte confermato, o null. */
internal fun parseAckedRange(value: String?): Long? {
  val match = Regex("""bytes=(\d+)-(\d+)""").find(value?.trim() ?: return null) ?: return null
  return match.groupValues[2].toLong()
}

/** L'errore della coda: porta il motivo da mostrare. */
internal class TransferException(val failure: dev.antigravity.fluidengine.foundation.transfer.FluidTransferFailure) :
  IOException(failure.toString())
