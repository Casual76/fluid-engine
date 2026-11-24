package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import dev.antigravity.fluidengine.ui.page.FluidBarPiece
import dev.antigravity.fluidengine.ui.page.LocalFluidBarPieceSink
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedSelectionAlpha
import dev.antigravity.fluidengine.ui.glass.interaction.fingerGlowAlone
import dev.antigravity.fluidengine.ui.glass.field.LocalGlassPaintedPanel
import dev.antigravity.fluidengine.ui.glass.field.glassPainted
import dev.antigravity.fluidengine.ui.glass.field.glassPaintedControlSeen
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.glass.backdrop.glassApiLevel

internal val FluidGlassIconTouchTargetSize: Dp = FluidMetrics.TouchTarget

/** La profondita' piena di serie di `glassControlSurface`, come istanza sola. */
private val FullDepth: () -> Float = { 1f }

/** `frozen` e `heldWhileMoving` di serie, come istanza sola. */
private val NeverFrozen: () -> Boolean = { false }
internal val FluidGlassIconLensSize: Dp = FluidMetrics.GlassButtonLens

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
 * proporzione**. La crescita e' una frazione (×1,35 sul tondo Liquid, ×1,5 in Classic,
 * [FluidPressFeedback.Expand] arriva a ×1,1 con un tetto sul lato lungo), la deriva elastica e'
 * misurata sul lato corto del vetro, il bagliore sul lato lungo: nessun numero in dp da riscalare.
 * L'unico numero in dp e' il tetto della crescita di un tasto tondo — 24 dp in tutto, 12 per lato
 * ([RoundLongSideCapDp]). In Liquid il 35% è intero fino a circa 68,6 dp.
 *
 * **Il bersaglio del tocco non scende mai sotto i 48 dp**, qualunque sia la taglia: un vetro da
 * 40 dp sta dentro un riquadro da 48 che e' quello che occupa nel layout, e il tocco lo vede. Il
 * vetro e' la parte che si disegna, il riquadro e' la parte che si tocca. Il gesto e l'azione accessibile
 * vivono sul riquadro misurato. Il bagliore legge il contatto nelle coordinate del vetro. Lo prova
 * `FluidGlassButtonStylesUiTest` per ogni taglia, per la misura libera,
 * per l'etichetta `Compact` e per i tasti della barra di sempre (F6.4: «almeno 44 dp», i 44 pt di
 * Apple, chiusura di F4A).
 *
 * **La taglia d'ambiente** (F6.4, `controlSize` di SwiftUI): un tasto senza `size` prende
 * [LocalFluidGlassSize], di serie [Regular]. Una barra `Compact` la fornisce una volta per tutti i suoi
 * tasti, e l'icona di ciascuno ne discende ([iconSize]). Fogli e popover dell'host la riportano a
 * [Regular].
 *
 * @property lens il lato del vetro di un tasto tondo solo icona.
 * @property buttonHeight l'altezza del vetro di un tasto con un testo. A [Regular] sono 48 dp e non
 *   46: e' l'altezza che [FluidGlassButton] ha sempre avuto, e una riga con un'etichetta da 17 sp
 *   ha bisogno di piu' aria di un'icona.
 */
enum class FluidGlassSize(val lens: Dp, val buttonHeight: Dp) {
  /** 40 dp: accanto a un campo, dentro un composer, dove la misura di serie è già troppo. */
  Compact(40.dp, 40.dp),

  /** 46 dp per la lente (48 per un tasto con testo): la misura di serie. */
  Regular(FluidGlassIconLensSize, FluidGlassButtonHeight),

  /** 54 dp: la barra flottante di una schermata, dove il tasto sta accanto a una pillola alta quanto lui. */
  Large(54.dp, 54.dp),
  ;

  /** Il riquadro che il tasto tondo occupa nel layout e che il dito colpisce: mai meno di 48 dp. */
  val touchTarget: Dp get() = lens.coerceAtLeast(FluidGlassIconTouchTargetSize)

