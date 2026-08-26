package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface

/**
 * Il tasto che SI ESPANDE nel proprio menù contestuale — la prima integrazione vera di
 * Fluid-physics nell'interfaccia.
 *
 * Il predecessore ([dev.antigravity.fluidengine.ui.fluid.FluidGlassMenuButton]) fa comparire il
 * pannello *sopra* il tasto: due superfici, una staffetta. Qui la superficie è UNA: la capsula del
 * tasto viaggia — col viaggio della casa: parte piano, accelera, rimbalza — fino a essere il
 * pannello del menù, e al congedo torna a essere il tasto. Il contenuto rispetta il contratto:
 * l'etichetta del tasto se ne va con l'inizio del viaggio, le voci del menù arrivano attaccate
 * alla superficie, niente si stira mai.
 *
 * Architettura portale/host come i modali della casa: l'ancora ([FluidMorphMenuButton]) vive nel
 * layout e riporta i propri limiti; la superficie che si trasforma vive in un [FluidMorphMenuHost]
 * montato alla radice — un menù deve crescere oltre i limiti del proprio genitore, e l'unico posto
 * dove il vetro può farlo restando vetro è dentro la composizione, non in una finestra.
 */
@Stable
class FluidMorphMenuState internal constructor() {

  internal var anchorBounds by mutableStateOf<Rect?>(null)
  internal var anchorText by mutableStateOf<String?>(null)
  internal var anchorIcon by mutableStateOf<ImageVector?>(null)
  internal var actions by mutableStateOf<List<FluidContextAction>>(emptyList())
  internal var panelFrame by mutableStateOf<Rect?>(null)

  /** Vero dal gesto d'apertura alla richiesta di congedo. */
  var isOpen by mutableStateOf(false)
    internal set

  /** Vero finché c'è qualcosa in scena, viaggio di ritorno compreso: l'ancora resta nascosta. */
  var isOnScreen by mutableStateOf(false)
    internal set

  internal val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))

  /** Chiede il viaggio di ritorno. Il tasto riappare quando la superficie è tornata capsula. */
  fun dismiss() {
    isOpen = false
  }

  internal fun open(bounds: Rect, text: String?, icon: ImageVector?, items: List<FluidContextAction>) {
    if (items.isEmpty() || isOnScreen) return
    anchorBounds = bounds
    anchorText = text
    anchorIcon = icon
    actions = items
    isOpen = true
  }
}

@Composable
fun rememberFluidMorphMenuState(): FluidMorphMenuState = remember { FluidMorphMenuState() }

/**
 * L'ancora: una capsula di vetro coi gesti del predecessore — tocco per l'azione, tenuto premuto
 * diventa il menù. Mentre il menù è in scena l'ancora non disegna niente: al suo posto, allo
 * stesso pixel, sta la superficie dell'host — la stessa capsula, che parte per il viaggio.
 */
@Composable
fun FluidMorphMenuButton(
  state: FluidMorphMenuState,
  actions: () -> List<FluidContextAction>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null,
  icon: ImageVector? = null,
  backdrop: GlassBackdropState,
  enabled: Boolean = true,
) {
  var bounds by remember { mutableStateOf<Rect?>(null) }
  val contentColor = MaterialTheme.colorScheme.onSurface

  Row(
    modifier = modifier
      .onGloballyPositioned { bounds = it.boundsInRoot() }
      // Sparizione secca, non dissolvenza: la superficie dell'host appare nello stesso fotogramma
      // allo stesso posto con la stessa sagoma — è un cambio di proprietario, non un'uscita.
      .graphicsLayer { alpha = if (state.isOnScreen) 0f else 1f }
      // interactive = false, deliberato: il lampo bianco del tocco scattava anche su un dito che
      // voleva solo scorrere. Il feedback di questo tasto è una scala leggera al press — e il
      // morph stesso: un tasto che diventa un pannello non ha bisogno di annunciarsi.
      .glassControlSurface(backdrop = backdrop, interactive = false)
      .fluidPressable(
        onClick = onClick,
        onLongClick = {
          bounds?.let { state.open(it, text, icon, actions()) }
        },
        enabled = enabled,
        role = Role.Button,
      )
      // Stessa altezza e stesso passo orizzontale di FluidGlassButton: l'ancora È quel tasto,
      // con un mestiere in più.
      .height(44.dp)
      .padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MorphMenuAnchorLabel(text = text, icon = icon, color = contentColor)
  }
}

