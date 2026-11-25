package dev.antigravity.fluidengine.people.prova

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.antigravity.fluidengine.account.AccountProtocol
import dev.antigravity.fluidengine.account.host.AccountHostProvider
import dev.antigravity.fluidengine.account.host.AccountProtocolHandler
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppPolicy
import dev.antigravity.fluidengine.account.host.AppearanceStore
import dev.antigravity.fluidengine.account.host.CustodyService
import dev.antigravity.fluidengine.account.host.HostSession
import dev.antigravity.fluidengine.account.host.PlacesStore
import dev.antigravity.fluidengine.account.host.custody.AndroidKeystoreVault
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyKind
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.CustodyLockedException
import dev.antigravity.fluidengine.account.host.custody.CustodyVault
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import dev.antigravity.fluidengine.account.host.custody.KeystoreCustodyVault
import dev.antigravity.fluidengine.account.host.server.AccountHttp
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.AndroidKeystoreDeviceKey
import dev.antigravity.fluidengine.account.host.server.ServerResponse
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.people.PeopleClient
import dev.antigravity.fluidengine.people.PeopleEvent
import dev.antigravity.fluidengine.people.PeopleStateStore
import dev.antigravity.fluidengine.people.PersonKeyRole
import dev.antigravity.fluidengine.people.PersonLockedException
import dev.antigravity.fluidengine.people.PersonVault
import dev.antigravity.fluidengine.people.PersonaStore
import dev.antigravity.fluidengine.people.client.PeopleApi
import dev.antigravity.fluidengine.people.store.AndroidAppNotifier
import dev.antigravity.fluidengine.people.store.PeopleBell
import dev.antigravity.fluidengine.people.store.PeopleDispatcher
import dev.antigravity.fluidengine.people.store.PeopleProviderMethods
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import dev.antigravity.fluidengine.people.testing.MemoryPersonVault
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * La cassaforte della persona sopra la custodia vera dello Store: [PersonKeyRole.ROOT] nella classe di
 * `CustodyKeyKind.PERSON_ROOT` (madre), [PersonKeyRole.WORK] in quella di `WORK_KEYS` (consegna), come dice
 * `KeyClassPolicy` (Q7 A). È l'adattatore che lo Store vero metterà in F10.7.
 */
class CustodyPersonVault(private val vault: CustodyVault) : PersonVault {
  private fun classOf(role: PersonKeyRole) = KeyClassPolicy.classOf(if (role == PersonKeyRole.ROOT) CustodyKeyKind.PERSON_ROOT else CustodyKeyKind.WORK_KEYS)
  override fun seal(role: PersonKeyRole, plaintext: ByteArray, aad: ByteArray) = vault.seal(classOf(role), plaintext, aad)
  override fun open(role: PersonKeyRole, sealed: ByteArray, aad: ByteArray): ByteArray = try {
    vault.open(classOf(role), sealed, aad)
  } catch (e: CustodyLockedException) {
    throw PersonLockedException(e.message ?: "chiusa")
  }
}

/** Il banco della prova: la mamma è la persona di questo «Store», Alessio sta nel server finto, dentro il processo. */
object Banco {
  const val TAG = "PampaPersoneProva"
  const val APP = "dev.antigravity.fluidengine.people.prova.app"
  private const val ACCOUNT = "acc_provapersone00000000000"

  private fun dir(c: Context) = File(c.noBackupFilesDir, "prova-persone")
  fun log(c: Context, line: String) {
    Log.i(TAG, line)
    runCatching { File(c.filesDir, "prova.log").appendText("${System.currentTimeMillis()} $line\n") }
  }

  private fun vault(c: Context) = AndroidKeystoreVault(c, KeystoreCustodyVault.Config(aliasPrefix = "prova.persone"))
  private fun locked(c: Context) = c.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
  private fun screen(c: Context) = if (locked(c)) "schermo BLOCCATO" else "schermo sbloccato"

  @Volatile private var store: PeopleStateStore? = null
  @Volatile private var mamma: PeopleClient? = null

  private fun persona(c: Context) = PersonaStore(File(dir(c), "persona"), CustodyPersonVault(vault(c)))

  @Synchronized
  fun stateStore(c: Context): PeopleStateStore = store ?: PeopleStateStore(File(dir(c), "persone.bin"), CustodyPersonVault(vault(c))).also { store = it }

