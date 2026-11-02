/*
 * La goccia che fa da maniglia e la copia della pista che rifrange sono quelle di `FluidSlider`, che le
 * porta dal LiquidSlider di Kyant0 (https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025
 * Kyant0, Apache License 2.0; il testo e' a http://www.apache.org/licenses/LICENSE-2.0 e le modifiche
 * sono elencate in LICENSES/AndroidLiquidGlass.md). Qui non c'e' un numero, un bordo, un'ombra o una
 * molla che non sia gia' la' (`glass/interaction/GlassDrop.kt`, `GlassDragAnimation`, `FluidMotion`).
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropLens
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropRecipe
import dev.antigravity.fluidengine.ui.glass.interaction.glassDrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Come un [FluidScrubber] racconta una posizione a parole: la [position] e l'[range] in cui sta, nella
 * stessa unita' del valore. Quella dell'app: millisecondi, secondi, l'indice di un fotogramma.
 */
typealias FluidScrubberFormat = (position: Float, range: ClosedFloatingPointRange<Float>) -> String

/**
 * Lo **scrubber**: la barra con cui si sceglie dove andare in qualcosa che dura — un brano, una
 * registrazione, una cronologia di fotogrammi.
 *
 * E' della famiglia della goccia (`docs/10-coerenza.md`): la maniglia e' la stessa di [FluidSlider],
 * bianca e opaca a riposo e lente sotto il dito, e la pista e' sottile come la sua. Quello che lo rende
 * un'altra cosa e' che **la posizione non e' sua**: la possiede un lettore, che la fa avanzare da solo
 * e che a un salto risponde con un ritardo. Un cursore che si limita a seguire il valore che gli arriva
 * combatte col dito (a ogni battito del cronometro la maniglia torna dove il lettore e' ancora) e, a
 * dito alzato, lampeggia indietro finche' il salto non e' arrivato. Lo scrubber ha per questo un modo
 * di stare in tre tempi, e tutto il resto ne discende ([FluidScrubbing.shownValue]):
 *
 *  * **a riposo** mostra la [position] che arriva da fuori, e la maniglia la segue con la molla della
 *    goccia (un battito a quattro al secondo diventa moto continuo);
 *  * **sotto il dito** mostra solo il dito. La [position] viene ignorata del tutto: la maniglia sta sulla
 *    posizione del dito 1:1 (relativa, come lo slider: afferrarla non la sposta, e un dito che sborda
 *    oltre l'estremita' e torna non la lascia indietro), e la lente e lo squash vengono dalla goccia;
 *  * **dopo il rilascio** mostra ancora il punto lasciato, finche' la [position] non lo ha raggiunto
 *    ([FluidScrubbing.caughtUp]) o passa [FluidMotion.ScrubberHoldMillis]: un seek rifiutato non deve
 *    tenere la maniglia dove nessuno sta riproducendo. Niente ritorno e niente lampo.
 *
 * **Le chiamate**, nell'ordine in cui avvengono in un trascinamento: [onScrubStart] quando il dito
 * prende la maniglia; [onScrub] a ogni valore nuovo sotto il dito (a ogni passo, se e' a passi): serve a
 * chi mostra un'anteprima viva — un fotogramma del radar, una miniatura — e a chi mette in pausa;
 * [onSeek] **una volta sola**, al rilascio, col valore finale; [onScrubEnd] subito dopo, o da sola se la
 * coroutine del gesto e' stata cancellata (niente salto, e il lettore puo' riprendere). Compose non
 * distingue un annullo di sistema (`ACTION_CANCEL`) e un modificatore tolto a meta' gesto da un
 * sollevamento: li recapita come un sollevamento, e lo scrubber li tratta come tale, saltando al punto in cui
 * il dito era. Un
 * **tocco sulla pista** e' un salto: la maniglia ci viaggia con la molla dello slider e c'e' solo
 * [onSeek]. Lo stesso per la tastiera e per TalkBack.
 *
 * **Continuo o a passi.** Con `step = 0` ogni punto della pista e' un valore, e non c'e' aptica durante
 * il moto: un brano non ha tacche, e un tick ogni tanto sotto il dito sarebbe un ronzio. Con `step > 0`
 * (la cronologia del radar: `valueRange = 0f..12f`, `step = 1f`) il valore cade sul multiplo piu' vicino,
 * un puntino per passo segna la pista (fino a [FluidScrubberDefaults.MaxStepDots]) e c'e' un `Tick` per
 * passo attraversato, con la rete dei 55 ms dei gesti di `FluidHapticDetents`. In entrambi i casi arrivare
 * a un'estremita' suona un `Threshold`, che non subisce nessuna rete; un tocco sulla pista continua suona
 * un `Tap` e, se e' proprio un'estremita', solo il `Threshold`: un momento, un colpo.
 *
 * **Accessibilita'.** Per TalkBack e' uno slider: un nodo solo, con [contentDescription], il valore a voce
 * ([valueDescription]: di serie «1:05 di 3:20»), l'intervallo (con i passi se e' a passi) e
 * `setProgress`; piu' due azioni, [stepBackLabel] e [stepForwardLabel], per muoversi di [keyStep] senza
 * dover indovinare una percentuale su una registrazione di ore. Da tastiera e D-pad: sinistra e destra
 * (specchiate da destra a sinistra) di [keyStep], pagina su e giu' di un decimo, inizio e fine. Il fuoco
 * e' un anello dell'accento sulla maniglia. Con le animazioni ridotte la maniglia non viaggia: si posa.
 *
 * Senza RenderEffect (prima di Android 12) la maniglia e' una pillola bianca con l'ombra su una pista
 * piena, come per lo slider: lo stesso scrubber senza l'unica cosa che non puo' avere.
 *
 * **Quello che lo scrubber non fa**: non sa cos'e' un secondo (il tempo si formatta con [startLabel],
 * [endLabel] e [valueDescription], e [FluidScrubbing] ha i formati di casa per i millisecondi), non
 * riproduce niente, e non e' un indicatore: per una barra che sta e basta c'e' `FluidProgressBar`.
 *
 * @param position dove e' la riproduzione, nell'unita' di [valueRange]. Una lambda e non un valore, come
 *   `FluidSpokenText`: a quattro battiti al secondo (o trenta) un parametro ricomporrebbe il chiamante a
 *   ogni battito, e letta nel disegno invalida solo il disegno. C'e' un overload che prende un `Float`.
 * @param onSeek il salto, **una volta**: il valore su cui il dito si e' fermato (sul passo, se e' a passi),
 *   o quello del tocco, del tasto, di TalkBack. Dentro [valueRange].
 * @param valueRange l'intervallo, nell'unita' dell'app. Vuoto o al contrario: e' un intervallo di un solo
 *   punto (un brano di cui non si conosce ancora la durata non divide per zero).
 * @param enabled spento non risponde (il lettore non e' pronto) e per TalkBack e' disattivato.
 * @param buffered fino a dove e' pronto, nell'unita' di [valueRange]: un riempimento piu' chiaro della pista
 *   e piu' scuro del velo, sotto quello dell'accento. Nullo: niente. Anche questo una lambda.
 * @param step il passo, nell'unita' di [valueRange]: 0 e' continuo. Se l'intervallo non e' un multiplo, anche
 *   l'estremita' e' un valore.
 * @param marks punti di riferimento sulla pista, nell'unita' di [valueRange]: l'«adesso» di una cronologia,
 *   i capitoli. Un punto sotto la pista, piu' grande dei passi.
 * @param onScrubStart il dito ha preso la maniglia. Non per un tocco.
 * @param onScrub il valore sotto il dito e' cambiato: dal primo movimento al rilascio, mai dopo.
 * @param onScrubEnd il dito ha lasciato la maniglia, con un salto (dopo [onSeek]) o senza (annullato).
 * @param startLabel il testo sotto la pista a inizio riga, dal valore *mostrato* (sotto il dito e' il suo,
 *   non quello del lettore): [FluidScrubbing.Elapsed]. Nullo: niente. Con tutti e due i testi nulli lo
 *   scrubber e' la sola riga da 48 dp.
 * @param endLabel il testo a fine riga: [FluidScrubbing.Remaining], [FluidScrubbing.Total].
 * @param contentDescription il nome per TalkBack.
 * @param valueDescription il valore a voce, dal valore mostrato; di serie «1:05 di 3:20» per i millisecondi.
 *   Per un'altra unita' va passato, o direbbe un orario che non e' (la cronologia di un radar dice
 *   «meno un'ora e quaranta»).
 * @param keyStep di quanto si muovono la tastiera e le due azioni di TalkBack, nell'unita' di [valueRange].
 *   Zero: un passo se e' a passi, il cinque per cento dell'intervallo se e' continuo (su una registrazione
 *   di ore e' troppo, e allora lo dice l'app: dieci secondi).
 * @param backdrop cio' che la maniglia rifrange, sotto la copia della pista. Di serie la superficie in
 *   scope, come per lo slider.
 */
