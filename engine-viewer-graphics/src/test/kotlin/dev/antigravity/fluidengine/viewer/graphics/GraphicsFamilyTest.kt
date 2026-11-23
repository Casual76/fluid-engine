package dev.antigravity.fluidengine.viewer.graphics

import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class GraphicsFamilyTest {
  @Test
  @Config(sdk = [27])
  fun heicBelowAndroid9OffersOpenWith() = fallback("studio.heic", "Android 9")

  @Test
  @Config(sdk = [30])
  fun avifBelowAndroid12OffersOpenWith() = fallback("studio.avif", "Android 12")

  @Test
  fun woff2OffersOpenWith() = fallback("studio.woff2", "WOFF2")

  @Test
  fun tiffHasTwoPages() {
    val input = bytes("studio-pages.tiff")
    val tiff = TiffPages(input, 12_000_000L)
    assertEquals(2, tiff.count)
  }

  @Test
  fun sfntAndWoffHaveTheSameFace() {
    val face = FontFile(bytes("studio.ttf")).faces.single()
    val woff = FontFile(bytes("studio.woff")).faces.single()
    assertEquals("Fluid Studio", face.family)
    assertEquals(face.names, woff.names)
    assertEquals(face.glyphCount, woff.glyphCount)
    assertTrue(woff.alphabets.contains("Latino"))
  }

  @Test
  fun collectionAndVariableFontExposeFacesAndAxes() {
    assertEquals(listOf("Regular", "Bold"), FontFile(bytes("studio.ttc")).faces.map { it.style })
    assertEquals("Fluid Studio", FontFile(bytes("studio.otf")).faces.single().family)
    val axis = FontFile(bytes("studio-variable.ttf")).faces.single().axes.single()
    assertEquals("wght", axis.tag)
    assertEquals(400f, axis.normal, 0f)
    assertEquals(800f, axis.max, 0f)
  }

  @Test
  fun specimenUsesMappedGlyphsAndReportsTheRealFontMetadata() {
    val face = FontFile(bytes("studio-variable.ttf")).faces.single()
    assertEquals("Aa", face.preview.hero)
    assertEquals("Quel vitreo balzo fra squarci d'ombra", face.preview.sentence)
    assertEquals(".,:;!?-+()&@#%='/€", face.preview.groups.single { it.label == "Segni" }.characters)
    assertEquals(listOf("Inglese"), face.preview.languages)
    assertTrue(face.tables.containsAll(listOf("cmap", "fvar", "glyf", "name")))
    val rows = face.details().associate { it.label to it.value }
    assertEquals("Fluid Engine test corpus", rows["Autore"])
    assertEquals("Version 1.1", rows["Versione"])
    assertEquals("CC0-1.0", rows["Licenza"])
    assertEquals("Sì", face.facts("TTF").single { it.label == "Variabile" }.value)
  }

  private fun fallback(name: String, reason: String) {
    runBlocking {
      val app = RuntimeEnvironment.getApplication()
      var chosen = false
      val source = FluidViewerSource.of(name, app.cacheDir) { bytes(name).inputStream() }
      FluidViewerRegistry.open(app, source, openWith = { chosen = true }).use { result ->
        assertTrue(result.details.any { it.label == "Anteprima" && it.value.contains(reason) })
        val unknown = result.content as FluidViewerContent.Unknown
        assertNotNull(unknown.openWith)
        unknown.openWith?.invoke()
        assertTrue(chosen)
      }
    }
  }

  private fun bytes(name: String): ByteArray = checkNotNull(javaClass.getResourceAsStream("/corpus/$name")).use { it.readBytes() }
}
