package dev.antigravity.fluidengine.sample.listsforms

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRowValue
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidStepper
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeAction
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeActionsContainer
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSwipeStyle
import dev.antigravity.fluidengine.ui.fluid.asContextAction
import dev.antigravity.fluidengine.ui.fluid.fluidGroupedItems
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeActions
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MarkEmailRead
import dev.antigravity.fluidengine.ui.fluid.icons.MarkEmailUnread
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.Person
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Settings
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.icons.Sync
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSwipeState
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone

/**
 * La pagina «Liste, campi e stepper» (piano «iOS in Android», F6.6): dove si guardano, e si riprendono dal tablet, le
 * azioni a scorrimento con l'aspetto di iOS 27, lo stepper di vetro, i campi a capsula e i separatori sotto il testo.
 *
 * - **Azioni a scorrimento**: sei messaggi in `fluidGroupedItems`; verso sinistra «Elimina» (toglie la riga), verso
 *   destra «Letto». La pressione lunga apre il menu con le stesse azioni in testa (`asContextAction`). Il segmentato
 *   in cima passa dall'aspetto di iOS 27 a quello di prima, per il confronto: `--es listsforms.swipe fill` apre col
 *   secondo. «Rimetti» riporta i sei messaggi.
 * - **Stepper**: in tre righe di un gruppo, sul canvas della pagina (vetro), col valore accanto; uno ai capi, uno spento.
 * - **Campi**: una riga, con l'icona, segreto, errore, su piu' righe.
 * - **Separatori**: un gruppo con le icone (il filo parte sotto il testo) e uno misto (vince il rientro piu' piccolo).
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page ListsForms
 *
 * Ferma, la pagina non chiede fotogrammi: niente si muove finche' non la si tocca.
 */
@Composable
internal fun ListsFormsPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val startsFill = remember(context) {
    context.findActivity()?.intent?.getStringExtra(ExtraSwipe)?.equals("fill", ignoreCase = true) == true
  }
  var style by remember { mutableStateOf(if (startsFill) FluidSwipeStyle.Fill else FluidSwipeStyle.Capsule) }
  CompositionLocalProvider(LocalFluidSwipeStyle provides style) {
    FluidSwipeActionsContainer {
      ListsFormsContent(style = style, onStyle = { style = it }, bottomInset = bottomInset, onBack = onBack)
    }
  }
}

