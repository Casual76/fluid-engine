package dev.antigravity.fluidengine.aiui.chat

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/*
 * La lista della chat e il fondo: dove si apre una conversazione, quando la si segue mentre la
 * risposta cresce, e quando la si lascia stare. Tre regole, quelle delle chat che si usano:
 *
 * 1. Una conversazione si apre gia' in fondo, senza scorrere davanti a tutti i messaggi. Tornando
 *    dalle impostazioni si ritrova il punto in cui la si era lasciata.
 * 2. Mentre la risposta si forma la lista la segue solo se si e' in fondo (a meno di FollowSlack).
 *    Chi e' risalito a rileggere non viene riportato giu': si riaggancia quando torna lui in fondo.
 * 3. Una domanda appena mandata porta in fondo, con l'animazione, anche da risaliti.
 *
 * Il fondo e' misurato contro l'area sopra il composer: il padding in fondo alla lista e' l'altezza
 * vera del composer piu' tastiera e barra di navigazione. Era di PampAI (`ChatFollowScroll`); la
 * decisione e' stata tirata fuori in una funzione pura, `FluidFollowRule`, perche' e' quella che
 * sbaglia in silenzio (una lista che salta sotto il dito di chi sta rileggendo) e si prova sul computer.
 */

/** Quanto sopra il fondo conta ancora come "in fondo": un dito che sfiora la lista non stacca il seguito. */
private val FollowSlack = 48.dp

/** Il tetto dell'attesa della prima misura, all'apertura: poi la lista si mostra comunque. */
private const val FirstLayoutTimeoutMillis = 600L

/** Cosa fare dopo aver guardato la lista: niente, andare in fondo (con la molla), o inseguire la crescita senza animare. */
enum class FluidFollowAction { None, ScrollToEnd, ChaseGrowth }

/** L'esito di [FluidFollowRule.decide]: il nuovo stato del seguito e cosa fare. */
data class FluidFollowDecision(val follow: Boolean, val action: FluidFollowAction)

/**
 * La regola del fondo, pura.
 *
 * - Una domanda appena mandata (`revealed`) porta in fondo e riaggancia il seguito, anche da risaliti.
 * - Il dito (o un lancio) decide il seguito: finito in fondo, si segue; risalito oltre lo scarto, no.
 *   La crescita del contenuto invece non stacca mai il seguito: e' la risposta che si allunga.
 * - Non si segue: ci si riaggancia solo tornando in fondo.
 * - Seguendo, un item nuovo lontano dal fondo (la domanda, la risposta che comincia) si raggiunge con
 *   la molla, perche' e' un salto e deve vedersi da dove arriva; una crescita di pochi pixel (la
 *   risposta viva, spalmata sui fotogrammi) si insegue senza animazione.
 * - Il fondo si insegue solo quando si allontana da solo: un dito lasciato a venti pixel dal fondo
 *   resta dov'e'.
 *
 * @param distance quanto manca al fondo in pixel, o null se l'ultimo item non e' nemmeno in vista.
 * @param slackPx lo scarto sotto il quale si e' ancora "in fondo".
 */
object FluidFollowRule {
  fun decide(
    follow: Boolean,
    scrolling: Boolean,
    distance: Int?,
    newItems: Boolean,
    grew: Boolean,
    revealed: Boolean,
    slackPx: Int,
  ): FluidFollowDecision = when {
    revealed -> FluidFollowDecision(true, FluidFollowAction.ScrollToEnd)
    scrolling -> FluidFollowDecision(distance != null && distance <= slackPx, FluidFollowAction.None)
    !follow -> FluidFollowDecision(distance != null && distance <= slackPx, FluidFollowAction.None)
    distance == null || (newItems && distance > slackPx) -> FluidFollowDecision(true, FluidFollowAction.ScrollToEnd)
    grew -> FluidFollowDecision(true, FluidFollowAction.ChaseGrowth)
    else -> FluidFollowDecision(true, FluidFollowAction.None)
  }

  /**
   * La stessa regola per una colonna con uno scorrimento solo (la card dell'overlay, dove la
   * risposta cresce dentro un'altezza massima): ogni tanto si guarda dov'e' il dito. Risalito di
   * piu' dello scarto rispetto a prima il seguito si ferma, tornato in fondo riparte. Ritorna il
   * nuovo seguito.
   */
  fun followColumn(follow: Boolean, value: Int, last: Int, max: Int, slackPx: Int): Boolean {
    var next = follow
    if (value < last - slackPx) next = false
    if (value >= max - slackPx) next = true
    return next
  }
}

/**
 * La lista di una conversazione e cio' che sa del fondo.
 *
 * [positioned] e' falso finche' la lista appena aperta non e' stata portata in fondo: fino ad
 * allora non si disegna (sono uno o due fotogrammi), cosi' non si vede la cima della conversazione
 * un attimo prima del salto. [follow] dice se la si sta seguendo; si salva con la posizione, perche'
 * tornando dalle impostazioni a meta' risposta il seguito deve riprendere da solo.
 */