  /** La mamma come la vede il provider: legge la posta per le app anche senza il server finto. */
  @Synchronized
  fun mammaClient(c: Context): PeopleClient = mamma ?: PeopleClient(persona(c), stateStore(c), PeopleApi({ throw java.io.IOException("niente server") }, { "" })).also { mamma = it }

  private inline fun <T> timed(c: Context, what: String, block: () -> T): T? {
    val t0 = SystemClock.elapsedRealtimeNanos()
    return try {
      block().also { log(c, "OK  $what in ${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0} ms") }
    } catch (e: Throwable) {
      log(c, "NO  $what: ${e.javaClass.simpleName} ${e.message} (${(SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0} ms)")
      null
    }
  }

  // --- La sessione dello Store per il provider (senza rete: un'entrata finta) ------------------------------------

  private val offline = AccountHttp { _, _ -> ServerResponse(503, ByteArray(0), null) }

  private fun policy() = AppPolicy(AppPolicy.DEFAULT + (APP to AppPolicy.AppEntry(APP, "Prova app", emptyMap())))

  @Volatile private var handler: AccountProtocolHandler? = null

  @Synchronized
  fun handler(c: Context): AccountProtocolHandler {
    handler?.let { return it }
    val v = vault(c)
    val keyring = CustodyKeyring(v, File(dir(c), "chiavi"))
    val client = AccountServerClient("https://prova.invalid", "prova", offline)
    val session = AccountSession(client, "https://prova.invalid", SessionStore(v, File(dir(c), "sessione")), AndroidKeystoreDeviceKey(c, "prova.persone.dispositivo"), TokenCache(), keyring, policy())
    session.restore()
    if (session.state.value is HostSession.SignedOut) {
      session.adopt(buildJsonObject {
        put("accountId", ACCOUNT); put("deviceId", "dev_provapersone0000000000000"); put("refreshToken", "rt-prova"); put("accessToken", "at-prova"); put("expiresIn", 900)
      }, "Mamma", c.packageName)
    }
    val h = AccountProtocolHandler(
      session, CustodyService(client, session, keyring, policy(), { ACCOUNT }), policy(),
      PlacesStore(v, File(dir(c), "luoghi"), policy()), AppearanceStore(File(dir(c), "aspetto")), { emptyList() }, "1.0.0",
      people = PeopleProviderMethods(mammaClient(c), hasOtherDevices = { false }),
    )
    handler = h
    return h
  }

  // --- I passi --------------------------------------------------------------------------------------------------

  /** Passo 1, a schermo sbloccato: la persona della mamma nel Keystore vero (la radice in classe madre). */
  fun prepara(c: Context) {
    log(c, "== prepara: Android ${Build.VERSION.SDK_INT}, ${Build.MODEL}, ${screen(c)}")
    dir(c).deleteRecursively()
    store = null
    mamma = null
    handler = null
    vault(c).destroy()
    timed(c, "persona della mamma (radice in madre, chiavi di lavoro in consegna)") { persona(c).create("Mamma").code }
    timed(c, "sessione dello Store per il provider") { handler(c) }
  }