  /**
   * Quanto sta bene un'icona dentro il vetro: 0,6 del lato (24, 28, 32 dp), la taglia che il tasto fornisce a una
   * [FluidIcon] senza `size` ([LocalFluidIconSize]). Fino alla 3.x era la meta' (20, 22, 27): R6 ha misurato che
   * cosi' l'icona riempiva 0,37 del vetro contro 0,44-0,48 di Apple, e sembrava sottile ([FluidMetrics.IconLensRatio]).
   */
  val iconSize: Dp get() = FluidMetrics.iconSizeIn(lens)
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
internal fun Modifier.fluidTouchTarget(press: FluidPressState): Modifier =
  layout { measurable, constraints ->
    val minPx = FluidGlassIconTouchTargetSize.roundToPx()
    val placeable = measurable.measure(constraints)
    val width = constraints.constrainWidth(maxOf(placeable.width, minPx))
    val height = constraints.constrainHeight(maxOf(placeable.height, minPx))
    layout(width, height) {
      val x = (width - placeable.width) / 2
      val y = (height - placeable.height) / 2
      press.visualBounds = Rect(Offset(x.toFloat(), y.toFloat()), Size(placeable.width.toFloat(), placeable.height.toFloat()))
      placeable.place(x, y)
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
 *  * **It swells and stretches.** A held Liquid button grows by up to 10% with a label and 35% with
 *    a round icon. Classic uses [fluidPressTransform], with up to 50% for the round icon. Pushed off-axis,
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
 * Liquid changes the geometry of its field. Only translation goes through the renderer's
 * layer block, so the moving control keeps sampling the page under its current position.
 *
 * **Il lato resta quello del tema** (4.0, F2.3b), come per [glassSurface]: il controllo segue lo sfondo con velo, ombra e
 * tinta ma non gira, perche' l'engine non sa di che colore sia l'etichetta che l'app ci mette sopra. Girano i tasti
 * dell'engine che possiedono la propria etichetta ([FluidGlassIconButton], [FluidGlassButton], i tasti dei menu).
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
): Modifier = glassControlSurfaceInked(
  backdrop = backdrop,
  shape = shape,
  tint = tint,
  optics = optics,
  selected = selected,
  interactive = interactive,
  depth = depth,
  frozen = frozen,
  heldWhileMoving = heldWhileMoving,
  pressState = pressState,
  ink = null,
)

/**
 * [glassControlSurface] col patto dell'inchiostro (F2.3, [GlassInk]): il componente che la chiama avvolge il suo
 * contenuto in [GlassInkContent], e allora il vetro gira lato con lo sfondo (R4 B6). Senza patto ([ink] nullo) il vetro
 * resta del lato del tema, com'e' per un `glassControlSurface` messo da un'app su un suo contenitore: il vetro non sa di
 * che colore e' cio' che l'app ci disegna sopra.
 */
@Composable
internal fun Modifier.glassControlSurfaceInked(
  backdrop: GlassBackdropState,
  shape: Shape,
  tint: GlassTint,
  optics: GlassOptics,
  selected: Boolean,
  interactive: Boolean,
  depth: () -> Float,
  frozen: () -> Boolean,
  heldWhileMoving: () -> Boolean,
  pressState: FluidPressState?,
  ink: GlassInk?,
  /**
   * Gli attributi del piano (`fluidGlassId`, …) scritti sul `modifier` dell'app quando il componente lo posa su un nodo
   * **esterno** al vetro (il riquadro del tocco di [FluidGlassIconButton]): il componente li legge da lì e li inoltra.
   * Quelli scritti nella catena del vetro vincono.
   */
  forwardedAttributes: FluidGlassAttributes? = null,
  labelAnchor: FluidGlassLabelAnchor? = null,
  blurIcon: Boolean = false,
): Modifier {
  val accent = MaterialTheme.colorScheme.primary
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val onDark = GlassDefaults.isDarkSurface()
  // Dentro un `FluidGlassContainer` (4.0, F3.2) il tasto è un pezzo del piano flottante, di qualunque forma il campo
  // sappia rendere: il vetro lo disegna il contenitore, e gli attributi scritti alla sua sinistra (`fluidGlassId`, …)
  // sono suoi, come per un `fluidGlass`.
  val attributes = fluidGlassAttributesOf(this).takeIf { it != FluidGlassAttributes.None }
    ?: forwardedAttributes ?: FluidGlassAttributes.None
  val planeContainer = currentFluidGlassContainer(shape)
  // Dentro la barra ancorata delle pagine (3.0) il tasto dice dove sta la sua lente e, mentre le
  // pagine cambiano, spegne il proprio vetro: lo porta una superficie sola di Fluid-physics, che fa
  // diventare i tasti della pagina che esce quelli della pagina che arriva. Fuori, niente. In un contenitore del piano
  // vince il contenitore (F4A porterà la barra sul piano).
  val pieceSink = if (planeContainer == null) LocalFluidBarPieceSink.current else null
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
  // Senza vetro (lo porta Fluid-physics durante la staffetta) il contenuto non segue nessun lato: quello del tema.
  if (materialSuppressed) ink?.unbind()
  val ownPress = rememberFluidPressState(FluidPressFeedback.Expand)
  val press = pressState ?: ownPress
  val iconBlur = if (interactive && blurIcon && !materialSuppressed && glassApiLevel() >= 31) {
    val amount = remember(press) { Animatable(if (press.motion.held) 1f else 0f) }
    LaunchedEffect(press) { press.animateIconBlur(amount) }
    amount.asState()
  } else null
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
  // F6B.16: col materiale nuovo il controllo scelto è vetro tinto (la tinta di Apple, schiarita e mescolata al vetro);
  // col vetro di prima resta la pellicola d'accento qui sotto.
  val tinted = selected && rememberFluidTintedGlassActive()
  val selectionTint = remember(tint, selected, accent, systemContrast, tinted) {
    if (!selected) {
      tint
    } else if (tinted) {
      fluidTintedGlassTint(accent, tint).forContrast(systemContrast)
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
        // La selezione e' una pellicola d'accento, com'e' sempre stata. Su una tinta di serie (`Neutral`)
        // basterebbe la regola dei modi (cambiato il colore, la tinta si legge `Fixed`); il modo resta scritto
        // perche' qui puo' arrivare anche una tinta `Colored` (un pannello tinto), e la selezione deve restare
        // la pellicola d'accento anche li'.
        mode = GlassTintMode.Fixed,
      ).forContrast(systemContrast)
    }
  }

  // Il materiale con cui questo vetro si disegna (4.0.0). Lo chiede anche `glassSurface`, con gli stessi
  // ingressi, quindi le due risposte coincidono; qui serve saperlo prima, perche' col materiale nuovo il
  // bagliore del dito non e' piu' un disegno a parte sopra il vetro: passa dalla sua luce.
  val resolvedOptics = remember(optics) { optics.sanitized() }
  val marked = if (tinted) this.fluidTintedGlassMark(FluidTintedGlassMark(painted = false, liquid = true)) else this
  if (planeContainer != null) {
    return marked.glassControlPiece(
      container = planeContainer,
      attributes = attributes,
      backdrop = backdrop,
      shape = shape,
      tint = selectionTint,
      optics = resolvedOptics,
      interactive = interactive,
      press = press,
      tracksOwnPress = pressState == null,
      reducedMotion = reducedMotion,
      ink = ink,
      labelAnchor = labelAnchor,
      iconBlur = iconBlur,
    )
  }
  // La forma alla taglia del controllo (F1A, giro di correzione): una sola misura, per la scelta di qui e per il
  // vetro di prima qui sotto, perche' i due rispondano uguale.
  val fit = rememberGlassFieldFit(shape)
  val liquid = rememberFluidGlassModel(shape, GlassFalloff.Uniform, resolvedOptics, fit) == FluidGlassModel.Liquid
  val gel = rememberGelGeometry(press, reducedMotion, interactive && liquid)
  val pressLayer = when {
    !interactive -> null
    gel != null -> remember(gel) { gelTranslation(gel) }
    else -> remember(press, reducedMotion) { fluidGlassPressLayer(press, reducedMotion) }
  }

  return marked
    .then(fit.modifier)
    .then(if (gel != null && !materialSuppressed) Modifier.fingerGlowAlone(press, reducedMotion, onDark, shape, gel) else Modifier)
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
          gel = gel,
          sampleOnce = false,
          resampleIntervalMillis = 0L,
          frozen = frozen,
          heldWhileMoving = heldWhileMoving,
          nestInContainer = true,
          touch = null,
          ink = ink,
        )
      } else {
        // Gli argomenti di prima, piu' la misura della forma di qui sopra: i valori di serie di `glassSurface` scritti.
        Modifier.glassSurfaceFitted(
          state = backdrop,
          tint = selectionTint,
          shape = shape,
          edge = GlassEdge.None,
          falloff = GlassFalloff.Uniform,
          intensity = { 1f },
          role = GlassRole.Interactive,
          optics = optics,
          opticalDepth = depth,
          pressed = { press.progress.fastCoerceIn(0f, 1f) },
          exports = null,
          layerBlock = pressLayer,
          sampleOnce = false,
          resampleIntervalMillis = 0L,
          frozen = frozen,
          heldWhileMoving = heldWhileMoving,
          nestInContainer = true,
          fit = fit,
          ink = ink,
        )
      },
    )
    .then(when {
      materialSuppressed || !interactive -> Modifier
      gel != null -> Modifier.gelContent(gel, iconBlur, false)
      iconBlur != null -> Modifier.graphicsLayer {
        val scale = fluidPressTransform(press.feedback, press.progress, press.lean, size.width, size.height, density, reducedMotion)
        renderEffect = iconBlurEffect(iconBlur.value * FluidGel.BlurDp * density, scale.scaleX, scale.scaleY)
      }
      else -> Modifier
    })
    // Il bagliore di prima, un disegno sopra il vetro: col materiale nuovo e' nella sua luce.
    .then(if (interactive && !liquid) highlight.modifier else Modifier)
    // Segue il dito da solo solo quando nessuno lo guida: una pressione condivisa la muove il
    // fluidPressable del controllo, e due inseguitori su una pressione scatterebbero due volte a
    // ogni passo del dito.
    .then(if (interactive && pressState == null) Modifier.fluidPressTracking(ownPress, ownsPress = true) else Modifier)
}

