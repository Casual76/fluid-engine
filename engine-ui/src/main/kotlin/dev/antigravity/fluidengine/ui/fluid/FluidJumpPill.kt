package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowDownward
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowUpward
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons

/** Verso dove porta una [FluidJumpPill]: in cima (le novita' sopra) o in fondo (le novita' sotto). */
enum class FluidJumpDirection { Up, Down }

/**
 * La pillola di vetro «N nuove ↑»: arrivano elementi sopra (o sotto) il punto in cui l'utente sta leggendo, e
 * invece di spostargli la lista sotto il dito glielo si dice, con un tocco per andarci.
 *
 * Il caso per cui esiste: una lista che riceve in cima (una casella, un feed) mentre l'utente e' a meta'. Con le
 * chiavi stabili di [fluidGroupedItems] la lista resta ferma su quello che si sta leggendo, e le righe nuove
 * entrano fuori vista: senza un segno, l'utente non sa che ci sono. Con [FluidJumpDirection.Down] e' il caso di una
 * conversazione, con i messaggi nuovi sotto.
 *
 * - **Vetro**: e' un [FluidGlassButton] compatto (bersaglio di 48 dp, la pressione e l'aptica dei tasti). Si mette
 *   nell'`overlay` di [FluidScreen], che gli passa il fondale; di serie campiona il canvas se c'e', altrimenti la
 *   chrome in scope.
 * - **Entra per offset e alfa, mai per scala**: un layer scalato sopra un vetro rifrange cio' che non c'e' piu'
 *   (`regole.md`, «Un ingresso non scala un vetro»). Col movimento ridotto compare e sparisce e basta.
 * - **TalkBack** la annuncia senza interrompere (`liveRegion` educata): quando compare e quando l'etichetta cambia
 *   («3 nuove», poi «5 nuove»). L'etichetta e' quella che si legge: deve dire cosa c'e' e dove («3 nuove in
 *   alto»), perche' la freccia e' decorazione.
 *
 * Quando mostrarla lo decide l'app: di solito ci sono righe nuove sopra **e** la prima riga visibile non e' in
 * cima; toccandola l'app scorre (`animateScrollToItem`) e azzera il conteggio, e lo azzera anche quando l'utente ci
 * arriva da solo.
 *
 * @param visible se si vede. Il cambio anima l'ingresso o l'uscita.
 * @param label l'etichetta, dall'app («3 nuove»): l'engine non sa cosa siano le cose nuove.
 * @param onClick porta al punto: l'app scorre la lista.
 * @param direction il verso della freccia e dell'ingresso (dall'alto per [FluidJumpDirection.Up]).
 * @param backdrop cio' che il vetro rifrange; nell'`overlay` di [FluidScreen], quello che lo slot riceve.
 */
@Composable
fun FluidJumpPill(
  visible: Boolean,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  direction: FluidJumpDirection = FluidJumpDirection.Up,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val fromAbove = direction == FluidJumpDirection.Up
  if (reducedMotion) {
    // Col movimento ridotto niente transizione, nemmeno vuota: compare e sparisce nel fotogramma in cui cambia.
    if (visible) FluidJumpPillButton(label, onClick, modifier, fromAbove, backdrop)
    return
  }
  val enter = slideInVertically(FluidMotion.jumpPillOffset()) { height -> if (fromAbove) -height else height } +
    fadeIn(FluidMotion.fadeIn(FluidMotion.JumpPillFadeInMillis))
  val exit = slideOutVertically(FluidMotion.jumpPillOffset()) { height -> if (fromAbove) -height else height } +
    fadeOut(FluidMotion.fadeOut(FluidMotion.JumpPillFadeOutMillis))
  AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit) {
    FluidJumpPillButton(label, onClick, Modifier, fromAbove, backdrop)
  }
}

@Composable
private fun FluidJumpPillButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier,
  fromAbove: Boolean,
  backdrop: GlassBackdropState,
) {
  FluidGlassButton(
    text = label,
    onClick = onClick,
    modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    backdrop = backdrop,
    size = FluidGlassSize.Compact,
    // Senza tetto di righe: a testo grande l'etichetta va a capo e il tasto cresce, invece di perdere la parola
    // che dice dove porta.
    leading = {
      FluidIcon(
        imageVector = if (fromAbove) FluidIcons.ArrowUpward else FluidIcons.ArrowDownward,
        contentDescription = null,
      )
    },
  )
}
