package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidDropWarp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.LocalFluidPhysicsLiquid
import dev.antigravity.fluidengine.ui.fluidphysics.dropRestSlab
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmFamily
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmUp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Il viaggio di un menu (3.0): l'ancora che diventa il pannello e il pannello che torna ancora.
 *
 * Le funzioni pure del viaggio — finestre di opacita', mappa del contenuto verso la sagoma, gonfiore
 * dell'oltrepasso, il rettangolo del tasto premuto, le misure e il posto del pannello — stanno qui
 * perche' sono le parti che si sbagliano senza che nessuno se ne accorga (un numero sbagliato di un
 * decimo e' un menu che "non sembra giusto") e si possono inchiodare in JVM, senza un frame clock.
 *
 * Accanto, i due pezzi con stato che i due host condividono (fase 7): [FluidMenuAnchor], l'ancora che
 * si nasconde e passa il dito, e [FluidMenuJourney], il progresso con la sua molla che guida la
 * finestra di Fluid-physics. Un menu che nasce da un tasto, quello di una riga e quello di una barra
 * fanno lo stesso viaggio perche' e' lo stesso oggetto a farlo.
 */

// --- Finestre di opacita' -------------------------------------------------------------

/**
 * L'opacita' del CONTENUTO del pannello (le voci) e della goccia, al progresso [p] del viaggio.
 *
 * **Il contenuto sta dentro il vetro formato, mai sopra il vetro che si forma** (3.0, il vetro
 * liquido). In apertura le voci arrivano mentre il corpo si forma (da 0,4 a 0,75; fino alla 3.0.3 da
 * 0,55 a 0,9, e il menu sembrava aprirsi lento), ritagliate dentro di lui; in
 * chiusura se ne vanno PER PRIME (da 0,94 a 0,62, una sessantina di millisecondi con la molla del
 * ritorno), attaccate al corpo e rimpicciolite verso l'ancora, e il liquido si ritira vuoto. Prima la
 * chiusura le teneva leggibili fino a 0,12 mentre il pannello si ritirava: la scala del contenuto,
 * fermata a 0,72, non stava piu' dentro una sagoma che scendeva verso il 25% — le scritte uscivano dal
 * vetro e galleggiavano sulla pagina mezze trasparenti. Era "il testo che se ne va in modo strano".
 *
 * Due finestre e non una perche' un viaggio puo' essere invertito a meta': [closing] sceglie la
 * finestra del verso in cui si sta andando, e la stessa p da' due opacita' diverse nei due versi —
 * voluto, e' la differenza fra il tasto che si apre e quello che si chiude.
 *
 * Lineare e fermata ai capi. [p] oltre 1 (l'oltrepasso della molla) e sotto 0 non cambiano niente.
 */
internal fun fluidMenuContentAlpha(p: Float, closing: Boolean): Float =
  if (closing) {
    unitRamp(p, FluidMotion.LiquidContentCloseFrom, FluidMotion.LiquidContentCloseTo)
  } else {
    unitRamp(p, FluidMotion.LiquidContentOpenFrom, FluidMotion.LiquidContentOpenTo)
  }

/**
 * L'opacita' della FACCIA dell'ancora (l'icona o l'etichetta del tasto) al progresso [p]: piena alla
 * partenza, spenta da [FluidMotion.MenuFaceTo] in poi (la finestra `Outgoing` di Fluid-physics). La
 * faccia se ne va nella prima parte del viaggio perche' le voci arrivano nella seconda, e i due
 * testi non devono mai essere leggibili insieme.
 */
internal fun fluidMenuFaceAlpha(p: Float): Float = 1f - unitRamp(p, 0f, FluidMotion.MenuFaceTo)

/**
 * Quanto del materiale del PANNELLO e' gia' sulla sagoma al progresso [p]: 0 e' il materiale
 * dell'ancora, 1 quello del pannello. Una smoothstep che arriva a 1 al 60% del viaggio: la sagoma
 * cambia pelle mentre viaggia, e a pannello quasi arrivato la pelle e' gia' quella finale — niente
 * gradino di luminosita' quando il viaggio si posa, che e' dove l'occhio guarda.
 */
internal fun fluidMenuMaterialBlend(p: Float): Float {
  val x = unitRamp(p, 0f, MaterialArrivesAt)
  return x * x * (3f - 2f * x)
}

/** A questo punto del viaggio la sagoma ha gia' il materiale del pannello. */
internal const val MaterialArrivesAt = 0.6f

