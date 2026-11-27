package dev.antigravity.fluidengine.sample.novita

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.audio.FluidAudioEnvelopes
import dev.antigravity.fluidengine.audio.FluidAudioRecorder
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.audio.FluidAudioEnvelope
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.transcript.FluidTranscript
import dev.antigravity.fluidengine.transcript.FluidTranscriptView
import dev.antigravity.fluidengine.ui.fluid.FluidAudioTimeline
import dev.antigravity.fluidengine.ui.fluid.FluidAudioWaveform
import dev.antigravity.fluidengine.ui.fluid.FluidRecorderClock
import dev.antigravity.fluidengine.ui.fluid.FluidScrubber
import dev.antigravity.fluidengine.ui.fluid.FluidSettledSemanticsDiagnostics
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaSession
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.nio.file.Files
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Un lettore finto che suona: la posizione la porta avanti la prova, un fotogramma alla volta. */
private class PlayingSession(duration: Long = 58_804L) : FluidMediaSession {
  override var playing: Boolean by mutableStateOf(true)
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

/**
 * R1 (review del 26/11/2026) · L'audio di F8.13 con le due regole di F7.24, con l'accessibilità accesa come sul Tab S9
 * di Alessio («Collegamento a Windows»):
 *  - D1: l'onda e l'onda che scorre non cambiano la semantica a ogni passo del dito, e mentre si ascolta le due onde e
 *    lo scrubber del lettore la dicono a TalkBack al secondo, non al millisecondo;
 *  - D2: l'onda, l'onda che scorre e il cronometro si ridisegnano in uno strato loro;
 *  - D6: il cronometro segue anche l'ascolto in pausa (TalkBack e la larghezza oltre l'ora);
 *  - D8: le scene della galleria non si ricompongono a ogni fotogramma dell'ascolto;
 *  - Muse 15: una trascrizione vuota dice che è vuota.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p13R1AudioTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private lateinit var folder: File

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidSettledSemanticsDiagnostics.resetForTesting()
    // Un servizio di accessibilita' acceso, prima di setContent.
    val shadow = shadowOf(manager())
    shadow.setEnabled(true)
    shadow.setTouchExplorationEnabled(true)
    shadow.setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    compose.mainClock.autoAdvance = false
    folder = Files.createTempDirectory("f8p13r1").toFile()
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    FluidSettledSemanticsDiagnostics.resetForTesting()
    folder.deleteRecursively()
  }

  private fun manager(): AccessibilityManager =
    ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java)

  private fun events(): Int = shadowOf(manager()).sentAccessibilityEvents.count { it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED }

  private fun advance(ms: Long, each: () -> Unit = {}) {
    var left = ms
    while (left > 0) {
      each()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize()) { content() }
      }
    }
    advance(600)
    finger = MaterialFinger(view)
  }

  private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun state(tag: String): String? =
    compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

  /** Un trascinamento lento a passi da 16 ms; [during] guarda a meta' strada, col dito giu' (prima del passo di un secondo). */
  private fun drag(from: Offset, to: Offset, steps: Int = 30, during: () -> Unit = {}) {
    finger.down(from)
    advance(16)
    for (i in 1..steps) {
      finger.move(Offset(from.x + (to.x - from.x) * i / steps, from.y + (to.y - from.y) * i / steps))
      advance(16)
      if (i == steps * 2 / 3) during()
    }
    finger.up(to)
    advance(16)
  }

  private val flat = FluidAudioEnvelope.of(FloatArray(2_941) { if ((it / 50) % 2 == 0) 0.6f else 0.05f })

  // --- D1: il dito ----------------------------------------------------------------------------------------------------

  private fun assertQuietUnderTheFinger(tag: String, from: Offset, to: Offset, settle: Long = 300) {
    val before = events()
    val stateBefore = state(tag)
    var during = -1
    var stateDuring: String? = null
    drag(from, to) {
      during = events() - before
      stateDuring = state(tag)
    }
    advance(settle)
    val after = events() - before
    println("R1 D1 $tag: $during eventi col dito giu', $after in tutto; «$stateBefore» -> «${state(tag)}»")
    assertEquals("$tag: nessun evento di accessibilita' col dito giu'", 0, during)
    assertEquals("$tag: col dito giu' TalkBack ha ancora il valore di partenza", stateBefore, stateDuring)
    assertTrue("$tag: a gesto finito il valore nuovo c'e'", state(tag) != stateBefore && after >= 1)
  }

  @Test
  fun `l'onda della riga sotto il dito non fa scattare l'accessibilita'`() {
    val session = PlayingSession().apply { playing = false; positionMillis = 10_000L }
    show {
      FluidAudioWaveform(
        envelope = flat,
        position = { session.positionMillis },
        onSeek = session::seekTo,
        durationMillis = 58_804L,
        modifier = Modifier.padding(top = 200.dp).padding(horizontal = 20.dp).testTag("riga"),
      )
    }
    val row = bounds("riga")
    assertQuietUnderTheFinger("riga", Offset(row.center.x, row.center.y), Offset(row.center.x + row.width / 3, row.center.y))
  }

