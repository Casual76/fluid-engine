package dev.antigravity.fluidengine.ui.tutorial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import dev.antigravity.fluidengine.ui.fluid.GlassInkContent
import dev.antigravity.fluidengine.ui.fluid.rememberGlassInk
import dev.antigravity.fluidengine.ui.fluid.glassSurfaceInked

/** Di quanto il callout scivola nell'entrata e nell'uscita: pochi dp, verso l'elemento che spiega. */
private val FluidCoachMarkRise = 8.dp

/**
 * Dove finisce il callout e da che parte sta rispetto al suo elemento. Coordinate della radice, in pixel.
 *
 * Dalla 3.0 (fase 9) il callout non disegna piu' una punta verso l'elemento: [pointerX] resta nella
 * geometria — e' la x del centro dell'elemento nel sistema di riferimento del callout, e chi lo posiziona
 * puo' ancora volerla — ma da qui non lo legge nessun disegno. Quanto a [below], decide anche da che
 * parte il callout scivola entrando.
 */
data class FluidCoachMarkPlacement(
  val x: Float,
  val y: Float,
  val below: Boolean,
  /** Il centro dell'elemento, in coordinate del callout, rientrato dagli angoli arrotondati. */
  val pointerX: Float,
)

/**
 * Dove mettere un callout rispetto al suo elemento. Puro: la geometria di un pop-up e' la cosa
 * piu' facile da sbagliare in fondo a uno schermo, ed e' anche la piu' facile da provare.
 *
 * Sotto l'elemento se c'e' posto, sopra altrimenti, e nel lato piu' capiente quando non ci sta da
 * nessuna delle due parti. Orizzontalmente e' centrato sull'elemento e rientrato nei margini; la
 * punta insegue il centro fin dove puo' senza uscire dagli angoli arrotondati.
 */
object FluidCoachMarkPlacer {

  fun place(
    anchor: Rect,
    calloutWidth: Float,
    calloutHeight: Float,
    screen: Size,
    safeTop: Float = 0f,
    safeBottom: Float = 0f,
    margin: Float = 0f,
    gap: Float = 0f,
    pointerInset: Float = 0f,
  ): FluidCoachMarkPlacement {
    val topLimit = safeTop + margin
    val bottomLimit = screen.height - safeBottom - margin
    val belowY = anchor.bottom + gap
    val aboveY = anchor.top - gap - calloutHeight
    val below = when {
      belowY + calloutHeight <= bottomLimit -> true
      aboveY >= topLimit -> false
      // Non ci sta da nessuna parte: vince il lato con piu' spazio, e il callout si rientra.
      else -> (bottomLimit - anchor.bottom) >= (anchor.top - topLimit)
    }
    val rawY = if (below) belowY else aboveY
    val y = rawY.coerceIn(topLimit, (bottomLimit - calloutHeight).coerceAtLeast(topLimit))

    val maxX = (screen.width - margin - calloutWidth).coerceAtLeast(margin)
    val x = (anchor.center.x - calloutWidth / 2f).coerceIn(margin, maxX)
    val pointerX = (anchor.center.x - x).coerceIn(
      pointerInset,
      (calloutWidth - pointerInset).coerceAtLeast(pointerInset),
    )
    return FluidCoachMarkPlacement(x = x, y = y, below = below, pointerX = pointerX)
  }
}

/**
 * L'elemento a cui un suggerimento si aggancia: misura i suoi limiti per il callout e, mentre e'
 * lui quello spiegato, si porta un alone. Senza, "questo" in una frase non indica niente.
 */
@Composable
fun Modifier.fluidTutorialAnchor(id: String): Modifier {
  val state = LocalFluidTutorialHostState.current ?: return this
  val highlighted = state.presenting?.anchorId == id
  val ring = MaterialTheme.colorScheme.primary
  // Un elemento che esce di scena porta via i suoi limiti: senza, un callout finirebbe per
  // indicare il punto dove c'era un tasto che ora non c'e' piu'.
  DisposableEffect(state, id) {
    onDispose { state.anchorBounds(id, null) }
  }
  return this
    .onGloballyPositioned { state.anchorBounds(id, it.boundsInRoot()) }
    .drawWithContent {
      drawContent()
      if (!highlighted) return@drawWithContent
      val inset = 6.dp.toPx()
      drawRoundRect(
        color = ring.copy(alpha = 0.55f),
        topLeft = Offset(-inset, -inset),
        size = Size(size.width + inset * 2, size.height + inset * 2),
        cornerRadius = CornerRadius(FluidRadius.Group.toPx()),
        style = Stroke(width = 2.dp.toPx()),
      )
    }
}

/**
 * Il padrone di casa dei suggerimenti: sta alla radice dell'app, sopra tutto il resto, e mostra
 * un callout per volta quando la politica dice che e' il momento.
 *
 * Non e' un modale. Nessuno scrim, la pagina sotto resta leggibile, e **un tocco fuori chiude** il
 * callout senza consumare il tocco: arriva lo stesso a quello che c'era sotto, perche' un
 * suggerimento che si mette fra il dito e l'app e' peggio del non averlo dato. Lo stesso tocco
 * conta anche come interazione, che e' quello che sblocca il suggerimento successivo.
 */
