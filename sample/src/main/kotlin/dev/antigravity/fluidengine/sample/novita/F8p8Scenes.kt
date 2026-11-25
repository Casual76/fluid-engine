package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.foundation.FluidRecurrenceText
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCalendar
import dev.antigravity.fluidengine.ui.fluid.FluidCalendarLabels
import dev.antigravity.fluidengine.ui.fluid.FluidCalendarMark
import dev.antigravity.fluidengine.ui.fluid.FluidCalendarMarkStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCalendarMarks
import dev.antigravity.fluidengine.ui.fluid.FluidDateCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidDateSelection
import dev.antigravity.fluidengine.ui.fluid.FluidDurationCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidRecurrenceLabels
import dev.antigravity.fluidengine.ui.fluid.FluidRecurrenceSheet
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidCalendarState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.delay

// F8.8 · Date: il calendario in pagina, gli intervalli e i giorni sparsi con la capsula della data, e il pannello «Ripeti»
// (le schede `calendario-in-pagina`, `intervalli-e-giorni-sparsi`, `scelta-ricorrenza`), nei casi delle app di Alessio.
// Le date sono fisse sul 24 novembre 2026, cosi' le riprese e le prove vedono sempre lo stesso mese.

/** Il giorno di oggi delle scene: fisso, per riprese ripetibili. */
internal val F8p8Today: LocalDate = LocalDate.of(2026, 11, 24)

/** Le scene di F8.8, nell'ordine delle idee. */
internal val F8p8Scenes: List<NewElementScene> = listOf(
  NewElementScene(
    "calendario-agenda", "L'agenda di ClasseViva",
    "Il mese coi segni di compiti e verifiche, che arrivano un mese alla volta; la lista sotto stringe il mese nella settimana",
  ) { onBack -> AgendaScene(onBack) },
  NewElementScene(
    "calendario-giorni", "I giorni da giustificare",
    "ClasseViva · piu' giorni sparsi, anche in mesi diversi, come MultiDatePicker; su foto chiara e scura",
  ) { onBack -> ScatteredDaysScene(onBack) },
  NewElementScene(
    "calendario-intervallo", "I voti di un periodo",
    "ClasseViva, MyCloud Gallery · dal… al…, la fascia fra i due giorni; i giorni restano giorni anche con l'ora legale",
  ) { onBack -> RangeScene(onBack) },
  NewElementScene(
    "capsula-data", "La data in una riga",
    "Pampa Notes, Fluidify · la capsula «23 nov 2026» che diventa il calendario, l'intervallo e la durata a ruota",
  ) { onBack -> CapsuleScene(onBack) },
  NewElementScene(
    "ripeti", "Il pannello «Ripeti»",
    "ClasseViva, Pampa Notes · ogni giorno, i giorni in capsule, il mese, la fine; la frase e le prossime date",
  ) { onBack -> RepeatScene(onBack) },
)

/** Le voci di F8.8 in «Da provare». */
internal val F8p8TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-8-calendario", TryArea.ContentData, "Il calendario in pagina",
    "Apri «L'agenda di ClasseViva», cambia mese scorrendo di lato, tocca il titolo del mese e poi scorri la lista sotto: " +
      "giudica se il mese segue il dito nitido e si posa su un mese intero, se le ruote di mese e anno prendono il posto " +
      "della griglia, se i segni arrivano senza far saltare la griglia e se il mese si stringe nella settimana e torna.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › L'agenda di ClasseViva"),
  ),
  TryItem(
    "f8-8-intervallo", TryArea.ContentData, "Intervallo e giorni sparsi",
    "In «I voti di un periodo» tocca un giorno, poi un altro dopo, poi uno prima: giudica se la fascia si legge e se il " +
      "terzo tocco ricomincia; in «I giorni da giustificare» accendi giorni in due mesi.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › I voti di un periodo"),
  ),
  TryItem(
    "f8-8-capsula", TryArea.ContentData, "La capsula della data",
    "Apri «La data in una riga» e tocca «23 nov 2026»: giudica se il calendario nasce dalla capsula, se la capsula scrive " +
      "subito il giorno scelto, se toccando fuori il popover ci rientra, e se la durata a ruota scatta come il timer.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › La data in una riga"),
  ),
  TryItem(
    "f8-8-ripeti", TryArea.ContentData, "Il pannello «Ripeti»",
    "Apri «Il pannello «Ripeti»», tocca «Ripeti», scegli «Personalizza», accendi lun e gio e una fine: giudica se la frase " +
      "e le prossime date cambiano sfocate e se sono quelle giuste.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Il pannello «Ripeti»"),
  ),
)

