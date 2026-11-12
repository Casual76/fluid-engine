package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.antigravity.fluidengine.ui.glass.field.GlassPaintedScrim
import dev.antigravity.fluidengine.ui.glass.field.LocalGlassPaintedPanel
import dev.antigravity.fluidengine.ui.glass.field.glassPainted
import dev.antigravity.fluidengine.ui.glass.field.glassPaintedPanelBody
import dev.antigravity.fluidengine.ui.glass.field.glassPaintedSeen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A modal sheet.
 *
 * Material's sheet is kept underneath for the drag gesture, the predictive-back handling and the
 * accessibility wiring — all of which are genuinely hard to redo — but nothing it draws survives:
 * the corners are continuous and much larger, the grabber is a short capsule rather than Material's
 * wide bar, and the scrim is darker so the sheet reads as being in front of the app rather than
 * merely on top of it.
 *
 * **What it cannot do is glass.** `ModalBottomSheet` owns a separate platform window, and a separate
 * window cannot read the app's `GraphicsLayer` — so this falls back to [fluidStaticGlassSurface],
 * which paints gradients. That is a system boundary and not a defect, but it does mean a sheet is
 * now the one surface in the interface made of a different material from everything around it.
 * [FluidGlassModalPortal] lives inside the app's composition and does not have the problem.
 *
 * **So with a host it is the glass sheet (3.0, fase 9).** When the app has a [FluidGlassModalHost]
 * at its root (`LocalFluidGlassModalHostState`) this does not open a platform window at all: it asks
 * the host for the glass sheet ([FluidGlassModalPresentation.Sheet]) and the content stands on a
 * refracting pane instead of a painted copy, in the one material every other sheet has. What goes
 * with the window is what the window was: [sheetState] and [modifier] are ignored (the glass sheet
 * has its own drag, its own grabber and its own insets), and [showGrabber] is too — the glass sheet
 * always carries its grabber. On dismissal (the scrim, back, a drag down) the sheet leaves with the
 * animation of the host and **only then** calls [onDismissRequest], the order the platform sheet
 * kept: whoever takes it out of composition sooner loses the exit, and nothing else.
 *
 * Without a host there is nowhere to draw it, and it is the platform sheet it has always been — still
 * right for anything that genuinely needs its own window.
 */
