package dev.antigravity.fluidengine.ai.speech

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReadyProvider
import dev.antigravity.fluidengine.ai.provider.TranscribeOptions
import java.io.File
import kotlinx.coroutines.CancellationException

/** Chi ha trascritto e cosa. */
data class Transcription(val text: String, val provider: ProviderId, val model: String)

/**
 * La trascrizione segue l'ordine dei provider scelto per la voce: il primo con chiave prova, i
 * successivi sono riserva su 429, 5xx e rete. La lingua e' quella dell'app (it/en), forzata:
 * piu' precisa dell'auto-rilevamento sulle frasi corte.
 */
class Transcriber(private val providers: suspend () -> List<ReadyProvider>) {

  suspend fun transcribe(audio: File, language: String, hint: String?): Transcription {
    val ordered = providers()
    if (ordered.isEmpty()) throw AiError.Unauthorized("nessun provider per la trascrizione")
    var last: Throwable? = null
    for (ready in ordered) {
      try {
        val transcript = ready.provider.transcribe(audio, "audio/wav", TranscribeOptions(ready.sttModel, language, hint))
        return Transcription(transcript.text.trim(), ready.provider.id, ready.sttModel)
      } catch (e: CancellationException) {
        throw e
      } catch (e: AiError.Unauthorized) {
        throw e
      } catch (e: Throwable) {
        last = e
      }
    }
    throw last ?: AiError.Network("trascrizione fallita")
  }

  companion object {
    /** Il prompt di Whisper vale al massimo 224 token: 800 caratteri ci stanno. */
    const val HINT_MAX_CHARS = 800

    /**
     * Il suggerimento di vocabolario per Whisper: una frase sul dominio dell'app ("Domande sul
     * registro elettronico: voti, compiti, ...") e i nomi propri che l'utente potrebbe dire.
     */
    fun hint(domainHint: String, vocabulary: List<String>): String {
      val words = vocabulary.filter { it.isNotBlank() }.take(20).joinToString(", ")
      return (if (words.isEmpty()) domainHint else "$domainHint $words.").take(HINT_MAX_CHARS)
    }
  }
}
