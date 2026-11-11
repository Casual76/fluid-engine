package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.glassContainerScope
import dev.antigravity.fluidengine.ui.glass.backdrop.glassMaterialRecording
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.colorControls
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.HighlightStyle
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.ShapeProvider
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.ShadowElement
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidSurface
import dev.antigravity.fluidengine.ui.glass.field.GlassShadowSpec
import dev.antigravity.fluidengine.ui.glass.field.GlassTouchLight
import dev.antigravity.fluidengine.ui.glass.field.glassShadow as glassFieldShadow

/**
 * Fluid Glass — a real refracting material, not a translucent rectangle.
 *
 * The optics are done by the vendored `backdrop` renderer in
 * [dev.antigravity.fluidengine.ui.glass.backdrop] (Kyant0/AndroidLiquidGlass, Apache-2.0; see
 * `LICENSES/AndroidLiquidGlass.md`). This file is the engine's opinion about *how much* of it each
 * kind of surface gets, and, for this glass, nothing else: no shader of ours competes with it.
 *
 * Dalla 4.0.0 quello descritto qui e' il vetro **di prima** ([FluidGlassModel.Classic]). Da Android 13, dove
 * il compilatore accetta le sue sorgenti, [glassSurface] disegna di serie il materiale nuovo
 * ([FluidGlassModel.Liquid]): shader dell'engine (`ui/glass/field`), una catena sola sulla cattura, la luce e
 * l'ombra disegnate a parte. Non passa per i cinque passi qui sotto. Quali superfici restano a questo vetro, e
 * perche': `resolveFluidGlassModel` in `FluidGlassModel.kt`.
 *
 * What the material actually does, in the order the pixels see it:
 *
 *  1. **Capture.** [glassBackdropSource] records the screen's body into a `GraphicsLayer`. Every
 *     pane of glass on that screen samples the same recording, transformed into its own coordinates,
 *     so a bar and a button hold the *same* image rather than two independent screenshots.
 *  2. **Vibrancy.** Saturation is pushed past 1. Glass concentrates the colour it transmits, and
 *     without this step a pane over a photograph reads as grey plastic.
 *  3. **Blur.** The frosting. Kept lighter than the old implementation on purpose — the previous
 *     glass compensated for having no real refraction by nearly hiding what was behind it.
 *  4. **Lens.** A signed-distance field of the pane's own shape drives a displacement that grows
 *     towards the edge, so the backdrop *bends* into the perimeter the way a thick edge bends light.
 *     This is the step the old implementation only faked in a 2 dp rim, and its absence is why every
 *     control looked flat.
 *  5. **Tint, highlight, shadows.** A specular ring lit from one angle, an inner shadow that gives
 *     the pane thickness, and a drop shadow that lifts it off the page.
 *
 * Steps 2–4 need `RenderEffect` (API 31) and AGSL (API 33). Below those the material degrades on its
 * own: API 31–32 keeps blur and vibrancy and loses the lens, and below API 31 [glassSurface] paints
 * [GlassTint.fallback] — a defined, near-opaque material rather than a broken one.
 *
 * The API is the same one the engine has always exposed — [rememberGlassBackdrop],
 * [glassBackdropSource], [glassSurface] — so screens do not change. [GlassOptics] did change: its
 * vocabulary is now the physical one (how deep the lens reaches, how far it displaces) instead of
 * the rim-painting one, because there is no rim being painted any more.
 */
@Stable
class GlassBackdropState internal constructor(
  internal val backdrop: Backdrop,
  /**
   * The layer this state records into, when it has one.
   *
   * Null for a state assembled out of other states by [rememberCombinedGlassBackdrop]: that one has
   * nothing of its own to record, it only says which recordings to stack and in what order.
   */
  internal val layerBackdrop: LayerBackdrop?,
  /**
   * Depth of the frosting, before each role's own multiplier. Solo nel vetro di prima: il materiale nuovo
   * ([FluidGlassModel.Liquid]) prende la smerigliatura dal proprio profilo e non legge questo raggio.
   */
  val blurRadius: Dp,
) {

  /** False below API 31: no `RenderEffect`, so surfaces fall back to a solid material. */
  val supported: Boolean get() = isRenderEffectSupported()

  /** False below API 33: blur and vibrancy still work, the lens does not. */
  val refractionSupported: Boolean get() = isRuntimeShaderSupported()
}

/**
 * Reference depth of the frosting. **Two and a half dp.**
 *
 * This number went 8 → 16 → 2 → 2.5, and the middle step is the mistake worth writing down. The
 * reasoning for widening it was sound in isolation — a wider kernel lets the tint come down, and it is the
 * tint that made the old glass look like painted plastic — but it answered the wrong question. At 16
 * dp a pane stops transmitting an image and starts transmitting that image's *average*, and a pane
 * holding an average is a fill. All the work the lens does at the edge is then bending a flat colour
 * into another flat colour, which is invisible by construction.
 *
 * Real Liquid Glass is nearly clear. What identifies it is the **displacement** at the perimeter and
 * the specular line on top, not the frosting — so the radius stays tiny and the lens carries the
 * material. Surfaces that genuinely have to hide something (a scrim, a full-page modal) ask for a
 * multiple of this through [GlassOptics.blurScale] instead of everything paying for it.
 *
 * Calibrated against Kyant's own catalogue and against Square (github.com/Lelonio/Square), which
 * ships the same renderer tuned by hand and lands on the same 2 dp.
 *
 * Il mezzo dp in piu' (2.5) e' una taratura chiesta a occhio: il vetro di base un filo piu'
 * smerigliato, e ancora lontanissimo dagli otto dp oltre i quali un pannello trasmette la sua
 * media invece di un'immagine. Questo e' il raggio dei pannelli *piccoli*: quelli grandi ne
 * pagano di piu' per via della regola della taglia, vedi [glassSizeGain].
 *
 * Dalla 4.0.0 vale per il vetro di prima ([FluidGlassModel.Classic]). Il materiale nuovo
 * ([FluidGlassModel.Liquid]) ha la smerigliatura del suo profilo, molto piu' larga, e non legge questo
 * raggio: qui la lezione «oltre otto dp si trasmette la media» resta vera per il vetro di prima, mentre il
 * nuovo nasconde lo sfondo con la smerigliatura e si fa riconoscere dal filo e dal riflesso.
 */
val DefaultGlassBlurRadius: Dp = 2.5.dp

@Composable
fun rememberGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState {
  val layerBackdrop = rememberLayerBackdrop()
  return remember(layerBackdrop, blurRadius) {
    // Su richiesta (2.13.0): quando questo stato e' il materiale che un pannello esporta
    // (`exports`), il pannello lo registra solo mentre del vetro lo sta campionando. Usato come
    // registrazione di una pagina (`glassBackdropSource`) non cambia niente: quella si registra
    // sempre, come prima.
    layerBackdrop.onDemand = true
    GlassBackdropState(layerBackdrop, layerBackdrop, blurRadius)
  }
}

/**
 * Stacks two backdrops into one, back to front.
 *
 * This is what lets glass sit on glass and still be honest. A control on a floating bar has to
 * refract the *bar* — its material, its tint, the icons printed on it — and not the page three
 * layers down; combining the page's recording with the bar's own gives the control exactly the image
 * a real lens resting on that bar would find.
 *
 * Dalla 2.13.0 per il caso comune non serve piu': una superficie di vetro dentro un'altra ne
 * campiona il materiale da sola (vedi `nestInContainer` in [glassSurface]). Resta per i cablaggi che
 * vogliono qualcosa di diverso dal pannello che contiene il controllo — l'indicatore di una barra
 * che guarda anche la copia accentata delle etichette — e un cablaggio a mano vince sempre.
 */
@Composable
fun rememberCombinedGlassBackdrop(
  behind: GlassBackdropState,
  inFront: GlassBackdropState,
  blurRadius: Dp = inFront.blurRadius,
): GlassBackdropState {
  val combined = rememberCombinedBackdrop(behind.backdrop, inFront.backdrop)
  return remember(combined, blurRadius) { GlassBackdropState(combined, null, blurRadius) }
}

/**
 * Marks the content that glass surfaces sample.
 *
 * Apply this to the scrolling body of a screen and never to the whole screen, otherwise the bars end
 * up sampling themselves. The body must also paint its own opaque background *inside* the recorded
 * region: a recording of text on transparency refracts into a smear that the sharp original still
 * shows through.
 */
fun Modifier.glassBackdropSource(
  state: GlassBackdropState,
  /**
   * While this answers true, the body is not re-recorded and the glass over it keeps the capture
   * it already has.
   *
   * For content that moves. Recording costs one traversal of the whole subtree per frame and scales
   * with the number of draw ops in it, so a list being flung pays for every row it holds on every
   * frame — see `LayerBackdropModifier`, where the same measurement is written down. Held still,
   * Compose takes its ordinary draw path and reuses every child that did not change.
   *
   * What it costs is a reflection that stops following while the content slides past. Under the
   * blur a pane applies that is close to invisible, and it buys back most of a frame.
   */
  frozen: () -> Boolean = { false },
): Modifier {
  // A combined state has no layer of its own; the states it was built from record theirs.
  val layer = state.layerBackdrop ?: return this
  return this
    .layerBackdrop(layer, frozen)
    // Rasterised **once**, sampled many times. A recording holds its content by reference, so
    // every pane replaying it re-executes the source's entire op stream — and a screen's chrome
    // holds many panes: a bar, a rail, the controls sitting on them. On a tablet's notice board
    // that was the whole list's fills and glyphs re-drawn some eight times per frame, three
    // hundred milliseconds of render thread that no single surface could be blamed for.
    // Compositing the source offscreen puts one texture between the op stream and its consumers:
    // the body rasterises once per change, and every pane after that samples a quad.
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
}

/**
 * The pane of glass the controls in this subtree should refract.
 *
 * Chrome provides it around its own action slot, so a `FluidBarAction` an app drops into a top bar
 * bends *that bar* without the app ever having to hold a backdrop or pass one down. Null means there
 * is no live material in scope — a plain page, a dialog in its own window — and controls then draw
 * their rim and their touch response without a backdrop to bend.
 */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdropState?> { null }

/**
 * Il vetro dentro questo nodo sta su [material]: lo campiona sopra a quello che gli e' stato dato,
 * come se questo nodo fosse figlio della superficie che lo disegna. Dalla 2.13.0.
 *
 * Non serve per il caso normale: una superficie di vetro dentro un'altra (`glassSurface`,
 * `glassControlSurface`, `fluidPhysicsSurface`, il pomello di un interruttore) trova da sola il
 * pannello che la contiene. Serve quando il contenuto **non e' figlio** del vetro su cui sta:
 *
 * - una superficie che si trasforma e porta il contenuto in un nodo fratello (la finestra di
 *   Fluid-physics, il menu' che nasce da un tasto): `exports = material` sulla superficie,
 *   `glassContainer(material)` sul contenuto;
 * - un pannello che l'app disegna con un altro motore: [glassMaterialSource] sul nodo che porta
 *   solo il materiale, `glassContainer(material)` sul contenuto.
 *
 * [material] deve essere uno stato di [rememberGlassBackdrop], non uno combinato. Non costa niente
 * finche' dentro non c'e' vetro: il materiale si registra solo quando qualcuno lo campiona.
 */
fun Modifier.glassContainer(material: GlassBackdropState): Modifier {
  val layer = material.layerBackdrop ?: return this
  return this.glassContainerScope(layer)
}

/**
 * Registra in [state] quello che disegnano i modificatori che vengono dopo questo — il materiale di
 * un pannello che l'app disegna da se' — cosi' il vetro dell'engine che ci sta sopra lo puo'
 * campionare (vedi [glassContainer]). Dalla 2.13.0.
 *
 * Va su un nodo che porta **solo** il materiale, mai il contenuto: il contenuto finirebbe nel
 * fondale di cio' che contiene, che e' il feedback ottico. In pratica un fratello sotto al contenuto
 * (i tasti, gli interruttori, le card di vetro che il pannello ospita):
 *
 *     val pane = rememberGlassBackdrop()
 *     Box(modifier) {
 *       Box(Modifier.matchParentSize().glassMaterialSource(pane).liquidGlass(...))
 *       Column(Modifier.glassContainer(pane)) { controls() }
 *     }
 *
 * Diverso da [glassBackdropSource], che registra una *pagina*: questo registra solo finche' del
 * vetro lo campiona, e non passa da un layer offscreen. Un pannello fatto con [glassSurface] non ne
 * ha bisogno: pubblica il proprio materiale da solo.
 */
fun Modifier.glassMaterialSource(state: GlassBackdropState): Modifier {
  val layer = state.layerBackdrop ?: return this
  return this.glassMaterialRecording(layer)
}

/**
 * A backdrop with nothing in it.
 *
 * Not a failure case: a control over ordinary opaque content genuinely has nothing to refract, and
 * this lets it keep its rim, its highlight and its whole gesture response instead of needing a
 * second component to exist for that situation.
 */
@Composable
fun rememberEmptyGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState =
  remember(blurRadius) { GlassBackdropState(emptyBackdrop(), null, blurRadius) }

/** The backdrop a control should use unless its caller names a different one. */
@Composable
fun currentGlassBackdrop(): GlassBackdropState =
  LocalGlassBackdrop.current ?: rememberEmptyGlassBackdrop()

/**
 * The screen's *ambient* recording: the coloured wash and motif painted under the page, and nothing
 * else.
 *
 * This is the twin of [LocalGlassBackdrop], and the reason there have to be two is the only piece of
 * this architecture that cannot be skipped. [LocalGlassBackdrop] holds the screen's whole scrolling
 * body, which is right for chrome standing *over* it and catastrophic for a card standing *inside*
 * it: that card would sample a recording containing itself, and optical feedback is the one artefact
 * in this material that cannot be hidden.
 *
 * A content surface therefore refracts this one — painted and recorded *before* the list, so it can
 * never contain a single pixel of it — while bars, tab bars and modals go on refracting the body.
 *
 * Null means the screen has no ambient canvas. That is not a failure: [dev.antigravity.fluidengine
 * .ui.theme.FluidCard] and friends then draw exactly the opaque surface they always did, so asking
 * for glass on a screen that has nothing to show through it costs nothing and breaks nothing.
 */