@Deprecated(
  message = "Un pop-up in vetro deve stare dentro la composizione dell'app: usa " +
    "FluidGlassModalPortal, che rifrange la pagina invece di dipingerne una copia. FluidSheet " +
    "resta per i casi che hanno davvero bisogno di una finestra di piattaforma separata.",
  replaceWith = ReplaceWith("FluidGlassModalPortal"),
  level = DeprecationLevel.WARNING,
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  title: String? = null,
  showGrabber: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  // Un host nella radice: il foglio e' quello di vetro, dentro la composizione. Il ramo e' deciso da
  // quello che c'e' in scope e non cambia mentre il foglio e' aperto (l'host sta alla radice).
  if (LocalFluidGlassModalHostState.current != null) {
    FluidSheetInHost(
      onDismissRequest = onDismissRequest,
      title = title,
      content = content,
    )
    return
  }
  val sheetShape = ContinuousCornerShape(topStart = FluidRadius.Sheet, topEnd = FluidRadius.Sheet)
  val darkSide = GlassDefaults.isDarkSurface()
  val page = MaterialTheme.colorScheme.background
  // Il materiale del dipinto, con la regola di ogni altro vetro: sotto Android 13 e con `Classic` il foglio della 3.x,
  // intero (contenitore al 92%, scrim al 42%, il dipinto di prima, nessuna ombra). Mai un ibrido.
  val liquid = rememberPaintedGlassLiquid(LocalFluidGlassModel.current)
  // Il vetro dipinto del foglio (F1B.5, `glass/field/GlassPaintedProfile.kt`): il corpo e' il colore pieno che il
  // foglio di vetro ha sopra la pagina del tema sotto lo scrim, e lo dipinge il contenitore, che copre anche la
  // maniglia. Prima il contenitore era la superficie al 92%: dal foglio si leggeva la pagina dietro, nitida.
  val body = remember(page, darkSide) { glassPaintedPanelBody(page, darkSide) }
  val seen = remember(page, darkSide) { glassPaintedSeen(page, darkSide) }
  // Dove comincia il contenuto sotto la maniglia: il bordo dipinto sale fin li', alla cima vera del foglio.
  var contentTop by remember { mutableFloatStateOf(0f) }
  val bottomBleed = with(LocalDensity.current) { SheetBottomBleed.toPx() }
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    // L'ombra del vetro, fuori dal ritaglio del foglio (il `modifier` sta prima della `Surface` del foglio). Non
    // segue lo scatto del gesto indietro predittivo, che Material applica dentro: si vede appena, sopra lo scrim.
    modifier = if (liquid) modifier.glassShadow(sheetShape, GlassDefaults.panelOptics()) else modifier,
    sheetState = sheetState,
    shape = sheetShape,
    // ModalBottomSheet owns a separate platform window, so it cannot sample a FluidScreen's
    // GraphicsLayer: the painted glass below stands in for it; the live-backdrop path remains
    // reserved for in-root overlays.
    containerColor = if (liquid) body else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    contentColor = MaterialTheme.colorScheme.onSurface,
    scrimColor = if (liquid) GlassPaintedScrim.color(darkSide) else Color.Black.copy(alpha = ClassicSheetScrimAlpha),
    tonalElevation = 0.dp,
    dragHandle = if (showGrabber) {
      { FluidGrabber() }
    } else {
      null
    },
    contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
  ) {
    Column(
      modifier = if (liquid) {
        Modifier
          .fillMaxWidth()
          .onPlaced { contentTop = it.positionInParent().y }
          // Filo, riflesso e ombreggiatura sulla sagoma di tutto il foglio: dalla cima (sopra la maniglia) a oltre
          // il fondo, che sta a filo dello schermo e non ha bordo. Il corpo lo dipinge il contenitore.
          .glassPainted(
            shape = sheetShape,
            darkSide = darkSide,
            seen = seen,
            largeVeil = 1f,
            drawBody = false,
            extendTop = { contentTop },
            extendBottomPx = bottomBleed,
          )
      } else {
        // Il foglio di prima: il dipinto della 3.x sotto la maniglia (lo sceglie lo stesso `fluidStaticGlassSurface`).
        Modifier
          .fillMaxWidth()
          .fluidStaticGlassSurface(shape = sheetShape)
      },
    ) {
      CompositionLocalProvider(LocalGlassPaintedPanel provides if (liquid) body else null) {
        if (title != null) {
          Text(
            text = title,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 20.dp, vertical = 4.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
          )
        }
        content()
      }
    }
  }
}

/** Lo scrim del foglio di piattaforma col vetro di prima ([FluidGlassModel.Classic]): quello della 3.x. */
private const val ClassicSheetScrimAlpha = 0.42f

/**
 * Di quanto la sagoma dipinta del foglio scende oltre il suo fondo: piu' del filo, della riga chiara, dell'alone e
 * dell'ombreggiatura (7 dp di rientro, 22 di ombreggiatura), cosi' il fondo a filo dello schermo non ha un bordo.
 */
private val SheetBottomBleed = 48.dp

/**
 * Il foglio di [FluidSheet] nell'host: la presentazione `Sheet` del portale, per la durata della
 * composizione di chi lo chiama.
 *
 * Chi chiama [FluidSheet] ne ha il contratto "composto = aperto", e il portale vuole un `visible`:
 * il ponte e' uno stato locale. Alla richiesta di chiusura (scrim, indietro, trascinamento) lo stato
 * passa a "chiuso", il foglio esce con l'animazione dell'host, e **a uscita finita** si chiama
 * [onDismissRequest] — che e' l'ordine del foglio di piattaforma, e cio' che permette al chiamante di
 * togliere il foglio dalla composizione senza che l'uscita diventi un taglio. Se e' il chiamante a
 * toglierlo da solo (un tasto dentro il foglio che spegne il suo stato) il portale si ritira subito:
 * uscita secca, come la `Dialog` di prima.
 */