@Composable
fun FluidScrubber(
  position: () -> Float,
  onSeek: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  buffered: (() -> Float)? = null,
  step: Float = 0f,
  marks: List<Float> = emptyList(),
  onScrubStart: (() -> Unit)? = null,
  onScrub: ((Float) -> Unit)? = null,
  onScrubEnd: (() -> Unit)? = null,
  startLabel: FluidScrubberFormat? = null,
  endLabel: FluidScrubberFormat? = null,
  contentDescription: String = FluidScrubberDefaults.ContentDescription,
  valueDescription: FluidScrubberFormat = FluidScrubbing.Spoken,
  keyStep: Float = 0f,
  stepBackLabel: String = FluidScrubberDefaults.StepBackLabel,
  stepForwardLabel: String = FluidScrubberDefaults.StepForwardLabel,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  val scheme = MaterialTheme.colorScheme
  val dark = GlassDefaults.isDarkSurface()
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val scope = rememberCoroutineScope()
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()

  val accentColor = scheme.primary
  // Gli stessi alpha dello slider: la pista cade dove cade il grigio della sorgente. Il pronto sta a meta'
  // fra la pista e l'accento, e si vede come un pieno anche sul velo piu' scuro.
  val trackColor = scheme.onSurface.copy(alpha = if (dark) TrackAlphaDark else TrackAlphaLight)
  val bufferedColor = scheme.onSurface.copy(alpha = if (dark) BufferedAlphaDark else BufferedAlphaLight)
  val stopIdleColor = scheme.onSurface.copy(alpha = if (dark) 0.38f else 0.30f)

  // Letto *ora*, non catturato allora: un trascinamento sopravvive alle ricomposizioni e le sue lambda
  // vivono piu' di quello che hanno chiuso (lo stesso errore e' annotato in `FluidSlider`).
  val currentPosition by rememberUpdatedState(position)
  val currentBuffered by rememberUpdatedState(buffered)
  val currentOnSeek by rememberUpdatedState(onSeek)
  val currentOnScrubStart by rememberUpdatedState(onScrubStart)
  val currentOnScrub by rememberUpdatedState(onScrub)
  val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
  val currentRange by rememberUpdatedState(FluidScrubbing.normalized(valueRange))
  val currentStep by rememberUpdatedState(step)
  val currentKeyStep by rememberUpdatedState(keyStep)
  val currentHaptics by rememberUpdatedState(haptics)
  val currentReducedMotion by rememberUpdatedState(reducedMotion)
  val currentIsLtr by rememberUpdatedState(isLtr)

  val hold = remember { FluidScrubberHold() }

  fun fractionOf(value: Float) = FluidScrubbing.fraction(value, currentRange)
  fun shownValue() = FluidScrubbing.shownValue(hold.phase, hold.value, currentPosition())

  // Dove la maniglia sta andando: il punto tenuto se c'e', la posizione da fuori se no.
  val targetFraction: () -> Float = { fractionOf(shownValue()) }

  // Il punto di partenza e' la posizione *adesso*, letta senza farsene invalidare: dentro `remember` una
  // lettura di stato registrerebbe la composizione, e allora ogni battito del lettore la ricomporrebbe.
  val dragAnimation = remember(scope) {
    GlassDragAnimation(
      animationScope = scope,
      initialValue = Snapshot.withoutReadObservation { fractionOf(currentPosition()) },
      valueRange = 0f..1f,
      visibilityThreshold = 0.001f,
      initialScale = 1f,
      pressedScale = 1.5f,
    )
  }

  // Dove si vede la maniglia: sotto il dito, a un valore continuo, esattamente dove sta il dito; da
  // ferma, e quando viaggia, la molla della goccia. Dopo il rilascio il dito resta la verita' finche' la
  // molla non l'ha raggiunto (la passa lui, con uno `snapToValue`): niente fotogramma in cui la maniglia
  // torna indietro della distanza che la molla aveva da recuperare, e niente corsa condivisa con altri
  // stati — il dito e la molla coincidono, e da li' in poi e' la molla.
  val drawnFraction: () -> Float = {
    val held = fractionOf(hold.value)
    if (hold.direct && abs(dragAnimation.progress - held) > SyncEpsilon) held else dragAnimation.progress
  }

  // La maniglia segue il punto mostrato: il dito mentre lo si tiene (e' questo `updateValue` che da' alla
  // goccia la velocita' da cui nasce lo squash), il punto lasciato dopo, la posizione da fuori a riposo.
  // Col movimento ridotto non viaggia: si posa.
  LaunchedEffect(dragAnimation, reducedMotion) {
    snapshotFlow { targetFraction() }.collectLatest { target ->
      if (abs(dragAnimation.targetValue - target) > SyncEpsilon) {
        if (reducedMotion) dragAnimation.snapToValue(target) else dragAnimation.updateValue(target)
      }
    }
  }

  // Il fermo dopo un rilascio o un salto: finisce quando la posizione da fuori ha raggiunto il punto, o
  // dopo [FluidMotion.ScrubberHoldMillis]. La chiave cambia a ogni gesto, quindi un fermo vecchio non
  // spegne quello nuovo; e la prima lettura avviene dopo che il fotogramma ha disegnato il punto.
  val generation = hold.generation
  LaunchedEffect(generation) {
    if (hold.phase != FluidScrubberPhase.Settling) return@LaunchedEffect
    withTimeoutOrNull(FluidMotion.ScrubberHoldMillis) {
      snapshotFlow {
        FluidScrubbing.caughtUp(currentPosition(), hold.value, hold.origin, currentRange, currentStep)
      }.first { it }
    }
    // Fra il momento in cui un gesto nuovo cambia lo stato e quello in cui la composizione cancella questo
    // fermo c'e' un fotogramma: se in quel fotogramma il fermo finisse, spegnerebbe il gesto nuovo.
    if (hold.generation == generation && hold.phase == FluidScrubberPhase.Settling) hold.clear()
  }

  val notches = remember { FluidSliderNotchGate() }

  val trackBackdrop = rememberLayerBackdrop()
  // La pista, aperta: schiacciata a due terzi in orizzontale e a niente in verticale a riposo, e intera
  // sotto il dito. Quello che la lente mostra non e' un'immagine della pista ma la pista che si dispiega.
  val unfoldedTrack: DrawScope.(DrawScope.() -> Unit) -> Unit = remember(dragAnimation) {
    { drawTrack ->
      val progress = dragAnimation.pressProgress
      scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) { drawTrack() }
    }
  }
  val thumbBackdrop: Backdrop = rememberCombinedBackdrop(
    backdrop.backdrop,
    rememberBackdrop(trackBackdrop, unfoldedTrack),
  )

  /**
   * Un salto: un tocco sulla pista, un tasto, TalkBack. La maniglia viaggia al punto (con [travel], come
   * quella dello slider al tocco; senza, la porta il filo che segue il punto mostrato) e se ne fa un
   * [onSeek] solo. Dal momento in cui il salto e' deciso il punto e' tenuto: la posizione da fuori non lo
   * sposta finche' non lo ha raggiunto.
   */
  fun jumpTo(raw: Float, travel: Boolean) {
    val range = currentRange
    val stepNow = currentStep
    val target = FluidScrubbing.snap(raw, range, stepNow)
    val before = shownValue()
    val beforeFraction = fractionOf(before)
    val landedAt = fractionOf(target)
    hold.jump(target, from = currentPosition())
    if (travel) {
      if (currentReducedMotion) dragAnimation.snapToValue(landedAt) else dragAnimation.animateToValue(landedAt)
      val changed = stepNow > 0f &&
        FluidScrubbing.stepIndex(before, range, stepNow) != FluidScrubbing.stepIndex(target, range, stepNow)
      FluidScrubbing.tapHaptic(
        edge = FluidScrubbing.edgeEntered(beforeFraction, landedAt),
        stepped = stepNow > 0f,
        stepChanged = changed,
      )?.let { currentHaptics.play(it) }
    }
    currentOnSeek(target)
  }

  val gestures = if (!enabled) {
    Modifier
  } else {
    // Chiavi: nessuna, come lo slider. Una chiave che cambia a meta' gesto (la larghezza, se la riga si
    // ridimensiona mentre la si trascina) rimette a zero il gesto sotto il dito: quello che puo' cambiare
    // si legge dal gesto stesso, a ogni evento.
    Modifier.pointerInput(Unit) {
      val edge = SliderEdge.toPx()
      val thumbPx = ThumbWidth.toPx()
      val grabReach = thumbPx / 2f + SliderGrabMargin.toPx()

      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        val trackPx = (width - edge * 2f).coerceAtLeast(1f)
        // Tutto si legge dall'inizio della riga: da destra a sinistra si specchia la x, una volta, qui.
        fun startX(x: Float) = if (currentIsLtr) x else width - x
        fun valueAt(fraction: Float) = FluidScrubbing.value(fraction, currentRange)

        val thumbCentre = edge + fluidSliderThumbCentre(drawnFraction(), trackPx, thumbPx)
        val onThumb = abs(startX(down.position.x) - thumbCentre) <= grabReach

        // Il punto da cui si misura il resto del gesto: dito e frazione in quel momento. Si calcola da
        // qui e non dallo spostamento del singolo evento, cosi' un dito che esce dalla pista e rientra
        // non lascia la maniglia indietro.
        var anchorX = startX(down.position.x)
        var anchorFraction = targetFraction()
        var claimedFromTrack = false
        var uptime = down.uptimeMillis

        if (onThumb) {
          // Il dito ha la maniglia, e un gesto che ha la maniglia non e' di nessun altro.
          down.consume()
        } else {
          // Sulla pista nuda: un tocco, un trascinamento di lato, o qualcuno che scorre la pagina.
          var travel = Offset.Zero
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == down.id } ?: return@awaitEachGesture
            // Qualcun altro l'ha preso: la pagina ha cominciato a scorrere su un gesto verticale.
            if (change.isConsumed) return@awaitEachGesture
            if (!change.pressed) {
              change.consume()
              jumpTo(valueAt(fluidSliderFractionAt(startX(down.position.x), edge, trackPx)), travel = true)
              return@awaitEachGesture
            }
            travel += change.positionChange()
            when (fluidSliderClaim(travel, viewConfiguration.touchSlop)) {
              FluidSliderClaim.Slider -> {
                change.consume()
                anchorX = startX(change.position.x)
                anchorFraction = fluidSliderFractionAt(anchorX, edge, trackPx)
                claimedFromTrack = true
                uptime = change.uptimeMillis
                break
              }
              FluidSliderClaim.Page -> return@awaitEachGesture
              FluidSliderClaim.Undecided -> Unit
            }
          }
        }

        // Da qui il gesto e' dello scrubber. Il punto di partenza e' quello che si vedeva: preso dalla
        // pista, la maniglia viene al dito subito, e il valore che cambia e' il primo del trascinamento.
        val startValue = shownValue()
        var lastFraction = fractionOf(startValue)
        hold.grab(startValue, direct = currentStep <= 0f)
        notches.restart(FluidScrubbing.stepIndex(startValue, currentRange, currentStep))
        dragAnimation.press()
        currentOnScrubStart?.invoke()

        fun scrubTo(rawFraction: Float, uptimeMillis: Long) {
          val range = currentRange
          val stepNow = currentStep
          val value = FluidScrubbing.snap(valueAt(rawFraction), range, stepNow)
          if (value == hold.value) return
          hold.move(value)
          val fraction = fractionOf(value)
          val edgeHit = FluidScrubbing.edgeEntered(lastFraction, fraction)
          lastFraction = fraction
          // Una tacca suona quando il valore ci si posa, non finche' ci sta; l'estremita' passa sempre.
          val landed = stepNow > 0f &&
            notches.land(FluidScrubbing.stepIndex(value, range, stepNow), uptimeMillis)
          FluidScrubbing.dragHaptic(edgeHit, stepNow > 0f, landed)?.let { currentHaptics.play(it) }
          currentOnScrub?.invoke(value)
        }

        var pointer = down.id
        var lifted = false
        try {
          if (claimedFromTrack) scrubTo(anchorFraction, uptime)
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == pointer }
              ?: event.changes.fastFirstOrNull { it.pressed }?.also {
                // Un altro dito raccoglie il gesto: da qui si misura dal suo punto, senza salti.
                pointer = it.id
                anchorX = startX(it.position.x)
                anchorFraction = targetFraction()
              }
              ?: break
            if (!change.pressed) {
              change.consume()
              break
            }
            uptime = change.uptimeMillis
            if (change.positionChanged()) {
              // Non limitata: un dito che sborda oltre l'estremita' e torna indietro non muove la
              // maniglia finche' non ripassa dal punto da cui e' uscito, come se la tenesse.
              val raw = anchorFraction + (startX(change.position.x) - anchorX) / trackPx
              scrubTo(raw.fastCoerceIn(0f, 1f), uptime)
            }
            // I due assi, ogni cambiamento, fino al rilascio: la pagina sotto uno scrubber che si
            // trascina non si muove, qualunque sia la deriva del dito.
            change.consume()
          }
          lifted = true
        } finally {
          if (lifted) {
            // Il dito ha lasciato il punto: da qui e' un punto tenuto, e la molla viene a posarsi
            // dov'e' il dito (a un valore continuo: a passi ci arriva da sola, per il filo).
            val value = hold.value
            hold.land(value, from = currentPosition())
            if (currentStep <= 0f) dragAnimation.snapToValue(fractionOf(value))
            dragAnimation.release()
            currentOnSeek(value)
          } else {
            // La coroutine e' stata cancellata senza che Compose recapitasse un sollevamento (un
            // ACTION_CANCEL di sistema o un modificatore tolto lo recapitano, e arrivano dall'altra
            // parte): niente salto, la maniglia torna alla posizione che il lettore dice.
            hold.clear()
            dragAnimation.release()
          }
          currentOnScrubEnd?.invoke()
        }
      }
    }
  }

  // La tastiera e il D-pad: un tasto un salto. Su e giu' non si prendono: sono del fuoco, e uno scrubber
  // che non lascia uscire un D-pad in verticale e' una trappola.
  val keys = Modifier.onKeyEvent { event ->
    if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
    val range = currentRange
    val stepNow = currentStep
    val forward = if (currentIsLtr) Key.DirectionRight else Key.DirectionLeft
    val backward = if (currentIsLtr) Key.DirectionLeft else Key.DirectionRight
    val shown = shownValue()
    val target = when (event.key) {
      forward -> FluidScrubbing.nudge(shown, range, stepNow, currentKeyStep, +1, page = false)
      backward -> FluidScrubbing.nudge(shown, range, stepNow, currentKeyStep, -1, page = false)
      Key.PageUp -> FluidScrubbing.nudge(shown, range, stepNow, currentKeyStep, +1, page = true)
      Key.PageDown -> FluidScrubbing.nudge(shown, range, stepNow, currentKeyStep, -1, page = true)
      Key.MoveHome -> range.start
      Key.MoveEnd -> range.endInclusive
      else -> return@onKeyEvent false
    }
    jumpTo(target, travel = false)
    true
  }

  // I testi sotto la pista: dal valore mostrato, e invalidano solo la loro riga, che cambia quando cambia
  // la stringa (una volta al secondo per un orologio) e non a ogni battito del lettore.
  val startText: State<String>? = remember(startLabel) {
    startLabel?.let { format -> derivedStateOf { format(shownValue(), currentRange) } }
  }
  val endText: State<String>? = remember(endLabel) {
    endLabel?.let { format -> derivedStateOf { format(shownValue(), currentRange) } }
  }

  Column(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      .semantics {
        this.contentDescription = contentDescription
        val range = currentRange
        val shown = FluidScrubbing.clamp(shownValue(), range)
        stateDescription = valueDescription(shown, range)
        // Uno slider, per chi lo ascolta: il valore dentro il suo intervallo (coi passi, se e' a passi),
        // e l'azione per impostarlo.
        progressBarRangeInfo = ProgressBarRangeInfo(
          shown,
          range,
          FluidScrubbing.interiorSteps(range, currentStep),
        )
        if (enabled) {
          setProgress { target ->
            jumpTo(target, travel = false)
            true
          }
          customActions = listOf(
            CustomAccessibilityAction(stepBackLabel) {
              jumpTo(FluidScrubbing.nudge(shownValue(), currentRange, currentStep, currentKeyStep, -1, page = false), travel = false)
              true
            },
            CustomAccessibilityAction(stepForwardLabel) {
              jumpTo(FluidScrubbing.nudge(shownValue(), currentRange, currentStep, currentKeyStep, +1, page = false), travel = false)
              true
            },
          )
        } else {
          disabled()
        }
      }
      // Prima di `focusable`: il gestore di tasti deve stare sopra il nodo che prende il fuoco, o il tasto
      // gli arriva dopo che il nodo l'ha lasciato passare.
      .then(keys)
      .focusable(enabled, interactionSource),
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        // Alto quanto un dito: la pista e' di 6 dp, ma il bersaglio no.
        .height(SliderHeight)
        // I gesti prima del margine, cosi' anche lui e' della riga.
        .then(gestures)
        // La maniglia sporgeva di un quarto oltre le estremita' della pista, nella sorgente. Qui lo spazio
        // glielo si lascia dentro, e `alpha` (che ritaglia ai bordi) non la mozza a fine corsa.
        .padding(horizontal = SliderEdge),
      contentAlignment = Alignment.CenterStart,
    ) {
      val trackPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
      val thumbPx = with(density) { ThumbWidth.toPx() }
      val dots = step > 0f && FluidScrubbing.stepCount(valueRange, step) <= FluidScrubberDefaults.MaxStepDots

      // 1. I puntini dei passi e i punti di riferimento, sotto la pista e fuori dalla registrazione: la
      // maniglia non li piega, e non devono fare parte di quello che la pista e' per la lente.
      if (dots || marks.isNotEmpty()) {
        Box(
          Modifier
            .fillMaxSize()
            .drawBehind {
              val range = currentRange
              val y = size.height / 2f + (TrackHeight / 2 + StopGap).toPx()
              val reached = drawnFraction() + SyncEpsilon
              val mirror = !currentIsLtr
              fun dot(value: Float, radius: Float) {
                val fraction = FluidScrubbing.fraction(value, range)
                // La stessa posizione che prende il centro della maniglia a quel valore: un punto sta
                // sotto la maniglia quando il valore ci si posa, anche vicino alle estremita'.
                val centre = fluidSliderThumbCentre(fraction, size.width, thumbPx)
                drawCircle(
                  color = if (fraction <= reached) accentColor else stopIdleColor,
                  radius = radius,
                  center = Offset(if (mirror) size.width - centre else centre, y),
                )
              }
              if (dots) {
                val gaps = FluidScrubbing.stepCount(range, step)
                for (i in 0..gaps) dot(min(range.start + i * step, range.endInclusive), StopSize.toPx() / 2f)
              }
              for (mark in marks) dot(mark, MarkSize.toPx() / 2f)
            },
        )
      }

      // 2. La pista, registrata: il velo, il pronto e l'accento, e la lente piega il loro bordo.
      Box(
        Modifier
          .fillMaxWidth()
          .height(TrackHeight)
          .layerBackdrop(trackBackdrop),
      ) {
        Box(
          Modifier
            .fillMaxSize()
            .clip(FluidCapsuleShape)
            .drawBehind { drawRect(trackColor) },
        )
        if (buffered != null) {
          Box(
            Modifier
              .clip(FluidCapsuleShape)
              .drawBehind { drawRect(bufferedColor) }
              .height(TrackHeight)
              .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val reach = currentBuffered?.let { fractionOf(it()) } ?: 0f
                val width = (constraints.maxWidth * reach.fastCoerceIn(0f, 1f)).fastRoundToInt()
                layout(width, placeable.height) { placeable.place(0, 0) }
              },
          )
        }
        Box(
          Modifier
            .clip(FluidCapsuleShape)
            .drawBehind { drawRect(accentColor) }
            .height(TrackHeight)
            .layout { measurable, constraints ->
              // Per larghezza e non per scala: una scala schiaccerebbe anche la testa tonda del
              // riempimento, e la pista e' sottile abbastanza da far vedere la differenza.
              val placeable = measurable.measure(constraints)
              val width = (constraints.maxWidth * drawnFraction().fastCoerceIn(0f, 1f)).fastRoundToInt()
              layout(width, placeable.height) { placeable.place(0, 0) }
            },
        )
      }

      // 3. La maniglia: la goccia dello slider.
      Box(
        Modifier
          .graphicsLayer {
            // Nel layer, non nel layout: spostarla invalida un disegno e basta, e il renderer inverte
            // questa traslazione quando campiona, quindi in ogni punto della corsa la lente ha in mano
            // l'immagine che la pista ha davvero dietro.
            val centre = fluidSliderThumbCentre(drawnFraction(), trackPx, thumbPx)
            translationX = (centre - thumbPx / 2f) * if (isLtr) 1f else -1f
          }
          .then(
            if (isRenderEffectSupported()) {
              // La goccia di Kyant (`glass/interaction/GlassDrop.kt`) coi numeri del pomello dello
              // slider: lente 10/14 dp e squash dal modulo della velocita'.
              Modifier.glassDrop(
                backdrop = thumbBackdrop,
                drag = dragAnimation,
                lensSize = GlassDropLens.Slider,
                squashDivisor = GlassDropRecipe.SquashDivisor,
                squashSigned = false,
                reducedMotion = { currentReducedMotion },
              )
            } else {
              Modifier
                .graphicsLayer {
                  val swell = 1f + FallbackSwell * dragAnimation.pressProgress
                  scaleX = swell
                  scaleY = swell
                }
                .shadow(2.dp, FluidCapsuleShape)
                .clip(FluidCapsuleShape)
                .drawBehind { drawRect(Color.White) }
            },
          )
          .drawWithContent {
            drawContent()
            // Il fuoco da tastiera: un anello dell'accento dentro il bordo della pillola, mai fuori, dove
            // il primo antenato che ritaglia lo mozzerebbe. Al tocco il nodo non prende il fuoco.
            if (focused) {
              val ring = FocusRingWidth.toPx()
              drawRoundRect(
                color = accentColor,
                topLeft = Offset(ring / 2f, ring / 2f),
                size = Size(size.width - ring, size.height - ring),
                cornerRadius = CornerRadius((size.height - ring) / 2f),
                style = Stroke(ring),
              )
            }
          }
          .size(ThumbWidth, ThumbHeight),
      )
    }

    if (startText != null || endText != null) {
      ScrubberLabels(start = startText, end = endText)
    }
  }
}

