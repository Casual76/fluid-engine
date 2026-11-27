package dev.antigravity.fluidengine.ai.bridge.assist

/**
 * La pulizia del testo che esce da PampAI verso l'app che chiama: cio' che somiglia a una chiave diventa
 * [Hidden]. E' difesa in profondita': il formato non ha campi per chiavi o modelli e gli errori passano come codici, ma
 * un modello o un tool potrebbero scrivere una chiave nel testo (un errore copiato, un file di configurazione letto).
 *
 * Le forme riconosciute: Groq (`gsk_…`), Google (`AIza…`), OpenRouter (`sk-or-…`), OpenAI e simili (`sk-…`), un
 * `Bearer …`, un JWT, e `api_key=…` / `x-goog-api-key: …` / `Authorization: …` (anche `Authorization: Basic …`).
 */
object AiAssistRedaction {
  const val Hidden = "[chiave nascosta]"

  private val Patterns: List<Regex> = listOf(
    Regex("gsk_[A-Za-z0-9]{16,}"),
    Regex("AIza[0-9A-Za-z_\\-]{20,}"),
    Regex("sk-or-v1-[A-Za-z0-9]{16,}"),
    Regex("sk-(?:proj-|ant-)?[A-Za-z0-9_\\-]{16,}"),
    Regex("eyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}"),
    Regex("(?i)bearer\\s+[A-Za-z0-9._\\-]{12,}"),
    // R3-S1: anche lo schema prima del valore («Authorization: Basic …», «Token …»), che da solo non arriva a 8.
    Regex("(?i)(?:api[_-]?key|x-goog-api-key|authorization)\\s*[:=]\\s*(?:(?:bearer|basic|token)\\s+)?\\S{8,}"),
  )

  /**
   * Il pezzo finale di un testo che puo' essere l'inizio di una chiave col suo prefisso staccato da spazi: «Bearer »,
   * «Authorization: », «api_key = », «Authorization: Basic ». Il valore arriva dopo: il filtro a flusso li tiene insieme.
   */
  private val Context = Regex(
    "(?i)(?:(?:api[_-]?key|x-goog-api-key|authorization)\\s*(?:[:=]\\s*)?(?:(?:bearer|basic|token)\\s+)?|bearer\\s+)$",
  )

  /** Come comincia ogni chiave che [Patterns] riconosce: le prime con le maiuscole giuste, le altre in qualunque forma. */
  private val Markers = listOf("gsk_", "AIza", "sk-", "eyJ")
  private val MarkersAnyCase = listOf("bearer", "apikey", "api_key", "api-key", "authorization")
  private const val LongestMarker = 13

  /**
   * I nomi dei fornitori e dei modelli di PampAI, per le righe che non sono la risposta (lo stato, il motivo di un
   * errore): l'app non deve sapere chi risponde (R3-S1). La risposta no: «llama» li' puo' essere l'animale.
   */
  private val ModelNames = Regex(
    "(?i)(?<![\\p{L}\\p{N}])(?:[a-z0-9][a-z0-9_.-]*/)?" +
      "(?:llama|qwen|gemma|gemini|gpt|mistral|mixtral|deepseek|claude|kimi|whisper|groq|openrouter)[a-z0-9_.:/-]*",
  )
  private const val HiddenModel = "[modello]"

  /** [text] senza niente che somigli a una chiave. */
  fun scrub(text: String): String {
    if (text.length < 12) return text
    var out = text
    for (pattern in Patterns) out = pattern.replace(out, Hidden)
    return out
  }

  /** Una riga di PampAI per l'app (lo stato, il motivo): senza chiavi e senza i nomi del fornitore e del modello. */
  internal fun scrubLine(line: String): String = ModelNames.replace(scrub(line), HiddenModel)

  /** Dove comincia, in [text], la prima cosa che puo' essere una chiave; -1 se niente. */
  private fun firstMarker(text: CharSequence): Int {
    val s = text.toString()
    var first = -1
    fun take(i: Int) { if (i >= 0 && (first < 0 || i < first)) first = i }
    Markers.forEach { take(s.indexOf(it)) }
    MarkersAnyCase.forEach { take(s.indexOf(it, ignoreCase = true)) }
    return first
  }

  /**
   * Il testo che arriva a pezzi, ripulito a **parole intere**: una chiave non si spezza mai fra due invii, quindi la
   * pulizia la vede sempre tutta. [push] restituisce cio' che si puo' mandare adesso (fino all'ultimo spazio) e tiene
   * il resto; [drain] restituisce tutto.
   *
   * Dopo R3-S1:
   * - un prefisso staccato dal valore («Bearer », «Authorization: », «api_key = ») resta indietro col valore che segue;
   * - una «parola» piu' lunga di [maxHold] caratteri (un indirizzo, un blocco di codice) esce lo stesso, ma solo fino a
   *   dove puo' cominciare una chiave: da li' si aspetta la fine della parola, al piu' [maxKeyHold] caratteri, oltre i
   *   quali la parola esce come [Hidden];
   * - [drain] con `complete = false` (la risposta fermata, o tagliata dal tetto) tratta l'ultima parola come una chiave
   *   a meta': se comincia come una chiave, esce [Hidden].
   */
  internal class Stream(private val maxHold: Int = 256, private val maxKeyHold: Int = 4_096) {
    private val held = StringBuilder()

    /** Vero dopo una chiave troppo lunga per tenerla: il resto della sua parola non esce. */
    private var swallowing = false

    fun push(delta: String): String {
      if (delta.isEmpty()) return ""
      var piece = delta
      if (swallowing) {
        val end = piece.indexOfFirst { it.isWhitespace() }
        if (end < 0) return ""
        swallowing = false
        piece = piece.substring(end)
      }
      held.append(piece)
      val cut = safeCut()
      if (cut > 0) return emit(cut)
      if (held.length <= maxHold) return ""
      // La parola e' troppo lunga per aspettarla tutta: esce fino a dove puo' cominciare una chiave.
      val marker = firstMarker(held)
      return when {
        marker < 0 -> emit((held.length - (LongestMarker - 1)).coerceAtLeast(0))
        marker > 0 -> emit(marker)
        held.length <= maxKeyHold -> ""
        else -> {
          held.setLength(0)
          swallowing = true
          Hidden
        }
      }
    }

    fun drain(complete: Boolean = true): String {
      swallowing = false
      val text = held.toString()
      held.setLength(0)
      if (complete) return scrub(text)
      val marker = firstMarker(text)
      return if (marker < 0) scrub(text) else scrub(text.substring(0, marker)) + Hidden
    }

    /** Quanti caratteri aspettano la fine della parola. */
    val pending: Int get() = held.length

    private fun emit(cut: Int): String {
      val ready = held.substring(0, cut)
      held.delete(0, cut)
      return scrub(ready)
    }

    /**
     * Fin dove si puo' mandare: fino all'ultimo spazio, ma senza un prefisso che aspetta il suo valore. Il prefisso
     * resta indietro dall'inizio della sua parola (un pezzo di parola che esce da solo puo' essere mezza chiave).
     */
    private fun safeCut(): Int {
      var cut = lastBreak(held) + 1
      while (cut > 0) {
        val m = Context.find(held.subSequence(0, cut)) ?: break
        var start = m.range.first
        while (start > 0 && !held[start - 1].isWhitespace()) start--
        if (start >= cut) break
        cut = start
      }
      return cut
    }

    private fun lastBreak(text: CharSequence): Int {
      for (i in text.length - 1 downTo 0) if (text[i].isWhitespace()) return i
      return -1
    }
  }
}
