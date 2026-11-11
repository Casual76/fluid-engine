package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import dev.antigravity.fluidengine.ui.page.FluidBarPiece
import dev.antigravity.fluidengine.ui.page.LocalFluidBarPieceSink
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.ui.glass.field.GlassTouchLight
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent

internal val FluidGlassIconTouchTargetSize: Dp = 48.dp
internal val FluidGlassIconLensSize: Dp = 44.dp

/** Height of a glass action button, matching [FluidButtonDefaults] at `Medium`. */
internal val FluidGlassButtonHeight: Dp = 48.dp

/**
 * La taglia di un tasto di vetro ([FluidGlassIconButton], [FluidGlassButton]).
 *
 * Le app che avevano bisogno di un tasto tondo piu' piccolo (la riga di un composer, accanto a un
 * campo) o piu' grande (la barra di una schermata) se lo sono riscritto a mano, perche' l'engine
 * aveva una misura sola, 44 dp, e la riscrittura riscriveva anche la pressione, il vetro e l'aptica,
 * cioe' le tre cose che il tasto esiste per non far riscrivere. Una taglia e' un numero, non un
 * componente nuovo: la ricetta del tasto (cresce, segue il dito, vibra) e' la stessa e **in
 * proporzione**. La crescita e' una frazione ([FluidPressFeedback.ExpandRound] fa 1,5 su un tasto
 * tondo, [FluidPressFeedback.Expand] 1,1 con un tetto sul lato lungo), la deriva elastica e'
 * misurata sul lato corto del vetro, il bagliore sul lato lungo: nessun numero in dp da riscalare.
 * L'unico numero in dp e' il tetto della crescita di un tasto tondo — 24 dp in tutto, 12 per lato
 * (3.0, [FluidPressFeedback.ExpandRound]) — che sotto i 48 dp non si vede: fino a li' x1,5 intero.
 *
 * **Il bersaglio del tocco non scende mai sotto i 48 dp**, qualunque sia la taglia: un vetro da
 * 40 dp sta dentro un riquadro da 48 che e' quello che occupa nel layout, e il tocco lo vede. Il
 * vetro e' la parte che si disegna, il riquadro e' la parte che si tocca.
 *
 * @property lens il lato del vetro di un tasto tondo solo icona.
 * @property buttonHeight l'altezza del vetro di un tasto con un testo. A [Regular] sono 48 dp e non
 *   44: e' l'altezza che [FluidGlassButton] ha sempre avuto, e una riga con un'etichetta da 17 sp
 *   ha bisogno di piu' aria di un'icona.
 */
enum class FluidGlassSize(val lens: Dp, val buttonHeight: Dp) {
  /** 40 dp: accanto a un campo, dentro un composer, dove 44 sono gia' troppo. */
  Compact(40.dp, 40.dp),

  /** 44 dp (48 per un tasto con testo): la misura di sempre. */
  Regular(FluidGlassIconLensSize, FluidGlassButtonHeight),

  /** 54 dp: la barra flottante di una schermata, dove il tasto sta accanto a una pillola alta quanto lui. */
  Large(54.dp, 54.dp),
  ;

  /** Il riquadro che il tasto tondo occupa nel layout e che il dito colpisce: mai meno di 48 dp. */
  val touchTarget: Dp get() = lens.coerceAtLeast(FluidGlassIconTouchTargetSize)

  /** Quanto sta bene un'icona dentro il vetro: la meta' del lato (20, 22, 27 dp). */
  val iconSize: Dp get() = lens / 2
}

/** Il margine laterale dell'etichetta di un [FluidGlassButton]: cresce con l'altezza. */
internal val FluidGlassSize.buttonPadding: Dp
  get() = when (this) {
    FluidGlassSize.Compact -> 16.dp
    FluidGlassSize.Regular -> 20.dp
    FluidGlassSize.Large -> 24.dp
  }

/** Il lato dell'icona davanti all'etichetta di un [FluidGlassButton]. */
internal val FluidGlassSize.buttonLeading: Dp
  get() = when (this) {
    FluidGlassSize.Compact -> 18.dp
    FluidGlassSize.Regular -> 20.dp
    FluidGlassSize.Large -> 22.dp
  }