val LocalFluidCanvasBackdrop = compositionLocalOf<GlassBackdropState?> { null }

/**
 * Whether the canvas in scope is itself a pane of glass rather than a page's ambient wash.
 *
 * It changes what a content surface should do, and by more than a shade. On a page the material is
 * held down: what is behind is a soft wash, and frosting a wash again buys nothing. Inside a modal
 * the surface is standing on a pane that is already tinted and already softened, so the same light
 * touch would be indistinguishable from no glass at all — which is exactly what it looked like.
 */
val LocalFluidCanvasIsGlass = compositionLocalOf { false }

/**
 * Which side the app is painting on, when the palette cannot be asked.
 *
 * Null, the default, means "work it out from the palette", which is what every app did before this
 * existed and what almost every app should go on doing.
 *
 * It exists because the palette can lie, and it lies in exactly one way. A design where every
 * surface is a translucent *film* puts that film in `colorScheme.surface` — that is the honest
 * place for it — and a film is a white at ten percent alpha. `Color.luminance()` does not look at
 * alpha: it reports 1.0, the luminance of the white, and the test below calls a black app a light
 * one. Every pane of glass then takes the wrong branch at once, and the symptom is a bar that
 * lightens where it should darken, on a page that is already as bright as it gets.
 *
 * The failure is silent and it is total, so it is not something an app should have to discover:
 * an app that knows which side it is on says so here, and the guess is skipped.
 *
 * [dev.antigravity.fluidengine.ui.theme.FluidTheme] provides it already, resolved from the theme
 * mode it was given, so an app that gets its colours from the engine never touches this.
 */
val LocalFluidSurfaceSide = compositionLocalOf<Boolean?> { null }


/** The ambient recording in scope, or null when the screen has no canvas. */
@Composable
fun currentCanvasBackdrop(): GlassBackdropState? = LocalFluidCanvasBackdrop.current

/**
 * Cosa vuol dire [GlassTint.overlay] per il materiale nuovo ([FluidGlassModel.Liquid]). Il vetro di prima
 * ([FluidGlassModel.Classic]) non lo legge: li' `overlay` e' sempre la pellicola dipinta sopra la lente.
 *
 * Il modo che conta e' quello **risolto** ([glassTintLiquidMode]), non sempre quello scritto: `Neutral` vale
 * solo per la pellicola con cui la tinta e' nata. Vedi [Neutral].
 */
enum class GlassTintMode {
  /**
   * La pellicola dipinta com'e': il colore con la sua alfa, sopra cio' che il vetro trasmette, **al posto**
   * del velo del materiale. E' il comportamento di sempre, ed e' il default: una tinta che un'app ha scritto
   * a mano resta quella che ha scritto. Sono `Fixed` anche le tinte dell'engine che sono una pellicola per
   * mestiere (`darkBarTint` e le sue sorelle sopra una foto, `selectionTint`), ogni tinta di serie a cui
   * qualcuno ha cambiato il colore (vedi [Neutral]), e ogni tinta passata da [forContrast] con un contrasto
   * di sistema sopra lo standard: chi chiede piu' contrasto riceve la pellicola densa di prima, non un velo
   * che lascia passare.
   */
  Fixed,

  /**
   * Il velo lo decide il materiale: `overlay` serve solo al vetro di prima. Sono cosi' le tinte di serie di
   * [GlassDefaults] (barra, flottante, controllo, contenuto, modale, pannello).
   *
   * **Vale solo per la pellicola con cui la tinta e' nata.** Un'app che da' un tono a una lastra lo fa cosi':
   * `GlassDefaults.barTint().copy(overlay = unColore)`. Se `Neutral` sopravvivesse a quella `copy`, il
   * materiale nuovo quel colore non lo vedrebbe, e l'app cambierebbe aspetto senza aver toccato una riga. Per
   * questo la tinta ricorda la pellicola per cui e' stata detta neutra (la porta `copy` insieme al resto), e
   * se `overlay` non e' piu' quella si legge [Fixed]: il colore dell'app, dipinto com'e', come oggi. Non
   * conta l'ordine delle chiamate ne' chi le fa: conta solo se il colore e' ancora quello.
   *
   * Cambiare `fallback` o `hairline` non tocca il modo: sono del ripiego e del filetto, che il materiale nuovo
   * disegna come quello di prima.
   *
   * Chi vuole davvero un'altra pellicola **per il vetro di prima** e il velo per il nuovo costruisce la tinta
   * per intero, dicendo il modo: `GlassTint(overlay, fallback, hairline, GlassTintMode.Neutral)`.
   */
  Neutral,

  /**
   * Una tinta alla Apple: `overlay` colora il vetro **sopra** il suo velo, e la sua luminosita' segue cio' che
   * il vetro ha dietro. E' cosi' [GlassDefaults.tintedModalTint].
   */
  Colored,
}

/**
 * Colour treatment of a glass surface.
 *
 * Il costruttore a tre argomenti e' quello di sempre, e una tinta scritta cosi' e' [GlassTintMode.Fixed]: la
 * pellicola dipinta com'e', nel vetro di prima e nel materiale nuovo.
 */
@Immutable
data class GlassTint(
  /**
   * Nel vetro di prima ([FluidGlassModel.Classic]) e' la pellicola dipinta sopra il fondale rifratto: tienila
   * bassa, il materiale deve trasmettere.
   *
   * Nel materiale nuovo ([FluidGlassModel.Liquid]) conta il modo risolto ([glassTintLiquidMode]): `Fixed` la
   * dipinge com'e' al posto del velo del materiale, `Neutral` non la dipinge affatto (il velo lo decide il
   * materiale, e questa serve solo al vetro di prima), `Colored` la posa sopra il velo, con una luminosita' che
   * segue lo sfondo. Vedi [GlassTintMode].
   */
  val overlay: Color,
  /** Used instead of the backdrop on devices without `RenderEffect`. */
  val fallback: Color,
  /** Hairline drawn on the edge that meets the content. */
  val hairline: Color,
  /**
   * Come il materiale nuovo legge [overlay]: vedi [GlassTintMode]. In coda e con un default, perche' il
   * costruttore a tre argomenti resti quello di sempre (4.0.0). E' il modo **detto**; quello con cui il vetro
   * si disegna lo da' [glassTintLiquidMode].
   */
  val mode: GlassTintMode = GlassTintMode.Fixed,
  /**
   * La pellicola per cui [mode] e' stato detto: di serie [overlay] stesso, cioe' quella con cui la tinta nasce.
   *
   * Sta nel costruttore primario perche' `copy` la porti con se': `copy(overlay = x)` cambia la pellicola e
   * lascia questa com'era, ed e' cosi' che il materiale nuovo sa che una tinta di serie ha cambiato colore
   * (vedi [GlassTintMode.Neutral]). Interna: e' la memoria della regola, non una cosa da impostare.
   */
  internal val modeOverlay: Color = overlay,
)

/**
 * Il modo con cui il materiale nuovo disegna davvero [tint]: quello detto, tranne una tinta `Neutral` il cui
 * `overlay` non e' piu' la pellicola con cui e' nata, che si legge `Fixed` (vedi [GlassTintMode.Neutral]).
 *
 * Pura e senza Compose: e' la regola che `GlassTintModeTest` prova caso per caso. `Fixed` e `Colored` restano
 * come sono, perche' il loro colore si vede gia'.
 */
internal fun glassTintLiquidMode(tint: GlassTint): GlassTintMode =
  if (tint.mode == GlassTintMode.Neutral && tint.overlay != tint.modeOverlay) GlassTintMode.Fixed else tint.mode

/** The optical job a pane performs; colour remains the separate concern of [GlassTint]. */
enum class GlassRole {
  /** Edge-to-edge chrome whose material should stay quiet behind navigation and status controls. */
  Bar,

  /** A detached navigation capsule or rail with a clearly visible silhouette. */
  Floating,

  /** A control that yields under a finger and therefore carries the strongest lens response. */
  Interactive,

  /** A sheet or alert surface: deeper than a bar, calmer than a control. */
  Modal,

  /**
   * A card or a grouped list standing *in* the page rather than over it.
   *
   * The one role that carries no drop shadow at all, and the reason is not restraint: content glass
   * is at the same depth as the text beside it. A shadow would say it floats, and the moment two
   * cards in a scrolling list both say that, the page reads as a stack of loose slabs. What it keeps
   * is a short, sharp bevel — an edge, not a dome — because at this size the edge is the whole tell.
   */
  Content,
}

/**
 * How thick the glass is, physically.
 *
 * Every distance is in `Dp` and describes the *edge*, because that is where glass does its work:
 * [refractionHeight] is how far in from the perimeter the bending is still felt, and
 * [refractionAmount] is how far the backdrop is dragged along the surface normal at the very edge.
 * A tall height with a small amount is a gentle dome; a short height with a large amount is a sharp
 * bevel. Apple's controls sit near height == amount.
 *
 * Cosi' la legge il vetro di prima ([FluidGlassModel.Classic]). Il materiale nuovo
 * ([FluidGlassModel.Liquid]) prende smerigliatura, lente, bordo e ombra dal proprio profilo, e di
 * un'ottica legge alcuni campi come attenuatori (sotto il valore piu' basso fra i ruoli di serie ne
 * resta la frazione, sopra non c'e' di piu'), altri come oggi, altri per niente: vedi `glassRecipe`.
 */
@Immutable
data class GlassOptics(
  /** Multiplies [GlassBackdropState.blurRadius]. Interactive glass is nearly clear. */
  val blurScale: Float,
  val refractionHeight: Dp,
  val refractionAmount: Dp,
  /**
   * Adds a centre-directed term to the surface normal, turning a bevelled edge into a whole dome.
   * Right for a small control the eye reads as a lens; wrong for a wide bar, where it would make the
   * middle of the screen swim.
   */
  val depthEffect: Boolean,
  /**
   * Splits the refracted sample into seven wavelengths. Only ever correct on a small, thick,
   * moving surface: on a large one it costs seven taps per pixel and reads as a colour fringe.
   */
  val dispersion: Boolean,
  /** Saturation multiplier applied to the transmitted image. 1 = off. */
  val vibrancy: Float,
  /** Specular ring drawn along the perimeter. */
  val highlightWidth: Dp,
  val highlightAlpha: Float,
  /** Degrees, clockwise from the positive x axis: where the light is coming from. */
  val highlightAngle: Float,
  /** Inner shadow: the pane's own thickness, seen from inside. */
  val innerShadowRadius: Dp,
  val innerShadowAlpha: Float,
  /** Drop shadow: how far off the page the pane sits. */
  val shadowRadius: Dp,
  val shadowAlpha: Float,
  /** Extra displacement while a finger is down, as a fraction of [refractionAmount]. */
  val pressedDepthBoost: Float,
  /**
   * How much of the specular rim and the drop shadow survive at zero optical depth.
   *
   * A surface whose depth is driven by a press — a tab bar's indicator, most obviously — has its
   * rim and its shadow multiplied down to *nothing* while nobody is touching it. That is right for
   * the bend, which is what a press is asking for, and wrong for the edge: with no rim the
   * indicator's shape comes entirely from its own wash, so it disappears into the bar the moment
   * the bar is sitting over something bright. A specular rim reads against light and dark alike,
   * which is the whole reason a rim is drawn rather than a lighter fill.
   *
   * Zero, the default, is the behaviour every existing surface has. A fraction floors the rim and
   * the shadow there and lets the press take them the rest of the way.
   */
  val edgeFloor: Float = 0f,
  /**
   * Fraction of its own resolution this surface processes its backdrop at, **on top of** the
   * blur-driven scale every pane already gets (see `glassResolutionScale`).
   *
   * The blur-driven scale answers "how much can the frosting hide": a nearly clear pane keeps
   * nearly full resolution, because its lens edge is sharp. This one answers a different question —
   * "how much detail does the *backdrop* have to lose": a content pane stands over an ambient wash,
   * which is three radial gradients and a faded motif, and a gradient downsampled to half comes
   * back up identical. What that buys is the whole page: the effect chain is charged per pixel,
   * and a grades list on a tablet holds four megapixels of content glass — at full resolution that
   * chain alone is the difference between a scroll and a slideshow.
   *
   * Keep it at 1 for anything that stands over *content* — a bar over scrolling text at half
   * resolution reads as smeared, which is exactly the artefact the blur-driven scale exists to
   * avoid.
   */
  val backdropResolution: Float = 1f,
  /**
   * Quanto la regola della taglia si applica a questa ottica, da 0 (mai) a 1 (per intero).
   *
   * Un pannello grande smerigliato come uno piccolo sembra un pannello piccolo ingrandito: la
   * sfocatura e la rifrazione al bordo crescono col lato corto, vedi [glassSizeGain]. Questo e'
   * il peso di quella crescita su *questa* ottica, e 1, il default, e' quello che hanno tutti i
   * ruoli.
   *
   * Si mette a 0 per una superficie che e' grande per ragioni che non c'entrano col vetro: lo scrim
   * di un modale copre lo schermo intero, e se la regola lo trattasse da pannello gigante lo
   * sfocherebbe fino a una media — che e' esattamente cio' che lo scrim ha il compito di non fare.
   * Aggiunto dopo la 2.11.0; con il default nessuna ottica scritta a mano cambia struttura.
   */
  val sizeResponse: Float = 1f,
)

/**
 * How much of the edge a surface is showing, given how deep its lens currently is.
 *
 * Out here with the rest of the arithmetic because it is the one line that decides whether an
 * indicator has an edge at rest, and it is easier to be sure of it in a test than on a screen.
 */
internal fun glassEdgeDepth(floor: Float, depth: Float): Float {
  val f = clampGlassUnit(floor)
  return f + (1f - f) * clampGlassUnit(depth)
}

internal fun clampGlassUnit(value: Float): Float =
  if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

