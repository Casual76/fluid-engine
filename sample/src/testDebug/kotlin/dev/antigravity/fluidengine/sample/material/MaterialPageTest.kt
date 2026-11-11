package dev.antigravity.fluidengine.sample.material

import android.content.pm.ApplicationInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.Gallery
import dev.antigravity.fluidengine.sample.GalleryEntry
import dev.antigravity.fluidengine.sample.GalleryNav
import dev.antigravity.fluidengine.sample.GalleryPage
import dev.antigravity.fluidengine.sample.MatrixTheme
import dev.antigravity.fluidengine.sample.RouteChrome
import dev.antigravity.fluidengine.sample.RouteSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassModelReason
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La pagina «Materiale» dentro la galleria vera (F1A.9): ogni scena si compone in chiaro e in scuro, con `Classic` e
 * `Liquid` affiancati, senza eccezioni, e gira per qualche secondo (il dito finto, i cicli, il menu e il foglio).
 * Nessuno ha visto la pagina sul tablet: questa è la prova che ci arriva intera.
 *
 * Sul livello 35, in modo grafico `NATIVE`, la sonda vera compila corpo e luce (F1A.7): la colonna `Liquid` prende
 * davvero il materiale nuovo, e la diagnostica lo conta. Schermo da tablet in orizzontale, perché le colonne stiano
 * affiancate come sul Tab S9; l'orologio lo fa avanzare il test, perché le scene hanno cicli senza fine.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1200dp-h760dp-mdpi")
class MaterialPageTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false
  private var flagsWere = 0

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    flagsWere = compose.activity.applicationInfo.flags
  }

  @After
  fun stop() {
    compose.activity.applicationInfo.flags = flagsWere
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun debuggable(on: Boolean) {
    val info = compose.activity.applicationInfo
    info.flags = if (on) info.flags or ApplicationInfo.FLAG_DEBUGGABLE else info.flags and ApplicationInfo.FLAG_DEBUGGABLE.inv()
  }

  /** La galleria come la monta l'attività: gli interruttori sopra la chiave, il vetro chiesto alla radice. */
  private fun show(
    theme: MatrixTheme,
    entries: List<GalleryEntry>,
    config: MaterialConfig? = null,
    autoAdvance: Boolean = false,
  ): Pair<GalleryGlassSwitches, GalleryNav> {
    val switches = GalleryGlassSwitches(mutableStateOf(FluidGlassModel.Liquid)).apply { materialConfig = config }
    val nav = GalleryNav(entries)
    // Le scene hanno cicli senza fine: l'orologio lo fa avanzare il test. Le Impostazioni no, e lo scorrimento della
    // lista vuole l'orologio che va da solo.
    compose.mainClock.autoAdvance = autoAdvance
    compose.setContent {
      var settings by remember { mutableStateOf(theme.applyTo(EngineSettings())) }
      CompositionLocalProvider(
        LocalGalleryGlassSwitches provides switches,
        LocalFluidGlassModel provides switches.model,
      ) {
        key(switches.epoch) {
          FluidTheme(settings = settings) {
            Gallery(settings = settings, onSettingsChange = { settings = it }, nav = nav)
          }
        }
      }
    }
    settle(500)
    return switches to nav
  }

  /**
   * Fa arrivare alla composizione ciò che il test ha cambiato, e poi passare [millis] di fotogrammi. Una scrittura di
   * stato fatta dal test arriva al ricompositore quando il filo principale si svuota (`waitForIdle`); con l'orologio
   * fermo i fotogrammi li fa passare `advanceTimeBy`.
   */
  private fun settle(millis: Long) {
    compose.waitForIdle()
    compose.mainClock.advanceTimeBy(millis)
    compose.waitForIdle()
  }

  private fun counts(): String =
    "superfici ${FluidGlassDiagnostics.liveSurfaces} scelte ${FluidGlassDiagnostics.liveModelChoices}"

  private val materialPage = listOf(GalleryEntry.Tab(RouteChrome), GalleryEntry.Page(GalleryPage.Material))

  /** Le scene che mettono i due vetri in colonna (la lista ha una pagina sua; il foglio, dalla rifinitura di F1A, è in colonna). */
  private val split = MaterialScene.entries - setOf(MaterialScene.ListUnderBar)

  private fun everySceneComposes(theme: MatrixTheme) {
    val (switches, _) = show(theme, materialPage)
    // Senza scena: l'elenco, col tag di logcat nel sottotitolo.
    assertTrue(
      compose.onAllNodesWithText("adb logcat -s FluidMateriale", substring = true).fetchSemanticsNodes().isNotEmpty(),
    )
    for (scene in MaterialScene.entries) {
      compose.runOnIdle { switches.materialConfig = MaterialConfig.defaults(scene) }
      // Oltre il primo giro dei cicli (1,2 s): il dito finto ha toccato, il menu e il foglio si sono aperti.
      settle(1_600)
      compose.onNodeWithTag("material-page").assertExists()
      if (scene in split) {
        compose.onNodeWithTag("material-cell-classic", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("material-cell-liquid", useUnmergedTree = true).assertExists()
      }
    }
    // E il fondo che scorre, su una foto, coi vetri da soli.
    for (models in listOf(MaterialModels.Liquid, MaterialModels.Classic)) {
      compose.runOnIdle {
        switches.materialConfig = MaterialConfig(
          MaterialScene.Components,
          backdrop = MaterialBackdrop.Photo,
          motion = MaterialMotion.Scroll,
          models = models,
        )
      }
      settle(800)
      compose.onNodeWithTag("material-page").assertExists()
    }
  }

  @Test
  fun `ogni scena si compone in chiaro`() = everySceneComposes(MatrixTheme.Light)

  @Test
  fun `ogni scena si compone in scuro`() = everySceneComposes(MatrixTheme.Dark)

  @Test
  fun `ogni fondo si compone`() {
    val (switches, _) = show(MatrixTheme.Light, materialPage)
    for (backdrop in MaterialBackdrop.entries) {
      compose.runOnIdle { switches.materialConfig = MaterialConfig(MaterialScene.Edge, backdrop = backdrop) }
      settle(600)
      compose.onNodeWithTag("material-cell-liquid", useUnmergedTree = true).assertExists()
    }
  }

  @Test
  fun `la colonna Liquid prende il materiale nuovo e la colonna Classic quello di prima`() {
    show(MatrixTheme.Light, materialPage, MaterialConfig(MaterialScene.Edge))
    settle(1_000)
    compose.runOnIdle {
      val surfaces = FluidGlassDiagnostics.liveSurfaces
      val choices = FluidGlassDiagnostics.liveModelChoices
      assertTrue("nessuna superficie Liquid: ${counts()}", (surfaces[FluidGlassModel.Liquid] ?: 0) >= 3)
      assertTrue("la colonna Classic non e' contata: ${counts()}", (choices[FluidGlassModelReason.ClassicRequested] ?: 0) >= 3)
      // Il riquadro di stato legge le stesse cose.
      val status = MaterialStatus.read(FluidGlassModel.Liquid).lines()
      assertTrue(status.toString(), status[1].startsWith("superfici liquid="))
    }
  }

  @Test
  fun `il foglio della scena modal prende il vetro della sua colonna`() {
    // Fino a F1A.9 il foglio si apriva nell'host della radice e prendeva il vetro di `gallery.model`: le due colonne
    // mostravano lo stesso foglio. Ora ogni colonna ha il suo host, sotto il suo vetro.
    show(MatrixTheme.Dark, materialPage, MaterialConfig(MaterialScene.Modal, cycle = MaterialCycle.Hold))
    settle(1_600)
    for (model in listOf(FluidGlassModel.Liquid, FluidGlassModel.Classic)) {
      compose.onNodeWithTag("material-cell-${model.name.lowercase()}", useUnmergedTree = true).assertExists()
      assertTrue(
        "il foglio della colonna ${model.name}",
        compose.onAllNodesWithText("Vetro di questa colonna: ${model.name}", substring = true)
          .fetchSemanticsNodes().isNotEmpty(),
      )
    }
  }

  @Test
  fun `il vetro chiesto alla radice cambia tutta la galleria`() {
    val (switches, _) = show(MatrixTheme.Light, materialPage, MaterialConfig(MaterialScene.Edge, models = MaterialModels.Liquid))
    settle(600)
    val before = compose.runOnIdle { counts() }
    val liquidBefore = compose.runOnIdle { FluidGlassDiagnostics.liveSurfaces[FluidGlassModel.Liquid] ?: 0 }
    compose.runOnIdle { switches.model = FluidGlassModel.Classic }
    settle(600)
    compose.onNodeWithTag("material-cell-liquid", useUnmergedTree = true).assertExists()
    compose.runOnIdle {
      // La colonna chiede `Liquid` da sé e la tiene; la barra delle schede della galleria passa al vetro di prima.
      val message = "prima: $before; dopo: ${counts()}"
      assertTrue(message, (FluidGlassDiagnostics.liveModelChoices[FluidGlassModelReason.ClassicRequested] ?: 0) >= 1)
      val liquidAfter = FluidGlassDiagnostics.liveSurfaces[FluidGlassModel.Liquid] ?: 0
      // La barra della galleria passa a Classic, le lastre della colonna no: ne restano tutte meno quelle della barra.
      assertTrue(message, liquidAfter in 1 until liquidBefore)
      assertTrue(message, (FluidGlassDiagnostics.liveSurfaces[FluidGlassModel.Classic] ?: 0) >= 1)
    }
  }

  @Test
  fun `una manopola fa rinascere la pagina, che resta dov'era`() {
    debuggable(true)
    val config = MaterialConfig(MaterialScene.Corner)
    val (switches, nav) = show(MatrixTheme.Light, materialPage, config)
    val epoch = switches.epoch
    compose.runOnIdle { assertTrue(switches.tune(compose.activity, mapOf("edge" to "ios26", "colore" to "rosso"))) }
    settle(600)
    compose.runOnIdle {
      assertEquals(epoch + 1, switches.epoch)
      assertEquals(mapOf("edge" to "ios26"), FluidGlassDiagnostics.liquidProfileTuning)
      assertEquals(mapOf("edge" to "ios26"), switches.tuning)
      assertEquals(GalleryPage.Material, nav.page)
      assertEquals(config, switches.materialConfig)
    }
    compose.onNodeWithTag("material-cell-liquid", useUnmergedTree = true).assertExists()
    compose.runOnIdle { switches.tune(compose.activity, emptyMap()) }
    settle(300)
    compose.runOnIdle { assertTrue(FluidGlassDiagnostics.liquidProfileTuning.isEmpty()) }
  }

  @Test
  fun `il livello simulato fa rinascere la galleria sul vetro di prima`() {
    debuggable(true)
    val (switches, _) = show(MatrixTheme.Light, materialPage, MaterialConfig(MaterialScene.Edge))
    compose.runOnIdle { assertTrue(switches.simulate(compose.activity, 32)) }
    settle(600)
    compose.runOnIdle {
      assertEquals(32, switches.apiLevel)
      assertEquals(counts(), 0, FluidGlassDiagnostics.liveSurfaces[FluidGlassModel.Liquid] ?: 0)
      assertTrue(counts(), (FluidGlassDiagnostics.liveModelChoices[FluidGlassModelReason.ApiLevel] ?: 0) >= 3)
    }
    compose.onNodeWithTag("material-cell-liquid", useUnmergedTree = true).assertExists()
    compose.runOnIdle { switches.simulate(compose.activity, null) }
    settle(600)
    compose.runOnIdle { assertTrue(counts(), (FluidGlassDiagnostics.liveSurfaces[FluidGlassModel.Liquid] ?: 0) >= 3) }
  }

  @Test
  fun `gli extra della galleria si applicano all'avvio`() {
    debuggable(true)
    GalleryGlassSwitches.applyAtLaunch(
      compose.activity,
      GalleryGlassLaunch.parse(
        mapOf("gallery.api" to "30", "gallery.warmup" to "off", "gallery.tune" to "frost=r24,blur=30")::get,
      ),
    )
    assertEquals(30, FluidGlassDiagnostics.simulatedApiLevel)
    assertFalse(FluidGlassDiagnostics.warmUpEnabled)
    assertEquals(mapOf("frost" to "r24", "blur" to "30"), FluidGlassDiagnostics.liquidProfileTuning)

    // Un'app che non e' debuggable non simula e non tara niente.
    FluidGlassDiagnostics.resetForTesting()
    debuggable(false)
    GalleryGlassSwitches.applyAtLaunch(
      compose.activity,
      GalleryGlassLaunch(api = 32, tune = mapOf("edge" to "ios26")),
    )
    assertEquals(null, FluidGlassDiagnostics.simulatedApiLevel)
    assertTrue(FluidGlassDiagnostics.liquidProfileTuning.isEmpty())
  }

  @Test
  fun `dalle Impostazioni si arriva agli interruttori e alla pagina`() {
    val (_, nav) = show(MatrixTheme.Light, listOf(GalleryEntry.Tab(RouteSettings)), autoAdvance = true)
    compose.waitForIdle()
    compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasTestTag("gallery-open-material"))
    compose.waitForIdle()
    compose.onNodeWithTag("gallery-glass-switches").assertExists()
    compose.onNodeWithTag("gallery-open-material").performClick()
    compose.waitForIdle()
    compose.runOnIdle { assertEquals(GalleryPage.Material, nav.page) }
    assertTrue(
      compose.onAllNodesWithText("adb logcat -s FluidMateriale", substring = true).fetchSemanticsNodes().isNotEmpty(),
    )
  }
}
