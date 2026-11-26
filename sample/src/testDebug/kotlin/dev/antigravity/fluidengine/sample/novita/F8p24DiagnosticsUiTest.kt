package dev.antigravity.fluidengine.sample.novita

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.diagnostics.FluidDiagnostics
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsConfig
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsConsent
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsConsentPrompt
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsDataPage
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsSettingsGroup
import dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsTags
import dev.antigravity.fluidengine.diagnostics.FluidLog
import dev.antigravity.fluidengine.diagnostics.testing.FluidDiagnosticsTesting
import dev.antigravity.fluidengine.feedback.FluidFeedbackConfig
import dev.antigravity.fluidengine.feedback.FluidFeedbackGitHub
import dev.antigravity.fluidengine.feedback.FluidFeedbackHost
import dev.antigravity.fluidengine.feedback.FluidFeedbackRow
import dev.antigravity.fluidengine.feedback.FluidFeedbackState
import dev.antigravity.fluidengine.feedback.FluidFeedbackTags
import dev.antigravity.fluidengine.feedback.rememberFluidFeedbackState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.lab.FluidExperiment
import dev.antigravity.fluidengine.foundation.lab.FluidExperimentApplies
import dev.antigravity.fluidengine.foundation.lab.FluidLab
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.lab.FluidLabPage
import dev.antigravity.fluidengine.ui.fluid.lab.FluidLabRow
import dev.antigravity.fluidengine.ui.fluid.lab.FluidLabTags
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareSheetTags
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F8.24 · Il Laboratorio (accendi col dito, spegni, «Al prossimo avvio», «Ripristina tutto», sopravvive al riavvio), il
 * foglio del consenso (una volta, il sì, il no), la pagina dei dati col JSON vero, e «Segnala un problema» (il foglio,
 * cosa parte in vista coi dati tolti, la issue precompilata nel browser), con TalkBack e «Riduci movimento».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p24DiagnosticsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var app: Application

  private val big = FluidExperiment("anteprime-grandi", "Anteprime grandi", "Le card più alte.", group = "Interfaccia")
  private val index = FluidExperiment(
    "indice-nuovo", "Indice nuovo", "Un indice più veloce.", group = "Prestazioni", applies = FluidExperimentApplies.NextLaunch,
  )

  @Before fun start() {
    app = ApplicationProvider.getApplicationContext()
    app.getSharedPreferences("fluid-lab-prova", Context.MODE_PRIVATE).edit().clear().commit()
    FluidDiagnosticsTesting.reset()
    FluidLog.clear()
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidDiagnosticsTesting.reset()
    FluidLog.clear()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun themed(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
          LocalFluidGlassModalHostState provides host,
        ) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
  }

  private fun field(tag: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

  private fun lab() = FluidLab.create(app, listOf(big, index), "fluid-lab-prova")

  private fun toggleState(tag: String): ToggleableState? =
    compose.onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)

  // --- il Laboratorio ---------------------------------------------------------------------------------------------------

  @Test
  fun laboratorio_accendiSpegniESopravviveAlRiavvio() {
    val lab = lab()
    themed { FluidLabPage(lab, onBack = {}, onReportProblem = {}) }
    advance(300)
    val row = FluidLabTags.experiment(big.key)
    assertEquals(ToggleableState.Off, toggleState(row))
    // TalkBack: un interruttore col suo nome.
    val node = compose.onNodeWithTag(row).fetchSemanticsNode()
    assertEquals(Role.Switch, node.config.getOrNull(SemanticsProperties.Role))
    compose.onNodeWithTag(row).performClick()
    advance(300)
    assertTrue(lab.isEnabled(big))
    assertEquals(ToggleableState.On, toggleState(row))

    // «Al prossimo avvio»: la scelta si vede, il valore in uso resta quello di partenza.
    compose.onNodeWithTag(FluidLabTags.experiment(index.key)).performClick()
    advance(300)
    // TalkBack legge «titolo, riassunto» e, sotto, «Al prossimo avvio».
    val described = compose.onNodeWithTag(FluidLabTags.experiment(index.key)).fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
    assertTrue(described, described.contains("Al prossimo avvio"))
    assertFalse(lab.isEnabled(index))

    // Un riavvio: un Laboratorio nuovo sullo stesso file.
    val restarted = lab()
    assertTrue(restarted.isEnabled(big))
    assertTrue(restarted.isEnabled(index))
    // Spento col dito, resta spento anche dopo.
    compose.onNodeWithTag(row).performClick()
    advance(300)
    assertFalse(lab().isEnabled(big))
  }

  @Test
  fun laboratorio_ripristinaTuttoChiedeConferma() {
    val lab = lab()
    lab.setEnabled(big, true)
    themed { FluidLabPage(lab, onBack = {}) }
    advance(300)
    compose.onNodeWithTag(FluidLabTags.Reset).performClick()
    advance(600)
    assertTrue("prima della conferma non cambia niente", lab.isEnabled(big))
    compose.onNodeWithText("Ripristina").performClick()
    advance(600)
    assertFalse(lab.isEnabled(big))
    assertFalse(lab().isEnabled(big))
  }

  @Test
  fun laboratorio_vuotoNonHaLaRiga() {
    val empty = FluidLab(emptyList(), dev.antigravity.fluidengine.foundation.lab.FluidLabStore.inMemory())
    themed(reduced = true) {
      FluidScreen(title = "Impostazioni") {
        item { FluidListGroup { FluidLabRow(empty, onClick = {}) } }
      }
    }
    advance(200)
    assertTrue("senza funzioni in prova la riga non c'è", compose.onAllNodesWithTag(FluidLabTags.Row).fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun laboratorio_conRiduciMovimento() {
    val lab = lab()
    themed(reduced = true) { FluidLabPage(lab, onBack = {}) }
    advance(50)
    // Col movimento ridotto la pagina è subito al suo posto, e l'interruttore cambia al tocco.
    compose.onNodeWithTag(FluidLabTags.experiment(big.key)).performClick()
    advance(50)
    assertTrue(lab.isEnabled(big))
  }

  // --- la diagnostica ---------------------------------------------------------------------------------------------------

  @Test
  fun consenso_chiestoUnaVoltaEIlSi() {
    val posts = ArrayList<String>()
    val diagnostics = FluidDiagnosticsTesting.create(
      app, FluidDiagnosticsConfig("Fluid Glass", endpoint = "http://127.0.0.1:8787/v1/reports"),
      transport = { _, body -> posts += String(body); 200 },
    )
    FluidDiagnosticsTesting.recordCrash(diagnostics, IllegalStateException("Nota «Diario di Marta»"))
    themed { FluidDiagnosticsConsentPrompt(diagnostics) }
    advance(800)
    compose.onNodeWithTag(FluidDiagnosticsTags.Consent).assertExists()
    compose.onNodeWithText("Aiuta a migliorare Fluid Glass").assertExists()
    compose.onNodeWithText("Cosa non parte mai", substring = true, ignoreCase = true).assertExists()
    assertTrue("prima del sì non parte niente", posts.isEmpty())
    compose.onNodeWithTag(FluidDiagnosticsTags.Share).performClick()
    advance(800)
    assertEquals(FluidDiagnosticsConsent.Granted, diagnostics.consent.value)
    assertEquals(1, posts.size)
    assertFalse(posts.single().contains("Marta"))
    assertTrue(compose.onAllNodesWithTag(FluidDiagnosticsTags.Consent).fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun consenso_ilNoSvuotaENonTornaPiu() {
    val diagnostics = FluidDiagnosticsTesting.create(app, FluidDiagnosticsConfig("Fluid Glass"))
    FluidDiagnosticsTesting.recordCrash(diagnostics, RuntimeException())
    themed(reduced = true) { FluidDiagnosticsConsentPrompt(diagnostics) }
    advance(400)
    compose.onNodeWithTag(FluidDiagnosticsTags.DontShare).performClick()
    advance(400)
    assertEquals(FluidDiagnosticsConsent.Denied, diagnostics.consent.value)
    assertEquals(0, diagnostics.queue.records().size)
    assertTrue(compose.onAllNodesWithTag(FluidDiagnosticsTags.Consent).fetchSemanticsNodes().isEmpty())
    // «Chiedi di nuovo» lo riapre.
    diagnostics.askAgain()
    advance(400)
    compose.onNodeWithTag(FluidDiagnosticsTags.Consent).assertExists()
  }

  @Test
  fun dati_ilJsonVeroEIlTastoCancella() {
    val diagnostics = FluidDiagnosticsTesting.create(app, FluidDiagnosticsConfig("Fluid Glass"))
    FluidDiagnosticsTesting.recordCrash(diagnostics, IllegalStateException("marta@example.com"))
    FluidDiagnosticsTesting.frame(diagnostics, "note", 300, 290, isJank = true)
    FluidDiagnosticsTesting.flushFrames(diagnostics)
    themed { FluidDiagnosticsDataPage(diagnostics, onBack = {}) }
    advance(600)
    compose.onNodeWithText("L'app si è chiusa").assertExists()
    compose.onNodeWithText("Fotogrammi di «note»").assertExists()
    compose.onNodeWithText("L'app si è chiusa").performClick()
    advance(800)
    val json = compose.onNodeWithTag(FluidDiagnosticsTags.Json).fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
    assertTrue(json, json.contains("\"kind\": \"crash\""))
    assertTrue(json.contains("IllegalStateException"))
    assertFalse(json.contains("marta"))
    compose.onNodeWithText("Chiudi").performClick()
    advance(800)
    compose.onNodeWithTag(FluidDiagnosticsTags.Clear).performClick()
    advance(600)
    compose.onNodeWithText("Cancella").performClick()
    advance(800)
    assertEquals(0, diagnostics.queue.records().size)
    compose.onNodeWithTag(FluidDiagnosticsTags.Empty).assertExists()
  }

  @Test
  fun dati_laRigaDelleImpostazioniContaIRapporti() {
    val diagnostics = FluidDiagnosticsTesting.create(app, FluidDiagnosticsConfig("Fluid Glass"))
    FluidDiagnosticsTesting.recordCrash(diagnostics, RuntimeException())
    FluidDiagnosticsTesting.recordCrash(diagnostics, RuntimeException())
    themed { FluidDiagnosticsSettingsGroup(diagnostics, onOpenData = {}) }
    advance(400)
    compose.onNodeWithText("2 rapporti sul telefono").assertExists()
    assertEquals(ToggleableState.Off, toggleState(FluidDiagnosticsTags.Toggle))
    compose.onNodeWithTag(FluidDiagnosticsTags.Toggle).performClick()
    advance(400)
    assertEquals(FluidDiagnosticsConsent.Granted, diagnostics.consent.value)
  }

  // --- Segnala un problema ----------------------------------------------------------------------------------------------

  private val feedbackConfig = FluidFeedbackConfig("Fluid Glass", FluidFeedbackGitHub("Casual76", "fluid-engine"))

  @Test
  fun segnala_nonDisturbaEMostraTuttoQuelloCheParte() {
    FluidLog.event("sync", "Sincronizzate 3 note")
    FluidLog.warn("posta", "Invio a marta.rossi@example.com non riuscito")
    val lab = lab()
    lab.setEnabled(big, true)
    lateinit var state: FluidFeedbackState
    // Col movimento ridotto: qui conta cosa parte, non come il foglio cambia altezza quando arrivano le informazioni.
    themed(reduced = true) {
      state = rememberFluidFeedbackState(feedbackConfig, lab = lab, diagnostics = null)
      FluidFeedbackHost(state) {
        FluidScreen(title = "Informazioni") { item { FluidListGroup { FluidFeedbackRow() } } }
      }
    }
    advance(400)
    // Chiuso finché non lo si chiede.
    assertTrue(compose.onAllNodesWithTag(FluidFeedbackTags.Sheet).fetchSemanticsNodes().isEmpty())
    compose.onNodeWithTag(FluidFeedbackTags.Row).performClick()
    advance(1_200)
    compose.onNodeWithTag(FluidFeedbackTags.Sheet).assertExists()
    // Senza una frase o uno screenshot non si manda niente.
    assertNotNull(
      compose.onNodeWithTag(FluidFeedbackTags.SendFile).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Disabled),
    )
    field(FluidFeedbackTags.Description).performTextInput("Il titolo è tagliato")
    advance(400)
    assertEquals("Il titolo è tagliato", state.draft.description)
    assertNotNull("le informazioni sono pronte", state.info)
    // Tutto quello che parte, in vista, coi dati personali tolti.
    compose.onNodeWithTag(FluidFeedbackTags.PreviewToggle).performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
    val preview = compose.onNodeWithTag(FluidFeedbackTags.Preview).fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
    assertTrue(preview, preview.contains("Il titolo è tagliato"))
    assertTrue(preview.contains("Anteprime grandi"))
    assertTrue(preview.contains("«[email]»"))
    assertFalse(preview.contains("marta"))
    // Spento, sparisce anche dall'anteprima.
    compose.onNodeWithTag(FluidFeedbackTags.include("eventi")).performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
    assertFalse(state.draft.includeLog)
    val after = compose.onNodeWithTag(FluidFeedbackTags.Preview).fetchSemanticsNode().config
      .getOrNull(SemanticsProperties.Text)!!.joinToString { it.text }
    assertFalse(after.contains("Ultimi eventi"))
  }

  @Test
  fun segnala_loZipVaNelFoglioCondividi() {
    // Sul JVM il FileProvider non trova le sue radici: gli Uri li dà la prova (come in F8p10StagingTest).
    FluidShareFiles.overrideUrisForTesting { Uri.fromFile(it) }
    try {
      FluidLog.warn("posta", "Invio a marta.rossi@example.com non riuscito")
      lateinit var state: FluidFeedbackState
      themed(reduced = true) {
        state = rememberFluidFeedbackState(feedbackConfig, diagnostics = null)
        FluidFeedbackHost(state) { Text("Pagina") }
      }
      advance(200)
      state.open()
      advance(800)
      field(FluidFeedbackTags.Description).performTextInput("La foto non si carica")
      advance(400)
      compose.onNodeWithTag(FluidFeedbackTags.SendFile).performSemanticsAction(SemanticsActions.OnClick)
      // Lo ZIP si scrive su Dispatchers.IO: si aspetta che ci sia, con un tetto.
      val shareDir = File(app.cacheDir, FluidShareFiles.Folder)
      var zip: File? = null
      repeat(50) {
        if (zip == null) {
          advance(64)
          Thread.sleep(10)
          zip = shareDir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(".zip") }
        }
      }
      advance(800)
      val file = zip!!
      assertTrue(file.name, file.name.matches(Regex("Segnalazione-fluid-glass-\\d{8}-\\d{4}\\.zip")))
      // Il foglio Condividi si apre con lo ZIP; il foglio della segnalazione esce.
      compose.onNodeWithTag(FluidShareSheetTags.Sheet).assertExists()
      compose.onNodeWithText(file.name, substring = true).assertExists()
      assertTrue(compose.onAllNodesWithTag(FluidFeedbackTags.Sheet).fetchSemanticsNodes().isEmpty())
      val entries = ZipInputStream(file.inputStream()).use { z -> generateSequence { z.nextEntry }.map { it.name to z.readBytes() }.toList() }
      assertEquals("LEGGIMI.txt", entries.first().first)
      val all = entries.joinToString("\n") { String(it.second, Charsets.UTF_8) }
      assertTrue(all.contains("La foto non si carica"))
      assertTrue(all.contains("«[email]»"))
      assertFalse(all.contains("marta"))
    } finally {
      FluidShareFiles.overrideUrisForTesting(null)
    }
  }

  @Test
  fun segnala_laIssueSiApreNelBrowserGiaScritta() {
    lateinit var state: FluidFeedbackState
    themed(reduced = true) {
      state = rememberFluidFeedbackState(feedbackConfig, diagnostics = null)
      FluidFeedbackHost(state) { Text("Pagina") }
    }
    advance(200)
    state.open()
    advance(800)
    field(FluidFeedbackTags.Description).performTextInput("La foto non si carica")
    field(FluidFeedbackTags.Credit).performTextInput("@mario")
    advance(400)
    compose.onNodeWithTag(FluidFeedbackTags.OpenIssue).performClick()
    advance(800)
    val started: Intent = shadowOf(app).nextStartedActivity
    assertEquals(Intent.ACTION_VIEW, started.action)
    val uri: Uri = started.data!!
    assertEquals("github.com", uri.host)
    assertEquals("/Casual76/fluid-engine/issues/new", uri.path)
    assertEquals("[Fluid Glass 2.0.0] La foto non si carica", uri.getQueryParameter("title"))
    assertTrue(uri.getQueryParameter("body")!!.contains("Segnalato da @mario"))
    // Mandata: la bozza si dimentica e il foglio si chiude.
    assertEquals("", state.draft.description)
    assertFalse(state.isOpen)
  }

  @Test
  fun segnala_laCapsulaDellaCatturaEAnnulla() {
    lateinit var state: FluidFeedbackState
    themed {
      state = rememberFluidFeedbackState(feedbackConfig, diagnostics = null)
      FluidFeedbackHost(state) { Text("Pagina") }
    }
    advance(200)
    state.open()
    advance(1_200)
    compose.onNodeWithTag(FluidFeedbackTags.Capture).performClick()
    advance(1_200)
    // Il foglio esce, resta la capsula sopra la pagina.
    assertTrue(compose.onAllNodesWithTag(FluidFeedbackTags.Sheet).fetchSemanticsNodes().isEmpty())
    compose.onNodeWithTag(FluidFeedbackTags.PillCapture).assertExists()
    compose.onNodeWithText("Pagina").assertExists()
    // «Annulla» riporta al foglio, con la bozza.
    compose.onNodeWithTag(FluidFeedbackTags.PillCancel).performClick()
    advance(1_200)
    compose.onNodeWithTag(FluidFeedbackTags.Sheet).assertExists()
  }

  @Test
  fun leScene() {
    for (id in listOf("laboratorio", "segnala-problema", "diagnostica", "scatti-per-pagina", "tracce")) {
      assertNotNull(id, newElementScene(id))
    }
    assertEquals("Bene", f8p24Verdict(2.0))
    assertEquals("Si vede", f8p24Verdict(7.5))
    assertEquals("Disturba", f8p24Verdict(12.0))
    assertTrue(FluidDiagnostics.get() == null)
  }
}
