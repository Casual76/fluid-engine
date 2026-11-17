/*
 * Il vetro come configurazione (piano «iOS in Android», 3.13 e F2.2): il `Glass` di SwiftUI. Chi scrive un'app dice
 * quale vetro vuole (di serie, limpido, nessuno), se è tinto e se reagisce al dito; come si disegna lo decide l'engine.
 *
 * Qui c'è il **tipo** e il modificatore che lo posa su un elemento, `Modifier.fluidGlass(glass, shape, backdrop)`
 * (`glassEffect(_:in:)` di SwiftUI, F3.2), e quello di un pannello che porta contenuto, `Modifier.fluidGlassPanel`
 * (F3.2e); il contenitore che li raccoglie in un piano è `FluidGlassContainer`.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.GlassTouchLight

/**
 * Quale vetro: la configurazione del materiale nuovo, come `Glass` in SwiftUI.
 *
 *     FluidGlass.Regular                        // il vetro di serie: smerigliato, adattivo
 *     FluidGlass.Regular.tint(accent)           // tinto: la tonalità resta, la luminosità segue lo sfondo
 *     FluidGlass.Clear.interactive()            // limpido, e reagisce al dito
 *     FluidGlass.Identity                       // nessun vetro, senza cambiare l'albero
 *
 * Lo posa su un elemento [fluidGlass]; dentro un `FluidGlassContainer` i vetri formano un piano che si anima da solo.
 *
 * Le tre varianti non si mescolano (Apple, WWDC25 219):
 *  - [Regular] è il vetro di quasi tutto: leggibile su qualunque sfondo e a qualunque taglia, perché si adatta a ciò
 *    che ha dietro (velo, ombra, lato chiaro o scuro). Va bene sotto il testo;
 *  - [Clear] è **più trasparente** e **non si adatta**: nessuna smerigliatura, e uno scurimento del 35% sotto il vetro
 *    (HIG) che tiene leggibile ciò che gli sta sopra. Va usato solo se tutte e tre: sta sopra contenuto ricco (foto,
 *    video), quel contenuto regge lo scurimento, e ciò che sta sul vetro è chiaro e marcato;
 *  - [Identity] è il vetro spento: il contenuto resta come senza vetro. Serve ad accendere e spegnere il vetro senza
 *    cambiare la struttura della schermata (e quindi senza perdere stato né animazioni).
 *
 * Il vetro di prima ([FluidGlassModel.Classic], e tutto sotto Android 13) non ha varianti: lì [Clear] è [Regular].
 *
 * Uguaglianza strutturale: un'istanza ricreata a ogni ricomposizione non invalida niente.
 */
