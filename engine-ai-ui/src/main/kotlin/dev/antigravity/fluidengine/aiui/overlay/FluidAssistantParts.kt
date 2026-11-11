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
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.glassSurface
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

/** Il tasto per interrompere: il materiale dei controlli dell'engine, un bersaglio da 40 dp. */
@Composable
fun FluidStopPill(
  backdrop: GlassBackdropState,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
  contentDescription: String = FluidAssistantStrings().stop,
) {
  Box(
    modifier = modifier
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
      tint = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.size(16.dp),
    )
  }
}

/**
 * Un chip sotto la risposta, in vetro. [maxLines] 2 per i nomi lunghi (le fermate di Fluid-transit:
 * "STAZIONE SANTA MARIA NOVELLA" si prendeva tutta la riga e gli altri chip restavano larghi quaranta dp).
 */
@Composable
fun FluidChipPill(
  label: String,
  backdrop: GlassBackdropState,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  maxLines: Int = 1,
) {
  Box(
    modifier = modifier
      .heightIn(min = 40.dp)
      .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
      .fluidPressable(onClick = onClick, role = Role.Button)
      .padding(horizontal = 14.dp, vertical = if (maxLines > 1) 8.dp else 0.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** La card ridotta: un puntino e una riga, perche' l'utente sta guardando altro. */
@Composable
fun FluidCollapsedPill(
  text: String,
  backdrop: GlassBackdropState,
  onExpand: () -> Unit,
  modifier: Modifier = Modifier,
  error: Boolean = false,
) {
  Row(
    modifier
      .glassSurface(state = backdrop, tint = GlassDefaults.floatingTint(), shape = FluidCapsuleShape, role = GlassRole.Floating)
      .fluidPressable(onClick = onExpand, pressedScale = 1f, role = Role.Button)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      Modifier
        .size(8.dp)
        .background(if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, FluidCapsuleShape),
    )
    Spacer(Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** Il tasto per fermare l'ascolto: una capsula di vetro sotto l'aureola, alta abbastanza da essere un bersaglio vero (48 dp). */
@Composable
fun FluidListeningHint(
  backdrop: GlassBackdropState,
  elapsedMillis: Long,
  onStop: () -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
) {
  Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
    Row(
      Modifier
        .heightIn(min = 48.dp)
        .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
        .fluidPressable(onClick = onStop, pressedScale = 1f, role = Role.Button)
        .padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.error, FluidCapsuleShape))
      Spacer(Modifier.width(10.dp))
      Text(strings.tapToFinish, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
      if (elapsedMillis >= 1_000) {
        Spacer(Modifier.width(8.dp))
        Text(
          "${elapsedMillis / 1000}s",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
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