/** [FluidScrubber] con la posizione data come valore: per chi la posizione la cambia di rado (una cronologia a passi). */
@Composable
fun FluidScrubber(
  position: Float,
  onSeek: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  buffered: (() -> Float)? = null,
  step: Float = 0f,
  marks: List<Float> = emptyList(),
  onScrubStart: (() -> Unit)? = null,
  onScrub: ((Float) -> Unit)? = null,
  onScrubEnd: (() -> Unit)? = null,
  startLabel: FluidScrubberFormat? = null,
  endLabel: FluidScrubberFormat? = null,
  contentDescription: String = FluidScrubberDefaults.ContentDescription,
  valueDescription: FluidScrubberFormat = FluidScrubbing.Spoken,
  keyStep: Float = 0f,
  stepBackLabel: String = FluidScrubberDefaults.StepBackLabel,
  stepForwardLabel: String = FluidScrubberDefaults.StepForwardLabel,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  FluidScrubber(
    position = { position },
    onSeek = onSeek,
    modifier = modifier,
    valueRange = valueRange,
    enabled = enabled,
    buffered = buffered,
    step = step,
    marks = marks,
    onScrubStart = onScrubStart,
    onScrub = onScrub,
    onScrubEnd = onScrubEnd,
    startLabel = startLabel,
    endLabel = endLabel,
    contentDescription = contentDescription,
    valueDescription = valueDescription,
    keyStep = keyStep,
    stepBackLabel = stepBackLabel,
    stepForwardLabel = stepForwardLabel,
    backdrop = backdrop,
  )
}

