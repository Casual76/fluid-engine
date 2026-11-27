package dev.antigravity.fluidengine.ai.memory

/**
 * Un turno di chat visto dalla memoria: cosa ha scritto l'utente e in che condizioni. L'estrattore legge **solo**
 * [userText] (e, come contesto, [previousAnswer]): mai i risultati dei tool, le pagine, le notifiche, lo schermo o gli
 * allegati. Cosi' un testo scritto da altri non puo' diventare un ricordo nemmeno se dice «ricorda che…».
 *
 * PampAI lo costruisce alla fine di ogni turno dalle cose che sa gia': la chat temporanea, il telefono bloccato, «non
 * usare la memoria» (`ResearchQuestionPlanner.storedKnowledgeAllowed`), il turno privato (`TurnPrivacy.privateTurn`).
 */
data class AiMemoryTurn(
  /** La conversazione di PampAI; diventa la fonte del ricordo. */
  val conversationId: Long?,
  /** Il messaggio dell'utente, solo il testo scritto o dettato da lui. */
  val userText: String,
  /** La risposta che l'utente aveva davanti (per capire «sì, è quella» o «4F»): solo contesto, mai un fatto. */
  val previousAnswer: String? = null,
  /** Quando: l'ora del turno. */
  val atMillis: Long,
  /** La chat temporanea: non lascia tracce, quindi non si ricorda niente. */
  val temporary: Boolean = false,
  /** Il telefono bloccato: chi parla potrebbe non essere il proprietario. */
  val locked: Boolean = false,
  /** Falso se l'utente ha escluso la memoria per questa domanda («senza usare quello che sai di me»). */
  val storedKnowledgeAllowed: Boolean = true,
  /**
   * Il turno ha usato dati di provenienza privata (le fonti della ricerca del profilo, i dati di un'app collegata letti
   * con un permesso): niente si ricorda in automatico, perche' un fatto potrebbe venire da li'.
   */
  val privateProvenance: Boolean = false,
  /**
   * Il testo dell'utente non e' tutto suo: un testo condiviso da un'altra app, incollato da una pagina, letto dallo
   * schermo. Niente si ricorda in automatico.
   */
  val untrustedInput: Boolean = false,
  /** Il turno ha letto testo scritto da altri (web, notifiche, schermo): «ricordati che…» allora chiede conferma. */
  val readExternal: Boolean = false,
  /** La lingua della chat («it», «en»): quella del prompt dell'estrattore e dei fatti. */
  val language: String = "it",
  /** Se la domanda arrivava da un'altra app (la chat «Dalle app» di F9.1): il pacchetto e il nome. */
  val app: String? = null,
  val appLabel: String? = null,
) {
  /** La fonte dei ricordi nati in questo turno. */
  val source: AiMemorySource get() = AiMemorySource.chat(conversationId, app, appLabel)
}

/**
 * Le regole della memoria, tutte in un posto: quando si ricorda da sola, quando chiede, quando no, e cosa non si ricorda
 * mai. Funzioni pure, provate sul computer (`AiMemoryPolicyTest`).
 */
object AiMemoryPolicy {
  /** Perche' l'estrattore non parte (o null se parte): costa zero gettoni. */
  fun automaticSkip(turn: AiMemoryTurn): String? = when {
    turn.temporary -> SkipTemporary
    turn.locked -> SkipLocked
    !turn.storedKnowledgeAllowed -> SkipRefused
    turn.privateProvenance -> SkipPrivate
    turn.untrustedInput -> SkipUntrusted
    turn.userText.isBlank() -> SkipEmpty
    else -> null
  }

  /**
   * Cosa fare di un «ricordati che…» chiesto dall'utente nel turno [turn] (dal tool `ricorda` o dalla domanda):
   * [VerdictAllowed], [VerdictAskFirst] (il turno ha letto testo scritto da altri, o dati privati: decide l'utente con un
   * tasto, come fa oggi PampAI con `confirmSafety`) o [VerdictRefused] (chat temporanea, telefono bloccato).
   */
  fun explicit(turn: AiMemoryTurn): String = when {
    turn.temporary || turn.locked -> VerdictRefused
    turn.privateProvenance || turn.untrustedInput || turn.readExternal -> VerdictAskFirst
    else -> VerdictAllowed
  }

  /**
   * Vero se [text] sembra un segreto, che non si ricorda mai, nemmeno se l'utente lo chiede: i ricordi entrano nelle
   * domande e vanno al fornitore del modello. Password, PIN, codici di verifica, IBAN, numeri di carta, codice fiscale,
   * chiavi dei fornitori. Meglio un fatto in meno che un segreto nel prompt di ogni domanda.
   */
  fun looksSecret(text: String): Boolean {
    val t = text.lowercase()
    if (SecretWords.containsMatchIn(t)) return true
    if (ProviderKey.containsMatchIn(text)) return true
    if (Iban.containsMatchIn(text.replace(" ", ""))) return true
    if (FiscalCode.containsMatchIn(text.uppercase())) return true
    // Una carta o un conto: 12 cifre o piu' di fila, anche separate da spazi o trattini.
    val digits = LongDigits.findAll(text).maxOfOrNull { m -> m.value.count { it.isDigit() } } ?: 0
    return digits >= 12
  }

