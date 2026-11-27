package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.net.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Chi propone un ricordo dal ponte, **gia' verificato** dal cancello del provider dell'assistenza di PampAI (F9.1): la
 * firma Pampa, il pacchetto detto dal sistema (`getPackagesForUid`, uno solo), la tabella. [label] e' il nome dell'app
 * letto dal sistema, non scritto dall'app.
 */
data class AiMemoryCaller(val packageName: String, val label: String)

/**
 * Quali app possono proporre ricordi, e su quali argomenti: una chiave che comincia con uno dei loro prefissi. Cosi'
 * ClasseViva puo' dire «è in 4F» (`scuola.classe`) ma non «è vegetariana» (`cibo.dieta`). La tabella e' codice rivisto in
 * git, come quella dell'assistenza di F9.1 e quella dell'Account Pampa; le varianti di sviluppo (`.debug`, `.dev`)
 * contano solo se anche PampAI e' una build di sviluppo ([devBuild]).
 */
class AiMemoryAppPolicy(
  val apps: Map<String, Entry> = DEFAULT,
  val devBuild: Boolean = false,
) {
  /** Un'app della tabella: il nome e i prefissi delle chiavi che puo' proporre. */
  class Entry(val label: String, val keyPrefixes: Set<String>)

  /** La voce di [packageName], o null se non puo' proporre ricordi. */
  fun entryFor(packageName: String): Entry? {
    apps[packageName]?.let { return it }
    if (!devBuild) return null
    val base = DevSuffixes.firstOrNull { packageName.endsWith(it) }?.let { packageName.removeSuffix(it) } ?: return null
    return apps[base]
  }

  /** Vero se [packageName] puo' proporre un ricordo con la chiave [key]. Senza chiave no: un'app dice sempre l'argomento. */
  fun allows(packageName: String, key: String?): Boolean {
    val entry = entryFor(packageName) ?: return false
    val k = key ?: return false
    return entry.keyPrefixes.any { k == it.trimEnd('.') || k.startsWith(it) }
  }

  companion object {
    val DevSuffixes = listOf(".debug", ".dev")

    /**
     * La tabella di serie, **scelta di Alessio del 26/11/2026** (risposta 3): possono proporre ricordi da sole Pampa
     * Mail (lo stile delle risposte, la firma), MyCloud Gallery (i luoghi, le persone, gli album) e ClasseViva (la classe,
     * la scuola, le materie e le verifiche, che scadono). **Le altre app no**, Pampa Notes compresa: un ricordo coi loro
     * dati nasce solo se lo chiede il modello della chat di PampAI con lo strumento della memoria
     * ([AiMemorySource.KindTool]), e le domande arrivate da loro (la chat «Dalle app») non lasciano ricordi automatici.
     */
    val DEFAULT: Map<String, Entry> = mapOf(
      "dev.pampa.mail" to Entry("Pampa Mail", setOf("scrittura.", "mail.")),
      "dev.pampa.mycloudgallery" to Entry("MyCloud Gallery", setOf("foto.", "luoghi.", "persone.")),
      "dev.antigravity.classevivaexpressive" to Entry("ClasseViva", setOf("scuola.")),
    )
  }
}

/**
 * **I ricordi che nascono in un'altra app passano da PampAI** (F9.3): l'aggancio per il provider dell'assistenza di F9.1.
 * Il provider fa i suoi controlli (chi chiama, la firma, la tabella), poi passa qui i metodi dei ricordi
 * ([AiMemoryProtocol.Methods]) con la richiesta JSON e chi chiama; la risposta e' un [AiMemoryProposalResult] in JSON.
 *
 * ```kotlin
 * // Nel provider dell'assistenza di PampAI (F9.1), dopo il cancello:
 * if (method in AiMemoryProtocol.Methods) return bundleOf("json" to inbox.handle(method, json, AiMemoryCaller(pkg, label)))
 * ```
 *
 * Qui si decide: l'app e' nella tabella e l'argomento e' suo ([AiMemoryAppPolicy], di serie quella della memoria),
 * l'utente non gliel'ha tolto ([AiMemoryBook.setAppBlocked]), il tetto del giorno. Il ricordo poi segue le regole di
 * tutti: di serie torna `pending` e l'app mostra «Vuoi che PampAI ricordi…?» con Ricorda e No (la risposta torna con
 * [AiMemoryProtocol.MethodAnswer]; la stessa domanda resta anche nella pagina di PampAI); col salvataggio automatico torna
 * `remembered` e l'app mostra la sua card con Annulla. Un'app risponde, annulla e dimentica solo quello che ha proposto
 * lei.
 */
