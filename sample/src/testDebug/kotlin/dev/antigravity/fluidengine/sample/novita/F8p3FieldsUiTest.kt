package dev.antigravity.fluidengine.sample.novita

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.net.Uri
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.autofill.FillableData
import androidx.compose.ui.autofill.createFromText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidPasswordRules
import dev.antigravity.fluidengine.foundation.FluidUnit
import dev.antigravity.fluidengine.foundation.FluidUnitDimension
import dev.antigravity.fluidengine.foundation.FluidUnits
import dev.antigravity.fluidengine.ui.fluid.FluidCodeField
import dev.antigravity.fluidengine.ui.fluid.FluidCodeFieldState
import dev.antigravity.fluidengine.ui.fluid.FluidCopyButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidPasswordField
import dev.antigravity.fluidengine.ui.fluid.FluidPasteButton
import dev.antigravity.fluidengine.ui.fluid.FluidPasteKind
import dev.antigravity.fluidengine.ui.fluid.FluidPasted
import dev.antigravity.fluidengine.ui.fluid.FluidPrivateState
import dev.antigravity.fluidengine.ui.fluid.FluidSensitiveClipboard
import dev.antigravity.fluidengine.ui.fluid.FluidSpecialFieldLabels
import dev.antigravity.fluidengine.ui.fluid.FluidUnitField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidDiscreetMode
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidPrivate
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.math.BigDecimal
import java.time.Duration
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowClipboardManager

/**
 * Gli appunti che contano le letture del **contenuto**: la prova che il tasto incolla decide dalla sola descrizione, così
 * Android non mostra l'avviso «ha incollato dagli appunti» prima del tocco.
 */
@Implements(ClipboardManager::class)
class CountingClipboardShadow : ShadowClipboardManager() {
  @Implementation
  override fun getPrimaryClip(): ClipData? {
    contentReads++
    return super.getPrimaryClip()
  }

  companion object {
    @JvmStatic var contentReads = 0
  }
}

