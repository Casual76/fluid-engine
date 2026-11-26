package dev.antigravity.fluidengine.diagnostics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowTrace

/**
 * F8.24 · Le tracce nella build di **release**: non ce n'è nessuna. Gira con `:engine-diagnostics:testReleaseUnitTest`,
 * cioè sul codice della release del modulo (`src/release`), quello che prende ogni app in release e la bench.
 */
@RunWith(RobolectricTestRunner::class)
class FluidTraceReleaseTest {
  @Before
  fun setUp() {
    ShadowTrace.reset()
    ShadowTrace.setEnabled(true)
  }

  @Test
  fun theReleaseHasNoTraces() = runBlocking {
    assertFalse(FluidTraceBackend.Enabled)
    assertFalse(FluidTrace.isAvailable)
    assertFalse(FluidTrace.isRecording)
    assertEquals(7, FluidTrace.section(FluidTraceNames.Decode) { 7 })
    assertEquals("ok", FluidTrace.async(FluidTraceNames.OpenItem) { "ok" })
    FluidTrace.begin(FluidTraceNames.FirstScreen).end()
    FluidTrace.counter(FluidTraceName("prova:coda"), 3)
    // Neanche un intervallo: Trace non è stato chiamato.
    assertTrue(ShadowTrace.getPreviousSections().isEmpty())
    assertTrue(ShadowTrace.getCurrentSections().isEmpty())
    assertTrue(ShadowTrace.getPreviousAsyncSections().isEmpty())
    assertTrue(ShadowTrace.getCurrentAsyncSections().isEmpty())
    assertTrue(ShadowTrace.getCounters().isEmpty())
  }

  @Test
  fun theReleaseBackendDoesNotEvenLinkTracing() {
    // La classe della release non nomina AndroidX Tracing: nessun riferimento nel suo bytecode.
    val bytes = FluidTraceBackend::class.java.getResourceAsStream("FluidTraceBackend.class")!!.readBytes()
    val text = String(bytes, Charsets.ISO_8859_1)
    assertFalse(text.contains("androidx/tracing"))
    assertFalse(text.contains("android/os/Trace"))
  }

  @Test
  fun errorsStillPassThrough() {
    val thrown = runCatching { FluidTrace.section(FluidTraceNames.ReadFile) { error("rotto") } }.exceptionOrNull()
    assertEquals("rotto", thrown?.message)
  }
}
