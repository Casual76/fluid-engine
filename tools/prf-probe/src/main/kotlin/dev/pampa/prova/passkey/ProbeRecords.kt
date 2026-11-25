package dev.pampa.prova.passkey

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * I gestori di passkey riconosciuti dall'AAGUID scritto alla creazione. Fonte: l'elenco
 * `passkeydeveloper/passkey-authenticator-aaguids` (`aaguid.json`), letto il 25/11/2026; fiducia media (lettura
 * riassunta, non riga per riga). Google e Samsung Pass sono quelli della prova; gli altri servono solo a non scrivere
 * «sconosciuto» se Alessio ne ha uno.
 */
internal object KnownManagers {
  const val GOOGLE = "Gestore password di Google"
  const val SAMSUNG = "Samsung Pass"

  private val byAaguid = mapOf(
    "ea9b8d66-4d01-1d21-3ce4-b6b48cb575d4" to GOOGLE,
    "53414d53-554e-4700-0000-000000000000" to SAMSUNG,
    "bada5566-a7aa-401f-bd96-45619a55120d" to "1Password",
    "d548826e-79b4-db40-a3d8-11116f7e8349" to "Bitwarden",
    "531126d6-e717-415c-9320-3d9aa6981239" to "Dashlane",
  )

  /** Il nome del gestore; con l'AAGUID a zero o sconosciuto, quello dichiarato da chi prova, se c'è. */
  fun name(aaguid: String?, declared: String?): String {
    val known = aaguid?.let { byAaguid[it] }
    if (known != null) return known
    val what = if (aaguid == null || aaguid == "00000000-0000-0000-0000-000000000000") "AAGUID non dichiarato" else "AAGUID $aaguid"
    return if (declared != null) "$declared ($what)" else "Sconosciuto ($what)"
  }
}

/**
 * L'impronta di un'uscita del PRF: 4 byte di `SHA-256("pampa/prova/impronta" ‖ prf)`, in esadecimale. Basta a dire se
 * due uscite sono uguali, e non dice niente del valore: il PRF vero non si scrive mai, né a schermo né nel riassunto.
 */
