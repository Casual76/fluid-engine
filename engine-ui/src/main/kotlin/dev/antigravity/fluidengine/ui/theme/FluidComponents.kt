package dev.antigravity.fluidengine.ui.theme

import dev.antigravity.fluidengine.ui.fluid.FluidSpinner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.rememberFluidContextMenu
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasIsGlass
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidPressState
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidRowPressable
import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

enum class FluidTone {
  Primary,
  Success,
  Warning,
  Danger,
  Info,
  Neutral,
}

/**
 * Turns a content container into glass over the screen's ambient canvas — when there is one.
 *
 * The rule this modifier enforces, and the one worth writing on the wall of the design system:
 * **glass goes on the container, never on the row.** A group of twelve rows is one pane, not twelve.
 * Every pane costs a layer recording and a chain of `RenderEffect`s over its own bounds, so a screen
 * that puts the material one level too low goes from eight surfaces to eighty and drops frames for
 * something nobody can see. Text, icons and badges standing on the glass stay fully opaque.
 *
 * Asking for glass is a *request*, not an instruction. With no canvas in scope — a plain screen, a
 * dialog in its own window, anything below API 31 — this returns the modifier untouched and the
 * caller draws the opaque surface it has always drawn. That is what makes `glass = true` safe to add
 * to a component before every screen that uses it has a canvas.
 */
@Composable
private fun Modifier.fluidContentGlass(enabled: Boolean, shape: Shape): Modifier {
  if (!enabled) return this
  val canvas = LocalFluidCanvasBackdrop.current ?: return this
  // Vetro su vetro quando il fondale e' gia' un pannello: gli effetti salgono invece di scendere.
  val stacked = LocalFluidCanvasIsGlass.current
  return this.glassSurface(
    state = canvas,
    tint = if (stacked) GlassDefaults.stackedContentTint() else GlassDefaults.contentTint(),
    shape = shape,
    role = GlassRole.Content,
    optics = if (stacked) GlassDefaults.stackedContentOptics() else GlassDefaults.optics(GlassRole.Content),
  )
}

/** Whether a container asking for glass will actually get it here. Drives its own fill colour. */
@Composable
private fun contentGlassAvailable(enabled: Boolean): Boolean =
  enabled && LocalFluidCanvasBackdrop.current != null

/**
 * L'intestazione di Material, rimasta nel design system dal tempo in cui non ce n'era un'altra.
 *
 * È una `LargeTopAppBar`: una barra piena, senza vetro, il cui titolo grande si ripiega dentro di
 * sé e non *dentro la pagina*. Tutto il resto dell'interfaccia ha un'altra idea di intestazione — il
 * titolo vive nello scroll e, quando la pagina lo porta sotto la barra di stato, si aggancia a una
 * barra di vetro che rifrange il contenuto — e questa è la sola superficie rimasta a dire
 * un'altra cosa.
 *
 * Deprecata e non cancellata (la rimozione è prevista per la 3.0). Non c'è una sostituzione a una
 * riga, perché la barra giusta non è un componente da mettere in cima: è la schermata.
 *
 *  - Se la pagina è una lista, il posto è [dev.antigravity.fluidengine.ui.fluid.FluidScreen]: titolo,
 *    sottotitolo, freccia indietro e azioni (`actions`) li ha già, e porta la barra di vetro.
 *  - Se la schermata è tua e il titolo sta in una pagina che non è una `LazyColumn` dell'engine,
 *    [dev.antigravity.fluidengine.ui.fluid.FluidCollapsingTitle] come primo elemento dello scroll e
 *    [dev.antigravity.fluidengine.ui.fluid.FluidCollapsingTopBar] in cima.
 */
@Deprecated(
  message = "È una LargeTopAppBar di Material, senza vetro e col titolo che si ripiega nella barra " +
    "invece che nella pagina. Usa FluidScreen (titolo, sottotitolo, indietro e azioni compresi) o, se " +
    "la schermata è tua, FluidCollapsingTitle + FluidCollapsingTopBar.",
  level = DeprecationLevel.WARNING,
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidTopHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  scrollBehavior: TopAppBarScrollBehavior? = null,
  titleTrailing: (@Composable () -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  LargeTopAppBar(
    modifier = modifier.fillMaxWidth(),
    scrollBehavior = scrollBehavior,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = title,
            modifier = if (titleTrailing != null) Modifier.weight(1f, fill = false) else Modifier,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          titleTrailing?.invoke()
        }
        subtitle?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    },
    navigationIcon = {
      if (onBack != null) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
      }
    },
    actions = actions,
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = MaterialTheme.colorScheme.background,
      scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
      navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
      actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      titleContentColor = MaterialTheme.colorScheme.onSurface,
    ),
  )
}

@Composable
fun FluidAccentLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text.uppercase(),
    modifier = modifier,
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
    color = MaterialTheme.colorScheme.primary,
  )
}

@Composable
fun FluidPillTabs(
  options: List<String>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Lo stesso controllo segmentato del resto del design system, non un secondo disegno.
  //
  // Questo era un guscio solido con una pastiglia bianca e un'ombra sotto: la sola superficie
  // dell'interfaccia rimasta a fingere il rilievo invece di averlo. La differenza non e' l'aspetto,
  // e' che [FluidSegmentedControl] tiene *una copia invisibile delle etichette tinte d'accento*
  // sotto una lente, quindi il segmento scelto non viene colorato, viene **visto attraverso il
  // vetro** — ed e' trascinabile fra i segmenti come la pastiglia della tab bar.
  //
  // Il nome resta perche' e' quello che le app chiamano.
  FluidSegmentedControl(
    options = options,
    selected = selected,
    onSelect = onSelect,
    modifier = modifier,
    label = { it },
  )
}

