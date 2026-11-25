package dev.antigravity.fluidengine.foundation.audio

import dev.antigravity.fluidengine.foundation.FluidFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.13b: le quattro preimpostazioni della qualità e lo spazio di un'ora (scelta di Alessio del 25 novembre 2026). */
class FluidRecordingQualityTest {

  @Test
  fun theFourPresetsFromTheLightestToTheHeaviest() {
    assertEquals(listOf("voce", "standard", "alta", "senza-perdita"), FluidRecordingQuality.All.map { it.id })
    assertEquals(64_000, FluidRecordingQuality.Voice.bitRate)
    assertEquals(96_000, FluidRecordingQuality.Standard.bitRate)
    assertEquals(128_000, FluidRecordingQuality.High.bitRate)
    assertTrue(FluidRecordingQuality.Lossless.lossless)
    assertFalse(FluidRecordingQuality.Standard.lossless)
    val sizes = FluidRecordingQuality.All.map { it.bytesPerHour }
    assertEquals(sizes, sizes.sorted())
  }

  @Test
  fun anHourOfEachOne() {
    val it = Locale.ITALY
    assertEquals("29,1 MB", FluidFormat.bytes(FluidRecordingQuality.Voice.bytesPerHour, locale = it))
    assertEquals("43,6 MB", FluidFormat.bytes(FluidRecordingQuality.Standard.bytesPerHour, locale = it))
    assertEquals("58,2 MB", FluidFormat.bytes(FluidRecordingQuality.High.bytesPerHour, locale = it))
    // 48 000 campioni da 2 byte al secondo per un'ora.
    assertEquals(345_600_000L, FluidRecordingQuality.Lossless.bytesPerHour)
    assertEquals("345,6 MB", FluidFormat.bytes(FluidRecordingQuality.Lossless.bytesPerHour, locale = it))
  }

  @Test
  fun savedIdsComeBackAndUnknownOnesDoNot() {
    FluidRecordingQuality.All.forEach { assertEquals(it, FluidRecordingQuality.byId(it.id)) }
    assertNull(FluidRecordingQuality.byId("vecchia"))
    assertNull(FluidRecordingQuality.byId(null))
    assertEquals(FluidRecordingQuality.Standard, FluidRecordingQuality.of(lossless = false, bitRate = 96_000, sampleRate = 48_000))
    assertEquals(FluidRecordingQuality.Lossless, FluidRecordingQuality.of(lossless = true, bitRate = 12_345, sampleRate = 48_000))
    assertNull(FluidRecordingQuality.of(lossless = false, bitRate = 80_000, sampleRate = 48_000))
  }
}
