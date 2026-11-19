package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import kotlin.math.roundToInt

// La riga sollevata del menu contestuale, trasformata in una card dello stesso materiale del menu. Un file
// a sé: l'anteprima del contestuale si cambia senza toccare lo scrim né il menu (spezzata da
// `FluidGlassModal.kt` in F5A.0; sollevamento continuo e anteprima personalizzata in F5A.4).

/**
 * The row, lifted out of the page and turned into a pane of the menu's own material.
 *
 * Not a scaled copy of itself. The first version simply replayed the row's recording a little
 * larger, and what that reads as is the row *doubling* — there are suddenly two of it, one blurred
 * and one not, and neither has become anything. What actually happens on iOS is that the thing you
 * held **turns into** the same glass card the menu is made of, and keeps its own contents printed on
 * it.
 *
 * Dalla 3.0 (fase 7) la card e' una superficie di Fluid-physics SORELLA dell'immagine, con lo stesso
 * progresso del menu, e il suo materiale entra ed esce con [presence]. A scalare e' solo l'immagine
 * registrata, che non e' vetro: prima si scalava il layer di un `glassSurface`, e il fondale dentro la card
 * si muoveva con lei (la prima trappola).
 *
 * **Il sollevamento (F5A.4).**
 *  - La card parte **dalla scala che l'elemento ha sotto il dito** ([anchorScale]), non dall'elemento a riposo:
 *    al click lungo la riga gonfiata del 2% e la card sono lo stesso rettangolo, e la card continua da li'
 *    ([fluidLiftStartFraction]). Prima la card ripartiva da 1 e la riga rimpiccioliva in un fotogramma.
 *  - Cresce **attorno al centro**, come la riga sotto il dito (prima dal bordo basso).
 *  - L'immagine **segue la sagoma del vetro** (la stessa mappa del contenuto dei menu: traslazione e una scala
 *    sola, presa dalla larghezza), non una scala calcolata a parte: col gonfiore dell'arrivo e la goccia che
 *    si posa, vetro e contenuto restano un oggetto solo.
 *  - Con un'anteprima personalizzata ([custom]) la card va dall'elemento all'anteprima, e i due contenuti si
 *    scambiano **attraverso la sfocatura** (R7: cio' che cambia identita' entra sfocato e si mette a fuoco).
 *  - La forma ([shape]) e' la sagoma del vetro e il ritaglio del contenuto insieme.
 *
 * Mai sfumata, l'immagine dell'elemento: la riga vera e' nascosta finche' il layer non smonta, e a vita finita
 * l'immagine e' esattamente sulla riga che ha registrato — comparire e sparire sono scambi identici al pixel.
 */
