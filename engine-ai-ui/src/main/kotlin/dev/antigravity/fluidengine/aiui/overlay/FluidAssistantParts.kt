package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidLiveDot
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Stop

// I pezzi piccoli dell'overlay, pubblici: le quattro app li avevano uguali e, in due, li avevano
// privati dentro il file della card. Un'app che compone il proprio pannello (come il dock di
// Fluid-transit, o la sessione sopra le altre app di PampAI) li riusa invece di rifarli.

/**
 * Il pallino che respira accanto alla riga di stato: l'assistente sta lavorando. E' il respiro
 * dell'engine (`FluidLiveDot`, un respiro solo per tutta l'app, fermo con le animazioni ridotte) e
 * non un `infiniteRepeatable` suo: la card ne aveva uno a 700 ms, e due cose vive sulla stessa
 * schermata che non battono insieme sembrano rotte.
 */
@Composable
fun FluidBreathingDot(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  size: Dp = 8.dp,
  pulsing: Boolean = true,
) {
  FluidLiveDot(modifier = modifier, color = color, size = size, pulsing = pulsing)
}

/**
 * Il tasto per interrompere: il materiale dei controlli dell'engine, un bersaglio da 40 dp.
 *
 * Da solo sulla pagina **gira** chiaro/scuro con lo sfondo, come i tasti di vetro dell'engine (un contenitore con un
 * pezzo solo, F3.2b); sulla card è vetro annidato e tiene il lato della card ([LocalFluidAssistantPanel]). Il glifo è
 * l'inchiostro del lato. Da solo, il `modifier` sta attorno al contenitore.
 */
