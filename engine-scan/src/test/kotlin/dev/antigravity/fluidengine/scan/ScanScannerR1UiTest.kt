package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * R1 · Lo scanner intero, nei casi in cui prima taceva o mentiva: lo scatto che fallisce, la fila piena, il «Riprendi»
 * detto a TalkBack con la pagina giusta, e «Salva» con una pagina che non si legge (prima l'eccezione chiudeva l'app).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanScannerR1UiTest {
  @get:Rule val compose = createComposeRule()

  private fun photo(name: String): Bitmap =
    javaClass.classLoader!!.getResourceAsStream("scan/$name.jpg").use { BitmapFactory.decodeStream(it) }!!

  @org.junit.Before fun provider() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    val info = android.content.pm.ProviderInfo().apply {
      authority = "${context.packageName}.fluidscan"
      name = FluidScanFileProvider::class.java.name
      packageName = context.packageName
      applicationInfo = context.applicationInfo
      grantUriPermissions = true
      metaData = android.os.Bundle().apply { putInt("android.support.FILE_PROVIDER_PATHS", R.xml.fluid_scan_paths) }
    }
    shadowOf(context.packageManager).addOrUpdateProvider(info)
  }

  private lateinit var state: FluidScanState
  private var result: FluidScanResult? = null

  /** Una fotocamera che si chiude a ogni scatto. */
  private class ClosingCamera(still: FluidStillScanCamera) : FluidScanCamera by still {
    override suspend fun capture(): Bitmap = throw java.io.IOException("la fotocamera si è chiusa")
  }

  private fun show(camera: FluidScanCamera, maxPages: Int = 100) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(EngineSettings()) {
        state = rememberFluidScanState(FluidScanOptions(autoCapture = false, maxPages = maxPages))
        FluidDocumentScanner(state, camera, onDone = { result = it }, onCancel = {})
      }
    }
  }

  private fun still() = FluidStillScanCamera(List(4) { suspend { photo("buona-luce") } }, table = null)

  private fun pump(ms: Long = 66) {
    compose.mainClock.advanceTimeBy(ms)
    Thread.sleep(4)
    shadowOf(Looper.getMainLooper()).idle()
    compose.waitForIdle()
  }

  private fun pumpUntil(what: String, limit: Int = 400, condition: () -> Boolean) {
    repeat(limit) {
      if (condition()) return
      pump()
    }
    throw AssertionError("Non è successo: $what")
  }

  private fun shows(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun shoot() {
    pumpUntil("la fotocamera pronta") { runCatching { compose.onNodeWithTag(FluidScanTags.Shutter).fetchSemanticsNode() }.isSuccess && ::state.isInitialized }
    repeat(10) { pump() }
    compose.onNodeWithTag(FluidScanTags.Shutter).performClick()
  }

  @Test fun loScattoCheFallisceLoDiceEloScriveSullaFotocamera() {
    show(ClosingCamera(still()))
    shoot()
    pumpUntil("l'avviso dello scatto non riuscito") { shows("Scatto non riuscito, riprova") }
    assertEquals(0, state.pages.size)
  }

  @Test fun conLaFilaPienaLoScannerLoDiceInveceDiAnnunciareUnaPagina() {
    show(still(), maxPages = 2)
    shoot()
    pumpUntil("la prima pagina") { state.pages.size == 1 }
    shoot()
    pumpUntil("la seconda pagina") { state.pages.size == 2 }
    pumpUntil("l'annuncio della seconda") { shows("Pagina 2 scansionata") }
    shoot()
    pumpUntil("l'avviso della fila piena") { shows("Hai raggiunto il massimo di 2 pagine") }
    repeat(20) { pump() }
    assertEquals(2, state.pages.size)
    assertTrue("niente «Pagina 3»", !shows("Pagina 3 scansionata"))
  }

  @Test fun riprendiAnnunciaLaPaginaRifatta() {
    show(still())
    shoot()
    pumpUntil("la prima pagina") { state.pages.size == 1 }
    shoot()
    pumpUntil("la seconda pagina") { state.pages.size == 2 }
    pumpUntil("le miniature") { state.pages.all { state.thumbnail(it.id) != null } }
    val first = state.pages[0].id
    compose.onNodeWithTag(FluidScanTags.Thumbnail).performClick()
    pump(400)
    compose.onNodeWithTag(FluidScanTags.stripPage(0)).performClick()
    pump(800)
    compose.onNodeWithTag(FluidScanTags.Retake).performClick()
    pump(400)
    shoot()
    pumpUntil("di nuovo la revisione") { runCatching { compose.onNodeWithTag(FluidScanTags.Review).fetchSemanticsNode() }.isSuccess }
    pumpUntil("l'annuncio della pagina rifatta") { shows("Pagina 1 scansionata") }
    assertEquals(first, state.pages[0].id)
  }

  @Test fun salvaConUnaPaginaCheNonSiLeggeLoDiceERestaAperto() {
    show(still())
    shoot()
    pumpUntil("la pagina") { state.pages.size == 1 }
    pumpUntil("la miniatura") { state.thumbnail(state.pages[0].id) != null }
    pump(600)
    // La foto si rompe e la pagina pulita sparisce (la cache svuotata, una scrittura interrotta).
    val page = state.pages[0]
    page.photo.writeBytes(ByteArray(96) { 7 })
    File(page.photo.parentFile, "pagina-${page.id}.jpg").delete()
    compose.onNodeWithTag(FluidScanTags.Save).performClick()
    pumpUntil("l'avviso del salvataggio non riuscito") { shows("Non è stato possibile salvare") }
    assertTrue(shows("La pagina 1 non si legge: rifalla con Riprendi o eliminala."))
    assertNull("niente consegna", result)
    assertEquals(1, state.pages.size)
  }
}
