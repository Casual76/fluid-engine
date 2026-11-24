package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidRecurrenceDay
import dev.antigravity.fluidengine.foundation.FluidRecurrenceFrequency
import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidCalendar
import dev.antigravity.fluidengine.ui.fluid.FluidCalendarMark
import dev.antigravity.fluidengine.ui.fluid.FluidCalendarMarks
import dev.antigravity.fluidengine.ui.fluid.FluidCalendarState
import dev.antigravity.fluidengine.ui.fluid.FluidDateCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidDateSelection
import dev.antigravity.fluidengine.ui.fluid.FluidDurationCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRecurrenceSheet
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class DateHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/** Conta le composizioni di una funzione dell'engine per nome (i gruppi del compilatore Compose). */
@OptIn(InternalComposeTracingApi::class)
private class DayCounter : CompositionTracer {
  var days = 0
  val names = linkedMapOf<String, Int>()
  override fun isTraceInProgress(): Boolean = true
  override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
    if (info.startsWith("dev.antigravity.fluidengine.ui.fluid.CalendarDay ")) days++
    if (info.startsWith("dev.antigravity.fluidengine.ui.fluid.")) {
      val name = info.substringBefore(" (").substringAfterLast('.')
      names[name] = (names[name] ?: 0) + 1
    }
  }

  override fun traceEventEnd() = Unit
}

