package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

/**
 * Le misure di una pagina elenco+dettaglio.
 *
 * L'elenco non e' una frazione fissa della finestra: e' largo quanto serve a leggere una riga di
 * lista senza andare a capo a ogni parola ([listMin]), cresce un poco con la finestra
 * ([listFraction]) e smette a [listMax], perche' oltre quello le righe diventano lunghe e il
 * dettaglio — che e' la cosa che si legge — perde spazio per niente.
 */
@Immutable
data class FluidListDetailSizes(
  val listMin: Dp = 360.dp,
  val listMax: Dp = 500.dp,
  val listFraction: Float = 0.4f,
  /** Sotto questa larghezza il dettaglio non e' piu' una pagina da leggere: si torna a uno solo. */
  val detailMin: Dp = 480.dp,
)

/** Cosa sta in scena in una pagina elenco+dettaglio: uno o due pannelli, e quanto larghi. */
@Immutable
data class FluidListDetailLayout(
  val twoPane: Boolean,
  val listWidth: Dp,
  val detailWidth: Dp,
)

/**
 * Quanto e' largo l'elenco in [available] dp, e se accanto c'e' posto per il dettaglio.
 *
 * Due pannelli solo quando **tutti e due** restano quello che sono: un elenco di almeno
 * [FluidListDetailSizes.listMin] e un dettaglio di almeno [FluidListDetailSizes.detailMin]. Un
 * tablet in verticale (800 dp) non ci arriva e resta una pagina sola, piu' larga: e' meglio di due
 * colonne strette, entrambe da telefono. In orizzontale ci arriva sempre.
 *
 * Una larghezza non finita da' un pannello solo: meglio una pagina da telefono per un fotogramma
 * che due pannelli che spariscono subito dopo.
 */
fun fluidListDetailLayout(
  available: Dp,
  sizes: FluidListDetailSizes = FluidListDetailSizes(),
): FluidListDetailLayout {
  if (!available.isFinite || available < sizes.listMin + sizes.detailMin) {
    return FluidListDetailLayout(twoPane = false, listWidth = available, detailWidth = 0.dp)
  }
  val list = (available * sizes.listFraction)
    .coerceIn(sizes.listMin, sizes.listMax)
    .coerceAtMost(available - sizes.detailMin)
  return FluidListDetailLayout(twoPane = true, listWidth = list, detailWidth = available - list)
}

/**
 * Una pagina che elenca cose e ne mostra una: su un telefono l'elenco e basta, su uno schermo
 * largo l'elenco a sinistra e la cosa scelta accanto.
 *
 * Tre decisioni, e ognuna risponde a un modo in cui un tablet si rompe.
 *
 *  * **Un fondale solo.** I due pannelli stanno dentro una [FluidAmbientSurface]: ogni
 *    [FluidScreen] al loro interno smette di dipingere il suo, e la finestra si legge come una
 *    pagina divisa in colonne, non come due telefoni accostati con una cucitura nel mezzo. Il
 *    motivo del fondale compare una volta, per la finestra, e non cambia scegliendo un'altra cosa.
 *  * **La chrome dell'app guarda l'elenco.** La barra delle schede galleggia in basso a sinistra,
 *    cioe' sopra l'elenco: e' quello che deve rifrangere. Il dettaglio non si registra presso il
 *    controller, cosi' non puo' vincere l'elezione e far vedere alla pillola una pagina che non ha
 *    sotto.
 *  * **Il dettaglio non esiste finche' non serve.** Su un pannello solo [detail] non viene composto:
 *    li' la cosa scelta si apre come si apriva prima — un pop-up dalla riga, una pagina — e il
 *    parametro di [list] dice quale dei due mondi e' questo.
 *
 * @param list l'elenco; riceve `true` quando il dettaglio gli sta accanto, e da quello decide se
 *   una riga toccata *seleziona* o *apre*.
 * @param detail il pannello destro: di solito una [FluidScreen], o [FluidDetailPlaceholder] quando
 *   non c'e' niente di scelto. Composto solo su due pannelli.
 */