@Stable
class FluidChatScroll(val list: LazyListState, positioned: Boolean, follow: Boolean) {
  var positioned by mutableStateOf(positioned)
  var follow: Boolean = follow

  /** Cresce a ogni domanda mandata: e' il segnale per [FluidFollowBottom] di portare in fondo, animato. */
  var reveals by mutableIntStateOf(0)
    private set

  /** Una domanda appena mandata: in fondo, anche da risaliti, e da li' si segue. */
  fun revealLatest() {
    follow = true
    reveals++
  }

  companion object {
    val Saver: Saver<FluidChatScroll, Any> = listSaver<FluidChatScroll, Int>(
      save = { listOf(it.list.firstVisibleItemIndex, it.list.firstVisibleItemScrollOffset, if (it.positioned) 1 else 0, if (it.follow) 1 else 0) },
      restore = { FluidChatScroll(LazyListState(it[0], it[1]), positioned = it[2] == 1, follow = it[3] == 1) },
    )
  }
}

/**
 * Una lista per conversazione. La chiave viene da [FluidChatScrollKeys]: cambia quando si apre un'altra
 * conversazione o una chat nuova, e allora la lista riparte e si porta in fondo; resta quando si
 * torna da un'altra pagina, e allora la posizione salvata torna com'era.
 */
@Composable
fun rememberFluidChatScroll(key: String): FluidChatScroll =
  rememberSaveable(key, saver = FluidChatScroll.Saver) { FluidChatScroll(LazyListState(), positioned = false, follow = true) }

/**
 * Quale lista mostra la chat. Di solito una per conversazione; l'eccezione e' la chat nuova che
 * nasce su disco mentre l'assistente le risponde: e' la stessa pagina, e cambiarle lista vorrebbe
 * dire rifarla da capo (e nasconderla per un fotogramma) proprio mentre la prima risposta arriva.
 * Allora la conversazione appena nata eredita la chiave della chat nuova.
 *
 * Campi semplici e non stato: li scrive la composizione con gli stessi ingressi e lo stesso esito,
 * e si salvano con la pagina ([Saver]). Pura, e provata da `FluidChatScrollKeysTest`.
 */
class FluidChatScrollKeys(
  private var epoch: Int = 0,
  private var last: Long? = null,
  private var adopted: Long? = null,
  private var bornHere: Boolean = false,
) {
  fun keyFor(conversationId: Long?, liveShown: Boolean): String {
    if (conversationId == null) {
      if (last != null) {
        epoch++
        last = null
        adopted = null
      }
      // Una chat nuova con una domanda in corso: quando nascera' su disco sara' ancora lei.
      bornHere = liveShown
      return "nuova-$epoch"
    }
    if (conversationId != last) {
      adopted = if (last == null && bornHere) conversationId else null
      bornHere = false
      last = conversationId
    }
    return if (conversationId == adopted) "nuova-$epoch" else "c-$conversationId"
  }

  companion object {
    val Saver: Saver<FluidChatScrollKeys, Any> = listSaver<FluidChatScrollKeys, Long>(
      save = { listOf(it.epoch.toLong(), it.last ?: NoId, it.adopted ?: NoId, if (it.bornHere) 1L else 0L) },
      restore = { FluidChatScrollKeys(it[0].toInt(), it[1].takeIf { id -> id != NoId }, it[2].takeIf { id -> id != NoId }, it[3] == 1L) },
    )
    private const val NoId = Long.MIN_VALUE
  }
}

@Composable
fun rememberFluidChatScrollKeys(): FluidChatScrollKeys = rememberSaveable(saver = FluidChatScrollKeys.Saver) { FluidChatScrollKeys() }

/**
 * Le tre regole del fondo, per [scroll]. Niente loop di fotogrammi: si ascoltano la misura della
 * lista e il dito (`snapshotFlow` su `layoutInfo` e `isScrollInProgress`), cosi' a riposo non si
 * chiede nessun fotogramma e la batteria non se ne accorge. Le decisioni sono di [FluidFollowRule].
 */
