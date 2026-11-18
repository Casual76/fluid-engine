package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsShadow
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurfaceInked
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars

// La finestra ancorata: il pannello di un pop-up o di un menu che nasce da un'ancora, con il suo posto, il
// ritaglio del contenuto in viaggio e i numeri che ne regolano il moto. Spezzata da `FluidGlassModal.kt`
// in F5A.0; la chiama `FluidGlassModalPaneLayer` (`FluidGlassModalHost.kt`).

/**
 * Where the pop-up ended up, and the rectangle it grew out of.
 *
 * Written by the measure pass and read by the draw pass. Kept in one object rather than in
 * separate states because the start geometry and the position are one decision: split them and
 * there is a frame where the pop-up is already in its new place and still growing out of its old
 * rectangle.
 */
@Stable
private class FluidPopoverPlacement {
  var pivotX by mutableFloatStateOf(0.5f)
  var pivotY by mutableFloatStateOf(0.5f)

  /**
   * How small the pop-up starts, per axis, as a fraction of its finished size.
   *
   * Solved from the anchor rather than fixed, because "grows out of the button" is a statement about
   * two rectangles and not about a number: a wide button opening a narrow menu has to start wide,
   * and a constant makes that read as a zoom from somewhere else.
   */
  var startScaleX by mutableFloatStateOf(FluidPopoverStartScale)
  var startScaleY by mutableFloatStateOf(FluidPopoverStartScale)

  /**
   * Where the pop-up's top-left corner sits at the start of its travel, relative to where it ends —
   * which, morphing from an anchor, is the anchor's own corner. Zero for the pivot-based growth
   * a context menu keeps.
   */
  var startOffsetX by mutableFloatStateOf(0f)
  var startOffsetY by mutableFloatStateOf(0f)

  /** True when the pop-up starts as the anchor's exact rectangle and travels to its place. */
  var morphFromAnchor by mutableStateOf(false)

  /** The anchor's rectangle in the pane's own coordinates: the frame the pane grows out of. */
  var anchorLeft by mutableFloatStateOf(0f)
  var anchorTop by mutableFloatStateOf(0f)
  var anchorWidth by mutableFloatStateOf(0f)
  var anchorHeight by mutableFloatStateOf(0f)

  /** La taglia risolta del pannello: serve a ombra e immagine in uscita, che vivono fuori dal Layout. */
  var paneWidth by mutableFloatStateOf(0f)
  var paneHeight by mutableFloatStateOf(0f)

  /** Dove il pannello e' posato (pixel dell'host, all'intero): il ritaglio del contenuto in viaggio. */
  var paneLeft by mutableFloatStateOf(0f)
  var paneTop by mutableFloatStateOf(0f)
}

/**
 * The pane's frame while it opens: the anchor's rectangle, travelling to the pane's own.
 *
 * This has to be a **frame** and not a scale, and the distinction is the whole history of this
 * animation. Scaling the pane from the anchor's rectangle means two different factors on the two
 * axes — a card three hundred dp tall squeezed into a row of ninety while keeping its width —
 * and for the first third of every opening the type was anamorphic. Nothing in an interface is
 * anamorphic, so the eye catches it at once.
 *
 * A travelling frame has no factors at all. What grows is the shape the pane is seen through; the
 * pane inside it is scaled only *uniformly*, by a few percent, so it reads as coming toward you
 * without one letter changing proportion.
 *
 * The outline is a plain rounded rectangle rather than this system's continuous corner, and
 * deliberately: a generic path is CPU-rasterised into Skia's small-path atlas keyed by subpixel
 * phase, so a corner that moves every frame would re-rasterise every frame. The smoothing lives
 * inside two pixels of a corner that is in motion.
 */
private class FluidPopoverMorphWindow(
  private val placement: FluidPopoverPlacement,
  private val growth: () -> Float,
  private val startRadiusPx: Float,
  private val endRadiusPx: Float,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val g = growth().coerceIn(0f, 1f)
    val left = lerp(placement.anchorLeft, 0f, g)
    val top = lerp(placement.anchorTop, 0f, g)
    val right = lerp(placement.anchorLeft + placement.anchorWidth, size.width, g)
    val bottom = lerp(placement.anchorTop + placement.anchorHeight, size.height, g)
    val radius = lerp(startRadiusPx, endRadiusPx, g)
    return Outline.Rounded(
      RoundRect(
        rect = Rect(left, top, maxOf(right, left + 1f), maxOf(bottom, top + 1f)),
        cornerRadius = CornerRadius(radius, radius),
      ),
    )
  }
}

