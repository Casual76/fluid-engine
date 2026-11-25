package dev.antigravity.fluidengine.foundation.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** F8.13: l'inviluppo, la scala che non satura e i silenzi. */
class FluidAudioEnvelopeTest {

  @Test
  fun peakIsTheMaximumOfTheTouchedBuckets() {
    val envelope = FluidAudioEnvelope.of(floatArrayOf(0.1f, 0.5f, 0.2f, 0.9f, 0.3f), bucketMillis = 20)
    assertEquals(100L, envelope.durationMillis)
    assertEquals(0.5f, envelope.peak(0, 40), 0f)
    // 40..60 tocca solo il terzo valore: un intervallo che finisce sul confine non tocca il dopo.
    assertEquals(0.2f, envelope.peak(40, 60), 0f)
    assertEquals(0.9f, envelope.peak(41, 70), 0f)
    assertEquals(0f, envelope.peak(200, 300), 0f)
    assertEquals(0f, envelope.peak(50, 50), 0f)
  }

  @Test
  fun pyramidAgreesWithAScanOnLongRanges() {
    val values = FloatArray(100_000) { i -> ((i * 7919L) % 1000L).toFloat() / 1000f * if (i == 54_321) 0f else 0.9f }
    values[54_321] = 1f
    val envelope = FluidAudioEnvelope.of(values)
    // Il massimo vero in un tratto lungo: la piramide puo' allargare i bordi, mai perdere il centro.
    assertEquals(1f, envelope.peak(54_000L * 20, 55_000L * 20), 0f)
    val from = 10_000
    val to = 30_000
    var exact = 0f
    for (i in from until to) exact = max(exact, values[i])
    val fast = envelope.peak(from * 20L, to * 20L)
    assertTrue("piramide $fast contro $exact", fast >= exact)
  }

  @Test
  fun valuesAreClampedAndNanIsSilence() {
    val envelope = FluidAudioEnvelope.of(floatArrayOf(-1f, 2f, Float.NaN))
    assertEquals(0f, envelope[0], 0f)
    assertEquals(1f, envelope[1], 0f)
    assertEquals(0f, envelope[2], 0f)
    assertEquals(0f, envelope[3], 0f)
  }

  @Test
  fun writeAndReadKeepTheShape() {
    val envelope = FluidAudioEnvelope.of(FloatArray(500) { it / 499f }, bucketMillis = 40)
    val bytes = ByteArrayOutputStream().also { envelope.write(it) }.toByteArray()
    assertEquals(12 + 500, bytes.size)
    val back = FluidAudioEnvelope.read(ByteArrayInputStream(bytes))
    assertEquals(40, back.bucketMillis)
    assertEquals(500, back.size)
    for (i in 0 until 500) assertEquals(envelope[i], back[i], 1f / 255f)
  }

  @Test
  fun readRefusesOtherFiles() {
    try {
      FluidAudioEnvelope.read(ByteArrayInputStream("RIFF....WAVE".toByteArray()))
      fail("un WAV non e' un inviluppo")
    } catch (expected: IOException) {
    }
  }

  @Test
  fun growingEnvelopeCrossesChunksAndAnswersPeaks() {
    val growing = FluidGrowingAudioEnvelope()
    val total = FluidGrowingAudioEnvelope.ChunkSize * 2 + 100
    for (i in 0 until total) growing.append(if (i == FluidGrowingAudioEnvelope.ChunkSize + 7) 0.8f else 0.1f)
    assertEquals(total, growing.size)
    assertEquals(0.8f, growing[FluidGrowingAudioEnvelope.ChunkSize + 7], 0f)
    assertEquals(0.8f, growing.peak(0, growing.durationMillis), 0f)
    assertEquals(0.1f, growing.peak(0, 20L * FluidGrowingAudioEnvelope.ChunkSize), 0f)
    val snapshot = growing.snapshot()
    assertEquals(total, snapshot.size)
    growing.clear()
    assertEquals(0, growing.size)
    assertEquals(total, snapshot.size)
  }

