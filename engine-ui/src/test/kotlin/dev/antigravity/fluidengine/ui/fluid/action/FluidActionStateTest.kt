package dev.antigravity.fluidengine.ui.fluid.action

import androidx.compose.runtime.saveable.SaverScope
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.Download
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Save
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Lo stato dei tasti con esito e l'esecutore (piano «iOS in Android», F6B.3a), in logica pura: la soglia dei 300 ms
 * (sotto si vede solo l'esito), il tasto spento mentre lavora, la tenuta dell'esito per preset o finché la si chiede, la
 * ripresa dopo la ricreazione; e ciò che il tasto mostra, dice e fa sentire in ogni stato. La resa nell'albero (le parole
 * di TalkBack, i bersagli da 48 dp, le catture) la provano i test della galleria (`FluidActionButtonUiTest`,
 * `FluidActionCaptureTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidActionStateTest {

  private fun TestScope.runner(preset: FluidActionPreset? = FluidActionPreset.Save, hold: FluidActionHold = FluidActionHold.Preset) =
    FluidActionRunner(this, preset, hold, clock = { testScheduler.currentTime })

  /** Avanza di [ms] e fa girare ciò che scade esattamente lì. */
  private fun TestScope.step(ms: Long) {
    advanceTimeBy(ms)
    runCurrent()
  }

  private fun FluidActionState.isPendingWork() = this is FluidActionState.Working && pending
  private fun FluidActionState.isVisibleWork() = this is FluidActionState.Working && !pending

  // ---- La soglia ------------------------------------------------------------------------------------------------

  @Test fun `pausa esterna conserva identita del lavoro e annuncia il valore esatto`() {
    val reader = { .48f }
    val active = FluidActionState.Working(reader)
    val paused = FluidActionState.Working(reader, paused = true)
    assertFalse(active.paused)
    assertFalse(paused.acceptsTap)
    assertFalse(active == paused)
    assertEquals(paused, FluidActionState.Working(reader, true))
    for (preset in listOf(FluidActionPreset.Download, FluidActionPreset.Upload)) {
      assertTrue(fluidActionFace(preset, paused).paused)
      assertEquals("In pausa", fluidActionSpoken(preset, paused, FluidActionLabels()))
      assertEquals("Paused", fluidActionSpoken(preset, paused, FluidActionLabels.english()))
    }
    assertEquals("48%", fluidActionSpokenProgress(reader(), FluidActionLabels(), paused = true))
    assertEquals("25%", fluidActionSpokenProgress(reader(), FluidActionLabels()))
  }

  @Test fun `un lavoro piu breve di 300 ms mostra solo l'esito`() = runTest {
    val save = runner()
    val seen = mutableListOf<String>()
    assertTrue(save.run { delay(180) })
    seen += save.state.label
    runCurrent()
    seen += save.state.label
    step(179)
    seen += save.state.label
    step(1)
    seen += save.state.label
    // Il lavoro sotto soglia ha la faccia di riposo: fra riposo e spunta non passa nessun anello.
    assertEquals(listOf("lavoro sotto soglia", "lavoro sotto soglia", "lavoro sotto soglia", "riuscito"), seen)
    assertEquals(FluidActionState.Succeeded, save.state)
    assertEquals(fluidActionFace(FluidActionPreset.Save, FluidActionState.Idle),
      fluidActionFace(FluidActionPreset.Save, FluidActionState.Working(null, pending = true)))
  }

  @Test fun `oltre la soglia il lavoro si vede dai 300 ms e il tasto e spento dall'inizio`() = runTest {
    val save = runner()
    save.run { delay(800) }
    runCurrent()
    assertTrue(save.state.isPendingWork())
    assertFalse("spento gia sotto soglia", save.state.acceptsTap)
    assertTrue(save.isRunning)
    step(299)
    assertTrue("a 299 ms ancora sotto soglia", save.state.isPendingWork())
    step(1)
    assertTrue("a 300 ms l'anello", save.state.isVisibleWork())
    assertFalse(save.state.acceptsTap)
    assertEquals(FluidActionRing.Indeterminate, fluidActionFace(FluidActionPreset.Save, save.state).ring)
    step(500)
    assertEquals(FluidActionState.Succeeded, save.state)
    assertTrue(save.state.acceptsTap)
    assertFalse(save.isRunning)
  }

  @Test fun `un lavoro in corso non riparte`() = runTest {
    val send = runner(FluidActionPreset.Send)
    var runs = 0
    assertTrue(send.run { runs++; delay(1_000) })
    assertFalse("il tasto spento non accoda un secondo invio", send.run { runs++ })
    step(1_000)
    assertEquals(1, runs)
    assertEquals(FluidActionState.Succeeded, send.state)
    assertTrue("a esito in mostra si puo ripartire", send.run { runs++ })
    runCurrent()
    assertEquals(2, runs)
  }

  @Test fun `la soglia si cambia e un preset senza lavoro non lo mostra mai`() = runTest {
    val slow = FluidActionRunner(this, FluidActionPreset.Save, minWorking = 50.milliseconds, clock = { testScheduler.currentTime })
    slow.run { delay(200) }
    step(50)
    assertTrue(slow.state.isVisibleWork())
    step(150)
    val copy = runner(FluidActionPreset.Copy)
    copy.run { delay(2_000) }
    step(1_000)
    assertTrue("Copia non mostra il lavoro", copy.state.isPendingWork())
    assertEquals(fluidActionFace(FluidActionPreset.Copy, FluidActionState.Idle), fluidActionFace(FluidActionPreset.Copy, copy.state))
    step(1_000)
    assertEquals(FluidActionState.Succeeded, copy.state)
  }

  // ---- La tenuta dell'esito -------------------------------------------------------------------------------------

  @Test fun `l'esito resta il tempo del preset e poi torna a riposo`() = runTest {
    for ((preset, ms) in listOf(FluidActionPreset.Save to 1_500L, FluidActionPreset.Copy to 1_000L,
      FluidActionPreset.Download to 2_000L, FluidActionPreset.Send to 1_500L)) {
      val r = runner(preset)
      r.run { }
      runCurrent()
      assertEquals(preset.toString(), FluidActionState.Succeeded, r.state)
      assertEquals(testScheduler.currentTime + ms, r.outcomeUntil)
      step(ms - 1)
      assertEquals(FluidActionState.Succeeded, r.state)
      step(1)
      assertEquals(preset.toString(), FluidActionState.Idle, r.state)
      assertNull(r.outcomeUntil)
    }
  }

  @Test fun `il fallimento resta piu a lungo della riuscita`() = runTest {
    val r = runner(FluidActionPreset.Send)
    r.run { error("rete") }
    runCurrent()
    assertEquals(FluidActionState.Failed, r.state)
    step(2_499)
    assertEquals(FluidActionState.Failed, r.state)
    step(1)
    assertEquals(FluidActionState.Idle, r.state)
    assertTrue(FluidActionProfile.FailureMs > FluidActionProfile.SuccessMs)
  }

  @Test fun `l'esito resta finche lo si chiede, o sparisce subito con una tenuta nulla`() = runTest {
    val sticky = runner(hold = FluidActionHold.UntilReset)
    sticky.run { }
    step(60_000)
    assertEquals(FluidActionState.Succeeded, sticky.state)
    assertNull("nessuna scadenza", sticky.outcomeUntil)
    sticky.reset()
    assertEquals(FluidActionState.Idle, sticky.state)

    val instant = runner(hold = FluidActionHold.For(Duration.ZERO))
    instant.run { }
    runCurrent()
    assertEquals(FluidActionState.Idle, instant.state)

    val fixed = runner(hold = FluidActionHold.For(400.milliseconds))
    fixed.run { error("x") }
    runCurrent()
    step(399)
    assertEquals(FluidActionState.Failed, fixed.state)
    step(1)
    assertEquals(FluidActionState.Idle, fixed.state)
  }

  @Test fun `un nuovo lavoro cancella la tenuta di prima`() = runTest {
    val r = runner()
    r.run { }
    runCurrent()
    step(1_000)
    r.run { delay(1_000) }
    runCurrent()
    // La tenuta del primo esito scadeva a 1500: non deve riportare a riposo il lavoro nuovo.
    step(600)
    assertTrue(r.state.isVisibleWork())
    step(400)
    assertEquals(FluidActionState.Succeeded, r.state)
  }

  // ---- Fallimenti, annullamenti, avanzamento --------------------------------------------------------------------

  @Test fun `fallisce chi lancia, chi chiama fail e chi scade dentro il lavoro`() = runTest {
    val thrown = runner()
    thrown.run { delay(10); throw IllegalStateException("disco pieno") }
    step(10)
    assertEquals(FluidActionState.Failed, thrown.state)

    val explicit = runner()
    explicit.run { fail() }
    runCurrent()
    assertEquals(FluidActionState.Failed, explicit.state)

    val timeout = runner()
    timeout.run { withTimeout(100) { delay(1_000) } }
    step(100)
    assertEquals("un withTimeout scaduto e un fallimento, non un annullamento", FluidActionState.Failed, timeout.state)
  }

  @Test fun `annullato dice annullato e torna a riposo`() = runTest {
    val r = runner(FluidActionPreset.Upload)
    r.run { delay(10_000) }
    step(400)
    assertTrue(r.state.isVisibleWork())
    r.cancel()
    runCurrent()
    assertEquals(FluidActionState.Cancelled, r.state)
    assertFalse(r.isRunning)
    assertEquals(fluidActionFace(FluidActionPreset.Upload, FluidActionState.Idle),
      fluidActionFace(FluidActionPreset.Upload, FluidActionState.Cancelled))
    step(FluidActionProfile.CancelledMs)
    assertEquals(FluidActionState.Idle, r.state)
  }

  @Test fun `l'avanzamento passa dall'anello che gira all'arco, senza ricreare lo stato a ogni valore`() = runTest {
    val r = runner(FluidActionPreset.Download)
    lateinit var work: FluidActionWork
    r.run { work = this; delay(5_000) }
    runCurrent()
    work.progress = 0.1f
    // Sotto soglia: niente cambia di faccia.
    assertTrue(r.state.isPendingWork())
    step(300)
    val first = r.state as FluidActionState.Working
    assertFalse(first.pending)
    val read = first.progress!!
    assertEquals(0.1f, read(), 0f)
    work.progress = 0.42f
    assertSame("lo stesso stato, l'avanzamento si legge al disegno", first, r.state)
    assertEquals(0.42f, read(), 0f)
    assertEquals(FluidActionFace(FluidActionKind.Download, null, FluidActionRing.Determinate, digits = true),
      fluidActionFace(FluidActionPreset.Download, r.state))
    work.progress = 7f
    assertEquals(1f, read(), 0f)
    step(5_000)
    assertEquals(FluidActionState.Succeeded, r.state)
  }

  @Test fun `l'avanzamento arrivato dopo la soglia cambia la faccia una volta`() = runTest {
    val r = runner(FluidActionPreset.Save)
    lateinit var work: FluidActionWork
    r.run { work = this; delay(5_000) }
    step(300)
    val spinning = r.state as FluidActionState.Working
    assertNull(spinning.progress)
    work.progress = 0.5f
    val arc = r.state as FluidActionState.Working
    assertNotEquals(spinning, arc)
    assertEquals(FluidActionRing.Determinate, fluidActionFace(FluidActionPreset.Save, arc).ring)
    work.progress = 0.6f
    assertSame(arc, r.state)
    work.progress = null
    assertEquals(FluidActionRing.Indeterminate, fluidActionFace(FluidActionPreset.Save, r.state).ring)
    step(5_000)
  }

  @Test fun `il preferito e ottimistico`() = runTest {
    val r = runner(FluidActionPreset.Favorite)
    r.run { delay(700) }
    runCurrent()
    // Al tocco la faccia piena, senza lavoro.
    assertEquals(FluidActionFace(FluidActionKind.Favorite, FluidIcons.Filled.Favorite), fluidActionFace(FluidActionPreset.Favorite, r.state))
    step(700)
    assertEquals("confermato, torna subito a riposo: l'app ha gia il cuore pieno", FluidActionState.Idle, r.state)
    r.run { delay(200); error("rifiutato") }
    step(200)
    assertEquals(FluidActionState.Failed, r.state)
    assertEquals(FluidIcons.Favorite, fluidActionFace(FluidActionPreset.Favorite, r.state).icon)
    step(FluidActionProfile.FavoriteFailureMs)
    assertEquals(FluidActionState.Idle, r.state)
  }

  @Test fun `un ambito gia chiuso non lascia il tasto spento per sempre`() = runTest {
    val dead = CoroutineScope(StandardTestDispatcher(testScheduler) + Job().also { it.cancel() })
    val r = FluidActionRunner(dead, FluidActionPreset.Save, clock = { testScheduler.currentTime })
    r.run { delay(100) }
    runCurrent()
    assertFalse(r.isRunning)
    assertTrue(r.state.acceptsTap)
  }

  // ---- La ripresa dopo la ricreazione ---------------------------------------------------------------------------

  private val saverScope = object : SaverScope {
    override fun canBeSaved(value: Any): Boolean = true
  }

  private fun TestScope.recreate(runner: FluidActionRunner, preset: FluidActionPreset = FluidActionPreset.Save): FluidActionRunner {
    val saver = FluidActionRunner.saver(this) { FluidActionRunner(it, preset, clock = { testScheduler.currentTime }) }
    val saved = with(saver) { saverScope.save(runner) }!!
    return saver.restore(saved)!!
  }

  @Test fun `dopo la ricreazione l'esito torna col tempo che gli restava`() = runTest {
    val before = runner()
    before.run { }
    runCurrent()
    val until = before.outcomeUntil!!
    step(500)
    val after = recreate(before)
    assertEquals(FluidActionState.Succeeded, after.state)
    assertEquals("la stessa scadenza, non una tenuta intera da capo", until, after.outcomeUntil)
    step(999)
    assertEquals(FluidActionState.Succeeded, after.state)
    step(1)
    assertEquals(FluidActionState.Idle, after.state)
  }

  @Test fun `dopo la ricreazione un esito scaduto e un lavoro interrotto sono a riposo`() = runTest {
    val failed = runner()
    failed.run { error("x") }
    runCurrent()
    val saver = FluidActionRunner.saver(this) { FluidActionRunner(it, FluidActionPreset.Save, clock = { testScheduler.currentTime }) }
    val saved = with(saver) { saverScope.save(failed) }!!
    advanceTimeBy(3_000) // la tenuta (2,5 s) scade mentre l'attività non c'è
    assertEquals(FluidActionState.Idle, saver.restore(saved)!!.state)

    val working = runner()
    working.run { delay(10_000) }
    step(400)
    val restored = recreate(working)
    assertEquals("nessun anello che gira per sempre", FluidActionState.Idle, restored.state)
    assertTrue(restored.state.acceptsTap)
    working.cancel()
    runCurrent()
  }

  @Test fun `dopo la ricreazione un esito tenuto finche lo si chiede resta`() = runTest {
    val sticky = runner(hold = FluidActionHold.UntilReset)
    sticky.run { error("x") }
    runCurrent()
    val saver = FluidActionRunner.saver(this) {
      FluidActionRunner(it, FluidActionPreset.Save, FluidActionHold.UntilReset, clock = { testScheduler.currentTime })
    }
    val back = saver.restore(with(saver) { saverScope.save(sticky) }!!)!!
    step(100_000)
    assertEquals(FluidActionState.Failed, back.state)
  }

  @Test fun `lo stato dell'app non ha logica e si confronta per valore`() {
    val progress = { 0.5f }
    assertEquals(FluidActionState.Working(progress), FluidActionState.Working(progress))
    assertNotEquals(FluidActionState.Working(progress), FluidActionState.Working { 0.5f })
    assertNotEquals(FluidActionState.Working(null), FluidActionState.Working(null, pending = true))
    assertFalse(FluidActionState.Working().pending)
    assertTrue(FluidActionState.Idle.acceptsTap && FluidActionState.Failed.acceptsTap && FluidActionState.Cancelled.acceptsTap)
    assertFalse(FluidActionState.Working().acceptsTap)
  }

  // ---- Le facce -------------------------------------------------------------------------------------------------

  @Test fun `le facce di ogni preset in ogni stato`() {
    val save = FluidActionPreset.Save
    assertEquals(FluidActionFace(FluidActionKind.Save, FluidIcons.Save), fluidActionFace(save, FluidActionState.Idle))
    assertEquals(FluidActionFace(FluidActionKind.Save, FluidIcons.Save, FluidActionRing.Indeterminate, iconScale = FluidActionProfile.WorkingIconScale),
      fluidActionFace(save, FluidActionState.Working()))
    assertEquals(FluidActionFace(FluidActionKind.Save, FluidIcons.Check), fluidActionFace(save, FluidActionState.Succeeded))
    assertEquals(FluidActionFace(FluidActionKind.Save, FluidIcons.Close, ink = FluidActionInk.Error), fluidActionFace(save, FluidActionState.Failed))
    assertEquals(fluidActionFace(save, FluidActionState.Idle), fluidActionFace(save, FluidActionState.Cancelled))
    // Salva con l'avanzamento: l'arco attorno all'icona, niente cifre.
    assertEquals(FluidActionFace(FluidActionKind.Save, FluidIcons.Save, FluidActionRing.Determinate, iconScale = FluidActionProfile.WorkingIconScale),
      fluidActionFace(save, FluidActionState.Working { 0.3f }))
    // Scarica senza avanzamento: l'anello che gira attorno alla freccia.
    assertEquals(FluidIcons.Download, fluidActionFace(FluidActionPreset.Download, FluidActionState.Working()).icon)
    assertEquals(FluidIcons.Send, fluidActionFace(FluidActionPreset.Send, FluidActionState.Working()).icon)
    // Ogni stato visibile ha una faccia diversa dal riposo: l'esito si capisce dall'icona.
    for (preset in listOf(FluidActionPreset.Send, FluidActionPreset.Save, FluidActionPreset.Copy, FluidActionPreset.Download, FluidActionPreset.Upload)) {
      val rest = fluidActionFace(preset, FluidActionState.Idle)
      assertNotEquals(rest, fluidActionFace(preset, FluidActionState.Succeeded))
      assertNotEquals(rest, fluidActionFace(preset, FluidActionState.Failed))
      assertNotEquals(fluidActionFace(preset, FluidActionState.Succeeded), fluidActionFace(preset, FluidActionState.Failed))
    }
  }

  @Test fun `il preferito scelto e quello da scegliere sono due preset`() {
    val chosen = FluidActionPreset.favorite(selected = true)
    assertEquals(FluidIcons.Filled.Favorite, chosen.idle)
    assertEquals(FluidIcons.Favorite, chosen.success)
    assertNotEquals(chosen, FluidActionPreset.Favorite)
    assertEquals(FluidActionPreset.favorite(false), FluidActionPreset.Favorite)
    // Il cuore pieno della riuscita ottimistica e quello a riposo del preferito scelto sono la stessa faccia: quando l'app
    // cambia `selected` alla fine, niente si scambia.
    assertEquals(fluidActionFace(FluidActionPreset.Favorite, FluidActionState.Working()), fluidActionFace(chosen, FluidActionState.Idle))
  }

  @Test fun `un preset su misura porta le sue icone, le sue parole e i suoi tempi`() = runTest {
    val words = FluidActionWords("Pubblica", "Pubblicazione…", "Pubblicato", "Non pubblicato", "Annullato")
    val publish = FluidActionPreset.custom(FluidIcons.Send, FluidIcons.Check, FluidIcons.Close, words,
      successHold = 3_000.milliseconds, percentInRing = true)
    assertEquals("Pubblicazione…", fluidActionSpoken(publish, FluidActionState.Working(), FluidActionLabels()))
    assertTrue(fluidActionFace(publish, FluidActionState.Working { 0.2f }).digits)
    val r = runner(publish)
    r.run { }
    runCurrent()
    step(2_999)
    assertEquals(FluidActionState.Succeeded, r.state)
    step(1)
    assertEquals(FluidActionState.Idle, r.state)
  }

  // ---- Le parole, le cifre, l'aptica, la scossa -----------------------------------------------------------------

  @Test fun `TalkBack dice in corso, inviato e non riuscito`() {
    val labels = FluidActionLabels()
    val send = FluidActionPreset.Send
    assertEquals("Invia", fluidActionSpoken(send, FluidActionState.Idle, labels))
    assertEquals("Invio in corso…", fluidActionSpoken(send, FluidActionState.Working(), labels))
    assertEquals("sotto soglia il tasto e gia spento e lo dice", "Invio in corso…",
      fluidActionSpoken(send, FluidActionState.Working(null, pending = true), labels))
    assertEquals("Inviato", fluidActionSpoken(send, FluidActionState.Succeeded, labels))
    assertEquals("Invio non riuscito", fluidActionSpoken(send, FluidActionState.Failed, labels))
    assertEquals("Invio annullato", fluidActionSpoken(send, FluidActionState.Cancelled, labels))
    assertEquals("il nome dell'app vale a riposo", "Invia la bozza", fluidActionSpoken(send, FluidActionState.Idle, labels, "Invia la bozza"))
    assertEquals("Inviato", fluidActionSpoken(send, FluidActionState.Succeeded, labels, "Invia la bozza"))
    assertEquals("Saved", fluidActionSpoken(FluidActionPreset.Save, FluidActionState.Succeeded, FluidActionLabels.english()))
    assertEquals("Aggiunto ai preferiti", fluidActionSpoken(FluidActionPreset.Favorite, FluidActionState.Working(), labels))
    assertEquals("Rimuovi dai preferiti", fluidActionSpoken(FluidActionPreset.favorite(true), FluidActionState.Idle, labels))
  }

  @Test fun `l'avanzamento si dice a passi e le cifre non arrivano a 100 prima della fine`() {
    val labels = FluidActionLabels()
    assertNull(fluidActionSpokenProgress(null, labels))
    assertEquals("0%", fluidActionSpokenProgress(0.2f, labels))
    assertEquals("25%", fluidActionSpokenProgress(0.25f, labels))
    assertEquals("50%", fluidActionSpokenProgress(0.74f, labels))
    assertEquals("100%", fluidActionSpokenProgress(1f, labels))
    assertEquals(99, fluidActionPercent(0.996f))
    assertEquals(42, fluidActionPercent(0.42f))
    assertEquals(100, fluidActionPercent(1f))
    assertEquals(0, fluidActionPercent(Float.NaN))
    assertEquals(0, fluidActionPercent(-1f))
  }

  @Test fun `un evento aptico per ogni momento`() {
    val save = FluidActionPreset.Save
    val idle = FluidActionState.Idle
    val pending = FluidActionState.Working(null, pending = true)
    val working = FluidActionState.Working()
    assertNull("il tocco lo suona gia il tasto", fluidActionHaptic(save, idle, pending))
    assertNull("il lavoro che compare non vibra", fluidActionHaptic(save, pending, working))
    assertEquals(FluidHapticEvent.Success, fluidActionHaptic(save, working, FluidActionState.Succeeded))
    assertEquals(FluidHapticEvent.Success, fluidActionHaptic(save, pending, FluidActionState.Succeeded))
    assertEquals(FluidHapticEvent.Error, fluidActionHaptic(save, working, FluidActionState.Failed))
    assertEquals(FluidHapticEvent.Close, fluidActionHaptic(save, working, FluidActionState.Cancelled))
    assertNull("il ritorno a riposo non vibra", fluidActionHaptic(save, FluidActionState.Succeeded, idle))
    assertNull(fluidActionHaptic(save, FluidActionState.Failed, FluidActionState.Failed))
    val heart = FluidActionPreset.Favorite
    assertEquals(FluidHapticEvent.ToggleOn, fluidActionHaptic(heart, idle, pending))
    assertEquals(FluidHapticEvent.ToggleOff, fluidActionHaptic(FluidActionPreset.favorite(true), idle, pending))
    assertNull("la conferma non cambia cio che si vede", fluidActionHaptic(heart, pending, idle))
    assertEquals(FluidHapticEvent.Error, fluidActionHaptic(heart, pending, FluidActionState.Failed))
  }

  @Test fun `la scossa parte e arriva ferma e resta nell'ampiezza`() {
    assertEquals(0f, fluidActionShake(0f), 1e-6f)
    assertEquals(0f, fluidActionShake(1f), 1e-5f)
    val samples = (0..100).map { fluidActionShake(it / 100f) }
    assertTrue(samples.all { abs(it) <= FluidActionProfile.ShakeAmplitude + 1e-6f })
    assertTrue("trema davvero", samples.maxOf { abs(it) } > FluidActionProfile.ShakeAmplitude * 0.5f)
    // Tre mezzi periodi: cambia verso due volte, e si smorza.
    val signs = samples.drop(1).dropLast(1).filter { abs(it) > 1e-4f }.map { it > 0f }
    assertEquals(3, signs.zipWithNext().count { (a, b) -> a != b } + 1)
    assertTrue(abs(fluidActionShake(1f / 6f)) > abs(fluidActionShake(5f / 6f)))
    // La scossa parte quando la X si legge (0,7 dello scambio sfocato) e finisce prima che l'esito se ne vada, anche il
    // fallimento più breve (il Preferito).
    val swap = dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults.DurationMs
    val shakeEnd = swap * FluidActionProfile.ShakeDelayFraction + FluidActionProfile.ShakeMs
    assertTrue(FluidActionProfile.ShakeDelayFraction in 0.5f..1f)
    assertTrue(shakeEnd < FluidActionProfile.FavoriteFailureMs)
  }

  @Test fun `le tenute per preset sono quelle del profilo`() {
    assertEquals(FluidActionProfile.CopySuccessMs, fluidActionHoldMs(FluidActionPreset.Copy, FluidActionHold.Preset, FluidActionState.Succeeded))
    assertEquals(FluidActionProfile.FailureMs, fluidActionHoldMs(FluidActionPreset.Copy, FluidActionHold.Preset, FluidActionState.Failed))
    assertEquals(0L, fluidActionHoldMs(FluidActionPreset.Favorite, FluidActionHold.Preset, FluidActionState.Succeeded))
    assertEquals(Long.MAX_VALUE, fluidActionHoldMs(FluidActionPreset.Save, FluidActionHold.UntilReset, FluidActionState.Succeeded))
    assertEquals(250L, fluidActionHoldMs(null, FluidActionHold.For(250.milliseconds), FluidActionState.Cancelled))
    assertEquals(300L, FluidActionDefaults.MinWorking.inWholeMilliseconds)
    assertEquals(FluidActionDefaults.MinWorkingMs, FluidActionDefaults.MinWorking.inWholeMilliseconds)
  }
}
