/*
 * The thumb, the unfolded copy of the track it refracts, and the numbers that shape both are ported
 * from Kyant0's LiquidSlider.
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant0, Apache License 2.0
 *
 * It reached the engine through Pampa notes, which ported the catalogue's slider first (a
 * `LiquidSlider` written with the engine's public API only, so that moving it here would be a
 * copy and paste); the gesture on the whole row, the snap stops and the fade to the app's accent
 * are from there. Licensed under the Apache License, Version 2.0; the text is at
 * http://www.apache.org/licenses/LICENSE-2.0 and the changes are listed in
 * LICENSES/AndroidLiquidGlass.md.
 *
 * Portato e non reinventato, come `FluidSwitch` con il LiquidToggle. Lo slider che c'era prima era
 * una pista spessa da 30 dp con una lente da 26 dp sempre accesa: la stessa costruzione che
 * l'engine aveva gia' scartato per l'interruttore. Quello vero e' una pista sottile e una pillola
 * che a riposo e' bianca e opaca, e diventa vetro solo mentre il dito la tiene; cio' che la lente
 * rifrange e' la pagina piu' una copia della pista che si dispiega sotto il dito. Nessuna
 * disposizione di una superficie generica produce questo, e quindi si prende com'e'.
 *
 * Quello che e' cambiato, detto dove succede:
 *  - il colore e' l'accento dell'app, e il velo della pista viene dal tema (`onSurface` con un
 *    alpha ricalcolato perche' sulla superficie atterri dove atterra il grigio della sorgente);
 *  - lo stiramento con la velocita' usa il modulo e non il segno: nella sorgente un verso allunga la
 *    pillola e l'altro la accorcia, e uno slider che si rimpicciolisce se lo trascini a sinistra e'
 *    un difetto, non un carattere;
 *  - la pista e la maniglia stanno dentro i bordi del controllo (un margine di un quarto di
 *    maniglia a ogni lato, il punto in cui la sorgente sporgeva): nulla nell'engine disegna fuori dai
 *    propri limiti, e una maniglia che sporge viene mozzata dal primo contenitore che ritaglia;
 *  - ci sono `enabled`, la fine del gesto, le tacche di scatto con il loro tocco aptico, TalkBack,
 *    il movimento ridotto e un ripiego senza RenderEffect, perche' li hanno tutti gli altri controlli.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
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
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.haptics.FluidDetent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticDetents
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Uno slider di vetro: una pista sottile riempita con l'accento, e una pillola che a riposo e'
 * bianca e opaca e si fa lente mentre il dito la tiene.
 *
 * La costruzione e' tutta qui, e come per [FluidSwitch] e' il motivo per cui sembra vetro:
 *
 *  * **La pista e' una registrazione, non uno sfondo.** Si dipinge in un layer suo, e quel layer e'
 *    quello che la maniglia legge.
 *  * **La maniglia e' l'unica lastra.** A riposo e' una pillola bianca con un'ombra; tenuta, il
 *    bianco svanisce e resta una lente che rifrange la pagina e una *copia della pista che si
 *    dispiega*: schiacciata a niente in verticale e a due terzi in orizzontale a riposo, aperta
 *    del tutto sotto il dito. L'accento sotto la maniglia si apre davvero.
 *  * **Si allunga.** Trascinata in fretta si assottiglia lungo il moto e si gonfia di traverso, di
 *    quanto va veloce. E' la meta' di cio' che la fa sentire un oggetto spinto e non un numero
 *    ridisegnato.
 *
 * **La riga e' tutta del dito.** Il controllo e' alto 48 dp anche se la pista ne ha 6: un tocco
 * sulla pista porta li' la maniglia (con la molla, come nella sorgente) e un trascinamento partito
 * da lei la prende senza spostarla. La maniglia si afferra anche a dieci dp di distanza dal bordo,
 * perche' sotto un polpastrello e' quasi tutta nascosta.
 *
 * **Un trascinamento e' dello slider, tutto (2.11.0).** Una pagina che scorre e uno slider che si
 * trascina vogliono lo stesso dito, e vince chi legge per primo (il passaggio principale va dal
 * figlio al padre, e lo slider e' il figlio):
 *
 *  * un dito che cade **sulla maniglia** l'ha subito: ogni cambiamento di quel puntatore viene
 *    consumato, sui due assi, dal primo movimento al rilascio, cosi' la pagina non vede mai un
 *    movimento da cui scorrere;
 *  * un dito che cade **sulla pista nuda** potrebbe essere qualcuno che scorre. Lo slider se lo
 *    prende quando ha fatto mezzo slop di lato e piu' di lato che in verticale ([fluidSliderClaim]);
 *    se va prima in verticale e' della pagina, cosi' uno slider a tutta larghezza non diventa una
 *    fascia dello schermo da cui non si scorre. Sollevato senza essersi mosso e' un tocco.
 *
 * **Tacche.** Con [snapValues] il valore *si attacca*: entro [snapThreshold] (una frazione
 * dell'intervallo, mai un numero nelle unita' del valore) dal piu' vicino dei valori dati, il
 * valore e' esattamente quello e la maniglia ci resta, fuori e' libero. Sotto la pista un
 * puntino segna ognuno, e ogni volta che il valore ci si posa c'e' un solo tocco aptico. Con
 * `snapThreshold = 1f` ogni posizione cade sul valore piu' vicino: uno slider a gradini.
 * Senza tacche il tocco aptico e' una tacca ogni ventesimo dell'intervallo e un colpo piu'
 * deciso alle estremita', passando da `FluidHapticDetents`: a un dito veloce toccano molti piu'
 * gradini di quanti un motore ne sappia rispondere, e chiederli tutti da' un ronzio.
 *
 * Senza RenderEffect (prima di Android 12) la maniglia e' una pillola bianca con l'ombra su una
 * pista piena: lo stesso slider senza l'unica cosa che non puo' avere.
 *
 * @param snapValues i valori a cui il valore si attacca, dentro [valueRange]. Vuoto: nessuna tacca.
 * @param snapThreshold quanto lontano, come frazione dell'intervallo (0..1), un valore viene
 *   ancora attaccato alla sua tacca.
 * @param onValueChangeFinished alla fine di un trascinamento o di un tocco sulla pista: il momento
 *   di salvare, non a ogni passo.
 * @param contentDescription il nome dello slider per TalkBack.
 * @param stateDescription quello che TalkBack legge come valore («30 minuti»); senza, direbbe una
 *   percentuale della pista, che di un elenco di scatti non dice niente.
 * @param backdrop cio' che la maniglia rifrange, sotto la copia della pista. Di serie la superficie
 *   in scope: il canvas ambientale della schermata se ce l'ha, altrimenti la chrome, altrimenti
 *   niente — e anche allora la maniglia ha la pista da piegare, che e' la parte che conta.
 */