/**
 * F8.8 · il calendario, la capsula, la durata e il pannello «Ripeti» col dito vero (eventi del sistema con l'orologio
 * che avanza, `MaterialFinger`) e col vetro vero (verdetti degli shader accettati: il popover e il foglio sono vetro
 * dell'engine). Le date sono quelle di novembre 2026, in italiano: il mese comincia di domenica, la griglia ha sei celle
 * vuote davanti, oggi e' martedi' 24.
 *
 * A 400 dp e densita' 2 una colonna e' 57 dp (114 px) e una riga 46,9 dp (94 px): sette colonne fanno 800 px, e una pagina
 * e' larga 800 px. La griglia comincia sotto la testata (44 dp) e la fila delle lettere (20 dp).
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p8DatesUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val haptics = DateHaptics()
  private val today = LocalDate.of(2026, 11, 24)
  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private val counter = DayCounter()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    Composer.setTracer(null)
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun show(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidHaptics provides haptics,
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
          LocalFluidGlassModalHostState provides host,
        ) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(600)
    finger = MaterialFinger(view)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun drag(from: Offset, stepX: Float, stepY: Float, steps: Int, hold: Long = 0L) {
    finger.down(from)
    var at = from
    repeat(steps) {
      advance(16)
      at = Offset(at.x + stepX, at.y + stepY)
      finger.move(at)
    }
    if (hold > 0) advance(hold)
    finger.up(at)
    advance(16)
  }

  private fun tap(at: Offset) {
    finger.down(at)
    advance(32)
    finger.up(at)
    advance(16)
  }

  private fun day(description: String, substring: Boolean = false): SemanticsNode =
    compose.onNode(hasContentDescription(description, substring = substring), useUnmergedTree = false).fetchSemanticsNode()

  private fun dayExists(description: String, substring: Boolean = false): Boolean =
    compose.onAllNodes(hasContentDescription(description, substring = substring), useUnmergedTree = false)
      .fetchSemanticsNodes().isNotEmpty()

  private fun writesDuring(ms: Long): Int {
    var writes = 0
    val observer = Snapshot.registerGlobalWriteObserver { writes++ }
    advance(ms)
    observer.dispose()
    return writes
  }

  // --- Il calendario: il mese che scorre ---------------------------------------------------------------------------

  private lateinit var state: FluidCalendarState
  private var selection by mutableStateOf<FluidDateSelection>(FluidDateSelection.Single(LocalDate.of(2026, 11, 24)))
  private val marks = FluidCalendarMarks()

  private fun showCalendar(reduced: Boolean = false, withList: Boolean = false) = show(reduced) {
    val s = remember { FluidCalendarState(YearMonth.of(2026, 11)) }
    state = s
    Column(Modifier.fillMaxSize()) {
      FluidCalendar(
        selection = selection,
        onSelectionChange = { selection = it },
        modifier = Modifier.testTag("calendario"),
        state = s,
        marks = marks,
        today = today,
        locale = Locale.ITALY,
      )
      if (withList) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().nestedScroll(s.nestedScrollConnection).testTag("lista")) {
          items(60) { Text("Riga $it", Modifier.height(48.dp).padding(8.dp)) }
        }
      }
    }
  }

  /** Il punto della griglia a meta' riga [row] e alla colonna [column], in px della radice. */
  private fun cell(row: Int, column: Int): Offset {
    val b = bounds("calendario")
    val col = b.width / 7f
    val top = b.top + 64f * 2f
    return Offset(b.left + col * (column + 0.5f), top + 94f * (row + 0.5f))
  }

  @Test fun `il mese trascinato oltre la meta' si posa sul mese dopo, nitido e senza ricomporre i giorni`() {
    showCalendar()
    Composer.setTracer(counter)
    counter.days = 0
    counter.names.clear()
    val from = cell(2, 5)
    // 20 passi da 24 px: 480 px, il 60% della pagina, poi fermo e su.
    finger.down(from)
    var at = from
    repeat(20) {
      advance(16)
      at = Offset(at.x - 24f, at.y)
      finger.move(at)
    }
    assertEquals("durante il trascinamento nessun giorno si ricompone", 0, counter.days)
    assertEquals(YearMonth.of(2026, 11), state.month)
    advance(160)
    finger.up(at)
    advance(900)
    assertEquals(YearMonth.of(2026, 12), state.month)
    // All'arrivo si compone un mese solo, quello nuovo dopo (gennaio): al piu' 31 giorni e qualche giorno della riga.
    println("F8p8: al cambio di mese giorni composti ${counter.days}, funzioni ${counter.names}")
    assertTrue("giorni composti al cambio: ${counter.days} (${counter.names})", counter.days in 1..45)
    assertEquals("a riposo niente si muove", 0, writesDuring(1000))
  }

  @Test fun `trascinato poco torna indietro, lanciato poco passa, e oltre l'ultimo mese resiste`() {
    showCalendar()
    val from = cell(2, 5)
    drag(from, -16f, 0f, steps = 10, hold = 200)
    advance(900)
    assertEquals(YearMonth.of(2026, 11), state.month)
    // Un lancio da 320 px senza fermarsi: la proiezione porta oltre la meta.
    drag(from, -40f, 0f, steps = 8)
    advance(900)
    assertEquals(YearMonth.of(2026, 12), state.month)
    // Indietro due volte, a destra.
    drag(cell(2, 1), 40f, 0f, steps = 8)
    advance(900)
    drag(cell(2, 1), 40f, 0f, steps = 8)
    advance(900)
    assertEquals(YearMonth.of(2026, 10), state.month)
  }

  @Test fun `le frecce cambiano mese, e TalkBack sente i giorni coi segni`() {
    showCalendar()
    compose.runOnIdle {
      marks.setMonth(YearMonth.of(2026, 11), mapOf(LocalDate.of(2026, 11, 25) to FluidCalendarMark(3)))
    }
    advance(600)
    assertTrue(dayExists("25 novembre, 3 impegni"))
    assertTrue(dayExists("24 novembre, oggi"))
    compose.onNode(hasContentDescription("Mese successivo")).performSemanticsAction(SemanticsActions.OnClick)
    advance(900)
    assertEquals(YearMonth.of(2026, 12), state.month)
    // Il mese di prima non e' piu' nell'albero di TalkBack: i mesi vicini sono pronti ma muti.
    assertFalse(dayExists("25 novembre, 3 impegni"))
    compose.onNode(hasContentDescription("Mese precedente")).performSemanticsAction(SemanticsActions.OnClick)
    advance(900)
    assertEquals(YearMonth.of(2026, 11), state.month)
  }

  @Test fun `i segni che arrivano ricompongono solo i giorni toccati e la griglia non salta`() {
    showCalendar()
    val before = bounds("calendario")
    Composer.setTracer(counter)
    counter.days = 0
    compose.runOnIdle {
      marks.setMonth(
        YearMonth.of(2026, 11),
        mapOf(
          LocalDate.of(2026, 11, 3) to FluidCalendarMark(1),
          LocalDate.of(2026, 11, 12) to FluidCalendarMark(2),
          LocalDate.of(2026, 11, 25) to FluidCalendarMark(3),
        ),
      )
    }
    advance(600)
    assertEquals("tre segni, tre giorni", 3, counter.days)
    assertEquals(before, bounds("calendario"))
    // Il segno entra sfocato e poi si ferma: dopo l'arrivo niente scrive piu'.
    assertEquals(0, writesDuring(800))
  }

  // --- La scelta ----------------------------------------------------------------------------------------------------

  @Test fun `un giorno, poi un intervallo col primo tocco, il secondo e quello che ricomincia, poi giorni sparsi`() {
    showCalendar()
    // Novembre 2026 in Italia: il 10 sta nella riga 2 (colonna 1, martedi'), il 20 nella riga 3 (colonna 4, venerdi').
    tap(cell(2, 1))
    advance(200)
    assertEquals(FluidDateSelection.Single(LocalDate.of(2026, 11, 10)), selection)
    assertTrue(haptics.played.contains(FluidHapticEvent.Tick))

    compose.runOnIdle { selection = FluidDateSelection.Range() }
    advance(100)
    tap(cell(2, 1))
    advance(100)
    assertEquals(FluidDateSelection.Range(LocalDate.of(2026, 11, 10), null), selection)
    tap(cell(3, 4))
    advance(100)
    assertEquals(FluidDateSelection.Range(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 20)), selection)
    // I giorni in mezzo sono nella fascia: TalkBack lo dice.
    assertEquals("nell'intervallo", day("15 novembre").config.getOrNull(SemanticsProperties.StateDescription))
    tap(cell(0, 6))
    advance(100)
    assertEquals("a intervallo completo un tocco ricomincia", FluidDateSelection.Range(LocalDate.of(2026, 11, 1), null), selection)

    compose.runOnIdle { selection = FluidDateSelection.Multiple() }
    advance(100)
    tap(cell(2, 1))
    advance(100)
    drag(cell(2, 5), -40f, 0f, steps = 8)
    advance(900)
    assertEquals(YearMonth.of(2026, 12), state.month)
    // Dicembre 2026 comincia di martedi': il 3 (giovedi') sta nella riga 0, colonna 3.
    tap(cell(0, 3))
    advance(100)
    assertEquals(FluidDateSelection.Multiple(setOf(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 3))), selection)
  }

  // --- La striscia della settimana --------------------------------------------------------------------------------

  @Test fun `la lista che scorre stringe il mese nella settimana, e tirata giu' in cima lo riapre`() {
    showCalendar(withList = true)
    val full = bounds("calendario").height
    val list = bounds("lista")
    drag(Offset(list.center.x, list.center.y + 200f), 0f, -30f, steps = 22, hold = 200)
    advance(900)
    assertTrue("striscia: ${state.collapseProgress}", state.isWeekStrip)
    val strip = bounds("calendario").height
    // La testata, le lettere e una riga: 44 + 20 + 46,9 dp.
    assertEquals((44f + 20f + 46.9f) * 2f, strip, 4f)
    assertTrue(strip < full)
    // Oggi e' nella striscia, e lo scorrimento di lato ora cambia settimana.
    assertTrue(dayExists("24 novembre, oggi"))
    drag(cell(0, 5), -40f, 0f, steps = 8)
    advance(900)
    assertTrue(dayExists("1 dicembre"))
    assertEquals(YearMonth.of(2026, 12), state.month)
    // Nella striscia del 30 novembre – 6 dicembre il lunedi' 30 e' di novembre: toccato, la testata torna a novembre.
    tap(cell(0, 0))
    advance(100)
    assertEquals(FluidDateSelection.Single(LocalDate.of(2026, 11, 30)), selection)
    assertEquals(YearMonth.of(2026, 11), state.month)
    // Giu': la lista torna in cima, poi il mese si riapre.
    drag(Offset(list.center.x, list.top + 40f), 0f, 30f, steps = 30, hold = 200)
    advance(900)
    assertEquals(0f, state.collapseProgress, 0f)
    assertEquals(full, bounds("calendario").height, 1f)
    assertEquals(0, writesDuring(600))
  }

  // --- Riduci movimento ---------------------------------------------------------------------------------------------

  @Test fun `con Riduci movimento la pagina va subito al mese e la striscia si posa senza moto`() {
    showCalendar(reduced = true, withList = true)
    val from = cell(2, 5)
    drag(from, -24f, 0f, steps = 20, hold = 160)
    // Subito dopo il dito: il mese nuovo, gia' fermo.
    assertEquals(YearMonth.of(2026, 12), state.month)
    assertEquals(0, writesDuring(500))
    compose.runOnIdle { marks[LocalDate.of(2026, 12, 9)] = FluidCalendarMark(2) }
    advance(32)
    // Il segno compare fermo: nessuna messa a fuoco da animare.
    assertEquals(0, writesDuring(400))
    val list = bounds("lista")
    drag(Offset(list.center.x, list.center.y + 200f), 0f, -20f, steps = 12, hold = 200)
    advance(32)
    val p = state.collapseProgress
    assertTrue("posata, non a meta': $p", p == 0f || p == 1f)
  }

  // --- Le ruote del mese e dell'anno ------------------------------------------------------------------------------

  private fun titleNode(text: String): SemanticsNode = compose.onNode(hasText(text)).fetchSemanticsNode()

  private fun wheel(description: String): SemanticsNode =
    compose.onNode(hasContentDescription(description), useUnmergedTree = true).fetchSemanticsNode()

  @Test fun `il titolo apre le ruote di mese e anno, e un altro tocco torna alla griglia sul mese scelto`() {
    showCalendar()
    val title = titleNode("novembre 2026")
    // TalkBack: il titolo si espande, e dice cosa fa il doppio tocco.
    assertTrue(title.config.contains(SemanticsActions.Expand))
    assertEquals("Scegli mese e anno", title.config[SemanticsActions.OnClick].label)
    tap(title.boundsInRoot.center)
    advance(600)
    assertTrue(state.isMonthYearPickerShown)
    assertFalse("la griglia non c'e' piu'", dayExists("25 novembre"))
    assertFalse("le frecce del mese non ci sono", dayExists("Mese successivo"))
    assertTrue(titleNode("novembre 2026").config.contains(SemanticsActions.Collapse))
    // Il mese col dito: due voci in su (mezzo slop per prendersi il dito, poi due voci da 64 px), da novembre a gennaio.
    val month = wheel("Mese").boundsInRoot
    drag(Offset(month.center.x, month.center.y + 60f), 0f, -8f, steps = 18, hold = 160)
    advance(900)
    assertEquals(YearMonth.of(2026, 1), state.month)
    // L'anno con TalkBack: «Aumenta» sulla colonna dell'anno.
    val increase = wheel("Anno").config[SemanticsActions.CustomActions].first { it.label == "Aumenta" }
    compose.runOnUiThread { increase.action() }
    Snapshot.sendApplyNotifications()
    advance(900)
    assertEquals(YearMonth.of(2027, 1), state.month)
    assertTrue(compose.onAllNodes(hasText("gennaio 2027")).fetchSemanticsNodes().isNotEmpty())
    // Un altro tocco sul titolo: la griglia, sul mese scelto; la scelta non e' cambiata.
    tap(titleNode("gennaio 2027").boundsInRoot.center)
    advance(800)
    assertFalse(state.isMonthYearPickerShown)
    assertTrue(dayExists("15 gennaio 2027"))
    assertEquals(FluidDateSelection.Single(LocalDate.of(2026, 11, 24)), selection)
    assertEquals(0, writesDuring(800))
  }

  @Test fun `con Riduci movimento le ruote compaiono e se ne vanno di colpo, e con le ruote la lista non stringe il mese`() {
    showCalendar(reduced = true, withList = true)
    tap(titleNode("novembre 2026").boundsInRoot.center)
    advance(32)
    assertTrue(state.isMonthYearPickerShown)
    assertTrue(compose.onAllNodes(hasContentDescription("Anno"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    assertEquals("ferme subito", 0, writesDuring(400))
    // La lista che scorre non stringe niente: la griglia non c'e'.
    val list = bounds("lista")
    drag(Offset(list.center.x, list.center.y + 200f), 0f, -20f, steps = 12, hold = 200)
    advance(100)
    assertEquals(0f, state.collapseProgress, 0f)
    tap(titleNode("novembre 2026").boundsInRoot.center)
    advance(32)
    assertFalse(state.isMonthYearPickerShown)
    assertTrue(dayExists("25 novembre"))
    assertEquals(0, writesDuring(400))
  }

  // --- La capsula e il popover --------------------------------------------------------------------------------------

  @Test fun `la capsula apre il calendario in un popover e lo richiude col giorno scelto`() {
    var picked by mutableLongStateOf(LocalDate.of(2026, 11, 23).toEpochDay())
    show {
      Column(Modifier.padding(top = 120.dp, start = 24.dp)) {
        FluidDateCapsule(
          selection = FluidDateSelection.Single(LocalDate.ofEpochDay(picked)),
          onSelectionChange = { (it as? FluidDateSelection.Single)?.date?.let { d -> picked = d.toEpochDay() } },
          modifier = Modifier.testTag("capsula"),
          label = "Scadenza",
          today = today,
          locale = Locale.ITALY,
        )
      }
    }
    assertTrue(compose.onAllNodes(hasText("23 nov 2026"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    assertFalse(dayExists("26 novembre"))
    tap(bounds("capsula").center)
    advance(800)
    assertTrue("il popover mostra il calendario", dayExists("26 novembre"))
    val target = day("26 novembre").boundsInRoot.center
    tap(target)
    advance(1200)
    assertEquals(LocalDate.of(2026, 11, 26).toEpochDay(), picked)
    assertFalse("il popover si e' chiuso", dayExists("27 novembre"))
    assertTrue(compose.onAllNodes(hasText("26 nov 2026"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
  }

  @Test fun `con closeOnSelection spento il popover resta aperto dopo il giorno, come il compact di iOS`() {
    var picked by mutableLongStateOf(LocalDate.of(2026, 11, 23).toEpochDay())
    show {
      Column(Modifier.padding(top = 120.dp, start = 24.dp)) {
        FluidDateCapsule(
          selection = FluidDateSelection.Single(LocalDate.ofEpochDay(picked)),
          onSelectionChange = { (it as? FluidDateSelection.Single)?.date?.let { d -> picked = d.toEpochDay() } },
          modifier = Modifier.testTag("capsula"),
          today = today,
          locale = Locale.ITALY,
          closeOnSelection = false,
        )
      }
    }
    tap(bounds("capsula").center)
    advance(800)
    tap(day("26 novembre").boundsInRoot.center)
    advance(1200)
    assertEquals(LocalDate.of(2026, 11, 26).toEpochDay(), picked)
    assertTrue("il popover e' ancora aperto", dayExists("27 novembre"))
  }

  @Test fun `la durata a ruota nel popover scatta voce per voce`() {
    var seconds by mutableLongStateOf(45L * 60L)
    show {
      Column(Modifier.padding(top = 120.dp, start = 24.dp)) {
        FluidDurationCapsule(
          seconds = seconds,
          onSecondsChange = { seconds = it },
          modifier = Modifier.testTag("durata"),
          label = "Spegnimento",
          minuteInterval = 5,
        )
      }
    }
    tap(bounds("durata").center)
    advance(800)
    val wheel = compose.onNode(hasContentDescription("Minuti"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    haptics.played.clear()
    // Due voci da 64 px, piu' mezzo slop per prendersi il dito: 144 px a passi di 8, fermo, su.
    drag(Offset(wheel.center.x, wheel.center.y + 60f), 0f, -8f, steps = 18, hold = 160)
    advance(900)
    assertEquals("45 min + 2 voci da 5", 55L * 60L, seconds)
    val ticks = haptics.played.count { it == FluidHapticEvent.Tick || it == FluidHapticEvent.DragTick }
    assertEquals("un tic per voce: ${haptics.played}", 2, ticks)
  }

  // --- Il pannello «Ripeti» -----------------------------------------------------------------------------------------

  @Test fun `il pannello Ripeti personalizzato restituisce la regola, con la frase e le date esatte`() {
    var confirmed: FluidRecurrenceRule? = null
    var open by mutableStateOf(true)
    show {
      FluidRecurrenceSheet(
        visible = open,
        onDismissRequest = { open = false },
        onConfirm = { confirmed = it; open = false },
        start = today,
        locale = Locale.ITALY,
      )
    }
    advance(600)
    compose.onNode(hasText("Personalizza")).performSemanticsAction(SemanticsActions.OnClick)
    advance(600)
    // Martedi' 24: la settimana parte dal martedi'; si accende il giovedi'.
    compose.onNode(hasContentDescription("giovedì")).performSemanticsAction(SemanticsActions.OnClick)
    advance(800)
    assertTrue(compose.onAllNodes(hasText("Ogni mar e gio"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    assertTrue(compose.onAllNodes(hasText("gio 26 nov 2026"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    compose.onNode(hasText("Fine")).performSemanticsAction(SemanticsActions.OnClick)
    advance(600)
    assertEquals(
      FluidRecurrenceRule(
        FluidRecurrenceFrequency.Weekly,
        daysOfWeek = listOf(FluidRecurrenceDay(DayOfWeek.TUESDAY), FluidRecurrenceDay(DayOfWeek.THURSDAY)),
      ),
      confirmed,
    )
  }
}
