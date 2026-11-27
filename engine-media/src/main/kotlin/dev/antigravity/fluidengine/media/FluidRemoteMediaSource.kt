package dev.antigravity.fluidengine.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Di chi si fida chi legge, fra le sorgenti delle altre app ([FluidMediaSources.discover]).
 *
 * Di serie [SameSignature]: solo le app firmate con la stessa chiave (le app Pampa). Una classe e non un enum (3.11).
 */
@Stable
class FluidMediaSourceTrust private constructor(
  internal val sameSignature: Boolean,
  internal val packages: Set<String>,
  internal val any: Boolean,
) {
  internal fun accepts(context: Context, packageName: String): Boolean {
    if (any || packageName in packages) return true
    if (!sameSignature) return false
    return try {
      context.packageManager.checkSignatures(context.packageName, packageName) == PackageManager.SIGNATURE_MATCH
    } catch (_: Exception) {
      false
    }
  }

  companion object {
    /** Le app firmate come questa (di serie). */
    val SameSignature: FluidMediaSourceTrust = FluidMediaSourceTrust(sameSignature = true, packages = emptySet(), any = false)

    /** Le app firmate come questa e, in piu', i pacchetti dati (un'app di prova firmata con un'altra chiave). */
    fun sameSignatureOr(vararg packages: String): FluidMediaSourceTrust =
      FluidMediaSourceTrust(sameSignature = true, packages = packages.toSet(), any = false)

    /** Ogni app che espone il contratto. Solo per le prove: una sorgente sconosciuta vede cosa cerca la persona. */
    val Any: FluidMediaSourceTrust = FluidMediaSourceTrust(sameSignature = false, packages = emptySet(), any = true)
  }
}

/** Le sorgenti delle altre app ([FluidMediaSourceContract]). */
object FluidMediaSources {
  /**
   * Le sorgenti che le altre app installate offrono (in F12 MyCloud Gallery), di cui ci si fida secondo [trust], gia'
   * collegate (ognuna ha detto chi e' e cosa sa fare). Esclude questa stessa app: una sorgente sua l'app la usa
   * direttamente. Fuori dal filo principale; una sorgente che non risponde entro [timeoutMillis] resta fuori.
   */
  suspend fun discover(
    context: Context,
    trust: FluidMediaSourceTrust = FluidMediaSourceTrust.SameSignature,
    timeoutMillis: Long = 3_000L,
  ): List<FluidRemoteMediaSource> = withContext(Dispatchers.IO) {
    val providers = try {
      context.packageManager.queryIntentContentProviders(Intent(FluidMediaSourceContract.ACTION_SOURCE), 0)
    } catch (_: Exception) {
      emptyList()
    }
    providers.mapNotNull { info ->
      val provider = info.providerInfo ?: return@mapNotNull null
      val pkg = provider.packageName ?: return@mapNotNull null
      if (pkg == context.packageName || !trust.accepts(context, pkg)) return@mapNotNull null
      val authority = provider.authority?.split(';')?.firstOrNull() ?: return@mapNotNull null
      withTimeoutOrNull(timeoutMillis) { connect(context, authority) }
    }
  }

  /**
   * La sorgente del provider con l'[authority] data, gia' collegata; null se non risponde o parla un'altra versione del
   * contratto. Per chi conosce gia' l'autorita' (e per le prove).
   */
  suspend fun connect(context: Context, authority: String): FluidRemoteMediaSource? = withContext(Dispatchers.IO) {
    val info = remoteCall(context.contentResolver, authority, FluidMediaSourceContract.METHOD_INFO, null) ?: return@withContext null
    if (info.getInt(FluidMediaSourceContract.KEY_VERSION, 0) < 1) return@withContext null
    val id = info.getString(FluidMediaSourceContract.KEY_ID) ?: return@withContext null
    FluidRemoteMediaSource(
      context = context.applicationContext ?: context,
      authority = authority,
      id = id,
      label = info.getString(FluidMediaSourceContract.KEY_LABEL) ?: id,
      capabilities = FluidMediaSourceContract.readCapabilities(info),
      initialAccess = FluidMediaSourceContract.readAccess(info),
    )
  }

  internal fun remoteCall(resolver: ContentResolver, authority: String, method: String, extras: Bundle?): Bundle? = try {
    resolver.call(Uri.parse("content://$authority"), method, null, extras)
  } catch (_: SecurityException) {
    null
  } catch (_: IllegalArgumentException) {
    // Il provider non c'e' (l'app e' stata tolta).
    null
  } catch (_: Exception) {
    null
  }
}