/**
 * Un tasto di vetro dentro un `FluidGlassContainer` (4.0, F3.2): si iscrive al piano come pezzo, e il suo vetro lo
 * disegna il contenitore con la stessa tinta, ottica e pressione. Il campo e il contenuto leggono la stessa
 * [FluidGlassPressGeometry]. La traslazione del vetro passa dal layer così che la cattura segua il fondale. Il lato è del
 * contenitore, e **il tasto annidato prende il lato del contenitore** anche col suo inchiostro ([ink] si lega alla
 * decisione del contenitore). `depth`, `frozen` e `heldWhileMoving` li decide il contenitore.
 */
@Composable
private fun Modifier.glassControlPiece(
  container: FluidGlassContainerState,
  attributes: FluidGlassAttributes,
  backdrop: GlassBackdropState,
  shape: Shape,
  tint: GlassTint,
  optics: GlassOptics,
  interactive: Boolean,
  press: FluidPressState,
  tracksOwnPress: Boolean,
  reducedMotion: Boolean,
  ink: GlassInk?,
  labelAnchor: FluidGlassLabelAnchor?,
  iconBlur: State<Float>?,
): Modifier {
  val adaptive = container.adaptive
  if (ink != null) {
    if (adaptive != null) ink.bind(adaptive, GlassDefaults.isDarkSurface()) else ink.unbind()
  }
  val touch = remember(press, reducedMotion, interactive) {
    if (interactive) fluidGlassTouchLight(press, reducedMotion) else null
  }
  // La pressione come la vede il palco (giro di correzione di F3): la stessa trasformazione del layer, che il palco porta
  // sulla lastra del tasto in volo e rilascia con la sua molla, invece di tornare alla misura di riposo in un fotogramma.
  val pressGeometry = rememberGelGeometry(press, reducedMotion, interactive)
  val layer = remember(pressGeometry) { pressGeometry?.let(::gelTranslation) }
  // La variante del tasto (F6.4): limpido per lo stile `Clear`, che la fornisce attorno a questa sola chiamata.
  val variant = LocalGlassLiquidVariant.current
  val look = remember(backdrop, tint, optics, shape, press, touch, layer, pressGeometry, variant) {
    FluidGlassLook(
      backdrop = backdrop,
      tint = tint,
      role = GlassRole.Interactive,
      optics = optics,
      variant = variant,
      shape = shape,
      press = press,
      touch = touch,
      layerBlock = layer,
      pressGeometry = pressGeometry,
    )
  }
  return this
    .then(FluidGlassMarkerElement)
    .fluidGlassPiece(container, look, attributes, labelAnchor)
    .then(if (pressGeometry != null) Modifier.gelContent(pressGeometry, iconBlur, true) else Modifier)
    .then(if (interactive && tracksOwnPress) Modifier.fluidPressTracking(press, ownsPress = true) else Modifier)
}

