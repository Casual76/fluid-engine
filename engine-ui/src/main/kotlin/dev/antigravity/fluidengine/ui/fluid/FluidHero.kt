package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Dove una schermata si colloca nella famiglia cromatica dell'app.
 *
 * Non è "scegli un colore": è una posizione su un anello costruito attorno alle tre famiglie di
 * accento di Material, con le miscele nei punti intermedi. Serve a far sì che sei o sette
 * destinazioni si distinguano restando evidentemente imparentate — dare a ognuna un colore
 * arbitrario le fa sembrare sette app diverse, e darle tutte uguali le rende indistinguibili.
 *
 * Il dominio resta fuori: l'engine non sa cosa sia una sezione "voti" o "assenze". L'app mappa le
 * sue destinazioni su questo anello.
 */
enum class FluidHeroTone {
  Primary,
  PrimaryToSecondary,
  Secondary,
  SecondaryToTertiary,
  Tertiary,
  TertiaryToPrimary,

  /**
   * Il tono di una sezione che a volte ha qualcosa da segnalare: un secondario appena virato verso
   * l'errore. Con `urgent` diventa errore pieno.
   */
  Alert,
}

/**
 * Il motivo astratto disegnato dietro l'intestazione.
 *
 * Sono forme, non illustrazioni: barre, punti, onde. È quello che rende riconoscibili due schermate
 * vicine anche quando condividono di proposito la stessa famiglia tonale.
 */
enum class FluidHeroMotif {
  /** Nessuna forma, solo un alone: per la schermata che deve sembrare luminosa e non illustrata. */
  Glow,
  Bars,
  Dots,
  Ripples,
  Ticks,
  Cards,
  Figures,
}

@Immutable
data class FluidHeroMetric(
  val label: String,
  val value: String,
  val onClick: (() -> Unit)? = null,
)

@Immutable
private data class FluidHeroColors(
  val container: Color,
  val content: Color,
  val accent: Color,
  val washStrength: Float,
)

/**
 * L'apertura editoriale di una schermata piena di dati.
 *
 * Dà a un solo valore vero la priorità e tiene compatti i fatti secondari. I valori scorrono in una
 * finestra ritagliata quando cambiano, così un aggiornamento si vede senza disegnare per un istante
 * due copie traslucide di tutta la card. I fatti di contorno passano da tre a due a una colonna
 * secondo lo spazio misurato e la scala del carattere.
 *
 * La navigazione resta attaccata a un fatto esplicito o alla chiamata all'azione: la superficie
 * editoriale non nasconde mai più destinazioni dietro un unico bersaglio.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FluidHero(
  tone: FluidHeroTone,
  motif: FluidHeroMotif,
  eyebrow: String,
  value: String,
  title: String,
  description: String,
  icon: ImageVector,
  modifier: Modifier = Modifier,
  metrics: List<FluidHeroMetric> = emptyList(),
  urgent: Boolean = false,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  val colors = fluidHeroColors(tone, urgent)
  val shape = ContinuousCornerShape(28.dp)

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = shape,
    color = colors.container,
    contentColor = colors.content,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(
          Brush.linearGradient(
            colors = listOf(
              colors.container,
              lerp(colors.container, colors.accent, colors.washStrength * 0.35f),
              lerp(colors.container, colors.accent, colors.washStrength),
            ),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
          ),
        ),
    ) {
      FluidHeroDecoration(
        motif = motif,
        color = colors.accent,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(width = 152.dp, height = 132.dp),
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier
              .size(42.dp)
              .background(colors.content.copy(alpha = 0.11f), ContinuousCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              modifier = Modifier.size(22.dp),
              tint = colors.content,
            )
          }
          Text(
            text = eyebrow.uppercase(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.content.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Column(
          modifier = Modifier.widthIn(max = 330.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          RollingHeroValue(value = value, color = colors.content)
          Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.content,
          )
          Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.content.copy(alpha = 0.76f),
          )
        }

        if (metrics.isNotEmpty()) {
          FluidHeroMetrics(
            metrics = metrics.take(3),
            contentColor = colors.content,
          )
        }

        if (actionLabel != null && onAction != null) {
          FluidHeroAction(
            label = actionLabel,
            contentColor = colors.content,
            onClick = onAction,
          )
        }
      }
    }
  }
}

@Composable
private fun FluidHeroAction(
  label: String,
  contentColor: Color,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .heightIn(min = 48.dp)
      .fluidPressable(
        onClick = onClick,
        pressedScale = 0.965f,
        role = Role.Button,
      )
      .background(contentColor.copy(alpha = 0.11f), FluidCapsuleShape)
      .padding(horizontal = 15.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      color = contentColor,
    )
    Icon(
      imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
      tint = contentColor.copy(alpha = 0.78f),
    )
  }
}

/**
 * Il valore grande, che scorre quando cambia.
 *
 * `SizeTransform(clip = true)` non è un dettaglio: senza, durante il cambio si vedono per un
 * istante due copie traslucide sovrapposte, che è il modo in cui un numero che si aggiorna sembra
 * un errore di disegno invece che un aggiornamento.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RollingHeroValue(
  value: String,
  color: Color,
) {
  val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
  AnimatedContent(
    targetState = value,
    transitionSpec = {
      slideInVertically(
        animationSpec = spatialSpec,
        initialOffsetY = { height -> height },
      ) togetherWith slideOutVertically(
        animationSpec = spatialSpec,
        targetOffsetY = { height -> -height },
      ) using SizeTransform(clip = true)
    },
    contentKey = { it },
    label = "fluidHeroValue",
  ) { animatedValue ->
    Text(
      text = animatedValue,
      style = MaterialTheme.typography.displaySmall,
      fontWeight = FontWeight.Bold,
      color = color,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun FluidHeroMetrics(
  metrics: List<FluidHeroMetric>,
  contentColor: Color,
) {
  val fontScale = LocalDensity.current.fontScale
  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
    val columnCount = fluidHeroMetricColumnCount(
      availableWidthDp = maxWidth.value,
      fontScale = fontScale,
      metricCount = metrics.size,
    )
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(HeroMetricGapDp.dp),
    ) {
      metrics.chunked(columnCount).forEach { rowMetrics ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
          horizontalArrangement = Arrangement.spacedBy(HeroMetricGapDp.dp),
        ) {
          rowMetrics.forEach { metric ->
            FluidHeroMetricCell(
              metric = metric,
              contentColor = contentColor,
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            )
          }
        }
      }
    }
  }
}

private const val HeroMetricGapDp = 8f
private const val HeroMetricMinCellWidthDp = 104f

/**
 * Quante colonne stanno nello spazio misurato.
 *
 * Politica di layout pura, tenuta visibile ai test: è l'unica parte dell'intestazione che si può
 * verificare senza disegnare, ed è quella che si rompe quando qualcuno alza la scala del carattere.
 */
