package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.foundation.FluidRecurrenceFrequency
import dev.antigravity.fluidengine.foundation.FluidRecurrenceRule
import dev.antigravity.fluidengine.foundation.FluidUnit
import dev.antigravity.fluidengine.ui.fluid.FluidCodeField
import dev.antigravity.fluidengine.ui.fluid.FluidCodeFieldState
import dev.antigravity.fluidengine.ui.fluid.FluidDurationCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidLiveClockProvider
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidPrivateGate
import dev.antigravity.fluidengine.ui.fluid.FluidRecurrenceSheet
import dev.antigravity.fluidengine.ui.fluid.FluidRelativeText
import dev.antigravity.fluidengine.ui.fluid.FluidSpecialFieldLabels
import dev.antigravity.fluidengine.ui.fluid.FluidTimeSource
import dev.antigravity.fluidengine.ui.fluid.FluidUnitField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidTimeSource
import dev.antigravity.fluidengine.ui.fluid.fluidPrivate
import dev.antigravity.fluidengine.ui.fluid.rememberFluidCodeFieldState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPrivateState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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

/** Un orologio finto che conta quante volte lo si legge. */
private class CountingTime(var now: Long) : FluidTimeSource {
  var reads = 0
  override fun nowMillis(): Long {
    reads++
    return now
  }
}


/**
 * Una ricreazione come quella di una rotazione: lo stato salvabile si salva, il contenuto esce dalla composizione (un
 * fotogramma vero, non solo uno scambio fra due fotogrammi) e rientra con lo stato salvato. `StateRestorationTester`
 * con l'orologio fermo non faceva uscire niente: scambiava e rimetteva i figli prima del fotogramma.
 */
private class Recreation {
  var registry by mutableStateOf(newRegistry(null))
  var shown by mutableStateOf(true)

  private fun newRegistry(saved: Map<String, List<Any?>>?) =
    SaveableStateRegistry(saved) { it is java.io.Serializable || it is android.os.Parcelable }

  @Composable
  fun Host(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { if (shown) content() }
  }

  fun recreate(frames: (Long) -> Unit) {
    val saved = registry.performSave()
    shown = false
    frames(64)
    registry = newRegistry(saved)
    shown = true
    frames(64)
  }
}

/** Un ciclo di vita mosso a mano, per mandare la pagina in secondo piano senza toccare l'attività. */
private class ManualLifecycle : LifecycleOwner {
  val registry = LifecycleRegistry.createUnsafe(this)
  override val lifecycle: Lifecycle get() = registry
}

