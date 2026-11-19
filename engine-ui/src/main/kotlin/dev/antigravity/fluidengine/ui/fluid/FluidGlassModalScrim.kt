package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
 * I tre livelli dello sfondo del menu contestuale. C è quello scelto da Alessio sulle catture S10a.
 *
 * La decisione di fondo e' sua (2026-11-10): la pagina dietro il contestuale si **scurisce con una sfocatura
 * leggera**, «anche se non e' esattamente pari ad Apple» (iOS 26 scurisce soltanto, R1 X2). Quanto, e' di gusto:
 * Apple non lo documenta e nel materiale non c'e' un contestuale da misurare. I tre livelli sono scelti a occhio
 * il 2026-11-18, tutti piu' scuri dello scrim dei menu ([FluidScrimLook.Modal]) e tutti sotto il tetto della
 * sfocatura ([FluidScrimBlurCeilingDp]):
 *
 *  - [A], il minimo che si distingue dai menu: la pagina resta quasi com'e', un passo piu' scura;
 *  - [B], l'intermedio: la pagina passa chiaramente *dietro*, il testo si riconosce ma non si legge di corsa;
 *  - [C], il massimo e il livello di serie: lo sfondo e' una macchia scura con le sue forme.
 */
internal enum class FluidContextScrimLevel(val look: FluidScrimLook) {
  A(FluidScrimLook(lightAlpha = 0.18f, darkAlpha = 0.30f, blurScale = 1.5f)),
  B(FluidScrimLook(lightAlpha = 0.24f, darkAlpha = 0.38f, blurScale = 2.0f)),
  C(FluidScrimLook(lightAlpha = 0.32f, darkAlpha = 0.48f, blurScale = 2.6f)),
}

/** Lo sfondo di serie del contestuale. Il menu legge il livello in vigore, [FluidContextScrimTuning.look]. */
internal val FluidContextScrimLook: FluidScrimLook get() = FluidContextScrimTuning.Default.look

/**
 * La manopola dello sfondo del contestuale (`ctxscrim=a|b|c`, correzione dopo S9): Alessio sceglie il livello sulle
 * catture del tablet **a schermo intero**, non sulle tavole ritagliate, e il tablet deve poter mostrare i tre livelli
 * senza ricompilare. Passa dalla porta delle manopole della diagnostica (`FluidGlassDiagnostics.tuneLiquidProfile`,
 * `gallery.tune` nella galleria), quindi solo nelle build di debug e di misura; fuori, il livello e' sempre quello di
 * serie. La manopola rimane disponibile per ripetere le catture di confronto.
 */
internal object FluidContextScrimTuning {

  /** Il nome della manopola, come lo scrive `gallery.tune`. */
  const val Knob: String = "ctxscrim"

  /** I valori che accetta. */
  val Values: List<String> = FluidContextScrimLevel.entries.map { it.name.lowercase() }

  /** Il livello di serie, senza manopola. */
  val Default: FluidContextScrimLevel = FluidContextScrimLevel.C

  /**
   * Il livello in vigore. E' uno stato di Compose: il menu lo legge mentre si compone, quindi un cambio vale dal
   * contestuale successivo, senza far rinascere la pagina (le tre catture si fanno una dopo l'altra sulla stessa).
   */
  var level: FluidContextScrimLevel by mutableStateOf(Default)
    private set

  /** Lo sfondo del livello in vigore: quello che scrim e pellicola della finestra del menu leggono. */
  val look: FluidScrimLook get() = level.look

  /**
   * Il livello chiesto in [knobs] (nome e valore senza distinzione di maiuscole), puro: null se la manopola non c'e' o
   * il valore non si riconosce.
   */
  fun parse(knobs: Map<String, String>?): FluidContextScrimLevel? {
    val raw = knobs?.entries?.firstOrNull { it.key.trim().lowercase() == Knob }?.value ?: return null
    return FluidContextScrimLevel.entries.firstOrNull { it.name.lowercase() == raw.trim().lowercase() }
  }

  /**
   * Prende la manopola da [knobs] e la applica; senza (o con un valore che non si riconosce) torna di serie.
   * Restituisce la manopola presa (nome e valore normalizzato), vuota se non e' stata presa.
   */
  fun apply(knobs: Map<String, String>?): Map<String, String> {
    val taken = parse(knobs)
    level = taken ?: Default
    return if (taken == null) emptyMap() else mapOf(Knob to taken.name.lowercase())
  }
}

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
  menu: Boolean = false,
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
    .graphicsLayer {
      alpha = intensity().coerceIn(0f, 1f)
      if (menu) compositingStrategy = CompositingStrategy.ModulateAlpha
    }
    // Il cancello del fotogramma di confine: al primo e all'ultimo fotogramma di vita del nodo
    // il layer qui sopra puo' disegnare con l'alpha di default (1) — e' il lampo scuro+blur di
    // un fotogramma misurato nel video dell'apertura E della chiusura. Con l'alpha a zero non
    // si registra niente, e una displaylist vuota non ha lampi da rigiocare.
    .then(if (menu) Modifier.menuDrawMarker("scrim") else Modifier)
    .drawWithContent { if (intensity() > 0.004f) drawContent() }
    // Il gate e la dissolvenza stanno fuori dalla texture. Se cambia solo l'alpha,
    // HWUI riusa blur e tinta già composti alla risoluzione del display.
    .then(if (menu && backdrop != null) MenuScrimMaterialLayer else Modifier)
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

/** Conserva i due disegni originali del materiale fra i cambi della sola opacità. */
private val MenuScrimMaterialLayer = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
