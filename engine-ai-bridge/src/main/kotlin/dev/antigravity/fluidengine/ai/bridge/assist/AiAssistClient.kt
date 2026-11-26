package dev.antigravity.fluidengine.ai.bridge.assist

import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 * Dove l'app cerca PampAI.
 *
 * @param hostPackage PampAI vera.
 * @param devHostPackage PampAI di sviluppo (stessa firma `pampa.jks`): una build di sviluppo dell'app la prova per
 *   prima, una build vera la ignora (come lo Store dell'Account Pampa). Null: solo [hostPackage].
 * @param devBuild l'app e' una build di sviluppo? Null: lo dice il flag `debuggable` dell'app.
 * @param pollIntervalMillis 0 (di serie): i pezzi arrivano sul canale di ritorno, subito. Piu' di 0: l'app li chiede
 *   lei ogni tanti millisecondi con `assist_poll` (la riserva; la galleria la usa per le misure).
 * @param graceMillis quanto l'app aspetta oltre il tempo della domanda prima di chiudere da sola con `timeout`.
 * @param storePackage / [devStorePackage] Pampa Store, per «Installa PampAI» (F9.1b): la stessa precedenza di PampAI.
 */
data class AiAssistClientConfig(
  val hostPackage: String = PampaiPackage,
  val devHostPackage: String? = PampaiDevPackage,
  val devBuild: Boolean? = null,
  val pollIntervalMillis: Long = 0L,
  val graceMillis: Long = 5_000L,
  val storePackage: String = StorePackage,
  val devStorePackage: String? = StoreDevPackage,
) {
  companion object {
    const val PampaiPackage = "dev.pampa.pampai"
    const val PampaiDevPackage = "dev.pampa.pampai.debug"
    const val StorePackage = "com.pampa.store"
    const val StoreDevPackage = "com.pampa.store.debug"

    /**
     * Come si apre Pampa Store alla pagina di un'app: l'azione dell'Account Pampa (`dev.pampa.account.OPEN`) con la
     * sezione `app` e il pacchetto in `app`. E' il contratto di F9.1b per lo Store (che lo impara col passaggio
     * all'engine 4.0); uno Store che non lo conosce si apre sulla sua prima pagina.
     */
    const val StoreActionOpen = "dev.pampa.account.OPEN"
    const val StoreExtraSection = "section"
    const val StoreSectionApp = "app"
    const val StoreExtraApp = "app"
  }
}

/** PampAI trovata: il pacchetto, l'autorita' dell'ospite dell'assistenza, l'uid (per riconoscere i suoi invii). */
data class AiAssistHostInfo(val packageName: String, val authority: String, val uid: Int)

/**
 * Il cliente dell'assistenza, per le app: chiede a PampAI e riceve la risposta (F9.1).
 *
 * ```kotlin
 * val pampai = AiAssistClient(context)
 * pampai.ask(AiAssistRequest(AiAssistTask.answer("Perché questo voto abbassa la media?"), page = page))
 *   .collect { p -> testo = p.text; riga = p.status?.line; errore = p.error?.message() }
 * ```
 *
 * - **PampAI si controlla prima di parlarle**: il pacchetto atteso, il provider `<pacchetto>.ai.assist` col meta-data
 *   dell'assistenza, e **la stessa firma dell'app**. Una PampAI firmata da altri per l'app «non c'è»
 *   ([AiAssistError.NotInstalled]) e non riceve niente; una PampAI senza l'ospite dell'assistenza «va aggiornata».
 * - [ask] da' lo stato intero a ogni novita' ([AiAssistProgress]); l'ultimo ha `done` o `error`. Cancellare chi raccoglie
 *   annulla la domanda in PampAI (che smette di spendere). Il tempo massimo e' quello della richiesta; se PampAI tace
 *   oltre, piu' [AiAssistClientConfig.graceMillis], l'app chiude con `timeout`; se PampAI muore, con `interrupted`.
 * - Le chiamate a PampAI sono Binder: il flusso le fa su `Dispatchers.IO`, chi raccoglie riceve gli stati nel suo
 *   contesto (in Compose, sul filo principale).
 */
/** Ogni quanto l'app controlla con `assist_poll` se PampAI tace: un tool lungo, o un invio perso (a occhio, 26/11/2026). */
internal const val IdlePollMillis = 2_000L

class AiAssistClient internal constructor(
  val config: AiAssistClientConfig,
  private val locate: () -> Located,
  private val connector: AiAssistConnector,
  private val io: CoroutineDispatcher,
  private val install: () -> Intent? = { null },
  private val launch: (String) -> Intent? = { null },
) {
  constructor(context: Context, config: AiAssistClientConfig = AiAssistClientConfig()) : this(
    config = config,
    locate = { AiAssistLocator(config, AiAssistLocator.isDebuggable(context), AiAssistLocator.systemProbe(context)).locate() },
    connector = AndroidConnector(context.applicationContext),
    io = Dispatchers.IO,
    install = { AiAssistLocator.storeIntent(context.applicationContext, config, AiAssistLocator.isDebuggable(context)) },
    launch = { pkg -> try { context.packageManager.getLaunchIntentForPackage(pkg) } catch (e: Exception) { null } },
  )

  /** PampAI, se c'e' e ha la firma giusta. */
  fun host(): AiAssistHostInfo? = (locate() as? Located.Found)?.host

  /** Cosa l'app puo' fare adesso: se mostrare il tasto «Chiedi a PampAI», quali compiti, il sì per la pagina. */
  suspend fun check(): AiAssistStatus = withContext(io) {
    val host = when (val l = locate()) {
      is Located.Found -> l.host
      is Located.Missing -> return@withContext AiAssistStatus(false, error = l.error)
    }
    val connection = connector.open(host) ?: return@withContext AiAssistStatus(false, error = AiAssistError(AiAssistError.NotInstalled), hostPackage = host.packageName)
    try {
      negotiate(connection)?.let { return@withContext AiAssistStatus(false, error = it, hostPackage = host.packageName) }
      when (val reply = connection.call(AiAssistProtocol.METHOD_SCOPE, AiAssistProtocol.scopeRequest())) {
        is AiAssistReply.Ok -> AiAssistProtocol.statusOf(reply.json, host.packageName)
        else -> AiAssistStatus(false, error = reply.error(), hostPackage = host.packageName)
      }
    } finally {
      connection.close()
    }
  }

  /**
   * Chiede a PampAI. Il primo stato e' quello vuoto (la domanda e' partita, o sta per), poi uno a ogni novita'; l'ultimo
   * e' finito ([AiAssistProgress.finished]).
   */
  fun ask(request: AiAssistRequest): Flow<AiAssistProgress> = channelFlow {
    val assembler = AiAssistAssembler()
    suspend fun fail(code: String) { assembler.fail(AiAssistError(code)); send(assembler.progress) }

    val host = when (val l = locate()) {
      is Located.Found -> l.host
      is Located.Missing -> { assembler.fail(l.error); send(assembler.progress); return@channelFlow }
    }
    val connection = connector.open(host) ?: run { fail(AiAssistError.NotInstalled); return@channelFlow }
    var requestId: String? = null
    // Vero se la fine l'ha decisa l'app (tempo scaduto, PampAI muta o morta) e non PampAI: allora va annullata li'.
    var endedHere = false
    try {
      negotiate(connection)?.let { assembler.fail(it); send(assembler.progress); return@channelFlow }
      val inbox = Channel<String>(Channel.UNLIMITED)
      val hostDied = CompletableDeferred<Unit>()
      val polled = config.pollIntervalMillis > 0
      val reply = connection.call(
        AiAssistProtocol.METHOD_ASK,
        AiAssistProtocol.askRequest(request),
        onBatch = if (polled) null else { batch -> inbox.trySend(batch) },
        onHostDied = { hostDied.complete(Unit) },
      )
      val json = (reply as? AiAssistReply.Ok)?.json
      val refused = if (reply is AiAssistReply.Ok) AiAssistProtocol.errorOf(json) else reply.error()
      if (refused != null) { assembler.fail(refused); send(assembler.progress); return@channelFlow }
      val id = AiAssistProtocol.requestIdOf(json) ?: run { fail(AiAssistError.Failed); return@channelFlow }
      requestId = id
      send(assembler.progress)

      suspend fun poll(after: Long): Boolean {
        val r = connection.call(AiAssistProtocol.METHOD_POLL, AiAssistProtocol.pollRequest(id, after))
        val body = (r as? AiAssistReply.Ok)?.json
        val error = if (r is AiAssistReply.Ok) AiAssistProtocol.errorOf(body) else AiAssistError(AiAssistError.Interrupted)
        if (error != null) { if (assembler.fail(error)) send(assembler.progress); return false }
        if (assembler.add(AiAssistProtocol.eventsOf(body?.get("events") as? kotlinx.serialization.json.JsonArray))) send(assembler.progress)
        return true
      }

      val limit = request.timeoutMillis.coerceIn(1_000L, 120_000L) + config.graceMillis
      val inTime = withTimeoutOrNull(limit) {
        while (!assembler.progress.finished) {
          if (polled) {
            delay(config.pollIntervalMillis)
            if (!poll(assembler.last)) break
            continue
          }
          val next: Any = withTimeoutOrNull(IdlePollMillis) {
            select<Any> {
              inbox.onReceive { it }
              hostDied.onAwait { HostDied }
            }
          } ?: Idle
          if (next === Idle) {
            // PampAI tace da un po' (un tool lungo, o un invio perso): si controlla.
            if (!poll(assembler.last)) break
            continue
          }
          if (next === HostDied) {
            // PampAI e' morta: forse l'ultimo invio e' gia' nella casella.
            while (true) { val rest = inbox.tryReceive().getOrNull() ?: break; handle(assembler, rest) }
            if (assembler.fail(AiAssistError(AiAssistError.Interrupted))) { endedHere = true; send(assembler.progress) }
            break
          }
          val batch = next as String
          if (handle(assembler, batch)) send(assembler.progress)
          // Un invio perso (un buco nei numeri): lo si chiede.
          val gap = assembler.missingAfter
          if (gap != null && !poll(gap)) break
        }
        true
      }
      if (inTime == null && assembler.fail(AiAssistError(AiAssistError.Timeout))) { endedHere = true; send(assembler.progress) }
    } finally {
      val id = requestId
      if (id != null && (!assembler.progress.finished || endedHere)) cancelQuietly(connection, id) else connection.close()
    }
  }.flowOn(io)

  /** Chiede e aspetta la fine: l'ultimo stato. */
  suspend fun run(request: AiAssistRequest): AiAssistProgress = ask(request).last()

  /** Una domanda libera, con la pagina se c'e'. */
  suspend fun answer(question: String, page: AiAssistPage? = null): AiAssistProgress = run(AiAssistRequest(AiAssistTask.answer(question), page))

  /**
   * L'Activity di PampAI che chiede il sì: la prima volta per usare PampAI da quest'app, e per la pagina se [page]
   * (**un foglio solo** per le due domande, F9.1b). Da aprire con `ActivityResultContracts.StartActivityForResult`
   * (solo cosi' PampAI sa dal sistema chi chiede); il risultato si legge con [AiAssistConsent.resultOf]. Null se
   * PampAI non c'e'. Un'app gia' rifiutata non viene richiesta: il foglio si chiude subito col «no».
   */
  fun consentIntent(page: Boolean = false): Intent? = host()?.let {
    Intent(AiAssistConsent.ACTION).setPackage(it.packageName).putExtra(AiAssistConsent.EXTRA_WITH_PAGE, page)
  }

  /**
   * «Installa PampAI» (F9.1b): Pampa Store alla pagina di PampAI, se lo Store c'e' ed e' firmato come l'app; uno Store
   * che non conosce ancora quella pagina si apre sulla sua prima pagina. Null se lo Store non c'e': l'app mostra
   * [AiAssistTexts.installHint]. Va usato solo per [AiAssistEntry.ActionInstall] e [AiAssistEntry.ActionUpdate], cioe'
   * quando PampAI non c'e' o e' vecchia: mai per un'app non autorizzata.
   */
  fun installIntent(): Intent? = install()

  /** «Apri PampAI» (per aggiungere una chiave, [AiAssistEntry.ActionOpen]); null se PampAI non c'e'. */
  fun openIntent(): Intent? = host()?.let { launch(it.packageName) }

  private fun handle(assembler: AiAssistAssembler, batch: String): Boolean {
    val parsed = AiAssistProtocol.batchOf(batch) ?: return false
    return assembler.add(parsed.events)
  }

  /** null se l'ospite parla l'assistenza; altrimenti l'errore da dare all'app. */
  private fun negotiate(connection: AiAssistConnection): AiAssistError? =
    when (val reply = connection.call(AiAssistProtocol.METHOD_NEGOTIATE, AiAssistProtocol.negotiationRequest())) {
      is AiAssistReply.Ok -> if (AiAssistProtocol.agreed(reply.json)) null else AiAssistError(AiAssistError.NeedsUpdate)
      else -> reply.error()
    }

  /**
   * L'annullamento esce anche se chi raccoglie e' stato cancellato, e non lo fa aspettare piu' di due secondi: una
   * chiamata Binder puo' bloccarsi anche quando la coroutine e' cancellata (come `RemoteTool.cancelJob`).
   */
  private suspend fun cancelQuietly(connection: AiAssistConnection, requestId: String) = withContext(NonCancellable) {
    val cleanup = CoroutineScope(SupervisorJob() + io)
    val job = cleanup.launch {
      try { connection.call(AiAssistProtocol.METHOD_CANCEL, AiAssistProtocol.cancelRequest(requestId)) } catch (_: Exception) { }
      connection.close()
    }
    withTimeoutOrNull(2_000L) { job.join() }
    Unit
  }

  private object HostDied
  private object Idle

  internal sealed interface Located {
    data class Found(val host: AiAssistHostInfo) : Located
    data class Missing(val error: AiAssistError) : Located
  }
}

/** La risposta di una chiamata a PampAI, prima di leggerne il JSON. */
internal sealed interface AiAssistReply {
  class Ok(val json: JsonObject?) : AiAssistReply

  /** `SecurityException`: PampAI non si fida di quest'app (la firma, o un uid condiviso). */
  data object Denied : AiAssistReply

  /** Il provider non c'e' (PampAI disinstallata nel frattempo, o senza l'ospite). */
  data object Missing : AiAssistReply

  /** Il processo di PampAI e' morto, o la chiamata non e' andata. */
  data object Failed : AiAssistReply

  fun error(): AiAssistError = when (this) {
    is Ok -> AiAssistProtocol.errorOf(json) ?: AiAssistError(AiAssistError.Failed)
    Denied -> AiAssistError(AiAssistError.NotAuthorized)
    Missing -> AiAssistError(AiAssistError.NotInstalled)
    Failed -> AiAssistError(AiAssistError.Interrupted)
  }
}

/** Una connessione a PampAI per una domanda: le chiamate, e il canale di ritorno di `assist_ask`. */
internal interface AiAssistConnection {
  fun call(method: String, json: JsonObject, onBatch: ((String) -> Unit)? = null, onHostDied: (() -> Unit)? = null): AiAssistReply
  fun close()
}

internal fun interface AiAssistConnector {
  fun open(host: AiAssistHostInfo): AiAssistConnection?
}

/**
 * La connessione vera: un `ContentProviderClient` *unstable* (un ospite che muore non uccide l'app), tenuto aperto per
 * tutta la domanda (cosi' il processo di PampAI resta alla priorita' dell'app), e un [AiAssistReceiver] come canale.
 */
internal class AndroidConnector(private val context: Context) : AiAssistConnector {
  override fun open(host: AiAssistHostInfo): AiAssistConnection? {
    val client = runCatching { context.contentResolver.acquireUnstableContentProviderClient(host.authority) }.getOrNull() ?: return null
    return AndroidConnection(client, host)
  }

  private class AndroidConnection(private val client: ContentProviderClient, private val host: AiAssistHostInfo) : AiAssistConnection {
    private var token: IBinder? = null
    private var recipient: IBinder.DeathRecipient? = null

    override fun call(method: String, json: JsonObject, onBatch: ((String) -> Unit)?, onHostDied: (() -> Unit)?): AiAssistReply = try {
      val extras = Bundle().apply {
        putString(AiAssistProtocol.KEY_JSON, json.toString())
        if (onBatch != null) putBinder(AiAssistProtocol.KEY_CALLBACK, AiAssistReceiver(host.uid, onBatch))
      }
      val bundle = client.call(method, null, extras)
      if (bundle == null) AiAssistReply.Failed else {
        if (onHostDied != null) bundle.getBinder(AiAssistProtocol.KEY_TOKEN)?.let { watch(it, onHostDied) }
        AiAssistReply.Ok(AiAssistProtocol.parse(bundle.getString(AiAssistProtocol.KEY_JSON)))
      }
    } catch (e: SecurityException) {
      AiAssistReply.Denied
    } catch (e: DeadObjectException) {
      AiAssistReply.Failed
    } catch (e: Exception) {
      AiAssistReply.Failed
    }

    private fun watch(binder: IBinder, onHostDied: () -> Unit) {
      val r = IBinder.DeathRecipient { onHostDied() }
      try {
        binder.linkToDeath(r, 0)
        token = binder
        recipient = r
      } catch (e: Exception) {
        onHostDied()
      }
    }

    override fun close() {
      val t = token
      val r = recipient
      if (t != null && r != null) runCatching { t.unlinkToDeath(r, 0) }
      token = null
      recipient = null
      runCatching { client.close() }
    }
  }
}

/**
 * Il canale di ritorno, nell'app: un `Binder` che riceve gli invii di PampAI (transazioni a senso unico). Accetta solo
 * quelle che vengono dall'uid di PampAI e col segno giusto; il resto lo ignora.
 */
internal class AiAssistReceiver(private val hostUid: Int, private val onBatch: (String) -> Unit) : Binder() {
  override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
    if (code != AiAssistProtocol.CODE_EVENTS) return super.onTransact(code, data, reply, flags)
    // PampAI, o il nostro stesso processo (la galleria tiene l'ospite finto nell'APK).
    val from = getCallingUid()
    if (from != hostUid && from != android.os.Process.myUid()) return false
    if (data.readInt() != AiAssistProtocol.CALLBACK_MAGIC) return false
    data.readString()?.let(onBatch)
    return true
  }
}