@Composable
fun FluidStopPill(
  backdrop: GlassBackdropState,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
  contentDescription: String = FluidAssistantStrings().stop,
) {
  FluidAssistantSmallGlass(modifier) { outer ->
    Box(
      modifier = outer
        .size(40.dp)
        .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
        // Niente tap: quando la richiesta si ferma arriva lo Stop dell'overlay, e due vibrazioni
        // per un tocco solo si sentono come un difetto.
        .fluidPressable(onClick = onStop, role = Role.Button, haptic = null),
      contentAlignment = Alignment.Center,
    ) {
      FluidIcon(
        imageVector = FluidIcons.Filled.Stop,
        contentDescription = contentDescription,
        // Letto qui, dentro il contenitore: lo schema di colori è quello del lato del vetro.
        tint = fluidAssistantInk(FluidAssistantInk.Default, MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

/**
 * Un chip sotto la risposta, in vetro. [maxLines] 2 per i nomi lunghi (le fermate di Fluid-transit:
 * "STAZIONE SANTA MARIA NOVELLA" si prendeva tutta la riga e gli altri chip restavano larghi quaranta dp).
 *
 * Sulla card e sul dock tiene il lato del pannello ([LocalFluidAssistantPanel]); da solo sulla pagina gira come i tasti
 * dell'engine, con l'etichetta nell'inchiostro del lato (F3.2b). Da solo, il `modifier` sta attorno al contenitore.
 */
@Composable
fun FluidChipPill(
  label: String,
  backdrop: GlassBackdropState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  maxLines: Int = 1,
) {
  FluidAssistantSmallGlass(modifier) { outer ->
    Box(
      modifier = outer
        .heightIn(min = 40.dp)
        .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
        .fluidPressable(onClick = onClick, role = Role.Button)
        .padding(horizontal = 14.dp, vertical = if (maxLines > 1) 8.dp else 0.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = fluidAssistantInk(FluidAssistantInk.Default, MaterialTheme.colorScheme.onSurface),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * La card ridotta: un puntino e una riga, perche' l'utente sta guardando altro.
 *
 * **Il vetro** (4.0, F3.2e): una capsula `Floating` del piano flottante, con la pellicola flottante di prima. Da sola
 * sulla pagina sta in un `FluidGlassContainer` con un pezzo solo e **gira** chiaro/scuro con lo sfondo come la capsula
 * flottante, con la riga nell'inchiostro del suo lato; il punto resta nell'accento (o nel colore d'errore) su tutti e
 * due i lati. Sul lato del tema è il vetro di prima. Il `modifier` sta attorno al contenitore, che passa alla capsula i
 * vincoli minimi.
 */
@Composable
fun FluidCollapsedPill(
  text: String,
  backdrop: GlassBackdropState,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
  error: Boolean = false,
) {
  // La pellicola si legge qui, fuori dal contenitore: è quella del lato del tema, come prima. Il lato lo fa il contenitore.
  val tint = GlassDefaults.floatingTint()
  FluidAssistantPanelGlass(modifier) { outer ->
    Row(
      outer
        .fluidGlassPanel(
          backdrop = backdrop,
          tint = tint,
          shape = FluidCapsuleShape,
          role = FluidAssistantSurface.CollapsedPill.role,
          large = FluidAssistantSurface.CollapsedPill.large,
        )
        .fluidPressable(onClick = onExpand, pressedScale = 1f, role = Role.Button)
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidAssistantPanelContent {
        Box(
          Modifier
            .size(8.dp)
            .background(if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, FluidCapsuleShape),
        )
        Spacer(Modifier.width(8.dp))
        // Letto qui, dentro il contenitore: lo schema di colori è quello del lato del vetro.
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/**
 * Il tasto per fermare l'ascolto: una capsula di vetro sotto l'aureola, alta abbastanza da essere un bersaglio vero (48 dp).
 *
 * Sta sulla pagina (sotto l'aureola, sopra quello che l'app mostra), quindi **gira** chiaro/scuro con lo sfondo come i
 * tasti dell'engine, con la scritta nell'inchiostro del lato (F3.2b). Il pallino resta nel colore d'errore su tutti e
 * due i lati. Il `modifier` è quello della riga che la centra, com'era.
 */
@Composable
fun FluidListeningHint(
  backdrop: GlassBackdropState,
  elapsedMillis: Long,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
) {
  Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
    FluidAssistantSmallGlass(Modifier) { outer ->
      Row(
        outer
          .heightIn(min = 48.dp)
          .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
          .fluidPressable(onClick = onStop, pressedScale = 1f, role = Role.Button)
          .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Letto qui, dentro il contenitore: lo schema di colori è quello del lato del vetro.
        val ink = fluidAssistantInk(FluidAssistantInk.Default, MaterialTheme.colorScheme.onSurface)
        Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, FluidCapsuleShape))
        Spacer(Modifier.width(10.dp))
        Text(strings.tapToFinish, style = MaterialTheme.typography.labelLarge, color = ink)
        if (elapsedMillis >= 1_000) {
          Spacer(Modifier.width(8.dp))
          Text(
            "${elapsedMillis / 1000}s",
            style = MaterialTheme.typography.labelMedium,
            color = ink.copy(alpha = 0.6f),
          )
        }
      }
    }
  }
}

/**
 * La conferma di un'azione che scrive: cosa sta per succedere (titolo e, se c'e', dettaglio) e i
 * due tasti. [fillButtons] li fa dividere la larghezza (nel dock di Fluid-transit, dove la conferma
 * e' una riga a se'); di serie sono piccoli e a sinistra come nella card.
 */
@Composable
fun FluidConfirmationRow(
  pending: PendingConfirmation,
  onConfirm: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
  fillButtons: Boolean = false,
) {
  Column(modifier) {
    Text(pending.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    pending.detail?.let {
      Spacer(Modifier.height(2.dp))
      Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = if (fillButtons) Modifier.fillMaxWidth() else Modifier) {
      FluidButton(
        text = strings.confirm,
        onClick = { onConfirm(true) },
        style = FluidButtonStyle.Tinted,
        size = FluidButtonSize.Small,
        modifier = if (fillButtons) Modifier.weight(1f) else Modifier,
        fillWidth = fillButtons,
      )
      FluidButton(
        text = strings.cancel,
        onClick = { onConfirm(false) },
        style = FluidButtonStyle.Plain,
        size = FluidButtonSize.Small,
        modifier = if (fillButtons) Modifier.weight(1f) else Modifier,
        fillWidth = fillButtons,
      )
    }
  }
}
