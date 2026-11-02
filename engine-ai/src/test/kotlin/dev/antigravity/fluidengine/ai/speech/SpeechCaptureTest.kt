package dev.antigravity.fluidengine.ai.speech

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** L'orologio della prova: avanza di un frame a ogni lettura del microfono finto, cosi' il tempo e' quello dei campioni. */
private class SpeechClock {
  @Volatile
  var now = 1_000_000L
}

/**
 * Un microfono finto: a ogni lettura riempie un frame da 20 ms con un'onda quadra di ampiezza
 * `amplitude(indice)` (il suo RMS e' proprio l'ampiezza), e fa avanzare l'orologio di 20 ms.
 * [reads] puo' dire al posto del frame un `0` (niente per ora) o un `-1` (errore).
 */
private class FakeMic(
  private val clock: SpeechClock,
  private val amplitude: (Int) -> Int,
  private val reads: (Int) -> Int? = { null },
  private val onFrame: (Int) -> Unit = {},
  private val startsOk: Boolean = true,
  override val failure: MicrophoneFailure = MicrophoneFailure.UNKNOWN,
) : PcmSource {
  @Volatile var starts = 0
  @Volatile var stops = 0
  @Volatile var count = 0

  override fun start(sampleRate: Int): Boolean {
    starts++
    return startsOk
  }

  override fun read(frame: ShortArray): Int {
    val index = count++
    clock.now += 20
    onFrame(index)
    reads(index)?.let { return it }
    val a = amplitude(index)
    for (i in frame.indices) frame[i] = (if (i % 2 == 0) a else -a).toShort()
    return frame.size
  }

  override fun stop() {
    stops++
  }
}

/** Le ampiezze per tratti: `(venti frame, 40)`, `(trenta frame, 3000)`; dopo l'ultimo tratto, [then] per sempre. */
private fun script(vararg segments: Pair<Int, Int>, then: Int = QUIET): (Int) -> Int = { index ->
  var rest = index
  var value = then
  for ((frames, amplitude) in segments) {
    if (rest < frames) {
      value = amplitude
      break
    }
    rest -= frames
  }
  value
}

private const val QUIET = 40
private const val VOICE = 3_000

/**
 * La cattura vocale con rilevamento del silenzio, provata senza microfono: frame sintetici da 20 ms
 * e un orologio che avanza coi campioni. Sono le soglie rifatte il 2026-09-03 dopo la prova su un
 * telefono vero: qui si tengono ferme, perche' non si rompano di nuovo in silenzio.
 */
class SpeechCaptureTest {

  private val clock = SpeechClock()
  private val dir: File = Files.createTempDirectory("speech").toFile()
  private val target = File(dir, "ask.wav")

  private lateinit var capture: SpeechCapture

  private fun record(mic: FakeMic, config: SpeechCapture.VadConfig = SpeechCapture.VadConfig()): List<SpeechCapture.Event> {
    capture = SpeechCapture(mic, config, clock = { clock.now })
    return runBlocking { capture.record(target).toList() }
  }

  private fun mic(
    amplitude: (Int) -> Int,
    reads: (Int) -> Int? = { null },
    stopAtFrame: Int? = null,
    startsOk: Boolean = true,
    failure: MicrophoneFailure = MicrophoneFailure.UNKNOWN,
  ) = FakeMic(clock, amplitude, reads, onFrame = { if (it == stopAtFrame) capture.stopNow() }, startsOk = startsOk, failure = failure)

  // ------------------------------------------------------------------------------ la fine giusta

  @Test
  fun `una frase e poi silenzio finisce da sola, col WAV tagliato all'ultima parola piu' un margine`() {
    val mic = mic(script(20 to QUIET, 30 to VOICE))
    val events = record(mic)

    assertEquals(1, events.count { it is SpeechCapture.Event.SpeechStarted })
    val finished = events.last() as SpeechCapture.Event.Finished
    assertEquals(SpeechCapture.EndReason.SILENCE, finished.reason)
    // 20 frame di silenzio, 30 di voce, 60 di silenzio (1200 ms) e la cattura si ferma.
    assertTrue("letti ${mic.count} frame", mic.count in 105..115)
    assertEquals("il microfono si ferma, una volta", 1, mic.stops)
    // Tagliato a 50 frame di audio (16000 campioni) piu' 300 ms di coda: 1300 ms.
    assertEquals(1_300L, finished.durationMillis)
    assertEquals(target, finished.file)
    assertEquals(44L + 20_800 * 2, target.length())
  }

