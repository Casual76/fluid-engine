package dev.antigravity.fluidengine.transcript

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidScrubbing
import dev.antigravity.fluidengine.ui.fluid.FluidSpokenText
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import kotlin.math.abs

/** I testi della vista, in italiano di serie. */
@Immutable
data class FluidTranscriptStrings(
  /** Il tasto che riporta la pagina alla voce dopo aver letto altrove. */
  val returnToVoice: String = "Torna alla voce",
  /** L'azione di TalkBack su un paragrafo. */
  val listenFromHere: String = "Ascolta da qui",
  /** Lo stato a voce del paragrafo che si sente. */
  val nowPlaying: String = "In ascolto",
  /**
   * La frase di una trascrizione senza paragrafi (non ancora pronta, o assente), sotto l'intestazione dell'app (R1, 26
   * novembre 2026). Vuota: niente frase.
   */
  val empty: String = "Nessuna trascrizione",
)

/**
 * Lo stato di una [FluidTranscriptView]: la lista e se la pagina **segue la voce**. Segue di serie; smette appena il dito
 * scorre la lista (si sta leggendo altrove: la pagina non si strappa di mano), e riprende con un tocco sul testo, con
 * «Torna alla voce» o con [follow].
 */
@Stable
class FluidTranscriptState(val list: LazyListState) {
  /** Vero se la pagina tiene in vista la parola che si sente. */
  var following: Boolean by mutableStateOf(true)
    internal set

  /** Torna a seguire la voce: la pagina ci va. */
  fun follow() {
    following = true
    request++
  }

  /** Smette di seguire (come fa da sé quando il dito scorre). */
  fun stopFollowing() {
    following = false
  }

  /** Cresce a ogni [follow]: la vista ci va subito, anche se la riga non è cambiata. */
  internal var request by mutableStateOf(0)

  internal val layouts = HashMap<Int, TextLayoutResult>()
}

/** Lo stato per questa composizione. */
@Composable
fun rememberFluidTranscriptState(list: LazyListState = rememberLazyListState()): FluidTranscriptState =
  remember(list) { FluidTranscriptState(list) }

/** Le misure della vista. */
object FluidTranscriptDefaults {
  /**
   * La fascia in cui la riga che si sente può stare senza che la pagina si muova: dal 18% al 62% dell'altezza. Uscita
   * dalla fascia, la pagina la riporta al [RestFraction]. È quello che tiene ferma la pagina mentre si legge: si muove
   * una volta ogni qualche riga, non a ogni parola. A occhio, 25 novembre 2026.
   */
  const val BandTop: Float = 0.18f
  const val BandBottom: Float = 0.62f
  const val RestFraction: Float = 0.28f
}

/**
 * La **trascrizione che si ascolta** (F8.13, idea `ascolto-dal-paragrafo`): i paragrafi di [transcript] in una lista
 * pigra (si compongono solo quelli in vista, anche in una lezione di due ore), ognuno con la sua ora; il paragrafo che si
 * sente ha l'ora nell'accento e **la parola che si sente accesa** nel testo (`FluidSpokenText`: il detto pieno, il resto
 * velato); gli altri sono testo pieno, da leggere.
 *
 * **Il tocco** ([FluidTranscriptSeek]): su una parola salta **a quella parola**, sull'ora al suo **paragrafo**; con le
 * sole frasi, alla frase; senza tempi, niente. Toccare riporta anche la pagina a seguire la voce. È l'ascolto dal
 * paragrafo delle trascrizioni di Podcast su iPhone («tocca un paragrafo qualsiasi per andare a quel punto della puntata»,
 * guida dell'iPhone, iOS 26), con le parole di Memo Vocali (la parola che si sente evidenziata).
 *
 * **Seguire senza far saltare la pagina.** Mentre suona, la riga della parola che si sente può muoversi in una fascia
 * ([FluidTranscriptDefaults]); quando ne esce, la pagina scorre una volta, morbida, e la riporta in alto. Appena il dito
 * scorre la lista la pagina smette di seguire, e in fondo compare «Torna alla voce» (vetro). Con le animazioni ridotte
 * la pagina si posa invece di scorrere. Niente sfocatura: è testo da leggere.
 *
 * **TalkBack**: ogni paragrafo è un nodo con l'ora e il testo, e l'azione «Ascolta da qui»; quello che si sente dice «In
 * ascolto».
 *
 * **Vuota** (nessun paragrafo: la trascrizione non è ancora pronta, o non c'è): sotto l'intestazione la frase
 * [FluidTranscriptStrings.empty], «Nessuna trascrizione», invece di una pagina bianca.
 *
 * @param position dove è l'ascolto, in millisecondi: una lambda letta fuori dalla composizione.
 * @param onSeek il salto chiesto da un tocco, in millisecondi.
 * @param playing vero mentre suona: solo allora la pagina segue e compare «Torna alla voce».
 * @param header righe prima dei paragrafi (il titolo, il lettore).
 */
