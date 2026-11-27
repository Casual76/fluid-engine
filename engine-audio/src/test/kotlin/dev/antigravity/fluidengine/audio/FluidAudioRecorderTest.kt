package dev.antigravity.fluidengine.audio

import dev.antigravity.fluidengine.foundation.audio.FluidRecordingQuality
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F8.13: il registratore con un microfono finto. Il microfono da' prima un copione (toni e silenzi), poi silenzio a tempo
 * vero (20 ms per lettura), come un microfono acceso in una stanza muta.
 */
class FluidAudioRecorderTest {

  private lateinit var folder: File

  @Before
  fun setUp() {
    folder = Files.createTempDirectory("fluid-audio").toFile()
  }

  @After
  fun tearDown() {
    folder.deleteRecursively()
  }

  private val config = FluidRecorderConfig(sampleRate = 48_000, format = FluidRecordingFormat.Wav)

  @Test
  fun recordsPausesResumesAndWritesOneContinuousFile() = runBlocking {
    val mic = ScriptedMicrophone(tone(seconds = 0.5, amplitude = 0.25))
    val recorder = FluidAudioRecorder(mic, config)
    val target = File(folder, "memo.wav")
    assertTrue(recorder.start(target))
    assertTrue(recorder.status.value is FluidRecorderStatus.Recording)
    waitFor { recorder.envelope.size >= 30 }

    recorder.pause()
    val paused = recorder.status.value
    assertTrue(paused is FluidRecorderStatus.Paused && !paused.interrupted)
    assertFalse("il microfono si spegne in pausa", mic.started)
    val atPause = recorder.elapsedMillis()
    Thread.sleep(120)
    assertEquals("in pausa il tempo e' fermo", atPause, recorder.elapsedMillis())
    val samplesAtPause = recorder.envelope.size

    assertTrue(recorder.resume())
    assertTrue(mic.started)
    waitFor { recorder.envelope.size >= samplesAtPause + 10 }
    val recording = recorder.stop()
    assertNotNull(recording)
    recording!!
    assertTrue(recorder.status.value is FluidRecorderStatus.Finished)
    assertEquals(2, mic.starts)

    // Il WAV: un file solo, con l'intestazione giusta e tutti i campioni delle due parti.
    val bytes = target.readBytes()
    val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals("RIFF", String(bytes, 0, 4))
    val dataSize = header.getInt(40)
    assertEquals(bytes.size - 44, dataSize)
    assertEquals(dataSize / 2 * 1000L / 48_000L, recording.durationMillis)
    assertEquals(recording.durationMillis, recorder.elapsedMillis())

    // L'onda: il tono e' alto, il silenzio e' zero; ed e' salvata accanto al file.
    assertTrue(recording.envelope[5] > 0.5f)
    assertEquals(0f, recording.envelope[recording.envelope.size - 2], 0f)
    val side = FluidAudioEnvelopes.sidecar(target)
    assertTrue(side.isFile)
    val reread = FluidAudioEnvelopes.load(target)!!
    assertEquals(recording.envelope.size, reread.size)
  }

  @Test
  fun anInterruptionPausesAndCanBeResumed() = runBlocking {
    val mic = ScriptedMicrophone(tone(seconds = 0.2, amplitude = 0.3))
    val recorder = FluidAudioRecorder(mic, config)
    assertTrue(recorder.start(File(folder, "chiamata.wav")))
    waitFor { recorder.envelope.size >= 5 }
    mic.onInterrupted!!.invoke()
    val status = recorder.status.value
    assertTrue("$status", status is FluidRecorderStatus.Paused && status.interrupted)
    assertTrue(recorder.resume())
    assertTrue(recorder.status.value is FluidRecorderStatus.Recording)
    recorder.discard()
    assertTrue(recorder.status.value is FluidRecorderStatus.Idle)
    assertFalse(File(folder, "chiamata.wav").exists())
  }

  @Test
  fun aMicrophoneThatDoesNotStartFails() {
    val mic = ScriptedMicrophone(ShortArray(0), startsOk = false)
    val recorder = FluidAudioRecorder(mic, config)
    assertFalse(recorder.start(File(folder, "no.wav")))
    val status = recorder.status.value
    assertTrue("$status", status is FluidRecorderStatus.Failed && status.reason == FluidMicrophoneFailure.Busy)
  }