@Composable
fun FluidEditorialCard(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  content: @Composable ColumnScope.() -> Unit,
) {
  // A fill, not a card. Elevation is a Material idea: it puts a shadow under every grouped block and
  // tints the surface by depth, so a screen of cards reads as a stack of floating slabs. iOS
  // separates a group from its background with colour alone, and keeps shadows for things that
  // genuinely float above the page — a switch thumb, a sheet.
  CompositionLocalProvider(LocalContentColor provides contentColor) {
    Column(
      modifier = modifier
        .fillMaxWidth()
        .clip(ContinuousCornerShape(FluidRadius.Group))
        .background(color)
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
fun FluidMiniChart(
  points: List<Float>,
  color: Color,
  modifier: Modifier = Modifier,
  fillAlpha: Float = 0.28f,
  threshold: Float? = null,
) {
  val centerColor = MaterialTheme.colorScheme.surface
  val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
  val thresholdColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(136.dp)
      .then(modifier),
  ) {
    if (points.isEmpty()) return@Canvas

    val safeMax = max(points.maxOrNull() ?: 1f, 1f)
    val safeMin = min(points.minOrNull() ?: 0f, threshold ?: 0f)
    val range = max(safeMax - safeMin, 1f)
    val xStep = if (points.size == 1) 0f else size.width / points.lastIndex.coerceAtLeast(1)

    for (index in 0..4) {
      val y = size.height * index / 4f
      drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }

    threshold?.let { level ->
      val y = size.height - ((level - safeMin) / range) * size.height
      drawLine(thresholdColor, Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx())
    }

    val linePath = Path()
    val fillPath = Path()
    points.forEachIndexed { index, value ->
      val x = index * xStep
      val normalized = (value - safeMin) / range
      val y = size.height - normalized * size.height
      if (index == 0) {
        linePath.moveTo(x, y)
        fillPath.moveTo(x, size.height)
        fillPath.lineTo(x, y)
      } else {
        linePath.lineTo(x, y)
        fillPath.lineTo(x, y)
      }
    }
    fillPath.lineTo(size.width, size.height)
    fillPath.close()

    drawPath(
      path = fillPath,
      brush = Brush.verticalGradient(
        colors = listOf(color.copy(alpha = fillAlpha), color.copy(alpha = 0.02f)),
      ),
    )
    drawPath(
      path = linePath,
      color = color,
      style = Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.cornerPathEffect(12f)),
    )
    points.forEachIndexed { index, value ->
      val x = index * xStep
      val normalized = (value - safeMin) / range
      val y = size.height - normalized * size.height
      drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(x, y))
      drawCircle(color = centerColor, radius = 2.dp.toPx(), center = Offset(x, y))
    }
  }
}

@Composable
fun FluidStatusBadge(
  label: String,
  modifier: Modifier = Modifier,
  tone: FluidTone = FluidTone.Neutral,
) {
  val colors = toneColors(tone)
  // Not a disabled chip. A chip is a control, and a disabled one is drawn to look unavailable — the
  // wrong signal entirely for a badge, which is a label that was never meant to be tapped.
  Text(
    text = label,
    modifier = modifier
      .clip(FluidCapsuleShape)
      .background(colors.container)
      .padding(horizontal = 10.dp, vertical = 4.dp),
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
    color = colors.content,
    maxLines = 1,
  )
}