@Composable
internal fun FluidLiftedPreview(
  preview: GraphicsLayer?,
  bounds: Rect?,
  /**
   * Il colore della riga che si e' sollevata.
   *
   * E' **qui** che il colore serve, non sul pannello delle azioni: la riga sollevata e' l'oggetto, e
   * una card colorata che diventa vetro grigio smette di essere quella card. Il menu invece e'
   * chrome, e la chrome non prende il colore di cio' che l'ha chiamata.
   */
  paneTint: Color?,
  backdrop: GlassBackdropState?,
  physics: FluidPhysicsState,
  progress: () -> Float,
  presence: () -> Float,
  /**
   * La card si gonfia oltre il sollevamento e si posa con la molla del menu (3.0, il vetro liquido):
   * una goccia che si stacca dalla lista, non un ritaglio incollato sopra. Il gonfiore e' geometria,
   * attorno al centro, e l'immagine lo segue con una scala sola.
   */
  swells: Boolean = false,
  /** Dove si posa la card ([fluidContextLiftTarget]); null: l'elemento sollevato ([fluidLiftedFrame]). */
  target: Rect? = null,
  /** Il menu si sta chiudendo: la partenza smette di essere quella fermata al click lungo. */
  closing: () -> Boolean = { false },
  /** La scala viva dell'elemento sotto il dito ([FluidContextLift.anchorScale]). */
  anchorScale: () -> Float = { 1f },
  /** La forma della card e del ritaglio; null: il raggio dei pannelli. */
  shape: Shape? = null,
  /** L'anteprima personalizzata, o null: la card e' l'elemento registrato. */
  custom: (@Composable () -> Unit)? = null,
  /** Il tetto della taglia di [custom] ([fluidContextPreviewMaxSize]). */
  customMaxSize: Size? = null,
  /** La taglia di [custom] appena misurata: decide [target] e il posto del menu. */
  onCustomMeasured: (IntSize) -> Unit = {},
) {
  if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) return
  if (preview == null && custom == null) return
  val density = LocalDensity.current
  val layoutDirection = LocalLayoutDirection.current
  val radiusPx = with(density) { FluidPopoverRadius.toPx() }
  val rest = target ?: fluidLiftedFrame(bounds)
  val clipShape = shape ?: ContinuousCornerShape(FluidPopoverRadius)

  // La scala dell'elemento al click lungo, fermata per tutta l'andata: se il dito si alza mentre la card sale,
  // la partenza non deve tornare indietro sotto di lei. Al ritorno torna viva (a dito alzato, 1): la card
  // finisce esattamente dove la riga ricompare.
  val frozenScale = remember { floatArrayOf(Float.NaN) }
  val startScale: () -> Float = {
    if (closing()) {
      frozenScale[0] = Float.NaN
      anchorScale()
    } else {
      if (frozenScale[0].isNaN()) frozenScale[0] = anchorScale()
      frozenScale[0]
    }
  }
  val lift: () -> Float = {
    fluidLiftProgress(progress().coerceAtMost(if (swells) Float.MAX_VALUE else 1f).coerceAtLeast(0f), fluidLiftStartFraction(bounds, rest, startScale()))
  }
  // Il velo e i contenuti usano lo stesso scambio. Con taglie di proporzioni diverse, il materiale
  // non deve disegnare le bande che restano libere attorno alla foto scalata uniformemente (S10a).
  val outgoing: () -> Float = { if (custom == null) 1f else 1f - liftRamp(lift(), FluidLiftSwapOutFrom, FluidLiftSwapOutTo) }
  val incoming: () -> Float = { liftRamp(lift(), FluidLiftSwapInFrom, FluidLiftSwapInTo) }
  val materialClip = remember { Path() }

  SideEffect {
    physics.driveExternally(
      from = FluidForm.Slab(bounds, fluidContextPreviewRadii(shape, bounds.size, density, layoutDirection, radiusPx)),
      to = FluidForm.Slab(rest, fluidContextPreviewRadii(shape, rest.size, density, layoutDirection, radiusPx)),
      progress = lift,
      overshootInflationX = if (swells) FluidMotion.LiftedSwellX else 0f,
      overshootInflationY = if (swells) FluidMotion.LiftedSwellY else 0f,
    )
  }
  val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
  val materialPresence: () -> Float = { fluidLiftMaterialPresence(presence(), lift(), closing(), custom = custom != null) }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawWithContent {
        if (materialPresence() <= 0.004f) return@drawWithContent
        if (custom == null || !closing()) {
          drawContent()
        } else {
          val natural = if (incoming() >= outgoing()) rest else bounds
          val painted = fluidLiftPaintedFrame(natural, physics.contentBoundsNow())
          if (painted.isEmpty) return@drawWithContent
          materialClip.reset()
          when (val outline = clipShape.createOutline(painted.size, layoutDirection, density)) {
            is Outline.Rectangle -> materialClip.addRect(outline.rect.translate(painted.topLeft))
            is Outline.Rounded -> materialClip.addRoundRect(outline.roundRect.translate(painted.topLeft))
            is Outline.Generic -> materialClip.addPath(outline.path, painted.topLeft)
          }
          clipPath(materialClip) { this@drawWithContent.drawContent() }
        }
      }
      .then(
        if (backdrop != null) {
          Modifier.fluidPhysicsSurface(
            state = physics,
            backdrop = backdrop,
            tint = GlassDefaults.panelTint(paneTint),
            role = GlassRole.Modal,
            optics = GlassDefaults.panelOptics(),
            // Il materiale entra ed esce; l'immagine no. E' questa la transizione della chiusura: il
            // colore torna quello di prima e il bordo si spegne, invece di essere scambiati di colpo.
            intensity = materialPresence,
          )
        } else {
          Modifier.drawBehind {
            drawPath(physics.ensurePlan().silhouette, fallback.copy(alpha = fallback.alpha * materialPresence()))
          }
        },
      ),
  )

  // Lo scambio fra l'elemento e l'anteprima, attraverso la sfocatura: l'elemento se ne va nella prima meta'
  // del sollevamento, l'anteprima arriva dalla seconda. Senza anteprima l'elemento resta pieno e nitido.
  val swapBlurPx = with(density) { FluidLiftSwapBlur.toPx() }

  if (preview != null) {
    Box(
      modifier = Modifier
        .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
        .size(
          width = with(density) { bounds.width.toDp() },
          height = with(density) { bounds.height.toDp() },
        )
        .graphicsLayer {
          // L'immagine, non il vetro: scalarla e' lecito. Sulla sagoma di questo istante, con una scala sola
          // (dalla larghezza: il gonfiore dell'arrivo e' piu' alto che largo, e un'immagine stirata e' un
          // testo schiacciato).
          val map = fluidLiftContentMap(target = bounds, current = physics.contentBoundsNow())
          translationX = map.translation.x
          translationY = map.translation.y
          scaleX = map.scale
          scaleY = map.scale
          val a = outgoing().coerceIn(0f, 1f)
          alpha = a
          renderEffect = fluidLiftSwapBlur((1f - a) * swapBlurPx)
          clip = true
          this.shape = clipShape
        }
        .drawWithContent {
          if (outgoing() <= 0.004f) return@drawWithContent
          // Replaying a released layer is a crash, and the list underneath can dispose a row while
          // the menu is up. Nothing here is worth a crash: if the recording is gone, the pane simply
          // comes up empty and the menu keeps its actions.
          runCatching { drawLayer(preview) }
        },
    )
  }

  if (custom != null) {
    val max = customMaxSize
    Layout(
      modifier = Modifier.fillMaxSize(),
      content = {
        Box(
          modifier = Modifier.graphicsLayer {
            // L'anteprima e' posata alla sua taglia dove si posera' la card ([rest]): da li' segue la sagoma.
            val natural = Rect(rest.left.roundToInt().toFloat(), rest.top.roundToInt().toFloat(), rest.left.roundToInt() + size.width, rest.top.roundToInt() + size.height)
            val map = fluidLiftContentMap(target = natural, current = physics.contentBoundsNow())
            translationX = map.translation.x
            translationY = map.translation.y
            scaleX = map.scale
            scaleY = map.scale
            val a = incoming().coerceIn(0f, 1f)
            alpha = a
            renderEffect = fluidLiftSwapBlur((1f - a) * swapBlurPx)
            clip = true
            this.shape = clipShape
          },
        ) { custom() }
      },
    ) { measurables, constraints ->
      val cap = max ?: Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
      val placeable = measurables.first().measure(
        Constraints(maxWidth = cap.width.roundToInt().coerceAtLeast(1), maxHeight = cap.height.roundToInt().coerceAtLeast(1)),
      )
      onCustomMeasured(IntSize(placeable.width, placeable.height))
      layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(rest.left.roundToInt(), rest.top.roundToInt())
      }
    }
  }
}