/**
 * Il bordo speculare di una lastra: colore e modo di comporre, per lato.
 *
 * **Uno solo.** Lo disegnano due strade — il renderer di `glassSurface` e il tratto di path di
 * `fluidPhysicsSurface` — e finche' ognuna aveva la sua ricetta la stessa lastra, ferma o in viaggio,
 * aveva due bordi: quello fisico era un gradiente bianco in entrambi i temi, e sul lato chiaro
 * bianco su bianco non si vede. Chi disegna un bordo di vetro chiede qui il colore.
 *
 * Il lato scuro e' luce: bianco, in `Plus`, perche' la luce si somma. Il lato chiaro non ha luce da
 * sommare, e il giro e' fatto di due meta' insieme — un bordo scuro e un modo ordinario di posarlo:
 * girare il solo colore compila, gira, e non disegna niente. Piu' quieto del gemello bianco, perche'
 * una riga scura su carta si legge a un contrasto piu' basso di una chiara al buio.
 */
internal data class GlassRimRecipe(val color: Color, val blendMode: BlendMode)

internal fun glassRimRecipe(darkSide: Boolean): GlassRimRecipe =
  if (darkSide) {
    GlassRimRecipe(Color.White.copy(alpha = 0.5f), BlendMode.Plus)
  } else {
    GlassRimRecipe(Color.Black.copy(alpha = 0.28f), BlendMode.SrcOver)
  }

/**
 * Il colore dell'ombra esterna di una lastra, per lato: nero al 16% sul chiaro, al 10% sullo scuro.
 *
 * Sul chiaro la pellicola non ha margine (la pagina e' gia' quasi bianca), quindi ombra e filetto
 * sono tutto cio' che dice che una superficie galleggia; sullo scuro un'ombra nera su un fondo nero
 * non fa niente e la alza la pellicola. Il peso del ruolo ([GlassOptics.shadowAlpha]) si moltiplica
 * a questo, ed e' lo stesso conto per `glassSurface`, per l'ombra dei pannelli in viaggio
 * (`fluidPhysicsShadow`) e per quella sotto la barra flottante.
 */
internal fun glassShadowColor(darkSide: Boolean): Color =
  Color.Black.copy(alpha = if (darkSide) 0.10f else 0.16f)

/**
 * Di quanto lo shader del bordo allarga l'arco dell'angolo per leggere la normale:
 * `gradRadius = min(raggio * 1,5, mezzo lato corto)` (`glass/backdrop/internal/Shaders.kt`).
 */
internal const val GlassRimCornerGain = 1.5f

/**
 * La rampa del bordo, in frazione dell'altezza, per un angolo di raggio [cornerRadiusPx] su una
 * sagoma di [widthPx] x [heightPx].
 *
 * Il bordo di `glassSurface` e' `|normale . (0, 1)|`: pieno dove la sagoma guarda in su o in giu',
 * zero sui fianchi. Lungo un fianco la normale gira solo dentro l'arco allargato di `gradRadius`, e li'
 * l'intensita' scende da 1 a 0 quasi linearmente con la quota: e' questa lunghezza, vista dall'alto
 * (o dal basso), la rampa. Una capsula la porta a mezza altezza (zero a meta' esatta); un angolo
 * piccolo su un pannello alto la tiene corta, e i fianchi restano spenti. Il tratto di path del
 * bordo fisico non puo' chiedere la normale a ogni pixel: ne disegna questo profilo, con un
 * gradiente verticale.
 */
internal fun glassRimRamp(cornerRadiusPx: Float, widthPx: Float, heightPx: Float): Float {
  if (!(heightPx > 0f) || !(widthPx > 0f)) return 0f
  val gradRadius = minOf(
    cornerRadiusPx.coerceAtLeast(0f) * GlassRimCornerGain,
    minOf(widthPx, heightPx) / 2f,
  )
  return (gradRadius / heightPx).coerceIn(0f, 0.5f)
}

/**
 * L'intensita' del bordo (0..1) a [position] dell'altezza (0 sopra, 1 sotto), data la rampa di sopra
 * e quella di sotto: 1 al bordo, zero oltre la rampa, e lineare in mezzo.
 *
 * E' anche la tabella del gradiente fisico: le sue fermate sono esattamente (0, 1), (rampa, 0),
 * (1 - rampa, 0), (1, 1).
 */
internal fun glassRimIntensity(position: Float, topRamp: Float, bottomRamp: Float): Float {
  val p = position.coerceIn(0f, 1f)
  return when {
    topRamp > 0f && p < topRamp -> 1f - p / topRamp
    bottomRamp > 0f && p > 1f - bottomRamp -> 1f - (1f - p) / bottomRamp
    else -> 0f
  }
}

private fun finiteNonNegative(value: Float): Float =
  if (value.isFinite()) value.coerceAtLeast(0f) else 0f

/**
 * Clamps a hand-written [GlassOptics] into the range the renderer can honour.
 *
 * A negative refraction height makes the lens shader sample outside its own input and a NaN alpha
 * poisons a whole `RenderEffect` chain, so malformed values are corrected rather than propagated.
 */
internal fun GlassOptics.sanitized(): GlassOptics = copy(
  // The ceiling is a multiple of [DefaultGlassBlurRadius], and it had to move when that did. At 4
  // it was a sane cap on a 16 dp reference — 64 dp of frosting, past which nothing gets any more
  // hidden and every pixel still costs. With the reference down at 2 dp the same 4 silently halved
  // the top bar, which is the one surface that genuinely needs a wide kernel: the cap was quietly
  // deciding a design question instead of catching a malformed value, which is its only job.
  blurScale = finiteNonNegative(blurScale).coerceAtMost(GlassMaxBlurScale),
  refractionHeight = finiteNonNegative(refractionHeight.value).dp,
  refractionAmount = finiteNonNegative(refractionAmount.value).dp,
  vibrancy = if (vibrancy.isFinite()) vibrancy.coerceIn(0f, 3f) else 1f,
  highlightWidth = finiteNonNegative(highlightWidth.value).dp,
  highlightAlpha = clampGlassUnit(highlightAlpha),
  highlightAngle = if (highlightAngle.isFinite()) highlightAngle else 45f,
  innerShadowRadius = finiteNonNegative(innerShadowRadius.value).dp,
  innerShadowAlpha = clampGlassUnit(innerShadowAlpha),
  shadowRadius = finiteNonNegative(shadowRadius.value).dp,
  shadowAlpha = clampGlassUnit(shadowAlpha),
  pressedDepthBoost = if (pressedDepthBoost.isFinite()) {
    pressedDepthBoost.coerceIn(0f, 2f)
  } else {
    0f
  },
  edgeFloor = clampGlassUnit(edgeFloor),
  backdropResolution = if (backdropResolution.isFinite()) {
    backdropResolution.coerceIn(0.1f, 1f)
  } else {
    1f
  },
  // Un valore malformato vale "nessuna regola", non "regola intera": la direzione sicura e' quella
  // in cui il pannello si comporta come prima che la regola esistesse.
  sizeResponse = clampGlassUnit(sizeResponse),
)

object GlassDefaults {

  /**
   * Chrome.
   *
   * The lens reaches a long way in but displaces gently: a bar is a wide, shallow sheet, and a
   * strong displacement across that width would make the whole top of the screen wobble while a list
   * moves under it. No drop shadow — the bar is flush with the screen, not floating over it.
   */
  private val BarOptics = GlassOptics(
    // The one surface that keeps a heavy kernel, and it earns it: a top bar is the only pane with
    // *sharp text scrolling underneath it*, arriving from a page it does not control. Everything
    // else in this file stands over something soft — a wash, a bar's own fill, a control's track —
    // and can afford to be nearly clear. This cannot, and going clear here was a mistake that made
    // the top of every screen look like it was leaking.
    //
    // Ten dp, not sixteen. Sixteen did stop the leak, and it also stopped the bar being glass: past
    // roughly five times the base radius the page underneath is averaged to a single colour, and a
    // pane with a flat colour behind it has nothing left to refract. What separates the bar from
    // the page is then the tint, which a painted rectangle would have done for nothing.
    blurScale = 5f,
    refractionHeight = 20.dp,
    refractionAmount = 16.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.5f,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 0.dp,
    shadowAlpha = 0f,
    pressedDepthBoost = 0f,
  )

  /**
   * A floating capsule: navigation that content passes beneath.
   *
   * This is the surface people look at longest, so it carries the full treatment — a visible bevel,
   * a lit rim, thickness on the inside and a shadow that separates it from whatever scrolls under.
   */
  private val FloatingOptics = GlassOptics(
    // Light, but not nothing: a floating capsule travels over arbitrary content, and its labels are
    // eight pixels tall. Enough to stop a saturated photograph from eating the word "Impostazioni",
    // and far short of the radius that would turn the bar back into a fill.
    blurScale = 1.8f,
    // 19 and 29 dp: the numbers Kyant's own Apple-matched capsule uses, and the ones Square
    // converges on independently. The amount running *past* the height is what makes the edge read
    // as a thick bevel rather than a soft dome — the reverse, which this had, reads as plastic.
    refractionHeight = 19.dp,
    refractionAmount = 29.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.6f,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 20.dp,
    shadowAlpha = 0.7f,
    pressedDepthBoost = 0.35f,
  )

  /**
   * A control.
   *
   * Barely frosted on purpose. A button this size cannot afford to hide what is behind it and still
   * look like glass: it is the *bend* at its edge that identifies the material, so the blur comes
   * almost all the way down and the lens comes all the way up. Dispersion is on — this is the one
   * place small enough for seven samples per pixel to be worth what it costs.
   */
  private val InteractiveOptics = GlassOptics(
    // A control is a lens, and a lens is nearly clear — but a *label* on a lens still has to be
    // read against whatever the lens happens to be standing over. This is the least frosting that
    // survives a button parked over a photograph. Chi non porta testo lo riporta a zero al sito di
    // chiamata: oggi sono l'indicatore del rail (`FluidTabBar.kt`) e la pillola del segmentato
    // (`FluidControls.kt`). I pomelli di slider e interruttore e la pillola della tab bar flottante
    // non passano di qui affatto: chiamano `drawBackdrop` con una ricetta loro, la goccia
    // (`glass/interaction/GlassDrop.kt`), e queste ottiche non le leggono mai.
    blurScale = 1.4f,
    refractionHeight = 10.dp,
    // Held to well under the radius of the smallest control the system has. A 44 dp lens displacing
    // 24 dp drags in whatever happens to be a centimetre away and reads as a coloured halo rather
    // than as an edge.
    refractionAmount = 14.dp,
    // A dome, on the one kind of surface small enough for the eye to read the whole thing as a
    // single lens. Wrong for anything wider — see the note on [GlassOptics.depthEffect] — and wrong
    // even here below about 40 dp: la lente del pomello dell'interruttore e' infatti senza cupola,
    // perche' la sua ricetta (la goccia) non l'ha mai accesa.
    depthEffect = true,
    dispersion = true,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.55f,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 12.dp,
    shadowAlpha = 0.5f,
    pressedDepthBoost = 0.6f,
  )

  /**
   * Un pulsante di vetro (3.0): [InteractiveOptics] con la lente di LibrePods **sotto il dito**.
   *
   * La lente di `StyledButton` di LibrePods (GPL-3.0, `StyledButton.kt:223-228`, branch
   * `android/rewrite`, commit 3d4d270) e' `lens(refractionHeight = 12 dp, refractionAmount = 24 dp,
   * depthEffect = true, chromaticAberration = true)`, sempre accesa. Qui a riposo il pulsante tiene
   * la lente di sempre — 10 dp di altezza e 14 di spostamento, che a 24 "tira dentro tutto quello che
   * e' a un centimetro e si legge come un alone colorato" — e **premuto** arriva a quella di
   * LibrePods: 12 dp di altezza, e uno spostamento che sale da 14 a 24 dp con la pressione
   * (`pressedDepthBoost` = 24 / 14 - 1). La profondita' e la dispersione c'erano gia'.
   *
   * Solo `glassControlSurface` la usa di serie: `GlassRole.Interactive` e' rimasto com'era per tutto
   * il resto (il puck della barra, i pomelli, le lenti dei pop-up).
   */
  private val ButtonOptics = InteractiveOptics.copy(
    refractionHeight = 12.dp,
    pressedDepthBoost = 24f / InteractiveOptics.refractionAmount.value - 1f,
  )

  /** L'ottica di un pulsante di vetro: vedi [ButtonOptics]. */
  fun buttonOptics(): GlassOptics = ButtonOptics

  /**
   * A sheet or an alert.
   *
   * Deep frosting, because a sheet has to win an argument with a whole page of content behind it,
   * and a wide bevel that makes its top edge read as a thick slab rather than a cut.
   */
  private val ModalOptics = GlassOptics(
    // Heavy, because the things wearing this preset — an in-app notification, an alert — arrive
    // over a page that is still fully sharp and are the only thing separating themselves from it.
    // A modal that comes with a scrim does not need it and says so: the pop-up's own optics turn
    // this down, because a second heavy blur on top of a blurred scrim is what turns a pane into a
    // grey rectangle with no material left in it.
    // Which is why it is three and a half and not six: at six the page behind an alert is a colour
    // rather than a picture, and the alert stops looking like it is *in front of* anything at all.
    blurScale = 3.5f,
    refractionHeight = 20.dp,
    refractionAmount = 28.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.6f,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 32.dp,
    shadowAlpha = 0.8f,
    pressedDepthBoost = 0f,
  )

