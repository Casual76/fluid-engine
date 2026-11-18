package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim

// Lo scrim dei modali. Un file a sé: i livelli dello scrim si scelgono qui, senza toccare i pannelli
// (spezzato da `FluidGlassModal.kt` in F5A.0).

/**
 * The scrim.
 *
 * Almost nothing, and that is the decision. The temptation with a full-screen pane is to turn the
 * radius up until the page behind is unrecognisable — which does separate the pop-up, at the price
 * of throwing away the only reason to keep the modal inside the app's composition at all. A page
 * averaged into grey is not "visible behind": it is grey, and a painted rectangle would have got
 * there cheaper. So the frosting stays at a fraction of a bar's, the page keeps its colour, and the
 * separation is bought with a light drop in brightness plus the pop-up's own edge and shadow.
 *
 * Displacement is zero on purpose: a lens the size of the display bends the corners of the screen
 * and reads as a fisheye rather than as depth.
 */
@Composable
internal fun FluidGlassModalScrim(
  backdrop: GlassBackdropState?,
  exports: GlassBackdropState,
  intensity: () -> Float,
  interactive: Boolean,
  onDismiss: () -> Unit,
) {
  val dark = GlassDefaults.isDarkSurface()
  val tint = remember(dark) {
    GlassTint(
      // Lo scrim dei modali e quello dei pannelli dipinti sono uno (F1B.5): i livelli stanno in `GlassPaintedScrim`.
      overlay = GlassPaintedScrim.color(dark),
      // Below API 31 there is no blur at all, so the scrim stops pretending and becomes a wall. The
      // modal works identically; you simply cannot see through it.
      fallback = Color.Black.copy(alpha = 0.92f),
      hairline = Color.Transparent,
    )
  }
  val optics = remember {
    GlassDefaults.optics(GlassRole.Modal).copy(
      // Two and a half dp times one and a half. Enough that the page reads as *behind* something
      // rather than as merely darker, and far too little to average it away.
      blurScale = 1.5f,
      // Lo scrim e' grande quanto lo schermo, ma non e' un pannello grande: la regola della taglia
      // (vedi [GlassOptics.sizeResponse]) lo porterebbe a raddoppiare la sfocatura, e un velo che
      // deve restare appena sfocato diventerebbe la media della pagina.
      sizeResponse = 0f,
      refractionHeight = 0.dp,
      refractionAmount = 0.dp,
      vibrancy = 1f,
      highlightAlpha = 0f,
      innerShadowAlpha = 0f,
      shadowAlpha = 0f,
    )
  }

  val base = Modifier
    .fillMaxSize()
    // The arrival is an alpha on the finished pane, **not** the material's own `intensity`.
    //
    // `intensity` scales the blur radius, and a changing blur radius changes the padding the layer
    // needs, and a changing padding is the one thing that genuinely invalidates a capture. Ramping
    // it therefore re-records the whole screen and re-runs the entire effect chain on every frame of
    // the fade — on the largest surface in the system, at the exact moment something is trying to
    // animate. Fading the result instead costs one composited layer and looks identical.
    .graphicsLayer { alpha = intensity().coerceIn(0f, 1f) }
    // Il cancello del fotogramma di confine: al primo e all'ultimo fotogramma di vita del nodo
    // il layer qui sopra puo' disegnare con l'alpha di default (1) — e' il lampo scuro+blur di
    // un fotogramma misurato nel video dell'apertura E della chiusura. Con l'alpha a zero non
    // si registra niente, e una displaylist vuota non ha lampi da rigiocare.
    .drawWithContent { if (intensity() > 0.004f) drawContent() }
    .then(
      if (interactive) {
        Modifier.pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
      } else {
        Modifier
      },
    )

  if (backdrop == null) {
    Box(
      modifier = base.drawWithContent {
        drawRect(tint.fallback)
        drawContent()
      },
    )
  } else {
    Box(
      modifier = base.glassSurface(
        state = backdrop,
        tint = tint,
        role = GlassRole.Modal,
        optics = optics,
        exports = exports,
      ),
    )
  }
}