internal fun fluidHeroMetricColumnCount(
  availableWidthDp: Float,
  fontScale: Float,
  metricCount: Int,
): Int {
  if (metricCount <= 0) return 0
  val cappedMetricCount = metricCount.coerceAtMost(3)
  val scaledMinimumCellWidth = HeroMetricMinCellWidthDp * fontScale.coerceIn(1f, 1.7f)
  val columnsByWidth = (
    (availableWidthDp + HeroMetricGapDp) /
      (scaledMinimumCellWidth + HeroMetricGapDp)
    ).toInt()
  return columnsByWidth.coerceIn(1, cappedMetricCount)
}

@Composable
private fun FluidHeroMetricCell(
  metric: FluidHeroMetric,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  val interaction = if (metric.onClick != null) {
    Modifier.fluidPressable(
      onClick = metric.onClick,
      pressedScale = 0.965f,
      role = Role.Button,
    )
  } else {
    Modifier
  }
  Column(
    modifier = modifier
      .heightIn(min = 78.dp)
      .then(interaction)
      .background(
        contentColor.copy(alpha = if (metric.onClick != null) 0.105f else 0.075f),
        ContinuousCornerShape(13.dp),
      )
      .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = metric.value,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (metric.onClick != null) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
          contentDescription = null,
          modifier = Modifier.size(15.dp),
          tint = contentColor.copy(alpha = 0.62f),
        )
      }
    }
    Text(
      text = metric.label,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor.copy(alpha = 0.74f),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Il tono, risolto sullo schema corrente.
 *
 * [urgent] promuove qualunque tono alla famiglia dell'errore, non solo [FluidHeroTone.Alert]: una
 * schermata che ha qualcosa di urgente da dire lo dice allo stesso modo ovunque.
 */
@Composable
private fun fluidHeroColors(tone: FluidHeroTone, urgent: Boolean): FluidHeroColors {
  val scheme = MaterialTheme.colorScheme
  if (urgent) {
    return FluidHeroColors(
      container = scheme.errorContainer,
      content = scheme.onErrorContainer,
      accent = scheme.error,
      washStrength = 0.14f,
    )
  }
  return when (tone) {
    FluidHeroTone.Primary -> FluidHeroColors(
      container = scheme.primaryContainer,
      content = scheme.onPrimaryContainer,
      accent = scheme.primary,
      washStrength = 0.09f,
    )

    FluidHeroTone.PrimaryToSecondary -> FluidHeroColors(
      container = lerp(scheme.primaryContainer, scheme.secondaryContainer, 0.22f),
      content = scheme.onPrimaryContainer,
      accent = scheme.secondary,
      washStrength = 0.13f,
    )

    FluidHeroTone.Secondary -> FluidHeroColors(
      container = scheme.secondaryContainer,
      content = scheme.onSecondaryContainer,
      accent = scheme.secondary,
      washStrength = 0.10f,
    )

    FluidHeroTone.SecondaryToTertiary -> FluidHeroColors(
      container = lerp(scheme.secondaryContainer, scheme.tertiaryContainer, 0.20f),
      content = scheme.onSecondaryContainer,
      accent = scheme.tertiary,
      washStrength = 0.13f,
    )

    FluidHeroTone.Tertiary -> FluidHeroColors(
      container = scheme.tertiaryContainer,
      content = scheme.onTertiaryContainer,
      accent = scheme.tertiary,
      washStrength = 0.11f,
    )

    FluidHeroTone.TertiaryToPrimary -> FluidHeroColors(
      container = lerp(scheme.tertiaryContainer, scheme.primaryContainer, 0.20f),
      content = scheme.onTertiaryContainer,
      accent = scheme.primary,
      washStrength = 0.13f,
    )

    FluidHeroTone.Alert -> FluidHeroColors(
      container = lerp(scheme.secondaryContainer, scheme.errorContainer, 0.14f),
      content = scheme.onSecondaryContainer,
      accent = scheme.error,
      washStrength = 0.11f,
    )
  }
}

@Composable
private fun FluidHeroDecoration(
  motif: FluidHeroMotif,
  color: Color,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier = modifier) {
    val faint = color.copy(alpha = 0.13f)
    val medium = color.copy(alpha = 0.23f)
    when (motif) {
      FluidHeroMotif.Glow -> {
        // Un alone, non un disegno. La versione precedente era un cerchio sovradimensionato con un
        // arco che uscivano dai limiti del Canvas e venivano ritagliati in un'ellisse spezzata.
        val center = Offset(size.width * 0.72f, size.height * 0.36f)
        val radius = size.minDimension * 0.30f
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.075f), Color.Transparent),
            center = center,
            radius = radius,
          ),
          radius = radius,
          center = center,
        )
      }

      FluidHeroMotif.Bars -> {
        val heights = listOf(0.24f, 0.42f, 0.63f, 0.82f)
        heights.forEachIndexed { index, height ->
          val barWidth = size.width * 0.095f
          val left = size.width * (0.38f + index * 0.14f)
          drawRoundRect(
            color = if (index == heights.lastIndex) medium else faint,
            topLeft = Offset(left, size.height * (0.92f - height)),
            size = Size(barWidth, size.height * height),
            cornerRadius = CornerRadius(barWidth / 2f),
          )
        }
      }

      FluidHeroMotif.Dots -> {
        repeat(3) { row ->
          repeat(3) { column ->
            drawCircle(
              color = if (row == 1 && column == 1) medium else faint,
              radius = if (row == 1 && column == 1) 8.dp.toPx() else 5.dp.toPx(),
              center = Offset(size.width * (0.52f + column * 0.17f), size.height * (0.28f + row * 0.21f)),
            )
          }
        }
      }

      FluidHeroMotif.Ripples -> {
        repeat(3) { index ->
          drawArc(
            color = if (index == 0) medium else faint,
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(size.width * (0.33f - index * 0.07f), size.height * (0.23f - index * 0.07f)),
            size = Size(size.width * (0.42f + index * 0.20f), size.height * (0.42f + index * 0.20f)),
            style = Stroke(width = 3.dp.toPx()),
          )
        }
        drawCircle(medium, 5.dp.toPx(), Offset(size.width * 0.66f, size.height * 0.72f))
      }

      FluidHeroMotif.Ticks -> {
        repeat(5) { index ->
          val x = size.width * (0.35f + index * 0.13f)
          val height = size.height * (0.30f + (index % 3) * 0.12f)
          drawLine(
            color = if (index == 3) medium else faint,
            start = Offset(x, size.height * 0.78f - height),
            end = Offset(x, size.height * 0.78f),
            strokeWidth = if (index == 3) 6.dp.toPx() else 3.dp.toPx(),
          )
        }
      }

      FluidHeroMotif.Cards -> {
        repeat(4) { index ->
          val top = size.height * (0.20f + index * 0.16f)
          drawRoundRect(
            color = if (index == 1) medium else faint,
            topLeft = Offset(size.width * (0.36f + (index % 2) * 0.08f), top),
            size = Size(size.width * 0.48f, size.height * 0.095f),
            cornerRadius = CornerRadius(6.dp.toPx()),
          )
        }
      }

      FluidHeroMotif.Figures -> {
        drawCircle(medium, size.minDimension * 0.13f, Offset(size.width * 0.60f, size.height * 0.34f))
        drawCircle(faint, size.minDimension * 0.11f, Offset(size.width * 0.80f, size.height * 0.42f))
        val path = Path().apply {
          moveTo(size.width * 0.42f, size.height * 0.82f)
          quadraticTo(size.width * 0.62f, size.height * 0.52f, size.width * 0.82f, size.height * 0.82f)
          close()
        }
        drawPath(path, faint)
      }
    }
  }
}
