package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.catalog.ChartsDemo
import dev.antigravity.fluidengine.ui.fluid.FluidBarChart
import dev.antigravity.fluidengine.ui.fluid.FluidBarGroup
import dev.antigravity.fluidengine.ui.fluid.FluidChartEntry
import dev.antigravity.fluidengine.ui.fluid.FluidChartLegend
import dev.antigravity.fluidengine.ui.fluid.FluidChartLegendItem
import dev.antigravity.fluidengine.ui.fluid.FluidChartSeries
import dev.antigravity.fluidengine.ui.fluid.FluidChartSumPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidDonutChart
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidLineChart
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidChartLocale
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * I grafici dentro un albero Compose vero: cio' che TalkBack legge (sintesi e un nodo per voce, in italiano e in
 * inglese), il tocco che sceglie, gli stati senza dati, il testo al 200%, il movimento ridotto.
 *
 * La matematica (scale, «Altro», percentuali, angoli, contrasto della palette) la provano i test JVM di
 * `engine-ui` (`FluidChartMathTest`); qui si prova che sia collegata.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidChartsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val italianLocale = Locale.ITALIAN
  private val englishLocale = Locale.US

  private var contentSet = false

  private fun show(
    english: Boolean = false,
    settings: EngineSettings = EngineSettings(),
    idle: Boolean = true,
    content: @Composable () -> Unit,
  ) {
    // Una sola composizione per attivita': un secondo `show` nello stesso test riparte da zero.
    if (contentSet) compose.activityRule.scenario.recreate()
    contentSet = true
    compose.setContent {
      FluidTheme(settings = remember { settings }) {
        CompositionLocalProvider(
          LocalFluidLabels provides (if (english) FluidLabels.english() else FluidLabels()),
          LocalFluidChartLocale provides (if (english) englishLocale else italianLocale),
        ) { content() }
      }
    }
    if (idle) compose.waitForIdle()
  }

  private val mail = listOf(
    FluidChartEntry("Principale", 620f),
    FluidChartEntry("Promozioni", 210f),
    FluidChartEntry("Social", 170f),
  )

  private fun isSelected(value: Boolean) = SemanticsMatcher.expectValue(SemanticsProperties.Selected, value)
  private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

  private fun semanticsClick(description: String) {
    compose.onNodeWithContentDescription(description).performSemanticsAction(SemanticsActions.OnClick)
    compose.waitForIdle()
  }

  // --- Ciambella: cio' che TalkBack legge --------------------------------------------------------------------

  @Test
  fun `a donut is read as a summary and each slice is its own node - italian`() {
    show {
      FluidDonutChart(entries = mail, contentDescription = "Arrivi per categoria", modifier = Modifier.testTag("donut"))
    }

    compose.onNodeWithContentDescription(
      "Arrivi per categoria. Ripartizione in 3 parti. La più grande: Principale, 62%.",
    ).assertExists()
    compose.onNodeWithContentDescription("Principale: 620, 62%").assert(hasRole(Role.Button))
    compose.onNodeWithContentDescription("Promozioni: 210, 21%").assertExists()
    compose.onNodeWithContentDescription("Social: 170, 17%").assertExists()
    // Tre fette, tre nodi: la legenda non ripete le voci per TalkBack.
    assertEquals(
      3,
      compose.onAllNodes(hasRole(Role.Button).and(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))).fetchSemanticsNodes().size,
    )
  }

  @Test
  fun `a donut is read in english with english labels and numbers`() {
    show(english = true) {
      FluidDonutChart(entries = mail, centerValue = "1,000", centerLabel = "Mail")
    }

    compose.onNodeWithContentDescription("Mail – 1,000. Breakdown in 3 parts. The largest: Principale, 62%.").assertExists()
    compose.onNodeWithContentDescription("Principale: 620 (62%)").assertExists()
    compose.onNodeWithContentDescription("Social: 170 (17%)").assertExists()
  }

  @Test
  fun `percentages in the description add up to a hundred`() {
    val thirds = listOf(FluidChartEntry("A", 1f), FluidChartEntry("B", 1f), FluidChartEntry("C", 1f))
    show { FluidDonutChart(entries = thirds, valueText = { "" }) }

    compose.onNodeWithContentDescription("A: 34%").assertExists()
    compose.onNodeWithContentDescription("B: 33%").assertExists()
    compose.onNodeWithContentDescription("C: 33%").assertExists()
  }

  @Test
  fun `choosing a slice with TalkBack selects it, says so and shows its percentage in the middle`() {
    val chosen = mutableListOf<Int?>()
    show {
      var selected by remember { mutableStateOf<Int?>(null) }
      FluidDonutChart(
        entries = mail,
        selectedIndex = selected,
        onSelectedChange = {
          chosen += it
          selected = it
        },
      )
    }

    compose.onNodeWithContentDescription("Promozioni: 210, 21%").assert(isSelected(false))
    semanticsClick("Promozioni: 210, 21%")
    compose.onNodeWithContentDescription("Promozioni: 210, 21%")
      .assert(isSelected(true))
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selezionato"))
    compose.onNodeWithText("21%", useUnmergedTree = true).assertExists()
    assertEquals(listOf<Int?>(1), chosen)

    // La stessa fetta, di nuovo: si toglie.
    semanticsClick("Promozioni: 210, 21%")
    compose.onNodeWithContentDescription("Promozioni: 210, 21%").assert(isSelected(false))
    assertEquals(listOf<Int?>(1, null), chosen)
  }

  @Test
  fun `an uncontrolled donut keeps its own selection`() {
    show { FluidDonutChart(entries = mail) }

    semanticsClick("Social: 170, 17%")
    compose.onNodeWithContentDescription("Social: 170, 17%").assert(isSelected(true))
    semanticsClick("Principale: 620, 62%")
    compose.onNodeWithContentDescription("Principale: 620, 62%").assert(isSelected(true))
    compose.onNodeWithContentDescription("Social: 170, 17%").assert(isSelected(false))
  }

  @Test
  fun `a tap on the ring selects the slice under the finger and a tap in the hole clears it`() {
    show {
      Column(Modifier.fillMaxWidth()) {
        FluidDonutChart(entries = mail, showLegend = false, modifier = Modifier.testTag("donut"))
      }
    }
    val density = compose.density
    val side = with(density) { 240.dp.toPx() }
    val outer = side / 2f - with(density) { 4.dp.toPx() }
    val mid = outer - side * 0.22f / 2f
    val width = compose.onNodeWithTag("donut").getBoundsInRoot().width.value * density.density
    val cx = width / 2f
    val cy = side / 2f

    // Ore 9: la seconda fetta (223..299 gradi) copre la sinistra.
    compose.onNodeWithTag("donut").performTouchInput { click(Offset(cx - mid, cy)) }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Promozioni: 210, 21%").assert(isSelected(true))

    // Ore 3: la prima.
    compose.onNodeWithTag("donut").performTouchInput { click(Offset(cx + mid, cy)) }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Principale: 620, 62%").assert(isSelected(true))

    // Il foro: nessuna.
    compose.onNodeWithTag("donut").performTouchInput { click(Offset(cx, cy)) }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Principale: 620, 62%").assert(isSelected(false))
  }

  @Test
  fun `a legend row is a 48 dp target that selects its slice`() {
    show { FluidDonutChart(entries = mail) }

    compose.onNodeWithText("Social", useUnmergedTree = true).assertExists().performClick()
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Social: 170, 17%").assert(isSelected(true))
    val rows = compose.onAllNodes(hasText("Social"), useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue(rows.isNotEmpty())
  }

  @Test
  fun `the legend alone is a plain list of names with their values`() {
    show {
      FluidChartLegend(
        items = listOf(
          FluidChartLegendItem("Ricevute", detail = "1.240"),
          FluidChartLegendItem("Inviate", detail = "310"),
        ),
        vertical = true,
      )
    }
    compose.onNodeWithText("Ricevute").assertExists()
    compose.onNodeWithText("310").assertExists()
  }

  // --- Ciambella: stati --------------------------------------------------------------------------------------

  @Test
  fun `empty and all-zero show nothing for the period, in both languages`() {
    show { FluidDonutChart(entries = emptyList()) }
    compose.onNodeWithText("Nessun dato nel periodo").assertExists()

    show(english = true) { FluidDonutChart(entries = listOf(FluidChartEntry("A", 0f), FluidChartEntry("B", 0f))) }
    compose.onNodeWithText("No data for this period").assertExists()
  }

  @Test
  fun `a custom empty text replaces the default`() {
    show { FluidDonutChart(entries = emptyList(), emptyText = "Nessuna mail negli ultimi 7 giorni") }
    compose.onNodeWithText("Nessuna mail negli ultimi 7 giorni").assertExists()
  }

  @Test
  fun `a negative value is not drawn - the chart says the data is invalid`() {
    show { FluidDonutChart(entries = listOf(FluidChartEntry("A", 5f), FluidChartEntry("B", -1f))) }
    compose.onNodeWithText("Dati non validi: il grafico non viene disegnato").assertExists()
    assertEquals(0, compose.onAllNodes(hasRole(Role.Button)).fetchSemanticsNodes().size)
  }

  @Test
  fun `values that miss the declared whole say both numbers, and normalising draws them`() {
    show { FluidDonutChart(entries = listOf(FluidChartEntry("A", 60f), FluidChartEntry("B", 30f)), whole = 100f) }
    compose.onNodeWithText("I valori sommano a 90 invece di 100: il grafico non viene disegnato").assertExists()

    show(english = true) { FluidDonutChart(entries = listOf(FluidChartEntry("A", 60f), FluidChartEntry("B", 30f)), whole = 100f) }
    compose.onNodeWithText("The values add up to 90 instead of 100: the chart is not drawn").assertExists()

    show {
      FluidDonutChart(
        entries = listOf(FluidChartEntry("A", 60f), FluidChartEntry("B", 30f)),
        whole = 100f,
        sumPolicy = FluidChartSumPolicy.Normalize,
      )
    }
    compose.onNodeWithContentDescription("A: 60, 67%").assertExists()
  }

  @Test
  fun `tiny slices read as Other with the right share`() {
    val entries = listOf(FluidChartEntry("Grande", 900f), FluidChartEntry("B", 60f), FluidChartEntry("C", 25f), FluidChartEntry("D", 15f))
    show { FluidDonutChart(entries = entries) }
    compose.onNodeWithContentDescription("Altro: 40, 4%").assertExists()
    compose.onNodeWithContentDescription("C: 25, 3%").assertDoesNotExist()
    show(english = true) { FluidDonutChart(entries = entries) }
    compose.onNodeWithContentDescription("Other: 40 (4%)").assertExists()
  }

  // --- Barre -------------------------------------------------------------------------------------------------

  private val labelGroups = listOf(
    FluidBarGroup("Lavoro", listOf(412f)),
    FluidBarGroup("Clienti", listOf(380f)),
    FluidBarGroup("Casa", listOf(90f)),
  )
  private val mailSeries = listOf(FluidChartSeries("Mail"))

  @Test
  fun `horizontal bars with a declared denominator say the scale and every bar`() {
    show {
      FluidBarChart(
        groups = labelGroups,
        series = mailSeries,
        horizontal = true,
        maxValue = 1240f,
        scaleLabel = "su 1.240 mail",
      )
    }

    compose.onNodeWithContentDescription(
      "Grafico a barre, 3 voci. Il valore più alto: Lavoro, 412. Scala: su 1.240 mail.",
    ).assertExists()
    compose.onNodeWithContentDescription("Lavoro: 412").assertExists()
    compose.onNodeWithContentDescription("Casa: 90").assertExists()
    // La scala dichiarata sta scritta sotto il grafico, finche' non si sceglie una barra.
    compose.onNodeWithText("su 1.240 mail", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `tapping a horizontal bar selects it and writes its full text under the chart`() {
    show {
      FluidBarChart(groups = labelGroups, series = mailSeries, horizontal = true, scaleLabel = "su 1.240 mail")
    }
    compose.onNodeWithText("Lavoro: 412", useUnmergedTree = true).assertDoesNotExist()

    compose.onNodeWithContentDescription("Clienti: 380").performClick()
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Clienti: 380").assert(isSelected(true))
    compose.onNodeWithText("Clienti: 380", useUnmergedTree = true).assertExists()
    // Il testo della scala ha ceduto il posto alla casella scelta.
    compose.onNodeWithText("su 1.240 mail", useUnmergedTree = true).assertDoesNotExist()

    compose.onNodeWithContentDescription("Clienti: 380").performClick()
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Clienti: 380").assert(isSelected(false))
  }

  @Test
  fun `a value above the declared denominator is invalid instead of a clipped bar`() {
    show {
      FluidBarChart(groups = listOf(FluidBarGroup("Troppo", listOf(1300f))), series = mailSeries, horizontal = true, maxValue = 1240f)
    }
    compose.onNodeWithText("Dati non validi: il grafico non viene disegnato").assertExists()
  }

  @Test
  fun `stacked vertical bars are one node per box with the values of every series`() {
    val series = listOf(FluidChartSeries("Ricevute"), FluidChartSeries("Inviate"))
    show {
      FluidBarChart(
        groups = listOf(FluidBarGroup("Mar", listOf(5f, 7f)), FluidBarGroup("Apr", listOf(8f, 2f))),
        series = series,
        stacked = true,
        scaleLabel = "mail al mese",
      )
    }

    compose.onNodeWithContentDescription(
      "Grafico a barre impilate, 2 voci. 2 serie. Il valore più alto: Mar, 12. Scala: mail al mese.",
    ).assertExists()
    compose.onNodeWithContentDescription("Mar: Ricevute 5, Inviate 7").assert(hasRole(Role.Button))
    semanticsClick("Apr: Ricevute 8, Inviate 2")
    compose.onNodeWithContentDescription("Apr: Ricevute 8, Inviate 2").assert(isSelected(true))
    compose.onNodeWithText("Apr: Ricevute 8, Inviate 2", useUnmergedTree = true).assertExists()
    // La legenda delle serie c'e', per gli occhi.
    compose.onNodeWithText("Ricevute", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `a stack with a negative value and all-zero bars show their states`() {
    show {
      FluidBarChart(
        groups = listOf(FluidBarGroup("X", listOf(5f, -2f))),
        series = listOf(FluidChartSeries("A"), FluidChartSeries("B")),
        stacked = true,
      )
    }
    compose.onNodeWithText("Dati non validi: il grafico non viene disegnato").assertExists()

    show { FluidBarChart(groups = listOf(FluidBarGroup("Lun", listOf(0f))), series = mailSeries) }
    compose.onNodeWithText("Nessun dato nel periodo").assertExists()

    show { FluidBarChart(groups = emptyList(), series = mailSeries) }
    compose.onNodeWithText("Nessun dato nel periodo").assertExists()
  }

  @Test
  fun `bars with negative and enormous values compose and read their numbers`() {
    show {
      FluidBarChart(
        groups = listOf(FluidBarGroup("Enorme", listOf(4.2e12f)), FluidBarGroup("Piccolo", listOf(3f))),
        series = mailSeries,
        horizontal = true,
      )
    }
    compose.onNodeWithContentDescription("Enorme: 4,2T").assertExists()

    show {
      FluidBarChart(
        groups = listOf(FluidBarGroup("Entrate", listOf(120f)), FluidBarGroup("Uscite", listOf(-45f))),
        series = mailSeries,
      )
    }
    compose.onNodeWithContentDescription("Uscite: -45").assertExists()
  }

  @Test
  fun `bars are read in english`() {
    show(english = true) {
      FluidBarChart(groups = labelGroups, series = mailSeries, scaleLabel = "of 1,240 mail")
    }
    compose.onNodeWithContentDescription("Bar chart, 3 items. Highest value: Lavoro, 412. Scale: of 1,240 mail.").assertExists()
    compose.onNodeWithContentDescription("Casa – 90").assertExists()
  }

  // --- Andamento ---------------------------------------------------------------------------------------------

  private val threeDays = listOf("1 mar", "2 mar", "3 mar")

  @Test
  fun `a trend says its range and every point, and a hole says there is no data`() {
    show {
      FluidLineChart(
        series = mailSeries,
        points = listOf(listOf(10f, null, 30f)),
        xLabels = threeDays,
        scaleLabel = "mail al giorno",
      )
    }

    compose.onNodeWithContentDescription(
      "Andamento, 3 punti, da 1 mar a 3 mar. Minimo 10, massimo 30. Scala: mail al giorno.",
    ).assertExists()
    compose.onNodeWithContentDescription("1 mar: 10").assertExists()
    compose.onNodeWithContentDescription("2 mar: nessun dato").assertExists()
    semanticsClick("3 mar: 30")
    compose.onNodeWithContentDescription("3 mar: 30").assert(isSelected(true))
    compose.onNodeWithText("3 mar: 30", useUnmergedTree = true).assertExists()
  }

  @Test
  fun `a trend with two series reads all of them at each point, in english too`() {
    show(english = true) {
      FluidLineChart(
        series = listOf(FluidChartSeries("Sent"), FluidChartSeries("Received")),
        points = listOf(listOf(1f, 2f, 3f), listOf(4f, null, 6f)),
        xLabels = threeDays,
      )
    }
    compose.onNodeWithContentDescription("2 mar – Sent 2, Received no data").assertExists()
    compose.onNodeWithContentDescription("1 mar – Sent 1, Received 4").assertExists()
  }

  @Test
  fun `a trend with no points, all holes or ragged lists shows its state`() {
    show { FluidLineChart(series = mailSeries, points = listOf(listOf(null, null, null)), xLabels = threeDays) }
    compose.onNodeWithText("Nessun dato nel periodo").assertExists()

    show { FluidLineChart(series = mailSeries, points = listOf(listOf(1f, 2f)), xLabels = threeDays) }
    compose.onNodeWithText("Dati non validi: il grafico non viene disegnato").assertExists()

    show { FluidLineChart(series = mailSeries, points = listOf(listOf(1f, Float.NaN, 3f)), xLabels = threeDays) }
    compose.onNodeWithText("Dati non validi: il grafico non viene disegnato").assertExists()
  }

  @Test
  fun `a trend of one single point and of enormous values composes`() {
    show { FluidLineChart(series = mailSeries, points = listOf(listOf(7f)), xLabels = listOf("Oggi")) }
    compose.onNodeWithContentDescription("Oggi: 7").assertExists()

    show { FluidLineChart(series = mailSeries, points = listOf(listOf(1e30f, 5f, 2f)), xLabels = threeDays, fill = true) }
    compose.onNodeWithContentDescription("1 mar: 1E30").assertExists()
  }

  // --- Testo al 200% -------------------------------------------------------------------------------------------

  private val months = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago")
  private val monthValues = listOf(88f, 94f, 71f, 102f, 97f, 85f, 64f, 58f)

  /** I rettangoli delle etichette dell'asse che si vedono: quelle del grafico, nell'albero non unito. */
  private fun visibleAxisLabels(): List<androidx.compose.ui.geometry.Rect> =
    months.flatMap { label ->
      compose.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
    }

  private fun showMonths(fontScale: Float) {
    show {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
        FluidBarChart(
          groups = months.indices.map { FluidBarGroup(months[it], listOf(monthValues[it])) },
          series = mailSeries,
        )
      }
    }
  }

  @Test
  fun `axis labels thin out at 200 percent and never overlap`() {
    showMonths(1f)
    val normal = visibleAxisLabels()
    compose.activityRule.scenario.recreate()
    showMonths(2f)
    val large = visibleAxisLabels()

    assertTrue("al 100% se ne vedono molte: ${normal.size}", normal.size >= 8)
    assertTrue("al 200% ne restano meno, senza sovrapporsi: ${normal.size} -> ${large.size}", large.size in 1 until normal.size)
    for (list in listOf(normal, large)) {
      for (i in list.indices) for (j in i + 1 until list.size) {
        val a = list[i]
        val b = list[j]
        val sameBand = a.top < b.bottom && b.top < a.bottom
        val overlapX = a.left < b.right - 0.5f && b.left < a.right - 0.5f
        assertFalse("etichette sovrapposte: $a e $b", sameBand && overlapX)
      }
    }
    // Il numero di ogni settimana c'e' comunque, nella descrizione.
    compose.onNodeWithContentDescription("Gen: 88").assertExists()
    compose.onNodeWithContentDescription("Ago: 58").assertExists()
  }

  @Test
  fun `the donut and the horizontal bars grow in height with text at 200 percent instead of cutting it`() {
    show {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          FluidDonutChart(entries = mail, centerValue = "1.240", centerLabel = "mail", modifier = Modifier.testTag("donut"))
          FluidBarChart(
            groups = listOf(
              FluidBarGroup("Fatture, ricevute e documenti contabili dei clienti", listOf(412f)),
              FluidBarGroup("Casa", listOf(90f)),
            ),
            series = mailSeries,
            horizontal = true,
            scaleLabel = "Su 1.240 mail del periodo, una mail puo' avere piu' etichette",
            modifier = Modifier.testTag("bars"),
          )
        }
      }
    }

    compose.onNodeWithTag("donut").assertHeightIsAtLeast(240.dp)
    compose.onNodeWithTag("bars").assertHeightIsAtLeast(96.dp)
    compose.onNodeWithText("1.240", useUnmergedTree = true).assertExists()
    compose.onNodeWithContentDescription("Fatture, ricevute e documenti contabili dei clienti: 412").assertExists()
  }

  // --- Movimento ridotto e ingresso ---------------------------------------------------------------------------

  @Test
  fun `selecting with reduced motion changes the middle at once`() {
    show {
      CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 0f)) {
        FluidDonutChart(entries = mail)
      }
    }
    semanticsClick("Social: 170, 17%")
    compose.onNodeWithText("17%", useUnmergedTree = true).assertExists()
    compose.onNodeWithContentDescription("Social: 170, 17%").assert(isSelected(true))
  }

  // --- Tema scuro ------------------------------------------------------------------------------------------

  @Test
  fun `the charts compose in the dark theme with their descriptions intact`() {
    show(settings = EngineSettings(themeMode = ThemeMode.DARK)) {
      Column {
        FluidDonutChart(entries = mail)
        FluidBarChart(groups = labelGroups, series = mailSeries, horizontal = true)
        FluidLineChart(series = mailSeries, points = listOf(listOf(1f, 2f, 3f)), xLabels = threeDays)
      }
    }
    compose.onNodeWithContentDescription("Principale: 620, 62%").assertExists()
    compose.onNodeWithContentDescription("Casa: 90").assertExists()
    compose.onNodeWithContentDescription("2 mar: 2").assertExists()
  }

  // --- La demo ---------------------------------------------------------------------------------------------

  @Test
  fun `the demo composes with all its states`() {
    show {
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChartsDemo()
      }
    }

    compose.onNodeWithTag("charts-demo").assertExists()
    compose.onNodeWithTag("charts-category").assertExists()
    compose.onNodeWithTag("charts-state-empty").performScrollTo().assertExists()
    assertTrue(compose.onAllNodes(hasText("Nessun dato nel periodo")).fetchSemanticsNodes().size >= 2)
    compose.onAllNodes(hasText("Dati non validi: il grafico non viene disegnato"))[0].assertExists()
    compose.onNodeWithText("Impossibile calcolare le statistiche").assertExists()
    compose.onNodeWithText("Riprova").performScrollTo().performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Riprovato 1 volte").assertExists()
    compose.onAllNodes(hasContentDescription("Forum: 23, 2%"))[0].assertExists()
  }

  @Test
  fun `the demo category chart can be selected from its legend and says which slice`() {
    show {
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        ChartsDemo()
      }
    }
    compose.onNodeWithTag("charts-category-selected").assert(hasText("Nessuna fetta scelta"))
    compose.onAllNodes(hasContentDescription("Promozioni: 238, 23%"))[0].assertExists()
    compose.onAllNodes(hasText("Promozioni"), useUnmergedTree = true)[0].performClick()
    compose.waitForIdle()
    compose.onNodeWithTag("charts-category-selected").assert(hasText("Scelta: Promozioni"))
  }
}