@Immutable
class FluidGlass private constructor(
  internal val variant: Variant,
  /**
   * La tinta, o [Color.Unspecified] se il vetro non è tinto. È la tinta di Apple: la **tonalità** resta, la
   * **luminosità** segue lo sfondo (su bianco piena, su nero al 58%: R4 A7), e l'alfa del colore è quanto copre. La
   * tinta è per l'azione principale, non di serie (sezione 2 del piano).
   */
  val tint: Color,
  /** Se il vetro reagisce al dito: si gonfia, si illumina sotto il dito, la lente si approfondisce. */
  val isInteractive: Boolean,
) {

  /** Le tre varianti. Interne: un `when` dell'app su un tipo che cresce si romperebbe a ogni valore nuovo. */
  internal enum class Variant { Regular, Clear, Identity }

  /** Vero per [Clear]. */
  val isClear: Boolean get() = variant == Variant.Clear

  /** Vero per [Identity]: il vetro non c'è. */
  val isIdentity: Boolean get() = variant == Variant.Identity

  /** Lo stesso vetro tinto di [color]; `null` o [Color.Unspecified] toglie la tinta. Su [Identity] non cambia niente. */
  fun tint(color: Color?): FluidGlass {
    val next = color?.takeIf { it.isSpecified } ?: Color.Unspecified
    return if (variant == Variant.Identity || next == tint) this else FluidGlass(variant, next, isInteractive)
  }

  /** Lo stesso vetro che reagisce al dito (o no, con [enabled] falso). Su [Identity] non cambia niente. */
  fun interactive(enabled: Boolean = true): FluidGlass =
    if (variant == Variant.Identity || enabled == isInteractive) this else FluidGlass(variant, tint, enabled)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FluidGlass) return false
    return variant == other.variant && tint == other.tint && isInteractive == other.isInteractive
  }

  override fun hashCode(): Int {
    var result = variant.hashCode()
    result = 31 * result + tint.hashCode()
    result = 31 * result + isInteractive.hashCode()
    return result
  }

  override fun toString(): String = buildString {
    append("FluidGlass.").append(variant.name)
    if (tint.isSpecified) append(".tint(").append(tint).append(')')
    if (isInteractive) append(".interactive()")
  }

  companion object {
    /** Il vetro di serie: smerigliato e adattivo. */
    val Regular: FluidGlass = FluidGlass(Variant.Regular, Color.Unspecified, isInteractive = false)

    /** Il vetro limpido: nessuna smerigliatura né adattività, scurimento del 35% sotto. Vedi [FluidGlass]. */
    val Clear: FluidGlass = FluidGlass(Variant.Clear, Color.Unspecified, isInteractive = false)

    /** Nessun vetro: il contenuto com'è, nella stessa struttura. */
    val Identity: FluidGlass = FluidGlass(Variant.Identity, Color.Unspecified, isInteractive = false)
  }
}

// ------------------------------------------------------------------------------------------------------------
// Dal tipo al materiale
// ------------------------------------------------------------------------------------------------------------

/** La variante della ricetta del materiale nuovo. [FluidGlass.Identity] è [GlassLiquidVariant.Regular] a intensità zero. */
internal val FluidGlass.liquidVariant: GlassLiquidVariant
  get() = if (variant == FluidGlass.Variant.Clear) GlassLiquidVariant.Clear else GlassLiquidVariant.Regular

/** Quanto vetro c'è: zero per [FluidGlass.Identity], uno altrimenti. Si moltiplica all'`intensity` della superficie. */
internal val FluidGlass.presence: Float
  get() = if (variant == FluidGlass.Variant.Identity) 0f else 1f

/**
 * La tinta di Apple come la ricetta la vuole (`tint` di `glassRecipe`, cioè `GlassTintMode.Colored`), o
 * [Color.Unspecified].
 */
internal val FluidGlass.recipeTint: Color
  get() = if (variant == FluidGlass.Variant.Identity) Color.Unspecified else tint

// ------------------------------------------------------------------------------------------------------------
// Il modificatore (F3.2)
// ------------------------------------------------------------------------------------------------------------