/**
 * Porta il riquadro di un controllo ad almeno [min] per lato senza ingrandire quello che c'e'
 * dentro: il contenuto resta della sua misura e si posa al centro, il layout ne occupa di piu'.
 *
 * Lo stesso gesto di `minimumInteractiveComponentSize` di Material, ma con la costante dell'engine
 * e senza leggere `LocalMinimumInteractiveComponentSize`: un'app che spegne quella per i suoi
 * componenti Material non toglie il bersaglio a un tasto che dice di averne uno da 48 dp.
 */
internal fun Modifier.fluidTouchTarget(min: Dp = FluidGlassIconTouchTargetSize): Modifier =
  layout { measurable, constraints ->
    val minPx = min.roundToPx()
    val placeable = measurable.measure(constraints)
    val width = constraints.constrainWidth(maxOf(placeable.width, minPx))
    val height = constraints.constrainHeight(maxOf(placeable.height, minPx))
    layout(width, height) {
      placeable.place((width - placeable.width) / 2, (height - placeable.height) / 2)
    }
  }

/**
 * A pane of glass that behaves like a control.
 *
 * The difference from [glassSurface] is entirely in what it does with a finger, and that is the
 * whole point: the previous implementation of this modifier painted three gradients and called the
 * result a lens, so a control looked like glass in a screenshot and like a button in the hand.
 *
 * What is here instead:
 *
 *  * **It refracts.** Real displacement over a real recording, with dispersion, so the content
 *    behind the control bends and splits at its edge as the control passes over it.
 *  * **It leans.** The whole pane translates toward the finger through a `tanh`, so it moves
 *    freely at first and then refuses — the shape of pushing on something soft that is attached.
 *  * **It swells and stretches.** Held, it grows — since 3.0 as LibrePods' buttons do, by 10% (a
 *    button with a label) or 50% (a round icon button), see [fluidPressTransform]; pushed off-axis,
 *    it stretches along the direction of the push. The lens deepens to LibrePods' 12/24 dp under
 *    the finger, see [GlassDefaults.buttonOptics].
 *  * **It lights up where you touched it.** An additive hotspot follows the finger, because a
 *    specular reflection adds light. Darkening on press is the plastic-button idiom. Since 3.0 it is
 *    LibrePods' glow, see [glassButtonGlowRecipe].
 *
 * All of it is one [FluidPressState], the same one [fluidPressable] drives, and the swell, the
 * lean, the light and the optics all read the same number. With reduced motion the swell is
 * halved and the lean dropped, but the press still happens, start to end.
 *
 * The lean and the swell are applied through the renderer's own layer block, which means the
 * refraction is computed *after* the transform and corrected for it: the control slides across the
 * backdrop and keeps showing what is genuinely behind it, instead of dragging a frozen picture
 * along.
 *
 * @param backdrop what the control refracts. Give it the surface the control sits *on* — see
 *   [rememberCombinedGlassBackdrop] — not the page underneath that surface.
 * @param interactive false for a control that is only decorative or disabled; it keeps the material
 *   and drops every gesture, rather than needing a second, quieter component to exist.
 * @param pressState la pressione del controllo a cui questo vetro appartiene, guidata dal suo
 *   [fluidPressable] (`pressState = ` la stessa). Il vetro risponde allora dal contatto, come il
 *   pulsante, e segue il dito finche' e' giu' — anche fuori dal vetro, e il dito e' del pulsante
 *   fino al sollevamento: vedi [fluidButtonGesture]. Null: il vetro segue il dito da solo, dal
 *   momento in cui scende, e non consuma niente. Dalla 2.11.0.
 * @param optics di serie [GlassDefaults.buttonOptics], dalla 3.0: la lente di sempre a riposo, e
 *   quella di LibrePods sotto il dito.
 */
