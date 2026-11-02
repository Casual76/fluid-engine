package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Come l'assistente e' in scena: nascosto, in ascolto (aureola grande), in modalita' testo
 * (barra + card), con la card ridotta a pillola perche' l'utente ha ripreso a usare la pagina.
 */
enum class FluidOverlayMode { HIDDEN, VOICE, TEXT }

/**
 * Lo stato dell'overlay tenuto fuori dalla composizione della pagina, cosi' sopravvive a ogni
 * ricomposizione e la pagina lo tocca solo con due callback (tocco e pressione lunga sul tasto).
 *
 * E' quello di ClasseViva, che di due fork identici era il piu' completo: `suppressed` (una schermata
 * che mostra la conversazione per intero rende l'overlay un doppione) e `onPageInteraction` (la
 * pagina e' stata toccata: la card si fa da parte).
 */
class FluidAssistantOverlayState {
  var mode by mutableStateOf(FluidOverlayMode.HIDDEN)
  var collapsed by mutableStateOf(false)

  /**
   * Vero mentre la barra di scrittura e' aperta. Dopo l'invio si chiude e resta il pensiero
   * dell'assistente: chi ha appena scritto la domanda non ha piu' niente da scrivere, e la
   * tastiera aperta sopra la risposta era solo un ingombro.
   */
  var composing by mutableStateOf(false)
    private set

  /**
   * Cresce a ogni tocco del tasto ed e' un **gettone**: chi lo consuma fa partire l'ascolto, e
   * nessun altro lo rifa'. Un contatore letto da un effetto bastava a far partire un secondo
   * `AudioRecord` sullo stesso microfono a ogni ricomposizione.
   */
  var voiceRequest by mutableIntStateOf(0)
    private set

  private var consumedRequest by mutableIntStateOf(0)

  /** Vero se aprendo la barra si vuole anche la tastiera: si' quando l'ha chiesta l'utente. */
  var autoFocus by mutableStateOf(true)
    private set

  /**
   * Il rettangolo del tasto dell'assistente sopra la pillola. E' l'origine della trasformazione:
   * la card non compare, **cresce da li'** — e tornando indietro ci rientra.
   */
  var originBounds by mutableStateOf<Rect?>(null)

  /** Vero mentre una schermata mostra la conversazione per intero: l'overlay sopra sarebbe un doppione. */
  var suppressed by mutableStateOf(false)

  fun openVoice() {
    mode = FluidOverlayMode.VOICE
    collapsed = false
    composing = false
    voiceRequest++
  }

  /** Consuma il gettone: vero solo per il primo che chiama, e una volta sola per tocco. */
  fun consumeVoiceRequest(): Boolean {
    if (voiceRequest == 0 || voiceRequest == consumedRequest) return false
    consumedRequest = voiceRequest
    return true
  }

  fun openText() {
    mode = FluidOverlayMode.TEXT
    collapsed = false
    composing = true
    autoFocus = true
  }

  /** La domanda e' partita: via la barra, resta la risposta che si sta formando. */
  fun sent() {
    composing = false
  }

  /**
   * La risposta e' arrivata: la barra torna, per la domanda dopo. Senza tastiera in faccia, pero':
   * la si apre solo se la si tocca. Riaprire con il fuoco significava coprire con la tastiera la
   * risposta che si e' appena aspettata.
   */
  fun resumeComposing() {
    if (mode != FluidOverlayMode.TEXT) return
    composing = true
    autoFocus = false
  }

  fun hide() {
    mode = FluidOverlayMode.HIDDEN
    collapsed = false
  }

  /** La pagina e' stata toccata o scorsa: la card si fa da parte senza fermare niente. */
  fun onPageInteraction() {
    if (mode != FluidOverlayMode.HIDDEN) collapsed = true
  }

  /**
   * Un tool ha aperto una pagina dell'app (o un tocco su un chip lo ha fatto): la card si fa da
   * parte, la pagina cambia. La navigazione e' dell'app; questo e' solo il mettersi da parte.
   */
  fun collapseForNavigation() {
    collapsed = true
  }
}