/**
 * Dove sta il contenuto della card (l'immagine dell'elemento, o l'anteprima) sulla sagoma di questo istante: centrato
 * sul centro corrente e scalato del **minore** dei due rapporti dei lati. Non quello dei lati corti dei menu
 * ([fluidMenuContentMap]): una riga e' larga e bassa, e il gonfiore dell'arrivo e' piu' alto che largo, quindi la
 * scala dei lati corti porterebbe il testo fuori dai fianchi del vetro. Identita' se uno dei due e' degenere.
 */
internal fun fluidLiftContentMap(target: Rect, current: Rect): FluidMenuContentMap {
  if (target.minDimension <= 0f || current.minDimension <= 0f) return FluidMenuContentMap.Identity
  return FluidMenuContentMap(
    translation = current.center - target.center,
    scale = minOf(current.width / target.width, current.height / target.height),
  )
}

/** Riquadro effettivamente coperto dall'immagine, dopo la stessa scala uniforme della registrazione. */
internal fun fluidLiftPaintedFrame(natural: Rect, current: Rect): Rect {
  val map = fluidLiftContentMap(natural, current)
  val half = Offset(natural.width, natural.height) * (map.scale / 2f)
  val center = natural.center + map.translation
  return Rect(center - half, center + half)
}

/**
 * Una sfocatura per lo scambio, o niente sotto mezzo pixel (un `RenderEffect` a zero costa lo stesso). Coi bordi
 * **tenuti** (`Clamp`), non sfumati nel vuoto (`Decal`): sfumati, i bordi del contenuto diventavano trasparenti per
 * tutto il raggio, e dietro c'era il vetro chiaro della card col suo filo di luce — il bordo bianco attorno alla foto
 * che rientra (S10a, ritrovato da S4 sul tablet in A, B e C). Il contenuto ha gia' il suo ritaglio (`clip`, `shape`).
 */