@Composable
internal fun rememberGelGeometry(press: FluidPressState, reduced: Boolean, enabled: Boolean): FluidGlassPressGeometry? {
  if (!enabled) return null
  DisposableEffect(press) {
    press.bindGel(1)
    onDispose { press.bindGel(-1) }
  }
  return remember(press, reduced) { FluidGlassPressGeometry(press, reduced) }
}

internal fun gelTranslation(geometry: FluidGlassPressGeometry): GraphicsLayerScope.() -> Unit = {
  val drift = geometry.pose(size.width, size.height, density).drift
  translationX = drift.x
  translationY = drift.y
}

/** La posa agisce sul disegno del layer di contenuto. Anche i gesti aggiunti dal chiamante leggono coordinate di riposo. */
internal fun Modifier.gelContent(geometry: FluidGlassPressGeometry, iconBlur: State<Float>?, translates: Boolean): Modifier =
  drawWithCache {
    onDrawWithContent {
      val pose = geometry.pose(size.width, size.height, density)
      val pivot = center
      withTransform({
        if (translates) translate(pose.drift.x, pose.drift.y)
        scale(pose.scaleX, pose.scaleY, pivot)
      }) { this@onDrawWithContent.drawContent() }
    }
  }.graphicsLayer {
    renderEffect = if (iconBlur != null && iconBlur.value > 0f) {
      val pose = geometry.pose(size.width, size.height, density)
      iconBlurEffect(iconBlur.value * FluidGel.BlurDp * density, pose.scaleX, pose.scaleY)
    } else null
  }

/**
 * La scala esterna del contenuto porta la sigma locale alla sigma misurata sullo schermo.
 *
 * Sotto [FluidGelBlurFloorPx] l'icona non passa piu' da una sfocatura di mezzo pixel a nitida in un fotogramma (il
 * minimo di HWUI, F6.4): la copia sfocata al pavimento si mescola con quella nitida ([glassContentBlurEffect]). Sopra
 * il pavimento l'effetto e' quello di F6.1, al bit.
 */
private fun iconBlurEffect(sigmaPx: Float, scaleX: Float, scaleY: Float): RenderEffect? = glassContentBlurEffect(
  sigmaXPx = sigmaPx / scaleX,
  sigmaYPx = sigmaPx / scaleY,
  floorPx = FluidGelBlurFloorPx,
  radiusOf = FluidGel::blurRadiusPx,
  tileMode = TileMode.Clamp,
)

/**
 * La sigma sotto la quale l'icona premuta si mescola invece di sfocarsi: 0,75 px. Con il raggio di F6.1
 * ([FluidGel.blurRadiusPx], esatto: σ = 0,57735 r + 0,5) HWUI sa fare ogni σ sopra 0,5 px, ma a 0,5 il raggio e'
 * zero e l'icona scattava nitida; da 0,75 px (raggio 0,43) in giu' la copia sfocata scende per pesi, senza scalino.
 */
internal const val FluidGelBlurFloorPx: Float = 0.75f

/**
 * Glass without a backdrop to refract.
 *
 * For the one place the live material genuinely cannot go: a sheet or a dialog, which the platform
 * puts in its own window and which therefore cannot read another window's `GraphicsLayer`. Painted
 * gradients are an honest fallback there, and a bad idea anywhere a real backdrop exists — the whole
 * point of the rest of this file is that painted highlights do not survive contact with a finger.
 *
 * Dalla 4.0 (F1B.5) il dipinto e' **lo stesso vetro** del materiale `Liquid`, non un vetro suo: corpo, filo sul
 * perimetro, riga chiara e alone dentro il bordo, ombreggiatura verso il contorno e ombra sono calcolati dalla
 * ricetta di serie alla taglia della superficie (`glass/field/GlassPaintedProfile.kt`), e cambiano se cambia il
 * profilo. Cio' che non puo' avere, la pagina sfocata dietro e la lente, non lo finge: il corpo e' il colore pieno
 * che il vetro vero ha sopra la pagina del tema sotto lo scrim dei modali. Dentro un foglio o un avviso di
 * piattaforma dell'engine la superficie sa di stare su quel pannello, e vi si posa come un vetro annidato (velo e
 * ombra ridotti, come un tasto dentro una notifica).
 *
 * @param selected scelta: dalla 4.0 (F6B.16) vetro tinto, la tinta dell'accento schiarita e mescolata al vetro
 *   (`FluidTintedGlass.kt`), come in [glassControlSurface]; col vetro di prima la pellicola d'accento di sempre.
 * @param pressure 0..1: il bagliore del vetro premuto, uniforme (un dipinto non sa dov'e' il dito).
 */