@Composable
fun FluidMetricTile(
  label: String,
  value: String,
  detail: String,
  modifier: Modifier = Modifier,
  tone: FluidTone = FluidTone.Neutral,
  onClick: (() -> Unit)? = null,
  animatePress: Boolean = true,
  /** See [fluidContentGlass]. */
  glass: Boolean = false,
) {
  val colors = toneColors(tone)
  val shape = ContinuousCornerShape(FluidRadius.Card)
  val onGlass = contentGlassAvailable(glass)
  val clickableModifier = if (onClick != null) {
    modifier.fluidPressable(onClick = onClick, pressedScale = if (animatePress) 0.968f else 1f)
  } else {
    modifier
  }

  Box(
    modifier = clickableModifier
      .animateContentSize(animationSpec = FluidMotion.intSize())
      .fluidContentGlass(glass, shape)
      .clip(shape)
      // A tile's tone is the tile: on glass the container is kept, at a fraction, so a Warning tile
      // is still visibly a Warning tile instead of becoming the same pane as its neighbours.
      .background(if (onGlass) colors.container.copy(alpha = 0.34f) else colors.container),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = colors.content,
      )
      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
fun FluidCard(
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
  onClick: (() -> Unit)? = null,
  animateContent: Boolean = true,
  animatePress: Boolean = true,
  /** See [fluidContentGlass]: a request honoured only where there is a canvas to look through. */
  glass: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  // `MaterialTheme.shapes.large` is a circular corner, and rule one of this design system says a
  // corner is continuous — a violation that had been sitting inside the engine that writes the rule.
  val shape = ContinuousCornerShape(FluidRadius.Card)
  val onGlass = contentGlassAvailable(glass)
  val baseModifier = modifier
    .fillMaxWidth()
    .then(
      if (animateContent) {
        Modifier.animateContentSize(animationSpec = FluidMotion.intSize())
      } else {
        Modifier
      },
    )

  val clickableModifier = if (onClick != null) {
    baseModifier.fluidPressable(onClick = onClick, pressedScale = if (animatePress) 0.974f else 1f)
  } else {
    baseModifier
  }

  Surface(
    modifier = clickableModifier.fluidContentGlass(glass, shape),
    shape = shape,
    color = when {
        // `highlighted` survives onto glass as a wash rather than as a fill. A highlighted card that
        // simply went opaque would be the one card on the page not made of the same material, which
        // reads as an error rather than as emphasis.
        onGlass && highlighted -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.28f)
        onGlass -> Color.Transparent
        highlighted -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
      },
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

/**
 * Sync state as a bar control rather than as a mark on the page.
 *
 * A bare coloured dot next to the large title had two problems, and both are why it is now a
 * self-contained glyph living in the navigation bar: a dot alone carries no meaning without colour
 * vision, and anything parked beside the large title ends up underneath it the moment the title
 * travels to the centre of the bar. Here it keeps its own place, stays visible at every scroll
 * position, and states which of the four things is happening with a shape as well as a colour.
 */
@Composable
fun FluidSyncIndicator(
  status: SyncStatus,
  modifier: Modifier = Modifier,
) {
  FluidSyncIndicatorSurface(status = status, modifier = modifier, press = null)
}

/**
 * Se l'indicatore sta su una barra di vetro: il materiale della barra e' in scope
 * ([LocalGlassBackdrop], che la barra superiore mette attorno alle sue azioni) e c'e' RenderEffect.
 * Altrove — fuori da una barra, sotto API 31 — resta il cerchio pieno di sempre.
 */
@Composable
private fun syncIndicatorBarGlass(): GlassBackdropState? =
  LocalGlassBackdrop.current.takeIf { isRenderEffectSupported() }

/**
 * L'indicatore, con la pressione di chi lo rende premibile ([FluidSyncAction]).
 *
 * **Nella barra e' un tasto di vetro** (3.0, fase 9): la stessa capsula delle azioni accanto a lui
 * ([glassControlSurface] sul fondale della barra), con la tinta del tono — non piu' un cerchio opaco
 * in mezzo a lenti. Il tono non sparisce: entra nel materiale, come l'accento in un tasto di vetro
 * scelto, e il glifo sta sopra, opaco. Fuori da una barra, o senza vetro, e' il cerchio colorato di prima.
 *
 * @param press la pressione condivisa col `fluidPressable` che lo avvolge: il vetro cresce, segue il dito e
 *   si accende sulla stessa linea del tempo del click. Nulla per un indicatore che non si tocca.
 */
@Composable
private fun FluidSyncIndicatorSurface(
  status: SyncStatus,
  modifier: Modifier,
  press: FluidPressState?,
) {
  val neverSynced = status.lastSuccessfulSyncEpochMillis == null
  // A year the school has not opened yet is not a fault: nothing is broken, nobody can fix it, and
  // it is the ordinary state of the source. Reporting it in the same colour as an expired session
  // teaches people to ignore the colour.
  val notice = status.notice?.takeIf(String::isNotBlank)
  val notStarted = notice != null
  val tone = when {
    notStarted -> FluidTone.Info
    status.state == SyncState.IDLE -> if (neverSynced) FluidTone.Warning else FluidTone.Success
    status.state == SyncState.SYNCING -> FluidTone.Info
    status.state == SyncState.PARTIAL -> FluidTone.Warning
    else -> FluidTone.Danger
  }
  val label = when {
    notStarted -> notice.orEmpty()
    status.state == SyncState.IDLE -> if (neverSynced) {
      "Sincronizzazione mai completata"
    } else {
      "Sincronizzazione completata"
    }
    status.state == SyncState.SYNCING -> "Aggiornamento in corso"
    status.state == SyncState.PARTIAL -> "Sincronizzazione parziale"
    else -> "Sincronizzazione non riuscita"
  }
  val colors = toneColors(tone)
  val container by animateColorAsState(
    targetValue = colors.container,
    animationSpec = FluidMotion.color(),
    label = "sync container",
  )
  val content by animateColorAsState(
    targetValue = colors.content,
    animationSpec = FluidMotion.color(),
    label = "sync content",
  )

  val barGlass = syncIndicatorBarGlass()
  // Il tono nel materiale: la sua tinta sopra la pellicola dei controlli, a meta' (il vetro resta
  // vetro) — il ripiego sotto API 31 non serve, perche' li' non si arriva (`barGlass` e' nullo).
  val glassTint = GlassDefaults.controlTint().let { base ->
    base.copy(overlay = container.copy(alpha = SyncIndicatorToneShare), fallback = container, hairline = Color.Transparent)
  }

  Box(
    modifier = modifier
      .size(SyncIndicatorSize)
      .then(
        if (barGlass != null) {
          Modifier.glassControlSurface(
            backdrop = barGlass,
            shape = FluidCapsuleShape,
            tint = glassTint,
            interactive = press != null,
            pressState = press,
          )
        } else {
          Modifier.background(container, CircleShape)
        },
      )
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    AnimatedContent(
      targetState = if (notStarted) null else status.state,
      transitionSpec = {
        (fadeIn(FluidMotion.fadeIn(180)) + scaleIn(FluidMotion.standard(), initialScale = 0.6f))
          .togetherWith(fadeOut(FluidMotion.fadeOut(120)) + scaleOut(FluidMotion.standard(), targetScale = 0.6f))
      },
      label = "sync glyph",
    ) { state ->
      when (state) {
        null -> Icon(
          imageVector = Icons.Rounded.Info,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = content,
        )
        SyncState.SYNCING -> FluidSpinner(size = 14.dp, color = content)
        SyncState.OFFLINE -> Icon(
          imageVector = Icons.Rounded.CloudOff,
          contentDescription = null,
          modifier = Modifier.size(13.dp),
          tint = content,
        )
        SyncState.ERROR,
        SyncState.PARTIAL -> Icon(
          imageVector = Icons.Rounded.PriorityHigh,
          contentDescription = null,
          modifier = Modifier.size(13.dp),
          tint = content,
        )
        SyncState.IDLE -> Box(
          modifier = Modifier
            .size(if (neverSynced) 7.dp else 8.dp)
            .background(content, CircleShape),
        )
      }
    }
  }
}

private val SyncIndicatorSize = 26.dp

/** Quanto del tono entra nel vetro dell'indicatore: la meta' — il glifo sopra resta leggibile e il materiale resta vetro. */
private const val SyncIndicatorToneShare = 0.55f

/**
 * The sentence [FluidSyncNotice] would show, or null when there is nothing to say.
 *
 * Exposed so a list can decide whether to reserve a slot at all: an item that renders nothing still
 * takes its share of the arrangement's spacing, which is a gap at the top of the page with no
 * explanation attached to it.
 */
fun SyncStatus.noticeMessage(): String? {
  if (state == SyncState.SYNCING) return null
  return message?.takeIf(String::isNotBlank)
}

/**
 * The one thing the sync knows that the page cannot say for itself, said on the page.
 *
 * A status glyph in the bar is a reasonable place to *keep* this and a hopeless place to *deliver*
 * it. A refresh that fails leaves an orange mark in the corner of a page that otherwise just looks
 * empty, and nothing about a mark invites the tap that would explain it. Anything worth an
 * exclamation mark is worth a sentence.
 *
 * The retry is offered only for the failures retrying can fix. Data the source does not publish
 * yet is not a failure — nothing went wrong, and asking again will get
 * the same answer — so it is stated and left alone.
 */
@Composable
fun FluidSyncNotice(
  status: SyncStatus,
  modifier: Modifier = Modifier,
  onRetry: (() -> Unit)? = null,
) {
  val message = status.noticeMessage() ?: return
  val notice = status.notice?.takeIf(String::isNotBlank)
  val notStarted = notice != null
  val isFailure = !notStarted && (
    status.state == SyncState.PARTIAL ||
      status.state == SyncState.ERROR ||
      status.state == SyncState.OFFLINE
    )
  val tone = when {
    notStarted -> FluidTone.Info
    status.state == SyncState.ERROR || status.state == SyncState.OFFLINE -> FluidTone.Danger
    status.state == SyncState.PARTIAL -> FluidTone.Warning
    else -> FluidTone.Info
  }
  val title = when {
    notStarted -> notice.orEmpty()
    status.state == SyncState.OFFLINE -> "Nessuna connessione"
    isFailure -> "Aggiornamento non riuscito"
    else -> "Dati non disponibili"
  }
  // The sections stay empty until the school opens the year, and no amount of retrying changes
  // that. What the reader can do is look at the year before it, so that is what is offered.
  val guidance = "Finché la scuola non lo apre, le sezioni legate all'anno restano vuote. " +
    "Puoi consultare l'anno precedente dal selettore in Impostazioni."


  FluidCard(modifier = modifier, highlighted = true) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Icon(
          imageVector = if (isFailure) Icons.Rounded.PriorityHigh else Icons.Rounded.Info,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = toneColors(tone).content,
        )
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = toneColors(tone).content,
          )
          Text(
            text = if (notStarted) "$message $guidance" else message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (isFailure && onRetry != null) {
        FluidButton(text = "Riprova", onClick = onRetry, fillWidth = true)
      }
    }
  }
}