/** Le due righe di testo sotto la pista, a inizio e a fine riga, lette dal loro stato e non dal chiamante. */
@Composable
private fun ScrubberLabels(start: State<String>?, end: State<String>?) {
  val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
  val color = MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = SliderEdge)
      // Tirata su verso la pista: la riga dello scrubber e' alta 48 dp per il dito, e il testo non deve
      // stare a ventun dp dalla pista che racconta. Si dichiara piu' bassa di quanto e': non rubano il
      // tocco (non ne hanno), ma neanche lasciano uno spazio vuoto in fondo.
      .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val pull = LabelPull.roundToPx().coerceAtMost(placeable.height)
        layout(placeable.width, placeable.height - pull) { placeable.place(0, -pull) }
      }
      // Il valore e' gia' a voce nel nodo dello scrubber: due volte lo stesso numero e' rumore.
      .clearAndSetSemantics {},
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (start != null) Text(text = start.value, style = style, color = color, maxLines = 1)
    Spacer(Modifier.weight(1f))
    if (end != null) Text(text = end.value, style = style, color = color, maxLines = 1)
  }
}

/** Le misure, i testi e le soglie dello scrubber che un'app puo' voler conoscere. */
object FluidScrubberDefaults {

  /** Il nome per TalkBack, quando l'app non ne da' uno piu' preciso. */
  const val ContentDescription: String = "Posizione"

