package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.graphics.Color
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Lo scrittore delle tracce: le tre regole di formato di TRACE.md e la cartella d'uscita. */
class GlassTraceJsonTest {

  @Test
  fun numbersUseADotWhateverTheMachineLanguage() {
    val previous = Locale.getDefault()
    Locale.setDefault(Locale.ITALY)
    try {
      assertEquals("1.5", GlassTraceJson.fixed(1.5, 6))
      assertEquals("12.346dp", glassDp(12.3456f))
      assertEquals("{\n  \"a\": [0.25, -3, \"x\"]\n}\n", GlassTraceJson.encode(mapOf("a" to listOf(0.25f, -3.0, "x"))))
    } finally {
      Locale.setDefault(previous)
    }
  }

  @Test
  fun aNonFiniteNumberStopsWithItsPath() {
    try {
      GlassTraceJson.encode(mapOf("frames" to listOf(mapOf("t" to Float.NaN))))
      fail("un NaN e' stato scritto")
    } catch (error: IllegalArgumentException) {
      assertTrue(error.message!!, error.message!!.contains("$.frames[0].t"))
    }
    try {
      glassDp(Float.POSITIVE_INFINITY)
      fail("un infinito e' stato scritto")
    } catch (expected: IllegalArgumentException) {
      // giusto cosi'
    }
  }

  @Test
  fun aTinyLengthDoesNotBecomeZero() {
    // L'altezza della lente tenuta a 0,0001 px, a densita' 3: zero sarebbe un divisore nullo nello shader.
    val tiny = glassDp(0.0001f / 3f)
    assertTrue(tiny, tiny.endsWith("dp") && tiny != "0dp")
    assertEquals(0.0001 / 3.0, tiny.removeSuffix("dp").toDouble(), 1e-7)
    assertEquals("0dp", glassDp(0f))
    assertEquals("0dp", glassDp(-0.0f))
  }

  @Test
  fun colorsAreUnpremultipliedRgba() {
    assertEquals("#FF000080", glassColor(Color.Red.copy(alpha = 128 / 255f)))
    assertEquals("#FAFAFAFF", glassColor(Color(0xFFFAFAFA)))
  }

  @Test
  fun aTraceWithoutTheZeroFrameIsRefused() {
    try {
      GlassTrace("x", "f0", 3f, "light", 10f, 10f, listOf(GlassTraceFrame(16.0, emptyList())))
      fail("traccia senza t = 0 accettata")
    } catch (expected: IllegalArgumentException) {
      // giusto cosi'
    }
  }

  @Test
  fun nothingIsWrittenWithoutTheVariableAndARelativeDirIsUnderTheRepo() {
    val root = File("radice-finta").absoluteFile
    assertNull(GlassTraceOutput.requestedDir(null) { root })
    assertNull(GlassTraceOutput.requestedDir("  ") { root })
    assertEquals(File(root, "artifacts/liquid-glass/moto"), GlassTraceOutput.requestedDir("artifacts/liquid-glass/moto") { root })
    val trace = GlassTrace("prova", "f0", 3f, "light", 10f, 10f, listOf(GlassTraceFrame(0.0, emptyList())))
    assertEquals(File(root, "f0/prova/trace.json"), GlassTraceOutput.fileFor(root, trace))
  }

  @Test
  fun durationIsTheLastInstantAndZeroForAStillScene() {
    // Una scena ferma (un fotogramma solo, a t = 0) porta `durationMs: 0`, che TRACE.md ammette.
    val still = GlassTrace("ferma", "f0", 3f, "light", 10f, 10f, listOf(GlassTraceFrame(0.0, emptyList())))
    assertEquals(0.0, still.toJson()["durationMs"])
    assertTrue(still.encode(), still.encode().contains("\"durationMs\": 0,"))
    // Un viaggio: l'ultimo istante, anche con un preambolo prima di t = 0 e a 120 Hz.
    val frames = (-2..3).map { GlassTraceFrame(glassTraceTimeMs(it, 120), emptyList()) }
    val moving = GlassTrace("viaggio", "f0", 2.125f, "light", 10f, 10f, frames, fps = 120)
    assertEquals(25.0, moving.toJson()["durationMs"] as Double, 1e-9)
  }

  @Test
  fun aSquaredLengthIsWrittenWithItsPower() {
    // `normalSmooth` e' 4 dp al quadrato: il banco lo porta in px² con density² (TRACE.md, «Unita'»).
    assertEquals("16dp^2", glassDpPower(16.0, 2))
    assertEquals("2.5dp^3", glassDpPower(2.5, 3))
    try {
      glassDpPower(1.0, 1)
      fail("la potenza 1 e' glassDp")
    } catch (expected: IllegalArgumentException) {
      // giusto cosi'
    }
  }

  @Test
  fun theRepoRootIsFoundFromTheModule() {
    val root = GlassTraceOutput.repoRoot()
    assertTrue(File(root, "tools/glassbench/TRACE.md").isFile)
    assertTrue(File(root, "engine-ui").isDirectory)
  }
}