/**
 * Posa un vetro dietro a questo elemento: `glassEffect(_:in:)` di SwiftUI.
 *
 * ```
 * Icon(FluidIcons.Play, null, Modifier.size(44.dp).fluidGlass())                  // una capsula di vetro di serie
 * Text("Fatto", Modifier.fluidGlass(FluidGlass.Regular.tint(accent).interactive()))
 * Box(Modifier.fluidGlass(FluidGlass.Clear, ContinuousCornerShape(24.dp))) { … }   // limpido, sopra una foto
 * ```
 *
 * **Dentro un [FluidGlassContainer]** l'elemento è un pezzo del piano: il vetro lo disegna il contenitore (dietro al
 * contenuto, una cattura per grappolo), lo fonde coi vicini, lo porta da uno stato all'altro quando l'app cambia lo
 * stato, e ne decide il lato insieme a tutti gli altri; il contenuto ne riceve l'inchiostro. L'identità e il modo di
 * comparire si dichiarano **prima**, nella stessa catena: `Modifier.fluidGlassId(id, ns).fluidGlass()` (vedi
 * [fluidGlassId]: scritti dopo non hanno effetto).
 *
 * **Fuori da un contenitore** è un vetro a sé, come [glassSurface] (o [glassControlSurface] se reagisce al dito): segue
 * lo sfondo con velo e ombra, ma **non gira** chiaro/scuro, perché un modificatore non sa di che colore è ciò che l'app
 * gli disegna sopra. Per il patto dell'inchiostro, un contenitore anche con un pezzo solo.
 *
 * [FluidGlass.Clear] è il vetro limpido: niente smerigliatura, uno scurimento fisso del 35% sotto (HIG), nessuna
 * adattività. [FluidGlass.Identity] non disegna niente e lascia il contenuto com'è. Col vetro di prima
 * (`FluidGlassModel.Classic`, e sotto Android 13) il limpido è il vetro di serie.
 *
 * Una [FluidConcentricShape] si risolve al layout contro il contenitore di forma più vicino ([fluidContainerShape]).
 *
 * @param glass quale vetro: [FluidGlass.Regular] di serie.
 * @param shape la sagoma: la capsula di serie. Una forma che il campo non sa rendere (un `Outline` generico) resta un
 *   vetro a sé anche in un contenitore.
 * @param backdrop ciò che il vetro rifrange: di serie quello del punto in cui sta ([currentGlassBackdrop]).
 */
@Composable
fun Modifier.fluidGlass(
  glass: FluidGlass = FluidGlass.Regular,
  shape: Shape = FluidCapsuleShape,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
): Modifier {
  // Gli attributi scritti alla sinistra di questo vetro, nella stessa catena (il contratto dell'ordine).
  val attributes = fluidGlassAttributesOf(this)
  val marked = this.then(FluidGlassMarkerElement)
  if (glass.isIdentity) return marked
  val baseTint = GlassDefaults.controlTint()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val press = if (glass.isInteractive) rememberFluidPressState(FluidPressFeedback.Expand) else null
  val container = currentFluidGlassContainer(shape)
  if (container != null) {
    val touch = remember(press, reducedMotion) { press?.let { fluidGlassTouchLight(it, reducedMotion) } }
    val layer = remember(press, reducedMotion) { press?.let { fluidGlassPressLayer(it, reducedMotion) } }
    val look = remember(glass, shape, backdrop, baseTint, press, touch, layer) {
      fluidGlassLook(glass, shape, backdrop, baseTint, press, touch, layer)
    }
    return marked
      .fluidGlassPiece(container, look, attributes)
      // Il contenuto cresce e segue il dito col vetro, che lo fa nella sua superficie con lo stesso blocco.
      .then(if (layer != null) Modifier.graphicsLayer(layer) else Modifier)
      .then(if (press != null) Modifier.fluidPressTracking(press, ownsPress = true) else Modifier)
  }
  return marked.fluidGlassAlone(glass, shape, backdrop, baseTint)
}