/** Il ritaglio del contenuto sul corpo della finestra in viaggio: un rettangolo arrotondato posato. */
private class FluidPopoverTravelClipShape(private val round: RoundRect) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    Outline.Rounded(round)
}

/**
 * A pop-up placed against [anchor], sized to its content, growing out of the corner nearest it.
 *
 * A `Layout` rather than a measured offset, because the alternative flashes: anything that has to
 * read its own size out of state before it can place itself gets drawn once in the wrong place
 * first, and on something that arrives under the finger, once is enough to see.
 *
 * The size is the content's own. That is what "dynamic" means here, and it is the reason this is not
 * simply a dialog with a source rectangle: a three-line menu is three lines tall, a detail with a
 * paragraph in it is as tall as the paragraph, and neither is padded out to a card.
 */
@Composable
internal fun FluidAnchoredPopover(
  anchor: Rect?,
  paneTitle: String?,
  /** Il colore della cosa da cui la finestra e' nata, se ne ha uno: vedi [GlassDefaults.tintedModalTint]. */
  paneTint: Color?,
  backdrop: GlassBackdropState?,
  compact: Boolean,
  /** True for [FluidGlassModalPresentation.Expand]: sit *on* the anchor rather than beside it. */
  overAnchor: Boolean,
  anchorRadiusPx: Float,
  /**
   * L'immagine registrata della riga che ha aperto la finestra, da [fluidExpandOrigin]. Viaggia
   * con la silhouette e svanisce nel primo terzo del percorso: e' la meta' "in uscita" del
   * contratto dei contenuti — la riga non sparisce, PARTE.
   */
  outgoing: GraphicsLayer?,
  /** La pagina cruda, per la finestra morph: il composito con lo scrim mente (vedi sotto). */
  windowBackdrop: GlassBackdropState?,
  /** Vero finche' il modale e' aperto: la chiusura cambia il clock del materiale della finestra. */
  open: () -> Boolean,
  presence: () -> Float,
  growth: () -> Float,
  growthCross: () -> Float,
  retreat: () -> Float,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  val gapPx = with(density) { FluidPopoverGap.toPx() }
  val marginPx = with(density) { FluidPopoverMargin.toPx() }
  // La barra di navigazione di sistema (3.0.3): un pannello aperto in basso non ci finisce sotto,
  // come i fogli. Conta solo per il limite basso: l'ancoraggio resta quello di sempre.
  val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
  val maxWidthPx = with(density) {
    when {
      compact -> FluidContextMenuMaxWidth
      // Un pop-up che nasce da una riga NON e' largo quanto la riga: se lo fosse, il viaggio
      // sarebbe solo verticale — un allungamento, non un'apertura. Stretto come una card, il
      // morph guadagna la componente laterale (i fianchi della riga si raccolgono) e il
      // pannello si legge come un oggetto, non come una fascia.
      anchor != null -> FluidPopoverAnchoredMaxWidth
      else -> FluidPopoverMaxWidth
    }.toPx()
  }
  val minWidthPx = with(density) { if (compact) FluidContextMenuMinWidth.toPx() else 0f }
  val paneRadiusPx = with(density) { (if (compact) FluidRadius.Group else FluidPopoverRadius).toPx() }
  val placement = remember { FluidPopoverPlacement() }
  val shape = ContinuousCornerShape(if (compact) FluidRadius.Group else FluidPopoverRadius)
  // Il pannello registra se stesso, cosi' quello che ci sta dentro puo' rifrangere *lui* invece
  // della pagina, che sta dietro uno scrim e non e' affar suo.
  val paneGlass = rememberGlassBackdrop()
  // F2.3b: la finestra possiede il suo contenuto e scurisce in continuo (un grande, `Modal`); il contenuto la segue.
  val popoverInk = rememberGlassInk()
  val windowPhysics = remember { FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)) }
  // Fluid Engine, 1.9.5: la finestra e' di Fluid-physics. La sagoma viaggia dal rettangolo
  // dell'ancora a quella del pannello con la rifrazione addosso — non un pannello scalato, la
  // silhouette stessa che si trasforma. L'orologio resta quello del modale (molle, dissolvenze, back
  // predittivo): la fisica e' guidata dall'esterno, vedi [FluidPhysicsState.driveExternally].
  //
  // Dalla 3.0 (fase 7) OGNI pannello di vetro passa da qui: anche quello senza ancora (cresce da un
  // seme al proprio centro) e quello accanto a una riga (da un seme sul perno piu' vicino). Il
  // pannello classico scalava il layer del proprio vetro — la prima trappola del vetro in movimento,
  // il fondale che si muove dentro la card — e resta solo per il ripiego opaco senza fondale.
  val useMorphWindow = backdrop != null
  // La pellicola della finestra con dentro la quota di scrim: lo stesso nero dello scrim vero,
  // composto sotto la tinta flottante. E' il punto di partenza del lerp tintFrom->tint guidato
  // da (1 - presence): a modale fermo la finestra porta lo scrim intero, lungo le dissolvenze
  // esattamente la frazione che lo scrim ha sullo schermo.
  val windowDark = GlassDefaults.isDarkSurface()
  // La pellicola a riposo: quella tinta quando la finestra porta il colore della cosa da cui e'
  // nata, altrimenti quella del modale. E' la pellicola di ogni pannello flottante (`panelTint`),
  // non una flottante propria: la finestra a riposo e' una lastra come il popover. E' **questa**
  // che si vede a modale aperto — `tintBlend` vale 0 con la presenza piena, cioe' sceglie `tintFrom`.
  val windowPanelTint = GlassDefaults.panelTint(paneTint)
  val windowOptics = GlassDefaults.panelOptics()
  val windowTintWithScrim = remember(windowPanelTint, windowDark) {
    windowPanelTint.copy(
      overlay = windowPanelTint.overlay.compositeOver(
        GlassPaintedScrim.color(windowDark),
      ),
    )
  }

  if (useMorphWindow && backdrop != null) {
    // Il materiale della finestra: la dissolvenza del modale, e in CHIUSURA anche il viaggio —
    // pellicola e ombra muoiono mentre la sagoma atterra sulla riga, non dopo. In apertura il
    // viaggio non c'entra: il materiale e' costante, cosi' quello che si vede attraverso il
    // vetro non pulsa ("il testo dietro non deve muoversi").
    val windowAmount: () -> Float = {
      presence() * (if (open()) 1f else minOf(growth(), growthCross()).coerceIn(0f, 1f))
    }
    // La finestra: una superficie a tutto schermo la cui silhouette e' il lerp ancora->pannello
    // al passo delle molle del modale. Il clip del layer resta fermo, la sagoma la scolpisce lo
    // shader — e' il contratto di Fluid-physics, quindi questo non ricattura e non ri-clippa mai.
    //
    // NIENTE scala sul layer: scalare il nodo scala anche il fondale campionato dentro il vetro
    // — "il testo dietro si muove", ed era vero. L'oltrepasso vive nella GEOMETRIA della
    // silhouette (overshootInflation di driveExternally): un moto solo, su tutti i lati.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawWithContent {
          // Il cancello del fotogramma di confine: al primo e all'ultimo fotogramma di vita del
          // nodo, il layer esterno puo' disegnare con le proprieta' di default — un lampo a
          // piena intensita', misurato alla luminanza nel video (214.6 -> 190.9 -> 204.1: un
          // fotogramma di scrim pieno, poi la dissolvenza che riparte da zero). Se il materiale
          // e' a zero non si disegna NIENTE, e una displaylist vuota non ha lampi da rigiocare.
          if (windowAmount() > 0.004f) drawContent()
        }
        // L'ombra della finestra: senza, il pannello aperto galleggiava piatto. E' quella di ogni
        // lastra di vetro (colore, peso e distanza di `glassSurface`), ma sfocata una volta sola per
        // taglia di pannello e stirata sulla sagoma a ogni fotogramma: il renderer la registrerebbe
        // di nuovo a ogni fotogramma del viaggio, ed era il lag dell'apertura. Vedi
        // [fluidPhysicsShadow].
        // Muore CON l'atterraggio, non col proprio orologio: un'ombra ancora accesa attorno a
        // una sagoma gia' posata sulla riga e' "il tasto resta scurito fino all'ultimo frame".
        .fluidPhysicsShadow(
          state = windowPhysics,
          optics = windowOptics,
          restSize = { Size(placement.paneWidth, placement.paneHeight) },
          cornerRadiusPx = paneRadiusPx,
          amount = windowAmount,
        )
        .fluidPhysicsSurfaceInked(
          ink = popoverInk,
          state = windowPhysics,
          // La pagina CRUDA, non il composito pagina+scrim. Gli exports dello scrim registrano
          // il suo materiale a forza PIENA (l'arrivo dello scrim e' un alpha sul pannello
          // finito), quindi campionare il composito significava mostrare dentro la card uno
          // scrim sempre pieno mentre quello sullo schermo sfumava — la card restava SCURA per
          // tutta la chiusura, misurato sui fotogrammi. Lo scurimento dello scrim la finestra
          // se lo dipinge da sola, qui sotto, alla forza CORRENTE.
          backdrop = windowBackdrop ?: backdrop,
          // Il materiale e' quello del pannello flottante, `panelOptics`: la finestra aveva numeri
          // suoi (sfocatura 2,2, bordo da 1,1 dp al 62%, scelti dopo il verdetto "troppo glassoso"
          // sulla prima versione) e a riposo non si distingueva da un popover per il motivo
          // sbagliato: non era la stessa lastra. Il vetro lo fa la rifrazione al bordo, non il
          // latte, e il bordo e' quello di ogni lastra (`glassRimRecipe`).
          // La finestra che nasce da una superficie colorata porta il suo colore: e' la stessa
          // card che si sta trasformando, non un pannello che la contiene.
          tint = windowPanelTint,
          role = GlassRole.Modal,
          optics = windowOptics,
          // La pellicola porta anche la quota di scrim che spetta alla finestra, alla forza a
          // cui lo scrim sta DAVVERO in questo fotogramma: piena a modale aperto, a scalare
          // lungo le dissolvenze. Cosi' il vetro non e' mai ne' piu' scuro ne' piu' chiaro
          // della pagina scrimmata che lo circonda.
          tintFrom = windowTintWithScrim,
          tintBlend = { 1f - presence().coerceIn(0f, 1f) },
          intensity = windowAmount,
          // Vetro su vetro (2.13.0): la finestra pubblica il proprio materiale, come il pannello
          // classico. Il contenuto vive in un nodo fratello, quindi non la trova da solo: lo dice
          // `glassContainer(paneGlass)` qui sotto. Registrato solo se dentro c'e' del vetro.
          exports = paneGlass,
          // F2.3b: la pellicola con la quota di scrim (`tintFrom`, fissa) vale sul lato del tema; quando la finestra
          // scurisce sopra uno sfondo scuro cede alla ricetta adattiva, come il foglio. Sul tablet il corpo restava a
          // 136 sopra il nero col testo gia' bianco.
          restingFilmYields = true,
        ),
    )
    // L'immagine della riga, in viaggio: parte dalla sua posizione esatta, segue il centro della
    // silhouette e svanisce nel primo terzo — la riga vera ha smesso di disegnarsi nello stesso
    // fotogramma, quindi quello che l'occhio segue e' UNA cosa che si trasforma.
    if (outgoing != null && anchor != null) {
      Box(
        modifier = Modifier
          .offset { IntOffset(anchor.left.roundToInt(), anchor.top.roundToInt()) }
          .size(
            width = with(density) { anchor.width.toDp() },
            height = with(density) { anchor.height.toDp() },
          )
          .graphicsLayer {
            val g = growth()
            val gc = growthCross()
            val travel = minOf(g, gc)
            // MAI moltiplicata per la dissolvenza del modale: al primo fotogramma la riga vera
            // e' gia' nascosta e la dissolvenza e' a zero — con `presence` qui dentro il vetro
            // partiva VUOTO. E in chiusura e' questa immagine, riapparendo lungo il ritorno, a
            // coprire lo scambio con la riga vera: quando il layer si smonta sono gia' identiche.
            // Vive fino a meta' viaggio: e' il titolo della riga che TRASLA dentro la card,
            // mentre il testo della card subentra sotto — e all'inverso in chiusura.
            alpha = (1f - travel / 0.5f).coerceIn(0f, 1f)
            val paneCenterX = anchor.left - placement.anchorLeft + placement.paneWidth / 2f
            val paneCenterY = anchor.top - placement.anchorTop + placement.paneHeight / 2f
            translationX = g.coerceAtMost(1f) * (paneCenterX - anchor.center.x)
            translationY = gc.coerceAtMost(1f) * (paneCenterY - anchor.center.y)
            val shrink = 1f - 0.06f * travel.coerceIn(0f, 1f)
            scaleX = shrink
            scaleY = shrink
          }
          .drawWithContent {
            // Un layer rilasciato non si ridisegna: se la lista sotto ha smontato la riga, la
            // finestra apre semplicemente senza immagine in uscita. Niente qui vale un crash.
            runCatching { drawLayer(outgoing) }
          },
      )
    }
  }

  Layout(
    // Sopra la tastiera (3.0.1), come il foglio e l'avviso: un pop-up con un campo di ricerca (il
    // selettore dei formati di universal_converter) le finiva sotto. Solo in basso, quindi l'origine resta
    // quella dell'host e l'ancora, la finestra e il ritaglio parlano le stesse coordinate; il pannello si
    // posa nello spazio che resta, e se non ci sta sotto l'ancora va sopra, come sempre.
    modifier = Modifier.fillMaxSize().imePadding(),
    content = {
      Column(
        modifier = Modifier
          .then(
            if (useMorphWindow) {
              // Il contenuto non esce mai dal vetro mentre si forma o si ritira (3.0): ritagliato sul
              // corpo della finestra, nelle coordinate del pannello. A riposo niente ritaglio.
              Modifier.graphicsLayer {
                val round = windowPhysics.travelClipIn(
                  Rect(placement.paneLeft, placement.paneTop, placement.paneLeft + 1f, placement.paneTop + 1f),
                  if (open()) minOf(growth(), growthCross()) else 0f,
                )
                if (round != null) {
                  this.shape = FluidPopoverTravelClipShape(round)
                  clip = true
                } else {
                  clip = false
                }
              }
            } else {
              Modifier
            },
          )
          .graphicsLayer {
            val g = growth()
            val gc = growthCross()
            val shrink = 1f - retreat() * 0.10f
            if (useMorphWindow) {
              // Il contenuto viaggia col contratto del menu che si espande — fluidPhysicsContent,
              // la ricetta gia' approvata — non con uno suo: TRASLA col centro corrente della
              // silhouette, si scala UNIFORME sulla taglia vera (min-dimension, con gli stessi
              // limiti 0.72..1.12), e la scritta si materializza nella seconda meta' del viaggio.
              // La versione precedente teneva il testo a taglia piena dall'inizio: sbordava dal
              // vetro ancora piccolo, ed era "il testo che appare in maniera strana".
              val travel = minOf(g, gc)
              // Subentra TARDI (45%..90% del viaggio): prima e' l'immagine della riga a viaggiare
              // — il titolo che trasla dentro la card — e solo poi si materializza il testo,
              // sotto. La staffetta delle due scritte e' cio' che toglie il popping del testo.
              // In chiusura se ne va PER PRIMO (3.0, il vetro liquido): dal 94% al 62% del ritorno,
              // poi la card si ritira vuota. Prima usciva con la finestra d'apertura rovesciata, e la
              // sua scala (mai sotto 0,9) non stava piu' nella sagoma che rientrava nella riga.
              alpha = presence() * if (open()) {
                ((travel - 0.45f) / 0.45f).coerceIn(0f, 1f)
              } else {
                ((travel - FluidMotion.LiquidContentCloseFrom) / (FluidMotion.LiquidContentCloseTo - FluidMotion.LiquidContentCloseFrom))
                  .coerceIn(0f, 1f)
              }
              val anchorCenterX = placement.anchorLeft + placement.anchorWidth / 2f
              val anchorCenterY = placement.anchorTop + placement.anchorHeight / 2f
              translationX = (1f - g.coerceAtMost(1f)) * (anchorCenterX - size.width / 2f)
              translationY = (1f - gc.coerceAtMost(1f)) * (anchorCenterY - size.height / 2f)
              val endMin = minOf(size.width, size.height)
              // Zoom di pochi punti percentuali, e NIENTE gonfiore del pop sul testo: la
              // trasformazione la racconta gia' l'immagine della riga in viaggio, e un
              // paragrafo che zooma da 0.72 gonfiando oltre l'1 era "il testo esplode".
              // Il pop resta al vetro; la scritta arriva in posa.
              val travelScale = if (endMin > 0f) {
                val startMin = minOf(placement.anchorWidth, placement.anchorHeight)
                (lerp(startMin, endMin, travel.coerceIn(0f, 1f)) / endMin).coerceIn(0.90f, 1f)
              } else {
                1f
              }
              scaleX = travelScale * shrink
              scaleY = travelScale * shrink
              transformOrigin = TransformOrigin(0.5f, 0.5f)
            } else {
              // L'opacita' cavalca la corsa, non l'orologio: vicino all'ancora — i primi istanti
              // di un'apertura, gli ultimi di una chiusura — il pannello e' trasparente, quindi
              // quando se ne va ha finito di dissolversi *prima* di arrivare sulla riga in cui
              // rientra, e le due cose non sono mai leggibili una sopra l'altra.
              alpha = presence() * ((minOf(g, gc) - 0.08f) / 0.27f).coerceIn(0f, 1f)
              scaleX = lerp(placement.startScaleX, 1f, g) * shrink
              scaleY = lerp(placement.startScaleY, 1f, gc) * shrink
              transformOrigin = TransformOrigin(placement.pivotX, placement.pivotY)
            }
            // F2.3b: la dissolvenza dell'inchiostro attorno al passaggio di lato (il contenuto sta in un nodo fratello
            // della finestra, quindi la fa lui): mai un fotogramma col testo del lato sbagliato.
            alpha *= popoverInk.state?.inkAlpha() ?: 1f
          }
          .then(
            // Il vetro lo disegna la finestra qui sopra; senza fondale, il ripiego opaco.
            if (backdrop == null) {
              Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            } else {
              Modifier
            },
          )
          .clip(shape)
          .semantics {
            paneTitle?.let { this.paneTitle = it }
            isTraversalGroup = true
          },
      ) {
        // **Il contenuto viaggia con la finestra, non compare dentro di lei.**
        //
        // Comparire era il difetto: il testo era gia' alla sua posizione finale e si accendeva mano
        // a mano che il bordo della finestra ci passava sopra, cioe' esattamente l'effetto di una
        // tendina tirata su un cartello gia' scritto. Non e' un'apertura, e' una rivelazione.
        //
        // Traslando dello stesso vettore della finestra, quello che si vede attraverso il buco resta
        // *sempre lo stesso pezzo di contenuto*: al primo fotogramma il titolo sta dove stava il
        // titolo della riga, e da li' scivola al suo posto insieme al bordo che lo scopre. In
        // chiusura la stessa corsa all'indietro riporta il titolo dentro la riga. E' il motivo per
        // cui l'apertura di un'app su iOS si legge come una cosa sola che si avvicina invece che
        // come due cose che si scambiano il posto.
        Column(
          modifier = Modifier
            // Il vetro del contenuto sta sul pannello: ovvio quando il pannello e' il genitore
            // (lo troverebbe da solo), necessario quando e' la finestra che si trasforma, che e'
            // un fratello. Vedi `glassContainer`.
            .glassContainer(paneGlass)
            .graphicsLayer {
              if (!placement.morphFromAnchor) return@graphicsLayer
              // Uniforme, e di pochi punti percentuali. Serve perche' il contenuto *cresca* col bordo
              // invece di stare fermo mentre una tendina lo scopre — ma con un fattore solo per i due
              // assi, che e' l'unica cosa che il testo non perdona.
              val zoom = lerp(FluidPopoverContentStartScale, 1f, growth())
              scaleX = zoom
              scaleY = zoom
              transformOrigin = TransformOrigin(
                if (size.width > 0f) {
                  ((placement.anchorLeft + placement.anchorWidth / 2f) / size.width).coerceIn(0f, 1f)
                } else {
                  0.5f
                },
                if (size.height > 0f) {
                  ((placement.anchorTop + placement.anchorHeight / 2f) / size.height).coerceIn(0f, 1f)
                } else {
                  0.5f
                },
              )
            },
        ) {
          CompositionLocalProvider(
            LocalFluidCanvasBackdrop provides paneGlass,
            LocalFluidCanvasIsGlass provides true,
          ) {
            GlassInkContent(popoverInk, LocalContentColor.current) { content() }
          }
        }
      }
    },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val available = (width - marginPx * 2f).coerceAtLeast(1f)
    val maxContentWidth = minOf(maxWidthPx, available).roundToInt().coerceAtLeast(1)

    val placeable = measurables.first().measure(
      constraints.copy(
        minWidth = minWidthPx.roundToInt().coerceIn(0, maxContentWidth),
        minHeight = 0,
        maxWidth = maxContentWidth,
        maxHeight = (height * FluidPopoverMaxHeightFraction - marginPx * 2f)
          .roundToInt()
          .coerceAtLeast(1),
      ),
    )

    val paneW = placeable.width.toFloat()
    val paneH = placeable.height.toFloat()
    val below: Boolean
    val x: Float
    val y: Float
    if (anchor == null) {
      below = true
      x = (width - paneW) / 2f
      y = (height - paneH) / 2f
    } else {
      val bottomLimit = height - navBarBottomPx
      val spaceBelow = bottomLimit - anchor.bottom - gapPx - marginPx
      val spaceAbove = anchor.top - gapPx - marginPx
      below = paneH <= spaceBelow || spaceBelow >= spaceAbove
      val maxX = (width - paneW - marginPx).coerceAtLeast(marginPx)
      // Centred on what was touched, then clamped to the screen. A pop-up pinned to the leading edge
      // of a full-width row looks like it belongs to the row text; pinned to its centre it looks like
      // it belongs to the touch.
      x = (anchor.center.x - paneW / 2f).coerceIn(marginPx, maxX)
      y = if (overAnchor) {
        // Sitting *on* the anchor: the edge the menu grows away from stays put, so the button does
        // not appear to jump the moment it unfolds.
        if (below) {
          anchor.top.coerceAtMost((bottomLimit - paneH - marginPx).coerceAtLeast(marginPx))
        } else {
          (anchor.bottom - paneH).coerceAtLeast(marginPx)
        }
      } else if (below) {
        (anchor.bottom + gapPx).coerceAtMost((bottomLimit - paneH - marginPx).coerceAtLeast(marginPx))
      } else {
        (anchor.top - gapPx - paneH).coerceAtLeast(marginPx)
      }
    }
    val pane = Rect(x, y, x + paneW, y + paneH)

    // The pivot is the point on the pop-up closest to what was touched, expressed as a fraction of
    // its own box. Growing from there is the difference between a pop-up that opens *out of* the
    // anchor and one that merely happens to be next to it.
    placement.pivotX = if (anchor == null || placeable.width <= 0) {
      0.5f
    } else {
      ((anchor.center.x - x) / paneW).coerceIn(0f, 1f)
    }
    placement.pivotY = if (anchor == null) 0.5f else if (below) 0f else 1f

    // **Una crescita sola, per ogni pop-up.** Sopra la propria ancora la finestra parte dal
    // rettangolo ESATTO dell'ancora e ne fa viaggiare il bordo (mai il pannello scalato, che
    // schiacciava il testo); accanto a una riga, o senza ancora, parte da un SEME — il pannello stesso
    // a 0,42 attorno al perno piu' vicino a cio' che e' stato toccato, o al proprio centro — e cresce
    // per geometria, non per un layer scalato sul vetro (3.0).
    val start = when {
      anchor != null && overAnchor -> anchor
      anchor != null -> fluidMenuSeedFrame(pane, Offset(x + placement.pivotX * paneW, y + placement.pivotY * paneH), FluidPopoverStartScale)
      else -> fluidMenuSeedFrame(pane, pane.center, FluidPopoverStartScale)
    }
    val startRadius = if (anchor != null && overAnchor) {
      anchorRadiusPx.coerceAtMost(anchor.minDimension / 2f)
    } else {
      paneRadiusPx * FluidPopoverStartScale
    }
    placement.morphFromAnchor = anchor != null && overAnchor && placeable.width > 0 && placeable.height > 0
    placement.anchorLeft = start.left - x
    placement.anchorTop = start.top - y
    placement.anchorWidth = start.width
    placement.anchorHeight = start.height
    placement.paneWidth = paneW
    placement.paneHeight = paneH
    placement.paneLeft = x.roundToInt().toFloat()
    placement.paneTop = y.roundToInt().toFloat()
    placement.startScaleX = FluidPopoverStartScale
    placement.startScaleY = FluidPopoverStartScale
    placement.startOffsetX = 0f
    placement.startOffsetY = 0f

    if (useMorphWindow && placeable.width > 0 && placeable.height > 0) {
      // La geometria della finestra si risolve QUI, dove si risolve tutto il resto: l'ancora com'e'
      // davvero, il pannello dov'e' davvero. L'orologio e' `growth` — le molle del modale.
      // La partenza e' il rettangolo ESATTO dell'ancora: partire ribassato dava una spinta verso
      // l'alto all'andata, ma al ritorno la finestra atterrava quattordici punti sotto la riga —
      // "non ritorna al punto originale", ed e' vero. La vita verticale la danno gia' le molle.
      //
      // Oltre l'1 la sagoma NON prosegue lungo il lerp: e' il pannello gonfiato attorno al centro, su
      // tutti i lati, con la molla grande quanto il viaggio e i fianchi tenuti dentro lo schermo —
      // `fluidMenuInflation`, coi numeri della rincorsa di questa finestra (0,6 e il picco 0,15).
      val inflation = fluidMenuInflation(
        paneW = paneW,
        paneH = paneH,
        availW = width.toFloat(),
        edgeMinPx = FluidPopoverPopEdgeMin.toPx(),
        refHeightPx = FluidPopoverPopReferenceHeight.toPx(),
        gain = FluidPopoverWindowPopGain,
        peakOvershoot = FluidPopoverPeakOvershoot,
      )
      windowPhysics.driveExternally(
        from = FluidForm.Slab(frame = start, cornerRadii = FluidCornerRadii.all(startRadius)),
        to = FluidForm.Slab(frame = pane, cornerRadii = FluidCornerRadii.all(paneRadiusPx)),
        progress = growth,
        overshootInflationX = inflation.x,
        overshootInflationY = inflation.y,
        startFrame = null,
      )
    }

    layout(width, height) { placeable.place(x.roundToInt(), y.roundToInt()) }
  }
}

