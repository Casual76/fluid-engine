package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.field.glassOpaquePanel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription

// Il foglio dal basso e la pagina intera: il pannello che arriva viaggiando e si congeda col dito, con la
// barra in cima della pagina intera. Spezzato da `FluidGlassModal.kt` in F5A.0; lo chiama
// `FluidGlassModalPaneLayer` (`FluidGlassModalHost.kt`).

/**
 * The bottom-anchored variant, for content tall enough to want the whole height.
 *
 * The only presentation whose edge is against the screen rather than against the thing that opened
 * it, and therefore the only one that behaves like a sheet: it travels in from below on a spring,
 * and it can be **pushed back out with a finger, from anywhere on it**.
 *
 * "From anywhere" is the part that takes work, because a sheet is allowed to contain a list and a
 * downward drag is then ambiguous. It is resolved the way Material resolves it, with nested scroll:
 * the content gets the gesture first and keeps it for as long as it has somewhere to scroll; the
 * moment it runs out — [NestedScrollConnection.onPostScroll] reporting leftover downward travel —
 * the sheet takes the remainder and starts moving. Letting go hands the fling to whichever of the
 * two is holding the gesture. A sheet with nothing scrollable in it never reaches that negotiation
 * and is simply dragged.
 */
@Composable
internal fun FluidGlassModalSheet(
  paneTitle: String?,
  paneTint: Color?,
  /** Il fondo della pagina intera, se l'app ne ha dato uno: vedi `FluidGlassModalPortal(pageColor)`. */
  pageColor: Color?,
  backdrop: GlassBackdropState?,
  slide: () -> Float,
  backProgress: () -> Float,
  reducedMotion: Boolean,
  /** [FluidGlassModalPresentation.FullScreen]: tutta l'altezza, la barra in cima, lo scorrimento. */
  fullScreen: Boolean,
  onDismiss: () -> Unit,
  footer: (@Composable ColumnScope.() -> Unit)?,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  // Il foglio registra se stesso come il pop-up (2.13.0): una card di vetro, un interruttore, un
  // tasto messi nel foglio rifrangono il foglio, non la pagina dietro lo scrim. Registrato solo se
  // dentro c'e' del vetro che lo campiona.
  val sheetGlass = rememberGlassBackdrop()
  // F2.3b: il foglio possiede il suo contenuto e scurisce in continuo (un grande, `Modal`); il contenuto lo segue.
  val sheetInk = rememberGlassInk()
  // Senza fondo il foglio e' pieno (4.0.0, F1A.13): col materiale nuovo pubblica il proprio colore e fa da
  // contenitore, come la notifica senza fondo, perche' il vetro che ci sta dentro sia vetro su vetro e non un
  // tasto che campiona il niente. La pagina intera no: e' una pagina, e un tasto ci sta sopra come su una pagina.
  val sheetFill = if (backdrop == null && !fullScreen) rememberGlassOpaquePanel() else null
  val dismissThresholdPx = with(density) { FluidSheetDismissTravel.toPx() }
  val flingDismissPx = with(density) { FluidSheetDismissVelocity.toPx() }

  // Plain state, not an `Animatable`: nested-scroll callbacks are not suspending, and a drag has to
  // land on the same frame as the finger. The animatable only comes out to settle it.
  var dragPx by remember { mutableFloatStateOf(0f) }
  var height by remember { mutableFloatStateOf(0f) }

  fun settle(velocity: Float) {
    val shouldDismiss = dragPx > dismissThresholdPx || velocity > flingDismissPx
    if (shouldDismiss) {
      onDismiss()
      return
    }
    scope.launch {
      val from = dragPx
      androidx.compose.animation.core.animate(
        initialValue = from,
        targetValue = 0f,
        initialVelocity = velocity,
        animationSpec = FluidMotion.SheetSettle,
      ) { value, _ -> dragPx = value }
    }
  }

  val nested = remember(dismissThresholdPx, flingDismissPx) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Pulling back up while the sheet is already out of place closes that gap first, before the
        // content is allowed to scroll. Otherwise a half-dragged sheet scrolls its list instead of
        // returning, which reads as the drag having been ignored.
        if (source != NestedScrollSource.UserInput || available.y >= 0f || dragPx <= 0f) {
          return Offset.Zero
        }
        val used = (-available.y).coerceAtMost(dragPx)
        dragPx -= used
        return Offset(0f, -used)
      }

      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        dragPx += available.y
        return Offset(0f, available.y)
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (dragPx <= 0f) return Velocity.Zero
        settle(available.y)
        return available
      }
    }
  }

  // Il pannello sta in fondo allo schermo e le app girano edge-to-edge, quindi la finestra non si
  // ridimensiona da sola: senza questo, la tastiera copre i campi di testo che il pannello contiene.
  BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
    // Una pagina intera su uno schermo largo si stacca dai bordi. Un modulo a tutta larghezza su un
    // tablet e' un muro, e il margine intorno e' quello che lo fa leggere come una cosa sopra la
    // pagina invece che come un'altra pagina.
    val detached = fullScreen && maxWidth >= FluidFullScreenDetachWidth
    val shape = if (detached) {
      ContinuousCornerShape(FluidRadius.Sheet)
    } else {
      ContinuousCornerShape(topStart = FluidRadius.Sheet, topEnd = FluidRadius.Sheet)
    }
    Column(
      modifier = Modifier
        .then(
          when {
            // Un foglio a tutta larghezza su un tablet in orizzontale e' una striscia bassa e larga
            // quanto la finestra: le righe dentro diventano lunghe un braccio. Oltre la misura del
            // pop-up si ferma e resta in mezzo, come un foglio posato sulla pagina.
            !fullScreen -> Modifier
              .widthIn(max = FluidSheetMaxWidth)
              .fillMaxWidth()
              .heightIn(max = FluidSheetMaxHeight)
            // I margini stanno PRIMA del materiale nella catena: la superficie si disegna dentro
            // di essi, e la molla che porta il pannello in scena muove solo il pannello.
            detached -> Modifier
              .fillMaxHeight()
              .windowInsetsPadding(WindowInsets.systemBars)
              .padding(FluidFullScreenDetachedMargin)
              .widthIn(max = FluidScreenDefaults.ContentMaxWidth)
              .fillMaxWidth()
            else -> Modifier
              .fillMaxSize()
              .windowInsetsPadding(WindowInsets.statusBars)
          },
        )
        .onSizeChanged { height = it.height.toFloat() }
        .nestedScroll(nested)
        .draggable(
          orientation = Orientation.Vertical,
          state = rememberDraggableState { delta -> dragPx = (dragPx + delta).coerceAtLeast(0f) },
          onDragStopped = { velocity -> settle(velocity) },
        )
        .graphicsLayer {
          val travelled = slide().coerceIn(0f, 1f)
          // Below the screen at 0, home at 1, and the drag is added on top in raw pixels so it
          // tracks the finger exactly rather than through an eased curve.
          translationY = if (reducedMotion) {
            dragPx
          } else {
            size.height * (1f - travelled) + dragPx
          }
          // The alpha only covers the last sliver of the exit; the travel is the animation.
          alpha = (travelled * 4f).coerceIn(0f, 1f)
          val retreat = 1f - backProgress() * 0.06f
          scaleX = retreat
          scaleY = retreat
          transformOrigin = TransformOrigin(0.5f, 1f)
        }
        .then(
          if (backdrop != null && !fullScreen) {
            Modifier.glassSurfaceInked(
              ink = sheetInk,
              state = backdrop,
              tint = GlassDefaults.panelTint(paneTint),
              shape = shape,
              role = GlassRole.Modal,
              optics = GlassDefaults.panelOptics(),
              exports = sheetGlass,
            )
          } else if (fullScreen) {
            // Una pagina intera e' opaca di proposito. Il vetro sta sulle cose che galleggiano
            // sopra un contenuto, e una pagina non galleggia: copre. Di vetro sarebbe l'intera
            // interfaccia sfocata, con la pagina di sotto che si legge attraverso il titolo — cioe'
            // esattamente il difetto per cui il vetro va tenuto sugli elementi piccoli. La tinta,
            // se c'e', resta come un velo: e' il colore da cui la pagina e' nata.
            val surface = MaterialTheme.colorScheme.surface
            val page = pageColor
              ?: paneTint?.let { androidx.compose.ui.graphics.lerp(surface, it, 0.08f) }
              ?: surface
            Modifier.background(page, shape)
          } else if (sheetFill != null) {
            Modifier
              .glassOpaquePanel(sheetFill.layerBackdrop, MaterialTheme.colorScheme.surfaceContainerHigh, shape)
              .glassContainer(sheetFill)
          } else {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
          },
        )
        .clip(shape)
        .semantics {
          paneTitle?.let { this.paneTitle = it }
          isTraversalGroup = true
        },
    ) {
      FluidGrabber()
      if (fullScreen) {
        // Il testo sulla pagina e' quello che ci si legge sopra: `onSurface` sulla pagina del tema, il chiaro
        // sul nero di un visualizzatore (3.0.1).
        val pageContent = pageColor?.let { fluidContentColorOn(it) } ?: MaterialTheme.colorScheme.onSurface
        CompositionLocalProvider(LocalContentColor provides pageContent) {
          FluidFullScreenHeader(title = paneTitle, onDismiss = onDismiss)
          // Lo scorrimento sta qui e non nel contenuto, perche' e' un compito della pagina: il
          // chiamante mette i suoi campi e basta. La chiusura trascinando passa di qui lo stesso —
          // quando la colonna non ha piu' niente da scorrere verso l'alto, il resto del gesto arriva
          // alla connessione del pannello.
          // La barra di sistema in fondo la scavalca l'ultima cosa della pagina: i tasti se ci sono,
          // altrimenti la coda del contenuto. Staccata dai bordi non serve: il margine e' gia' fuori.
          val clearsSystemBar = if (detached) Modifier else Modifier.navigationBarsPadding()
          Column(
            modifier = Modifier
              .weight(1f)
              .verticalScroll(rememberScrollState())
              .then(if (footer == null) clearsSystemBar else Modifier),
            content = content,
          )
          if (footer != null) {
            Column(modifier = Modifier.fillMaxWidth().then(clearsSystemBar), content = footer)
          }
        }
      } else if (backdrop != null) {
        // Come nel pop-up: il canvas del contenuto e' il foglio, e il vetro che ci sta sopra e'
        // vetro impilato (`LocalFluidCanvasIsGlass`), non una card su una pagina.
        CompositionLocalProvider(
          LocalFluidCanvasBackdrop provides sheetGlass,
          LocalFluidCanvasIsGlass provides true,
        ) {
          GlassInkContent(sheetInk, LocalContentColor.current) {
            content()
            footer?.invoke(this)
          }
        }
      } else {
        content()
        footer?.invoke(this)
      }
    }
  }
}