  @Test
  fun `l'onda che scorre sotto il dito e nel lancio non fa scattare l'accessibilita'`() {
    val session = PlayingSession().apply { playing = false; positionMillis = 20_000L }
    show {
      FluidAudioTimeline(
        envelope = flat,
        position = { session.positionMillis },
        onSeek = session::seekTo,
        durationMillis = { 58_804L },
        modifier = Modifier.padding(top = 120.dp).testTag("onda"),
      )
    }
    val band = bounds("onda")
    // Un trascinamento veloce alla fine: il lancio continua dopo il rilascio, e conta ancora come il dito.
    assertQuietUnderTheFinger("onda", Offset(band.center.x + 200f, band.center.y), Offset(band.center.x - 200f, band.center.y), settle = 2_500)
    assertEquals("un salto solo", 1, session.seeks.size)
  }

  // --- D1: l'ascolto --------------------------------------------------------------------------------------------------

  /**
   * Tre secondi d'ascolto, un fotogramma alla volta: in quanti fotogrammi la pagina ha mandato eventi di accessibilita',
   * cioe' quante volte Compose ha ricontrollato la semantica e trovato qualcosa da dire (un controllo solo ne manda tre o
   * quattro: lo stato, l'intervallo, il nodo che li contiene).
   */
  private fun eventsWhileListening(tag: String, session: PlayingSession): Int {
    advance(300)
    val before = events()
    val published = FluidSettledSemanticsDiagnostics.publishes
    var frames = 0
    var last = before
    var left = 3_000L
    while (left > 0) {
      session.positionMillis += 16
      advance(16)
      val now = events()
      if (now != last) frames++
      last = now
      left -= 16
    }
    println(
      "R1 D1 $tag in ascolto: $frames fotogrammi con eventi (${events() - before} eventi) e " +
        "${FluidSettledSemanticsDiagnostics.publishes - published} semantiche in 3 s, «${state(tag)}»",
    )
    return frames
  }

  @Test
  fun `ascoltando, l'onda della riga si dice a TalkBack al secondo`() {
    val session = PlayingSession().apply { positionMillis = 5_000L }
    show {
      FluidAudioWaveform(
        envelope = flat,
        position = { session.positionMillis },
        onSeek = session::seekTo,
        durationMillis = 58_804L,
        modifier = Modifier.padding(top = 200.dp).testTag("riga"),
      )
    }
    val count = eventsWhileListening("riga", session)
    assertTrue("fotogrammi con eventi in 3 s d'ascolto: $count", count <= 4)
    assertEquals("0:08 di 0:58", state("riga"))
  }

  @Test
  fun `ascoltando, l'onda che scorre si dice a TalkBack al secondo`() {
    val session = PlayingSession().apply { positionMillis = 5_000L }
    show {
      FluidAudioTimeline(
        envelope = flat,
        position = { session.positionMillis },
        onSeek = session::seekTo,
        durationMillis = { 58_804L },
        modifier = Modifier.padding(top = 120.dp).testTag("onda"),
      )
    }
    val count = eventsWhileListening("onda", session)
    assertTrue("fotogrammi con eventi in 3 s d'ascolto: $count", count <= 4)
    assertEquals("0:08 di 0:58", state("onda"))
  }

  @Test
  fun `ascoltando, lo scrubber del lettore si dice a TalkBack al secondo`() {
    val session = PlayingSession().apply { positionMillis = 5_000L }
    show {
      FluidScrubber(
        position = { session.positionMillis.toFloat() },
        onSeek = { session.seekTo(it.toLong()) },
        valueRange = 0f..58_804f,
        // Senza le scritte sotto la pista: cambiano una volta al secondo per conto loro (sono `Text`), e qui si guarda il
        // valore dello scrubber.
        modifier = Modifier.padding(top = 200.dp).testTag("scrubber"),
      )
    }
    val published = FluidSettledSemanticsDiagnostics.publishes
    val count = eventsWhileListening("scrubber", session)
    val semantics = FluidSettledSemanticsDiagnostics.publishes - published
    // La semantica dello scrubber si rifà una volta al secondo (prima a ogni fotogramma). Fino a F7.31 gli eventi
    // restavano in circa un fotogramma su sei (`count`, solo stampato: 33 in 3 s): li mandava la maniglia di vetro, che si
    // spostava con la traslazione del suo `graphicsLayer` (R1-A, domanda 6). Da F7.31 la maniglia si sposta nel disegno e
    // restano i fotogrammi della semantica (3); la guardia e' `F7p31GlassHandlesTest`.
    assertTrue("semantiche date in 3 s d'ascolto: $semantics", semantics <= 4)
    assertEquals("0:08 di 0:58", state("scrubber"))
  }

  // --- D2: lo strato --------------------------------------------------------------------------------------------------

  /** Vero se il nodo di [tag] ha uno strato suo (un `graphicsLayer`) fra i suoi modificatori. */
  private fun hasOwnLayer(tag: String): Boolean =
    compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.getModifierInfo().any { it.extra != null }

