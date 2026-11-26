package dev.antigravity.fluidengine.ai.bridge.assist

/**
 * Le parole degli errori dell'assistenza, in italiano e in inglese. Sono dell'engine e non di PampAI: l'ospite manda
 * solo il codice ([AiAssistError.code]), cosi' nessun testo di un fornitore (che potrebbe portare una chiave) arriva
 * all'app. Un'app che vuole parole sue le scrive a partire dal codice.
 */
object AiAssistTexts {

  /** Le parole per [error] nella lingua [language] (`it` di serie; `en` per l'inglese, ogni altra lingua cade sull'italiano). */
  fun message(error: AiAssistError, language: String = "it"): String {
    val en = language.lowercase().startsWith("en")
    return when (error.code) {
      AiAssistError.NotInstalled -> if (en) "PampAI isn't installed" else "PampAI non c'è"
      AiAssistError.NeedsUpdate -> if (en) "PampAI needs an update" else "PampAI va aggiornata"
      AiAssistError.NotAuthorized -> if (en) "PampAI hasn't authorized this app" else "PampAI non ha autorizzato quest'app"
      AiAssistError.ConsentRequired -> when {
        AiAssistError.NeedsApp in error.needs -> if (en) "Using PampAI from this app needs your OK" else "Per usare PampAI da quest'app serve il tuo sì"
        else -> if (en) "Sending this page needs your OK in PampAI" else "Per mandare la pagina serve il tuo sì in PampAI"
      }
      AiAssistError.NotReady -> error.reason?.takeIf { it.isNotBlank() }?.let { if (en) "PampAI isn't ready: $it" else "PampAI non è pronta: $it" }
        ?: if (en) "PampAI isn't ready: open it and add a key" else "PampAI non è pronta: aprila e aggiungi una chiave"
      AiAssistError.LimitReached -> if (en) "Limit reached: try again ${retry(error.retryAfterMillis, true)}" else "Limite raggiunto: riprova ${retry(error.retryAfterMillis, false)}"
      AiAssistError.Busy -> if (en) "PampAI is busy: try again ${retry(error.retryAfterMillis, true)}" else "PampAI è occupata: riprova ${retry(error.retryAfterMillis, false)}"
      AiAssistError.TooLarge -> if (en) "The text is too long for PampAI" else "Il testo è troppo lungo per PampAI"
      AiAssistError.Cancelled -> if (en) "Cancelled" else "Annullata"
      AiAssistError.Timeout -> if (en) "PampAI didn't answer in time" else "PampAI non ha risposto in tempo"
      AiAssistError.Interrupted -> if (en) "PampAI stopped" else "PampAI si è fermata"
      else -> if (en) "PampAI couldn't answer" else "PampAI non è riuscita a rispondere"
    }
  }

  /** L'etichetta di un'azione del tasto spento ([AiAssistEntry.action]). */
  fun action(id: String, language: String = "it"): String {
    val en = language.lowercase().startsWith("en")
    return when (id) {
      AiAssistEntry.ActionInstall -> if (en) "Install PampAI" else "Installa PampAI"
      AiAssistEntry.ActionUpdate -> if (en) "Update PampAI" else "Aggiorna PampAI"
      else -> if (en) "Open PampAI" else "Apri PampAI"
    }
  }

  /**
   * La spiegazione breve quando PampAI non c'e' e nemmeno Pampa Store (F9.1b): `AiAssistClient.installIntent()` e' null.
   */
  fun installHint(language: String = "it"): String =
    if (language.lowercase().startsWith("en")) "PampAI is the assistant of the Pampa apps: it installs from Pampa Store, which isn't on this phone. Install Pampa Store first, then PampAI from it."
    else "PampAI è l'assistente delle app Pampa: si installa da Pampa Store, che su questo telefono non c'è. Installa prima Pampa Store, poi PampAI da lì."

  /** «fra 2 minuti», «fra 30 secondi», «fra poco». */
  internal fun retry(millis: Long?, en: Boolean): String {
    val ms = millis?.takeIf { it > 0 } ?: return if (en) "shortly" else "fra poco"
    val seconds = ((ms + 999) / 1000).toInt()
    return when {
      seconds < 60 -> if (en) "in $seconds ${if (seconds == 1) "second" else "seconds"}" else "fra $seconds ${if (seconds == 1) "secondo" else "secondi"}"
      else -> {
        val minutes = (seconds + 59) / 60
        if (en) "in $minutes ${if (minutes == 1) "minute" else "minutes"}" else "fra $minutes ${if (minutes == 1) "minuto" else "minuti"}"
      }
    }
  }
}