  private val SecretWords = Regex(
    "\\b(password|passwd|passcode|pin|puk|otp|codice (di verifica|segreto|di sblocco|otp|pin|della carta|cvv)|cvv|cvc|" +
      "chiave (api|segreta|privata)|api key|secret key|seed phrase|frase di recupero|codice di recupero|recovery code)\\b",
  )
  private val ProviderKey = Regex("\\b(gsk_[A-Za-z0-9]{8,}|AIza[0-9A-Za-z_-]{10,}|sk-or-[A-Za-z0-9-]{8,}|sk-[A-Za-z0-9]{16,}|Bearer\\s+[A-Za-z0-9._-]{12,})")
  private val Iban = Regex("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b")
  private val FiscalCode = Regex("\\b[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]\\b")
  private val LongDigits = Regex("\\d[\\d -]{10,}\\d")

  const val SkipTemporary = "temporary"
  const val SkipLocked = "locked"
  const val SkipRefused = "refused"
  const val SkipPrivate = "private"
  const val SkipUntrusted = "untrusted"
  const val SkipEmpty = "empty"

  /** Il messaggio non dice niente di se' (il filtro locale, [AiMemorySignals]): nessuna chiamata. */
  const val SkipNoSignal = "no_signal"

  /** Il tetto dei ricordi automatici di oggi e' gia' pieno. */
  const val SkipDailyCap = "daily_cap"

  /**
   * La domanda arrivava da un'app che non puo' proporre ricordi da sola (risposta 3 di Alessio: solo Pampa Mail,
   * MyCloud Gallery e ClasseViva): per le altre un ricordo nasce solo se lo chiede il modello della chat di PampAI con lo
   * strumento della memoria.
   */
  const val SkipApp = "app_not_allowed"

  const val VerdictAllowed = "allowed"
  const val VerdictAskFirst = "ask_first"
  const val VerdictRefused = "refused"
}

/**
 * Il filtro locale prima dell'estrattore: la maggior parte dei messaggi («che tempo fa domani?», «metti un timer») non
 * dice niente di chi scrive, e per loro il modello non si chiama. Parole in prima persona e richieste di ricordare, in
 * italiano e in inglese. Sbaglia apposta per eccesso: un falso sì costa una chiamata piccola (il modello risponde «niente»),
 * un falso no perde un fatto. Le misure sono in `AiMemoryExtractorTest` (il corpus di 80 messaggi).
 */
object AiMemorySignals {
  private val Italian = Regex(
    "\\b(ricord\\w*|memorizz\\w*|tieni a mente|segnat[ie]|non dimenticar\\w*|" +
      "sono|ero|saro|mi chiamo|chiamami|dammi del|abito|vivo|lavoro|studio|frequento|faccio|gioco|suono|tifo|guido|" +
      "preferisco|preferirei|odio|amo|adoro|detesto|non mangio|non bevo|mangio|bevo|allergic\\w*|intollerant\\w*|" +
      "vegetarian\\w*|vegan\\w*|celiac\\w*|il mio|la mia|i miei|le mie|mio|mia|miei|mie|mi piace|mi piacciono|" +
      "ho (un|una|due|tre|quattro|\\d+) |ho cambiato|non sono piu|adesso sono|ora sono|vado a|vado in|" +
      "(ho|avro) (la|il|lo|l|un|una|gli|le|i) (verifica|interrogazion|esame|compito|partita|allenamento|lezione|visita|" +
      "appuntamento|colloquio|volo|treno)\\w*|da (lunedi|martedi|mercoledi|" +
      "giovedi|venerdi|sabato|domenica|domani|settembre|ottobre|gennaio|oggi) (sono|ho|vado|lavoro|studio|abito))\\b",
  )
  private val English = Regex(
    // Gia' normalizzato: l'apostrofo e' uno spazio («i'm» e' «i m», «don't» e' «don t»).
    "\\b(remember|keep in mind|don t forget|i am|i m|i was|my|mine|i live|i work|i study|i prefer|i like|i love|i hate|" +
      "call me|i don t eat|i have a|i moved|i changed)\\b",
  )

  /** Vero se [text] potrebbe dire qualcosa di chi scrive. */
  fun mayCarryFacts(text: String): Boolean {
    val t = normalize(text)
    return Italian.containsMatchIn(t) || English.containsMatchIn(t)
  }

  /** Vero se [text] chiede esplicitamente di ricordare («ricordati che…», «remember that…»). */
  fun asksToRemember(text: String): Boolean = Explicit.containsMatchIn(normalize(text))

  private val Explicit = Regex("\\b(ricordati|ricorda|ricordatelo|memorizza|tieni a mente|segnati|remember that|remember this|keep in mind)\\b")

  /** In minuscolo, senza accenti e con l'apostrofo come spazio: «perché» e «perche'» si leggono uguali. */
  internal fun normalize(text: String): String {
    val decomposed = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{M}+"), "").replace(Regex("['’`´]"), " ")
  }
}
