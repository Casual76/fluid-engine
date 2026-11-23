package dev.antigravity.fluidengine.viewer.data

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAvatar
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidIdentityColor
import dev.antigravity.fluidengine.ui.fluid.icons.CalendarMonth
import dev.antigravity.fluidengine.ui.fluid.icons.CheckBox
import dev.antigravity.fluidengine.ui.fluid.icons.CheckBoxOutlineBlank
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Group
import dev.antigravity.fluidengine.ui.fluid.icons.LocationOn
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.PersonAdd
import dev.antigravity.fluidengine.ui.fluid.icons.Replay
import dev.antigravity.fluidengine.ui.fluid.icons.Schedule
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope
import dev.antigravity.fluidengine.ui.fluid.viewer.viewerTypeActions
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Un calendario aperto, con «Apri con…» dell'app per «Aggiungi al calendario». */
internal class DataCalendarModel(val name: String, val calendar: DataCalendar, val openWith: (() -> Unit)?)

/** Dei contatti aperti, con «Apri con…» dell'app per «Aggiungi ai contatti». */
internal class DataContactsModel(val name: String, val contacts: List<DataContact>, val openWith: (() -> Unit)?)

private val Italian = Locale.ITALIAN

/** L'ora di un evento per le persone: «10:00 – 11:30», «Tutto il giorno», «dal 3 al 5 novembre». */
internal object DataWhen {
  private val Time = DateTimeFormatter.ofPattern("HH:mm", Italian)
  private val Day = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Italian)
  private val ShortDay = DateTimeFormatter.ofPattern("d MMMM", Italian)

  fun day(e: DataCalendarEvent, device: ZoneId): String? {
    val start = e.start ?: return null
    val date = if (start.allDay) start.date else start.zoned(device).withZoneSameInstant(device).toLocalDate()
    return date.format(Day).replaceFirstChar { it.uppercase(Italian) }
  }

  fun time(e: DataCalendarEvent, device: ZoneId, allDay: String): String? {
    val start = e.start ?: return null
    val end = e.end
    if (start.allDay) {
      // La fine di una giornata intera è esclusa: un evento del 3 novembre finisce il 4.
      val last = end?.date?.minusDays(1)
      return if (last == null || !last.isAfter(start.date)) allDay else "$allDay · dal ${start.date.format(ShortDay)} al ${last.format(ShortDay)}"
    }
    val s = start.zoned(device).withZoneSameInstant(device)
    val f = end?.zoned(device)?.withZoneSameInstant(device)
    return when {
      f == null -> s.format(Time)
      f.toLocalDate() != s.toLocalDate() -> "${s.format(Time)} – ${f.format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Italian))}"
      else -> "${s.format(Time)} – ${f.format(Time)}"
    }
  }

  /** Se l'evento ha un fuso diverso da quello del dispositivo: l'ora là, come la scrive il file. */
  fun zone(e: DataCalendarEvent, device: ZoneId): String? {
    val start = e.start ?: return null
    if (start.allDay) return null
    val zone = start.zone ?: return if (start.zoneName == null) "ora locale, senza fuso" else null
    val there: ZonedDateTime = start.zoned(device)
    val here = there.withZoneSameInstant(device)
    if (there.offset == here.offset) return null
    val name = start.zoneName ?: zone.id
    if (name == "Z" || name == "UTC" || name == "GMT") return "${there.format(Time)} UTC"
    val label = name.substringAfterLast('/').replace('_', ' ')
    return "${there.format(Time)} ora di $label"
  }

  /**
   * F6C.20 · «Copia gli eventi»: ogni evento in testo semplice, come lo dice la sua scheda (il titolo; il giorno e l'ora
   * nel fuso del dispositivo, e l'ora del file se è un altro fuso; la ripetizione; il luogo), con una riga vuota fra
   * l'uno e l'altro. Pura.
   */
  fun plainText(events: List<DataCalendarEvent>, device: ZoneId, allDay: String): String = events.joinToString("\n\n") { e ->
    listOfNotNull(
      e.summary ?: "(senza titolo)",
      listOfNotNull(day(e, device), time(e, device, allDay)).joinToString(" · ").ifEmpty { null },
      zone(e, device),
      e.repeat,
      e.location,
    ).joinToString("\n")
  }
}

