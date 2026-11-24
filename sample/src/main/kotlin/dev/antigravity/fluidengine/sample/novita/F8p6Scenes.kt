package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.TryArea
import dev.antigravity.fluidengine.sample.TryItem
import dev.antigravity.fluidengine.sample.TryTarget
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidBarChart
import dev.antigravity.fluidengine.ui.fluid.FluidBarGroup
import dev.antigravity.fluidengine.ui.fluid.FluidChartRangeHighlight
import dev.antigravity.fluidengine.ui.fluid.FluidChartSeries
import dev.antigravity.fluidengine.ui.fluid.FluidGauge
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeThreshold
import dev.antigravity.fluidengine.ui.fluid.FluidLineChart
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

// F8.6 · Grafici da toccare e indicatore a quadrante, nei casi delle app di Alessio (le schede `grafici-da-toccare` e
// `indicatore-a-quadrante`): l'andamento dei voti di ClasseViva, lo spazio mese per mese di MyCloud, i minuti di ascolto
// di Fluidify, le assenze sul massimo, lo spazio occupato e i decibel dal vivo di Pampa Sounds. Ogni scena ha il suo fondo
// (pagina, foto chiara, foto scura): sulle foto il grafico sta in un pannello di vetro, come in un foglio.

/** Le scene di F8.6, nell'ordine delle idee. */
internal val F8p6Section = NewElementSection(
  pack = "F8.6",
  title = "Grafici da toccare e quadrante",
  scenes = listOf(
    NewElementScene(
      "andamento-voti", "I voti di Matematica",
      "ClasseViva · il dito sull'andamento: punto, linea e capsula; due dita come Borsa, verde se sale e rosso se scende",
    ) { onBack -> GradesScene(onBack) },
    NewElementScene(
      "spazio-mese", "Lo spazio mese per mese",
      "MyCloud Gallery · le barre da toccare: la casella, la capsula coi GB, due dita con la fascia neutra",
    ) { onBack -> StorageBarsScene(onBack) },
    NewElementScene(
      "ascolto-settimane", "Minuti di ascolto",
      "Fluidify · due serie sotto lo stesso dito, musica e podcast settimana per settimana",
    ) { onBack -> ListeningScene(onBack) },
    NewElementScene(
      "quadrante-assenze", "Le assenze sul massimo",
      "ClasseViva · l'arco col numero, «limite 25 %», e la media di ogni materia in un anello da riga",
    ) { onBack -> AbsencesScene(onBack) },
    NewElementScene(
      "quadrante-forme", "Spazio e decibel",
      "MyCloud, Pampa Store, Pampa Sounds · la barra a capacita', gli anelli, e l'arco dei decibel dal vivo",
    ) { onBack -> ShapesScene(onBack) },
  ),
  detail = "Il grafico si tocca come in Borsa e Salute; il quadrante come Gauge di SwiftUI e gli anelli di Attivita'",
)

/** Le voci da provare a mano di F8.6, in coda a quelle di «Da provare». */
internal val F8p6TryItems: List<TryItem> = listOf(
  TryItem(
    "f8-6-scrubber", TryArea.ContentData, "Il dito sull'andamento",
    "Apri «I voti di Matematica», trascina il dito di lato sull'andamento e poi tienilo fermo su un punto: giudica se la " +
      "capsula di vetro segue il dito restando dentro il grafico e se si sente una tacca per verifica.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › I voti di Matematica"),
  ),
  TryItem(
    "f8-6-due-dita", TryArea.ContentData, "Due dita come Borsa",
    "In «I voti di Matematica» appoggia due dita su due verifiche lontane, poi su due in cui il voto scende: giudica se " +
      "il resto sbiadisce come in Borsa e se il verde e il rosso della differenza si leggono; in «Lo spazio mese per " +
      "mese» la fascia neutra.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › I voti di Matematica"),
  ),
  TryItem(
    "f8-6-quadrante", TryArea.ContentData, "Il quadrante delle assenze",
    "Apri «Le assenze sul massimo» e porta il cursore oltre il 20 e il 25 %: giudica se l'arco cambia colore senza " +
      "rimbalzare e se «Limite 25 %» si capisce anche senza guardare il colore.",
    TryTarget.Page(GalleryPage.NewElements, "Elementi nuovi › Le assenze sul massimo"),
  ),
)

