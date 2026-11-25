package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

// F8.18 · Riprendi dallo stesso punto (`riprendi-dallo-stesso-punto`). Un file riaperto torna dove l'utente era: la pagina
// e il punto della pagina di un documento, lo zoom, la riga di un testo, il secondo di un audio o di un video. Come i
// Libri e l'Anteprima di Apple, che riaprono in silenzio all'ultima pagina letta, e come Podcast e TV, che riprendono
// dal secondo lasciato (riferimento: la posizione che Handoff riporta su un altro dispositivo, `NSUserActivity`;
// fiducia media, nessuna misura pubblica). Il telaio sa solo leggere e scrivere la posizione: dove si salva lo decide
// chi la dà (di serie `FluidViewerResumeStore` di engine-viewer, salvato in modo leggero e cancellato quando il file
// non c'è più).

/**
 * Dove l'utente era in un file. I campi che un preset non usa restano al valore di serie.
 *
 * - [page]: la pagina di un documento a pagine (da zero), la riga di un testo (da zero).
 * - [offset]: quanto di quella pagina era già passato sopra il bordo in alto, da 0 a 1. Una frazione e non i pixel, così
 *   la posizione vale anche con un'altra larghezza (il telefono girato, un altro dispositivo).
 * - [zoom]: lo zoom della colonna delle pagine (1 = adatta alla larghezza).
 * - [mediaMillis]: il punto di un audio o di un video, in millisecondi; null se il file non suona.
 *
 * [encode] e [decode] la scrivono in una riga corta (`p=41;o=0.25;z=1.5;m=93000`), per chi la porta su un altro
 * dispositivo: è il formato del negozio di serie, e un campo nuovo in coda non rompe le righe di prima.
 */
@Immutable
data class FluidViewerPosition(
  val page: Int = 0,
  val offset: Float = 0f,
  val zoom: Float = 1f,
  val mediaMillis: Long? = null,
) {
  /** Vero se non c'è niente da riprendere: la prima pagina dall'alto, senza zoom, senza un secondo. */
  val isStart: Boolean get() = page <= 0 && offset <= 0f && zoom == 1f && (mediaMillis ?: 0L) <= 0L

  /** La riga corta del formato: `p=…;o=…;z=…`, e `m=…` solo per i file che suonano. */
  fun encode(): String = buildString {
    append("p=").append(page.coerceAtLeast(0))
    append(";o=").append(String.format(Locale.ROOT, "%.4f", offset.coerceIn(0f, 1f)))
    append(";z=").append(String.format(Locale.ROOT, "%.3f", zoom))
    mediaMillis?.let { append(";m=").append(it.coerceAtLeast(0L)) }
  }

  companion object {
    /** La posizione scritta da [encode]; null se la riga non ne è una. I campi sconosciuti si ignorano. */
    fun decode(text: String?): FluidViewerPosition? {
      if (text.isNullOrBlank()) return null
      var page = 0
      var offset = 0f
      var zoom = 1f
      var media: Long? = null
      var known = false
      for (part in text.split(';')) {
        val name = part.substringBefore('=', "").trim()
        val value = part.substringAfter('=', "").trim()
        when (name) {
          "p" -> value.toIntOrNull()?.let { page = it.coerceAtLeast(0); known = true }
          "o" -> value.toFloatOrNull()?.takeIf { it.isFinite() }?.let { offset = it.coerceIn(0f, 1f); known = true }
          "z" -> value.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }?.let { zoom = it; known = true }
          "m" -> value.toLongOrNull()?.let { media = it.coerceAtLeast(0L); known = true }
        }
      }
      return if (known) FluidViewerPosition(page, offset, zoom, media) else null
    }
  }
}

/**
 * Il posto di un file per la sua posizione, che il telaio dà al preset della pagina.
 *
 * - [position] è la posizione salvata per **questa** revisione del file: il preset parte da lì, senza animazione e
 *   senza un fotogramma sulla prima pagina (il viaggio d'oro arriva già sul punto giusto).
 * - [stale] è una posizione salvata quando il file era diverso (cambiati peso o data): il preset non la applica da sé e
 *   la offre con una capsula («Riprendi da pagina 42»), come nella scheda dell'idea. [dismissStale] la toglie, anche
 *   quando l'utente la usa.
 * - [save] riceve la posizione a riposo (a fine scorrimento, alla pausa, uscendo dalla pagina): chi la custodisce la
 *   tiene in memoria e la scrive su disco più tardi, tutta insieme. Non va chiamata a ogni fotogramma.
 */
@Stable
interface FluidViewerResumeSlot {
  val position: FluidViewerPosition?
  val stale: FluidViewerPosition? get() = null
  fun save(position: FluidViewerPosition)
  fun dismissStale() {}
}

/**
 * Dà a [FluidFileViewer] il posto della posizione di ogni pagina, come [FluidViewerInfoProvider] dà il pannello. Il
 * telaio lo chiede una volta per pagina composta; null per una pagina che non si riprende. Di serie, con
 * engine-viewer: `FluidViewerResumeProvider(rememberFluidViewerResume(files)) { FluidFileViewer(…) }`.
 */
@Composable
fun FluidViewerResumeProvider(resume: (index: Int) -> FluidViewerResumeSlot?, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalViewerResumeProvider provides resume, content = content)
}

/**
 * Il posto della posizione della pagina che si sta disegnando, per un preset dell'app ([FluidViewerContent.Custom], un
 * suo visore a tasselli): null fuori dal telaio o senza [FluidViewerResumeProvider].
 */
@Composable
fun currentFluidViewerResume(): FluidViewerResumeSlot? = LocalViewerResumeSlot.current

internal val LocalViewerResumeProvider = compositionLocalOf<((Int) -> FluidViewerResumeSlot?)?> { null }
internal val LocalViewerResumeSlot = compositionLocalOf<FluidViewerResumeSlot?> { null }

/** Le parole della capsula «Riprendi da…» (F8.18). I numeri sono già da uno. */
@Immutable
data class FluidViewerResumeStrings(
  val page: (page: Int) -> String = { "Riprendi da pagina $it" },
  val line: (line: Int) -> String = { "Riprendi dalla riga $it" },
  val time: (clock: String) -> String = { "Riprendi da $it" },
  val changed: String = "Il file è cambiato dall'ultima lettura",
  val dismiss: String = "Ignora",
)

/** Un audio o un video si riprende solo lontano dai bordi: i primi 5 s e gli ultimi 5 s ripartono dall'inizio. */
internal object ViewerResumeMedia {
  /**
   * 5 s, a occhio (25 novembre 2026): Podcast riprende dal punto anche dopo pochi secondi, ma un brano lasciato a fine
   * corsa riparte da capo (come la riproduzione di Musica dopo la fine).
   */
  const val EdgeMillis: Long = 5_000L

  fun resumable(position: Long?, duration: Long?): Boolean {
    if (position == null || position < EdgeMillis) return false
    return duration == null || position < duration - EdgeMillis
  }
}