@Composable
fun FluidTutorialHost(
  state: FluidTutorialHostState,
  labels: FluidTutorialLabels,
  backdrop: GlassBackdropState? = null,
  modalPresenting: () -> Boolean = { false },
) {
  LaunchedEffect(state) {
    while (isActive) {
      state.evaluate(modalPresenting())
      val idle = state.presenting == null && state.pendingCount == 0
      delay(if (idle) FluidTutorialPolicy.IDLE_TICK_MILLIS else FluidTutorialPolicy.TICK_MILLIS)
    }
  }

  val tutorial = state.presenting
  val anchor = state.presentingAnchor
  // L'ultimo mostrato resta finche' l'uscita non e' finita: senza, il callout sparirebbe di colpo
  // lasciando in scena solo la sua ombra che si spegne.
  var shown by remember { mutableStateOf<FluidTutorial?>(null) }
  var shownAnchor by remember { mutableStateOf<Rect?>(null) }
  LaunchedEffect(tutorial, anchor) {
    if (tutorial != null) shown = tutorial
    if (anchor != null) shownAnchor = anchor
  }

  // Il colore del testo sul callout (3.0.1): l'host sta alla radice, fuori da una `Surface`.
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val density = LocalDensity.current
      val screen = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
      val margin = with(density) { 16.dp.toPx() }
      val gap = with(density) { 12.dp.toPx() }
      val pointerInset = with(density) { 26.dp.toPx() }

      var calloutSize by remember { mutableStateOf(IntSize.Zero) }
      val placement = shownAnchor?.takeIf { calloutSize.width > 0 }?.let { bounds ->
        FluidCoachMarkPlacer.place(
          anchor = bounds,
          calloutWidth = calloutSize.width.toFloat(),
          calloutHeight = calloutSize.height.toFloat(),
          screen = screen,
          margin = margin,
          gap = gap,
          pointerInset = pointerInset,
        )
      }

      // I limiti del callout li legge chi osserva i tocchi: il padrone di casa non mette nulla
      // sopra la pagina. Un fratello a tutto schermo che intercetta i puntatori si prende l'intero
      // percorso di hit test, e l'app sotto smette di rispondere al dito: e' successo davvero.
      state.calloutBounds = placement?.let {
        Rect(it.x, it.y, it.x + calloutSize.width, it.y + calloutSize.height)
      }

      // L'ingresso e' di geometria e di alfa, mai di scala (3.0, fase 9): il callout e' un pannello di
      // vetro, e un `scaleIn` su un nodo di vetro scala anche quello che il vetro sta rifrangendo — il
      // testo della pagina dietro si ingrandisce di un 6% per il tempo dell'entrata, e quel testo sta
      // fermo. Il callout arriva dalla parte del suo elemento: scende di qualche dp verso il posto che
      // gli spetta se sta sotto, sale se sta sopra. Lo scivolamento e' un offset di layout (geometria:
      // il vetro campiona dove sta davvero), e la dissolvenza e' l'unico layer sopra di lui.
      val rise = with(density) { FluidCoachMarkRise.roundToPx() }
      val towardsAnchor = if (placement?.below == false) rise else -rise
      AnimatedVisibility(
        visible = tutorial != null && anchor != null,
        enter = fadeIn(FluidMotion.fadeIn(160)) + slideInVertically(FluidMotion.snappy()) { towardsAnchor },
        exit = fadeOut(FluidMotion.fadeOut(120)) + slideOutVertically(FluidMotion.snappy()) { towardsAnchor },
      ) {
        val current = shown ?: return@AnimatedVisibility
        FluidCoachMark(
          tutorial = current,
          labels = labels,
          placement = placement,
          backdrop = backdrop,
          onMeasured = { calloutSize = it },
          onDismiss = { state.dismiss() },
          onOptOut = { state.dismiss(optOut = true) },
        )
      }
    }
  }
}

/**
 * L'osservatore dei tocchi, da mettere sul **contenitore** della pagina e mai sopra di essa: un
 * nodo genitore vede il tocco nel giro iniziale senza consumarlo, e i figli lo ricevono lo stesso.
 * Sopra la pagina, invece, lo intercetterebbe e basta.
 */
fun Modifier.fluidTutorialTouches(state: FluidTutorialHostState): Modifier = this.pointerInput(state) {
  awaitPointerEventScope {
    while (true) {
      val event = awaitPointerEvent(PointerEventPass.Initial)
      val down = event.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
      state.touchAt(down.position)
    }
  }
}

