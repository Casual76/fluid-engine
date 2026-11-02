package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone

/**
 * Una riga di impostazioni con un interruttore: **la riga e' l'interruttore**.
 *
 * Piu' di un'app aveva scritto la propria (card cliccabile, riga con `toggleable`, riga con un
 * `FluidSwitch` che aveva pure il suo callback), e sbagliavano in modo diverso la stessa cosa: chi
 * dava il callback all'interruttore *e* alla riga si ritrovava con due bersagli che rispondevano a
 * un tocco solo, chi non lo dava a nessuno con un bersaglio alto quanto la pista, 28 dp.
 * Qui il bersaglio e' tutta la riga (almeno 48 dp, in pratica 56 o 72 con il sottotitolo), il
 * `FluidSwitch` disegna e anima soltanto (`onCheckedChange = null`: vedi la sua nota) e per TalkBack
 * c'e' **un** elemento solo, un interruttore con il titolo e il sottotitolo come nome.
 *
 * **Una vibrazione, quella dell'interruttore.** `ToggleOn` o `ToggleOff` secondo dove va, e niente
 * `Tap` sopra: la riga lo suonerebbe da sola (e l'aptica fonde due eventi a meno di 70 ms, ma
 * fonderli sceglie il primo, cioe' quello sbagliato). Per questo la pressione e' di
 * `fluidRowPressable(haptic = null)` e non del `onClick` della riga.
 *
 * **Cede, non cresce.** E' una riga di un gruppo: la pressione e' la tinta di `fluidRowPressable`, mai
 * la scala (vedi `FluidListRow`). Il pomello non si trascina, qui: il tocco vale ovunque sulla riga, e
 * trascinare un pomello e' un gesto da controllo isolato (`FluidSwitch` con il suo callback).
 *
 * Disattivata non diventa grigia: sfuma, come ogni controllo dell'engine, e non risponde (la
 * semantica dice «disattivato», non solo il colore).
 *
 * **Un nodo solo, con un nome vero.** `FluidListRow` contiene un `ListItem` di Material, che fonde la
 * propria semantica: Compose non fonde un figlio che a sua volta fonde, e sotto la riga restava un
 * secondo nodo con il testo, mentre quello con il ruolo e lo stato non aveva nome. Per questo la riga
 * **azzera** la semantica di quello che contiene (`clearAndSetSemantics`) e si da' il nome da se':
 * il titolo, e «titolo, sottotitolo» come descrizione. Conseguenza: quello che sta dentro la riga, a
 * parte titolo e sottotitolo, non viene letto — l'informazione che serve a chi non vede sta nel
 * sottotitolo.
 *
 * @param tone il tono della piastrella di [leading]; il tono sta sull'icona, mai sulla riga.
 */
@Composable
fun FluidToggleRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String = "",
  enabled: Boolean = true,
  tone: FluidTone = FluidTone.Neutral,
  leading: (@Composable () -> Unit)? = null,
) {
  val haptics = rememberFluidHaptics()
  FluidListRow(
    title = title,
    subtitle = subtitle,
    modifier = modifier
      // Fuori da tutto, cosi' sfuma anche la tinta della pressione e il fuoco da tastiera.
      .alpha(if (enabled) 1f else FluidSettingRowDisabledAlpha)
      .fluidRowPressable(
        onClick = {
          val next = !checked
          haptics.play(fluidSwitchHaptic(next))
          onCheckedChange(next)
        },
        enabled = enabled,
        haptic = null,
      )
      .clearAndSetSemantics {
        fluidRowLabel(title, subtitle)
        role = Role.Switch
        toggleableState = ToggleableState(checked)
      },
    tone = tone,
    leading = leading,
    // `enabled` resta vero: a sfumare e' la riga, e un interruttore che sfumasse anche da solo
    // arriverebbe al quarto dell'opacita'. Non risponde comunque: senza callback non ha gesto.
    badge = { FluidSwitch(checked = checked, onCheckedChange = null) },
  )
}

