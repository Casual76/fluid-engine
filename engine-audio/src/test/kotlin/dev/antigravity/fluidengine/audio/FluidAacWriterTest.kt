package dev.antigravity.fluidengine.audio

import android.media.MediaCodec
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaCodec


/** Il codificatore di Robolectric, che in più conta i `release()` (quello di serie non li vede). */
@Implements(MediaCodec::class)
class CountingMediaCodecShadow : ShadowMediaCodec() {
  @Implementation
  protected fun native_release() {
    releases.incrementAndGet()
  }

  companion object {
    val releases = AtomicInteger()
  }
}

// Il contenitore che conta e sa fallire è `CountingMediaMuxerShadow`, in Java (src/test/java).

/**
 * R1 (review del 26/11/2026, Sol): l'AAC del registratore con il codificatore e il contenitore di Robolectric. Se una
 * fase dell'apertura o della chiusura fallisce, il codificatore e il contenitore si liberano lo stesso, e il registratore
 * lo dice (`Failed`) invece di lanciare o di restare a metà.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [CountingMediaCodecShadow::class, CountingMediaMuxerShadow::class])
class FluidAacWriterTest {

  private lateinit var folder: File

  @Before
  fun setUp() {
    folder = Files.createTempDirectory("fluid-aac").toFile()
    CountingMediaCodecShadow.releases.set(0)
    CountingMediaMuxerShadow.releases.set(0)
    CountingMediaMuxerShadow.failStop = false
  }

  @After
  fun tearDown() {
    CountingMediaMuxerShadow.failStop = false
    folder.deleteRecursively()
  }

  private val config = FluidRecorderConfig(format = FluidRecordingFormat.Aac)

  @Test
  fun aContainerThatDoesNotOpenReleasesTheEncoder() {
    // La destinazione è una cartella: il contenitore non si apre, dopo che il codificatore è già nato.
    val target = File(folder, "cartella.m4a").apply { mkdirs() }
    val recorder = FluidAudioRecorder(ToneThenSilence(), config)
    assertFalse(recorder.start(target))
    assertTrue("${recorder.status.value}", recorder.status.value is FluidRecorderStatus.Failed)
    assertEquals("il codificatore liberato", 1, CountingMediaCodecShadow.releases.get())
  }

  @Test
  fun aContainerThatDoesNotCloseReleasesEverythingAndSaysSo() = runBlocking {
    val recorder = FluidAudioRecorder(ToneThenSilence(), config)
    val target = File(folder, "memo.m4a")
    assertTrue(recorder.start(target))
    val end = System.currentTimeMillis() + 5_000L
    while (recorder.envelope.size < 10) {
      check(System.currentTimeMillis() < end) { "il registratore non registra" }
      kotlinx.coroutines.delay(5)
    }
    CountingMediaMuxerShadow.failStop = true
    val result = runCatching { recorder.stop() }
    assertTrue("stop() lancia: ${result.exceptionOrNull()}", result.isSuccess)
    assertNull(result.getOrNull())
    assertTrue("${recorder.status.value}", recorder.status.value is FluidRecorderStatus.Failed)
    assertEquals("il contenitore liberato", 1, CountingMediaMuxerShadow.releases.get())
    assertEquals("il codificatore liberato", 1, CountingMediaCodecShadow.releases.get())
    // Il registratore non è rimasto a metà: se ne comincia un'altra.
    CountingMediaMuxerShadow.failStop = false
    assertTrue(recorder.start(File(folder, "altro.m4a")))
    recorder.discard()
  }

  /** Un microfono finto: un tono breve, poi silenzio a tempo vero (20 ms per lettura). */
  private class ToneThenSilence : FluidPcmSource {
    private val tone = ShortArray(9_600) { (sin(2 * PI * 220 * it / 48_000) * 0.3 * 32767).toInt().toShort() }
    private var at = 0
    override var onInterrupted: (() -> Unit)? = null
    override fun start(sampleRate: Int): Boolean = true
    override fun read(frame: ShortArray): Int {
      if (at < tone.size) {
        val n = minOf(frame.size, tone.size - at)
        System.arraycopy(tone, at, frame, 0, n)
        at += n
        return n
      }
      Thread.sleep(20)
      frame.fill(0)
      return frame.size
    }
    override fun stop() = Unit
  }
}
