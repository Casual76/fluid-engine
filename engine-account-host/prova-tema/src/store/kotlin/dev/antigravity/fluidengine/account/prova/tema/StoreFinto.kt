package dev.antigravity.fluidengine.account.prova.tema

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import dev.antigravity.fluidengine.account.host.AccountChangedBroadcaster
import dev.antigravity.fluidengine.account.host.AccountHostProvider
import dev.antigravity.fluidengine.account.host.AccountProtocolHandler
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppPolicy
import dev.antigravity.fluidengine.account.host.AppearanceStore
import dev.antigravity.fluidengine.account.host.CustodyService
import dev.antigravity.fluidengine.account.host.PlacesStore
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.server.AccountHttp
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import java.io.File
import java.io.IOException

private const val TAG = "PampaProvaTema"

/**
 * Lo Store finto: il motore di `engine-account-host` senza account e **senza rete** (ogni chiamata al server è un
 * errore di rete), con l'aspetto comune e l'avviso esplicito alle app con la stessa firma. Le chiavi stanno in memoria:
 * è una prova, non uno Store.
 */
internal class StoreFinto private constructor(context: Context) {
  private val app = context.applicationContext
  private val dir = File(app.noBackupFilesDir, "prova-tema")
  private val vault = MemoryCustodyVault()
  private val broadcaster = AccountChangedBroadcaster(app)

  /** La tabella di serie più le due app finte, che possono chiedere i luoghi. */
  private val policy = AppPolicy(
    AppPolicy.DEFAULT + listOf(
      AppPolicy.AppEntry("$PREFIX.uno", "Prova uno", emptyMap(), AppPolicy.Places.ASK),
      AppPolicy.AppEntry("$PREFIX.due", "Prova due", emptyMap(), AppPolicy.Places.ASK),
    ).associateBy { it.packageName },
  )

  val appearance = AppearanceStore(File(dir, "aspetto")) { what -> notify(what) }
  private val places = PlacesStore(MemoryCustodyVault(), File(dir, "luoghi"), policy) { what -> notify(what) }
  private val client = AccountServerClient("https://account.invalid", "prova-tema", AccountHttp { _, _ -> throw IOException("niente rete nella prova") })
  private val keyring = CustodyKeyring(vault, File(dir, "chiavi"))
  private val session = AccountSession(client, "https://account.invalid", SessionStore(vault, File(dir, "sessione")), SoftwareDeviceKey(), TokenCache(), keyring, policy)
  val handler = AccountProtocolHandler(session, CustodyService(client, session, keyring, policy, { null }), policy, places, appearance, { null }, "prova-tema")

  /** Gli ultimi avvisi mandati, per lo schermo. */
  val sent = ArrayList<String>()

  private fun notify(what: List<String>) {
    val to = broadcaster.receivers()
    val n = broadcaster.send(what)
    val line = "avviso $what a $n app: $to"
    sent += line
    Log.i(TAG, line)
  }

  companion object {
    const val PREFIX = "dev.antigravity.fluidengine.account.prova"
    @Volatile private var instance: StoreFinto? = null

    fun of(context: Context): StoreFinto = instance ?: synchronized(this) {
      instance ?: StoreFinto(context).also { instance = it }
    }
  }
}

/** Il provider dello Store finto. */
class ProvaStoreProvider : AccountHostProvider() {
  override fun handler(): AccountProtocolHandler = StoreFinto.of(context!!).handler
}

/** Gli extra di adb e le chiavi dell'aspetto comune. */
internal val ASPETTO_EXTRAS = mapOf(
  "modo" to "appearance.mode",
  "accento" to "appearance.accent",
  "vetro" to "appearance.glass",
  "icone" to "icon.set",
  "testo" to "appearance.textScale",
  "contrasto" to "appearance.contrast",
  "movimento" to "motion.reduced",
)

/** Applica gli extra di [extras] all'aspetto comune dello Store finto; risponde a cosa ha scritto. */
internal fun applyAspetto(context: Context, extras: Bundle?): Map<String, String> {
  val values = ASPETTO_EXTRAS.mapNotNull { (extra, key) -> extras?.getString(extra)?.let { key to it } }.toMap()
  if (values.isNotEmpty()) {
    Log.i(TAG, "Aspetto comune: $values")
    StoreFinto.of(context).appearance.setGlobal(values)
  }
  return values
}

/**
 * Lo stesso della pagina «Aspetto», ma **senza aprirla**: così l'app che è in primo piano ci resta (la prova delle icone
 * di un'app aperta). Solo in questa app di prova: `am broadcast -a dev.antigravity.fluidengine.account.prova.ASPETTO
 * -p <Store finto> --es icone dark`.
 */
class AspettoReceiver : android.content.BroadcastReceiver() {
  override fun onReceive(context: Context, intent: android.content.Intent) {
    applyAspetto(context, intent.extras)
  }
}

/**
 * La pagina «Aspetto» dello Store finto, pilotata da adb: ogni extra è una chiave dell'aspetto comune.
 * `--es modo light|dark|system --es accento #rrggbb --es vetro clear|tinted|reduced --es icone default|tinted|dark|clear
 * --es testo 1.2 --es contrasto standard|high --es movimento true|false`. Senza extra mostra lo stato.
 */
class AspettoActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    apply(intent?.extras)
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    apply(intent.extras)
  }

  private fun apply(extras: Bundle?) {
    val store = StoreFinto.of(this)
    applyAspetto(this, extras)
    val s = store.appearance.snapshot()
    Log.i(TAG, "stato: versione ${s.version}, comune ${s.global}, eccezioni ${s.overrides}")
    val text = buildString {
      appendLine("Store finto · aspetto comune")
      appendLine()
      appendLine("versione ${s.version}")
      for ((k, v) in s.global.toSortedMap()) appendLine("$k = $v")
      appendLine()
      appendLine("eccezioni: ${s.overrides} · segue il comune: ${s.follow}")
      appendLine()
      store.sent.takeLast(6).forEach { appendLine(it) }
    }
    setContentView(ScrollView(this).apply {
      addView(TextView(this@AspettoActivity).apply {
        this.text = text
        textSize = 18f
        setPadding(48, 96, 48, 48)
      })
    })
  }
}