/**
 * Una riga di scelta singola, con il segno di spunta a destra come nelle impostazioni di iOS: **non**
 * un radio.
 *
 * Il radio dice «una fra queste» disegnando un cerchio vuoto per ogni alternativa, e in un gruppo di
 * tre righe sono tre cerchi che chiedono attenzione per dire una cosa sola. La spunta ne disegna
 * uno, sulla riga scelta, e il resto della lista resta testo. Per TalkBack e' comunque un radio
 * (`Role.RadioButton`, `selected`): il segno e' una questione di occhi, non di semantica.
 *
 * La spunta sta in uno spazio **sempre riservato**. Comparendo solo sulla riga scelta spostava il
 * testo di quella riga di venti dp a ogni cambio, e un titolo lungo si spezzava in due solo mentre
 * era scelto.
 *
 * **Una vibrazione, e solo se cambia qualcosa:** un `Tick` quando la scelta si sposta su questa
 * riga. Toccare la riga gia' scelta non vibra (non e' cambiato niente) ma chiama comunque
 * [onSelect], perche' il chiamante e' spesso un foglio che si chiude alla scelta, e deve chiudersi
 * anche quando la scelta e' quella di prima.
 *
 * Un nodo solo per riga, con il titolo come nome e «titolo, sottotitolo» come descrizione: la semantica
 * di quello che sta dentro e' azzerata, per la ragione spiegata in [FluidToggleRow].
 *
 * Per il gruppo, `FluidListGroup(modifier = Modifier.selectableGroup())` fa dire a TalkBack «1 di 3».
 *
 * @param onSelect chiamata a ogni tocco, anche sulla riga gia' scelta.
 */
@Composable
fun FluidChoiceRow(
  title: String,
  selected: Boolean,
  onSelect: () -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String = "",
  enabled: Boolean = true,
  tone: FluidTone = FluidTone.Neutral,
  leading: (@Composable () -> Unit)? = null,
) {
  val haptics = rememberFluidHaptics()
  FluidListRow(
    title = title,
    subtitle = subtitle,
    modifier = modifier
      .alpha(if (enabled) 1f else FluidSettingRowDisabledAlpha)
      .fluidRowPressable(
        onClick = {
          if (fluidChoicePlaysHaptic(selected)) haptics.play(FluidHapticEvent.Tick)
          onSelect()
        },
        enabled = enabled,
        haptic = null,
      )
      .clearAndSetSemantics {
        fluidRowLabel(title, subtitle)
        role = Role.RadioButton
        this.selected = selected
      },
    tone = tone,
    leading = leading,
    badge = { FluidChoiceMark(selected = selected) },
  )
}

/**
 * La spunta della riga scelta: nell'accento, in uno spazio fisso, e dissolve invece di comparire.
 *
 * Il passaggio da una riga all'altra e' una dissolvenza incrociata di due segni, non uno che salta:
 * quello vecchio esce piano e quello nuovo entra un poco piu' svelto, cosi' per un attimo se ne vedono
 * due e la scelta si legge come uno spostamento. Le animazioni ridotte le onora il token.
 */
@Composable
private fun FluidChoiceMark(selected: Boolean) {
  val visibility by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = if (selected) FluidMotion.fadeIn(140) else FluidMotion.fadeOut(120),
    label = "fluid choice mark",
  )
  Box(
    modifier = Modifier
      .size(FluidChoiceMarkSize)
      .graphicsLayer { alpha = visibility },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = Icons.Rounded.Check,
      contentDescription = null,
      modifier = Modifier.size(FluidChoiceMarkGlyph),
      tint = MaterialTheme.colorScheme.primary,
    )
  }
}

/**
 * Il nome di una riga per chi legge lo schermo: il titolo come testo, e «titolo, sottotitolo» come
 * descrizione quando il sottotitolo c'e'. Il testo e' il titolo da solo perche' e' quello che un test o
 * uno strumento cerca; la descrizione e' quello che TalkBack legge.
 */
private fun SemanticsPropertyReceiver.fluidRowLabel(title: String, subtitle: String) {
  text = AnnotatedString(title)
  if (subtitle.isNotBlank()) contentDescription = "$title, $subtitle"
}

/**
 * Se un tocco su una riga di scelta deve vibrare: solo se la riga **non** era gia' quella scelta.
 *
 * Pura e a parte perche' e' la regola che il test puo' dire senza comporre niente, e quella che
 * un'app riscrive male per prima ("vibra sempre, e' piu' semplice").
 */
internal fun fluidChoicePlaysHaptic(alreadySelected: Boolean): Boolean = !alreadySelected

/** Quanto sfuma una riga disattivata: come l'interruttore, che e' il controllo che ne ha deciso la misura. */
internal const val FluidSettingRowDisabledAlpha = 0.5f

/** Lo spazio riservato alla spunta, e il segno dentro: il bersaglio di un'icona piccola, non di piu'. */
private val FluidChoiceMarkSize = 24.dp
private val FluidChoiceMarkGlyph = 22.dp