  /**
   * A card or a grouped list, in the page.
   *
   * The frosting is heavier than a bar's because what is behind this is a coloured wash and the text
   * printed on top has to win against it — legibility here is bought with radius, as everywhere else
   * in this file. The lens is the opposite: short reach, modest displacement, no dome. A dome on a
   * surface that is 300 dp wide and full of text makes the middle of the paragraph swim.
   *
   * Nothing outside the pane at all: no drop shadow, and only enough inner shadow to give the edge a
   * thickness. See [GlassRole.Content].
   */
  private val ContentOptics = GlassOptics(
    // What is behind a content card is an ambient wash — already soft, with nothing sharp in it to
    // hide. Frosting it again buys nothing and costs the only thing worth having: the wash's own
    // gradient bending into the card's perimeter.
    // So: barely.
    blurScale = 0.8f,
    refractionHeight = 18.dp,
    refractionAmount = 22.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.5f,
    highlightAngle = 90f,
    // **Nessuna ombra interna, e vale per tutte le superfici che portano un bordo speculare.**
    //
    // Il rim lucido e l'ombra interna sono due trattamenti del *medesimo* millimetro di perimetro, e
    // messi insieme non si leggono come spessore: si leggono come **due bordi**. Una riga chiara,
    // subito dentro una riga piu' scura, tutt'intorno alla card. Su un fondo chiaro e' la prima cosa
    // che si vede di ogni pannello dell'app, ed e' anche l'unica cosa che nessuno ha mai chiesto.
    //
    // Lo spessore lo dice gia' la dislocazione: il bordo di questo materiale *piega* quello che ha
    // dietro, che e' un'informazione di profondita' molto piu' forte di una banda scura, e costa
    // zero in piu' perche' la lente c'e' comunque.
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 0.dp,
    shadowAlpha = 0f,
    pressedDepthBoost = 0f,
    // Half, and it is the one preset that can afford it: what a content pane refracts is the
    // ambient wash, and a radial gradient survives a downsample untouched. This is what keeps a
    // tablet's grades page — megapixels of grouped-list glass — scrolling instead of slideshowing.
    backdropResolution = 0.5f,
  )

  /**
   * Content standing on **another pane of glass**, not on a page.
   *
   * A card inside a modal used to give up and go opaque, and opaque is the wrong answer twice over:
   * it puts a solid rectangle inside a translucent pane, and it throws away the one place in the
   * system where this material can legitimately be *stacked*.
   *
   * Stacked, every number goes up rather than down. What is behind is already tinted and already
   * softened, so a light touch reads as nothing at all: the frosting doubles, the bevel reaches
   * further, the tint is denser, the rim is brighter. What does not change is that it stays
   * transparent — the pane's own colour keeps coming through, which is the entire difference
   * between glass on glass and a card on glass.
   */
  private val StackedContentOptics = GlassOptics(
    blurScale = 1.8f,
    refractionHeight = 14.dp,
    refractionAmount = 18.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    highlightAlpha = 0.6f,
    highlightAngle = 90f,
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    shadowRadius = 0.dp,
    shadowAlpha = 0f,
    pressedDepthBoost = 0f,
    backdropResolution = 0.6f,
  )

  /** The optics a content surface takes when its canvas is another pane. See [LocalFluidCanvasIsGlass]. */
  fun stackedContentOptics(): GlassOptics = StackedContentOptics

  /**
   * Il pannello flottante (3.0): popover, menu contestuale, finestra che si espande, foglio,
   * notifica, callout, menu che si trasforma. **Un solo materiale**, non uno per componente.
   *
   * Ognuno di questi aveva i suoi numeri: la finestra che si espande sfocava 2,2 con un bordo da
   * 1,1 dp, il popover 2,5 con uno da 0,8, la notifica 3,5 con la lente di un `Modal`, il callout lo
   * stesso. Messi uno accanto all'altro sullo schermo sono cinque vetri diversi per la stessa
   * famiglia di cose. Sono qui, e un pannello nuovo chiede questi.
   *
   * Il punto di partenza e' quello che il popover aveva gia' dopo i suoi giri di taratura, e i
   * commenti sotto sono la loro storia. La regola della taglia ([glassSizedOptics]) si applica
   * sopra, come a ogni lastra: un menu piccolo e uno largo mezzo schermo escono dalla stessa ricetta
   * con la sfocatura e la lente che la loro taglia chiede.
   *
   * Vetro vero, con la lente accesa. Un pannello che e' solo traslucido e' il difetto che questo
   * file esiste per evitare: ora che lo scrim sfoca appena la pagina, una pellicola piatta la
   * mostrerebbe dritta, senza piegarla, e si leggerebbe come un rettangolo semitrasparente e non
   * come un pannello. La sfocatura resta modesta e a identificare e' la *dislocazione*: la pagina
   * che si piega visibilmente nel perimetro del pannello e' tutto cio' che dice che li' c'e' uno
   * spessore.
   */
  private val PanelOptics = GlassOptics(
    // The one surface here that *is* frosted, and the division of labour is deliberate: the scrim
    // barely touches the page, the pop-up frosts hard. That is the way round Apple does it — the page
    // stays itself and merely dims, and the thing in front of it is the pane you cannot read through.
    // The reverse (a heavy scrim under a clear pop-up) hides the page and gains nothing.
    //
    // This was twelve dp, on top of a scrim that had already blurred the same pixels, and the result
    // was the complaint that started this pass: a pop-up you cannot see anything through is not
    // glass, it is a light grey card, and every one of the expensive things below — the bevel, the
    // rim, the vibrancy — was being spent describing the edge of a blank. The material is the
    // *bend*; frosting is only what keeps the text on top readable, and five dp over an
    // already-dimmed page is enough for that.
    blurScale = PanelBlurScale,
    // And the bevel comes in to match. Twenty-eight dp of displacement on a menu 210 dp wide meant a
    // quarter of the pane was edge: what should read as a thick slab read as a fisheye, with the page
    // smeared round the outside of it in a coloured halo.
    refractionHeight = 14.dp,
    refractionAmount = 18.dp,
    depthEffect = false,
    dispersion = false,
    vibrancy = 1.6f,
    highlightWidth = 0.8.dp,
    // Bright enough to find the edge, dim enough that the pane is not outlined in white. At 0.7 the
    // rim was the loudest thing on the screen and the pop-up read as a sticker.
    highlightAlpha = 0.45f,
    highlightAngle = 90f,
    // Nessuna ombra interna: il rim speculare e l'ombra interna sono due trattamenti dello stesso
    // millimetro di perimetro, e insieme si leggono come due bordi invece che come spessore.
    innerShadowRadius = 0.dp,
    innerShadowAlpha = 0f,
    // Tight and soft. A wide dark shadow over a scrim that is already darkening the page reads as
    // grime around the pop-up rather than as height.
    shadowRadius = 18.dp,
    shadowAlpha = 0.36f,
    pressedDepthBoost = 0f,
  )

  /** La sfocatura di base del pannello: vedi [PanelOptics]. */
  private const val PanelBlurScale = 2.5f

  /**
   * La sfocatura di un pannello che arriva sopra una pagina ancora nitida, senza scrim.
   *
   * Una notifica e un callout non hanno lo scrim che sfoca la pagina prima di loro: il testo che sta
   * sotto passa nitido, e il pannello deve nasconderlo da solo. Alza solo la sfocatura, da 2,5 a 3,5
   * — lo stesso numero che il ruolo `Modal` aveva per loro, e per la ragione data in [ModalOptics]:
   * oltre, la pagina dietro diventa un colore e il pannello smette di stare *davanti* a qualcosa.
   */
  private const val PanelBlurScaleOverSharpPage = 3.5f

  /**
   * La lente di un pannello piccolo.
   *
   * La lente e' una misura assoluta, la superficie no: diciotto dp di spostamento su una finestra
   * larga sono un bordo, sugli stessi diciotto dp di un menu contestuale — due righe, poco piu' di
   * duecento dp di lato — sono un terzo del pannello. E quando la lente e' cosi' grande rispetto a
   * cio' che sta piegando, l'immagine ripiegata sul bordo si stacca da quella dritta al centro: si
   * vedono **due versioni** dello stesso oggetto dentro lo stesso vetro, una schiacciata al margine e
   * una al suo posto. Non e' un difetto della rifrazione, e' la rifrazione applicata a una taglia per
   * cui non era tarata.
   *
   * Era gia' successo una volta — la nota su [PanelOptics] racconta i ventotto dp diventati diciotto
   * — ma la correzione era stata fatta guardando la finestra grande.
   */
  private val CompactPanelOptics = PanelOptics.copy(
    refractionHeight = 7.dp,
    refractionAmount = 9.dp,
  )
  private val SharpPagePanelOptics = PanelOptics.copy(blurScale = PanelBlurScaleOverSharpPage)

  /**
   * Compatto *e* su una pagina nitida: la sfocatura sale, ma non a 3,5.
   *
   * La lente compatta alta 7 dp copre al massimo 7 dp di sfocatura (2,8 volte il riferimento di
   * 2,5 dp), e il padding della cattura vale `max(0, sfocatura - altezza)`: oltre, una superficie di
   * Fluid-physics avrebbe un padding che si muove con la taglia, e sporcherebbe la cattura a ogni
   * fotogramma del viaggio. Nessun pannello oggi e' le due cose insieme; se ne arriva uno, qui non
   * trova la trappola.
   */
  private val CompactSharpPagePanelOptics = CompactPanelOptics.copy(blurScale = 2.8f)

  /**
   * L'ottica del pannello flottante: vedi [PanelOptics].
   *
   * @param compact un pannello piccolo (un menu di poche righe): lente 7/9 dp invece di 14/18.
   * @param overSharpPage il pannello sta sopra una pagina nitida, senza scrim: solo la sfocatura sale
   *   (da 2,5 a 3,5; da 2,5 a 2,8 se e' anche [compact], vedi [CompactSharpPagePanelOptics]).
   */
  fun panelOptics(compact: Boolean = false, overSharpPage: Boolean = false): GlassOptics = when {
    compact && overSharpPage -> CompactSharpPagePanelOptics
    compact -> CompactPanelOptics
    overSharpPage -> SharpPagePanelOptics
    else -> PanelOptics
  }

  /**
   * La pellicola del pannello flottante: quella di un modale, o quella tinta col [colour] della cosa
   * da cui il pannello e' nato (vedi [tintedModalTint]). Una sola, per tutti i pannelli di
   * [panelOptics].
   */
  @Composable
  fun panelTint(colour: Color? = null): GlassTint =
    if (colour == null) modalTint() else tintedModalTint(colour)