@Composable
fun Modifier.fluidStaticGlassSurface(
  shape: Shape = FluidCapsuleShape,
  selected: Boolean = false,
  pressure: () -> Float = { 0f },
): Modifier {
  // Sotto Android 13 e con `Classic` resta il dipinto della 3.x, intero: corpo, bordo e niente ombra.
  if (!rememberPaintedGlassLiquid(LocalFluidGlassModel.current)) {
    return fluidStaticGlassSurfaceClassic(shape = shape, selected = selected, pressure = pressure)
  }
  val scheme = MaterialTheme.colorScheme
  // Chiaro o scuro lo decide una regola sola, `GlassDefaults.isDarkSurface()`: la luminanza di
  // `surface` ignora l'alpha, quindi un'app con una pellicola traslucida in `surface` veniva
  // letta come chiara sempre, e dichiarare da che parte sta (`LocalFluidSurfaceSide`) non serviva.
  val darkSide = GlassDefaults.isDarkSurface()
  // Su un pannello dipinto (il foglio e l'avviso di piattaforma) sta sul suo corpo, annidato; altrimenti sulla pagina
  // del tema, senza scrim: i pannelli modali, che uno scrim sotto lo hanno, il fondo lo passano da se'.
  val panel = LocalGlassPaintedPanel.current
  // Il cursore dell'utente (giro di correzione di F2), come nel pannello dipinto che lo porta.
  val preference = LocalFluidGlassPreference.current
  val seen = glassPaintedControlSeen(panel, scheme.background, darkSide)
  // F6B.16: scelta, la superficie dipinta è vetro tinto (la tinta di Apple della ricetta), non più la pellicola d'accento.
  val tint = if (selected) fluidTintedGlassColor(scheme.primary) else Color.Unspecified
  // L'ombra e' quella del vetro (le maschere della ricetta, o quella di prima dove il materiale e' `Classic`); da
  // annidato pesa quanto la regola dell'annidato del profilo dice.
  val shadowOptics = remember(panel != null, darkSide) {
    val optics = GlassDefaults.panelOptics()
    if (panel == null) {
      optics
    } else {
      optics.copy(shadowAlpha = optics.shadowAlpha * GlassLiquidProfile.Default.nesting(darkSide).shadow.coerceIn(0f, 1f))
    }
  }
  return this
    .then(if (selected) Modifier.fluidTintedGlassMark(FluidTintedGlassMark(painted = true, liquid = true)) else Modifier)
    .glassShadow(shape, shadowOptics)
    .glassPainted(
      shape = shape,
      darkSide = darkSide,
      seen = seen,
      nested = panel != null,
      pressure = pressure,
      preference = preference,
      tint = tint,
    )
}

/**
 * Se il vetro dipinto di questo punto dell'albero e' quello del materiale nuovo (`glass/field/GlassPaintedProfile.kt`)
 * o quello della 3.x ([fluidStaticGlassSurfaceClassic]). La **stessa regola** di ogni altro vetro,
 * [resolveFluidGlassModel], cosi' un foglio di piattaforma non e' mai un ibrido: sotto Android 13, con
 * [FluidGlassModel.Classic] chiesto da un antenato o su un dispositivo che rifiuta le sorgenti delle lastre, corpo,
 * bordo, ombra e scrim sono tutti quelli di prima. La forma non conta (il dipinto non ha un campo da rendere) e non e'
 * una caduta ne' un velo piatto. La chiedono `fluidStaticGlassSurface`, `FluidSheet` e l'avviso di `FluidAlert`.
 *
 * @param requested il materiale chiesto, cioe' [LocalFluidGlassModel] di chi chiama.
 */
internal fun paintedGlassTakesLiquid(
  requested: FluidGlassModel,
  sdk: Int = glassApiLevel(),
  shadersHealthy: () -> Boolean = ::glassLiquidSlabShadersHealthy,
): Boolean = resolveFluidGlassModel(
  sdk = sdk,
  shadersHealthy = shadersHealthy,
  requested = requested,
  falloff = GlassFalloff.Uniform,
  genericShape = false,
) == FluidGlassModel.Liquid

/** [paintedGlassTakesLiquid] ricordata per [requested]: la sonda delle sorgenti si chiede una volta. */
@Composable
internal fun rememberPaintedGlassLiquid(requested: FluidGlassModel): Boolean =
  remember(requested) { paintedGlassTakesLiquid(requested) }

/**
 * Il vetro dipinto **della 3.x**, alla lettera (com'era `fluidStaticGlassSurface` fino alla 4.0): tre gradienti in
 * diagonale e un tratto interno, nessuna ombra. E' cio' che si vede dove il vetro e' [FluidGlassModel.Classic]
 * ([paintedGlassTakesLiquid] falso), e non deve cambiare di un pixel.
 */