@Composable
fun FluidListDetailScaffold(
  ambient: FluidAmbient?,
  modifier: Modifier = Modifier,
  sizes: FluidListDetailSizes = FluidListDetailSizes(),
  list: @Composable (twoPane: Boolean) -> Unit,
  detail: @Composable () -> Unit,
) {
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val layout = fluidListDetailLayout(maxWidth, sizes)
    if (!layout.twoPane) {
      list(false)
      return@BoxWithConstraints
    }
    FluidAmbientSurface(ambient = ambient) {
      Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(layout.listWidth).fillMaxHeight()) {
          CompositionLocalProvider(LocalFluidPaneRole provides FluidPaneRole.List) { list(true) }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          CompositionLocalProvider(
            LocalFluidPaneRole provides FluidPaneRole.Detail,
            LocalFluidChromeController provides null,
          ) { detail() }
        }
      }
    }
  }
}

/**
 * Il contenuto del pannello di dettaglio, che cambia quando si sceglie un'altra cosa.
 *
 * Il cambio e' un movimento fra **pari**, lo stesso delle schede: due cose dello stesso elenco non
 * sono una dentro l'altra, quindi niente cresce e niente arretra. Quella nuova scorre di
 * [FluidMotion.PeerSlideFraction] dalla parte in cui sta nell'elenco ([order]: piu' in basso arriva
 * da destra, piu' in alto da sinistra), quella vecchia se ne va dall'altra parte sfumando.
 *
 * Quella nuova **non sfuma mai** — una pagina che arriva e' opaca dal primo fotogramma in cui si
 * vede. Ma dentro un fondale condiviso nessuna pagina e' opaca: il fondo e' della finestra. Per
 * questo compare solo quando quella vecchia se n'e' quasi andata, e le due non si leggono mai
 * l'una attraverso l'altra.
 *
 * @param item la cosa mostrata, o null se non ce n'e' una. Chi esce si tiene la *sua*: la pagina che
 *   se ne va non si riempie col contenuto di quella che arriva.
 * @param key l'identita' di [item]: cambia lei, parte il movimento; cambia solo il dato (una
 *   circolare appena segnata come letta) e la pagina si aggiorna dove sta.
 * @param order la posizione nell'elenco, per scegliere la direzione; null se non ne ha una.
 */
@Composable
fun <T> FluidDetailContent(
  item: T,
  modifier: Modifier = Modifier,
  key: (T) -> Any? = { it },
  order: (T) -> Int? = { null },
  content: @Composable (T) -> Unit,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  AnimatedContent(
    targetState = item,
    modifier = modifier.fillMaxSize(),
    contentKey = key,
    transitionSpec = {
      if (reducedMotion) {
        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
      } else {
        val from = order(initialState)
        val to = order(targetState)
        val direction = if (from != null && to != null && to < from) -1 else 1
        fluidDetailPeerTransform(direction)
      }
    },
    label = "fluid-detail",
  ) { shown ->
    content(shown)
  }
}

private fun fluidDetailPeerTransform(direction: Int): ContentTransform {
  val slide = tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = FluidMotion.DurationPeer,
    easing = FluidMotion.EaseEmphasized,
  )
  val arriving = slideInHorizontally(slide) { width ->
    (width * FluidMotion.PeerSlideFraction * direction).roundToInt()
  } + fadeIn(tween(durationMillis = 1, delayMillis = DetailArrivalDelayMillis))
  val leaving = slideOutHorizontally(slide) { width ->
    -(width * FluidMotion.PeerSlideFraction * direction).roundToInt()
  } + fadeOut(tween(durationMillis = DetailArrivalDelayMillis, easing = FluidMotion.EaseIn))
  return (arriving togetherWith leaving).apply { targetContentZIndex = 1f }
}

/**
 * Quanto aspetta la cosa nuova prima di comparire: il tempo in cui quella vecchia se ne va.
 *
 * Corto, perche' il movimento e' di chi arriva e il tocco deve avere una risposta subito; lungo
 * abbastanza che le due pagine, entrambe senza fondo, non stiano mai leggibili insieme.
 */
private const val DetailArrivalDelayMillis = 110

/**
 * Il pannello di dettaglio quando non c'e' niente di scelto.
 *
 * Non e' uno stato d'errore e non deve sembrarlo: dice che cosa comparira' li', con un'icona
 * velata e due righe, al centro del pannello. Niente titolo grande e niente barra: una pagina vuota
 * con la chrome completa sembra una pagina che non ha finito di caricare.
 */
@Composable
fun FluidDetailPlaceholder(
  title: String,
  modifier: Modifier = Modifier,
  message: String? = null,
  icon: ImageVector? = null,
) {
  Box(
    modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier.widthIn(max = 360.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
          modifier = Modifier.padding(bottom = 4.dp).size(40.dp),
        )
      }
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
      )
      if (message != null) {
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