@Composable
private fun FluidSheetInHost(
  onDismissRequest: () -> Unit,
  title: String?,
  content: @Composable ColumnScope.() -> Unit,
) {
  val scope = rememberCoroutineScope()
  val currentDismiss by rememberUpdatedState(onDismissRequest)
  var open by remember { mutableStateOf(true) }

  FluidGlassModalPortal(
    visible = open,
    onDismissRequest = {
      // Una volta sola: lo scrim, indietro e il trascinamento possono arrivare nello stesso istante.
      if (open) {
        open = false
        scope.launch {
          delay(FluidSheetExitMillis)
          currentDismiss()
        }
      }
    },
    presentation = FluidGlassModalPresentation.Sheet,
    paneTitle = title,
  ) {
    if (title != null) {
      Text(
        text = title,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 4.dp),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
      )
    }
    content()
  }
}

/**
 * Quanto aspetta [FluidSheetInHost] prima di dire al chiamante che il foglio e' chiuso: l'uscita del
 * foglio dell'host (dissolvenza di 240 ms, molla critica a 420) e' finita, o quasi, a questo punto.
 */
private const val FluidSheetExitMillis = 300L

/**
 * The grabber.
 *
 * 36 x 5, centred, at 18% of the text colour. Material's default is nearly twice as wide and sits in
 * a much taller strip, which is the first thing that gives a sheet away.
 */
@Composable
fun FluidGrabber(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(top = 8.dp, bottom = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(width = 36.dp, height = 5.dp)
        .clip(FluidCapsuleShape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
    )
  }
}

/** One button in a [FluidAlert]. */
data class FluidAlertAction(
  val label: String,
  val onClick: () -> Unit,
  val emphasis: Emphasis = Emphasis.Normal,
  val enabled: Boolean = true,
) {
  enum class Emphasis { Normal, Preferred, Destructive }
}

/**
 * An alert.
 *
 * Centred, narrow, and built out of hairlines rather than padding — the buttons are cells in a grid
 * that reaches the edges of the card, not floating pills in a corner. Material puts its buttons at
 * the bottom right; that single difference is enough to place a dialog on the wrong platform.
 *
 * Two actions sit side by side, three or more stack, exactly as UIKit does it, because at three the
 * labels stop fitting on one line.
 *
 * **Dentro l'host (3.0, fase 7).** Con un [FluidGlassModalHost] in scope l'avviso non apre piu' una
 * finestra di piattaforma: sta nell'host, e il vetro e' vero — la pagina rifratta dietro, il materiale
 * dei pannelli (`panelOptics`), l'arrivo che si posa come su iOS e l'uscita che si vede anche dopo che
 * il chiamante l'ha tolto dalla composizione. I pulsanti hanno la goccia e l'aptica dei menu (un Tick
 * per pulsante attraversato, un Tap alla scelta). Senza host, o con [inHost] falso, il Dialog di
 * sempre: per un avviso che deve stare sopra le finestre di piattaforma (un foglio, una tastiera).
 *
 * @param inHost falso per restare nella finestra di piattaforma anche con l'host. Dalla 3.0.
 */
@Composable
fun FluidAlert(
  onDismissRequest: () -> Unit,
  title: String,
  modifier: Modifier = Modifier,
  message: String? = null,
  actions: List<FluidAlertAction>,
  inHost: Boolean = true,
  content: (@Composable ColumnScope.() -> Unit)? = null,
) {
  val host = LocalFluidGlassModalHostState.current
  if (inHost && host != null) {
    FluidAlertInHost(
      host = host,
      onDismissRequest = onDismissRequest,
      spec = FluidAlertSpec(
        title = title,
        message = message,
        actions = actions,
        content = content,
        modifier = modifier,
      ),
    )
    return
  }
  FluidAlertDialog(onDismissRequest, title, modifier, message, actions, content)
}