internal fun fluidLiftSwapBlur(radiusPx: Float) =
  if (radiusPx < 0.5f) null else BlurEffect(radiusPx, radiusPx, TileMode.Clamp)

private fun liftRamp(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

/**
 * Quanto vetro ha la card sollevata. Senza anteprima personalizzata: tutto, e al ritorno, allo scambio con l'elemento
 * registrato, anche il filo del vetro lascia il posto al suo bordo.
 *
 * Con un'anteprima personalizzata ([custom]) il vetro e' della **piattaforma dell'anteprima**: arriva e se ne va con lei,
 * al cubo della sua opacita' (`incoming³`), all'andata e al ritorno. Il vetro sta dietro il contenuto, e nello scambio
 * il contenuto e' in parte trasparente (due immagini che si incrociano sfocate): col vetro pieno se ne vedevano la
 * pellicola chiara e il filo di luce attorno alla tessera che rientrava (S4, NO 1: A f387, B f1077, C f854–856). Cosi'
 * il vetro che traspare resta sotto l'11% (al piu' `i³·(1 − i)`), e quando il contenuto e' gia' l'elemento sotto il 5%.
 */
internal fun fluidLiftMaterialPresence(presence: Float, lift: Float, closing: Boolean, custom: Boolean = false): Float {
  val p = presence.coerceIn(0f, 1f)
  if (custom) {
    val i = liftRamp(lift, FluidLiftSwapInFrom, FluidLiftSwapInTo)
    return p * i * i * i
  }
  return p * if (closing) liftRamp(lift, FluidLiftSwapOutFrom, FluidLiftSwapOutTo) else 1f
}

/**
 * Lo scambio elemento → anteprima personalizzata, sul progresso della card: l'elemento se ne va fra
 * [FluidLiftSwapOutFrom] e [FluidLiftSwapOutTo], l'anteprima arriva fra [FluidLiftSwapInFrom] e [FluidLiftSwapInTo],
 * e chi e' a meta' e' sfocato di [FluidLiftSwapBlur] per la parte che gli manca. A occhio (2026-11-18), sulla regola
 * di R7: nei cambi di Apple il contenuto vecchio e' gia' sfocato all'8% del viaggio e il nuovo arriva sfocato
 * dentro il vetro dal 60% (`viaggi-contro-apple`); qui l'anteprima e' leggibile prima, perche' e' lei l'oggetto.
 */
internal const val FluidLiftSwapOutFrom = 0.05f
internal const val FluidLiftSwapOutTo = 0.45f
internal const val FluidLiftSwapInFrom = 0.15f
internal const val FluidLiftSwapInTo = 0.6f
internal val FluidLiftSwapBlur = 8.dp