/** I tag delle prove. */
internal object F8p8Tags {
  const val Calendar = "f8p8:calendario"
  const val Agenda = "f8p8:agenda"
  const val Readout = "f8p8:valore"
  const val Ground = "f8p8:fondo"
  const val DateCapsule = "f8p8:capsula-data"
  const val RangeCapsule = "f8p8:capsula-intervallo"
  const val DurationCapsule = "f8p8:capsula-durata"
  const val RepeatRow = "f8p8:ripeti"
}

/** Il fondo di una scena. */
internal enum class F8p8Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

@Composable
private fun F8p8Screen(id: String, title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  var ground by rememberSaveable { mutableStateOf(F8p8Ground.Page) }
  FluidScreen(title = title, subtitle = "F8.8 · $id", onBack = onBack) {
    item(key = "fondo") {
      FluidSegmentedControl(
        options = F8p8Ground.entries,
        selected = ground,
        onSelect = { ground = it },
        modifier = Modifier.fillMaxWidth().testTag(F8p8Tags.Ground),
        label = { it.label },
      )
    }
    item(key = "scena") { F8p8Stage(ground, content) }
  }
}

/** La pagina, o una foto con sopra un pannello di vetro che porta l'elemento (come in un foglio). */
@Composable
private fun F8p8Stage(ground: F8p8Ground, content: @Composable ColumnScope.() -> Unit) {
  if (ground == F8p8Ground.Page) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    return
  }
  val photo = rememberGlassBackdrop()
  Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Group))) {
    Image(
      painter = painterResource(if (ground == F8p8Ground.LightPhoto) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.matchParentSize().glassBackdropSource(photo),
    )
    Column(
      Modifier
        .padding(horizontal = 10.dp, vertical = 24.dp)
        .fillMaxWidth()
        .fluidGlassPanel(photo, GlassDefaults.modalTint(), ContinuousCornerShape(FluidRadius.Group), GlassRole.Modal)
        .padding(vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
private fun Readout(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyLarge,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(F8p8Tags.Readout),
  )
}

@Composable
private fun Caption(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
  )
}

// --- L'agenda: i segni dalla rete e la striscia della settimana ---------------------------------------------------

/** Un impegno dell'agenda finta: deterministico, cosi' ogni giro vede gli stessi segni. */
internal data class F8p8Entry(val date: LocalDate, val title: String, val test: Boolean)

/** Gli impegni di [month]: due o tre a settimana, una verifica ogni tanto. */
internal fun f8p8Entries(month: YearMonth): List<F8p8Entry> = (1..month.lengthOfMonth()).flatMap { d ->
  val date = month.atDay(d)
  val seed = (date.toEpochDay() * 7919L) % 97L
  buildList {
    if (date.dayOfWeek.value <= 5 && seed % 3L == 0L) add(F8p8Entry(date, "Compiti di matematica", false))
    if (date.dayOfWeek.value <= 5 && seed % 5L == 1L) add(F8p8Entry(date, "Esercizi di inglese", false))
    if (date.dayOfWeek.value <= 5 && seed % 11L == 2L) add(F8p8Entry(date, "Verifica di storia", true))
  }
}

/** I segni di [month], come arriverebbero dal registro: i pallini rossi per le verifiche, quelli blu per i compiti. */
internal fun f8p8Marks(month: YearMonth): Map<LocalDate, FluidCalendarMark> =
  f8p8Entries(month).groupBy { it.date }.mapValues { (_, list) ->
    val tests = list.count { it.test }
    val homework = list.size - tests
    FluidCalendarMark(
      count = list.size,
      color = if (tests > 0) Color(0xFFE5484D) else Color(0xFF3E8BFF),
      description = listOfNotNull(
        homework.takeIf { it > 0 }?.let { if (it == 1) "1 compito" else "$it compiti" },
        tests.takeIf { it > 0 }?.let { if (it == 1) "1 verifica" else "$it verifiche" },
      ).joinToString(", "),
    )
  }

/** Quanto ci mette il registro finto a dare i segni di un mese (ms): la griglia non deve saltare quando arrivano. */
internal const val F8p8NetworkMillis: Long = 650L

@Composable
private fun AgendaScene(onBack: () -> Unit) {
  var day by rememberSaveable { mutableLongStateOf(F8p8Today.toEpochDay()) }
  val selected = LocalDate.ofEpochDay(day)
  val state = rememberFluidCalendarState(YearMonth.from(selected))
  val marks = remember { FluidCalendarMarks() }
  // I segni arrivano dalla rete un mese alla volta: quello mostrato, poi i due vicini.
  LaunchedEffect(state.month) {
    val month = state.month
    delay(F8p8NetworkMillis)
    marks.setMonth(month, f8p8Marks(month))
    marks.setMonth(month.plusMonths(1), f8p8Marks(month.plusMonths(1)))
    marks.setMonth(month.minusMonths(1), f8p8Marks(month.minusMonths(1)))
  }
  val entries = remember(day) { f8p8Entries(YearMonth.from(selected)).filter { it.date == selected } }
  val dateText = remember(day) { selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.ITALY)) }
  FluidScreen(title = "L'agenda di ClasseViva", subtitle = "F8.8 · calendario-agenda", onBack = onBack) {
    item(key = "agenda") {
      Column(Modifier.fillParentMaxHeight().fillMaxWidth()) {
        FluidCalendar(
          selection = FluidDateSelection.Single(selected),
          onSelectionChange = { (it as? FluidDateSelection.Single)?.date?.let { d -> day = d.toEpochDay() } },
          modifier = Modifier.testTag(F8p8Tags.Calendar),
          state = state,
          marks = marks,
          today = F8p8Today,
          locale = Locale.ITALY,
        )
        Readout(dateText)
        // La lista sotto: scorrendo in su stringe il mese nella striscia della settimana, come Calendario.
        LazyColumn(
          Modifier.weight(1f).fillMaxWidth().nestedScroll(state.nestedScrollConnection).testTag(F8p8Tags.Agenda),
        ) {
          item(key = "giorno") {
            FluidListGroup(Modifier.padding(top = 8.dp)) {
              if (entries.isEmpty()) {
                FluidListRow(title = "Niente per oggi", subtitle = "Nessun compito e nessuna verifica", disclosure = false)
              }
              entries.forEachIndexed { index, entry ->
                if (index > 0) FluidListDivider()
                FluidListRow(title = entry.title, subtitle = if (entry.test) "Verifica" else "Compito", disclosure = false)
              }
            }
          }
          // Il resto della settimana, perche' la lista abbia di che scorrere.
          items(count = 14, key = { "dopo-$it" }) { i ->
            val next = selected.plusDays(i + 1L)
            FluidListRow(
              title = next.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALY)),
              subtitle = f8p8Entries(YearMonth.from(next)).filter { it.date == next }.joinToString(" · ") { it.title }
                .ifEmpty { "Libero" },
              disclosure = false,
              modifier = Modifier.padding(top = 6.dp),
            )
          }
        }
      }
    }
  }
}

