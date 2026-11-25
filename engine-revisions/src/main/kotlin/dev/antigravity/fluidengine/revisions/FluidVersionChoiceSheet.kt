package dev.antigravity.fluidengine.revisions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidTone
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le parole del foglio, nella lingua dell'app: di serie italiane, [english] per l'inglese. I titoli seguono il foglio di
 * iOS per i documenti di iCloud in conflitto («Risolvi conflitto», fiducia media: la guida di Pages, letta il 24/11/2026);
 * i tasti sono quelli del brief di F8.21 («tieni questa», «tieni tutte e due»).
 */
@Immutable
data class FluidVersionLabels(
  val title: String = "Risolvi conflitto",
  val message: (String) -> String = { "«$it» è cambiata su più dispositivi senza che si vedessero. Scegli quale versione tenere." },
  val keepOne: String = "Tieni questa versione",
  val keepAll: (Int) -> String = { n -> if (n == 2) "Tieni tutte e due" else "Tieni tutte e $n" },
  val keepSome: (Int) -> String = { n -> "Tieni queste $n" },
  val chooseFirst: String = "Scegli una versione",
  val later: String = "Decidi dopo",
  val current: String = "Attuale",
  val updated: String = "Guardale di nuovo prima di scegliere.",
  val excerptsOnly: String = "Le versioni sono troppo diverse per segnare i passaggi: ecco l'inizio di ciascuna.",
  val selected: String = "Scelta",
  val notSelected: String = "Non scelta",
  val byAuthor: (String) -> String = { "di $it" },
  val today: (String) -> String = { "Oggi, $it" },
  val yesterday: (String) -> String = { "Ieri, $it" },
  val onDay: (String, String) -> String = { day, time -> "$day, $time" },
  /** Il titolo della riga che dice che e' arrivata una versione nuova a foglio aperto. */
  val updatedTitle: String = "È arrivata una versione nuova",
) {
  companion object {
    fun english() = FluidVersionLabels(
      title = "Resolve Conflict",
      message = { "“$it” changed on more than one device. Choose which version to keep." },
      keepOne = "Keep This Version",
      keepAll = { n -> if (n == 2) "Keep Both" else "Keep All $n" },
      keepSome = { n -> "Keep These $n" },
      chooseFirst = "Choose a Version",
      later = "Decide Later",
      current = "Current",
      updated = "Look again before choosing.",
      excerptsOnly = "The versions are too different to mark the changes: here is how each one starts.",
      selected = "Selected",
      notSelected = "Not selected",
      byAuthor = { "by $it" },
      today = { "Today, $it" },
      yesterday = { "Yesterday, $it" },
      onDay = { day, time -> "$day, $time" },
      updatedTitle = "A newer version arrived",
    )
  }
}

/** I tag dei test. */
object FluidVersionChoiceTags {
  const val Pane = "versioni:foglio"
  const val Keep = "versioni:tieni"
  const val Later = "versioni:dopo"
  const val Updated = "versioni:nuova"

  fun card(id: String): String = "versioni:$id"
}

/**
 * **La scelta tra versioni** (F8.21, `scelta-tra-versioni`): quando due copie di un elemento sono cambiate e non si
 * possono fondere, un foglio di vetro le mette a confronto e chiede quale tenere. Il riferimento e' Apple: le versioni in
 * conflitto di `NSFileVersion` e il foglio «Risolvi conflitto» dei documenti di iCloud, con una riga per versione (il
 * dispositivo, l'ora, l'anteprima) da spuntare e il tasto «Mantieni».
 *
 * Ogni versione e' una card con:
 * - il dispositivo (o l'autore, in uno spazio condiviso) e l'ora («Oggi, 14:32»);
 * - l'anteprima tagliata attorno al primo **passaggio diverso**, con i passaggi diversi segnati; se le versioni sono
 *   troppo diverse per il tetto di lavoro, due estratti senza segni ([fluidVersionExcerpts]);
 * - il segno della scelta ([FluidSelectionMark]): si tocca la card.
 *
 * In fondo il tasto dice cosa fara': «Tieni questa versione», «Tieni tutte e due», spento finche' non si sceglie; e
 * «Decidi dopo» chiude senza scegliere. Il foglio non si chiude da solo: lo chiude l'app quando ha salvato la scelta
 * ([onDecision]), mettendo [choice] a null.
 *
 * **Una revisione nuova a foglio aperto.** Se [choice] cambia revisioni (stessa [FluidVersionChoice.key]), la card
 * cambiata entra sfocata (la cambia l'app, non il dito), la scelta si azzera e una riga dice che e' arrivata una versione
 * nuova (TalkBack la annuncia). La scelta restituita porta sempre le revisioni che il foglio mostrava.
 *
 * Nasce da [source] (il tasto o la riga che segnala il conflitto) e ci torna; sul telefono e' un foglio dal basso, sul
 * tablet un popover. Con «Riduci movimento» il segno e le card cambiano senza passaggi. A riposo non ridisegna niente.
 */