@Composable
fun Modifier.glassControlSurface(
  backdrop: GlassBackdropState,
  shape: Shape = FluidCapsuleShape,
  tint: GlassTint = GlassDefaults.controlTint(),
  optics: GlassOptics = GlassDefaults.buttonOptics(),
  selected: Boolean = false,
  interactive: Boolean = true,
  /** Independent optical weight, so a control can rest almost invisible and thicken on demand. */
  depth: () -> Float = { 1f },
  /** See [glassSurface]. Since 2.10.0. */
  frozen: () -> Boolean = { false },
  /** See [glassSurface]. Since 2.10.0. */
  heldWhileMoving: () -> Boolean = { false },
  pressState: FluidPressState? = null,
): Modifier {
  val accent = MaterialTheme.colorScheme.primary
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val onDark = GlassDefaults.isDarkSurface()
  // Dentro la barra ancorata delle pagine (3.0) il tasto dice dove sta la sua lente e, mentre le
  // pagine cambiano, spegne il proprio vetro: lo porta una superficie sola di Fluid-physics, che fa
  // diventare i tasti della pagina che esce quelli della pagina che arriva. Fuori, niente.
  val pieceSink = LocalFluidBarPieceSink.current
  val barPiece = remember(pieceSink, shape) {
    if (pieceSink != null) FluidBarPiece(capsule = shape == FluidCapsuleShape) else null
  }
  DisposableEffect(pieceSink, barPiece) {
    if (pieceSink != null && barPiece != null) pieceSink.add(barPiece)
    onDispose { if (pieceSink != null && barPiece != null) pieceSink.remove(barPiece) }
  }
  // A intensita' zero glassSurface salta anche drawContent: sparivano le icone insieme al vetro.
  // Quando il morph prende il materiale togliamo solo il modificatore, lasciando il contenuto
  // alla dissolvenza della barra. Cambia solo alla staffetta, mai a ogni fotogramma del viaggio.
  val materialSuppressed = pieceSink?.suppressed() == true
  val ownPress = rememberFluidPressState(FluidPressFeedback.Expand)
  val press = pressState ?: ownPress
  val highlight = remember(scope, reducedMotion, onDark, press) {
    GlassTouchHighlight(
      animationScope = scope,
      strength = { if (reducedMotion) 0.4f else 1f },
      onDarkSurface = onDark,
      // Questo e' vetro, non una riga: sul chiaro il bagliore aggiunge luce invece di stendere
      // nero. Col nero il pulsante premuto diventava un riempimento grigio-verde (~#A8B3AE) con un
      // gradiente duro verso il dito, e non rifrangeva piu' niente. Vedi glowRecipe.
      onGlass = true,
      // Dalla 3.0 il bagliore di un pulsante e' quello di LibrePods, grande e luminoso, su chiaro e
      // su scuro: vedi glassButtonGlowRecipe, che dice anche cosa si rischia a luce chiara.
      button = true,
      progress = { press.progress },
      // Dove e' sceso il dito piu' la strada che ha fatto: al rilascio torna al punto di discesa
      // insieme al tasto, con la stessa molla.
      touch = { press.glow },
    )
  }
  val systemContrast = LocalFluidSystemContrast.current
  val selectionTint = remember(tint, selected, accent, systemContrast) {
    if (!selected) {
      tint
    } else {
      // Selection is carried by the accent joining the material, not by an opaque fill replacing it.
      //
      // At a fifth of an alpha this was calibrated for a control standing on a
      // page, where the material has a quiet ground to tint. Over a photograph
      // the ground is already loud — and when the accent is drawn *from* that
      // photograph, which is what an app that takes its colour from the artwork
      // does, the tinted one and the plain one came out the same colour. A
      // third survives that, and it is still the accent joining the material
      // rather than a fill replacing it: the picture goes on showing through.
      tint.copy(
        overlay = accent.copy(alpha = 0.36f),
        fallback = accent.copy(alpha = 0.9f),
        hairline = accent.copy(alpha = 0.45f),
        // La selezione e' una pellicola d'accento, com'e' sempre stata: la tinta di serie e' `Neutral`
        // (il velo lo decide il materiale nuovo), e copiata cosi' com'e' l'accento non si vedrebbe.
        mode = GlassTintMode.Fixed,
      ).forContrast(systemContrast)
    }
  }

  // Il materiale con cui questo vetro si disegna (4.0.0). Lo chiede anche `glassSurface`, con gli stessi
  // ingressi, quindi le due risposte coincidono; qui serve saperlo prima, perche' col materiale nuovo il
  // bagliore del dito non e' piu' un disegno a parte sopra il vetro: passa dalla sua luce.
  val resolvedOptics = remember(optics) { optics.sanitized() }
  val liquid = rememberFluidGlassModel(shape, GlassFalloff.Uniform, resolvedOptics) == FluidGlassModel.Liquid
  val touchLight = remember(press, reducedMotion) {
    GlassTouchLight(
      // Gli stessi due numeri che riceve il bagliore di prima: la pressione, e il peso che le animazioni
      // ridotte le lasciano.
      amount = { press.progress.fastCoerceIn(0f, 1f) * (if (reducedMotion) 0.4f else 1f) },
      position = { press.glow },
    )
  }
  val pressLayer: (GraphicsLayerScope.() -> Unit)? = if (interactive) {
    {
      // The same transform a solid button draws, here in the layer the renderer corrects for:
      // grow, lean toward the finger through a tanh, stretch along its drag. See
      // fluidPressTransform for the numbers and the reasons.
      val transform = fluidPressTransform(
        feedback = press.feedback,
        progress = press.progress,
        lean = press.lean,
        width = size.width,
        height = size.height,
        dp = density,
        reducedMotion = reducedMotion,
      )
      scaleX = transform.scaleX
      scaleY = transform.scaleY
      translationX = transform.translationX
      translationY = transform.translationY
    }
  } else {
    null
  }

  return this
    .then(
      if (pieceSink != null && barPiece != null) {
        Modifier.onGloballyPositioned { coordinates ->
          barPiece.coordinates = coordinates
          pieceSink.onChanged()
        }
      } else {
        Modifier
      },
    )
    .then(
      if (materialSuppressed) {
        Modifier
      } else if (liquid) {
        // Gli stessi ingressi che `glassSurface` riceve qui sotto, piu' il bagliore del dito: di una
        // superficie non interattiva non c'e', come prima.
        Modifier.glassLiquidSurface(
          state = backdrop,
          tint = selectionTint,
          shape = shape,
          edge = GlassEdge.None,
          intensity = { 1f },
          role = GlassRole.Interactive,
          optics = resolvedOptics,
          opticalDepth = depth,
          pressed = { press.progress.fastCoerceIn(0f, 1f) },
          exports = null,
          layerBlock = pressLayer,
          sampleOnce = false,
          resampleIntervalMillis = 0L,
          frozen = frozen,
          heldWhileMoving = heldWhileMoving,
          nestInContainer = true,
          touch = if (interactive) touchLight else null,
        )
      } else {
        Modifier.glassSurface(
          state = backdrop,
          tint = selectionTint,
          shape = shape,
          role = GlassRole.Interactive,
          optics = optics,
          opticalDepth = depth,
          pressed = { press.progress.fastCoerceIn(0f, 1f) },
          frozen = frozen,
          heldWhileMoving = heldWhileMoving,
          layerBlock = pressLayer,
        )
      },
    )
    // Il bagliore di prima, un disegno sopra il vetro: col materiale nuovo e' nella sua luce.
    .then(if (interactive && !liquid) highlight.modifier else Modifier)
    // Segue il dito da solo solo quando nessuno lo guida: una pressione condivisa la muove il
    // fluidPressable del controllo, e due inseguitori su una pressione scatterebbero due volte a
    // ogni passo del dito.
    .then(if (interactive && pressState == null) Modifier.fluidPressTracking(ownPress, ownsPress = true) else Modifier)
}