/** I tag delle prove. */
internal object F8p6Tags {
  const val Chart = "f8p6:grafico"
  const val Header = "f8p6:testata"
  const val Gauge = "f8p6:quadrante"
  const val Slider = "f8p6:cursore"
  const val Background = "f8p6:fondo"
}

/** Il fondo di una scena. */
internal enum class F8p6Ground(val label: String) { Page("Pagina"), LightPhoto("Foto chiara"), DarkPhoto("Foto scura") }

@Composable
private fun F8p6Screen(id: String, title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  var ground by rememberSaveable { mutableStateOf(F8p6Ground.Page) }
  FluidScreen(title = title, subtitle = "F8.6 · $id", onBack = onBack) {
    item(key = "fondo") {
      FluidSegmentedControl(
        options = F8p6Ground.entries,
        selected = ground,
        onSelect = { ground = it },
        modifier = Modifier.fillMaxWidth().testTag(F8p6Tags.Background),
        label = { it.label },
      )
    }
    item(key = "scena") { F8p6Stage(ground, content) }
  }
}

/** La pagina, o una foto con sopra un pannello di vetro che porta l'elemento. */
@Composable
private fun F8p6Stage(ground: F8p6Ground, content: @Composable ColumnScope.() -> Unit) {
  if (ground == F8p6Ground.Page) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    return
  }
  val photo = rememberGlassBackdrop()
  Box(Modifier.padding(top = 12.dp).fillMaxWidth().clip(ContinuousCornerShape(FluidRadius.Group))) {
    Image(
      painter = painterResource(if (ground == F8p6Ground.LightPhoto) R.drawable.viewer_flower else R.drawable.viewer_astronaut),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.matchParentSize().glassBackdropSource(photo),
    )
    Column(
      Modifier
        .padding(horizontal = 12.dp, vertical = 28.dp)
        .fillMaxWidth()
        .fluidGlassPanel(photo, GlassDefaults.modalTint(), ContinuousCornerShape(FluidRadius.Group), GlassRole.Modal)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
private fun Caption(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
  )
}

/**
 * La testata di Borsa: il valore grande e la riga sotto. A riposo dice il riassunto; mentre il dito scorre dice il punto
 * scelto (o la differenza fra due), come la testata di Borsa che segue il dito. La scrive l'app da `onScrub`.
 */
@Composable
private fun ChartHeader(value: String, detail: String) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag(F8p6Tags.Header)) {
    Text(value, style = FluidTextStyles.largeNumeric, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
  }
}

private val Italian = Locale.ITALY

private fun one(v: Float): String = String.format(Italian, if (v == v.roundToInt().toFloat()) "%.0f" else "%.1f", v)

// --- I grafici da toccare ------------------------------------------------------------------------------------------

/** Le verifiche di Matematica da settembre a febbraio: dodici voti. */
internal val F8p6GradeDates = listOf("12 set", "26 set", "10 ott", "24 ott", "7 nov", "21 nov", "5 dic", "19 dic", "16 gen", "30 gen", "13 feb", "27 feb")
internal val F8p6Grades = listOf(5.5f, 6f, 6.5f, 5f, 6f, 7f, 7.5f, 6.5f, 7f, 8f, 7.5f, 8.5f)