/**
 * F6C.20 · «Copia i contatti»: ogni contatto in testo semplice, come la sua scheda (il nome; il ruolo e l'azienda; i
 * telefoni, le e-mail, gli indirizzi e i siti con le etichette di Contatti; compleanno e note), con una riga vuota fra
 * l'uno e l'altro. Pura.
 */
internal fun dataContactsPlainText(contacts: List<DataContact>): String = contacts.joinToString("\n\n") { c ->
  buildList {
    add(c.name)
    listOfNotNull(c.title, c.organization).joinToString(" · ").takeIf { it.isNotEmpty() }?.let(::add)
    (c.phones + c.emails + c.addresses + c.urls).forEach { add("${it.label}: ${it.value}") }
    c.birthday?.let { add("compleanno: $it") }
    c.note?.let { add("note: $it") }
  }.joinToString("\n")
}

/** La tessera della data, come Calendario: il mese in rosso piccolo sopra, il giorno grande sotto. */
@Composable
private fun DateTile(e: DataCalendarEvent, device: ZoneId) {
  val start = e.start
  val date = when {
    start == null -> null
    start.allDay -> start.date
    else -> start.zoned(device).withZoneSameInstant(device).toLocalDate()
  }
  val scheme = MaterialTheme.colorScheme
  Column(
    Modifier.size(56.dp).background(scheme.surfaceContainerHigh, ContinuousCornerShape(FluidRadius.Control)).padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
  ) {
    if (date == null) {
      FluidIcon(FluidIcons.CalendarMonth, contentDescription = null)
    } else {
      Text(date.month.getDisplayName(TextStyle.SHORT, Italian).uppercase(Italian), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = dataTypeColors().error)
      Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
    }
  }
}

@Composable
private fun EventLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color = Color.Unspecified) {
  Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FluidIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = if (color == Color.Unspecified) LocalContentColor.current else color)
  }
}

/**
 * F6C.13 · Un calendario come schede (§5.6): per ogni evento la tessera della data, il titolo, l'ora nel fuso del
 * dispositivo (e l'ora del fuso del file se è un altro), la ripetizione in italiano, il luogo, gli avvisi, le note e chi
 * partecipa. Il fondo ha la tinta leggera di D. In basso «Aggiungi al calendario», che passa il file all'app di
 * calendario con «Apri con…» dell'app (è un intento dell'app, §1.3).
 */
@Composable
internal fun DataCalendarPage(scope: FluidViewerCustomScope, model: DataCalendarModel, settings: FluidDataSettings) {
  val strings = settings.strings
  val calendar = model.calendar
  val device = remember { ZoneId.systemDefault() }
  val clipboard = LocalClipboardManager.current
  DataInner(
    scope,
    actions = {
      model.openWith?.let { text("data.ics.add", strings.addToCalendar, it, placement = FluidToolbarPlacement.BottomBar) }
      // L'azione del tipo (F6C.20): gli eventi in testo negli appunti. «Aggiungi al calendario» resta in barra.
      if (calendar.events.isNotEmpty()) viewerTypeActions(strings.more) {
        listOf(FluidContextAction(strings.copyEvents) {
          clipboard.setText(AnnotatedString(DataWhen.plainText(calendar.events, device, strings.allDay)))
        })
      }
    },
  ) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
      Modifier.fillMaxSize().dataTint(scheme.primary),
      contentPadding = PaddingValues(
        start = FluidScreenDefaults.HorizontalPadding, end = FluidScreenDefaults.HorizontalPadding,
        top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding(),
      ),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      calendar.name?.let { item(key = "name") { DataSectionTitle(it) } }
      if (calendar.events.isEmpty()) item(key = "empty") {
        Text("Nessun evento", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, color = scheme.onSurfaceVariant)
      }
      itemsIndexed(calendar.events, key = { i, _ -> i }) { _, e ->
        val day = DataWhen.day(e, device)
        val time = DataWhen.time(e, device, strings.allDay)
        Row(Modifier.fillMaxWidth().dataCard().padding(16.dp).semantics(mergeDescendants = true) {},
          horizontalArrangement = Arrangement.spacedBy(14.dp)) {
          DateTile(e, device)
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              if (e.todo) FluidIcon(if (e.status == "Completata" || e.percent == 100) FluidIcons.CheckBox else FluidIcons.CheckBoxOutlineBlank,
                contentDescription = if (e.todo) "Attività" else null, modifier = Modifier.size(18.dp))
              Text(e.summary ?: "(senza titolo)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() })
            }
            listOfNotNull(day, time).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { EventLine(FluidIcons.Schedule, it) }
            DataWhen.zone(e, device)?.let { EventLine(FluidIcons.Schedule, it, scheme.onSurfaceVariant) }
            e.repeat?.let { EventLine(FluidIcons.Replay, it + if (e.exceptions > 0) " (${e.exceptions} eccezioni)" else "") }
            e.location?.let { EventLine(FluidIcons.LocationOn, it) }
            e.alarms.firstOrNull()?.let { EventLine(FluidIcons.Notifications, e.alarms.joinToString(" · ")) }
            val people = listOfNotNull(e.organizer?.let { "Organizza $it" }) + if (e.attendees.isNotEmpty()) listOf("${e.attendees.size} invitati") else emptyList()
            if (people.isNotEmpty()) EventLine(FluidIcons.Group, people.joinToString(" · "))
            e.status?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant) }
            e.description?.let {
              Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 8)
            }
          }
        }
      }
    }
  }
}