/**
 * Glass without a backdrop to refract.
 *
 * For the one place the live material genuinely cannot go: a sheet or a dialog, which the platform
 * puts in its own window and which therefore cannot read another window's `GraphicsLayer`. Painted
 * gradients are an honest fallback there, and a bad idea anywhere a real backdrop exists — the whole
 * point of the rest of this file is that painted highlights do not survive contact with a finger.
 */
@Composable
fun Modifier.fluidStaticGlassSurface(
  shape: Shape = FluidCapsuleShape,
  selected: Boolean = false,
  pressure: () -> Float = { 0f },
): Modifier {
  val scheme = MaterialTheme.colorScheme
  // Chiaro o scuro lo decide una regola sola, `GlassDefaults.isDarkSurface()`: la luminanza di
  // `surface` ignora l'alpha, quindi un'app con una pellicola traslucida in `surface` veniva
  // letta come chiara sempre, e dichiarare da che parte sta (`LocalFluidSurfaceSide`) non serviva.
  val lightSurface = !GlassDefaults.isDarkSurface()
  val body = if (selected) scheme.primary else scheme.surface
  val topLight = Color.White.copy(alpha = if (lightSurface) 0.86f else 0.58f)
  val quietLight = Color.White.copy(alpha = if (lightSurface) 0.24f else 0.16f)
  val lowerShade = Color.Black.copy(alpha = if (lightSurface) 0.30f else 0.52f)
  val innerShade = scheme.onSurface.copy(alpha = if (lightSurface) 0.22f else 0.32f)

  return this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val insetPx = 2.dp.toPx()
    val innerSize = Size(
      width = (size.width - insetPx * 2f).coerceAtLeast(0f),
      height = (size.height - insetPx * 2f).coerceAtLeast(0f),
    )
    val innerOutline = if (innerSize.width > 0f && innerSize.height > 0f) {
      shape.createOutline(innerSize, layoutDirection, this)
    } else {
      null
    }
    val diagonal = Offset(size.width, size.height)
    val bodyBrush = Brush.linearGradient(
      colorStops = arrayOf(
        0f to body.copy(alpha = if (selected) 0.30f else 0.18f),
        0.46f to body.copy(alpha = if (selected) 0.18f else 0.10f),
        1f to body.copy(alpha = if (selected) 0.24f else 0.15f),
      ),
      start = Offset.Zero,
      end = diagonal,
    )
    val rimBrush = Brush.linearGradient(
      colorStops = arrayOf(
        0f to topLight,
        0.38f to quietLight,
        0.68f to Color.Transparent,
        1f to lowerShade,
      ),
      start = Offset.Zero,
      end = diagonal,
    )
    val innerBrush = Brush.linearGradient(
      colorStops = arrayOf(
        0f to quietLight,
        0.50f to Color.Transparent,
        1f to innerShade,
      ),
      start = Offset.Zero,
      end = diagonal,
    )
    val baseRimWidth = 1.35.dp.toPx()
    val innerRimWidth = 0.95.dp.toPx()

    onDrawBehind {
      val pressed = pressure().coerceIn(0f, 1f)
      drawOutline(outline = outline, brush = bodyBrush, alpha = 1f)
      drawOutline(
        outline = outline,
        brush = rimBrush,
        alpha = 1f - pressed * 0.18f,
        style = Stroke(width = baseRimWidth * (1f - pressed * 0.24f)),
      )
      innerOutline?.let {
        translate(insetPx, insetPx) {
          drawOutline(
            outline = it,
            brush = innerBrush,
            alpha = 1f - pressed * 0.12f,
            style = Stroke(width = innerRimWidth),
          )
        }
      }
      if (pressed > 0.001f) {
        drawOutline(outline = outline, color = scheme.onSurface.copy(alpha = 0.055f * pressed))
      }
    }
  }
}

