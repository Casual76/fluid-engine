package dev.antigravity.fluidengine.aiui.overlay

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.antigravity.fluidengine.aiui.markdown.FluidMarkdownParser
import java.util.Locale

/**
 * Chi legge la risposta ad alta voce. Un'interfaccia perche' la voce di sistema ([FluidTtsSpeaker])
 * e' solo la prima: PampAI ha anche voci cloud (Gemini, Groq) con ripiego sul telefono, e un'app
 * che le ha le implementa qui e le passa all'overlay. L'overlay non sa che voce sia: sa quando
 * ricominciare, quando leggere il pezzo nuovo, quando zittirsi.
 */
interface FluidSpeaker {
  /** Le frasi nuove di [fullText] rispetto all'ultima volta, accodate. Si chiama con lo stream parziale. */
  fun speakNewSentences(fullText: String, final: Boolean)

  /** Una domanda nuova: si azzera cio' che era stato letto. */
  fun restart()

  fun stop()

  fun release()
}

/**
 * La voce di sistema (gratis, offline). Legge le frasi man mano che lo stream le chiude,
 * accodandole; un tocco sulla card o una domanda nuova la zittisce. Spenta di default: e' un
 * interruttore nelle impostazioni dell'app, e vale solo per le domande fatte a voce.
 *
 * @param locales le lingue da provare, in ordine: la prima che il telefono sa parlare vince. Le
 *   due app che lo avevano scritto divergevano qui — ClasseViva voleva l'italiano e ripiegava sulla
 *   lingua di sistema, FluidWeather il contrario con l'inglese in fondo — ed e' un parametro e non
 *   una scelta dell'engine perche' e' la lingua dell'app, non la sua.
 */
class FluidTtsSpeaker(
  context: Context,
  private val locales: List<Locale> = listOf(Locale.getDefault(), Locale.ENGLISH),
) : FluidSpeaker {

  private var ready = false
  private var pending = mutableListOf<String>()
  private var spokenChars = 0
  private var utterance = 0
  private val engine = TextToSpeech(context.applicationContext) { status ->
    ready = status == TextToSpeech.SUCCESS
    if (ready) {
      engineLanguage()
      val queued = pending.toList()
      pending.clear()
      queued.forEach { speak(it) }
    }
  }

  private fun engineLanguage() {
    for (locale in locales) {
      val result = engine.setLanguage(locale)
      if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) return
    }
  }

  override fun speakNewSentences(fullText: String, final: Boolean) {
    val plain = FluidMarkdownParser.plainText(fullText)
    val next = FluidSentences.next(plain, spokenChars, final)
    spokenChars = next.nextOffset
    next.sentences.forEach { speak(it) }
  }

  override fun restart() {
    stop()
    spokenChars = 0
  }

  private fun speak(text: String) {
    if (!ready) {
      pending += text
      return
    }
    engine.speak(text, TextToSpeech.QUEUE_ADD, null, "ai-${utterance++}")
  }

  override fun stop() {
    pending.clear()
    runCatching { engine.stop() }
  }

  override fun release() {
    stop()
    runCatching { engine.shutdown() }
  }
}

/**
 * Quali frasi leggere di un testo che cresce. Pura, perche' e' la parte che sbaglia (una frase
 * letta due volte, una tagliata a meta') e quella che si prova sul computer.
 */
object FluidSentences {
  class Next(val sentences: List<String>, val nextOffset: Int)

  /** Fine di frase: punto, punto esclamativo, interrogativo o a capo, seguiti da spazio o fine. */
  private val SentenceEnd = Regex("[.!?…]+(?=\\s|$)|\\n")

  /**
   * Le frasi chiuse in [plain] dopo [from] caratteri gia' letti. A [final] anche l'ultimo pezzo,
   * chiuso o no; altrimenti resta da parte finche' lo stream non lo chiude. [Next.nextOffset] e' da
   * dove ripartire la volta dopo.
   */
  fun next(plain: String, from: Int, final: Boolean): Next {
    if (plain.length <= from) return Next(emptyList(), from)
    val tail = plain.substring(from)
    val sentences = mutableListOf<String>()
    var start = 0
    SentenceEnd.findAll(tail).forEach { match ->
      val end = match.range.last + 1
      sentences += tail.substring(start, end).trim()
      start = end
    }
    if (final && start < tail.length) sentences += tail.substring(start).trim()
    val consumed = if (final) tail.length else start
    return Next(sentences.filter { it.isNotBlank() }, from + consumed)
  }
}

/**
 * Una voce di sistema legata alla composizione: nasce quando serve e si rilascia quando la schermata
 * se ne va. Non crea il motore finche' [enabled] e' falso: un `TextToSpeech` costa un servizio.
 */
@Composable
fun rememberFluidTtsSpeaker(
  enabled: Boolean = true,
  locales: List<Locale> = listOf(Locale.getDefault(), Locale.ENGLISH),
): FluidSpeaker? {
  val context = LocalContext.current
  val speaker = remember(enabled, locales) { if (enabled) FluidTtsSpeaker(context, locales) else null }
  DisposableEffect(speaker) { onDispose { speaker?.release() } }
  return speaker
}