/**
 * Il colore del testo su un fondo qualunque: il ruolo «on» del tema se il fondo e' un ruolo del tema,
 * altrimenti nero o bianco secondo la luminanza (3.0.1, per `FluidGlassModalPortal(pageColor)`).
 */
@Composable
internal fun fluidContentColorOn(background: Color): Color =
  MaterialTheme.colorScheme.contentColorFor(background).takeOrElse {
    if (background.luminance() > 0.5f) Color.Black.copy(alpha = 0.87f) else Color.White
  }

/**
 * La barra in cima a una pagina intera: il titolo nel mezzo, la chiusura a destra.
 *
 * La chiusura e' un **tasto di vetro** ([FluidGlassIconButton], la croce al centro), come ogni altro
 * tasto di una barra: la pagina intera e' opaca di proposito, ma i comandi che le stanno sopra sono
 * della stessa famiglia di quelli di ogni barra dell'app, e un cerchio dipinto accanto a un tasto di
 * vetro e' la differenza che si legge. Sotto API 31 non c'e' un vetro da attraversare e resta il
 * cerchio pieno con la croce di prima, lo stesso segno del tasto che svuota un campo.
 */
@Composable
private fun FluidFullScreenHeader(title: String?, onDismiss: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(FluidScreenDefaults.ControlRowHeight)
      // 4.0 (F1A.11): il vetro della croce sta a 16 dp dal bordo come ogni tasto di barra ([FluidMetrics.ScreenEdgeMargin]);
      // prima il riquadro da 48 stava a 8 e il vetro a 10. [FluidBarEdgeInset] e' il margine del riquadro, non del vetro.
      .padding(horizontal = FluidBarEdgeInset),
  ) {
    if (title != null) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        // Il colore della pagina (3.0.1): `onSurface` di serie, il chiaro su un fondo scuro dell'app.
        color = LocalContentColor.current,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .align(Alignment.Center)
          // Il titolo non entra nel riquadro del tasto (48 dp, [FluidMetrics.TouchTarget]) e il margine e' simmetrico.
          .padding(horizontal = FluidMetrics.TouchTarget),
      )
    }
    FluidFullScreenCloseButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd))
  }
}

