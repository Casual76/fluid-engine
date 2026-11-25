package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.audio.FluidAudioRecorder
import dev.antigravity.fluidengine.audio.FluidMicrophoneFailure
import dev.antigravity.fluidengine.audio.FluidPcmSource
import dev.antigravity.fluidengine.audio.FluidRecorderConfig
import dev.antigravity.fluidengine.audio.FluidRecorderStatus
import dev.antigravity.fluidengine.audio.FluidRecordingFormat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.audio.FluidAudioEnvelope
import dev.antigravity.fluidengine.foundation.audio.FluidRecordingQuality
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidRecordingQualityRow
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.transcript.FluidTranscript
import dev.antigravity.fluidengine.transcript.FluidTranscriptState
import dev.antigravity.fluidengine.transcript.FluidTranscriptView
import dev.antigravity.fluidengine.transcript.rememberFluidTranscriptState
import dev.antigravity.fluidengine.ui.fluid.FluidAudioTimeline
import dev.antigravity.fluidengine.ui.fluid.FluidAudioTimelineState
import dev.antigravity.fluidengine.ui.fluid.FluidAudioWaveform
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRecorderPhase
import dev.antigravity.fluidengine.ui.fluid.FluidVoiceRecorder
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidAudioTimelineState
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaSession
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.nio.file.Files
import java.time.Duration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Un lettore finto: la posizione la sposta il test, i salti si ricordano. */
private class AudioFakeSession(duration: Long = 58_804L) : FluidMediaSession {
  override var playing: Boolean by mutableStateOf(false)
  override var positionMillis: Long by mutableLongStateOf(0L)
  override var durationMillis: Long? by mutableStateOf(duration)
  override val canSeek: Boolean = true
  val seeks = mutableListOf<Long>()
  override fun play() { playing = true }
  override fun pause() { playing = false }
  override fun seekTo(positionMillis: Long) {
    seeks += positionMillis
    this.positionMillis = positionMillis
  }
}

private class AudioHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) { played += event }
  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/** Il microfono finto: un tono, poi silenzio a tempo vero. */
private class ToneMicrophone : FluidPcmSource {
  @Volatile var started = false
  private var at = 0
  private val tone = ShortArray(48_000) { (sin(2 * PI * 220 * it / 48_000) * 0.3 * 32767).toInt().toShort() }
  override var onInterrupted: (() -> Unit)? = null
  override val failure: FluidMicrophoneFailure get() = FluidMicrophoneFailure.Unknown
  override fun start(sampleRate: Int): Boolean { started = true; return true }
  override fun read(frame: ShortArray): Int {
    Thread.sleep(20)
    for (i in frame.indices) frame[i] = tone[(at + i) % tone.size]
    at += frame.size
    return frame.size
  }
  override fun stop() { started = false }
}