@Composable
fun FluidVersionChoiceSheet(
  choice: FluidVersionChoice?,
  onDecision: (FluidVersionDecision) -> Unit,
  onDismissRequest: () -> Unit,
  source: FluidTransitionSource? = null,
  labels: FluidVersionLabels = FluidVersionLabels(),
  zone: ZoneId = ZoneId.systemDefault(),
  locale: Locale = Locale.getDefault(),
) {
  FluidGlassModalPortal(
    item = choice,
    onDismissRequest = onDismissRequest,
    presentation = FluidGlassModalPresentation.Popover,
    paneTitle = labels.title,
    source = source,
    compactAdaptation = FluidCompactAdaptation.Sheet,
  ) { shown ->
    FluidVersionChoicePane(shown, onDecision, onDismissRequest, labels, zone, locale)
  }
}

/** Il contenuto del foglio, per chi lo mette in una superficie sua. */
@Composable
fun FluidVersionChoicePane(
  choice: FluidVersionChoice,
  onDecision: (FluidVersionDecision) -> Unit,
  onDismissRequest: () -> Unit,
  labels: FluidVersionLabels = FluidVersionLabels(),
  zone: ZoneId = ZoneId.systemDefault(),
  locale: Locale = Locale.getDefault(),
  modifier: Modifier = Modifier,
) {
  val ids = choice.ids
  val openedWith = remember(choice.key) { ids }
  val updated = ids != openedWith
  var selected by remember(choice.key, ids) { mutableStateOf(emptySet<String>()) }
  // Il confronto: subito per i testi corti (pochi microsecondi), fuori dal thread principale per quelli lunghi.
  val short = remember(choice.versions) { choice.versions.all { it.text.length <= ShortText } }
  val immediate = remember(choice.versions) { if (short) fluidVersionExcerpts(choice.versions) else null }
  val background by produceState(initialValue = immediate, choice.versions) {
    if (!short) value = withContext(Dispatchers.Default) { fluidVersionExcerpts(choice.versions) }
  }
  val excerpts = immediate ?: background
  // Gli estratti di ogni versione vista in questo confronto: lo strato che esce durante un cambio tiene i suoi, non
  // prende il testo della versione che entra.
  val seenExcerpts = remember(choice.key) { HashMap<String, FluidVersionExcerpt>() }
  excerpts?.let(seenExcerpts::putAll)
  val today = remember(zone) { LocalDate.now(zone) }

  // Il foglio scorre quando le versioni non ci stanno (tre versioni, il testo grande): i tasti in fondo restano raggiungibili.
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .navigationBarsPadding()
      .padding(horizontal = 20.dp)
      .padding(bottom = 20.dp)
      .testTag(FluidVersionChoiceTags.Pane),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = labels.title,
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      style = MaterialTheme.typography.titleLarge,
      textAlign = TextAlign.Center,
    )
    Text(
      text = labels.message(choice.title),
      modifier = Modifier.fillMaxWidth(),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (updated) {
      FluidInlineMessage(
        message = labels.updated,
        title = labels.updatedTitle,
        tone = FluidTone.Info,
        modifier = Modifier
          .testTag(FluidVersionChoiceTags.Updated)
          .semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
    if (excerpts != null && excerpts.values.any { !it.compared }) {
      Text(
        text = labels.excerptsOnly,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    choice.versions.forEachIndexed { index, version ->
      // Una card per posto: se la versione di un posto cambia, il suo contenuto entra sfocato.
      key(index) {
        VersionCard(
          version = version,
          excerptOf = { id -> excerpts?.get(id) ?: seenExcerpts[id] },
          selected = version.id in selected,
          labels = labels,
          time = fluidVersionTime(version.timeMillis, zone, locale, today, labels),
          onToggle = { selected = if (version.id in selected) selected - version.id else selected + version.id },
        )
      }
    }
    val count = selected.size
    FluidButton(
      text = when {
        count == 0 -> labels.chooseFirst
        count == 1 -> labels.keepOne
        count == ids.size -> labels.keepAll(count)
        else -> labels.keepSome(count)
      },
      onClick = { onDecision(FluidVersionDecision(keep = ids.filter { it in selected }, shown = ids)) },
      enabled = count > 0,
      // Grande come i tasti principali dei fogli di Apple (50 dp): sopra i 44 dp del bersaglio minimo.
      size = FluidButtonSize.Large,
      fillWidth = true,
      modifier = Modifier.padding(top = 4.dp).testTag(FluidVersionChoiceTags.Keep),
    )
    FluidButton(
      text = labels.later,
      onClick = onDismissRequest,
      style = FluidButtonStyle.Plain,
      size = FluidButtonSize.Large,
      fillWidth = true,
      modifier = Modifier.testTag(FluidVersionChoiceTags.Later),
    )
  }
}

@Composable
private fun VersionCard(
  version: FluidVersion,
  excerptOf: (String) -> FluidVersionExcerpt?,
  selected: Boolean,
  labels: FluidVersionLabels,
  time: String,
  onToggle: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val shape = ContinuousCornerShape(FluidRadius.Card)
  val cardFill = scheme.surfaceContainerHigh.copy(alpha = 0.92f)
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 44.dp)
      .testTag(FluidVersionChoiceTags.card(version.id))
      .fluidPressable(
        onClick = onToggle,
        role = Role.Checkbox,
        haptic = FluidHapticEvent.Tap,
        pressedScale = FluidPressFeedback.DefaultYieldScale,
      )
      .semantics(mergeDescendants = true) {
        role = Role.Checkbox
        toggleableState = ToggleableState(selected)
        stateDescription = if (selected) labels.selected else labels.notSelected
      }
      .clip(shape)
      // Piena quanto basta per leggere sopra il vetro del foglio (come i gruppi di un foglio di iOS); la scelta ha la tinta
      // dell'accento sopra.
      .background(if (selected) scheme.primary.copy(alpha = 0.16f).compositeOver(cardFill) else cardFill)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    FluidSelectionMark(selected = selected, contentDescription = null, modifier = Modifier.padding(top = 2.dp))
    FluidGlassContentTransition(
      targetState = version,
      key = { it.id },
      blurReference = 22.dp,
      contentAlignment = Alignment.TopStart,
      modifier = Modifier.weight(1f),
    ) { shown ->
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = shown.source,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (shown.current) {
            Text(
              text = labels.current,
              style = MaterialTheme.typography.labelSmall,
              color = scheme.primary,
              modifier = Modifier
                .clip(ContinuousCornerShape(8.dp))
                .background(scheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
        Text(
          text = shown.author?.let { "$time · ${labels.byAuthor(it)}" } ?: time,
          style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurfaceVariant,
        )
        Text(
          text = excerptText(shown, excerptOf(shown.id), scheme.primary),
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 6,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
  }
}

/** L'anteprima con i passaggi diversi segnati: il fondo dell'accento e il tratto un po' piu' pieno. */
private fun excerptText(version: FluidVersion, excerpt: FluidVersionExcerpt?, accent: Color): AnnotatedString {
  if (excerpt == null) return AnnotatedString(version.text.take(PlainPreview))
  val mark = SpanStyle(background = accent.copy(alpha = 0.20f), fontWeight = FontWeight.Medium)
  return buildAnnotatedString {
    if (excerpt.clippedStart) append("… ")
    val offset = length
    append(excerpt.text)
    for (range in excerpt.changed) addStyle(mark, offset + range.first, offset + range.last + 1)
    if (excerpt.clippedEnd) append(" …")
  }
}

/** Fino a quanti caratteri il confronto si fa subito, nella composizione. */
private const val ShortText = 2_000

/** L'inizio del testo mostrato mentre il confronto di un testo lungo gira fuori dal thread principale. */
private const val PlainPreview = 280