/**
 * A glass lens for icon actions that live in chrome or another overlay.
 *
 * 44 dp of material inside a 48 dp hit target: the lens is allowed to be the size Apple draws it
 * while the tappable area stays the size accessibility requires.
 *
 * @param size la taglia ([FluidGlassSize]): 40, 44 (di serie) o 54 dp di vetro. Il riquadro che il
 *   tasto occupa e che il dito colpisce e' sempre almeno 48 dp ([FluidGlassSize.touchTarget]), e la
 *   pressione e' la stessa di sempre in proporzione: vedi [FluidGlassSize]. Per una misura che non e'
 *   fra queste c'e' l'overload con `size: Dp`. Dalla 3.0.
 * @param haptic cosa si sente al click, come in [fluidPressable]: di serie il click di LibrePods
 *   (con `Tap` un tasto che cresce suona `PressClick`); un evento diverso lo sostituisce; `null`
 *   spegne tutto — contatto, rilascio, grana e click — per un tasto la cui azione ha gia' il suo
 *   colpo (un microfono che apre l'ascolto, un menu). Dalla 3.0.
 * @param grip di chi e' il dito quando la pagina sotto vuole scorrere: del tasto
 *   ([FluidPressGrip.HoldsFinger], di serie) o della pagina ([FluidPressGrip.YieldsToScroll], per un
 *   tasto ripetuto su ogni riga di una lista che scorre). Vedi [fluidPressable]. Dalla 3.0.
 */
