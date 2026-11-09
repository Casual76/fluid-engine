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

  /**
   * L'altezza di una riga che deve essere un bersaglio da 48 dp (QLT-21): si passa come `minHeight` a
   * [FluidSidebarRow]. [RowHeight] resta 44 dp, perche' cambiarla sposterebbe tutte le barre che esistono.
   */
  val ComfortableRowHeight: Dp = 48.dp

  /** Il rientro di un livello di annidamento: 16 dp, quanto lo spazio fra la piastrella e il testo piu' un poco. */
  val IndentPerLevel: Dp = 16.dp

  /**
   * Fin dove cresce il rientro: oltre il sesto livello le righe non si spostano piu'. Una barra larga
   * 360 dp con una cartella a dieci livelli di profondita' non deve lasciare al nome tre lettere.
   */
  const val MaxIndentLevels: Int = 6
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

/**
 * Il titolo di un gruppo di righe nella barra laterale: «Materie», «Altro».
 *
 * @param leading un segno prima del titolo: l'avatar o il colore di un account, quando il gruppo e'
 *   «le cartelle di questo account». Non e' toccabile (e' un titolo): se serve un'intestazione che
 *   si apre, e' una [FluidSidebarRow].
 * @param detail una parola o un numero a destra: «12», l'indirizzo dell'account.
 * @param uppercase il titolo in maiuscolo (di serie, com'e' sempre stato). Va spento quando il titolo e'
 *   un nome proprio o un indirizzo, che le maiuscole deformano.
 */
@Composable
fun FluidSidebarSection(
  title: String,
  modifier: Modifier = Modifier,
  leading: (@Composable () -> Unit)? = null,
  detail: String? = null,
  uppercase: Boolean = true,
) {
  val titleText: @Composable (Modifier) -> Unit = { textModifier ->
    Text(
      text = if (uppercase) title.uppercase() else title,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = textModifier,
    )
  }
  if (leading == null && detail == null) {
    // Il percorso di sempre: un testo solo, senza una Row in mezzo.
    titleText(modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp))
    return
  }
  Row(
    modifier = modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leading?.invoke()
    titleText(Modifier.weight(1f))
    if (detail != null) {
      Text(
        text = detail,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

/**
 * Una riga della barra laterale: una sezione dell'app, o una materia con la sua piastrella.
 *
 * La selezione e' un velo dell'accento dietro la riga, non una pillola di vetro: dentro una barra
 * gia' di vetro un secondo vetro non si vede, e un velo si legge anche con la coda dell'occhio.
 *
 * Per le righe annidate, con un contenuto proprio o piu' alte c'e' l'overload con `level`, `leading`,
 * `trailing` e `minHeight` (3.1.0). Questa firma e' quella della 3.0 e resta identica: [contextActions]
 * e' l'ultimo parametro, quindi una lambda finale (`FluidSidebarRow(…) { listOf(…) }`) e' sempre il menu.
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
  FluidSidebarRow(
    label = label,
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    icon = icon,
    tint = tint,
    detail = detail,
    badge = badge,
    level = 0,
    leading = null,
    trailing = null,
    minHeight = FluidSidebarDefaults.RowHeight,
    contextActions = contextActions,
  )
}

/**
 * La riga della barra laterale con le aggiunte della 3.1.0 (pm-f1): annidata ([level]), con un
 * contenuto proprio prima del testo ([leading]) o in coda ([trailing]), e piu' alta ([minHeight]).
 * Con i valori di serie e' la riga dell'altro [FluidSidebarRow], pixel per pixel.
 *
 * **Perche' un overload e non parametri in coda.** L'ultimo parametro della firma della 3.0 e' una
 * lambda ([contextActions]): parametri aggiunti dopo di lei toglievano la lambda finale a chi la usava,
 * e inserirli prima spostava le chiamate posizionali. Qui i nuovi stanno fra [badge] e [contextActions],
 * che resta **l'ultimo**: la lambda finale e' il menu anche qui (`FluidSidebarRow(…, level = 1) { … }`).
 * Una chiamata scritta per la 3.0 sceglie sempre l'altra firma: con nove argomenti posizionali il nono
 * qui sarebbe [level], un `Int`; con meno, Kotlin preferisce la funzione che lascia meno parametri al
 * valore di serie. Le due, coi valori di serie, disegnano la stessa riga.
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
  /**
   * Il livello di annidamento: 0 e' la riga di sempre, ogni livello in piu' la sposta di
   * [FluidSidebarDefaults.IndentPerLevel] (fino a [FluidSidebarDefaults.MaxIndentLevels]). Per le
   * cartelle dentro le cartelle. Il velo della selezione resta largo quanto la riga: e' la riga ad
   * essere scelta, non il rientro. Fuori scala (negativo) vale 0.
   */
  level: Int = 0,
  /**
   * Il contenuto prima del testo, **al posto** della piastrella di [icon]: un avatar, un pallino
   * di colore, la freccia che apre un gruppo. Se c'e', [icon] e [tint] non si disegnano.
   */
  leading: (@Composable () -> Unit)? = null,
  /**
   * Un contenuto in coda (un `FluidBadge` coi non letti), dopo [detail] e [badge]. Non cambia la
   * semantica della riga: se porta un numero, la riga lo dice una volta sola.
   */
  trailing: (@Composable () -> Unit)? = null,
  /**
   * L'altezza minima della riga; di serie [FluidSidebarDefaults.RowHeight] (44 dp). Una riga che e' il
   * bersaglio principale di una barra usabile col dito passa [FluidSidebarDefaults.ComfortableRowHeight]
   * (48 dp); col testo grande la riga cresce comunque.
   */
  minHeight: Dp = FluidSidebarDefaults.RowHeight,
  /** Il menu della riga tenuta premuta, come nell'altra firma. Ultimo, per la lambda finale. */
  contextActions: (() -> List<FluidContextAction>)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val shape = ContinuousCornerShape(FluidRadius.Control)
  val accent = tint ?: scheme.primary
  val contextMenu = contextActions?.let { rememberFluidContextMenu(it, tint = tint) }
  val wash by animateColorAsState(
    targetValue = if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
    animationSpec = FluidMotion.flick(),
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
      .heightIn(min = minHeight)
      .padding(
        start = 12.dp + FluidSidebarDefaults.IndentPerLevel * level.coerceIn(0, FluidSidebarDefaults.MaxIndentLevels),
        end = 12.dp,
        top = 8.dp,
        bottom = 8.dp,
      ),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leading != null) {
      leading()
    } else if (icon != null) {
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
    trailing?.invoke()
  }
}