/**
 * F8.13 · L'audio nell'interfaccia, col dito vero (eventi del sistema) e col vetro vero (verdetti degli shader accettati):
 * l'onda da scorrere e quella di Memo Vocali, il registratore con un microfono finto e il registratore vero di
 * engine-audio, l'ascolto dal paragrafo con la lezione della galleria (i tempi veri di ogni parola). In Robolectric la
 * densita' e' 2.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p13AudioUiTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = AudioHaptics()
  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private lateinit var folder: File

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
    folder = Files.createTempDirectory("f8p13").toFile()
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    folder.deleteRecursively()
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
        CompositionLocalProvider(
          LocalFluidHaptics provides haptics,
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
        ) {
          Box(Modifier.fillMaxSize()) { content() }
        }
      }
    }
    advance(300)
    finger = MaterialFinger(view)
  }

  private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun state(tag: String): String? =
    compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

  /** Un trascinamento a passi da 16 ms, come un dito. */
  private fun drag(from: Offset, to: Offset, steps: Int = 12, stepMs: Long = 16) {
    finger.down(from)
    advance(stepMs)
    for (i in 1..steps) {
      finger.move(Offset(from.x + (to.x - from.x) * i / steps, from.y + (to.y - from.y) * i / steps))
      advance(stepMs)
    }
    finger.up(to)
    advance(stepMs)
  }

  private val flat = FluidAudioEnvelope.of(FloatArray(2_941) { if ((it / 50) % 2 == 0) 0.6f else 0.05f })

  // --- L'onda della riga ------------------------------------------------------------------------------------------

  @Test
  fun theRowWaveformFollowsTheFingerRelativelyAndSeeksOnce() {
    val session = AudioFakeSession()
    val scrubs = mutableListOf<Long>()
    show {
      FluidAudioWaveform(
        envelope = flat,
        position = { session.positionMillis },
        onSeek = session::seekTo,
        durationMillis = 58_804L,
        onScrub = { scrubs += it },
        modifier = Modifier.padding(top = 200.dp).padding(horizontal = 20.dp).testTag("riga"),
      )
    }
    val row = bounds("riga")
    // Prendere l'onda in mezzo non la sposta: conta solo quanto si muove il dito (un quarto della riga = un quarto).
    drag(Offset(row.center.x, row.center.y), Offset(row.center.x + row.width / 4, row.center.y))
    assertEquals("un salto solo, al rilascio", 1, session.seeks.size)
    val expected = 58_804L / 4
    assertTrue("salto ${session.seeks.single()} contro $expected", abs(session.seeks.single() - expected) < 58_804L / 25)
    assertTrue(scrubs.size > 3)
    assertEquals("i valori del dito salgono", scrubs, scrubs.sorted())
    // Il tocco salta dove si tocca.
    finger.down(Offset(row.left + row.width * 0.75f, row.center.y))
    advance(32)
    finger.up(Offset(row.left + row.width * 0.75f, row.center.y))
    advance(64)
    assertEquals(2, session.seeks.size)
    assertTrue(abs(session.seeks.last() - 58_804L * 3 / 4) < 58_804L / 20)
  }

  @Test
  fun theRowWaveformTalksToTalkBack() {
    val session = AudioFakeSession()
    session.positionMillis = 30_000L
    show {
      FluidAudioWaveform(
        envelope = flat,
        position = { session.positionMillis },
        onSeek = session::seekTo,
        durationMillis = 58_804L,
        modifier = Modifier.padding(top = 200.dp).testTag("riga"),
      )
    }
    assertEquals("0:30 di 0:58", state("riga"))
    val node = compose.onNodeWithTag("riga", useUnmergedTree = true).fetchSemanticsNode()
    val actions = node.config[SemanticsActions.CustomActions]
    assertEquals(listOf("Indietro di 15 secondi", "Avanti di 15 secondi"), actions.map { it.label })
    actions[1].action()
    assertEquals(45_000L, session.seeks.last())
    compose.onNodeWithTag("riga", useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(10_000f) }
    assertEquals(10_000L, session.seeks.last())
  }

  // --- L'onda di Memo Vocali --------------------------------------------------------------------------------------

  private fun showTimeline(session: AudioFakeSession, reduced: Boolean = false, recording: Boolean = false): FluidAudioTimelineState {
    lateinit var timeline: FluidAudioTimelineState
    show(reduced) {
      timeline = rememberFluidAudioTimelineState()
      FluidAudioTimeline(
        envelope = flat,
        position = { session.positionMillis },
        onSeek = session::seekTo,
        durationMillis = { 58_804L },
        state = timeline,
        recording = recording,
        modifier = Modifier.padding(top = 120.dp).testTag("onda"),
      )
    }
    return timeline
  }

  @Test
  fun theTimelineMovesWithTheFingerAndStopsWhereItIsLeftWithReducedMotion() {
    val session = AudioFakeSession().apply { positionMillis = 20_000L }
    val timeline = showTimeline(session, reduced = true)
    val band = bounds("onda")
    // Il dito a sinistra di 400 px (200 dp, densita' 2): l'onda va con lui, il tempo avanza di 2 s a 100 dp al secondo
    // (meno lo slop del tocco, che non sposta niente).
    drag(Offset(band.center.x + 200f, band.center.y), Offset(band.center.x - 200f, band.center.y), steps = 10, stepMs = 8)
    assertEquals(1, session.seeks.size)
    assertTrue("salto ${session.seeks.single()}", abs(session.seeks.single() - 22_000L) < 400L)
    assertEquals(100f, timeline.dpPerSecond, 0f)
  }

  @Test
  fun aFastSwipeIsThrownFurtherThanTheFinger() {
    val session = AudioFakeSession().apply { positionMillis = 20_000L }
    showTimeline(session)
    val band = bounds("onda")
    drag(Offset(band.center.x + 150f, band.center.y), Offset(band.center.x - 150f, band.center.y), steps = 6, stepMs = 8)
    advance(2_000)
    assertEquals("un salto solo, a fine lancio", 1, session.seeks.size)
    // Il dito da solo porta a 21,5 s (300 px, 150 dp): il lancio va oltre.
    assertTrue("salto ${session.seeks.single()}", session.seeks.single() > 22_500L)
  }

  @Test
  fun aPinchZoomsAroundThePlayhead() {
    val session = AudioFakeSession().apply { positionMillis = 20_000L }
    val timeline = showTimeline(session)
    val band = bounds("onda")
    val y = band.center.y
    finger.downAll(listOf(Offset(band.center.x - 60f, y), Offset(band.center.x + 60f, y)))
    advance(16)
    for (i in 1..8) {
      finger.moveAll(listOf(Offset(band.center.x - 60f - i * 15f, y), Offset(band.center.x + 60f + i * 15f, y)))
      advance(16)
    }
    finger.upAll(listOf(Offset(band.center.x - 180f, y), Offset(band.center.x + 180f, y)))
    advance(32)
    assertTrue("zoom ${timeline.dpPerSecond}", timeline.dpPerSecond > 250f)
    assertTrue("il pizzico non salta", session.seeks.isEmpty())
  }

  @Test
  fun whileRecordingTheTimelineIsNotTouched() {
    val session = AudioFakeSession().apply { positionMillis = 5_000L }
    showTimeline(session, recording = true)
    val band = bounds("onda")
    drag(Offset(band.center.x + 100f, band.center.y), Offset(band.center.x - 100f, band.center.y))
    assertTrue(session.seeks.isEmpty())
  }

  // --- Il registratore ----------------------------------------------------------------------------------------------

  @Test
  fun theRecorderPanelGoesThroughItsPhases() {
    var phase by mutableStateOf(FluidRecorderPhase.Idle)
    val calls = mutableListOf<String>()
    var elapsed = 0L
    show {
      Box(Modifier.fillMaxSize()) {
        FluidVoiceRecorder(
          phase = phase,
          onRecord = { calls += "registra"; phase = FluidRecorderPhase.Recording },
          onPause = { calls += "pausa"; phase = FluidRecorderPhase.Paused },
          onResume = { calls += "riprendi"; phase = FluidRecorderPhase.Recording },
          onDone = { calls += "fine"; phase = FluidRecorderPhase.Idle },
          envelope = flat,
          elapsedMillis = { elapsed },
          modifier = Modifier.padding(top = 300.dp),
        )
      }
    }
    compose.onNode(hasContentDescription("Registra")).performClick()
    advance(900)
    elapsed = 7_250L
    advance(1_100)
    compose.onNode(hasContentDescription("Durata 0:07"), useUnmergedTree = true).assertExists()
    compose.onNode(hasContentDescription("Pausa"), useUnmergedTree = true).performClick()
    advance(400)
    compose.onNode(hasContentDescription("Riprendi"), useUnmergedTree = true).performClick()
    advance(400)
    compose.onNode(hasContentDescription("Fine"), useUnmergedTree = true).performClick()
    advance(900)
    assertEquals(listOf("registra", "pausa", "riprendi", "fine"), calls)
    compose.onNode(hasContentDescription("Registra")).assertExists()
  }

  @Test
  fun theRealRecorderBehindThePanelWritesOneFileWithPauses() = runBlocking {
    val mic = ToneMicrophone()
    val recorder = FluidAudioRecorder(mic, FluidRecorderConfig(format = FluidRecordingFormat.Wav))
    val target = File(folder, "memo.wav")
    show {
      val status by recorder.status.collectAsState()
      val phase = when (status) {
        is FluidRecorderStatus.Recording -> FluidRecorderPhase.Recording
        is FluidRecorderStatus.Paused -> FluidRecorderPhase.Paused
        else -> FluidRecorderPhase.Idle
      }
      FluidVoiceRecorder(
        phase = phase,
        onRecord = { recorder.start(target) },
        onPause = { recorder.pause() },
        onResume = { recorder.resume() },
        onDone = {},
        envelope = recorder.envelope,
        elapsedMillis = recorder::elapsedMillis,
        modifier = Modifier.padding(top = 300.dp),
      )
    }
    compose.onNode(hasContentDescription("Registra")).performClick()
    advance(200)
    Thread.sleep(400)
    advance(200)
    assertTrue(recorder.status.value is FluidRecorderStatus.Recording)
    assertTrue("l'onda cresce", recorder.envelope.size > 5)
    compose.onNode(hasContentDescription("Pausa"), useUnmergedTree = true).performClick()
    advance(200)
    assertFalse("in pausa il microfono e' spento", mic.started)
    val paused = recorder.elapsedMillis()
    compose.onNode(hasContentDescription("Riprendi"), useUnmergedTree = true).performClick()
    Thread.sleep(300)
    advance(200)
    val recording = recorder.stop()
    assertNotNull(recording)
    assertTrue(recording!!.durationMillis > paused)
    // Il file e' uno solo: 48 000 campioni da 2 byte al secondo, per tutta la durata (le due parti).
    assertTrue(abs((target.length() - 44) - recording.durationMillis * 96L) <= 96L * 2)
  }

  // --- La qualita' (F8.13b) ----------------------------------------------------------------------------------------

  @Test
  fun theQualityRowShowsTheHourAndChangesThePreset() {
    var chosen by mutableStateOf(FluidRecordingQuality.Standard)
    show {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        Box(Modifier.fillMaxSize()) {
          FluidListGroup(glass = true, modifier = Modifier.padding(top = 120.dp)) {
            FluidRecordingQualityRow(selected = chosen, onSelected = { chosen = it }, modifier = Modifier.testTag("qualita"))
          }
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
    val node = compose.onNodeWithTag("qualita", useUnmergedTree = true).fetchSemanticsNode()
    assertEquals("Standard", node.config.getOrNull(SemanticsProperties.StateDescription))
    val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
    assertTrue(description, description.startsWith("Qualità di registrazione, AAC 96 kbit/s · circa 43"))
    assertTrue(description, description.endsWith("MB all'ora"))
    compose.onNodeWithTag("qualita", useUnmergedTree = true).performClick()
    advance(700)
    // Nel pop-up le quattro, ognuna col suo spazio di un'ora.
    // Il pop-up nasce dalla riga: durante e dopo il viaggio ogni voce puo' comparire in due nodi (il pannello e la sua
    // copia), basta che ci sia.
    assertTrue(compose.onAllNodes(hasText("Voce per l'IA"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    assertTrue(compose.onAllNodes(hasContentDescription("WAV 48 kHz · circa 345", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    compose.onAllNodes(hasText("Senza perdita"), useUnmergedTree = true).onFirst().performClick()
    advance(900)
    assertEquals(FluidRecordingQuality.Lossless, chosen)
  }

  // --- L'ascolto dal paragrafo ------------------------------------------------------------------------------------

  private fun showTranscript(
    session: AudioFakeSession,
    words: Boolean = true,
    reduced: Boolean = false,
  ): Pair<FluidTranscript, () -> FluidTranscriptState> {
    lateinit var transcript: FluidTranscript
    lateinit var state: FluidTranscriptState
    show(reduced) {
      val context = LocalContext.current
      transcript = remember { lectureTranscript(context, words) }
      state = rememberFluidTranscriptState()
      Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
        FluidTranscriptView(
          transcript = transcript,
          position = { session.positionMillis },
          onSeek = session::seekTo,
          playing = session.playing,
          state = state,
          modifier = Modifier.fillMaxWidth().height(520.dp).testTag("testo"),
        )
      }
    }
    return transcript to { state }
  }

  /** Il centro della parola [word] nel paragrafo [paragraph], in coordinate della radice. */
  private fun wordCentre(transcript: FluidTranscript, paragraph: Int, word: String): Offset {
    val text = transcript.paragraphs[paragraph].text
    val start = text.indexOf(word)
    val node = compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
    val box = layouts.first().getBoundingBox(start + word.length / 2)
    return node.boundsInRoot.topLeft + box.center
  }

  private fun tap(at: Offset) {
    finger.down(at)
    advance(32)
    finger.up(at)
    advance(400)
  }

  @Test
  fun aTapOnAWordJumpsToThatWordNotToTheStartOfTheSentence() {
    val session = AudioFakeSession()
    val (transcript, _) = showTranscript(session)
    // «laghi» sta nella seconda frase del primo paragrafo: il salto va a lei, non a «L'acqua» ne' a «Oggi».
    val paragraph = transcript.paragraphs[0]
    val target = paragraph.words.first { paragraph.text.substring(it.start, it.end) == "laghi" }
    tap(wordCentre(transcript, 0, "laghi"))
    assertEquals(target.startMs, session.seeks.single())
    assertTrue("non l'inizio del paragrafo", session.seeks.single() > paragraph.startMillis!! + 2_000L)
  }

  @Test
  fun theTimeLabelAndTalkBackJumpToTheParagraph() {
    val session = AudioFakeSession()
    val (transcript, _) = showTranscript(session)
    compose.onNodeWithText("0:13", useUnmergedTree = true).performClick()
    advance(200)
    assertEquals(transcript.paragraphs[1].startMillis, session.seeks.last())
    // TalkBack: il paragrafo e' un nodo con «Ascolta da qui».
    session.seeks.clear()
    compose.onNode(hasText(transcript.paragraphs[0].text)).performSemanticsAction(SemanticsActions.OnClick)
    advance(200)
    assertEquals(transcript.paragraphs[0].startMillis, session.seeks.last())
    val label = compose.onNode(hasText(transcript.paragraphs[0].text)).fetchSemanticsNode().config[SemanticsActions.OnClick].label
    assertEquals("Ascolta da qui", label)
  }

  @Test
  fun withoutWordTimesAWordGoesToTheParagraphStart() {
    val session = AudioFakeSession()
    val (transcript, _) = showTranscript(session, words = false)
    tap(wordCentre(transcript, 0, "vapore"))
    assertEquals(transcript.paragraphs[0].startMillis, session.seeks.single())
  }

  @Test
  fun thePageFollowsTheVoiceStopsForTheFingerAndComesBack() {
    val session = AudioFakeSession()
    val (transcript, stateOf) = showTranscript(session)
    val state = stateOf()
    session.playing = true
    advance(100)
    // Il quarto paragrafo: la pagina scorre una volta e lo porta in vista.
    session.positionMillis = transcript.paragraphs[3].words[20].startMs
    advance(1_500)
    val text = bounds("testo")
    val heard = compose.onNode(hasText(transcript.paragraphs[3].text), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue("il paragrafo che si sente e' in vista", heard.top < text.bottom && heard.bottom > text.top)
    assertEquals("In ascolto", compose.onNode(hasText(transcript.paragraphs[3].text)).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    // Il dito scorre: la pagina smette di seguire e compare «Torna alla voce».
    drag(Offset(text.center.x, text.top + 80f), Offset(text.center.x, text.bottom - 40f), steps = 10)
    // Il lancio della lista finisce.
    advance(2_000)
    assertFalse(state.following)
    val offsetAfterDrag = state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset
    // Il lettore va avanti, la pagina sta ferma.
    session.positionMillis = transcript.paragraphs[3].words[30].startMs
    advance(800)
    assertEquals(offsetAfterDrag, state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset)
    compose.onNodeWithText("Torna alla voce").performClick()
    advance(1_500)
    assertTrue(state.following)
    val back = compose.onNode(hasText(transcript.paragraphs[3].text), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(back.top < text.bottom && back.bottom > text.top)
  }

  @Test
  fun withReducedMotionThePageSettlesInOneFrame() {
    val session = AudioFakeSession()
    val (transcript, stateOf) = showTranscript(session, reduced = true)
    val state = stateOf()
    session.playing = true
    advance(100)
    session.positionMillis = transcript.paragraphs[3].words[25].startMs
    advance(48)
    val after = state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset
    advance(800)
    assertEquals("si posa, non scorre", after, state.list.firstVisibleItemIndex to state.list.firstVisibleItemScrollOffset)
    assertTrue(after != (0 to 0))
  }

  // --- Le scene ---------------------------------------------------------------------------------------------------

  @Test
  fun everySceneOpens() {
    val session = AudioFakeSession()
    F8p13Audio.recorderFactory = { FluidAudioRecorder(ToneMicrophone(), FluidRecorderConfig(format = FluidRecordingFormat.Wav)) }
    F8p13Audio.memoFolder = { folder }
    try {
      var wanted by mutableStateOf<String?>(null)
      show {
        CompositionLocalProvider(LocalF8p13Player provides session) {
          androidx.compose.runtime.key(wanted) { NewElementsPage(onBack = {}, initial = wanted) }
        }
      }
      val titles = mapOf(
        "memo-vocali" to "Memo vocali",
        "onda-audio" to "L'onda da scorrere",
        "riproduzione-sistema" to "La lezione in sottofondo",
        "ascolto-paragrafo" to "L'ascolto dal paragrafo",
      )
      for (scene in F8p13Section.scenes) {
        wanted = scene.id
        advance(1_200)
        val title = titles.getValue(scene.id)
        assertTrue("${scene.id} non si vede", compose.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty())
      }
      assertEquals(listOf("memo-vocali", "onda-audio", "riproduzione-sistema", "ascolto-paragrafo"), F8p13Section.scenes.map { it.id })
      assertTrue(NewElementSections.contains(F8p13Section))
    } finally {
      F8p13Audio.recorderFactory = { FluidAudioRecorder(it) }
      F8p13Audio.memoFolder = { File(it.filesDir, "novita-memo") }
    }
  }
}