/**
 * Come l'app trova PampAI e decide se fidarsi (come `StoreLocator` dell'Account Pampa): la precedenza (le build di
 * sviluppo provano prima PampAI di sviluppo), il provider dell'assistenza col meta-data, e **la stessa firma dell'app**.
 */
internal class AiAssistLocator(private val config: AiAssistClientConfig, devBuild: Boolean, private val probe: Probe) {
  private val dev = config.devBuild ?: devBuild

  interface Probe {
    fun installed(packageName: String): Boolean

    /** Il provider di [authority] se appartiene a [packageName]: il valore del meta-data dell'assistenza e l'uid. */
    fun provider(packageName: String, authority: String): Pair<Int, Int>?
    fun sameSignature(packageName: String): Boolean
  }

  fun candidates(): List<String> = listOfNotNull(config.devHostPackage?.takeIf { dev }, config.hostPackage).distinct()

  fun locate(): AiAssistClient.Located {
    var installedWithout = false
    for (pkg in candidates()) {
      if (!probe.installed(pkg)) continue
      if (!probe.sameSignature(pkg)) continue
      val found = probe.provider(pkg, pkg + AiAssistProtocol.AUTHORITY_SUFFIX)
      if (found == null) { installedWithout = true; continue }
      val (version, uid) = found
      if (version < AiAssistProtocol.PROTOCOL) { installedWithout = true; continue }
      return AiAssistClient.Located.Found(AiAssistHostInfo(pkg, pkg + AiAssistProtocol.AUTHORITY_SUFFIX, uid))
    }
    return AiAssistClient.Located.Missing(AiAssistError(if (installedWithout) AiAssistError.NeedsUpdate else AiAssistError.NotInstalled))
  }