  @Test
  fun `l'onda, l'onda che scorre e il cronometro si ridisegnano in uno strato loro`() {
    val session = PlayingSession()
    show {
      Column {
        FluidAudioWaveform(flat, { session.positionMillis }, session::seekTo, Modifier.testTag("riga"), durationMillis = 58_804L)
        FluidAudioTimeline(flat, { session.positionMillis }, session::seekTo, Modifier.testTag("onda"), durationMillis = { 58_804L })
        FluidRecorderClock(elapsedMillis = { session.positionMillis }, advancing = false, modifier = Modifier.testTag("orologio"))
      }
    }
    for (tag in listOf("riga", "onda", "orologio")) assertTrue("$tag senza uno strato suo", hasOwnLayer(tag))
  }

  // --- D6: il cronometro nell'ascolto in pausa ------------------------------------------------------------------------

  private fun description(tag: String): String? =
    compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

  @Test
  fun `in pausa il cronometro segue l'ascolto, anche oltre l'ora`() {
    val session = PlayingSession(duration = 2 * 3_600_000L).apply { playing = false }
    show {
      // Il pannello in pausa: il tempo e' quello del lettore, e non avanza da solo.
      FluidRecorderClock(elapsedMillis = { session.positionMillis }, advancing = false, modifier = Modifier.padding(top = 200.dp).testTag("orologio"))
    }
    val narrow = bounds("orologio").width
    assertEquals("Durata 0:00", description("orologio"))
    session.positionMillis = 3_725_000L
    advance(200)
    assertEquals("Durata 1:02:05", description("orologio"))
    assertTrue("oltre l'ora il tempo e' piu' largo: ${bounds("orologio").width} contro $narrow", bounds("orologio").width > narrow)
  }

  // --- D8: le scene della galleria ------------------------------------------------------------------------------------

  private fun recompositions(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

  /** Un secondo d'ascolto nella scena aperta: quante volte la composizione si e' rifatta. */
  private fun recompositionsWhileListening(session: PlayingSession): Long {
    advance(600)
    val before = recompositions()
    advance(1_000) { session.positionMillis += 16 }
    return recompositions() - before
  }

  private fun showScene(id: String, session: PlayingSession) {
    show {
      CompositionLocalProvider(LocalF8p13Player provides session) {
        NewElementsPage(onBack = {}, initial = id)
      }
    }
    advance(1_200)
  }

  @Test
  fun `l'onda da scorrere non si ricompone a ogni fotogramma dell'ascolto`() {
    val session = PlayingSession()
    showScene("onda-audio", session)
    val count = recompositionsWhileListening(session)
    println("R1 D8 onda-audio: $count ricomposizioni in un secondo d'ascolto")
    assertTrue("ricomposizioni in un secondo d'ascolto: $count", count <= 6)
  }

  @Test
  fun `l'ascolto dal paragrafo non si ricompone a ogni fotogramma`() {
    val session = PlayingSession()
    showScene("ascolto-paragrafo", session)
    val count = recompositionsWhileListening(session)
    println("R1 D8 ascolto-paragrafo: $count ricomposizioni in un secondo d'ascolto")
    assertTrue("ricomposizioni in un secondo d'ascolto: $count", count <= 6)
  }

  @Test
  fun `il memo aperto non si ricompone a ogni fotogramma dell'ascolto`() {
    // Un memo gia' registrato nella cartella della scena: un secondo di WAV muto e la sua onda accanto.
    val memo = File(folder, "memo-prova.wav")
    memo.writeBytes(ByteArray(44 + 96_000))
    FluidAudioEnvelopes.save(memo, FluidAudioEnvelope.of(FloatArray(50) { 0.4f }))
    val session = PlayingSession(duration = 1_000L)
    F8p13Audio.memoFolder = { folder }
    try {
      showScene("memo-vocali", session)
      advance(600)
      compose.onNode(hasText("Memo prova"), useUnmergedTree = true).performClick()
      advance(600)
      val count = recompositionsWhileListening(session)
      println("R1 D8 memo-vocali: $count ricomposizioni in un secondo d'ascolto")
      assertTrue("ricomposizioni in un secondo d'ascolto: $count", count <= 6)
    } finally {
      F8p13Audio.recorderFactory = { FluidAudioRecorder(it) }
      F8p13Audio.memoFolder = { File(it.filesDir, "novita-memo") }
    }
  }

  // --- Muse 15: la trascrizione vuota ---------------------------------------------------------------------------------

  @Test
  fun `una trascrizione vuota dice che e' vuota`() {
    show {
      FluidTranscriptView(
        transcript = FluidTranscript(emptyList()),
        position = { 0L },
        onSeek = {},
        modifier = Modifier.fillMaxSize().testTag("vuota"),
      )
    }
    assertTrue(
      "nessuna frase per la trascrizione vuota",
      compose.onAllNodes(hasText("Nessuna trascrizione"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(),
    )
  }
}