@Composable
private fun FluidFullScreenCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  // Il nome per TalkBack viene dalle parole dell'app (3.0.1): era «Chiudi» scritto qui.
  val closeLabel = LocalFluidLabels.current.close
  if (isRenderEffectSupported()) {
    // Il tasto tondo di vetro delle barre: pressione di LibrePods, luce del dito, `Tap`. Il fondale e'
    // quello che c'e' in scope, e su una pagina opaca e' vuoto — il tasto tiene il bordo, l'ombra e la
    // risposta al dito, e niente da piegare, come ogni tasto di vetro su una pagina piatta.
    FluidGlassIconButton(
      onClick = onClick,
      modifier = modifier,
    ) {
      // A inchiostro pieno dalla 4.0 (era all'80%): le icone dei tasti di vetro sono nell'inchiostro del testo (R6).
      FluidIcon(
        imageVector = FluidIcons.Close,
        contentDescription = closeLabel,
        size = FluidFullScreenCloseGlyph,
      )
    }
    return
  }
  val fill = LocalContentColor.current.copy(alpha = 0.10f)
  val glyph = LocalContentColor.current.copy(alpha = 0.70f)
  Box(
    modifier = modifier
      .size(FluidFullScreenCloseTouchTarget)
      .fluidPressable(onClick = onClick, role = Role.Button, feedback = FluidPressFeedback.ExpandRound)
      .semantics { contentDescription = closeLabel },
    contentAlignment = Alignment.Center,
  ) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(FluidFullScreenCloseSize)) {
      val radius = size.minDimension / 2f
      drawCircle(color = fill, radius = radius)
      val arm = radius * 0.36f
      val stroke = radius * 0.14f
      listOf(
        Offset(-arm, -arm) to Offset(arm, arm),
        Offset(-arm, arm) to Offset(arm, -arm),
      ).forEach { (from, to) ->
        drawLine(color = glyph, start = center + from, end = center + to, strokeWidth = stroke, cap = StrokeCap.Round)
      }
    }
  }
}

private val FluidSheetMaxHeight = 640.dp

/** Oltre questa larghezza un foglio non si stira: resta in mezzo, largo quanto un pop-up. */
private val FluidSheetMaxWidth = 620.dp

/** Da questa larghezza in su una pagina intera si stacca dai bordi invece di stare a filo. */
private val FluidFullScreenDetachWidth = 600.dp
private val FluidFullScreenDetachedMargin = 24.dp
private val FluidFullScreenCloseSize = 30.dp
private val FluidFullScreenCloseTouchTarget = 44.dp

/**
 * La croce dentro il tasto di vetro: 0,6 del lato della lente (44 dp), come ogni icona di un tasto tondo, cioe'
 * 26 dp dalla 4.0 (era la meta', 20: R6).
 */
private val FluidFullScreenCloseGlyph = FluidMetrics.BarIconSize

/** How far the sheet must be pushed down before letting go dismisses instead of springing back. */
private val FluidSheetDismissTravel = 110.dp

/** Or how fast, in dp per second: a short flick down closes it however little it travelled. */
private val FluidSheetDismissVelocity = 900.dp