// --- I giorni sparsi ----------------------------------------------------------------------------------------------

@Composable
private fun ScatteredDaysScene(onBack: () -> Unit) {
  var days by rememberSaveable { mutableStateOf(listOf(F8p8Today.minusDays(3).toEpochDay(), F8p8Today.minusDays(12).toEpochDay())) }
  val set = days.map(LocalDate::ofEpochDay).toSet()
  F8p8Screen("calendario-giorni", "I giorni da giustificare", onBack) {
    FluidCalendar(
      selection = FluidDateSelection.Multiple(set),
      onSelectionChange = { (it as? FluidDateSelection.Multiple)?.let { m -> days = m.dates.map(LocalDate::toEpochDay).sorted() } },
      modifier = Modifier.testTag(F8p8Tags.Calendar),
      today = F8p8Today,
      maxDate = F8p8Today,
      locale = Locale.ITALY,
    )
    Readout(if (set.isEmpty()) "Nessun giorno" else "${set.size} giorni: " + set.sorted().joinToString(", ") { "${it.dayOfMonth}/${it.monthValue}" })
    Caption("Un tocco accende o spegne un giorno; cambia mese e i giorni dell'altro mese restano scelti. I giorni dopo oggi sono spenti.")
  }
}

// --- L'intervallo -------------------------------------------------------------------------------------------------