@Composable
internal fun Modifier.fluidStaticGlassSurfaceClassic(
  shape: Shape,
  selected: Boolean,
  pressure: () -> Float,
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
 * The regular lens occupies 46 dp inside a 48 dp hit target, with the visible glass centered in the target.
 *
 * Dalla 4.0 il tasto dice al contenuto quanto e' grande: una [FluidIcon] senza `size` dentro di lui e' a 0,6 del
 * vetro ([FluidGlassSize.iconSize], 26 dp a `Regular`), la proporzione delle icone di Apple (R6). Il colore resta
 * quello del contenuto: il tasto non lo cambia, quindi un'icona tinta dall'app resta tinta.
 *
 * @param size la taglia ([FluidGlassSize]): 40, 46 (di serie) o 54 dp di vetro. Il riquadro che il
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
 * @param style lo stile ([FluidGlassButtonStyle]): di serie quello d'ambiente ([LocalFluidGlassButtonStyle],
 *   [FluidGlassButtonStyle.Glass] se nessuno lo fornisce). Con [FluidGlassButtonStyle.Prominent] l'icona prende
 *   l'inchiostro che sulla tinta si legge meglio. Dalla 4.0 (F6.4). La taglia di serie e' quella d'ambiente
 *   ([LocalFluidGlassSize]).
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
  size: FluidGlassSize = LocalFluidGlassSize.current,
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
  /**
   * Cosa si sente quando scatta il click lungo; null per un click lungo che ha il suo colpo (un menu
   * che suona `Open` solo se si apre). Dalla 3.0.
   */
  longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current,
  content: @Composable BoxScope.() -> Unit,
) {
  // One press for the click and the glass: the lens swells, leans and lights up on the same
  // timeline the action is confirmed on, and a finger sliding over it ticks once, not twice.
  // Il campo Liquid applica RoundGrowth. Classic usa la ricetta ExpandRound di LibrePods.
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
    style = style,
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
 *  * **la crescita sotto il dito ha un tetto**: in Liquid ×1,35 fino a circa 68,6 dp, con un massimo di 24 dp
 *    ([FluidPressFeedback.ExpandRound]). Un disco da 76 dp arriva a 100, non a 114: con 16 dp fra due
 *    dischi non si toccano;
 *  * un'icona sta bene a 0,6 del lato ([FluidMetrics.iconSizeIn]: 24, 26, 32 dp per le tre taglie, 46 in un disco
 *    da 76), ed e' la taglia che il tasto da' a una [FluidIcon] dentro di lui.
 *
 * Le due strade si scelgono dal tipo: `size = FluidGlassSize.Large` o `size = 76.dp`. Senza `size`
 * vale l'overload con la taglia, `Regular`: questo vuole la misura.
 *
 * @param size il lato del vetro, positivo. La misura scritta non segue la taglia d'ambiente.
 * @param style lo stile, di serie quello d'ambiente: vedi l'altro overload. Dalla 4.0 (F6.4).
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
  style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current,
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
    style = style,
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
  /** Lo stile (F6.4). Di serie il vetro dei tasti: l'ancora di un menu della barra non segue lo stile d'ambiente. */
  style: FluidGlassButtonStyle = FluidGlassButtonStyle.Glass,
  lensWidth: Dp = lens,
  content: @Composable BoxScope.() -> Unit,
) {
  // F2.3: il contenuto del tasto segue l'inchiostro del lato del vetro, e solo cosi' il vetro gira con lo sfondo. Sul
  // lato del tema l'inchiostro e' quello che il tasto trova intorno, come prima.
  val ink = rememberGlassInk()
  val ambientInk = LocalContentColor.current
  val look = rememberFluidGlassButtonLook(style)
  // Il `modifier` dell'app sta sul riquadro del tocco, fuori dal vetro: gli attributi del piano scritti lì (`fluidGlassId`,
  // …) sono del vetro qui dentro (F3.2).
  val planeAttributes = fluidGlassAttributesOf(modifier)
  Layout(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.38f)
      .size(lensWidth.coerceAtLeast(FluidGlassIconTouchTargetSize),
        lens.coerceAtLeast(FluidGlassIconTouchTargetSize))
      .fluidPressable(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        role = Role.Button,
        haptic = haptic,
        focusShape = shape,
        // Senza vetro ([FluidGlass.Identity]) nessuno disegna la pressione: la disegna il gesto, che cede.
        pressState = if (look.glassless) null else press,
        longClickHaptic = longClickHaptic,
        grip = grip,
      ),
    content = {
      val lensChain = Modifier.size(lensWidth, lens)
        .then(if (onLensPlaced != null) Modifier.onGloballyPositioned(onLensPlaced) else Modifier)
        .then(lensModifier)
      Box(
        modifier = if (look.glassless) {
          lensChain.filledButtonFill(look, shape)
        } else {
          lensChain.glassButtonSurface(
            look = look,
            backdrop = backdrop,
            shape = shape,
            selected = selected,
            interactive = enabled,
            press = press,
            ink = ink,
            forwardedAttributes = planeAttributes,
            labelAnchor = null,
            blurIcon = FluidGel.BlurIcon,
          )
        },
        contentAlignment = Alignment.Center,
      ) {
        // Il tasto dice all'icona quanto e' grande (4.0): 0,6 del vetro. Un'icona con la sua taglia scritta resta com'e'.
        FluidGlassButtonInk(look, ink, ambientInk) {
          CompositionLocalProvider(LocalFluidIconSize provides FluidMetrics.iconSizeIn(lens)) { content() }
        }
      }
    },
  ) { measurables, constraints ->
    val glass = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
    val target = IntSize(constraints.constrainWidth(glass.width), constraints.constrainHeight(glass.height))
    val position = Alignment.Center.align(IntSize(glass.width, glass.height), target, layoutDirection)
    layout(target.width, target.height) {
      // Le ancore mantengono la misura del vetro. Il bagliore parte dal contatto nel suo riquadro.
      press.visualBounds = Rect(Offset(position.x.toFloat(), position.y.toFloat()), Size(glass.width.toFloat(), glass.height.toFloat()))
      glass.place(position)
    }
  }
}

/**
 * Come un tasto disegna il suo [FluidGlassButtonStyle] (F6.4): la pellicola, la variante del materiale, se un vetro c'e'
 * e, per lo stile in evidenza, l'inchiostro fisso dell'etichetta.
 *
 * @property ink l'inchiostro dell'etichetta di un tasto in evidenza, o [Color.Unspecified]: allora il contenuto segue il
 *   lato del vetro come sempre ([GlassInkContent]).
 * @property tintedAccent (F6B.16) per il tasto in evidenza col materiale nuovo, l'accento del suo vetro tinto: il vetro
 *   gira col fondo come gli altri tasti e l'etichetta prende l'inchiostro adattivo del vetro tinto
 *   ([rememberFluidTintedGlassInk]). [Color.Unspecified] altrimenti.
 * @property fill (F8.7b) il colore pieno del tasto [FluidGlassButtonStyle.Filled], senza vetro; [Color.Unspecified]
 *   altrimenti.
 */