  @Test
  fun aLostMicrophoneKeepsTheFileAndPauses() = runBlocking {
    val mic = ScriptedMicrophone(tone(seconds = 0.2, amplitude = 0.3), failAfterScript = true)
    val recorder = FluidAudioRecorder(mic, config)
    assertTrue(recorder.start(File(folder, "perso.wav")))
    waitFor { recorder.status.value is FluidRecorderStatus.Paused }
    val status = recorder.status.value as FluidRecorderStatus.Paused
    assertTrue(status.interrupted)
    val recording = recorder.stop()!!
    assertTrue(recording.durationMillis in 150L..260L)
  }

  /** Le due misure dell'intestazione di un WAV sul disco: la dimensione del RIFF e quella dei dati. */
  private fun sizesOnDisk(file: File): Pair<Int, Int> {
    val header = ByteBuffer.wrap(file.readBytes(), 0, 44).order(ByteOrder.LITTLE_ENDIAN)
    return header.getInt(4) to header.getInt(40)
  }

  @Test
  fun aWavBeingRecordedKeepsItsSizesOnDiskIfTheProcessDies() = runBlocking {
    // R1 (Muse 4): una lezione registrata e il processo che muore a meta'. Niente stop(): quel che resta e' il file sul disco,
    // che deve dire quanto audio contiene (al piu' un secondo indietro).
    val mic = ScriptedMicrophone(tone(seconds = 2.6, amplitude = 0.25))
    val recorder = FluidAudioRecorder(mic, config)
    val target = File(folder, "lezione.wav")
    assertTrue(recorder.start(target))
    waitFor { recorder.envelope.size >= 125 }
    val (riff, data) = sizesOnDisk(target)
    val onDisk = target.length() - 44
    println("R1 Muse 4: $data byte di dati nell'intestazione, $onDisk sul disco")
    assertTrue("l'intestazione dice $data byte su $onDisk", data >= 2 * 48_000 * 2)
    assertTrue(data <= onDisk)
    assertEquals(36 + data, riff)
    recorder.discard()
  }

  @Test
  fun afterAFailedCaptureResetAndStartThrowTheHalfFileAway() = runBlocking {
    // R1 (Muse 13, scelta di Alessio del 26/11/2026: b): la cattura fallisce da sola (Failed). Chi torna a riposo o
    // ricomincia senza stop() non lascia il file aperto, e il file a meta' si butta. Solo stop() lo chiude e lo consegna.
    val mic = ScriptedMicrophone(tone(seconds = 0.3, amplitude = 0.3), failAfterScript = true, failWith = FluidMicrophoneFailure.Unknown)
    val recorder = FluidAudioRecorder(mic, config)
    val first = File(folder, "prima.wav")
    assertTrue(recorder.start(first))
    waitFor { recorder.status.value is FluidRecorderStatus.Failed }
    recorder.reset()
    assertFalse("reset() lascia il file a meta'", first.exists())
    assertTrue(recorder.status.value is FluidRecorderStatus.Idle)

    // Lo stesso con start(): la registrazione nuova butta quella fallita.
    val again = ScriptedMicrophone(tone(seconds = 0.2, amplitude = 0.3), failAfterScript = true, failWith = FluidMicrophoneFailure.Unknown)
    val second = FluidAudioRecorder(again, config)
    val failed = File(folder, "seconda.wav")
    assertTrue(second.start(failed))
    waitFor { second.status.value is FluidRecorderStatus.Failed }
    assertTrue(second.start(File(folder, "terza.wav")))
    assertFalse("start() lascia il file a meta'", failed.exists())
    second.discard()

    // stop() dopo una cattura fallita invece consegna quel che c'e', chiuso e leggibile.
    val third = FluidAudioRecorder(
      ScriptedMicrophone(tone(seconds = 0.3, amplitude = 0.3), failAfterScript = true, failWith = FluidMicrophoneFailure.Unknown),
      config,
    )
    val kept = File(folder, "quarta.wav")
    assertTrue(third.start(kept))
    waitFor { third.status.value is FluidRecorderStatus.Failed }
    val recording = third.stop()
    assertNotNull(recording)
    assertEquals(0.3 * 48_000 * 2, sizesOnDisk(kept).second.toDouble(), 0.0)
  }

  @Test
  fun trimSilenceCutsHeadAndTail() = runBlocking {
    val script = silence(1.0) + tone(seconds = 0.6, amplitude = 0.3)
    val mic = ScriptedMicrophone(script)
    val recorder = FluidAudioRecorder(mic, config.copy(trimSilence = true))
    val target = File(folder, "taglio.wav")
    assertTrue(recorder.start(target))
    // Il copione, piu' un secondo di silenzio in coda.
    waitFor { recorder.envelope.size >= 130 }
    val recording = recorder.stop()!!
    assertTrue(recording.trimmed)
    // 0,6 s di voce con 300 ms per lato.
    assertTrue("durata ${recording.durationMillis}", recording.durationMillis in 1_150L..1_250L)
    val header = ByteBuffer.wrap(target.readBytes(), 0, 44).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(target.length() - 44, header.getInt(40).toLong())
    assertEquals(recording.envelope.size.toLong(), recording.durationMillis / 20 + if (recording.durationMillis % 20 == 0L) 0 else 1)
  }

