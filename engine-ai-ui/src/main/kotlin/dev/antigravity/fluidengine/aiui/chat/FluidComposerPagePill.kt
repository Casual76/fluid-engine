package dev.antigravity.fluidengine.aiui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.Description
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons

/**
 * Le parole del chip «Questa pagina» (F9.1), con i default italiani e [english].
 *
 * @param description per TalkBack, col titolo della pagina in `%1$s`.
 */
@Immutable
data class FluidPageContextStrings(
  val label: String = "Questa pagina",
  val addLabel: String = "Aggiungi questa pagina",
  val included: String = "Passa a PampAI con la domanda",
  val excluded: String = "Non passa",
  val description: String = "Questa pagina: %1\$s",
  val show: String = "Mostra cosa passa",
  val remove: String = "Togli la pagina",
  val add: String = "Aggiungi la pagina",
) {
  companion object {
    fun english(): FluidPageContextStrings = FluidPageContextStrings(
      label = "This page",
      addLabel = "Add this page",
      included = "Goes to PampAI with the question",
      excluded = "Not sent",
      description = "This page: %1\$s",
      show = "Show what is sent",
      remove = "Remove the page",
      add = "Add the page",
    )
  }
}

/**
 * Il chip «Questa pagina» (F9.1, `assistente-vede-la-pagina`): sta fra le pillole del composer e dice se la pagina che
 * l'utente ha davanti parte con la domanda. Acceso e' la pillola dell'accento con la X: un tocco sul nome mostra cosa
 * passa ([onShowContents], di solito un foglio col testo di `AiAssistPage.preview()`), un tocco sulla X la toglie.
 * Spento e' «+ Aggiungi questa pagina», e un tocco la rimette. Senza Apple equivalente diretto: e' il gemello del chip
 * della pagina che Siri e ChatGPT mostrano prima di mandare lo schermo (fiducia media).
 *
 * Alto come le altre pillole del composer (30 dp in [FluidComposer], 40 nel composer del campo), col bersaglio da 48
 * dp; la X ha il suo bersaglio largo 40. Non si anima: compare e sparisce con la pagina, quindi «Riduci movimento» non
 * ha niente da fermare.
 *
 * TalkBack legge un nodo solo: «Questa pagina: <titolo>», lo stato («Passa a PampAI con la domanda» o «Non passa»),
 * il tocco (mostra o aggiunge) e le azioni «Mostra cosa passa» e «Togli la pagina» (o «Aggiungi la pagina»).
 *
 * @param title il titolo della pagina, per TalkBack (la pillola mostra solo «Questa pagina»).
 */
@Composable
fun FluidComposerPagePill(
  title: String,
  included: Boolean,
  onIncludedChange: (Boolean) -> Unit,
  onShowContents: () -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidPageContextStrings = FluidPageContextStrings(),
) {
  val height = LocalComposerPillHeight.current
  val large = height > ComposerPillHeightBefore
  val glyph = if (large) 18.dp else 14.dp
  val bg = if (included) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
  val fg = if (included) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
  val description = strings.description.replace("%1\$s", title)
  Row(
    modifier = modifier
      .composerTarget(height)
      .drawBehind { drawComposerPill(bg, height) }
      .clearAndSetSemantics {
        role = Role.Button
        contentDescription = description
        stateDescription = if (included) strings.included else strings.excluded
        if (included) {
          onClick(label = strings.show) { onShowContents(); true }
          customActions = listOf(
            CustomAccessibilityAction(strings.show) { onShowContents(); true },
            CustomAccessibilityAction(strings.remove) { onIncludedChange(false); true },
          )
        } else {
          onClick(label = strings.add) { onIncludedChange(true); true }
          customActions = listOf(CustomAccessibilityAction(strings.add) { onIncludedChange(true); true })
        }
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier
        .fluidPressable(onClick = { if (included) onShowContents() else onIncludedChange(true) }, role = Role.Button, haptic = null)
        .composerTargetInner(height)
        .padding(start = if (large) 12.dp else 10.dp, end = if (included) 2.dp else if (large) 12.dp else 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(if (included) FluidIcons.Description else FluidIcons.Add, contentDescription = null, tint = fg, modifier = Modifier.size(glyph))
      Spacer(Modifier.width(6.dp))
      Text(
        if (included) strings.label else strings.addLabel,
        style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
        color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
      )
    }
    if (included) {
      Box(
        modifier = Modifier
          .fluidPressable(onClick = { onIncludedChange(false) }, role = Role.Button, haptic = null)
          .composerTargetInner(height)
          .width(40.dp)
          .fillMaxHeight(),
        contentAlignment = Alignment.Center,
      ) {
        FluidIcon(FluidIcons.Close, contentDescription = null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(glyph))
      }
    }
  }
}
