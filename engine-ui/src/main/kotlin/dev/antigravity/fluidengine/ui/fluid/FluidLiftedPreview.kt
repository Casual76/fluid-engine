package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
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
) {
  if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) return
  if (preview == null) return
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
  val materialPresence: () -> Float = { fluidLiftMaterialPresence(presence(), lift(), closing()) }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawWithContent {
        if (materialPresence() > 0.004f) drawContent()
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

  Box(
    modifier = Modifier
      .absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
      .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
      .graphicsLayer {
        val map = fluidLiftContentMap(bounds, physics.contentBoundsNow())
        translationX = map.translation.x
        translationY = map.translation.y
        scaleX = map.scale
        scaleY = map.scale
        clip = true
        this.shape = clipShape
      }
      .drawBehind { if (!preview.isReleased) drawLayer(preview) },
  )
}

/** Misura una sola istanza dello slot prima del disegno. Il vetro e le due immagini sono figli
 * dello stesso ritaglio, in coordinate locali. Solo la molla della card ne muove i quattro bordi. */
@Composable
internal fun FluidCustomLiftedPreview(
  source: Rect,
  recording: GraphicsLayer?,
  custom: @Composable () -> Unit,
  shape: Shape?,
  viewport: Rect,
  margins: Rect,
  progress: () -> Float,
  anchorScale: () -> Float,
  presence: () -> Float,
  paneTint: Color?,
  backdrop: GlassBackdropState?,
  onMeasured: (IntSize) -> Unit,
) {
  if (source.isEmpty || margins.isEmpty) return
  val clipShape = shape ?: ContinuousCornerShape(FluidPopoverRadius)
  val blurPx = with(LocalDensity.current) { FluidLiftSwapBlur.toPx() }
  val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
  var destination by remember { mutableStateOf(source) }
  fun frame() = fluidCustomPreviewFrame(source, anchorScale(), destination, viewport, margins, progress())
  fun mix() = if (recording == null || recording.isReleased || recording.size == IntSize.Zero) 1f
    else fluidPreviewMix(progress())
  val materialPresence = { fluidLiftMaterialPresence(presence(), progress(), closing = false, custom = true) }

  Box(
    Modifier.absoluteOffset {
      val rect = frame()
      IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
    }.clip(clipShape),
    contentAlignment = androidx.compose.ui.AbsoluteAlignment.TopLeft,
  ) {
    // Il vetro resta in coordinate dello schermo. La sua misura ? quella del contenitore:
    // nessun secondo contorno elastico che insegua la foto e traspaia attraverso di lei.
    Box(Modifier.matchParentSize().drawWithContent {
      if (materialPresence() > 0.004f) drawContent()
    }.then(
      if (backdrop != null) Modifier.glassSurface(
        state = backdrop, shape = clipShape, tint = GlassDefaults.panelTint(paneTint),
        optics = GlassDefaults.panelOptics(), role = GlassRole.Modal, intensity = materialPresence,
      ) else Modifier.drawBehind { drawRect(fallback.copy(alpha = fallback.alpha * materialPresence())) },
    ))
    Layout(
      modifier = Modifier.graphicsLayer {
        // Plus somma le due immagini premoltiplicate su trasparente. Il buffer ha la taglia
        // della card e serve solo nell'incrocio. Anche due slot traslucidi conservano il loro alfa.
        val i = mix()
        compositingStrategy = if (i > 0f && i < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
      },
      content = {
        Box(Modifier.drawBehind { if (recording != null && !recording.isReleased) drawLayer(recording) })
        Box { custom() }
      },
    ) { children, _ ->
      val cap = fluidContextPreviewMaxSize(margins.size, 0f)
      val incoming = children[1].measure(Constraints(maxWidth = cap.width.roundToInt(), maxHeight = cap.height.roundToInt()))
      val measured = IntSize(incoming.width, incoming.height)
      destination = fluidPreviewFit(source.center, Size(measured.width.toFloat(), measured.height.toFloat()), margins)
      onMeasured(measured)
      val natural = recording?.size?.takeUnless { it == IntSize.Zero }
      val outgoing = children[0].measure(Constraints.fixed(natural?.width ?: 0, natural?.height ?: 0))
      val rect = frame()
      val w = (rect.right.roundToInt() - rect.left.roundToInt()).coerceAtLeast(0)
      val h = (rect.bottom.roundToInt() - rect.top.roundToInt()).coerceAtLeast(0)
      layout(w, h) {
        val i = mix()
        fun place(image: androidx.compose.ui.layout.Placeable, weight: Float, add: Boolean) {
          if (weight <= 0f || image.width == 0 || image.height == 0) return
          val cover = maxOf(w.toFloat() / image.width, h.toFloat() / image.height)
          image.placeWithLayer(0, 0) {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = cover
            scaleY = cover
            translationX = (w - image.width * cover) * 0.5f
            translationY = (h - image.height * cover) * 0.5f
            alpha = weight
            blendMode = if (add) BlendMode.Plus else BlendMode.SrcOver
            renderEffect = fluidLiftSwapBlur((1f - weight) * blurPx)
          }
        }
        place(outgoing, 1f - i, false)
        place(incoming, i, i < 1f)
      }
    }
  }
}

internal fun fluidPreviewMix(progress: Float): Float = liftRamp(progress, FluidLiftSwapInFrom, FluidLiftSwapInTo)

/**
 * Dove sta il contenuto della card (l'immagine dell'elemento, o l'anteprima) sulla sagoma di questo istante: centrato
 * sul centro corrente e scalato del **minore** dei due rapporti dei lati. Non quello dei lati corti dei menu
 * ([fluidMenuContentMap]): una riga e' larga e bassa, e il gonfiore dell'arrivo e' piu' alto che largo, quindi la
 * scala dei lati corti porterebbe il testo fuori dai fianchi del vetro. Identita' se uno dei due e' degenere.
 *
 * Con [fill] il **maggiore** dei due: l'immagine copre tutta la sagoma e quello che sporge lo taglia il ritaglio del corpo.
 * E' lo scambio con un'anteprima personalizzata (S4c): due immagini di proporzioni diverse dentro un contorno solo.
 */
internal fun fluidLiftContentMap(target: Rect, current: Rect, fill: Boolean = false): FluidMenuContentMap {
  if (target.minDimension <= 0f || current.minDimension <= 0f) return FluidMenuContentMap.Identity
  val sx = current.width / target.width
  val sy = current.height / target.height
  return FluidMenuContentMap(
    translation = current.center - target.center,
    scale = if (fill) maxOf(sx, sy) else minOf(sx, sy),
  )
}

/**
 * Dove puo' stare il vetro della card: con un'anteprima personalizzata ([custom]) la sagoma del corpo di adesso ([current]),
 * la stessa che ritaglia le due immagini dello scambio; null senza, perche' una riga sollevata e' lei stessa il vetro.
 *
 * Fino a S4b era il riquadro dell'immagine davanti ([fluidLiftPaintedFrame]): le immagini stavano dentro la sagoma con la
 * scala che le contiene, e il vetro andava tagliato su di loro per non restare scoperto ai fianchi. Ma le due immagini hanno
 * proporzioni diverse e ognuna aveva la sua cornice: due contorni nello scambio (S4c, ripresa 22). Ora le immagini riempiono
 * il corpo ([fluidLiftContentMap] con `fill`) e il contorno e' uno solo.
 */
internal fun fluidLiftMaterialFrame(custom: Boolean, current: Rect): Rect? = if (custom) current else null

/** Riquadro effettivamente coperto dall'immagine, dopo la stessa scala uniforme della registrazione ([fill] come la mappa). */
internal fun fluidLiftPaintedFrame(natural: Rect, current: Rect, fill: Boolean = false): Rect {
  val map = fluidLiftContentMap(natural, current, fill)
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