/**
 * Un **pannello** di vetro che può essere un pezzo del piano (F3.2e): lo stesso vetro di [glassSurface], con il suo
 * ruolo, la sua tinta e la sua ottica, per un vetro che porta del contenuto (una card, un dock, una barra di scrittura,
 * la capsula di un campo). [fluidGlass] non basta, perché ha la ricetta dei controlli (la loro pellicola, il ruolo
 * `Floating` o `Interactive`) e la tinta di Apple, non la pellicola di un pannello.
 *
 * ```
 * FluidGlassContainer(modifier, propagateMinConstraints = true) {
 *   Column(Modifier.fluidGlassPanel(backdrop, GlassDefaults.modalTint(), shape, GlassRole.Modal).padding(16.dp)) {
 *     FluidGlassNested { … }      // i vetri del contenuto sono annidati nel pannello, non suoi fratelli
 *   }
 * }
 * ```
 *
 * **Dentro un [FluidGlassContainer]** è un pezzo come un `fluidGlass` (identità, unione e transizione si scrivono prima,
 * nella stessa catena): il vetro lo disegna il contenitore con questi stessi ingressi, il contenuto ne riceve
 * l'inchiostro, e il contenitore gira o scurisce col lato. Il pannello **esporta il suo materiale** ([exports], o uno
 * suo se l'app non ne dà) e fa da contenitore di vetro per il suo contenuto: un vetro dentro un [FluidGlassNested] lo
 * campiona come su un `glassSurface`, e prende il suo lato.
 *
 * **Fuori da un contenitore** (e col vetro di prima, e sotto Android 13) è esattamente `glassSurface(backdrop, tint,
 * shape, role = role, optics = optics, exports = exports, resampleIntervalMillis = …)`, com'era: del lato del tema.
 *
 * @param tint la pellicola del pannello: quella del suo ruolo (`GlassDefaults.modalTint()`, `floatingTint()`, …), letta
 *   **fuori** dal contenitore, dove il lato è quello del tema.
 * @param large se il pannello è un **grande** che scurisce in continuo (vero) o un piccolo che gira (falso). Null: lo
 *   dice il ruolo, come per i grandi dell'engine (`Modal` e `Content` scuriscono). Serve a dire ciò che il ruolo non sa:
 *   un dock `Floating` a raggio di foglio è un grande, una barra `Modal` alta una riga è un piccolo.
 * @param exports dove pubblicare il materiale del pannello, se l'app lo vuole anche altrove (un tasto che lo campiona
 *   per un cablaggio suo, con `rememberCombinedGlassBackdrop`). Dentro un contenitore, null vuol dire un materiale del
 *   pannello, che si registra solo mentre qualcuno lo campiona.
 * @param resampleIntervalMillis vedi [glassSurface].
 */
@Composable
fun Modifier.fluidGlassPanel(
  backdrop: GlassBackdropState,
  tint: GlassTint,
  shape: Shape,
  role: GlassRole = GlassRole.Floating,
  optics: GlassOptics = GlassDefaults.optics(role),
  large: Boolean? = null,
  exports: GlassBackdropState? = null,
  resampleIntervalMillis: Long = 0L,
): Modifier {
  val attributes = fluidGlassAttributesOf(this)
  val marked = this.then(FluidGlassMarkerElement)
  val container = currentFluidGlassContainer(shape)
  if (container != null) {
    // Il materiale per i vetri annidati: quello dell'app, o uno del pannello (non costa niente finché nessuno lo campiona).
    val material = exports ?: rememberGlassBackdrop()
    val look = remember(backdrop, tint, shape, role, optics, large, material, resampleIntervalMillis) {
      fluidGlassPanelLook(backdrop, tint, shape, role, optics, large, material, resampleIntervalMillis)
    }
    return marked
      .fluidGlassPiece(container, look, attributes)
      // Il vetro del pannello lo disegna un nodo fratello (la superficie del contenitore): il contenuto lo sa da qui.
      .glassContainer(material)
  }
  return marked.glassSurface(
    state = backdrop,
    tint = tint,
    shape = shape,
    role = role,
    optics = optics,
    exports = exports,
    resampleIntervalMillis = resampleIntervalMillis,
  )
}

/**
 * L'aspetto di un [fluidGlassPanel] nel contenitore: gli stessi ingressi di `glassSurface` (l'ottica passata da
 * `sanitized()`, come fa lui), la variante di serie, e il grande dichiarato o quello del ruolo. Pura.
 */
internal fun fluidGlassPanelLook(
  backdrop: GlassBackdropState,
  tint: GlassTint,
  shape: Shape,
  role: GlassRole,
  optics: GlassOptics,
  large: Boolean?,
  exports: GlassBackdropState?,
  resampleIntervalMillis: Long,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
): FluidGlassLook = FluidGlassLook(
  backdrop = backdrop,
  tint = tint,
  role = role,
  optics = optics.sanitized(),
  variant = GlassLiquidVariant.Regular,
  shape = shape,
  exports = exports,
  resampleIntervalMillis = resampleIntervalMillis,
  large = large ?: profile.adaptive.darkensContinuously(role),
)