  /** Le due azioni di TalkBack per muoversi del `keyStep` di [FluidScrubber]. */
  const val StepBackLabel: String = "Indietro"
  const val StepForwardLabel: String = "Avanti"

  /**
   * Oltre questo numero di passi i puntini non si disegnano: sarebbero un tratteggio, e sotto un
   * polpastrello non si contano. Il passo c'e' lo stesso, e la maniglia ci si posa.
   */
  const val MaxStepDots: Int = 32
}

// ---------------------------------------------------------------------------------------------
// I conti dello scrubber, puri
// ---------------------------------------------------------------------------------------------
//
// Fuori dal composable per la ragione per cui lo e' lo slider: girano a ogni evento di un trascinamento e
// sono la parte che sbaglia in silenzio (`FluidScrubberTest`). In particolare la regola piu' importante,
// *quale valore si mostra*, e quando smette di esserci un valore tenuto.

/**
 * Cosa sta mostrando lo scrubber, in tre tempi. Vedi [FluidScrubbing.shownValue].
 */
internal enum class FluidScrubberPhase {
  /** Il valore e' quello che arriva da fuori. */
  Idle,

  /** Il dito tiene la maniglia: conta solo lui. */
  Dragging,

  /** Il dito ha lasciato un punto e la posizione da fuori non lo ha ancora raggiunto. */
  Settling,
}