@Composable
private fun ListsFormsContent(
  style: FluidSwipeStyle,
  onStyle: (FluidSwipeStyle) -> Unit,
  bottomInset: Dp,
  onBack: () -> Unit,
) {
  val messages = remember { mutableStateListOf<Int>().apply { addAll(DemoMessages.indices) } }
  val read = remember { mutableStateMapOf<Int, Boolean>() }
  var copies by remember { mutableIntStateOf(2) }
  var people by remember { mutableIntStateOf(4) }
  var minutes by remember { mutableIntStateOf(10) }
  var lastAction by remember { mutableStateOf("Nessuna azione ancora") }
  var plain by remember { mutableStateOf("") }
  var search by remember { mutableStateOf("") }
  var secret by remember { mutableStateOf("") }
  var code by remember { mutableStateOf("12") }
  var notes by remember { mutableStateOf("") }
  val scheme = MaterialTheme.colorScheme

  FluidScreen(
    title = "Liste, campi e stepper",
    subtitle = "F6.6 · azioni a scorrimento di iOS 27, stepper di vetro, campi a capsula, separatori sotto il testo",
    onBack = onBack,
    extraBottomPadding = bottomInset,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards) },
  ) {
    item(key = "swipe-header") {
      FluidSectionHeader(
        title = "Azioni a scorrimento",
        detail = "Tira una riga di lato e rilasciala per mostrare l’azione. Scorri fino in fondo per eseguirla.",
      )
    }
    item(key = "swipe-style") {
      FluidSegmentedControl(
        options = listOf(FluidSwipeStyle.Capsule, FluidSwipeStyle.Fill),
        selected = style,
        onSelect = onStyle,
        modifier = Modifier.fillMaxWidth().testTag("listsforms-swipe-style"),
        label = { if (it == FluidSwipeStyle.Capsule) "iOS 27" else "Com'era" },
      )
    }
    fluidGroupedItems(
      count = messages.size,
      key = { index -> "msg-${messages[index]}" },
    ) { index ->
      val id = messages[index]
      val message = DemoMessages[id]
      val isRead = read[id] == true
      val swipe = rememberFluidSwipeState()
      val toggleRead = FluidSwipeAction(
        label = if (isRead) "Da leggere" else "Letto",
        icon = if (isRead) FluidIcons.MarkEmailUnread else FluidIcons.MarkEmailRead,
        color = scheme.primary,
      ) {
        read[id] = !isRead
        lastAction = "${message.first}: ${if (isRead) "da leggere" else "letto"}"
      }
      val delete = FluidSwipeAction(
        label = "Elimina",
        icon = FluidIcons.Delete,
        color = DemoRed,
        removesItem = true,
      ) {
        messages.remove(id)
        lastAction = "${message.first}: eliminato"
      }
      FluidListRow(
        title = message.first,
        subtitle = message.second,
        tone = if (isRead) FluidTone.Neutral else FluidTone.Primary,
        leading = { FluidIcon(if (isRead) FluidIcons.MarkEmailRead else FluidIcons.MarkEmailUnread, null) },
        onClick = { lastAction = "${message.first}: aperto" },
        // Le stesse azioni in testa al menu della pressione lunga, nello stesso ordine (R1 L3).
        contextActions = {
          listOf(
            toggleRead.asContextAction(),
            FluidSwipeAction("Archivia", FluidIcons.Archive, scheme.tertiary) { lastAction = "${message.first}: archiviato" }
              .asContextAction(),
            delete.asContextAction(destructive = true),
          )
        },
        modifier = Modifier
          .fluidSwipeActions(
            state = swipe,
            startAction = toggleRead,
            endAction = delete,
            shape = segmentShape,
            fill = rowFill,
          )
          .testTag("listsforms-row-$id"),
      )
    }
    item(key = "swipe-footnote") {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FluidSectionFootnote(text = "Ultima azione: $lastAction")
        if (messages.size < DemoMessages.size) {
          FluidButton(
            text = "Rimetti i messaggi",
            onClick = {
              messages.clear()
              messages.addAll(DemoMessages.indices)
            },
            style = FluidButtonStyle.Tinted,
            modifier = Modifier.testTag("listsforms-restore"),
          )
        }
      }
    }

    item(key = "stepper-header") { FluidSectionHeader(title = "Stepper", detail = "Tocca meno o piu'; tienilo premuto.") }
    item(key = "stepper-group") {
      FluidListGroup {
        FluidListRow(
          title = "Copie",
          subtitle = "Da 1 a 10",
          leading = { FluidIcon(FluidIcons.Star, null) },
          disclosure = false,
          badge = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              FluidRowValue("$copies")
              FluidStepper(
                value = copies,
                onValueChange = { copies = it },
                range = 1..10,
                label = "Copie",
                modifier = Modifier.testTag("listsforms-stepper-copies"),
              )
            }
          },
        )
        FluidListDivider()
        FluidListRow(
          title = "Persone",
          subtitle = "Senza capi, a passi di uno",
          leading = { FluidIcon(FluidIcons.Person, null) },
          disclosure = false,
          badge = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              FluidRowValue("$people")
              FluidStepper(value = people, onValueChange = { people = it }, label = "Persone")
            }
          },
        )
        FluidListDivider()
        FluidListRow(
          title = "Promemoria",
          subtitle = "Minuti prima, a passi di cinque, spento",
          leading = { FluidIcon(FluidIcons.Notifications, null) },
          disclosure = false,
          badge = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              FluidRowValue("$minutes min")
              FluidStepper(value = minutes, onValueChange = { minutes = it }, step = 5, range = 0..60, enabled = false)
            }
          },
        )
      }
    }

    item(key = "fields-header") { FluidSectionHeader(title = "Campi", detail = "Capsula da 40 dp, come il campo di iOS 27.") }
    item(key = "fields") {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidTextField(value = plain, onValueChange = { plain = it }, placeholder = "Nome", modifier = Modifier.testTag("listsforms-field-plain"))
        FluidTextField(
          value = search,
          onValueChange = { search = it },
          placeholder = "Cerca nei messaggi",
          leading = { FluidIcon(FluidIcons.Search, null) },
        )
        FluidTextField(value = secret, onValueChange = { secret = it }, placeholder = "Password", secret = true)
        FluidTextField(
          value = code,
          onValueChange = { code = it },
          label = "Codice",
          isError = code.length != 4,
          supportingText = if (code.length != 4) "Servono quattro cifre" else null,
        )
        FluidTextField(
          value = notes,
          onValueChange = { notes = it },
          placeholder = "Note, su piu' righe",
          singleLine = false,
          minLines = 3,
        )
      }
    }

    item(key = "dividers-header") {
      FluidSectionHeader(title = "Separatori", detail = "Partono dove parte il testo, come in Apple.")
    }
    item(key = "dividers-icons") {
      FluidListGroup {
        FluidListRow(title = "Notifiche", subtitle = "", leading = { FluidIcon(FluidIcons.Notifications, null) }, tone = FluidTone.Danger, onClick = {})
        FluidListDivider()
        FluidListRow(title = "Sincronizza", subtitle = "", leading = { FluidIcon(FluidIcons.Sync, null) }, tone = FluidTone.Success, onClick = {})
        FluidListDivider()
        FluidListRow(title = "Impostazioni", subtitle = "", leading = { FluidIcon(FluidIcons.Settings, null) }, onClick = {})
      }
    }
    item(key = "dividers-mixed") {
      FluidListGroup(modifier = Modifier.padding(top = 4.dp)) {
        FluidListRow(title = "Con l'icona", subtitle = "", leading = { FluidIcon(FluidIcons.Star, null) }, onClick = {})
        FluidListDivider()
        FluidListRow(title = "Senza icona", subtitle = "In un gruppo misto vince il rientro piu' piccolo", onClick = {})
      }
    }
  }
}

/** L'extra di lancio che apre con l'aspetto di prima delle azioni a scorrimento. */
private const val ExtraSwipe = "listsforms.swipe"

/** Il rosso dell'azione che elimina: quello di sistema di Apple (255, 59, 48), misurato pieno sulla capsula. */
private val DemoRed = Color(0xFFFF3B30)

private val DemoMessages = listOf(
  "Marta" to "Domani alle 9 in sala riunioni",
  "Banca" to "Il tuo estratto conto di ottobre",
  "Luca" to "Foto della gita, finalmente",
  "Palestra" to "La lezione di giovedi' e' spostata",
  "Biblioteca" to "Il prestito scade fra tre giorni",
  "Nonna" to "Domenica pranzo da noi?",
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