/**
 * Una sorgente di un'altra app, letta attraverso il contratto ([FluidMediaSourceContract]): la si ottiene da
 * [FluidMediaSources.discover] (o [FluidMediaSources.connect]) e si passa al selettore come le altre.
 *
 * Ogni chiamata gira fuori dal filo principale con una scadenza: una sorgente che non risponde diventa
 * [FluidMediaAccess.Unavailable] ([FluidMediaSourceContract.UNAVAILABLE_GONE]) e la griglia lo dice, senza attese. Una
 * pagina o gli album che la sorgente non riesce a dare (la sua scadenza, un suo errore: la risposta con `error`) sono
 * un'`IOException` (R3): il selettore dice «… non è disponibile» con «Riprova», mai una libreria vuota.
 */
@Stable
class FluidRemoteMediaSource internal constructor(
  private val context: Context,
  /** L'autorita' del provider. */
  val authority: String,
  override val id: String,
  override val label: String,
  override val capabilities: FluidMediaSourceCapabilities,
  initialAccess: FluidMediaAccess,
) : FluidMediaSource {

  override var access: FluidMediaAccess by mutableStateOf(initialAccess)
    private set

  private val resolver: ContentResolver get() = context.contentResolver

  override suspend fun refresh() {
    val info = call(FluidMediaSourceContract.METHOD_INFO, null, InfoTimeout)
    access = FluidMediaSourceContract.readAccess(info)
  }

  override suspend fun albums(): List<FluidMediaAlbum> {
    if (!capabilities.albums) return emptyList()
    val bundle = call(FluidMediaSourceContract.METHOD_ALBUMS, null, InfoTimeout) ?: return gone(emptyList())
    return FluidMediaSourceContract.readAlbums(bundle, id)
  }

  override suspend fun page(query: FluidMediaQuery, cursor: String?, limit: Int): FluidMediaPage {
    val bundle = call(FluidMediaSourceContract.METHOD_PAGE, FluidMediaSourceContract.encodeQuery(query, cursor, limit), PageTimeout)
      ?: return gone(FluidMediaPage(emptyList()))
    return FluidMediaSourceContract.readPage(bundle, id)
  }

  override suspend fun thumbnail(item: FluidMediaItem, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
    val signal = CancellationSignal()
    val opts = Bundle().apply { putParcelable(ContentResolver.EXTRA_SIZE, Point(sizePx, sizePx)) }
    withTimeoutOrNull(ThumbnailTimeout) {
      suspendCancellableCoroutine<Bitmap?> { continuation ->
        continuation.invokeOnCancellation { signal.cancel() }
        val bitmap = try {
          resolver.openTypedAssetFileDescriptor(FluidMediaSourceContract.thumbnailUri(authority, item), "image/*", opts, signal)
            ?.use { afd -> afd.createInputStream().use { BitmapFactory.decodeStream(it) } }
        } catch (_: Exception) {
          null
        }
        continuation.resume(bitmap)
      }
    }
  }

  override suspend fun deliver(
    items: List<FluidMediaItem>,
    request: FluidMediaRequest,
    onProgress: (FluidMediaProgress) -> Unit,
  ): FluidMediaDelivery {
    onProgress(FluidMediaProgress(0, items.size))
    val bundle = call(FluidMediaSourceContract.METHOD_DELIVER, FluidMediaSourceContract.encodeDeliverRequest(items, request), DeliverTimeout)
      ?: return gone(FluidMediaDelivery.Refused(FluidMediaRefusal.Unreachable))
    val delivery = FluidMediaSourceContract.readDelivery(bundle, id)
    if (delivery is FluidMediaDelivery.NeedsUnlock) access = FluidMediaAccess.Locked(delivery.unlock)
    if (delivery is FluidMediaDelivery.Ready) onProgress(FluidMediaProgress(items.size, items.size))
    return delivery
  }

  private suspend fun call(method: String, extras: Bundle?, timeout: Long): Bundle? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(timeout) { FluidMediaSources.remoteCall(resolver, authority, method, extras) }
  }

  private fun <T> gone(value: T): T {
    access = FluidMediaAccess.Unavailable(FluidMediaSourceContract.UNAVAILABLE_GONE)
    return value
  }

  override fun toString(): String = "FluidRemoteMediaSource($id @ $authority)"

  private companion object {
    const val InfoTimeout = 5_000L
    const val PageTimeout = 10_000L
    const val ThumbnailTimeout = 5_000L
    const val DeliverTimeout = 125_000L
  }
}