/**
 * Il valore *tenuto*: quello del dito mentre lo si trascina, e dopo il rilascio quello lasciato, finche'
 * la posizione da fuori non lo ha raggiunto.
 *
 * Una classe di stato di snapshot e non tre variabili del composable perche' lo leggono il disegno, il
 * layout, la semantica e il filo che muove la molla, ognuno nella sua fase, e nessuno deve ricomporre.
 */
@Stable
internal class FluidScrubberHold {

  var phase by mutableStateOf(FluidScrubberPhase.Idle)
    private set

  /** Il valore tenuto: quello del dito, o quello lasciato. Senza senso a riposo. */
  var value by mutableFloatStateOf(0f)
    private set

  /**
   * Vero dal momento in cui il dito prende la maniglia di uno scrubber continuo, finche' un salto o la fine del
   * fermo non lo spengono: la maniglia si disegna sul valore tenuto e non sulla molla, che a un dito veloce sta
   * qualche punto percentuale indietro. Vedi `drawnFraction` nel composable.
   */
  var direct by mutableStateOf(false)
    private set

  /** Dov'era la posizione da fuori quando il punto e' stato lasciato: da qui si misura quanta strada manca. */
  var origin: Float = 0f
    private set

  /** Cambia a ogni gesto nuovo: la chiave del fermo, perche' un fermo vecchio non spenga quello nuovo. */
  var generation by mutableIntStateOf(0)
    private set

  val active: Boolean get() = phase != FluidScrubberPhase.Idle

  /** Il dito prende la maniglia, che sta a [at]. [direct]: la maniglia segue il dito senza la molla. */
  fun grab(at: Float, direct: Boolean) {
    phase = FluidScrubberPhase.Dragging
    value = at
    this.direct = direct
    generation++
  }

  /** Il dito si e' spostato su [to]. */
  fun move(to: Float) {
    value = to
  }

  /** Il dito ha lasciato il punto [at]; la posizione da fuori era [from]. Il fermo parte. */
  fun land(at: Float, from: Float) {
    value = at
    origin = from
    phase = FluidScrubberPhase.Settling
    generation++
  }

  /** Un salto (un tocco, un tasto, TalkBack): un punto tenuto da subito, e la maniglia lo raggiunge con la molla. */
  fun jump(at: Float, from: Float) {
    direct = false
    land(at, from)
  }

  /** Il fermo e' finito, o il gesto e' stato annullato: da qui conta di nuovo la posizione da fuori. */
  fun clear() {
    phase = FluidScrubberPhase.Idle
    direct = false
  }
}

/**
 * I conti e i testi di [FluidScrubber]. Pubblico per i formati: un'app che mostra il tempo da se' accanto
 * alla barra usa gli stessi.
 */
object FluidScrubbing {

  /** Sotto questa frazione dell'intervallo (un centesimo di punto percentuale: meno di un pixel) due posizioni sono la stessa. */
  internal const val CatchUpEpsilon = 1e-4f

