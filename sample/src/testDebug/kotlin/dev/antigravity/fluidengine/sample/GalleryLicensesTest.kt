package dev.antigravity.fluidengine.sample

import dev.antigravity.fluidengine.ink.FluidInkLicenses
import dev.antigravity.fluidengine.playback.FluidPlaybackLicenses
import dev.antigravity.fluidengine.qr.FluidQrLicenses
import dev.antigravity.fluidengine.scan.FluidScanLicenses
import dev.antigravity.fluidengine.search.semantic.FluidSemanticSearchLicenses
import dev.antigravity.fluidengine.ui.fluid.FluidEngineLicenses
import dev.antigravity.fluidengine.viewer.ocr.FluidOcrLicenses
import dev.antigravity.fluidengine.sample.viewer.VisoriLicenses
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1 (Sol, minore): la pagina «Licenze e crediti» della galleria porta le librerie di ogni modulo che la galleria
 * distribuisce (`sample/build.gradle`, uguale in debug e in bench). Prima c'erano solo l'engine e i visori: mancavano
 * Jetpack Ink, CameraX, Tesseract (da `engine-viewer-all`), Media3, ZXing e LiteRT-LM.
 */
class GalleryLicensesTest {
  @Test
  fun `R1 - i crediti della galleria hanno le librerie di ogni modulo incluso`() {
    val modules = mapOf(
      "engine-ui" to FluidEngineLicenses,
      "visori" to VisoriLicenses,
      "engine-viewer-ocr" to FluidOcrLicenses,
      "engine-ink" to FluidInkLicenses,
      "engine-scan" to FluidScanLicenses,
      "engine-playback" to FluidPlaybackLicenses,
      "engine-qr" to FluidQrLicenses,
      "engine-search-semantic" to FluidSemanticSearchLicenses,
    )
    val missing = modules.flatMap { (module, list) -> list.filter { it !in GalleryLicenses }.map { "$module: ${it.name}" } }
    assertTrue("mancano nei crediti: $missing", missing.isEmpty())
    assertEquals("una riga per opera", GalleryLicenses.size, GalleryLicenses.distinctBy { it.name to it.licence }.size)
  }
}