  /**
   * The tint for the same case: denser than a page card's, because it is competing with a pane that
   * already carries a film of its own, and text has to stay readable on top of both.
   */
  @Composable
  fun stackedContentTint(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = glassFilm().copy(alpha = if (dark) 0.30f else 0.34f),
      fallback = MaterialTheme.colorScheme.surfaceContainerLow,
      hairline = Color.Transparent,
      mode = GlassTintMode.Neutral,
    ).forSystemContrast()
  }

  /** Stable, allocation-free presets. Custom callers can use `copy` and are sanitized at use. */
  fun optics(role: GlassRole): GlassOptics = when (role) {
    GlassRole.Bar -> BarOptics
    GlassRole.Floating -> FloatingOptics
    GlassRole.Interactive -> InteractiveOptics
    GlassRole.Modal -> ModalOptics
    GlassRole.Content -> ContentOptics
  }

  /**
   * The colour every pane of glass is made of.
   *
   * **Not `MaterialTheme.surface`.** That is the mistake this replaces, and it is the one that made
   * the navigation pill disappear: matching the film to the page's own background means a bar over
   * an AMOLED-black page is a black bar, and a black bar over black content is not a translucent
   * material, it is nothing. Apple's Liquid Glass is a *bright, reflective* material — in dark mode
   * it reads as a distinctly lighter frosted panel, never as a darker rectangle.
   *
   * So the film starts from a fixed pair of glass greys, near-white on a light page and a mid grey
   * on a dark one, and only then takes a third of the palette's own container colour so it still
   * belongs to the app's accent and to Material You. A third is enough to be felt and not enough to
   * let an AMOLED theme drag it back down to the background it is supposed to stand out from.
   *
   * The greys are Square's (github.com/Lelonio/Square), which arrived at them by hand against the
   * same renderer.
   */
  @Composable
  internal fun glassFilm(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (isDarkSurface()) {
      androidx.compose.ui.graphics.lerp(DarkGlassGrey, scheme.surfaceContainerHigh, PaletteShare)
    } else {
      androidx.compose.ui.graphics.lerp(LightGlassGrey, scheme.surfaceContainerLowest, PaletteShare)
    }
  }

  private val LightGlassGrey = Color(0xFFFAFAFA)
  private val DarkGlassGrey = Color(0xFF4A4A4E)

  /** How much of the app's own palette the film is allowed to take. See [glassFilm]. */
  private const val PaletteShare = 0.34f

  /**
   * Bars.
   *
   * Denser than it looks, and it has to be: with the frosting down at 2.5 dp the film is now doing
   * all of the separating on its own. The old value tried the opposite trade — a very light film over a
   * very wide blur — and produced a bar that hid its background completely while looking like
   * nothing in particular.
   */
  @Composable
  fun barTint(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = glassFilm().copy(alpha = if (dark) 0.40f else 0.44f),
      fallback = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.95f else 0.96f),
      hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.16f else 0.10f),
      mode = GlassTintMode.Neutral,
    ).forSystemContrast()
  }

  /**
   * The bar, when the page beneath it is a picture rather than a colour.
   *
   * The dark twin of [barTint], and it exists because the family's film is a
   * *bright* material by design: even its dark branch lightens, to a mid grey,
   * because that is what makes a bar read as frosted glass over a page. Over a
   * full-bleed cover it does the opposite of its job — it raises the floor
   * exactly where the controls have to stay legible, and the bar ends up the
   * brightest thing on the screen.
   *
   * The colours are fixed rather than derived, and that is deliberate twice
   * over: this is a scrim, and a scrim's whole trade is to darken whatever is
   * behind it, so there is no palette to take it from; and deriving it would
   * put it back through the same light/dark detection that a translucent
   * `surface` colour can already fool.
   */
  @Composable
  fun darkBarTint(): GlassTint = GlassTint(
    overlay = Color.Black.copy(alpha = 0.42f),
    fallback = Color(0xFF101012).copy(alpha = 0.95f),
    hairline = Color.White.copy(alpha = 0.12f),
  ).forSystemContrast()

  /**
   * Floating navigation, when what it floats over is a picture.
   *
   * The same argument as [darkBarTint], one step denser, because a floating control has content
   * passing on every side of it rather than only underneath. A pill of the family's bright film,
   * sitting on a full-bleed cover, is the brightest object on the screen and the cover is what it
   * is supposed to be letting through.
   *
   * Use it for the whole family at once or not at all. Its reason for existing is that a bar, the
   * pill it carries and a window growing out of that pill have to be the same material — and an app
   * that darkens one of the three has just built the join it was trying to hide.
   */
  @Composable
  fun darkFloatingTint(): GlassTint = GlassTint(
    overlay = Color.Black.copy(alpha = 0.48f),
    fallback = Color(0xFF141416).copy(alpha = 0.95f),
    hairline = Color.White.copy(alpha = 0.16f),
  ).forSystemContrast()

  /**
   * The bar over a picture, on the side the page is actually on.
   *
   * [darkBarTint] existed alone for a long time and that was the whole fault. It is documented as
   * "the bar, when the page beneath it is a picture rather than a colour" — and a page can be a
   * picture on either side. An app on the light side asking for the right tint over a cover got the
   * *family* tint back, which is built for a flat page, and the two ways that fails are both bad:
   *
   * - over a bright cover the film lands within a percent or two of the page and the bar disappears;
   * - over a dark cover it lands at a mid grey with dark ink on top, which is the one that makes a
   *   library unreadable.
   *
   * Prefer this to naming either arm by hand. An app that darkens the bar and leaves the pill has
   * built the join it was trying to hide — see [darkFloatingTint].
   */
  @Composable
  fun barTintOnPhoto(): GlassTint = if (isDarkSurface()) darkBarTint() else lightBarTint()

  /** [barTintOnPhoto] for the floating family. Use the pair together or neither. */
  @Composable
  fun floatingTintOnPhoto(): GlassTint =
    if (isDarkSurface()) darkFloatingTint() else lightFloatingTint()

  /**
   * The light twin of [darkBarTint]: a white scrim, at the weight a photograph asks for.
   *
   * Denser than a mirror of its dark twin would be, and the ratio is not invented here — it is the
   * one a veil over a cover already uses. Dark ink needs more of a picture taken away than light ink
   * does, because the bright parts of a photograph are far brighter than any dark wash lets them be,
   * and it is the bright parts that swallow dark letters. Black at 0.42 answers as white at 0.56.
   *
   * The hairline turns over with it, and it is the half that matters most here: over a *bright*
   * cover the overlay has nothing left to do — the page is already near paper — so the line is the
   * only thing saying a bar is there at all.
   *
   * Fixed rather than derived, for [darkBarTint]'s two reasons: a scrim has no palette to come from,
   * and deriving it would put it back through the same light/dark detection that a translucent
   * `surface` colour can fool.
   */
  @Composable
  fun lightBarTint(): GlassTint = GlassTint(
    overlay = Color.White.copy(alpha = 0.56f),
    fallback = Color(0xFFFBFBFD).copy(alpha = 0.96f),
    hairline = Color.Black.copy(alpha = 0.16f),
  ).forSystemContrast()

  /** The light twin of [darkFloatingTint]. One step denser, for that one's reason. */
  @Composable
  fun lightFloatingTint(): GlassTint = GlassTint(
    overlay = Color.White.copy(alpha = 0.64f),
    fallback = Color(0xFFFDFDFF).copy(alpha = 0.96f),
    hairline = Color.Black.copy(alpha = 0.20f),
  ).forSystemContrast()

  /** Floating navigation: a little denser, because it travels over arbitrary content. */
  @Composable
  fun floatingTint(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = glassFilm().copy(alpha = if (dark) 0.48f else 0.52f),
      fallback = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.96f else 0.97f),
      hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.18f else 0.08f),
      mode = GlassTintMode.Neutral,
    ).forSystemContrast()
  }

  /**
   * Controls.
   *
   * Almost nothing. A control is a lens sitting on a surface that is itself already glass; a second
   * opaque wash here is what made the old buttons read as grey pills stuck onto a bar.
   */
  @Composable
  fun controlTint(): GlassTint {
    val dark = isDarkSurface()
    val scheme = MaterialTheme.colorScheme
    // Tinted with the app's own accent, and that is not decoration. A control's film is the only
    // thing standing between its label and whatever it is floating over; a white one at 18% was
    // effectively no film at all, and the buttons came out completely transparent with unreadable
    // text on them. Taking a fifth of the accent gives the pane a colour of its own to hold the
    // label against, and ties every control in the app to the one accent the palette has.
    val film = androidx.compose.ui.graphics.lerp(glassFilm(), scheme.primary, AccentShare)
    return GlassTint(
      overlay = film.copy(alpha = if (dark) 0.34f else 0.38f),
      fallback = if (dark) {
        scheme.surface.copy(alpha = 0.88f)
      } else {
        scheme.surface.copy(alpha = 0.90f)
      },
      hairline = Color.Transparent,
      mode = GlassTintMode.Neutral,
    ).forSystemContrast()
  }

  /** How much of the accent a control's film takes. Enough to be felt, not enough to be a fill. */
  private const val AccentShare = 0.20f

  /**
   * The capsule that marks a selection inside a bar.
   *
   * Barely there, because it is not what carries the selection: the tab underneath it is already
   * showing through in the accent colour, and a strong fill here would only cover that up. It exists
   * to give the selected tab an edge to sit inside.
   */
  @Composable
  fun selectionTint(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
      fallback = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
      hairline = Color.Transparent,
    )
  }

  /**
   * Cards and grouped lists.
   *
   * The one place the film is allowed to lean back towards the app's own surface rather than towards
   * the glass grey, because this is the only surface that holds paragraphs: what it stands over is a
   * coloured wash, and a bright film plus a colour wash plus body text is one layer too many for the
   * contrast to survive. The rule that goes with it is that the text itself never joins in — labels,
   * icons and badges over content glass stay fully opaque.
   */
  @Composable
  fun contentTint(): GlassTint {
    val dark = isDarkSurface()
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    return GlassTint(
      overlay = androidx.compose.ui.graphics.lerp(surface, glassFilm(), 0.4f)
        .copy(alpha = if (dark) 0.52f else 0.56f),
      // The opaque material a card falls back to is exactly the one it used before it could be
      // glass, so an API 30 device sees the page it has always seen rather than a degraded copy.
      fallback = surface,
      hairline = Color.Transparent,
      mode = GlassTintMode.Neutral,
    ).forSystemContrast()
  }

  /** Sheets and alerts. */
  @Composable
  fun modalTint(): GlassTint = modalTintBase().forSystemContrast()

  /**
   * Il modale a contrasto standard. [tintedModalTint] parte da qui e applica il contrasto del sistema
   * una volta sola, alla fine: partendo da [modalTint] lo applicherebbe due volte sul ripiego e sul
   * bordo, e la tinta, che ricalcola dall'inizio, non lo vedrebbe affatto.
   */
  @Composable
  private fun modalTintBase(): GlassTint {
    val dark = isDarkSurface()
    return GlassTint(
      overlay = glassFilm().copy(alpha = if (dark) 0.50f else 0.54f),
      fallback = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.97f else 0.98f),
      hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.14f else 0.08f),
      mode = GlassTintMode.Neutral,
    )
  }

  /**
   * Il materiale di una finestra che porta il colore della cosa da cui e' nata.
   *
   * Una card colorata che si apre in un pannello neutro diventa una card dentro una card: due
   * bordi, due superfici, e il colore che era l'informazione principale si ritrova relegato a un
   * rettangolo interno. Tingendo il **materiale**, invece, la card non entra nella finestra: la
   * finestra *e'* la card, ingrandita.
   *
   * Il colore entra al [ColourShare] e non intero: il vetro deve restare vetro — sopra questa
   * quota smette di trasmettere l'immagine di cio' che ha dietro e diventa un rettangolo colorato,
   * che e' precisamente cio' che non stiamo cercando di fare. Il ripiego sotto API 31 invece prende
   * il colore quasi pieno, perche' li' non c'e' nessuna immagine da trasmettere.
   */
  @Composable
  fun tintedModalTint(colour: Color): GlassTint {
    val dark = isDarkSurface()
    val base = modalTintBase()
    return base.copy(
      overlay = lerp(base.overlay, colour, ColourShare).copy(alpha = if (dark) 0.72f else 0.76f),
      fallback = lerp(base.fallback, colour, 0.82f).copy(alpha = if (dark) 0.97f else 0.98f),
      hairline = lerp(base.hairline, colour, 0.30f),
      // Il colore d'origine e' una tinta, non una pellicola: nel materiale nuovo sta sopra il velo e la sua
      // luminosita' segue lo sfondo. Vedi [GlassTintMode.Colored].
      mode = GlassTintMode.Colored,
    ).forSystemContrast()
  }

  /**
   * Quanto del colore d'origine entra nel film del vetro.
   *
   * Tarato guardando il caso peggiore e non quello facile: sopra una pagina chiara bastava poco,
   * ma un menu contestuale si apre spesso **sopra un'altra superficie satura** — e li' un film
   * timido perdeva contro il colore che aveva dietro, tanto da sembrare non applicato affatto. Il
   * vetro trasmette ancora: si vede cosa c'e' sotto, ma di chi sia la finestra non e' piu' in
   * discussione.
   */
  private const val ColourShare = 0.78f

  /**
   * Whether the app is painting a dark surface.
   *
   * Read from the palette rather than from `isSystemInDarkTheme()`: the app carries its own theme
   * setting, so with the system light and the app forced to AMOLED the bars would otherwise mix a
   * white tint into a black backdrop.
   *
   * [LocalFluidSurfaceSide] comes first, and only because the palette can be unreadable: a design
   * whose surfaces are translucent films files a film in `surface`, and luminance ignores alpha.
   * An app that says which side it is on is believed; everyone else keeps the guess they had.
   *
   * Public since 2.10.0, for the effects an app draws *over* glass — a touch highlight, a burst of
   * light — which have to turn over with the material exactly as its rim does.
   */
  @Composable
  fun isDarkSurface(): Boolean =
    darkSurface(LocalFluidSurfaceSide.current, MaterialTheme.colorScheme.surface)

  /**
   * The rule itself, out where it can be read and tested without a composition.
   *
   * Pure on purpose: the interesting case is a `surface` that cannot be believed, and a test that
   * has to stand up a theme to reach it is a test nobody writes.
   */
  internal fun darkSurface(declaredSide: Boolean?, surface: Color): Boolean =
    declaredSide ?: (surface.luminance() < 0.5f)
}

/** Which edge, if any, carries the hairline that separates glass from content. */
enum class GlassEdge { None, Bottom, Top }

/** How the material ends. */
enum class GlassFalloff {
  /** The same everywhere, with a defined edge. For a floating control with its own silhouette. */
  Uniform,

  /**
   * Strongest at the top, ramping to nothing at the bottom.
   *
   * For a bar pinned under the status bar. A uniform bar has to end somewhere, and wherever it ends
   * there is a line across the screen with sharp content on one side and refracted content on the
   * other. That line is the single most artificial-looking part of a translucent bar; ramping the
   * whole material out — glass, tint and rim together — removes it entirely.
   */
  FadeDown,
}

/**
 * Turns a composable into a pane of glass over [state]'s content.
 *
 * Dalla 4.0.0 i materiali sono due ([FluidGlassModel]), con la stessa firma. Da Android 13, con le sorgenti
 * accettate dal compilatore, su una forma che il campo sa rendere (un rettangolo o una `CornerBasedShape`) e
 * senza un antenato che chieda il vetro di prima ([LocalFluidGlassModel]), il pannello e' disegnato dal
 * materiale nuovo ([FluidGlassModel.Liquid]). Altrimenti, e sempre per la fascia ([GlassFalloff.FadeDown]) e
 * per un velo senza lente, bordo ne' ombra (lo scrim di un modale), dal vetro di prima, al pixel com'era. Nel
 * materiale nuovo [optics] vale come attenuatore: dal valore piu' basso che ogni campo ha fra i ruoli di serie
 * in su il materiale e' quello del profilo, sotto ne resta la frazione (vedi `glassRecipe`); il raggio di
 * [state] non si legge, e la pellicola di [tint] conta secondo il suo modo ([GlassTintMode]).
 *
 * @param intensity 0 = il pannello non c'e', 1 = materiale pieno. A zero non si disegna niente, **contenuto
 *   compreso**. In mezzo il materiale arriva come materiale (nel vetro di prima salgono vividezza, sfocatura,
 *   lente, pellicola, bordo e ombra; nel nuovo saturazione, lente, velo, luce e ombra, con lo sfondo che passa
 *   da nitido a smerigliato senza che il raggio cambi), e il contenuto del pannello si dissolve insieme a lui,
 *   in un layer. Non lascia quindi opachi i controlli che il pannello contiene: un'etichetta che deve restare
 *   piena mentre il vetro arriva sta fuori dal pannello, in un fratello disegnato dopo. Sotto API 31 segue
 *   l'intensita' la sola pellicola di ripiego, e il contenuto resta pieno.
 * @param opticalDepth independent weight on the lens, the rim and the shadows, leaving the blur and
 *   the tint alone. A control uses it to melt its own edge away while it is at rest.
 * @param pressed 0 = resting, 1 = fully pressed. Deepens the lens without re-recording anything.
 * @param exports when set, this pane also publishes *itself* — material, tint and all — into that
 *   state, so another pane can refract it. That is how a control on a bar bends the bar rather than
 *   the page underneath it. Pair it with [rememberCombinedGlassBackdrop] or [glassContainer]. Dalla
 *   2.13.0 il materiale si registra solo mentre qualcuno lo campiona, e il vetro che sta *dentro*
 *   questo pannello lo trova anche senza: vedi `nestInContainer`. Mai il contenuto; e nel materiale nuovo
 *   mai la luce (filo, riflessi, bagliore): un tasto sul pannello campiona il suo corpo, non il suo bordo.
 * @param layerBlock transforms applied to the whole pane. The renderer inverts them when it samples,
 *   so a bar that scales or slides keeps holding the image the world actually has behind it. L'ombra sta
 *   dentro questa trasformazione, nel vetro di prima come nel nuovo: un tasto che cresce sotto il dito la porta
 *   con se'.
 */