/**
 * The sync indicator, and the explanation behind it.
 *
 * A red dot that cannot be asked *why* is not a status, it is a rumour: the app already knew the
 * failure message and which sections it applied to, and showed neither. Tapping opens what it
 * knows, and offers the one action worth offering when something did not arrive.
 *
 * Il dettaglio si apre come un foglio di vetro dentro la composizione ([FluidGlassModalPortal]),
 * quindi rifrange la pagina invece di dipingerne una copia, **se l'app ha già un host dei modali
 * alla radice** (un `FluidGlassModalHost` e il suo stato in [LocalFluidGlassModalHostState]).
 * Senza, non c'è dove disegnarlo: il portale tornerebbe in silenzio e il tocco non farebbe niente,
 * quindi in quel caso — e solo in quel caso — resta il foglio di piattaforma di prima. L'app non
 * deve aggiungere niente per averne uno o l'altro: aggiornare l'engine basta.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluidSyncAction(
  status: SyncStatus,
  modifier: Modifier = Modifier,
  onRetry: (() -> Unit)? = null,
) {
  var showDetails by remember { mutableStateOf(false) }
  val modalHost = LocalFluidGlassModalHostState.current

  // Nella barra l'indicatore e' vetro e si tocca come ogni tasto di vetro: una pressione sola per il
  // vetro e per il click, e niente increspatura di Material sopra a una lente. Altrove e' un cerchio
  // pieno che cresce sotto il dito, come ogni tasto.
  val onGlass = syncIndicatorBarGlass() != null
  val press = rememberFluidPressState(FluidPressFeedback.ExpandRound)
  FluidSyncIndicatorSurface(
    status = status,
    modifier = modifier.fluidPressable(
      onClick = { showDetails = true },
      role = Role.Button,
      focusShape = CircleShape,
      feedback = if (onGlass) null else FluidPressFeedback.ExpandRound,
      pressState = if (onGlass) press else null,
    ),
    press = if (onGlass) press else null,
  )

  if (modalHost != null) {
    // La variante con l'elemento, non quella booleana: alla chiusura lo stato smette di essere
    // "aperto" e il pannello deve uscire intero, col testo che aveva, mentre lo scrim sfuma.
    FluidGlassModalPortal(
      item = status.takeIf { showDetails },
      onDismissRequest = { showDetails = false },
      presentation = FluidGlassModalPresentation.Sheet,
      paneTitle = SyncDetailsTitle,
    ) { shown ->
      // Il foglio di vetro non ha un titolo e non ha un margine per la barra di sistema: il primo lo
      // metteva `FluidSheet`, il secondo la finestra di piattaforma.
      Column(modifier = Modifier.navigationBarsPadding()) {
        Text(
          text = SyncDetailsTitle,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
          style = MaterialTheme.typography.titleLarge,
          textAlign = TextAlign.Center,
        )
        FluidSyncDetails(status = shown, onRetry = onRetry, onDone = { showDetails = false })
      }
    }
  } else if (showDetails) {
    FluidSheet(
      onDismissRequest = { showDetails = false },
      title = SyncDetailsTitle,
    ) {
      FluidSyncDetails(status = status, onRetry = onRetry, onDone = { showDetails = false })
    }
  }
}

private const val SyncDetailsTitle = "Sincronizzazione"

/** Quello che sta dentro il dettaglio della sincronizzazione, qualunque sia la superficie che lo porta. */
@Composable
private fun FluidSyncDetails(
  status: SyncStatus,
  onRetry: (() -> Unit)?,
  onDone: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .padding(bottom = 24.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text(
      text = status.lastSyncLabel(),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    status.message?.takeIf(String::isNotBlank)?.let { message ->
      FluidInlineMessage(
        message = message,
        title = if (status.state == SyncState.OFFLINE) "Nessuna connessione" else "Dettaglio",
        tone = if (status.state == SyncState.PARTIAL) FluidTone.Warning else FluidTone.Danger,
      )
    }
    if (status.failedSections.isNotEmpty()) {
      Text(
        text = "Non aggiornate: ${status.failedSections.joinToString(", ")}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (status.notice != null) {
      Text(
        text = "L'anno selezionato non è ancora stato aperto dalla scuola: i dati mostrati sono quelli dell'anno precedente.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (status.message.isNullOrBlank() && status.failedSections.isEmpty() && status.state != SyncState.SYNCING) {
      Text(
        text = "Tutti i dati risultano aggiornati.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (onRetry != null) {
      FluidButton(
        text = "Riprova adesso",
        onClick = {
          onDone()
          onRetry()
        },
        fillWidth = true,
      )
    }
  }
}

/**
 * Un messaggio dentro la pagina: un titolo, una frase e, se serve, qualcosa da toccare.
 *
 * Senza [onClick] e' quello di sempre, un riquadro che dice una cosa. Con [onClick] **tutto il
 * messaggio e' un bersaglio**: cede sotto il dito come ogni superficie ([FluidCard]) e dice che si
 * puo' toccare con una freccia a destra, perche' un avviso toccabile senza un segno e' una
 * promessa che nessuno scopre. Pampa Store si era fatto una riga di lista con un pannello suo per
 * averlo, e aveva scritto perche': riscrivere a mano la superficie, i colori del tono e il tocco
 * sono le tre cose che il design system esiste per non far riscrivere.
 *
 * @param onClick cosa succede toccando il messaggio. Dalla 3.0. Il ruolo per chi legge lo schermo
 *   diventa "pulsante".
 * @param action la parola di cio' che il tocco fa ("Apri le impostazioni", "Riprova"): una riga
 *   nel colore del tono sotto la frase. Conta solo con [onClick]: da sola non sarebbe cliccabile,
 *   quindi sarebbe una riga di testo che finge di esserlo. Dalla 3.0.
 * @param leading un'icona davanti al titolo, nella piastrella del tono ([FluidToneTile]): il segno
 *   che distingue un avviso da un errore senza leggere. Dalla 3.0.
 * @param onDismiss il tasto "Nascondi". Con [onClick] sostituisce la freccia: un messaggio che si
 *   puo' chiudere e toccare tiene la chiusura, che e' l'azione che non si puo' sbagliare.
 */
@Composable
fun FluidInlineMessage(
  message: String,
  modifier: Modifier = Modifier,
  title: String = "Messaggio",
  tone: FluidTone = FluidTone.Info,
  onDismiss: (() -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  action: String? = null,
  leading: (@Composable () -> Unit)? = null,
) {
  val toneColor = toneColors(tone).content
  FluidCard(
    modifier = if (onClick != null) modifier.semantics { role = Role.Button } else modifier,
    highlighted = tone != FluidTone.Neutral,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      if (leading != null) FluidToneTile(tone = tone, content = leading)
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = toneColor,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onClick != null && !action.isNullOrBlank()) {
          Text(
            text = action,
            style = MaterialTheme.typography.labelLarge,
            color = toneColor,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
      when {
        onDismiss != null -> IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Nascondi")
        }
        onClick != null -> Icon(
          imageVector = Icons.Rounded.ChevronRight,
          contentDescription = null,
          modifier = Modifier
            .size(18.dp)
            .align(Alignment.CenterVertically),
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
      }
    }
  }
}

fun SyncStatus.lastSyncLabel(nowEpochMillis: Long = System.currentTimeMillis()): String {
  if (state == SyncState.SYNCING) return "Aggiornamento in corso"
  val last = lastSuccessfulSyncEpochMillis ?: return "Mai aggiornato"
  val ageMillis = (nowEpochMillis - last).coerceAtLeast(0L)
  val minute = 60_000L
  val hour = 60 * minute
  val day = 24 * hour
  return when {
    ageMillis < minute -> "Aggiornato adesso"
    ageMillis < hour -> "Aggiornato ${ageMillis / minute} min fa"
    ageMillis < day -> "Aggiornato ${ageMillis / hour} h fa"
    else -> "Aggiornato il ${lastSyncDateFormatter.format(Instant.ofEpochMilli(last))}"
  }
}

/**
 * Il pulsante a pillola di prima, sotto il nome che le app già chiamano.
 *
 * Era una `AssistChip` di Material con i colori del contenitore secondario: l'unica pillola
 * dell'interfaccia che non cresceva sotto il dito, non seguiva le molle di [FluidMotion] e non
 * aveva la sagoma continua. Adesso è un [FluidChip] non selezionato: stessa etichetta, stesso
 * `onClick`, stesso `modifier`, e il resto lo decide il design system.
 *
 * Cosa si vede cambiare: la pillola è quella di [FluidChip] — una velatura neutra al posto del
 * contenitore secondario, e alta almeno 48 dp (la `AssistChip` ne disegnava 32 dentro un bersaglio
 * da 48) — e l'etichetta sta su una riga sola. Un'azione che prima era un chip colorato diventa un
 * chip quieto: se l'azione deve spiccare, è un [FluidButton] (`Tinted`).
 *
 * Deprecata e non cancellata (la rimozione è prevista per la 3.0).
 */
@Deprecated(
  message = "Era una AssistChip di Material; adesso è un FluidChip non selezionato. Usa FluidChip " +
    "(o FluidButton con stile Tinted, se l'azione deve spiccare).",
  replaceWith = ReplaceWith(
    "FluidChip(label = label, selected = false, onClick = onClick, modifier = modifier)",
    "dev.antigravity.fluidengine.ui.fluid.FluidChip",
  ),
)
@Composable
fun FluidQuickAction(
  label: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  FluidChip(
    label = label,
    selected = false,
    onClick = onClick,
    modifier = modifier,
  )
}

private val lastSyncDateFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

/**
 * Where a piece sits inside a list that had to be cut up. See [FluidListGroup].
 *
 * Una lista raggruppata vuole essere **un** pannello, ma un pannello e' un `GraphicsLayer` e un
 * layer e' una texture: oltre il tetto la registrazione torna vuota e il pannello disegna nero, e
 * tutto quello che ci sta dentro si compone nel singolo fotogramma in cui il pannello entra. Per
 * questo una sezione lunga viene tagliata in piu' pannelli — e sono due fatti sulla macchina.
 *
 * Non devono diventare fatti sul disegno. Un mese di comunicazioni e' una lista sola; una giuntura
 * che cade fra due voci dello stesso giorno si legge come un errore, perche' dal lato di chi guarda
 * lo e': nei dati, li', non cambia niente. Quindi i pezzi arrotondano solo gli angoli che sono
 * davvero l'inizio e la fine, e chi li emette richiude il vuoto in mezzo.
 */
/**
 * Quanto ritaglia un lato aperto di [FluidListGroup], e quanto devono sovrapporsi due pezzi.
 *
 * E' lo spessore che serve a togliere di mezzo il bordo speculare del vetro senza mangiare niente
 * che si veda: il bordo e' 0.8 dp piu' la propria sfocatura.
 */
val FluidGroupOpenEdge: Dp = 2.dp

enum class FluidGroupSegment {
  /** Tutta la lista sta in un pannello: arrotonda tutto. */
  Whole,

  /** Apre la lista: arrotonda solo in alto. */
  First,

  /** Ne' inizio ne' fine: nessun angolo tondo. */
  Middle,

  /** Chiude la lista: arrotonda solo in basso. */
  Last,
}

/**
 * A grouped list: rows share one rounded container, the way an inset-grouped table view does.
 *
 * The container clips, so a row's press highlight is trimmed to the group's corners instead of
 * painting a square patch over them.
 */
@Composable
fun FluidListGroup(
  modifier: Modifier = Modifier,
  /**
   * See [fluidContentGlass]. This is *the* place the material belongs on a list: one pane for the
   * whole group, whatever number of rows it happens to hold.
   */
  glass: Boolean = false,
  /**
   * Quale pezzo di una lista piu' lunga e' questo, quando la lista era troppo lunga per un pannello.
   *
   * Lascialo [FluidGroupSegment.Whole] a meno che tu non sia il codice che taglia: un gruppo che
   * arrotonda solo due angoli per qualunque altro motivo e' un gruppo che sembrera' rotto il giorno
   * in cui la lista sopra o sotto cambia lunghezza.
   */
  segment: FluidGroupSegment = FluidGroupSegment.Whole,
  content: @Composable ColumnScope.() -> Unit,
) {
  val radius = FluidRadius.Group
  val shape = when (segment) {
    FluidGroupSegment.Whole -> ContinuousCornerShape(radius)
    FluidGroupSegment.First -> ContinuousCornerShape(topStart = radius, topEnd = radius)
    FluidGroupSegment.Middle -> ContinuousCornerShape()
    FluidGroupSegment.Last -> ContinuousCornerShape(bottomEnd = radius, bottomStart = radius)
  }
  // Un lato aperto non porta bordo.
  //
  // Togliere gli angoli e chiudere il vuoto non bastava: il vetro disegna il proprio bordo
  // speculare tutto intorno alla sagoma, quindi due pezzi a filo mettevano due bordi uno contro
  // l'altro e la giuntura tornava a vedersi — misurata, una banda BIANCA di sei pixel dove un
  // separatore normale e' una riga scura di uno. Il bordo pero' non e' un parametro: e' dentro il
  // materiale. Quello che si puo' fare dall'esterno e' non farlo arrivare allo schermo, ritagliando
  // il lato aperto di [FluidGroupOpenEdge]. Costano due dp di riempimento della prima o dell'ultima
  // riga, che nessuno vede, e chi taglia la lista li riassorbe sovrapponendo i pezzi.
  val openTop = segment == FluidGroupSegment.Middle || segment == FluidGroupSegment.Last
  val openBottom = segment == FluidGroupSegment.Middle || segment == FluidGroupSegment.First
  val trimPx = with(LocalDensity.current) { FluidGroupOpenEdge.toPx() }
  val onGlass = contentGlassAvailable(glass)
  Surface(
    // Surface already clips its children to [shape]. A second clip created another large offscreen
    // layer for the whole group, which was especially expensive for long catalog lists.
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (!openTop && !openBottom) {
          Modifier
        } else {
          Modifier.drawWithContent {
            val top = if (openTop) trimPx else 0f
            val bottom = size.height - if (openBottom) trimPx else 0f
            clipRect(left = 0f, top = top, right = size.width, bottom = bottom) {
              this@drawWithContent.drawContent()
            }
          }
        },
      )
      .fluidContentGlass(glass, shape),
    shape = shape,
    color = if (onGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor = MaterialTheme.colorScheme.onSurface,
  ) {
    Column(modifier = Modifier.fillMaxWidth(), content = content)
  }
}

/** Separator between rows of a group, inset so it starts where the row's text does. */
@Composable
fun FluidListDivider(modifier: Modifier = Modifier) {
  HorizontalDivider(
    modifier = modifier.padding(start = 16.dp),
    thickness = 0.5.dp,
    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
  )
}


@Composable
fun FluidListRow(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  eyebrow: String? = null,
  meta: String? = null,
  tone: FluidTone = FluidTone.Neutral,
  badge: (@Composable () -> Unit)? = null,
  labels: (@Composable () -> Unit)? = null,
  titleMaxLines: Int = Int.MAX_VALUE,
  leading: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  contextActions: (() -> List<FluidContextAction>)? = null,
  animatePress: Boolean = true,
  animateContent: Boolean = false,
  selected: Boolean = false,
  disclosure: Boolean = true,
  leadingTile: Boolean = true,
) {
  FluidListRow(
    title = AnnotatedString(title),
    subtitle = subtitle,
    modifier = modifier,
    eyebrow = eyebrow,
    meta = meta,
    tone = tone,
    badge = badge,
    labels = labels,
    titleMaxLines = titleMaxLines,
    leading = leading,
    onClick = onClick,
    onLongClick = onLongClick,
    contextActions = contextActions,
    animatePress = animatePress,
    animateContent = animateContent,
    selected = selected,
    disclosure = disclosure,
    leadingTile = leadingTile,
  )
}

/**
 * La riga di un gruppo, con un titolo che puo' portare stili propri — per esempio le parole che
 * sono cambiate dall'ultima volta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FluidListRow(
  title: AnnotatedString,
  subtitle: String,
  modifier: Modifier = Modifier,
  eyebrow: String? = null,
  meta: String? = null,
  tone: FluidTone = FluidTone.Neutral,
  /**
   * Quello che sta a destra, accanto alla freccia: un valore, un interruttore, un tasto. Prende la
   * sua larghezza per primo, quindi va tenuto stretto — per le pillole di stato c'e' [labels].
   */
  badge: (@Composable () -> Unit)? = null,
  /**
   * Le pillole di stato della riga ("Compito", "Nuova", "Firmata"). Stanno accanto al titolo quando
   * c'e' posto e vanno sotto quando ruberebbero la riga: nel [badge] due pillole e la freccia
   * lasciavano al titolo una colonna di sillabe.
   */
  labels: (@Composable () -> Unit)? = null,
  /** Oltre queste righe il titolo finisce con i puntini; il testo intero sta nel dettaglio. */
  titleMaxLines: Int = Int.MAX_VALUE,
  leading: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  /**
   * Actions raised by a long press, in a menu anchored to this row.
   *
   * One parameter is the whole subscription: the row records its own picture, lifts it out of the
   * blurred page and hangs the menu off it. Prefer this to [onLongClick] — a long press that does
   * exactly one thing, and does not say which, is a gesture nobody discovers.
   */
  contextActions: (() -> List<FluidContextAction>)? = null,
  animatePress: Boolean = true,
  animateContent: Boolean = false,
  /**
   * La riga e' quella mostrata accanto, in un elenco+dettaglio ([FluidListDetailScaffold]).
   *
   * Un velo dell'accento dietro la riga, come nella barra laterale: sullo schermo largo l'elenco
   * resta visibile mentre si legge, e senza un segno non si sa da che riga viene quello che si sta
   * leggendo.
   */
  selected: Boolean = false,
  /**
   * La freccia a destra, che dice «si apre una pagina». In un elenco+dettaglio non si apre niente:
   * la riga sceglie cosa mostrare accanto, e la freccia promettere un'altra cosa.
   */
  disclosure: Boolean = true,
  /**
   * [leading] sta nella piastrella del [tone] ([FluidToneTile]). Falso: sta com'e', e il colore della
   * piastrella lo sceglie l'app con un [FluidToneTile] suo - un colore per materia, per cartella, per
   * conto - che prima si ottenevano solo disegnando fuori dai limiti del contenuto, o mettendo una
   * piastrella dentro la piastrella. Dalla 3.0.
   */
  leadingTile: Boolean = true,
) {
  val colors = toneColors(tone)
  val selectionWash by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
    animationSpec = FluidMotion.crossFade(),
    label = "fluidListRowSelection",
  )
  // The menu has to be raised from the row's *own* long-press. A separate long-press detector next
  // to a `combinedClickable` never fires — the click handler consumes the gesture first — which is
  // why the controller and the gesture are two things in the engine rather than one modifier.
  val contextMenu = contextActions?.let { rememberFluidContextMenu(it) }
  val haptics = LocalFluidHaptics.current
  // **Un nodo solo, per costruzione.** Il modificatore della riga (il clic, il ruolo, lo stato, la tinta) sta sul
  // `ListItem` stesso e non su un contenitore sopra di lui. Il `ListItem` di Material fonde da sé la propria
  // semantica, e Compose non fonde dentro un nodo che fonde un figlio che a sua volta fonde: con un `clickable` su un
  // contenitore la riga erano due nodi, uno col clic, il ruolo e lo stato ma senza nome, e uno col nome ma senza
  // clic (`FluidMatrixTest`). Sullo stesso nodo ci sono insieme il nome (titolo, sottotitolo, pillole, badge), il
  // ruolo, lo stato e il clic.
  ListItem(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (animateContent) {
          Modifier.animateContentSize(animationSpec = FluidMotion.intSize())
        } else {
          Modifier
        },
      )
      .then(
        if (contextMenu != null) Modifier.fluidContextMenuAnchor(contextMenu) else Modifier,
      )
      .drawBehind { if (selectionWash.alpha > 0f) drawRect(selectionWash) }
      .semantics { if (selected) this.selected = true }
      // A row inside a grouped list tints instead of scaling: shrinking one row of a stack breaks
      // the group's silhouette and is what made the previous treatment look unsettled.
      .fluidRowPressable(
        onClick = onClick,
        onLongClick = if (contextMenu != null) {
          {
            // Un momento, un colpo (3.0): Open se il menu si apre — e il dito passa al menu, che si
            // sceglie trascinando e rilasciando — altrimenti il click lungo dell'app col suo
            // GestureStart. Prima suonavano GestureStart qui e Open nel menu.
            if (contextMenu.open()) {
              haptics.play(FluidHapticEvent.Open)
            } else if (onLongClick != null) {
              haptics.play(FluidHapticEvent.GestureStart)
              onLongClick()
            }
          }
        } else {
          onLongClick
        },
        animateFeedback = animatePress,
        longClickHaptic = if (contextMenu != null) null else FluidHapticEvent.GestureStart,
        // Una riga che apre qualcosa e' un pulsante per chi non la vede: il ruolo sta sul nodo del
        // `ListItem`, che ha gia' il nome fuso, insieme al clic e allo stato.
        role = if (onClick != null) Role.Button else null,
      ),
    // Rows in a group share one background. Tinting individual rows by tone turned a tidy group
    // into a patchwork of coloured blocks; the tone now lives on the icon tile and the eyebrow,
    // where it labels the row instead of shouting over it.
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    overlineContent = eyebrow?.let {
      {
        Text(
          text = it,
          color = colors.content,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
    },
    headlineContent = {
      FluidRowHeadline(title = title, maxLines = titleMaxLines, labels = labels)
    },
    // Un sottotitolo vuoto non e' una riga vuota: occupava comunque l'altezza di una riga di
    // testo, e una voce con solo il titolo sembrava avere un buco sotto.
    supportingContent = if (subtitle.isBlank() && meta.isNullOrBlank()) null else {
      {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        meta?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      }
    },
    leadingContent = leading?.let {
      { if (leadingTile) FluidToneTile(tone = tone, content = it) else it() }
    },
    trailingContent = if (badge != null || (onClick != null && disclosure)) {
      {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          badge?.invoke()
          if (onClick != null && disclosure) {
            // The disclosure chevron is the one affordance that tells a row apart from a label
            // without needing colour, weight or a border to do it.
            Icon(
              imageVector = Icons.Rounded.ChevronRight,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
          }
        }
      }
    } else {
      null
    },
  )
}

/** Il lato della piastrella di un'icona ([FluidToneTile]): il contenuto sta in 19 dp al centro. */
val FluidToneTileSize: Dp = 32.dp

/** Quanto del colore del tono sta nel velo della piastrella: il sedici per cento. */
internal const val FluidToneTileVeilAlpha = 0.16f

/**
 * The rounded, filled square an icon sits in at the head of a list row.
 *
 * Carrying the row's tone here rather than on the row's background is what keeps a grouped list
 * looking like one object: the colour identifies the row without breaking the group into blocks.
 *
 * Pubblica dalla 3.0 (era `ToneIconTile`, privata): [FluidListRow] la usa da sola per il suo
 * `leading`, ma un'app che mette un'icona colorata fuori da una riga - in una card, in un messaggio,
 * in una lista tutta sua - se la riscriveva, e se la riscriveva di 30 dp, con un velo diverso e
 * i colori del tono mappati a mano. Questa e' la forma con un tono dell'engine; per un colore
 * qualunque c'e' l'altra, [FluidToneTile] con `color`.
 *
 * @param size il lato; il raggio dell'angolo e il contenuto scalano con lui (di serie [FluidToneTileSize]).
 */
@Composable
fun FluidToneTile(
  tone: FluidTone,
  modifier: Modifier = Modifier,
  size: Dp = FluidToneTileSize,
  content: @Composable () -> Unit,
) {
  val colors = toneColors(tone)
  FluidToneTile(
    color = colors.content,
    modifier = modifier,
    size = size,
    content = content,
  )
}

/**
 * La piastrella con un colore qualunque: l'icona (e il testo, via `LocalContentColor`) nel [color],
 * e dietro un velo.
 *
 * E' il caso di chi ha un colore per cosa - una materia, una cartella, un conto - e non un tono
 * dell'engine: la regola resta quella di una riga ben fatta, **il colore sta sulla piastrella e mai
 * sullo sfondo**. Il [veil] e' separato dal [color] perche' non sempre sono lo stesso colore: una
 * materia ha un riempimento e un inchiostro per l'icona scelti per leggersi l'uno sull'altro, e il
 * sedici per cento dell'inchiostro non e' il suo riempimento.
 *
 * @param color il colore dell'icona.
 * @param veil il velo dietro l'icona; di serie [color] al 16%, come la piastrella di un tono.
 * @param size il lato; il raggio dell'angolo e il contenuto scalano con lui.
 */
@Composable
fun FluidToneTile(
  color: Color,
  modifier: Modifier = Modifier,
  size: Dp = FluidToneTileSize,
  veil: Color = color.copy(alpha = FluidToneTileVeilAlpha),
  content: @Composable () -> Unit,
) {
  // Il raggio e il contenuto seguono il lato: 10 dp e 19 dp a 32, che e' la piastrella di una riga,
  // e la stessa forma a qualunque taglia (un 10 dp fisso su una piastrella da 56 e' un quadrato).
  val scale = size / FluidToneTileSize
  Box(
    modifier = modifier
      .size(size)
      .background(veil, ContinuousCornerShape(FluidRadius.Small * scale)),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(LocalContentColor provides color) {
      Box(modifier = Modifier.size(19.dp * scale)) { content() }
    }
  }
}

private data class ToneColors(
  val container: Color,
  val content: Color,
)

@Composable
private fun toneColors(tone: FluidTone): ToneColors {
  // The app can force Light, Dark or AMOLED independently from the operating-system theme, so
  // this cannot ask the system. It used to ask `background.luminance()` instead, which is worse:
  // an app that paints an image behind its pages leaves `background` transparent so the image can
  // show through, and a transparent colour has luminance zero — so this said "dark" on every side
  // and the light theme got dark containers with light text in them. The theme knows which side it
  // resolved to, and that answer cannot be faked.
  val isDark = dev.antigravity.fluidengine.ui.fluid.GlassDefaults.isDarkSurface()
  return when (tone) {
    FluidTone.Primary -> ToneColors(
      MaterialTheme.colorScheme.primaryContainer,
      MaterialTheme.colorScheme.onPrimaryContainer,
    )
    FluidTone.Success -> if (isDark) {
      ToneColors(Color(0xFF0F2D1D), Color(0xFF81C784))
    } else {
      ToneColors(Color(0xFFE8F5E9), Color(0xFF1B5E20))
    }
    FluidTone.Warning -> if (isDark) {
      ToneColors(Color(0xFF332000), Color(0xFFFFB74D))
    } else {
      ToneColors(Color(0xFFFFF3E0), Color(0xFFE65100))
    }
    // Il contenuto e' `error`, non `onErrorContainer`: questo colore va sulla piastrella di una riga
    // e sul suo occhiello, sopra il gruppo, e `onErrorContainer` sul chiaro e' un bruno quasi nero.
    // Una verifica o una nota disciplinare finivano con la piastrella grigio scuro accanto al badge
    // rosso della stessa riga: due segni per la stessa cosa, di due colori diversi.
    FluidTone.Danger -> ToneColors(
      MaterialTheme.colorScheme.errorContainer,
      MaterialTheme.colorScheme.error,
    )
    FluidTone.Info -> ToneColors(
      MaterialTheme.colorScheme.secondaryContainer,
      MaterialTheme.colorScheme.onSecondaryContainer,
    )
    FluidTone.Neutral -> ToneColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/**
 * La scheda in evidenza di prima: un titolo, una frase e un'icona su un contenitore tinto.
 *
 * È una `Surface` di Material con l'angolo `extraLarge`, cioè circolare — una violazione della
 * prima regola del design system dentro il design system. Disegna un riquadro tinto e nient'altro:
 * niente tono che segua la sezione, niente motivo, niente dati, e l'icona di default è un cappello
 * da laureato — un'idea di dominio che l'engine non dovrebbe avere.
 *
 * Deprecata e non cancellata (la rimozione è prevista per la 3.0). Non c'è una sostituzione a una
 * riga, perché le due strade che la rimpiazzano non hanno la stessa forma:
 *
 *  - dove la scheda apre una schermata piena di dati, è [dev.antigravity.fluidengine.ui.fluid.FluidHero]
 *    (tono, motivo, un valore in primo piano, fatti di contorno, azione);
 *  - dove serviva solo un riquadro che dica una cosa, è [FluidCard] con `highlighted = true` e il
 *    testo dentro, con l'angolo continuo e la pressione dell'engine.
 */
@Deprecated(
  message = "Surface di Material con l'angolo circolare e un'icona di default da scuola. Usa FluidHero " +
    "(schermata piena di dati: tono, motivo, valore) oppure FluidCard(highlighted = true) per un " +
    "riquadro che dice una cosa.",
  level = DeprecationLevel.WARNING,
)
@Composable
fun FluidHeroCard(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.primaryContainer,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(20.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      trailing?.invoke() ?: Icon(
        imageVector = Icons.Filled.School,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
fun FluidSectionTitle(
  eyebrow: String,
  title: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = eyebrow.uppercase(),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onBackground,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
fun FluidStatChip(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .clip(ContinuousCornerShape(FluidRadius.Card))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

/**
 * Uno stato vuoto: dice **perche'** non c'e' niente e cosa si puo' fare.
 *
 * @param illustration un disegno sopra il titolo: un'icona grande, un vettoriale, qualunque cosa
 *   l'app abbia per riconoscere quella schermata vuota. Dalla 3.0. MyCloud si era scritto una
 *   vista tutta sua per averlo, e l'engine non puo' decidere cosa disegnare (non conosce il
 *   dominio) ma puo' dargli un posto: con l'illustrazione la colonna si centra, come un
 *   disegno si aspetta, e prende un po' piu' di aria; senza, e' quella di sempre, allineata a
 *   sinistra come una riga. L'illustrazione eredita un colore quieto (`onSurfaceVariant`) e
 *   non e' letta da TalkBack: e' decorazione, il titolo dice tutto.
 */
@Composable
fun FluidEmptyState(
  title: String,
  detail: String,
  modifier: Modifier = Modifier,
  illustration: (@Composable () -> Unit)? = null,
) {
  val centered = illustration != null
  val textAlign = if (centered) TextAlign.Center else TextAlign.Start
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp, vertical = if (centered) 24.dp else 12.dp),
    horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (illustration != null) {
      Box(
        modifier = Modifier.padding(bottom = 12.dp).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
      ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
          illustration()
        }
      }
    }
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
      textAlign = textAlign,
    )
    Text(
      text = detail,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = textAlign,
    )
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FluidLoading(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
) {
  FluidSpinner(
    modifier = modifier,
    size = 24.dp,
    color = color,
  )
}

/**
 * La riga semplice di prima, sotto il nome che le app già chiamano.
 *
 * Era una `ListItem` di Material scritta a mano, con una gerarchia sua — il «sottotitolo» *sopra* il
 * titolo, in tinta d'accento, e la frase sotto chiamata `supporting` — e una freccia di Material a
 * destra. Adesso è [FluidListRow], la riga di tutto il design system: stessa disposizione dei testi
 * (il vecchio `subtitle` è l'`eyebrow`, il vecchio `supporting` è il `subtitle`), stessa pressione a
 * tinta, stessa freccia, e `trailing` va nel `badge`.
 *
 * Una cosa resta come prima: un `trailing` proprio sostituisce la freccia, non le sta accanto.
 *
 * Cosa si vede cambiare: i testi prendono la tipografia di [FluidListRow] (la frase di sostegno è
 * `bodyMedium` e non più `bodySmall`), l'eyebrow ha la tinta del tono `Primary` invece dell'accento
 * pieno, e la freccia è la chevron del design system. Fuori da un [FluidListGroup] resta una riga
 * sola, senza il contenitore, come prima.
 *
 * Deprecata e non cancellata (la rimozione è prevista per la 3.0).
 */
@Deprecated(
  message = "Era una ListItem di Material con una gerarchia sua; adesso è una FluidListRow. Usa " +
    "FluidListRow (il vecchio subtitle è l'eyebrow, il vecchio supporting è il subtitle).",
  replaceWith = ReplaceWith(
    "FluidListRow(title = title, subtitle = supporting ?: \"\", modifier = modifier, " +
      "eyebrow = subtitle, tone = FluidTone.Primary, badge = trailing, onClick = onClick)",
    "dev.antigravity.fluidengine.ui.theme.FluidListRow",
    "dev.antigravity.fluidengine.ui.theme.FluidTone",
  ),
)
@Composable
fun FluidListItem(
  title: String,
  subtitle: String,
  supporting: String? = null,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  FluidListRow(
    title = title,
    subtitle = supporting.orEmpty(),
    modifier = modifier,
    eyebrow = subtitle,
    // L'eyebrow di una riga prende il colore del tono, e il tono di una riga senza icona non ha altro
    // da colorare: Primary tiene la linea in cima all'accento, com'era.
    tone = FluidTone.Primary,
    badge = trailing,
    // Un `trailing` proprio sostituiva la freccia; nella riga nuova il badge le sta accanto.
    disclosure = trailing == null,
    onClick = onClick,
  )
}