@Composable
private fun GradesScene(onBack: () -> Unit) {
  val series = remember { listOf(FluidChartSeries("Matematica")) }
  val points = remember { listOf<List<Float?>>(F8p6Grades) }
  var scrub by remember { mutableStateOf<IntRange?>(null) }
  val average = F8p6Grades.average().toFloat()
  F8p6Screen("andamento-voti", "I voti di Matematica", onBack) {
    val range = scrub
    when {
      range == null -> ChartHeader("Media ${one(average)}", "12 verifiche · da settembre a febbraio")
      range.first == range.last -> ChartHeader(one(F8p6Grades[range.first]), "Verifica del ${F8p6GradeDates[range.first]}")
      else -> {
        val delta = F8p6Grades[range.last] - F8p6Grades[range.first]
        ChartHeader((if (delta > 0) "+" else if (delta < 0) "−" else "") + one(kotlin.math.abs(delta)), "Dal ${F8p6GradeDates[range.first]} al ${F8p6GradeDates[range.last]}")
      }
    }
    FluidLineChart(
      series = series,
      points = points,
      xLabels = F8p6GradeDates,
      valueText = ::one,
      startAtZero = false,
      scaleLabel = "Voti da 1 a 10",
      contentDescription = "I voti di Matematica",
      onScrub = { scrub = it },
      modifier = Modifier.testTag(F8p6Tags.Chart),
    )
    Caption(
      "Trascina di lato, o tieni fermo il dito: il punto, la linea e la capsula. Con due dita, come Borsa, il resto " +
        "sbiadisce e la differenza fra due verifiche e' verde se il voto sale, rossa se scende.",
    )
  }
}

/** Lo spazio occupato di MyCloud, in GB, mese per mese. */
internal val F8p6Months = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
internal val F8p6Storage = listOf(18.2f, 19.0f, 20.4f, 21.1f, 23.8f, 26.5f, 31.2f, 34.0f, 34.9f, 36.7f, 38.8f, 41.5f)

@Composable
private fun StorageBarsScene(onBack: () -> Unit) {
  val groups = remember { F8p6Months.indices.map { FluidBarGroup(F8p6Months[it], listOf(F8p6Storage[it])) } }
  val series = remember { listOf(FluidChartSeries("Spazio")) }
  val gb: (Float) -> String = remember { { v -> "${one(v)} GB" } }
  F8p6Screen("spazio-mese", "Lo spazio mese per mese", onBack) {
    FluidBarChart(
      groups = groups,
      series = series,
      valueText = gb,
      scaleLabel = "GB occupati alla fine del mese",
      contentDescription = "Lo spazio di MyCloud",
      // Lo spazio che cresce non e' ne' bene ne' male: la fascia neutra, senza verde e rosso.
      rangeHighlight = FluidChartRangeHighlight.Band,
      modifier = Modifier.testTag(F8p6Tags.Chart),
    )
    Caption(
      "La capsula dice i GB del mese; con due dita quanto e' cresciuto lo spazio fra due mesi, su una fascia neutra: " +
        "qui salire non e' ne' bene ne' male.",
    )
  }
}

@Composable
private fun ListeningScene(onBack: () -> Unit) {
  val weeks = remember { (1..10).map { "Sett. $it" } }
  val series = remember { listOf(FluidChartSeries("Musica"), FluidChartSeries("Podcast")) }
  val points = remember {
    listOf<List<Float?>>(
      listOf(320f, 280f, 410f, 390f, 450f, 300f, 520f, 480f, 455f, 600f),
      listOf(90f, 120f, 60f, null, 150f, 180f, 140f, 200f, 210f, 160f),
    )
  }
  val minutes: (Float) -> String = remember { { v -> "${v.roundToInt()} min" } }
  F8p6Screen("ascolto-settimane", "Minuti di ascolto", onBack) {
    FluidLineChart(
      series = series,
      points = points,
      xLabels = weeks,
      valueText = minutes,
      scaleLabel = "Minuti alla settimana",
      contentDescription = "Minuti di ascolto",
      modifier = Modifier.testTag(F8p6Tags.Chart),
    )
    Caption("La quarta settimana non ha i podcast: la capsula dice «nessun dato», e la differenza non si inventa.")
  }
}

// --- Il quadrante --------------------------------------------------------------------------------------------------

private val AbsenceLimits = listOf(FluidGaugeThreshold(20f, "Attenzione"), FluidGaugeThreshold(25f, "Limite"))

