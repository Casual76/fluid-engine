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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.rememberFluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassSurface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
  return this.glassSurface(
    state = canvas,
    tint = GlassDefaults.contentTint(),
    shape = shape,
    role = GlassRole.Content,
  )
}

/** Whether a container asking for glass will actually get it here. Drives its own fill colour. */
@Composable
private fun contentGlassAvailable(enabled: Boolean): Boolean =
  enabled && LocalFluidCanvasBackdrop.current != null

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

  Box(
    modifier = modifier
      .size(SyncIndicatorSize)
      .background(container, CircleShape)
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

  FluidSyncIndicator(
    status = status,
    modifier = modifier
      .clip(CircleShape)
      .clickable(role = Role.Button) { showDetails = true },
  )

  if (showDetails) {
    FluidSheet(
      onDismissRequest = { showDetails = false },
      title = "Sincronizzazione",
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
              showDetails = false
              onRetry()
            },
            fillWidth = true,
          )
        }
      }
    }
  }
}

@Composable
fun FluidInlineMessage(
  message: String,
  modifier: Modifier = Modifier,
  title: String = "Messaggio",
  tone: FluidTone = FluidTone.Info,
  onDismiss: (() -> Unit)? = null,
) {
  FluidCard(
    modifier = modifier,
    highlighted = tone != FluidTone.Neutral,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = toneColors(tone).content,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      onDismiss?.let {
        IconButton(onClick = it) {
          Icon(Icons.Rounded.Close, contentDescription = "Nascondi")
        }
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

@Composable
fun FluidQuickAction(
  label: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  AssistChip(
    modifier = modifier,
    onClick = onClick,
    label = { Text(label) },
    colors = AssistChipDefaults.assistChipColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
      leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      trailingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ),
  )
}

private val lastSyncDateFormatter: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())

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
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = ContinuousCornerShape(FluidRadius.Group)
  val onGlass = contentGlassAvailable(glass)
  Surface(
    // Surface already clips its children to [shape]. A second clip created another large offscreen
    // layer for the whole group, which was especially expensive for long catalog lists.
    modifier = modifier.fillMaxWidth().fluidContentGlass(glass, shape),
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FluidListRow(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  eyebrow: String? = null,
  meta: String? = null,
  tone: FluidTone = FluidTone.Neutral,
  badge: (@Composable () -> Unit)? = null,
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
) {
  val colors = toneColors(tone)
  // The menu has to be raised from the row's *own* long-press. A separate long-press detector next
  // to a `combinedClickable` never fires — the click handler consumes the gesture first — which is
  // why the controller and the gesture are two things in the engine rather than one modifier.
  val contextMenu = contextActions?.let { rememberFluidContextMenu(it) }
  Column(
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
      // A row inside a grouped list tints instead of scaling: shrinking one row of a stack breaks
      // the group's silhouette and is what made the previous treatment look unsettled.
      .fluidRowPressable(
        onClick = onClick,
        onLongClick = if (contextMenu != null) {
          { if (!contextMenu.open()) onLongClick?.invoke() }
        } else {
          onLongClick
        },
        animateFeedback = animatePress,
      ),
  ) {
    ListItem(
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
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      },
      supportingContent = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          meta?.takeIf { it.isNotBlank() }?.let {
            Text(
              text = it,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
      leadingContent = leading?.let {
        { ToneIconTile(tone = tone, content = it) }
      },
      trailingContent = if (badge != null || onClick != null) {
        {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            badge?.invoke()
            if (onClick != null) {
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
}

/**
 * The rounded, filled square an icon sits in at the head of a list row.
 *
 * Carrying the row's tone here rather than on the row's background is what keeps a grouped list
 * looking like one object: the colour identifies the row without breaking the group into blocks.
 */
@Composable
private fun ToneIconTile(
  tone: FluidTone,
  content: @Composable () -> Unit,
) {
  val colors = toneColors(tone)
  Box(
    modifier = Modifier
      .size(32.dp)
      .background(colors.content.copy(alpha = 0.16f), ContinuousCornerShape(FluidRadius.Small)),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(LocalContentColor provides colors.content) {
      Box(modifier = Modifier.size(19.dp)) { content() }
    }
  }
}

private data class ToneColors(
  val container: Color,
  val content: Color,
)

@Composable
private fun toneColors(tone: FluidTone): ToneColors {
  // The app can force Light, Dark or AMOLED independently from the operating-system theme.
  val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
    FluidTone.Danger -> ToneColors(
      MaterialTheme.colorScheme.errorContainer,
      MaterialTheme.colorScheme.onErrorContainer,
    )
    FluidTone.Info -> ToneColors(
      MaterialTheme.colorScheme.secondaryContainer,
      MaterialTheme.colorScheme.onSecondaryContainer,
    )
    FluidTone.Neutral -> ToneColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

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

@Composable
fun FluidEmptyState(
  title: String,
  detail: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 4.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = detail,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
fun FluidListItem(
  title: String,
  subtitle: String,
  supporting: String? = null,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .fluidRowPressable(onClick = onClick),
  ) {
    ListItem(
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      headlineContent = {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      },
      overlineContent = {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
      },
      supportingContent = supporting?.let {
        {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      trailingContent = trailing ?: if (onClick != null) {
        {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        null
      },
    )
  }
}