@Immutable
internal class FluidGlassButtonLook(
  val tint: GlassTint,
  val variant: GlassLiquidVariant,
  val glassless: Boolean,
  val ink: Color,
  val tintedAccent: Color = Color.Unspecified,
  val fill: Color = Color.Unspecified,
) {
  /** Il tasto in evidenza: niente stato scelto, la sua tinta è il suo vetro. */
  val prominent: Boolean get() = ink.isSpecified || tintedAccent.isSpecified
}

/**
 * La resa di [style] con la tinta dei controlli [base] e l'accento del tema [accent]. Pura: lo stile di serie e il
 * limpido tengono la tinta dei controlli di sempre, quindi un tasto `Glass` si disegna con gli stessi ingressi di prima.
 *
 * @param tinted (F6B.16) vero col materiale nuovo: il tasto in evidenza è vetro tinto ([fluidTintedGlassTint]) con
 *   l'inchiostro adattivo. Falso, col vetro di prima: la pellicola che copre quanto serve perché l'etichetta stia ad
 *   almeno 4,5:1 su qualunque fondo ([fluidProminentTint]), com'era, perché lì la tinta `Colored` non si legge e il
 *   vetro non gira.
 */
internal fun resolveFluidGlassButtonLook(
  style: FluidGlassButtonStyle,
  base: GlassTint,
  accent: Color,
  tinted: Boolean = false,
): FluidGlassButtonLook =
  when {
    // F8.7b: il tasto pieno, senza vetro, con l'inchiostro chiesto se ci si legge.
    style.fill.isSpecified -> FluidGlassButtonLook(base, GlassLiquidVariant.Regular, glassless = true,
      fluidFilledInk(style.fill, style.fillInk), fill = style.fill.copy(alpha = 1f))
    style.glass.isIdentity -> FluidGlassButtonLook(base, GlassLiquidVariant.Regular, glassless = true, Color.Unspecified)
    style.isProminent && tinted -> {
      val tint = if (style.tint.isSpecified) style.tint else accent
      FluidGlassButtonLook(fluidTintedGlassTint(tint, base), GlassLiquidVariant.Regular, glassless = false,
        Color.Unspecified, tintedAccent = tint.copy(alpha = 1f))
    }
    style.isProminent -> {
      val tint = if (style.tint.isSpecified) style.tint else accent
      FluidGlassButtonLook(fluidProminentTint(tint, base), GlassLiquidVariant.Regular, glassless = false, fluidProminentInk(tint))
    }
    else -> FluidGlassButtonLook(fluidGlassTint(style.glass, base), style.glass.liquidVariant, glassless = false, Color.Unspecified)
  }

/**
 * L'inchiostro dell'etichetta di un tasto pieno ([FluidGlassButtonStyle.Filled]): [requested] se sul pieno [fill] fa
 * almeno [FluidProminentLabelContrast], altrimenti quello di [fluidProminentInk]. Pura.
 */
internal fun fluidFilledInk(fill: Color, requested: Color): Color {
  val opaque = fill.copy(alpha = 1f)
  if (requested.isSpecified && fluidContrastRatio(requested.copy(alpha = 1f), opaque) >= FluidProminentLabelContrast) {
    return requested.copy(alpha = 1f)
  }
  return fluidProminentInk(opaque)
}

/** Il pieno di un tasto [FluidGlassButtonStyle.Filled] nella sua forma, o niente per gli altri stili. */
private fun Modifier.filledButtonFill(look: FluidGlassButtonLook, shape: Shape): Modifier =
  if (look.fill.isSpecified) this.background(look.fill, shape) else this

@Composable
private fun rememberFluidGlassButtonLook(style: FluidGlassButtonStyle): FluidGlassButtonLook {
  val base = GlassDefaults.controlTint()
  val accent = MaterialTheme.colorScheme.primary
  val tinted = style.isProminent && rememberFluidTintedGlassActive()
  return remember(style, base, accent, tinted) { resolveFluidGlassButtonLook(style, base, accent, tinted) }
}

/**
 * Il vetro di un tasto col suo stile: [glassControlSurfaceInked] con la pellicola di [look]. La variante limpida arriva
 * alla lastra per [LocalGlassLiquidVariant], fornita attorno a questa sola chiamata (come fa `fluidGlass`), e solo
 * quando non e' quella di serie: un tasto `Glass` passa dagli stessi nodi di prima. Lo stile in evidenza non stringe il
 * patto dell'inchiostro: la sua etichetta ha l'inchiostro della tinta e il vetro non gira.
 */
@Composable
private fun Modifier.glassButtonSurface(
  look: FluidGlassButtonLook,
  backdrop: GlassBackdropState,
  shape: Shape,
  selected: Boolean,
  interactive: Boolean,
  press: FluidPressState,
  ink: GlassInk,
  forwardedAttributes: FluidGlassAttributes?,
  labelAnchor: FluidGlassLabelAnchor?,
  blurIcon: Boolean,
): Modifier {
  // F6B.16: il tasto in evidenza col materiale nuovo si registra come vetro tinto.
  val head = if (look.tintedAccent.isSpecified) {
    this.fluidTintedGlassMark(FluidTintedGlassMark(painted = false, liquid = true))
  } else {
    this
  }
  val surface: @Composable () -> Modifier = {
    head.glassControlSurfaceInked(
      backdrop = backdrop,
      shape = shape,
      tint = look.tint,
      optics = GlassDefaults.buttonOptics(),
      // Il tasto in evidenza non ha uno stato scelto: la sua pellicola e' la tinta, non l'accento della selezione.
      selected = selected && !look.prominent,
      interactive = interactive,
      depth = FullDepth,
      frozen = NeverFrozen,
      heldWhileMoving = NeverFrozen,
      pressState = press,
      ink = if (look.ink.isSpecified) null else ink,
      forwardedAttributes = forwardedAttributes,
      labelAnchor = labelAnchor,
      blurIcon = blurIcon,
    )
  }
  if (look.variant == GlassLiquidVariant.Regular) return surface()
  var surfaced: Modifier = Modifier
  CompositionLocalProvider(LocalGlassLiquidVariant provides look.variant) { surfaced = surface() }
  return surfaced
}