@Composable
fun FluidSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,
  snapValues: List<Float> = emptyList(),
  snapThreshold: Float = 0.03f,
  contentDescription: String? = null,
  stateDescription: String? = null,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  val scheme = MaterialTheme.colorScheme
  val dark = GlassDefaults.isDarkSurface()
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val scope = rememberCoroutineScope()

  val accentColor = scheme.primary
  // Il grigio della sorgente (0x787878 al 20%, 0x787880 al 36% sul tema scuro) preso dal tema. Gli
  // alpha non sono quelli: `onSurface` e' molto piu' scuro (o chiaro) di quel grigio, e allo stesso
  // alpha la pista verrebbe quasi il doppio piu' forte. Questi due lo fanno cadere dove cade
  // l'originale sulla superficie tipica, un bianco e un 0x1C.
  val trackColor = scheme.onSurface.copy(alpha = if (dark) TrackAlphaDark else TrackAlphaLight)
  val stopIdleColor = scheme.onSurface.copy(alpha = if (dark) 0.38f else 0.30f)

  // Letto *ora*, non catturato allora: un trascinamento sopravvive alle ricomposizioni e le sue
  // lambda vivono piu' di quello che hanno chiuso. Lo stesso bug e' annotato in FluidFoldingTabBar.
  val currentValue by rememberUpdatedState(value)
  val currentOnValueChange by rememberUpdatedState(onValueChange)
  val currentFinished by rememberUpdatedState(onValueChangeFinished)
  val currentRange by rememberUpdatedState(valueRange)
  val currentStops by rememberUpdatedState(snapValues)
  val currentThreshold by rememberUpdatedState(snapThreshold)
  val currentHaptics by rememberUpdatedState(haptics)
  val currentReducedMotion by rememberUpdatedState(reducedMotion)
  val currentIsLtr by rememberUpdatedState(isLtr)
  val fraction = fluidSliderFraction(value, valueRange)
  val currentFraction by rememberUpdatedState(fraction)

  // La molla vive nello spazio 0..1, non in quello del chiamante: la velocita' che ne esce e' in
  // "intervalli al secondo" per qualunque intervallo, e l'intervallo puo' cambiare sotto la molla
  // senza che questa debba rinascere.
  val dragAnimation = remember(scope) {
    GlassDragAnimation(
      animationScope = scope,
      initialValue = fraction,
      valueRange = 0f..1f,
      visibilityThreshold = 0.001f,
      initialScale = 1f,
      pressedScale = 1.5f,
    )
  }

  // La maniglia segue il valore del chiamante, sempre: dito, tocco, ripristino di una preferenza.
  // Cosi' un chiamante che il valore lo arrotonda, lo rifiuta o lo salva in ritardo vede la maniglia
  // dove il suo stato dice che e'. Col movimento ridotto non viaggia: si posa.
  LaunchedEffect(dragAnimation, reducedMotion) {
    snapshotFlow { currentFraction }.collectLatest { target ->
      if (abs(dragAnimation.targetValue - target) > SyncEpsilon) {
        if (reducedMotion) dragAnimation.snapToValue(target) else dragAnimation.updateValue(target)
      }
    }
  }

  val detents = remember { FluidHapticDetents() }
  val notches = remember { FluidSliderNotchGate() }

  val trackBackdrop = rememberLayerBackdrop()
  // La pista, aperta: schiacciata a due terzi in orizzontale e a niente in verticale a riposo, e
  // intera sotto il dito. Quello che la lente mostra non e' un'immagine della pista ma la pista
  // che si *dispiega*.
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

  val gestures = if (!enabled) {
    Modifier
  } else {
    // Chiavi: nessuna. Una chiave che cambia a meta' gesto (la larghezza, che cambia se la riga si
    // ridimensiona mentre la si trascina) rimette a zero il gesto sotto il dito. Tutto quello che
    // puo' cambiare si legge dal gesto stesso, ad ogni evento.
    Modifier.pointerInput(Unit) {
      val edge = SliderEdge.toPx()
      val thumbPx = ThumbWidth.toPx()
      val grabReach = thumbPx / 2f + SliderGrabMargin.toPx()

      /**
       * Il dito a [rawFraction] della pista: dove cade il valore, cosa si sente, cosa si comunica.
       * Restituisce la frazione su cui il valore si posa, che con le tacche non e' quella del dito.
       * Con [travel] la maniglia ci viaggia, dal tocco alla posizione, prima di comunicarla.
       */
      fun report(rawFraction: Float, uptimeMillis: Long, travel: Boolean, lastReported: FloatArray): Float {
        val range = currentRange
        val stops = currentStops
        val rawValue = fluidSliderValue(rawFraction, range)
        val stop = fluidSliderStopIndex(rawValue, stops, range, currentThreshold)
        val next = if (stop >= 0) stops[stop].fastCoerceIn(range.start, range.endInclusive) else rawValue
        val nextFraction = fluidSliderFraction(next, range)

        if (travel) {
          // Prima di dirlo al chiamante, come nella sorgente: la molla parte con il suo bersaglio.
          if (currentReducedMotion) dragAnimation.snapToValue(nextFraction) else dragAnimation.animateToValue(nextFraction)
        }

        val detent = detents.step(nextFraction, SliderTickSteps, uptimeMillis)
        val landed = notches.land(stop, uptimeMillis)
        when {
          // L'estremita' passa sempre, e se e' anche una tacca non suona due volte.
          detent == FluidDetent.Edge -> currentHaptics.play(FluidHapticEvent.Threshold)
          stops.isEmpty() -> if (detent == FluidDetent.Tick) currentHaptics.play(FluidHapticEvent.DragTick)
          landed -> currentHaptics.play(FluidHapticEvent.Tick)
        }

        if (next != lastReported[0]) {
          lastReported[0] = next
          currentOnValueChange(next)
        }
        return nextFraction
      }

      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val width = size.width.toFloat()
        val trackPx = (width - edge * 2f).coerceAtLeast(1f)
        // Tutto si legge dall'inizio della riga: da destra a sinistra si specchia la x, una volta, qui.
        fun startX(x: Float) = if (currentIsLtr) x else width - x
        val lastReported = floatArrayOf(currentValue)

        val thumbCentre = edge + fluidSliderThumbCentre(dragAnimation.progress, trackPx, thumbPx)
        val onThumb = abs(startX(down.position.x) - thumbCentre) <= grabReach

        // Il punto da cui si misura il resto del gesto: dito e frazione in quel momento. La
        // posizione si calcola da qui e non dallo spostamento del singolo evento, cosi' un dito che
        // esce dalla pista e rientra non lascia la maniglia indietro.
        var anchorX = startX(down.position.x)
        var anchorFraction = dragAnimation.targetValue
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
              detents.restartAt(dragAnimation.targetValue, SliderTickSteps)
              notches.restart(fluidSliderStopIndex(currentValue, currentStops, currentRange, currentThreshold))
              report(
                fluidSliderFractionAt(startX(down.position.x), edge, trackPx),
                change.uptimeMillis,
                travel = true,
                lastReported,
              )
              currentFinished?.invoke()
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

        dragAnimation.press()
        detents.restartAt(dragAnimation.targetValue, SliderTickSteps)
        notches.restart(fluidSliderStopIndex(currentValue, currentStops, currentRange, currentThreshold))
        var pointer = down.id
        try {
          // Preso dalla pista, la maniglia viene al dito adesso, non al prossimo movimento.
          if (claimedFromTrack) report(anchorFraction, uptime, travel = false, lastReported)
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.fastFirstOrNull { it.id == pointer }
              ?: event.changes.fastFirstOrNull { it.pressed }?.also {
                // Un altro dito raccoglie il gesto: da qui si misura dal suo punto, senza salti.
                pointer = it.id
                anchorX = startX(it.position.x)
                anchorFraction = dragAnimation.targetValue
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
              report(raw.fastCoerceIn(0f, 1f), uptime, travel = false, lastReported)
            }
            // I due assi, ogni cambiamento, fino al rilascio: la pagina sotto uno slider che si
            // trascina non si muove, qualunque sia la deriva del dito.
            change.consume()
          }
        } finally {
          dragAnimation.release()
          currentFinished?.invoke()
        }
      }
    }
  }

  BoxWithConstraints(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      // Alto quanto un dito: la pista e' di 6 dp, ma il bersaglio no.
      .height(SliderHeight)
      .semantics {
        if (contentDescription != null) this.contentDescription = contentDescription
        if (stateDescription != null) this.stateDescription = stateDescription
        // Uno slider, per chi lo ascolta: il valore dentro il suo intervallo, e l'azione per
        // impostarlo. Prima si presentava come un interruttore, che e' l'unica cosa che non e'.
        progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
        if (enabled) {
          setProgress { target ->
            val range = currentRange
            currentOnValueChange(
              fluidSliderSnap(target.coerceIn(range), currentStops, range, currentThreshold),
            )
            currentFinished?.invoke()
            true
          }
        } else {
          disabled()
        }
      }
      // I gesti prima del margine, cosi' anche lui e' della riga.
      .then(gestures)
      // La maniglia sporgeva di un quarto oltre le estremita' della pista, nella sorgente. Qui lo
      // spazio glielo si lascia dentro, e `alpha` (che ritaglia ai bordi) non la mozza a fine corsa.
      .padding(horizontal = SliderEdge),
    contentAlignment = Alignment.CenterStart,
  ) {
    val trackPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val thumbPx = with(density) { ThumbWidth.toPx() }

    // 1. Le tacche, sotto la pista e fuori dalla registrazione: la maniglia non le piega, e non
    // devono fare parte di quello che la pista e' per la lente.
    if (snapValues.isNotEmpty()) {
      Box(
        Modifier
          .fillMaxSize()
          .drawBehind {
            val range = valueRange
            val y = size.height / 2f + (TrackHeight / 2 + StopGap).toPx()
            val radius = StopSize.toPx() / 2f
            val reached = dragAnimation.progress + SyncEpsilon
            for (stop in snapValues) {
              val stopFraction = fluidSliderFraction(stop, range)
              // La stessa posizione che prende il centro della maniglia a quel valore: una tacca
              // sta sotto la maniglia quando il valore ci si posa, anche vicino alle estremita'.
              val centre = fluidSliderThumbCentre(stopFraction, size.width, thumbPx)
              drawCircle(
                color = if (stopFraction <= reached) accentColor else stopIdleColor,
                radius = radius,
                center = Offset(if (isLtr) centre else size.width - centre, y),
              )
            }
          },
      )
    }

    // 2. La pista, registrata: due riempimenti che si toccano, e la lente piega il loro bordo.
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
      Box(
        Modifier
          .clip(FluidCapsuleShape)
          .drawBehind { drawRect(accentColor) }
          .height(TrackHeight)
          .layout { measurable, constraints ->
            // Per larghezza e non per scala: una scala schiaccerebbe anche la testa tonda del
            // riempimento, e la pista e' sottile abbastanza da far vedere la differenza.
            val placeable = measurable.measure(constraints)
            val width = (constraints.maxWidth * dragAnimation.progress.fastCoerceIn(0f, 1f)).fastRoundToInt()
            layout(width, placeable.height) { placeable.place(0, 0) }
          },
      )
    }

    // 3. La maniglia.
    Box(
      Modifier
        .graphicsLayer {
          // Nel layer, non nel layout: spostarla invalida un disegno e basta, e il renderer inverte
          // questa traslazione quando campiona, quindi in ogni punto della corsa la lente ha in
          // mano l'immagine che la pista ha davvero dietro.
          val centre = fluidSliderThumbCentre(dragAnimation.progress, trackPx, thumbPx)
          translationX = (centre - thumbPx / 2f) * if (isLtr) 1f else -1f
        }
        .then(
          if (isRenderEffectSupported()) {
            Modifier.drawBackdrop(
              backdrop = thumbBackdrop,
              shape = { FluidCapsuleShape },
              effects = {
                val progress = dragAnimation.pressProgress
                blur(8f.dp.toPx() * (1f - progress))
                lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
              },
              highlight = {
                val progress = dragAnimation.pressProgress
                Highlight.Ambient.copy(
                  width = Highlight.Ambient.width / 1.5f,
                  blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                  alpha = progress,
                )
              },
              shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
              innerShadow = {
                val progress = dragAnimation.pressProgress
                InnerShadow(radius = 4f.dp * progress, alpha = progress)
              },
              layerBlock = {
                scaleX = dragAnimation.scaleX
                scaleY = dragAnimation.scaleY
                if (!currentReducedMotion) {
                  // Il modulo, non il segno: vedi l'intestazione. La sorgente dividendo per 1 - v
                  // allunga la pillola in un verso e la accorcia nell'altro.
                  val speed = abs(dragAnimation.velocity / 10f)
                  scaleX /= 1f - (speed * 0.75f).fastCoerceIn(0f, 0.2f)
                  scaleY *= 1f - (speed * 0.25f).fastCoerceIn(0f, 0.2f)
                }
              },
              // Bianca a riposo e sparita sotto il dito: e' questo bianco che la lente sostituisce.
              // Il bianco e' voluto e non del tema, come quello della manopola di `FluidSwitch`:
              // e' la superficie della lente, che resta una pillola chiara anche sul tema scuro.
              onDrawSurface = {
                drawRect(Color.White.copy(alpha = 1f - dragAnimation.pressProgress))
              },
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
        .size(ThumbWidth, ThumbHeight),
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Gesti
// ---------------------------------------------------------------------------------------------

/**
 * Di chi e' un gesto che e' caduto sulla pista nuda di un [FluidSlider], dopo [travel].
 *
 * Lo slider lo prende a **mezzo** slop di lato, purche' sia piu' di lato che no: legge il puntatore
 * prima della pagina, e prenderlo prima del suo slop e' cio' che gli lascia una deriva diagonale che
 * la pagina avrebbe preso. La pagina lo prende quando la sola parte verticale supera uno slop intero:
 * lo stesso test che applica il suo `scrollable`, nello stesso momento.
 */
internal fun fluidSliderClaim(travel: Offset, touchSlop: Float): FluidSliderClaim {
  val sideways = abs(travel.x)
  val vertical = abs(travel.y)
  return when {
    sideways >= touchSlop * SliderClaimSlopFraction && sideways >= vertical -> FluidSliderClaim.Slider
    vertical > touchSlop -> FluidSliderClaim.Page
    else -> FluidSliderClaim.Undecided
  }
}

internal enum class FluidSliderClaim { Undecided, Slider, Page }

/** Quanto di uno slop serve, di lato, perche' lo slider si prenda un gesto sulla pista. */
internal const val SliderClaimSlopFraction = 0.5f

/**
 * Se il tocco ha avuto un solo tocco aptico per tacca.
 *
 * Una tacca suona quando il valore *si posa* su di lei, non finche' ci sta, e al piu' una ogni 55 ms
 * come le altre tacche di un gesto: passando veloci sopra cinque tacche se ne sentono alcune, non
 * un ronzio. Afferrare la maniglia ferma su una tacca non suona.
 */
internal class FluidSliderNotchGate(
  private val minGapMillis: Long = FluidHapticDetents.DETENT_MIN_GAP_MILLIS,
) {
  private var last = -1
  private var lastAt = Long.MIN_VALUE / 2

  /** Da dove riparte un gesto: [index] e' la tacca su cui sta gia', o -1. */
  fun restart(index: Int) {
    last = index
  }

  /** Il valore sta ora sulla tacca [index] (-1: fra due tacche). Vero se merita un tocco. */
  fun land(index: Int, nowMillis: Long): Boolean {
    val changed = index != last
    last = index
    if (!changed || index < 0) return false
    if (nowMillis - lastAt < minGapMillis) return false
    lastAt = nowMillis
    return true
  }
}

// ---------------------------------------------------------------------------------------------
// L'aritmetica dello slider
// ---------------------------------------------------------------------------------------------
//
// Qui fuori per la ragione per cui lo e' quella della barra pieghevole: gira a ogni fotogramma di
// un trascinamento ed e' la parte che sbaglia in silenzio. Un intervallo vuoto o scritto al
// contrario e' un errore di chi chiama, non un motivo per dividere per zero e mandare la maniglia a
// un'estremita'.

/** Dove sta un valore nel suo intervallo, da 0 a 1. */
internal fun fluidSliderFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
  val span = range.endInclusive - range.start
  if (span <= 0f) return 0f
  return ((value - range.start) / span).fastCoerceIn(0f, 1f)
}

/** Il valore che una frazione della pista rappresenta. L'inverso di [fluidSliderFraction]. */
internal fun fluidSliderValue(fraction: Float, range: ClosedFloatingPointRange<Float>): Float {
  val span = range.endInclusive - range.start
  if (span <= 0f) return range.start
  return range.start + fraction.fastCoerceIn(0f, 1f) * span
}

/**
 * Cosa chiede un tocco a [x], misurata dall'inizio della riga che ha [edgePx] di margine a ogni lato.
 *
 * Il valore e' quello sotto il dito, linearmente sulla pista. Vicino alle estremita' la maniglia non
 * ci arriva sotto (si ferma a un quarto di se' dal bordo), ma il valore si': come nella sorgente,
 * dove la corsa del valore e' quella della pista e quella della maniglia e' limitata.
 */
internal fun fluidSliderFractionAt(x: Float, edgePx: Float, trackPx: Float): Float {
  if (trackPx <= 0f) return 0f
  return ((x - edgePx) / trackPx).fastCoerceIn(0f, 1f)
}

/**
 * Dove sta il centro della maniglia, dall'inizio della pista lunga [trackPx], quando il valore e' a
 * [fraction].
 *
 * La maniglia segue la frazione per tutta la pista, ma il suo centro non arriva mai piu' vicino al
 * bordo di un quarto della sua larghezza: e' il limite della sorgente (la maniglia, piu' larga della
 * pista e' sottile, si ferma li' invece di uscire).
 */
internal fun fluidSliderThumbCentre(fraction: Float, trackPx: Float, thumbPx: Float): Float {
  val low = thumbPx / 4f
  val high = max(low, trackPx - thumbPx / 4f)
  return max(low, min(trackPx * fraction, high))
}

/**
 * La tacca a cui [value] si attacca, come indice in [stops], o -1.
 *
 * Si attacca alla piu' vicina che sia a meno di [threshold] dell'intervallo — una frazione, perche'
 * un numero nelle unita' del valore vorrebbe dire una cosa su 0..1 e un'altra su 0..120. Con
 * `threshold = 1` ogni valore ha la sua tacca. A pari distanza vince la prima della lista.
 */
internal fun fluidSliderStopIndex(
  value: Float,
  stops: List<Float>,
  range: ClosedFloatingPointRange<Float>,
  threshold: Float,
): Int {
  if (stops.isEmpty()) return -1
  val span = range.endInclusive - range.start
  if (span <= 0f) return -1
  val reach = threshold.fastCoerceIn(0f, 1f) * span
  var best = -1
  var bestDistance = Float.MAX_VALUE
  for (i in stops.indices) {
    val distance = abs(value - stops[i].fastCoerceIn(range.start, range.endInclusive))
    if (distance <= reach && distance < bestDistance) {
      best = i
      bestDistance = distance
    }
  }
  return best
}

/** Il valore su cui si posa [value]: la sua tacca se ne ha una, altrimenti se stesso nell'intervallo. */
internal fun fluidSliderSnap(
  value: Float,
  stops: List<Float>,
  range: ClosedFloatingPointRange<Float>,
  threshold: Float,
): Float {
  val index = fluidSliderStopIndex(value, stops, range, threshold)
  return if (index >= 0) {
    stops[index].fastCoerceIn(range.start, range.endInclusive)
  } else {
    value.fastCoerceIn(range.start, range.endInclusive)
  }
}

// ---------------------------------------------------------------------------------------------
// Misure
// ---------------------------------------------------------------------------------------------

/** La pista, 6 dp: sottile apposta, la maniglia e' l'unica cosa che ha spessore. */
private val TrackHeight: Dp = 6.dp

/** 40 x 24, i numeri della sorgente: una pillola, non un cerchio. */
private val ThumbWidth: Dp = 40.dp
private val ThumbHeight: Dp = 24.dp

/**
 * Il margine a ogni lato della riga, un quarto della maniglia. E' dove la sorgente la lasciava
 * sporgere oltre la pista, e anche l'unica cosa che separa la pista dal bordo del controllo.
 */
private val SliderEdge: Dp = ThumbWidth / 4

/** Alto 48 dp: il bersaglio minimo, con la pista e la maniglia al centro. */
private val SliderHeight: Dp = 48.dp

/**
 * Quanto fuori dalla maniglia un dito la prende ancora. Sotto un polpastrello una maniglia da 40 dp
 * e' quasi tutta nascosta, e un tocco appena accanto vuole dire lei, non la pista.
 */
private val SliderGrabMargin: Dp = 10.dp

/** Tacche: un puntino da 2 dp, a 7 dp dal bordo della pista. */
private val StopSize: Dp = 2.dp
private val StopGap: Dp = 7.dp

/** Quanti passaggi dividono l'intervallo per il tocco aptico senza tacche. */
private const val SliderTickSteps = 20

/**
 * Sotto questa distanza una maniglia e un valore sono lo stesso punto. La molla si ferma a 0.001, e
 * il valore che torna da fuori dopo un tocco differisce dal bersaglio di un'unita' dell'ultimo bit.
 */
private const val SyncEpsilon = 1e-4f

/** Quanto si gonfia la maniglia di ripiego sotto il dito: un ripiego non ha la lente da far vedere. */
private const val FallbackSwell = 0.15f

/** Gli alpha di `onSurface` che riportano la pista dove cade il grigio della sorgente. */
private const val TrackAlphaLight = 0.12f
private const val TrackAlphaDark = 0.17f