  /**
   * Quanto della strada chiesta dal salto deve aver fatto la posizione da fuori perche' si dica che
   * l'ha raggiunto: la meta'. E' relativo alla *distanza del salto* e non all'intervallo: un colpetto di
   * un minuto su una registrazione di diciannove ore e' lo 0,09% dell'intervallo, e una tolleranza in
   * frazioni lo darebbe per raggiunto prima ancora che il lettore si sia mosso — la maniglia tornerebbe
   * indietro di un minuto e poi ripartirebbe. Cosi', finche' la posizione e' ancora dov'era, non e' mai
   * raggiunto, e dopo il seek basta che sia piu' vicina al punto che a dov'era.
   */
  internal const val CatchUpShare = 0.5f

  /** Di quanto si muove una freccia senza un passo ne' un `keyStep`: il cinque per cento. */
  internal const val DefaultKeyFraction = 0.05f

  /** Pagina su e giu': un decimo, o il passo se e' piu' grande. */
  internal const val PageFraction = 0.1f

  // --- Testi -------------------------------------------------------------------------------------

  /**
   * Un tempo come lo scrive un orologio: «3:20», «59:59», «1:02:03». Da [millis] in millisecondi, per
   * difetto al secondo (un lettore a 3:19,9 e' ancora a 3:19) e mai sotto zero.
   */
  fun clock(millis: Long): String {
    val total = max(millis, 0L) / 1_000L
    val hours = total / 3_600L
    val minutes = (total % 3_600L) / 60L
    val seconds = total % 60L
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0L) "$hours:${minutes.toString().padStart(2, '0')}:$ss" else "$minutes:$ss"
  }

  /** Il tempo trascorso, «1:05». Per i millisecondi: per un'altra unita' si passa un formato proprio. */
  val Elapsed: FluidScrubberFormat = { position, range -> clock((position - range.start).toLong()) }

  /** Quanto manca, «−2:15», con il meno tipografico. */
  val Remaining: FluidScrubberFormat = { position, range ->
    "−" + clock((range.endInclusive - position).toLong())
  }

  /** La durata intera, «3:20». */
  val Total: FluidScrubberFormat = { _, range -> clock((range.endInclusive - range.start).toLong()) }

  /** Il valore a voce, «1:05 di 3:20»: il default di [FluidScrubber]. */
  val Spoken: FluidScrubberFormat = { position, range ->
    "${Elapsed(position, range)} di ${Total(position, range)}"
  }

  // --- Posizione e frazione ------------------------------------------------------------------------

  /** L'intervallo come lo si usa: uno vuoto, al contrario o con un NaN e' un intervallo di un solo punto. */
  internal fun normalized(range: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float> = when {
    range.start.isNaN() || range.endInclusive.isNaN() -> 0f..0f
    range.start > range.endInclusive -> range.start..range.start
    else -> range
  }

  /** Il valore tenuto dentro l'intervallo; un NaN e' l'inizio. */
  internal fun clamp(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val r = normalized(range)
    return if (value.isNaN()) r.start else value.coerceIn(r)
  }

  /** Dove sta un valore nel suo intervallo, da 0 a 1. Un intervallo di un solo punto e' sempre a 0. */
  internal fun fraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val r = normalized(range)
    val span = r.endInclusive - r.start
    if (span <= 0f || value.isNaN()) return 0f
    return ((value - r.start) / span).fastCoerceIn(0f, 1f)
  }

  /** Il valore di una frazione: l'inverso di [fraction]. L'estremita' e' l'estremita' esatta, senza un'ultima cifra di errore. */
  internal fun value(fraction: Float, range: ClosedFloatingPointRange<Float>): Float {
    val r = normalized(range)
    val span = r.endInclusive - r.start
    if (span <= 0f) return r.start
    val f = fraction.fastCoerceIn(0f, 1f)
    return if (f >= 1f) r.endInclusive else r.start + f * span
  }

  // --- Passi -----------------------------------------------------------------------------------------

  /**
   * Il valore sul passo piu' vicino, a partire dall'inizio dell'intervallo; con [step] zero (o un intervallo
   * di un punto) il valore stesso. Se l'intervallo non e' un multiplo del passo anche l'estremita' e' un
   * valore, cosi' la fine di un brano si raggiunge. A meta' fra due passi vince quello avanti.
   */
  internal fun snap(value: Float, range: ClosedFloatingPointRange<Float>, step: Float): Float {
    val r = normalized(range)
    val clamped = clamp(value, r)
    val span = r.endInclusive - r.start
    if (step <= 0f || span <= 0f) return clamped
    val k = floor((clamped - r.start) / step)
    val lower = r.start + k * step
    val upper = min(r.start + (k + 1f) * step, r.endInclusive)
    return if (clamped - lower < upper - clamped) lower else upper
  }

  /** Quanti intervalli fra un passo e l'altro: 0 se e' continuo. Un'estremita' che non e' un multiplo ne aggiunge uno. */
  internal fun stepCount(range: ClosedFloatingPointRange<Float>, step: Float): Int {
    val r = normalized(range)
    val span = r.endInclusive - r.start
    if (step <= 0f || span <= 0f) return 0
    return ceil(span / step - 1e-4f).toInt().coerceAtLeast(1)
  }

  /** I passi *interni* di un intervallo, come li dice `ProgressBarRangeInfo`: i valori meno le due estremita'. */
  internal fun interiorSteps(range: ClosedFloatingPointRange<Float>, step: Float): Int =
    (stepCount(range, step) - 1).coerceAtLeast(0)

  /** L'indice del passo su cui cade [value], da 0; -1 se e' continuo. */
  internal fun stepIndex(value: Float, range: ClosedFloatingPointRange<Float>, step: Float): Int {
    val gaps = stepCount(range, step)
    if (gaps == 0) return -1
    val r = normalized(range)
    val snapped = snap(value, r, step)
    if (snapped >= r.endInclusive) return gaps
    return ((snapped - r.start) / step).roundToInt().coerceIn(0, gaps)
  }

  /**
   * Dove porta una freccia, una pagina o un'azione di TalkBack: [direction] (+1 o -1) volte [keyStep], o il
   * passo se e' a passi, o il cinque per cento. Una pagina e' un decimo dell'intervallo, o di piu' se la
   * freccia e' gia' piu' grande. Parte dal valore *sul passo*, cosi' una posizione fra due passi non fa fare
   * a una freccia mezzo passo.
   */
  internal fun nudge(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    keyStep: Float,
    direction: Int,
    page: Boolean,
  ): Float {
    val r = normalized(range)
    val span = r.endInclusive - r.start
    if (span <= 0f) return r.start
    val unit = when {
      keyStep > 0f -> keyStep
      step > 0f -> step
      else -> span * DefaultKeyFraction
    }
    val amount = if (page) max(unit, span * PageFraction) else unit
    return snap(snap(value, r, step) + direction * amount, r, step)
  }

  // --- Quale valore, e quando smette il fermo ------------------------------------------------------------

  /**
   * **Il valore che si mostra**: a riposo quello che arriva da fuori, sotto il dito e dopo il rilascio quello
   * tenuto. E' la regola che impedisce al lettore di combattere col dito.
   */
  internal fun shownValue(phase: FluidScrubberPhase, held: Float, external: Float): Float =
    if (phase == FluidScrubberPhase.Idle) external else held

  /**
   * Se la posizione da fuori ha raggiunto il punto lasciato, e il fermo puo' finire.
   *
   * Dopo un salto la posizione da fuori resta dov'era finche' il lettore non ha finito di spostarsi, poi
   * salta al punto (e un po' piu' in la, per quanto e' durato il seek) e prosegue. Quindi: **raggiunto** quando
   * e' entro la meta' della strada del salto dal punto, che prima del seek non e' mai, e dopo e' sempre — anche
   * se il lettore ha scelto un punto vicino ma non uguale (un seek che si appoggia a un fotogramma chiave).
   * A passi e' raggiunto entro mezzo passo, che e' dove la posizione ci si poserebbe comunque. Un salto di
   * lunghezza zero e' raggiunto subito.
   *
   * @param external la posizione che arriva da fuori, adesso.
   * @param target il punto lasciato.
   * @param origin dov'era la posizione da fuori quando e' stato lasciato.
   */
  internal fun caughtUp(
    external: Float,
    target: Float,
    origin: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
  ): Boolean {
    val r = normalized(range)
    val span = r.endInclusive - r.start
    if (span <= 0f) return true
    val remaining = abs(fraction(external, r) - fraction(target, r))
    val tolerance = if (step > 0f) 0.5f * min(step / span, 1f) else CatchUpShare * abs(fraction(origin, r) - fraction(target, r))
    return remaining <= max(tolerance, CatchUpEpsilon)
  }

  // --- Aptica ---------------------------------------------------------------------------------------

  /**
   * Il dito e' appena arrivato a un'estremita' (frazioni 0..1). Arrivarci, non starci: afferrare la maniglia
   * in fondo non e' arrivare in fondo, e un dito fermo li' non suona a ogni evento.
   */
  internal fun edgeEntered(previous: Float, next: Float): Boolean =
    (next <= 0f && previous > 0f) || (next >= 1f && previous < 1f)

  /**
   * Cosa si sente mentre il dito trascina: l'estremita' passa sempre, un passo atterrato ([landed], gia'
   * passato per la rete dei 55 ms) suona un `Tick` se lo scrubber e' a passi, e un trascinamento continuo non
   * suona altro. Un momento, un colpo: l'estremita' che e' anche un passo non suona due volte.
   */
  internal fun dragHaptic(edge: Boolean, stepped: Boolean, landed: Boolean): FluidHapticEvent? = when {
    edge -> FluidHapticEvent.Threshold
    stepped && landed -> FluidHapticEvent.Tick
    else -> null
  }

  /**
   * Cosa si sente a un tocco sulla pista: l'estremita' se ci arriva, altrimenti un `Tick` se cambia passo (a
   * passi) o un `Tap` (continuo). Mai due: `Threshold` e `Tap` sono dello stesso momento, e la rete dei 70 ms
   * ne scarterebbe uno comunque.
   */
  internal fun tapHaptic(edge: Boolean, stepped: Boolean, stepChanged: Boolean): FluidHapticEvent? = when {
    edge -> FluidHapticEvent.Threshold
    stepped -> if (stepChanged) FluidHapticEvent.Tick else null
    else -> FluidHapticEvent.Tap
  }
}