class AiMemoryInbox(
  private val book: AiMemoryBook,
  private val policy: AiMemoryAppPolicy = book.config.apps,
) {
  /** Risponde al metodo [method] con la richiesta [json] da [caller]. Mai un'eccezione: un errore e' un esito. */
  suspend fun handle(method: String, json: String, caller: AiMemoryCaller): String = runCatching {
    respond(method, json, caller)
  }.getOrElse { AiMemoryProposalResult(AiMemoryProposalResult.OutcomeInvalid) }.toJson()

  private suspend fun respond(method: String, json: String, caller: AiMemoryCaller): AiMemoryProposalResult {
    if (policy.entryFor(caller.packageName) == null) return AiMemoryProposalResult(AiMemoryProposalResult.OutcomeNotAllowed)
    val request = Json.parseToJsonElement(json) as? JsonObject ?: return AiMemoryProposalResult(AiMemoryProposalResult.OutcomeInvalid)
    val version = (request["memory"] as? JsonPrimitive)?.intOrNull
    if (version != AiMemoryProtocol.Version) return AiMemoryProposalResult(AiMemoryProposalResult.OutcomeInvalid)
    return when (method) {
      AiMemoryProtocol.MethodPropose -> propose(request, caller)
      AiMemoryProtocol.MethodAnswer -> {
        val id = request["noticeId"].string() ?: return AiMemoryProposalResult(AiMemoryProposalResult.OutcomeInvalid)
        val accept = (request["accept"] as? JsonPrimitive)?.booleanOrNull ?: return AiMemoryProposalResult(AiMemoryProposalResult.OutcomeInvalid)
        val outcome = book.answer(id, accept, app = caller.packageName)
        AiMemoryProposalResult(outcome.result, noticeId = outcome.notice?.id, text = outcome.memory?.text ?: outcome.notice?.text,
          previousText = outcome.notice?.previousText)
      }
      AiMemoryProtocol.MethodUndo -> {
        val id = request["noticeId"].string() ?: return AiMemoryProposalResult(AiMemoryProposalResult.OutcomeInvalid)
        AiMemoryProposalResult(if (book.undo(id, app = caller.packageName)) AiMemoryProposalResult.OutcomeUndone else AiMemoryProposalResult.OutcomeNotFound)
      }
      AiMemoryProtocol.MethodForgetMine ->
        AiMemoryProposalResult(AiMemoryProposalResult.OutcomeForgotten, count = book.forgetFromApp(caller.packageName, onlyProposed = true))
      else -> AiMemoryProposalResult(AiMemoryProposalResult.OutcomeInvalid)
    }
  }

  private suspend fun propose(request: JsonObject, caller: AiMemoryCaller): AiMemoryProposalResult {
    val proposal = AiMemoryProposal.fromJsonElement(request["proposal"])?.sanitized()
      ?: return AiMemoryProposalResult(AiMemoryProposalResult.OutcomeInvalid)
    if (!policy.allows(caller.packageName, proposal.key)) return AiMemoryProposalResult(AiMemoryProposalResult.OutcomeNotAllowed)
    // Un'app non dice «vale per ogni domanda»: lo decide solo l'utente (il nome, come rispondere).
    val outcome = book.remember(proposal.copy(always = false), AiMemorySource.app(caller.packageName, caller.label))
    val notice = outcome.notice
    return AiMemoryProposalResult(
      outcome = outcome.result,
      noticeId = notice?.id,
      // Per una domanda il fatto e' quello proposto (il ricordo non c'e' ancora); per gli altri, com'e' nella memoria.
      text = if (outcome.result == AiMemoryProposalResult.OutcomePending) notice?.text else outcome.memory?.text,
      previousText = notice?.previousText,
    )
  }
}
