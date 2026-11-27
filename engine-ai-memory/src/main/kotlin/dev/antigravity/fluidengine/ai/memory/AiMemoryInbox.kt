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
 * `AiAssistHostProvider` fa i suoi controlli (chi chiama, la firma, il pacchetto dal sistema) e il «no» di F9.1 (un'app
 * rifiutata o bloccata in «App che usano PampAI» riceve `not_allowed`), poi passa qui i metodi dei ricordi
 * ([AiMemoryProtocol.Methods]) con la richiesta JSON e chi chiama; la risposta e' un [AiMemoryProposalResult] in JSON
 * (D5 di Alessio, R3-S1b).
 *
 * ```kotlin
 * // Nel provider dell'assistenza di PampAI (F9.1), la sottoclasse di AiAssistHostProvider:
 * override suspend fun memory(method: String, json: String, caller: AiAssistCaller): String =
 *   graph().memoryInbox.handle(method, json, AiMemoryCaller(caller.packageName, caller.label))
 * ```
 *
 * Qui si decide: l'app e' nella tabella e l'argomento e' suo ([AiMemoryAppPolicy], di serie quella della memoria),
 * l'utente non gliel'ha tolto ([AiMemoryBook.setAppBlocked]), il tetto del giorno. Il ricordo poi segue le regole di
 * tutti: di serie torna `pending` e l'app mostra «Vuoi che PampAI ricordi…?» con Ricorda e No (la risposta torna con
 * [AiMemoryProtocol.MethodAnswer]; la stessa domanda resta anche nella pagina di PampAI); col salvataggio automatico torna
 * `remembered` e l'app mostra la sua card con Annulla. Un'app risponde, annulla e dimentica solo quello che ha proposto
 * lei.
 *
 * **La risposta non porta niente della memoria** (R3-S1): l'esito, l'id dell'avviso e al piu' il fatto che l'app ha
 * scritto. Il ricordo con la stessa chiave che resta (`kept`) o che viene sostituito (`pending`, `updated`) e' dell'utente:
 * lo mostra solo la pagina di PampAI.
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
        // Il fatto della domanda e' quello che l'app ha scritto: si prende prima, dalla domanda sua (R3-S1).
        val own = book.pending.value.firstOrNull { it.id == id && it.source.fromApp && it.source.app == caller.packageName }?.text
        val outcome = book.answer(id, accept, app = caller.packageName)
        AiMemoryProposalResult(outcome.result, noticeId = outcome.notice?.id, text = own?.takeIf { outcome.notice != null })
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
    // Torna solo cio' che l'app sa gia': l'esito, l'avviso e il fatto che ha scritto lei. Mai un ricordo di PampAI (il
    // fatto che resta per «kept», quello di prima per «pending» e «updated»): quelli sono della pagina di PampAI (R3-S1).
    return AiMemoryProposalResult(
      outcome = outcome.result,
      noticeId = notice?.id,
      text = proposal.text.takeIf { notice != null },
    )
  }
}