/**
 * How small the pop-up starts.
 *
 * Not zero. Growing from nothing is a zoom, and a zoom has no material in it — the pop-up has to be
 * recognisably itself from the first frame and simply be *small*, the way the tab bar's pill is
 * always a pill however hard it is squashed.
 */
// Il moto della finestra ancorata (la rincorsa e le molle di assestamento), i tempi del pop-up e
// delle uscite sono token di `FluidMotion` (`PopoverWindowRunUp`, `PopoverWindowSettleX/Y`,
// `PopoverOpenX/Y`, `PopoverWindowExit`, `PopoverExit`, `Popover*Millis`, `SheetSettle`): stessi
// numeri di prima, e con il loro perche' accanto.

/**
 * Quanto dell'oltrepasso della molla diventa GONFIORE della card (via overshootInflation di
 * driveExternally): geometria, non scala del layer — scalare il layer zoomava anche il fondale
 * campionato dentro il vetro, "il testo dietro si muove". Su tutti i lati.
 */
private const val FluidPopoverWindowPopGain = 0.6f

/**
 * La taglia a cui il gonfiore vale [FluidPopoverWindowPopGain] esatto: un pannello piu' alto
 * arriva con piu' slancio e rimbalza di piu', uno piu' basso di meno — "la molla e' grande
 * quanto il viaggio". Il fattore e' bloccato in 0.55..1.6.
 */