@Composable
fun FluidGlassIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  enabled: Boolean = true,
  selected: Boolean = false,
  shape: Shape = FluidCapsuleShape,
  onLongClick: (() -> Unit)? = null,
  size: FluidGlassSize = FluidGlassSize.Regular,
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
  /**
   * Cosa si sente quando scatta il click lungo; null per un click lungo che ha il suo colpo (un menu
   * che suona `Open` solo se si apre). Dalla 3.0.
   */
  longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  content: @Composable BoxScope.() -> Unit,
) {
  // One press for the click and the glass: the lens swells, leans and lights up on the same
  // timeline the action is confirmed on, and a finger sliding over it ticks once, not twice.
  // Tondo, solo icona: cresce del 50%, come `StyledIconButton` di LibrePods (3.0).
  val press = rememberFluidPressState(FluidPressFeedback.ExpandRound)
  FluidGlassIconButtonCore(
    onClick = onClick,
    modifier = modifier,
    backdrop = backdrop,
    enabled = enabled,
    selected = selected,
    shape = shape,
    onLongClick = onLongClick,
    size = size,
    haptic = haptic,
    longClickHaptic = longClickHaptic,
    press = press,
    grip = grip,
    content = content,
  )
}

/**
 * Un tasto tondo di vetro di una misura qualunque (3.0): i 62 e i 76 dp dei dischi del trasporto di un
 * player, i 52 di un hero, i 54 di un pulsante di menu, che le tre taglie di [FluidGlassSize] non
 * dicono.
 *
 * E' lo stesso tasto, con le stesse regole, e [size] e' il lato del **vetro**:
 *
 *  * **il bersaglio del tocco non scende mai sotto i 48 dp**: un vetro da 32 o da 40 sta dentro un
 *    riquadro da 48 che e' quello che occupa nel layout e che il dito colpisce; da 48 in su il vetro
 *    e' il proprio bersaglio, senza riquadro in piu' ([FluidGlassSize.touchTarget] e' la stessa regola);
 *  * **la crescita sotto il dito ha un tetto**: x1,5 fino a 48 dp, poi 24 dp in tutto e non di piu'
 *    ([FluidPressFeedback.ExpandRound]). Un disco da 76 dp arriva a 100, non a 114: con 16 dp fra due
 *    dischi non si toccano;
 *  * un'icona sta bene alla meta' del lato (`size / 2`: 20, 22, 27 dp per le tre taglie).
 *
 * Le due strade si scelgono dal tipo: `size = FluidGlassSize.Large` o `size = 76.dp`. Senza `size`
 * vale l'overload con la taglia, `Regular`: questo vuole la misura.
 *
 * @param size il lato del vetro, positivo.
 * @see FluidGlassIconButton la versione con le tre taglie, e gli altri parametri.
 */
@Composable
fun FluidGlassIconButton(
  onClick: () -> Unit,
  size: Dp,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  enabled: Boolean = true,
  selected: Boolean = false,
  shape: Shape = FluidCapsuleShape,
  onLongClick: (() -> Unit)? = null,
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
  longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  content: @Composable BoxScope.() -> Unit,
) {
  val press = rememberFluidPressState(FluidPressFeedback.ExpandRound)
  FluidGlassIconButtonCore(
    onClick = onClick,
    modifier = modifier,
    backdrop = backdrop,
    enabled = enabled,
    selected = selected,
    shape = shape,
    onLongClick = onLongClick,
    // La taglia e' solo il punto di partenza di `lens`: la misura vera e' quella che passa il chiamante.
    size = FluidGlassSize.Regular,
    haptic = haptic,
    longClickHaptic = longClickHaptic,
    press = press,
    grip = grip,
    lens = size,
    content = content,
  )
}

/**
 * Il tasto tondo di vetro, con i ganci che servono a un'ancora di menu (3.0, fase 7): la pressione
 * dal chiamante (per leggere il tasto gonfiato al click lungo), un modificatore sulla LENTE (il
 * cancello `hidden`, che deve nascondere il vetro e non il riquadro da 48 dp) e la posizione della
 * lente (il menu parte dalla sagoma del vetro, non dal bersaglio del tocco). Il pubblico
 * [FluidGlassIconButton] ci delega senza ganci.
 */