/** 0 fino a [from], 1 da [to] in poi, lineare in mezzo. */
private fun unitRamp(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

private fun smoothMenuRamp(x: Float): Float = x * x * (3f - 2f * x)

/** Il clock resta lineare: i bordi del vetro e l'inchiostro hanno tempi propri. */
internal fun fluidMenuReturnSpec(millis: Int = FluidMotion.MenuReturnMillis) =
  tween<Float>(millis, easing = LinearEasing)

internal fun fluidMenuReturnFocusSpec(from: Float) =
  tween<Float>((FluidMotion.MenuReturnFocusMillis * from).roundToInt(), easing = Easing {
    val left = 1f - it
    1f - left * left * left
  })

internal fun fluidMenuReturnInkSpec(from: Float) =
  tween<Float>((FluidMotion.MenuReturnInkMillis * from).roundToInt(), easing = Easing {
    val left = 1f - it
    1f - left * left
  })

/**
 * R4 B1 (a): il margine lontano cede per primo, la parte vicina resta sopra la sorgente e raccoglie
 * la bolla. La bolla conserva volume fin quasi all'arrivo, anche quando torna una capsula larga.
 */
internal fun fluidMenuReturnSlab(
  source: FluidForm.Slab,
  panel: FluidForm.Slab,
  progress: Float,
): FluidForm.Slab {
  if (progress >= 1f) return panel
  if (progress <= 0f) return source
  val t = 1f - progress
  val frame = panel.frame
  val home = source.frame
  val up = home.center.y >= frame.center.y
  val held = Rect(
    left = frame.left + (home.center.x - frame.left) * FluidMotion.MenuReturnWidthHold,
    top = frame.top + frame.height * if (up) FluidMotion.MenuReturnFarEdge else -FluidMotion.MenuReturnNearEdge,
    right = frame.right + (home.center.x - frame.right) * FluidMotion.MenuReturnWidthHold,
    bottom = frame.bottom + frame.height * if (up) FluidMotion.MenuReturnNearEdge else -FluidMotion.MenuReturnFarEdge,
  )
  val diameter = home.minDimension * FluidMotion.MenuReturnBubbleSize
  val lift = home.minDimension * FluidMotion.MenuReturnBubbleLift * if (up) -1f else 1f
  val center = home.center + Offset(0f, lift)
  val bubble = FluidForm.Slab(
    Rect(center - Offset(diameter / 2f, diameter / 2f), Size(diameter, diameter)),
    FluidCornerRadii.all(diameter / 2f),
  )
  val shoulder = panel.copy(frame = held)
  return when {
    t < FluidMotion.MenuReturnHold -> {
      val give = t / FluidMotion.MenuReturnHold
      panel.copy(frame = lerp(frame, held, give * give))
    }
    t < FluidMotion.MenuReturnBubble -> {
      val fall = smoothMenuRamp(unitRamp(t, FluidMotion.MenuReturnHold, FluidMotion.MenuReturnBubble))
      dropRestSlab(shoulder, bubble, fall, 0f, 0f)
    }
    else -> {
      val land = smoothMenuRamp(unitRamp(t, FluidMotion.MenuReturnBubble, 1f))
      dropRestSlab(bubble, source, land, 0f, 0f)
    }
  }
}

/** Le icone ricompaiono mentre la bolla si riposa sulla sorgente, dopo la scomparsa delle voci. */
internal fun fluidMenuReturnFaceProgress(progress: Float): Float =
  1f - unitRamp(1f - progress, FluidMotion.MenuReturnBubble, 1f)

/** Il contorno di ritorno disegna la bolla della posa, senza una seconda coda che ritardi la staffetta. */
@Composable
internal fun FluidMenuReturnSurface(controlledReturn: Boolean, surface: @Composable () -> Modifier) {
  CompositionLocalProvider(LocalFluidPhysicsLiquid provides (LocalFluidPhysicsLiquid.current && !controlledReturn)) {
    Box(surface())
  }
}

internal fun fluidMenuOpeningOvershoot(
  slab: FluidForm.Slab,
  panel: Rect,
  source: Rect,
  millis: Float,
  widthRoom: Float,
): FluidForm.Slab {
  val peak = FluidMotion.MenuOvershootPeakMillis.toFloat()
  val rise = smoothMenuRamp(unitRamp(millis, FluidMotion.MenuOvershootStartMillis.toFloat(), peak))
  if (rise == 0f || millis >= FluidMotion.MenuOvershootEndMillis) return slab
  val width = rise * (1f - smoothMenuRamp(unitRamp(millis, peak, FluidMotion.MenuOvershootWidthEndMillis.toFloat())))
  val height = rise * (1f - smoothMenuRamp(unitRamp(millis, peak, FluidMotion.MenuOvershootEndMillis.toFloat())))
  val dx = panel.width * min(widthRoom, FluidMotion.MenuOvershootWidth) * width / 2f
  val dy = panel.height * FluidMotion.MenuOvershootFarEdge * height
  val up = source.center.y >= panel.center.y
  val r = slab.frame
  return slab.copy(frame = Rect(r.left - dx, r.top - if (up) dy else 0f, r.right + dx, r.bottom + if (up) 0f else dy))
}

// --- La mappa del contenuto ----------------------------------------------------------

/**
 * Dove sta il contenuto del pannello mentre la sagoma viaggia: una traslazione e UNA scala uniforme,
 * mai due scale (un contenuto stirato e' il testo schiacciato del design rigettato del 1.8.x).
 */
internal data class FluidMenuContentMap(val translation: Offset, val scale: Float) {
  companion object {
    /** Il contenuto dov'e': a viaggio finito, o per una geometria che non si sa mappare. */
    val Identity = FluidMenuContentMap(Offset.Zero, 1f)
  }
}

/**
 * Dove cade nella geometria a riposo del pannello [panel] un punto [root] dello schermo, quando le voci si disegnano con
 * [map] (un `graphicsLayer` grande quanto il pannello, scalato attorno al suo centro e traslato): l'inversa della mappa. Serve
 * al dito che sceglie mentre il menu nasce (`FluidMenuSelection.pointerMap`): la voce che si vede staccata col corpo o
 * rimpicciolita nella goccia e' quella che si sceglie. Identita' per la mappa identita' o una scala degenere.
 */
internal fun fluidMenuPointerAtRest(root: Offset, panel: Rect, map: FluidMenuContentMap): Offset {
  if (map == FluidMenuContentMap.Identity || !(map.scale > 0.0001f)) return root
  val c = panel.center
  return Offset(
    c.x + (root.x - map.translation.x - c.x) / map.scale,
    c.y + (root.y - map.translation.y - c.y) / map.scale,
  )
}

/**
 * La mappa che porta il contenuto, posato alla taglia della posa finale ([target]), sulla sagoma di
 * questo istante ([current]): traslato sul centro corrente e scalato di quanto la sagoma e' piu'
 * piccola o grande del traguardo. E' la regola di `fluidPhysicsContent` (Incoming), che viaggia con
 * la superficie invece di aspettarla: senza, il testo sta fermo mentre il pannello gli cresce sotto
 * — la "tendina tirata su un cartello gia' scritto".
 *
 * La scala e' il rapporto dei lati CORTI, fermata fra [minScale] e [maxScale] (0,72 e 1,12): sotto, le
 * voci diventerebbero un puntino che nessuna opacita' giustifica; sopra, l'oltrepasso gonfierebbe
 * una riga larga fuori dal pannello.
 *
 * Identita' se uno dei due rettangoli e' degenere: meglio il contenuto fermo che un NaN nel layer.
 * Vale per le voci e per la goccia, che e' dello stesso contenuto.
 */
internal fun fluidMenuContentMap(
  target: Rect,
  current: Rect,
  minScale: Float = 0.72f,
  maxScale: Float = 1.12f,
): FluidMenuContentMap {
  if (target.minDimension <= 0f || current.minDimension <= 0f) return FluidMenuContentMap.Identity
  return FluidMenuContentMap(
    translation = current.center - target.center,
    scale = (current.minDimension / target.minDimension).coerceIn(minScale, maxScale),
  )
}

/**
 * La mappa delle voci di un menu di vetro liquido (3.0): traslate sulla sagoma a riposo di adesso
 * (spostata con la goccia: [current]), e scalate "di poco verso l'ancora" con la loro opacita' —
 * `0,86 + 0,14 · alpha`, [FluidMotion.LiquidContentMinScale] — non un menu in miniatura che
 * rimpicciolisce col corpo. Dove il corpo e' piu' piccolo del contenuto lo ritaglia (`travelClip`).
 */
internal fun fluidMenuLiquidContentMap(target: Rect, current: Rect, alpha: Float): FluidMenuContentMap {
  if (target.minDimension <= 0f || current.minDimension <= 0f) return FluidMenuContentMap.Identity
  val a = alpha.coerceIn(0f, 1f)
  return FluidMenuContentMap(
    translation = current.center - target.center,
    scale = FluidMotion.LiquidContentMinScale + (1f - FluidMotion.LiquidContentMinScale) * a,
  )
}

/** La sagoma seme si raccoglie sul posto, poi risale verso il punto da cui si apre la nuova forma. */
internal fun fluidMenuJourneySeed(
  source: Rect,
  trigger: Offset,
  expansionOrigin: Offset,
  progress: Float,
  grouped: Boolean,
): Rect {
  val gather = unitRamp(progress, 0f, FluidMotion.MenuGatherEnd)
  val travel = unitRamp(progress, FluidMotion.MenuGatherEnd, FluidMotion.MenuTravelEnd)
  val diameter = source.minDimension * FluidMotion.MenuSeedDiameterFraction
  val gatheredWidth = if (grouped) source.width * FluidMotion.MenuGroupGatherWidth else diameter
  val width = if (travel == 0f) {
    source.width + (gatheredWidth - source.width) * gather
  } else {
    gatheredWidth + (diameter - gatheredWidth) * travel
  }
  val height = source.height + (diameter - source.height) * (gather * 0.25f + travel * 0.75f)
  val center = androidx.compose.ui.geometry.lerp(source.center, expansionOrigin, travel) +
    (source.center - trigger) * (FluidMotion.MenuGroupTravelBias * travel * (1f - travel) * 4f)
  return Rect(center - Offset(width / 2f, height / 2f), Size(width, height))
}

/** Progresso del solo allargamento. Gli oltrepassi mantengono il guadagno della molla esistente. */
internal fun fluidMenuExpansionProgress(progress: Float): Float =
  if (progress in 0f..1f) unitRamp(progress, FluidMotion.MenuExpandStart, 1f) else progress

/** Dal tasto: raccolta sul posto, risalita con la bolla già in espansione, raccordo al pannello. */
internal fun fluidMenuButtonTravel(
  source: Rect,
  panel: Rect,
  panelRadius: Float,
  expansionOrigin: Offset,
  progress: Float,
): FluidForm.Slab {
  if (progress >= 1f) return FluidForm.Slab(panel, FluidCornerRadii.all(panelRadius))
  val gathered = FluidMotion.MenuSeedDiameterFraction
  val gathering = unitRamp(progress, 0f, FluidMotion.MenuButtonGatherEnd)
  val rise = unitRamp(progress, FluidMotion.MenuButtonGatherEnd, FluidMotion.MenuButtonFirstRise)
  val bloom = unitRamp(progress, FluidMotion.MenuButtonFirstRise, FluidMotion.MenuButtonBloom)
  val bubble = unitRamp(progress, FluidMotion.MenuButtonBloom, FluidMotion.MenuButtonBubble)
  val fill = unitRamp(progress, FluidMotion.MenuButtonBubble, FluidMotion.MenuButtonPanel)
  val finish = unitRamp(progress, FluidMotion.MenuButtonPanel, 1f)
  val rounding = unitRamp(progress, FluidMotion.MenuButtonRoundnessEnd, 1f)
  // Il bersaglio si allarga già nella prima risalita. Il bordo della goccia lo raggiunge in ritardo.
  val growthX = 0.46f * rise + 0.62f * bloom - 0.04f * (bubble + fill)
  val growthY = 0.43f * rise + 0.65f * bloom - 0.04f * (bubble + fill)
  val startWidth = source.width * (1f - (1f - gathered) * gathering)
  val startHeight = source.height * (1f - (1f - gathered) * gathering)
  val width = startWidth + (panel.width - startWidth) * growthX
  val height = startHeight + (panel.height - startHeight) * growthY
  val toward = expansionOrigin - source.center
  val center = source.center + Offset(
    toward.x * (0.325f * rise + 0.66f * bloom - 0.10f * bubble + 0.215f * fill - 0.10f * finish),
    toward.y * (0.49f * rise + 0.67f * bloom - 0.01f * (bubble + fill) - 0.14f * finish),
  ) + (panel.center - expansionOrigin) * fill
  val half = min(width, height) / 2f
  val rounded = half * (1f - 0.24f * unitRamp(progress, 0.58f, 0.88f))
  val radius = rounded + (panelRadius.coerceAtMost(half) - rounded) * rounding
  val rect = Rect(center - Offset(width / 2f, height / 2f), Size(width, height))
  return FluidForm.Slab(rect, FluidCornerRadii.all(radius))
}

internal fun fluidMenuLensMap(
  target: Rect,
  content: Rect,
  button: Boolean = false,
  progress: Float = 0f,
  focus: Float = 1f,
  /**
   * Il congedo di un menu dal tasto nato come Apple: l'ingrandimento cresce mentre la goccia si stringe, come in B1 (a)
   * (a 33 ms le voci sono ancora alla loro misura, a ~100 ms a 1,35×), e non segue la messa a fuoco che torna.
   */
  returning: Boolean = false,
): FluidMenuContentMap {
  if (content.isEmpty || target.isEmpty) return FluidMenuContentMap.Identity
  val fitted = min(content.width / target.width, content.height / target.height).coerceIn(0.04f, 1f)
  val zoom = when {
    !button -> 0f
    returning -> unitRamp(1f - progress, MenuReturnZoomFrom, MenuReturnZoomTo)
    else -> unitRamp(progress, FluidMotion.MenuButtonGatherEnd, FluidMotion.MenuButtonFirstRise) *
      (1f - unitRamp(focus, 0.37f, 0.63f))
  }
  val extra = (FluidMotion.MenuButtonLensMagnification - fitted) * zoom
  // Mentre il bordo ritaglia le righe estreme, il centro dell'inchiostro resta nella bolla.
  val inset = Offset(target.width * FluidMotion.MenuButtonLensInkPivot * extra, 0f)
  return FluidMenuContentMap(content.center - target.center + inset, fitted + extra)
}

/**
 * Dove cresce l'ingrandimento delle voci al congedo del menu dal tasto nato come Apple, in frazioni del ritorno (233 ms):
 * da 33 ms (0,14) a ~117 ms (0,5). B1 (a): f9188 alla misura, f9189 a ~1,15×, f9190 a ~1,35×.
 */
private const val MenuReturnZoomFrom = 0.14f
private const val MenuReturnZoomTo = 0.5f

internal fun fluidMenuLensAlpha(focus: Float): Float =
  FluidMotion.MenuContentFirstAlpha + (1f - FluidMotion.MenuContentFirstAlpha) * unitRamp(focus, 0f, FluidMotion.MenuContentInkFull)

internal fun fluidMenuLensBlurDp(focus: Float): Float =
  FluidMotion.MenuContentMaxBlurDp * (1f - focus.coerceIn(0f, 1f)).let { it * it }

internal fun fluidMenuLensStrength(focus: Float): Float =
  1f - unitRamp(focus, FluidMotion.MenuContentFieldFade, FluidMotion.MenuContentFieldEnd)

internal fun fluidMenuSpotAmount(progress: Float): Float =
  FluidMotion.MenuSpotPeak * unitRamp(progress, FluidMotion.MenuSpotStart, FluidMotion.MenuSpotCrest) *
    (1f - unitRamp(progress, FluidMotion.MenuSpotCrest, FluidMotion.MenuSpotEnd))

/** Punto di luce in viaggio sui menu Liquid, acceso di serie. */
val LocalFluidMenuPointOfLight = androidx.compose.runtime.compositionLocalOf { true }

internal fun fluidMenuSpotCenter(body: Rect, source: Offset, destination: Offset): Offset {
  val travel = destination - source
  val length = travel.getDistance()
  val direction = if (length > 0f) travel / length else Offset(0f, -1f)
  return body.center + direction * (body.minDimension * FluidMotion.MenuSpotSide)
}

@androidx.compose.runtime.Composable
internal fun fluidMenuUsesField(): Boolean {
  val requested = LocalFluidGlassModel.current
  val available = remember(requested) {
    requested == FluidGlassModel.Liquid &&
      dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported() &&
      dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidShadersHealthy()
  }
  if (available) {
    val view = LocalView.current
    remember(view) { GlassWarmUp.request(GlassWarmFamily.MenuContent, view) }
  }
  return available
}

/**
 * Il gonfiore della goccia oltre il pannello (3.0): uniforme, [FluidMotion.DropInflationGain] per
 * unita' d'oltrepasso, e sui fianchi mai oltre quanto lo spazio fra il pannello e il margine minimo
 * ([edgeMinPx]) consente al picco della molla ([FluidMotion.DropPeakOvershoot]).
 */
internal fun fluidDropInflation(paneW: Float, availW: Float, edgeMinPx: Float): FluidMenuInflation {
  if (paneW <= 0f) return FluidMenuInflation.None
  val gain = FluidMotion.DropInflationGain
  val room = ((availW - 2f * edgeMinPx - paneW).coerceAtLeast(0f) / paneW) / FluidMotion.DropPeakOvershoot
  return FluidMenuInflation(x = min(gain, room), y = gain, widthRoom = room * FluidMotion.DropPeakOvershoot)
}

// --- Il gonfiore dell'oltrepasso -------------------------------------------------------

/** Il gonfiore dell'oltrepasso per asse: la frazione di crescita per unita' di (progress − 1). */
internal data class FluidMenuInflation(val x: Float, val y: Float, val widthRoom: Float = 0f) {
  companion object {
    val None = FluidMenuInflation(0f, 0f)
  }
}

/**
 * Il gonfiore con cui la sagoma oltrepassa il pannello (`overshootInflationX/Y` di
 * `driveExternally`). Estratto da `FluidGlassModal.kt` (la finestra dei pop-up), dove era scritto in
 * linea, perche' il menu del morph, il menu contestuale e l'avviso hanno lo stesso bisogno e non
 * possono avere tre copie di un'aritmetica che si tara a occhio.
 *
 * - **Y**: la molla e' GRANDE QUANTO IL VIAGGIO. Un pannello alto arriva con piu' slancio di un
 *   biglietto, quindi il gonfiore scala con l'altezza ([paneH]) rispetto a un'altezza di riferimento
 *   ([refHeightPx]), fermato fra 0,55 e 1,6, per [gain].
 * - **X**: lo schermo limita i fianchi, il fondo no. La X e' il minimo fra la Y e quanto spazio resta
 *   davvero fra il pannello e il margine minimo ([edgeMinPx]) su ogni lato, in frazione della
 *   larghezza, diviso per il picco atteso dell'oltrepasso ([peakOvershoot]): al picco il pannello
 *   non finisce a ridosso del bordo dello schermo, e la Y non perde vita per colpa dei fianchi.
 *
 * [availW] e' la larghezza disponibile (quella dello schermo o del contenitore che ospita la
 * finestra). Con una taglia non positiva non c'e' niente da gonfiare: [FluidMenuInflation.None].
 *
 * I default ([FluidMotion.MenuInflationGain], [FluidMotion.MenuPeakOvershoot]) sono quelli del menu;
 * il modale di prima passava 0,6 e 0,15 (la molla del morph, che oltrepassava di piu'), ed e' con
 * quei due numeri che un test confronta questa funzione con la sua formula originale.
 */
internal fun fluidMenuInflation(
  paneW: Float,
  paneH: Float,
  availW: Float,
  edgeMinPx: Float,
  refHeightPx: Float,
  gain: Float = FluidMotion.MenuInflationGain,
  peakOvershoot: Float = FluidMotion.MenuPeakOvershoot,
): FluidMenuInflation {
  if (paneW <= 0f || paneH <= 0f || refHeightPx <= 0f || peakOvershoot <= 0f) return FluidMenuInflation.None
  val y = gain * (paneH / refHeightPx).coerceIn(0.55f, 1.6f)
  val room = ((availW - 2f * edgeMinPx - paneW).coerceAtLeast(0f) / paneW) / peakOvershoot
  return FluidMenuInflation(x = min(y, room), y = y)
}

// --- Il tasto premuto -----------------------------------------------------------------

/**
 * Il rettangolo di un tasto mentre il dito lo preme: [rest] trasformato da [transform], come lo
 * trasforma il `layerBlock` di `glassControlSurface` (scala attorno al centro, poi la deriva).
 *
 * Si legge al click lungo, quando la pressione e' ancora ~1: e' da li' che la sagoma del menu parte.
 * Partire dal rettangolo a riposo voleva dire un primo fotogramma piu' piccolo del tasto che il dito
 * stava ancora gonfiando — un sussulto all'indietro proprio dove l'occhio guarda.
 */
internal fun fluidPressedBounds(rest: Rect, transform: FluidPressTransform): Rect {
  if (rest.width <= 0f || rest.height <= 0f || transform.isIdentity) return rest
  val cx = rest.center.x + transform.translationX
  val cy = rest.center.y + transform.translationY
  val hw = rest.width * transform.scaleX / 2f
  val hh = rest.height * transform.scaleY / 2f
  return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
}

/**
 * Il segnaposto di partenza del viaggio di un menu che parte da un tasto (`FluidMenuJourney.install`): la
 * **taglia del tasto a riposo** ([rest]), centrata **dove il dito l'ha spinto** ([pressed]).
 *
 * Perché (F1B.3, sessione sul tablet dopo F1B.2): la geometria della partenza la da' la partenza viva
 * (`startFrame`, il tasto premuto che torna a riposo), e il segnaposto serve a riconoscere la corsa, a dire
 * da dove parte l'ancora della goccia (il suo centro) e da quale **materiale** parte il viaggio: il velo dei
 * grandi della superficie che viaggia parte da quello della forma di partenza (`PhysicsLargeVeil`, dal lato
 * corto del segnaposto). Il tasto premuto e' gonfiato dal dito (un tasto da 44 dp arriva a ~65), ma il suo
 * vetro prende il velo dai 44 dp del nodo: la scala della pressione e' un `graphicsLayer`. Col segnaposto
 * gonfiato, sul lato scuro la goccia nasceva con un terzo del velo dei grandi e il primo fotogramma del clic
 * lungo saltava di −10 livelli rispetto al tasto. Il centro resta quello del tasto premuto: l'ancora della
 * goccia non si sposta.
 *
 * Con un [rest] senza area (il tasto non ancora posato) resta [pressed], com'era.
 */
internal fun fluidMenuOriginFrame(rest: Rect, pressed: Rect): Rect {
  if (!(rest.width > 0f) || !(rest.height > 0f)) return pressed
  val c = pressed.center
  return Rect(c.x - rest.width / 2f, c.y - rest.height / 2f, c.x + rest.width / 2f, c.y + rest.height / 2f)
}

/** [fluidPressedBounds] con la pressione di adesso di [press]. Fuori dal disegno: e' un'istantanea. */
internal fun fluidPressedBounds(rest: Rect, press: FluidPressState, density: Float): Rect =
  fluidPressedBounds(
    rest,
    fluidPressTransform(
      feedback = press.feedback,
      progress = press.progress,
      lean = press.lean,
      width = rest.width,
      height = rest.height,
      dp = density,
      reducedMotion = press.reducedMotion,
    ),
  )

// --- Il pannello: misure e posto ----------------------------------------------------------

/**
 * Le misure del pannello di un menu, in pixel. UNA aritmetica per chi risolve il pannello (l'host,
 * prima ancora di comporlo, perche' l'apertura e' sincrona) e per chi lo disegna e ne fa il
 * hit-test: con due aritmetiche il dito sceglierebbe la riga sopra quella che vede.
 */
@Immutable
internal data class FluidMenuPanelMetrics(
  val widthPx: Float,
  val rowHeightPx: Float,
  val paddingPx: Float,
  val sectionGapPx: Float,
  val radiusPx: Float,
  val gapPx: Float,
  val marginPx: Float,
  val edgeMinPx: Float,
  val refHeightPx: Float,
)

/**
 * L'altezza di una riga di menu: `max(48 dp, lineHeight + 24 dp)`. Segue il font (al testo grande
 * la riga cresce invece di tagliare l'etichetta), e a 1× e' la riga di sempre.
 */
internal fun fluidMenuRowHeight(lineHeight: Dp): Dp = max(FluidMenuMinRowHeight.value, (lineHeight + 24.dp).value).dp

/**
 * Quanto e' largo il pannello di un menu per le sue voci (3.0.1): la misura di serie
 * ([FluidMenuPanelMetrics.widthPx], 252 dp) se ci stanno, altrimenti quanto serve alla voce piu' lunga —
 * etichetta, icona e spunta — fino a [FluidMenuPanelMaxWidth] e mai oltre lo schermo meno i margini.
 * Prima era sempre 252 dp, e una voce che porta il suo stato («Salta i silenzi oltre 12 s: spento»)
 * perdeva proprio lo stato nei puntini. Oltre il tetto i puntini restano: una riga di menu e' una riga.
 *
 * @param labelWidthsPx la larghezza di ogni etichetta, nel tipo delle voci.
 * @param hasIcon, hasCheck se qualche voce ha l'icona o la spunta (lo spazio vale per tutte, le colonne
 *   restano allineate).
 */
internal fun fluidMenuPanelWidth(
  metrics: FluidMenuPanelMetrics,
  labelWidthsPx: List<Float>,
  hasIcon: Boolean,
  hasCheck: Boolean,
  density: Float,
  hostWidthPx: Float,
): Float {
  val widest = labelWidthsPx.maxOrNull() ?: 0f
  // Le misure della riga di FluidGlassMenuPanel: 16 dp per lato, 12 dp fra l'etichetta e ogni segno da 22.
  val extras = (FluidMenuRowSidePadding.value * 2f +
    (if (hasIcon) FluidMenuRowGlyph.value + FluidMenuRowGap.value else 0f) +
    (if (hasCheck) FluidMenuRowGlyph.value + FluidMenuRowGap.value else 0f)) * density +
    metrics.paddingPx * 2f
  val wanted = widest + extras
  val ceiling = minOf(FluidMenuPanelMaxWidth.value * density, hostWidthPx - metrics.marginPx * 2f)
  return wanted.coerceAtMost(ceiling).coerceAtLeast(metrics.widthPx).coerceAtMost(maxOf(metrics.widthPx, ceiling))
}

/**
 * La larghezza di un'etichetta di menu nel tipo delle voci, per [fluidMenuPanelWidth]: misurata col
 * `TextMeasurer` di Compose, fuori dal disegno, perche' l'apertura risolve il pannello prima di comporlo.
 */
@Composable
internal fun rememberFluidMenuLabelMeasure(): (String) -> Float {
  val measurer = androidx.compose.ui.text.rememberTextMeasurer()
  val style = MaterialTheme.typography.bodyLarge
  return remember(measurer, style) {
    { label -> measurer.measure(label, style = style, maxLines = 1).size.width.toFloat() }
  }
}

/** Le misure di serie, dalla densita' e dal tipo del tema. */
@Composable
internal fun rememberFluidMenuPanelMetrics(): FluidMenuPanelMetrics {
  val density = LocalDensity.current
  val body = MaterialTheme.typography.bodyLarge
  return remember(density, body) {
    with(density) {
      val line = if (body.lineHeight.isSpecified) body.lineHeight.toDp() else (body.fontSize.toDp() * 1.4f)
      FluidMenuPanelMetrics(
        widthPx = FluidMenuPanelWidth.toPx(),
        rowHeightPx = fluidMenuRowHeight(line).toPx(),
        paddingPx = FluidMenuPanelPadding.toPx(),
        sectionGapPx = FluidMenuSectionGap.toPx(),
        radiusPx = FluidRadius.Group.toPx(),
        gapPx = FluidMenuAnchorGap.toPx(),
        marginPx = FluidMenuEdgeMargin.toPx(),
        edgeMinPx = FluidMenuInflationEdgeMin.toPx(),
        refHeightPx = FluidMenuInflationReferenceHeight.toPx(),
      )
    }
  }
}

/**
 * Dove stanno le voci dentro il pannello: il bordo alto di ogni cella (coordinate del pannello) e
 * l'altezza totale. Le sezioni sono uno spazio in piu' prima della voce che ne apre una (mai prima
 * della prima): una banda, non una cella, quindi il dito che ci si ferma non sceglie niente.
 */
internal class FluidMenuPanelLayout(
  val tops: FloatArray,
  val rowHeightPx: Float,
  val height: Float,
  /** Il bordo alto di ogni banda di sezione, nell'ordine. */
  val sectionBands: FloatArray,
  val sectionGapPx: Float,
)

internal fun fluidMenuPanelLayout(
  sectionStarts: List<Boolean>,
  rowHeightPx: Float,
  paddingPx: Float,
  sectionGapPx: Float,
): FluidMenuPanelLayout {
  val tops = FloatArray(sectionStarts.size)
  val bands = ArrayList<Float>()
  var y = paddingPx
  sectionStarts.forEachIndexed { i, starts ->
    if (i > 0 && starts) {
      bands += y
      y += sectionGapPx
    }
    tops[i] = y
    y += rowHeightPx
  }
  return FluidMenuPanelLayout(
    tops = tops,
    rowHeightPx = rowHeightPx,
    height = y + paddingPx,
    sectionBands = bands.toFloatArray(),
    sectionGapPx = sectionGapPx,
  )
}

/** [fluidMenuPanelLayout] con le misure di [m]. */
internal fun fluidMenuPanelLayout(sectionStarts: List<Boolean>, m: FluidMenuPanelMetrics): FluidMenuPanelLayout =
  fluidMenuPanelLayout(sectionStarts, m.rowHeightPx, m.paddingPx, m.sectionGapPx)

/** La geometria del hit-test per un pannello che sta a [panel] (pixel della radice). */
internal fun fluidMenuGeometryOf(layout: FluidMenuPanelLayout, enabled: List<Boolean>, panel: Rect): FluidMenuGeometry =
  FluidMenuGeometry(
    cells = List(layout.tops.size) { i ->
      Rect(panel.left, panel.top + layout.tops[i], panel.right, panel.top + layout.tops[i] + layout.rowHeightPx)
    },
    enabled = enabled,
    panel = panel,
  )

/** Dove si posa il pannello rispetto all'ancora. */
internal enum class FluidMenuPlacement {
  /** Sopra l'ancora se ci sta, altrimenti sotto (il menu che si trasforma: nasce da un tasto in basso). */
  AboveOrBelow,

  /** SULL'ancora: il pannello la copre e si apre sotto il dito (le azioni della barra, `Expand`). */
  OverAnchor,

  /** Accanto, sotto se c'e' posto: la riga sollevata resta visibile (il menu contestuale). */
  BesideAnchor,
}

/**
 * Il rettangolo del pannello, puro: largo [width] (mai oltre lo spazio fra i margini), alto [height],
 * col centro orizzontale il piu' vicino possibile all'ancora e sempre dentro i margini dell'host.
 */
internal fun solveFluidMenuPanel(
  anchor: Rect,
  host: Size,
  width: Float,
  height: Float,
  gapPx: Float,
  marginPx: Float,
  placement: FluidMenuPlacement,
): Rect {
  val w = width.coerceAtMost((host.width - marginPx * 2f).coerceAtLeast(1f))
  val left = (anchor.center.x - w / 2f).coerceIn(marginPx, (host.width - marginPx - w).coerceAtLeast(marginPx))
  val maxTop = (host.height - marginPx - height).coerceAtLeast(marginPx)
  val top = when (placement) {
    FluidMenuPlacement.AboveOrBelow -> {
      val fitsAbove = anchor.top - gapPx - height >= marginPx
      if (fitsAbove) anchor.top - gapPx - height else (anchor.bottom + gapPx).coerceAtMost(maxTop)
    }

    FluidMenuPlacement.OverAnchor -> {
      // Il bordo da cui il pannello si allontana resta fermo: il tasto non salta nel fotogramma in
      // cui si apre. Verso il basso se ci sta, se no verso l'alto dal bordo basso dell'ancora.
      val fitsDown = anchor.top + height <= host.height - marginPx
      if (fitsDown) anchor.top.coerceAtLeast(marginPx) else (anchor.bottom - height).coerceIn(marginPx, maxTop)
    }

    FluidMenuPlacement.BesideAnchor -> {
      val spaceBelow = host.height - anchor.bottom - gapPx - marginPx
      val spaceAbove = anchor.top - gapPx - marginPx
      val below = height <= spaceBelow || spaceBelow >= spaceAbove
      if (below) (anchor.bottom + gapPx).coerceAtMost(maxTop) else (anchor.top - gapPx - height).coerceAtLeast(marginPx)
    }
  }
  return Rect(left, top, left + w, top + height)
}

/**
 * Il seme da cui cresce un pannello SENZA ancora da cui trasformarsi (il menu contestuale, un pop-up
 * senza origine): il pannello stesso scalato di [scale] attorno a [pivot]. Cresce per geometria — la
 * sagoma, non un layer scalato sul vetro.
 */
internal fun fluidMenuSeedFrame(panel: Rect, pivot: Offset, scale: Float): Rect =
  fluidMenuScaledFrame(panel, pivot, scale)

/**
 * La goccia da cui nasce il menu contestuale (3.0, il vetro liquido): un cerchio di [diameterPx]
 * appeso al bordo della riga che guarda il pannello, sotto il dito ([touchX], o il centro della riga),
 * che entra nella riga di [overlapPx] — esce DALLA riga, non le compare accanto. Il corpo parte da qui,
 * e il corpo del menu ne esce come una goccia ancorata li', sotto il dito.
 *
 * Prima il seme era il pannello stesso a 0,42 attorno al perno: un pannello piccolo che compariva
 * sfumando dal nulla accanto alla riga, e la riga e il menu erano due oggetti.
 *
 * Dentro la riga, non dentro la colonna del pannello (correzioni dopo S4b, NO 7): col dito sul bordo della riga (x = 130 sul
 * Tab S9) e il pannello centrato la goccia nasceva gia' spostata verso il centro. Nasce sotto il dito, e il corpo va verso il
 * pannello inclinandosi, come l'uovo di Apple verso la graffetta ([fluidContextBirthWarp]).
 */
internal fun fluidMenuDropSeed(
  row: Rect,
  panel: Rect,
  touchX: Float?,
  diameterPx: Float,
  overlapPx: Float,
): Rect {
  val d = diameterPx.coerceAtMost(row.minDimension.coerceAtLeast(1f))
  val r = d / 2f
  val minX = row.left + r
  val maxX = (row.right - r).coerceAtLeast(minX)
  val x = (touchX ?: row.center.x).coerceIn(minX, maxX)
  val below = panel.center.y >= row.center.y
  val top = if (below) row.bottom - overlapPx else row.top + overlapPx - d
  return Rect(x - r, top, x + r, top + d)
}

/**
 * Quanto della goccia del contestuale si vede al progresso [p]: niente sulla riga (p = 0), tutta da
 * [FluidMenuDropEmerge] in poi. E' la goccia che ESCE dalla riga, e al ritorno ci rientra.
 */
internal fun fluidMenuDropPresence(p: Float): Float {
  val x = (p / FluidMenuDropEmerge).coerceIn(0f, 1f)
  return x * x * (3f - 2f * x)
}

/** Entro questo progresso la goccia del contestuale e' tutta fuori dalla riga. */
internal const val FluidMenuDropEmerge = 0.15f

/** [panel] scalato di [scale] attorno a [pivot]. */
private fun fluidMenuScaledFrame(panel: Rect, pivot: Offset, scale: Float): Rect =
  Rect(
    pivot.x + (panel.left - pivot.x) * scale,
    pivot.y + (panel.top - pivot.y) * scale,
    pivot.x + (panel.right - pivot.x) * scale,
    pivot.y + (panel.bottom - pivot.y) * scale,
  )

/** Il punto del pannello piu' vicino all'ancora: il perno della crescita del menu contestuale. */
internal fun fluidMenuPivot(panel: Rect, anchor: Rect): Offset = Offset(
  anchor.center.x.coerceIn(panel.left, panel.right),
  if (panel.center.y >= anchor.center.y) panel.top else panel.bottom,
)

internal val FluidMenuPanelWidth = 252.dp

/** Il tetto della larghezza di un menu con voci lunghe (3.0.1): oltre, la riga va in puntini. */
internal val FluidMenuPanelMaxWidth = 340.dp

/** Le misure orizzontali di una riga di menu, le stesse di `FluidMenuPanelRow`. */
internal val FluidMenuRowSidePadding = 16.dp
internal val FluidMenuRowGap = 12.dp
/** L'icona e la spunta di una voce: 22 dp dalla 4.0 (erano 20), [FluidMetrics.MenuIconSize] (R6). */
internal val FluidMenuRowGlyph = FluidMetrics.MenuIconSize
internal val FluidMenuPanelPadding = 6.dp
internal val FluidMenuSectionGap = 8.dp
internal val FluidMenuMinRowHeight = 48.dp
internal val FluidMenuAnchorGap = 12.dp
internal val FluidMenuEdgeMargin = 16.dp

/** Sotto questa distanza dai bordi laterali il gonfiore non porta mai il pannello (vedi [fluidMenuInflation]). */
internal val FluidMenuInflationEdgeMin = 6.dp

/** L'altezza a cui il gonfiore vale il guadagno esatto: quella della finestra dei modali. */
internal val FluidMenuInflationReferenceHeight = 240.dp

/** Di quanto e' piccolo il seme del menu contestuale: quello del pop-up di sempre. */
internal const val FluidMenuSeedScale = 0.42f

/** La goccia da cui nasce il menu contestuale (vedi [fluidMenuDropSeed]). */
internal val FluidMenuDropSeedDiameter = 28.dp
internal val FluidMenuDropSeedOverlap = 4.dp

// --- L'ancora ------------------------------------------------------------------------------

/**
 * L'ancora di un menu: il tasto (o la lente di una barra) che DIVENTA il pannello.
 *
 * La tiene chi disegna il tasto, ricordata: il gesto che apre ci scrive dentro la pressione del
 * momento, l'host ci legge dove partire. Due cose in piu' rispetto a un rettangolo:
 *
 * - [hidden], letto SOLO in disegno (`drawWithContent { if (!hidden) drawContent() }`): mentre il
 *   menu e' in scena il tasto non disegna niente, e al suo posto sta la superficie dell'host con la
 *   stessa sagoma. Un cambio di proprietario, non una dissolvenza; e niente `graphicsLayer` alpha,
 *   che e' un layer sopra un vetro.
 * - [handOff], il ponte del dito: `Modifier.fluidMenuPointerRelay(anchor.handOff)` in testa al tasto.
 *
 * [face] e' la faccia del tasto (icona, etichetta) che l'host porta lungo la prima parte del viaggio:
 * deve essere inerte — icone e testo, nessuno stato catturato — perche' vive in un oggetto, non in una
 * composizione.
 */
@Stable
internal class FluidMenuAnchor {
  /** Dove sta il tasto a riposo, in pixel della radice: lo scrive il suo layout. */
  var restBounds: Rect = Rect.Zero

  /** Dove stava al click lungo, gonfiato dal dito: vedi [fluidPressedBounds]. */
  var pressedBounds: Rect = Rect.Zero

  /** Capsula del gruppo fotografata prima di dichiararne vuoto il contenuto. */
  var groupBounds: Rect? = null
  var groupLease: ((Boolean) -> Unit)? = null
  var groupProgress: () -> Float = { 0f }
  var groupClip: () -> androidx.compose.ui.geometry.RoundRect? = { null }

  /**
   * Le icone del gruppo registrate dalla barra (il pezzo che il menu porta via), nelle coordinate della capsula, e se le
   * disegna l'host del menu SOPRA il suo vetro ([groupFacesAbove]: al ritorno) invece che la barra sotto. Correzioni dopo
   * S4b (NO 4): nell'ultimo tratto del ritorno il vetro del menu e' gia' la capsula, e le icone disegnate dalla barra stavano
   * sotto di lui: 2-3 fotogrammi di capsula vuota prima della consegna.
   */
  var groupFaces: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
  var groupFacesAbove: () -> Boolean = { false }

  var face: (@Composable () -> Unit)? = null

  /**
   * L'inchiostro del tasto (4.0, G1, rilievo C-R2): il suo stato adattivo e il lato del tema, o null se il tasto non
   * gira. L'host disegna la faccia in viaggio con questo (il lato, la fase e la dissolvenza del tasto, anche a metà
   * giro) e fa nascere il materiale del pannello dal suo stato.
   */
  var ink: GlassInk? = null

  /** Il materiale del tasto: la sagoma parte da qui e arriva a quello del pannello. */
  var tintFrom: GlassTint? = null
  var opticsFrom: GlassOptics? = null

  val handOff = FluidMenuHandOff()

  var hidden by mutableStateOf(false)

  fun consume(value: Boolean) {
    hidden = value
    groupLease?.invoke(value)
    if (!value) {
      groupBounds = null
      groupLease = null
      groupProgress = { 0f }
      groupClip = { null }
      groupFaces = null
      groupFacesAbove = { false }
    }
  }
}

/**
 * Le icone del gruppo al ritorno, come `groupProgress` (0 = piene, 1 = spente, [FluidMotion.MenuGroupFaceProgressSpan]):
 * spente finche' il vetro torna verso la capsula, e di nuovo piene nell'ultimo tratto, quello in cui la bolla si allarga
 * nella capsula ([FluidMotion.MenuReturnBubble] in poi, cioe' il progresso sotto [FluidMenuGroupFaceReturnShare]). Alla
 * consegna sono gia' quelle del pezzo vero: capsula e icone tornano nello stesso fotogramma (S4b, NO 4).
 */
internal fun fluidMenuGroupFaceReturn(progress: Float): Float =
  (progress / FluidMenuGroupFaceReturnShare).coerceIn(0f, 1f)

/** Il tratto finale del ritorno, in progresso: dalla bolla alla capsula. */
internal const val FluidMenuGroupFaceReturnShare: Float = 1f - FluidMotion.MenuReturnBubble

/**
 * La faccia dell'ancora dentro l'host (G1, C-R2): con l'inchiostro del tasto ([FluidMenuAnchor.ink]), così la faccia in
 * viaggio è quella del tasto al primo e all'ultimo fotogramma, girata o a metà giro. Senza inchiostro, com'era.
 */
@androidx.compose.runtime.Composable
internal fun FluidMenuAnchorFace(anchor: FluidMenuAnchor) {
  val face = anchor.face ?: return
  val ink = anchor.ink
  if (ink == null) {
    face()
    return
  }
  GlassInkContent(ink, androidx.compose.material3.LocalContentColor.current) { face() }
}

/** Quanto è visibile la faccia dell'ancora per la dissolvenza dell'inchiostro del tasto. Letto in disegno. */
internal fun fluidMenuAnchorInkAlpha(anchor: FluidMenuAnchor): Float = anchor.ink?.state?.inkAlpha() ?: 1f

// --- Il viaggio ------------------------------------------------------------------------------

/**
 * Il viaggio di UN menu: un progresso geometrico per i due versi (0 = l'ancora, 1 = il pannello, puo'
 * superare 1 con la molla d'apertura) che guida la finestra di Fluid-physics con `driveExternally`.
 *
 * - **Apertura**: [FluidMotion.menuOpen], sottosmorzata; l'oltrepasso e' un gonfiore uniforme attorno
 *   al centro del pannello ([inflation]), mai il lerp oltre l'1.
 * - **Partenza viva**: col tasto premuto la sagoma parte dal rettangolo GONFIATO dal dito e, mentre il
 *   viaggio corre, la pressione si scioglie verso il riposo ([anchorRelax], criticamente smorzata).
 * - **Chiusura e inversione**: dal valore corrente, con la sua velocita' ([retarget] la legge PRIMA che
 *   la molla vecchia venga cancellata: cancellata, un `Animatable` la azzera). Un back a meta'
 *   apertura torna indietro da dove si trova, non dal pannello pieno.
 *
 * [target] e' uno stato (la chiave dell'effetto dell'host); [progress] e [anchorRelax] si leggono
 * solo in disegno.
 */
@Stable
internal class FluidMenuJourney {

  val progress = Animatable(0f, ProgressThreshold)
  val focus = Animatable(0f, ProgressThreshold)
  var contentVisibility = Animatable(1f, ProgressThreshold)
    private set

  var liquidMenu: Boolean = false
    private set
  private var expansionOrigin: Offset = Offset.Zero

  val usesButtonTravel: Boolean get() = liquidMenu && anchor?.let { it.groupBounds == null } == true
  /**
   * La lente ingrandisce le voci del tasto singolo. Con la nascita di Apple anche al ritorno: in B1 (a) le voci che se ne
   * vanno sono ingrandite dalla goccia che si stringe («Orders», «Wishlis» a ~1,35× a 100 ms, f9190).
   */
  val magnifiesInk: Boolean get() = usesButtonTravel && (born || !returningOnContour)
  private var returningOnContour by mutableStateOf(false)
  val controlledReturn: Boolean get() = returningOnContour
  var openingAge = Animatable(0f, ProgressThreshold)
    private set

  /**
   * La nascita di Apple per i menu col vetro liquido che partono da un tasto o da un gruppo (sessione «contestuale», fase
   * tasto): il tasto singolo come B1 (a), il gruppo come la graffetta B1 (b) ([FluidMenuBirth.kt]). Chiesta dall'host in
   * [prepare]; senza, il viaggio e' quello di prima (le tracce storiche e i menu senza vetro liquido).
   */
  var appleBirth: Boolean = false
    private set

  /** I millisecondi della nascita, in disegno: ricreato a un'apertura nuova, come [openingAge]. */
  var birthClock = Animatable(BirthDoneMillis, 0.5f)
    private set

  /** La sagoma al congedo, se la nascita non era finita: il ritorno parte da li'. Null: dal pannello. */
  private var frozen: FluidForm.Slab? = null
  private var frozenProgress = 1f
  private var frozenMillis = -1f

  /** La nascita di Apple e' accesa: menu liquido, chiesta dall'host, da un tasto o da un gruppo. */
  val born: Boolean get() = appleBirth && liquidMenu && anchor != null

  /** Il menu nasce da un gruppo (la graffetta) e non da un tasto singolo. */
  private val fromGroup: Boolean get() = anchor?.groupBounds != null

  val faceProgress: Float get() = when {
    returningOnContour -> fluidMenuReturnFaceProgress(progress.value)
    born && !closing -> fluidMenuBirthFaceProgress(birthClock.value)
    else -> progress.value
  }

  /**
   * Quanto delle voci c'e' nella goccia in apertura (B1 a: subito, ingrandite; B1 b: da 67 a 117 ms). 1 senza la nascita
   * e in chiusura, dove l'inchiostro e' [contentVisibility].
   */
  fun birthInk(): Float = if (!born || closing) 1f else fluidMenuBirthInk(fromGroup, birthClock.value)

  /**
   * L'opacita' delle voci nella lente: quella della messa a fuoco ([fluidMenuLensAlpha]) per l'inchiostro, e con la nascita
   * le voci che entrano quando in Apple ([birthInk]). Al congedo con la nascita solo l'inchiostro di Apple
   * ([contentVisibility], [fluidContextExitInkSpec]): in B1 (a) le voci che se ne vanno sono sfocate ma piene fino a ~100 ms,
   * e la messa a fuoco che torna in 100 ms le avrebbe gia' spente a 0,3 verso 67 ms.
   */
  fun lensInkAlpha(): Float =
    if (born && closing) contentVisibility.value else fluidMenuLensAlpha(focus.value) * contentVisibility.value * birthInk()

  /** Il congedo di un menu nato come Apple: la lente del tasto ingrandisce le voci che se ne vanno (vedi [fluidMenuLensMap]). */
  val bornReturn: Boolean get() = born && closing

  fun faceAlpha(physics: FluidPhysicsState): Float =
    if (closing && liquidMenu && !controlledReturn && anchor != null) {
      fluidModalReturnFaceAlpha(progress.value, physics.ensurePlan().bounds, startFrame())
    } else fluidMenuFaceAlpha(faceProgress)

  /** 0 = il fotogramma di partenza e' il tasto premuto, 1 = a riposo. Nuovo a ogni apertura. */
  var anchorRelax = Animatable(1f, ProgressThreshold)
    private set

  var target by mutableFloatStateOf(0f)
    private set

  private var seedVelocity = 0f

  /** L'ancora da cui si parte, o null per un pannello che cresce da un seme ([seed]). */
  var anchor: FluidMenuAnchor? = null
    private set

  /** Dove sta l'host nella radice: le ancore parlano radice, la superficie parla host. */
  var hostOrigin: Offset = Offset.Zero
    private set

  /** Il pannello, in coordinate dell'host. */
  var to: Rect = Rect.Zero
    private set

  var toRadius: Float = 0f
    private set

  /** Il seme senza ancora, in coordinate dell'host. */
  var seed: Rect? = null
    private set

  var inflation: FluidMenuInflation = FluidMenuInflation.None
    private set

  /** Il verso di adesso: le finestre di opacita' del contenuto dipendono da questo. */
  val closing: Boolean get() = target == 0f

  /** Il viaggio di ritorno e' finito: l'host puo' rimettere il tasto e smontare. */
  val settledClosed: Boolean get() = target == 0f && !progress.isRunning && progress.value <= ProgressThreshold

  private val startFrameBlock: () -> Rect = {
    val start = startFrame()
    if (born) {
      // La goccia e' ancorata dove nasce: al tasto premuto (nel gruppo, la goccia sul tasto e non il centro della capsula).
      // Al ritorno, come prima, alla sorgente intera.
      if (fromGroup && !closing) birthDrop() else start
    } else if (liquidMenu && !returningOnContour) {
      val a = anchor
      val trigger = a?.restBounds?.center?.minus(hostOrigin) ?: start.center
      fluidMenuJourneySeed(start, trigger, expansionOrigin, progress.value, a?.groupBounds != null)
    } else {
      start
    }
  }
  private val progressBlock: () -> Float = {
    if (liquidMenu && !born && !returningOnContour && anchor?.groupBounds != null) {
      fluidMenuExpansionProgress(progress.value)
    } else {
      progress.value
    }
  }
  private val travelBlock: () -> FluidForm.Slab? = travel@{
    if (born) return@travel birthTravel()
    val p = progress.value
    if (p >= 1f && openingAge.value == 1f) return@travel null
    val panel = FluidForm.Slab(to, FluidCornerRadii.all(toRadius))
    val source = startFrame()
    val from = FluidForm.Slab(source, FluidCornerRadii.all(source.minDimension / 2f))
    when {
      returningOnContour -> fluidMenuReturnSlab(from, panel, p)
      p < 0f -> null
      else -> {
        val age = openingAge.value * FluidMotion.MenuOvershootEndMillis
        val base = when {
          p >= 1f -> panel
          anchor?.groupBounds != null -> {
            val seed = startFrameBlock()
            val seedSlab = FluidForm.Slab(seed, FluidCornerRadii.all(seed.minDimension / 2f))
            dropRestSlab(seedSlab, panel, fluidMenuExpansionProgress(p), 0f, 0f)
          }
          else -> fluidMenuButtonTravel(source, to, toRadius, expansionOrigin, p)
        }
        // Un'inversione a metà non sposta il profilo: l'accento si spegne mentre il vetro si ritira.
        val weight = unitRamp(p, FluidMotion.MenuButtonBubble, FluidMotion.MenuButtonPanel)
        val swollen = fluidMenuOpeningOvershoot(base, to, source, age, inflation.widthRoom)
        if (swollen == base) base else base.copy(frame = lerp(base.frame, swollen.frame, weight))
      }
    }
  }

  /**
   * Il fotogramma di partenza di QUESTO istante, in coordinate dell'host: il tasto premuto che torna
   * a riposo mentre il viaggio corre. Fase di disegno.
   */
  fun startFrame(): Rect {
    val a = anchor ?: return seed ?: to
    val r = a.groupBounds ?: lerp(a.pressedBounds, a.restBounds, anchorRelax.value.coerceIn(0f, 1f))
    return r.translate(-hostOrigin.x, -hostOrigin.y)
  }

  // --- La nascita di Apple (sessione contestuale, fase tasto) --------------------------------------------------------

  /** Il tasto premuto del gruppo, in coordinate dell'host. */
  private fun birthTrigger(): Rect {
    val r = anchor?.restBounds ?: return to
    return r.translate(-hostOrigin.x, -hostOrigin.y)
  }

  /** La goccia sul tasto premuto del gruppo, in coordinate dell'host ([fluidGroupBirthDrop]). */
  private fun birthDrop(): Rect = fluidGroupBirthDrop(birthTrigger())

  /**
   * La sagoma della nascita a [ms]: il tasto come B1 (a), il gruppo come B1 (b). Null dalla fine: il pannello. Con [lead]
   * quella da chiedere alla goccia (in anticipo, perche' quella che si vede sia Apple); senza, quella che si vede.
   */
  private fun birthSlabAt(ms: Float, lead: Boolean = true): FluidForm.Slab? {
    val a = anchor ?: return null
    val group = a.groupBounds
    return if (group != null) {
      fluidGroupBirthSlab(group.translate(-hostOrigin.x, -hostOrigin.y), birthTrigger(), to, toRadius, ms, lead)
    } else {
      fluidButtonBirthSlab(startFrame(), to, toRadius, ms, lead)
    }
  }

  /**
   * La sagoma di questo istante con la nascita accesa: in apertura quella di Apple all'istante dell'orologio; in chiusura
   * il ritorno dei menu ([fluidMenuReturnSlab], i 233 ms di B1 a) verso la sorgente, dal pannello o dalla sagoma fermata al
   * congedo. Null a riposo.
   */
  private fun birthTravel(): FluidForm.Slab? {
    if (!closing) return birthSlabAt(birthClock.value)
    val source = startFrame()
    val from = FluidForm.Slab(source, FluidCornerRadii.all(source.minDimension / 2f))
    val home = frozen ?: FluidForm.Slab(to, FluidCornerRadii.all(toRadius))
    val q = (progress.value / frozenProgress.coerceAtLeast(ProgressThreshold)).coerceIn(0f, 1f)
    return fluidMenuReturnSlab(from, home, q)
  }

  /**
   * Il contorno della nascita, per `driveExternally(travelWarp = ...)`: nel gruppo la goccia con la lente, il fagiolo e
   * l'uovo storto con la coda verso il tasto ([fluidGroupBirthWarp]); al congedo a meta' nascita quello di quell'istante,
   * che si spegne mentre il ritorno lascia la sagoma fermata. Il tasto singolo non ne ha: B1 (a) e' un rettangolo
   * arrotondato che cresce, senza uovo.
   */
  private val warpBlock: () -> FluidDropWarp? = warp@{
    if (!born || !fromGroup) return@warp null
    if (closing) {
      if (frozenMillis < 0f) return@warp null
      val slab = birthTravel() ?: return@warp null
      val q = (progress.value / frozenProgress.coerceAtLeast(ProgressThreshold)).coerceIn(0f, 1f)
      return@warp fluidGroupBirthWarpFading(birthTrigger(), to, slab, frozenMillis, q)
    }
    val ms = birthClock.value
    val slab = birthSlabAt(ms) ?: return@warp null
    fluidGroupBirthWarp(birthTrigger(), to, slab, ms)
  }

  /**
   * Ferma la sagoma della nascita al congedo, nell'istante in cui si decide (vedi [retarget]): quella che si vede, senza
   * l'anticipo per la goccia. Con l'anticipo il ritorno partiva da una sagoma piu' avanti di quella sullo schermo, e il
   * corpo cresceva ancora del 7% per due fotogrammi prima di tornare (primo giro della fase tasto, `interrotto-gruppo`).
   */
  private fun freezeBirth() {
    val ms = birthClock.value
    val slab = if (ms < fluidMenuBirthEndMillis(fromGroup)) birthSlabAt(ms, lead = false) else null
    frozen = slab
    frozenProgress = if (slab != null) progress.value.coerceAtLeast(ProgressThreshold) else 1f
    frozenMillis = if (slab != null) ms else -1f
  }

  /**
   * L'apertura con la nascita: l'orologio fino alla fine, il progresso del viaggio sul profilo di Apple, la messa a fuoco
   * (B1 a: lineare in 270 ms, come prima; B1 b: in 334 ms come il contestuale), l'inchiostro che riprende da dove si trova.
   * Un menu riaperto a meta' chiusura riprende la nascita dall'istante del suo progresso.
   */
  private suspend fun openBirth(): Unit = coroutineScope {
    val group = fromGroup
    val end = fluidMenuBirthEndMillis(group)
    val from = progress.value
    val start = if (from <= ProgressThreshold) birthClock.value.coerceIn(0f, end) else fluidMenuBirthMillisAt(group, from)
    birthClock.snapTo(start)
    frozen = null
    frozenProgress = 1f
    frozenMillis = -1f
    val remaining = (end - start).coerceAtLeast(1f)
    val millis = ceil(remaining).toInt()
    launch { birthClock.animateTo(end, tween(millis, easing = LinearEasing)) }
    launch {
      val f = focus.value
      val spec = if (group) {
        tween<Float>((FluidContextFocusMillis * (1f - f)).toInt().coerceAtLeast(1), easing = FluidContextFocusEasing)
      } else {
        tween((FluidMotion.MenuContentFocusMillis * (1f - f)).roundToInt(), easing = LinearEasing)
      }
      focus.animateTo(1f, spec)
    }
    launch {
      val visibility = contentVisibility
      val duration = (FluidMotion.MenuContentExitMillis * (1f - visibility.value)).roundToInt()
      visibility.animateTo(1f, tween(duration, easing = LinearEasing))
    }
    // Il superamento e' nel profilo di Apple: l'accento del viaggio di prima non serve.
    launch { openingAge.snapTo(1f) }
    val relax = anchorRelax
    if (relax.value < 1f) launch { relax.animateTo(1f, FluidMotion.snappy()) }
    val span = 1f - from
    if (span > 0.0001f) {
      progress.animateTo(
        1f,
        tween(millis, easing = Easing { f -> (fluidMenuBirthProgress(group, start + f * remaining) - from) / span }),
      )
    } else {
      progress.snapTo(1f)
    }
  }

  /**
   * Il congedo con la nascita: il ritorno di 233 ms dal pannello fermo (o la molla dei menu liquidi da una nascita a
   * meta'), le voci che si sfocano subito ma restano dentro il vetro fino a ~67 ms e se ne vanno entro ~167
   * ([fluidContextExitInkSpec], l'inchiostro di B1 a, lo stesso del contestuale).
   */
  private suspend fun closeBirth(velocity: Float): Unit = coroutineScope {
    birthClock.stop()
    launch { focus.animateTo(0f, fluidMenuReturnFocusSpec(focus.value)) }
    launch {
      val visibility = contentVisibility
      visibility.animateTo(0f, fluidContextExitInkSpec(visibility.value))
    }
    val spec = if (returningOnContour && progress.value == 1f) {
      fluidMenuReturnSpec()
    } else {
      androidx.compose.animation.core.spring<Float>(FluidMotion.DropJourneyDamping, FluidMotion.MenuLiquidCloseStiffness)
    }
    progress.animateTo(0f, spec, initialVelocity = velocity)
  }

  /**
   * Prepara un'apertura: l'ancora (o il seme), il pannello e il gonfiore. Non tocca [progress]: un
   * menu riaperto mentre si chiudeva riparte da dove si trova.
   *
   * @param freshStart un'apertura nuova (non la riapertura di un menu che si stava chiudendo, ne' una
   *   seconda chiamata per la stessa apertura): la pressione dell'ancora riparte dal tasto premuto.
   */
  fun prepare(
    anchor: FluidMenuAnchor?,
    hostOrigin: Offset,
    panel: Rect,
    radius: Float,
    inflation: FluidMenuInflation,
    seed: Rect? = null,
    freshStart: Boolean = true,
    liquidMenu: Boolean = false,
    expansionOrigin: Offset = panel.center,
    /**
     * La nascita di Apple ([born]): il tasto come B1 (a), il gruppo come la graffetta B1 (b). Vale solo per un menu liquido
     * con un'ancora.
     */
    appleBirth: Boolean = false,
  ) {
    if (anchor != null && (freshStart || this.anchor !== anchor)) {
      // Una partenza nuova: la pressione si scioglie da qui. Un `Animatable` nuovo e non uno snapTo,
      // che e' sospeso: la partenza deve essere giusta gia' al primo fotogramma, che arriva prima di
      // qualunque coroutine.
      anchorRelax = Animatable(if (anchor.pressedBounds == anchor.restBounds) 1f else 0f, ProgressThreshold)
    }
    if (liquidMenu && freshStart) {
      contentVisibility = Animatable(1f, ProgressThreshold)
      openingAge = Animatable(0f, ProgressThreshold)
      returningOnContour = false
      if (appleBirth) {
        // Un `Animatable` nuovo e non uno snapTo, come `anchorRelax`: il primo fotogramma arriva prima di qualunque coroutine.
        birthClock = Animatable(0f, 0.5f)
        frozen = null
        frozenProgress = 1f
        frozenMillis = -1f
      }
    }
    this.appleBirth = appleBirth
    this.anchor = anchor
    this.hostOrigin = hostOrigin
    this.to = panel
    this.toRadius = radius
    this.inflation = inflation
    this.seed = seed
    this.liquidMenu = liquidMenu
    this.expansionOrigin = expansionOrigin
    anchor?.groupProgress = progressBlockForGroup
    anchor?.groupFacesAbove = groupFacesAboveBlock
  }

  // Il ritorno lascia il gruppo spento finche' il vetro non torna capsula, e lo riaccende con lei (S4b, NO 4): le icone le
  // disegna l'host sopra il vetro ([groupFacesAboveBlock]), e alla consegna sono gia' piene.
  private val progressBlockForGroup: () -> Float = { if (closing) fluidMenuGroupFaceReturn(progress.value) else faceProgress }
  private val groupFacesAboveBlock: () -> Boolean = { closing }

  /** Mette la finestra di [physics] al servizio di questo viaggio. Gratis se gli estremi non cambiano. */
  fun install(physics: FluidPhysicsState) {
    val a = anchor
    physics.useSampledDropFrame = liquidMenu && a != null
    // La goccia del tasto singolo e' piu' tesa (B1 a: una sagoma netta e rapidissima); le altre quella di serie.
    physics.dropAnchorReachDp = if (born && !fromGroup) FluidButtonBirthProfile.DropReachDp else FluidMotion.DropAnchorReachDp
    // Il `from` e' fisso per un'apertura (il tasto premuto, o il seme): e' il segnaposto con cui
    // `driveExternally` riconosce la stessa corsa. La partenza vera, viva, la da' [startFrame].
    // F1B.3: il segnaposto ha la taglia del tasto a riposo (vedi [fluidMenuOriginFrame]).
    val start = if (a != null && born && a.groupBounds != null) {
      // La nascita del gruppo e' ancorata alla goccia sul tasto anche a progresso oltre l'1 (vedi `dropAnchorNow`).
      birthDrop()
    } else if (a != null) {
      val frame = a.groupBounds ?: fluidMenuOriginFrame(rest = a.restBounds, pressed = a.pressedBounds)
      frame.translate(-hostOrigin.x, -hostOrigin.y)
    } else {
      seed ?: to
    }
    // Tondo per costruzione: il tasto e' una capsula, la goccia del contestuale un cerchio.
    val from = FluidForm.Slab(
      frame = start,
      cornerRadii = FluidCornerRadii.all(start.minDimension / 2f),
    )
    physics.driveExternally(
      from = from,
      to = FluidForm.Slab(to, FluidCornerRadii.all(toRadius)),
      progress = progressBlock,
      overshootInflationX = if (liquidMenu) 0f else inflation.x,
      overshootInflationY = if (liquidMenu) 0f else inflation.y,
      startFrame = if (a != null) startFrameBlock else null,
      travelSlab = travelBlock.takeIf { liquidMenu },
      travelWarp = warpBlock.takeIf { born },
    )
  }

  /**
   * Cambia verso. Chiamata nel momento in cui si decide (il gesto, il congedo, o la composizione che
   * se ne accorge), MAI dopo: la velocita' si legge qui, finche' la molla vecchia e' viva.
   */
  fun retarget(t: Float) {
    if (target == t) return
    // A pannello posato parte il ritorno rapido scelto per il tasto. A metà corsa
    // si conserva il contorno che si sta già seguendo, anche se cambia il verso.
    if (liquidMenu) {
      if (t == 0f && !progress.isRunning && progress.value == 1f) returningOnContour = true
      if (t == 1f && progress.value <= 0f) returningOnContour = false
    }
    // La sagoma della nascita si ferma ADESSO: dal prossimo fotogramma la chiusura parte da lei, non dal pannello pieno.
    if (born && t == 0f) freezeBirth()
    seedVelocity = progress.velocity
    target = t
  }

  /** Corre verso [target]. Sospende finche' non arriva; cancellabile (un [retarget] la sostituisce). */
  suspend fun run(reducedMotion: Boolean, withLens: Boolean = liquidMenu) {
    val t = target
    val v = seedVelocity
    seedVelocity = 0f
    if (reducedMotion) {
      anchorRelax.snapTo(1f)
      progress.snapTo(t)
      focus.snapTo(t)
      contentVisibility.snapTo(t)
      openingAge.snapTo(1f)
      birthClock.snapTo(BirthDoneMillis)
      return
    }
    if (born && withLens) {
      if (t >= 1f) openBirth() else closeBirth(v)
      return
    }
    coroutineScope {
      if (withLens) launch {
        val duration = (FluidMotion.MenuContentFocusMillis * (1f - focus.value)).roundToInt()
        val spec = if (t == 0f) fluidMenuReturnFocusSpec(focus.value) else tween(duration, easing = LinearEasing)
        focus.animateTo(t, spec)
      }
      if (withLens) launch {
        val visibility = contentVisibility
        val duration = (FluidMotion.MenuContentExitMillis * (1f - visibility.value)).roundToInt()
        val spec = if (t == 0f) fluidMenuReturnInkSpec(visibility.value) else tween(duration, easing = LinearEasing)
        visibility.animateTo(t, spec)
      }
      if (liquidMenu && !returningOnContour && openingAge.value < 1f) launch {
        val age = openingAge
        val duration = (FluidMotion.MenuOvershootEndMillis * (1f - age.value)).roundToInt()
        age.animateTo(1f, tween(duration, easing = LinearEasing))
      }
      if (t >= 1f) {
        val relax = anchorRelax
        if (relax.value < 1f) launch { relax.animateTo(1f, FluidMotion.snappy()) }
        // La raccolta e la risalita conservano la molla d'andata. L'accento ha un orologio distinto.
        progress.animateTo(1f, FluidMotion.dropOpen(), initialVelocity = v)
      } else {
        val spec = if (returningOnContour && progress.value == 1f) {
          fluidMenuReturnSpec()
        } else if (liquidMenu) {
          androidx.compose.animation.core.spring<Float>(FluidMotion.DropJourneyDamping, FluidMotion.MenuLiquidCloseStiffness)
        } else {
          FluidMotion.dropClose<Float>()
        }
        progress.animateTo(0f, spec, initialVelocity = v)
      }
    }
  }

  /** Dimentica l'ancora a viaggio finito: un menu chiuso non tiene in mano un tasto che non c'e'. */
  fun clearAnchor() {
    anchor = null
    seed = null
  }

  private companion object {
    const val ProgressThreshold = 0.001f

    /** L'orologio di una nascita finita (o mai cominciata): oltre la fine di tutte e due. */
    const val BirthDoneMillis = 10_000f
  }
}