private val FluidPopoverPopReferenceHeight = 240.dp

/** Sotto questa distanza dai bordi laterali il gonfiore non deve mai portare la card. */
private val FluidPopoverPopEdgeMin = 6.dp

/** Il picco atteso di (progress − 1) con la rincorsa e la molla di casa: taratura del tetto X. */
private const val FluidPopoverPeakOvershoot = 0.15f

private const val FluidPopoverStartScale = 0.42f

/** Quanto e' piccolo il contenuto quando il bordo comincia ad aprirsi. Pochi punti percentuali. */
private const val FluidPopoverContentStartScale = 0.90f

private val FluidPopoverMargin = 16.dp
private val FluidPopoverGap = 10.dp
// Largo abbastanza da far respirare un dettaglio con allegati su un tablet; su un telefono e' la
// larghezza dello schermo meno i margini a decidere, quindi il numero grande non cambia niente li'.
private val FluidPopoverMaxWidth = 620.dp

/**
 * Il pop-up ANCORATO e' una card, non una fascia: un po' piu' stretto della riga da cui nasce,
 * cosi' il morph ha una componente laterale — ma di POCO. A 320 la strizzata dominava e il
 * pannello sembrava rimpicciolirsi invece di crescere; la crescita la deve raccontare l'altezza.
 */
private val FluidPopoverAnchoredMaxWidth = 360.dp

private const val FluidPopoverMaxHeightFraction = 0.78f

/**
 * The pop-up's corner, and it is [FluidRadius.Group] on purpose.
 *
 * A pop-up that grows out of a row is growing out of a *grouped list*, and it starts at that row's
 * rectangle: if its radius were its own number, the first frame would be the row with the wrong
 * corners and the animation would be spent correcting them. Sharing the group's radius means the
 * corner the pop-up starts with is the corner it was standing on.
 */
internal val FluidPopoverRadius = FluidRadius.Group

private val FluidContextMenuMinWidth = 210.dp
private val FluidContextMenuMaxWidth = 300.dp