@Composable
fun Modifier.glassSurface(
  state: GlassBackdropState,
  tint: GlassTint,
  shape: Shape = RectangleShape,
  edge: GlassEdge = GlassEdge.None,
  falloff: GlassFalloff = GlassFalloff.Uniform,
  intensity: () -> Float = { 1f },
  role: GlassRole = if (falloff == GlassFalloff.FadeDown) GlassRole.Bar else GlassRole.Floating,
  optics: GlassOptics = GlassDefaults.optics(role),
  opticalDepth: () -> Float = { 1f },
  pressed: () -> Float = { 0f },
  exports: GlassBackdropState? = null,
  layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
  /**
   * Sample the backdrop once and keep the capture while the pane moves, instead of re-capturing —
   * and re-running the whole effect chain — on every frame of a scroll. Defaults to on for
   * [GlassRole.Content], whose backdrop is the static ambient wash: gradients riding along with the
   * pane are indistinguishable from gradients fixed behind it, and the saving is the entire
   * per-frame cost of content glass. Never right for glass over live content — a bar over a
   * scrolling body must keep re-sampling or it carries a stale copy of the page around.
   */
  sampleOnce: Boolean = role == GlassRole.Content,
  /**
   * Ogni quanto, al massimo, questo pannello puo' rifare la propria cattura dello sfondo. Zero
   * (il default) vuol dire "quando serve", che sopra uno sfondo animato significa **a ogni
   * fotogramma**: nove pannelli su una schermata sono nove replay dello schermo per fotogramma.
   * Con un fondale che si muove piano, 100 ms non si distinguono sotto la sfocatura e costano un
   * dodicesimo.
   */
  resampleIntervalMillis: Long = 0L,
  /**
   * While this answers true the pane keeps the capture it has instead of taking a new one. See
   * `drawBackdrop(frozen)`: still refreshed a few times a second, and always fresh once released.
   * Since 2.10.0.
   */
  frozen: () -> Boolean = { false },
  /** Hold the capture even while the pane moves. See `drawBackdrop(heldWhileMoving)`. Since 2.10.0. */
  heldWhileMoving: () -> Boolean = { false },
  /**
   * Vetro su vetro (2.13.0). Dentro un altro pannello di vetro questa superficie campiona il
   * *materiale* di quel pannello — il fondale come lo vede lui: sfocato, rifratto, tinto — sopra a
   * [state], invece di [state] e basta. Un tasto su un pop-up, una card su un foglio, un'azione su
   * una notifica non sono mai piu' nitidi di quello su cui stanno: sfocano la sfocatura.
   *
   * Automatico: il pannello e' il nodo di vetro piu' vicino fra gli antenati (o un nodo marcato con
   * [glassContainer]), e [state] resta quello che si vede fuori dai suoi bordi. Se [state] contiene
   * gia' il materiale di quel pannello (un cablaggio a mano con [exports]) non cambia niente. False
   * per la rara superficie che ricostruisce da sola il materiale del pannello su cui sta.
   *
   * Nel materiale nuovo ([FluidGlassModel.Liquid]) una superficie che sta cosi' dentro un pannello e' anche
   * **vetro annidato** (4.0.0): il materiale che campiona ha gia' il suo velo, quindi il suo velo scende (sul
   * tema scuro a un terzo, sul chiaro resta) e l'ombra si dimezza, perche' un tasto dentro una notifica ne prenda
   * il colore invece di posarci sopra una toppa grigia. Vale per gli stessi casi e con la stessa regola: un vetro
   * accanto a un altro, o con questo parametro falso, resta una lastra sulla pagina.
   */
  nestInContainer: Boolean = true,
): Modifier {
  val resolved = remember(optics) { optics.sanitized() }

  // Below API 31 there is no RenderEffect at all: no blur, no lens, no vibrancy. Sampling the
  // backdrop there would hand a *sharp* copy of the page to the bar, which is worse than not
  // sampling it. A defined solid material is the honest degradation.
  if (!isRenderEffectSupported()) {
    return this.glassFallback(shape, tint, edge, falloff, intensity)
  }

  // Il materiale nuovo (4.0.0), dove puo' esistere: Android 13, sorgenti sane, una forma che il campo sa
  // rendere, e nessun antenato che chieda il vetro di prima. Tutto cio' che segue questo `if` e' il vetro
  // di prima, lasciato com'era: e' il ripiego, e deve restare identico al pixel.
  if (rememberFluidGlassModel(shape, falloff, resolved) == FluidGlassModel.Liquid) {
    return this.glassLiquidSurface(
      state = state,
      tint = tint,
      shape = shape,
      edge = edge,
      intensity = intensity,
      role = role,
      optics = resolved,
      opticalDepth = opticalDepth,
      pressed = pressed,
      exports = exports,
      layerBlock = layerBlock,
      sampleOnce = sampleOnce,
      resampleIntervalMillis = resampleIntervalMillis,
      frozen = frozen,
      heldWhileMoving = heldWhileMoving,
      nestInContainer = nestInContainer,
      touch = null,
    )
  }

  val currentIntensity by rememberUpdatedState(intensity)
  // Il livello di qualità è letto come *stato*, dentro le lambda che lo usano, non catturato qui:
  // così un cambio di livello ridisegna e basta, senza ricomporre la superficie che lo porta.
  val quality = LocalFluidGlassQuality.current
  val qualityFactor: () -> Float = remember(quality) { { quality?.level ?: 1f } }
  val currentDepth by rememberUpdatedState(opticalDepth)
  val currentPressed by rememberUpdatedState(pressed)
  val shapeBlock = remember(shape) { { shape } }
  val blurRadius = state.blurRadius
  val lensAvailable = isRuntimeShaderSupported()

  val effects: BackdropEffectScope.() -> Unit = remember(resolved, blurRadius, quality, lensAvailable) {
    {
      val amount = clampGlassUnit(currentIntensity())
      if (amount > 0.001f) {
        // Sfocatura e tinta non lo vedono mai: sono le due cose che tengono leggibile il testo
        // sopra il vetro. Quello che scala è la *forma* del materiale, che durante uno
        // scorrimento veloce nessuno guarda. Vedi [FluidGlassQuality].
        val q = quality?.level ?: 1f
        val depth = clampGlassUnit(currentDepth()) * amount * q
        val press = clampGlassUnit(currentPressed())

        if (resolved.vibrancy != 1f) {
          // Ramped with the material rather than switched on: a bar whose colours saturate a frame
          // before it has any blur announces itself as an effect.
          colorControls(saturation = 1f + (resolved.vibrancy - 1f) * amount)
        }
        // La sfocatura è la parte più cara della catena, ed è l'unica il cui compito — tenere
        // leggibile il testo stampato sopra — durante un lancio non esiste: a quella velocità non
        // si legge niente. Quindi scende, ma non a zero e non fino in fondo: sotto una certa
        // frazione il vetro smette di sembrare vetro e il cambio si vedrebbe quando la pagina si
        // ferma. Vedi [FluidGlassQuality].
        //
        // La taglia del pannello (lato corto, in dp veri: `size` e `density` dello scope sono
        // scalati dello stesso fattore, quindi il rapporto non dipende dalla risoluzione della
        // cattura) fa crescere sfocatura e lente. Senza la lente non c'e' niente che copra il
        // margine in piu' della sfocatura, quindi sotto API 33 la regola resta spenta.
        val sized = glassSizedOptics(
          optics = resolved,
          baseBlurPx = blurRadius.toPx(),
          amount = amount,
          quality = q,
          minDimPx = size.minDimension,
          density = density,
          lensAvailable = lensAvailable,
        )
        blur(sized.blurPx)
        lens(
          refractionHeight = sized.refractionHeightPx * depth,
          refractionAmount = sized.refractionAmountPx * depth *
            (1f + press * resolved.pressedDepthBoost),
          depthEffect = resolved.depthEffect,
          // Sette campioni per pixel sono la prima cosa che se ne va: a pagina in movimento la
          // frangia cromatica non si distingue, e a pagina ferma torna intera.
          chromaticAberration = resolved.dispersion && q > 0.75f,
        )
      }
    }
  }

  // The depth the *edge* answers to, which is not the one the bend answers to: see
  // [GlassOptics.edgeFloor].
  val edgeDepth: () -> Float = remember(resolved) {
    { glassEdgeDepth(resolved.edgeFloor, currentDepth()) }
  }

  // A specular rim is drawn *additively*, and that is what makes it read as light
  // caught on an edge rather than as a line painted along one. On a light surface
  // there is no light left to add — white plus white is white — so every pane in
  // an app on its light side simply ended where its film ended, with no edge at
  // all. The edge is most of what says "glass" at a glance, so this is not a
  // detail of the material; it is the material failing to appear.
  //
  // The turn-over is both halves at once: a dark rim, and an ordinary blend to
  // put it down with. Turning the colour over on its own changes nothing, which
  // is the trap — it compiles, it runs, and it draws exactly nothing.
  //
  // Colore e modo di comporre stanno in [glassRimRecipe], e non qui, perche' il bordo e' uno solo:
  // `fluidPhysicsSurface` lo disegna da se' e leggeva un'altra ricetta (un gradiente bianco in
  // entrambi i temi), cosi' la stessa lastra aveva due bordi che non andavano d'accordo.
  val darkSide = GlassDefaults.isDarkSurface()
  val highlight: () -> Highlight? = remember(resolved, quality, edgeDepth, darkSide) {
    val rim = glassRimRecipe(darkSide)
    val style = HighlightStyle.Default(
      color = rim.color,
      blendMode = rim.blendMode,
      angle = resolved.highlightAngle,
      falloff = 1f,
    );
    {
      val amount = clampGlassUnit(currentIntensity()) * edgeDepth() *
        (quality?.level ?: 1f)
      if (resolved.highlightAlpha <= 0f || amount <= 0.001f) {
        null
      } else {
        Highlight(
          width = resolved.highlightWidth,
          blurRadius = resolved.highlightWidth / 2f,
          alpha = resolved.highlightAlpha * amount,
          style = style,
        )
      }
    }
  }

  // Heavier on paper, and it is not a preference.
  //
  // On a dark page a black shadow is a black square on a black floor: it does nothing, and the
  // film is what lifts the pane. On a light one it is the opposite — the film has no headroom
  // left, because the page is already near white — so the shadow and the hairline are the whole
  // of what says a surface is floating. Half the weight of the two-sided value was chosen when
  // only one side existed.
  val shadow: () -> Shadow? = remember(resolved, quality, edgeDepth, darkSide) {
    {
      val amount = clampGlassUnit(currentIntensity()) * edgeDepth() *
        (quality?.level ?: 1f)
      if (resolved.shadowAlpha <= 0f || amount <= 0.001f) {
        null
      } else {
        Shadow(
          radius = resolved.shadowRadius,
          color = glassShadowColor(darkSide),
          alpha = resolved.shadowAlpha * amount,
        )
      }
    }
  }

  val innerShadow: () -> InnerShadow? = remember(resolved, quality) {
    {
      val amount = clampGlassUnit(currentIntensity()) * clampGlassUnit(currentDepth()) *
        (quality?.level ?: 1f)
      if (resolved.innerShadowAlpha <= 0f || amount <= 0.001f) {
        null
      } else {
        InnerShadow(
          radius = resolved.innerShadowRadius,
          color = Color.Black.copy(alpha = 0.15f),
          alpha = resolved.innerShadowAlpha * amount,
        )
      }
    }
  }

  val onDrawSurface: DrawScopeBlock = remember(tint, edge, falloff) {
    {
      val amount = clampGlassUnit(currentIntensity())
      if (amount > 0.001f) {
        drawGlassTint(tint, edge, falloff, amount)
      }
    }
  }

  return this
    .glassMask(falloff, resolved.shadowRadius, currentIntensity)
    .drawBackdrop(
      backdrop = state.backdrop,
      shape = shapeBlock,
      effects = effects,
      highlight = highlight,
      shadow = shadow,
      innerShadow = innerShadow,
      layerBlock = layerBlock,
      exportedBackdrop = exports?.layerBackdrop,
      onDrawSurface = onDrawSurface,
      backdropScale = if (layerBlock != null) {
        1f
      } else {
        glassResolutionScale(blurRadius.value * resolved.blurScale) * resolved.backdropResolution
      },
      sampleOnce = sampleOnce,
      resampleIntervalMillis = resampleIntervalMillis,
      // La leva più grande che il materiale ha, e la ragione per cui vale la pena avere un livello
      // di qualità: la catena si paga per pixel, quindi dimezzare la cattura è un quarto del
      // lavoro. Vedi [FluidGlassQuality] e `quantiseScaleFactor`.
      backdropScaleFactor = qualityFactor,
      frozen = frozen,
      heldWhileMoving = heldWhileMoving,
      nestInContainer = nestInContainer,
    )
}

/**
 * Una `glassSurface` disegnata dal materiale nuovo ([FluidGlassModel.Liquid]): gli stessi ingressi di
 * [glassSurface], gia' risolti, piu' il bagliore del dito che `glassControlSurface` passa da qui.
 *
 * Chi la chiama ha gia' chiesto a [rememberFluidGlassModel] e ha avuto `Liquid`: qui non c'e' un ripiego.
 *
 * Cosa cambia rispetto al vetro di prima, nodo per nodo (piano 3.3):
 *  - **l'ombra** e' `Modifier.glassShadow` di `glass/field` (due maschere in un layer), dentro il layer di
 *    [layerBlock] e fuori dal ritaglio; un'ottica senza ombra non monta il nodo;
 *  - **il corpo** e' una catena sola sulla cattura: saturazione, sfocatura, mescola, shader del corpo. Velo,
 *    tinta e maschera stanno nello shader: niente pellicola dipinta, niente anello, niente ombra interna;
 *  - **la luce** (filo, riflessi, bagliore) si disegna sopra il corpo e sotto il contenuto, fuori dal
 *    materiale che il vetro annidato campiona;
 *  - **`intensity`** non scala piu' il raggio di sfocatura ne' passa da un `saveLayer` attorno a tutto: e'
 *    `materialize`. A zero non si disegna niente, come prima; fra zero e uno il contenuto si dissolve come
 *    prima, ma da solo.
 *
 * @param optics gia' passata da `sanitized()`.
 * @param touch il bagliore del dito, o `null` per una lastra che non lo ha.
 */