@Composable
fun FluidFollowBottom(scroll: FluidChatScroll) {
  val slack = with(LocalDensity.current) { FollowSlack.toPx() }.toInt()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  LaunchedEffect(scroll, reducedMotion) {
    val list = scroll.list
    // La prima misura: prima non c'e' niente da misurare, e il seguito si deciderebbe sul vuoto.
    withTimeoutOrNull(FirstLayoutTimeoutMillis) { snapshotFlow { list.layoutInfo.totalItemsCount }.first { it > 0 } }
    if (!scroll.positioned) {
      guarded { list.jumpToEnd() }
      scroll.follow = true
      scroll.positioned = true
    }
    var lastCount = list.layoutInfo.totalItemsCount
    var lastReveal = scroll.reveals
    var lastDistance = list.distanceToEnd()
    snapshotFlow { FollowSample(list.isScrollInProgress, list.distanceToEnd(), list.layoutInfo.totalItemsCount, scroll.reveals) }
      .collect { sample ->
        val newItems = sample.count > lastCount
        lastCount = sample.count
        val distance = sample.distance
        // Il fondo si insegue solo quando si allontana da solo (il contenuto cresce, il composer o
        // la tastiera si alzano): un dito lasciato a venti pixel dal fondo resta dov'e'.
        val grew = distance != null && distance > (lastDistance ?: 0)
        lastDistance = distance
        val revealed = sample.reveals != lastReveal
        lastReveal = sample.reveals
        val decision = FluidFollowRule.decide(scroll.follow, sample.scrolling, distance, newItems, grew, revealed, slack)
        scroll.follow = decision.follow
        when (decision.action) {
          FluidFollowAction.ScrollToEnd -> {
            guarded { list.toEnd(animated = !reducedMotion) }
            lastDistance = list.distanceToEnd()
          }
          FluidFollowAction.ChaseGrowth -> {
            guarded { list.scrollBy((distance ?: 0).toFloat()) }
            lastDistance = list.distanceToEnd()
          }
          FluidFollowAction.None -> Unit
        }
      }
  }
}

private data class FollowSample(val scrolling: Boolean, val distance: Int?, val count: Int, val reveals: Int)

/**
 * Quanto manca al fondo, in pixel: dal bordo basso dell'ultimo item (piu' il padding in fondo, cioe'
 * il composer) al bordo basso della lista. Null se l'ultimo item non e' nemmeno in vista.
 */
fun LazyListState.distanceToEnd(): Int? {
  val info = layoutInfo
  val last = info.visibleItemsInfo.lastOrNull() ?: return null
  if (last.index != info.totalItemsCount - 1) return null
  return (last.offset + last.size + info.afterContentPadding - info.viewportEndOffset).coerceAtLeast(0)
}

/**
 * In fondo in un colpo: all'ultimo item e poi di quanto sporge. Dopo `scrollToItem` la misura e'
 * gia' rifatta (la lista si rimisura subito), quindi i due passi finiscono nello stesso fotogramma.
 */
suspend fun LazyListState.jumpToEnd() {
  val total = layoutInfo.totalItemsCount
  if (total == 0) return
  if (distanceToEnd() == null) scrollToItem(total - 1)
  distanceToEnd()?.takeIf { it > 0 }?.let { scrollBy(it.toFloat()) }
}

/** In fondo, con la molla della chrome se [animated]. Un item lontano si raggiunge prima con il salto della lista. */
private suspend fun LazyListState.toEnd(animated: Boolean) {
  if (!animated) {
    jumpToEnd()
    return
  }
  val total = layoutInfo.totalItemsCount
  if (total == 0) return
  if (distanceToEnd() == null) animateScrollToItem(total - 1)
  distanceToEnd()?.takeIf { it > 0 }?.let { animateScrollBy(it.toFloat(), FollowSpec) }
}

private val FollowSpec: AnimationSpec<Float> = FluidMotion.snappy()

/**
 * Uno scorrimento nostro che il dito puo' interrompere: la lista lo cancella quando l'utente la
 * tocca (il dito ha la precedenza), e quella cancellazione non deve chiudere l'ascolto del fondo.
 * Se invece a chiudersi e' l'effetto stesso, la cancellazione passa.
 */
private suspend inline fun guarded(block: () -> Unit) {
  try {
    block()
  } catch (e: CancellationException) {
    currentCoroutineContext().ensureActive()
  }
}

/**
 * Il corpo che segue la risposta mentre si forma, per una colonna con uno scorrimento solo (la card
 * dell'overlay): ogni [FollowIntervalMillis] torna in fondo, ma solo se ci si era. Chi e' risalito a
 * rileggere resta dov'e' e si riaggancia quando torna lui in fondo — la stessa regola della lista
 * della chat ([FluidFollowRule.followColumn]), con lo scroll di una colonna invece che di una `LazyColumn`.
 */
@Composable
fun FluidFollowAnswer(scroll: ScrollState, following: Boolean) {
  val slack = with(LocalDensity.current) { 12.dp.toPx() }.toInt()
  LaunchedEffect(scroll, following) {
    if (!following) return@LaunchedEffect
    var follow = true
    var last = scroll.value
    while (true) {
      delay(FollowIntervalMillis)
      follow = FluidFollowRule.followColumn(follow, scroll.value, last, scroll.maxValue, slack)
      if (follow && scroll.value < scroll.maxValue) guarded { scroll.animateScrollTo(scroll.maxValue, FluidMotion.snappy()) }
      last = scroll.value
    }
  }
}

/** Ogni quanto si torna in fondo nella card. */
private const val FollowIntervalMillis = 120L