  /**
   * Il campanello finto: nel server finto (rifatto ogni volta, dentro il processo) Alessio aggiunge la mamma e le manda
   * una lista dalla sua app; poi la busta suona allo Store della mamma, che ritira, apre con le chiavi di lavoro e avvisa
   * l'app di prova col broadcast esplicito. Va anche a schermo bloccato: la radice non serve.
   */
  fun campanello(c: Context) {
    log(c, "== campanello: ${screen(c)}")
    val server = FakePeopleServer(System.currentTimeMillis())
    val me = server.newAccount().devices[0]
    val people = PeopleClient(persona(c), stateStore(c), PeopleApi(me.http, me.auth))
    mamma = people
    handler = null
    val tmp = Files.createTempDirectory("alessio").toFile()
    val alessioVault = MemoryPersonVault()
    val alessioDevice = server.newAccount().devices[0]
    val alessio = PeopleClient(PersonaStore(tmp, alessioVault).also { it.create("Alessio") }, PeopleStateStore(File(tmp, "s.bin"), alessioVault), PeopleApi(alessioDevice.http, alessioDevice.auth))
    timed(c, "la radice della mamma adesso (a schermo bloccato deve restare chiusa)") {
      try {
        persona(c).root().publicKey.size
      } catch (e: PersonLockedException) {
        "chiusa: ${e.message}"
      }
    }
    runBlocking {
      timed(c, "la mamma pubblica la scheda (chiavi di lavoro, classe consegna)") { stateStore(c).wipe(); people.publish() } ?: return@runBlocking
      timed(c, "Alessio la aggiunge, la mamma accetta") {
        alessio.publish()
        alessio.request(alessio.lookup(people.identity().code), "Ciao mamma!")
        people.accept(people.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
        alessio.pickup()
      } ?: return@runBlocking
      timed(c, "Alessio manda la lista dalla sua app") {
        val methods = PeopleProviderMethods(alessio, hasOtherDevices = { false })
        val row = methods.handle(APP, AccountProtocol.METHOD_PEOPLE_ROW, null)["row"]!!.jsonArray.map { it.jsonObject }
        methods.handle(APP, AccountProtocol.METHOD_SHARE_SEND, buildJsonObject {
          put("to", row.first()["id"]!!.jsonPrimitive.content); put("kind", "live"); put("title", "La spesa"); put("payload", """{"lista":"spesa"}""")
        })
      } ?: return@runBlocking
      log(c, "    sveglie del campanello finto per la mamma: ${server.ringsFor(me).map { it.priority }}")
      val bell = PeopleBell(people, PeopleDispatcher(people, AndroidAppNotifier(c)))
      timed(c, "campanello: ritiro, apertura, avviso esplicito all'app") { bell.ring(mapOf("k" to "posta")).joinToString() }
      log(c, "    nella posta per l'app: ${stateStore(c).load().inbox.filter { it.app == APP }.map { it.title }}")
    }
  }

  /** Il campanello fra [secondi], con WorkManager: il tempo di bloccare lo schermo col tasto di accensione. */
  fun campanelloFra(c: Context, secondi: Long) {
    WorkManager.getInstance(c).enqueue(OneTimeWorkRequestBuilder<CampanelloWorker>().setInitialDelay(secondi, TimeUnit.SECONDS).build())
    log(c, "== campanello fra $secondi s: ${screen(c)}")
  }

  fun stato(c: Context) {
    log(c, "== stato: ${screen(c)}, persona=${persona(c).exists}, posta per l'app=${runCatching { stateStore(c).load().inbox.size }.getOrNull()}")
  }
}

class NegozioActivity : Activity() {
  private lateinit var out: TextView
  private var secondi = 60L

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    out = TextView(this).apply { textSize = 12f }
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(32, 48, 32, 32)
      for ((label, step) in listOf("1 · Prepara" to "prepara", "2 · Campanello adesso" to "campanello", "3 · Campanello fra 60 s" to "campanello-fra", "4 · Stato" to "stato")) {
        addView(Button(context).apply { text = label; setOnClickListener { run(step) } })
      }
      addView(out)
    }
    setContentView(ScrollView(this).apply { addView(column) })
    secondi = intent?.getIntExtra("secondi", 60)?.toLong() ?: 60L
    intent?.getStringExtra("passo")?.let(::run)
    refresh()
  }

  private fun run(step: String) = thread {
    when (step) {
      "prepara" -> Banco.prepara(applicationContext)
      "campanello" -> Banco.campanello(applicationContext)
      "campanello-fra" -> Banco.campanelloFra(applicationContext, secondi)
      "stato" -> Banco.stato(applicationContext)
    }
    runOnUiThread { refresh() }
  }

  private fun refresh() {
    out.text = runCatching { File(filesDir, "prova.log").readLines().takeLast(60).joinToString("\n") }.getOrDefault("")
  }
}

/** Il campanello finto: lo suona adb, anche a schermo bloccato. Il lavoro va su un filo, come nel servizio di FCM. */
class CampanelloReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    thread {
      try {
        Banco.campanello(context.applicationContext)
      } finally {
        pending.finish()
      }
    }
  }
}

class CampanelloWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    Banco.campanello(applicationContext)
    return Result.success()
  }
}

/** Il provider firmato di questo «Store»: il controllo della firma è quello vero di `AccountHostProvider`. */
class ProvaHostProvider : AccountHostProvider() {
  override fun handler(): AccountProtocolHandler = Banco.handler(context!!.applicationContext)
}