/**
 * F6C.13 · Dei contatti come schede (§5.6): la foto (o le iniziali col colore d'identità del nome), il nome, l'azienda
 * e il ruolo; poi telefoni, e-mail, indirizzi, siti, compleanno e note, con le etichette di Contatti. Una pressione
 * lunga copia il valore. La tinta leggera viene dal primo contatto. In basso «Aggiungi ai contatti», con «Apri con…»
 * dell'app.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DataContactsPage(scope: FluidViewerCustomScope, model: DataContactsModel, settings: FluidDataSettings) {
  val strings = settings.strings
  val clipboard = LocalClipboardManager.current
  DataInner(
    scope,
    actions = {
      model.openWith?.let { text("data.vcf.add", strings.addToContacts, it, placement = FluidToolbarPlacement.BottomBar) }
      // L'azione del tipo (F6C.20): i contatti in testo negli appunti. «Aggiungi ai contatti» resta in barra.
      viewerTypeActions(strings.more) {
        listOf(FluidContextAction(strings.copyContacts) { clipboard.setText(AnnotatedString(dataContactsPlainText(model.contacts))) })
      }
    },
  ) {
    val scheme = MaterialTheme.colorScheme
    val tint = fluidIdentityColor(model.contacts.first().name)
    LazyColumn(
      Modifier.fillMaxSize().dataTint(tint),
      contentPadding = PaddingValues(
        start = FluidScreenDefaults.HorizontalPadding, end = FluidScreenDefaults.HorizontalPadding,
        top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding(),
      ),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      itemsIndexed(model.contacts, key = { i, _ -> i }) { _, c ->
        val photo by produceState<ImageBitmap?>(null, c) {
          value = c.photo?.let { bytes -> withContext(Dispatchers.Default) { FluidDataFamily.decodePicture(bytes, 512) } }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val picture = photo
          FluidAvatar(
            c.name, size = 96.dp,
            image = if (picture != null) ({ Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }) else null,
            contentDescription = if (picture != null) "Foto di ${c.name}" else null,
          )
          Text(c.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
          listOfNotNull(c.title, c.organization).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
          }
          c.nickname?.let { Text("«$it»", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
          val fields = buildList {
            c.phones.forEach { add(it.label to it.value) }
            c.emails.forEach { add(it.label to it.value) }
            c.addresses.forEach { add(it.label to it.value) }
            c.urls.forEach { add(it.label to it.value) }
            c.birthday?.let { add("compleanno" to it) }
            c.anniversary?.let { add("anniversario" to it) }
            c.others.forEach { add(it.label to it.value) }
            if (c.categories.isNotEmpty()) add("gruppi" to c.categories.joinToString(", "))
            c.note?.let { add("note" to it) }
            if (c.photoRemote) add("foto" to "un indirizzo web: non scaricata")
          }
          if (fields.isNotEmpty()) Column(Modifier.fillMaxWidth().dataCard().clip(ContinuousCornerShape(FluidRadius.Group))) {
            fields.forEachIndexed { i, (label, value) ->
              DataField(label, value, Modifier.combinedClickable(onLongClickLabel = strings.copied, onLongClick = {
                clipboard.setText(AnnotatedString(value))
              }, onClick = {}))
              if (i < fields.lastIndex) FluidListDivider()
            }
          }
        }
      }
    }
  }
}