  @Test
  fun aReaderNeverSeesAValueBeyondWhatWasPublished() {
    val growing = FluidGrowingAudioEnvelope()
    val stop = AtomicBoolean(false)
    val started = CountDownLatch(1)
    var bad = false
    val reader = Thread {
      started.countDown()
      while (!stop.get()) {
        val n = growing.size
        if (n > 0 && growing[n - 1] != 0.5f) bad = true
      }
    }
    reader.start()
    started.await()
    repeat(20_000) { growing.append(0.5f) }
    stop.set(true)
    reader.join()
    assertFalse("un lettore ha visto un valore non ancora scritto", bad)
  }

  @Test
  fun levelScaleIsMonotonicAndNeverReachesTheCeiling() {
    var previous = -1f
    var db = -80f
    while (db <= 0f) {
      val level = FluidAudioLevel.fromDb(db)
      assertTrue("$db dB: $level < $previous", level >= previous)
      previous = level
      db += 0.5f
    }
    assertEquals(0f, FluidAudioLevel.fromDb(-60f), 0f)
    val full = FluidAudioLevel.fromDb(0f)
    assertTrue("fondo scala $full", full in 0.9f..0.95f)
    // Una voce normale a mezzo metro (-25 dBFS) sta a meta' altezza, una forte (-12) ancora sotto il soffitto.
    assertTrue(FluidAudioLevel.fromDb(-25f) in 0.45f..0.6f)
    assertTrue(FluidAudioLevel.fromDb(-12f) < 0.85f)
  }

  @Test
  fun rmsAndPeakOfPcm() {
    val square = ShortArray(160) { if (it % 2 == 0) 16384 else -16384 }
    assertEquals(0.5f, FluidAudioLevel.rms(square), 1e-4f)
    assertEquals(0.5f, FluidAudioLevel.peak(square), 1e-4f)
    assertFalse(FluidAudioLevel.isClipping(0.5f))
    assertTrue(FluidAudioLevel.isClipping(FluidAudioLevel.peak(shortArrayOf(Short.MIN_VALUE))))
    assertEquals(0f, FluidAudioLevel.rms(ShortArray(0)), 0f)
  }

  @Test
  fun silenceRangesAndTrim() {
    // 1 s di silenzio, 2 s di voce, 1,5 s di silenzio, 1 s di voce, 0,4 s di silenzio (troppo corto per contare).
    val values = ArrayList<Float>()
    fun add(seconds: Double, level: Float) = repeat((seconds * 50).toInt()) { values += level }
    add(1.0, 0.05f); add(2.0, 0.6f); add(1.5, 0.05f); add(1.0, 0.55f); add(0.4, 0.05f)
    val envelope = FluidAudioEnvelope.of(values.toFloatArray())
    val ranges = FluidAudioSilence.ranges(envelope)
    assertEquals(listOf(0L until 1_000L, 3_000L until 4_500L), ranges)
    val trim = FluidAudioSilence.trimBounds(envelope)!!
    assertEquals(700L, trim.first)
    // L'ultima voce finisce a 5,5 s: piu' 300 ms, dentro i 5,9 s dell'audio.
    assertEquals(5_799L, trim.last)
    assertNull(FluidAudioSilence.trimBounds(FluidAudioEnvelope.of(FloatArray(100) { 0.02f })))
  }

  @Test
  fun thresholdFollowsANoisyRoom() {
    val quiet = FluidAudioEnvelope.of(FloatArray(1000) { if (it % 10 == 0) 0.6f else 0.02f })
    assertEquals(FluidAudioSilence.MinThreshold, FluidAudioSilence.threshold(quiet), 0.02f)
    val fan = FluidAudioEnvelope.of(FloatArray(1000) { if (it % 10 == 0) 0.7f else 0.4f })
    assertTrue(FluidAudioSilence.threshold(fan) > 0.45f)
  }
}