@Composable
internal fun Modifier.glassLiquidSurface(
  state: GlassBackdropState,
  tint: GlassTint,
  shape: Shape,
  edge: GlassEdge,
  intensity: () -> Float,
  role: GlassRole,
  optics: GlassOptics,
  opticalDepth: () -> Float,
  pressed: () -> Float,
  exports: GlassBackdropState?,
  layerBlock: (GraphicsLayerScope.() -> Unit)?,
  sampleOnce: Boolean,
  resampleIntervalMillis: Long,
  frozen: () -> Boolean,
  heldWhileMoving: () -> Boolean,
  nestInContainer: Boolean,
  touch: GlassTouchLight?,
): Modifier {
  val currentIntensity by rememberUpdatedState(intensity)
  val currentDepth by rememberUpdatedState(opticalDepth)
  val currentPressed by rememberUpdatedState(pressed)
  val currentTouch by rememberUpdatedState(touch)
  // Pellicola e tinta sono lette in disegno: un colore che si anima rifa' la catena, non l'elemento del nodo.
  // Il modo risolto, non quello scritto: una tinta di serie a cui hanno cambiato colore e' una pellicola.
  val tintMode = glassTintLiquidMode(tint)
  val currentFilm by rememberUpdatedState(if (tintMode == GlassTintMode.Fixed) tint.overlay else Color.Unspecified)
  val currentTint by rememberUpdatedState(if (tintMode == GlassTintMode.Colored) tint.overlay else Color.Unspecified)
  // Come nel vetro di prima: la qualita' e' uno stato letto dentro le lambda, non catturato qui.
  val quality = LocalFluidGlassQuality.current
  val qualityFactor: () -> Float = remember(quality) { { quality?.level ?: 1f } }
  val darkSide = GlassDefaults.isDarkSurface()
  val screenDensity = LocalDensity.current.density

  val surface = remember(role, optics, shape, darkSide, screenDensity, quality) {
    GlassLiquidSurface(
      role = role,
      optics = optics,
      shape = shape,
      darkSide = darkSide,
      screenDensity = screenDensity,
      intensity = { currentIntensity() },
      opticalDepth = { currentDepth() },
      pressed = { currentPressed() },
      quality = { quality?.level ?: 1f },
      film = { currentFilm },
      tint = { currentTint },
      touch = { currentTouch },
    )
  }

  // Del vetro di prima `onDrawSurface` tiene solo il filetto di `GlassEdge`, dove c'e': la pellicola e' nel
  // corpo. Niente lambda dove non c'e' niente da disegnare.
  val hairline = tint.hairline
  val onDrawSurface: DrawScopeBlock? = remember(hairline, edge) {
    if (edge == GlassEdge.None || hairline.alpha <= 0f) {
      null
    } else {
      {
        val amount = clampGlassUnit(currentIntensity())
        if (amount > 0.001f) drawGlassHairline(hairline, edge, amount)
      }
    }
  }

  val shadow = remember(surface) {
    if (surface.castsShadow) Modifier.glassFieldShadow(surface.shadowOutline, surface.shadow) else Modifier
  }

  return this
    .glassPresence(currentIntensity)
    .drawBackdrop(
      backdrop = state.backdrop,
      shape = surface.clipShapeBlock,
      effects = surface.effects,
      highlight = null,
      shadow = null,
      innerShadow = null,
      layerBlock = layerBlock,
      exportedBackdrop = exports?.layerBackdrop,
      onDrawSurface = onDrawSurface,
      // Come oggi: con un `layerBlock` la cattura resta a risoluzione piena (il blocco si inverte contro la
      // densita' dello scope, e una densita' scalata lo sposterebbe).
      backdropScale = if (layerBlock != null) 1f else surface.captureScale,
      sampleOnce = sampleOnce,
      resampleIntervalMillis = resampleIntervalMillis,
      backdropScaleFactor = qualityFactor,
      frozen = frozen,
      heldWhileMoving = heldWhileMoving,
      nestInContainer = nestInContainer,
      onDrawLight = surface.onDrawLight,
      underlay = shadow,
    )
    .glassContentFade(currentIntensity)
}

/**
 * A intensita' zero una lastra `Liquid` non disegna niente, contenuto compreso: e' cio' che faceva
 * `glassMask` nel vetro di prima, e chi spegne una superficie con `intensity` conta su questo. Nessun layer:
 * salta il disegno e basta.
 */
private fun Modifier.glassPresence(intensity: () -> Float): Modifier =
  this.drawWithContent { if (clampGlassUnit(intensity()) > 0.001f) drawContent() }

/**
 * Il contenuto di una lastra `Liquid` segue `intensity` come nel vetro di prima: pieno a uno, dissolto in
 * mezzo. Nel vetro di prima lo faceva il `saveLayer` di `glassMask`, che avvolgeva materiale e contenuto
 * insieme; qui il materiale si dissolve da se' (`materialize`) e il `saveLayer` resta per il solo contenuto,
 * e solo mentre l'intensita' e' a meta' strada: una lastra piena non paga un buffer.
 *
 * Sta **dopo** il nodo del vetro, quindi avvolge cio' che il nodo chiama contenuto e non il materiale.
 */
private fun Modifier.glassContentFade(intensity: () -> Float): Modifier = this.drawWithCache {
  val paint = Paint()
  val bounds = Rect(0f, 0f, size.width, size.height)
  onDrawWithContent {
    val amount = clampGlassUnit(intensity())
    when {
      amount <= 0.001f -> Unit
      amount >= 0.999f -> drawContent()
      else -> {
        paint.alpha = amount
        drawContext.canvas.saveLayer(bounds, paint)
        drawContent()
        drawContext.canvas.restore()
      }
    }
  }
}

/**
 * Il filetto di `GlassEdge`, da solo: mezzo dp in alto o in basso, col colore di `GlassTint.hairline`. Sono
 * le stesse righe che chiudono `drawGlassTint`, che il vetro di prima continua a usare: stanno qui in copia
 * perche' quella funzione non si tocca.
 */
private fun DrawScope.drawGlassHairline(color: Color, edge: GlassEdge, amount: Float) {
  val hairlinePx = maxOf(1f, 0.5f.dp.toPx())
  val hairline = color.copy(alpha = color.alpha * amount)
  when (edge) {
    GlassEdge.None -> Unit
    GlassEdge.Bottom -> drawRect(
      color = hairline,
      topLeft = Offset(0f, size.height - hairlinePx),
      size = Size(size.width, hairlinePx),
    )
    GlassEdge.Top -> drawRect(
      color = hairline,
      topLeft = Offset.Zero,
      size = Size(size.width, hairlinePx),
    )
  }
}

/**
 * L'ombra esterna del vetro, da sola: per un pannello che non e' il pannello di vetro a disegnarla.
 *
 * `glassSurface` disegna la propria ombra, ma dentro la propria superficie: se un antenato la ritaglia
 * (la barra flottante mette un `clip` sulla sagoma prima del pannello, che puo' essere dell'app) fuori
 * dalla sagoma non resta niente, e l'ombra che si vedeva era quella di piattaforma (`Modifier.shadow`).
 * Quell'ombra su una superficie trasparente trapela dentro la sagoma, e non e' del materiale: non ha il
 * colore del lato del tema ([glassShadowColor]) ne' il peso del ruolo. Questa e' la stessa che disegna
 * il vetro — stesso nodo (`ShadowElement`), stesso colore, stessa distanza (un sesto del raggio) — ma
 * messa dove si vede, **fuori** dal ritaglio.
 *
 * Si disegna sotto tutto cio' che la segue nella catena, e solo fuori dalla sagoma (il nodo scava
 * l'ombra sotto di essa). Non dipende da `RenderEffect`: dove il vetro e' il ripiego, l'ombra c'e'
 * comunque.
 *
 * Dalla 4.0.0 segue il materiale: dove il pannello e' disegnato dal materiale nuovo
 * ([FluidGlassModel.Liquid]) questa e' l'ombra **di quel materiale** (le due maschere di `glass/field`, coi
 * numeri della ricetta per questa ottica e questa taglia), perche' resti "la stessa che disegna il vetro".
 * Altrimenti la barra flottante avrebbe il corpo nuovo sopra l'ombra larga di prima.
 *
 * @param optics il ruolo da cui prende raggio e peso; di serie quello flottante.
 * @param radiusScale quanto scalare il raggio: un'ombra piu' bassa (un'elevazione minore) e' piu'
 *   stretta, con lo stesso peso. 0 o meno non disegna niente.
 */
@Composable
internal fun Modifier.glassShadow(
  shape: Shape,
  optics: GlassOptics = GlassDefaults.optics(GlassRole.Floating),
  radiusScale: Float = 1f,
): Modifier {
  val resolved = remember(optics) { optics.sanitized() }
  val darkSide = GlassDefaults.isDarkSurface()
  if (rememberFluidGlassModel(shape, GlassFalloff.Uniform, resolved) == FluidGlassModel.Liquid) {
    return this.glassLiquidShadow(shape, resolved, darkSide, radiusScale)
  }
  val shapeBlock = remember(shape) { { shape } }
  val shadow: () -> Shadow? = remember(resolved, radiusScale, darkSide) {
    {
      if (resolved.shadowAlpha <= 0f || !(radiusScale > 0f)) {
        null
      } else {
        Shadow(
          radius = resolved.shadowRadius * radiusScale,
          color = glassShadowColor(darkSide),
          alpha = resolved.shadowAlpha,
        )
      }
    }
  }
  return this.then(ShadowElement(ShapeProvider(shapeBlock), shadow))
}

/**
 * [glassShadow] per il materiale nuovo: l'ombra che una `glassSurface` `Liquid` di questa forma e di questa
 * ottica disegna da se', a materiale pieno, messa dove chi chiama la vuole.
 *
 * @param optics gia' passata da `sanitized()`.
 * @param radiusScale scala raggi e scostamento insieme (un'elevazione minore e' un'ombra piu' stretta e piu'
 *   vicina), non le alfa: lo stesso peso, come nel vetro di prima.
 */
@Composable
private fun Modifier.glassLiquidShadow(
  shape: Shape,
  optics: GlassOptics,
  darkSide: Boolean,
  radiusScale: Float,
): Modifier {
  val screenDensity = LocalDensity.current.density
  val surface = remember(optics, shape, darkSide, screenDensity) {
    GlassLiquidSurface(
      role = GlassRole.Floating,
      optics = optics,
      shape = shape,
      darkSide = darkSide,
      screenDensity = screenDensity,
      intensity = { 1f },
      opticalDepth = { 1f },
      pressed = { 0f },
      quality = { 1f },
      film = { Color.Unspecified },
      tint = { Color.Unspecified },
      touch = { null },
    )
  }
  val shadow: () -> GlassShadowSpec? = remember(surface, radiusScale) {
    // La superficie restituisce lo stesso oggetto finche' i numeri non cambiano: quello scalato si rifa'
    // solo allora, non a ogni disegno.
    var from: GlassShadowSpec? = null
    var scaled: GlassShadowSpec? = null
    val block: () -> GlassShadowSpec? = {
      val spec = surface.shadow()
      when {
        spec == null || !(radiusScale > 0f) -> null
        radiusScale == 1f -> spec
        else -> {
          if (spec !== from) {
            from = spec
            scaled = spec.copy(
              contactRadius = spec.contactRadius * radiusScale,
              ambientRadius = spec.ambientRadius * radiusScale,
              offset = DpOffset(spec.offset.x * radiusScale, spec.offset.y * radiusScale),
            )
          }
          scaled
        }
      }
    }
    block
  }
  return if (surface.castsShadow) this.glassFieldShadow(surface.shadowOutline, shadow) else this
}

/**
 * What fraction of its own resolution a pane processes its backdrop at.
 *
 * The `RenderEffect` chain is charged per pixel and it is the most expensive thing the design system
 * does, so a full-screen scrim is worth six times a small one. What pays for the shortcut is the
 * blur itself — a wide kernel is exactly what hides the upscale — which is why the fraction is a
 * function of the radius and nothing else. At zero blur nothing is hidden and the pane stays at full
 * resolution, because a clear lens is *all* edge and a soft edge is the one thing it cannot have.
 *
 * The floor is deliberately conservative. Pushing it lower is tempting and self-defeating: the
 * effect parameters are pre-multiplied by this, so far enough down a modest blur collapses to under
 * a pixel and the upscale has nothing left smoothing it, which reads as pixelation rather than as
 * frost.
 */
internal fun glassResolutionScale(blurRadiusDp: Float): Float {
  if (!blurRadiusDp.isFinite() || blurRadiusDp <= 0f) return 1f
  val t = (blurRadiusDp / GlassFullQualityBlurDp).coerceIn(0f, 1f)
  return 1f - t * (1f - GlassMinResolutionScale)
}

/** Lowest fraction a heavily frosted pane may drop to. */
internal const val GlassMinResolutionScale = 0.4f

/** Quanta sfocatura resta a qualità minima. Vedi [FluidGlassQuality]. */
internal const val FluidGlassBlurFloor = 0.45f

/** Radius at or above which that floor is safe, in dp. */
internal const val GlassFullQualityBlurDp = 10f

/**
 * Il tetto di [GlassOptics.blurScale], e anche del prodotto finale dopo la regola della taglia.
 *
 * Non e' una scelta di design: e' la rete che prende un valore malformato (sessantaquattro dp di
 * sfocatura, oltre i quali nulla si nasconde di piu' e ogni pixel costa comunque). La regola della
 * taglia moltiplica il raggio, quindi senza questo tetto sul prodotto un `blurScale` gia' alto
 * potrebbe raddoppiare oltre la rete. Con i ruoli di serie (il piu' alto e' 5) non scatta mai.
 */
