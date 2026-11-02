package dev.antigravity.fluidengine.aiui.strings

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.ai.orchestrator.SwitchReason
import dev.antigravity.fluidengine.ai.provider.ModelTier

/**
 * Le parole dell'overlay, della card e della chat: tutte, con i default italiani.
 *
 * Come `FluidAppearanceStrings`: un'app in una lingua sola non scrive niente, un'app tradotta le
 * passa **tutte** (una sola lasciata al default e' una frase italiana in mezzo a una pagina
 * inglese, e nessuno se ne accorge finche' non gira il telefono). Un componente non puo' leggere le
 * risorse da se': le legge l'app, prima, e le passa. I modelli con segnaposto usano il formato di
 * `String.format` (`%1$s`, `%2$d`): sono quelli che una risorsa Android ha gia', senza argomenti.
 *
 * Quello che qui **non** c'e' e' il dominio. Le frasi dei gruppi di strumenti dell'app ("Guardo i
 * voti…", "Cerco il posto…") arrivano da [statusFor], che l'app riempie con la sua tabella: ritorna
 * null per le chiavi che non conosce, e allora vale la frase generica.
 */
@Immutable
data class FluidAssistantStrings(
  val title: String = "Assistente",
  val askPlaceholder: String = "Chiedi qualcosa…",

  // --- le righe di stato ---------------------------------------------------------------------
  val listening: String = "Ti ascolto…",
  val transcribing: String = "Trascrivo…",
  val classifying: String = "Capisco cosa serve…",
  val thinking: String = "Penso…",
  val thinkingDeep: String = "Analizzo con calma…",
  val moreTools: String = "Mi serve dell'altro…",
  val deepModel: String = "Passo al modello più capace…",
  /** Sta in coda a una frase di stato quando si lavora col livello profondo: `"%1$s"` non serve, e' un suffisso. */
  val deepSuffix: String = " (modello profondo)",
  /** Il numero delle chiamate in parallelo: base e quante. */
  val parallel: String = "%1\$s (+%2\$d)",
  val waiting: String = "%1\$s è al limite: riprovo fra %2\$d s",
  val switching: String = "Passo a %1\$s…",
  /** Un cambio di servizio con il perche': chi lascia, perche' (da [reasonText]), chi prende. */
  val switchingWhy: String = "%1\$s %2\$s: passo a %3\$s…",
  val writing: String = "Rispondo…",
  val needsConfirmation: String = "Serve una conferma",
  val heardNothing: String = "Non ho sentito niente",
  val stopped: String = "Fermato",
  val answerReady: String = "Risposta pronta",
  val idleStatus: String = "Chiedimi qualcosa",
  /** La testata del pannello ancorato a risposta finita. */
  val dockDone: String = "Fatto",

  // --- i perche' di un cambio di servizio (SwitchReason), al passato ---------------------------
  val reasonRateLimited: String = "era al limite",
  val reasonServer: String = "non era disponibile",
  val reasonTimeout: String = "non rispondeva",
  val reasonNetwork: String = "non era raggiungibile",
  val reasonToolUseFailed: String = "ha sbagliato a usare uno strumento",
  val reasonModelUnavailable: String = "non ha più il modello scelto",
  val reasonContextTooLong: String = "non reggeva una conversazione così lunga",
  val reasonBadRequest: String = "ha rifiutato la richiesta",
  val reasonEmptyAnswer: String = "ha dato una risposta vuota",
  val reasonParse: String = "ha risposto in modo illeggibile",
  /** La riga sotto una risposta arrivata da un altro servizio: chi ha risposto, e i perche'. */
  val answeredBy: String = "Ha risposto %1\$s: %2\$s",
  /** Il nome generico del servizio quando non si sa quale sia. */
  val theService: String = "Il servizio",

  // --- i fallimenti (FailureKind) --------------------------------------------------------------
  val failNoKeys: String = "Nessuna chiave verificata: aggiungine una nelle impostazioni.",
  val failUnauthorized: String = "La chiave non è più valida: controllala nelle impostazioni.",
  /** Limite con i secondi: servizio, secondi. */
  val failRateLimitedIn: String = "%1\$s è al limite: riprova fra %2\$d s (o accendi la riserva automatica nelle impostazioni).",
  val failRateLimited: String = "%1\$s è al limite di richieste: riprova fra poco (o accendi la riserva automatica nelle impostazioni).",
  /** Il limite dopo che la riserva li ha provati tutti: i nomi, i secondi (già in forma "40 s" o "poco"). */
  val failRateLimitedAll: String = "Sono al limite %1\$s: riprova fra %2\$s.",
  val failNetwork: String = "Niente rete: controlla la connessione e riprova.",
  val failTimeout: String = "Ci ha messo troppo. Riprova, o dividi la domanda in pezzi più piccoli.",
  val failBlocked: String = "Il servizio ha rifiutato la richiesta per i suoi filtri: prova a riformularla, o a rigenerare con un altro servizio.",
  val failProvider: String = "%1\$s ha risposto con un errore: riprova, o rigenera con un altro servizio.",
  val failModelUnavailable: String = "Il modello scelto per %1\$s non c'è più: ne ho scelto un altro, riprova.",
  val failContextTooLong: String = "La conversazione è troppo lunga per %1\$s: aprine una nuova, o rigenera con un altro servizio.",
  val failMicrophone: String = "Il microfono non è disponibile: chiudi l'app che lo sta usando.",
  val failTranscription: String = "Non sono riuscito a trascrivere: riprova.",
  val failUnknown: String = "Qualcosa è andato storto.",
  /** "questo servizio", dentro le frasi qui sopra che nominano un servizio che non si conosce. */
  val thisService: String = "questo servizio",
  /** Il "e" dell'elenco dei servizi: "Groq e Gemini", "Groq, Gemini e OpenRouter". */
  val and: String = "e",
  val soon: String = "poco",

  // --- i titoli brevi dei fallimenti, per il foglio dei dettagli ---------------------------------
  val failTitleNoKeys: String = "Nessuna chiave",
  val failTitleUnauthorized: String = "Chiave non valida",
  val failTitleRateLimited: String = "Limite di richieste",
  val failTitleNetwork: String = "Niente rete",
  val failTitleTimeout: String = "Tempo scaduto",
  val failTitleBlocked: String = "Bloccata dai filtri",
  val failTitleProvider: String = "Errore del servizio",
  val failTitleModelUnavailable: String = "Modello non disponibile",
  val failTitleContextTooLong: String = "Conversazione troppo lunga",
  val failTitleMicrophone: String = "Microfono occupato",
  val failTitleTranscription: String = "Trascrizione non riuscita",
  val failTitleUnknown: String = "Errore sconosciuto",

  // --- i tasti e le etichette -------------------------------------------------------------------
  val stop: String = "Ferma",
  val send: String = "Invia",
  val voice: String = "Chiedi a voce",
  val newConversation: String = "Nuova conversazione",
  val tapToFinish: String = "Tocca per finire",
  val confirm: String = "Conferma",
  val cancel: String = "Annulla",
  val close: String = "Chiudi l'assistente",
  val finishedSpeaking: String = "Ho finito di parlare",
  val details: String = "Dettagli",
  val examples: String = "Per esempio",
  val failureDetailsTitle: String = "Dettagli dell'errore",
  val failureWhatHappened: String = "Cosa è successo",
  val failureService: String = "Servizio",
  val failureReason: String = "Motivo",
  val failureServiceAnswer: String = "Risposta del servizio",
  val failureCode: String = "Codice %1\$d",
  val failureNoCode: String = "Senza codice",
  val failureTried: String = "Servizi provati",
  val failureCopy: String = "Copia",
  val failureCopyHeader: String = "Dettagli dell'errore",
  val failureCopyHttp: String = "Codice HTTP: %1\$d",
  val failureCopyAnswer: String = "Risposta del servizio: %1\$s",
  val failureCopyTechnical: String = "Dettaglio tecnico: %1\$s",
  val failureCopyService: String = "Servizio: %1\$s",
  val failureCopyReason: String = "Motivo: %1\$s",
  val failureCopyTried: String = "Servizi provati: %1\$s",

  /**
   * La frase di stato di un gruppo di strumenti dell'app (`AssistantState.Working.statusKey`):
   * "voti" diventa "Guardo i voti…". Null per una chiave che l'app non conosce: vale [thinking].
   * E' il solo punto in cui il dominio entra, e lo mette l'app.
   */
  val statusFor: (key: String, tier: ModelTier) -> String? = { _, _ -> null },
) {
  /** Il perche' di un cambio di servizio, al passato: "era al limite", "non rispondeva"... */
  fun reasonText(reason: SwitchReason): String = when (reason) {
    SwitchReason.RATE_LIMITED -> reasonRateLimited
    SwitchReason.SERVER -> reasonServer
    SwitchReason.TIMEOUT -> reasonTimeout
    SwitchReason.NETWORK -> reasonNetwork
    SwitchReason.TOOL_USE_FAILED -> reasonToolUseFailed
    SwitchReason.MODEL_UNAVAILABLE -> reasonModelUnavailable
    SwitchReason.CONTEXT_TOO_LONG -> reasonContextTooLong
    SwitchReason.BAD_REQUEST -> reasonBadRequest
    SwitchReason.EMPTY_ANSWER -> reasonEmptyAnswer
    SwitchReason.PARSE -> reasonParse
  }
}

/**
 * `String.format` che non esplode sul modello rovinato: una traduzione con un segnaposto in meno
 * (o in piu') deve produrre una frase storta, non far cadere l'app mentre mostra un errore.
 */
internal fun fluidFormat(template: String, vararg args: Any?): String =
  try {
    String.format(java.util.Locale.getDefault(), template, *args)
  } catch (_: java.util.IllegalFormatException) {
    template
  }