@Composable
private fun AbsencesScene(onBack: () -> Unit) {
  var percent by rememberSaveable { mutableFloatStateOf(18f) }
  val percentText: (Float) -> String = remember { { v -> "${v.roundToInt()} %" } }
  F8p6Screen("quadrante-assenze", "Le assenze sul massimo", onBack) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      FluidGauge(
        value = percent,
        range = 0f..100f,
        label = "Assenze",
        valueText = percentText,
        thresholds = AbsenceLimits,
        size = 200.dp,
        modifier = Modifier.testTag(F8p6Tags.Gauge),
      )
    }
    FluidSlider(
      value = percent,
      onValueChange = { percent = it.roundToInt().toFloat() },
      valueRange = 0f..40f,
      contentDescription = "Assenze di prova",
      modifier = Modifier.fillMaxWidth().testTag(F8p6Tags.Slider),
    )
    Caption("Porta le assenze oltre il 20 % e il 25 %: l'arco cambia colore, e la soglia superata e' anche scritta.")
    val subjects = remember { listOf("Matematica" to 6.9f, "Italiano" to 7.4f, "Inglese" to 8.2f, "Fisica" to 5.6f) }
    val pass = remember { listOf(FluidGaugeThreshold(6f, "Sufficienza", androidx.compose.ui.graphics.Color(0xFF2E7D32))) }
    FluidListGroup {
      subjects.forEachIndexed { i, (name, mean) ->
        if (i > 0) FluidListDivider()
        FluidListRow(
          title = name,
          subtitle = if (mean >= 6f) "Media sufficiente" else "Media sotto il 6",
          leadingTile = false,
          disclosure = false,
          leading = {
            FluidGauge(
              value = mean,
              range = 0f..10f,
              style = FluidGaugeStyle.Ring,
              label = "Media di $name",
              valueText = ::one,
              thresholds = pass,
              // Sotto il 6 l'anello e' rosso: la soglia buona e' verde, il valore di serie lo dice l'app.
              color = MaterialTheme.colorScheme.error,
              size = 40.dp,
            )
          },
        )
      }
    }
  }
}

@Composable
private fun ShapesScene(onBack: () -> Unit) {
  val gb: (Float) -> String = remember { { v -> "${one(v)} GB" } }
  var live by rememberSaveable { mutableStateOf(false) }
  val decibel = remember { mutableFloatStateOf(62f) }
  LaunchedEffect(live) {
    var t = 0f
    while (live) {
      // Otto letture al secondo, come il fonometro di Pampa Sounds.
      delay(125)
      t += 0.125f
      decibel.floatValue = (68f + 14f * sin(t * 1.7f) + 6f * sin(t * 5.3f)).coerceIn(30f, 110f)
    }
  }
  val dbText: (Float) -> String = remember { { v -> "${v.roundToInt()} dB" } }
  val loud = remember { listOf(FluidGaugeThreshold(70f, "Forte"), FluidGaugeThreshold(85f, "Dannoso")) }
  F8p6Screen("quadrante-forme", "Spazio e decibel", onBack) {
    FluidGauge(
      value = 41.5f,
      range = 0f..64f,
      style = FluidGaugeStyle.Capacity,
      label = "Spazio di MyCloud",
      valueText = gb,
      maxLabel = "64 GB",
      thresholds = remember { listOf(FluidGaugeThreshold(57.6f, "Quasi pieno")) },
      modifier = Modifier.testTag(F8p6Tags.Gauge),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
      listOf("Foto" to 0.62f, "Video" to 0.27f, "Documenti" to 0.08f).forEach { (name, share) ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          FluidGauge(value = share * 100f, range = 0f..100f, style = FluidGaugeStyle.Ring, label = name, valueText = { "${it.roundToInt()}%" }, size = 48.dp)
          Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
      }
    }
    FluidSegmentedControl(
      options = listOf(false, true),
      selected = live,
      onSelect = { live = it },
      modifier = Modifier.fillMaxWidth(),
      label = { if (it) "Dal vivo" else "Fermo" },
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      // La lambda: otto valori al secondo ridisegnano l'arco, non la scena.
      FluidGauge(
        value = { decibel.floatValue },
        range = 30f..110f,
        label = "Livello",
        valueText = dbText,
        thresholds = loud,
        size = 180.dp,
      )
    }
    Text(
      "Il colore non e' l'unica informazione: il numero c'e' sempre, e la soglia superata e' scritta.",
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