  @Test
  fun `il livello segue la voce e dice quando e' parlato`() {
    val events = record(mic(script(20 to QUIET, 30 to VOICE)))
    val levels = events.filterIsInstance<SpeechCapture.Event.Level>()
    assertTrue("un livello per frame letto: ${levels.size}", levels.size in 105..115)
    val quiet = levels.first()
    val loud = levels[30]
    assertTrue("il silenzio e' sotto 0,1: ${quiet.level}", quiet.level < 0.1f)
    assertTrue("la voce e' sopra 0,5: ${loud.level}", loud.level > 0.5f)
    assertFalse(quiet.speaking)
    assertTrue(loud.speaking)
    assertTrue("il tempo del livello e' quello dei campioni", levels.zipWithNext().all { (a, b) -> b.elapsedMillis - a.elapsedMillis == 20L })
  }

  @Test
  fun `una frase troppo breve non e' una domanda`() {
    val events = record(mic(script(20 to QUIET, 10 to VOICE)))
    val last = events.last() as SpeechCapture.Event.Empty
    assertEquals(SpeechCapture.EmptyReason.TOO_SHORT, last.reason)
    assertTrue("niente file: non c'e' niente da trascrivere", !target.exists())
  }

  @Test
  fun `se nessuno parla e si ferma a mano, non ha sentito niente`() {
    val events = record(mic(script(), stopAtFrame = 49))
    val last = events.last() as SpeechCapture.Event.Empty
    assertEquals(SpeechCapture.EmptyReason.NOTHING_HEARD, last.reason)
    assertEquals(0, events.count { it is SpeechCapture.Event.SpeechStarted })
    assertTrue(!target.exists())
  }

  @Test
  fun `una voce bassa fermata a mano non si butta, l'audio c'e'`() {
    // 150 e' sotto la soglia di ingresso (180): il rilevatore non se ne accorge, ma chi tocca
    // "ferma" dopo aver parlato non deve sentirsi dire "non ho sentito nulla".
    val mic = mic(script(20 to QUIET, 30 to 150), stopAtFrame = 49)
    val events = record(mic)
    assertEquals(0, events.count { it is SpeechCapture.Event.SpeechStarted })
    val finished = events.last() as SpeechCapture.Event.Finished
    assertEquals(SpeechCapture.EndReason.MANUAL, finished.reason)
    assertEquals("tutto l'audio, dall'inizio", 1_000L, finished.durationMillis)
    assertTrue(target.exists())
  }

  @Test
  fun `si ferma al tetto di durata`() {
    val config = SpeechCapture.VadConfig(maxDurationMillis = 1_000)
    val events = record(mic(script(20 to QUIET, 100 to VOICE)), config)
    val finished = events.last() as SpeechCapture.Event.Finished
    assertEquals(SpeechCapture.EndReason.MAX_DURATION, finished.reason)
    assertEquals(1_000L, finished.durationMillis)
  }

  // ----------------------------------------------------------------------------- il rumore di fondo

  @Test
  fun `in una stanza rumorosa il parlato forte si riconosce lo stesso`() {
    val noise = 250
    val events = record(mic(script(20 to noise, 30 to VOICE, then = noise)))
    assertEquals(1, events.count { it is SpeechCapture.Event.SpeechStarted })
    assertEquals(SpeechCapture.EndReason.SILENCE, (events.last() as SpeechCapture.Event.Finished).reason)
  }

  @Test
  fun `il solo rumore di fondo non e' parlato, nemmeno fermando a mano`() {
    val noise = 250
    val events = record(mic(script(then = noise), stopAtFrame = 59))
    assertEquals(0, events.count { it is SpeechCapture.Event.SpeechStarted })
    assertEquals(SpeechCapture.EmptyReason.NOTHING_HEARD, (events.last() as SpeechCapture.Event.Empty).reason)
  }

  // --------------------------------------------------------------------------- il microfono che manca

