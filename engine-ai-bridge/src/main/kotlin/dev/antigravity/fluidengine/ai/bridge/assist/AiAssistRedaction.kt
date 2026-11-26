package dev.antigravity.fluidengine.ai.bridge.assist

/**
 * La pulizia del testo che esce da PampAI verso l'app che chiama: cio' che somiglia a una chiave diventa
 * [Hidden]. E' difesa in profondita': il formato non ha campi per chiavi o modelli e gli errori passano come codici, ma
 * un modello o un tool potrebbero scrivere una chiave nel testo (un errore copiato, un file di configurazione letto).
 *
 * Le forme riconosciute: Groq (`gsk_…`), Google (`AIza…`), OpenRouter (`sk-or-…`), OpenAI e simili (`sk-…`), un
 * `Bearer …`, un JWT, e `api_key=…` / `x-goog-api-key: …` / `Authorization: …`.
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
    Regex("(?i)(?:api[_-]?key|x-goog-api-key|authorization)\\s*[:=]\\s*\\S{8,}"),
  )

  /** [text] senza niente che somigli a una chiave. */
  fun scrub(text: String): String {
    if (text.length < 12) return text
    var out = text
    for (pattern in Patterns) out = pattern.replace(out, Hidden)
    return out
  }

  /**
   * Il testo che arriva a pezzi, ripulito a **parole intere**: una chiave non si spezza mai fra due invii, quindi la
   * pulizia la vede sempre tutta. [push] restituisce cio' che si puo' mandare adesso (fino all'ultimo spazio) e tiene
   * il resto; [drain] restituisce tutto. Una «parola» piu' lunga di [maxHold] caratteri (un indirizzo, un blocco di
   * codice) esce lo stesso, ripulita.
   */
  internal class Stream(private val maxHold: Int = 256) {
    private val held = StringBuilder()

    fun push(delta: String): String {
      if (delta.isEmpty()) return ""
      held.append(delta)
      val cut = lastBreak(held)
      if (cut < 0) {
        if (held.length <= maxHold) return ""
        val out = scrub(held.toString())
        held.setLength(0)
        return out
      }
      val ready = held.substring(0, cut + 1)
      held.delete(0, cut + 1)
      return scrub(ready)
    }

    fun drain(): String {
      val out = scrub(held.toString())
      held.setLength(0)
      return out
    }

    /** Quanti caratteri aspettano la fine della parola. */
    val pending: Int get() = held.length

    private fun lastBreak(text: CharSequence): Int {
      for (i in text.length - 1 downTo 0) if (text[i].isWhitespace()) return i
      return -1
    }
  }
}
