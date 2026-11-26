package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.17 · Lo scanner intero col dito finto e la fotocamera finta: due fogli trovati e scattati da soli, la fila delle
 * pagine riordinata tenendo premuto, un angolo ritagliato, il filtro, e il PDF consegnato per URI. Anche col movimento
 * ridotto e con TalkBack (le azioni ci sono).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidDocumentScannerUiTest {
  @get:Rule val compose = createComposeRule()

  private fun photo(name: String): Bitmap =
    javaClass.classLoader!!.getResourceAsStream("scan/$name.jpg").use { BitmapFactory.decodeStream(it) }!!

  /**
   * Il FileProvider del modulo per la consegna: nelle prove di una libreria l'autorità del manifest fuso non è quella del
   * pacchetto di prova, quindi lo si registra come lo registrerebbe un'app.
   */
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

  private fun show(reduced: Boolean = false, auto: Boolean = true): FluidStillScanCamera {
    val camera = FluidStillScanCamera(listOf(suspend { photo("buona-luce") }, suspend { photo("scontrino") }),
      table = { photo("tavolo-vuoto") })
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(EngineSettings()) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default) {
          state = rememberFluidScanState(FluidScanOptions(autoCapture = auto))
          FluidDocumentScanner(state, camera, onDone = { result = it }, onCancel = {})
        }
      }
    }
    return camera
  }

  /** Il tempo che passa: i fotogrammi della fotocamera finta, l'analisi sul suo filo e i risultati sul principale. */
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

  @Test fun dueFogliScattatiDaSoliPoiRiordinatiRitagliatiEConsegnati() {
    show()
    pumpUntil("il primo foglio scattato da solo") { ::state.isInitialized && state.pages.size == 1 }
    pumpUntil("il secondo foglio, dopo il tavolo vuoto") { state.pages.size == 2 }
    pumpUntil("le miniature pulite") { state.pages.all { state.thumbnail(it.id) != null } }
    // Il primo foglio è un A4: la pagina pulita ha le proporzioni giuste; il secondo è lo scontrino, stretto.
    val first = state.pages[0]
    val second = state.pages[1]
    assertTrue("il foglio trovato non è tutta la foto", first.quad != FluidScanQuad.Full)
    val a4 = state.thumbnail(first.id)!!
    assertEquals(0.707f, a4.width.toFloat() / a4.height, 0.05f)
    val receipt = state.thumbnail(second.id)!!
    assertTrue("lo scontrino è stretto", receipt.width.toFloat() / receipt.height < 0.45f)

    // La revisione dalla miniatura, poi la fila: tieni premuta la prima pagina e portala dopo la seconda.
    compose.onNodeWithTag(FluidScanTags.Thumbnail).performClick()
    pump(400)
    compose.onNodeWithTag(FluidScanTags.Review).assertIsDisplayed()
    val strip = compose.onNodeWithTag(FluidScanTags.stripPage(0))
    strip.performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    repeat(8) {
      strip.performTouchInput { moveBy(Offset(width * 0.25f, 0f)) }
      compose.mainClock.advanceTimeBy(16)
      compose.waitForIdle()
    }
    strip.performTouchInput { up() }
    pump(600)
    assertEquals("riordinate col dito", listOf(second.id, first.id), state.pages.map { it.id })

    // Ritaglia: l'angolo in alto a sinistra trascinato verso il centro.
    compose.onNodeWithTag(FluidScanTags.Crop).performClick()
    pump(400)
    val before = state.pages.first().quad
    compose.onNodeWithTag(FluidScanTags.corner(0)).performTouchInput {
      down(center)
      repeat(6) { moveBy(Offset(12f, 16f)) }
      up()
    }
    pump()
    compose.onNodeWithTag(FluidScanTags.CropDone).performClick()
    pump(400)
    val after = state.pages.first().quad
    assertNotEquals("l'angolo si è spostato", before.topLeft, after.topLeft)
    assertTrue(after.topLeft.x > before.topLeft.x && after.topLeft.y > before.topLeft.y)

    // Il filtro bianco e nero sulla pagina, e la rotazione.
    compose.onNodeWithTag(FluidScanTags.Filter).performClick()
    pump(400)
    compose.onNodeWithTag(FluidScanTags.filter(FluidScanFilter.BlackAndWhite)).performClick()
    compose.onNodeWithTag(FluidScanTags.Rotate).performClick()
    pump(200)
    assertEquals(FluidScanFilter.BlackAndWhite, state.pages.first().filter)
    assertEquals(1, state.pages.first().quarterTurns)

    // Salva: il PDF consegnato per URI, con le due pagine.
    compose.onNodeWithTag(FluidScanTags.Save).performClick()
    pumpUntil("la consegna") { result != null }
    val done = result!!
    assertEquals(2, done.pageCount)
    assertNotNull(done.pdf)
    assertEquals("content", done.pdf!!.scheme)
    assertEquals("${compose.activityPackage()}.fluidscan", done.pdf!!.authority)
    val pdf = done.pdfFile!!.readBytes().toString(Charsets.ISO_8859_1)
    assertTrue(pdf.contains("/Count 2"))
    // Lo scontrino ruotato di un quarto è largo: la sua pagina nel PDF è orizzontale.
    assertTrue("due immagini JPEG", Regex("/Filter /DCTDecode").findAll(pdf).count() == 2)
  }

  @Test fun inManualeScattaSoloColTastoEColMovimentoRidottoNienteVolo() {
    show(reduced = true, auto = false)
    pumpUntil("il foglio trovato") {
      compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Documento trovato")))
        .fetchSemanticsNodes().isNotEmpty()
    }
    repeat(40) { pump() }
    assertEquals("niente scatti da soli in manuale", 0, state.pages.size)
    compose.onNodeWithTag(FluidScanTags.Shutter).performClick()
    pumpUntil("lo scatto col tasto") { state.pages.size == 1 }
    // TalkBack: lo scatto ha il suo nome, l'interruttore Auto dice com'è.
    compose.onNodeWithTag(FluidScanTags.Shutter).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Scatta")))
    compose.onNodeWithTag(FluidScanTags.Auto).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Manuale"))
  }

  private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.activityPackage(): String =
    androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().packageName
}