/**
 * L'host: da montare alla radice, sopra il contenuto e sotto niente. Non compone nulla finché non
 * c'è un menù in scena.
 */
@Composable
fun FluidMorphMenuHost(
  state: FluidMorphMenuState,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
) {
  state.physics.reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  if (!state.isOnScreen && !state.isOpen) return

  val density = LocalDensity.current
  var hostBounds by remember { mutableStateOf(Rect.Zero) }
  val rowHeightPx = with(density) { MorphMenuRowHeight.toPx() }
  val panelWidthPx = with(density) { MorphMenuPanelWidth.toPx() }
  val panelPaddingPx = with(density) { MorphMenuPanelPadding.toPx() }
  val gapPx = with(density) { MorphMenuAnchorGap.toPx() }
  val marginPx = with(density) { MorphMenuEdgeMargin.toPx() }
  val groupRadiusPx = with(density) { FluidRadius.Group.toPx() }

  // Il conducente del viaggio: apre quando isOpen arriva, riporta a capsula quando se ne va.
  LaunchedEffect(state.isOpen, hostBounds) {
    val anchor = state.anchorBounds ?: return@LaunchedEffect
    if (hostBounds.width <= 0f) return@LaunchedEffect
    val local = anchor.translate(-hostBounds.left, -hostBounds.top)
    val capsule = FluidForm.Slab(local, FluidCornerRadii.all(local.minDimension / 2f))
    if (state.isOpen) {
      state.isOnScreen = true
      val panel = solveMorphMenuPanel(
        anchor = local,
        host = Rect(0f, 0f, hostBounds.width, hostBounds.height),
        itemCount = state.actions.size,
        rowHeightPx = rowHeightPx,
        panelWidthPx = panelWidthPx,
        panelPaddingPx = panelPaddingPx,
        gapPx = gapPx,
        marginPx = marginPx,
      )
      state.panelFrame = panel
      state.physics.snapTo(capsule)
      // NON il viaggio della casa: la rincorsa l'ha gia' pagata il dito — quattrocento
      // millisecondi di pressione sono la partenza lenta. Da li' la superficie deve ARRIVARE:
      // molla sola, sottosmorzata, che parte alla velocita' massima e rimbalza sul traguardo.
      // Il viaggio completo resta ai morph avviati da un tocco secco, dove la partenza lenta
      // si vede davvero (il Playground, promosso cosi' com'e').
      state.physics.morphTo(
        FluidForm.Slab(panel, FluidCornerRadii.all(groupRadiusPx)),
        spring(dampingRatio = 0.6f, stiffness = 400f, visibilityThreshold = 0.001f),
      )
    } else if (state.isOnScreen) {
      // Ritorno di servizio: rapido e senza rimbalzo — un congedo che rimbalza trattiene.
      state.physics.morphTo(capsule, FluidMotion.snappy())
      state.isOnScreen = false
      state.panelFrame = null
    }
  }

  BackHandler(enabled = state.isOpen) { state.dismiss() }

  Box(
    modifier = modifier
      .fillMaxSize()
      .onGloballyPositioned { hostBounds = it.boundsInRoot() },
  ) {
    // L'acchiappa-tocco: la pagina resta a colori — è il bordo del vetro a separare, non un velo.
    if (state.isOpen) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(state) { detectTapGestures(onTap = { state.dismiss() }) },
      )
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .fluidPhysicsSurface(
          state = state.physics,
          backdrop = backdrop,
          tint = GlassDefaults.floatingTint(),
          role = GlassRole.Modal,
          optics = GlassDefaults.optics(GlassRole.Modal).copy(
            blurScale = 2.5f,
            shadowRadius = 0.dp,
            shadowAlpha = 0f,
          ),
          // La pellicola è parte del viaggio: capsula col colore del tasto, pannello col colore
          // del menù — e al ritorno la strada inversa, così il tasto riappare senza salti di
          // colore nell'ultimo fotogramma.
          tintFrom = GlassDefaults.controlTint(),
          tintBlend = {
            val t = state.physics.progress.coerceIn(0f, 1f)
            if (state.isOpen) t else 1f - t
          },
        ),
    )

    // L'etichetta del tasto: parte col viaggio d'andata, torna con quello di ritorno.
    val anchor = state.anchorBounds?.translate(-hostBounds.left, -hostBounds.top)
    if (anchor != null) {
      MorphMenuFrameBox(frame = anchor) {
        Row(
          modifier = Modifier.fluidPhysicsContent(
            state.physics,
            if (state.isOpen) FluidPhysicsContentRole.Outgoing else FluidPhysicsContentRole.Incoming,
          ),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          MorphMenuAnchorLabel(
            text = state.anchorText,
            icon = state.anchorIcon,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

    // Le voci del menù: arrivano attaccate alla superficie, se ne vanno per prime al congedo.
    val panel = state.panelFrame
    if (panel != null) {
      MorphMenuFrameBox(frame = panel) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .fluidPhysicsContent(
              state.physics,
              if (state.isOpen) FluidPhysicsContentRole.Incoming else FluidPhysicsContentRole.Outgoing,
            )
            .padding(vertical = MorphMenuPanelPadding),
          verticalArrangement = Arrangement.Top,
        ) {
          state.actions.forEachIndexed { index, action ->
            if (index > 0) {
              // La riga sottile che separa le voci: la stessa grammatica delle liste raggruppate.
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 18.dp)
                  .height(0.5.dp)
                  .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
              )
            }
            MorphMenuRow(
              action = action,
              onPicked = {
                state.dismiss()
                action.onClick()
              },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MorphMenuAnchorLabel(text: String?, icon: ImageVector?, color: androidx.compose.ui.graphics.Color) {
  if (icon != null) {
    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
  }
  if (text != null) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
      color = color,
    )
  }
}

@Composable
private fun MorphMenuRow(action: FluidContextAction, onPicked: () -> Unit) {
  val tone = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(MorphMenuRowHeight)
      .fluidPressable(onClick = onPicked, enabled = action.enabled, role = Role.Button)
      .padding(horizontal = 18.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (action.icon != null) {
      Icon(imageVector = action.icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
    }
    Text(
      text = action.label,
      style = MaterialTheme.typography.bodyLarge,
      color = tone,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun MorphMenuFrameBox(frame: Rect, content: @Composable () -> Unit) {
  val density = LocalDensity.current
  Box(
    modifier = Modifier
      .offset { IntOffset(frame.left.toInt(), frame.top.toInt()) }
      .size(
        width = with(density) { frame.width.toDp() },
        height = with(density) { frame.height.toDp() },
      ),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

/**
 * Dove sta il pannello: sopra l'ancora se lo spazio basta — un menù che nasce da un tasto in
 * basso cresce verso l'alto — altrimenti sotto, sempre dentro i margini dell'host, col centro
 * orizzontale il più vicino possibile al tasto. Pura e testabile.
 */
internal fun solveMorphMenuPanel(
  anchor: Rect,
  host: Rect,
  itemCount: Int,
  rowHeightPx: Float,
  panelWidthPx: Float,
  panelPaddingPx: Float,
  gapPx: Float,
  marginPx: Float,
): Rect {
  val width = panelWidthPx.coerceAtMost(host.width - marginPx * 2f)
  val height = itemCount * rowHeightPx + panelPaddingPx * 2f
  val left = (anchor.center.x - width / 2f).coerceIn(marginPx, host.width - marginPx - width)
  val fitsAbove = anchor.top - gapPx - height >= marginPx
  val top = if (fitsAbove) {
    anchor.top - gapPx - height
  } else {
    (anchor.bottom + gapPx).coerceAtMost(host.height - marginPx - height)
  }
  return Rect(left, top, left + width, top + height)
}

private val MorphMenuRowHeight = 48.dp
private val MorphMenuPanelWidth = 252.dp
private val MorphMenuPanelPadding = 8.dp
private val MorphMenuAnchorGap = 12.dp
private val MorphMenuEdgeMargin = 16.dp
