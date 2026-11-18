package dev.antigravity.fluidengine.sample.material

import android.content.pm.ApplicationInfo
import dev.antigravity.fluidengine.sample.BuildConfig
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Chi può usare gli extra di lancio e la diagnostica della galleria, nelle tre build: la release dello store (nessuno),
 * la debug (il flag `debuggable`) e la `bench` (non debuggable ma ammessa dalla costante di build).
 *
 * La costante di build vale qualcosa di diverso in ogni build, e i test girano sempre in debug: per questo le
 * funzioni ne prendono una copia come argomento e si provano tutte e tre le combinazioni, qui, senza compilare la
 * `bench`. Il caso che conta è l'ultimo: la `bench` deve riuscire a far accettare all'engine `gallery.tune` e
 * `gallery.api` (che lui rifiuta se l'app non è `debuggable`) senza diventare debuggable davvero.
 */
@RunWith(RobolectricTestRunner::class)
class GalleryExtrasTest {

  private val context get() = RuntimeEnvironment.getApplication()
  private var flagsWere = 0

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    flagsWere = context.applicationInfo.flags
  }

  @After
  fun stop() {
    context.applicationInfo.flags = flagsWere
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun debuggable(on: Boolean) {
    val info = context.applicationInfo
    info.flags = if (on) info.flags or ApplicationInfo.FLAG_DEBUGGABLE else info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
  }

  @Test
  fun theDebugBuildKeepsTheFlagAsTheOnlyJudge() {
    // I test di release (flag spento) e di debug (acceso) di tutta la galleria contano su questo: in debug la costante
    // è falsa, e a decidere resta il flag.
    assertFalse(BuildConfig.EXTRAS_WITHOUT_DEBUGGABLE)
    debuggable(false)
    assertFalse(context.isDebuggableApp())
    debuggable(true)
    assertTrue(context.isDebuggableApp())
  }

  @Test
  fun theReleaseBuildAdmitsNothing() {
    debuggable(false)
    assertFalse(context.isDebuggableApp(extrasWithoutDebuggable = false))
    // Nessun travestimento: l'engine vede l'app com'è e rifiuta, come ha sempre fatto in release.
    assertSame(context, context.diagnosticHost(extrasWithoutDebuggable = false))
    assertFalse(FluidGlassDiagnostics.tuneLiquidProfile(context.diagnosticHost(false), mapOf("blur" to "12")))
    assertFalse(FluidGlassDiagnostics.simulateApiLevel(context.diagnosticHost(false), 32))
    assertNull(FluidGlassDiagnostics.simulatedApiLevel)
    assertTrue(FluidGlassDiagnostics.liquidProfileTuning.isEmpty())
  }

  @Test
  fun aDebuggableAppIsLeftAsItIs() {
    debuggable(true)
    assertTrue(context.isDebuggableApp(extrasWithoutDebuggable = false))
    assertTrue(context.isDebuggableApp(extrasWithoutDebuggable = true))
    assertSame(context, context.diagnosticHost(extrasWithoutDebuggable = true))
  }

  @Test
  fun theBenchBuildIsAdmittedWithoutBecomingDebuggable() {
    debuggable(false)
    assertTrue(context.isDebuggableApp(extrasWithoutDebuggable = true))

    // Senza il travestimento l'engine rifiuta, ed è il motivo per cui `gallery.tune` risponderebbe NON APPLICATO...
    assertFalse(FluidGlassDiagnostics.tuneLiquidProfile(context, null))
    assertFalse(FluidGlassDiagnostics.simulateApiLevel(context, 32))

    // ...col travestimento accetta, e l'app vera resta non debuggable (ART, JIT e R8 non c'entrano).
    val host = context.diagnosticHost(extrasWithoutDebuggable = true)
    assertNotSame(context, host)
    assertTrue((host.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0)
    assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
    assertTrue(FluidGlassDiagnostics.tuneLiquidProfile(host, mapOf("blur" to "12")))
    assertTrue(FluidGlassDiagnostics.liquidProfileTuning.containsKey("blur"))
    assertTrue(FluidGlassDiagnostics.simulateApiLevel(host, 32))
    assertEquals(32, FluidGlassDiagnostics.simulatedApiLevel)
  }

  @Test
  fun theContextScrimIsPickedLiveKeepingTheOtherKnobs() {
    // Correzione dopo S9: lo sfondo del contestuale (`ctxscrim`) si sceglie dalla pagina dei menu senza far rinascere
    // la galleria (la pagina resta dov'e' per le tre catture), e le altre manopole restano quelle di prima.
    debuggable(true)
    val switches = GalleryGlassSwitches(androidx.compose.runtime.mutableStateOf(dev.antigravity.fluidengine.ui.fluid.FluidGlassModel.Liquid))
    assertTrue(switches.tune(context, mapOf("blur" to "12")))
    val epoch = switches.epoch
    assertTrue(switches.tuneLive(context, "ctxscrim", "a"))
    assertEquals("nessuna rinascita", epoch, switches.epoch)
    assertEquals("a", switches.tuning["ctxscrim"])
    assertTrue(switches.tuning.containsKey("blur"))
    assertTrue(switches.tuneLive(context, "ctxscrim", "c"))
    assertEquals("c", FluidGlassDiagnostics.liquidProfileTuning["ctxscrim"])
    // Un valore sconosciuto non si prende: torna il livello di serie, che non e' una manopola.
    switches.tuneLive(context, "ctxscrim", "z")
    assertNull(switches.tuning["ctxscrim"])
    assertTrue(switches.tuning.containsKey("blur"))
  }
}