  @Test
  fun wavEnvelopeReadsPcmFormats() {
    // Un secondo stereo a 16 bit: il canale sinistro un tono, il destro muto. Mescolati, meta' ampiezza.
    val rate = 8_000
    val pcm = ByteBuffer.allocate(rate * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until rate) {
      pcm.putShort((sin(2 * PI * 440 * i / rate) * 16_000).toInt().toShort())
      pcm.putShort(0)
    }
    val file = wavHeader(pcm.capacity(), rate, channels = 2) + pcm.array()
    val envelope = FluidAudioEnvelopes.fromWav(ByteArrayInputStream(file), bucketMillis = 20)
    assertEquals(50, envelope.size)
    assertTrue(envelope[10] > 0.5f)
    assertEquals(1_000L, envelope.durationMillis)
    val slice = FluidAudioEnvelopes.slice(envelope, 200L, 400L)
    assertEquals(10, slice.size)
  }

  @Test
  fun theDefaultIsStandardAndAQualityChangesOneRecording() = runBlocking {
    val defaults = FluidRecorderConfig()
    assertEquals(96_000, defaults.aacBitRate)
    assertEquals(FluidRecordingFormat.Aac, defaults.format)
    assertEquals(FluidRecordingQuality.Standard, defaults.quality)
    assertEquals(64_000, defaults.with(FluidRecordingQuality.Voice).aacBitRate)
    assertEquals(FluidRecordingQuality.High, defaults.with(FluidRecordingQuality.High).quality)
    assertEquals(FluidRecordingFormat.Wav, defaults.with(FluidRecordingQuality.Lossless).format)

    // Il registratore e' di serie in AAC; la registrazione «senza perdita» scrive un WAV (e la prossima torna AAC).
    val recorder = FluidAudioRecorder(ScriptedMicrophone(tone(seconds = 0.2, amplitude = 0.3)), FluidRecorderConfig())
    val target = File(folder, "senza-perdita.wav")
    assertTrue(recorder.start(target, FluidRecordingQuality.Lossless))
    assertEquals(FluidRecordingFormat.Wav, recorder.recordingConfig.format)
    waitFor { recorder.envelope.size >= 10 }
    val recording = recorder.stop()!!
    assertEquals(FluidRecordingFormat.Wav, recording.format)
    assertEquals("RIFF", String(target.readBytes(), 0, 4))
    assertEquals(FluidRecordingFormat.Aac, recorder.config.format)
  }

  private suspend fun waitFor(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
      check(System.currentTimeMillis() < end) { "non e' successo in tempo" }
      kotlinx.coroutines.delay(5)
    }
  }

  private fun tone(seconds: Double, amplitude: Double): ShortArray =
    ShortArray((seconds * 48_000).toInt()) { (sin(2 * PI * 220 * it / 48_000) * amplitude * 32767).toInt().toShort() }

  private fun silence(seconds: Double): ShortArray = ShortArray((seconds * 48_000).toInt())

  /** Un microfono finto: il copione senza attese, poi silenzio a tempo vero (o un errore). */
  private class ScriptedMicrophone(
    private val script: ShortArray,
    private val startsOk: Boolean = true,
    private val failAfterScript: Boolean = false,
    /** Il perché di un errore: di serie quello di un microfono perso (o occupato, se non parte). */
    private val failWith: FluidMicrophoneFailure? = null,
  ) : FluidPcmSource {
    @Volatile var started = false
    @Volatile var starts = 0
    private var at = 0
    override var onInterrupted: (() -> Unit)? = null
    override val failure: FluidMicrophoneFailure
      get() = failWith ?: if (startsOk) FluidMicrophoneFailure.Lost else FluidMicrophoneFailure.Busy

    override fun start(sampleRate: Int): Boolean {
      if (!startsOk) return false
      started = true
      starts++
      return true
    }

    override fun read(frame: ShortArray): Int {
      if (at < script.size) {
        val n = minOf(frame.size, script.size - at)
        System.arraycopy(script, at, frame, 0, n)
        at += n
        return n
      }
      if (failAfterScript) return -1
      Thread.sleep(20)
      frame.fill(0)
      return frame.size
    }

    override fun stop() {
      started = false
    }
  }
}