@Composable
fun FluidTranscriptView(
  transcript: FluidTranscript,
  position: () -> Long,
  onSeek: (Long) -> Unit,
  modifier: Modifier = Modifier,
  playing: Boolean = false,
  state: FluidTranscriptState = rememberFluidTranscriptState(),
  contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  strings: FluidTranscriptStrings = FluidTranscriptStrings(),
  header: (LazyListScope.() -> Unit)? = null,
) {
  val latestPosition by rememberUpdatedState(position)
  val latestSeek by rememberUpdatedState(onSeek)
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // Il paragrafo che si sente: uno stato intero, scritto solo quando cambia (la composizione si rifa' allora, non a ogni
  // battito del lettore).
  var active by remember(transcript) {
    mutableIntStateOf(Snapshot.withoutReadObservation { transcript.paragraphAt(position()) })
  }

  // Un tocco sul testo salta e torna a seguire, ma non sposta la pagina: la riga toccata e' gia' sotto il dito.
  fun seek(target: Long?) {
    if (target == null) return
    latestSeek(target)
    state.following = true
  }

  // Il dito che scorre la lista: la pagina smette di seguire.
  LaunchedEffect(state) {
    state.list.interactionSource.interactions.collect { interaction ->
      if (interaction is DragInteraction.Start) state.stopFollowing()
    }
  }

  // Il battito: mentre suona, a ogni fotogramma (senza allocare) si guarda quale paragrafo e quale parola si sentono; se
  // la riga esce dalla fascia, una scrollata sola. Fermo, si guarda solo quando la posizione cambia (un salto).
  LaunchedEffect(state, transcript, playing, reducedMotion) {
    var lastParagraph = Int.MIN_VALUE
    var lastOffset = Int.MIN_VALUE
    // Vero se la riga da tenere in vista e' cambiata dall'ultima volta. Non sospende: nel ciclo dei fotogrammi non nasce
    // niente, la scrollata (che sospende) parte solo quando serve.
    fun changed(at: Long): Boolean {
      val paragraph = transcript.paragraphAt(at)
      if (paragraph != active) active = paragraph
      if (paragraph < 0) return false
      val offset = transcript.spokenOffset(paragraph, at)
      if (paragraph == lastParagraph && offset == lastOffset) return false
      lastParagraph = paragraph
      lastOffset = offset
      return true
    }
    if (playing) {
      while (true) {
        withFrameNanos { }
        if (changed(latestPosition()) && state.following) {
          keepInView(state, transcript.paragraphs.size, lastParagraph, lastOffset, reducedMotion, force = false)
        }
      }
    } else {
      snapshotFlow { latestPosition() }.collect { changed(it) }
    }
  }

  // «Torna alla voce», un tocco sul testo, `follow()`: la pagina ci va anche se la riga non è cambiata.
  LaunchedEffect(state, transcript, reducedMotion) {
    snapshotFlow { state.request }.collect { request ->
      if (request == 0) return@collect
      val at = latestPosition()
      val paragraph = transcript.paragraphAt(at)
      if (paragraph >= 0) {
        keepInView(state, transcript.paragraphs.size, paragraph, transcript.spokenOffset(paragraph, at), reducedMotion, force = true)
      }
      state.request = 0
    }
  }

  Box(modifier) {
    LazyColumn(state = state.list, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
      header?.invoke(this)
      if (transcript.paragraphs.isEmpty() && strings.empty.isNotEmpty()) {
        // Una trascrizione vuota lo dice, invece di lasciare la pagina bianca (R1).
        item(key = "fluid-transcript-empty") {
          Text(
            text = strings.empty,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
          )
        }
      }
      itemsIndexed(transcript.paragraphs, key = { index, paragraph -> transcriptKey(paragraph.key ?: index) }) { index, paragraph ->
        val isActive = index == active
        TranscriptParagraph(
          paragraph = paragraph,
          active = isActive,
          position = { if (isActive) latestPosition() else 0L },
          onSeek = ::seek,
          onLayout = { state.layouts[index] = it },
          strings = strings,
        )
      }
    }
    if (playing && !state.following && transcript.hasTimes) {
      FluidGlassButton(
        text = strings.returnToVoice,
        onClick = { state.follow() },
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
      )
    }
  }
}

