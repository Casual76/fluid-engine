package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.FluidToneTile

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
 * @param leadingTile [leading] nella piastrella del [tone]; falso: com'e' (una piastrella dell'app, un
 *   colore suo), come in `FluidListRow`. Dalla 3.0.1.
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
  leadingTile: Boolean = true,
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
    leadingTile = leadingTile,
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
 * @param leading un'icona davanti al titolo, nella piastrella del [tone] (32 dp, [FluidToneTileSize]): in
 *   una riga di una riga (56 dp) e di due (72 dp) sta al centro con il margine dei titoli, come la
 *   piastrella da 29 pt di una riga da 44 di iOS.
 * @param leadingTile falso: [leading] sta com'e', senza la piastrella del tono — per una piastrella col
 *   colore dell'app (`FluidToneTile(color = …)`), un'anteprima, un'icona di un formato. Dalla 3.0.1: prima
 *   una riga di scelta metteva sempre la piastrella, e l'icona dell'app finiva in una piastrella dentro la
 *   piastrella.
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
  leadingTile: Boolean = true,
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
    leadingTile = leadingTile,
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

/** Un'alternativa di [FluidPickerRow]: la chiave che l'app conosce, e come si legge. */
@Immutable
data class FluidPickerOption<K>(
  val key: K,
  val label: String,
  /** Una riga sotto l'alternativa, nel pop-up; vuota: niente. */
  val subtitle: String = "",
)

/**
 * Una riga che sceglie fra alternative in un pop-up (3.0.1): il titolo a sinistra, la scelta di adesso a
 * destra, e al tocco **la riga diventa il pop-up** con le alternative — righe di scelta, la spunta su quella
 * di adesso. Una scelta chiude il pop-up e chiama [onSelected] (solo se cambia); un tocco fuori lo chiude e
 * basta.
 *
 * E' il selettore delle impostazioni di iOS (la riga con il valore e le due frecce), e KeyVoice, Pampa sounds
 * e universal_converter se l'erano scritta ognuno con un `FluidGlassModalPortal` e una lista di
 * `FluidChoiceRow`: qui e' un pezzo solo, con l'origine, la trasformazione dalla riga e la semantica giuste.
 *
 * Per TalkBack e' **un** nodo, un elenco a discesa con il titolo come nome e la scelta come stato; dentro il
 * pop-up le alternative sono un gruppo di radio («2 di 4»). Un colpo solo all'apertura (`Open`, e' un
 * tasto che apre qualcosa di suo), un `Tick` se la scelta si sposta.
 *
 * Serve un `FluidGlassModalHost` alla radice, come per ogni portale: senza, la riga c'e' e il tocco non apre
 * niente. Con molte alternative il pop-up scorre (e' alto al piu' l'85% dello schermo).
 *
 * @param paneTitle il titolo del pop-up per TalkBack; di serie [title].
 */
@Composable
fun <K> FluidPickerRow(
  title: String,
  selectedKey: K,
  options: List<FluidPickerOption<K>>,
  onSelected: (K) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String = "",
  enabled: Boolean = true,
  tone: FluidTone = FluidTone.Neutral,
  leading: (@Composable () -> Unit)? = null,
  leadingTile: Boolean = true,
  paneTitle: String = title,
) {
  var open by remember { mutableStateOf(false) }
  var anchor by remember { mutableStateOf<Rect?>(null) }
  val current = options.firstOrNull { it.key == selectedKey }?.label.orEmpty()
  FluidListRow(
    title = title,
    subtitle = subtitle,
    modifier = modifier
      .alpha(if (enabled) 1f else FluidSettingRowDisabledAlpha)
      // La riga e' l'origine del pop-up, e mentre e' aperto non si disegna: e' lei che e' diventata il pannello.
      .fluidExpandOrigin(onMeasured = { anchor = it })
      .fluidRowPressable(
        onClick = { open = true },
        enabled = enabled,
        haptic = FluidHapticEvent.Open,
      )
      .clearAndSetSemantics {
        fluidRowLabel(title, subtitle)
        role = Role.DropdownList
        stateDescription = current
      },
    tone = tone,
    leading = leading,
    leadingTile = leadingTile,
    badge = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = current,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(
          imageVector = Icons.Rounded.UnfoldMore,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        )
      }
    },
  )
  FluidGlassModalPortal(
    visible = open,
    onDismissRequest = { open = false },
    origin = { anchor },
    paneTitle = paneTitle,
  ) {
    Column(modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
      options.forEachIndexed { index, option ->
        if (index > 0) FluidListDivider()
        FluidChoiceRow(
          title = option.label,
          subtitle = option.subtitle,
          selected = option.key == selectedKey,
          onSelect = {
            open = false
            if (option.key != selectedKey) onSelected(option.key)
          },
        )
      }
    }
  }
}

/**
 * Una riga con uno slider (3.0.1): il titolo e il valore in parole sopra, il [FluidSlider] sotto, nella
 * misura di una riga di un gruppo — la luminosita', il volume, la soglia, il tempo di un timer.
 *
 * Il valore in parole ([valueLabel], «30 minuti», «-12 dB») e' quello che TalkBack legge come stato dello
 * slider: senza, direbbe una percentuale della pista. Il titolo e' il nome dello slider. Le cifre sono
 * tabulari, cosi' il valore non balla mentre il dito si muove.
 *
 * Il gesto e' dello slider (un trascinamento di lato e' suo, uno in verticale e' della pagina), e la riga non
 * ha un tocco suo: e' una riga di un gruppo, non un bersaglio.
 *
 * @param snapValues vedi [FluidSlider].
 */
@Composable
fun FluidSliderRow(
  title: String,
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueLabel: String? = null,
  subtitle: String = "",
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  snapValues: List<Float> = emptyList(),
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,
  tone: FluidTone = FluidTone.Neutral,
  leading: (@Composable () -> Unit)? = null,
  leadingTile: Boolean = true,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Il testo e' detto dallo slider (nome e stato): qui sopra e' solo da leggere. Sfuma lui, non lo slider,
    // che da spento sfuma da se' (insieme arriverebbero al quarto dell'opacita').
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .alpha(if (enabled) 1f else FluidSettingRowDisabledAlpha)
        .clearAndSetSemantics { },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (leading != null) {
        if (leadingTile) FluidToneTile(tone = tone, content = leading) else leading()
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle.isNotBlank()) {
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (valueLabel != null) {
        Text(
          text = valueLabel,
          style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
    }
    FluidSlider(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth(),
      valueRange = valueRange,
      enabled = enabled,
      onValueChangeFinished = onValueChangeFinished,
      snapValues = snapValues,
      contentDescription = title,
      stateDescription = valueLabel,
    )
  }
}
