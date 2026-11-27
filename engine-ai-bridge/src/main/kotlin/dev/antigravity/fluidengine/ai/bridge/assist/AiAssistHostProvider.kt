package dev.antigravity.fluidengine.ai.bridge.assist

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import dev.antigravity.fluidengine.ai.bridge.BridgeProtocol
import dev.antigravity.fluidengine.ai.memory.AiMemoryProposalResult
import dev.antigravity.fluidengine.ai.memory.AiMemoryProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * L'ospite dell'assistenza, per PampAI: un `ContentProvider` «solo `call`» con autorita' `<applicationId>.ai.assist`,
 * `exported="true"` **senza `android:permission`** e il meta-data `dev.antigravity.fluidengine.ai.ASSIST_HOST` con
 * valore `3`. La sottoclasse da' l'[AiAssistHost] del processo (con l'esecutore, il registro del consenso e la tabella) e,
 * se vuole, la memoria per i ricordi delle altre app (`memory`, R3-S1b); il resto sta qui.
 *
 * **Il cancello**, a ogni chiamata e prima di leggere la richiesta, su ogni metodo (anche uno sconosciuto):
 * 1. la firma di chi chiama (`Binder.getCallingUid()`) e' quella dell'ospite ([compareSignatures]), altrimenti
 *    `SecurityException`, che l'app legge come `not_authorized`;
 * 2. il pacchetto viene dal sistema ([packagesForUid]) e dev'essere uno solo: un'app non puo' dire di essere un'altra,
 *    e un uid condiviso non e' ammesso;
 * 3. poi la tabella, il blocco e il consenso, nell'[AiAssistHost].
 *
 * **Il canale di ritorno.** `assist_ask` porta nel `Bundle` un `IBinder` dell'app (`callback`): l'ospite ci manda gli
 * invii come transazioni a senso unico e, se muore (`linkToDeath`), ferma la domanda. In risposta da' un suo `Binder`
 * (`token`), che l'app osserva per sapere se PampAI muore a meta'. L'app tiene aperto il provider per tutta la domanda,
 * e il sistema tiene il processo di PampAI alla priorita' dell'app.
 *
 * **I ricordi delle altre app** (F9.3, R3-S1b): i metodi di `AiMemoryProtocol` passano dalla stessa porta, dopo lo stesso
 * cancello, se la sottoclasse da' la memoria ([memory]).
 */
abstract class AiAssistHostProvider : ContentProvider() {

  /** L'ospite del processo: lo stesso oggetto per ogni chiamata (le domande in corso vivono li'). */
  abstract fun host(): AiAssistHost

  final override fun onCreate(): Boolean = true

  final override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
    // Il cancello sta fuori dal try: la SecurityException arriva all'app com'e'.
    val uid = Binder.getCallingUid()
    if (compareSignatures(uid, Process.myUid()) != PackageManager.SIGNATURE_MATCH) {
      throw SecurityException("chiamante con un'altra firma: PampAI risponde solo alle app firmate come lei")
    }
    val packageName = packagesForUid(uid)?.singleOrNull() ?: throw SecurityException("pacchetto del chiamante non univoco")
    val request = AiAssistProtocol.parse(extras?.getString(AiAssistProtocol.KEY_JSON))
    val out = Bundle()
    val response: JsonObject = try {
      val host = host()
      when (method) {
        AiAssistProtocol.METHOD_NEGOTIATE -> host.negotiate(request)
        AiAssistProtocol.METHOD_SCOPE -> host.scope(caller(host, packageName, withTools = false))
        AiAssistProtocol.METHOD_ASK -> ask(host, caller(host, packageName, withTools = true), request, extras?.getBinder(AiAssistProtocol.KEY_CALLBACK), out)
        AiAssistProtocol.METHOD_POLL -> host.poll(caller(host, packageName, withTools = false), request)
        AiAssistProtocol.METHOD_CANCEL -> host.cancel(caller(host, packageName, withTools = false), request)
        in AiMemoryProtocol.Methods -> memoryCall(host, method, extras?.getString(AiAssistProtocol.KEY_JSON), packageName)
        else -> BridgeProtocol.errorResponse("metodo sconosciuto: $method")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      // Mai il messaggio: potrebbe portare un dettaglio dell'esecutore.
      AiAssistProtocol.errorResponse(AiAssistError(AiAssistError.Failed))
    }
    out.putString(AiAssistProtocol.KEY_JSON, response.toString())
    return out
  }

  private fun ask(host: AiAssistHost, caller: AiAssistCaller, request: JsonObject?, callback: IBinder?, out: Bundle): JsonObject {
    val listener = callback?.let { BinderListener(it) }
    val outcome = host.ask(caller, request, listener)
    val session = outcome.session ?: return outcome.response
    val token = Binder()
    session.token = token
    out.putBinder(AiAssistProtocol.KEY_TOKEN, token)
    if (listener != null) {
      val alive = listener.watch { host.callerGone(session.id) }
      if (!alive) host.callerGone(session.id)
    }
    return outcome.response
  }

  /**
   * **I ricordi delle altre app** (F9.3, `AiMemoryProtocol`: `memory_propose`, `memory_answer`, `memory_undo`,
   * `memory_forget_mine`). Arrivano qui **dopo il cancello** (la firma, il pacchetto dal sistema) e **dopo il «no» di
   * F9.1**: a un'app che l'utente ha rifiutato o bloccato in «App che usano PampAI» l'ospite risponde `not_allowed` senza
   * chiamare qui (D5 di Alessio, R3-S1b). Il resto lo decide la memoria: la tabella delle app che propongono e i loro
   * argomenti, il blocco della memoria, i tetti. In PampAI:
   *
   * ```kotlin
   * override suspend fun memory(method: String, json: String, caller: AiAssistCaller): String =
   *   graph().memoryInbox.handle(method, json, AiMemoryCaller(caller.packageName, caller.label))   // engine-ai-memory
   * ```
   *
   * Torna il JSON dell'esito (`AiMemoryProposalResult`), o null (di serie): PampAI non ha la memoria, e i metodi
   * rispondono «metodo sconosciuto» come prima. [caller] e' verificato dal sistema e il suo nome viene dalla tabella di F9.1
   * (o dal sistema). Si chiama sul filo di Binder, al piu' per [MemoryTimeoutMillis].
   */
  protected open suspend fun memory(method: String, json: String, caller: AiAssistCaller): String? = null

  private fun memoryCall(host: AiAssistHost, method: String, json: String?, packageName: String): JsonObject {
    if (host.consents.appConsent(packageName) == AiAssistConsentState.Denied) return memoryResult(AiMemoryProposalResult.OutcomeNotAllowed)
    val caller = caller(host, packageName, withTools = false)
    var handled = true
    val answer = runBlocking {
      withTimeoutOrNull(MemoryTimeoutMillis) { memory(method, json ?: "{}", caller).also { if (it == null) handled = false } }
    }
    if (!handled) return BridgeProtocol.errorResponse("metodo sconosciuto: $method")
    val out = answer?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    return out ?: memoryResult(AiMemoryProposalResult.OutcomeInvalid)
  }

  private fun memoryResult(outcome: String): JsonObject = Json.parseToJsonElement(AiMemoryProposalResult(outcome).toJson()) as JsonObject

  private fun caller(host: AiAssistHost, packageName: String, withTools: Boolean): AiAssistCaller = AiAssistCaller(
    packageName = packageName,
    label = host.policy.entryFor(packageName)?.name ?: labelOf(packageName),
    toolsAuthority = if (withTools) runCatching { toolsAuthorityOf(packageName) }.getOrNull() else null,
  )

  /**
   * Il confronto delle firme come lo fa il `PackageManager`: [PackageManager.SIGNATURE_MATCH] se sono le stesse. A
   * parte perche' il `PackageManager` di Robolectric risponde sempre «uguali».
   */
  protected open fun compareSignatures(callingUid: Int, ownUid: Int): Int =
    context?.packageManager?.checkSignatures(callingUid, ownUid) ?: PackageManager.SIGNATURE_UNKNOWN_PACKAGE

  /** I pacchetti dell'uid che chiama, dal sistema. */
  protected open fun packagesForUid(uid: Int): Array<String>? = context?.packageManager?.getPackagesForUid(uid)

  /** Il nome di un'app che non e' in tabella (solo per i registri: un'app fuori tabella non passa). */
  protected open fun labelOf(packageName: String): String = runCatching {
    val pm = context!!.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
  }.getOrDefault(packageName)

  /** L'ospite dei tool dell'app che chiama (il provider col meta-data `TOOL_HOST` nel suo pacchetto), se c'e'. */
  @Suppress("DEPRECATION")
  protected open fun toolsAuthorityOf(packageName: String): String? {
    val pm = context?.packageManager ?: return null
    val info = pm.getPackageInfo(packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)
    return info.providers?.firstOrNull { it.metaData?.getInt(BridgeProtocol.META_TOOL_HOST, 0) == 1 }?.authority?.substringBefore(';')
  }

  companion object {
    /** Quanto aspetta al piu' la memoria per un metodo dei ricordi: 10 s, a occhio (27/11/2026; una scrittura cifrata). */
    const val MemoryTimeoutMillis: Long = 10_000L
  }

  // Un provider «solo call»: le query non esistono.
  final override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
  final override fun getType(uri: Uri): String? = null
  final override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  final override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  final override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/** Il canale di ritorno visto dall'ospite: una transazione a senso unico per invio, e la morte dell'app. */
internal class BinderListener(private val binder: IBinder) : AiAssistListener {
  private var recipient: IBinder.DeathRecipient? = null

  override fun deliver(batch: String): Boolean {
    val data = Parcel.obtain()
    return try {
      data.writeInt(AiAssistProtocol.CALLBACK_MAGIC)
      data.writeString(batch)
      binder.transact(AiAssistProtocol.CODE_EVENTS, data, null, IBinder.FLAG_ONEWAY)
    } catch (e: RemoteException) {
      false
    } finally {
      data.recycle()
    }
  }

  /** Osserva la morte dell'app; falso se e' gia' morta. Nello stesso processo non c'e' niente da osservare. */
  fun watch(onDeath: () -> Unit): Boolean {
    val r = IBinder.DeathRecipient { onDeath() }
    return try {
      binder.linkToDeath(r, 0)
      recipient = r
      true
    } catch (e: RemoteException) {
      false
    }
  }

  override fun closed() {
    recipient?.let { runCatching { binder.unlinkToDeath(it, 0) } }
    recipient = null
  }
}