/**
 * Il callout: vetro, un titolo di due parole, una frase, il gesto disegnato quando serve, e i due
 * modi di chiuderlo — "Ok" (un tasto di vetro), e il link che li spegne tutti.
 *
 * Tutto vetro, senza niente di dipinto accanto: il pannello e' il pannello flottante di tutti, il
 * tasto "Ok" e' un tasto di vetro, e l'indicazione di quale elemento si sta spiegando non e' una
 * punta ma l'alone attorno all'elemento ([fluidTutorialAnchor]) e la posizione del callout.
 *
 * Con [placement] nullo il callout e' gia' composto ma invisibile: la sua posizione dipende dalla
 * sua misura, e la misura si sa solo dopo averlo disegnato una volta.
 */
@Composable
fun FluidCoachMark(
  tutorial: FluidTutorial,
  labels: FluidTutorialLabels,
  placement: FluidCoachMarkPlacement?,
  backdrop: GlassBackdropState?,
  onMeasured: (IntSize) -> Unit,
  onDismiss: () -> Unit,
  onOptOut: () -> Unit,
) {
  val shape = ContinuousCornerShape(FluidRadius.Card)
  val scheme = MaterialTheme.colorScheme
  // F2.3b: il suggerimento possiede il suo testo e scurisce in continuo (un grande, `Modal`); il testo lo segue.
  val coachInk = rememberGlassInk()
  val surface = if (backdrop != null) {
    Modifier.glassSurfaceInked(
      ink = coachInk,
      state = backdrop,
      tint = GlassDefaults.panelTint(),
      shape = shape,
      role = GlassRole.Modal,
      // Il pannello flottante di tutti, con la sfocatura di chi sta sopra una pagina nitida.
      optics = GlassDefaults.panelOptics(overSharpPage = true),
    )
  } else {
    Modifier.background(scheme.surfaceContainerHigh, shape)
  }
  val dismissOnGlass = backdrop != null && isRenderEffectSupported()

  Column(
    modifier = Modifier
      .offset { IntOffset(placement?.x?.toInt() ?: 0, placement?.y?.toInt() ?: 0) }
      .alpha(if (placement == null) 0f else 1f)
      .widthIn(max = 320.dp)
      .onSizeChanged(onMeasured)
      // Il callout e' l'unica cosa che si prende i propri tocchi: senza, toccarne il testo
      // premerebbe quello che ci sta dietro.
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
          }
        }
      }
      // Niente punta (fase 9). Era un triangolo dipinto, opaco al 94%, appoggiato al bordo di un
      // pannello di vetro: l'unica cosa del callout che non rifrangeva. Farne parte della sagoma non
      // si puo': la lente e' la dislocazione di un rettangolo arrotondato (lo shader e' una SDF di
      // rettangolo, e una sagoma qualsiasi perde la lente e tiene solo la sfocatura), e uno shader
      // nuovo accanto a quello del vetro e' proprio cio' che la regola dei materiali vieta. Il suo
      // mestiere — dare a "questo" un referente — lo fanno gia' due cose: l'alone che
      // `fluidTutorialAnchor` disegna attorno all'elemento mentre e' lui quello spiegato, e il
      // posizionamento (centrato sull'elemento, a un respiro di distanza, dal lato che ha posto).
      .then(surface)
      .padding(16.dp),
  ) {
    GlassInkContent(coachInk, LocalContentColor.current) {
      @Suppress("NAME_SHADOWING")
      val scheme = MaterialTheme.colorScheme
      Text(
        text = tutorial.title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = scheme.onSurface,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = tutorial.text,
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant,
      )
      tutorial.hint?.let { hint ->
        Spacer(Modifier.height(10.dp))
        FluidGestureHintAnimation(
          hint = hint,
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        )
      }
      Spacer(Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = labels.optOut,
          style = MaterialTheme.typography.labelMedium,
          color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
          modifier = Modifier
            .fluidPressable(onClick = onOptOut, role = Role.Button, feedback = FluidPressFeedback.Expand)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        )
        Spacer(Modifier.width(12.dp))
        if (dismissOnGlass) {
          // Un tasto di vetro, scelto: l'accento entra nel materiale invece di un riempimento che lo
          // sostituisce, come ogni tasto di vetro scelto. Sta su un pannello di vetro, e ne campiona il
          // materiale da solo (vetro su vetro, 2.13.0), non la pagina dietro. Il colpo e' `Close`, come
          // la capsula che sostituisce.
          FluidGlassButton(
            text = labels.dismiss,
            onClick = onDismiss,
            backdrop = backdrop!!,
            selected = true,
            size = FluidGlassSize.Compact,
            haptic = FluidHapticEvent.Close,
          )
        } else {
          Text(
            text = labels.dismiss,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onPrimaryContainer,
            // The press outside the fill, so the capsule grows and not only its word (2.11.0).
            modifier = Modifier
              .fluidPressable(
                onClick = onDismiss,
                role = Role.Button,
                haptic = FluidHapticEvent.Close,
                feedback = FluidPressFeedback.Expand,
              )
              .background(scheme.primaryContainer, FluidCapsuleShape)
              .padding(horizontal = 18.dp, vertical = 8.dp),
          )
        }
      }
    }
  }
}