/**
 * L'avviso nell'host: una voce dell'host che dura quanto la composizione del chiamante, e un po' di
 * piu' — al `onDispose` si ritira (`retire`) invece di sparire, e la toglie il layer a uscita finita.
 */
@Composable
private fun FluidAlertInHost(
  host: FluidGlassModalHostState,
  onDismissRequest: () -> Unit,
  spec: FluidAlertSpec,
) {
  val entry = remember { FluidGlassModalEntry() }
  SideEffect {
    entry.alert = spec
    entry.onDismissRequest = onDismissRequest
    entry.paneTitle = spec.title
  }
  DisposableEffect(host, entry) {
    // Prima della registrazione, non dopo: il layer sceglie che cosa disegnare da `alert`, e una voce
    // registrata senza sarebbe per un fotogramma un pop-up vuoto.
    entry.alert = spec
    entry.presentation = FluidGlassModalPresentation.Popover
    entry.onDismissRequest = onDismissRequest
    entry.paneTitle = spec.title
    entry.retiring = false
    entry.openedAt = host.nextSequence()
    entry.visible = true
    host.register(entry)
    onDispose { host.retire(entry) }
  }
}

@Composable
private fun FluidAlertDialog(
  onDismissRequest: () -> Unit,
  title: String,
  modifier: Modifier,
  message: String?,
  actions: List<FluidAlertAction>,
  content: (@Composable ColumnScope.() -> Unit)?,
) {
  val scheme = MaterialTheme.colorScheme
  var visible by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { visible = true }
  // The card comes in slightly oversized and settles. iOS scales *down* into place from 1.12; the
  // effect reads as the alert arriving from in front of the screen rather than growing out of it.
  val scale by animateFloatAsState(
    targetValue = if (visible) 1f else 1.12f,
    animationSpec = FluidMotion.snappy(),
    label = "alert scale",
  )
  val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    animationSpec = FluidMotion.fadeIn(140),
    label = "alert alpha",
  )

  // The buttons are the only way out of an alert, so they are the one part that may never be
  // pushed off the screen. An unbounded content column did exactly that: a long enough message —
  // an app changelog, say — grew the card past the display and left the actions unreachable, which
  // turns an alert into a trap. The card is now bounded to the window, the message scrolls inside
  // whatever is left over, and the actions are measured first so they always have their place.
  val windowHeight = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.height.toDp()
  }
  val darkSide = GlassDefaults.isDarkSurface()
  val page = scheme.background
  // Come il foglio: con `Classic` (e sotto Android 13) l'avviso della 3.x, col velo di piattaforma della `Dialog`.
  val liquid = rememberPaintedGlassLiquid(LocalFluidGlassModel.current)
  val body = remember(page, darkSide) { glassPaintedPanelBody(page, darkSide) }
  val seen = remember(page, darkSide) { glassPaintedSeen(page, darkSide) }
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    // Lo scrim dei pannelli dipinti (F1B.5): lo stesso nero del foglio di piattaforma e dei modali di vetro, al
    // posto del velo di piattaforma della `Dialog`.
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
    // Il velo di piattaforma della finestra com'era prima che lo toccassimo: letto in composizione, alla prima, prima
    // di ogni `SideEffect`. Con `Classic` si rimette quello, anche se il modello cambia con la `Dialog` aperta (C, R12).
    val platformDim = remember(dialogWindow) { dialogWindow?.attributes?.dimAmount }
    SideEffect {
      val window = dialogWindow ?: return@SideEffect
      window.setDimAmount(paintedDialogDim(liquid, darkSide, platformDim ?: window.attributes.dimAmount))
    }
    val alertShape = ContinuousCornerShape(FluidRadius.Card)
    Column(
      modifier = modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          this.alpha = alpha
        }
        // L'ombra del vetro, fuori dal ritaglio. Il layer qui sopra scala un dipinto, non un vetro: si puo'.
        .then(if (liquid) Modifier.glassShadow(alertShape, GlassDefaults.panelOptics()) else Modifier)
        .widthIn(max = 280.dp)
        .heightIn(max = windowHeight * AlertMaxHeightFraction)
        .clip(alertShape)
        // Dialog is another platform window: use the painted glass (F1B.5, the same material as the
        // glass alert in the host, painted) rather than pretending the screen's live sample can
        // cross that boundary. With `Classic`, the painted glass of 3.x over its 92% fill.
        .then(
          if (liquid) {
            Modifier.glassPainted(shape = alertShape, darkSide = darkSide, seen = seen, largeVeil = 1f)
          } else {
            Modifier
              .background(scheme.surfaceContainerHigh.copy(alpha = 0.92f))
              .fluidStaticGlassSurface(shape = alertShape)
          },
        ),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          // `fill = false` is what keeps a short alert the size of its content: it takes the space
          // the actions left over only if it needs it, rather than always stretching to the cap.
          .weight(1f, fill = false)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp)
          .padding(top = 20.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          color = scheme.onSurface,
        )
        message?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = scheme.onSurface,
          )
        }
        // Il contenuto dell'app sta sulla card, non sulla pagina del chiamante (3.0.1): la finestra
        // eredita il suo `LocalContentColor`, che fuori da una `Surface` vale nero. Un vetro dipinto
        // dentro sa di stare sul corpo dell'avviso (F1B.5).
        content?.let { slot ->
          CompositionLocalProvider(
            LocalContentColor provides scheme.onSurface,
            LocalGlassPaintedPanel provides if (liquid) body else null,
          ) { slot(this) }
        }
      }

      val hairline = scheme.onSurface.copy(alpha = 0.12f)
      val onAction: (FluidAlertAction) -> Unit = { action -> action.onClick() }

      if (actions.size == 2) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(hairline))
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
          AlertButton(actions[0], Modifier.weight(1f)) { onAction(actions[0]) }
          Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(hairline))
          AlertButton(actions[1], Modifier.weight(1f)) { onAction(actions[1]) }
        }
      } else {
        actions.forEach { action ->
          Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(hairline))
          AlertButton(action, Modifier.fillMaxWidth()) { onAction(action) }
        }
      }
    }
  }
}

