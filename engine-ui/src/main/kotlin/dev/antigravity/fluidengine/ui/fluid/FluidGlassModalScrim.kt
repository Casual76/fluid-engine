package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
 * Quanto uno scrim scurisce e smeriglia la pagina: un nero sopra la pagina sfocata, e il moltiplicatore della
 * sfocatura di base dei modali (`DefaultGlassBlurRadius`, 2,5 dp).
 *
 * Un tipo e non due numeri sparsi perche' lo stesso nero va in due posti che devono restare uguali: lo scrim
 * e la pellicola della finestra del menu, che campiona la pagina **cruda** e lo scurimento se lo dipinge da
 * se' (`FluidMenuLayer`). Un livello cambiato in un posto solo fa un menu piu' chiaro o piu' scuro della pagina
 * attorno, cioe' un rettangolo che si vede.
 */
@Immutable
internal data class FluidScrimLook(
  val lightAlpha: Float,
  val darkAlpha: Float,
  val blurScale: Float,
) {
  fun alpha(darkSide: Boolean): Float = if (darkSide) darkAlpha else lightAlpha

  fun color(darkSide: Boolean): Color = Color.Black.copy(alpha = alpha(darkSide))

  /** La sfocatura in dp, sulla base dei modali. */
  val blurDp: Float get() = FluidScrimBaseBlurDp * blurScale

  companion object {
    /**
     * Lo scrim dei menu dal tasto, dei fogli, degli avvisi e dei popover: nero al 12% sul chiaro e al 20% sullo
     * scuro (sono i livelli dei pannelli dipinti, `GlassPaintedScrim`, F1B.5), sfocatura 3,75 dp.
     */
    val Modal = FluidScrimLook(
      lightAlpha = GlassPaintedScrim.LightAlpha,
      darkAlpha = GlassPaintedScrim.DarkAlpha,
      blurScale = 1.5f,
    )
  }
}

/**
 * I tre livelli dello sfondo del menu contestuale fra cui Alessio sceglie sulle tavole (F5A.4, piano: «lo scrim
 * del contestuale prende un livello suo, piu' scuro di quello dei menu, da scegliere fra tre tavole»).
 *
 * La decisione di fondo e' sua (2026-11-10): la pagina dietro il contestuale si **scurisce con una sfocatura
 * leggera**, «anche se non e' esattamente pari ad Apple» (iOS 26 scurisce soltanto, R1 X2). Quanto, e' di gusto:
 * Apple non lo documenta e nel materiale non c'e' un contestuale da misurare. I tre livelli sono scelti a occhio
 * il 2026-11-18, tutti piu' scuri dello scrim dei menu ([FluidScrimLook.Modal]) e tutti sotto il tetto della
 * sfocatura ([FluidScrimBlurCeilingDp]):
 *
 *  - [A], il minimo che si distingue dai menu: la pagina resta quasi com'e', un passo piu' scura;
 *  - [B], la proposta: la pagina passa chiaramente *dietro*, il testo si riconosce ma non si legge di corsa;
 *  - [C], il massimo prima che la pagina diventi una media: lo sfondo e' una macchia scura con le sue forme.
 */
internal enum class FluidContextScrimLevel(val look: FluidScrimLook) {
  A(FluidScrimLook(lightAlpha = 0.18f, darkAlpha = 0.30f, blurScale = 1.5f)),
  B(FluidScrimLook(lightAlpha = 0.24f, darkAlpha = 0.38f, blurScale = 2.0f)),
  C(FluidScrimLook(lightAlpha = 0.32f, darkAlpha = 0.48f, blurScale = 2.6f)),
}

/**
 * Il livello dello sfondo del contestuale in uso. La proposta in attesa della scelta di Alessio sulle tavole di
 * F5A.4 (`sessioni/f5a-4/`): [FluidContextScrimLevel.B].
 */
internal val FluidContextScrimLook: FluidScrimLook = FluidContextScrimLevel.B.look

/** La sfocatura di base dei modali, in dp (`DefaultGlassBlurRadius`): il riferimento di [FluidScrimLook.blurScale]. */
internal const val FluidScrimBaseBlurDp = 2.5f

/**
 * Il tetto della sfocatura di uno scrim, in dp: oltre ~8 dp un pannello grande quanto lo schermo trasmette la
 * media della pagina, non la pagina (invariante 20 di R2: «base 2,5 dp; oltre ~8 dp un pannello trasmette la
 * media»). Un margine sotto, perche' lo scrim deve ancora far riconoscere cio' che copre.
 */
internal const val FluidScrimBlurCeilingDp = 7f

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
 * Il menu contestuale passa un [look] suo, piu' scuro ([FluidContextScrimLook]): li' la pagina deve andare
 * *dietro* l'elemento sollevato, ed e' la scelta di Alessio.
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
  look: FluidScrimLook = FluidScrimLook.Modal,
) {
  val dark = GlassDefaults.isDarkSurface()
  val tint = remember(dark, look) {
    GlassTint(
      // Lo scrim dei modali e quello dei pannelli dipinti sono uno (F1B.5): i livelli stanno in `GlassPaintedScrim`.
      overlay = look.color(dark),
      // Below API 31 there is no blur at all, so the scrim stops pretending and becomes a wall. The
      // modal works identically; you simply cannot see through it.
      fallback = Color.Black.copy(alpha = 0.92f),
      hairline = Color.Transparent,
    )
  }
  val optics = remember(look) {
    GlassDefaults.optics(GlassRole.Modal).copy(
      // Two and a half dp times one and a half. Enough that the page reads as *behind* something
      // rather than as merely darker, and far too little to average it away.
      blurScale = look.blurScale,
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