@Composable
internal fun FluidGlassIconButtonCore(
  onClick: () -> Unit,
  modifier: Modifier,
  backdrop: GlassBackdropState,
  enabled: Boolean,
  selected: Boolean,
  shape: Shape,
  onLongClick: (() -> Unit)?,
  size: FluidGlassSize,
  haptic: FluidHapticEvent?,
  longClickHaptic: FluidHapticEvent?,
  press: FluidPressState,
  lensModifier: Modifier = Modifier,
  onLensPlaced: ((LayoutCoordinates) -> Unit)? = null,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  /** Il lato del vetro; di serie quello della taglia. Una misura libera lo cambia, il riquadro con lui. */
  lens: Dp = size.lens,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.38f)
      .size(lens.coerceAtLeast(FluidGlassIconTouchTargetSize)),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(lens)
        .then(if (onLensPlaced != null) Modifier.onGloballyPositioned(onLensPlaced) else Modifier)
        .then(lensModifier)
        .glassControlSurface(
          backdrop = backdrop,
          shape = shape,
          selected = selected,
          interactive = enabled,
          pressState = press,
        )
        // The click sits inside the material, so the glass reacts to the same press the action does.
        .fluidPressable(
          onClick = onClick,
          onLongClick = onLongClick,
          enabled = enabled,
          role = Role.Button,
          haptic = haptic,
          focusShape = shape,
          pressState = press,
          longClickHaptic = longClickHaptic,
          grip = grip,
        ),
      contentAlignment = Alignment.Center,
      content = content,
    )
  }
}

/**
 * A glass action button.
 *
 * The counterpart of [FluidButton] for anything floating over content: same proportions and same
 * typography, but the surface transmits instead of covering. Use it where a solid accent button
 * would sit on a photograph, a map or a hero image — and keep [FluidButton] for the one solid
 * primary action a form or a settings page is about, which should still look pressed into the page.
 *
 * @param backdrop cio' che il vetro rifrange. Di serie (3.0.1) il canvas della pagina se c'e', come i chip,
 *   il segmentato e lo slider, altrimenti la chrome in scope: prima era sempre la chrome, e su una pagina
 *   con un canvas ambientale il tasto rifrangeva il corpo della pagina (con dentro se stesso) invece della
 *   lavata. In una barra il canvas non c'e' (la barra lo toglie), e resta il vetro della barra.
 * @param size la taglia ([FluidGlassSize]): 40, 48 (di serie, l'altezza di sempre) o 54 dp. Sotto i
 *   48 dp il layout occupa comunque 48 dp di altezza ([fluidTouchTarget]) e il vetro sta al centro:
 *   il tasto e' piu' basso da vedere, non da toccare. Dalla 3.0.
 * @param haptic come per [FluidGlassIconButton]: `null` per un tasto la cui azione ha gia' il suo colpo.
 * @param grip come per [FluidGlassIconButton]: [FluidPressGrip.YieldsToScroll] per un tasto in una
 *   riga di una lista che scorre, dove non c'e' un fuori da cui partire. Dalla 3.0.
 * @param maxLines quante righe puo' avere l'etichetta (3.0.4). `1` la tronca con i puntini in uno spazio
 *   stretto, invece di farla andare a capo dentro un tasto di altezza fissa. Di serie senza tetto, come
 *   sempre; un'etichetta che va a capo fa crescere il tasto oltre l'altezza della taglia invece di tagliarla.
 */
@Composable
fun FluidGlassButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
  enabled: Boolean = true,
  selected: Boolean = false,
  shape: Shape = FluidCapsuleShape,
  size: FluidGlassSize = FluidGlassSize.Regular,
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  leading: (@Composable () -> Unit)? = null,
  maxLines: Int = Int.MAX_VALUE,
) {
  val contentColor = if (selected) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  val press = rememberFluidPressState(FluidPressFeedback.Expand)
  // Il riquadro da 48 dp lo porta fuori dal vetro solo chi e' piu' basso: a 48 e 54 dp l'albero e'
  // quello di prima, nodo per nodo.
  val touchTarget = if (size.buttonHeight < FluidGlassIconTouchTargetSize) Modifier.fluidTouchTarget() else Modifier
  val labelStyle = if (size == FluidGlassSize.Compact) {
    MaterialTheme.typography.bodyMedium
  } else {
    MaterialTheme.typography.bodyLarge
  }
  Row(
    modifier = modifier
      .then(touchTarget)
      .alpha(if (enabled) 1f else 0.38f)
      .glassControlSurface(
        backdrop = backdrop,
        shape = shape,
        selected = selected,
        interactive = enabled,
        pressState = press,
      )
      .fluidPressable(
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        haptic = haptic,
        focusShape = shape,
        pressState = press,
        grip = grip,
      )
      .heightIn(min = size.buttonHeight)
      .padding(horizontal = size.buttonPadding),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
      if (leading != null) {
        Box(modifier = Modifier.size(size.buttonLeading), contentAlignment = Alignment.Center) { leading() }
      }
      Text(
        text = text,
        style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
