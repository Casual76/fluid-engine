package dev.antigravity.fluidengine.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowTrace

/** F8.24 · Le tracce nella build di debug: gli intervalli ci sono, e si chiudono anche quando il blocco lancia. */
@RunWith(RobolectricTestRunner::class)
class FluidTraceDebugTest {
  @Before
  fun setUp() {
    ShadowTrace.reset()
    ShadowTrace.setEnabled(true)
  }

  @Test
  fun theDebugBuildWritesSections() {
    assertTrue(FluidTrace.isAvailable)
    val value = FluidTrace.section(FluidTraceNames.Decode) { 42 }
    assertEquals(42, value)
    assertEquals(listOf("pampa:decodifica"), ShadowTrace.getPreviousSections().toList())
    assertTrue(ShadowTrace.getCurrentSections().isEmpty())
  }

  @Test
  fun aSectionClosesWhenTheBlockThrows() {
    runCatching { FluidTrace.section(FluidTraceNames.ReadFile) { error("rotto") } }
    assertTrue(ShadowTrace.getCurrentSections().isEmpty())
    assertEquals(listOf("pampa:leggi-file"), ShadowTrace.getPreviousSections().toList())
  }

  @Test
  fun asyncSectionsCrossThreadsAndClose() = runBlocking {
    val result = FluidTrace.async(FluidTraceNames.OpenItem) { withContext(Dispatchers.IO) { "album" } }
    assertEquals("album", result)
    assertTrue(ShadowTrace.getCurrentAsyncSections().isEmpty())
    assertEquals(listOf("pampa:apri"), ShadowTrace.getPreviousAsyncSections().map { it.sectionName })
    runCatching { FluidTrace.async(FluidTraceNames.Network) { error("rete") } }
    assertTrue(ShadowTrace.getCurrentAsyncSections().isEmpty())
  }

  @Test
  fun anIntervalEndsOnce() {
    val interval = FluidTrace.begin(FluidTraceNames.FirstScreen)
    assertEquals(1, ShadowTrace.getCurrentAsyncSections().size)
    interval.end()
    interval.end()
    assertTrue(ShadowTrace.getCurrentAsyncSections().isEmpty())
    assertEquals(1, ShadowTrace.getPreviousAsyncSections().size)
  }

  @Test
  fun namesComeFromTheCodeNotFromData() {
    for (bad in listOf("Diario di Marta.pdf", "https://x", "", "A", "x".repeat(65), "nota/1")) {
      assertTrue(bad, runCatching { FluidTraceName(bad) }.isFailure)
    }
    FluidTraceName("gallery:apri-album")
  }
}