/**
 * R2-F5a · le correzioni dei campi e delle date della review R2 (`muse-f8-prime.md`), in Robolectric col vetro vero:
 * il codice riscritto durante la scossa, il PIN senza il tipo «codice SMS», il cancello che lancia, i tempi vivi in
 * secondo piano, la valuta che non si converte, i mesi e le durate negative di `FluidFormat`, il pannello «Ripeti» che
 * non tocca la regola e resiste alla ricreazione, la capsula di una durata negativa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class R2F5aFieldsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val today = LocalDate.of(2026, 11, 24)

  @Before fun start() {
    Locale.setDefault(Locale.ITALY)
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long, step: Long = 16) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(step)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(step))
      Snapshot.sendApplyNotifications()
      left -= step
    }
  }

  @Composable
  private fun Harness(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(
        LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 1f),
        LocalFluidGlassModalHostState provides host,
      ) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  private fun show(content: @Composable () -> Unit) {
    compose.setContent { Harness(content) }
    advance(300)
  }

  private fun exists(matcher: androidx.compose.ui.test.SemanticsMatcher): Boolean =
    compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  // --- 28. Il codice sbagliato e chi riscrive subito ---------------------------------------------------------------

  @Test fun `chi riscrive durante la scossa del codice sbagliato non perde le cifre nuove`() {
    lateinit var state: FluidCodeFieldState
    show {
      state = rememberFluidCodeFieldState(6)
      FluidCodeField(state, onComplete = {}, pasteButton = false)
    }
    state.set("123456")
    advance(100)
    state.fail()
    advance(100)
    // A meta' scossa la persona cancella e scrive due cifre nuove.
    state.set("98")
    advance(1_000)
    assertEquals("98", state.code)
    // Senza riscrivere, le caselle si svuotano come prima.
    state.set("123456")
    advance(100)
    state.fail()
    advance(1_000)
    assertEquals("", state.code)
  }

  // --- Minore: il PIN non e' un codice SMS ----------------------------------------------------------------------------

  /** I suggerimenti di autocompilazione del campo [tag] («smsOTPCode», «password»…), letti dal suo `ContentType`. */
  private fun autofillHintsOf(tag: String): Set<String> {
    val nodes = compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes()
    val node = nodes.single { n ->
      generateSequence(n) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
    }
    val type = node.config.firstOrNull { it.key.name == "ContentType" }?.value ?: return emptySet()
    val getter = type.javaClass.methods.firstOrNull { it.name == "getAndroidAutofillHints" } ?: return setOf(type.toString())
    // La classe di Compose non e' pubblica: il metodo si apre prima di chiamarlo.
    getter.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return (getter.invoke(type) as Set<String>)
  }

  @Test fun `il campo del PIN non chiede all'autocompilazione un codice SMS`() {
    show {
      Column {
        FluidCodeField(rememberFluidCodeFieldState(6), onComplete = {}, pasteButton = false, modifier = Modifier.testTag("otp"))
        FluidCodeField(rememberFluidCodeFieldState(6), onComplete = {}, pasteButton = false, secret = true, modifier = Modifier.testTag("pin"))
      }
    }
    // View.AUTOFILL_HINT_SMS_OTP
    assertTrue("il codice monouso resta un codice SMS: ${autofillHintsOf("otp")}", "smsOTPCode" in autofillHintsOf("otp"))
    assertFalse("il PIN non e' un codice SMS: ${autofillHintsOf("pin")}", "smsOTPCode" in autofillHintsOf("pin"))
  }

  // --- Minore: il cancello biometrico che lancia ----------------------------------------------------------------------

  @Test fun `un cancello che lancia non fa cadere l'app e il valore resta coperto`() {
    val labels = FluidSpecialFieldLabels()
    show {
      val state = rememberFluidPrivateState()
      Text(
        "1.234,56 €",
        Modifier.testTag("saldo").fluidPrivate(state, gate = FluidPrivateGate { throw IllegalStateException("biometria guasta") }),
      )
    }
    compose.onNodeWithTag("saldo").performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
    val node = compose.onNodeWithTag("saldo").fetchSemanticsNode()
    val said = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    assertTrue(said, said.startsWith(labels.hidden))
  }

  // --- Minore: i tempi vivi in secondo piano ----------------------------------------------------------------------

  @Test fun `in secondo piano i tempi vivi non si svegliano, e al ritorno si aggiornano`() {
    val time = CountingTime(1_800_000_000_000L)
    val owner = ManualLifecycle().apply { registry.currentState = Lifecycle.State.RESUMED }
    val then = Instant.ofEpochMilli(time.now - 12 * 60_000L)
    show {
      CompositionLocalProvider(LocalFluidTimeSource provides time, LocalLifecycleOwner provides owner) {
        Column {
          FluidRelativeText(then, Modifier.testTag("solo"), locale = Locale.ITALY)
          FluidLiveClockProvider {
            FluidRelativeText(then, Modifier.testTag("orologio"), locale = Locale.ITALY)
          }
        }
      }
    }
    advance(1_000)
    assertTrue("il testo vivo di partenza", exists(hasText("12 min fa")))
    owner.registry.currentState = Lifecycle.State.CREATED
    advance(100)
    time.reads = 0
    repeat(300) {
      time.now += 1_000L
      advance(1_000, step = 1_000)
    }
    assertEquals("letture dell'orologio in secondo piano", 0, time.reads)
    owner.registry.currentState = Lifecycle.State.RESUMED
    advance(2_000)
    assertEquals(2, compose.onAllNodes(hasText("17 min fa"), useUnmergedTree = true).fetchSemanticsNodes().size)
  }

  // --- Minore: la valuta senza cambio -----------------------------------------------------------------------------

  @Test fun `una valuta senza cambio non si offre se il valore andrebbe convertito`() {
    val eur = FluidUnit.currency("EUR")
    val usd = FluidUnit.currency("USD")
    var unit by mutableStateOf(eur)
    var value by mutableStateOf<BigDecimal?>(BigDecimal("12"))
    val picker = FluidSpecialFieldLabels().unitPicker
    show {
      FluidUnitField(value = value, onValueChange = { value = it }, unit = unit, units = listOf(eur, usd),
        onUnitChange = { unit = it }, locale = Locale.ITALY)
    }
    // 12 € non diventano «12 $» in silenzio: la capsula non apre la scelta.
    assertTrue(!exists(hasContentDescription(picker) and hasClickAction()))
    // Con il campo vuoto non c'e' niente da convertire: la scelta c'e'.
    value = null
    advance(300)
    assertTrue(exists(hasContentDescription(picker) and hasClickAction()))
  }

  // --- Minore: i mesi oltre l'undicesimo e le durate negative ---------------------------------------------------------

  @Test fun `360 giorni fa sono 11 mesi, e un millisecondo negativo e' zero secondi`() {
    val now = Instant.ofEpochMilli(1_800_000_000_000L)
    val day = 86_400_000L
    fun ago(days: Long) = FluidFormat.relative(now.minusMillis(days * day), now, Locale.ITALY)
    assertEquals("11 mesi fa", ago(330))
    assertEquals("11 mesi fa", ago(360))
    assertEquals("11 mesi fa", ago(364))
    assertEquals("1 anno fa", ago(365))
    // Il testo vivo a 340 giorni cambia a 365 (l'anno), non a 360 (dove resterebbe «11 mesi»).
    assertEquals(25 * day, FluidFormat.relativeNextChangeMillis(now.minusMillis(340 * day), now))
    assertEquals(FluidFormat.duration(Duration.ofMillis(1), Locale.ITALY), FluidFormat.duration(Duration.ofMillis(-1), Locale.ITALY))
    assertEquals("-" + FluidFormat.duration(Duration.ofSeconds(1), Locale.ITALY), FluidFormat.duration(Duration.ofMillis(-1_500), Locale.ITALY))
  }

  // --- 39. Il pannello «Ripeti» ------------------------------------------------------------------------------------

  @Test fun `il pannello Ripeti aperto e chiuso con Fine restituisce la regola com'era`() {
    val rich = FluidRecurrenceRule(FluidRecurrenceFrequency.Monthly, daysOfMonth = listOf(15))
    var confirmed: FluidRecurrenceRule? = null
    var done = false
    var open by mutableStateOf(true)
    show {
      FluidRecurrenceSheet(
        visible = open,
        onDismissRequest = { open = false },
        onConfirm = { confirmed = it; done = true; open = false },
        start = today,
        initialRule = rich,
        locale = Locale.ITALY,
      )
    }
    advance(600)
    compose.onNode(hasText("Fine") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    advance(600)
    assertTrue(done)
    assertEquals(rich, confirmed)
  }

  @Test fun `la bozza del pannello Ripeti resiste alla ricreazione`() {
    val tester = Recreation()
    var confirmed: FluidRecurrenceRule? = null
    var open by mutableStateOf(true)
    compose.setContent {
      tester.Host {
        Harness {
          FluidRecurrenceSheet(
          visible = open,
          onDismissRequest = { open = false },
          onConfirm = { confirmed = it; open = false },
          start = today,
          locale = Locale.ITALY,
          )
        }
      }
    }
    advance(900)
    compose.onNode(hasText("Ogni anno") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
    tester.recreate { advance(it) }
    advance(900)
    compose.onNode(hasText("Fine") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    advance(600)
    assertEquals(FluidRecurrenceFrequency.Yearly, confirmed?.frequency)
  }

  // --- Minore: la capsula di una durata negativa --------------------------------------------------------------------

  @Test fun `una durata negativa nella capsula dice il minimo della ruota`() {
    show { FluidDurationCapsule(seconds = -90L, onSecondsChange = {}, modifier = Modifier.testTag("durata")) }
    assertTrue(exists(hasText("1 min")))
  }
}