@Composable
private fun RangeScene(onBack: () -> Unit) {
  var start by rememberSaveable { mutableStateOf<Long?>(LocalDate.of(2026, 11, 9).toEpochDay()) }
  var end by rememberSaveable { mutableStateOf<Long?>(LocalDate.of(2026, 11, 20).toEpochDay()) }
  val selection = FluidDateSelection.Range(start?.let(LocalDate::ofEpochDay), end?.let(LocalDate::ofEpochDay))
  F8p8Screen("calendario-intervallo", "I voti di un periodo", onBack) {
    FluidCalendar(
      selection = selection,
      onSelectionChange = { picked ->
        (picked as? FluidDateSelection.Range)?.let { start = it.start?.toEpochDay(); end = it.end?.toEpochDay() }
      },
      modifier = Modifier.testTag(F8p8Tags.Calendar),
      today = F8p8Today,
      locale = Locale.ITALY,
    )
    val range = selection.range
    val first = selection.start
    val rome = ZoneId.of("Europe/Rome")
    Readout(
      when {
        range != null -> "Dal ${range.start.dayOfMonth}/${range.start.monthValue} al ${range.endInclusive.dayOfMonth}/" +
          "${range.endInclusive.monthValue}: ${range.dayCount} giorni, ${range.duration(rome).toHours()} ore"
        first != null -> "Dal ${first.dayOfMonth}/${first.monthValue}: tocca il giorno della fine"
        else -> "Tocca il primo giorno"
      },
    )
    Caption(
      "Primo tocco: l'inizio. Secondo tocco dopo: la fine. Un tocco prima dell'inizio, o a intervallo completo, ricomincia. " +
        "Il 29 marzo e il 25 ottobre contano un giorno anche se durano 23 e 25 ore.",
    )
  }
}

// --- La capsula ---------------------------------------------------------------------------------------------------

