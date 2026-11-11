package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowUpward
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.Refresh

/**
 * La barra di scrittura in alto (pressione lunga sul tasto): una capsula di vetro con il campo, il
 * microfono per passare alla voce, l'invio, e la freccia circolare per ricominciare la
 * conversazione. Il focus arriva da solo: la tastiera si apre senza un secondo tocco.
 *
 * Quella di ClasseViva e di FluidWeather (stessa barra). Il segnaposto e' [strings] `askPlaceholder`
 * ("Chiedi al registro…" era dominio di CV, qui e' una parola dell'app). [leading] sostituisce il
 * tasto "nuova conversazione" per chi ne vuole un altro (o nessuno: passa `{}`).
 *
 * @param autoFocus vero quando l'ha chiesta l'utente; falso a risposta finita, perche' riaprire con
 *   la tastiera copre la risposta che si e' appena aspettata.
 */
@Composable
fun FluidAssistantTextBar(
  backdrop: GlassBackdropState,
  busy: Boolean,
  micAvailable: Boolean,
  onSend: (String) -> Unit,
  onVoice: () -> Unit,
  onNewConversation: () -> Unit,
  modifier: Modifier = Modifier,
  autoFocus: Boolean = true,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
  leading: (@Composable RowScope.() -> Unit)? = null,
) {
  var text by rememberSaveable { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
  fun submit() {
    val query = text.trim()
    if (query.isEmpty() || busy) return
    onSend(query)
    text = ""
  }
  Row(
    modifier = modifier
      .fillMaxWidth()
      .glassSurface(state = backdrop, tint = GlassDefaults.modalTint(), shape = FluidCapsuleShape, role = GlassRole.Modal)
      .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (leading != null) {
      leading()
    } else {
      FluidBarIcon(icon = FluidIcons.Refresh, description = strings.newConversation, onClick = onNewConversation)
    }
    FluidTextField(
      value = text,
      onValueChange = { text = it },
      placeholder = strings.askPlaceholder,
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { submit() }),
      modifier = Modifier
        .weight(1f)
        .focusRequester(focus),
    )
    Spacer(Modifier.width(4.dp))
    if (text.isBlank()) {
      if (micAvailable) FluidBarIcon(icon = FluidIcons.Mic, description = strings.voice, onClick = onVoice)
    } else {
      FluidBarIcon(icon = FluidIcons.ArrowUpward, description = strings.send, onClick = { submit() }, primary = true)
    }
  }
}

/** Un'icona della barra: bersaglio da 40 dp (il tocco ne ha comunque 48), grigia o nell'accento. */
@Composable
fun FluidBarIcon(
  icon: ImageVector,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  primary: Boolean = false,
) {
  Box(
    modifier = modifier
      .size(40.dp)
      .fluidPressable(onClick = onClick, role = Role.Button),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = description,
      tint = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
      modifier = Modifier.size(22.dp),
    )
  }
}
