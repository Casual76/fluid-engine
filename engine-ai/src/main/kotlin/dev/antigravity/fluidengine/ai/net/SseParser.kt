package dev.antigravity.fluidengine.ai.net

/**
 * Un parser di Server-Sent Events riga per riga, come dice la specifica: le righe `data:` di un
 * evento si accumulano, una riga vuota lo chiude, i commenti (`: OPENROUTER PROCESSING`, i
 * keep-alive di Groq) non ci interessano. Una istanza per stream; [flush] recupera un ultimo evento
 * non terminato a fine connessione.
 *
 * Dalla 4.0 (F9.2) il parser ricorda anche `id:` ed `event:` dell'ultimo evento completo
 * ([lastEventId], [lastEventName]): i server MCP numerano gli eventi, e un flusso interrotto si
 * riprende con l'intestazione `Last-Event-ID`. I provider IA non li mandano, e per loro non cambia niente.
 */
class SseParser {

  private val buffer = mutableListOf<String>()
  private var pendingName: String? = null

  /**
   * L'ultimo `id:` letto: come dice la specifica vale subito, anche per un evento senza dati (il
   * "priming" dei server MCP: un id e un `data:` vuoto), e resta finche' un altro id non lo sostituisce.
   */
  var lastEventId: String? = null
    private set

  /** L'`event:` dell'ultimo evento completo, o null se non ne aveva (vale "message"). */
  var lastEventName: String? = null
    private set

  /** Ritorna il payload di un evento appena completo, altrimenti null. */
  fun feed(rawLine: String): String? {
    val line = rawLine.removeSuffix("\r")
    if (line.isEmpty()) return flush()
    if (line.startsWith(":")) return null
    val colon = line.indexOf(':')
    val field = if (colon >= 0) line.substring(0, colon) else line
    var value = if (colon >= 0) line.substring(colon + 1) else ""
    if (value.startsWith(" ")) value = value.substring(1)
    when (field) {
      "data" -> buffer += value
      // Un id con il carattere nullo si ignora (specifica HTML, "id").
      "id" -> if ('\u0000' !in value) lastEventId = value
      "event" -> pendingName = value
    }
    return null
  }

  fun flush(): String? {
    if (buffer.isEmpty()) {
      pendingName = null
      return null
    }
    val payload = buffer.joinToString("\n")
    buffer.clear()
    lastEventName = pendingName
    pendingName = null
    return payload
  }
}
