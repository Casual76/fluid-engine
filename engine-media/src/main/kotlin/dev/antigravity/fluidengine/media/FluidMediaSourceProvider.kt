package dev.antigravity.fluidengine.media

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Il lato di chi **serve** il contratto delle sorgenti ([FluidMediaSourceContract], F8.14): un `ContentProvider` che mette
 * una [FluidMediaSource] dell'app a disposizione del selettore delle altre app Pampa. In F12 MyCloud Gallery lo estende
 * con la sua Libreria (la vista di adesso, la ricerca semantica, i solo-NAS e la loro consegna) e lo dichiara nel
 * manifest con l'azione [FluidMediaSourceContract.ACTION_SOURCE].
 *
 * Cosa fa da solo:
 * - **chi chiede**: ogni chiamata passa da [isCallerAllowed] (di serie: le app firmate come questa), altrimenti
 *   `SecurityException`, anche per le miniature;
 * - **le scadenze**: ogni metodo ha la sua ([infoDeadlineMillis], [pageDeadlineMillis], [deliverDeadlineMillis]); oltre,
 *   la risposta e' «non disponibile» o il rifiuto [FluidMediaRefusal.Unreachable], mai un'attesa infinita del binder;
 * - **la consegna**: gli indirizzi della sorgente passano a chi chiede col solo permesso di **lettura**
 *   (`grantUriPermission`), nell'ordine chiesto; un indirizzo che non si puo' dare conta fra i saltati. Gli indirizzi
 *   devono stare in un provider di questa app con `grantUriPermissions` (MyCloud Gallery: il suo `DocumentsProvider` con
 *   gli id opachi consegnati);
 * - **quanto vale la consegna** (R1): il permesso dato con `Context.grantUriPermission` non ha un'attività che lo chiuda.
 *   Vale finché qualcuno non chiama `revokeUriPermission`, il telefono non si riavvia o una delle due app non viene
 *   fermata o tolta: **bloccare la sorgente non lo toglie**. È come il selettore di iOS, dove ciò che è consegnato resta
 *   all'app che l'ha chiesto. Se il blocco deve chiudere anche ciò che è già stato consegnato, è il provider che serve i
 *   file a ricontrollare il blocco a ogni apertura (`openDocument`/`openFile`), perché ogni lettura passa da lì;

 * - **le miniature**: `openTypedAssetFile` su [FluidMediaSourceContract.thumbnailUri], col lato chiesto in
 *   `ContentResolver.EXTRA_SIZE`, scritte in JPEG su un tubo, mai il file intero.
 *
 * Il sistema crea il provider con il costruttore senza argomenti: la sorgente la da' [createSource], chiamata alla prima
 * richiesta (il contesto c'e' solo dopo `attachInfo`).
 */
abstract class FluidMediaSourceProvider : ContentProvider() {

  /** La sorgente che questo provider serve. Chiamata una volta, alla prima richiesta. */
  protected abstract fun createSource(context: Context): FluidMediaSource

  private val source: FluidMediaSource by lazy { createSource(requireNotNull(context) { "Provider non collegato." }) }

  /** La scadenza di [FluidMediaSourceContract.METHOD_INFO] e di [FluidMediaSourceContract.METHOD_ALBUMS]: 4 s. */
  protected open val infoDeadlineMillis: Long = 4_000L

  /** La scadenza di una pagina (anche una ricerca semantica): 8 s. */
  protected open val pageDeadlineMillis: Long = 8_000L

  /** La scadenza di una consegna (un video solo nel backup puo' dover arrivare dal NAS): 2 minuti. */
  protected open val deliverDeadlineMillis: Long = 120_000L

  /** La scadenza di una miniatura: 4 s. */
  protected open val thumbnailDeadlineMillis: Long = 4_000L

  /**
   * Chi puo' chiedere. Di serie: questa stessa app e le app firmate con la sua chiave (le app Pampa). MyCloud Gallery lo
   * sostituisce col suo controllo (`CallerGuard`) se vuole una lista sua.
   */
  protected open fun isCallerAllowed(callingPackage: String?): Boolean {
    val context = context ?: return false
    // Una chiamata dallo stesso processo (la stessa app) non ha un pacchetto di chi chiama: e' di casa.
    val caller = callingPackage ?: return android.os.Binder.getCallingUid() == android.os.Process.myUid()
    if (caller == context.packageName) return true
    return try {
      context.packageManager.checkSignatures(context.packageName, caller) == PackageManager.SIGNATURE_MATCH
    } catch (_: Exception) {
      false
    }
  }

  override fun onCreate(): Boolean = true