// ---------------------------------------------------------------------------------------------
// Misure
// ---------------------------------------------------------------------------------------------
//
// Le stesse dello slider, che sono quelle della sorgente: la goccia e' una e le misure con lei. Private li'
// e private qui, perche' un terzo file non le importi da un secondo.

/** La pista, 6 dp: sottile apposta, la maniglia e' l'unica cosa che ha spessore. */
private val TrackHeight: Dp = 6.dp

/** 40 x 24, i numeri della sorgente: una pillola, non un cerchio. */
private val ThumbWidth: Dp = 40.dp
private val ThumbHeight: Dp = 24.dp

/** Il margine a ogni lato della riga, un quarto della maniglia: dove la sorgente la lasciava sporgere. */
private val SliderEdge: Dp = ThumbWidth / 4

/** Alto 48 dp: il bersaglio minimo, con la pista e la maniglia al centro. */
private val SliderHeight: Dp = 48.dp

/** Quanto fuori dalla maniglia un dito la prende ancora: sotto un polpastrello e' quasi tutta nascosta. */
private val SliderGrabMargin: Dp = 10.dp

/** I puntini dei passi: 2 dp, a 7 dp dal bordo della pista, come le tacche dello slider. */
private val StopSize: Dp = 2.dp
private val StopGap: Dp = 7.dp

/** I punti di riferimento (l'«adesso» di una cronologia, i capitoli): un punto da 5 dp, sulla stessa riga dei passi. */
private val MarkSize: Dp = 5.dp

/** Quanto i testi sotto la pista si tirano su verso di lei. */
private val LabelPull: Dp = 8.dp

/** Lo spessore dell'anello del fuoco da tastiera. */
private val FocusRingWidth: Dp = 2.dp

/**
 * Sotto questa distanza una maniglia e un valore sono lo stesso punto: la molla si ferma a 0.001, e il
 * valore che torna da fuori differisce dal bersaglio di un'unita' dell'ultimo bit.
 */
private const val SyncEpsilon = 1e-4f

/** Quanto si gonfia la maniglia di ripiego sotto il dito: un ripiego non ha la lente da far vedere. */
private const val FallbackSwell = 0.15f

/** Gli alpha di `onSurface` che riportano la pista dove cade il grigio della sorgente (quelli dello slider). */
private const val TrackAlphaLight = 0.12f
private const val TrackAlphaDark = 0.17f

/** Il pronto: il velo della pista piu' qualcosa, perche' si legga come un pieno fra il velo e l'accento. */
private const val BufferedAlphaLight = 0.26f
private const val BufferedAlphaDark = 0.36f