/**
 * L'inchiostro del contenuto di un tasto: quello fisso dello stile in evidenza, altrimenti quello del lato del vetro
 * ([GlassInkContent]) col tema [themeInk] come prima.
 */
@Composable
private fun FluidGlassButtonInk(look: FluidGlassButtonLook, ink: GlassInk, themeInk: Color, content: @Composable () -> Unit) {
  if (look.ink.isSpecified) {
    CompositionLocalProvider(LocalContentColor provides look.ink, content = content)
  } else if (look.tintedAccent.isSpecified) {
    // F6B.16: il vetro tinto gira col fondo; dentro, l'inchiostro adattivo del vetro tinto per il lato del vetro.
    GlassInkContent(ink, themeInk) {
      CompositionLocalProvider(LocalContentColor provides rememberFluidTintedGlassInk(look.tintedAccent), content = content)
    }
  } else {
    GlassInkContent(ink, themeInk, content)
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
 * @param style lo stile ([FluidGlassButtonStyle]), di serie quello d'ambiente ([LocalFluidGlassButtonStyle]).
 *   [FluidGlassButtonStyle.Prominent] e' l'azione principale: la tinta copre quanto serve perche' l'etichetta stia ad
 *   almeno 4,5:1 su qualunque fondo. Dalla 4.0 (F6.4). La taglia di serie e' quella d'ambiente ([LocalFluidGlassSize]).
 * @param sizing la larghezza ([FluidButtonSizing]), di serie quella d'ambiente ([LocalFluidButtonSizing]):
 *   [FluidButtonSizing.Fitted] quanto l'etichetta, [FluidButtonSizing.Flexible] tutta quella che gli si da'. Dalla 4.0.
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
  size: FluidGlassSize = LocalFluidGlassSize.current,
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  leading: (@Composable () -> Unit)? = null,
  maxLines: Int = Int.MAX_VALUE,
  style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current,
  sizing: FluidButtonSizing = LocalFluidButtonSizing.current,
) {
  val look = rememberFluidGlassButtonLook(style)
  // F6B.16: scelto col materiale nuovo, il tasto è vetro tinto e l'etichetta prende il suo inchiostro.
  val tintedInk = if (selected && rememberFluidTintedGlassActive()) rememberFluidTintedGlassInk() else Color.Unspecified
  val contentColor = if (selected) {
    if (tintedInk.isSpecified) tintedInk else MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  val press = rememberFluidPressState(FluidPressFeedback.Expand)
  // F2.3: l'etichetta segue l'inchiostro del lato del vetro (sul lato del tema e' quella di sempre). Selezionato, il
  // vetro e' una pellicola d'accento che non gira, e l'etichetta resta `onPrimaryContainer`.
  val ink = rememberGlassInk()
  val labelStyle = if (size == FluidGlassSize.Compact) {
    MaterialTheme.typography.bodyMedium
  } else {
    MaterialTheme.typography.bodyLarge
  }
  val plane = currentFluidGlassContainer(shape)
  val labelAnchor = remember(plane) { plane?.let { FluidGlassLabelAnchor() } }
  val pressed = modifier
    // Flessibile: tutta la larghezza data, con l'etichetta al centro (`buttonSizing(.flexible)`).
    .then(if (sizing == FluidButtonSizing.Flexible) Modifier.fillMaxWidth() else Modifier)
    .alpha(if (enabled) 1f else 0.38f)
    // Il bersaglio del gesto resta nelle coordinate di riposo anche mentre il contenuto cresce.
    .fluidPressable(
      onClick = onClick,
      enabled = enabled,
      role = Role.Button,
      haptic = haptic,
      focusShape = shape,
      // Senza vetro ([FluidGlass.Identity]) la pressione la disegna il gesto, che cede.
      pressState = if (look.glassless) null else press,
      grip = grip,
    )
    .fluidTouchTarget(press)
  val glassed = if (look.glassless) {
    pressed.filledButtonFill(look, shape)
  } else {
    pressed.glassButtonSurface(
      look = look,
      backdrop = backdrop,
      shape = shape,
      selected = selected,
      interactive = enabled,
      press = press,
      ink = ink,
      forwardedAttributes = null,
      labelAnchor = labelAnchor,
      blurIcon = false,
    )
  }
  Row(
    modifier = glassed
      .heightIn(min = size.buttonHeight)
      .padding(horizontal = size.buttonPadding),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FluidGlassButtonInk(look, ink, contentColor) {
      if (leading != null) {
        Box(modifier = Modifier.size(size.buttonLeading), contentAlignment = Alignment.Center) {
          // L'icona davanti all'etichetta prende la taglia del suo posto, non quella di un tasto solo icona.
          CompositionLocalProvider(LocalFluidIconSize provides size.buttonLeading) { leading() }
        }
      }
      val label: @Composable (String) -> Unit = { value ->
        Text(
          text = value,
          style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
          maxLines = maxLines,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (labelAnchor == null) label(text) else {
        val lineHeight = labelStyle.lineHeight
        val reference = with(LocalDensity.current) {
          if (lineHeight.isSp) lineHeight.toDp() else GlassContentDefaults.BlurReference
        }
        FluidGlassContentTransitionWithShape(text, blurReference = reference, labelAnchor = labelAnchor, content = label)
      }
    }
  }
}