  override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
    val caller = callingPackage
    if (!isCallerAllowed(caller)) throw SecurityException("Sorgente non disponibile per questa app.")
    return when (method) {
      FluidMediaSourceContract.METHOD_INFO -> guarded(infoDeadlineMillis, unavailable()) {
        source.refresh()
        FluidMediaSourceContract.encodeInfo(source)
      }
      FluidMediaSourceContract.METHOD_ALBUMS -> guarded(infoDeadlineMillis, Bundle()) {
        if (!source.access.allowsListing()) Bundle() else FluidMediaSourceContract.encodeAlbums(source.albums())
      }
      FluidMediaSourceContract.METHOD_PAGE -> guarded(pageDeadlineMillis, errorPage()) {
        val (query, cursor, limit) = FluidMediaSourceContract.readQuery(extras)
        if (!source.access.allowsListing()) {
          FluidMediaSourceContract.encodePage(FluidMediaPage(emptyList()))
        } else {
          FluidMediaSourceContract.encodePage(source.page(query, cursor, limit))
        }
      }
      FluidMediaSourceContract.METHOD_DELIVER -> {
        val refusedLate = FluidMediaSourceContract.encodeDelivery(FluidMediaDelivery.Refused(FluidMediaRefusal.Unreachable))
        guarded(deliverDeadlineMillis, refusedLate) {
          val (items, request) = FluidMediaSourceContract.readDeliverRequest(extras, source.id)
          FluidMediaSourceContract.encodeDelivery(deliverTo(caller, items, request))
        }
      }
      else -> super.call(method, arg, extras)
    }
  }

  private suspend fun deliverTo(caller: String?, items: List<FluidMediaItem>, request: FluidMediaRequest): FluidMediaDelivery {
    if (items.isEmpty()) return FluidMediaDelivery.Refused(FluidMediaRefusal.NothingAvailable)
    val cap = minOf(FluidMediaSourceContract.MAX_DELIVERY, source.capabilities.maxDelivery ?: Int.MAX_VALUE, request.limit)
    if (items.size > cap) return FluidMediaDelivery.Refused(FluidMediaRefusal.tooMany(cap))
    when (val access = source.access) {
      is FluidMediaAccess.Locked -> return FluidMediaDelivery.NeedsUnlock(access.unlock)
      FluidMediaAccess.Full, FluidMediaAccess.Limited -> Unit
      else -> return FluidMediaDelivery.Refused(FluidMediaRefusal.NothingAvailable)
    }
    val delivery = source.deliver(items, request)
    if (delivery !is FluidMediaDelivery.Ready) return delivery
    val context = requireNotNull(context)
    var skipped = delivery.skipped
    val granted = delivery.media.filter { picked ->
      val ok = try {
        if (caller != null && caller != context.packageName) {
          context.grantUriPermission(caller, picked.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        true
      } catch (_: SecurityException) {
        false
      }
      if (!ok) skipped++
      ok
    }
    if (granted.isEmpty()) return FluidMediaDelivery.Refused(FluidMediaRefusal.NothingAvailable)
    return FluidMediaDelivery.Ready(granted, skipped)
  }

  override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?, signal: CancellationSignal?): AssetFileDescriptor {
    if (!isCallerAllowed(callingPackage)) throw SecurityException("Sorgente non disponibile per questa app.")
    if (uri.pathSegments.firstOrNull() != FluidMediaSourceContract.PATH_THUMBNAIL) throw FileNotFoundException("Non e' una miniatura.")
    val id = uri.getQueryParameter(FluidMediaSourceContract.QUERY_ID) ?: throw FileNotFoundException("Manca l'elemento.")
    val kind = uri.getQueryParameter(FluidMediaSourceContract.QUERY_KIND)?.let { FluidMediaKind.of(it) } ?: FluidMediaKind.Photo
    @Suppress("DEPRECATION")
    val size = (if (Build.VERSION.SDK_INT >= 33) opts?.getParcelable(ContentResolver.EXTRA_SIZE, Point::class.java)
      else opts?.getParcelable(ContentResolver.EXTRA_SIZE) as? Point)
    val side = (size?.let { maxOf(it.x, it.y) } ?: 256).coerceIn(32, 2048)
    signal?.throwIfCanceled()
    if (!source.access.allowsListing()) throw FileNotFoundException("Non disponibile.")
    val bitmap: Bitmap = try {
      runBlocking {
        withTimeout(thumbnailDeadlineMillis) {
          withContext(Dispatchers.IO) { source.thumbnail(FluidMediaItem(source.id, id, kind), side) }
        }
      }
    } catch (_: TimeoutCancellationException) {
      null
    } catch (_: Exception) {
      null
    } ?: throw FileNotFoundException("Nessuna miniatura.")
    val pipe = ParcelFileDescriptor.createPipe()
    Thread({
      try {
        FileOutputStream(pipe[1].fileDescriptor).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, ThumbnailQuality, out) }
      } catch (_: Exception) {
        // Chi legge ha chiuso prima della fine: niente da fare.
      } finally {
        try {
          pipe[1].close()
        } catch (_: Exception) {
        }
      }
    }, "fluid-media-thumbnail").start()
    return AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH)
  }

  override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? =
    if (uri.pathSegments.firstOrNull() == FluidMediaSourceContract.PATH_THUMBNAIL) arrayOf("image/jpeg") else null

  // --- Cio' che non si fa: niente righe, niente scritture ------------------------------------------------------------

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Sola lettura.")
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Sola lettura.")
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
    throw UnsupportedOperationException("Sola lettura.")

  private fun unavailable(): Bundle = Bundle().also {
    it.putInt(FluidMediaSourceContract.KEY_VERSION, FluidMediaSourceContract.VERSION)
    FluidMediaSourceContract.putAccess(it, FluidMediaAccess.Unavailable(FluidMediaSourceContract.UNAVAILABLE_NOT_READY))
  }

  private fun errorPage(): Bundle = FluidMediaSourceContract.encodePage(FluidMediaPage(emptyList())).also {
    it.putString(FluidMediaSourceContract.KEY_ERROR, "timeout")
  }

  /** Una chiamata sospesa dal filo del binder, con la scadenza: alla scadenza o a un errore, [fallback]. */
  private fun guarded(deadlineMillis: Long, fallback: Bundle, block: suspend () -> Bundle): Bundle = try {
    runBlocking { withTimeout(deadlineMillis) { withContext(Dispatchers.IO) { block() } } }
  } catch (e: SecurityException) {
    throw e
  } catch (_: Exception) {
    fallback
  }

  private companion object {
    const val ThumbnailQuality = 85
  }
}

/** Si possono elencare elementi con questo accesso. */
internal fun FluidMediaAccess.allowsListing(): Boolean = this == FluidMediaAccess.Full || this == FluidMediaAccess.Limited