internal fun prfFingerprint(prf: ByteArray): String {
  val digest = MessageDigest.getInstance("SHA-256")
  digest.update("pampa/prova/impronta".toByteArray(Charsets.UTF_8))
  return digest.digest(prf).copyOf(4).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

internal data class CreationRecord(
  val credentialId: String,
  val manager: String,
  val aaguid: String?,
  /** Il gestore dichiara il PRF (`prf.enabled`). */
  val prfEnabled: Boolean?,
  /** Si è chiesto il valore già alla creazione (`eval.first`). */
  val askedValue: Boolean,
  /** L'impronta del valore avuto alla creazione, se c'è. */
  val prfAtCreation: String?,
  val backupEligible: Boolean,
  val backedUp: Boolean,
  val userVerified: Boolean,
  val userName: String,
  val at: Long,
)

internal data class AccessRecord(
  val credentialId: String,
  val prf: String?,
  val backupEligible: Boolean,
  val backedUp: Boolean,
  val userVerified: Boolean,
  val at: Long,
)

internal data class FailureRecord(val step: String, val phrase: String, val cause: String, val at: Long)

/** Quello che si sa del telefono e dell'app, per la testa del riassunto. */
internal data class ProbeEnvironment(
  val rpId: String,
  val app: String,
  val signingSha256: String?,
  val expectedSha256: String,
  val origin: String?,
  val device: String,
  val android: String,
  val unsupported: String?,
)

/** Il registro della prova: creazioni, accessi, errori. Niente segreti: solo impronte, flag e nomi. */
internal class ProbeLog(
  val creations: MutableList<CreationRecord> = mutableListOf(),
  val accesses: MutableList<AccessRecord> = mutableListOf(),
  val failures: MutableList<FailureRecord> = mutableListOf(),
  /** Il gestore che chi prova dice di avere scelto nelle impostazioni (serve se l'AAGUID è a zero). */
  var declaredManager: String? = null,
) {
  fun managerOf(credentialId: String): String =
    creations.lastOrNull { it.credentialId == credentialId }?.manager ?: "Passkey non creata da questa prova"

  /** Il resoconto per gestore e il confronto: è quello che finisce a schermo, negli appunti e nel file. */
  fun summary(env: ProbeEnvironment): String = buildString {
    appendLine("Pampa · prova passkey — riassunto (F10.2)")
    appendLine("RP ID: ${env.rpId}")
    appendLine("App: ${env.app}")
    appendLine("Firma dell'app: ${env.signingSha256 ?: "non letta"}")
    appendLine(
      if (env.signingSha256.equals(env.expectedSha256, ignoreCase = true)) "  = quella di assetlinks.json di prova"
      else "  ATTENZIONE: diversa da quella di assetlinks.json (${env.expectedSha256}): i gestori rifiuteranno",
    )
    env.origin?.let { appendLine("Origine vista dal gestore: $it") }
    appendLine("Telefono: ${env.device}, ${env.android}")
    appendLine("Passkey possibili: ${env.unsupported ?: "sì"}")
    declaredManager?.let { appendLine("Gestore dichiarato da chi prova: $it") }
    appendLine()
    val managers = (creations.map { it.manager } + accesses.map { managerOf(it.credentialId) }).distinct()
    if (managers.isEmpty()) appendLine("Ancora nessuna prova.")
    val verdicts = linkedMapOf<String, ManagerVerdict>()
    for (manager in managers) {
      val verdict = verdict(manager)
      verdicts[manager] = verdict
      appendLine("== $manager")
      appendLine("  passkey create: ${verdict.created}, accessi: ${verdict.accessCount}")
      appendLine("  PRF dichiarato alla creazione: ${yesNo(verdict.prfDeclared)}")
      appendLine("  valore del PRF alla creazione: ${yesNo(verdict.prfAtCreation)}")
      appendLine("  valore del PRF all'accesso: ${yesNo(verdict.prfAtAccess)}")
      appendLine("  stesso valore fra due accessi: ${yesNo(verdict.stableAcrossAccesses, "serve un secondo accesso")}")
      appendLine("  stesso valore fra creazione e accesso: ${yesNo(verdict.sameAsCreation, "—")}")
      appendLine("  BE (sincronizzabile): ${yesNo(verdict.backupEligible)}, BS (sincronizzata): ${yesNo(verdict.backedUp)}")
      appendLine("  UV (impronta o PIN): ${yesNo(verdict.userVerified)}")
      appendLine("  porta dei dati possibile: ${yesNo(verdict.doorPossible, "da provare")}")
      for (access in accesses.filter { managerOf(it.credentialId) == manager }) {
        appendLine("  · accesso ${clock(access.at)}: PRF ${access.prf ?: "assente"}, BE ${yesNo(access.backupEligible)}, BS ${yesNo(access.backedUp)}")
      }
      appendLine()
    }
    val google = verdicts[KnownManagers.GOOGLE]
    val samsung = verdicts[KnownManagers.SAMSUNG]
    if (google != null && samsung != null) {
      appendLine("== Differenze fra Google e Samsung Pass")
      val differences = google.differences(samsung)
      if (differences.isEmpty()) appendLine("  nessuna") else differences.forEach { appendLine("  - $it") }
      appendLine()
    }
    if (verdicts.isNotEmpty()) {
      val ready = verdicts.filterValues { it.doorPossible == true }.keys
      val missing = verdicts.filterValues { it.doorPossible == false }.keys
      appendLine("== Esito per F10.4")
      appendLine("  PRF presente e stabile con: ${ready.joinToString().ifEmpty { "nessuno" }}")
      if (missing.isNotEmpty()) appendLine("  PRF assente o instabile con: ${missing.joinToString()} → ramo «codice di recupero per tutti»")
      appendLine()
    }
    if (failures.isNotEmpty()) {
      appendLine("== Errori")
      failures.forEach { appendLine("  ${clock(it.at)} ${it.step}: ${it.phrase} (${it.cause})") }
    }
  }.trimEnd() + "\n"

  internal data class ManagerVerdict(
    val created: Int,
    val accessCount: Int,
    val prfDeclared: Boolean?,
    val prfAtCreation: Boolean?,
    val prfAtAccess: Boolean?,
    val stableAcrossAccesses: Boolean?,
    val sameAsCreation: Boolean?,
    val backupEligible: Boolean?,
    val backedUp: Boolean?,
    val userVerified: Boolean?,
  ) {
    /** La passkey può essere una porta dei dati: PRF all'accesso, stabile, e sincronizzata (chiavi.md K2). */
    val doorPossible: Boolean?
      get() = when {
        prfAtAccess == false || stableAcrossAccesses == false -> false
        prfAtAccess == true && stableAcrossAccesses == true -> backedUp == true
        else -> null
      }

    fun differences(other: ManagerVerdict): List<String> {
      val out = mutableListOf<String>()
      fun compare(what: String, a: Boolean?, b: Boolean?) {
        if (a != b) out += "$what: Google ${yesNo(a)}, Samsung Pass ${yesNo(b)}"
      }
      compare("PRF dichiarato alla creazione", prfDeclared, other.prfDeclared)
      compare("valore alla creazione", prfAtCreation, other.prfAtCreation)
      compare("valore all'accesso", prfAtAccess, other.prfAtAccess)
      compare("stabile fra due accessi", stableAcrossAccesses, other.stableAcrossAccesses)
      compare("uguale fra creazione e accesso", sameAsCreation, other.sameAsCreation)
      compare("BE", backupEligible, other.backupEligible)
      compare("BS", backedUp, other.backedUp)
      return out
    }
  }

  fun verdict(manager: String): ManagerVerdict {
    val made = creations.filter { it.manager == manager }
    val used = accesses.filter { managerOf(it.credentialId) == manager }
    // Per credenziale: tutti gli accessi col PRF devono dare la stessa impronta.
    val byCredential = used.groupBy { it.credentialId }
    val stable = byCredential.values.filter { list -> list.count { it.prf != null } >= 2 }
      .map { list -> list.mapNotNull { it.prf }.distinct().size == 1 }
    val sameAsCreation = made.filter { it.prfAtCreation != null }.mapNotNull { c ->
      byCredential[c.credentialId]?.mapNotNull { it.prf }?.firstOrNull()?.let { it == c.prfAtCreation }
    }
    val asked = made.filter { it.askedValue }
    return ManagerVerdict(
      created = made.size,
      accessCount = used.size,
      prfDeclared = made.mapNotNull { it.prfEnabled }.takeIf { it.isNotEmpty() }?.all { it },
      prfAtCreation = asked.takeIf { it.isNotEmpty() }?.all { it.prfAtCreation != null },
      prfAtAccess = used.takeIf { it.isNotEmpty() }?.all { it.prf != null },
      stableAcrossAccesses = stable.takeIf { it.isNotEmpty() }?.all { it },
      sameAsCreation = sameAsCreation.takeIf { it.isNotEmpty() }?.all { it },
      backupEligible = (made.map { it.backupEligible } + used.map { it.backupEligible }).takeIf { it.isNotEmpty() }?.all { it },
      backedUp = (made.map { it.backedUp } + used.map { it.backedUp }).takeIf { it.isNotEmpty() }?.all { it },
      userVerified = (made.map { it.userVerified } + used.map { it.userVerified }).takeIf { it.isNotEmpty() }?.all { it },
    )
  }

  fun toJson(): String = JsonObject(
    mapOf(
      "declaredManager" to (declaredManager?.let { JsonPrimitive(it) } ?: JsonNull),
      "creations" to JsonArray(creations.map { c ->
        JsonObject(
          mapOf(
            "credentialId" to JsonPrimitive(c.credentialId), "manager" to JsonPrimitive(c.manager),
            "aaguid" to (c.aaguid?.let { JsonPrimitive(it) } ?: JsonNull), "prfEnabled" to JsonPrimitive(c.prfEnabled),
            "askedValue" to JsonPrimitive(c.askedValue), "prfAtCreation" to JsonPrimitive(c.prfAtCreation),
            "be" to JsonPrimitive(c.backupEligible), "bs" to JsonPrimitive(c.backedUp), "uv" to JsonPrimitive(c.userVerified),
            "userName" to JsonPrimitive(c.userName), "at" to JsonPrimitive(c.at),
          ),
        )
      }),
      "accesses" to JsonArray(accesses.map { a ->
        JsonObject(
          mapOf(
            "credentialId" to JsonPrimitive(a.credentialId), "prf" to JsonPrimitive(a.prf),
            "be" to JsonPrimitive(a.backupEligible), "bs" to JsonPrimitive(a.backedUp), "uv" to JsonPrimitive(a.userVerified),
            "at" to JsonPrimitive(a.at),
          ),
        )
      }),
      "failures" to JsonArray(failures.map { f ->
        JsonObject(mapOf("step" to JsonPrimitive(f.step), "phrase" to JsonPrimitive(f.phrase), "cause" to JsonPrimitive(f.cause), "at" to JsonPrimitive(f.at)))
      }),
    ),
  ).toString()

  companion object {
    fun fromJson(text: String?): ProbeLog {
      if (text.isNullOrBlank()) return ProbeLog()
      return try {
        val root = Json.parseToJsonElement(text).jsonObject
        ProbeLog(
          creations = root["creations"]!!.jsonArray.map { it.jsonObject }.map { o ->
            CreationRecord(
              credentialId = o.s("credentialId")!!, manager = o.s("manager")!!, aaguid = o.s("aaguid"),
              prfEnabled = o.b("prfEnabled"), askedValue = o.b("askedValue") ?: true, prfAtCreation = o.s("prfAtCreation"),
              backupEligible = o.b("be") == true, backedUp = o.b("bs") == true, userVerified = o.b("uv") == true,
              userName = o.s("userName") ?: "", at = o.l("at"),
            )
          }.toMutableList(),
          accesses = root["accesses"]!!.jsonArray.map { it.jsonObject }.map { o ->
            AccessRecord(o.s("credentialId")!!, o.s("prf"), o.b("be") == true, o.b("bs") == true, o.b("uv") == true, o.l("at"))
          }.toMutableList(),
          failures = root["failures"]!!.jsonArray.map { it.jsonObject }.map { o ->
            FailureRecord(o.s("step") ?: "", o.s("phrase") ?: "", o.s("cause") ?: "", o.l("at"))
          }.toMutableList(),
          declaredManager = root.s("declaredManager"),
        )
      } catch (e: Exception) {
        ProbeLog()
      }
    }

    private fun JsonObject.s(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private fun JsonObject.b(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.l(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun yesNo(value: Boolean?, unknown: String = "non provato"): String = when (value) {
      true -> "sì"
      false -> "no"
      null -> unknown
    }

    private fun clock(at: Long): String = SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(Date(at))
  }
}
