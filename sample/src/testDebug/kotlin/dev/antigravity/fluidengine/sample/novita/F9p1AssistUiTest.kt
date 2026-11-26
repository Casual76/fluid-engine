package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistConsent
import dev.antigravity.fluidengine.ai.bridge.assist.AiAssistConsentState
import dev.antigravity.fluidengine.aiui.chat.FluidComposer
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.FluidComposerPagePill
import dev.antigravity.fluidengine.aiui.chat.FluidPageContextStrings
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F9.1 · Le parti d'interfaccia nuove: il chip «Questa pagina» (TalkBack: un nodo, lo stato, le azioni; il bersaglio da
 * 48 dp) e la scena «Chiedi da una pagina» col PampAI finto vero (la risposta che arriva sfocata, poi ferma; con «Riduci
 * movimento» ferma da subito; le righe che cambiano lette da TalkBack).
 *
 * F9.1d, dalle riprese del tablet: i suggerimenti sono capsule di vetro, lo spazio in fondo segue la risposta aperta
 * (la sezione di PampAI finto esce da sotto), e «non autorizzata» non ha «Riprova». Le catture (vetro di ripiego,
 * `Balanced`: col materiale `Liquid` Robolectric non compila le lastre) finiscono in `sample/build/f9p1d-pixels/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F9p1AssistUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val pkg: String get() = compose.activity.packageName

  @After fun stop() {
    F9p1FakePampai.host(compose.activity).cancelAll()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun waitFor(what: String, condition: () -> Boolean) {
    repeat(700) {
      if (condition()) return
      advance(32)
      Thread.sleep(4)
    }
    throw AssertionError("non è successo: $what")
  }

  private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun pill() = compose.onNodeWithTag("pill").fetchSemanticsNode()

  private fun actions(): List<String> = pill().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

  @Test fun `il chip «Questa pagina» - un nodo per TalkBack con titolo, stato e azioni, toglierlo e rimetterlo`() {
    compose.mainClock.autoAdvance = false
    var included by mutableStateOf(true)
    var shown = 0
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val glass = rememberGlassBackdrop()
        FluidComposer(
          value = "", onValueChange = {}, backdrop = glass, action = FluidComposerAction.Send, onSend = {}, requestMicPermission = false,
          pills = {
            FluidComposerPagePill(F9p1Page.title, included, onIncludedChange = { included = it }, onShowContents = { shown++ }, modifier = Modifier.testTag("pill"))
          },
        )
      }
    }
    advance(200)
    val node = pill()
    assertEquals(listOf("Questa pagina: ${F9p1Page.title}"), node.config.getOrNull(SemanticsProperties.ContentDescription))
    assertEquals("Passa a PampAI con la domanda", node.config.getOrNull(SemanticsProperties.StateDescription))
    assertEquals(listOf("Mostra cosa passa", "Togli la pagina"), actions())
    // Il bersaglio sta dentro la misura della pillola (che al genitore dice 30 dp, l'altezza disegnata): la sua altezza e'
    // quella dei modificatori interni del nodo.
    val target = node.layoutInfo.getModifierInfo().maxOf { it.coordinates.size.height }
    assertTrue("il bersaglio e' alto 48 dp: $target", target >= with(compose.density) { 48.dp.toPx() } - 1f)
    compose.onNodeWithTag("pill").performSemanticsAction(SemanticsActions.OnClick)
    assertEquals(1, shown)
    val remove = pill().config[SemanticsActions.CustomActions].first { it.label == "Togli la pagina" }
    compose.runOnUiThread { remove.action() }
    advance(64)
    assertFalse(included)
    assertEquals("Non passa", pill().config.getOrNull(SemanticsProperties.StateDescription))
    assertEquals(listOf("Aggiungi la pagina"), actions())
    compose.onNodeWithTag("pill").performSemanticsAction(SemanticsActions.OnClick)
    advance(64)
    assertTrue(included)
    assertEquals("This page: x", FluidPageContextStrings.english().description.replace("%1\$s", "x"))
  }

  private fun open(reduced: Boolean, scene: String = "pampai-chiedi") {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default) {
          val host = rememberFluidGlassModalHostState()
          CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
            Box(Modifier.fillMaxSize()) {
              NewElementsPageAt(onBack = {}, initial = scene)
              FluidGlassModalHost(state = host, backdrop = null)
            }
          }
        }
      }
    }
    advance(600)
  }

  private fun arriving(): List<Boolean> = compose.onAllNodes(SemanticsMatcher.keyIsDefined(F9p1Arriving), useUnmergedTree = true)
    .fetchSemanticsNodes().map { it.config[F9p1Arriving] }

  /**
   * Tocca un suggerimento. F9.1d: e' un `FluidChipPill`, che mette il `modifier` (e il tag) sul contenitore di vetro e il
   * tocco sul tasto dentro: si cerca il nodo che si tocca, sotto il tag.
   */
  private fun ask(index: Int) {
    val tag = F9p1Tags.suggestion(index)
    compose.onNode(hasClickAction() and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))), useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.OnClick)
    advance(64)
  }

  @Test fun `la scena - col sì la risposta arriva sfocata, con la riga di stato letta da TalkBack, poi ferma`() {
    F9p1FakePampai.host(compose.activity).setAppConsent(pkg, AiAssistConsentState.Allowed)
    F9p1FakePampai.host(compose.activity).setPageConsent(pkg, AiAssistConsentState.Allowed)
    open(reduced = false)
    waitFor("il PampAI finto trovato") { compose.onAllNodes(hasTestTag(F9p1Tags.OpenPampai), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    advance(800)
    ask(0)
    waitFor("la riga di stato") { has(F9p1Tags.Status) }
    val status = compose.onNodeWithTag(F9p1Tags.Status, useUnmergedTree = true).fetchSemanticsNode()
    assertEquals(LiveRegionMode.Polite, status.config.getOrNull(SemanticsProperties.LiveRegion))
    var sawArriving = false
    waitFor("il testo che arriva") { arriving().any { it }.also { if (it) sawArriving = true } }
    waitFor("la fine") { has(F9p1Tags.AnswerText) && arriving().none { it } && !has(F9p1Tags.Status) }
    assertTrue(sawArriving)
    val text = compose.onNodeWithTag(F9p1Tags.AnswerText, useUnmergedTree = true).fetchSemanticsNode()
      .config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
    assertTrue("la risposta usa la pagina: $text", text.contains(F9p1Page.title))
  }

  @Test fun `con Riduci movimento la risposta compare ferma, e la ricevuta c'e'`() {
    F9p1FakePampai.host(compose.activity).setAppConsent(pkg, AiAssistConsentState.Allowed)
    F9p1FakePampai.host(compose.activity).setPageConsent(pkg, AiAssistConsentState.Allowed)
    open(reduced = true)
    waitFor("il PampAI finto trovato") { has(F9p1Tags.OpenPampai) }
    advance(800)
    ask(1)
    waitFor("la ricevuta") { has(F9p1Tags.Receipt) }
    waitFor("il testo") { has(F9p1Tags.AnswerText) }
    repeat(20) { assertTrue("niente sfocatura con Riduci movimento", arriving().none { it }); advance(48) }
  }

  @Test fun `la prima volta - il tocco apre il foglio di PampAI con la pagina, e col sì la domanda parte`() {
    val host = F9p1FakePampai.host(compose.activity)
    host.forget(pkg)
    open(reduced = true)
    waitFor("lo stato di PampAI finto, con la galleria da chiedere") {
      compose.onAllNodes(androidx.compose.ui.test.hasText("la galleria: da chiedere", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    ask(0)
    waitFor("il foglio di PampAI") { shadowOf(compose.activity).peekNextStartedActivityForResult() != null }
    val started = shadowOf(compose.activity).nextStartedActivityForResult
    assertEquals(AiAssistConsent.ACTION, started.intent.action)
    assertTrue("un foglio solo, anche per la pagina", AiAssistConsent.wantsPage(started.intent))
    assertFalse("prima del sì nessuna risposta", has(F9p1Tags.AnswerText))
    // L'utente tocca «Consenti» nel foglio di PampAI finto.
    val step = AiAssistConsent(host).begin(pkg, withPage = true) as AiAssistConsent.Step.Ask
    val decided = AiAssistConsent(host).answer(step, step.choices.first())
    compose.runOnUiThread { shadowOf(compose.activity).receiveResult(started.intent, android.app.Activity.RESULT_OK, AiAssistConsent.resultIntent(decided)) }
    waitFor("la risposta con la pagina") {
      has(F9p1Tags.AnswerText) && compose.onNodeWithTag(F9p1Tags.AnswerText, useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty().contains(F9p1Page.title)
    }
  }

  private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  /** Fino in fondo alla pagina: l'ultimo elemento e' lo spazio sotto la sezione di PampAI. */
  private fun toEnd() {
    compose.onNode(hasScrollToKeyAction()).performScrollToKey("sotto-pampai")
    advance(400)
  }

  private fun allowed() {
    F9p1FakePampai.host(compose.activity).setAppConsent(pkg, AiAssistConsentState.Allowed)
    F9p1FakePampai.host(compose.activity).setPageConsent(pkg, AiAssistConsentState.Allowed)
  }

  private fun capture(name: String) {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnUiThread { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f9p1d-pixels").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test fun `lo spazio in fondo segue la risposta aperta - fino in fondo la sezione di PampAI esce da sotto`() {
    allowed()
    open(reduced = true)
    waitFor("il PampAI finto trovato") { has(F9p1Tags.OpenPampai) }
    advance(800)
    toEnd()
    val before = bounds(F9p1Tags.BottomSpace).height
    assertTrue("lo spazio c'e' gia' per il composer e le pillole: $before", before > with(compose.density) { 150.dp.toPx() })
    ask(0)
    waitFor("la fine") { has(F9p1Tags.AnswerText) && !has(F9p1Tags.Status) }
    advance(400)
    toEnd()
    val answer = bounds(F9p1Tags.Answer)
    val after = bounds(F9p1Tags.BottomSpace).height
    assertTrue("lo spazio e' cresciuto con la risposta: $before -> $after, risposta ${answer.height}", after - before >= with(compose.density) { 100.dp.toPx() })
    val row = bounds(F9p1Tags.OpenPampai)
    val section = bounds(F9p1Tags.PampaiSection)
    assertTrue("«Apri PampAI finto» sopra la risposta: ${row.bottom} <= ${answer.top}", row.bottom <= answer.top + 0.5f)
    assertTrue("la nota della sezione sopra la risposta: ${section.bottom} <= ${answer.top}", section.bottom <= answer.top + 0.5f)
  }

  @Test fun `Non consentire - la risposta dice non autorizzata senza Riprova, e il tasto si spegne`() {
    val host = F9p1FakePampai.host(compose.activity)
    host.forget(pkg)
    open(reduced = true)
    waitFor("lo stato di PampAI finto, con la galleria da chiedere") {
      compose.onAllNodes(hasText("la galleria: da chiedere", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    ask(0)
    waitFor("il foglio di PampAI") { shadowOf(compose.activity).peekNextStartedActivityForResult() != null }
    val started = shadowOf(compose.activity).nextStartedActivityForResult
    val step = AiAssistConsent(host).begin(pkg, withPage = true) as AiAssistConsent.Step.Ask
    val decided = AiAssistConsent(host).answer(step, step.choices.single { !it.allowApp })
    compose.runOnUiThread { shadowOf(compose.activity).receiveResult(started.intent, android.app.Activity.RESULT_OK, AiAssistConsent.resultIntent(decided)) }
    waitFor("la risposta che dice non autorizzata, e il tasto spento") { has(F9p1Tags.Error) && has(F9p1Tags.DisabledEntry) }
    assertTrue(compose.onAllNodes(hasTestTag(F9p1Tags.Error) and hasAnyDescendant(hasText("non ha autorizzato", substring = true)), useUnmergedTree = true)
      .fetchSemanticsNodes().isNotEmpty())
    repeat(10) { assertFalse("niente «Riprova» per non autorizzata", has(F9p1Tags.Retry)); advance(48) }
  }

  @Test fun `i suggerimenti sono capsule di vetro da 40 dp, con le catture per il rapporto`() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    try {
      allowed()
      open(reduced = true)
      waitFor("il PampAI finto trovato") { has(F9p1Tags.OpenPampai) }
      advance(800)
      val min = with(compose.density) { 40.dp.toPx() } - 1f
      repeat(3) { i -> bounds(F9p1Tags.suggestion(i)).height.let { assertTrue("il suggerimento $i e' alto $it", it >= min) } }
      capture("pillole-sopra-la-pagina")
      ask(0)
      waitFor("la fine") { has(F9p1Tags.AnswerText) && !has(F9p1Tags.Status) }
      advance(400)
      capture("risposta-in-cima")
      toEnd()
      capture("risposta-fino-in-fondo")
    } finally {
      FluidGlassDiagnostics.resetForTesting()
    }
  }

  @Test fun `senza PampAI il tasto e' spento per TalkBack, e Installa PampAI senza lo Store spiega`() {
    open(reduced = true, scene = "pampai-assente")
    waitFor("il tasto spento") { has(F9p1Tags.DisabledEntry) }
    assertFalse("niente composer senza PampAI", has(F9p1Tags.Composer))
    val node = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled), useUnmergedTree = true).fetchSemanticsNodes()
      .single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.startsWith("Chiedi a PampAI") == true }
    assertEquals(listOf("Chiedi a PampAI: PampAI non c'è"), node.config.getOrNull(SemanticsProperties.ContentDescription))
    compose.onNodeWithTag(F9p1Tags.EntryAction, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    waitFor("la spiegazione") { has(F9p1Tags.InstallSheet) }
    assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasText("si installa da Pampa Store", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
  }
}
