package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

// La riga sollevata del menu contestuale, trasformata in una card dello stesso materiale del menu. Un file
// a sé: l'anteprima del contestuale si cambia senza toccare lo scrim né il menu (spezzata da
// `FluidGlassModal.kt` in F5A.0).

/**
 * The row, lifted out of the page and turned into a pane of the menu's own material.
 *
 * Not a scaled copy of itself. The first version simply replayed the row's recording a little
 * larger, and what that reads as is the row *doubling* — there are suddenly two of it, one blurred
 * and one not, and neither has become anything. What actually happens on iOS is that the thing you
 * held **turns into** the same glass card the menu is made of, and keeps its own contents printed on
 * it.
 *
 * Dalla 3.0 (fase 7) la card e' una superficie di Fluid-physics SORELLA dell'immagine: la sagoma va
 * dalla riga alla riga gonfiata del 3% attorno al centro del bordo basso (quello contro cui si apre
 * il menu), con lo stesso progresso del menu, e il suo materiale entra ed esce con [presence]. A
 * scalare e' solo l'immagine registrata, che non e' vetro: prima si scalava il layer di un
 * `glassSurface`, e il fondale dentro la card si muoveva con lei (la prima trappola).
 *
 * Mai sfumata, l'immagine: la riga vera e' nascosta finche' il layer non smonta, e a vita finita
 * l'immagine e' a scala 1 esatta sulla riga che ha registrato — comparire e sparire sono scambi
 * identici al pixel.
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
) {
  if (preview == null || bounds == null || bounds.width <= 0f || bounds.height <= 0f) return
  val density = LocalDensity.current
  val radiusPx = with(density) { FluidPopoverRadius.toPx() }
  val lifted = Rect(
    left = bounds.center.x - bounds.width * FluidLiftedPreviewScale / 2f,
    top = bounds.bottom - bounds.height * FluidLiftedPreviewScale,
    right = bounds.center.x + bounds.width * FluidLiftedPreviewScale / 2f,
    bottom = bounds.bottom,
  )
  val lift: () -> Float = { progress().coerceIn(0f, 1f) }
  // L'oltrepasso della molla del menu, come gonfiore della card (zero senza [swells]).
  val swell: () -> Float = { if (swells) (progress() - 1f).coerceAtLeast(0f) else 0f }
  SideEffect {
    physics.driveExternally(
      from = FluidForm.Slab(bounds, FluidCornerRadii.all(radiusPx.coerceAtMost(bounds.minDimension / 2f))),
      to = FluidForm.Slab(lifted, FluidCornerRadii.all(radiusPx.coerceAtMost(lifted.minDimension / 2f))),
      progress = if (swells) ({ progress().coerceAtLeast(0f) }) else lift,
      overshootInflationX = if (swells) FluidMotion.LiftedSwellX else 0f,
      overshootInflationY = if (swells) FluidMotion.LiftedSwellY else 0f,
    )
  }
  val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
  Box(
    modifier = Modifier
      .fillMaxSize()
      .drawWithContent { if (presence() > 0.004f) drawContent() }
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
            intensity = presence,
          )
        } else {
          Modifier.drawBehind {
            drawPath(physics.ensurePlan().silhouette, fallback.copy(alpha = fallback.alpha * presence().coerceIn(0f, 1f)))
          }
        },
      ),
  )
  val shape = ContinuousCornerShape(FluidPopoverRadius)
  Box(
    modifier = Modifier
      .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
      .size(
        width = with(density) { bounds.width.toDp() },
        height = with(density) { bounds.height.toDp() },
      )
      .graphicsLayer {
        // L'immagine, non il vetro: scalarla e' lecito. Dal bordo basso, contro cui si apre il menu; il
        // gonfiore dell'arrivo con una scala sola (il minore dei due assi), mai anamorfica.
        val s = lerp(1f, FluidLiftedPreviewScale, lift()) * (1f + swell() * FluidMotion.LiftedSwellX)
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(0.5f, 1f)
        clip = true
        this.shape = shape
      }
      .drawWithContent {
        // Replaying a released layer is a crash, and the list underneath can dispose a row while
        // the menu is up. Nothing here is worth a crash: if the recording is gone, the pane simply
        // comes up empty and the menu keeps its actions.
        runCatching { drawLayer(preview) }
      },
  )
}

/** How much the lifted row grows. Any more and it stops being the row and becomes a card. */
private const val FluidLiftedPreviewScale = 1.03f