@Composable
private fun CapsuleScene(onBack: () -> Unit) {
  var due by rememberSaveable { mutableLongStateOf(LocalDate.of(2026, 11, 23).toEpochDay()) }
  var from by rememberSaveable { mutableStateOf<Long?>(LocalDate.of(2026, 12, 21).toEpochDay()) }
  var to by rememberSaveable { mutableStateOf<Long?>(LocalDate.of(2027, 1, 6).toEpochDay()) }
  var minutes by rememberSaveable { mutableLongStateOf(45L * 60L) }
  var english by rememberSaveable { mutableStateOf(false) }
  val locale = if (english) Locale.US else Locale.ITALY
  val labels = if (english) FluidCalendarLabels.english() else FluidCalendarLabels()
  F8p8Screen("capsula-data", "La data in una riga", onBack) {
    FluidSegmentedControl(
      options = listOf(false, true),
      selected = english,
      onSelect = { english = it },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      label = { if (it) "English" else "Italiano" },
    )
    FluidListGroup(Modifier.padding(horizontal = 4.dp)) {
      CapsuleRow(if (english) "Due" else "Scadenza") {
        FluidDateCapsule(
          selection = FluidDateSelection.Single(LocalDate.ofEpochDay(due)),
          onSelectionChange = { (it as? FluidDateSelection.Single)?.date?.let { d -> due = d.toEpochDay() } },
          modifier = Modifier.testTag(F8p8Tags.DateCapsule),
          label = if (english) "Due" else "Scadenza",
          locale = locale,
          labels = labels,
        )
      }
      FluidListDivider()
      CapsuleRow(if (english) "Holidays" else "Vacanze") {
        FluidDateCapsule(
          selection = FluidDateSelection.Range(from?.let(LocalDate::ofEpochDay), to?.let(LocalDate::ofEpochDay)),
          onSelectionChange = { (it as? FluidDateSelection.Range)?.let { r -> from = r.start?.toEpochDay(); to = r.end?.toEpochDay() } },
          modifier = Modifier.testTag(F8p8Tags.RangeCapsule),
          label = if (english) "Holidays" else "Vacanze",
          locale = locale,
          labels = labels,
        )
      }
      FluidListDivider()
      CapsuleRow(if (english) "Sleep timer" else "Spegnimento") {
        FluidDurationCapsule(
          seconds = minutes,
          onSecondsChange = { minutes = it },
          modifier = Modifier.testTag(F8p8Tags.DurationCapsule),
          label = if (english) "Sleep timer" else "Spegnimento",
          maxHours = 5,
          minuteInterval = 5,
          minimumSeconds = 5 * 60L,
        )
      }
    }
    Readout(
      "Scadenza ${LocalDate.ofEpochDay(due)}, vacanze ${from?.let(LocalDate::ofEpochDay) ?: "—"} → " +
        "${to?.let(LocalDate::ofEpochDay) ?: "—"}, spegnimento ${minutes / 60} min",
    )
    Caption("La capsula diventa il calendario e scrive subito il giorno scelto; come in iOS il popover ci rientra quando tocchi fuori.")
  }
}

@Composable
private fun CapsuleRow(title: String, capsule: @Composable () -> Unit) {
  Row(
    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    capsule()
  }
}

// --- Il pannello «Ripeti» -----------------------------------------------------------------------------------------

@Composable
private fun RepeatScene(onBack: () -> Unit) {
  var rule by remember { mutableStateOf<FluidRecurrenceRule?>(null) }
  var open by rememberSaveable { mutableStateOf(false) }
  var english by rememberSaveable { mutableStateOf(false) }
  val locale = if (english) Locale.US else Locale.ITALY
  val namespace = rememberFluidGlassNamespace()
  val source = remember(namespace) { FluidTransitionSource("ripeti", namespace) }
  val summary = rule?.let { FluidRecurrenceText.describe(it, F8p8Today, locale) } ?: if (english) "Never" else "Mai"
  F8p8Screen("ripeti", "Il pannello «Ripeti»", onBack) {
    FluidSegmentedControl(
      options = listOf(false, true),
      selected = english,
      onSelect = { english = it },
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      label = { if (it) "English" else "Italiano" },
    )
    FluidListGroup(Modifier.padding(horizontal = 4.dp)) {
      FluidListRow(
        title = if (english) "Repeat" else "Ripeti",
        subtitle = summary,
        modifier = Modifier.fluidTransitionSource("ripeti", namespace, ContinuousCornerShape(FluidRadius.Group)).testTag(F8p8Tags.RepeatRow),
        onClick = { open = true },
      )
    }
    Readout(rule?.toRRule()?.let { "RRULE:$it" } ?: "Nessuna regola")
    Caption("Il pannello nasce dalla riga; la frase e le prossime date cambiano sfocate, perche' le cambia la regola.")
  }
  FluidRecurrenceSheet(
    visible = open,
    onDismissRequest = { open = false },
    onConfirm = { rule = it; open = false },
    start = F8p8Today,
    initialRule = rule,
    locale = locale,
    labels = if (english) FluidRecurrenceLabels.english() else FluidRecurrenceLabels(),
    source = source,
  )
}
