package dev.antigravity.fluidengine.ai.memory

import kotlinx.coroutines.withTimeoutOrNull

/**
 * **Il modello del telefono** per la memoria (F9.3b, risposta 5 di Alessio): il servizio di sistema **AICore** di
 * Android, coi modelli che il sistema gia' offre (Gemini Nano), senza niente da scaricare o installare. Il collegamento
 * vero (le API GenAI di ML Kit sopra AICore) e' di F9.5; qui c'e' solo la porta, una funzione: istruzioni e testo dentro,
 * testo fuori. Null se il telefono non ha AICore o il modello non e' pronto.
 *
 * **Cosa fa AICore per la memoria, e cosa no.** AICore da' un modello che scrive testo (il «Prompt API»: istruzioni e
 * testo dentro, testo fuori) e alcuni compiti pronti (riassunto, correzione, riscrittura, descrizione di un'immagine).
 * **Non da' vettori del significato** (niente embedding): la pertinenza «per significato» alla EmbeddingGemma non la puo'
 * fare. Per questo la memoria lo usa in due modi:
 * - **allargare la domanda** ([AiMemoryQueryExpansion]): «cosa cucino stasera?» diventa anche «cena, ricette, cibo»,
 *   e l'indice per parole trova «è vegetariana», che ha quelle parole fra i suoi argomenti. E' la stessa idea degli
 *   argomenti che l'estrattore scrive accanto a ogni fatto, fatta dall'altra parte;
 * - **estrarre i fatti** sul telefono ([AiMemoryExtractor.onDevice]): gratis, senza rete, senza gettoni del fornitore.
 *
 * **Senza AICore** valgono le altre due strade della risposta 5: un modello del significato scaricato col consenso
 * ([AiMemoryConfig.embedder], `FluidLiteRtEmbedder` di `engine-search-semantic`), oppure niente, e la pertinenza va per
 * parole e argomenti.
 */
fun interface AiMemoryOnDeviceModel {
  /** Il testo del modello per [instructions] e [input], al piu' [maxOutputTokens] gettoni; null se non risponde. */
  suspend fun generate(instructions: String, input: String, maxOutputTokens: Int): String?
}

/**
 * Allargare la domanda col modello del telefono: le parole dell'argomento che la domanda non dice («cosa cucino
 * stasera?» → «cena, ricette, cibo, dieta»). Poche parole, al piu' [MaxWords], con un tempo massimo: se il modello e'
 * lento o non risponde, la ricerca va per le sole parole della domanda e la risposta non aspetta.
 */
object AiMemoryQueryExpansion {
  /** Quante parole al piu'. */
  const val MaxWords: Int = 6

  /** Il tetto della risposta del modello: 24 gettoni bastano per sei parole. */
  const val MaxOutputTokens: Int = 24

  /**
   * Quanto si aspetta al piu': **800 ms** (a occhio, 26/11/2026): un prompt di cento gettoni con una risposta di venti, sul
   * modello del telefono, sta sotto il mezzo secondo sui telefoni che hanno AICore (fiducia bassa: da misurare in F9.5).
   */
  const val TimeoutMillis: Long = 800

  /** Le istruzioni del modello. */
  fun instructions(language: String): String = if (language == "it") {
    "Scrivi da 3 a $MaxWords parole chiave, in minuscolo, separate da virgole, per gli argomenti della domanda " +
      "dell'utente: le parole che userebbe un appunto su di lui (scuola, cibo, casa, trasporti, salute, famiglia, lavoro, " +
      "sport, musica…). Niente frasi, niente spiegazioni."
  } else {
    "Write 3 to $MaxWords lowercase keywords, comma separated, for the topics of the user's question: the words a note " +
      "about them would use (school, food, home, transport, health, family, work, sport, music…). No sentences."
  }

  /** Le parole per [question], o niente se il modello non risponde in [timeoutMillis]. */
  suspend fun expand(model: AiMemoryOnDeviceModel, question: String, language: String = "it", timeoutMillis: Long = TimeoutMillis): List<String> {
    if (question.isBlank()) return emptyList()
    val text = withTimeoutOrNull(timeoutMillis) {
      runCatching { model.generate(instructions(language), question.take(600), MaxOutputTokens) }.getOrNull()
    } ?: return emptyList()
    return parse(text)
  }

  /** Le parole di [text]: separate da virgole, punti o a capo, in minuscolo, di lettere, al piu' [MaxWords]. */
  fun parse(text: String): List<String> = text.lowercase().split(Regex("[,;\\n.]+"))
    .map { w -> w.trim().trim('-', '*', '•', '"').filter { it.isLetter() || it == ' ' }.trim() }
    .filter { it.length in 2..AiMemoryProposal.MaxTopicChars && it.count { c -> c == ' ' } <= 1 }
    .distinct()
    .take(MaxWords)
}