internal const val GlassMaxBlurScale = 16f

// ---------------------------------------------------------------------------------------------
// La regola della taglia
//
// Un pannello grande smerigliato come uno piccolo sembra un pannello piccolo ingrandito: il bordo
// piega l'immagine di pochi dp su una superficie larga mezzo schermo, e il suo spessore non si
// legge piu'. Quindi piu' un pannello e' grande piu' sfoca e piu' rifrange al bordo; quelli
// piccoli — tasti, chip, pomelli, la pillola di navigazione da 64 dp — restano esattamente come
// erano, perche' sotto la soglia il guadagno e' zero e non "quasi zero".
//
// I tre massimi sono numeri di partenza da giudicare su un dispositivo, non verita': stanno qui,
// con il motivo accanto, perche' e' qui che si ritoccano.
// ---------------------------------------------------------------------------------------------

/**
 * Lato corto, in dp, fino al quale un pannello e' "piccolo": guadagno esattamente zero.
 *
 * 64 dp e' l'altezza della pillola di navigazione, il pannello piu' grande che deve restare com'e'.
 * La curva e' una smoothstep, che parte con pendenza zero: un lato di 64.0001 dp (un arrotondamento
 * di pixel) guadagna zero virgola niente, quindi la soglia non puo' produrre uno scalino.
 */
internal const val GlassSizeSmallDp = 64f

/**
 * Lato corto, in dp, a cui il guadagno e' pieno. 320 dp e' la finestra di un alert o un gruppo
 * lista a larghezza di telefono: sopra, un pannello non e' piu' "piu' grande" in modo che l'occhio
 * distingua, e la curva smette di salire invece di continuare per sempre.
 */
internal const val GlassSizeLargeDp = 320f

/**
 * Di quanto cresce la sfocatura al guadagno pieno: 1.0 vuol dire il doppio.
 *
 * Il doppio e' il massimo che si e' voluto concedere: con il riferimento a 2.5 dp un pannello
 * `Content` arriva a 4 dp, una capsula `Floating` a 9, un `Modal` a 17.5 (il catalogo di Kyant
 * tiene una finestra da 250-300 dp a 16 e 8 dp). Oltre il doppio un pannello grande smette di
 * trasmettere un'immagine e trasmette la sua media — l'errore del 16 dp, gia' pagato una volta.
 * E non supera mai quello che la lente a profondita' piena copre: vedi [glassSizedOptics].
 */
internal const val GlassSizeBlurBoost = 1.0f

/**
 * Di quanto cresce l'altezza di rifrazione (quanto in dentro si sente il bordo) al guadagno pieno.
 *
 * Il playground di Kyant tiene l'altezza a ~0.1 del lato corto: a 320 dp sono 32 dp, e con 0.6 i
 * ruoli `Floating` e `Modal` ci arrivano (30.4 e 32). Non puo' comunque superare meta' del lato
 * corto — li' il campo del pannello e' tutto al centro e lo shader satura — ed e' anche cio' che
 * copre il margine della sfocatura, quindi non scende mai sotto l'altezza di base.
 */
internal const val GlassSizeHeightBoost = 0.6f

/**
 * Di quanto cresce lo spostamento al bordo al guadagno pieno.
 *
 * Kyant tiene lo spostamento a ~0.2 del lato corto, cioe' 64 dp a 320; con 0.8 i ruoli arrivano a
 * 40-52 dp (`Floating` 52.2, `Modal` 50.4, `Content` 39.6): dalla stessa parte, con meno slancio.
 * E' il numero piu' delicato dei tre, perche' uno spostamento che cresce piu' dell'altezza si
 * stacca dall'immagine dritta e fa vedere due versioni dello stesso oggetto nello stesso vetro (il
 * difetto del "fisheye" gia' pagato sui pop-up): se sul dispositivo succede, si scende da qui.
 */
internal const val GlassSizeAmountBoost = 0.8f

/**
 * Il guadagno della regola della taglia per un pannello con questo lato corto, da 0 a 1.
 *
 * Una smoothstep fra [GlassSizeSmallDp] (zero) e [GlassSizeLargeDp] (uno). Pura e senza stato,
 * perche' e' l'unica funzione che decide quanto cambia un pannello e conviene poterla leggere in un
 * test. Un lato non finito o non positivo non e' una taglia: vale zero, cioe' "come prima".
 */
internal fun glassSizeGain(minDimDp: Float): Float {
  if (!minDimDp.isFinite() || minDimDp <= GlassSizeSmallDp) return 0f
  val t = ((minDimDp - GlassSizeSmallDp) / (GlassSizeLargeDp - GlassSizeSmallDp)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/** I tre numeri che la regola della taglia decide per un pannello, tutti in pixel dello scope. */
@Immutable
internal class GlassSizedOptics(
  /** Il raggio da dare a `blur()`: gia' moltiplicato per intensita' e qualita'. */
  val blurPx: Float,
  /** L'altezza di rifrazione piena, da moltiplicare per la profondita' di chi la usa. */
  val refractionHeightPx: Float,
  /** Lo spostamento pieno al bordo, da moltiplicare per la profondita' di chi lo usa. */
  val refractionAmountPx: Float,
)

/**
 * La formula della taglia, in un posto solo: la usano `glassSurface` e `fluidPhysicsSurface`, e una
 * regola scritta due volte e' una regola che prima o poi smette di essere la stessa.
 *
 * Tutti i parametri sono nello spazio dello scope degli effetti: [minDimPx] e [density] sono
 * scalati dallo stesso fattore di risoluzione, quindi `minDimPx / density` sono dp veri anche
 * quando la cattura e' a meta' risoluzione.
 *
 * @param optics ottica gia' passata da `sanitized()`.
 * @param baseBlurPx il raggio di riferimento dello stato (`GlassBackdropState.blurRadius`) in px,
 *   prima del moltiplicatore del ruolo.
 * @param amount intensita' del materiale, 0..1.
 * @param quality [FluidGlassQuality] corrente, 0..1: scala la sfocatura fra [FluidGlassBlurFloor]
 *   e 1, come ha sempre fatto.
 * @param minDimPx lato corto del pannello. Per un morph e' quello della *sagoma*, non quello del
 *   layer che la ospita, che e' lo schermo intero.
 * @param lensAvailable false sotto API 33. Senza lente niente copre il margine in piu' della
 *   sfocatura, e un raggio che cambia con la taglia sposterebbe il padding a ogni fotogramma di un
 *   morph: la regola resta spenta e il pannello e' quello di prima.
 *
 * **Il padding resta fermo.** Il margine della cattura vale `max(0, sfocatura - altezza * profondita')`,
 * e durante un morph la taglia cambia a ogni fotogramma: se quel margine si muovesse, ogni
 * fotogramma sporcherebbe la cattura (vedi il contratto in `FluidPhysicsEffects`). Percio' la
 * sfocatura puo' crescere *solo fin dove la lente a profondita' piena la copre* — `cover` qui
 * sotto — e mai sotto quella che il pannello aveva senza la regola. A materiale pieno e qualita'
 * piena, con ogni ruolo di serie, il tetto non scatta (la sfocatura resta sotto 0.8 volte
 * l'altezza), quindi il guadagno e' intero e il margine e' zero a ogni taglia; a qualita' piu'
 * bassa il margine non e' mai maggiore di quello che il pannello aveva prima della regola.
 */
internal fun glassSizedOptics(
  optics: GlassOptics,
  baseBlurPx: Float,
  amount: Float,
  quality: Float,
  minDimPx: Float,
  density: Float,
  lensAvailable: Boolean = true,
): GlassSizedOptics {
  val blurQuality = FluidGlassBlurFloor + (1f - FluidGlassBlurFloor) * quality
  // L'espressione di sempre, nello stesso ordine: a guadagno zero il risultato e' identico bit per
  // bit a quello che i due chiamanti calcolavano da soli.
  val blur = baseBlurPx * optics.blurScale * amount * blurQuality
  val height = optics.refractionHeight.value * density
  val reach = optics.refractionAmount.value * density

  val minDimDp = if (density > 0f && density.isFinite() && minDimPx > 0f) minDimPx / density else 0f
  val gain = if (lensAvailable) glassSizeGain(minDimDp) * optics.sizeResponse else 0f
  if (gain <= 0f) return GlassSizedOptics(blur, height, reach)

  // Mai oltre meta' del lato corto: li' il campo del pannello e' al centro e lo shader satura. Il
  // tetto non scende sotto l'altezza di base, perche' un'ottica che la voleva gia' piu' grande non
  // deve diventare *piu' piccola* per via di una regola che serve a ingrandire.
  val scaledHeight = minOf(
    height * (1f + GlassSizeHeightBoost * gain),
    maxOf(height, minDimPx * 0.5f),
  )
  val scaledReach = reach * (1f + GlassSizeAmountBoost * gain)

  val ceiling = baseBlurPx * GlassMaxBlurScale * amount * blurQuality
  val grown = minOf(blur * (1f + GlassSizeBlurBoost * gain), maxOf(blur, ceiling))
  val cover = scaledHeight * amount * quality
  return GlassSizedOptics(
    blurPx = minOf(grown, maxOf(blur, cover)),
    refractionHeightPx = scaledHeight,
    refractionAmountPx = scaledReach,
  )
}

private typealias DrawScopeBlock = DrawScope.() -> Unit

private fun DrawScope.drawGlassTint(
  tint: GlassTint,
  edge: GlassEdge,
  falloff: GlassFalloff,
  amount: Float,
) {
  val overlay = tint.overlay.copy(alpha = tint.overlay.alpha * amount)
  when (falloff) {
    GlassFalloff.Uniform -> drawRect(overlay)
    // The tint runs out earlier than the glass does, so the bottom of a bar is refraction with no
    // colour left in it — which is what stops the ramp from ending on a visible grey lip.
    GlassFalloff.FadeDown -> drawRect(
      brush = Brush.verticalGradient(
        colorStops = arrayOf(
          0f to overlay,
          0.55f to overlay,
          1f to overlay.copy(alpha = 0f),
        ),
        startY = 0f,
        endY = size.height,
      ),
    )
  }

  if (tint.hairline.alpha <= 0f || edge == GlassEdge.None) return
  val hairlinePx = maxOf(1f, 0.5f.dp.toPx())
  val hairline = tint.hairline.copy(alpha = tint.hairline.alpha * amount)
  when (edge) {
    GlassEdge.None -> Unit
    GlassEdge.Bottom -> drawRect(
      color = hairline,
      topLeft = Offset(0f, size.height - hairlinePx),
      size = Size(size.width, hairlinePx),
    )
    GlassEdge.Top -> drawRect(
      color = hairline,
      topLeft = Offset.Zero,
      size = Size(size.width, hairlinePx),
    )
  }
}

/**
 * Fades the finished material — glass, rim, shadows, everything — instead of any one of its parts.
 *
 * It has to sit *outside* the renderer, because a mask applied inside the recorded layer would be
 * blurred along with the image and the ramp would grow its own soft lip. Wrapping the node in a
 * single `saveLayer` and cutting the result with `DstIn` is the only way to apply a gradient to
 * something already rasterised.
 *
 * When there is nothing to fade the modifier disappears entirely, so an ordinary pane at full
 * strength never pays for an offscreen buffer.
 */
private fun Modifier.glassMask(
  falloff: GlassFalloff,
  shadowRadius: Dp,
  intensity: () -> Float,
): Modifier {
  if (falloff == GlassFalloff.Uniform) {
    return this.drawWithCache {
      val paint = Paint()
      // Inflated so the drop shadow, which lives outside the pane, is not cut off by its own layer.
      val padding = shadowRadius.toPx() * 2f
      val bounds = Rect(-padding, -padding, size.width + padding, size.height + padding)
      onDrawWithContent {
        val amount = clampGlassUnit(intensity())
        when {
          amount <= 0.001f -> Unit
          amount >= 0.999f -> drawContent()
          else -> {
            paint.alpha = amount
            drawContext.canvas.saveLayer(bounds, paint)
            drawContent()
            drawContext.canvas.restore()
          }
        }
      }
    }
  }
  return this.drawWithCache {
    val paint = Paint()
    val bounds = Rect(0f, 0f, size.width, size.height)
    onDrawWithContent {
      val amount = clampGlassUnit(intensity())
      if (amount <= 0.001f) return@onDrawWithContent
      paint.alpha = amount
      drawContext.canvas.saveLayer(bounds, paint)
      drawContent()
      drawRect(
        brush = Brush.verticalGradient(
          colorStops = fadeDownStops(amount),
          startY = 0f,
          endY = size.height,
        ),
        blendMode = BlendMode.DstIn,
      )
      drawContext.canvas.restore()
    }
  }
}

/**
 * The ramp's coverage grows with the material rather than appearing at full height.
 *
 * A bar that reaches its final extent on the first non-zero scroll frame and only then gains opacity
 * reads as a rectangle switching on. Growing downward reads as the material arriving.
 */
internal fun fadeDownStops(amount: Float): Array<Pair<Float, Color>> {
  val coverage = smoothStep(clampGlassUnit(amount)).coerceIn(0.001f, 1f)
  val hold = coverage * 0.62f
  return arrayOf(
    0f to Color.White,
    hold to Color.White,
    coverage to Color.Transparent,
    1f to Color.Transparent,
  )
}

/** Below API 31 the material is a defined solid, deliberately not an approximation of glass. */
private fun Modifier.glassFallback(
  shape: Shape,
  tint: GlassTint,
  edge: GlassEdge,
  falloff: GlassFalloff,
  intensity: () -> Float,
): Modifier = this
  .clip(shape)
  .drawWithContent {
    val amount = clampGlassUnit(intensity())
    if (amount > 0.001f) {
      drawGlassTint(
        tint = tint.copy(overlay = tint.fallback),
        edge = edge,
        falloff = falloff,
        amount = amount,
      )
    }
    drawContent()
  }
