package dev.antigravity.fluidengine.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.account.PampaPeople
import dev.antigravity.fluidengine.account.PeopleRowEntry
import dev.antigravity.fluidengine.account.PeopleRowKind
import dev.antigravity.fluidengine.account.SharedItem
import dev.antigravity.fluidengine.ui.fluid.FluidAvatar
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Forum
import dev.antigravity.fluidengine.ui.fluid.icons.PhoneAndroid
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.fluid.icons.VisibilityOff
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.util.concurrent.TimeUnit

/**
 * Le parole dei componenti delle persone (`progetto.md` § 3.7). Di serie in italiano; sono di base, le parole e la
 * grafica finali arrivano con F10.11.
 */
data class PeopleLabels(
  val devices: String = "I tuoi dispositivi",
  val more: String = "Altri…",
  val sharedWithYou: String = "Condivisi con te",
  val from: String = "Da %s",
  val today: String = "oggi",
  val yesterday: String = "ieri",
  val daysAgo: String = "%d giorni fa",
  val reply: String = "Rispondi",
  val openConversation: String = "Apri la conversazione",
  val removeFromShared: String = "Togli dai condivisi",
  val muteSender: String = "Non mostrare più da %s in questa app",
  val replyTo: String = "Rispondi a %s",
  val replyPlaceholder: String = "Scrivi una risposta",
  val send: String = "Invia",
  val close: String = "Chiudi",
  /** Le parole cambiate dopo un rinnovo (rapporto di F10.5, § 9; brief di F10.6): come il numero di sicurezza di Signal. */
  val wordsChangedTheirs: String = "Le parole con %1\$s sono cambiate perché %1\$s ha rinnovato le chiavi. Confrontatele di nuovo per tenere il segno «verificato».",
  val wordsChangedMine: String = "Le parole con %1\$s sono cambiate perché hai rinnovato le tue chiavi. Confrontatele di nuovo per tenere il segno «verificato».",
  val compareAgain: String = "Confronta di nuovo",
  val later: String = "Più tardi",
)

/** Cosa mostra la fila: le voci, nell'ordine, e se c'è «Altri…». Una funzione pura: le prove la controllano. */
data class PeopleRowModel(val entries: List<PeopleRowEntry>, val hasMore: Boolean)

/**
 * La fila per [entries]: «I tuoi dispositivi» per primi (scelta Q1), poi fino a [max] persone e gruppi nell'ordine dello
 * Store (i più recenti), e «Altri…» se ce ne sono di più. Senza persone e senza dispositivi la fila non c'è (P1).
 */
fun peopleRowModel(entries: List<PeopleRowEntry>, max: Int = 8): PeopleRowModel {
  val devices = entries.filter { it.kind == PeopleRowKind.DEVICES }.take(1)
  val people = entries.filter { it.kind != PeopleRowKind.DEVICES }
  return PeopleRowModel(devices + people.take(max), people.size > max)
}

/** «Da Alessio · ieri» per una cosa ricevuta all'ora [at], se adesso è [now]. */
fun sharedSubtitle(item: SharedItem, now: Long, labels: PeopleLabels = PeopleLabels()): String {
  val days = TimeUnit.MILLISECONDS.toDays((now - item.receivedAt).coerceAtLeast(0))
  val whenText = when (days) {
    0L -> labels.today
    1L -> labels.yesterday
    else -> labels.daysAgo.format(days)
  }
  return labels.from.format(item.from.name) + " · " + whenText
}

/** Le quattro voci del tocco su «Da X» (`progetto.md` § 3.7), nell'ordine. */
enum class AttributionAction { REPLY, OPEN_CONVERSATION, REMOVE, MUTE_SENDER }

fun attributionLabels(name: String, labels: PeopleLabels = PeopleLabels()): Map<AttributionAction, String> = mapOf(
  AttributionAction.REPLY to labels.reply,
  AttributionAction.OPEN_CONVERSATION to labels.openConversation,
  AttributionAction.REMOVE to labels.removeFromShared,
  AttributionAction.MUTE_SENDER to labels.muteSender.format(name),
)