/**
 * F8.3 · i campi speciali in un albero Compose vero, col dito vero (le azioni di semantica che usa TalkBack e la tastiera):
 * il campo con unità in italiano e in inglese, il campo a codice che si riempie incollando e con l'autocompilazione e trema
 * all'errore, le regole della password, il velo che si ricopre e la finestra fuori dalle recenti, il clip riservato con la
 * sua scadenza, il tasto incolla che compare solo coi dati concordati.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi", shadows = [CountingClipboardShadow::class])
class F8p3FieldsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
    CountingClipboardShadow.contentReads = 0
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  private fun show(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val modals = rememberFluidGlassModalHostState()
        val policy = if (reduced) FluidMotionPolicy(durationScale = 0f) else LocalFluidMotionPolicy.current
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals, LocalFluidMotionPolicy provides policy) {
          Box(Modifier.fillMaxSize()) {
            Column { content() }
            FluidGlassModalHost(modals, backdrop = null)
          }
        }
      }
    }
    advance(600)
  }

  private val clipboard: ClipboardManager
    get() = compose.activity.getSystemService(ClipboardManager::class.java)

  private fun editable(tag: String): String =
    compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

  private fun hasNode(matcher: SemanticsMatcher): Boolean =
    compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  // --- Il campo con unità ---------------------------------------------------------------------------------------

  @Test fun `il campo con unità si scrive in italiano, mostra le migliaia e restituisce il numero`() {
    var value by mutableStateOf<BigDecimal?>(null)
    show {
      FluidUnitField(value = value, onValueChange = { value = it }, unit = FluidUnits.Kilometer, locale = Locale.ITALY,
        convertTo = FluidUnits.Mile, modifier = Modifier.testTag("km"))
    }
    val field = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
    "1234,5".forEach { field.performTextInput(it.toString()); advance(32) }
    assertEquals(0, value!!.compareTo(BigDecimal("1234.5")))
    assertEquals("1.234,5", compose.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()
      .config[SemanticsProperties.EditableText].text)
    // La conversione sotto: 1234,5 km = 767,08 mi (1234,5 / 1,609344 = 767,0827…).
    val texts = compose.onAllNodes(SemanticsMatcher("testo") { SemanticsProperties.Text in it.config }, useUnmergedTree = true)
      .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
    assertTrue("testi: $texts", texts.any { it == "≈ 767,08 mi" })
  }

  @Test fun `in inglese il punto è il decimale, e l'incolla da un'altra lingua si legge`() {
    var value by mutableStateOf<BigDecimal?>(null)
    show {
      FluidUnitField(value = value, onValueChange = { value = it }, unit = FluidUnits.Euro, locale = Locale.US)
    }
    val field = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
    "1249.9".forEach { field.performTextInput(it.toString()); advance(32) }
    assertEquals(0, value!!.compareTo(BigDecimal("1249.9")))
    assertEquals("1,249.9", compose.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()
      .config[SemanticsProperties.EditableText].text)
    // Il simbolo dell'euro sta davanti in inglese (la capsula prima del testo).
    assertTrue(hasNode(hasContentDescription("€")))
    field.performTextReplacement("1.234,56")
    advance(100)
    assertEquals(0, value!!.compareTo(BigDecimal("1234.56")))
  }

  @Test fun `la capsula apre il menu delle unità e il valore si converte`() {
    var value by mutableStateOf<BigDecimal?>(BigDecimal("10"))
    var unit by mutableStateOf<FluidUnit>(FluidUnits.Kilometer)
    show {
      FluidUnitField(value = value, onValueChange = { value = it }, unit = unit, locale = Locale.ITALY,
        units = FluidUnits.of(FluidUnitDimension.Length), onUnitChange = { unit = it })
    }
    // Il dito vero sulla capsula. (Con la sola azione di semantica, in Robolectric, il menu contestuale dell'engine non
    // compare: è un limite del menu, scritto nel rapporto di F8.3, da provare con TalkBack sul dispositivo.)
    compose.onNode(hasContentDescription("Cambia unità"), useUnmergedTree = true).performClick()
    advance(900)
    // Le voci del menu si scelgono col dito vero (il menu dell'engine segue il dito, non un'azione di semantica).
    val items = compose.onAllNodes(hasText("mi"))
    val shown = compose.onAllNodes(SemanticsMatcher("testo") { SemanticsProperties.Text in it.config })
      .fetchSemanticsNodes().flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }
    assertTrue("il menu delle unità non si è aperto: $shown", items.fetchSemanticsNodes().isNotEmpty())
    items.onFirst().performClick()
    advance(900)
    assertEquals(FluidUnits.Mile, unit)
    assertEquals(0, value!!.compareTo(BigDecimal("6.21")))
  }

  /** Visto sul tablet: passando da italiano a inglese il testo restava con la virgola («1,249,,90»). */
  @Test fun `cambiando lingua il testo si riscrive coi separatori nuovi`() {
    var locale by mutableStateOf(Locale.ITALY)
    show {
      FluidUnitField(value = BigDecimal("1249.9"), onValueChange = {}, unit = FluidUnits.Euro, locale = locale)
    }
    val text = { compose.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()
      .config[SemanticsProperties.EditableText].text }
    assertEquals("1.249,90", text())
    locale = Locale.US
    Snapshot.sendApplyNotifications()
    advance(200)
    assertEquals("1,249.90", text())
  }

  // --- Il campo a codice ----------------------------------------------------------------------------------------

  @Test fun `il codice si riempie incollando tutto in un colpo, e chiama onComplete una volta`() {
    val state = FluidCodeFieldState(6)
    val completed = mutableListOf<String>()
    show { FluidCodeField(state = state, onComplete = { completed += it }, modifier = Modifier.testTag("codice")) }
    val field = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
    assertEquals("cifra 1 di 6", field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    assertEquals(ContentType.SmsOtpCode, field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentType))
    field.performTextReplacement("Il tuo codice MyCloud è 482913")
    advance(200)
    assertEquals("482913", state.code)
    assertEquals(listOf("482913"), completed)
    assertEquals("codice completo", field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
  }

  @OptIn(ExperimentalComposeUiApi::class)
  @Test fun `il codice proposto dall'autocompilazione riempie tutte le caselle`() {
    val state = FluidCodeFieldState(6)
    val completed = mutableListOf<String>()
    show { FluidCodeField(state = state, onComplete = { completed += it }) }
    compose.onNode(hasSetTextAction(), useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.OnFillData) { it(FillableData.createFromText("482913")!!) }
    advance(200)
    assertEquals("482913", state.code)
    assertEquals(listOf("482913"), completed)
  }

  @Test fun `il codice si scrive cifra per cifra e il cancella torna indietro`() {
    val state = FluidCodeFieldState(4)
    show { FluidCodeField(state = state, onComplete = {}) }
    val field = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
    "25".forEach { field.performTextInput(it.toString()); advance(32) }
    assertEquals("25", state.code)
    assertEquals("cifra 3 di 4", field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    field.performTextReplacement("2")
    advance(32)
    assertEquals("2", state.code)
  }

  @Test fun `il codice sbagliato trema, prende l'errore e si svuota`() {
    val state = FluidCodeFieldState(6)
    show { FluidCodeField(state = state, onComplete = { if (it != "482913") state.fail() }) }
    compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement("111111")
    advance(100)
    assertTrue(state.isError)
    assertEquals("Codice non corretto", compose.onNode(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNode()
      .config.getOrNull(SemanticsProperties.Error))
    // Durante la scossa il codice si vede ancora; dopo, le caselle sono vuote.
    assertEquals("111111", state.code)
    advance(800)
    assertEquals("", state.code)
    assertTrue(state.isError)
    compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextInput("4")
    advance(32)
    assertFalse(state.isError)
  }

  @Test fun `con riduci movimento il codice sbagliato si svuota senza scossa`() {
    val state = FluidCodeFieldState(4)
    show(reduced = true) { FluidCodeField(state = state, onComplete = { state.fail() }) }
    compose.onNode(hasSetTextAction(), useUnmergedTree = true).performTextReplacement("1234")
    advance(300)
    assertEquals("", state.code)
  }

  // --- La password ----------------------------------------------------------------------------------------------

  @Test fun `le regole si spuntano mentre si scrive`() {
    var password by mutableStateOf("")
    show { FluidPasswordField(value = password, onValueChange = { password = it }, label = "Nuova password") }
    assertTrue(hasNode(hasContentDescription("Almeno 8 caratteri, da fare")))
    val field = compose.onNode(hasSetTextAction(), useUnmergedTree = true)
    assertEquals(ContentType.NewPassword, field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentType))
    assertTrue(SemanticsProperties.Password in field.fetchSemanticsNode().config)
    "ciaoCiao".forEach { field.performTextInput(it.toString()); advance(32) }
    assertTrue(hasNode(hasContentDescription("Almeno 8 caratteri, fatto")))
    assertTrue(hasNode(hasContentDescription("Una lettera maiuscola, fatto")))
    assertTrue(hasNode(hasContentDescription("Un numero, da fare")))
    field.performTextInput("7")
    advance(400)
    assertTrue(hasNode(hasContentDescription("Un numero, fatto")))
    assertTrue(hasNode(hasContentDescription("Forza stimata: discreta")) || hasNode(hasContentDescription("Forza stimata: buona")) ||
      hasNode(hasContentDescription("Forza stimata: debole")))
  }

  @Test fun `la password forte proposta rispetta le regole e si scrive nel campo`() {
    var password by mutableStateOf("")
    val rules = FluidPasswordRules.Default
    show { FluidPasswordField(value = password, onValueChange = { password = it }, rules = rules) }
    compose.onNode(hasSetTextAction(), useUnmergedTree = true).performSemanticsAction(SemanticsActions.RequestFocus)
    advance(600)
    val use = compose.onAllNodes(hasText("Usa password forte"), useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue("la proposta non c'è", use.isNotEmpty())
    compose.onAllNodes(hasText("Usa password forte")).fetchSemanticsNodes().first().let { node ->
      compose.runOnUiThread { node.config[SemanticsActions.OnClick].action?.invoke() }
    }
    Snapshot.sendApplyNotifications()
    advance(600)
    assertEquals(20, password.length)
    assertTrue(Regex("[A-Za-z0-9]{6}-[A-Za-z0-9]{6}-[A-Za-z0-9]{6}").matches(password))
    assertTrue(rules.isSatisfiedBy(password))
    // Mostrata in chiaro, come iOS: l'occhio dice «Nascondi».
    assertTrue(hasNode(hasContentDescription("Nascondi il testo")))
  }

  // --- Il valore riservato --------------------------------------------------------------------------------------

  @Test fun `il velo copre il valore, il tocco lo scopre e si ricopre da solo`() {
    val state = FluidPrivateState()
    show {
      Text("sk-or-v1-segreto", modifier = Modifier.testTag("chiave").fluidPrivate(state, recoverAfter = 3.seconds,
        blockScreenshots = true))
    }
    // Coperto: il segreto non è nell'albero di TalkBack.
    assertFalse(hasNode(hasText("sk-or-v1-segreto")))
    assertTrue(hasNode(hasContentDescription("Valore nascosto, Tocca per vedere")))
    compose.onNode(hasContentDescription("Valore nascosto, Tocca per vedere"), useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
    assertTrue(state.isRevealed)
    assertTrue(hasNode(hasText("sk-or-v1-segreto")))
    // Scoperto: la finestra è protetta (niente recenti, niente screenshot).
    val flags = { compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE }
    assertTrue(flags() != 0)
    advance(2_400)
    assertTrue(state.isRevealed)
    advance(1_000)
    assertFalse(state.isRevealed)
    assertFalse(hasNode(hasText("sk-or-v1-segreto")))
    assertEquals(0, flags())
  }

  @Test fun `la modalità discreta vela i valori discreti tutti insieme`() {
    var discreet by mutableStateOf(false)
    show {
      CompositionLocalProvider(LocalFluidDiscreetMode provides discreet) {
        Text("8½", modifier = Modifier.fluidPrivate(remember { FluidPrivateState() }, discreetOnly = true))
        Text("9", modifier = Modifier.fluidPrivate(remember { FluidPrivateState() }, discreetOnly = true))
      }
    }
    assertTrue(hasNode(hasText("8½")))
    discreet = true
    Snapshot.sendApplyNotifications()
    advance(400)
    assertFalse(hasNode(hasText("8½")))
    assertFalse(hasNode(hasText("9")))
    assertEquals(2, compose.onAllNodes(hasContentDescription("Valore nascosto, Tocca per vedere"), useUnmergedTree = true)
      .fetchSemanticsNodes().size)
  }

  // --- La copia delicata ----------------------------------------------------------------------------------------

  @Test fun `il clip è riservato e si cancella alla scadenza`() {
    show { Text("pagina") }
    val copy = compose.runOnUiThread {
      FluidSensitiveClipboard.copy(compose.activity, "sk-or-v1-segreto", expireAfter = 60.seconds)
    }
    val description = clipboard.primaryClipDescription!!
    assertTrue(description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    assertTrue(copy.isPending)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(59))
    assertTrue(clipboard.hasPrimaryClip() && clipboard.primaryClip!!.getItemAt(0).text.isNotEmpty())
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
    assertFalse(copy.isPending)
    assertTrue(copy.wasCleared)
    val left = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
    assertEquals("", left)
  }

  @Test fun `se la persona copia altro, alla scadenza non si cancella niente`() {
    show { Text("pagina") }
    val copy = compose.runOnUiThread {
      FluidSensitiveClipboard.copy(compose.activity, "sk-or-v1-segreto", expireAfter = 10.seconds)
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("", "la lista della spesa"))
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11))
    assertFalse(copy.isPending)
    assertFalse(copy.wasCleared)
    assertEquals("la lista della spesa", clipboard.primaryClip!!.getItemAt(0).text.toString())
  }

  @Test fun `il tasto dice Copiato e il tempo che resta`() {
    show { FluidCopyButton(text = { "sk-or-v1-segreto" }, label = "Copia", expireAfter = 60.seconds) }
    compose.onAllNodes(hasContentDescription("Copia") or hasText("Copia"), useUnmergedTree = true).fetchSemanticsNodes()
      .first { SemanticsActions.OnClick in it.config }
      .let { node -> compose.runOnUiThread { node.config[SemanticsActions.OnClick].action?.invoke() } }
    Snapshot.sendApplyNotifications()
    advance(1_200)
    assertTrue(clipboard.primaryClipDescription!!.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
    val spoken = compose.onAllNodes(SemanticsMatcher("Copiato") { node ->
      node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Copiato, Si cancella fra") } == true
    }, useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue("il tasto non dice il tempo che resta", spoken.isNotEmpty())
  }

  /**
   * Visto sul tablet: finito il contatore, il tasto «Copia» restava un vetro vuoto (la faccia di riposo aveva la chiave
   * `null`, che per la transizione del contenuto è il vuoto). Si contano i pixel scuri del tasto prima e dopo.
   */
  @Test fun `finita la copia il tasto torna Copia, non un vetro vuoto`() {
    show {
      Box(Modifier.testTag("copia")) {
        FluidCopyButton(text = { "sk-or-v1-segreto" }, label = "Copia", expireAfter = 2.seconds)
      }
    }
    fun ink(): Int {
      // La finestra con PixelCopy (captureToImage non gira sotto Robolectric), ritagliata sul tasto.
      val window = compose.activity.window
      val full = android.graphics.Bitmap.createBitmap(window.decorView.width, window.decorView.height,
        android.graphics.Bitmap.Config.ARGB_8888)
      compose.runOnUiThread {
        android.view.PixelCopy.request(window, full, { }, android.os.Handler(Looper.getMainLooper()))
      }
      shadowOf(Looper.getMainLooper()).idle()
      val box = compose.onNodeWithTag("copia").fetchSemanticsNode().boundsInWindow
      var dark = 0
      for (y in box.top.toInt().coerceAtLeast(0) until box.bottom.toInt().coerceAtMost(full.height) step 2) {
        for (x in box.left.toInt().coerceAtLeast(0) until box.right.toInt().coerceAtMost(full.width) step 2) {
          val p = full.getPixel(x, y)
          val l = (android.graphics.Color.red(p) + android.graphics.Color.green(p) + android.graphics.Color.blue(p)) / 3
          if (l < 90) dark++
        }
      }
      return dark
    }
    val before = ink()
    assertTrue("il tasto a riposo non ha inchiostro", before > 20)
    compose.onAllNodes(hasContentDescription("Copia"), useUnmergedTree = true).fetchSemanticsNodes()
      .first { SemanticsActions.OnClick in it.config }
      .let { node -> compose.runOnUiThread { node.config[SemanticsActions.OnClick].action?.invoke() } }
    Snapshot.sendApplyNotifications()
    advance(800)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2_200))
    advance(2_500)
    val after = ink()
    assertTrue("dopo la copia il tasto è vuoto: $after pixel scuri contro $before", after > before / 2)
  }

  // --- Il tasto incolla -----------------------------------------------------------------------------------------

  /** Mette negli appunti [text] con la classificazione di Android (i metodi nascosti di `ClipDescription`). */
  private fun copyClassified(text: String, scores: Map<String, Float>) {
    val clip = ClipData.newPlainText("", text)
    val description = clip.description
    ClipDescription::class.java.getDeclaredMethod("setConfidenceScores", Map::class.java)
      .apply { isAccessible = true }.invoke(description, scores)
    ClipDescription::class.java.getDeclaredMethod("setClassificationStatus", Int::class.javaPrimitiveType)
      .apply { isAccessible = true }.invoke(description, ClipDescription.CLASSIFICATION_COMPLETE)
    compose.runOnUiThread { clipboard.setPrimaryClip(clip) }
    Snapshot.sendApplyNotifications()
    advance(600)
  }

  /**
   * La classificazione «otp» la dà Android 16 (`TextClassifier.TYPE_OTP`); qui gira su Android 15 perché Robolectric con
   * Android 16 vuole Java 21. La strada del tasto è la stessa: la descrizione classificata, mai il contenuto.
   */
  @Test
  fun `il tasto del codice compare solo quando Android dice che è un codice, senza leggere gli appunti prima del tocco`() {
    var pasted by mutableStateOf<FluidPasted?>(null)
    show { FluidPasteButton(FluidPasteKind.code(6..6), onPaste = { pasted = it }) }
    assertFalse(hasNode(hasText("Incolla il codice")))
    copyClassified("Ci vediamo alle otto davanti al cinema", emptyMap())
    assertFalse("appunti normali: niente tasto", hasNode(hasText("Incolla il codice")))
    copyClassified("Il tuo codice è 482913", mapOf("otp" to 1f))
    assertTrue(hasNode(hasText("Incolla il codice")))
    assertEquals("nessuna lettura del contenuto prima del tocco", 0, CountingClipboardShadow.contentReads)
    compose.onAllNodes(hasText("Incolla il codice")).fetchSemanticsNodes().first().let { node ->
      compose.runOnUiThread { node.config[SemanticsActions.OnClick].action?.invoke() }
    }
    Snapshot.sendApplyNotifications()
    advance(100)
    assertEquals(FluidPasted.Code("482913"), pasted)
    assertEquals(1, CountingClipboardShadow.contentReads)
  }

  @Test fun `un codice che Android non classifica (Android 12-15) non fa comparire il tasto`() {
    show { FluidPasteButton(FluidPasteKind.code(6..6), onPaste = {}) }
    copyClassified("482913", emptyMap())
    assertFalse(hasNode(hasText("Incolla il codice")))
    assertEquals(0, CountingClipboardShadow.contentReads)
  }

  @Test fun `il tasto del link compare con un link e non con un testo`() {
    var pasted by mutableStateOf<FluidPasted?>(null)
    show { FluidPasteButton(FluidPasteKind.Link, onPaste = { pasted = it }) }
    copyClassified("Ci vediamo alle otto", emptyMap())
    assertFalse(hasNode(hasText("Incolla il link")))
    copyClassified("https://pampa.example/album/estate-2026", mapOf("url" to 1f))
    assertTrue(hasNode(hasText("Incolla il link")))
    assertEquals(0, CountingClipboardShadow.contentReads)
    compose.onAllNodes(hasText("Incolla il link")).fetchSemanticsNodes().first().let { node ->
      compose.runOnUiThread { node.config[SemanticsActions.OnClick].action?.invoke() }
    }
    Snapshot.sendApplyNotifications()
    advance(100)
    assertEquals(FluidPasted.Link("https://pampa.example/album/estate-2026"), pasted)
  }

  @Test fun `il tasto dell'immagine compare con un'immagine`() {
    var pasted by mutableStateOf<FluidPasted?>(null)
    show { FluidPasteButton(FluidPasteKind.Image, onPaste = { pasted = it }) }
    copyClassified("https://pampa.example", mapOf("url" to 1f))
    assertFalse(hasNode(hasText("Incolla l'immagine")))
    val uri = Uri.parse("content://dev.antigravity.test/screenshot.png")
    compose.runOnUiThread {
      clipboard.setPrimaryClip(ClipData(ClipDescription("Screenshot", arrayOf("image/png")), ClipData.Item(uri)))
    }
    Snapshot.sendApplyNotifications()
    advance(600)
    assertTrue(hasNode(hasText("Incolla l'immagine")))
    assertEquals(0, CountingClipboardShadow.contentReads)
    compose.onAllNodes(hasText("Incolla l'immagine")).fetchSemanticsNodes().first().let { node ->
      compose.runOnUiThread { node.config[SemanticsActions.OnClick].action?.invoke() }
    }
    Snapshot.sendApplyNotifications()
    advance(100)
    assertEquals(FluidPasted.Image(uri, "image/png"), pasted)
  }

  @Test @Config(sdk = [28])
  fun `sotto Android 12 il testo corto si legge in silenzio, e solo un codice intero fa comparire il tasto`() {
    show { FluidPasteButton(FluidPasteKind.code(6..6), onPaste = {}) }
    compose.runOnUiThread { clipboard.setPrimaryClip(ClipData.newPlainText("", "Chiamami al 3331234567")) }
    Snapshot.sendApplyNotifications()
    advance(600)
    assertFalse(hasNode(hasText("Incolla il codice")))
    compose.runOnUiThread { clipboard.setPrimaryClip(ClipData.newPlainText("", "482 913")) }
    Snapshot.sendApplyNotifications()
    advance(600)
    assertTrue(hasNode(hasText("Incolla il codice")))
  }

  // --- La galleria ----------------------------------------------------------------------------------------------

  @Test fun `le sei scene si aprono`() {
    var open by mutableStateOf<String?>(null)
    show { key(open) { NewElementsPage(onBack = {}, initial = open) } }
    assertTrue(hasNode(hasText("Elementi nuovi")))
    for (scene in F8p3Section.scenes) {
      open = scene.id
      Snapshot.sendApplyNotifications()
      advance(900)
      assertTrue("${scene.id}: manca il titolo", hasNode(hasText(scene.title)))
    }
    assertNotNull(newElementScene("codice"))
    assertNull(newElementScene("nessuna"))
    assertEquals(FluidSpecialFieldLabels.English.copied, "Copied")
  }
}