/**
 * Il velo della `Dialog` di [FluidAlert] fuori dall'host: lo scrim dei pannelli dipinti col materiale nuovo, il velo di
 * piattaforma [platformDim] (quello che la finestra aveva) col vetro di prima.
 */
internal fun paintedDialogDim(liquid: Boolean, darkSide: Boolean, platformDim: Float): Float =
  if (liquid) GlassPaintedScrim.alpha(darkSide) else platformDim

/** How much of the window an alert may occupy before its message starts scrolling instead. */
private const val AlertMaxHeightFraction = 0.86f

@Composable
private fun AlertButton(
  action: FluidAlertAction,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val color = when (action.emphasis) {
    FluidAlertAction.Emphasis.Destructive -> scheme.error
    else -> scheme.primary
  }
  Box(
    modifier = modifier
      .fluidRowPressable(onClick = onClick, enabled = action.enabled)
      .alpha(if (action.enabled) 1f else 0.35f)
      .heightIn(min = 44.dp)
      .padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = action.label,
      style = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = if (action.emphasis == FluidAlertAction.Emphasis.Preferred) {
          FontWeight.SemiBold
        } else {
          FontWeight.Normal
        },
      ),
      color = color,
      textAlign = TextAlign.Center,
      maxLines = 2,
    )
  }
}
