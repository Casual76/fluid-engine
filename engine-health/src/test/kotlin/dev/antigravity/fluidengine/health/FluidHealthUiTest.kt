package dev.antigravity.fluidengine.health

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * F8.26 · I pezzi d'interfaccia della salute (Robolectric): gli anelli (che TalkBack legge, e che col movimento ridotto
 * sono gia' al loro posto), le card che dicono la fonte e «Nessun dato» invece di «0», la settimana con il giorno vuoto,
 * e le righe dei permessi negati e parziali col tocco che chiede solo la misura toccata.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidHealthUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val sounds = FluidHealthSource("dev.pampa.sounds", "Pampa Sounds", FluidHealthDeviceKind.Phone)
  private val watch = FluidHealthSource("com.example.watch", "Pixel Watch", FluidHealthDeviceKind.Watch)

  private fun show(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(EngineSettings()) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default) {
          content()
        }
      }
    }
    compose.mainClock.advanceTimeBy(32)
    compose.waitForIdle()
  }

  private val rings = listOf(
    FluidHealthRing("Passi", 7412.0, 10_000.0, "7.412", "10.000", Color(0xFFB45309)),
    FluidHealthRing("Minuti attivi", 38.0, 45.0, "38", "45", Color(0xFF00796B)),
    FluidHealthRing("Acqua", 2.4, 2.0, "2,4 l", "2 l", Color(0xFF1565C0)),
  )

  @Test
  fun `TalkBack legge ogni anello col valore, l'obiettivo e la percentuale, anche oltre il cento`() {
    show { FluidHealthRings(rings) }
    val text = compose.onNodeWithTag(FluidHealthTags.Rings).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
    assertTrue(text, text.startsWith("Passi, 7.412 su 10.000, 74"))
    assertTrue(text, text.contains("Acqua, 2,4 l su 2 l, 120"))
  }

  @Test
  fun `col movimento ridotto gli anelli sono gia' al loro posto al primo fotogramma, senza la molla`() {
    val drawn = ArrayList<List<Float>>()
    show(reduced = true) {
      CompositionLocalProvider(LocalFluidHealthRingsProbe provides { drawn.add(it) }) { FluidHealthRings(rings) }
    }
    drawOnce()
    assertTrue("disegnato", drawn.isNotEmpty())
    val last = drawn.last()
    assertEquals(0.7412f, last[0], 1e-4f)
    assertEquals(38f / 45f, last[1], 1e-4f)
    assertEquals("oltre l'obiettivo l'arco e' pieno, non fa un secondo giro", 1f, last[2], 0f)
  }

  @Test
  fun `senza movimento ridotto l'arco cresce con la molla e arriva, poi resta fermo`() {
    val drawn = ArrayList<List<Float>>()
    show {
      CompositionLocalProvider(LocalFluidHealthRingsProbe provides { drawn.add(it) }) { FluidHealthRings(rings) }
    }
    drawOnce()
    assertTrue("al primo fotogramma l'arco e' ancora indietro: ${drawn.lastOrNull()}", drawn.last()[0] < 0.5f)
    repeat(150) { compose.mainClock.advanceTimeBy(16) }
    compose.waitForIdle()
    drawOnce()
    assertEquals(0.7412f, drawn.last()[0], 2e-3f)
    val arrived = drawn.last()
    repeat(30) { compose.mainClock.advanceTimeBy(16) }
    compose.waitForIdle()
    drawOnce()
    assertEquals("fermo", arrived, drawn.last())
  }

  /** Disegna la finestra una volta (in Robolectric col tempo fermo il disegno non parte da solo). */
  private fun drawOnce() {
    compose.runOnUiThread {
      val view = compose.activity.window.decorView
      val bitmap = android.graphics.Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
      view.draw(android.graphics.Canvas(bitmap))
    }
  }

  @Test
  fun `la card dice Nessun dato invece di zero, e la fonte e i buchi quando ci sono`() {
    val zone = ZoneId.systemDefault()
    val gapStart = LocalDate.now().atTime(14, 10).atZone(zone).toInstant()
    val gapEnd = LocalDate.now().atTime(18, 30).atZone(zone).toInstant()
    val labels = FluidHealthLabels()
    show {
      Column {
        FluidHealthCard(FluidHealthMetric.HeartRate, value = null)
        FluidHealthCard(
          FluidHealthMetric.Steps, value = "6.000", numeric = 6000.0, unit = "passi", time = "Oggi", sources = listOf(watch),
          note = labels.gapLine(listOf(FluidHealthGap(gapStart, gapEnd)), now = gapEnd.plusSeconds(1), zone = zone, locale = Locale.ITALY),
        )
        FluidHealthCard(FluidHealthMetric.Sleep, value = "7 h e 12 min", numeric = 432.0, sources = listOf(sounds, watch))
      }
    }
    assertEquals("Battito, Nessun dato", descriptionOf(FluidHealthTags.card(FluidHealthMetric.HeartRate)))
    compose.onNodeWithText("0", useUnmergedTree = true).assertDoesNotExist()
    val steps = descriptionOf(FluidHealthTags.card(FluidHealthMetric.Steps))
    assertEquals("Passi, 6.000 passi, Oggi, Da Pixel Watch · orologio, Niente dalle 14:10", steps)
    val sleep = descriptionOf(FluidHealthTags.card(FluidHealthMetric.Sleep))
    assertTrue(sleep, sleep.endsWith("Da Pampa Sounds e Pixel Watch"))
  }

  @Test
  fun `la settimana ha il trattino nel giorno vuoto e TalkBack lo dice`() {
    val first = LocalDate.of(2026, 11, 16) // lunedi'
    val readings = listOf(
      FluidHealthReading.Value(8000.0, listOf(watch)), FluidHealthReading.NoData, FluidHealthReading.Value(0.0, listOf(watch)),
      FluidHealthReading.Value(12000.0, listOf(watch)), FluidHealthReading.Value(4000.0, listOf(watch)), FluidHealthReading.NoData,
      FluidHealthReading.Value(9000.0, listOf(watch)),
    )
    val days = FluidHealthFormat.week(readings, first, Locale.ITALY)
    assertEquals(listOf("L", "M", "M", "G", "V", "S", "D"), days.map { it.label })
    show { FluidHealthWeek(days, FluidHealthMetric.Steps, goal = 10_000.0) }
    val text = descriptionOf(FluidHealthTags.Week)
    assertTrue(text, text.startsWith("Passi: L 8.000, M Nessun dato, M 0, G 12.000"))
  }

  @Test
  fun `le righe dei permessi dicono negato, parziale e consentito, e il tocco chiede solo la misura toccata`() {
    val asked = listOf(
      FluidHealthPermission.read(FluidHealthMetric.Steps),
      FluidHealthPermission.read(FluidHealthMetric.Sleep), FluidHealthPermission.write(FluidHealthMetric.Sleep),
      FluidHealthPermission.read(FluidHealthMetric.HeartRate),
    )
    val granted = setOf(FluidHealthPermission.read(FluidHealthMetric.Steps), FluidHealthPermission.read(FluidHealthMetric.Sleep))
    val requests = ArrayList<Set<FluidHealthPermission>>()
    show { FluidHealthPermissionRows(asked, granted, onRequest = { requests.add(it) }) }
    compose.onNodeWithText("Consentito").assertIsDisplayed()
    compose.onNodeWithText("Solo lettura").assertIsDisplayed()
    compose.onNodeWithText("Non consentito").assertIsDisplayed()
    compose.onNodeWithText("Lettura e scrittura").assertIsDisplayed()
    compose.onNodeWithTag(FluidHealthTags.permission(FluidHealthMetric.Sleep)).performClick()
    compose.mainClock.advanceTimeBy(100)
    assertEquals(listOf(setOf(FluidHealthPermission.write(FluidHealthMetric.Sleep))), requests)
    compose.onNodeWithTag(FluidHealthTags.permission(FluidHealthMetric.Steps)).performClick()
    compose.mainClock.advanceTimeBy(100)
    assertEquals("la riga consentita non chiede niente", 1, requests.size)
  }

  @Test
  fun `senza Health Connect le righe lo dicono e non chiedono`() {
    val requests = ArrayList<Set<FluidHealthPermission>>()
    show {
      FluidHealthPermissionRows(
        listOf(FluidHealthPermission.read(FluidHealthMetric.Steps)), emptySet(), onRequest = { requests.add(it) },
        availability = FluidHealthAvailability.Unavailable,
      )
    }
    compose.onNodeWithText("Health Connect non c'e' su questo telefono").assertIsDisplayed()
    compose.onNodeWithTag(FluidHealthTags.permission(FluidHealthMetric.Steps)).performClick()
    assertTrue(requests.isEmpty())
  }

  @Test
  fun `dopo due rifiuti il richiedente apre Health Connect invece del foglio`() {
    var launched = 0
    var settings = 0
    val requester = FluidHealthPermissionRequester(launch = { launched += 1 }, openHealthConnect = { settings += 1 })
    val steps = setOf(FluidHealthPermission.read(FluidHealthMetric.Steps))
    requester.request(steps)
    requester.onResult(emptySet())
    requester.request(steps)
    requester.onResult(emptySet())
    assertTrue(requester.needsSettings(FluidHealthMetric.Steps))
    requester.request(steps)
    assertEquals(2, launched)
    assertEquals(1, settings)
    // Un permesso dato azzera niente ma non conta come rifiuto.
    requester.request(setOf(FluidHealthPermission.read(FluidHealthMetric.Sleep)))
    requester.onResult(setOf("android.permission.health.READ_SLEEP"))
    assertFalse(requester.needsSettings(FluidHealthMetric.Sleep))
  }

  @Test
  fun `la frase dei buchi dice i fatti, con l'ora di fine o fino ad adesso`() {
    val zone = ZoneId.of("Europe/Rome")
    val a = Instant.parse("2026-11-20T13:10:00Z")
    val b = Instant.parse("2026-11-20T17:30:00Z")
    val labels = FluidHealthLabels()
    assertEquals("Niente dalle 14:10 alle 18:30", labels.gapLine(listOf(FluidHealthGap(a, b)), now = b.plusSeconds(3600), zone = zone, locale = Locale.ITALY))
    assertEquals("Niente dalle 14:10", labels.gapLine(listOf(FluidHealthGap(a, b)), now = b, zone = zone, locale = Locale.ITALY))
    assertEquals("Da Pampa Sounds · telefono", labels.sourceLine(listOf(sounds), Locale.ITALY))
    assertEquals("Da 3 fonti", labels.sourceLine(listOf(sounds, watch, FluidHealthSource("x")), Locale.ITALY))
  }

  private fun descriptionOf(tag: String): String =
    compose.onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].first()
}