  @Test
  fun `un microfono che non parte dice perche'`() {
    val mic = mic(script(), startsOk = false, failure = MicrophoneFailure.BUSY)
    val events = record(mic)
    val failed = events.single() as SpeechCapture.Event.Failed
    assertEquals(MicrophoneFailure.BUSY, (failed.cause as MicrophoneException).failure)
    assertEquals("non ha mai letto", 0, mic.count)
  }

  @Test
  fun `un microfono che smette di mandare campioni e' perso`() {
    val mic = mic(script(), reads = { if (it >= 10) 0 else null })
    val events = record(mic)
    val failed = events.last() as SpeechCapture.Event.Failed
    assertEquals(MicrophoneFailure.LOST, (failed.cause as MicrophoneException).failure)
    assertEquals("dopo venticinque letture vuote di fila", 35, mic.count)
    assertEquals(1, mic.stops)
  }

  @Test
  fun `un frame vuoto ogni tanto non e' un guasto`() {
    val mic = mic(script(20 to QUIET, 30 to VOICE), reads = { if (it % 7 == 3 && it > 0 && it < 40) 0 else null })
    val events = record(mic)
    assertTrue("la cattura e' arrivata in fondo", events.last() is SpeechCapture.Event.Finished)
  }

  @Test
  fun `una lettura che fallisce dice perche'`() {
    val mic = mic(script(), reads = { if (it == 7) -1 else null }, failure = MicrophoneFailure.BUSY)
    val events = record(mic)
    val failed = events.last() as SpeechCapture.Event.Failed
    assertEquals(MicrophoneFailure.BUSY, (failed.cause as MicrophoneException).failure)
    assertEquals(1, mic.stops)
  }

  @Test
  fun `la cancellazione ferma il microfono e non scrive niente`() = runBlocking {
    val mic = mic(script(20 to QUIET, 100 to VOICE))
    capture = SpeechCapture(mic, clock = { clock.now })
    val first = capture.record(target).take(5).toList()
    assertEquals(5, first.size)
    // Il flusso e' stato chiuso a monte: il microfono si ferma e il file non c'e'.
    assertEquals(1, mic.stops)
    assertTrue(!target.exists())
  }

  // ------------------------------------------------------------------------------------- il WAV

  @Test
  fun `l'intestazione del WAV e' little-endian e dice cio' che c'e' dentro`() {
    val header = WavWriter.header(dataSize = 640, sampleRate = 16_000)
    assertEquals(44, header.size)
    val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    fun tag(at: Int) = String(header, at, 4, Charsets.US_ASCII)
    assertEquals("RIFF", tag(0))
    assertEquals(36 + 640, buffer.getInt(4))
    assertEquals("WAVE", tag(8))
    assertEquals("fmt ", tag(12))
    assertEquals(16, buffer.getInt(16))
    assertEquals("PCM", 1, buffer.getShort(20).toInt())
    assertEquals("mono", 1, buffer.getShort(22).toInt())
    assertEquals(16_000, buffer.getInt(24))
    assertEquals(32_000, buffer.getInt(28))
    assertEquals(2, buffer.getShort(32).toInt())
    assertEquals(16, buffer.getShort(34).toInt())
    assertEquals("data", tag(36))
    assertEquals(640, buffer.getInt(40))
  }

  @Test
  fun `il WAV scritto ha l'intestazione e i campioni, nell'ordine`() {
    val pcm = shortArrayOf(1, -2, 300, -400, 32_767, -32_768)
    val file = File(dir, "sotto/cartella/nuova.wav")
    WavWriter.write(file, pcm, samples = 4, sampleRate = 16_000)
    assertTrue("la cartella si crea", file.parentFile!!.isDirectory)
    val bytes = file.readBytes()
    assertEquals(44 + 4 * 2, bytes.size)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(8, buffer.getInt(40))
    assertEquals(listOf<Short>(1, -2, 300, -400), (0 until 4).map { buffer.getShort(44 + it * 2) })
  }

  @Test
  fun `un frame della cattura e' di 20 ms a 16 kHz`() {
    assertEquals(320, SpeechCapture.VadConfig().frameSamples)
    assertEquals(160, SpeechCapture.VadConfig(sampleRate = 8_000).frameSamples)
  }
}
