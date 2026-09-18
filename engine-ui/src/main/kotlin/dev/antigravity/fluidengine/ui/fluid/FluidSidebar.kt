package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object FluidSidebarDefaults {
  /** L'aria fra la barra e i bordi della finestra: galleggia, non e' incollata. */
  val Margin: Dp = 12.dp
  val RowHeight: Dp = 44.dp
}

/**
 * La barra laterale di una pagina larga: le sezioni dell'app e le sue cose di primo livello, in
 * una colonna di vetro.
 *
 * E' chrome — sta di fianco alla pagina, non dentro — quindi e' di vetro come il rail e la
 * pillola, mentre le liste della pagina non lo sono piu'. Il contenuto e' una `LazyColumn`: una
 * barra con cento materie deve scorrere, e la testa e il piede restano fermi.
 */
@Composable
fun FluidSidebar(
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  header: (@Composable ColumnScope.() -> Unit)? = null,
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  content: LazyListScope.() -> Unit,
) {
  val shape = ContinuousCornerShape(FluidRadius.Group)
  Column(
    modifier = modifier
      .fillMaxHeight()
      .windowInsetsPadding(WindowInsets.systemBars)
      .padding(FluidSidebarDefaults.Margin)
      .glassSurface(
        state = backdrop,
        tint = GlassDefaults.floatingTint(),
        shape = shape,
        role = GlassRole.Floating,
      )
      .clip(shape),
  ) {
    if (header != null) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        content = header,
      )
    }
    LazyColumn(
      modifier = Modifier.weight(1f).fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
      content = content,
    )
    if (footer != null) {
      FluidHairline()
      Column(modifier = Modifier.fillMaxWidth().padding(8.dp), content = footer)
    }
  }
}

/** Il titolo di un gruppo di righe nella barra laterale: «Materie», «Altro». */
@Composable
fun FluidSidebarSection(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
  )
}

/**
 * Una riga della barra laterale: una sezione dell'app, o una materia con la sua piastrella.
 *
 * La selezione e' un velo dell'accento dietro la riga, non una pillola di vetro: dentro una barra
 * gia' di vetro un secondo vetro non si vede, e un velo si legge anche con la coda dell'occhio.
 */
@Composable
fun FluidSidebarRow(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  /** Il colore della piastrella dell'icona: quello della materia. Senza, l'accento. */
  tint: Color? = null,
  /** Un numero o una parola a destra: «12», «oggi». */
  detail: String? = null,
  badge: String? = null,
  contextActions: (() -> List<FluidContextAction>)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val shape = ContinuousCornerShape(FluidRadius.Control)
  val accent = tint ?: scheme.primary
  val contextMenu = contextActions?.let { rememberFluidContextMenu(it, tint = tint) }
  val wash by animateColorAsState(
    targetValue = if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
    label = "fluidSidebarSelection",
  )

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(shape)
      .background(wash)
      .then(if (contextMenu != null) Modifier.fluidContextMenuAnchor(contextMenu) else Modifier)
      .fluidPressable(
        onClick = onClick,
        onLongClick = contextMenu?.let { menu -> { menu.open() } },
        pressedScale = 0.98f,
        role = Role.Tab,
      )
      .semantics { this.selected = selected }
      .heightIn(min = FluidSidebarDefaults.RowHeight)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Box(
        modifier = Modifier
          .size(26.dp)
          .clip(ContinuousCornerShape(8.dp))
          .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
      }
    }
    Text(
      text = label,
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
      color = if (selected) scheme.primary else scheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (detail != null) {
      Text(
        text = detail,
        style = MaterialTheme.typography.labelMedium,
        color = scheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
    if (badge != null) {
      Text(
        text = badge,
        style = MaterialTheme.typography.labelSmall,
        color = scheme.onPrimary,
        modifier = Modifier
          .clip(FluidCapsuleShape)
          .background(scheme.primary)
          .padding(horizontal = 7.dp, vertical = 2.dp),
      )
    }
  }
}
