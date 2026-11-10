package dev.antigravity.fluidengine.sample.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidBarChart
import dev.antigravity.fluidengine.ui.fluid.FluidBarGroup
import dev.antigravity.fluidengine.ui.fluid.FluidChartEntry
import dev.antigravity.fluidengine.ui.fluid.FluidChartMessage
import dev.antigravity.fluidengine.ui.fluid.FluidChartSeries
import dev.antigravity.fluidengine.ui.fluid.FluidChartSkeleton
import dev.antigravity.fluidengine.ui.fluid.FluidChartSumPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidDonutChart
import dev.antigravity.fluidengine.ui.fluid.FluidLineChart
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * I grafici (F1.5b): ciambella, barre orizzontali e verticali (anche impilate), andamento, legenda e gli stati
 * che li accompagnano, con statistiche finte ma credibili di una casella di posta.
 *
 * Sta nella galleria come pagina «Grafici» (con la matrice degli stati). Mostra, nell'ordine:
 * 1. la ripartizione per categoria (le cinque categorie **disgiunte**, che sommano 100%) con il valore nel foro;
 * 2. le **etichette sovrapposte** (un messaggio puo' averne due) in barre separate col denominatore dichiarato:
 *    le barre non sommano 100% e il grafico lo dice;
 * 3. molte fette: le minuscole confluiscono in «Altro»;
 * 4. barre verticali, affiancate e impilate, e l'andamento (linea con un buco, area);
 * 5. i dati che non si disegnano: vuoto, caricamento, errore, ripartizione che non somma, valore negativo;
 * 6. le stesse cose col testo al 200%, in scuro e con il movimento ridotto.
 */
@Composable
fun ChartsDemo() {
  Column(
    modifier = Modifier.fillMaxWidth().testTag("charts-demo"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    CategorySection()
    LabelsSection()
    OtherSection()
    WeeklySection()
    MonthlySection()
    TrendSection()
    StatesSection()
    VariantsSection()
  }
}

// --- Dati finti ------------------------------------------------------------------------------------------

private val categories = listOf(
  FluidChartEntry("Principale", 612f),
  FluidChartEntry("Promozioni", 238f),
  FluidChartEntry("Social", 96f),
  FluidChartEntry("Aggiornamenti", 71f),
  FluidChartEntry("Forum", 23f),
)

private const val TotalMail = 1040f

/** Le nostre etichette: un messaggio puo' averne piu' d'una, quindi i conteggi **non** sommano al totale. */
private val labelCounts = listOf(
  FluidBarGroup("Lavoro", listOf(412f)),
  FluidBarGroup("Clienti", listOf(380f)),
  FluidBarGroup("Fatture e ricevute", listOf(164f)),
  FluidBarGroup("Famiglia", listOf(121f)),
  FluidBarGroup("Viaggi", listOf(58f)),
)

private val manySources = listOf(
  FluidChartEntry("Newsletter A", 420f),
  FluidChartEntry("Newsletter B", 260f),
  FluidChartEntry("Negozio", 150f),
  FluidChartEntry("Banca", 70f),
  FluidChartEntry("Social", 40f),
  FluidChartEntry("Forum", 20f),
  FluidChartEntry("Altri 1", 14f),
  FluidChartEntry("Altri 2", 9f),
  FluidChartEntry("Altri 3", 6f),
  FluidChartEntry("Altri 4", 4f),
)

private val weeks = listOf("3 nov", "10 nov", "17 nov", "24 nov", "1 dic", "8 dic", "15 dic", "22 dic", "29 dic", "5 gen", "12 gen", "19 gen")
private val received = listOf(88f, 94f, 71f, 102f, 97f, 85f, 64f, 58f, 91f, 120f, 110f, 99f)
private val sent = listOf(21f, 25f, 18f, 30f, 28f, 22f, 12f, 9f, 24f, 35f, 31f, 27f)

private val months = listOf("Giu", "Lug", "Ago", "Set", "Ott", "Nov")
private val categorySeries = listOf(
  FluidChartSeries("Principale"),
  FluidChartSeries("Promozioni"),
  FluidChartSeries("Social"),
  FluidChartSeries("Aggiornamenti"),
)
private val monthlyByCategory = listOf(
  FluidBarGroup("Giu", listOf(310f, 120f, 55f, 40f)),
  FluidBarGroup("Lug", listOf(280f, 140f, 61f, 38f)),
  FluidBarGroup("Ago", listOf(190f, 90f, 33f, 29f)),
  FluidBarGroup("Set", listOf(330f, 160f, 72f, 51f)),
  FluidBarGroup("Ott", listOf(356f, 170f, 80f, 57f)),
  FluidBarGroup("Nov", listOf(340f, 150f, 66f, 49f)),
)

private val daily: List<Float?> = List(30) { day ->
  when {
    day == 11 || day == 12 -> null // il telefono era spento
    else -> (20 + (day * 7) % 17 + if (day % 7 == 5 || day % 7 == 6) -9 else 6).toFloat()
  }
}
private val dayLabels = List(30) { "${it + 1} nov" }

// --- Sezioni ------------------------------------------------------------------------------------------

@Composable
private fun Panel(testTag: String, content: @Composable () -> Unit) {
  FluidCard(modifier = Modifier.testTag(testTag), glass = true) { content() }
}

@Composable
private fun CategorySection() {
  var selected by remember { mutableStateOf<Int?>(null) }
  FluidSectionHeader(
    title = "Arrivi per categoria",
    detail = "Cinque categorie disgiunte: ogni mail e' in una sola, la somma e' 100%. Tocca una fetta o una riga.",
  )
  Panel("charts-category") {
    FluidDonutChart(
      entries = categories,
      centerValue = "1.040",
      centerLabel = "mail",
      whole = TotalMail,
      selectedIndex = selected,
      onSelectedChange = { selected = it },
      contentDescription = "Arrivi per categoria, ultimi 30 giorni",
    )
    Text(
      text = selected?.let { "Scelta: ${categories[it].label}" } ?: "Nessuna fetta scelta",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.testTag("charts-category-selected"),
    )
    Text(
      text = "Basato su 1.040 mail del telefono",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun LabelsSection() {
  FluidSectionHeader(
    title = "Le tue etichette",
    detail = "Un messaggio puo' averne piu' d'una: le barre non sommano 100%. Il denominatore e' dichiarato.",
  )
  Panel("charts-labels") {
    FluidBarChart(
      groups = labelCounts,
      series = listOf(FluidChartSeries("Mail con l'etichetta")),
      horizontal = true,
      maxValue = TotalMail,
      scaleLabel = "Su 1.040 mail del periodo; una mail puo' avere piu' etichette",
      contentDescription = "Mail per etichetta",
    )
  }
}

@Composable
private fun OtherSection() {
  FluidSectionHeader(
    title = "Molte fette",
    detail = "Dieci mittenti: le fette minuscole confluiscono in «Altro» (e restano nella legenda come una sola riga).",
  )
  Panel("charts-other") {
    FluidDonutChart(
      entries = manySources,
      centerValue = "993",
      centerLabel = "mail",
      contentDescription = "Mail per mittente",
    )
  }
}

@Composable
private fun WeeklySection() {
  FluidSectionHeader(
    title = "Settimana per settimana",
    detail = "Barre verticali con un valore per barra (dove c'e' posto) e etichette che si diradano.",
  )
  Panel("charts-weekly") {
    FluidBarChart(
      groups = weeks.indices.map { FluidBarGroup(weeks[it], listOf(received[it])) },
      series = listOf(FluidChartSeries("Ricevute")),
      scaleLabel = "Mail ricevute a settimana",
      contentDescription = "Mail ricevute per settimana",
    )
    FluidBarChart(
      groups = weeks.take(5).let { first ->
        first.indices.map { FluidBarGroup(first[it], listOf(received[it], sent[it])) }
      },
      series = listOf(FluidChartSeries("Ricevute"), FluidChartSeries("Inviate")),
      scaleLabel = "Ricevute e inviate, affiancate",
    )
  }
}

@Composable
private fun MonthlySection() {
  FluidSectionHeader(
    title = "Per mese e categoria",
    detail = "Barre impilate: solo per serie disgiunte; la barra intera e' la somma.",
  )
  Panel("charts-monthly") {
    FluidBarChart(
      groups = monthlyByCategory,
      series = categorySeries,
      stacked = true,
      scaleLabel = "Mail ricevute al mese, per categoria",
      contentDescription = "Arrivi per mese e categoria",
    )
  }
}

@Composable
private fun TrendSection() {
  FluidSectionHeader(
    title = "Andamento",
    detail = "Una linea con due serie; un'area con un buco (due giorni senza dati: la linea si interrompe).",
  )
  Panel("charts-trend") {
    FluidLineChart(
      series = listOf(FluidChartSeries("Ricevute"), FluidChartSeries("Inviate")),
      points = listOf(received, sent),
      xLabels = weeks,
      scaleLabel = "Mail a settimana",
      contentDescription = "Andamento ricevute e inviate",
    )
    FluidLineChart(
      series = listOf(FluidChartSeries("Ricevute")),
      points = listOf(daily),
      xLabels = dayLabels,
      fill = true,
      scaleLabel = "Mail al giorno",
      contentDescription = "Mail ricevute ogni giorno di novembre",
    )
  }
}

@Composable
private fun StatesSection() {
  var retries by remember { mutableIntStateOf(0) }
  FluidSectionHeader(
    title = "Stati",
    detail = "Nessun dato nel periodo, caricamento, errore con «Riprova», ripartizione che non somma, valore negativo.",
  )
  Panel("charts-states") {
    Box(Modifier.testTag("charts-state-empty")) { FluidDonutChart(entries = emptyList()) }
    Box(Modifier.testTag("charts-state-zero")) {
      FluidBarChart(groups = listOf(FluidBarGroup("Lun", listOf(0f))), series = listOf(FluidChartSeries("Mail")))
    }
    Box(Modifier.testTag("charts-state-loading")) { FluidChartSkeleton() }
    Box(Modifier.testTag("charts-state-error")) {
      FluidChartMessage(
        text = if (retries == 0) "Impossibile calcolare le statistiche" else "Riprovato $retries volte",
        detail = "Controlla la connessione e riprova.",
        actionLabel = "Riprova",
        onAction = { retries++ },
      )
    }
    Box(Modifier.testTag("charts-state-sum")) {
      FluidDonutChart(
        entries = listOf(FluidChartEntry("A", 60f), FluidChartEntry("B", 30f)),
        whole = 100f,
      )
    }
    Box(Modifier.testTag("charts-state-normalized")) {
      FluidDonutChart(
        entries = listOf(FluidChartEntry("A", 60f), FluidChartEntry("B", 30f)),
        whole = 100f,
        sumPolicy = FluidChartSumPolicy.Normalize,
      )
    }
    Box(Modifier.testTag("charts-state-negative")) {
      FluidDonutChart(entries = listOf(FluidChartEntry("A", 5f), FluidChartEntry("B", -1f)))
    }
    Box(Modifier.testTag("charts-state-huge")) {
      FluidBarChart(
        groups = listOf(FluidBarGroup("Enorme", listOf(4.2e12f)), FluidBarGroup("Piccolo", listOf(3f))),
        series = listOf(FluidChartSeries("Byte")),
        horizontal = true,
      )
    }
  }
}

// --- Gli stessi grafici: testo 200%, scuro, movimento ridotto -----------------------------------------------

@Composable
private fun VariantsSection() {
  FluidSectionHeader(
    title = "Varianti",
    detail = "Testo al 200% (le etichette si diradano, l'anello si assottiglia), tema scuro, movimento ridotto.",
  )
  val density = LocalDensity.current
  Column(
    modifier = Modifier.fillMaxWidth().testTag("charts-variant-large-text"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Testo al 200%", style = MaterialTheme.typography.labelLarge)
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) { VariantCharts() }
  }
  Column(
    modifier = Modifier.fillMaxWidth().testTag("charts-variant-dark"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Tema scuro", style = MaterialTheme.typography.labelLarge)
    FluidTheme(settings = EngineSettings(themeMode = ThemeMode.DARK), systemBars = false) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(ContinuousCornerShape(FluidRadius.Group))
          .background(MaterialTheme.colorScheme.surface)
          .padding(12.dp),
      ) { VariantCharts() }
    }
  }
  Column(
    modifier = Modifier.fillMaxWidth().testTag("charts-variant-reduced-motion"),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Movimento ridotto", style = MaterialTheme.typography.labelLarge)
    CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 0f)) {
      VariantCharts()
    }
  }
}

@Composable
private fun VariantCharts() {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    FluidDonutChart(entries = categories, centerValue = "1.040", centerLabel = "mail")
    FluidBarChart(
      groups = monthlyByCategory,
      series = categorySeries,
      stacked = true,
      scaleLabel = "Mail al mese per categoria",
    )
    FluidLineChart(
      series = listOf(FluidChartSeries("Ricevute")),
      points = listOf(received),
      xLabels = weeks,
      scaleLabel = "Mail a settimana",
    )
  }
}