  companion object {
    fun isDebuggable(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /**
     * Pampa Store alla pagina di PampAI ([AiAssistClient.installIntent]): lo Store di sviluppo prima, in una build di
     * sviluppo; solo uno Store con la firma dell'app; la sua pagina se la sa aprire, se no la sua prima pagina.
     */
    @Suppress("DEPRECATION")
    fun storeIntent(context: Context, config: AiAssistClientConfig, debuggable: Boolean): Intent? {
      val pm = context.packageManager
      val dev = config.devBuild ?: debuggable
      val stores = listOfNotNull(config.devStorePackage?.takeIf { dev }, config.storePackage).distinct()
      for (store in stores) {
        val installed = runCatching { pm.getPackageInfo(store, 0); true }.getOrDefault(false)
        if (!installed) continue
        val same = runCatching { pm.checkSignatures(context.packageName, store) == PackageManager.SIGNATURE_MATCH }.getOrDefault(false)
        if (!same) continue
        val page = Intent(AiAssistClientConfig.StoreActionOpen).setPackage(store)
          .putExtra(AiAssistClientConfig.StoreExtraSection, AiAssistClientConfig.StoreSectionApp)
          .putExtra(AiAssistClientConfig.StoreExtraApp, config.hostPackage)
        if (runCatching { pm.resolveActivity(page, 0) }.getOrNull() != null) return page
        val home = runCatching { pm.getLaunchIntentForPackage(store) }.getOrNull() ?: continue
        return home.putExtra(AiAssistClientConfig.StoreExtraSection, AiAssistClientConfig.StoreSectionApp)
          .putExtra(AiAssistClientConfig.StoreExtraApp, config.hostPackage)
      }
      return null
    }

    @Suppress("DEPRECATION")
    fun systemProbe(context: Context): Probe = object : Probe {
      private val pm = context.packageManager

      override fun installed(packageName: String): Boolean = runCatching { pm.getPackageInfo(packageName, 0); true }.getOrDefault(false)

      override fun provider(packageName: String, authority: String): Pair<Int, Int>? {
        val info = runCatching { pm.resolveContentProvider(authority, PackageManager.GET_META_DATA) }.getOrNull()
          ?: runCatching {
            pm.getPackageInfo(packageName, PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA).providers
              ?.firstOrNull { p -> p.authority?.split(';')?.contains(authority) == true }
          }.getOrNull()
          ?: return null
        if (info.packageName != packageName) return null
        // Il valore del meta-data e' un numero; un manifest che lo scrive come testo vale lo stesso.
        val raw = info.metaData?.get(AiAssistProtocol.META_ASSIST_HOST)
        val version = (raw as? Number)?.toInt() ?: (raw as? String)?.trim()?.toIntOrNull() ?: 0
        return version to info.applicationInfo.uid
      }

      override fun sameSignature(packageName: String): Boolean =
        packageName == context.packageName ||
          runCatching { pm.checkSignatures(context.packageName, packageName) == PackageManager.SIGNATURE_MATCH }.getOrDefault(false)
    }
  }
}