/** L'avviso delle parole cambiate: [byThem] se ha rinnovato l'altro, altrimenti ho rinnovato io. */
fun wordsChangedText(name: String, byThem: Boolean, labels: PeopleLabels = PeopleLabels()): String =
  (if (byThem) labels.wordsChangedTheirs else labels.wordsChangedMine).format(name)

/**
 * La fila delle persone per il foglio di condivisione (`FluidShareSheet(people = …)`): «I tuoi dispositivi», poi le
 * persone e i gruppi recenti, coi loro avatar, e «Altri…». Gli id sono quelli dello Store, validi solo per quest'app: chi
 * li riceve li ripassa a `PampaPeople.send`. Senza voci non disegna niente (P1).
 *
 * Scorre col dito ed è nitida (regola F8: ciò che muove il dito resta nitido).
 */
@Composable
fun FluidPeopleRow(
  entries: List<PeopleRowEntry>,
  onPick: (PeopleRowEntry) -> Unit,
  modifier: Modifier = Modifier,
  onMore: (() -> Unit)? = null,
  labels: PeopleLabels = PeopleLabels(),
) {
  val model = peopleRowModel(entries)
  if (model.entries.isEmpty()) return
  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    for (e in model.entries) {
      Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (e.kind == PeopleRowKind.DEVICES) {
          FluidAvatar(
            name = labels.devices,
            key = "pampa-devices",
            image = { Icon(FluidIcons.PhoneAndroid, contentDescription = null) },
            onClick = { onPick(e) },
            contentDescription = labels.devices,
          )
        } else {
          FluidAvatar(name = e.name, key = e.mineral ?: e.id, onClick = { onPick(e) }, contentDescription = e.name)
        }
        Spacer(Modifier.height(6.dp))
        Text(
          if (e.kind == PeopleRowKind.DEVICES) labels.devices else e.name,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    if (model.hasMore && onMore != null) {
      Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FluidAvatar(name = labels.more, key = "pampa-more", onClick = onMore, contentDescription = labels.more)
        Spacer(Modifier.height(6.dp))
        Text(labels.more, style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

/** La fila letta da Pampa Store (`PampaPeople.row`); vuota finché non risponde, e senza Store. */
@Composable
fun rememberPampaPeopleRow(): List<PeopleRowEntry> {
  val context = LocalContext.current
  val row by produceState(emptyList<PeopleRowEntry>()) { value = runCatching { PampaPeople.row(context) }.getOrDefault(emptyList()) }
  return row
}

/**
 * «Condivisi con te» (`progetto.md` § 3.7; scelta D8): la stessa sezione in ogni app, con l'avatar, «Da Alessio · ieri» e
 * il titolo. **Se è vuota non compare**: niente sezioni vuote che chiedono attenzione.
 */
@Composable
fun FluidSharedWithYou(
  items: List<SharedItem>,
  onOpen: (SharedItem) -> Unit,
  modifier: Modifier = Modifier,
  now: Long = System.currentTimeMillis(),
  onAttribution: ((SharedItem, AttributionAction) -> Unit)? = null,
  labels: PeopleLabels = PeopleLabels(),
) {
  if (items.isEmpty()) return
  Column(modifier.fillMaxWidth()) {
    FluidSectionHeader(labels.sharedWithYou)
    FluidListGroup(glass = true) {
      for (item in items) {
        FluidListRow(
          title = item.title.ifBlank { labels.from.format(item.from.name) },
          subtitle = sharedSubtitle(item, now, labels),
          leading = { FluidAvatar(name = item.from.name, key = item.from.mineral ?: item.from.id) },
          onClick = { onOpen(item) },
          contextActions = onAttribution?.let { act ->
            { attributionLabels(item.from.name, labels).map { (a, text) -> dev.antigravity.fluidengine.ui.fluid.FluidContextAction(text, destructive = a == AttributionAction.MUTE_SENDER) { act(item, a) } } }
          },
        )
      }
    }
  }
}

/**
 * «Da Alessio» (`FluidAttribution`, come `SWAttributionView` di Apple): resta sulla cosa anche dopo che l'hai aperta o
 * importata. Un tocco apre le quattro voci: Rispondi, Apri la conversazione, Togli dai condivisi, Non mostrare più da
 * Alessio in questa app.
 */
@Composable
fun FluidAttribution(
  from: PeopleRowEntry,
  onAction: (AttributionAction) -> Unit,
  modifier: Modifier = Modifier,
  labels: PeopleLabels = PeopleLabels(),
) {
  var open by rememberSaveable { mutableStateOf(false) }
  FluidChip(
    label = labels.from.format(from.name),
    selected = false,
    onClick = { open = true },
    modifier = modifier.semantics { contentDescription = labels.from.format(from.name) },
    leading = { FluidAvatar(name = from.name, key = from.mineral ?: from.id, size = 20.dp) },
  )
  FluidGlassSheet(visible = open, onDismissRequest = { open = false }, detents = setOf(FluidSheetDetent.Medium), paneTitle = labels.from.format(from.name)) {
    FluidListGroup(Modifier.padding(16.dp), glass = true) {
      for ((action, text) in attributionLabels(from.name, labels)) {
        FluidListRow(
          title = text,
          subtitle = "",
          leading = {
            Icon(
              when (action) {
                AttributionAction.REPLY -> FluidIcons.Send
                AttributionAction.OPEN_CONVERSATION -> FluidIcons.Forum
                AttributionAction.REMOVE -> FluidIcons.Close
                AttributionAction.MUTE_SENDER -> FluidIcons.VisibilityOff
              },
              contentDescription = null,
            )
          },
          onClick = {
            open = false
            onAction(action)
          },
        )
      }
    }
  }
}

/**
 * «Rispondi» (`FluidQuickReply`): un foglio piccolo con la cosa citata sopra. La risposta va nella conversazione in Pampa
 * Mail, o resta attaccata alla cosa (`PampaPeople.reply`).
 */
@Composable
fun FluidQuickReply(
  visible: Boolean,
  to: String,
  quoted: String,
  onSend: (String) -> Unit,
  onDismissRequest: () -> Unit,
  labels: PeopleLabels = PeopleLabels(),
) {
  var text by remember(visible) { mutableStateOf("") }
  FluidGlassSheet(visible = visible, onDismissRequest = onDismissRequest, detents = setOf(FluidSheetDetent.Medium), paneTitle = labels.replyTo.format(to)) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(labels.replyTo.format(to), style = MaterialTheme.typography.titleMedium)
      if (quoted.isNotBlank()) Text("«$quoted»", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
      FluidTextField(value = text, onValueChange = { text = it }, placeholder = labels.replyPlaceholder, singleLine = false, minLines = 2)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        FluidButton(text = labels.close, onClick = onDismissRequest, style = FluidButtonStyle.Plain)
        FluidButton(text = labels.send, onClick = { onSend(text.trim()) }, enabled = text.isNotBlank())
      }
    }
  }
}

/**
 * L'avviso delle quattro parole cambiate (come il numero di sicurezza di Signal): «Le parole con Marco sono cambiate
 * perché Marco ha rinnovato le chiavi». Non è un allarme: le chiavi nuove sono firmate dalla stessa radice. La verifica a
 * distanza è da rifare per tenere il segno «verificato» (`PeoplePolicy.RENEWAL_DROPS_WORDS_VERIFICATION`).
 */
@Composable
fun FluidWordsChangedNotice(
  name: String,
  byThem: Boolean,
  onCompare: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  labels: PeopleLabels = PeopleLabels(),
) {
  FluidListGroup(modifier.fillMaxWidth(), glass = true) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(wordsChangedText(name, byThem, labels), style = MaterialTheme.typography.bodyMedium)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
        FluidButton(text = labels.later, onClick = onDismiss, style = FluidButtonStyle.Plain)
        FluidButton(text = labels.compareAgain, onClick = onCompare, style = FluidButtonStyle.Tinted)
      }
    }
  }
}
