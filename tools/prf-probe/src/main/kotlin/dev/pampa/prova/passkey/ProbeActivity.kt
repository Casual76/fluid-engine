package dev.pampa.prova.passkey

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import dev.antigravity.fluidengine.auth.AuthResult
import dev.antigravity.fluidengine.auth.FluidPasskeyResult
import dev.antigravity.fluidengine.auth.FluidPasskeys
import dev.antigravity.fluidengine.auth.FluidPrfInput
import dev.antigravity.fluidengine.auth.phrase
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * «Pampa · prova passkey» (F10.2): crea passkey su `prova.pampa-apps.com` ed entra, con engine-auth come farà Pampa
 * Store, e scrive cosa fa il gestore col PRF. Una pagina sola, a viste semplici: è uno strumento di misura, non
 * un'interfaccia dell'ecosistema.
 *
 * Le opzioni WebAuthn le fa l'app, con la stessa forma che manda il server dell'Account Pampa
 * (`@simplewebauthn/server`, vettori di `engine-auth`): qui non c'è un server, perché per il PRF basta il gestore.
 * Niente rete: l'app non ha il permesso INTERNET. Il gestore legge `assetlinks.json` da sé.
 *
 * Il riassunto (mai il PRF: solo impronte) va a schermo, negli appunti col tasto, in `files/riassunto.txt`, in
 * `Android/data/dev.pampa.prova.passkey/files/riassunto.txt` e nel logcat (`PampaProvaPasskey`).
 */
class ProbeActivity : ComponentActivity() {

  private val random = SecureRandom()
  private lateinit var log: ProbeLog
  private lateinit var summaryView: TextView
  private lateinit var statusView: TextView
  private val buttons = mutableListOf<Button>()

  private val prefs by lazy { getSharedPreferences("prova", Context.MODE_PRIVATE) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    log = ProbeLog.fromJson(prefs.getString("log", null))
    setContentView(page())
    refresh()
  }

  // --- la pagina -------------------------------------------------------------------------------------------------

  private fun page(): View {
    val pad = (16 * resources.displayMetrics.density).toInt()
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(pad, pad * 2, pad, pad * 2)
    }
    column.addView(TextView(this).apply {
      text = getString(R.string.app_name)
      textSize = 26f
      setTypeface(typeface, Typeface.BOLD)
    })
    column.addView(TextView(this).apply {
      text = "Dominio di prova: ${BuildConfig.RP_ID}. Le passkey create qui servono solo alla prova: alla fine si tolgono dal gestore (guida della mini-seduta)."
      setPadding(0, pad / 2, 0, pad)
    })
    statusView = TextView(this).apply { setPadding(0, 0, 0, pad / 2) }
    column.addView(statusView)

    column.addView(section("Quale gestore stai provando?"))
    column.addView(row(
      button("Google") { declare(KnownManagers.GOOGLE) },
      button("Samsung Pass") { declare(KnownManagers.SAMSUNG) },
    ))
    column.addView(button("Scegli il gestore nelle impostazioni") { openProviderSettings() })

    column.addView(section("Prova"))
    column.addView(button("1 · Crea una passkey (chiede il PRF)") { create(askValue = true) })
    column.addView(button("1b · Crea senza chiedere il valore del PRF") { create(askValue = false) })
    column.addView(button("2 · Accedi (ripeti per il secondo accesso)") { signIn() })