@Composable
private fun TranscriptParagraph(
  paragraph: FluidTranscriptParagraph,
  active: Boolean,
  position: () -> Long,
  onSeek: (Long?) -> Unit,
  onLayout: (TextLayoutResult) -> Unit,
  strings: FluidTranscriptStrings,
) {
  val scheme = MaterialTheme.colorScheme
  var layout by remember(paragraph) { mutableStateOf<TextLayoutResult?>(null) }
  val start = paragraph.start
  val timed = paragraph.hasTimes
  Column(
    Modifier
      .fillMaxWidth()
      .padding(vertical = 10.dp)
      .semantics(mergeDescendants = true) {
        if (timed) onClick(label = strings.listenFromHere) { onSeek(FluidTranscriptSeek.paragraphStart(paragraph)); true }
        if (active) stateDescription = strings.nowPlaying
      },
  ) {
    if (start != null || paragraph.speaker != null) {
      val label = listOfNotNull(paragraph.speaker, start?.let(FluidScrubbing::clock)).joinToString(" · ")
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
        color = if (active) scheme.primary else scheme.onSurfaceVariant,
        modifier = Modifier
          .padding(bottom = 4.dp)
          .then(if (timed) Modifier.fluidPressable(onClick = { onSeek(FluidTranscriptSeek.paragraphStart(paragraph)) }) else Modifier)
          .padding(vertical = 2.dp),
      )
    }
    FluidSpokenText(
      text = paragraph.text,
      words = if (active) paragraph.words else emptyList(),
      positionMs = position,
      style = MaterialTheme.typography.bodyLarge,
      spokenColor = scheme.onSurface,
      // Il paragrafo che si sente vela quello che deve ancora venire; gli altri sono testo pieno, da leggere.
      pendingColor = if (active && paragraph.words.isNotEmpty()) scheme.onSurfaceVariant.copy(alpha = 0.62f) else scheme.onSurface,
      onTextLayout = {
        layout = it
        onLayout(it)
      },
      modifier = Modifier
        .fillMaxWidth()
        .then(
          if (timed) {
            Modifier.pointerInput(paragraph) {
              detectTapGestures { offset ->
                val result = layout ?: return@detectTapGestures
                onSeek(FluidTranscriptSeek.target(paragraph, spokenCharAt(result, offset)))
              }
            }
          } else {
            Modifier
          },
        ),
    )
  }
}

/**
 * Tiene in vista la riga del carattere [offset] del paragrafo [paragraph]: niente se è dentro la fascia e non è [force];
 * altrimenti una scrollata che la porta al [FluidTranscriptDefaults.RestFraction]. Le voci prima dei paragrafi (l'intestazione
 * dell'app) si contano dalla lista: sono quelle in più rispetto ai [paragraphs].
 */
private suspend fun keepInView(
  state: FluidTranscriptState,
  paragraphs: Int,
  paragraph: Int,
  offset: Int,
  reducedMotion: Boolean,
  force: Boolean,
) {
  val list = state.list
  val info = list.layoutInfo
  val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
  if (viewport <= 0f) return
  val item = paragraph + (info.totalItemsCount - paragraphs).coerceAtLeast(0)
  val rest = viewport * FluidTranscriptDefaults.RestFraction
  var visible: androidx.compose.foundation.lazy.LazyListItemInfo? = null
  for (candidate in info.visibleItemsInfo) if (candidate.index == item) visible = candidate
  if (visible == null) {
    // Il paragrafo non è composto (un salto lontano): ci si va per indice.
    val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
    if (reducedMotion || abs(item - first) > 12) list.scrollToItem(item, -rest.toInt()) else list.animateScrollToItem(item, -rest.toInt())
    return
  }
  val layout = state.layouts[paragraph]
  val lineTop = if (layout != null && offset >= 0) {
    layout.getLineTop(layout.getLineForOffset(offset.coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))))
  } else {
    0f
  }
  // La riga in coordinate della lista: l'inizio della voce, l'ora sopra il testo, la riga nel testo.
  val above = (visible.size - (layout?.size?.height ?: visible.size)).coerceAtLeast(0)
  val y = visible.offset - info.viewportStartOffset + above + lineTop
  val inBand = y >= viewport * FluidTranscriptDefaults.BandTop && y <= viewport * FluidTranscriptDefaults.BandBottom
  if (inBand && !force) return
  val delta = y - rest
  if (abs(delta) < 1f) return
  if (reducedMotion) list.scrollBy(delta) else list.animateScrollBy(delta)
}

/** La chiave delle voci dei paragrafi (una stringa: le chiavi di una lista devono stare in un `Bundle`). */
private fun transcriptKey(value: Any): String = "fluid-transcript:$value"

/**
 * Il **carattere sotto il dito** in [layout], per [FluidTranscriptSeek.target] (R3). `getOffsetForPosition` dà il punto
 * di inserimento più vicino, non il carattere: la metà destra dell'ultima lettera di una parola e la metà sinistra dello
 * spazio dopo danno lo stesso numero. Qui si guarda in quale carattere cade il dito, in orizzontale (la riga la sceglie già
 * `getOffsetForPosition`). Lo spazio in fondo a una riga che va a capo resta della parola prima: non si vede, e il dito
 * dopo l'ultima parola della riga vuole quella.
 */
internal fun spokenCharAt(layout: TextLayoutResult, position: androidx.compose.ui.geometry.Offset): Int {
  val text = layout.layoutInput.text
  if (text.isEmpty()) return 0
  val caret = layout.getOffsetForPosition(position)
  val char = if (caret < text.length && layout.getBoundingBox(caret).let { position.x >= it.left && position.x < it.right }) {
    caret
  } else {
    (caret - 1).coerceAtLeast(0)
  }
  val line = layout.getLineForOffset(char)
  val wrappedSpace = text[char].isWhitespace() && line < layout.lineCount - 1 && char == layout.getLineEnd(line) - 1
  return if (wrappedSpace && char > 0) char - 1 else char
}