/** Un `fluidGlass` fuori da un contenitore: la superficie di sempre, con la forma concentrica risolta al layout. */
@Composable
private fun Modifier.fluidGlassAlone(
  glass: FluidGlass,
  shape: Shape,
  backdrop: GlassBackdropState,
  baseTint: GlassTint,
): Modifier {
  val concentric = shape as? FluidConcentricShape
  val layoutDirection = LocalLayoutDirection.current
  val radii = if (concentric != null) rememberFluidConcentricRadii(concentric.minimum) else null
  val drawn = if (concentric != null && radii != null) {
    remember(concentric, radii.radii, layoutDirection) { concentric.resolvedTo(radii.radii, layoutDirection) }
  } else {
    shape
  }
  val head = if (radii != null) this.fluidConcentricRadii(radii) else this
  // Il limpido è una variante del materiale nuovo: col vetro di prima è il vetro di serie (vedi [FluidGlass]), e lì la
  // variante non si legge.
  val tint = fluidGlassTint(glass, baseTint)
  val optics = fluidGlassOptics(glass)
  // La variante arriva alla lastra per [LocalGlassLiquidVariant] (F3.2c): `glassSurface` e `glassControlSurface` non la
  // hanno fra i loro ingressi. Fornita attorno alla sola chiamata del modificatore, sempre (la struttura dei gruppi non
  // cambia fra limpido e di serie), e mai attorno al contenuto.
  var surface: Modifier = Modifier
  CompositionLocalProvider(LocalGlassLiquidVariant provides glass.liquidVariant) {
    surface = if (glass.isInteractive) {
      head.glassControlSurface(backdrop = backdrop, shape = drawn, tint = tint, optics = optics)
    } else {
      head.glassSurface(state = backdrop, tint = tint, shape = drawn, role = GlassRole.Floating, optics = optics)
    }
  }
  return surface
}

/**
 * La pellicola di un `fluidGlass`: quella di serie dei controlli, o la tinta di Apple ([GlassTintMode.Colored]: la
 * tonalità resta, la luminosità segue lo sfondo) se il vetro è tinto. Anche per il limpido: lo scurimento del 35%, il
 * velo che non c'è e la smerigliatura a nitido li fa la variante della ricetta (`GlassLiquidVariant.Clear`, F3.2c), che
 * arriva fino alle superfici. Fino a F3.2 qui c'era un ponte (pellicola nera e smerigliatura a zero): a riposo lo stesso
 * conto, ma senza la mescola che tiene il raggio (piano 3.6), cioè senza poter animare fra le due varianti.
 */
internal fun fluidGlassTint(glass: FluidGlass, base: GlassTint): GlassTint = when {
  glass.recipeTint.isSpecified -> GlassTint(
    overlay = glass.tint,
    fallback = glass.tint.compositeOver(base.fallback),
    hairline = base.hairline,
    mode = GlassTintMode.Colored,
  )
  else -> base
}

/** Il bagliore del dito di un `fluidGlass` interattivo, come quello di `glassControlSurface`. */
internal fun fluidGlassTouchLight(press: FluidPressState, reducedMotion: Boolean): GlassTouchLight = GlassTouchLight(
  amount = { press.progress.fastCoerceIn(0f, 1f) * (if (reducedMotion) 0.4f else 1f) },
  position = { press.glow },
)

/**
 * La trasformazione della pressione di un `fluidGlass` interattivo (cresce, si piega verso il dito), la stessa di
 * `glassControlSurface`: il vetro la riceve come `layerBlock` (il renderer la corregge quando campiona), il contenuto
 * come layer suo.
 */
internal fun fluidGlassPressLayer(press: FluidPressState, reducedMotion: Boolean): GraphicsLayerScope.() -> Unit = {
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