    column.addView(section("Riassunto"))
    column.addView(row(
      button("Copia il riassunto") { copySummary() },
      button("Azzera") { reset() },
    ))
    summaryView = TextView(this).apply {
      typeface = Typeface.MONOSPACE
      textSize = 12f
      setTextIsSelectable(true)
      setPadding(0, pad / 2, 0, 0)
    }
    column.addView(summaryView)
    // targetSdk 36: la pagina va sotto le barre di sistema; fitsSystemWindows le lascia libere.
    return ScrollView(this).apply {
      fitsSystemWindows = true
      addView(column)
    }
  }

  private fun section(title: String) = TextView(this).apply {
    text = title
    textSize = 18f
    setTypeface(typeface, Typeface.BOLD)
    setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
  }

  private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    setOnClickListener { onClick() }
    buttons += this
  }

  private fun row(vararg views: View) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    views.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
  }

  // --- le azioni -------------------------------------------------------------------------------------------------

  private fun declare(manager: String) {
    log.declaredManager = manager
    save()
    status("Gestore dichiarato: $manager. Controlla che sia quello scelto nelle impostazioni.")
  }

  private fun create(askValue: Boolean) = busy {
    val n = log.creations.size + 1
    val userName = "Prova PRF $n"
    val result = FluidPasskeys.create(this, creationOptions(userName), FluidPrfInput.PampaV1, evaluatePrfAtCreation = askValue)
    when (result) {
      is AuthResult.Ok -> {
        val r = result.value
        val data = r.authenticatorData
        log.creations += CreationRecord(
          credentialId = r.credentialId,
          manager = KnownManagers.name(data.aaguid, log.declaredManager),
          aaguid = data.aaguid,
          prfEnabled = r.prfEnabled,
          askedValue = askValue,
          prfAtCreation = r.prfOutput()?.let(::prfFingerprint),
          backupEligible = data.backupEligible,
          backedUp = data.backedUp,
          userVerified = data.userVerified,
          userName = userName,
          at = System.currentTimeMillis(),
        )
        lastOrigin = r.origin
        status("Creata «$userName» (${log.creations.last().manager}). Ora fai «Accedi» due volte.")
      }
      else -> fail("creazione", result)
    }
  }

  private fun signIn() = busy {
    val result = FluidPasskeys.get(this, assertionOptions(), FluidPrfInput.PampaV1)
    when (result) {
      is AuthResult.Ok -> {
        val r: FluidPasskeyResult = result.value
        val data = r.authenticatorData
        log.accesses += AccessRecord(
          credentialId = r.credentialId,
          prf = r.prfOutput()?.let(::prfFingerprint),
          backupEligible = data.backupEligible,
          backedUp = data.backedUp,
          userVerified = data.userVerified,
          at = System.currentTimeMillis(),
        )
        lastOrigin = r.origin
        val count = log.accesses.count { it.credentialId == r.credentialId }
        status("Accesso $count con ${log.managerOf(r.credentialId)}: PRF ${log.accesses.last().prf ?: "assente"}.")
      }
      else -> fail("accesso", result)
    }
  }

  private fun fail(step: String, result: AuthResult<*>) {
    val phrase = result.phrase() ?: "?"
    val cause = when (result) {
      is AuthResult.Failed -> result.cause?.javaClass?.simpleName ?: "nessuna causa"
      is AuthResult.Unsupported -> result.why.name
      else -> result.javaClass.simpleName
    }
    log.failures += FailureRecord(step, phrase, cause, System.currentTimeMillis())
    status("$step: $phrase")
  }

  private fun openProviderSettings() {
    try {
      CredentialManager.create(this).createSettingsPendingIntent().send()
    } catch (e: Exception) {
      Toast.makeText(this, "Apri Impostazioni → Password, passkey e account", Toast.LENGTH_LONG).show()
    }
  }

  private fun copySummary() {
    val text = summary()
    val clip = ClipData.newPlainText("Pampa · prova passkey", text)
    // Il riassunto non ha segreti, ma lo si segna lo stesso come «sensibile»: niente anteprima negli appunti
    // (la chiave vale da Android 13; prima si ignora).
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    Toast.makeText(this, "Riassunto copiato", Toast.LENGTH_SHORT).show()
  }

  private fun reset() {
    log = ProbeLog(declaredManager = log.declaredManager)
    save()
    status("Risultati azzerati. Le passkey restano nei gestori: si tolgono a mano (guida).")
  }

  private fun busy(work: suspend () -> Unit) {
    buttons.forEach { it.isEnabled = false }
    lifecycleScope.launch {
      try {
        work()
      } finally {
        save()
        buttons.forEach { it.isEnabled = true }
      }
    }
  }

  // --- le opzioni WebAuthn, con la forma del server dell'Account Pampa ------------------------------------------------

  private fun creationOptions(userName: String): String = JsonObject(
    mapOf(
      "challenge" to JsonPrimitive(randomB64(32)),
      "rp" to JsonObject(mapOf("name" to JsonPrimitive("Pampa prova"), "id" to JsonPrimitive(BuildConfig.RP_ID))),
      "user" to JsonObject(
        mapOf("id" to JsonPrimitive(randomB64(16)), "name" to JsonPrimitive(userName), "displayName" to JsonPrimitive(userName)),
      ),
      "pubKeyCredParams" to JsonArray(listOf(-8, -7, -257).map {
        JsonObject(mapOf("alg" to JsonPrimitive(it), "type" to JsonPrimitive("public-key")))
      }),
      "timeout" to JsonPrimitive(300_000),
      "attestation" to JsonPrimitive("none"),
      "excludeCredentials" to JsonArray(emptyList()),
      "authenticatorSelection" to JsonObject(
        mapOf(
          "residentKey" to JsonPrimitive("required"),
          "userVerification" to JsonPrimitive("required"),
          "requireResidentKey" to JsonPrimitive(true),
        ),
      ),
      "extensions" to JsonObject(mapOf("credProps" to JsonPrimitive(true))),
      "hints" to JsonArray(emptyList()),
    ),
  ).toString()

  private fun assertionOptions(): String = JsonObject(
    mapOf(
      "rpId" to JsonPrimitive(BuildConfig.RP_ID),
      "challenge" to JsonPrimitive(randomB64(32)),
      "allowCredentials" to JsonArray(emptyList()),
      "timeout" to JsonPrimitive(300_000),
      "userVerification" to JsonPrimitive("required"),
    ),
  ).toString()

  private fun randomB64(size: Int): String =
    Base64.encodeToString(ByteArray(size).also(random::nextBytes), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

  // --- il riassunto ------------------------------------------------------------------------------------------------

  private var lastOrigin: String? = null

  private fun environment() = ProbeEnvironment(
    rpId = BuildConfig.RP_ID,
    app = "$packageName ${BuildConfig.VERSION_NAME}",
    signingSha256 = signingSha256(),
    expectedSha256 = BuildConfig.EXPECTED_CERT_SHA256,
    origin = lastOrigin ?: signingSha256()?.let { expectedOrigin(it) },
    device = "${Build.MANUFACTURER} ${Build.MODEL}",
    android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    unsupported = FluidPasskeys.unsupportedReason(this)?.let { "no, ${it.name}" },
  )

  private fun summary(): String = log.summary(environment())

  private fun refresh() {
    val text = summary()
    summaryView.text = text
    val env = environment()
    if (statusView.text.isNullOrEmpty()) {
      statusView.text = if (env.signingSha256.equals(env.expectedSha256, ignoreCase = true)) {
        "Firma di prova giusta. Scegli il gestore, poi «Crea»."
      } else {
        "Firma diversa da quella di assetlinks.json: la prova non può riuscire."
      }
    }
  }

  private fun status(text: String) {
    statusView.text = text
    Log.i(TAG, text)
    refresh()
  }

  private fun save() {
    prefs.edit().putString("log", log.toJson()).apply()
    val text = summary()
    // Due copie: una con run-as (l'app è debuggable), una leggibile anche senza.
    runCatching { File(filesDir, "riassunto.txt").writeText(text) }
    runCatching { getExternalFilesDir(null)?.let { File(it, "riassunto.txt").writeText(text) } }
    text.lineSequence().forEach { Log.i(TAG, it) }
    refresh()
  }

  @Suppress("DEPRECATION")
  private fun signingSha256(): String? = try {
    val signatures = if (Build.VERSION.SDK_INT >= 28) {
      packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
    } else {
      packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
    }
    signatures?.firstOrNull()?.toByteArray()?.let { cert ->
      MessageDigest.getInstance("SHA-256").digest(cert).joinToString(":") { "%02X".format(it.toInt() and 0xff) }
    }
  } catch (e: Exception) {
    null
  }

  private fun expectedOrigin(sha256: String): String {
    val bytes = sha256.split(":").map { it.toInt(16).toByte() }.toByteArray()
    return "android:apk-key-hash:" + Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
  }

  private companion object {
    const val TAG = "PampaProvaPasskey"
  }
}
